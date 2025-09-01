package max.chess.engine.search.evaluator;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;

public class PositionEvaluator {
    // The higher the score, the better the position
    public static int evaluatePosition(Game game) {
        int currentPlayer = game.currentPlayer;
        boolean isWhiteTurn = ColorUtils.isWhite(currentPlayer);

        long sideBB;
        long oppositeSideBB;
        if(isWhiteTurn) {
            sideBB = game.board().whiteBB;
            oppositeSideBB = game.board().blackBB;
        } else {
            sideBB = game.board().blackBB;
            oppositeSideBB = game.board().whiteBB;
        }

        int rookValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(sideBB & game.board().rookBB);
        int bishopValue = PieceValues.BISHOP_VALUE * BitUtils.bitCount(sideBB & game.board().bishopBB);
        int pawnValue = PieceValues.PAWN_VALUE * BitUtils.bitCount(sideBB & game.board().pawnBB);
        int queenValue = PieceValues.QUEEN_VALUE * BitUtils.bitCount(sideBB & game.board().queenBB);
        int knightValue = PieceValues.KNIGHT_VALUE * BitUtils.bitCount(sideBB & game.board().knightBB);

        int scoreForPlayingSide = rookValue+bishopValue+pawnValue+queenValue+knightValue;

        int scoreAttackerPlayingSide = BitUtils.bitCount(MoveGenerator.doGetAttackBB(game.board(), game.currentPlayer, 0));
        rookValue = PieceValues.ROOK_VALUE * BitUtils.bitCount(oppositeSideBB & game.board().rookBB);
        bishopValue = PieceValues.BISHOP_VALUE * BitUtils.bitCount(oppositeSideBB & game.board().bishopBB);
        pawnValue = PieceValues.PAWN_VALUE * BitUtils.bitCount(oppositeSideBB & game.board().pawnBB);
        queenValue = PieceValues.QUEEN_VALUE * BitUtils.bitCount(oppositeSideBB & game.board().queenBB);
        knightValue = PieceValues.KNIGHT_VALUE * BitUtils.bitCount(oppositeSideBB & game.board().knightBB);

        int scoreForOppositeSide = rookValue+bishopValue+pawnValue+queenValue+knightValue;
        int scoreAttackerOppositeSide = BitUtils.bitCount(MoveGenerator.doGetAttackBB(game.board(), ColorUtils.switchColor(game.currentPlayer), 0));

        return scoreForPlayingSide + scoreAttackerPlayingSide - scoreForOppositeSide - scoreAttackerOppositeSide;

    }
}
