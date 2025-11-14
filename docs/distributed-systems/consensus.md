# Consensus Algorithms

## The Consensus Problem

**Definition**: How to get multiple nodes in a distributed system to agree on a single value, even when some nodes fail or network is unreliable.

**Why It's Hard**:
- Nodes can crash
- Messages can be lost or delayed
- Network can partition
- Nodes can be malicious (Byzantine failures)

**Critical Use Cases**:
- Leader election
- Distributed configuration management
- Distributed locks
- Atomic commitment (transactions)

---

## Raft Consensus Algorithm

**Design Philosophy**: Understandable consensus algorithm

### Raft Basics

**Roles**:
1. **Leader**: Handles all client requests, sends heartbeats
2. **Follower**: Passive, responds to leader/candidate requests
3. **Candidate**: Trying to become leader during election

### Leader Election

```
Normal State:
[Leader] → heartbeat → [Follower 1]
         → heartbeat → [Follower 2]
         → heartbeat → [Follower 3]

Leader Fails:
[×××××] (no heartbeat)
[Follower 1] ← timeout → becomes Candidate
[Follower 2] ← timeout → becomes Candidate
[Follower 3] ← timeout → becomes Candidate

Election:
[Candidate] → RequestVote → [Follower 1] → Vote!
            → RequestVote → [Follower 2] → Vote!
(Majority votes) → Becomes Leader

New State:
[New Leader] → heartbeat → [Follower 1]
             → heartbeat → [Follower 2]
```

### Log Replication

```
Client writes value "X":
1. Client → [Leader]: Set X
2. Leader appends to local log
3. Leader → Followers: AppendEntries(X)
4. Followers append to logs
5. Followers → Leader: ACK
6. Leader commits (majority ACKs)
7. Leader → Client: Success
8. Leader → Followers: Update commit index
```

**Guarantees**:
- Logs are identical across majority
- Committed entries never lost
- Entries applied in same order

### Safety Properties

**Election Safety**: At most one leader per term
**Leader Append-Only**: Leader never overwrites/deletes entries
**Log Matching**: If two logs contain entry with same index/term, logs identical up to that point
**Leader Completeness**: If entry committed in term, present in logs of future leaders
**State Machine Safety**: If node applied log entry at index, no other node applies different entry at same index

### Raft vs Paxos

| Raft | Paxos |
|------|-------|
| Easier to understand | More complex |
| Strong leader | No designated leader |
| Term-based elections | Proposal numbers |
| Widely implemented | Theoretical foundation |

### Real-World Use

- **etcd**: Kubernetes' coordination service
- **Consul**: Service discovery and config
- **CockroachDB**: Distributed SQL database

**Interview Tip**:
```
"For a distributed configuration service like etcd, Raft provides
strong consistency guarantees. When updating config, we need all
nodes to see the same value in the same order. Raft's leader-based
approach makes this straightforward - leader serializes all updates."
```

---

## Paxos Consensus Algorithm

**The Classic**: Original proven consensus algorithm

### Key Concepts

**Phases**:
1. **Prepare**: Proposer sends proposal number
2. **Promise**: Acceptors promise not to accept older proposals
3. **Accept**: Proposer sends value
4. **Accepted**: Acceptors accept value

### Basic Paxos

```
Proposer                 Acceptors (A1, A2, A3)
   │
   ├─ Prepare(n=1) ────→ A1, A2, A3
   │                      │
   │←─ Promise(n=1) ─────┤ (no previous proposals)
   │
   ├─ Accept(n=1, v=X) ─→ A1, A2, A3
   │                      │
   │←─ Accepted(n=1) ────┤ (majority)
   │
   ├─ Commit
   │
   └─ Return success
```

### Multi-Paxos

Optimization for multiple consensus instances:
- Elect stable leader
- Skip prepare phase for subsequent proposals
- Similar to Raft in practice

### Use Cases

- **Google Chubby**: Distributed lock service
- **Apache ZooKeeper**: (Modified Paxos - ZAB protocol)
- **Google Spanner**: Uses Paxos for replication groups

---

## Two-Phase Commit (2PC)

**Purpose**: Atomic commitment protocol for distributed transactions

### Protocol

```
Phase 1: Prepare
Coordinator → Participants: "Can you commit?"
Participants → Coordinator: "Yes" or "No"

Phase 2: Commit/Abort
If all "Yes":
  Coordinator → Participants: "Commit"
  Participants commit transaction
Else:
  Coordinator → Participants: "Abort"
  Participants rollback transaction
```

### Example

```
Transfer $100 from Account A (DB1) to Account B (DB2):

Coordinator (Transaction Manager):
1. Begin transaction
2. Prepare phase:
   → DB1: "Prepare to debit $100 from A"
   → DB2: "Prepare to credit $100 to B"
3. Wait for votes
   ← DB1: "Yes (prepared)"
   ← DB2: "Yes (prepared)"
4. Commit phase:
   → DB1: "Commit"
   → DB2: "Commit"
5. Transaction complete
```

### Problems with 2PC

**Blocking Problem**:
```
If coordinator crashes after prepare phase:
- Participants are blocked (cannot commit or abort)
- Resources locked
- System unavailable

Solution: 3PC (Three-Phase Commit) - adds timeout
```

**Performance Issues**:
- Multiple round trips
- Synchronous blocking
- Single point of failure (coordinator)

**When to Use**:
- Strong consistency required
- Limited scale (< 100 participants)
- Can tolerate blocking
- Examples: Database transactions across shards

