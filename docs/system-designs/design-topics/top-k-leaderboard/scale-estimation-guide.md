# 🎯 Top K Leaderboard: Scale Estimation Masterclass

## The POWER Technique Applied to Leaderboard Systems
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This is a **mental framework** optimized for real-time ranking systems with high write throughput.

---

## 📊 PART 1: Gaming System Scale Patterns

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Registered Users | 100M | Mid-tier gaming platform (Candy Crush, Clash Royale scale) |
| | Daily Active Users (DAU) | 10M | ~10% daily engagement (typical for mobile games) |
| | Concurrent Users (Peak) | 2M | ~20% of DAU online simultaneously |
| | Power Users | 1M | ~10% of DAU, heavy engagement |
| **User Behavior** | Score Updates/User/Day | 10 | Each game session updates score |
| | Leaderboard Queries/User/Day | 5 | Users check rankings frequently |
| | Avg Session Duration | 15 min | Typical mobile game session |
| | Sessions per Day per User | 3 | Morning, lunch, evening |
| **Time Distribution** | Peak Hours | 6pm-10pm | Evening gaming peak (4 hours) |
| | Peak Traffic Multiplier | 5x | Concentrated evening usage |
| **Data Characteristics** | Avg Score Value | 1000-100K | Game-dependent (points, trophies) |
| | Score Update Size | 16 bytes | user_id (8B) + score (8B) |
| | Leaderboard Types | 20 | Global, regional (10), daily, weekly, monthly, friends |

### Key Gaming System Patterns

**Pattern 1: Write-Heavy Workload**
- Gaming leaderboards are **write-dominant** (opposite of TinyURL)
- Read:Write ratio = **1:2** (for every 1 read, 2 writes)
- Score updates happen continuously during gameplay

**Pattern 2: Temporal Spikes**
- **Event-driven spikes**: Tournament finals, season resets
- **Geographic waves**: Asia peak → EU peak → US peak (24hr cycle)
- **Weekend effect**: 3-5x higher traffic on Sat/Sun

**Pattern 3: Long-Tail Distribution**
- Top 1% users generate 50% of all writes
- Bottom 50% users are dormant (weekly active only)
- **Pareto in reverse**: Small user base, massive activity

---

## 🧮 PART 2: The "Gamer's Calculator" - Leaderboard Math Toolkit

### Rule #1: **The Gaming Time Distribution**
```
Remember these anchors:
• Peak Hour Traffic = 5x Average  (vs 2x for typical systems)
• Concurrent Users  = 20% of DAU   (high engagement)
• Session Frequency = 3 per day    (mobile gaming pattern)
• Score Update Rate = 1 per minute (during active session)
```

### Rule #2: **The Write Amplification Factor**
```
Single score update triggers:
1. Global leaderboard write     (1x)
2. Regional leaderboard write   (1x)
3. Daily leaderboard write      (1x)
4. Friends leaderboard writes   (N friends, avg 50)
──────────────────────────────────────
Total writes per update = 53x amplification!

Solution: Update only actively queried leaderboards
Practical writes = 3-5x (global + region + time-based)
```

### Rule #3: **The In-Memory Sizing**
```
Per-user storage in Redis Sorted Set:
- Member (user_id): 8 bytes
- Score: 8 bytes
- Overhead: ~50 bytes (Redis internal structures)
──────────────────────────
Total per user: ~70 bytes

For 100M users:
100M × 70 bytes = 7 GB raw data
With Redis overhead (2x): ~14 GB per leaderboard
With 20 leaderboard types: 280 GB total
```

---

## 📈 PART 3: Leaderboard Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│  🎯 LEADERBOARD NAPKIN MATH - Universal Template        │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):      [____] M
Score Updates/User/Day:        [____]
Leaderboard Queries/User/Day:  [____]

→ Total Writes/Day = DAU × Updates    = [____] M
→ Total Reads/Day  = DAU × Queries    = [____] M
→ Write QPS = Writes/Day ÷ 100K       = [____]
→ Read QPS  = Reads/Day ÷ 100K        = [____]
→ Peak QPS  = Average × 5 (gaming!)   = [____]

