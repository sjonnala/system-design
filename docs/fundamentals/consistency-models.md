# Consistency Models

## Overview

Consistency models define the rules for how and when updates to distributed data become visible to readers. Understanding these models is critical for designing distributed systems.

```
Strong Consistency
        |
        | (Weaker guarantees, Better performance)
        |
Linearizability
        |
Sequential Consistency
        |
Causal Consistency
        |
Eventual Consistency
        |
Weak Consistency
```

## 1. Strong Consistency (Linearizability)

### Definition
- All operations appear to execute atomically in some sequential order
- Once a write completes, all subsequent reads see that write
- Real-time guarantee: respects actual time ordering
- The "gold standard" of consistency

### Guarantees
```
Timeline:
T1: Client A writes X=5
T2: Write completes
T3: Client B reads X → Must return 5
T4: Client C reads X → Must return 5
```

### Implementation
- Synchronous replication to all replicas
- Consensus protocols (Paxos, Raft)
- Distributed locks
- Two-phase commit (2PC)

### Trade-offs
**Pros**:
- Easiest to reason about
- No surprises for developers
- Matches single-machine behavior

**Cons**:
- High latency (wait for all replicas)
- Lower availability (cannot tolerate partition)
- Poor performance in geo-distributed systems

### Use Cases
- Banking and financial transactions
- Inventory management
- Reservation systems
- Distributed locks

### Real-World Examples
- Google Spanner (globally distributed)
- etcd
- ZooKeeper
- Traditional RDBMS with synchronous replication

### Interview Tip
```
"For a ticket booking system, I'd use strong consistency
to prevent double-booking. The latency cost is acceptable
because correctness is paramount. Users can wait an extra
100ms to ensure they actually get the seat."
```

---

## 2. Sequential Consistency

### Definition
- All operations appear in some sequential order
- Order is consistent across all nodes
- **Does NOT** guarantee real-time ordering

### Difference from Linearizability
```
Linearizability:
Real Time: ─────────────────────────────>
T1: Write X=1 ──┐
                └── completes
T2: Read X       └──> Must return 1

Sequential Consistency:
May allow: Read X → 0 (even after write completed in real-time)
As long as all nodes agree on same order eventually
```

### Characteristics
- Weaker than linearizability
- Doesn't respect real-time constraints
- All processes see same interleaving of operations
- Individual process operations stay in order

### Use Cases
- Multi-player games (game state)
- Collaborative editing
- Distributed counters

### Example Systems
- Some configurations of Cassandra
- MongoDB with specific settings

---

## 3. Causal Consistency

### Definition
- Operations that are causally related are seen in same order by all nodes
- Concurrent (unrelated) operations can be seen in different orders

### Causal Relationships
```
Event A happens-before Event B if:
1. A and B are from same process and A occurs before B
2. A is a send event and B is corresponding receive
3. Transitivity: A → B and B → C, then A → C
```

### Example
```
Scenario: Social Media Comments

Alice posts: "Going to party!"          (Event A)
   ↓
Bob replies: "See you there!"          (Event B - causally depends on A)
   ↓
Carol likes Bob's comment              (Event C - causally depends on B)

Dan posts: "Nice weather today"        (Event D - concurrent with all)

Guarantee:
- Everyone sees A before B before C (causal order preserved)
- D can appear anywhere (independent event)
```

### Implementation
- Vector clocks
- Version vectors
- Lamport timestamps

### Trade-offs
**Pros**:
- Better availability than strong consistency
- Preserves intuitive ordering
- Lower latency than sequential consistency

**Cons**:
- More complex to implement
- Still requires coordination for causal dependencies
- Conflict resolution needed

### Use Cases
- Social media feeds
- Messaging systems
- Comment threads
- Collaborative applications

### Real-World Examples
- MongoDB (with causal consistency sessions)
- Cassandra (optional causal consistency)
- COPS (Causal+ consistency system)

### Interview Tip
```
"For a messaging app, causal consistency ensures that
reply appears after original message. But two unrelated
conversations can be ordered differently for different
users - which is acceptable and improves performance."
```

---

## 4. Eventual Consistency

### Definition
- If no new updates are made, eventually all replicas converge to same value
- **No** guarantees about when convergence happens
- Reads may return stale data

### Characteristics
```
Timeline:
T1: Write X=5 to Node A
T2: Read from Node B → May return old value (X=3)
T3: Read from Node C → May return old value (X=3)
...
TN: All reads return X=5 (eventually)
```

### Convergence Time
- Typically milliseconds to seconds
- Depends on network latency
- Can be longer during failures
- No upper bound guarantee

