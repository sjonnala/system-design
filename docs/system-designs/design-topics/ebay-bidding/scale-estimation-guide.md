# 🎯 eBay Bidding System: Scale Estimation Masterclass

## The AUCTION-SCALE Framework
**(A)uctions → (U)sers → (C)oncurrency → (T)hroughput → (I)nteractions → (O)utput → (N)otifications → (S)torage → (C)ache → (A)nalytics → (L)atency → (E)stimation**

This framework helps you systematically estimate scale for **real-time auction/marketplace** systems.

---

## 📊 PART 1: Understanding Auction System Dynamics

### Key Differences from Other Systems

| **Aspect** | **Typical System (e.g., Social Media)** | **Auction System (eBay)** |
|------------|---------------------------------------|----------------------------|
| **Write Pattern** | Bursty (posts when inspired) | Clustered (auctions ending soon) |
| **Read Pattern** | Read-heavy (100:1 ratio) | Moderately read-heavy (20:1 ratio) |
| **Consistency** | Eventual consistency OK | **Strong consistency REQUIRED** |
| **Real-time** | Nice to have (<5 sec OK) | **Critical** (<100ms needed) |
| **Concurrency** | Low contention | **High contention** (many bid on same item) |
| **Time-sensitive** | Not critical | **Auction end time MUST be exact** |

**Key Insight**: Auction systems have **HOT SPOTS** - popular auctions receive disproportionate traffic in final minutes.

---

## 🧮 PART 2: The "Auction Calculator" - Mental Math Toolkit

### Rule #1: **The Pareto Pyramid for Auctions**
```
Remember these patterns:
• 80% of bids occur in LAST 20% of auction time
• 20% of auctions get 80% of total bids
• Final 5 minutes: 50% of all bids placed

Example:
7-day auction → Last 33 hours get 80% of bids
                Last 5 minutes get 50% of bids
```

### Rule #2: **The Bid Clustering Formula**
```
Peak Bid Rate = Average × Clustering Factor

For auctions:
• Average period (days 1-6): 1x
• Last day: 5x
• Last hour: 20x
• Last 5 minutes: 100x

If average is 10 bids/hour across week:
• Last hour: 10 × 20 = 200 bids/hour
• Last 5 min: 10 × 100 = 1000 bids in 5 min = 3.3 bids/second
```

### Rule #3: **The Concurrent Auction Load**
```
Instead of "QPS", think "Auctions Ending Per Hour"

100K auctions ending per hour (peak 4-8PM):
= 100K ÷ 60 = ~1,700 auctions/minute
= 1,700 ÷ 60 = ~28 auctions/second

If each auction averages 50 bids in final minute:
= 28 auctions/sec × 50 bids = 1,400 bids/sec (PEAK!)
```

---

## 📈 PART 3: eBay Bidding System - Scale Estimation Template