STEP 2: MEMORY ESTIMATION (Critical for In-Memory!)
───────────────────────────
Total Users in Leaderboard:    [____] M
Bytes per User Entry:          ~70 bytes (user_id + score + overhead)
Number of Leaderboard Types:   [____]

→ Single LB Memory = Users × 70 bytes     = [____] GB
→ Total Memory = Single × LB_Types × 2    = [____] GB
  (2x factor for Redis persistence + replication)

STEP 3: STORAGE ESTIMATION (Historical Data)
───────────────────────────
Score Updates/Day:             [____] M
Bytes per Update Event:        200 bytes (user_id, score, delta, timestamp, metadata)
Retention Period:              [____] days

→ Daily Storage = Updates × 200 bytes  = [____] GB
→ Total Storage = Daily × Retention    = [____] TB

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
→ Write Bandwidth = Write QPS × 200 bytes = [____] MB/s
→ Read Bandwidth  = Read QPS × 1KB        = [____] MB/s
  (Read payload larger: top 100 users with metadata)

STEP 5: REDIS CLUSTER SIZING
───────────────────────────
Redis Instance Limits:
- Max memory: ~200 GB per instance (practical limit)
- Max throughput: ~100K ops/sec per instance

→ Memory-based Shards = Total Memory ÷ 200 GB    = [____]
→ Traffic-based Shards = Peak QPS ÷ 100K         = [____]
→ Final Shard Count = MAX(memory shards, traffic shards)
```

---

## 💾 PART 4: Top K Leaderboard Filled Template

```
┌─────────────────────────────────────────────────────────┐
│   TOP K LEADERBOARD SYSTEM - NAPKIN MATH SOLUTION       │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):      10 M
Score Updates/User/Day:        10
Leaderboard Queries/User/Day:  5

→ Total Writes/Day = 10M × 10       = 100 M writes
→ Total Reads/Day  = 10M × 5        = 50 M reads
→ Write QPS = 100M ÷ 100K           = 1,000 QPS (avg)
→ Read QPS  = 50M ÷ 100K            = 500 QPS (avg)
→ Peak QPS  = (1K + 500) × 5        = 7,500 QPS (evening peak)

Breaking down by operation type:
Write Operations:
  - Score updates: 1,000 QPS
  - Batch updates (events): +200 QPS (during tournaments)
  - Peak write: 6,000 QPS

Read Operations:
  - Top K queries (top 100): 300 QPS
  - User rank queries: 150 QPS
  - Nearby ranks: 50 QPS
  - Peak read: 2,500 QPS

STEP 2: MEMORY ESTIMATION
───────────────────────────
Total Users in Leaderboard: 100 M
Bytes per User Entry:       70 bytes
Number of Leaderboard Types: 20

Breaking down leaderboard types:
1. Global all-time:         100M users × 70B = 7 GB
2. Regional (10 regions):   10M each × 70B = 700 MB each → 7 GB total
3. Daily:                   10M active × 70B = 700 MB
4. Weekly:                  30M active × 70B = 2.1 GB
5. Monthly:                 50M active × 70B = 3.5 GB
6. Friends (materialized):  100M × 50 friends × 70B = 350 GB

→ Core Leaderboards Memory = 7 + 7 + 0.7 + 2.1 + 3.5 = 20.3 GB
→ With Friends Leaderboards = 20.3 + 350 = ~370 GB
→ With Replication (2x) = 740 GB
→ With Safety Margin (1.5x) = ~1.1 TB

**Optimization**: Don't materialize friends leaderboards!
→ Optimized Total Memory = 40 GB (20 GB × 2 for replication)

STEP 3: STORAGE ESTIMATION (Historical)
───────────────────────────
Score Updates/Day:          100 M
Bytes per Event:            200 bytes (full audit trail)
Retention Period:           365 days (1 year)