### Variants

#### Strong Eventual Consistency
- Replicas that received same updates are consistent
- No conflicts (CRDTs - Conflict-free Replicated Data Types)
- Example: Riak with CRDTs

#### Weak Eventual Consistency
- May have conflicts
- Requires conflict resolution
- Example: Amazon DynamoDB

### Conflict Resolution Strategies

#### Last Write Wins (LWW)
```
Node A: Set X=5 at T1
Node B: Set X=7 at T2
Result: X=7 (based on timestamp)

Problem: Clock skew can cause issues
```

#### Application-Level Resolution
```
Shopping Cart Conflict:
Cart A: [Item1, Item2]
Cart B: [Item1, Item3]
Merge: [Item1, Item2, Item3]  (Union - prefer additions)
```

#### Version Vectors
```
Track causality, detect conflicts
Let application decide resolution
```

### Trade-offs
**Pros**:
- Highest availability
- Lowest latency
- Works during network partitions
- Scales easily

**Cons**:
- Application complexity (handle stale reads)
- Conflict resolution required
- Counter-intuitive behavior

### Use Cases
- DNS
- Caching layers
- Session storage
- Social media likes/views
- Shopping carts
- User preferences

### Real-World Examples
- Amazon DynamoDB
- Apache Cassandra (default)
- Riak
- Azure Cosmos DB (eventual consistency level)

### Interview Tip
```
"For a view counter, eventual consistency is perfect.
If two users see slightly different counts (1,000,523 vs
1,000,527) it doesn't matter. We get high availability
and low latency, and counts converge within seconds."
```

---

## 5. Read-After-Write Consistency

### Definition
- Users always see their own writes immediately
- May not see other users' writes immediately
- Also called "read-your-writes" consistency

### Example
```
User A:
  1. Updates profile picture
  2. Immediately sees new picture ✓

User B:
  1. Views User A's profile
  2. May see old picture for a few seconds
```

### Implementation
- Route user's reads to same replica that handled write
- Track version/timestamp of last write per user
- Use sticky sessions

### Use Cases
- User profile updates
- Post-after-comment scenarios
- Shopping cart updates
- User settings

### Real-World Examples
- Facebook (see your own posts immediately)
- Twitter (see your tweets right away)
- LinkedIn (profile changes visible to you instantly)

---

## 6. Monotonic Reads

### Definition
- If a user reads value X, subsequent reads won't return older values
- Prevents "moving backward in time"

### Example
```
Bad (without monotonic reads):
T1: User reads X=10
T2: User reads X=5  ← Older value! Confusing!

Good (with monotonic reads):
T1: User reads X=10
T2: User reads X=10 or newer ✓
```

### Implementation
- Sticky sessions (always read from same replica)
- Track client's "last seen" timestamp
- Route to replica with sufficient freshness

### Use Cases
- Reading message threads
- Viewing update histories
- Pagination through data
- Any sequential reading pattern

---

## 7. Monotonic Writes

### Definition
- Writes from same client are applied in order
- Write W1 before W2 means W1 applied before W2 on all replicas

### Example
```
User updates:
  1. Set status = "Away"
  2. Set status = "Online"

Guarantee: Final status is "Online" on all replicas
(not "Away" due to reordering)
```

### Use Cases
- Status updates
- Configuration changes
- Sequential operations from same user

---

## Consistency Levels in Practice

### Apache Cassandra

Tunable consistency per query:

| Level | Description | Use Case |
|-------|-------------|----------|
| ONE | Read/write from one replica | Maximum performance |
| QUORUM | Majority of replicas | Balance consistency/availability |
| ALL | All replicas | Strong consistency |
| LOCAL_QUORUM | Majority in local datacenter | Multi-DC setup |

### Amazon DynamoDB

| Level | Description | Latency |
|-------|-------------|---------|
| Eventual | Default, best performance | ~low ms |
| Strong | Linearizable reads | ~higher ms |

### MongoDB

| Level | Description |
|-------|-------------|
| majority | Acknowledged by majority |
| linearizable | Strongest consistency |
| local | Local replica set |

---

## Quorum-Based Consistency

### Formula
```
R + W > N

R = Read quorum size
W = Write quorum size
N = Total number of replicas
```

### Examples

#### Strong Consistency
```
N=3, W=2, R=2
2 + 2 > 3 ✓
Read and write overlap, ensuring latest value
```

#### Eventual Consistency
```
N=3, W=1, R=1
1 + 1 = 2, not > 3
May read stale data, but faster
```

