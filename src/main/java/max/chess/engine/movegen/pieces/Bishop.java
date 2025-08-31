package max.chess.engine.movegen.pieces;

/**
 * FancyBishop: bishop attacks with "fancy" magic bitboards.
 * <p>
 * Squares: 0..63 with a1 = 0, h1 = 7, a8 = 56
 * Occ:     ALL pieces (both colors).
 * <p>
 * Usage:
 *   FancyBishop.init();                       // once at startup
 *   long atk = FancyBishop.attacks(sq, occ);  // fast: one multiply + shift + table read
 * <p>
 * Notes:
 *   - This builds *collision-free* magics at startup (deterministic, no I/O).
 *   - Table layout is flat + per-square offsets for cache locality.
 *   - If you already have known-good bishop magics, paste them into BISHOP_MAGIC_PRESET
 *     and set USE_PRESET_MAGICS = true to skip the search.
 */
public final class Bishop {

    /* ---------- config ---------- */

    // Set to true and fill BISHOP_MAGIC_PRESET to bypass the search.
    private static final boolean USE_PRESET_MAGICS = false;
    private static final long[] BISHOP_MAGIC_PRESET = { //
            0x0808431002060060L, 0x0010420214086902L, 0x0004211401000114L, 0x02060a1200005290L, //
            0x8012021000400000L, 0x0801015840800080L, 0x0004120804044500L, 0x0501040184240604L, //
            0x0a00682310020601L, 0x4001041002004901L, 0x6804500102002464L, 0x4800022082014400L, //
            0x00000a0210800008L, 0x0280c088044041b0L, 0x84000044100c1080L, 0x24101888c8021002L, //
            0x2008080451540810L, 0x0004080801080208L, 0xc0021a4400620200L, 0x0098000082004400L, //
            0x800e10c401040400L, 0x0420400809101021L, 0x0052000101901401L, 0x2001820032011000L, //
            0x0002109662604a03L, 0x0738580002100104L, 0x04030400018c0400L, 0x0001802108020020L, //
            0x0089001021014000L, 0x4000820001010080L, 0x880401406c064200L, 0x100202000c84c14dL, //
            0x0021211080200400L, 0x0108041010020200L, 0x000c004800011200L, 0x0280401048060200L, //
            0x0102008400220020L, 0x4022080200004050L, 0x0122008401050402L, 0x0014040150812100L, //
            0x0582020240212000L, 0x0218a80402031000L, 0x0101420041001020L, 0x0020902024200800L, //
            0x0800401093021202L, 0x00a2100202020020L, 0x1120640421408080L, 0x00888084008054c8L, //
            0x0004120804044500L, 0x1240908090102005L, 0x8000226884100400L, 0x0880008042020000L, //
            0x2000040410442040L, 0x041010200169002aL, 0x0808431002060060L, 0x0010420214086902L, //
            0x0501040184240604L, 0x24101888c8021002L, 0x6003250104431002L, 0x8012a0040c208801L, //
            0x0108041010020200L, 0x0400000420440100L, 0x0a00682310020601L, 0x0808431002060060L, };
    /* ---------- per-square data ---------- */

    private static final long[] MASK  = new long[64];   // relevant occupancy mask (no edges)
    private static final long[] MAGIC = new long[64];   // magic multipliers (found or preset)
    private static final int[]  SHIFT = new int[64];    // 64 - popcount(mask)
    private static final int[]  OFFSET= new int[64];    // base into TABLE
    private static       long[] TABLE;                  // flat attacks table

    private Bishop() {}

    static {
        init();
        selfTest();
    }

    public static void warmUp () {
        // to trigger the init();
    }

    public static long getPseudoLegalMovesBB(int positionIndex, long friendlyPiecesBB, long occupiedBB) {
        long legalMovesBB = attacks(positionIndex, occupiedBB);
        legalMovesBB &= ~friendlyPiecesBB;

        return legalMovesBB;
    }

    public static long getAttackBB(int positionIndex, long occupiedBB) {
        return attacks(positionIndex, occupiedBB);
    }

    /* ===================== public API ===================== */

    /** Build masks, magics, and the flat table. Call once at startup. */
    public static void init() {
        // 1) compute masks and sizes, assign contiguous offsets
        int total = 0;
        for (int sq = 0; sq < 64; sq++) {
            long m = bishopMaskNoEdge(sq);
            MASK[sq] = m;
            int bits = Long.bitCount(m);
            SHIFT[sq] = 64 - bits;
            OFFSET[sq] = total;
            total += 1 << bits;
        }
        TABLE = new long[total];

        // 2) build magics (or use preset)
        if (USE_PRESET_MAGICS) {
            System.arraycopy(BISHOP_MAGIC_PRESET, 0, MAGIC, 0, 64);
        } else {
            buildMagicsAndFill();
        }

        // 3) tiny sanity: table slots should all be written
        // (optionally keep a visited bitmap in build if you want to assert hard)
    }

