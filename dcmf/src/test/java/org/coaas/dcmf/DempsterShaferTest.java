package org.coaas.dcmf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class DempsterShaferTest {

    @Test
    void combiningTwoConsistentSourcesRaisesBelief() {
        DempsterShafer.Mass a = new DempsterShafer.Mass(0.6, 0.1, 0.3);
        DempsterShafer.Mass b = new DempsterShafer.Mass(0.7, 0.1, 0.2);
        DempsterShafer.Decision d = DempsterShafer.combine(a, b);
        // Both sources agree on caching — combined belief should exceed either input.
        assertTrue(d.beliefCache > 0.6, "combined belief in cache should exceed 0.6");
        assertTrue(d.beliefCache >= a.cache);
        assertTrue(d.beliefCache >= b.cache);
    }

    @Test
    void conflictingSourcesReduceBelief() {
        DempsterShafer.Mass a = new DempsterShafer.Mass(0.7, 0.1, 0.2);
        DempsterShafer.Mass b = new DempsterShafer.Mass(0.1, 0.7, 0.2);
        DempsterShafer.Decision d = DempsterShafer.combine(a, b);
        // Sources strongly disagree — conflict mass should be substantial.
        assertTrue(d.conflict > 0.4, "conflict mass should be > 0.4");
    }

    @Test
    void ignoranceOnlySourceLeavesOtherAlone() {
        DempsterShafer.Mass a = new DempsterShafer.Mass(0.6, 0.2, 0.2);
        DempsterShafer.Mass b = new DempsterShafer.Mass(0.0, 0.0, 1.0);  // total ignorance
        DempsterShafer.Decision d = DempsterShafer.combine(a, b);
        // With no evidence from b, combined belief should equal a's normalised belief.
        assertEquals(a.cache, d.beliefCache, 1e-9);
        assertEquals(a.evict, d.beliefEvict, 1e-9);
    }

    @Test
    void betPRespectsIgnoranceSplit() {
        DempsterShafer.Mass a = new DempsterShafer.Mass(0.4, 0.1, 0.5);
        DempsterShafer.Mass b = new DempsterShafer.Mass(0.4, 0.1, 0.5);
        DempsterShafer.Decision d = DempsterShafer.combine(a, b);
        double betP = d.betPCache();
        // BetP should split the remaining ignorance evenly between hypotheses.
        assertEquals(d.beliefCache + 0.5 * (1.0 - d.beliefCache - d.beliefEvict), betP, 1e-9);
    }
}
