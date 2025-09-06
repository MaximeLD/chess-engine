package max.chess.engine.game;

public final class RepetitionCounter {
    private final long[] keys; // power-of-two sized
    private final int[] cnt;
    private final int[] stamp; // epoch markers
    private final int mask; // capacity - 1
    private int epoch;


    public RepetitionCounter(int capacityPow2) {
        int cap = 1 << capacityPow2; // e.g., 8..10 (256..1024)
        this.keys = new long[cap];
        this.cnt = new int[cap];
        this.stamp = new int[cap];
        this.mask = cap - 1;
        this.epoch = 1; // 0 means "empty"
    }


    /** O(1) logical clear for positions since last irreversible event. */
    public void resetEpoch() {
        if (++epoch == 0) { // wrap safety
            java.util.Arrays.fill(stamp, 0);
            epoch = 1;
        }
    }


    /** SplitMix64-based mixer for even distribution of Zobrist keys. */
    private static long mix(long z) {
        z += 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Snapshot current epoch so you can restore it on undo. */
    public int snapshotEpoch() { return epoch; }

    /** Restore a previously saved epoch (from snapshotEpoch). */
    public void restoreEpoch(int prevEpoch) { epoch = (prevEpoch == 0 ? 1 : prevEpoch); }

    /** Find slot for key: returns either existing key index or the first free slot for this epoch. */
    private int find(long k) {
        int i = (int) (mix(k) & mask);
        final int start = i;
        while (true) {
            int s = stamp[i];
            if (s != epoch) return i; // empty for this epoch
            if (keys[i] == k) return i; // found existing
            i = (i + 1) & mask; // linear probe
            if (i == start) throw new IllegalStateException(
                    "RepetitionCounter full for current epoch; increase capacity or resetEpoch more often");
        }
    }


    /** Increment count of key for current epoch. */
    public void inc(long k) {
        int i = find(k);
        if (stamp[i] != epoch) { stamp[i] = epoch; keys[i] = k; cnt[i] = 1; }
        else { cnt[i]++; }
    }


    /** Decrement; if it reaches zero, free the slot for reuse this epoch. */
    public void dec(long k) {
        int i = find(k);
        if (stamp[i] == epoch && keys[i] == k) {
            if (--cnt[i] <= 0) {
                cnt[i] = 0;
                stamp[i] = 0; // mark empty; optional: keys[i] = 0L;
            }
        }
    }


    /** Current count (0 if absent). */
    public int get(long k) {
        int i = find(k);
        return (stamp[i] == epoch && keys[i] == k) ? cnt[i] : 0;
    }
}