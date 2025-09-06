package max.chess.engine.uci;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.movegen.Move;
import max.chess.engine.search.SearchConfig;
import max.chess.engine.search.SearchFacade;
import max.chess.engine.search.archived.NegamaxDeepeningSearchWithTTAndQuiescenceSEEDelta;
import max.chess.engine.search.SearchResult;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UciEngineImpl implements UciEngine {
    private Game game;

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

    static {
//        NegamaxDeepeningSearchWithTTAndQuiescenceSEEDelta.init();
    }

    @Override
    public void newGame() {
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
        // Triggering a small search to warmup the hotpath
        UciServer.GoParams goParams = new UciServer.GoParams();
        goParams.movetime = 500; // 500 ms of warmup
        search(goParams, new AtomicBoolean(false), (string) -> System.err.println("WARM UP MESSAGE : "+string));
    }

    @Override
    public UciResult search(UciServer.GoParams go, AtomicBoolean stopFlag, Consumer<String> infoSink) {
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
}
