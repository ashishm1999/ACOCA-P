package org.coaas.dcmf;

/**
 * Dempster-Shafer belief combination.
 *
 * We operate on the frame of discernment Ω = {cache, evict}, which yields
 * three mass focal elements: {cache}, {evict}, and Θ = {cache, evict}
 * (the ignorance mass). Each mass function sums to 1.
 *
 * The combined belief in a hypothesis A is computed from Dempster's rule:
 *
 *   m_{1⊕2}(A) = (1 / (1 - K)) · sum_{B ∩ C = A} m_1(B) · m_2(C)
 *
 * where K is the conflict mass. The pignistic transformation BetP is used
 * to derive the point estimate the cache decision uses.
 */
public final class DempsterShafer {

    private DempsterShafer() { /* no instances */ }

    /**
     * Bivariate mass function with masses on {cache}, {evict}, and Θ.
     */
    public static final class Mass {
        public final double cache;
        public final double evict;
        public final double theta;

        public Mass(double cache, double evict, double theta) {
            this.cache = cache;
            this.evict = evict;
            this.theta = theta;
        }

        /** Build a mass function from a probability-like belief in caching. */
        public static Mass fromBelief(double beliefCache, double ignorance) {
            double clampedIgnorance = Math.max(0.0, Math.min(1.0, ignorance));
            double remaining = 1.0 - clampedIgnorance;
            double c = Math.max(0.0, Math.min(1.0, beliefCache)) * remaining;
            double e = remaining - c;
            return new Mass(c, e, clampedIgnorance);
        }
    }

    /** Combined decision returned by {@link #combine(Mass, Mass)}. */
    public static final class Decision {
        public final double beliefCache;
        public final double beliefEvict;
        public final double conflict;

        public Decision(double beliefCache, double beliefEvict, double conflict) {
            this.beliefCache = beliefCache;
            this.beliefEvict = beliefEvict;
            this.conflict = conflict;
        }

        /** Pignistic (BetP) probability of caching. */
        public double betPCache() {
            return beliefCache + 0.5 * (1.0 - beliefCache - beliefEvict);
        }
    }

    /**
     * Combine two mass functions using Dempster's rule of combination.
     *
     * Focal intersections on the frame {cache, evict}:
     *   {cache} ∩ {cache}  = {cache}    (contributes to m(cache))
     *   {cache} ∩ Θ        = {cache}
     *   Θ       ∩ {cache}  = {cache}
     *   {evict} ∩ {evict}  = {evict}    (contributes to m(evict))
     *   {evict} ∩ Θ        = {evict}
     *   Θ       ∩ {evict}  = {evict}
     *   Θ       ∩ Θ        = Θ          (contributes to m(θ))
     *   {cache} ∩ {evict}  = ∅          (conflict K)
     */
    public static Decision combine(Mass a, Mass b) {
        double cache = a.cache * b.cache
                     + a.cache * b.theta
                     + a.theta * b.cache;
        double evict = a.evict * b.evict
                     + a.evict * b.theta
                     + a.theta * b.evict;
        double conflict = a.cache * b.evict + a.evict * b.cache;

        double norm = 1.0 - conflict;
        if (norm <= 0.0) {
            // Total conflict — fall back to the ignorance-only mass.
            return new Decision(0.0, 0.0, conflict);
        }

        return new Decision(cache / norm, evict / norm, conflict);
    }
}
