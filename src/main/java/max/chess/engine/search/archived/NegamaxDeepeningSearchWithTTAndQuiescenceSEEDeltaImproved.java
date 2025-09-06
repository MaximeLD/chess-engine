package max.chess.engine.search.archived;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.Board;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.movegen.pieces.Bishop;
import max.chess.engine.movegen.pieces.King;
import max.chess.engine.movegen.pieces.Knight;
import max.chess.engine.movegen.pieces.Rook;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import max.chess.engine.search.SearchResult;
import max.chess.engine.search.evaluator.GameValues;
import max.chess.engine.search.evaluator.PawnEval;
import max.chess.engine.search.evaluator.PieceValues;
import max.chess.engine.search.evaluator.PositionEvaluator;
import max.chess.engine.search.transpositiontable.TranspositionTable;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.PieceUtils;

import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static max.chess.engine.search.SearchConstants.INF;
import static max.chess.engine.search.SearchConstants.MAX_MOVES;
import static max.chess.engine.search.SearchConstants.MAX_PLY;

public class NegamaxDeepeningSearchWithTTAndQuiescenceSEEDeltaImproved {
    private static final int DEFAULT_TT_SIZE = 64; // In MB
    private static final boolean DEFAULT_USE_TT = true; // In MB
    private static final boolean USE_TT      ;   // global flag for TT
    private static final boolean USE_TT_BOUNDS      = true;   // A/B: disable bound tightening, keep only ttMove ordering
    private static final boolean STORE_EXACT_ONLY   = false;  // A/B: store only TT_EXACT entries
    private static final boolean TIGHT_STORE_BOUNDS = true;   // store tighter cut scores instead of alphaOrig/beta

    // Reusable move buffers per ply to avoid allocations (still “simple”).
    private static final int[][] SCORE_BUF = new int[MAX_PLY][MAX_MOVES];
    private static final int[][] MOVE_BUF = new int[MAX_PLY][MAX_MOVES];
    private static final long[][] UNDOMOVE_BUF = new long[MAX_PLY][MAX_MOVES];

    // Principal variation storage
    private static final int[][] PV    = new int[MAX_PLY][MAX_PLY];
    private static final int[]   PVLEN = new int[MAX_PLY];

    // history[side][from][to] small ints
    private static final int[][][] HISTORY = new int[2][64][64];
    private static final int[][] KILLER = new int[MAX_PLY][2];

    // Transposition table
    private static final TranspositionTable tt; // 64 MB table
//    private static final TranspositionTable.Hit ttHit = new TranspositionTable.Hit();

    private static long nodes;
    private static long totalNodes;
    private static long quiesceNodes;
    private static int currentDepth = 1;

    static {
        MoveGenerator.warmUp();
        PositionEvaluator.warmUp();
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
        // Special-case: depth 0 → static eval; no iterative deepening
        if (go.depth == 0 || go.staticEvalOnly) {
            return staticEvalOnly(game, sinkConsumer);
        }

        SearchResult searchResult = pickNextMoveWithNegamax(game, stopFlag, go, sinkConsumer);
        sinkConsumer.accept(PawnEval.PAWN_HASH.toUCIInfo());
        return searchResult;
    }

