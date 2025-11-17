# Data Warehousing Concepts: Snowflake and Mesa

> Deep dive into modern data warehousing with Snowflake's architecture and Google's Mesa system

## Table of Contents
- [Overview](#overview)
- [Data Warehouse Fundamentals](#data-warehouse-fundamentals)
- [Snowflake Architecture](#snowflake-architecture)
- [Google Mesa](#google-mesa)
- [Comparison and Trade-offs](#comparison-and-trade-offs)
- [Real-World Use Cases](#real-world-use-cases)
- [Interview Tips](#interview-tips)

## Overview

Data warehouses are specialized databases optimized for analytical queries (OLAP) on large datasets, as opposed to transactional databases (OLTP) optimized for frequent updates.

### OLTP vs OLAP

```
┌────────────────┬──────────────────┬──────────────────┐
│ Aspect         │ OLTP             │ OLAP             │
├────────────────┼──────────────────┼──────────────────┤
│ Purpose        │ Transactions     │ Analytics        │
│ Queries        │ Simple, frequent │ Complex, ad-hoc  │
│ Data Volume    │ GB - TB          │ TB - PB          │
│ Users          │ Thousands        │ Hundreds         │
│ Response Time  │ Milliseconds     │ Seconds-Minutes  │
│ Operations     │ INSERT/UPDATE    │ SELECT (complex) │
│ Schema         │ Normalized       │ Denormalized     │
│ Example        │ MySQL, Postgres  │ Snowflake, Mesa  │
└────────────────┴──────────────────┴──────────────────┘
```

**OLTP Query Example:**
```sql
-- Find single customer order
SELECT * FROM orders WHERE order_id = 12345;

-- Update inventory
UPDATE products SET quantity = quantity - 1 WHERE product_id = 789;
```

**OLAP Query Example:**
```sql
-- Analyze sales trends across regions and products
SELECT
  region,
  product_category,
  DATE_TRUNC('month', order_date) as month,
  SUM(total_amount) as revenue,
  COUNT(DISTINCT customer_id) as unique_customers,
  AVG(total_amount) as avg_order_value
FROM orders
JOIN customers ON orders.customer_id = customers.id
JOIN products ON orders.product_id = products.id
WHERE order_date >= '2024-01-01'
GROUP BY region, product_category, month
ORDER BY revenue DESC;
```

## Data Warehouse Fundamentals

### Star Schema

Most common data warehouse schema pattern:

```
                    ┌──────────────────┐
                    │   Dim_Product    │
                    ├──────────────────┤
                    │ product_id (PK)  │
                    │ product_name     │
                    │ category         │
                    │ brand            │
                    └────────┬─────────┘
                             │
        ┌────────────────────┼────────────────────┐
        │                    │                    │
┌───────▼────────┐  ┌────────▼────────┐  ┌───────▼────────┐
│  Dim_Customer  │  │   Fact_Sales    │  │   Dim_Time     │
├────────────────┤  ├─────────────────┤  ├────────────────┤
│customer_id(PK) │  │ sale_id (PK)    │  │ date_id (PK)   │
│ name           │  │ customer_id(FK) │  │ date           │
│ region         │  │ product_id (FK) │  │ month          │
│ segment        │  │ store_id (FK)   │  │ quarter        │
└────────────────┘  │ date_id (FK)    │  │ year           │
                    │ quantity        │  └────────────────┘
        ┌───────────│ revenue         │
        │           │ cost            │
        │           │ profit          │
        │           └─────────────────┘
        │
┌───────▼────────┐
│   Dim_Store    │
├────────────────┤
│ store_id (PK)  │
│ store_name     │
│ location       │
│ manager        │
└────────────────┘
```

**Fact Table**: Central table with metrics/measures (sales, revenue)
**Dimension Tables**: Descriptive attributes (products, customers, time)

### Snowflake Schema

Normalized version of star schema:

```
┌────────────┐
│Dim_Category│
└─────┬──────┘
      │
┌─────▼──────┐     ┌─────────────┐
│Dim_Product │────→│ Fact_Sales  │
└────────────┘     └─────────────┘
```

Categories normalized into separate table (more storage efficient, more joins).

### Data Warehouse Architecture Patterns

**Traditional ETL (Extract-Transform-Load):**
```
Source DBs → ETL Pipeline → Data Warehouse
             (Overnight)      (Clean data)

Pros: Clean, consistent data
Cons: Latency (hours/days)
```

**Modern ELT (Extract-Load-Transform):**
```
Source DBs → Load Raw Data → Transform in Warehouse
             (Real-time)      (SQL transformations)

Pros: Faster loading, flexible transformations
Cons: Raw data storage costs
```

**Lambda Architecture:**
```
Source → Batch Layer → Batch Views ──┐
      ↓                               ├→ Merged View
      → Speed Layer → Real-time Views ┘

Pros: Both batch and real-time
Cons: Complex, dual processing
```

## Snowflake Architecture

Snowflake is a cloud-native data warehouse with a unique multi-cluster, shared-data architecture.

### Core Architecture

Snowflake separates compute, storage, and services:

```
┌──────────────────────────────────────────────────────────┐
│                   Cloud Services Layer                    │
│  ┌─────────────┐ ┌──────────────┐ ┌──────────────────┐  │
│  │ Auth & Auth │ │  Metadata    │ │ Query Optimizer  │  │
│  │ Management  │ │  Management  │ │ Transaction Mgmt │  │
│  └─────────────┘ └──────────────┘ └──────────────────┘  │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────┐
│                    Compute Layer                         │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │Virtual WH 1  │  │Virtual WH 2  │  │Virtual WH 3  │  │
│  │ (ETL Jobs)   │  │ (Analytics)  │  │ (Data Sci)   │  │
│  │  X-Small     │  │  Large       │  │  Medium      │  │
│  └──────────────┘  └──────────────┘  └──────────────┘  │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────┴─────────────────────────────────┐
│                    Storage Layer                         │
│              (Amazon S3 / Azure Blob)                    │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌─────────┐ │
│  │Database 1│  │Database 2│  │Database 3│  │ Clones  │ │
│  │  Table1  │  │  Table1  │  │  Table1  │  │(Zero-copy)│
│  │  Table2  │  │  Table2  │  │  Table2  │  │         │ │
│  └──────────┘  └──────────┘  └──────────┘  └─────────┘ │
└──────────────────────────────────────────────────────────┘
```

### Key Architectural Concepts

#### 1. Separation of Storage and Compute

**Traditional Data Warehouse (Oracle, Teradata):**
```
┌──────────────────────┐
│  Compute + Storage   │
│  (Tightly Coupled)   │
│                      │
│ Scale Up = More $$$  │
└──────────────────────┘
```

**Snowflake:**
```
Compute Layer (Virtual Warehouses)
  ↕ Scale independently
Storage Layer (S3/Blob)
  ↕ Pay only for what you use

Benefits:
- Pause compute when not in use → Save money
- Scale compute without moving data
- Multiple warehouses access same data
```

#### 2. Virtual Warehouses

Virtual warehouses are clusters of compute resources:

```sql
-- Create warehouse
CREATE WAREHOUSE analytics_wh
  WITH WAREHOUSE_SIZE = 'LARGE'
  AUTO_SUSPEND = 300        -- Suspend after 5 min idle
  AUTO_RESUME = TRUE        -- Auto-resume on query
  MIN_CLUSTER_COUNT = 1
  MAX_CLUSTER_COUNT = 10    -- Auto-scale to 10 clusters
  SCALING_POLICY = 'STANDARD';

-- Use warehouse
USE WAREHOUSE analytics_wh;

-- Run query (warehouse auto-resumes if suspended)
SELECT * FROM sales_data;
```

**Warehouse Sizes:**
```
┌──────────┬──────────┬─────────────┐
│ Size     │ Nodes    │ Credits/Hr  │
├──────────┼──────────┼─────────────┤
│ X-Small  │ 1        │ 1           │
│ Small    │ 2        │ 2           │
│ Medium   │ 4        │ 4           │
│ Large    │ 8        │ 8           │
│ X-Large  │ 16       │ 16          │
│ 2X-Large │ 32       │ 32          │
│ 3X-Large │ 64       │ 64          │
│ 4X-Large │ 128      │ 128         │
└──────────┴──────────┴─────────────┘
```

**Multi-Cluster Warehouses:**
```
Query Load:  ▁▂▃▄▅▆▇█████▇▆▅▄▃▂▁

Auto-scaling:
Cluster 1: [███████████████████]
Cluster 2:       [████████████]
Cluster 3:          [██████]

Off-peak:  Only Cluster 1 runs
Peak:      All 3 clusters run
```

#### 3. Micro-Partitions

Snowflake automatically partitions data into immutable micro-partitions:

```
Table: sales (1 billion rows)

Micro-partitions (16 MB each, columnar):
┌─────────────┐ ┌─────────────┐ ┌─────────────┐
│ Partition 1 │ │ Partition 2 │ │ Partition 3 │ ...
│ 500k rows   │ │ 500k rows   │ │ 500k rows   │
│ 2024-01-01  │ │ 2024-01-01  │ │ 2024-01-02  │
│ Region: US  │ │ Region: EU  │ │ Region: US  │
└─────────────┘ └─────────────┘ └─────────────┘

Metadata per partition:
- Min/Max values per column
- Number of distinct values
- NULL count
```

**Partition Pruning:**
```sql
SELECT * FROM sales
WHERE date = '2024-01-02' AND region = 'US';

Snowflake reads metadata → Scans only Partition 3
(Avoids scanning Partitions 1, 2, and thousands more)
```

#### 4. Time Travel and Cloning

**Time Travel** (Query historical data):
```sql
-- Query table as it was 1 hour ago
SELECT * FROM sales AT(OFFSET => -3600);

-- Query table at specific timestamp
SELECT * FROM sales AT(TIMESTAMP => '2024-01-15 10:00:00');

-- Query table before specific statement
SELECT * FROM sales BEFORE(STATEMENT => '01a8b3c4-...');

-- Restore deleted table
UNDROP TABLE sales;
```

**Zero-Copy Cloning:**
```sql
-- Clone entire database instantly
CREATE DATABASE dev_db CLONE prod_db;

-- Clone table (0 storage cost initially)
CREATE TABLE sales_backup CLONE sales;

-- Clones share micro-partitions
Prod Table: [P1][P2][P3][P4]
             ↑   ↑   ↑   ↑
Clone Table: [P1][P2][P3][P4]
(No data copied, just metadata)

-- Only diverged partitions consume storage
After updates:
Prod:  [P1][P2][P3'][P4]
Clone: [P1][P2][P3 ][P4][P5]
        └Shared┘  └─New─┘
```

#### 5. Data Sharing

Share live data with other Snowflake accounts (no copies, no ETL):

```sql
-- Create share
CREATE SHARE sales_share;
GRANT USAGE ON DATABASE sales_db TO SHARE sales_share;
GRANT SELECT ON TABLE sales_db.public.sales TO SHARE sales_share;

-- Add consumer account
ALTER SHARE sales_share ADD ACCOUNTS = consumer_account;

-- Consumer sees live data
-- Provider controls access, consumer pays for compute
```

```
Provider Account              Consumer Account
┌──────────────┐             ┌──────────────┐
│  sales_db    │  ─────────→ │ shared_db    │
│  (Read/Write)│  Live Data  │ (Read Only)  │
└──────────────┘             └──────────────┘

- No data movement
- No latency
- Provider updates → Consumer sees immediately
- Consumer can't modify shared data
```

### Snowflake Performance Optimizations

#### 1. Clustering Keys

For very large tables (TB+), define clustering:

```sql
-- Without clustering (random data distribution)
SELECT * FROM events WHERE event_date = '2024-01-15';
→ Scans many micro-partitions

-- Add clustering key
ALTER TABLE events CLUSTER BY (event_date);

-- Snowflake reorganizes micro-partitions
Before: [Jan1,Jan5,Jan2][Jan3,Jan8,Jan1]...
After:  [Jan1,Jan1,Jan1][Jan2,Jan2,Jan2][Jan3,Jan3,Jan3]...

-- Query now scans fewer partitions
SELECT * FROM events WHERE event_date = '2024-01-15';
→ Scans only relevant partitions
```

#### 2. Search Optimization

```sql
-- Enable search optimization for point lookups
ALTER TABLE users ADD SEARCH OPTIMIZATION;

-- Optimizes queries like:
SELECT * FROM users WHERE email = 'user@example.com';
SELECT * FROM users WHERE user_id = 12345;

-- Creates additional metadata structures for fast lookups
```

#### 3. Materialized Views

```sql
-- Expensive aggregation query
SELECT
  product_id,
  DATE_TRUNC('day', sale_date) as day,
  SUM(quantity) as total_quantity,
  SUM(revenue) as total_revenue
FROM sales
GROUP BY product_id, day;

-- Create materialized view (automatically maintained)
CREATE MATERIALIZED VIEW daily_sales AS
SELECT
  product_id,
  DATE_TRUNC('day', sale_date) as day,
  SUM(quantity) as total_quantity,
  SUM(revenue) as total_revenue
FROM sales
GROUP BY product_id, day;

-- Query materialized view (fast!)
SELECT * FROM daily_sales WHERE product_id = 123;

-- Snowflake automatically updates materialized view when sales table changes
```

#### 4. Result Caching

Snowflake caches query results for 24 hours:

```sql
-- First execution: Runs query, caches result
SELECT * FROM sales WHERE region = 'US';
→ Execution time: 10 seconds

-- Second execution (within 24 hours): Returns cached result
SELECT * FROM sales WHERE region = 'US';
→ Execution time: 0.1 seconds (no compute used!)

-- Cache invalidated if underlying data changes
```

## Google Mesa

Mesa is Google's data warehousing system for storing and analyzing advertising data (AdWords, AdSense, etc.).

### Mesa Requirements

Google's advertising system needs:
1. **Atomic Updates**: Batch updates must be atomic
2. **Consistency**: Same query result across datacenters
3. **Near Real-Time**: Updates available within minutes
4. **Scalability**: Petabytes of data, trillions of rows
5. **High Availability**: No downtime for updates

### Mesa Architecture

```
┌──────────────────────────────────────────────────────────┐
│                   Mesa Controller                         │
│  - Metadata management                                    │
│  - Update coordination                                    │
│  - Query routing                                          │
└─────────────────────┬────────────────────────────────────┘
                      │
        ┌─────────────┼─────────────┐
        │             │             │
┌───────▼───────┐ ┌───▼──────┐ ┌───▼──────┐
│ Mesa Instance │ │  Mesa    │ │  Mesa    │
│ (Datacenter 1)│ │(DC 2)    │ │(DC 3)    │
│               │ │          │ │          │
│ ┌───────────┐ │ │┌────────┐│ │┌────────┐│
│ │Base Delta │ │ ││ Delta  ││ ││ Delta  ││
│ │  Files    │ │ ││ Files  ││ ││ Files  ││
│ └───────────┘ │ │└────────┘│ │└────────┘│
│               │ │          │ │          │
│ ┌───────────┐ │ │┌────────┐│ │┌────────┐│
│ │ Indexes   │ │ ││Indexes ││ ││Indexes ││
│ └───────────┘ │ │└────────┘│ │└────────┘│
└───────────────┘ └──────────┘ └──────────┘
```

### Key Mesa Concepts

#### 1. Versioned Data

Mesa uses multi-version concurrency control (MVCC):

```
Table: ad_clicks

Version [0, 100):
┌─────────┬────────┬────────┐
│ ad_id   │ date   │ clicks │
├─────────┼────────┼────────┤
│ ad123   │ Jan 1  │ 1000   │
│ ad456   │ Jan 1  │ 2000   │
└─────────┴────────┴────────┘

Version [100, 200):  (Delta)
┌─────────┬────────┬────────┐
│ ad_id   │ date   │ clicks │
├─────────┼────────┼────────┤
│ ad123   │ Jan 1  │ +500   │ ← New clicks
│ ad789   │ Jan 1  │ 1500   │ ← New ad
└─────────┴────────┴────────┘

Query at version 150:
  Result = Base[0,100) + Delta[100,200)
  ad123: 1000 + 500 = 1500 clicks
```

**Version Range Format:** `[begin_version, end_version)`
- Each update creates a new delta with version range
- Query specifies version for consistent reads

#### 2. Multi-Level Storage

```
┌────────────────────────────────────────────────┐
│           Singleton Delta                      │
│      (Recent updates, minutes old)             │
│      Version: [1000, 1010)                     │
└────────────────┬───────────────────────────────┘
                 │ Compaction
┌────────────────▼───────────────────────────────┐
│         Cumulative Deltas                      │
│    (Merged updates, hours old)                 │
│    Version: [900, 1000)                        │
└────────────────┬───────────────────────────────┘
                 │ Compaction
┌────────────────▼───────────────────────────────┐
│            Base Delta                          │
│     (Consolidated data, days old)              │
│     Version: [0, 900)                          │
└────────────────────────────────────────────────┘
```

**Compaction Process:**
```
Every hour:
  Merge recent singleton deltas → Cumulative delta

Every day:
  Merge cumulative deltas → New base delta
```

Benefits:
- Fast updates (append small delta)
- Efficient queries (fewer files to read)
- Background compaction (no query impact)

#### 3. Update Batching

Mesa processes updates in batches:

```
┌──────────────┐
│ Click Stream │ (Millions of events/second)
└──────┬───────┘
       │ Buffer (1-5 minutes)
       ▼
┌──────────────┐
│ Update Batch │
│ 100M events  │
└──────┬───────┘
       │ Aggregate
       ▼
┌──────────────┐
│  Delta File  │
│ 1M rows      │ (Atomic commit)
└──────────────┘
```

**Batch Properties:**
- **Atomic**: All or nothing
- **Versioned**: Assigned version range
- **Replicated**: To all datacenters

#### 4. Schema Definition

Mesa tables defined as multi-dimensional aggregates:

```
Table: ad_performance

Schema:
  Keys: [advertiser_id, campaign_id, ad_id, date, country]
  Values: [clicks, impressions, cost, conversions]
  Aggregation: SUM

Example data:
┌──────────┬──────────┬──────┬────────┬─────────┬────────┐
│advert_id │campaign  │ad_id │ date   │ country │ clicks │
├──────────┼──────────┼──────┼────────┼─────────┼────────┤
│ 12345    │ camp001  │ ad1  │ Jan1   │ US      │ 1000   │
│ 12345    │ camp001  │ ad1  │ Jan1   │ UK      │ 500    │
│ 12345    │ camp001  │ ad2  │ Jan1   │ US      │ 2000   │
└──────────┴──────────┴──────┴────────┴─────────┴────────┘

Queries aggregate automatically:
  SELECT SUM(clicks) WHERE advertiser_id = 12345 AND date = 'Jan1'
  → 3500 (from all matching rows)
```

#### 5. Consistency Guarantees

**Global Consistency** across datacenters:

```
Update @ version 100 applied:

Datacenter 1: Version 100 ✓
Datacenter 2: Version 95  ✗ (not ready)
Datacenter 3: Version 100 ✓

Query at version 100:
  → Routed to DC1 or DC3 (have version 100)
  → NOT DC2 (would return stale data)

When DC2 catches up:
  → Can serve queries at version 100
```

**Repeatable Reads:**
```
Query 1: SELECT * FROM table VERSION 100
Query 2: SELECT * FROM table VERSION 100
→ Identical results, even if new updates arrived

Query 3: SELECT * FROM table VERSION 110
→ Includes new updates
```

### Mesa Performance

**Scale:**
- Petabytes of data
- Trillions of rows
- Millions of updates/second

**Latency:**
- Update latency: Minutes (batching)
- Query latency: Seconds

**Optimization Techniques:**

1. **Pre-aggregation**: Data already aggregated by keys
2. **Columnar Storage**: Read only needed columns
3. **Indexing**: B-tree indexes on key columns
4. **Caching**: Recent queries cached
5. **Parallel Execution**: Queries parallelized across servers

## Comparison and Trade-offs

### Snowflake vs Mesa

```
┌──────────────────┬─────────────────┬─────────────────┐
│ Aspect           │ Snowflake       │ Mesa            │
├──────────────────┼─────────────────┼─────────────────┤
│ Design           │ General-purpose │ Purpose-built   │
│ Use Case         │ Analytics       │ Ad metrics      │
│ Updates          │ Row-level       │ Batched, atomic │
│ Consistency      │ Eventual        │ Global, strict  │
│ Schema           │ Flexible        │ Pre-defined agg │
│ SQL Support      │ Full SQL        │ Limited         │
│ Deployment       │ SaaS (cloud)    │ Internal (Google)│
│ Versioning       │ Time travel     │ MVCC versions   │
│ Scalability      │ Petabytes       │ Petabytes+      │
│ Update Latency   │ Immediate       │ Minutes (batch) │
│ Geo-replication  │ Cross-region    │ Multi-DC sync   │
└──────────────────┴─────────────────┴─────────────────┘
```

### When to Use Each

**Use Snowflake when:**
- General-purpose analytics
- Ad-hoc querying flexibility
- SQL ecosystem integration
- Managed cloud service preferred
- Variable workloads (auto-scaling)
- Need data sharing capabilities

**Use Mesa-like system when:**
- Pre-defined aggregations known
- Atomic batch updates critical
- Global consistency required
- Massive scale (Google-level)
- Near real-time updates on aggregates
- Willing to build custom solution

### Modern Alternatives

**BigQuery** (Google Cloud):
- Similar to Snowflake
- Serverless, auto-scaling
- Columnar storage (Capacitor)
- Pay per query

**Redshift** (AWS):
- Columnar storage (C-Store based)
- Tight AWS integration
- Less separation of compute/storage

**Databricks** (Lakehouse):
- Built on Apache Spark
- Unified batch and streaming
- Delta Lake for ACID transactions

## Real-World Use Cases

### Use Case 1: E-Commerce Analytics (Snowflake)

```sql
-- Daily sales dashboard
CREATE MATERIALIZED VIEW daily_sales AS
SELECT
  DATE_TRUNC('day', order_timestamp) as day,
  product_category,
  region,
  COUNT(*) as order_count,
  SUM(order_total) as revenue,
  AVG(order_total) as avg_order_value,
  COUNT(DISTINCT customer_id) as unique_customers
FROM orders
GROUP BY day, product_category, region;

-- ETL pipeline with virtual warehouses
-- ETL_WH: Large warehouse for nightly batch loads
-- ANALYTICS_WH: Medium warehouse for analysts (auto-scales)
-- REPORTING_WH: Small warehouse for dashboards

-- Load data from S3
COPY INTO orders
FROM @s3_stage/orders/
FILE_FORMAT = (TYPE = PARQUET)
ON_ERROR = 'CONTINUE';

-- Analysts query with separate warehouse
USE WAREHOUSE analytics_wh;
SELECT * FROM daily_sales WHERE day >= CURRENT_DATE - 30;
```

### Use Case 2: Ad Tech Metrics (Mesa-like)

```
Real-Time Ad Click Pipeline:

1. Click Events (Pub/Sub)
   100M events/minute

2. Aggregation (Dataflow/Flink)
   GROUP BY [advertiser, campaign, ad, date, country]
   → 10M aggregated records/minute

3. Batch to Mesa (5-minute batches)
   Atomic update with version [V, V+1)

4. Query Service
   GET /metrics?advertiser=123&date=2024-01-15
   → Read from latest version
   → Consistent across all datacenters

5. Dashboard Update
   Metrics updated every 5 minutes
   All dashboards see same data (consistency)
```

### Use Case 3: Financial Reporting

```sql
-- Snowflake with Time Travel for auditing

-- Current balance
SELECT account_id, SUM(amount) as balance
FROM transactions
GROUP BY account_id;

-- Balance as of yesterday 5 PM
SELECT account_id, SUM(amount) as balance
FROM transactions AT(TIMESTAMP => '2024-01-14 17:00:00')
GROUP BY account_id;

-- Track changes
SELECT
  current.account_id,
  current.balance - yesterday.balance as change
FROM current_balances current
JOIN yesterday_balances AT(OFFSET => -86400) yesterday
  ON current.account_id = yesterday.account_id;

-- Compliance: Keep 90 days of history
ALTER TABLE transactions SET DATA_RETENTION_TIME_IN_DAYS = 90;
```

## Interview Tips

### Key Concepts to Emphasize

**Snowflake:**
1. **Separation of compute and storage**: Independent scaling
2. **Virtual warehouses**: Isolated compute clusters, auto-suspend/resume
3. **Micro-partitions**: Automatic partitioning with metadata pruning
4. **Zero-copy cloning**: Instant copies for dev/test
5. **Data sharing**: Live data sharing without copies

**Mesa:**
1. **Versioned data**: MVCC for consistency
2. **Atomic batch updates**: All-or-nothing updates
3. **Multi-level storage**: Singleton → Cumulative → Base deltas
4. **Global consistency**: Same results across datacenters
5. **Pre-aggregated schema**: Optimized for known queries

### Common Interview Questions

**Q: How does Snowflake achieve high performance?**

A: Multiple techniques:
1. **Columnar storage**: Read only needed columns
2. **Micro-partitions**: Automatic partitioning with metadata pruning
3. **Result caching**: 24-hour query result cache
4. **Parallel execution**: Distributed query processing
5. **Clustering**: Optional clustering for very large tables
6. **Materialized views**: Pre-computed aggregations

**Q: What is the advantage of separating compute and storage?**

A: Enables:
1. **Cost savings**: Pause compute when not in use, pay only for storage
2. **Independent scaling**: Scale compute without moving data
3. **Workload isolation**: Multiple warehouses for different workloads
4. **Elasticity**: Auto-scale compute based on demand
5. **Concurrency**: Many users querying same data without contention

**Q: How does Mesa ensure consistency across datacenters?**

A: Mesa uses versioned data with strict version ordering:
1. Updates assigned version numbers (monotonically increasing)
2. Updates replicated to all datacenters with same version
3. Queries specify version number
4. Query routed only to datacenters with that version
5. All datacenters eventually reach same version (consistency)

**Q: When would you use Mesa over Snowflake?**

A: Mesa-like system when:
1. Pre-defined aggregations (known query patterns)
2. Need atomic batch updates (all-or-nothing)
3. Require global consistency across geo-distributed DCs
4. Massive scale with predictable access patterns
5. Willing to build/maintain custom system

Use Snowflake for general-purpose analytics with flexibility.

**Q: How does zero-copy cloning work in Snowflake?**

A: Cloning creates a new table/database that initially shares all micro-partitions with the source. Only metadata is copied (instant). When either clone or original is modified, new micro-partitions are created only for changed data (copy-on-write). Shared partitions don't consume additional storage.

**Q: Explain Snowflake's Time Travel feature.**

A: Time Travel allows querying historical data within retention period (default 1 day, up to 90 days). Uses:
1. Query data as of specific timestamp: `AT(TIMESTAMP => '...')`
2. Query data before statement: `BEFORE(STATEMENT => '...')`
3. Undrop tables/schemas: `UNDROP TABLE ...`
4. Audit/compliance: Track data changes over time

Implemented using micro-partitions retained during retention period.

## Summary

### Snowflake Strengths
- **Separation of compute/storage**: Cost efficiency and elasticity
- **Multi-cluster warehouses**: Handle variable workloads
- **Zero-copy cloning**: Instant dev/test environments
- **Data sharing**: Secure, live data sharing
- **Time Travel**: Historical queries and recovery
- **Managed service**: No infrastructure management

### Mesa Strengths
- **Atomic updates**: Batch updates all-or-nothing
- **Global consistency**: Same results across datacenters
- **Versioned data**: Point-in-time consistency
- **Pre-aggregation**: Optimized for known queries
- **Massive scale**: Google-level (petabytes+, trillions of rows)

### Key Takeaways

1. **OLAP vs OLTP**: Different architectures for different workloads
2. **Cloud-native**: Leverage cloud storage/compute separation
3. **Columnar storage**: Essential for analytical queries
4. **Partitioning**: Prune irrelevant data early
5. **Caching**: Multiple levels (results, metadata, data)
6. **Consistency models**: Trade-offs between consistency and latency

---

**Related Topics:**
- [Advanced Data Formats: Parquet, Iceberg](data-formats.md)
- [Stream Processing: Flink](stream-processing.md)
- [Data Capture: Debezium](data-capture.md)

**References:**
- [Snowflake Architecture White Paper](https://www.snowflake.com/resource/snowflake-architecture/)
- Gupta et al., "Mesa: Geo-Replicated, Near Real-Time, Scalable Data Warehousing" (VLDB 2014)
- Dageville et al., "The Snowflake Elastic Data Warehouse" (SIGMOD 2016)
