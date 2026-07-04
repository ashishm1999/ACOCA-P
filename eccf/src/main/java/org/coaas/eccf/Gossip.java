package org.coaas.eccf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Two-timescale gossip coordinator (Chapter 10).
 *
 * Uses one loop for fast peer exchange (T_gossip = 2 s by default) and a
 * slower loop for core synchronisation (T_sync = 60 s by default). Each
 * round samples a peer uniformly at random and pushes the local
 * Count-Min Sketch summary of hot items. Coordination overhead stays
 * under ~24 KB/s per node and scales logarithmically in the number of
 * nodes.
 *
 * The transport layer is abstracted through a functional interface so
 * unit tests can swap in a mock without a real network.
 */
public final class Gossip {

    /** Transport hook that ships a digest to one peer. */
    @FunctionalInterface
    public interface Transport {
        boolean send(String peer, byte[] payload);
    }

    private final String nodeId;
    private final List<String> peers;
    private final Transport transport;
    private final Random rng;
    private final AtomicLong round = new AtomicLong(0);
    private CountMinSketch cms;

    public Gossip(String nodeId, List<String> peers, Transport transport,
                  CountMinSketch cms, long randomSeed) {
        this.nodeId = nodeId;
        this.peers = Collections.unmodifiableList(new ArrayList<>(peers));
        this.transport = transport;
        this.cms = cms;
        this.rng = new Random(randomSeed);
    }

    /** Record an access — replaces the item's counter in the sketch. */
    public void recordAccess(String itemId) {
        cms.add(itemId, 1L);
    }

    /** One tick of the fast gossip loop. */
    public boolean gossipRound() {
        if (peers.isEmpty()) return false;
        String peer = peers.get(rng.nextInt(peers.size()));
        byte[] payload = cms.serialise();
        boolean ok = transport.send(peer, payload);
        round.incrementAndGet();
        return ok;
    }

    /** One tick of the slow core-sync loop: broadcast to every peer. */
    public int syncRound() {
        int successes = 0;
        byte[] payload = cms.serialise();
        for (String peer : peers) {
            if (transport.send(peer, payload)) successes++;
        }
        return successes;
    }

    /** Merge an incoming peer sketch into the local one. */
    public void receive(CountMinSketch peerSketch) {
        cms.merge(peerSketch);
    }

    public long round() {
        return round.get();
    }

    public String nodeId() {
        return nodeId;
    }

    public int peerCount() {
        return peers.size();
    }
}
