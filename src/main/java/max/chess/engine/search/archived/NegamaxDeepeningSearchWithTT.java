package max.chess.engine.search.archived;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.search.SearchResult;
import max.chess.engine.search.evaluator.GameValues;
import max.chess.engine.search.evaluator.PositionEvaluator;
import max.chess.engine.search.transpositiontable.TranspositionTable;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.ColorUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static max.chess.engine.search.SearchConstants.INF;
import static max.chess.engine.search.SearchConstants.MAX_MOVES;
import static max.chess.engine.search.SearchConstants.MAX_PLY;

public class NegamaxDeepeningSearchWithTT {
    private static final int DEFAULT_TT_SIZE = 64; // In MB
    private static final boolean DEFAULT_USE_TT = true; // In MB
    private static final boolean USE_TT      ;   // global flag for TT
    private static final boolean USE_TT_BOUNDS      = true;   // A/B: disable bound tightening, keep only ttMove ordering
    private static final boolean STORE_EXACT_ONLY   = false;  // A/B: store only TT_EXACT entries
    private static final boolean TIGHT_STORE_BOUNDS = false;   // store tighter cut scores instead of alphaOrig/beta

    // Reusable move buffers per ply to avoid allocations (still “simple”).
    private static final int[][] MOVE_BUF = new int[MAX_PLY][MAX_MOVES];
    private static final long[][] UNDOMOVE_BUF = new long[MAX_PLY][MAX_MOVES];

    // Principal variation storage
    private static final int[][] PV    = new int[MAX_PLY][MAX_PLY];
    private static final int[]   PVLEN = new int[MAX_PLY];

    // Transposition table
    private static final TranspositionTable tt; // 64 MB table
//    private static final TranspositionTable.Hit ttHit = new TranspositionTable.Hit();

    private static long nodes;
    private static long totalNodes;
    private static int currentDepth = 1;

    static {
        MoveGenerator.warmUp();
        boolean shouldUseTT = DEFAULT_USE_TT;
        String overridenTTUsage = System.getProperty("tt.enabled");
        if(overridenTTUsage != null) {
            shouldUseTT = Boolean.parseBoolean(overridenTTUsage);
        }

        if(shouldUseTT) {
            int actualTableSizeInMB = DEFAULT_TT_SIZE;
            String overriddenTableSize = System.getProperty("tt.size");
            if (overriddenTableSize != null) {
                actualTableSizeInMB = Integer.parseInt(overriddenTableSize);
            }
            tt = new TranspositionTable(actualTableSizeInMB);
            System.err.println("Transposition table enabled with " + actualTableSizeInMB + " MB.");
        } else {
            tt = null;
            System.err.println("Transposition table disabled.");
        }

        USE_TT = shouldUseTT;
    }

    public static void init() {
        // To init the static block
        if(USE_TT) {
            tt.clear();
        }
    }

    public static SearchResult pickNextMove(Game game, AtomicBoolean stopFlag, UciServer.GoParams go, Consumer<String> sinkConsumer) {
        return pickNextMoveWithNegamax(game, stopFlag, go, sinkConsumer);
    }

