package max.chess.engine.search;

import max.chess.engine.movegen.Move;
import max.chess.engine.utils.ColorUtils;

/** Late Move Reductions: table + gating in one place. */
final class LMR {
    // Reasonable caps; adjust if you routinely search deeper.
    private static final int D_MAX = 64;   // depth cap for table
    private static final int M_MAX = 64;   // move-index cap for table

    /** R[depth][moveIndex] baseline reductions (>=0). moveIndex is 1-based in the table. */
    static final int[][] R = buildTable();

    private LMR() {}

    private static int[][] buildTable() {
        int[][] t = new int[D_MAX + 1][M_MAX + 1];
        // Typical scaling; tune divisor (2.25..3.0). Lower => stronger reductions.
        final double scale = 2.70;
        for (int d = 1; d <= D_MAX; d++) {
            for (int m = 1; m <= M_MAX; m++) {
                double rd = Math.log(d + 1.0) * Math.log(m + 1.0) / scale;
                int r = (int) Math.floor(rd);
                if (r < 1) r = 1;  // baseline reductions start at 1 for late-enough moves
                t[d][m] = r;
            }
        }
        return t;
    }

    /** Compute a reduction with common-sense gates and caps. */
    static int suggestReduction(
            int depth,
            int globalIndex,     // i in [0..moveCount)
            int capCount,        // captures are [0..capCount)
            boolean isPV,
            boolean inCheck,
            boolean inNullMove,
            boolean isCapture,
            boolean givesCheck,
            boolean isKiller,
            boolean isTTTrusted,
            int historyScore,
            boolean immediateBounceBack,   // quiet A→B immediately returning B→A
            boolean isRecapture,           // capturing back on last captured square
            SearchConfig cfg
    ) {
        // Hard gates
        if (inCheck || inNullMove) return 0;
        if (depth < cfg.lmrMinDepth) return 0;

        // First PV move is never reduced
        if (isPV && globalIndex == 0) return 0;

        // Segment-local index: quiet #1 should not be punished because many captures existed
        final int segIndex = isCapture ? globalIndex : Math.max(0, globalIndex - capCount);

        if (segIndex < cfg.lmrMinMove) return 0;

        // Lookup baseline reduction
        final int d = clamp(depth, 1, D_MAX);
        final int m = clamp(segIndex + 1, 1, M_MAX);
        int r = R[d][m];

        // Captures and checks are reduced more conservatively
        if (isRecapture) {
            r = 0; // never reduce recaptures
        } else if (isCapture) {
            if (!cfg.lmrReduceCaptures) r = 0;
            else r = Math.max(0, r - 1); // soften by 1
        }
        if (givesCheck) {
            if (!cfg.lmrReduceChecks) r = 0;
            else r = Math.max(0, r - 1);
        }

        // Don’t reduce trusted TT or killer moves if configured
        if (isTTTrusted && cfg.lmrNoReduceTTTrusted) r = 0;
        if (isKiller     && cfg.lmrNoReduceKiller)   r = 0;

        // History gating: skip or soften for high-history moves
        if (historyScore >= cfg.lmrHistorySkip) r = 0;
        else if (historyScore <= cfg.lmrHistoryPunish) r += 1; // optional nudge for “bad” history

        // Extra reduction for immediate bounce-backs (quiet A→B then B→A)
        if (cfg.bumpLMROnImmediateRepetition && immediateBounceBack && !isCapture) {
            r += Math.max(0, cfg.immRepLMRBump); // typically +1
        }

        /* ---- REASSERT DOMINATING NO-REDUCE GUARDS ---- */
        if (isRecapture) r = 0;
        if (isTTTrusted && cfg.lmrNoReduceTTTrusted) r = 0;
        if (isKiller     && cfg.lmrNoReduceKiller)   r = 0;
        if (historyScore >= cfg.lmrHistorySkip)      r = 0;
        if (givesCheck && !cfg.lmrReduceChecks)      r = 0;
        if (isCapture  && !cfg.lmrReduceCaptures)    r = 0;
        /* ---------------------------------------------- */

        // Shallow depth clamps (avoid suiciding lines too early)
        if (depth <= 6)  r = Math.min(r, 1);
        else if (depth <= 8) r = Math.min(r, 2);

        // PV cap: allow tiny reductions late in PV only
        if (isPV) r = Math.min(r, cfg.lmrPVMaxReduction); // typically 1
        // Global caps
        if (r > cfg.lmrMax) r = cfg.lmrMax;
        if (r > depth - 1)  r = depth - 1;
        return Math.max(0, r);
    }

    private static int clamp(int v, int lo, int hi) { return (v < lo) ? lo : Math.min(v, hi); }
}
