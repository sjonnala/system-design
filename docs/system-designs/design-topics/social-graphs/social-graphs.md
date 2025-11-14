# Design a Social Network Platform (Instagram / Twitter / Facebook / Reddit)

**Companies**: Meta (Facebook, Instagram), Twitter, Reddit, TikTok
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a social networking platform that supports user profiles, posts, feeds, follows, likes, comments, and recommendations. The system should handle billions of users and provide real-time updates.

---

## Step 1: Requirements (10 min)

### Functional Requirements

**Core Features** (MVP):
1. **User Management**: Registration, authentication, profiles
2. **Post Creation**: Create posts with text, images, videos
3. **Social Graph**: Follow/unfollow users
4. **Feed Generation**: Personalized timeline of posts
5. **Interactions**: Like, comment, share posts
6. **Notifications**: Real-time updates for interactions

**Extended Features**:
7. **Search**: Find users and content
8. **Direct Messaging**: Private conversations
9. **Stories**: Ephemeral content (24-hour expiry)
10. **Recommendations**: Suggested users and content
11. **Analytics**: Post insights, engagement metrics

**Prioritize**: Focus on posts, feeds, follows, and likes for core design

### Non-Functional Requirements

1. **Scalability**: Handle 2B users, 500M DAU
2. **High Availability**: 99.99% uptime
3. **Low Latency**:
   - Feed load: <500ms
   - Post creation: <200ms
   - Likes/comments: <100ms
4. **Consistency**: Eventual consistency OK for most features
5. **Durability**: No data loss for posts

### Capacity Estimation

**Assumptions**:
- 2 billion total users
- 500M DAU (Daily Active Users)
- 50M posts per day (10% of DAU create content)
- 200M comments per day
- 2B likes per day
- Each user refreshes feed 20 times/day

**Traffic Load**:
```
Write Operations:
- Posts: 50M/day ÷ 86,400 sec = ~600 writes/sec
- Comments: 200M/day = ~2,300 writes/sec
- Likes: 2B/day = ~23,000 writes/sec
- Total writes: ~26,000 writes/sec
- Peak (3×): ~80,000 writes/sec

Read Operations:
- Feed requests: 500M DAU × 20 = 10B requests/day
- Feed QPS: 10B ÷ 86,400 = ~115,000 QPS
- Peak (3×): ~350,000 QPS
```

**Storage**:
```
Per Post Storage:
- Text: 1 KB (content + metadata)
- Image (80% of posts): 500 KB average
- Video (20% of posts): 5 MB average

Daily Storage:
- Text: 50M × 1 KB = 50 GB
- Images: 40M × 500 KB = 20 TB
- Videos: 10M × 5 MB = 50 TB
- Total: ~70 TB/day

Yearly: 70 TB × 365 = ~25 PB/year
5-year: ~125 PB total
```

**Bandwidth**:
```
Feed Read (metadata only): 115K QPS × 50 posts × 1 KB = ~5 GB/s
Media (via CDN): 115K QPS × 20 images × 500 KB = ~1 TB/s
  → 99% CDN hit rate → 10 GB/s origin bandwidth
```

---

## Step 2: High-Level Architecture (15 min)

### System Overview

```
┌────────────────────────────────────────────────────────┐
│                 CLIENTS (iOS, Android, Web)            │
└──────────────────────┬─────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────┐
│            CDN (CloudFront, Fastly)                    │
│         Static Assets + Media Delivery                 │
└──────────────────────┬─────────────────────────────────┘
                       │
                       ▼
┌────────────────────────────────────────────────────────┐
│          Load Balancer + API Gateway                   │
│      (Kong, AWS ALB, Rate Limiting, Auth)              │
└──────────────────────┬─────────────────────────────────┘
                       │
      ┌────────────────┼────────────────┐
      ▼                ▼                ▼
┌──────────┐   ┌──────────┐    ┌──────────┐
│  User    │   │  Post    │    │  Feed    │
│ Service  │   │ Service  │    │ Service  │
└──────────┘   └──────────┘    └──────────┘
      │                │                │
      ▼                ▼                ▼
┌────────────────────────────────────────────────────────┐
│               Message Queue (Kafka)                    │
│     Topics: post.created, user.followed, etc.          │
└──────────────────────┬─────────────────────────────────┘
      │                │                │
      ▼                ▼                ▼
┌──────────┐   ┌──────────┐    ┌──────────┐
│PostgreSQL│   │ Cassandra│    │  Neo4j   │
│ (Users,  │   │(Timelines│    │ (Social  │
│  Posts)  │   │  Feeds)  │    │  Graph)  │
└──────────┘   └──────────┘    └──────────┘
      │                │                │
      └────────────────┴────────────────┘
                       │
                       ▼
           ┌───────────────────────┐
           │   Redis Cache Layer   │
           │  (Sessions, Feeds,    │
           │   Hot Data)           │
           └───────────────────────┘
```

