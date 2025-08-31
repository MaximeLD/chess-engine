package max.chess.engine.movegen.utils;

public final class DiagonalMoveUtils {
    public static final long A1_H8 = 1L << 0 | 1L << 9 | 1L << 18 | 1L << 27 | 1L << 36 | 1L << 45 | 1L << 54 | 1L << 63;
    public static final long B1_H7 = 1L << 1 | 1L << 10 | 1L << 19 | 1L << 28 | 1L << 37 | 1L << 46 | 1L << 55;
    public static final long A2_G8 = 1L << 8 | 1L << 17 | 1L << 26 | 1L << 35 | 1L << 44 | 1L << 53 | 1L << 62;
    public static final long C1_H6 = 1L << 2 | 1L << 11 | 1L << 20 | 1L << 29 | 1L << 38 | 1L << 47;
    public static final long A3_F8 = 1L << 16 | 1L << 25 | 1L << 34 | 1L << 43 | 1L << 52 | 1L << 61;
    public static final long D1_H5 = 1L << 3 | 1L << 12 | 1L << 21 | 1L << 30 | 1L << 39;
    public static final long A4_E8 = 1L << 24 | 1L << 33 | 1L << 42 | 1L << 51 | 1L << 60;
    public static final long E1_H4 = 1L << 4 | 1L << 13 | 1L << 22 | 1L << 31;
    public static final long A5_D8 = 1L << 32 | 1L << 41 | 1L << 50 | 1L << 59;
    public static final long F1_H3 = 1L << 5 | 1L << 14 | 1L << 23;
    public static final long A6_C8 = 1L << 40 | 1L << 49 | 1L << 58;
    public static final long G1_H2 = 1L << 6 | 1L << 15;
    public static final long A7_B8 = 1L << 48 | 1L << 57;
    public static final long H1_A8 = 1L << 7 | 1L << 14 | 1L << 21 | 1L << 28 | 1L << 35 | 1L << 42 | 1L << 49 | 1L << 56;
    public static final long G1_A7 = 1L << 6 | 1L << 13 | 1L << 20 | 1L << 27 | 1L << 34 | 1L << 41 | 1L << 48;
    public static final long H2_B8 = 1L << 15 | 1L << 22 | 1L << 29 | 1L << 36 | 1L << 43 | 1L << 50 | 1L << 57;
    public static final long F1_A6 = 1L << 5 | 1L << 12 | 1L << 19 | 1L << 26 | 1L << 33 | 1L << 40;
    public static final long H3_C8 = 1L << 23 | 1L << 30 | 1L << 37 | 1L << 44 | 1L << 51 | 1L << 58;
    public static final long E1_A5 = 1L << 4 | 1L << 11 | 1L << 18 | 1L << 25 | 1L << 32;
    public static final long H4_D8 = 1L << 31 | 1L << 38 | 1L << 45 | 1L << 52 | 1L << 59;
    public static final long D1_A4 = 1L << 3 | 1L << 10 | 1L << 17 | 1L << 24;
    public static final long H5_E8 = 1L << 39 | 1L << 46 | 1L << 53 | 1L << 60;
    public static final long C1_A3 = 1L << 2 | 1L << 9 | 1L << 16;
    public static final long H6_F8 = 1L << 47 | 1L << 54 | 1L << 61;
    public static final long B1_A2 = 1L << 1 | 1L << 8;
    public static final long H7_G8 = 1L << 55 | 1L << 62;

    // 64 elements, each element is a bitboard where 1 are representing the valid diagonals of this square
    public static final long[] diagonals = {
            // First row of the board
            A1_H8, B1_H7 | B1_A2, C1_A3 | C1_H6, D1_A4 | D1_H5, E1_A5 | E1_H4, F1_H3 | F1_A6, G1_H2 | G1_A7, H1_A8,
            // Second row
            B1_A2 | A2_G8, C1_A3 | A1_H8, D1_A4 | B1_H7, E1_A5 | C1_H6, F1_A6 | D1_H5, G1_A7 | E1_H4, H1_A8 | F1_H3, H2_B8 | G1_H2,
            // Third row
            C1_A3 | A3_F8, D1_A4 | A2_G8, E1_A5 | A1_H8, F1_A6 | B1_H7, G1_A7 | C1_H6, H1_A8 | D1_H5, H2_B8 | E1_H4, H3_C8 | F1_H3,
            // Fourth row
            D1_A4 | A4_E8, E1_A5 | A3_F8, F1_A6 | A2_G8, G1_A7 | A1_H8, H1_A8 | B1_H7, H2_B8 | C1_H6, H3_C8 | D1_H5, H4_D8 | E1_H4,
            // Fifth row
            E1_A5 | A5_D8, F1_A6 | A4_E8, G1_A7 | A3_F8, H1_A8 | A2_G8, H2_B8 | A1_H8, H3_C8 | B1_H7, H4_D8 | C1_H6, H5_E8 | D1_H5,
            // Sixth row
            F1_A6 | A6_C8, G1_A7 | A5_D8, H1_A8 | A4_E8, H2_B8 | A3_F8, H3_C8 | A2_G8, H4_D8 | A1_H8, H5_E8 | B1_H7, H6_F8 | C1_H6,
            // Seventh row
            G1_A7 | A7_B8, H1_A8 | A6_C8, H2_B8 | A5_D8, H3_C8 | A4_E8, H4_D8 | A3_F8, H5_E8 | A2_G8, H6_F8 | A1_H8, H7_G8 | B1_H7,
            // Eighth row
            H1_A8, H2_B8 | A7_B8, H3_C8 | A6_C8, H4_D8 | A5_D8, H5_E8 | A4_E8, H6_F8 | A3_F8, H7_G8 | A2_G8, A1_H8
    };
}
