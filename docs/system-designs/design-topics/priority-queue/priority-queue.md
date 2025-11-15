# Design a Distributed Priority Queue

**Companies**: Amazon (SQS), RabbitMQ, Google Cloud Tasks, Azure Service Bus
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a distributed priority queue system that processes messages based on priority levels. Higher priority messages should be processed before lower priority ones, with support for millions of messages per second, guaranteed delivery, and horizontal scalability.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Enqueue with Priority**: Add messages with priority levels (1-10, where 10 is highest)
2. **Dequeue by Priority**: Retrieve highest priority message available
3. **Message Visibility Timeout**: Prevent duplicate processing
4. **Dead Letter Queue (DLQ)**: Handle failed messages after N retries
5. **Delay Queue** (optional): Schedule messages for future delivery
6. **Message Acknowledgment**: Confirm successful processing
7. **Consumer Groups** (optional): Multiple consumers with load balancing

**Prioritize**: Focus on enqueue, dequeue, and acknowledgment for MVP

### Non-Functional Requirements

1. **High Throughput**: Handle 100K+ messages/sec
2. **Low Latency**: <50ms for enqueue, <100ms for dequeue
3. **High Availability**: 99.99% uptime
4. **At-Least-Once Delivery**: Messages delivered minimum once
5. **Ordering**: FIFO within same priority level
6. **Durability**: Messages persisted to disk
7. **Scalability**: Horizontal scaling for producers and consumers

### Capacity Estimation

**Assumptions**:
- 10M messages enqueued per hour
- 10M messages dequeued per hour
- Average message size: 10 KB
- Message retention: 7 days
- Average processing time: 5 seconds

**Message Rate**:
```
Enqueue: 10M/hour ÷ 3600 = ~2,800 msg/sec
Dequeue: 10M/hour ÷ 3600 = ~2,800 msg/sec
Peak: 2,800 × 3 = ~8,400 msg/sec
```

**Storage**:
```
Daily messages: 10M × 24 = 240M messages
Message size: 10 KB
Daily storage: 240M × 10 KB = 2.4 TB/day
7-day retention: 2.4 TB × 7 = 16.8 TB
```

**Memory (for in-flight messages)**:
```
Messages in-flight (assuming 5 sec processing):
8,400 msg/sec × 5 sec = 42,000 messages
Memory needed: 42K × 10 KB = 420 MB
Add buffer (10x): 4.2 GB
```

**Bandwidth**:
```
Enqueue: 2,800 msg/sec × 10 KB = 28 MB/sec
Dequeue: 2,800 msg/sec × 10 KB = 28 MB/sec
Total: 56 MB/sec (~450 Mbps)
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                  Producers                          │
│         (Microservices, Apps, Workers)              │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                 Load Balancer                       │
│                  (API Gateway)                      │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Queue Service 1 │  │  Queue Service 2 │
│   (Stateless)    │  │   (Stateless)    │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │  Coordination Layer │
         │  (Zookeeper/etcd)   │
         └──────────┬──────────┘
                    ▼
┌──────────────────────────────────────────┐
│         Priority Queue Storage            │
│  ┌──────────┐  ┌──────────┐             │
│  │ Redis    │  │PostgreSQL│             │
│  │(Hot Data)│  │(Durable) │             │
│  └──────────┘  └──────────┘             │
└──────────────────────┬───────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│                    Consumers                        │
│         (Workers, Processing Services)              │
└─────────────────────────────────────────────────────┘
```

### Components

**API Gateway / Load Balancer**:
- Route requests to queue service instances
- Rate limiting per client
- Authentication & authorization
- Health checks

**Queue Service (Stateless)**:
- Handle enqueue/dequeue operations
- Implement priority logic
- Manage visibility timeouts
- Route to storage layer
- Horizontally scalable

**Coordination Layer (Zookeeper/etcd)**:
- Leader election for partitions
- Service discovery
- Configuration management
- Distributed locks for critical sections

**Storage Layer**:
- **Redis**: Hot data, in-memory priority queue (sorted sets)
- **PostgreSQL**: Durable storage, message metadata
- **S3/Blob Storage**: Large message payloads

**Monitoring & Metrics**:
- Queue depth per priority
- Enqueue/dequeue rates
- Message age (oldest message waiting)
- DLQ size
- Consumer lag

---

## Step 3: Data Model (10 min)

### Database Choice

