package max.chess.models.encoding.v2;

import max.chess.engine.utils.PieceUtils;
import max.chess.engine.movegen.Move;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

public class MoveTest {

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 6})
    public void testPieceTypeEncoding(byte pieceType) {
        // When
        int move = Move.asBytes(0, 0, pieceType);

        // then
        assert Move.getPieceType(move) ==  pieceType;
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 6})
    public void testPromotionEncoding(byte promotion) {
        // When
        int move = Move.asBytes(0, 0, PieceUtils.KING, promotion);

        // then
        assert Move.getPromotion(move) == promotion;
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 6, 63})
    public void testStartPositionEncoding(int startPosition) {
        // When
        int move = Move.asBytes(startPosition, 0, PieceUtils.NONE);

        // then
        assert Move.getStartPosition(move) == startPosition;
    }

    @ParameterizedTest
    @ValueSource(bytes = {0, 1, 2, 3, 4, 5, 6, 63})
    public void testEndPositionEncoding(int endPosition) {
        // When
        int move = Move.asBytes(0, endPosition, PieceUtils.NONE);

        // then
        assert Move.getEndPosition(move) == endPosition;
    }

    @Test
    public void testKingCastleEncoding() {
        // When
        int moveWhite = Move.CASTLE_KING_SIDE_WHITE_MOVE;
        int moveBlack = Move.CASTLE_KING_SIDE_BLACK_MOVE;

        // then
        assert Move.isCastleKingSide(moveWhite);
        assert Move.isCastleKingSide(moveBlack);
    }

    @Test
    public void testQueenCastleEncoding() {
        // When
        int moveWhite = Move.CASTLE_QUEEN_SIDE_WHITE_MOVE;
        int moveBlack = Move.CASTLE_QUEEN_SIDE_BLACK_MOVE;

        // then
        assert Move.isCastleQueenSide(moveWhite);
        assert Move.isCastleQueenSide(moveBlack);
    }

    @Test
    public void testEnPassantEncoding() {
        // When
        int move = Move.asBytesEnPassant(0, 0);

        // then
        assert Move.isEnPassant(move);
    }
}
