package max.chess.engine.movegen;

import max.chess.engine.utils.PieceUtils;
import max.chess.engine.utils.notations.MoveIOUtils;

public record Move(int startPosition, int endPosition, byte promotion) {
    private static final int FLAGS_MASK = 0b11 << 15;
    private static final int EN_PASSANT_FLAG = 0b01 << 15;
    private static final int CASTLE_KING_SIDE_FLAG = 0b10 << 15;
    private static final int CASTLE_QUEEN_SIDE_FLAG = 0b11 << 15;

    public static final int CASTLE_KING_SIDE_WHITE_MOVE = Move.asBytes(4, 6, PieceUtils.KING) | CASTLE_KING_SIDE_FLAG;
    public static final int CASTLE_QUEEN_SIDE_WHITE_MOVE = Move.asBytes(4, 2, PieceUtils.KING) | CASTLE_QUEEN_SIDE_FLAG;
    public static final int CASTLE_KING_SIDE_BLACK_MOVE = Move.asBytes(60, 62, PieceUtils.KING) | CASTLE_KING_SIDE_FLAG;
    public static final int CASTLE_QUEEN_SIDE_BLACK_MOVE = Move.asBytes(60, 58, PieceUtils.KING) | CASTLE_QUEEN_SIDE_FLAG;

    // Don't build a Move object if you're on the hotpath, rely on the static below instead
    @Deprecated
    public Move {
    }

    @Deprecated
    public Move(int startPosition, int endPosition) {
        this(startPosition, endPosition, PieceUtils.NONE);
    }

    @Deprecated
    public static Move fromAlgebraicNotation(String notation) {
        int startPosition = MoveIOUtils.getPositionFromSquare(notation.substring(0, 2)).getFlatIndex();
        int endPosition = MoveIOUtils.getPositionFromSquare(notation.substring(2, 4)).getFlatIndex();
        if (notation.length() == 4) {
            return new Move(startPosition, endPosition);
        } else if (notation.length() == 5) {
            return Move.promote(startPosition, endPosition, MoveIOUtils.getPieceTypeFromLetter(notation.charAt(4)).toBytes());
        }

        throw new RuntimeException("Cannot parse algebraic notation " + notation);
    }

    @Deprecated
    public static Move promote(int startPosition, int endPosition, byte pieceType) {
        return new Move(startPosition, endPosition, pieceType);
    }


    @Override
    public String toString() {
        return MoveIOUtils.writeAlgebraicNotation(this);
    }

    public static int asBytes(final int startPosition, final int endPosition,
                              final byte pieceType, final byte promotion) {
        return (promotion << 17) | asBytes(startPosition, endPosition, pieceType);
    }

    public static int asBytes(final int startPosition, final int endPosition, final byte pieceType) {
        return (pieceType << 12) | (startPosition << 6) | endPosition;
    }

    public static int asBytesEnPassant(final int startPosition, final int endPosition) {
        return asBytes(startPosition, endPosition, PieceUtils.PAWN) | EN_PASSANT_FLAG;
    }

    public static Move fromBytes(final int bytes) {
        return new Move(getStartPosition(bytes), getEndPosition(bytes), getPromotion(bytes));
    }

    public static int getStartPosition(final int bytes) {
        return (bytes & 0b111111000000) >> 6;
    }

    public static int getEndPosition(final int bytes) {
        return bytes & 0b111111;
    }

    public static byte getPromotion(final int bytes) {
        return (byte) ((bytes & 0b11100000000000000000) >> 17);
    }

    public static byte getPieceType(final int bytes) {
        return (byte) ((bytes & 0b111000000000000) >> 12);
    }

    public static boolean isCastleKingSide(final int bytes) {
        return (bytes & FLAGS_MASK) == CASTLE_KING_SIDE_FLAG;
    }

    public static boolean isCastleQueenSide(final int bytes) {
        return (bytes & FLAGS_MASK) == CASTLE_QUEEN_SIDE_FLAG;
    }

    public static boolean isEnPassant(final int bytes) {
        return (bytes & FLAGS_MASK) == EN_PASSANT_FLAG;
    }
}
