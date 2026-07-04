package org.coaas.eccf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Context Variability Assessment Engine.
 *
 * Per-node dual estimator (Chapter 10). Combines:
 *
 *   stability  — an EWMA-tracked coefficient of variation
 *   rate       — an EWMA-tracked rate of change per second
 *
 * Both are updated in O(1) per observation, so the total streaming
 * cost is O(m) per item across a burst of m observations. This keeps
 * CVAE affordable on the Raspberry Pi 5 edge nodes where the windowed
 * statistics required by VACF's centralised CVI exceed the compute
 * budget.
 */
public final class CVAE {

    private final double stabilityAlpha;   // EWMA weight for stability
    private final double rateBeta;         // EWMA weight for rate-of-change
    private final ConcurrentHashMap<String, State> states = new ConcurrentHashMap<>();

    public CVAE(double stabilityAlpha, double rateBeta) {
        this.stabilityAlpha = stabilityAlpha;
        this.rateBeta = rateBeta;
    }

    public double observe(String itemId, double value, long timestampMs) {
        State s = states.computeIfAbsent(itemId, k -> new State());
        return s.observe(value, timestampMs, stabilityAlpha, rateBeta);
    }

    public double get(String itemId) {
        State s = states.get(itemId);
        return s == null ? 0.0 : s.currentCV();
    }

    public int trackedItems() {
        return states.size();
    }

    static final class State {
        double mean = Double.NaN;
        double sqMean = 0.0;         // EWMA of squared value
        double lastValue = Double.NaN;
        long lastTs = -1L;
        double stability = 0.0;      // low = stable
        double rate = 0.0;           // per-second rate of change
        long updates = 0;

        double observe(double x, long tsMs, double alpha, double beta) {
            if (Double.isNaN(mean)) {
                mean = x;
                sqMean = x * x;
                lastValue = x;
                lastTs = tsMs;
                stability = 0.0;
                rate = 0.0;
                updates = 1;
                return 0.0;
            }
            // Update EWMA of value and value^2.
            mean   = alpha * mean   + (1.0 - alpha) * x;
            sqMean = alpha * sqMean + (1.0 - alpha) * (x * x);
            double var = Math.max(0.0, sqMean - mean * mean);
            double std = Math.sqrt(var);
            double cv = mean == 0.0 ? std : std / Math.abs(mean);
            stability = alpha * stability + (1.0 - alpha) * cv;

            // Update rate of change per second.
            long dtMs = Math.max(1L, tsMs - lastTs);
            double dv = Math.abs(x - lastValue);
            double instRate = dv / (dtMs / 1000.0);
            rate = beta * rate + (1.0 - beta) * instRate;

            lastValue = x;
            lastTs = tsMs;
            updates++;
            return currentCV();
        }

        /** The item-level CV combining stability and rate. */
        double currentCV() {
            return 0.5 * clamp01(stability) + 0.5 * clamp01(rate);
        }

        private static double clamp01(double v) {
            if (Double.isNaN(v)) return 0.0;
            return Math.max(0.0, Math.min(1.0, v));
        }
    }
}