```
┌─────────────────────────────────────────────────────────┐
│     EBAY BIDDING SYSTEM - SCALE ESTIMATION TEMPLATE     │
└─────────────────────────────────────────────────────────┘

STEP 1: USER & AUCTION ESTIMATION
───────────────────────────────
Total Users:              100 M
Daily Active Users (DAU): 10 M (~10% engagement)
Active Sellers:           2 M (~2% of total)

Active Auctions:          10 M (at any time)
New Auctions/Day:         1 M  (sellers create)
Auctions Ending/Day:      1 M  (same as created)

Average Auction Duration: 7 days
Average Bids Per Auction: 100 bids

STEP 2: TRAFFIC ESTIMATION (CRITICAL!)
───────────────────────────
Write Operations (Bids):
  Total Bids/Day = 1M auctions × 100 bids = 100M bids/day
  Average QPS = 100M ÷ 86.4K = ~1,200 bids/sec

  ⚠️ BUT AUCTIONS CLUSTER! Apply Pareto:
  Peak Hour (6-7 PM): 1,200 × 20 = 24,000 bids/sec
  Peak 5 Min (auction endings): 1,200 × 100 = 120,000 bids/5min
    = 120K ÷ 300 = 400 bids/sec PER ENDING AUCTION

Read Operations:
  Auction Views: 10M DAU × 20 views = 200M/day
  View QPS = 200M ÷ 86.4K = ~2,300 QPS
  Peak: 2,300 × 5 = ~12,000 QPS

  Bid History Reads: 10M DAU × 3 = 30M/day
  History QPS = 30M ÷ 86.4K = ~350 QPS

  WebSocket Connections (Real-time):
    Assume 10% DAU watching auctions live = 1M users
    1M concurrent WebSocket connections

STEP 3: STORAGE ESTIMATION
───────────────────────────
Auction Data:
  Per Auction: 2 KB (title, desc, images URLs, prices, timestamps)
  Active Auctions: 10M × 2KB = 20 GB
  1 Year Storage: 365M auctions × 2KB = 730 GB
  3 Year Total: ~2.2 TB

Bid Data:
  Per Bid: 500 bytes (auction_id, bidder_id, amount, timestamp, IP)
  Bids/Day: 100M × 500 bytes = 50 GB/day
  1 Year Bids: 50GB × 365 = 18.25 TB
  3 Year Bids: ~55 TB

User Data:
  Users: 100M × 1KB = 100 GB

Total 3-Year Storage: 2.2TB + 55TB + 0.1TB = ~60 TB

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
Write Bandwidth:
  Bids: 1,200 QPS × 500 bytes = 600 KB/sec = ~5 Mbps
  Peak: 24K QPS × 500 bytes = 12 MB/sec = ~100 Mbps

Read Bandwidth:
  Auction Views: 2,300 QPS × 2KB = 4.6 MB/sec = ~40 Mbps
  Peak: 12K QPS × 2KB = 24 MB/sec = ~200 Mbps

WebSocket (Real-time Updates):
  1M connections × 10 updates/min × 500 bytes
  = 1M × 10 ÷ 60 × 500 bytes
  = 83 MB/sec = ~700 Mbps

STEP 5: CACHE ESTIMATION (80-20 Rule)
───────────────────────────
Hot Auctions (ending soon + popular):
  20% of auctions = 2M auctions
  Cache Size = 2M × 2KB = 4 GB

Current Bid Cache (ultra-hot):
  100K most active auctions × 500 bytes = 50 MB

User Session Cache:
  1M concurrent users × 10KB session = 10 GB

Total Cache: ~15 GB (easily fits in Redis)

STEP 6: DATABASE SIZING
───────────────────────────
Primary DB (Writes):
  Write Load: 1,200 bids/sec average
  Peak: 24K bids/sec

  With sharding (16 shards by auction_id):
  → 1,200 ÷ 16 = 75 writes/sec per shard
  → Peak: 24K ÷ 16 = 1,500 writes/sec per shard

Read Replicas (Reads):
  Read Load: 2,650 QPS (views + history)
  With 4 replicas:
  → 2,650 ÷ 4 = ~660 reads/sec per replica

STEP 7: WEBSOCKET CAPACITY
───────────────────────────
1M concurrent connections
Each server handles ~10K connections
→ Need 100 WebSocket servers

Each connection sends ~10 updates/min:
→ 1M × 10 ÷ 60 = 166K messages/sec
→ Per server: 1,660 messages/sec (easily achievable)

STEP 8: NOTIFICATION LOAD
───────────────────────────
Outbid Notifications:
  For each bid, 1 previous winner gets notified
  → 1,200 notifications/sec average
  → 24K notifications/sec peak

Auction Ending Notifications:
  1M auctions end/day
  Each has ~10 watchers
  → 10M notifications/day = 116 notifications/sec

Total: ~120 notifications/sec average
       ~25K notifications/sec peak
```

---

## 💾 PART 4: Hot Spot Analysis (CRITICAL for Auctions!)

### The "Last Minute Rush" Problem

```
Scenario: Popular iPhone auction ending at 8:00:00 PM
- 1,000 users watching
- Final 60 seconds: 500 bids placed

Challenge: All bids hit SAME auction_id
→ Database row-level contention
→ Optimistic locking causes retries
→ Need distributed lock + retry logic

┌─────────────────────────────────────────────────┐
│  TIME     │  BIDS/SEC  │  RETRY RATE  │  NOTES  │
├───────────┼────────────┼──────────────┼─────────┤
│  -60s     │     5      │     0%       │  Normal │
│  -30s     │    20      │     5%       │  Rising │
│  -10s     │    50      │    20%       │  High   │
│   -5s     │   100      │    40%       │  Peak   │
│   -1s     │   200      │    60%       │  Extreme│
└───────────┴────────────┴──────────────┴─────────┘

Solution:
1. Distributed Lock (Redis): Prevent 200 concurrent writes
2. Optimistic Locking: Retry logic (max 3 attempts)
3. Shard by auction_id: Distribute hot auctions across DBs
```

