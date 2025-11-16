# Database Scaling

Scaling databases is critical for handling growth in data volume, traffic, and user load.

## Contents
- [Vertical vs Horizontal Scaling](#vertical-vs-horizontal-scaling)
- [Read Replicas](#read-replicas)
- [Database Sharding](#database-sharding)
- [Partitioning Strategies](#partitioning-strategies)
- [Trade-offs and Considerations](#trade-offs-and-considerations)

## Vertical vs Horizontal Scaling

### Vertical Scaling (Scale Up)
**Add more resources to existing server** (CPU, RAM, Disk)

**Pros:**
- Simple to implement
- No application changes needed
- Maintains ACID properties
- No data distribution complexity

**Cons:**
- Hardware limits (can't scale indefinitely)
- Single point of failure
- Expensive at scale
- Downtime during upgrades

**When to Use:**
- Early-stage applications
- Small to medium workloads
- Strong consistency requirements

### Horizontal Scaling (Scale Out)
**Add more servers to distribute load**

**Pros:**
- Nearly unlimited scaling potential
- Better fault tolerance
- Cost-effective (commodity hardware)
- No downtime for scaling

**Cons:**
- Complex implementation
- Data consistency challenges
- Requires application changes
- Network overhead

**When to Use:**
- High-growth applications
- Global distribution needed
- Cost optimization at scale

## Read Replicas

Separate read and write workloads by replicating data to read-only copies.

### Architecture Pattern

```
       ┌─────────────┐
       │   Primary   │ ◄── Writes
       │  (Master)   │
       └──────┬──────┘
              │
       ┌──────┴───────────┬───────────┐
       │                  │           │
       ▼                  ▼           ▼
┌──────────┐       ┌──────────┐ ┌──────────┐
│ Replica  │       │ Replica  │ │ Replica  │
│   (1)    │       │   (2)    │ │   (3)    │
└──────────┘       └──────────┘ └──────────┘
     ▲                  ▲           ▲
     └──────────────────┴───────────┘
              Reads
```

### Replication Types

#### 1. Synchronous Replication
- Primary waits for replica acknowledgment before committing
- **Strong consistency**
- Higher latency
- Used by: PostgreSQL synchronous standby

#### 2. Asynchronous Replication
- Primary commits immediately
- **Eventual consistency**
- Lower latency
- Risk of data loss on primary failure
- Used by: MySQL default replication, MongoDB

#### 3. Semi-Synchronous Replication
- Wait for at least one replica
- Balance between consistency and performance
- Used by: MySQL semi-sync plugin

### Replication Lag Challenges

**Problem:** Replicas may be behind primary

**Solutions:**
- Read from primary for critical data
- Session stickiness (same user → same replica)
- Implement read-your-writes consistency
- Monitor replication lag metrics

### Load Balancing Reads

```
Application → Load Balancer → [Replica 1, Replica 2, Replica 3]
```

**Strategies:**
- Round-robin
- Least connections
- Geographic proximity
- Health check-based routing

## Database Sharding

Sharding distributes data across multiple databases (shards).

### Sharding Strategies

#### 1. Range-Based Sharding (Key Range)

```
Users 1-10M     → Shard 1
Users 10M-20M   → Shard 2
Users 20M-30M   → Shard 3
```

**Pros:**
- Simple to implement
- Range queries efficient
- Easy to add new shards

**Cons:**
- Uneven data distribution (hotspots)
- Manual rebalancing needed

**Use Cases:** Time-series data, ordered datasets

#### 2. Hash-Based Sharding

```
hash(user_id) % num_shards → Shard number
```

**Pros:**
- Even data distribution
- Automatic balancing

**Cons:**
- Range queries difficult
- Resharding is complex
- Can't easily add/remove shards

**Use Cases:** User data, session storage

#### 3. Consistent Hashing

Uses hash ring to minimize resharding when adding/removing nodes.

```
         ┌────── Shard 3 ──────┐
         │                     │
    Shard 2                 Shard 4
         │                     │
         └────── Shard 1 ──────┘
```

**Pros:**
- Minimal data movement on resharding
- Even distribution with virtual nodes

**Cons:**
- Complex implementation
- Range queries not supported

**Use Cases:** Distributed caching (Cassandra, DynamoDB)

#### 4. Directory-Based Sharding (Lookup)

Maintain a lookup table mapping keys to shards.

```
user_id → lookup_service → shard_location
```

**Pros:**
- Flexible shard assignment
- Easy to rebalance
- Supports any partitioning logic

**Cons:**
- Lookup service is a bottleneck
- Extra network hop
- Single point of failure

**Use Cases:** Multi-tenant applications

#### 5. Geographic Sharding

Partition by geographic region.

```
US users     → US Shard
EU users     → EU Shard
APAC users   → APAC Shard
```

**Pros:**
- Low latency (data locality)
- Regulatory compliance
- Fault isolation

**Cons:**
- Uneven distribution
- Cross-region queries complex

**Use Cases:** Global applications with regional data laws

### Sharding Challenges

#### 1. Cross-Shard Queries
**Problem:** JOINs across shards are expensive

**Solutions:**
- Denormalize data
- Application-level joins
- Use shard key in queries
- Avoid cross-shard transactions

#### 2. Shard Key Selection
**Critical decision** - difficult to change later

**Good Shard Key Properties:**
- High cardinality
- Even distribution
- Commonly queried field
- Minimal cross-shard queries

**Examples:**
- ✅ user_id (for user-centric apps)
- ✅ tenant_id (for multi-tenant SaaS)
- ❌ country (low cardinality)
- ❌ timestamp (creates hotspots)

#### 3. Rebalancing Shards
**When needed:** Uneven load, adding capacity

**Strategies:**
- Live migration (minimal downtime)
- Read from old, write to new
- Consistent hashing (automatic)

## Partitioning Strategies

Partitioning divides a table within a single database.

### Horizontal Partitioning (Row-based)

Split table rows across multiple partitions.

```sql
-- By date range
orders_2023_q1
orders_2023_q2
orders_2023_q3
orders_2023_q4
```

**Benefits:**
- Faster queries (partition pruning)
- Easier archival
- Parallel query execution

### Vertical Partitioning (Column-based)

Split table columns into separate tables.

```sql
-- Frequently accessed
users (id, name, email)

-- Infrequently accessed
user_profiles (id, bio, preferences, settings)
```

**Benefits:**
- Reduce I/O for common queries
- Better cache utilization
- Optimize for different access patterns

## Trade-offs and Considerations

### When to Scale

**Vertical Scaling First:**
- Simplicity is valuable
- Budget allows
- Under hardware limits

**Horizontal Scaling When:**
- Hit hardware limits
- Global distribution needed
- High availability critical
- Cost optimization needed

### Consistency Trade-offs

| Approach | Consistency | Performance | Complexity |
|----------|------------|-------------|------------|
| Single DB | Strong | Limited | Low |
| Read Replicas | Eventual | High reads | Medium |
| Sharding | Varies | Very High | High |

### Monitoring Metrics

**Essential Metrics:**
- Query response time
- Throughput (QPS)
- Replication lag
- Shard distribution (hotspots)
- Connection pool utilization
- Disk I/O and CPU

## Best Practices

1. **Plan for Growth:** Design sharding strategy early
2. **Choose Shard Key Carefully:** Hard to change later
3. **Monitor Replication Lag:** Alert on excessive lag
4. **Implement Connection Pooling:** Reduce connection overhead
5. **Test Failover Scenarios:** Validate replica promotion
6. **Use Database Proxies:** Abstract complexity (ProxySQL, Vitess)
7. **Document Shard Mapping:** Clear operational procedures
8. **Gradual Migration:** Test with subset before full rollout

## Real-World Examples

**Instagram:**
- PostgreSQL sharding by user_id
- Thousands of shards
- Geographic distribution

**Uber:**
- Schemaless (MySQL sharding layer)
- Ring-based sharding
- Global replication

**Discord:**
- Cassandra for messages (consistent hashing)
- PostgreSQL for user data (sharded)

**Netflix:**
- Cassandra for viewing history
- Multi-region active-active
- Eventual consistency

## Further Reading

- [Vitess](https://vitess.io/) - MySQL sharding solution
- [Citus](https://www.citusdata.com/) - PostgreSQL sharding extension
- [MongoDB Sharding](https://docs.mongodb.com/manual/sharding/)
- [AWS Aurora](https://aws.amazon.com/rds/aurora/) - Read replica architecture
