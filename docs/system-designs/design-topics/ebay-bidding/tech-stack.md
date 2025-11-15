# 🛠️ eBay Bidding System: Complete Technology Stack & DevOps Guide

## Overview

This document provides a comprehensive breakdown of the technology stack, DevOps solutions, and infrastructure choices used in production-grade auction and bidding systems like eBay, Christie's, and DealDash.

---

## 📚 Table of Contents

1. [Backend Services](#backend-services)
2. [Data Stores](#data-stores)
3. [Message Queues & Event Streaming](#message-queues--event-streaming)
4. [Caching Layer](#caching-layer)
5. [Search & Analytics](#search--analytics)
6. [Real-Time Communication](#real-time-communication)
7. [Infrastructure & Cloud](#infrastructure--cloud)
8. [DevOps & CI/CD](#devops--cicd)
9. [Monitoring & Observability](#monitoring--observability)
10. [Security & Compliance](#security--compliance)
11. [Industry Examples](#industry-examples)

---

## 1. Backend Services

### 1.1 Primary Language & Framework Choices

#### **Bid Service (High Performance Critical Path)**

**Technology**: **Go (Golang)**

**Why Go?**
- **Low Latency**: P99 latency <100ms critical for bid placement
- **Concurrency**: Goroutines handle 10K+ concurrent requests efficiently
- **Simplicity**: Easy to reason about, reduces bugs in critical path
- **Performance**: Compiled, near-C performance, low GC pauses

**Framework**: **Gin Web Framework**

```go
// Example Bid Service Structure
package main

import (
    "github.com/gin-gonic/gin"
    "github.com/go-redis/redis/v8"
    "gorm.io/gorm"
)

type BidService struct {
    db    *gorm.DB
    redis *redis.Client
    kafka *KafkaProducer
}

func (s *BidService) PlaceBid(c *gin.Context) {
    var req BidRequest
    if err := c.ShouldBindJSON(&req); err != nil {
        c.JSON(400, gin.H{"error": err.Error()})
        return
    }

    // Distributed lock
    lock := s.redis.SetNX(ctx, fmt.Sprintf("lock:auction:%d", req.AuctionID), "locked", 2*time.Second)
    if !lock.Val() {
        c.JSON(429, gin.H{"error": "too many concurrent bids"})
        return
    }
    defer s.redis.Del(ctx, fmt.Sprintf("lock:auction:%d", req.AuctionID))

    // Optimistic locking
    result := s.db.Exec(`
        UPDATE auctions
        SET current_price = ?, winner_id = ?, version = version + 1
        WHERE id = ? AND version = ?`,
        req.BidAmount, req.BidderID, req.AuctionID, currentVersion)

    if result.RowsAffected == 0 {
        // Retry logic
        return s.retryBid(c, req)
    }

    // Publish event to Kafka
    s.kafka.Publish("bid-events", bidEvent)

    c.JSON(200, gin.H{"success": true, "bid_id": bid.ID})
}
```

**Configuration**:
```yaml
# Go Service Configuration
service:
  name: bid-service
  port: 8080
  workers: 100  # Goroutines
  max_conn: 10000
  timeout: 5s

database:
  max_open_conns: 100
  max_idle_conns: 25
  conn_max_lifetime: 5m

redis:
  pool_size: 100
  max_retries: 3
```

**Alternatives Considered**:
- **Rust**: Even faster, but steeper learning curve
- **C++**: Maximum performance, but higher complexity
- **Java**: Good, but higher latency due to GC

---

#### **Auction Service (Business Logic)**

**Technology**: **Java 17 + Spring Boot 3**

**Why Java/Spring?**
- **Mature Ecosystem**: Spring Data, Spring Security, Spring Scheduler
- **Enterprise Features**: Transaction management, AOP, comprehensive logging
- **Team Familiarity**: Large pool of Java developers
- **Robustness**: Proven at eBay scale for 20+ years

**Framework**: **Spring Boot 3 + Spring Cloud**

```java
@Service
@Transactional
public class AuctionService {

    @Autowired
    private AuctionRepository auctionRepo;

    @Autowired
    private KafkaTemplate<String, AuctionEvent> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, Auction> redisTemplate;

    @Cacheable(value = "auctions", key = "#auctionId", unless = "#result == null")
    public Auction getAuction(Long auctionId) {
        return auctionRepo.findById(auctionId)
            .orElseThrow(() -> new AuctionNotFoundException(auctionId));
    }

    @Scheduled(cron = "0 * * * * *")  // Every minute
    public void checkEndingAuctions() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneMinuteLater = now.plusMinutes(1);

        List<Auction> endingAuctions = auctionRepo.findByStatusAndEndTimeBetween(
            AuctionStatus.ACTIVE, now, oneMinuteLater);

        for (Auction auction : endingAuctions) {
            scheduleExactEndJob(auction);
        }
    }

    private void scheduleExactEndJob(Auction auction) {
        long delayMs = ChronoUnit.MILLIS.between(LocalDateTime.now(), auction.getEndTime());
        scheduler.schedule(() -> endAuction(auction.getId()), delayMs, TimeUnit.MILLISECONDS);
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void endAuction(Long auctionId) {
        Auction auction = auctionRepo.findByIdForUpdate(auctionId);

        if (auction.getStatus() != AuctionStatus.ACTIVE) {
            return;  // Already ended (idempotency)
        }

        auction.setStatus(AuctionStatus.ENDED);

        if (auction.getCurrentPrice().compareTo(auction.getReservePrice()) >= 0) {
            auction.setWinnerId(auction.getCurrentWinnerId());
            kafkaTemplate.send("auction-events", new AuctionEndedEvent(auction));
            paymentService.initiatePayment(auction);
        } else {
            auction.setStatus(AuctionStatus.ENDED_NO_WINNER);
        }

        auctionRepo.save(auction);
        redisTemplate.delete("auction:" + auctionId);
    }
}
```

**Dependencies (pom.xml)**:
```xml
<dependencies>
    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.1.5</version>
    </dependency>

    <!-- Spring Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.6.0</version>
    </dependency>

    <!-- Redis -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>

    <!-- Kafka -->
    <dependency>
        <groupId>org.springframework.kafka</groupId>
        <artifactId>spring-kafka</artifactId>
    </dependency>

    <!-- Scheduler -->
    <dependency>
        <groupId>org.quartz-scheduler</groupId>
        <artifactId>quartz</artifactId>
        <version>2.3.2</version>
    </dependency>
</dependencies>
```

---

#### **Search Service (Fast Queries)**

**Technology**: **Python 3.11 + FastAPI**

**Why Python/FastAPI?**
- **Elasticsearch Integration**: Excellent Python client libraries
- **FastAPI**: Modern, async, auto-generated OpenAPI docs
- **Data Science**: ML-based relevance ranking, personalization
- **Rapid Development**: Quick iterations on search algorithms

```python
from fastapi import FastAPI, Query
from elasticsearch import AsyncElasticsearch
from pydantic import BaseModel
from typing import Optional, List

app = FastAPI()
es = AsyncElasticsearch(["http://elasticsearch:9200"])

class SearchRequest(BaseModel):
    query: str
    category: Optional[str] = None
    price_min: Optional[float] = None
    price_max: Optional[float] = None
    sort: str = "ending_soon"

@app.get("/api/v1/search")
async def search_auctions(
    q: str = Query(..., min_length=1),
    category: Optional[str] = None,
    price_min: Optional[float] = None,
    price_max: Optional[float] = None,
    sort: str = "ending_soon",
    page: int = 1,
    size: int = 20
):
    # Build Elasticsearch query
    must_clauses = [
        {"match": {"title": {"query": q, "fuzziness": "AUTO"}}},
        {"term": {"status": "active"}}
    ]

    if category:
        must_clauses.append({"term": {"category": category}})

    filter_clauses = []
    if price_min or price_max:
        range_filter = {"range": {"current_price": {}}}
        if price_min:
            range_filter["range"]["current_price"]["gte"] = price_min
        if price_max:
            range_filter["range"]["current_price"]["lte"] = price_max
        filter_clauses.append(range_filter)

    sort_mapping = {
        "ending_soon": [{"end_time": "asc"}],
        "price_asc": [{"current_price": "asc"}],
        "price_desc": [{"current_price": "desc"}],
        "popular": [{"total_bids": "desc"}]
    }

    query = {
        "query": {
            "bool": {
                "must": must_clauses,
                "filter": filter_clauses
            }
        },
        "sort": sort_mapping.get(sort, [{"_score": "desc"}]),
        "from": (page - 1) * size,
        "size": size
    }

    result = await es.search(index="auctions", body=query)

    return {
        "total": result["hits"]["total"]["value"],
        "auctions": [hit["_source"] for hit in result["hits"]["hits"]],
        "page": page,
        "size": size
    }
```

**Requirements (requirements.txt)**:
```
fastapi==0.104.1
uvicorn[standard]==0.24.0
elasticsearch==8.10.0
pydantic==2.4.2
redis==5.0.1
httpx==0.25.0
```

---

#### **Notification Service (Real-time)**

**Technology**: **Node.js + Express + Socket.IO**

**Why Node.js?**
- **WebSocket Excellence**: Native async I/O perfect for WebSocket
- **High Concurrency**: Event loop handles 10K+ connections per process
- **NPM Ecosystem**: Rich libraries for push notifications (FCM, APNS)
- **JavaScript Everywhere**: Same language as frontend

```javascript
const express = require('express');
const http = require('http');
const socketIO = require('socket.io');
const { Kafka } = require('kafkajs');
const Redis = require('ioredis');

const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
    cors: { origin: "*" },
    transports: ['websocket', 'polling']
});

const redis = new Redis({ host: 'redis', port: 6379 });
const kafka = new Kafka({
    clientId: 'notification-service',
    brokers: ['kafka:9092']
});

// Connection manager
const auctionRooms = new Map();

io.on('connection', (socket) => {
    console.log('Client connected:', socket.id);

    socket.on('subscribe', async ({ auction_id, token }) => {
        // Verify JWT token
        const user = await verifyToken(token);
        if (!user) {
            socket.emit('error', { message: 'Unauthorized' });
            return;
        }

        // Join room for this auction
        socket.join(`auction:${auction_id}`);
        socket.userId = user.id;
        socket.auctionId = auction_id;

        // Track connection in Redis
        await redis.sadd(`auction:${auction_id}:watchers`, user.id);

        console.log(`User ${user.id} subscribed to auction ${auction_id}`);
    });

    socket.on('disconnect', async () => {
        if (socket.userId && socket.auctionId) {
            await redis.srem(`auction:${socket.auctionId}:watchers`, socket.userId);
        }
        console.log('Client disconnected:', socket.id);
    });
});

// Kafka consumer for bid events
const consumer = kafka.consumer({ groupId: 'notification-group' });

async function consumeBidEvents() {
    await consumer.connect();
    await consumer.subscribe({ topic: 'bid-events', fromBeginning: false });

    await consumer.run({
        eachMessage: async ({ topic, partition, message }) => {
            const event = JSON.parse(message.value.toString());

            if (event.type === 'bid.placed') {
                // Broadcast to all watchers of this auction
                io.to(`auction:${event.auction_id}`).emit('bid_placed', {
                    auction_id: event.auction_id,
                    current_price: event.bid_amount,
                    total_bids: event.total_bids,
                    time_remaining: event.time_remaining
                });

                // Send outbid notification to previous winner
                if (event.previous_winner_id) {
                    io.to(event.previous_winner_id).emit('outbid', {
                        auction_id: event.auction_id,
                        message: "You've been outbid!",
                        current_price: event.bid_amount,
                        your_bid: event.previous_bid
                    });

                    // Also send push notification (mobile)
                    await sendPushNotification(event.previous_winner_id, {
                        title: "Outbid!",
                        body: `You've been outbid on ${event.auction_title}`,
                        data: { auction_id: event.auction_id }
                    });
                }
            }
        }
    });
}

consumeBidEvents();

server.listen(3000, () => {
    console.log('WebSocket server running on port 3000');
});
```

**Package.json**:
```json
{
  "name": "notification-service",
  "version": "1.0.0",
  "dependencies": {
    "express": "^4.18.2",
    "socket.io": "^4.6.1",
    "kafkajs": "^2.2.4",
    "ioredis": "^5.3.2",
    "jsonwebtoken": "^9.0.2",
    "firebase-admin": "^11.11.0",
    "apn": "^2.2.0"
  }
}
```

---

## 2. Data Stores

### 2.1 Primary Database

**Technology**: **PostgreSQL 15**

**Why PostgreSQL?**
- **ACID Guarantees**: Critical for bid consistency
- **Optimistic Locking**: Row-level versioning support
- **Mature**: 30+ years, battle-tested at scale
- **Rich Indexing**: B-tree, GiST, GIN, partial indexes
- **JSON Support**: Flexible schema where needed

**Configuration (postgresql.conf)**:
```
# Connection Settings
max_connections = 500
shared_buffers = 16GB
effective_cache_size = 48GB

# Write Performance
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 4GB
min_wal_size = 1GB

# Query Performance
random_page_cost = 1.1  # SSD optimized
effective_io_concurrency = 200

# Replication
wal_level = replica
max_wal_senders = 10
max_replication_slots = 10
hot_standby = on

# Logging
log_min_duration_statement = 1000  # Log slow queries > 1s
log_line_prefix = '%t [%p]: user=%u,db=%d,app=%a,client=%h '
log_checkpoints = on
log_connections = on
log_disconnections = on
```

**Sharding Strategy**: **Consistent Hashing by auction_id**

```sql
-- Shard routing function
CREATE OR REPLACE FUNCTION get_shard(auction_id BIGINT)
RETURNS INTEGER AS $$
BEGIN
    RETURN auction_id % 16;  -- 16 shards
END;
$$ LANGUAGE plpgsql IMMUTABLE;

-- Example: Route bid to correct shard
-- In application layer:
shard_id = auction_id % 16
connection = shard_connections[shard_id]
```

**High Availability**: **Patroni + etcd**

```yaml
# Patroni configuration
scope: postgres-auction-cluster
name: postgres-node1

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576

postgresql:
  use_pg_rewind: true
  parameters:
    max_connections: 500
    shared_buffers: 16GB
    wal_level: replica

etcd:
  hosts: etcd1:2379,etcd2:2379,etcd3:2379
```

**Connection Pooling**: **PgBouncer**

```ini
[databases]
auction_db = host=localhost port=5432 dbname=auction_db

[pgbouncer]
pool_mode = transaction
max_client_conn = 10000
default_pool_size = 25
max_db_connections = 100
reserve_pool_size = 5
reserve_pool_timeout = 3
```

---

### 2.2 Caching Layer

**Technology**: **Redis Cluster 7.0**

**Why Redis?**
- **In-Memory Performance**: Sub-millisecond latency
- **Rich Data Structures**: Sorted sets, hashes, sets, streams
- **Pub/Sub**: Real-time messaging
- **Distributed Locks**: SETNX for concurrency control
- **Clustering**: Horizontal scaling built-in

**Cluster Configuration**:
```
# Redis Cluster: 6 nodes (3 masters + 3 replicas)
# Master nodes
redis-1:7001 (slots 0-5461)
redis-2:7002 (slots 5462-10922)
redis-3:7003 (slots 10923-16383)

# Replica nodes
redis-4:7004 (replica of redis-1)
redis-5:7005 (replica of redis-2)
redis-6:7006 (replica of redis-3)
```

**redis.conf**:
```
# Cluster Mode
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
cluster-replica-validity-factor 0

# Memory Management
maxmemory 64gb
maxmemory-policy allkeys-lru
maxmemory-samples 10

# Persistence (RDB + AOF for durability)
save 900 1
save 300 10
save 60 10000

appendonly yes
appendfsync everysec
auto-aof-rewrite-percentage 100
auto-aof-rewrite-min-size 64mb

# Performance
tcp-backlog 511
timeout 0
tcp-keepalive 300
slowlog-log-slower-than 10000
slowlog-max-len 128
```

**Use Cases & Data Structures**:

```python
import redis

r = redis.Redis(host='localhost', port=7001, decode_responses=True)

# 1. Auction metadata cache
auction_key = f"auction:{auction_id}"
r.setex(auction_key, 300, json.dumps(auction_data))  # 5 min TTL

# 2. Current highest bid (sorted set for leaderboard)
r.zadd(f"auction:{auction_id}:bids", {f"bid:{bid_id}": bid_amount})
top_bid = r.zrevrange(f"auction:{auction_id}:bids", 0, 0, withscores=True)[0]

# 3. User watchlist
r.sadd(f"user:{user_id}:watchlist", auction_id)
watching = r.smembers(f"user:{user_id}:watchlist")

# 4. Distributed lock for bid placement
lock_key = f"lock:auction:{auction_id}"
lock_acquired = r.set(lock_key, "locked", nx=True, ex=2)  # 2 sec expiry

if lock_acquired:
    try:
        # Place bid
        pass
    finally:
        r.delete(lock_key)

# 5. Rate limiting (token bucket)
user_key = f"rate_limit:user:{user_id}"
current = r.get(user_key)
if current is None or int(current) < 100:
    r.incr(user_key)
    r.expire(user_key, 3600)  # 100 bids per hour
else:
    raise RateLimitError("Too many bids")

# 6. Session management
session_key = f"session:{token}"
r.setex(session_key, 3600, json.dumps(user_session))

# 7. Trending auctions (sorted by bid count)
r.zincrby("trending_auctions", 1, auction_id)
trending = r.zrevrange("trending_auctions", 0, 9, withscores=True)  # Top 10
```

---

## 3. Message Queues & Event Streaming

### 3.1 Apache Kafka

**Why Kafka?**
- **High Throughput**: Millions of events/sec
- **Durability**: Persistent event log
- **Scalability**: Partitioned topics
- **Replay**: Consumer can replay from any offset
- **Exactly-once**: Critical for financial transactions

**Cluster Configuration**:
```
# 3 Kafka brokers for replication
kafka-1:9092
kafka-2:9092
kafka-3:9092

# 3 Zookeeper nodes for coordination
zookeeper-1:2181
zookeeper-2:2181
zookeeper-3:2181
```

**server.properties**:
```
# Broker ID
broker.id=1

# Listeners
listeners=PLAINTEXT://0.0.0.0:9092
advertised.listeners=PLAINTEXT://kafka-1:9092

# Log Configuration
log.dirs=/var/lib/kafka/data
num.partitions=100  # Default partitions per topic
default.replication.factor=3
min.insync.replicas=2

# Performance
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Retention
log.retention.hours=168  # 7 days
log.segment.bytes=1073741824  # 1 GB
log.retention.check.interval.ms=300000

# Zookeeper
zookeeper.connect=zookeeper-1:2181,zookeeper-2:2181,zookeeper-3:2181
zookeeper.connection.timeout.ms=18000
```

**Topic Configuration**:
```bash
# bid-events topic (high volume)
kafka-topics --create \
  --topic bid-events \
  --partitions 100 \
  --replication-factor 3 \
  --config retention.ms=604800000 \  # 7 days
  --config compression.type=lz4 \
  --bootstrap-server kafka-1:9092

# auction-events topic
kafka-topics --create \
  --topic auction-events \
  --partitions 50 \
  --replication-factor 3 \
  --config retention.ms=2592000000 \  # 30 days
  --bootstrap-server kafka-1:9092

# notification-events topic
kafka-topics --create \
  --topic notification-events \
  --partitions 20 \
  --replication-factor 3 \
  --config retention.ms=86400000 \  # 1 day
  --bootstrap-server kafka-1:9092
```

**Producer Configuration (Java)**:
```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

// Performance tuning
props.put("acks", "all");  // Wait for all replicas
props.put("retries", 3);
props.put("batch.size", 16384);
props.put("linger.ms", 10);  // Wait up to 10ms to batch
props.put("compression.type", "lz4");
props.put("max.in.flight.requests.per.connection", 5);

// Idempotence (exactly-once semantics)
props.put("enable.idempotence", true);

KafkaProducer<String, String> producer = new KafkaProducer<>(props);
```

**Consumer Configuration (Java)**:
```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
props.put("group.id", "notification-service");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

// Consumer tuning
props.put("enable.auto.commit", false);  // Manual commit for exactly-once
props.put("max.poll.records", 500);
props.put("fetch.min.bytes", 1024);
props.put("fetch.max.wait.ms", 500);

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("bid-events", "auction-events"));
```

---

## 4. Search & Analytics

### 4.1 Elasticsearch

**Cluster Configuration**:
```
# 6 data nodes + 3 master nodes
es-master-1, es-master-2, es-master-3 (master-eligible only)
es-data-1 through es-data-6 (data nodes)
```

**elasticsearch.yml**:
```yaml
cluster.name: auction-search-cluster
node.name: es-data-1
node.roles: [ data, ingest ]

network.host: 0.0.0.0
http.port: 9200
transport.port: 9300

discovery.seed_hosts: ["es-master-1", "es-master-2", "es-master-3"]
cluster.initial_master_nodes: ["es-master-1", "es-master-2", "es-master-3"]

# Performance
thread_pool.search.queue_size: 1000
indices.memory.index_buffer_size: 30%
indices.queries.cache.size: 15%

# JVM Heap (50% of RAM, max 32GB)
-Xms16g
-Xmx16g
```

**Index Mapping**:
```json
PUT /auctions
{
  "settings": {
    "number_of_shards": 12,
    "number_of_replicas": 1,
    "refresh_interval": "1s",
    "index.max_result_window": 100000
  },
  "mappings": {
    "properties": {
      "auction_id": { "type": "keyword" },
      "title": {
        "type": "text",
        "analyzer": "english",
        "fields": {
          "keyword": { "type": "keyword" },
          "autocomplete": {
            "type": "text",
            "analyzer": "autocomplete"
          }
        }
      },
      "description": {
        "type": "text",
        "analyzer": "english"
      },
      "category": { "type": "keyword" },
      "current_price": { "type": "double" },
      "starting_price": { "type": "double" },
      "buy_now_price": { "type": "double" },
      "end_time": { "type": "date" },
      "created_at": { "type": "date" },
      "total_bids": { "type": "integer" },
      "seller": {
        "properties": {
          "id": { "type": "keyword" },
          "username": { "type": "keyword" },
          "reputation": { "type": "double" }
        }
      },
      "status": { "type": "keyword" },
      "location": { "type": "geo_point" },
      "images": { "type": "keyword" },
      "shipping_free": { "type": "boolean" }
    }
  }
}
```

### 4.2 ClickHouse (Analytics)

**Why ClickHouse?**
- **Column-oriented**: Perfect for analytics queries
- **Compression**: 10x data compression
- **Fast Aggregations**: Billion-row queries in seconds
- **Scalable**: Distributed tables across cluster

**Configuration**:
```xml
<!-- config.xml -->
<clickhouse>
    <max_connections>4096</max_connections>
    <max_concurrent_queries>100</max_concurrent_queries>
    <max_memory_usage>50000000000</max_memory_usage>

    <merge_tree>
        <max_bytes_to_merge_at_max_space_usage>100000000000</max_bytes_to_merge_at_max_space_usage>
    </merge_tree>

    <distributed_ddl>
        <path>/clickhouse/task_queue/ddl</path>
    </distributed_ddl>
</clickhouse>
```

**Schema Design**:
```sql
-- Bid events table (time-series)
CREATE TABLE bid_events ON CLUSTER auction_cluster
(
    event_id UUID DEFAULT generateUUIDv4(),
    auction_id UInt64,
    bidder_id UInt64,
    bid_amount Decimal(10, 2),
    max_bid_amount Nullable(Decimal(10, 2)),
    bid_type Enum8('manual' = 1, 'auto' = 2),
    timestamp DateTime,
    ip_address IPv4,
    user_agent String,
    country_code FixedString(2),
    city String,
    device_type Enum8('desktop' = 1, 'mobile' = 2, 'tablet' = 3),
    referrer String
)
ENGINE = ReplicatedMergeTree('/clickhouse/tables/{shard}/bid_events', '{replica}')
PARTITION BY toYYYYMM(timestamp)
ORDER BY (auction_id, timestamp)
SETTINGS index_granularity = 8192;

-- Distributed table for queries
CREATE TABLE bid_events_distributed ON CLUSTER auction_cluster AS bid_events
ENGINE = Distributed(auction_cluster, default, bid_events, rand());

-- Materialized view for hourly aggregations
CREATE MATERIALIZED VIEW bid_hourly_stats ON CLUSTER auction_cluster
ENGINE = SummingMergeTree()
PARTITION BY toYYYYMMDD(hour)
ORDER BY (auction_id, hour)
AS SELECT
    auction_id,
    toStartOfHour(timestamp) AS hour,
    count() AS bid_count,
    avg(bid_amount) AS avg_bid,
    max(bid_amount) AS max_bid,
    uniq(bidder_id) AS unique_bidders
FROM bid_events
GROUP BY auction_id, hour;

-- Example analytics queries
-- Top auctions by bid volume (last 24 hours)
SELECT
    auction_id,
    count() AS total_bids,
    uniq(bidder_id) AS unique_bidders,
    max(bid_amount) AS highest_bid
FROM bid_events_distributed
WHERE timestamp >= now() - INTERVAL 24 HOUR
GROUP BY auction_id
ORDER BY total_bids DESC
LIMIT 10;

-- Bid activity by hour of day
SELECT
    toHour(timestamp) AS hour,
    count() AS bid_count,
    avg(bid_amount) AS avg_bid_amount
FROM bid_events_distributed
WHERE timestamp >= today()
GROUP BY hour
ORDER BY hour;
```

---

## 5. Infrastructure & Cloud

### 5.1 Cloud Provider: AWS

**Architecture Overview**:
```
┌─────────────────────────────────────────────────────┐
│                     VPC (10.0.0.0/16)               │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Public Subnet (10.0.1.0/24)                 │   │
│  │  - NAT Gateway                                │   │
│  │  - Application Load Balancer                  │   │
│  │  - Bastion Host                               │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Private Subnet 1 (10.0.10.0/24) - AZ1      │   │
│  │  - EKS Worker Nodes (Bid Service)            │   │
│  │  - ElastiCache Redis Cluster                 │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Private Subnet 2 (10.0.11.0/24) - AZ2      │   │
│  │  - EKS Worker Nodes (Auction Service)        │   │
│  │  - RDS PostgreSQL Primary                    │   │
│  └──────────────────────────────────────────────┘   │
│                                                      │
│  ┌──────────────────────────────────────────────┐   │
│  │  Private Subnet 3 (10.0.12.0/24) - AZ3      │   │
│  │  - EKS Worker Nodes (Search Service)         │   │
│  │  - RDS PostgreSQL Replicas                   │   │
│  └──────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
```

**Key AWS Services Used**:

1. **EKS (Elastic Kubernetes Service)**
   - Managed Kubernetes for microservices
   - Auto-scaling node groups
   - Spot instances for cost savings

2. **RDS PostgreSQL**
   - Multi-AZ deployment
   - Read replicas (3x)
   - Automated backups
   - Instance: db.r6g.4xlarge

3. **ElastiCache for Redis**
   - Cluster mode enabled
   - 6 nodes (3 shards + 3 replicas)
   - Instance: cache.r6g.xlarge

4. **MSK (Managed Streaming for Kafka)**
   - 3-node cluster (m5.xlarge)
   - Apache Kafka 3.5
   - Encryption in-transit and at-rest

5. **Elasticsearch Service**
   - 9-node cluster
   - 3 master (m5.large)
   - 6 data (r5.2xlarge)

6. **S3**
   - Auction images
   - Database backups
   - Application logs
   - Analytics exports

7. **CloudFront**
   - CDN for static assets
   - Image delivery
   - API caching (limited)

8. **Route 53**
   - DNS management
   - Health checks
   - Geo-routing

9. **ELB (Application Load Balancer)**
   - SSL termination
   - Path-based routing
   - Health checks

10. **CloudWatch**
    - Metrics collection
    - Log aggregation
    - Alarms

---

### 5.2 Kubernetes Configuration

**Cluster Setup**:
```yaml
# eksctl config
apiVersion: eksctl.io/v1alpha5
kind: ClusterConfig

metadata:
  name: auction-production
  region: us-east-1
  version: "1.27"

vpc:
  cidr: 10.0.0.0/16
  nat:
    gateway: HighlyAvailable

managedNodeGroups:
  - name: bid-service-ng
    instanceType: c5.2xlarge
    desiredCapacity: 10
    minSize: 5
    maxSize: 50
    labels:
      workload: bid-service
    tags:
      service: bid-service
      environment: production

  - name: auction-service-ng
    instanceType: m5.xlarge
    desiredCapacity: 5
    minSize: 3
    maxSize: 20
    labels:
      workload: auction-service

  - name: websocket-ng
    instanceType: c5.large
    desiredCapacity: 20
    minSize: 10
    maxSize: 100
    labels:
      workload: websocket

addons:
  - name: vpc-cni
  - name: coredns
  - name: kube-proxy
  - name: aws-ebs-csi-driver
```

**Bid Service Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: bid-service
  namespace: production
spec:
  replicas: 10
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 3
      maxUnavailable: 1
  selector:
    matchLabels:
      app: bid-service
  template:
    metadata:
      labels:
        app: bid-service
    spec:
      containers:
      - name: bid-service
        image: ecr.us-east-1.amazonaws.com/bid-service:v2.5.3
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: host
        - name: REDIS_HOST
          value: "redis-cluster.cache.amazonaws.com:6379"
        - name: KAFKA_BROKERS
          value: "kafka-1:9092,kafka-2:9092,kafka-3:9092"
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5

---
apiVersion: v1
kind: Service
metadata:
  name: bid-service
  namespace: production
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
  selector:
    app: bid-service

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: bid-service-hpa
  namespace: production
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: bid-service
  minReplicas: 5
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  behavior:
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
```

---

## 6. DevOps & CI/CD

### 6.1 CI/CD Pipeline (GitLab CI)

**.gitlab-ci.yml**:
```yaml
stages:
  - test
  - build
  - deploy

variables:
  DOCKER_REGISTRY: ecr.us-east-1.amazonaws.com
  AWS_REGION: us-east-1

# Test Stage
test:bid-service:
  stage: test
  image: golang:1.21
  script:
    - cd bid-service
    - go test -v -race -coverprofile=coverage.out ./...
    - go tool cover -func=coverage.out
  coverage: '/total:\s+\(statements\)\s+(\d+\.\d+)%/'
  artifacts:
    reports:
      coverage_report:
        coverage_format: cobertura
        path: bid-service/coverage.xml

test:auction-service:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  script:
    - cd auction-service
    - mvn clean test
    - mvn jacoco:report
  artifacts:
    reports:
      junit: auction-service/target/surefire-reports/*.xml

# Build Stage
build:bid-service:
  stage: build
  image: docker:20.10
  services:
    - docker:20.10-dind
  before_script:
    - aws ecr get-login-password --region $AWS_REGION | docker login --username AWS --password-stdin $DOCKER_REGISTRY
  script:
    - cd bid-service
    - docker build -t $DOCKER_REGISTRY/bid-service:$CI_COMMIT_SHA .
    - docker tag $DOCKER_REGISTRY/bid-service:$CI_COMMIT_SHA $DOCKER_REGISTRY/bid-service:latest
    - docker push $DOCKER_REGISTRY/bid-service:$CI_COMMIT_SHA
    - docker push $DOCKER_REGISTRY/bid-service:latest
  only:
    - main
    - develop

# Deploy Stage
deploy:production:
  stage: deploy
  image: alpine/k8s:1.27.3
  script:
    - aws eks update-kubeconfig --region $AWS_REGION --name auction-production
    - kubectl set image deployment/bid-service bid-service=$DOCKER_REGISTRY/bid-service:$CI_COMMIT_SHA -n production
    - kubectl rollout status deployment/bid-service -n production --timeout=5m
  environment:
    name: production
    url: https://api.auction.com
  when: manual
  only:
    - main
```

### 6.2 Infrastructure as Code (Terraform)

**main.tf**:
```hcl
terraform {
  required_version = ">= 1.5"
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
    kubernetes = {
      source  = "hashicorp/kubernetes"
      version = "~> 2.20"
    }
  }

  backend "s3" {
    bucket = "auction-terraform-state"
    key    = "production/terraform.tfstate"
    region = "us-east-1"
    encrypt = true
    dynamodb_table = "terraform-locks"
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC
module "vpc" {
  source = "terraform-aws-modules/vpc/aws"
  version = "5.0.0"

  name = "auction-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["us-east-1a", "us-east-1b", "us-east-1c"]
  private_subnets = ["10.0.10.0/24", "10.0.11.0/24", "10.0.12.0/24"]
  public_subnets  = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]

  enable_nat_gateway = true
  enable_vpn_gateway = false
  enable_dns_hostnames = true

  tags = {
    Environment = "production"
    Project     = "auction"
  }
}

# RDS PostgreSQL
module "db" {
  source = "terraform-aws-modules/rds/aws"
  version = "6.0.0"

  identifier = "auction-db"

  engine            = "postgres"
  engine_version    = "15.3"
  instance_class    = "db.r6g.4xlarge"
  allocated_storage = 2000
  storage_encrypted = true

  db_name  = "auction_db"
  username = "auction_admin"
  password = random_password.db_password.result

  vpc_security_group_ids = [aws_security_group.rds_sg.id]
  db_subnet_group_name   = aws_db_subnet_group.rds_subnet.name

  multi_az = true
  backup_retention_period = 30
  backup_window = "03:00-04:00"
  maintenance_window = "mon:04:00-mon:05:00"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  parameters = [
    {
      name  = "max_connections"
      value = "500"
    },
    {
      name  = "shared_buffers"
      value = "16777216"  # 16GB in KB
    }
  ]

  tags = {
    Environment = "production"
  }
}

# ElastiCache Redis
module "redis" {
  source = "terraform-aws-modules/elasticache/aws"
  version = "1.2.0"

  cluster_id = "auction-redis"
  engine     = "redis"
  engine_version = "7.0"
  node_type  = "cache.r6g.xlarge"

  num_cache_clusters = 6
  parameter_group_name = "default.redis7.cluster.on"
  port = 6379

  subnet_ids = module.vpc.private_subnets
  security_group_ids = [aws_security_group.redis_sg.id]

  automatic_failover_enabled = true
  multi_az_enabled = true

  tags = {
    Environment = "production"
  }
}

# EKS Cluster
module "eks" {
  source = "terraform-aws-modules/eks/aws"
  version = "19.15.0"

  cluster_name    = "auction-production"
  cluster_version = "1.27"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  cluster_endpoint_public_access = true

  eks_managed_node_groups = {
    bid_service = {
      min_size     = 5
      max_size     = 50
      desired_size = 10

      instance_types = ["c5.2xlarge"]
      capacity_type  = "ON_DEMAND"

      labels = {
        workload = "bid-service"
      }

      tags = {
        Service = "bid-service"
      }
    }

    auction_service = {
      min_size     = 3
      max_size     = 20
      desired_size = 5

      instance_types = ["m5.xlarge"]
      capacity_type  = "ON_DEMAND"

      labels = {
        workload = "auction-service"
      }
    }
  }

  tags = {
    Environment = "production"
  }
}

# MSK (Kafka)
resource "aws_msk_cluster" "auction_kafka" {
  cluster_name           = "auction-kafka"
  kafka_version          = "3.5.1"
  number_of_broker_nodes = 3

  broker_node_group_info {
    instance_type = "kafka.m5.xlarge"
    client_subnets = module.vpc.private_subnets
    storage_info {
      ebs_storage_info {
        volume_size = 1000
      }
    }
    security_groups = [aws_security_group.kafka_sg.id]
  }

  encryption_info {
    encryption_in_transit {
      client_broker = "TLS"
      in_cluster    = true
    }
  }

  logging_info {
    broker_logs {
      cloudwatch_logs {
        enabled   = true
        log_group = aws_cloudwatch_log_group.kafka_logs.name
      }
    }
  }

  tags = {
    Environment = "production"
  }
}
```

---

## 7. Monitoring & Observability

### 7.1 Prometheus + Grafana

**Prometheus Configuration**:
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Kubernetes service discovery
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__

  # PostgreSQL Exporter
  - job_name: 'postgresql'
    static_configs:
      - targets: ['postgres-exporter:9187']

  # Redis Exporter
  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

  # Kafka Exporter
  - job_name: 'kafka'
    static_configs:
      - targets: ['kafka-exporter:9308']

alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']

rule_files:
  - /etc/prometheus/rules/*.yml
```

**Alert Rules**:
```yaml
# alerts.yml
groups:
  - name: auction_alerts
    interval: 30s
    rules:
      # High bid latency
      - alert: HighBidLatency
        expr: histogram_quantile(0.95, rate(bid_placement_duration_seconds_bucket[5m])) > 0.5
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High bid placement latency"
          description: "P95 bid latency is {{ $value }}s (threshold: 0.5s)"

      # High bid failure rate
      - alert: HighBidFailureRate
        expr: rate(bid_failures_total[5m]) / rate(bid_attempts_total[5m]) > 0.05
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High bid failure rate"
          description: "{{ $value | humanizePercentage }} of bids are failing"

      # Database replication lag
      - alert: DatabaseReplicationLag
        expr: pg_replication_lag_seconds > 10
        for: 3m
        labels:
          severity: warning
        annotations:
          summary: "PostgreSQL replication lag is high"
          description: "Replication lag is {{ $value }}s on {{ $labels.instance }}"

      # Redis memory usage
      - alert: RedisHighMemory
        expr: redis_memory_used_bytes / redis_memory_max_bytes > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Redis memory usage is high"
          description: "Redis is using {{ $value | humanizePercentage }} of max memory"

      # Kafka consumer lag
      - alert: KafkaConsumerLag
        expr: kafka_consumergroup_lag > 10000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Kafka consumer lag is high"
          description: "Consumer {{ $labels.consumergroup }} has lag of {{ $value }} messages"

      # WebSocket disconnections
      - alert: HighWebSocketDisconnections
        expr: rate(websocket_disconnections_total[5m]) > 100
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "High WebSocket disconnection rate"
          description: "{{ $value }} disconnections/sec"
```

**Grafana Dashboards** (Key Metrics):
1. **Auction Overview**
   - Active auctions count
   - Bids per second (real-time)
   - Auctions ending per hour
   - Total GMV (Gross Merchandise Value)

2. **Bid Service Dashboard**
   - Request rate (QPS)
   - Latency (P50, P95, P99)
   - Error rate
   - Concurrency (goroutines)
   - Retry rate

3. **Database Dashboard**
   - Connections (active, idle, waiting)
   - Query latency
   - Slow queries (>1s)
   - Replication lag
   - Deadlocks

4. **Cache Dashboard**
   - Hit/Miss ratio
   - Memory usage
   - Evictions
   - Command latency

5. **Kafka Dashboard**
   - Message rate (in/out)
   - Consumer lag
   - Partition distribution
   - Replication status

---

### 7.2 Distributed Tracing (Jaeger)

**Instrumentation Example (Go)**:
```go
import (
    "github.com/opentracing/opentracing-go"
    "github.com/uber/jaeger-client-go"
    jaegercfg "github.com/uber/jaeger-client-go/config"
)

func initJaeger(serviceName string) (opentracing.Tracer, io.Closer, error) {
    cfg := jaegercfg.Configuration{
        ServiceName: serviceName,
        Sampler: &jaegercfg.SamplerConfig{
            Type:  jaeger.SamplerTypeConst,
            Param: 1,  // Sample 100% (adjust in production)
        },
        Reporter: &jaegercfg.ReporterConfig{
            LogSpans:           true,
            LocalAgentHostPort: "jaeger-agent:6831",
        },
    }

    tracer, closer, err := cfg.NewTracer()
    if err != nil {
        return nil, nil, err
    }

    opentracing.SetGlobalTracer(tracer)
    return tracer, closer, nil
}

// Usage in bid placement
func (s *BidService) PlaceBid(c *gin.Context) {
    span := opentracing.StartSpan("PlaceBid")
    defer span.Finish()

    ctx := opentracing.ContextWithSpan(c.Request.Context(), span)

    // Child span for validation
    validationSpan, validationCtx := opentracing.StartSpanFromContext(ctx, "ValidateBid")
    err := s.validateBid(validationCtx, req)
    validationSpan.Finish()

    // Child span for DB write
    dbSpan, dbCtx := opentracing.StartSpanFromContext(ctx, "DatabaseWrite")
    err = s.db.WithContext(dbCtx).Create(&bid).Error
    dbSpan.Finish()

    // Child span for Kafka publish
    kafkaSpan, kafkaCtx := opentracing.StartSpanFromContext(ctx, "KafkaPublish")
    s.kafka.Publish(kafkaCtx, "bid-events", event)
    kafkaSpan.Finish()
}
```

---

## 8. Industry Examples

### 8.1 eBay's Tech Stack

**Known Stack (from public sources)**:
- **Backend**: Java, Node.js, Scala
- **Database**: Oracle (primary), MySQL, Cassandra
- **Cache**: Redis, Memcached
- **Search**: Elasticsearch (migrated from custom Cassini)
- **Message Queue**: Kafka
- **Infrastructure**: Multi-cloud (private + public cloud)
- **Containers**: Kubernetes
- **Monitoring**: Prometheus, Grafana, custom tools
- **CDN**: Akamai

**Scale**:
- 182 million active buyers (2023)
- 1.7 billion listings
- $73 billion GMV annually
- 100K+ QPS peak

---

### 8.2 Christie's (Luxury Auctions)

**Inferred Stack**:
- **Backend**: .NET Core, Java
- **Database**: SQL Server, PostgreSQL
- **Real-time**: SignalR (.NET WebSocket library)
- **Payment**: Custom integrations with banks
- **Infrastructure**: Azure
- **Security**: High compliance (art authentication)

**Scale**:
- ~350 auctions per year
- $7 billion annual sales
- Lower volume, higher value
- Focus on security & compliance

---

### 8.3 StockX (Sneaker/Streetwear Marketplace)

**Inferred Stack**:
- **Backend**: Node.js, Python
- **Database**: PostgreSQL, DynamoDB
- **Cache**: Redis
- **Search**: Algolia (SaaS)
- **Queue**: AWS SQS
- **Infrastructure**: AWS
- **Mobile**: React Native

**Scale**:
- 30+ million users
- $2 billion GMV (2021)
- Real-time price updates
- High mobile traffic

---

## 9. Cost Estimation (AWS)

**Monthly Infrastructure Cost (10M DAU, 10M auctions)**:

| **Service** | **Configuration** | **Monthly Cost** |
|-------------|-------------------|------------------|
| **EKS Cluster** | 3 node groups, ~50 instances | $7,500 |
| **RDS PostgreSQL** | db.r6g.4xlarge Multi-AZ + 3 replicas | $8,000 |
| **ElastiCache Redis** | cache.r6g.xlarge cluster (6 nodes) | $2,400 |
| **MSK (Kafka)** | 3 brokers (m5.xlarge) | $1,800 |
| **Elasticsearch** | 9 nodes (3 master + 6 data) | $3,500 |
| **S3** | 10 TB storage + transfer | $500 |
| **CloudFront** | 5 TB transfer | $425 |
| **ALB** | 2 load balancers | $50 |
| **Route 53** | Hosted zone + queries | $50 |
| **CloudWatch** | Metrics + logs | $500 |
| **Data Transfer** | Cross-AZ, internet | $1,000 |
| **Backups** | RDS snapshots, S3 | $300 |
| **Total** | | **~$25,000/month** |

**Cost Optimization Strategies**:
- Use Reserved Instances (save 40%)
- Spot instances for non-critical workloads
- S3 lifecycle policies (move old data to Glacier)
- Optimize Redis cache (evict cold data)
- Right-size instances based on metrics

---

## 10. Security Best Practices

### 10.1 Authentication & Authorization

**JWT Token Example**:
```javascript
const jwt = require('jsonwebtoken');

// Generate token
function generateToken(user) {
    return jwt.sign(
        {
            user_id: user.id,
            email: user.email,
            tier: user.tier
        },
        process.env.JWT_SECRET,
        {
            expiresIn: '1h',
            issuer: 'auction-api',
            audience: 'auction-client'
        }
    );
}

// Verify token middleware
function authenticateToken(req, res, next) {
    const authHeader = req.headers['authorization'];
    const token = authHeader && authHeader.split(' ')[1];

    if (!token) {
        return res.sendStatus(401);
    }

    jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
        if (err) return res.sendStatus(403);
        req.user = user;
        next();
    });
}

// Rate limiting by user tier
function rateLimitByTier(req, res, next) {
    const tier = req.user.tier;
    const limits = {
        'free': 10,      // 10 bids/hour
        'premium': 100,   // 100 bids/hour
        'enterprise': 1000 // 1000 bids/hour
    };

    const key = `rate_limit:${req.user.user_id}`;
    redis.incr(key, (err, count) => {
        if (count === 1) {
            redis.expire(key, 3600);  // 1 hour
        }

        if (count > limits[tier]) {
            return res.status(429).json({ error: 'Rate limit exceeded' });
        }

        next();
    });
}
```

### 10.2 Data Encryption

**At Rest**:
- RDS: Encryption enabled (AES-256)
- S3: Server-side encryption (SSE-S3 or SSE-KMS)
- EBS: Encrypted volumes

**In Transit**:
- TLS 1.3 for all external APIs
- mTLS for inter-service communication
- VPN/Private Link for database access

### 10.3 Secrets Management

**AWS Secrets Manager**:
```python
import boto3
from botocore.exceptions import ClientError

def get_secret(secret_name):
    session = boto3.session.Session()
    client = session.client(
        service_name='secretsmanager',
        region_name='us-east-1'
    )

    try:
        response = client.get_secret_value(SecretId=secret_name)
        secret = json.loads(response['SecretString'])
        return secret
    except ClientError as e:
        raise e

# Usage
db_credentials = get_secret('auction/production/database')
DATABASE_URL = f"postgresql://{db_credentials['username']}:{db_credentials['password']}@{db_credentials['host']}:5432/auction_db"
```

---

## Summary

This comprehensive technology stack provides:

✅ **High Performance**: Go for critical path (bid service)
✅ **Reliability**: PostgreSQL with ACID guarantees
✅ **Scalability**: Kubernetes, sharding, caching
✅ **Real-Time**: WebSocket, Kafka event streaming
✅ **Observability**: Prometheus, Grafana, Jaeger
✅ **Security**: JWT, encryption, secrets management
✅ **Cost-Effective**: ~$25K/month for 10M DAU

**Key Takeaways**:
1. Choose languages based on requirements (Go for speed, Java for features)
2. Use managed services (RDS, ElastiCache, MSK) to reduce ops burden
3. Implement robust monitoring from day 1
4. Security must be baked in, not bolted on
5. Cost optimization is ongoing, not one-time

This stack is proven at scale by eBay, Christie's, and similar auction platforms handling billions in GMV annually. 🚀
