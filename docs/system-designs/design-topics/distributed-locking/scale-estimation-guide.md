# 🎯 Distributed Locking System: Scale Estimation Masterclass

## The LOCKS Technique for Lock System Capacity Planning
**(L)atency requirements → (O)perations per second → (C)luster sizing → (K)ey metrics → (S)torage calculation**

This is a **mental framework** specifically designed for distributed coordination systems.

---

## 📊 PART 1: Understanding Lock System Scale

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Client Base** | Total Microservices | 500 | Medium-sized distributed system |
| | Active Instances | 2,000 | ~4 instances per service |
| | Concurrent Workers | 10,000 | Including background jobs |
| **Lock Patterns** | Locks/Instance/Min | 10 | Critical sections rate |
| | Lock Hold Duration | 5 seconds | Avg critical section time |
| | Lock Renewal Frequency | 2.5s | TTL/2 for 5s locks |
| | Read:Write Ratio | 3:1 | Check lock status vs acquire |
| **Temporal** | Peak Hour Multiplier | 3x | Business hours concentration |
| | Lock TTL (default) | 30 seconds | Safety margin |
| | Heartbeat Interval | 15 seconds | TTL/2 |

---

## 🧮 PART 2: The "Lock Math Calculator" - Mental Model

### Rule #1: **The Lock Operation Ladder**
```
Lock Operations Hierarchy:
• Acquire Lock    → Expensive (consensus write)
• Renew Lock      → Medium   (consensus write, but cached check)
• Check Lock      → Cheap    (read from follower)
• Release Lock    → Medium   (consensus write + cleanup)

Cost Ratio: Acquire:Renew:Check:Release = 10:5:1:5
```

### Rule #2: **The Consensus Tax**
Every write operation requires consensus (Raft/Paxos):
```
Consensus Overhead:
✗ BAD THINKING:  "1 lock operation = 1 database write"
✓ GOOD THINKING: "1 lock operation = 1 leader write + N-1 follower replicates + quorum wait"

Actual work: 1 operation × 5 nodes × (write + network + ACK) = ~5× base cost
```

### Rule #3: **The Heartbeat Multiplier**
Long-running locks generate continuous heartbeat traffic:
```
If lock held for 5 minutes with 15s heartbeat interval:
→ 1 acquire + (300s / 15s) renewals + 1 release
→ 1 + 20 + 1 = 22 operations per lock lifecycle

Heartbeat traffic = Acquire rate × (Avg hold duration / Heartbeat interval)
```

---

## 📈 PART 3: Distributed Locking Scale Math Template

```
┌─────────────────────────────────────────────────────────────┐
│  🔒 NAPKIN MATH TEMPLATE - Distributed Lock System          │
└─────────────────────────────────────────────────────────────┘

STEP 1: LOCK OPERATION ESTIMATION
───────────────────────────────────
Active Instances:        [____]
Locks per Instance/Min:  [____]
Avg Lock Hold Duration:  [____] seconds

→ Acquire QPS  = Instances × Locks/min ÷ 60     = [____]
→ Renew QPS    = Acquire QPS × (Duration / Heartbeat) = [____]
→ Release QPS  = Acquire QPS (same as acquire)  = [____]
→ Total Write QPS = Acquire + Renew + Release   = [____]

STEP 2: CONSENSUS CLUSTER SIZING
───────────────────────────────────
Write QPS per Node:      [____] (Total / Leader capacity)
Quorum Size:             [____] (N/2 + 1 for N nodes)
Failure Tolerance:       [____] nodes (N - Quorum)

→ Cluster Size = Max(5 nodes, TotalQPS / NodeCapacity)
→ Leader capacity (etcd): ~10K QPS
→ Leader capacity (ZK):   ~5K QPS

STEP 3: STORAGE ESTIMATION
───────────────────────────
Lock Entry Size:         [____] bytes
  - Resource name (100B) + Owner (50B) + Metadata (50B) = ~200B
Active Locks (peak):     [____]
Lock History Retention:  [____] days

→ Active Lock Storage = Active × 200B     = [____] MB
→ History per Day = QPS × 86400 × 200B    = [____] GB/day
→ Total Storage = Active + (History × Days) = [____] GB

STEP 4: NETWORK BANDWIDTH
───────────────────────────
→ Write BW (leader) = Write QPS × 200B × 5 (replication) = [____] MB/s
→ Read BW (followers) = Read QPS × 200B    = [____] MB/s

STEP 5: LATENCY BUDGET
───────────────────────────
Target P99 Lock Acquisition: [____] ms (typically <10ms)

Breakdown:
  - Network RTT (client → leader): 1-2ms
  - Leader log append: 1ms
  - Replication to followers: 2-3ms (parallel)
  - Quorum wait: 2-3ms
  - Response: 1-2ms
→ Total: ~7-12ms (healthy cluster, same datacenter)
```

