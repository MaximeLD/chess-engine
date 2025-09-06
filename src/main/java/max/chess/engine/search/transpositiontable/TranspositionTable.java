package max.chess.engine.search.transpositiontable;

import static max.chess.engine.search.SearchConstants.MAX_PLY;
import static max.chess.engine.search.evaluator.GameValues.CHECKMATE_VALUE;

public final class TranspositionTable {
    private static final int WAYS = 4;

    public static final byte TT_EXACT = 0, TT_LOWER = 1, TT_UPPER = 2;

    private final long[] keys;   // 64-bit zobrist
    private final long[] info;   // [63..32]=move, [31..16]=score16, [15..8]=depth8, [7..2]=gen6, [1..0]=flag2
    private static final int SE_WAYS = 2;

    private final long[] seKeys;   // 64-bit zobrist for SE entries
    private final int[]  seInfo;   // [31..26]=gen6, [15..0]=eval16 (signed)

    private final int seBucketsMask;
    private final int seBuckets;
    private final int seSlots;
    public static final class IntRef { public int value; }

    private final int bucketsMask;
    private final int buckets;
    private final int slots;     // buckets * WAYS
    private int generation;      // 0..63

    // ----- metrics -----
    public static final class Stats {
        public long probes;
        public long hits;
        public long hitsSufficient;
        public long exactHits, lowerHits, upperHits;

        public long stores;
        public long emptyWrites;
        public long replaceSameKey;
        public long replaceOtherKey;

        public long cutoffsFromTT;  // increment from search when TT closes window
        public long ttMoveHints;    // increment when probe returns a nonzero move

        public long filledSlots;    // approx live entries

        // static-eval counters
        public long seProbes, seHits, seStores;

        public void clear() {
            probes = hits = hitsSufficient = exactHits = lowerHits = upperHits = 0;
            stores = emptyWrites = replaceSameKey = replaceOtherKey = 0;
            cutoffsFromTT = ttMoveHints = 0;
            seProbes = seHits = seStores = 0;
            // keep filledSlots as-is (we maintain it incrementally)
        }
        public Stats copy() {
            Stats s = new Stats();
            s.probes = probes; s.hits = hits; s.hitsSufficient = hitsSufficient;
            s.exactHits = exactHits; s.lowerHits = lowerHits; s.upperHits = upperHits;
            s.stores = stores; s.emptyWrites = emptyWrites; s.replaceSameKey = replaceSameKey; s.replaceOtherKey = replaceOtherKey;
            s.cutoffsFromTT = cutoffsFromTT; s.ttMoveHints = ttMoveHints; s.filledSlots = filledSlots;
            s.seProbes = seProbes; s.seHits = seHits;  s.seStores = seStores;
            return s;
        }

        public String toInfoStringForUCI(TranspositionTable tt) {
            return String.format("string TT: probes=%d hits=%d suff=%d (%.1f%%) exact=%d lower=%d upper=%d, cutoffs=%d, " +
                            "stores=%d replSK=%d replOK=%d empty=%d, load=%.1f%%%n " +
                            "seStores=%d, seHits=%d, seProbes=%d (%.1f%%)%n",
                    probes, hits, hitsSufficient, tt.hitRate(), exactHits, lowerHits, upperHits,
                    cutoffsFromTT, stores, replaceSameKey, replaceOtherKey, emptyWrites, tt.loadFactor(),
                    seStores, seHits, seProbes, (seProbes==0?0.0:(100.0*seHits/seProbes)));
        }
    }
    private final Stats stats = new Stats();

    public TranspositionTable(int megaBytes) {
        long bytes = (long) megaBytes << 20;
        long entries = Math.max(WAYS, (bytes / 16L));
        long bucketsWanted = Math.max(1, entries / WAYS);
        int b = 1; while ((long) b < bucketsWanted) b <<= 1;
        this.buckets = b;
        this.bucketsMask = b - 1;
        this.slots = buckets * WAYS;
        this.keys = new long[slots];
        this.info = new long[slots];
        this.generation = 0;

        // SE cache ~¼ the main buckets (tweak if you want bigger/smaller)
        this.seBuckets = Math.max(1, buckets >>> 2);
        this.seBucketsMask = seBuckets - 1;
        this.seSlots = seBuckets * SE_WAYS;
        this.seKeys = new long[seSlots];
        this.seInfo = new int[seSlots];
    }

    public void clear() {
        java.util.Arrays.fill(keys, 0L);
        java.util.Arrays.fill(info, 0L);
        java.util.Arrays.fill(seInfo, 0);
        generation = 0;
        stats.clear();
        stats.filledSlots = 0;
    }

    public void newSearch() { generation = (generation + 1) & 63; }

    /** Read-only snapshot of current stats. */
    public Stats snapshot() { return stats.copy(); }
    /** Reset counters (not occupancy) at the start of an iteration. */
    public void resetCounters() { stats.clear(); }

    /** Handy percentages. */
    public double hitRate() {
        return stats.probes == 0 ? 0 : (100.0 * stats.hitsSufficient / stats.probes);
    }

    public double loadFactor() { return 100.0 * stats.filledSlots / slots; }

