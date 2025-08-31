package max.chess.models.pieces.perft.v2;

import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.game.Game;
import max.chess.engine.game.GameCache;
import max.chess.engine.movegen.Move;
import max.chess.models.pieces.perft.PerftTestSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

// https://www.chessprogramming.org/Perft_Results
public class PerftFullTest {
    private final static long MAX_TARGET_NODE_COUNT = 100_000_000L;
    private final static boolean COMPARE_TO_STOCKFISH = true;
    public static void main(String[] args) {
        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        DiscoverySelectors.selectClass(PerftFullTest.class)
                )
                .build();

        final Launcher launcher = LauncherFactory.create();

        final boolean pathContainsTests = launcher.discover(request).containsTests();
        if (!pathContainsTests) {
            System.out.println("This path is invalid or folder doesn't consist tests");
        }

        final SummaryGeneratingListener listener = new SummaryGeneratingListener();

        launcher.execute(request, listener);
    }

    public static Stream<Arguments> getPerftTestSet() {
        List<Arguments> argumentsList = new ArrayList<>();
        PerftTestSet.PERFT_TEST_FEN_MAP.forEach((fen, results) -> argumentsList.add(Arguments.of(PerftTestSet.FEN_TEST_NAMES.get(fen), fen, results)));

        return argumentsList.stream();
    }

    @BeforeAll
    public static void beforeAll() {
        System.out.println("Warming up the system for tests...");
        warmUp();
        System.out.println("System ready !");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("getPerftTestSet")
    public void runPerftTest(String testName, String fen, Map<Integer, Long> expectedResults) throws PerftException {
        for(Map.Entry<Integer, Long> expectedResult : expectedResults.entrySet()) {
                runPerftTest(fen, expectedResult.getKey(), expectedResult.getValue());
                GameCache.printZobristCacheReport();
                resetZobristCache();
        }
    }

    public static void resetZobristCache() {
        GameCache.clearZobristCache();
    }

    private static long runPerft(Game game, int depth) throws PerftException {
        long nodeResult = 0;

        if(COMPARE_TO_STOCKFISH && depth == 1) {
            return game.getLegalMoves().length;
        }

        if(depth == 0) {
            return 1;
        }

        for(int currentDepth = 1 ; currentDepth <= depth ; currentDepth++) {
            nodeResult = 0;
            int[] moves = game.getLegalMoves();
            for (int moveBytes : moves) {
//                Move move = Move.fromBytes(moveBytes);
                try {
                    long gameChanges = game.playMove(moveBytes);
                    long nodeResultAtDepth = runPerft(game, currentDepth - 1);
                    game.undoMove(gameChanges);
                    nodeResult += nodeResultAtDepth;
                } catch (PerftException e) {
                    e.failingMoveSequence().add(Move.fromBytes(moveBytes));
                    throw e;
                } catch (AssertionError | RuntimeException e) {
                    PerftException exception = new PerftException(e);
                    exception.failingMoveSequence().add(Move.fromBytes(moveBytes));
                    throw exception;
                }
            }
        }

        return nodeResult;

    }

    private void runPerftTest(String fen, int depth, long expectedResult) throws PerftException {
        if(expectedResult > MAX_TARGET_NODE_COUNT) {
            System.out.println("SKIPPING (too high target result) depth "+depth+" for fen "+fen);
            return;
        }

        Game game = BoardGenerator.from(fen);
        try {
            System.out.println("\n*************************************************");
            System.out.println("STARTING PERFT TEST AT DEPTH "+depth+" FOR FEN "+fen);
            Instant start = Instant.now();
            long result = runPerft(game, depth);
            Duration perftDuration = Duration.between(start, Instant.now());

            if(result != expectedResult) {
                System.out.println("ERROR DURING PERFT TEST");
                System.out.println("FEN : "+ fen);
                System.out.println("Depth : "+ depth);
                System.out.println("Expected value: "+expectedResult);
                System.out.println("Actual value: "+result);
            } else {
                System.out.println("PERFT TEST AT DEPTH "+depth+" COMPLETED IN "+perftDuration+" FOR FEN "+fen);
                System.out.println(perftDuration.toMillis() == 0 ? "too small duration to estimate nps" : (result / perftDuration.toMillis() * 1000)  + " nps");
            }
            assert expectedResult == result;
        } catch (PerftException e) {
            System.out.println("EXCEPTION DURING PERFT TEST");
            System.out.println("FEN : "+ fen);
            System.out.println("Depth : "+ depth);
            System.out.println("Move sequence : "+ e.getFailingMoveSequence());
            System.out.println("Error : "+ e.getCause().getMessage());
            throw e;
        } finally {
            System.out.println("*************************************************\n");
        }
    }

    private static void warmUp() {
        Instant start = Instant.now();
        int allocatedNumberOfSeconds = 2;
        Game.warmUp();
        Duration warmUpDuration = Duration.between(start, Instant.now());
        if(warmUpDuration.getSeconds() >= allocatedNumberOfSeconds) {
            System.out.println("WARM UP TOOK MORE THAN ALLOCATED TIME : "+warmUpDuration);
        } else {
            try {
//                System.out.println("Waiting until end of allocated time for warmup");
//                Thread.sleep(allocatedNumberOfSeconds*1000-warmUpDuration.toMillis());
                System.out.println("Warm up done !");
            } catch (Exception e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }
}
