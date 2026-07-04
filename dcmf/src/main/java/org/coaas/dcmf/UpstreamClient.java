package org.coaas.dcmf;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Blocking HTTP client that fetches PoA from CAPME and CF from CFMS.
 *
 * DCMF is on the per-request path so the client is intentionally
 * lightweight and uses connection pooling. A gRPC replacement is a
 * two-line drop-in whenever CAPME and CFMS expose their gRPC surface.
 */
@Component
public class UpstreamClient {

    @Value("${dcmf.upstream.capme}") private String capmeBase;
    @Value("${dcmf.upstream.cfms}")  private String cfmsBase;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(500))
            .build();

    /** Query CAPME for the current PoA of one item. */
    public double poa(String itemId, Map<String, Object> attrs) {
        Map<String, Object> body = new HashMap<>();
        body.put("items", Map.of(itemId, attrs));
        try {
            String json = toJson(body);
            HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(capmeBase + "/api/poa"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(200))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            return extractScore(resp.body(), itemId);
        } catch (Exception e) {
            // On failure the CoaaS deployment should fall back to the historical PoA.
            return Double.NaN;
        }
    }

    /** Query CFMS for the current CF of one cached item. */
    public double cf(String itemId, Map<String, Object> body) {
        try {
            HttpResponse<String> resp = http.send(
                HttpRequest.newBuilder(URI.create(cfmsBase + "/api/freshness/" + itemId))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(200))
                    .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                    .build(),
                HttpResponse.BodyHandlers.ofString()
            );
            return extractField(resp.body(), "cf");
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    // Minimal JSON serialisation to avoid dragging in a full dependency.
    private static String toJson(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(StringBuilder sb, Object o) {
        if (o == null) { sb.append("null"); return; }
        if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                sb.append('"').append(escape(e.getKey())).append("\":");
                write(sb, e.getValue());
            }
            sb.append('}');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else {
            sb.append('"').append(escape(o.toString())).append('"');
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static double extractField(String json, String field) {
        int i = json.indexOf("\"" + field + "\"");
        if (i < 0) return Double.NaN;
        int colon = json.indexOf(':', i);
        int comma = json.indexOf(',', colon);
        int brace = json.indexOf('}', colon);
        int end = comma < 0 ? brace : Math.min(comma, brace);
        try {
            return Double.parseDouble(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static double extractScore(String json, String itemId) {
        int i = json.indexOf("\"" + itemId + "\"");
        if (i < 0) return Double.NaN;
        int colon = json.indexOf(':', i);
        int comma = json.indexOf(',', colon);
        int brace = json.indexOf('}', colon);
        int end = comma < 0 ? brace : Math.min(comma, brace);
        try {
            return Double.parseDouble(json.substring(colon + 1, end).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