**Multi-Storage Strategy**:
- **Redis Sorted Sets**: Fast priority-based retrieval, in-memory
- **PostgreSQL**: Durable message storage, metadata queries
- **S3**: Large message bodies (>256 KB)

### Schema Design

**PostgreSQL Schema**:
```sql
-- Main message table
CREATE TABLE messages (
    message_id UUID PRIMARY KEY,
    queue_name VARCHAR(255) NOT NULL,
    priority SMALLINT NOT NULL CHECK (priority BETWEEN 1 AND 10),
    payload JSONB,
    payload_s3_key VARCHAR(512),  -- if payload > 256KB

    -- Metadata
    created_at TIMESTAMP DEFAULT NOW(),
    scheduled_at TIMESTAMP,  -- for delay queues
    visible_at TIMESTAMP DEFAULT NOW(),  -- visibility timeout

    -- Processing tracking
    receive_count INT DEFAULT 0,
    max_receive_count INT DEFAULT 5,
    status VARCHAR(20) DEFAULT 'READY',  -- READY, IN_FLIGHT, PROCESSED, DEAD_LETTER

    -- Consumer tracking
    consumer_id VARCHAR(255),
    last_received_at TIMESTAMP,

    -- Indexing
    partition_key VARCHAR(255),  -- for sharding
    version INT DEFAULT 1  -- for optimistic locking
);

-- Indexes for performance
CREATE INDEX idx_queue_priority_created ON messages(queue_name, priority DESC, created_at ASC)
    WHERE status = 'READY';

CREATE INDEX idx_visibility_timeout ON messages(visible_at)
    WHERE status = 'IN_FLIGHT';

CREATE INDEX idx_scheduled_messages ON messages(scheduled_at)
    WHERE status = 'READY' AND scheduled_at > NOW();

-- Partitioning by queue_name for scale
CREATE TABLE messages_high_priority PARTITION OF messages
    FOR VALUES FROM (8) TO (11);

CREATE TABLE messages_medium_priority PARTITION OF messages
    FOR VALUES FROM (4) TO (8);

CREATE TABLE messages_low_priority PARTITION OF messages
    FOR VALUES FROM (1) TO (4);

-- Dead Letter Queue table
CREATE TABLE dead_letter_queue (
    message_id UUID PRIMARY KEY,
    original_queue VARCHAR(255),
    payload JSONB,
    failure_reason TEXT,
    receive_count INT,
    created_at TIMESTAMP DEFAULT NOW(),
    FOREIGN KEY (message_id) REFERENCES messages(message_id)
);

-- Queue metadata
CREATE TABLE queue_metadata (
    queue_name VARCHAR(255) PRIMARY KEY,
    max_message_size_kb INT DEFAULT 256,
    visibility_timeout_sec INT DEFAULT 30,
    message_retention_sec INT DEFAULT 604800,  -- 7 days
    delay_seconds INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW()
);
```

**Redis Data Structures**:
```python
# Priority queue using sorted sets (ZSET)
# Key: "queue:{queue_name}:priority:{priority_level}"
# Score: timestamp (for FIFO within priority)
# Member: message_id

ZADD queue:orders:priority:10 1699900000.123 "msg-uuid-1"
ZADD queue:orders:priority:10 1699900000.456 "msg-uuid-2"
ZADD queue:orders:priority:5 1699900001.789 "msg-uuid-3"

# In-flight messages (hash with expiration)
HSET inflight:msg-uuid-1 consumer_id "consumer-123"
HSET inflight:msg-uuid-1 visible_at "1699900030"
EXPIRE inflight:msg-uuid-1 60

# Queue metadata counters
HINCRBY queue:orders:stats total_enqueued 1
HINCRBY queue:orders:stats total_dequeued 1
HINCRBY queue:orders:stats:priority:10 count 1
```

---

## Step 4: APIs (5 min)

### Enqueue Message

```http
POST /queues/{queue_name}/messages
Content-Type: application/json
Authorization: Bearer {token}

Request:
{
  "priority": 8,
  "payload": {
    "order_id": "ORD-12345",
    "customer_id": "CUST-98765",
    "items": [...],
    "total": 299.99
  },
  "delay_seconds": 0,
  "attributes": {
    "contentType": "application/json",
    "correlationId": "corr-xyz-789"
  }
}

Response: 201 Created
{
  "message_id": "550e8400-e29b-41d4-a716-446655440000",
  "queue_name": "orders",
  "priority": 8,
  "enqueued_at": "2025-11-15T10:30:00Z",
  "visible_at": "2025-11-15T10:30:00Z"
}
```

