# Caching Strategies

## Why Caching?

**Performance**: RAM is 100x faster than disk, 1000x faster than network
**Cost**: Reduce expensive database queries
**Scalability**: Handle more traffic with same backend

---

## Cache Levels

```
┌─────────────────────────────────────┐
│   Browser Cache (client-side)      │ ← Fastest, smallest
├─────────────────────────────────────┤
│   CDN (edge locations)              │
├─────────────────────────────────────┤
│   Application Cache (Redis/Memcache)│
├─────────────────────────────────────┤
│   Database Query Cache              │
└─────────────────────────────────────┘
```

### 1. Client-Side Caching

**Browser Cache**:
```http
Cache-Control: max-age=3600, public
ETag: "33a64df551425fcc"
Last-Modified: Wed, 21 Oct 2025 07:28:00 GMT
```

**Use For**:
- Static assets (CSS, JS, images)
- Rarely changing data
- API responses with cache headers

### 2. CDN Caching

**Edge Location Caching**:
```
User in Tokyo → Tokyo CDN → Origin Server (US)
               (cache hit)   (cache miss only)
```

**Use For**:
- Images, videos, static files
- Downloadable content
- API responses (geo-distributed)

### 3. Application-Level Caching

**In-Memory Cache (Redis, Memcached)**:
```
Application → [Cache] → (hit: return)
                ↓ (miss)
           [Database] → store in cache → return
```

**Use For**:
- Database query results
- Session data
- Computed values
- API responses

### 4. Database Caching

**Query Cache**:
- Cache query results
- Invalidated on table changes

**Buffer Pool**:
- Frequently accessed pages in memory
- Automatic in most databases

---

## Caching Patterns

### 1. Cache-Aside (Lazy Loading)

**Most Common Pattern**

```python
def get_user(user_id):
    # Try cache first
    user = cache.get(f"user:{user_id}")

    if user is None:
        # Cache miss - query database
        user = db.query("SELECT * FROM users WHERE id = ?", user_id)

        # Store in cache for next time
        cache.set(f"user:{user_id}", user, ttl=3600)

    return user

def update_user(user_id, data):
    # Update database
    db.execute("UPDATE users SET ... WHERE id = ?", user_id, data)

    # Invalidate cache
    cache.delete(f"user:{user_id}")
```

**Pros**:
- Only cache what's actually requested (efficient)
- Resilient (cache failure → slower, not broken)
- Easy to implement

**Cons**:
- Initial requests slow (cache miss)
- Cache and DB can diverge
- Need invalidation strategy

### 2. Read-Through Cache

**Cache handles database reads**:

```python
# Application doesn't touch database directly
user = cache.get(f"user:{user_id}")
# Cache internally fetches from DB if miss
```

**Pros**:
- Simpler application code
- Cache responsibility clear

**Cons**:
- More complex cache implementation
- Tight coupling

**Used By**: Some ORM caching layers

### 3. Write-Through Cache

**Write to cache AND database synchronously**:

```python
def update_user(user_id, data):
    # Write to cache
    cache.set(f"user:{user_id}", data)

    # Write to database (synchronously)
    db.execute("UPDATE users SET ... WHERE id = ?", user_id, data)
```

**Pros**:
- Cache always consistent with DB
- No stale data

**Cons**:
- Higher write latency (two operations)
- Wasted cache space (may cache unused data)

**Use When**: Consistency critical, read-heavy workload

### 4. Write-Behind (Write-Back) Cache

**Write to cache immediately, database asynchronously**:

```python
def update_user(user_id, data):
    # Write to cache immediately
    cache.set(f"user:{user_id}", data)

    # Queue database write (async)
    queue.enqueue(lambda: db.execute("UPDATE users ...", user_id, data))

    return  # Return immediately
```

**Pros**:
- Very fast writes
- Can batch database updates
- Reduces database load

**Cons**:
- Risk of data loss (cache failure before DB write)
- Complex consistency

**Use When**: Write-heavy workload, can tolerate small data loss risk

### 5. Refresh-Ahead

**Proactively refresh cache before expiration**:

```python
def get_popular_item(item_id):
    item = cache.get(f"item:{item_id}")

    # Check TTL remaining
    ttl_remaining = cache.ttl(f"item:{item_id}")

    # If close to expiration, refresh in background
    if ttl_remaining < 300:  # < 5 minutes
        background_task(refresh_item, item_id)

    return item
```

**Pros**:
- No cache miss penalty for popular items
- Consistent performance

**Cons**:
- Complexity
- May refresh unused items
- Requires prediction of popular items

**Use When**: Known access patterns, latency-sensitive

---

## Eviction Policies

**When cache is full, what to remove?**

### LRU (Least Recently Used)

```
Cache: [A, B, C, D] (oldest to newest)
Access: B
Cache: [A, C, D, B]
Add: E
Cache: [C, D, B, E] (A evicted - oldest)
```

**Pros**: Good for most workloads
**Cons**: Recent != frequently used

**Used By**: Redis, Memcached

### LFU (Least Frequently Used)

```
Track access frequency:
A: 100 accesses
B: 50 accesses
C: 200 accesses
D: 10 accesses

Cache full → Evict D (least frequent)
```

**Pros**: Keeps popular items
**Cons**: Old popular items stay forever

### FIFO (First In, First Out)

Simple queue:
```
Cache: [A, B, C, D]
Add: E
Cache: [B, C, D, E] (A evicted - first in)
```

**Pros**: Simple
**Cons**: May evict popular items

### TTL (Time To Live)

```
cache.set("key", value, ttl=3600)  # Expire in 1 hour
```

**Pros**: Predictable, prevents stale data
**Cons**: May evict before full, cache misses spike at expiration

