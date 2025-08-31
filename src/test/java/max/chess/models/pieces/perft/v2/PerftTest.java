package max.chess.models.pieces.perft.v2;

import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.game.Game;
import max.chess.engine.game.GameCache;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.movegen.utils.CheckUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

@Disabled
// https://www.chessprogramming.org/Perft_Results
public class PerftTest {
//    private final static String INITIAL_MOVES = "b5c4";
    private final static String INITIAL_MOVES = null;
//        private final static String FEN_USED = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private final static String FEN_USED = null;

    private final static boolean DEBUG_MODE = false;
    private final static boolean PRINT_MOVES = true;
    private final static boolean PRINT_MOVES_WITH_RECURSION = false;
    private final static boolean COMPARE_TO_STOCKFISH = true;

    private static int PERFT_DEPTH = 7;

    static final int MAX_PLY = 128;
    static final int MAX_MOVES = 256;
    static final int[][] MOVE_BUF = new int[MAX_PLY][MAX_MOVES];
    static final int[]   COUNT    = new int[MAX_PLY];

    @Test
    public void runPerftTestOnStandardBoard() {
        int originalDepth = PERFT_DEPTH;
        warmUp();
        PERFT_DEPTH = originalDepth;
        Instant startOfPerft = Instant.now();
        long perftResult = runPerftTest(PERFT_DEPTH);
        Duration perftDuration = Duration.between(startOfPerft, Instant.now());
        GameCache.printZobristCacheReport();
        CheckUtils.printChecksReport();
        MoveGenerator.printGeneratorReport();
        System.out.println("*************************");
        System.out.println("*************************");
        System.out.println("*************************");
        System.out.println("PERFT TEST AT DEPTH "+PERFT_DEPTH+" COMPLETED IN "+perftDuration+" !");
        System.out.println("FINAL RESULT: "+perftResult);
    }

    private static long runPerftTest(int depth) {
        Game game;
        if(FEN_USED == null) {
            game = BoardGenerator.newStandardGameBoard();
        } else {
            game = BoardGenerator.from(FEN_USED);
        }
        if(INITIAL_MOVES != null) {
            depth -= game.playMoves(INITIAL_MOVES).size();
            depth = Math.max(depth, 1);
            PERFT_DEPTH = depth;
            System.out.println("Played "+INITIAL_MOVES+" upfront and reduced depth to "+depth);
        }
        return runPerft(game, depth, 0, true);
    }

    private static long runPerft(Game game, int depth, int ply, boolean root) {
        Instant startOfPerftIteration = null;

        if(root && depth == PERFT_DEPTH) {
            startOfPerftIteration = Instant.ofEpochMilli(System.currentTimeMillis());
        }

        long nodeResult = 0;

        if(COMPARE_TO_STOCKFISH && depth == 1 && PERFT_DEPTH != 1) {
            return game.getLegalMovesCount();
        }

        if(depth == 0) {
            return 1;
        }

        for(int currentDepth = DEBUG_MODE ? 1 : 1 ; currentDepth <= depth ; currentDepth++) {
            nodeResult = 0;
            int[] moves = MOVE_BUF[ply];
            int n = COUNT[ply] = game.getLegalMoves(moves);  // fills buf[0..n)
//            perftResult = new PerftResult();
//            perftResult = perftResult.add(fromMoveSet(moves));
            for (int i = 0 ; i < n; i++) {
//                Move move = Move.fromBytes(moves[i]);
                long gameChanges = game.playMove(moves[i]);
                long nodeResultAtDepth = runPerft(game, currentDepth - 1, ply + 1, false);
                game.undoMove(gameChanges);
                nodeResult += nodeResultAtDepth;
                if((DEBUG_MODE || PRINT_MOVES) && currentDepth == depth) {
                    if(root || PRINT_MOVES_WITH_RECURSION) {
                        System.out.println(Move.fromBytes(moves[i]) + ": " + nodeResultAtDepth);
                    }
                }
//                perftResult = perftResult.add(movePerft);
            }

            if(root && currentDepth == PERFT_DEPTH) {
                Duration perftDuration = Duration.between(startOfPerftIteration, Instant.now());
                System.out.println("Depth " + currentDepth + " in "+ perftDuration +" : " + nodeResult);
                System.out.println((nodeResult / perftDuration.toMillis() * 1000)  + " nps");
//                long expectedResult = PerftTestSet.PERFT_TEST_FEN_MAP.get(FEN_USED).get(currentDepth);
//                if(expectedResult != nodeResult) {
//                    System.out.println("*************************************************");
//                    System.out.println("ERROR DURING PERFT TEST - SESSION WILL STOP THERE");
//                    System.out.println("Expected value: "+expectedResult+" but got "+nodeResult+" at depth "+currentDepth);
//                    System.out.println("*************************************************");
//                }
//
//                assert expectedResult == nodeResult;
            }
        }

        return nodeResult;

    }

    private static void warmUp() {
        Instant start = Instant.now();
        int allocatedNumberOfSeconds = 2;
        Game.warmUp();
        if(PERFT_DEPTH > 1) {
            // Warming up with a depth - 1
            runPerftTest(PERFT_DEPTH - 1);
        }
        Duration warmUpDuration = Duration.between(start, Instant.now());
        if(warmUpDuration.getSeconds() >= allocatedNumberOfSeconds) {
            System.out.println("WARM UP TOOK MORE THAN ALLOCATED TIME : "+warmUpDuration);
        }

        System.out.println("Warm up done !");

        MoveGenerator.clearGeneratorReport();
        CheckUtils.clearChecksReport();
        GameCache.clearZobristCache();
    }
}
