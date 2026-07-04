package org.coaas.vacf;

import java.util.Arrays;

/**
 * Multi-Attribute Fusion Engine.
 *
 * Aggregates per-attribute CVIs into a single item-level score using
 * AHP-derived weights (from Chapter 8). Uses a p-norm aggregator so
 * that VACF can emphasise peak volatility during incidents (p=2) and
 * average volatility under stable operation (p=1).
 */
public final class MAFE {

    private final double[] weights;    // sums to 1, one per attribute
    private double pNorm;

    public MAFE(double[] weights, double pNorm) {
        double sum = 0.0;
        for (double w : weights) {
            if (w < 0) throw new IllegalArgumentException("weights must be non-negative");
            sum += w;
        }
        if (sum == 0.0) {
            this.weights = new double[weights.length];
            Arrays.fill(this.weights, 1.0 / weights.length);
        } else {
            this.weights = new double[weights.length];
            for (int i = 0; i < weights.length; i++) this.weights[i] = weights[i] / sum;
        }
        this.pNorm = pNorm;
    }

    public double aggregate(double[] attributeCVIs) {
        if (attributeCVIs.length != weights.length) {
            throw new IllegalArgumentException("mismatched attribute count");
        }
        if (pNorm == 1.0) {
            double s = 0.0;
            for (int i = 0; i < weights.length; i++) s += weights[i] * attributeCVIs[i];
            return s;
        }
        double s = 0.0;
        for (int i = 0; i < weights.length; i++) {
            s += weights[i] * Math.pow(Math.abs(attributeCVIs[i]), pNorm);
        }
        return Math.pow(s, 1.0 / pNorm);
    }

    /** Increase the norm when incidents are detected. */
    public void setPNorm(double pNorm) {
        this.pNorm = pNorm;
    }

    public double[] weights() {
        return Arrays.copyOf(weights, weights.length);
    }
}