### Key Components

**1. CDN / Edge Network**
- Serves static assets (images, videos, CSS, JS)
- 99%+ cache hit rate for media
- Global distribution (200+ edge locations)
- DDoS protection

**2. API Gateway**
- Authentication (JWT, OAuth 2.0)
- Rate limiting (per user, per IP)
- Request routing
- API versioning

**3. Microservices**
- **User Service**: Registration, profiles, settings
- **Post Service**: Create, edit, delete posts
- **Feed Service**: Generate personalized timelines
- **Graph Service**: Follow/unfollow, social connections
- **Notification Service**: Push notifications, emails
- **Search Service**: User and content discovery
- **Media Service**: Upload, processing, storage

**4. Databases**
- **PostgreSQL**: Users, posts (sharded by ID)
- **Cassandra**: Pre-computed feeds, time-series data
- **Neo4j**: Social graph (followers, friends)
- **Redis**: Cache layer (sessions, hot posts, feeds)
- **S3**: Media storage (images, videos)

**5. Message Queue (Kafka)**
- Asynchronous processing
- Event-driven architecture
- Fan-out for feed updates
- Analytics pipeline

---

## Step 3: Data Model (10 min)

### Database Choice

**Users & Posts: PostgreSQL (Sharded)**
- ACID transactions for critical data
- Complex queries with indexes
- Horizontal sharding by user_id/post_id

**Timelines: Cassandra**
- High write throughput (fan-out on write)
- Time-series data model
- Denormalized for fast reads

**Social Graph: Neo4j**
- Graph traversal (followers, mutual friends)
- Recommendation algorithms
- Shortest path queries

**Cache: Redis**
- Sub-millisecond latency
- Session management
- Feed caching (TTL: 5 min)

### Schema Design

```sql
-- PostgreSQL: Users Table (Sharded by user_id)
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    full_name VARCHAR(255),
    bio TEXT,
    profile_pic_url TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);

-- PostgreSQL: Posts Table (Sharded by post_id)
CREATE TABLE posts (
    post_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    content TEXT,
    media_urls TEXT[],  -- Array of S3 URLs
    hashtags TEXT[],
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    likes_count INTEGER DEFAULT 0,
    comments_count INTEGER DEFAULT 0,
    shares_count INTEGER DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_posts_user_created ON posts(user_id, created_at DESC);
CREATE INDEX idx_posts_created ON posts(created_at DESC);

-- PostgreSQL: Follows Table (Sharded by follower_id)
CREATE TABLE follows (
    follower_id BIGINT NOT NULL,
    following_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (follower_id, following_id)
);

CREATE INDEX idx_follows_following ON follows(following_id, created_at DESC);

-- PostgreSQL: Likes Table (Sharded by post_id)
CREATE TABLE likes (
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (post_id, user_id)
);

CREATE INDEX idx_likes_user ON likes(user_id, created_at DESC);

-- PostgreSQL: Comments Table (Sharded by post_id)
CREATE TABLE comments (
    comment_id BIGSERIAL PRIMARY KEY,
    post_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    parent_comment_id BIGINT,  -- For nested replies
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP,
    likes_count INTEGER DEFAULT 0,
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_comments_post ON comments(post_id, created_at ASC);
CREATE INDEX idx_comments_user ON comments(user_id, created_at DESC);
```

```sql
-- Cassandra: User Timeline (Fan-out on Write)
CREATE TABLE user_timeline (
    user_id BIGINT,
    post_timestamp TIMESTAMP,
    post_id BIGINT,
    author_id BIGINT,
    content TEXT,
    media_urls LIST<TEXT>,
    likes_count INT,
    comments_count INT,
    PRIMARY KEY (user_id, post_timestamp)
) WITH CLUSTERING ORDER BY (post_timestamp DESC);

-- Cassandra allows fast retrieval of user's timeline
-- SELECT * FROM user_timeline WHERE user_id = ? LIMIT 50
```

