# Distributed Caching

## Why Caching?

**Performance**: Memory is 1000x faster than disk

```
Typical Latencies:
- L1 cache: 1 ns
- RAM: 100 ns
- SSD: 100 μs (1,000x slower than RAM)
- Network (same datacenter): 500 μs
- HDD: 10 ms (100,000x slower than RAM)
```

**Problem Without Cache**:
```
Every request → Database query
1000 requests/sec × 50ms query time = Database overloaded
```

**With Cache**:
```
Cache hit (95% of requests) → 1ms response
Cache miss (5% of requests) → 50ms + cache update
Average latency: ~3ms (vs 50ms)
Database load: 50 queries/sec (vs 1000)
```

---

## Caching Strategies

### Cache-Aside (Lazy Loading)

**Pattern**: Application manages cache explicitly

```
Read Flow:
1. Check cache
2. If hit: Return cached data
3. If miss: Query database
4. Update cache
5. Return data
```

**Implementation**:
```python
def get_user(user_id):
    # 1. Check cache
    cached = cache.get(f"user:{user_id}")

    if cached:
        # 2. Cache hit
        return cached

    # 3. Cache miss - query database
    user = db.query("SELECT * FROM users WHERE id = ?", user_id)

    # 4. Update cache
    cache.set(f"user:{user_id}", user, ttl=3600)  # 1 hour TTL

    # 5. Return
    return user

def update_user(user_id, data):
    # Update database
    db.update("UPDATE users SET ... WHERE id = ?", user_id, data)

    # Invalidate cache
    cache.delete(f"user:{user_id}")
```

**Pros**:
- Simple
- Cache only requested data (efficient)
- Cache failures don't bring down app

**Cons**:
- Cache miss penalty (extra latency)
- Stale data possible (if TTL too long)

**Use Cases**: Read-heavy, infrequently changing data

### Write-Through

**Pattern**: Write to cache and database together

```
Write Flow:
1. Write to cache
2. Write to database (synchronous)
3. Return success
```

**Implementation**:
```python
def update_user(user_id, data):
    # 1. Update cache
    cache.set(f"user:{user_id}", data, ttl=3600)

    # 2. Update database
    db.update("UPDATE users SET ... WHERE id = ?", user_id, data)

    # Both must succeed
```

**Pros**:
- Cache always fresh
- No cache miss after write

