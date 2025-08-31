package max.chess.models.pieces.perft.v2;

import max.chess.engine.movegen.Move;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Queue;
import java.util.stream.Collectors;

public class PerftException extends Exception {
    private final Queue<Move> failingMoveSequence;

    public PerftException(Throwable cause) {
        super("", cause, true, false);
        failingMoveSequence = Collections.asLifoQueue(new ArrayDeque<>());
    }
    public PerftException(String message) {
        super(message, null, true, true);
        failingMoveSequence = Collections.asLifoQueue(new ArrayDeque<>());
    }

    public Queue<Move> failingMoveSequence() {
        return failingMoveSequence;
    }

    public String getFailingMoveSequence() {
        return failingMoveSequence.stream().map(Move::toString).collect(Collectors.joining(" "));
    }
}
