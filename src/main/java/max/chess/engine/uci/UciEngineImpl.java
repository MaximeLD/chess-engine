package max.chess.engine.uci;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.search.NaivePositionEvaluatorSearch;
import max.chess.engine.search.RandomSearch;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class UciEngineImpl implements UciEngine {
    private Game game;

    @Override
    public void newGame() {
        MoveGenerator.warmUp();
    }

    @Override
    public void onIsReady() {
        UciEngine.super.onIsReady();
    }

    @Override
    public void setOption(String name, String value) {
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
    }

    @Override
    public UciResult search(UciServer.GoParams go, AtomicBoolean stopFlag, Consumer<String> infoSink) {
        return UciResult.best(MoveIOUtils.writeAlgebraicNotation(NaivePositionEvaluatorSearch.pickNextMove(game)));
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
