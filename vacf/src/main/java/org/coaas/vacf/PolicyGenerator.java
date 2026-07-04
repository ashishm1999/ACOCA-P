package org.coaas.vacf;

/**
 * VACF policy generator.
 *
 * Given CVI, CF, and PoA for a cached item plus the caller's SLA bound,
 * produces the adaptive TTL and the demand-aware replica count. The
 * formulae are from Chapter 8 of the thesis:
 *
 *   TTL_base = τ_min + (τ_max - τ_min) · (1 - CVI)^α
 *   TTL_adj  = a + b · CF
 *   TTL     = min(SLA, TTL_base · TTL_adj)
 *
 *   replicas = r_min + floor( λ_r · PoA · (1 - CVI) )
 *
 * with SLA measured in milliseconds and CVI/CF/PoA in [0, 1].
 */
public final class PolicyGenerator {

    private final long tauMinMs;
    private final long tauMaxMs;
    private final double alpha;
    private final int replicaMin;
    private final int replicaMax;
    private final double lambdaR;

    private static final double COEFF_A = 0.85;
    private static final double COEFF_B = 0.30;

    public PolicyGenerator(long tauMinMs, long tauMaxMs, double alpha,
                           int replicaMin, int replicaMax) {
        if (tauMinMs > tauMaxMs) {
            throw new IllegalArgumentException("tauMin must be <= tauMax");
        }
        this.tauMinMs = tauMinMs;
        this.tauMaxMs = tauMaxMs;
        this.alpha = alpha;
        this.replicaMin = replicaMin;
        this.replicaMax = replicaMax;
        this.lambdaR = Math.max(1, replicaMax - replicaMin);
    }

    public static final class Policy {
        public final long ttlMs;
        public final int replicas;
        public final double cvi;
        public final double cf;
        public final double poa;

        public Policy(long ttlMs, int replicas, double cvi, double cf, double poa) {
            this.ttlMs = ttlMs;
            this.replicas = replicas;
            this.cvi = cvi;
            this.cf = cf;
            this.poa = poa;
        }
    }

    public Policy generate(double cvi, double cf, double poa, long slaMs) {
        double cviClamped = clamp01(cvi);
        double cfClamped = clamp01(cf);
        double poaClamped = clamp01(poa);

        double base = tauMinMs + (tauMaxMs - tauMinMs) * Math.pow(1.0 - cviClamped, alpha);
        double adj  = COEFF_A + COEFF_B * cfClamped;
        long ttl = (long) Math.min(slaMs, base * adj);
        ttl = Math.max(tauMinMs, Math.min(tauMaxMs, ttl));

        int replicas = replicaMin + (int) Math.floor(lambdaR * poaClamped * (1.0 - cviClamped));
        replicas = Math.max(replicaMin, Math.min(replicaMax, replicas));

        return new Policy(ttl, replicas, cviClamped, cfClamped, poaClamped);
    }

    private static double clamp01(double x) {
        if (Double.isNaN(x)) return 0.0;
        return Math.max(0.0, Math.min(1.0, x));
    }
}
