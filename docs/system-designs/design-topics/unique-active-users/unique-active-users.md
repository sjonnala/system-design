# Design a Unique Active Users Analytics System

**Companies**: Facebook, Google Analytics, Mixpanel, Amplitude, Segment
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a real-time analytics system that tracks and reports Daily Active Users (DAU), Weekly Active Users (WAU), and Monthly Active Users (MAU) for a platform with hundreds of millions of users. The system should handle billions of events per day and provide near real-time insights with the ability to slice data by various dimensions (country, device, app version, etc.).

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Track User Activity**: Record user events (login, page view, action)
2. **Calculate DAU/WAU/MAU**: Unique users in 1 day, 7 days, 30 days
3. **Multi-dimensional Analysis**: Segment by country, device, platform, cohort
4. **Historical Data**: Query historical trends (last 90 days, year-over-year)
5. **Real-time Updates**: Near real-time dashboard updates (< 5 min latency)
6. **Retention Analysis**: Cohort retention, churn rate calculation
7. **Custom Events**: Support for custom business events (purchase, signup, etc.)

**Prioritize**: Focus on DAU/MAU calculation and real-time ingestion for MVP

### Non-Functional Requirements

1. **High Throughput**: Handle 100K+ events/second
2. **Low Latency**: Query response time < 2 seconds
3. **Scalable**: Support billions of users
4. **Accurate**: Exact unique count (or acceptable approximation)
5. **Available**: 99.9% uptime for event ingestion
6. **Cost-Effective**: Optimize storage and compute costs
7. **Flexible**: Support ad-hoc queries for analysts

### Capacity Estimation

**Assumptions**:
- 500M total users
- 100M Daily Active Users (DAU)
- Each user generates 50 events/day on average
- Event size: 1KB (userId, timestamp, eventType, metadata)
- Retention: 2 years for raw events, 5 years for aggregates

**Event Ingestion Load**:
```
100M DAU × 50 events/day = 5B events/day
5B events/day ÷ 86,400 sec = ~58K events/sec average
Peak (3× average): ~175K events/sec
```

**Storage**:
```
Raw Events:
5B events/day × 1KB = 5TB/day
5TB/day × 365 days × 2 years = 3.65 PB (with compression: ~1 PB)

Aggregated Data (Pre-computed):
DAU tables: 100M users × 365 days × 100 bytes = 3.65 TB/year
```

**Query Load**:
```
Analysts + Dashboards: ~1,000 queries/day
Real-time dashboards: ~100 concurrent queries
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                    Client Apps                          │
│           (Web, iOS, Android, Backend)                  │
└────────────────────┬────────────────────────────────────┘
                     │ Events (JSON/Protobuf)
                     ▼
┌─────────────────────────────────────────────────────────┐
│               API Gateway / Load Balancer               │
│                   (Rate Limiting)                       │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│            Event Ingestion Service                      │
│         (Validation, Enrichment, Deduplication)         │
└────────┬──────────────────────────┬─────────────────────┘
         │                          │
         ▼                          ▼
┌────────────────────┐   ┌─────────────────────────────┐
│  Message Queue     │   │    Fast Path (Redis)        │
│  (Apache Kafka)    │   │  Recent Events Cache        │
└────────┬───────────┘   └─────────────────────────────┘
         │
         ├──────────┬──────────┬──────────┐
         ▼          ▼          ▼          ▼
┌──────────────┐ ┌────────┐ ┌─────────┐ ┌──────────────┐
│ Stream Proc  │ │  OLAP  │ │  Data   │ │   Machine    │
│ (Flink/Spark)│ │  Store │ │  Lake   │ │   Learning   │
│              │ │        │ │  (S3)   │ │   Pipeline    │
└──────┬───────┘ └───┬────┘ └─────────┘ └──────────────┘
       │             │
       ▼             ▼
┌──────────────────────────────────────────────────┐
│          ClickHouse / Druid / BigQuery           │
│         (OLAP Database for Analytics)            │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│            Query Service (API)                   │
│         (GraphQL/REST for Dashboards)            │
└────────────────────┬─────────────────────────────┘
                     │
                     ▼
┌──────────────────────────────────────────────────┐
│        Visualization Layer (Grafana/Tableau)     │
└──────────────────────────────────────────────────┘
```

