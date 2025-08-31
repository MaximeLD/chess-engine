package max.chess.engine.movegen.pieces;

/**
 * FancyRook: rook attacks with "fancy" magic bitboards.
 *
 * Squares: 0..63 with a1 = 0, h1 = 7, a8 = 56.
 * occ:     ALL pieces (both colors).
 *
 * Usage:
 *   FancyRook.init();                      // once at startup
 *   long atk = FancyRook.attacks(sq, occ); // hot path: one mul+shift + one table load
 *
 * If you already have precomputed rook magics, set USE_PRESET_MAGICS = true
 * and fill ROOK_MAGIC_PRESET[64] to skip the search.
 */
public final class Rook {

    /* ---------- config ---------- */

    private static final boolean USE_PRESET_MAGICS = false;
    private static final long[] ROOK_MAGIC_PRESET = new long[64]; // paste your constants if you have them

    /* ---------- per-square data ---------- */

    private static final long[] MASK   = new long[64]; // relevant blockers mask (edges excluded)
    private static final long[] MAGIC  = new long[64]; // magic multipliers
    private static final int[]  SHIFT  = new int[64];  // 64 - popcount(mask)
    private static final int[]  OFFSET = new int[64];  // base into TABLE
    private static       long[] TABLE;                 // flat attacks table

    private Rook() {}

    static {
        init();
        selfTest();
    }

    public static void warmUp () {
        // to trigger the init();
    }

    /* ===================== public API ===================== */

    public static long getLegalMovesBB(int positionIndex, long friendlyPiecesBB, long occupiedBB) {
        long legalMovesBB = attacks(positionIndex, occupiedBB);
        legalMovesBB &= ~friendlyPiecesBB;

        return legalMovesBB;
    }

    public static long getAttackBB(int positionIndex, long occupiedBB) {
        return attacks(positionIndex, occupiedBB);
    }

    /** Build masks, magics, and the flat table. Call once at startup. */
    private static void init() {
        // 1) masks, shifts, offsets, total table size
        int total = 0;
        for (int sq = 0; sq < 64; sq++) {
            long m = rookMaskNoEdge(sq);
            MASK[sq] = m;
            int bits = Long.bitCount(m);
            SHIFT[sq] = 64 - bits;
            OFFSET[sq] = total;
            total += 1 << bits;
        }
        TABLE = new long[total];

        // 2) magics: either preset or search
        if (USE_PRESET_MAGICS) {
            System.arraycopy(ROOK_MAGIC_PRESET, 0, MAGIC, 0, 64);
            fillTablesWithGivenMagics();
        } else {
            buildMagicsAndFill();
        }
    }

    /** Fast rook attacks from 'sq' under occupancy 'occ' (all pieces). */
    private static long attacks(int sq, long occ) {
        long occRel = occ & MASK[sq];
        int idx = OFFSET[sq] + (int) ((occRel * MAGIC[sq]) >>> SHIFT[sq]);
        return TABLE[idx];
    }

    /* ===================== builder ===================== */

    private static void fillTablesWithGivenMagics() {
        for (int sq = 0; sq < 64; sq++) {
            long mask = MASK[sq];
            int bits  = 64 - SHIFT[sq];
            int size  = 1 << bits;

            int base = OFFSET[sq];
            // enumerate subsets of mask
            long subset = 0L;
            while (true) {
                int index = (int) ((subset * MAGIC[sq]) >>> SHIFT[sq]);
                TABLE[base + index] = rookSlidingFrom(sq, subset);
                if (subset == mask) break;
                subset = (subset - mask) & mask;
            }
        }
    }

