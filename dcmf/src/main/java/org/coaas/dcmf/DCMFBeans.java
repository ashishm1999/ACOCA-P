package org.coaas.dcmf;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring bean wiring for DCMF.
 */
@Configuration
public class DCMFBeans {

    @Value("${dcmf.alpha}")            private double alpha;
    @Value("${dcmf.beta}")             private double beta;
    @Value("${dcmf.beliefThreshold}")  private double beliefThreshold;

    @Value("${dcmf.lambda:0.01}")      private double lambda;
    @Value("${dcmf.kappa:1.5}")        private double kappa;

    @Bean
    public CEE cee() {
        return new CEE(alpha, beta);
    }

    @Bean
    public CMM cmm() {
        return new CMM(lambda, kappa);
    }

    @Bean
    public DecisionEngine decisionEngine(CEE cee, CMM cmm) {
        return new DecisionEngine(cee, cmm, beliefThreshold);
    }
}
