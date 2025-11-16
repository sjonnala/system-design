package com.systemdesign.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Cache statistics tracking.
 */
@Getter
@AllArgsConstructor
public class CacheStats {
    private final long hits;
    private final long misses;
    private final long evictions;
    
    public long getTotalRequests() {
        return hits + misses;
    }
    
    public double getHitRate() {
        long total = getTotalRequests();
        return total == 0 ? 0.0 : (double) hits / total;
    }
    
    public double getMissRate() {
        return 1.0 - getHitRate();
    }
    
    @Override
    public String toString() {
        return String.format("CacheStats{hits=%d, misses=%d, evictions=%d, hitRate=%.2f%%}", 
                hits, misses, evictions, getHitRate() * 100);
    }
}

