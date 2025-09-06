package max.chess.models.pieces.search.evaluator;

import static org.junit.jupiter.api.Assertions.*;

import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.search.evaluator.GamePhase;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import max.chess.engine.game.Game;
import max.chess.engine.utils.notations.FENUtils;

public class GamePhaseTest {

    private int phase(Game g) {
        // Replace with your function:
         return GamePhase.toPhase256(GamePhase.currentGameProgress(g));
    }

    @Test
    public void startIsOpeningish() {
        Game start = FENUtils.getBoardFrom(BoardGenerator.STANDARD_GAME);
        int p = phase(start);
        assertTrue(p >= 0 && p <= 32, "Start should be near opening (close to 0)");
    }

    @Test
    public void kOnlyIsEndgame() {
        Game kk = FENUtils.getBoardFrom("8/8/8/8/8/8/8/Kk6 w - - 0 1");
        int p = phase(kk);
        assertTrue(p >= 248 && p <= 256, "K vs K must be ~endgame (very close to 256)");
    }

    @Test
    public void queensOffIncreasesPhase() {
        Game start = FENUtils.getBoardFrom(BoardGenerator.STANDARD_GAME);
        Game noQueens = FENUtils.getBoardFrom("rnb1kbnr/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/RNB1KBNR w KQkq - 0 1"); // both queens traded from start-like material
        assertTrue(phase(noQueens) > phase(start), "Removing queens should push phase toward endgame");
    }

    @Test
    @Disabled
    public void heavyOffBeatsLightOff() {
        Game minusMinors = FENUtils.getBoardFrom("r3k2r/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/R3K2R w KQkq - 0 1"); // only kings+rooks+pawns
        Game minusMajors = FENUtils.getBoardFrom("1nb1kbn1/pppp1ppp/8/4p3/4P3/8/PPPP1PPP/1NB1KBN1 w - - 0 1"); // only minors+pawns
        // Removing rooks tends to increase phase more than removing minors
        assertTrue(phase(minusMajors) > phase(minusMinors), "Losing majors should push phase more than losing minors (typical weighting)");
    }

    @Test
    public void sideToMoveDoesNotAffectPhase() {
        Game a = FENUtils.getBoardFrom(BoardGenerator.STANDARD_GAME);
        Game b = FENUtils.getBoardFrom(BoardGenerator.STANDARD_GAME); // but with 'b' to move:
        b.nextTurn();
        assertEquals(phase(a), phase(b), "Phase must be independent of side to move");
    }
}