### Dequeue Message

```http
POST /queues/{queue_name}/dequeue
Content-Type: application/json
Authorization: Bearer {token}

Request:
{
  "max_messages": 1,
  "visibility_timeout": 30,
  "consumer_id": "worker-node-5"
}

Response: 200 OK
{
  "messages": [
    {
      "message_id": "550e8400-e29b-41d4-a716-446655440000",
      "priority": 8,
      "payload": {...},
      "receipt_handle": "AQEBzJK...xN4Eg==",  // for ack
      "enqueued_at": "2025-11-15T10:30:00Z",
      "receive_count": 1,
      "visible_until": "2025-11-15T10:31:00Z"
    }
  ]
}
```

### Acknowledge Message (Delete)

```http
DELETE /queues/{queue_name}/messages/{message_id}
Content-Type: application/json
Authorization: Bearer {token}

Request:
{
  "receipt_handle": "AQEBzJK...xN4Eg=="
}

Response: 204 No Content
```

### Change Visibility Timeout

```http
PATCH /queues/{queue_name}/messages/{message_id}/visibility
Content-Type: application/json

Request:
{
  "receipt_handle": "AQEBzJK...xN4Eg==",
  "visibility_timeout": 60
}

Response: 200 OK
```

### Get Queue Stats

```http
GET /queues/{queue_name}/stats

Response: 200 OK
{
  "queue_name": "orders",
  "approximate_message_count": 15432,
  "messages_by_priority": {
    "10": 245,
    "9": 1203,
    "8": 5678,
    "7": 3421,
    "6-1": 4885
  },
  "oldest_message_age_seconds": 127,
  "in_flight_count": 234,
  "dlq_count": 12
}
```

---

## Step 5: Core Algorithms

### Enqueue Algorithm

```python
def enqueue(queue_name, message, priority):
    """
    Enqueue message with priority
    Time Complexity: O(log N) for Redis ZADD
    """
    # 1. Validate message
    validate_message(message, priority)

    # 2. Generate unique message ID
    message_id = generate_uuid()
    timestamp = time.time()

    # 3. Store in PostgreSQL (durable storage)
    with db.transaction():
        db.execute("""
            INSERT INTO messages (
                message_id, queue_name, priority, payload,
                created_at, visible_at, status
            ) VALUES (?, ?, ?, ?, NOW(), NOW(), 'READY')
        """, [message_id, queue_name, priority, message])

    # 4. Add to Redis sorted set (fast retrieval)
    # Score = timestamp for FIFO within priority
    redis.zadd(
        f"queue:{queue_name}:priority:{priority}",
        {message_id: timestamp}
    )

    # 5. Update queue stats
    redis.hincrby(f"queue:{queue_name}:stats", "total_enqueued", 1)
    redis.hincrby(f"queue:{queue_name}:stats:priority:{priority}", "count", 1)

    # 6. Publish metrics
    metrics.increment("messages.enqueued", tags={
        "queue": queue_name,
        "priority": priority
    })

    return message_id

# Optimistic approach for high throughput
def enqueue_batch(queue_name, messages):
    """
    Batch enqueue for higher throughput
    """
    with db.transaction():
        # Bulk insert to PostgreSQL
        db.bulk_insert("messages", messages)

    # Bulk add to Redis (pipeline)
    pipeline = redis.pipeline()
    for msg in messages:
        pipeline.zadd(
            f"queue:{queue_name}:priority:{msg.priority}",
            {msg.id: msg.timestamp}
        )
    pipeline.execute()
```

### Dequeue Algorithm (Priority-Based)

