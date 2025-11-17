# Database Partitioning & Sharding Deep Dive

## Contents

- [Database Partitioning & Sharding Deep Dive](#database-partitioning--sharding-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Partitioning Strategies](#partitioning-strategies)
    - [Sharding Architecture](#sharding-architecture)
    - [Rebalancing & Data Migration](#rebalancing--data-migration)
    - [Query Routing & Coordination](#query-routing--coordination)
    - [Failure Modes & Edge Cases](#failure-modes--edge-cases)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Partitioning Strategy (RADIO: Data-Model)](#3-partitioning-strategy-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: PARTITIONING & SHARDING CONCEPTS](#mind-map-partitioning--sharding-concepts)

## Core Mental Model

```text
Partitioning = Splitting data into smaller pieces
Sharding = Partitioning data across multiple machines

Why Partition?
--------------
1. Single machine limits:
   - Storage: Disk capacity maxes out
   - Performance: CPU/Memory bottlenecks
   - Scalability: Vertical scaling expensive

2. Solution: Horizontal Scaling
   - Distribute data across multiple nodes
   - Scale by adding more machines
   - Linear scalability (ideally)
```

**The Fundamental Trade-off:**
```
┌────────────────────────────────────────────────────┐
│ Single Database          vs    Sharded Database    │
│                                                    │
│ ✅ Simple queries              ❌ Complex queries  │
│ ✅ ACID transactions           ❌ Distributed txn  │
│ ✅ Easy operations             ❌ Operational cost │
│ ❌ Limited scale               ✅ Infinite scale   │
│ ❌ Single point of failure     ✅ Fault tolerant   │
└────────────────────────────────────────────────────┘
```

**Mathematical Foundation:**
```
Query Performance = f(Data Size, Index Size, Network Latency)

Single Node:
- Query Time: O(log N) where N = total dataset
- N = 1TB → log(1TB) lookups

Sharded (M shards):
- Data per shard: N/M
- Query Time: O(log(N/M)) + network
- If M = 10, data per shard = 100GB
- Faster local queries, but coordination overhead
```

**Visual Model:**
```text
BEFORE SHARDING: Single Database
┌─────────────────────────────────┐
│     Single Database Server      │
│                                 │
│  ┌──────────────────────────┐  │
│  │  Users: 1B rows (1TB)    │  │
│  │  Orders: 10B rows (5TB)  │  │
│  │  Products: 100M rows     │  │
│  └──────────────────────────┘  │
│                                 │
│  Storage: 6TB                   │
│  CPU: 100% ← Bottleneck! 🔥     │
│  Memory: 512GB                  │
└─────────────────────────────────┘

AFTER SHARDING: Distributed Database
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│   Shard 0    │  │   Shard 1    │  │   Shard 2    │
│              │  │              │  │              │
│ Users 0-333M │  │ Users 334-   │  │ Users 667-1B │
│ Orders(U0)   │  │ 666M         │  │ Orders(U2)   │
│              │  │ Orders(U1)   │  │              │
│ Storage: 2TB │  │ Storage: 2TB │  │ Storage: 2TB │
│ CPU: 33%     │  │ CPU: 33%     │  │ CPU: 33%     │
└──────────────┘  └──────────────┘  └──────────────┘
      ↑                  ↑                  ↑
      └──────────────────┴──────────────────┘
                  Query Router
```

**Real-World Implementation:**
```java
/**
 * Core abstraction for sharding
 */
public interface ShardingStrategy {
    /**
     * Determine which shard(s) a key belongs to
     * @param key The sharding key (user_id, tenant_id, etc.)
     * @return Shard identifier(s)
     */
    List<ShardId> getShards(String key);

    /**
     * Get all shards (for scatter-gather queries)
     */
    List<ShardId> getAllShards();

    /**
     * Add new shard and rebalance
     */
    void addShard(ShardId newShard);

    /**
     * Remove shard and migrate data
     */
    void removeShard(ShardId oldShard);
}

/**
 * Hash-based sharding (most common)
 */
public class HashShardingStrategy implements ShardingStrategy {

    private final int numShards;
    private final HashFunction hashFunction;

    public HashShardingStrategy(int numShards) {
        this.numShards = numShards;
        this.hashFunction = Hashing.murmur3_128();
    }

    @Override
    public List<ShardId> getShards(String key) {
        long hash = hashFunction.hashString(key, StandardCharsets.UTF_8)
                               .asLong();
        int shardIndex = (int) Math.abs(hash % numShards);
        return List.of(new ShardId(shardIndex));
    }

    @Override
    public List<ShardId> getAllShards() {
        return IntStream.range(0, numShards)
                       .mapToObj(ShardId::new)
                       .collect(Collectors.toList());
    }

    @Override
    public void addShard(ShardId newShard) {
        // Problem: changing numShards causes massive data movement!
        // Solution: Use consistent hashing instead
        throw new UnsupportedOperationException(
            "Adding shards requires data rebalancing. " +
            "Consider using ConsistentHashShardingStrategy"
        );
    }

    @Override
    public void removeShard(ShardId oldShard) {
        throw new UnsupportedOperationException(
            "Removing shards requires data migration"
        );
    }
}
```

**Key Insight**: **Choose the right sharding key**. This decision is hard to change later!

---

## Partitioning Strategies

🎓 **PROFESSOR**: There are **three fundamental partitioning approaches**:

### 1. **Horizontal Partitioning (Sharding)**

Split rows across multiple tables/databases:

```sql
-- Original table: users (1B rows)
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(200),
    created_at TIMESTAMP
);

-- After horizontal partitioning:

-- Shard 0: users_shard_0
CREATE TABLE users_shard_0 (
    user_id BIGINT PRIMARY KEY,  -- user_id % 3 == 0
    email VARCHAR(255),
    name VARCHAR(200),
    created_at TIMESTAMP
);

-- Shard 1: users_shard_1
CREATE TABLE users_shard_1 (
    user_id BIGINT PRIMARY KEY,  -- user_id % 3 == 1
    email VARCHAR(255),
    name VARCHAR(200),
    created_at TIMESTAMP
);

-- Shard 2: users_shard_2
CREATE TABLE users_shard_2 (
    user_id BIGINT PRIMARY KEY,  -- user_id % 3 == 2
    email VARCHAR(255),
    name VARCHAR(200),
    created_at TIMESTAMP
);
```

**Pros & Cons:**
```
✅ Each shard smaller → faster queries
✅ Can scale horizontally (add more shards)
✅ Fault isolation (one shard failure ≠ total failure)

❌ Complex queries across shards (JOINs)
❌ Distributed transactions difficult
❌ Rebalancing when adding shards
```

### 2. **Vertical Partitioning (Column-Based)**

Split columns across multiple tables:

```sql
-- Original table: users
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(200),
    bio TEXT,              -- Rarely accessed
    profile_image BLOB,    -- Large, rarely accessed
    preferences JSON,      -- Large, rarely accessed
    created_at TIMESTAMP
);

-- After vertical partitioning:

-- Hot columns (frequently accessed)
CREATE TABLE users_core (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255),
    name VARCHAR(200),
    created_at TIMESTAMP
);

-- Cold columns (rarely accessed)
CREATE TABLE users_extended (
    user_id BIGINT PRIMARY KEY,
    bio TEXT,
    profile_image BLOB,
    preferences JSON,
    FOREIGN KEY (user_id) REFERENCES users_core(user_id)
);
```

**Use Cases:**
```
✅ Separate hot/cold data
✅ Reduce row size → more rows per page
✅ Improve cache hit ratio
✅ Security: isolate sensitive columns

❌ Requires JOINs to reconstruct full row
❌ More complex application logic
```

### 3. **Range-Based Partitioning**

Partition by value ranges:

```sql
-- Partition orders by date
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATE NOT NULL,
    total_amount DECIMAL(10,2)
)
PARTITION BY RANGE (created_at) (
    PARTITION p_2023_q1 VALUES LESS THAN ('2023-04-01'),
    PARTITION p_2023_q2 VALUES LESS THAN ('2023-07-01'),
    PARTITION p_2023_q3 VALUES LESS THAN ('2023-10-01'),
    PARTITION p_2023_q4 VALUES LESS THAN ('2024-01-01'),
    PARTITION p_2024_q1 VALUES LESS THAN ('2024-04-01'),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Query optimizer scans only relevant partition!
SELECT * FROM orders
WHERE created_at BETWEEN '2023-07-01' AND '2023-09-30';
-- Only scans p_2023_q3 partition
```

**Range Partitioning Strategies:**

```java
public enum RangePartitionStrategy {

    /**
     * 1. DATE-BASED (Most common)
     * Use case: Time-series data, logs, events
     */
    DATE_BASED {
        @Override
        public String getPartition(Object value) {
            LocalDate date = (LocalDate) value;
            return "p_" + date.getYear() + "_q" + getQuarter(date);
        }

        private int getQuarter(LocalDate date) {
            return (date.getMonthValue() - 1) / 3 + 1;
        }
    },

    /**
     * 2. GEOGRAPHIC
     * Use case: Multi-region applications
     */
    GEOGRAPHIC {
        @Override
        public String getPartition(Object value) {
            String country = (String) value;
            if (US_COUNTRIES.contains(country)) return "shard_us";
            if (EU_COUNTRIES.contains(country)) return "shard_eu";
            if (APAC_COUNTRIES.contains(country)) return "shard_apac";
            return "shard_other";
        }
    },

    /**
     * 3. TENANT-BASED (SaaS applications)
     * Use case: Multi-tenant systems
     */
    TENANT_BASED {
        @Override
        public String getPartition(Object value) {
            Long tenantId = (Long) value;
            // Large tenants get dedicated shards
            if (LARGE_TENANTS.contains(tenantId)) {
                return "shard_tenant_" + tenantId;
            }
            // Small tenants share shards
            return "shard_shared_" + (tenantId % 10);
        }
    },

    /**
     * 4. SIZE-BASED
     * Use case: User tiers (free, premium, enterprise)
     */
    SIZE_BASED {
        @Override
        public String getPartition(Object value) {
            UserTier tier = (UserTier) value;
            return switch (tier) {
                case ENTERPRISE -> "shard_enterprise";  // Dedicated
                case PREMIUM -> "shard_premium";
                case FREE -> "shard_free";
            };
        }
    };

    public abstract String getPartition(Object value);
}
```

🏗️ **ARCHITECT**: **Choosing the right partitioning strategy**:

```python
class PartitioningDecisionTree:
    """
    Decision tree for selecting partitioning strategy
    """
    def recommend_strategy(self, workload: Workload) -> str:
        # 1. Check if data has natural time dimension
        if workload.is_time_series:
            # Time-based queries common?
            if workload.queries_by_time_range > 0.7:
                return "RANGE_BY_DATE"
            # Archive old data?
            if workload.has_data_retention_policy:
                return "RANGE_BY_DATE (easy to drop old partitions)"

        # 2. Check for geographic access patterns
        if workload.is_multi_region:
            if workload.queries_are_geo_local > 0.8:
                return "RANGE_BY_GEOGRAPHY (low latency)"

        # 3. Check for multi-tenancy
        if workload.is_multi_tenant:
            if workload.has_large_tenants:
                return "HYBRID: Dedicated shards for large tenants, " \
                       "hash-based for small tenants"

        # 4. Check for even distribution requirement
        if workload.requires_even_load_distribution:
            return "HASH_BASED (uniform distribution)"

        # 5. Default for general OLTP
        return "HASH_BASED (simple, predictable)"

    def detect_hot_spots(self, metrics: ShardMetrics) -> List[str]:
        """
        Detect uneven load distribution
        """
        avg_load = statistics.mean(metrics.load_per_shard)
        std_dev = statistics.stdev(metrics.load_per_shard)

        hot_shards = []
        for shard_id, load in enumerate(metrics.load_per_shard):
            if load > avg_load + 2 * std_dev:  # 2 sigma outlier
                hot_shards.append({
                    'shard_id': shard_id,
                    'load': load,
                    'avg_load': avg_load,
                    'recommendation': self._hot_shard_fix(shard_id, metrics)
                })

        return hot_shards

    def _hot_shard_fix(self, shard_id: int, metrics: ShardMetrics) -> str:
        # Analyze why shard is hot
        if metrics.has_celebrity_key(shard_id):
            return "Split celebrity key across multiple shards"
        if metrics.shard_size(shard_id) > 2 * metrics.avg_shard_size:
            return "Shard is too large, split into 2 shards"
        return "Add read replicas for hot shard"
```

**Interview talking point**: "Range partitioning is great for time-series data because you can easily drop old partitions. Hash partitioning gives better load distribution but makes range queries expensive."

---

## Sharding Architecture

🎓 **PROFESSOR**: Sharding introduces **three core challenges**:

```
┌─────────────────────────────────────────────────┐
│ 1. DATA PLACEMENT                               │
│    → Which shard owns which data?               │
│                                                 │
│ 2. QUERY ROUTING                                │
│    → How to find the right shard(s)?           │
│                                                 │
│ 3. REBALANCING                                  │
│    → How to add/remove shards?                 │
└─────────────────────────────────────────────────┘
```

### Sharding Key Selection

🏗️ **ARCHITECT**: **Most critical decision in sharding**:

```java
/**
 * Sharding key evaluation criteria
 */
public class ShardingKeyEvaluator {

    /**
     * Score a potential sharding key (0-100)
     */
    public int scoreShardingKey(String columnName, TableStats stats) {
        int score = 0;

        // 1. Cardinality (higher is better)
        double cardinality = stats.getCardinality(columnName);
        if (cardinality > 0.9) score += 30;  // Excellent
        else if (cardinality > 0.5) score += 20;  // Good
        else if (cardinality > 0.1) score += 10;  // Okay
        else score += 0;  // Poor (hot spot risk)

        // 2. Query patterns (present in WHERE clause?)
        double queryUsage = stats.getQueryUsageFrequency(columnName);
        if (queryUsage > 0.8) score += 25;  // Excellent
        else if (queryUsage > 0.5) score += 15;  // Good
        else score += 5;  // Poor (will need scatter-gather)

        // 3. Immutability (immutable is better)
        if (stats.isImmutable(columnName)) {
            score += 20;  // Never changes (no resharding needed)
        } else {
            score += 0;  // Mutable (resharding nightmare)
        }

        // 4. Even distribution
        double distributionUniformity = stats.getDistributionUniformity(columnName);
        if (distributionUniformity > 0.9) score += 15;  // Excellent
        else if (distributionUniformity > 0.7) score += 10;  // Good
        else score += 0;  // Poor (imbalanced shards)

        // 5. Co-location needs (bonus points)
        if (stats.benefitsFromCoLocation(columnName)) {
            score += 10;  // E.g., user_id keeps user + orders together
        }

        return score;
    }

    /**
     * Recommend sharding key
     */
    public String recommendShardingKey(List<String> candidates, TableStats stats) {
        return candidates.stream()
            .max(Comparator.comparing(col -> scoreShardingKey(col, stats)))
            .orElseThrow(() -> new IllegalArgumentException("No valid sharding key"));
    }
}
```

**Common Sharding Keys:**

```sql
-- E-commerce example

-- ✅ GOOD: Shard by user_id
-- Pros: Even distribution, queries by user_id common
-- Cons: Cross-user queries expensive (e.g., global leaderboard)
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,  -- SHARDING KEY
    created_at TIMESTAMP,
    total_amount DECIMAL(10,2)
) SHARD BY HASH(user_id);

-- ✅ GOOD: Shard by tenant_id (SaaS)
-- Pros: Perfect data isolation, tenant queries fast
-- Cons: Uneven shard sizes if tenant sizes vary
CREATE TABLE documents (
    doc_id BIGINT PRIMARY KEY,
    tenant_id BIGINT NOT NULL,  -- SHARDING KEY
    content TEXT,
    created_at TIMESTAMP
) SHARD BY HASH(tenant_id);

-- ❌ BAD: Shard by status
-- Pros: None
-- Cons: Low cardinality (only 3 values), massive hot spots
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT,
    status VARCHAR(20),  -- DON'T USE AS SHARDING KEY!
    created_at TIMESTAMP
);
-- 80% orders are 'completed' → all go to same shard!

-- ❌ BAD: Shard by created_at
-- Pros: Range queries fast
-- Cons: All new writes go to one shard (hot spot)
CREATE TABLE events (
    event_id BIGINT PRIMARY KEY,
    created_at TIMESTAMP,  -- DON'T USE AS SHARDING KEY!
    data JSON
);
-- All inserts go to "latest" shard → bottleneck!
```

### Shard Topology Patterns

```text
┌─────────────────────────────────────────────────────┐
│ PATTERN 1: Dedicated Sharding Layer                 │
│                                                     │
│         Application Servers                         │
│                 ↓                                   │
│         Shard Proxy/Router                          │
│      (Vitess, ProxySQL, pgpool)                     │
│         ↓         ↓         ↓                       │
│      Shard 0   Shard 1   Shard 2                    │
│                                                     │
│ Pros: Centralized routing, easy to manage           │
│ Cons: Proxy is single point of failure              │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ PATTERN 2: Client-Side Routing                      │
│                                                     │
│    App Server 1    App Server 2    App Server 3     │
│   [Shard Router]  [Shard Router]  [Shard Router]    │
│         ↓              ↓              ↓             │
│         └──────────────┼──────────────┘             │
│                        ↓                            │
│              Shard 0, 1, 2, ...                     │
│                                                     │
│ Pros: No proxy overhead, resilient                  │
│ Cons: All apps must implement routing               │
└─────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────┐
│ PATTERN 3: Coordinator-Based (Distributed)          │
│                                                     │
│         Application Servers                         │
│                 ↓                                   │
│         Metadata Service                            │
│      (ZooKeeper, etcd, Consul)                      │
│         ↓         ↓         ↓                       │
│      Shard 0   Shard 1   Shard 2                    │
│                                                     │
│ Pros: Dynamic shard discovery, flexible             │
│ Cons: Dependency on coordination service            │
└─────────────────────────────────────────────────────┘
```

**Production Implementation:**

```java
/**
 * Shard-aware data access layer
 */
@Repository
public class ShardedUserRepository {

    private final ShardingStrategy shardingStrategy;
    private final Map<ShardId, DataSource> shardDataSources;
    private final ShardCoordinator coordinator;

    /**
     * Single-shard operation (fast path)
     */
    public User findById(Long userId) {
        // 1. Determine shard
        ShardId shard = shardingStrategy.getShards(userId.toString()).get(0);

        // 2. Execute on specific shard
        JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
        return jdbc.queryForObject(
            "SELECT * FROM users WHERE user_id = ?",
            new Object[]{userId},
            new UserRowMapper()
        );
    }

    /**
     * Multi-shard operation (scatter-gather)
     */
    public List<User> findByEmail(String email) {
        // Email is NOT sharding key → must query all shards!

        List<ShardId> allShards = shardingStrategy.getAllShards();

        // Parallel scatter-gather
        List<CompletableFuture<List<User>>> futures = allShards.stream()
            .map(shard -> CompletableFuture.supplyAsync(() -> {
                JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
                return jdbc.query(
                    "SELECT * FROM users WHERE email = ?",
                    new Object[]{email},
                    new UserRowMapper()
                );
            }))
            .collect(Collectors.toList());

        // Gather results
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    /**
     * Cross-shard JOIN (expensive!)
     */
    public List<OrderWithUser> findOrdersWithUserInfo(List<Long> orderIds) {
        // Step 1: Fetch orders (grouped by shard)
        Map<ShardId, List<Long>> orderIdsByShard = groupByShardKey(orderIds);

        Map<Long, Order> orders = new HashMap<>();
        for (Map.Entry<ShardId, List<Long>> entry : orderIdsByShard.entrySet()) {
            ShardId shard = entry.getKey();
            List<Long> ids = entry.getValue();

            JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
            List<Order> shardOrders = jdbc.query(
                "SELECT * FROM orders WHERE order_id IN (?)",
                new Object[]{ids},
                new OrderRowMapper()
            );

            shardOrders.forEach(o -> orders.put(o.getOrderId(), o));
        }

        // Step 2: Fetch users (grouped by shard)
        Set<Long> userIds = orders.values().stream()
            .map(Order::getUserId)
            .collect(Collectors.toSet());

        Map<ShardId, List<Long>> userIdsByShard = groupByShardKey(userIds);

        Map<Long, User> users = new HashMap<>();
        for (Map.Entry<ShardId, List<Long>> entry : userIdsByShard.entrySet()) {
            ShardId shard = entry.getKey();
            List<Long> ids = entry.getValue();

            JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
            List<User> shardUsers = jdbc.query(
                "SELECT * FROM users WHERE user_id IN (?)",
                new Object[]{ids},
                new UserRowMapper()
            );

            shardUsers.forEach(u -> users.put(u.getUserId(), u));
        }

        // Step 3: Join in application (expensive!)
        return orders.values().stream()
            .map(order -> new OrderWithUser(
                order,
                users.get(order.getUserId())
            ))
            .collect(Collectors.toList());
    }

    private Map<ShardId, List<Long>> groupByShardKey(Collection<Long> ids) {
        return ids.stream()
            .collect(Collectors.groupingBy(id ->
                shardingStrategy.getShards(id.toString()).get(0)
            ));
    }
}
```

---

## Rebalancing & Data Migration

🎓 **PROFESSOR**: **Adding/removing shards is the hardest problem** in sharding:

```text
Problem: Adding a shard changes hash function
=========================================

Before: 4 shards
shard_id = hash(key) % 4

After: 5 shards
shard_id = hash(key) % 5

Result: ~80% of keys now map to different shards!
→ Massive data migration required 💥
```

**Solution Strategies:**

### 1. **Consistent Hashing (Minimal Migration)**

```java
/**
 * Consistent hashing for sharding
 * Only ~1/N keys move when adding Nth shard
 */
public class ConsistentHashShardingStrategy implements ShardingStrategy {

    private final ConsistentHashRing<ShardId> ring;
    private final int virtualNodesPerShard = 150;

    public ConsistentHashShardingStrategy(List<ShardId> initialShards) {
        this.ring = new ConsistentHashRing<>(virtualNodesPerShard);
        initialShards.forEach(ring::addServer);
    }

    @Override
    public List<ShardId> getShards(String key) {
        ShardId shard = ring.getServer(key);
        return List.of(shard);
    }

    @Override
    public void addShard(ShardId newShard) {
        // Only ~1/N keys need migration (where N = shard count)
        ring.addServer(newShard);

        // Determine which keys to migrate
        List<KeyRange> rangesToMigrate = identifyAffectedRanges(newShard);

        // Background migration
        migrateData(rangesToMigrate, newShard);
    }

    private List<KeyRange> identifyAffectedRanges(ShardId newShard) {
        List<KeyRange> ranges = new ArrayList<>();

        for (VirtualNode vnode : newShard.getVirtualNodes()) {
            // Find predecessor shard (current owner of this range)
            ShardId predecessor = ring.getPredecessorServer(vnode.getHash());

            ranges.add(new KeyRange(
                predecessor.getLastVnodeHash(),
                vnode.getHash(),
                predecessor
            ));
        }

        return ranges;
    }
}
```

### 2. **Directory-Based Routing (Flexible but Complex)**

```java
/**
 * Directory service tracks key → shard mapping
 */
public class DirectoryBasedSharding implements ShardingStrategy {

    private final ShardDirectory directory;  // Stored in ZooKeeper/etcd

    /**
     * Key ranges stored in directory
     */
    static class ShardDirectory {
        // Range → Shard mapping
        NavigableMap<Long, ShardId> ranges = new TreeMap<>();

        public ShardId getShard(String key) {
            long hash = hash(key);
            Map.Entry<Long, ShardId> entry = ranges.floorEntry(hash);
            return entry.getValue();
        }

        public void splitRange(Long startHash, ShardId oldShard, ShardId newShard) {
            // Split range: [start, mid) → oldShard, [mid, end) → newShard
            long mid = (startHash + getNextRange(startHash)) / 2;
            ranges.put(mid, newShard);
        }
    }

    @Override
    public List<ShardId> getShards(String key) {
        ShardId shard = directory.getShard(key);
        return List.of(shard);
    }

    @Override
    public void addShard(ShardId newShard) {
        // 1. Pick a range to split (largest shard)
        ShardId largestShard = directory.findLargestShard();
        Long startHash = directory.getRangeStart(largestShard);

        // 2. Update directory (atomic)
        directory.splitRange(startHash, largestShard, newShard);

        // 3. Background data migration
        migrateSplitRange(largestShard, newShard);
    }
}
```

### 3. **Gradual Migration (Zero-Downtime)**

🏗️ **ARCHITECT**: Production migration strategy:

```java
/**
 * Zero-downtime shard migration
 */
public class ShardMigrationService {

    enum MigrationPhase {
        NOT_STARTED,
        DUAL_WRITE,      // Write to both old and new shard
        BACKFILL,        // Copy historical data
        VERIFICATION,    // Verify data consistency
        CUTOVER,         // Switch reads to new shard
        CLEANUP          // Delete from old shard
    }

    /**
     * Migrate key range from source to target shard
     */
    public void migrate(KeyRange range, ShardId source, ShardId target) {
        MigrationState state = new MigrationState(range, source, target);

        try {
            // Phase 1: Start dual-write
            enterDualWriteMode(state);
            log.info("Phase 1: Dual-write enabled for range {}", range);

            // Phase 2: Backfill historical data
            backfillData(state);
            log.info("Phase 2: Backfill complete, {} keys migrated",
                state.getKeysMigrated());

            // Phase 3: Verification
            verifyConsistency(state);
            log.info("Phase 3: Verification complete, {} mismatches found",
                state.getMismatches());

            // Phase 4: Cutover reads
            cutoverReads(state);
            log.info("Phase 4: Cutover complete, reads now from target shard");

            // Phase 5: Cleanup
            cleanup(state);
            log.info("Phase 5: Cleanup complete, migration finished");

        } catch (Exception e) {
            rollback(state);
            throw new MigrationException("Migration failed: " + e.getMessage(), e);
        }
    }

    private void enterDualWriteMode(MigrationState state) {
        // Update routing rules: writes go to BOTH shards
        routingService.addDualWriteRule(state.range, state.source, state.target);

        // Wait for all in-flight requests to complete
        Thread.sleep(5000);

        state.phase = MigrationPhase.DUAL_WRITE;
    }

    private void backfillData(MigrationState state) {
        // Stream data from source to target (rate-limited)
        long offset = 0;
        long batchSize = 1000;
        long rateLimit = 5000;  // keys/sec

        RateLimiter rateLimiter = RateLimiter.create(rateLimit);

        while (true) {
            // Read batch from source
            List<KeyValue> batch = readBatch(state.source, state.range, offset, batchSize);
            if (batch.isEmpty()) break;

            // Write to target (with rate limiting)
            for (KeyValue kv : batch) {
                rateLimiter.acquire();
                writeToTarget(state.target, kv);
                state.incrementKeysMigrated();
            }

            offset += batchSize;

            // Checkpoint progress (for crash recovery)
            state.saveCheckpoint(offset);
        }

        state.phase = MigrationPhase.BACKFILL;
    }

    private void verifyConsistency(MigrationState state) {
        // Sample random keys and compare source vs target
        int sampleSize = 10000;
        List<String> sampleKeys = sampleRandomKeys(state.range, sampleSize);

        int mismatches = 0;
        for (String key : sampleKeys) {
            KeyValue sourceValue = read(state.source, key);
            KeyValue targetValue = read(state.target, key);

            if (!sourceValue.equals(targetValue)) {
                log.warn("Mismatch for key {}: source={}, target={}",
                    key, sourceValue, targetValue);
                mismatches++;

                // Fix mismatch
                writeToTarget(state.target, sourceValue);
            }
        }

        state.setMismatches(mismatches);
        state.phase = MigrationPhase.VERIFICATION;

        if (mismatches > sampleSize * 0.01) {  // >1% mismatch rate
            throw new MigrationException("Too many mismatches: " + mismatches);
        }
    }

    private void cutoverReads(MigrationState state) {
        // Update routing: reads now from target shard
        routingService.cutoverReads(state.range, state.target);

        // Writes still dual-write (safety)
        state.phase = MigrationPhase.CUTOVER;

        // Wait for monitoring to confirm success
        Thread.sleep(60000);  // Monitor for 1 minute
    }

    private void cleanup(MigrationState state) {
        // Stop dual-write
        routingService.removeDualWriteRule(state.range);

        // Delete data from source shard (gradual)
        deleteGradually(state.source, state.range);

        state.phase = MigrationPhase.CLEANUP;
    }

    private void rollback(MigrationState state) {
        log.error("Rolling back migration for range {}", state.range);

        // Revert routing rules
        routingService.removeRule(state.range);

        // Mark migration as failed
        state.markFailed();
    }
}
```

**Migration Metrics to Monitor:**

```java
@Component
public class MigrationMetrics {

    @Autowired
    private MeterRegistry registry;

    public void recordMigration(MigrationState state) {
        // 1. Migration duration
        registry.timer("shard.migration.duration")
               .record(state.getDuration());

        // 2. Keys migrated
        registry.counter("shard.migration.keys_migrated")
               .increment(state.getKeysMigrated());

        // 3. Error rate
        registry.gauge("shard.migration.error_rate",
            state.getErrors() / (double) state.getKeysMigrated());

        // 4. Throughput
        registry.gauge("shard.migration.throughput_keys_per_sec",
            state.getKeysMigrated() / state.getDuration().getSeconds());

        // 5. Lag (writes during migration)
        registry.gauge("shard.migration.replication_lag",
            state.getReplicationLag());
    }
}
```

---

## Query Routing & Coordination

🎓 **PROFESSOR**: **Query types in sharded systems**:

```
┌────────────────────────────────────────────────┐
│ 1. SINGLE-SHARD QUERY (Fast)                  │
│    SELECT * FROM users WHERE user_id = 123    │
│    → Route to specific shard                  │
│                                               │
│ 2. SCATTER-GATHER QUERY (Slow)               │
│    SELECT * FROM users WHERE email = 'x@y.z' │
│    → Query all shards, merge results          │
│                                               │
│ 3. CROSS-SHARD JOIN (Expensive!)             │
│    SELECT * FROM users u JOIN orders o        │
│      ON u.user_id = o.user_id                │
│    → Requires application-level join          │
│                                               │
│ 4. AGGREGATION (Variable)                    │
│    SELECT COUNT(*) FROM users                 │
│    → Aggregate from all shards                │
└────────────────────────────────────────────────┘
```

**Query Routing Implementation:**

```java
/**
 * Smart query router with optimization
 */
public class ShardQueryRouter {

    private final ShardingStrategy sharding;
    private final Map<ShardId, DataSource> shardDataSources;
    private final ExecutorService executor = Executors.newFixedThreadPool(32);

    /**
     * Route query based on WHERE clause analysis
     */
    public <T> List<T> executeQuery(String sql, QueryParams params,
                                     RowMapper<T> mapper) {
        // Parse query to extract sharding key
        QueryAnalysis analysis = analyzeSql(sql, params);

        if (analysis.hasShardingKey()) {
            // Fast path: single-shard query
            return executeSingleShard(sql, params, analysis.getShardingKey(), mapper);
        } else {
            // Slow path: scatter-gather
            return executeScatterGather(sql, params, mapper);
        }
    }

    /**
     * Single-shard execution (fast)
     */
    private <T> List<T> executeSingleShard(String sql, QueryParams params,
                                            String shardingKey, RowMapper<T> mapper) {
        ShardId shard = sharding.getShards(shardingKey).get(0);

        JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
        return jdbc.query(sql, params.toArray(), mapper);
    }

    /**
     * Scatter-gather execution (slow)
     */
    private <T> List<T> executeScatterGather(String sql, QueryParams params,
                                              RowMapper<T> mapper) {
        List<ShardId> allShards = sharding.getAllShards();

        // Execute in parallel
        List<CompletableFuture<List<T>>> futures = allShards.stream()
            .map(shard -> CompletableFuture.supplyAsync(() -> {
                JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
                return jdbc.query(sql, params.toArray(), mapper);
            }, executor))
            .collect(Collectors.toList());

        // Merge results
        return futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    /**
     * Aggregation query (needs special handling)
     */
    public long executeCount(String table, String whereClause) {
        List<ShardId> allShards = sharding.getAllShards();

        // Count on each shard
        List<CompletableFuture<Long>> futures = allShards.stream()
            .map(shard -> CompletableFuture.supplyAsync(() -> {
                String sql = "SELECT COUNT(*) FROM " + table +
                            (whereClause.isEmpty() ? "" : " WHERE " + whereClause);

                JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
                return jdbc.queryForObject(sql, Long.class);
            }, executor))
            .collect(Collectors.toList());

        // Sum counts
        return futures.stream()
            .map(CompletableFuture::join)
            .mapToLong(Long::longValue)
            .sum();
    }

    /**
     * Optimized ORDER BY + LIMIT (merge K sorted lists)
     */
    public <T extends Comparable<T>> List<T> executeOrderByLimit(
            String sql, QueryParams params, int limit, RowMapper<T> mapper) {

        List<ShardId> allShards = sharding.getAllShards();

        // Fetch top K from each shard
        List<CompletableFuture<List<T>>> futures = allShards.stream()
            .map(shard -> CompletableFuture.supplyAsync(() -> {
                JdbcTemplate jdbc = new JdbcTemplate(shardDataSources.get(shard));
                return jdbc.query(sql, params.toArray(), mapper);
            }, executor))
            .collect(Collectors.toList());

        // Merge K sorted lists using min-heap
        PriorityQueue<T> heap = new PriorityQueue<>(limit);

        futures.stream()
            .map(CompletableFuture::join)
            .flatMap(List::stream)
            .forEach(item -> {
                heap.offer(item);
                if (heap.size() > limit) {
                    heap.poll();  // Keep only top K
                }
            });

        // Return sorted result
        return heap.stream()
            .sorted()
            .collect(Collectors.toList());
    }
}
```

**Query Analysis:**

```java
/**
 * Analyze SQL to determine routing strategy
 */
public class QueryAnalyzer {

    /**
     * Extract sharding key from WHERE clause
     */
    public QueryAnalysis analyzeSql(String sql, QueryParams params) {
        // Parse SQL (using JSqlParser or similar)
        Statement stmt = CCJSqlParserUtil.parse(sql);

        if (stmt instanceof Select) {
            Select select = (Select) stmt;
            PlainSelect plainSelect = (PlainSelect) select.getSelectBody();

            Expression where = plainSelect.getWhere();

            // Look for sharding key in WHERE clause
            ShardingKeyExtractor extractor = new ShardingKeyExtractor();
            where.accept(extractor);

            if (extractor.hasShardingKey()) {
                return new QueryAnalysis(
                    QueryType.SINGLE_SHARD,
                    extractor.getShardingKey()
                );
            } else if (plainSelect.getJoins() != null) {
                return new QueryAnalysis(QueryType.CROSS_SHARD_JOIN, null);
            } else {
                return new QueryAnalysis(QueryType.SCATTER_GATHER, null);
            }
        }

        return new QueryAnalysis(QueryType.UNKNOWN, null);
    }

    /**
     * AST visitor to extract sharding key
     */
    private class ShardingKeyExtractor extends ExpressionVisitorAdapter {
        private String shardingKey;

        @Override
        public void visit(EqualsTo equals) {
            // Look for: user_id = 123
            if (equals.getLeftExpression() instanceof Column) {
                Column column = (Column) equals.getLeftExpression();
                if ("user_id".equals(column.getColumnName())) {
                    // Extract value
                    shardingKey = equals.getRightExpression().toString();
                }
            }
        }

        public boolean hasShardingKey() {
            return shardingKey != null;
        }

        public String getShardingKey() {
            return shardingKey;
        }
    }
}
```

---

## Failure Modes & Edge Cases

🎓 **PROFESSOR**: Sharded systems have **unique failure modes**:

```
┌─────────────────────────────────────────────┐
│ 1. SHARD UNAVAILABILITY                     │
│    → Partial outage (single shard down)    │
│                                             │
│ 2. HOT SHARDS                               │
│    → Uneven load distribution              │
│                                             │
│ 3. DATA SKEW                                │
│    → Some shards much larger than others   │
│                                             │
│ 4. CROSS-SHARD TRANSACTIONS                │
│    → Distributed 2PC required              │
│                                             │
│ 5. SCATTER-GATHER TIMEOUTS                 │
│    → One slow shard delays entire query    │
└─────────────────────────────────────────────┘
```

### 1. Handling Shard Failures

```java
/**
 * Resilient shard access with circuit breaker
 */
public class ResilientShardAccess {

    private final Map<ShardId, CircuitBreaker> circuitBreakers;
    private final Map<ShardId, DataSource> primaryShards;
    private final Map<ShardId, List<DataSource>> replicaShards;

    /**
     * Execute with automatic failover
     */
    @CircuitBreaker(name = "shard-access")
    @Retry(name = "shard-access", maxAttempts = 3)
    public <T> T execute(ShardId shard, ShardQuery<T> query) {
        CircuitBreaker breaker = circuitBreakers.get(shard);

        if (breaker.getState() == CircuitBreaker.State.OPEN) {
            // Primary shard is down, try replicas
            log.warn("Shard {} is down, failing over to replica", shard);
            return executeOnReplica(shard, query);
        }

        try {
            // Try primary
            DataSource primary = primaryShards.get(shard);
            return query.execute(new JdbcTemplate(primary));

        } catch (Exception e) {
            // Circuit breaker will open after N failures
            log.error("Query failed on shard {}", shard, e);
            throw new ShardUnavailableException("Shard " + shard + " unavailable", e);
        }
    }

    private <T> T executeOnReplica(ShardId shard, ShardQuery<T> query) {
        List<DataSource> replicas = replicaShards.get(shard);

        for (DataSource replica : replicas) {
            try {
                return query.execute(new JdbcTemplate(replica));
            } catch (Exception e) {
                log.warn("Replica query failed, trying next replica", e);
            }
        }

        throw new AllReplicasUnavailableException(
            "All replicas for shard " + shard + " unavailable"
        );
    }
}
```

### 2. Hot Shard Detection & Mitigation

```python
class HotShardDetector:
    """
    Detect and mitigate hot shards
    """
    def __init__(self, shards: List[ShardId]):
        self.shards = shards
        self.metrics = {}  # shard → metrics

    def detect_hot_shards(self) -> List[ShardId]:
        """
        Identify shards with disproportionate load
        """
        # Collect metrics
        total_qps = sum(m.qps for m in self.metrics.values())
        avg_qps = total_qps / len(self.shards)

        hot_shards = []
        for shard, metrics in self.metrics.items():
            if metrics.qps > 3 * avg_qps:  # 3x average
                hot_shards.append({
                    'shard_id': shard,
                    'qps': metrics.qps,
                    'avg_qps': avg_qps,
                    'ratio': metrics.qps / avg_qps
                })

        return hot_shards

    def mitigate_hot_shard(self, shard: ShardId):
        """
        Mitigation strategies for hot shard
        """
        # Strategy 1: Add read replicas
        if self.is_read_heavy(shard):
            self.add_read_replicas(shard, count=3)
            return "Added 3 read replicas"

        # Strategy 2: Split shard (if data skew)
        if self.is_data_skewed(shard):
            new_shards = self.split_shard(shard, num_splits=2)
            return f"Split into {len(new_shards)} shards"

        # Strategy 3: Cache hot keys
        hot_keys = self.identify_hot_keys(shard)
        self.cache_hot_keys(hot_keys)
        return f"Cached {len(hot_keys)} hot keys"

    def identify_hot_keys(self, shard: ShardId) -> List[str]:
        """
        Find celebrity keys causing hot spot
        """
        # Analyze query logs
        key_frequency = defaultdict(int)

        for query in self.get_recent_queries(shard):
            key = extract_sharding_key(query)
            key_frequency[key] += 1

        # Top 1% keys
        threshold = percentile(key_frequency.values(), 99)
        return [k for k, v in key_frequency.items() if v > threshold]
```

### 3. Cross-Shard Transactions

```java
/**
 * Distributed transaction using 2-Phase Commit
 */
public class CrossShardTransactionManager {

    /**
     * Execute transaction across multiple shards
     */
    public void executeDistributedTransaction(
            Map<ShardId, List<SqlStatement>> shardOperations) {

        String transactionId = UUID.randomUUID().toString();
        Map<ShardId, TransactionState> states = new HashMap<>();

        try {
            // PHASE 1: PREPARE
            boolean allPrepared = preparePhase(transactionId, shardOperations, states);

            if (!allPrepared) {
                // At least one shard failed to prepare
                abortAll(transactionId, states);
                throw new DistributedTransactionException("Prepare phase failed");
            }

            // PHASE 2: COMMIT
            commitPhase(transactionId, states);

        } catch (Exception e) {
            // Abort on any error
            abortAll(transactionId, states);
            throw new DistributedTransactionException(
                "Distributed transaction failed: " + e.getMessage(), e
            );
        }
    }

    /**
     * Phase 1: Ask all shards if they can commit
     */
    private boolean preparePhase(String txId,
                                  Map<ShardId, List<SqlStatement>> operations,
                                  Map<ShardId, TransactionState> states) {

        List<CompletableFuture<Boolean>> futures = new ArrayList<>();

        for (Map.Entry<ShardId, List<SqlStatement>> entry : operations.entrySet()) {
            ShardId shard = entry.getKey();
            List<SqlStatement> stmts = entry.getValue();

            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    // Execute in local transaction but DON'T commit yet
                    DataSource ds = getDataSource(shard);
                    Connection conn = ds.getConnection();
                    conn.setAutoCommit(false);

                    for (SqlStatement stmt : stmts) {
                        stmt.execute(conn);
                    }

                    // Write PREPARE log
                    writePrepareLog(txId, shard);

                    // Store connection for phase 2
                    states.put(shard, new TransactionState(conn, TransactionPhase.PREPARED));

                    return true;  // Vote: YES

                } catch (Exception e) {
                    log.error("Prepare failed on shard {}", shard, e);
                    return false;  // Vote: NO
                }
            }));
        }

        // Wait for all votes
        return futures.stream()
            .map(CompletableFuture::join)
            .allMatch(vote -> vote);  // Unanimous YES required
    }

    /**
     * Phase 2: Commit all shards
     */
    private void commitPhase(String txId, Map<ShardId, TransactionState> states) {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Map.Entry<ShardId, TransactionState> entry : states.entrySet()) {
            ShardId shard = entry.getKey();
            TransactionState state = entry.getValue();

            futures.add(CompletableFuture.runAsync(() -> {
                try {
                    // Commit local transaction
                    state.connection.commit();

                    // Write COMMIT log
                    writeCommitLog(txId, shard);

                    state.phase = TransactionPhase.COMMITTED;

                } catch (Exception e) {
                    log.error("Commit failed on shard {} (data inconsistent!)", shard, e);
                    // This is bad: some shards committed, some didn't
                    // Need manual intervention or automatic reconciliation
                    throw new DistributedTransactionException(
                        "Commit phase failed on shard " + shard, e
                    );
                }
            }));
        }

        // Wait for all commits
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
    }

    /**
     * Abort all shards
     */
    private void abortAll(String txId, Map<ShardId, TransactionState> states) {
        for (Map.Entry<ShardId, TransactionState> entry : states.entrySet()) {
            ShardId shard = entry.getKey();
            TransactionState state = entry.getValue();

            try {
                if (state.connection != null) {
                    state.connection.rollback();
                }
                writeAbortLog(txId, shard);
            } catch (Exception e) {
                log.error("Abort failed on shard {}", shard, e);
            }
        }
    }
}
```

**Interview talking point**: "Cross-shard transactions are expensive and fragile. I'd design to avoid them: either use eventual consistency, or co-locate related data on same shard."

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Functional:
- What is the total data size? (determines if sharding needed)
- What are the query patterns? (determines sharding key)
- What entities need to be co-located? (determines sharding strategy)
- Are cross-shard queries acceptable? (determines complexity)

Non-Functional:
- Scale: X writes/sec, Y reads/sec
- Data volume: Z GB total, growth rate
- Latency: P95 < 50ms for single-shard queries
- Availability: 99.99% (requires replication)
- Consistency: Strong vs eventual
```

### 2. Capacity Estimation (RADIO: Scale)

```
Example: Social Network Database

Users:
- 1B users
- User record: ~2KB
- Total: 1B * 2KB = 2TB

Posts:
- 100M posts/day
- Post record: ~5KB
- Daily: 100M * 5KB = 500GB/day
- Yearly: 500GB * 365 = 182TB/year

When to shard?
- Single MySQL can handle ~1TB efficiently
- Beyond 1TB, consider sharding
- Our dataset: 2TB + 182TB/year → MUST SHARD

Shard count:
- Target: 200GB per shard (for performance)
- Total data: 184TB
- Shards needed: 184TB / 200GB = 920 shards
- Rounds to: 1024 shards (power of 2)

QPS per shard:
- Total reads: 1M QPS
- Per shard: 1M / 1024 = ~1000 QPS (manageable)
```

### 3. Partitioning Strategy (RADIO: Data-Model)

```java
/**
 * Social network sharding strategy
 */
public class SocialNetworkSharding {

    /**
     * Shard by user_id (primary dimension)
     */
    private static final ShardingKey PRIMARY_KEY = ShardingKey.USER_ID;

    /**
     * Co-locate user data on same shard
     */
    @ShardBy("user_id")
    @Table(name = "users")
    public class User {
        private Long userId;      // Sharding key
        private String email;
        private String name;
    }

    @ShardBy("user_id")  // Co-located with User
    @Table(name = "posts")
    public class Post {
        private Long postId;
        private Long userId;      // Sharding key (same as User)
        private String content;
        private Instant createdAt;
    }

    @ShardBy("user_id")  // Co-located with User
    @Table(name = "followers")
    public class Follower {
        private Long followerId;   // user_id
        private Long followeeId;
        // Sharding key: followerId
        // Tradeoff: "who follows X?" query requires scatter-gather
    }

    /**
     * Query patterns:
     * ✅ Get user posts: Single-shard (shard by user_id)
     * ✅ Get user followers: Single-shard (shard by follower_id)
     * ❌ Get user followees: Scatter-gather (shard by followee_id)
     * ❌ Get post by post_id: Scatter-gather (no post_id in shard key)
     */
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌──────────────────────────────────────────────────┐
│              APPLICATION SERVERS                  │
│                                                   │
│  ┌────────────────────────────────────────────┐ │
│  │     Sharding Middleware                    │ │
│  │  - Query routing                           │ │
│  │  - Scatter-gather                          │ │
│  │  - Connection pooling                      │ │
│  └────────────────┬───────────────────────────┘ │
└───────────────────┼───────────────────────────────┘
                    ↓
        ┌───────────┴───────────┐
        ↓                       ↓
┌────────────────┐      ┌────────────────┐
│  SHARD GROUP 0 │      │  SHARD GROUP 1 │
│                │      │                │
│  ┌──────────┐ │      │  ┌──────────┐  │
│  │ Primary  │ │      │  │ Primary  │  │
│  │ (Write)  │ │      │  │ (Write)  │  │
│  └────┬─────┘ │      │  └────┬─────┘  │
│       ↓       │      │       ↓        │
│  ┌──────────┐ │      │  ┌──────────┐  │
│  │ Replica  │ │      │  │ Replica  │  │
│  │ (Read)   │ │      │  │ (Read)   │  │
│  └──────────┘ │      │  └──────────┘  │
└────────────────┘      └────────────────┘

        ... (1024 shard groups total)

┌────────────────────────────────────────────┐
│        METADATA SERVICE                     │
│  (ZooKeeper, etcd, Consul)                 │
│                                            │
│  - Shard mapping (key → shard)             │
│  - Shard health (up/down)                  │
│  - Migration state                         │
└────────────────────────────────────────────┘
```

### 5. Deep Dives (RADIO: Optimize)

**A. Shard Key Selection**

```python
# Evaluation of sharding key candidates

candidates = ['user_id', 'email', 'created_at', 'country']

# user_id:
# ✅ High cardinality (1B unique values)
# ✅ Present in most queries
# ✅ Immutable (never changes)
# ✅ Even distribution (auto-increment)
# Score: 95/100 → BEST CHOICE

# email:
# ✅ High cardinality
# ❌ Queries rarely use email
# ❌ Mutable (users can change email)
# Score: 50/100

# created_at:
# ❌ Low cardinality (dates)
# ❌ Hot spot (recent dates get all writes)
# Score: 20/100

# country:
# ❌ Very low cardinality (~200 countries)
# ❌ Uneven distribution (US >> Vatican)
# Score: 10/100
```

**B. Avoiding Cross-Shard Queries**

```java
/**
 * Denormalize to avoid cross-shard queries
 */

// ❌ BAD: Requires cross-shard JOIN
@Table("posts")
class Post {
    Long postId;
    Long userId;      // Foreign key to users table
    String content;
}

// Query: "Show post with author name"
// SELECT p.content, u.name
// FROM posts p JOIN users u ON p.user_id = u.user_id
// WHERE p.post_id = 123
// → Expensive! Post and User might be on different shards

// ✅ GOOD: Denormalize author name
@Table("posts")
class Post {
    Long postId;
    Long userId;
    String content;
    String authorName;  // Denormalized!
}

// Query: "Show post with author name"
// SELECT content, author_name FROM posts WHERE post_id = 123
// → Single-shard query (if we also shard posts by user_id)
```

**C. Monitoring & Alerting**

```java
@Component
public class ShardingMetrics {

    @Autowired
    private MeterRegistry registry;

    public void recordQuery(ShardId shard, QueryType type, Duration latency) {
        // 1. Query latency by shard and type
        registry.timer("shard.query.latency",
            "shard", shard.toString(),
            "type", type.name()
        ).record(latency);

        // 2. Query count by shard
        registry.counter("shard.query.count",
            "shard", shard.toString()
        ).increment();

        // 3. Scatter-gather query count
        if (type == QueryType.SCATTER_GATHER) {
            registry.counter("shard.scatter_gather.total").increment();
        }
    }

    public void recordShardHealth(ShardId shard, boolean isHealthy) {
        registry.gauge("shard.health",
            Tags.of("shard", shard.toString()),
            isHealthy ? 1 : 0
        );
    }

    public void recordDataSkew(Map<ShardId, Long> rowCounts) {
        long avg = rowCounts.values().stream()
            .mapToLong(Long::longValue)
            .average()
            .orElse(0);

        double stdDev = Math.sqrt(
            rowCounts.values().stream()
                .mapToDouble(count -> Math.pow(count - avg, 2))
                .average()
                .orElse(0)
        );

        registry.gauge("shard.data_skew.coefficient_of_variation",
            stdDev / avg
        );
    }

    /**
     * Alerts:
     * - shard.health == 0 (shard down)
     * - shard.query.latency P99 > 500ms
     * - shard.scatter_gather.total > 10% of total queries
     * - shard.data_skew.coefficient_of_variation > 0.3
     */
}
```

---

## 🧠 MIND MAP: PARTITIONING & SHARDING CONCEPTS

```
           SHARDING
              |
      ┌───────┴───────┐
      ↓               ↓
  STRATEGIES      OPERATIONS
      |               |
  ┌───┴───┐       ┌───┴───┐
  ↓       ↓       ↓       ↓
HASH   RANGE   ROUTING REBALANCING
  |       |       |       |
MODULO  DATE   SINGLE  CONSISTENT
        GEO    SHARD   HASHING
        |      SCATTER  |
     TENANT   GATHER MIGRATION
        |        |       |
     SIZE    CROSS-   DUAL
             SHARD   WRITE
             JOIN      |
                    BACKFILL
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **Sharding = Splitting Pizza 🍕**
- Can't eat entire pizza alone (too big)
- Split into slices, share with friends
- Each person gets specific slices (sharding key)
- If you want toppings from multiple slices, must ask around (scatter-gather)

### 2. **Sharding Key = Home Address 🏠**
- Mail goes to specific address (deterministic)
- Good address: unique, never changes, easy to find
- Bad address: ambiguous, changes often, hard to locate

### 3. **Hot Shard = Popular Checkout Lane 🛒**
- One lane has celebrity cashier (hot spot)
- Line backs up, other lanes empty
- Solution: Open more lanes (read replicas) or split customers (re-shard)

### 4. **Scatter-Gather = Asking Entire Class ❓**
- Teacher asks question without specifying student
- Must ask EVERYONE, wait for all responses
- Slow! Better to ask specific student (single-shard query)

### 5. **Cross-Shard Join = Conference Call 📞**
- Need info from multiple people in different cities
- Must coordinate timing, merge responses
- Expensive! Better if everyone in same room (co-location)

### 6. **Shard Rebalancing = Moving Day 📦**
- Moving furniture to new apartment (data migration)
- Can't stop living during move (dual-write)
- Gradually move boxes, verify nothing lost (backfill + verification)
- Finally switch addresses (cutover)

### 7. **Consistent Hashing = Circular Seating 🎪**
- Guests sit at circular table
- New guest arrives, only neighbors reshuffle
- NOT everyone changing seats!

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Sharding:**

1. **Choose Sharding Key Wisely**
   - This decision is nearly impossible to change later
   - Optimize for most common query pattern
   - Immutable, high cardinality, even distribution

2. **Co-locate Related Data**
   - Keep related entities on same shard
   - Avoids expensive cross-shard queries
   - Denormalize if necessary

3. **Delay Sharding as Long as Possible**
   - Single database is simpler, faster, cheaper
   - Only shard when truly needed (> 1TB, > 10K QPS)
   - Vertical scaling + read replicas can take you far

---

## 🏗️ ARCHITECT'S CHECKLIST

Before implementing sharding:

- [ ] **Sharding Key**: High cardinality? Immutable? Present in queries?
- [ ] **Co-location**: Related entities on same shard?
- [ ] **Query Patterns**: Avoid scatter-gather queries?
- [ ] **Rebalancing**: Strategy for adding/removing shards?
- [ ] **Monitoring**: Metrics for hot shards, data skew?
- [ ] **Failover**: Replicas for each shard? Circuit breakers?
- [ ] **Cross-Shard**: Avoid distributed transactions?
- [ ] **Migration**: Zero-downtime migration plan?
- [ ] **Rollback**: Can rollback failed migration?
- [ ] **Testing**: Load tested shard routing?

---

**Interview Closer**: "I'd start with hash-based sharding by user_id for even distribution. Co-locate user, posts, and followers on same shard to avoid cross-shard queries. Use consistent hashing for easy rebalancing. Monitor for hot shards and data skew. Only use scatter-gather queries when absolutely necessary. Let me draw the architecture..."
