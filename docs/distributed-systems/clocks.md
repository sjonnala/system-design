# Clock Synchronization

## The Time Problem in Distributed Systems

**Fundamental Issue**: No global clock in distributed systems

**Why Time Matters**:
- Ordering events
- Detecting causality
- Conflict resolution
- Consistency checks
- Logging and debugging

**The Challenge**:
```
Server 1 clock: 10:00:00.000
Server 2 clock: 10:00:00.050  (50ms ahead)
Server 3 clock: 09:59:59.980  (20ms behind)

Which event happened first?
```

---

## Physical Clocks

### System Clocks

**How Computers Track Time**:
- Quartz crystal oscillator
- Counts oscillations since epoch (Jan 1, 1970)
- **Drift**: Clocks run at slightly different speeds

**Clock Drift**:
```
Perfect clock: 1 second = 1,000,000 microseconds
Real clock: 1 second ≈ 1,000,050 microseconds (50 ppm drift)

Over 1 day:
  Drift = 86400 seconds × 50/1,000,000 = 4.32 seconds

Without synchronization, clocks diverge!
```

### Network Time Protocol (NTP)

**Purpose**: Synchronize clocks across network

**How NTP Works**:
```
Client                                  NTP Server
  |                                          |
  |--- Request (t1) ---------------------->  |
  |                                          |
  |                                    (t2) Receive
  |                                    (t3) Send
  |                                          |
  |<---------------------- Response (t4) ---|
  |                                          |

Network Delay = ((t4 - t1) - (t3 - t2)) / 2
Server Offset = ((t2 - t1) + (t3 - t4)) / 2

Adjust client clock by offset
```

**Example Calculation**:
```
t1 = 10:00:00.000 (client sends)
t2 = 10:00:00.010 (server receives)
t3 = 10:00:00.011 (server sends)
t4 = 10:00:00.021 (client receives)

Delay = ((21 - 0) - (11 - 10)) / 2 = 10ms
Offset = ((10 - 0) + (11 - 21)) / 2 = 0ms

Client clock is accurate!
```

**NTP Accuracy**:
- LAN: 1-10 milliseconds
- WAN: 10-100 milliseconds
- Public internet: 100+ milliseconds

**Limitations**:
- Asymmetric network delays
- Network congestion
- Can't guarantee perfect sync
- **Can never provide ordering guarantees**

### Monotonic vs Time-of-Day Clocks

**Time-of-Day Clock** (Wall Clock):
```
System.currentTimeMillis()  // Java
time.time()                 // Python

Returns: Milliseconds since epoch
Problem: Can jump backward! (NTP adjustment)

Example:
10:00:00.000
10:00:00.100 (NTP sync)
09:59:59.900  ← Jumped backward!
```

**Monotonic Clock**:
```
System.nanoTime()         // Java
time.monotonic()          // Python

Returns: Time since arbitrary point
Guarantee: Always moves forward
Use for: Measuring elapsed time, timeouts

Example:
start = nanoTime()
... do work ...
elapsed = nanoTime() - start  // Always positive
```

**When to Use**:
- **Time-of-Day**: Timestamps, logging, human-readable time
- **Monotonic**: Timeouts, measuring duration, performance

---

## Logical Clocks

### Lamport Timestamps

**Key Insight**: Don't need absolute time, just relative ordering

**Lamport Clock Rules**:
1. Each process has counter (initially 0)
2. Before event: increment counter
3. When sending message: include counter
4. When receiving message: `counter = max(local, received) + 1`

**Example**:
```
Process A          Process B          Process C
LC=0               LC=0               LC=0
  |                  |                  |
  | event            |                  |
LC=1                 |                  |
  |                  |                  |
  |--- msg(LC=1) --->|                  |
  |                  | receive          |
  |                LC=2 (max(0,1)+1)    |
  |                  |                  |
  |                  | event            |
  |                LC=3                 |
  |                  |                  |
  |                  |--- msg(LC=3) --->|
  |                  |                  | receive
  |                  |                LC=4 (max(0,3)+1)
```