### Estimating Hot Spot Impact

```
Total Auctions: 10M
Popular Auctions (>100 watchers): 100K (1%)

Peak Hour: 100K auctions ending
Popular Auctions: 100K × 1% = 1,000

Each popular auction:
- 500 bids in final minute
- 1,000 watchers to notify

Hot Spot Load (Final Minute of Popular Auction):
- 500 bids → 500 writes/sec to SAME row
- 1,000 WebSocket broadcasts
- 500 outbid notifications

With 1,000 popular auctions ending simultaneously:
→ 500K bids/min = 8,333 bids/sec (distributed across shards)
→ 1M WebSocket messages/min = 16,666 messages/sec
```

---

## 🧠 PART 5: Mental Math Techniques for Auction Systems

### **Technique 1: The "Auction Funnel"**
```
Think of auction lifecycle as a funnel:

Day 1-6: Browsing phase
  → 10% of total bids
  → Low QPS

Day 7 (Last Day): Commitment phase
  → 40% of total bids
  → 4x QPS

Last Hour: Frenzy phase
  → 30% of total bids
  → 40x QPS

Last 5 Minutes: Final battle
  → 20% of total bids
  → 100x QPS

Example: 100 bids over 7 days
  → Days 1-6: 10 bids → 10 ÷ (6 × 24 × 60) = 0.001 bids/min
  → Last 5 min: 20 bids → 20 ÷ 5 = 4 bids/min (4000x increase!)
```

### **Technique 2: The "Ending Auction Calculator"**
```
Key Metric: Auctions Ending Per Hour (AEPH)

Total auctions/day = 1M
Auctions are distributed 24 hours
Peak Hours (4-8 PM): 2x traffic
→ Peak AEPH = 1M ÷ 24 × 2 = ~80K/hour

But auctions cluster at round times (6:00 PM, 7:00 PM)
→ In practice: 100K auctions ending 6:00-6:05 PM
→ 100K ÷ 5 min = 20K auctions/min = 333 auctions/sec

If each auction gets 50 bids in final minute:
→ 333 × 50 = 16,650 bids/sec (PEAK WRITE LOAD!)
```

### **Technique 3: The "Notification Multiplier"**
```
For each bid placed, calculate notification load:

1 Bid Event Triggers:
  1. Previous winner: Outbid notification (1 person)
  2. All watchers: New bid notification (N people)
  3. Bidder: Confirmation notification (1 person)

Notification Factor = 1 + N + 1 = N + 2

If average auction has 10 watchers:
  1,200 bids/sec × (10 + 2) = 14,400 notifications/sec
```

---

## 🎨 PART 6: Visual Scale Map for Auction Systems

```
                    🏛️ EBAY BIDDING SYSTEM
                            |
        ┌───────────────────┼───────────────────┐
        |                   |                   |
    📊 USERS            💰 AUCTIONS          📬 BIDS
        |                   |                   |
    ┌───┴───┐          ┌────┴────┐         ┌───┴────┐
   DAU   Total       Active   New        Avg   Peak
   10M   100M         10M     1M/d      1.2K   24K/s
                                              QPS   QPS

                    STORAGE BREAKDOWN
    ┌─────────────┬──────────────┬─────────────┐
    │  Auctions   │    Bids      │    Users    │
    │   2.2 TB    │   55 TB      │   0.1 TB    │
    │  (3 years)  │  (3 years)   │             │
    └─────────────┴──────────────┴─────────────┘
            TOTAL: ~60 TB (3-year projection)

                REAL-TIME CONNECTIONS
    ┌──────────────────────────────────────┐
    │  WebSocket: 1M concurrent users      │
    │  → 100 servers × 10K connections     │
    │  → 166K messages/sec broadcast       │
    └──────────────────────────────────────┘
```

**Memory Trigger**: Think **"U.A.B."** = Users, Auctions, Bids

---

## 🏗️ PART 7: Capacity Planning Formula

### The "5-Dimension Auction Scale Model"

