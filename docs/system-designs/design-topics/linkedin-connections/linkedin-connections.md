# Design LinkedIn Connections System

**Companies**: LinkedIn, Facebook, Twitter (X), Instagram
**Difficulty**: Advanced
**Time**: 60 minutes
**Category**: Social Graph, ML & Recommendation Systems

## Problem Statement

Design a professional networking system like LinkedIn Connections. Users can send connection requests, build their professional network, discover connections through degrees of separation, and receive intelligent connection recommendations.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Send Connection Request**: User A can send connection request to User B
2. **Accept/Reject Request**: User B can accept or reject the request
3. **View Connections**: See all 1st-degree connections
4. **Network Degrees**: View 2nd and 3rd degree connections
5. **Connection Recommendations**: "People You May Know" feature
6. **Mutual Connections**: Display common connections between users
7. **Remove Connection**: Unfriend/disconnect from a connection
8. **Block User**: Prevent specific users from connecting

**Prioritize**: Focus on send, accept, view connections, and basic recommendations for MVP

### Non-Functional Requirements

1. **High Availability**: 99.99% uptime (professional network critical)
2. **Low Latency**: <200ms for connection queries, <500ms for recommendations
3. **Scalable**: Handle 800M+ users (LinkedIn scale)
4. **Consistency**: Strong consistency for connection state (accepted/pending/rejected)
5. **Graph Traversal**: Efficient degree separation calculations
6. **Privacy**: Respect connection visibility settings

### Capacity Estimation

**Assumptions**:
- 800M total users (LinkedIn scale)
- 200M DAU (Daily Active Users)
- Average connections per user: 500
- Connection requests sent per user: 5 per month
- Read:Write ratio: 50:1 (viewing connections >> making new ones)

**Write Load**:
```
Connection Requests: 800M users × 5 requests/month = 4B requests/month
Daily writes: 4B ÷ 30 = 133M writes/day
Write QPS: 133M ÷ 86,400 = ~1,500 writes/sec
Peak: 1,500 × 2 = ~3,000 writes/sec
```

**Read Load**:
```
Daily reads: 200M DAU × 20 connection views = 4B reads/day
Read QPS: 4B ÷ 86,400 = ~46,000 reads/sec
Peak: ~90,000 reads/sec
```

**Storage** (Graph Data):
```
Total connections: 800M users × 500 connections ÷ 2 = 200B edges
Storage per edge: 50 bytes (user_id, connection_id, timestamp, metadata)
Graph storage: 200B × 50 bytes = 10TB

User profiles: 800M × 2KB = 1.6TB
Total: ~12TB primary data
```

**Memory** (Cache):
```
Hot users (20% of users generate 80% traffic): 160M users
Cache per user: 500 connections × 20 bytes = 10KB
Total cache: 160M × 10KB = 1.6TB (distributed Redis)
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                  Client Apps                         │
│          (Web / Mobile / Desktop)                   │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 CDN / Load Balancer                  │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                  API Gateway                         │
│         (Auth, Rate Limiting, Routing)              │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        │              │              │
        ▼              ▼              ▼
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Connection  │ │ Recommend-  │ │   Graph     │
│  Service    │ │ ation Engine│ │  Service    │
└──────┬──────┘ └──────┬──────┘ └──────┬──────┘
       │               │               │
       └───────────────┼───────────────┘
                       ▼
        ┌──────────────┴──────────────┐
        │                             │
        ▼                             ▼
┌─────────────┐              ┌─────────────┐
│   Graph DB  │              │  Redis      │
│  (Neo4j/    │◄────────────►│  Cache      │
│   Dgraph)   │              │             │
└─────────────┘              └─────────────┘
        │
        ▼
┌─────────────┐
│   Message   │
│   Queue     │
│  (Kafka)    │
└─────────────┘
        │
        ▼
┌─────────────┐
│  ML Model   │
│  Training   │
│  Pipeline   │
└─────────────┘
```

### Components

**API Gateway**:
- Authentication (JWT tokens)
- Rate limiting (100 connection requests per day)
- Request routing
- SSL termination

**Connection Service**:
- Handle connection CRUD operations
- Manage connection states (pending, accepted, rejected, blocked)
- Validate connection requests
- Enforce privacy rules

**Graph Service**:
- Query social graph
- Calculate degrees of separation
- Find mutual connections
- Network traversal algorithms (BFS, DFS)