### Components

**Event Ingestion Service**:
- Validate events (schema, required fields)
- Enrich with metadata (geo-location, device info)
- Deduplicate events (idempotency)
- Batch writes to Kafka

**Apache Kafka**:
- Durable event stream
- Multiple consumer groups
- Partitioned by userId for ordering
- Retention: 7 days

**Stream Processing (Apache Flink)**:
- Real-time aggregation (5-minute windows)
- Calculate running DAU/MAU using HyperLogLog
- Join with dimension tables
- Output to OLAP database

**OLAP Database (ClickHouse)**:
- Columnar storage for analytics
- Pre-aggregated rollup tables
- Fast query performance (< 2s)
- Partitioned by date

**Data Lake (S3)**:
- Long-term storage (2+ years)
- Parquet format for efficiency
- Accessed by Spark for batch jobs

---

## Step 3: Data Model (10 min)

### Event Schema

```protobuf
message UserEvent {
  string event_id = 1;          // UUID for idempotency
  string user_id = 2;           // Unique user identifier
  int64 timestamp = 3;          // Unix timestamp (ms)
  string event_type = 4;        // "page_view", "login", "purchase"
  string platform = 5;          // "web", "ios", "android"
  string app_version = 6;       // "2.1.5"
  string country = 7;           // "US", "IN", "BR"
  string device_type = 8;       // "mobile", "desktop", "tablet"
  map<string, string> properties = 9;  // Custom event properties
}
```

### ClickHouse Schema

```sql
-- Raw Events Table (7-day retention)
CREATE TABLE events (
    event_id String,
    user_id String,
    timestamp DateTime,
    event_type String,
    platform String,
    country String,
    device_type String,
    properties String  -- JSON
) ENGINE = MergeTree()
PARTITION BY toYYYYMMDD(timestamp)
ORDER BY (timestamp, user_id);

-- Daily Active Users Table (Pre-aggregated)
CREATE TABLE dau (
    date Date,
    country String,
    platform String,
    device_type String,
    unique_users AggregateFunction(uniq, String),  -- HyperLogLog
    event_count SimpleAggregateFunction(sum, UInt64)
) ENGINE = AggregatingMergeTree()
PARTITION BY toYYYYMM(date)
ORDER BY (date, country, platform);

-- Materialized View for Real-time Aggregation
CREATE MATERIALIZED VIEW dau_mv TO dau AS
SELECT
    toDate(timestamp) as date,
    country,
    platform,
    device_type,
    uniqState(user_id) as unique_users,
    count() as event_count
FROM events
GROUP BY date, country, platform, device_type;

-- User Dimension Table (SCD Type 2)
CREATE TABLE users (
    user_id String,
    signup_date Date,
    cohort String,
    user_tier String,  -- "free", "premium"
    valid_from DateTime,
    valid_to DateTime
) ENGINE = ReplacingMergeTree(valid_to)
ORDER BY (user_id, valid_from);
```

### Redis Schema (Fast Path)

```python
# Recent active users (last 24 hours) using HyperLogLog
PFADD active:users:2025-11-15 "user_123"
PFCOUNT active:users:2025-11-15  # Get DAU count

# Recent users by dimension (last hour)
PFADD active:users:2025-11-15:14:country:US "user_123"
PFADD active:users:2025-11-15:14:platform:ios "user_123"

# Rolling MAU using sorted sets
ZADD rolling:mau:2025-11 1731628800 "user_123"  # score = last_seen_timestamp
ZCOUNT rolling:mau:2025-11 (NOW - 30days) +inf
```

---

## Step 4: APIs (5 min)

### Event Ingestion API

```http
POST /api/v1/events
Content-Type: application/json
Authorization: Bearer {api_key}

Request:
{
  "events": [
    {
      "event_id": "evt_123",
      "user_id": "user_456",
      "timestamp": 1731628800000,
      "event_type": "page_view",
      "platform": "web",
      "properties": {
        "page": "/dashboard",
        "session_id": "sess_789"
      }
    }
  ]
}

Response: 202 Accepted
{
  "accepted": 1,
  "rejected": 0,
  "message": "Events queued for processing"
}
```

