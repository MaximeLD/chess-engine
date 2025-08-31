package max.chess.engine.search;

import max.chess.engine.game.Game;
import max.chess.engine.movegen.MoveGenerator;

import java.util.Random;

public class RandomSearch {
    static {
        MoveGenerator.warmUp();
    }

    public static int pickNextMove(Game game) {
        return pickNextMoveRandomly(game);
    }

    private static int pickNextMoveRandomly(Game game) {
        int[] moveBuffer = new int[218];

        int numberOfMoves = MoveGenerator.generateMoves(game, moveBuffer);

        int movePickedIndex = Random.from(new Random()).nextInt(0, numberOfMoves);
        return moveBuffer[movePickedIndex];
    }
}