    private static SearchResult pickNextMoveWithNegamax(Game game, AtomicBoolean stopFlag, UciServer.GoParams go,
                                                        Consumer<String> sinkConsumer) {
        for (int p=0;p<MAX_PLY;p++){ KILLER[p][0]=KILLER[p][1]=0; }
        for (int s=0;s<2;s++) for (int f=0;f<64;f++) Arrays.fill(HISTORY[s][f], 0);
        boolean isWhiteTurn = ColorUtils.isWhite(game.currentPlayer);
        totalNodes = 0;
        quiesceNodes = 0;
        long maxAllowedTimeNanos = go.movetime;
        if(maxAllowedTimeNanos != -1) {
            // Reducing from 10ms of the allowed time to account for the overhead of cancelling for short time controls
            if (maxAllowedTimeNanos <= 10) maxAllowedTimeNanos = 10;  // don’t go negative
            else maxAllowedTimeNanos -= 10;
        }

        if(maxAllowedTimeNanos == -1) {
            maxAllowedTimeNanos = isWhiteTurn ? go.wtime : go.btime;
            if(maxAllowedTimeNanos != -1) {
                // Reducing to 2% of the remaining time
                maxAllowedTimeNanos = 2 * maxAllowedTimeNanos / 100;
            }
        }

        int maxDepth = go.depth == -1 ? Integer.MAX_VALUE : go.depth;

        if(maxAllowedTimeNanos == -1) {
            if(maxDepth == Integer.MAX_VALUE) {
                // No time provided ; we default to 2seconds
                maxAllowedTimeNanos = Duration.ofSeconds(2).toNanos();
            } else {
                // Safety net - ideally let the go depth be reached
                maxAllowedTimeNanos = Duration.ofSeconds(120).toNanos();
            }
        } else {
            maxAllowedTimeNanos = maxAllowedTimeNanos * 1000 * 1000; // -> to nanos
        }
        long startTime = System.nanoTime();

        if(USE_TT) {
            tt.newSearch();
            PawnEval.newSearch();
            tt.resetCounters();
        }
        SearchResult lastResult = null;
        int prevScore = 0;
        int ASP = 18; // ~0.18 pawn; tune 12..30

        for (currentDepth = 1; currentDepth <= maxDepth && !aborted(stopFlag, startTime, maxAllowedTimeNanos); currentDepth++) {
            System.err.println("Starting search at depth " + currentDepth);

            // Aspiration window
            int alpha = (currentDepth == 1) ? -INF : prevScore - ASP;
            int beta  = (currentDepth == 1) ?  INF : prevScore + ASP;

            SearchResult r;
            while (true) {
                r = search(game, currentDepth, alpha, beta, stopFlag, startTime, maxAllowedTimeNanos, sinkConsumer);
                if (r == null)  {
                    if (lastResult == null) {
                        int[] root = MOVE_BUF[0];
                        int n = game.getLegalMoves(root);
                        int fallback = (n > 0) ? root[0] : 0;
                        // never output 0000: if n==0 you're checkmated/stalemated; your UCI should handle that.
                        return new SearchResult(fallback, 0, 0, 0, 0, new int[0]);
                    }
                    return lastResult;
                }; // time
                if (r.score() <= alpha) { alpha -= ASP + ASP; }       // fail-low: widen
                else if (r.score() >= beta) { beta += ASP + ASP; }    // fail-high: widen
                else break; // inside window
                if (alpha < -INF/2) alpha = -INF;
                if (beta  >  INF/2) beta  =  INF;
            }
            prevScore = r.score();
            lastResult = r;
        }
//        while(currentDepth <= maxDepth && !stopFlag.get() && System.nanoTime() - startTime < maxAllowedTimeNanos) {
//            SearchResult result = search(game, currentDepth, stopFlag, startTime, maxAllowedTimeNanos, sinkConsumer);
//            if(result != null && result.move() != 0) {
//                lastResult = result;
//            }
//            currentDepth++;
//        }

        if (lastResult == null) {
            int[] root = MOVE_BUF[0];
            int n = game.getLegalMoves(root);
            int fallback = (n > 0) ? root[0] : 0;
            return new SearchResult(fallback, 0, 0, 0, 0, new int[0]);
        }

        return lastResult;
    }

    /** Run a single fixed-depth search and return best move + score. */

