package org.coaas.vacf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PolicyGeneratorTest {

    private final PolicyGenerator gen =
        new PolicyGenerator(30_000L, 300_000L, 1.5, 1, 3);

    @Test
    void lowVolatilityYieldsLongTTL() {
        PolicyGenerator.Policy p = gen.generate(0.05, 0.95, 0.5, 5_000_000L);
        assertTrue(p.ttlMs > 150_000L, "low CVI should give a long TTL");
    }

    @Test
    void highVolatilityContractsTTL() {
        PolicyGenerator.Policy p = gen.generate(0.85, 0.5, 0.5, 5_000_000L);
        assertTrue(p.ttlMs < 90_000L, "high CVI should give a short TTL");
    }

    @Test
    void slaCapsTTL() {
        PolicyGenerator.Policy p = gen.generate(0.05, 0.95, 0.5, 60_000L);
        assertTrue(p.ttlMs <= 60_000L, "TTL cannot exceed SLA bound");
    }

    @Test
    void demandDrivesReplicas() {
        PolicyGenerator.Policy hot = gen.generate(0.05, 0.9, 0.95, 5_000_000L);
        PolicyGenerator.Policy cold = gen.generate(0.05, 0.9, 0.05, 5_000_000L);
        assertTrue(hot.replicas >= cold.replicas);
    }

    @Test
    void replicaCountRespectsBounds() {
        for (double poa : new double[]{0.0, 0.25, 0.5, 0.75, 1.0}) {
            PolicyGenerator.Policy p = gen.generate(0.2, 0.5, poa, 5_000_000L);
            assertTrue(p.replicas >= 1 && p.replicas <= 3);
        }
    }

    @Test
    void cviComputesLargerDispersionForVolatileStream() {
        double[] stable = {0.5, 0.5, 0.5, 0.5, 0.5, 0.5, 0.5};
        double[] volatile_ = {0.1, 0.9, 0.2, 0.8, 0.15, 0.85, 0.1};
        assertTrue(CVI.compute(volatile_) > CVI.compute(stable));
    }
}
