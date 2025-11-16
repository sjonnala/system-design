package com.systemdesign.queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.systemdesign.queue.Message;
import com.systemdesign.queue.MessageQueue;

class MessageQueueTest {
    
    private MessageQueue<String> queue;
    
    @BeforeEach
    void setUp() {
        queue = new MessageQueue<>(30, 3); // 30 sec timeout, 3 max retries
    }
    
    @Test
    void testSendAndReceive() {
        String messageId = queue.send("test message");
        assertNotNull(messageId);
        assertEquals(1, queue.size());
        
        Optional<Message<String>> received = queue.receive();
        assertTrue(received.isPresent());
        assertEquals("test message", received.get().getPayload());
        assertEquals(0, queue.size());
        assertEquals(1, queue.inFlightCount());
    }
    
    @Test
    void testAcknowledge() {
        String messageId = queue.send("test");
        Optional<Message<String>> received = queue.receive();
        
        assertTrue(received.isPresent());
        assertTrue(queue.acknowledge(received.get().getId()));
        
        assertEquals(0, queue.size());
        assertEquals(0, queue.inFlightCount());
    }
    
    @Test
    void testNackAndRetry() {
        String messageId = queue.send("test");
        Optional<Message<String>> received = queue.receive();
        
        assertTrue(received.isPresent());
        assertTrue(queue.nack(received.get().getId()));
        
        // Message should be back in queue for retry
        assertEquals(1, queue.size());
        assertEquals(0, queue.inFlightCount());
        
        // Receive again
        Optional<Message<String>> retry = queue.receive();
        assertTrue(retry.isPresent());
        assertEquals(1, retry.get().getRetryCount());
    }
    
    @Test
    void testDeadLetterQueue() {
        String messageId = queue.send("test");
        
        // Fail message multiple times
        for (int i = 0; i <= 3; i++) {
            Optional<Message<String>> received = queue.receive();
            assertTrue(received.isPresent());
            queue.nack(received.get().getId());
        }
        
        // Should be in dead letter queue now
        assertEquals(0, queue.size());
        assertEquals(1, queue.deadLetterQueueSize());
        
        Optional<Message<String>> dlq = queue.peekDeadLetter();
        assertTrue(dlq.isPresent());
        assertEquals("test", dlq.get().getPayload());
    }
    
    @Test
    void testPriorityOrdering() {
        queue.send("low priority", 1);
        queue.send("high priority", 10);
        queue.send("medium priority", 5);
        
        assertEquals(3, queue.size());
        
        // Should receive in priority order
        Optional<Message<String>> msg1 = queue.receive();
        assertEquals("high priority", msg1.get().getPayload());
        
        Optional<Message<String>> msg2 = queue.receive();
        assertEquals("medium priority", msg2.get().getPayload());
        
        Optional<Message<String>> msg3 = queue.receive();
        assertEquals("low priority", msg3.get().getPayload());
    }
    
    @Test
    void testVisibilityTimeout() throws InterruptedException {
        queue = new MessageQueue<>(1, 3); // 1 second timeout
        
        String messageId = queue.send("test");
        Optional<Message<String>> received = queue.receive();
        
        assertTrue(received.isPresent());
        assertEquals(0, queue.size());
        assertEquals(1, queue.inFlightCount());
        
        // Wait for visibility timeout
        Thread.sleep(1100);
        
        // Try to receive again - should get the timed-out message
        Optional<Message<String>> timedOut = queue.receive();
        assertTrue(timedOut.isPresent());
        assertEquals("test", timedOut.get().getPayload());
    }
    
    @Test
    void testEmptyQueue() {
        Optional<Message<String>> received = queue.receive();
        assertFalse(received.isPresent());
    }
    
    @Test
    void testClear() {
        queue.send("msg1");
        queue.send("msg2");
        queue.send("msg3");
        
        assertEquals(3, queue.size());
        
        queue.clear();
        
        assertEquals(0, queue.size());
        assertEquals(0, queue.inFlightCount());
    }
    
    @Test
    void testMultipleConsumers() {
        // Send multiple messages
        for (int i = 0; i < 10; i++) {
            queue.send("message " + i);
        }
        
        assertEquals(10, queue.size());
        
        // Simulate multiple consumers
        for (int i = 0; i < 10; i++) {
            Optional<Message<String>> msg = queue.receive();
            assertTrue(msg.isPresent());
        }
        
        assertEquals(0, queue.size());
        assertEquals(10, queue.inFlightCount());
        
        // All messages should be unique
        assertEquals(10, queue.inFlightCount());
    }
}

