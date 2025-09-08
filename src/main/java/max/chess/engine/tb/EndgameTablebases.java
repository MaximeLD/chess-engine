package max.chess.engine.tb;

import max.chess.engine.game.Game;

import java.util.Optional;
import java.util.OptionalInt;

public interface EndgameTablebases extends AutoCloseable {
    boolean isAvailable();
    /** If possible, return a best root move and meta (WDL/DTZ). */
    Optional<TBRootResult> probeRoot(Game game, int maxPieces);
    /** Probe WDL at arbitrary nodes (side-to-move implicit in Game). */
    OptionalInt probeWDL(Game game, int maxPieces);
    @Override void close();
}
