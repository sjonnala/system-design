# 🎯 Unique Active Users System: Scale Estimation Masterclass

## The DATA Framework for Analytics Scale Math
**(D)imensions → (A)ctive users → (T)hroughput → (A)ggregations**

This mental framework applies to ANY data analytics and event processing system.

---

## 📊 PART 1: Understanding the Analytics Scale

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Registered Users | 500M | Facebook-scale platform |
| | Daily Active Users (DAU) | 100M | ~20% of total (industry standard) |
| | Weekly Active Users (WAU) | 200M | ~40% of total |
| | Monthly Active Users (MAU) | 300M | ~60% of total |
| **Event Generation** | Events per DAU | 50/day | Mix of page views, clicks, actions |
| | Peak Hour Concentration | 30% | Traffic concentrated in 8 peak hours |
| **Event Size** | Average Event Size | 1 KB | JSON payload with metadata |
| | Compressed Size | 200 bytes | LZ4 compression (5:1 ratio) |
| **Retention** | Raw Events | 7 days | Hot storage, then archive |
| | Aggregated Data | 5 years | Historical analytics |

---

## 🧮 PART 2: The "Analytics Calculator" - Mental Math Toolkit

### Rule #1: **Events Per Second (EPS) Formula**
```
Remember these anchors:
• 1 Day = 86,400 seconds ≈ 100K seconds (for quick math)
• Peak multiplier = 3× average (industry standard)
• Events/sec = (DAU × Events/User/Day) ÷ 100K

Quick Example:
100M DAU × 50 events/day ÷ 100K = 50,000 events/sec (average)
Peak: 50K × 3 = 150,000 events/sec
```

### Rule #2: **The HyperLogLog Memory Trick**
```
Exact counting memory: 16 bytes × number of unique users
HyperLogLog memory: 12 KB (fixed, regardless of users!)

Example - Storing DAU:
✗ Exact (HashSet): 100M users × 16 bytes = 1.6 GB per day
✓ HyperLogLog: 12 KB per day (99.19% memory savings!)

Trade-off: ±0.81% accuracy (acceptable for analytics)
```

### Rule #3: **Storage Compression Multiplier**
```
Raw JSON: 1 KB per event
Compressed (LZ4): 200 bytes (5:1 ratio)
Columnar (Parquet): 100 bytes (10:1 ratio)
Aggregated: 50 bytes (20:1 ratio)

Always calculate:
- Raw ingestion storage
- Compressed storage (÷ 5)
- Aggregated storage (÷ 20)
```

---

## 📈 PART 3: Scale Math Template for Analytics Systems

```
┌─────────────────────────────────────────────────────────┐
│  🎯 THE ANALYTICS NAPKIN MATH - Universal Template     │
└─────────────────────────────────────────────────────────┘

STEP 1: EVENT INGESTION RATE
───────────────────────────
Daily Active Users:        [____] M
Events per User per Day:   [____]
Peak Hour Concentration:   [____]%

→ Total Events/Day = DAU × Events/User = [____] B
→ Average EPS = Events/Day ÷ 100K       = [____] K
→ Peak EPS = Average × 3                = [____] K

STEP 2: STORAGE ESTIMATION
───────────────────────────
Event Size (Raw):          [____] KB
Compression Ratio:         5:1 (LZ4)
Retention Period:          [____] days

→ Daily Raw Storage = Events × Size        = [____] TB
→ Daily Compressed = Daily Raw ÷ 5         = [____] TB
→ Total Storage = Daily × Retention        = [____] PB

STEP 3: UNIQUE USER COUNTING
───────────────────────────
Users to Track:            [____] M
Counting Method:           HyperLogLog

→ Memory (Exact): Users × 16 bytes         = [____] GB
→ Memory (HLL): 12 KB per dimension        = [____] KB
→ Dimensions (country, platform, etc.):    = [____]
→ Total HLL Memory = 12KB × Dimensions     = [____] MB

STEP 4: QUERY LOAD
───────────────────────────
Dashboard Queries/Day:     [____]
Analyst Queries/Day:       [____]
Real-time Updates/Min:     [____]

→ Total Queries/Day = [____]
→ QPS = Total ÷ 100K = [____]
→ Average Query Latency Target: < 2s
→ Cache Hit Ratio: 70-80%

STEP 5: AGGREGATION STORAGE
───────────────────────────
Dimensions to Track:       [____]
Granularity:              Hourly, Daily, Monthly
Retention:                5 years

→ Hourly Rollups = 24 × 365 × 5 × Dims    = [____] GB
→ Daily Rollups = 365 × 5 × Dims          = [____] MB
→ Monthly Rollups = 12 × 5 × Dims         = [____] KB
```

