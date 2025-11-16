package com.systemdesign.ratelimiter;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class FixedWindowRateLimiter implements RateLimiter {

    private final int maxRequests;
    private final long windowSizeMillis;
    private final ConcurrentHashMap<String, Window> windows;

    public FixedWindowRateLimiter(int maxRequests, long windowSizeMillis) {
        this.maxRequests = maxRequests;
        this.windowSizeMillis = windowSizeMillis;
        this.windows = new ConcurrentHashMap<>();
    }

    @Override
    public boolean allowRequest(String key) {
        long now = System.currentTimeMillis();
        Window window = windows.computeIfAbsent(key, k -> new Window(now, new AtomicInteger(0)));

        if (now - window.startTime >= windowSizeMillis) {
            synchronized (window) {
                if (now - window.startTime >= windowSizeMillis) {
                    window.startTime = now;
                    window.counter.set(0);
                }
            }
        }

        int currentCount = window.counter.incrementAndGet();
        if (currentCount <= maxRequests) {
            log.debug("Request allowed for key: {}, count: {}/{}", key, currentCount, maxRequests);
            return true;
        }

        log.debug("Rate limit exceeded for key: {}, count: {}/{}", key, currentCount, maxRequests);
        window.counter.decrementAndGet();
        return false;
    }

    @Override
    public int getAvailablePermits(String key) {
        Window window = windows.get(key);
        if (window == null) {
            return maxRequests;
        }

        long now = System.currentTimeMillis();
        if (now - window.startTime >= windowSizeMillis) {
            return maxRequests;
        }

        return Math.max(0, maxRequests - window.counter.get());
    }

    @Override
    public void reset(String key) {
        windows.remove(key);
    }

    @AllArgsConstructor
    private static class Window {
        volatile long startTime;
        final AtomicInteger counter;
    }
}
