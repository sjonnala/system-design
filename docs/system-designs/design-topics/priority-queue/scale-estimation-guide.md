# 🎯 Priority Queue System: Scale Estimation Masterclass

## The QUEUES Technique for Message System Scale Math
**(Q)ueue depth → (U)sage patterns → (E)stimate throughput → (U)nderstand storage → (E)valuate latency → (S)ize infrastructure**

This is a **mental framework** specifically for distributed messaging systems.

---

## 📊 PART 1: Message System Fundamentals

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Message Volume** | Messages enqueued/day | 240M | Typical e-commerce platform scale |
| | Messages dequeued/day | 240M | Balanced queue (steady state) |
| | Peak multiplier | 3x | Black Friday, flash sales |
| | Average message size | 10 KB | Order data, notifications |
| **Queue Behavior** | Priority levels | 10 | Granular priority control (1-10) |
| | Priority distribution | 80-20 rule | 80% medium, 15% high, 5% critical |
| | Messages in-flight | 5% of daily | Processing latency ~5 sec |
| | Retention period | 7 days | Compliance, debugging |
| **Processing** | Avg processing time | 5 seconds | Business logic execution |
| | Visibility timeout | 30 seconds | Re-delivery window |
| | Max receive count | 5 | DLQ threshold |
| **Consumer Patterns** | Consumer count | 100-500 workers | Auto-scaling based on queue depth |
| | Polling frequency | 1 req/sec | Long polling (20s wait) |

---

## 🧮 PART 2: Message System Calculator - Mental Math Toolkit

### Rule #1: **Messages Per Second Conversion**
```
Remember: 1 day = 100K seconds (actually 86,400, but round for simplicity)

Daily messages ÷ 100K = Messages per second (average)
Average × 3 = Peak messages per second

Example:
240M messages/day ÷ 100K = 2,400 msg/sec (average)
2,400 × 3 = 7,200 msg/sec (peak, e.g., Black Friday)
```

### Rule #2: **Queue Depth Calculation**
```
Queue Depth = Enqueue Rate - Dequeue Rate (if unbalanced)

Steady state (balanced):
  Enqueue Rate ≈ Dequeue Rate
  Queue Depth = Messages in-flight only

Backlog scenario:
  Enqueue > Dequeue
  Queue Depth grows → Need more consumers
```

### Rule #3: **Storage for Messages**
```
Storage = Messages × Message Size × Retention Days

Don't forget:
- Metadata overhead: +20% (timestamps, IDs, status)
- Indexes: +15% (priority, queue_name, status indexes)
- Replication factor: ×2 (primary + replica)
```

---

## 📈 PART 3: Priority Queue Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│    PRIORITY QUEUE NAPKIN MATH - Universal Template      │
└─────────────────────────────────────────────────────────┘

STEP 1: MESSAGE THROUGHPUT
───────────────────────────
Messages Enqueued/Day:   [____] M
Messages Dequeued/Day:   [____] M
Priority Distribution:
  - Critical (10):       [____] %
  - High (7-9):          [____] %
  - Medium (4-6):        [____] %
  - Low (1-3):           [____] %

→ Enqueue Rate = Messages/Day ÷ 100K  = [____] msg/sec
→ Dequeue Rate = Messages/Day ÷ 100K  = [____] msg/sec
→ Peak Rate    = Average × 3           = [____] msg/sec

STEP 2: QUEUE DEPTH ESTIMATION
───────────────────────────
Processing Time per Message: [____] sec
Concurrent Messages:         [____] M

→ In-Flight Messages = Peak Rate × Processing Time = [____]
→ Backlog (if any)   = Enqueue Rate - Dequeue Rate = [____]
→ Total Queue Depth  = In-Flight + Backlog         = [____]

STEP 3: STORAGE ESTIMATION
───────────────────────────
Message Size:          [____] KB
Metadata Overhead:     20% (timestamps, IDs, status)
Retention Period:      [____] days

→ Daily Raw Storage = Messages/Day × Size         = [____] TB
→ With Overhead     = Daily × 1.35 (metadata+indexes) = [____] TB
→ With Replication  = Daily × 2 (primary+replica) = [____] TB
→ Total Storage     = Daily × Retention × 2       = [____] TB

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Hot Data (Redis):
  - In-flight messages: [____] M
  - Priority queues:    10 sorted sets per queue
  - Metadata:           20% overhead