```cypher
// Neo4j: Social Graph Model
// User Node
CREATE (u:User {
    user_id: 12345,
    username: "john_doe",
    followers_count: 1500,
    following_count: 300
})

// Follow Relationship
CREATE (follower:User)-[:FOLLOWS {created_at: timestamp()}]->(following:User)

// Query: Get followers
MATCH (follower:User)-[:FOLLOWS]->(user:User {user_id: $userId})
RETURN follower
LIMIT 100

// Query: Mutual friends
MATCH (user:User {user_id: $userId})-[:FOLLOWS]->(friend)-[:FOLLOWS]->(mutualFriend)-[:FOLLOWS]->(user)
WHERE mutualFriend <> user
RETURN mutualFriend

// Query: Friend suggestions (2nd degree connections)
MATCH (user:User {user_id: $userId})-[:FOLLOWS]->()-[:FOLLOWS]->(suggestion)
WHERE NOT (user)-[:FOLLOWS]->(suggestion) AND suggestion <> user
RETURN suggestion, count(*) as mutualConnections
ORDER BY mutualConnections DESC
LIMIT 10
```

---

## Step 4: APIs (5 min)

### User APIs

```http
POST /api/v1/users/register
POST /api/v1/users/login
GET /api/v1/users/{userId}/profile
PUT /api/v1/users/{userId}/profile
POST /api/v1/users/{userId}/follow/{targetUserId}
DELETE /api/v1/users/{userId}/unfollow/{targetUserId}
GET /api/v1/users/{userId}/followers
GET /api/v1/users/{userId}/following
```

### Post APIs

```http
POST /api/v1/posts
{
  "content": "Hello world!",
  "media_urls": ["https://cdn.example.com/image.jpg"],
  "hashtags": ["#tech", "#coding"]
}

Response: 201 Created
{
  "post_id": "123456",
  "user_id": "789",
  "content": "Hello world!",
  "created_at": "2025-11-14T10:00:00Z",
  "likes_count": 0,
  "comments_count": 0
}

GET /api/v1/posts/{postId}
PUT /api/v1/posts/{postId}
DELETE /api/v1/posts/{postId}
```

### Feed APIs

```http
GET /api/v1/users/{userId}/feed?limit=50&cursor=abc123

Response: 200 OK
{
  "posts": [
    {
      "post_id": "123",
      "author": {
        "user_id": "456",
        "username": "jane_doe",
        "profile_pic": "https://cdn.../profile.jpg"
      },
      "content": "Check out my new blog!",
      "media_urls": ["https://cdn.../image.jpg"],
      "created_at": "2025-11-14T09:30:00Z",
      "likes_count": 42,
      "comments_count": 5,
      "is_liked_by_user": false
    }
  ],
  "next_cursor": "xyz789"
}
```

### Interaction APIs

```http
POST /api/v1/posts/{postId}/like
DELETE /api/v1/posts/{postId}/unlike
GET /api/v1/posts/{postId}/likes

POST /api/v1/posts/{postId}/comments
{
  "content": "Great post!",
  "parent_comment_id": null
}

GET /api/v1/posts/{postId}/comments?limit=20&cursor=abc
```

---

## Step 5: Feed Generation - Core Algorithm (15 min)

### Strategy 1: Fan-out on Write (Push Model - Twitter Style)

**When to use**: Most users have <10K followers

**How it works**:
```python
def on_post_created(post, author_id):
    """
    When user creates a post, push it to all followers' feeds
    """
    # 1. Get all followers
    followers = graph_db.get_followers(author_id)

    # 2. Write post to each follower's timeline (parallel)
    for follower_id in followers:
        cassandra.write(
            table="user_timeline",
            user_id=follower_id,
            post_timestamp=post.created_at,
            post_data=post
        )

    # 3. Invalidate Redis cache for affected users
    for follower_id in followers:
        redis.delete(f"feed:{follower_id}")
```

**Pros**:
- ✅ Ultra-fast reads (<10ms from Cassandra)
- ✅ Simple read path (just query user_timeline)
- ✅ Scalable for reads (100K+ QPS)