→ Daily Storage = 100M × 200 bytes     = 20 GB/day
→ Yearly Storage = 20 GB × 365         = 7.3 TB/year
→ With Compression (3x) = 7.3 ÷ 3      = ~2.5 TB/year

Additional storage:
- Leaderboard snapshots (daily): 7 GB × 365 = 2.6 TB/year
- User metadata: 100M × 500 bytes = 50 GB (static)

→ Total Storage (1 year) = 2.5 + 2.6 + 0.05 = ~5.2 TB

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
Peak Scenario (Evening):
Write Traffic:
→ Write Bandwidth = 6,000 QPS × 200 bytes = 1.2 MB/s (write)
  (Negligible - writes are small)

Read Traffic:
→ Top K queries: 1,500 QPS × 10 KB (top 100 with metadata) = 15 MB/s
→ Rank queries:  750 QPS × 500 bytes = 375 KB/s
→ Total Read Bandwidth = 15 + 0.4 = ~15.5 MB/s

Total Bandwidth = 1.2 + 15.5 = ~17 MB/s (~136 Mbps)
Peak with safety margin (2x) = 34 MB/s (~270 Mbps)

STEP 5: REDIS CLUSTER SIZING
───────────────────────────
Memory Requirement: 40 GB (optimized, with replication)
Traffic Requirement: 7,500 peak QPS

Option 1: Single Large Instance
- Memory: 64 GB instance (AWS r7g.2xlarge)
- Throughput: Up to 250K ops/sec
- Cost: ~$400/month
✓ Sufficient for MVP!

Option 2: Sharded Cluster (for 100M+ users)
- Memory-based: 40 GB ÷ 64 GB per instance = 1 instance
- Traffic-based: 7.5K QPS ÷ 100K per instance = 1 instance
- Recommendation: 3 shards (horizontal scaling headroom)
  - Shard 1: Global + Daily + Weekly
  - Shard 2: Regional leaderboards (US, EU, Asia)
  - Shard 3: Monthly + Special events
- Total memory: 3 × 64 GB = 192 GB cluster
- Cost: ~$1,200/month

STEP 6: POSTGRESQL SIZING (Historical Data)
───────────────────────────
Storage: 5.2 TB (1 year retention)
Instance: AWS RDS db.r6g.2xlarge (64 GB RAM, 8 vCPUs)
IOPS: 10,000 IOPS (for batch analytics queries)
Cost: ~$800/month + storage (~$500/month) = $1,300/month
```

---

## 🧠 PART 5: Mental Math Techniques for Leaderboards

### **Technique 1: The "70-Byte Rule"**
*(For Memory Calculations)*
```
EMOTION TRIGGER: "Every user = 70 bytes in Redis!"

Quick Memory Math:
1 Million users   = 70 MB
10 Million users  = 700 MB
100 Million users = 7 GB
1 Billion users   = 70 GB

Add 2x for replication: Double the number!
100M users → 14 GB with replication
```

### **Technique 2: The "5x Gaming Peak"**
*(For QPS Calculations)*
```
EMOTION TRIGGER: "Gamers are evening warriors - 5x peak!"

Unlike typical web apps (2x peak), gaming has:
- Concentrated evening hours (6pm-10pm)
- Weekend spikes (Sat/Sun 3x higher)
- Event spikes (tournament finals 10x)

Always use 5x multiplier for peak planning!
Average 1K QPS → Plan for 5K peak QPS
```

### **Technique 3: The "Write Amplification Trap"**
*(Avoiding Over-Engineering)*
```
Naive approach:
Update global + 10 regional + daily + weekly + monthly + 50 friends
= 1 + 10 + 1 + 1 + 1 + 50 = 64 writes per update!

Smart approach (lazy evaluation):
Only update actively queried leaderboards:
= Global + User's region + Active time-based = 3 writes

Savings: 64x → 3x = 21x reduction in write load!
```

### **Technique 4: The "Sorted Set Tax"**
*(Redis Memory Overhead)*
```
Redis Sorted Set overhead is ~2x raw data

