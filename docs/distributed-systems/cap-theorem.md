# CAP Theorem

## The Fundamental Trade-off

**CAP Theorem** (Brewer's Theorem): A distributed system can provide at most **two** of three guarantees:

1. **C**onsistency: All nodes see the same data at the same time
2. **A**vailability: Every request receives a response (success or failure)
3. **P**artition Tolerance: System continues operating despite network partitions

**Key Insight**: When network partition occurs, choose either C or A (not both)

```
       Consistency
            /\
           /  \
          /    \
         /  CA  \
        /        \
       /          \
      /            \
     /   CHOOSE    \
    /      2       \
   /______________  \
  CP              AP
Partition       Partition
Tolerance       Tolerance
```

---

## Understanding Each Property

### Consistency (Linearizability)

**Definition**: Every read receives the most recent write

**Example**:
```
Timeline:
t1: Client A writes X=5
t2: Write completes
t3: Client B reads X → Must return 5 (not old value)

Guarantee: Once write completes, all subsequent reads see new value
```

**What Consistency Means**:
```
Consistent System:
Write(X=5) completes at t=100
All reads after t=100 return X=5

Inconsistent System:
Write(X=5) completes at t=100
Read at t=110 might return X=4 (old value)
Read at t=120 might return X=5 (new value)
```

**Strong Consistency Examples**:
- Traditional relational databases (PostgreSQL, MySQL)
- Google Spanner
- etcd, ZooKeeper

### Availability

**Definition**: Every request receives a response, without guarantee it contains most recent data

**What Availability Means**:
```
Available System:
Request comes in → System MUST respond
- Can't say "wait, I'm syncing"
- Can't say "network partition, try later"
- Must return result (even if stale)

Unavailable System:
Request comes in → Might wait indefinitely
- Waiting for consensus
- Waiting for network to heal
- Waiting for coordinator
```

**Example**:
```
Available:
Client → Read(X)
System → Returns value (even if not latest)
Response time: 50ms

Not Available:
Client → Read(X)
System → Waits for quorum...
System → Waits for leader...
Response time: Never (timeout)
```

**High Availability Examples**:
- DynamoDB
- Cassandra
- Couchbase

### Partition Tolerance

**Definition**: System continues operating despite network failures splitting nodes

**Network Partition**:
```
Before Partition:
[Node 1] ←→ [Node 2] ←→ [Node 3]
  (All connected)

After Partition:
[Node 1] ←→ [Node 2]    X    [Node 3]
  (Partition: Node 3 isolated)

System must continue operating!
```

**Reality**: Partitions are inevitable
- Network switches fail
- Cables cut
- Datacenter isolation
- Cloud network issues

**Must Handle Partitions**: All distributed systems need P

---

## CAP Combinations

### CA: Consistency + Availability (No Partition Tolerance)

**When It Works**: Single node or network that never partitions

```
[Single Database]
 - Always consistent (one copy)
 - Always available (no coordination)
 - But: No partition tolerance (crashes = down)
```

**Examples**:
- Traditional single-server RDBMS (PostgreSQL, MySQL on one machine)
- Not truly distributed

**Reality**: **CA doesn't exist** in distributed systems
- Networks partition (it's physics)
- Must choose CP or AP when partition occurs

### CP: Consistency + Partition Tolerance (Sacrifice Availability)

**When Partition Occurs**: Prioritize consistency over availability

```
Network Partition Scenario:

[Node 1] ←→ [Node 2]    X    [Node 3]
(Majority partition)       (Minority partition)

CP System Behavior:
- Majority (Node 1, 2): Accept writes (has quorum)
- Minority (Node 3): REFUSE writes (no quorum)
  → Unavailable but consistent

Client → Node 3 → "Error: Cannot write (no quorum)"
```

**Guarantees**:
- All successful reads return latest write
- Some requests may fail (unavailable)
- No split-brain (conflicting data)

**Use Cases**:
- Financial transactions (no inconsistency)
- Inventory systems (prevent overselling)
- Configuration management

**Examples**:
- **MongoDB** (default): Primary-secondary, waits for majority
- **HBase**: Single master, waits for ZooKeeper
- **Google Spanner**: Waits for quorum
- **etcd**: Raft consensus
- **ZooKeeper**: ZAB consensus
- **Redis** (with wait): Synchronous replication

**Trade-off**: Downtime during partitions, but data always consistent

### AP: Availability + Partition Tolerance (Sacrifice Consistency)

**When Partition Occurs**: Prioritize availability over consistency

```
Network Partition Scenario:

[Node 1] ←→ [Node 2]    X    [Node 3]

AP System Behavior:
- Partition 1 (Node 1, 2): Accept writes
- Partition 2 (Node 3): Accept writes
  → All available!
  → But data diverges (temporarily inconsistent)

Client A → Node 1 → Write X=5 (succeeds)
Client B → Node 3 → Write X=7 (succeeds)

Conflict! Need reconciliation when partition heals
```

**Guarantees**:
- Every request gets response
- Might return stale data
- Eventual consistency (data converges over time)

**Use Cases**:
- Social media (likes, posts)
- Shopping carts (availability critical)
- DNS (stale acceptable)
- Caching (freshness less critical)

**Examples**:
- **Cassandra**: Multi-master, accepts writes everywhere
- **DynamoDB**: Leaderless, tunable consistency
- **Riak**: Leaderless, eventual consistency
- **CouchDB**: Multi-master replication
- **Voldemort**: Amazon-style AP

**Trade-off**: Temporary inconsistency, conflict resolution needed

---

## Practical Examples

### Example 1: Bank Account Transfer

**Requirement**: Transfer $100 from Account A to B

**CP Approach** (Traditional Banks):
```
Transaction starts:
1. Lock Account A and B
2. Debit A: $100
3. Credit B: $100
4. Commit or Rollback (atomic)

During network partition:
- If can't reach all replicas → FAIL transaction
- User sees error: "Service temporarily unavailable"
- Consistent: Never partial transfer

Result: Availability sacrificed for consistency
```

**AP Approach** (Not suitable for banking!):
```
Transaction starts:
1. Debit A: $100 (succeeds immediately)
2. Credit B: $100 (eventual, async)

During network partition:
- Both partitions accept operations
- Might debit A but not credit B yet
- Temporary inconsistency

Result: Would violate banking rules
(This is why banks use CP systems)
```

### Example 2: Social Media Likes

**Requirement**: Count likes on a post

**AP Approach** (Facebook, Twitter):
```
User clicks "Like":
1. Write to local datacenter (fast!)
2. Asynchronously replicate globally
3. User sees like counted immediately

During network partition:
- US datacenter: Shows 1,000 likes
- EU datacenter: Shows 998 likes
- Temporary inconsistency

Result:
- High availability ✓
- Low latency ✓
- Eventual consistency (fine for likes)
```

**Why AP Works Here**:
- Likes are not critical (small inconsistency OK)
- User experience > perfect accuracy
- Eventually consistent count is acceptable

### Example 3: Shopping Cart

**AP Approach** (Amazon):
```
User adds items to cart:
1. Update local replica immediately
2. Replicate asynchronously
3. User always sees cart (available)

Scenario - Two Devices:
Device 1: Add Book A → Cart: [Book A]
Device 2: Add Book B → Cart: [Book B]
(Network partition between devices)

When partition heals:
- Conflict: Cart has [Book A] or [Book B]?
- Resolution: Union → Cart: [Book A, Book B]

Result: Never lose cart items (bias toward adding)
```

**Why AP Works**:
- Cart availability critical (don't lose sales)
- Conflict resolution: Merge (add all items)
- User can remove unwanted items
- Better than refusing to add to cart

---

## PACELC: Beyond CAP

**PACELC Theorem**: More nuanced than CAP

**Formula**:
```
IF Partition (P):
  Choose Availability (A) or Consistency (C)
ELSE (E):
  Choose Latency (L) or Consistency (C)
```

**Key Insight**: Even without partitions, trade-off between latency and consistency

### Examples:

**PA/EL (DynamoDB default)**:
```
During Partition: Choose Availability
During Normal: Choose Latency (eventual consistency)

Behavior:
- Writes return immediately (don't wait for all replicas)
- Reads from local replica (might be stale)
- Fast, available, eventually consistent
```

**PC/EC (Google Spanner)**:
```
During Partition: Choose Consistency
During Normal: Choose Consistency (at cost of latency)

Behavior:
- Writes wait for quorum
- Reads strongly consistent
- Higher latency, strongly consistent
```

**PA/EC (Cassandra with quorum)**:
```
During Partition: Choose Availability (some nodes)
During Normal: Choose Consistency (quorum reads/writes)

Configurable per query!
```

---

## Tunable Consistency

**Modern Databases**: Not binary CP vs AP, but tunable

### Cassandra Consistency Levels

**Write Consistency Levels**:
```
ONE: Wait for 1 replica (fast, available)
QUORUM: Wait for majority (balanced)
ALL: Wait for all replicas (slow, consistent)
LOCAL_QUORUM: Majority in local DC
```

**Read Consistency Levels**:
```
ONE: Read from 1 replica (fast, might be stale)
QUORUM: Read from majority (consistent)
ALL: Read from all (highest consistency)
```

**Combining for Strong Consistency**:
```
If W + R > N (replication factor):
  → Strong consistency

Example:
N = 3 (three replicas)
W = 2 (QUORUM write)
R = 2 (QUORUM read)
W + R = 4 > 3 → Guarantees overlap → Consistent!
```

**Flexibility**:
```
Critical data (inventory):
  Write: QUORUM
  Read: QUORUM
  → Strong consistency

Non-critical (analytics):
  Write: ONE
  Read: ONE
  → High availability, low latency
```

### DynamoDB Consistency Options

**Eventual Consistent Reads** (default):
```
- Fastest
- Cheapest
- Might return stale data
- Use case: Product catalog, user profiles
```

**Strongly Consistent Reads**:
```
- Slower
- More expensive
- Always returns latest data
- Use case: Inventory count, account balance
```

---

## Strategies for Handling Consistency/Availability Trade-offs

### 1. Read Repair

**Concept**: Fix inconsistencies during reads

```
Read from 3 replicas:
Replica 1: X=5 (version 10)
Replica 2: X=5 (version 10)
Replica 3: X=4 (version 9)  ← Stale!

Client receives: X=5 (latest)
Background: System updates Replica 3 to X=5
```

### 2. Hinted Handoff

**Concept**: Temporary storage when node down

```
Write X=5, need 3 replicas:
Node A: Success
Node B: Success
Node C: Down!

Solution:
- Write "hint" to Node D: "When C returns, write X=5"
- Node D holds hint temporarily
- When C returns: Node D sends X=5 to C
```

### 3. Anti-Entropy (Merkle Trees)

**Concept**: Background sync to detect divergence

```
Each node maintains Merkle tree:
- Hash of data ranges
- Compare trees between nodes
- Identify differences
- Sync only different ranges

Periodic background process ensures eventual consistency
```

### 4. Conflict Resolution Strategies

**Last Write Wins (LWW)**:
```
Write 1: X=5 timestamp=100
Write 2: X=7 timestamp=102
Result: X=7 (higher timestamp wins)

Problem: Lost update if clocks skewed
```

**Vector Clocks**:
```
Track causality:
Version 1: X=5 [1, 0, 0]
Version 2: X=7 [0, 1, 0]

Concurrent writes! → Conflict
Application resolves (or keep both)
```

**Application-Level Resolution**:
```
Shopping cart conflict:
Cart 1: [Book A]
Cart 2: [Book B]

Resolution: Union → [Book A, Book B]
Business logic decides merge strategy
```

---

## Interview Q&A

**Q: "Explain CAP theorem and give real-world examples"**

**Answer**:
```
CAP Theorem: In a distributed system during network partition,
choose either Consistency or Availability.

Real-World Examples:

CP (Consistency + Partition Tolerance):
- Banking systems: Must prevent double-spending
- Inventory: Can't oversell items
- Google Spanner: Waits for quorum, might be unavailable
- Trade-off: Downtime during partitions

AP (Availability + Partition Tolerance):
- Social media likes: Temporary wrong count OK
- Shopping carts: Never refuse to show cart
- DNS: Stale records acceptable for availability
- Trade-off: Eventual consistency, conflicts

Key Insight:
- Networks WILL partition (reality)
- Must choose based on business requirements
- Not binary: Modern systems offer tunable consistency

Example Decision:
"For e-commerce checkout, I'd use CP for payment processing
(consistency critical) but AP for browsing catalog (availability
more important than perfect real-time inventory)."
```

**Q: "How does DynamoDB achieve both high availability and consistency?"**

**Answer**:
```
DynamoDB offers TUNABLE consistency:

Architecture:
- Data replicated to 3 availability zones
- Leaderless (no single master)
- Quorum-based reads/writes

Option 1: Eventual Consistency (AP)
Read:
  - Query 1 replica (fast!)
  - Might get stale data
  - Default for GetItem

Write:
  - Write to 1 replica immediately
  - Background replication to others
  - Fast, available

Option 2: Strong Consistency (CP)
Read:
  - Query quorum (2+ replicas)
  - Wait for latest value
  - ConsistentRead=true flag

Write:
  - Wait for quorum acknowledgment
  - Slower but consistent

Handling Partitions:
- Eventual: All partitions accept reads/writes (AP)
- Strong: Majority partition accepts (CP), minority refuses

Trade-offs:
- Eventual: 50% cost, 2x throughput, low latency
- Strong: 100% cost, 1x throughput, higher latency

Application chooses per request based on needs!
```

**Q: "Design a system for a global e-commerce platform considering CAP"**

**Answer**:
```
Use different consistency models for different components:

1. Product Catalog (AP):
   - Eventual consistency acceptable
   - High availability critical
   - Multi-region DynamoDB with eventual reads
   - User can tolerate slightly stale prices/descriptions

2. Shopping Cart (AP):
   - Always available (never lose cart)
   - Conflict resolution: Union (add all items from all sessions)
   - Session-based, eventual sync
   - User experience > perfect consistency

3. Inventory (CP when critical):
   - Write: Strong consistency (prevent overselling)
   - Read: Eventual (browsing OK with approximate count)
   - Reserve operation: ACID transaction
   - At checkout: Verify with strong consistency

4. Payment Processing (CP):
   - Strong consistency required
   - Database transactions (2PC)
   - Can show "processing" to user (brief unavailability OK)
   - Financial correctness > availability

5. Order History (AP):
   - Eventual consistency fine
   - Historical data, not time-sensitive
   - High read availability

Architecture:
┌─────────────────────┐
│   API Gateway       │
└──────────┬──────────┘
           │
    ┌──────┴──────┐
    │             │
┌───▼────┐   ┌───▼────┐
│Catalog │   │Payment │
│  (AP)  │   │  (CP)  │
└────────┘   └────────┘
    │             │
┌───▼────┐   ┌───▼────┐
│ Cart   │   │Inventory
│ (AP)   │   │ (CP)   │
└────────┘   └────────┘

Rationale:
- Different components have different needs
- Use appropriate consistency model for each
- Balance user experience with correctness
- Most data can be eventually consistent
- Critical path (payment) uses strong consistency
```

---

## Key Takeaways

1. **CAP is Fundamental**: Understand trade-offs in distributed systems
2. **Partitions Happen**: Must choose C or A when network fails
3. **Not Binary**: Modern systems offer tunable consistency
4. **Business Requirements Drive Choice**: Payment (CP) vs Likes (AP)
5. **PACELC Extends CAP**: Consider latency during normal operation
6. **Most Systems Choose AP**: High availability often more valuable

---

## Further Reading

- "Brewer's CAP Theorem" - Original paper by Eric Brewer
- "CAP Twelve Years Later: How the Rules Have Changed" - Eric Brewer
- "Designing Data-Intensive Applications" - Chapter 5, 9
- "Perspectives on the CAP Theorem" - Gilbert and Lynch
- DynamoDB Paper: "Dynamo: Amazon's Highly Available Key-value Store"
- Google Spanner Paper: "Spanner: Google's Globally-Distributed Database"
- Jepsen.io: Testing distributed systems for CAP violations
