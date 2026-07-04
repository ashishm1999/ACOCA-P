package org.coaas.dcmf;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Context Evaluation Engine.
 *
 * Maintains per-item EWMA statistics on the composite PoA that combines
 * CAPME's model output with a longer-horizon historical average. The
 * balance is controlled by {@link #alpha} (historical weight) and
 * {@link #beta} (utility-PoA balance).
 *
 * Composite PoA: comp = alpha · hist + (1 - alpha) · current
 *                utility-adjusted = beta · comp + (1 - beta) · quality
 */
public final class CEE {

    private final double alpha;   // historical EWMA weight
    private final double beta;    // utility-PoA balance

    private final ConcurrentHashMap<String, Double> history = new ConcurrentHashMap<>();

    public CEE(double alpha, double beta) {
        if (alpha < 0 || alpha > 1) throw new IllegalArgumentException("alpha must be in [0, 1]");
        if (beta  < 0 || beta  > 1) throw new IllegalArgumentException("beta must be in [0, 1]");
        this.alpha = alpha;
        this.beta  = beta;
    }

    /** Update the per-item running average with a fresh PoA reading. */
    public double update(String itemId, double currentPoa) {
        return history.compute(itemId, (id, prev) -> {
            if (prev == null) {
                return currentPoa;
            }
            return alpha * prev + (1.0 - alpha) * currentPoa;
        });
    }

    /** Compute a utility-adjusted composite PoA, folding in a quality signal. */
    public double compositeScore(String itemId, double currentPoa, double quality) {
        double comp = update(itemId, currentPoa);
        return beta * comp + (1.0 - beta) * clamp(quality);
    }

    public Double getHistorical(String itemId) {
        return history.get(itemId);
    }

    public int size() {
        return history.size();
    }

    public void reset() {
        history.clear();
    }

    private static double clamp(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