```python
def dequeue(queue_name, consumer_id, visibility_timeout=30):
    """
    Dequeue highest priority message
    Time Complexity: O(log N) for Redis operations
    """
    # Priority levels from high to low
    priorities = [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]

    for priority in priorities:
        # 1. Get oldest message from this priority level (FIFO)
        redis_key = f"queue:{queue_name}:priority:{priority}"

        # Atomic operation: get and remove from sorted set
        message_ids = redis.zrange(redis_key, 0, 0)  # Get oldest

        if not message_ids:
            continue  # Try next priority level

        message_id = message_ids[0]

        # 2. Remove from sorted set (claim the message)
        removed = redis.zrem(redis_key, message_id)

        if removed == 0:
            # Someone else claimed it, try again
            continue

        # 3. Update PostgreSQL (mark as IN_FLIGHT)
        visible_until = datetime.now() + timedelta(seconds=visibility_timeout)

        result = db.execute("""
            UPDATE messages
            SET status = 'IN_FLIGHT',
                visible_at = ?,
                consumer_id = ?,
                receive_count = receive_count + 1,
                last_received_at = NOW()
            WHERE message_id = ? AND status = 'READY'
            RETURNING *
        """, [visible_until, consumer_id, message_id])

        if not result:
            # Message was already processed, try again
            continue

        message = result[0]

        # 4. Check if exceeded max receive count
        if message.receive_count > message.max_receive_count:
            move_to_dlq(message)
            continue

        # 5. Add to in-flight tracker in Redis
        redis.hset(f"inflight:{message_id}", mapping={
            "consumer_id": consumer_id,
            "visible_until": visible_until.isoformat()
        })
        redis.expire(f"inflight:{message_id}", visibility_timeout + 10)

        # 6. Update stats
        redis.hincrby(f"queue:{queue_name}:stats", "total_dequeued", 1)
        redis.hincrby(f"queue:{queue_name}:stats:priority:{priority}", "count", -1)

        # 7. Generate receipt handle (for ack)
        receipt_handle = generate_receipt_handle(message_id, consumer_id)

        return {
            "message_id": message_id,
            "priority": priority,
            "payload": message.payload,
            "receipt_handle": receipt_handle,
            "receive_count": message.receive_count
        }

    # No messages available
    return None
```

### Acknowledge (Delete) Algorithm

```python
def acknowledge(queue_name, message_id, receipt_handle):
    """
    Acknowledge successful processing and delete message
    """
    # 1. Validate receipt handle
    if not validate_receipt_handle(receipt_handle, message_id):
        raise InvalidReceiptHandleError()

    # 2. Delete from PostgreSQL
    deleted = db.execute("""
        DELETE FROM messages
        WHERE message_id = ? AND status = 'IN_FLIGHT'
        RETURNING *
    """, [message_id])

    if not deleted:
        raise MessageNotFoundError()

    # 3. Remove from in-flight tracker
    redis.delete(f"inflight:{message_id}")

    # 4. Update stats
    redis.hincrby(f"queue:{queue_name}:stats", "total_processed", 1)

    return True
```

### Visibility Timeout Expiration Handler

```python
def handle_visibility_timeouts():
    """
    Background job: return expired in-flight messages to queue
    Runs every 5 seconds
    """
    now = datetime.now()

    # Find messages with expired visibility timeout
    expired_messages = db.execute("""
        SELECT message_id, queue_name, priority
        FROM messages
        WHERE status = 'IN_FLIGHT'
          AND visible_at <= ?
        LIMIT 1000
    """, [now])

    for msg in expired_messages:
        # 1. Update status back to READY
        db.execute("""
            UPDATE messages
            SET status = 'READY',
                visible_at = NOW(),
                consumer_id = NULL
            WHERE message_id = ?
        """, [msg.message_id])

        # 2. Re-add to Redis sorted set
        timestamp = time.time()
        redis.zadd(
            f"queue:{msg.queue_name}:priority:{msg.priority}",
            {msg.message_id: timestamp}
        )

        # 3. Remove from in-flight tracker
        redis.delete(f"inflight:{msg.message_id}")

        # 4. Update stats
        redis.hincrby(f"queue:{msg.queue_name}:stats", "visibility_timeouts", 1)
```

---

## Step 6: Optimizations & Trade-offs

### 1. Sharding Strategy

**Horizontal Partitioning by Queue Name**:
```python
# Hash-based sharding
def get_shard_id(queue_name, num_shards=16):
    return hash(queue_name) % num_shards

# Each shard has its own:
# - PostgreSQL instance (or schema)
# - Redis instance
# - Queue service instances
```

**Benefits**:
- Linear scalability
- Isolated failure domains
- Independent scaling per queue

### 2. Priority Queue Optimization