    /** Call from search when a TT bound caused an immediate cutoff. */
    public void countCutoff() { stats.cutoffsFromTT++; }

    /** Lightweight probe result (you can reuse one instance). */
    public static final class Hit {
        public boolean found; // key present at any depth
        public int move;
        public int score;
        public int depth;
        public byte flag;
        public boolean hasStaticEval;
        public int staticEval;
        public void reset() {
            found = false; move = score = depth = 0; flag = 0;
            hasStaticEval = false; staticEval = 0;
        }
    }

    /** Probe TT. Return true if depth-sufficient bound exists. Always fills move if present. */
//    public boolean probe(long key, int reqDepth, int ply, Hit out) {
//        stats.probes++;
//
//        final int base = bucket(key);
//        int ttMoveLocal = 0;
//        boolean anyFound = false;
//
//        for (int i = 0; i < WAYS; i++) {
//            int idx = base + i;
//            if (keys[idx] != key) continue;
//
//            anyFound = true;
//            long w = info[idx];
//            int move = (int) (w >>> 32);
//            int depth = (int) ((w >>> 8) & 0xFF);
//            byte flag = (byte) (w & 0x3);
//            int score = fromTT((short) ((w >>> 16) & 0xFFFF), ply);
//
//            if (move != 0) ttMoveLocal = move;    // remember to report best hint
//
//            if (depth >= reqDepth) {
//                stats.hits++;
//                stats.hitsSufficient++;
//                switch (flag) {
//                    case TT_EXACT: stats.exactHits++; break;
//                    case TT_LOWER: stats.lowerHits++; break;
//                    case TT_UPPER: stats.upperHits++; break;
//                }
//                out.found = true; out.move = move; out.depth = depth; out.flag = flag; out.score = score;
//                if (ttMoveLocal != 0) stats.ttMoveHints++;
//                return true;
//            } else {
//                // not deep enough: still a hit, but not sufficient
//                stats.hits++;
//                out.found = false; out.move = move; out.depth = depth; out.flag = flag; out.score = score;
//                if (ttMoveLocal != 0) { out.move = ttMoveLocal; stats.ttMoveHints++; }
//                return false;
//            }
//        }
//
//        // miss
//        out.reset();
//        return false;
//    }

    // --- helpers for static eval packing ---
    private static int packSE(int gen6, int eval16) {
        // clamp eval to short
        if (eval16 >  32767) eval16 =  32767;
        if (eval16 < -32768) eval16 = -32768;
        return (0x80000000) | ((gen6 & 63) << 24) | (eval16 & 0xFFFF);
    }
    private static boolean hasSE(int p) { return (p & 0x80000000) != 0; }
    private static int unpackEval(int p) { return (short)(p & 0xFFFF); }
    private int seBucket(long key) {
        int h = Long.hashCode(key);
        h ^= (h >>> 16);
        h *= 0x9E3779B1;
        h ^= (h >>> 15);
        return (h & seBucketsMask) * SE_WAYS;
    }
    private int seAgeDist(int gen6) { return (generation - gen6) & 63; }
    private static int packSE(short eval16, int gen6) { return ((gen6 & 63) << 26) | (eval16 & 0xFFFF); }
    private static short clampToShort(int v) {
        if (v >  32767) v =  32767;
        if (v < -32768) v = -32768;
        return (short) v;
    }
    /** Probe TT. Return true if a depth-sufficient bound exists.
     Always returns any cached static eval (hasStaticEval/staticEval) if key matched. */
    public boolean probe(long key, int reqDepth, int ply, Hit out) {
        stats.probes++;
        stats.seProbes++; // we attempted to find SE too
        final int base = bucket(key);


        int bestIdx = -1;
        int bestDepth = -1;
        int bestGenDist = Integer.MAX_VALUE; // smaller is better


        for (int i = 0; i < WAYS; i++) {
            int idx = base + i;
            if (keys[idx] != key) continue;
            long w = info[idx];
            int depth = (int) ((w >>> 8) & 0xFF);
            int gen = ((int) w) & 0xFF; // [7..2]=gen6, [1..0]=flag2
            int g6 = (gen >>> 2) & 63;
            int ageDist = (generation - g6) & 63; // 0 is youngest
            if (depth > bestDepth || (depth == bestDepth && ageDist < bestGenDist)) {
                bestDepth = depth; bestGenDist = ageDist; bestIdx = idx;
            }
        }

        if (bestIdx < 0) { out.reset(); return false; }

        long w = info[bestIdx];
        int move = (int) (w >>> 32);
        int depth = (int) ((w >>> 8) & 0xFF);
        byte flag = (byte) (w & 0x3);
        int score = fromTT((short) ((w >>> 16) & 0xFFFF), ply);

        out.move = move; out.depth = depth; out.flag = flag; out.score = score;

        if (depth >= reqDepth) {
            stats.hits++; stats.hitsSufficient++;
            switch (flag) { case TT_EXACT -> stats.exactHits++; case TT_LOWER -> stats.lowerHits++; case TT_UPPER -> stats.upperHits++; }
            out.found = true; return true;
        } else {
            stats.hits++;
            // insufficient depth: not a sufficient hit for bounds
            out.found = false; return false;
        }
    }

