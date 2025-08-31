package max.chess.models.pieces.zobrist.v2;

import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.game.Game;
import max.chess.engine.game.ZobristHashKeys;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

public class ZobristHashKeysTest {
    private static final Long ZOBRIST_VALUE_STANDARD = ZobristHashKeys.readHexa("463b96181691fc9c");
    private static final Map<String, Long> ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD;

    static {
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD = new TreeMap<>();
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4", ZobristHashKeys.readHexa("823c9b50fd114196"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4 d7d5", ZobristHashKeys.readHexa("0756b94461c50fb0"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4 d7d5 e4e5", ZobristHashKeys.readHexa("662fafb965db29d4"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4 d7d5 e4e5 f7f5", ZobristHashKeys.readHexa("22a48b5a8e47ff78"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4 d7d5 e4e5 f7f5 e1e2", ZobristHashKeys.readHexa("652a607ca3f242c1"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("e2e4 d7d5 e4e5 f7f5 e1e2 e8f7", ZobristHashKeys.readHexa("00fdd303c946bdd9"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("a2a4 b7b5 h2h4 b5b4 c2c4", ZobristHashKeys.readHexa("3c8123ea7b067637"));
        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.put("a2a4 b7b5 h2h4 b5b4 c2c4 b4c3 a1a3", ZobristHashKeys.readHexa("5c3f9b829b279560"));
    }

    @Test
    public void testHashing() {
        Game game = BoardGenerator.newStandardGameBoard();
        testHash(game, ZOBRIST_VALUE_STANDARD);

        ZOBRIST_VALUES_FOR_MOVES_FROM_STANDARD.forEach((moves, expectedValue) -> {
            Game newGame = BoardGenerator.newStandardGameBoard();
            try {
                newGame.playMoves(moves);
                testHash(newGame, expectedValue);
            } catch(AssertionError e) {
                System.out.println("INVALID HASH KEY");
                System.out.println("FEN: "+ FENUtils.getFENFromBoard(game));
                System.out.println("Expected: "+ ZobristHashKeys.print(expectedValue));
                System.out.println("Actual: "+ ZobristHashKeys.print(ZobristHashKeys.getHashKey(game)));
                System.out.println("Moves: "+ moves);
                assert false;
            }
        });
    }

    public static void testHash(Game game, long expectedValue) {
        long actualValue = ZobristHashKeys.getHashKey(game);
        if(Long.compareUnsigned(actualValue, expectedValue) != 0) {
            System.out.println("INVALID HASH KEY");
            System.out.println("FEN: "+ FENUtils.getFENFromBoard(game));
            System.out.println("Expected: "+ZobristHashKeys.print(expectedValue));
            System.out.println("Actual: "+ZobristHashKeys.print(actualValue));
        }

        assert actualValue == expectedValue;
    }
}