**Recommendation Engine**:
- "People You May Know" feature
- ML-based recommendations
- Consider: mutual connections, shared employers, schools, skills
- Real-time and batch processing

**Graph Database** (Neo4j / Amazon Neptune / Dgraph):
- Store social graph relationships
- Optimized for graph queries
- Support Cypher or Gremlin queries
- Handle bidirectional relationships

**Cache** (Redis):
- Cache user connections
- Cache recommendation results
- Cache frequently accessed user profiles
- TTL: 1 hour for connections, 24 hours for recommendations

**Message Queue** (Kafka):
- Async processing of connection events
- Feed updates
- Notification triggers
- ML model training data

---

## Step 3: Data Model (10 min)

### Graph Database Schema (Neo4j)

#### Nodes

```cypher
// User Node
CREATE (u:User {
    userId: "user123",
    name: "John Doe",
    email: "john@example.com",
    headline: "Software Engineer at Google",
    profilePictureUrl: "...",
    createdAt: timestamp(),
    location: "San Francisco, CA"
})

// Company Node (for recommendations)
CREATE (c:Company {
    companyId: "comp456",
    name: "Google",
    industry: "Technology"
})

// School Node (for recommendations)
CREATE (s:School {
    schoolId: "school789",
    name: "Stanford University"
})
```

#### Relationships

```cypher
// Connection Relationship (bidirectional)
CREATE (u1:User)-[c:CONNECTED_TO {
    connectedAt: timestamp(),
    status: "ACCEPTED"
}]->(u2:User)

// Pending Connection Request
CREATE (u1:User)-[r:REQUESTED {
    requestedAt: timestamp(),
    message: "Let's connect!",
    status: "PENDING"
}]->(u2:User)

// Blocked Relationship
CREATE (u1:User)-[b:BLOCKED {
    blockedAt: timestamp()
}]->(u2:User)

// User works at Company
CREATE (u:User)-[w:WORKS_AT {
    startDate: "2020-01",
    endDate: null,
    title: "Software Engineer"
}]->(c:Company)

// User attended School
CREATE (u:User)-[a:ATTENDED {
    startYear: 2015,
    endYear: 2019,
    degree: "B.S. Computer Science"
}]->(s:School)
```

### Relational Database Schema (PostgreSQL for metadata)

```sql
-- Users table (basic info)
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    headline TEXT,
    profile_picture_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    connection_count INTEGER DEFAULT 0,
    INDEX idx_email (email)
);

-- Connection requests (tracking state)
CREATE TABLE connection_requests (
    request_id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL, -- PENDING, ACCEPTED, REJECTED, BLOCKED
    message TEXT,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    FOREIGN KEY (from_user_id) REFERENCES users(user_id),
    FOREIGN KEY (to_user_id) REFERENCES users(user_id),
    INDEX idx_to_user_status (to_user_id, status),
    INDEX idx_from_user (from_user_id),
    UNIQUE (from_user_id, to_user_id)
);

-- Connections (accepted relationships)
CREATE TABLE connections (
    connection_id BIGSERIAL PRIMARY KEY,
    user_id_1 BIGINT NOT NULL,
    user_id_2 BIGINT NOT NULL,
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id_1) REFERENCES users(user_id),
    FOREIGN KEY (user_id_2) REFERENCES users(user_id),
    INDEX idx_user1 (user_id_1),
    INDEX idx_user2 (user_id_2),
    UNIQUE (user_id_1, user_id_2),
    CHECK (user_id_1 < user_id_2) -- Ensure canonical ordering
);

-- User activities (for ML features)
CREATE TABLE user_activities (
    activity_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50), -- profile_view, search, connection_request
    target_user_id BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_user_activity (user_id, created_at)
);
```

---

## Step 4: APIs (5 min)

### Send Connection Request

```http
POST /api/v1/connections/request
Authorization: Bearer <jwt_token>
Content-Type: application/json

Request:
{
  "to_user_id": 456789,
  "message": "I'd like to add you to my professional network."
}

Response: 201 Created
{
  "request_id": 123,
  "from_user_id": 123456,
  "to_user_id": 456789,
  "status": "PENDING",
  "requested_at": "2025-11-16T10:00:00Z"
}
```

### Accept/Reject Connection Request