**Cons**:
- ❌ Slow writes for influencers (10M followers = 10M writes)
- ❌ Wasted work for inactive users
- ❌ High storage overhead (duplicate data)

---

### Strategy 2: Fan-out on Read (Pull Model - Instagram Style)

**When to use**: Influencers with >10K followers

**How it works**:
```python
def get_user_feed(user_id, limit=50):
    """
    Generate feed on-demand by aggregating posts from followed users
    """
    # 1. Check cache first
    cached_feed = redis.get(f"feed:{user_id}")
    if cached_feed:
        return cached_feed

    # 2. Get users that this user follows
    following_list = graph_db.get_following(user_id)

    # 3. Fetch recent posts from each followed user (parallel)
    all_posts = []
    for followed_user_id in following_list:
        recent_posts = postgres.query(
            "SELECT * FROM posts WHERE user_id = ? ORDER BY created_at DESC LIMIT 20",
            followed_user_id
        )
        all_posts.extend(recent_posts)

    # 4. Merge and rank posts
    ranked_feed = rank_posts(all_posts, user_id, limit)

    # 5. Cache for 5 minutes
    redis.setex(f"feed:{user_id}", 300, ranked_feed)

    return ranked_feed

def rank_posts(posts, user_id, limit):
    """
    Ranking algorithm
    """
    for post in posts:
        score = (
            time_decay(post.created_at) * 0.5 +
            engagement_score(post.likes_count, post.comments_count) * 0.3 +
            user_affinity(user_id, post.author_id) * 0.2
        )
        post.score = score

    return sorted(posts, key=lambda p: p.score, reverse=True)[:limit]
```

**Pros**:
- ✅ Fast writes (O(1), just save to DB)
- ✅ Works for influencers (no fan-out bottleneck)
- ✅ No wasted work (only generate for active users)
- ✅ Storage efficient

**Cons**:
- ❌ Slow reads (must query multiple shards)
- ❌ Complex ranking logic
- ❌ Hot-spotting (celebrity posts queried millions of times)

---

### Strategy 3: Hybrid Approach (Recommended - Facebook Style)

**Best of both worlds**:

```python
FOLLOWER_THRESHOLD = 10_000  # Threshold for fan-out strategy

def on_post_created(post, author):
    """
    Hybrid: Use different strategies based on follower count
    """
    # Save post to database (always)
    postgres.insert("posts", post)

    # Publish event to Kafka
    kafka.publish("post.created", {
        "post_id": post.id,
        "author_id": author.id,
        "follower_count": author.followers_count
    })

def handle_post_created_event(event):
    """
    Async consumer decides fan-out strategy
    """
    if event.follower_count < FOLLOWER_THRESHOLD:
        # Fan-out on write for normal users
        fan_out_on_write(event.post_id, event.author_id)
    else:
        # Fan-out on read for influencers
        # Just invalidate cache, feed will be generated on next request
        followers = graph_db.get_followers(event.author_id, limit=1000)
        for follower_id in followers:
            redis.delete(f"feed:{follower_id}")

def get_user_feed(user_id):
    """
    Hybrid feed generation
    """
    # 1. Check cache
    if cached := redis.get(f"feed:{user_id}"):
        return cached

    # 2. Get pre-computed timeline (from fan-out on write)
    precomputed = cassandra.query(
        "SELECT * FROM user_timeline WHERE user_id = ? LIMIT 30",
        user_id
    )

    # 3. Get following list and fetch posts from celebrities (fan-out on read)
    following_influencers = get_following_influencers(user_id)
    celebrity_posts = fetch_posts_from_users(following_influencers, limit=20)

    # 4. Merge and rank
    all_posts = precomputed + celebrity_posts
    ranked = rank_posts(all_posts, user_id, limit=50)

    # 5. Cache
    redis.setex(f"feed:{user_id}", 300, ranked)

    return ranked
```

**Benefits**:
- ✅ Fast writes for everyone
- ✅ Fast reads for most users
- ✅ Scalable for influencers
- ✅ Best resource utilization

---

## Step 6: Optimizations & Trade-offs (10 min)

### 1. Caching Strategy