**Ordering Events**:
```
If event a happened before event b:
  LC(a) < LC(b)

BUT: LC(a) < LC(b) doesn't mean a happened before b!
(Could be concurrent events in different processes)
```

**Use Cases**:
- Distributed debugging
- Detecting causality violations
- Consistent snapshots

**Limitation**: Can't detect concurrent events

### Vector Clocks

**Enhancement**: Track causality more precisely

**Vector Clock Structure**:
```
Each process maintains vector: [P1: count, P2: count, P3: count]

Process 1: [3, 0, 2] means:
  - Seen 3 events from P1
  - Seen 0 events from P2
  - Seen 2 events from P3
```

**Vector Clock Rules**:
1. Initialize: `VC = [0, 0, 0]`
2. Before event: `VC[self]++`
3. When sending: include `VC`
4. When receiving: `VC[i] = max(local[i], received[i])` for all i, then `VC[self]++`

**Example**:
```
Process A                Process B                Process C
VC=[0,0,0]               VC=[0,0,0]               VC=[0,0,0]
   |                        |                        |
   | event                  |                        |
VC=[1,0,0]                  |                        |
   |                        |                        |
   |-- msg([1,0,0]) ------->|                        |
   |                        | receive                |
   |                     VC=[1,1,0]                  |
   |                     (max + increment)           |
   |                        |                        |
   | event                  | event                  |
VC=[2,0,0]              VC=[1,2,0]                   |
   |                        |                        |
   |                        |-- msg([1,2,0]) ------->|
   |                        |                        | receive
   |                        |                     VC=[1,2,1]
```

**Detecting Causality**:
```
VC1 = [3, 1, 0]
VC2 = [3, 2, 0]

VC1 < VC2 (VC1 happened before VC2)
Because: All components VC1[i] ≤ VC2[i] and at least one is strictly less
```

**Detecting Concurrency**:
```
VC1 = [3, 1, 0]
VC2 = [2, 2, 0]

Neither VC1 < VC2 nor VC2 < VC1
→ Events are concurrent!

(VC1 has higher count for P1, VC2 has higher count for P2)
```

**Real-World Example - Distributed Database**:
```
Write 1: Set X=5 with VC=[1,0,0]
Write 2: Set X=7 with VC=[0,1,0]

Read receives both versions:
  Version 1: X=5 [1,0,0]
  Version 2: X=7 [0,1,0]

Compare vectors:
  [1,0,0] vs [0,1,0] → Concurrent!
  → Conflict detected
  → Application must resolve (or use last-write-wins)
```

**Use Cases**:
- **Riak**: Conflict detection
- **Cassandra**: Version tracking
- **DynamoDB**: Vector clocks for conflict resolution
- **CRDTs**: Causality tracking

**Limitation**: Vector size grows with number of processes

### Hybrid Logical Clocks

**Combines Physical + Logical Time**

**Structure**:
```
HLC = (physical_time, logical_counter)

Example: (1234567890, 0)
```

**Rules**:
1. `physical_time` = max(wall_clock, last_HLC.physical_time)
2. If `physical_time` unchanged: `logical_counter++`
3. Else: `logical_counter = 0`

**Example**:
```
Event 1: Wall=100, HLC=(100, 0)
Event 2: Wall=100, HLC=(100, 1)  (same physical time)
Event 3: Wall=102, HLC=(102, 0)  (physical time advanced)
Event 4: Wall=101, HLC=(102, 1)  (wall clock behind, use HLC)
```

**Benefits**:
- Bounded space (unlike vector clocks)
- Compatible with timestamps
- Causality tracking
- Useful for distributed databases

**Used By**:
- **CockroachDB**: Transaction ordering
- **MongoDB**: Causality tracking

---

## Google Spanner TrueTime

### The Innovation

**TrueTime API**:
```
TT.now() returns interval: [earliest, latest]

Example: TT.now() = [10:00:00.001, 10:00:00.009]

Guarantee: Actual time is somewhere in this interval
```

