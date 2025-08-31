package max.chess.engine.common;

import max.chess.engine.utils.PieceUtils;

// Dot not use it on the hotpath, only for convenience
@Deprecated
public enum PieceType {
    PAWN, KNIGHT, BISHOP, ROOK, QUEEN, KING, NONE;

    public final int promotionBits;
    public static final PieceType[] VALUES = PieceType.values();

    PieceType() {
        promotionBits = (0b1000 | (ordinal() - 1));
    }

    public byte toBytes() {
        return switch (this) {
            case PAWN -> PieceUtils.PAWN;
            case KNIGHT -> PieceUtils.KNIGHT;
            case BISHOP -> PieceUtils.BISHOP;
            case ROOK -> PieceUtils.ROOK;
            case QUEEN -> PieceUtils.QUEEN;
            case KING -> PieceUtils.KING;
            case NONE -> PieceUtils.NONE;
        };
    }
}
