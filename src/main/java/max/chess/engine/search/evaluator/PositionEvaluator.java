package max.chess.engine.search.evaluator;

import max.chess.engine.game.Game;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;

public class PositionEvaluator {
    // The higher the score, the better the position
    public static int evaluatePosition(Game game) {
        int currentPlayer = game.currentPlayer;
        boolean isWhiteTurn = ColorUtils.isWhite(currentPlayer);

        long sideBB;
        if(isWhiteTurn) {
            sideBB = game.board().whiteBB;
        } else {
            sideBB = game.board().blackBB;
        }

        int rookValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & game.board().rookBB);
        int bishopValue = PieceValues.BISHOP_VALUE * BitUtils.bitCount(sideBB & game.board().bishopBB);
        int pawnValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & game.board().pawnBB);
        int queenValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & game.board().queenBB);
        int knightValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & game.board().knightBB);

        return rookValue+bishopValue+pawnValue+queenValue+knightValue;

    }
}
