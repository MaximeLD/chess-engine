package max.chess.models.pieces.perft.v2;

import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.game.Game;
import max.chess.engine.game.GameCache;
import max.chess.engine.game.ZobristHashKeys;
import max.chess.engine.movegen.Move;
import max.chess.models.pieces.perft.PerftTestSet;
import max.chess.models.pieces.zobrist.v2.ZobristHashKeysTest;
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

import static max.chess.models.pieces.perft.v2.PerftFullTest.resetZobristCache;

// https://www.chessprogramming.org/Perft_Results
public class PerftFullTestWithZobristKey {
    private final static long MAX_TARGET_NODE_COUNT = 100_000_000L;
    private static final boolean DEBUG_COLLISIONS = true;
    private static final long DEBUGGED_HASH_KEY = -5685318346073965908L;

    public static void main(String[] args) {
        final LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(
                        DiscoverySelectors.selectClass(PerftFullTestWithZobristKey.class)
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
            try {
                runPerftTest(fen, expectedResult.getKey(), expectedResult.getValue());
            } catch (Throwable t) {
                throw t;
            } finally {
                GameCache.printZobristCacheReport();
                resetZobristCache();
            }
        }
    }

    private static long runPerft(Game game, int depth) throws PerftException {
        long nodeResult = 0;

        if(depth == 0) {
            return 1;
        }

        for(int currentDepth = 1 ; currentDepth <= depth ; currentDepth++) {
            nodeResult = 0;
            long zobristKeyBeforeMove = game.zobristKey();
//            Collection<MoveRepresentation> cachedMoves = null;
            int[] moves;
//            if(DEBUGGED_HASH_KEY == zobristKeyBeforeMove) {
//                System.out.println("Debugged hash key matched for FEN:\n"+ FENUtils.getFENFromBoard(game));
//            }
//            if(DEBUG_COLLISIONS && BoardRepresentation.LEGAL_MOVES_CACHE.containsKey(zobristKeyBeforeMove)) {
//                cachedMoves = BoardRepresentation.LEGAL_MOVES_CACHE.get(zobristKeyBeforeMove);
//                BoardRepresentation.TRANSPOSITION_TABLES_ENABLED = false;
//            }
            moves = game.getLegalMoves();
//            if(DEBUG_COLLISIONS && cachedMoves != null) {
//                if(!moves.containsAll(cachedMoves) || !cachedMoves.containsAll(moves)) {
//                    throw new max.chess.models.pieces.perft.v1.PerftException("ZOBRIST CACHE COLLISION FOR KEY "+zobristKeyBeforeMove+"L");
//                }
//            }
            for (int moveBytes : moves) {
                Move move = Move.fromBytes(moveBytes);
                try {
                    long gameChanges = game.playMove(moveBytes);
                    try {
                        ZobristHashKeysTest.testHash(game, game.zobristKey());
                    } catch(AssertionError e) {
                        System.out.println("Previous valid hash key hex: "+ ZobristHashKeys.print(zobristKeyBeforeMove));
                        System.out.println("Previous valid hash key decimal: "+zobristKeyBeforeMove+"L");
                        throw e;
                    }
                    long nodeResultAtDepth = runPerft(game, currentDepth - 1);
                    game.undoMove(gameChanges);
                    if(zobristKeyBeforeMove != game.zobristKey()) {
                        System.out.println("ZOBRIST KEY ISSUE: DID NOT REVERT BACK TO SAME ZOBRIST KEY AFTER DOING AND UNDOING A MOVE !");
                        assert zobristKeyBeforeMove == game.zobristKey();
                    }
                    nodeResult += nodeResultAtDepth;
                } catch (PerftException e) {
                    e.failingMoveSequence().add(move);
                    throw e;
                } catch (AssertionError | RuntimeException e) {
                    PerftException exception = new PerftException(e);
                    exception.failingMoveSequence().add(move);
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
        ZobristHashKeysTest.testHash(game, game.zobristKey());
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
            System.out.println("Error : "+ (e.getCause() != null ? e.getCause().getMessage() : e.getMessage()));
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
