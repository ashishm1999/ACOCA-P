package org.coaas.dcmf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Context Monitoring Manager.
 *
 * Tracks per-item CF using an exponential decay model with statistical
 * thresholds:
 *
 *   CF(Δt) = exp(-λ · Δt)
 *
 * with λ tuned per context type. Also holds a per-item mean and variance
 * of CF so DCMF can turn a raw CF reading into a Dempster-Shafer mass
 * function whose ignorance mass reflects the observed variance.
 */
public final class CMM {

    private final double lambdaDecay;
    private final double kappa;
    private final ConcurrentHashMap<String, WelfordStats> stats = new ConcurrentHashMap<>();

    public CMM(double lambdaDecay, double kappa) {
        this.lambdaDecay = lambdaDecay;
        this.kappa = kappa;
    }

    public double cf(long tLastMillis, long nowMillis) {
        double dtSec = Math.max(0.0, (nowMillis - tLastMillis) / 1000.0);
        return Math.exp(-lambdaDecay * dtSec);
    }

    public double observe(String itemId, double cf) {
        WelfordStats s = stats.computeIfAbsent(itemId, k -> new WelfordStats());
        s.update(cf);
        return s.mean();
    }

    public double threshold(String itemId) {
        WelfordStats s = stats.get(itemId);
        if (s == null || s.count() < 2) return 0.0;
        return Math.max(0.0, s.mean() - kappa * s.std());
    }

    public double variance(String itemId) {
        WelfordStats s = stats.get(itemId);
        return s == null ? 0.0 : s.variance();
    }

    /**
     * Turn a CF reading into a Dempster-Shafer mass function.
     *
     * The belief in caching rises with CF above threshold; the ignorance
     * mass rises with the observed variance so DCMF's DST combination
     * discounts noisy CF sources.
     */
    public DempsterShafer.Mass toMass(String itemId, double cf) {
        double theta = threshold(itemId);
        double belief = theta > 0.0 ? Math.min(1.0, Math.max(0.0, cf / (theta + 1e-6))) : cf;
        double ignorance = Math.min(0.5, Math.sqrt(variance(itemId)));
        return DempsterShafer.Mass.fromBelief(belief, ignorance);
    }

    /** Streaming (count, mean, M2) statistics — Welford 1962. */
    static final class WelfordStats {
        private long count = 0;
        private double mean = 0.0;
        private double m2 = 0.0;

        void update(double x) {
            count++;
            double delta = x - mean;
            mean += delta / count;
            double delta2 = x - mean;
            m2 += delta * delta2;
        }

        long count() { return count; }
        double mean() { return mean; }
        double variance() { return count < 2 ? 0.0 : m2 / (count - 1); }
        double std() { return Math.sqrt(variance()); }
    }
}