→ Redis Memory = In-Flight × Size × 1.2 = [____] GB

STEP 5: CONSUMER SIZING
───────────────────────────
Processing Time:       [____] sec
Target Throughput:     [____] msg/sec

→ Consumers Needed = Throughput × Processing Time = [____]
→ With Headroom    = Consumers × 1.5 (for spikes)  = [____]

STEP 6: BANDWIDTH ESTIMATION
───────────────────────────
→ Enqueue Bandwidth = Enqueue Rate × Message Size = [____] MB/s
→ Dequeue Bandwidth = Dequeue Rate × Message Size = [____] MB/s
→ Total Bandwidth   = Enqueue + Dequeue           = [____] MB/s
```

---

## 💾 PART 4: Priority Queue Filled Template

```
┌─────────────────────────────────────────────────────────┐
│      PRIORITY QUEUE SYSTEM - NAPKIN MATH SOLUTION       │
└─────────────────────────────────────────────────────────┘

STEP 1: MESSAGE THROUGHPUT
───────────────────────────
Messages Enqueued/Day:   240 M (orders, notifications, jobs)
Messages Dequeued/Day:   240 M (balanced queue)
Priority Distribution:
  - Critical (10):       5% (12M msgs, payment failures)
  - High (7-9):          15% (36M msgs, new orders)
  - Medium (4-6):        60% (144M msgs, emails, updates)
  - Low (1-3):           20% (48M msgs, analytics, batch jobs)

→ Enqueue Rate = 240M ÷ 100K     = 2,400 msg/sec
→ Dequeue Rate = 240M ÷ 100K     = 2,400 msg/sec
→ Peak Rate    = 2,400 × 3       = 7,200 msg/sec (Black Friday)

STEP 2: QUEUE DEPTH ESTIMATION
───────────────────────────
Processing Time per Message: 5 sec (avg business logic)
Visibility Timeout:          30 sec

→ In-Flight Messages = 7,200 msg/s × 5s    = 36,000 messages
→ Backlog (steady state)                   = 0 (balanced)
→ Safety Buffer (20%)                      = 7,200 messages
→ Total Queue Depth                        = ~43,000 messages

STEP 3: STORAGE ESTIMATION
───────────────────────────
Message Size:          10 KB (payload + metadata)
Retention Period:      7 days

→ Daily Raw Storage = 240M × 10 KB         = 2.4 TB/day
→ Metadata Overhead = 2.4TB × 1.35         = 3.24 TB/day
→ With Replication  = 3.24TB × 2           = 6.48 TB/day
→ 7-Day Storage     = 6.48TB × 7           = 45.36 TB
→ Round to          = ~50 TB (for 7-day retention)

Breakdown:
  - PostgreSQL: 50 TB (durable storage, partitioned by priority)
  - S3 Archive:  Additional 100 TB (older messages, 30+ days)

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Hot Data (Redis):
  - In-flight messages: 36,000 × 10 KB      = 360 MB
  - Priority queues:    10 levels × 100 queues = 1,000 sorted sets
  - Metadata overhead:  20%
  - Working set:        Last 1 hour of messages

→ Redis Memory (in-flight) = 360 MB × 1.2  = 432 MB
→ Redis Memory (hot queues) = 1 hour worth
   = 2,400 msg/s × 3600s × 10KB × 1.2      = 103 GB
→ Total Redis Memory                       = ~105 GB
→ Practical Redis Cluster (3 masters)      = 128 GB (64 GB × 2 for headroom)

STEP 5: CONSUMER SIZING
───────────────────────────
Processing Time:       5 sec
Target Throughput:     7,200 msg/sec (peak)

→ Consumers Needed = 7,200 × 5            = 36,000 concurrent
   ❌ This is unrealistic! Apply batching:

With Batch Processing (10 messages/batch):
→ Batches/sec = 7,200 ÷ 10                = 720 batches/sec
→ Consumers = 720 × 5                     = 3,600 consumers
→ With Headroom (50%)                     = 5,400 consumers

Auto-Scaling Policy:
  - Min: 1,000 consumers (baseline)
  - Max: 10,000 consumers (extreme peak)
  - Scale up: Queue depth > 10,000
  - Scale down: Queue depth < 1,000

