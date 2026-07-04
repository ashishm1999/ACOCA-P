package org.coaas.eccf;

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
public class ECCFController {

    @Value("${eccf.nodeId}")             private String nodeId;
    @Value("${eccf.stabilityAlpha}")     private double stabilityAlpha;
    @Value("${eccf.rateBeta}")           private double rateBeta;
    @Value("${eccf.cmsWidth}")           private int cmsWidth;
    @Value("${eccf.cmsDepth}")           private int cmsDepth;

    private CVAE cvae;
    private LCVF lcvf;

    public ECCFController() {
        this.lcvf = new LCVF();
    }

    private CVAE cvae() {
        if (cvae == null) cvae = new CVAE(stabilityAlpha, rateBeta);
        return cvae;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> out = new HashMap<>();
        out.put("status", "ok");
        out.put("component", "ECCF");
        out.put("node_id", nodeId);
        return out;
    }

    @PostMapping("/observe")
    public Map<String, Object> observe(@RequestBody ObservationRequest req) {
        double cv = cvae().observe(req.itemId, req.value, System.currentTimeMillis());
        Map<String, Object> out = new HashMap<>();
        out.put("item_id", req.itemId);
        out.put("cv", cv);
        return out;
    }

    @PostMapping("/lcvf")
    public Map<String, Object> lcvf(@RequestBody LCVFRequest req) {
        LCVF.Factors f = new LCVF.Factors(req.poa, req.cf, req.cv, req.hopCost, req.missCost, req.providerReputation);
        double u = lcvf.utility(f);
        Map<String, Object> out = new HashMap<>();
        out.put("item_id", req.itemId);
        out.put("utility", u);
        out.put("eviction_score", 1.0 - u);
        return out;
    }

    @GetMapping("/config")
    public Map<String, Object> config() {
        Map<String, Object> out = new HashMap<>();
        out.put("node_id", nodeId);
        out.put("stability_alpha", stabilityAlpha);
        out.put("rate_beta", rateBeta);
        out.put("cms_width", cmsWidth);
        out.put("cms_depth", cmsDepth);
        return out;
    }

    public static class ObservationRequest {
        public String itemId;
        public double value;
    }

    public static class LCVFRequest {
        public String itemId;
        public double poa;
        public double cf;
        public double cv;
        public double hopCost;
        public double missCost;
        public double providerReputation;
    }
}
