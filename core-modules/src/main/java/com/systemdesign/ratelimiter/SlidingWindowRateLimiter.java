package com.systemdesign.ratelimiter;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 * Sliding Window Log Rate Limiter implementation.
 * 
 * Algorithm:
 * - Maintains a log of request timestamps
 * - When a request arrives, removes timestamps older than the window
 * - Allows request if log size is less than the limit
 * 
 * Advantages: Precise rate limiting
 * Disadvantages: Memory intensive for high traffic
 * 
 * Use cases: Precise API rate limiting, user action throttling
 */
@Slf4j
public class SlidingWindowRateLimiter implements RateLimiter {
    
    private final int maxRequests;
    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> requestLogs;
    Logger logger = Logger.getLogger(SlidingWindowRateLimiter.class.getName());
    
    public SlidingWindowRateLimiter(int maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.requestLogs = new ConcurrentHashMap<>();
    }
    
    @Override
    public boolean allowRequest(String key) {
        long now = System.currentTimeMillis();
        ConcurrentLinkedQueue<Long> log = requestLogs.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        
        // Remove expired timestamps
        long cutoffTime = now - windowSizeMillis;
        while (!log.isEmpty() && log.peek() <= cutoffTime) {
            log.poll();
        }
        
        // Check if under the limit
        if (log.size() < maxRequests) {
            log.offer(now);
            logger.info(String.format("Request allowed for key: %s, count: %d/%d", key, log.size(), maxRequests));
            return true;
        }

        logger.info(String.format("Rate limit exceeded for key: {}, count: {}/{}", key, log.size(), maxRequests));
        return false;
    }
    
    @Override
    public int getAvailablePermits(String key) {
        ConcurrentLinkedQueue<Long> log = requestLogs.get(key);
        if (log == null) {
            return maxRequests;
        }
        
        // Clean up old entries
        long now = System.currentTimeMillis();
        long cutoffTime = now - windowSizeMillis;
        while (!log.isEmpty() && log.peek() <= cutoffTime) {
            log.poll();
        }
        
        return Math.max(0, maxRequests - log.size());
    }
    
    @Override
    public void reset(String key) {
        requestLogs.remove(key);
    }
}

