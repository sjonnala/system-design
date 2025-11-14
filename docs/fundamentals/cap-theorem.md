# CAP Theorem

## Definition

**CAP Theorem** (Brewer's Theorem) states that a distributed data store can provide only **two out of three** guarantees:

- **C**onsistency: All nodes see the same data at the same time
- **A**vailability: Every request receives a response (success or failure)
- **P**artition Tolerance: System continues to operate despite network partitions

```
    Consistency
       /  \
      /    \
     /  CA  \
    /________\
   /   |  |   \
  / CP |  | AP \
 /_____|__|_____\
Partition    Availability
```

## Deep Dive

### Consistency (C)
- Every read receives the most recent write
- All clients see the same data simultaneously
- Strong consistency guarantee
- Achieved through synchronous replication
- Example: Reading from database immediately shows latest write

### Availability (A)
- Every request (read/write) gets a non-error response
- System remains operational even when nodes fail
- Response might not contain latest data
- Prioritizes system uptime
- Example: System returns cached/stale data rather than error

### Partition Tolerance (P)
- System continues despite network failures between nodes
- Messages can be dropped or delayed
- Required in distributed systems (you can't avoid network issues)
- The system must choose between C or A during partition

## Why You Can't Have All Three

When a **network partition** occurs:

```
Network Partition Scenario:

   Client A          Client B
      |                 |
      v                 v
  [Node 1] -X-X-X- [Node 2]
  (writes)         (reads)
```

You must choose:

### Option 1: Maintain Consistency (CP)
- Block writes until partition heals
- Some requests return errors
- Sacrifice availability

### Option 2: Maintain Availability (AP)
- Accept writes on both sides
- Data diverges temporarily
- Sacrifice consistency

## CAP Combinations

### CA Systems (Consistency + Availability)
**Reality**: Impractical in distributed systems

- Network partitions are inevitable
- Can only exist in single-node or same-datacenter systems
- Examples: Traditional RDBMS in single server, PostgreSQL (single node)

**Interview Insight**: In modern distributed systems, P is not optional. Networks fail. The real choice is between CP and AP.

### CP Systems (Consistency + Partition Tolerance)
**Priority**: Correctness over availability

**Characteristics**:
- Blocks operations during partition
- Returns errors rather than stale data
- Strong consistency guarantees
- Higher latency during normal operations

**Use Cases**:
- Financial transactions
- Inventory management
- Booking systems
- Banking systems

**Examples**:
- MongoDB (configurable, default CP)
- HBase
- Redis (single leader)
- etcd
- Consul
- ZooKeeper

**Interview Example**:
```
"For a banking system, I'd choose a CP database like MongoDB with
strong consistency settings. We cannot allow reading stale balance
data, as it could lead to overdrafts. During network partition,
it's better to return an error than show incorrect balance."
```

### AP Systems (Availability + Partition Tolerance)
**Priority**: Uptime over immediate consistency

**Characteristics**:
- Always accepts reads/writes
- Data eventually becomes consistent
- Lower latency
- May return stale data

**Use Cases**:
- Social media feeds
- Shopping carts
- DNS
- Caching systems
- Analytics data

**Examples**:
- Cassandra
- DynamoDB
- Riak
- CouchDB
- Voldemort

**Interview Example**:
```
"For a social media feed, I'd choose an AP database like Cassandra.
Users can tolerate seeing slightly stale posts. It's more important
that the app remains responsive and doesn't show errors. Data will
eventually sync across regions."
```

## Real-World Trade-offs

### Banking System (CP)
```
Scenario: Transfer $100 from Account A to Account B

During Partition:
- System blocks transaction
- Returns error to user
- No inconsistent state

Trade-off Accepted:
✓ No money created/lost (consistent)
✗ Transaction temporarily unavailable
```

### Social Media (AP)
```
Scenario: User posts a photo

During Partition:
- Post accepted immediately
- May not appear to all users instantly
- Eventually propagates

Trade-off Accepted:
✓ User gets immediate confirmation
✗ Followers may see post with delay
```

## PACELC Theorem (Extended CAP)

CAP only addresses network partition scenarios. **PACELC** extends this:

```
If Partition (P):
  Choose between Availability (A) and Consistency (C)
Else (E):
  Choose between Latency (L) and Consistency (C)
```

### Examples

| System | Partition | Normal Operation |
|--------|-----------|------------------|
| DynamoDB | AP | LC (favors latency) |
| MongoDB | CP | PC (favors consistency) |
| Cassandra | AP | EL (favors latency) |
| HBase | CP | PC (favors consistency) |

**Interview Insight**: Even when network is healthy, you trade latency for consistency. Synchronous replication (consistent) is slower than async (lower latency).

## Common Misconceptions

### Myth 1: "NoSQL is always AP"
**Reality**: Depends on configuration
- MongoDB can be configured as CP
- Cassandra allows tunable consistency
- DynamoDB supports strong consistency reads

### Myth 2: "ACID violates CAP"
**Reality**: ACID is about single-node transactions
- CAP applies to distributed systems
- Can have ACID transactions in CP systems
- BASE (AP systems) is alternative to ACID

### Myth 3: "You choose CAP once for entire system"
**Reality**: Different components can make different choices
- User data: CP (consistent profiles)
- Activity feed: AP (eventual consistency okay)
- Payment: CP (must be consistent)

## Interview Strategy

### Requirements Gathering
Ask these questions to determine CAP choice:

1. **Data Criticality**: Can users see stale data?
2. **Geographic Distribution**: Single vs multi-region?
3. **Availability Requirements**: Can we return errors?
4. **Latency Requirements**: How fast must responses be?
5. **Consistency Requirements**: Must all reads be up-to-date?

### Example Dialog

**Interviewer**: "Design a distributed counter for video views"

**Good Response**:
```
"Let me clarify the consistency requirements:

1. Do we need exact counts in real-time?
   - If yes → CP system
   - If approximate is okay → AP system

2. Can we tolerate some view count divergence across regions?
   - If yes → AP with eventual consistency
   - If no → CP with synchronous replication

For YouTube-like system, I'd choose AP because:
- Users tolerate slightly stale counts
- High availability is critical (views recorded 24/7)
- Low latency more important than exact count
- Can use eventual consistency and periodic reconciliation

I'd use DynamoDB with async replication, accepting
short-term inconsistencies for better performance."
```

## CAP in Real Systems

### Amazon DynamoDB (AP)
- Highly available
- Eventual consistency by default
- Offers optional strong consistency for reads
- Accepts writes during partitions
- **Design Choice**: Shopping cart can tolerate temporary inconsistency

### Google Spanner (CP-ish)
- Strong consistency across global distribution
- Uses synchronized clocks (TrueTime)
- Higher latency due to coordination
- **Design Choice**: Financial data requires consistency

### Apache Cassandra (AP)
- Always-on availability
- Tunable consistency levels
- Partitioned across data centers
- **Design Choice**: Handles write-heavy workloads, eventually consistent

### MongoDB (CP)
- Primary handles writes
- Strong consistency by default
- Blocks writes if majority unavailable
- **Design Choice**: Document store with consistency guarantees

## Interview Red Flags to Avoid

❌ "We'll use blockchain for consistency"
❌ "We can have all three with this new architecture"
❌ "CAP doesn't matter for our use case"
❌ "We'll just avoid network partitions"

✓ "Given the requirements, I'd choose [CP/AP] because..."
✓ "We can use different strategies for different data types"
✓ "During partition, we'll prioritize [availability/consistency]"
✓ "Network partitions are inevitable, so we must plan for them"

## Practice Questions

1. **Design a like counter for Instagram posts. CP or AP?**
   - Answer: AP. Slight inconsistency acceptable, availability critical

2. **Design a seat reservation system for airlines. CP or AP?**
   - Answer: CP. Cannot double-book seats, consistency critical

3. **Design a distributed cache. CP or AP?**
   - Answer: Usually AP. Stale cache acceptable, high availability needed

4. **Design a collaborative document editor. CP or AP?**
   - Answer: Depends. Real-time requires AP with conflict resolution. Version-based can use CP.

5. **How would you handle shopping cart in multi-region deployment?**
   - Answer: AP system. Cart merging on conflicts. Better to have duplicate items than lose items.

## Key Takeaways

1. **P is Mandatory**: In distributed systems, you must handle partitions
2. **Real Choice**: CP vs AP, based on business requirements
3. **Not Binary**: Can tune consistency levels, use different approaches for different data
4. **Think Beyond CAP**: Consider latency, cost, complexity in normal operation (PACELC)
5. **Business First**: Let business requirements drive technical choices

## Further Reading

- Original Paper: "Brewer's Conjecture and the Feasibility of Consistent, Available, Partition-Tolerant Web Services"
- PACELC Theorem by Daniel Abadi
- "Designing Data-Intensive Applications" by Martin Kleppmann (Chapter 9)
