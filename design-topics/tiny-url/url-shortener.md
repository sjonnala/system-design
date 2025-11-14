# URL Shortener - Complete System Design

> A comprehensive guide to designing a scalable URL shortening service like Bitly or TinyURL

## 📋 Table of Contents

1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [System Architecture](#system-architecture)
5. [Data Model](#data-model)
6. [API Design](#api-design)
7. [Core Algorithm](#core-algorithm)
8. [Caching Strategy](#caching-strategy)
9. [Database Design](#database-design)
10. [Scalability](#scalability)
11. [Security & Reliability](#security--reliability)
12. [Monitoring](#monitoring)
13. [Trade-offs](#trade-offs)

---

## Problem Statement

Design a URL shortening service that:
- Takes long URLs and generates short, unique URLs
- Redirects users from short URLs to original URLs
- Tracks analytics (click counts, referrers, etc.)
- Handles millions of URLs and billions of redirects
- Provides low-latency redirects (< 100ms)

**Examples:** Bitly, TinyURL, goo.gl (deprecated)

---

## Requirements

### Functional Requirements

✅ **Must Have:**
1. Create short URL from long URL
2. Redirect short URL to original URL
3. Short URL must be unique
4. URLs should be permanent (no expiration by default)

🎯 **Nice to Have:**
1. Custom short URLs (user-defined)
2. Click analytics and tracking
3. URL expiration
4. User accounts and URL management
5. API rate limiting

### Non-Functional Requirements

- **Availability:** 99.9% (High availability)
- **Latency:** < 100ms for redirects (p95)
- **Durability:** URLs must never be lost
- **Scalability:** Handle 100M new URLs per month
- **Read-Heavy:** 100:1 read-to-write ratio

---

## Capacity Estimation

### Traffic

```
New URLs per month:    100 million
Redirects per month:   10 billion (100:1 ratio)

Write QPS:  40-80 writes/second
Read QPS:   4,000-8,000 reads/second
Peak QPS:   ~8,000 total requests/second
```

### Storage

```
URLs Storage:
- 6 billion URLs over 5 years
- 500 bytes per URL
- Total: 3TB

Analytics Storage:
- 120 billion events per year
- 200 bytes per event
- Total: ~60TB (compressed)

Total: ~190TB (with backups and buffer)
```

### Bandwidth

```
Incoming:  40KB/sec (negligible)
Outgoing:  2MB/sec average, 4MB/sec peak
```

### Cache

```
Hot URLs (20% of traffic):  20 million URLs
Cache size:                  10-20GB Redis
Cache hit ratio target:      > 90%
```

**📊 Detailed Calculations:** See [scale-estimation-guide.md](scale-estimation-guide.md)

---

## System Architecture

```
                          [Client]
                             |
                             v
                      [CDN / DNS]
                             |
                             v
                   [Load Balancer (ALB)]
                             |
              +--------------+--------------+
              |              |              |
              v              v              v
        [App Server]   [App Server]   [App Server]
              |              |              |
              +------+-------+------+-------+
                     |              |
                     v              v
             [Redis Cluster]   [SQS Queue]
                     |              |
                     v              v
         [PostgreSQL Cluster]  [Analytics Worker]
         Primary + 3 Replicas
```

### Components

1. **Load Balancer:** AWS ALB for traffic distribution
2. **App Servers:** Stateless Node.js/Python/Go services
3. **Redis Cache:** Hot URL caching (90% hit rate)
4. **PostgreSQL:** Primary + read replicas for URL storage
5. **SQS Queue:** Asynchronous analytics processing
6. **Analytics Workers:** Process click events in batches

**🏗️ Detailed Architecture:** See [architecture.html](architecture.html)

---

## Data Model

### Database Schema (PostgreSQL)

```sql
CREATE TABLE urls (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(7) UNIQUE NOT NULL,
    long_url TEXT NOT NULL,
    user_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    click_count INTEGER DEFAULT 0,

    INDEX idx_short_code (short_code),
    INDEX idx_user_id (user_id),
    INDEX idx_created_at (created_at)
);

CREATE TABLE analytics (
    id BIGSERIAL PRIMARY KEY,
    short_code VARCHAR(7) NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    referrer TEXT,
    country VARCHAR(2),

    INDEX idx_short_code_time (short_code, clicked_at)
);

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    api_key VARCHAR(64) UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### Cache Schema (Redis)

```
Key:   url:{short_code}
Value: {long_url}
TTL:   No expiration (URLs don't change)

Example:
Key:   url:aB3xY
Value: "https://example.com/very/long/url/path"

Key:   stats:{short_code}
Value: {click_count, last_accessed}
TTL:   1 hour
```

---

## API Design

### 1. Create Short URL

```http
POST /api/v1/shorten
Content-Type: application/json
Authorization: Bearer {api_key}

Request:
{
  "long_url": "https://example.com/very/long/url/path",
  "custom_code": "my-link" (optional),
  "expires_at": "2025-12-31T23:59:59Z" (optional)
}

Response: 201 Created
{
  "short_url": "https://short.ly/aB3xY",
  "short_code": "aB3xY",
  "long_url": "https://example.com/very/long/url/path",
  "created_at": "2025-11-14T10:00:00Z",
  "expires_at": null
}

Error Response: 400 Bad Request
{
  "error": "invalid_url",
  "message": "URL format is invalid"
}

Error Response: 409 Conflict
{
  "error": "code_taken",
  "message": "Custom short code already in use"
}
```

### 2. Redirect

```http
GET /{short_code}

Response: 302 Found
Location: https://example.com/very/long/url/path

Error Response: 404 Not Found
"URL not found or expired"
```

### 3. Get URL Details

```http
GET /api/v1/urls/{short_code}
Authorization: Bearer {api_key}

Response: 200 OK
{
  "short_code": "aB3xY",
  "long_url": "https://example.com/very/long/url/path",
  "created_at": "2025-11-14T10:00:00Z",
  "expires_at": null,
  "click_count": 1523,
  "last_clicked": "2025-11-14T15:30:00Z"
}
```

### 4. Get Analytics

```http
GET /api/v1/urls/{short_code}/analytics
Authorization: Bearer {api_key}

Response: 200 OK
{
  "short_code": "aB3xY",
  "total_clicks": 1523,
  "clicks_by_date": {
    "2025-11-14": 342,
    "2025-11-13": 189
  },
  "top_referrers": [
    {"domain": "twitter.com", "count": 450},
    {"domain": "facebook.com", "count": 320}
  ],
  "top_countries": [
    {"country": "US", "count": 650},
    {"country": "UK", "count": 230}
  ]
}
```

### Rate Limiting

```
Free tier:     100 requests/hour per IP
Registered:    1,000 requests/hour per API key
Premium:       10,000 requests/hour per API key

Response Header:
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1634567890
```

---

## Core Algorithm: Short Code Generation

### Approach: Base62 Encoding (Recommended)

**Character Set:** `[a-z, A-Z, 0-9]` = 62 characters

**Algorithm:**

```python
def base62_encode(num):
    """Convert integer to Base62 string"""
    charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"

    if num == 0:
        return charset[0]

    result = []
    while num > 0:
        result.append(charset[num % 62])
        num //= 62

    return ''.join(reversed(result))

def base62_decode(code):
    """Convert Base62 string back to integer"""
    charset = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
    num = 0

    for char in code:
        num = num * 62 + charset.index(char)

    return num

# Usage
def create_short_url(long_url):
    # Insert into database, get auto-increment ID
    url_id = db.insert(long_url)  # e.g., 125

    # Generate short code
    short_code = base62_encode(url_id)  # "2B"

    # Update record with short code
    db.update(url_id, short_code=short_code)

    return short_code

# Examples:
# ID: 125       → "2B"
# ID: 1000000   → "4c92"
# ID: 1000000000 → "15FTGg"
```

**Why Base62?**

✅ **No Collisions:** Each ID maps to unique code
✅ **Short Codes:** 7 characters = 62^7 = 3.5 trillion URLs
✅ **Fast:** Simple math, no database lookup
✅ **URL-Safe:** Only alphanumeric characters

**Length Analysis:**
```
6 characters: 62^6 = 56.8 billion
7 characters: 62^7 = 3.5 trillion
8 characters: 62^8 = 218 trillion

Our need: 6 billion URLs → 7 characters sufficient
```

### Alternative: Custom Short Codes

```python
def create_custom_short_url(long_url, custom_code):
    # Validate format
    if not is_valid_code(custom_code):
        raise ValueError("Invalid format")

    # Check availability
    if db.exists(custom_code):
        raise ConflictError("Code already taken")

    # Reserve code
    db.insert(short_code=custom_code, long_url=long_url)

    return custom_code

def is_valid_code(code):
    # 4-15 characters, alphanumeric + hyphen
    return 4 <= len(code) <= 15 and code.replace('-', '').isalnum()
```

---

## Caching Strategy

### Cache-Aside Pattern

```python
def get_long_url(short_code):
    # 1. Try cache first
    cache_key = f"url:{short_code}"
    long_url = redis.get(cache_key)

    if long_url:
        # Cache hit (90% of traffic)
        return long_url

    # 2. Cache miss - query database
    long_url = db.query(
        "SELECT long_url FROM urls WHERE short_code = ?",
        short_code
    )

    if not long_url:
        return None  # 404

    # 3. Store in cache (no TTL - URLs don't change)
    redis.set(cache_key, long_url)

    return long_url
```

### Invalidation Strategy

```python
def update_url(short_code, new_long_url):
    # Update database
    db.update("UPDATE urls SET long_url = ? WHERE short_code = ?",
              new_long_url, short_code)

    # Invalidate cache
    redis.delete(f"url:{short_code}")

    # Next read will repopulate cache

def delete_url(short_code):
    # Mark as deleted in database
    db.update("UPDATE urls SET deleted_at = NOW() WHERE short_code = ?",
              short_code)

    # Invalidate cache
    redis.delete(f"url:{short_code}")
```

### Cache Warming

```python
# On application startup or periodically
def warm_cache():
    # Get most popular URLs
    popular_urls = db.query("""
        SELECT short_code, long_url
        FROM urls
        ORDER BY click_count DESC
        LIMIT 10000
    """)

    # Pre-load into cache
    for url in popular_urls:
        redis.set(f"url:{url.short_code}", url.long_url)
```

---

## Database Design

### Sharding Strategy (for 10x scale)

**Partition by:** Hash of short_code

```python
def get_shard_id(short_code):
    return hash(short_code) % NUM_SHARDS

# Example with 10 shards:
# short_code "aB3xY" → hash → shard 3
# short_code "xY7zW" → hash → shard 7
```

**Benefits:**
- Even distribution
- Independent scaling
- Fault isolation

**Trade-offs:**
- Cross-shard queries difficult
- Rebalancing complex
- Application-level routing needed

### Read Replicas

```
Primary DB: All writes
Replica 1:  25% of reads
Replica 2:  25% of reads
Replica 3:  25% of reads
Replica 4:  25% of reads
```

**Configuration:**
- Async replication (acceptable slight lag)
- Replication lag monitoring (< 1s)
- Automatic failover to replica if primary fails

---

## Scalability

### Horizontal Scaling

**Application Layer:**
- Stateless servers (easy to scale)
- Auto-scaling: 4-20 instances based on CPU/QPS
- Blue-green deployment for zero-downtime updates

**Cache Layer:**
- Redis cluster mode (multiple shards)
- Consistent hashing for key distribution
- 3-5 cache nodes for redundancy

**Database Layer:**
- Primary-replica architecture
- Add read replicas as needed (up to 10)
- Shard when single DB reaches limits (> 10M URLs)

### Vertical Scaling

**When to scale up:**
- Cache: Increase memory (20GB → 50GB)
- Database: Bigger instance (4 vCPU → 8 vCPU)
- Temporary solution before horizontal scaling

### Geographic Distribution

**Multi-Region Setup:**
```
US Region:
  - Load Balancer
  - App Servers (5)
  - Redis Cache (local)
  - Database Replica (read)

EU Region:
  - Load Balancer
  - App Servers (3)
  - Redis Cache (local)
  - Database Replica (read)

Primary Database: US Region
Write replication: US → EU (async)
```

**🔄 Detailed Flows:** See [flow-diagram.html](flow-diagram.html)

---

## Security & Reliability

### Security Measures

**1. Input Validation:**
```python
def validate_url(url):
    # Check format
    if not url.startswith(('http://', 'https://')):
        raise ValueError("Invalid protocol")

    # Check length
    if len(url) > 2048:
        raise ValueError("URL too long")

    # Prevent self-references
    if 'short.ly' in url:
        raise ValueError("Cannot shorten our own URLs")

    # Check against malicious URL database
    if is_malicious(url):
        raise ValueError("URL flagged as malicious")

    return True
```

**2. Rate Limiting:**
- IP-based rate limiting (100 req/hour)
- API key rate limiting (1,000 req/hour)
- Distributed rate limiting (Redis)

**3. DDoS Protection:**
- CloudFlare/AWS Shield
- Request throttling
- IP blacklisting

**4. Data Protection:**
- HTTPS only (SSL/TLS)
- API key authentication
- Secrets in AWS Secrets Manager

### Reliability

**1. Multi-AZ Deployment:**
- 3 availability zones
- Automatic failover
- Data replication across AZs

**2. Database Backups:**
- Automated daily snapshots
- Point-in-time recovery (35 days)
- Cross-region backup replication

**3. Monitoring & Alerts:**
```
Metrics to Monitor:
- API latency (p50, p95, p99)
- Error rate (4xx, 5xx)
- Cache hit ratio
- Database connections
- Queue depth

Alerts:
- Latency p95 > 100ms
- Error rate > 1%
- Cache hit ratio < 80%
- Database CPU > 80%
```

**4. Circuit Breaker:**
```python
class CircuitBreaker:
    def call_with_circuit_breaker(self, func):
        if self.state == 'OPEN':
            # Fail fast
            raise ServiceUnavailableError()

        try:
            result = func()
            self.on_success()
            return result
        except Exception as e:
            self.on_failure()
            raise
```

---

## Monitoring

### Key Metrics

**Application Metrics:**
- Request rate (req/sec)
- Error rate (%)
- Latency (p50, p95, p99)
- Active connections

**Cache Metrics:**
- Cache hit ratio (%)
- Cache memory usage (%)
- Eviction rate
- Connections

**Database Metrics:**
- Query latency (ms)
- Connection count
- Replication lag (seconds)
- Disk usage (%)

**Business Metrics:**
- URLs created per hour
- Redirects per hour
- Most popular URLs
- Conversion rate

### Dashboards

**Operational Dashboard:**
```
+------------------+------------------+
| Request Rate     | Error Rate       |
| 4,235 req/s      | 0.2%            |
+------------------+------------------+
| Latency p95      | Cache Hit Ratio  |
| 12ms             | 92%             |
+------------------+------------------+
```

**Business Dashboard:**
```
+------------------+------------------+
| URLs Created     | Total Redirects  |
| 138K today       | 14.2M today     |
+------------------+------------------+
| Top URL          | Top Referrer     |
| /promo2024       | twitter.com     |
+------------------+------------------+
```

---

## Trade-offs

### 1. Base62 vs Hash-Based Generation

| Base62 (Chosen) | Hash-Based |
|-----------------|------------|
| ✅ No collisions | ❌ Collisions possible |
| ✅ Predictable length | ✅ Unpredictable |
| ❌ Sequential (minor security concern) | ✅ Random |
| ✅ Simple logic | ❌ Collision handling needed |

**Decision:** Base62 for simplicity and no collisions

### 2. 301 vs 302 Redirect

| 301 Permanent | 302 Temporary (Chosen) |
|---------------|------------------------|
| ✅ Faster (browser cache) | ❌ Slower (no browser cache) |
| ❌ Can't track all clicks | ✅ Track every click |
| ✅ Better for SEO | ❌ Worse for SEO |

**Decision:** 302 for analytics tracking

### 3. SQL vs NoSQL

| PostgreSQL (Chosen) | DynamoDB |
|---------------------|----------|
| ✅ ACID transactions | ✅ Better scalability |
| ✅ Complex queries | ❌ Limited queries |
| ❌ Harder to scale | ✅ Auto-scaling |
| ✅ Familiar | ❌ Eventual consistency |

**Decision:** PostgreSQL initially, consider NoSQL at 10x scale

### 4. Sync vs Async Analytics

| Synchronous | Asynchronous (Chosen) |
|-------------|----------------------|
| ✅ Immediate updates | ❌ Slight delay (< 5s) |
| ❌ Blocks redirect | ✅ Fast redirect |
| ❌ Impacts latency | ✅ Better performance |

**Decision:** Async for better user experience

---

## Further Reading

- [System Architecture](architecture.html) - Detailed component diagrams
- [Flow Diagrams](flow-diagram.html) - Request/response flows
- [Scale Estimation Guide](scale-estimation-guide.md) - Capacity planning

## Interview Tips

1. **Start with Requirements:** Clarify traffic, latency, durability needs
2. **Show Calculations:** Back-of-the-envelope math for storage, QPS
3. **Draw Diagrams:** Visual representation of architecture
4. **Discuss Trade-offs:** Explain decisions (Base62 vs hash, 301 vs 302)
5. **Consider Edge Cases:** URL validation, custom codes, expiration
6. **Mention Operations:** Monitoring, scaling, disaster recovery
7. **Think About Cost:** AWS cost estimates show business awareness

## Common Follow-up Questions

**Q: How do you prevent abuse (malicious URLs)?**
- URL validation and blacklist checking
- Integration with Google Safe Browsing API
- User reporting mechanism
- Rate limiting per IP/user

**Q: How do you handle hot URLs (celebrity tweets)?**
- Aggressive caching (already in place)
- CDN for extremely viral URLs
- Read replicas distribute load
- Consider separate cache tier for top 1000 URLs

**Q: How do you migrate from SQL to NoSQL?**
- Dual-write phase (write to both)
- Gradual read migration (canary %)
- Validate data consistency
- Complete cutover after validation

---

**Designed by:** System Design Reference Guide
**Last Updated:** November 14, 2025
**Difficulty:** Beginner-Intermediate
**Time to Complete:** 45-60 minutes in interview
