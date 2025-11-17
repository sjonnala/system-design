# Batch Job Data Joins Deep Dive: The Right Way

## Contents

- [Batch Job Data Joins Deep Dive: The Right Way](#batch-job-data-joins-deep-dive-the-right-way)
    - [Core Mental Model](#core-mental-model)
    - [The Join Problem in Distributed Systems](#the-join-problem-in-distributed-systems)
    - [Reduce-Side Join (Sort-Merge Join)](#reduce-side-join-sort-merge-join)
    - [Map-Side Join (Broadcast Join)](#map-side-join-broadcast-join)
    - [Broadcast Hash Join](#broadcast-hash-join)
    - [Handling Data Skew](#handling-data-skew)
    - [Join Types & Strategies](#join-types--strategies)
    - [Optimization Techniques](#optimization-techniques)
    - [Real-World Patterns](#real-world-patterns)
    - [Anti-Patterns & Pitfalls](#anti-patterns--pitfalls)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: DATA JOINS](#mind-map-data-joins)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Data Join = Combining two datasets based on common key

The Challenge:
┌────────────────────────────────────────────────────────────┐
│ In single-machine database:  JOIN is straightforward      │
│ In distributed system:       JOIN is THE bottleneck       │
│                                                             │
│ Why? Data is split across many machines!                   │
│      Matching records might be on different nodes!         │
└────────────────────────────────────────────────────────────┘

Example:
  Users table (1TB):  [user_id, name, email, ...]
  Orders table (10TB): [order_id, user_id, amount, ...]

  Goal: JOIN to get [order_id, user_id, name, amount, ...]

  Problem: users and orders distributed across 100 machines
           → How to match user_id efficiently?

Three Main Strategies:
──────────────────────

1. REDUCE-SIDE JOIN (Sort-Merge)
   ┌──────────┐    ┌──────────┐
   │  Users   │    │  Orders  │
   └─────┬────┘    └─────┬────┘
         │               │
     [SHUFFLE]       [SHUFFLE]    ← Expensive!
         │               │
         └───────┬───────┘
                 ↓
           ┌──────────┐
           │ Reducers │ Join in reducer
           └──────────┘

   When: Both datasets large
   Cost: Shuffle BOTH datasets (expensive!)

2. MAP-SIDE JOIN (Replicated)
   ┌──────────┐    ┌──────────┐
   │ Users    │    │  Orders  │
   │ (small)  │    │ (large)  │
   └─────┬────┘    └─────┬────┘
         │               │
   [BROADCAST]           │        ← Fast!
         │               │
         └───────┬───────┘
                 ↓
           ┌──────────┐
           │  Mappers │ Join in mapper
           └──────────┘

   When: One dataset small (fits in memory)
   Cost: Broadcast small dataset (cheap!)

3. BROADCAST HASH JOIN
   ┌──────────┐    ┌──────────┐
   │ Users    │    │  Orders  │
   │ (small)  │    │ (large)  │
   └─────┬────┘    └─────┬────┘
         │               │
   [BUILD HASH]          │        ← Fastest!
         │               │
   [BROADCAST HASH]      │
         │               │
         └───────┬───────┘
                 ↓
           ┌──────────┐
           │  Nodes   │ Hash lookup (O(1))
           └──────────┘

   When: One dataset small + hash fits in memory
   Cost: Broadcast hash table (cheapest!)
```

**The Key Insight:**
```
Shuffle is expensive (network + disk I/O)
→ Avoid shuffling large datasets
→ Broadcast small datasets instead
→ 10-100x speedup!

Decision tree:
  Small dataset (< 100MB)?     → Broadcast Hash Join
  Medium dataset (< 1GB)?      → Map-side Join
  Both datasets large (> 1GB)? → Reduce-side Join
```

---

## The Join Problem in Distributed Systems

🎓 **PROFESSOR**: Why joins are hard in distributed systems.

### The Single-Machine Join

```sql
-- Easy on single machine (e.g., PostgreSQL)

SELECT o.order_id, o.amount, u.name, u.email
FROM orders o
JOIN users u ON o.user_id = u.user_id;

-- Database internally:
--   1. Load both tables into memory (or scan from disk)
--   2. Build hash table on users (smaller table)
--   3. For each order, lookup user in hash table
--   4. Output joined record
--   Time: O(orders + users) = linear
--   Memory: O(users) for hash table
```

### The Distributed Join Challenge

```
Same query on distributed system (100 nodes):
─────────────────────────────────────────────

Users table distributed:
  Node 1: user_ids [1, 2, 3, ...]
  Node 2: user_ids [101, 102, 103, ...]
  ...
  Node 100: user_ids [9901, 9902, 9903, ...]

Orders table distributed:
  Node 1: orders with user_ids [45, 67, 123, ...]
  Node 2: orders with user_ids [1, 200, 567, ...]
  ...
  Node 100: orders with user_ids [88, 234, 901, ...]

Problem:
  Order on Node 2 (user_id=1) needs User on Node 1 (user_id=1)
  → Data on different machines!
  → Network transfer required!

Naive solution (BAD):
  For each order, lookup user over network
  10 billion orders × 1 network request = 10 billion network calls
  Time: FOREVER

Better solution:
  GROUP data by user_id (shuffle)
  Send matching records to same node
  Join locally
```

### Why Joins Are Expensive

```java
/**
 * Cost analysis of distributed joins
 */
public class JoinCostAnalysis {

    /**
     * Example: Join 1TB users with 10TB orders
     */
    public void costAnalysis() {
        /**
         * Reduce-side join (shuffle both):
         *   Network transfer: 1TB + 10TB = 11TB
         *   Disk I/O: Write 11TB (shuffle), Read 11TB (reducer)
         *   Time: 11TB / 1Gbps = 24 hours (network bound)
         *
         * Map-side join (broadcast 1TB users):
         *   Network transfer: 1TB (broadcast to all nodes)
         *   Disk I/O: 0TB (no shuffle write)
         *   Time: 1TB / 1Gbps = 2.2 hours (network bound)
         *   Speedup: 11x
         *
         * Broadcast hash join (1TB users fits in memory):
         *   Network transfer: 1TB (broadcast hash table)
         *   Disk I/O: 0TB
         *   Memory: 1TB distributed across cluster (10GB/node × 100 nodes)
         *   Time: 1TB / 1Gbps = 2.2 hours (network bound)
         *   BUT: Subsequent queries instant (hash table cached!)
         *
         * Reality:
         *   Reduce-side join: ~3 hours (1TB + 10TB with 100 nodes)
         *   Map-side join: ~20 minutes (broadcast 1TB users)
         *   Broadcast hash join: ~15 minutes (first time), <1 min (cached)
         */
    }

    /**
     * Key bottlenecks:
     */
    public void bottlenecks() {
        /**
         * 1. Network bandwidth
         *    - Shuffle transfers TBs of data
         *    - 1Gbps link → 11TB takes 24 hours
         *    - 10Gbps link → 11TB takes 2.4 hours
         *    → Network is often the bottleneck!
         *
         * 2. Disk I/O
         *    - Shuffle writes to disk (map side)
         *    - Shuffle reads from disk (reduce side)
         *    - Random I/O is especially slow
         *
         * 3. Memory pressure
         *    - Reducer must hold data for all keys
         *    - If data doesn't fit → Spill to disk (slow!)
         *    - Broadcast join requires memory for hash table
         *
         * 4. Data skew
         *    - Some keys have many more values
         *    - One reducer gets overloaded
         *    - Job blocked on single slow task
         */
    }
}
```

---

## Reduce-Side Join (Sort-Merge Join)

🏗️ **ARCHITECT**: The default join strategy - works for any dataset size.

### How It Works

```
Reduce-Side Join Algorithm:
──────────────────────────

STAGE 1: MAP PHASE (Tag and Emit)
──────────────────────────────────

Mapper 1 (Users):
  Input:  (user_id=1, name="Alice", country="US")
  Output: (user_id=1, ("USER", "Alice", "US"))

Mapper 2 (Orders):
  Input:  (order_id=101, user_id=1, amount=50)
  Output: (user_id=1, ("ORDER", 101, 50))

STAGE 2: SHUFFLE (Group by Key)
────────────────────────────────

All records with same user_id sent to same reducer

Reducer 1 receives:
  (user_id=1, [("USER", "Alice", "US"), ("ORDER", 101, 50), ("ORDER", 102, 75)])

Reducer 2 receives:
  (user_id=2, [("USER", "Bob", "UK"), ("ORDER", 201, 100)])

STAGE 3: REDUCE PHASE (Join)
─────────────────────────────

Reducer 1:
  1. Separate users and orders:
     users = [("USER", "Alice", "US")]
     orders = [("ORDER", 101, 50), ("ORDER", 102, 75)]

  2. Cartesian product (join):
     FOR each user:
       FOR each order:
         EMIT (user_id, user.name, order.order_id, order.amount)

  Output:
    (1, "Alice", 101, 50)
    (1, "Alice", 102, 75)

Reducer 2:
  Output:
    (2, "Bob", 201, 100)
```

### Implementation

```scala
/**
 * Reduce-side join in Spark
 */
object ReduceSideJoin {

  /**
   * Method 1: Manual implementation (RDD)
   */
  def manualJoin(spark: SparkContext): Unit = {
    // Load datasets
    val users: RDD[(Int, String)] = spark.textFile("/data/users")
      .map { line =>
        val fields = line.split(",")
        (fields(0).toInt, fields(1))  // (user_id, name)
      }

    val orders: RDD[(Int, Double)] = spark.textFile("/data/orders")
      .map { line =>
        val fields = line.split(",")
        (fields(1).toInt, fields(2).toDouble)  // (user_id, amount)
      }

    // Tag records
    val taggedUsers = users.map { case (k, v) => (k, ("USER", v)) }
    val taggedOrders = orders.map { case (k, v) => (k, ("ORDER", v)) }

    // Union and group by key
    val grouped = taggedUsers.union(taggedOrders).groupByKey()

    // Join in reducer
    val result = grouped.flatMap { case (userId, records) =>
      val userRecords = records.filter(_._1 == "USER").map(_._2)
      val orderRecords = records.filter(_._1 == "ORDER").map(_._2)

      // Cartesian product
      for {
        userName <- userRecords
        orderAmount <- orderRecords
      } yield (userId, userName, orderAmount)
    }

    result.saveAsTextFile("/output/joined")
  }

  /**
   * Method 2: Using built-in join (recommended)
   */
  def builtInJoin(spark: SparkContext): Unit = {
    val users: RDD[(Int, String)] = spark.textFile("/data/users")
      .map { line =>
        val fields = line.split(",")
        (fields(0).toInt, fields(1))
      }

    val orders: RDD[(Int, Double)] = spark.textFile("/data/orders")
      .map { line =>
        val fields = line.split(",")
        (fields(1).toInt, fields(2).toDouble)
      }

    // Spark handles shuffle automatically!
    val result = users.join(orders)
    // Result: RDD[(Int, (String, Double))]
    // Example: (1, ("Alice", 50.0))

    result.saveAsTextFile("/output/joined")
  }

  /**
   * Method 3: DataFrame join (best)
   */
  def dataframeJoin(spark: SparkSession): Unit = {
    val users = spark.read
      .option("header", "true")
      .csv("/data/users")

    val orders = spark.read
      .option("header", "true")
      .csv("/data/orders")

    // DataFrame join with optimization
    val result = orders.join(users, "user_id")

    result.write.parquet("/output/joined")

    /**
     * DataFrame advantages:
     *   - Catalyst optimizer (optimize join strategy)
     *   - Predicate pushdown (filter before join)
     *   - Column pruning (read only needed columns)
     *   - Automatic broadcast for small tables
     */
  }
}
```

### When to Use Reduce-Side Join

```
Use reduce-side join when:
  ✓ Both datasets are large (> 1GB)
  ✓ Cannot fit smaller dataset in memory
  ✓ Data is already partitioned by join key (no shuffle needed!)
  ✓ Need to join on multiple keys

Characteristics:
  + Works for any dataset size
  + Distributed across all nodes
  - Requires shuffle (expensive)
  - Memory pressure on reducers
  - Susceptible to data skew

Example use cases:
  - Join two large fact tables (orders ⋈ shipments)
  - Historical data joins (all orders ⋈ all users)
  - Multi-way joins (orders ⋈ users ⋈ products)
```

---

## Map-Side Join (Broadcast Join)

🏗️ **ARCHITECT**: The fast path - avoid shuffle entirely!

### How It Works

```
Map-Side Join Algorithm (Replicated Join):
──────────────────────────────────────────

PREREQUISITE: Small dataset fits in memory (< 1GB)

STEP 1: Broadcast small dataset to all nodes
─────────────────────────────────────────────

Users (100MB):
  Load entire users table into memory
  Broadcast to all mapper nodes
  Each node has complete copy

STEP 2: Map-side join (no shuffle!)
────────────────────────────────────

Mapper 1 (Orders partition 1):
  1. Load broadcast users into memory (hash table)
     users_map = {1: "Alice", 2: "Bob", ...}

  2. For each order:
     user_id = order.user_id
     user_name = users_map.get(user_id)  ← O(1) lookup!
     EMIT (order_id, user_id, user_name, amount)

Mapper 2 (Orders partition 2):
  Same as Mapper 1 (independent!)

Mapper 3, 4, ... (All partitions in parallel)

RESULT: No shuffle! All work done in mappers!

Cost comparison:
  Reduce-side join:
    - Shuffle: 100MB (users) + 10TB (orders) = 10.1TB
    - Time: 2 hours

  Map-side join:
    - Broadcast: 100MB (users) × 1 time = 100MB
    - Time: 10 minutes
    - Speedup: 12x
```

### Implementation

```scala
/**
 * Map-side join in Spark
 */
object MapSideJoin {

  /**
   * Method 1: Broadcast variable (manual)
   */
  def broadcastVariableJoin(spark: SparkContext): Unit = {
    // Load small dataset (users)
    val usersArray = spark.textFile("/data/users")
      .map { line =>
        val fields = line.split(",")
        (fields(0).toInt, fields(1))  // (user_id, name)
      }
      .collect()  // Collect to driver (WARNING: Must fit in driver memory!)

    // Create broadcast variable
    val usersMap = spark.broadcast(usersArray.toMap)

    // Load large dataset (orders)
    val orders = spark.textFile("/data/orders")
      .map { line =>
        val fields = line.split(",")
        (fields(0).toInt, fields(1).toInt, fields(2).toDouble)  // (order_id, user_id, amount)
      }

    // Join in mapper using broadcast variable
    val result = orders.map { case (orderId, userId, amount) =>
      val userName = usersMap.value.get(userId)
      (orderId, userId, userName, amount)
    }

    result.saveAsTextFile("/output/joined")

    /**
     * How broadcast works:
     *   1. usersMap sent to each executor (not each task!)
     *   2. All tasks on same executor share same copy
     *   3. Memory usage: O(users) per executor (not per task)
     *   4. Network usage: O(users × num_executors)
     */
  }

  /**
   * Method 2: DataFrame broadcast join (automatic)
   */
  def dataframeBroadcastJoin(spark: SparkSession): Unit = {
    import org.apache.spark.sql.functions.broadcast

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Explicit broadcast hint
    val result = orders.join(broadcast(users), "user_id")

    result.write.parquet("/output/joined")

    /**
     * Spark automatically broadcasts if:
     *   - Table size < spark.sql.autoBroadcastJoinThreshold (default 10MB)
     *   - Statistics available (ANALYZE TABLE)
     *
     * Manual broadcast() forces broadcast even if larger
     */
  }

  /**
   * Method 3: Automatic broadcast (Spark decides)
   */
  def automaticBroadcast(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark automatically uses broadcast if users < 10MB
    val result = orders.join(users, "user_id")

    /**
     * Spark execution plan:
     *   == Physical Plan ==
     *   BroadcastHashJoin [user_id#0], [user_id#5]
     *   :- *(1) Project [...]
     *   :  +- *(1) FileScan parquet [...]
     *   +- BroadcastExchange HashedRelationBroadcastMode [...]
     *      +- *(2) Project [...]
     *         +- *(2) FileScan parquet [...]
     *
     * "BroadcastHashJoin" confirms broadcast used!
     */

    // View execution plan
    result.explain(true)
  }
}
```

### When to Use Map-Side Join

```
Use map-side join when:
  ✓ One dataset is small (< 1GB, preferably < 100MB)
  ✓ Small dataset fits in memory on each node
  ✓ Want fastest possible join (avoid shuffle)

Characteristics:
  + No shuffle (very fast!)
  + Low memory overhead (one copy per executor)
  + Scales with large dataset size
  - Requires small dataset to fit in memory
  - Network overhead (broadcast to all nodes)
  - Not suitable if both datasets large

Example use cases:
  - Join with dimension table (orders ⋈ products)
  - Enrich with lookup data (events ⋈ geo_lookup)
  - Filter with whitelist (data ⋈ allowed_users)
```

---

## Broadcast Hash Join

🏗️ **ARCHITECT**: The ultimate optimization - hash table broadcast.

### Hash Table Optimization

```
Broadcast Hash Join = Map-side join + Hash table
────────────────────────────────────────────────

Step 1: Build hash table from small dataset
  users: [(1, "Alice"), (2, "Bob"), (3, "Charlie")]
  ↓
  hash_table: {1: "Alice", 2: "Bob", 3: "Charlie"}

Step 2: Broadcast hash table (not raw data!)
  Benefit: O(1) lookup instead of O(n) scan

Step 3: Join in mapper with hash lookup
  FOR each order:
    user_name = hash_table.get(order.user_id)  ← O(1)!
    EMIT joined_record

Performance:
  Array broadcast: O(n) lookup per order
  Hash table broadcast: O(1) lookup per order
  For 1 billion orders × 1M users:
    Array: 1B × 1M comparisons (too slow!)
    Hash: 1B × 1 lookups (fast!)
```

### Implementation

```scala
object BroadcastHashJoin {

  /**
   * Spark automatically uses hash join for broadcast
   */
  def sparkHashJoin(spark: SparkSession): Unit = {
    import org.apache.spark.sql.functions.broadcast

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark builds hash table internally
    val result = orders.join(broadcast(users), "user_id")

    /**
     * Spark internally:
     *   1. Builds HashMap from users:
     *      HashMap<user_id, Row>
     *
     *   2. Serializes HashMap (not raw data)
     *      Benefit: HashMap is compact representation
     *
     *   3. Broadcasts HashMap to all executors
     *
     *   4. For each order, hash lookup:
     *      user_row = hashMap.get(order.user_id)  ← O(1)
     */
  }

  /**
   * Manual hash join (for custom logic)
   */
  def manualHashJoin(spark: SparkContext): Unit = {
    // Load users and build hash map
    val usersMap = spark.textFile("/data/users")
      .map { line =>
        val fields = line.split(",")
        (fields(0).toInt, fields(1))
      }
      .collectAsMap()  // Collect as Map (HashMap)

    // Broadcast hash map
    val broadcastMap = spark.broadcast(usersMap)

    // Join with hash lookup
    val orders = spark.textFile("/data/orders")
    val result = orders.map { line =>
      val fields = line.split(",")
      val orderId = fields(0).toInt
      val userId = fields(1).toInt
      val amount = fields(2).toDouble

      // O(1) hash lookup!
      val userName = broadcastMap.value.get(userId)

      (orderId, userId, userName, amount)
    }

    result.saveAsTextFile("/output/joined")
  }

  /**
   * Optimization: Bloom filter pre-filtering
   */
  def bloomFilterOptimization(spark: SparkSession): Unit = {
    /**
     * Problem: Broadcast 1GB users table
     *   - Expensive to broadcast
     *   - Memory pressure
     *
     * Optimization: Two-phase join
     *   Phase 1: Filter orders using Bloom filter
     *   Phase 2: Join filtered orders
     */

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Build Bloom filter from user_ids
    val userIds = users.select("user_id").distinct()
    val bloomFilter = userIds.stat.bloomFilter("user_id", 1000000, 0.01)

    // Broadcast Bloom filter (much smaller than full data!)
    val broadcastBloom = spark.sparkContext.broadcast(bloomFilter)

    // Filter orders (false positives ok, no false negatives)
    val filteredOrders = orders.filter { row =>
      broadcastBloom.value.mightContain(row.getAs[Int]("user_id"))
    }

    // Now join with smaller filtered dataset
    val result = filteredOrders.join(broadcast(users), "user_id")

    /**
     * Benefits:
     *   - Bloom filter: 1GB users → 10MB bloom filter
     *   - Filter out 90% of orders that won't match
     *   - Join 10% filtered orders (much faster!)
     *   - Total: 10MB broadcast + 10% shuffle (vs 1GB broadcast)
     */
  }
}
```

---

## Handling Data Skew

🎓 **PROFESSOR**: Data skew is the #1 cause of slow joins. Here's how to fix it.

### The Skew Problem

```
Data Skew = Uneven distribution of keys
───────────────────────────────────────

Example: E-commerce orders by user_id
  Regular users: 1-10 orders
  Power users: 100,000+ orders (celebrities, businesses)

Distribution:
  user_id=1:     5 orders
  user_id=2:     8 orders
  ...
  user_id=12345: 500,000 orders (HOT KEY!)
  ...
  user_id=99999: 3 orders

Join execution:
  Reducer 1: user_id=1,2,3,... (1000 orders) → Fast (1 second)
  Reducer 2: user_id=12345 (500,000 orders) → Slow (10 minutes)
  Reducer 3: user_id=10001,10002,... (1000 orders) → Fast (1 second)

Problem:
  Job blocked waiting for Reducer 2 (hot key)
  Other reducers idle
  Cluster underutilized

Result:
  Job takes 10 minutes (instead of 1 second without skew)
  100x slowdown!
```

### Skew Detection

```scala
object SkewDetection {

  /**
   * Identify skewed keys
   */
  def detectSkew(spark: SparkSession): Unit = {
    val orders = spark.read.parquet("/data/orders")

    // Count orders per user
    val orderCounts = orders.groupBy("user_id").count()

    // Find hot keys (e.g., > 10,000 orders)
    val hotKeys = orderCounts.filter($"count" > 10000)
      .orderBy($"count".desc)

    hotKeys.show()

    /**
     * Output:
     * +---------+-------+
     * | user_id | count |
     * +---------+-------+
     * |   12345 |500000 | ← HOT KEY!
     * |   67890 |250000 | ← HOT KEY!
     * |   11111 | 50000 |
     * +---------+-------+
     */

    // Check partition sizes
    val partitionSizes = orders.rdd.mapPartitions { iter =>
      Iterator(iter.size)
    }.collect()

    println(s"Partition sizes: ${partitionSizes.mkString(", ")}")

    /**
     * Skewed output:
     * Partition sizes: 1000, 1200, 500000, 1500, 900, ...
     *                               ↑
     *                          Hot partition!
     */
  }

  /**
   * Monitor during execution
   */
  def monitorSkew(): Unit = {
    /**
     * Spark UI indicators of skew:
     *   - One task takes much longer than others
     *   - One task processes much more data
     *   - Memory errors on one executor
     *   - "Shuffle read" metric varies widely across tasks
     *
     * Example:
     *   Task 1: 1GB shuffle read, 30 seconds
     *   Task 2: 1GB shuffle read, 30 seconds
     *   Task 3: 100GB shuffle read, 30 minutes ← SKEW!
     *   Task 4: 1GB shuffle read, 30 seconds
     */
  }
}
```

### Skew Mitigation Strategies

```scala
object SkewMitigation {

  /**
   * Strategy 1: Salting
   *   Add random suffix to hot keys → Distribute across multiple reducers
   */
  def saltingTechnique(spark: SparkSession): Unit = {
    import spark.implicits._
    import org.apache.spark.sql.functions._

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Identify hot keys
    val hotKeys = Set(12345, 67890)  // From detection step
    val broadcastHotKeys = spark.sparkContext.broadcast(hotKeys)

    // Add salt to both sides
    val saltedUsers = users.withColumn("salt",
      when($"user_id".isin(broadcastHotKeys.value.toSeq: _*),
        (rand() * 10).cast("int"))  // Salt hot keys (0-9)
      .otherwise(lit(0))  // No salt for regular keys
    ).withColumn("salted_key",
      when($"salt" > 0,
        concat($"user_id", lit("_"), $"salt"))
      .otherwise($"user_id".cast("string"))
    )

    val saltedOrders = orders.withColumn("salt",
      when($"user_id".isin(broadcastHotKeys.value.toSeq: _*),
        (rand() * 10).cast("int"))
      .otherwise(lit(0))
    ).withColumn("salted_key",
      when($"salt" > 0,
        concat($"user_id", lit("_"), $"salt"))
      .otherwise($"user_id".cast("string"))
    )

    // Join on salted key
    val result = saltedOrders.join(saltedUsers, "salted_key")

    /**
     * How salting works:
     *
     * Before salting:
     *   user_id=12345 → Reducer 1 (500,000 orders)
     *
     * After salting:
     *   user_id=12345_0 → Reducer 1 (50,000 orders)
     *   user_id=12345_1 → Reducer 2 (50,000 orders)
     *   ...
     *   user_id=12345_9 → Reducer 10 (50,000 orders)
     *
     * Result: Hot key distributed across 10 reducers
     *         10x speedup!
     */
  }

  /**
   * Strategy 2: Two-phase join (Hybrid approach)
   *   Phase 1: Join non-skewed keys (reduce-side)
   *   Phase 2: Join skewed keys separately (broadcast)
   */
  def twoPhaseJoin(spark: SparkSession): Unit = {
    import spark.implicits._

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Identify hot keys
    val hotKeys = Set(12345, 67890)
    val broadcastHotKeys = spark.sparkContext.broadcast(hotKeys)

    // Phase 1: Join non-skewed keys (regular join)
    val regularUsers = users.filter(!$"user_id".isin(broadcastHotKeys.value.toSeq: _*))
    val regularOrders = orders.filter(!$"user_id".isin(broadcastHotKeys.value.toSeq: _*))
    val regularResult = regularOrders.join(regularUsers, "user_id")

    // Phase 2: Join skewed keys (broadcast)
    val hotUsers = users.filter($"user_id".isin(broadcastHotKeys.value.toSeq: _*))
    val hotOrders = orders.filter($"user_id".isin(broadcastHotKeys.value.toSeq: _*))
    val hotResult = hotOrders.join(broadcast(hotUsers), "user_id")

    // Union results
    val result = regularResult.union(hotResult)

    /**
     * Benefits:
     *   - Regular keys: Fast reduce-side join (99% of data)
     *   - Hot keys: Broadcast join (1% of data, but takes 90% of time)
     *   - Total time: Much faster than pure reduce-side join
     */
  }

  /**
   * Strategy 3: Adaptive query execution (Spark 3.0+)
   *   Spark automatically detects and handles skew
   */
  def adaptiveQueryExecution(spark: SparkSession): Unit = {
    // Enable AQE
    spark.conf.set("spark.sql.adaptive.enabled", "true")
    spark.conf.set("spark.sql.adaptive.skewJoin.enabled", "true")
    spark.conf.set("spark.sql.adaptive.skewJoin.skewedPartitionFactor", "5")
    spark.conf.set("spark.sql.adaptive.skewJoin.skewedPartitionThresholdInBytes", "256MB")

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark automatically handles skew!
    val result = orders.join(users, "user_id")

    /**
     * AQE automatically:
     *   1. Detects skewed partitions during execution
     *   2. Splits skewed partition into multiple sub-partitions
     *   3. Processes sub-partitions in parallel
     *   4. No code changes needed!
     *
     * Example:
     *   Partition 3 is skewed (10GB)
     *   → Split into 10 sub-partitions (1GB each)
     *   → Process in parallel
     *   → 10x speedup
     */
  }

  /**
   * Strategy 4: Partitioning by range (for sorted data)
   */
  def rangePartitioning(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Partition by user_id range
    val partitionedUsers = users.repartitionByRange(100, $"user_id")
    val partitionedOrders = orders.repartitionByRange(100, $"user_id")

    // Join with same partitioning
    val result = partitionedOrders.join(partitionedUsers, "user_id")

    /**
     * Benefits:
     *   - Range partitioning distributes keys evenly
     *   - No shuffle if both sides use same partitioning
     *   - Good for sorted data (e.g., time-series)
     *
     * Trade-off:
     *   - Initial repartitioning cost
     *   - Best for multiple joins on same key
     */
  }
}
```

---

## Join Types & Strategies

🏗️ **ARCHITECT**: Choosing the right join type and strategy.

### Join Types

```sql
/**
 * Different join types and their use cases
 */

-- 1. INNER JOIN (default)
--    Return only matching rows from both sides
SELECT o.*, u.*
FROM orders o
INNER JOIN users u ON o.user_id = u.user_id;
-- Use case: Only orders with valid users

-- 2. LEFT OUTER JOIN
--    Return all rows from left, nulls for non-matching right
SELECT o.*, u.*
FROM orders o
LEFT JOIN users u ON o.user_id = u.user_id;
-- Use case: All orders, even if user deleted

-- 3. RIGHT OUTER JOIN
--    Return all rows from right, nulls for non-matching left
SELECT o.*, u.*
FROM orders o
RIGHT JOIN users u ON o.user_id = u.user_id;
-- Use case: All users, even if no orders

-- 4. FULL OUTER JOIN
--    Return all rows from both sides
SELECT o.*, u.*
FROM orders o
FULL OUTER JOIN users u ON o.user_id = u.user_id;
-- Use case: All orders and all users (rare)

-- 5. LEFT SEMI JOIN
--    Return rows from left that have match in right (no right columns)
SELECT o.*
FROM orders o
LEFT SEMI JOIN users u ON o.user_id = u.user_id;
-- Use case: Filter orders by user existence (like EXISTS)

-- 6. LEFT ANTI JOIN
--    Return rows from left that DON'T have match in right
SELECT o.*
FROM orders o
LEFT ANTI JOIN users u ON o.user_id = u.user_id;
-- Use case: Orphaned orders (users deleted)

-- 7. CROSS JOIN (Cartesian product)
--    Every row from left × every row from right
SELECT o.*, u.*
FROM orders o
CROSS JOIN users u;
-- Use case: Combinations (RARELY used - expensive!)
```

### Join Strategy Selection

```scala
object JoinStrategySelection {

  /**
   * Decision matrix for join strategy
   */
  def chooseStrategy(leftSize: Long, rightSize: Long): String = {
    (leftSize, rightSize) match {
      // Both small (< 1GB)
      case (l, r) if l < 1e9 && r < 1e9 =>
        "Broadcast both → Cartesian join (if needed)"

      // One small, one large
      case (l, r) if l < 1e8 =>  // Left < 100MB
        "Broadcast left → Map-side join"

      case (l, r) if r < 1e8 =>  // Right < 100MB
        "Broadcast right → Map-side join"

      // Both large
      case (l, r) =>
        "Reduce-side join (sort-merge)"
    }
  }

  /**
   * Spark's join strategy selection (simplified)
   */
  def sparkJoinStrategy(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark decides strategy based on statistics
    val result = orders.join(users, "user_id")

    result.explain(true)

    /**
     * Physical plan shows strategy:
     *
     * == Physical Plan ==
     * *(5) SortMergeJoin [user_id#0], [user_id#5]
     * :- *(2) Sort [user_id#0 ASC NULLS FIRST]
     * :  +- Exchange hashpartitioning(user_id#0, 200)
     * :     +- *(1) FileScan parquet [...]
     * +- *(4) Sort [user_id#5 ASC NULLS FIRST]
     *    +- Exchange hashpartitioning(user_id#5, 200)
     *       +- *(3) FileScan parquet [...]
     *
     * Strategy: SortMergeJoin (reduce-side)
     *
     * Alternative with broadcast:
     * == Physical Plan ==
     * *(2) BroadcastHashJoin [user_id#0], [user_id#5]
     * :- *(2) FileScan parquet [...]
     * +- BroadcastExchange HashedRelationBroadcastMode [...]
     *    +- *(1) FileScan parquet [...]
     *
     * Strategy: BroadcastHashJoin (map-side)
     */
  }

  /**
   * Force strategy with hints
   */
  def forceStrategy(spark: SparkSession): Unit = {
    import org.apache.spark.sql.functions._

    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Force broadcast join
    val broadcastResult = orders.join(broadcast(users), "user_id")

    // Force sort-merge join
    val sortMergeResult = orders.hint("merge").join(users.hint("merge"), "user_id")

    // Force shuffle hash join
    val shuffleHashResult = orders.hint("shuffle_hash")
      .join(users.hint("shuffle_hash"), "user_id")

    /**
     * Hints override Spark's default strategy
     * Use when you know better than optimizer
     * (e.g., statistics are wrong)
     */
  }
}
```

---

## Optimization Techniques

🏗️ **ARCHITECT**: Advanced techniques for faster joins.

### Predicate Pushdown

```scala
/**
 * Push filters before join to reduce data volume
 */
object PredicatePushdown {

  def withoutPushdown(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")  // 1TB
    val orders = spark.read.parquet("/data/orders")  // 10TB

    // BAD: Join then filter
    val result = orders.join(users, "user_id")
      .filter($"country" === "US")  // Filter after join

    /**
     * Cost:
     *   - Join 1TB + 10TB = 11TB shuffled
     *   - Then filter to 1TB
     *   - Wasted: 10TB shuffle
     */
  }

  def withPushdown(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
      .filter($"country" === "US")  // Filter BEFORE join (100GB)

    val orders = spark.read.parquet("/data/orders")

    // GOOD: Filter then join
    val result = orders.join(users, "user_id")

    /**
     * Cost:
     *   - Filter users: 1TB → 100GB
     *   - Join 100GB + 10TB = 10.1TB shuffled
     *   - Saved: 900GB shuffle
     */
  }

  def automaticPushdown(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark automatically pushes predicates
    val result = orders.join(users, "user_id")
      .filter($"country" === "US")  // Spark pushes before join!

    /**
     * Catalyst optimizer rewrites query:
     *   join(orders, users).filter(country = "US")
     *   → join(orders, users.filter(country = "US"))
     *
     * No code changes needed!
     */
  }
}
```

### Column Pruning

```scala
/**
 * Read only needed columns to reduce I/O
 */
object ColumnPruning {

  def withoutPruning(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    // users schema: (user_id, name, email, phone, address, country, ...)
    // 20 columns, 1TB total

    val orders = spark.read.parquet("/data/orders")

    // BAD: Read all columns
    val result = orders.join(users, "user_id")
      .select("order_id", "user_id", "name", "amount")

    /**
     * Cost:
     *   - Read 1TB (all columns from users)
     *   - Shuffle 1TB
     *   - But only use 2 columns (user_id, name)!
     */
  }

  def withPruning(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
      .select("user_id", "name")  // Select BEFORE join (50GB)

    val orders = spark.read.parquet("/data/orders")
      .select("order_id", "user_id", "amount")

    // GOOD: Read only needed columns
    val result = orders.join(users, "user_id")

    /**
     * Cost:
     *   - Read 50GB (2 columns from users)
     *   - Shuffle 50GB
     *   - Saved: 950GB I/O
     */
  }

  def automaticPruning(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // Spark automatically prunes columns
    val result = orders.join(users, "user_id")
      .select("order_id", "user_id", "name", "amount")

    /**
     * Catalyst optimizer:
     *   - Analyzes query
     *   - Identifies needed columns
     *   - Pushes projection to data source
     *   - Parquet reads only those columns
     *
     * No code changes needed!
     */
  }
}
```

---

## Real-World Patterns

🏗️ **ARCHITECT**: Common join patterns in production.

### Pattern 1: Star Schema Join

```scala
/**
 * Fact table joined with multiple dimension tables
 */
object StarSchemaJoin {

  def starSchemaExample(spark: SparkSession): Unit = {
    // Fact table (large)
    val sales = spark.read.parquet("/data/sales")  // 10TB
    // Schema: (sale_id, product_id, customer_id, store_id, date_id, amount)

    // Dimension tables (small)
    val products = spark.read.parquet("/data/products")  // 100MB
    val customers = spark.read.parquet("/data/customers")  // 1GB
    val stores = spark.read.parquet("/data/stores")  // 10MB
    val dates = spark.read.parquet("/data/dates")  // 1MB

    // Join fact with dimensions (broadcast all dimensions)
    val result = sales
      .join(broadcast(products), "product_id")
      .join(broadcast(customers), "customer_id")
      .join(broadcast(stores), "store_id")
      .join(broadcast(dates), "date_id")

    /**
     * Strategy: Broadcast all dimension tables
     *   - Total broadcast: 100MB + 1GB + 10MB + 1MB ≈ 1.1GB
     *   - No shuffle of fact table (10TB)
     *   - Fast!
     *
     * Why this works:
     *   - Dimensions are small (typical in star schema)
     *   - Fact table stays partitioned
     *   - All joins are map-side
     */
  }
}
```

### Pattern 2: Incremental Join

```scala
/**
 * Join only new data (delta) with historical data
 */
object IncrementalJoin {

  def incrementalJoinExample(spark: SparkSession): Unit = {
    // Historical data (large, unchanging)
    val historicalOrders = spark.read.parquet("/data/orders/historical")  // 10TB

    // Today's orders (small)
    val todayOrders = spark.read.parquet("/data/orders/today")  // 10GB

    // Users (small, updated daily)
    val users = spark.read.parquet("/data/users")  // 1GB

    // Join today's orders only (not historical)
    val enrichedToday = todayOrders.join(broadcast(users), "user_id")

    // Union with historical (no join needed!)
    val allOrders = historicalOrders.union(enrichedToday)

    /**
     * Benefits:
     *   - Join 10GB (today) instead of 10TB (all)
     *   - 1000x less data processed
     *   - Reuse historical results (cached)
     */
  }
}
```

### Pattern 3: Self-Join (Find Duplicates)

```scala
/**
 * Join table with itself to find relationships
 */
object SelfJoin {

  def findDuplicates(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")

    // Find users with same email (duplicates)
    val duplicates = users.as("u1")
      .join(users.as("u2"),
        $"u1.email" === $"u2.email" && $"u1.user_id" < $"u2.user_id")
      .select($"u1.user_id".as("user_id_1"), $"u2.user_id".as("user_id_2"), $"u1.email")

    /**
     * Pitfall: Expensive (Cartesian product!)
     *
     * Better approach: Group by email
     */
    val betterApproach = users
      .groupBy("email")
      .agg(collect_list("user_id").as("user_ids"))
      .filter(size($"user_ids") > 1)

    /**
     * Why better:
     *   - No join (group + aggregate)
     *   - O(n) instead of O(n²)
     *   - Much faster
     */
  }
}
```

---

## Anti-Patterns & Pitfalls

🎓 **PROFESSOR**: Common mistakes and how to avoid them.

### Anti-Pattern 1: Cartesian Product

```scala
/**
 * NEVER do cross join on large datasets
 */
object CartesianAntiPattern {

  def badCrossJoin(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")  // 1M rows
    val products = spark.read.parquet("/data/products")  // 1M rows

    // DISASTER: Cartesian product
    val result = users.crossJoin(products)  // 1M × 1M = 1 TRILLION rows!

    /**
     * Cost:
     *   - 1 trillion rows generated
     *   - Network: TBs of shuffle
     *   - Time: Hours or NEVER completes
     *   - Memory: OOM
     *
     * NEVER DO THIS!
     */
  }

  def fixCrossJoin(spark: SparkSession): Unit = {
    // If you really need combinations, add filter
    val users = spark.read.parquet("/data/users")
    val products = spark.read.parquet("/data/products")

    val result = users.crossJoin(products)
      .filter($"user.country" === $"product.country")  // Filter ASAP

    /**
     * Better: Use join with proper key
     */
    val better = users.join(products, $"user.country" === $"product.country")
  }
}
```

### Anti-Pattern 2: Multiple Sequential Joins

```scala
/**
 * Joining same tables multiple times
 */
object MultipleJoinsAntiPattern {

  def badMultipleJoins(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // BAD: Join users multiple times
    val orders1 = orders.filter($"amount" > 100)
      .join(users, "user_id")  // Join 1

    val orders2 = orders.filter($"amount" <= 100)
      .join(users, "user_id")  // Join 2 (reads users again!)

    val result = orders1.union(orders2)

    /**
     * Problem:
     *   - Users read twice
     *   - Users shuffled twice
     *   - Wasted resources
     */
  }

  def goodMultipleJoins(spark: SparkSession): Unit = {
    val users = spark.read.parquet("/data/users")
    val orders = spark.read.parquet("/data/orders")

    // GOOD: Join once, then filter
    val result = orders.join(users, "user_id")
      .withColumn("order_type",
        when($"amount" > 100, "high")
        .otherwise("low"))

    /**
     * Benefits:
     *   - Single join
     *   - Users read once
     *   - Much faster
     */
  }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Using Joins in System Design

```
Scenario: "Design a system to generate daily sales reports"
──────────────────────────────────────────────────────────

Requirements:
  - Join sales (10TB) with products, customers, stores
  - Generate reports: Sales by product, customer, region
  - Run nightly (results by 6am)

Design:
  ┌────────────────────────────────────────────────┐
  │  1. Data ingestion (throughout day)            │
  │     Sales → Kafka → S3 (Parquet)               │
  │                                                │
  │  2. ETL job (nightly at 2am)                   │
  │     a. Load sales (10TB)                       │
  │     b. Load dimensions (products, customers)   │
  │     c. Join using star schema pattern          │
  │     d. Aggregate to reports                    │
  │     e. Write to data warehouse                 │
  │                                                │
  │  3. BI tools query warehouse (6am+)            │
  └────────────────────────────────────────────────┘

Join Strategy:
  - Sales (10TB, fact table): Partitioned by date
  - Products (100MB): Broadcast join
  - Customers (1GB): Broadcast join
  - Stores (10MB): Broadcast join

  All dimension tables broadcasted (total 1.1GB)
  → Map-side joins only
  → No shuffle of 10TB sales
  → Fast (30 minutes with 100 nodes)

Deep Dive Questions:
  Q: What if customers table grows to 10GB?
  A: Still broadcast (10GB ok with 100 nodes × 10GB RAM)
     If > 50GB: Switch to reduce-side join

  Q: How to handle late-arriving data?
  A: - Partition sales by event_time (not processing_time)
     - Reprocess last 3 days with CDC (change data capture)
     - Idempotent writes (overwrite partitions)

  Q: What about data skew (Black Friday)?
  A: - Enable AQE (adaptive query execution)
     - Salt hot dates if needed
     - Allocate more resources for peak days
```

---

## 🧠 MIND MAP: DATA JOINS

```
      DATA JOINS
          |
     ┌────┼────┐
     ↓    ↓    ↓
  REDUCE MAP BROADCAST
   SIDE  SIDE  HASH
     |    |      |
  Shuffle Replicate Hash
  Both   Small  Table
  Sides  Dataset  |
     |      |    O(1)
  Slow   Fast  Lookup
  Works  Conditional
  Always
```

---

## 💡 EMOTIONAL ANCHORS

### 1. Reduce-Side Join = Sorting Mail 📮
- Collect all letters (shuffle)
- Sort by address (group by key)
- Deliver matching letters together
- Slow but handles any volume

### 2. Map-Side Join = Carrying Phone Book 📚
- Carry phone book (broadcast small table)
- For each person, lookup in book (no sorting needed)
- Fast! But book must fit in backpack

### 3. Data Skew = Concert Ticket Lines 🎫
- Regular fans: Short lines (fast)
- VIP entrance: Long line (bottleneck)
- Solution: Open multiple VIP lines (salting)

### 4. Broadcast Join = Cheat Sheet 📝
- Teacher gives cheat sheet to all students (broadcast)
- Each student uses own copy (no sharing)
- Fast! But sheet must be small

### 5. Shuffle = Moving Day 📦
- Pack belongings (serialize)
- Truck to new home (network transfer)
- Unpack (deserialize)
- Expensive! Avoid if possible

---

## 🔑 Key Takeaways

1. **Join strategy matters more than dataset size**
   - Same data, different strategy = 100x performance difference
   - Always consider broadcast join first

2. **Broadcast join when small dataset < 1GB**
   - 10-100x faster than reduce-side join
   - No shuffle = major win

3. **Data skew kills performance**
   - One slow task blocks entire job
   - Detect with Spark UI, mitigate with salting/AQE

4. **Optimize before joining**
   - Filter first (predicate pushdown)
   - Select needed columns only (column pruning)
   - Can reduce data by 10-100x

5. **Use DataFrames over RDDs**
   - Catalyst optimizer chooses best strategy
   - Automatic broadcast for small tables
   - 2-5x faster

6. **Avoid anti-patterns**
   - No Cartesian products on large data
   - No multiple sequential joins of same table
   - No collect() on large datasets

7. **Star schema is your friend**
   - Broadcast all dimension tables
   - Keep fact table partitioned
   - Standard pattern for analytics

8. **Monitor and tune**
   - Check Spark UI for skew
   - Tune broadcast threshold
   - Enable AQE for automatic optimization

**Final Thought**: Joins are the most expensive operation in batch processing. Master join optimization, and you'll make your jobs 10-100x faster. The key insight: avoid shuffling large datasets by broadcasting small ones. This one technique alone will solve 80% of join performance problems.
