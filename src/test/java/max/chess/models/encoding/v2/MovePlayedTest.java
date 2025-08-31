package max.chess.models.encoding.v2;

import max.chess.engine.game.board.MovePlayed;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class MovePlayedTest {

    @ParameterizedTest
    @ValueSource(ints = {65535, (1 << 21) - 1})
    public void testMoveEncoding(int move) {
        // When
        int movePlayed = MovePlayed.asBytes(move, (byte) 0, 0);

        // then
        assert MovePlayed.getMove(movePlayed) == move;
    }

    @ParameterizedTest
    @ValueSource(bytes = {-1, 0, 1, 2, 3, 4, 5, 63})
    public void testPreviousEnPassantEncoding(int previousEnPassantIndex) {
        // When
        int movePlayed = MovePlayed.asBytes(0, (byte) 0, previousEnPassantIndex);

        // then
        assert MovePlayed.getPreviousEnPassantIndex(movePlayed) == previousEnPassantIndex;
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 6, 7})
    public void testPieceEatenEncoding(byte pieceEaten) {
        // When
        int movePlayed = MovePlayed.asBytes(0, pieceEaten, 0);

        // then
        assert MovePlayed.getPieceEaten(movePlayed) == pieceEaten;
    }
}
