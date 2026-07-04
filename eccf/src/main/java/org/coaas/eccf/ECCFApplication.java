package org.coaas.eccf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * ECCF — Edge Context Caching Framework.
 *
 * Chapter 10 of the thesis. Distributed edge realisation of ACOCA-P
 * that removes the central controller. Each edge node runs:
 *
 *   1. Context Variability Assessment Engine (CVAE) — local dual
 *      estimator combining a stability measure and a rate-of-change
 *      measure in O(m) streaming time and memory.
 *   2. Local Caching Value Function (LCVF) — six-factor utility for
 *      placement, refresh, and eviction (PoA, CF, stability, hop cost,
 *      miss cost, provider reputation).
 *   3. Two-timescale gossip protocol (T_gossip = 2 s peer exchange,
 *      T_sync = 60 s core sync) with Count-Min Sketch summaries.
 */
@SpringBootApplication
@EnableScheduling
public class ECCFApplication {
    public static void main(String[] args) {
        SpringApplication.run(ECCFApplication.class, args);
    }
}