---

## 💾 PART 4: Distributed Locking Filled Template

```
┌─────────────────────────────────────────────────────────────┐
│     DISTRIBUTED LOCK SYSTEM - NAPKIN MATH SOLUTION          │
└─────────────────────────────────────────────────────────────┘

STEP 1: LOCK OPERATION ESTIMATION
───────────────────────────────────
Active Instances:        2,000
Locks per Instance/Min:  10
Avg Lock Hold Duration:  300 seconds (5 minutes)
Heartbeat Interval:      15 seconds

→ Acquire QPS  = 2000 × 10 ÷ 60         = 333 QPS
→ Renewals per Lock = 300 / 15          = 20 renewals
→ Renew QPS    = 333 × 20               = 6,660 QPS
→ Release QPS  = 333                    = 333 QPS
→ Total Write QPS = 333 + 6660 + 333    = 7,326 QPS

Peak Multiplier (3x): 7,326 × 3 = ~22,000 QPS

STEP 2: CONSENSUS CLUSTER SIZING
───────────────────────────────────
Target: Handle 22K QPS writes (peak)

Using etcd (leader capacity: ~10K QPS):
→ Need 3 leaders (sharding) → 3 clusters OR
→ Single cluster with read offloading

Recommended: 5-node cluster (tolerates 2 failures)
  - 1 leader handles writes (up to 10K QPS)
  - 4 followers handle reads
  - If exceeds 10K QPS → Shard by resource hash

STEP 3: STORAGE ESTIMATION
───────────────────────────
Lock Entry: 200 bytes
  - Resource name: "payment:order:abc123" → 100B
  - Owner: "payment-service-pod-42" → 50B
  - Metadata: {fencingToken, expiresAt, acquiredAt} → 50B

Active Locks (peak):
  - Avg hold: 300s, acquire rate: 333/s
  - Active = 333 QPS × 300s = 100,000 concurrent locks

→ Active Lock Storage = 100K × 200B = 20 MB

Lock History (30 days retention):
  - Operations/day = 7326 QPS × 86,400s = 632M ops/day
  - Storage/day = 632M × 200B = 126 GB/day
  - 30-day storage = 126GB × 30 = 3.8 TB

Total Storage: 20 MB (active) + 3.8 TB (history) ≈ 3.8 TB

STEP 4: NETWORK BANDWIDTH
───────────────────────────
Write QPS: 7,326 (average), 22K (peak)

→ Leader Write BW = 7326 QPS × 200B × 5 nodes = 7.3 MB/s avg, 22 MB/s peak
→ Follower Read BW = (Check QPS) × 200B
  - If 1/3 checks → 2,442 QPS × 200B = 0.5 MB/s

Total Network: ~25 MB/s peak (well within 1 Gbps NIC)

STEP 5: LATENCY ANALYSIS
───────────────────────────
Target P99: <10ms

Same-datacenter 5-node cluster:
  - Network RTT: 1ms
  - Leader append: 1ms
  - Replication (parallel): 2ms
  - Quorum ACK (3/5): 2ms
  - Response: 1ms
→ Total: 7ms P99 ✅

Cross-region cluster (NOT recommended for locks):
  - Network RTT: 50ms (US-East to US-West)
  - Total: ~55ms P99 ❌ Too slow for locks
```

---

## 🧠 PART 5: Mental Math Techniques for Lock Systems

