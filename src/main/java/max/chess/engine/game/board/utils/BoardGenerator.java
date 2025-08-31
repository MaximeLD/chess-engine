package max.chess.engine.game.board.utils;

 import max.chess.engine.game.Game;
 import max.chess.engine.utils.notations.FENUtils;

public class BoardGenerator {
    public static final String STANDARD_GAME = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    private static final String TEST_CASTLING_GAME = "r3k2r/pppppppp/8/8/8/8/PPPPPPPP/R3K2R w KQkq - 0 1";
    private static final String TEST_CHECKMATE_BLACK = "rnbqkbnr/pppppQpp/8/8/2B2P2/8/PPPPP1PP/RNB1K1NR b KQkq - 0 1";
    private static final String TEST_CHECKMATE_WHITE = "rnb1k1nr/pppppppp/8/2b5/8/8/PPPPPqPP/RNBQKBNR w KQkq - 0 1";
    private static final String TEST_PAT_WHITE = "1r1r4/2K5/8/2k5/8/8/8/8 w - - 0 1";
    private static final String TEST_PAT_BLACK = "1R1R4/2k5/8/2K5/8/8/8/8 b - - 0 1";
    public static Game newStandardGameBoard() {
        return FENUtils.getBoardFrom(STANDARD_GAME);
    }
    public static Game from(String FEN) {
        return FENUtils.getBoardFrom(FEN);
    }
}
