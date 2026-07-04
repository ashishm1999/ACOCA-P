package org.coaas.eccf;

/**
 * Local Caching Value Function.
 *
 * Combines six factors into a scalar utility used for placement, refresh,
 * and eviction on every edge node (Chapter 10). Higher utility ⇒ retain.
 *
 *   utility = w_poa · PoA
 *           + w_cf  · CF
 *           + w_stab · (1 − CV)
 *           + w_hop · (1 − hopCost)
 *           + w_miss · missCost
 *           + w_rep · providerReputation
 *
 * Default weights are drawn from Chapter 10; deployments can override
 * them per node.
 */
public final class LCVF {

    /** Default weights (must sum to 1). */
    public static final double W_POA  = 0.28;
    public static final double W_CF   = 0.22;
    public static final double W_STAB = 0.18;
    public static final double W_HOP  = 0.10;
    public static final double W_MISS = 0.12;
    public static final double W_REP  = 0.10;

    public static final class Factors {
        public double poa;
        public double cf;
        public double cv;                 // 0 = stable, 1 = volatile
        public double hopCost;            // normalised 0..1
        public double missCost;           // normalised 0..1
        public double providerReputation; // 0..1

        public Factors(double poa, double cf, double cv, double hopCost,
                       double missCost, double providerReputation) {
            this.poa = poa;
            this.cf = cf;
            this.cv = cv;
            this.hopCost = hopCost;
            this.missCost = missCost;
            this.providerReputation = providerReputation;
        }
    }

    private final double wPoa;
    private final double wCf;
    private final double wStab;
    private final double wHop;
    private final double wMiss;
    private final double wRep;

    public LCVF() {
        this(W_POA, W_CF, W_STAB, W_HOP, W_MISS, W_REP);
    }

    public LCVF(double wPoa, double wCf, double wStab, double wHop, double wMiss, double wRep) {
        double sum = wPoa + wCf + wStab + wHop + wMiss + wRep;
        if (sum <= 0.0) throw new IllegalArgumentException("weights sum must be > 0");
        this.wPoa  = wPoa  / sum;
        this.wCf   = wCf   / sum;
        this.wStab = wStab / sum;
        this.wHop  = wHop  / sum;
        this.wMiss = wMiss / sum;
        this.wRep  = wRep  / sum;
    }

    public double utility(Factors f) {
        return wPoa  * clamp01(f.poa)
             + wCf   * clamp01(f.cf)
             + wStab * (1.0 - clamp01(f.cv))
             + wHop  * (1.0 - clamp01(f.hopCost))
             + wMiss * clamp01(f.missCost)
             + wRep  * clamp01(f.providerReputation);
    }

    /** Convenience: eviction score = 1 - utility. */
    public double evictionScore(Factors f) {
        return 1.0 - utility(f);
    }

    private static double clamp01(double v) {
        if (Double.isNaN(v)) return 0.0;
        return Math.max(0.0, Math.min(1.0, v));
    }
}
