package max.chess.engine.utils;

public final class BitUtils {

    /**
     * Returns the index of the first (<i>rightmost</i>) bit set to 1 in the bitboard provided in input. The bit is the
     * Least Significant 1-bit (LS1B).
     *
     * @param bb the bitboard for which the LS1B is to be returned
     * @return the index of the first bit set to 1
     */
    public static int bitScanForward(long bb) {
        return Long.numberOfTrailingZeros(bb);
    }
    public static int bitScanBackward(long bb) {
        return Long.numberOfLeadingZeros(bb);
    }

    public static long getPositionIndexBitMask(int positionIndex) {
        return 1L << positionIndex;
    }

    public static int bitCount(long bb) {
        return Long.bitCount(bb);
    }
}