    public static SearchResult search(Game game, int depth, int rootAlpha, int rootBeta,
                                      AtomicBoolean stopFlag, long startTime, long maximumAllowedTimeNanos,
                                      Consumer<String> sinkConsumer) {
        long z0 = game.zobristKey();
        nodes = 0;
        // reset PV lengths
        Arrays.fill(PVLEN, 0);

        if(aborted(stopFlag, startTime, maximumAllowedTimeNanos)) {
            // Cancelling the search
            return null;
        }

        long t0 = System.nanoTime();

        int ply = 0;
        // root: pick the best child by calling negamax on each
        int[] moves = MOVE_BUF[0];
        int n = game.getLegalMoves(moves);
        if (n == 0 && !game.inCheck()) throw new IllegalStateException("No legal moves at root but not in check");

        // 2) Partition captures and score captures by MVV-LVA
        int k = orderCapturesMVVLVA(game, moves, n, SCORE_BUF[ply]); // captures in [0..k)

        // 3) Score and sort the quiet tail by history/killers
        int q = k;
        for (int i = q; i < n; i++) SCORE_BUF[ply][i] = scoreQuiet(game, moves[i], ply);
        // simple insertion sort on [q..n)
        for (int i = q + 1; i < n; i++) {
            int m = moves[i], s = SCORE_BUF[ply][i], j = i - 1;
            while (j >= q && SCORE_BUF[ply][j] < s) { moves[j+1] = moves[j]; SCORE_BUF[ply][j+1] = SCORE_BUF[ply][j]; j--; }
            moves[j+1] = m; SCORE_BUF[ply][j+1] = s;
        }

        if (USE_TT) {
            int ttm = tt.peekMove(game.zobristKey());
            if (ttm != 0) {
                for (int i = 0; i < n; i++) if (moves[i] == ttm) {
                    int t = moves[0]; moves[0] = moves[i]; moves[i] = t; break;
                }
            }
        }

        int bestMove = 0;
        int bestScore = -INF;
        int alpha = rootAlpha, beta = rootBeta;

        boolean chose = false;

        if (n == 0) {
            if(game.isADraw()) {
                bestScore = 0;
            } else {
                // If we reach here it means it's either a mate or a pat
                bestScore = game.inCheck() ? -mateScore(0) : 0; // terminal node
            }
            chose = true;
        } else {
            for (int i = 0; i < n; i++) {
                if(aborted(stopFlag, startTime, maximumAllowedTimeNanos)) {
                    // Cancelling the search
                    return null;
                }
                int move = moves[i];
                long gameChanges = game.playMove(move);
                int score;
                if (i == 0) {
                    score = -negamax(game, depth - 1, 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                } else {
                    score = -negamax(game, depth - 1, 1, -(alpha+1), -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                    if (score > alpha && score < beta) {
                        score = -negamax(game, depth - 1, 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                    }
                }
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

        // paranoid fallback if all children aborted due to time
        if (!chose) {
            return null;
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
        if(aborted(stopFlag, startTime, maximumAllowedTimeNanos)) {
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
            boolean useBounds = USE_TT_BOUNDS && depth >= 3;
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
            return quiesce(game, alpha, beta, ply, stopFlag, startTime, maximumAllowedTimeNanos);
        }

        int[] moves = MOVE_BUF[ply];
        int n = game.getLegalMoves(moves);

        // 2) Partition captures and score captures by MVV-LVA
        int k = orderCapturesMVVLVA(game, moves, n, SCORE_BUF[ply]); // captures in [0..k)

        // 3) Score and sort the quiet tail by history/killers
        int q = k;
        for (int i = q; i < n; i++) SCORE_BUF[ply][i] = scoreQuiet(game, moves[i], ply);
        // simple insertion sort on [q..n)
        for (int i = q + 1; i < n; i++) {
            int m = moves[i], s = SCORE_BUF[ply][i], j = i - 1;
            while (j >= q && SCORE_BUF[ply][j] < s) { moves[j+1] = moves[j]; SCORE_BUF[ply][j+1] = SCORE_BUF[ply][j]; j--; }
            moves[j+1] = m; SCORE_BUF[ply][j+1] = s;
        }
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
            int score;
            if (i == 0) {
                score = -negamax(game, depth - 1, ply + 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
            } else {
                // null-window try
                score = -negamax(game, depth - 1, ply + 1, -(alpha+1), -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                if (score > alpha && score < beta) {
                    // re-search only when necessary
                    score = -negamax(game, depth - 1, ply + 1, -beta, -alpha, stopFlag, startTime, maximumAllowedTimeNanos);
                }
            }
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
                boolean isQuiet = Move.getPromotion(move) == PieceUtils.NONE && !isCapture(game, move);
                if (isQuiet) {
                    int side = sideIdx(game.currentPlayer); // after undo, side-to-move reverted
                    HISTORY[side][Move.getStartPosition(move)][Move.getEndPosition(move)] += depth * depth;
                    if (KILLER[ply][0] != move) { KILLER[ply][1] = KILLER[ply][0]; KILLER[ply][0] = move; }
                }
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

            boolean store = USE_TT && depth >= 1;
            if(store) {
                boolean storeExactOnly = STORE_EXACT_ONLY || depth <= 2; // small depths: exacts only
                if (storeExactOnly) {
                    if (flag == TranspositionTable.TT_EXACT) {
                        tt.store(key, bestMove, depth, storeScore, TranspositionTable.TT_EXACT, ply);
                    }
                    return best;
                }
                tt.store(key, bestMove, depth, storeScore, flag, ply);
            }
        }

        return best;
    }

    private static boolean aborted(AtomicBoolean stopFlag, long startTime, long maximumAllowedTimeNanos) {
        return stopFlag.get() || System.nanoTime() - startTime >= maximumAllowedTimeNanos;
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

    private static int quiesce(Game g, int alpha, int beta, int ply,
                               AtomicBoolean stopFlag, long startTime, long maxTimeNanos) {
        if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;

        final long key = g.zobristKey();
        final int alphaOrig = alpha;

        totalNodes++; quiesceNodes++;
        // ----------------------------
        // 0) TT probe for bounds/hint
        // ----------------------------
        TranspositionTable.Hit hit = USE_TT ? new TranspositionTable.Hit() : null;
        if (USE_TT && tt.probe(key, /*reqDepth=*/0, ply, hit)) {
            // Any stored bound is depth-sufficient for qsearch (depth==0).
            // Use it just like at PVS nodes: try cutoffs, else tighten.
            if (hit.flag == TranspositionTable.TT_EXACT) {
                return hit.score; // perfect shortcut
            } else if (hit.flag == TranspositionTable.TT_LOWER) {
                if (hit.score >= beta) return beta;
                if (hit.score > alpha) alpha = hit.score;
            } else { // UPPER
                if (hit.score <= alpha) return alpha;
                if (hit.score < beta) beta = hit.score;
            }
            if (alpha >= beta) return alpha;
        }

        // If in check: no stand-pat; search evasions (full legal set)
        if (g.inCheck()) {
            int[] moves = MOVE_BUF[ply];
            int n = g.getLegalMoves(moves);
            if (n == 0) return -mateScore(ply);
            // Prefer TT move if legal (and typically an evasion)
            if (USE_TT && hit != null && hit.move != 0) {
                int idx = -1;
                for (int i = 0; i < n; i++) if (moves[i] == hit.move) { idx = i; break; }
                if (idx > 0) { int t = moves[0]; moves[0] = moves[idx]; moves[idx] = t; }
            }
            int best = -INF;
            for (int i = 0; i < n; i++) {
                if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;
                long undo = g.playMove(moves[i]);
                int score = -quiesce(g, -beta, -alpha, ply + 1, stopFlag, startTime, maxTimeNanos);
                g.undoMove(undo);
                if (score >= beta) {
                    if (USE_TT) tt.store(key, moves[i], 0, score, TranspositionTable.TT_LOWER, ply);
                    return score;
                }
                if (score > best) best = score;
                if (score > alpha) alpha = score;
            }
            if (USE_TT) {
                byte flag = (best <= alphaOrig) ? TranspositionTable.TT_UPPER
                        : TranspositionTable.TT_EXACT;
                // Store best move if you kept it; otherwise 0 is fine at q-nodes
                tt.store(key, /*move*/0, 0, best, flag, ply);
            }
            return alpha;
        }

        // --------------------------------------------
        // 1) Stand-pat: try TT static eval (or compute)
        // --------------------------------------------
        int standPat = getStaticEvaluation(g);

        if (standPat >= beta) {
            if (USE_TT) tt.store(key, 0, 0, standPat, TranspositionTable.TT_LOWER, ply);
            return standPat;
        }
        if (standPat > alpha) alpha = standPat;

        // ----------------------------------------------------
        // 2) Generate captures (and promotions if you include)
        // ----------------------------------------------------
        int[] moves = MOVE_BUF[ply];
        int n = g.getLegalMoves(moves);

        // Reorder: TT move first if it’s a capture/promo and legal
        if (USE_TT && hit.move != 0 && isTactical(g, hit.move)) {
            int idx = -1;
            for (int i = 0; i < n; i++) if (moves[i] == hit.move) { idx = i; break; }
            if (idx > 0) { int t = moves[0]; moves[0] = moves[idx]; moves[idx] = t; }
        }

        // Keep only tactical moves (captures/promotions). Use your MVV-LVA ordering.
        // k = number of tactical moves kept at front.
        int k = orderCapturesMVVLVA(g, moves, n, SCORE_BUF[ply]); // your helper

        for (int i = 0; i < k; i++) {
            if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;

            int m = moves[i];

            // DON’T prune if it gives check or is a big promotion
            if (!givesCheckFast(g, m) && Move.getPromotion(m) != PieceUtils.QUEEN) {
                // ----------------------------------------------------
                // 3) (Optional) Delta pruning for obviously bad trades
                //    Simple version: if standPat + gainUpperBound + margin <= alpha, skip
                // ----------------------------------------------------
                final int ub = captureUpperBound(g, m);
                if (standPat + ub + 40 <= alpha) continue;             // margin ~2/5 pawn; tune

                // --- SEE pruning:
                // 1) If SEE says the capture loses material and it's not a promotion, skip it.
                // 2) Also use SEE as a tighter delta bound.
                if (ub < PieceValues.ROOK_VALUE) {
                    final int see = seeSwap(g, m);
                    if (Move.getPromotion(m) == PieceUtils.NONE && see < 0) continue;
                    if (standPat + see + 20 <= alpha) continue;
                }
            }

            long undo = g.playMove(m);
            int score = -quiesce(g, -beta, -alpha, ply + 1, stopFlag, startTime, maxTimeNanos);
            g.undoMove(undo);

            if (score >= beta) {
                if (USE_TT) tt.store(key, m, 0, score, TranspositionTable.TT_LOWER, ply);
                return score;
            }
            if (score > alpha) {
                alpha = score;
            }
        }

        // -----------------------------------
        // 4) Store qresult as a bound in the TT
        // -----------------------------------
        if (USE_TT) {
            byte flag = (alpha <= alphaOrig) ? TranspositionTable.TT_UPPER
                    : TranspositionTable.TT_EXACT;
            tt.store(key, /*move*/0, 0, alpha, flag, ply);
        }

        return alpha;
    }

    // promo or capture
    private static boolean isTactical(Game g, int move) {
        long opponentBB = ColorUtils.isWhite(g.currentPlayer) ? g.board().blackBB : g.board().whiteBB;
        return Move.getPromotion(move) != PieceUtils.NONE || (BitUtils.getPositionIndexBitMask(Move.getEndPosition(move)) & opponentBB) != 0;
    }
    private static int captureUpperBound(Game g, int move) {
        final int to = Move.getEndPosition(move);
        final boolean stmIsWhite = ColorUtils.isWhite(g.currentPlayer);

        int capturedType;
        if (Move.isEnPassant(move)) {
            // EP victim sits behind 'to'
            final int epVictimSq = stmIsWhite ? to - 8 : to + 8;
            capturedType = g.board().getPieceTypeAt(epVictimSq);
        } else {
            capturedType = g.board().getPieceTypeAt(to);
        }
        int ub = PieceValues.pieceTypeToValue(capturedType);

        final byte prom = Move.getPromotion(move);
        if (prom != PieceUtils.NONE) {
            ub += PieceValues.pieceTypeToValue(prom) - PieceValues.PAWN_VALUE;
        }
        return ub;
    }

    private static int seeSwap(Game g, int move) {
        final Board b = g.board();
        final boolean whiteSTM = ColorUtils.isWhite(g.currentPlayer);
        final int from = Move.getStartPosition(move);
        final int to   = Move.getEndPosition(move);
        final byte promo = Move.getPromotion(move);

        final int[] VAL = {
                0,
                PieceValues.PAWN_VALUE,
                PieceValues.KNIGHT_VALUE,
                PieceValues.BISHOP_VALUE,
                PieceValues.ROOK_VALUE,
                PieceValues.QUEEN_VALUE,
                PieceValues.KING_VALUE
        };

        long WP = b.pawnBB   & b.whiteBB, BP = b.pawnBB   & b.blackBB;
        long WN = b.knightBB & b.whiteBB, BN = b.knightBB & b.blackBB;
        long WB = b.bishopBB & b.whiteBB, BB = b.bishopBB & b.blackBB;
        long WR = b.rookBB   & b.whiteBB, BR = b.rookBB   & b.blackBB;
        long WQ = b.queenBB  & b.whiteBB, BQ = b.queenBB  & b.blackBB;
        long WK = b.kingBB   & b.whiteBB, BK = b.kingBB   & b.blackBB;

        long occ = b.gameBB;

        // --- victim on 'to' (remove it from its color set right now) ---
        int capturedType;
        if (Move.isEnPassant(move)) {
            int epVictimSq = whiteSTM ? (to - 8) : (to + 8);
            capturedType = PieceUtils.PAWN;
            if (whiteSTM) BP &= ~(1L << epVictimSq); else WP &= ~(1L << epVictimSq);
            occ &= ~(1L << epVictimSq);
        } else {
            capturedType = b.getPieceTypeAt(to);
            if (capturedType != PieceUtils.NONE) {
                long bitTo = 1L << to;
                if (whiteSTM) { // victim is black
                    switch (capturedType) {
                        case PieceUtils.PAWN  -> BP &= ~bitTo;
                        case PieceUtils.KNIGHT-> BN &= ~bitTo;
                        case PieceUtils.BISHOP-> BB &= ~bitTo;
                        case PieceUtils.ROOK  -> BR &= ~bitTo;
                        case PieceUtils.QUEEN -> BQ &= ~bitTo;
                        case PieceUtils.KING  -> BK &= ~bitTo;
                    }
                } else {         // victim is white
                    switch (capturedType) {
                        case PieceUtils.PAWN  -> WP &= ~bitTo;
                        case PieceUtils.KNIGHT-> WN &= ~bitTo;
                        case PieceUtils.BISHOP-> WB &= ~bitTo;
                        case PieceUtils.ROOK  -> WR &= ~bitTo;
                        case PieceUtils.QUEEN -> WQ &= ~bitTo;
                        case PieceUtils.KING  -> WK &= ~bitTo;
                    }
                }
            }
        }

        // remove attacker from 'from'
        {
            long bitFrom = 1L << from;
            int attType = b.getPieceTypeAt(from);
            if (whiteSTM) {
                switch (attType) {
                    case PieceUtils.PAWN  -> WP &= ~bitFrom;
                    case PieceUtils.KNIGHT-> WN &= ~bitFrom;
                    case PieceUtils.BISHOP-> WB &= ~bitFrom;
                    case PieceUtils.ROOK  -> WR &= ~bitFrom;
                    case PieceUtils.QUEEN -> WQ &= ~bitFrom;
                    case PieceUtils.KING  -> WK &= ~bitFrom;
                }
            } else {
                switch (attType) {
                    case PieceUtils.PAWN  -> BP &= ~bitFrom;
                    case PieceUtils.KNIGHT-> BN &= ~bitFrom;
                    case PieceUtils.BISHOP-> BB &= ~bitFrom;
                    case PieceUtils.ROOK  -> BR &= ~bitFrom;
                    case PieceUtils.QUEEN -> BQ &= ~bitFrom;
                    case PieceUtils.KING  -> BK &= ~bitFrom;
                }
            }
            occ &= ~bitFrom;
        }

        // first gain
        int promoDelta = (promo != PieceUtils.NONE) ? (VAL[promo] - VAL[PieceUtils.PAWN]) : 0;
        final int[] gain = new int[32];
        int d = 0;
        gain[0] = VAL[capturedType] + promoDelta;

        // current occupant on 'to' after the first capture
        int curType  = (promo != PieceUtils.NONE) ? promo : b.getPieceTypeAt(from);
        boolean curWhite = whiteSTM;

        // place the mover on 'to'
        {
            long bitTo = 1L << to;
            if (curWhite) {
                switch (curType) {
                    case PieceUtils.PAWN  -> WP |= bitTo;
                    case PieceUtils.KNIGHT-> WN |= bitTo;
                    case PieceUtils.BISHOP-> WB |= bitTo;
                    case PieceUtils.ROOK  -> WR |= bitTo;
                    case PieceUtils.QUEEN -> WQ |= bitTo;
                    case PieceUtils.KING  -> WK |= bitTo;
                }
            } else {
                switch (curType) {
                    case PieceUtils.PAWN  -> BP |= bitTo;
                    case PieceUtils.KNIGHT-> BN |= bitTo;
                    case PieceUtils.BISHOP-> BB |= bitTo;
                    case PieceUtils.ROOK  -> BR |= bitTo;
                    case PieceUtils.QUEEN -> BQ |= bitTo;
                    case PieceUtils.KING  -> BK |= bitTo;
                }
            }
            occ |= bitTo; // 'to' stays occupied
        }

        boolean sideWhite = !whiteSTM;

        while (true) {
            // attackers from the side to move (use current, mutated sets)
            long attP = pawnAttackerMaskTo(to, sideWhite) & (sideWhite ? WP : BP);
            long attN = Knight.getAttackBB(to) & (sideWhite ? WN : BN);
            long attB = Bishop.getAttackBB(to, occ) & (sideWhite ? WB : BB);
            long attR = Rook.getAttackBB(to, occ)   & (sideWhite ? WR : BR);
            long attQ = (attB | attR) & (sideWhite ? WQ : BQ); // queens on opened lines
            long attK = King.getAttackBB(to) & (sideWhite ? WK : BK);

            long attackers = attP | attN | attB | attR | attQ | attK;
            if (attackers == 0) break;

            // least valuable attacker
            int takeType; long fromBB;
            if      (attP != 0) { fromBB = attP & -attP; takeType = PieceUtils.PAWN; }
            else if (attN != 0) { fromBB = attN & -attN; takeType = PieceUtils.KNIGHT; }
            else if (attB != 0) { fromBB = attB & -attB; takeType = PieceUtils.BISHOP; }
            else if (attR != 0) { fromBB = attR & -attR; takeType = PieceUtils.ROOK;   }
            else if (attQ != 0) { fromBB = attQ & -attQ; takeType = PieceUtils.QUEEN;  }
            else                { fromBB = attK & -attK; takeType = PieceUtils.KING;   }

            int fromSq = Long.numberOfTrailingZeros(fromBB);

            gain[++d] = VAL[takeType] - gain[d-1];

            long bitFrom = 1L << fromSq, bitTo = 1L << to;

            // --- remove the previous occupant from 'to' (the one being captured now!) ---
            if (curWhite) {
                switch (curType) {
                    case PieceUtils.PAWN  -> WP &= ~bitTo;
                    case PieceUtils.KNIGHT-> WN &= ~bitTo;
                    case PieceUtils.BISHOP-> WB &= ~bitTo;
                    case PieceUtils.ROOK  -> WR &= ~bitTo;
                    case PieceUtils.QUEEN -> WQ &= ~bitTo;
                    case PieceUtils.KING  -> WK &= ~bitTo;
                }
            } else {
                switch (curType) {
                    case PieceUtils.PAWN  -> BP &= ~bitTo;
                    case PieceUtils.KNIGHT-> BN &= ~bitTo;
                    case PieceUtils.BISHOP-> BB &= ~bitTo;
                    case PieceUtils.ROOK  -> BR &= ~bitTo;
                    case PieceUtils.QUEEN -> BQ &= ~bitTo;
                    case PieceUtils.KING  -> BK &= ~bitTo;
                }
            }

            // move the new capturer onto 'to'
            if (sideWhite) {
                switch (takeType) {
                    case PieceUtils.PAWN  -> { WP &= ~bitFrom; WP |= bitTo; }
                    case PieceUtils.KNIGHT-> { WN &= ~bitFrom; WN |= bitTo; }
                    case PieceUtils.BISHOP-> { WB &= ~bitFrom; WB |= bitTo; }
                    case PieceUtils.ROOK  -> { WR &= ~bitFrom; WR |= bitTo; }
                    case PieceUtils.QUEEN -> { WQ &= ~bitFrom; WQ |= bitTo; }
                    case PieceUtils.KING  -> { WK &= ~bitFrom; WK |= bitTo; }
                }
            } else {
                switch (takeType) {
                    case PieceUtils.PAWN  -> { BP &= ~bitFrom; BP |= bitTo; }
                    case PieceUtils.KNIGHT-> { BN &= ~bitFrom; BN |= bitTo; }
                    case PieceUtils.BISHOP-> { BB &= ~bitFrom; BB |= bitTo; }
                    case PieceUtils.ROOK  -> { BR &= ~bitFrom; BR |= bitTo; }
                    case PieceUtils.QUEEN -> { BQ &= ~bitFrom; BQ |= bitTo; }
                    case PieceUtils.KING  -> { BK &= ~bitFrom; BK |= bitTo; }
                }
            }

            occ &= ~bitFrom;  // 'to' remains occupied

            // update current occupant info
            curType = takeType;
            curWhite = sideWhite;

            sideWhite = !sideWhite;
        }

        while (--d >= 0) gain[d] = -Math.max(-gain[d], gain[d+1]);
        return gain[0];
    }

    // Only the mask of *source* squares from which pawns could attack 'sq'.
    // We AND with current WP/BP in the caller, so do NOT read b.pawnBB here.
    private static long pawnAttackerMaskTo(int sq, boolean white) {
        long bb = 1L << sq;
        if (white) {
            long s1 = (bb >>> 7) & ~OrthogonalMoveUtils.FILES[0];
            long s2 = (bb >>> 9) & ~OrthogonalMoveUtils.FILES[7];
            return s1 | s2;
        } else {
            long s1 = (bb << 7) & ~OrthogonalMoveUtils.FILES[7];
            long s2 = (bb << 9) & ~OrthogonalMoveUtils.FILES[0];
            return s1 | s2;
        }
    }

    private static boolean givesCheckFast(Game g, int move) {
        final Board b = g.board();
        final boolean white = ColorUtils.isWhite(g.currentPlayer);
        final int from = Move.getStartPosition(move);
        final int to   = Move.getEndPosition(move);
        final int usPiece = b.getPieceTypeAt(from);
        final byte promo = Move.getPromotion(move);

        // Opp king square
        long oppKBB = b.kingBB & (white ? b.blackBB : b.whiteBB);
        if (oppKBB == 0) return false; // shouldn't happen
        final int oppKingSq = Long.numberOfTrailingZeros(oppKBB);

        long occ = b.gameBB;
        occ &= ~(1L << from);
        occ |=  (1L << to);
        if (Move.isEnPassant(move)) {
            int epVict = white ? (to - 8) : (to + 8);
            occ &= ~(1L << epVict);
        }

        int pt = (promo != PieceUtils.NONE) ? promo : usPiece;

        long att;
        switch (pt) {
            case PieceUtils.PAWN -> {
                long bb = 1L << to;
                att = white
                        ? ((bb << 7) & ~OrthogonalMoveUtils.FILES[7]) | ((bb << 9) & ~OrthogonalMoveUtils.FILES[0])
                        : ((bb >>> 7) & ~OrthogonalMoveUtils.FILES[0]) | ((bb >>> 9) & ~OrthogonalMoveUtils.FILES[7]);
            }
            case PieceUtils.KNIGHT -> att = Knight.getAttackBB(to);
            case PieceUtils.BISHOP -> att = Bishop.getAttackBB(to, occ);
            case PieceUtils.ROOK   -> att = Rook.getAttackBB(to, occ);
            case PieceUtils.QUEEN  -> att = Bishop.getAttackBB(to, occ) | Rook.getAttackBB(to, occ);
            case PieceUtils.KING   -> att = King.getAttackBB(to);
            default -> att = 0L;
        }
        return ((att >>> oppKingSq) & 1L) != 0;
    }

    private static int capturesFirst(Game game, int[] moves, int n) {
        boolean isWhiteTurn = ColorUtils.isWhite(game.currentPlayer);
        final long enemyOcc = isWhiteTurn ? game.board().blackBB : game.board().whiteBB;
        int k = 0; // write index for captures
        for (int i = 0; i < n; i++) {
            int m = moves[i];
            boolean isCap =
                    Move.isEnPassant(m) ||                     // EP is a capture though dest is empty
                            ((enemyOcc >>> Move.getEndPosition(m)) & 1L) != 0;     // destination square occupied by enemy
            if (isCap || Move.getPromotion(m) != PieceUtils.NONE) {            // optional: include quiet promotions in QSearch
                int tmp = moves[k]; moves[k] = m; moves[i] = tmp;
                k++;
            }
        }
        return k; // number of capture(+promo) moves now at front of array
    }


    // Score only captures/promotions for ordering (higher is better)
    private static int scoreCaptureMVVLVA(Game g, int move) {
        boolean isWhiteTurn = ColorUtils.isWhite(g.currentPlayer);
        int to   = Move.getEndPosition(move);
        int from = Move.getStartPosition(move);

        int victimPiece = g.board().getPieceTypeAt(to);
        int attacker    = g.board().getPieceTypeAt(from);    // type of moving piece

        // En passant: victim stands on the square behind 'to'
        if (Move.isEnPassant(move)) {
            int epVictimSq = isWhiteTurn ? to - 8 : to + 8;    // or compute from side to move
            victimPiece = g.board().getPieceTypeAt(epVictimSq);
            assert victimPiece != PieceUtils.NONE; // sanity check for the epVictimSq
        }

        int victim = PieceValues.pieceTypeToValue(victimPiece);
        int lva    = PieceValues.lvaRankOfPiece(attacker);

        int s = (victim << 8) - (lva << 4);   // dominate by victim value, tie-break by LVA

        // Small promotion bias (queen >> others)
        if (Move.getPromotion(move) != PieceUtils.NONE) {
            int promo = Move.getPromotion(move); // your code: 2..5 for N,B,R,Q etc.
            int pv = switch (promo) { case PieceUtils.QUEEN -> 800; case PieceUtils.ROOK -> 500; case PieceUtils.BISHOP -> 330; case PieceUtils.KNIGHT -> 320; default -> 200; };
            s += pv;
        }
        return s;
    }

    // Partition captures to the front, then sort those captures by MVV-LVA (simple insertion sort)
    private static int orderCapturesMVVLVA(Game game, int[] moves, int n, int[] tmpScores) {
        boolean isWhiteTurn = ColorUtils.isWhite(game.currentPlayer);
        final long enemyOcc = isWhiteTurn ? game.board().blackBB : game.board().whiteBB;
        int k = 0;
        for (int i = 0; i < n; i++) {
            int m = moves[i];
            int to = Move.getEndPosition(m);
            boolean isCap = Move.isEnPassant(m) || (((enemyOcc >>> to) & 1L) != 0);
            if (isCap || Move.getPromotion(m) != PieceUtils.NONE) {
                int t = moves[k]; moves[k] = m; moves[i] = t;
                tmpScores[k] = scoreCaptureMVVLVA(game, moves[k]);
                k++;
            }
        }
        // insertion sort by score descending (captures only)
        for (int i = 1; i < k; i++) {
            int m = moves[i], s = tmpScores[i], j = i - 1;
            while (j >= 0 && tmpScores[j] < s) { moves[j+1] = moves[j]; tmpScores[j+1] = tmpScores[j]; j--; }
            moves[j+1] = m; tmpScores[j+1] = s;
        }
        return k; // number of tactical moves at front
    }

    private static SearchResult staticEvalOnly(Game game, Consumer<String> sinkConsumer) {
        // terminal detection so score type is correct
        final int[] legal = MOVE_BUF[0];
        final int n = game.getLegalMoves(legal);

        int score;
        if (n == 0) {
            // no legal moves: mate or stalemate
            if (game.inCheck()) {
                // depth==0 and in check → checkmated now
                // UCI “mate 0” equivalent; your SearchResult stores cp, so keep the int
                score = -mateScore(0); // or -mateScore(0) if you prefer
                sinkConsumer.accept("info depth 0 score mate 0 nodes 0 nps 0 pv");
            } else {
                // stalemate
                score = 0;
                sinkConsumer.accept("info depth 0 score cp 0 nodes 0 nps 0 pv");
            }
            // No legal move to play
            SearchResult searchResult = new SearchResult(0, score, 0, 0, 0, new int[0]);
            sinkConsumer.accept(searchResult.toUCIInfo());
            sinkConsumer.accept(PawnEval.PAWN_HASH.toUCIInfo());
            return searchResult;
        }

        // normal static evaluation from side to move
        score = getStaticEvaluation(game);

        // GUIs still want a bestmove; pick something harmless (first legal or a TT hint if you want)
        int bestMove = legal[0];

        SearchResult searchResult = new SearchResult(bestMove, score, 0, 0, 0, new int[0]);
        sinkConsumer.accept(searchResult.toUCIInfo());
        sinkConsumer.accept(PawnEval.PAWN_HASH.toUCIInfo());
        return searchResult;
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

    private static boolean isCapture(Game g, int m) {
        int to = Move.getEndPosition(m);
        if (Move.isEnPassant(m)) return true;
        long opp = ColorUtils.isWhite(g.currentPlayer) ? g.board().blackBB : g.board().whiteBB;
        return ((opp >>> to) & 1L) != 0;
    }

    private static int scoreQuiet(Game g, int move, int ply) {
        if (move == KILLER[ply][0]) return 1_000_000;
        if (move == KILLER[ply][1]) return   900_000;
        int side = sideIdx(g.currentPlayer);
        return HISTORY[side][Move.getStartPosition(move)][Move.getEndPosition(move)];
    }

    private static int sideIdx(int color) { return ColorUtils.isWhite(color) ? 0 : 1; }
}