STEP 6: BANDWIDTH ESTIMATION
───────────────────────────
→ Enqueue Bandwidth = 2,400 msg/s × 10 KB = 24 MB/s  (~200 Mbps)
→ Dequeue Bandwidth = 2,400 msg/s × 10 KB = 24 MB/s  (~200 Mbps)
→ Total Bandwidth   = 48 MB/s              (~400 Mbps)
→ Peak Bandwidth    = 48 × 3               = 144 MB/s (~1.2 Gbps)
```

---

## 🧠 PART 5: Mental Math Techniques for Message Systems

### **Technique 1: The "Queue Depth Rule of Thumb"**
```
Healthy Queue Depth = Processing Rate × Processing Time

Example:
  Processing Rate = 2,400 msg/sec
  Processing Time = 5 sec
  Healthy Depth   = 2,400 × 5 = 12,000 messages

If queue depth > 100,000 → System is backed up, scale consumers!
If queue depth < 100     → System is idle, scale down.
```

### **Technique 2: The "Priority Split"**
```
Use 80-20 rule for priority distribution:
- 80% of messages are medium priority (routine operations)
- 15% are high priority (important, time-sensitive)
- 5% are critical (payments, fraud alerts)

This helps size separate consumer pools:
  Critical consumers:  5% × Total Consumers
  High consumers:     15% × Total Consumers
  Medium consumers:   80% × Total Consumers
```

### **Technique 3: The "Visibility Timeout Multiplier"**
```
In-flight messages grow with visibility timeout:

Short timeout (10s):  Fewer in-flight, more retries
Long timeout (300s):  More in-flight, fewer retries

Optimal Visibility Timeout = Avg Processing Time × 2

Why? Allows for retries without excessive in-flight messages.
```

### **Technique 4: The "DLQ Percentage"**
```
Healthy system: DLQ < 0.1% of total messages
Warning:        DLQ = 0.1-1%
Critical:       DLQ > 1%

Daily Messages: 240M
Expected DLQ:   <240K messages (0.1%)

If DLQ > 2.4M (1%) → Investigate root cause immediately!
```

---

## 🎨 PART 6: The Visual Mind Map Approach

```
                🔄 PRIORITY QUEUE SYSTEM
                          |
        ┌─────────────────┼─────────────────────┐
        |                 |                     |
    📊 THROUGHPUT     💾 STORAGE          🖥️ COMPUTE
        |                 |                     |
   ┌────┴────┐      ┌─────┴─────┐         ┌────┴────┐
 Enqueue Dequeue  PostgreSQL Redis     Consumers  Cache
  2.4K/s  2.4K/s     50TB      105GB     1K-10K   128GB
```

**Memory Trigger**: Think **"T.S.C."** = Throughput, Storage, Compute

---

## 🏗️ PART 7: Message Schema Impact on Scale

```python
# EXAMPLE: Order Processing Message

@dataclass
class OrderMessage:
    # Core fields (always present)
    message_id: UUID          # 16 bytes
    queue_name: str           # 50 bytes avg
    priority: int             # 1 byte
    created_at: datetime      # 8 bytes

    # Payload (variable)
    payload: Dict            # 5-50 KB typical

    # Metadata (processing)
    receive_count: int       # 1 byte
    visible_at: datetime     # 8 bytes
    consumer_id: str         # 50 bytes

    # Total: ~100 bytes overhead + payload size

# Scale Impact:
# Small messages (1 KB payload): 1.1 KB total → Can handle 10M/sec with 11 GB/s bandwidth
# Large messages (100 KB payload): 100.1 KB → Can handle 100K/sec with 10 GB/s bandwidth

