package org.coaas.vacf;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Push generated VACF policies to the registered edge nodes.
 *
 * The real deployment uses a gRPC streaming RPC (see PolicyService in the
 * shared proto file) — this class isolates that concern so the rest of
 * VACF can push through a plain method call and let the transport layer
 * batch and stream it.
 */
public final class PolicyPusher {

    /** Transport hook that ships one policy to one edge node. */
    @FunctionalInterface
    public interface Transport {
        boolean push(String nodeId, PolicyGenerator.Policy policy);
    }

    private final Transport transport;
    private final List<String> edgeNodes = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Long> lastPushMs = new ConcurrentHashMap<>();

    public PolicyPusher(Transport transport) {
        this.transport = transport;
    }

    public void registerEdge(String nodeId) {
        if (!edgeNodes.contains(nodeId)) edgeNodes.add(nodeId);
    }

    public void deregisterEdge(String nodeId) {
        edgeNodes.remove(nodeId);
        lastPushMs.remove(nodeId);
    }

    public List<String> registeredEdges() {
        return Collections.unmodifiableList(edgeNodes);
    }

    /**
     * Broadcast one policy to every registered edge. Returns the number
     * of successful pushes.
     */
    public int broadcast(PolicyGenerator.Policy policy) {
        int successes = 0;
        long now = System.currentTimeMillis();
        for (String edge : edgeNodes) {
            if (transport.push(edge, policy)) {
                lastPushMs.put(edge, now);
                successes++;
            }
        }
        return successes;
    }

    public Long lastPushed(String nodeId) {
        return lastPushMs.get(nodeId);
    }
}
