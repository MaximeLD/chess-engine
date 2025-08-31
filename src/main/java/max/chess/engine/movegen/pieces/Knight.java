package max.chess.engine.movegen.pieces;

import max.chess.engine.common.Position;

public final class Knight {
    public static long getLegalMovesBB(int positionIndex, long friendlyOccupiedSquareBB) {
        long movesBB = getPseudoLegalMovesBB(positionIndex);
        movesBB &= ~friendlyOccupiedSquareBB;

        return movesBB;
    }

    public static long getAttackBB(int positionIndex) {
        return getPseudoLegalMovesBB(positionIndex);
    }

    private static long getPseudoLegalMovesBB(int positionIndex) {
        return KNIGHT_MOVES_BB[positionIndex];
    }
    public static long[] KNIGHT_MOVES_BB;

    static {
        generateKnightMovesBB();
    }

    public static void warmUp() {
        // To init the caches
    }

    private static void generateKnightMovesBB() {
        // 1 move per square
        KNIGHT_MOVES_BB = new long[64];

        for(int i=0;i<64;i++) {
            Position position = Position.of(i);
            KNIGHT_MOVES_BB[i]= generateKnightMovesAt(position);
        }
    }

    private static long generateKnightMovesAt(Position knightPosition) {
        int positionIndex = knightPosition.getFlatIndex();
        long movesBB = 0;

        // up - left
        if(knightPosition.getX() > 0 && knightPosition.getY() < 6)
            movesBB |= 1L << (positionIndex + 8 + 7);
        // up - right
        if(knightPosition.getX() < 7 && knightPosition.getY() < 6)
            movesBB |= 1L << (positionIndex + 8 + 9);
        // down - left
        if(knightPosition.getX() > 0 && knightPosition.getY() > 1)
            movesBB |= 1L << (positionIndex - 8 - 9);
        // down - right
        if(knightPosition.getX() < 7 && knightPosition.getY() > 1)
            movesBB |= 1L << (positionIndex - 8 - 7);
        // left - up
        if(knightPosition.getX() > 1 && knightPosition.getY() < 7)
            movesBB |= 1L << (positionIndex - 1 + 7);
        // left - down
        if(knightPosition.getX() > 1 && knightPosition.getY() > 0)
            movesBB |= 1L << (positionIndex - 1 - 9);
        // right - up
        if(knightPosition.getX() < 6 && knightPosition.getY() < 7)
            movesBB |= 1L << (positionIndex + 1 + 9);
        // right - down
        if(knightPosition.getX() < 6 && knightPosition.getY() > 0)
            movesBB |= 1L << (positionIndex + 1 - 7);

        return movesBB;
    }

}
