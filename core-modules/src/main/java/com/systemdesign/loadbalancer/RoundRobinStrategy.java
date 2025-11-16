package com.systemdesign.loadbalancer;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round Robin load balancing strategy.
 * 
 * Algorithm:
 * - Distributes requests sequentially across all servers
 * - Each server gets equal number of requests
 * - Simple and effective for homogeneous servers
 * 
 * Pros:
 * - Simple to implement
 * - Fair distribution
 * - No server state needed
 * 
 * Cons:
 * - Doesn't consider server load or capacity
 * - All servers treated equally regardless of specs
 * 
 * Use cases:
 * - Stateless applications
 * - Homogeneous server pools
 * - Simple load distribution
 */
@Slf4j
public class RoundRobinStrategy implements LoadBalancingStrategy {
    
    private final AtomicInteger currentIndex = new AtomicInteger(0);
    
    @Override
    public Optional<Server> selectServer(List<Server> servers, String clientId) {
        List<Server> healthyServers = servers.stream()
                .filter(Server::isHealthy)
                .toList();
        
        if (healthyServers.isEmpty()) {
            log.warn("No healthy servers available");
            return Optional.empty();
        }
        
        // Get next server in round-robin fashion
        int index = currentIndex.getAndIncrement() % healthyServers.size();
        Server selected = healthyServers.get(index);
        
        log.debug("Selected server {} using Round Robin", selected.getId());
        return Optional.of(selected);
    }
    
    @Override
    public String getName() {
        return "Round Robin";
    }
}

