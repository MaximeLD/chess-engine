package max.chess.engine.search.evaluator;

import max.chess.engine.game.Game;

public final class GamePhase {

    // Phase weights (same as Stockfish idea)
    private static final int PH_KNIGHT = 1;
    private static final int PH_BISHOP = 1;
    private static final int PH_ROOK   = 2;
    private static final int PH_QUEEN  = 4;

    // Max starting counts (both sides combined)
    private static final int MAX_N = 4;
    private static final int MAX_B = 4;
    private static final int MAX_R = 4;
    private static final int MAX_Q = 2;

    private static final int PHASE_TOTAL =
            MAX_N * PH_KNIGHT +
            MAX_B * PH_BISHOP +
            MAX_R * PH_ROOK +
            MAX_Q * PH_QUEEN;        // = 24

    /** 0.0 = opening, 1.0 = endgame */
    public static double currentGameProgress(Game g) {
        int n = Long.bitCount(g.board().knightBB);
        int b = Long.bitCount(g.board().bishopBB);
        int r = Long.bitCount(g.board().rookBB);
        int q = Long.bitCount(g.board().queenBB);

        // Cap by starting maxima so promotions don’t push phase “backwards”
        n = Math.min(n, MAX_N);
        b = Math.min(b, MAX_B);
        r = Math.min(r, MAX_R);
        q = Math.min(q, MAX_Q);

        int phaseRemaining =
                n * PH_KNIGHT +
                b * PH_BISHOP +
                r * PH_ROOK +
                q * PH_QUEEN;

        double progress = 1.0 - (double) phaseRemaining / PHASE_TOTAL;

        // Optional: blend in a tiny pawn-based hint (fewer pawns -> more endgame)
        // This helps in queenless but pawn-rich positions not to overcall "endgame".
        int pawns = Long.bitCount(g.board().pawnBB); // 0..16
        double pawnProgress = 1.0 - pawns / 16.0;   // 0 opening .. 1 pawnless
        progress = 0.85 * progress + 0.15 * pawnProgress;

        // Optional: non-linear shaping (bias a bit toward middlegame)
        // progress = Math.pow(progress, 1.20);

        // Clamp
        if (progress < 0) progress = 0;
        if (progress > 1) progress = 1;
        return progress;
    }

    /** Convenience weights for tapering */
    public static double middleGameWeight(Game g) { return 1.0 - currentGameProgress(g); }
    public static double endGameWeight(Game g) { return currentGameProgress(g); }

    @Deprecated // Not performant enough
    public static int blendValue(int middleGameValue, int endGameValue, double currentGameProgress) {
        return (int) Math.round((1.0 - currentGameProgress) * middleGameValue + currentGameProgress * endGameValue);
    }

    public static int blend256(int mg, int eg, int phase256) {
        int mgW = 256 - phase256;
        // rounding + shift; all int arithmetic, no double/Math
        return ((mg * mgW) + (eg * phase256) + 128) >> 8;
    }

    public static int toPhase256(double gameProgress) {
        // clamp just in case
        if (gameProgress <= 0) return 0;
        if (gameProgress >= 1) return 256;
        return (int)(gameProgress * 256.0 + 0.5);
    }
}