    private static SearchResult pickNextMoveWithNegamax(Game game, AtomicBoolean stopFlag, UciServer.GoParams go,
                                                        Consumer<String> sinkConsumer) {
        boolean isWhiteTurn = ColorUtils.isWhite(game.currentPlayer);
        totalNodes = 0;
        long maxAllowedTimeNanos = go.movetime;
        if(maxAllowedTimeNanos != -1) {
            // Reducing from 10ms of the allowed time to account for the overhead of cancelling for short time controls
            maxAllowedTimeNanos = maxAllowedTimeNanos - 10;
        }

        if(maxAllowedTimeNanos == -1) {
            maxAllowedTimeNanos = isWhiteTurn ? go.wtime : go.btime;
            if(maxAllowedTimeNanos != -1) {
                // Reducing to 2% of the remaining time
                maxAllowedTimeNanos = 2 * maxAllowedTimeNanos / 100;
            }
        }

        if(maxAllowedTimeNanos == -1) {
            // No time provided ; we default to 2seconds
            maxAllowedTimeNanos = Duration.ofSeconds(2).toNanos();
        } else {
            maxAllowedTimeNanos = maxAllowedTimeNanos * 1000 * 1000; // -> to nanos
        }
        long startTime = System.nanoTime();
        currentDepth = 1;
        int maxDepth = go.depth == -1 ? Integer.MAX_VALUE : go.depth;

        if(USE_TT) {
            tt.newSearch();
            tt.resetCounters();
        }
        SearchResult lastResult = null;
        while(currentDepth <= maxDepth && !stopFlag.get() && System.nanoTime() - startTime < maxAllowedTimeNanos) {
            System.err.println("Starting search at depth " + currentDepth);
            SearchResult result = search(game, currentDepth, stopFlag, startTime, maxAllowedTimeNanos, sinkConsumer);
            if(result != null && result.move() != 0) {
                lastResult = result;
            }
            currentDepth++;
        }
        return lastResult;
    }

    /** Run a single fixed-depth search and return best move + score. */
    public static SearchResult search(Game game, int depth, AtomicBoolean stopFlag,
                                      long startTime, long maximumAllowedTimeNanos, Consumer<String> sinkConsumer) {
        long z0 = game.zobristKey();
        nodes = 0;
        // reset PV lengths
        Arrays.fill(PVLEN, 0);

        if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
            // Cancelling the search
            return null;
        }

        long t0 = System.nanoTime();

        // root: pick the best child by calling negamax on each
        int[] moves = MOVE_BUF[0];
        int n = game.getLegalMoves(moves);
        if (n == 0 && !game.inCheck()) throw new IllegalStateException("No legal moves at root but not in check");

        int bestMove = 0;
        int bestScore = -INF;
        int alpha = -INF, beta = INF;

        boolean chose = false;
        boolean aborted = false;

        if (n == 0) {
            if(game.isADraw()) {
                bestScore = 0;
            } else {
                // If we reach here it means it's either a mate or a pat
                bestScore = game.inCheck() ? -mateScore(0) : 0; // terminal node
            }
        } else {
            for (int i = 0; i < n; i++) {
                if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
                    // Cancelling the search
                    return null;
                }
                int move = moves[i];
                long gameChanges = game.playMove(move);
                int score = -negamax(game, depth - 1, 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                game.undoMove(gameChanges);
                if(score == Integer.MIN_VALUE) {
                    // we need to break
                    return null;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;
                    chose = true;


                    // Build root PV from child PV
                    // root PV update
                    PV[0][0] = move;
                    PVLEN[0] = 1 + PVLEN[1];
                    System.arraycopy(PV[1], 0, PV[0], 1, PVLEN[1]);
                }
                if (bestScore > alpha) alpha = bestScore; // keep a narrow window at root too
            }
        }

        // If we aborted this iteration before picking any move, signal caller to keep previous result
        if (aborted && !chose) return null;

        // paranoid fallback if all children aborted due to time
        if (!chose) {
            for (int i = 0; i < n; i++) if (moves[i] != 0) { bestMove = moves[i]; break; }
        }

        long tFinal = System.nanoTime();
//        long elapsedNs = tFinal - t0;
        long totalElapsedNs = tFinal - startTime;
//        long timeMs = Math.max(1, elapsedNs / 1_000_000);         // avoid div-by-zero
        long totalTimeMs = Math.max(1, totalElapsedNs / 1_000_000);         // avoid div-by-zero
//        long nps    = (nodes * 1000L) / timeMs;                   // nodes per second
        long totalNps    = (totalNodes * 1000L) / totalTimeMs;                   // nodes per second
        bestMove = sanitizeBestMove(game, bestMove);  // do this first

