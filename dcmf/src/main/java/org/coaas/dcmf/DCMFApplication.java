package org.coaas.dcmf;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DCMF — Dynamic Context Monitoring Framework.
 *
 * Refinement-stage engine of ACOCA-P (Chapter 7 of the thesis).
 * DCMF fuses CF and PoA evidence via Dempster-Shafer Theory so caching
 * decisions account for the uncertainty in each source rather than
 * collapsing both into a single weighted score.
 *
 * Spring Boot provides the REST management surface; the gRPC server
 * (started in {@link GrpcServerRunner}) provides the per-request
 * DCMF-decision fast path used by the CoaaS Query Engine.
 */
@SpringBootApplication
public class DCMFApplication {
    public static void main(String[] args) {
        SpringApplication.run(DCMFApplication.class, args);
    }
}