```http
PUT /api/v1/connections/request/{request_id}
Authorization: Bearer <jwt_token>
Content-Type: application/json

Request:
{
  "action": "ACCEPT"  // or "REJECT"
}

Response: 200 OK
{
  "request_id": 123,
  "status": "ACCEPTED",
  "connection_id": 789,
  "responded_at": "2025-11-16T10:05:00Z"
}
```

### Get User Connections

```http
GET /api/v1/users/{user_id}/connections?page=1&limit=50
Authorization: Bearer <jwt_token>

Response: 200 OK
{
  "user_id": 123456,
  "total_connections": 542,
  "connections": [
    {
      "user_id": 456789,
      "name": "Jane Smith",
      "headline": "Product Manager at Meta",
      "profile_picture_url": "...",
      "connected_at": "2025-01-15T08:30:00Z",
      "mutual_connections": 23
    },
    ...
  ],
  "pagination": {
    "page": 1,
    "limit": 50,
    "total_pages": 11
  }
}
```

### Get Connection Recommendations

```http
GET /api/v1/recommendations/connections?limit=10
Authorization: Bearer <jwt_token>

Response: 200 OK
{
  "recommendations": [
    {
      "user_id": 789012,
      "name": "Bob Johnson",
      "headline": "Senior SWE at Amazon",
      "profile_picture_url": "...",
      "reason": "You both worked at Google",
      "mutual_connections": 15,
      "confidence_score": 0.87
    },
    ...
  ]
}
```

### Get Mutual Connections

```http
GET /api/v1/users/{user_id}/mutual-connections/{other_user_id}
Authorization: Bearer <jwt_token>

Response: 200 OK
{
  "user_id": 123456,
  "other_user_id": 456789,
  "mutual_connection_count": 23,
  "mutual_connections": [
    {
      "user_id": 111222,
      "name": "Alice Cooper",
      "headline": "Engineering Manager",
      "profile_picture_url": "..."
    },
    ...
  ]
}
```

### Check Connection Degree

```http
GET /api/v1/users/{user_id}/degree/{target_user_id}
Authorization: Bearer <jwt_token>

Response: 200 OK
{
  "from_user_id": 123456,
  "to_user_id": 789012,
  "degree": 2,
  "path": [
    {
      "user_id": 123456,
      "name": "You"
    },
    {
      "user_id": 456789,
      "name": "Jane Smith"
    },
    {
      "user_id": 789012,
      "name": "Bob Johnson"
    }
  ]
}
```

---

## Step 5: Core Algorithms

### Algorithm 1: Find Mutual Connections

**Approach**: Graph Intersection using Neo4j

```cypher
// Find mutual connections between user A and user B
MATCH (a:User {userId: $userA})-[:CONNECTED_TO]-(mutual)-[:CONNECTED_TO]-(b:User {userId: $userB})
WHERE a <> b AND mutual <> a AND mutual <> b
RETURN mutual.userId, mutual.name, mutual.headline
LIMIT 50
```

**Optimization**: Cache results for frequently queried pairs

### Algorithm 2: Calculate Degrees of Separation (BFS)

**Approach**: Breadth-First Search limited to 3 degrees

```cypher
// Find shortest path between two users (max 3 hops)
MATCH path = shortestPath(
  (a:User {userId: $userA})-[:CONNECTED_TO*1..3]-(b:User {userId: $userB})
)
RETURN length(path) as degree,
       [node in nodes(path) | {userId: node.userId, name: node.name}] as path
```

**Optimization**:
- Bidirectional BFS (search from both ends)
- Early termination at degree 3
- Cache degree calculations for popular users

### Algorithm 3: "People You May Know" Recommendation

**Approach**: Multi-factor ML-based ranking

```python
def recommend_connections(user_id, limit=10):
    """
    Recommendation factors:
    1. Mutual connections (strong signal)
    2. Shared employers/schools
    3. Similar skills/interests
    4. Profile views
    5. Common groups
    6. Geographic proximity
    """

    # Step 1: Get candidates (2nd degree connections)
    candidates = get_second_degree_connections(user_id)

    # Step 2: Calculate features for each candidate
    features = []
    for candidate in candidates:
        feature_vector = {
            'mutual_count': count_mutual_connections(user_id, candidate),
            'shared_employers': count_shared_employers(user_id, candidate),
            'shared_schools': count_shared_schools(user_id, candidate),
            'skill_similarity': calculate_skill_overlap(user_id, candidate),
            'profile_views': check_profile_interaction(user_id, candidate),
            'location_proximity': calculate_distance(user_id, candidate),
            'recency_score': calculate_recency(candidate)
        }
        features.append((candidate, feature_vector))

    # Step 3: ML model scoring
    scored_candidates = ml_model.predict_proba(features)

    # Step 4: Rank and filter
    ranked = sorted(scored_candidates, key=lambda x: x.score, reverse=True)

    return ranked[:limit]
```

