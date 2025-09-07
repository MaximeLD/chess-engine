package max.chess.engine.book.polyglot;

import max.chess.engine.book.BookPolicy;
import max.chess.engine.book.OpeningBook;
import max.chess.engine.game.Game;
import max.chess.engine.game.ZobristHashKeys;
import max.chess.engine.movegen.Move;
import max.chess.engine.movegen.MoveGenerator;
import max.chess.engine.utils.PieceUtils;

import java.io.IOException;
import java.util.*;

public final class PolyglotBook implements OpeningBook {
    private static final int ENTRY_BYTES = 16;

    private final java.nio.file.Path file; // may be null for classpath stream
    private final java.nio.channels.FileChannel ch; // may be null
    private final java.nio.ByteBuffer buf;
    private final int entryCount;

    /** Filesystem-backed, memory-mapped. */
    public PolyglotBook(java.nio.file.Path file) throws java.io.IOException {
        this.file = file;
        this.ch = java.nio.channels.FileChannel.open(file);
        long size = ch.size();
        if (size % ENTRY_BYTES != 0) throw new java.io.IOException("Invalid polyglot size: " + size);
        this.entryCount = (int) (size / ENTRY_BYTES);
        this.buf = ch.map(java.nio.channels.FileChannel.MapMode.READ_ONLY, 0, size).order(java.nio.ByteOrder.BIG_ENDIAN);
    }

    /** Classpath-backed: read bytes into a direct buffer. */
    public PolyglotBook(java.io.InputStream in) throws java.io.IOException {
        this.file = null;
        this.ch = null;
        byte[] bytes = in.readAllBytes();
        if (bytes.length % ENTRY_BYTES != 0) throw new java.io.IOException("Invalid polyglot size: " + bytes.length);
        this.entryCount = bytes.length / ENTRY_BYTES;
        var bb = java.nio.ByteBuffer.allocateDirect(bytes.length).order(java.nio.ByteOrder.BIG_ENDIAN);
        bb.put(bytes).flip();
        this.buf = bb;
    }

    @Override public boolean isLoaded() { return entryCount > 0; }

    @Override public void close() {
        try { if (ch != null) ch.close(); } catch (java.io.IOException ignored) {}
    }

    @Override
    public Optional<Integer> pickMove(Game game, BookPolicy policy, long rngSeed) {
        long key = polyglotKey(game);
        int lo = lowerBound(key), hi = upperBound(key);
        if (lo >= hi) return Optional.empty();

        // Gather candidates, map to legal engine moves with weights
        int[] legal = MoveGenerator.generateMoves(game);
        List<Entry> choices = new ArrayList<>(hi - lo);
        for (int i = lo; i < hi; i++) {
            long k = getKey(i);
            if (k != key) break;
            int pMove = getMove(i);
            int weight = getWeight(i);

            if (weight < policy.minWeight()) continue;
            int mv = toEngineMove(pMove, legal);
            if (mv != 0) choices.add(new Entry(mv, weight));
        }
        if (choices.isEmpty()) return Optional.empty();

        if (policy.randomnessPct() <= 0) { // deterministic
            choices.sort((a, b) -> {
                if (policy.preferMainline()) return Integer.compare(b.w, a.w);
                return Integer.compare(a.mv, b.mv);
            });
            return Optional.of(choices.get(0).mv);
        } else {
            // tempered weight sampling: w^alpha
            double alpha = 1.0 / Math.max(1e-9, (policy.randomnessPct() / 100.0) * 3.0);
            double sum = 0;
            for (Entry e : choices) { e.score = Math.pow(e.w, alpha); sum += e.score; }
            double r = new java.util.Random(rngSeed ^ key).nextDouble() * sum;
            for (Entry e : choices) { r -= e.score; if (r <= 0) return Optional.of(e.mv); }
            return Optional.of(choices.get(choices.size() - 1).mv);
        }
    }

    /* ---------------- implementation details ---------------- */

    private static final class Entry {
        private final int mv;
        private final int w;

        double score;

        private Entry(int mv, int w) {
            this.mv = mv;
            this.w = w;
        }

        public int mv() {
            return mv;
        }

        public int w() {
            return w;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null || obj.getClass() != this.getClass()) return false;
            var that = (Entry) obj;
            return this.mv == that.mv &&
                this.w == that.w;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mv, w);
        }

        @Override
        public String toString() {
            return "Entry[" +
                "mv=" + mv + ", " +
                "w=" + w + ']';
        }
    }

    private long polyglotKey(Game g) {
        // Your ZobristHashKeys is already the canonical Polyglot table (781 keys),
        // with the same offsets. We reuse it directly.
        return ZobristHashKeys.getHashKey(g);
    }

    private long getKey(int idx) {
        int off = idx * ENTRY_BYTES;
        return buf.getLong(off);
    }
    private int getMove(int idx) {
        int off = idx * ENTRY_BYTES + 8;
        return buf.getShort(off) & 0xFFFF;
    }
    private int getWeight(int idx) {
        int off = idx * ENTRY_BYTES + 10;
        return buf.getShort(off) & 0xFFFF;
    }
    private int lowerBound(long key) {
        int lo = 0, hi = entryCount;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            long k = getKey(mid);
            if (k < key) lo = mid + 1; else hi = mid;
        }
        return lo;
    }
    private int upperBound(long key) {
        int lo = 0, hi = entryCount;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            long k = getKey(mid);
            if (k <= key) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    /** Convert 16-bit polyglot move to our engine move by matching against legals. */
    private static int toEngineMove(int poly, int[] legalMoves) {
        int from = (poly >>> 6) & 0x3F;
        int to   =  poly        & 0x3F;
        int promoNibble = (poly >>> 12) & 0xF;
        byte promo = switch (promoNibble) {
            case 1 -> PieceUtils.KNIGHT;
            case 2 -> PieceUtils.BISHOP;
            case 3 -> PieceUtils.ROOK;
            case 4 -> PieceUtils.QUEEN;
            default -> PieceUtils.NONE;
        };

        // Match against legal list
        for (int mv : legalMoves) {
            if (Move.getStartPosition(mv) != from) continue;
            if (Move.getEndPosition(mv) != to) continue;
            // Promotion piece is stored in the promotion nibble, not the piece-type nibble
            byte mvPromo = Move.getPromotion(mv);
            if (mvPromo != promo) continue;
            return mv;
        }
        return 0;
    }
}
