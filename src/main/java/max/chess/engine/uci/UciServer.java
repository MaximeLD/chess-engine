package max.chess.engine.uci;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Minimal UCI server for cutechess / GUIs.
 * Plug your engine via UciEngine, then call new UciServer("Name","Author", engine).run();
 */
public final class UciServer {
    private final String name;
    private final String author;
    private final UciEngine engine;

    private final PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(System.out, StandardCharsets.US_ASCII)), true);
    private final BufferedReader in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.US_ASCII));

    private Thread searchThread;
    private final AtomicBoolean stopFlag = new AtomicBoolean(false);
    private volatile GoParams lastGo; // in case you care about ponderhit semantics

    public UciServer(String name, String author, UciEngine engine) {
        this.name = Objects.requireNonNull(name);
        this.author = Objects.requireNonNull(author);
        this.engine = Objects.requireNonNull(engine);
    }

    /** Run the UCI loop on the current thread. */
    public void run() {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.equals("uci")) {
                    send("id name " + name);
                    send("id author " + author);
                    // If you expose options dynamically, print: send("option name X type ... default ...");
                    send("uciok");
                } else if (line.equals("isready")) {
                    engine.onIsReady();
                    send("readyok");
                } else if (line.startsWith("setoption")) {
                    handleSetOption(line);
                } else if (line.equals("ucinewgame")) {
                    engine.newGame();
                } else if (line.startsWith("position")) {
                    handlePosition(line);
                } else if (line.startsWith("go")) {
                    handleGo(line);
                } else if (line.equals("stop")) {
                    requestStopAndJoin();
                } else if (line.equals("ponderhit")) {
                    // tell the current search it may start thinking for real
                    GoParams lg = lastGo;
                    if (lg != null) lg.ponderHit.set(true);
                } else if (line.equals("quit")) {
                    requestStopAndJoin();
                    break;
                } else if (line.equals("print")) { // handy debug
                    engine.debugDump(this::send);
                } else {
                    // ignore unknown commands per UCI tolerance
                }
            }
        } catch (IOException e) {
            // exit silently; GUIs sometimes close pipes abruptly
        }
    }

    /* -------------------- command handlers -------------------- */

    private void handleSetOption(String line) {
        // Syntax: setoption name <id> [value <x>]
        // We’ll parse in a tolerant way.
        String rest = line.substring("setoption".length()).trim();
        if (rest.isEmpty()) return;

        String name = null, value = null;
        List<String> toks = Arrays.asList(rest.split("\\s+"));
        for (int i = 0; i < toks.size(); i++) {
            String t = toks.get(i);
            if (t.equals("name")) {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < toks.size() && !toks.get(i).equals("value")) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(toks.get(i++));
                }
                i--; // step back one, for loop will ++
                name = sb.toString();
            } else if (t.equals("value")) {
                StringBuilder sb = new StringBuilder();
                i++;
                while (i < toks.size()) {
                    if (sb.length() > 0) sb.append(' ');
                    sb.append(toks.get(i++));
                }
                i--;
                value = sb.toString();
            }
        }
        if (name != null) engine.setOption(name, value == null ? "" : value);
    }

    private void handlePosition(String line) {
        // position [startpos | fen <FEN...>] [moves <m1> <m2> ...]
        String rest = line.substring("position".length()).trim();
        if (rest.startsWith("startpos")) {
            List<String> moves = Collections.emptyList();
            int idx = rest.indexOf("moves");
            if (idx >= 0) {
                moves = splitMoves(rest.substring(idx + "moves".length()).trim());
            }
            engine.setPositionStartpos(moves);
        } else if (rest.startsWith("fen")) {
            String afterFen = rest.substring(3).trim();
            // FEN is 6 space-separated fields; stop if we see "moves"
            String fen, movesPart = null;
            int movesIdx = afterFen.indexOf(" moves ");
            if (movesIdx >= 0) {
                fen = afterFen.substring(0, movesIdx).trim();
                movesPart = afterFen.substring(movesIdx + 7).trim();
            } else {
                fen = afterFen.trim();
            }
            List<String> moves = movesPart == null ? Collections.emptyList() : splitMoves(movesPart);
            engine.setPositionFEN(fen, moves);
        } else {
            // tolerant: if someone sends just "position" we ignore
        }
    }

    private static List<String> splitMoves(String s) {
        if (s.isEmpty()) return Collections.emptyList();
        String[] arr = s.trim().split("\\s+");
        return Arrays.asList(arr);
    }

    private void handleGo(String line) {
        GoParams gp = parseGo(line);
        lastGo = gp;
        requestStopAndJoin(); // ensure no previous search running
        stopFlag.set(false);
        searchThread = new Thread(() -> {
            try {
                UciResult res = engine.search(gp, stopFlag, this::sendInfo);
                if (res == null || res.bestmove == null || res.bestmove.isEmpty()) {
                    // UCI requires bestmove anyway
                    send("bestmove 0000");
                } else {
                    if (res.ponder != null && !res.ponder.isEmpty())
                        send("bestmove " + res.bestmove + " ponder " + res.ponder);
                    else
                        send("bestmove " + res.bestmove);
                }
            } catch (Throwable t) {
                // As a last resort, don't crash the GUI
                send("bestmove 0000");
            }
        }, "uci-search");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private GoParams parseGo(String line) {
        GoParams gp = new GoParams();
        String[] t = line.split("\\s+");
        for (int i = 1; i < t.length; i++) {
            switch (t[i]) {
                case "wtime": gp.wtime = parseLong(t, ++i); break;
                case "btime": gp.btime = parseLong(t, ++i); break;
                case "winc": gp.winc = parseLong(t, ++i); break;
                case "binc": gp.binc = parseLong(t, ++i); break;
                case "movestogo": gp.movestogo = (int) parseLong(t, ++i); break;
                case "movetime": gp.movetime = parseLong(t, ++i); break;
                case "depth": gp.depth = (int) parseLong(t, ++i); break;
                case "nodes": gp.nodes = parseLong(t, ++i); break;
                case "mate": gp.mate = (int) parseLong(t, ++i); break;
                case "ponder": gp.ponder = true; break;
                case "infinite": gp.infinite = true; break;
                default: /* ignore others (e.g., searchmoves…) for now */ break;
            }
        }
        return gp;
    }

    private long parseLong(String[] tok, int i) {
        if (i >= tok.length) return 0;
        try { return Long.parseLong(tok[i]); } catch (Exception e) { return 0; }
    }

    /* -------------------- lifecycle helpers -------------------- */

    private void requestStopAndJoin() {
        Thread t = searchThread;
        if (t != null && t.isAlive()) {
            stopFlag.set(true);
            engine.onStopHint(); // optional hook to nudge your search
            try { t.join(100); } catch (InterruptedException ignored) {}
        }
    }

    private synchronized void send(String line) {
        out.println(line);
        out.flush();
    }

    private void sendInfo(String infoLine) {
        if (infoLine == null || infoLine.isEmpty()) return;
        // Ensure the line already starts with "info"; your engine should format it
        if (!infoLine.startsWith("info")) send("info " + infoLine);
        else send(infoLine);
    }

    /* -------------------- types you implement -------------------- */


    /** Search parameters passed on "go". All values are milliseconds unless noted. */
    public static final class GoParams {
        public long wtime = -1, btime = -1, winc = 0, binc = 0;
        public int movestogo = -1;
        public long movetime = -1;
        public int depth = -1;
        public long nodes = -1;
        public int mate = -1;
        public boolean ponder = false;
        public boolean infinite = false;
        /** Set by GUI "ponderhit". Your search can watch this if you support ponder. */
        public final AtomicBoolean ponderHit = new AtomicBoolean(false);
    }

    /* -------------------- Example main -------------------- */

    public static void main(String[] args) {
        // Replace DummyEngine with your implementation of UciEngine.
        UciEngine engine = new DummyEngine(); // TODO plug your engine bridge here
        new UciServer("MyEngine", "Your Name", engine).run();
    }

    /* A tiny stub so this compiles standalone. Delete and wire your engine. */
    static final class DummyEngine implements UciEngine {
        private boolean whiteToMove = true;
        @Override public void newGame() { whiteToMove = true; }
        @Override public void setOption(String n, String v) {}
        @Override public void setPositionStartpos(List<String> moves) { applyMoves(moves); }
        @Override public void setPositionFEN(String fen, List<String> moves) { /* parse FEN here */ applyMoves(moves); }
        private void applyMoves(List<String> moves) { whiteToMove = ((moves.size() & 1) == 0); }

        @Override
        public UciResult search(GoParams go, AtomicBoolean stop, java.util.function.Consumer<String> info) {
            // Replace with your real search. This is a 1-ply pass move to keep cutechess happy.
            // Emit a fake info line:
            info.accept("depth 1 score cp 0 nodes 1 nps 1000 time 1 pv e2e4");
            // Pick a legal move from your engine instead of hardcoding:
            String bm = whiteToMove ? "e2e4" : "e7e5";
            return UciResult.best(bm);
        }
    }
}
