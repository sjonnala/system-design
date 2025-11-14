# Feed Generation Deep Dive: The Heart of Social Media

## Introduction

Feed generation is the most critical component of any social media platform. It determines what users see, how engaged they are, and ultimately the success of the platform. This document provides a comprehensive deep-dive into feed generation algorithms, strategies, and implementation details.

---

## Table of Contents

1. [The Feed Generation Problem](#the-feed-generation-problem)
2. [Fan-out on Write (Push Model)](#fan-out-on-write-push-model)
3. [Fan-out on Read (Pull Model)](#fan-out-on-read-pull-model)
4. [Hybrid Approach](#hybrid-approach)
5. [Ranking Algorithms](#ranking-algorithms)
6. [Implementation Examples](#implementation-examples)
7. [Performance Optimization](#performance-optimization)
8. [Real-world Case Studies](#real-world-case-studies)

---

## The Feed Generation Problem

### Core Challenge

Given:
- User A follows 500 people
- These 500 people collectively create 10,000 posts per day
- User A opens the app and expects to see a personalized feed instantly

**Questions to answer:**
1. How do we select which posts to show?
2. How do we rank/order these posts?
3. How do we do this in <500ms at scale?

### Scale Constraints

```
Platform Scale (Instagram/Facebook level):
- 500M Daily Active Users
- Each user follows 200 people on average
- Each user creates 0.1 posts/day on average
- Total: 50M posts/day
- Feed requests: 500M users × 20 refreshes/day = 10B requests/day
- Feed QPS: 10B ÷ 86,400 sec = 115,000 QPS
```

---

## Fan-out on Write (Push Model)

### Concept

**"When a user posts, immediately push that post to all followers' feeds"**

### Algorithm

```python
def on_post_created(post, author_id):
    """
    Step 1: Save post to database
    """
    post_id = db.posts.insert({
        'author_id': author_id,
        'content': post.content,
        'created_at': now(),
        'media_urls': post.media_urls
    })

    """
    Step 2: Get all followers (from graph database)
    """
    follower_ids = graph_db.query(
        "MATCH (follower:User)-[:FOLLOWS]->(author:User {id: $author_id}) "
        "RETURN follower.id",
        author_id=author_id
    )
    # Returns: [user_1, user_2, ..., user_N]

    """
    Step 3: Fan-out - Write to each follower's timeline
    """
    tasks = []
    for follower_id in follower_ids:
        task = async_write_to_timeline(follower_id, post_id, post)
        tasks.append(task)

    # Execute all writes in parallel
    await asyncio.gather(*tasks)

    """
    Step 4: Invalidate cache
    """
    for follower_id in follower_ids:
        cache.delete(f"feed:{follower_id}")

    return post_id


async def async_write_to_timeline(user_id, post_id, post):
    """
    Write to Cassandra timeline table
    """
    await cassandra.execute(
        """
        INSERT INTO user_timeline (
            user_id, post_timestamp, post_id,
            author_id, content, media_urls, likes_count
        ) VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        user_id, post.created_at, post_id,
        post.author_id, post.content, post.media_urls, 0
    )
```

### Data Model (Cassandra)

```cql
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
) WITH CLUSTERING ORDER BY (post_timestamp DESC)
  AND compaction = {'class': 'TimeWindowCompactionStrategy'};

-- Query is simple and fast:
SELECT * FROM user_timeline
WHERE user_id = 12345
LIMIT 50;
-- Returns in ~5ms
```

### Reading the Feed (Ultra-Fast)

```python
def get_user_feed(user_id, limit=50, cursor=None):
    """
    Feed read is trivial - just query pre-computed timeline
    """
    # Check cache first
    cache_key = f"feed:{user_id}:{limit}:{cursor}"
    if cached := redis.get(cache_key):
        return json.loads(cached)

    # Query Cassandra
    if cursor:
        feed = cassandra.query(
            "SELECT * FROM user_timeline "
            "WHERE user_id = ? AND post_timestamp < ? "
            "LIMIT ?",
            user_id, cursor, limit
        )
    else:
        feed = cassandra.query(
            "SELECT * FROM user_timeline "
            "WHERE user_id = ? "
            "LIMIT ?",
            user_id, limit
        )

    # Enrich with real-time data (likes, comments)
    enriched_feed = enrich_posts(feed)

    # Cache for 5 minutes
    redis.setex(cache_key, 300, json.dumps(enriched_feed))

    return enriched_feed

def enrich_posts(posts):
    """
    Fetch latest like/comment counts from database
    """
    post_ids = [p['post_id'] for p in posts]

    # Batch query for efficiency
    stats = db.query(
        "SELECT post_id, likes_count, comments_count "
        "FROM posts WHERE post_id IN (?)",
        post_ids
    )

    stats_map = {s['post_id']: s for s in stats}

    for post in posts:
        if post['post_id'] in stats_map:
            post['likes_count'] = stats_map[post['post_id']]['likes_count']
            post['comments_count'] = stats_map[post['post_id']]['comments_count']

    return posts
```

### Performance Analysis

**Write Complexity:**
- Time: O(N) where N = number of followers
- For average user (200 followers): 200 writes
- For influencer (10M followers): 10M writes ❌

**Write Performance:**
```
User with 200 followers:
  200 writes × 2ms = 400ms (acceptable)

User with 10K followers:
  10K writes × 2ms = 20 seconds (BAD!)

User with 10M followers:
  10M writes × 2ms = 5.5 HOURS (IMPOSSIBLE!)
```

**Read Complexity:**
- Time: O(1) - single partition query
- Latency: ~5-10ms (Cassandra) + cache hit = ~1ms

### Pros & Cons

**Advantages:**
- ✅ **Lightning-fast reads**: Feed is pre-computed, simple query
- ✅ **Scalable reads**: Can handle millions of concurrent users
- ✅ **Simple read logic**: No complex aggregation
- ✅ **Predictable latency**: Always fast, regardless of following count

**Disadvantages:**
- ❌ **Slow writes for influencers**: O(followers) write complexity
- ❌ **Wasted work**: Inactive users still get updates
- ❌ **Storage overhead**: Duplicate post data across millions of timelines
- ❌ **Delayed writes**: High fan-out takes seconds/minutes
- ❌ **Thundering herd**: Celebrity post triggers millions of writes

### When to Use

```
Use fan-out on write when:
✓ Average follower count < 10,000
✓ Read:Write ratio > 100:1
✓ Storage cost is acceptable
✓ Fast reads are critical

Example platforms:
- Twitter (most users)
- Early-stage Instagram
- LinkedIn
```

---

## Fan-out on Read (Pull Model)

### Concept

**"When a user requests their feed, fetch posts from all followed users in real-time"**

### Algorithm

```python
def get_user_feed(user_id, limit=50):
    """
    Generate feed on-demand by aggregating posts from followed users
    """

    # Step 1: Check cache
    cache_key = f"feed:{user_id}"
    if cached := redis.get(cache_key):
        return json.loads(cached)

    # Step 2: Get following list
    following_ids = graph_db.query(
        "MATCH (user:User {id: $user_id})-[:FOLLOWS]->(following) "
        "RETURN following.id",
        user_id=user_id
    )
    # Returns: [author_1, author_2, ..., author_M]

    # Step 3: Fetch recent posts from each followed user (parallel)
    tasks = []
    for author_id in following_ids:
        task = fetch_recent_posts(author_id, limit_per_author=20)
        tasks.append(task)

    posts_per_author = await asyncio.gather(*tasks)
    all_posts = [post for posts in posts_per_author for post in posts]

    # Step 4: Merge and rank
    ranked_posts = rank_posts(all_posts, user_id, limit)

    # Step 5: Cache (short TTL to balance freshness and performance)
    redis.setex(cache_key, 300, json.dumps(ranked_posts))  # 5 min cache

    return ranked_posts


async def fetch_recent_posts(author_id, limit_per_author=20):
    """
    Fetch latest posts from a specific author
    """
    # Query from sharded PostgreSQL
    shard = get_shard(author_id)
    posts = await postgres[shard].query(
        "SELECT * FROM posts "
        "WHERE author_id = ? AND created_at > NOW() - INTERVAL '7 days' "
        "ORDER BY created_at DESC "
        "LIMIT ?",
        author_id, limit_per_author
    )
    return posts


def rank_posts(posts, user_id, limit):
    """
    Ranking algorithm (simplified)
    """
    for post in posts:
        # Calculate relevance score
        time_score = time_decay(post.created_at)
        engagement_score = log(1 + post.likes_count + post.comments_count * 2)
        affinity_score = get_user_affinity(user_id, post.author_id)

        post.score = (
            0.4 * time_score +
            0.3 * engagement_score +
            0.3 * affinity_score
        )

    # Sort by score and return top N
    ranked = sorted(posts, key=lambda p: p.score, reverse=True)
    return ranked[:limit]


def time_decay(created_at):
    """
    Exponential decay based on post age
    """
    hours_old = (now() - created_at).total_seconds() / 3600
    return math.exp(-hours_old / 48)  # Half-life of 48 hours


def get_user_affinity(user_id, author_id):
    """
    How much does user_id interact with author_id?
    Calculated offline, cached in Redis
    """
    affinity = redis.get(f"affinity:{user_id}:{author_id}")
    if affinity is None:
        # Calculate from historical interactions
        interactions = db.query(
            "SELECT COUNT(*) FROM interactions "
            "WHERE user_id = ? AND target_user_id = ?",
            user_id, author_id
        )
        affinity = min(1.0, interactions / 100)  # Normalize to [0, 1]
        redis.setex(f"affinity:{user_id}:{author_id}", 3600, affinity)

    return float(affinity)
```

### Performance Analysis

**Write Complexity:**
- Time: O(1) - just insert into database
- Latency: ~10ms regardless of follower count

**Read Complexity:**
- Time: O(M × log N) where M = following count, N = posts per author
- For user following 200 people: 200 queries
- Parallelized: ~100-200ms total

**Read Performance:**
```
User following 200 people:
  200 parallel queries × 5ms = 200ms (with good caching)
  Ranking: ~50ms
  Total: ~250ms (acceptable with caching)

User following 1000 people:
  1000 queries (even parallelized) = ~500ms (getting slow)

Cache hit (85%): ~5ms
Cache miss: ~200-500ms
```

### Pros & Cons

**Advantages:**
- ✅ **Fast writes**: O(1), works for any follower count
- ✅ **No wasted work**: Only generate feeds for active users
- ✅ **Fresh data**: Always shows latest posts
- ✅ **Storage efficient**: No duplicate timeline data
- ✅ **Flexible ranking**: Can personalize in real-time

**Disadvantages:**
- ❌ **Slow reads**: Must query multiple database shards
- ❌ **Complex implementation**: Parallel queries, merging, ranking
- ❌ **Hot-spotting**: Celebrity posts queried millions of times
- ❌ **Variable latency**: Depends on following count and cache hit
- ❌ **Database load**: Heavy read traffic on post tables

### When to Use

```
Use fan-out on read when:
✓ Many users have >10K following
✓ Influencers/celebrities are common
✓ Storage cost is high
✓ Freshness is critical
✓ Personalized ranking is needed

Example platforms:
- Instagram (influencer-heavy)
- YouTube (subscriptions)
- Pinterest (many boards)
```

---

## Hybrid Approach

### Concept

**"Use fan-out on write for normal users, fan-out on read for influencers"**

### Decision Logic

```python
# Configuration
FOLLOWER_THRESHOLD = 10_000  # Threshold for switching strategies
CELEBRITY_THRESHOLD = 1_000_000  # Super celebrities

def on_post_created(post, author):
    """
    Determine strategy based on follower count
    """
    follower_count = get_follower_count(author.id)

    # Always save post to database
    post_id = db.posts.insert(post)

    # Publish event to Kafka for async processing
    kafka.publish("post.created", {
        "post_id": post_id,
        "author_id": author.id,
        "follower_count": follower_count,
        "post_data": post
    })

    return post_id


def handle_post_created_event(event):
    """
    Kafka consumer - decide fan-out strategy
    """
    if event.follower_count < FOLLOWER_THRESHOLD:
        # Strategy 1: Fan-out on write (push to all followers)
        fan_out_on_write(event)

    elif event.follower_count < CELEBRITY_THRESHOLD:
        # Strategy 2: Hybrid - push to active followers only
        fan_out_to_active_followers(event)

    else:
        # Strategy 3: Fan-out on read (no pre-computation)
        # Just invalidate cache for followers
        invalidate_follower_caches(event.author_id)


def fan_out_on_write(event):
    """
    Push to all followers' timelines
    """
    followers = graph_db.get_followers(event.author_id)

    for follower_id in followers:
        cassandra.write(
            table="user_timeline",
            user_id=follower_id,
            post_timestamp=event.post_data.created_at,
            post_id=event.post_id,
            post_data=event.post_data
        )

        # Invalidate cache
        redis.delete(f"feed:{follower_id}")


def fan_out_to_active_followers(event):
    """
    Only push to followers active in last 24 hours
    """
    active_followers = db.query(
        "SELECT follower_id FROM follows "
        "WHERE following_id = ? "
        "AND follower_id IN ("
        "  SELECT user_id FROM user_activity "
        "  WHERE last_active > NOW() - INTERVAL '24 hours'"
        ")",
        event.author_id
    )

    for follower_id in active_followers:
        cassandra.write(
            table="user_timeline",
            user_id=follower_id,
            post_id=event.post_id,
            post_data=event.post_data
        )


def invalidate_follower_caches(author_id):
    """
    For celebrities: just invalidate cache, feed will be generated on request
    """
    # Option 1: Invalidate cache for all followers (expensive)
    # followers = graph_db.get_followers(author_id)
    # for follower_id in followers:
    #     redis.delete(f"feed:{follower_id}")

    # Option 2: Set a "new_content_available" flag (better)
    redis.setex(f"new_posts:{author_id}", 3600, "1")
```

### Feed Generation (Hybrid)

```python
def get_user_feed_hybrid(user_id, limit=50):
    """
    Hybrid feed generation
    """
    # Step 1: Check cache
    if cached := redis.get(f"feed:{user_id}"):
        return json.loads(cached)

    # Step 2: Get following list
    following = graph_db.get_following(user_id)

    # Segment following list by strategy
    normal_users = []
    celebrities = []

    for followed_id, follower_count in following:
        if follower_count < FOLLOWER_THRESHOLD:
            normal_users.append(followed_id)
        else:
            celebrities.append(followed_id)

    # Step 3: Get pre-computed posts (from fan-out on write)
    precomputed_posts = cassandra.query(
        "SELECT * FROM user_timeline "
        "WHERE user_id = ? "
        "LIMIT ?",
        user_id, 100
    )

    # Step 4: Fetch posts from celebrities (fan-out on read)
    celebrity_posts = []
    for celeb_id in celebrities:
        posts = fetch_recent_posts(celeb_id, limit=20)
        celebrity_posts.extend(posts)

    # Step 5: Merge and rank
    all_posts = precomputed_posts + celebrity_posts
    ranked_posts = rank_posts(all_posts, user_id, limit)

    # Step 6: Cache
    redis.setex(f"feed:{user_id}", 300, json.dumps(ranked_posts))

    return ranked_posts
```

### Performance Characteristics

```
Write Performance:
- Normal user (200 followers): 200 writes × 2ms = 400ms ✓
- Influencer (100K followers): No fan-out, just DB insert = 10ms ✓
- Celebrity (10M followers): No fan-out, just DB insert = 10ms ✓

Read Performance:
- User following normal users: ~10ms (Cassandra) ✓
- User following celebrities: ~200ms (fan-out on read) ✓
- Mixed (typical): ~50-100ms (hybrid) ✓
```

### Pros & Cons

**Advantages:**
- ✅ **Balanced**: Fast writes AND fast reads
- ✅ **Scalable**: Works for all user types
- ✅ **Resource efficient**: Optimized for each scenario
- ✅ **Flexible**: Can tune thresholds per platform

**Disadvantages:**
- ❌ **Complex implementation**: Multiple code paths
- ❌ **Harder to debug**: Different behaviors for different users
- ❌ **Threshold tuning**: Requires monitoring and adjustment

### When to Use

```
Use hybrid approach when:
✓ Platform has diverse user types (normal + influencers)
✓ Scale is massive (100M+ users)
✓ Both read and write performance matter
✓ Engineering resources available for complexity

Example platforms:
- Facebook ✓
- Instagram ✓
- Twitter ✓
- LinkedIn ✓
```

---

## Ranking Algorithms

### Basic Chronological

```python
def rank_chronological(posts):
    """
    Simple: newest first
    """
    return sorted(posts, key=lambda p: p.created_at, reverse=True)
```

### Time-Weighted Engagement

```python
def rank_time_weighted(posts):
    """
    Balance recency and engagement
    """
    for post in posts:
        hours_old = (now() - post.created_at).total_seconds() / 3600

        # Time decay (exponential)
        time_score = math.exp(-hours_old / 24)  # Half-life of 24 hours

        # Engagement score (logarithmic to prevent outliers)
        engagement = post.likes_count + 2 * post.comments_count + 5 * post.shares_count
        engagement_score = math.log10(1 + engagement)

        post.score = 0.6 * time_score + 0.4 * engagement_score

    return sorted(posts, key=lambda p: p.score, reverse=True)
```

### Personalized ML-Based Ranking

```python
def rank_ml_personalized(posts, user_id):
    """
    Use ML model to predict engagement probability
    """
    user_features = get_user_features(user_id)

    for post in posts:
        # Extract features
        features = {
            # Temporal features
            'hours_since_post': (now() - post.created_at).total_seconds() / 3600,
            'hour_of_day': post.created_at.hour,
            'day_of_week': post.created_at.weekday(),

            # Post features
            'has_image': len(post.media_urls) > 0,
            'has_video': any('video' in url for url in post.media_urls),
            'content_length': len(post.content),
            'hashtag_count': len(post.hashtags),

            # Author features
            'author_follower_count': post.author.followers_count,
            'author_post_frequency': get_post_frequency(post.author_id),

            # Engagement features
            'likes_count': post.likes_count,
            'comments_count': post.comments_count,
            'shares_count': post.shares_count,
            'engagement_rate': (post.likes_count / post.author.followers_count),

            # User-author relationship
            'user_follows_author': is_following(user_id, post.author_id),
            'user_author_affinity': get_affinity(user_id, post.author_id),
            'mutual_friends_count': count_mutual_friends(user_id, post.author_id),

            # Content similarity
            'content_similarity': cosine_sim(user_features.interests, post.embedding),
            'hashtag_overlap': len(set(user_features.hashtags) & set(post.hashtags)),
        }

        # ML model predicts P(user will engage with this post)
        post.score = ml_model.predict(features)

    return sorted(posts, key=lambda p: p.score, reverse=True)
```

---

## Real-world Case Studies

### Twitter's Timeline Algorithm

**Approach**: Hybrid (Home timeline uses both)

**Strategy**:
- Fan-out on write for most users (<10K followers)
- Fan-out on read for celebrities/verified accounts
- Cache aggressively (both L1 and L2)

**Ranking**:
- Originally chronological (2006-2016)
- Now ML-based relevance ranking (2016+)
- "Best tweets first" option

**Key Optimizations**:
- Early termination in ranking (only rank top 1000, not all posts)
- Bloom filters to avoid unnecessary DB queries
- Tweet compression (store metadata, fetch full tweet on-demand)

---

### Instagram's Feed Evolution

**Approach**: Fan-out on read (due to influencers)

**Strategy**:
- Always fetch latest posts from followed users
- Heavy caching (5-minute TTL)
- Pre-fetch and background refresh

**Ranking** (Instagram's algorithm):
```python
score = (
    interest * 0.35 +      # How much user cares about this content
    recency * 0.25 +       # How recent the post is
    relationship * 0.20 +  # How often user interacts with author
    frequency * 0.10 +     # How often user opens Instagram
    following * 0.10       # How many people user follows
)
```

**Key Optimizations**:
- Aggressive image CDN (Fastly)
- Lazy loading (only fetch media URLs, not images initially)
- "Seen posts" deduplication

---

### Facebook's EdgeRank → News Feed Algorithm

**Approach**: Hybrid + ML

**Ranking Factors**:
1. **Affinity Score**: How close is user to author?
2. **Content Weight**: Type of post (video > photo > link > status)
3. **Time Decay**: How old is the post?
4. **Engagement Velocity**: How quickly is post getting likes/comments?

**Modern ML Model** (2018+):
- Deep learning model with 1000+ features
- Predicts P(meaningful interaction)
- Trained on billions of examples
- A/B tested continuously

**Key Optimizations**:
- TAO (The Associations and Objects) - distributed graph database
- Memphis cache layer
- Multi-region replication

---

## Key Takeaways

1. **No One-Size-Fits-All**: Different platforms need different strategies
2. **Hybrid is King**: Most successful platforms use hybrid approaches
3. **Cache Aggressively**: 80%+ cache hit rate is achievable
4. **ML is the Future**: Personalized ranking beats chronological
5. **Monitor Everything**: Feed latency is your most critical metric
6. **Iterate Quickly**: A/B test ranking changes continuously

---

## Further Reading

- [Instagram Engineering: Feed Ranking](https://engineering.instagram.com/)
- [Twitter's Timeline Service](https://blog.twitter.com/engineering)
- [Facebook's TAO: The Power of the Graph](https://www.usenix.org/conference/atc13/technical-sessions/presentation/bronson)
- [Pinterest's Feed Architecture](https://medium.com/pinterest-engineering)
