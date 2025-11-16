package com.systemdesign.consistent.hashing;

//import com.systemdesign.consistent.hashing.ConsistentHashRing;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsistentHashRingTest {
    
    private ConsistentHashRing<String> hashRing;
    
    @BeforeEach
    void setUp() {
        hashRing = new ConsistentHashRing<>(150); // 150 virtual nodes per physical node
    }
    
    @Test
    void testAddAndGetNode() {
        hashRing.addNode("server1");
        hashRing.addNode("server2");
        hashRing.addNode("server3");
        
        assertEquals(3, hashRing.getNodeCount());
        assertEquals(450, hashRing.getRingSize()); // 3 nodes * 150 virtual nodes
        
        String node = hashRing.getNode("user123");
        assertNotNull(node);
        assertTrue(hashRing.getAllNodes().contains(node));
    }
    
    @Test
    void testConsistentMapping() {
        hashRing.addNode("server1");
        hashRing.addNode("server2");
        hashRing.addNode("server3");
        
        // Same key should always map to same node
        String node1 = hashRing.getNode("user123");
        String node2 = hashRing.getNode("user123");
        String node3 = hashRing.getNode("user123");
        
        assertEquals(node1, node2);
        assertEquals(node2, node3);
    }
    
    @Test
    void testMinimalRemapping() {
        hashRing.addNode("server1");
        hashRing.addNode("server2");
        hashRing.addNode("server3");
        
        // Generate test keys and their mappings
        List<String> testKeys = IntStream.range(0, 1000)
                .mapToObj(i -> "key" + i)
                .collect(Collectors.toList());
        
        Map<String, String> originalMapping = new HashMap<>();
        for (String key : testKeys) {
            originalMapping.put(key, hashRing.getNode(key));
        }
        
        // Remove one node
        hashRing.removeNode("server2");
        
        // Count how many keys got remapped
        int remapped = 0;
        for (String key : testKeys) {
            String newNode = hashRing.getNode(key);
            if (!newNode.equals(originalMapping.get(key))) {
                remapped++;
            }
        }
        
        // With consistent hashing, only ~1/3 of keys should be remapped
        double remapPercentage = (remapped * 100.0) / testKeys.size();
        System.out.println("Remapped: " + remapPercentage + "%");
        
        // Should be approximately 33% (since we had 3 nodes and removed 1)
        assertTrue(remapPercentage > 25 && remapPercentage < 45, 
                "Remap percentage should be around 33%, but was " + remapPercentage);
    }
    
    @Test
    void testLoadDistribution() {
        hashRing.addNode("server1");
        hashRing.addNode("server2");
        hashRing.addNode("server3");
        
        // Generate test keys
        List<String> testKeys = IntStream.range(0, 10000)
                .mapToObj(i -> "key" + i)
                .collect(Collectors.toList());
        
        Map<String, Integer> stats = hashRing.getDistributionStats(testKeys);
        
        System.out.println("Distribution stats:");
        stats.forEach((node, count) -> 
                System.out.println(node + ": " + count + " keys (" + (count * 100.0 / testKeys.size()) + "%)"));
        
        // Each node should get roughly 33% of keys (allowing 10% variance)
        int expectedPerNode = testKeys.size() / 3;
        for (Map.Entry<String, Integer> entry : stats.entrySet()) {
            int count = entry.getValue();
            double variance = Math.abs(count - expectedPerNode) * 100.0 / expectedPerNode;
            assertTrue(variance < 20, 
                    "Node " + entry.getKey() + " has " + count + " keys, expected around " + expectedPerNode);
        }
    }
    
    @Test
    void testEmptyRing() {
        assertNull(hashRing.getNode("anykey"));
        assertEquals(0, hashRing.getNodeCount());
    }
    
    @Test
    void testSingleNode() {
        hashRing.addNode("server1");
        
        // All keys should map to the only node
        assertEquals("server1", hashRing.getNode("key1"));
        assertEquals("server1", hashRing.getNode("key2"));
        assertEquals("server1", hashRing.getNode("key3"));
    }
}

