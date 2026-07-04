package org.coaas.dcmf;

/**
 * Orchestrates the DCMF refinement decision.
 *
 * Given CF and PoA evidence for one context item, DCMF:
 *   1. asks {@link CMM} to convert CF into a Dempster-Shafer mass;
 *   2. asks {@link CEE} for a composite PoA and converts that into a
 *      second Dempster-Shafer mass;
 *   3. combines the two masses via Dempster's rule (see
 *      {@link DempsterShafer#combine(DempsterShafer.Mass, DempsterShafer.Mass)});
 *   4. returns a Decision whose pignistic probability is thresholded
 *      against the caller's belief threshold.
 */
public final class DecisionEngine {

    private final CEE cee;
    private final CMM cmm;
    private final double beliefThreshold;

    public DecisionEngine(CEE cee, CMM cmm, double beliefThreshold) {
        this.cee = cee;
        this.cmm = cmm;
        this.beliefThreshold = beliefThreshold;
    }

    /** Outcome of a DCMF decision for one context item. */
    public static final class Outcome {
        public final boolean cache;
        public final double beliefCache;
        public final double beliefEvict;
        public final double conflict;
        public final double betP;

        public Outcome(boolean cache, double beliefCache, double beliefEvict, double conflict, double betP) {
            this.cache = cache;
            this.beliefCache = beliefCache;
            this.beliefEvict = beliefEvict;
            this.conflict = conflict;
            this.betP = betP;
        }
    }

    public Outcome decide(String itemId, double cf, double poa, double quality) {
        cmm.observe(itemId, cf);
        DempsterShafer.Mass cfMass = cmm.toMass(itemId, cf);

        double composite = cee.compositeScore(itemId, poa, quality);
        DempsterShafer.Mass poaMass = DempsterShafer.Mass.fromBelief(composite, 0.1);

        DempsterShafer.Decision combined = DempsterShafer.combine(cfMass, poaMass);
        double betP = combined.betPCache();
        boolean cache = betP >= beliefThreshold;
        return new Outcome(cache, combined.beliefCache, combined.beliefEvict, combined.conflict, betP);
    }
}
