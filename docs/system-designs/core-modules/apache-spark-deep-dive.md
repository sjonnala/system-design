# Apache Spark Deep Dive: In-Memory Computing & Fault Tolerance

## Contents

- [Apache Spark Deep Dive: In-Memory Computing & Fault Tolerance](#apache-spark-deep-dive-in-memory-computing--fault-tolerance)
    - [Core Mental Model](#core-mental-model)
    - [Why Spark? The MapReduce Problem](#why-spark-the-mapreduce-problem)
    - [RDD: Resilient Distributed Dataset](#rdd-resilient-distributed-dataset)
    - [Spark Execution Model](#spark-execution-model)
    - [Fault Tolerance: The Lineage Graph](#fault-tolerance-the-lineage-graph)
    - [Wide vs Narrow Dependencies](#wide-vs-narrow-dependencies)
    - [Spark vs MapReduce: The Showdown](#spark-vs-mapreduce-the-showdown)
    - [Spark Components & APIs](#spark-components--apis)
    - [Performance Optimization](#performance-optimization)
    - [When to Use Spark](#when-to-use-spark)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: APACHE SPARK](#mind-map-apache-spark)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Spark = MapReduce on steroids
        Fast, in-memory, flexible

The Key Insight:
┌────────────────────────────────────────────────────────────┐
│ MapReduce: Read from disk → Process → Write to disk        │
│            ↓                                                │
│            Slow! Every step hits disk I/O                   │
│                                                             │
│ Spark:     Read from disk → Process in memory → Output     │
│            ↓                                                │
│            Fast! Data stays in memory across steps          │
└────────────────────────────────────────────────────────────┘

Performance Difference:
──────────────────────
MapReduce: Iterative job (10 iterations)
  Read → Map → Reduce → Write → Read → Map → Reduce → Write → ...
  10 × disk I/O = SLOW (hours)

Spark: Iterative job (10 iterations)
  Read → [Map → Reduce → Map → Reduce → ...] → Write
         ↑──────── All in memory ────────────↑
  1 × disk read, 1 × disk write = FAST (minutes)

  Speedup: 10-100x faster for iterative workloads!

                  MAPREDUCE                         SPARK
            ┌─────────────────┐            ┌─────────────────┐
            │ ╔════╗          │            │ ╔════╗          │
            │ ║Disk║ ↔ Map    │            │ ║Disk║ → RAM   │
            │ ╚════╝ ↕         │            │ ╚════╝   ↓     │
            │ ╔════╗          │            │        Map      │
            │ ║Disk║ ↔ Reduce │            │         ↓      │
            │ ╚════╝          │            │       Reduce    │
            │                  │            │         ↓      │
            │  (Every step     │            │        Map     │
            │   hits disk)     │            │         ↓      │
            └─────────────────┘            │       Output    │
                                            └─────────────────┘
```

**Spark's Secret Sauce:**
```
1. RDD (Resilient Distributed Dataset)
   - Immutable, distributed collection
   - Lazy evaluation (build computation graph)
   - Automatic parallelization

2. In-Memory Computing
   - Cache data in RAM across operations
   - Avoid repeated disk I/O
   - 100x faster for iterative algorithms

3. Lineage-based Fault Tolerance
   - Don't checkpoint intermediate data
   - If partition lost → recompute from lineage
   - Faster recovery than replication

4. Rich APIs
   - Not just map and reduce
   - 80+ high-level operations
   - SQL, streaming, ML, graph processing
```

---

## Why Spark? The MapReduce Problem

🎓 **PROFESSOR**: MapReduce is slow for iterative algorithms. Here's why.

### The Iterative Algorithm Problem

```python
"""
Problem: Machine Learning needs iteration
"""

def machine_learning_with_mapreduce():
    """
    Example: Logistic Regression (10 iterations)

    Algorithm:
      1. Initialize weights
      2. FOR i = 1 to 10:
           a. Compute gradient (MapReduce job)
           b. Update weights
      3. Return final weights
    """

    # MapReduce implementation (pseudocode)
    weights = initialize_weights()

    for iteration in range(10):
        # Launch MapReduce job to compute gradient
        job = Job("compute_gradient")
        job.setInputPath("/data/training_data")      # Read from HDFS
        job.setOutputPath(f"/temp/gradient_{iteration}")  # Write to HDFS

        # Wait for job to complete
        job.waitForCompletion()

        # Read result from HDFS
        gradient = read_from_hdfs(f"/temp/gradient_{iteration}")

        # Update weights
        weights = weights - learning_rate * gradient

    """
    Problem: Each iteration...
      1. Reads entire dataset from HDFS (1TB)
      2. Computes gradient
      3. Writes result to HDFS
      4. Next iteration repeats

    Cost per iteration:
      - Read 1TB from disk
      - Write 1MB to disk
      - Total I/O: 10TB reads + 10MB writes

    Time: 10 iterations × 1 hour = 10 hours
    """


def machine_learning_with_spark():
    """
    Same algorithm with Spark
    """
    from pyspark import SparkContext

    sc = SparkContext()

    # Load data into RDD
    training_data = sc.textFile("/data/training_data")

    # CACHE IN MEMORY! (key difference)
    training_data.cache()

    weights = initialize_weights()

    for iteration in range(10):
        # Compute gradient (uses cached data in memory)
        gradient = training_data.map(lambda x: compute_gradient(x, weights)) \
                                 .reduce(lambda a, b: a + b)

        # Update weights
        weights = weights - learning_rate * gradient

    """
    Spark approach:
      1. Read dataset from HDFS once (1TB)
      2. Cache in memory across cluster
      3. Each iteration reads from memory (fast!)

    Cost per iteration:
      - Initial read: 1TB from disk
      - Iterations 1-10: Read from RAM
      - Total I/O: 1TB read

    Time: Initial load (10 min) + 10 iterations × 1 min = 20 minutes

    Speedup: 10 hours → 20 minutes = 30x faster!
    """
```

### The Interactive Query Problem

```scala
/**
 * Problem: Exploratory data analysis
 */
object InteractiveAnalysis {

  def withMapReduce(): Unit = {
    """
    Data analyst workflow:
      1. Query 1: Count records → Launch MapReduce job → Wait 5 min
      2. Query 2: Filter errors → Launch MapReduce job → Wait 5 min
      3. Query 3: Group by user → Launch MapReduce job → Wait 5 min

    Total time: 15 minutes for 3 queries
    Problem: Can't iterate quickly!
    """
  }

  def withSpark(): Unit = {
    val spark = SparkSession.builder().getOrCreate()

    // Load once
    val logs = spark.read.text("/data/logs").cache()

    // Query 1: Count (instant - data cached)
    val count = logs.count()  // 1 second

    // Query 2: Filter (instant - data cached)
    val errors = logs.filter($"value".contains("ERROR")).count()  // 1 second

    // Query 3: Group (instant - data cached)
    val byUser = logs.groupBy($"user").count()  // 2 seconds

    """
    Total time: 5 seconds for 3 queries
    Speedup: 15 minutes → 5 seconds = 180x faster!

    Why? Data loaded once, cached in memory, queries reuse cached data
    """
  }
}
```

### The Multi-Step Pipeline Problem

```
Problem: Complex pipelines with multiple MapReduce jobs
───────────────────────────────────────────────────────

Example: Web analytics pipeline

MapReduce (5 jobs):
  Job 1: Parse logs            → Write to HDFS (1TB)
  Job 2: Filter bots           → Write to HDFS (800GB)
  Job 3: Sessionize users      → Write to HDFS (500GB)
  Job 4: Join with user data   → Write to HDFS (600GB)
  Job 5: Aggregate metrics     → Write to HDFS (1GB)

  Total I/O: 5 reads + 5 writes = 5TB reads + 2.9TB writes
  Time: 5 jobs × 30 min = 2.5 hours

Spark (1 job, pipelined):
  Parse → Filter → Sessionize → Join → Aggregate → Output

  Total I/O: 1 read + 1 write = 1TB read + 1GB write
  Time: 15 minutes

  Speedup: 2.5 hours → 15 minutes = 10x faster!
```

---

## RDD: Resilient Distributed Dataset

🏗️ **ARCHITECT**: RDD is Spark's fundamental abstraction.

### What is an RDD?

```scala
/**
 * RDD = Immutable, distributed collection of objects
 */

// Example: RDD of integers
val numbers: RDD[Int] = sc.parallelize(Seq(1, 2, 3, 4, 5))

// Example: RDD of strings (from file)
val lines: RDD[String] = sc.textFile("/data/log.txt")

/**
 * Key properties:
 *
 * 1. Resilient: Automatic fault tolerance via lineage
 * 2. Distributed: Data split across cluster
 * 3. Dataset: Collection of objects (any type)
 * 4. Immutable: Can't modify, only create new RDDs
 * 5. Lazy: Operations not executed until action
 * 6. Typed: Strong typing (RDD[Int], RDD[String], etc.)
 */
```

### RDD Partitions

```
RDD Partitioning:
────────────────

RDD divided into partitions (chunks)
Each partition processed on one node (parallel)

Example: RDD with 1 million integers

    ┌──────────────────────────────────────┐
    │           RDD[Int]                   │
    │      (1 million integers)            │
    └───────────────┬──────────────────────┘
                    │
         ┌──────────┼──────────┐
         ↓          ↓          ↓
    ┌────────┐ ┌────────┐ ┌────────┐
    │ Part 0 │ │ Part 1 │ │ Part 2 │
    │ [1..   │ │[334K.. │ │[667K.. │
    │ 333K]  │ │ 666K]  │ │  1M]   │
    └────────┘ └────────┘ └────────┘
        ↓          ↓          ↓
    Node 1     Node 2     Node 3

Each node processes its partition independently (parallel!)

Number of partitions:
  - Default: Number of blocks in input file (HDFS)
  - Or: Explicitly set with repartition()
  - Or: Controlled via sc.defaultParallelism

  Example:
    val rdd = sc.textFile("/data/file.txt", numPartitions=100)
```

### Transformations vs Actions

```scala
/**
 * Two types of operations on RDDs
 */

// TRANSFORMATIONS: Create new RDD (lazy)
// ─────────────────────────────────────
val rdd1 = sc.parallelize(1 to 10)

// map: Transform each element
val rdd2 = rdd1.map(x => x * 2)
// [1,2,3,4,5,6,7,8,9,10] → [2,4,6,8,10,12,14,16,18,20]

// filter: Select elements
val rdd3 = rdd2.filter(x => x > 10)
// [2,4,6,8,10,12,14,16,18,20] → [12,14,16,18,20]

// flatMap: Map then flatten
val words = sc.parallelize(Seq("hello world", "spark is fast"))
val wordsRdd = words.flatMap(line => line.split(" "))
// ["hello world", "spark is fast"] → ["hello","world","spark","is","fast"]

// NOTHING EXECUTED YET! (lazy evaluation)
// Spark just builds computation graph


// ACTIONS: Trigger computation, return result
// ────────────────────────────────────────────
val count = rdd3.count()  // Execute! Returns 5

val first = rdd3.first()  // Execute! Returns 12

val all = rdd3.collect()  // Execute! Returns [12,14,16,18,20]

val sum = rdd3.reduce((a, b) => a + b)  // Execute! Returns 80


/**
 * Why lazy evaluation?
 *
 * 1. Optimization: Spark can optimize entire pipeline
 *    Example: filter().map() → Spark can combine
 *
 * 2. Avoid unnecessary work
 *    Example: rdd.map().filter().first()
 *            → Only process until first element found
 *
 * 3. Pipelining: Multiple transformations without intermediate storage
 */
```

### Common Transformations

```scala
object RDDTransformations {

  /**
   * 1. map(f): Apply function to each element
   */
  val numbers = sc.parallelize(1 to 5)
  val squared = numbers.map(x => x * x)  // [1,4,9,16,25]


  /**
   * 2. filter(f): Select elements matching predicate
   */
  val evens = numbers.filter(x => x % 2 == 0)  // [2,4]


  /**
   * 3. flatMap(f): Map then flatten
   */
  val nested = sc.parallelize(Seq(Seq(1,2), Seq(3,4)))
  val flattened = nested.flatMap(x => x)  // [1,2,3,4]


  /**
   * 4. reduceByKey(f): Aggregate by key
   */
  val pairs = sc.parallelize(Seq(("a",1), ("b",1), ("a",1)))
  val counts = pairs.reduceByKey((a,b) => a + b)  // [("a",2), ("b",1)]


  /**
   * 5. groupByKey(): Group values by key
   */
  val grouped = pairs.groupByKey()
  // [("a", [1,1]), ("b", [1])]
  // WARNING: Can cause OOM if values too large!


  /**
   * 6. join(): Join two RDDs by key
   */
  val rdd1 = sc.parallelize(Seq(("a",1), ("b",2)))
  val rdd2 = sc.parallelize(Seq(("a",3), ("c",4)))
  val joined = rdd1.join(rdd2)
  // [("a", (1,3))]


  /**
   * 7. union(): Combine two RDDs
   */
  val rdd3 = sc.parallelize(1 to 3)
  val rdd4 = sc.parallelize(4 to 6)
  val combined = rdd3.union(rdd4)  // [1,2,3,4,5,6]


  /**
   * 8. distinct(): Remove duplicates
   */
  val duplicates = sc.parallelize(Seq(1,2,2,3,3,3))
  val unique = duplicates.distinct()  // [1,2,3]


  /**
   * 9. sortBy(f): Sort elements
   */
  val unsorted = sc.parallelize(Seq(3,1,4,1,5,9,2,6))
  val sorted = unsorted.sortBy(x => x)  // [1,1,2,3,4,5,6,9]


  /**
   * 10. repartition(n): Change number of partitions
   */
  val repartitioned = numbers.repartition(10)  // 10 partitions
}
```

### Common Actions

```scala
object RDDActions {

  val numbers = sc.parallelize(1 to 10)

  /**
   * 1. collect(): Return all elements to driver
   */
  val all: Array[Int] = numbers.collect()  // [1,2,3,4,5,6,7,8,9,10]
  // WARNING: OOM if RDD too large!


  /**
   * 2. count(): Count elements
   */
  val count: Long = numbers.count()  // 10


  /**
   * 3. first(): Return first element
   */
  val first: Int = numbers.first()  // 1


  /**
   * 4. take(n): Return first n elements
   */
  val firstFive: Array[Int] = numbers.take(5)  // [1,2,3,4,5]


  /**
   * 5. reduce(f): Aggregate elements
   */
  val sum: Int = numbers.reduce((a,b) => a + b)  // 55


  /**
   * 6. fold(zero)(f): Reduce with initial value
   */
  val product: Int = numbers.fold(1)((a,b) => a * b)  // 3628800


  /**
   * 7. aggregate(zero)(seqOp, combOp): Advanced aggregation
   */
  val (sum, count) = numbers.aggregate((0,0))(
    (acc, x) => (acc._1 + x, acc._2 + 1),  // Sequential op
    (acc1, acc2) => (acc1._1 + acc2._1, acc1._2 + acc2._2)  // Combine op
  )
  // (55, 10)


  /**
   * 8. foreach(f): Apply function to each element (side effects)
   */
  numbers.foreach(x => println(x))


  /**
   * 9. saveAsTextFile(path): Write to HDFS/S3
   */
  numbers.saveAsTextFile("/output/numbers")


  /**
   * 10. countByKey(): Count occurrences of each key
   */
  val pairs = sc.parallelize(Seq(("a",1), ("b",1), ("a",1)))
  val counts: Map[String, Long] = pairs.countByKey()
  // Map("a" -> 2, "b" -> 1)
}
```

---

## Spark Execution Model

🏗️ **ARCHITECT**: How Spark executes jobs under the hood.

### Architecture

```
Spark Cluster Architecture:
──────────────────────────

┌────────────────────────────────────────────────────────┐
│                    DRIVER PROGRAM                      │
│  ┌──────────────────────────────────────────────────┐ │
│  │  SparkContext                                     │ │
│  │  - Creates RDDs                                   │ │
│  │  - Builds DAG (Directed Acyclic Graph)           │ │
│  │  - Schedules tasks                                │ │
│  └───────────────────┬──────────────────────────────┘ │
└────────────────────────┼───────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         ↓                                ↓
┌──────────────────┐            ┌──────────────────┐
│ CLUSTER MANAGER  │            │                  │
│  (YARN / Mesos)  │            │                  │
│                  │            │                  │
└────────┬─────────┘            │                  │
         │                      │                  │
    ┌────┴────────────┐         │                  │
    ↓                 ↓         ↓                  ↓
┌─────────┐      ┌─────────┐ ┌─────────┐    ┌─────────┐
│EXECUTOR1│      │EXECUTOR2│ │EXECUTOR3│    │EXECUTOR4│
│ Task 1  │      │ Task 2  │ │ Task 3  │    │ Task 4  │
│ Task 5  │      │ Task 6  │ │ Task 7  │    │ Task 8  │
│ Cache   │      │ Cache   │ │ Cache   │    │ Cache   │
└─────────┘      └─────────┘ └─────────┘    └─────────┘
  Node 1           Node 2      Node 3         Node 4

Components:
  Driver: Runs main() function, creates SparkContext
  Executors: Run tasks, store data for caching
  Cluster Manager: Allocate resources (YARN, Mesos, Standalone)
```

### Job Execution Flow

```python
"""
Step-by-step: How Spark executes a job
"""

# User code
from pyspark import SparkContext

sc = SparkContext()

# 1. Create RDD (no execution yet)
lines = sc.textFile("/data/logs.txt")

# 2. Transformations (build DAG, no execution)
errors = lines.filter(lambda x: "ERROR" in x)
warnings = lines.filter(lambda x: "WARN" in x)

# 3. Action triggers execution
error_count = errors.count()  # ← THIS TRIGGERS EXECUTION!

"""
Execution flow:
──────────────

STEP 1: Driver builds DAG (Directed Acyclic Graph)
───────────────────────────────────────────────────
  textFile("/data/logs.txt")
      ↓
    filter(contains "ERROR")
      ↓
    count()

STEP 2: DAGScheduler divides DAG into stages
─────────────────────────────────────────────
  Stage 1:
    - textFile → filter → count
    - Narrow dependencies only
    - Can pipeline!

  (If shuffle needed, would create multiple stages)

STEP 3: TaskScheduler creates tasks
────────────────────────────────────
  Input file has 4 partitions
  → Create 4 tasks (one per partition)

  Task 1: Process partition 0
  Task 2: Process partition 1
  Task 3: Process partition 2
  Task 4: Process partition 3

STEP 4: Tasks sent to executors
────────────────────────────────
  Task 1 → Executor 1
  Task 2 → Executor 2
  Task 3 → Executor 3
  Task 4 → Executor 4

STEP 5: Executors execute tasks
────────────────────────────────
  Each executor:
    1. Reads its partition from HDFS
    2. Applies filter
    3. Counts matching lines
    4. Returns count to driver

STEP 6: Driver aggregates results
──────────────────────────────────
  Driver receives: [100, 200, 150, 50]
  Aggregates: 100 + 200 + 150 + 50 = 500
  Returns to user: error_count = 500
"""
```

### DAG (Directed Acyclic Graph)

```
Example: Word count DAG
──────────────────────

Code:
  val lines = sc.textFile("/data/text.txt")
  val words = lines.flatMap(_.split(" "))
  val pairs = words.map(w => (w, 1))
  val counts = pairs.reduceByKey(_ + _)
  counts.saveAsTextFile("/output")

DAG:
  textFile("/data/text.txt")    ← RDD 1
       ↓ (narrow dependency)
  flatMap(split)                 ← RDD 2
       ↓ (narrow dependency)
  map(w => (w,1))                ← RDD 3
       ↓ (WIDE dependency - shuffle!)
  reduceByKey(_ + _)             ← RDD 4
       ↓ (narrow dependency)
  saveAsTextFile()

Stages (divided at shuffle boundaries):
  Stage 1:
    textFile → flatMap → map
    (All narrow dependencies, can pipeline)

  Stage 2:
    reduceByKey → saveAsTextFile
    (After shuffle)

Why stages?
  - Narrow dependencies: Can pipeline in single stage
  - Wide dependencies: Must shuffle → new stage
```

---

## Fault Tolerance: The Lineage Graph

🎓 **PROFESSOR**: Spark's most clever design decision - lineage-based fault tolerance.

### The Problem with Replication

```
Traditional approach: Checkpoint intermediate data
──────────────────────────────────────────────────

Example: Multi-step pipeline
  RDD1 → RDD2 → RDD3 → RDD4 → RDD5

Traditional approach:
  1. Compute RDD2 → Replicate to 3 nodes (3x storage)
  2. Compute RDD3 → Replicate to 3 nodes (3x storage)
  3. Compute RDD4 → Replicate to 3 nodes (3x storage)
  4. Compute RDD5 → Output

  Cost:
    - 9x storage (3 RDDs × 3 replicas)
    - Network overhead (replication)
    - If node fails: Read from replica (fast recovery)

Problem:
  ✗ High storage cost
  ✗ Network overhead
  ✗ Slows down job (replication latency)
```

### Spark's Solution: Lineage

```
Spark approach: Remember how to recompute
─────────────────────────────────────────

Same pipeline:
  RDD1 → RDD2 → RDD3 → RDD4 → RDD5

Spark approach:
  1. Don't checkpoint RDD2, RDD3, RDD4
  2. Remember lineage (how each RDD was computed)
  3. If partition lost → recompute from lineage

  Cost:
    - 0x extra storage (no replication)
    - No network overhead
    - If node fails: Recompute lost partition (slower recovery)

Benefit:
  ✓ No storage overhead
  ✓ Faster normal execution
  ✗ Slower recovery (must recompute)

Trade-off: Optimize for common case (no failures)
```

### Lineage Example

```scala
/**
 * Lineage tracking in action
 */
object LineageExample {

  val sc = new SparkContext()

  // RDD 1: Load from HDFS
  val lines = sc.textFile("/data/logs.txt")
  // Lineage: textFile("/data/logs.txt")

  // RDD 2: Filter
  val errors = lines.filter(_.contains("ERROR"))
  // Lineage: textFile("/data/logs.txt").filter(contains "ERROR")

  // RDD 3: Map
  val timestamps = errors.map(extractTimestamp)
  // Lineage: textFile("/data/logs.txt")
  //           .filter(contains "ERROR")
  //           .map(extractTimestamp)

  // RDD 4: Reduce
  val count = timestamps.count()
  // Lineage: textFile("/data/logs.txt")
  //           .filter(contains "ERROR")
  //           .map(extractTimestamp)
  //           .count()

  /**
   * Fault tolerance scenario:
   *
   * 1. Task processing partition 5 of RDD 3 fails
   * 2. Spark looks at lineage: RDD3 = RDD2.map(extractTimestamp)
   * 3. RDD2 partition 5 still available? Use it.
   * 4. If not, look at RDD2 lineage: RDD2 = RDD1.filter(...)
   * 5. RDD1 partition 5 still available? Use it.
   * 6. If not, RDD1 = textFile(...) → Re-read from HDFS
   * 7. Apply filter → Apply map → Recompute partition 5
   * 8. Continue execution
   *
   * Key insight: Only recompute LOST partition, not entire RDD!
   */

  /**
   * View lineage
   */
  println(timestamps.toDebugString)
  /**
   * Output:
   * (8) MapPartitionsRDD[3] at map at <console>:25 []
   *  |  MapPartitionsRDD[2] at filter at <console>:23 []
   *  |  /data/logs.txt MapPartitionsRDD[1] at textFile at <console>:21 []
   *  |  /data/logs.txt HadoopRDD[0] at textFile at <console>:21 []
   *
   * This is the lineage graph!
   * Shows how to recompute from source
   */
}
```

### Lineage vs Checkpointing Trade-offs

```scala
object CheckpointingStrategy {

  /**
   * When to use checkpointing
   */
  def whenToCheckpoint(): Unit = {
    """
    Problem scenarios:

    1. Long lineage chains
       RDD1 → RDD2 → RDD3 → ... → RDD50
       → Recompute takes too long
       → Checkpoint intermediate RDDs (e.g., RDD10, RDD20, RDD30)

    2. Wide dependencies (shuffles)
       RDD1 → [SHUFFLE] → RDD2 → [SHUFFLE] → RDD3
       → Shuffle expensive to recompute
       → Checkpoint after shuffles

    3. Iterative algorithms (ML)
       FOR i = 1 to 1000:
         weights = weights.map(update)
       → Lineage grows unboundedly
       → Checkpoint every 10 iterations
    """

    // Example: Checkpoint long lineage
    val rdd1 = sc.textFile("/data/file.txt")
    val rdd2 = rdd1.map(...)
    // ... many transformations
    val rdd10 = rdd9.map(...)

    rdd10.checkpoint()  // Save to HDFS
    rdd10.count()       // Trigger checkpoint

    // Future operations use checkpoint (not lineage)
    val rdd11 = rdd10.map(...)  // If rdd11 fails, restore from checkpoint
  }

  /**
   * Checkpoint vs Cache
   */
  def checkpointVsCache(): Unit = {
    """
    Cache (persist):
      - Store in memory (RAM)
      - Lost if node fails
      - Still has lineage (can recompute)
      - Fast access
      - Use for: Repeated access, interactive queries

    Checkpoint:
      - Store to reliable storage (HDFS)
      - Survives node failures
      - Truncates lineage (can't recompute beyond checkpoint)
      - Slower access (disk I/O)
      - Use for: Fault tolerance, long lineages
    """

    val rdd = sc.textFile("/data/file.txt").map(...)

    // Cache: Fast but volatile
    rdd.cache()

    // Checkpoint: Slower but durable
    rdd.checkpoint()
  }
}
```

---

## Wide vs Narrow Dependencies

🏗️ **ARCHITECT**: Understanding dependencies is critical for Spark optimization.

### Narrow Dependencies

```
Narrow Dependency:
  Each partition of parent RDD used by AT MOST ONE partition of child RDD

Examples:
  - map
  - filter
  - union
  - mapPartitions

Visual:
  Parent RDD:  [P0] [P1] [P2] [P3]
                 ↓    ↓    ↓    ↓
  Child RDD:   [P0] [P1] [P2] [P3]

  Each child partition depends on exactly one parent partition

Benefits:
  ✓ No shuffle (data stays on same node)
  ✓ Fast
  ✓ Can pipeline multiple narrow operations
  ✓ Easy to recover from failure (recompute one partition)

Example:
  val rdd1 = sc.parallelize(1 to 10, 4)  // 4 partitions
  val rdd2 = rdd1.map(_ * 2)              // Narrow: map
  val rdd3 = rdd2.filter(_ > 10)          // Narrow: filter

  Execution:
    Task 1: P0: [1,2,3] → map → [2,4,6] → filter → [0]
    Task 2: P1: [4,5,6] → map → [8,10,12] → filter → [12]
    Task 3: P2: [7,8,9] → map → [14,16,18] → filter → [14,16,18]
    Task 4: P3: [10] → map → [20] → filter → [20]

  All pipelined in single stage! No data movement between nodes!
```

### Wide Dependencies

```
Wide Dependency:
  Multiple child partitions depend on same parent partition

Examples:
  - groupByKey
  - reduceByKey
  - join
  - sortBy
  - distinct

Visual:
  Parent RDD:  [P0] [P1] [P2] [P3]
                 ↓\  ↓\  ↓\  ↓\
                 ↓ \ ↓ \ ↓ \ ↓ \
                 ↓  \↓  \↓  \↓  \
  Child RDD:   [P0] [P1] [P2] [P3]

  Each child partition depends on ALL parent partitions

Requires SHUFFLE:
  1. Each parent partition writes data to temporary files (one per child partition)
  2. Child partitions fetch data from all parent partitions
  3. Network transfer (expensive!)

Example:
  val pairs = sc.parallelize(Seq(("a",1), ("b",2), ("a",3), ("b",4)), 2)
  val counts = pairs.reduceByKey(_ + _)

  Execution:
    STAGE 1 (Map-side):
      Task 1: P0: [("a",1), ("b",2)]
        Write to shuffle files:
          shuffle_0_0 (for reducer 0): ("a",1)
          shuffle_0_1 (for reducer 1): ("b",2)

      Task 2: P1: [("a",3), ("b",4)]
        Write to shuffle files:
          shuffle_1_0 (for reducer 0): ("a",3)
          shuffle_1_1 (for reducer 1): ("b",4)

    SHUFFLE:
      Network transfer of shuffle files

    STAGE 2 (Reduce-side):
      Task 1 (Reducer 0):
        Fetch shuffle_0_0, shuffle_1_0
        Read: ("a",1), ("a",3)
        Reduce: ("a", 4)

      Task 2 (Reducer 1):
        Fetch shuffle_0_1, shuffle_1_1
        Read: ("b",2), ("b",4)
        Reduce: ("b", 6)

  Result: [("a",4), ("b",6)]

Cost:
  ✗ Network I/O (shuffle data transfer)
  ✗ Disk I/O (write + read shuffle files)
  ✗ Serialization/deserialization
  ✗ Multiple stages (barrier between stages)
```

### Optimization: Avoid Wide Dependencies

```scala
object AvoidWideDependencies {

  /**
   * Example: Avoid groupByKey
   */
  def badApproach(): Unit = {
    val pairs = sc.parallelize(Seq(("a",1), ("a",2), ("b",3), ("b",4)))

    // BAD: groupByKey (wide dependency)
    val grouped = pairs.groupByKey()  // [("a", [1,2]), ("b", [3,4])]
    val sums = grouped.mapValues(_.sum)  // [("a", 3), ("b", 7)]

    /**
     * Problem:
     * 1. groupByKey shuffles ALL values
     * 2. If key "a" has 1M values → Shuffle 1M values
     * 3. Memory pressure on reducer
     * 4. Slow!
     */
  }

  def goodApproach(): Unit = {
    val pairs = sc.parallelize(Seq(("a",1), ("a",2), ("b",3), ("b",4)))

    // GOOD: reduceByKey (combines before shuffle)
    val sums = pairs.reduceByKey(_ + _)  // [("a", 3), ("b", 7)]

    /**
     * Benefit:
     * 1. Map-side combine (like combiner in MapReduce)
     * 2. Shuffle only aggregated values
     * 3. If key "a" has 1M values → Shuffle 1 value (sum)
     * 4. 1M times less data shuffled!
     * 5. Much faster!
     */
  }

  /**
   * General rule:
   *   groupByKey → mapValues(reduce)  ✗ BAD
   *   reduceByKey                     ✓ GOOD
   *
   *   groupByKey → mapValues(sum)     ✗ BAD
   *   reduceByKey(_ + _)              ✓ GOOD
   *
   *   groupByKey → mapValues(count)   ✗ BAD
   *   countByKey                      ✓ GOOD
   */
}
```

---

## Spark vs MapReduce: The Showdown

🎓 **PROFESSOR**: Let's compare them head-to-head.

### Performance Comparison

```
Benchmark: Iterative Algorithm (Logistic Regression, 100 iterations)
────────────────────────────────────────────────────────────────────

Dataset: 1TB training data

MapReduce:
  - 100 jobs (one per iteration)
  - Each job: Read 1TB from HDFS → Process → Write to HDFS
  - Total I/O: 100 × (1TB read + 1TB write) = 200TB I/O
  - Time: 10 hours

Spark (with caching):
  - 1 job
  - Load 1TB into memory (once)
  - 100 iterations in memory
  - Total I/O: 1TB read + 1GB write
  - Time: 20 minutes

Speedup: 30x

────────────────────────────────────────────────────────────────────

Benchmark: Interactive Queries
────────────────────────────────────────────────────────────────────

Dataset: 1TB web logs

Scenario: Run 10 ad-hoc queries

MapReduce:
  - 10 jobs
  - Each job reads 1TB from HDFS
  - Time: 10 × 5 min = 50 minutes

Spark (with caching):
  - Load 1TB into memory (once)
  - 10 queries on cached data
  - Time: 1 min (load) + 10 × 3 sec (query) = 1.5 minutes

Speedup: 33x

────────────────────────────────────────────────────────────────────

Benchmark: Multi-stage Pipeline
────────────────────────────────────────────────────────────────────

Pipeline: Parse → Filter → Join → Aggregate

MapReduce:
  - 4 jobs
  - Each job writes output to HDFS
  - Time: 4 × 30 min = 2 hours

Spark:
  - 1 job (pipelined)
  - No intermediate HDFS writes
  - Time: 15 minutes

Speedup: 8x
```

### Feature Comparison

```
┌─────────────────────────────────────────────────────────────────┐
│                  MAPREDUCE vs SPARK                             │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│ PROGRAMMING MODEL                                               │
│ MapReduce: map() and reduce() only                              │
│ Spark:     80+ operations (map, filter, join, groupBy, etc.)   │
│            → More expressive                                    │
│                                                                 │
│ EXECUTION                                                       │
│ MapReduce: Disk-based (write after each stage)                 │
│ Spark:     Memory-based (keep data in RAM)                     │
│            → 10-100x faster                                     │
│                                                                 │
│ FAULT TOLERANCE                                                 │
│ MapReduce: Re-execute failed tasks                             │
│ Spark:     Lineage-based recomputation                         │
│            → Less overhead                                      │
│                                                                 │
│ APIS                                                            │
│ MapReduce: Java only (Hadoop Streaming for others)             │
│ Spark:     Scala, Java, Python, R, SQL                         │
│            → More accessible                                    │
│                                                                 │
│ ITERATIVE ALGORITHMS                                            │
│ MapReduce: Slow (disk I/O per iteration)                       │
│ Spark:     Fast (in-memory iteration)                          │
│            → 10-100x faster for ML                             │
│                                                                 │
│ INTERACTIVE QUERIES                                             │
│ MapReduce: Not suitable (5+ min per query)                     │
│ Spark:     Excellent (seconds per query)                       │
│            → Enables exploratory analysis                       │
│                                                                 │
│ STREAMING                                                       │
│ MapReduce: Not supported                                        │
│ Spark:     Spark Streaming (micro-batches)                     │
│            → Unified batch + streaming                          │
│                                                                 │
│ ECOSYSTEM                                                       │
│ MapReduce: Hive, Pig, Mahout (separate tools)                  │
│ Spark:     Spark SQL, MLlib, GraphX (integrated)               │
│            → One engine for everything                          │
│                                                                 │
└─────────────────────────────────────────────────────────────────┘
```

### When to Use Each

```
Use MapReduce when:
  ✓ Very large datasets (PBs) that don't fit in memory
  ✓ One-pass algorithms (no iteration)
  ✓ Write-once, read-rarely workloads
  ✓ Existing MapReduce codebase
  ✓ Extreme fault tolerance needed (checkpoint everything)

Use Spark when:
  ✓ Iterative algorithms (ML, graph processing)
  ✓ Interactive queries (exploratory analysis)
  ✓ Multi-step pipelines
  ✓ Dataset fits in cluster memory (or can cache key parts)
  ✓ Need fast results
  ✓ Want unified API for batch, streaming, SQL, ML

Reality check:
  Most organizations moving from MapReduce to Spark
  Spark can run on YARN/HDFS (same infrastructure)
  Spark often 10x faster with same resources
  MapReduce still used for archival batch jobs
```

---

## Spark Components & APIs

🏗️ **ARCHITECT**: Spark is not just RDDs - it's a unified platform.

### Spark Stack

```
┌───────────────────────────────────────────────────────────┐
│                    APPLICATIONS                           │
│   BI Tools | Notebooks | Web Apps | Stream Processors    │
└───────────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────┐
│                     SPARK APIS                            │
│ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────────┐ │
│ │ Spark SQL│ │  MLlib   │ │ GraphX   │ │Spark Streams │ │
│ │(DataFrames│ │(Machine  │ │(Graph    │ │(Stream       │ │
│ │   SQL)   │ │Learning) │ │Processing)│ │Processing)   │ │
│ └──────────┘ └──────────┘ └──────────┘ └──────────────┘ │
└───────────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────┐
│                    SPARK CORE                             │
│        RDDs | Task Scheduling | Fault Tolerance           │
│        Memory Management | Shuffle | Caching              │
└───────────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────┐
│                  CLUSTER MANAGERS                         │
│         Standalone  |  YARN  |  Mesos  |  Kubernetes      │
└───────────────────────────┬───────────────────────────────┘
                            ↓
┌───────────────────────────────────────────────────────────┐
│                      STORAGE                              │
│         HDFS  |  S3  |  Cassandra  |  HBase  |  ...       │
└───────────────────────────────────────────────────────────┘
```

### Spark SQL & DataFrames

```scala
/**
 * DataFrames: Higher-level API than RDDs
 */
object SparkSQLExample {

  val spark = SparkSession.builder().getOrCreate()
  import spark.implicits._

  /**
   * Create DataFrame from JSON
   */
  val users = spark.read.json("/data/users.json")

  // DataFrame = RDD + Schema
  users.printSchema()
  /**
   * root
   *  |-- id: long (nullable = true)
   *  |-- name: string (nullable = true)
   *  |-- age: long (nullable = true)
   */

  /**
   * SQL queries
   */
  users.createOrReplaceTempView("users")
  val adults = spark.sql("SELECT * FROM users WHERE age >= 18")

  /**
   * DataFrame operations (similar to SQL)
   */
  val result = users
    .filter($"age" >= 18)
    .groupBy($"country")
    .agg(count("*").as("count"))
    .orderBy($"count".desc)

  /**
   * Why DataFrames > RDDs?
   *
   * 1. Optimizations:
   *    - Catalyst optimizer (query optimization)
   *    - Tungsten engine (code generation, memory management)
   *    - Predicate pushdown, column pruning
   *
   * 2. Performance:
   *    - 2-5x faster than RDDs (same operations)
   *    - Better memory efficiency
   *
   * 3. Interoperability:
   *    - SQL, Python, R, Scala (same API)
   *    - Connect to JDBC, Parquet, JSON, CSV, etc.
   *
   * 4. Ease of use:
   *    - Familiar SQL syntax
   *    - Automatic schema inference
   *    - Better error messages
   */
}
```

### Spark Streaming

```scala
/**
 * Process stream of data (micro-batches)
 */
object SparkStreamingExample {

  val spark = SparkSession.builder().getOrCreate()
  import spark.implicits._

  /**
   * Read from Kafka
   */
  val stream = spark.readStream
    .format("kafka")
    .option("kafka.bootstrap.servers", "localhost:9092")
    .option("subscribe", "clicks")
    .load()

  /**
   * Process stream
   */
  val clicks = stream
    .selectExpr("CAST(value AS STRING)")
    .as[String]
    .map(parseClick)

  /**
   * Window aggregation (5-minute windows)
   */
  val clicksPerMinute = clicks
    .groupBy(
      window($"timestamp", "5 minutes"),
      $"page"
    )
    .count()

  /**
   * Write to sink
   */
  clicksPerMinute.writeStream
    .format("parquet")
    .option("path", "/output/clicks")
    .option("checkpointLocation", "/checkpoints/clicks")
    .start()

  /**
   * Spark Streaming = Micro-batches
   *   - Divide stream into small batches (e.g., 1 second)
   *   - Process each batch with Spark
   *   - Latency: seconds (not milliseconds)
   *
   * Alternative: Structured Streaming (newer)
   *   - Same DataFrame API for batch and streaming
   *   - Automatic fault tolerance (checkpointing)
   *   - Exactly-once semantics
   */
}
```

### MLlib (Machine Learning)

```python
"""
Spark MLlib: Distributed machine learning
"""

from pyspark.ml.classification import LogisticRegression
from pyspark.ml.feature import VectorAssembler

# Load data
data = spark.read.parquet("/data/features")

# Prepare features
assembler = VectorAssembler(
    inputCols=["feature1", "feature2", "feature3"],
    outputCol="features"
)
training = assembler.transform(data)

# Train model
lr = LogisticRegression(maxIter=10, regParam=0.01)
model = lr.fit(training)

# Predict
predictions = model.transform(test)

"""
MLlib algorithms:
  - Classification: Logistic Regression, Decision Trees, Random Forest, GBT
  - Regression: Linear Regression, GLM
  - Clustering: K-means, GMM
  - Collaborative Filtering: ALS
  - Dimensionality Reduction: PCA, SVD

Why Spark for ML?
  - Scale to TBs of data
  - Iterative algorithms (fast with caching)
  - Feature engineering (SQL + DataFrames)
  - End-to-end pipelines (ETL → train → predict)
"""
```

---

## Performance Optimization

🏗️ **ARCHITECT**: Making Spark blazing fast.

### Memory Management

```scala
object MemoryOptimization {

  /**
   * 1. Caching/Persistence
   */
  def caching(): Unit = {
    val rdd = sc.textFile("/data/file.txt")

    // Cache in memory (default: MEMORY_ONLY)
    rdd.cache()  // Shorthand for persist(MEMORY_ONLY)

    // Persist with different storage levels
    rdd.persist(StorageLevel.MEMORY_AND_DISK)  // Spill to disk if RAM full
    rdd.persist(StorageLevel.MEMORY_ONLY_SER)  // Serialize to save RAM
    rdd.persist(StorageLevel.MEMORY_AND_DISK_SER)  // Serialize + spill
    rdd.persist(StorageLevel.DISK_ONLY)  // Store only on disk
    rdd.persist(StorageLevel.OFF_HEAP)  // Store in off-heap memory

    /**
     * When to cache?
     *   ✓ RDD used multiple times
     *   ✓ Expensive to recompute (long lineage)
     *   ✓ Iterative algorithms
     *
     * When NOT to cache?
     *   ✗ RDD used only once
     *   ✗ RAM limited (caching causes spills → slower)
     */
  }

  /**
   * 2. Memory configuration
   */
  def memoryConfig(): Unit = {
    /**
     * Spark memory areas:
     *   - Execution: Shuffles, joins, sorts, aggregations
     *   - Storage: Cached RDDs, broadcast variables
     *   - User: User code, data structures
     *   - Reserved: Spark internal (300MB)
     *
     * Configuration:
     */
    conf.set("spark.executor.memory", "8g")  // Total executor memory
    conf.set("spark.memory.fraction", "0.6")  // Fraction for execution + storage
    conf.set("spark.memory.storageFraction", "0.5")  // Storage within memory

    /**
     * Math:
     *   Executor memory: 8GB
     *   Reserved: 300MB
     *   Usable: 7.7GB
     *   Spark memory (60%): 4.6GB
     *     Storage (50%): 2.3GB
     *     Execution (50%): 2.3GB
     *   User memory (40%): 3.1GB
     */
  }

  /**
   * 3. Serialization
   */
  def serialization(): Unit = {
    /**
     * Java serialization (default):
     *   - Slow
     *   - Large size
     *
     * Kryo serialization (recommended):
     *   - 10x faster
     *   - 10x smaller
     */
    conf.set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    conf.registerKryoClasses(Array(classOf[MyClass1], classOf[MyClass2]))

    /**
     * When does serialization matter?
     *   - Shuffle data
     *   - Broadcast variables
     *   - Cached RDDs (MEMORY_ONLY_SER)
     *   - Task serialization
     */
  }
}
```

### Shuffle Optimization

```scala
object ShuffleOptimization {

  /**
   * 1. Avoid shuffles when possible
   */
  def avoidShuffle(): Unit = {
    // BAD: groupByKey (always shuffles)
    val grouped = pairs.groupByKey()

    // GOOD: reduceByKey (map-side combine)
    val reduced = pairs.reduceByKey(_ + _)

    // BAD: Cartesian join (shuffle everything)
    val joined = rdd1.cartesian(rdd2)

    // GOOD: Broadcast join (no shuffle if one side is small)
    val small = sc.broadcast(rdd1.collectAsMap())
    val joined = rdd2.map(x => (x, small.value.get(x._1)))
  }

  /**
   * 2. Tune shuffle parameters
   */
  def tuneShuffleParams(): Unit = {
    // Number of partitions after shuffle
    conf.set("spark.sql.shuffle.partitions", "200")  // Default: 200

    // For very large shuffles: increase
    conf.set("spark.sql.shuffle.partitions", "2000")

    // For small shuffles: decrease
    conf.set("spark.sql.shuffle.partitions", "20")

    /**
     * Rule of thumb:
     *   - Partition size: 128MB - 200MB
     *   - Total data / partition size = num partitions
     *   - Example: 100GB / 128MB = 800 partitions
     */

    // Shuffle compression
    conf.set("spark.shuffle.compress", "true")  // Default: true
    conf.set("spark.shuffle.compress.codec", "snappy")  // lz4, snappy, zstd

    // Shuffle spill compression
    conf.set("spark.shuffle.spill.compress", "true")
  }

  /**
   * 3. Coalesce vs Repartition
   */
  def coalescVsRepartition(): Unit = {
    val rdd = sc.parallelize(1 to 1000, 100)  // 100 partitions

    // Reduce partitions (no shuffle)
    val coalesced = rdd.coalesce(10)  // 100 → 10 partitions, no shuffle

    // Repartition (with shuffle - even distribution)
    val repartitioned = rdd.repartition(10)  // 100 → 10 partitions, shuffle

    /**
     * When to use each:
     *   coalesce: Reduce partitions after filter (no shuffle)
     *   repartition: Increase partitions or even distribution (shuffle ok)
     */
  }
}
```

### Code Optimization

```scala
object CodeOptimization {

  /**
   * 1. Prefer DataFrames over RDDs
   */
  def dataframeVsRDD(): Unit = {
    // RDD: No optimization
    val rdd = sc.textFile("/data/users.txt")
      .map(parseLine)
      .filter(_.age > 18)
      .groupBy(_.country)
      .mapValues(_.size)

    // DataFrame: Optimized by Catalyst
    val df = spark.read.json("/data/users.json")
      .filter($"age" > 18)
      .groupBy($"country")
      .count()

    /**
     * DataFrame benefits:
     *   - Catalyst optimizer (reorder, combine operations)
     *   - Predicate pushdown (push filter to data source)
     *   - Column pruning (read only needed columns)
     *   - Code generation (JVM bytecode generation)
     *   - Result: 2-5x faster
     */
  }

  /**
   * 2. Broadcast variables
   */
  def broadcastVariables(): Unit = {
    // BAD: Closure captures large variable
    val largeMap = Map(...)  // 100MB
    rdd.map(x => largeMap.get(x))
    // Problem: largeMap sent to EACH TASK (100s of copies!)

    // GOOD: Broadcast variable
    val broadcastMap = sc.broadcast(Map(...))  // 100MB
    rdd.map(x => broadcastMap.value.get(x))
    // Benefit: largeMap sent to EACH NODE (one copy per node)

    /**
     * When to broadcast:
     *   - Variable > 1MB
     *   - Variable used in many tasks
     *   - Example: Small lookup table for joins
     */
  }

  /**
   * 3. Avoid UDFs (User-Defined Functions)
   */
  def avoidUDFs(): Unit = {
    // BAD: UDF (no optimization, serialization overhead)
    val upperUDF = udf((s: String) => s.toUpperCase)
    df.withColumn("name_upper", upperUDF($"name"))

    // GOOD: Built-in function
    df.withColumn("name_upper", upper($"name"))

    /**
     * Built-in functions are optimized:
     *   - Code generation
     *   - No serialization
     *   - Catalyst optimization
     *
     * Use UDFs only when necessary!
     */
  }

  /**
   * 4. Data format optimization
   */
  def dataFormats(): Unit = {
    /**
     * Format comparison:
     *
     * CSV:
     *   Size: 1.0x (baseline)
     *   Read speed: 1.0x
     *   Use: Human-readable exports
     *
     * JSON:
     *   Size: 1.5x (larger than CSV)
     *   Read speed: 0.8x (slower)
     *   Use: Semi-structured data, APIs
     *
     * Parquet:
     *   Size: 0.2x (5x smaller - columnar compression)
     *   Read speed: 5x (column pruning, predicate pushdown)
     *   Use: Data warehousing, analytics (RECOMMENDED)
     *
     * ORC:
     *   Size: 0.2x (similar to Parquet)
     *   Read speed: 5x
     *   Use: Hive integration
     */

    // Save as Parquet
    df.write.parquet("/output/data.parquet")

    // Read Parquet (with column pruning)
    spark.read.parquet("/output/data.parquet")
      .select("id", "name")  // Only reads these columns!
  }
}
```

---

## When to Use Spark

🎓 **PROFESSOR**: Decision framework for Spark adoption.

### Use Spark When:

```
✓ Iterative algorithms (ML, graph processing)
✓ Interactive queries (exploratory analysis)
✓ Multi-step pipelines (ETL)
✓ Need fast results (minutes, not hours)
✓ Sufficient cluster memory (or can cache key datasets)
✓ Unified platform needed (batch + streaming + SQL + ML)

Examples:
  - Machine learning training (feature engineering + model training)
  - Real-time analytics (Spark Streaming)
  - Graph analytics (PageRank, community detection)
  - ETL pipelines (cleanse, transform, aggregate)
  - Ad-hoc queries (data exploration)
```

### Don't Use Spark When:

```
✗ Simple one-pass algorithms (MapReduce sufficient)
✗ Extremely limited memory (< 1GB per executor)
✗ Need sub-second latency (use stream processor like Flink)
✗ Transactional workloads (use database)
✗ Very small data (< 10GB - use single machine)

Examples:
  - Simple aggregations (use Hive or Presto)
  - Real-time event processing (use Flink or Storm)
  - OLTP (use PostgreSQL, MySQL)
  - Small datasets (use pandas, R)
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Using Spark in System Design

```
Scenario: "Design a recommendation system"
──────────────────────────────────────────

Requirements:
  - 100M users, 1M items
  - Train model daily on user interactions (10TB)
  - Serve recommendations in real-time (<100ms)

Architecture:
  ┌──────────────────────────────────────────┐
  │  User Events → Kafka → HDFS              │
  │                           ↓              │
  │      Spark Batch Job (nightly)           │
  │  - Load interactions (10TB)              │
  │  - Feature engineering                   │
  │  - Train ALS model (MLlib)               │
  │                           ↓              │
  │      Model → Model Store                 │
  │                           ↓              │
  │      Serving Layer (Redis)               │
  │  - Precompute recommendations            │
  │  - Serve from cache (<100ms)             │
  └──────────────────────────────────────────┘

Why Spark?
  ✓ Feature engineering: Spark SQL (aggregations, joins)
  ✓ Model training: MLlib ALS (iterative algorithm)
  ✓ Scale: 10TB dataset processed in 2 hours (100 nodes)
  ✓ Unified: Same platform for ETL + ML

Deep Dive:
  Q: How to handle data skew (popular items)?
  A: Salting - add random suffix to hot keys
     Original: ("item_123", features)
     Salted: ("item_123_0", features), ("item_123_1", features)
     → Distribute across multiple partitions

  Q: How to optimize model training?
  A: - Cache training data in memory
     - Use Kryo serialization
     - Tune number of partitions (2-4x cores)
     - Checkpoint every 10 iterations (truncate lineage)

  Q: What if training takes too long?
  A: - Increase cluster size
     - Sample data (train on 10% for initial model)
     - Use feature selection (reduce dimensions)
     - Consider incremental training (update model, don't retrain)
```

---

## 🧠 MIND MAP: APACHE SPARK

```
         APACHE SPARK
              |
      ┌───────┼───────┐
      ↓       ↓       ↓
     RDD    FAULT   PERFORMANCE
            TOLERANCE
      |       |         |
  ┌───┴──┐    |     ┌───┴────┐
  ↓      ↓    ↓     ↓        ↓
Immutable  Lineage  Cache  Shuffle
Distributed Graph   In-Memory Optimize
  |        |         |        |
Lazy    Recompute  10-100x  Avoid
Eval     Lost      Faster   Wide Deps
       Partitions
```

---

## 💡 EMOTIONAL ANCHORS

### 1. Spark = Whiteboard vs Filing Cabinet 📝
- MapReduce: Write on paper → File in cabinet → Retrieve → Write again (slow)
- Spark: Use whiteboard → Keep visible → Erase when done (fast)
- Data stays in view (memory) instead of stored away (disk)

### 2. Lineage = Recipe Book 📖
- Don't save every cooking step (expensive)
- Just remember recipe (lineage graph)
- If dish ruined, follow recipe again (recompute)
- Only remake failed dish (partition), not entire meal

### 3. Wide Dependency = Group Project Meeting 👥
- Narrow: Everyone works independently (fast)
- Wide: Everyone must meet and share (slow)
- Meeting = shuffle (expensive coordination)
- Minimize meetings (shuffles) for productivity

### 4. Caching = Keeping Tools Nearby 🔧
- MapReduce: Tools in garage (disk), walk to get them every time
- Spark: Tools on workbench (memory), grab instantly
- Repetitive tasks much faster with tools nearby

### 5. RDD = Assembly Line Blueprint 🏭
- Each step defined, not executed (lazy)
- When order placed, execute entire pipeline
- Can optimize route before starting
- If worker fails, consult blueprint and redo their step

### 6. DataFrame = Spreadsheet Magic ✨
- RDD = Raw data manipulation (low-level)
- DataFrame = Excel formulas (high-level)
- Automatic optimization (like Excel's formula engine)
- Same task, less code, better performance

---

## 🔑 Key Takeaways

### Core Concepts

1. **Spark = In-memory computing for speed**
   - Keep data in RAM across operations
   - 10-100x faster than disk-based MapReduce
   - Critical for iterative algorithms (ML, graph processing)

2. **RDD = Fundamental abstraction**
   - Immutable, distributed collection
   - Lazy evaluation (build DAG, optimize, execute)
   - Automatic parallelization

3. **Lineage-based fault tolerance**
   - No need to checkpoint intermediate data
   - Recompute lost partitions from lineage
   - Lower overhead than replication

### Performance

4. **Cache frequently-used datasets**
   - `rdd.cache()` keeps data in memory
   - Essential for iterative algorithms
   - Speeds up interactive queries 100x+

5. **Minimize shuffles (wide dependencies)**
   - Shuffle = Network + disk I/O (expensive)
   - Use `reduceByKey` instead of `groupByKey`
   - Use broadcast joins for small tables

6. **Prefer DataFrames over RDDs**
   - Catalyst optimizer improves query plans
   - Tungsten engine for memory management
   - 2-5x faster for same operations

### Architecture

7. **Spark vs MapReduce**
   - MapReduce: Disk-based, map+reduce only
   - Spark: Memory-based, rich API (80+ operations)
   - Use Spark for iterative/interactive workloads
   - Use MapReduce for simple one-pass jobs

8. **Spark is a unified platform**
   - Spark SQL: Structured data queries
   - MLlib: Machine learning
   - GraphX: Graph processing
   - Structured Streaming: Stream processing
   - One API, one engine, multiple use cases

9. **Fault tolerance is automatic**
   - Task failure → Re-execute on different node
   - Lineage graph tracks how to recompute
   - Checkpointing for long lineages

### Practical Wisdom

10. **Spark revolutionized big data**
    - Academic research (UC Berkeley, 2010)
    - Production adoption by major tech companies
    - 10x faster than MapReduce, same cost
    - Enabling real-time analytics at scale
    - Standard for big data processing today

---

**Final Thought**: Spark took the MapReduce model and made it 100x faster by keeping data in memory. But the real innovation is lineage-based fault tolerance - achieving both speed (no checkpointing overhead) and reliability (automatic recovery). Understanding Spark means understanding modern big data - it's the foundation for most large-scale data processing today.
