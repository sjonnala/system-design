# Advanced Data Formats: Apache Iceberg, Apache Hudi, and Columnar Storage

> Deep dive into modern data lake table formats and columnar storage with Parquet and Dremel

## Table of Contents
- [Overview](#overview)
- [Columnar Storage](#columnar-storage)
- [Apache Parquet](#apache-parquet)
- [Google Dremel](#google-dremel)
- [Table Formats Overview](#table-formats-overview)
- [Apache Iceberg](#apache-iceberg)
- [Apache Hudi](#apache-hudi)
- [Comparison and Use Cases](#comparison-and-use-cases)
- [Interview Tips](#interview-tips)

## Overview

Modern data architectures use specialized file formats and table formats to efficiently store and query massive datasets in data lakes.

### The Evolution

```
1st Generation: Files in HDFS/S3
├─ data/2024/01/15/part-001.csv
├─ data/2024/01/15/part-002.csv
└─ data/2024/01/16/part-001.csv

Problems:
- No schema enforcement
- Manual partition management
- No ACID transactions
- Slow queries (scan all files)

2nd Generation: Columnar Formats (Parquet)
├─ data/2024/01/15/part-001.parquet
└─ metadata in footer (column stats)

Benefits:
- Columnar storage (fast analytics)
- Compression
- Column pruning, predicate pushdown

3rd Generation: Table Formats (Iceberg, Hudi)
├─ Metadata layer (schema, partitions, snapshots)
├─ ACID transactions
├─ Time travel
└─ Schema evolution

Benefits:
- All benefits of Parquet
- + Transactional semantics
- + Metadata optimization
- + Advanced features
```

## Columnar Storage

### Row-Oriented vs Column-Oriented

**Row-Oriented (CSV, Avro, traditional databases):**
```
Table: Employees
┌────┬──────┬─────┬────────┐
│ ID │ Name │ Age │ Salary │
├────┼──────┼─────┼────────┤
│ 1  │ Alice│ 30  │ 100000 │
│ 2  │ Bob  │ 25  │ 80000  │
│ 3  │ Carol│ 35  │ 120000 │
└────┴──────┴─────┴────────┘

Storage (Row-oriented):
[1,Alice,30,100000][2,Bob,25,80000][3,Carol,35,120000]

Good for: OLTP (read/write full rows)
```

**Column-Oriented (Parquet, ORC, Dremel):**
```
Storage (Column-oriented):
ID:     [1][2][3]
Name:   [Alice][Bob][Carol]
Age:    [30][25][35]
Salary: [100000][80000][120000]

Good for: OLAP (read few columns, aggregate)
```

### Why Columnar for Analytics?

**Query Example:**
```sql
SELECT AVG(salary) FROM employees WHERE age > 30;
```

**Row-oriented:**
```
Read all columns for all rows → Filter → Aggregate
[1,Alice,30,100000] ✗ (age <= 30)
[2,Bob,25,80000]    ✗ (age <= 30)
[3,Carol,35,120000] ✓ → Include in average

Read 4 columns × 3 rows = 12 values
```

**Column-oriented:**
```
Read only Age and Salary columns
Age:    [30, 25, 35]   → Filter index 2 (age > 30)
Salary: [120000]       → Average

Read 2 columns × 3 rows = 6 values (50% less!)

For millions of rows with 100s of columns:
Read 2 columns instead of 100 = 98% reduction!
```

### Compression Benefits

**Columnar data compresses better:**
```
Row-oriented:
[1,Alice,30,100000][2,Bob,25,80000][3,Carol,35,120000]
Mixed types, less repetition → Lower compression

Column-oriented:
Age: [30,25,35,30,31,28,27,30,32,29,...]
Same type, patterns → Higher compression

Compression techniques:
- Run-Length Encoding: [30×5, 25×3, 35×2]
- Dictionary Encoding: {30→0, 25→1, 35→2} → [0,1,2,0,0,...]
- Delta Encoding: [30, -5, +10, -5, +1, -3, -1, +3, +2, -3]
```

**Compression Ratios:**
```
┌─────────────┬──────────────┬────────────────┐
│ Format      │ Size (GB)    │ Compression    │
├─────────────┼──────────────┼────────────────┤
│ CSV         │ 100          │ 1x (baseline)  │
│ Parquet     │ 10-20        │ 5-10x          │
│ ORC         │ 8-15         │ 6-12x          │
└─────────────┴──────────────┴────────────────┘
```

## Apache Parquet

Apache Parquet is a columnar storage format designed for Hadoop ecosystem.

### Parquet File Structure

```
┌────────────────────────────────────────────────┐
│              Parquet File                      │
├────────────────────────────────────────────────┤
│  Magic Number: "PAR1"                          │
├────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────┐ │
│  │         Row Group 1                      │ │
│  │  ┌────────────────────────────────────┐  │ │
│  │  │ Column Chunk: ID                   │  │ │
│  │  │  - Page 1 (Data + Stats)           │  │ │
│  │  │  - Page 2 (Data + Stats)           │  │ │
│  │  └────────────────────────────────────┘  │ │
│  │  ┌────────────────────────────────────┐  │ │
│  │  │ Column Chunk: Name                 │  │ │
│  │  │  - Page 1 (Data + Stats)           │  │ │
│  │  └────────────────────────────────────┘  │ │
│  │  ┌────────────────────────────────────┐  │ │
│  │  │ Column Chunk: Age                  │  │ │
│  │  │  - Page 1 (Data + Stats)           │  │ │
│  │  └────────────────────────────────────┘  │ │
│  └──────────────────────────────────────────┘ │
│  ┌──────────────────────────────────────────┐ │
│  │         Row Group 2                      │ │
│  │  (Same structure)                        │ │
│  └──────────────────────────────────────────┘ │
├────────────────────────────────────────────────┤
│              Footer (Metadata)                 │
│  - Schema                                      │
│  - Row group metadata (locations, stats)      │
│  - Column metadata (encodings, compression)   │
├────────────────────────────────────────────────┤
│  Footer Length (4 bytes)                       │
│  Magic Number: "PAR1"                          │
└────────────────────────────────────────────────┘
```

**Key Components:**

1. **Row Group**: Collection of rows (typically 128 MB)
2. **Column Chunk**: Data for one column in one row group
3. **Page**: Unit of compression/encoding (typically 1 MB)
4. **Footer**: Metadata for entire file (schema, statistics)

### Writing Parquet (Spark Example)

```scala
// Write DataFrame to Parquet
df.write
  .mode("overwrite")
  .option("compression", "snappy")           // Fast compression
  .option("parquet.block.size", 134217728)   // 128 MB row groups
  .option("parquet.page.size", 1048576)      // 1 MB pages
  .partitionBy("year", "month", "day")       // Partition by date
  .parquet("s3://bucket/data/events")

// Result:
// s3://bucket/data/events/
//   year=2024/month=01/day=15/part-00000.parquet
//   year=2024/month=01/day=16/part-00000.parquet
```

### Reading Parquet

```scala
// Read Parquet with Spark
val df = spark.read.parquet("s3://bucket/data/events")

// Query with predicate pushdown
df.filter($"age" > 30)
  .select("name", "salary")
  .show()

// Spark optimization:
// 1. Partition pruning (if partitioned by date)
// 2. Column pruning (read only "age", "name", "salary")
// 3. Predicate pushdown (use row group stats to skip row groups)
```

### Metadata and Statistics

Parquet footer contains statistics for predicate pushdown:

```
Row Group 1 Metadata:
  Column: age
    - min: 25
    - max: 45
    - null_count: 0
    - distinct_count: ~20

  Column: salary
    - min: 50000
    - max: 150000
    - null_count: 5

Query: SELECT * FROM table WHERE age > 50

Engine reads footer:
  Row Group 1: max(age) = 45 → SKIP (no values > 50)
  Row Group 2: max(age) = 65 → READ
```

### Encoding and Compression

**Encodings:**
```
1. Plain: Raw values
   [100000, 80000, 120000, 90000]

2. Dictionary: Map values to IDs
   Dictionary: {100000→0, 80000→1, 120000→2, 90000→3}
   Data: [0, 1, 2, 3]
   (Efficient for low cardinality)

3. RLE (Run-Length Encoding): Repeated values
   [30, 30, 30, 30, 25, 25]
   → [(30, count=4), (25, count=2)]

4. Delta: Differences between values
   [100, 105, 110, 115]
   → Base: 100, Deltas: [5, 5, 5]

5. Delta Binary Packed: Delta + bit packing
   Small deltas packed into fewer bits
```

**Compression Codecs:**
```
┌──────────┬────────────┬─────────────┬──────────────┐
│ Codec    │ Ratio      │ Speed       │ CPU Usage    │
├──────────┼────────────┼─────────────┼──────────────┤
│ Snappy   │ ~2x        │ Very Fast   │ Low          │
│ GZIP     │ ~3x        │ Moderate    │ Moderate     │
│ LZ4      │ ~2x        │ Very Fast   │ Very Low     │
│ ZSTD     │ ~3-4x      │ Fast        │ Moderate     │
│ Brotli   │ ~4x        │ Slow        │ High         │
└──────────┴────────────┴─────────────┴──────────────┘

Recommendation: Snappy (default) - good balance
```

### Nested Data Support

Parquet supports complex nested types:

```
Schema:
{
  "name": "string",
  "addresses": [
    {
      "street": "string",
      "city": "string",
      "zip": "int"
    }
  ],
  "phone_numbers": ["string"]
}

Storage (Dremel encoding - see next section):
name:                   [Alice][Bob]
addresses.street:       [123 Main][456 Oak][789 Elm]
addresses.city:         [NYC][LA][NYC]
addresses.zip:          [10001][90001][10002]
addresses (repetition): [0][0,1][0]  ← Indicates list structure
phone_numbers:          [555-1234][555-5678,555-9999]
```

## Google Dremel

Dremel is Google's interactive ad-hoc query system for read-only nested data (basis for BigQuery).

### Dremel's Contribution

1. **Columnar storage for nested data** (Parquet uses this)
2. **Tree architecture for distributed queries**
3. **Interactive query speeds** (seconds on petabytes)

### Nested Data Encoding

**Problem**: How to store nested/repeated data in columnar format?

**Example Document:**
```json
{
  "name": "Alice",
  "addresses": [
    {"city": "NYC", "zip": 10001},
    {"city": "LA",  "zip": 90001}
  ]
}
```

**Dremel Encoding** (Repetition and Definition Levels):

```
Column: addresses.city

Record 1:
  name: Alice
  addresses: [
    {city: NYC, zip: 10001},
    {city: LA, zip: 90001}
  ]

Storage:
value:          [NYC, LA]
repetition:     [0, 1]      ← 0 = new record, 1 = repeated in same list
definition:     [2, 2]      ← 2 = fully defined (not null)

Record 2:
  name: Bob
  addresses: []             ← Empty list

Storage:
value:          []
repetition:     [0]
definition:     [1]         ← 1 = list exists but empty

Record 3:
  name: Carol
  addresses: null           ← Null

Storage:
value:          []
repetition:     [0]
definition:     [0]         ← 0 = null
```

**Repetition Level**: How many repeated fields need to be reset
**Definition Level**: How many optional fields are defined

This allows reconstructing nested structure from columnar data!

### Dremel Query Architecture

```
┌────────────────────────────────────────────┐
│           Root Server                      │
│  - Parse query                             │
│  - Optimize                                │
│  - Distribute to mixers                    │
└──────────────┬─────────────────────────────┘
               │
       ┌───────┼───────┐
       │       │       │
┌──────▼──┐ ┌──▼────┐ ┌▼──────┐
│ Mixer 1 │ │Mixer 2│ │Mixer 3│  (Intermediate aggregation)
└──┬───┬──┘ └┬────┬─┘ └┬────┬─┘
   │   │     │    │     │    │
┌──▼┐ ┌▼──┐┌▼─┐ ┌▼─┐ ┌▼─┐ ┌▼─┐
│L1 │ │L2 ││L3│ │L4│ │L5│ │L6│  Leaf Servers (scan data)
└───┘ └───┘└──┘ └──┘ └──┘ └──┘

Query execution (seconds):
1. Root parses and plans (ms)
2. Mixers coordinate (ms)
3. Leaves scan in parallel (seconds)
4. Mixers aggregate intermediate results (ms)
5. Root aggregates final result (ms)

Parallelism: 1000s of leaf servers → Interactive speeds
```

**Query Example:**
```sql
SELECT country, SUM(revenue)
FROM sales
WHERE date = '2024-01-15'
GROUP BY country

Execution:
1. Root → Distribute to Mixers
2. Mixers → Distribute to Leaves
3. Leaves → Scan local shards, filter date, partial SUM per country
4. Leaves → Return to Mixers
5. Mixers → Combine partial SUMs
6. Mixers → Return to Root
7. Root → Final aggregation, return to client
```

## Table Formats Overview

Table formats add a metadata layer on top of data files (Parquet/ORC).

### Why Table Formats?

**Problem with raw Parquet files:**
```
s3://bucket/data/events/
  part-00000.parquet
  part-00001.parquet
  part-00002.parquet
  ...

Issues:
1. Which files make up current table? (Manual tracking)
2. How to update data? (Replace files → not atomic)
3. Schema changes? (Manual coordination)
4. Time travel? (No history)
5. Concurrent writes? (Conflicts)
```

**Solution: Table Format (Iceberg/Hudi/Delta)**
```
s3://bucket/data/events/
  metadata/
    v1.metadata.json  ← Schema, partition spec, snapshots
    v2.metadata.json
    snap-123.avro     ← Snapshot metadata
  data/
    file-1.parquet
    file-2.parquet

Table format tracks:
- Current snapshot (version)
- Schema evolution history
- Partition specifications
- File manifest (which files are in table)
- Statistics for query optimization
```

## Apache Iceberg

Apache Iceberg is a table format for huge analytic datasets, designed for high-performance queries and atomic transactions.

### Iceberg Architecture

```
┌────────────────────────────────────────────────────┐
│            Metadata JSON                           │
│  - Schema                                          │
│  - Partition spec                                  │
│  - Snapshot list                                   │
│  - Current snapshot pointer                        │
└────────────────┬───────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
┌───────▼────────┐ ┌──────▼─────────┐
│ Manifest List  │ │ Manifest List  │  (Snapshot 1 & 2)
│  (Snapshot 1)  │ │  (Snapshot 2)  │
└───────┬────────┘ └──────┬─────────┘
        │                 │
    ┌───┴────┐       ┌────┴────┐
    │        │       │         │
┌───▼────┐ ┌▼──────┐ ┌▼──────┐ ┌▼──────┐
│Manifest│ │Manifest│ │Manifest│ │Manifest│  (File metadata)
│ File 1 │ │ File 2│ │ File 3│ │ File 4│
└───┬────┘ └┬──────┘ └┬──────┘ └┬──────┘
    │       │         │         │
    │       │         │         │
┌───▼──┐  ┌─▼───┐  ┌─▼───┐  ┌──▼──┐
│Data  │  │Data │  │Data │  │Data │  (Parquet/ORC files)
│File1 │  │File2│  │File3│  │File4│
└──────┘  └─────┘  └─────┘  └─────┘
```

**Metadata Hierarchy:**
1. **Metadata JSON**: Top-level, points to current snapshot
2. **Manifest List**: Lists all manifest files for a snapshot
3. **Manifest Files**: Lists data files with statistics
4. **Data Files**: Actual data (Parquet, ORC, Avro)

### Creating Iceberg Table

```sql
-- Spark SQL
CREATE TABLE events (
  event_id BIGINT,
  user_id BIGINT,
  event_type STRING,
  timestamp TIMESTAMP,
  properties MAP<STRING, STRING>
)
USING iceberg
PARTITIONED BY (days(timestamp))
LOCATION 's3://bucket/warehouse/events';
```

```scala
// Spark DataFrame
df.writeTo("events")
  .using("iceberg")
  .partitionedBy($"day")
  .tableProperty("write.format.default", "parquet")
  .tableProperty("write.parquet.compression-codec", "zstd")
  .create()
```

### Time Travel

```sql
-- Query current version
SELECT * FROM events;

-- Query as of specific timestamp
SELECT * FROM events
TIMESTAMP AS OF '2024-01-15 10:00:00';

-- Query specific snapshot ID
SELECT * FROM events
VERSION AS OF 12345;

-- List snapshots
SELECT * FROM events.snapshots;
┌─────────────┬─────────────┬─────────────────────┐
│ snapshot_id │ parent_id   │ committed_at        │
├─────────────┼─────────────┼─────────────────────┤
│ 12345       │ NULL        │ 2024-01-15 09:00:00 │
│ 12346       │ 12345       │ 2024-01-15 10:00:00 │
│ 12347       │ 12346       │ 2024-01-15 11:00:00 │
└─────────────┴─────────────┴─────────────────────┘

-- Rollback to previous snapshot
CALL system.rollback_to_snapshot('events', 12346);
```

### Schema Evolution

```sql
-- Add column (no data rewrite!)
ALTER TABLE events ADD COLUMN device_type STRING;

-- Rename column
ALTER TABLE events RENAME COLUMN user_id TO customer_id;

-- Drop column
ALTER TABLE events DROP COLUMN properties;

-- Iceberg tracks schema versions
SELECT * FROM events.history;
┌─────────┬────────────┬──────────────────────┐
│ version │ snapshot   │ schema_change        │
├─────────┼────────────┼──────────────────────┤
│ 1       │ 12345      │ Initial schema       │
│ 2       │ 12346      │ ADD device_type      │
│ 3       │ 12347      │ RENAME user_id       │
└─────────┴────────────┴──────────────────────┘
```

Schema evolution is metadata-only (instant), no data rewrite required!

### Partition Evolution

Change partitioning without rewriting data:

```sql
-- Initial: Partition by day
CREATE TABLE events (...)
PARTITIONED BY (days(timestamp));

-- Later: Too many small files, change to month
ALTER TABLE events
SET PARTITION SPEC (months(timestamp));

-- Old data: Still partitioned by day
-- New data: Partitioned by month
-- Queries work correctly across both!
```

### ACID Transactions

**Concurrent Writes:**
```
Writer 1: INSERT data → Create Snapshot A → Commit
Writer 2: INSERT data → Create Snapshot B → Commit

Iceberg uses optimistic concurrency:
1. Read current metadata version
2. Write data files
3. Create new snapshot
4. Atomically update metadata pointer
   (Fails if metadata changed by another writer)

Retry on conflict with exponential backoff
```

**Example:**
```scala
// Spark supports concurrent writes
spark.sql("INSERT INTO events SELECT ...")  // Writer 1
spark.sql("INSERT INTO events SELECT ...")  // Writer 2

// Both succeed (different partitions) or one retries (same partition)
```

### Hidden Partitioning

Users don't specify partition in queries:

```sql
-- Traditional Hive (manual partition)
SELECT * FROM events
WHERE date_partition = '2024-01-15';  ← User must know partition column

-- Iceberg (hidden partition)
SELECT * FROM events
WHERE timestamp = '2024-01-15';  ← Iceberg automatically uses partition

Benefit:
- Queries work even after partition evolution
- No partition column in application logic
```

## Apache Hudi

Apache Hudi (Hadoop Upserts Deletes and Incrementals) is a table format optimized for streaming upserts and incremental processing.

### Hudi Table Types

#### 1. Copy-on-Write (COW)

```
Storage Format: Parquet files only

Write (UPDATE):
  1. Read entire Parquet file
  2. Apply updates
  3. Write new Parquet file
  4. Update metadata

┌─────────────┐         ┌─────────────┐
│ file-1.parq │ UPDATE  │ file-1.parq │  (New version)
│ [row1]      │  row1   │ [row1']     │
│ [row2]      │  ──────→│ [row2]      │
│ [row3]      │         │ [row3]      │
└─────────────┘         └─────────────┘

Pros:
- Fast reads (pure Parquet)
- No merge at query time

Cons:
- Slow writes (rewrite entire file)
- High I/O for small updates
```

#### 2. Merge-on-Read (MOR)

```
Storage Format: Parquet (base) + Avro (delta logs)

Write (UPDATE):
  1. Append update to delta log (Avro)
  2. Update metadata
  (No Parquet rewrite!)

┌─────────────┐
│ file-1.parq │  (Base - Parquet)
│ [row1]      │
│ [row2]      │
│ [row3]      │
└─────────────┘
      +
┌─────────────┐
│.log (Avro)  │  (Delta logs)
│ UPDATE row1 │
│ DELETE row2 │
└─────────────┘

Read:
  Merge base + deltas on-the-fly

Pros:
- Fast writes (append-only)
- Efficient for streaming

Cons:
- Slower reads (merge required)
- Background compaction needed
```

**Compaction (MOR):**
```
Periodic background job:
  1. Read base Parquet + delta logs
  2. Merge updates
  3. Write new base Parquet
  4. Delete old delta logs

file-1.parq [row1, row2, row3]
.log [UPDATE row1, DELETE row2]
         ↓ Compaction
file-1.parq [row1', row3]  ← New base
```

### Hudi Write Operations

```scala
import org.apache.hudi.QuickstartUtils._
import org.apache.spark.sql.SaveMode._
import org.apache.hudi.DataSourceWriteOptions._
import org.apache.hudi.config.HoodieWriteConfig._

val hudiOptions = Map[String,String](
  "hoodie.table.name" -> "events",
  "hoodie.datasource.write.recordkey.field" -> "event_id",
  "hoodie.datasource.write.partitionpath.field" -> "date",
  "hoodie.datasource.write.precombine.field" -> "timestamp",
  "hoodie.datasource.write.operation" -> "upsert",
  "hoodie.upsert.shuffle.parallelism" -> "100",
  "hoodie.insert.shuffle.parallelism" -> "100"
)

// Upsert (Insert or Update)
df.write
  .format("hudi")
  .options(hudiOptions)
  .mode(Overwrite)
  .save("s3://bucket/events")

// Incremental read (only changes since timestamp)
spark.read
  .format("hudi")
  .option("hoodie.datasource.query.type", "incremental")
  .option("hoodie.datasource.read.begin.instanttime", "20240115100000")
  .load("s3://bucket/events")
```

### Incremental Processing

Hudi's killer feature for streaming:

```
Event Stream (Kafka) → Spark Streaming → Hudi Table
                                           ↓
                         Incremental Queries (only new data)

┌─────────────────────────────────────────────────────┐
│  Hudi Table Timeline                                │
├─────────────────────────────────────────────────────┤
│ 10:00 → Commit 1 (1000 records)                     │
│ 10:05 → Commit 2 (500 records)                      │
│ 10:10 → Commit 3 (800 records)                      │
└─────────────────────────────────────────────────────┘

Incremental Query:
  FROM: 10:00
  TO:   10:10
  → Returns only records from Commits 2 and 3 (1300 records)

Use case:
  Downstream pipeline processes only new/changed data
  (Not full table scan!)
```

**Example:**
```scala
// Initial load
val checkpoint1 = "20240115100000"

// Incremental processing loop
while (true) {
  val incrementalDF = spark.read
    .format("hudi")
    .option("hoodie.datasource.query.type", "incremental")
    .option("hoodie.datasource.read.begin.instanttime", checkpoint1)
    .load("s3://bucket/events")

  // Process new records
  processRecords(incrementalDF)

  // Update checkpoint
  checkpoint1 = getLatestCommitTime()

  Thread.sleep(60000)  // Every minute
}
```

### Hudi Indexing

Hudi uses indexes to locate records for updates:

**Index Types:**
```
1. Bloom Filter (Default for COW)
   - Per file: Bloom filter for record keys
   - Fast probabilistic lookup
   - False positives possible (rare)

2. Simple Index
   - Scan all files
   - Accurate but slow

3. HBase Index
   - External HBase table
   - Fast lookups
   - Requires HBase cluster

4. Bucket Index
   - Hash-based bucketing
   - No index lookup needed
   - Fixed number of buckets
```

**Update Flow with Bloom Index:**
```
Update event_id = 12345

1. Check Bloom filters for all files
   file-1.parq: Maybe contains 12345 ✓
   file-2.parq: Definitely NOT         ✗
   file-3.parq: Maybe contains 12345   ✓

2. Read only files 1 and 3

3. Find record in file-1

4. Apply update (COW: rewrite file-1, MOR: append to log)
```

## Comparison and Use Cases

### Iceberg vs Hudi vs Delta Lake

```
┌──────────────────┬────────────┬────────────┬─────────────┐
│ Feature          │ Iceberg    │ Hudi       │ Delta Lake  │
├──────────────────┼────────────┼────────────┼─────────────┤
│ Vendor           │ Netflix    │ Uber       │ Databricks  │
│ Open Source      │ Apache     │ Apache     │ Linux Found.│
│ ACID             │ Yes        │ Yes        │ Yes         │
│ Time Travel      │ Yes        │ Yes        │ Yes         │
│ Schema Evolution │ Excellent  │ Good       │ Good        │
│ Upserts          │ Yes        │ Optimized  │ Yes         │
│ Incremental      │ Yes        │ Native     │ Yes         │
│ Partition Evolve │ Yes        │ No         │ No          │
│ Hidden Partition │ Yes        │ No         │ No          │
│ Streaming Focus  │ Batch+     │ Streaming+ │ Batch+      │
│ Engine Support   │ Multi      │ Multi      │ Spark-first │
└──────────────────┴────────────┴────────────┴─────────────┘
```

### When to Use Each

**Apache Iceberg:**
```
✓ Analytical workloads (OLAP)
✓ Complex schema evolution needed
✓ Partition scheme changes over time
✓ Multiple query engines (Spark, Flink, Presto, Trino)
✓ Large batch updates
✓ Netflix-scale data lakes

Example: Data warehouse with evolving schema
```

**Apache Hudi:**
```
✓ Streaming upserts (CDC, event streams)
✓ Incremental processing pipelines
✓ Record-level updates frequently
✓ Need to track changes incrementally
✓ Uber-style real-time data lakes

Example: Real-time analytics on streaming data
```

**Delta Lake:**
```
✓ Databricks ecosystem
✓ Spark-centric workloads
✓ Need ACID on data lake
✓ Time travel for ML experiments
✓ Unified batch and streaming

Example: ML feature stores, Databricks-native pipelines
```

### Performance Characteristics

**Query Performance:**
```
┌─────────────┬──────────┬──────────┬────────────┐
│ Operation   │ Iceberg  │ Hudi COW │ Hudi MOR   │
├─────────────┼──────────┼──────────┼────────────┤
│ Read        │ Fast     │ Fast     │ Moderate   │
│ Write       │ Moderate │ Slow     │ Fast       │
│ Update      │ Moderate │ Slow     │ Fast       │
│ Scan        │ Fast     │ Fast     │ Moderate   │
└─────────────┴──────────┴──────────┴────────────┘

Iceberg: Optimized for read-heavy analytical workloads
Hudi COW: Optimized for read performance, slower writes
Hudi MOR: Optimized for write performance, slower reads
```

**Storage Efficiency:**
```
Iceberg:
- Metadata overhead: Low (manifest files)
- Snapshot retention: Configurable
- Compaction: Not required (merge on write)

Hudi MOR:
- Metadata overhead: Low
- Delta logs: Accumulate over time
- Compaction: Required regularly

Hudi COW:
- Metadata overhead: Low
- No delta logs
- Compaction: Not needed
```

## Real-World Use Cases

### Use Case 1: Netflix - Iceberg for Analytics

```
Data Lake (S3)
  ↓
Iceberg Tables (100s of PB)
  ├─ Viewing history (petabytes)
  ├─ A/B test results
  ├─ Recommendations data
  └─ Content metadata

Benefits:
- Schema evolution (add columns for new features)
- Partition evolution (daily → hourly for popular shows)
- Time travel (reproduce ML experiments)
- Multiple engines (Spark, Presto, Trino)

Example:
┌──────────────────────────────────────────────────────┐
│ CREATE TABLE viewing_history                         │
│ USING iceberg                                        │
│ PARTITIONED BY (months(view_timestamp))              │
│                                                      │
│ -- Later: Changed to days for recent data           │
│ ALTER TABLE viewing_history                          │
│ SET PARTITION SPEC (days(view_timestamp))            │
│                                                      │
│ -- Query works across both partition schemes!       │
└──────────────────────────────────────────────────────┘
```

### Use Case 2: Uber - Hudi for Real-Time

```
Kafka (Driver locations, trip events)
  ↓
Spark Streaming
  ↓
Hudi MOR Tables
  ↓
Incremental ETL → Downstream systems

Benefits:
- Fast upserts (driver location updates)
- Incremental processing (only changed trips)
- Record-level updates (trip status changes)
- Late data handling (trip corrections)

Example:
┌──────────────────────────────────────────────────────┐
│ Table: trips                                         │
│ - 1 billion trips                                    │
│ - 1M updates/minute (status changes)                 │
│                                                      │
│ Hudi MOR:                                            │
│ - Append updates to delta logs (fast)                │
│ - Compaction every 30 minutes                        │
│ - Incremental queries for downstream                 │
└──────────────────────────────────────────────────────┘
```

### Use Case 3: CDC with Iceberg/Hudi

```
MySQL (orders table)
  ↓
Debezium CDC → Kafka
  ↓
Spark Streaming
  ↓
Hudi Table (upserts)
  ↓
Analytics

┌──────────────────────────────────────────────────────┐
│ MySQL Change    → Hudi Operation                     │
├──────────────────────────────────────────────────────┤
│ INSERT order    → INSERT into Hudi                   │
│ UPDATE status   → UPSERT (by order_id)               │
│ DELETE order    → DELETE from Hudi                   │
└──────────────────────────────────────────────────────┘

Result: Real-time copy of MySQL in data lake
```

## Interview Tips

### Key Concepts to Emphasize

**Columnar Storage:**
1. **Column pruning**: Read only needed columns
2. **Better compression**: Similar data types together
3. **Predicate pushdown**: Filter using column statistics
4. **OLAP optimized**: Aggregations on few columns

**Parquet:**
1. **Row groups**: Horizontal partitioning (128 MB default)
2. **Footer metadata**: Statistics for optimization
3. **Nested types**: Dremel encoding
4. **Compression**: Snappy/GZIP/ZSTD

**Iceberg:**
1. **Hidden partitioning**: Partition evolution without query changes
2. **Time travel**: Query historical snapshots
3. **Schema evolution**: Metadata-only (instant)
4. **ACID**: Optimistic concurrency

**Hudi:**
1. **COW vs MOR**: Trade-off between read and write speed
2. **Incremental queries**: Process only changes
3. **Upsert optimized**: Record-level updates
4. **Timeline**: Track all commits

### Common Interview Questions

**Q: Why use columnar storage for analytics?**

A: Columnar storage is optimized for OLAP because:
1. **Column pruning**: Read only needed columns (vs all columns in row format)
2. **Better compression**: Similar data types compress better (10x typical)
3. **Predicate pushdown**: Skip row groups using min/max statistics
4. **CPU cache**: Sequential access to same-type data

Example: `SELECT AVG(salary) WHERE age > 30` only reads 2 columns, not entire row.

**Q: What problem do table formats (Iceberg/Hudi) solve?**

A: Raw Parquet files lack:
1. **ACID transactions**: No atomic updates
2. **Schema evolution**: Manual coordination needed
3. **Time travel**: No snapshot history
4. **Metadata**: Manual file tracking

Table formats add metadata layer providing transactional semantics, schema evolution, time travel, and query optimization on data lakes.

**Q: Iceberg vs Hudi - when to use which?**

A:
**Use Iceberg** for:
- Analytical workloads (batch-heavy)
- Complex schema evolution
- Partition evolution needed
- Multi-engine access (Spark, Flink, Trino)

**Use Hudi** for:
- Streaming upserts (CDC, real-time)
- Incremental processing
- Frequent record-level updates
- Need MOR for write performance

**Q: How does Parquet achieve high compression?**

A: Multiple techniques:
1. **Columnar layout**: Same types together
2. **Encoding**: Dictionary, RLE, Delta for different patterns
3. **Compression**: Snappy/GZIP/ZSTD on encoded data
4. **Nested types**: Efficient Dremel encoding

Result: 5-10x compression typical (100 GB CSV → 10-20 GB Parquet)

**Q: Explain Hudi's incremental processing.**

A: Hudi maintains a timeline of commits. Incremental queries specify:
- Start time: `begin.instanttime`
- End time: Optional

Hudi returns only records added/updated between those times. Enables:
- Downstream ETL processes only changes (not full scan)
- Efficient streaming pipelines
- Change tracking without external tools

**Q: What is Dremel's contribution to modern data systems?**

A: Dremel pioneered:
1. **Nested data in columnar format**: Repetition/definition levels
2. **Interactive queries on petabytes**: Tree-based distributed execution
3. **Separation of storage/compute**: Cloud-native architecture

Parquet uses Dremel's encoding. BigQuery is based on Dremel architecture.

## Summary

### Columnar Storage Benefits
- **10x smaller**: Better compression than row formats
- **Faster queries**: Read only needed columns
- **Predicate pushdown**: Skip data using statistics
- **OLAP optimized**: Perfect for analytics

### Parquet
- Industry-standard columnar format
- Nested type support (Dremel encoding)
- Rich metadata for optimization
- Hadoop ecosystem integration

### Iceberg
- Hidden partitioning (partition evolution)
- Time travel and rollback
- Schema evolution (metadata-only)
- Multi-engine support

### Hudi
- Streaming upsert optimized
- COW vs MOR trade-offs
- Incremental processing
- Timeline-based versioning

### When to Use What

**Columnar (Parquet):**
- Analytical queries
- Data lake storage
- Foundation for table formats

**Iceberg:**
- Batch analytics
- Complex schema changes
- Multi-engine access
- Netflix-scale data lakes

**Hudi:**
- Streaming pipelines
- CDC integration
- Real-time analytics
- Uber-style updates

---

**Related Topics:**
- [Stream Processing: Flink](stream-processing.md)
- [Data Warehousing: Snowflake, Mesa](data-warehousing.md)
- [Data Capture: Debezium](data-capture.md)

**References:**
- [Apache Parquet Documentation](https://parquet.apache.org/docs/)
- [Apache Iceberg Documentation](https://iceberg.apache.org/)
- [Apache Hudi Documentation](https://hudi.apache.org/)
- Melnik et al., "Dremel: Interactive Analysis of Web-Scale Datasets" (VLDB 2010)
- Distributed Data Management with Apache Iceberg (Netflix Blog)
