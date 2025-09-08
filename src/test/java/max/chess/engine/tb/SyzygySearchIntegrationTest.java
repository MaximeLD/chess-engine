package max.chess.engine.tb;

import max.chess.engine.search.SearchConfig;
import max.chess.engine.search.SearchFacade;
import max.chess.engine.uci.UciEngineImpl;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

final class SyzygySearchIntegrationTest {

    @Test
    void searchUsesTbAtRoot() {
        var uci = new UciEngineImpl();
        // mimic GUI options
        uci.setOption("UseSyzygy", "true");
        uci.setOption("SyzygyMaxPieces", "5");
        uci.setOption("SyzygyUseDTZ", "true");
        uci.setOption("SyzygyProbeInSearch", "true");
        uci.setOption("SyzygyPath", "syzygy/3-4-5/Syzygy345");

        var fen = "k7/2P5/2K5/8/8/8/8/8 w - - 0 1";
        uci.setPositionFEN(fen, Collections.emptyList());
        UciServer.GoParams goParams = new UciServer.GoParams();
        goParams.movetime = 500; // should take way less to probe the table base
        List<String> uciMessages = new ArrayList<>();
        var result = uci.search(goParams, new AtomicBoolean(false), uciMessages::add);

        assertTrue(!"0000".equals(result.bestmove), "Engine should return a UCI move from TB");
        assertTrue(uciMessages.contains("info string tb move c7c8Q"), "Root probe should bypass normal eval");
    }
}