```python
# Multi-tier cache
class FeedCache:
    def get_feed(self, user_id):
        # L1: Application memory (LRU cache, 1000 feeds)
        if feed := self.memory_cache.get(user_id):
            return feed

        # L2: Redis (100M feeds, 5 min TTL)
        if feed := redis.get(f"feed:{user_id}"):
            self.memory_cache.set(user_id, feed)
            return feed

        # L3: Cassandra (pre-computed timelines)
        if feed := cassandra.get_timeline(user_id):
            redis.setex(f"feed:{user_id}", 300, feed)
            self.memory_cache.set(user_id, feed)
            return feed

        # L4: Generate on-demand (fan-out on read)
        feed = self.generate_feed(user_id)
        redis.setex(f"feed:{user_id}", 300, feed)
        return feed
```

**Cache Invalidation**:
```python
def on_user_follows(follower_id, following_id):
    # Invalidate follower's feed (they follow someone new)
    redis.delete(f"feed:{follower_id}")

    # If following_id is not influencer, fan-out their posts
    if get_follower_count(following_id) < THRESHOLD:
        recent_posts = get_recent_posts(following_id, limit=10)
        for post in recent_posts:
            cassandra.write_to_timeline(follower_id, post)
```

### 2. Database Sharding

**Sharding Strategy**:
```python
# Consistent hashing for users and posts
def get_shard(entity_id, num_shards=64):
    return hash(entity_id) % num_shards

# User sharding
user_shard = get_shard(user_id)
db = postgres_cluster[user_shard]

# Post sharding (separate from users for independent scaling)
post_shard = get_shard(post_id)
db = postgres_cluster[post_shard]

# Follows sharding (by follower_id for efficient fan-out)
follows_shard = get_shard(follower_id)
db = postgres_cluster[follows_shard]
```

**Cross-shard queries** (avoid when possible):
```python
# BAD: Get all posts from user (could be on different shard)
# Need to query all shards or maintain secondary index

# GOOD: Denormalize - store user_id with post in same shard
# Trade-off: Consistency vs Performance
```

### 3. Media Optimization

```python
# Media upload pipeline
def upload_media(file, user_id):
    # 1. Upload original to S3
    original_key = f"originals/{user_id}/{uuid4()}.jpg"
    s3.upload(file, original_key)

    # 2. Trigger async processing (Lambda/Cloud Function)
    sqs.send_message({
        "original_key": original_key,
        "user_id": user_id
    })

    return original_key

def process_media_async(original_key):
    # Download original
    image = s3.download(original_key)

    # Generate multiple sizes
    variants = {
        "thumbnail": resize(image, 150, 150),
        "small": resize(image, 500, 500),
        "medium": resize(image, 1080, 1080),
        "large": resize(image, 2048, 2048)
    }

    # Optimize (WebP, compress)
    for size, img in variants.items():
        webp = convert_to_webp(img, quality=85)
        s3.upload(webp, f"processed/{size}/{original_key}.webp")

    # Update CDN cache
    cloudfront.invalidate([f"/processed/*"])
```

### 4. Real-time Updates (WebSockets)

```python
# WebSocket server for live updates
class FeedWebSocket:
    def on_connect(self, user_id):
        # Subscribe to user's feed updates
        redis_pubsub.subscribe(f"feed_updates:{user_id}")

    def on_new_post(self, post, author_id):
        # Get followers
        followers = graph_db.get_followers(author_id)

        # Publish to each follower's channel
        for follower_id in followers:
            redis_pubsub.publish(f"feed_updates:{follower_id}", {
                "type": "new_post",
                "post": post
            })

    def on_message(self, user_id, message):
        # Send to client via WebSocket
        websocket.send(user_id, message)
```

### 5. Ranking & Personalization

```python
def personalized_ranking(posts, user_id):
    """
    ML-based ranking using historical engagement
    """
    user_features = get_user_features(user_id)  # Past interactions

    for post in posts:
        # Feature engineering
        features = {
            "time_since_post": (now() - post.created_at).seconds,
            "author_follower_count": post.author.followers_count,
            "engagement_rate": post.likes / post.author.followers,
            "user_affinity": get_affinity(user_id, post.author_id),
            "content_similarity": cos_sim(user_features, post.embedding),
            "has_media": len(post.media_urls) > 0,
            "hashtag_match": len(set(user_features.hashtags) & set(post.hashtags))
        }

        # ML model prediction (trained offline)
        post.score = ml_model.predict(features)

    return sorted(posts, key=lambda p: p.score, reverse=True)
```