**Approach 1: Separate Redis Sorted Sets per Priority**
```python
# Current approach - separate sorted set per priority level
# Pros: Simple, FIFO within priority guaranteed
# Cons: Need to check multiple keys for dequeue

# Optimization: Maintain bitmap of non-empty priorities
redis.setbit(f"queue:{queue_name}:priority_bitmap", priority, 1)

def dequeue_optimized(queue_name):
    # Get all non-empty priorities (O(1) for bitmap)
    priorities = redis.bitfield(f"queue:{queue_name}:priority_bitmap", "GET", "u10", 0)

    # Iterate only non-empty priorities
    for priority in get_set_bits(priorities):
        # ... dequeue logic
```

**Approach 2: Single Sorted Set with Composite Score**
```python
# Score = (priority * 10^15) + timestamp
# Ensures higher priority always wins, FIFO within priority

def enqueue_composite(queue_name, message_id, priority):
    score = (priority * 1e15) + time.time()
    redis.zadd(f"queue:{queue_name}", {message_id: score})

def dequeue_composite(queue_name):
    # Get highest score (highest priority, oldest timestamp)
    messages = redis.zrevrange(f"queue:{queue_name}", 0, 0)
    # ... process
```

**Pros**: Single key, simpler dequeue
**Cons**: Score precision limits (float64)

### 3. Long Polling for Dequeue

```python
def dequeue_with_long_poll(queue_name, wait_time_seconds=20):
    """
    Long polling to reduce empty responses
    """
    start_time = time.time()

    while True:
        message = dequeue(queue_name)

        if message:
            return message

        # Check if wait time exceeded
        elapsed = time.time() - start_time
        if elapsed >= wait_time_seconds:
            return None  # Timeout

        # Wait before retry (exponential backoff)
        sleep_time = min(0.1 * (2 ** min(5, elapsed)), 1)
        time.sleep(sleep_time)
```

**Benefits**:
- Reduces API calls (cost savings)
- Lower latency (consumer immediately gets message)
- Better resource utilization

### 4. Message Batching

**Batch Dequeue**:
```python
def dequeue_batch(queue_name, max_messages=10):
    """
    Dequeue multiple messages at once
    """
    messages = []

    for priority in [10, 9, 8, 7, 6, 5, 4, 3, 2, 1]:
        redis_key = f"queue:{queue_name}:priority:{priority}"

        # Get multiple messages from this priority
        needed = max_messages - len(messages)
        message_ids = redis.zrange(redis_key, 0, needed - 1)

        # Atomic removal
        if message_ids:
            redis.zrem(redis_key, *message_ids)

        # Process each message
        for msg_id in message_ids:
            # ... mark as IN_FLIGHT, etc.
            messages.append(msg_id)

        if len(messages) >= max_messages:
            break

    return messages
```

### 5. Dead Letter Queue Handling

```python
def move_to_dlq(message):
    """
    Move failed message to DLQ for manual inspection
    """
    with db.transaction():
        # Insert to DLQ table
        db.execute("""
            INSERT INTO dead_letter_queue (
                message_id, original_queue, payload,
                failure_reason, receive_count
            ) VALUES (?, ?, ?, ?, ?)
        """, [
            message.message_id,
            message.queue_name,
            message.payload,
            "Exceeded max receive count",
            message.receive_count
        ])

        # Update original message status
        db.execute("""
            UPDATE messages
            SET status = 'DEAD_LETTER'
            WHERE message_id = ?
        """, [message.message_id])

    # Alert operations team
    alerts.send_slack(f"Message {message.message_id} moved to DLQ")
```

### 6. Delay Queue Implementation

```python
def enqueue_with_delay(queue_name, message, priority, delay_seconds):
    """
    Schedule message for future delivery
    """
    scheduled_at = datetime.now() + timedelta(seconds=delay_seconds)

    # Store in PostgreSQL with scheduled_at
    db.execute("""
        INSERT INTO messages (
            message_id, queue_name, priority, payload,
            scheduled_at, status
        ) VALUES (?, ?, ?, ?, ?, 'SCHEDULED')
    """, [message_id, queue_name, priority, message, scheduled_at])

    # Add to Redis sorted set for scheduled messages
    redis.zadd(
        f"queue:{queue_name}:scheduled",
        {message_id: scheduled_at.timestamp()}
    )

# Background job to process scheduled messages
def process_scheduled_messages():
    """
    Runs every 1 second
    """
    now = time.time()

    for queue in get_all_queues():
        # Get messages ready to be delivered
        ready_messages = redis.zrangebyscore(
            f"queue:{queue}:scheduled",
            0,
            now
        )

        for msg_id in ready_messages:
            # Move to active queue
            msg = db.get_message(msg_id)

            # Update status to READY
            db.execute("""
                UPDATE messages
                SET status = 'READY', visible_at = NOW()
                WHERE message_id = ?
            """, [msg_id])

            # Add to priority queue
            redis.zadd(
                f"queue:{queue}:priority:{msg.priority}",
                {msg_id: time.time()}
            )

            # Remove from scheduled
            redis.zrem(f"queue:{queue}:scheduled", msg_id)
```

