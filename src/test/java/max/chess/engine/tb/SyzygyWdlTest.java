package max.chess.engine.tb;

import max.chess.engine.game.Game;
import max.chess.engine.tb.syzygy.SyzygyJNI;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class SyzygyWdlTest {
    static TBManager tb;

    @BeforeAll
    static void setUp() {
        tb = new TBManager();
        tb.loadProvider();
        tb.setEnabled(true);
        tb.setMaxPieces(5);
        tb.setUseDTZ(true);
        tb.setPath("syzygy/3-4-5/Syzygy345");
    }


    @Test
    void wdlReflectsBlessedLoss() {
        Game g = FENUtils.getBoardFrom("8/8/1k2P2K/6P1/8/3p4/8/8 b - - 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent());
        assertTrue(SyzygyJNI.isDraw(wdl.getAsInt()), "WDL must be a draw");
        assertTrue(wdl.getAsInt() == SyzygyJNI.WDL_BLESSED_LOSS, "WDL must be a blessed loss");
    }

    @Test
    void wdlReflectsCursedWin() {
        Game g = FENUtils.getBoardFrom("k7/n7/3P4/K5N1/8/8/8/8 w - - 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent());
        assertTrue(SyzygyJNI.isDraw(wdl.getAsInt()), "WDL must be a draw");
        assertTrue(wdl.getAsInt() == SyzygyJNI.WDL_CURSED_WIN, "WDL must be a cursed win");
    }

    @Test
    void kpkWinningIsWin() {
        // FEN: white to move wins trivially: WK c6, WP c7, BK a8 (promotion unstoppable)
        Game g = FENUtils.getBoardFrom("k7/2P5/2K5/8/8/8/8/8 w - - 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent(), "WDL must be present for 3-man");
        assertTrue(SyzygyJNI.isWin(wdl.getAsInt()), "Expected winning WDL");
    }

    @Test
    void kpkLosingIsLoss() {
        // FEN: white to move wins trivially: WK c6, WP c7, BK a8 (promotion unstoppable)
        Game g = FENUtils.getBoardFrom("k7/2P5/2K5/8/8/8/8/8 b - - 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent(), "WDL must be present for 3-man");
        assertTrue(SyzygyJNI.isLoss(wdl.getAsInt()), "Expected losing WDL");
    }

    @Test
    void trivialDrawIsDraw() {
        // K vs KR is loss; K vs K is 2-man (not in 3-5 set), so use KB vs KB with wrong color bishops -> draw
        Game g = FENUtils.getBoardFrom("8/8/8/8/8/k6b/8/K6B w - - 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent(), "WDL must be present");
        assertTrue(SyzygyJNI.isDraw(wdl.getAsInt()), "Opposite-color bishops 4-man is draw");
    }

    @Test
    void wdlPresentWhenEpAvailable() {
        // White: Kd1, Pd5; Black: Ke8, Pe5; last move e7-e5 -> EP at e6
        Game g = FENUtils.getBoardFrom("4k3/8/8/3Pp3/8/8/8/3K4 w - e6 0 1");
        var wdl = tb.probeWDL(g);
        assertTrue(wdl.isPresent(), "WDL must be present for EP position (4-man)");
    }

}
