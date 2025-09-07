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

    // --------------------- BOOK FIELDS ---------------------
    private final BookManager book = new BookManager();
    private volatile boolean ownBook = true;
    // Use classpath by default; points to a directory. In dev (file://), we list; in jar, prefer a specific file.
    private volatile String bookFileOrDir = "classpath:books/Perfect_2023/BIN";
    private volatile int bookMaxPlies = 20;
    private volatile int bookMinWeight = 2;
    private volatile int bookRandomness = 15;
    private volatile boolean bookPreferMainline = true;
    private volatile int pliesPlayed = 0;
    // --------------------------------------------------------------

    private volatile boolean staticEvalOnly = false;
    private final SearchConfig cfg = new SearchConfig.Builder()
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
        try { book.loadAuto(bookFileOrDir); } catch (Throwable ignored) {}
        syncBookPolicy();
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
        if ("staticevalonly".equalsIgnoreCase(name)) {
            staticEvalOnly = Boolean.parseBoolean(value);
        }
        // Book options
        switch (name.toLowerCase()) {
            case "staticevalonly" -> { staticEvalOnly = Boolean.parseBoolean(value); }
            case "ownbook" -> { ownBook = Boolean.parseBoolean(value); book.setEnabled(ownBook); }
            case "bookfile" -> {
                bookFileOrDir = value;
                try { book.loadAuto(bookFileOrDir); } catch (Exception ignored) {}
            }
            case "bookmaxplies" -> { bookMaxPlies = clampInt(value, 0, 200, 20); syncBookPolicy(); }
            case "bookminweight" -> { bookMinWeight = clampInt(value, 0, 65535, 2); syncBookPolicy(); }
            case "bookrandomness" -> { bookRandomness = clampInt(value, 0, 100, 15); syncBookPolicy(); }
            case "bookprefermainline" -> { bookPreferMainline = Boolean.parseBoolean(value); syncBookPolicy(); }
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

        // Triggering a small search to warmup the hotpath
        boolean didOwnBook = ownBook;
        ownBook = false; // Disabling for warmup on search
        UciServer.GoParams goParams = new UciServer.GoParams();
        goParams.movetime = 500; // 500 ms of warmup
        search(goParams, new AtomicBoolean(false), (string) -> System.err.println("WARM UP MESSAGE : "+string));
        ownBook = didOwnBook;
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

}
