package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import max.chess.engine.utils.ColorUtils;

final class KingSafety {
    private KingSafety() {}

    /** Super cheap: holes in the pawn shield + semi/open files near the king. */
    static boolean quickDanger(Game g) {
        final Board b = g.board();
        final boolean stmWhite = ColorUtils.isWhite(g.currentPlayer);

        long usK = b.kingBB & (stmWhite ? b.whiteBB : b.blackBB);
        if (usK == 0) return false;
        int ks = Long.numberOfTrailingZeros(usK);
        int file = ks & 7;

        long usP   = b.pawnBB & (stmWhite ? b.whiteBB : b.blackBB);
        long themP = b.pawnBB & (stmWhite ? b.blackBB : b.whiteBB);

        // Count missing shield pawns on f,g,h (or c,b,a) around the king file if short castled
        int holes = 0;
        if (file >= 5) { // f,g,h region
            for (int f = 5; f <= 7; f++) {
                long mask = OrthogonalMoveUtils.FILES[f];
                if ((usP & mask) == 0) holes++;
            }
        } else if (file <= 2) { // a,b,c region
            for (int f = 0; f <= 2; f++) {
                long mask = OrthogonalMoveUtils.FILES[f];
                if ((usP & mask) == 0) holes++;
            }
        }

        // Semi/open g/h (or a/b) files for opponent
        int semopen = 0;
        int[] checkFiles = (file >= 5) ? new int[]{5,6,7} : new int[]{0,1,2};
        for (int f : checkFiles) {
            long mask = OrthogonalMoveUtils.FILES[f];
            boolean ours   = (usP & mask) != 0;
            boolean theirs = (themP & mask) != 0;
            if (!ours && theirs) semopen++;
            if (!ours && !theirs) semopen += 2; // open is worse
        }

        return (holes + semopen) >= 3; // threshold is cheap & conservative
    }
}