**ML Model**: Gradient Boosting (XGBoost/LightGBM)
- Training data: Historical connection acceptances
- Features: Mutual connections, shared attributes, user activity
- Labels: Connection accepted (1) or ignored (0)
- Retraining: Weekly with new interaction data

**Graph-based approach** (simpler alternative):

```cypher
// Find 2nd degree connections with most mutual connections
MATCH (user:User {userId: $userId})-[:CONNECTED_TO]->(friend)-[:CONNECTED_TO]->(recommendation)
WHERE NOT (user)-[:CONNECTED_TO]-(recommendation)
  AND NOT (user)-[:REQUESTED]-(recommendation)
  AND user <> recommendation
WITH recommendation, COUNT(DISTINCT friend) as mutualCount
ORDER BY mutualCount DESC
LIMIT 10
RETURN recommendation.userId, recommendation.name, mutualCount
```

---

## Step 6: Optimizations & Trade-offs (15 min)

### 1. Graph Database Sharding

**Challenge**: Single graph database can't handle 800M users

**Solution**: Shard by User ID ranges

```
Shard 1: Users 0-199M
Shard 2: Users 200M-399M
Shard 3: Users 400M-599M
Shard 4: Users 600M-799M
```

**Trade-off**: Cross-shard queries (for connections across shards) require coordination

**Optimization**: Use consistent hashing for better distribution

### 2. Denormalization for Performance

**Strategy**: Duplicate connection count in user profile

```sql
-- Instead of COUNT(*) query every time
SELECT connection_count FROM users WHERE user_id = 123;

-- Update on each connection change (eventual consistency OK)
UPDATE users SET connection_count = connection_count + 1 WHERE user_id = 123;
```

**Trade-off**: Slight data staleness for massive performance gain

### 3. Caching Strategy

**Multi-level cache**:

```python
def get_user_connections(user_id):
    # L1: Application-level cache (local memory)
    if cached := app_cache.get(f"conn:{user_id}"):
        return cached

    # L2: Redis distributed cache
    if cached := redis.get(f"connections:{user_id}"):
        app_cache.set(f"conn:{user_id}", cached, ttl=300)
        return cached

    # L3: Graph database query
    connections = graph_db.query(
        "MATCH (u:User {userId: $uid})-[:CONNECTED_TO]-(conn) RETURN conn",
        uid=user_id
    )

    # Cache for future requests
    redis.setex(f"connections:{user_id}", 3600, connections)
    app_cache.set(f"conn:{user_id}", connections, ttl=300)

    return connections
```

**Cache invalidation**: Event-driven

```python
# When new connection created
def on_connection_accepted(user1_id, user2_id):
    # Invalidate both users' connection caches
    redis.delete(f"connections:{user1_id}")
    redis.delete(f"connections:{user2_id}")

    # Publish event for other services
    kafka.publish("connection.created", {
        "user1": user1_id,
        "user2": user2_id,
        "timestamp": now()
    })
```

### 4. Read Replicas for Graph Database

**Setup**:
```
[Primary Neo4j] ← writes
    ↓ replication
[Read Replica 1] ← connection queries
[Read Replica 2] ← degree calculations
[Read Replica 3] ← recommendations
```

**Routing logic**:
- Writes → Primary
- Heavy traversals (degree calculations) → Dedicated replica
- User connection queries → Load balanced across replicas

### 5. Recommendation Engine Optimization

**Hybrid approach**: Pre-compute + Real-time

**Batch processing** (Spark job, runs nightly):
```python
# Generate top 100 recommendations for each user
# Store in Redis sorted set
for user in all_users:
    recommendations = ml_model.predict(user)
    redis.zadd(f"recs:{user.id}", {
        rec.user_id: rec.score
        for rec in recommendations[:100]
    })
```

