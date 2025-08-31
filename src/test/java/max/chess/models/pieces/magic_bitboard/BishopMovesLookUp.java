package max.chess.models.pieces.magic_bitboard;

import max.chess.engine.movegen.utils.BitBoardUtils;
import max.chess.engine.movegen.utils.DiagonalMoveUtils;
import max.chess.engine.utils.BitUtils;

import java.util.HashMap;
import java.util.Map;

public class BishopMovesLookUp {
    public static final Map<Integer, Map<Long, Long>> ORIGINAL_MOVES;
    public static final Long[] BISHOP_MOVES_LOOKUP;

    public static final Integer[] RIGHT_SHIFTS =
            {58, 59, 59, 59, 59, 59, 59, 58, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 57, 57, 57, 57, 59, 59, 59, 59, 57, 55, 55, 57, 59, 59, 59, 59, 57, 55, 55, 57, 59, 59, 59, 59, 57, 57, 57, 57, 59, 59, 59, 59, 59, 59, 59, 59, 59, 59, 58, 59, 59, 59, 59, 59, 59, 58};

    public static final Long[] MAGIC_NUMBERS =
            {2312633603831693356L, 2306416962940503560L, -6898876911171075968L, 685677456396451904L, 579856336368443540L, 4723639660838912L, 72646979585376288L, 667095849442218000L, 2308099241706012808L, 864711023315157504L, -8881094048865714112L, -9223270864462020576L, 36039800994203778L, 288234920365522952L, 731839339843297280L, -9151170404477304320L, 189151219246236256L, 562984447460361L, 18577365653520640L, -9203105836215877630L, 654147854752810000L, 4616752569094643760L, 6413213847503777792L, 19707655576764928L, 884992512257819168L, 2964283885421572L, 36068379571799040L, 1155182100515782688L, 145135543345156L, -9214285122956648444L, 578713376784523784L, 46162446005703428L, 2330999015088128L, 289358509441716288L, 317277824614440L, 72059795209191552L, 363674474593148936L, 441431939056943184L, 180746659201042436L, 1153242584013898272L, 2316015828836829204L, 1298165994337862148L, -9222807984084596735L, 18067458669674624L, 577938564935846416L, 225217385437594112L, 38299324007055873L, 76002642866225296L, 36248708203937804L, 4543272845312000L, 155375305992699972L, 576469549503873062L, 9570768255582210L, 4630901668557422608L, 198303536388259840L, 369437015244210176L, 3461034464184901888L, 18055097628624960L, 5189272747953489920L, 353022964172816L, 2594227317532271754L, 594484084412187137L, 8839312442112L, 4612816385201013396L};

    public static final Integer[] POSITION_LOOKUP_SHIFT = new Integer[64];
    static {
        ORIGINAL_MOVES = new HashMap<>();
        int currentShift = 0;
        for(int i = 0 ; i < 64 ; i++) {
            POSITION_LOOKUP_SHIFT[i] = currentShift;
            currentShift += 1 << (64 - RIGHT_SHIFTS[i]);
        }

        final int lookUpSize = currentShift;

        BISHOP_MOVES_LOOKUP = new Long[lookUpSize];

        generateBishopLookupTable();
        long bitboardSize = lookUpSize * 8L; // A long is 8 bytes
        System.out.println("Bishop Magic Bitboard initialized ; size used : "+bitboardSize+ "B");

    }

    public static void warmUp() {
        // To init the caches
    }

    public static long getMagicBitboardSize(Integer[] rightShifts) {
        int currentShift = 0;
        for(int i = 0 ; i < 64 ; i++) {
            currentShift += 1 >>> (rightShifts[i]);
        }

        final int lookUpSize = currentShift;

        // A long is 8 bytes
        return lookUpSize*8L;
    }

    public static int buildIndex(int positionIndex, long blockerBB) {
        return (int) ((blockerBB * MAGIC_NUMBERS[positionIndex]) >>> RIGHT_SHIFTS[positionIndex]);
    }

    public static long buildIndex(long blockerBB, long magicNumber, long rightShift) {
        return (blockerBB * magicNumber) >>> rightShift;
    }

    public static long getBishopMoves(int positionIndex, long blockerBB) {
        return BISHOP_MOVES_LOOKUP[POSITION_LOOKUP_SHIFT[positionIndex] + buildIndex(positionIndex, blockerBB)];
//        return ORIGINAL_MOVES.get(positionIndex).get(blockerBB);
    }

    private static void generateBishopLookupTable() {
        // For each square of the board, we generate all possible blocker boards
        for(int i = 0 ; i < 64 ; i++) {
            ORIGINAL_MOVES.put(i, new HashMap<>());

            long bishopMovementMask = DiagonalMoveUtils.diagonals[i];
            long[] blockerBitboards = BitBoardUtils.createAllBlockersBitBoard(bishopMovementMask);

            for(long blockerBB : blockerBitboards) {
                blockerBB &= ~BitBoardUtils.BORDER_BB;
                blockerBB &= DiagonalMoveUtils.diagonals[i];
                long legalMovesBB = getBishopMovesBB(i, blockerBB);
                ORIGINAL_MOVES.get(i).put(blockerBB, legalMovesBB);
                BISHOP_MOVES_LOOKUP[POSITION_LOOKUP_SHIFT[i]+buildIndex(i, blockerBB)] = legalMovesBB;
            }
        }
    }

    private static long getBishopMovesBB(int positionIndex, long blockerBB) {
        long bishopMovesBB = 0;
        long positionBitMask = BitUtils.getPositionIndexBitMask(positionIndex);

        long currentPositionBitMask;

        // Moving up-right
        currentPositionBitMask = positionBitMask << 9;
        while((currentPositionBitMask & DiagonalMoveUtils.diagonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            bishopMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask <<= 9;
        }

        // Moving up-left
        currentPositionBitMask = positionBitMask << 7;
        while((currentPositionBitMask & DiagonalMoveUtils.diagonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            bishopMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask <<= 7;
        }

        // Moving down-right
        currentPositionBitMask = positionBitMask >>> 7;
        while((currentPositionBitMask & DiagonalMoveUtils.diagonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            bishopMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask >>>= 7;
        }

        // Moving down-left
        currentPositionBitMask = positionBitMask >>> 9;
        while((currentPositionBitMask & DiagonalMoveUtils.diagonals[BitUtils.bitScanForward(positionBitMask)]) != 0) {
            bishopMovesBB |= currentPositionBitMask;
            if((currentPositionBitMask & blockerBB) == currentPositionBitMask) {
                // We are on a blocker, we cannot go further !
                break;
            }
            currentPositionBitMask >>>= 9;
        }

        bishopMovesBB &= DiagonalMoveUtils.diagonals[positionIndex];
        return bishopMovesBB;
    }
}
