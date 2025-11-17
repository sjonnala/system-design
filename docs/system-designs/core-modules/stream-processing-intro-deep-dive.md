# Stream Processing Deep Dive: Real-Time Data Processing

## Contents

- [Stream Processing Deep Dive: Real-Time Data Processing](#stream-processing-deep-dive-real-time-data-processing)
    - [Core Mental Model](#core-mental-model)
    - [What is Stream Processing?](#what-is-stream-processing)
    - [Batch vs Stream Processing](#batch-vs-stream-processing)
    - [Stream Processing Concepts](#stream-processing-concepts)
    - [Windowing: Time in Stream Processing](#windowing-time-in-stream-processing)
    - [Stateful Stream Processing](#stateful-stream-processing)
    - [Event Time vs Processing Time](#event-time-vs-processing-time)
    - [Guarantees & Semantics](#guarantees--semantics)
    - [Popular Stream Processing Frameworks](#popular-stream-processing-frameworks)
    - [When to Use Stream Processing](#when-to-use-stream-processing)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: STREAM PROCESSING](#mind-map-stream-processing)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Stream Processing = Process UNBOUNDED data in REAL-TIME
                    (opposite of batch processing)

The Core Difference:
┌────────────────────────────────────────────────────────────┐
│ Batch:  Collect data → Process ALL at once → Output       │
│         [==========] → [Process] → [Output]                │
│         Bounded input, High latency                        │
│                                                             │
│ Stream: Data flows → Process EACH event → Output          │
│         →→→→→→→→→→ → [Process] → →→→→→→→→→                │
│         Unbounded input, Low latency                       │
└────────────────────────────────────────────────────────────┘

Visual Comparison:
          BATCH PROCESSING              STREAM PROCESSING
    ┌──────────────────┐          ┌──────────────────┐
    │ Wait for all data│          │ Process as it    │
    │                  │          │ arrives          │
    │ [====Data====]   │          │ →→→→→→→→→→→→→→  │
    │      ↓           │          │  ↓  ↓  ↓  ↓  ↓  │
    │   Process        │          │ Process each     │
    │      ↓           │          │  ↓  ↓  ↓  ↓  ↓  │
    │   Results        │          │ Results stream   │
    │                  │          │ →→→→→→→→→→→→→→  │
    │ Latency: Hours   │          │ Latency: Seconds │
    └──────────────────┘          └──────────────────┘
```

**The Key Insight:**
```
Stream processing treats data as CONTINUOUS FLOW
Not discrete batches

Example: Website clicks
─────────────────────────
Batch approach:
  1. Collect clicks for 1 hour → 1M clicks
  2. Process all 1M clicks → Generate report
  3. Wait another hour → Repeat
  Result: Report shows data from 1+ hour ago (stale!)

Stream approach:
  1. Each click arrives → Process immediately
  2. Update running metrics
  3. Results always current (within seconds)
  Result: Real-time dashboard!

Use cases:
  - Fraud detection (block fraudulent transaction NOW)
  - Real-time dashboards (live metrics)
  - Alerting (notify when threshold crossed)
  - Personalization (show relevant content immediately)
```

---

## What is Stream Processing?

🎓 **PROFESSOR**: Stream processing handles unbounded, continuous data flows.

### Definition & Characteristics

```
Stream Processing:
─────────────────
Continuous computation over unbounded, event-driven data

Key characteristics:
  1. Unbounded input
     - No start or end
     - Data arrives continuously
     - Potentially infinite

  2. Low latency
     - Process events within seconds (or milliseconds)
     - Not hours like batch processing

  3. Event-at-a-time
     - Process each event as it arrives
     - Or small micro-batches (e.g., 1-second windows)

  4. Stateful
     - Maintain state across events
     - Example: Running count, session tracking

  5. Fault-tolerant
     - Must handle failures without losing data
     - Checkpointing, exactly-once semantics

Contrast with Batch:
────────────────────
┌──────────────────────────────────────────────────────┐
│               BATCH vs STREAM                         │
├──────────────────────────────────────────────────────┤
│                                                       │
│ Input:                                                │
│   Batch:  Bounded (known size)                       │
│   Stream: Unbounded (infinite)                       │
│                                                       │
│ Latency:                                              │
│   Batch:  Hours to days                              │
│   Stream: Seconds to minutes                         │
│                                                       │
│ Processing:                                           │
│   Batch:  All data at once                           │
│   Stream: Event-by-event                             │
│                                                       │
│ Results:                                              │
│   Batch:  Periodic (daily, hourly)                   │
│   Stream: Continuous                                  │
│                                                       │
│ State:                                                │
│   Batch:  Stateless (each run independent)           │
│   Stream: Stateful (maintain running state)          │
│                                                       │
└──────────────────────────────────────────────────────┘
```

### Stream Processing Pipeline

```
Typical Stream Processing Architecture:
───────────────────────────────────────

┌──────────────┐     ┌──────────────┐     ┌──────────────┐
│   SOURCES    │ ──→ │  PROCESSING  │ ──→ │    SINKS     │
│              │     │              │     │              │
│ - Kafka      │     │ - Filter     │     │ - Database   │
│ - Kinesis    │     │ - Transform  │     │ - Dashboard  │
│ - Event Hub  │     │ - Aggregate  │     │ - Alerts     │
│ - Logs       │     │ - Join       │     │ - Files      │
│ - Sensors    │     │ - Window     │     │ - APIs       │
└──────────────┘     └──────────────┘     └──────────────┘
        ↓                    ↓                    ↓
    Ingest              Transform              Output
   →→→→→→→→           →→→→→→→→           →→→→→→→→

Example: Clickstream processing
────────────────────────────────
Source:     Web servers → Kafka
Processing: Count clicks per page (1-minute windows)
Sink:       Update dashboard

Flow:
  Click event → Kafka → Stream processor
              → Group by page
              → Count within 1-min window
              → Update Redis
              → Dashboard reads from Redis
              → User sees real-time metrics!
```

---

## Batch vs Stream Processing

🏗️ **ARCHITECT**: Understanding the trade-offs between batch and stream.

### When to Use Each

```python
"""
Decision framework: Batch or Stream?
"""

class ProcessingDecision:

    def choose_approach(self, requirements):
        """
        Decision matrix
        """

        # Latency requirement
        if requirements.latency < 1_minute:
            return "STREAM"  # Need real-time results

        if requirements.latency > 1_hour:
            return "BATCH"  # Can wait for batch processing

        # Data volume
        if requirements.volume > 1_TB_per_hour:
            if requirements.latency < 5_minutes:
                return "STREAM (with large cluster)"
            else:
                return "BATCH (more cost-effective)"

        # Complexity
        if requirements.has_complex_joins:
            return "BATCH"  # Joins easier in batch

        if requirements.has_simple_aggregations:
            return "STREAM"  # Simple ops work well

        # Cost
        if requirements.budget == "limited":
            return "BATCH"  # More cost-effective (pay per job)
        else:
            return "STREAM"  # 24/7 cluster (higher cost)

        # Use case
        if requirements.use_case in ["fraud_detection", "alerting", "monitoring"]:
            return "STREAM"  # Need immediate action

        if requirements.use_case in ["reporting", "ml_training", "etl"]:
            return "BATCH"  # Historical analysis

        return "HYBRID (Lambda architecture)"  # Best of both

"""
Real-world examples:
───────────────────

STREAM use cases:
  ✓ Fraud detection (block fraudulent card immediately)
  ✓ Real-time dashboards (live metrics)
  ✓ Alerting (notify when server down)
  ✓ Personalization (show relevant ads now)
  ✓ IoT processing (sensor data, telemetry)
  ✓ Log monitoring (detect anomalies)

BATCH use cases:
  ✓ Daily reports (aggregated sales)
  ✓ ML training (train on historical data)
  ✓ ETL pipelines (nightly data warehouse load)
  ✓ Large-scale joins (join TBs of data)
  ✓ Historical analysis (last year's trends)

HYBRID (Lambda architecture):
  ✓ E-commerce analytics
    - Stream: Real-time inventory updates
    - Batch: Daily sales reports
  ✓ Recommendation systems
    - Stream: Immediate click tracking
    - Batch: Model training on historical data
"""
```

### Performance Comparison

```
Performance characteristics:
───────────────────────────

Throughput (events/sec):
  Batch:  Very high (millions/sec possible)
          Process massive datasets efficiently
          Optimize for throughput, not latency

  Stream: High (hundreds of thousands/sec)
          Lower than batch (per-event overhead)
          Optimize for latency, not throughput

Latency:
  Batch:  Hours to days
          Example: Daily job runs at 2am, results at 6am
                   → 4+ hour latency

  Stream: Seconds to minutes
          Example: Event arrives, processed in 1 second
                   → 1 second latency

Cost:
  Batch:  Lower (pay per job)
          Run only when needed (e.g., nightly)
          Cluster spins up, processes, spins down

  Stream: Higher (24/7 cluster)
          Always running (waiting for events)
          Even if few events, cluster runs

Resource utilization:
  Batch:  High (burst usage)
          100% CPU during job, 0% otherwise

  Stream: Variable (depends on event rate)
          Low during quiet hours, high during peaks
          Must provision for peak load

Example cost comparison:
───────────────────────
Scenario: Process 1TB data daily

Batch:
  - Run 2-hour job on 100-node cluster
  - Cost: 100 nodes × 2 hours × $1/hr = $200/day
  - Total: $200/day

Stream:
  - Run 24/7 on 20-node cluster
  - Cost: 20 nodes × 24 hours × $1/hr = $480/day
  - Total: $480/day

Batch is 2.4× cheaper (but higher latency)
```

---

## Stream Processing Concepts

🎓 **PROFESSOR**: Core concepts you must understand.

### Events & Streams

```
Event:
──────
Single occurrence at a point in time
Immutable (can't change past)

Example events:
  - User clicked button
  - Temperature sensor reading
  - Stock price update
  - Payment transaction

Event structure:
  {
    "event_id": "evt_12345",
    "event_type": "click",
    "timestamp": "2024-01-15T10:30:00Z",
    "user_id": "user_789",
    "page": "/products/123",
    "metadata": {...}
  }

Stream:
───────
Sequence of events over time
Unbounded (never ends)
Ordered (usually by timestamp)

Visual:
  Time ──────────────────────────────→
  Events: [e1] [e2] [e3] [e4] [e5] ...

Properties:
  - Append-only (can't modify past events)
  - Immutable (events don't change)
  - Ordered (by event time or arrival time)
  - Unbounded (infinite stream)
```

### Operations on Streams

```scala
/**
 * Common stream operations
 */

import org.apache.flink.streaming.api.scala._

// Create stream from source
val clickStream: DataStream[Click] = env
  .addSource(new FlinkKafkaConsumer[Click](...))

/**
 * 1. Map: Transform each event
 */
val enrichedClicks = clickStream.map { click =>
  EnrichedClick(
    click.userId,
    click.page,
    getUserCountry(click.userId),  // Lookup
    click.timestamp
  )
}

/**
 * 2. Filter: Select events
 */
val purchaseClicks = clickStream.filter { click =>
  click.eventType == "purchase"
}

/**
 * 3. KeyBy: Group by key (for stateful operations)
 */
val clicksByUser = clickStream.keyBy(_.userId)

/**
 * 4. Window: Group by time
 */
val clicksPerMinute = clickStream
  .keyBy(_.page)
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .reduce((c1, c2) => Click(c1.page, c1.count + c2.count))

/**
 * 5. Aggregate: Running aggregation
 */
val runningTotal = clickStream
  .keyBy(_.userId)
  .map(new MapFunction[Click, (String, Int)] {
    var count = 0
    override def map(click: Click): (String, Int) = {
      count += 1
      (click.userId, count)
    }
  })

/**
 * 6. Join: Join two streams
 */
val clicksWithPurchases = clickStream
  .join(purchaseStream)
  .where(_.userId)
  .equalTo(_.userId)
  .window(TumblingEventTimeWindows.of(Time.minutes(5)))

/**
 * 7. Union: Combine streams
 */
val allEvents = mobileClicks.union(webClicks).union(apiClicks)
```

---

## Windowing: Time in Stream Processing

🏗️ **ARCHITECT**: Windowing is THE most important concept in stream processing.

### Why Windows?

```
Problem: How to aggregate UNBOUNDED stream?
───────────────────────────────────────────

Question: "Count clicks per page"
  Stream: [click1] [click2] [click3] ... (infinite)

  Naive approach:
    count = 0
    for click in stream:  # Never ends!
      count += 1
    return count  # Never reached!

Solution: WINDOWING
  Divide stream into finite chunks (windows)
  Aggregate within each window
  Emit result per window

Example: Count clicks per page per minute
  Window 1 (10:00-10:01): 100 clicks → emit (page, 100)
  Window 2 (10:01-10:02): 150 clicks → emit (page, 150)
  Window 3 (10:02-10:03): 80 clicks → emit (page, 80)
  ...

Now we can aggregate infinite stream!
```

### Window Types

```
1. TUMBLING WINDOW (Fixed, non-overlapping)
───────────────────────────────────────────

Size: 1 minute
Events assigned to exactly one window

Time:   10:00    10:01    10:02    10:03
        │        │        │        │
        ├────────┤        │        │
        │ Win 1  │        │        │
        │        ├────────┤        │
        │        │ Win 2  │        │
        │        │        ├────────┤
        │        │        │ Win 3  │
        └────────┴────────┴────────┘

Event at 10:00:30 → Window 1 only

Use case: "Count clicks per minute"


2. SLIDING WINDOW (Overlapping)
────────────────────────────────

Size: 2 minutes, Slide: 1 minute
Events assigned to multiple windows

Time:   10:00    10:01    10:02    10:03
        │        │        │        │
        ├─────────────────┤        │
        │     Win 1       │        │
        │        ├─────────────────┤
        │        │     Win 2       │
        │        │        ├─────────────────┤
        │        │        │     Win 3       │
        └────────┴────────┴────────┘

Event at 10:00:30 → Window 1 AND Window 2

Use case: "Average response time over last 2 minutes, updated every minute"


3. SESSION WINDOW (Dynamic, gap-based)
───────────────────────────────────────

Gap: 5 minutes of inactivity
Window closes after 5 min without events

Events: [e1] [e2]  ...5min gap...  [e3] [e4] [e5]
        │────┬────│                 │────┬────┬────│
        │ Session 1│                │  Session 2  │
        └──────────┘                └─────────────┘

Use case: "User session duration" (session ends after inactivity)


4. GLOBAL WINDOW (No time bounds)
──────────────────────────────────

Never closes automatically
Must use trigger for output

Events: [e1] [e2] [e3] [e4] [e5] ...
        │────────────────────────────
        │       Global Window
        └────────────────────────────

Trigger: Every 100 events, or every 5 minutes

Use case: Custom aggregation logic
```

### Window Implementation

```scala
import org.apache.flink.streaming.api.scala._
import org.apache.flink.streaming.api.windowing.time.Time
import org.apache.flink.streaming.api.windowing.assigners._

/**
 * 1. Tumbling window: Count clicks per page per minute
 */
val clicksPerMinute = clickStream
  .keyBy(_.page)
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .reduce((c1, c2) => Click(c1.page, c1.count + c2.count))

/**
 * 2. Sliding window: Average load over last 5 minutes, updated every minute
 */
val avgLoad = metricsStream
  .keyBy(_.server)
  .window(SlidingEventTimeWindows.of(Time.minutes(5), Time.minutes(1)))
  .aggregate(new AverageAggregate)

/**
 * 3. Session window: User session duration
 */
val sessionDuration = clickStream
  .keyBy(_.userId)
  .window(EventTimeSessionWindows.withGap(Time.minutes(30)))
  .process(new SessionDurationProcessFunction)

/**
 * 4. Global window with trigger: Emit every 100 events
 */
val everyHundred = clickStream
  .keyBy(_.page)
  .window(GlobalWindows.create())
  .trigger(CountTrigger.of(100))
  .reduce((c1, c2) => Click(c1.page, c1.count + c2.count))

/**
 * 5. Custom window: Emit at specific times
 */
val customWindow = clickStream
  .keyBy(_.page)
  .window(TumblingEventTimeWindows.of(Time.hours(1)))
  .trigger(new CustomTrigger)  // Emit at :00, :15, :30, :45
  .reduce((c1, c2) => Click(c1.page, c1.count + c2.count))
```

---

## Stateful Stream Processing

🏗️ **ARCHITECT**: State management is critical for real-world stream processing.

### Why State?

```
Stateless operations (easy):
────────────────────────────
  map, filter
  Each event processed independently
  No memory of past events

Example:
  filter(event => event.amount > 100)
  Each event checked independently

Stateful operations (complex but powerful):
───────────────────────────────────────────
  count, sum, average, join
  Must remember past events
  State maintained across events

Example:
  Count clicks per user
    State: { user_123: 5, user_456: 10, user_789: 3 }
    Event: user_123 clicks
    Update state: { user_123: 6, ... }
    Output: (user_123, 6)

Why state is hard in distributed systems:
  - State must be distributed across nodes
  - State must survive failures (checkpointing)
  - State can grow large (need cleanup/TTL)
```

### State Types

```scala
/**
 * Different types of state in Flink
 */

import org.apache.flink.api.common.state._

/**
 * 1. ValueState: Single value per key
 */
class CountFunction extends RichFlatMapFunction[Event, (String, Long)] {
  var count: ValueState[Long] = _

  override def open(parameters: Configuration): Unit = {
    val descriptor = new ValueStateDescriptor[Long]("count", classOf[Long])
    count = getRuntimeContext.getState(descriptor)
  }

  override def flatMap(event: Event, out: Collector[(String, Long)]): Unit = {
    val currentCount = Option(count.value()).getOrElse(0L)
    val newCount = currentCount + 1
    count.update(newCount)
    out.collect((event.userId, newCount))
  }
}

/**
 * 2. ListState: List of values per key
 */
class RecentEventsFunction extends ProcessFunction[Event, Seq[Event]] {
  var recentEvents: ListState[Event] = _

  override def open(parameters: Configuration): Unit = {
    val descriptor = new ListStateDescriptor[Event]("recent", classOf[Event])
    recentEvents = getRuntimeContext.getListState(descriptor)
  }

  override def processElement(event: Event, ctx: Context, out: Collector[Seq[Event]]): Unit = {
    // Add new event
    recentEvents.add(event)

    // Keep only last 10
    val events = recentEvents.get().asScala.toSeq
    if (events.size > 10) {
      recentEvents.clear()
      events.takeRight(10).foreach(recentEvents.add)
    }

    out.collect(events)
  }
}

/**
 * 3. MapState: Map per key
 */
class PageViewsFunction extends ProcessFunction[Click, Map[String, Long]] {
  var pageViews: MapState[String, Long] = _

  override def open(parameters: Configuration): Unit = {
    val descriptor = new MapStateDescriptor[String, Long]("pages", classOf[String], classOf[Long])
    pageViews = getRuntimeContext.getMapState(descriptor)
  }

  override def processElement(click: Click, ctx: Context, out: Collector[Map[String, Long]]): Unit = {
    val currentCount = Option(pageViews.get(click.page)).getOrElse(0L)
    pageViews.put(click.page, currentCount + 1)

    // Output all page views
    val allViews = pageViews.entries().asScala.map(e => e.getKey -> e.getValue).toMap
    out.collect(allViews)
  }
}

/**
 * 4. ReducingState: Accumulate with reduce function
 */
class SumFunction extends ProcessFunction[Metric, Long] {
  var sum: ReducingState[Long] = _

  override def open(parameters: Configuration): Unit = {
    val descriptor = new ReducingStateDescriptor[Long](
      "sum",
      (a: Long, b: Long) => a + b,
      classOf[Long]
    )
    sum = getRuntimeContext.getReducingState(descriptor)
  }

  override def processElement(metric: Metric, ctx: Context, out: Collector[Long]): Unit = {
    sum.add(metric.value)
    out.collect(sum.get())
  }
}
```

### State Backends & Checkpointing

```
State Backends: Where state is stored
─────────────────────────────────────

1. MemoryStateBackend
   - State in Java heap
   - Fast but limited by memory
   - Lost on failure (not durable)
   - Use: Development, testing

2. FsStateBackend (FileSystem)
   - State in heap, checkpoint to filesystem (HDFS, S3)
   - Fast access, durable checkpoints
   - Use: Production (moderate state)

3. RocksDBStateBackend
   - State in RocksDB (disk-based)
   - Checkpoint to filesystem
   - Can handle very large state (TBs)
   - Slightly slower but scalable
   - Use: Production (large state)

Checkpointing: Fault tolerance
───────────────────────────────

Checkpoint = Snapshot of all state
  - Periodic (e.g., every 1 minute)
  - Saved to durable storage (HDFS, S3)
  - On failure: Restore from last checkpoint

Example:
  t=0:    Start processing, state = {}
  t=10:   State = {user1: 5, user2: 3}
  t=60:   Checkpoint! Save state to HDFS
  t=90:   State = {user1: 10, user2: 7}
  t=95:   CRASH! 💥
  t=100:  Restart, restore from checkpoint (t=60)
          State = {user1: 5, user2: 3}
          Replay events from t=60 to t=95
          State = {user1: 10, user2: 7}  ← Back to consistent state!

Configuration:
  env.enableCheckpointing(60000)  // Checkpoint every 60 seconds
  env.getCheckpointConfig.setMinPauseBetweenCheckpoints(30000)
  env.setStateBackend(new RocksDBStateBackend("s3://bucket/checkpoints"))
```

---

## Event Time vs Processing Time

🎓 **PROFESSOR**: Understanding time is CRITICAL in stream processing.

### The Two Times

```
Event Time: When event occurred (in real world)
Processing Time: When event processed (in system)

Example: Temperature sensor
───────────────────────────

Event Time:      10:00:00 (sensor records temp)
                    ↓
                 [Network delay]
                    ↓
Processing Time: 10:00:05 (system receives event)

In perfect world: Event Time = Processing Time
In real world: Event Time ≠ Processing Time (due to delays)

Causes of delay:
  - Network latency
  - System backlog (high load)
  - Clock skew
  - Retries
  - Late-arriving data

Visual:
  Event Time ─────────────────────────────→
  10:00        10:01        10:02        10:03

  Event e1 created at 10:00:10
      ↓ (5 sec delay)
  Processed at 10:00:15

  Event e2 created at 10:00:30
      ↓ (1 min delay - network issue)
  Processed at 10:01:30  ← Out of order!

  Events arrive out-of-order!
```

### Watermarks

```
Watermark: System's estimate of event time progress
───────────────────────────────────────────────────

Definition:
  "All events with timestamp < watermark have arrived"
  (Probably - not guaranteed!)

Example:
  Watermark = 10:00:00
  → Expect no more events with timestamp < 10:00:00

Purpose:
  Tell windowing when to close window
  Window [9:59:00, 10:00:00) closes when watermark reaches 10:00:00

Visual:
  Event Time ─────────────────────────────→
  9:59         10:00        10:01

  Events: [e1: 9:59:10] [e2: 9:59:50] [e3: 10:00:05]

  Watermark progression:
    t=0:    Watermark = 0
    t=10:   e1 arrives (9:59:10) → Watermark = 9:59:00 (10 sec lag)
    t=55:   e2 arrives (9:59:50) → Watermark = 9:59:40 (10 sec lag)
    t=70:   Watermark reaches 10:00:00 → Close window [9:59:00, 10:00:00)
    t=75:   e3 arrives (10:00:05) → Watermark = 9:59:55 (10 sec lag)

Watermark strategies:
  1. Periodic: Generate every N seconds
  2. Punctuated: Generate after certain events
  3. Fixed delay: Watermark = max_event_time - delay

Late data:
  Event arrives after watermark passed
  Example: Event timestamp 9:59:45 arrives after watermark 10:00:00
  Options:
    - Drop (ignore late data)
    - Recompute window (update result)
    - Side output (process separately)
```

### Event Time Example

```scala
import org.apache.flink.streaming.api.TimeCharacteristic
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor
import org.apache.flink.streaming.api.windowing.time.Time

/**
 * Event-time processing with watermarks
 */
// Enable event time
env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime)

// Define timestamp extractor and watermark generator
val clicksWithTimestamps = clickStream
  .assignTimestampsAndWatermarks(
    new BoundedOutOfOrdernessTimestampExtractor[Click](Time.seconds(10)) {
      override def extractTimestamp(click: Click): Long = {
        click.timestamp  // Use event timestamp
      }
    }
  )

// Window by event time (not processing time!)
val clicksPerMinute = clicksWithTimestamps
  .keyBy(_.page)
  .window(TumblingEventTimeWindows.of(Time.minutes(1)))
  .allowedLateness(Time.seconds(30))  // Accept late data for 30 sec
  .sideOutputLateData(lateDataTag)    // Capture very late data
  .reduce((c1, c2) => Click(c1.page, c1.count + c2.count))

/**
 * How it works:
 *
 * 1. Events arrive (possibly out-of-order)
 *    [e1: 10:00:10] [e3: 10:00:40] [e2: 10:00:25]  ← Out of order!
 *
 * 2. Watermark tracks progress
 *    Watermark = max_event_time - 10 seconds
 *    After e3: Watermark = 10:00:30
 *
 * 3. Window [10:00:00, 10:01:00) closes when watermark reaches 10:01:00
 *
 * 4. Late data (within 30 sec) triggers recomputation
 *    Event at 10:00:55 arrives at 10:01:20 → Updates window
 *
 * 5. Very late data (> 30 sec) goes to side output
 *    Event at 10:00:55 arrives at 10:01:40 → Side output
 */
```

---

## Guarantees & Semantics

🏗️ **ARCHITECT**: Understanding delivery guarantees is critical for correctness.

### Delivery Semantics

```
Three types of delivery guarantees:
───────────────────────────────────

1. AT-MOST-ONCE
   ───────────────
   Each event processed 0 or 1 times
   Events can be lost (no reprocessing on failure)

   Use case: OK to lose data (e.g., metrics, logs)
   Example: Temperature readings (lose one, not critical)

   Implementation: Fire and forget, no checkpointing

2. AT-LEAST-ONCE
   ───────────────
   Each event processed 1 or more times
   No data loss (retry on failure)
   But: Duplicates possible

   Use case: OK to process duplicates (idempotent operations)
   Example: Increment counter (if idempotent: set to value)

   Implementation: Retry + checkpointing

3. EXACTLY-ONCE
   ──────────────
   Each event processed exactly 1 time
   No data loss, no duplicates

   Use case: Critical operations (payments, financial)
   Example: Money transfer (MUST NOT duplicate!)

   Implementation: Distributed transactions, 2PC, or idempotent sinks

Visual:
  Event e1 → Processing → Output
             ↓ CRASH!

  At-most-once:  Don't retry → Event lost
  At-least-once: Retry → Event processed 2x (duplicate output)
  Exactly-once:  Retry + Deduplication → Event processed 1x

Cost:
  At-most-once:  Fastest (no overhead)
  At-least-once: Fast (checkpoint overhead)
  Exactly-once:  Slowest (transaction overhead)
```

### Exactly-Once in Practice

```scala
/**
 * Exactly-once with Kafka + Flink
 */

// Enable exactly-once checkpointing
env.enableCheckpointing(60000, CheckpointingMode.EXACTLY_ONCE)

// Kafka source with exactly-once
val kafkaSource = new FlinkKafkaConsumer[Event](
  "input-topic",
  new EventDeserializationSchema(),
  kafkaProps
)
kafkaSource.setCommitOffsetsOnCheckpoints(true)  // Commit on checkpoint

val events = env.addSource(kafkaSource)

// Process events
val results = events
  .keyBy(_.userId)
  .map(new ProcessFunction)

// Kafka sink with exactly-once
val kafkaSink = new FlinkKafkaProducer[Result](
  "output-topic",
  new ResultSerializationSchema(),
  kafkaProps,
  FlinkKafkaProducer.Semantic.EXACTLY_ONCE  // Exactly-once guarantee!
)

results.addSink(kafkaSink)

/**
 * How exactly-once works:
 *
 * 1. Checkpoint triggered
 *    - Flink pauses processing
 *    - Saves state snapshot
 *    - Records Kafka offsets
 *
 * 2. State + offsets saved to durable storage (HDFS, S3)
 *
 * 3. On failure:
 *    - Restore state from checkpoint
 *    - Rewind Kafka to saved offsets
 *    - Replay events
 *
 * 4. Output to Kafka:
 *    - Use Kafka transactions
 *    - Commit output atomically with checkpoint
 *    - If checkpoint fails, Kafka transaction aborted
 *    - Result: No duplicate outputs!
 *
 * Requirements:
 *   - Kafka 0.11+ (transactions support)
 *   - Idempotent operations OR transactional sinks
 *   - Checkpointing enabled
 */
```

---

## Popular Stream Processing Frameworks

🎓 **PROFESSOR**: Comparing the major players.

### Framework Comparison

```
┌─────────────────────────────────────────────────────────────┐
│            STREAM PROCESSING FRAMEWORKS                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│ APACHE FLINK                                                │
│ ─────────────                                               │
│ + True streaming (event-at-a-time)                          │
│ + Best-in-class exactly-once semantics                      │
│ + Advanced windowing support                                │
│ + Stateful processing with RocksDB                          │
│ + Low latency (milliseconds)                                │
│ - Steeper learning curve                                    │
│ - Smaller ecosystem than Spark                              │
│ Use: Real-time analytics, event-driven apps                 │
│                                                              │
│ APACHE SPARK STREAMING (Structured Streaming)               │
│ ──────────────────────────────────────────                  │
│ + Unified API (batch + streaming)                           │
│ + Mature ecosystem (Spark SQL, MLlib)                       │
│ + Exactly-once semantics                                    │
│ + Easy to learn (if know Spark)                             │
│ - Micro-batching (higher latency than Flink)                │
│ - Less flexible windowing                                   │
│ Use: Teams already using Spark, moderate latency ok         │
│                                                              │
│ APACHE KAFKA STREAMS                                        │
│ ──────────────────────                                      │
│ + Lightweight (library, not framework)                      │
│ + Tight Kafka integration                                   │
│ + Exactly-once with Kafka                                   │
│ + Easy deployment (no cluster)                              │
│ - Kafka-only (can't read from other sources)                │
│ - Less powerful than Flink/Spark                            │
│ Use: Kafka-centric architectures, microservices             │
│                                                              │
│ APACHE STORM                                                │
│ ─────────────                                               │
│ + Low latency (sub-second)                                  │
│ + At-least-once semantics (Trident for exactly-once)        │
│ - Older technology (less active development)                │
│ - More complex than modern frameworks                       │
│ Use: Legacy systems, low-latency requirements               │
│                                                              │
│ APACHE SAMZA                                                │
│ ─────────────                                               │
│ + Integrates with YARN, Kafka                               │
│ + Stateful processing                                       │
│ - Less popular than others                                  │
│ Use: LinkedIn (creator), YARN environments                  │
│                                                              │
│ AWS KINESIS DATA ANALYTICS                                  │
│ ───────────────────────────                                 │
│ + Serverless (no infrastructure)                            │
│ + SQL interface (easy)                                      │
│ + AWS integration                                           │
│ - AWS lock-in                                               │
│ - Limited functionality                                     │
│ Use: AWS environments, simple use cases                     │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### When to Use Each

```python
def choose_framework(requirements):
    """
    Framework selection guide
    """

    # Low latency (< 100ms)
    if requirements.latency < 100:
        return "Flink (true streaming)"

    # Moderate latency (1-10 seconds)
    if requirements.latency < 10:
        return "Spark Streaming (micro-batches ok)"

    # Already using Spark
    if requirements.has_spark_jobs:
        return "Spark Streaming (unified platform)"

    # Kafka-only pipeline
    if requirements.all_data_in_kafka:
        return "Kafka Streams (lightweight)"

    # Complex stateful processing
    if requirements.needs_large_state:
        return "Flink (RocksDB state backend)"

    # Simple transformations
    if requirements.complexity == "low":
        return "Kafka Streams (simplest)"

    # AWS environment
    if requirements.cloud == "aws" and requirements.complexity == "low":
        return "Kinesis Data Analytics (serverless)"

    # Default
    return "Flink (most powerful)"

"""
Real-world adoption:
───────────────────

Flink:
  - Uber (real-time ML features)
  - Netflix (streaming ETL)
  - Alibaba (real-time recommendations)

Spark Streaming:
  - Pinterest (event processing)
  - Verizon (log aggregation)
  - ING Bank (fraud detection)

Kafka Streams:
  - LinkedIn (activity tracking)
  - Zalando (inventory management)
  - New York Times (content publishing)
"""
```

---

## When to Use Stream Processing

🏗️ **ARCHITECT**: Decision framework for stream vs batch.

### Use Stream Processing When:

```
✓ Need low latency (seconds, not hours)
  Example: Fraud detection, alerting

✓ Data arrives continuously
  Example: Sensors, logs, clicks

✓ Need real-time insights
  Example: Live dashboards, monitoring

✓ Time-sensitive decisions
  Example: Trading, bidding, recommendations

✓ Event-driven architecture
  Example: Microservices, reactive systems

Specific use cases:
──────────────────

1. Fraud Detection
   - Analyze transactions in real-time
   - Block suspicious activity immediately
   - Latency: < 100ms

2. Real-Time Analytics
   - Live dashboards (website metrics)
   - IoT monitoring (sensor data)
   - Latency: 1-10 seconds

3. Alerting & Monitoring
   - Detect anomalies (server down, spike in errors)
   - Send notifications immediately
   - Latency: < 1 minute

4. Personalization
   - Update recommendations based on latest clicks
   - Show relevant content
   - Latency: < 1 second

5. ETL / Data Pipelines
   - Continuous data integration
   - Transform and load as data arrives
   - Latency: 1-60 seconds

6. Stream Joins
   - Enrich events with reference data
   - Join multiple streams
   - Latency: < 1 minute
```

### Don't Use Stream Processing When:

```
✗ Latency > 1 hour is acceptable
  → Use batch processing (cheaper)

✗ Data is bounded (finite dataset)
  → Use batch processing (simpler)

✗ Complex joins on large datasets
  → Use batch processing (more efficient)

✗ Need to reprocess historical data frequently
  → Use batch processing (easier to backfill)

✗ Limited budget
  → Use batch processing (pay per job, not 24/7)

Examples:
─────────

Use Batch, Not Stream:
  - Monthly financial reports
  - ML model training on historical data
  - Large-scale data migrations
  - Ad-hoc analytics queries
  - Data warehouse ETL (nightly)
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Using Stream Processing in System Design

```
Scenario: "Design a real-time fraud detection system"
─────────────────────────────────────────────────────

Requirements:
  - Process credit card transactions in real-time
  - Detect fraudulent patterns
  - Block suspicious transactions (<100ms)
  - 10,000 transactions/second

Architecture:
  ┌────────────────────────────────────────────────┐
  │  Credit Card Swipe → Kafka (transactions)      │
  │                           ↓                    │
  │          Flink Stream Processing               │
  │  ┌──────────────────────────────────────────┐ │
  │  │ 1. Enrich with user profile              │ │
  │  │ 2. Extract features (amount, location)   │ │
  │  │ 3. Check rules (velocity, anomaly)       │ │
  │  │ 4. Score with ML model                   │ │
  │  │ 5. Decide: Allow/Block/Review            │ │
  │  └──────────────────────────────────────────┘ │
  │                           ↓                    │
  │      Decision → Kafka → Payment Gateway       │
  │                           ↓                    │
  │        (APPROVE / DENY in <100ms)             │
  └────────────────────────────────────────────────┘

Why Stream Processing?
  ✓ Need <100ms latency (batch too slow)
  ✓ Continuous data flow (transactions 24/7)
  ✓ Stateful (track velocity: txns per user per hour)
  ✓ Real-time decision required

Why Flink?
  ✓ Low latency (event-at-a-time, not micro-batches)
  ✓ Exactly-once semantics (financial transactions!)
  ✓ Stateful processing (maintain user transaction history)
  ✓ Advanced windowing (velocity checks)

Deep Dive:
  Q: How to handle high traffic (100K txns/sec)?
  A: - Partition by user_id (parallel processing)
     - Scale Flink cluster (more task managers)
     - Use Kafka partitioning for load distribution

  Q: How to ensure exactly-once processing?
  A: - Enable Flink checkpointing (every 10 sec)
     - Use Kafka transactions for output
     - Idempotent fraud checks (same input → same output)

  Q: How to update fraud detection model?
  A: - Train model offline (batch job on historical data)
     - Deploy new model without downtime (versioning)
     - A/B test new model (route % of traffic)

  Q: What about false positives?
  A: - Stream processing flags suspicious transactions
     - Human review queue for borderline cases
     - Feedback loop to improve model
```

---

## 🧠 MIND MAP: STREAM PROCESSING

```
   STREAM PROCESSING
          |
    ┌─────┼─────┐
    ↓     ↓     ↓
  TIME  STATE WINDOWS
    |     |      |
 Event  Stateful Tumbling
  Time   Ops    Sliding
    |     |    Session
Watermark Checkpt
```

---

## 💡 EMOTIONAL ANCHORS

### 1. Stream = River 🌊
- Batch: Lake (finite, process all at once)
- Stream: River (infinite, flows continuously)
- Can't wait for river to stop (unbounded)
- Process water as it flows (events)

### 2. Window = Time Bucket ⏰
- River of events flows by
- Catch events in bucket for 1 minute
- Process bucket when full
- Start new bucket for next minute

### 3. State = Running Tally 📊
- Stateless: Each person counts independently
- Stateful: One person keeps running total
- Remembers all previous counts
- Must save tally (checkpoint) in case person leaves

### 4. Watermark = "No More Events!" Flag 🚩
- Events arrive out of order (like mail)
- Watermark: "All mail before Monday arrived"
- Safe to process Monday's mail now
- Late mail (Tuesday arrives later) → Side pile

### 5. Exactly-Once = Bank Transfer 💰
- At-most-once: Money might disappear (lost)
- At-least-once: Money might duplicate (sent twice)
- Exactly-once: Money transfers exactly once (correct!)
- Need for financial operations

### 6. Event Time vs Processing Time = Timestamp vs Clock ⏰
- Event time: When photo taken (timestamp on photo)
- Processing time: When photo uploaded (clock on server)
- Photos uploaded out of order (old photo uploaded late)
- Sort by timestamp (event time), not upload time (processing time)

---

## 🔑 Key Takeaways

1. **Stream processing = real-time processing of unbounded data**
   - Continuous flow of events
   - Process each event as it arrives
   - Low latency (seconds, not hours)

2. **Batch vs Stream trade-offs**
   - Batch: High latency, high throughput, cheaper
   - Stream: Low latency, moderate throughput, expensive
   - Choose based on latency requirements

3. **Windowing is fundamental**
   - Divide unbounded stream into finite chunks
   - Types: Tumbling, Sliding, Session
   - Required for aggregations on infinite streams

4. **State management is critical**
   - Maintain state across events (counts, sessions)
   - Checkpoint state for fault tolerance
   - Use RocksDB for large state

5. **Event time vs Processing time**
   - Event time: When event occurred (real world)
   - Processing time: When processed (system time)
   - Use event time for correctness
   - Watermarks track event time progress

6. **Delivery semantics matter**
   - At-most-once: Fast but lossy
   - At-least-once: No loss but duplicates
   - Exactly-once: Correct but expensive
   - Choose based on use case

7. **Flink vs Spark Streaming**
   - Flink: True streaming, lower latency, more complex
   - Spark: Micro-batches, higher latency, simpler
   - Kafka Streams: Lightweight, Kafka-only

8. **Use stream processing when**
   - Need low latency (< 1 minute)
   - Data arrives continuously
   - Real-time insights required
   - Event-driven architecture

9. **Challenges in stream processing**
   - Out-of-order events (watermarks)
   - State management (checkpointing)
   - Scalability (parallelism)
   - Exactly-once semantics (transactions)

10. **Stream processing is mainstream**
    - Industry standard for real-time systems
    - Powers recommendation engines, fraud detection, monitoring
    - Critical skill for data engineers

---

**Final Thought**: Stream processing is the paradigm shift from "process data when you have time" (batch) to "process data as it happens" (stream). Understanding windowing, state, and time semantics is critical. Master these concepts, and you can build real-time systems that react to events within milliseconds - the foundation of modern data-driven applications.