```
For any auction system, estimate these 5 dimensions:

1. AUCTION INVENTORY
   Active_Auctions = New_Per_Day × Avg_Duration
   Example: 1M/day × 7 days = 7M active auctions

2. BID THROUGHPUT
   Avg_Bids_Per_Sec = (Auctions_Per_Day × Bids_Per_Auction) ÷ 86400
   Peak_Multiplier = 20x (for last-hour cluster)
   Example: (1M × 100) ÷ 86.4K = 1,200 QPS avg
            1,200 × 20 = 24,000 QPS peak

3. STORAGE GROWTH RATE
   Daily_Growth = New_Auctions × Auction_Size + Bids × Bid_Size
   Example: 1M × 2KB + 100M × 500B = 2GB + 50GB = 52 GB/day
            52GB × 365 = ~19 TB/year

4. REAL-TIME CONNECTIONS
   Concurrent_Watchers = DAU × Watch_Ratio
   Example: 10M × 10% = 1M concurrent WebSocket connections

5. HOT SPOT INTENSITY
   Hot_Auctions = Total × Popularity_Threshold
   Peak_Per_Hot = Final_Minute_Bids
   Example: 10M × 1% = 100K hot auctions
            500 bids/min on each hot auction
```

---

## 🎯 PART 8: The Interview Cheat Sheet for Auction Systems

```
┌──────────────────────────────────────────────────┐
│  AUCTION SYSTEM SCALE ESTIMATION - 10 MIN RITUAL │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify Auction Type:
      - Fixed duration (7 days) or flexible?
      - Auto-bidding supported?
      - Buy-now option?

[ ] 2. Estimate Core Metrics:
      ✓ Total users, DAU (10% typical)
      ✓ Active auctions (new/day × duration)
      ✓ Bids per auction (100 typical)

[ ] 3. Calculate Bid Throughput:
      ✓ Average QPS = (auctions × bids) ÷ 86.4K
      ✓ Peak QPS = Average × 20 (Pareto!)

[ ] 4. Identify Hot Spots:
      ✓ Popular auctions (1% of total)
      ✓ Final minute rush (100x multiplier)
      ✓ Concurrent bids on same auction

[ ] 5. Storage Breakdown:
      ✓ Auction metadata: 2KB per auction
      ✓ Bid records: 500 bytes per bid
      ✓ User data: 1KB per user

[ ] 6. Real-Time Considerations:
      ✓ WebSocket connections = DAU × 10%
      ✓ Notification rate = Bids/sec × (watchers + 2)

[ ] 7. Database Sharding:
      ✓ Shard by auction_id (isolate hot spots)
      ✓ 16-32 shards typical
      ✓ Each shard: Peak_QPS ÷ num_shards

[ ] 8. Cache Strategy:
      ✓ Hot auctions (20% of total)
      ✓ Current bid cache (100K most active)
      ✓ User sessions (10KB per active user)

[ ] 9. Sanity Check:
      ✓ Does 24K peak QPS seem reasonable? (YES for eBay)
      ✓ Can we handle 500 bids/sec on one auction? (With locking, YES)
      ✓ Is 60TB storage for 3 years realistic? (YES)

[ ] 10. Discuss Trade-offs:
      ✓ Strong consistency (slower) vs Eventual (faster)
      ✓ Optimistic locking (retries) vs Pessimistic (serialized)
      ✓ Real-time (<100ms) vs Near real-time (<1s)
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** | **How to Size** |
|------------|-----------|-------------------|-----------------|
| **Avg Bid QPS** | 1,200 | Database write capacity | Auctions/day × Bids ÷ 86.4K |
| **Peak Bid QPS** | 24,000 | Infrastructure sizing | Avg × 20 (Pareto) |
| **Hot Spot QPS** | 500 per auction | Concurrency control design | Bids in final minute |
| **WebSocket Conn** | 1M | Server count | DAU × 10% |
| **Notification QPS** | 14K | Message queue sizing | Bids/sec × (watchers + 2) |
| **Storage (3yr)** | 60 TB | Database sizing | (Auctions + Bids) × time |
| **Cache Size** | 15 GB | Redis cluster sizing | Hot auctions × 2KB |
| **Auction End Rate** | 333/sec | Scheduler job capacity | Auctions/day ÷ 86.4K |

---

## 💡 Pro Architect Tips for Auction Systems

### **Tip 1: The Ending Auction Smell Test**
After calculations, ask:
- "Can we handle 100K auctions ending in 5 minutes?"
  → 100K ÷ 5 min = 333/sec (Scheduler can handle? YES)

- "Can one database shard handle 1,500 bids/sec?"
  → With PostgreSQL + SSD + optimistic locking? YES (borderline)
  → Need connection pooling + tuning

- "Can we broadcast 16K WebSocket messages/sec?"
  → 100 servers × 166 msg/sec each? YES

### **Tip 2: The Bid Contention Multiplier**
Always ask: "What if 100 people bid on same auction in last second?"
- Optimistic locking retry rate: ~60%
- Need 3 retry attempts average
- Effective throughput: 100 bids ÷ 1.6 = 62 successful bids/sec
- Latency impact: 100ms → 300ms (due to retries)

### **Tip 3: The Auction Timeline Pattern**
Recognize the temporal distribution:

```
Day 1     Day 2-5    Day 6     Last Hour  Last 5 Min
  |          |         |           |           |
 5%        15%       30%         30%         20%  ← Bid distribution
  ↓          ↓         ↓           ↓           ↓
 Low      Low      Medium       High       PEAK  ← Server load