**How TrueTime Works**:
```
GPS + Atomic Clocks:
- Each datacenter has GPS receivers
- Each datacenter has atomic clocks
- Cross-check multiple time sources
- Calculate uncertainty (epsilon)

Return: [now - epsilon, now + epsilon]
```

**Typical Uncertainty**: 1-7 milliseconds

### Wait Out the Uncertainty

**Commit Wait Protocol**:
```
Transaction commits at time T:

1. Assign commit timestamp: T_commit = TT.now().latest
2. Wait until: TT.now().earliest > T_commit
3. Only then: Report transaction committed

Guarantee: Commit time definitely in the past
```

**Example**:
```
T1: TT.now() = [100, 107]
    Assign commit: 107
    Wait until: TT.now().earliest > 107

T2: TT.now() = [105, 112]
    Wait...

T3: TT.now() = [108, 115]
    OK! earliest (108) > commit (107)
    Commit!
```

**Result**: **External Consistency**
```
If transaction T1 completes before T2 starts:
  → timestamp(T1) < timestamp(T2)

Provides true global ordering!
```

### Why This Matters

**Traditional Databases**:
```
Can't guarantee ordering across datacenters:

Transaction T1 commits in US at wall time 10:00:00.000
Transaction T2 commits in EU at wall time 10:00:00.001

But clocks might be skewed → Wrong ordering!
```

**With TrueTime**:
```
T1 commits with timestamp 100 (US)
T2 starts after T1 finishes
T2 must have timestamp > 100 (guaranteed)

Perfect ordering even across globe!
```

**Use Cases**:
- Global strongly consistent transactions
- Serializable isolation
- Consistent snapshots across regions

**Cost**: Added latency (waiting for uncertainty)

---

## Ordering Without Clocks

### Happens-Before Relation

**Definition** (Leslie Lamport):
```
Event a happens before event b (a → b) if:
1. a and b in same process and a before b
2. a is send event and b is receive event
3. a → c and c → b (transitivity)

If not (a → b) and not (b → a): a and b are concurrent
```

**Example**:
```
P1: a -----> b -----> c
            /          \
           /            \
P2:       d -----> e -----> f

a → b (same process)
b → d (message send/receive)
d → e (same process)
e → f (same process)
b → f (transitivity: b → d → e → f)

a || e? No! a → b → d → e, so a → e
c || e? Yes! No path from c to e or e to c
```

### Total Order Broadcast

**Goal**: All nodes deliver messages in same order

**Properties**:
1. **Validity**: If message broadcast, eventually delivered
2. **Uniform Agreement**: If one node delivers m, all deliver m
3. **Uniform Integrity**: Each message delivered exactly once
4. **Uniform Total Order**: Same delivery order on all nodes

**Implementation** (Using Consensus):
```
1. Node broadcasts message to all
2. Run consensus on order of messages
3. Deliver in agreed order

Example:
Node 1 broadcasts: msg_A
Node 2 broadcasts: msg_B
Consensus decides: [msg_A, msg_B]
All nodes deliver in order: msg_A then msg_B
```

**Use Cases**:
- State machine replication
- Distributed databases
- Consistent logging

**Equivalence**: Total Order Broadcast ≡ Consensus

---

## Interview Q&A

**Q: "Why can't we just use NTP to order events in a distributed system?"**

**Answer**:
```
NTP provides clock synchronization but NOT ordering guarantees:

Problems:
1. Clock Skew:
   - NTP accuracy: 10-100ms typically
   - Events within this window: can't order reliably

   Example:
   Event A at Server1: timestamp 10:00:00.050
   Event B at Server2: timestamp 10:00:00.040
   A appears later but might have happened first!

2. Clock Drift:
   - Clocks drift between syncs
   - Timestamps unreliable for ordering

3. Backward Jumps:
   - NTP can move clock backward
   - Violates ordering assumptions

Solutions:
- Lamport/Vector clocks: Logical ordering
- TrueTime (Google): Bounded uncertainty
- Consensus: Agree on order explicitly
- Hybrid Logical Clocks: Combine physical + logical

Use NTP for:
- Human-readable timestamps
- Approximate time
- Logging

Don't use NTP for:
- Guaranteed event ordering
- Distributed coordination
- Conflict resolution
```

