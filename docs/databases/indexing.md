# Indexing Strategies

Indexes are critical for database performance, enabling fast data retrieval at the cost of storage and write performance.

## Contents
- [What is an Index?](#what-is-an-index)
- [Index Data Structures](#index-data-structures)
- [Index Types](#index-types)
- [Indexing Strategies](#indexing-strategies)
- [Index Trade-offs](#index-trade-offs)
- [Best Practices](#best-practices)

## What is an Index?

An index is a data structure that improves query performance by providing quick lookups.

**Without Index:**
```sql
SELECT * FROM users WHERE email = 'user@example.com';
-- Full table scan: O(n) - checks every row
```

**With Index:**
```sql
CREATE INDEX idx_email ON users(email);
SELECT * FROM users WHERE email = 'user@example.com';
-- Index lookup: O(log n) - B-tree search
```

### Analogy
Like a book index: instead of reading every page to find a topic, you check the index at the back to jump directly to relevant pages.

## Index Data Structures

### 1. B-Tree (Balanced Tree)

**Most common index type** used in SQL databases.

```
                [50]
              /      \
         [25]          [75]
        /    \        /    \
    [10,15] [30,40] [60,65] [80,90]
      |       |       |       |
    (data) (data)  (data)   (data)
```

**Properties:**
- Self-balancing tree
- All leaves at same depth
- Sorted order maintained
- O(log n) search, insert, delete

**Best For:**
- Range queries (`WHERE age BETWEEN 20 AND 30`)
- Sorted results (`ORDER BY`)
- Equality searches (`WHERE id = 5`)
- Prefix searches (`WHERE name LIKE 'John%'`)

**Used By:** PostgreSQL, MySQL InnoDB, Oracle, SQL Server

### 2. B+Tree

**Optimized variant** where all data in leaf nodes.

```
                [50]
              /      \
         [25]          [75]
        /    \        /    \
    [10]→[25]→[50]→[75]   (linked list of leaves)
     ↓    ↓    ↓    ↓
   data  data data  data
```

**Advantages over B-Tree:**
- Leaf nodes linked (faster range scans)
- More keys per internal node (less height)
- Better cache locality

**Used By:** Most modern RDBMS (default index structure)

### 3. Hash Index

**Direct key-to-location mapping** using hash function.

```
hash('user@example.com') → bucket → data location
```

**Properties:**
- O(1) average lookup time
- No ordering maintained
- Collision handling needed

**Best For:**
- Exact match queries (`WHERE id = 123`)
- Key-value lookups
- In-memory databases

**Limitations:**
- ❌ No range queries
- ❌ No sorting
- ❌ No prefix matching

**Used By:** PostgreSQL (explicit), MySQL MEMORY engine

### 4. LSM Tree (Log-Structured Merge-Tree)

**Write-optimized structure** using multiple levels.

```
Memory (MemTable)
    ↓ (flush)
Level 0 (SSTables)
    ↓ (merge/compact)
Level 1
    ↓
Level 2
```

**Properties:**
- Fast writes (append-only)
- Background compaction
- Trade read speed for write speed

**Best For:**
- Write-heavy workloads
- Time-series data
- Log analytics

**Used By:** Cassandra, RocksDB, LevelDB, HBase

### 5. Bitmap Index

**Bit arrays for each distinct value.**

```
Gender Index:
Male:   [1, 0, 1, 0, 1, 0]
Female: [0, 1, 0, 1, 0, 1]

Status Index:
Active:   [1, 1, 0, 1, 0, 0]
Inactive: [0, 0, 1, 0, 1, 1]
```

**Best For:**
- Low cardinality columns (gender, status, country)
- Multiple condition queries (AND/OR operations)
- Data warehouses

**Limitations:**
- ❌ Expensive updates (rebuild bitmap)
- ❌ Poor for high cardinality

**Used By:** Oracle (common in OLAP), PostgreSQL (contrib)

### 6. Full-Text Index

**Specialized for text search** using inverted index.

```
Document 1: "The quick brown fox"
Document 2: "The lazy brown dog"

Inverted Index:
quick → [Doc1]
brown → [Doc1, Doc2]
fox   → [Doc1]
lazy  → [Doc2]
dog   → [Doc2]
```

**Best For:**
- Text search
- Document retrieval
- Search engines

**Used By:** PostgreSQL (GiST/GIN), MySQL (FULLTEXT), Elasticsearch

## Index Types

### 1. Primary Index (Clustered)

**Physical order of data matches index order.**

```sql
CREATE TABLE users (
    id INT PRIMARY KEY,  -- Clustered index
    name VARCHAR(100)
);
```

**Properties:**
- Only one per table (defines physical order)
- Usually on primary key
- Fastest for range scans on primary key

**Performance:**
- ✅ Fast range queries on key
- ✅ No extra storage for data
- ❌ Slower inserts (maintain order)

### 2. Secondary Index (Non-Clustered)

**Separate structure pointing to data.**

```sql
CREATE INDEX idx_email ON users(email);
```

**Properties:**
- Multiple per table
- Stores: index key + pointer to row
- Requires extra disk space

**Performance:**
- ✅ Fast lookups on indexed column
- ❌ Extra lookup to get full row data

### 3. Composite Index (Multi-Column)

**Index on multiple columns.**

```sql
CREATE INDEX idx_name_age ON users(last_name, first_name, age);
```

**Left-to-Right Rule:**
```sql
-- ✅ Uses index (leftmost prefix)
WHERE last_name = 'Smith'
WHERE last_name = 'Smith' AND first_name = 'John'
WHERE last_name = 'Smith' AND first_name = 'John' AND age = 30

-- ❌ Doesn't use index (skips leftmost)
WHERE first_name = 'John'
WHERE age = 30
WHERE first_name = 'John' AND age = 30
```

**Column Order Matters:**
- Most selective column first
- Commonly queried together
- Consider query patterns

### 4. Covering Index

**Index contains all columns needed by query.**

```sql
-- Query
SELECT first_name, last_name FROM users WHERE email = 'user@example.com';

-- Covering index (no table lookup needed)
CREATE INDEX idx_email_names ON users(email, first_name, last_name);
```

**Benefits:**
- No table access required
- Faster query execution
- Reduced I/O

### 5. Partial Index

**Index only subset of rows.**

```sql
-- Only index active users
CREATE INDEX idx_active_users ON users(email) WHERE status = 'active';
```

**Benefits:**
- Smaller index size
- Faster updates
- Query must match condition

**Use Cases:**
- Sparse data (e.g., null values rare)
- Status-based filtering
- Time-based (recent data only)

### 6. Unique Index

**Enforces uniqueness.**

```sql
CREATE UNIQUE INDEX idx_unique_email ON users(email);
```

**Properties:**
- Prevents duplicate values
- Automatically created for PRIMARY KEY and UNIQUE constraints
- Can be composite

## Indexing Strategies

### 1. Identify Slow Queries

**Analyze query patterns:**

```sql
-- PostgreSQL: Explain plan
EXPLAIN ANALYZE
SELECT * FROM orders WHERE customer_id = 123;

-- Look for:
-- • Seq Scan (full table scan) - BAD
-- • Index Scan - GOOD
-- • Bitmap Heap Scan - OK
```

**MySQL:**
```sql
EXPLAIN SELECT * FROM orders WHERE customer_id = 123;

-- Look for:
-- • type: ALL (full scan) - BAD
-- • type: ref, range (index) - GOOD
```

### 2. Index WHERE Clause Columns

**Index columns frequently in WHERE:**

```sql
-- Frequent query
SELECT * FROM orders WHERE status = 'pending' AND created_at > '2024-01-01';

-- Create composite index
CREATE INDEX idx_status_date ON orders(status, created_at);
```

### 3. Index JOIN Columns

**Both sides of JOIN:**

```sql
SELECT * FROM orders o
JOIN users u ON o.user_id = u.id;

-- Index foreign key
CREATE INDEX idx_orders_user_id ON orders(user_id);
-- Primary key on users(id) already indexed
```

### 4. Index ORDER BY Columns

**Avoid sorting:**

```sql
SELECT * FROM users ORDER BY created_at DESC LIMIT 10;

-- Index for sorted retrieval
CREATE INDEX idx_created_at ON users(created_at DESC);
```

### 5. Avoid Over-Indexing

**Each index has cost:**
- Storage space
- Slower writes (update all indexes)
- Query planner complexity

**Rule of Thumb:**
- 3-5 indexes per table is common
- 10+ indexes may be excessive
- Monitor index usage

### 6. Drop Unused Indexes

**Find unused indexes (PostgreSQL):**

```sql
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan as index_scans
FROM pg_stat_user_indexes
WHERE idx_scan = 0
  AND indexrelname NOT LIKE '%pkey%';
```

## Index Trade-offs

### Performance Impact

| Operation | Impact of Index |
|-----------|-----------------|
| SELECT (indexed column) | ✅ Much faster (log n vs n) |
| INSERT | ❌ Slower (update indexes) |
| UPDATE (indexed column) | ❌ Slower (rebuild index entry) |
| DELETE | ❌ Slower (remove from indexes) |
| ORDER BY (indexed column) | ✅ Faster (pre-sorted) |
| GROUP BY (indexed column) | ✅ Faster |

### Storage Cost

**Example:**
- Table: 10GB
- Index: ~15-20% of table size = 1.5-2GB per index
- 5 indexes = 7.5-10GB additional storage

### Write Amplification

**Each write affects multiple structures:**

```
INSERT 1 row with 3 indexes:
→ Write to heap (table data)
→ Update index 1
→ Update index 2
→ Update index 3
= 4 write operations per insert
```

## Best Practices

### 1. Index Selectivity

**High selectivity = better index:**

```sql
-- High selectivity (unique email) ✅
CREATE INDEX idx_email ON users(email);

-- Low selectivity (gender: M/F) ❌
CREATE INDEX idx_gender ON users(gender);  -- Usually not worth it
```

**Formula:**
```
Selectivity = Unique values / Total rows

> 0.5 (50%): Excellent
0.1 - 0.5:   Good
< 0.1:       Poor (consider bitmap index)
```

### 2. Analyze Index Usage

**Monitor effectiveness:**

```sql
-- PostgreSQL: Index hit ratio
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
ORDER BY idx_scan DESC;
```

### 3. Update Statistics

**Keep query planner informed:**

```sql
-- PostgreSQL
ANALYZE table_name;

-- MySQL
ANALYZE TABLE table_name;
```

### 4. Consider Index Maintenance

**Indexes degrade over time:**

```sql
-- PostgreSQL: Rebuild index
REINDEX INDEX idx_name;

-- MySQL: Optimize table
OPTIMIZE TABLE table_name;
```

### 5. Index Design Checklist

- ✅ Index columns in WHERE clauses
- ✅ Index columns in JOIN conditions
- ✅ Consider composite indexes for multi-column queries
- ✅ Use covering indexes when possible
- ✅ Index ORDER BY columns
- ❌ Don't index low-selectivity columns
- ❌ Don't index columns updated frequently
- ✅ Monitor and remove unused indexes

## Common Mistakes

### 1. Function on Indexed Column

```sql
-- ❌ Doesn't use index
SELECT * FROM users WHERE UPPER(email) = 'USER@EXAMPLE.COM';

-- ✅ Use functional index
CREATE INDEX idx_email_upper ON users(UPPER(email));
```

### 2. Type Mismatch

```sql
-- ❌ Doesn't use index (id is INT, '123' is string)
SELECT * FROM users WHERE id = '123';

-- ✅ Correct type
SELECT * FROM users WHERE id = 123;
```

### 3. Leading Wildcard

```sql
-- ❌ Doesn't use index
SELECT * FROM users WHERE email LIKE '%@example.com';

-- ✅ Uses index
SELECT * FROM users WHERE email LIKE 'john%';
```

### 4. OR Conditions

```sql
-- ❌ May not use indexes efficiently
SELECT * FROM users WHERE first_name = 'John' OR last_name = 'Smith';

-- ✅ Use UNION (if appropriate)
SELECT * FROM users WHERE first_name = 'John'
UNION
SELECT * FROM users WHERE last_name = 'Smith';
```

## Real-World Examples

**Twitter:**
- Composite indexes on (user_id, created_at) for timeline queries
- Covering indexes to avoid table lookups

**Facebook:**
- Billions of indexes across sharded databases
- Automated index recommendation systems

**Stack Overflow:**
- Full-text indexes for search
- Composite indexes on (tag_id, score, created_at)

**Uber:**
- Geospatial indexes for location queries
- LSM trees for write-heavy trip data

## Further Reading

- [Use The Index, Luke](https://use-the-index-luke.com/) - Comprehensive indexing guide
- [PostgreSQL Index Types](https://www.postgresql.org/docs/current/indexes-types.html)
- [MySQL Index Hints](https://dev.mysql.com/doc/refman/8.0/en/index-hints.html)
- [Database Indexing Explained](https://www.essentialsql.com/what-is-a-database-index/)