Raw: user_id (8B) + score (8B) = 16 bytes
With Redis overhead: ~70 bytes per entry

Mental model: Multiply raw size by 4-5x for final Redis memory
100M × 16 bytes = 1.6 GB raw
Reality: 7 GB in Redis (4.4x overhead)
```

---

## 🎨 PART 6: The Visual Mind Map for Leaderboard Scale

```
                    🎮 TOP K LEADERBOARD SYSTEM
                              |
        ┌─────────────────────┼─────────────────────┐
        |                     |                     |
    📊 SCALE              💾 MEMORY            🗄️ STORAGE
        |                     |                     |
    ┌───┴───┐           ┌─────┴─────┐        ┌─────┴─────┐
  10M      6K QPS      40GB    Redis      5.2TB   History
  DAU      Write      In-Mem   Cluster    1yr     PostgreSQL
```

**Memory Trigger**: Think **"S.M.S."** = Scale, Memory, Storage
- **Scale**: 10M DAU, 6K write QPS
- **Memory**: 40 GB Redis (in-memory critical!)
- **Storage**: 5 TB history (audit trail)

---

## 🏗️ PART 7: Comparison with Other System Patterns

### Leaderboard vs TinyURL vs Social Feed

| **Metric** | **Top K Leaderboard** | **TinyURL** | **Social Feed** |
|------------|-----------------------|-------------|-----------------|
| **Read:Write Ratio** | 1:2 (write-heavy!) | 100:1 (read-heavy) | 10:1 (read-heavy) |
| **Data Structure** | Sorted Set (ordered) | Key-Value (hash) | Time-series (sorted) |
| **Primary Store** | Redis (in-memory) | PostgreSQL (disk) | Cassandra (disk) |
| **Latency Req** | <50ms (real-time) | <100ms (acceptable) | <200ms (ok) |
| **Consistency** | Eventual (AP) | Strong (CA) | Eventual (AP) |
| **Memory Critical?** | YES (must fit in RAM) | NO (cache layer) | NO (disk-based) |
| **Peak Pattern** | 5x evening spike | 2x daily spike | 3x lunch/evening |
| **Sharding Strategy** | By leaderboard type | By short_code hash | By user_id |

**Key Insight**: Leaderboards are **fundamentally different** from typical CRUD systems!

---

## 🎯 PART 8: The Interview Cheat Sheet for Leaderboards

```
┌──────────────────────────────────────────────────┐
│  LEADERBOARD SCALE ESTIMATION - 5 MIN RITUAL     │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify user base: DAU, total users, concurrent users
[ ] 2. Identify leaderboard types: Global? Regional? Time-based?
[ ] 3. Calculate QPS: Updates/day ÷ 100K (use 5x for peak!)
[ ] 4. Calculate Memory: Users × 70 bytes × LB_types × 2
[ ] 5. Check in-memory feasibility: <200 GB per Redis instance?
[ ] 6. Plan sharding: By leaderboard type (simplest)
[ ] 7. Smell test: Can Redis handle the traffic? (100K ops/sec limit)
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Write QPS (Avg)** | 1,000 | Determines Redis write capacity |
| **Write QPS (Peak)** | 6,000 | Size your Redis cluster for peaks |
| **Read QPS (Peak)** | 2,500 | Cache layer sizing |
| **Memory (Redis)** | 40 GB | In-memory storage requirement |
| **Storage (1yr)** | 5.2 TB | Historical data (PostgreSQL/S3) |
| **Bandwidth (Peak)** | 270 Mbps | Network capacity planning |
| **Users per LB** | 100M | Determines single instance vs cluster |
| **Leaderboard Types** | 20 | Memory amplification factor |
| **Latency Target** | <50ms | P95 for rank queries |

---

## 💡 Pro Architect Tips - Leaderboard Edition

