package max.chess.engine.movegen.pieces;

import max.chess.engine.common.Color;
import max.chess.engine.common.Position;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.movegen.utils.BitBoardUtils;

public final class Pawn {
    public static final long[] BLACK_PAWN_ATTACKING_MOVES_BB = new long[64];
    public static final long[] WHITE_PAWN_ATTACKING_MOVES_BB = new long[64];
    public static final long[] BLACK_PAWN_NON_ATTACKING_MOVES_BB = new long[64];
    public static final long[] WHITE_PAWN_NON_ATTACKING_MOVES_BB = new long[64];

    static {
        generatePawnNonAttackingMovesLookUp();
        generatePawnAttackingMovesLookUp();
    }

    public static void warmUp() {
        // To init the caches
    }

    public static long getPseudoLegalMovesBB(int positionIndex, int color, long occupiedSquaresBB, long occupiedSquaresEnemyBB) {
        long attackingMovesBB = getAttackBB(positionIndex, color) & occupiedSquaresEnemyBB;
        long nonAttackingMovesBB = getNonAttackBB(positionIndex, color);
        long nonAttackingMovesBBWithBlockers = nonAttackingMovesBB & ~occupiedSquaresBB;
        if(nonAttackingMovesBBWithBlockers != 0 && BitUtils.bitCount(nonAttackingMovesBB) > 1) {
            if(ColorUtils.isWhite(color)) {
                if (BitUtils.bitScanForward(nonAttackingMovesBB) != BitUtils.bitScanForward(nonAttackingMovesBBWithBlockers)) {
                    nonAttackingMovesBBWithBlockers = 0;
                }
            } else {
                if (BitUtils.bitScanBackward(nonAttackingMovesBB) != BitUtils.bitScanBackward(nonAttackingMovesBBWithBlockers)) {
                    nonAttackingMovesBBWithBlockers = 0;
                }
            }
        }

        return attackingMovesBB | nonAttackingMovesBBWithBlockers;
    }

    public static long getAttackBB(int pawnPosition, int color) {
        return ColorUtils.isWhite(color)
                ? WHITE_PAWN_ATTACKING_MOVES_BB[pawnPosition]
                : BLACK_PAWN_ATTACKING_MOVES_BB[pawnPosition];
    }

    // Batch generation
    public static long getAttackBB(long pawnBB, int color) {
        return ColorUtils.isWhite(color)
                ? BitBoardUtils.shift(pawnBB, BitBoardUtils.Direction.NORTHEAST) | BitBoardUtils.shift(pawnBB, BitBoardUtils.Direction.NORTHWEST)
                : BitBoardUtils.shift(pawnBB, BitBoardUtils.Direction.SOUTHEAST) | BitBoardUtils.shift(pawnBB, BitBoardUtils.Direction.SOUTHWEST)
                ;
    }

    private static long getNonAttackBB(int pawnPosition, int color) {
        return ColorUtils.isWhite(color)
                ? WHITE_PAWN_NON_ATTACKING_MOVES_BB[pawnPosition]
                : BLACK_PAWN_NON_ATTACKING_MOVES_BB[pawnPosition];
    }

    private static void generatePawnAttackingMovesLookUp() {
        for(int i=0;i<64;i++) {
            Position position = Position.of(i);
            WHITE_PAWN_ATTACKING_MOVES_BB[i]= generatePawnAttackingMovesAt(position, Color.WHITE);
            BLACK_PAWN_ATTACKING_MOVES_BB[i]= generatePawnAttackingMovesAt(position, Color.BLACK);
        }
    }

    private static void generatePawnNonAttackingMovesLookUp() {
        for(int i=0;i<64;i++) {
            Position position = Position.of(i);
            WHITE_PAWN_NON_ATTACKING_MOVES_BB[i]= generatePawnNonAttackingMovesAt(position, Color.WHITE);
            BLACK_PAWN_NON_ATTACKING_MOVES_BB[i]= generatePawnNonAttackingMovesAt(position, Color.BLACK);
        }
    }

    private static long generatePawnNonAttackingMovesAt(Position position, Color color) {
        if (position.getY() == 0 || position.getY() == 7) {
            return 0;
        }

        long pawnMovesBB = 0;
        int positionIndex = position.getFlatIndex();

        // 2 square moves
        // If we are eligible to 2-square move
        if (color == Color.WHITE && position.getY() == 1) {
            pawnMovesBB |= 1L << (positionIndex + 16);
        } else if (color == Color.BLACK && position.getY() == 6) {
            pawnMovesBB |= 1L << (positionIndex - 16);
        }

        // 1 square move
        if (color == Color.WHITE) {
            pawnMovesBB |= 1L << (positionIndex + 8);
        } else {
            pawnMovesBB |= 1L << (positionIndex - 8);
        }

        return pawnMovesBB;
    }

    private static long generatePawnAttackingMovesAt(Position position, Color color) {
        if ((position.getY() == 0 && color == Color.BLACK) || (color == Color.WHITE && position.getY() == 7)) {
            return 0;
        }

        long pawnMovesBB = 0;
        int positionIndex = position.getFlatIndex();

        if(color == Color.WHITE) {
            if(position.getX() > 0) {
                // up-left
                pawnMovesBB |= 1L << positionIndex + 7;
            }

            if(position.getX() < 7) {
                // up-right
                pawnMovesBB |= 1L << positionIndex + 9;
            }
        } else {
            if(position.getX() > 0) {
                // down-left
                pawnMovesBB |= 1L << positionIndex - 9;
            }

            if(position.getX() < 7) {
                // down-right
                pawnMovesBB |= 1L << positionIndex - 7;
            }
        }

        return pawnMovesBB;
    }
}
