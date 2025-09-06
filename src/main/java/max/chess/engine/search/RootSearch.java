package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.search.transpositiontable.TranspositionTable;
import max.chess.engine.utils.ColorUtils;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static max.chess.engine.search.SearchConstants.INF;

final class RootSearch {

    static SearchResult searchAtDepth(Game game, SearchContext ctx, int depth,
                                      int rootAlpha, int rootBeta,
                                      AtomicBoolean stop, long start, long budgetNs,
                                      Consumer<String> out) {
        out.accept("string Searching at depth " + depth);
        ctx.nodes = 0; // per-depth nodes
        ctx.rootIsWhite = ColorUtils.isWhite(game.currentPlayer);
        // At root there is no previous move
        if (ctx.prevMove.length > 0) ctx.prevMove[0] = 0;
        Arrays.fill(ctx.pvLen, 0);

        if (TimeControl.aborted(stop, start, budgetNs)) return null;

        final long zStart = game.zobristKey();

        final int ply = 0;
        final int[] moves = ctx.moveBuf[ply];
        int moveCount = game.getLegalMoves(moves);
        if (moveCount == 0 && !game.inCheck()) throw new IllegalStateException("No legal moves at root but not in check");

        // Order moves: captures by MVV-LVA, quiets by history/killers, TT move first if present
        final int capCount = MoveOrdering.partitionAndScoreCaptures(game, moves, moveCount, ctx.scoreBuf[ply]);
        final int prev = (ply < ctx.prevMove.length) ? ctx.prevMove[ply] : 0;
        MoveOrdering.scoreAndSortQuiets(game, moves, capCount, moveCount, ctx.scoreBuf[ply],
                ctx.killer, ctx.history, ply, ctx, prev);
        if (ctx.tt != null) MoveOrdering.moveTTToFront(ctx.tt.peekMove(game.zobristKey()), moves, moveCount);

        int bestMove = 0, bestScore = -INF;
        int alpha = rootAlpha, beta = rootBeta;
        boolean picked = false;

        if (moveCount == 0) {
            bestScore = game.isADraw() ? 0 : (game.inCheck() ? -mateScore(0) : 0);
            picked = true;
        } else {
            for (int i = 0; i < moveCount; i++) {
                if (TimeControl.aborted(stop, start, budgetNs)) return null;
                int mv = moves[i];
                long undo = game.playMove(mv);

                // The child node (ply+1) should see this mv as its previous move
                if (1 < ctx.prevMove.length) ctx.prevMove[1] = mv;

                int score;
                if (i == 0) {
                    score = -Negamax.search(game, ctx, depth - 1, 1, -beta, -alpha, stop, start, budgetNs);
                } else {
                    score = -Negamax.search(game, ctx, depth - 1, 1, -(alpha + 1), -alpha, stop, start, budgetNs);
                    if (score > alpha && score < beta) {
                        score = -Negamax.search(game, ctx, depth - 1, 1, -beta, -alpha, stop, start, budgetNs);
                    }
                }
                game.undoMove(undo);
                if (score == Integer.MIN_VALUE) return null; // timed out deeper

                if (score > bestScore) {
                    bestScore = score;
                    bestMove = mv; picked = true;
                    // Build root PV from child PV
                    ctx.pv[0][0] = mv;
                    ctx.pvLen[0] = 1 + ctx.pvLen[1];
                    System.arraycopy(ctx.pv[1], 0, ctx.pv[0], 1, ctx.pvLen[1]);
                }
                if (bestScore > alpha) alpha = bestScore;
            }
        }

        if (!picked) return null;

        // Sanitize best move if needed (illegal after sanitize? fall back to first legal)
        bestMove = MoveOrdering.sanitizeBestMove(game, bestMove, ctx);
        int[] legals = ctx.moveBuf[0];
        int n = game.getLegalMoves(legals);
        boolean ok = false;
        for (int i = 0; i < n; i++) if (legals[i] == bestMove) { ok = true; break; }

        if (!ok) {
            if (ctx.cfg.debug) {
                throw new IllegalStateException("Root bestMove not legal in current position");
            }
            int fallback = 0;
            // Try TT hint, but only if key-safe peekMove as above
            if (ctx.tt != null) {
                int ttMove = ctx.tt.peekMove(game.zobristKey());
                if (ttMove != 0) {
                    for (int i = 0; i < n; i++) { if (legals[i] == ttMove) { fallback = ttMove; break; } }
                }
            }
            if (fallback == 0) fallback = (n > 0) ? legals[0] : 0;  // 0000 only if truly no legals
            bestMove = fallback;
        }

        // Legalize PV to avoid GUI weirdness if sanitize changed root move
        final int rawLen = Math.min(ctx.pvLen[0], SearchConstants.MAX_PLY);
        final int legalLen = PrincipalVariation.legalize(game, ctx.pv[0], rawLen, ctx.moveBuf);
        final int[] pvLine = (legalLen == 0 || ctx.pv[0][0] != bestMove)
                ? new int[]{bestMove}
                : java.util.Arrays.copyOf(ctx.pv[0], legalLen);

        if (ctx.tt != null) {
            TranspositionTable tt = ctx.tt;
            out.accept(tt.snapshot().toInfoStringForUCI(tt));
        }

        if (zStart != game.zobristKey()) {
            throw new IllegalStateException("Position mutated across search");
        }

        long now = System.nanoTime();
        long totalMs = Math.max(1, (now - start) / 1_000_000);
        long nps = (ctx.totalNodes * 1000L) / totalMs;

        out.accept(ctx.toUCIInfo(depth));
        return new SearchResult(bestMove, bestScore, ctx.totalNodes, totalMs, nps, pvLine);
    }

    static SearchResult staticEvalOnly(Game game, SearchContext ctx, Consumer<String> out) {
        int[] legal = ctx.moveBuf[0];
        int n = game.getLegalMoves(legal);
        int score;
        if (n == 0) {
            score = game.inCheck() ? -mateScore(0) : 0;
            SearchResult sr = new SearchResult(0, score, 0, 0, 0, new int[0]);
            out.accept(sr.toUCIInfo());
            out.accept(max.chess.engine.search.evaluator.PawnEval.PAWN_HASH.toUCIInfo());
            return sr;
        }
        score = StaticEvalCache.get(game, ctx);
        int best = legal[0];
        SearchResult sr = new SearchResult(best, score, 0, 0, 0, new int[0]);
        out.accept(sr.toUCIInfo());
        out.accept(max.chess.engine.search.evaluator.PawnEval.PAWN_HASH.toUCIInfo());
        return sr;
    }

    private static int mateScore(int ply) { return max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - ply; }
}