**Cons**:
- Write latency (wait for both)
- Wasteful (cache data that's never read)

**Use Cases**: Read-after-write scenarios, strong consistency needs

### Write-Behind (Write-Back)

**Pattern**: Write to cache immediately, async to database

```
Write Flow:
1. Write to cache
2. Return success (fast!)
3. Asynchronously write to database
```

**Implementation**:
```python
import queue

write_queue = queue.Queue()

def update_user(user_id, data):
    # 1. Update cache
    cache.set(f"user:{user_id}", data, ttl=3600)

    # 2. Queue database write
    write_queue.put(('users', user_id, data))

    # 3. Return immediately

# Background worker
def database_writer():
    while True:
        table, id, data = write_queue.get()
        try:
            db.update(f"UPDATE {table} SET ... WHERE id = ?", id, data)
        except Exception as e:
            # Retry logic
            write_queue.put((table, id, data))
```

**Pros**:
- Low write latency
- Can batch database writes
- Reduces database load

**Cons**:
- Data loss risk (if cache crashes before DB write)
- Complex (need reliable queue)

**Use Cases**: Write-heavy workloads, can tolerate small data loss

### Refresh-Ahead

**Pattern**: Proactively refresh cache before expiration

```
Background Process:
1. Monitor cache expiration times
2. Before TTL expires: Refresh from database
3. Keep cache warm
```

**Implementation**:
```python
def refresh_ahead_cache(key, ttl, refresh_threshold=0.8):
    cached = cache.get_with_ttl(key)

    if cached:
        data, remaining_ttl = cached

        # If within refresh window
        if remaining_ttl < ttl * refresh_threshold:
            # Asynchronously refresh
            background_refresh(key)

        return data

    # Cache miss - load and cache
    data = load_from_database(key)
    cache.set(key, data, ttl=ttl)
    return data
```

**Use Cases**: Predictable access patterns, high read rates

---

## Distributed Caching with Redis

### Basic Operations

```python
import redis

# Connect
r = redis.Redis(host='localhost', port=6379, db=0)

# String operations
r.set('user:123', json.dumps({'name': 'Alice', 'email': 'alice@example.com'}))
user = json.loads(r.get('user:123'))

# With expiration
r.setex('session:abc', 3600, 'user_id:123')  # Expire in 1 hour

# Hash operations (better for structured data)
r.hset('user:123', mapping={
    'name': 'Alice',
    'email': 'alice@example.com',
    'age': 30
})
name = r.hget('user:123', 'name')
user = r.hgetall('user:123')

# Lists (for queues, recent items)
r.lpush('recent:user:123', 'product:456')  # Add to head
r.ltrim('recent:user:123', 0, 9)  # Keep only 10 items
recent = r.lrange('recent:user:123', 0, 9)

# Sets (for tags, unique items)
r.sadd('tags:product:123', 'electronics', 'laptop', 'sale')
tags = r.smembers('tags:product:123')
is_tagged = r.sismember('tags:product:123', 'sale')

# Sorted sets (for leaderboards, rankings)
r.zadd('leaderboard', {'user:123': 1000, 'user:456': 950, 'user:789': 900})
top_10 = r.zrevrange('leaderboard', 0, 9, withscores=True)
user_rank = r.zrevrank('leaderboard', 'user:123')
```

### Redis Cluster

**Sharding**: Data distributed across nodes

```
16384 hash slots distributed across nodes:

Node 1: Slots 0-5460
Node 2: Slots 5461-10922
Node 3: Slots 10923-16383

Key "user:123" → hash → slot 5500 → Node 2
```

**Replication**: Each master has replicas

```
Master 1 (slots 0-5460)
  ├─ Replica 1a
  └─ Replica 1b

Master 2 (slots 5461-10922)
  ├─ Replica 2a
  └─ Replica 2b

Master 3 (slots 10923-16383)
  ├─ Replica 3a
  └─ Replica 3b
```

**Client**:
```python
from rediscluster import RedisCluster

startup_nodes = [
    {"host": "redis1", "port": 7000},
    {"host": "redis2", "port": 7001},
    {"host": "redis3", "port": 7002}
]

rc = RedisCluster(startup_nodes=startup_nodes, decode_responses=True)

# Client automatically routes to correct node
rc.set('user:123', 'data')
```

---

## Memcached

**Characteristics**:
- Simple key-value store
- In-memory only
- LRU eviction
- Multi-threaded

**vs Redis**:
| Feature | Redis | Memcached |
|---------|-------|-----------|
| Data structures | Rich (hash, list, set, sorted set) | Key-value only |
| Persistence | Yes | No |
| Replication | Yes | No (app handles) |
| Transactions | Yes | No |
| Threading | Single-threaded | Multi-threaded |
| Use case | Complex caching, sessions | Simple caching |

**Memcached Example**:
```python
import memcache

mc = memcache.Client(['127.0.0.1:11211'])

# Set
mc.set('user:123', {'name': 'Alice'}, time=3600)

# Get
user = mc.get('user:123')

# Delete
mc.delete('user:123')

# Increment (atomic)
mc.set('counter', 0)
mc.incr('counter', 1)
```

---

## Cache Invalidation

### Time-Based (TTL)

**Simplest approach**: Expire after time

```python
# 1 hour TTL
cache.set('product:123', data, ttl=3600)

# After 1 hour: Automatic eviction
```

**Pros**: Simple
**Cons**: Stale data before expiration, thundering herd on expiration

### Event-Based Invalidation

**Invalidate on update**:
```python
def update_product(product_id, data):
    # Update database
    db.update(...)

    # Invalidate cache
    cache.delete(f'product:{product_id}')
```

**Pub-Sub Pattern**:
```python
# Service that updates data
def update_product(product_id, data):
    db.update(...)

    # Publish invalidation event
    redis.publish('cache-invalidation', f'product:{product_id}')

# Services with cache subscribe
def cache_invalidation_listener():
    pubsub = redis.pubsub()
    pubsub.subscribe('cache-invalidation')

    for message in pubsub.listen():
        key = message['data']
        local_cache.delete(key)
```

### Cache Stampede / Thundering Herd

**Problem**: Cache expires, many requests hit database simultaneously

```
Time: 10:00:00 - Cache entry "popular_item" expires

10:00:00.001 - Request 1: Cache miss → Query DB
10:00:00.002 - Request 2: Cache miss → Query DB
10:00:00.003 - Request 3: Cache miss → Query DB
...
10:00:00.100 - Request 100: Cache miss → Query DB

Result: Database overwhelmed!
```

**Solution 1: Lock**:
```python
import threading

cache_locks = {}

def get_with_lock(key):
    cached = cache.get(key)
    if cached:
        return cached

    # Acquire lock for this key
    if key not in cache_locks:
        cache_locks[key] = threading.Lock()

    lock = cache_locks[key]

    with lock:
        # Double-check (another thread might have cached)
        cached = cache.get(key)
        if cached:
            return cached

        # Only one thread queries database
        data = database.query(key)
        cache.set(key, data, ttl=3600)
        return data
```

**Solution 2: Probabilistic Early Expiration**:
```python
import random

def get_with_early_expiration(key, ttl=3600, beta=1.0):
    cached = cache.get_with_ttl(key)

    if cached:
        value, current_ttl = cached

        # Probabilistically refresh early
        if current_ttl < -beta * math.log(random.random()):
            # Asynchronously refresh
            async_refresh(key)

        return value

    # Cache miss
    value = database.query(key)
    cache.set(key, value, ttl=ttl)
    return value
```

**Solution 3: Refresh-Ahead**:
```python
# Background job refreshes popular items before expiration
def cache_refresher():
    while True:
        popular_keys = get_popular_keys()

        for key in popular_keys:
            ttl_remaining = cache.ttl(key)

            # Refresh if TTL < 10% of original
            if ttl_remaining < 360:  # Original TTL: 3600s
                data = database.query(key)
                cache.set(key, data, ttl=3600)

        time.sleep(60)  # Run every minute
```

---

## Cache Partitioning / Sharding

### Consistent Hashing

**Problem with Simple Modulo**:
```
3 servers: hash(key) % 3 = server

Adding 4th server: hash(key) % 4
→ Most keys rehashed to different servers
→ Cache invalidated!
```

**Consistent Hashing Solution**:
```python
import hashlib

class ConsistentHashRing:
    def __init__(self, nodes, virtual_nodes=150):
        self.ring = {}
        self.sorted_keys = []

        for node in nodes:
            for i in range(virtual_nodes):
                key = self._hash(f"{node}:{i}")
                self.ring[key] = node

        self.sorted_keys = sorted(self.ring.keys())

    def _hash(self, key):
        return int(hashlib.md5(key.encode()).hexdigest(), 16)

    def get_node(self, key):
        if not self.ring:
            return None

        hash_val = self._hash(key)

        # Find first node >= hash_val
        for ring_key in self.sorted_keys:
            if ring_key >= hash_val:
                return self.ring[ring_key]

        # Wrap to first node
        return self.ring[self.sorted_keys[0]]

# Usage
servers = ['cache1', 'cache2', 'cache3']
ring = ConsistentHashRing(servers)

def cache_set(key, value):
    server = ring.get_node(key)
    send_to_server(server, 'SET', key, value)

def cache_get(key):
    server = ring.get_node(key)
    return send_to_server(server, 'GET', key)

# Add server (minimal redistribution)
ring = ConsistentHashRing(['cache1', 'cache2', 'cache3', 'cache4'])
```

---

## Local vs Distributed Caching

### Local Cache (In-Process)

**Pros**:
- Ultra-fast (no network)
- Simple
- No external dependency

**Cons**:
- Memory per instance
- Inconsistent across instances
- No sharing

**Example**:
```python
from functools import lru_cache

@lru_cache(maxsize=1000)
def get_product(product_id):
    return database.query(...)
```

### Distributed Cache (Redis/Memcached)

**Pros**:
- Shared across instances
- Consistent
- Larger capacity (dedicated)

**Cons**:
- Network latency
- Extra infrastructure
- Single point of failure (if not redundant)

### Two-Level Caching

**Combine both**: Local (L1) + Distributed (L2)

```python
class TwoLevelCache:
    def __init__(self, local_cache, redis_cache):
        self.l1 = local_cache  # LRU cache
        self.l2 = redis_cache  # Redis

    def get(self, key):
        # L1 cache
        value = self.l1.get(key)
        if value:
            return value

        # L2 cache
        value = self.l2.get(key)
        if value:
            self.l1.set(key, value)  # Populate L1
            return value

        # Database
        value = database.query(key)
        self.l1.set(key, value)
        self.l2.set(key, value)
        return value

    def set(self, key, value):
        self.l1.set(key, value)
        self.l2.set(key, value)

    def delete(self, key):
        self.l1.delete(key)
        self.l2.delete(key)
```

**Benefits**:
- Best of both worlds
- Reduced Redis load (L1 absorbs hot keys)
- Lower latency (L1 hits are instant)

---

## Interview Q&A

**Q: "Design caching for an e-commerce product catalog"**

**Answer**:
```
Multi-layered caching strategy:

1. CDN (Edge Caching):
   - Cache product images, static assets
   - TTL: 1 day
   - Invalidate on update via CDN API

2. Application Cache (Redis):
   - Cache product data, inventory, prices
   - TTL: 5 minutes
   - Two-level: Local LRU (1000 items) + Redis

3. Database Query Cache:
   - MySQL query cache or pg_stat_statements
   - Automatic

Caching Strategy:

Product Data (Infrequently Changing):
```python
def get_product(product_id):
    key = f'product:{product_id}'

    # L1 cache (local)
    cached = local_cache.get(key)
    if cached:
        return cached

    # L2 cache (Redis)
    cached = redis.get(key)
    if cached:
        local_cache.set(key, cached, ttl=60)  # 1 min L1
        return cached

    # Database
    product = db.query("SELECT * FROM products WHERE id = ?", product_id)

    # Cache with 5 min TTL
    redis.setex(key, 300, json.dumps(product))
    local_cache.set(key, product, ttl=60)

    return product
```

Inventory (Frequently Changing):
```python
def get_inventory(product_id):
    key = f'inventory:{product_id}'

    # Shorter TTL (30 seconds)
    cached = redis.get(key)
    if cached:
        return cached

    inventory = db.query("SELECT quantity FROM inventory WHERE product_id = ?", product_id)

    # 30s TTL (more fresh)
    redis.setex(key, 30, inventory)

    return inventory
```

Invalidation:
```python
def update_product(product_id, data):
    # Update database
    db.update(...)

    # Invalidate cache
    local_cache.delete(f'product:{product_id}')
    redis.delete(f'product:{product_id}')

    # Invalidate CDN
    cdn.purge(f'/products/{product_id}/image.jpg')

    # Publish to other app servers
    redis.publish('invalidate', f'product:{product_id}')
```

Popular Products (Hot Keys):
- Preload in cache on startup
- Refresh-ahead pattern
- Higher TTL (10 minutes)

Search Results:
```python
def search_products(query):
    key = f'search:{hash(query)}'

    cached = redis.get(key)
    if cached:
        return cached

    results = elasticsearch.search(query)

    # 2 min TTL (search less critical for freshness)
    redis.setex(key, 120, json.dumps(results))

    return results
```

Monitoring:
- Cache hit rate (target: > 90%)
- Cache miss latency
- Eviction rate
- Memory usage

Architecture:
```
[CDN] → [Load Balancer]
           ↓
    [App Servers (with local cache)]
           ↓
    [Redis Cluster]
           ↓
    [Database (MySQL)]
```

Trade-offs:
- Stale data (TTL-based) vs freshness
- Memory usage vs latency
- Complex invalidation logic
- But: 10x-100x performance improvement
```

---

## Key Takeaways

1. **Caching Essential**: Reduce latency and database load dramatically
2. **Choose Strategy**: Cache-aside for reads, write-through for consistency
3. **TTL vs Invalidation**: TTL simple but stale, invalidation complex but fresh
4. **Distributed Caching**: Redis/Memcached for shared cache across instances
5. **Thundering Herd**: Use locks or probabilistic expiration to prevent
6. **Monitor**: Track hit rate, latency, evictions

---

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 3
- Redis documentation
- Memcached documentation
- "Caching at Scale" - Various blog posts from Facebook, Twitter
- "Web Scalability for Startup Engineers" - Artur Ejsmont
