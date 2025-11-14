# Replication Strategies

## Why Replication?

1. **High Availability**: System continues if one node fails
2. **Read Scalability**: Distribute read load across replicas
3. **Low Latency**: Serve from geographically closer replica
4. **Disaster Recovery**: Data backup in different locations

---

## Leader-Follower Replication

### Architecture

```
Client (writes) → [Leader] → log → [Follower 1]
                           → log → [Follower 2]
                           → log → [Follower 3]

Client (reads) → [Any Replica]
```

### How It Works

1. Client sends write to leader
2. Leader writes to local storage
3. Leader sends replication log to followers
4. Followers apply changes from log
5. Followers acknowledge
6. Leader responds to client

### Synchronous vs Asynchronous

**Synchronous Replication**:
```
Client → Leader: Write(X=5)
Leader → Local: Write(X=5)
Leader → Follower1: Replicate(X=5)
Follower1 → Leader: ACK
Leader → Follower2: Replicate(X=5)
Follower2 → Leader: ACK
Leader → Client: Success
```
- **Pros**: Strong consistency, no data loss
- **Cons**: Higher latency, reduced availability

**Asynchronous Replication**:
```
Client → Leader: Write(X=5)
Leader → Local: Write(X=5)
Leader → Client: Success (immediate)
Leader → Followers: Replicate(X=5) (background)
```
- **Pros**: Low latency, high availability
- **Cons**: Potential data loss, eventual consistency

**Semi-Synchronous** (Hybrid):
```
- Wait for one follower (synchronous)
- Others replicate asynchronously
- Balance of safety and performance
```

### Failover Process

```
1. Detect leader failure (heartbeat timeout)
2. Choose new leader (election or predetermined)
3. Reconfigure system to use new leader
4. Resume operations
```

**Challenges**:
- Split-brain: Two leaders simultaneously
- Data loss: Async replication lag
- Consistency: Follower becomes leader with stale data

### Use Cases

- **MySQL Replication**: Leader-follower with async/semi-sync
- **PostgreSQL**: Primary-replica with streaming replication
- **MongoDB**: Primary-secondary with replica sets
- **Redis**: Master-replica replication

---

## Multi-Leader Replication

### Architecture

```
[Leader 1] ⟷ replication ⟷ [Leader 2]
    ↓                          ↓
[Follower 1a]             [Follower 2a]
[Follower 1b]             [Follower 2b]
```

### When to Use

1. **Multi-Datacenter**: Leader in each datacenter
2. **Offline Operations**: Each device has local leader
3. **Collaborative Editing**: Multiple concurrent editors

### Example: Multi-Datacenter

```
US Datacenter:
  [Leader US] → local followers
      ↓ (async replication)
EU Datacenter:
  [Leader EU] → local followers

Benefits:
- Low latency for local writes
- Datacenter failure tolerance
- Better performance
```

### Write Conflicts

**Problem**:
```
User A (US): Updates title = "Hello"
User B (EU): Updates title = "Hi there"

Both leaders accept writes → Conflict!
```

**Conflict Resolution Strategies**:

1. **Last Write Wins (LWW)**:
```
- Use timestamp to determine winner
- Simple but can lose data
- Used by: Cassandra, Riak
```

2. **Application-Level Resolution**:
```
- Present both versions to user
- User decides which to keep
- Used by: Git, CouchDB
```

3. **Merge**:
```
- Automatic merge if possible
- Example: Shopping cart - union of items
- Used by: Riak (CRDTs)
```

4. **Causality Tracking**:
```
- Use version vectors
- Detect concurrent vs sequential updates
- Merge concurrent, override sequential
```

### Real-World Examples

- **DynamoDB**: Multi-leader within region
- **Cassandra**: Multi-leader everywhere
- **CouchDB**: Multi-leader with conflict resolution

---

## Leaderless Replication

### Architecture

```
Client writes to multiple nodes directly:

Client → [Node 1]
      → [Node 2]
      → [Node 3]
      → [Node 4]
      → [Node 5]

Waits for quorum (e.g., 3/5) before success
```

### Quorum Writes and Reads

