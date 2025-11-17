# Distributed Caching Deep Dive

## Contents

- [Distributed Caching Deep Dive](#distributed-caching-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Caching Fundamentals](#caching-fundamentals)
    - [Distributed Cache Architecture](#distributed-cache-architecture)
    - [Cache Consistency Patterns](#cache-consistency-patterns)
    - [Cache Invalidation Strategies](#cache-invalidation-strategies)
    - [Failure Modes & Edge Cases](#failure-modes--edge-cases)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Cache Architecture (RADIO: Data-Model)](#3-cache-architecture-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: DISTRIBUTED CACHING CONCEPTS](#mind-map-distributed-caching-concepts)

## Core Mental Model

```text
Caching = Storing frequently accessed data in fast storage

The Fundamental Trade-off:
-------------------------
Speed ←→ Freshness
- Faster cache = potentially stale data
- Fresher data = more database hits

Cache Hierarchy:
L1: CPU Cache       (~1 ns)
L2: RAM             (~100 ns)
L3: SSD             (~100 μs)
L4: HDD             (~10 ms)
L5: Network DB      (~100 ms)
```

**The Cache Hit Ratio Formula:**
```
Cache Hit Ratio = Cache Hits / Total Requests

Example:
- 1000 requests
- 900 served from cache (hits)
- 100 served from database (misses)
- Hit Ratio = 900/1000 = 90%

Performance Improvement:
- Cache latency: 1ms
- DB latency: 100ms
- Avg latency without cache: 100ms
- Avg latency with 90% hit ratio:
  = 0.9 * 1ms + 0.1 * 100ms
  = 0.9 + 10
  = 10.9ms
→ 9x improvement! 🚀
```

**Visual Model:**
```text
WITHOUT CACHE:
┌────────────┐
│   Client   │
└─────┬──────┘
      ↓ (100ms per request)
┌─────────────┐
│  Database   │ ← All requests hit DB
│  (Slow)     │
└─────────────┘

WITH CACHE:
┌────────────┐
│   Client   │
└─────┬──────┘
      ↓
  ┌───┴────┐
  │        ↓ (10% miss → 100ms)
  │   ┌─────────────┐
  │   │  Database   │
  │   └─────────────┘
  ↓ (90% hit → 1ms)
┌─────────────┐
│    Cache    │
│   (Fast)    │
└─────────────┘
```

**Real-World Implementation:**
```java
/**
 * Multi-level caching strategy
 */
@Service
public class CachingService {

    // L1: Local in-memory cache (fastest)
    private final LoadingCache<String, Product> localCache;

    // L2: Distributed cache (fast, shared)
    private final RedisTemplate<String, Product> redis;

    // L3: Database (slow, source of truth)
    private final ProductRepository database;

    public CachingService() {
        // L1: Caffeine cache (in-process)
        this.localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .recordStats()  // Track hit/miss
            .build(key -> getFromL2OrDB(key));
    }

    /**
     * Get with multi-level cache
     */
    public Product getProduct(Long productId) {
        String key = "product:" + productId;

        // L1: Check local cache (fastest)
        return localCache.get(key);
    }

    /**
     * L2/L3 fallback (called by L1 on miss)
     */
    private Product getFromL2OrDB(String key) {
        // L2: Check Redis
        Product product = redis.opsForValue().get(key);
        if (product != null) {
            return product;
        }

        // L3: Check database (cache miss)
        Long productId = extractId(key);
        product = database.findById(productId)
            .orElseThrow(() -> new ProductNotFoundException(productId));

        // Populate L2 cache
        redis.opsForValue().set(key, product, 1, TimeUnit.HOURS);

        return product;
    }

    /**
     * Invalidate all levels
     */
    public void invalidate(Long productId) {
        String key = "product:" + productId;

        // Invalidate L1
        localCache.invalidate(key);

        // Invalidate L2
        redis.delete(key);
    }

    /**
     * Get cache statistics
     */
    public CacheStats getStats() {
        CacheStats stats = localCache.stats();
        return new CacheStats(
            stats.hitCount(),
            stats.missCount(),
            stats.hitRate(),
            stats.evictionCount()
        );
    }
}
```

**Key Insight**: **Cache only data with high read-to-write ratio**. If data changes frequently, caching adds overhead without benefit.

---

## Caching Fundamentals

🎓 **PROFESSOR**: There are **three fundamental questions** in caching:

```
┌────────────────────────────────────────────────┐
│ 1. WHAT to cache?                              │
│    → Expensive queries, frequently accessed    │
│                                                │
│ 2. WHEN to cache?                              │
│    → Cache-aside, read-through, write-through │
│                                                │
│ 3. WHERE to cache?                             │
│    → Client, CDN, server, database            │
└────────────────────────────────────────────────┘
```

### Cache Access Patterns

🏗️ **ARCHITECT**: **Five fundamental caching patterns**:

#### 1. **Cache-Aside (Lazy Loading)**

```java
/**
 * Application manages cache explicitly
 * Most common pattern
 */
public class CacheAsidePattern {

    @Autowired
    private CacheManager cache;

    @Autowired
    private Database database;

    public User getUser(Long userId) {
        String key = "user:" + userId;

        // 1. Try cache first
        User user = cache.get(key);
        if (user != null) {
            return user;  // Cache hit
        }

        // 2. Cache miss → fetch from DB
        user = database.findById(userId);

        // 3. Populate cache
        if (user != null) {
            cache.set(key, user, TTL_1_HOUR);
        }

        return user;
    }

    public void updateUser(User user) {
        // 1. Update database
        database.save(user);

        // 2. Invalidate cache
        cache.delete("user:" + user.getId());

        // Note: Next read will populate cache
    }
}
```

**Pros & Cons:**
```
✅ Only requested data is cached (efficient memory)
✅ Cache failure doesn't break application
✅ Simple to implement

❌ Cache miss penalty (read + write to cache)
❌ Stale data if cache not invalidated
❌ Cache stampede risk (see Thundering Herd)
```

#### 2. **Read-Through**

```java
/**
 * Cache automatically loads from database
 * Application only talks to cache
 */
public class ReadThroughCache {

    private final LoadingCache<String, User> cache;

    public ReadThroughCache(Database database) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build(key -> {
                // Cache automatically loads from DB on miss
                Long userId = extractId(key);
                return database.findById(userId);
            });
    }

    public User getUser(Long userId) {
        // Cache handles miss automatically
        return cache.get("user:" + userId);
    }
}
```

**Pros & Cons:**
```
✅ Simpler application code
✅ Consistent cache loading logic
✅ Automatic cache population

❌ Cache becomes single point of failure
❌ Cold start problem (empty cache = slow)
❌ Harder to implement custom logic
```

#### 3. **Write-Through**

```java
/**
 * Write to cache AND database synchronously
 * Guarantees cache consistency
 */
public class WriteThroughCache {

    @Autowired
    private Cache cache;

    @Autowired
    private Database database;

    public void updateUser(User user) {
        String key = "user:" + user.getId();

        // Write to BOTH (synchronously)
        database.save(user);          // 1. Database first
        cache.set(key, user);         // 2. Then cache

        // Cache always consistent with DB
    }

    public User getUser(Long userId) {
        String key = "user:" + userId;

        // Cache always has latest data
        return cache.get(key);
    }
}
```

**Pros & Cons:**
```
✅ Cache always consistent with DB
✅ Read performance excellent (always in cache)
✅ No stale data

❌ Write performance slow (2 writes)
❌ Wasted cache space (infrequently read data)
❌ Cache failure breaks writes
```

#### 4. **Write-Behind (Write-Back)**

```java
/**
 * Write to cache immediately, DB asynchronously
 * Best write performance, eventual consistency
 */
public class WriteBehindCache {

    private final Cache cache;
    private final Database database;
    private final BlockingQueue<WriteOperation> writeQueue;
    private final ExecutorService writerThread;

    public WriteBehindCache() {
        this.writeQueue = new LinkedBlockingQueue<>();

        // Background thread flushes to DB
        this.writerThread = Executors.newSingleThreadExecutor();
        this.writerThread.submit(this::flushWorker);
    }

    public void updateUser(User user) {
        String key = "user:" + user.getId();

        // 1. Write to cache immediately (fast!)
        cache.set(key, user);

        // 2. Queue DB write (async)
        writeQueue.offer(new WriteOperation(user));

        // Return immediately (low latency)
    }

    /**
     * Background worker flushes to DB
     */
    private void flushWorker() {
        while (true) {
            try {
                // Batch writes for efficiency
                List<WriteOperation> batch = new ArrayList<>();
                writeQueue.drainTo(batch, 100);

                if (!batch.isEmpty()) {
                    // Batch write to DB
                    database.batchSave(batch);
                }

                Thread.sleep(100);  // Flush every 100ms

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public User getUser(Long userId) {
        // Always read from cache (fast)
        return cache.get("user:" + userId);
    }
}
```

**Pros & Cons:**
```
✅ Excellent write performance (async)
✅ Excellent read performance (always cached)
✅ Batch DB writes (efficient)

❌ Data loss risk (cache failure before flush)
❌ Complex to implement
❌ Eventual consistency (DB lags cache)
```

#### 5. **Refresh-Ahead**

```java
/**
 * Proactively refresh cache before expiration
 * Prevents cache misses for hot data
 */
public class RefreshAheadCache {

    private final LoadingCache<String, User> cache;
    private final ScheduledExecutorService refresher;

    public RefreshAheadCache(Database database) {
        this.cache = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build(key -> database.findById(extractId(key)));

        // Background refresh thread
        this.refresher = Executors.newScheduledThreadPool(4);
    }

    public User getUser(Long userId) {
        String key = "user:" + userId;
        User user = cache.get(key);

        // If close to expiration, refresh proactively
        if (isCloseToExpiration(key)) {
            refreshAsync(key);
        }

        return user;
    }

    private void refreshAsync(String key) {
        refresher.submit(() -> {
            try {
                // Reload from DB
                Long userId = extractId(key);
                User fresh = database.findById(userId);

                // Update cache
                cache.put(key, fresh);

            } catch (Exception e) {
                log.error("Failed to refresh cache for key: {}", key, e);
            }
        });
    }

    private boolean isCloseToExpiration(String key) {
        // Check if entry will expire in next 2 minutes
        CacheEntry<User> entry = cache.getIfPresent(key);
        if (entry == null) return false;

        long timeToExpire = entry.getExpirationTime() - System.currentTimeMillis();
        return timeToExpire < 2 * 60 * 1000;  // 2 minutes
    }
}
```

**Decision Matrix:**

```python
def choose_cache_pattern(workload: Workload) -> str:
    """
    Select caching pattern based on workload characteristics
    """
    # 1. Write-heavy workload?
    if workload.write_ratio > 0.5:
        if workload.can_tolerate_data_loss:
            return "WRITE_BEHIND (async writes)"
        else:
            return "CACHE_ASIDE (don't cache writes)"

    # 2. Read-heavy workload?
    if workload.read_ratio > 0.9:
        if workload.requires_strong_consistency:
            return "WRITE_THROUGH (always consistent)"
        else:
            return "CACHE_ASIDE (lazy loading)"

    # 3. Hot data that must never miss?
    if workload.has_critical_hot_keys:
        return "REFRESH_AHEAD (proactive refresh)"

    # 4. Unpredictable access patterns?
    if workload.access_pattern_random:
        return "CACHE_ASIDE (only cache what's used)"

    # Default: simplest pattern
    return "CACHE_ASIDE (most flexible)"
```

**Interview talking point**: "I'd use cache-aside for most cases because it's simple and resilient. Only use write-through if you need guaranteed consistency. Write-behind is for extreme write performance, but risky."

---

## Distributed Cache Architecture

🎓 **PROFESSOR**: Distributed caching introduces **data distribution challenges**:

```
┌────────────────────────────────────────────────┐
│ Single-Node Cache    vs   Distributed Cache   │
│                                                │
│ ✅ Simple              ❌ Complex              │
│ ✅ Consistent          ❌ Eventually consistent│
│ ❌ Limited memory      ✅ Scales horizontally  │
│ ❌ No fault tolerance  ✅ Replicated           │
│ ❌ Cold start slow     ✅ Shared cache         │
└────────────────────────────────────────────────┘
```

### Cache Distribution Strategies

🏗️ **ARCHITECT**: **Three main distribution patterns**:

#### 1. **Replicated Cache**

```text
Every cache node has ALL data

     App Server 1        App Server 2        App Server 3
          ↓                   ↓                   ↓
    ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
    │   Cache A   │     │   Cache B   │     │   Cache C   │
    │             │     │             │     │             │
    │ Key1: Val1  │     │ Key1: Val1  │     │ Key1: Val1  │ ← Replicated
    │ Key2: Val2  │     │ Key2: Val2  │     │ Key2: Val2  │
    │ Key3: Val3  │     │ Key3: Val3  │     │ Key3: Val3  │
    └─────────────┘     └─────────────┘     └─────────────┘

Pros:
✅ No network latency (local reads)
✅ High availability (any node can serve)
✅ No single point of failure

Cons:
❌ Memory inefficient (N copies)
❌ Write coordination overhead
❌ Limited by single-node capacity
```

#### 2. **Partitioned (Sharded) Cache**

```text
Each cache node has SUBSET of data

     App Server        App Server        App Server
          ↓                 ↓                 ↓
          └─────────────────┼─────────────────┘
                            ↓
                    Consistent Hashing
                            ↓
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
  ┌─────────────┐     ┌─────────────┐     ┌─────────────┐
  │   Cache A   │     │   Cache B   │     │   Cache C   │
  │             │     │             │     │             │
  │ Key1: Val1  │     │ Key4: Val4  │     │ Key7: Val7  │
  │ Key2: Val2  │     │ Key5: Val5  │     │ Key8: Val8  │
  │ Key3: Val3  │     │ Key6: Val6  │     │ Key9: Val9  │
  └─────────────┘     └─────────────┘     └─────────────┘

Pros:
✅ Memory efficient (distributed)
✅ Scales horizontally (add nodes)
✅ High capacity (sum of all nodes)

Cons:
❌ Network latency (remote reads)
❌ Hot shard problem
❌ Node failure loses data (need replication)
```

#### 3. **Hybrid (Partitioned + Replicated)**

```text
Partition + replicate each partition

                    App Servers
                        ↓
                Consistent Hashing
                        ↓
        ┌───────────────┼───────────────┐
        ↓               ↓               ↓
   Partition 0     Partition 1     Partition 2
        ↓               ↓               ↓
  ┌─────────┐     ┌─────────┐     ┌─────────┐
  │Primary A│     │Primary B│     │Primary C│
  │Key1,2,3 │     │Key4,5,6 │     │Key7,8,9 │
  └────┬────┘     └────┬────┘     └────┬────┘
       ↓               ↓               ↓
  ┌─────────┐     ┌─────────┐     ┌─────────┐
  │Replica A│     │Replica B│     │Replica C│
  │Key1,2,3 │     │Key4,5,6 │     │Key7,8,9 │
  └─────────┘     └─────────┘     └─────────┘

Pros:
✅ Scales horizontally (partition)
✅ Fault tolerant (replication)
✅ Balanced approach

Cons:
❌ Most complex
❌ Replication lag
❌ Consistency challenges
```

**Production Implementation:**

```java
/**
 * Distributed cache client with consistent hashing
 */
public class DistributedCacheClient {

    private final ConsistentHashRing<CacheNode> ring;
    private final int replicationFactor;

    public DistributedCacheClient(List<CacheNode> nodes, int replicationFactor) {
        this.ring = new ConsistentHashRing<>(150);  // 150 vnodes
        nodes.forEach(ring::addServer);
        this.replicationFactor = replicationFactor;
    }

    /**
     * Get from distributed cache
     */
    public <T> T get(String key) {
        // 1. Find primary node using consistent hashing
        CacheNode primary = ring.getServer(key);

        try {
            // 2. Try primary
            T value = primary.get(key);
            if (value != null) {
                return value;
            }

        } catch (CacheNodeUnavailableException e) {
            // 3. Primary down, try replicas
            log.warn("Primary {} unavailable, trying replicas", primary);
            return getFromReplica(key);
        }

        return null;  // Cache miss
    }

    /**
     * Set to distributed cache with replication
     */
    public <T> void set(String key, T value, Duration ttl) {
        // 1. Find replica nodes
        List<CacheNode> replicas = ring.getReplicas(key, replicationFactor);

        // 2. Write to all replicas (parallel)
        List<CompletableFuture<Void>> futures = replicas.stream()
            .map(node -> CompletableFuture.runAsync(() ->
                node.set(key, value, ttl)
            ))
            .collect(Collectors.toList());

        // 3. Wait for quorum (replicationFactor/2 + 1)
        int quorum = (replicationFactor / 2) + 1;
        try {
            waitForQuorum(futures, quorum);
        } catch (TimeoutException e) {
            log.error("Failed to achieve quorum for key: {}", key);
            throw new CacheWriteException("Quorum not met", e);
        }
    }

    /**
     * Delete from all replicas
     */
    public void delete(String key) {
        List<CacheNode> replicas = ring.getReplicas(key, replicationFactor);

        // Best effort delete from all replicas
        replicas.forEach(node -> {
            try {
                node.delete(key);
            } catch (Exception e) {
                log.warn("Failed to delete from node {}", node, e);
            }
        });
    }

    /**
     * Get from replica on primary failure
     */
    private <T> T getFromReplica(String key) {
        List<CacheNode> replicas = ring.getReplicas(key, replicationFactor);

        // Try each replica
        for (CacheNode replica : replicas) {
            try {
                T value = replica.get(key);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                log.warn("Replica {} also unavailable", replica);
            }
        }

        return null;  // All replicas failed
    }

    private void waitForQuorum(List<CompletableFuture<Void>> futures, int quorum)
            throws TimeoutException {

        long start = System.currentTimeMillis();
        int completed = 0;

        while (completed < quorum) {
            if (System.currentTimeMillis() - start > 5000) {  // 5s timeout
                throw new TimeoutException("Quorum timeout");
            }

            completed = (int) futures.stream()
                .filter(CompletableFuture::isDone)
                .count();

            if (completed < quorum) {
                try {
                    Thread.sleep(10);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new TimeoutException("Interrupted");
                }
            }
        }
    }
}
```

### Cache Topology Patterns

```java
/**
 * Cache deployment topologies
 */
public enum CacheTopology {

    /**
     * 1. EMBEDDED (In-Process)
     * - Cache library runs inside application
     * - E.g., Caffeine, Guava Cache, EhCache
     */
    EMBEDDED {
        public String getCharacteristics() {
            return """
                ✅ Lowest latency (~1μs)
                ✅ No network overhead
                ✅ Simple deployment
                ❌ Not shared across instances
                ❌ Limited by JVM heap
                ❌ Cold start per instance
                """;
        }
    },

    /**
     * 2. CLIENT-SERVER (Dedicated Cache)
     * - Separate cache servers
     * - E.g., Redis, Memcached
     */
    CLIENT_SERVER {
        public String getCharacteristics() {
            return """
                ✅ Shared across all app instances
                ✅ Large cache capacity
                ✅ Independent scaling
                ❌ Network latency (~1ms)
                ❌ Extra infrastructure
                ❌ Serialization overhead
                """;
        }
    },

    /**
     * 3. SIDECAR (Per-Instance Proxy)
     * - Cache runs alongside each app
     * - E.g., Envoy with caching, DragonflyDB sidecar
     */
    SIDECAR {
        public String getCharacteristics() {
            return """
                ✅ Low latency (localhost)
                ✅ Scales with app instances
                ✅ Resource isolation
                ❌ Not shared (replicated)
                ❌ Higher memory usage
                ❌ Complex orchestration
                """;
        }
    },

    /**
     * 4. PROXY (Gateway Cache)
     * - Cache sits between app and backend
     * - E.g., Varnish, CDN edge caches
     */
    PROXY {
        public String getCharacteristics() {
            return """
                ✅ Transparent to application
                ✅ Protects backend
                ✅ Centralized invalidation
                ❌ Extra hop (latency)
                ❌ Single point of failure
                ❌ Limited cache control
                """;
        }
    };

    public abstract String getCharacteristics();
}
```

---

## Cache Consistency Patterns

🎓 **PROFESSOR**: The **hardest problem** in caching is **keeping cache and database consistent**:

```
┌────────────────────────────────────────────────┐
│ The Cache Consistency Problem                  │
│                                                │
│   Time  Cache    Database    Problem           │
│   t0    Val=1    Val=1       ✅ Consistent     │
│   t1    Val=1    Val=2       ❌ Stale cache!   │
│   t2    Val=2    Val=2       ✅ Consistent     │
└────────────────────────────────────────────────┘

How did we get inconsistent at t1?
1. Database updated to Val=2
2. Cache invalidation failed
3. Cache still has old Val=1
→ Stale data served to users! 💥
```

### Consistency Strategies

🏗️ **ARCHITECT**: **Four approaches to cache consistency**:

#### 1. **Time-To-Live (TTL)**

```java
/**
 * Simplest approach: Expire cache after time
 * Eventual consistency guaranteed
 */
public class TTLConsistency {

    @Autowired
    private Cache cache;

    public void updateUser(User user) {
        // 1. Update database
        database.save(user);

        // 2. Set cache with TTL
        cache.set(
            "user:" + user.getId(),
            user,
            Duration.ofMinutes(5)  // Expire after 5 minutes
        );

        // Worst case staleness: 5 minutes
    }
}
```

**Pros & Cons:**
```
✅ Simple to implement
✅ Guaranteed eventual consistency
✅ Self-healing (old data expires)

❌ Stale data possible (up to TTL)
❌ Cache misses on expiration
❌ Thundering herd on expiration
```

#### 2. **Invalidate-on-Write**

```java
/**
 * Delete cache entry when database updates
 * Strong consistency if done correctly
 */
public class InvalidateOnWrite {

    @Autowired
    private Cache cache;

    @Transactional
    public void updateUser(User user) {
        String key = "user:" + user.getId();

        // ❌ BAD ORDER: Cache invalidation before DB update
        // cache.delete(key);
        // database.save(user);
        // Problem: Concurrent read might cache old value!

        // ✅ GOOD ORDER: DB update before cache invalidation
        database.save(user);
        cache.delete(key);

        // Next read will cache miss → reload from DB
    }
}
```

**Race Condition Problem:**
```text
Thread 1 (Write)          Thread 2 (Read)
─────────────────         ───────────────
DELETE cache
                          READ cache (miss)
                          READ DB (old!)
UPDATE DB
                          WRITE cache (old!) ← BUG!

Result: Stale value in cache! 💥
```

**Solution: Delete After Write (Cache-Aside)**
```java
public class SafeInvalidation {

    @Transactional
    public void updateUser(User user) {
        String key = "user:" + user.getId();

        // 1. Update DB first
        database.save(user);

        // 2. Then delete cache
        cache.delete(key);

        // 3. Optional: Double-delete after delay
        scheduledExecutor.schedule(() -> {
            cache.delete(key);  // Catch any race conditions
        }, 1, TimeUnit.SECONDS);
    }
}
```

#### 3. **Write-Through + TTL**

```java
/**
 * Write to both cache and DB
 * TTL as safety net
 */
public class WriteThroughWithTTL {

    @Transactional
    public void updateUser(User user) {
        String key = "user:" + user.getId();

        // 1. Update database
        database.save(user);

        // 2. Update cache with TTL safety net
        cache.set(
            key,
            user,
            Duration.ofHours(1)  // Expire as safety
        );
    }
}
```

#### 4. **Event-Driven Invalidation**

```java
/**
 * Publish invalidation events
 * All cache instances react
 */
@Service
public class EventDrivenInvalidation {

    @Autowired
    private MessageBroker broker;

    @Autowired
    private Cache localCache;

    @TransactionalEventListener
    public void onUserUpdated(UserUpdatedEvent event) {
        // 1. Publish invalidation event
        broker.publish("cache.invalidate", new CacheInvalidationEvent(
            "user:" + event.getUserId()
        ));
    }

    @EventListener
    public void onCacheInvalidation(CacheInvalidationEvent event) {
        // 2. All instances invalidate their local cache
        localCache.delete(event.getKey());

        log.info("Invalidated cache key: {}", event.getKey());
    }
}
```

**Consistency vs Performance Trade-offs:**

```python
class ConsistencyStrategy:
    """
    Choose consistency strategy based on requirements
    """
    def recommend(self, requirements: Requirements) -> str:
        # Financial data: strong consistency required
        if requirements.domain == 'FINANCIAL':
            return "WRITE_THROUGH (no stale data allowed)"

        # Real-time leaderboard: consistency critical
        if requirements.requires_strong_consistency:
            return "INVALIDATE_ON_WRITE + Event-Driven"

        # Product catalog: eventual consistency okay
        if requirements.staleness_tolerance > 60:  # seconds
            return "TTL (simple, good enough)"

        # Social media feed: stale okay, performance critical
        if requirements.performance_critical:
            return "TTL with long expiration + background refresh"

        # Default: balanced approach
        return "INVALIDATE_ON_WRITE + TTL safety net"
```

---

## Cache Invalidation Strategies

🎓 **PROFESSOR**: Phil Karlton's famous quote:

> "There are only two hard things in Computer Science: cache invalidation and naming things."

**Invalidation Challenges:**

```
┌────────────────────────────────────────────────┐
│ 1. CACHE STAMPEDE (Thundering Herd)            │
│    → Popular key expires                       │
│    → 1000s of requests hit DB simultaneously   │
│                                                │
│ 2. STALE DATA                                  │
│    → Cache not invalidated on update           │
│    → Users see old data                        │
│                                                │
│ 3. CASCADING FAILURES                          │
│    → Cache down → all traffic to DB            │
│    → DB overload → entire system down          │
└────────────────────────────────────────────────┘
```

### Preventing Thundering Herd

```java
/**
 * Protect against cache stampede
 */
public class ThunderingHerdProtection {

    private final Cache cache;
    private final LoadingCache<String, CompletableFuture<User>> inflightRequests;

    public ThunderingHerdProtection() {
        // Track in-flight DB requests
        this.inflightRequests = Caffeine.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();
    }

    /**
     * Get with stampede protection
     */
    public User getUser(Long userId) {
        String key = "user:" + userId;

        // 1. Try cache
        User cached = cache.get(key);
        if (cached != null) {
            return cached;
        }

        // 2. Cache miss - check if someone else is loading
        CompletableFuture<User> inflight = inflightRequests.getIfPresent(key);
        if (inflight != null) {
            // Wait for in-flight request to complete
            try {
                return inflight.get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout or error, load ourselves
            }
        }

        // 3. We're first to request - load from DB
        CompletableFuture<User> future = new CompletableFuture<>();
        inflightRequests.put(key, future);

        try {
            User user = database.findById(userId);

            // 4. Populate cache
            cache.set(key, user, Duration.ofMinutes(5));

            // 5. Notify waiters
            future.complete(user);

            return user;

        } catch (Exception e) {
            future.completeExceptionally(e);
            throw e;

        } finally {
            // 6. Remove from in-flight
            inflightRequests.invalidate(key);
        }
    }
}
```

### Probabilistic Early Expiration

```java
/**
 * XFetch algorithm: Probabilistic early recomputation
 * Prevents thundering herd by spreading expiration
 */
public class XFetchCache {

    private final Cache cache;
    private final Random random = new Random();

    public User getUser(Long userId) {
        String key = "user:" + userId;

        CacheEntry<User> entry = cache.getEntry(key);
        if (entry == null) {
            return loadAndCache(userId);
        }

        // Calculate probability of early refresh
        long now = System.currentTimeMillis();
        long expiresAt = entry.getExpirationTime();
        long timeToExpire = expiresAt - now;
        long ttl = entry.getTTL();

        // XFetch formula: early_refresh_prob = -log(rand) * timeToExpire / ttl
        double beta = 1.0;  // Tuning parameter
        double earlyRefreshProb = -Math.log(random.nextDouble()) * timeToExpire / (beta * ttl);

        if (earlyRefreshProb > 1.0) {
            // Refresh early (probabilistic)
            refreshAsync(userId, key);
        }

        return entry.getValue();
    }

    private void refreshAsync(Long userId, String key) {
        CompletableFuture.runAsync(() -> {
            User fresh = database.findById(userId);
            cache.set(key, fresh, Duration.ofMinutes(5));
        });
    }
}
```

### Cache Warming

```java
/**
 * Proactively populate cache (avoid cold start)
 */
@Component
public class CacheWarmer {

    @Autowired
    private Cache cache;

    @Autowired
    private Database database;

    /**
     * Warm cache on startup
     */
    @PostConstruct
    public void warmCache() {
        log.info("Warming cache...");

        // Load hot data into cache
        List<Long> hotUserIds = database.getTopUsers(10_000);

        hotUserIds.parallelStream().forEach(userId -> {
            try {
                User user = database.findById(userId);
                cache.set("user:" + userId, user, Duration.ofHours(1));
            } catch (Exception e) {
                log.warn("Failed to warm cache for user: {}", userId, e);
            }
        });

        log.info("Cache warming complete: {} entries", hotUserIds.size());
    }

    /**
     * Continuous background warming
     */
    @Scheduled(fixedDelay = 300_000)  // Every 5 minutes
    public void refreshHotData() {
        List<Long> hotUserIds = database.getTopUsers(1000);

        hotUserIds.forEach(userId -> {
            // Refresh in background
            CompletableFuture.runAsync(() -> {
                User user = database.findById(userId);
                cache.set("user:" + userId, user, Duration.ofHours(1));
            });
        });
    }
}
```

---

## Failure Modes & Edge Cases

🎓 **PROFESSOR**: **Distributed caches fail in predictable ways**:

```
┌─────────────────────────────────────────────┐
│ 1. CACHE OUTAGE                             │
│    → All traffic to DB (overload)          │
│                                             │
│ 2. CACHE POLLUTION                          │
│    → Useless data evicts hot data          │
│                                             │
│ 3. HOT KEY                                  │
│    → Single key overloads one cache node   │
│                                             │
│ 4. BIG KEY                                  │
│    → Large value causes memory issues      │
│                                             │
│ 5. SERIALIZATION BUGS                       │
│    → Corrupt data in cache                 │
└─────────────────────────────────────────────┘
```

### 1. Cache Failure Resilience

```java
/**
 * Gracefully degrade when cache fails
 */
@Service
public class ResilientCaching {

    @Autowired
    private Cache cache;

    @Autowired
    private Database database;

    @CircuitBreaker(name = "cache", fallbackMethod = "getFromDatabaseDirectly")
    @Retry(name = "cache", maxAttempts = 2)
    public User getUser(Long userId) {
        String key = "user:" + userId;

        try {
            // Try cache first
            User cached = cache.get(key);
            if (cached != null) {
                return cached;
            }

            // Cache miss - load from DB
            User user = database.findById(userId);
            cache.set(key, user, Duration.ofMinutes(5));

            return user;

        } catch (CacheException e) {
            // Cache failure - fallback to DB
            log.warn("Cache failure, falling back to database", e);
            return getFromDatabaseDirectly(userId, e);
        }
    }

    /**
     * Fallback when cache fails (circuit breaker open)
     */
    public User getFromDatabaseDirectly(Long userId, Exception e) {
        log.warn("Circuit breaker open, bypassing cache", e);

        // Go directly to database
        return database.findById(userId);
    }
}
```

### 2. Hot Key Mitigation

```java
/**
 * Detect and handle hot keys
 */
public class HotKeyHandler {

    private final LoadingCache<String, AtomicLong> requestCounter;
    private final int hotKeyThreshold = 10_000;  // req/sec

    public HotKeyHandler() {
        // Track request frequency
        this.requestCounter = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .build(key -> new AtomicLong(0));
    }

    public User getUser(Long userId) {
        String key = "user:" + userId;

        // Track request count
        long count = requestCounter.get(key).incrementAndGet();

        if (count > hotKeyThreshold) {
            // Hot key detected!
            return getWithHotKeyOptimization(userId, key);
        }

        // Normal path
        return cache.get(key);
    }

    /**
     * Optimizations for hot keys
     */
    private User getWithHotKeyOptimization(Long userId, String key) {
        // Strategy 1: Local caching (L1 cache)
        User localCached = localCache.getIfPresent(key);
        if (localCached != null) {
            return localCached;
        }

        // Strategy 2: Replicate hot key across multiple cache nodes
        List<String> replicaKeys = List.of(
            key + ":replica:0",
            key + ":replica:1",
            key + ":replica:2"
        );

        // Random load balancing across replicas
        String replicaKey = replicaKeys.get(random.nextInt(replicaKeys.size()));
        User user = cache.get(replicaKey);

        // Populate local cache
        localCache.put(key, user);

        return user;
    }
}
```

### 3. Big Key Problems

```java
/**
 * Handle large cache values
 */
public class BigKeyHandler {

    private static final int MAX_VALUE_SIZE = 1_000_000;  // 1MB

    public void set(String key, Object value, Duration ttl) {
        // Serialize to check size
        byte[] serialized = serialize(value);

        if (serialized.length > MAX_VALUE_SIZE) {
            // Value too large - handle specially
            handleBigValue(key, value, ttl);
        } else {
            // Normal caching
            cache.set(key, value, ttl);
        }
    }

    private void handleBigValue(String key, Object value, Duration ttl) {
        // Strategy 1: Compress
        byte[] compressed = compress(serialize(value));
        if (compressed.length <= MAX_VALUE_SIZE) {
            cache.set(key, compressed, ttl);
            return;
        }

        // Strategy 2: Chunk into smaller pieces
        List<byte[]> chunks = chunk(compressed, MAX_VALUE_SIZE);
        for (int i = 0; i < chunks.size(); i++) {
            cache.set(key + ":chunk:" + i, chunks.get(i), ttl);
        }
        cache.set(key + ":chunks", chunks.size(), ttl);

        // Strategy 3: Don't cache (too big)
        log.warn("Value too large to cache: {} bytes", compressed.length);
    }

    public Object get(String key) {
        // Check if chunked
        Integer chunkCount = cache.get(key + ":chunks");
        if (chunkCount != null) {
            return getChunked(key, chunkCount);
        }

        // Normal get
        return cache.get(key);
    }

    private Object getChunked(String key, int chunkCount) {
        // Reassemble chunks
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (int i = 0; i < chunkCount; i++) {
            byte[] chunk = cache.get(key + ":chunk:" + i);
            baos.write(chunk, 0, chunk.length);
        }

        byte[] compressed = baos.toByteArray();
        byte[] decompressed = decompress(compressed);
        return deserialize(decompressed);
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Functional:
- What data to cache? (query results, objects, pages)
- What operations? (GET, SET, DELETE, increment/decrement)
- Cache invalidation? (TTL, manual, event-driven)
- Data types? (strings, objects, lists, sets)

Non-Functional:
- Scale: X reads/sec, Y writes/sec
- Latency: P99 < 1ms
- Hit ratio: Target 90%+
- Availability: 99.99%
- Data size: average value size, total cache size
```

### 2. Capacity Estimation (RADIO: Scale)

```
Example: E-commerce Product Cache

Traffic:
- 100K product page views/sec
- 80% cache hit ratio (target)
- Cache hits: 80K/sec
- Cache misses: 20K/sec (DB load)

Cache Size:
- Total products: 10M
- Hot products (80/20 rule): 2M products
- Average product size: 5KB
- Cache size needed: 2M * 5KB = 10GB

Memory per node:
- Cache servers: 5 nodes
- RAM per node: 16GB (10GB cache + 6GB overhead)

Network:
- Cache hit: 5KB
- Bandwidth: 80K/sec * 5KB = 400MB/sec = 3.2Gbps
- Need 10Gbps NICs

Eviction rate:
- Cache capacity: 2M items
- New items/day: 100K
- Eviction: ~5% per day
```

### 3. Cache Architecture (RADIO: Data-Model)

```java
/**
 * Cached data model
 */
public class CachedProduct {

    // Cache key design: "product:{productId}"
    private String cacheKey;

    // Cached value
    @Serializable
    public static class ProductValue {
        private Long productId;
        private String name;
        private BigDecimal price;
        private Integer stockQuantity;
        private String imageUrl;
        private Instant cachedAt;      // Track freshness
        private Long version;           // Optimistic locking

        // Don't cache large/changing data
        // private List<Review> reviews;  ❌ Too large
        // private Integer viewCount;     ❌ Changes too frequently
    }

    // Cache metadata
    private Duration ttl = Duration.ofMinutes(15);
    private CacheTier tier;  // L1, L2, L3

    /**
     * Determine what to cache
     */
    public boolean shouldCache(Product product) {
        // Don't cache unpopular products
        if (product.getViewsPerDay() < 10) {
            return false;
        }

        // Don't cache if frequently updated
        if (product.getUpdatesPerHour() > 10) {
            return false;
        }

        // Don't cache if too large
        if (estimateSize(product) > 100_000) {  // 100KB
            return false;
        }

        return true;
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────┐
│            APPLICATION LAYER                     │
│                                                  │
│  ┌──────────────────────────────────────────┐  │
│  │  L1: Local Cache (Caffeine)              │  │
│  │  - Size: 10K items                       │  │
│  │  - TTL: 5 minutes                        │  │
│  │  - Hit Ratio: ~60%                       │  │
│  └───────────────┬──────────────────────────┘  │
└──────────────────┼──────────────────────────────┘
                   ↓ (40% miss)
┌─────────────────────────────────────────────────┐
│  L2: Distributed Cache (Redis Cluster)          │
│                                                  │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Primary  │  │ Primary  │  │ Primary  │      │
│  │ Shard 0  │  │ Shard 1  │  │ Shard 2  │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│       ↓             ↓             ↓             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Replica  │  │ Replica  │  │ Replica  │      │
│  └──────────┘  └──────────┘  └──────────┘      │
│                                                  │
│  - Size: 2M items total                         │
│  - TTL: 15 minutes                              │
│  - Hit Ratio: ~30%                              │
└──────────────────┬──────────────────────────────┘
                   ↓ (10% miss)
┌─────────────────────────────────────────────────┐
│            DATABASE (PostgreSQL)                 │
│  - Read Replicas: 3                             │
│  - Connection Pool: 100                          │
└─────────────────────────────────────────────────┘

Overall Hit Ratio: 60% (L1) + 30% (L2) = 90% ✅
Database Load: 10% of traffic ✅
```

### 5. Deep Dives (RADIO: Optimize)

**A. Cache Key Design**

```java
/**
 * Hierarchical cache key design
 */
public class CacheKeyDesign {

    /**
     * Good key design principles:
     * 1. Namespace to avoid collisions
     * 2. Include version for schema changes
     * 3. Human-readable for debugging
     */

    // ✅ GOOD: Clear, namespaced, versioned
    public static String productKey(Long productId) {
        return "v1:product:" + productId;
        // Examples: "v1:product:12345"
    }

    // ✅ GOOD: Composite key for relationships
    public static String userOrdersKey(Long userId) {
        return "v1:user:" + userId + ":orders";
        // Examples: "v1:user:789:orders"
    }

    // ✅ GOOD: Include filter params in key
    public static String productSearchKey(String query, String category, int page) {
        return String.format("v1:search:%s:cat:%s:page:%d",
            query, category, page);
        // Examples: "v1:search:laptop:cat:electronics:page:1"
    }

    // ❌ BAD: No namespace, hard to debug
    // public static String badKey(Long id) {
    //     return String.valueOf(id);  // "12345" - what is this?
    // }

    // ❌ BAD: No version, can't migrate
    // public static String badKey2(Long id) {
    //     return "product:" + id;  // Can't change schema!
    // }
}
```

**B. Monitoring & Metrics**

```java
@Component
public class CacheMetrics {

    @Autowired
    private MeterRegistry registry;

    public void recordCacheAccess(String key, boolean hit) {
        // 1. Hit/Miss counter
        registry.counter("cache.requests",
            "result", hit ? "hit" : "miss",
            "tier", "L2"
        ).increment();

        // 2. Hit ratio gauge
        updateHitRatio(hit);
    }

    public void recordLatency(String operation, Duration latency) {
        // 3. Operation latency
        registry.timer("cache.operation.latency",
            "operation", operation
        ).record(latency);
    }

    public void recordEviction(String reason) {
        // 4. Eviction counter
        registry.counter("cache.evictions",
            "reason", reason  // size, ttl, explicit
        ).increment();
    }

    public void recordMemoryUsage(long bytes) {
        // 5. Memory usage
        registry.gauge("cache.memory.bytes", bytes);
    }

    /**
     * Key metrics to monitor:
     * - cache.hit_ratio (target: > 90%)
     * - cache.operation.latency P99 (target: < 1ms)
     * - cache.evictions (should be low)
     * - cache.memory.usage (% of capacity)
     */
}
```

**C. Advanced Patterns**

```java
/**
 * Negative caching: Cache misses to reduce DB load
 */
public class NegativeCaching {

    public Product getProduct(Long productId) {
        String key = "product:" + productId;

        // Check cache
        CachedValue<Product> cached = cache.get(key);
        if (cached != null) {
            if (cached.isNull()) {
                // Cached negative result
                return null;
            }
            return cached.getValue();
        }

        // Query database
        Product product = database.findById(productId);

        if (product != null) {
            // Cache positive result
            cache.set(key, CachedValue.of(product), Duration.ofMinutes(15));
        } else {
            // Cache negative result (shorter TTL)
            cache.set(key, CachedValue.ofNull(), Duration.ofMinutes(1));
        }

        return product;
    }
}

/**
 * Cache-aside with write coalescing
 */
public class WriteCoalescing {

    private final BlockingQueue<WriteOperation> writeQueue;

    @PostConstruct
    public void startBatchWriter() {
        // Background thread batches cache writes
        executor.submit(() -> {
            while (true) {
                List<WriteOperation> batch = new ArrayList<>();
                writeQueue.drainTo(batch, 100);

                if (!batch.isEmpty()) {
                    // Batch write to cache (1 network call)
                    cache.multiSet(batch);
                }

                Thread.sleep(100);  // Batch every 100ms
            }
        });
    }

    public void updateProduct(Product product) {
        // 1. Update database
        database.save(product);

        // 2. Queue cache update (async)
        writeQueue.offer(new WriteOperation(
            "product:" + product.getId(),
            product
        ));
    }
}
```

---

## 🧠 MIND MAP: DISTRIBUTED CACHING CONCEPTS

```
         DISTRIBUTED
           CACHING
              |
      ┌───────┴────────┐
      ↓                ↓
  PATTERNS        CHALLENGES
      |                |
  ┌───┴───┐        ┌───┴────┐
  ↓       ↓        ↓        ↓
CACHE   WRITE   CONSIST  FAILURE
ASIDE   THROUGH   ENCY    MODES
  |       |        |        |
LAZY   SYNC    TTL    STAMPEDE
LOAD   WRITE  INVAL   HOT_KEY
  |       |      |        |
MISS  ALWAYS EVENTS  BIG_KEY
PENALTY FRESH  RACE   POLLUTION
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **Cache = Notebook on Desk 📓**
- Frequently used info written in notebook (fast access)
- Occasionally look up in textbook (slow DB access)
- Notebook might have outdated info (staleness)
- Erase old notes when full (eviction)

### 2. **Cache Invalidation = News Updates 📰**
- Breaking news: old headlines wrong (invalidation needed)
- Wait for next edition (TTL expiration)
- Special bulletin (event-driven invalidation)
- Old news still visible until update (stale data)

### 3. **Thundering Herd = Black Friday Sale 🏬**
- Store opens (cache expires)
- Everyone rushes in at once (stampede)
- Clerks overwhelmed (DB overload)
- Solution: Ticket system (request coalescing)

### 4. **Hot Key = Celebrity Cashier ⭐**
- One cashier is famous (hot key)
- Everyone lines up at that register (imbalanced load)
- Other registers empty (wasted capacity)
- Solution: Multiple registers for celebrity (replication)

### 5. **Cache Consistency = Synchronized Watches ⌚**
- Everyone's watch should show same time (consistency)
- Watches drift (staleness)
- Periodically sync with atomic clock (TTL refresh)
- Immediate sync after time change (invalidation)

### 6. **Write-Through = Double-Entry Bookkeeping 📒**
- Write transaction in TWO ledgers (cache + DB)
- Slower but always consistent
- Can't have discrepancy
- Both fail or both succeed

### 7. **Write-Behind = Taking Notes in Class 📝**
- Scribble notes quickly during lecture (cache)
- Organize into clean notes later (async DB write)
- Fast note-taking but might lose if notebook lost
- Batch organize for efficiency

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Caching:**

1. **Cache Only What's Worth Caching**
   - High read-to-write ratio (10:1 or higher)
   - Expensive to compute/fetch
   - Frequently accessed (80/20 rule)

2. **Consistency is a Spectrum**
   - Not all data needs strong consistency
   - Financial: strong consistency (write-through)
   - Social media: eventual consistency (TTL)
   - Choose per use case

3. **Cache Failure is Not System Failure**
   - Design for cache to fail gracefully
   - Circuit breakers protect database
   - Degraded performance, not outage

---

## 🏗️ ARCHITECT'S CHECKLIST

Before deploying distributed cache:

- [ ] **Cache Policy**: What to cache? TTL strategy?
- [ ] **Key Design**: Namespaced? Versioned? Human-readable?
- [ ] **Consistency**: TTL? Invalidation? Event-driven?
- [ ] **Eviction**: LRU? LFU? Size limits?
- [ ] **Monitoring**: Hit ratio? Latency? Memory usage?
- [ ] **Failure Handling**: Circuit breakers? Fallback to DB?
- [ ] **Hot Keys**: Detection? Mitigation strategy?
- [ ] **Serialization**: Efficient? Versioned schema?
- [ ] **Security**: Encrypted? Access control?
- [ ] **Capacity Planning**: Growth projection? Scaling plan?

---

**Interview Closer**: "I'd implement a two-tier caching strategy: L1 local cache (Caffeine) for ultra-low latency, L2 distributed cache (Redis) for shared state. Use cache-aside pattern with TTL for simplicity and eventual consistency. Monitor hit ratio closely - target 90%+. Implement circuit breakers so cache failures don't cascade to database. Let me draw the architecture..."
