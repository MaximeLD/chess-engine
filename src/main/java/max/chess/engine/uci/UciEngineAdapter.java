package max.chess.engine.uci;

import max.chess.engine.common.Color;
import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;

public class UciEngineAdapter {
    private Game game;
    public void newGame() {
        // Nothing to do
    }

    public void isReady() {
        // Warmup
        MoveGenerator.warmUp();
    }

    public void setOption(String name, String s) {
        // Not implemented for now
    }

    public void setStartPos() {
        setFEN(BoardGenerator.STANDARD_GAME);
    }

    public void setFEN(String FEN) {
        game = FENUtils.getBoardFrom(FEN);
    }

    public void makeUciMove(String simpleMove) {
        game.playSimpleMove(Move.fromAlgebraicNotation(simpleMove));
    }

    public Color sideToMove() {
        return ColorUtils.isWhite(game.currentPlayer) ? Color.WHITE : Color.BLACK;
    }
}
