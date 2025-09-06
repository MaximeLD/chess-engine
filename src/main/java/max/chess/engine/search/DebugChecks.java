package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.utils.ColorUtils;

final class DebugChecks {
    private DebugChecks() {}
    static boolean kingIsOn(Game g, int color, int sq) {
        long bb = g.board().kingBB & (max.chess.engine.utils.ColorUtils.isWhite(color) ? g.board().whiteBB : g.board().blackBB);
        return bb != 0L && Long.numberOfTrailingZeros(bb) == sq;
    }
    static boolean isSquareAttackedOcc(max.chess.engine.game.board.Board b, int sq, boolean byWhite, long occ) {
        long bb = 1L << sq;
        long pawns = b.pawnBB & (byWhite ? b.whiteBB : b.blackBB);
        long pAtt = byWhite
                ? (((bb >>> 7) & ~max.chess.engine.movegen.utils.OrthogonalMoveUtils.FILES[0]) |
                   ((bb >>> 9) & ~max.chess.engine.movegen.utils.OrthogonalMoveUtils.FILES[7]))
                : (((bb << 7)  & ~max.chess.engine.movegen.utils.OrthogonalMoveUtils.FILES[7]) |
                   ((bb << 9)  & ~max.chess.engine.movegen.utils.OrthogonalMoveUtils.FILES[0]));
        if ((pAtt & pawns) != 0) return true;
        long knights = b.knightBB & (byWhite ? b.whiteBB : b.blackBB);
        if ((max.chess.engine.movegen.pieces.Knight.getAttackBB(sq) & knights) != 0) return true;
        long kings = b.kingBB & (byWhite ? b.whiteBB : b.blackBB);
        if ((max.chess.engine.movegen.pieces.King.getAttackBB(sq) & kings) != 0) return true;
        long diag = max.chess.engine.movegen.pieces.Bishop.getAttackBB(sq, occ);
        long bishops = b.bishopBB & (byWhite ? b.whiteBB : b.blackBB);
        long queens  = b.queenBB  & (byWhite ? b.whiteBB : b.blackBB);
        if ((diag & (bishops | queens)) != 0) return true;
        long ortho = max.chess.engine.movegen.pieces.Rook.getAttackBB(sq, occ);
        long rooks = b.rookBB & (byWhite ? b.whiteBB : b.blackBB);
        if ((ortho & (rooks | queens)) != 0) return true;
        return false;
    }
    static void assertMoveDidNotLeaveOwnKingInCheck(max.chess.engine.game.Game g) {
        final var b = g.board();
        final boolean white = max.chess.engine.utils.ColorUtils.isWhite(ColorUtils.switchColor(g.currentPlayer));
        long kBB = b.kingBB & (white ? b.whiteBB : b.blackBB);
        if (kBB == 0L) {
            throw new IllegalStateException("Illegal move slipped through: king is no longer present !");
        }
        int kSq = Long.numberOfTrailingZeros(kBB);
        if (isSquareAttackedOcc(b, kSq, !white, b.gameBB)) {
            throw new IllegalStateException("Illegal move slipped through: king in check after make()");
        }
    }
}