---

## Step 7: Advanced Considerations

### Security

**1. Authentication & Authorization**:
```python
# OAuth2 / API Key based auth
@require_auth
@require_permission("queue:write")
def enqueue(queue_name, message):
    # Only authorized clients can write to queue
    ...

# Per-queue access control
if not check_queue_acl(user_id, queue_name, "WRITE"):
    raise PermissionDeniedError()
```

**2. Message Encryption**:
```python
# Encrypt sensitive payloads
from cryptography.fernet import Fernet

def enqueue_encrypted(queue_name, message, encryption_key):
    cipher = Fernet(encryption_key)
    encrypted_payload = cipher.encrypt(json.dumps(message).encode())

    enqueue(queue_name, {
        "encrypted": True,
        "payload": encrypted_payload
    })
```

**3. Rate Limiting**:
```python
# Token bucket algorithm per client
@rate_limit(max_requests=1000, window=60)  # 1000/min
def enqueue(queue_name, message):
    ...
```

### Monitoring & Observability

**Key Metrics**:
```python
# Enqueue/Dequeue rates
metrics.gauge("queue.depth", current_message_count, tags={"queue": queue_name})
metrics.histogram("message.age", oldest_message_age_seconds)
metrics.increment("messages.enqueued", tags={"priority": priority})
metrics.increment("messages.dequeued")
metrics.increment("messages.dlq_moved")

# Consumer metrics
metrics.gauge("consumers.active", active_consumer_count)
metrics.histogram("consumer.processing_time", processing_duration_ms)

# System health
metrics.gauge("queue.lag", messages_waiting)
metrics.gauge("redis.memory_usage", redis_memory_mb)
metrics.gauge("postgres.connections", db_connection_count)
```

**Alerts**:
- Queue depth > 10,000 messages
- Oldest message age > 5 minutes
- DLQ growth > 100 messages/hour
- Consumer processing time > 30 seconds (P95)
- No consumers connected for > 1 minute

### High Availability

**Multi-Region Deployment**:
```
Region 1 (Primary):
  - Queue Service Cluster (3 nodes)
  - PostgreSQL Primary + 2 Replicas
  - Redis Cluster (6 nodes)

Region 2 (Secondary):
  - Queue Service Cluster (3 nodes)
  - PostgreSQL Replica (read-only)
  - Redis Cluster (6 nodes)

Cross-region replication:
  - PostgreSQL: Streaming replication
  - Redis: CRDT-based replication or master-slave
```

**Failover Strategy**:
1. Health check failures → Route to healthy nodes
2. Region failure → DNS failover to secondary region (5 min RTO)
3. Database failover → Promote replica (30 sec RTO)

### Exactly-Once Delivery (Advanced)

```python
# Idempotency using deduplication
def enqueue_idempotent(queue_name, message, idempotency_key):
    """
    Prevent duplicate messages using idempotency key
    """
    # Check if message already enqueued
    existing = redis.get(f"idempotency:{idempotency_key}")

    if existing:
        return json.loads(existing)  # Return existing message_id

    # Enqueue message
    message_id = enqueue(queue_name, message)

    # Store idempotency key (expire after 24 hours)
    redis.setex(
        f"idempotency:{idempotency_key}",
        86400,  # 24 hours
        json.dumps({"message_id": message_id})
    )

    return message_id

# Consumer-side idempotency
def process_message_idempotent(message):
    """
    Track processed messages to avoid duplicate processing
    """
    message_id = message["message_id"]

    # Check if already processed
    if redis.exists(f"processed:{message_id}"):
        return  # Already processed, skip

    # Process message
    result = process(message)

    # Mark as processed (with expiration)
    redis.setex(f"processed:{message_id}", 7 * 86400, "1")  # 7 days

    # Acknowledge
    acknowledge(message["queue_name"], message_id, message["receipt_handle"])
```

