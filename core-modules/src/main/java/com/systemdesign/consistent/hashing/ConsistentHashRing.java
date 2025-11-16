package com.systemdesign.consistent.hashing;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Consistent Hashing implementation with virtual nodes.
 * 
 * Use cases:
 * - Distributed caching (memcached, Redis clusters)
 * - Load balancing
 * - Data partitioning in distributed databases
 * - CDN routing
 * 
 * Benefits:
 * - Minimal redistribution when nodes are added/removed
 * - Virtual nodes improve load distribution
 * - Scalable and fault-tolerant
 */
@Slf4j
public class ConsistentHashRing<T> {
    
    private final int virtualNodesPerNode;
    private final TreeMap<Long, T> ring;
    private final MessageDigest md5;
    
    public ConsistentHashRing(int virtualNodesPerNode) {
        this.virtualNodesPerNode = virtualNodesPerNode;
        this.ring = new TreeMap<>();
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }
    
    /**
     * Add a node to the hash ring.
     * Creates virtual nodes to improve distribution.
     */
    public void addNode(T node) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            String virtualNodeKey = node.toString() + "#" + i;
            long hash = hash(virtualNodeKey);
            ring.put(hash, node);
            log.debug("Added virtual node: {} at position {}", virtualNodeKey, hash);
        }
        log.info("Added node {} with {} virtual nodes. Total ring size: {}", 
                node, virtualNodesPerNode, ring.size());
    }
    
    /**
     * Remove a node from the hash ring.
     */
    public void removeNode(T node) {
        for (int i = 0; i < virtualNodesPerNode; i++) {
            String virtualNodeKey = node.toString() + "#" + i;
            long hash = hash(virtualNodeKey);
            ring.remove(hash);
        }
        log.info("Removed node {} and its virtual nodes. Total ring size: {}", 
                node, ring.size());
    }
    
    /**
     * Get the node responsible for a given key.
     * Returns the first node clockwise from the key's hash position.
     */
    public T getNode(String key) {
        if (ring.isEmpty()) {
            return null;
        }
        
        long hash = hash(key);
        
        // Find the first node clockwise from the hash
        Map.Entry<Long, T> entry = ring.ceilingEntry(hash);
        
        // Wrap around if we're past the last node
        if (entry == null) {
            entry = ring.firstEntry();
        }
        
        log.debug("Key '{}' (hash: {}) mapped to node: {}", key, hash, entry.getValue());
        return entry.getValue();
    }
    
    /**
     * Get all nodes in the ring (without duplicates from virtual nodes).
     */
    public Set<T> getAllNodes() {
        return new HashSet<>(ring.values());
    }
    
    /**
     * Get the number of physical nodes in the ring.
     */
    public int getNodeCount() {
        return getAllNodes().size();
    }
    
    /**
     * Get the total number of positions (including virtual nodes).
     */
    public int getRingSize() {
        return ring.size();
    }
    
    /**
     * Get distribution statistics for testing and monitoring.
     */
    public Map<T, Integer> getDistributionStats(List<String> keys) {
        Map<T, Integer> stats = new HashMap<>();
        
        for (String key : keys) {
            T node = getNode(key);
            stats.put(node, stats.getOrDefault(node, 0) + 1);
        }
        
        return stats;
    }
    
    /**
     * Hash function using MD5.
     * Returns a long value for positioning on the ring.
     */
    private long hash(String key) {
        md5.reset();
        md5.update(key.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md5.digest();
        
        // Use first 8 bytes to create a long
        long hash = 0;
        for (int i = 0; i < 8; i++) {
            hash = (hash << 8) | (digest[i] & 0xFF);
        }
        
        return hash;
    }
}
