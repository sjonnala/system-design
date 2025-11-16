package com.systemdesign.queue;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a message in the queue.
 */
@Data
@AllArgsConstructor
public class Message<T> {
    private String id;
    private T payload;
    private int priority;
    private LocalDateTime timestamp;
    private int retryCount;
    private LocalDateTime visibilityTimeout;
    
    public Message(T payload) {
        this(payload, 0);
    }
    
    public Message(T payload, int priority) {
        this.id = UUID.randomUUID().toString();
        this.payload = payload;
        this.priority = priority;
        this.timestamp = LocalDateTime.now();
        this.retryCount = 0;
        this.visibilityTimeout = null;
    }
    
    public boolean isVisible() {
        return visibilityTimeout == null || LocalDateTime.now().isAfter(visibilityTimeout);
    }
    
    public void incrementRetry() {
        this.retryCount++;
    }
}

