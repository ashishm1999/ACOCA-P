package org.coaas.eccf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class ECCFComponentsTest {

    // --- CVAE ------------------------------------------------------------

    @Test
    void cvaeReportsHigherCVForVolatileStream() {
        CVAE cvae = new CVAE(0.3, 0.5);
        long t = 0;
        for (int i = 0; i < 20; i++) {
            cvae.observe("stable", 0.5, t);
            t += 1000;
        }
        double cvStable = cvae.get("stable");

        t = 0;
        for (int i = 0; i < 20; i++) {
            cvae.observe("volatile", i % 2 == 0 ? 0.1 : 0.9, t);
            t += 1000;
        }
        double cvVolatile = cvae.get("volatile");
        assertTrue(cvVolatile > cvStable, "volatile CV should exceed stable CV");
    }

    // --- LCVF ------------------------------------------------------------

    @Test
    void lcvfUtilityRisesWithFavourableFactors() {
        LCVF lcvf = new LCVF();
        LCVF.Factors good = new LCVF.Factors(0.9, 0.9, 0.1, 0.1, 0.9, 0.95);
        LCVF.Factors bad  = new LCVF.Factors(0.1, 0.1, 0.9, 0.9, 0.1, 0.3);
        assertTrue(lcvf.utility(good) > lcvf.utility(bad));
    }

    @Test
    void lcvfUtilityIsBounded() {
        LCVF lcvf = new LCVF();
        LCVF.Factors f = new LCVF.Factors(0.5, 0.5, 0.5, 0.5, 0.5, 0.5);
        double u = lcvf.utility(f);
        assertTrue(u >= 0.0 && u <= 1.0);
    }

    // --- Count-Min Sketch -----------------------------------------------

    @Test
    void cmsUnderestimatesTrueFrequency() {
        CountMinSketch cms = new CountMinSketch(1024, 4, 42L);
        for (int i = 0; i < 100; i++) cms.add("hot", 1L);
        for (int i = 0; i < 10; i++)  cms.add("warm", 1L);
        assertTrue(cms.estimate("hot") >= 100L);
        assertTrue(cms.estimate("warm") >= 10L);
    }

    @Test
    void cmsMergePreservesCounts() {
        CountMinSketch a = new CountMinSketch(512, 4, 7L);
        CountMinSketch b = new CountMinSketch(512, 4, 7L);
        a.add("item1", 5);
        b.add("item1", 3);
        a.merge(b);
        assertTrue(a.estimate("item1") >= 8L);
    }

    // --- Gossip ---------------------------------------------------------

    @Test
    void gossipReachesEveryPeerOnSync() {
        CountMinSketch cms = new CountMinSketch(256, 3, 1L);
        int[] delivered = {0};
        Gossip.Transport transport = (peer, payload) -> {
            delivered[0]++;
            return true;
        };
        Gossip g = new Gossip("h1", List.of("b1", "m1", "c1"), transport, cms, 99L);
        int successes = g.syncRound();
        assertEquals(3, successes);
        assertEquals(3, delivered[0]);
    }

    @Test
    void gossipRoundPushesOnePeer() {
        CountMinSketch cms = new CountMinSketch(256, 3, 1L);
        int[] delivered = {0};
        Gossip.Transport transport = (peer, payload) -> {
            delivered[0]++;
            return true;
        };
        Gossip g = new Gossip("h1", List.of("b1", "m1"), transport, cms, 12L);
        g.gossipRound();
        assertEquals(1, delivered[0]);
    }
}