**Real-time adjustments**:
```python
def get_recommendations(user_id, limit=10):
    # Get pre-computed recommendations
    base_recs = redis.zrange(f"recs:{user_id}", 0, 50, desc=True)

    # Filter out already connected users (real-time check)
    user_connections = get_user_connections(user_id)
    filtered_recs = [r for r in base_recs if r not in user_connections]

    # Re-rank based on recent activity
    recent_activity = get_recent_activity(user_id, hours=24)
    boosted_recs = boost_by_activity(filtered_recs, recent_activity)

    return boosted_recs[:limit]
```

**Trade-off**: Slightly stale recommendations (refreshed daily) vs real-time accuracy

### 6. Connection Request Rate Limiting

**Per-user limits**:
```python
# Redis-based rate limiting
def can_send_connection_request(user_id):
    key = f"conn_req_limit:{user_id}:{today}"

    count = redis.incr(key)
    if count == 1:
        redis.expire(key, 86400)  # 24 hours

    if count > 100:  # Max 100 requests per day
        return False

    return True
```

**Global rate limiting** (prevent spam):
```python
# Sliding window rate limiter
def check_global_rate_limit(user_id):
    key = f"global_limit:{user_id}"
    window = 3600  # 1 hour
    max_requests = 20

    current_time = time.time()

    # Remove old requests outside window
    redis.zremrangebyscore(key, 0, current_time - window)

    # Count requests in current window
    request_count = redis.zcard(key)

    if request_count >= max_requests:
        return False

    # Add current request
    redis.zadd(key, {uuid.uuid4(): current_time})
    redis.expire(key, window)

    return True
```

---

## Step 7: Advanced Considerations

### Security & Privacy

**1. Privacy Levels**:
```sql
-- User privacy settings
CREATE TABLE privacy_settings (
    user_id BIGINT PRIMARY KEY,
    connection_visibility VARCHAR(20), -- PUBLIC, CONNECTIONS_ONLY, PRIVATE
    profile_visibility VARCHAR(20),
    allow_requests_from VARCHAR(20), -- EVERYONE, 2ND_DEGREE, NONE
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);
```

**2. Prevent Abuse**:
- Block users after 10 rejected requests
- Detect spam patterns (same message to 100+ users)
- CAPTCHA for suspicious activity

**3. Data Encryption**:
- Encrypt sensitive graph data at rest
- TLS for all API communications
- Encrypt PII in database

### Monitoring & Observability

**Key Metrics**:
- Connection request rate (per user, per day)
- Acceptance rate (% of requests accepted)
- Time to accept (latency between request and acceptance)
- Recommendation click-through rate (CTR)
- Graph query latency (P50, P95, P99)
- Cache hit ratio
- Mutual connection query performance

**Alerts**:
- Graph DB query latency > 500ms
- Recommendation service error rate > 1%
- Cache hit ratio < 70%
- Abnormal connection request spike (potential spam)

**Distributed Tracing** (Jaeger):
```
Request: GET /api/v1/users/123/connections
├─ API Gateway (5ms)
├─ Connection Service (120ms)
│  ├─ Redis Cache lookup (2ms) [MISS]
│  ├─ Graph DB query (100ms)
│  └─ Cache write (3ms)
└─ Response serialization (15ms)

Total: 140ms
```

### Scalability Strategies

**1. Geographic Distribution**:
```
US Region:
- Primary Neo4j + 3 replicas
- Redis cluster (6 nodes)
- API servers (auto-scale 10-100)

EU Region:
- Neo4j replica (read-only for GDPR compliance)
- Redis cluster
- API servers

APAC Region:
- Neo4j replica
- Redis cluster
- API servers
```

**2. Asynchronous Processing**:

```python
# Connection acceptance flow
def accept_connection_request(request_id, user_id):
    # Synchronous: Update connection state
    request = connection_requests.get(request_id)
    request.status = "ACCEPTED"
    request.save()

    # Create bidirectional connection in graph
    graph_db.execute(
        "MATCH (a:User {userId: $user1}), (b:User {userId: $user2}) "
        "CREATE (a)-[:CONNECTED_TO {connectedAt: timestamp()}]->(b), "
        "       (b)-[:CONNECTED_TO {connectedAt: timestamp()}]->(a)",
        user1=request.from_user_id,
        user2=user_id
    )

    # Asynchronous: Everything else
    kafka.publish("connection.accepted", {
        "request_id": request_id,
        "from_user": request.from_user_id,
        "to_user": user_id
    })

    # Async workers handle:
    # - Send notification to requester
    # - Update user connection counts
    # - Invalidate caches
    # - Update recommendation models
    # - Feed updates for both users

    return {"status": "accepted", "request_id": request_id}
```

