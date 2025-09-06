package max.chess.engine.search;

import max.chess.engine.game.Game;

final class PrincipalVariation {
    static int legalize(Game game, int[] pv, int maxLen, int[][] moveBuf) {
        int len = 0;
        long[] undo = new long[Math.min(maxLen, SearchConstants.MAX_PLY)];
        for (int i = 0; i < maxLen; i++) {
            int mv = pv[i];
            int[] buf = moveBuf[len];
            int n = game.getLegalMoves(buf);
            boolean ok = false;
            for (int j = 0; j < n; j++) if (buf[j] == mv) { ok = true; break; }
            if (!ok) break;
            undo[len] = game.playMove(mv);
            len++;
        }
        for (int i = len - 1; i >= 0; i--) game.undoMove(undo[i]);
        return len;
    }
}