# Recommendation: Store large payloads (>256 KB) in S3, keep reference in message
```

---

## 🎯 PART 8: The Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│  PRIORITY QUEUE SCALE ESTIMATION - 5 MIN RITUAL  │
└──────────────────────────────────────────────────┘

[ ] 1. Messages/day, Priority distribution, Message size
[ ] 2. Calculate msg/sec: Messages/day ÷ 100K
[ ] 3. Queue depth: Rate × Processing time
[ ] 4. Storage: Messages × Size × Retention × 2 (replication)
[ ] 5. Consumers: Throughput × Processing time ÷ Batch size
[ ] 6. Bandwidth: Rate × Message size
[ ] 7. Smell test: Can 1K consumers handle 2.4K msg/s? YES!
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Enqueue Rate** | 2,400 msg/sec | API gateway sizing, rate limits |
| **Dequeue Rate** | 2,400 msg/sec | Consumer pool sizing |
| **Peak Rate** | 7,200 msg/sec | Infrastructure headroom |
| **Queue Depth** | 12K-43K msgs | Redis memory, alert thresholds |
| **Storage (7d)** | 50 TB | PostgreSQL disk sizing |
| **Redis Memory** | 128 GB | Cluster sizing (3 masters × 64 GB) |
| **Consumers** | 1K-10K workers | Kubernetes auto-scaling limits |
| **Bandwidth** | 400 Mbps-1.2 Gbps | Network capacity planning |
| **DLQ Rate** | <0.1% | System health indicator |

---

## 💡 Pro Architect Tips

### **Tip 1: The Queue Depth Alert Strategy**
```
Green:  Queue depth < 10K    → System healthy
Yellow: Queue depth 10K-50K  → Monitor closely
Orange: Queue depth 50K-100K → Scale consumers
Red:    Queue depth > 100K   → Incident, immediate action

Alert thresholds:
  queue_depth > 10,000 for 5 min   → Page on-call
  queue_depth > 100,000             → Critical alert
  oldest_message_age > 300 sec     → Processing lag
```

### **Tip 2: The Consumer Scaling Formula**
```
Consumers Needed = (Enqueue Rate × Processing Time) ÷ Batch Size

Example:
  Enqueue Rate     = 7,200 msg/sec
  Processing Time  = 5 sec
  Batch Size       = 10 messages

  Consumers = (7,200 × 5) ÷ 10 = 3,600 consumers

Add 50% headroom: 3,600 × 1.5 = 5,400 consumers (target)
```

### **Tip 3: The Priority Starvation Check**
```
Monitor: "How long does a low-priority message wait?"

Healthy:  Low priority processed within 5 min
Warning:  Low priority waiting 10-30 min
Critical: Low priority waiting > 1 hour

Solution: Implement priority boost:
  - If message waits > 10 min, boost priority by 1 level
  - If message waits > 30 min, boost priority by 2 levels
  - Ensures fairness across priorities
```

---

## 🎓 Professor's Final Wisdom

> **"In distributed queue systems, CAPACITY planning beats PERFORMANCE tuning"**

Your interviewer wants to see:
1. ✅ Understanding of queue behavior (depth, throughput, latency)
2. ✅ Trade-offs between durability and speed (PostgreSQL vs Redis)
3. ✅ Consumer scaling strategy (auto-scaling based on queue depth)
4. ✅ Failure handling (DLQ, visibility timeout, retries)

**NOT NEEDED:**
- ❌ Exact queueing theory formulas (M/M/1, Little's Law)
- ❌ Complex distributed consensus algorithms
- ❌ Perfect accuracy (order of magnitude is sufficient)

---

## 🔁 Repetition Backed by Emotion (Your Power Principle!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"Queue depth = Processing rate × Processing time - I can size any consumer pool!"*
2. *"Messages/day ÷ 100K = Messages/sec - Simple conversion!"*
3. *"Storage = Messages × Size × Retention × 2 - Always account for replication!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say: "So we have 240 million messages per day, that's about 2,400 per second, which means..."

---

## 📚 Quick Reference: Message System Benchmarks

| **System Type** | **Throughput** | **Latency** | **Durability** | **Priority** |
|-----------------|----------------|-------------|----------------|--------------|
| **RabbitMQ** | 10K-50K msg/s | 1-5ms | Disk-backed | Native support |
| **Amazon SQS** | 10K-100K msg/s | 100ms | Highly durable | Limited (FIFO) |
| **Apache Kafka** | 100K-1M msg/s | <10ms | Highly durable | Partition-based |
| **Redis Streams** | 100K-1M msg/s | <1ms | Optional (AOF) | No native support |
| **Our Design** | 100K+ msg/s | <50ms | PostgreSQL + Redis | 10 levels |

---

## 🔧 Practical Application: Adapting This Template

### For a **Notification System** (like Firebase Cloud Messaging):
```
STEP 1: THROUGHPUT
- Write: Push notifications sent (high volume)
- Read: N/A (fire-and-forget)
- Ratio: Write-only (1:0)