    /** Fast bishop attacks from 'sq' under occupancy 'occ' (all pieces). */
    private static long attacks(int sq, long occ) {
        long occRel = occ & MASK[sq];
        int idx = OFFSET[sq] + (int) ((occRel * MAGIC[sq]) >>> SHIFT[sq]);
        return TABLE[idx];
    }

    /* ===================== builder ===================== */

    private static void buildMagicsAndFill() {
        // Deterministic RNG per square
        SplitMix64 rng = new SplitMix64(0x9E3779B97F4A7C15L);

        // Reusable buffers for each square
        for (int sq = 0; sq < 64; sq++) {
            long mask = MASK[sq];
            int bits  = 64 - SHIFT[sq];
            int size  = 1 << bits;

            // Build all subset occupancies and their resulting attack sets once
            long[] occs    = new long[size];
            long[] attacks = new long[size];

            // Enumerate subsets of mask
            int n = 0;
            long subset = 0L;
            while (true) {
                occs[n]    = subset;
                attacks[n] = bishopSlidingFrom(sq, subset);
                n++;
                if (subset == mask) break;
                subset = (subset - mask) & mask; // next subset
            }

            // Find a collision-free magic
            long magic;
            while (true) {
                magic = randomMagicCandidate(rng);

                // Optional heuristic filter (keeps candidates with some top-bit entropy):
                // if (Long.bitCount((mask * magic) & 0xFF00_0000_0000_0000L) < 6) continue;

                // test
                int tableSize = 1 << bits;
                int[] used = new int[tableSize]; // 0 means empty; store (i+1)
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
                // keep trying; bishops are easy, this converges quickly
            }

            MAGIC[sq] = magic;

            // Fill the final flat table
            int base = OFFSET[sq];
            for (int i = 0; i < size; i++) {
                int index = (int) ((occs[i] * magic) >>> SHIFT[sq]);
                TABLE[base + index] = attacks[i];
            }
        }
    }

    /* ===================== core helpers ===================== */

    // Bishop mask excluding board edges; these are the "relevant blocker" squares.
    private static long bishopMaskNoEdge(int sq) {
        long mask = 0L;
        int r = sq >>> 3, f = sq & 7;

        // NE
        for (int rr = r + 1, ff = f + 1; rr <= 6 && ff <= 6; rr++, ff++) mask |= 1L << (rr * 8 + ff);
        // NW
        for (int rr = r + 1, ff = f - 1; rr <= 6 && ff >= 1; rr++, ff--) mask |= 1L << (rr * 8 + ff);
        // SE
        for (int rr = r - 1, ff = f + 1; rr >= 1 && ff <= 6; rr--, ff++) mask |= 1L << (rr * 8 + ff);
        // SW
        for (int rr = r - 1, ff = f - 1; rr >= 1 && ff >= 1; rr--, ff--) mask |= 1L << (rr * 8 + ff);

        return mask;
    }

    // On-the-fly bishop rays from sq given *subset* occupancy on relevant squares (not full board).
    private static long bishopSlidingFrom(int sq, long occSubset) {
        long attacks = 0L;
        int r = sq >>> 3, f = sq & 7;

        // NE
        for (int rr = r + 1, ff = f + 1; rr < 8 && ff < 8; rr++, ff++) {
            int s = (rr << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // NW
        for (int rr = r + 1, ff = f - 1; rr < 8 && ff >= 0; rr++, ff--) {
            int s = (rr << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // SE
        for (int rr = r - 1, ff = f + 1; rr >= 0 && ff < 8; rr--, ff++) {
            int s = (rr << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }
        // SW
        for (int rr = r - 1, ff = f - 1; rr >= 0 && ff >= 0; rr--, ff--) {
            int s = (rr << 3) | ff; long b = 1L << s; attacks |= b; if ((occSubset & b) != 0) break;
        }

        return attacks;
    }

    /* ===================== tiny RNG for magic search ===================== */

    // SplitMix64: fast deterministic 64-bit generator
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
        // Classic trick: AND three randoms to make a sparse-ish candidate
        return rng.next() & rng.next() & rng.next();
    }

    /* ===================== optional self-test ===================== */

    private static void selfTest() {
        for (int sq = 0; sq < 64; sq++) {
            long mask = MASK[sq];
            // enumerate all subsets to verify table correctness
            long subset = 0;
            do {
                int idx = OFFSET[sq] + (int) (((subset & mask) * MAGIC[sq]) >>> SHIFT[sq]);
                long pre = TABLE[idx];
                long ref = bishopSlidingFrom(sq, subset);
                if (pre != ref) {
                    throw new AssertionError("FancyBishop mismatch at sq=" + sq +
                        " subset=" + Long.toUnsignedString(subset));
                }
                subset = (subset - mask) & mask;
            } while (subset != 0);
        }
    }
}
