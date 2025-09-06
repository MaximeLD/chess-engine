package max.chess.engine.search.archived;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
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

public class NegamaxDeepeningSearchWithTTAndQuiescence {
    private static final int DEFAULT_TT_SIZE = 64; // In MB
    private static final boolean DEFAULT_USE_TT = true; // In MB
    private static final boolean USE_TT      ;   // global flag for TT
    private static final boolean USE_TT_BOUNDS      = true;   // A/B: disable bound tightening, keep only ttMove ordering
    private static final boolean STORE_EXACT_ONLY   = false;  // A/B: store only TT_EXACT entries
    private static final boolean TIGHT_STORE_BOUNDS = false;   // store tighter cut scores instead of alphaOrig/beta

    // Reusable move buffers per ply to avoid allocations (still “simple”).
    private static final int[][] SCORE_BUF = new int[MAX_PLY][MAX_MOVES];
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
        currentDepth = 1;

        if(USE_TT) {
            tt.newSearch();
            PawnEval.newSearch();
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

        if (lastResult == null) {
            int[] root = MOVE_BUF[0];
            int n = game.getLegalMoves(root);
            int fallback = (n > 0) ? root[0] : 0;
            // never output 0000: if n==0 you're checkmated/stalemated; your UCI should handle that.
            return new SearchResult(fallback, 0, 0, 0, 0, new int[0]);
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

        if(aborted(stopFlag, startTime, maximumAllowedTimeNanos)) {
            // Cancelling the search
            return null;
        }

        long t0 = System.nanoTime();

        // root: pick the best child by calling negamax on each
        int[] moves = MOVE_BUF[0];
        int n = game.getLegalMoves(moves);
        if (n == 0 && !game.inCheck()) throw new IllegalStateException("No legal moves at root but not in check");

        capturesFirst(game, moves, n);

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
                if(aborted(stopFlag, startTime, maximumAllowedTimeNanos)) {
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
            return quiesce(game, alpha, beta, ply, stopFlag, startTime, maximumAllowedTimeNanos);
        }

        int[] moves = MOVE_BUF[ply];
        int n = game.getLegalMoves(moves);
        capturesFirst(game, moves, n);

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
                if (hit.score >= beta) return hit.score;
                if (hit.score > alpha) alpha = hit.score;
            } else { // UPPER
                if (hit.score <= alpha) return hit.score;
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

        // ----------------------------------------------------
        // 3) (Optional) Delta pruning for obviously bad trades
        //    Simple version: if standPat + gainUpperBound + margin <= alpha, skip
        // ----------------------------------------------------
        for (int i = 0; i < k; i++) {
            if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;

            int m = moves[i];

            // Simple delta pruning (tweak numbers to taste)
            // (You can replace captureGainUpperBound with MVV or a small SEE.)
            int gainUb = captureGainUpperBound(g, m); // e.g., value(captured) + promoBonus
            if (standPat + gainUb + 20 <= alpha) continue;

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

    private static int captureGainUpperBound(Game g, int move) {
        int captured = PieceValues.pieceTypeToValue(g.board().getPieceTypeAt(Move.getEndPosition(move))); // 0 if none
        byte promotion = Move.getPromotion(move);
        int promoBonus = promotion != PieceUtils.NONE ? (PieceValues.QUEEN_VALUE - PieceValues.PAWN_VALUE) : 0;
        return captured + promoBonus;
    }

    private static int quiesceNoTT(Game g, int alpha, int beta, int ply,
                               AtomicBoolean stopFlag, long startTime, long maxTimeNanos) {
        if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;

        // If side to move is in check, we must search *evasions*, not stand-pat.
        if (g.inCheck()) {
            int[] moves = MOVE_BUF[ply];
            int n = g.getLegalMoves(moves);       // full legal set = evasions
            if (n == 0) return -mateScore(ply);   // checkmated in quiescence
            for (int i = 0; i < n; i++) {
                if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;
                long undo = g.playMove(moves[i]);
                int score = -quiesce(g, -beta, -alpha, ply + 1, stopFlag, startTime, maxTimeNanos);
                g.undoMove(undo);
                if (score >= beta) return beta;
                if (score > alpha) alpha = score;
            }
            return alpha;
        }

        // Stand pat (static) evaluation
        int standPat = PositionEvaluator.evaluatePosition(g);
        if (standPat >= beta) return beta;
        if (standPat > alpha) alpha = standPat;

        // Generate legal moves, keep only captures (+ promotions if you want)
        int[] moves = MOVE_BUF[ply];
        int n = g.getLegalMoves(moves);
        int k = orderCapturesMVVLVA(g, moves, n, SCORE_BUF[ply]);
//        int k = capturesFirst(g, moves, n);   // only search first k capture/promotions

        // (Optional) lightweight pruning: skip obviously bad captures (SEE/delta later)

        for (int i = 0; i < k; i++) {
            if (aborted(stopFlag, startTime, maxTimeNanos)) return Integer.MIN_VALUE;

            int m = moves[i];
            long undo = g.playMove(m);
            int score = -quiesce(g, -beta, -alpha, ply + 1, stopFlag, startTime, maxTimeNanos);
            g.undoMove(undo);

            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
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
}
