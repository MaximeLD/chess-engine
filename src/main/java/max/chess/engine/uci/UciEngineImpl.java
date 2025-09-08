package max.chess.engine.uci;

import max.chess.engine.book.BookManager;
import max.chess.engine.book.BookPolicy;
import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.movegen.Move;
import max.chess.engine.search.SearchConfig;
import max.chess.engine.search.SearchFacade;
import max.chess.engine.search.SearchResult;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UciEngineImpl implements UciEngine {
    private Game game;

    private boolean warmedUp = false;

    // --------------------- BOOK FIELDS ---------------------
    private final BookManager book = new BookManager();
    private volatile boolean ownBook = Boolean.parseBoolean(System.getProperty("book.enabled", "true"));
    // Use classpath by default; points to a directory. In dev (file://), we list; in jar, prefer a specific file.
    private volatile String bookFileOrDir = System.getProperty("book.path", "classpath:books/Perfect_2023/BIN");
    private volatile int bookMaxPlies = 20;
    private volatile int bookRandomness = 0;  // deterministic for Elo tests
    private volatile int bookMinWeight = 50;  // ignore ultra-rare sidelines
    private volatile boolean bookPreferMainline = true;
    private volatile int pliesPlayed = 0;
    // --------------------------------------------------------------

    // TB FIELDS
    private final max.chess.engine.tb.TBManager tb = new max.chess.engine.tb.TBManager();
    private volatile boolean useSyzygy = Boolean.parseBoolean(System.getProperty("syzygy.enabled", "true"));
    private volatile int tbMaxPieces = 5;
    private volatile boolean tbUseDTZ = true;
    private volatile boolean tbProbeInSearch = false; // default off; enable explicitly when benchmarking it
    private volatile String syzygyPath = System.getProperty("syzygy.path", "syzygy/3-4-5/Syzygy345");

    private volatile boolean staticEvalOnly = false;
    public final SearchConfig cfg = new SearchConfig.Builder()
            .debug(Boolean.parseBoolean(System.getProperty("debug", "false")))
            .useTT(Boolean.parseBoolean(System.getProperty("tt.enabled", "true")))
            .ttSizeMb(Integer.parseInt(System.getProperty("tt.size", "64")))
            .useNullMove(true).nullBaseReduction(2).nullMinDepth(3).nullVerifyDepth(0)
            .useLMR(true).lmrMinDepth(3).lmrMinMove(4).lmrBase(1).lmrMax(3)
            .lmrReduceCaptures(false).lmrReduceChecks(false)
            .lmrNoReduceTTTrusted(true).lmrNoReduceKiller(true)
            .lmrHistorySkip(4000)
            .useFutility(true).useExtendedFutility(true).useReverseFutility(true)
            .futilityMargin1(100).futilityMargin2(200).futilityMargin3(300)
            .reverseFutilityMargin(100)
            .useProbCut(Boolean.parseBoolean(System.getProperty("probcut.enabled", "true")))
            .probCutMinDepth(5)
            .probCutReduction(2)
            .probCutMargin(120)
            .probCutMaxMoves(8)
            .probCutRequireSEEPositive(true)
            .probCutVictimMin(max.chess.engine.search.evaluator.PieceValues.ROOK_VALUE)
            .build();

    private final SearchFacade engine = new SearchFacade(cfg);;

    public UciEngineImpl() {
        // Try default book at startup (doesn't fail if missing)
        if(ownBook) {
            try {
                book.loadAuto(bookFileOrDir);
            } catch (Throwable ignored) {
            }
            syncBookPolicy();
        }

        // TB init: look for an implementation via ServiceLoader; falls back to Noop
        if(useSyzygy) {
            tb.loadProvider();
            syncTBPolicy();
        }

        engine.setTablebases(tb, tbProbeInSearch);
    }

    static {
//        NegamaxDeepeningSearchWithTTAndQuiescenceSEEDelta.init();
    }

    @Override
    public void newGame() {
        pliesPlayed = 0;
        book.setPliesPlayed(0);
    }

    @Override
    public void onIsReady() {
        UciEngine.super.onIsReady();
    }

    @Override
    public void setOption(String name, String value) {
        switch (name.toLowerCase()) {
            case "staticevalonly" -> { staticEvalOnly = Boolean.parseBoolean(value); }
            // Book options
            case "ownbook" -> { ownBook = Boolean.parseBoolean(value); book.setEnabled(ownBook); }
            case "bookfile" -> {
                bookFileOrDir = value;
                try { book.loadAuto(bookFileOrDir); } catch (Exception ignored) {}
            }
            case "bookmaxplies" -> { bookMaxPlies = clampInt(value, 0, 200, 20); syncBookPolicy(); }
            case "bookminweight" -> { bookMinWeight = clampInt(value, 0, 65535, 2); syncBookPolicy(); }
            case "bookrandomness" -> { bookRandomness = clampInt(value, 0, 100, 15); syncBookPolicy(); }
            case "bookprefermainline" -> { bookPreferMainline = Boolean.parseBoolean(value); syncBookPolicy(); }
            // TB options
            case "usesyzygy" -> { useSyzygy = Boolean.parseBoolean(value); syncTBPolicy(); }
            case "syzygymaxpieces" -> { tbMaxPieces = clampInt(value, 0, 7, 5); syncTBPolicy(); }
            case "syzygyusedtz" -> { tbUseDTZ = Boolean.parseBoolean(value); syncTBPolicy(); }
            case "syzygyprobeinsearch" -> { tbProbeInSearch = Boolean.parseBoolean(value); syncTBPolicy(); }
            case "syzygypath" -> { syzygyPath = value; syncTBPolicy(); }

            default -> { /* pass through */ }
        }
        UciEngine.super.setOption(name, value);
    }

    @Override
    public void setPositionStartpos(List<String> uciMoves) {
        setPositionFEN(BoardGenerator.STANDARD_GAME, uciMoves);
    }

    @Override
    public void setPositionFEN(String fen, List<String> uciMoves) {
        game = FENUtils.getBoardFrom(fen);
        uciMoves.forEach(move -> game.playSimpleMove(Move.fromAlgebraicNotation(move)));
        pliesPlayed = uciMoves.size();
        book.setPliesPlayed(pliesPlayed);
    }

    @Override
    public UciResult search(UciServer.GoParams go, AtomicBoolean stopFlag, Consumer<String> infoSink) {
        if (game == null) {
            return UciResult.best("0000");
        }

        // --------------------- BOOK PROBE ---------------------------
        if (ownBook) {
            book.setPliesPlayed(pliesPlayed);
            var ob = book.pickUci(game);
            if (ob.isPresent()) {
                String moveUci = ob.get();
                infoSink.accept("info string book move " + moveUci);
                // do NOT increment pliesPlayed here; GUI will send the move back in 'position ... moves'
                return UciResult.best(moveUci);
            }
        }
        // ------------------------------------------------------------

        // TB ROOT PROBE: if in tablebase realm, play perfect move immediately
        if (useSyzygy) {
            var tbUci = tb.pickUci(game);
            if (tbUci.isPresent()) {
                infoSink.accept("info string tb move " + tbUci.get());
                return UciResult.best(tbUci.get());
            }
        }

        go.staticEvalOnly = staticEvalOnly;
        SearchResult searchResult = engine.findBestMove(game, stopFlag, go, infoSink);
        infoSink.accept(searchResult.toUCIInfo());
        return UciResult.best(MoveIOUtils.writeAlgebraicNotation(searchResult.move()));
    }

    @Override
    public void onStopHint() {
        UciEngine.super.onStopHint();
    }

    @Override
    public void debugDump(Consumer<String> out) {
        UciEngine.super.debugDump(out);
    }


    private static int clampInt(String s, int lo, int hi, int dflt) {
        try { int v = Integer.parseInt(s); return Math.min(hi, Math.max(lo, v)); }
        catch (Exception ignored) { return dflt; }
    }

    private void syncBookPolicy() {
        book.setPolicy(new BookPolicy(bookMaxPlies, bookMinWeight, bookRandomness, bookPreferMainline));
        book.setEnabled(ownBook);
    }

    private void syncTBPolicy() {
        tb.setEnabled(useSyzygy);
        tb.setMaxPieces(tbMaxPieces);
        tb.setUseDTZ(tbUseDTZ);
        tb.setPath(syzygyPath);

        // If disabled, hide TB entirely from the search context
        if (!useSyzygy) {
            engine.setTablebases(null, false);
        } else {
            engine.setTablebases(tb, tbProbeInSearch);
        }
    }
}