**3. Database Partitioning**:

**Vertical partitioning**:
- Hot data (active users, recent connections) → SSD storage
- Cold data (inactive users, old requests) → HDD storage

**Horizontal partitioning** (sharding):
```python
def get_shard_for_user(user_id):
    """
    Consistent hashing for user distribution
    """
    shard_count = 16
    shard_id = hash(user_id) % shard_count
    return f"graph_db_shard_{shard_id}"
```

### High Availability & Disaster Recovery

**Multi-region deployment**:
```
Primary Region (US-East):
- Read-Write Neo4j cluster
- PostgreSQL primary
- Redis Sentinel

Secondary Region (US-West):
- Read-only Neo4j replicas
- PostgreSQL replica
- Redis cluster

Failover Region (EU):
- Read-only replicas
- Can be promoted to primary
```

**Data Backup**:
- Neo4j incremental backups (every hour)
- Full backup (daily)
- Point-in-time recovery (PostgreSQL)
- Cross-region replication (async)

**Disaster Recovery Plan**:
- RTO (Recovery Time Objective): 15 minutes
- RPO (Recovery Point Objective): 1 hour
- Automated failover for database
- DNS failover for traffic routing

---

## Step 8: ML-Powered Recommendations Deep Dive

### Feature Engineering

**User Features**:
```python
{
    "user_id": 123456,
    "features": {
        # Network features
        "connection_count": 542,
        "avg_connections_of_connections": 320,
        "clustering_coefficient": 0.42,

        # Professional features
        "companies": ["Google", "Meta"],
        "schools": ["Stanford", "MIT"],
        "skills": ["Python", "ML", "Distributed Systems"],
        "seniority_level": "Senior",

        # Engagement features
        "profile_views_last_30d": 150,
        "connection_requests_sent_last_30d": 12,
        "acceptance_rate": 0.85,

        # Temporal features
        "account_age_days": 1825,
        "last_active_days_ago": 2,
        "most_active_hours": [9, 10, 14, 15, 16]
    }
}
```

**Pairwise Features** (for recommendation scoring):
```python
def extract_pairwise_features(user_a, user_b):
    return {
        # Social features
        "mutual_connections": count_mutual_connections(user_a, user_b),
        "mutual_connection_ratio": mutual / min(conn_a, conn_b),
        "degree_separation": calculate_degree(user_a, user_b),

        # Professional overlap
        "shared_companies": len(set(companies_a) & set(companies_b)),
        "shared_schools": len(set(schools_a) & set(schools_b)),
        "skill_jaccard_similarity": jaccard(skills_a, skills_b),

        # Interaction features
        "profile_view_history": has_viewed_profile(user_a, user_b),
        "appeared_in_search": appeared_in_search(user_a, user_b),

        # Demographic
        "location_distance_km": calculate_distance(loc_a, loc_b),
        "industry_match": industry_a == industry_b,
        "seniority_difference": abs(seniority_a - seniority_b)
    }
```

### Model Architecture

**Two-stage approach**:

**Stage 1: Candidate Generation** (fast, broad)
```python
# Retrieve potential candidates (graph-based)
def generate_candidates(user_id, k=1000):
    """
    Get 2nd and 3rd degree connections
    Filter by basic criteria
    """
    candidates = graph_db.execute("""
        MATCH (user:User {userId: $uid})-[:CONNECTED_TO*2..3]-(candidate)
        WHERE NOT (user)-[:CONNECTED_TO]-(candidate)
          AND NOT (user)-[:BLOCKED]-(candidate)
          AND NOT (user)-[:REQUESTED]-(candidate)
        WITH candidate, COUNT(*) as pathCount
        ORDER BY pathCount DESC
        LIMIT 1000
        RETURN candidate.userId
    """, uid=user_id)

    return candidates
```

**Stage 2: Ranking** (ML model, precise)
```python
# LightGBM model for ranking
import lightgbm as lgb

class ConnectionRanker:
    def __init__(self):
        self.model = lgb.Booster(model_file='connection_ranker.txt')

    def rank_candidates(self, user_id, candidates):
        """
        Score each candidate and rank
        """
        features = []
        for candidate_id in candidates:
            feature_vec = extract_pairwise_features(user_id, candidate_id)
            features.append(feature_vec)

        # Predict probability of connection acceptance
        scores = self.model.predict(features)

        # Rank by score
        ranked = sorted(
            zip(candidates, scores),
            key=lambda x: x[1],
            reverse=True
        )

        return ranked
```

