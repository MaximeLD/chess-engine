package max.chess.engine.tb;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.utils.notations.MoveIOUtils;

import java.util.Optional;
import java.util.OptionalInt;
import java.util.ServiceLoader;

public final class TBManager {
    private volatile boolean enabled = true;
    private volatile int maxPieces = 5;
    private volatile boolean useDTZ = true;
    private volatile String path = "syzygy/3-4-5/Syzygy345";

    private volatile EndgameTablebases provider = new NoopTablebases();

    public void setEnabled(boolean v) { enabled = v; }
    public void setMaxPieces(int n) { maxPieces = Math.max(0, Math.min(7, n)); }
    public void setUseDTZ(boolean v) { useDTZ = v; }
    public void setPath(String p) { path = p; configureProvider(); }

    public void setProvider(EndgameTablebases p) { provider = (p != null) ? p : new NoopTablebases(); configureProvider(); }

    public void loadProvider() {
        ClassLoader tccl = Thread.currentThread().getContextClassLoader();
        var sl = (tccl != null)
            ? java.util.ServiceLoader.load(EndgameTablebases.class, tccl)
            : java.util.ServiceLoader.load(EndgameTablebases.class);
        for (EndgameTablebases p : sl) { provider = p; configureProvider(); return; }
        for (EndgameTablebases p : java.util.ServiceLoader.load(EndgameTablebases.class)) { provider = p; configureProvider(); return; }
        provider = new NoopTablebases();
    }

    private void configureProvider() {
        try {
            var m = provider.getClass().getMethod("configure", String.class);
            m.setAccessible(true);
            m.invoke(provider, path);
        } catch (Throwable ignored) { /* provider may not support configure */ }
    }

    /** Returns UCI of a TB best move at root if available. */
    public Optional<String> pickUci(Game game) {
        return probeRoot(game).map(r -> MoveIOUtils.writeAlgebraicNotation(r.bestMove()));
    }

    public Optional<TBRootResult> probeRoot(Game game) {
        if (!enabled) return Optional.empty();
        int pieces = Long.bitCount(game.board().gameBB);
        if (pieces > maxPieces) return Optional.empty();
        int nLegal = MoveGenerator.countMoves(game);
        if (nLegal == 0) return Optional.empty();
        return provider.probeRoot(game, maxPieces);
    }

    /** WDL probe for search nodes. Empty if disabled or out of scope. */
    public java.util.OptionalInt probeWDL(Game game) {
        if (!enabled) return java.util.OptionalInt.empty();
        int pieces = Long.bitCount(game.board().gameBB);
        if (pieces > maxPieces) return java.util.OptionalInt.empty();
        OptionalInt optWdl = provider.probeWDL(game, maxPieces);
        return optWdl;
    }

}
