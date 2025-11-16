package com.systemdesign.loadbalancer;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Represents a backend server in the load balancer pool.
 */
@Data
@AllArgsConstructor
public class Server {
    private String id;
    private String host;
    private int port;
    private boolean healthy;
    private int activeConnections;
    private int weight; // For weighted algorithms
    
    public Server(String id, String host, int port) {
        this(id, host, port, true, 0, 1);
    }
    
    public void incrementConnections() {
        activeConnections++;
    }
    
    public void decrementConnections() {
        if (activeConnections > 0) {
            activeConnections--;
        }
    }
    
    @Override
    public String toString() {
        return String.format("%s (%s:%d)", id, host, port);
    }
}

