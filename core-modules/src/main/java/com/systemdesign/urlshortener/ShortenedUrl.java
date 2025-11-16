package com.systemdesign.urlshortener;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents a shortened URL with metadata.
 */
@Data
@AllArgsConstructor
public class ShortenedUrl {
    private String shortCode;
    private String originalUrl;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private long clickCount;
    private String userId; // Optional: track who created it
    
    public ShortenedUrl(String shortCode, String originalUrl) {
        this.shortCode = shortCode;
        this.originalUrl = originalUrl;
        this.createdAt = LocalDateTime.now();
        this.expiresAt = null; // No expiration by default
        this.clickCount = 0;
        this.userId = null;
    }
    
    public boolean isExpired() {
        return expiresAt != null && LocalDateTime.now().isAfter(expiresAt);
    }
    
    public void incrementClickCount() {
        this.clickCount++;
    }
}

