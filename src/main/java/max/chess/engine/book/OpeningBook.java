package max.chess.engine.book;

import max.chess.engine.game.Game;

import java.util.Optional;

public interface OpeningBook extends AutoCloseable {
    /** Returns a legal move (engine int) from the book for this game, or empty if not found / out of policy. */
    Optional<Integer> pickMove(Game game, BookPolicy policy, long rngSeed);
    /** @return true if the book has loaded data and is operational. */
    boolean isLoaded();
    @Override void close();
}
