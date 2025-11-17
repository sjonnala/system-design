# Cache Eviction & Write Techniques Deep Dive

## Contents

- [Cache Eviction & Write Techniques Deep Dive](#cache-eviction--write-techniques-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Cache Eviction Algorithms](#cache-eviction-algorithms)
    - [Distributed Cache Write Patterns](#distributed-cache-write-patterns)
    - [Cache Replacement Policies](#cache-replacement-policies)
    - [Write Coordination Patterns](#write-coordination-patterns)
    - [Failure Modes & Edge Cases](#failure-modes--edge-cases)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Eviction Strategy Design (RADIO: Data-Model)](#3-eviction-strategy-design-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: CACHE EVICTION & WRITE CONCEPTS](#mind-map-cache-eviction--write-concepts)

## Core Mental Model

```text
Cache Eviction = Deciding what to remove when cache is full

The Fundamental Problem:
-----------------------
Limited Memory + Unlimited Data → Must Choose What to Keep

Goal: Maximize cache hit ratio
Strategy: Keep data most likely to be accessed again
Challenge: Predict future access patterns

Cache Eviction Policy Performance:
Best Case (Oracle): 100% hit ratio (knows future!)
LRU (Realistic):    80-95% hit ratio
Random:             60-70% hit ratio
FIFO:               70-80% hit ratio
```

**The Belady's Optimal Algorithm:**
```
┌────────────────────────────────────────────────┐
│ OPTIMAL (Theoretical):                         │
│ Evict item that will be accessed FARTHEST      │
│ in the future                                  │
│                                                │
│ Problem: Requires knowing the future! 🔮       │
│                                                │
│ Practical algorithms approximate this:        │
│ - LRU: Past ≈ Future (temporal locality)      │
│ - LFU: Frequency predicts future access       │
│ - ARC: Adapts between recency and frequency   │
└────────────────────────────────────────────────┘
```

**Visual Model:**
```text
CACHE FULL - MUST EVICT SOMETHING

┌─────────────────────────────────────┐
│        Cache (Capacity: 3)          │
│                                     │
│  [Key: A, Last Access: 10s ago]    │ ← LRU candidate
│  [Key: B, Last Access: 5s ago]     │
│  [Key: C, Last Access: 2s ago]     │ ← Recently used
└─────────────────────────────────────┘
                ↑
          New item arrives!
                ↓
        Must evict one item
                ↓
     LRU chooses: Evict A
     (least recently used)
```

**Real-World Implementation:**
```java
/**
 * Cache eviction abstraction
 */
public interface EvictionPolicy<K, V> {

    /**
     * Called when value is accessed
     */
    void onAccess(K key);

    /**
     * Called when value is inserted
     */
    void onInsert(K key, V value);

    /**
     * Choose victim to evict
     * @return key to evict
     */
    K selectVictim();

    /**
     * Called when value is removed
     */
    void onRemove(K key);
}

/**
 * Generic cache with pluggable eviction
 */
public class EvictableCache<K, V> {

    private final Map<K, V> storage;
    private final EvictionPolicy<K, V> evictionPolicy;
    private final int maxSize;

    public EvictableCache(int maxSize, EvictionPolicy<K, V> evictionPolicy) {
        this.storage = new ConcurrentHashMap<>();
        this.evictionPolicy = evictionPolicy;
        this.maxSize = maxSize;
    }

    public V get(K key) {
        V value = storage.get(key);
        if (value != null) {
            evictionPolicy.onAccess(key);  // Update recency/frequency
        }
        return value;
    }

    public void put(K key, V value) {
        // Evict if at capacity
        if (storage.size() >= maxSize && !storage.containsKey(key)) {
            K victim = evictionPolicy.selectVictim();
            if (victim != null) {
                storage.remove(victim);
                evictionPolicy.onRemove(victim);
            }
        }

        storage.put(key, value);
        evictionPolicy.onInsert(key, value);
    }

    public void remove(K key) {
        storage.remove(key);
        evictionPolicy.onRemove(key);
    }
}
```

**Key Insight**: **No single eviction policy is best for all workloads**. Choose based on access patterns.

---

## Cache Eviction Algorithms

🎓 **PROFESSOR**: There are **seven fundamental eviction algorithms**:

### 1. **LRU (Least Recently Used)**

Most popular due to good performance and simplicity:

```java
/**
 * LRU implementation using LinkedHashMap
 */
public class LRUCache<K, V> extends LinkedHashMap<K, V> {

    private final int maxSize;

    public LRUCache(int maxSize) {
        // accessOrder=true: iteration order = access order (not insertion)
        super(16, 0.75f, true);
        this.maxSize = maxSize;
    }

    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        // Automatically remove least recently used when size exceeded
        return size() > maxSize;
    }
}

/**
 * Manual LRU with explicit tracking
 */
public class ManualLRU<K, V> implements EvictionPolicy<K, V> {

    // Doubly-linked list for O(1) move-to-front
    private final LinkedHashMap<K, Node<K, V>> accessOrder;

    private static class Node<K, V> {
        K key;
        V value;
        long lastAccessTime;

        Node(K key, V value) {
            this.key = key;
            this.value = value;
            this.lastAccessTime = System.nanoTime();
        }
    }

    public ManualLRU() {
        this.accessOrder = new LinkedHashMap<>(16, 0.75f, true);
    }

    @Override
    public void onAccess(K key) {
        Node<K, V> node = accessOrder.get(key);
        if (node != null) {
            node.lastAccessTime = System.nanoTime();
            // LinkedHashMap automatically moves to end on access
        }
    }

    @Override
    public void onInsert(K key, V value) {
        accessOrder.put(key, new Node<>(key, value));
    }

    @Override
    public K selectVictim() {
        // First entry is least recently used
        return accessOrder.isEmpty() ? null :
               accessOrder.entrySet().iterator().next().getKey();
    }

    @Override
    public void onRemove(K key) {
        accessOrder.remove(key);
    }
}
```

**LRU Characteristics:**
```
Time Complexity:
- Get: O(1)
- Put: O(1)
- Evict: O(1)

Space Complexity: O(n) for tracking access order

Pros:
✅ Simple to implement
✅ Good hit ratio (80-95% for typical workloads)
✅ Exploits temporal locality

Cons:
❌ Sequential scans pollute cache
❌ Doesn't consider access frequency
❌ Not scan-resistant
```

### 2. **LFU (Least Frequently Used)**

Evicts items accessed least often:

```java
/**
 * LFU implementation with frequency tracking
 */
public class LFUCache<K, V> implements EvictionPolicy<K, V> {

    private final Map<K, CacheEntry<V>> cache;
    private final Map<K, Integer> frequency;  // Track access count
    private final TreeMap<Integer, Set<K>> frequencyBuckets;  // freq → keys

    private static class CacheEntry<V> {
        V value;
        int accessCount;

        CacheEntry(V value) {
            this.value = value;
            this.accessCount = 1;
        }
    }

    public LFUCache() {
        this.cache = new HashMap<>();
        this.frequency = new HashMap<>();
        this.frequencyBuckets = new TreeMap<>();
    }

    @Override
    public void onAccess(K key) {
        if (!cache.containsKey(key)) return;

        // Increment frequency
        int oldFreq = frequency.get(key);
        int newFreq = oldFreq + 1;

        // Update frequency tracking
        frequency.put(key, newFreq);

        // Move to higher frequency bucket
        frequencyBuckets.get(oldFreq).remove(key);
        if (frequencyBuckets.get(oldFreq).isEmpty()) {
            frequencyBuckets.remove(oldFreq);
        }

        frequencyBuckets
            .computeIfAbsent(newFreq, k -> new LinkedHashSet<>())
            .add(key);

        cache.get(key).accessCount = newFreq;
    }

    @Override
    public void onInsert(K key, V value) {
        cache.put(key, new CacheEntry<>(value));
        frequency.put(key, 1);
        frequencyBuckets
            .computeIfAbsent(1, k -> new LinkedHashSet<>())
            .add(key);
    }

    @Override
    public K selectVictim() {
        if (frequencyBuckets.isEmpty()) return null;

        // Get lowest frequency bucket
        Map.Entry<Integer, Set<K>> lowestFreq = frequencyBuckets.firstEntry();

        // Get first key (oldest among least frequent)
        return lowestFreq.getValue().iterator().next();
    }

    @Override
    public void onRemove(K key) {
        int freq = frequency.remove(key);
        cache.remove(key);

        Set<K> bucket = frequencyBuckets.get(freq);
        bucket.remove(key);
        if (bucket.isEmpty()) {
            frequencyBuckets.remove(freq);
        }
    }
}
```

**LFU Characteristics:**
```
Pros:
✅ Good for skewed access patterns (Zipfian)
✅ Hot items never evicted
✅ Frequency is strong predictor

Cons:
❌ Old popular items linger (even if not accessed recently)
❌ New items evicted quickly
❌ More complex implementation
```

### 3. **LRU-K (K-th Most Recent Access)**

Tracks K most recent accesses:

```java
/**
 * LRU-2: Track 2 most recent accesses
 * Better than LRU at handling scans
 */
public class LRU2Cache<K, V> implements EvictionPolicy<K, V> {

    private static class AccessHistory {
        long firstAccess;
        long secondAccess;

        void recordAccess(long timestamp) {
            firstAccess = secondAccess;
            secondAccess = timestamp;
        }

        long getKthAccess(int k) {
            return k == 1 ? secondAccess : firstAccess;
        }
    }

    private final Map<K, AccessHistory> history;

    public LRU2Cache() {
        this.history = new HashMap<>();
    }

    @Override
    public void onAccess(K key) {
        AccessHistory h = history.computeIfAbsent(key, k -> new AccessHistory());
        h.recordAccess(System.nanoTime());
    }

    @Override
    public void onInsert(K key, V value) {
        AccessHistory h = new AccessHistory();
        h.recordAccess(System.nanoTime());
        history.put(key, h);
    }

    @Override
    public K selectVictim() {
        // Evict item with oldest second-to-last access
        return history.entrySet().stream()
            .min(Comparator.comparing(e -> e.getValue().getKthAccess(2)))
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    @Override
    public void onRemove(K key) {
        history.remove(key);
    }
}
```

**LRU-K Use Case:**
```
Scenario: Database buffer pool with sequential scans

Without LRU-K:
- Scan reads 1000 pages sequentially
- Evicts all hot pages from cache
- Cache polluted with scan data (accessed once)

With LRU-2:
- Scan pages accessed only once
- Not promoted (need 2 accesses)
- Hot pages remain in cache
```

### 4. **2Q (Two Queues)**

Separates one-hit wonders from frequently accessed:

```java
/**
 * 2Q: Two-queue eviction policy
 * - A1in: First-access queue (FIFO)
 * - Am: Main queue (LRU)
 */
public class TwoQueueCache<K, V> implements EvictionPolicy<K, V> {

    // A1in: First-time access (FIFO)
    private final Queue<K> firstAccessQueue;

    // Am: Multi-access (LRU)
    private final LinkedHashMap<K, Long> mainQueue;

    // A1out: Ghost queue (tracks recently evicted)
    private final Set<K> ghostQueue;

    private final int firstAccessSize;
    private final int mainSize;
    private final int ghostSize;

    public TwoQueueCache(int cacheSize) {
        // Tuning: 25% first-access, 75% main
        this.firstAccessSize = cacheSize / 4;
        this.mainSize = (cacheSize * 3) / 4;
        this.ghostSize = cacheSize / 2;

        this.firstAccessQueue = new LinkedList<>();
        this.mainQueue = new LinkedHashMap<>(16, 0.75f, true);
        this.ghostQueue = new LinkedHashSet<>();
    }

    @Override
    public void onAccess(K key) {
        if (mainQueue.containsKey(key)) {
            // Already in main queue - update LRU
            mainQueue.put(key, System.nanoTime());

        } else if (ghostQueue.contains(key)) {
            // Was evicted recently, now accessed again
            // Promote directly to main queue (skip first-access)
            ghostQueue.remove(key);
            promoteToMain(key);

        } else if (firstAccessQueue.contains(key)) {
            // Second access - promote to main
            firstAccessQueue.remove(key);
            promoteToMain(key);
        }
        // else: new key, will be added via onInsert
    }

    @Override
    public void onInsert(K key, V value) {
        // New keys go to first-access queue
        if (firstAccessQueue.size() >= firstAccessSize) {
            // Evict from first-access queue
            K evicted = firstAccessQueue.poll();
            // Add to ghost queue
            addToGhost(evicted);
        }

        firstAccessQueue.offer(key);
    }

    @Override
    public K selectVictim() {
        // Evict from main queue (LRU)
        if (!mainQueue.isEmpty()) {
            return mainQueue.entrySet().iterator().next().getKey();
        }

        // Fallback: evict from first-access
        return firstAccessQueue.peek();
    }

    @Override
    public void onRemove(K key) {
        firstAccessQueue.remove(key);
        mainQueue.remove(key);
    }

    private void promoteToMain(K key) {
        if (mainQueue.size() >= mainSize) {
            // Evict from main
            K victim = selectVictim();
            mainQueue.remove(victim);
            addToGhost(victim);
        }

        mainQueue.put(key, System.nanoTime());
    }

    private void addToGhost(K key) {
        if (ghostQueue.size() >= ghostSize) {
            // Remove oldest ghost
            ghostQueue.iterator().next();
            ghostQueue.remove(ghostQueue.iterator().next());
        }

        ghostQueue.add(key);
    }
}
```

### 5. **ARC (Adaptive Replacement Cache)**

Dynamically balances between recency and frequency:

```java
/**
 * ARC: Adaptive Replacement Cache
 * Balances between LRU and LFU adaptively
 */
public class ARCCache<K, V> implements EvictionPolicy<K, V> {

    // T1: Recent items (accessed once)
    private final LinkedHashMap<K, V> t1;

    // T2: Frequent items (accessed 2+ times)
    private final LinkedHashMap<K, V> t2;

    // B1: Ghost list for T1 (recently evicted from T1)
    private final Set<K> b1;

    // B2: Ghost list for T2 (recently evicted from T2)
    private final Set<K> b2;

    // Target size for T1 (adapts based on workload)
    private int p;

    private final int capacity;

    public ARCCache(int capacity) {
        this.capacity = capacity;
        this.t1 = new LinkedHashMap<>(16, 0.75f, true);
        this.t2 = new LinkedHashMap<>(16, 0.75f, true);
        this.b1 = new LinkedHashSet<>();
        this.b2 = new LinkedHashSet<>();
        this.p = 0;  // Initially balanced
    }

    @Override
    public void onAccess(K key) {
        if (t1.containsKey(key)) {
            // Move from T1 to T2 (now frequent)
            V value = t1.remove(key);
            t2.put(key, value);

        } else if (t2.containsKey(key)) {
            // Already in T2, update LRU
            t2.get(key);  // Access updates LRU order

        } else if (b1.contains(key)) {
            // Cache hit in ghost list B1
            // Increase preference for recency
            p = Math.min(p + Math.max(b2.size() / b1.size(), 1), capacity);

            replace(key, true);
            b1.remove(key);

        } else if (b2.contains(key)) {
            // Cache hit in ghost list B2
            // Increase preference for frequency
            p = Math.max(p - Math.max(b1.size() / b2.size(), 1), 0);

            replace(key, false);
            b2.remove(key);
        }
    }

    @Override
    public void onInsert(K key, V value) {
        // New item goes to T1
        if (t1.size() + t2.size() >= capacity) {
            replace(key, true);
        }

        t1.put(key, value);
    }

    @Override
    public K selectVictim() {
        // Choose victim based on adaptive parameter p
        if (t1.size() >= Math.max(1, p)) {
            return t1.keySet().iterator().next();  // From T1
        } else {
            return t2.keySet().iterator().next();  // From T2
        }
    }

    @Override
    public void onRemove(K key) {
        t1.remove(key);
        t2.remove(key);
        b1.remove(key);
        b2.remove(key);
    }

    /**
     * Replace victim based on adaptive policy
     */
    private void replace(K key, boolean preferT1) {
        if (t1.size() > 0 && (t1.size() > p || preferT1)) {
            // Evict from T1
            K victim = t1.keySet().iterator().next();
            t1.remove(victim);
            b1.add(victim);  // Move to ghost list
        } else {
            // Evict from T2
            K victim = t2.keySet().iterator().next();
            t2.remove(victim);
            b2.add(victim);  // Move to ghost list
        }

        // Maintain ghost list sizes
        if (b1.size() > capacity) {
            b1.iterator().next();
            b1.remove(b1.iterator().next());
        }
        if (b2.size() > capacity) {
            b2.iterator().next();
            b2.remove(b2.iterator().next());
        }
    }
}
```

**ARC Adaptive Behavior:**
```
Workload 1: Sequential scans (recent focus)
→ ARC learns: Increase p (more space for T1/recent)

Workload 2: Hot set (frequency focus)
→ ARC learns: Decrease p (more space for T2/frequent)

Result: Better than pure LRU or LFU! 🎯
```

### 6. **FIFO (First-In-First-Out)**

Simplest policy, rarely used in practice:

```java
/**
 * FIFO: Evict oldest entry
 */
public class FIFOCache<K, V> implements EvictionPolicy<K, V> {

    private final Queue<K> insertionOrder;

    public FIFOCache() {
        this.insertionOrder = new LinkedList<>();
    }

    @Override
    public void onAccess(K key) {
        // FIFO doesn't care about access patterns
    }

    @Override
    public void onInsert(K key, V value) {
        insertionOrder.offer(key);
    }

    @Override
    public K selectVictim() {
        return insertionOrder.peek();
    }

    @Override
    public void onRemove(K key) {
        insertionOrder.remove(key);
    }
}
```

### 7. **Random Replacement**

Surprisingly effective baseline:

```java
/**
 * Random eviction
 * O(1) and scan-resistant!
 */
public class RandomCache<K, V> implements EvictionPolicy<K, V> {

    private final List<K> keys;
    private final Random random;

    public RandomCache() {
        this.keys = new ArrayList<>();
        this.random = new Random();
    }

    @Override
    public void onAccess(K key) {
        // Random doesn't track access
    }

    @Override
    public void onInsert(K key, V value) {
        keys.add(key);
    }

    @Override
    public K selectVictim() {
        if (keys.isEmpty()) return null;
        int index = random.nextInt(keys.size());
        return keys.get(index);
    }

    @Override
    public void onRemove(K key) {
        keys.remove(key);
    }
}
```

🏗️ **ARCHITECT**: **Choosing eviction policy**:

```python
class EvictionPolicySelector:
    """
    Select eviction policy based on workload
    """
    def recommend(self, workload: Workload) -> str:
        # Sequential scans? Use LRU-2 or 2Q
        if workload.has_sequential_scans:
            return "LRU-2 or 2Q (scan-resistant)"

        # Highly skewed (Zipfian)? Use LFU
        if workload.access_distribution == 'ZIPFIAN':
            if workload.data_changes_slowly:
                return "LFU (hot set stable)"
            else:
                return "ARC (adapts to changing hot set)"

        # Variable workload? Use ARC
        if workload.is_variable:
            return "ARC (self-tuning)"

        # Simple, predictable? Use LRU
        if workload.is_simple:
            return "LRU (good default, simple)"

        # Small cache, random okay? Use Random
        if workload.cache_size_ratio < 0.1:
            return "Random (simple, scan-resistant)"

        # Default: LRU (80/20 rule)
        return "LRU (most common choice)"
```

**Eviction Policy Performance Comparison:**

```
Workload Type       LRU    LFU    LRU-2   2Q    ARC   Random
────────────────────────────────────────────────────────────
Temporal Locality   95%    80%    96%     95%   96%   70%
Freq Locality       85%    97%    87%     90%   95%   75%
Sequential Scans    60%    70%    90%     92%   93%   80%
Mixed Workload      87%    85%    90%     92%   94%   73%
Implementation      Easy   Med    Med     Hard  Hard  Easy
────────────────────────────────────────────────────────────

Legend: Hit ratio for different workloads
Recommendation: ARC > 2Q > LRU-2 > LRU > LFU > Random
```

---

## Distributed Cache Write Patterns

🎓 **PROFESSOR**: Writing to distributed cache requires **coordination**:

```
┌────────────────────────────────────────────────┐
│ Single-Node Write    vs   Distributed Write   │
│                                                │
│ ✅ Simple              ❌ Complex              │
│ ✅ Atomic              ❌ Distributed txn      │
│ ❌ No redundancy       ✅ Replicated           │
│ ❌ Single point        ✅ Fault tolerant       │
└────────────────────────────────────────────────┘
```

### Write Replication Strategies

🏗️ **ARCHITECT**: **Four replication patterns**:

#### 1. **Synchronous Replication (All Writes)**

```java
/**
 * Write to all replicas synchronously
 * Strong consistency, slow writes
 */
public class SynchronousReplication {

    private final List<CacheNode> replicas;

    public void set(String key, String value) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        // Write to ALL replicas in parallel
        for (CacheNode replica : replicas) {
            futures.add(CompletableFuture.runAsync(() -> {
                replica.set(key, value);
            }));
        }

        // Wait for ALL to complete
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(5, TimeUnit.SECONDS);

        } catch (TimeoutException e) {
            // Write failed - rollback?
            throw new CacheWriteException("Synchronous replication failed", e);
        }
    }
}
```

**Characteristics:**
```
✅ Strong consistency (all replicas identical)
✅ Read from any replica (all have latest)

❌ Slow writes (limited by slowest replica)
❌ Reduced availability (any replica down = write fails)
❌ Write amplification (N writes for 1 update)
```

#### 2. **Asynchronous Replication**

```java
/**
 * Write to primary, replicate async
 * Fast writes, eventual consistency
 */
public class AsynchronousReplication {

    private final CacheNode primary;
    private final List<CacheNode> replicas;
    private final BlockingQueue<ReplicationTask> replicationQueue;
    private final ExecutorService replicator;

    public AsynchronousReplication() {
        this.replicationQueue = new LinkedBlockingQueue<>();
        this.replicator = Executors.newFixedThreadPool(4);

        // Background replication workers
        for (int i = 0; i < 4; i++) {
            replicator.submit(this::replicationWorker);
        }
    }

    public void set(String key, String value) {
        // 1. Write to primary immediately (fast!)
        primary.set(key, value);

        // 2. Queue replication (async)
        replicationQueue.offer(new ReplicationTask(key, value));

        // Return immediately (low latency)
    }

    /**
     * Background worker replicates to followers
     */
    private void replicationWorker() {
        while (true) {
            try {
                ReplicationTask task = replicationQueue.take();

                // Replicate to all followers
                for (CacheNode replica : replicas) {
                    try {
                        replica.set(task.key, task.value);
                    } catch (Exception e) {
                        log.warn("Replication failed to {}", replica, e);
                        // Retry logic here
                    }
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public String get(String key) {
        // Always read from primary (latest data)
        return primary.get(key);

        // OR: Read from any replica (might be stale)
        // return selectRandomReplica().get(key);
    }
}
```

**Characteristics:**
```
✅ Fast writes (async replication)
✅ High availability (primary writes always succeed)

❌ Eventual consistency (replicas lag)
❌ Read replicas might serve stale data
❌ Replication lag during failures
```

#### 3. **Quorum-Based Writes**

```java
/**
 * Write to majority (quorum) of replicas
 * Balance between consistency and performance
 */
public class QuorumReplication {

    private final List<CacheNode> replicas;
    private final int replicationFactor;

    public QuorumReplication(List<CacheNode> replicas) {
        this.replicas = replicas;
        this.replicationFactor = replicas.size();
    }

    /**
     * Write to W replicas (W = quorum)
     */
    public void set(String key, String value) {
        int W = (replicationFactor / 2) + 1;  // Majority

        List<CompletableFuture<Void>> futures = replicas.stream()
            .map(replica -> CompletableFuture.runAsync(() ->
                replica.set(key, value)
            ))
            .collect(Collectors.toList());

        // Wait for W replicas to acknowledge
        try {
            waitForQuorum(futures, W);
        } catch (TimeoutException e) {
            throw new QuorumNotMetException(
                "Failed to achieve write quorum", e
            );
        }
    }

    /**
     * Read from R replicas (R = quorum)
     */
    public String get(String key) {
        int R = (replicationFactor / 2) + 1;  // Majority

        List<CompletableFuture<VersionedValue>> futures = replicas.stream()
            .map(replica -> CompletableFuture.supplyAsync(() ->
                replica.getVersioned(key)
            ))
            .collect(Collectors.toList());

        try {
            // Read from R replicas
            List<VersionedValue> results = waitForQuorum(futures, R);

            // Return value with highest version (resolve conflicts)
            return results.stream()
                .max(Comparator.comparing(VersionedValue::getVersion))
                .map(VersionedValue::getValue)
                .orElse(null);

        } catch (TimeoutException e) {
            throw new QuorumNotMetException(
                "Failed to achieve read quorum", e
            );
        }
    }

    /**
     * Quorum formula: W + R > N guarantees consistency
     * Where: W = write replicas, R = read replicas, N = total replicas
     *
     * Example: N=5, W=3, R=3 → Strong consistency (3+3=6 > 5)
     * Example: N=5, W=2, R=2 → Weak (might miss updates)
     */
}
```

**Tunable Consistency:**
```java
public enum ConsistencyLevel {
    ONE(1, 1),           // Fastest, weakest
    QUORUM(
        (n) -> n/2 + 1,  // Balanced
        (n) -> n/2 + 1
    ),
    ALL(
        (n) -> n,        // Slowest, strongest
        (n) -> n
    );

    private final Function<Integer, Integer> writeReplicas;
    private final Function<Integer, Integer> readReplicas;

    ConsistencyLevel(Function<Integer, Integer> w, Function<Integer, Integer> r) {
        this.writeReplicas = w;
        this.readReplicas = r;
    }

    public int getWriteReplicas(int totalReplicas) {
        return writeReplicas.apply(totalReplicas);
    }

    public int getReadReplicas(int totalReplicas) {
        return readReplicas.apply(totalReplicas);
    }
}
```

#### 4. **Last-Write-Wins (LWW)**

```java
/**
 * Resolve conflicts using timestamps
 * Simple but can lose data
 */
public class LastWriteWins {

    public static class VersionedValue {
        String value;
        long timestamp;
        String nodeId;  // Who wrote this?

        public VersionedValue(String value, String nodeId) {
            this.value = value;
            this.timestamp = System.currentTimeMillis();
            this.nodeId = nodeId;
        }

        /**
         * Conflict resolution: Latest timestamp wins
         */
        public static VersionedValue resolve(VersionedValue v1, VersionedValue v2) {
            if (v1.timestamp > v2.timestamp) {
                return v1;
            } else if (v2.timestamp > v1.timestamp) {
                return v2;
            } else {
                // Same timestamp - use node ID as tiebreaker
                return v1.nodeId.compareTo(v2.nodeId) > 0 ? v1 : v2;
            }
        }
    }

    /**
     * Write with timestamp
     */
    public void set(String key, String value, CacheNode node) {
        VersionedValue versioned = new VersionedValue(value, node.getId());
        node.set(key, versioned);

        // Replicate to other nodes (async)
        replicateAsync(key, versioned);
    }

    /**
     * Read and resolve conflicts
     */
    public String get(String key) {
        // Read from multiple replicas
        List<VersionedValue> values = readFromReplicas(key);

        // Resolve conflicts (latest wins)
        return values.stream()
            .reduce(VersionedValue::resolve)
            .map(v -> v.value)
            .orElse(null);
    }
}
```

**Problem with LWW:**
```text
Time  Node A      Node B      Result
t0    Set X=1     -           ✅ X=1
t1    Set X=2     Set X=3     ❌ Conflict!
t2    -           -           LWW: X=3 (Node B's write lost A's!)

Better: Use Vector Clocks or CRDTs for true conflict detection
```

---

## Cache Replacement Policies

🎓 **PROFESSOR**: Advanced replacement techniques:

### Size-Aware Eviction

```java
/**
 * Evict based on size, not just count
 * Good for variable-size values
 */
public class SizeAwareLRU<K, V> {

    private static class SizedEntry<V> {
        V value;
        int size;
        long lastAccess;

        SizedEntry(V value, int size) {
            this.value = value;
            this.size = size;
            this.lastAccess = System.nanoTime();
        }
    }

    private final Map<K, SizedEntry<V>> cache;
    private final long maxBytes;
    private long currentBytes;

    public SizeAwareLRU(long maxBytes) {
        this.cache = new LinkedHashMap<>(16, 0.75f, true);
        this.maxBytes = maxBytes;
        this.currentBytes = 0;
    }

    public void put(K key, V value, int size) {
        // Evict until enough space
        while (currentBytes + size > maxBytes && !cache.isEmpty()) {
            evictOldest();
        }

        // Add new entry
        SizedEntry<V> entry = new SizedEntry<>(value, size);
        SizedEntry<V> old = cache.put(key, entry);

        if (old != null) {
            currentBytes -= old.size;  // Remove old size
        }
        currentBytes += size;
    }

    public V get(K key) {
        SizedEntry<V> entry = cache.get(key);
        if (entry != null) {
            entry.lastAccess = System.nanoTime();
            return entry.value;
        }
        return null;
    }

    private void evictOldest() {
        // Find LRU entry
        Map.Entry<K, SizedEntry<V>> oldest = cache.entrySet().stream()
            .min(Comparator.comparing(e -> e.getValue().lastAccess))
            .orElse(null);

        if (oldest != null) {
            cache.remove(oldest.getKey());
            currentBytes -= oldest.getValue().size;
        }
    }
}
```

### Cost-Aware Eviction

```java
/**
 * Consider cost to recompute when evicting
 * Evict cheap-to-recompute items first
 */
public class CostAwareLRU<K, V> {

    private static class CostEntry<V> {
        V value;
        long lastAccess;
        int recomputeCost;  // ms to recompute

        double evictionScore() {
            long age = System.nanoTime() - lastAccess;
            // Score = age / cost (prefer old, cheap items)
            return age / (double) recomputeCost;
        }
    }

    private final Map<K, CostEntry<V>> cache;

    public void put(K key, V value, int recomputeCost) {
        CostEntry<V> entry = new CostEntry<>();
        entry.value = value;
        entry.lastAccess = System.nanoTime();
        entry.recomputeCost = recomputeCost;

        cache.put(key, entry);
    }

    public K selectVictim() {
        // Evict item with highest score (old + cheap)
        return cache.entrySet().stream()
            .max(Comparator.comparing(e -> e.getValue().evictionScore()))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
```

### TTL-Based Eviction

```java
/**
 * Combine LRU with TTL
 * Items expire after fixed time OR evicted when full
 */
public class TTLCache<K, V> {

    private static class TTLEntry<V> {
        V value;
        long expiresAt;
        long lastAccess;

        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }

    private final Map<K, TTLEntry<V>> cache;
    private final ScheduledExecutorService cleaner;

    public TTLCache() {
        this.cache = new ConcurrentHashMap<>();

        // Background thread removes expired entries
        this.cleaner = Executors.newScheduledThreadPool(1);
        this.cleaner.scheduleAtFixedRate(
            this::removeExpired,
            1, 1, TimeUnit.MINUTES
        );
    }

    public void put(K key, V value, Duration ttl) {
        TTLEntry<V> entry = new TTLEntry<>();
        entry.value = value;
        entry.expiresAt = System.currentTimeMillis() + ttl.toMillis();
        entry.lastAccess = System.nanoTime();

        cache.put(key, entry);
    }

    public V get(K key) {
        TTLEntry<V> entry = cache.get(key);
        if (entry == null) return null;

        // Check if expired
        if (entry.isExpired()) {
            cache.remove(key);
            return null;
        }

        entry.lastAccess = System.nanoTime();
        return entry.value;
    }

    private void removeExpired() {
        long now = System.currentTimeMillis();

        cache.entrySet().removeIf(e ->
            e.getValue().expiresAt < now
        );
    }

    /**
     * Evict based on LRU among non-expired items
     */
    public K selectVictim() {
        return cache.entrySet().stream()
            .filter(e -> !e.getValue().isExpired())
            .min(Comparator.comparing(e -> e.getValue().lastAccess))
            .map(Map.Entry::getKey)
            .orElse(null);
    }
}
```

---

## Write Coordination Patterns

### Optimistic Locking

```java
/**
 * Version-based optimistic concurrency control
 */
public class OptimisticCache {

    private static class VersionedValue {
        String value;
        long version;

        VersionedValue(String value, long version) {
            this.value = value;
            this.version = version;
        }
    }

    private final Map<String, VersionedValue> cache;

    /**
     * Read with version
     */
    public VersionedValue get(String key) {
        return cache.get(key);
    }

    /**
     * Write with version check (CAS = Compare-And-Swap)
     */
    public boolean compareAndSet(String key, String newValue, long expectedVersion) {
        return cache.compute(key, (k, current) -> {
            if (current == null && expectedVersion == 0) {
                // New key
                return new VersionedValue(newValue, 1);
            }

            if (current != null && current.version == expectedVersion) {
                // Version matches - update
                return new VersionedValue(newValue, current.version + 1);
            }

            // Version mismatch - conflict!
            return current;  // Keep old value
        }) != null;
    }

    /**
     * Usage example
     */
    public void updateWithRetry(String key, Function<String, String> updater) {
        int maxRetries = 10;

        for (int retry = 0; retry < maxRetries; retry++) {
            // 1. Read current value and version
            VersionedValue current = get(key);
            String oldValue = current != null ? current.value : null;
            long version = current != null ? current.version : 0;

            // 2. Compute new value
            String newValue = updater.apply(oldValue);

            // 3. Try to update with version check
            if (compareAndSet(key, newValue, version)) {
                return;  // Success!
            }

            // 4. Conflict - retry
            log.debug("Optimistic lock conflict, retry {}", retry);
            try {
                Thread.sleep(10 * (retry + 1));  // Exponential backoff
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CacheException("Interrupted during retry");
            }
        }

        throw new CacheException("Failed after " + maxRetries + " retries");
    }
}
```

### Pessimistic Locking

```java
/**
 * Distributed lock for cache coordination
 */
public class DistributedLockCache {

    private final RedissonClient redisson;  // Redisson for distributed locks
    private final Cache cache;

    /**
     * Update with distributed lock
     */
    public void updateWithLock(String key, Function<String, String> updater) {
        RLock lock = redisson.getLock("lock:" + key);

        try {
            // Acquire lock (blocks until available)
            boolean acquired = lock.tryLock(10, 30, TimeUnit.SECONDS);
            if (!acquired) {
                throw new LockTimeoutException("Failed to acquire lock for " + key);
            }

            // Critical section (exclusive access)
            String oldValue = cache.get(key);
            String newValue = updater.apply(oldValue);
            cache.set(key, newValue);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CacheException("Interrupted while acquiring lock");

        } finally {
            // Always release lock
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
```

---

## Failure Modes & Edge Cases

### Write Amplification

```text
Problem: Single logical write → multiple physical writes

Example: Write to cache with 3 replicas
User writes 1 value → 3 cache writes → 3x write amplification

Impact:
- Network bandwidth (3x)
- Disk I/O (3x)
- Latency (wait for all replicas)

Mitigation:
- Asynchronous replication (don't wait)
- Batch writes (amortize overhead)
- Selective replication (not all data needs 3 copies)
```

### Cache Coherence

```java
/**
 * Problem: Multiple cache layers get out of sync
 */
public class CacheCoherenceProblem {

    /**
     * Scenario:
     * 1. App Server 1 writes X=1 to L1 and L2
     * 2. App Server 2 writes X=2 to L1 and L2
     * 3. Invalidation message lost
     * 4. Server 1's L1 still has X=1 (stale!)
     */

    /**
     * Solution: Invalidation broadcast
     */
    @Service
    public class CoherentCache {

        @Autowired
        private MessageBroker broker;

        @Autowired
        private Cache l1;  // Local

        @Autowired
        private Cache l2;  // Shared

        public void set(String key, String value) {
            // 1. Write to L2 (shared)
            l2.set(key, value);

            // 2. Update local L1
            l1.set(key, value);

            // 3. Broadcast invalidation to other servers
            broker.publish("cache.invalidate", new InvalidationEvent(key));
        }

        @EventListener
        public void onInvalidation(InvalidationEvent event) {
            // Other servers invalidate their L1
            l1.delete(event.getKey());
        }
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Functional:
- What eviction policy? (LRU, LFU, TTL)
- What consistency? (Strong, eventual, tunable)
- Write pattern? (Write-through, write-behind, cache-aside)

Non-Functional:
- Hit ratio target: 90%+
- Write latency: P99 < 10ms
- Cache size: 100GB
- Replication factor: 3
- Consistency: Eventual (for social media)
```

### 2. Capacity Estimation (RADIO: Scale)

```
Eviction Rate Calculation:

Cache Capacity: 100GB
Item Size: 1KB average
Max Items: 100GB / 1KB = 100M items

New Items Per Day: 10M
Eviction Rate: 10M / 100M = 10% per day

Peak Eviction Rate:
- Assume 2x during peak hours
- Peak eviction: 10M items / 12 hours = 231 items/sec

Memory for Metadata:
- LRU: 40 bytes per item (pointers)
- 100M items * 40 bytes = 4GB metadata
- Total memory: 100GB + 4GB = 104GB
```

### 3. Eviction Strategy Design (RADIO: Data-Model)

```java
/**
 * Choose eviction policy
 */
public class EvictionStrategyDesign {

    public EvictionPolicy selectPolicy(CacheRequirements req) {
        if (req.hasSequentialScans()) {
            return new TwoQueueCache(req.getSize());
        }

        if (req.isZipfianDistribution()) {
            return new LFUCache();
        }

        if (req.hasVariableWorkload()) {
            return new ARCCache(req.getSize());
        }

        // Default: LRU (80% of use cases)
        return new LRUCache(req.getSize());
    }

    public WriteStrategy selectWriteStrategy(CacheRequirements req) {
        if (req.requiresStrongConsistency()) {
            return WriteStrategy.WRITE_THROUGH;
        }

        if (req.isWriteHeavy()) {
            return WriteStrategy.WRITE_BEHIND;
        }

        // Default: Cache-aside
        return WriteStrategy.CACHE_ASIDE;
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────┐
│            APPLICATION SERVERS                   │
│                                                  │
│  ┌──────────────────────────────────────────┐  │
│  │  L1 Cache (LRU, 10K items)               │  │
│  │  Eviction: Every 5 minutes               │  │
│  └───────────────┬──────────────────────────┘  │
└──────────────────┼──────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────┐
│  DISTRIBUTED CACHE (Redis Cluster)              │
│                                                  │
│  ┌──────────────────────────────────────────┐  │
│  │  Eviction: LRU with maxmemory-policy     │  │
│  │  Replication: Async (3 replicas)         │  │
│  │  Write: Quorum (W=2, R=2, N=3)           │  │
│  └──────────────────────────────────────────┘  │
│                                                  │
│     Shard 0        Shard 1        Shard 2       │
│  [P] [R] [R]    [P] [R] [R]    [P] [R] [R]     │
└─────────────────────────────────────────────────┘

Eviction Trigger:
- Memory threshold: 90% full
- TTL expiration: 1 hour
- Explicit invalidation: On write
```

### 5. Deep Dives (RADIO: Optimize)

**A. Eviction Performance Optimization**

```java
/**
 * Approximate LRU for better performance
 * O(1) instead of O(log n)
 */
public class ApproximateLRU<K, V> {

    private static class Entry<V> {
        V value;
        byte accessCounter;  // 8-bit counter

        void incrementAccess() {
            if (accessCounter < 127) {
                accessCounter++;
            }
        }

        void decrementAccess() {
            if (accessCounter > 0) {
                accessCounter--;
            }
        }
    }

    private final Map<K, Entry<V>> cache;
    private final int maxSize;
    private final Random random;

    /**
     * Sample-based eviction (Redis-style)
     * Sample N random keys, evict one with lowest counter
     */
    public K selectVictim() {
        int sampleSize = 5;  // Redis uses 5
        K victim = null;
        int minCounter = Integer.MAX_VALUE;

        List<K> keys = new ArrayList<>(cache.keySet());

        for (int i = 0; i < sampleSize && i < keys.size(); i++) {
            int idx = random.nextInt(keys.size());
            K key = keys.get(idx);
            Entry<V> entry = cache.get(key);

            if (entry.accessCounter < minCounter) {
                minCounter = entry.accessCounter;
                victim = key;
            }
        }

        return victim;
    }

    /**
     * Background thread decays counters
     * Implements time-based decay
     */
    @Scheduled(fixedDelay = 60000)  // Every minute
    public void decayCounters() {
        cache.values().forEach(Entry::decrementAccess);
    }
}
```

**B. Monitoring Eviction Health**

```java
@Component
public class EvictionMetrics {

    public void recordEviction(String reason, int itemSize) {
        // Track eviction reasons
        registry.counter("cache.evictions",
            "reason", reason  // size, ttl, lru
        ).increment();

        // Track evicted item sizes
        registry.summary("cache.evicted.size").record(itemSize);
    }

    public void recordEvictionRate() {
        // Evictions per second
        registry.gauge("cache.eviction.rate",
            evictionsLastSecond
        );

        /**
         * Alerts:
         * - eviction.rate > 1000/sec (thrashing)
         * - evicted.size P95 > 100KB (large items evicted)
         * - evictions with reason=size > 80% (cache too small)
         */
    }
}
```

---

## 🧠 MIND MAP: CACHE EVICTION & WRITE CONCEPTS

```
      EVICTION & WRITES
            |
      ┌─────┴─────┐
      ↓           ↓
  EVICTION     WRITES
      |           |
  ┌───┴───┐   ┌───┴───┐
  ↓       ↓   ↓       ↓
 LRU    LFU  SYNC   ASYNC
  |       |    |       |
ACCESS FREQ  ALL   PRIMARY
COUNT COUNT WAIT   ONLY
  |       |    |       |
O(1)   O(logn) SLOW  FAST
SIMPLE COMPLEX STRONG EVENTUAL
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **LRU = Recently Used Books 📚**
- Books you read recently stay on desk (easy access)
- Old books go back to shelf (evicted)
- If desk full, remove book not touched in longest time
- NOT most rarely used, but LEAST RECENTLY used

### 2. **LFU = Popular Songs Playlist 🎵**
- Frequently played songs stay in playlist
- Rarely played songs removed
- New song must prove itself (play count)
- Old favorites might stick around too long

### 3. **2Q = Airport Security Lines 🛫**
- First-time flyers: Slow lane (FIFO)
- Frequent flyers: Fast lane (priority)
- Prove you're frequent to get fast lane access
- One-time travelers don't clog fast lane

### 4. **Write-Through = Carbon Copy Forms 📋**
- Write on top sheet (cache)
- Carbon automatically writes to bottom (DB)
- Both copies always identical
- Slower writing but guaranteed consistency

### 5. **Write-Behind = Taking Notes 📝**
- Scribble notes quickly (cache)
- Clean up and file later (DB)
- Fast note-taking but might lose if notebook lost
- Batch organize for efficiency

### 6. **Quorum = Jury Verdict ⚖️**
- Need majority agreement (not unanimous)
- 5 jurors: 3 must agree (quorum)
- Faster than unanimous
- Still strong consensus

### 7. **Last-Write-Wins = Whiteboard Updates 🖊️**
- Multiple people can write on whiteboard
- Last person's writing stays
- Previous writing erased (lost!)
- Simple but lossy

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Cache Eviction:**

1. **No Universal Best Policy**
   - LRU: Good default (80% of cases)
   - LFU: Zipfian distributions
   - ARC: Unknown/variable workloads

2. **Measure Before Optimizing**
   - Track hit ratio, eviction rate
   - Profile access patterns
   - Choose policy based on data, not intuition

3. **Consistency vs Performance Trade-off**
   - Sync replication: Slow, consistent
   - Async replication: Fast, eventually consistent
   - Quorum: Balanced middle ground

---

## 🏗️ ARCHITECT'S CHECKLIST

Before deploying eviction strategy:

- [ ] **Policy Choice**: LRU? LFU? ARC? Based on workload?
- [ ] **Eviction Trigger**: Memory threshold? Item count? TTL?
- [ ] **Eviction Rate**: Sustainable? Not thrashing?
- [ ] **Write Strategy**: Sync? Async? Quorum?
- [ ] **Replication**: How many replicas? Quorum size?
- [ ] **Consistency**: Strong? Eventual? Acceptable staleness?
- [ ] **Monitoring**: Track hit ratio, eviction rate, latency?
- [ ] **Conflict Resolution**: LWW? Vector clocks? CRDTs?
- [ ] **Failure Handling**: Write failures? Replication lag?
- [ ] **Testing**: Load tested eviction under stress?

---

**Interview Closer**: "I'd use LRU eviction as the default since it's simple and effective. For write coordination, I'd use quorum-based replication (W=2, R=2, N=3) to balance consistency and performance. Monitor eviction rate closely - high rate indicates cache too small or poor policy choice. For hot keys, consider LFU instead. Let me draw the architecture..."
