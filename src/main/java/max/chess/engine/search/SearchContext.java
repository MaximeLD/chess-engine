package max.chess.engine.search;

import max.chess.engine.search.evaluator.PawnEval;
import max.chess.engine.search.transpositiontable.TranspositionTable;

import static max.chess.engine.search.SearchConstants.*;

public final class SearchContext {
    // Buffers per ply (qsearch can go deeper than MAX_PLY)
    public final int[][] moveBuf  = new int[SearchConstants.STACK_PLY][SearchConstants.MAX_MOVES];
    public final int[][] scoreBuf = new int[SearchConstants.STACK_PLY][SearchConstants.MAX_MOVES];

    // PV stays limited to MAX_PLY
    public final int[][] pv = new int[SearchConstants.MAX_PLY][SearchConstants.MAX_PLY];
    public final int[] pvLen = new int[SearchConstants.MAX_PLY];
    public final long[] keyStack = new long[SearchConstants.MAX_PLY];

    // Heuristics at search ply only
    public final int[][][] history = new int[2][64][64];
    public final int[][] killer = new int[SearchConstants.MAX_PLY][2];

    // Countermove table and Continuation History
    // countermove[prevFrom][prevTo] = best reply move (packed int)
    public final int[][] countermove = new int[64][64];
    // contHistory[side][prevTo][currTo] = quiet follow-up bonus
    public final int[][][] contHistory = new int[2][64][64];

    // Track previous move per ply so each node knows the opponent's last move
    public final int[] prevMove = new int[SearchConstants.STACK_PLY];

    public boolean rootIsWhite;

    // Counters
    public long nodes, totalNodes, qNodes;
    public int currentDepth;

    public long nmpTried, nmpCut, nmpVerify, nmpVerifyFail;
    public long lmrTried, lmrReduced, lmrResearched, lmrWidened;
    public long iidTried, iidUsed;
    public long probCutTried, probCutCut;

    // TT
    public final TranspositionTable tt; // nullable if disabled

    // Config
    public final SearchConfig cfg;

    public SearchContext(SearchConfig cfg) {
        this.cfg = cfg;
        this.tt = cfg.useTT ? new TranspositionTable(cfg.ttSizeMb) : null;
    }

    public void resetDiag() {
        nmpTried = nmpCut = nmpVerify = nmpVerifyFail = 0;
        lmrTried = lmrReduced = lmrResearched = lmrWidened = 0;
        iidTried = iidUsed = 0;
        probCutTried = probCutCut = 0;
    }

    public void newSearch() {
        nodes = totalNodes = qNodes = 0;
        for (int p = 0; p < SearchConstants.MAX_PLY; p++) { killer[p][0] = killer[p][1] = 0; pvLen[p]=0; }
        for (int s = 0; s < 2; s++) for (int f = 0; f < 64; f++) java.util.Arrays.fill(history[s][f], 0);
        for (int f = 0; f < 64; f++) java.util.Arrays.fill(countermove[f], 0);
        for (int s = 0; s < 2; s++) for (int t = 0; t < 64; t++) java.util.Arrays.fill(contHistory[s][t], 0);
        java.util.Arrays.fill(prevMove, 0);
        if (tt != null) { tt.newSearch(); tt.resetCounters(); }
        PawnEval.newSearch();
    }

    public String toUCIInfo(int depth) {
        return String.format(
                "info string diag depth %d nodes %d qnodes %d " +
                        "| nmp tried %d cut %d verify %d fail %d " +
                        "| lmr tried %d reduced %d re-search %d widened %d" +
                        "| iid tried %d used %d" +
                        "| probcut tried %d cut %d",
                depth, totalNodes, qNodes,
                nmpTried, nmpCut, nmpVerify, nmpVerifyFail,
                lmrTried, lmrReduced, lmrResearched, lmrWidened,
                iidTried, iidUsed,
                probCutTried, probCutCut
                );
    }
}