### Analytics Query API

```http
POST /api/v1/analytics/query
Content-Type: application/json

Request:
{
  "metrics": ["dau", "wau", "mau"],
  "dimensions": ["country", "platform"],
  "filters": {
    "date_range": {
      "start": "2025-10-01",
      "end": "2025-11-15"
    },
    "country": ["US", "IN", "BR"]
  },
  "granularity": "day"
}

Response: 200 OK
{
  "data": [
    {
      "date": "2025-11-15",
      "country": "US",
      "platform": "ios",
      "dau": 15000000,
      "wau": 45000000,
      "mau": 120000000
    },
    {
      "date": "2025-11-15",
      "country": "US",
      "platform": "android",
      "dau": 20000000,
      "wau": 60000000,
      "mau": 150000000
    }
  ],
  "query_time_ms": 1250
}
```

### Cohort Retention API

```http
GET /api/v1/analytics/retention?cohort_date=2025-10-01&period=30

Response:
{
  "cohort_date": "2025-10-01",
  "cohort_size": 5000000,
  "retention": [
    {"day": 0, "users": 5000000, "percentage": 100.0},
    {"day": 1, "users": 3500000, "percentage": 70.0},
    {"day": 7, "users": 2000000, "percentage": 40.0},
    {"day": 30, "users": 1000000, "percentage": 20.0}
  ]
}
```

---

## Step 5: Core Algorithm - Unique Counting

### Challenge: Count Unique Users at Scale

**Problem**: Counting exact unique users requires storing all user IDs, which is memory-intensive.

**Solutions**:

### Approach 1: Exact Count with Bitmap (Redis)

```python
import redis

def track_dau_exact(date, user_id):
    """Uses bitmap - 1 bit per user"""
    # Assuming user_id is numeric (0 to 1B)
    redis.setbit(f"dau:{date}", user_id, 1)

def get_dau_exact(date):
    return redis.bitcount(f"dau:{date}")

# Memory: 1B users = 125 MB per day
# Pros: Exact count, fast
# Cons: Requires numeric user IDs, memory grows with users
```

### Approach 2: HyperLogLog (RECOMMENDED)

```python
def track_dau_approx(date, user_id):
    """Uses HyperLogLog - probabilistic data structure"""
    redis.pfadd(f"dau:{date}", user_id)

def get_dau_approx(date):
    return redis.pfcount(f"dau:{date}")

# Memory: ~12 KB per HyperLogLog (fixed size!)
# Accuracy: ±0.81% standard error
# Pros: Fixed memory, works with string IDs, mergeable
# Cons: Approximate (acceptable for analytics)

def get_wau(date):
    """Merge 7 daily HyperLogLogs"""
    keys = [f"dau:{date - i}" for i in range(7)]
    return redis.pfcount(*keys)  # Union of sets
```

### Approach 3: Count-Min Sketch (For Top-K)

```python
from countminsketch import CountMinSketch

cms = CountMinSketch(width=10000, depth=7)

def track_event(user_id, event_type):
    cms.add(f"{user_id}:{event_type}")

def get_top_users(k=100):
    """Get top K active users"""
    # Use heap with CMS estimates
    pass

# Use Case: "Top 100 most active users today"
```

### Approach 4: SQL with Distinct (Small-Medium Scale)

```sql
-- Works well up to 100M users with proper indexing
SELECT
    toDate(timestamp) as date,
    country,
    uniq(user_id) as dau  -- ClickHouse uses HyperLogLog internally
FROM events
WHERE timestamp >= today() - INTERVAL 7 DAY
GROUP BY date, country;
```

---

## Step 6: Stream Processing Pipeline (15 min)

### Apache Flink Job

