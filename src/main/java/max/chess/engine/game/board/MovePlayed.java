package max.chess.engine.game.board;

import max.chess.engine.movegen.Move;

public record MovePlayed(byte pieceType, Move move, boolean enPassant, boolean castleKingSide, boolean castleQueenSide,
                         byte pieceEaten, int previousEnPassantIndex) {
    // Don't build a MovePlayed object if you're on the hotpath, rely on the static below instead
    @Deprecated
    public MovePlayed {
    }

    private static final long MOVE_MASK = ~0b1111111111L;
    private static final int PIECE_EATEN_MASK = 0b1110000000;
    private static final int PREVIOUS_EN_PASSANT_MASK = 0b1111111;

    public static int asBytes(int move, byte pieceEaten, int previousEnPassantIndex) {
        return (move << 10) | (pieceEaten << 7) | (previousEnPassantIndex+1);
    }

    public static int getMove(int bytes) {
        return (int) ((bytes & MOVE_MASK) >>> 10);
    }
    public static byte getPieceEaten(int bytes) {
        return (byte) ((bytes & PIECE_EATEN_MASK) >>> 7);
    }
    public static int getPreviousEnPassantIndex(int bytes) {
        return (bytes & PREVIOUS_EN_PASSANT_MASK) - 1;
    }
}
