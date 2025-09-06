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
        return sr;
    }
}
