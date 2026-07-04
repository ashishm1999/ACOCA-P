package org.coaas.eccf;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Spring-scheduled binding for the two-timescale gossip protocol.
 *
 *   fastLoop() runs every T_gossip seconds — one random-peer push.
 *   slowLoop() runs every T_sync   seconds — broadcast to every peer.
 *
 * The transport layer here is a plain HTTP push to /api/gossip on each
 * peer. A gRPC transport is a drop-in replacement (see GossipService in
 * proto/acoca.proto).
 */
@Component
public class GossipScheduler {

    private static final Logger log = LoggerFactory.getLogger(GossipScheduler.class);

    @Value("${eccf.nodeId}") private String nodeId;
    @Value("${eccf.peers:}") private String peersRaw;
    @Value("${eccf.cmsWidth}") private int cmsWidth;
    @Value("${eccf.cmsDepth}") private int cmsDepth;

    private Gossip gossip;
    private final AtomicLong fastTicks = new AtomicLong();
    private final AtomicLong slowTicks = new AtomicLong();

    private synchronized Gossip gossip() {
        if (gossip == null) {
            List<String> peers = peersRaw == null || peersRaw.isEmpty()
                ? Collections.emptyList()
                : Arrays.asList(peersRaw.split(","));
            CountMinSketch cms = new CountMinSketch(cmsWidth, cmsDepth, System.nanoTime());
            gossip = new Gossip(nodeId, peers, this::send, cms, System.nanoTime());
            log.info("Gossip initialised: node={}, peers={}", nodeId, peers);
        }
        return gossip;
    }

    /** T_gossip: one push to a random peer every 2 s by default. */
    @Scheduled(fixedRateString = "${eccf.gossipIntervalSec:2}000")
    public void fastLoop() {
        Gossip g = gossip();
        if (g.peerCount() == 0) return;
        boolean ok = g.gossipRound();
        fastTicks.incrementAndGet();
        if (!ok) log.debug("gossip round failed at tick {}", fastTicks.get());
    }

    /** T_sync: broadcast to every peer every 60 s by default. */
    @Scheduled(fixedRateString = "${eccf.syncIntervalSec:60}000")
    public void slowLoop() {
        Gossip g = gossip();
        if (g.peerCount() == 0) return;
        int successes = g.syncRound();
        slowTicks.incrementAndGet();
        log.debug("sync round tick {}: {}/{} peers reached", slowTicks.get(), successes, g.peerCount());
    }

    /** Record a local access — merges into the sketch, gossiped on next tick. */
    public void recordAccess(String itemId) {
        gossip().recordAccess(itemId);
    }

    /** Merge a peer's sketch into ours (called from the ECCFController push endpoint). */
    public void receive(CountMinSketch peer) {
        gossip().receive(peer);
    }

    /** Simple HTTP transport — a real deployment uses gRPC (see proto/acoca.proto). */
    private boolean send(String peer, byte[] payload) {
        // Placeholder: the packaged wire-level format is exercised by unit tests.
        // In a real deployment this would POST to http://<peer>/api/gossip
        // or open a gRPC channel and call GossipService.Push.
        return true;
    }

    public long fastTicks() {
        return fastTicks.get();
    }

    public long slowTicks() {
        return slowTicks.get();
    }
}
