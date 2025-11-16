package com.systemdesign.queue;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Simple in-memory message queue implementation.
 * 
 * Features:
 * - Priority-based message ordering
 * - Visibility timeout (like AWS SQS)
 * - Dead letter queue for failed messages
 * - At-least-once delivery guarantee
 * - Message retry mechanism
 * 
 * Use cases:
 * - Task queues
 * - Event processing
 * - Background job processing
 * - Asynchronous communication
 */
@Slf4j
public class MessageQueue<T> {
    
    private final PriorityBlockingQueue<Message<T>> queue;
    private final Map<String, Message<T>> inFlight;
    private final Queue<Message<T>> deadLetterQueue;
    private final int visibilityTimeoutSeconds;
    private final int maxRetries;
    private final Lock lock;
    
    public MessageQueue(int visibilityTimeoutSeconds, int maxRetries) {
        this.queue = new PriorityBlockingQueue<>(100, 
                Comparator.comparingInt(Message<T>::getPriority).reversed()
                        .thenComparing(Message::getTimestamp));
        this.inFlight = new ConcurrentHashMap<>();
        this.deadLetterQueue = new LinkedList<>();
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.maxRetries = maxRetries;
        this.lock = new ReentrantLock();
    }
    
    /**
     * Send a message to the queue.
     */
    public String send(T payload) {
        return send(payload, 0);
    }
    
    /**
     * Send a message with priority.
     * Higher priority messages are processed first.
     */
    public String send(T payload, int priority) {
        Message<T> message = new Message<>(payload, priority);
        queue.offer(message);
        log.info("Message sent: {} (priority: {})", message.getId(), priority);
        return message.getId();
    }
    
    /**
     * Receive a message from the queue.
     * Message becomes invisible for the visibility timeout period.
     */
    public Optional<Message<T>> receive() {
        lock.lock();
        try {
            // Return timed-out messages to queue
            returnTimedOutMessages();
            
            Message<T> message = queue.poll();
            if (message == null) {
                return Optional.empty();
            }
            
            // Set visibility timeout
            message.setVisibilityTimeout(
                    LocalDateTime.now().plusSeconds(visibilityTimeoutSeconds));
            inFlight.put(message.getId(), message);
            
            log.debug("Message received: {} (in-flight: {})", 
                    message.getId(), inFlight.size());
            return Optional.of(message);
            
        } finally {
            lock.unlock();
        }
    }
    
    /**
     * Acknowledge successful processing of a message.
     * Removes the message from the queue permanently.
     */
    public boolean acknowledge(String messageId) {
        Message<T> message = inFlight.remove(messageId);
        if (message != null) {
            log.info("Message acknowledged: {}", messageId);
            return true;
        }
        log.warn("Message not found for acknowledgment: {}", messageId);
        return false;
    }
    
    /**
     * Negative acknowledge - message processing failed.
     * Message will be retried or moved to DLQ if max retries exceeded.
     */
    public boolean nack(String messageId) {
        Message<T> message = inFlight.remove(messageId);
        if (message == null) {
            log.warn("Message not found for nack: {}", messageId);
            return false;
        }
        
        message.incrementRetry();
        message.setVisibilityTimeout(null);
        
        if (message.getRetryCount() > maxRetries) {
            // Move to dead letter queue
            deadLetterQueue.offer(message);
            log.warn("Message moved to DLQ after {} retries: {}", 
                    maxRetries, messageId);
        } else {
            // Retry
            queue.offer(message);
            log.info("Message nacked, retry count: {}", message.getRetryCount());
        }
        
        return true;
    }
    
    /**
     * Get the current size of the main queue.
     */
    public int size() {
        return queue.size();
    }
    
    /**
     * Get the number of in-flight messages.
     */
    public int inFlightCount() {
        return inFlight.size();
    }
    
    /**
     * Get the dead letter queue size.
     */
    public int deadLetterQueueSize() {
        return deadLetterQueue.size();
    }
    
    /**
     * Peek at dead letter queue without removing.
     */
    public Optional<Message<T>> peekDeadLetter() {
        return Optional.ofNullable(deadLetterQueue.peek());
    }
    
    /**
     * Clear all queues.
     */
    public void clear() {
        queue.clear();
        inFlight.clear();
        deadLetterQueue.clear();
        log.info("All queues cleared");
    }
    
    /**
     * Return timed-out in-flight messages back to the queue.
     */
    private void returnTimedOutMessages() {
        Iterator<Map.Entry<String, Message<T>>> iterator = inFlight.entrySet().iterator();
        
        while (iterator.hasNext()) {
            Map.Entry<String, Message<T>> entry = iterator.next();
            Message<T> message = entry.getValue();
            
            if (message.isVisible()) {
                iterator.remove();
                message.setVisibilityTimeout(null);
                queue.offer(message);
                log.info("Message visibility timeout expired, returned to queue: {}", 
                        message.getId());
            }
        }
    }
}

