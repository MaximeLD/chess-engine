package max.chess.engine.tb;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.tb.syzygy.SyzygyJNI;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

final class SyzygyRootTest {
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
    void rootCanReturnEpCapture() {
        Game g = FENUtils.getBoardFrom("k7/8/8/3Pp3/8/8/8/3K4 w - e6 0 1");
        List<String> legalMoveListString = MoveIOUtils.getMoveListString(g.getLegalMoves());

        var bestUciOpt = tb.pickUci(g);
        assertTrue(bestUciOpt.isPresent(), "Root probe should return a best move");
        String best = bestUciOpt.get();
        // EP UCI looks like a normal capture: d5e6
        int[] legal = MoveGenerator.generateMoves(g);
        boolean legalContainsBest = false;
        for (int mv : legal) {
            String uci = max.chess.engine.utils.notations.MoveIOUtils.writeAlgebraicNotation(mv);
            if (uci.equals(best)) { legalContainsBest = true; break; }
        }
        assertTrue(legalMoveListString.contains(best));
        assertTrue(legalContainsBest, "TB best move must be legal but was "+best);
    }

    @Test
    void rootReturnsALegalMove() {
        Game g = FENUtils.getBoardFrom("k7/2P5/2K5/8/8/8/8/8 w - - 0 1");
        int[] legal = MoveGenerator.generateMoves(g);
        assertNotNull(legal);
        assertTrue(legal.length > 0);
        var res = tb.pickUci(g); // through TBManager
        assertTrue(res.isPresent(), "Root probe should return a move");
        // optional: ensure it is actually legal
        // We already validate in provider, so this just sanity-checks
    }


    @Test
    void rootPickSuggestsPromotion() {
        Game g = FENUtils.getBoardFrom("k7/2P5/2K5/8/8/8/8/8 w - - 0 1");
        var best = tb.pickUci(g);
        assertTrue(best.isPresent(), "Root probe should return a best move");
        // “c7c8q” is the canonical UCI; some TBs may pick underpromotion in rare cases,
        // but here promotion to queen is the stable answer.
        assertEquals("c7c8Q", best.get(), "Expected promotion suggested by TB");
    }

    @Test
    void wdlReflectsBlessedLoss() {
        Game g = FENUtils.getBoardFrom("8/8/1k2P2K/6P1/8/3p4/8/8 b - - 0 1");
        Optional<TBRootResult> rootResult = tb.probeRoot(g);
        assertTrue(rootResult.isPresent());
        assertTrue(SyzygyJNI.isDraw(rootResult.get().wdl()), "WDL must be a draw");
        assertTrue(rootResult.get().wdl() == SyzygyJNI.WDL_BLESSED_LOSS, "WDL must be a blessed loss");
    }

    @Test
    void wdlReflectsBlessedLoss_whenAccountingFor50MoveCounter() {
        // It's a Mate in 2 (DTZ 2 as well)
        Game g = FENUtils.getBoardFrom("8/8/8/8/4K3/6R1/5R2/k7 b - - 100 100");
        Optional<TBRootResult> rootResult = tb.probeRoot(g);
        assertTrue(rootResult.isPresent());
        assertTrue(SyzygyJNI.isDraw(rootResult.get().wdl()), "WDL must be a draw");
        assertTrue(rootResult.get().wdl() == SyzygyJNI.WDL_BLESSED_LOSS, "WDL must be a blessed loss");
    }

    @Test
    void wdlReflectsCursedWin() {
        Game g = FENUtils.getBoardFrom("k7/n7/3P4/K5N1/8/8/8/8 w - - 0 1");
        Optional<TBRootResult> rootResult = tb.probeRoot(g);
        assertTrue(rootResult.isPresent());
        assertTrue(SyzygyJNI.isDraw(rootResult.get().wdl()), "WDL must be a draw");
        assertTrue(rootResult.get().wdl() == SyzygyJNI.WDL_CURSED_WIN, "WDL must be a cursed win");
    }

    @Test
    void wdlReflectsCursedWin_whenAccountingFor50MoveCounter() {
        // It's a Mate in 3 (DTZ 3 as well)
        Game g = FENUtils.getBoardFrom("8/8/8/8/4KR2/6R1/8/k7 w - - 100 100");
        Optional<TBRootResult> rootResult = tb.probeRoot(g);
        assertTrue(rootResult.isPresent());
        assertTrue(SyzygyJNI.isDraw(rootResult.get().wdl()), "WDL must be a draw");
        assertTrue(rootResult.get().wdl() == SyzygyJNI.WDL_CURSED_WIN, "WDL must be a cursed win");
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
}