#### Read-Optimized
```
N=3, W=3, R=1
Fast reads, slower writes
Good for read-heavy workloads
```

#### Write-Optimized
```
N=3, W=1, R=3
Fast writes, slower reads
Good for write-heavy workloads
```

---

## Choosing a Consistency Model

### Decision Matrix

```
┌─────────────────────────────────────────────────┐
│ Financial Data → Strong Consistency             │
│ User Writes → Read-After-Write Consistency      │
│ Analytics → Eventual Consistency                │
│ Messaging → Causal Consistency                  │
│ Caching → Eventual Consistency                  │
│ Inventory → Strong Consistency                  │
│ Social Feed → Eventual Consistency              │
│ Bookings → Strong Consistency                   │
└─────────────────────────────────────────────────┘
```

### Questions to Ask

1. **Can users tolerate stale data?**
   - Yes → Eventual consistency
   - No → Strong consistency

2. **Do users need to see their own writes?**
   - Yes → Add read-after-write consistency
   - No → Pure eventual consistency okay

3. **Is causal ordering important?**
   - Yes → Causal consistency
   - No → Eventual consistency

4. **What's the read/write ratio?**
   - Read-heavy → Lower write quorum
   - Write-heavy → Lower read quorum

5. **Geo-distributed?**
   - Yes → Eventual or causal consistency
   - No → Strong consistency feasible

---

## Interview Strategy

### Framework for Discussion

1. **Clarify Requirements**
   ```
   "What are the consistency requirements for this data?
   Can users tolerate seeing slightly stale information?"
   ```

2. **Identify Data Categories**
   ```
   "User profiles can be eventually consistent, but
   payment transactions need strong consistency."
   ```

3. **Discuss Trade-offs**
   ```
   "Eventual consistency gives us 99.99% availability
   and <10ms latency, but users might see old data for
   up to 1 second. Is that acceptable?"
   ```

4. **Propose Solution**
   ```
   "I'd use DynamoDB with eventual consistency for reads
   and strong consistency for writes to critical data.
   For user session, read-after-write consistency ensures
   users see their own updates immediately."
   ```

---

## Common Interview Questions

### Q1: "How does eventual consistency work in practice?"
**Answer**:
```
When you write to a distributed database like Cassandra:
1. Write goes to coordinator node
2. Coordinator writes to W replicas (e.g., W=2 out of N=3)
3. Returns success to client
4. Asynchronously propagates to remaining replicas

When you read:
1. Read from R replicas (e.g., R=1)
2. May get stale data if reading from replica not yet updated
3. Eventually (typically milliseconds), all replicas converge

Example: Shopping cart
- User adds item, writes to 2 of 3 replicas
- Reads might show old cart from 3rd replica briefly
- Within seconds, all replicas show added item
```

### Q2: "Why can't we always use strong consistency?"
**Answer**:
```
1. Latency: Synchronous replication to all replicas adds latency
   - Local: 10ms → 50ms
   - Cross-region: 50ms → 300ms+

2. Availability: If majority of replicas unavailable, system blocks
   - Strong: Can't serve requests during partition
   - Eventual: Continues serving requests

3. Scalability: Coordination overhead limits throughput
   - Consensus protocols have inherent bottlenecks
   - Eventual consistency scales horizontally easily

4. Cost: More replicas needed for fault tolerance with strong consistency
```

### Q3: "Design a system requiring multiple consistency levels"
**Answer**:
```
E-commerce Platform:

Strong Consistency:
- Inventory count (no overselling)
- Payment transactions
- Order placement

Read-After-Write:
- User cart (see your additions immediately)
- Order status for user who placed it

Eventual Consistency:
- Product reviews and ratings
- View counts
- Recommendation engine data
- User activity logs

Rationale: Different data has different business requirements.
Critical path (checkout) uses strong consistency, while
non-critical features use eventual consistency for better performance.
```

---

## Key Takeaways

1. **No One-Size-Fits-All**: Different data needs different consistency models
2. **It's a Spectrum**: Not binary choice, many levels in between
3. **Trade-offs Matter**: Consistency vs Availability vs Latency vs Cost
4. **Business Drives Tech**: Let business requirements determine consistency needs
5. **Tunable is Better**: Systems offering configurable consistency provide flexibility

---

## Further Reading

- "Designing Data-Intensive Applications" - Martin Kleppmann (Chapter 5)
- "Consistency Models" - Jepsen.io
- Aphyr's blog posts on consistency testing
- CAP Theorem paper by Eric Brewer
- PACELC Theorem by Daniel Abadi