### **Technique 1: The Concurrent Lock Estimator**
```
Active Locks = Acquire Rate (QPS) × Avg Hold Duration (seconds)

Example:
  - 100 locks/sec acquired
  - Average hold: 60 seconds
  - Active locks = 100 × 60 = 6,000 concurrent locks

MEMORY TRIGGER: "Locks are like cars in a parking lot.
  Arrival rate × parking duration = cars in lot at any time"
```

### **Technique 2: The Heartbeat Tax Calculator**
```
Renewal overhead = (Lock hold duration / Heartbeat interval) - 1

Example:
  - 5-minute lock (300s)
  - 15s heartbeat
  - Renewals = (300 / 15) = 20 renewals per lock
  - If 100 locks/sec acquired → 100 × 20 = 2,000 renewals/sec

RULE OF THUMB: Renew QPS ≈ 10× to 20× Acquire QPS (for long locks)
```

### **Technique 3: The Quorum Safety Check**
```
Failure Tolerance = (Cluster Size / 2) - 1 (rounded down)

3 nodes → tolerates 1 failure (quorum = 2)
5 nodes → tolerates 2 failures (quorum = 3)
7 nodes → tolerates 3 failures (quorum = 4)

WHY NOT MORE? Consensus latency increases with cluster size!
  - More nodes = more network hops
  - Diminishing returns beyond 5-7 nodes
  - Use sharding instead of giant clusters
```

### **Technique 4: The Contention Detector**
```
Lock Contention Rate = Failed Acquires / Total Attempts

Low Contention (<5%):   Fine-grained locks, good design ✅
Medium (5-20%):         Acceptable, monitor trends
High (>20%):            Coarse locks, redesign needed ❌

REMEDY: Finer lock granularity
  - ❌ Lock entire "orders" table
  - ✅ Lock specific order "orders:12345"
```

---

## 🎨 PART 6: Visual Lock Capacity Model

```
                🔒 DISTRIBUTED LOCK SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 LOAD          💾 STORAGE        ⚖️ CONSENSUS
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
  QPS    Peak      Active  History   Nodes  Quorum
  7.3K   22K       20MB    3.8TB     5      3
```

**Memory Trigger**: Think **"L.S.C."** = Load, Storage, Consensus

---

## 🏗️ PART 7: Lock System Domain Model

```python
# Think in terms of lock lifecycle states

class Lock:
    resource_name: str      # "payment:user:123"
    owner_id: str           # "payment-service-pod-42"
    fencing_token: int      # Monotonically increasing (prevent stale ops)
    acquired_at: int        # Unix timestamp (ms)
    expires_at: int         # acquired_at + ttl
    ttl: int                # 30,000 ms (30 seconds)
    renewal_count: int      # Track heartbeats
    state: LockState        # ACQUIRED, EXPIRED, RELEASED

# Scale Insight: Every lock generates:
# - 1 acquire (write)
# - N renewals (writes) where N = (hold_duration / heartbeat_interval)
# - 1 release (write)
# Total writes per lock = 2 + N

# For 5-minute lock with 15s heartbeat:
# Writes = 2 + (300 / 15) = 22 writes per lock!
# This drives our 7.3K QPS calculation.
```

---

## 🎯 PART 8: The Interview Cheat Sheet for Locks