---

## 💾 PART 4: Filled Template - Unique Active Users System

```
┌─────────────────────────────────────────────────────────┐
│    UNIQUE ACTIVE USERS - NAPKIN MATH SOLUTION          │
└─────────────────────────────────────────────────────────┘

STEP 1: EVENT INGESTION RATE
───────────────────────────
Daily Active Users:        100 M
Events per User per Day:   50
Peak Hour Concentration:   30% in 8 hours

→ Total Events/Day = 100M × 50           = 5,000 M (5 B)
→ Average EPS = 5B ÷ 100K                = 50,000 EPS
→ Peak EPS = 50K × 3                     = 150,000 EPS

Peak Hour Detail:
- 30% of daily events = 1.5B events
- Concentrated in 8 hours = 28,800 sec
- Peak EPS = 1.5B ÷ 28,800               = 52,000 EPS
- With spikes (2×): 100K+ EPS

STEP 2: STORAGE ESTIMATION
───────────────────────────
Event Size (Raw):          1 KB
Event Size (Compressed):   200 bytes (LZ4)
Event Size (Parquet):      100 bytes
Retention Period:          7 days (raw), 2 years (archive)

→ Daily Raw Storage = 5B × 1KB           = 5 TB/day
→ Daily Compressed = 5TB ÷ 5             = 1 TB/day
→ 7-day Hot Storage = 1TB × 7            = 7 TB
→ 2-year Archive = 1TB × 730 ÷ 10        = 73 TB (Parquet)

Total Storage:
- Hot (7 days): 7 TB (SSD/NVMe)
- Warm (90 days): 90 TB (HDD)
- Cold (2 years): 73 TB (S3/Glacier)

STEP 3: UNIQUE USER COUNTING
───────────────────────────
Users to Track:            100 M (DAU)
Dimensions:
- Countries: 195
- Platforms: 4 (web, ios, android, other)
- Device Types: 3 (mobile, desktop, tablet)
- App Versions: 50 (active versions)
- Combinations: 195 × 4 × 3 = 2,340 dimensions

Using Exact Counting (NOT FEASIBLE):
→ Memory per Day = 100M × 16 bytes       = 1.6 GB/day
→ 30 days = 1.6GB × 30                   = 48 GB
→ With dimensions: 48GB × 2,340          = 112 TB (IMPOSSIBLE!)

Using HyperLogLog (RECOMMENDED):
→ Memory per HLL = 12 KB
→ HLLs per day = 2,340 dimensions        = 2,340 HLLs
→ Memory per day = 2,340 × 12KB          = 28 MB
→ 30 days (MAU) = 28MB × 30              = 840 MB
→ 90 days (rolling) = 28MB × 90          = 2.5 GB

Memory Savings: 112 TB → 2.5 GB (99.998% reduction!)

STEP 4: QUERY LOAD
───────────────────────────
Dashboard Auto-refresh:    100 dashboards × 12/hour = 1,200/hour
Analyst Queries:           100 analysts × 20/day    = 2,000/day
Real-time Updates:         50 screens × 12/hour     = 600/hour

→ Total Queries/Day = (1,200 × 24) + 2,000 + (600 × 24)
                    = 28,800 + 2,000 + 14,400
                    = 45,200 queries/day

→ QPS = 45,200 ÷ 86,400                  ≈ 0.5 QPS (very low!)
→ Peak Hour = 0.5 × 5                    ≈ 2.5 QPS

Average Query Latency: < 2s (P95)
Cache Hit Ratio: 75% (common dashboards)
→ Actual DB QPS = 2.5 × 0.25             ≈ 0.6 QPS

Insight: Query load is LOW compared to ingestion!

STEP 5: AGGREGATION STORAGE
───────────────────────────
Dimensions:                2,340 (see above)
Metrics per Dimension:     5 (DAU, new users, events, sessions, revenue)
Size per Record:           50 bytes

Hourly Rollups:
- Records/hour = 2,340 dimensions × 5 metrics = 11,700 records
- Size/hour = 11,700 × 50 bytes               = 585 KB
- 1 day = 585KB × 24                          = 14 MB
- 90 days = 14MB × 90                         = 1.26 GB

Daily Rollups:
- Records/day = 11,700
- Size/day = 585 KB
- 5 years = 585KB × 365 × 5                   = 1.07 GB

Monthly Rollups:
- Records/month = 11,700
- Size/month = 585 KB
- 5 years = 585KB × 12 × 5                    = 35 MB

Total Aggregated Storage:
- Hourly (90 days): 1.26 GB
- Daily (5 years): 1.07 GB
- Monthly (5 years): 35 MB
- Grand Total: ~2.4 GB (tiny!)

Compare to raw: 2.4 GB vs 73 TB (30,000× reduction!)
```