    private static void buildMagicsAndFill() {
        SplitMix64 rng = new SplitMix64(0xC0FFEE_5EED_F00DL); // deterministic seed

        for (int sq = 0; sq < 64; sq++) {
            long mask = MASK[sq];
            int bits  = 64 - SHIFT[sq];
            int size  = 1 << bits;

            long[] occs    = new long[size];
            long[] attacks = new long[size];

            // precompute all subset occupancies and their attacks
            int n = 0;
            long subset = 0L;
            while (true) {
                occs[n]    = subset;
                attacks[n] = rookSlidingFrom(sq, subset);
                n++;
                if (subset == mask) break;
                subset = (subset - mask) & mask;
            }

            // search for a collision-free magic
            long magic;
            while (true) {
                magic = randomMagicCandidate(rng);

                int tableSize = 1 << bits;
                int[] used = new int[tableSize]; // 0 empty; store (i+1)
                boolean ok = true;

                for (int i = 0; i < size; i++) {
                    int index = (int) ((occs[i] * magic) >>> SHIFT[sq]);
                    int u = used[index];
                    if (u == 0) {
                        used[index] = i + 1;
                    } else if (attacks[u - 1] != attacks[i]) {
                        ok = false;
                        break;
                    }
                }
                if (ok) break;
            }

            MAGIC[sq] = magic;

            // fill the final table for this square
            int base = OFFSET[sq];
            for (int i = 0; i < size; i++) {
                int index = (int) ((occs[i] * magic) >>> SHIFT[sq]);
                TABLE[base + index] = attacks[i];
            }
        }
    }

    /* ===================== helpers ===================== */

    // Rook relevant-occupancy mask excluding edge squares on each ray.
    private static long rookMaskNoEdge(int sq) {
        long mask = 0L;
        int r = sq >>> 3, f = sq & 7;

        // rank (left/right), skip file 0 and 7
        for (int ff = f + 1; ff <= 6; ff++) mask |= 1L << (r * 8 + ff);
        for (int ff = f - 1; ff >= 1; ff--) mask |= 1L << (r * 8 + ff);

        // file (up/down), skip rank 0 and 7
        for (int rr = r + 1; rr <= 6; rr++) mask |= 1L << (rr * 8 + f);
        for (int rr = r - 1; rr >= 1; rr--) mask |= 1L << (rr * 8 + f);

        return mask;
    }

    // Rays from sq given *subset* occupancy on relevant squares (mask-coherent).
    private static long rookSlidingFrom(int sq, long occSubset) {
        long attacks = 0L;
        int r = sq >>> 3, f = sq & 7;

        // Right
        for (int ff = f + 1; ff <= 7; ff++) {
            int s = (r << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // Left
        for (int ff = f - 1; ff >= 0; ff--) {
            int s = (r << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // Up
        for (int rr = r + 1; rr <= 7; rr++) {
            int s = (rr << 3) | f; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // Down
        for (int rr = r - 1; rr >= 0; rr--) {
            int s = (rr << 3) | f; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }

        return attacks;
    }

    /* ===================== tiny RNG for magic search ===================== */

    private static final class SplitMix64 {
        private long x;
        SplitMix64(long seed) { this.x = seed; }
        long next() {
            long z = (x += 0x9E3779B97F4A7C15L);
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            return z ^ (z >>> 31);
        }
    }

    private static long randomMagicCandidate(SplitMix64 rng) {
        // Sparse-ish candidate; classic triple-AND trick
        return rng.next() & rng.next() & rng.next();
    }

    /* ===================== optional self-test ===================== */

    private static void selfTest() {
        for (int sq = 0; sq < 64; sq++) {
            long mask = MASK[sq];
            int bits = 64 - SHIFT[sq];
            int size = 1 << bits;
            long subset = 0L;
            while (true) {
                int idx = OFFSET[sq] + (int) (((subset & mask) * MAGIC[sq]) >>> SHIFT[sq]);
                long pre = TABLE[idx];
                long ref = rookSlidingFrom(sq, subset);
                if (pre != ref) {
                    throw new AssertionError("FancyRook mismatch at sq=" + sq +
                            " subset=" + Long.toUnsignedString(subset));
                }
                if (subset == mask) break;
                subset = (subset - mask) & mask;
            }
        }
    }
}
