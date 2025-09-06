package max.chess.engine.search;

import max.chess.engine.uci.UciServer;

final class TimeControl {
    static long computeBudgetNs(boolean whiteToMove, UciServer.GoParams go, int requestedDepth, SearchConfig cfg) {
        long ms = go.movetime;
        if (ms != -1) {
            if (ms <= 10) ms = 10;
            else ms -= 10;
            return ms * 1_000_000L;
        }
        ms = whiteToMove ? go.wtime : go.btime;
        if (ms != -1) return (2L * ms / 100L) * 1_000_000L; // 2% of remaining
        // No movetime, no wtime/btime:
        // Original behavior: if depth is specified, allow a big safety cap; else small default.
        if (requestedDepth != -1) {
            return cfg.maxHardCapNs; // e.g., 120s in config
        } else {
            return cfg.defaultNoTimeNs; // e.g., 2s
        }
    }
    static boolean aborted(java.util.concurrent.atomic.AtomicBoolean stop, long startNs, long budgetNs) {
        return stop.get() || System.nanoTime() - startNs >= budgetNs;
    }
}
