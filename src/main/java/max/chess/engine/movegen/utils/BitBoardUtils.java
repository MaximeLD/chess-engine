package max.chess.engine.movegen.utils;

import max.chess.engine.utils.BitUtils;

import java.util.ArrayList;
import java.util.List;

public final class BitBoardUtils {
    public final static long BORDER_BB = 0b11111111L | 0b11111111L << 56
            | 1L << 8 | 1L << 16 | 1L << 24 | 1L << 32 | 1L << 40 | 1L << 48
            | 1L << 15 | 1L << 23 | 1L << 31 | 1L << 39 | 1L << 47 | 1L << 55;

    public enum Direction {
        NORTH, SOUTH, EAST, WEST, NORTHEAST, NORTHWEST, SOUTHEAST, SOUTHWEST
    }

    /**
     * The bitboard representing the light squares on a chessboard.
     */
    public static final long lightSquares = 0x55AA55AA55AA55AAL;
    /**
     * The bitboard representing the dark squares on a chessboard.
     */
    public static final long darkSquares = 0xAA55AA55AA55AA55L;

    /**
     * The bitboards representing the ranks on a chessboard. Bitboard at index 0
     * identifies the 1st rank on a board, bitboard at index 1 the 2nd rank, etc.
     */
    final static long[] rankBB = { 0x00000000000000FFL, 0x000000000000FF00L, 0x0000000000FF0000L, 0x00000000FF000000L,
            0x000000FF00000000L, 0x0000FF0000000000L, 0x00FF000000000000L, 0xFF00000000000000L };
    /**
     * The bitboards representing the files on a chessboard. Bitboard at index 0
     * identifies the 1st file on a board, bitboard at index 1 the 2nd file, etc.
     */
    final static long[] fileBB = { 0x0101010101010101L, 0x0202020202020202L, 0x0404040404040404L, 0x0808080808080808L,
            0x1010101010101010L, 0x2020202020202020L, 0x4040404040404040L, 0x8080808080808080L };

    public static long generateRayAttack(int positionIndex, BitBoardUtils.Direction direction, long occ)
    {
        long attack = 0L;
        long sqBB = BitUtils.getPositionIndexBitMask(positionIndex);

        while (true)
        {
            sqBB = BitBoardUtils.shift(sqBB, direction);
            attack |= sqBB;

            if (sqBB == 0 || 0L != (attack & occ))
            {
                break;
            }
        }

        return attack;
    }

    public static long shift(long bitboard, Direction direction) {
        return switch (direction) {
            case NORTH -> bitboard << 8;
            case SOUTH -> bitboard >>> 8;
            case EAST -> (bitboard & ~fileBB[7]) << 1;
            case WEST -> (bitboard & ~fileBB[0]) >>> 1;
            case NORTHEAST -> (bitboard & ~fileBB[7]) << 9;
            case NORTHWEST -> (bitboard & ~fileBB[0]) << 7;
            case SOUTHEAST -> (bitboard & ~fileBB[7]) >>> 7;
            case SOUTHWEST -> (bitboard & ~fileBB[0]) >>> 9;
        };
    }

    public static long[] createAllBlockersBitBoard(long movementMask) {
        // We start by extracting the index of each accessible square from the movementMask
        List<Integer> moveSquareIndices = new ArrayList<>();
        for (int i = 0; i < 64; i++) {
            if(((movementMask >> i) & 1) == 1) {
                moveSquareIndices.add(i);
            }
        }

        // Total number of bitboards (one per arrangement of pieces)
        int numberOfBitboards = 1 << moveSquareIndices.size(); // 2^N
        long[] blockers = new long[numberOfBitboards];

        // Let's now create each blocker bitboard
        for(int blockerIndex = 0; blockerIndex < numberOfBitboards; blockerIndex++) {
            // We need to loop through each candidate bit for the bitboard
            for(int bitIndex = 0; bitIndex < moveSquareIndices.size(); bitIndex++) {
                int bit = (blockerIndex >> bitIndex) & 1;
                blockers[blockerIndex] |= (long) bit << moveSquareIndices.get(bitIndex);
            }
        }

        return blockers;
    }
}
