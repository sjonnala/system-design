# Database Indexes: What Do They Do? - Deep Dive

## Contents

- [Database Indexes: What Do They Do? - Deep Dive](#database-indexes-what-do-they-do---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [The Performance Problem: Sequential Scans](#1-the-performance-problem-sequential-scans)
  - [Index Anatomy: Data Structure Fundamentals](#2-index-anatomy-data-structure-fundamentals)
  - [Write Amplification: The Hidden Cost](#3-write-amplification-the-hidden-cost)
  - [Index Selectivity & Cardinality](#4-index-selectivity--cardinality)
  - [Covering Indexes: The Ultimate Optimization](#5-covering-indexes-the-ultimate-optimization)
  - [Index Maintenance & Fragmentation](#6-index-maintenance--fragmentation)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
    - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
    - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
    - [Index Strategy (RADIO: Optimize)](#4-index-strategy-radio-optimize)
  - [MIND MAP: DATABASE INDEX CONCEPTS](#mind-map-database-index-concepts)

## Core Mental Model

```text
Query Time Complexity:
- No Index:  O(n) - Full table scan
- With Index: O(log n) - For B-Tree/Binary search
              O(1) - For Hash index (equality only)

Space-Time Tradeoff:
Index Storage Cost = f(Cardinality, Key Size, Data Structure Overhead)
Query Speed Gain = g(Selectivity, Index Type, Access Pattern)
```

**Fundamental Equation:**
```
Read Performance ↑  =  Write Performance ↓  +  Storage ↑

Every index is a conscious tradeoff!
```

**Three Core Purposes of Indexes:**
```
┌──────────────────────────────────────────────────┐
│ 1. SPEED: Reduce I/O operations (disk seeks)    │
│ 2. SORTING: Pre-sorted data for ORDER BY        │
│ 3. UNIQUENESS: Enforce unique constraints       │
└──────────────────────────────────────────────────┘
```

**Real World Pattern:**
```java
// Domain Model: Index Strategy Decision
public class IndexStrategy {

    public enum AccessPattern {
        EQUALITY,      // WHERE id = 123
        RANGE,         // WHERE created_at > '2024-01-01'
        PREFIX,        // WHERE name LIKE 'John%'
        FULL_TEXT,     // WHERE description CONTAINS 'database'
        GEOSPATIAL     // WHERE distance(location, point) < 5km
    }

    public IndexType selectIndexType(Column column, AccessPattern pattern) {
        // Decision tree based on access pattern
        if (pattern == AccessPattern.EQUALITY && column.hasHighCardinality()) {
            return IndexType.HASH;  // O(1) lookup
        }

        if (pattern == AccessPattern.RANGE || pattern == AccessPattern.PREFIX) {
            return IndexType.BTREE;  // Supports range scans
        }

        if (pattern == AccessPattern.FULL_TEXT) {
            return IndexType.INVERTED;  // ElasticSearch, Lucene
        }

        if (pattern == AccessPattern.GEOSPATIAL) {
            return IndexType.R_TREE;  // Spatial indexing
        }

        // Default: B-Tree is versatile
        return IndexType.BTREE;
    }
}
```

---

### 1. **The Performance Problem: Sequential Scans**

🎓 **PROFESSOR**: Let's understand the **fundamental problem** that indexes solve:

```text
Without Index (Sequential Scan):
┌─────────────────────────────────────────────────────┐
│ Query: SELECT * FROM users WHERE email = 'john@...' │
│                                                      │
│ Database must:                                       │
│  1. Read EVERY row from disk                        │
│  2. Check if email matches                          │
│  3. Return matching rows                            │
│                                                      │
│ For 1M rows:                                         │
│  - Disk I/O: 1M row reads                           │
│  - Time: ~10 seconds (assuming 100K rows/sec)       │
└─────────────────────────────────────────────────────┘

With Index (Index Seek):
┌─────────────────────────────────────────────────────┐
│ Same query with index on 'email' column             │
│                                                      │
│ Database:                                            │
│  1. Searches index (B-Tree): O(log n) = ~20 nodes  │
│  2. Finds row pointer(s)                            │
│  3. Reads specific row from disk                    │
│                                                      │
│ For 1M rows:                                         │
│  - Index lookups: 20 (log₂ 1,000,000)               │
│  - Disk I/O: 1 row read                             │
│  - Time: ~1 millisecond                             │
└─────────────────────────────────────────────────────┘

Performance improvement: 10,000x faster!
```

🏗️ **ARCHITECT**: Real production scenario - **before/after index impact**:

```sql
-- E-commerce database: 10M orders
-- Query: Find all orders for customer in last 30 days

-- WITHOUT INDEX
EXPLAIN ANALYZE
SELECT * FROM orders
WHERE customer_id = 12345
  AND created_at > NOW() - INTERVAL '30 days';

/*
Result:
Seq Scan on orders  (cost=0.00..250000.00 rows=100 width=200)
                    (actual time=8234.123..8234.456 rows=15)
  Filter: (customer_id = 12345 AND created_at > ...)
  Rows Removed by Filter: 9999985
Planning Time: 0.234 ms
Execution Time: 8234.691 ms  ← 8+ seconds!
*/

-- WITH COMPOSITE INDEX
CREATE INDEX idx_customer_created ON orders(customer_id, created_at);

EXPLAIN ANALYZE
SELECT * FROM orders
WHERE customer_id = 12345
  AND created_at > NOW() - INTERVAL '30 days';

/*
Result:
Index Scan using idx_customer_created on orders
                    (cost=0.43..12.78 rows=15 width=200)
                    (actual time=0.034..0.067 rows=15)
  Index Cond: (customer_id = 12345 AND created_at > ...)
Planning Time: 0.156 ms
Execution Time: 0.098 ms  ← Sub-millisecond!
*/

-- 84,000x performance improvement!
```

**Interview gold**: "The difference between O(n) and O(log n) isn't academic. For 10M rows, it's the difference between 8 seconds and 1 millisecond in production."

---

### 2. **Index Anatomy: Data Structure Fundamentals**

🎓 **PROFESSOR**: An index is fundamentally a **separate data structure** that maintains **sorted pointers** to table rows:

```text
Table (Heap):
┌──────┬────────────┬──────────────┬─────────┐
│ ID   │ Email      │ Name         │ Age     │
├──────┼────────────┼──────────────┼─────────┤
│ 1    │ zoe@...    │ Zoe Smith    │ 25      │  ← Row ID 1 (physical location)
│ 2    │ alice@...  │ Alice Jones  │ 30      │  ← Row ID 2
│ 3    │ bob@...    │ Bob Wilson   │ 28      │  ← Row ID 3
│ 4    │ mike@...   │ Mike Brown   │ 35      │  ← Row ID 4
└──────┴────────────┴──────────────┴─────────┘

Index on Email (B-Tree):
┌────────────┬──────────────┐
│ Index Key  │ Row Pointer  │
├────────────┼──────────────┤
│ alice@...  │ → Row ID 2   │  ← Alphabetically sorted
│ bob@...    │ → Row ID 3   │
│ mike@...   │ → Row ID 4   │
│ zoe@...    │ → Row ID 1   │
└────────────┴──────────────┘

Notice: Index is sorted (alice < bob < mike < zoe)
        Table is NOT sorted (insertion order)
```

**Key Insight**: The index is a **secondary data structure**. The table (heap) remains unsorted.

#### Clustered vs Non-Clustered Indexes

```text
NON-CLUSTERED INDEX (Most indexes):
┌─────────┐      ┌──────────┐
│ Index   │ ──→  │ Heap     │
│ (sorted)│      │ (random) │
└─────────┘      └──────────┘
Two separate structures. Index points to heap.
Example: PostgreSQL secondary indexes, SQL Server non-clustered

CLUSTERED INDEX (Primary key in InnoDB):
┌─────────────────┐
│ Index = Table   │
│ (data in index) │
└─────────────────┘
The table IS the index. Data sorted by key.
Example: MySQL InnoDB primary key, SQL Server clustered
```

🏗️ **ARCHITECT**: Real MySQL InnoDB behavior:

```java
// Domain Model: Understanding InnoDB Storage
public class InnoDBTable {

    // PRIMARY KEY = Clustered Index (the table itself)
    private BTreeNode primaryIndex;  // Leaf nodes contain FULL ROWS

    // Secondary Indexes
    private Map<String, BTreeNode> secondaryIndexes;  // Leaf nodes contain PRIMARY KEY

    public Row findByEmail(String email) {
        // Step 1: Search secondary index on email
        BTreeNode emailIndex = secondaryIndexes.get("idx_email");
        Integer primaryKey = emailIndex.search(email);  // Returns PK value

        // Step 2: Use PK to search clustered index (the table)
        Row row = primaryIndex.search(primaryKey);  // Returns full row

        return row;  // Two lookups! This is why covering indexes matter
    }

    public Row findByPrimaryKey(Integer id) {
        // Step 1: Direct search in clustered index
        return primaryIndex.search(id);  // One lookup - faster!
    }
}
```

**Interview talking point**: "In InnoDB, a secondary index lookup requires TWO B-Tree searches: one in the secondary index to get the PK, then another in the clustered index to get the row. This is why primary key queries are fastest."

---

### 3. **Write Amplification: The Hidden Cost**

🎓 **PROFESSOR**: Indexes have a **cost**. Every write operation must update **all** indexes:

```text
Write Amplification Formula:
Total Writes = 1 (table write) + N (index writes)

For a table with 5 indexes:
- 1 INSERT = 6 write operations (1 table + 5 indexes)
- 1 UPDATE = up to 6 writes (if indexed columns change)
- 1 DELETE = 6 write operations
```

🏗️ **ARCHITECT**: Real production anti-pattern:

```sql
-- Anti-pattern: Over-indexing
CREATE TABLE user_events (
    id BIGINT PRIMARY KEY,
    user_id BIGINT,
    event_type VARCHAR(50),
    created_at TIMESTAMP,
    metadata JSONB
);

-- Someone created indexes on EVERYTHING:
CREATE INDEX idx_user_id ON user_events(user_id);
CREATE INDEX idx_event_type ON user_events(event_type);
CREATE INDEX idx_created_at ON user_events(created_at);
CREATE INDEX idx_user_event ON user_events(user_id, event_type);
CREATE INDEX idx_user_created ON user_events(user_id, created_at);
CREATE INDEX idx_event_created ON user_events(event_type, created_at);
CREATE INDEX idx_all ON user_events(user_id, event_type, created_at);

-- 7 indexes! Every INSERT now does 8 writes (1 table + 7 indexes)
-- Result: Write throughput dropped from 50K/sec to 8K/sec (6x slower!)
```

**The Fix**:
```sql
-- Analyze actual query patterns
-- Found only 3 queries:
-- 1. SELECT WHERE user_id = ? ORDER BY created_at
-- 2. SELECT WHERE event_type = ? AND created_at > ?
-- 3. SELECT WHERE user_id = ? AND event_type = ?

-- Optimal index strategy: 3 indexes instead of 7
CREATE INDEX idx_user_created ON user_events(user_id, created_at);
CREATE INDEX idx_event_created ON user_events(event_type, created_at);
CREATE INDEX idx_user_event ON user_events(user_id, event_type);

-- Result: Write throughput improved to 35K/sec
-- All queries still fast!
```

**Code Example - Monitoring Write Amplification**:

```python
class IndexHealthMonitor:
    """
    Monitor write amplification and unused indexes
    """
    def analyze_write_amplification(self, table: str) -> dict:
        # Get number of indexes
        indexes = db.execute(f"""
            SELECT COUNT(*) as index_count
            FROM pg_indexes
            WHERE tablename = '{table}'
        """).fetchone()

        # Get write statistics
        stats = db.execute(f"""
            SELECT
                n_tup_ins as inserts,
                n_tup_upd as updates,
                n_tup_del as deletes
            FROM pg_stat_user_tables
            WHERE relname = '{table}'
        """).fetchone()

        total_writes = stats['inserts'] + stats['updates'] + stats['deletes']
        amplification_factor = 1 + indexes['index_count']
        actual_disk_writes = total_writes * amplification_factor

        return {
            'logical_writes': total_writes,
            'physical_writes': actual_disk_writes,
            'amplification_factor': amplification_factor,
            'overhead': actual_disk_writes - total_writes
        }

    def find_unused_indexes(self, min_days: int = 30) -> list:
        """
        Find indexes that haven't been used in N days
        """
        return db.execute(f"""
            SELECT
                schemaname,
                tablename,
                indexname,
                idx_scan as scans,
                pg_size_pretty(pg_relation_size(indexrelid)) as size
            FROM pg_stat_user_indexes
            WHERE idx_scan = 0  -- Never used
              AND indexrelname NOT LIKE '%_pkey'  -- Exclude primary keys
            ORDER BY pg_relation_size(indexrelid) DESC
        """).fetchall()
```

---

### 4. **Index Selectivity & Cardinality**

🎓 **PROFESSOR**: Not all columns are good index candidates. **Selectivity** determines index effectiveness:

```text
Selectivity = Unique Values / Total Rows

High Selectivity (GOOD for indexes):
- email: 1,000,000 unique / 1,000,000 rows = 1.0 (100%)
- user_id: 950,000 unique / 1,000,000 rows = 0.95 (95%)
- phone_number: 980,000 unique / 1,000,000 rows = 0.98 (98%)

Low Selectivity (BAD for indexes):
- gender: 3 unique / 1,000,000 rows = 0.000003 (0.0003%)
- is_active: 2 unique / 1,000,000 rows = 0.000002 (0.0002%)
- country: 50 unique / 1,000,000 rows = 0.00005 (0.005%)
```

**Rule of Thumb**: Don't index columns with selectivity < 5% (unless using bitmap indexes)

🏗️ **ARCHITECT**: Real-world decision making:

```sql
-- BAD: Indexing low-selectivity column
CREATE INDEX idx_is_active ON users(is_active);  -- Only 2 values: true/false

-- Query performance:
SELECT * FROM users WHERE is_active = true;

/*
Problem:
- 90% of users are active
- Index scan reads 900,000 index entries
- Then reads 900,000 table rows
- Slower than sequential scan!
- Database optimizer will IGNORE the index
*/

-- GOOD: Use in composite index for filtering
CREATE INDEX idx_active_created ON users(is_active, created_at);

-- Now this query is fast:
SELECT * FROM users
WHERE is_active = true
  AND created_at > NOW() - INTERVAL '7 days';

/*
Why it works:
- First filter (is_active) narrows to 900K rows
- Second filter (created_at) narrows to ~10K rows
- Composite index handles both filters efficiently
*/
```

**Code Example - Selectivity Analysis**:

```python
class IndexAdvisor:
    """
    Analyze column selectivity to recommend indexes
    """
    def calculate_selectivity(self, table: str, column: str) -> float:
        result = db.execute(f"""
            SELECT
                COUNT(DISTINCT {column})::float / COUNT(*)::float as selectivity
            FROM {table}
        """).fetchone()

        return result['selectivity']

    def recommend_index(self, table: str, column: str) -> dict:
        selectivity = self.calculate_selectivity(table, column)
        total_rows = db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0]

        if selectivity > 0.95:
            return {
                'recommendation': 'EXCELLENT - Create index',
                'reason': 'Very high selectivity (nearly unique)',
                'index_type': 'B-Tree or Hash'
            }
        elif selectivity > 0.5:
            return {
                'recommendation': 'GOOD - Create index',
                'reason': 'Good selectivity for point queries',
                'index_type': 'B-Tree'
            }
        elif selectivity > 0.05:
            return {
                'recommendation': 'CONSIDER - Use in composite',
                'reason': 'Low selectivity alone, but useful in composite',
                'index_type': 'B-Tree (composite)'
            }
        else:
            return {
                'recommendation': 'AVOID - Poor index candidate',
                'reason': f'Only {selectivity*100:.2f}% unique values',
                'alternative': 'Consider partial index or bitmap'
            }
```

---

### 5. **Covering Indexes: The Ultimate Optimization**

🎓 **PROFESSOR**: A **covering index** contains ALL columns needed by a query, eliminating the need to access the table:

```text
Regular Index:
Query: SELECT name, age FROM users WHERE email = 'john@example.com';
Index on (email):

Step 1: Search index for 'john@example.com' → Get Row Pointer
Step 2: Access table to retrieve 'name' and 'age'
Total: 2 I/O operations

Covering Index:
Index on (email, name, age):  ← Includes all SELECT columns

Step 1: Search index for 'john@example.com' → Get name, age from index
Total: 1 I/O operation (50% faster!)
```

🏗️ **ARCHITECT**: Production example - **index-only scans**:

```sql
-- Slow query: Reporting dashboard
SELECT
    user_id,
    COUNT(*) as order_count,
    SUM(total_amount) as revenue
FROM orders
WHERE created_at >= '2024-01-01'
  AND created_at < '2024-02-01'
GROUP BY user_id;

-- Execution: Sequential scan (12 seconds for 50M rows)

-- Solution: Covering index
CREATE INDEX idx_orders_covering ON orders(
    created_at,           -- For WHERE clause
    user_id,              -- For GROUP BY
    total_amount          -- For SUM
);

-- Result: Index-only scan (800ms - 15x faster!)
/*
Why so fast?
- All data needed (created_at, user_id, total_amount) is IN the index
- Never touches the main table
- Reads ~1M index entries instead of 50M table rows
*/
```

**Code Example**:

```java
// Domain Model: Query Optimization
public class QueryOptimizer {

    public IndexRecommendation analyzeCovering(Query query) {
        Set<String> whereColumns = query.getWhereClause().getColumns();
        Set<String> selectColumns = query.getSelectClause().getColumns();
        Set<String> orderByColumns = query.getOrderByClause().getColumns();

        // A covering index must include ALL these columns
        Set<String> requiredColumns = new HashSet<>();
        requiredColumns.addAll(whereColumns);
        requiredColumns.addAll(selectColumns);
        requiredColumns.addAll(orderByColumns);

        // Optimal column order for covering index:
        // 1. WHERE equality columns (most selective first)
        // 2. WHERE range columns
        // 3. ORDER BY columns
        // 4. Remaining SELECT columns

        List<String> indexColumns = new ArrayList<>();
        indexColumns.addAll(whereColumns);     // Filtering
        indexColumns.addAll(orderByColumns);   // Sorting
        indexColumns.addAll(selectColumns);    // Coverage

        return new IndexRecommendation(
            "CREATE INDEX idx_covering ON " + query.getTable() +
            "(" + String.join(", ", indexColumns) + ")",
            "Index-only scan - no table access needed"
        );
    }
}
```

**Interview gold**: "A covering index is like carrying a cheat sheet to an exam. You have all the answers without opening the textbook."

---

### 6. **Index Maintenance & Fragmentation**

🎓 **PROFESSOR**: Indexes degrade over time due to **fragmentation**:

```text
B-Tree Fragmentation:

Fresh Index (after creation):
┌─────┬─────┬─────┬─────┐
│ 100%│ 100%│ 100%│ 100%│  ← Pages full, no wasted space
└─────┴─────┴─────┴─────┘

After many INSERT/UPDATE/DELETE operations:
┌────┬──┬─────┬───┬──────┬────┐
│ 40%│  │ 75% │   │ 60%  │    │  ← Pages partially empty
└────┴──┴─────┴───┴──────┴────┘
        ↑                    ↑
    Page splits       Deleted entries

Problems:
1. More pages to read (lower I/O efficiency)
2. Poor cache utilization
3. Slower range scans
```

🏗️ **ARCHITECT**: Production maintenance strategy:

```sql
-- Monitor index fragmentation (PostgreSQL)
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) as size,
    idx_scan as scans,
    idx_tup_read as tuples_read,
    idx_tup_fetch as tuples_fetched
FROM pg_stat_user_indexes
ORDER BY pg_relation_size(indexrelid) DESC;

-- Rebuild fragmented indexes
REINDEX INDEX CONCURRENTLY idx_orders_created_at;  -- PostgreSQL
-- or
ALTER INDEX idx_orders_created_at REBUILD ONLINE;  -- Oracle

-- For MySQL InnoDB:
OPTIMIZE TABLE orders;  -- Rebuilds clustered + secondary indexes
```

**Automated Maintenance**:

```python
class IndexMaintenanceScheduler:
    """
    Automate index maintenance during low-traffic periods
    """
    def __init__(self, maintenance_window: tuple):
        self.start_hour, self.end_hour = maintenance_window  # e.g., (2, 5) = 2am-5am

    def analyze_fragmentation(self) -> list:
        """
        Find indexes needing rebuild
        """
        return db.execute("""
            SELECT
                indexname,
                pg_relation_size(indexrelid) as size_bytes,
                idx_scan,
                -- Heuristic: Large indexes with high usage benefit most
                CASE
                    WHEN idx_scan > 1000000
                     AND pg_relation_size(indexrelid) > 1073741824  -- > 1GB
                    THEN 'HIGH_PRIORITY'
                    WHEN idx_scan > 100000
                    THEN 'MEDIUM_PRIORITY'
                    ELSE 'LOW_PRIORITY'
                END as priority
            FROM pg_stat_user_indexes
            WHERE schemaname = 'public'
              AND pg_relation_size(indexrelid) > 104857600  -- > 100MB
            ORDER BY priority, size_bytes DESC
        """).fetchall()

    def schedule_rebuild(self, index_name: str):
        """
        Schedule non-blocking index rebuild
        """
        if not self.is_maintenance_window():
            raise Exception("Outside maintenance window")

        # Use CONCURRENTLY to avoid blocking writes
        db.execute(f"REINDEX INDEX CONCURRENTLY {index_name}")

        logger.info(f"Rebuilt index {index_name} during maintenance window")

    def is_maintenance_window(self) -> bool:
        current_hour = datetime.now().hour
        return self.start_hour <= current_hour < self.end_hour
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

When discussing indexes in system design interviews:

### 1. Requirements Clarification (RADIO: Requirements)
```
Query Patterns:
- What are the most common queries?
- Point queries (WHERE id = ?) or range queries (WHERE date > ?)?
- Read-heavy or write-heavy workload?
- Any full-text search requirements?

Scale:
- How many rows in the table?
- Expected growth rate?
- Query latency requirements (P95, P99)?

Constraints:
- Available storage for indexes?
- Write throughput requirements?
- Can we afford write amplification?
```

### 2. Capacity Estimation (RADIO: Scale)
```
Index Size Estimation:

For B-Tree index on BIGINT column (8 bytes):
- Table: 100M rows
- Index entry: 8 bytes (key) + 8 bytes (pointer) = 16 bytes
- B-Tree overhead: ~30% (internal nodes, padding)
- Total: 100M * 16 * 1.3 = 2.08 GB

For composite index (user_id BIGINT, created_at TIMESTAMP):
- Index entry: 8 + 8 + 8 (pointer) = 24 bytes
- Total: 100M * 24 * 1.3 = 3.12 GB

Memory for Index Cache:
- Active indexes should fit in RAM
- PostgreSQL shared_buffers, MySQL buffer pool
- For 10GB indexes, need ~12-16GB RAM for buffer cache
```

### 3. Data Model (RADIO: Data Model)
```sql
-- Example: E-commerce Orders Table
CREATE TABLE orders (
    id BIGINT PRIMARY KEY,                    -- Clustered index (InnoDB)
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,

    -- Indexes based on query patterns
    INDEX idx_user_created (user_id, created_at),     -- User order history
    INDEX idx_status_created (status, created_at),    -- Admin dashboard
    INDEX idx_created (created_at)                    -- Time-series queries
);

-- Index selection rationale:
-- 1. idx_user_created: Covers "user's recent orders" query
-- 2. idx_status_created: Covers "pending orders" dashboard
-- 3. idx_created: Covers daily/monthly reports
```

### 4. Index Strategy (RADIO: Optimize)

**A. Query Analysis**
```sql
-- Use EXPLAIN to verify index usage
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM orders
WHERE user_id = 12345
  AND created_at > NOW() - INTERVAL '30 days';

-- Look for:
-- ✅ Index Scan (good)
-- ❌ Sequential Scan (bad - missing index)
-- ❌ Index Scan with Filter (bad - wrong index column order)
```

**B. Composite Index Column Ordering**
```text
Rule: Most Selective → Least Selective

Example:
Query: WHERE user_id = ? AND status = ? AND created_at > ?

Column Selectivity:
- user_id: 1M unique values (high)
- status: 5 unique values (low)
- created_at: continuous (medium)

Correct Order:
CREATE INDEX idx_composite ON orders(user_id, created_at, status);

Why?
1. user_id narrows to ~100 rows (highly selective)
2. created_at narrows to ~10 rows (range filter)
3. status filters remaining rows (equality, less selective)
```

**C. Partial Indexes**
```sql
-- Index only active records (save 90% space)
CREATE INDEX idx_active_users ON users(email)
WHERE is_active = true;

-- Index only recent data (time-based partitioning)
CREATE INDEX idx_recent_orders ON orders(user_id, created_at)
WHERE created_at > NOW() - INTERVAL '90 days';
```

---

## 🧠 **MIND MAP: DATABASE INDEX CONCEPTS**
```
                    DATABASE INDEXES
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
    DATA STRUCTURES    STRATEGIES        TRADEOFFS
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
 B-Tree    Hash   Composite Covering  Write    Space
  LSM              Partial   Index-   Ampli-   Over-
  R-Tree           Unique    Only     fication  head
   |                 |                  |
Balanced       Selectivity          1 + N
Sorted         Cardinality          indexes
Range          Column Order
```

### 💡 **EMOTIONAL ANCHORS (For Subconscious Power)**

Create strong memories with these analogies:

1. **Index = Book Index 📖**
   - Want to find "B-Tree" in a textbook?
   - Option A: Read all 500 pages (sequential scan)
   - Option B: Check index at back → "B-Tree: page 147" (index seek)
   - Same concept for databases!

2. **Clustered Index = Dictionary 📚**
   - Words are sorted A-Z (data IS sorted by key)
   - Fast to find "elephant" (binary search)
   - Non-clustered = separate index pointing to dictionary pages

3. **Write Amplification = Carbon Copies 📋**
   - Writing a form with 5 carbon copies
   - 1 write becomes 6 physical writes
   - More indexes = more carbon copies = slower writes

4. **Covering Index = Cheat Sheet 📝**
   - Exam question: "What's the capital of France?"
   - Regular index: "See textbook page 47" (need to look up)
   - Covering index: "Paris" (answer already on cheat sheet)
   - No need to access main textbook (table)!

5. **Index Selectivity = Phone Book Usefulness 📞**
   - Search by last name: Great! (high selectivity)
   - Search by "people named John": Terrible (low selectivity - too many)
   - Search by "gender": Useless (only 2 values)

---

## 🔑 **KEY TAKEAWAYS**

```
1. Indexes trade SPACE and WRITE performance for READ performance
2. Choose index type based on ACCESS PATTERN (equality, range, full-text)
3. Selectivity > 5% for single-column indexes
4. Composite index column order: Equality → Range → Sorting
5. Covering indexes eliminate table access (index-only scans)
6. Monitor: unused indexes, write amplification, fragmentation
7. Rebuild indexes during maintenance windows
8. EXPLAIN ANALYZE is your best friend for query tuning
```

**Production Mindset**:
> "Every index is a promise to make reads faster at the cost of writes and storage. Break that promise by measuring query performance before and after. The database optimizer is smart, but verify it's using your indexes!"
