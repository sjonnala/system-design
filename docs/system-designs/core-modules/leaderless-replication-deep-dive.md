# Leaderless Replication Deep Dive - Introduction

## Contents

- [Leaderless Replication Deep Dive - Introduction](#leaderless-replication-deep-dive---introduction)
    - [Core Mental Model](#core-mental-model)
    - [Why Leaderless? (The Availability Problem)](#why-leaderless-the-availability-problem)
    - [How Leaderless Replication Works](#how-leaderless-replication-works)
    - [Read Repair and Anti-Entropy](#read-repair-and-anti-entropy)
    - [Consistency Levels in Practice](#consistency-levels-in-practice)
    - [Sloppy Quorums and Hinted Handoff](#sloppy-quorums-and-hinted-handoff)
    - [Real-World Leaderless Systems](#real-world-leaderless-systems)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: LEADERLESS REPLICATION](#mind-map-leaderless-replication)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Leaderless Replication = Democratic Consensus

No single leader, all nodes are equals

┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐  ┌────────┐
│ Node A │  │ Node B │  │ Node C │  │ Node D │  │ Node E │
│  (peer)│  │  (peer)│  │  (peer)│  │  (peer)│  │  (peer)│
└────────┘  └────────┘  └────────┘  └────────┘  └────────┘
    ↕           ↕           ↕           ↕           ↕
  Client writes/reads to ANY node
  Node coordinates with others (no dedicated leader)

The Core Principle:
┌──────────────────────────────────────────────────────────┐
│                                                           │
│  Write to MULTIPLE nodes                                 │
│  Read from MULTIPLE nodes                                │
│  Use QUORUM to determine success                         │
│                                                           │
│  No single point of failure                              │
│  No failover needed                                      │
│                                                           │
└──────────────────────────────────────────────────────────┘
```

**The Fundamental Equation:**
```
N = Total number of replicas
W = Write quorum (successful writes needed)
R = Read quorum (replicas to read from)

Consistency guarantee: If W + R > N, reads see latest write

Example: N=3, W=2, R=2
- Write succeeds on 2 out of 3 nodes
- Read from 2 out of 3 nodes
- Guaranteed: Read overlap includes ≥1 node with latest write
```

---

## Why Leaderless? (The Availability Problem)

🎓 **PROFESSOR**: The motivation for leaderless architectures:

### Problem with Leader-Based Replication

```
Single-Leader:
─────────────
┌──────────┐
│  LEADER  │ ← Single point of failure
└────┬─────┘
     │
  ┌──┼──┐
  ↓  ↓  ↓
 R1 R2 R3

If leader fails:
1. Detect failure (30-60 seconds)
2. Elect new leader (consensus, could fail)
3. Reconfigure clients
4. During this time: NO WRITES ACCEPTED

Downtime: Seconds to minutes
```

### Leaderless Solution

```
Leaderless:
──────────
┌────┐ ┌────┐ ┌────┐ ┌────┐ ┌────┐
│ N1 │ │ N2 │ │ N3 │ │ N4 │ │ N5 │
└────┘ └────┘ └────┘ └────┘ └────┘

If N2 fails:
1. Client tries N2, gets timeout
2. Client writes to N1, N3, N4, N5
3. Success if W nodes respond (e.g., 3 out of 4)
4. No reconfiguration needed

Downtime: ZERO (as long as W nodes available)
```

🏗️ **ARCHITECT**: Real-world motivation - Amazon Dynamo (2007):

```python
class DynamoMotivation:
    """
    Amazon's reasoning for leaderless replication (from Dynamo paper)
    """

    def business_requirements(self):
        """
        Amazon.com's 2007 requirements:
        - 99.99% availability (53 minutes downtime/year max)
        - Scale to millions of customers
        - Shopping cart must ALWAYS be writable
        - Brief inconsistency acceptable (user can resolve in cart UI)
        """

        # Problem with leader-based:
        # - Leader failure = no writes for 1-2 minutes
        # - At Amazon's scale: $10,000s lost per minute
        # - Unacceptable for business

        # Solution: Leaderless
        # - No single point of failure
        # - Always accept writes (even if inconsistent)
        # - Eventually consistent (good enough for shopping cart)

    def architecture_choices(self):
        """
        Dynamo's design choices
        """
        return {
            "replication": "Leaderless (no primary)",
            "consistency": "Tunable quorums",
            "conflict_resolution": "Vector clocks + app merge",
            "partitioning": "Consistent hashing",
            "availability": "Sloppy quorums (write to any healthy node)",
            "anti_entropy": "Merkle trees"
        }
```

**Interview Gold**: "Leaderless replication is the CAP theorem in action. Dynamo chose AP (Availability + Partition tolerance) over C (Consistency). Shopping cart availability > consistency."

---

## How Leaderless Replication Works

🎓 **PROFESSOR**: The write and read protocol:

### Write Protocol

```
Client wants to write: key="user:123", value="Alice"
Config: N=5, W=3 (need 3 successful writes)

Step 1: Client determines which nodes store this key
        (using consistent hashing)
        Nodes: A, B, C, D, E

Step 2: Client sends write request to ALL 5 nodes in parallel
        ┌─→ Node A: WRITE(user:123, "Alice", version=5)
        ├─→ Node B: WRITE(user:123, "Alice", version=5)
        ├─→ Node C: WRITE(user:123, "Alice", version=5)
Client ─┼─→ Node D: WRITE(user:123, "Alice", version=5)
        └─→ Node E: WRITE(user:123, "Alice", version=5)

Step 3: Nodes respond
        Node A: ✓ Success
        Node B: ✗ Timeout (down or slow)
        Node C: ✓ Success
        Node D: ✓ Success
        Node E: ✗ Timeout

Step 4: Client received 3 successes (≥ W=3)
        → Write considered successful!
        → Return SUCCESS to application

Result: 3 nodes have new data, 2 nodes have old/no data
```

### Read Protocol

```
Client wants to read: key="user:123"
Config: N=5, R=3 (read from 3 nodes)

Step 1: Client sends read request to R=3 nodes
        (usually same nodes as write, but could be different)

        ┌─→ Node A: READ(user:123)
Client ─┼─→ Node C: READ(user:123)
        └─→ Node D: READ(user:123)

Step 2: Nodes respond with value AND version
        Node A: value="Alice", version=5
        Node C: value="Alice", version=5
        Node D: value="Alice", version=5

Step 3: Client picks latest version (all same, version=5)
        → Return "Alice" to application

Edge Case: Inconsistent reads
──────────────────────────────
If Node D was down during write, it might return:
        Node A: value="Alice", version=5
        Node C: value="Alice", version=5
        Node D: value="Bob",   version=4  ← Stale!

Client detects: version=5 > version=4
        → Return "Alice" (latest)
        → Trigger read repair (update Node D in background)
```

🏗️ **ARCHITECT**: Cassandra implementation:

```java
/**
 * Cassandra's leaderless write/read
 */
public class CassandraClient {

    private static final int N = 3;  // Replication factor
    private int W = 2;  // Write quorum (tunable)
    private int R = 2;  // Read quorum (tunable)

    public void write(String key, String value) {
        // Step 1: Determine replicas using token ring (consistent hashing)
        List<Node> replicas = consistentHash.getReplicas(key, N);

        // Step 2: Send write to all replicas (async)
        List<CompletableFuture<WriteResponse>> futures = new ArrayList<>();
        for (Node node : replicas) {
            CompletableFuture<WriteResponse> future =
                node.writeAsync(key, value, System.currentTimeMillis());
            futures.add(future);
        }

        // Step 3: Wait for W responses
        int successCount = 0;
        for (CompletableFuture<WriteResponse> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);  // Timeout after 1s
                successCount++;
                if (successCount >= W) {
                    break;  // Got enough responses, can return
                }
            } catch (TimeoutException | ExecutionException e) {
                // This replica failed/timed out, try others
            }
        }

        if (successCount < W) {
            throw new InsufficientReplicasException(
                "Only " + successCount + " replicas responded, need " + W
            );
        }

        // Success! (even though some replicas might have failed)
    }

    public String read(String key) {
        List<Node> replicas = consistentHash.getReplicas(key, N);

        // Read from R replicas
        List<CompletableFuture<ReadResponse>> futures = new ArrayList<>();
        for (int i = 0; i < R && i < replicas.size(); i++) {
            futures.add(replicas.get(i).readAsync(key));
        }

        // Collect responses
        List<ReadResponse> responses = new ArrayList<>();
        for (CompletableFuture<ReadResponse> future : futures) {
            try {
                responses.add(future.get(1, TimeUnit.SECONDS));
            } catch (Exception e) {
                // This replica failed, but we might have enough from others
            }
        }

        if (responses.size() < R) {
            throw new InsufficientReplicasException();
        }

        // Pick latest version
        ReadResponse latest = responses.stream()
            .max(Comparator.comparing(ReadResponse::getTimestamp))
            .orElseThrow();

        // Trigger read repair if versions differ
        if (responses.stream().anyMatch(r -> r.getTimestamp() < latest.getTimestamp())) {
            readRepair(key, latest, replicas);
        }

        return latest.getValue();
    }
}
```

---

## Read Repair and Anti-Entropy

🎓 **PROFESSOR**: How stale replicas catch up:

### Read Repair (Lazy, On-Demand)

```
Scenario: Node C was down during write, now has stale data

Client reads from Nodes A, B, C:
- Node A: version=5, value="Alice"
- Node B: version=5, value="Alice"
- Node C: version=3, value="Bob"    ← Stale!

Client detects: C is behind

Read Repair Process:
1. Client returns "Alice" to application (latest version)
2. In background: Client writes version=5 to Node C
3. Node C now caught up

Pros: Lazy (only repairs when accessed)
Cons: Rarely-read data stays stale forever
```

### Anti-Entropy (Eager, Background)

```
Background process runs periodically:

Node A:                  Node B:
┌────────────────┐      ┌────────────────┐
│ key1: v5       │      │ key1: v5       │
│ key2: v3       │ ←──→ │ key2: v7       │  ← Diff!
│ key3: v2       │      │ key3: v1       │  ← Diff!
└────────────────┘      └────────────────┘

Anti-Entropy Process:
1. Compare Merkle trees (efficient summary of all keys)
2. Identify differences: key2, key3
3. Sync differences:
   - Send key2 (v7) from B to A
   - Send key3 (v2) from A to B

Result: Both nodes fully synced
```

🏗️ **ARCHITECT**: Cassandra's approach:

```python
class CassandraAntiEntropy:
    """
    Cassandra's background repair mechanisms
    """

    def read_repair(self, key, latest_version, replicas):
        """
        On-demand repair during read
        Configured per table:
        - read_repair_chance: 0.1 (10% of reads trigger repair)
        - dclocal_read_repair_chance: 1.0 (always within datacenter)
        """
        if random.random() < self.read_repair_chance:
            # Send latest version to all replicas
            for replica in replicas:
                replica.write_async(key, latest_version)

    def nodetool_repair(self):
        """
        Manual full repair (via nodetool repair command)
        Compares Merkle trees, syncs all differences
        """
        # Step 1: Build Merkle tree for each replica
        merkle_trees = {}
        for replica in self.all_replicas:
            merkle_trees[replica] = replica.build_merkle_tree()

        # Step 2: Compare trees pairwise
        for i, replica1 in enumerate(self.all_replicas):
            for replica2 in self.all_replicas[i+1:]:
                tree1 = merkle_trees[replica1]
                tree2 = merkle_trees[replica2]

                # Find differing ranges
                diff_ranges = tree1.diff(tree2)

                # Step 3: Stream differing data
                for range_key in diff_ranges:
                    data1 = replica1.get_range(range_key)
                    data2 = replica2.get_range(range_key)

                    # Merge using version vectors
                    merged = self.merge(data1, data2)

                    # Write merged back to both
                    replica1.put_range(range_key, merged)
                    replica2.put_range(range_key, merged)

    def hinted_handoff(self, failed_node, pending_writes):
        """
        When node is down, another node temporarily stores its writes
        """
        # Node A down during write
        # Node B stores hint: "This write is for Node A"

        # When Node A comes back online:
        # Node B: "Hey A, here are writes you missed"
        # A applies all hinted writes

        for write in pending_writes:
            failed_node.write(write.key, write.value)
```

**Merkle Tree Explanation:**

```python
class MerkleTree:
    """
    Efficient way to detect differences between datasets
    """

    def __init__(self, data_ranges):
        """
        Build Merkle tree:

                    Root Hash
                   /         \
              Hash1            Hash2
             /    \           /    \
          H1-1   H1-2      H2-1   H2-2
          /  \   /  \      /  \   /  \
         [data ranges...]

        Leaf = hash of data range
        Parent = hash of children
        """
        self.root = self.build_tree(data_ranges)

    def diff(self, other_tree):
        """
        Compare two Merkle trees:
        - If root hashes match → Trees identical (no sync needed!)
        - If different → Recursively check children
        - Only sync differing ranges

        Efficiency: O(log N) comparisons vs O(N) for naive approach
        """
        return self._diff_recursive(self.root, other_tree.root)

    def _diff_recursive(self, node1, node2):
        if node1.hash == node2.hash:
            return []  # Subtrees identical

        if node1.is_leaf():
            return [node1.range]  # This range differs

        # Recursively check children
        diffs = []
        diffs += self._diff_recursive(node1.left, node2.left)
        diffs += self._diff_recursive(node1.right, node2.right)
        return diffs
```

---

## Consistency Levels in Practice

🎓 **PROFESSOR**: Tunable consistency through quorum configuration:

### Common Configurations

```
┌────────────────────────────────────────────────────────────┐
│ N=3 (replication factor)                                   │
├────────────────────────────────────────────────────────────┤
│ Config      │ W │ R │ W+R>N? │ Consistency  │ Availability │
├─────────────┼───┼───┼────────┼──────────────┼──────────────┤
│ Strong      │ 3 │ 3 │  Yes   │ Linearizable │ Low          │
│ Quorum      │ 2 │ 2 │  Yes   │ Read latest  │ Medium       │
│ One         │ 1 │ 1 │  No    │ Eventual     │ High         │
│ Write-heavy │ 1 │ 3 │  Yes   │ Read latest  │ High writes  │
│ Read-heavy  │ 3 │ 1 │  Yes   │ Read latest  │ High reads   │
└─────────────┴───┴───┴────────┴──────────────┴──────────────┘
```

🏗️ **ARCHITECT**: Cassandra consistency level API:

```java
// Cassandra allows per-query consistency level

Session session = cluster.connect("my_keyspace");

// Strong consistency (ALL replicas must respond)
Statement statement = new SimpleStatement(
    "SELECT * FROM users WHERE id = ?"
).setConsistencyLevel(ConsistencyLevel.ALL);
ResultSet rs = session.execute(statement);

// Quorum (majority must respond)
statement.setConsistencyLevel(ConsistencyLevel.QUORUM);

// One (fastest, but may be stale)
statement.setConsistencyLevel(ConsistencyLevel.ONE);

// Local quorum (majority within datacenter)
statement.setConsistencyLevel(ConsistencyLevel.LOCAL_QUORUM);
```

### Real-World Example: Facebook's Cassandra Usage

```python
class FacebookCassandraUsage:
    """
    How Facebook uses Cassandra for Instagram
    """

    def write_user_post(self, post):
        """
        Writing a post:
        - W = QUORUM (2 out of 3)
        - Ensures durability (post won't be lost)
        - But can succeed even if 1 replica down
        """
        session.execute(
            "INSERT INTO posts (user_id, post_id, content) VALUES (?, ?, ?)",
            consistency_level=ConsistencyLevel.QUORUM
        )

    def read_user_feed(self, user_id):
        """
        Reading feed:
        - R = ONE (fastest replica)
        - May be slightly stale (acceptable for feed)
        - Optimizes for low latency
        """
        return session.execute(
            "SELECT * FROM posts WHERE user_id = ? LIMIT 20",
            consistency_level=ConsistencyLevel.ONE
        )

    def read_user_profile(self, user_id):
        """
        Reading user's own profile:
        - R = QUORUM (ensure fresh data)
        - User expects to see their latest changes
        """
        return session.execute(
            "SELECT * FROM users WHERE id = ?",
            consistency_level=ConsistencyLevel.QUORUM
        )
```

---

## Sloppy Quorums and Hinted Handoff

🎓 **PROFESSOR**: Increasing availability at the cost of consistency:

### Strict Quorum

```
Key "user:123" maps to nodes: A, B, C (via consistent hashing)
Config: N=3, W=2, R=2

If Node B is down:
- Write to A ✓, B ✗, C ✓ → 2 successes, write OK
- But we ONLY write to designated replicas (A, B, C)
```

### Sloppy Quorum

```
Key "user:123" maps to nodes: A, B, C
Node B is down

Instead of failing, write to Node D (temporary substitute):
- Write to A ✓
- Write to B ✗ (down)
- Write to C ✓
- Write to D ✓ (with hint: "This is for B")

Result: W=3 achieved (A, C, D)

When B comes back:
- D forwards its writes to B (hinted handoff)
- B catches up
- D discards the hints
```

🏗️ **ARCHITECT**: Implementation:

```python
class SloppyQuorum:
    """
    DynamoDB/Cassandra style sloppy quorums
    """

    def write(self, key, value):
        # Determine N primary replicas
        primary_replicas = self.consistent_hash.get_replicas(key, self.N)

        # Try to write to primaries
        successful_writes = []
        for replica in primary_replicas:
            if replica.is_available():
                replica.write(key, value)
                successful_writes.append(replica)

        # If we don't have W successes, use backups
        if len(successful_writes) < self.W:
            backup_replicas = self.consistent_hash.get_next_replicas(
                key,
                count=self.W - len(successful_writes)
            )

            for backup in backup_replicas:
                # Write with hint
                backup.write_with_hint(
                    key,
                    value,
                    hint="intended_for: " + primary_replicas[0].id
                )
                successful_writes.append(backup)

        if len(successful_writes) < self.W:
            raise InsufficientReplicasException()

    def hinted_handoff_process(self):
        """
        Background process: Forward hints when primaries recover
        """
        while True:
            # Check for hints
            hints = self.get_pending_hints()

            for hint in hints:
                primary_node = self.get_node(hint.intended_for)

                if primary_node.is_available():
                    # Primary is back online, forward the write
                    primary_node.write(hint.key, hint.value)

                    # Delete hint
                    self.delete_hint(hint)

            time.sleep(60)  # Check every minute
```

**Trade-off:**

```
Strict Quorum:
✓ Strong consistency guarantee (W+R>N ensures overlap)
✗ Lower availability (write fails if can't reach N nodes)

Sloppy Quorum:
✓ Higher availability (can write to backup nodes)
✗ Weaker consistency (reads might not see latest write)
✗ "Last hop" problem: Hints might be lost

Use sloppy quorum when:
- Availability > Consistency
- Temporary inconsistency acceptable
- Eventually consistent is good enough
```

---

## Real-World Leaderless Systems

🏗️ **ARCHITECT**: Production systems and their choices:

### Amazon DynamoDB

```javascript
// DynamoDB: Leaderless with tunable consistency

const AWS = require('aws-sdk');
const docClient = new AWS.DynamoDB.DocumentClient();

// Eventual consistent read (default, cheapest)
docClient.get({
    TableName: 'Users',
    Key: {userId: '123'},
    ConsistentRead: false  // Read from any replica
});

// Strongly consistent read (more expensive)
docClient.get({
    TableName: 'Users',
    Key: {userId: '123'},
    ConsistentRead: true  // Read quorum
});

// Writes always use quorum (W=majority)
docClient.put({
    TableName: 'Users',
    Item: {userId: '123', name: 'Alice'}
});
```

### Apache Cassandra

```sql
-- Cassandra: Fully tunable consistency

-- Create keyspace with replication
CREATE KEYSPACE instagram
WITH REPLICATION = {
    'class': 'NetworkTopologyStrategy',
    'us-east': 3,   -- 3 replicas in US East
    'eu-west': 3    -- 3 replicas in EU West
};

-- Write with quorum
INSERT INTO posts (user_id, post_id, content)
VALUES ('alice', uuid(), 'Hello World')
USING CONSISTENCY QUORUM;

-- Read with local quorum (majority within datacenter)
SELECT * FROM posts WHERE user_id = 'alice'
USING CONSISTENCY LOCAL_QUORUM;
```

### Riak

```erlang
%% Riak: Designed from ground-up for leaderless

%% Configure bucket with N=3, allow sloppy quorum
riakc_pb_socket:set_bucket(Pid, <<"users">>, [
    {n_val, 3},              % 3 replicas
    {allow_mult, true},      % Allow siblings (multi-value)
    {dvv_enabled, true}      % Use version vectors
]).

%% Write with W=2
riakc_pb_socket:put(Pid, Obj, [{w, 2}, {dw, 2}]).
%% w=2: Wait for 2 write ACKs
%% dw=2: Wait for 2 durable writes (to disk)

%% Read with R=2, PR=1
riakc_pb_socket:get(Pid, <<"users">>, <<"alice">>, [
    {r, 2},   % Read from 2 replicas
    {pr, 1}   % At least 1 must be primary replica
]).
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### When to Use Leaderless Replication

```
✓ Use Leaderless IF:
  - High availability is critical (99.99%+)
  - Multi-datacenter deployment
  - Can tolerate eventual consistency
  - Write-heavy workload (no single bottleneck)
  - Examples: Shopping carts, sessions, user preferences

✗ Avoid Leaderless IF:
  - Need strong consistency (bank transfers)
  - Single datacenter (leader-based simpler)
  - Read-heavy workload (read replicas sufficient)
  - Small scale (complexity not justified)
```

### Design Checklist

```
1. Choose Quorum Values
   ☐ N (replication factor): Usually 3 or 5
   ☐ W (write quorum): Usually N/2 + 1 (majority)
   ☐ R (read quorum): Usually N/2 + 1 (majority)
   ☐ Ensure W + R > N for consistency

2. Consistency Level Strategy
   ☐ Critical operations: QUORUM or ALL
   ☐ Normal operations: QUORUM
   ☐ Analytics/bulk reads: ONE (eventual consistency)

3. Failure Handling
   ☐ Read repair: On or off?
   ☐ Anti-entropy frequency: Daily? Weekly?
   ☐ Hinted handoff: Enable for higher availability
   ☐ Sloppy quorum: Trade-off availability vs consistency

4. Monitoring
   ☐ Quorum failures (insufficient replicas)
   ☐ Hint backlog size
   ☐ Repair progress
   ☐ Consistency violations (version conflicts)
```

---

## 🧠 MIND MAP: LEADERLESS REPLICATION

```
         LEADERLESS REPLICATION
                  |
     ┌────────────┼────────────┐
     ↓            ↓             ↓
  WRITES       READS       REPAIR
     |            |             |
  ┌──┼──┐     ┌──┼──┐      ┌───┼───┐
  ↓  ↓  ↓     ↓  ↓  ↓      ↓   ↓   ↓
 W  Sloppy  R  Latest   Read Anti Hinted
 Quorum Quorum Version  Repair Entropy Handoff
  |            |            |      |      |
 N/2+1    Temporary    Lazy   Merkle Temporary
          Replicas     Sync   Trees  Storage
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **Leaderless = Committee Vote 🗳️**
- No chairman, all members equal
- Vote on every decision (quorum)
- Majority wins (W=majority, R=majority)
- If some members absent, still proceed (availability)

### 2. **Quorum = Jury Verdict ⚖️**
- Need majority to agree (not unanimous)
- If 2 out of 3 agree, decision made
- Faster than waiting for everyone
- Occasional dissenter doesn't block progress

### 3. **Read Repair = Gossip Correction 📢**
- You hear rumor from 3 friends
- 2 say one thing, 1 says another
- You believe the 2 (latest version)
- You correct the 1 (read repair)

### 4. **Hinted Handoff = Post Office Hold 📬**
- Friend on vacation (node down)
- Post office holds their mail (hints)
- Friend returns, gets all mail (handoff)
- Mail delivered, just delayed

### 5. **Sloppy Quorum = Substitute Teacher 👨‍🏫**
- Regular teacher sick (primary node down)
- Substitute fills in (backup node)
- Takes notes for regular teacher (hints)
- Regular teacher returns, gets caught up

---

## 🔑 Key Takeaways

1. **Leaderless eliminates single point of failure**
   - No designated leader, all nodes equal
   - No failover needed
   - Always available (as long as quorum reachable)

2. **Quorum is the foundation**
   - W + R > N guarantees consistency
   - Typical: N=3, W=2, R=2
   - Tunable per operation

3. **Sloppy quorum trades consistency for availability**
   - Write to backup nodes if primaries down
   - Higher availability, weaker guarantees
   - Use for non-critical data

4. **Repair mechanisms are essential**
   - Read repair: Lazy, on-demand
   - Anti-entropy: Eager, comprehensive
   - Hinted handoff: Catch up after downtime

5. **Consistency is tunable**
   - Per-query consistency level
   - Critical ops: ALL or QUORUM
   - Bulk ops: ONE (eventual)

6. **Best for AP systems (CAP)**
   - Availability + Partition tolerance
   - Eventual consistency
   - Shopping carts, sessions, caches

7. **Complexity is real**
   - More moving parts than leader-based
   - Need good monitoring
   - Conflict resolution required
