package com.systemdesign.loadbalancer;

import lombok.extern.slf4j.Slf4j;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Least Connections load balancing strategy.
 * 
 * Algorithm:
 * - Routes requests to the server with fewest active connections
 * - Considers current server load
 * - Dynamic load distribution
 * 
 * Pros:
 * - Considers actual server load
 * - Better for long-lived connections
 * - Adapts to server capacity
 * 
 * Cons:
 * - Requires tracking connection counts
 * - More complex than round robin
 * 
 * Use cases:
 * - WebSocket connections
 * - Database connection pools
 * - Long-running requests
 */
@Slf4j
public class LeastConnectionsStrategy implements LoadBalancingStrategy {
    
    @Override
    public Optional<Server> selectServer(List<Server> servers, String clientId) {
        Optional<Server> selected = servers.stream()
                .filter(Server::isHealthy)
                .min(Comparator.comparingInt(Server::getActiveConnections));
        
        selected.ifPresent(server -> 
                log.debug("Selected server {} with {} active connections", 
                        server.getId(), server.getActiveConnections()));
        
        return selected;
    }
    
    @Override
    public String getName() {
        return "Least Connections";
    }
}

