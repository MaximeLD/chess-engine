package max.chess.engine.movegen.pieces;

import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.common.Position;
import max.chess.engine.utils.BitUtils;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.game.board.Board;

public final class King {
    public static final long[] KING_MOVES_BB = new long[64];
    private static final long BIT_MASK_KING_CASTLE_WHITE_PASSAGE_SQUARES = (0b11L << 5);
    private static final long BIT_MASK_QUEEN_CASTLE_WHITE_PASSAGE_SQUARES = (0b111L << 1);
    private static final long BIT_MASK_KING_CASTLE_BLACK_PASSAGE_SQUARES = (0b11L << 61);
    private static final long BIT_MASK_QUEEN_CASTLE_BLACK_PASSAGE_SQUARES = (0b111L << 57);

    public static void warmUp() {
        generateKingMovesBB();
    }

    public static long getEvasionMovesBB(int positionIndex, long enemyAttackBB, long friendlySquaresBB) {
        return getPseudoLegalMovesBB(positionIndex) & ~enemyAttackBB & ~friendlySquaresBB;
    }

    public static long getNonCastleLegalMovesBB(int kingColor, int positionIndex, long friendlySquaresOccupiedBB, Board board) {
        long kingMovesBitboard = getPseudoLegalMovesBB(positionIndex);
        int oppositeColor = ColorUtils.switchColor(kingColor);

        kingMovesBitboard &= ~friendlySquaresOccupiedBB;

        long kingMovesCheckSafeBB = 0;
        while(kingMovesBitboard != 0) {
            int nextMoveIndex = BitUtils.bitScanForward(kingMovesBitboard);
            kingMovesBitboard &= kingMovesBitboard-1;
            if(MoveGenerator.getCheckersBB(nextMoveIndex, board, oppositeColor, true) == 0) {
                kingMovesCheckSafeBB |= BitUtils.getPositionIndexBitMask(nextMoveIndex);
            }
        }
        return kingMovesCheckSafeBB;
    }

    public static boolean isCastleKingSideLegal(int kingColor, long squaresOccupiedBB, boolean isKingInCheck,
                                                boolean canCastleKingSide, Board board) {
        if(!canCastleKingSide || isKingInCheck) {
            return false;
        }

        int oppositeColor = ColorUtils.switchColor(kingColor);

        if(ColorUtils.isWhite(kingColor)
                && (squaresOccupiedBB & BIT_MASK_KING_CASTLE_WHITE_PASSAGE_SQUARES) == 0) {
            boolean isPassageFree = MoveGenerator.getCheckersBB(5, board, oppositeColor, true) == 0;
            if(isPassageFree) {
                // passage is free for castle
                return MoveGenerator.getCheckersBB(6, board, oppositeColor, true) == 0;
            }
        } else if(ColorUtils.isBlack(kingColor)
                && (squaresOccupiedBB & BIT_MASK_KING_CASTLE_BLACK_PASSAGE_SQUARES) == 0) {
            boolean isPassageFree = MoveGenerator.getCheckersBB(61, board, oppositeColor, true) == 0;
            if(isPassageFree) {
                // passage is free for castle
                return MoveGenerator.getCheckersBB(62, board, oppositeColor, true) == 0;
            }
        }

        return false;
    }

    public static boolean isCastleQueenSideLegal(int kingColor, long squaresOccupiedBB, boolean isKingInCheck,
                                                boolean canCastleQueenSide, Board board) {
        if(!canCastleQueenSide || isKingInCheck) {
            return false;
        }

        int oppositeColor = ColorUtils.switchColor(kingColor);

        if(ColorUtils.isWhite(kingColor)
                && (squaresOccupiedBB & BIT_MASK_QUEEN_CASTLE_WHITE_PASSAGE_SQUARES) == 0) {
            boolean isPassageFree = MoveGenerator.getCheckersBB(3, board, oppositeColor, true) == 0;
            if(isPassageFree) {
                // passage is free for castle
                return MoveGenerator.getCheckersBB(2, board, oppositeColor, true) == 0;
            }
        } else if(ColorUtils.isBlack(kingColor)
                && (squaresOccupiedBB & BIT_MASK_QUEEN_CASTLE_BLACK_PASSAGE_SQUARES) == 0) {
            boolean isPassageFree = MoveGenerator.getCheckersBB(59, board, oppositeColor, true) == 0;
            if(isPassageFree) {
                // passage is free for castle
                return MoveGenerator.getCheckersBB(58, board, oppositeColor, true) == 0;
            }
        }

        return false;
    }

    public static long getAttackBB(int positionIndex) {
        return getPseudoLegalMovesBB(positionIndex);
    }

    private static long getPseudoLegalMovesBB(int positionIndex) {
        return KING_MOVES_BB[positionIndex];
    }

    private static void generateKingMovesBB() {
        for(int i=0;i<64;i++) {
            Position position = Position.of(i);
            KING_MOVES_BB[i]= generateKingMovesAt(position);
        }
    }

    private static long generateKingMovesAt(Position kingPosition) {
        long kingMovesBitboard = 0;
        int positionIndex = kingPosition.getFlatIndex();

        // move in any of the 8 directions
        // right
        if(kingPosition.getX() < 7) {
            kingMovesBitboard |= 1L << (positionIndex + 1);
        }
        // left
        if(kingPosition.getX() > 0) {
            kingMovesBitboard |= 1L << (positionIndex - 1);
        }

        // up
        if(kingPosition.getY() < 7) {
            kingMovesBitboard |= 1L << (positionIndex + 8);
        }

        // down
        if(kingPosition.getY() > 0) {
            kingMovesBitboard |= 1L << (positionIndex - 8);
        }

        // down - left
        if(kingPosition.getY() > 0 && kingPosition.getX() > 0) {
            kingMovesBitboard |= 1L << (positionIndex - 9);
        }

        // down - right
        if(kingPosition.getY() > 0 && kingPosition.getX() < 7) {
            kingMovesBitboard |= 1L << (positionIndex - 7);
        }

        // up - left
        if(kingPosition.getY() < 7 && kingPosition.getX() > 0) {
            kingMovesBitboard |= 1L << (positionIndex + 7);
        }

        // up - right
        if(kingPosition.getY() < 7 && kingPosition.getX() < 7) {
            kingMovesBitboard |= 1L << (positionIndex + 9);
        }

        return kingMovesBitboard;
    }
}
