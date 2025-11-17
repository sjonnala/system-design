# Redis vs. Memcached Deep Dive

## Contents

- [Redis vs. Memcached Deep Dive](#redis-vs-memcached-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Architecture Comparison](#architecture-comparison)
    - [Data Structures & Features](#data-structures--features)
    - [Performance Characteristics](#performance-characteristics)
    - [Persistence & Durability](#persistence--durability)
    - [Scaling & High Availability](#scaling--high-availability)
    - [Use Case Selection](#use-case-selection)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Technology Selection (RADIO: Data-Model)](#3-technology-selection-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: REDIS VS MEMCACHED](#mind-map-redis-vs-memcached)

## Core Mental Model

```text
Memcached = Simple, Pure Cache (Key-Value only)
Redis = Cache + Database + Message Broker (Swiss Army Knife)

The Fundamental Trade-off:
--------------------------
Simplicity ←→ Features
Memcached: Extremely simple, does ONE thing well
Redis: Feature-rich, does MANY things well
```

**High-Level Comparison:**
```
┌──────────────────────────────────────────────────┐
│         Memcached    vs    Redis                 │
│                                                  │
│ Created:     2003            2009                │
│ Language:    C               C                   │
│ Model:       Key-Value       Data Structures     │
│ Threading:   Multi-thread    Single-thread       │
│ Persistence: None            Optional (RDB/AOF)  │
│ Replication: None            Built-in            │
│ Clustering:  Client-side     Built-in (Redis)    │
│ Data Types:  Strings only    Strings, Lists,     │
│                              Sets, Hashes, etc.  │
│ Pub/Sub:     No              Yes                 │
│ Lua Scripts: No              Yes                 │
│ Eviction:    LRU             8 policies          │
│ Max Key:     250 bytes       512 MB              │
│ Max Value:   1 MB            512 MB              │
└──────────────────────────────────────────────────┘
```

**When to Use Which:**
```
Use Memcached when:
✅ Simple key-value caching
✅ Multi-core scaling important
✅ Minimal memory overhead
✅ Simplicity over features

Use Redis when:
✅ Need data structures (lists, sets, sorted sets)
✅ Need persistence
✅ Need pub/sub messaging
✅ Need atomic operations
✅ Need replication/clustering
✅ Need Lua scripting
```

**Visual Model:**
```text
MEMCACHED: Simple Cache
┌─────────────────────────┐
│   SET key value         │ ─→ Hash Table
│   GET key               │ ←─ Simple Lookup
│   DELETE key            │
└─────────────────────────┘
Features: ✅ Fast  ❌ Basic

REDIS: Multi-Purpose
┌─────────────────────────┐
│   String: SET key val   │
│   List: LPUSH key val   │ ─→ Multiple Data Structures
│   Set: SADD key val     │
│   Hash: HSET k f v      │
│   Sorted: ZADD k s v    │
│   PubSub: PUBLISH ch m  │ ←─ Advanced Features
│   Script: EVAL lua      │
└─────────────────────────┘
Features: ✅ Fast  ✅ Rich
```

**Key Insight**: **Memcached is a pure cache. Redis is a cache, database, and more**. Choose based on requirements, not just "caching."

---

## Architecture Comparison

🎓 **PROFESSOR**: **Fundamental architectural differences**:

### Threading Model

#### Memcached: Multi-Threaded

```text
┌─────────────────────────────────────────┐
│         Memcached Architecture          │
│                                         │
│  ┌────────┐  ┌────────┐  ┌────────┐   │
│  │Thread 1│  │Thread 2│  │Thread 3│   │
│  │(CPU 1) │  │(CPU 2) │  │(CPU 3) │   │
│  └───┬────┘  └───┬────┘  └───┬────┘   │
│      │           │           │         │
│      └───────────┼───────────┘         │
│                  ↓                      │
│      ┌───────────────────────┐         │
│      │   Shared Hash Table   │         │
│      │   (Lock-based)        │         │
│      └───────────────────────┘         │
└─────────────────────────────────────────┘

Pros:
✅ Utilizes multiple CPU cores
✅ Higher throughput on multi-core systems
✅ Better for CPU-bound workloads

Cons:
❌ Lock contention overhead
❌ More complex code
❌ Context switching overhead
```

#### Redis: Single-Threaded (Event Loop)

```text
┌─────────────────────────────────────────┐
│           Redis Architecture             │
│                                          │
│  ┌────────────────────────────────────┐ │
│  │   Single Thread (Event Loop)       │ │
│  │                                    │ │
│  │   while (true) {                   │ │
│  │     events = epoll_wait();         │ │
│  │     for (event : events) {         │ │
│  │       process(event);              │ │
│  │     }                              │ │
│  │   }                                │ │
│  └────────────────────────────────────┘ │
│                  ↓                       │
│      ┌───────────────────────┐          │
│      │   Data Structures     │          │
│      │   (No locks needed!)  │          │
│      └───────────────────────┘          │
│                                          │
│  Note: Redis 6.0+ has I/O threads       │
│  for reading/writing network buffers    │
└─────────────────────────────────────────┘

Pros:
✅ No lock overhead
✅ Simple, predictable performance
✅ Atomic operations (no race conditions)

Cons:
❌ Single CPU core (mostly)
❌ Long-running commands block everything
❌ CPU-bound operations slow
```

🏗️ **ARCHITECT**: **Performance implications**:

```java
/**
 * Memcached: Multi-threaded access
 */
public class MemcachedClient {

    private final MemcachedClient client;

    /**
     * Multiple threads can process concurrently
     * Good for: Many concurrent connections
     */
    public String get(String key) {
        // Thread 1, 2, 3... all execute in parallel
        // Memcached handles concurrency internally
        return client.get(key);
    }

    /**
     * Benchmark: 4-core server
     * - Throughput: 1M ops/sec
     * - Scales linearly with cores
     */
}

/**
 * Redis: Single-threaded processing
 */
public class RedisClient {

    private final Jedis redis;

    /**
     * Commands processed sequentially
     * Good for: Fast, atomic operations
     */
    public String get(String key) {
        // All commands queued and processed sequentially
        // No race conditions possible
        return redis.get(key);
    }

    /**
     * Benchmark: 4-core server
     * - Throughput: 100K ops/sec (single instance)
     * - Doesn't scale with cores (need multiple instances)
     * - But: Can use pipelining for 10x improvement
     */
}
```

### Memory Management

#### Memcached: Slab Allocator

```text
┌─────────────────────────────────────────────┐
│     Memcached Slab Allocation               │
│                                             │
│  Memory divided into fixed-size slabs:     │
│                                             │
│  Slab 1: 96 bytes   ─→ Items 89-96 bytes   │
│  Slab 2: 120 bytes  ─→ Items 97-120 bytes  │
│  Slab 3: 152 bytes  ─→ Items 121-152 bytes │
│  ...                                        │
│  Slab N: 1 MB       ─→ Items up to 1 MB    │
│                                             │
│  Pros:                                      │
│  ✅ No fragmentation                        │
│  ✅ Predictable allocation                  │
│  ✅ Fast allocation/deallocation            │
│                                             │
│  Cons:                                      │
│  ❌ Internal fragmentation (waste space)    │
│  ❌ Slab rebalancing needed                 │
└─────────────────────────────────────────────┘

Example:
Item size: 100 bytes
Allocated slab: 120 bytes
Wasted: 20 bytes (16% overhead)
```

#### Redis: jemalloc

```text
┌─────────────────────────────────────────────┐
│         Redis Memory Management             │
│                                             │
│  Uses jemalloc (efficient general allocator)│
│                                             │
│  Pros:                                      │
│  ✅ Low fragmentation                       │
│  ✅ Works well for variable sizes           │
│  ✅ Good performance                        │
│                                             │
│  Cons:                                      │
│  ❌ More complex than slab                  │
│  ❌ Small overhead per allocation           │
│                                             │
│  Memory overhead:                           │
│  - 24 bytes per key (metadata)             │
│  - 8 bytes per value pointer               │
│  - ~32 bytes total per key-value           │
└─────────────────────────────────────────────┘
```

**Memory Efficiency Comparison:**

```python
# Calculate memory overhead

# Memcached
def memcached_memory(key_size, value_size):
    # Find slab size (next power of 1.25)
    slab_size = next_slab_size(key_size + value_size + 48)  # 48 byte header
    overhead = slab_size - (key_size + value_size)
    return {
        'total': slab_size,
        'overhead': overhead,
        'overhead_percent': overhead / slab_size * 100
    }

# Redis
def redis_memory(key_size, value_size):
    metadata = 32  # dictEntry overhead
    total = key_size + value_size + metadata
    return {
        'total': total,
        'overhead': metadata,
        'overhead_percent': metadata / total * 100
    }

# Example: 20 byte key, 100 byte value
memcached = memcached_memory(20, 100)
# Total: 192 bytes (slab), Overhead: 72 bytes (37%)

redis = redis_memory(20, 100)
# Total: 152 bytes, Overhead: 32 bytes (21%)

# Winner: Redis (lower overhead for this case)
```

---

## Data Structures & Features

🎓 **PROFESSOR**: **The key differentiator**:

### Memcached: Strings Only

```java
/**
 * Memcached: Simple key-value
 */
public class MemcachedOperations {

    private final MemcachedClient client;

    /**
     * SET: Store string value
     */
    public void set(String key, String value, int ttl) {
        client.set(key, ttl, value);
    }

    /**
     * GET: Retrieve string value
     */
    public String get(String key) {
        return client.get(key);
    }

    /**
     * DELETE: Remove key
     */
    public void delete(String key) {
        client.delete(key);
    }

    /**
     * INCR/DECR: Atomic increment
     */
    public long incr(String key, int delta) {
        return client.incr(key, delta);
    }

    /**
     * CAS: Compare-and-swap
     */
    public boolean cas(String key, String value, long casId) {
        return client.cas(key, casId, value);
    }

    /**
     * That's it! No other data types.
     * For complex data, serialize to JSON/Protobuf
     */

    // Example: Store user object
    public void storeUser(User user) {
        String json = objectMapper.writeValueAsString(user);
        client.set("user:" + user.getId(), 3600, json);
    }

    // Must deserialize every time
    public User getUser(Long userId) {
        String json = client.get("user:" + userId);
        return objectMapper.readValue(json, User.class);
    }
}
```

### Redis: Rich Data Structures

```java
/**
 * Redis: Multiple data types
 */
public class RedisOperations {

    private final Jedis redis;

    // ============ STRINGS ============
    public void setString(String key, String value) {
        redis.set(key, value);
        redis.expire(key, 3600);
    }

    public String getString(String key) {
        return redis.get(key);
    }

    // ============ LISTS (Ordered) ============
    /**
     * Use case: Message queue, activity feed
     */
    public void pushToList(String key, String... values) {
        redis.lpush(key, values);  // Left push
        redis.ltrim(key, 0, 999);  // Keep only 1000 items
    }

    public List<String> getList(String key, int start, int end) {
        return redis.lrange(key, start, end);
    }

    // Example: Recent activity feed
    public void addActivity(Long userId, String activity) {
        String key = "feed:" + userId;
        redis.lpush(key, activity);
        redis.ltrim(key, 0, 99);  // Keep last 100 activities
    }

    // ============ SETS (Unordered, Unique) ============
    /**
     * Use case: Tags, unique visitors, relationships
     */
    public void addToSet(String key, String... members) {
        redis.sadd(key, members);
    }

    public Set<String> getSet(String key) {
        return redis.smembers(key);
    }

    public boolean isMember(String key, String member) {
        return redis.sismember(key, member);
    }

    // Example: User's followers
    public void follow(Long followerId, Long followeeId) {
        redis.sadd("followers:" + followeeId, String.valueOf(followerId));
        redis.sadd("following:" + followerId, String.valueOf(followeeId));
    }

    public Set<String> getCommonFollowers(Long user1, Long user2) {
        return redis.sinter(
            "followers:" + user1,
            "followers:" + user2
        );
    }

    // ============ HASHES (Field-Value pairs) ============
    /**
     * Use case: Objects, counters per field
     */
    public void setHash(String key, Map<String, String> fields) {
        redis.hset(key, fields);
    }

    public String getHashField(String key, String field) {
        return redis.hget(key, field);
    }

    public Map<String, String> getHash(String key) {
        return redis.hgetAll(key);
    }

    // Example: User object (no serialization needed!)
    public void storeUser(User user) {
        String key = "user:" + user.getId();
        Map<String, String> fields = Map.of(
            "name", user.getName(),
            "email", user.getEmail(),
            "age", String.valueOf(user.getAge())
        );
        redis.hset(key, fields);
    }

    // Get single field without loading entire object
    public String getUserName(Long userId) {
        return redis.hget("user:" + userId, "name");
    }

    // ============ SORTED SETS (Ordered by score) ============
    /**
     * Use case: Leaderboards, priority queues, time-series
     */
    public void addToSortedSet(String key, double score, String member) {
        redis.zadd(key, score, member);
    }

    public List<String> getTopN(String key, int n) {
        // Get top N by score (descending)
        return redis.zrevrange(key, 0, n - 1);
    }

    public Long getRank(String key, String member) {
        return redis.zrevrank(key, member);  // Rank (0-based)
    }

    // Example: Game leaderboard
    public void updateScore(Long userId, int score) {
        redis.zadd("leaderboard", score, String.valueOf(userId));
    }

    public List<String> getTopPlayers(int count) {
        return redis.zrevrange("leaderboard", 0, count - 1);
    }

    public Long getPlayerRank(Long userId) {
        Long rank = redis.zrevrank("leaderboard", String.valueOf(userId));
        return rank != null ? rank + 1 : null;  // 1-based
    }

    // ============ BITMAPS ============
    /**
     * Use case: Boolean flags, presence tracking
     */
    public void setBit(String key, long offset, boolean value) {
        redis.setbit(key, offset, value);
    }

    public boolean getBit(String key, long offset) {
        return redis.getbit(key, offset);
    }

    public long countBits(String key) {
        return redis.bitcount(key);
    }

    // Example: Daily active users
    public void markUserActive(Long userId, LocalDate date) {
        String key = "active:" + date;
        redis.setbit(key, userId, true);
        redis.expire(key, 86400 * 30);  // Keep 30 days
    }

    public long getDailyActiveUsers(LocalDate date) {
        return redis.bitcount("active:" + date);
    }

    // ============ HYPERLOGLOGS ============
    /**
     * Use case: Cardinality estimation (unique counts)
     * Memory: Only 12 KB per key!
     */
    public void addToHyperLogLog(String key, String... elements) {
        redis.pfadd(key, elements);
    }

    public long getUniqueCount(String key) {
        return redis.pfcount(key);
    }

    // Example: Unique visitors (approximate)
    public void recordVisit(String visitorId, LocalDate date) {
        redis.pfadd("visitors:" + date, visitorId);
    }

    public long getUniqueVisitors(LocalDate date) {
        return redis.pfcount("visitors:" + date);
    }

    // ============ STREAMS ============
    /**
     * Use case: Event logging, message queue
     */
    public String addToStream(String stream, Map<String, String> fields) {
        return redis.xadd(stream, (StreamEntryID) null, fields);
    }

    public List<StreamEntry> readStream(String stream, int count) {
        return redis.xread(count, 0, Map.entry(stream, "0"));
    }

    // Example: Event log
    public void logEvent(String eventType, Map<String, String> data) {
        redis.xadd("events", StreamEntryID.NEW_ENTRY,
            Map.of("type", eventType, "data", data.toString())
        );
    }
}
```

**Feature Comparison:**

```
┌──────────────────────────────────────────────────────┐
│ Feature              Memcached    Redis              │
├──────────────────────────────────────────────────────┤
│ Strings              ✅           ✅                  │
│ Lists                ❌           ✅ (LPUSH, RPUSH)  │
│ Sets                 ❌           ✅ (SADD, SINTER)  │
│ Sorted Sets          ❌           ✅ (ZADD, ZRANGE)  │
│ Hashes               ❌           ✅ (HSET, HGET)    │
│ Bitmaps              ❌           ✅                  │
│ HyperLogLogs         ❌           ✅                  │
│ Streams              ❌           ✅                  │
│ Geospatial           ❌           ✅                  │
│                                                      │
│ Pub/Sub              ❌           ✅                  │
│ Lua Scripting        ❌           ✅                  │
│ Transactions         ❌           ✅ (MULTI/EXEC)    │
│ Pipelining           ✅           ✅                  │
│                                                      │
│ Persistence          ❌           ✅ (RDB, AOF)      │
│ Replication          ❌           ✅ (Built-in)      │
│ Clustering           ❌           ✅ (Redis Cluster) │
└──────────────────────────────────────────────────────┘
```

---

## Performance Characteristics

🎓 **PROFESSOR**: **Performance benchmarks and trade-offs**:

### Latency Comparison

```text
┌──────────────────────────────────────────────┐
│     Latency (P99) - Single Instance          │
│                                              │
│ Operation        Memcached    Redis          │
│ ────────────────────────────────────────────│
│ GET (string)     0.1-0.2 ms   0.1-0.3 ms    │
│ SET (string)     0.1-0.2 ms   0.1-0.3 ms    │
│ INCR             0.1-0.2 ms   0.1-0.3 ms    │
│                                              │
│ GET (1KB)        0.2-0.3 ms   0.2-0.4 ms    │
│ GET (10KB)       0.5-0.7 ms   0.5-0.8 ms    │
│ GET (100KB)      2-3 ms       2-4 ms        │
│                                              │
│ Pipeline (100)   0.5-1 ms     0.5-1 ms      │
└──────────────────────────────────────────────┘

Note: Similar latency for basic operations
Redis overhead comes from richer features
```

### Throughput Comparison

```java
/**
 * Benchmark results (AWS c5.2xlarge: 8 vCPU, 16 GB RAM)
 */
public class PerformanceBenchmarks {

    /**
     * MEMCACHED (Multi-threaded)
     * - 1M SET/sec (4-8 threads optimal)
     * - 1.2M GET/sec
     * - Scales linearly with cores (up to 8)
     */
    public void memcachedBenchmark() {
        // redis-benchmark equivalent for Memcached
        // 8 threads, 50 connections each
        // Result: ~1M SET/sec, ~1.2M GET/sec
    }

    /**
     * REDIS (Single-threaded)
     * - 100K SET/sec (single instance)
     * - 120K GET/sec (single instance)
     * - Does NOT scale with cores
     *
     * BUT: With pipelining
     * - 1M SET/sec (10x improvement!)
     * - 1.2M GET/sec
     */
    public void redisBenchmark() {
        // redis-benchmark -t set,get -n 1000000 -q
        // Result: ~100K SET/sec, ~120K GET/sec

        // With pipelining (-P 100)
        // Result: ~1M SET/sec, ~1.2M GET/sec
    }

    /**
     * REDIS CLUSTER (Sharded)
     * - Linear scaling: N nodes = N * throughput
     * - 10 nodes = 1M ops/sec
     */
}
```

### Pipelining Benefits

```java
/**
 * Redis pipelining can match Memcached throughput
 */
public class PipeliningExample {

    /**
     * WITHOUT pipelining (slow)
     * RTT (round-trip time) per command
     */
    public void withoutPipelining(Jedis redis) {
        long start = System.nanoTime();

        for (int i = 0; i < 10000; i++) {
            redis.set("key:" + i, "value:" + i);
            // Wait for response (network RTT)
        }

        long elapsed = System.nanoTime() - start;
        // Time: ~5000ms (0.5ms RTT * 10000)
        // Throughput: 2000 ops/sec
    }

    /**
     * WITH pipelining (fast!)
     * Batch commands, single RTT
     */
    public void withPipelining(Jedis redis) {
        long start = System.nanoTime();

        Pipeline pipeline = redis.pipelined();

        for (int i = 0; i < 10000; i++) {
            pipeline.set("key:" + i, "value:" + i);
            // Commands queued locally (no network)
        }

        pipeline.sync();  // Send all at once, wait for responses

        long elapsed = System.nanoTime() - start;
        // Time: ~100ms (1 RTT for all commands)
        // Throughput: 100,000 ops/sec (50x improvement!)
    }
}
```

🏗️ **ARCHITECT**: **Performance decision matrix**:

```python
def choose_for_performance(requirements):
    """
    Performance-based selection
    """
    # CPU-bound workload? Memcached wins
    if requirements.workload == 'CPU_BOUND':
        return "Memcached (multi-threaded, better CPU utilization)"

    # Network-bound? Similar performance
    if requirements.workload == 'NETWORK_BOUND':
        return "Either (both limited by network, not CPU)"

    # Can use pipelining? Redis matches Memcached
    if requirements.can_batch_commands:
        return "Redis with pipelining (matches Memcached throughput)"

    # Need absolute maximum single-key throughput?
    if requirements.single_key_hot_path:
        return "Memcached (slight edge in raw throughput)"

    # Default: Redis (more features, similar performance)
    return "Redis (similar performance, more features)"
```

---

## Persistence & Durability

🎓 **PROFESSOR**: **Memcached has NO persistence. Redis has TWO options**:

### Memcached: No Persistence

```text
┌─────────────────────────────────────────┐
│         Memcached (Pure Cache)          │
│                                         │
│  ┌──────────────────────────┐          │
│  │      In-Memory Only       │          │
│  │                           │          │
│  │  Key1: Value1             │          │
│  │  Key2: Value2             │          │
│  │  Key3: Value3             │          │
│  └──────────────────────────┘          │
│                                         │
│  Server restart → ALL DATA LOST! 💥     │
│                                         │
│  Use case: True cache (can reload)     │
│  Not for: Data that can't be lost      │
└─────────────────────────────────────────┘
```

### Redis: RDB (Snapshots)

```text
┌─────────────────────────────────────────┐
│         Redis RDB (Snapshotting)         │
│                                         │
│  ┌──────────────────┐                  │
│  │   Memory         │                  │
│  │   Key1: Value1   │                  │
│  │   Key2: Value2   │                  │
│  └────────┬─────────┘                  │
│           │                             │
│           ↓ (Periodic save)             │
│  ┌──────────────────┐                  │
│  │   dump.rdb       │                  │
│  │   (Binary file)  │                  │
│  └──────────────────┘                  │
│                                         │
│  Pros:                                  │
│  ✅ Compact (compressed)                │
│  ✅ Fast restart (load snapshot)        │
│  ✅ Good for backups                    │
│                                         │
│  Cons:                                  │
│  ❌ Data loss possible (between saves)  │
│  ❌ Fork overhead (copy-on-write)       │
└─────────────────────────────────────────┘
```

**RDB Configuration:**

```conf
# redis.conf

# Save snapshot every N seconds if M keys changed
save 900 1      # Save after 15 min if ≥1 key changed
save 300 10     # Save after 5 min if ≥10 keys changed
save 60 10000   # Save after 1 min if ≥10K keys changed

# Compression
rdbcompression yes

# Checksum verification
rdbchecksum yes

# Filename
dbfilename dump.rdb
```

### Redis: AOF (Append-Only File)

```text
┌─────────────────────────────────────────┐
│         Redis AOF (Write-Ahead Log)      │
│                                         │
│  Client writes → Append to AOF          │
│                                         │
│  ┌──────────────────┐                  │
│  │   appendonly.aof │                  │
│  │                  │                  │
│  │   SET key1 val1  │                  │
│  │   SET key2 val2  │                  │
│  │   INCR counter   │                  │
│  │   DEL key1       │                  │
│  │   ...            │                  │
│  └──────────────────┘                  │
│                                         │
│  Pros:                                  │
│  ✅ Minimal data loss (1 sec or less)   │
│  ✅ Append-only (crash-safe)            │
│  ✅ Auto-rewrite (compaction)           │
│                                         │
│  Cons:                                  │
│  ❌ Larger files than RDB               │
│  ❌ Slower restart (replay commands)    │
│  ❌ Slight write overhead               │
└─────────────────────────────────────────┘
```

**AOF Configuration:**

```conf
# redis.conf

# Enable AOF
appendonly yes

# Fsync policy (durability vs performance)
appendfsync always    # Fsync every write (safest, slowest)
appendfsync everysec  # Fsync every second (good balance) ✅
appendfsync no        # OS decides when to fsync (fastest, risky)

# Auto-rewrite when file grows 100% from last rewrite
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# Filename
appendfilename "appendonly.aof"
```

**Hybrid Approach (Best Practice):**

```conf
# Use BOTH RDB + AOF for best durability

# RDB: Fast backups and restarts
save 900 1

# AOF: Minimal data loss
appendonly yes
appendfsync everysec

# On restart: Load AOF (more complete) if exists, else RDB
# Result: <1 second of data loss in worst case
```

---

## Scaling & High Availability

### Memcached: Client-Side Sharding

```java
/**
 * Memcached: Application does sharding
 */
public class MemcachedSharding {

    private final List<MemcachedClient> servers;
    private final ConsistentHashRing<MemcachedClient> ring;

    public MemcachedSharding(List<String> serverAddresses) {
        // Create clients for each server
        this.servers = serverAddresses.stream()
            .map(addr -> new MemcachedClient(addr))
            .collect(Collectors.toList());

        // Consistent hashing for distribution
        this.ring = new ConsistentHashRing<>(150);
        servers.forEach(ring::addServer);
    }

    public void set(String key, String value) {
        // Client determines which server
        MemcachedClient server = ring.getServer(key);
        server.set(key, 3600, value);
    }

    public String get(String key) {
        MemcachedClient server = ring.getServer(key);
        return server.get(key);
    }

    /**
     * Pros:
     * ✅ Simple (no server coordination)
     * ✅ Independent servers (no network chatter)
     *
     * Cons:
     * ❌ No replication (server failure = data loss)
     * ❌ All clients must agree on sharding
     * ❌ Rebalancing is manual
     */
}
```

### Redis: Built-in Clustering

```java
/**
 * Redis Cluster: Automatic sharding + replication
 */
public class RedisClusterConfig {

    /**
     * Redis Cluster Architecture:
     *
     *  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
     *  │ Master 0    │  │ Master 1    │  │ Master 2    │
     *  │ Slots:      │  │ Slots:      │  │ Slots:      │
     *  │ 0-5460      │  │ 5461-10922  │  │ 10923-16383 │
     *  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
     *         │                │                │
     *         ↓                ↓                ↓
     *  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
     *  │ Replica 0   │  │ Replica 1   │  │ Replica 2   │
     *  └─────────────┘  └─────────────┘  └─────────────┘
     *
     * - 16384 hash slots (divided among masters)
     * - Each master has N replicas
     * - Automatic failover on master failure
     */

    public JedisCluster createCluster() {
        Set<HostAndPort> nodes = new HashSet<>();
        nodes.add(new HostAndPort("node1.example.com", 7000));
        nodes.add(new HostAndPort("node2.example.com", 7001));
        nodes.add(new HostAndPort("node3.example.com", 7002));

        return new JedisCluster(nodes);
    }

    /**
     * Client usage (transparent)
     */
    public void usage(JedisCluster cluster) {
        // Client automatically routes to correct node
        cluster.set("key1", "value1");  // → Master 0 (based on hash slot)
        cluster.set("key2", "value2");  // → Master 1

        String val = cluster.get("key1");  // → Reads from Master 0 or Replica 0

        // Pipelining NOT supported across shards
        // Multi-key operations limited to same hash slot
    }

    /**
     * Pros:
     * ✅ Automatic sharding
     * ✅ Built-in replication
     * ✅ Automatic failover
     * ✅ Client redirection (MOVED, ASK)
     *
     * Cons:
     * ❌ Complex setup
     * ❌ Multi-key operations limited
     * ❌ No cross-shard transactions
     */
}
```

### Redis Sentinel (Master-Slave Replication)

```text
┌─────────────────────────────────────────────────┐
│         Redis Sentinel (High Availability)       │
│                                                  │
│  ┌───────────┐     ┌───────────┐     ┌────────┐│
│  │ Sentinel  │     │ Sentinel  │     │Sentinel││
│  │     1     │     │     2     │     │   3    ││
│  └─────┬─────┘     └─────┬─────┘     └────┬───┘│
│        │                 │                 │    │
│        └─────────────────┼─────────────────┘    │
│                          ↓ (Monitor)            │
│                                                  │
│  ┌─────────────┐       ┌─────────────┐         │
│  │   Master    │ ───→  │   Replica   │         │
│  │             │       │             │         │
│  └─────────────┘       └─────────────┘         │
│                                                  │
│  If Master fails:                               │
│  1. Sentinels detect failure (quorum)           │
│  2. Elect new master from replicas              │
│  3. Reconfigure clients automatically           │
└─────────────────────────────────────────────────┘
```

---

## Use Case Selection

🏗️ **ARCHITECT**: **Decision framework**:

```python
class CacheSelector:
    """
    Choose between Redis and Memcached
    """
    def recommend(self, requirements: Requirements) -> str:

        # ============ CHOOSE MEMCACHED ============

        # Pure caching (key-value only)
        if requirements.need_only_key_value:
            if not requirements.need_persistence:
                return "Memcached (simplest for pure caching)"

        # Multi-core scaling critical
        if requirements.needs_multi_core_scaling:
            if not requirements.need_advanced_features:
                return "Memcached (better CPU utilization)"

        # Minimal memory overhead
        if requirements.memory_constrained:
            if requirements.uniform_key_sizes:
                return "Memcached (slab allocator efficient)"

        # ============ CHOOSE REDIS ============

        # Need data structures
        if requirements.need_lists_sets_hashes:
            return "Redis (rich data structures)"

        # Need persistence
        if requirements.need_durability:
            return "Redis (RDB + AOF)"

        # Need pub/sub
        if requirements.need_pubsub:
            return "Redis (built-in pub/sub)"

        # Need replication/clustering
        if requirements.need_high_availability:
            return "Redis (Sentinel or Cluster)"

        # Need atomic operations
        if requirements.need_transactions:
            return "Redis (MULTI/EXEC, Lua scripts)"

        # Need complex queries
        if requirements.need_sorted_data:
            return "Redis (Sorted Sets)"

        # Default: Redis (more features, similar performance)
        return "Redis (recommended default)"
```

### Real-World Examples

```
┌──────────────────────────────────────────────────────┐
│ Use Case                  Recommendation              │
├──────────────────────────────────────────────────────┤
│ Session store             → Redis (Hash, persistence) │
│ Page caching              → Either (simple strings)   │
│ User profiles             → Redis (Hash structure)    │
│ Leaderboard               → Redis (Sorted Set)        │
│ Real-time analytics       → Redis (HyperLogLog)       │
│ Pub/Sub messaging         → Redis (built-in)         │
│ Rate limiting             → Redis (INCR, EXPIRE)      │
│ Full-page cache (CDN)     → Memcached (simpler)      │
│ Object cache (WordPress)  → Memcached (traditional)  │
│ Task queue                → Redis (List, BLPOP)       │
│ Distributed locks         → Redis (SET NX EX)         │
│ Recommendations           → Redis (Sets, SINTER)      │
└──────────────────────────────────────────────────────┘
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Functional:
- Data types? (strings only, or lists/sets/hashes?)
- Operations? (GET/SET, or complex queries?)
- Persistence? (can lose data on restart?)
- Pub/Sub? (message broadcasting?)

Non-Functional:
- Scale: 100K ops/sec
- Latency: P99 < 1ms
- Availability: 99.99%
- Durability: Can lose 1 second of data
```

### 2. Capacity Estimation (RADIO: Scale)

```
Redis vs Memcached Memory:

Item count: 10M
Key size: 50 bytes
Value size: 500 bytes

Memcached:
- Slab size: ~600 bytes (next slab up)
- Total: 10M * 600 = 6 GB

Redis:
- Item overhead: 32 bytes
- Total: 10M * (50 + 500 + 32) = 5.82 GB

Winner: Redis (slightly more efficient)

Throughput:

Memcached:
- 8 cores → 1M ops/sec

Redis (single instance):
- 100K ops/sec
- With pipelining: 1M ops/sec

Redis Cluster (10 nodes):
- 10 * 100K = 1M ops/sec

Winner: Tie (both can achieve 1M ops/sec)
```

### 3. Technology Selection (RADIO: Data-Model)

```java
/**
 * Technology selection matrix
 */
public class TechnologyChoice {

    public String selectCache(Requirements req) {
        // Complex data structures? → Redis
        if (req.needsLists() || req.needsSets() || req.needsSortedSets()) {
            return "Redis";
        }

        // Persistence required? → Redis
        if (req.needsPersistence()) {
            return "Redis (RDB + AOF)";
        }

        // Clustering/Replication? → Redis
        if (req.needsHighAvailability()) {
            return "Redis Cluster";
        }

        // Simple caching + Multi-core? → Memcached
        if (req.isSimpleCache() && req.hasManyCores()) {
            return "Memcached";
        }

        // Default: Redis (more features)
        return "Redis";
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
REDIS CLUSTER DEPLOYMENT
┌─────────────────────────────────────────────────┐
│            APPLICATION LAYER                     │
│  ┌──────────────────────────────────────────┐  │
│  │  Redis Client (JedisCluster)             │  │
│  │  - Caching: Hash slots routing           │  │
│  └───────────────┬──────────────────────────┘  │
└──────────────────┼──────────────────────────────┘
                   ↓
┌─────────────────────────────────────────────────┐
│  REDIS CLUSTER (6 nodes: 3 masters + 3 replicas)│
│                                                  │
│  Master 0        Master 1        Master 2       │
│  Slots 0-5460    5461-10922      10923-16383    │
│       ↓              ↓                ↓          │
│  Replica 0       Replica 1       Replica 2      │
│                                                  │
│  - Auto sharding by hash slot                   │
│  - Auto failover on master failure              │
│  - Replication: Async                           │
│  - Persistence: RDB + AOF                       │
└─────────────────────────────────────────────────┘
```

### 5. Deep Dives (RADIO: Optimize)

**A. Pipelining Optimization**

```java
/**
 * Redis pipelining for batch operations
 */
public class PipelineOptimization {

    public void batchLoad(List<User> users, Jedis redis) {
        Pipeline pipeline = redis.pipelined();

        for (User user : users) {
            String key = "user:" + user.getId();
            Map<String, String> fields = Map.of(
                "name", user.getName(),
                "email", user.getEmail()
            );

            pipeline.hset(key, fields);
            pipeline.expire(key, 3600);
        }

        pipeline.sync();  // Execute all at once

        // 100x faster than individual SET commands!
    }
}
```

**B. Monitoring**

```java
@Component
public class CacheMetrics {

    public void monitorRedis(Jedis redis) {
        // INFO command returns extensive metrics
        String info = redis.info("stats");

        // Key metrics:
        // - used_memory_human
        // - total_commands_processed
        // - instantaneous_ops_per_sec
        // - keyspace_hits / keyspace_misses (hit ratio)
        // - evicted_keys (eviction rate)
        // - expired_keys
    }

    /**
     * Critical Redis metrics:
     * - memory_fragmentation_ratio (should be ~1.0-1.5)
     * - connected_clients
     * - blocked_clients
     * - ops_per_sec
     * - hit_rate (hits / (hits + misses))
     */
}
```

---

## 🧠 MIND MAP: REDIS VS MEMCACHED

```
      CACHE SYSTEMS
            |
      ┌─────┴─────┐
      ↓           ↓
  MEMCACHED    REDIS
      |           |
  ┌───┴───┐   ┌───┴────┐
  ↓       ↓   ↓        ↓
SIMPLE MULTI DATA   FEATURES
K-V   THREAD STRUCT
  |       |     |        |
STRING CORES LISTS   PERSIST
ONLY   8+   SETS    RDB/AOF
  |       |   HASH      |
LRU   SLAB SORTED  CLUSTER
     ALLOC  SETS   SENTINEL
       |      |        |
    FRAG  ATOMIC   PUBSUB
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **Memcached = Vending Machine 🍫**
- Insert coin (key), get candy (value)
- Only one type of operation (simple)
- Multiple slots work in parallel (multi-threaded)
- Power off → everything gone (no persistence)
- Fast, simple, reliable

### 2. **Redis = Swiss Army Knife 🔪**
- Many tools in one (data structures)
- Can do simple things (cache) and complex (queue, leaderboard)
- One tool at a time (single-threaded)
- Can remember settings (persistence)
- More powerful but more complex

### 3. **Redis Cluster = Library Branches 📚**
- Books distributed across branches (sharding)
- Each branch has backup copies (replication)
- System knows which branch has which books (hash slots)
- If branch closes, redirect to another (failover)

### 4. **Memcached Slab = Parking Garage 🅿️**
- Cars (data) park in fixed-size spots
- Compact car in truck spot → wasted space
- Simple, predictable, but some waste
- Fast to find spot (no searching)

### 5. **Redis jemalloc = Tetris 🎮**
- Fit pieces efficiently (dynamic allocation)
- Minimize gaps (low fragmentation)
- More complex but efficient
- Occasionally reorganize (compaction)

### 6. **Pipelining = Batch Mail Delivery 📬**
- Don't send one letter at a time
- Collect many letters, deliver together
- Same trip, 100x more efficient
- One round-trip instead of many

### 7. **Persistence = Notebook Backup 📓**
- RDB = Take photos of pages (snapshot)
- AOF = Write down every change (journal)
- Both = Photo + recent changes
- Can recover even if notebook lost

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Cache Selection:**

1. **Features vs Simplicity**
   - Need data structures? → Redis
   - Simple caching? → Memcached (slightly simpler)
   - Default: Redis (minimal complexity overhead)

2. **Performance is Similar**
   - Memcached: Better multi-core (no pipelining)
   - Redis: Match Memcached with pipelining
   - Both: Sub-millisecond latency
   - Choose based on features, not raw speed

3. **Persistence Changes Everything**
   - Need to survive restarts? → Redis (only option)
   - Pure cache (can reload)? → Either works
   - Critical data? → Redis with AOF

---

## 🏗️ ARCHITECT'S CHECKLIST

Choosing cache technology:

- [ ] **Data Types**: Strings only? → Memcached. Complex? → Redis
- [ ] **Persistence**: Need durability? → Redis (RDB+AOF)
- [ ] **Replication**: Need HA? → Redis (Cluster/Sentinel)
- [ ] **Pub/Sub**: Need messaging? → Redis
- [ ] **Multi-Core**: Need CPU scaling? → Memcached (slight edge)
- [ ] **Pipelining**: Can batch operations? → Redis (same perf as Memcached)
- [ ] **Atomicity**: Need transactions? → Redis (MULTI/EXEC, Lua)
- [ ] **Complexity**: Prefer simplicity? → Memcached
- [ ] **Team Expertise**: More familiar with? → Use that
- [ ] **Future Needs**: Might need features later? → Redis (safer bet)

**Default Recommendation**: **Redis** (unless you have a specific reason for Memcached)

Reasons for Memcached:
- Legacy system already using it
- Purely simple key-value
- Extreme multi-core scaling needs
- Team strongly prefers simplicity

---

**Interview Closer**: "I'd choose Redis for this use case because we need [lists/persistence/replication/etc]. While Memcached is simpler, Redis provides the features we need with similar performance. We can use pipelining to match Memcached's throughput on multi-core systems. The single-threaded model actually simplifies reasoning about atomic operations. For high availability, we'd deploy Redis Cluster with 3 masters and 3 replicas, using RDB+AOF for persistence. Let me draw the architecture..."
