# Batch Processing & MapReduce Deep Dive

## Contents

- [Batch Processing & MapReduce Deep Dive](#batch-processing--mapreduce-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [What is Batch Processing?](#what-is-batch-processing)
    - [MapReduce - The Foundation](#mapreduce---the-foundation)
    - [WTF is Hadoop?](#wtf-is-hadoop)
    - [MapReduce Execution Flow](#mapreduce-execution-flow)
    - [Fault Tolerance in MapReduce](#fault-tolerance-in-mapreduce)
    - [MapReduce Patterns](#mapreduce-patterns)
    - [Performance Optimization](#performance-optimization)
    - [When to Use Batch Processing](#when-to-use-batch-processing)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: BATCH PROCESSING](#mind-map-batch-processing)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Batch Processing = Process LARGE amounts of data in ONE go
                   (opposite of real-time/streaming)

The Core Idea:
┌────────────────────────────────────────────────────────────┐
│ Collect data over time → Process all at once → Output      │
│                                                             │
│ Input:  1TB of server logs (collected over 24 hours)       │
│ Process: Count errors per API endpoint                     │
│ Output: Aggregated report                                  │
│                                                             │
│ Key: Trade latency for throughput                          │
│      (Process hours/days of data in minutes/hours)         │
└────────────────────────────────────────────────────────────┘

MapReduce = Programming model for batch processing
            Divide & Conquer for big data

                  ┌─────────────────┐
                  │  BIG DATA       │
                  │  (1TB logs)     │
                  └────────┬────────┘
                           │
                    ┌──────┴──────┐
                    │   SPLIT     │ Split into chunks
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        ↓                  ↓                  ↓
    ┌───────┐         ┌───────┐         ┌───────┐
    │  MAP  │         │  MAP  │         │  MAP  │  Process in parallel
    │ Task1 │         │ Task2 │         │ Task3 │
    └───┬───┘         └───┬───┘         └───┬───┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
                    ┌──────┴──────┐
                    │   SHUFFLE   │ Group by key
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        ↓                  ↓                  ↓
    ┌────────┐        ┌────────┐        ┌────────┐
    │ REDUCE │        │ REDUCE │        │ REDUCE │  Aggregate
    │ Task1  │        │ Task2  │        │ Task3  │
    └────┬───┘        └────┬───┘        └────┬───┘
         │                  │                  │
         └──────────────────┴──────────────────┘
                           │
                    ┌──────┴──────┐
                    │   OUTPUT    │
                    └─────────────┘
```

**The Key Insight:**
```
Split big problem into many small problems
Solve small problems in parallel
Combine results

Example: Count words in 1TB text
─────────────────────────────────
Serial approach:   10 hours (one machine)
MapReduce (100 machines): 10 minutes (100x speedup!)
```

---

## What is Batch Processing?

🎓 **PROFESSOR**: Batch processing is the oldest and most fundamental data processing model.

### Definition & Characteristics

```
Batch Processing:
────────────────
Process a FINITE amount of input data to produce output

Characteristics:
1. Input is bounded (has start and end)
2. Read entire input before producing output
3. High latency (minutes to hours)
4. High throughput (TBs per job)
5. Resource efficient (optimize for throughput, not latency)

Contrast with Stream Processing:
─────────────────────────────────
Batch:   Input bounded,  High latency,  High throughput
Stream:  Input unbounded, Low latency,   Lower throughput

         BATCH                      STREAM
    ┌──────────────┐          ┌──────────────┐
    │ [========]   │          │ →→→→→→→→→→   │
    │  Process all │          │ Process each │
    │  at once     │          │ event as it  │
    │              │          │ arrives      │
    └──────────────┘          └──────────────┘
```

### Why Batch Processing?

```java
/**
 * Use cases where batch processing excels
 */
public class BatchProcessingUseCases {

    /**
     * 1. Historical data analysis
     */
    public void historicalAnalysis() {
        /**
         * Problem: Analyze last month's sales data
         *
         * Input:  30 days × 1M transactions/day = 30M transactions
         * Goal:   Top 100 products by revenue per region
         *
         * Why Batch?
         *   - Data already collected (bounded input)
         *   - No need for real-time results
         *   - Can process efficiently in one pass
         *
         * Alternative (Stream): Would need to buffer 30 days of data anyway!
         */
    }

    /**
     * 2. ETL (Extract, Transform, Load)
     */
    public void etlPipeline() {
        /**
         * Problem: Daily ETL from OLTP → Data Warehouse
         *
         * Process:
         *   1. Extract: Pull yesterday's data from production DB
         *   2. Transform: Clean, normalize, aggregate
         *   3. Load: Insert into data warehouse
         *
         * Why Batch?
         *   - Runs during low-traffic hours (2am)
         *   - Process entire day's data at once
         *   - Optimized for throughput (not latency)
         *
         * Timing: Run nightly, results ready by 6am
         */
    }

    /**
     * 3. Machine Learning training
     */
    public void mlTraining() {
        /**
         * Problem: Train recommendation model
         *
         * Input:  1 year of user behavior (10TB)
         * Output: Trained model
         *
         * Why Batch?
         *   - Training requires full dataset (not incremental)
         *   - Can take hours/days (latency acceptable)
         *   - GPU/CPU utilization optimized for throughput
         *
         * Run weekly, deploy new model after training complete
         */
    }

    /**
     * 4. Report generation
     */
    public void reportGeneration() {
        /**
         * Problem: Monthly business reports
         *
         * Examples:
         *   - Revenue by product line
         *   - Customer churn analysis
         *   - Supply chain metrics
         *
         * Why Batch?
         *   - Reports run monthly (fixed schedule)
         *   - Aggregate large amounts of data
         *   - Results don't need to be real-time
         */
    }
}
```

### Batch vs Stream vs Request-Response

```
┌─────────────────────────────────────────────────────────────────┐
│                  PROCESSING MODELS COMPARISON                    │
├─────────────────────────────────────────────────────────────────┤
│                                                                  │
│  REQUEST-RESPONSE (Online)                                       │
│  ────────────────────────                                        │
│  User → [Request] → Server → [Response] → User                  │
│  Latency: ms                                                     │
│  Example: Web request, API call                                  │
│                                                                  │
│  BATCH (Offline)                                                 │
│  ────────────────                                                │
│  [Data 24hrs] → [Process] → [Output Report]                     │
│  Latency: hours                                                  │
│  Example: Daily ETL, monthly reports                             │
│                                                                  │
│  STREAM (Near Real-time)                                         │
│  ───────────────────────                                         │
│  Event → [Process] → Output → Event → [Process] → Output        │
│  Latency: seconds                                                │
│  Example: Fraud detection, real-time dashboards                  │
│                                                                  │
└─────────────────────────────────────────────────────────────────┘

Decision Matrix:
───────────────
Need result in < 1 second?      → Request-Response
Need result in 1-60 seconds?    → Stream Processing
Need result in 1-24 hours?      → Batch Processing
Input size < 1GB?               → Request-Response or Stream
Input size > 100GB?             → Batch Processing
```

---

## MapReduce - The Foundation

🏗️ **ARCHITECT**: MapReduce revolutionized big data processing. Here's why.

### The MapReduce Paper (2004)

```
Google's MapReduce Paper:
────────────────────────
Published: 2004 (Jeff Dean, Sanjay Ghemawat)
Problem: Process 20+ TB of web crawl data daily
Solution: Simple programming model + automatic parallelization

The Breakthrough:
  Programmers write TWO functions:
    map(key, value) → list of (k2, v2)
    reduce(key, list of values) → list of values

  Framework handles:
    ✓ Splitting data across machines
    ✓ Scheduling tasks
    ✓ Fault tolerance
    ✓ Inter-machine communication

Before MapReduce:
  Write complex distributed systems code
  Handle failures manually
  Manage parallelization

After MapReduce:
  Write map() and reduce()
  Framework does the rest!
```

### The MapReduce Model

```python
"""
Classic example: Word count
"""

def map_function(document):
    """
    Map: Extract words from document

    Input:  (doc_id, "hello world hello")
    Output: [("hello", 1), ("world", 1), ("hello", 1)]
    """
    words = document.split()
    for word in words:
        emit(word, 1)  # Emit (key, value) pair


def reduce_function(word, counts):
    """
    Reduce: Aggregate counts for each word

    Input:  ("hello", [1, 1, 1, 1, 1])
    Output: ("hello", 5)
    """
    total = sum(counts)
    emit(word, total)


# Framework automatically:
# 1. Reads input files in parallel
# 2. Calls map_function on each chunk
# 3. Shuffles intermediate data (group by key)
# 4. Calls reduce_function on each group
# 5. Writes output files
```

**Visual: Word Count MapReduce**
```text
INPUT FILES (3 files, processed in parallel):
──────────────────────────────────────────────

File1: "hello world"
File2: "hello spark"
File3: "world spark"

MAP PHASE (3 map tasks in parallel):
────────────────────────────────────

Map Task 1 (File1):                Map Task 2 (File2):          Map Task 3 (File3):
  "hello world"                      "hello spark"                "world spark"
  ↓                                  ↓                            ↓
  ("hello", 1)                       ("hello", 1)                 ("world", 1)
  ("world", 1)                       ("spark", 1)                 ("spark", 1)

SHUFFLE PHASE (group by key):
─────────────────────────────

  ("hello", [1, 1])      ← From Map Task 1 & 2
  ("world", [1, 1])      ← From Map Task 1 & 3
  ("spark", [1, 1])      ← From Map Task 2 & 3

REDUCE PHASE (3 reduce tasks):
──────────────────────────────

Reduce Task 1:           Reduce Task 2:           Reduce Task 3:
  ("hello", [1, 1])        ("world", [1, 1])        ("spark", [1, 1])
  ↓ sum([1, 1])            ↓ sum([1, 1])            ↓ sum([1, 1])
  ("hello", 2)             ("world", 2)             ("spark", 2)

OUTPUT:
───────
  hello  2
  world  2
  spark  2
```

### MapReduce Programming Model

```java
/**
 * MapReduce interface (Hadoop style)
 */
public class WordCount {

    /**
     * Mapper: Processes input records, emits intermediate key-value pairs
     */
    public static class TokenizerMapper
            extends Mapper<LongWritable, Text, Text, IntWritable> {

        private final static IntWritable one = new IntWritable(1);
        private Text word = new Text();

        @Override
        public void map(LongWritable key, Text value, Context context)
                throws IOException, InterruptedException {

            // Input: key = byte offset, value = line of text
            String line = value.toString();

            // Tokenize
            StringTokenizer tokenizer = new StringTokenizer(line);

            // Emit (word, 1) for each word
            while (tokenizer.hasMoreTokens()) {
                word.set(tokenizer.nextToken().toLowerCase());
                context.write(word, one);  // emit(word, 1)
            }
        }
    }

    /**
     * Reducer: Aggregates values for each key
     */
    public static class IntSumReducer
            extends Reducer<Text, IntWritable, Text, IntWritable> {

        @Override
        public void reduce(Text key, Iterable<IntWritable> values, Context context)
                throws IOException, InterruptedException {

            // Input: key = word, values = [1, 1, 1, ...]
            int sum = 0;

            // Sum all counts
            for (IntWritable val : values) {
                sum += val.get();
            }

            // Emit (word, total_count)
            context.write(key, new IntWritable(sum));
        }
    }

    /**
     * Job configuration
     */
    public static void main(String[] args) throws Exception {
        Configuration conf = new Configuration();
        Job job = Job.getInstance(conf, "word count");

        job.setJarByClass(WordCount.class);
        job.setMapperClass(TokenizerMapper.class);
        job.setCombinerClass(IntSumReducer.class);  // Local aggregation!
        job.setReducerClass(IntSumReducer.class);

        job.setOutputKeyClass(Text.class);
        job.setOutputValueClass(IntWritable.class);

        FileInputFormat.addInputPath(job, new Path(args[0]));
        FileOutputFormat.setOutputPath(job, new Path(args[1]));

        System.exit(job.waitForCompletion(true) ? 0 : 1);
    }
}
```

### Key Concepts

```
1. Map Function
   ───────────
   Input:  (K1, V1)
   Output: list of (K2, V2)

   Purpose: Transform and filter data
   Example: Extract features, parse logs, generate pairs

2. Reduce Function
   ───────────────
   Input:  (K2, list of V2)
   Output: list of (K3, V3)

   Purpose: Aggregate and summarize
   Example: Sum, average, count, join

3. Shuffle Phase (Framework handles this)
   ───────────────────────────────────────
   After Map, before Reduce
   Groups all values by key
   Sorts keys (optional)

   This is WHERE THE MAGIC HAPPENS:
   - Distributed sorting
   - Network transfer
   - Most expensive phase!

4. Combiner (Optional optimization)
   ──────────────────────────────
   "Mini-reduce" on map output
   Reduces network traffic
   Must be associative and commutative

   Example: Sum combiner
     Map output:    [("hello", 1), ("hello", 1), ("hello", 1)]
     After combine: [("hello", 3)]
     Network traffic: 1 record instead of 3!
```

---

## WTF is Hadoop?

🎓 **PROFESSOR**: Hadoop = Open-source implementation of MapReduce + Distributed File System

### Hadoop Ecosystem

```
Hadoop = Not just MapReduce!
────────────────────────────

┌─────────────────────────────────────────────────────────────┐
│                    HADOOP ECOSYSTEM                         │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  APPLICATIONS                                               │
│  ├─ Hive      (SQL on Hadoop)                              │
│  ├─ Pig       (Scripting language)                         │
│  ├─ HBase     (NoSQL database)                             │
│  └─ Mahout    (Machine learning)                           │
│                                                             │
│  RESOURCE MANAGEMENT                                        │
│  └─ YARN      (Yet Another Resource Negotiator)            │
│     ├─ Resource Manager                                    │
│     └─ Node Manager (on each machine)                      │
│                                                             │
│  PROCESSING                                                 │
│  ├─ MapReduce (Batch processing)                           │
│  └─ Spark     (In-memory processing) *newer*               │
│                                                             │
│  STORAGE                                                    │
│  └─ HDFS      (Hadoop Distributed File System)             │
│     ├─ NameNode    (metadata)                              │
│     └─ DataNode    (actual data storage)                   │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

### HDFS (Hadoop Distributed File System)

```
HDFS: Store massive files across cluster
────────────────────────────────────────

Design Goals:
  1. Store very large files (GBs to TBs per file)
  2. Handle hardware failures gracefully
  3. Optimize for throughput (not latency)
  4. Scale to thousands of nodes

Architecture:
  ┌─────────────────────────────────────────────────┐
  │              NameNode (Master)                  │
  │  - Stores file metadata                         │
  │  - Knows which blocks on which DataNodes        │
  │  - Single point of coordination                 │
  └──────────────────┬──────────────────────────────┘
                     │
      ┌──────────────┼──────────────┐
      ↓              ↓              ↓
  ┌─────────┐   ┌─────────┐   ┌─────────┐
  │DataNode1│   │DataNode2│   │DataNode3│
  │ Block1  │   │ Block1  │   │ Block2  │
  │ Block2  │   │ Block3  │   │ Block3  │
  └─────────┘   └─────────┘   └─────────┘

File Storage:
  File: "logs.txt" (256MB)
  Split into blocks: 128MB each (default)
  Block 1: Bytes 0-128MB
  Block 2: Bytes 128-256MB

  Each block replicated 3x (default):
  Block 1: DataNode1, DataNode2, DataNode5
  Block 2: DataNode1, DataNode3, DataNode4
```

```java
/**
 * HDFS operations
 */
public class HDFSExample {

    /**
     * Write file to HDFS
     */
    public void writeToHDFS() throws IOException {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);

        // Create file
        Path path = new Path("/user/data/input.txt");
        FSDataOutputStream out = fs.create(path);

        // Write data
        out.writeBytes("Hello HDFS!");
        out.close();

        /**
         * What happens internally:
         * 1. Client contacts NameNode: "I want to create /user/data/input.txt"
         * 2. NameNode checks permissions, allocates block IDs
         * 3. NameNode returns list of DataNodes for first block
         * 4. Client writes data to DataNode1
         * 5. DataNode1 pipelines to DataNode2, DataNode3 (replication)
         * 6. All DataNodes ACK
         * 7. Client notifies NameNode: "Block written successfully"
         */
    }

    /**
     * Read file from HDFS
     */
    public void readFromHDFS() throws IOException {
        Configuration conf = new Configuration();
        FileSystem fs = FileSystem.get(conf);

        // Open file
        Path path = new Path("/user/data/input.txt");
        FSDataInputStream in = fs.open(path);

        // Read data
        byte[] buffer = new byte[1024];
        int bytesRead = in.read(buffer);
        in.close();

        /**
         * What happens internally:
         * 1. Client contacts NameNode: "Where is /user/data/input.txt?"
         * 2. NameNode returns: "Block1 on DataNode1,2,3; Block2 on DataNode4,5,6"
         * 3. Client reads Block1 from closest DataNode (e.g., DataNode1)
         * 4. If DataNode1 fails, try DataNode2
         * 5. Client reads Block2 from DataNode4
         * 6. Client concatenates blocks → complete file
         */
    }

    /**
     * HDFS characteristics
     */
    public void hdfsCharacteristics() {
        /**
         * Block Size: 128MB (vs 4KB on local filesystem)
         *   Why so large?
         *     - Minimize metadata (NameNode stores metadata for each block)
         *     - Optimize for throughput (sequential reads)
         *     - Reduce seek overhead
         *
         * Replication: 3x (default)
         *   Why?
         *     - Fault tolerance (tolerate 2 node failures)
         *     - Read parallelism (read from any replica)
         *     - Load balancing
         *
         * Write Once, Read Many:
         *   - Files immutable after creation
         *   - Appends supported (not random writes)
         *   - Optimizes for batch processing workloads
         *
         * Data Locality:
         *   - MapReduce schedules tasks on nodes with data
         *   - Avoids network transfer (fast!)
         */
    }
}
```

### YARN (Resource Manager)

```
YARN: Manage cluster resources for multiple applications
────────────────────────────────────────────────────────

Before YARN:
  Hadoop = MapReduce only
  Can't run other workloads (Spark, etc.)

After YARN:
  Hadoop = General-purpose cluster OS
  Run MapReduce, Spark, Flink, etc.

Architecture:
  ┌─────────────────────────────────────┐
  │     Resource Manager (Master)       │
  │  - Allocates resources              │
  │  - Schedules applications           │
  └──────────────┬──────────────────────┘
                 │
      ┌──────────┼──────────┐
      ↓          ↓          ↓
  ┌────────┐ ┌────────┐ ┌────────┐
  │  Node  │ │  Node  │ │  Node  │
  │Manager1│ │Manager2│ │Manager3│
  └────────┘ └────────┘ └────────┘
      │          │          │
   ┌──┴──┐    ┌──┴──┐   ┌──┴──┐
   │Task │    │Task │   │Task │
   │Task │    │Task │   │Task │
   └─────┘    └─────┘   └─────┘

Application Submission:
  1. Client submits application to Resource Manager
  2. Resource Manager allocates container for Application Master
  3. Application Master requests resources for tasks
  4. Resource Manager allocates containers on Node Managers
  5. Application Master launches tasks in containers
  6. Tasks execute, report progress to Application Master
  7. Application Master reports to Resource Manager
```

---

## MapReduce Execution Flow

🏗️ **ARCHITECT**: Let's trace a MapReduce job from start to finish.

### Detailed Execution Flow

```python
class MapReduceExecutionFlow:
    """
    Step-by-step MapReduce execution
    """

    def job_submission(self):
        """
        STEP 1: Job submission
        """
        # Client submits job to JobTracker (master)
        job = JobConf()
        job.setMapperClass(WordCountMapper.class)
        job.setReducerClass(WordCountReducer.class)
        job.setInputPath("/input/logs")
        job.setOutputPath("/output/word_counts")

        JobClient.runJob(job)

        """
        JobTracker:
          1. Creates job ID
          2. Copies job resources (JAR, config) to HDFS
          3. Divides input files into splits
          4. Schedules map tasks
        """

    def input_splitting(self):
        """
        STEP 2: Input splitting
        """
        # Input: /input/logs (1GB file on HDFS)
        # HDFS blocks: 128MB each
        # Block 1: Bytes 0-128MB     (DataNode1, 2, 3)
        # Block 2: Bytes 128-256MB   (DataNode2, 3, 4)
        # Block 3: Bytes 256-384MB   (DataNode3, 4, 5)
        # ...
        # Block 8: Bytes 896-1024MB  (DataNode1, 2, 5)

        # MapReduce creates 8 input splits (one per block)
        # → 8 map tasks

        """
        Key Insight: Input split = HDFS block
          → Data locality! Schedule map task on node with block
          → No network transfer needed
        """

    def map_task_execution(self):
        """
        STEP 3: Map task execution
        """
        # JobTracker schedules map tasks on TaskTrackers

        # Map Task 1 (on DataNode1):
        #   1. Read Block 1 from local disk (data locality!)
        #   2. Parse into records (lines)
        #   3. Call map() on each record
        #   4. Write output to local disk (not HDFS!)

        # Map output format:
        #   Partition 0: ("apple", 1), ("banana", 2), ...
        #   Partition 1: ("cat", 5), ("dog", 3), ...
        #   Partition 2: ("spark", 10), ("hadoop", 8), ...

        # Partitioning: hash(key) % num_reducers
        #   Ensures same key goes to same reducer

        """
        Map output goes to local disk (not HDFS):
          - Intermediate data (will be deleted after job)
          - HDFS write overhead unnecessary
          - If map task fails, re-execute
        """

    def shuffle_phase(self):
        """
        STEP 4: Shuffle & Sort
        """
        # THE MOST COMPLEX PHASE!

        # 1. Each reducer fetches its partitions from all mappers
        # Reducer 0:
        #   Fetch Partition 0 from Map Task 1
        #   Fetch Partition 0 from Map Task 2
        #   ...
        #   Fetch Partition 0 from Map Task 8

        # 2. Sort by key (merge sort)
        # Before: [("hello", 1), ("world", 1), ("hello", 1)]
        # After:  [("hello", 1), ("hello", 1), ("world", 1)]

        # 3. Group by key
        # Result: [("hello", [1, 1]), ("world", [1])]

        """
        Shuffle characteristics:
          - Network-intensive (all-to-all communication)
          - Disk-intensive (sort large datasets)
          - Bottleneck in many jobs

        Optimization: Combiner
          - Runs after map, before shuffle
          - Reduces shuffle data size
        """

    def reduce_task_execution(self):
        """
        STEP 5: Reduce task execution
        """
        # Reducer receives sorted, grouped data
        # Input: ("hello", [1, 1, 1, 1, 1])

        # Call reduce() on each key
        def reduce(key, values):
            return (key, sum(values))

        # Output: ("hello", 5)

        # Write to HDFS
        # Output file: /output/word_counts/part-00000

        """
        Reduce output goes to HDFS:
          - Final output (persistent)
          - Replicated (fault tolerant)
          - Available for downstream jobs
        """

    def output_commit(self):
        """
        STEP 6: Output commit
        """
        # All reducers complete successfully
        # JobTracker commits output
        #   - Move from temp directory to final location
        #   - Atomic operation

        # If any task fails:
        #   - Re-execute task
        #   - Don't commit output
        #   → All-or-nothing semantics
```

**Visual Timeline:**
```
Timeline of MapReduce Job (1GB input, 8 mappers, 4 reducers):
──────────────────────────────────────────────────────────────

t=0s:   Job submission
        ├─ JobTracker receives job
        ├─ Copies JAR to HDFS
        └─ Creates input splits (8 splits)

t=1s:   Map task scheduling
        ├─ Schedule Map Task 1 on DataNode1 (data locality)
        ├─ Schedule Map Task 2 on DataNode2
        └─ ... (8 tasks total)

t=2s:   Map task execution begins
        ├─ Map Task 1: Read block, parse, emit pairs
        ├─ Map Task 2: Read block, parse, emit pairs
        └─ ...

t=60s:  Map tasks complete (parallel execution)
        └─ All 8 mappers finished

t=61s:  Shuffle begins
        ├─ Reducer 1 fetches partitions from all mappers
        ├─ Reducer 2 fetches partitions from all mappers
        └─ ... (4 reducers)

t=90s:  Shuffle complete, reduce begins
        ├─ Reducer 1: Sort, group, reduce
        ├─ Reducer 2: Sort, group, reduce
        └─ ...

t=120s: Reduce complete
        └─ Write output to HDFS

t=121s: Job complete!
        └─ Output: /output/word_counts/part-0000{0-3}

Total time: 121 seconds
Speedup: ~8x (with 8 mappers) for map phase
```

---

## Fault Tolerance in MapReduce

🎓 **PROFESSOR**: MapReduce assumes failures are NORMAL, not exceptional.

### Handling Task Failures

```java
/**
 * MapReduce fault tolerance mechanisms
 */
public class MapReduceFaultTolerance {

    /**
     * 1. Task failure detection
     */
    public void taskFailureDetection() {
        /**
         * Master (JobTracker) monitors workers (TaskTrackers)
         *
         * Heartbeat protocol:
         *   - TaskTracker sends heartbeat every 3 seconds
         *   - If no heartbeat for 10 minutes → TaskTracker dead
         *
         * Task monitoring:
         *   - TaskTracker reports task progress
         *   - If no progress for 10 minutes → Task stuck
         */
    }

    /**
     * 2. Map task failure recovery
     */
    public void mapTaskFailure() {
        /**
         * Scenario: Map Task 3 fails (machine crash)
         *
         * Recovery:
         *   1. JobTracker detects failure (no heartbeat)
         *   2. Mark Map Task 3 as FAILED
         *   3. Re-schedule Map Task 3 on different node
         *   4. New task reads same input split
         *   5. Re-executes map function
         *   6. Produces same output (assuming deterministic)
         *
         * Key insight: Map output is on local disk
         *   → If machine dies, output lost
         *   → Must re-execute
         *
         * Idempotency: map() must produce same output for same input
         */
    }

    /**
     * 3. Reduce task failure recovery
     */
    public void reduceTaskFailure() {
        /**
         * Scenario: Reduce Task 2 fails after 90% complete
         *
         * Recovery:
         *   1. JobTracker detects failure
         *   2. Mark Reduce Task 2 as FAILED
         *   3. Re-schedule Reduce Task 2 on different node
         *   4. New task re-fetches data from mappers
         *   5. Re-executes reduce function
         *   6. Writes output to HDFS
         *
         * Optimization: Checkpointing
         *   - Periodically save reduce state
         *   - On failure, resume from last checkpoint
         *   - Avoid re-processing all data
         */
    }

    /**
     * 4. Master failure (JobTracker)
     */
    public void masterFailure() {
        /**
         * Hadoop 1.x: Single point of failure
         *   - If JobTracker dies, entire job fails
         *   - Must restart from beginning
         *
         * Hadoop 2.x (YARN): High availability
         *   - Standby Resource Manager
         *   - ZooKeeper for coordination
         *   - Automatic failover
         *
         * Solution:
         *   1. JobTracker writes state to HDFS (checkpointing)
         *   2. Standby JobTracker monitors active
         *   3. If active fails, standby takes over
         *   4. Recovers state from HDFS
         *   5. Job continues!
         */
    }

    /**
     * 5. Stragglers (slow tasks)
     */
    public void stragglerMitigation() {
        /**
         * Problem: One slow task delays entire job
         *
         * Example:
         *   - 99 map tasks complete in 60 seconds
         *   - 1 map task takes 600 seconds (10x slower!)
         *   - Job blocked until slowest task completes
         *
         * Cause:
         *   - Bad disk
         *   - Competing workload
         *   - Data skew
         *
         * Solution: Speculative execution
         *   1. Detect straggler (task 10x slower than average)
         *   2. Launch duplicate task on different node
         *   3. Use result from whichever finishes first
         *   4. Kill the other task
         *
         * Trade-off:
         *   + Faster job completion
         *   - More resource usage (duplicate work)
         */

        // Configuration
        conf.set("mapreduce.map.speculative", "true");
        conf.set("mapreduce.reduce.speculative", "true");
    }

    /**
     * 6. Data loss (HDFS)
     */
    public void dataLoss() {
        /**
         * HDFS fault tolerance:
         *   - 3x replication (default)
         *   - Block stored on 3 different DataNodes
         *   - If DataNode fails, NameNode re-replicates block
         *
         * Scenario: DataNode3 fails
         *   1. NameNode detects (no heartbeat)
         *   2. NameNode finds under-replicated blocks
         *   3. NameNode commands DataNode4 to replicate
         *   4. DataNode4 copies from DataNode1 or DataNode2
         *   5. Replication factor restored to 3x
         *
         * Data loss requires 3 simultaneous failures (unlikely!)
         */
    }
}
```

### Why MapReduce Fault Tolerance Works

```
Key principles:
──────────────

1. Deterministic functions
   map() and reduce() must be pure functions
   Same input → Same output (always)
   → Safe to re-execute on failure

2. Immutable inputs
   Input files never change
   → Safe to re-read on failure

3. Atomic output commits
   Output written to temp location
   Renamed atomically on success
   → All-or-nothing semantics

4. No side effects
   map() and reduce() should not:
     - Write to external database
     - Send HTTP requests
     - Update global state
   → Safe to execute multiple times (speculative execution)

5. Task independence
   Map tasks independent of each other
   Reduce tasks independent of each other
   → Can re-execute individual tasks

Google's experience (2004 paper):
────────────────────────────────
  - Ran 1000 MapReduce jobs
  - 1800 workers (machines)
  - 5% of workers failed during execution
  - All jobs completed successfully!
  → Fault tolerance works in practice
```

---

## MapReduce Patterns

🏗️ **ARCHITECT**: Common patterns for solving problems with MapReduce.

### Pattern 1: Filtering

```python
"""
Pattern: Select subset of data
"""

def filter_pattern():
    """
    Example: Extract error logs

    Input:  All server logs
    Output: Only ERROR level logs
    """

    def mapper(log_line):
        # Parse log
        level, timestamp, message = parse_log(log_line)

        # Filter: Emit only ERRORs
        if level == "ERROR":
            emit(timestamp, message)
        # Lines with other levels → discarded

    def reducer(timestamp, messages):
        # No aggregation needed, just pass through
        for message in messages:
            emit(timestamp, message)

    # Alternative: Map-only job (no reducer needed)
    # Output directly from mapper
```

### Pattern 2: Aggregation

```python
"""
Pattern: Compute statistics
"""

def aggregation_pattern():
    """
    Example: Count users per country

    Input:  User records (user_id, country, age, ...)
    Output: (country, user_count)
    """

    def mapper(user_record):
        user_id, country, age = parse(user_record)
        emit(country, 1)  # Emit 1 for each user

    def combiner(country, counts):
        # Local aggregation (optimization)
        emit(country, sum(counts))

    def reducer(country, counts):
        total = sum(counts)
        emit(country, total)

    # Output: [("USA", 10000), ("UK", 5000), ...]
```

### Pattern 3: Joining

```python
"""
Pattern: Join two datasets
"""

def join_pattern():
    """
    Example: Join users with orders

    Input:
      users.txt:  (user_id, name, country)
      orders.txt: (order_id, user_id, amount)

    Output: (user_id, name, order_id, amount)
    """

    def mapper(record, dataset_type):
        if dataset_type == "users":
            user_id, name, country = parse(record)
            emit(user_id, ("USER", name, country))

        elif dataset_type == "orders":
            order_id, user_id, amount = parse(record)
            emit(user_id, ("ORDER", order_id, amount))

    def reducer(user_id, records):
        # Separate users and orders
        users = [r for r in records if r[0] == "USER"]
        orders = [r for r in records if r[0] == "ORDER"]

        # Cartesian product (join)
        for user in users:
            for order in orders:
                _, name, country = user
                _, order_id, amount = order
                emit(user_id, (name, order_id, amount))

    # Note: Reduce-side join
    # All records for same user_id go to same reducer
    # Reducer performs join in memory
```

### Pattern 4: Sorting

```python
"""
Pattern: Global sort
"""

def sorting_pattern():
    """
    Example: Sort users by age (descending)

    Input:  (user_id, age)
    Output: Users sorted by age
    """

    def mapper(user_record):
        user_id, age = parse(user_record)
        # Emit with age as key (MapReduce sorts by key!)
        emit(age, user_id)

    # No reducer needed!
    # MapReduce framework sorts keys automatically
    # Output: Keys in sorted order

    # For descending order: Emit negative age
    # emit(-age, user_id)

    """
    Advanced: Total order sort (use partitioner)
    - Sample data to find quantiles
    - Partition based on key ranges
    - Each partition sorted independently
    - Concatenate partitions → globally sorted
    """
```

### Pattern 5: Top-K

```python
"""
Pattern: Find top K items
"""

def top_k_pattern():
    """
    Example: Top 100 products by revenue

    Input:  (product_id, revenue)
    Output: Top 100 products
    """

    def mapper(product_record):
        product_id, revenue = parse(product_record)
        emit("all", (revenue, product_id))  # Single key for all products

    def reducer(key, products):
        # Sort by revenue (descending)
        sorted_products = sorted(products, key=lambda x: x[0], reverse=True)

        # Take top 100
        top_100 = sorted_products[:100]

        for revenue, product_id in top_100:
            emit(product_id, revenue)

    # Problem: Single reducer bottleneck!
    # Better: Two-stage MapReduce
    #   Stage 1: Each mapper emits local top 100
    #   Stage 2: Reducer finds global top 100
```

### Pattern 6: Inverted Index

```python
"""
Pattern: Build search index
"""

def inverted_index_pattern():
    """
    Example: Build inverted index for documents

    Input:  Documents (doc_id, text)
    Output: (word, [doc_ids])
    """

    def mapper(doc_id, text):
        words = tokenize(text)
        for word in words:
            emit(word, doc_id)

    def reducer(word, doc_ids):
        # Remove duplicates, sort
        unique_docs = sorted(set(doc_ids))
        emit(word, unique_docs)

    # Output: [("mapreduce", [1, 5, 23]), ("hadoop", [1, 2, 8]), ...]
    # → Search index for full-text search
```

---

## Performance Optimization

🏗️ **ARCHITECT**: Making MapReduce jobs faster.

### Optimization Techniques

```java
/**
 * MapReduce performance optimization
 */
public class MapReduceOptimization {

    /**
     * 1. Use Combiner (critical!)
     */
    public void useCombiner() {
        /**
         * Problem: Shuffle transfers massive data
         *
         * Example: Word count
         *   Map output: 1B records
         *   Network transfer: 1B records (slow!)
         *
         * Solution: Combiner (local aggregation)
         *   Map output: 1B records
         *   After combine: 1M records (1000x reduction!)
         *   Network transfer: 1M records (fast!)
         *
         * Implementation:
         */
        job.setCombinerClass(IntSumReducer.class);

        /**
         * Requirement: Combiner must be associative & commutative
         *   - SUM: ✓  (a+b)+c = a+(b+c), a+b = b+a)
         *   - AVG: ✗  (need sum and count separately)
         *   - MAX: ✓
         *   - MIN: ✓
         */
    }

    /**
     * 2. Compression
     */
    public void useCompression() {
        /**
         * Compress intermediate data (map output)
         *   - Reduce network transfer
         *   - Trade CPU for I/O (usually worth it)
         *
         * Compression codecs:
         *   - Snappy: Fast, moderate compression (recommended)
         *   - LZO: Fast, moderate compression
         *   - Gzip: Slow, high compression
         */

        conf.setBoolean("mapreduce.map.output.compress", true);
        conf.setClass("mapreduce.map.output.compress.codec",
                      SnappyCodec.class, CompressionCodec.class);

        /**
         * Results:
         *   - Shuffle data: 70-90% smaller
         *   - Job time: 20-40% faster
         *   - CPU usage: +10%
         */
    }

    /**
     * 3. Tune number of reducers
     */
    public void tuneReducers() {
        /**
         * Too few reducers:
         *   - Each reducer processes too much data
         *   - Long running time
         *   - Memory pressure
         *
         * Too many reducers:
         *   - Small output files (inefficient for HDFS)
         *   - High overhead (task setup)
         *
         * Rule of thumb:
         *   num_reducers = 0.95 * (nodes * mapred.tasktracker.reduce.tasks.maximum)
         *
         * Or:
         *   num_reducers = (total_data_size / 1GB)
         */

        job.setNumReduceTasks(200);  // Explicit setting

        /**
         * Special case: num_reducers = 0
         *   → Map-only job (no shuffle!)
         *   → Very fast for filtering, transformation
         */
    }

    /**
     * 4. Custom partitioner
     */
    public void customPartitioner() {
        /**
         * Default: hash(key) % num_reducers
         * Problem: Uneven distribution (data skew)
         *
         * Example: User activity by user_id
         *   - Most users: 1-10 events
         *   - Power users: 1M+ events
         *   - One reducer gets 1M events (slow!)
         *
         * Solution: Custom partitioner
         */

        public class SkewedPartitioner extends Partitioner<Text, IntWritable> {
            @Override
            public int getPartition(Text key, IntWritable value, int numPartitions) {
                String userId = key.toString();

                // Special handling for power users
                if (isPowerUser(userId)) {
                    // Spread power users across multiple reducers
                    return hash(userId + random()) % numPartitions;
                }

                // Normal users: standard partitioning
                return hash(userId) % numPartitions;
            }
        }

        job.setPartitionerClass(SkewedPartitioner.class);
    }

    /**
     * 5. Memory tuning
     */
    public void tuneMemory() {
        /**
         * Map task memory:
         */
        conf.set("mapreduce.map.memory.mb", "2048");  // 2GB
        conf.set("mapreduce.map.java.opts", "-Xmx1638m");  // 80% of 2GB

        /**
         * Reduce task memory:
         */
        conf.set("mapreduce.reduce.memory.mb", "4096");  // 4GB
        conf.set("mapreduce.reduce.java.opts", "-Xmx3277m");  // 80% of 4GB

        /**
         * Sort buffer:
         */
        conf.setInt("mapreduce.task.io.sort.mb", 512);  // 512MB sort buffer

        /**
         * Shuffle memory:
         */
        conf.setFloat("mapreduce.reduce.shuffle.input.buffer.percent", 0.7f);  // 70% of reduce memory
    }

    /**
     * 6. Data locality
     */
    public void dataLocality() {
        /**
         * Problem: Network transfer is slow
         * Solution: Move computation to data (not data to computation)
         *
         * MapReduce automatically schedules map tasks on nodes with data
         *
         * Levels:
         *   1. Node-local: Data on same node (best)
         *   2. Rack-local: Data on same rack (ok)
         *   3. Off-rack: Data on different rack (slow)
         *
         * Monitoring:
         *   - Check job counters for locality percentages
         *   - Goal: >80% node-local
         *
         * If low locality:
         *   - HDFS replication factor too low
         *   - Cluster too busy (can't schedule locally)
         *   - Input split size too small
         */
    }

    /**
     * 7. Reduce-side join vs Map-side join
     */
    public void joinOptimization() {
        /**
         * Reduce-side join (default):
         *   - Both datasets shuffled
         *   - Network-intensive
         *   - Works for any input sizes
         *
         * Map-side join (faster):
         *   - One dataset small enough to fit in memory
         *   - Load small dataset in mapper (distributed cache)
         *   - Join in mapper (no shuffle!)
         *   - 10x faster
         */

        // Add small dataset to distributed cache
        job.addCacheFile(new URI("/data/small_users.txt"));

        // In mapper:
        Map<String, User> userMap = loadFromCache();  // Load once

        // For each record, join in memory:
        public void map(Text key, Text value, Context context) {
            Order order = parse(value);
            User user = userMap.get(order.getUserId());  // Memory lookup!
            emit(order.getId(), join(user, order));
        }

        // No reducer needed!
    }
}
```

### Performance Benchmarks

```
Typical MapReduce performance (1TB input):
──────────────────────────────────────────

Baseline:
  - 100 nodes
  - No optimization
  - Time: 4 hours

With combiner:
  - Time: 2.5 hours (1.6x faster)

With compression:
  - Time: 2 hours (2x faster)

With combiner + compression:
  - Time: 1.5 hours (2.7x faster)

With map-side join (if applicable):
  - Time: 20 minutes (12x faster!)
```

---

## When to Use Batch Processing

🎓 **PROFESSOR**: Decision framework for choosing batch processing.

### Use Batch Processing When:

```
✓ Data is bounded (finite dataset)
✓ Latency > 1 hour is acceptable
✓ Data size > 100GB
✓ Need high throughput (TB/day)
✓ Can tolerate reprocessing on failure

Examples:
  - Daily ETL (extract-transform-load)
  - Monthly reports
  - ML model training
  - Log analysis
  - Data warehouse loading
```

### Don't Use Batch Processing When:

```
✗ Need low latency (< 1 minute)
✗ Data is unbounded (continuous stream)
✗ Need incremental results
✗ Interactive queries

Examples:
  - Fraud detection (use stream processing)
  - Real-time dashboards (use stream processing)
  - User-facing queries (use database)
  - Live recommendations (use stream processing + cache)
```

### Batch vs Stream vs Database

```
┌───────────────────────────────────────────────────────────────┐
│                       DECISION MATRIX                          │
├───────────────────────────────────────────────────────────────┤
│                                                                │
│ Latency < 100ms  + Data size < 1GB    → Database (OLTP)       │
│ Latency < 1s     + Unbounded input    → Stream Processing     │
│ Latency < 1hr    + Data size > 100GB  → Batch Processing      │
│ Complex queries  + Historical data    → Data Warehouse (OLAP) │
│                                                                │
└───────────────────────────────────────────────────────────────┘

Hybrid architectures:
────────────────────
Lambda Architecture:
  - Batch layer: Process all historical data (accuracy)
  - Speed layer: Process recent data (low latency)
  - Serving layer: Merge results

Kappa Architecture:
  - Stream processing only
  - Reprocess by replaying stream
  - Simpler than Lambda
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### Using MapReduce in System Design

```
Scenario: "Design a system to analyze clickstream data"
───────────────────────────────────────────────────────

Requirements Clarification:
  Q: What's the data volume?
  A: 10TB/day of click logs

  Q: What kind of analysis?
  A: Daily reports - top pages, user sessions, conversion funnels

  Q: Latency requirements?
  A: Results needed next morning (overnight batch job)

Capacity Estimation:
  Data: 10TB/day = 3.6PB/year
  Processing: 10TB in 2 hours (with 100-node cluster)

Design:
  ┌──────────────────────────────────────────────────┐
  │                                                  │
  │  Web Servers → Kafka → HDFS (raw logs)          │
  │                           ↓                      │
  │                   MapReduce Jobs (nightly)       │
  │                           ↓                      │
  │                   Aggregated Results             │
  │                           ↓                      │
  │                   BI Tools (Tableau, etc.)       │
  │                                                  │
  └──────────────────────────────────────────────────┘

MapReduce Jobs:
  1. Page Views per URL (aggregation pattern)
  2. User Sessions (grouping pattern)
  3. Conversion Funnel (filtering + joining)

Deep Dive:
  - Job 1: Page Views
    * Map: (log_line) → (url, 1)
    * Reduce: (url, [1,1,1...]) → (url, sum)
    * Output: Top 1000 pages by views

  - Job 2: User Sessions
    * Map: (log_line) → (user_id, (timestamp, url))
    * Reduce: Group clicks, sessionize (30-min timeout)
    * Output: (user_id, session_id, duration, pages)

  - Job 3: Conversion Funnel
    * Input: Sessions + Purchases
    * Map-side join: Load purchases in memory
    * Map: Enrich sessions with purchase data
    * Reduce: Count conversions per step

Optimization:
  - Use Parquet format (columnar, compressed)
  - Partition by date (skip old data)
  - Use combiner for aggregations
  - Compress intermediate data (Snappy)
```

### Interview Talking Points

```
When to mention MapReduce:
 "For this batch processing workload, we can use MapReduce..."
 "Since we're processing TBs of data overnight, MapReduce is a good fit..."
 "We need fault tolerance for this long-running job, so MapReduce handles that..."

Key points to demonstrate understanding:
 1. Data locality: "MapReduce schedules tasks on nodes with data"
 2. Fault tolerance: "Map tasks can be re-executed on failure"
 3. Scalability: "Add more nodes for more throughput"
 4. Simplicity: "Just write map() and reduce(), framework handles rest"

Trade-offs:
 + High throughput
 + Fault tolerant
 + Scales to PBs
 - High latency (minutes to hours)
 - Not suitable for real-time
 - Overkill for small data (<1GB)

Modern alternatives:
 "For this use case, Apache Spark might be better because..."
 "If we need SQL queries, we could use Hive on top of MapReduce..."
 "For real-time processing, we'd use Spark Streaming or Flink instead..."
```

---

## 🧠 MIND MAP: BATCH PROCESSING

```
           BATCH PROCESSING
                  |
      ┌───────────┼───────────┐
      ↓           ↓           ↓
  MAPREDUCE    HADOOP       PATTERNS
      |           |            |
  ┌───┴───┐   ┌───┴────┐  ┌───┴────┐
  ↓       ↓   ↓        ↓  ↓        ↓
 Map   Reduce HDFS   YARN Filter  Join
  |       |    |        |   |        |
  ↓       ↓    ↓        ↓   ↓        ↓
Parse  Aggregate Blocks Tasks Data  Shuffle
  |       |      |        |   |        |
Emit   Group  128MB  Containers Key  Reduce
```

---

## 💡 EMOTIONAL ANCHORS

### 1. MapReduce = Factory Assembly Line 🏭
- Raw materials (data) arrive at factory
- Split into stations (mappers)
- Each station processes independently (parallel)
- Results gathered (shuffle)
- Final assembly (reduce)
- Quality control (fault tolerance)

### 2. HDFS = Library with Multiple Copies 📚
- Each book (block) has 3 copies
- Stored in different rooms (DataNodes)
- Librarian knows where all copies are (NameNode)
- If one copy destroyed, make new copy from other 2
- Never lose a book!

### 3. Shuffle = Sorting Mail 📮
- Letters arrive mixed up (map output)
- Sort by zip code (group by key)
- Put all letters for same zip code together
- Deliver batches to post office (reducer)
- Most time-consuming step!

### 4. Combiner = Pre-sorting at Home 🏠
- Before mailing 100 letters to same address
- Put them in one envelope (local aggregation)
- Save postage (network bandwidth)
- Faster delivery!

### 5. Fault Tolerance = Understudy Actor 🎭
- Main actor (task) rehearses scene
- If main actor sick (fails), understudy takes over
- Performs same scene (re-execute)
- Audience doesn't notice (transparent recovery)

### 6. Data Locality = Bring Kitchen to Food 🍳
- Don't carry heavy ingredients across city (network transfer)
- Instead, cook where ingredients are stored
- Much faster!
- MapReduce schedules tasks where data lives

---

## 🔑 Key Takeaways

### Core Concepts

1. **Batch processing trades latency for throughput**
   - Process large amounts of data efficiently
   - Accept high latency (minutes to hours)
   - Optimize for throughput (TBs per hour)

2. **MapReduce = Divide and Conquer**
   - Split data into chunks
   - Process in parallel (map)
   - Aggregate results (reduce)
   - Framework handles complexity

3. **HDFS = Distributed storage for big data**
   - Store files across cluster
   - Large blocks (128MB)
   - 3x replication for fault tolerance
   - Optimized for sequential reads

### Design Principles

4. **Move computation to data (not data to computation)**
   - Data locality is critical
   - Network is bottleneck
   - Schedule tasks on nodes with data

5. **Assume failures are normal**
   - Tasks fail → re-execute
   - Nodes fail → reschedule tasks
   - Deterministic functions enable fault tolerance

6. **Shuffle is the bottleneck**
   - Network transfer + sorting
   - Optimize with combiners
   - Use compression
   - Avoid shuffle when possible (map-side join)

### Practical Wisdom

7. **Use combiner whenever possible**
   - Reduces shuffle data by 10-1000x
   - Must be associative and commutative
   - Single biggest optimization

8. **Right tool for right job**
   - MapReduce: Batch processing, high throughput
   - Spark: Iterative algorithms, interactive queries
   - Stream processing: Real-time, unbounded data
   - Database: Low latency, point queries

9. **Hadoop is more than MapReduce**
   - HDFS: Distributed storage
   - YARN: Resource management
   - Hive: SQL on Hadoop
   - Spark: Faster than MapReduce

### Interview Gold

10. **MapReduce enabled big data revolution**
    - Google paper (2004) changed everything
    - Hadoop democratized big data processing
    - Influenced: Spark, Flink, BigQuery, Presto
    - Still used in production at massive scale

---

**Final Thought**: MapReduce is the foundation of big data processing. Even if you use Spark or Flink today, understanding MapReduce helps you understand how distributed data processing works. Master MapReduce, and modern frameworks will make much more sense. The concepts—data locality, fault tolerance, shuffle optimization—apply universally to all big data systems.
