package org.coaas.vacf;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class VACFController {

    @Value("${vacf.tauMinSec}") private long tauMinSec;
    @Value("${vacf.tauMaxSec}") private long tauMaxSec;
    @Value("${vacf.alpha}")     private double alpha;
    @Value("${vacf.pNorm}")     private double pNorm;
    @Value("${vacf.replicaMin}") private int replicaMin;
    @Value("${vacf.replicaMax}") private int replicaMax;

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new HashMap<>();
        out.put("status", "ok");
        out.put("component", "VACF");
        return out;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new HashMap<>();
        out.put("tau_min_s", tauMinSec);
        out.put("tau_max_s", tauMaxSec);
        out.put("alpha", alpha);
        out.put("p_norm", pNorm);
        out.put("replica_min", replicaMin);
        out.put("replica_max", replicaMax);
        return out;
    }

    @PostMapping("/policy")
    public Map<String, Object> generatePolicy(@RequestBody PolicyRequest req) {
        PolicyGenerator gen = new PolicyGenerator(
            tauMinSec * 1000, tauMaxSec * 1000, alpha, replicaMin, replicaMax);
        PolicyGenerator.Policy p = gen.generate(req.cvi, req.cf, req.poa, req.slaMs);
        VACFDSH.Verdict v = VACFDSH.decide(req.cvi, req.cf, req.poa);
        Map<String, Object> out = new HashMap<>();
        out.put("item_id", req.itemId);
        out.put("ttl_ms", p.ttlMs);
        out.put("replicas", p.replicas);
        out.put("retain", v.retain);
        out.put("retain_belief", v.belief);
        return out;
    }

    @PostMapping("/cvi")
    public Map<String, Object> computeCVI(@RequestBody CVIRequest req) {
        double cvi = CVI.compute(req.observations);
        Map<String, Object> out = new HashMap<>();
        out.put("item_id", req.itemId);
        out.put("cvi", cvi);
        return out;
    }

    public static class PolicyRequest {
        public String itemId;
        public double cvi;
        public double cf;
        public double poa;
        public long slaMs = 250;
    }

    public static class CVIRequest {
        public String itemId;
        public double[] observations;
    }
}