---

## Cache Invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." - Phil Karlton

### Strategies

**1. TTL-Based**:
```python
cache.set("user:123", user, ttl=3600)  # Auto-expire in 1 hour
```
- Simple, predictable
- May serve stale data
- Use for data that's okay if slightly stale

**2. Event-Based**:
```python
def update_user(user_id, data):
    db.update(user_id, data)
    cache.delete(f"user:{user_id}")  # Invalidate immediately
```
- Always fresh data
- Requires discipline
- Use for critical data

**3. Pattern-Based**:
```python
# Invalidate all user-related caches
cache.delete_pattern("user:*")
```
- Convenient for related data
- May invalidate too much
- Use carefully

**4. Write-Through**:
```python
def update_user(user_id, data):
    db.update(user_id, data)
    cache.set(f"user:{user_id}", data)  # Update cache
```
- No invalidation needed
- Always fresh
- Write overhead

---

## Common Caching Problems

### 1. Cache Stampede (Thundering Herd)

**Problem**: Popular item expires, many requests hit database simultaneously

```
Cache expires for "trending_video"
100 requests simultaneously:
  → Cache miss
  → All query database
  → Database overload
```

**Solutions**:

**Lock-Based**:
```python
def get_item(key):
    item = cache.get(key)
    if item is None:
        lock = cache.lock(f"{key}:lock", timeout=10)
        if lock.acquire(blocking=False):
            try:
                item = db.query(...)
                cache.set(key, item)
            finally:
                lock.release()
        else:
            # Wait and retry
            time.sleep(0.1)
            return get_item(key)
    return item
```

**Probabilistic Early Expiration**:
```python
import random

def get_item(key):
    item = cache.get(key)
    ttl_remaining = cache.ttl(key)

    # Refresh probabilistically before expiration
    if ttl_remaining < 60 and random.random() < 0.1:
        refresh_cache(key)

    return item
```

### 2. Cache Penetration

**Problem**: Requests for non-existent items always hit database

```
Request for user_id=999999 (doesn't exist)
  → Cache miss
  → Database query (not found)
  → No cache (because null)
  → Next request repeats
```

**Solutions**:

**Cache Null Values**:
```python
user = cache.get(f"user:{user_id}")
if user is None:
    user = db.query(user_id)
    if user is None:
        cache.set(f"user:{user_id}", "NULL", ttl=60)  # Short TTL
    else:
        cache.set(f"user:{user_id}", user, ttl=3600)
```

**Bloom Filter**:
```python
# Probabilistic data structure
if not bloom_filter.might_contain(user_id):
    return None  # Definitely doesn't exist

# Might exist, check cache/DB
```

### 3. Large Cache Keys

**Problem**: Large values slow down cache

**Solution**: Compress or store reference
```python
# Bad: Store entire 1MB document
cache.set("doc:123", large_document)

# Good: Store reference or compressed
compressed = compress(large_document)
cache.set("doc:123", compressed)

# Or: Store in object storage, cache URL
url = s3.put(large_document)
cache.set("doc:123", url)
```

---

## Distributed Caching

### Cache Cluster (Redis Cluster, Memcached)

**Partitioning**:
```
hash(key) % num_nodes = target_node

Example:
cache1: keys hash to 0-33%
cache2: keys hash to 34-66%
cache3: keys hash to 67-100%
```

**Consistent Hashing**:
```
Minimize data movement when adding/removing nodes
```

**Replication**:
```
Primary-Replica: Write to primary, replicate to replicas
Benefit: Higher availability, read scalability
```

---

## Cache Metrics

**Monitor These**:

1. **Hit Rate**:
   ```
   Hit Rate = Cache Hits / (Cache Hits + Misses)
   Target: > 90% for hot data
   ```

2. **Latency**:
   ```
   Cache hit: < 1ms
   Cache miss + DB: 10-100ms
   ```

3. **Memory Usage**:
   ```
   Track: Used / Total
   Alert: > 80% (evictions increase)
   ```

4. **Eviction Rate**:
   ```
   High evictions = cache too small
   ```

---

## Interview Q&A

**Q: "Design caching for an e-commerce product catalog"**

**Answer**:
```
Multi-Level Caching:

Level 1: CDN (CloudFront)
- Product images, CSS, JS
- TTL: 24 hours (static assets)
- Cache-Control: public, max-age=86400

Level 2: Application Cache (Redis)
- Product details (name, price, description)
- TTL: 5 minutes (prices change)
- Pattern: cache-aside with active invalidation

Level 3: Database Query Cache
- Complex joins, aggregations
- Automatic (managed by database)

Invalidation Strategy:
- Price update → Invalidate Redis cache for that product
- Image update → Invalidate CDN cache (versioned URLs)
- Inventory update → Don't cache (real-time requirement)

Example Flow:
1. User requests product page
2. Check Redis for product:123 → Hit (return in 1ms)
3. Check Redis for inventory:123 → Miss
4. Query database for inventory → Cache for 10 seconds
5. Load product images from CDN (cached)

Trade-offs:
- Stale prices for up to 5 minutes (acceptable)
- Real-time inventory (critical for checkout)
- CDN costs vs origin bandwidth savings
```

---

## Key Takeaways

1. **Cache at Multiple Levels**: Client, CDN, Application, Database
2. **Choose Right Pattern**: Cache-aside for most use cases
3. **Invalidation is Hard**: Use TTL + event-based hybrid
4. **Monitor Hit Rate**: Should be >80-90%
5. **Handle Cache Failures**: Graceful degradation

## Further Reading

- Redis Documentation
- "Caching at Scale" - Meta Engineering Blog
- "The Two Hard Things" by Martin Fowler
- AWS ElastiCache Best Practices
