package org.coaas.vacf;

/**
 * VACF-DSH decision engine.
 *
 * Applies Dempster-Shafer belief fusion to CVI, CF, and PoA masses to
 * decide whether a cached item should be retained or evicted. Uses the
 * same combination rule as DCMF but with three sources.
 */
public final class VACFDSH {

    public static final double BELIEF_THRESHOLD = 0.6;

    public static final class Verdict {
        public final boolean retain;
        public final double belief;
        public final double conflict;

        public Verdict(boolean retain, double belief, double conflict) {
            this.retain = retain;
            this.belief = belief;
            this.conflict = conflict;
        }
    }

    private VACFDSH() { /* no instances */ }

    /**
     * Combine three evidence sources — CVI (retain iff low), CF (retain
     * iff high), and PoA (retain iff high) — via Dempster's rule.
     *
     * Each source contributes m(retain), m(evict), m(θ) with m(θ) rising
     * with the source's uncertainty. Sources are combined pairwise.
     */
    public static Verdict decide(double cvi, double cf, double poa) {
        double retainCVI = 1.0 - clamp01(cvi);    // low volatility -> retain
        double retainCF = clamp01(cf);
        double retainPoA = clamp01(poa);

        double[] result = new double[]{0.0, 0.0, 1.0};  // start from total ignorance
        result = combineIn(result, massFromBelief(retainCVI, 0.15));
        result = combineIn(result, massFromBelief(retainCF, 0.10));
        result = combineIn(result, massFromBelief(retainPoA, 0.10));

        double belief = result[0];
        double conflict = 0.0;  // approximate — track only for reporting
        boolean retain = belief >= BELIEF_THRESHOLD;
        return new Verdict(retain, belief, conflict);
    }

    /** Returns {m(retain), m(evict), m(θ)}. */
    private static double[] massFromBelief(double beliefRetain, double ignorance) {
        double clampedIgnorance = clamp01(ignorance);
        double remaining = 1.0 - clampedIgnorance;
        double retain = clamp01(beliefRetain) * remaining;
        double evict = remaining - retain;
        return new double[]{retain, evict, clampedIgnorance};
    }

    /** Dempster's rule on the frame {retain, evict}. */
    private static double[] combineIn(double[] a, double[] b) {
        double retain = a[0] * b[0] + a[0] * b[2] + a[2] * b[0];
        double evict  = a[1] * b[1] + a[1] * b[2] + a[2] * b[1];
        double conflict = a[0] * b[1] + a[1] * b[0];
        double norm = 1.0 - conflict;
        if (norm <= 0.0) return new double[]{0.0, 0.0, 1.0};
        return new double[]{retain / norm, evict / norm, (a[2] * b[2]) / norm};
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
