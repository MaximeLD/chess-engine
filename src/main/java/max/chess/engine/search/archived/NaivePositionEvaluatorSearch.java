package max.chess.engine.search.archived;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.search.evaluator.PositionEvaluator;

import java.util.Random;

public class NaivePositionEvaluatorSearch {
    static {
        MoveGenerator.warmUp();
    }

    public static int pickNextMove(Game game) {
        return pickNextMoveWithBestPositionValue(game);
    }

    private static int pickNextMoveWithBestPositionValue(Game game) {
        int[] moveBuffer = new int[218];
        int[] candidateMoveBuffer = new int[218];
        int[] evaluationBuffer = new int[218];

        int numberOfMoves = MoveGenerator.generateMoves(game, moveBuffer);

        int minEvaluation = Integer.MAX_VALUE;
        for(int i = 0; i < numberOfMoves; i++) {
            long gameChanges = game.playMove(moveBuffer[i]);
            evaluationBuffer[i] = PositionEvaluator.evaluatePosition(game);
            if(evaluationBuffer[i] < minEvaluation) {
                minEvaluation = evaluationBuffer[i];
            }
            game.undoMove(gameChanges);
        }

        int moveSelected;
        int numberOfBestMoves = 0;
        for(int i = 0; i < numberOfMoves; i++) {
            if(evaluationBuffer[i] == minEvaluation) {
                candidateMoveBuffer[numberOfBestMoves++] = moveBuffer[i];
            }
        }
        // Picking at random among the best moves (i.e. the ones minimizing enemy score)
        int movePickedIndex = Random.from(new Random()).nextInt(0, numberOfBestMoves);
        return candidateMoveBuffer[movePickedIndex];
    }
}
