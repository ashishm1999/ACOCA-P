package org.coaas.dcmf;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST surface for DCMF.
 *
 * The high-throughput per-request path uses gRPC (see
 * {@link CacheServiceImpl}); this controller is for administrative
 * introspection and integration tests.
 */
@RestController
@RequestMapping("/api")
public class DCMFController {

    private final DecisionEngine engine;

    @Value("${dcmf.alpha}") private double alpha;
    @Value("${dcmf.beta}")  private double beta;
    @Value("${dcmf.beliefThreshold}") private double beliefThreshold;

    public DCMFController(DecisionEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new HashMap<>();
        out.put("status", "ok");
        out.put("component", "DCMF");
        return out;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new HashMap<>();
        out.put("alpha", alpha);
        out.put("beta", beta);
        out.put("belief_threshold", beliefThreshold);
        return out;
    }

    /** JSON POST for testing the DCMF decision without a gRPC client. */
    @PostMapping("/decide")
    public Map<String, Object> decide(@RequestBody Evidence body) {
        DecisionEngine.Outcome out = engine.decide(
            body.itemId,
            body.cf,
            body.poa,
            body.quality
        );
        Map<String, Object> resp = new HashMap<>();
        resp.put("item_id", body.itemId);
        resp.put("cache", out.cache);
        resp.put("belief_cache", out.beliefCache);
        resp.put("belief_evict", out.beliefEvict);
        resp.put("conflict", out.conflict);
        resp.put("betP", out.betP);
        return resp;
    }

    public static class Evidence {
        public String itemId;
        public double cf;
        public double poa;
        public double quality = 1.0;
    }
}
