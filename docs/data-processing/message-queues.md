# Message Queues: Apache Kafka and RabbitMQ

> Deep dive into message queuing systems, with focus on Apache Kafka's log-based architecture and comparison with RabbitMQ

## Table of Contents
- [Overview](#overview)
- [Apache Kafka](#apache-kafka)
- [Kafka Architecture](#kafka-architecture)
- [Kafka for Logs](#kafka-for-logs)
- [RabbitMQ](#rabbitmq)
- [Kafka vs RabbitMQ](#kafka-vs-rabbitmq)
- [Use Cases](#use-cases)
- [Interview Tips](#interview-tips)

## Overview

Message queues enable asynchronous communication between services, decoupling producers from consumers.

### Why Message Queues?

**Without Message Queue:**
```
Service A ──→ Service B (if B is down, A fails)
              Service C (if C is slow, A waits)
```

**With Message Queue:**
```
Service A ──→ [Queue] ──→ Service B (B can be down, messages queued)
                     └──→ Service C (C processes at its own pace)
```

### Key Benefits
- **Decoupling**: Producers and consumers operate independently
- **Scalability**: Add consumers to handle more load
- **Reliability**: Messages persist until processed
- **Load Leveling**: Absorb traffic spikes
- **Ordering**: Guarantee message order (when needed)

## Apache Kafka

Apache Kafka is a distributed streaming platform designed as a distributed commit log.

### Core Philosophy

**Traditional Message Queue** (RabbitMQ):
```
Producer → Queue → Consumer (message deleted)
```

**Kafka** (Distributed Log):
```
Producer → Log (append-only) → Consumers read at their own offsets
           [0][1][2][3][4][5][6][7]...
```

Key insight: Treat messages as an immutable, append-only log. Consumers track their position (offset).

## Kafka Architecture

### Topics and Partitions

**Topic**: Logical channel for messages (e.g., "user-events", "payments")

**Partition**: Physical division of a topic for parallelism

```
Topic: "user-events"

Partition 0: [msg0][msg3][msg6][msg9]  ─┐
Partition 1: [msg1][msg4][msg7][msg10] ─┤─ Distributed across brokers
Partition 2: [msg2][msg5][msg8][msg11] ─┘

Messages with same key → same partition (ordering guarantee)
```

### Kafka Cluster

```
┌──────────────────────────────────────────────────────┐
│                    ZooKeeper                         │
│            (Cluster Coordination)                     │
│     - Leader election                                │
│     - Configuration management                        │
│     - Consumer group coordination (old API)           │
└─────────────────┬────────────────────────────────────┘
                  │
      ┌───────────┼───────────┐
      │           │           │
┌─────▼────┐ ┌────▼────┐ ┌───▼─────┐
│ Broker 1 │ │Broker 2 │ │Broker 3 │
│          │ │         │ │         │
│ P0(L)    │ │ P0(F)   │ │ P1(L)   │  L = Leader
│ P1(F)    │ │ P2(L)   │ │ P1(F)   │  F = Follower
│ P2(F)    │ │ P2(F)   │ │ P0(F)   │
└──────────┘ └─────────┘ └─────────┘
```

**Note**: Modern Kafka (KRaft) removes ZooKeeper dependency.

### Replication

Each partition has replicas for fault tolerance:

```
Topic: payments, Partition: 0, Replication Factor: 3

Broker 1 (Leader):   [msg1][msg2][msg3][msg4] ← writes go here
Broker 2 (Follower): [msg1][msg2][msg3][msg4] ← replicated
Broker 3 (Follower): [msg1][msg2][msg3][msg4] ← replicated

If Broker 1 fails → Broker 2 or 3 becomes leader
```

**In-Sync Replicas (ISR):**
- Replicas that are caught up with the leader
- Only ISR members can become leader
- Configurable with `min.insync.replicas`

```java
// Producer config for durability
props.put("acks", "all");  // Wait for all ISR to acknowledge
props.put("min.insync.replicas", "2");  // At least 2 replicas
```

### Producer API

```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka1:9092,kafka2:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("acks", "all");  // Durability
props.put("retries", 3);
props.put("compression.type", "snappy");

KafkaProducer<String, String> producer = new KafkaProducer<>(props);

// Send message
ProducerRecord<String, String> record =
    new ProducerRecord<>("user-events", userId, event);

// Async send
producer.send(record, (metadata, exception) -> {
    if (exception != null) {
        log.error("Failed to send", exception);
    } else {
        log.info("Sent to partition {} at offset {}",
                 metadata.partition(), metadata.offset());
    }
});

// Sync send (wait for acknowledgment)
RecordMetadata metadata = producer.send(record).get();
```

**Partitioning Strategies:**

```java
// 1. Key-based (same key → same partition)
new ProducerRecord<>("topic", key, value);

// 2. Round-robin (key is null)
new ProducerRecord<>("topic", value);

// 3. Custom partitioner
class UserPartitioner implements Partitioner {
    public int partition(String topic, Object key, byte[] keyBytes,
                        Object value, byte[] valueBytes,
                        Cluster cluster) {
        // VIP users to partition 0, others distributed
        if (isVIP(key)) return 0;
        return Utils.toPositive(Utils.murmur2(keyBytes)) %
               (cluster.partitionCountForTopic(topic) - 1) + 1;
    }
}
```

### Consumer API

#### Consumer Groups

Multiple consumers can work together to consume a topic:

```
Topic with 4 partitions:
[P0] [P1] [P2] [P3]

Consumer Group "analytics":
┌──────────┐  ┌──────────┐
│Consumer A│  │Consumer B│
│   P0, P1 │  │   P2, P3 │
└──────────┘  └──────────┘

Each partition consumed by exactly one consumer in the group
```

**Multiple Consumer Groups:**
```
Topic: "user-events"
  [P0] [P1] [P2] [P3]
    │    │    │    │
    ├────┴────┴────┴──→ Group "analytics" (all messages)
    │
    └────────────────→ Group "notifications" (all messages)
```

```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka1:9092");
props.put("group.id", "analytics-group");
props.put("key.deserializer", StringDeserializer.class.getName());
props.put("value.deserializer", StringDeserializer.class.getName());
props.put("enable.auto.commit", "false");  // Manual offset management
props.put("auto.offset.reset", "earliest"); // Start from beginning if no offset

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("user-events"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

    for (ConsumerRecord<String, String> record : records) {
        processRecord(record);
    }

    // Manual commit after processing
    consumer.commitSync();
}
```

**Offset Management:**

```
Kafka stores offset per partition per consumer group:

Topic: events, Partition: 0
[0][1][2][3][4][5][6][7][8][9][10]
                    ▲
                    │
            Current Offset = 7
            (Consumer Group "analytics")

Next poll() returns messages starting at offset 7
```

```java
// Manual offset control
consumer.seek(new TopicPartition("events", 0), 100);  // Jump to offset 100

// Commit specific offset
Map<TopicPartition, OffsetAndMetadata> offsets = new HashMap<>();
offsets.put(
    new TopicPartition("events", 0),
    new OffsetAndMetadata(record.offset() + 1)
);
consumer.commitSync(offsets);
```

### Exactly-Once Semantics (EOS)

Kafka supports exactly-once processing with transactions:

```java
// Producer with transactions
props.put("transactional.id", "my-transactional-id");
producer.initTransactions();

try {
    producer.beginTransaction();

    // Send messages
    producer.send(record1);
    producer.send(record2);

    // Send offsets (for consume-process-produce pattern)
    producer.sendOffsetsToTransaction(offsets, consumerGroupId);

    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Idempotent Producer:**
```java
props.put("enable.idempotence", "true");
// Automatically sets: acks=all, retries=MAX, max.in.flight=5
```

This prevents duplicate messages even with retries:
```
Without idempotence:
Send(msg) → Network issue → Retry → DUPLICATE

With idempotence:
Send(msg, sequence=1) → Retry → Kafka deduplicates based on sequence
```

## Kafka for Logs

Kafka excels as a log aggregation system due to its append-only, distributed log architecture.

### Why Kafka for Logs?

1. **High Throughput**: Millions of messages/second
2. **Durability**: Configurable retention (days, weeks, forever)
3. **Scalability**: Horizontal scaling with partitions
4. **Multiple Consumers**: Different teams/systems can consume same logs
5. **Replay**: Reprocess logs from any point in time

### Log Aggregation Architecture

```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│  Service A  │     │  Service B  │     │  Service C  │
│   (1000)    │     │   (500)     │     │   (2000)    │
│  instances  │     │  instances  │     │  instances  │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
       │                   │                   │
       ▼                   ▼                   ▼
    ┌─────────────────────────────────────────────┐
    │              Kafka Topic: "logs"            │
    │  Partition 0: [Service A logs]              │
    │  Partition 1: [Service B logs]              │
    │  Partition 2: [Service C logs]              │
    │  Partition 3: [Mixed logs]                  │
    └─────────────────────────────────────────────┘
                         │
           ┌─────────────┼─────────────┐
           │             │             │
           ▼             ▼             ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │  Splunk  │  │   HDFS   │  │  Alerts  │
    │(Search)  │  │(Archive) │  │(Real-time)│
    └──────────┘  └──────────┘  └──────────┘
```

### Log Format Best Practices

```json
{
  "timestamp": "2025-01-15T10:30:00.123Z",
  "service": "payment-service",
  "instance": "payment-7f8d9c-abc12",
  "level": "ERROR",
  "message": "Payment failed",
  "trace_id": "abc123def456",
  "span_id": "def789",
  "user_id": "user_12345",
  "error": {
    "type": "PaymentGatewayTimeout",
    "message": "Gateway did not respond within 5s",
    "stack_trace": "..."
  },
  "metadata": {
    "payment_id": "pay_xyz789",
    "amount": 99.99,
    "currency": "USD"
  }
}
```

### Kafka Connect for Logs

Kafka Connect provides pre-built connectors:

```yaml
# File Source Connector (read log files)
name: file-source
connector.class: FileStreamSource
tasks.max: 3
file: /var/log/application.log
topic: logs

# Elasticsearch Sink Connector (index logs)
name: elasticsearch-sink
connector.class: ElasticsearchSinkConnector
topics: logs
connection.url: http://elasticsearch:9200
type.name: _doc
batch.size: 1000
```

### Log Retention Strategies

```properties
# Time-based retention (7 days)
log.retention.hours=168

# Size-based retention (100 GB per partition)
log.retention.bytes=107374182400

# Segment size (1 GB segments)
log.segment.bytes=1073741824

# Cleanup policy
log.cleanup.policy=delete  # or "compact" for deduplication
```

### Performance Optimizations for Logs

**1. Batching**
```java
props.put("linger.ms", "10");      // Wait 10ms to batch
props.put("batch.size", "32768");  // 32KB batches
```

**2. Compression**
```java
props.put("compression.type", "lz4");  // Fast compression for logs
```

**3. Partitioning Strategy**
```java
// Partition by service name for better consumer parallelism
new ProducerRecord<>("logs", serviceName, logMessage);
```

**Throughput Example:**
```
Single partition: ~50 MB/s
10 partitions: ~500 MB/s
100 partitions: ~5 GB/s (theoretical, depends on cluster)

For 10,000 log messages/second × 1KB each = 10 MB/s
→ 1-2 partitions sufficient
→ 3-4 partitions for headroom
```

## RabbitMQ

RabbitMQ is a traditional message broker implementing AMQP (Advanced Message Queuing Protocol).

### Architecture

```
┌──────────┐      ┌────────────────┐      ┌──────────┐
│ Producer │─────→│    Exchange    │─────→│  Queue   │─────→ Consumer
└──────────┘      └────────────────┘      └──────────┘
                   (Routing Logic)        (Message Store)
```

### Exchange Types

**1. Direct Exchange** (exact routing key match)
```
Producer → [Exchange] → Queue1 (routing key: "error")
                     → Queue2 (routing key: "warning")
                     → Queue3 (routing key: "info")

Message with key "error" → Queue1 only
```

**2. Topic Exchange** (pattern matching)
```
Routing patterns:
  "order.*.created" → Queue1
  "order.payment.*" → Queue2
  "*.*.failed"      → Queue3

Message "order.payment.created" → Queue1, Queue2
Message "order.payment.failed"  → Queue2, Queue3
```

**3. Fanout Exchange** (broadcast to all queues)
```
Producer → [Fanout] → Queue1
                   → Queue2
                   → Queue3

All queues receive all messages
```

**4. Headers Exchange** (match on message headers)
```java
Map<String, Object> headers = new HashMap<>();
headers.put("priority", "high");
headers.put("region", "us-west");

// Route based on header matching
```

### Message Acknowledgment

```java
// Auto-acknowledge (fast but risky)
channel.basicConsume(queueName, true, consumer);

// Manual acknowledge (safe)
channel.basicConsume(queueName, false, (consumerTag, delivery) -> {
    try {
        processMessage(delivery.getBody());
        channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
    } catch (Exception e) {
        // Negative acknowledge (requeue or dead-letter)
        channel.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
    }
});
```

### Message Durability

```java
// Durable queue
channel.queueDeclare(
    "tasks",
    true,      // durable
    false,     // exclusive
    false,     // auto-delete
    null       // arguments
);

// Persistent messages
channel.basicPublish(
    "",
    "tasks",
    MessageProperties.PERSISTENT_TEXT_PLAIN,
    message.getBytes()
);
```

### Dead Letter Exchanges

Handle failed messages:
```java
Map<String, Object> args = new HashMap<>();
args.put("x-dead-letter-exchange", "dlx");
args.put("x-message-ttl", 60000);  // 60 seconds
args.put("x-max-length", 10000);   // Max queue length

channel.queueDeclare("tasks", true, false, false, args);

// Failed messages go to DLX after TTL or max retries
```

### Priority Queues

```java
Map<String, Object> args = new HashMap<>();
args.put("x-max-priority", 10);
channel.queueDeclare("priority-tasks", true, false, false, args);

// Send with priority
AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
    .priority(5)
    .build();
channel.basicPublish("", "priority-tasks", properties, message.getBytes());
```

## Kafka vs RabbitMQ

### Detailed Comparison

```
┌─────────────────┬──────────────────────┬──────────────────────┐
│ Feature         │ Kafka                │ RabbitMQ             │
├─────────────────┼──────────────────────┼──────────────────────┤
│ Architecture    │ Distributed Log      │ Message Broker       │
│ Model           │ Pull                 │ Push                 │
│ Message Retain  │ Configurable (days)  │ Until consumed       │
│ Ordering        │ Per partition        │ Per queue            │
│ Throughput      │ Very High (100k+/s)  │ Moderate (10k-20k/s) │
│ Latency         │ Higher (ms-sec)      │ Lower (μs-ms)        │
│ Replay          │ Yes (any offset)     │ No                   │
│ Consumer Model  │ Consumer Groups      │ Competing Consumers  │
│ Routing         │ Topic/Partition      │ Exchange (flexible)  │
│ Protocol        │ Custom Binary        │ AMQP                 │
│ Use Case        │ Event Streaming      │ Task Queues          │
└─────────────────┴──────────────────────┴──────────────────────┘
```

### Architectural Differences

**Kafka: Pull-based**
```
Consumer pulls messages at its own pace
  ▲
  │ poll()
  │
[Kafka Partition]
  ↓
Consumer controls rate, can pause/resume
```

**RabbitMQ: Push-based**
```
[Queue] ──→ pushes messages to consumer
           (respects prefetch count)

Consumer receives messages as they arrive
```

### Message Consumption

**Kafka:**
```java
// Consumer reads multiple messages per poll
ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
// Process batch
for (ConsumerRecord<String, String> record : records) {
    process(record);
}
consumer.commitSync();  // Commit batch
```

**RabbitMQ:**
```java
// Consumer receives messages one at a time
channel.basicConsume(queueName, false, (consumerTag, delivery) -> {
    process(delivery.getBody());
    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
});
```

### Scaling Patterns

**Kafka Scaling:**
```
Add partitions to topic (at creation or later)
Add consumers (up to partition count)

10 partitions → 10 consumers max in a group
Need more parallelism? Add more partitions
```

**RabbitMQ Scaling:**
```
Add consumers to queue (unlimited)
Add queues with sharding plugin
Use clustering for HA

Queue can have many consumers
Cluster can have many queues
```

### When to Choose Kafka

1. **Event Streaming / Event Sourcing**
   - Need to replay events
   - Multiple consumers need same data
   - Audit trail required

2. **High Throughput Logs**
   - Millions of events/second
   - Long retention (days/weeks)
   - Multiple downstream systems

3. **Stream Processing**
   - Real-time analytics
   - ETL pipelines
   - Integration with Flink/Spark

4. **Message Replay**
   - Reprocess data from past
   - Multiple consumer groups
   - Time travel queries

**Example: E-commerce Order Events**
```
Order Created → Kafka
  ↓
  ├─→ Inventory Service (decrement stock)
  ├─→ Analytics (sales metrics)
  ├─→ Recommendation Engine (update models)
  └─→ Audit System (compliance)

Later: Add new ML model → replay last 30 days
```

### When to Choose RabbitMQ

1. **Task Queues**
   - Background jobs
   - Worker pools
   - Priority processing

2. **Complex Routing**
   - Topic-based routing
   - Header-based routing
   - Request-reply patterns

3. **Low Latency**
   - Sub-millisecond delivery
   - Real-time notifications
   - Immediate processing

4. **Traditional Messaging**
   - RPC patterns
   - Point-to-point
   - Publish-subscribe with routing

**Example: Background Jobs**
```
User uploads file → RabbitMQ Queue
                         ↓
                    Worker Pool
                    ┌────┬────┬────┐
                    │ W1 │ W2 │ W3 │
                    └────┴────┴────┘
                    Process one file each

Priority queue: Premium users first
```

### Hybrid Architectures

Many systems use both:

```
┌─────────────┐
│   Clients   │
└──────┬──────┘
       │
       ▼
  ┌─────────┐
  │  Kafka  │ ← Event streaming, high throughput
  └────┬────┘
       │
       ├─→ Stream Processing (Flink)
       │
       ├─→ Data Lake (long-term storage)
       │
       └─→ [RabbitMQ] ← Task distribution, complex routing
                ↓
           Worker Services
```

## Use Cases

### Kafka Use Cases

**1. Log Aggregation**
```java
// All microservices send logs to Kafka
logger.info("Payment processed",
    keyValue("payment_id", paymentId),
    keyValue("amount", amount));

// Kafka topic: "logs"
// Consumers: ELK Stack, Splunk, S3 Archive
```

**2. Event Sourcing**
```java
// Store all state changes as events
OrderCreatedEvent → Kafka
OrderPaidEvent → Kafka
OrderShippedEvent → Kafka

// Rebuild state by replaying events
currentState = events.stream()
    .reduce(initialState, (state, event) -> state.apply(event));
```

**3. CDC (Change Data Capture) with Debezium**
```
MySQL → Debezium → Kafka → Elasticsearch
                         → Data Warehouse
                         → Cache invalidation
```

**4. Metrics Collection**
```java
// High-volume metrics
metrics.counter("requests.total", tags).increment();
→ Kafka → Prometheus → Grafana
```

### RabbitMQ Use Cases

**1. Background Job Processing**
```java
// Submit job
channel.basicPublish("", "jobs", null, job.serialize());

// Workers process
channel.basicConsume("jobs", false, (tag, delivery) -> {
    Job job = deserialize(delivery.getBody());
    processJob(job);
    channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
});
```

**2. Notification Delivery**
```java
// Route by notification type
Map<String, Object> headers = new HashMap<>();
headers.put("type", "email");
headers.put("priority", "high");

channel.basicPublish("notifications", "",
    propsWithHeaders(headers),
    notification.getBytes());

// Email workers listen to email notifications
// SMS workers listen to SMS notifications
```

**3. RPC (Request-Reply)**
```java
// Client sends request with reply-to queue
String replyQueue = channel.queueDeclare().getQueue();
AMQP.BasicProperties props = new AMQP.BasicProperties.Builder()
    .replyTo(replyQueue)
    .correlationId(UUID.randomUUID().toString())
    .build();

channel.basicPublish("", "rpc_queue", props, request.getBytes());

// Wait for response
channel.basicConsume(replyQueue, true, (tag, delivery) -> {
    if (delivery.getProperties().getCorrelationId().equals(correlationId)) {
        handleResponse(delivery.getBody());
    }
});
```

## Performance Benchmarks

### Throughput Comparison

**Kafka** (3 brokers, 3x replication):
```
Messages/sec:  100,000 - 1,000,000+
MB/sec:        100 - 1,000+
Latency (p99): 5-50ms
```

**RabbitMQ** (3 nodes, mirrored queues):
```
Messages/sec:  10,000 - 50,000
MB/sec:        10 - 100
Latency (p99): 1-10ms
```

### Resource Usage

**Kafka:**
- CPU: Low (sequential I/O)
- Memory: Moderate (OS page cache)
- Disk: High (all messages persisted)
- Network: High (replication)

**RabbitMQ:**
- CPU: Moderate (routing logic)
- Memory: High (message storage in RAM)
- Disk: Low (persistent messages only)
- Network: Moderate

## Interview Tips

### Key Talking Points

**For Kafka:**
1. **Log-based architecture**: Immutable, append-only log
2. **Partitions for parallelism**: Horizontal scaling
3. **Consumer groups**: Multiple consumers, load balancing
4. **Offset management**: Replay capability
5. **High throughput**: Designed for millions of events

**For RabbitMQ:**
1. **Traditional broker**: Messages deleted after consumption
2. **Flexible routing**: Exchanges and binding keys
3. **Push model**: Broker pushes to consumers
4. **Priority queues**: Built-in priority support
5. **AMQP protocol**: Standard messaging protocol

### Common Interview Questions

**Q: Why does Kafka have higher throughput than RabbitMQ?**

A: Several reasons:
1. **Sequential I/O**: Kafka appends to log, leveraging OS page cache
2. **Zero-copy**: Direct transfer from disk to network socket
3. **Batching**: Messages batched at producer, broker, and consumer
4. **No per-message ACK**: Offsets committed in batches
5. **Simple data structure**: Immutable log vs complex routing

**Q: When would you use RabbitMQ over Kafka?**

A: Use RabbitMQ when you need:
1. **Complex routing**: Topic exchanges, header-based routing
2. **Low latency**: Sub-millisecond delivery requirements
3. **Priority queues**: Built-in priority support
4. **Traditional patterns**: RPC, request-reply
5. **Smaller scale**: 10k-50k messages/sec sufficient
6. **Mature ecosystem**: Existing AMQP integrations

**Q: How does Kafka handle message ordering?**

A: Kafka guarantees ordering within a partition. Messages with the same key go to the same partition. For global ordering, use a single partition (limits throughput). For practical systems, partition by entity ID (user_id, order_id) to get per-entity ordering with parallelism.

**Q: Explain Kafka's zero-copy optimization.**

A: Kafka uses the `sendfile()` system call to transfer data from disk to network socket without copying to user space:
```
Traditional: Disk → Kernel buffer → User buffer → Socket buffer → Network
Zero-copy:   Disk → Kernel buffer → Network (direct transfer)
```
This saves CPU cycles and reduces latency significantly.

**Q: How do you prevent message loss in Kafka?**

A: Multiple strategies:
1. **Producer**: `acks=all` (wait for all ISR), `retries > 0`
2. **Broker**: `min.insync.replicas=2` (at least 2 replicas)
3. **Replication**: Set replication factor ≥ 3
4. **Consumer**: Commit offsets only after processing

## Summary

### Kafka Strengths
- Distributed commit log architecture
- Excellent for event streaming and logs
- High throughput (millions of messages/second)
- Message replay capability
- Multiple consumer groups on same data

### RabbitMQ Strengths
- Traditional message broker with flexible routing
- Lower latency for small messages
- Complex routing patterns (exchanges)
- Priority queues built-in
- Mature AMQP ecosystem

### Decision Matrix

Choose **Kafka** if:
- High throughput requirements (100k+ msg/s)
- Multiple consumers need same data
- Need to replay messages
- Event sourcing / streaming architecture
- Log aggregation

Choose **RabbitMQ** if:
- Complex routing requirements
- Priority-based processing
- Traditional task queue patterns
- Low latency critical (< 1ms)
- Moderate throughput (< 50k msg/s)

---

**Related Topics:**
- [Stream Processing: Apache Flink](stream-processing.md)
- [Data Capture: Debezium](data-capture.md)
- [Data Warehousing Concepts](data-warehousing.md)

**References:**
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [RabbitMQ Documentation](https://www.rabbitmq.com/documentation.html)
- Kreps, Jay. "The Log: What every software engineer should know about real-time data's unifying abstraction"
