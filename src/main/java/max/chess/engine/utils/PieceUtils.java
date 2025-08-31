package max.chess.engine.utils;

import max.chess.engine.common.PieceType;

public class PieceUtils {
    // 4th bit will be the color
    private static final byte COLOR_MASK = 0b0001000;
    private static final byte PIECE_TYPE_MASK = 0b0000111;

    public static final byte NONE = 0;
    public static final byte PAWN = 1;
    public static final byte KNIGHT = 2;
    public static final byte BISHOP = 3;
    public static final byte ROOK = 4;
    public static final byte QUEEN = 5;
    public static final byte KING = 6;

    // static prebuilt table (index 0..6)
    static final PieceType[] FROM_CODE = {
            PieceType.NONE, PieceType.PAWN, PieceType.KNIGHT,
            PieceType.BISHOP, PieceType.ROOK, PieceType.QUEEN, PieceType.KING
    };

    public static PieceType toPieceType(int code) { return FROM_CODE[code & PIECE_TYPE_MASK]; }
    public static byte fromPieceType(PieceType pieceType) {
        return switch (pieceType) {
            case PAWN -> PAWN;
            case KNIGHT -> KNIGHT;
            case BISHOP -> BISHOP;
            case ROOK -> ROOK;
            case QUEEN -> QUEEN;
            case KING -> KING;
            case NONE -> NONE;
        };
    }
    public static byte toPieceCode(int code) { return (byte) (code & PIECE_TYPE_MASK); }
    public static byte toColor(int code) { return (byte) (code & COLOR_MASK); }

    public static byte encode(byte piece, byte color) {
        return (byte) (color << 3 | piece);
    }

    public static byte encodeEmpty() {
        return 0;
    }

}
