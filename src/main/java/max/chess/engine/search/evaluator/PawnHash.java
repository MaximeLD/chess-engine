package max.chess.engine.search.evaluator;

public final class PawnHash {
    private static final int WAYS = 2;

    public static final class Entry {
        long wp, bp;           // keys
        int  mgDiff, egDiff;   // side-differential of pawn structure (MG/EG), EXCLUDING blocked-passed
        long wPassed, bPassed; // passed-pawn bitboards (pawn-only definition)
        byte gen;              // 0..63
    }

    private final long[] keyWP;   // wp
    private final long[] keyBP;   // bp
    private final int[]  mgDiff;
    private final int[]  egDiff;
    private final long[] wPassed;
    private final long[] bPassed;
    private final byte[] gen;

    public static final class Hit {
        public boolean found;
        public int mgDiff, egDiff;
        public long wPassed, bPassed;
        public void reset() { found = false; mgDiff = egDiff = 0; wPassed = bPassed = 0L; }
    }

    private final int buckets, bucketsMask;
    private byte generation = 0;

    // Stats (optional)
    public long probes, hits;

    public PawnHash(int bucketsPow2) {
        // bucketsPow2 must be power of two (e.g., 1<<16 = 65536 buckets)
        this.buckets = bucketsPow2;
        this.bucketsMask = buckets - 1;
        int cap = buckets * WAYS;
        keyWP = new long[cap];
        keyBP = new long[cap];
        mgDiff = new int[cap];
        egDiff = new int[cap];
        wPassed = new long[cap];
        bPassed = new long[cap];
        gen = new byte[cap];
    }

    public void clear() {
        java.util.Arrays.fill(keyWP, 0L);
        java.util.Arrays.fill(keyBP, 0L);
        // mgDiff/egDiff/wPassed/bPassed need not be cleared when keys are 0.
        generation = 0;
    }

    public String toUCIInfo() {
        StringBuilder sb = new StringBuilder("info")
                .append(" pawn hits ").append(hits)
                .append(" pawn probes ").append(probes)
                .append(" %: ").append(probes==0?0:100*hits/probes);

        return sb.toString();
    }

    public void newSearch() { generation = (byte)((generation + 1) & 63); }

    boolean probe(long wp, long bp, Hit out) {
        probes++;
        int base = bucket(wp, bp);
        int bestIdx = -1, bestAge = -1;
        for (int i=0;i<WAYS;i++) {
            int idx = base + i;
            if (keyWP[idx] == wp && keyBP[idx] == bp) {
                hits++;
                out.found = true;
                out.mgDiff = mgDiff[idx];
                out.egDiff = egDiff[idx];
                out.wPassed = wPassed[idx];
                out.bPassed = bPassed[idx];
                return true;
            }
            // track empty or oldest for store
            int age = (keyWP[idx]==0L && keyBP[idx]==0L) ? 9999 : ((generation - gen[idx]) & 63);
            if (age > bestAge) { bestAge = age; bestIdx = idx; }
        }
        out.reset();
        return false;
    }

    void store(long wp, long bp, int mg, int eg, long wP, long bP) {
        int base = bucket(wp,bp);
        int victim = -1, worstAge = -1;
        for (int i=0;i<WAYS;i++) {
            int idx = base + i;
            if (keyWP[idx]==wp && keyBP[idx]==bp) { victim = idx; break; }
            int age = (keyWP[idx]==0L && keyBP[idx]==0L) ? 9999 : ((generation - gen[idx]) & 63);
            if (age > worstAge) { worstAge = age; victim = idx; }
        }
        keyWP[victim]=wp; keyBP[victim]=bp;
        mgDiff[victim]=mg; egDiff[victim]=eg;
        wPassed[victim]=wP; bPassed[victim]=bP;
        gen[victim]=generation;
    }

    private int bucket(long wp, long bp) {
        int h = mix64to32(wp) ^ Integer.rotateLeft(mix64to32(bp), 13);
        return (h & bucketsMask) * WAYS;
    }

    private static int mix64to32(long x) {
        x ^= (x >>> 33);
        x *= 0xff51afd7ed558ccdL;
        x ^= (x >>> 33);
        x *= 0xc4ceb9fe1a85ec53L;
        x ^= (x >>> 33);
        return (int)x;
    }
}