---

## Complete System Design Diagram

```
                          ┌──────────────────────┐
                          │  Producers (Apps)    │
                          │  - Microservices     │
                          │  - Background Jobs   │
                          │  - API Gateways      │
                          └──────────┬───────────┘
                                     │
                          ┌──────────▼───────────┐
                          │   API Gateway / LB   │
                          │  - Auth & Rate Limit │
                          │  - SSL Termination   │
                          └──────────┬───────────┘
                                     │
                   ┌─────────────────┼─────────────────┐
                   │                 │                 │
           ┌───────▼──────┐  ┌──────▼──────┐  ┌──────▼──────┐
           │Queue Service │  │Queue Service│  │Queue Service│
           │   Node 1     │  │   Node 2    │  │   Node 3    │
           │  (Stateless) │  │  (Stateless)│  │  (Stateless)│
           └───────┬──────┘  └──────┬──────┘  └──────┬──────┘
                   │                │                 │
                   └────────────────┼─────────────────┘
                                    │
                   ┌────────────────┼────────────────┐
                   │                │                │
         ┌─────────▼─────┐  ┌──────▼──────┐  ┌─────▼────────┐
         │   Zookeeper   │  │ Redis Cluster│  │  PostgreSQL  │
         │     Cluster   │  │ (Sorted Sets)│  │   Primary    │
         │ - Coordination│  │ - Hot Data   │  │  + Replicas  │
         │ - Leader      │  │ - In-flight  │  │ - Durable    │
         │   Election    │  │   Messages   │  │   Storage    │
         └───────────────┘  └──────┬──────┘  └──────┬────────┘
                                    │                │
                                    └────────┬───────┘
                                             │
                              ┌──────────────▼───────────────┐
                              │    Background Workers        │
                              │ - Visibility Timeout Handler │
                              │ - Scheduled Message Processor│
                              │ - DLQ Monitor                │
                              │ - Metrics Aggregator         │
                              └──────────────┬───────────────┘
                                             │
                          ┌──────────────────▼──────────────────┐
                          │         Consumers (Workers)         │
                          │  - Message Processors               │
                          │  - Business Logic Executors         │
                          │  - Auto-scaling Worker Pools        │
                          └─────────────────────────────────────┘
```

---

## Tech Stack (Industry Standard)

### Message Broker Options

| **Technology** | **Pros** | **Cons** | **Best For** |
|----------------|----------|----------|--------------|
| **RabbitMQ** | Native priority queues, rich features, AMQP protocol | Single point of failure without clustering | Enterprise messaging, complex routing |
| **Amazon SQS** | Fully managed, infinite scale, native AWS integration | No strict ordering, higher latency | AWS-native applications, serverless |
| **Apache Kafka** | High throughput, durable, distributed | Complex setup, not designed for priority | Event streaming, log aggregation |
| **Redis Streams** | Fast, simple, good for small-medium scale | Limited durability, memory-bound | Real-time processing, caching layer |
| **Azure Service Bus** | Enterprise features, .NET integration | Cost, vendor lock-in | Microsoft ecosystem apps |
| **Google Cloud Tasks** | Managed, HTTP-based, good for scheduled tasks | Limited throughput vs Kafka | Task scheduling, background jobs |

### Storage Layer

**Redis** (In-memory hot data):
```yaml
Configuration:
  - Cluster mode: 6 nodes (3 master, 3 replica)
  - Persistence: RDB + AOF
  - Memory: 64 GB per node
  - Eviction: allkeys-lru
  - Data structures: Sorted Sets (ZSET), Hashes, Bitmaps
```

**PostgreSQL** (Durable storage):
```yaml
Configuration:
  - Version: 14+
  - Replication: Streaming (1 primary, 2 replicas)
  - Partitioning: Range partitioning by priority
  - Connection pooling: PgBouncer (max 1000 connections)
  - Extensions: pg_stat_statements, pg_trgm
```

**Object Storage** (Large payloads):
- **S3**: Scalable, durable, 99.999999999% durability
- **Azure Blob**: Good for Azure ecosystem
- **Google Cloud Storage**: Multi-region, strong consistency

### Coordination Layer

**Zookeeper**:
- Leader election for queue partitions
- Service discovery
- Configuration management
- Distributed locks

**etcd** (Alternative):
- Kubernetes-native
- gRPC-based API
- Watch mechanism for configuration changes