```
Configuration: N=5, W=3, R=3

Write:
1. Client sends write to all 5 nodes
2. Waits for 3 ACKs (W=3)
3. Returns success

Read:
1. Client sends read to all 5 nodes
2. Waits for 3 responses (R=3)
3. Returns latest value (by version/timestamp)

Guarantee: R + W > N ensures latest value
```

### Handling Failed Replicas

**Read Repair**:
```
During read:
- Node1 returns v5
- Node2 returns v5
- Node3 returns v4 (stale)

Client detects staleness → Sends v5 to Node3
```

**Anti-Entropy Process**:
```
Background process:
- Nodes compare data (Merkle trees)
- Identify differences
- Synchronize data
```

### Sloppy Quorums and Hinted Handoff

**Problem**: Some nodes temporarily unreachable

**Sloppy Quorum**:
```
Prefer nodes: [N1, N2, N3, N4, N5]
N2 is down → Write to temporary node N6
Later: Transfer data from N6 to N2 (hinted handoff)

Increases availability at cost of consistency
```

### Concurrent Writes

**Version Vectors**:
```
Track causality per replica:

Node1 writes: [N1: 1, N2: 0, N3: 0]
Node2 writes: [N1: 1, N2: 1, N3: 0]
```

**CRDTs** (Conflict-Free Replicated Data Types):
```
Data structures that automatically merge:
- Counters (increment only)
- Sets (add/remove elements)
- Registers (versioned values)

Example: Shopping cart - union of all items
```

### Use Cases

- **Cassandra**: Leaderless with tunable consistency
- **Riak**: Leaderless with CRDTs
- **DynamoDB**: Leaderless with quorums

---

## Replication Lag

### Problems

**Reading Your Own Writes**:
```
User updates profile → Write to leader
User refreshes → Read from stale follower → Sees old profile!

Solution: Read-after-write consistency
- Read user's own data from leader
- Track timestamp of last update
```

**Monotonic Reads**:
```
User reads comment: "Great post!" (from Replica 1)
User refreshes: Comment disappears! (from slower Replica 2)

Solution:
- Route user to same replica
- Track last-seen version
```

**Consistent Prefix Reads**:
```
Observer sees:
Q: "How are you?"
(Missing: Original post)
A: "I'm great!"

Solution: Keep causally related writes together
```

---

## Interview Q&A

**Q: "Design replication for a global social media platform"**

**Answer**:
```
I'd use multi-leader replication with eventual consistency:

Architecture:
- Leader in each region (US, EU, Asia)
- Local followers in each region
- Async replication between leaders

Rationale:
1. Low latency: Users write to local leader
2. High availability: Region failure doesn't affect others
3. Eventual consistency acceptable for social media

Conflict Resolution:
- Posts: Unique IDs, no conflicts
- Likes: CRDTs (counter), automatic merge
- Profile updates: Last-write-wins with vector clocks

Trade-offs:
- User might see different like counts temporarily
- Profile updates from different devices might conflict
- But: System always available, low latency globally
```

**Q: "When would you choose synchronous over asynchronous replication?"**

**Answer**:
```
Choose Synchronous when:
- Data loss unacceptable (financial transactions)
- Strong consistency required (inventory, reservations)
- Can tolerate higher latency
- Example: Banking - must confirm money transferred

Choose Asynchronous when:
- Availability critical (always accept writes)
- Can tolerate eventual consistency
- Low latency required
- Example: Social media posts, analytics

Hybrid (Semi-Sync):
- Wait for one synchronous replica (safety)
- Others async (performance)
- Example: MySQL semi-sync, good middle ground
```

---

## Key Takeaways

1. **Leader-Follower**: Simple, common, good for read-heavy workloads
2. **Multi-Leader**: Complex but needed for multi-datacenter, offline
3. **Leaderless**: High availability, eventual consistency
4. **Lag is Inevitable**: Async replication always has lag
5. **Pick Based on Needs**: Consistency vs availability vs latency

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 5
- MySQL Replication Documentation
- MongoDB Replica Sets
- Cassandra Architecture
- DynamoDB Consistency Models
