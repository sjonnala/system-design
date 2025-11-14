# Interview Framework: RADIO

## The RADIO Method

**R**equirements
**A**rchitecture
**D**ata Model
**I**nterface Design (APIs)
**O**ptimizations & Trade-offs

---

## R - Requirements (5-10 minutes)

### Functional Requirements

**Ask**: What should the system do?

**Questions to Ask**:
- What are the core features?
- Who are the users?
- What are the primary use cases?
- Any features we should prioritize?
- Any features we can deprioritize/skip?

**Example: URL Shortener**
```
Clarifying Questions:
Q: What features do we need?
A: Create short URL, redirect to original

Q: Do we need analytics?
A: Yes, track click counts

Q: Custom URLs allowed?
A: Nice to have, but optional

Q: Expiration of URLs?
A: Not required for MVP

Functional Requirements:
1. Create short URL from long URL
2. Redirect short URL to original URL
3. Track basic analytics (click count)
4. (Optional) Custom short URLs
```

### Non-Functional Requirements

**Ask**: How should the system perform?

**Questions to Ask**:
- Expected scale (users, requests/sec)?
- Latency requirements?
- Availability requirements?
- Consistency requirements?
- Geographic distribution?

**Example: URL Shortener**
```
Q: How many URLs shortened per month?
A: 100M new URLs/month

Q: How many redirects?
A: 10B redirects/month (100:1 read:write ratio)

Q: Latency requirement?
A: <100ms for redirects

Q: Availability?
A: 99.9% (3 nines)

Non-Functional Requirements:
1. 100M writes/month (40 writes/sec)
2. 10B reads/month (4K reads/sec)
3. Low latency (<100ms)
4. High availability (99.9%)
5. Read-heavy (100:1 ratio)
```

### Constraints

- Storage limits?
- Budget constraints?
- Regulatory requirements (GDPR, etc.)?
- Technology restrictions?

---

## A - Architecture (15-20 minutes)

### High-Level Design

**Start Simple**: Draw basic components

```
[Client] → [Load Balancer] → [Application Servers] → [Database]
                                     ↓
                                 [Cache]
```

**Explain Flow**:
1. User submits long URL
2. Load balancer routes to app server
3. App server generates short URL
4. Stores in database
5. Returns short URL to user

### Scale It Up

**Add components as needed**:

```
                    ┌─→ [App Server 1] ─┐
[Client] → [CDN] → [Load Balancer]       ├→ [Redis Cache]
                    └─→ [App Server 2] ─┘        ↓
                                           [Database Cluster]
                                           Primary + Replicas
```

### Identify Bottlenecks

- Single points of failure?
- Scalability limits?
- Performance bottlenecks?

**Address Each**:
- Database bottleneck → Caching, read replicas
- Single server → Horizontal scaling
- No redundancy → Multi-AZ deployment

---

## D - Data Model (10 minutes)

### Database Choice

**SQL vs NoSQL Decision**:

```
For URL Shortener:
- Simple schema (id, short_url, long_url, created_at)
- No complex relationships
- High read throughput needed

Could use either:
- SQL: Simple, ACID, but harder to scale
- NoSQL: Easier scaling, eventual consistency okay

Choice: NoSQL (DynamoDB or Cassandra)
- Better for read-heavy workload
- Easy horizontal scaling
- Eventual consistency acceptable
```

### Schema Design

**SQL Example**:
```sql
CREATE TABLE urls (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    short_code VARCHAR(7) UNIQUE NOT NULL,
    long_url TEXT NOT NULL,
    user_id BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    INDEX idx_short_code (short_code),
    INDEX idx_user_id (user_id)
);

CREATE TABLE analytics (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    short_code VARCHAR(7) NOT NULL,
    clicked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(45),
    user_agent TEXT,
    INDEX idx_short_code (short_code)
);
```

