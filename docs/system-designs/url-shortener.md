# Design a URL Shortener

**Companies**: Google (goo.gl), Bitly, TinyURL
**Difficulty**: Beginner
**Time**: 45 minutes

## Problem Statement

Design a URL shortening service like Bitly. Users can create short URLs from long URLs and be redirected when accessing the short URL.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Shorten URL**: Given long URL, return short URL
2. **Redirect**: Given short URL, redirect to original URL
3. **Custom URLs** (optional): User-defined short URLs
4. **Analytics** (optional): Track click counts
5. **Expiration** (optional): URLs expire after time period

**Prioritize**: Focus on shorten and redirect for MVP

### Non-Functional Requirements

1. **High Availability**: 99.9% uptime
2. **Low Latency**: <100ms for redirects
3. **Scalable**: Handle millions of URLs
4. **Durable**: URLs should not be lost

### Capacity Estimation

**Assumptions**:
- 100M new URLs per month
- 100:1 read-write ratio (10B redirects per month)

**Write Load**:
```
100M URLs/month ÷ 30 days ÷ 24 hours ÷ 3600 sec = ~40 writes/sec
Peak: 40 × 2 = ~80 writes/sec
```

**Read Load**:
```
10B redirects/month = ~4,000 reads/sec
Peak: ~8,000 reads/sec
```

**Storage**:
```
100M URLs/month × 12 months × 5 years = 6B URLs
Per URL storage: 500 bytes (URL + metadata)
Total: 6B × 500 bytes = 3TB
```

**Bandwidth**:
```
Write: 40 writes/sec × 500 bytes = 20KB/sec (negligible)
Read: 4K reads/sec × 500 bytes = 2MB/sec
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                    Client                           │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 Load Balancer                       │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  App Server 1    │  │  App Server 2    │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │    Redis Cache      │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │   Database Cluster  │
         │  (Primary + Replica)│
         └─────────────────────┘
```

### Components

**Load Balancer**:
- Distribute traffic across app servers
- Health checks
- SSL termination

**Application Servers**:
- Stateless (easy to scale)
- Handle shorten and redirect requests
- Generate short codes

