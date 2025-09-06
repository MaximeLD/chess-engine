package max.chess.engine.search.evaluator;

import max.chess.engine.utils.PieceUtils;

public class PieceValues {
    // Mobility weights (cp per reachable square)
    final class Mobility {
        static final int KN_MG = 4, KN_EG = 3, KN_FLOOR_AT = 2, KN_FLOOR = -8;
        static final int BI_MG = 3, BI_EG = 5, BI_FLOOR_AT = 3, BI_FLOOR = -8;
        static final int RO_MG = 2, RO_EG = 4, RO_FLOOR_AT = 3, RO_FLOOR = -6;
        static final int QU_MG = 1, QU_EG = 1;
        static final int KI_MG = 0, KI_EG = 2;
    }

    public static final int ROOK_VALUE = 500;
    public static final int BISHOP_VALUE = 330;
    public static final int KNIGHT_VALUE = 320;
    public static final int KING_VALUE = 20000;
    public static final int PAWN_VALUE = 100;
    public static final int QUEEN_VALUE = 900;

    // Piece values (cheap access)
    public static final int[] VAL = {
            0,
            PieceValues.PAWN_VALUE,
            PieceValues.KNIGHT_VALUE,
            PieceValues.BISHOP_VALUE,
            PieceValues.ROOK_VALUE,
            PieceValues.QUEEN_VALUE,
            PieceValues.KING_VALUE
    };

    // Pawn MG
    static final int[] P_MG = {
            0,  0,  0,  0,  0,  0,  0,  0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5,  5, 10, 25, 25, 10,  5,  5,
            0,  0,  0, 20, 20,  0,  0,  0,
            5, -5,-10,  0,  0,-10, -5,  5,
            5, 10, 10,-20,-20, 10, 10,  5,
            0,  0,  0,  0,  0,  0,  0,  0
    };
    // Pawn EG
    static final int[] P_EG = {
            0,  0,  0,  0,  0,  0,  0,  0,
            60, 60, 60, 60, 60, 60, 60, 60,
            30, 30, 35, 40, 40, 35, 30, 30,
            15, 20, 25, 30, 30, 25, 20, 15,
            10, 10, 15, 20, 20, 15, 10, 10,
            5,  5, 10, 10, 10, 10,  5,  5,
            0,  0,  0, -5, -5,  0,  0,  0,
            0,  0,  0,  0,  0,  0,  0,  0
    };

    // Knight MG
    static final int[] N_MG = {
            -50,-40,-30,-30,-30,-30,-40,-50,
            -40,-20,  0,  0,  0,  0,-20,-40,
            -30,  0, 10, 15, 15, 10,  0,-30,
            -30,  5, 15, 20, 20, 15,  5,-30,
            -30,  0, 15, 20, 20, 15,  0,-30,
            -30,  5, 10, 15, 15, 10,  5,-30,
            -40,-20,  0,  5,  5,  0,-20,-40,
            -50,-40,-30,-30,-30,-30,-40,-50
    };
    // Knight EG (often similar)
    static final int[] N_EG = N_MG;

    // Bishop MG
    static final int[] B_MG = {
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  5,  0,  0,  0,  0,  5,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10,  5,  5, 10, 10,  5,  5,-10,
            -10,  0,  5, 10, 10,  5,  0,-10,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
    };
    // Bishop EG
    static final int[] B_EG = {
            -20,-10,-10,-10,-10,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10, 10, 10, 10, 10, 10, 10,-10,
            -10,  0, 10, 10, 10, 10,  0,-10,
            -10,  5,  5,  5,  5,  5,  5,-10,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -20,-10,-10,-10,-10,-10,-10,-20
    };

    // Rook MG
    static final int[] R_MG = {
            0,  0,  0,  5,  5,  0,  0,  0,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            -5,  0,  0,  0,  0,  0,  0, -5,
            5, 10, 10, 10, 10, 10, 10,  5,
            0,  0,  0,  5,  5,  0,  0,  0
    };
    // Rook EG
    static final int[] R_EG = R_MG;

    // Queen MG  (very mild central preference)
    static final int[] Q_MG = {
            -20,-10,-10, -5, -5,-10,-10,-20,
            -10,  0,  0,  0,  0,  0,  0,-10,
            -10,  0,  5,  5,  5,  5,  0,-10,
            -5,  0,  5,  5,  5,  5,  0, -5,
            0,  0,  5,  5,  5,  5,  0, -5,
            -10,  5,  5,  5,  5,  5,  0,-10,
            -10,  0,  5,  0,  0,  0,  0,-10,
            -20,-10,-10, -5, -5,-10,-10,-20
    };
    // Queen EG
    static final int[] Q_EG = Q_MG;

    // King MG  (safer in the corner)
    static final int[] K_MG = {
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-40,-40,-50,-50,-40,-40,-30,
            -30,-30,-40,-50,-50,-40,-30,-30,
            -30,-30,-40,-50,-50,-40,-30,-30,
            -20,-30,-30,-40,-40,-30,-30,-20,
            -10,-20,-20,-20,-20,-20,-20,-10,
            20, 20,  0,  0,  0,  0, 20, 20,
            20, 30, 10,  0,  0, 10, 30, 20
    };
    // King EG  (centralization)
    static final int[] K_EG = {
            -50,-40,-30,-20,-20,-30,-40,-50,
            -30,-20,-10,  0,  0,-10,-20,-30,
            -30,-10, 20, 30, 30, 20,-10,-30,
            -30,-10, 30, 40, 40, 30,-10,-30,
            -30,-10, 30, 40, 40, 30,-10,-30,
            -30,-10, 20, 30, 30, 20,-10,-30,
            -30,-30,  0,  0,  0,  0,-30,-30,
            -50,-30,-30,-30,-30,-30,-30,-50
    };

    public static int pieceTypeToValue(int pieceType) {
        return switch (pieceType) {
            case PieceUtils.PAWN -> PAWN_VALUE;
            case PieceUtils.BISHOP -> BISHOP_VALUE;
            case PieceUtils.ROOK -> ROOK_VALUE;
            case PieceUtils.QUEEN -> QUEEN_VALUE;
            case PieceUtils.KING -> KING_VALUE;
            case PieceUtils.KNIGHT -> KNIGHT_VALUE;
            default -> 0;
        };
    }

    // Attacker “LVA rank”: smaller is better for ordering (P best, then N/B, R, Q, K last)
    public static int lvaRankOfPiece(int pieceType) {
        return switch (pieceType) {
            case PieceUtils.PAWN -> 1;
            case PieceUtils.BISHOP, PieceUtils.KNIGHT -> 2;
            case PieceUtils.ROOK -> 3;
            case PieceUtils.QUEEN -> 4;
            case PieceUtils.KING -> 5;
            default -> 6;
        };
    }

    public static int mirrorV(int sq) { return sq ^ 56; } // flip vertically
}
