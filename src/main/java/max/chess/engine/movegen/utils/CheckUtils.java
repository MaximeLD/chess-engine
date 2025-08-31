package max.chess.engine.movegen.utils;

import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.game.board.DirtyBoard;
import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;

public final class CheckUtils {
    public static int NECESSARY_KING_CHECKS = 0;
    public static int UNNECESSARY_KING_CHECKS = 0;
    public static boolean isKingInCheck(long kingPositionBB, long enemyAttackBB) {
        return (kingPositionBB & enemyAttackBB) != 0;
    }

    public static boolean wouldKingBeInCheck(final int startPosition, final int endPosition, int kingColor, Game game) {
        DirtyBoard dirtyBoard = game.board().dirtyCopy();
        dirtyBoard.playDirtyMove(startPosition, endPosition, kingColor);
        long kingBB = ColorUtils.isWhite(kingColor) ? dirtyBoard.whiteKingBB : dirtyBoard.blackKingBB;
        boolean wouldBeInCheck = MoveGenerator.getCheckersBB(BitUtils.bitScanForward(kingBB), dirtyBoard, ColorUtils.switchColor(kingColor), true) != 0L;
        if(wouldBeInCheck) {
            NECESSARY_KING_CHECKS++;
        } else {
            UNNECESSARY_KING_CHECKS++;
        }
        return wouldBeInCheck;
    }

    public static void printChecksReport() {
        System.out.println("*************************");
        System.out.println("CHECK REPORT");
        System.out.println("NECESSARY KING CHECKS: "+NECESSARY_KING_CHECKS);
        System.out.println("UNNECESSARY KING CHECKS: "+UNNECESSARY_KING_CHECKS);
        System.out.println("*************************");
    }

    public static void clearChecksReport() {
        NECESSARY_KING_CHECKS = 0;
        UNNECESSARY_KING_CHECKS = 0;
    }
}
