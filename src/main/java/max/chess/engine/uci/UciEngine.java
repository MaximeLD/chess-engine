package max.chess.engine.uci;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/** Your bridge to the actual engine. */
    public interface UciEngine {
        /** Called on "ucinewgame". Reset your state. */
        void newGame();

        /** Called on "isready". Do any lazy init; return when ready. */
        default void onIsReady() {}

        /** Called on "setoption name X value Y". */
        default void setOption(String name, String value) {}

        /** "position startpos [moves ...]" */
        void setPositionStartpos(List<String> uciMoves);

        /** "position fen <fen> [moves ...]" */
        void setPositionFEN(String fen, List<String> uciMoves);

        /**
         * Run a search per the GoParams. Use stopFlag / ponderHit to manage abort/wakeup.
         * Use infoSink.accept("info ...") to emit UCI info lines (PV, score, nodes, nps, depth, etc).
         * Return bestmove (and optional ponder).
         */
        UciResult search(UciServer.GoParams go, AtomicBoolean stopFlag, java.util.function.Consumer<String> infoSink);

        /** Optional hint when GUI sent "stop" (you can set your own flag). */
        default void onStopHint() {}

        /** Debug hook for "print" command if you want it. */
        default void debugDump(java.util.function.Consumer<String> out) {}
    }