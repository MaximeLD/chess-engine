package max.chess.models.pieces.search.evaluator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import max.chess.engine.common.Position;
import max.chess.engine.game.Game;
import max.chess.engine.search.evaluator.GamePhase;
import max.chess.engine.search.evaluator.PositionEvaluator;
import max.chess.engine.utils.ColorUtils;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.Test;

public class KingScoreEvaluatorTest {
        private int eval(Game g, int color, double gameProgress) {
            return PositionEvaluator.getKingScore(g, color, GamePhase.toPhase256(gameProgress));
        }

        @Test
        public void openVsClosedKingFile() {
            // White: K on g1, full shelter g2 f2 h2
            Game closed = FENUtils.getBoardFrom("6k1/8/8/8/8/8/6PP/6K1 w - - 0 1"); // add f2 pawn if you track it
            // Open king file: remove g2 pawn
            Game open   = FENUtils.getBoardFrom("6k1/8/8/8/8/8/7P/6K1 w - - 0 1");

            int closedScore = eval(closed, ColorUtils.WHITE, 0);
            int openScore   = eval(open,   ColorUtils.WHITE, 0);
            assertTrue(openScore < closedScore, "Open king file must be worse");
        }

        @Test
        public void halfOpenVsOpenKingFile() {
            // Half-open g-file: no white pawn on g-file, black pawn on g7
            Game halfOpen = FENUtils.getBoardFrom("6k1/6p1/8/8/8/8/7P/6K1 w - - 0 1");
            // Fully open g-file: remove black pawn
            Game open     = FENUtils.getBoardFrom("6k1/8/8/8/8/8/7P/6K1 w - - 0 1");

            int half = eval(halfOpen, ColorUtils.WHITE, 0);
            int openS = eval(open, ColorUtils.WHITE, 0);
            assertTrue(openS < half, "Open worse than half-open");
        }

        @Test
        public void shelterBonusOnBestRank() {
            // King g1; pawn on g2 (best) vs only g3
            Game g2 = FENUtils.getBoardFrom("6k1/8/8/8/8/8/6P1/6K1 w - - 0 1"); // g2 + g3
            Game g3 = FENUtils.getBoardFrom("6k1/8/8/8/8/6P1/8/6K1 w - - 0 1");   // only g3

            int s2 = eval(g2, ColorUtils.WHITE, 0);
            int s3 = eval(g3, ColorUtils.WHITE, 0);
            assertTrue(s2 > s3, "Shelter on home rank should be better");
        }

        @Test
        public void pawnStormPenalty() {
            // King g1; enemy pawn advanced on g5 vs g6 absent
            Game calm   = FENUtils.getBoardFrom("6k1/8/8/8/8/8/6P1/6K1 w - - 0 1");
            Game stormR4Black  = FENUtils.getBoardFrom("6k1/8/8/6p1/8/8/8/6K1 w - - 0 1");
            Game stormR5Black  = FENUtils.getBoardFrom("6k1/8/8/8/6p1/8/8/6K1 w - - 0 1");
            Position whiteKingPosition = Position.of(6);
            Position blackKingPosition = Position.of(62);
            int file = 6;

            int sCalm = PositionEvaluator.scorePawnStorm(whiteKingPosition, file, true, calm.board().blackBB);
            int sStormR4Black = PositionEvaluator.scorePawnStorm(whiteKingPosition, file, true, stormR4Black.board().blackBB);
            int sStormR5Black = PositionEvaluator.scorePawnStorm(whiteKingPosition, file, true, stormR5Black.board().blackBB);
            assertTrue(sStormR4Black < sCalm, "Pawn storm rank 4 near king should be worse than calm");
            assertTrue(sStormR5Black < sStormR4Black, "Pawn storm rank 5 near king should be worse than calm");
        }

        @Test
        public void ringPressureCountsEnemy() {
            // King g1; enemy queen on h5 giving ring pressure
            Game qh5 = FENUtils.getBoardFrom("6k1/8/8/7q/8/8/6P1/6K1 w - - 0 1");
            Game none = FENUtils.getBoardFrom("6k1/8/8/8/8/8/6P1/6K1 w - - 0 1");

            int withQ = eval(qh5, ColorUtils.WHITE, 0);
            int noQ   = eval(none, ColorUtils.WHITE, 0);
            assertTrue(withQ < noQ, "Queen near king should be worse");
        }

        @Test
        public void endgameCentralization() {
            // White king: e4 vs a1, queens off
            Game center = FENUtils.getBoardFrom("8/8/8/8/4K3/8/8/8 w - - 0 1");
            Game corner = FENUtils.getBoardFrom("8/8/8/8/8/8/8/K7 w - - 0 1");

            // force full endgame blending for test
            double gp = 1.0;
            int cen = PositionEvaluator.getKingEndGameScore(Position.of(28));
            int cor = PositionEvaluator.getKingEndGameScore(Position.of(0));
            assertTrue(cen > cor, "Center should be better in endgame");
        }
}