---

## 🧠 PART 6: Mental Math Techniques for Analytics

### **Technique 1: The Power of Approximation**
```
🎯 ANALYTICS MANTRA:
"Exact counts are impossible at scale. Embrace probabilistic data structures!"

HyperLogLog: ±0.81% error (acceptable)
Count-Min Sketch: ±2% error (top-K queries)
Bloom Filter: 1% false positive (existence checks)

Trade accuracy for:
- 1000× memory savings
- Real-time processing
- Distributed computation
```

### **Technique 2: The "Five Nines" Rule**
```
When estimating storage savings:

Exact → HyperLogLog: 99.9% memory reduction
Raw → Compressed: 80% reduction (5:1)
Raw → Aggregated: 95% reduction (20:1)
Raw → Columnar: 90% reduction (10:1)

Quick Math:
1 PB raw events → 50 TB aggregated (98% savings!)
```

### **Technique 3: Dimensional Explosion Calculator**
```
Dimensions grow multiplicatively, not additively!

Example:
- Countries: 200
- Platforms: 4
- Device Types: 3
- Total Combinations = 200 × 4 × 3 = 2,400

With user segments (10): 2,400 × 10 = 24,000 combinations!

Rule: Limit dimensions to < 10 to avoid explosion
```

### **Technique 4: The Lambda Architecture Cost Model**
```
Speed Layer (Real-time):
- Cost: HIGH (Flink cluster, Redis)
- Latency: < 1 minute
- Data: Last 24 hours

Batch Layer (Historical):
- Cost: MEDIUM (Spark jobs, S3)
- Latency: 1 hour
- Data: > 24 hours

Savings: Process recent data differently from old data!
```

---

## 🎨 PART 7: The Visual Analytics Scale Model

```
                    📊 ANALYTICS SYSTEM SCALE
                             |
        ┌────────────────────┼────────────────────┐
        |                    |                    |
   🌊 INGESTION         💾 STORAGE           🔍 QUERY
        |                    |                    |
    ┌───┴───┐           ┌────┴────┐         ┌────┴────┐
   EPS    Volume     Hot   Cold   Cache    Latency  QPS
  150K    5B/day    7TB   73TB    20GB      <2s     2.5
```

**Memory Trigger**: Think **"I.S.Q."** = Ingestion, Storage, Query

---

## 🏗️ PART 8: Real-World Benchmark Comparisons

### Industry Benchmarks

