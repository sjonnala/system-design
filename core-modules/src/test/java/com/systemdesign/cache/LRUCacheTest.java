package com.systemdesign.cache;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LRUCacheTest {
    
    private LRUCache<String, String> cache;
    
    @BeforeEach
    void setUp() {
        cache = new LRUCache<>(3);
    }
    
    @Test
    void testPutAndGet() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertEquals(Optional.of("value1"), cache.get("key1"));
        assertEquals(Optional.of("value2"), cache.get("key2"));
        assertEquals(2, cache.size());
    }
    
    @Test
    void testCacheMiss() {
        cache.put("key1", "value1");
        
        assertEquals(Optional.empty(), cache.get("nonexistent"));
    }
    
    @Test
    void testEviction() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // Cache is full (capacity 3)
        assertEquals(3, cache.size());
        
        // Add another item - should evict key1 (least recently used)
        cache.put("key4", "value4");
        
        assertEquals(3, cache.size());
        assertEquals(Optional.empty(), cache.get("key1")); // key1 was evicted
        assertEquals(Optional.of("value2"), cache.get("key2"));
        assertEquals(Optional.of("value3"), cache.get("key3"));
        assertEquals(Optional.of("value4"), cache.get("key4"));
    }
    
    @Test
    void testLRUOrder() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        
        // Access key1 to make it recently used
        cache.get("key1");
        
        // Add key4 - should evict key2 (now least recently used)
        cache.put("key4", "value4");
        
        assertEquals(Optional.of("value1"), cache.get("key1")); // Still present
        assertEquals(Optional.empty(), cache.get("key2")); // Evicted
        assertEquals(Optional.of("value3"), cache.get("key3"));
        assertEquals(Optional.of("value4"), cache.get("key4"));
    }
    
    @Test
    void testUpdateExisting() {
        cache.put("key1", "value1");
        cache.put("key1", "value2"); // Update
        
        assertEquals(Optional.of("value2"), cache.get("key1"));
        assertEquals(1, cache.size());
    }
    
    @Test
    void testRemove() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        assertTrue(cache.remove("key1"));
        assertEquals(1, cache.size());
        assertEquals(Optional.empty(), cache.get("key1"));
        
        assertFalse(cache.remove("nonexistent"));
    }
    
    @Test
    void testClear() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        cache.clear();
        
        assertEquals(0, cache.size());
        assertEquals(Optional.empty(), cache.get("key1"));
        assertEquals(Optional.empty(), cache.get("key2"));
    }
    
    @Test
    void testStats() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        
        cache.get("key1"); // hit
        cache.get("key1"); // hit
        cache.get("key3"); // miss
        
        CacheStats stats = cache.getStats();
        assertEquals(2, stats.getHits());
        assertEquals(1, stats.getMisses());
        assertEquals(3, stats.getTotalRequests());
        assertTrue(stats.getHitRate() > 0.6 && stats.getHitRate() < 0.7);
    }
    
    @Test
    void testEvictionCount() {
        cache.put("key1", "value1");
        cache.put("key2", "value2");
        cache.put("key3", "value3");
        cache.put("key4", "value4"); // Evicts key1
        cache.put("key5", "value5"); // Evicts key2
        
        CacheStats stats = cache.getStats();
        assertEquals(2, stats.getEvictions());
    }
}

