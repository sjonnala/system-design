package com.systemdesign.loadbalancer;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Optional;

/**
 * IP Hash load balancing strategy.
 * 
 * Algorithm:
 * - Hashes client IP address to select server
 * - Same client always routes to same server (sticky sessions)
 * - Useful for session persistence
 * 
 * Pros:
 * - Session affinity without shared storage
 * - Predictable routing
 * - No session replication needed
 * 
 * Cons:
 * - Uneven distribution if clients behind NAT
 * - Server failure breaks sessions
 * - Not dynamic
 * 
 * Use cases:
 * - Stateful applications
 * - Session-based authentication
 * - Shopping carts
 */
@Slf4j
public class IpHashStrategy implements LoadBalancingStrategy {
    
    private final MessageDigest md5;
    
    public IpHashStrategy() {
        try {
            this.md5 = MessageDigest.getInstance("MD5");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 not available", e);
        }
    }
    
    @Override
    public Optional<Server> selectServer(List<Server> servers, String clientId) {
        List<Server> healthyServers = servers.stream()
                .filter(Server::isHealthy)
                .toList();
        
        if (healthyServers.isEmpty()) {
            log.warn("No healthy servers available");
            return Optional.empty();
        }
        
        if (clientId == null || clientId.isEmpty()) {
            // Fallback to first server if no client ID
            return Optional.of(healthyServers.get(0));
        }
        
        // Hash client ID to determine server
        int hash = Math.abs(hashClientId(clientId));
        int index = hash % healthyServers.size();
        Server selected = healthyServers.get(index);
        
        log.debug("Selected server {} for client {} using IP Hash", selected.getId(), clientId);
        return Optional.of(selected);
    }
    
    private int hashClientId(String clientId) {
        md5.reset();
        byte[] digest = md5.digest(clientId.getBytes(StandardCharsets.UTF_8));
        
        // Convert first 4 bytes to int
        int hash = 0;
        for (int i = 0; i < 4 && i < digest.length; i++) {
            hash = (hash << 8) | (digest[i] & 0xFF);
        }
        
        return hash;
    }
    
    @Override
    public String getName() {
        return "IP Hash";
    }
}