| **Company** | **DAU** | **Events/Day** | **Storage** | **Query Latency** |
|-------------|---------|----------------|-------------|-------------------|
| Facebook | 2B | 500B+ | 300+ PB | < 1s |
| Google Analytics | 30M sites | 100B+ | 100+ PB | < 2s |
| Mixpanel | 10M tracked | 50B | 10 PB | < 1s |
| Our System | 100M | 5B | 80 TB | < 2s |
| Twitter | 200M | 500B | 50+ PB | < 1s |

---

## 🎯 PART 9: The Interview Cheat Sheet

```
┌──────────────────────────────────────────────────┐
│  ANALYTICS SYSTEM ESTIMATION - 5 MIN RITUAL      │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify: DAU, Events/User, Dimensions
[ ] 2. Calculate EPS: DAU × Events ÷ 100K
[ ] 3. Calculate Storage: Events × Size × Retention
[ ] 4. Choose Counting: HyperLogLog for > 100K users
[ ] 5. Estimate Queries: Usually LOW compared to ingestion
[ ] 6. Plan Aggregations: Pre-compute everything
[ ] 7. Sanity Check: Does this match known systems?
```

---

## 💡 Pro Architect Tips

### **Tip 1: The 80-20 Rule for Analytics**
```
80% of queries access:
- 20% of data (last 7 days)
- 20% of dimensions (country, platform)
- 20% of users (most active)

Optimize for the 80%, not the 20%!
```

### **Tip 2: Pre-aggregation is King**
```
Query cost comparison:
- Raw events scan: $10 per query
- Pre-aggregated: $0.01 per query (1000× cheaper!)

Always pre-aggregate:
- Hourly rollups
- Daily summaries
- Monthly totals
```

### **Tip 3: Lambda Architecture Split Point**
```
Question: "When to split speed vs batch layer?"

Answer: At the 24-hour mark!
- < 24 hours: Real-time (Flink, Redis)
- > 24 hours: Batch (Spark, ClickHouse)

Reason: Recent data changes frequently (corrections, late arrivals)
Old data is stable, can be optimized
```

### **Tip 4: Dimension Cardinality Matters**
```
Low Cardinality (< 1000): Store in each record
- Country (200)
- Platform (10)
- Device (10)

High Cardinality (> 1M): Use dimension tables
- User ID (100M)
- Session ID (1B)
- Event ID (10B)

Join at query time, don't denormalize!
```

---

## 📚 Quick Reference: Analytics Scale Benchmarks

| **Metric** | **Small** | **Medium** | **Large** | **Huge** |
|------------|-----------|------------|-----------|----------|
| **DAU** | < 1M | 1M - 10M | 10M - 100M | > 100M |
| **Events/Day** | < 100M | 100M - 1B | 1B - 10B | > 10B |
| **EPS** | < 1K | 1K - 10K | 10K - 100K | > 100K |
| **Storage** | < 100 GB | 100GB - 1TB | 1TB - 10TB | > 10TB |
| **Ingestion Tool** | Logstash | Kafka | Kafka + Flink | Distributed Kafka |
| **OLAP DB** | PostgreSQL | ClickHouse | Druid/ClickHouse | Druid + Pinot |
| **Counting** | Exact (SQL) | HyperLogLog | HyperLogLog | HyperLogLog + Sampling |

---

## 🔁 Repetition Backed by Emotion

**REPEAT 3 TIMES OUT LOUD:**
1. *"HyperLogLog saves 99.9% memory - it's magic!"*
2. *"Pre-aggregate everything - raw queries don't scale!"*
3. *"Lambda architecture: real-time + batch = best of both!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say:
> "With 100 million DAU and 50 events per user, we're looking at 50,000 events per second on average, peaking at 150K. We'll use HyperLogLog to track unique users with only 12KB per dimension, down from 1.6GB exact counting..."

---

## 🔧 Practical Application: Adapting This Template

### For a **Video Analytics** System (like YouTube):
```
STEP 1: INGESTION
- Views/day: Billions
- Unique viewers: 100M+
- Video interactions: plays, pauses, seeks

STEP 2: STORAGE
- Video metadata: 1KB per video
- View events: 500 bytes each
- Watch time: aggregate by video, user, country

STEP 3: COUNTING
- Use HyperLogLog for unique viewers
- Exact count for watch time (SUM aggregation)
- Pre-aggregate by hour, day, video

STEP 4: QUERIES
- "Top trending videos" (hot data, cache heavily)
- "Channel analytics" (pre-aggregate by channel)
- "Viewer demographics" (dimension tables)
```