### **Tip 1: The In-Memory Limit**
After calculations, ask:
- "Can 40 GB fit in single Redis instance?" → YES (64 GB instance)
- "Can we afford 40 GB in-memory?" → YES (~$400/month)
- "What if it grows to 1B users?" → 400 GB → Need sharding

**Rule of Thumb**: If total memory > 200 GB, shard the cluster

### **Tip 2: The "Friends Leaderboard" Trap**
Materializing friends leaderboards:
- 100M users × 50 friends × 70 bytes = **350 GB** just for friends!
- Alternative: Compute on-read (query-time filtering)
- Trade-off: Memory vs Latency

**Decision**: Only materialize for power users (<1M users)

### **Tip 3: The Time-Based Optimization**
Daily leaderboard:
- Only 10M active users (not 100M)
- Daily memory: 700 MB (not 7 GB)
- Reset at midnight: Archive to S3, start fresh
- Cost saving: 10x reduction in memory

### **Tip 4: The Regional Sharding Win**
Instead of one 100M user global leaderboard:
- Shard by region: US (30M), EU (25M), Asia (35M), Others (10M)
- Each shard: 2-3 GB (manageable)
- Query pattern: 95% users query their own region
- Cross-region query: Merge top K from each shard (rare)

---

## 🔁 Repetition Backed by Emotion (Gaming Edition!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"70 bytes per user - I can size any leaderboard in my head!"*
2. *"Gaming is 5x peak, not 2x - evening warriors are real!"*
3. *"In-memory is king - if it doesn't fit in Redis, rethink the design!"*

**VISUALIZE:** You're at the whiteboard, the interviewer asks: "How much memory for 100M users?"
You confidently respond: "100M × 70 bytes = 7 GB raw, 14 GB with replication, done!"

---

## 📚 Quick Reference: Leaderboard System Benchmarks

| **System** | **Users** | **Write QPS** | **Read QPS** | **Memory** | **Company** |
|------------|-----------|---------------|--------------|------------|-------------|
| Candy Crush Leaderboard | 50M | 2K | 1K | 20 GB | King |
| League of Legends Ranked | 100M+ | 5K | 3K | 50 GB | Riot Games |
| Clash Royale Trophies | 100M | 10K | 5K | 70 GB | Supercell |
| Steam Achievements | 150M | 3K | 8K | 100 GB | Valve |
| Xbox Live Gamerscore | 200M+ | 15K | 10K | 150 GB | Microsoft |

**Pattern**: Most leaderboards fit in **<100 GB RAM** with smart sharding!

---

## 🔧 Practical Application: Adapting the Template

### For a **Real-Time Competitive Game** (e.g., Battle Royale):

```
STEP 1: TRAFFIC
- DAU: 20M (high engagement)
- Matches per day per user: 5
- Score update per match: 1
- Writes: 20M × 5 = 100M updates/day = 1,200 QPS
- Peak: 1,200 × 10 = 12,000 QPS (evening + weekend)

STEP 2: MEMORY
- Global leaderboard: 20M users × 70B = 1.4 GB
- Regional (5 regions): 4M each × 70B × 5 = 1.4 GB
- Season (monthly): 15M active × 70B = 1 GB
- Total: 1.4 + 1.4 + 1 = 3.8 GB
- With replication: 7.6 GB (easy!)

STEP 3: LATENCY
- During match: Not queried (players are playing)
- Post-match: Batch update (all 100 players in match)
- Optimization: Batch writes reduce QPS by 100x!
```

### For a **Fitness App** (e.g., Strava segments):

