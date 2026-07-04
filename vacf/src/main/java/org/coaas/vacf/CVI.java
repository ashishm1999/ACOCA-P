package org.coaas.vacf;

/**
 * Context Volatility Index.
 *
 * Streaming multi-scale EWMA over one attribute's observation history,
 * with dispersion aggregated across scales by a p-norm. Chapter 8 of
 * the thesis defines the CVI at scale s as
 *
 *   CVI_s = sqrt( EWMVar_s(x) )
 *
 * and the item-level CVI is p-norm aggregate over the configured
 * window set W = {1, 5, 15} minutes.
 */
public final class CVI {

    /** Scale definitions used across VACF. */
    public static final class Scale {
        public final int windowSeconds;
        public final double weight;   // EWMA weight multiplier

        public Scale(int windowSeconds, double weight) {
            this.windowSeconds = windowSeconds;
            this.weight = weight;
        }
    }

    public static final Scale[] SCALES = {
        new Scale(60,   1.00),   // 1-minute window (fast reaction)
        new Scale(300,  0.65),   // 5-minute window
        new Scale(900,  0.35),   // 15-minute window (slow baseline)
    };

    public static final double BETA = 0.1;
    public static final double P_NORM = 1.0;

    private CVI() { /* no instances */ }

    /**
     * Compute the CVI for a stream of observations.
     *
     * @param observations one attribute's samples in the current window
     * @param beta          EWMA decay parameter
     * @param pNorm         norm exponent for cross-scale aggregation
     */
    public static double compute(double[] observations, double beta, double pNorm) {
        if (observations == null || observations.length == 0) return 0.0;
        double[] dispersion = new double[SCALES.length];
        for (int s = 0; s < SCALES.length; s++) {
            double b = beta * SCALES[s].weight;
            double mean = 0.0;
            double var = 0.0;
            for (double x : observations) {
                double delta = x - mean;
                mean += b * delta;
                var  = (1.0 - b) * (var + b * delta * delta);
            }
            dispersion[s] = Math.sqrt(var);
        }
        return pNormAggregate(dispersion, pNorm);
    }

    public static double compute(double[] observations) {
        return compute(observations, BETA, P_NORM);
    }

    private static double pNormAggregate(double[] xs, double p) {
        if (p == 1.0) {
            double s = 0.0;
            for (double x : xs) s += Math.abs(x);
            return s;
        }
        double s = 0.0;
        for (double x : xs) s += Math.pow(Math.abs(x), p);
        return Math.pow(s, 1.0 / p);
    }
}