### For a **E-commerce Analytics** System:
```
STEP 1: INGESTION
- Page views, add-to-cart, purchases
- Lower volume than social (10× less)
- Higher value per event (revenue tracking)

STEP 2: STORAGE
- Events with product metadata
- Join with product catalog (dimension table)
- Revenue calculations (SUM, not COUNT)

STEP 3: COUNTING
- Unique visitors: HyperLogLog
- Purchases: Exact count (low volume)
- Revenue: Exact SUM (financial accuracy)

STEP 4: QUERIES
- "Conversion funnel" (pre-computed)
- "Top products" (materialized view)
- "Revenue by country" (hourly rollup)
```

---

## 🎓 Professor's Final Wisdom

> **"In analytics, FAST APPROXIMATE beats SLOW EXACT"**

Your interviewer wants to see:
1. ✅ Understanding of scale (millions vs billions)
2. ✅ Trade-offs (accuracy vs memory)
3. ✅ Practical solutions (HyperLogLog, pre-aggregation)
4. ✅ Cost awareness (storage, compute optimization)

**NOT NEEDED:**
- ❌ Exact calculations
- ❌ Memorizing algorithms
- ❌ Over-engineering

---

## 🚀 Key Metrics Summary

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Avg EPS** | 50,000 | Kafka partition sizing |
| **Peak EPS** | 150,000 | Autoscaling triggers |
| **Raw Storage** | 5 TB/day | Infrastructure cost |
| **Compressed** | 1 TB/day | Actual storage needed |
| **HLL Memory** | 28 MB/day | Redis sizing |
| **Query Latency** | < 2s | User experience |
| **Aggregation** | 2.4 GB | 30,000× savings! |

---

## 🎯 Mental Math Practice Problem

### Problem: Design Analytics for Uber

```
Given:
- 100M trips/month
- 10M active riders/month
- 1M active drivers/month
- Each trip generates 100 events (GPS, status updates)
- Track: trips by city, driver ratings, surge pricing

Calculate:
1. Events per second
2. Storage (1 year)
3. HyperLogLog memory for MAU
4. Aggregation tables size

[Try it yourself, then check your approach]
```

<details>
<summary>Approach</summary>

```
1. EVENTS PER SECOND:
   - Trips/month = 100M
   - Events/trip = 100
   - Total events/month = 100M × 100 = 10B
   - Events/day = 10B ÷ 30 = 333M
   - EPS = 333M ÷ 100K = 3,330 EPS (avg)
   - Peak = 3,330 × 3 = 10,000 EPS

2. STORAGE (1 year):
   - Events/year = 10B × 12 = 120B
   - Size/event = 500 bytes (GPS data)
   - Raw = 120B × 500B = 60 TB
   - Compressed (5:1) = 12 TB
   - Parquet (10:1) = 6 TB for archive

3. HLL MEMORY (MAU):
   - Riders: 10M
   - Drivers: 1M
   - Cities: 1000
   - HLLs needed = 2 (riders, drivers) × 1000 cities = 2000
   - Memory = 2000 × 12KB = 24 MB

4. AGGREGATIONS:
   - Dimensions: city (1000), hour (24), day (365)
   - Metrics: trips, revenue, avg_rating (3)
   - Records/year = 1000 × 365 × 3 = 1,095,000
   - Size = 1M × 100 bytes = 100 MB
```
</details>

---

**Remember**:
> "Analytics systems are write-heavy, storage-heavy, but query-light. Optimize for ingestion throughput and storage efficiency, not query complexity!"

**Now go design world-class analytics systems!** 🚀

---

*Created with the DATA technique: Dimensions → Active users → Throughput → Aggregations*
*Perfect for: FAANG interviews, Analytics System Design, Real-time Data Processing*