**Q: "How does Cassandra handle conflicting writes?"**

**Answer**:
```
Cassandra uses Last-Write-Wins with timestamps:

Write Flow:
1. Client writes: SET X=5
2. Cassandra adds timestamp (microseconds)
3. Replicates to multiple nodes

Conflict Resolution:
Write 1: X=5 timestamp=1000
Write 2: X=7 timestamp=1002

Higher timestamp wins → X=7

Problems:
1. Clock Skew:
   Node 1 clock ahead → Its writes always win!

2. Lost Updates:
   Concurrent writes → One arbitrarily lost

3. Not Causally Consistent:
   Can violate application logic

Example Issue:
  Thread 1: SET counter=1 (timestamp 1000)
  Thread 2: SET counter=2 (timestamp 999, slow clock)
  Result: counter=1 (wrong! 2 should win)

Better Approaches:
- Vector Clocks (Riak): Detect conflicts, let app resolve
- CRDTs: Automatic merge without conflicts
- Application-level versioning: Track causality explicitly

Cassandra LWW works when:
- Clocks well synchronized
- Conflicts rare
- Arbitrary resolution acceptable
- Performance critical
```

**Q: "Explain Google Spanner's TrueTime and why it's important"**

**Answer**:
```
TrueTime provides time with bounded uncertainty:

Traditional Problem:
Can't order events across datacenters reliably
  Server 1 (US): timestamp 100
  Server 2 (EU): timestamp 101
  Who's first? Unknown! (clock skew)

TrueTime Solution:
Returns interval [earliest, latest]
  TT.now() = [100, 107]

Guarantee: Actual time is in this range

How It Works:
- GPS receivers (time source)
- Atomic clocks (backup)
- Multiple time masters per datacenter
- Calculate uncertainty (epsilon)

Commit Wait Protocol:
1. Transaction commits: assign timestamp T
2. Wait until: T definitely in past
3. Result: Global ordering guaranteed!

Example:
  T1 commits: timestamp 100
  Wait until time > 100 (certain)
  T2 starts: must get timestamp > 100
  Guarantee: T1 before T2

Why Important:
1. External Consistency:
   - Real-world order preserved
   - If T1 completes before T2 starts: TS(T1) < TS(T2)

2. Snapshot Reads:
   - Read from any replica
   - Consistent view at timestamp
   - No coordination needed

3. Global Strong Consistency:
   - ACID across datacenters
   - Serializable isolation
   - Like single database

Trade-offs:
- Requires special hardware (GPS, atomic clocks)
- Added latency (commit wait)
- But: Enables global consistency at scale

Used by: Google Spanner, Cloud Spanner
```

---

## Key Takeaways

1. **No Global Clock**: Distributed systems can't rely on synchronized time
2. **Physical Clocks Drift**: NTP helps but doesn't guarantee ordering
3. **Logical Clocks**: Lamport and vector clocks track causality
4. **TrueTime**: Bounded uncertainty enables global ordering (special hardware)
5. **Choose Based on Needs**: Physical time for timestamps, logical for causality
6. **Happens-Before**: Fundamental relation for reasoning about distributed systems

---

## Further Reading

- "Time, Clocks, and the Ordering of Events" - Leslie Lamport (1978)
- "Designing Data-Intensive Applications" - Chapter 8
- Google Spanner Paper: "Spanner: Google's Globally-Distributed Database"
- "Logical Physical Clocks" - Hybrid Logical Clocks paper
- CockroachDB blog: "Living Without Atomic Clocks"
- Vector Clocks Revisited: "Why Vector Clocks are Easy"
- NTP Protocol Specification (RFC 5905)