```java
// Flink job for real-time DAU calculation
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Read from Kafka
FlinkKafkaConsumer<UserEvent> consumer = new FlinkKafkaConsumer<>(
    "user-events",
    new UserEventDeserializationSchema(),
    properties
);

DataStream<UserEvent> events = env.addSource(consumer);

// Deduplicate events (by event_id within 1-hour window)
DataStream<UserEvent> deduped = events
    .keyBy(event -> event.getEventId())
    .window(TumblingEventTimeWindows.of(Time.hours(1)))
    .reduce((e1, e2) -> e1);  // Keep first event

// Calculate DAU by country in 5-minute windows
DataStream<ActiveUserCount> dau = deduped
    .keyBy(event -> new Tuple3<>(
        event.getDate(),
        event.getCountry(),
        event.getPlatform()
    ))
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new HyperLogLogAggregator());

// Sink to ClickHouse
dau.addSink(new ClickHouseSink());

env.execute("Real-time DAU Calculator");
```

### Custom HyperLogLog Aggregator

```java
public class HyperLogLogAggregator
    implements AggregateFunction<UserEvent, HyperLogLog, ActiveUserCount> {

    @Override
    public HyperLogLog createAccumulator() {
        return new HyperLogLog(14);  // Standard error: 0.81%
    }

    @Override
    public HyperLogLog add(UserEvent event, HyperLogLog hll) {
        hll.offer(event.getUserId());
        return hll;
    }

    @Override
    public HyperLogLog merge(HyperLogLog a, HyperLogLog b) {
        a.addAll(b);
        return a;
    }

    @Override
    public ActiveUserCount getResult(HyperLogLog hll) {
        return new ActiveUserCount(
            hll.cardinality(),  // Estimated unique count
            hll  // Store HLL for later merging (WAU/MAU)
        );
    }
}
```

---

## Step 7: Optimizations & Trade-offs (10 min)

### 1. Pre-aggregation Strategy

**Problem**: Querying raw events is slow
**Solution**: Pre-compute common aggregations

```sql
-- Instead of scanning billions of raw events:
SELECT uniq(user_id) FROM events WHERE date = today()

-- Use pre-aggregated table:
SELECT uniqMerge(unique_users) FROM dau WHERE date = today()
```

**Benefits**:
- 100× faster queries
- Reduced compute cost
- Enables real-time dashboards

**Trade-off**: Storage cost (10TB raw → 100GB aggregated)

### 2. Lambda Architecture

```
┌─────────────────────────────────────────┐
│  SPEED LAYER (Real-time)                │
│  Flink → Redis → Last 24 hours          │
│  Latency: < 1 minute                    │
└─────────────────┬───────────────────────┘
                  │
                  ▼ Merge Results
┌─────────────────────────────────────────┐
│  BATCH LAYER (Historical)               │
│  Spark → ClickHouse → > 24 hours        │
│  Latency: 1 hour                        │
└─────────────────────────────────────────┘
```

**Speed Layer**:
- Recent data (last 24 hours) in Redis
- HyperLogLog for fast approximate counts
- Updated every minute

**Batch Layer**:
- Reprocess data nightly for accuracy
- Backfill missing data
- Compute complex aggregations (retention, cohorts)

**Serving Layer**:
- Merge results from both layers
- Cache frequent queries

### 3. Data Partitioning

**ClickHouse Partitioning**:
```sql
-- Partition by month for efficient pruning
PARTITION BY toYYYYMM(date)

-- Benefits:
-- - Drop old partitions quickly (DELETE is slow in ClickHouse)
-- - Query only relevant partitions
-- - Parallel processing
```

**Kafka Partitioning**:
```
Partition by: hash(user_id) % num_partitions

Benefits:
- All events for a user go to same partition (ordering guaranteed)
- Parallel processing
- Flink can maintain user state efficiently
```

### 4. Sampling for Exploration

```sql
-- For ad-hoc exploration, sample 1% of data
SELECT uniq(user_id) * 100 as estimated_dau
FROM events SAMPLE 0.01  -- 1% sample
WHERE date = today();

-- 100× faster, acceptable for exploration
```

### 5. Retention & Archival

```python
# Data Lifecycle Policy
- Raw Events: 7 days (hot) → 90 days (warm) → 2 years (cold/S3)
- Hourly Aggregates: 30 days
- Daily Aggregates: 5 years
- Monthly Aggregates: Forever

# Storage Cost Optimization:
7 days hot: $1000/month (SSD)
90 days warm: $500/month (HDD)
2 years cold: $200/month (S3 Glacier)
```

