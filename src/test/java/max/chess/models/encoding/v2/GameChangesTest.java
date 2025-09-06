package max.chess.models.encoding.v2;

import max.chess.engine.game.GameChanges;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class GameChangesTest {

    @ParameterizedTest
    @ValueSource(longs = {65535, (1L << 31) -1})
    public void testMovePlayed(long movePlayed) {
        // When
        long gameChanges = GameChanges.asBytes(movePlayed, 0, false, false, false, false, 0);

        // then
        assert GameChanges.getMovePlayed(gameChanges) == movePlayed;
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 63})
    public void testPreviousHalfMoveClock(int previousHalfMoveClock) {
        // When
        long gameChanges = GameChanges.asBytes(0, previousHalfMoveClock, false, false, false, false, 0);

        // then
        assert GameChanges.getPreviousHalfMoveClock(gameChanges) == previousHalfMoveClock;
    }


    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 255})
    public void testPreviousEpoch(int previousEpoch) {
        // When
        long gameChanges = GameChanges.asBytes(0, 0, false, false, false, false, previousEpoch);

        // then
        assert GameChanges.getPreviousEpoch(gameChanges) == previousEpoch;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPreviousWhiteCanCastleKingSide(boolean previousWhiteCanCastleKingSide) {
        // When
        long gameChanges = GameChanges.asBytes(0, 0, previousWhiteCanCastleKingSide, false, false, false, 0);

        // then
        assert GameChanges.getPreviousWhiteCanCastleKingSide(gameChanges) == previousWhiteCanCastleKingSide;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPreviousWhiteCanCastleQueenSide(boolean previousWhiteCanCastleQueenSide) {
        // When
        long gameChanges = GameChanges.asBytes(0, 0, false, previousWhiteCanCastleQueenSide, false, false, 0);

        // then
        assert GameChanges.getPreviousWhiteCanCastleQueenSide(gameChanges) == previousWhiteCanCastleQueenSide;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPreviousBlackCanCastleKingSide(boolean previousBlackCanCastleKingSide) {
        // When
        long gameChanges = GameChanges.asBytes(0, 0, false, false, previousBlackCanCastleKingSide, false, 0);

        // then
        assert GameChanges.getPreviousBlackCanCastleKingSide(gameChanges) == previousBlackCanCastleKingSide;
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    public void testPreviousBlackCanCastleQueenSide(boolean previousBlackCanCastleQueenSide) {
        // When
        long gameChanges = GameChanges.asBytes(0, 0, false, false, false, previousBlackCanCastleQueenSide, 0);

        // then
        assert GameChanges.getPreviousBlackCanCastleQueenSide(gameChanges) == previousBlackCanCastleQueenSide;
    }
}
