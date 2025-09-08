package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.search.transpositiontable.TranspositionTable;
import max.chess.engine.tb.TBUtils;

final class Quiescence {

    static int search(Game g, SearchContext ctx, int alpha, int beta, int ply,
                      java.util.concurrent.atomic.AtomicBoolean stop, long start, long budgetNs) {
        if (TimeControl.aborted(stop, start, budgetNs)) return Integer.MIN_VALUE;

        final long z0 = g.zobristKey();
        ctx.totalNodes++; ctx.qNodes++;

        // If enabled, treat WDL as a stand-pat baseline; do NOT return early.
        int standPatFromTB = Integer.MIN_VALUE;
        if (ctx.tbProbeInSearch && ctx.tb != null) {
            var w = ctx.tb.probeWDL(g);
            if (w.isPresent()) standPatFromTB = TBUtils.scoreFromWDL(w.getAsInt());
        }

        final long key = g.zobristKey();
        final int alphaOrig = alpha;

        TranspositionTable.Hit hit = (ctx.tt != null) ? new TranspositionTable.Hit() : null;
        if (ctx.tt != null && ctx.tt.probe(key, 0, ply, hit)) {
            // Any bound is fine at qsearch depth
            if (hit.flag == TranspositionTable.TT_EXACT) {
                return hit.score;
            }
            if (hit.flag == TranspositionTable.TT_LOWER) { if (hit.score >= beta) {
                return beta;
            } if (hit.score > alpha) alpha = hit.score; }
            else { if (hit.score <= alpha) return alpha; if (hit.score < beta) beta = hit.score; }
            if (alpha >= beta) {
                return alpha;
            }
        }

        // In check: search evasions (full move gen), no stand-pat
        if (g.inCheck()) {
            int bufPly = (ply < ctx.moveBuf.length) ? ply : (ctx.moveBuf.length - 1);
            int[] moves = ctx.moveBuf[bufPly];
            int n = g.getLegalMoves(moves);
            if (n > moves.length) n = moves.length;
            if (n == 0) return -(max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE - ply);
            if (ctx.tt != null && hit != null && hit.move != 0) MoveOrdering.moveTTToFront(hit.move, moves, n);

            int best = Integer.MIN_VALUE / 2;
            for (int i = 0; i < n; i++) {
                if (TimeControl.aborted(stop, start, budgetNs)) {
                    return Integer.MIN_VALUE;
                }
                long u = g.playMove(moves[i]);
                if (ctx.cfg.debug) {
                    DebugChecks.assertMoveDidNotLeaveOwnKingInCheck(g);
                }
                int score = -search(g, ctx, -beta, -alpha, ply + 1, stop, start, budgetNs);
                g.undoMove(u);
                if (score >= beta) { if (ctx.tt != null) ctx.tt.store(key, moves[i], 0, score, TranspositionTable.TT_LOWER, ply); return score; }
                if (score > best) best = score;
                if (score > alpha) alpha = score;
            }
            if (ctx.tt != null) {
                byte flag = (best <= alphaOrig) ? TranspositionTable.TT_UPPER : TranspositionTable.TT_EXACT;
                ctx.tt.store(key, 0, 0, best, flag, ply);
            }
            return alpha;
        }

        // Stand-pat using static eval (TT cached if available)
        int standPat = StaticEvalCache.get(g, ctx);
        if (standPatFromTB != Integer.MIN_VALUE) standPat = Math.max(standPat, standPatFromTB);

        if (standPat >= beta) { if (ctx.tt != null) ctx.tt.store(key, 0, 0, standPat, TranspositionTable.TT_LOWER, ply);
            return standPat;
        }
        if (standPat > alpha) alpha = standPat;

        // Generate all legal, keep only tactical (captures/promos), order with MVV-LVA
        int bufPly = (ply < ctx.moveBuf.length) ? ply : (ctx.moveBuf.length - 1);
        int[] moves = ctx.moveBuf[bufPly];
        int n = g.getLegalMoves(moves);
        if (n > moves.length) n = moves.length;
        if (ctx.tt != null && hit != null && MoveOrdering.isTactical(g, hit.move)) MoveOrdering.moveTTToFront(hit.move, moves, n);
        int k = MoveOrdering.partitionAndScoreCaptures(g, moves, n, ctx.scoreBuf[bufPly]);

        for (int i = 0; i < k; i++) {
            if (TimeControl.aborted(stop, start, budgetNs)) {
                return Integer.MIN_VALUE;
            }

            int m = moves[i];

            // Skip clearly bad trades unless checking or big promo
            if (!MoveOrdering.givesCheckFast(g, m) && MoveOrdering.promotionType(m) != max.chess.engine.utils.PieceUtils.QUEEN) {
                final int ub = MoveOrdering.captureUpperBound(g, m);
                if (standPat + ub + ctx.cfg.deltaMargin <= alpha) continue;

                if (ub < max.chess.engine.search.evaluator.PieceValues.ROOK_VALUE) {
                    final int see = MoveOrdering.seeSwap(g, m);
                    if (MoveOrdering.promotionType(m) == max.chess.engine.utils.PieceUtils.NONE && see < 0) continue;
                    if (standPat + see + ctx.cfg.seeMargin <= alpha) continue;
                }
            }

            long u = g.playMove(m);
            if (ctx.cfg.debug) {
                DebugChecks.assertMoveDidNotLeaveOwnKingInCheck(g);
            }
            int score = -search(g, ctx, -beta, -alpha, ply + 1, stop, start, budgetNs);
            g.undoMove(u);

            if (score >= beta) { if (ctx.tt != null) ctx.tt.store(key, m, 0, score, TranspositionTable.TT_LOWER, ply);
                return score;
            }
            if (score > alpha) alpha = score;
        }

        if (ctx.tt != null) {
            byte flag = (alpha <= alphaOrig) ? TranspositionTable.TT_UPPER : TranspositionTable.TT_EXACT;
            ctx.tt.store(key, 0, 0, alpha, flag, ply);
        }

        return alpha;
    }
}