---

## Step 8: Advanced Considerations

### Handling Late Arrivals

```java
// Flink watermark strategy for late events
WatermarkStrategy<UserEvent> watermarkStrategy =
    WatermarkStrategy
        .<UserEvent>forBoundedOutOfOrderness(Duration.ofMinutes(15))
        .withTimestampAssigner((event, timestamp) -> event.getTimestamp());

// Allow events up to 15 minutes late
// After watermark passes, trigger window computation
```

### Data Quality & Validation

```python
def validate_event(event):
    """Data quality checks"""
    issues = []

    # Required fields
    if not event.user_id:
        issues.append("Missing user_id")

    # Timestamp sanity check
    if event.timestamp > now() + 60:
        issues.append("Future timestamp")

    if event.timestamp < now() - 7*86400:
        issues.append("Event older than 7 days")

    # Valid enum values
    if event.platform not in ['web', 'ios', 'android']:
        issues.append(f"Invalid platform: {event.platform}")

    if issues:
        send_to_dead_letter_queue(event, issues)
        return False

    return True
```

### Privacy & Compliance (GDPR)

```sql
-- User Deletion (Right to be Forgotten)
-- Challenge: Distributed data across systems

-- 1. Mark user as deleted
UPDATE users SET deleted_at = now() WHERE user_id = 'user_123';

-- 2. Stop collecting events
-- (Check deleted_at before ingestion)

-- 3. Aggregate counts don't need adjustment
-- (No PII, just counts)

-- 4. Delete raw events (if stored)
-- Use TTL partitioning for automatic deletion
ALTER TABLE events MODIFY TTL timestamp + INTERVAL 90 DAY;
```

### Anomaly Detection

```sql
-- Detect sudden drops/spikes in DAU
WITH daily_stats AS (
    SELECT
        date,
        uniqMerge(unique_users) as dau,
        avg(dau) OVER (ORDER BY date ROWS BETWEEN 7 PRECEDING AND 1 PRECEDING) as avg_7d
    FROM dau
    WHERE date >= today() - 30
)
SELECT
    date,
    dau,
    avg_7d,
    (dau - avg_7d) / avg_7d * 100 as pct_change
FROM daily_stats
WHERE abs((dau - avg_7d) / avg_7d) > 0.2  -- Alert if >20% change
ORDER BY date DESC;
```

### Multi-Region Deployment

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│  US-EAST     │         │  EU-WEST     │         │  APAC        │
│              │         │              │         │              │
│  Kafka       │         │  Kafka       │         │  Kafka       │
│  Flink       │◄───────►│  Flink       │◄───────►│  Flink       │
│  ClickHouse  │  Mirror │  ClickHouse  │  Mirror │  ClickHouse  │
└──────────────┘         └──────────────┘         └──────────────┘
       │                        │                        │
       └────────────────────────┴────────────────────────┘
                                │
                                ▼
                    ┌───────────────────────┐
                    │  Global Data Warehouse│
                    │      (BigQuery)       │
                    └───────────────────────┘
```

---

## Complete System Design Diagram

```
┌────────────────────────────────────────────────────────────────────┐
│                         CLIENT APPLICATIONS                         │
│           Web (React) | iOS (Swift) | Android (Kotlin)              │
└──────────────────────────┬─────────────────────────────────────────┘
                           │ Track Events (HTTPS)
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                     CDN / API GATEWAY                               │
│  CloudFront → ALB → Kong (Rate Limit: 1K req/sec per client)      │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│              EVENT INGESTION SERVICE (Python/Go)                    │
│  • Validate schema (Protobuf)                                      │
│  • Enrich (GeoIP, User-Agent parsing)                              │
│  • Deduplicate (Redis: SET event_id EX 3600)                       │
│  • Batch write to Kafka (1000 events/batch)                        │
└───────────┬────────────────────────────┬───────────────────────────┘
            │                            │
            ▼                            ▼
