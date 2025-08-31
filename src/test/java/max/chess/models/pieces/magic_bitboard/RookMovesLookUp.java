package max.chess.models.pieces.magic_bitboard;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import max.chess.engine.movegen.utils.BitBoardUtils;
import max.chess.engine.movegen.utils.OrthogonalMoveUtils;
import max.chess.engine.utils.BitUtils;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RookMovesLookUp {
    public static final Map<Integer, Map<Long, Long>> ORIGINAL_MOVES = new HashMap<>();

    private static final Long[] ROOK_MOVES_LOOKUP;
    public static final LongArrayList FAST_ROOK_MOVES_LOOKUP;

    public static final IntArrayList RIGHT_SHIFTS = new IntArrayList(
            new int[]{49, 50, 51, 51, 51, 51, 52, 51, 50, 53, 53, 53, 53, 53, 54, 50, 50, 53, 53, 53, 53, 53, 54, 51, 50, 53, 53, 53, 53, 53, 54, 50, 50, 53, 53, 53, 53, 53, 54, 51, 51, 53, 53, 53, 53, 53, 54, 50, 52, 53, 53, 53, 53, 53, 54, 52, 51, 51, 51, 51, 51, 51, 52, 51}
    );
    public static final IntArrayList MAX_INDEXES = new IntArrayList(
            new int[]{32765, 16383, 8191, 8191, 8191, 8191, 4095, 8191, 15157, 2047, 2047, 2047, 2047, 2047, 1023, 16349, 16382, 2038, 2047, 2047, 2047, 2047, 1023, 8191, 16383, 2047, 2047, 2047, 2047, 2047, 1023, 16376, 16382, 2047, 2047, 2047, 2047, 2047, 1023, 8191, 8191, 2047, 2047, 2047, 2047, 2047, 1023, 16383, 4095, 2047, 2047, 2047, 2047, 2047, 1023, 4095, 8191, 8191, 8191, 8191, 8191, 8191, 4095, 8191}
    );
    public static final LongArrayList MAGIC_NUMBERS = new LongArrayList(
            new long[]{-4264768109093502976L, -9150750942570739712L, 144117421527015488L, 144117455953613088L, -8791024239092944752L, 144116566794174616L, 5476379364295868676L, 8214565858844935186L, 8797369171968L, 4573971861217280L, 4612249039260360840L, -4534702577937010688L, 666673491331785728L, 594756660216463618L, 19140401772765460L, 7698730979392L, 4815472068608L, 307370709005373792L, 9079767567454216L, 94575867119806464L, 288232575311286276L, 1099646107650L, 36121156567238736L, 576462968514936864L, 281492290813952L, 2306423571754262528L, 17598629027845L, 292734819741206528L, 1424975659802632L, 36033197213089795L, 649645397300480002L, 69021468704L, 4672928710656L, 9007542856319016L, 2469186461073678338L, 2882871453122824192L, 5764642999766092048L, 74608753105700865L, 2395320507961361L, 137472770176L, 633800273461248L, 1315192653382484001L, 144132917705113608L, 2322478133280784L, 72638153894789128L, 292751654804652035L, 1297055453133668360L, 4611688218558103808L, 36295971717632L, 19211253171937792L, 4574039244802560L, 578748578442248448L, 153142281619378432L, 1163621951871649024L, 3819061348839588864L, -5764598176644267520L, 308278005367106L, 18416895268898L, 8865888349258L, 40699379081218L, 554186048514L, 3455502534658L, 867734423556L, 4611686091446067330L}
    );

    public static final IntArrayList POSITION_LOOKUP_SHIFT = new IntArrayList(100);
    static {
        int currentShift = 0;
        for(int i = 0 ; i < 64 ; i++) {
            POSITION_LOOKUP_SHIFT.add(i, currentShift);
            currentShift += MAX_INDEXES.getInt(i) + 1;
        }

        final int lookUpSize = currentShift;

        ROOK_MOVES_LOOKUP = new Long[lookUpSize];
        Arrays.fill(ROOK_MOVES_LOOKUP, 0L);
        generateRookLookupTable();
        FAST_ROOK_MOVES_LOOKUP = new LongArrayList(Arrays.asList(ROOK_MOVES_LOOKUP));
        long bitboardSize = lookUpSize * 8L; // A long is 8 bytes
        System.out.println("Rook Magic Bitboard initialized ; size used : "+bitboardSize+ "B");
    }

    public static void warmUp() {
        // To init the caches
    }

    public static long getMagicBitboardSize(List<Integer> rightShifts) {
        int currentShift = 0;
        for(int i = 0 ; i < 64 ; i++) {
            currentShift += 1 << (64 - rightShifts.get(i));
        }

        final int lookUpSize = currentShift;

        // A long is 8 bytes
        return lookUpSize*8L;
    }

    public static int buildIndex(int positionIndex, long blockerBB) {
        return (int) ((blockerBB * MAGIC_NUMBERS.getLong(positionIndex)) >>> RIGHT_SHIFTS.getInt(positionIndex));
    }

    public static long buildIndex(long blockerBB, long magicNumber, long rightShift) {
        return (blockerBB * magicNumber) >>> rightShift;
    }

    public static long getRookMoves(int positionIndex, long blockerBB) {
//        return FAST_ROOK_MOVES_LOOKUP.getLong(POSITION_LOOKUP_SHIFT.getInt(positionIndex) + buildIndex(positionIndex, blockerBB));
        return ROOK_MOVES_LOOKUP[POSITION_LOOKUP_SHIFT.getInt(positionIndex) + buildIndex(positionIndex, blockerBB)];
    }

    private static void generateRookLookupTable() {
        // For each square of the board, we generate all possible blocker boards
        for(int i = 0 ; i < 64 ; i++) {
            ORIGINAL_MOVES.put(i, new HashMap<>());
            long rookMovementMask = OrthogonalMoveUtils.orthogonals[i];
            long[] blockerBitboards = BitBoardUtils.createAllBlockersBitBoard(rookMovementMask);

            int maximumIndex = -1;
            for(long blockerBB : blockerBitboards) {
                if((BitUtils.getPositionIndexBitMask(i) & BitBoardUtils.BORDER_BB) == 0) {
                    // We are not on a border, we can simplify a bit
                    // We don't need to evaluate the blockers including the borders of the board,
                    // as they cannot prevent a move
                    blockerBB &= ~BitBoardUtils.BORDER_BB;

                    // TODO we could simplify further by just not removing the borders we are on (i.e; remove opposite borders)
                }
                long legalMovesBB = getRookMovesBB(i, blockerBB);
                int index = buildIndex(i, blockerBB);
                if(maximumIndex < index) {
                    maximumIndex = index;
                }

                ROOK_MOVES_LOOKUP[POSITION_LOOKUP_SHIFT.getInt(i) + index] = legalMovesBB;
                ORIGINAL_MOVES.get(i).put(blockerBB, legalMovesBB);
            }
//            MAX_INDEXES[i] = maximumIndex;
        }

//        System.out.println("MAX INDEXES ROOK:");
//        System.out.println("{"+ Arrays.stream(MAX_INDEXES).map(String::valueOf).collect(Collectors.joining(", "))+"};");
    }

    private static long getRookMovesBB(int positionIndex, long blockerBB) {
        long rookMovesBB = 0;
        long positionBitMask = BitUtils.getPositionIndexBitMask(positionIndex);

        long currentPositionBitMask;

        // Moving up
        currentPositionBitMask = positionBitMask << 8;
        while((currentPositionBitMask & OrthogonalMoveUtils.orthogonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            rookMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask <<= 8;
        }

        // Moving down
        currentPositionBitMask = positionBitMask >>> 8;
        while((currentPositionBitMask & OrthogonalMoveUtils.orthogonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            rookMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask >>>= 8;
        }

        // Moving left
        currentPositionBitMask = positionBitMask >>> 1;
        while((currentPositionBitMask & OrthogonalMoveUtils.orthogonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            rookMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask >>>= 1;
        }

        // Moving right
        currentPositionBitMask = positionBitMask << 1;
        while((currentPositionBitMask & OrthogonalMoveUtils.orthogonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            rookMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask <<= 1;
        }

        // Keeping only legal squares
        rookMovesBB &= OrthogonalMoveUtils.orthogonals[positionIndex];

        return rookMovesBB;
    }
}
