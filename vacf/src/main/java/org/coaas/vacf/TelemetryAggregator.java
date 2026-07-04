package org.coaas.vacf;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Aggregates telemetry pushed by every edge node so the VACF-Core
 * controller can drive its five-stage policy pipeline. Runs on the
 * controller, listens on gRPC (see {@link PolicyServiceImpl}) and
 * updates a per-item view of freshness, access rate, and volatility.
 */
public final class TelemetryAggregator {

    /** Latest snapshot for one cached item across the fleet. */
    public static final class Snapshot {
        public final String itemId;
        public double avgFreshness;
        public double accessRatePerMin;
        public long lastReportMs;
        public double hitRate;
        public double missRate;

        public Snapshot(String itemId) {
            this.itemId = itemId;
        }
    }

    private final ConcurrentHashMap<String, Snapshot> snapshots = new ConcurrentHashMap<>();
    private final Map<String, Long> lastReportedByNode = new ConcurrentHashMap<>();
    private final AtomicLong totalReports = new AtomicLong();

    public void report(String nodeId, long reportedAtMs, double hitRate, double missRate,
                       double avgFreshness, List<ItemReading> readings) {
        lastReportedByNode.put(nodeId, reportedAtMs);
        totalReports.incrementAndGet();
        for (ItemReading r : readings) {
            Snapshot s = snapshots.computeIfAbsent(r.itemId, Snapshot::new);
            synchronized (s) {
                s.avgFreshness = 0.5 * s.avgFreshness + 0.5 * r.cf;
                s.hitRate = 0.7 * s.hitRate + 0.3 * hitRate;
                s.missRate = 0.7 * s.missRate + 0.3 * missRate;
                long dt = Math.max(1L, reportedAtMs - s.lastReportMs);
                s.accessRatePerMin = 60_000.0 / dt;
                s.lastReportMs = reportedAtMs;
            }
        }
    }

    public Snapshot snapshot(String itemId) {
        return snapshots.get(itemId);
    }

    public Map<String, Long> nodeHeartbeats() {
        return new HashMap<>(lastReportedByNode);
    }

    public int itemsTracked() {
        return snapshots.size();
    }

    public long totalReports() {
        return totalReports.get();
    }

    public static final class ItemReading {
        public final String itemId;
        public final double cf;

        public ItemReading(String itemId, double cf) {
            this.itemId = itemId;
            this.cf = cf;
        }
    }
}