```
STEP 1: TRAFFIC
- DAU: 5M (fitness enthusiasts)
- Activities per day: 1
- Leaderboard queries: 3 (before, during, after activity)
- Writes: 5M × 1 = 5M updates/day = 60 QPS
- Reads: 5M × 3 = 15M queries/day = 180 QPS
- Pattern: Morning peak (6-9am), Evening peak (5-8pm)

STEP 2: MEMORY
- Segments: 10K popular segments (not global users!)
- Users per segment: 1K avg (power law distribution)
- Memory: 10K segments × 1K users × 70B = 700 MB
- Top 1K segments have 100K users each: 7 GB
- Total: 8 GB (single Redis instance)

KEY INSIGHT: Leaderboards are per-segment, not global!
Much smaller memory footprint than gaming leaderboards.
```

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting Replication Factor**
```
✗ BAD:  "100M users × 70B = 7 GB, we need 8 GB instance"
✓ GOOD: "7 GB raw, 14 GB with replication, need 16-32 GB instance"
```

### Mistake 2: **Using Average Instead of Peak**
```
✗ BAD:  "1K average QPS, single Redis handles 100K, we're good"
✓ GOOD: "1K avg, but 6K peak in evening, need headroom for 10K"
```

### Mistake 3: **Materializing Everything**
```
✗ BAD:  "Store all 20 leaderboard types for all 100M users"
✓ GOOD: "Only materialize global + regional + active time-based"
```

### Mistake 4: **Ignoring Write Amplification**
```
✗ BAD:  "Single score update = single write"
✓ GOOD: "Update triggers 3-5 writes (global, region, daily, etc.)"
```

### Mistake 5: **SQL for Real-Time Ranks**
```
✗ BAD:  "Use PostgreSQL with ORDER BY and LIMIT"
✓ GOOD: "Use Redis Sorted Sets - O(log N) vs O(N log N)"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
SYSTEM: TOP K LEADERBOARD FOR _______________

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):     [____] M
Score Updates/User/Day:       [____]
Leaderboard Queries/User/Day: [____]

Total Writes/Day:         [____] M
Total Reads/Day:          [____] M

→ Write QPS (avg) = [____] M ÷ 100K = [____]
→ Read QPS (avg)  = [____] M ÷ 100K = [____]
→ Peak QPS  = [____] × 5 (gaming)   = [____]

STEP 2: MEMORY ESTIMATION
───────────────────────────
Total Users:              [____] M
Leaderboard Types:        [____]
Bytes per User:           70 bytes (fixed)

→ Single LB = [____] M × 70B       = [____] GB
→ Total Memory = [____] GB × [____] types = [____] GB
→ With Replication (2x) = [____] × 2 = [____] GB

STEP 3: IN-MEMORY FEASIBILITY
───────────────────────────
Redis Instance Limit:     200 GB (practical)

→ Single Instance? [YES/NO]
→ Shards Needed = [____] GB ÷ 200 GB = [____]

STEP 4: HISTORICAL STORAGE
───────────────────────────
Score Updates/Day:        [____] M
Bytes per Event:          200 bytes
Retention:                [____] days

→ Daily Storage  = [____] M × 200B   = [____] GB
→ Total Storage = [____] GB × [____] = [____] TB

SMELL TEST:
───────────────────────────
□ Memory fits in Redis? (<200 GB per instance)
□ Peak QPS reasonable? (<100K per Redis instance)
□ Latency achievable? (Redis O(log N) < 50ms)
□ Cost acceptable? (Redis ~$2-5 per GB/month)
```

---

## 🎁 Bonus: Leaderboard Scale Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║      TOP K LEADERBOARD SCALE ESTIMATION CHEAT SHEET    ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1M users    = 70 MB (Redis Sorted Set)
• 10M users   = 700 MB
• 100M users  = 7 GB
• 1B users    = 70 GB
• Always 2x for replication!

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
QPS = (DAU × Updates_per_day) ÷ 100K
Memory = Users × 70 bytes × LB_types × 2
Storage = Updates/day × 200 bytes × Days
Peak_Multiplier = 5x (gaming systems)

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Read:Write = 1:2 (write-heavy for gaming)
• Peak:Avg = 5x (evening + weekend)
• DAU:Total = 10% (typical engagement)
• Active:Total Users = 20% (monthly active)
• Top 1% users = 50% of writes (power law)

