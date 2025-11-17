# Strong Consistency: Linearizability and Ordering Deep Dive

## Contents

- [Strong Consistency: Linearizability and Ordering Deep Dive](#strong-consistency-linearizability-and-ordering-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Consistency Spectrum](#the-consistency-spectrum)
    - [Linearizability Definition](#linearizability-definition)
    - [Serializability vs Linearizability](#serializability-vs-linearizability)
    - [Implementing Linearizability](#implementing-linearizability)
    - [Google Spanner - Globally Distributed Linearizability](#google-spanner---globally-distributed-linearizability)
    - [The Cost of Strong Consistency](#the-cost-of-strong-consistency)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: CONSISTENCY MODELS](#mind-map-consistency-models)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Linearizability = System Appears as Single Copy

┌────────────────────────────────────────────────────────────┐
│                                                             │
│  Despite multiple replicas, system behaves as if there's   │
│  only ONE copy of the data                                 │
│                                                             │
│  Operations appear to execute ATOMICALLY and INSTANTLY     │
│  in some total order consistent with real-time             │
│                                                             │
└────────────────────────────────────────────────────────────┘

Example:
────────

Client A writes X=1 at time t1
Client B reads X at time t2 (t2 > t1)
→ Client B MUST see X=1 (not old value)

This seems obvious, but is HARD in distributed systems!
```

**The Illusion:**
```
                Physical Reality:
                ─────────────────
                ┌─────┐  ┌─────┐  ┌─────┐
                │ DB1 │  │ DB2 │  │ DB3 │
                │ X=1 │  │ X=0 │  │ X=1 │  ← Replicas may differ
                └─────┘  └─────┘  └─────┘


                Linearizable View:
                ──────────────────
                     ┌─────┐
                     │ DB  │
                     │ X=1 │  ← Appears as single copy
                     └─────┘
```

---

## The Consistency Spectrum

🎓 **PROFESSOR**: Understanding different consistency levels:

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  STRONGEST ←───────────────────────────────────→ WEAKEST    │
│                                                              │
│  Linearizability                                             │
│       ↓                                                      │
│  Sequential Consistency                                      │
│       ↓                                                      │
│  Causal Consistency                                          │
│       ↓                                                      │
│  Read-Your-Writes                                            │
│       ↓                                                      │
│  Monotonic Reads                                             │
│       ↓                                                      │
│  Eventual Consistency                                        │
│                                                              │
└─────────────────────────────────────────────────────────────┘

Cost increases from bottom to top:
- Eventual: Cheap, fast, always available
- Linearizable: Expensive, slower, may block
```

---

## Linearizability Definition

🎓 **PROFESSOR**: The formal definition:

### Three Requirements

```
A system is linearizable if:
───────────────────────────

1. Total Order: All operations appear to execute in some total order
2. Real-Time Order: If operation A completes before B starts (in real-time),
                    then A appears before B in the total order
3. Sequential Specification: Each read sees the value of the most recent write
                            in the total order
```

### Visualizing Linearizability

```python
class LinearizabilityExample:
    """
    Determining if an execution is linearizable
    """

    def linearizable_execution(self):
        """
        Example 1: LINEARIZABLE
        """
        # Timeline (real-time):
        # ──────────────────────────────────────────→ time
        #
        # A: |─ write(x, 1) ─|
        # B:                    |─ read(x) → 1 ─|
        # C:                                       |─ read(x) → 1 ─|

        # Linearization point (where operation takes effect):
        # write(x,1) could take effect anywhere in its interval
        # reads must see most recent write

        # Valid linearization:
        # write(x,1) → read(x)→1 → read(x)→1  ✓

    def non_linearizable_execution(self):
        """
        Example 2: NOT LINEARIZABLE
        """
        # Timeline:
        # ──────────────────────────────────────────→ time
        #
        # A: |─ write(x, 1) ─|
        # B:                    |─ read(x) → 0 ─|  ← Read OLD value!
        # C:                                       |─ read(x) → 1 ─|

        # B's read completed AFTER A's write finished
        # But B saw old value (0)
        # → NOT linearizable!

    def concurrent_operations(self):
        """
        Example 3: Concurrent operations
        """
        # Timeline:
        # ──────────────────────────────────────────→ time
        #
        # A: |────── write(x, 1) ──────|
        # B:      |──── write(x, 2) ──────|
        # C:                                  |─ read(x) → ? ─|

        # A and B overlap (concurrent)
        # Either linearization is valid:
        # 1. write(x,1) → write(x,2) → read(x)→2  ✓
        # 2. write(x,2) → write(x,1) → read(x)→1  ✓

        # But C MUST see either 1 or 2 (not 0)
```

---

## Serializability vs Linearizability

🏗️ **ARCHITECT**: Two often-confused concepts:

```
┌────────────────────────────────────────────────────────────┐
│ Serializability (Database Transactions)                    │
│                                                             │
│ - Applies to: Transactions (groups of operations)          │
│ - Guarantee: Transactions appear to execute sequentially   │
│ - No real-time requirement                                 │
│                                                             │
│ Example:                                                    │
│   T1: read(x), write(y)                                    │
│   T2: read(y), write(x)                                    │
│                                                             │
│   Valid serialization: T1 then T2 (or T2 then T1)          │
│   Even if T2 actually finished first in real-time!         │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ Linearizability (Distributed Systems)                      │
│                                                             │
│ - Applies to: Individual operations (read/write)           │
│ - Guarantee: Operations appear atomic and instant          │
│ - Respects real-time order                                 │
│                                                             │
│ Example:                                                    │
│   write(x,1) completes at t1                               │
│   read(x) starts at t2 > t1                                │
│                                                             │
│   read(x) MUST see value 1 (real-time constraint)          │
└────────────────────────────────────────────────────────────┘

                Strict Serializability
                ═════════════════════
                Serializability + Linearizability
                (Strongest possible guarantee)
```

---

## Implementing Linearizability

🏗️ **ARCHITECT**: How to build linearizable systems:

### Approach 1: Single Leader

```python
class SingleLeaderLinearizability:
    """
    Single leader provides linearizability naturally
    """

    def write(self, key, value):
        # All writes go to leader
        self.leader.write(key, value)

        # Optionally wait for replication (sync vs async)

    def read(self, key):
        # For linearizability: read from leader
        return self.leader.read(key)

        # Reading from follower → NOT linearizable (may be stale)

    """
    Pros:
    ✓ Simple
    ✓ Naturally linearizable (single source of truth)

    Cons:
    ✗ Single point of failure
    ✗ All reads/writes go through leader (bottleneck)
    ✗ High latency for remote clients
    """
```

### Approach 2: Consensus (Raft/Paxos)

```java
/**
 * Use consensus for linearizable operations
 */
public class ConsensusLinearizability {

    /**
     * Every operation goes through consensus
     */
    public void write(String key, String value) {
        // Propose write to Raft cluster
        LogEntry entry = new LogEntry(
            Operation.WRITE,
            key,
            value
        );

        // Consensus: replicate to majority
        raft.propose(entry);

        // Once committed, apply to state machine
        // All replicas apply in same order → linearizable
    }

    public String read(String key) {
        // Option 1: Read from leader (linearizable)
        return raft.getLeader().read(key);

        // Option 2: Consensus read (slower but always linearizable)
        LogEntry entry = new LogEntry(Operation.READ, key, null);
        raft.propose(entry);  // Consensus on read!
        return raft.getCommittedValue(key);

        /**
         * Consensus provides linearizability because:
         * - All operations totally ordered (Raft log)
         * - Majority quorum ensures real-time ordering
         * - All replicas apply operations in same order
         */
    }
}
```

### Approach 3: Quorum Reads and Writes

```python
class QuorumLinearizability:
    """
    Quorum-based linearizability (like Dynamo-style systems)
    """

    def __init__(self, n=5, r=3, w=3):
        self.n = n  # Total replicas
        self.r = r  # Read quorum
        self.w = w  # Write quorum

        # For linearizability: r + w > n

    def write(self, key, value):
        """
        Write to majority quorum
        """
        version = self.get_next_version()

        # Write to W replicas
        responses = []
        for replica in self.replicas[:self.w]:
            replica.write(key, value, version)
            responses.append(replica)

        # Wait for W acknowledgments
        # → At least one replica in every read quorum has this write

    def read(self, key):
        """
        Read from majority quorum
        """
        # Read from R replicas
        responses = []
        for replica in self.replicas[:self.r]:
            value, version = replica.read(key)
            responses.append((value, version))

        # Return value with highest version
        latest = max(responses, key=lambda x: x[1])
        return latest[0]

        """
        Why r + w > n provides linearizability:

        - Write quorum size W
        - Read quorum size R
        - Total replicas N

        If W + R > N:
        → Any read quorum overlaps with any write quorum
        → Read sees latest write

        Example: N=5, W=3, R=3
        → W + R = 6 > 5  ✓
        → Linearizable
        """
```

---

## Google Spanner - Globally Distributed Linearizability

🏗️ **ARCHITECT**: Achieving linearizability across datacenters:

### The Challenge

```
Problem: Global linearizability with low latency
────────────────────────────────────────────────

Traditional approaches:
  - Consensus (Raft/Paxos): Limited to single region (latency)
  - Quorums: Work globally, but high quorum sizes needed

Spanner's solution:
  1. TrueTime (globally synchronized clocks)
  2. Wait out clock uncertainty
  3. Achieve external consistency (stronger than linearizability!)
```

### TrueTime Implementation

```java
public class TrueTime {

    /**
     * Returns time interval [earliest, latest]
     * Guarantee: actual time is in this interval
     */
    public TimeInterval now() {
        // GPS + atomic clocks
        // Uncertainty: typically ±7ms

        long currentTime = System.nanoTime();
        long uncertainty = 7_000_000;  // 7ms

        return new TimeInterval(
            currentTime - uncertainty,
            currentTime + uncertainty
        );
    }
}

public class SpannerExternalConsistency {

    /**
     * External consistency: If T1 commits before T2 starts,
     *                      T2 sees T1's writes
     */
    public void commit() {
        // 1. Get commit timestamp
        TimeInterval tt = trueTime.now();
        long commitTimestamp = tt.latest;

        // 2. Wait until commitTimestamp is in the past EVERYWHERE
        while (trueTime.now().earliest < commitTimestamp) {
            Thread.sleep(1);  // Wait ~7ms
        }

        // 3. Now safe to commit
        // All future transactions will see this commit

        this.raftGroup.commit(this.transaction, commitTimestamp);
    }

    /**
     * Why this works:
     *
     * Transaction T1:
     * - Commits with timestamp 100
     * - Waits until time 100 is in the past everywhere
     *
     * Transaction T2 (starts after T1 commits):
     * - Starts at time > 100 (because we waited)
     * - Sees all writes with timestamp ≤ 100
     * - Therefore sees T1's writes
     *
     * → External consistency!
     */
}
```

### Read-Only Transactions

```python
class SpannerReadOnlyTransactions:
    """
    Lock-free read-only transactions
    """

    def read_only_transaction(self):
        """
        Read consistent snapshot without locks
        """
        # Choose read timestamp
        read_ts = truetime.now().earliest

        # Read multiple rows at this timestamp
        user = self.read("users", "alice", read_ts)
        account = self.read("accounts", "alice", read_ts)

        # Consistent snapshot!
        # No locks acquired
        # Can read from nearest replica

    """
    Benefits:
    ✓ No locks (doesn't block writers)
    ✓ Can read from any replica (low latency)
    ✓ Consistent snapshot across all reads
    ✓ Perfect for analytics queries

    Cost:
    ✗ May read slightly stale data (within clock uncertainty)
    """
```

---

## The Cost of Strong Consistency

🎓 **PROFESSOR**: Understanding the trade-offs:

### Performance Impact

```
┌────────────────────────────────────────────────────────────┐
│                                                             │
│  Consistency Level     Latency    Availability  Throughput │
│  ──────────────────────────────────────────────────────────│
│  Eventual              1ms        High (AP)     High       │
│  Read-Your-Writes      5ms        High          High       │
│  Causal                10ms       Medium         Medium    │
│  Linearizable (local)  20ms       Medium (CP)    Medium    │
│  Linearizable (global) 100ms+     Medium (CP)    Low       │
│                                                             │
└────────────────────────────────────────────────────────────┘

Key insights:
- Linearizability requires coordination (consensus or quorums)
- Coordination adds latency (network roundtrips)
- Global linearizability even slower (cross-datacenter latency)
```

### CAP Theorem Perspective

```python
class CAPTheorem:
    """
    Linearizability forces choice between A and P
    """

    def cap_tradeoff(self):
        """
        Can't have all three:
        - Consistency (linearizability)
        - Availability (always respond)
        - Partition tolerance (work despite network splits)
        """

        # Scenario: Network partition
        # Cluster split into two partitions

        # CP (Consistency + Partition tolerance):
        # → Sacrifice availability
        # → Minority partition becomes unavailable
        # → Example: etcd, Consul, Spanner

        # AP (Availability + Partition tolerance):
        # → Sacrifice consistency
        # → Both partitions stay available
        # → Accept stale reads / write conflicts
        # → Example: Cassandra, DynamoDB

    def choosing_cp(self):
        """
        When to choose CP (linearizability)
        """
        use_cases = {
            "Bank account balances": "MUST be consistent",
            "Inventory counts": "MUST be consistent",
            "Leader election": "MUST have exactly one leader",
            "Distributed locks": "MUST be exclusive",
        }

        # Accept: Higher latency, potential unavailability

    def choosing_ap(self):
        """
        When to choose AP (eventual consistency)
        """
        use_cases = {
            "Social media posts": "Staleness OK",
            "Product catalog": "Staleness OK",
            "User comments": "Staleness OK",
            "DNS records": "Staleness OK",
        }

        # Accept: Stale reads, write conflicts
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Choosing Consistency Levels

```
Interview Decision Tree:
───────────────────────

Q1: Can system tolerate stale reads?
    YES → Use eventual consistency
    NO  → Continue to Q2

Q2: Is this financial or safety-critical data?
    YES → Use linearizability
    NO  → Continue to Q3

Q3: Do users need to see their own writes?
    YES → Use read-after-write consistency
    NO  → Use eventual consistency

Q4: Is data globally distributed?
    YES → Consider Spanner (if can afford TrueTime cost)
    NO  → Use Raft/Paxos-based system (etcd, Consul)
```

### Articulating Trade-offs

```
Interview talking points:
────────────────────────

"For bank account balances, we need linearizability because
users must see consistent balances across all operations.
We'll use Spanner with TrueTime, accepting ~100ms write latency.
This is acceptable because transfers are infrequent compared to reads."

"For social media posts, eventual consistency is sufficient.
Users can tolerate seeing slightly stale post counts.
We'll use Cassandra with tunable consistency, defaulting to
async replication for low latency."

"For inventory reservation, we need linearizability to prevent
double-booking. We'll use etcd (Raft-based) for coordination,
with pessimistic locking on inventory items."
```

---

## 🧠 MIND MAP: CONSISTENCY MODELS

```
       CONSISTENCY MODELS
              │
      ┌───────┴────────┐
      ↓                ↓
 STRONG           WEAK
      │                │
   ┌──┼──┐          ┌──┼──┐
   ↓  ↓  ↓          ↓  ↓  ↓
Linear Seq Causal  RYW Mon Event
 izable  ual          Reads  ual
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **Linearizability = Single Truth Source 📰**
- Official newspaper of record
- Everyone reads same edition
- No contradictions possible
- Expensive to distribute globally

### 2. **Eventual Consistency = Gossip 🗣️**
- News spreads person to person
- Different people know at different times
- Eventually everyone knows
- Fast but may be inaccurate initially

### 3. **Consensus = Jury Verdict ⚖️**
- Jury must agree unanimously (or majority)
- Takes time to deliberate
- But decision is final and consistent
- Can't proceed if jurors can't communicate

### 4. **TrueTime = Synchronized Watches ⌚**
- Everyone's watch shows same time (±7 seconds)
- Wait until everyone's watch surely past time T
- Then safe to proceed
- Coordination via time

### 5. **Quorum = Democracy Voting 🗳️**
- Need majority vote to proceed
- Ensures overlap between operations
- Prevents inconsistencies
- Requires communication with many nodes

---

## 🔑 Key Takeaways

1. **Linearizability = single-copy illusion**
   - Strongest consistency guarantee
   - Operations appear atomic and instant
   - Respects real-time ordering

2. **Not same as serializability**
   - Serializability: Transaction isolation
   - Linearizability: Distributed consistency
   - Strict serializability: Both combined

3. **Implementation requires coordination**
   - Single leader (simple but bottleneck)
   - Consensus (Raft/Paxos)
   - Quorums (r + w > n)

4. **TrueTime enables global linearizability**
   - Synchronized clocks across datacenters
   - Wait out uncertainty
   - Achieve external consistency

5. **Cost: latency and availability**
   - Coordination adds latency
   - May sacrifice availability (CAP theorem)
   - Only use when truly needed

6. **Most systems don't need it**
   - Eventual consistency often sufficient
   - Read-your-writes good middle ground
   - Reserve linearizability for critical data

7. **Know when to choose what**
   - Financial: Linearizable
   - User data: Read-after-write
   - Analytics: Eventual
   - Match consistency to requirements

---

**Final Thought**: Linearizability is the gold standard of consistency, but gold is expensive. Use it wisely - only for data where consistency is non-negotiable. For everything else, weaker (and cheaper) consistency models often suffice.
