package com.systemdesign.cache;

import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * LRU (Least Recently Used) Cache implementation.
 * 
 * Algorithm:
 * - Evicts the least recently used item when capacity is reached
 * - Uses a doubly-linked list to track access order
 * - HashMap for O(1) lookups
 * 
 * Time Complexity:
 * - Get: O(1)
 * - Put: O(1)
 * - Remove: O(1)
 * 
 * Use cases:
 * - Browser cache
 * - Database query cache
 * - CDN caching
 * - Page cache
 */
@Slf4j
public class LRUCache<K, V> implements Cache<K, V> {
    
    private final int capacity;
    private final Map<K, Node<K, V>> cache;
    private final DoublyLinkedList<K, V> list;
    
    // Statistics
    private final AtomicLong hits = new AtomicLong(0);
    private final AtomicLong misses = new AtomicLong(0);
    private final AtomicLong evictions = new AtomicLong(0);
    
    public LRUCache(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        this.capacity = capacity;
        this.cache = new HashMap<>(capacity);
        this.list = new DoublyLinkedList<>();
    }
    
    @Override
    public synchronized Optional<V> get(K key) {
        Node<K, V> node = cache.get(key);
        
        if (node == null) {
            misses.incrementAndGet();
            log.debug("Cache miss for key: {}", key);
            return Optional.empty();
        }
        
        // Move to front (most recently used)
        list.moveToFront(node);
        hits.incrementAndGet();
        log.debug("Cache hit for key: {}", key);
        return Optional.of(node.value);
    }
    
    @Override
    public synchronized void put(K key, V value) {
        Node<K, V> existingNode = cache.get(key);
        
        if (existingNode != null) {
            // Update existing entry
            existingNode.value = value;
            list.moveToFront(existingNode);
            log.debug("Updated cache entry for key: {}", key);
            return;
        }
        
        // Check capacity
        if (cache.size() >= capacity) {
            // Evict least recently used
            Node<K, V> lru = list.removeLast();
            if (lru != null) {
                cache.remove(lru.key);
                evictions.incrementAndGet();
                log.debug("Evicted LRU entry: {}", lru.key);
            }
        }
        
        // Add new entry
        Node<K, V> newNode = new Node<>(key, value);
        list.addToFront(newNode);
        cache.put(key, newNode);
        log.debug("Added new cache entry for key: {}", key);
    }
    
    @Override
    public synchronized boolean remove(K key) {
        Node<K, V> node = cache.remove(key);
        if (node != null) {
            list.remove(node);
            log.debug("Removed cache entry for key: {}", key);
            return true;
        }
        return false;
    }
    
    @Override
    public synchronized void clear() {
        cache.clear();
        list.clear();
        log.info("Cache cleared");
    }
    
    @Override
    public synchronized int size() {
        return cache.size();
    }
    
    @Override
    public CacheStats getStats() {
        return new CacheStats(hits.get(), misses.get(), evictions.get());
    }
    
    // Doubly-linked list node
    private static class Node<K, V> {
        K key;
        V value;
        Node<K, V> prev;
        Node<K, V> next;
        
        Node(K key, V value) {
            this.key = key;
            this.value = value;
        }
    }
    
    // Doubly-linked list for tracking order
    private static class DoublyLinkedList<K, V> {
        private Node<K, V> head;
        private Node<K, V> tail;
        
        void addToFront(Node<K, V> node) {
            if (head == null) {
                head = tail = node;
                return;
            }
            
            node.next = head;
            head.prev = node;
            head = node;
        }
        
        void moveToFront(Node<K, V> node) {
            if (node == head) {
                return;
            }
            
            remove(node);
            addToFront(node);
        }
        
        Node<K, V> removeLast() {
            if (tail == null) {
                return null;
            }
            
            Node<K, V> last = tail;
            remove(last);
            return last;
        }
        
        void remove(Node<K, V> node) {
            if (node.prev != null) {
                node.prev.next = node.next;
            } else {
                head = node.next;
            }
            
            if (node.next != null) {
                node.next.prev = node.prev;
            } else {
                tail = node.prev;
            }
            
            node.prev = null;
            node.next = null;
        }
        
        void clear() {
            head = tail = null;
        }
    }
}

