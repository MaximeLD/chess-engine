package max.chess.engine.game;

import max.chess.engine.game.board.MovePlayed;

public record GameChanges(MovePlayed movePlayed, int previousHalfMoveClock,
                          boolean previousWhiteCanCastleKingSide, boolean previousWhiteCanCastleQueenSide,
                          boolean previousBlackCanCastleKingSide, boolean previousBlackCanCastleQueenSide) {
    // Don't build a GameChanges object if you're on the hotpath, rely on the static below instead
    @Deprecated
    public GameChanges {
    }

    private static final int PREVIOUS_WHITE_CAN_CASTLE_KING_SIDE_MASK = 0b1;
    private static final int PREVIOUS_WHITE_CAN_CASTLE_QUEEN_SIDE_MASK = 0b10;
    private static final int PREVIOUS_BLACK_CAN_CASTLE_KING_SIDE_MASK = 0b100;
    private static final int PREVIOUS_BLACK_CAN_CASTLE_QUEEN_SIDE_MASK = 0b1000;
    private static final int PREVIOUS_HALF_MOVE_CLOCK_MASK = 0b1111110000;
    private static final long PREVIOUS_EPOCH_MASK = 0b11111111110000000000L;
    private static final long MOVE_PLAYED_MASK = ~0b11111111111111111111L;

    public static long asBytes(long movePlayed, int previousHalfMoveClock, boolean previousWhiteCanCastleKingSide,
                              boolean previousWhiteCanCastleQueenSide, boolean previousBlackCanCastleKingSide,
                              boolean previousBlackCanCastleQueenSide, int previousEpoch) {
        return (movePlayed << 20)
                | ((long) previousEpoch << 10)
                | ((long) previousHalfMoveClock << 4)
                | ((previousBlackCanCastleQueenSide ? 1 : 0) << 3)
                | ((previousBlackCanCastleKingSide ? 1 : 0) << 2)
                | ((previousWhiteCanCastleQueenSide ? 1 : 0) << 1)
                | ((previousWhiteCanCastleKingSide ? 1 : 0))
                ;
    }

    public static boolean getPreviousWhiteCanCastleKingSide(long bytes) {
        return (bytes & PREVIOUS_WHITE_CAN_CASTLE_KING_SIDE_MASK) != 0;
    }
    public static boolean getPreviousWhiteCanCastleQueenSide(long bytes) {
        return (bytes & PREVIOUS_WHITE_CAN_CASTLE_QUEEN_SIDE_MASK) != 0;
    }
    public static boolean getPreviousBlackCanCastleKingSide(long bytes) {
        return (bytes & PREVIOUS_BLACK_CAN_CASTLE_KING_SIDE_MASK) != 0;
    }
    public static boolean getPreviousBlackCanCastleQueenSide(long bytes) {
        return (bytes & PREVIOUS_BLACK_CAN_CASTLE_QUEEN_SIDE_MASK) != 0;
    }

    public static int getPreviousHalfMoveClock(long bytes) {
        return (int) ((bytes & PREVIOUS_HALF_MOVE_CLOCK_MASK) >>> 4);
    }

    public static int getPreviousEpoch(long bytes) {
        return (int) ((bytes & PREVIOUS_EPOCH_MASK) >>> 10);
    }

    public static int getMovePlayed(long bytes) {
        return (int) ((bytes & MOVE_PLAYED_MASK) >>> 20);
    }

}