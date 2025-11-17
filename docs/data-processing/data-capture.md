# Data Capture: Debezium and Change Data Capture (CDC)

> Deep dive into Change Data Capture (CDC) with Debezium for real-time data pipelines

## Table of Contents
- [Overview](#overview)
- [What is Change Data Capture](#what-is-change-data-capture)
- [Debezium Architecture](#debezium-architecture)
- [Database Connectors](#database-connectors)
- [Event Structure](#event-structure)
- [Use Cases](#use-cases)
- [Comparison with Alternatives](#comparison-with-alternatives)
- [Interview Tips](#interview-tips)

## Overview

Change Data Capture (CDC) is a pattern for tracking changes in databases and propagating them to downstream systems in real-time. Debezium is an open-source distributed platform for CDC built on Apache Kafka.

### The Problem CDC Solves

**Traditional Approach: Polling**
```sql
-- Query every 5 seconds
SELECT * FROM orders WHERE updated_at > :last_check

Problems:
- High database load
- Polling delay (not real-time)
- Missed concurrent updates
- Deleted records not captured
```

**CDC Approach: Event Streaming**
```
Database Transaction Log → CDC → Event Stream
   (INSERT/UPDATE/DELETE)        (Real-time)
```

Benefits:
- **Real-time**: Changes captured immediately
- **Low overhead**: Read from transaction log (no queries)
- **Complete**: Captures all changes including deletes
- **Non-invasive**: No application code changes

## What is Change Data Capture

CDC captures row-level changes from database transaction logs and converts them to events.

### How Databases Work

Every database maintains a transaction log for durability:

```
Application → SQL → Database Engine
                         ↓
              ┌──────────┴──────────┐
              ↓                     ↓
         Data Files          Transaction Log
       (eventual)            (immediate)
```

**Transaction Log** (Write-Ahead Log - WAL):
```
Transaction 1: BEGIN
               INSERT INTO users (id, name) VALUES (1, 'Alice')
               UPDATE users SET email='alice@example.com' WHERE id=1
               COMMIT

Transaction 2: BEGIN
               DELETE FROM users WHERE id=5
               COMMIT
```

CDC reads this log to produce events.

### CDC vs Application-Level Events

**Application Events:**
```java
// Application publishes event
orderService.createOrder(order);
eventPublisher.publish(new OrderCreatedEvent(order));

Problems:
- Dual write problem (DB + Kafka)
- Not transactional
- Can miss changes (admin updates, batch jobs)
```

**CDC Events:**
```
Database Change → Auto-captured → Kafka
(Single source of truth)

Benefits:
- Guaranteed consistency
- Captures all changes
- No application modification
```

## Debezium Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Source Databases                       │
│  ┌───────────┐  ┌────────┐  ┌──────────┐  ┌─────────┐ │
│  │  MySQL    │  │Postgres│  │ MongoDB  │  │  Oracle │ │
│  └─────┬─────┘  └────┬───┘  └────┬─────┘  └────┬────┘ │
└────────┼─────────────┼───────────┼─────────────┼───────┘
         │             │           │             │
         │ (binlog)    │ (WAL)     │ (oplog)     │ (redo)
         │             │           │             │
         ▼             ▼           ▼             ▼
┌──────────────────────────────────────────────────────────┐
│              Kafka Connect Cluster                       │
│  ┌──────────────────────────────────────────────────┐   │
│  │          Debezium Source Connectors              │   │
│  │  ┌────────┐  ┌─────────┐  ┌────────┐  ┌──────┐  │   │
│  │  │ MySQL  │  │Postgres │  │MongoDB │  │Oracle│  │   │
│  │  │Connector│  │Connector│  │Connector│  │Conn. │  │   │
│  │  └────┬───┘  └────┬────┘  └────┬───┘  └───┬──┘  │   │
│  └───────┼───────────┼────────────┼──────────┼──────┘   │
└──────────┼───────────┼────────────┼──────────┼──────────┘
           │           │            │          │
           ▼           ▼            ▼          ▼
┌──────────────────────────────────────────────────────────┐
│                   Apache Kafka                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐   │
│  │ Topic:       │  │ Topic:       │  │ Topic:       │   │
│  │ db.users     │  │ db.orders    │  │ db.products  │   │
│  └──────────────┘  └──────────────┘  └──────────────┘   │
└──────────┬───────────────────────────────────────────────┘
           │
           ▼
┌──────────────────────────────────────────────────────────┐
│              Downstream Consumers                        │
│  ┌──────────┐  ┌────────────┐  ┌─────────────┐          │
│  │  Search  │  │Data Warehouse│ │Cache Invalidation│      │
│  │(Elastic) │  │  (Snowflake)│  │   (Redis)   │          │
│  └──────────┘  └────────────┘  └─────────────┘          │
└──────────────────────────────────────────────────────────┘
```

### Debezium Components

1. **Source Connectors**: Read database logs
2. **Kafka Connect**: Distributed platform for connectors
3. **Kafka Topics**: Store change events
4. **Schema Registry**: Manage event schemas (Avro)

### Kafka Connect

Debezium runs as Kafka Connect source connectors:

```properties
# connector.properties
name=inventory-connector
connector.class=io.debezium.connector.mysql.MySqlConnector
tasks.max=1

# Database connection
database.hostname=mysql
database.port=3306
database.user=debezium
database.password=secret
database.server.id=184054

# Which databases/tables to capture
database.include.list=inventory
table.include.list=inventory.customers,inventory.orders

# Kafka topics
database.server.name=dbserver1
topic.prefix=dbserver1
```

Deploy connector:
```bash
curl -X POST -H "Content-Type: application/json" \
  --data @connector.json \
  http://localhost:8083/connectors
```

## Database Connectors

Debezium supports multiple databases, each with specific log-reading mechanisms.

### MySQL Connector

**Reads from**: MySQL binlog (binary log)

**Enable binlog:**
```sql
-- my.cnf
[mysqld]
server-id=1
log_bin=mysql-bin
binlog_format=ROW
binlog_row_image=FULL
expire_logs_days=10
```

**Connector Configuration:**
```json
{
  "name": "mysql-connector",
  "config": {
    "connector.class": "io.debezium.connector.mysql.MySqlConnector",
    "database.hostname": "mysql",
    "database.port": "3306",
    "database.user": "debezium",
    "database.password": "dbz",
    "database.server.id": "184054",
    "database.server.name": "dbserver1",
    "database.include.list": "inventory",
    "table.include.list": "inventory.customers,inventory.orders",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.inventory",
    "snapshot.mode": "initial"
  }
}
```

**Snapshot Modes:**
- `initial`: Full snapshot on first run
- `when_needed`: Snapshot if no offset found
- `never`: Never snapshot
- `schema_only`: Only capture schema

### PostgreSQL Connector

**Reads from**: PostgreSQL WAL (Write-Ahead Log) via logical decoding

**Enable logical replication:**
```sql
-- postgresql.conf
wal_level = logical
max_replication_slots = 4
max_wal_senders = 4
```

**Create replication slot:**
```sql
SELECT * FROM pg_create_logical_replication_slot('debezium', 'pgoutput');
```

**Connector Configuration:**
```json
{
  "name": "postgres-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "postgres",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "inventory",
    "database.server.name": "dbserver1",
    "table.include.list": "public.customers,public.orders",
    "plugin.name": "pgoutput",
    "slot.name": "debezium"
  }
}
```

**Logical Decoding Plugins:**
- `pgoutput`: Native PostgreSQL (default, recommended)
- `wal2json`: JSON output
- `decoderbufs`: Protocol buffers

### MongoDB Connector

**Reads from**: MongoDB oplog (operations log)

**Connector Configuration:**
```json
{
  "name": "mongodb-connector",
  "config": {
    "connector.class": "io.debezium.connector.mongodb.MongoDbConnector",
    "mongodb.hosts": "rs0/mongo1:27017,mongo2:27017",
    "mongodb.name": "dbserver1",
    "collection.include.list": "inventory.customers,inventory.orders",
    "capture.mode": "change_streams_update_full"
  }
}
```

**Capture Modes:**
- `change_streams`: MongoDB 4.0+ (recommended)
- `oplog`: Read from oplog (replica set required)

### Oracle Connector

**Reads from**: Oracle redo logs via LogMiner

**Enable LogMiner:**
```sql
ALTER DATABASE ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
ALTER TABLE customers ADD SUPPLEMENTAL LOG DATA (ALL) COLUMNS;
```

**Connector Configuration:**
```json
{
  "name": "oracle-connector",
  "config": {
    "connector.class": "io.debezium.connector.oracle.OracleConnector",
    "database.hostname": "oracle",
    "database.port": "1521",
    "database.user": "c##dbzuser",
    "database.password": "dbz",
    "database.dbname": "ORCLCDB",
    "database.server.name": "dbserver1",
    "table.include.list": "INVENTORY.CUSTOMERS",
    "database.history.kafka.bootstrap.servers": "kafka:9092",
    "database.history.kafka.topic": "schema-changes.inventory"
  }
}
```

## Event Structure

Debezium produces structured change events with rich metadata.

### Event Format

Each change event has:
- **before**: State before the change (null for INSERT)
- **after**: State after the change (null for DELETE)
- **source**: Metadata about the change
- **op**: Operation type (c=create, u=update, d=delete, r=read/snapshot)
- **ts_ms**: Timestamp

### INSERT Event Example

```json
{
  "before": null,
  "after": {
    "id": 1001,
    "first_name": "Sally",
    "last_name": "Thomas",
    "email": "sally.thomas@example.com"
  },
  "source": {
    "version": "2.1.0.Final",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1642511940000,
    "snapshot": "false",
    "db": "inventory",
    "table": "customers",
    "server_id": 223344,
    "gtid": null,
    "file": "mysql-bin.000003",
    "pos": 154,
    "row": 0,
    "thread": 7,
    "query": null
  },
  "op": "c",
  "ts_ms": 1642511940308,
  "transaction": null
}
```

### UPDATE Event Example

```json
{
  "before": {
    "id": 1001,
    "first_name": "Sally",
    "last_name": "Thomas",
    "email": "sally.thomas@example.com"
  },
  "after": {
    "id": 1001,
    "first_name": "Sally",
    "last_name": "Thomas",
    "email": "sally.thomas@newdomain.com"
  },
  "source": {
    "version": "2.1.0.Final",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1642512040000,
    "snapshot": "false",
    "db": "inventory",
    "table": "customers",
    "server_id": 223344,
    "file": "mysql-bin.000003",
    "pos": 484,
    "row": 0
  },
  "op": "u",
  "ts_ms": 1642512040617
}
```

### DELETE Event Example

```json
{
  "before": {
    "id": 1001,
    "first_name": "Sally",
    "last_name": "Thomas",
    "email": "sally.thomas@newdomain.com"
  },
  "after": null,
  "source": {
    "version": "2.1.0.Final",
    "connector": "mysql",
    "name": "dbserver1",
    "ts_ms": 1642512140000,
    "snapshot": "false",
    "db": "inventory",
    "table": "customers",
    "server_id": 223344,
    "file": "mysql-bin.000003",
    "pos": 805,
    "row": 0
  },
  "op": "d",
  "ts_ms": 1642512140824
}
```

### Tombstone Events

After a DELETE, Debezium sends a tombstone (null value) for log compaction:

```json
{
  "key": {"id": 1001},
  "value": null
}
```

This allows Kafka's log compaction to eventually remove all traces of deleted records.

### Schema Information

Debezium can embed schema in events or use Schema Registry:

**Embedded Schema (JSON):**
```json
{
  "schema": {
    "type": "struct",
    "fields": [
      {"type": "int32", "optional": false, "field": "id"},
      {"type": "string", "optional": false, "field": "first_name"},
      {"type": "string", "optional": false, "field": "last_name"},
      {"type": "string", "optional": true, "field": "email"}
    ]
  },
  "payload": {
    "id": 1001,
    "first_name": "Sally",
    "last_name": "Thomas",
    "email": "sally.thomas@example.com"
  }
}
```

**Avro with Schema Registry:**
```json
// Compact binary format
// Schema stored separately in registry
```

## Processing Change Events

### Consumer Example (Java)

```java
@KafkaListener(topics = "dbserver1.inventory.customers")
public void handleCustomerChange(ConsumerRecord<String, String> record) {
    JsonNode event = objectMapper.readTree(record.value());

    String op = event.get("op").asText();
    JsonNode after = event.get("after");
    JsonNode before = event.get("before");

    switch (op) {
        case "c": // CREATE
            handleCustomerCreated(after);
            break;
        case "u": // UPDATE
            handleCustomerUpdated(before, after);
            break;
        case "d": // DELETE
            handleCustomerDeleted(before);
            break;
        case "r": // SNAPSHOT/READ
            handleCustomerSnapshot(after);
            break;
    }
}

private void handleCustomerCreated(JsonNode customer) {
    // Index in Elasticsearch
    elasticsearchClient.index("customers", customer);

    // Invalidate cache
    cache.remove("customer:" + customer.get("id").asText());
}

private void handleCustomerUpdated(JsonNode before, JsonNode after) {
    // Update search index
    elasticsearchClient.update("customers",
        after.get("id").asText(),
        after);

    // Invalidate cache
    cache.remove("customer:" + after.get("id").asText());
}

private void handleCustomerDeleted(JsonNode customer) {
    // Remove from search
    elasticsearchClient.delete("customers",
        customer.get("id").asText());

    // Clear cache
    cache.remove("customer:" + customer.get("id").asText());
}
```

### Transformations (SMT - Single Message Transforms)

Apply transformations to events in Kafka Connect:

```json
{
  "transforms": "unwrap,addPrefix",
  "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
  "transforms.unwrap.drop.tombstones": "false",
  "transforms.addPrefix.type": "org.apache.kafka.connect.transforms.RegexRouter",
  "transforms.addPrefix.regex": ".*",
  "transforms.addPrefix.replacement": "cdc.$0"
}
```

**ExtractNewRecordState**: Unwraps Debezium envelope
```json
// Before
{"before": {...}, "after": {...}, "op": "u"}

// After
{...}  // Just the "after" value
```

**Routing**: Route to different topics based on operation
```json
{
  "transforms": "route",
  "transforms.route.type": "io.debezium.transforms.ByLogicalTableRouter",
  "transforms.route.topic.regex": ".*customers.*",
  "transforms.route.topic.replacement": "aggregated-customers"
}
```

## Use Cases

### 1. Cache Invalidation

**Problem**: Database updated, cache becomes stale

```
┌─────────┐
│   App   │ ─────────→ UPDATE users SET name='Alice' WHERE id=1
└─────────┘                    ↓
                    ┌─────────────────┐
                    │   MySQL         │
                    └────────┬────────┘
                             │ binlog
                             ▼
                    ┌─────────────────┐
                    │   Debezium      │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │   Kafka         │
                    └────────┬────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │ Cache Service   │
                    │ DELETE cache['user:1']
                    └─────────────────┘
```

**Implementation:**
```java
@KafkaListener(topics = "dbserver1.inventory.users")
public void invalidateCache(ConsumerRecord<String, String> record) {
    JsonNode event = objectMapper.readTree(record.value());
    String userId = event.get("after").get("id").asText();
    redisTemplate.delete("user:" + userId);
    log.info("Invalidated cache for user {}", userId);
}
```

### 2. Search Index Sync

**Problem**: Keep Elasticsearch in sync with database

```
MySQL → Debezium → Kafka → Elasticsearch Connector
```

**Elasticsearch Sink Connector:**
```json
{
  "name": "elasticsearch-sink",
  "config": {
    "connector.class": "io.confluent.connect.elasticsearch.ElasticsearchSinkConnector",
    "topics": "dbserver1.inventory.products",
    "connection.url": "http://elasticsearch:9200",
    "type.name": "_doc",
    "key.ignore": "false",
    "schema.ignore": "true",
    "behavior.on.null.values": "delete",
    "transforms": "unwrap",
    "transforms.unwrap.type": "io.debezium.transforms.ExtractNewRecordState",
    "transforms.unwrap.drop.tombstones": "false"
  }
}
```

**Result:**
- INSERT in MySQL → Document added to Elasticsearch
- UPDATE in MySQL → Document updated in Elasticsearch
- DELETE in MySQL → Document removed from Elasticsearch

### 3. Data Warehousing (ETL)

**Problem**: Real-time data pipeline to data warehouse

```
OLTP Database (MySQL)
      ↓ CDC
   Debezium
      ↓
    Kafka
      ↓ Stream Processing
    Flink
      ↓
  Snowflake
```

**Flink Job Example:**
```java
// Read CDC events from Kafka
DataStream<String> cdcStream = env
    .addSource(new FlinkKafkaConsumer<>("dbserver1.inventory.orders", ...));

// Parse and transform
DataStream<OrderFact> orderFacts = cdcStream
    .map(new ParseCDCEvent())
    .filter(event -> event.getOp().equals("c") || event.getOp().equals("u"))
    .map(event -> buildOrderFact(event));

// Aggregate by time window
DataStream<OrderMetrics> metrics = orderFacts
    .keyBy(OrderFact::getCustomerId)
    .window(TumblingEventTimeWindows.of(Time.hours(1)))
    .aggregate(new OrderMetricsAggregator());

// Sink to Snowflake
metrics.addSink(new SnowflakeSink(...));
```

### 4. Event Sourcing

**Hybrid approach**: Database as source of truth + CDC for event stream

```
┌─────────────────┐
│  Database       │
│  (Current State)│
└────────┬────────┘
         │ CDC
         ▼
┌─────────────────┐
│  Kafka          │
│  (Event Stream) │
└────────┬────────┘
         │
         ├─→ Materialized View 1 (Analytics)
         ├─→ Materialized View 2 (Reporting)
         └─→ Materialized View 3 (Real-time Dashboard)
```

### 5. Microservices Data Sync

**Problem**: Synchronize data across microservices

```
Order Service (Postgres)
      ↓ CDC
   Debezium → Kafka Topic: "orders"
      ↓
   ┌──┴──────┬──────────┐
   ▼         ▼          ▼
Inventory  Shipping  Analytics
Service    Service    Service
```

Each service subscribes to relevant CDC topics and maintains its own denormalized view.

### 6. Audit Trail

**Capture all changes for compliance:**

```java
@KafkaListener(topics = "dbserver1.inventory.*")
public void auditTrail(ConsumerRecord<String, String> record) {
    JsonNode event = objectMapper.readTree(record.value());
    JsonNode source = event.get("source");

    AuditLog log = AuditLog.builder()
        .timestamp(source.get("ts_ms").asLong())
        .database(source.get("db").asText())
        .table(source.get("table").asText())
        .operation(event.get("op").asText())
        .beforeValue(event.get("before"))
        .afterValue(event.get("after"))
        .user(getCurrentUser(source))
        .build();

    auditRepository.save(log);
}
```

## Comparison with Alternatives

### Debezium vs Custom Triggers

**Database Triggers:**
```sql
CREATE TRIGGER customer_changes
AFTER INSERT OR UPDATE OR DELETE ON customers
FOR EACH ROW EXECUTE FUNCTION notify_change();
```

```
┌────────────────┬────────────────┬──────────────┐
│ Feature        │ Debezium       │ Triggers     │
├────────────────┼────────────────┼──────────────┤
│ Performance    │ High           │ Lower        │
│ DB Overhead    │ Minimal        │ Significant  │
│ Maintenance    │ External       │ In Database  │
│ Scalability    │ Excellent      │ Limited      │
│ Backpressure   │ Handled        │ Can block DB │
│ Schema Changes │ Auto-detected  │ Manual update│
└────────────────┴────────────────┴──────────────┘
```

### Debezium vs Polling

**Polling Approach:**
```sql
SELECT * FROM orders
WHERE updated_at > :last_poll_time
LIMIT 1000;
```

```
┌────────────────┬────────────────┬──────────────┐
│ Feature        │ Debezium       │ Polling      │
├────────────────┼────────────────┼──────────────┤
│ Latency        │ < 1s           │ Poll interval│
│ DB Load        │ Very Low       │ High         │
│ Deletes        │ Captured       │ Missed       │
│ Ordering       │ Guaranteed     │ Not guaranteed│
│ Completeness   │ 100%           │ Gaps possible│
│ Initial Load   │ Snapshot       │ Full scan    │
└────────────────┴────────────────┴──────────────┘
```

### Debezium vs Dual Writes

**Dual Write:**
```java
@Transactional
public void createOrder(Order order) {
    orderRepository.save(order);  // Write 1
    eventPublisher.publish(new OrderCreatedEvent(order));  // Write 2
}
```

**Problem**: Not atomic! Can fail after DB write but before event publish.

**Debezium**: Single write (to DB), events automatically generated.

### Debezium vs Kafka Connect JDBC

**JDBC Source Connector** (polling-based):
```json
{
  "connector.class": "io.confluent.connect.jdbc.JdbcSourceConnector",
  "mode": "timestamp",
  "timestamp.column.name": "updated_at",
  "poll.interval.ms": "5000"
}
```

```
┌────────────────┬────────────────┬──────────────┐
│ Feature        │ Debezium       │ JDBC Connector│
├────────────────┼────────────────┼──────────────┤
│ Mechanism      │ Log-based      │ Query-based  │
│ Latency        │ < 1s           │ Poll interval│
│ DB Impact      │ Minimal        │ Moderate     │
│ Deletes        │ Yes            │ No           │
│ Before Values  │ Yes            │ No           │
│ Schema Changes │ Auto-detected  │ Manual       │
└────────────────┴────────────────┴──────────────┘
```

## Operational Considerations

### Monitoring

**Key Metrics:**
```java
// Connector lag (behind database)
debezium_connector_lag_seconds

// Events per second
debezium_events_total

// Snapshot progress
debezium_snapshot_completed_total
debezium_snapshot_running

// Errors
debezium_errors_total
```

**Kafka Connect Metrics:**
```bash
curl http://localhost:8083/connectors/mysql-connector/status

{
  "name": "mysql-connector",
  "connector": {"state": "RUNNING"},
  "tasks": [
    {"id": 0, "state": "RUNNING", "worker_id": "kafka-connect-1:8083"}
  ]
}
```

### Handling Schema Changes

**ADD COLUMN (Compatible):**
```sql
ALTER TABLE customers ADD COLUMN phone VARCHAR(20);
```
Debezium automatically detects and includes new column in events.

**RENAME COLUMN (Breaking):**
```sql
ALTER TABLE customers RENAME COLUMN email TO email_address;
```
Requires coordination with consumers. Use SMT to map old → new.

**DROP COLUMN:**
```sql
ALTER TABLE customers DROP COLUMN middle_name;
```
Events will no longer include the column. Consumers must handle gracefully.

### Backpressure and Flow Control

**Connector slowing down?**
```properties
# Increase batch sizes
max.batch.size=2048
max.queue.size=8192

# Increase parallelism (if supported)
tasks.max=4
```

**Kafka can't keep up?**
```properties
# Add partitions to topics
kafka-topics --alter --topic dbserver1.inventory.orders --partitions 10

# Add consumer instances
```

### Disaster Recovery

**Connector state stored in Kafka:**
- Offsets: `connect-offsets` topic
- Configuration: `connect-configs` topic
- Status: `connect-status` topic

**Restore connector:**
```bash
# Connector automatically resumes from last offset
# No data loss as long as:
# 1. Database binlog/WAL retained
# 2. Kafka topics retained
```

**Initial snapshot on new database:**
```json
{
  "snapshot.mode": "initial",
  "snapshot.locking.mode": "minimal"
}
```

## Interview Tips

### Key Points to Emphasize

1. **Log-based CDC**: Read from transaction logs (binlog, WAL, redo log)
2. **Non-invasive**: No application code changes, minimal DB overhead
3. **Exactly-once**: When combined with Kafka transactions
4. **Complete capture**: All changes including deletes
5. **Schema evolution**: Automatic detection of schema changes

### Common Interview Questions

**Q: How does CDC differ from polling?**

A: CDC reads directly from database transaction logs in real-time with minimal overhead, while polling queries the database at intervals. CDC captures all changes including deletes with guaranteed ordering, whereas polling can miss changes, create DB load, and has inherent latency (poll interval).

**Q: What is the dual-write problem and how does CDC solve it?**

A: Dual-write is when you write to database and message broker separately - not atomic, can fail partially. CDC solves this by having a single write (to database), with changes automatically captured from transaction log and published. Database remains single source of truth.

**Q: How does Debezium handle database failures?**

A: Debezium stores offset in Kafka (last position in binlog/WAL). On failure, it resumes from that offset. As long as database retains logs long enough, no data is lost. Configure `expire_logs_days` (MySQL) or `wal_keep_segments` (Postgres) appropriately.

**Q: What happens when you add a new column to a table?**

A: Debezium automatically detects schema changes and includes the new column in subsequent events. The schema is versioned in Schema Registry. Consumers should handle schema evolution gracefully (ignore unknown fields or update schema).

**Q: How do you handle large initial snapshots?**

A: Use snapshot modes:
- `initial`: Full snapshot (can lock tables)
- `when_needed`: Only if no offset exists
- `schema_only`: Skip data, only schema

For large tables, consider:
- Snapshot in off-peak hours
- Use `snapshot.select.statement.overrides` to filter rows
- Increase `snapshot.fetch.size` for better performance

**Q: When would you NOT use CDC?**

A: Avoid CDC when:
1. Database doesn't support log-based CDC (e.g., some managed databases)
2. Very high write volume (millions/sec) might strain log reading
3. Need transformation logic before events (better in application)
4. Compliance prevents log access
5. Simple use cases where polling suffices

### Design Patterns

**Pattern 1: Outbox Pattern (Transactional Messaging)**

```sql
-- Orders table
CREATE TABLE orders (
  id SERIAL PRIMARY KEY,
  customer_id INT,
  total DECIMAL(10,2),
  status VARCHAR(20)
);

-- Outbox table (events to publish)
CREATE TABLE outbox (
  id SERIAL PRIMARY KEY,
  aggregate_type VARCHAR(255),
  aggregate_id VARCHAR(255),
  event_type VARCHAR(255),
  payload JSONB,
  created_at TIMESTAMP DEFAULT NOW()
);

-- Transaction includes both
BEGIN;
  INSERT INTO orders (...) VALUES (...) RETURNING id;
  INSERT INTO outbox (aggregate_type, aggregate_id, event_type, payload)
  VALUES ('Order', order_id, 'OrderCreated', '{"orderId": 123, ...}');
COMMIT;
```

Debezium captures outbox changes → publishes to Kafka → consumers process.

**Pattern 2: Saga Pattern with CDC**

```
Order Service:
  CreateOrder → DB → CDC → Kafka

Inventory Service:
  Subscribe "OrderCreated"
  → Reserve Inventory → DB → CDC → Kafka

Payment Service:
  Subscribe "InventoryReserved"
  → Charge Payment → DB → CDC → Kafka

Order Service:
  Subscribe "PaymentCompleted"
  → Complete Order → DB → CDC → Kafka
```

Each service writes to its DB, CDC propagates events to next service.

## Summary

Debezium provides reliable, low-latency Change Data Capture by:
- Reading database transaction logs (not queries)
- Publishing changes to Kafka in real-time
- Preserving complete change history including deletes
- Enabling event-driven architectures without dual-write problems

### When to Use Debezium

Use Debezium when you need:
- **Real-time sync**: Database → Search/Cache/Warehouse
- **Event-driven architecture**: Database changes trigger workflows
- **Audit trail**: Complete history of all changes
- **Microservices sync**: Shared data across services
- **No code changes**: Capture from existing databases

### Alternatives

Consider alternatives if:
- Database doesn't support CDC
- Need transformation before events (use triggers or app events)
- Very simple use cases (polling may suffice)
- Can't access transaction logs

---

**Related Topics:**
- [Message Queues: Kafka](message-queues.md)
- [Stream Processing: Flink](stream-processing.md)
- [Data Warehousing Concepts](data-warehousing.md)

**References:**
- [Debezium Documentation](https://debezium.io/documentation/)
- [Change Data Capture Patterns](https://www.confluent.io/blog/change-data-capture-with-debezium-and-kafka/)
- Kleppmann, Martin. "Designing Data-Intensive Applications" (Chapter 11: Stream Processing)