**NoSQL Example (DynamoDB)**:
```
Table: URLs
Partition Key: short_code
Attributes:
  - short_code (String)
  - long_url (String)
  - created_at (Number - timestamp)
  - click_count (Number)

Table: Analytics
Partition Key: short_code
Sort Key: timestamp
Attributes:
  - ip_address
  - user_agent
```

### Scaling Data

**Partitioning Strategy**:
```
For 100M URLs:
- Average URL entry: 500 bytes
- Total: 50GB (fits single DB)

For 10B analytics events:
- Average event: 200 bytes
- Total: 2TB (needs partitioning)

Partitioning: Hash-based on short_code
- Even distribution
- Efficient lookups
```

---

## I - Interface Design (5-10 minutes)

### API Endpoints

**RESTful API**:

```
POST /api/shorten
Request:
{
  "long_url": "https://example.com/very/long/url",
  "custom_code": "mylink" (optional)
}

Response:
{
  "short_url": "https://short.ly/abc123",
  "short_code": "abc123",
  "created_at": "2025-11-14T10:00:00Z"
}

---

GET /api/{short_code}
Redirects to long_url
HTTP 302 Found
Location: https://example.com/very/long/url

---

GET /api/analytics/{short_code}
Response:
{
  "short_code": "abc123",
  "long_url": "https://example.com/very/long/url",
  "click_count": 1523,
  "created_at": "2025-11-14T10:00:00Z"
}
```

### Error Handling

```
400 Bad Request: Invalid URL format
404 Not Found: Short code doesn't exist
409 Conflict: Custom code already exists
429 Too Many Requests: Rate limit exceeded
500 Internal Server Error: System error
```

---

## O - Optimizations & Trade-offs (10-15 minutes)

### Performance Optimizations

**1. Caching**:
```
Cache popular short URLs in Redis:
- 80/20 rule: 20% of URLs get 80% of traffic
- Cache these aggressively
- TTL: Don't expire (URLs don't change)

Cache Strategy:
- Cache-aside pattern
- On redirect, check cache first
- Miss → Query DB → Update cache
- Hit → Return immediately (<1ms)
```

**2. Database Optimization**:
```
Read Replicas:
- Read-heavy workload (100:1)
- Primary for writes
- Replicas for reads
- 5-10 replicas can handle 4K reads/sec

Indexing:
- Index on short_code (primary lookup)
- Make short_code unique
```

**3. CDN**:
```
Not applicable for redirects (dynamic content)
But useful for static assets (website UI)
```

**4. Short URL Generation**:
```
Option 1: Auto-increment ID + Base62 encoding
- ID: 123456789
- Base62: "aB3xY"
- Fast, predictable length
- Con: Sequential (predictable)

Option 2: Hash + Truncate
- Hash(long_url) → MD5 → Take first 7 chars
- Con: Collisions possible

Option 3: Random + Collision Check
- Generate random 7-char string
- Check if exists → Retry
- Con: Needs DB lookup on generation

Chosen: Auto-increment + Base62
- Simple, fast, no collisions
```

### Trade-offs Discussed

**1. Consistency vs Performance**:
```
Trade-off: Eventual consistency for analytics
- Click counts may be slightly stale
- Benefit: Better performance, scalability
- Acceptable: Analytics not real-time critical
```

**2. SQL vs NoSQL**:
```
Trade-off: NoSQL for better scalability
- Lose: ACID transactions, complex queries
- Gain: Easier scaling, lower latency
- Acceptable: Simple queries, eventual consistency okay
```

**3. Redirect Type**:
```
301 (Permanent) vs 302 (Temporary):

301: Browsers cache, faster subsequent visits
- Con: Can't track all clicks (cached redirects)

302: No browser cache, track every click
- Con: Slightly slower

Chosen: 302 for analytics tracking
```

**4. Custom URLs**:
```
Trade-off: Allow custom URLs
- Benefit: Better UX, branded links
- Cost: Collision handling, validation
- Implementation: Check availability, reserve
```

