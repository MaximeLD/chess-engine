package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.Move;
import max.chess.engine.utils.ColorUtils;

final class Heuristics {
    static void onBetaCutoffUpdateHeuristics(SearchContext ctx, Game g, int mv, int depth, int ply, int prevMove) {
        // Only quiets should reach here. Caller must check MoveOrdering.isQuiet(...)
        final int side = ColorUtils.isWhite(g.currentPlayer) ? 0 : 1; // after undo, side-to-move reverted
        final int from = Move.getStartPosition(mv) & 63;
        final int to   = Move.getEndPosition(mv) & 63;

        final int bonus = depth * depth;

        // Main quiet history (self-bounded)
        ctx.history[side][from][to] = boundedUpdate(ctx.history[side][from][to], bonus, ctx);

        // Countermove: map prev(from,to) -> refutation (mv)
        if (prevMove != 0) {
            final int pf = Move.getStartPosition(prevMove) & 63;
            final int pt = Move.getEndPosition(prevMove) & 63;
            ctx.countermove[pf][pt] = mv;
        }

        // Continuation: side-aware (same side to move after undo), half strength
        if (prevMove != 0) {
            final int pt = Move.getEndPosition(prevMove) & 63;
            ctx.contHistory[side][pt][to] = boundedUpdate(ctx.contHistory[side][pt][to], bonus >>> 1, ctx);
        }


        // Killers
        if (ctx.killer[ply][0] != mv) {
            ctx.killer[ply][1] = ctx.killer[ply][0];
            ctx.killer[ply][0] = mv;
        }
    }

    static void decayHeuristics(SearchContext ctx) {
        // Optional: call once per ID depth if cfg.decayHeuristics
        for (int s = 0; s < 2; s++) {
            for (int f = 0; f < 64; f++) {
                for (int t = 0; t < 64; t++) {
                    ctx.history[s][f][t] -= (ctx.history[s][f][t] >> 4); // ~1/16 decay
                }
            }
            for (int a = 0; a < 64; a++) {
                for (int b = 0; b < 64; b++) {
                    ctx.contHistory[s][a][b] -= (ctx.contHistory[s][a][b] >> 4);
                }
            }
        }
    }

    /** Self-bounded history update: h += b - |h|*b/scale. Keeps |h| ~= scale at equilibrium. */
    private static int boundedUpdate(int h, int b, SearchContext ctx) {
        // You can expose this in cfg; default works well.
        final int scale = (ctx != null && ctx.cfg != null && ctx.cfg.historyCustomScale > 0)
                ? ctx.cfg.historyCustomScale
                : 16384; // 2^14

        // Use long to avoid intermediate overflow; division is fine, or use shift if scale is a power of two.
        final int damp = (int)(((long)Math.abs(h) * (long)b) / (long)scale);
        return h + b - damp;
    }
}