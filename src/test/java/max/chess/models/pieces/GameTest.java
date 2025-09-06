package max.chess.models.pieces;

import max.chess.engine.game.Game;
import max.chess.engine.game.GameChanges;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.movegen.Move;
import max.chess.engine.search.SearchConfig;
import max.chess.engine.search.SearchFacade;
import max.chess.engine.uci.UciServer;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.utils.notations.FENUtils;
import max.chess.engine.utils.notations.MoveIOUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GameTest {

    @Test
    @Disabled
    public void gameTest() {
        // Given
        Game game = FENUtils.getBoardFrom("8/6pk/7p/8/3R4/1P1P1P1P/5KP1/8 b - - 0 47");
        SearchConfig.Builder builder = new SearchConfig.Builder();
        SearchFacade searchFacade = new SearchFacade(builder.build());
        UciServer.GoParams goParams = new UciServer.GoParams();
        goParams.depth = 20;
        searchFacade.findBestMove(game, new AtomicBoolean(false), goParams, (string) -> System.out.println("Search log: "+string));
    }

    @Test
    @Disabled
    public void gameTest2() {
        // Given
        int[] moves = {0, 28142, 18155, 27557, 5469, 7143, 25429, 6623, 5209, 26988, 5022, 7598, 5989, 27835, 5022, 27494, 5989};
        Game game = FENUtils.getBoardFrom("8/6pk/7p/8/3R4/1P1P1P1P/5KP1/8 b - - 0 47");
        for(int i = 0; i < moves.length; i++){
            if(moves[i] == 0){
                game.playNullMove();
            } else {
                game.playMove(moves[i]);
            }
        }

        int[] moveGen = game.getLegalMoves(false);
        SearchConfig.Builder builder = new SearchConfig.Builder();
    }

    @Test
    public void gameShouldDetect_drawBy3FoldRepetition() {
        // Given
        // We start with standard board
        Game game = BoardGenerator.newStandardGameBoard();

        // Knight dance !
        // Nc3
        Move whiteMove = new Move(1, 18);
        // Nb1
        Move whiteUndoMove = new Move(18, 1);
        // Nc6
        Move blackMove = new Move(57, 42);
        // Nb8
        Move blackUndoMove = new Move(42, 57);

        // When
        // We will play repetitively the same moves with white and black until we reach a 3-fold

        // First time the initial position is reached for white
        game.playSimpleMove(whiteMove);
        game.playSimpleMove(blackMove);
        game.playSimpleMove(whiteUndoMove);
        game.playSimpleMove(blackUndoMove);
        // Second time the initial position is reached for white
        game.playSimpleMove(whiteMove);
        game.playSimpleMove(blackMove);
        game.playSimpleMove(whiteUndoMove);
        game.playSimpleMove(blackUndoMove);
        // Third time ! should now be a draw (i.e. no more move is legal)
        assert game.getLegalMovesCount() == 0;
    }

    @Test
    public void gameShouldDetect_drawBy50MovesRule() {
        // Given
        // We start with a board on which we have 49 moves on the half move clock
        // The black rook is on b3, the white rook is on a3 ; all the squares on 3rd rank and above are free
        String testFen = "8/8/8/8/8/Rr6/N6n/KN4nk b - - 49 102"; // black to move

        Game game = BoardGenerator.from(testFen);

        Move move50 = new Move(17, 18);

        assert game.getLegalMovesCount() > 0; // Verify we are not in a draw yet

        // When
        game.playSimpleMove(move50);

        // Then
        assert game.getLegalMovesCount() == 0;
    }
}
