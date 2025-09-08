package max.chess.engine.book.polyglot;

import max.chess.engine.book.BookPolicy;
import max.chess.engine.book.polyglot.PolyglotBook;
import max.chess.engine.game.Game;
import max.chess.engine.game.board.utils.BoardGenerator;
import max.chess.engine.utils.PieceUtils;
import max.chess.engine.utils.notations.FENUtils;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class PolyglotBookTest {

    @Test
    void picksBestMoveFromMiniBook() throws Exception {
        String fen = "8/P7/8/8/4K3/1R6/5R2/k7 w - - 0 1";
        Path bin = makeMiniBook(fen); // a7a8Q weight = 120, e2e4 weight=100, d2d4 weight=50
        try (var book = new PolyglotBook(bin)) {
            var game = FENUtils.getBoardFrom(fen);
            var mv = book.pickMove(game, BookPolicy.defaults() /* randomness=15% but best wins */, 123L);
            assertTrue(mv.isPresent(), "book should return a move");
            // With randomness>0 it could pick the lighter move; force deterministic:
            var deterministic = new BookPolicy(20, 2, 0, true);
            var mv2 = book.pickMove(game, deterministic, 123L);
            assertTrue(mv2.isPresent());
            String uci = max.chess.engine.utils.notations.MoveIOUtils.writeAlgebraicNotation(mv2.get());
            assertEquals("a7a8Q", uci, "deterministic should pick highest weight");
        }
    }

    private static Path makeMiniBook(String fen) throws IOException {
        var game = FENUtils.getBoardFrom(fen);
        long key = max.chess.engine.game.ZobristHashKeys.getHashKey(game);

        // Three entries for same key: e2e4 (from=12,to=28), d2d4 (from=11,to=27) + a promotion to test it
        short e2e4 = poly(12, 28, 0);
        short d2d4 = poly(11, 27, 0);
        short a7a8Q = poly(48, 56, toPromoNibble(PieceUtils.QUEEN));

        ByteBuffer bb = ByteBuffer.allocate(3 * 16).order(ByteOrder.BIG_ENDIAN);
        // sorted by key already
        bb.putLong(key).putShort(a7a8Q).putShort((short)120).putInt(0);
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

    private static int toPromoNibble(byte promo) {
        return switch (promo) {
            case PieceUtils.KNIGHT -> 1;
            case PieceUtils.BISHOP -> 2;
            case PieceUtils.ROOK -> 3;
            case PieceUtils.QUEEN -> 4;
            default -> PieceUtils.NONE;
        };
    }
}
