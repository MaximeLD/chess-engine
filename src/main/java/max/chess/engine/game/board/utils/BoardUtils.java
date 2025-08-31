package max.chess.engine.game.board.utils;

import max.chess.engine.common.PieceType;
import max.chess.engine.utils.ColorUtils;

public class BoardUtils {
    private static final int BIT_MASK_PIECE_COLOR = 0b0000010;
    private static final int BIT_MASK_PIECE_TYPE = 0b0011100;
    private static final int BIT_MASK_EMPTINESS = 0b0000001;
    private static final int BIT_MASK_EMPTY = 0b0000000;
    private static final int BIT_MASK_NOT_EMPTY = 0b0000001;
    private static final int EMPTY_SQUARE = BIT_MASK_EMPTY;
    private static final int BIT_MASK_WHITE = 0b0000000;
    private static final int BIT_MASK_BLACK = 0b0000010;
    private static final int BIT_MASK_KNIGHT = 0b0001100;
    private static final int BIT_MASK_QUEEN = 0b0010000;
    private static final int BIT_MASK_BISHOP = 0b0011000;
    private static final int BIT_MASK_PAWN = 0b0001000;
    private static final int BIT_MASK_KING = 0b0000100;
    private static final int BIT_MASK_ROOK = 0b0010100;

    public static byte encodePiece(int color, PieceType pieceType) {
        return getSquareEncoded(color, pieceType);
    }

    public static byte encodeEmptySquare() {
        return EMPTY_SQUARE;
    }

    public static boolean isEmptySquare(byte square) {
        return square == EMPTY_SQUARE;
    }

    public static PieceType getSquarePieceType(int square) {
        square &= BIT_MASK_PIECE_TYPE;

        return switch (square) {
            case BIT_MASK_BISHOP -> PieceType.BISHOP;
            case BIT_MASK_KING -> PieceType.KING;
            case BIT_MASK_PAWN -> PieceType.PAWN;
            case BIT_MASK_KNIGHT -> PieceType.KNIGHT;
            case BIT_MASK_ROOK -> PieceType.ROOK;
            case BIT_MASK_QUEEN -> PieceType.QUEEN;
            default -> throw new IllegalStateException("Unexpected value: " + square);
        };
    }


    public static int getSquareColor(int square) {
        return (square & BIT_MASK_PIECE_COLOR) == BIT_MASK_BLACK
                ? ColorUtils.BLACK : ColorUtils.WHITE;
    }

    private static byte getSquareEncoded(int color, PieceType pieceType) {
        byte squareEncoded = 0;
        squareEncoded |= getColorEncoded(color);
        squareEncoded |= getEmptinessEncoded(false);
        squareEncoded |= getPieceTypeEncoded(pieceType);

        return squareEncoded;
    }

    private static byte getPieceTypeEncoded(PieceType pieceType) {
        return switch (pieceType) {
            case KING -> BIT_MASK_KING;
            case PAWN -> BIT_MASK_PAWN;
            case KNIGHT -> BIT_MASK_KNIGHT;
            case QUEEN -> BIT_MASK_QUEEN;
            case ROOK -> BIT_MASK_ROOK;
            case BISHOP -> BIT_MASK_BISHOP;
            case NONE -> (byte)0;
        };
    }

    private static byte getEmptinessEncoded(boolean empty) {
        if(empty) {
            return BIT_MASK_EMPTY;
        } else {
            return BIT_MASK_NOT_EMPTY;
        }
    }

    private static byte getColorEncoded(int color) {
        if(ColorUtils.isWhite(color)) {
            return BIT_MASK_WHITE;
        } else {
            return BIT_MASK_BLACK;
        }
    }
}