┌───────────────────────┐    ┌──────────────────────────────┐
│   APACHE KAFKA        │    │    REDIS (Fast Path)         │
│  Topic: user-events   │    │  HyperLogLog for last 24h    │
│  Partitions: 100      │    │  PFADD active:2025-11-15 uid │
│  Retention: 7 days    │    │  Latency: < 10ms             │
│  Throughput: 100K/s   │    └──────────────────────────────┘
└──────┬────────────────┘
       │
       ├───────────────┬───────────────┬──────────────────┐
       │               │               │                  │
       ▼               ▼               ▼                  ▼
┌─────────────┐ ┌──────────────┐ ┌──────────┐  ┌────────────────┐
│ FLINK JOB 1 │ │ FLINK JOB 2  │ │  SPARK   │  │   SNOWFLAKE    │
│ Real-time   │ │ Sessionize   │ │  Batch   │  │   Data Warehouse│
│ DAU Calc    │ │ Users        │ │  Jobs    │  │   (Long-term)  │
└──────┬──────┘ └──────┬───────┘ └────┬─────┘  └────────────────┘
       │               │               │
       │               │               ▼
       │               │        ┌─────────────┐
       │               │        │    S3       │
       │               │        │ Data Lake   │
       │               │        │  (Parquet)  │
       │               │        └─────────────┘
       │               │
       └───────┬───────┘
               ▼
┌────────────────────────────────────────────────────────────────────┐
│                    CLICKHOUSE CLUSTER                               │
│  • Distributed across 10 nodes                                     │
│  • Replication: 3x                                                 │
│  • Compression: LZ4 (5:1 ratio)                                    │
│  • Tables: events, dau, wau, mau, retention                        │
│  • Query latency: < 2s (P95)                                       │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                      QUERY SERVICE (GraphQL)                        │
│  • Cache frequent queries (Redis: 5 min TTL)                       │
│  • Query optimization (materialized views)                         │
│  • Rate limiting per dashboard                                     │
└──────────────────────────┬─────────────────────────────────────────┘
                           │
                           ▼
┌────────────────────────────────────────────────────────────────────┐
│                   VISUALIZATION LAYER                               │
│  Grafana Dashboards | Custom React Dashboards | Tableau            │
│  • Real-time DAU/MAU charts                                        │
│  • Cohort retention heatmaps                                       │
│  • Funnel analysis                                                 │
└────────────────────────────────────────────────────────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected event volume (QPS)?
- Accuracy requirements (exact vs approximate)?
- Latency requirements (real-time vs hourly)?
- Dimensions to track (country, device, custom)?
- Query patterns (dashboards vs ad-hoc)?
- Data retention policy?

**Topics to Cover**:
- HyperLogLog for memory-efficient unique counting
- Stream processing for real-time aggregation
- Lambda architecture (speed + batch layers)
- ClickHouse for OLAP workloads
- Data partitioning strategies
- Late arrival handling

**Common Follow-ups**:
- "How would you handle 10× traffic?"
  → Scale Kafka partitions, add Flink parallelism, shard ClickHouse
- "How do you ensure data quality?"
  → Schema validation, dead letter queues, monitoring
- "How do you calculate retention?"
  → Join signup cohort with daily activity, compute percentages
- "How do you handle GDPR deletions?"
  → User deletion flags, aggregate data doesn't need deletion

---

## Key Takeaways

1. **HyperLogLog is Essential**: Trade 0.81% accuracy for 99.9% memory savings
2. **Pre-aggregate Everything**: Raw event queries don't scale
3. **Lambda Architecture**: Combine real-time (Flink) + batch (Spark) for best of both worlds
4. **Columnar Storage**: ClickHouse/Druid perfect for OLAP analytics
5. **Partition Smart**: By date for time-series, by user for stateful processing
6. **Monitor Data Quality**: Bad data in = bad insights out
7. **Plan for Growth**: Design for 10× current scale from day one

## Further Reading

- "Designing Data-Intensive Applications" - Martin Kleppmann
- "HyperLogLog in Practice" - Google Research Paper
- "Apache Flink Documentation" - Stream Processing
- "ClickHouse Benchmark" - OLAP Performance
- Facebook's "Scuba" Paper - Real-time Data Analysis
