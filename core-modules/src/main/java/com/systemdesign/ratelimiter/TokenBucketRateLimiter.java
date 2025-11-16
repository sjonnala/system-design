package com.systemdesign.ratelimiter;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Token Bucket Rate Limiter implementation.
 * 
 * Algorithm:
 * - Tokens are added to a bucket at a fixed rate
 * - Each request consumes one token
 * - If no tokens are available, the request is rejected
 * - Bucket has a maximum capacity
 * 
 * Use cases: API rate limiting, request throttling
 */
@Slf4j
public class TokenBucketRateLimiter implements RateLimiter {
    
    private final int capacity;
    private final int refillRate; // tokens per second
    private final ConcurrentHashMap<String, Bucket> buckets;
    
    public TokenBucketRateLimiter(int capacity, int refillRate) {
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.buckets = new ConcurrentHashMap<>();
    }
    
    @Override
    public boolean allowRequest(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity));
        return bucket.tryConsume();
    }
    
    @Override
    public int getAvailablePermits(String key) {
        Bucket bucket = buckets.get(key);
        return bucket == null ? capacity : (int) bucket.getAvailableTokens();
    }
    
    @Override
    public void reset(String key) {
        buckets.remove(key);
    }
    
    private class Bucket {
        private final AtomicLong tokens;
        private final AtomicLong lastRefillTime;
        
        Bucket(int initialTokens) {
            this.tokens = new AtomicLong(initialTokens);
            this.lastRefillTime = new AtomicLong(System.nanoTime());
        }
        
        boolean tryConsume() {
            refill();
            
            long currentTokens;
            do {
                currentTokens = tokens.get();
                if (currentTokens <= 0) {
                    log.debug("Rate limit exceeded - no tokens available");
                    return false;
                }
            } while (!tokens.compareAndSet(currentTokens, currentTokens - 1));
            
            return true;
        }
        
        void refill() {
            long now = System.nanoTime();
            long lastRefill = lastRefillTime.get();
            long elapsedNanos = now - lastRefill;
            
            // Calculate tokens to add based on elapsed time
            long tokensToAdd = (elapsedNanos * refillRate) / 1_000_000_000L;
            
            if (tokensToAdd > 0 && lastRefillTime.compareAndSet(lastRefill, now)) {
                long currentTokens;
                long newTokens;
                do {
                    currentTokens = tokens.get();
                    newTokens = Math.min(capacity, currentTokens + tokensToAdd);
                } while (!tokens.compareAndSet(currentTokens, newTokens));
                
                log.trace("Refilled {} tokens, current: {}", tokensToAdd, newTokens);
            }
        }
        
        long getAvailableTokens() {
            refill();
            return tokens.get();
        }
    }
}

