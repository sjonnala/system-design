# Partitioning & Sharding

## Why Partition Data?

**Scalability**: Single machine has limits:
- Storage capacity
- CPU/memory
- Network bandwidth

**Solution**: Split data across multiple machines (shards/partitions)

**Goals**:
- Distribute data evenly
- Distribute query load evenly
- Support efficient queries

---

## Partitioning Strategies

### 1. Key-Range Partitioning

**Concept**: Partition by ranges of keys

```
Shard 1: A-G
Shard 2: H-N
Shard 3: O-Z

Example:
"Alice" → Shard 1
"John" → Shard 2
"Zara" → Shard 3
```

**Pros**:
- Range queries efficient (scan one shard)
- Easy to add shards (split ranges)
- Simple to understand

**Cons**:
- Hot spots if data not evenly distributed
- Sequential writes go to same shard
- Requires rebalancing as data grows

**Use Cases**:
- Time-series data (partition by date)
- Alphabetical data with even distribution
- HBase, Bigtable

**Example: Time-Series**:
```
Shard 1: 2025-01-01 to 2025-03-31
Shard 2: 2025-04-01 to 2025-06-30
Shard 3: 2025-07-01 to 2025-09-30
Shard 4: 2025-10-01 to 2025-12-31

Query "events in Q2" → Shard 2 only
```

### 2. Hash Partitioning

**Concept**: Hash the key, assign to shard based on hash

```
hash(key) % num_shards = shard_id

Example (3 shards):
hash("Alice") % 3 = 1 → Shard 1
hash("John") % 3 = 2 → Shard 2
hash("Zara") % 3 = 0 → Shard 0
```

**Pros**:
- Even distribution
- No hotspots
- Automatic load balancing

**Cons**:
- Range queries require all shards
- Rebalancing difficult (changing num_shards rehashes everything)
- Lost key locality

**Use Cases**:
- User IDs
- Random access patterns
- Cassandra, DynamoDB, MongoDB

### 3. Consistent Hashing

**Problem with Simple Hashing**:
```
3 shards: hash(key) % 3
Add 4th shard: hash(key) % 4 → Most keys rehashed!
```

**Consistent Hashing Solution**:
```
Hash ring: 0 to 2^32

Nodes placed on ring:
Node A: position 100
Node B: position 500
Node C: position 900

Key assignment:
Key X hashed to 250 → Goes to next node (Node B at 500)
Key Y hashed to 600 → Goes to next node (Node C at 900)
Key Z hashed to 950 → Wraps to Node A at 100
```

**Adding Node**:
```
Add Node D at position 700:
- Only keys between 600-700 move (from C to D)
- Other keys unaffected
- Minimal data movement
```

**Virtual Nodes**:
```
Problem: Uneven distribution with few nodes

Solution: Each physical node gets multiple positions (virtual nodes)

Node A: positions [100, 300, 700]
Node B: positions [200, 500, 900]
Node C: positions [400, 600, 800]

Better distribution across ring
```

**Use Cases**:
- DynamoDB
- Cassandra
- Memcached
- Content Delivery Networks

### 4. Geo-Partitioning

**Concept**: Partition by geographic region

```
Shard 1 (US West): California, Oregon, Washington
Shard 2 (US East): New York, Virginia, Florida
Shard 3 (EU): UK, Germany, France
Shard 4 (Asia): Japan, Singapore, Australia
```

**Pros**:
- Low latency (data close to users)
- Data sovereignty compliance (GDPR)
- Natural partitioning

**Cons**:
- Uneven distribution (population differences)
- Cross-region queries expensive
- Complex routing

**Use Cases**:
- Multi-region applications
- Compliance requirements
- Gaming (regional servers)

---

## Partitioning Secondary Indexes

**Problem**: How to query by non-partition key?

### Document-Based Partitioning (Local Index)

```
Each shard maintains its own index:

Shard 1 (users A-M):
  user_id -> document
  email_index -> user_id

Shard 2 (users N-Z):
  user_id -> document
  email_index -> user_id

Query by email:
- Must query ALL shards (scatter-gather)
- Aggregate results
```

**Pros**: Writes contained to one shard
**Cons**: Reads query all shards

**Used by**: MongoDB, Elasticsearch

### Term-Based Partitioning (Global Index)

```
Partition the index itself:

Index Shard A (emails a-m):
  email -> user_id -> partition

Index Shard B (emails n-z):
  email -> user_id -> partition

Query by email:
- Query one index shard
- Then query data partition
```

**Pros**: Efficient reads (query one index shard)
**Cons**: Writes hit multiple shards (data + index)

**Used by**: DynamoDB GSI, Cassandra

---

## Rebalancing Partitions

**When to Rebalance**:
- Add more nodes (scale out)
- Remove nodes (failure or scale down)
- Uneven data distribution (hotspots)

### Strategies

**1. Fixed Number of Partitions**:
```
Create many partitions upfront:
10 nodes, 1000 partitions → 100 partitions per node

Add node:
Move 100 partitions from existing nodes to new node

Pro: Simple
Con: Fixed partition size
Used by: Riak, Elasticsearch
```

