package com.systemdesign.loadbalancer;

import java.util.List;
import java.util.Optional;

/**
 * Load balancing strategy interface.
 */
public interface LoadBalancingStrategy {
    
    /**
     * Select the next server to handle a request.
     * 
     * @param servers List of available servers
     * @param clientId Optional client identifier for sticky sessions
     * @return Selected server, or empty if no healthy servers available
     */
    Optional<Server> selectServer(List<Server> servers, String clientId);
    
    /**
     * Get the name of this load balancing strategy.
     */
    String getName();
}