### Model Training Pipeline

```python
# Offline training (Spark job)
from pyspark.sql import SparkSession
from pyspark.ml import Pipeline
from pyspark.ml.feature import VectorAssembler
from pyspark.ml.classification import GBTClassifier

def train_recommendation_model():
    spark = SparkSession.builder.appName("ConnectionModel").getOrCreate()

    # Load training data (historical connections)
    data = spark.read.parquet("s3://training-data/connections/")

    # Feature engineering
    feature_cols = [
        "mutual_connections", "shared_companies", "shared_schools",
        "skill_similarity", "degree_separation", "profile_view_history",
        "location_distance", "industry_match", "seniority_difference"
    ]

    assembler = VectorAssembler(
        inputCols=feature_cols,
        outputCol="features"
    )

    # Train gradient boosting classifier
    gbt = GBTClassifier(
        featuresCol="features",
        labelCol="accepted",  # 1 if connection accepted, 0 otherwise
        maxIter=100,
        maxDepth=6,
        stepSize=0.1
    )

    pipeline = Pipeline(stages=[assembler, gbt])
    model = pipeline.fit(data)

    # Save model
    model.save("s3://models/connection-ranker/v2.0")

    # Evaluate
    predictions = model.transform(data)
    evaluate_model(predictions)

    return model
```

**Training Labels**:
- Positive: Connection request sent AND accepted
- Negative:
  - Request sent but rejected
  - User saw recommendation but didn't send request (implicit negative)

**Model Retraining**: Weekly or when performance degrades

### Online Serving

```python
# Real-time recommendation API
@app.get("/api/v1/recommendations/connections")
async def get_recommendations(
    current_user: User,
    limit: int = 10,
    refresh: bool = False
):
    # Check cache first (unless refresh requested)
    if not refresh:
        cached = await redis.get(f"recs:{current_user.id}")
        if cached:
            return json.loads(cached)[:limit]

    # Stage 1: Candidate generation (fast)
    candidates = await generate_candidates(current_user.id, k=1000)

    # Stage 2: ML ranking (slower but accurate)
    ranker = ConnectionRanker()
    ranked_candidates = ranker.rank_candidates(
        current_user.id,
        candidates
    )

    # Format response
    recommendations = []
    for candidate_id, score in ranked_candidates[:limit]:
        user_info = await get_user_info(candidate_id)
        mutual_count = await count_mutual_connections(
            current_user.id,
            candidate_id
        )

        # Explain why recommended
        reason = generate_recommendation_reason(
            current_user,
            candidate_id,
            mutual_count
        )

        recommendations.append({
            "user_id": candidate_id,
            "name": user_info.name,
            "headline": user_info.headline,
            "mutual_connections": mutual_count,
            "reason": reason,
            "confidence_score": score
        })

    # Cache result (24 hour TTL)
    await redis.setex(
        f"recs:{current_user.id}",
        86400,
        json.dumps(recommendations)
    )

    return recommendations
```

---

## Complete System Design Diagram

