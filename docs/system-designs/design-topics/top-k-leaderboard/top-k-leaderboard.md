# Design a Top K Leaderboard System

**Companies**: Google (Play Games), Amazon (AWS GameLift), King (Candy Crush), Riot Games (League of Legends), Blizzard
**Difficulty**: Intermediate to Advanced
**Time**: 45-60 minutes

## Problem Statement

Design a real-time leaderboard system that tracks and displays the top K players/users based on their scores. The system should handle millions of concurrent users, support real-time score updates, and provide fast retrieval of rankings (e.g., Top 100, user's rank, users around a specific rank).

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Update Score**: Users can update their scores (increment or set new score)
2. **Get Top K**: Retrieve top K users by score (e.g., top 100)
3. **Get User Rank**: Retrieve a specific user's current rank
4. **Get Users Around Rank**: Get users ranked around a specific position (e.g., ranks 95-105)
5. **Get User Score**: Retrieve a specific user's current score
6. **Multiple Leaderboards** (optional): Support different leaderboard types (global, regional, friends)
7. **Time-based Leaderboards** (optional): Daily, weekly, monthly, all-time

**Prioritize**: Focus on score updates, top K retrieval, and user rank for MVP

### Non-Functional Requirements

1. **High Availability**: 99.99% uptime (gaming systems are critical)
2. **Low Latency**:
   - Score updates: <100ms
   - Top K retrieval: <50ms
   - Rank queries: <100ms
3. **Scalable**: Handle 100M+ active users
4. **Consistency**: Eventually consistent is acceptable (CAP theorem - prefer AP)
5. **High Throughput**: Handle 100K+ score updates per second

### Capacity Estimation

**Assumptions**:
- 100M total users
- 10M daily active users (DAU)
- Each user updates score 10 times per day
- Each user queries leaderboard 5 times per day
- Read:Write ratio = 1:2 (writes dominate in gaming)

**Write Load**:
```
10M users × 10 updates/day = 100M score updates/day
100M ÷ 86,400 seconds = ~1,200 writes/sec
Peak: 1,200 × 5 = ~6,000 writes/sec
```

**Read Load**:
```
10M users × 5 queries/day = 50M queries/day
50M ÷ 86,400 seconds = ~580 reads/sec
Peak: 580 × 5 = ~3,000 reads/sec
```

**Storage**:
```
Per user entry: user_id (8 bytes) + score (8 bytes) + metadata (100 bytes) = ~120 bytes
100M users × 120 bytes = 12 GB (in-memory feasible!)
With historical data (1 year): 12 GB × 365 = ~4.4 TB
```

**Memory Requirements** (for real-time leaderboard):
```
Active leaderboard: 12 GB (100M users)
Top K cache (K=10,000): ~1.2 MB
Regional shards (100 regions): 120 MB each
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                  Mobile/Web Clients                 │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 API Gateway / LB                    │
│              (Rate Limiting + Auth)                 │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Leaderboard     │  │   Query Service  │
│  Update Service  │  │   (Read-Heavy)   │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │   Redis Sorted Set  │
         │    (In-Memory)      │
         │   ZADD, ZRANGE,     │
         │   ZRANK, ZSCORE     │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌────────────────┐   ┌────────────────┐
│   PostgreSQL   │   │  Message Queue │
│  (Persistence) │   │     (Kafka)    │
└────────────────┘   └────────┬───────┘
                              ▼
                   ┌─────────────────────┐
                   │  Analytics Service  │
                   │   (Spark/Flink)     │
                   └─────────────────────┘
```

### Core Components

**1. API Gateway / Load Balancer**:
- SSL termination
- Authentication (JWT tokens)
- Rate limiting (prevent score manipulation abuse)
- Request routing

**2. Leaderboard Update Service**:
- Handles score update requests
- Validates score changes (anti-cheat checks)
- Updates Redis sorted sets
- Publishes events to Kafka
- Stateless, horizontally scalable

**3. Query Service**:
- Handles read requests (top K, rank, nearby ranks)
- Optimized for low-latency reads
- Caching layer for frequently accessed data
- Stateless, horizontally scalable

**4. Redis Cluster (Primary Data Store)**:
- **Sorted Sets** (ZSET): Perfect data structure for leaderboards
- In-memory: Ultra-fast reads/writes
- Persistence: RDB + AOF for durability
- Sharding: Partition by leaderboard type or region

**5. PostgreSQL (Persistent Store)**:
- Historical score data
- Audit logs
- User metadata
- Backup/recovery

**6. Message Queue (Kafka)**:
- Decouple score updates from analytics
- Event sourcing for score history
- Enable real-time analytics

**7. Analytics Service**:
- Process historical trends
- Detect anomalies (cheating detection)
- Generate daily/weekly/monthly reports

---

## Step 3: Data Model (10 min)

### Redis Data Structures

**Primary: Sorted Sets (ZSET)**

```redis
# Global Leaderboard
ZADD global_leaderboard 1500 "user:12345"  # score, member
ZADD global_leaderboard 2300 "user:67890"
ZADD global_leaderboard 1800 "user:11111"

# Get top 100 (descending order, highest scores first)
ZREVRANGE global_leaderboard 0 99 WITHSCORES

# Get user rank (0-indexed, 0 = highest score)
ZREVRANK global_leaderboard "user:12345"

# Get user score
ZSCORE global_leaderboard "user:12345"

# Get users around a rank (e.g., rank 95-105)
ZREVRANGE global_leaderboard 95 105 WITHSCORES

# Increment score
ZINCRBY global_leaderboard 50 "user:12345"

# Get user count
ZCARD global_leaderboard
```

**Secondary: Hash Maps (for user metadata)**

```redis
# User details
HSET user:12345 name "PlayerOne" country "US" level 25
HGET user:12345 name
```

**Leaderboard Naming Convention**:
```
Format: {scope}:{timeframe}:{region}
Examples:
- global:alltime:world
- global:daily:2024-11-15
- friends:weekly:2024-W46
- regional:monthly:2024-11:US
```

### PostgreSQL Schema

```sql
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    country_code VARCHAR(3),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_country ON users(country_code);

-- Score history (for analytics and audit)
CREATE TABLE score_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    score BIGINT NOT NULL,
    delta BIGINT,  -- change in score
    leaderboard_type VARCHAR(50),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_score_history_user ON score_history(user_id, created_at DESC);
CREATE INDEX idx_score_history_time ON score_history(created_at);

-- Leaderboard snapshots (for time-based leaderboards)
CREATE TABLE leaderboard_snapshots (
    id BIGSERIAL PRIMARY KEY,
    leaderboard_key VARCHAR(100),
    user_id BIGINT NOT NULL,
    rank INTEGER NOT NULL,
    score BIGINT NOT NULL,
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshots_key_time ON leaderboard_snapshots(leaderboard_key, snapshot_time);
```

---

## Step 4: APIs (5 min)

### Update Score

```http
POST /api/v1/scores
Authorization: Bearer <jwt_token>
Content-Type: application/json

Request:
{
  "user_id": "12345",
  "score_delta": 50,      // increment by 50
  "leaderboard_type": "global",
  "timestamp": "2025-11-15T10:30:00Z"
}

Response: 200 OK
{
  "user_id": "12345",
  "new_score": 1550,
  "rank": 2345,
  "previous_rank": 2456
}
```

### Get Top K Users

```http
GET /api/v1/leaderboard/top?k=100&type=global

Response: 200 OK
{
  "leaderboard_type": "global",
  "total_users": 10000000,
  "top_users": [
    {
      "rank": 1,
      "user_id": "67890",
      "username": "ProGamer99",
      "score": 9850,
      "country": "KR"
    },
    {
      "rank": 2,
      "user_id": "12345",
      "username": "ElitePlayer",
      "score": 9720,
      "country": "US"
    },
    ...
  ],
  "as_of": "2025-11-15T10:30:15Z"
}
```

### Get User Rank

```http
GET /api/v1/leaderboard/rank/{user_id}?type=global

Response: 200 OK
{
  "user_id": "12345",
  "username": "ElitePlayer",
  "rank": 2345,
  "score": 1550,
  "total_users": 10000000,
  "percentile": 99.98
}
```

### Get Users Around Rank

```http
GET /api/v1/leaderboard/nearby?rank=100&range=10&type=global

Response: 200 OK
{
  "leaderboard_type": "global",
  "center_rank": 100,
  "users": [
    {"rank": 90, "user_id": "11111", "score": 8500},
    {"rank": 91, "user_id": "22222", "score": 8490},
    ...
    {"rank": 100, "user_id": "33333", "score": 8400},
    ...
    {"rank": 110, "user_id": "44444", "score": 8300}
  ]
}
```

---

## Step 5: Core Algorithm & Data Structure

### Why Redis Sorted Sets?

**Perfect for Leaderboards**:
- **Sorted by score**: Automatically maintains order
- **O(log N) insertions**: Efficient updates
- **O(log N) rank lookups**: Fast rank queries
- **O(K) range queries**: Fast top K retrieval
- **Atomic operations**: Thread-safe

**Time Complexities**:
```
ZADD (update score):        O(log N)
ZREVRANK (get rank):        O(log N)
ZSCORE (get score):         O(1)
ZREVRANGE (get top K):      O(log N + K)
ZINCRBY (increment):        O(log N)
ZCARD (total count):        O(1)
```

### Implementation Pattern

```python
import redis

class LeaderboardService:
    def __init__(self):
        self.redis_client = redis.Redis(host='localhost', port=6379, decode_responses=True)
        self.leaderboard_key = "global:alltime:world"

    def update_score(self, user_id: str, score: int) -> dict:
        """
        Update user's score in leaderboard
        Returns: user's new rank and score
        """
        # Update score in Redis sorted set
        self.redis_client.zadd(self.leaderboard_key, {user_id: score})

        # Get new rank (0-indexed, 0 = highest)
        rank = self.redis_client.zrevrank(self.leaderboard_key, user_id)

        # Get current score
        current_score = self.redis_client.zscore(self.leaderboard_key, user_id)

        return {
            "user_id": user_id,
            "score": int(current_score),
            "rank": rank + 1  # Convert to 1-indexed
        }

    def increment_score(self, user_id: str, delta: int) -> dict:
        """
        Increment user's score by delta
        """
        new_score = self.redis_client.zincrby(self.leaderboard_key, delta, user_id)
        rank = self.redis_client.zrevrank(self.leaderboard_key, user_id)

        return {
            "user_id": user_id,
            "score": int(new_score),
            "rank": rank + 1
        }

    def get_top_k(self, k: int = 100) -> list:
        """
        Get top K users by score (descending)
        """
        # ZREVRANGE: descending order (highest first)
        # WITHSCORES: include scores
        results = self.redis_client.zrevrange(
            self.leaderboard_key,
            0,
            k - 1,
            withscores=True
        )

        leaderboard = []
        for idx, (user_id, score) in enumerate(results):
            leaderboard.append({
                "rank": idx + 1,
                "user_id": user_id,
                "score": int(score)
            })

        return leaderboard

    def get_user_rank(self, user_id: str) -> dict:
        """
        Get user's current rank and score
        """
        rank = self.redis_client.zrevrank(self.leaderboard_key, user_id)
        score = self.redis_client.zscore(self.leaderboard_key, user_id)
        total_users = self.redis_client.zcard(self.leaderboard_key)

        if rank is None:
            return {"error": "User not found in leaderboard"}

        return {
            "user_id": user_id,
            "rank": rank + 1,
            "score": int(score) if score else 0,
            "total_users": total_users,
            "percentile": ((total_users - rank) / total_users) * 100
        }

    def get_users_around_rank(self, rank: int, range_size: int = 10) -> list:
        """
        Get users around a specific rank
        E.g., rank=100, range=10 returns ranks 90-110
        """
        start = max(0, rank - range_size - 1)  # -1 for 0-indexing
        end = rank + range_size - 1

        results = self.redis_client.zrevrange(
            self.leaderboard_key,
            start,
            end,
            withscores=True
        )

        users = []
        for idx, (user_id, score) in enumerate(results):
            users.append({
                "rank": start + idx + 1,
                "user_id": user_id,
                "score": int(score)
            })

        return users
```

### Alternative Approaches

**Approach 1: SQL with Indexing** (Not recommended at scale)
```sql
-- Get top K
SELECT user_id, score,
       ROW_NUMBER() OVER (ORDER BY score DESC) as rank
FROM leaderboard
ORDER BY score DESC
LIMIT 100;

-- Get user rank
SELECT COUNT(*) + 1 as rank
FROM leaderboard
WHERE score > (SELECT score FROM leaderboard WHERE user_id = ?);
```

**Issues**:
- O(N) scans for rank calculation
- Slow at 100M+ users
- Index maintenance overhead

**Approach 2: Skip List** (Custom implementation)
- O(log N) average case
- More complex to implement
- Redis ZSET uses skip lists internally!

**Approach 3: Distributed Skip List with Cassandra**
- For multi-region, massive scale
- More operational complexity

---

## Step 6: Optimizations & Advanced Features (15 min)

### 1. Sharding Strategy

**Problem**: Single Redis instance limits at ~10M ops/sec

**Solution 1: Shard by Leaderboard Type**
```
Shard 1: global:alltime:*
Shard 2: regional:*:US
Shard 3: regional:*:EU
Shard 4: friends:*
Shard 5: daily:*
```

**Solution 2: Shard by Score Range** (for massive global leaderboards)
```
Shard 1: scores 0-1000
Shard 2: scores 1001-5000
Shard 3: scores 5001-10000
Shard 4: scores 10000+
```

**Trade-off**: Cross-shard queries become complex

### 2. Caching Layer

**Application-Level Cache**:
```python
from functools import lru_cache
import time

class CachedLeaderboard:
    def __init__(self):
        self.cache_ttl = 10  # seconds
        self.last_fetch = 0
        self.cached_top_k = []

    def get_top_k_cached(self, k=100):
        now = time.time()
        if now - self.last_fetch > self.cache_ttl:
            # Refresh cache
            self.cached_top_k = self.get_top_k(k)
            self.last_fetch = now

        return self.cached_top_k
```

**Benefits**:
- Reduce Redis load for popular queries (top 100)
- Sub-millisecond response times
- Can serve 100K+ QPS from app cache

### 3. Time-Based Leaderboards

**Daily Leaderboard Reset**:
```python
import datetime

def get_daily_leaderboard_key():
    today = datetime.date.today().isoformat()
    return f"daily:{today}:global"

# Cron job: Reset at midnight
def reset_daily_leaderboard():
    today_key = get_daily_leaderboard_key()
    # Archive yesterday's data
    yesterday = datetime.date.today() - datetime.timedelta(days=1)
    yesterday_key = f"daily:{yesterday.isoformat()}:global"

    # Copy to archive (PostgreSQL or S3)
    archive_leaderboard(yesterday_key)

    # Delete old key
    redis_client.delete(yesterday_key)
```

**Weekly/Monthly**: Similar pattern with different keys

### 4. Friends Leaderboard

**Approach 1: Filter on Read**
```python
def get_friends_leaderboard(user_id: str) -> list:
    # Get user's friends from social graph
    friends = get_friends(user_id)  # e.g., [123, 456, 789]

    # Get scores for each friend
    pipeline = redis_client.pipeline()
    for friend_id in friends:
        pipeline.zscore("global:alltime:world", friend_id)
    scores = pipeline.execute()

    # Sort and rank
    friend_scores = [(friends[i], scores[i]) for i in range(len(friends))]
    friend_scores.sort(key=lambda x: x[1], reverse=True)

    return friend_scores
```

**Approach 2: Materialized Friends Leaderboard**
```python
# Maintain separate sorted set per user
def update_score_with_friends(user_id: str, new_score: int):
    # Update global leaderboard
    redis_client.zadd("global:alltime:world", {user_id: new_score})

    # Update each friend's friends-leaderboard
    friends = get_friends(user_id)
    for friend_id in friends:
        friend_lb_key = f"friends:{friend_id}"
        redis_client.zadd(friend_lb_key, {user_id: new_score})
```

**Trade-off**: More memory, faster reads

### 5. Anti-Cheat / Score Validation

```python
def validate_score_update(user_id: str, new_score: int, old_score: int) -> bool:
    delta = new_score - old_score

    # Rate limiting: Max score increase per minute
    max_delta_per_minute = 1000
    if delta > max_delta_per_minute:
        log_suspicious_activity(user_id, delta)
        return False

    # Statistical anomaly detection
    avg_delta = get_average_score_delta(user_id)
    if delta > avg_delta * 10:  # 10x normal increase
        flag_for_review(user_id)
        return False

    return True
```

### 6. Percentile Calculation

```python
def get_user_percentile(user_id: str) -> float:
    rank = redis_client.zrevrank("global:alltime:world", user_id)
    total = redis_client.zcard("global:alltime:world")

    if rank is None:
        return 0.0

    # Percentile: (total - rank) / total * 100
    percentile = ((total - rank) / total) * 100
    return round(percentile, 2)
```

### 7. Handling Ties

**Default Redis Behavior**: Lexicographical order (by member key)

**Custom Tie-Breaking**:
```python
def add_score_with_tiebreaker(user_id: str, score: int, timestamp: int):
    # Store score with microsecond precision for tiebreaking
    # Higher score wins, earlier timestamp wins on tie
    score_with_tie = score + (timestamp / 1e15)  # Add tiny fraction
    redis_client.zadd("global:alltime:world", {user_id: score_with_tie})
```

---

## Step 7: Advanced Scalability Patterns

### Multi-Region Architecture

```
Region 1 (US-East)          Region 2 (EU-West)          Region 3 (APAC)
┌──────────────┐           ┌──────────────┐           ┌──────────────┐
│ Redis Cluster│           │ Redis Cluster│           │ Redis Cluster│
│  (Primary)   │◄─────────►│  (Primary)   │◄─────────►│  (Primary)   │
└──────────────┘  Sync     └──────────────┘  Sync     └──────────────┘
       │                          │                          │
       └──────────────────────────┴──────────────────────────┘
                                  │
                        Global Aggregation Service
                    (Merge regional leaderboards)
```

**Conflict Resolution**: Last-write-wins (LWW) with vector clocks

### Hybrid Approach: Hot/Cold Data

```
┌─────────────────────────────────────┐
│  Redis (Hot Data)                   │
│  - Top 10K users                    │
│  - Last 24 hours data               │
│  - Active users                     │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  PostgreSQL (Warm Data)             │
│  - Top 1M users                     │
│  - Last 30 days data                │
└──────────────┬──────────────────────┘
               │
               ▼
┌─────────────────────────────────────┐
│  S3 / Data Warehouse (Cold Data)    │
│  - Historical snapshots             │
│  - Analytics data                   │
└─────────────────────────────────────┘
```

### Batch Score Updates

```python
def batch_update_scores(updates: list[dict]):
    """
    Process multiple score updates in single transaction
    updates = [{"user_id": "123", "score": 1500}, ...]
    """
    pipeline = redis_client.pipeline()

    for update in updates:
        pipeline.zadd(
            "global:alltime:world",
            {update["user_id"]: update["score"]}
        )

    # Execute all updates atomically
    results = pipeline.execute()
    return results
```

**Benefits**: 10-100x throughput improvement

---

## Step 8: Monitoring & Observability

### Key Metrics to Track

**Performance Metrics**:
```
- Throughput: Score updates/sec, Queries/sec
- Latency: P50, P95, P99 for updates and queries
- Error rate: Failed validations, Redis errors
- Cache hit ratio: Application cache effectiveness
```

**Business Metrics**:
```
- Active users per leaderboard
- Score distribution (histogram)
- Cheating detection rate
- Peak concurrent users
```

**Infrastructure Metrics**:
```
- Redis memory usage
- Redis CPU usage
- Network throughput
- Replication lag (multi-region)
```

### Alerting Rules

```yaml
alerts:
  - name: high_latency
    condition: p99_latency > 200ms
    severity: warning

  - name: redis_memory_high
    condition: memory_usage > 85%
    severity: critical

  - name: score_update_spike
    condition: update_rate > baseline * 5
    severity: warning
    description: "Possible DDoS or cheating attack"
```

---

## Complete System Design Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                    Mobile/Web Clients                          │
│           (10M DAU, 6K writes/sec, 3K reads/sec)               │
└──────────────────────────┬─────────────────────────────────────┘
                           │
                  ┌────────▼────────┐
                  │   CDN / WAF     │ (DDoS protection)
                  └────────┬────────┘
                           │
                  ┌────────▼────────┐
                  │  Load Balancer  │ (AWS ALB / NGINX)
                  │  + API Gateway  │
                  └────────┬────────┘
                           │
        ┌──────────────────┼──────────────────┐
        ▼                  ▼                  ▼
┌──────────────┐   ┌──────────────┐   ┌──────────────┐
│   Update     │   │    Query     │   │    Admin     │
│   Service    │   │   Service    │   │   Service    │
│ (Stateless)  │   │ (Stateless)  │   │ (Analytics)  │
└──────┬───────┘   └──────┬───────┘   └──────┬───────┘
       │                  │                  │
       └──────────────────┼──────────────────┘
                          │
         ┌────────────────┼────────────────┐
         │                │                │
         ▼                ▼                ▼
┌────────────────┐ ┌────────────────┐ ┌────────────────┐
│ Redis Cluster  │ │  PostgreSQL    │ │  Kafka Cluster │
│  (Primary DS)  │ │ (Persistence)  │ │  (Events)      │
│  - Sorted Sets │ │ - Score Hist   │ │  - Audit       │
│  - 12 GB RAM   │ │ - User Data    │ │  - Analytics   │
└────────────────┘ └────────────────┘ └────────┬───────┘
                                               │
                                               ▼
                                      ┌─────────────────┐
                                      │ Analytics Engine│
                                      │ (Spark/Flink)   │
                                      │ - Trend Analysis│
                                      │ - Cheat Detect  │
                                      └─────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- What's the expected scale (DAU, total users)?
- What's the read:write ratio?
- Do we need real-time updates or eventual consistency is OK?
- Do we need multiple leaderboard types (global, regional, friends)?
- Time-based leaderboards (daily, weekly, monthly)?
- What's the acceptable latency for queries?
- How do we handle ties in scores?

**Topics to Cover**:
- Data structure choice (Redis Sorted Sets - explain why)
- Sharding strategy (by leaderboard type or score range)
- Caching layers (application cache, Redis, persistent DB)
- Time complexity analysis (O(log N) for updates/queries)
- Anti-cheat mechanisms
- Multi-region considerations

**Common Follow-ups**:
- "How would you handle 1 billion users?"
  → Hierarchical leaderboards, shard by region, hot/cold data separation
- "How do you prevent cheating?"
  → Rate limiting, statistical anomaly detection, score delta validation
- "What if Redis goes down?"
  → Replica sets, persistence (RDB+AOF), fallback to PostgreSQL
- "How do you handle daily/weekly resets?"
  → Cron jobs, key rotation, archival to S3/warehouse

---

## Key Takeaways

1. **Redis Sorted Sets are Perfect**: O(log N) operations, built-in ordering, atomic updates
2. **In-Memory is Critical**: Leaderboards need sub-50ms latency, only in-memory can deliver
3. **Sharding by Type**: Easier than sharding by score range, more predictable
4. **Cache Aggressively**: Top K queries are highly cacheable (10-60 second TTL)
5. **Eventual Consistency is OK**: Gaming leaderboards can tolerate slight delays
6. **Monitor for Anomalies**: Cheating detection is crucial for gaming systems
7. **Separate Reads/Writes**: CQRS pattern allows independent scaling

## Architecture Patterns Applied

- **CQRS**: Separate read (query) and write (update) services
- **Event Sourcing**: Kafka stores all score changes for audit/replay
- **Cache-Aside**: Application cache + Redis cache + persistent DB
- **Sharding**: Horizontal partitioning by leaderboard type
- **Circuit Breaker**: Fallback to cached data if Redis is slow

## Further Reading

- Redis Sorted Sets documentation
- "Designing Data-Intensive Applications" - Martin Kleppmann
- AWS GameLift leaderboard patterns
- Riot Games engineering blog (League of Legends leaderboards)
- Discord's use of sorted sets for user rankings
