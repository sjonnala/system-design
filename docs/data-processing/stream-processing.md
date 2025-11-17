# Stream Processing Systems: Apache Flink

> Deep dive into stream processing with Apache Flink, stateful processing, and exactly-once semantics

## Table of Contents
- [Overview](#overview)
- [Core Concepts](#core-concepts)
- [Apache Flink Architecture](#apache-flink-architecture)
- [Stateful Processing](#stateful-processing)
- [Exactly-Once Processing](#exactly-once-processing)
- [Comparison with Other Stream Processors](#comparison-with-other-stream-processors)
- [Real-World Use Cases](#real-world-use-cases)
- [Interview Tips](#interview-tips)

## Overview

Stream processing is the continuous processing of unbounded data streams in real-time. Unlike batch processing, stream processing systems handle data as it arrives, enabling low-latency insights and actions.

### Key Characteristics
- **Low Latency**: Process events within milliseconds to seconds
- **Continuous Processing**: Never-ending data streams
- **Event Time Semantics**: Handle out-of-order events correctly
- **Fault Tolerance**: Recover from failures without data loss

### Why Apache Flink?

Apache Flink is a distributed stream processing framework designed for:
- **True Stream Processing**: Native streaming (not micro-batching)
- **Stateful Computations**: Rich state management capabilities
- **Exactly-Once Guarantees**: Strong consistency semantics
- **Event Time Processing**: Handle late-arriving and out-of-order events

```
Batch Processing (Hadoop)
Input → Process → Output
[1TB data] → [Hours] → [Results]

Stream Processing (Flink)
Input Stream → Process → Output Stream
[Events] → [Milliseconds] → [Real-time Results]
```

## Core Concepts

### 1. DataStreams

Flink processes data as DataStreams - immutable, distributed collections of data elements.

```java
// Reading from Kafka
DataStream<Event> stream = env
    .addSource(new FlinkKafkaConsumer<>(
        "events",
        new EventDeserializationSchema(),
        properties
    ));

// Transformations
DataStream<ProcessedEvent> processed = stream
    .keyBy(event -> event.getUserId())
    .window(TumblingEventTimeWindows.of(Time.minutes(5)))
    .aggregate(new EventAggregator());
```

### 2. Windows

Windows divide infinite streams into finite chunks for processing.

#### Window Types

**Tumbling Windows** (Non-overlapping)
```
Time:  0  1  2  3  4  5  6  7  8
       [  W1  ][  W2  ][  W3  ]
Events in W1: {e1, e2, e3}
Events in W2: {e4, e5}
Events in W3: {e6, e7, e8}
```

**Sliding Windows** (Overlapping)
```
Window Size: 3, Slide: 1
Time:  0  1  2  3  4  5  6
       [  W1  ]
          [  W2  ]
             [  W3  ]
```

**Session Windows** (Activity-based)
```
Gap Timeout: 2 units
Events: e1(0) e2(1) e3(2) ... [gap] ... e7(8) e8(9)
        [   Session 1   ]           [ Session 2 ]
```

### 3. Time Semantics

Flink supports three time notions:

**Event Time**: When the event actually occurred
```java
stream.assignTimestampsAndWatermarks(
    WatermarkStrategy
        .<Event>forBoundedOutOfOrderness(Duration.ofSeconds(5))
        .withTimestampAssigner((event, timestamp) -> event.getTimestamp())
);
```

**Processing Time**: When the event is processed by the system

**Ingestion Time**: When the event enters Flink

#### Watermarks

Watermarks are timestamps that flow through the stream, indicating progress in event time.

```
Events:  e1(t=1) e2(t=3) e3(t=2) e4(t=5) e5(t=4)
Stream:  e1 → W(1) → e2 → e3 → W(3) → e4 → e5 → W(5)

Watermark(t) = "All events with timestamp < t have arrived"
```

**Out-of-Order Handling**
```
Allowed Lateness: 2 seconds
Watermark: t=10

Event arrives with timestamp=9  → ACCEPTED (within lateness)
Event arrives with timestamp=7  → REJECTED (too late)
```

## Apache Flink Architecture

### Cluster Components

```
┌─────────────────────────────────────────────────┐
│              JobManager (Master)                 │
│  - Job Scheduling                                │
│  - Checkpoint Coordination                       │
│  - Recovery Coordination                         │
└────────────┬────────────────────────────────────┘
             │
    ┌────────┴────────┐
    │                 │
┌───▼────┐      ┌────▼────┐
│TaskMgr │      │TaskMgr  │  (Workers)
│  Slot  │      │  Slot   │  - Execute tasks
│  Slot  │      │  Slot   │  - Manage state
└────────┘      └─────────┘
```

### Job Execution Model

```
User Code (DataStream API)
        ↓
   Job Graph (Logical)
        ↓
  Execution Graph (Physical)
        ↓
  Parallel Execution
        ↓
   Task Managers
```

**Example: Word Count Parallelism**
```
Source (p=2) → FlatMap (p=4) → KeyBy → Sum (p=4) → Sink (p=2)

┌─────┐   ┌──────┐   ┌──────┐   ┌─────┐
│Src 1│──→│Map 1 │──→│Sum 1 │──→│Snk 1│
└─────┘   ├──────┤   ├──────┤   └─────┘
          │Map 2 │   │Sum 2 │
┌─────┐   ├──────┤   ├──────┤   ┌─────┐
│Src 2│──→│Map 3 │──→│Sum 3 │──→│Snk 2│
└─────┘   ├──────┤   ├──────┤   └─────┘
          │Map 4 │   │Sum 4 │
          └──────┘   └──────┘
```

## Stateful Processing

State is information that operators remember between events. This is crucial for:
- Aggregations (counts, sums, averages)
- Pattern detection (CEP)
- Machine learning models
- Deduplication

### State Types

#### 1. Keyed State
State scoped to a specific key (user, session, device)

```java
public class CountFunction extends RichFlatMapFunction<Event, Tuple2<String, Long>> {
    // ValueState holds a single value per key
    private transient ValueState<Long> countState;

    @Override
    public void open(Configuration parameters) {
        ValueStateDescriptor<Long> descriptor =
            new ValueStateDescriptor<>("count", Long.class, 0L);
        countState = getRuntimeContext().getState(descriptor);
    }

    @Override
    public void flatMap(Event event, Collector<Tuple2<String, Long>> out) {
        // Get current count for this key
        Long currentCount = countState.value();
        currentCount++;

        // Update state
        countState.update(currentCount);

        out.collect(new Tuple2<>(event.getKey(), currentCount));
    }
}
```

**Keyed State Types:**
- **ValueState&lt;T&gt;**: Single value per key
- **ListState&lt;T&gt;**: List of values per key
- **MapState&lt;K,V&gt;**: Map of key-value pairs
- **ReducingState&lt;T&gt;**: Aggregated value using ReduceFunction
- **AggregatingState&lt;IN,OUT&gt;**: Aggregated value using AggregateFunction

#### 2. Operator State
State scoped to parallel operator instance (not keyed)

```java
public class BufferingSink implements SinkFunction<Event>,
                                      CheckpointedFunction {
    private transient ListState<Event> checkpointedState;
    private List<Event> bufferedElements;

    @Override
    public void invoke(Event event, Context context) {
        bufferedElements.add(event);
        if (bufferedElements.size() >= THRESHOLD) {
            flushBuffer();
        }
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) {
        checkpointedState.clear();
        checkpointedState.addAll(bufferedElements);
    }

    @Override
    public void initializeState(FunctionInitializationContext context) {
        ListStateDescriptor<Event> descriptor =
            new ListStateDescriptor<>("buffered-elements", Event.class);
        checkpointedState = context.getOperatorStateStore()
                                   .getListState(descriptor);

        if (context.isRestored()) {
            // Restore from checkpoint
            for (Event element : checkpointedState.get()) {
                bufferedElements.add(element);
            }
        }
    }
}
```

### State Backends

State backends determine how and where state is stored.

#### MemoryStateBackend
```java
env.setStateBackend(new MemoryStateBackend());
```
- **Storage**: TaskManager JVM heap
- **Use Case**: Local development, small state
- **Limitations**: Limited by heap size

#### FsStateBackend
```java
env.setStateBackend(new FsStateBackend("hdfs://namenode/flink/checkpoints"));
```
- **Working State**: TaskManager memory
- **Checkpoints**: File system (HDFS, S3)
- **Use Case**: Large state with fast local access

#### RocksDBStateBackend
```java
env.setStateBackend(new EmbeddedRocksDBStateBackend());
env.getCheckpointConfig()
   .setCheckpointStorage("hdfs://namenode/flink/checkpoints");
```
- **Storage**: RocksDB (on-disk, embedded key-value store)
- **Capacity**: Limited by disk space (TBs possible)
- **Use Case**: Very large state, exceeding memory
- **Trade-off**: Slower than in-memory, but massive capacity

**Comparison:**
```
┌─────────────┬──────────┬───────────┬────────────┐
│ Backend     │ Location │ Max Size  │ Performance│
├─────────────┼──────────┼───────────┼────────────┤
│ Memory      │ Heap     │ ~GB       │ Fastest    │
│ FsState     │ Heap     │ ~10s GB   │ Fast       │
│ RocksDB     │ Disk     │ ~TBs      │ Moderate   │
└─────────────┴──────────┴───────────┴────────────┘
```

### State TTL (Time-To-Live)

Clean up old state automatically:

```java
StateTtlConfig ttlConfig = StateTtlConfig
    .newBuilder(Time.days(7))
    .setUpdateType(UpdateType.OnCreateAndWrite)
    .setStateVisibility(StateVisibility.NeverReturnExpired)
    .build();

ValueStateDescriptor<Long> descriptor =
    new ValueStateDescriptor<>("count", Long.class);
descriptor.enableTimeToLive(ttlConfig);
```

## Exactly-Once Processing

Exactly-once semantics guarantee that each event affects the final result exactly once, even in the presence of failures.

### The Challenge

```
Without Exactly-Once:
Event → Process → Failure → Replay → DUPLICATE!

With Exactly-Once:
Event → Process → Checkpoint → Failure → Restore → Continue
```

### Flink's Approach: Chandy-Lamport Algorithm

Flink uses distributed snapshots based on the Chandy-Lamport algorithm.

#### Checkpoint Barriers

Barriers are special markers injected into the stream:

```
Source 1: e1 e2 e3 | barrier-5 | e4 e5
Source 2: e6 e7 | barrier-5 | e8 e9 e10

Barrier-5 = "Snapshot all state when reaching this point"
```

**Alignment Process:**
```
    Source 1              Source 2
       │                     │
    e1 e2 e3 B5 e4        e6 e7 B5 e8
       └─────┬──────────────┘
             │
         Operator
      (waits for B5 from both)
             │
    Buffer: [e1,e2,e3,e6,e7]
    State: {...}
             │
       Save Checkpoint-5
             │
    Continue: [e4,e8,...]
```

### Checkpoint Configuration

```java
// Enable checkpointing every 10 seconds
env.enableCheckpointing(10000);

// Checkpoint configuration
CheckpointConfig config = env.getCheckpointConfig();

// Exactly-once mode (default)
config.setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

// Minimum time between checkpoints
config.setMinPauseBetweenCheckpoints(5000);

// Checkpoint timeout
config.setCheckpointTimeout(60000);

// Allow concurrent checkpoints
config.setMaxConcurrentCheckpoints(1);

// Externalized checkpoints (survive job cancellation)
config.enableExternalizedCheckpoints(
    ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
```

### Two-Phase Commit Protocol (2PC)

For exactly-once with external systems (databases, Kafka):

```
Phase 1: Pre-commit
┌──────────┐
│  Flink   │ ──① Begin Transaction
│ Operator │ ──② Write to Kafka (uncommitted)
└──────────┘ ──③ Checkpoint Barrier Received

Phase 2: Commit
┌──────────┐
│JobManager│ ──④ All operators checkpointed?
│          │ ──⑤ Notify commit
└──────────┘
     │
     ▼
┌──────────┐
│  Kafka   │ ──⑥ Commit Transaction
│(Producer)│
└──────────┘
```

**Example with Kafka:**
```java
FlinkKafkaProducer<Event> producer = new FlinkKafkaProducer<>(
    "output-topic",
    new EventSerializationSchema(),
    properties,
    FlinkKafkaProducer.Semantic.EXACTLY_ONCE  // 2PC enabled
);

stream.addSink(producer);
```

### Savepoints vs Checkpoints

**Checkpoints** (Automatic):
- Taken periodically by Flink
- For failure recovery
- Automatically cleaned up

**Savepoints** (Manual):
```bash
# Trigger savepoint
flink savepoint <jobId> [targetDirectory]

# Resume from savepoint
flink run -s <savepointPath> <jobJar>
```
- Triggered manually
- For version upgrades, A/B testing
- User manages lifecycle
- Compatible across code changes (with care)

## Comparison with Other Stream Processors

### Apache Flink vs Apache Spark Streaming

```
┌──────────────┬────────────────┬──────────────────┐
│ Feature      │ Flink          │ Spark Streaming  │
├──────────────┼────────────────┼──────────────────┤
│ Model        │ True Streaming │ Micro-batching   │
│ Latency      │ Milliseconds   │ Seconds          │
│ Throughput   │ High           │ Very High        │
│ State Mgmt   │ Advanced       │ Basic            │
│ Exactly-Once │ Yes (native)   │ Yes (with effort)│
│ Event Time   │ First-class    │ Supported        │
│ Windows      │ Flexible       │ Limited          │
└──────────────┴────────────────┴──────────────────┘
```

**Spark Streaming Architecture:**
```
Stream → [Batch 1][Batch 2][Batch 3] → Results
         (RDD)    (RDD)    (RDD)

Micro-batch interval: 500ms - 5s
```

**Flink Architecture:**
```
Stream → Event → Event → Event → Results
         ↓        ↓        ↓
      Continuous processing
```

### Apache Flink vs Apache Storm

```
┌──────────────┬────────────────┬──────────────────┐
│ Feature      │ Flink          │ Storm            │
├──────────────┼────────────────┼──────────────────┤
│ Processing   │ Streaming      │ Streaming        │
│ Exactly-Once │ Yes            │ Trident only     │
│ State        │ Built-in       │ Manual           │
│ SQL Support  │ Yes            │ No               │
│ Batch        │ Yes            │ No               │
│ Performance  │ Higher         │ Lower            │
└──────────────┴────────────────┴──────────────────┘
```

### Apache Flink vs Apache Kafka Streams

```
┌──────────────┬────────────────┬──────────────────┐
│ Feature      │ Flink          │ Kafka Streams    │
├──────────────┼────────────────┼──────────────────┤
│ Deployment   │ Cluster        │ Library (JVM)    │
│ Scalability  │ Massive        │ Moderate         │
│ Complexity   │ Higher         │ Lower            │
│ Input        │ Many sources   │ Kafka only       │
│ Exactly-Once │ Yes            │ Yes (Kafka only) │
│ Use Case     │ Complex CEP    │ Simple transform │
└──────────────┴────────────────┴──────────────────┘
```

## Real-World Use Cases

### 1. Real-Time Fraud Detection

```java
DataStream<Transaction> transactions = env.addSource(...);

// Complex event processing
Pattern<Transaction, ?> fraudPattern = Pattern
    .<Transaction>begin("first")
    .where(t -> t.getAmount() > 1000)
    .followedBy("second")
    .where(t -> t.getLocation().distanceTo(first.getLocation()) > 1000)
    .within(Time.minutes(5));

PatternStream<Transaction> patternStream = CEP.pattern(
    transactions.keyBy(Transaction::getUserId),
    fraudPattern
);

DataStream<Alert> alerts = patternStream.select(
    (Map<String, List<Transaction>> pattern) -> {
        return new Alert("Potential fraud detected", pattern);
    }
);
```

### 2. Real-Time Analytics Dashboard

```java
DataStream<PageView> pageViews = env.addSource(...);

// Count views per page per minute
DataStream<PageStats> stats = pageViews
    .keyBy(PageView::getPageId)
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .aggregate(new PageViewAggregator())
    .name("page-stats");

// Write to dashboard backend
stats.addSink(new ElasticsearchSink<>(...));
```

### 3. Stream Joins

```java
DataStream<Click> clicks = ...;
DataStream<Impression> impressions = ...;

// Join clicks with impressions (within 10 minutes)
clicks.join(impressions)
    .where(Click::getAdId)
    .equalTo(Impression::getAdId)
    .window(TumblingEventTimeWindows.of(Time.minutes(10)))
    .apply((click, impression) -> new Attribution(click, impression));
```

### 4. Session Analytics

```java
DataStream<UserEvent> events = ...;

events
    .keyBy(UserEvent::getUserId)
    .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
    .process(new SessionAnalyzer())
    .addSink(...);

class SessionAnalyzer extends ProcessWindowFunction<
    UserEvent, SessionMetrics, String, TimeWindow> {

    @Override
    public void process(String userId, Context ctx,
                       Iterable<UserEvent> events,
                       Collector<SessionMetrics> out) {
        // Analyze entire session
        SessionMetrics metrics = new SessionMetrics();
        for (UserEvent event : events) {
            metrics.addEvent(event);
        }
        out.collect(metrics);
    }
}
```

## Performance Optimization

### 1. Parallelism Tuning

```java
// Global default
env.setParallelism(10);

// Per operator
stream.map(...).setParallelism(20);

// Calculate optimal parallelism
int parallelism = Math.min(
    availableSlots,
    expectedThroughput / singleTaskCapacity
);
```

### 2. Network Buffer Tuning

```yaml
taskmanager.network.memory.fraction: 0.1
taskmanager.network.memory.min: 64mb
taskmanager.network.memory.max: 1gb
```

### 3. State Access Optimization

```java
// ✗ BAD: Multiple state accesses
for (Event event : events) {
    Long count = countState.value();  // Deserialization
    count++;
    countState.update(count);         // Serialization
}

// ✓ GOOD: Minimize state access
Long count = countState.value();
count += events.size();
countState.update(count);
```

### 4. Operator Chaining

```java
// Chained operators run in same thread (lower latency)
stream.filter(...).map(...);  // Chained by default

// Disable chaining if needed
stream.filter(...).disableChaining().map(...);
```

## Interview Tips

### Key Points to Emphasize

1. **Exactly-Once Semantics**
   - Explain checkpoint barriers and alignment
   - Discuss two-phase commit for sinks
   - Mention savepoints for upgrades

2. **Stateful Processing**
   - Different state types and when to use each
   - State backends comparison
   - State TTL for cleanup

3. **Event Time vs Processing Time**
   - Watermarks for handling late data
   - Out-of-order event handling
   - Allowed lateness configuration

4. **Backpressure Handling**
   - Flink automatically applies backpressure
   - Credit-based flow control
   - Monitor via metrics/UI

### Common Interview Questions

**Q: How does Flink achieve exactly-once semantics?**

A: Flink uses distributed snapshots (Chandy-Lamport) with checkpoint barriers. Barriers flow through the stream, triggering state snapshots. For external systems, Flink uses two-phase commit (2PC). On failure, Flink restores from the latest checkpoint and replays from that point.

**Q: What's the difference between event time and processing time?**

A: Event time is when the event occurred (embedded in data), while processing time is when Flink processes it. Event time enables correct handling of out-of-order events and late arrivals, crucial for accurate analytics. Watermarks track progress in event time.

**Q: How do you handle very large state in Flink?**

A: Use RocksDBStateBackend which stores state on disk (TBs possible). Configure incremental checkpoints to avoid full state snapshots. Implement state TTL to clean up old data. Consider state schema evolution for long-running jobs.

**Q: When would you choose Flink over Spark Streaming?**

A: Choose Flink for:
- Sub-second latency requirements
- Complex stateful processing
- Advanced event-time handling
- CEP (Complex Event Processing)

Choose Spark for:
- Existing Spark ecosystem integration
- Simpler processing logic
- Team familiarity with Spark

**Q: How does Flink handle backpressure?**

A: Flink uses credit-based flow control. When a downstream operator is slow, it stops granting credits to upstream operators, automatically slowing them down. This propagates back to the source, preventing out-of-memory errors.

### Design Patterns

**Pattern 1: Late Data Handling**
```java
stream
    .assignTimestampsAndWatermarks(...)
    .keyBy(...)
    .window(...)
    .allowedLateness(Time.minutes(5))  // Grace period
    .sideOutputLateData(lateTag)       // Capture very late data
    .aggregate(...);

// Process late data separately
DataStream<Event> lateStream = mainStream.getSideOutput(lateTag);
```

**Pattern 2: Stateful Deduplication**
```java
class DeduplicationFunction extends KeyedProcessFunction<String, Event, Event> {
    private ValueState<Boolean> seenState;

    @Override
    public void processElement(Event event, Context ctx, Collector<Event> out) {
        if (seenState.value() == null) {
            seenState.update(true);
            ctx.timerService().registerEventTimeTimer(
                ctx.timestamp() + TIMEOUT
            );
            out.collect(event);
        }
        // Else: duplicate, drop it
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<Event> out) {
        seenState.clear();  // Clean up after timeout
    }
}
```

## Summary

Apache Flink is a powerful stream processing framework that excels at:
- **Low-latency** stream processing with millisecond response times
- **Stateful computations** with advanced state management
- **Exactly-once guarantees** through distributed snapshots
- **Event-time processing** for handling out-of-order events

Choose Flink when you need true stream processing with strong consistency guarantees and complex stateful operations.

---

**Related Topics:**
- [Message Queues: Kafka](message-queues.md)
- [Data Capture: Debezium](data-capture.md)
- [Advanced Data Formats](data-formats.md)

**References:**
- [Apache Flink Documentation](https://flink.apache.org/)
- Carbone et al., "State Management in Apache Flink" (VLDB 2017)
- Akidau et al., "The Dataflow Model" (VLDB 2015)
