package max.chess.models.pieces.search.evaluator;

import max.chess.engine.game.Game;
import max.chess.engine.search.evaluator.GamePhase;
import max.chess.engine.search.evaluator.PawnEval;
import max.chess.engine.search.evaluator.PawnHash;
import max.chess.engine.search.evaluator.PositionEvaluator;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Directional unit tests for pawn evaluation (and pawn-hash consistency).
 * They compare positions that differ by one clear pawn-structure motif.
 *
 * NOTE:
 *  - Kings are fixed on g1/g8 to minimize side effects.
 *  - All FENs use "w" (White to move) so evaluatePosition is from White's POV.
 *  - If you expose helpers like PositionEvaluator.clearPawnHash()/newSearch(), call them in @BeforeEach.
 */
public class PawnEvalTest {

    private int eval(String fen) {
        Game g = FENUtils.getBoardFrom(fen);
        return eval(g, GamePhase.toPhase256(GamePhase.currentGameProgress(g)));
    }

    private int eval(String fen, int phase256) {
        Game g = FENUtils.getBoardFrom(fen);
        return eval(g, phase256);
    }

    private int eval(Game g, int phase256) {
        return PawnEval.evalPawnStructureWithHash(g.board(), phase256);
    }

    @BeforeEach
    public void setup() {
         PawnEval.PAWN_HASH.clear();
         PositionEvaluator.warmUp();
    }

    @Test
    public void passedPawnIsGood() {
        // White passed pawn on e5 (no black pawns at/near d/e/f files ahead)
        String passed = "6k1/8/8/4P3/8/8/8/6K1 w - - 0 1";
        // Make it NOT passed by adding a black pawn on e6
        String notPassed = "6k1/8/4p3/4P3/8/8/8/6K1 w - - 0 1";

        int sPassed = eval(passed);
        int sNotPassed = eval(notPassed);
        assertTrue(sPassed > sNotPassed, "A passed pawn should score better than a non-passed counterpart.");
    }

    @Test
    public void protectedPassedIsBetterThanLonePassed() {
        // White passed pawn on e5; add f4 pawn to protect it (f4->e5)
        String protectedPassed = "6k1/8/8/4P3/5P2/8/8/6K1 w - - 0 1";
        String lonePassed      = "6k1/8/8/4P3/8/8/8/6K1 w - - 0 1";

        int sProt = eval(protectedPassed);
        int sLone = eval(lonePassed);
        assertTrue(sProt > sLone, "Protected passed pawn should be better than lone passed pawn.");
    }

    @Test
    public void blockedPassedByPieceIsWorse() {
        // Same passed pawn on e5, but blocked by a NON-PAWN piece (black knight on e6)
        String unblocked = "6k1/8/8/4P3/8/8/8/6K1 w - - 0 1";
        String blocked   = "6k1/8/4n3/4P3/8/8/8/6K1 w - - 0 1";

        int sUnblocked = eval(unblocked);
        int sBlocked   = eval(blocked);
        assertTrue(sUnblocked > sBlocked, "Passed pawn blocked by piece in front should score worse.");
    }

    @Test
    public void connectedPawnsBeatIsolatedPawn() {
        // Connected pawns b2+c2 vs a single isolated pawn c2
        String phalanx = "6k1/8/8/8/8/8/1PP5/6K1 w - - 0 1"; // b2,c2
        String connected = "6k1/8/8/8/8/2P5/1P6/6K1 w - - 0 1";
        String isolated  = "6k1/8/8/8/8/8/2P5/6K1 w - - 0 1";   // c2

        int sConn = eval(connected);
        int sPhalanx = eval(phalanx);
        int sIso  = eval(isolated);
        assertTrue(sConn > sIso, "Connected pawns should outscore a comparable isolated pawn.");
        assertTrue(sPhalanx > sIso, "Phalanx pawns should outscore a comparable isolated pawn.");
    }

