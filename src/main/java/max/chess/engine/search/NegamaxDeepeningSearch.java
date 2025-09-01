package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.search.evaluator.GameValues;
import max.chess.engine.search.evaluator.PositionEvaluator;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.ColorUtils;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class NegamaxDeepeningSearch {
    private static final int MAX_PLY = 128;
    private static final int INF     =  30000;

    // Reusable move buffers per ply to avoid allocations (still “simple”).
    private static final int MAX_MOVES = 256;
    private static final int[][] MOVE_BUF = new int[MAX_PLY][MAX_MOVES];

    // Principal variation storage
    private static final int[][] PV    = new int[MAX_PLY][MAX_PLY];
    private static final int[]   PVLEN = new int[MAX_PLY];

    private static long nodes;
    private static long totalNodes;
    private static int currentDepth = 1;

    static {
        MoveGenerator.warmUp();
    }

    public static SearchResult pickNextMove(Game game, AtomicBoolean stopFlag, UciServer.GoParams go) {
        return pickNextMoveWithNegamax(game, stopFlag, go);
    }

    private static SearchResult pickNextMoveWithNegamax(Game game, AtomicBoolean stopFlag, UciServer.GoParams go) {
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

        SearchResult lastResult = null;
        while(currentDepth <= maxDepth && !stopFlag.get() && System.nanoTime() - startTime < maxAllowedTimeNanos) {
            System.err.println("Starting search at depth " + currentDepth);
            SearchResult result = search(game, currentDepth, stopFlag, startTime, maxAllowedTimeNanos);
            if(result != null) {
                lastResult = result;
            }
            currentDepth++;
        }
        return lastResult;
    }

    /** Run a single fixed-depth search and return best move + score. */
    public static SearchResult search(Game game, int depth, AtomicBoolean stopFlag, long startTime, long maximumAllowedTimeNanos) {
        nodes = 0;

        for (int i = 0; i < MAX_PLY; i++) PVLEN[i] = 0;  // reset PV lengths

        if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
            // Cancelling the search
            return null;
        }

        long t0 = System.nanoTime();

        // root: pick the best child by calling negamax on each
        int[] moves = MOVE_BUF[0];
        int n = game.getLegalMoves(moves);

        int bestMove = moves[0];
        int bestScore = -INF;
        int alpha = -INF, beta = INF;

        if (n == 0) {
            bestScore = game.inCheck() ? -mateScore(0) : 0; // checkmate/stalemate
        } else {
            for (int i = 0; i < n; i++) {
                if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
                    // Cancelling the search
                    return null;
                }
                int move = moves[i];
                long gameChanges = game.playMove(move);
                int score = -negamax(game, depth - 1, 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                if(score == Integer.MIN_VALUE) {
                    // we need to break
                    return null;
                }
                game.undoMove(gameChanges);

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = move;

                    // Build root PV from child PV
                    PV[0][0] = move;
                    int childLen = PVLEN[1];
                    if (childLen > 1) {
                        System.arraycopy(PV[1], 1, PV[0], 1, childLen - 1);
                        PVLEN[0] = childLen;
                    } else {
                        PVLEN[0] = 1; // just the root move
                    }
                }
                if (bestScore > alpha) alpha = bestScore; // keep a narrow window at root too
            }
        }

        long tFinal = System.nanoTime();
//        long elapsedNs = tFinal - t0;
        long totalElapsedNs = tFinal - startTime;
//        long timeMs = Math.max(1, elapsedNs / 1_000_000);         // avoid div-by-zero
        long totalTimeMs = Math.max(1, totalElapsedNs / 1_000_000);         // avoid div-by-zero
//        long nps    = (nodes * 1000L) / timeMs;                   // nodes per second
        long totalNps    = (totalNodes * 1000L) / totalTimeMs;                   // nodes per second

        int pvLen = Math.max(0, Math.min(PVLEN[0], MAX_PLY));
        int[] pvLine = new int[pvLen];
        if (pvLen > 0) System.arraycopy(PV[0], 0, pvLine, 0, pvLen);

        return new SearchResult(bestMove, bestScore, totalNodes, totalTimeMs, totalNps, pvLine);
    }


    /** Core negamax with alpha-beta. */
    private static int negamax(Game game, int depth, int ply, int alpha, int beta, AtomicBoolean stopFlag, long startTime, long maximumAllowedTimeNanos) {
        if(stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos) {
            // Cancelling the search
            return Integer.MIN_VALUE;
        }

        nodes++;
        totalNodes++;

        if (depth == 0) {
            PVLEN[ply] = ply;                 // PV at a leaf ends here
            // Simple leaf: static eval from side-to-move POV
            return PositionEvaluator.evaluatePosition(game);
        }

        int[] moves = MOVE_BUF[ply];
        int n = game.getLegalMoves(moves);

        if (n == 0) {
            PVLEN[ply] = ply;                 // terminal node: no moves
            return game.inCheck() ? -mateScore(ply) : 0; // terminal node
        }

        int best = -INF;

        for (int i = 0; i < n; i++) {
            int move = moves[i];
            long gameChanges = game.playMove(move);
            int score = -negamax(game, depth - 1, ply + 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
            game.undoMove(gameChanges);
            if(score == Integer.MIN_VALUE) {
                // we need to break
                return Integer.MIN_VALUE;
            }

            if (score > best) {
                best = score;
                // Update PV: current move + child's PV
                PV[ply][ply] = move;
                int childLen = PVLEN[ply + 1];
                if (childLen > ply + 1) {
                    System.arraycopy(PV[ply + 1], ply + 1, PV[ply], ply + 1, childLen - (ply + 1));
                    PVLEN[ply] = childLen;
                } else {
                    PVLEN[ply] = ply + 1;
                }
            }
            if (best > alpha) alpha = best;
            if (alpha >= beta) break; // cut
        }
        return best;
    }

    private static int mateScore(int ply) { return GameValues.CHECKMATE_VALUE - ply; } // distance-to-mate packing for terminals

}
