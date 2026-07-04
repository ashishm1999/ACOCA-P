package org.coaas.vacf;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Random;

import org.junit.jupiter.api.Test;

class VACFNGBTest {

    @Test
    void ngbPredictionsInProbabilityRange() {
        VACFNGB model = new VACFNGB();
        model.fit(sampleX(200, 42L), sampleY(200, 42L), 20, 42L);
        double[] x = new double[]{0.3, 0.8, 0.7, 0.2, 0.9, 0.1, 0.9};
        double p = model.predict(x);
        assertTrue(p >= 0.0 && p <= 1.0, "probability out of range: " + p);
    }

    @Test
    void ngbFavoursHighCFHighPoALowCVI() {
        VACFNGB model = new VACFNGB();
        model.fit(sampleX(500, 7L), sampleY(500, 7L), 50, 7L);

        double[] retain = new double[]{0.1, 0.95, 0.9, 0.1, 0.95, 0.0, 1.0};
        double[] evict  = new double[]{0.9, 0.05, 0.1, 0.9, 0.30, 0.0, 1.0};
        double pRetain = model.predict(retain);
        double pEvict  = model.predict(evict);
        assertTrue(pRetain > pEvict, "retain probability should exceed evict probability");
    }

    // Generate a synthetic dataset that reflects the rule
    //   retain iff CVI low, CF high, PoA high.
    private double[][] sampleX(int n, long seed) {
        Random r = new Random(seed);
        double[][] x = new double[n][7];
        for (int i = 0; i < n; i++) {
            x[i][0] = r.nextDouble();   // cvi
            x[i][1] = r.nextDouble();   // cf
            x[i][2] = r.nextDouble();   // poa
            x[i][3] = r.nextDouble();   // hop cost
            x[i][4] = r.nextDouble();   // provider reputation
            x[i][5] = r.nextDouble();   // tod sin
            x[i][6] = r.nextDouble();   // tod cos
        }
        return x;
    }

    private int[] sampleY(int n, long seed) {
        Random r = new Random(seed);
        double[][] x = sampleX(n, seed);
        int[] y = new int[n];
        for (int i = 0; i < n; i++) {
            double score = 0.4 * (1 - x[i][0]) + 0.3 * x[i][1] + 0.3 * x[i][2];
            y[i] = score >= 0.55 ? 1 : 0;
        }
        return y;
    }
}