```

This pattern helps you:
- Size infrastructure for peak (not average)
- Plan cache warming strategies
- Design auto-scaling triggers

---

## 🔁 Repetition Backed by Emotion (Your Power Principle!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"80% of bids in last 20% of time - auctions CLUSTER at the end!"*
2. *"Hot spots are REAL - one auction can get 500 bids/minute!"*
3. *"Peak is 20x average - always multiply by Pareto factor!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say:
"So with 1 million auctions per day and 100 bids each, that's 100 million bids per day. Divided by 86,400 seconds gives us about 1,200 bids per second on average. BUT - and this is critical for auction systems - we need to account for the Pareto effect. 80% of bids happen in the final 20% of auction time, so our peak load during the 4-8 PM window will be around 24,000 bids per second. That's why we need robust concurrency control with optimistic locking and distributed locks for hot auctions..."

---

## 📚 Quick Reference: Auction System Benchmarks

| **System Type** | **DAU** | **Active Auctions** | **Avg Bid QPS** | **Peak Bid QPS** | **Storage (1yr)** |
|-----------------|---------|---------------------|-----------------|------------------|-------------------|
| **eBay (Large)** | 10M | 10M | 1,200 | 24K | 20 TB |
| **eBay (Small)** | 1M | 1M | 120 | 2.4K | 2 TB |
| **Christie's** | 100K | 5K | 10 | 200 | 50 GB |
| **DealDash** | 500K | 50K | 60 | 1.2K | 300 GB |

---

## 🔧 Practical Application: Adapting This Template

### For a **Stock Trading** system (similar patterns):
```
STEP 1: TRAFFIC
- Write: Orders placed (market, limit, stop)
- Read: Price quotes, order book, portfolio
- Ratio: 1:10 (more balanced than auctions)

STEP 2: STORAGE
- Order: 200 bytes (order_id, user, symbol, price, qty, type, time)
- Trade: 300 bytes (execution details)
- Apply retention: 7 years (compliance)

STEP 3: HOT SPOTS
- Popular stocks (AAPL, TSLA): 10K orders/sec
- Market open (9:30 AM): 100x spike
- Earnings announcements: 50x spike

STEP 4: LATENCY
- Critical: <10ms (vs auctions <100ms)
- Need in-memory matching engine
- Co-location with exchanges
```

### For a **Real Estate Bidding** system:
```
STEP 1: DIFFERENT PATTERNS
- Longer auction duration (30-60 days typical)
- Fewer bids per listing (5-20 typical)
- Higher verification requirements
- Less real-time pressure

STEP 2: TRAFFIC
- Much lower QPS (100x less than eBay)
- Predictable patterns (business hours)
- Geographic clustering (local markets)

STEP 3: STORAGE
- Larger item data (photos, documents, legal)
- Document storage: 50MB per listing
- Total storage dominated by media (not metadata)
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Sneaker Resale Platform (Like StockX)