        int rawLen = Math.min(PVLEN[0], MAX_PLY);
        int legalLen = legalizePV(game, PV[0], rawLen);
        int[] pvLine;
        if (legalLen == 0 || PV[0][0] != bestMove) {
            // If the root move differs after sanitize, show a 1-move PV (always legal)
            pvLine = new int[] { bestMove };
        } else {
            pvLine = Arrays.copyOf(PV[0], legalLen);
        }

        if(USE_TT) {
            sinkConsumer.accept(tt.snapshot().toInfoStringForUCI(tt));
        }

        long z1 = game.zobristKey();
        if (z0 != z1) {
            throw new IllegalStateException("Position mutated across search; make/unmake leak or PV extractor bug");
        }

        if (bestMove == 0 && n > 0) {
            throw new IllegalStateException("Root has legal moves but sanitize failed");
        }
        final int[] rootMoves = MOVE_BUF[0];
        int rootN = game.getLegalMoves(rootMoves);

        boolean inSet = false;
        for (int i = 0; i < rootN; i++) if (rootMoves[i] == bestMove) { inSet = true; break; }
        if (!inSet) {
            sinkConsumer.accept("string [ILLEGAL_OUT] bestMove not in legal set. n=" + rootN
                    + " zobrist=" + Long.toHexString(game.zobristKey())
                    + " bestMovePacked=" + bestMove);
            // dump the first 10 legal moves in both packed and UCI form
        }

