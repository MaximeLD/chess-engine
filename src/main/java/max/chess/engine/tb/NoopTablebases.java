package max.chess.engine.tb;

import max.chess.engine.game.Game;
import java.util.Optional;
import java.util.OptionalInt;

public final class NoopTablebases implements EndgameTablebases {
    public NoopTablebases() {
    }

    @Override public boolean isAvailable() { return false; }
    @Override public Optional<TBRootResult> probeRoot(Game g, int maxPieces) { return Optional.empty(); }

    @Override
    public OptionalInt probeWDL(Game game, int maxPieces) {
        return OptionalInt.empty();
    }

    @Override public void close() {}
}
