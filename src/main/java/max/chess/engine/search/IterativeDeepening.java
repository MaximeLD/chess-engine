package max.chess.engine.search;

import max.chess.engine.game.Game;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static max.chess.engine.search.SearchConstants.INF;

final class IterativeDeepening {

    static SearchResult run(Game game, SearchContext ctx, AtomicBoolean stop,
                            long budgetNs, int maxDepthOrInf, Consumer<String> out) {
        final long start = System.nanoTime();
        final int maxDepth = (maxDepthOrInf == -1) ? Integer.MAX_VALUE : maxDepthOrInf;

        SearchResult last = null;
        int prevScore = 0;
        int asp = ctx.cfg.aspirationCp;

        for (ctx.currentDepth = 1; ctx.currentDepth <= maxDepth; ctx.currentDepth++) {
            if (TimeControl.aborted(stop, start, budgetNs)) break;

            int alpha = (ctx.currentDepth == 1) ? -INF : prevScore - asp;
            int beta  = (ctx.currentDepth == 1) ?  INF : prevScore + asp;

            SearchResult r;
            while (true) {
                ctx.resetDiag();
                r = RootSearch.searchAtDepth(game, ctx, ctx.currentDepth, alpha, beta, stop, start, budgetNs, out);
                if (r == null) break; // timed out during depth
                if (r.score() <= alpha)       { alpha -= asp + asp; if (alpha < -INF/2) alpha = -INF; }
                else if (r.score() >= beta)   { beta  += asp + asp; if (beta  >  INF/2) beta  =  INF; }
                else break;
            }
            if (r == null) break;
            prevScore = r.score();
            last = r;
        }

        if (last == null) {
            // Fallback: return first legal if any
            int[] buf = ctx.moveBuf[0];
            int n = game.getLegalMoves(buf);
            int mv = (n > 0) ? buf[0] : 0;
            return new SearchResult(mv, 0, 0, 0, 0, new int[0]);
        }
        return last;
    }
}
