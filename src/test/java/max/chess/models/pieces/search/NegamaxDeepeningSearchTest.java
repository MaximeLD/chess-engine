package max.chess.models.pieces.search;

import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.search.archived.NegamaxDeepeningSearch;
import max.chess.engine.search.SearchResult;
import max.chess.engine.uci.UciServer;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class NegamaxDeepeningSearchTest {

    @Test
    public void simpleSearch() {
        Game game = BoardGenerator.newStandardGameBoard();
        NegamaxDeepeningSearch.init();

        UciServer.GoParams go = new UciServer.GoParams();
        go.movetime = Duration.ofSeconds(1).toMillis();
        SearchResult searchResult = NegamaxDeepeningSearch.pickNextMove(game, new AtomicBoolean(false), go);
        System.out.println(searchResult.toUCIInfo());
        System.out.println(searchResult);
    }
}