STEP 2: QUEUE DEPTH
- In-flight: Notifications being sent to APNS/FCM
- Backlog: Pending notifications (if downstream slow)

STEP 3: STORAGE
- Notification: ~2 KB (title, body, metadata)
- Retention: 7 days (for retry)
- Apply compression: 30% reduction

STEP 4: CONSUMERS
- APNS Workers: 100 (for iOS)
- FCM Workers: 200 (for Android)
- SMS Workers: 50 (fallback)
```

### For a **Background Job Queue** (like Sidekiq, Celery):
```
STEP 1: THROUGHPUT
- Jobs enqueued: 1M jobs/day (emails, reports, data processing)
- Priority: Critical (5%), High (15%), Normal (80%)

STEP 2: QUEUE DEPTH
- Depends on worker availability
- If workers busy → queue grows
- Monitor: queue_depth / worker_count ratio

STEP 3: STORAGE
- Job: ~5 KB (task name, args, kwargs)
- Retention: 1 day (failed jobs retained longer)

STEP 4: CONSUMERS
- Worker pools per queue:
  - Critical: 10 workers (always ready)
  - High: 50 workers (auto-scale 20-100)
  - Normal: 200 workers (auto-scale 50-500)
```

### For a **Event Streaming + Priority** (Kafka with priority partitions):
```
STEP 1: THROUGHPUT
- Events: 10M events/day
- Priority: Use separate topics per priority
  - critical-events (topic)
  - high-priority-events (topic)
  - normal-events (topic)

STEP 2: PARTITIONING
- Partitions per topic: 10 (for parallelism)
- Consumer group per topic: Dedicated workers

STEP 3: RETENTION
- Kafka retention: 7 days (all topics)
- Long-term: Archive to S3 (via Kafka Connect)

STEP 4: CONSUMERS
- Critical consumers: 10 (1 per partition)
- High consumers: 20 (2 per partition)
- Normal consumers: 50 (5 per partition)
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: E-commerce Order Queue
```
Given:
- 500M orders/month
- Peak (holiday season): 5× normal traffic
- Order message size: 15 KB
- Processing time: 10 sec
- Retention: 14 days

Calculate:
1. Peak enqueue rate (msg/sec)
2. Consumer pool size needed
3. Storage required (14 days)
4. Redis memory for in-flight messages

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. PEAK ENQUEUE RATE:
   - Orders/month = 500M
   - Orders/day = 500M ÷ 30 = ~17M/day
   - Normal rate = 17M ÷ 100K = 170 msg/sec
   - Peak rate = 170 × 5 = 850 msg/sec

2. CONSUMER POOL SIZE:
   - Processing time = 10 sec
   - Peak rate = 850 msg/sec
   - Consumers = 850 × 10 = 8,500 consumers
   - With headroom (50%) = 8,500 × 1.5 = 12,750 consumers
   - Practical: Min 2K, Max 15K (auto-scaling)

3. STORAGE (14 days):
   - Daily = 17M × 15 KB = 255 GB/day
   - With metadata (1.35×) = 344 GB/day
   - With replication (2×) = 688 GB/day
   - 14 days = 688 GB × 14 = 9.6 TB ≈ 10 TB

4. REDIS MEMORY (in-flight):
   - In-flight = 850 msg/s × 10s = 8,500 messages
   - Memory = 8,500 × 15 KB = 127 MB
   - With overhead (1.2×) = 152 MB
   - Hot data (1 hour) = 170 msg/s × 3600s × 15 KB × 1.2 = 11 GB
   - Total Redis: ~12 GB (use 32 GB cluster for headroom)
```
</details>

---

