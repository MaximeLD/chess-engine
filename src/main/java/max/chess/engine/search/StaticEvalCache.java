package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.search.transpositiontable.TranspositionTable;

final class StaticEvalCache {
    static int get(Game game, SearchContext ctx) {
        if (ctx.tt != null) {
            TranspositionTable.IntRef out = new TranspositionTable.IntRef();
            if (ctx.tt.probeSE(game.zobristKey(), out)) return out.value;
            int se = max.chess.engine.search.evaluator.PositionEvaluator.evaluatePosition(game);
            ctx.tt.storeSE(game.zobristKey(), se);
            return se;
        }
        return max.chess.engine.search.evaluator.PositionEvaluator.evaluatePosition(game);
    }
}
