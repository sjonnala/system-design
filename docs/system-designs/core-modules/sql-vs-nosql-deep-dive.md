# SQL vs NoSQL - Who Wins? Deep Dive

## Contents

- [SQL vs NoSQL - Who Wins? Deep Dive](#sql-vs-nosql---who-wins-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [SQL Databases: Strengths and Weaknesses](#sql-databases-strengths-and-weaknesses)
    - [NoSQL Databases: Types and Trade-offs](#nosql-databases-types-and-trade-offs)
    - [The CAP Theorem Reality](#the-cap-theorem-reality)
    - [Data Modeling: Normalized vs Denormalized](#data-modeling-normalized-vs-denormalized)
    - [Scalability Patterns](#scalability-patterns)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Database Selection Decision Tree](#1-database-selection-decision-tree)
        - [Polyglot Persistence Strategy](#2-polyglot-persistence-strategy)
        - [Migration Considerations](#3-migration-considerations)
        - [Real-World Architectures](#4-real-world-architectures)
    - [MIND MAP: SQL VS NOSQL](#mind-map-sql-vs-nosql)

## Core Mental Model

```text
SQL vs NoSQL is NOT "either/or"
It's about choosing the RIGHT TOOL for the RIGHT JOB

SQL = Structured Query Language (Relational model)
NoSQL = Not Only SQL (Multiple models: document, k-v, graph, column)

Key Insight: Modern systems use BOTH (polyglot persistence)
```

**The Fundamental Tradeoff:**
```
┌────────────────────────────────────────────────┐
│ SQL DATABASES (RDBMS)                          │
│                                                │
│ Optimize for:                                  │
│ ├─ ACID transactions (consistency)             │
│ ├─ Complex queries (JOINs)                     │
│ ├─ Strong schema (data integrity)              │
│ └─ Normalized data (minimize redundancy)       │
│                                                │
│ Trade-off:                                     │
│ ├─ Harder to scale horizontally                │
│ ├─ Schema changes expensive                    │
│ └─ Can be slower for simple lookups            │
│                                                │
│ Examples: PostgreSQL, MySQL, Oracle, SQL Server│
└────────────────────────────────────────────────┘

┌────────────────────────────────────────────────┐
│ NoSQL DATABASES                                │
│                                                │
│ Optimize for:                                  │
│ ├─ Horizontal scalability (partition easily)   │
│ ├─ Flexible schema (rapid iteration)           │
│ ├─ High throughput (simple queries)            │
│ └─ Availability (eventual consistency OK)      │
│                                                │
│ Trade-off:                                     │
│ ├─ Limited ACID (eventual consistency)         │
│ ├─ No JOINs (denormalization required)         │
│ ├─ Application complexity (logic in code)      │
│ └─ Data redundancy (denormalization)           │
│                                                │
│ Examples: MongoDB, Cassandra, Redis, Neo4j     │
└────────────────────────────────────────────────┘
```

**Key insight**: "Who wins?" Wrong question. Ask: "Which fits my use case?"

---

## SQL Databases: Strengths and Weaknesses

🎓 **PROFESSOR**: SQL databases excel at **structured, relational data**:

### Strengths

```
┌─────────────────────────────────────────────────┐
│ 1. ACID TRANSACTIONS                            │
│    └─ Critical for financial, inventory data    │
│                                                 │
│ 2. POWERFUL QUERY LANGUAGE                      │
│    └─ Complex JOINs, aggregations, subqueries   │
│                                                 │
│ 3. DATA INTEGRITY                               │
│    └─ Foreign keys, constraints, triggers       │
│                                                 │
│ 4. MATURE ECOSYSTEM                             │
│    └─ 40+ years of tooling, expertise          │
│                                                 │
│ 5. DECLARATIVE QUERIES                          │
│    └─ Optimizer handles execution plan         │
│                                                 │
│ 6. STRONG TYPING                                │
│    └─ Schema enforces data quality              │
└─────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: Example SQL strengths:

```sql
-- Strength 1: Complex queries are elegant
SELECT
    u.name,
    COUNT(o.order_id) as order_count,
    SUM(o.total_amount) as total_spent,
    AVG(oi.quantity) as avg_items_per_order
FROM users u
LEFT JOIN orders o ON u.user_id = o.user_id
LEFT JOIN order_items oi ON o.order_id = oi.order_id
WHERE u.created_at >= '2024-01-01'
GROUP BY u.user_id, u.name
HAVING COUNT(o.order_id) > 5
ORDER BY total_spent DESC
LIMIT 100;

-- In NoSQL: Would require:
-- 1. Multiple queries
-- 2. Application-level joins
-- 3. Denormalized data (duplicate user info in orders)


-- Strength 2: Transactions ensure consistency
BEGIN;

-- Transfer money: atomic, consistent, isolated, durable
UPDATE accounts SET balance = balance - 100
WHERE account_id = 1;

UPDATE accounts SET balance = balance + 100
WHERE account_id = 2;

-- If either fails, BOTH rollback
COMMIT;

-- In NoSQL: Would require:
-- 1. Two-phase commit (complex)
-- 2. Or eventual consistency (risky for money)


-- Strength 3: Constraints enforce integrity
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(20) CHECK (status IN ('pending', 'shipped', 'delivered')),
    total_amount DECIMAL(10,2) CHECK (total_amount >= 0),

    -- Can't delete user with orders
    FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT
);

-- NoSQL: These checks must be in application code (error-prone)
```

### Weaknesses

```
┌─────────────────────────────────────────────────┐
│ 1. VERTICAL SCALING LIMITS                      │
│    └─ Single-node bottleneck (eventually)       │
│                                                 │
│ 2. SCHEMA RIGIDITY                              │
│    └─ ALTER TABLE locks, downtime for changes   │
│                                                 │
│ 3. JOIN PERFORMANCE                             │
│    └─ Degraded at scale (TB+ datasets)          │
│                                                 │
│ 4. OBJECT-RELATIONAL IMPEDANCE MISMATCH         │
│    └─ Objects → tables → objects (ORM overhead) │
│                                                 │
│ 5. SHARDING COMPLEXITY                          │
│    └─ Not designed for horizontal partitioning  │
└─────────────────────────────────────────────────┘
```

**Code Example: SQL Weaknesses**
```java
// Weakness: ORM impedance mismatch
@Entity
public class User {
    @Id
    private Long id;

    private String name;

    // One user has many orders
    @OneToMany(mappedBy = "user", fetch = FetchType.LAZY)
    private List<Order> orders;  // Loaded separately (N+1 problem)
}

// N+1 query problem
List<User> users = userRepo.findAll();  // 1 query

for (User user : users) {
    // Each access triggers separate query!
    System.out.println(user.getOrders().size());  // N queries
}
// Total: 1 + N queries (performance killer)

// Solution: JOIN FETCH
@Query("SELECT u FROM User u LEFT JOIN FETCH u.orders WHERE ...")
List<User> findAllWithOrders();  // 1 query with JOIN


// Weakness: Schema changes
-- Slow migration on large table
ALTER TABLE users ADD COLUMN phone_number VARCHAR(20);
-- Locks table (minutes to hours on TB-scale)

-- NoSQL equivalent: Just start writing new field
db.users.insertOne({
    name: "Alice",
    phone_number: "555-1234"  // New field, no migration!
});
```

**Interview talking point**: "SQL is great for complex relationships and transactions. But at web scale (billions of rows), JOINs slow down and horizontal scaling is painful."

---

## NoSQL Databases: Types and Trade-offs

🎓 **PROFESSOR**: NoSQL is **not one thing** - four main types:

### 1. Document Databases (MongoDB, CouchDB)

```javascript
// Data stored as JSON-like documents
{
    "_id": "user_123",
    "name": "Alice",
    "email": "alice@example.com",
    "address": {
        "street": "123 Main St",
        "city": "NYC",
        "zip": "10001"
    },
    "orders": [
        {
            "order_id": "order_456",
            "total": 99.99,
            "items": [
                { "product": "Book", "quantity": 2 }
            ]
        }
    ]
}

// Strengths:
// ├─ Flexible schema (add fields anytime)
// ├─ Nested data (no JOINs needed)
// ├─ Intuitive for developers (JSON)
// └─ Horizontal scaling built-in

// Weaknesses:
// ├─ Data duplication (orders embedded in users)
// ├─ Limited transactions (single document)
// ├─ No foreign keys (manual consistency)
// └─ Query complexity for joins
```

### 2. Key-Value Stores (Redis, DynamoDB)

```python
# Simplest NoSQL: hash table
cache.set("user:123", {
    "name": "Alice",
    "email": "alice@example.com"
})

value = cache.get("user:123")

# Strengths:
# ├─ Extreme performance (O(1) lookup)
# ├─ Simple API (get/set/delete)
# ├─ Easy to partition (shard by key)
# └─ In-memory options (Redis)

# Weaknesses:
# ├─ No queries (only lookup by key)
# ├─ No secondary indexes (can't query by email)
# ├─ Application must manage relationships
# └─ Limited data model
```

### 3. Column-Family Stores (Cassandra, HBase)

```
// Wide-column storage: rows can have different columns
Row Key: user_123
├─ name: "Alice"
├─ email: "alice@example.com"
└─ last_login: "2024-01-15"

Row Key: user_456
├─ name: "Bob"
├─ phone: "555-1234"  // Different columns!
└─ created_at: "2023-06-10"

// Strengths:
// ├─ Handles massive scale (PB+)
// ├─ Time-series data (append-optimized)
// ├─ High write throughput
// └─ Tunable consistency

// Weaknesses:
// ├─ Limited queries (primary key only)
// ├─ No JOINs
// ├─ Complex data modeling
// └─ Eventual consistency default
```

### 4. Graph Databases (Neo4j, Amazon Neptune)

```cypher
// Nodes and edges (relationships)
CREATE (alice:User {name: "Alice"})
CREATE (bob:User {name: "Bob"})
CREATE (alice)-[:FOLLOWS]->(bob)

// Strengths:
// ├─ Relationship queries (graph traversal)
// ├─ Social networks, recommendation engines
// ├─ Shortest path, pattern matching
// └─ Intuitive for connected data

// Weaknesses:
// ├─ Niche use case
// ├─ Hard to shard (graph partitioning)
// ├─ Limited aggregations
// └─ Smaller ecosystem
```

🏗️ **ARCHITECT**: Decision matrix for NoSQL types:

```python
class NoSQLTypeSelector:
    """
    Choose NoSQL type based on access patterns
    """
    def select_type(self, requirements: Requirements) -> str:

        # Document DB: Flexible schema, nested data
        if (requirements.has_nested_objects and
            requirements.schema_evolves_frequently and
            requirements.needs_simple_queries):
            return "DOCUMENT_DB"  # MongoDB

        # Key-Value: Simple lookups, high performance
        if (requirements.access_pattern == "LOOKUP_BY_KEY" and
            requirements.needs_low_latency and
            not requirements.needs_complex_queries):
            return "KEY_VALUE_STORE"  # Redis, DynamoDB

        # Column-family: Time-series, high write volume
        if (requirements.is_time_series or
            requirements.write_throughput > 1_000_000 and
            requirements.data_size > "10TB"):
            return "COLUMN_FAMILY"  # Cassandra

        # Graph: Relationship-heavy queries
        if (requirements.has_many_relationships and
            requirements.needs_graph_traversal):
            return "GRAPH_DB"  # Neo4j

        # Default: Document DB (most flexible)
        return "DOCUMENT_DB"
```

**Interview gold**: "NoSQL isn't one technology. MongoDB (document) and Cassandra (column-family) solve different problems. Choose based on access patterns, not hype."

---

## The CAP Theorem Reality

🎓 **PROFESSOR**: CAP Theorem explains the tradeoffs:

```
CAP Theorem (Eric Brewer):
In a distributed system, you can only guarantee 2 of 3:

C = Consistency (all nodes see same data)
A = Availability (system always responds)
P = Partition Tolerance (works despite network failures)

Reality: Network partitions WILL happen
→ Must choose: CP or AP
```

**The Spectrum:**
```
┌─────────────────────────────────────────────────┐
│ CP SYSTEMS (Consistency + Partition Tolerance)  │
│                                                 │
│ Choose consistency over availability            │
│ ├─ During partition: reject writes              │
│ ├─ Wait for consensus                           │
│ └─ Examples: MongoDB (default), HBase, Redis    │
│                                                 │
│ Use cases:                                      │
│ └─ Financial systems, inventory, auth           │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ AP SYSTEMS (Availability + Partition Tolerance) │
│                                                 │
│ Choose availability over consistency            │
│ ├─ During partition: accept writes              │
│ ├─ Resolve conflicts later                      │
│ └─ Examples: Cassandra, DynamoDB, CouchDB       │
│                                                 │
│ Use cases:                                      │
│ └─ Social media, analytics, shopping carts      │
└─────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: Real-world CAP examples:

```python
# CP Example: Banking (MongoDB with strong consistency)
class BankingService:
    def transfer(self, from_account, to_account, amount):
        # Use majority write concern (CP)
        session = client.start_session()
        with session.start_transaction(
            write_concern=WriteConcern(w="majority")
        ):
            # If partition: operation FAILS (unavailable)
            # But guarantees: all nodes see same data
            accounts.update_one(
                {"_id": from_account},
                {"$inc": {"balance": -amount}},
                session=session
            )
            accounts.update_one(
                {"_id": to_account},
                {"$inc": {"balance": amount}},
                session=session
            )
        # Either both succeed or both fail


# AP Example: Shopping cart (Cassandra, DynamoDB)
class ShoppingCartService:
    def add_item(self, user_id, item):
        # Write to any available node (AP)
        # Eventual consistency: other nodes catch up later
        dynamodb.put_item(
            TableName='shopping_carts',
            Item={
                'user_id': user_id,
                'items': item
            },
            # Don't wait for replication
            ReturnConsumedCapacity='NONE'
        )
        # Always available (even during partition)
        # Trade-off: might see stale data briefly


# PACELC: Extension of CAP
# Even without Partition:
# ├─ Latency vs Consistency trade-off exists
# └─ E.g., sync replication (consistent, slow) vs async (fast, stale)
```

**Interview talking point**: "CAP is theoretical. In practice, systems are on a spectrum. Even 'CP' systems like PostgreSQL can be tuned for availability. It's about tunable consistency."

---

## Data Modeling: Normalized vs Denormalized

🎓 **PROFESSOR**: **Data modeling philosophy differs**:

### SQL: Normalized (Minimize Redundancy)

```sql
-- Third Normal Form (3NF): No transitive dependencies

-- Users table
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(255) UNIQUE
);

-- Orders table
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    created_at TIMESTAMP,
    total_amount DECIMAL(10,2)
);

-- Order items table
CREATE TABLE order_items (
    order_item_id BIGINT PRIMARY KEY,
    order_id BIGINT REFERENCES orders(order_id),
    product_id BIGINT REFERENCES products(product_id),
    quantity INT,
    price DECIMAL(10,2)
);

-- Query requires JOINs:
SELECT
    u.name,
    o.order_id,
    o.total_amount,
    oi.quantity,
    p.name AS product_name
FROM users u
JOIN orders o ON u.user_id = o.user_id
JOIN order_items oi ON o.order_id = oi.order_id
JOIN products p ON oi.product_id = p.product_id
WHERE u.user_id = 123;

-- Benefits:
-- ├─ No data duplication (user name stored once)
-- ├─ Updates easy (change user name in one place)
-- └─ Enforced integrity (foreign keys)

-- Cost:
-- ├─ Slow reads (multiple JOINs)
-- └─ Hard to scale (JOIN across shards)
```

### NoSQL: Denormalized (Optimize for Queries)

```javascript
// MongoDB: Embed related data (denormalized)
{
    "_id": "user_123",
    "name": "Alice",
    "email": "alice@example.com",
    "orders": [
        {
            "order_id": "order_456",
            "created_at": "2024-01-15T10:30:00Z",
            "total_amount": 99.99,
            "items": [
                {
                    "product_id": "prod_789",
                    "product_name": "Book",  // Duplicated!
                    "quantity": 2,
                    "price": 49.99
                }
            ]
        }
    ]
}

// Query is simple (single document lookup):
db.users.findOne({ "_id": "user_123" })
// No JOINs! All data in one document

// Benefits:
// ├─ Fast reads (single lookup)
// ├─ Easy to scale (document = unit of sharding)
// └─ Schema flexible

// Cost:
// ├─ Data duplication (product_name in every order_item)
// ├─ Updates complex (if product name changes, update ALL orders)
// └─ Document size limits (16MB in MongoDB)
```

**Design Decision:**
```java
public class DataModelSelector {

    public ModelType selectModel(AccessPattern pattern) {
        // Read-heavy, 1:N relationships → Denormalize
        if (pattern.readWriteRatio > 10 &&
            pattern.hasOneToManyRelationships()) {
            return ModelType.DENORMALIZED;  // NoSQL
        }

        // Complex queries, transactions → Normalize
        if (pattern.needsComplexJoins() ||
            pattern.needsACID()) {
            return ModelType.NORMALIZED;  // SQL
        }

        // Frequently changing data → Normalize
        if (pattern.updateFrequency > 0.1) {
            return ModelType.NORMALIZED;  // Avoid denorm update cost
        }

        // Default: Normalize first, denormalize hot paths
        return ModelType.NORMALIZED;
    }
}
```

---

## Scalability Patterns

🎓 **PROFESSOR**: Scaling approaches differ fundamentally:

### SQL: Vertical then Horizontal (Hard)

```
┌─────────────────────────────────────────────────┐
│ STAGE 1: SINGLE-NODE (0-10M users)              │
│                                                 │
│     ┌────────────────┐                          │
│     │  PostgreSQL    │                          │
│     │  (One Machine) │                          │
│     └────────────────┘                          │
│                                                 │
│ Scale: Add RAM, CPU, SSD (vertical scaling)     │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ STAGE 2: PRIMARY-REPLICA (10M-100M users)       │
│                                                 │
│     ┌────────────────┐                          │
│     │  Primary (RW)  │                          │
│     └────────┬───────┘                          │
│              ↓ (replication)                    │
│      ┌───────┴────────┐                         │
│      ↓                ↓                         │
│ ┌─────────┐      ┌─────────┐                   │
│ │Replica 1│      │Replica 2│  (Read-only)      │
│ └─────────┘      └─────────┘                   │
│                                                 │
│ Scale: Read traffic distributed to replicas     │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ STAGE 3: SHARDING (100M+ users) COMPLEX!        │
│                                                 │
│  ┌───────────┐       ┌───────────┐             │
│  │ Shard 1   │       │ Shard 2   │             │
│  │ Users 1-  │       │ Users 50M-│             │
│  │ 50M       │       │ 100M      │             │
│  └───────────┘       └───────────┘             │
│                                                 │
│ Challenges:                                     │
│ ├─ Cross-shard JOINs impossible                 │
│ ├─ Distributed transactions complex (2PC)       │
│ ├─ Rebalancing shards (data migration)          │
│ └─ Application complexity (shard routing)       │
└─────────────────────────────────────────────────┘
```

### NoSQL: Horizontal from Day 1 (Easy)

```
┌─────────────────────────────────────────────────┐
│ CASSANDRA/DYNAMODB CLUSTER                      │
│                                                 │
│    ┌──────┐   ┌──────┐   ┌──────┐             │
│    │Node 1│   │Node 2│   │Node 3│             │
│    │ A-F  │   │ G-M  │   │ N-Z  │             │
│    └──────┘   └──────┘   └──────┘             │
│                                                 │
│ ├─ Consistent hashing (automatic partitioning)  │
│ ├─ Add nodes → data rebalances automatically    │
│ ├─ No JOINs → no cross-partition queries        │
│ └─ Linear scalability (double nodes = 2x QPS)   │
└─────────────────────────────────────────────────┘
```

**Code Example: Sharding Complexity**

```java
// SQL Sharding: Manual, complex
public class ShardedUserRepository {

    private final DataSource shard1;  // Users 1-50M
    private final DataSource shard2;  // Users 50M-100M

    public User findById(Long userId) {
        // Application must route to correct shard
        DataSource shard = (userId <= 50_000_000) ? shard1 : shard2;
        return queryUser(shard, userId);
    }

    public List<User> findFriends(Long userId) {
        // Friends might be on different shard!
        // Need to query both shards and merge results
        List<User> friendsFromShard1 = queryFriends(shard1, userId);
        List<User> friendsFromShard2 = queryFriends(shard2, userId);

        // Application-level merge
        return Stream.concat(
            friendsFromShard1.stream(),
            friendsFromShard2.stream()
        ).collect(Collectors.toList());
    }
}


// NoSQL: Built-in partitioning
public class CassandraUserRepository {

    private final CassandraSession session;

    public User findById(Long userId) {
        // Cassandra automatically routes to correct node
        // Using consistent hashing on partition key
        return session.execute(
            "SELECT * FROM users WHERE user_id = ?",
            userId
        ).one();
        // No application-level sharding logic!
    }

    public List<User> findFriends(Long userId) {
        // If friends stored denormalized in user doc:
        return session.execute(
            "SELECT friends FROM users WHERE user_id = ?",
            userId
        ).one();
        // Single query, no cross-partition complexity
    }
}
```

**Interview gold**: "SQL scales vertically (bigger machines) then horizontally (sharding, complex). NoSQL scales horizontally from day 1 (simpler). Choose based on when you'll need horizontal scale."

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Database Selection Decision Tree

```
┌─────────────────────────────────────────────────┐
│ DECISION TREE                                   │
│                                                 │
│ Q1: Need ACID transactions?                     │
│     YES → SQL                                   │
│     NO  → Continue                              │
│                                                 │
│ Q2: Need complex JOINs or aggregations?         │
│     YES → SQL                                   │
│     NO  → Continue                              │
│                                                 │
│ Q3: Schema changes frequently?                  │
│     YES → NoSQL (Document)                      │
│     NO  → Continue                              │
│                                                 │
│ Q4: Access pattern?                             │
│     - Simple key-value → NoSQL (Redis, DynamoDB)│
│     - Time-series, logs → NoSQL (Cassandra)     │
│     - Graph relationships → NoSQL (Neo4j)       │
│     - Flexible docs → NoSQL (MongoDB)           │
│     - Relational → SQL                          │
│                                                 │
│ Q5: Scale expectations?                         │
│     - < 10M users → SQL (single-node)           │
│     - 10M-100M → SQL (replicas) or NoSQL        │
│     - > 100M → NoSQL (built-in sharding)        │
│                                                 │
│ Q6: Consistency requirements?                   │
│     - Strong (financial) → SQL or CP NoSQL      │
│     - Eventual OK → AP NoSQL                    │
└─────────────────────────────────────────────────┘
```

**Code Framework:**
```python
class DatabaseRecommendation:
    def recommend(self, requirements: Requirements) -> str:

        # Critical: ACID transactions
        if requirements.needs_acid_transactions:
            if requirements.scale < "100M_users":
                return "PostgreSQL"  # Best ACID + features
            else:
                return "CockroachDB or Google Spanner"  # Distributed SQL

        # Complex analytical queries
        if requirements.has_complex_queries:
            return "PostgreSQL or ClickHouse"  # OLAP

        # Simple lookups, extreme performance
        if requirements.access_pattern == "KEY_VALUE":
            if requirements.needs_persistence:
                return "DynamoDB or Redis (persistent)"
            else:
                return "Redis or Memcached"

        # Time-series, IoT, logs
        if requirements.is_time_series:
            return "Cassandra or InfluxDB or TimescaleDB"

        # Graph data (social, recommendations)
        if requirements.is_graph_data:
            return "Neo4j or Amazon Neptune"

        # Flexible schema, rapid iteration
        if requirements.schema_flexibility:
            return "MongoDB"

        # Default: PostgreSQL (most versatile)
        return "PostgreSQL (then migrate if needed)"
```

### 2. Polyglot Persistence Strategy

```
Modern Architecture: Use MULTIPLE databases!

Example: E-commerce system

┌─────────────────────────────────────────────────┐
│ PostgreSQL (Primary transactional data)         │
│ ├─ Users, orders, payments                      │
│ ├─ Requires: ACID, foreign keys, transactions   │
│ └─ Scale: Read replicas, then Citus (sharding)  │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ Redis (Caching, session storage)                │
│ ├─ User sessions                                │
│ ├─ Product catalog cache                        │
│ └─ Leaderboards, counters                       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ Elasticsearch (Search, analytics)               │
│ ├─ Product search                               │
│ ├─ Log analytics                                │
│ └─ Full-text search                             │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ MongoDB (Product catalog)                       │
│ ├─ Flexible product attributes                  │
│ ├─ Rapid schema evolution                       │
│ └─ Denormalized for fast reads                  │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ Neo4j (Recommendations)                         │
│ ├─ "Customers who bought X also bought Y"       │
│ ├─ Social connections                           │
│ └─ Graph traversal queries                      │
└─────────────────────────────────────────────────┘
```

### 3. Migration Considerations

```java
// Pattern: Dual-write during migration (SQL → NoSQL)
@Service
public class DualWriteUserService {

    private final UserSqlRepository sqlRepo;
    private final UserMongoRepository mongoRepo;

    public void createUser(User user) {
        // Write to both databases
        sqlRepo.save(user);  // Old system

        try {
            mongoRepo.save(user);  // New system
        } catch (Exception e) {
            // Log error but don't fail transaction
            // Eventually consistent migration
            log.error("Failed to write to MongoDB", e);
        }
    }

    public User findUser(Long userId) {
        // Phase 1: Read from SQL (old system)
        return sqlRepo.findById(userId);

        // Phase 2: Read from MongoDB, fallback to SQL
        // User user = mongoRepo.findById(userId);
        // return user != null ? user : sqlRepo.findById(userId);

        // Phase 3: Read from MongoDB only (migration complete)
        // return mongoRepo.findById(userId);
    }
}
```

### 4. Real-World Architectures

```
NETFLIX:
├─ Cassandra: Viewing history, user preferences (billions of events/day)
├─ MySQL: Billing, subscriptions (ACID required)
└─ Elasticsearch: Search, recommendations

UBER:
├─ Postgres: Trips, payments, driver data (transactional)
├─ Redis: Geospatial (driver locations)
├─ Cassandra: Time-series analytics
└─ MySQL: User accounts

SPOTIFY:
├─ PostgreSQL: User accounts, subscriptions
├─ Cassandra: User listening history, playlists
└─ Google Bigtable: Analytics

AMAZON:
├─ DynamoDB: Shopping cart, session storage
├─ Aurora (MySQL): Orders, inventory
├─ Neptune: Product recommendations (graph)
└─ Redshift: Analytics (data warehouse)
```

---

## 🧠 MIND MAP: SQL VS NOSQL

```
        DATABASE CHOICE
               |
       ┌───────┴────────┐
       ↓                ↓
      SQL            NoSQL
       |                |
  ┌────┴────┐      ┌────┴────┐
  ↓         ↓      ↓         ↓
ACID     Joins   Scale    Flexible
Transact Complex Horizon  Schema
  |         |       |         |
Money   Analytics Part    Rapid
Inventory Reports  Key     Iteration
  |         |       |         |
Strong  Foreign  Cassandra Document
Consist  Keys    DynamoDB  MongoDB
  |         |       |         |
Postgres  OLAP   KV-Store   JSON
MySQL    Click   Redis     Nested
  |      House     |         |
Vertical        Linear    Denorm
Scale          Scale     Model
  |               |         |
Replicas      No Joins  Eventual
Sharding*     Eventual  Consist
              Consist
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **SQL = Library Catalog 📚**
- Books organized by Dewey Decimal (structured)
- Card catalog = indexes
- Librarian knows where everything is (query optimizer)
- Rules enforced (can't check out if fines owed = foreign keys)

### 2. **NoSQL = Personal Bookshelf 📖**
- Organize however you want (flexible schema)
- Book + notes together (denormalized)
- Fast to grab (no catalog lookup)
- But: duplicates across shelves, hard to reorganize

### 3. **ACID = Bank Vault 🏦**
- Transaction = either fully complete or not at all
- No partial withdrawals
- Lock during transaction
- Audit trail (durability)

### 4. **Eventual Consistency = Email 📧**
- Send email → not instant everywhere
- Eventually propagates to all servers
- Temporary inconsistency OK
- Fast, available

### 5. **CAP Theorem = Speed-Cost-Quality ⚖️**
- "Pick any two"
- Can't have perfect consistency + availability + partition tolerance
- Partition tolerance = must-have (network fails)
- Choose: CP (consistent) or AP (available)

### 6. **Polyglot Persistence = Tool Belt 🔧**
- Hammer for nails (SQL for transactions)
- Screwdriver for screws (NoSQL for scale)
- Don't use hammer for everything
- Right tool for right job

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of SQL vs NoSQL:**

1. **Start with SQL, Migrate to NoSQL if Needed**
   - SQL is safer default (ACID, mature tooling)
   - Premature NoSQL = premature optimization
   - Migrate when: scale hits limits OR schema too rigid

2. **Use Both (Polyglot Persistence)**
   - Modern systems use 3-5 databases
   - Each for specific use case
   - Don't force one database for everything

3. **Consistency is a Business Decision**
   - Financial data: strong consistency (SQL or CP NoSQL)
   - Social media likes: eventual consistency (AP NoSQL)
   - Let business requirements drive choice

---

## 🏗️ ARCHITECT'S CHECKLIST

Before choosing database:

- [ ] **Transactions**: Need ACID? (Yes → SQL or NewSQL)
- [ ] **Queries**: Complex JOINs? (Yes → SQL)
- [ ] **Scale**: Expected users? (>100M → consider NoSQL)
- [ ] **Schema**: Changes frequently? (Yes → NoSQL document)
- [ ] **Consistency**: Strong or eventual? (Strong → SQL or CP)
- [ ] **Access Pattern**: Key-value, document, graph? (Matches NoSQL type)
- [ ] **Team Expertise**: Familiar with which database?
- [ ] **Ecosystem**: Tools, libraries available?
- [ ] **Cost**: Licensing, hosting, operations?
- [ ] **Migration Path**: Can migrate later if needed?

---

## 📊 COMPARISON TABLE

| Feature | SQL | NoSQL Document | NoSQL K-V | NoSQL Column | NoSQL Graph |
|---------|-----|----------------|-----------|--------------|-------------|
| **ACID** | ✓ | Partial | ✗ | ✗ | Partial |
| **Joins** | ✓ | ✗ | ✗ | ✗ | ✓ (graph) |
| **Schema** | Rigid | Flexible | None | Flexible | Flexible |
| **Scale** | Vertical | Horizontal | Horizontal | Horizontal | Vertical* |
| **Consistency** | Strong | Tunable | Eventual | Tunable | Strong |
| **Query Complexity** | High | Medium | Low | Low | High (graph) |
| **Examples** | PostgreSQL, MySQL | MongoDB, Couch | Redis, Dynamo | Cassandra, HBase | Neo4j, Neptune |
| **Best For** | Transactions, analytics | Flexible docs | Cache, session | Time-series, logs | Relationships |

---

**Interview Closer**: "I'd start with PostgreSQL for most applications—it's versatile, ACID-compliant, and has great tooling. If we anticipate massive scale (>100M users) or need flexible schema, I'd consider MongoDB. For specific use cases, polyglot persistence: Redis for caching, Elasticsearch for search. The key is matching database to access pattern, not blindly following trends."