### Problem 2: Real-Time Notification System
```
Given:
- 1B push notifications/day
- Priority:
  - Critical (fraud alerts): 1%
  - High (new messages): 10%
  - Normal (marketing): 89%
- Message size: 2 KB
- Processing time: 0.5 sec (send to APNS/FCM)
- Retention: 3 days

Calculate:
1. Throughput per priority level
2. In-flight messages per priority
3. Total storage needed
4. Bandwidth requirements

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. THROUGHPUT PER PRIORITY:
   - Total = 1B/day ÷ 100K = 10,000 msg/sec
   - Critical (1%) = 10,000 × 0.01 = 100 msg/sec
   - High (10%) = 10,000 × 0.10 = 1,000 msg/sec
   - Normal (89%) = 10,000 × 0.89 = 8,900 msg/sec

2. IN-FLIGHT MESSAGES:
   - Processing time = 0.5 sec
   - Critical in-flight = 100 × 0.5 = 50 messages
   - High in-flight = 1,000 × 0.5 = 500 messages
   - Normal in-flight = 8,900 × 0.5 = 4,450 messages
   - Total in-flight = ~5,000 messages

3. STORAGE (3 days):
   - Daily = 1B × 2 KB = 2 TB/day
   - With metadata (1.35×) = 2.7 TB/day
   - With replication (2×) = 5.4 TB/day
   - 3 days = 5.4 TB × 3 = 16.2 TB ≈ 16 TB

4. BANDWIDTH:
   - Enqueue = 10,000 msg/s × 2 KB = 20 MB/s (~160 Mbps)
   - Dequeue = 10,000 msg/s × 2 KB = 20 MB/s (~160 Mbps)
   - Total = 40 MB/s (~320 Mbps)
   - Peak (3×) = 120 MB/s (~1 Gbps)
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting In-Flight Messages**
```
✗ BAD:  "Queue is balanced, so queue depth is 0"
✓ GOOD: "Queue depth = In-flight messages even when balanced
         In-flight = Throughput × Processing Time"
```

### Mistake 2: **Ignoring Priority Distribution**
```
✗ BAD:  "All messages are equal priority"
✓ GOOD: "80% medium, 15% high, 5% critical → Size consumer pools accordingly"
```

### Mistake 3: **Underestimating Peak Traffic**
```
✗ BAD:  "Average throughput is 2.4K msg/sec, size for that"
✓ GOOD: "Average 2.4K, but peak is 3-5× during events → size for 7.2K-12K"
```

### Mistake 4: **Not Accounting for Retries**
```
✗ BAD:  "240M messages/day = 240M processed"
✓ GOOD: "240M messages/day, but 5% retry once = 252M total operations"
```

### Mistake 5: **Forgetting Replication**
```
✗ BAD:  "Storage = 240M × 10 KB × 7 days = 16.8 TB"
✓ GOOD: "Storage with replication = 16.8 TB × 2 = 33.6 TB (primary + replica)"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
SYSTEM: Priority Queue for ___________________

STEP 1: MESSAGE THROUGHPUT
───────────────────────────
Messages Enqueued/Day:    [____] M
Messages Dequeued/Day:    [____] M
Priority Distribution:
  - Critical (10):        [____] %
  - High (7-9):           [____] %
  - Medium (4-6):         [____] %
  - Low (1-3):            [____] %

→ Enqueue Rate = [____] M ÷ 100K = [____] msg/sec
→ Dequeue Rate = [____] M ÷ 100K = [____] msg/sec
→ Peak Rate    = [____] × 3       = [____] msg/sec

STEP 2: QUEUE DEPTH
───────────────────────────
Processing Time:          [____] sec
Visibility Timeout:       [____] sec

→ In-Flight = Peak Rate × Processing Time = [____]
→ Backlog   = Enqueue - Dequeue           = [____]
→ Total     = In-Flight + Backlog         = [____]

STEP 3: STORAGE
───────────────────────────
Message Size:             [____] KB
Retention:                [____] days

→ Daily Storage   = [____] M × [____] KB        = [____] TB
→ With Metadata   = [____] TB × 1.35            = [____] TB
→ With Replication = [____] TB × 2              = [____] TB
→ Total           = [____] TB × [____] days     = [____] TB

STEP 4: CONSUMERS
───────────────────────────
→ Consumers = Peak Rate × Processing Time = [____]
→ With Batch Processing (10 msgs/batch)   = [____]
→ With Headroom (50%)                     = [____]

STEP 5: BANDWIDTH
───────────────────────────
→ Enqueue BW = [____] msg/s × [____] KB = [____] MB/s
→ Dequeue BW = [____] msg/s × [____] KB = [____] MB/s
→ Total BW   = [____] MB/s               = [____] Gbps