```
Given:
- 1M DAU
- 100K active listings
- 10K new listings/day
- Avg 20 bids per listing
- Listing duration: 3 days
- Peak: Product drops at 10 AM EST

Calculate:
1. Average Bid QPS
2. Peak Bid QPS (during product drop)
3. Storage after 1 year
4. WebSocket connections needed

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. AVERAGE BID QPS:
   - Listings/day = 10K
   - Bids/listing = 20
   - Total bids/day = 10K × 20 = 200K
   - Avg QPS = 200K ÷ 86.4K = ~2.3 bids/sec

2. PEAK BID QPS (Product Drop):
   - Hot releases: 1% of listings = 100 listings/day
   - Each hot listing: 500 bids in first hour
   - 100 × 500 = 50K bids in 1 hour
   - Peak QPS = 50K ÷ 3600 = ~14 bids/sec per drop
   - Multiple drops happening: 14 × 10 = 140 bids/sec

3. STORAGE (1 year):
   - Listings: 10K × 365 × 2KB = 7.3 GB
   - Bids: 200K × 365 × 500B = 36.5 GB
   - Total: ~44 GB (very small!)

4. WEBSOCKET CONNECTIONS:
   - Users watching drops: 10% of DAU = 100K
   - 100K concurrent connections
```
</details>

---

### Problem 2: Charity Auction Platform

```
Given:
- 50K DAU
- 5K active auctions
- 500 new auctions/day
- Avg 30 bids per auction
- Auction duration: 14 days
- Peak: Gala events (8 PM, 100 auctions end simultaneously)

Calculate:
1. Average Bid QPS
2. Peak Bid QPS during gala
3. Database sharding strategy
4. Notification load during peak

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. AVERAGE BID QPS:
   - Bids/day = 500 auctions × 30 bids = 15K
   - Avg QPS = 15K ÷ 86.4K = ~0.17 bids/sec (very low!)

2. PEAK BID QPS (Gala Event):
   - 100 auctions ending simultaneously
   - Each auction: 50 bids in final 5 minutes
   - 100 × 50 = 5,000 bids in 5 minutes
   - Peak QPS = 5,000 ÷ 300 = ~17 bids/sec

3. DATABASE SHARDING:
   - Low traffic → Single database sufficient
   - Use read replicas (3 replicas)
   - No need for sharding at this scale

4. NOTIFICATION LOAD:
   - Each auction has ~20 watchers
   - 100 auctions × 20 watchers = 2,000 notifications
   - Spread over 5 minutes = 400 notifications/min = 7/sec
   - Very manageable!
```
</details>

---

## 🚨 Common Mistakes to Avoid (Auction Systems)

### Mistake 1: **Ignoring the Pareto Clustering**
```
✗ BAD:  "We have 1,200 bids/sec average, so plan for 2,400 peak"
✓ GOOD: "Auction systems have 20x peaks due to end-time clustering,
         so we need to handle 24,000 bids/sec"
```

### Mistake 2: **Not Accounting for Hot Spots**
```
✗ BAD:  "1,200 QPS across 16 shards = 75 QPS per shard"
✓ GOOD: "Popular auctions create hot spots - one auction can get
         500 bids/min, so we need distributed locking + retries"
```

### Mistake 3: **Forgetting Notification Multiplier**
```
✗ BAD:  "1,200 bids/sec = 1,200 notifications/sec"
✓ GOOD: "Each bid triggers notifications to previous winner + N watchers,
         so 1,200 × (10 + 2) = 14,400 notifications/sec"
```

### Mistake 4: **Using Eventual Consistency**
```
✗ BAD:  "We can use eventual consistency like social media systems"
✓ GOOD: "Auction systems REQUIRE strong consistency to prevent two
         winners. Must use ACID transactions with optimistic locking"
```

