package max.chess.engine.uci;

import max.chess.engine.book.BookPolicy;
import max.chess.engine.book.polyglot.PolyglotBook;
import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class UciEngineBookIntegrationTest {

    @Test
    void uciReturnsBookMoveWhenAvailable() throws Exception {
        Path bin = makeMiniBook(); // startpos → e2e4 (100), d2d4 (50)

        UciEngineImpl uci = new UciEngineImpl();
        uci.setOption("OwnBook", "true");
        uci.setOption("BookFile", bin.toString());
        uci.setOption("BookRandomness", "0"); // deterministic best

        uci.setPositionStartpos(List.of());
        var go = new UciServer.GoParams();
        go.movetime = 1;

        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicReference<String> info = new AtomicReference<>();
        var res = uci.search(go, stop, s -> info.set(s));

        assertNotNull(res);
        assertTrue(res.bestmove.equals("e2e4") || res.bestmove.equals("d2d4"));
        assertTrue(info.get() == null || info.get().contains("book move"));
    }

    private static Path makeMiniBook() throws IOException {
        var game = FENUtils.getBoardFrom(BoardGenerator.STANDARD_GAME);
        long key = max.chess.engine.game.ZobristHashKeys.getHashKey(game);

        short e2e4 = poly(12, 28, 0);
        short d2d4 = poly(11, 27, 0);

        ByteBuffer bb = ByteBuffer.allocate(2 * 16).order(ByteOrder.BIG_ENDIAN);
        bb.putLong(key).putShort(e2e4).putShort((short)100).putInt(0);
        bb.putLong(key).putShort(d2d4).putShort((short)50).putInt(0);
        bb.flip();

        Path tmp = Files.createTempFile("mini-book", ".bin");
        Files.write(tmp, bb.array());
        tmp.toFile().deleteOnExit();
        return tmp;
    }

    private static short poly(int from, int to, int promoNibble) {
        int v = ((promoNibble & 0xF) << 12) | ((from & 0x3F) << 6) | (to & 0x3F);
        return (short)(v & 0xFFFF);
    }
}