SMELL TEST:
───────────────────────────
□ Queue depth reasonable? (<100K healthy)
□ Consumers achievable? (1K-10K typical)
□ Storage practical? (10-100 TB range)
□ Bandwidth achievable? (<10 Gbps typical datacenter)
```

---

## 🎁 Bonus: Priority Queue Scale Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║     PRIORITY QUEUE SCALE ESTIMATION CHEAT SHEET        ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 Day      = 100K seconds (~86.4K)
• Queue Depth = Rate × Processing Time
• In-Flight  = Messages currently being processed
• Backlog    = Messages waiting to be processed

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Msg Rate (QPS) = Daily Messages ÷ 100K
Queue Depth    = Rate × Processing Time + Backlog
Storage        = Messages × Size × Retention × 2 (replication)
Consumers      = (Rate × Processing Time) ÷ Batch Size
Bandwidth      = Rate × Message Size

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Priority Distribution: 5% critical, 15% high, 80% medium/low
• Peak:Avg = 3-5x (flash sales, events)
• DLQ Rate: <0.1% (healthy system)
• Batch Size: 10-100 messages (for efficiency)
• Visibility Timeout: 2× avg processing time

QUEUE HEALTH INDICATORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Green:  Queue depth < 10K, oldest message < 30s
Yellow: Queue depth 10K-50K, oldest message 30s-5min
Red:    Queue depth > 100K, oldest message > 5min

QUICK ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:  1K-10K msg/s,   <10 TB,      100-1K consumers
Medium: 10K-100K msg/s, 10-100 TB,   1K-10K consumers
Large:  100K-1M msg/s,  100TB-1PB,   10K-100K consumers

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify requirements (5 min)
   → Message volume? Priority levels? Processing time?

2. Throughput estimation (5 min)
   → Messages/sec, queue depth, in-flight

3. Storage & consumers (5 min)
   → Retention period, consumer pool sizing

4. System design (20 min)
   → Redis for hot data, PostgreSQL for durability
   → Visibility timeout, DLQ handling

5. Trade-offs (5 min)
   → At-least-once vs exactly-once
   → Durability vs latency
   → Cost optimization

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can 1K consumers handle 2.4K msg/s with 5s processing?
  → 1K × (1 msg/5s) = 200 msg/s ✗
  → Need 2.4K × 5 = 12K consumers (or batch processing) ✓

✓ Can Redis cache 100K in-flight messages @ 10 KB each?
  → 100K × 10 KB = 1 GB ✓ (easily fits in 64 GB Redis)

✓ Can PostgreSQL store 50 TB with indexing?
  → Yes, with partitioning by priority + sharding by queue_name ✓

✓ Can network handle 1 Gbps peak?
  → Yes, typical datacenter has 10 Gbps+ capacity ✓
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these systems and practice the full estimation:

1. **Slack** - Real-time messaging with priority (DMs > mentions > channels)
2. **Uber** - Driver dispatch queue (nearby drivers prioritized)
3. **Food Delivery** - Order assignment to drivers (priority by order time)
4. **CI/CD Pipeline** - Build job queue (hotfix builds > regular builds)
5. **Email Service** - Transactional emails (verification > marketing)

Use the blank template above and time yourself: **Can you complete it in 5 minutes?**

---

## 📚 Additional Resources

- **Books**:
  - "Designing Data-Intensive Applications" (Martin Kleppmann) - Chapter 11: Stream Processing
  - "Enterprise Integration Patterns" (Gregor Hohpe) - Message Queue patterns
- **Papers**:
  - "Amazon SQS: A Distributed Queue Service" (AWS whitepaper)
  - "RabbitMQ in Action" (Manning Publications)
- **Practice**:
  - Design RabbitMQ-like system (with priority queues)
  - Design Celery/Sidekiq background job processor
  - Design notification delivery system

---

**Remember**:
> "Queue depth tells the story - if it's growing, you need more consumers; if it's empty, you're over-provisioned."

**Now go build scalable queues!** 🚀

---

*Created with the QUEUES technique: Queue depth → Usage → Estimate → Understand → Evaluate → Size*
*Perfect for: FAANG interviews, Distributed Systems design, Message Queue architecture*
