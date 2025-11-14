# SQL vs NoSQL

## SQL (Relational Databases)

### Characteristics

**Structure**: Tables with fixed schema
**Relationships**: Foreign keys, JOINs
**Transactions**: ACID guarantees
**Query Language**: SQL (standardized)

### When to Use SQL

✓ **Complex Relationships**:
```sql
-- Multi-table joins easy
SELECT users.name, orders.total, items.product_name
FROM users
JOIN orders ON users.id = orders.user_id
JOIN items ON orders.id = items.order_id
WHERE users.region = 'US'
```

✓ **ACID Transactions Required**:
```sql
BEGIN TRANSACTION;
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

✓ **Strong Consistency**:
- Banking systems
- Inventory management
- Reservation systems

✓ **Complex Queries**:
- Analytics
- Reporting
- Ad-hoc queries

✓ **Well-Defined Schema**:
- Stable data model
- Data integrity important

### SQL Databases

**PostgreSQL**:
- Most feature-rich
- JSON support (hybrid)
- Extensions (PostGIS, etc.)
- Strong ACID

**MySQL**:
- Widely used
- Good performance
- Replication built-in
- Large ecosystem

**Oracle**:
- Enterprise features
- High performance
- Expensive
- Complex operations

### SQL Scaling Limitations

**Vertical Scaling**:
- Limited by hardware
- Expensive

**Horizontal Scaling (Sharding)**:
- Complex to implement
- JOINs across shards difficult
- Distributed transactions hard

---

## NoSQL (Non-Relational Databases)

### Types

### 1. Document Databases

**MongoDB, CouchDB, Firebase**

**Structure**: JSON-like documents
```json
{
  "_id": "123",
  "name": "Alice",
  "email": "alice@example.com",
  "orders": [
    {"id": "order1", "total": 50.00},
    {"id": "order2", "total": 75.00}
  ]
}
```

**When to Use**:
- Flexible schema
- Hierarchical data
- Denormalized data
- Rapid development

**Example Use Cases**:
- Content management
- User profiles
- Product catalogs
- Mobile apps

### 2. Key-Value Stores

**Redis, DynamoDB, Memcached**

**Structure**: Simple key → value
```python
cache.set("user:123", {"name": "Alice", "email": "..."})
value = cache.get("user:123")
```

**When to Use**:
- Simple access patterns (get/put)
- High performance needed
- Caching
- Session storage

**Example Use Cases**:
- Session management
- Caching layer
- Real-time analytics
- Leaderboards

### 3. Wide-Column Stores

**Cassandra, HBase, Bigtable**

**Structure**: Column families
```
Row Key: user_123
  Column Family: profile
    name: "Alice"
    email: "alice@example.com"
  Column Family: activity
    last_login: "2025-11-14"
    login_count: 42
```

**When to Use**:
- Time-series data
- High write throughput
- Sparse data
- Massive scale

**Example Use Cases**:
- IoT data
- Time-series metrics
- Activity logs
- Event tracking

### 4. Graph Databases

**Neo4j, Neptune, ArangoDB**

**Structure**: Nodes and edges
```cypher
(Alice:User)-[:FOLLOWS]->(Bob:User)
(Alice)-[:LIKES]->(Post1:Post)
```

**When to Use**:
- Relationship-focused queries
- Network analysis
- Pattern matching

**Example Use Cases**:
- Social networks
- Fraud detection
- Recommendation engines
- Knowledge graphs

---

## Decision Framework

### Choose SQL When:

| Requirement | Why SQL |
|-------------|---------|
| Complex relationships | JOINs are natural |
| ACID transactions | Built-in support |
| Data integrity | Constraints, foreign keys |
| Ad-hoc queries | Flexible SQL |
| Mature tooling | Wide ecosystem |
| Moderate scale | < 10TB, < 10K QPS |

### Choose NoSQL When:

| Requirement | Why NoSQL |
|-------------|-----------|
| Massive scale | Horizontal scaling |
| Flexible schema | Schema evolution |
| High throughput | Optimized for specific patterns |
| Geographic distribution | Built for multi-datacenter |
| Specific access patterns | Optimized data structures |

---

## Hybrid Approaches

### Polyglot Persistence

**Use multiple database types in one system:**

```
E-Commerce Application:

PostgreSQL (SQL):
- User accounts (transactions)
- Orders (ACID required)
- Payments (consistency critical)

MongoDB (Document):
- Product catalog (flexible schema)
- Reviews (nested documents)

Redis (Key-Value):
- Session storage
- Shopping cart (temporary)
- Cache layer

Elasticsearch (Search):
- Product search
- Full-text queries

Cassandra (Wide-Column):
- User activity logs
- Analytics events
```

### SQL with NoSQL Features

**PostgreSQL JSONB**:
```sql
CREATE TABLE products (
  id SERIAL PRIMARY KEY,
  name VARCHAR(255),
  attributes JSONB  -- Flexible schema
);

SELECT * FROM products
WHERE attributes->>'color' = 'red';
```

Benefits:
- ACID transactions + flexible schema
- Best of both worlds
- Single database to manage

---

## Common Misconceptions

### Myth 1: "NoSQL is always faster"

**Reality**: Depends on workload
- SQL fast for complex queries
- NoSQL fast for simple key-value
- Both can be optimized

### Myth 2: "NoSQL means no schema"

**Reality**: Schema still exists, just flexible
- MongoDB: Schema-on-read
- SQL: Schema-on-write
- Both need data modeling

### Myth 3: "NoSQL doesn't support transactions"

**Reality**: Many support transactions
- MongoDB: Multi-document ACID
- DynamoDB: Transactions
- Cassandra: Lightweight transactions

### Myth 4: "SQL can't scale"

**Reality**: SQL can scale
- YouTube uses MySQL (sharded)
- Facebook uses MySQL (modified)
- Requires effort, but possible

---

## Interview Q&A

**Q: "How would you choose between SQL and NoSQL for a new application?"**

**Answer**:
```
I'd ask these questions:

1. Data Relationships:
   - Complex relationships → SQL (easier JOINs)
   - Flat/hierarchical → NoSQL (documents)
   - Graph-heavy → Graph DB

2. Consistency Requirements:
   - Strong consistency → SQL (ACID)
   - Eventual consistency okay → NoSQL

3. Query Patterns:
   - Ad-hoc, complex → SQL
   - Known, simple → NoSQL (optimize for patterns)

4. Scale Requirements:
   - <10TB, <10K QPS → SQL fine
   - >10TB, >10K QPS → Consider NoSQL

5. Schema Stability:
   - Stable schema → SQL (enforce integrity)
   - Evolving schema → NoSQL (flexibility)

Example: E-commerce
- Orders/Payments → PostgreSQL (transactions)
- Product Catalog → MongoDB (flexible)
- Session Data → Redis (fast access)

Don't limit to one database type!
```

**Q: "Design data storage for Twitter"**

**Answer**:
```
Hybrid Approach:

MySQL (SQL) - User Data:
- User profiles (id, username, email)
- Relationships (follows table)
- Requires transactions, consistent

Cassandra (Wide-Column) - Tweets:
- Tweet storage (high write volume)
- Timeline data (time-series)
- Optimized for reads by user_id

Redis (Key-Value) - Caching:
- Timeline cache
- Trending topics
- Real-time counters

Rationale:
- User data: Relational, ACID needed
- Tweets: Massive scale, time-series, eventual consistency okay
- Cache: Performance critical, temporary data

Scaling:
- MySQL: Sharded by user_id
- Cassandra: Partitioned by user_id + timestamp
- Redis: Cluster mode for capacity
```

---

## Key Takeaways

1. **Not SQL vs NoSQL**: Often SQL AND NoSQL
2. **Requirements Drive Choice**: Let use case determine database
3. **Scaling Strategies Differ**: SQL harder to scale horizontally
4. **Transactions**: SQL easier for ACID, NoSQL for scale
5. **Schema**: SQL rigid (integrity), NoSQL flexible (evolution)

## Further Reading

- "Designing Data-Intensive Applications" - Chapters 2-3
- MongoDB Use Cases
- DynamoDB Developer Guide
- PostgreSQL Documentation
- "Seven Databases in Seven Weeks"