REDIS SORTED SET LIMITS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Max members: 2^32 (4 billion users) ✓
• Max memory per instance: ~200 GB (practical)
• Max throughput: ~100K ops/sec per instance
• Latency: O(log N) for updates, ranks

QUICK ESTIMATES BY SCALE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:    <1M users,   <100 QPS,  <1 GB,      Single Redis
Medium:   1-10M users, 100-1K QPS, 1-10 GB,   Single Redis
Large:    10-100M,     1K-10K QPS, 10-100 GB, Redis Cluster (3-5 shards)
Massive:  100M-1B,     10K-100K,   100GB-1TB, Redis Cluster (10-50 shards)

SHARDING STRATEGIES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. By Leaderboard Type (easiest):
   - Shard 1: Global all-time
   - Shard 2: Regional (US, EU, Asia)
   - Shard 3: Time-based (daily, weekly, monthly)

2. By Score Range (complex, for massive scale):
   - Shard 1: Top 1K users (hot tier)
   - Shard 2: Rank 1K-1M (warm tier)
   - Shard 3: Rank 1M+ (cold tier)

3. By Region (geo-distributed):
   - US-East Redis cluster
   - EU-West Redis cluster
   - APAC Redis cluster

COST ESTIMATES (AWS/GCP):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Small (8 GB): ~$150/month (r7g.large)
• Medium (32 GB): ~$400/month (r7g.xlarge)
• Large (64 GB): ~$800/month (r7g.2xlarge)
• Massive (256 GB): ~$3,200/month (r7g.8xlarge)

Managed Redis (ElastiCache, Redis Enterprise):
• Add 30-50% premium for managed service

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Single Redis handles 100M users? YES (7 GB fits)
✓ Redis Sorted Set O(log N) < 50ms? YES (27 ops for 100M)
✓ Peak 10K QPS on single instance? YES (limit 100K)
✓ Daily leaderboard < 1 GB? YES (only 10M DAU)

✗ Materialize friends LB for 100M users? NO (350 GB!)
✗ Use SQL for real-time ranks? NO (O(N) scans)
✗ Store 5 years of history in Redis? NO (100+ TB)
✗ Synchronous replication? NO (latency killer)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Practice Problems

### Problem 1: Fitness App Leaderboard (Strava-like)
```
Given:
- 10M DAU
- 100K popular running segments
- Each user runs 1 segment per day
- Each user checks leaderboard 2 times per activity
- Avg 500 users per segment leaderboard

Calculate:
1. Write QPS (avg and peak)
2. Read QPS
3. Memory required
4. Number of Redis instances needed

[Try it yourself, then check answer below]
```

<details>
<summary>Answer</summary>

```
1. WRITE QPS:
   - Activities/day = 10M × 1 = 10M
   - Write QPS = 10M ÷ 100K = 100 QPS (avg)
   - Peak (morning 6-9am): 100 × 5 = 500 QPS ✓

2. READ QPS:
   - Queries/day = 10M × 2 = 20M
   - Read QPS = 20M ÷ 100K = 200 QPS (avg)
   - Peak: 200 × 5 = 1,000 QPS ✓

3. MEMORY:
   - Segments: 100K
   - Users per segment: 500 avg
   - Memory = 100K × 500 × 70 bytes = 3.5 GB
   - With replication: 7 GB ✓
   - Single instance sufficient!

4. REDIS INSTANCES:
   - Memory: 7 GB < 64 GB instance limit ✓
   - Traffic: 1.5K peak QPS < 100K limit ✓
   - Answer: 1 instance (r7g.large 16 GB) at ~$200/month
```
</details>

---

**Remember**:
> "In leaderboard design, IN-MEMORY is not optional - it's the foundation. If your design doesn't fit in Redis, you're not designing a real-time leaderboard, you're designing a batch reporting system."

**Now go dominate those leaderboard design interviews!** 🚀

---

*Created with the POWER technique optimized for real-time ranking systems*
*Perfect for: Gaming companies (Riot, Blizzard, King), Social platforms (Strava, Discord), FAANG system design*
