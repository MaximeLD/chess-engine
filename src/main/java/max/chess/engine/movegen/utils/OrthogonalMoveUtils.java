package max.chess.engine.movegen.utils;

public final class OrthogonalMoveUtils {
    public static final long A1_H1 = 0b11111111L;
    public static final long A2_H2 = A1_H1 << 8;
    public static final long A3_H3 = A2_H2 << 8;
    public static final long A4_H4 = A3_H3 << 8;
    public static final long A5_H5 = A4_H4 << 8;
    public static final long A6_H6 = A5_H5 << 8;
    public static final long A7_H7 = A6_H6 << 8;
    public static final long A8_H8 = A7_H7 << 8;
    public static final long A1_A8 = generateVertical(0);
    public static final long B1_B8 = generateVertical(1);
    public static final long C1_C8 = generateVertical(2);
    public static final long D1_D8 = generateVertical(3);
    public static final long E1_E8 = generateVertical(4);
    public static final long F1_F8 = generateVertical(5);
    public static final long G1_G8 = generateVertical(6);
    public static final long H1_H8 = generateVertical(7);


    // 64 elements, each element is a bitboard where 1 are representing the valid orthogonals of this square
    public static final long[] orthogonals = {
            // First row of the board
            A1_A8 | A1_H1, B1_B8 | A1_H1, C1_C8 | A1_H1, D1_D8 | A1_H1, E1_E8 | A1_H1, F1_F8 | A1_H1, G1_G8 | A1_H1, H1_H8 | A1_H1,
            // Second row of the board
            A1_A8 | A2_H2, B1_B8 | A2_H2, C1_C8 | A2_H2, D1_D8 | A2_H2, E1_E8 | A2_H2, F1_F8 | A2_H2, G1_G8 | A2_H2, H1_H8 | A2_H2,
            // First row of the board
            A1_A8 | A3_H3, B1_B8 | A3_H3, C1_C8 | A3_H3, D1_D8 | A3_H3, E1_E8 | A3_H3, F1_F8 | A3_H3, G1_G8 | A3_H3, H1_H8 | A3_H3,
            // First row of the board
            A1_A8 | A4_H4, B1_B8 | A4_H4, C1_C8 | A4_H4, D1_D8 | A4_H4, E1_E8 | A4_H4, F1_F8 | A4_H4, G1_G8 | A4_H4, H1_H8 | A4_H4,
            // First row of the board
            A1_A8 | A5_H5, B1_B8 | A5_H5, C1_C8 | A5_H5, D1_D8 | A5_H5, E1_E8 | A5_H5, F1_F8 | A5_H5, G1_G8 | A5_H5, H1_H8 | A5_H5,
            // First row of the board
            A1_A8 | A6_H6, B1_B8 | A6_H6, C1_C8 | A6_H6, D1_D8 | A6_H6, E1_E8 | A6_H6, F1_F8 | A6_H6, G1_G8 | A6_H6, H1_H8 | A6_H6,
            // First row of the board
            A1_A8 | A7_H7, B1_B8 | A7_H7, C1_C8 | A7_H7, D1_D8 | A7_H7, E1_E8 | A7_H7, F1_F8 | A7_H7, G1_G8 | A7_H7, H1_H8 | A7_H7,
            // First row of the board
            A1_A8 | A8_H8, B1_B8 | A8_H8, C1_C8 | A8_H8, D1_D8 | A8_H8, E1_E8 | A8_H8, F1_F8 | A8_H8, G1_G8 | A8_H8, H1_H8 | A8_H8,
    };

    private static long generateVertical(int columnIndex) {
        assert columnIndex < 8 && columnIndex >= 0;
        long vertical = 0;
        for(int i = 0 ; i < 8; i++) {
            vertical |= 1L << columnIndex + 8*i;
        }

        return vertical;
    }
}