```
┌──────────────────────────────────────────────────┐
│  DISTRIBUTED LOCK ESTIMATION - 5 MIN RITUAL      │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify lock usage pattern:
    - How many clients? (instances, workers)
    - Lock frequency? (per instance, per second)
    - Lock duration? (seconds, minutes)
    - Renewal frequency? (TTL / 2 typical)

[ ] 2. Calculate lock operations:
    - Acquire QPS = clients × frequency
    - Renew QPS = acquire × (duration / heartbeat)
    - Total writes = acquire + renew + release

[ ] 3. Size consensus cluster:
    - Leader capacity: ~10K QPS (etcd), ~5K (ZK)
    - Cluster size: 5 nodes (tolerates 2 failures)
    - Sharding: If QPS > leader capacity

[ ] 4. Storage & bandwidth:
    - Active locks = acquire_qps × hold_duration
    - Storage = active × 200B + history
    - Bandwidth = write_qps × 200B × replicas

[ ] 5. Latency check:
    - Same DC: <10ms P99 ✅
    - Cross-region: >50ms ❌ (not suitable)
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Acquire QPS** | 333 (avg), 1K (peak) | Determines new lock rate |
| **Renew QPS** | 6,660 (avg), 20K (peak) | Dominant write load for long locks |
| **Total Write QPS** | 7,326 (avg), 22K (peak) | Cluster sizing |
| **Active Locks** | 100,000 | Memory/storage for lock table |
| **Lock Entry Size** | 200 bytes | Minimal (key-value pair) |
| **Cluster Size** | 5 nodes | Tolerates 2 failures |
| **P99 Latency** | <10ms | Same-datacenter consensus |
| **Storage (30d)** | 3.8 TB | History for auditing |

---

## 💡 Pro Architect Tips

### **Tip 1: The Lock Lifecycle Smell Test**
After calculations, ask:
- "Do 100K concurrent locks seem reasonable?" → YES (if 2K instances)
- "Is 7K QPS within etcd's 10K limit?" → YES ✅
- "Can we handle 22K peak?" → Maybe (sharding or read offloading)

### **Tip 2: The Heartbeat Efficiency Check**
```
If renewals dominate (>80% of writes):
  ❌ Problem: Heartbeat overhead too high
  ✅ Solutions:
     - Increase TTL (longer locks, fewer renewals)
     - Batch renewals (renew multiple locks in one call)
     - Use lease-based systems (etcd leases)
```

### **Tip 3: The Consistency vs Availability Trade-off**
Locks are inherently **CP (Consistency + Partition Tolerance)**:
```
During network partition:
  - Minority partition: Cannot acquire locks (unavailable)
  - Majority partition: Continues normal operation
  - Why: Safety > Availability (prevent split-brain)

Use Case Suitability:
  ✅ Financial transactions (need strong consistency)
  ✅ Leader election (only one leader allowed)
  ✅ Distributed cron (no duplicate job execution)
  ❌ Best-effort rate limiting (use AP system like Redis)
```

---

## 🎓 Professor's Wisdom: Lock System Edition

> **"In distributed locking, CORRECTNESS beats PERFORMANCE"**

Interviewer priorities:
1. ✅ Safety guarantees (fencing tokens, quorum)
2. ✅ Failure handling (what if leader crashes?)
3. ✅ Latency awareness (same DC vs cross-region)
4. ✅ Scalability bottlenecks (leader write limit)

**NOT NEEDED:**
- ❌ Exact QPS numbers
- ❌ Specific database choices
- ❌ Code implementation details

---

## 🔁 Repetition Backed by Emotion (Lock Systems!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"Active locks = Acquire rate × Hold duration - simple as that!"*
2. *"Renewals dominate writes - 10x to 20x acquire rate!"*
3. *"Consensus needs quorum - Majority or bust for safety!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you say:
"So with 333 lock acquisitions per second and a 5-minute hold time,
we have about 100,000 concurrent locks at any moment..."

---

## 📚 Quick Reference: Lock System Scale Benchmarks

| **System Type** | **Instances** | **Acquire QPS** | **Total Write QPS** | **Cluster Size** |
|-----------------|---------------|-----------------|---------------------|------------------|
| Small Startup | 100 | 10 | 200 | 3 nodes |
| Medium SaaS | 2,000 | 333 | 7K | 5 nodes |
| Large Enterprise | 10,000 | 1,600 | 35K | 5 nodes × 4 shards |
| Hyperscale | 100,000 | 16K | 350K | 5 nodes × 40 shards |

---

## 🔧 Practical Application: Capacity Planning Examples

### Example 1: **E-commerce Inventory Lock**
```
Scenario: Lock inventory items during checkout

Given:
- 100K items in catalog
- 1M daily orders
- Avg checkout time: 2 minutes
- Peak hour: 3x average

