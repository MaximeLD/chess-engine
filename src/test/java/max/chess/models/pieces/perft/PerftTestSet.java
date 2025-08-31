package max.chess.models.pieces.perft;

import max.chess.engine.game.board.utils.BoardGenerator;

import java.util.Map;
import java.util.TreeMap;

// Based on https://www.chessprogramming.org/Perft_Results
public class PerftTestSet {
    public static final Map<String, Map<Integer, Long>> PERFT_TEST_FEN_MAP = new TreeMap<>();
    private static final String FEN_POSITION_2 = "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1";
    private static final String FEN_POSITION_3 = "8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1";
    private static final String FEN_POSITION_4 = "r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1";
    private static final String FEN_POSITION_4_MIRROR = "r2q1rk1/pP1p2pp/Q4n2/bbp1p3/Np6/1B3NBn/pPPP1PPP/R3K2R b KQ - 0 1";
    private static final String FEN_POSITION_5 = "rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8";
    private static final String FEN_POSITION_6 = "r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10";

    public static final Map<String, String> FEN_TEST_NAMES = Map.of(
            BoardGenerator.STANDARD_GAME, "Standard board",
            FEN_POSITION_2, "Position 2",
            FEN_POSITION_3, "Position 3",
            FEN_POSITION_4, "Position 4",
            FEN_POSITION_4_MIRROR, "Position 4 - Mirror",
            FEN_POSITION_5, "Position 5",
            FEN_POSITION_6, "Position 6"
    );

    static {
        loadStandardFenTestSet();
        loadPosition2();
        loadPosition3();
        loadPosition4();
        loadPosition5();
        loadPosition6();
    }

    private static void loadPosition6() {
        String FEN = FEN_POSITION_6;
        Map<Integer, Long> expectedResults = new TreeMap<>(
                Map.of(
                        0, 1L,
                        1, 46L,
                        2, 2_079L,
                        3, 89_890L,
                        4, 3_894_594L,
                        5, 164_075_551L,
                        6, 6_923_051_137L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
    }

    private static void loadPosition5() {
        String FEN = FEN_POSITION_5;
        Map<Integer, Long> expectedResults =
                new TreeMap<>(Map.of(
                        1, 44L,
                        2, 1486L,
                        3, 62_379L,
                        4, 2_103_487L,
                        5, 89_941_194L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
    }

    private static void loadPosition4() {
        String FEN = FEN_POSITION_4;
        String MIRRORED_FEN = FEN_POSITION_4_MIRROR;
        Map<Integer, Long> expectedResults =
                new TreeMap<>(Map.of(
                        1, 6L,
                        2, 264L,
                        3, 9_467L,
                        4, 422_333L,
                        5, 15_833_292L,
                        6, 706_045_033L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
        PERFT_TEST_FEN_MAP.put(MIRRORED_FEN, expectedResults);
    }

    private static void loadPosition3() {
        String FEN = FEN_POSITION_3;
        Map<Integer, Long> expectedResults =
                new TreeMap<>(Map.of(
                        1, 14L,
                        2, 191L,
                        3, 2_812L,
                        4, 43_238L,
                        5, 674_624L,
                        6, 11_030_083L,
                        7, 178_633_661L,
                        8, 3_009_794_393L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
    }

    private static void loadPosition2() {
        String FEN = FEN_POSITION_2;
        Map<Integer, Long> expectedResults =
                new TreeMap<>(Map.of(
                        1, 48L,
                        2, 2_039L,
                        3, 97_862L,
                        4, 4_085_603L,
                        5, 193_690_690L,
                        6, 8_031_647_685L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
    }

    private static void loadStandardFenTestSet() {
        String FEN = BoardGenerator.STANDARD_GAME;
        Map<Integer, Long> expectedResults =
                new TreeMap<>(Map.of(
                        0, 1L,
                        1, 20L,
                        2, 400L,
                        3, 8_902L,
                        4, 197_281L,
                        5, 4_865_609L,
                        6, 119_060_324L,
                        7, 3_195_901_860L,
                        8, 84_998_978_956L,
                        9, 2_439_530_234_167L
                ));
        PERFT_TEST_FEN_MAP.put(FEN, expectedResults);
    }
}
