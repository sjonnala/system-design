package com.systemdesign.ratelimiter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TokenBucketRateLimiterTest {
    
    private TokenBucketRateLimiter rateLimiter;
    
    @BeforeEach
    void setUp() {
        // 10 tokens capacity, refill 5 tokens per second
        rateLimiter = new TokenBucketRateLimiter(10, 5);
    }
    
    @Test
    void testAllowRequestWithinLimit() {
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user1"));
    }
    
    @Test
    void testExceedRateLimit() {
        String key = "user2";
        
        // Consume all tokens
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.allowRequest(key), "Request " + i + " should be allowed");
        }
        
        // Next request should be denied
        assertFalse(rateLimiter.allowRequest(key), "Request should be denied after limit");
    }
    
    @Test
    void testDifferentKeysIndependent() {
        assertTrue(rateLimiter.allowRequest("user1"));
        assertTrue(rateLimiter.allowRequest("user2"));
        assertTrue(rateLimiter.allowRequest("user3"));
        
        assertEquals(9, rateLimiter.getAvailablePermits("user1"));
        assertEquals(9, rateLimiter.getAvailablePermits("user2"));
        assertEquals(9, rateLimiter.getAvailablePermits("user3"));
    }
    
    @Test
    void testReset() {
        String key = "user4";
        
        // Consume some tokens
        rateLimiter.allowRequest(key);
        rateLimiter.allowRequest(key);
        rateLimiter.allowRequest(key);
        
        assertEquals(7, rateLimiter.getAvailablePermits(key));
        
        // Reset
        rateLimiter.reset(key);
        
        // Should have full capacity again
        assertEquals(10, rateLimiter.getAvailablePermits(key));
    }
    
    @Test
    void testTokenRefill() throws InterruptedException {
        String key = "user5";
        
        // Consume all tokens
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowRequest(key);
        }
        
        assertEquals(0, rateLimiter.getAvailablePermits(key));
        
        // Wait for refill (1 second should add 5 tokens)
        Thread.sleep(1100);
        
        // Should have some tokens available
        assertTrue(rateLimiter.getAvailablePermits(key) > 0);
    }
}

