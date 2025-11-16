package com.systemdesign.loadbalancer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoadBalancerTest {
    
    private LoadBalancer lb;
    private Server server1;
    private Server server2;
    private Server server3;
    
    @BeforeEach
    void setUp() {
        server1 = new Server("server1", "localhost", 8001);
        server2 = new Server("server2", "localhost", 8002);
        server3 = new Server("server3", "localhost", 8003);
    }
    
    @Test
    void testRoundRobinStrategy() {
        lb = new LoadBalancer(new RoundRobinStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        lb.addServer(server3);
        
        // Should distribute evenly
        Optional<Server> s1 = lb.routeRequest(null);
        Optional<Server> s2 = lb.routeRequest(null);
        Optional<Server> s3 = lb.routeRequest(null);
        Optional<Server> s4 = lb.routeRequest(null); // Should wrap around
        
        assertTrue(s1.isPresent());
        assertTrue(s2.isPresent());
        assertTrue(s3.isPresent());
        assertTrue(s4.isPresent());
        
        // Verify different servers selected
        assertNotEquals(s1.get().getId(), s2.get().getId());
        assertNotEquals(s2.get().getId(), s3.get().getId());
        
        // Fourth request should match first (round robin)
        assertEquals(s1.get().getId(), s4.get().getId());
    }
    
    @Test
    void testLeastConnectionsStrategy() {
        lb = new LoadBalancer(new LeastConnectionsStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        lb.addServer(server3);
        
        // All servers at 0 connections - any can be selected
        Optional<Server> first = lb.routeRequest(null);
        assertTrue(first.isPresent());
        
        // Now one server has 1 connection, others have 0
        // Next request should go to a server with 0 connections
        Optional<Server> second = lb.routeRequest(null);
        assertTrue(second.isPresent());
        
        // Release first connection
        lb.releaseConnection(first.get().getId());
        
        // All should be at equal connections again
        assertEquals(1, lb.getTotalActiveConnections());
    }
    
    @Test
    void testIpHashStrategy() {
        lb = new LoadBalancer(new IpHashStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        lb.addServer(server3);
        
        // Same client should always get same server
        String clientId = "192.168.1.100";
        
        Optional<Server> s1 = lb.routeRequest(clientId);
        Optional<Server> s2 = lb.routeRequest(clientId);
        Optional<Server> s3 = lb.routeRequest(clientId);
        
        assertTrue(s1.isPresent());
        assertTrue(s2.isPresent());
        assertTrue(s3.isPresent());
        
        assertEquals(s1.get().getId(), s2.get().getId());
        assertEquals(s2.get().getId(), s3.get().getId());
        
        // Different client might get different server
        String clientId2 = "192.168.1.101";
        Optional<Server> s4 = lb.routeRequest(clientId2);
        assertTrue(s4.isPresent());
    }
    
    @Test
    void testHealthChecking() {
        lb = new LoadBalancer(new RoundRobinStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        lb.addServer(server3);
        
        assertEquals(3, lb.getHealthyServerCount());
        
        // Mark server2 as unhealthy
        lb.setServerHealth("server2", false);
        
        assertEquals(2, lb.getHealthyServerCount());
        
        // Requests should only go to healthy servers
        for (int i = 0; i < 10; i++) {
            Optional<Server> server = lb.routeRequest(null);
            assertTrue(server.isPresent());
            assertNotEquals("server2", server.get().getId());
        }
    }
    
    @Test
    void testNoHealthyServers() {
        lb = new LoadBalancer(new RoundRobinStrategy());
        lb.addServer(server1);
        
        lb.setServerHealth("server1", false);
        
        Optional<Server> server = lb.routeRequest(null);
        assertFalse(server.isPresent());
    }
    
    @Test
    void testAddRemoveServers() {
        lb = new LoadBalancer(new RoundRobinStrategy());
        
        assertEquals(0, lb.getServers().size());
        
        lb.addServer(server1);
        assertEquals(1, lb.getServers().size());
        
        lb.addServer(server2);
        assertEquals(2, lb.getServers().size());
        
        lb.removeServer("server1");
        assertEquals(1, lb.getServers().size());
    }
    
    @Test
    void testConnectionTracking() {
        lb = new LoadBalancer(new LeastConnectionsStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        
        assertEquals(0, lb.getTotalActiveConnections());
        
        Optional<Server> s1 = lb.routeRequest(null);
        assertEquals(1, lb.getTotalActiveConnections());
        
        Optional<Server> s2 = lb.routeRequest(null);
        assertEquals(2, lb.getTotalActiveConnections());
        
        lb.releaseConnection(s1.get().getId());
        assertEquals(1, lb.getTotalActiveConnections());
        
        lb.releaseConnection(s2.get().getId());
        assertEquals(0, lb.getTotalActiveConnections());
    }
    
    @Test
    void testLoadDistribution() {
        lb = new LoadBalancer(new RoundRobinStrategy());
        lb.addServer(server1);
        lb.addServer(server2);
        lb.addServer(server3);
        
        Map<String, Integer> distribution = new HashMap<>();
        
        // Send 300 requests
        for (int i = 0; i < 300; i++) {
            Optional<Server> server = lb.routeRequest(null);
            server.ifPresent(s -> 
                distribution.merge(s.getId(), 1, Integer::sum));
            server.ifPresent(s -> lb.releaseConnection(s.getId()));
        }
        
        // Each server should get exactly 100 requests (round robin)
        assertEquals(3, distribution.size());
        distribution.values().forEach(count -> 
            assertEquals(100, count, "Each server should get equal requests"));
    }
}

