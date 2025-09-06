package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.search.evaluator.PawnEval;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.ColorUtils;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class SearchFacade {

    private final SearchContext ctx;

    public SearchFacade(SearchConfig cfg) {
        this.ctx = new SearchContext(cfg);
        // warm-ups
        max.chess.engine.movegen.MoveGenerator.warmUp();
        max.chess.engine.search.evaluator.PositionEvaluator.warmUp();
    }

    public void init() {
        if (ctx.tt != null) ctx.tt.clear();
    }

    public SearchResult findBestMove(Game game, AtomicBoolean stop, UciServer.GoParams go, Consumer<String> out) {
        if (go.depth == 0 || go.staticEvalOnly) {
            return RootSearch.staticEvalOnly(game, ctx, out);
        }
        ctx.newSearch();
        final long budgetNs = TimeControl.computeBudgetNs(ColorUtils.isWhite(game.currentPlayer), go, go.depth, ctx.cfg);
        SearchResult sr = IterativeDeepening.run(game, ctx, stop, budgetNs, go.depth, out);
        out.accept(PawnEval.PAWN_HASH.toUCIInfo());

        if (ctx.cfg.debug) {
            // Verify bestMove legality in the current position
            int mv = sr.move();
            if (mv != 0) {
                int[] buf = ctx.moveBuf[0];
                int n = game.getLegalMoves(buf);
                boolean legal = false;
                for (int i = 0; i < n; i++) {
                    if (buf[i] == mv) { legal = true; break; }
                }
                if (!legal) {
                    throw new IllegalStateException("Illegal bestMove [CE-v7.3-P002]: " + mv);
                }
                // PV head must match bestMove when present
                int[] pv = sr.principalVariation();
                if (pv != null && pv.length > 0 && pv[0] != mv) {
                    throw new IllegalStateException("BestMove/PV mismatch [CE-v7.3-P002]: " + mv + " vs " + pv[0]);
                }
            }
        }

        return sr;
    }
}
