# Data Modeling

Effective data modeling is crucial for building scalable, maintainable, and performant systems.

## Contents
- [Database Design Principles](#database-design-principles)
- [Normalization](#normalization)
- [Denormalization](#denormalization)
- [Schema Design Patterns](#schema-design-patterns)
- [NoSQL Data Modeling](#nosql-data-modeling)
- [Best Practices](#best-practices)

## Database Design Principles

### Entity-Relationship Modeling

**Identify core entities and their relationships:**

```
┌─────────────┐         ┌─────────────┐
│   Users     │         │   Orders    │
├─────────────┤         ├─────────────┤
│ id (PK)     │────┐    │ id (PK)     │
│ name        │    │    │ user_id (FK)│
│ email       │    └───<│ total       │
│ created_at  │         │ created_at  │
└─────────────┘         └─────────────┘
                              │
                              │ 1:N
                              ▼
                        ┌─────────────┐
                        │ OrderItems  │
                        ├─────────────┤
                        │ id (PK)     │
                        │ order_id(FK)│
                        │ product_id  │
                        │ quantity    │
                        │ price       │
                        └─────────────┘
```

### Relationship Types

**One-to-One (1:1)**
```sql
-- User has one profile
users (id, email)
user_profiles (user_id PK, bio, avatar)
```

**One-to-Many (1:N)**
```sql
-- User has many orders
users (id, email)
orders (id, user_id, total)
```

**Many-to-Many (M:N)**
```sql
-- Students enrolled in courses
students (id, name)
courses (id, title)
enrollments (student_id, course_id, enrolled_at)
```

## Normalization

Organizing data to reduce redundancy and improve integrity.

### First Normal Form (1NF)

**Atomic values** - no repeating groups or arrays.

**❌ Violates 1NF:**
```sql
orders
┌────┬─────────┬────────────────────────┐
│ id │ user_id │ products               │
├────┼─────────┼────────────────────────┤
│ 1  │ 101     │ iPhone, MacBook, iPad  │
└────┴─────────┴────────────────────────┘
```

**✅ Follows 1NF:**
```sql
order_items
┌──────────┬─────────────┐
│ order_id │ product     │
├──────────┼─────────────┤
│ 1        │ iPhone      │
│ 1        │ MacBook     │
│ 1        │ iPad        │
└──────────┴─────────────┘
```

### Second Normal Form (2NF)

**No partial dependencies** - non-key attributes depend on entire primary key.

**❌ Violates 2NF:**
```sql
order_items (order_id, product_id, product_name, quantity, price)
-- product_name depends only on product_id, not full PK (order_id + product_id)
```

**✅ Follows 2NF:**
```sql
products (product_id, product_name, unit_price)
order_items (order_id, product_id, quantity, price_at_purchase)
```

### Third Normal Form (3NF)

**No transitive dependencies** - non-key attributes depend only on primary key.

**❌ Violates 3NF:**
```sql
orders (id, user_id, user_email, user_city, total)
-- user_email and user_city depend on user_id, not order id
```

**✅ Follows 3NF:**
```sql
users (id, email, city)
orders (id, user_id, total)
```

### Boyce-Codd Normal Form (BCNF)

**Stronger version of 3NF** - every determinant is a candidate key.

**Example:**
```sql
-- Course taught by one professor per semester
course_schedule (course_id, semester, professor_id, professor_office)
-- professor_office depends on professor_id (not candidate key)

-- BCNF Solution:
professors (professor_id, office)
course_schedule (course_id, semester, professor_id)
```

### Benefits of Normalization

✅ **Eliminates redundancy**
✅ **Prevents update anomalies**
✅ **Improves data integrity**
✅ **Saves storage space**
✅ **Easier to maintain**

### Drawbacks of Normalization

❌ **More JOINs required**
❌ **Slower read queries**
❌ **Complex queries**
❌ **More tables to manage**

## Denormalization

**Intentionally introducing redundancy** to improve read performance.

### When to Denormalize

✅ **Read-heavy workloads** (10:1 or higher read/write ratio)
✅ **Expensive JOINs** causing performance issues
✅ **Reporting and analytics** (aggregated data)
✅ **Caching layer** (materialized views)

### Denormalization Patterns

#### 1. Duplicate Data

**Store frequently accessed data together:**

```sql
-- Normalized
users (id, name, email)
orders (id, user_id, total)

-- Denormalized (store user_name in orders)
orders (id, user_id, user_name, user_email, total)
```

**Trade-off:**
- ✅ Avoid JOIN for common queries
- ❌ Update multiple places when user changes name

#### 2. Precomputed Aggregates

**Store computed values:**

```sql
-- Computed on-the-fly (slow)
SELECT user_id, COUNT(*) as order_count, SUM(total) as lifetime_value
FROM orders
GROUP BY user_id;

-- Denormalized (fast)
users (id, name, order_count, lifetime_value)
-- Updated by triggers or batch jobs
```

#### 3. Materialized Views

**Precomputed query results:**

```sql
-- PostgreSQL materialized view
CREATE MATERIALIZED VIEW user_stats AS
SELECT
    u.id,
    u.name,
    COUNT(o.id) as order_count,
    SUM(o.total) as total_spent,
    MAX(o.created_at) as last_order_date
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
GROUP BY u.id, u.name;

-- Refresh periodically
REFRESH MATERIALIZED VIEW user_stats;
```

#### 4. Embedded Documents (NoSQL)

**Nest related data:**

```javascript
// MongoDB - embed order items
{
  _id: "order-123",
  user_id: 456,
  total: 299.99,
  items: [
    { product_id: 1, name: "iPhone", quantity: 1, price: 999 },
    { product_id: 2, name: "Case", quantity: 1, price: 29 }
  ]
}
```

### Maintaining Consistency

**Strategies for denormalized data:**

**1. Application Logic:**
```javascript
// Update both places
async function updateUserName(userId, newName) {
  await db.users.update(userId, { name: newName });
  await db.orders.updateMany(
    { user_id: userId },
    { user_name: newName }
  );
}
```

**2. Database Triggers:**
```sql
CREATE TRIGGER update_user_stats
AFTER INSERT ON orders
FOR EACH ROW
BEGIN
  UPDATE users
  SET order_count = order_count + 1,
      lifetime_value = lifetime_value + NEW.total
  WHERE id = NEW.user_id;
END;
```

**3. Batch Jobs:**
```javascript
// Nightly reconciliation
async function reconcileUserStats() {
  const stats = await db.orders.aggregate([
    { $group: {
      _id: "$user_id",
      count: { $sum: 1 },
      total: { $sum: "$total" }
    }}
  ]);

  for (const stat of stats) {
    await db.users.update(stat._id, {
      order_count: stat.count,
      lifetime_value: stat.total
    });
  }
}
```

**4. Event Sourcing:**
```javascript
// Publish events, subscribers update denormalized views
eventBus.publish('OrderPlaced', { userId, orderId, total });

// Subscriber updates user stats
eventBus.subscribe('OrderPlaced', async (event) => {
  await updateUserStats(event.userId);
});
```

## Schema Design Patterns

### 1. Polymorphic Associations

**Single table references multiple types:**

```sql
-- Comments can be on posts or photos
comments (
  id,
  commentable_type VARCHAR,  -- 'Post' or 'Photo'
  commentable_id INT,
  text
)

posts (id, title, content)
photos (id, url, caption)
```

**Challenges:**
- No foreign key constraints
- Difficult to maintain integrity

**Better Alternative: Junction Tables**

```sql
post_comments (id, post_id, text)
photo_comments (id, photo_id, text)
```

### 2. Adjacency List (Hierarchies)

**Self-referencing table for tree structures:**

```sql
categories (
  id,
  name,
  parent_id  -- References categories.id
)

-- Data
┌────┬─────────────┬───────────┐
│ id │ name        │ parent_id │
├────┼─────────────┼───────────┤
│ 1  │ Electronics │ NULL      │
│ 2  │ Computers   │ 1         │
│ 3  │ Phones      │ 1         │
│ 4  │ Laptops     │ 2         │
└────┴─────────────┴───────────┘
```

**Pros:** Simple, easy updates
**Cons:** Inefficient for deep trees (recursive queries)

### 3. Nested Sets

**Efficient tree traversal:**

```sql
categories (
  id,
  name,
  lft INT,  -- Left boundary
  rgt INT   -- Right boundary
)

-- Tree structure encoded in lft/rgt
┌────┬─────────────┬─────┬─────┐
│ id │ name        │ lft │ rgt │
├────┼─────────────┼─────┼─────┤
│ 1  │ Electronics │ 1   │ 8   │
│ 2  │ Computers   │ 2   │ 5   │
│ 4  │ Laptops     │ 3   │ 4   │
│ 3  │ Phones      │ 6   │ 7   │
└────┴─────────────┴─────┴─────┘

-- Get all descendants: WHERE lft BETWEEN 1 AND 8
```

**Pros:** Fast reads, subtree queries
**Cons:** Slow inserts/updates (renumber nodes)

### 4. Closure Table

**Store all ancestor-descendant pairs:**

```sql
categories (id, name)
category_paths (
  ancestor_id,
  descendant_id,
  depth  -- 0 for self-reference
)

-- Data
┌─────────────┬──────────────┬───────┐
│ ancestor_id │ descendant_id│ depth │
├─────────────┼──────────────┼───────┤
│ 1           │ 1            │ 0     │
│ 1           │ 2            │ 1     │
│ 1           │ 4            │ 2     │
│ 2           │ 2            │ 0     │
│ 2           │ 4            │ 1     │
│ 4           │ 4            │ 0     │
└─────────────┴──────────────┴───────┘
```

**Pros:** Fast reads, easy to query any relationship
**Cons:** More storage, inserts more complex

### 5. Temporal Data (Versioning)

**Track changes over time:**

```sql
-- Effective dating
products (
  id,
  name,
  price,
  valid_from TIMESTAMP,
  valid_to TIMESTAMP
)

-- Query current price
SELECT * FROM products
WHERE product_id = 1
  AND CURRENT_TIMESTAMP BETWEEN valid_from AND valid_to;

-- Query historical price
SELECT * FROM products
WHERE product_id = 1
  AND '2023-01-15' BETWEEN valid_from AND valid_to;
```

**Alternative: Separate History Table**

```sql
products (id, name, current_price)
product_price_history (
  product_id,
  price,
  changed_at,
  changed_by
)
```

### 6. Soft Deletes

**Mark records as deleted instead of removing:**

```sql
users (
  id,
  email,
  deleted_at TIMESTAMP NULL
)

-- Active users
SELECT * FROM users WHERE deleted_at IS NULL;

-- Include deleted
SELECT * FROM users;

-- Restore
UPDATE users SET deleted_at = NULL WHERE id = 123;
```

**Pros:** Audit trail, easy recovery
**Cons:** Complicates queries, indices

### 7. Multi-Tenancy Patterns

**Pattern 1: Shared Schema (Single Database)**

```sql
-- All tenants share tables
users (id, tenant_id, email)
orders (id, tenant_id, user_id, total)

-- Always filter by tenant
SELECT * FROM orders WHERE tenant_id = 123;
```

**Pros:** Cost-effective, easy to maintain
**Cons:** Risk of data leakage, noisy neighbors

**Pattern 2: Separate Schemas (Same Database)**

```sql
-- Each tenant has own schema
tenant_123.users (id, email)
tenant_123.orders (id, user_id, total)

tenant_456.users (id, email)
tenant_456.orders (id, user_id, total)
```

**Pros:** Better isolation, easier backups per tenant
**Cons:** Schema migrations complex

**Pattern 3: Separate Databases**

```
Database: tenant_123 → users, orders
Database: tenant_456 → users, orders
```

**Pros:** Complete isolation, independent scaling
**Cons:** Expensive, complex management

## NoSQL Data Modeling

### Document Databases (MongoDB, CouchDB)

**Design for access patterns, not normalization.**

#### Embedding vs Referencing

**Embed when:**
- 1:1 or 1:few relationships
- Data accessed together
- Infrequent updates
- Bounded array size

```javascript
// Embedded
{
  _id: "user-123",
  name: "John Doe",
  address: {
    street: "123 Main St",
    city: "Boston",
    zip: "02101"
  }
}
```

**Reference when:**
- 1:many or many:many
- Data accessed independently
- Frequent updates
- Unbounded growth

```javascript
// Referenced
{
  _id: "user-123",
  name: "John Doe",
  address_id: "addr-456"
}

{
  _id: "addr-456",
  street: "123 Main St",
  city: "Boston"
}
```

#### MongoDB Patterns

**1. Computed Pattern**
```javascript
// Store precomputed values
{
  _id: "product-123",
  name: "Laptop",
  reviews: [...],
  review_count: 245,
  avg_rating: 4.5  // Precomputed
}
```

**2. Bucket Pattern**
```javascript
// Group time-series data
{
  sensor_id: "sensor-1",
  date: "2024-01-15",
  readings: [
    { time: "00:00", temp: 72 },
    { time: "00:05", temp: 73 },
    // ... 288 readings per day
  ]
}
```

**3. Polymorphic Pattern**
```javascript
// Different document types in same collection
{ type: "book", title: "...", author: "..." }
{ type: "movie", title: "...", director: "..." }
{ type: "game", title: "...", platform: "..." }
```

### Key-Value Stores (Redis, DynamoDB)

**Design around keys:**

```
# Simple key-value
user:123:profile → { name: "John", email: "..." }

# Hash for structured data
HSET user:123 name "John" email "john@example.com"

# Lists for queues
LPUSH queue:emails "email@example.com"
RPOP queue:emails

# Sorted sets for rankings
ZADD leaderboard 9500 "user:123"
ZRANGE leaderboard 0 9 WITHSCORES  # Top 10
```

### Wide-Column Stores (Cassandra, HBase)

**Design for queries (query-first design):**

```sql
-- Time-series table
CREATE TABLE sensor_data (
  sensor_id UUID,
  year INT,
  month INT,
  timestamp TIMESTAMP,
  temperature FLOAT,
  PRIMARY KEY ((sensor_id, year, month), timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);

-- Query pattern
SELECT * FROM sensor_data
WHERE sensor_id = ? AND year = 2024 AND month = 1
  AND timestamp > '2024-01-15';
```

**Denormalization is mandatory** - design separate tables for each query pattern.

## Best Practices

### 1. Design for Access Patterns

**Start with queries, then design schema:**

```
1. List queries needed
   - Get user profile
   - Get user's orders
   - Get order items

2. Design schema to support queries efficiently
3. Minimize JOINs for hot paths
```

### 2. Choose Appropriate Keys

**Primary Key:**
- Unique identifier
- Immutable
- Meaningful or surrogate (auto-increment, UUID)

**Foreign Key:**
- Maintain referential integrity
- Index for JOIN performance

**Natural vs Surrogate:**
```sql
-- Natural key (email)
users (email PRIMARY KEY, name)
-- Pros: Meaningful, Cons: Can change

-- Surrogate key (id)
users (id PRIMARY KEY, email UNIQUE, name)
-- Pros: Stable, Cons: Extra column
```

### 3. Plan for Growth

**Consider scalability:**

```sql
-- Bad: Will grow unbounded
users (
  id,
  order_history TEXT  -- JSON array of all orders
)

-- Good: Separate table
orders (
  id,
  user_id,
  created_at
)
```

### 4. Index Strategically

**Index foreign keys and query filters:**

```sql
CREATE INDEX idx_orders_user_id ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created_at ON orders(created_at);
```

### 5. Use Constraints

**Enforce integrity at database level:**

```sql
CREATE TABLE orders (
  id SERIAL PRIMARY KEY,
  user_id INT NOT NULL REFERENCES users(id),
  total DECIMAL(10,2) CHECK (total >= 0),
  status VARCHAR(20) CHECK (status IN ('pending', 'paid', 'shipped')),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

### 6. Version Your Schema

**Track schema changes:**

```sql
-- migrations/001_create_users.sql
CREATE TABLE users (id, email, created_at);

-- migrations/002_add_user_name.sql
ALTER TABLE users ADD COLUMN name VARCHAR(100);
```

**Tools:** Flyway, Liquibase, Alembic (Python), Knex (Node.js)

## Common Mistakes

❌ **Over-normalization** → Too many JOINs, slow queries
❌ **Under-normalization** → Data redundancy, update anomalies
❌ **Wrong data types** → VARCHAR(255) for everything
❌ **No indexes** → Full table scans
❌ **Too many indexes** → Slow writes
❌ **No constraints** → Data integrity issues
❌ **God tables** → One table with 100+ columns
❌ **Ignoring NULL semantics** → Unexpected query results

## Further Reading

- [Database Design for Mere Mortals](https://www.amazon.com/Database-Design-Mere-Mortals-Hands/dp/0321884493)
- [MongoDB Data Modeling](https://www.mongodb.com/docs/manual/core/data-modeling-introduction/)
- [PostgreSQL Schema Design](https://www.postgresql.org/docs/current/ddl.html)
- [SQL Antipatterns](https://pragprog.com/titles/bksqla/sql-antipatterns/)
- [DynamoDB Best Practices](https://docs.aws.amazon.com/amazondynamodb/latest/developerguide/best-practices.html)
