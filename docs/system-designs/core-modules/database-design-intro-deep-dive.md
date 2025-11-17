# Database Design: General Introduction Deep Dive

## Contents

- [Database Design: General Introduction Deep Dive](#database-design-general-introduction-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Data Modeling Fundamentals](#data-modeling-fundamentals)
    - [Storage Engine Architecture](#storage-engine-architecture)
    - [Indexing Strategies](#indexing-strategies)
    - [Query Optimization Principles](#query-optimization-principles)
    - [Failure Modes & Data Integrity](#failure-modes--data-integrity)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model Design (RADIO: Data-Model)](#3-data-model-design-radio-data-model)
        - [Schema Design (RADIO: Initial Design)](#4-schema-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: DATABASE DESIGN CONCEPTS](#mind-map-database-design-concepts)

## Core Mental Model

```text
Database Design = Data Model + Access Patterns + Consistency Guarantees

Where:
- Data Model: How you represent reality (entities, relationships)
- Access Patterns: How you read/write data (queries, transactions)
- Consistency: What guarantees you need (ACID, eventual consistency)
```

**Fundamental Tradeoff:**
```
┌────────────────────────────────────────────────┐
│ The CAP Theorem (for distributed systems)     │
│                                                │
│   Consistency + Availability + Partition Tol.  │
│                                                │
│   Pick 2 (during network partition):          │
│   ├─ CP: Consistency + Partition Tolerance    │
│   │      (MongoDB, HBase, Redis)              │
│   ├─ AP: Availability + Partition Tolerance   │
│   │      (Cassandra, DynamoDB, CouchDB)       │
│   └─ CA: Not realistic in distributed systems │
└────────────────────────────────────────────────┘
```

**Real World Pattern:**
```java
// Domain Model: Entity Design
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email", unique = true),
    @Index(name = "idx_created_at", columnList = "created_at")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 255)
    private String email;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version  // Optimistic locking for concurrency control
    private Long version;

    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<Order> orders;

    // Denormalization for performance: cache expensive computation
    @Column(name = "order_count")
    private Integer orderCount;

    @PrePersist
    protected void onCreate() {
        createdAt = updatedAt = Instant.now();
        orderCount = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

**Key insight**: **Design databases around access patterns, not just entities**. Know your queries before you design your schema.

---

## Data Modeling Fundamentals

🎓 **PROFESSOR**: Database design starts with **three fundamental modeling approaches**:

```
┌──────────────────────────────────────────────────┐
│ 1. RELATIONAL MODEL (E.F. Codd, 1970)            │
│    - Data in tables (relations)                  │
│    - Relationships via foreign keys              │
│    - Strong schema, normalization                │
│    - SQL query language                          │
│                                                  │
│ 2. DOCUMENT MODEL                                │
│    - Data in nested documents (JSON/BSON)        │
│    - Schema flexibility                          │
│    - Denormalization encouraged                  │
│    - MongoDB, CouchDB                            │
│                                                  │
│ 3. GRAPH MODEL                                   │
│    - Nodes and edges                             │
│    - Natural for relationship-heavy data         │
│    - Neo4j, Amazon Neptune                       │
└──────────────────────────────────────────────────┘
```

### Normalization vs Denormalization

🏗️ **ARCHITECT**: This is the **most critical design decision**:

```sql
-- NORMALIZED (3NF): Minimize redundancy
-- Good for: Write-heavy, data integrity critical
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(200) NOT NULL
);

CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    total_amount DECIMAL(10,2),
    created_at TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- Query requires JOIN (expensive at scale)
SELECT u.name, o.order_id, o.total_amount
FROM users u
JOIN orders o ON u.user_id = o.user_id
WHERE u.email = 'john@example.com';


-- DENORMALIZED: Duplicate data for read performance
-- Good for: Read-heavy, performance critical
CREATE TABLE orders_denormalized (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,  -- Duplicated!
    user_name VARCHAR(200) NOT NULL,    -- Duplicated!
    total_amount DECIMAL(10,2),
    created_at TIMESTAMP
);

-- Single table lookup (fast!)
SELECT user_name, order_id, total_amount
FROM orders_denormalized
WHERE user_email = 'john@example.com';

-- Tradeoff: Must update user_name in ALL orders when user updates name
```

**Decision Matrix:**
```python
class NormalizationStrategy:
    """
    Decide normalization level based on access patterns
    """
    def choose_strategy(self, table_stats: TableStats) -> str:
        read_write_ratio = table_stats.reads / table_stats.writes

        if read_write_ratio > 100:
            # Read-heavy: denormalize for performance
            return "DENORMALIZE"

        if table_stats.has_strict_consistency_requirements:
            # Financial data, inventory: normalize
            return "NORMALIZE"

        if table_stats.join_depth > 3:
            # Deep joins kill performance
            return "DENORMALIZE_SELECTIVE"

        # Default: normalize, add read replicas
        return "NORMALIZE_WITH_CACHING"
```

**Interview talking point**: "I'd start normalized for data integrity, then denormalize specific hot paths based on profiling. Premature denormalization is the root of all evil."

---

## Storage Engine Architecture

🎓 **PROFESSOR**: All databases have **two core components**:

```
┌─────────────────────────────────────────────┐
│         DATABASE ARCHITECTURE               │
│                                             │
│  ┌─────────────────────────────────┐       │
│  │  QUERY LAYER (SQL Parser)       │       │
│  │  - Parse SQL                     │       │
│  │  - Optimize query plan           │       │
│  │  - Execute                       │       │
│  └──────────────┬──────────────────┘       │
│                 ↓                           │
│  ┌─────────────────────────────────┐       │
│  │  STORAGE ENGINE                  │       │
│  │  - Write data to disk            │       │
│  │  - Read data from disk           │       │
│  │  - Index management              │       │
│  │  - Transaction management        │       │
│  │  - Concurrency control           │       │
│  └─────────────────────────────────┘       │
└─────────────────────────────────────────────┘
```

### Two Main Storage Engine Types:

🏗️ **ARCHITECT**:

#### 1. **B-Tree Based** (MySQL InnoDB, PostgreSQL)
```
Characteristics:
├─ In-place updates (overwrite data)
├─ Good for random reads
├─ Write amplification (update = rewrite entire page)
├─ Fragmentation over time (needs VACUUM/OPTIMIZE)
└─ ACID transactions with WAL (Write-Ahead Log)

Use cases:
- OLTP (Online Transaction Processing)
- Strong consistency requirements
- Complex queries with JOINs
```

#### 2. **LSM-Tree Based** (Cassandra, RocksDB, HBase)
```
Characteristics:
├─ Append-only writes (no in-place updates)
├─ Excellent write throughput
├─ Sequential I/O (SSD and HDD friendly)
├─ Background compaction (merge sorted files)
└─ Read amplification (check multiple SSTables)

Use cases:
- Write-heavy workloads (time-series, logs)
- Large datasets (TB-PB scale)
- Eventual consistency acceptable
```

**Code Example:**
```python
# Simulated LSM-Tree Write Path
class LSMStorageEngine:
    """
    LSM-Tree: Log-Structured Merge Tree
    Optimized for write throughput
    """
    def __init__(self, memtable_size_mb=64):
        self.memtable = SortedDict()  # In-memory sorted map
        self.sstables = []             # Immutable sorted files on disk
        self.wal = WriteAheadLog()     # Durability
        self.memtable_size_mb = memtable_size_mb

    def put(self, key: str, value: bytes):
        """
        Write path: Memory → WAL → Disk (eventually)
        """
        # 1. Write to WAL for durability (crash recovery)
        self.wal.append(key, value)

        # 2. Write to memtable (fast, in-memory)
        self.memtable[key] = value

        # 3. Flush to disk when memtable full
        if self.memtable.size() > self.memtable_size_mb * 1024 * 1024:
            self._flush_memtable()

    def get(self, key: str) -> bytes:
        """
        Read path: Check memtable → Recent SSTables → Old SSTables
        """
        # 1. Check memtable first (hot data)
        if key in self.memtable:
            return self.memtable[key]

        # 2. Check SSTables (newest to oldest)
        for sstable in reversed(self.sstables):
            # Use bloom filter to avoid disk read
            if sstable.bloom_filter.might_contain(key):
                value = sstable.get(key)
                if value:
                    return value

        return None  # Key not found

    def _flush_memtable(self):
        """
        Flush memtable to disk as immutable SSTable
        """
        sstable = SSTable.create_from_memtable(self.memtable)
        self.sstables.append(sstable)
        self.memtable.clear()
        self.wal.clear()

        # Trigger background compaction if too many SSTables
        if len(self.sstables) > 10:
            self._trigger_compaction()
```

**Interview gold**: "LSM-Trees trade read performance for write performance. B-Trees are opposite. Know your workload: read-heavy vs write-heavy."

---

## Indexing Strategies

🎓 **PROFESSOR**: An index is a **data structure that improves query performance** at the cost of:
- Extra storage
- Slower writes (must update index)
- Maintenance overhead

```
┌──────────────────────────────────────────────┐
│ INDEX TYPES & USE CASES                      │
│                                              │
│ 1. PRIMARY KEY (Clustered)                   │
│    - Data physically sorted by this key      │
│    - Only ONE per table                      │
│    - Fast range scans                        │
│                                              │
│ 2. SECONDARY INDEX (Non-clustered)           │
│    - Separate data structure                 │
│    - Points to primary key                   │
│    - Multiple per table                      │
│                                              │
│ 3. COMPOSITE INDEX                           │
│    - Index on multiple columns               │
│    - Order matters! (leftmost prefix rule)   │
│                                              │
│ 4. COVERING INDEX                            │
│    - Includes all columns in query           │
│    - No table lookup needed (index-only)     │
│                                              │
│ 5. PARTIAL INDEX                             │
│    - Index subset of rows (WHERE clause)     │
│    - Smaller, faster for specific queries    │
└──────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: Real-world indexing strategy:

```sql
-- E-commerce orders table
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,           -- Clustered index
    user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,           -- 'pending', 'shipped', 'delivered'
    created_at TIMESTAMP NOT NULL,
    total_amount DECIMAL(10,2),

    -- Query: "Find all orders for user X"
    INDEX idx_user_id (user_id),

    -- Query: "Find pending orders created today"
    -- Composite index: order matters!
    INDEX idx_status_created (status, created_at),

    -- Query: "Find user's recent orders with totals"
    -- Covering index: avoid table lookup
    INDEX idx_user_recent_covering (
        user_id,
        created_at DESC,
        total_amount
    ),

    -- Partial index: only index active orders (save space)
    INDEX idx_active_orders (created_at)
        WHERE status IN ('pending', 'processing')
);
```

**Index Selection Decision Tree:**
```java
public class IndexAdvisor {

    public List<IndexRecommendation> analyze(Query query, TableStats stats) {
        List<IndexRecommendation> recommendations = new ArrayList<>();

        // 1. Check if index already exists
        if (hasExactIndex(query.whereClause)) {
            return Collections.emptyList();
        }

        // 2. High cardinality columns = good index candidates
        for (Column col : query.whereClause.getColumns()) {
            if (stats.getCardinality(col) > 0.8 * stats.getTotalRows()) {
                recommendations.add(
                    new IndexRecommendation(col, "HIGH_CARDINALITY")
                );
            }
        }

        // 3. Frequently joined columns
        if (query.hasJoins()) {
            for (JoinCondition join : query.getJoins()) {
                recommendations.add(
                    new IndexRecommendation(
                        join.getForeignKeyColumn(),
                        "JOIN_OPTIMIZATION"
                    )
                );
            }
        }

        // 4. ORDER BY + LIMIT queries benefit from index
        if (query.hasOrderBy() && query.hasLimit()) {
            recommendations.add(
                new IndexRecommendation(
                    query.getOrderByColumns(),
                    "SORT_OPTIMIZATION"
                )
            );
        }

        // 5. Covering index for SELECT columns
        if (canCreateCoveringIndex(query, stats)) {
            recommendations.add(
                new IndexRecommendation(
                    query.getAllColumns(),
                    "COVERING_INDEX"
                )
            );
        }

        return recommendations;
    }

    private boolean canCreateCoveringIndex(Query query, TableStats stats) {
        // Only if index size < 20% of table size
        long indexSize = estimateIndexSize(query.getAllColumns());
        return indexSize < stats.getTableSize() * 0.2;
    }
}
```

---

## Query Optimization Principles

🎓 **PROFESSOR**: Query optimization is about **minimizing disk I/O and CPU**:

```
Query Cost = Disk I/O Cost + CPU Cost + Network Cost

Where:
- Disk I/O: # of pages read (most expensive!)
- CPU: Sorting, filtering, joining
- Network: Data transfer (distributed systems)
```

**The Query Execution Pipeline:**
```
┌─────────────────────────────────────────────┐
│  1. PARSING                                 │
│     └─ SQL → Abstract Syntax Tree (AST)     │
│                                             │
│  2. LOGICAL OPTIMIZATION                    │
│     └─ Predicate pushdown                   │
│     └─ Constant folding                     │
│     └─ Join reordering                      │
│                                             │
│  3. PHYSICAL PLAN SELECTION                 │
│     └─ Choose index scan vs table scan      │
│     └─ Choose join algorithm                │
│     └─ Estimate row counts (statistics)     │
│                                             │
│  4. EXECUTION                               │
│     └─ Iterator model (Volcano-style)       │
│     └─ Vectorized execution (modern DBs)    │
└─────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: Common optimization techniques:

```sql
-- ❌ BAD: SELECT * loads unnecessary data
SELECT * FROM users WHERE email = 'john@example.com';

-- ✅ GOOD: Select only needed columns
SELECT user_id, name FROM users WHERE email = 'john@example.com';


-- ❌ BAD: Function on indexed column prevents index use
SELECT * FROM orders WHERE DATE(created_at) = '2024-01-15';

-- ✅ GOOD: Sargable predicate (Search ARGument ABLE)
SELECT * FROM orders
WHERE created_at >= '2024-01-15 00:00:00'
  AND created_at < '2024-01-16 00:00:00';


-- ❌ BAD: Implicit type conversion
SELECT * FROM users WHERE user_id = '12345';  -- user_id is BIGINT

-- ✅ GOOD: Explicit types
SELECT * FROM users WHERE user_id = 12345;


-- ❌ BAD: N+1 query problem
for user in users:
    orders = db.query("SELECT * FROM orders WHERE user_id = ?", user.id)

-- ✅ GOOD: Single query with JOIN
SELECT u.*, o.*
FROM users u
LEFT JOIN orders o ON u.user_id = o.user_id;
```

**Query Profiling:**
```python
class QueryProfiler:
    """
    Profile and optimize slow queries
    """
    def analyze_slow_query(self, sql: str, execution_time_ms: float):
        # Get query execution plan
        explain_plan = self.db.explain(sql)

        issues = []

        # 1. Check for table scans
        if explain_plan.has_table_scan():
            issues.append({
                'type': 'TABLE_SCAN',
                'severity': 'HIGH',
                'suggestion': 'Add index on WHERE/JOIN columns'
            })

        # 2. Check for large sorts
        if explain_plan.has_filesort() and explain_plan.rows > 10000:
            issues.append({
                'type': 'LARGE_SORT',
                'severity': 'MEDIUM',
                'suggestion': 'Add index on ORDER BY columns'
            })

        # 3. Check for nested loops on large tables
        if explain_plan.has_nested_loop() and explain_plan.rows > 100000:
            issues.append({
                'type': 'NESTED_LOOP_JOIN',
                'severity': 'HIGH',
                'suggestion': 'Use hash join or add index on join key'
            })

        # 4. Check for missing statistics
        if explain_plan.row_estimate_accuracy < 0.5:
            issues.append({
                'type': 'STALE_STATISTICS',
                'severity': 'MEDIUM',
                'suggestion': 'Run ANALYZE TABLE to update statistics'
            })

        return issues
```

---

## Failure Modes & Data Integrity

🎓 **PROFESSOR**: Databases fail in predictable ways. **Failure taxonomy**:

```
┌─────────────────────────────────────────────────┐
│ 1. CRASH (Power loss, OOM kill)                 │
│    → WAL (Write-Ahead Log) for recovery         │
│                                                 │
│ 2. CORRUPTION (Bit rot, hardware failure)       │
│    → Checksums + Replication                    │
│                                                 │
│ 3. CONSTRAINT VIOLATION (Bad data)              │
│    → Foreign keys, CHECK constraints            │
│                                                 │
│ 4. DEADLOCK (Mutual waiting)                    │
│    → Deadlock detection + abort                 │
│                                                 │
│ 5. SPLIT BRAIN (Network partition)              │
│    → Consensus protocols (Paxos/Raft)           │
└─────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: **Write-Ahead Logging (WAL)** is fundamental:

```python
class WriteAheadLog:
    """
    WAL ensures durability: Write to log BEFORE modifying data

    Invariant: Log must be flushed to disk before corresponding
               data page is written (fsync)
    """
    def __init__(self, log_file_path: str):
        self.log_file = open(log_file_path, 'ab')  # Append binary
        self.lsn = 0  # Log Sequence Number

    def append(self, transaction_id: int, operation: str,
               table: str, row: dict) -> int:
        """
        Append log record. Returns LSN.
        """
        log_record = {
            'lsn': self.lsn,
            'tx_id': transaction_id,
            'operation': operation,  # INSERT/UPDATE/DELETE
            'table': table,
            'old_value': row.get('old'),  # For rollback
            'new_value': row.get('new'),
            'timestamp': time.time()
        }

        # Write to log file
        serialized = pickle.dumps(log_record)
        self.log_file.write(serialized)

        # CRITICAL: fsync to disk before returning
        # Without this, data loss on crash!
        os.fsync(self.log_file.fileno())

        self.lsn += 1
        return self.lsn - 1

    def recover(self):
        """
        Crash recovery: Replay WAL to restore consistent state
        """
        # REDO phase: replay all committed transactions
        for log_record in self.read_log():
            if log_record['operation'] == 'COMMIT':
                self._redo_transaction(log_record['tx_id'])

        # UNDO phase: rollback uncommitted transactions
        for log_record in reversed(list(self.read_log())):
            if not self._is_committed(log_record['tx_id']):
                self._undo_operation(log_record)
```

**Data Integrity Constraints:**
```sql
CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(15,2) NOT NULL,

    -- Constraint: balance cannot be negative
    CHECK (balance >= 0),

    -- Foreign key: user must exist
    FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT  -- Prevent deletion if accounts exist
        ON UPDATE CASCADE,  -- Update account if user_id changes

    -- Unique constraint: one account per user
    UNIQUE (user_id)
);

-- Trigger for audit trail
CREATE TRIGGER audit_balance_changes
AFTER UPDATE ON accounts
FOR EACH ROW
BEGIN
    IF NEW.balance != OLD.balance THEN
        INSERT INTO audit_log (
            account_id,
            old_balance,
            new_balance,
            changed_at
        ) VALUES (
            NEW.account_id,
            OLD.balance,
            NEW.balance,
            NOW()
        );
    END IF;
END;
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

When asked to *"Design a database for X"*, use this structure:

### 1. Requirements Clarification (RADIO: Requirements)

```
Functional Requirements:
- What entities do we need? (Users, Orders, Products, etc.)
- What relationships exist? (1:1, 1:N, N:M)
- What queries are most common? (Read/write patterns)
- What data consistency is required? (Strong vs eventual)

Non-Functional Requirements:
- Scale: X writes/sec, Y reads/sec
- Data volume: Z GB/day growth
- Latency: P95 < 100ms
- Availability: 99.99%
- Compliance: GDPR, data retention
```

### 2. Capacity Estimation (RADIO: Scale)

```
Example: E-commerce database

Users:
- 100M active users
- User record: ~2KB
- Total: 100M * 2KB = 200GB

Orders:
- 10M orders/day
- Order record: ~5KB
- Daily: 10M * 5KB = 50GB/day
- Yearly: 50GB * 365 = 18TB/year

Read/Write Ratio:
- Reads: 100K QPS (product views, search)
- Writes: 10K QPS (orders, updates)
- Ratio: 10:1 (read-heavy, optimize for reads)

Indexing Overhead:
- Assume 30% overhead for indexes
- Total storage: 18TB * 1.3 = 23.4TB/year
```

### 3. Data Model Design (RADIO: Data-Model)

```sql
-- E-commerce core entities

CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    INDEX idx_email (email),
    INDEX idx_created_at (created_at)
);

CREATE TABLE products (
    product_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(500) NOT NULL,
    description TEXT,
    price DECIMAL(10,2) NOT NULL,
    stock_quantity INT NOT NULL DEFAULT 0,
    category_id INT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CHECK (price >= 0),
    CHECK (stock_quantity >= 0),
    INDEX idx_category (category_id),
    INDEX idx_price (price),
    FULLTEXT INDEX idx_name_desc (name, description)
);

CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    status ENUM('pending', 'processing', 'shipped', 'delivered', 'cancelled'),
    total_amount DECIMAL(10,2) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(user_id),
    INDEX idx_user_id (user_id),
    INDEX idx_status_created (status, created_at),
    INDEX idx_created_at (created_at DESC)
);

CREATE TABLE order_items (
    order_item_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    price_at_purchase DECIMAL(10,2) NOT NULL,  -- Snapshot price

    FOREIGN KEY (order_id) REFERENCES orders(order_id),
    FOREIGN KEY (product_id) REFERENCES products(product_id),
    INDEX idx_order_id (order_id),
    INDEX idx_product_id (product_id)
);
```

### 4. Schema Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────┐
│           APPLICATION LAYER                 │
│     (REST API, GraphQL, gRPC)               │
└────────────────┬────────────────────────────┘
                 ↓
┌─────────────────────────────────────────────┐
│         CONNECTION POOL                     │
│   (PgBouncer, ProxySQL, HikariCP)           │
└────────────────┬────────────────────────────┘
                 ↓
      ┌──────────────────────┐
      │   PRIMARY DATABASE   │
      │   (Write + Read)     │
      └──────────┬───────────┘
                 ↓ (Replication)
      ┌──────────────────────┐
      │  READ REPLICAS (3)   │
      │  (Read-only)         │
      └──────────────────────┘

Sharding Strategy (if needed):
├─ User-based sharding: shard_id = user_id % NUM_SHARDS
├─ Geographic sharding: US, EU, APAC databases
└─ Time-based sharding: orders_2024_q1, orders_2024_q2
```

### 5. Deep Dives (RADIO: Optimize)

**A. Caching Strategy**
```python
class CacheStrategy:
    """
    Multi-level caching for database optimization
    """
    def get_product(self, product_id: int) -> Product:
        # L1: Application memory cache (fastest)
        if product_id in self.local_cache:
            return self.local_cache[product_id]

        # L2: Redis cache (fast, shared across instances)
        cached = self.redis.get(f'product:{product_id}')
        if cached:
            product = deserialize(cached)
            self.local_cache[product_id] = product
            return product

        # L3: Database (slowest)
        product = self.db.query(
            "SELECT * FROM products WHERE product_id = ?",
            product_id
        )

        # Populate caches
        self.redis.setex(
            f'product:{product_id}',
            3600,  # 1 hour TTL
            serialize(product)
        )
        self.local_cache[product_id] = product

        return product
```

**B. Read/Write Splitting**
```java
@Service
public class DatabaseRouter {

    @Autowired
    private DataSource primaryDb;

    @Autowired
    private List<DataSource> readReplicas;

    public Connection getConnection(QueryType type) {
        if (type == QueryType.WRITE) {
            // All writes go to primary
            return primaryDb.getConnection();
        } else {
            // Reads distributed across replicas
            DataSource replica = selectReplica();
            return replica.getConnection();
        }
    }

    private DataSource selectReplica() {
        // Round-robin or least-connections
        int index = counter.getAndIncrement() % readReplicas.size();
        return readReplicas.get(index);
    }
}
```

**C. Database Partitioning**
```sql
-- Horizontal partitioning by date (MySQL 8.0+)
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    created_at DATE NOT NULL,
    total_amount DECIMAL(10,2)
)
PARTITION BY RANGE (YEAR(created_at)) (
    PARTITION p2022 VALUES LESS THAN (2023),
    PARTITION p2023 VALUES LESS THAN (2024),
    PARTITION p2024 VALUES LESS THAN (2025),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Query optimizer automatically scans only relevant partition
SELECT * FROM orders
WHERE created_at >= '2024-01-01'
  AND created_at < '2024-02-01';
-- Only scans p2024 partition!
```

---

## 🧠 MIND MAP: DATABASE DESIGN CONCEPTS

```
                DATABASE DESIGN
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓              ↓
   DATA MODEL    STORAGE        QUERY
                 ENGINE       OPTIMIZATION
        |             |              |
    ┌───┴───┐    ┌───┴───┐      ┌───┴───┐
    ↓       ↓    ↓       ↓      ↓       ↓
Relational  Doc  B-Tree LSM   Index  Query
Document   Graph          Tree  Plan  Rewrite
    |                |          |       |
Normalize      Append-Only   B+Tree  Predicate
vs Denorm      Updates      Hash     Pushdown
    |                |        Bitmap
Foreign Keys    Compaction    GIN
Constraints      WAL          |
    |            |         Covering
3NF/BCNF    Durability   Partial
                ACID      Composite
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

Create strong memories with these analogies:

### 1. **Database = Filing Cabinet 🗄️**
- **Tables** are drawers (Users drawer, Orders drawer)
- **Rows** are individual files
- **Indexes** are the tabs/labels for quick finding
- **Primary Key** is the file number (unique identifier)

### 2. **Normalization = DRY Principle 📝**
- Don't Repeat Yourself in code = Don't Duplicate Data
- Functions centralize logic = Foreign keys centralize data
- Refactoring removes duplication = Normalization removes redundancy

### 3. **Indexes = Book Index 📚**
- Want to find "Database" in a book?
- Option 1: Read every page (table scan)
- Option 2: Check index at back (index seek)
- Index takes space but saves time

### 4. **WAL = Laboratory Notebook 🔬**
- Scientists write down EVERYTHING before doing experiment
- If experiment fails, can replay from notes
- Database writes to log BEFORE changing data
- If crash occurs, replay log to recover

### 5. **Denormalization = Caching 🏪**
- Store duplicates of popular items at checkout (faster)
- Must keep synchronized with warehouse (consistency)
- Tradeoff: convenience vs maintenance

### 6. **B-Tree = Library Bookshelf 📖**
- Books sorted alphabetically (ordered)
- Quick to find range (A-C, D-F)
- Updates require moving books (write amplification)

### 7. **LSM-Tree = Inbox Processing 📥**
- New mail goes on top (append-only)
- Periodically sort and file (compaction)
- Recent items easy to find (in memory)
- Old items archived (on disk)

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Database Design:**

1. **Know Your Queries First**
   - Schema follows access patterns, not the other way around
   - "Show me your queries, and I'll design your schema"

2. **Premature Optimization is Real**
   - Start normalized, profile, then denormalize hot paths
   - Don't shard until single-node DB is exhausted

3. **Consistency is a Spectrum**
   - Not all data needs ACID
   - Choose consistency level per table/operation
   - Eventual consistency is okay for many use cases

---

## 🏗️ ARCHITECT'S CHECKLIST

Before shipping database design:

- [ ] **Indexes**: All WHERE/JOIN/ORDER BY columns indexed?
- [ ] **Constraints**: Foreign keys, CHECK constraints in place?
- [ ] **Monitoring**: Slow query log enabled? Metrics collected?
- [ ] **Backups**: Automated backups tested and verified?
- [ ] **Replication**: Read replicas for scale? Lag monitored?
- [ ] **Partitioning**: Large tables partitioned by date/range?
- [ ] **Connection Pooling**: Max connections configured properly?
- [ ] **Query Review**: All queries use EXPLAIN to verify plans?
- [ ] **Data Migration**: Zero-downtime migration strategy planned?
- [ ] **Disaster Recovery**: RTO/RPO defined and tested?

---

**Interview Closer**: "I'd design this database starting with normalized schema for data integrity, add indexes based on query profiling, implement read replicas for scale, and only denormalize specific hot paths after measuring performance. Let's walk through the schema..."