        return new SearchResult(bestMove, bestScore, totalNodes, totalTimeMs, totalNps, pvLine);
    }


    /** Core negamax with alpha-beta. */
    private static int negamax(Game game, int depth, int ply, int alpha, int beta, AtomicBoolean stopFlag, long startTime, long maximumAllowedTimeNanos) {
        if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
            // Cancelling the search
            return Integer.MIN_VALUE;
        }
        final int alphaOrig = alpha;  // keep the original window
        boolean cut = false;
        final long key = game.zobristKey();

        TranspositionTable.Hit ttHit = USE_TT ? new TranspositionTable.Hit() : null;
        if(USE_TT) {
            // TT probe

            // Gate TT bound tightening by depth
            boolean useBounds = USE_TT_BOUNDS && depth >= 3; // try 3..4
            if (useBounds) {
                if (tt.probe(key, depth, ply, ttHit)) {
                    // exact bound
                    if (ttHit.flag == TranspositionTable.TT_EXACT) {
                        PVLEN[ply] = 0;
                        tt.countCutoff();           // cutoff purely from TT
                        return ttHit.score;
                    } else if (ttHit.flag == TranspositionTable.TT_LOWER) {
                        if (ttHit.score > alpha) alpha = ttHit.score;
                        if (alpha >= beta) {
                            PVLEN[ply] = 0;
                            tt.countCutoff();
                            return ttHit.score;
                        }
                    } else { // UPPER
                        if (ttHit.score < beta) beta = ttHit.score;
                        if (alpha >= beta) {
                            PVLEN[ply] = 0;
                            tt.countCutoff();
                            return ttHit.score;
                        }
                    }
                }
            }
        }

        nodes++;
        totalNodes++;

        if (game.isADraw()) {
            PVLEN[ply] = 0;
            return 0;
        }
        if (depth == 0) {
            PVLEN[ply] = 0;                 // leaf contributes no moves
            return getStaticEvaluation(game);
        }

        int[] moves = MOVE_BUF[ply];
        int n = game.getLegalMoves(moves);

        if (n == 0) {
            PVLEN[ply] = 0;                 // terminal node: no moves
            // If we reach here it means it's either a mate or a pat
            return game.inCheck() ? -mateScore(ply) : 0; // terminal node
        }

        int best = -INF;

        int bestMove = 0;

        if (USE_TT) {
            // If we didn’t probe for bounds (depth < 3), still get a fresh hint for THIS node
            if (!(USE_TT_BOUNDS && depth >= 3)) {
                tt.probe(key, /*reqDepth*/0, ply, ttHit);  // ignore returned boolean; we only want hint.move
            }
            int ttMove = ttHit.move;
            if (ttMove != 0) {
                boolean trust = (ttHit.depth >= depth) || (ttHit.depth >= depth - 1 && ttHit.flag != TranspositionTable.TT_UPPER);
                if (trust) {
                    int idx = -1;
                    for (int i = 0; i < n; i++) if (moves[i] == ttMove) { idx = i; break; }
                    if (idx > 0) { int t = moves[0]; moves[0] = moves[idx]; moves[idx] = t; }
                }
            }
        }

        for (int i = 0; i < n; i++) {
            int move = moves[i];
            long gameChanges = game.playMove(move);
            int score = -negamax(game, depth - 1, ply + 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
            game.undoMove(gameChanges);
            if (score == Integer.MIN_VALUE) {
                // we need to break
                return Integer.MIN_VALUE;
            }

            if (score > best) {
                best = score;
                bestMove = move;

                PV[ply][0] = move;
                PVLEN[ply] = 1 + PVLEN[ply + 1];
                System.arraycopy(PV[ply + 1], 0, PV[ply], 1, PVLEN[ply + 1]);
            }
            if (best > alpha) alpha = best;
            if (alpha >= beta) {
                cut = true;
                break; // cut
            }
        }

        if(bestMove != 0) {
            // Decide the TT flag with alphaOrig / beta and the cut flag
            byte flag;
            int storeScore;
            if (cut) {                       // fail-high
                flag = TranspositionTable.TT_LOWER;
                storeScore = TIGHT_STORE_BOUNDS ? best : beta;
            } else if (best <= alphaOrig) {  // fail-low
                flag = TranspositionTable.TT_UPPER;
                storeScore = TIGHT_STORE_BOUNDS ? best : alphaOrig;
            } else {                         // exact
                flag = TranspositionTable.TT_EXACT;
                storeScore = best;
            }

            boolean store = USE_TT && depth > 1;
            if(store) {
                boolean storeExactOnly = STORE_EXACT_ONLY || depth <= 2; // small depths: exacts only
                if (storeExactOnly) {
                    if (flag == TranspositionTable.TT_EXACT) {
                        tt.store(key, bestMove, depth, best, TranspositionTable.TT_EXACT, ply);
                    }
                    return best;
                }
                tt.store(key, bestMove, depth, storeScore, flag, ply);
            }
        }

        return best;
    }

    private static int getStaticEvaluation(Game game) {
        int standPat;
        if (USE_TT) {
            TranspositionTable.IntRef se = new TranspositionTable.IntRef();
            if (tt.probeSE(game.zobristKey(), se)) {
                standPat = se.value;
                // (Optional) stats/log: “SE hit”
            } else {
                standPat = PositionEvaluator.evaluatePosition(game);
                tt.storeSE(game.zobristKey(), standPat);
            }
        } else {
            standPat = PositionEvaluator.evaluatePosition(game);
        }

        return standPat;
    }

    private static int mateScore(int ply) { return GameValues.CHECKMATE_VALUE - ply; } // distance-to-mate packing for terminals

    private static int legalizePV(Game game, int[] pv, int maxLen) {
        int len = 0;
        long[] undo = new long[Math.min(maxLen, MAX_PLY)];
        for (int i = 0; i < maxLen; i++) {
            int mv = pv[i];
            int[] buf = MOVE_BUF[len];
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

    private static int sanitizeBestMove(Game game, int bestMove) {
        final int[] buf = MOVE_BUF[0];
        final int n = game.getLegalMoves(buf);
        if (n == 0) return 0;

        // exact match?
        for (int i = 0; i < n; i++) if (buf[i] == bestMove) return bestMove;

        if(USE_TT) {
            // try TT hint if present and legal
            int ttMv = tt.peekMove(game.zobristKey());
            if (ttMv != 0) {
                for (int i = 0; i < n; i++) if (buf[i] == ttMv) return ttMv;
            }
        }

        // give up and play first legal
        return buf[0];
    }

}
