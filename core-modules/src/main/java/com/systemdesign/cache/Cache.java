package com.systemdesign.cache;

import java.util.Optional;

/**
 * Generic cache interface.
 * 
 * @param <K> Key type
 * @param <V> Value type
 */
public interface Cache<K, V> {
    
    /**
     * Get a value from the cache.
     * 
     * @param key The cache key
     * @return Optional containing the value if present, empty otherwise
     */
    Optional<V> get(K key);
    
    /**
     * Put a value into the cache.
     * 
     * @param key The cache key
     * @param value The value to cache
     */
    void put(K key, V value);
    
    /**
     * Remove a value from the cache.
     * 
     * @param key The cache key
     * @return true if the key was present and removed
     */
    boolean remove(K key);
    
    /**
     * Clear all entries from the cache.
     */
    void clear();
    
    /**
     * Get the current size of the cache.
     * 
     * @return Number of entries in the cache
     */
    int size();
    
    /**
     * Get cache statistics.
     * 
     * @return CacheStats object with hit/miss information
     */
    CacheStats getStats();
}