**When to Avoid**:
- High availability critical
- Large scale (100+ nodes)
- Cross-datacenter (high latency)
- Prefer eventual consistency

---

## Quorum-Based Consensus

**Concept**: Majority agreement instead of unanimous

### Quorum Formula

```
N = Number of replicas
W = Write quorum (number of ACKs needed for write)
R = Read quorum (number of reads to ensure latest value)

For strong consistency:
R + W > N

Common configurations:
N=3, W=2, R=2 → (R+W=4 > N=3) → Strong consistency
N=5, W=3, R=3 → (R+W=6 > N=5) → Strong consistency
```

### How It Works

```
Write to Key=X, Value=5, N=3:
Client → Node1: Write(X=5)
Node1 → Node1, Node2, Node3: Replicate(X=5)
Node2, Node3 → Node1: ACK
Client ← Node1: Success (W=2 ACKs received)

Read Key=X, N=3:
Client → Node1: Read(X)
Node1 → Node1, Node2, Node3: Read(X)
Node1, Node2 → responses: X=5
Node3 → response: X=4 (stale)
Node1 → Client: X=5 (latest value by timestamp/version)
```

### Use Cases

- **DynamoDB**: Configurable R/W quorums
- **Cassandra**: Tunable consistency levels
- **Riak**: Quorum-based operations

---

## Vector Clocks

**Purpose**: Track causality in distributed systems without synchronized clocks

### How Vector Clocks Work

```
Each node maintains vector: [N1: count, N2: count, N3: count]

Node1 processes event: [1, 0, 0]
Node2 processes event: [0, 1, 0]
Node1 sends to Node2:   [2, 0, 0]
Node2 receives:         [2, 2, 0] (max of each component + increment own)
```

### Detecting Conflicts

```
Version A: [3, 1, 0]
Version B: [2, 2, 0]

Neither is descendant of other → Conflict!
(A has higher N1, B has higher N2)
```

### Real-World Example (Shopping Cart)

```
User adds ItemA on Device1: [1, 0]
User adds ItemB on Device2: [0, 1] (offline)

Device synchronizes:
Cart1: [ItemA] [1, 0]
Cart2: [ItemB] [0, 1]

Conflict detected → Merge: [ItemA, ItemB] [1, 1]
```

**Used By**: Riak, Voldemort, DynamoDB

---

## Interview Questions & Answers

### Q: "Why is consensus hard in distributed systems?"

**Answer**:
```
Three core challenges:

1. Asynchronous Network:
   - Can't distinguish slow node from crashed node
   - Messages can be delayed arbitrarily
   - No way to know if message was lost

2. Partial Failures:
   - Some nodes fail while others work
   - Network can partition (split brain)
   - Must continue operating during failures

3. No Shared Clock:
   - Cannot rely on timestamps for ordering
   - Must use logical clocks or consensus
   - Race conditions in concurrent updates

These combine to create scenarios where nodes have
different views of system state, making agreement difficult.

Example: If you send a message asking "Are you alive?"
and get no response, you don't know if:
- Node crashed
- Network dropped your request
- Network dropped their response
- Node is just slow

This uncertainty makes building correct distributed
systems extremely challenging.
```

### Q: "When would you use Raft vs Paxos?"

**Answer**:
```
Use Raft when:
- Building new system (easier to implement correctly)
- Team needs to understand consensus (better documentation)
- Need stable leader (most cases)
- Examples: etcd, Consul

Use Paxos when:
- Academic/theoretical work
- Optimizing for specific scenarios
- Building on existing Paxos-based system
- Examples: Google Chubby, Spanner

In practice, most new systems use Raft because:
- Easier to understand and debug
- More resources and examples available
- Proven in production (etcd, Consul, etc.)
- Performance is comparable

Both provide same guarantees, Raft is just more accessible.
```

### Q: "How does DynamoDB achieve high availability with eventual consistency?"

**Answer**:
```
DynamoDB uses quorum-based replication without strict consensus:

1. Replication:
   - Data replicated across 3 availability zones
   - Each write goes to multiple replicas

2. Quorum Configuration:
   - Eventual consistency: W=1, R=1
   - Strong consistency: W=2, R=2 (or W=majority)

3. Eventual Consistency Process:
   Write:
   - Client writes to coordinator
   - Coordinator writes to one replica (fast!)
   - Returns success immediately
   - Asynchronously replicates to other replicas

   Read:
   - Reads from one replica (fast!)
   - Might get stale data
   - Background process synchronizes replicas

4. Strong Consistency Option:
   - Wait for majority of replicas
   - Guarantees latest value
   - Higher latency

5. Conflict Resolution:
   - Last-write-wins based on timestamp
   - Vector clocks for versioning
   - Application can handle conflicts

This gives:
- High availability (always accepts writes/reads)
- Low latency (don't wait for all replicas)
- Eventual convergence (data becomes consistent)

Trade-off: Temporary inconsistency for better performance
```

---

## Key Takeaways

1. **Consensus is Fundamental**: Core to distributed systems (leader election, replication, coordination)
2. **CAP Applies**: Can't have perfect consensus with perfect availability during partitions
3. **Choose Based on Needs**: Different algorithms for different consistency/availability trade-offs
4. **Quorum is Common**: Most production systems use quorum-based approaches
5. **Understand Trade-offs**: Consensus adds latency but provides correctness guarantees

---

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 9
- Raft Paper: "In Search of an Understandable Consensus Algorithm"
- Paxos Made Simple - Leslie Lamport
- Dynamo Paper (Amazon)
- Google Spanner Paper
- Jepsen.io - Consensus testing