**2. Dynamic Partitioning**:
```
Split partitions when too large:
Partition > 10GB → Split into 2 partitions

Merge partitions when too small:
Partition < 1GB → Merge with neighbor

Pro: Adaptive to data volume
Con: Complex logic
Used by: HBase, MongoDB
```

**3. Proportional Partitioning**:
```
Fixed number of partitions per node:
Add node → Create new partitions on that node

Pro: Scales with cluster size
Con: Uneven partition sizes
Used by: Cassandra
```

### Rebalancing Process

```
1. Start moving data to new node
2. Keep accepting writes to both old and new locations
3. Complete data transfer
4. Switch all reads to new node
5. Remove data from old node
```

**Automatic vs Manual**:
- **Automatic**: System rebalances without intervention (Cassandra)
- **Manual**: Operator triggers rebalancing (safer, more control)

---

## Handling Hotspots

**Problem**: Some keys accessed much more than others

**Examples**:
- Celebrity's Twitter account
- Popular product on sale
- Viral video

**Solutions**:

**1. Application-Level Sharding**:
```
Hot key: "celebrity_123"

Split into:
  "celebrity_123_0"
  "celebrity_123_1"
  "celebrity_123_2"

Application randomly assigns suffix
```

**2. Caching**:
```
Hot data → Cache (Redis/Memcached)
Reduces load on database partitions
```

**3. Rate Limiting**:
```
Limit writes to hot keys
Prevent overwhelming single partition
```

**4. Data Duplication**:
```
Replicate hot data across multiple partitions
Distribute read load
```

---

## Routing Requests

**How does client know which partition to query?**

### 1. Client-Side Partitioning

```
Client has routing logic:
- Knows partition scheme
- Calculates target partition
- Sends request directly

Pro: Low latency (no extra hop)
Con: Client complexity, routing changes break clients
```

### 2. Proxy/Router Layer

```
Client → [Proxy] → determines partition → [Shard]

Pro: Client simple, easy to change routing
Con: Extra network hop, proxy can be bottleneck
Used by: MongoDB (mongos)
```

### 3. Coordination Service

```
Client → [Coordinator] → (ZooKeeper/etcd) → [Shard]

Coordinator tracks: Which partition is on which node

Pro: Dynamic routing, handle failures
Con: Extra dependency
Used by: HBase (ZooKeeper), Kafka
```

---

## Interview Q&A

**Q: "How would you partition data for Instagram?"**

**Answer**:
```
User Data: Hash partition by user_id
- Even distribution
- User data accessed together
- Shards: hash(user_id) % num_shards

Posts: Hash partition by user_id
- User's posts on same shard as profile
- Efficient for "fetch user's posts"

Feed: Timeline partition by follower_user_id
- Pre-computed feed stored with user
- Efficient feed reads

Photos: Geo-partition + CDN
- Store in region closest to user
- CDN for low latency delivery

Secondary Indexes:
- Username -> user_id: Global index (range partitioned)
- Hashtags -> posts: Local indexes (scatter-gather acceptable)

Trade-offs:
- Hash partitioning loses range query capability
- But Instagram doesn't need range queries on user_id
- Geo-partitioning for media reduces latency
- Feed denormalization for read performance
```

**Q: "How do you handle rebalancing without downtime?"**

**Answer**:
```
Use dual-write strategy:

1. Start data migration:
   - Copy data from Shard A to new Shard B
   - Keep Shard A serving requests

2. Dual-write phase:
   - New writes go to BOTH A and B
   - Reads still from A
   - Continue background migration

3. Switch reads:
   - Once migration complete
   - Start reading from B
   - Keep dual-writing temporarily

4. Cleanup:
   - Stop writing to A
   - Verify B has all data
   - Decommission A

This ensures:
- Zero downtime
- No data loss
- Rollback possible
- Gradual migration

Example: Moving user_id range 1000-2000 from Node1 to Node2
```

**Q: "How does Cassandra handle partitioning?"**

**Answer**:
```
Cassandra uses consistent hashing with virtual nodes:

1. Partitioning:
   - Hash partition key → token
   - Token determines owning node(s)

2. Virtual Nodes (vnodes):
   - Each physical node has 256 virtual nodes
   - Better distribution
   - Easier rebalancing

3. Replication:
   - Data replicated to N nodes
   - Next N-1 nodes on ring
   - Configurable per keyspace

4. Consistency:
   - Tunable (ONE, QUORUM, ALL)
   - Can choose per query

Example:
Partition key: user_id=123
hash(123) = token 5678
Node owning token 5678 is primary
Next 2 nodes are replicas (if RF=3)

Adding new node:
- Gets 256 vnodes scattered across ring
- Gradually takes data from neighbors
- Even distribution maintained
```

---

## Key Takeaways

1. **Partition for Scale**: Single machine limits require sharding
2. **Choose Strategy**: Hash (even), Range (locality), Consistent Hash (dynamic)
3. **Secondary Indexes Hard**: Trade-off: local (write-optimized) vs global (read-optimized)
4. **Rebalancing Critical**: Plan for adding/removing nodes smoothly
5. **Hotspots Happen**: Monitor and have mitigation strategies

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 6
- Cassandra Architecture
- DynamoDB Paper
- MongoDB Sharding Guide
- Google Bigtable Paper
