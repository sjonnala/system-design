package com.systemdesign.urlshortener;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class UrlShortenerServiceTest {
    
    private UrlShortenerService service;
    
    @BeforeEach
    void setUp() {
        service = new UrlShortenerService("https://short.url");
    }
    
    @Test
    void testShortenUrl() {
        String shortUrl = service.shortenUrl("https://www.example.com/very/long/url");
        
        assertNotNull(shortUrl);
        assertTrue(shortUrl.startsWith("https://short.url/"));
        assertEquals(1, service.getUrlCount());
    }
    
    @Test
    void testExpandUrl() {
        String originalUrl = "https://www.example.com/page";
        String shortUrl = service.shortenUrl(originalUrl);
        
        // Extract short code
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        Optional<String> expanded = service.expandUrl(shortCode);
        assertTrue(expanded.isPresent());
        assertEquals(originalUrl, expanded.get());
    }
    
    @Test
    void testExpandNonExistentCode() {
        Optional<String> expanded = service.expandUrl("nonexistent");
        assertFalse(expanded.isPresent());
    }
    
    @Test
    void testCustomShortCode() {
        String shortUrl = service.shortenUrl("https://www.example.com", "mycode", null);
        
        assertTrue(shortUrl.endsWith("/mycode"));
        
        Optional<String> expanded = service.expandUrl("mycode");
        assertTrue(expanded.isPresent());
        assertEquals("https://www.example.com", expanded.get());
    }
    
    @Test
    void testDuplicateCustomShortCode() {
        service.shortenUrl("https://www.example1.com", "mycode", null);
        
        assertThrows(IllegalArgumentException.class, () -> {
            service.shortenUrl("https://www.example2.com", "mycode", null);
        });
    }
    
    @Test
    void testUrlDeduplication() {
        String url = "https://www.example.com";
        
        String shortUrl1 = service.shortenUrl(url);
        String shortUrl2 = service.shortenUrl(url);
        
        assertEquals(shortUrl1, shortUrl2);
        assertEquals(1, service.getUrlCount());
    }
    
    @Test
    void testClickTracking() {
        String shortUrl = service.shortenUrl("https://www.example.com");
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        // Click multiple times
        service.expandUrl(shortCode);
        service.expandUrl(shortCode);
        service.expandUrl(shortCode);
        
        Optional<ShortenedUrl> analytics = service.getAnalytics(shortCode);
        assertTrue(analytics.isPresent());
        assertEquals(3, analytics.get().getClickCount());
    }
    
    @Test
    void testUrlExpiration() {
        LocalDateTime pastTime = LocalDateTime.now().minusHours(1);
        String shortUrl = service.shortenUrl("https://www.example.com", "expired", pastTime);
        
        Optional<String> expanded = service.expandUrl("expired");
        assertFalse(expanded.isPresent(), "Expired URL should not be expanded");
    }
    
    @Test
    void testDeleteUrl() {
        String shortUrl = service.shortenUrl("https://www.example.com");
        String shortCode = shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
        
        assertTrue(service.deleteUrl(shortCode));
        assertEquals(0, service.getUrlCount());
        
        Optional<String> expanded = service.expandUrl(shortCode);
        assertFalse(expanded.isPresent());
    }
    
    @Test
    void testBase62Encoding() {
        assertEquals("g8", Base62Encoder.encode(1000));
        assertEquals("1C", Base62Encoder.encode(100));
        assertEquals("0", Base62Encoder.encode(0));
    }
    
    @Test
    void testBase62Decoding() {
        assertEquals(1000, Base62Encoder.decode("g8"));
        assertEquals(100, Base62Encoder.decode("1C"));
        assertEquals(0, Base62Encoder.decode("0"));
    }
    
    @Test
    void testBase62RoundTrip() {
        for (long i = 0; i < 10000; i += 123) {
            String encoded = Base62Encoder.encode(i);
            long decoded = Base62Encoder.decode(encoded);
            assertEquals(i, decoded);
        }
    }
}