### Infrastructure & DevOps

**Container Orchestration**:
```yaml
Kubernetes:
  Deployment:
    - Queue Service: 10 replicas (HPA: 5-50 based on CPU/Queue depth)
    - Background Workers: 5 replicas
  Services:
    - LoadBalancer for external access
    - ClusterIP for internal communication
  ConfigMaps:
    - Queue configurations
    - Feature flags
  Secrets:
    - Database credentials
    - Redis passwords
    - Encryption keys
```

**Infrastructure as Code**:
```hcl
# Terraform example
module "priority_queue" {
  source = "./modules/priority-queue"

  redis_cluster_nodes = 6
  postgres_instance_type = "db.r5.2xlarge"
  queue_service_instances = 10

  vpc_id = var.vpc_id
  availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
}
```

**CI/CD Pipeline**:
```yaml
# GitHub Actions / GitLab CI
stages:
  - test
  - build
  - deploy

test:
  script:
    - pytest tests/
    - coverage report --fail-under=80

build:
  script:
    - docker build -t queue-service:$CI_COMMIT_SHA .
    - docker push ecr.amazonaws.com/queue-service:$CI_COMMIT_SHA

deploy:
  script:
    - kubectl set image deployment/queue-service queue-service=ecr.amazonaws.com/queue-service:$CI_COMMIT_SHA
    - kubectl rollout status deployment/queue-service
```

### Monitoring Stack

**Prometheus + Grafana**:
```yaml
Metrics Collection:
  - Service-level metrics (enqueue/dequeue rates, latency)
  - Infrastructure metrics (CPU, memory, disk I/O)
  - Business metrics (queue depth, DLQ size, message age)

Dashboards:
  - Queue Health Dashboard
  - Consumer Performance Dashboard
  - System Resource Dashboard
  - SLA Compliance Dashboard

Alert Rules:
  - Queue depth > 10,000
  - Message age > 300 seconds
  - Consumer lag > 1000 messages
  - Error rate > 1%
```

**Logging: ELK Stack**:
- **Elasticsearch**: Log storage and search
- **Logstash**: Log processing pipeline
- **Kibana**: Visualization and exploration
- **Filebeat**: Log shipping from containers

**Distributed Tracing**:
- **Jaeger**: Request tracing across services
- **OpenTelemetry**: Unified observability framework

---

## Interview Tips

**Questions to Ask**:
- Expected message volume and peak traffic?
- Priority levels needed (strict vs weighted)?
- Durability requirements (at-least-once vs exactly-once)?
- Message retention period?
- Consumer patterns (single vs multiple consumer groups)?

**Topics to Cover**:
- Priority queue data structure choice (sorted set, heap, B-tree)
- Distributed consensus for ordering (Zookeeper, Raft)
- Message durability vs performance trade-off
- Visibility timeout mechanism
- Dead letter queue handling
- Horizontal scaling strategy

**Common Follow-ups**:
- "How would you handle 1M messages/sec?"
  → Partition by queue name, use Redis cluster, shard PostgreSQL
- "How do you prevent message loss?"
  → Sync replication to PostgreSQL, WAL, message acknowledgments
- "How do you ensure fair processing across priorities?"
  → Weighted priority, quota-based dequeue, priority boost for starvation
- "What happens if Redis crashes?"
  → Rebuild from PostgreSQL, use Redis persistence (AOF), consumer reprocessing

---

## Key Takeaways

1. **Hybrid Storage**: Redis for speed, PostgreSQL for durability
2. **Priority Ordering**: Sorted sets with composite scores (priority + timestamp)
3. **Visibility Timeout**: Prevent duplicate processing, enable retries
4. **Dead Letter Queue**: Handle poison messages gracefully
5. **Monitoring is Critical**: Queue depth, message age, consumer lag
6. **Horizontal Scaling**: Partition by queue name, stateless services
7. **Trade-offs**: Throughput vs latency, durability vs speed, complexity vs features

## Further Reading

- **RabbitMQ Priority Queues**: https://www.rabbitmq.com/priority.html
- **AWS SQS Best Practices**: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-best-practices.html
- **Redis Sorted Sets**: https://redis.io/topics/data-types#sorted-sets
- **Designing Data-Intensive Applications** (Martin Kleppmann) - Chapter on Message Queues
- **Building Microservices** (Sam Newman) - Inter-service Communication patterns
