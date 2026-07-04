package org.coaas.vacf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * VACF — Volatility-Aware Context Caching Framework.
 *
 * ACOCA-P replacement + eviction engine (Chapter 8 of the thesis).
 * Introduces the Context Volatility Index (CVI) and coordinates
 * retention decisions through Dempster-Shafer belief fusion (VACF-DSH)
 * or Natural Gradient Boosting (VACF-NGB).
 */
@SpringBootApplication
public class VACFApplication {
    public static void main(String[] args) {
        SpringApplication.run(VACFApplication.class, args);
    }
}