### Scaling Further

**10x Scale (1B URLs/month, 100B redirects/month)**:

```
1. Database Sharding:
   - Partition by short_code hash
   - 10 shards, 10B URLs per shard

2. Cache Cluster:
   - Redis cluster (not single instance)
   - 100GB+ cache capacity

3. Geographic Distribution:
   - Multi-region deployment
   - DynamoDB global tables
   - Route users to nearest region

4. Rate Limiting:
   - Prevent abuse
   - Per-user limits
   - API key required

5. Analytics Pipeline:
   - Async processing (Kafka)
   - Batch writes to analytics DB
   - Separate from main DB
```

---

## Example: Full RADIO Walkthrough

**Problem**: Design Instagram

### R - Requirements (5 min)

**Functional**:
- Upload photos
- Follow users
- View feed (following + discover)
- Like and comment on photos
- (Deprioritize: Stories, messages, video)

**Non-Functional**:
- 500M daily active users
- 100M photos uploaded/day
- Low latency (<100ms feed load)
- High availability (99.99%)

**Scale Calculations**:
- 100M photos/day = 1.2K photos/sec
- Avg photo: 2MB → 200TB/day storage
- Feed requests: 500M users × 10 views/day = 5B reads/day = 60K reads/sec

### A - Architecture (20 min)

```
                         ┌─→ [Photo Upload Service] → [S3]
[Client] → [API Gateway] ├─→ [Feed Service] → [Redis] → [Cassandra]
                         ├─→ [Social Graph Service] → [Neo4j]
                         └─→ [Recommendation Service]
```

**Components**:
- API Gateway: Route requests
- Photo Upload: Handle uploads → S3, generate thumbnails
- Feed Service: Generate user feeds
- Social Graph: Store follow relationships
- Cassandra: Feed data, metadata
- Redis: Cache feeds
- S3: Photo storage
- CDN: Serve photos

### D - Data Model (10 min)

```sql
-- PostgreSQL (for users, social graph)
Users (id, username, email, bio, profile_pic_url)
Follows (follower_id, followee_id, created_at)

-- Cassandra (for photos, feeds)
Photos (photo_id, user_id, s3_url, caption, created_at, likes_count)
Partition Key: user_id, Sort Key: created_at

Feeds (user_id, photo_id, created_at, author_id)
Partition Key: user_id, Sort Key: created_at
Pre-computed feeds for fast reads

-- Redis (cache)
Key: "feed:user_id"
Value: List of photo IDs
TTL: 5 minutes
```

### I - Interface (5 min)

```
POST /photos
GET /feed
POST /follow/{user_id}
POST /like/{photo_id}
GET /photos/{photo_id}
```

### O - Optimizations (15 min)

**Feed Generation**:
- Fan-out on write (pre-compute feeds)
- Celebrities: Fan-out on read (too many followers)
- Hybrid approach

**Caching**:
- Redis for active user feeds
- CDN for photos

**Trade-offs**:
- Eventual consistency for like counts (acceptable)
- Fan-out on write (storage for speed)
- Cassandra for scale (lose some query flexibility)

---

## Key Takeaways

1. **RADIO is a Guide**: Adapt based on interview flow
2. **Communicate**: Think out loud, explain reasoning
3. **Ask Questions**: Clarify before diving in
4. **Trade-offs**: Always discuss pros/cons
5. **Stay Flexible**: Interviewer may steer conversation

## Time Management

**45-minute interview**:
- Requirements: 5-10 min
- Architecture: 15-20 min
- Data Model: 5-10 min
- APIs: 5 min
- Optimizations: 10-15 min

**60-minute interview**:
- Add 15 min for deeper dives or additional components

## Practice

Practice this framework with these systems:
- URL Shortener (simple)
- Instagram (medium)
- Uber (complex)
- Netflix (complex)
- Design a rate limiter
- Design a distributed cache