```
                    ┌────────────────────────┐
                    │   Client Applications  │
                    │ (Web / Mobile / API)   │
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │   CloudFlare CDN       │
                    │   + DDoS Protection    │
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │  AWS Application LB    │
                    │  (SSL Termination)     │
                    └───────────┬────────────┘
                                │
                    ┌───────────▼────────────┐
                    │     API Gateway        │
                    │  (Kong / AWS Gateway)  │
                    │  - Auth (JWT)          │
                    │  - Rate Limiting       │
                    │  - Request Validation  │
                    └───────────┬────────────┘
                                │
        ┌───────────────────────┼───────────────────────┐
        │                       │                       │
        ▼                       ▼                       ▼
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│ Connection   │      │  Graph       │      │ Recommend    │
│ Service      │      │  Service     │      │ Engine       │
│              │      │              │      │              │
│ - CRUD ops   │      │ - Traversal  │      │ - ML Model   │
│ - State mgmt │      │ - Degrees    │      │ - Ranking    │
│ - Validation │      │ - Mutual     │      │ - A/B Test   │
└──────┬───────┘      └──────┬───────┘      └──────┬───────┘
       │                     │                     │
       └─────────────────────┼─────────────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
        ▼                    ▼                    ▼
┌──────────────┐    ┌──────────────┐    ┌──────────────┐
│  Redis       │    │  Neo4j/      │    │ PostgreSQL   │
│  Cluster     │◄──►│  Dgraph      │    │  (Metadata)  │
│              │    │  (Graph DB)  │    │              │
│ - User cache │    │              │    │ - Users      │
│ - Rec cache  │    │ - Nodes:     │    │ - Requests   │
│ - Rate limit │    │   Users,     │    │ - Activities │
│ - Sessions   │    │   Companies, │    │              │
└──────────────┘    │   Schools    │    └──────────────┘
                    │ - Edges:     │             │
                    │   CONNECTED, │             │
                    │   WORKS_AT,  │             │
                    │   ATTENDED   │             │
                    └──────┬───────┘             │
                           │                     │
                           └─────────┬───────────┘
                                     │
                         ┌───────────▼───────────┐
                         │   Apache Kafka        │
                         │   Message Broker      │
                         │                       │
                         │ Topics:               │
                         │ - connection.created  │
                         │ - connection.removed  │
                         │ - request.sent        │
                         │ - recommendation.shown│
                         └───────────┬───────────┘
                                     │
        ┌────────────────────────────┼────────────────┐
        │                            │                │
        ▼                            ▼                ▼
┌──────────────┐            ┌──────────────┐  ┌─────────────┐
│ Notification │            │  ML Training │  │  Analytics  │
│  Service     │            │  Pipeline    │  │  Service    │
│              │            │  (Spark)     │  │             │
│ - Email      │            │              │  │ - Metrics   │
│ - Push       │            │ - Feature    │  │ - Dashboards│
│ - In-app     │            │   extraction │  │ - Reports   │
└──────────────┘            │ - Model      │  └─────────────┘
                            │   training   │
                            │ - Evaluation │
                            └──────┬───────┘
                                   │
                         ┌─────────▼────────┐
                         │   S3 Storage     │
                         │ - Training data  │
                         │ - Model artifacts│
                         │ - Backups        │
                         └──────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected user scale (millions vs billions)?
- Geographic distribution requirements?
- Privacy constraints (GDPR, data residency)?
- Recommendation freshness requirements (real-time vs batch)?
- Connection limits per user?

**Topics to Cover**:
- Graph database choice (Neo4j vs alternatives)
- Bidirectional relationship handling
- BFS for degree calculation
- ML recommendation pipeline
- Caching strategy (critical for performance)
- Eventual consistency trade-offs

**Common Follow-ups**:
- "How would you handle 1 billion users?"
  → Shard graph database, geo-distributed deployment, more aggressive caching
- "How do you prevent connection spam?"
  → Rate limiting, behavioral analysis, reputation scores, CAPTCHA
- "How do you ensure recommendation quality?"
  → A/B testing, CTR monitoring, user feedback loops, model retraining
- "What if Neo4j becomes a bottleneck?"
  → Read replicas, caching layer, denormalization to PostgreSQL for simple queries

---

## Key Takeaways

1. **Graph Database is Essential**: Social networks are inherently graphs; use purpose-built graph DBs (Neo4j, Dgraph, Neptune)
2. **Bidirectional Relationships**: Always create edges in both directions for fast lookups
3. **Multi-tier Caching**: Application → Redis → Graph DB for optimal performance
4. **ML for Recommendations**: Simple graph queries for candidates, ML for ranking
5. **Asynchronous Processing**: Offload non-critical tasks (notifications, analytics) to message queues
6. **Privacy First**: Design privacy settings into the data model from day 1
7. **Monitor Graph Queries**: Graph traversals can be expensive; track and optimize
8. **Sharding Strategy**: Plan for horizontal scaling early

## Further Reading

- **Books**:
  - "Graph Databases" by Ian Robinson, Jim Webber
  - "Designing Data-Intensive Applications" by Martin Kleppmann
- **Papers**:
  - Facebook TAO: "TAO: Facebook's Distributed Data Store for the Social Graph"
  - LinkedIn: "Scaling the LinkedIn Network Graph"
- **Technologies**:
  - Neo4j Cypher Query Language
  - Apache TinkerPop (Gremlin)
  - AWS Neptune
  - Dgraph
- **ML**:
  - Graph Neural Networks (GNN) for recommendations
  - Link Prediction algorithms
