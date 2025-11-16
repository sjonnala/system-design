package com.systemdesign.ratelimiter;

/**
 * Interface for rate limiting implementations.
 * Rate limiters control the rate at which operations are allowed to proceed.
 */
public interface RateLimiter {
    
    /**
     * Attempt to acquire a permit for an operation.
     * 
     * @param key The identifier for the resource being rate limited (e.g., user ID, API key)
     * @return true if the request is allowed, false if rate limit is exceeded
     */
    boolean allowRequest(String key);
    
    /**
     * Get the current number of available permits for a key.
     * 
     * @param key The identifier for the resource
     * @return Number of available permits
     */
    int getAvailablePermits(String key);
    
    /**
     * Reset the rate limiter for a specific key.
     * 
     * @param key The identifier to reset
     */
    void reset(String key);
}