**Cache (Redis)**:
- Cache popular URLs (80/20 rule)
- Reduce database load
- TTL: No expiration (URLs don't change)

**Database**:
- Store URL mappings
- Primary for writes
- Read replicas for reads

---

## Step 3: Data Model (10 min)

### Database Choice

**SQL vs NoSQL**:
- Simple schema (id, short_code, long_url)
- No complex relationships
- High read throughput needed

**Choice**: NoSQL (DynamoDB) or SQL (PostgreSQL)
- DynamoDB: Better scaling, lower latency
- PostgreSQL: Simpler, ACID transactions

**Going with PostgreSQL** (easier to start, can migrate later)

### Schema

```sql
CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(7) UNIQUE NOT NULL,
    long_url TEXT NOT NULL,
    user_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    click_count INTEGER DEFAULT 0
);

CREATE INDEX idx_short_code ON urls(short_code);
CREATE INDEX idx_user_id ON urls(user_id);
CREATE INDEX idx_created_at ON urls(created_at);

-- Optional: Analytics table
CREATE TABLE clicks (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(7) NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referer TEXT
);

CREATE INDEX idx_clicks_short_code ON clicks(short_code);
```

---

## Step 4: APIs (5 min)

### Create Short URL

```http
POST /api/shorten
Content-Type: application/json

Request:
{
  "long_url": "https://www.example.com/very/long/url/path",
  "custom_code": "my-link" (optional),
  "expires_at": "2025-12-31T23:59:59Z" (optional)
}

Response: 201 Created
{
  "short_url": "https://short.ly/aB3xY",
  "short_code": "aB3xY",
  "long_url": "https://www.example.com/very/long/url/path",
  "created_at": "2025-11-14T10:00:00Z",
  "expires_at": null
}
```

### Redirect

```http
GET /{short_code}

Response: 302 Found
Location: https://www.example.com/very/long/url/path
```

### Get Analytics (optional)

```http
GET /api/analytics/{short_code}

Response: 200 OK
{
  "short_code": "aB3xY",
  "long_url": "https://www.example.com/very/long/url/path",
  "click_count": 1523,
  "created_at": "2025-11-14T10:00:00Z"
}
```

---

## Step 5: Core Algorithm - Short Code Generation

### Approach 1: Hash + Truncate

```python
import hashlib

def generate_short_code(long_url):
    # MD5 hash of URL
    hash_value = hashlib.md5(long_url.encode()).hexdigest()
    # Take first 7 characters
    short_code = hash_value[:7]
    return short_code
```

**Pros**: Deterministic, same URL → same short code
**Cons**: Collisions possible, not evenly distributed

### Approach 2: Random + Collision Check

```python
import random
import string

def generate_short_code():
    chars = string.ascii_letters + string.digits
    short_code = ''.join(random.choices(chars, k=7))

    # Check if exists in database
    while db.exists(short_code):
        short_code = ''.join(random.choices(chars, k=7))

    return short_code
```

**Pros**: Even distribution
**Cons**: Requires database lookup, possibility of many retries

### Approach 3: Base62 Encoding (RECOMMENDED)

```python
def base62_encode(num):
    charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    if num == 0:
        return charset[0]

    result = []
    while num:
        result.append(charset[num % 62])
        num //= 62

    return ''.join(reversed(result))

def generate_short_code(id):
    # Use auto-increment database ID
    return base62_encode(id)

# Example:
# ID: 125 → "2B"
# ID: 1000000 → "4c92"
# ID: 1000000000 → "15FTGg"
```

**Pros**:
- No collisions (unique ID)
- Short codes (62^7 = 3.5 trillion combinations)
- Fast (no database lookup)
- Predictable length

**Cons**:
- Sequential (slightly predictable)

**Character Set**: [a-z, A-Z, 0-9] = 62 characters
**Length**: 7 characters = 62^7 = 3.5 trillion URLs

---

## Step 6: Optimizations & Trade-offs (15 min)

### 1. Caching Strategy

**Cache Popular URLs**:
```python
def redirect(short_code):
    # Check cache first
    long_url = redis.get(f"url:{short_code}")

    if long_url is None:
        # Cache miss - query database
        long_url = db.query("SELECT long_url FROM urls WHERE short_code = ?", short_code)

        if long_url is None:
            return 404  # Not found

        # Cache for future requests (no expiration)
        redis.set(f"url:{short_code}", long_url)

    # Increment click count asynchronously
    queue.enqueue(increment_clicks, short_code)

    return redirect_to(long_url)
```

**Benefits**:
- <1ms response time (cache hit)
- 80% of traffic served from cache
- Reduced database load

### 2. Database Scaling

**Read Replicas**:
```
[Primary DB] ← writes
     ↓ replication
[Replica 1] ← reads
[Replica 2] ← reads
[Replica 3] ← reads
```

**Partitioning** (if needed at massive scale):
```
Shard by short_code hash:
hash(short_code) % num_shards = shard_id
```

### 3. Analytics

**Asynchronous Tracking**:
```python
def redirect(short_code):
    long_url = get_long_url(short_code)

    # Don't wait for analytics to complete
    background_task.enqueue(track_click, short_code, ip, user_agent)

    return redirect_to(long_url)
```

**Benefits**:
- Low latency for users
- Analytics don't block redirects
- Can batch writes to database

### 4. Custom Short URLs

**Implementation**:
```python
def create_short_url(long_url, custom_code=None):
    if custom_code:
        # Check if available
        if db.exists(custom_code):
            return error("Custom code already taken")
        short_code = custom_code
    else:
        # Generate using Base62
        id = db.get_next_id()
        short_code = base62_encode(id)

    db.insert(short_code, long_url)
    return short_code
```

**Validation**:
- Length: 4-15 characters
- Characters: [a-z, A-Z, 0-9, -, _]
- Reserved words: Block "api", "admin", etc.

### 5. Redirect Type

**301 vs 302**:

**301 Permanent Redirect**:
- Browser caches redirect
- Subsequent visits don't hit server
- Can't track all clicks
- Use if: Tracking not important

**302 Temporary Redirect**:
- No browser caching
- Every visit hits server
- Can track all clicks
- Use if: Analytics important (recommended)

### 6. Rate Limiting

**Prevent Abuse**:
```python
# Per IP rate limit
@rate_limit(max_requests=100, window=3600)  # 100/hour
def create_short_url():
    ...
```

**Benefits**:
- Prevent spam
- Protect database
- Fair usage

---

## Step 7: Advanced Considerations

### Security

**1. URL Validation**:
```python
def validate_url(url):
    # Check format
    if not url.startswith(('http://', 'https://')):
        return False

    # Prevent loops
    if 'short.ly' in url:
        return False

    # Check length
    if len(url) > 2048:
        return False

    return True
```

**2. Malicious URLs**:
- Check against blacklist
- Use Google Safe Browsing API
- Scan for phishing/malware

### Monitoring

**Metrics to Track**:
- Request rate (creates, redirects)
- Latency (p50, p95, p99)
- Error rate (404s, 500s)
- Cache hit ratio
- Database connections
- Storage usage

**Alerts**:
- Latency > 100ms
- Error rate > 1%
- Cache hit ratio < 80%
- Database CPU > 80%

### Cost Optimization

**Storage**:
```
3TB for 6B URLs over 5 years
PostgreSQL on m5.xlarge: ~$150/month
DynamoDB on-demand: ~$750/month
```

**Caching**:
```
Redis 10GB cache: ~$50/month
Saves 80% of database queries
ROI: Massive
```

### High Availability

**Multi-AZ Deployment**:
```
US-East-1a: LB, App Servers, DB Primary
US-East-1b: LB, App Servers, DB Replica
US-East-1c: LB, App Servers, DB Replica
```

**Disaster Recovery**:
- Database backups (daily)
- Point-in-time recovery
- Cross-region replication (optional)

---

## Complete System Design Diagram

```
                    ┌────────────────┐
                    │     Client     │
                    └────────┬───────┘
                             │
                    ┌────────▼───────┐
                    │   DNS / CDN    │ (for static assets)
                    └────────┬───────┘
                             │
                    ┌────────▼────────┐
                    │ Load Balancer   │ (AWS ALB)
                    │  SSL Termination│
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌─────────┐        ┌─────────┐       ┌─────────┐
    │  App    │        │  App    │       │  App    │
    │Server 1 │        │Server 2 │       │Server 3 │
    └────┬────┘        └────┬────┘       └────┬────┘
         │                  │                  │
         └──────────────────┼──────────────────┘
                            │
                   ┌────────┴────────┐
                   │                 │
           ┌───────▼──────┐  ┌──────▼────────┐
           │ Redis Cluster│  │  Message      │
           │   (Cache)    │  │  Queue        │
           └───────┬──────┘  │ (Analytics)   │
                   │         └───────────────┘
                   │
         ┌─────────┴──────────┐
         │                    │
   ┌─────▼──────┐      ┌─────▼────────┐
   │  Primary   │      │  Analytics   │
   │ PostgreSQL │      │  Database    │
   └─────┬──────┘      │ (ClickHouse) │
         │             └──────────────┘
   ┌─────┴──────┐
   │  Replicas  │
   │ (Read-only)│
   └────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (QPS, storage)?
- Analytics requirements?
- Custom URLs needed?
- Expiration needed?
- Geographic distribution?

**Topics to Cover**:
- Short code generation algorithm (Base62 recommended)
- Caching strategy (critical for performance)
- Database choice and scaling
- 301 vs 302 redirect trade-off
- Analytics implementation (async)

**Common Follow-ups**:
- "How would you handle 10x traffic?"
  → More read replicas, cache cluster, sharding
- "How do you prevent abuse?"
  → Rate limiting, URL validation, blacklisting
- "How do you ensure high availability?"
  → Multi-AZ, health checks, database replication

---

## Key Takeaways

1. **Simple Problem, Many Layers**: Seems simple, but scale reveals complexity
2. **Read-Heavy Workload**: Optimize for reads (caching, replicas)
3. **Base62 Encoding**: Best approach for short code generation
4. **Async Analytics**: Don't block redirects
5. **Cache Aggressively**: URLs don't change, cache forever
6. **Monitor Everything**: Track latency, errors, cache hit ratio

## Further Reading

- Bitly Engineering Blog
- "Designing a URL Shortening Service" - System Design Interview
- Base62 Encoding Implementation
- Redis Caching Strategies