### Mistake 5: **Not Planning for Auction End Jobs**
```
✗ BAD:  "Auctions just end when time expires"
✓ GOOD: "Need scheduled jobs to end auctions precisely, determine
         winners, initiate payments. 333 auctions/sec need processing"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
AUCTION SYSTEM: ___________________

CORE METRICS:
───────────────────────────
Users (DAU):                    [____] M
Active Auctions:                [____] M
New Auctions/Day:               [____] K
Avg Auction Duration:           [____] days
Avg Bids Per Auction:           [____]

BID THROUGHPUT:
───────────────────────────
Total Bids/Day = [____] K auctions × [____] bids = [____] M
Avg QPS = [____] M ÷ 86.4K = [____]
Peak Multiplier = 20x (Pareto)
Peak QPS = [____] × 20 = [____]

HOT SPOT ANALYSIS:
───────────────────────────
Popular Auctions (1%) = [____]
Bids in Final Minute = [____]
Peak Per Hot Auction = [____] bids/min

STORAGE (3 years):
───────────────────────────
Auctions = [____] M × 3 years × 2KB = [____] TB
Bids = [____] M/day × 365 × 3 × 500B = [____] TB
Total = [____] TB

REAL-TIME:
───────────────────────────
WebSocket Connections = DAU × 10% = [____]
Notification Rate = Bids/sec × (watchers + 2) = [____]

DATABASE SHARDING:
───────────────────────────
Num Shards = [____] (typically 16-32)
Peak QPS Per Shard = [____] ÷ [____] = [____]

CACHE:
───────────────────────────
Hot Auctions (20%) = [____] × 2KB = [____] GB
Current Bids = 100K × 500B = 50 MB
User Sessions = [____] × 10KB = [____] GB
Total Cache = [____] GB
```

---

## 🎁 Bonus: Auction System Scale Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║        AUCTION SYSTEM SCALE ESTIMATION CHEAT SHEET     ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Pareto: 80% bids in last 20% of time
• Peak Multiplier: 20x for auctions (not 2x!)
• Hot Auction: 500 bids in final minute
• Notification Factor: Bids × (watchers + 2)

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Active_Auctions = New_Per_Day × Duration
Avg_Bid_QPS = (Auctions × Bids) ÷ 86.4K
Peak_Bid_QPS = Avg × 20
Hot_Spot_QPS = Final_Min_Bids ÷ 60
Storage = Auctions × 2KB + Bids × 500B

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• DAU:Total = 10% (auction engagement)
• Sellers:Users = 2%
• Hot Auctions:Total = 1%
• Bids Per Auction = 100 (average)
• Auction Duration = 7 days (typical)
• WebSocket Connections = DAU × 10%

CONSISTENCY REQUIREMENTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Bids:          Strong Consistency (ACID)
Auction View:  Eventual OK (cache <30s)
Search:        Eventual OK (index <1min)
Notifications: At-least-once delivery

HOT SPOT MITIGATION:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Distributed Lock (Redis): Serialize writes
• Optimistic Locking: Retry on conflict (3x)
• Sharding: Isolate hot auctions
• Connection Pooling: Reuse DB connections

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify auction type & duration (5 min)
2. Estimate core metrics (DAU, auctions, bids)
3. Calculate QPS with 20x peak multiplier
4. Identify hot spots (1% popular auctions)
5. Storage breakdown (auctions + bids)
6. Real-time: WebSocket + notifications
7. Database sharding strategy
8. Cache sizing (hot auctions)
9. Sanity check all numbers
10. Discuss concurrency control

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can shard handle 1,500 bids/sec? YES (with tuning)
✓ Can Redis cache 15GB? YES (typical r5.xlarge: 32GB)
✓ Can server handle 10K WebSocket conn? YES
✓ Can scheduler process 333 auction ends/sec? YES

✗ Can single DB handle 24K writes/sec? NO (need sharding)
✗ Can eventual consistency work for bids? NO (need ACID)
✗ Can we ignore Pareto effect? NO (critical for sizing!)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these auction/marketplace systems and practice the full estimation:

1. **Airbnb** - Booking system with bidding-like patterns
2. **Uber** - Ride matching (similar to auction mechanics)
3. **Stock Trading** - Order matching with high-frequency requirements
4. **NFT Marketplace** - Digital asset auctions
5. **Government Procurement** - B2B reverse auctions

Use the blank template above and time yourself: **Can you complete it in 10 minutes?**

---

**Remember**:
> "Auction systems are ALL ABOUT CLUSTERING - 80% of activity in final 20% of time. Miss this and you'll undersize by 10x!"

**Now go design world-class auction platforms!** 🏛️🚀

---

*Created with the AUCTION-SCALE framework: Tailored for real-time bidding, marketplace, and auction systems*
*Perfect for: eBay, Christie's, StockX, DealDash, Marketplace platforms interviews*