    public void store(long key, int move, int depth, int score, byte flag, int ply) {
        stats.stores++;

        short packedScore = (short) toTT(score, ply);
        int meta = ((generation & 63) << 2) | (flag & 3);
        int d8 = (depth & 0xFF);
        long w = ((long) move << 32)
                | ((((long) packedScore) & 0xFFFFL) << 16)
                | (((long) d8) << 8)
                | (meta & 0xFFL);

        final int base = bucket(key);

        int victim = -1;
        int worstScore = Integer.MIN_VALUE;

        for (int i = 0; i < WAYS; i++) {
            int idx = base + i;
            long k = keys[idx];
            if (k == 0L) {
                victim = idx;
                stats.emptyWrites++;
                stats.filledSlots++;         // occupancy increases on first fill
                break;
            }
            if (k == key) {
                int oldDepth = (int) ((info[idx] >>> 8) & 0xFF);
                if (depth >= oldDepth) {
                    victim = idx;
                    stats.replaceSameKey++;
                } else {
                    return; // keep deeper
                }
                break;
            }
            int sc = replacementScore(info[idx]);
            if (sc > worstScore) {
                worstScore = sc;
                victim = idx;
            }
        }
        if (victim < 0) {
            victim = base; // extreme fallback; treat as other-key replacement
            if (keys[victim] != 0L && keys[victim] != key) stats.replaceOtherKey++;
        } else if (keys[victim] != 0L && keys[victim] != key) {
            stats.replaceOtherKey++;
        }

        keys[victim] = key;
        info[victim] = w;
    }

    public boolean probeSE(long key, IntRef out) {
        stats.seProbes++;
        final int base = seBucket(key);
        for (int i = 0; i < SE_WAYS; i++) {
            int idx = base + i;
            if (seKeys[idx] == key) {
                int w = seInfo[idx];
                short se16 = (short) (w & 0xFFFF);
                out.value = se16;        // sign-extended to int
                stats.seHits++;
                return true;
            }
        }
        return false;
    }

    public void storeSE(long key, int staticEval) {
        // Don’t store mate-packed or extreme terminal “evals” as SE
        if (staticEval >=  CHECKMATE_VALUE - MAX_PLY) return;
        if (staticEval <= -CHECKMATE_VALUE + MAX_PLY) return;

        stats.seStores++;

        final int base = seBucket(key);
        final short se16 = clampToShort(staticEval);
        final int packed = packSE(se16, generation & 63);

        int victim = -1;
        int worst = -1; // largest age distance -> oldest

        for (int i = 0; i < SE_WAYS; i++) {
            int idx = base + i;
            long k = seKeys[idx];
            if (k == 0L || k == key) { victim = idx; break; }
            int gen6 = (seInfo[idx] >>> 26) & 63;
            int age = seAgeDist(gen6);
            if (age > worst) { worst = age; victim = idx; }
        }
        if (victim < 0) victim = base; // fallback

        seKeys[victim] = key;
        seInfo[victim] = packed;
    }

    public int peekMove(long key) {
        final int base = bucket(key);
        int bestIdx = -1, bestDepth = -1, bestGenDist = Integer.MAX_VALUE;
        for (int i = 0; i < WAYS; i++) {
            int idx = base + i;
            if (keys[idx] != key) continue;
            long w = info[idx];
            int d = (int) ((w >>> 8) & 0xFF);
            int gen = ((int) w) & 0xFF;
            int g6  = (gen >>> 2) & 63;
            int age = (generation - g6) & 63;    // smaller is younger
            if (d > bestDepth || (d == bestDepth && age < bestGenDist)) {
                bestDepth = d; bestGenDist = age; bestIdx = idx;
            }
        }
        return bestIdx < 0 ? 0 : (int) (info[bestIdx] >>> 32);
    }

    private int replacementScore(long word) {
        int meta = (int) (word & 0xFF);
        int gen  = (meta >>> 2) & 63;
        int ageDist = (generation - gen) & 63;
        int depth = (int) ((word >>> 8) & 0xFF);
        return (ageDist << 8) | (0xFF - depth);
    }

    private static int toTT(int score, int ply) {
        if (score >=  CHECKMATE_VALUE - MAX_PLY) return score + ply;
        if (score <= -CHECKMATE_VALUE + MAX_PLY) return score - ply;
        if (score >  32767) score =  32767;
        if (score < -32768) score = -32768;
        return score;
    }

    private static int fromTT(short packed, int ply) {
        int s = packed;
        if (s >=  CHECKMATE_VALUE - MAX_PLY) return s - ply;
        if (s <= -CHECKMATE_VALUE + MAX_PLY) return s + ply;
        return s;
    }

    public int capacitySlots() { return slots; }

    private int bucket(long key) {
        int h = Long.hashCode(key);
        h ^= (h >>> 16);
        h *= 0x9E3779B1;                   // golden ratio mix
        h ^= (h >>> 15);
        return (h & bucketsMask) * WAYS;
    }
}