Calculate:
1. Order QPS: 1M / 86400 = 11.5 QPS avg, 35 QPS peak
2. Acquire QPS: 35 (one lock per order)
3. Hold duration: 120s
4. Heartbeat: 60s (TTL 120s → renew at 60s)
5. Renew QPS: 35 × (120 / 60) = 70 QPS
6. Total Write QPS: 35 (acq) + 70 (renew) + 35 (rel) = 140 QPS

Conclusion: Single 5-node etcd cluster (10K capacity) ✅
Active locks: 35 × 120 = 4,200 concurrent
```

### Example 2: **Distributed Cron Scheduler**
```
Scenario: 1000 cron jobs, prevent duplicate execution

Given:
- 1000 jobs running every minute
- Each job acquires leader lock
- Lock held for entire job duration (avg 30s)

Calculate:
1. Acquire QPS: 1000 / 60 = 16.6 QPS
2. Hold duration: 30s
3. Heartbeat: 15s
4. Renew QPS: 16.6 × (30 / 15) = 33.2 QPS
5. Total: 16.6 + 33.2 + 16.6 = 66.4 QPS

Conclusion: Trivial load, single cluster handles easily ✅
```

### Example 3: **Payment Processing Locks**
```
Scenario: Lock user accounts during payment

Given:
- 10M daily payments
- Avg payment processing: 5 seconds
- Peak: 5x average (Black Friday)

Calculate:
1. Payment QPS: 10M / 86400 = 115 avg, 575 peak
2. Hold duration: 5s (short!)
3. Heartbeat: 2.5s
4. Renew QPS: 575 × (5 / 2.5) = 1,150 QPS
5. Total: 575 + 1150 + 575 = 2,300 QPS

Conclusion: Well within single cluster capacity ✅
Short locks = low renewal overhead
```

---

## 🚨 Common Capacity Planning Mistakes

### Mistake 1: **Forgetting Renewal Overhead**
```
✗ BAD:  "We acquire 100 locks/sec, so 100 QPS writes"
✓ GOOD: "100 acq/s + renewals (10x) + 100 rel/s = ~2,100 QPS"
```

### Mistake 2: **Ignoring Lock Contention**
```
✗ BAD:  "Lock the entire database during migrations"
✓ GOOD: "Lock specific tables/rows, minimize contention"

High contention = Failed acquisitions = Retry storms = Cluster overload
```

### Mistake 3: **Cross-Region Consensus**
```
✗ BAD:  "Deploy 5-node Raft cluster across 5 continents"
✓ GOOD: "Single region for consensus, replicate state cross-region for reads"

Cross-region consensus latency: 100-500ms (unacceptable for locks!)
```

### Mistake 4: **Undersizing TTL**
```
✗ BAD:  "Use 5-second TTL for all locks"
✓ GOOD: "TTL = 2× max expected hold time (safety margin)"

If task takes 10s, use 20-30s TTL. Avoids premature expiry on slow ops.
```

### Mistake 5: **Single Leader Bottleneck**
```
✗ BAD:  "All 100K QPS writes go through one leader"
✓ GOOD: "Shard locks by resource hash across multiple Raft clusters"

Each leader: ~10K QPS capacity
Need 100K? → 10 shards × 5 nodes = 50-node deployment
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
LOCK SYSTEM: ___________________

STEP 1: WORKLOAD CHARACTERIZATION
───────────────────────────────────
Active Clients:           [____]
Locks per Client/Min:     [____]
Avg Lock Hold Duration:   [____] seconds
Heartbeat Interval:       [____] seconds (TTL/2)

STEP 2: QPS CALCULATION
───────────────────────────────────
→ Acquire QPS  = [____] clients × [____] locks/min ÷ 60 = [____]
→ Renewals per Lock = [____] duration ÷ [____] heartbeat = [____]
→ Renew QPS    = [____] acquire × [____] renewals = [____]
→ Release QPS  = [____] (same as acquire)
→ Total Write QPS = [____] + [____] + [____] = [____]
→ Peak QPS (3x) = [____]

STEP 3: CLUSTER SIZING
───────────────────────────────────
Leader Capacity (etcd): 10,000 QPS