---

## Step 7: Advanced Considerations

### Security & Privacy

```python
# Content moderation
def moderate_post(post):
    # 1. Text moderation (profanity, hate speech)
    if contains_profanity(post.content):
        return "BLOCKED"

    # 2. Image moderation (AI/ML)
    if post.media_urls:
        for url in post.media_urls:
            if is_inappropriate(url):  # AWS Rekognition, Google Vision
                return "BLOCKED"

    # 3. Spam detection
    if is_spam(post):
        return "FLAGGED_FOR_REVIEW"

    return "APPROVED"

# Privacy controls
def can_view_post(post, viewer_id):
    if post.visibility == "PUBLIC":
        return True
    elif post.visibility == "FRIENDS":
        return are_friends(post.author_id, viewer_id)
    elif post.visibility == "PRIVATE":
        return post.author_id == viewer_id
    return False
```

### Monitoring & Alerts

```yaml
# Prometheus metrics
metrics:
  - feed_generation_latency_ms (p50, p95, p99)
  - post_creation_qps
  - like_qps
  - cache_hit_ratio
  - db_connection_pool_usage
  - kafka_consumer_lag

alerts:
  - name: HighFeedLatency
    condition: feed_latency_p99 > 1000ms
    for: 5m
    action: page_oncall

  - name: CacheHitRateLow
    condition: cache_hit_ratio < 0.8
    for: 10m
    action: slack_alert

  - name: KafkaConsumerLag
    condition: consumer_lag > 100000
    for: 5m
    action: auto_scale_consumers
```

### Disaster Recovery

```python
# Multi-region active-active
regions = ["us-east-1", "eu-west-1", "ap-southeast-1"]

# Write to local region, async replicate to others
def create_post(post, user_id):
    local_region = get_user_region(user_id)

    # Write to local DB
    postgres[local_region].insert(post)

    # Async replicate to other regions
    for region in regions:
        if region != local_region:
            replication_queue.enqueue(region, post)

    return post

# Read from nearest region
def get_feed(user_id):
    nearest_region = get_nearest_region()
    return feed_service[nearest_region].get_feed(user_id)
```

---

## Complete System Design Diagram

See [architecture.html](./architecture.html) and [flow-diagram.html](./flow-diagram.html) for interactive visualizations.

---

## Interview Tips

**Questions to Ask**:
- Scale: Twitter (200M DAU) or Facebook (2B DAU)?
- Features: Text-only or include media?
- Feed model: Chronological or ranked?
- Real-time updates required?
- Geographic distribution?

**Topics to Cover**:
- Fan-out strategies (write vs read vs hybrid)
- Database sharding strategy
- Caching layers (CDN, Redis, Application)
- Social graph storage (Neo4j vs denormalized SQL)
- Media handling (S3, CDN, processing pipeline)
- Consistency trade-offs (eventual vs strong)

**Common Follow-ups**:
- "How would you handle a viral post with 100M likes?"
  → Async like counter, eventual consistency, batch updates
- "How do you prevent spam and abuse?"
  → Rate limiting, content moderation AI, user reputation scores
- "How would you implement Stories (24-hour content)?"
  → Separate table with TTL, Redis for active stories, S3 auto-delete
- "How do you scale to 10× traffic during major events?"
  → Auto-scaling, read replicas, cache pre-warming, CDN

---

## Key Takeaways

1. **Fan-out Hybrid is Essential**: No single strategy works for all users
2. **Media Dominates Storage**: 99% of storage is images/videos, not text
3. **Cache Aggressively**: 1% of content serves 50% of traffic
4. **Eventual Consistency**: Like counts don't need to be exact
5. **Graph Database**: Neo4j excels at social connections
6. **Sharding is Critical**: Single DB can't handle billions of users
7. **Async Everything**: Use Kafka for non-critical paths

## Further Reading

- Instagram Engineering Blog: Feed Ranking
- Facebook's TAO: The Power of the Graph
- Twitter's Approach to Scaling Timelines
- Cassandra Data Modeling for Time-Series
- Neo4j Graph Algorithms for Recommendations