    @Test
    public void doubledPawnsAreWorseThanSplit() {
        // Doubled on c2/c3 vs split c2+d2
        String doubled = "6k1/8/8/8/8/2P5/2P5/6K1 w - - 0 1";   // c3 + c2
        String split   = "6k1/8/8/8/8/8/2PP4/6K1 w - - 0 1";     // c2 + d2

        int sDbl = eval(doubled);
        int sSpl = eval(split);
        assertTrue(sSpl > sDbl, "Split pawns should score better than doubled pawns on same material.");
    }

    @Test
    public void fewerPawnIslandsIsBetter() {
        // Three islands: a2, c2, f2
        String threeIslands = "6k1/8/8/8/8/8/P1P2P2/6K1 w - - 0 1";
        // One island (chain): a2,b2,c2
        String oneIsland    = "6k1/8/8/8/8/8/PPP5/6K1 w - - 0 1";

        int s1 = eval(oneIsland);
        int s3 = eval(threeIslands);
        assertTrue(s1 > s3, "Fewer pawn islands should be better.");
    }

    @Test
    public void backwardPawnPenaltyAndSupportRelief() {
        // White pawn on c3, black pawn on d5 (attacks c4). No support => backward.
        String backward  = "6k1/8/8/3p4/8/2P5/8/6K1 w - - 0 1";
        // Add a supporting white pawn on b2 so c3 is supported => not backward
        String supported = "6k1/8/8/3p4/8/2P5/1P6/6K1 w - - 0 1";

        int sBwd = eval(backward);
        int sSup = eval(supported);
        assertTrue(sSup > sBwd, "Supported pawn should remove/reduce backward penalty.");
    }

    @Test
    public void endgameEmphasizesPassedPawnsMoreThanMiddlegame() {
        // Same passed pawn; shift phase with heavy pieces (queens)
        String endgameLike = "6k1/8/8/4P3/8/8/8/6K1 w - - 0 1";              // just kings+pawn
        String middlegame  = "6k1/8/8/4P3/8/8/8/5QK1 w - - 0 1";             // add white queen (shifts phase earlier)
        // (If your phase function is symmetric, adding both queens also works.)
        // String middlegame  = "6k1/8/8/4P3/8/8/8/5QKq w - - 0 1";

        int sEG = eval(endgameLike);
        int sMG = eval(middlegame);
        assertTrue(sEG > sMG, "Passed pawns should matter more in endgame than in middlegame.");
    }

    @Test
    public void samePawnsDifferentNonBlockingPiecesKeepPawnScoreStable() {
        // Identical pawns; add a far rook that shouldn’t affect pawn-only terms nor blocked-passed
        String base = "6k1/8/8/4P3/8/8/8/6K1 w - - 0 1";
        String withRookFar = "6k1/8/8/4P3/8/8/8/R5K1 w - - 0 1";

        int sBase = eval(base, 0);
        int sRook = eval(withRookFar, 0);
        // Full eval may differ by tiny mobility/king terms; if your eval includes only pawns+king here,
        // this should be equal. If not, loosen to <= and document.
        assertEquals(sBase, sRook, "Non-blocking, non-pawn piece far away should not change pawn score.");
    }

    @Test
    public void pawnHashConsistencySamePawnBitboards() {
        // Two positions with the SAME pawn bitboards but different (non-blocking) piece placements
        // The pawn-structure *component* should be identical; the full eval may vary slightly.
        // If you expose a test hook to get pawn-only score, use that here instead of evaluatePosition.
        String A = "6k1/8/8/3P4/8/8/8/6K1 w - - 0 1";     // white pawn d5
        String B = "6k1/8/8/3P4/8/8/8/5QK1 w - - 0 1";    // add a white queen far from push square

        int sA = eval(A);
        int sB = eval(B);

        // If you have a public hook like PositionEvaluator.pawnOnlyScore(game), assert equality on that.
        // Here we allow small drift if other terms exist; tighten when you expose the pawn-only accessor.
        assertTrue(Math.abs(sA - sB) <= 5, "Same pawn bitboards => pawn-only term must be identical (hash-consistent).");
    }
}
