package com.systemdesign.urlshortener;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * URL Shortener Service implementation.
 * 
 * Design Considerations:
 * 1. ID Generation: Auto-increment counter (in production, use distributed ID generator)
 * 2. Encoding: Base62 for URL-safe short codes
 * 3. Collision Handling: Sequential IDs guarantee uniqueness
 * 4. Custom URLs: Support for user-defined short codes
 * 5. Expiration: Optional TTL for URLs
 * 6. Analytics: Track click counts
 * 
 * Scalability:
 * - Use database for persistence (this is in-memory for demo)
 * - Distributed ID generation (Snowflake, UUID, or database sequences)
 * - Cache frequently accessed URLs (Redis)
 * - CDN for global distribution
 */
@Slf4j
public class UrlShortenerService {
    
    private final AtomicLong idGenerator;
    private final Map<String, ShortenedUrl> shortCodeToUrl;
    private final Map<String, String> urlToShortCode; // For deduplication
    private final String baseUrl;
    
    public UrlShortenerService(String baseUrl) {
        this.baseUrl = baseUrl;
        this.idGenerator = new AtomicLong(1000); // Start from 1000 for demo
        this.shortCodeToUrl = new ConcurrentHashMap<>();
        this.urlToShortCode = new ConcurrentHashMap<>();
    }
    
    /**
     * Shorten a URL with auto-generated short code.
     * 
     * @param originalUrl The URL to shorten
     * @return The complete shortened URL
     */
    public String shortenUrl(String originalUrl) {
        return shortenUrl(originalUrl, null, null);
    }
    
    /**
     * Shorten a URL with optional custom short code and expiration.
     * 
     * @param originalUrl The URL to shorten
     * @param customShortCode Optional custom short code
     * @param expiresAt Optional expiration time
     * @return The complete shortened URL
     */
    public String shortenUrl(String originalUrl, String customShortCode, LocalDateTime expiresAt) {
        if (originalUrl == null || originalUrl.trim().isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        
        // Check if URL already shortened (deduplication)
        if (urlToShortCode.containsKey(originalUrl)) {
            String existingCode = urlToShortCode.get(originalUrl);
            log.debug("URL already shortened: {} -> {}", originalUrl, existingCode);
            return baseUrl + "/" + existingCode;
        }
        
        String shortCode;
        
        if (customShortCode != null && !customShortCode.trim().isEmpty()) {
            // Use custom short code
            if (shortCodeToUrl.containsKey(customShortCode)) {
                throw new IllegalArgumentException("Custom short code already in use: " + customShortCode);
            }
            shortCode = customShortCode;
        } else {
            // Generate short code from ID
            long id = idGenerator.getAndIncrement();
            shortCode = Base62Encoder.encode(id);
        }
        
        ShortenedUrl shortened = new ShortenedUrl(shortCode, originalUrl);
        shortened.setExpiresAt(expiresAt);
        
        shortCodeToUrl.put(shortCode, shortened);
        urlToShortCode.put(originalUrl, shortCode);
        
        log.info("Created short URL: {} -> {}", baseUrl + "/" + shortCode, originalUrl);
        return baseUrl + "/" + shortCode;
    }
    
    /**
     * Expand a short code to get the original URL.
     * 
     * @param shortCode The short code to expand
     * @return Optional containing the original URL if found and not expired
     */
    public Optional<String> expandUrl(String shortCode) {
        ShortenedUrl shortened = shortCodeToUrl.get(shortCode);
        
        if (shortened == null) {
            log.debug("Short code not found: {}", shortCode);
            return Optional.empty();
        }
        
        if (shortened.isExpired()) {
            log.info("Short code expired: {}", shortCode);
            return Optional.empty();
        }
        
        // Track click
        shortened.incrementClickCount();
        log.debug("Expanded {} -> {} (clicks: {})", 
                shortCode, shortened.getOriginalUrl(), shortened.getClickCount());
        
        return Optional.of(shortened.getOriginalUrl());
    }
    
    /**
     * Get analytics for a short code.
     * 
     * @param shortCode The short code
     * @return Optional containing the ShortenedUrl with metadata
     */
    public Optional<ShortenedUrl> getAnalytics(String shortCode) {
        return Optional.ofNullable(shortCodeToUrl.get(shortCode));
    }
    
    /**
     * Delete a short URL.
     * 
     * @param shortCode The short code to delete
     * @return true if deleted, false if not found
     */
    public boolean deleteUrl(String shortCode) {
        ShortenedUrl removed = shortCodeToUrl.remove(shortCode);
        if (removed != null) {
            urlToShortCode.remove(removed.getOriginalUrl());
            log.info("Deleted short URL: {}", shortCode);
            return true;
        }
        return false;
    }
    
    /**
     * Get total number of shortened URLs.
     */
    public int getUrlCount() {
        return shortCodeToUrl.size();
    }
}

