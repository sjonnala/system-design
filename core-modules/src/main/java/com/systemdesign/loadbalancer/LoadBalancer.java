package com.systemdesign.loadbalancer;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Load Balancer implementation supporting multiple strategies.
 * 
 * Features:
 * - Multiple load balancing algorithms
 * - Health checking
 * - Connection tracking
 * - Dynamic server pool management
 */
@Slf4j
public class LoadBalancer {
    
    private final List<Server> servers;
    private final LoadBalancingStrategy strategy;
    
    public LoadBalancer(LoadBalancingStrategy strategy) {
        this.servers = new CopyOnWriteArrayList<>();
        this.strategy = strategy;
        log.info("Load balancer initialized with strategy: {}", strategy.getName());
    }
    
    /**
     * Add a server to the pool.
     */
    public void addServer(Server server) {
        servers.add(server);
        log.info("Added server: {}", server);
    }
    
    /**
     * Remove a server from the pool.
     */
    public void removeServer(String serverId) {
        servers.removeIf(s -> s.getId().equals(serverId));
        log.info("Removed server: {}", serverId);
    }
    
    /**
     * Mark a server as healthy or unhealthy.
     */
    public void setServerHealth(String serverId, boolean healthy) {
        servers.stream()
                .filter(s -> s.getId().equals(serverId))
                .findFirst()
                .ifPresent(server -> {
                    server.setHealthy(healthy);
                    log.info("Server {} marked as {}", serverId, healthy ? "healthy" : "unhealthy");
                });
    }
    
    /**
     * Route a request to a server.
     * 
     * @param clientId Optional client identifier for sticky sessions
     * @return Selected server, or empty if none available
     */
    public Optional<Server> routeRequest(String clientId) {
        Optional<Server> selected = strategy.selectServer(new ArrayList<>(servers), clientId);
        
        selected.ifPresent(server -> {
            server.incrementConnections();
            log.info("Routed request to server: {} (connections: {})", 
                    server.getId(), server.getActiveConnections());
        });
        
        return selected;
    }
    
    /**
     * Release a connection from a server.
     */
    public void releaseConnection(String serverId) {
        servers.stream()
                .filter(s -> s.getId().equals(serverId))
                .findFirst()
                .ifPresent(server -> {
                    server.decrementConnections();
                    log.debug("Released connection from server: {} (connections: {})", 
                            serverId, server.getActiveConnections());
                });
    }
    
    /**
     * Get all servers in the pool.
     */
    public List<Server> getServers() {
        return new ArrayList<>(servers);
    }
    
    /**
     * Get count of healthy servers.
     */
    public long getHealthyServerCount() {
        return servers.stream().filter(Server::isHealthy).count();
    }
    
    /**
     * Get total active connections across all servers.
     */
    public int getTotalActiveConnections() {
        return servers.stream()
                .mapToInt(Server::getActiveConnections)
                .sum();
    }
}

