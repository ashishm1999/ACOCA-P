package org.coaas.eccf;

import java.util.Arrays;
import java.util.Random;

/**
 * Count-Min Sketch (Cormode & Muthukrishnan 2005).
 *
 * Compact probabilistic frequency counter used by ECCF gossip to
 * summarise per-item access counts without shipping the full cache
 * catalogue. On the two-timescale gossip protocol (T_gossip = 2 s,
 * T_sync = 60 s), each peer transmits its serialised CMS instead of
 * a raw item list, keeping payload under ~24 KB/s.
 *
 * Guarantees: with width w and depth d,
 *   estimated_freq ≤ true_freq + ε · N  with prob. ≥ 1 - δ
 *   where ε = e / w and δ = e^{-d}.
 */
public final class CountMinSketch {

    private final int width;
    private final int depth;
    private final long[][] table;
    private final int[] seeds;
    private long total = 0L;

    public CountMinSketch(int width, int depth, long randomSeed) {
        if (width <= 0 || depth <= 0) {
            throw new IllegalArgumentException("width and depth must be positive");
        }
        this.width = width;
        this.depth = depth;
        this.table = new long[depth][width];
        this.seeds = new int[depth];
        Random rng = new Random(randomSeed);
        for (int i = 0; i < depth; i++) seeds[i] = rng.nextInt();
    }

    public void add(String key, long count) {
        for (int i = 0; i < depth; i++) {
            int idx = Math.floorMod(hash(key, seeds[i]), width);
            table[i][idx] += count;
        }
        total += count;
    }

    public long estimate(String key) {
        long min = Long.MAX_VALUE;
        for (int i = 0; i < depth; i++) {
            int idx = Math.floorMod(hash(key, seeds[i]), width);
            if (table[i][idx] < min) min = table[i][idx];
        }
        return min == Long.MAX_VALUE ? 0L : min;
    }

    /** Merge another CMS with the same dimensions into this one. */
    public CountMinSketch merge(CountMinSketch other) {
        if (other.width != width || other.depth != depth) {
            throw new IllegalArgumentException("cannot merge CMS with mismatched dimensions");
        }
        for (int i = 0; i < depth; i++) {
            for (int j = 0; j < width; j++) {
                table[i][j] += other.table[i][j];
            }
        }
        total += other.total;
        return this;
    }

    /** Serialise as a compact byte string for gossip payloads. */
    public byte[] serialise() {
        // 8 bytes total + width*depth * 8 bytes of counter data.
        int payload = 8 + width * depth * 8;
        byte[] out = new byte[payload];
        writeLong(out, 0, total);
        int off = 8;
        for (long[] row : table) {
            for (long v : row) {
                writeLong(out, off, v);
                off += 8;
            }
        }
        return out;
    }

    /** Debug: how many bytes on the wire per gossip round. */
    public int sizeBytes() {
        return 8 + width * depth * 8;
    }

    public long total() {
        return total;
    }

    private static int hash(String key, int seed) {
        // Murmur-lite: fine for CMS, not for security.
        int h = seed;
        for (int i = 0; i < key.length(); i++) {
            h ^= key.charAt(i);
            h *= 0x9E3779B1;
            h = Integer.rotateLeft(h, 13);
        }
        return h;
    }

    private static void writeLong(byte[] buf, int off, long v) {
        for (int i = 7; i >= 0; i--) {
            buf[off + i] = (byte) (v & 0xFF);
            v >>>= 8;
        }
    }

    @Override
    public String toString() {
        return "CMS(" + width + "x" + depth + ", total=" + total + ", bytes=" + sizeBytes() + ")";
    }

    // Package-private for tests
    long[][] tableSnapshot() {
        long[][] out = new long[depth][];
        for (int i = 0; i < depth; i++) out[i] = Arrays.copyOf(table[i], width);
        return out;
    }
}