→ Clusters Needed = ceil([____] peak QPS / 10K) = [____]
→ Nodes per Cluster = 5 (tolerates 2 failures)
→ Total Nodes = [____] clusters × 5 = [____]

STEP 4: STORAGE
───────────────────────────────────
→ Lock Entry Size = 200 bytes
→ Active Locks = [____] QPS × [____] seconds = [____]
→ Active Storage = [____] locks × 200B = [____] MB
→ History/Day = [____] QPS × 86400 × 200B = [____] GB
→ Total ([____] days) = [____] GB

SMELL TEST:
───────────────────────────────────
□ QPS within leader capacity? (<10K per cluster)
□ Latency achievable? (<10ms same DC)
□ Failure tolerance acceptable? (2 nodes for 5-node cluster)
□ Storage reasonable? (GBs to TBs range)
```

---

## 🎁 Bonus: Lock System Capacity Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║      DISTRIBUTED LOCK CAPACITY CHEAT SHEET             ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Lock Entry:    ~200 bytes (resource + owner + meta)
• etcd Capacity: ~10K write QPS per leader
• ZK Capacity:   ~5K write QPS per leader
• P99 Latency:   <10ms (same DC), >50ms (cross-region)
• Cluster Size:  5 nodes (tolerates 2 failures)

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Acquire QPS = Clients × Locks/min ÷ 60
Renew QPS = Acquire × (Hold Duration / Heartbeat)
Active Locks = Acquire QPS × Hold Duration (seconds)
Total Writes = Acquire + Renew + Release
Storage = Active × 200B + (Daily QPS × 86400 × 200B × Days)

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Short Locks (<30s):  Renew ~2× Acquire
• Medium Locks (5min): Renew ~20× Acquire
• Long Locks (1hr):    Renew ~120× Acquire
• Heartbeat:           TTL / 2 (typical)
• Peak:Average:        3:1 (business hours)

CLUSTER SIZING:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:   <1K QPS    → 3 nodes
Medium:  1K-10K QPS → 5 nodes
Large:   >10K QPS   → 5 nodes × N shards

LATENCY BUDGET (Same DC):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Network RTT:         1-2ms
Leader Log Append:   1ms
Replication:         2-3ms (parallel to followers)
Quorum ACK:          2-3ms
Response:            1-2ms
──────────────────────────
Total P99:           7-12ms ✅

FAILURE TOLERANCE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3-node cluster: Tolerates 1 failure  (quorum = 2)
5-node cluster: Tolerates 2 failures (quorum = 3)
7-node cluster: Tolerates 3 failures (quorum = 4)

WHY NOT MORE? Consensus latency increases with cluster size!

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can 5-node etcd handle 5K QPS? YES
✓ Can same-DC cluster do <10ms? YES
✓ Can lock survive 2 node failures? YES (5-node)
✓ Are renewals <90% of writes? YES (if hold <5min)

✗ Can cross-region cluster do <10ms? NO
✗ Can single leader handle 50K QPS? NO (shard it)
✗ Can 3-node cluster tolerate 2 failures? NO (use 5)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these systems and estimate lock capacity:

1. **Kubernetes Scheduler** - Leader election for scheduler instances
2. **Distributed Cron** - 10,000 jobs, prevent duplicate execution
3. **Order Processing** - Lock inventory items during checkout
4. **Microservice Coordination** - Rate limiting per user account
5. **Database Migration** - Ensure single migrator across replicas

Use the blank template above and time yourself: **Can you complete it in 7 minutes?**

---

## 📚 Additional Resources

- **Papers**: "In Search of an Understandable Consensus Algorithm" (Raft, Diego Ongaro)
- **Books**: "Designing Data-Intensive Applications" Chapter 9 (Consistency & Consensus)
- **Tools**: etcd, Consul, ZooKeeper benchmarking guides
- **Practice**: Design leader election, distributed cron, lock service

---

**Remember**:
> "Lock systems trade availability for correctness. In the face of uncertainty, safety comes first."

**Now go design rock-solid distributed coordination!** 🚀

---

*Created with the LOCKS technique: Latency → Operations → Cluster → Key metrics → Storage*
*Perfect for: System design interviews, Production capacity planning, Architecture reviews*
