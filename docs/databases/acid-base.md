# ACID vs BASE

Understanding transaction models is crucial for choosing the right database and designing reliable systems.

## Contents
- [ACID Properties](#acid-properties)
- [BASE Properties](#base-properties)
- [ACID vs BASE Comparison](#acid-vs-base-comparison)
- [Transaction Isolation Levels](#transaction-isolation-levels)
- [Choosing Between ACID and BASE](#choosing-between-acid-and-base)
- [Real-World Examples](#real-world-examples)

## ACID Properties

ACID guarantees **strong consistency** and **reliability** in transactions.

### A - Atomicity

**All-or-nothing**: Transaction either completes fully or not at all.

```sql
BEGIN TRANSACTION;
    UPDATE accounts SET balance = balance - 100 WHERE id = 1;
    UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT;
```

**If any operation fails:**
- Entire transaction rolls back
- Database returns to previous state
- No partial updates

**Example:**
```
Transfer $100 from Account A to Account B:
✅ Debit A: $1000 → $900
✅ Credit B: $500 → $600
= Transaction commits

❌ Debit A: $1000 → $900
❌ Credit B fails (insufficient memory)
= Transaction rolls back, A returns to $1000
```

**Implementation:**
- Write-Ahead Logging (WAL)
- Transaction logs
- Undo/Redo logs

### C - Consistency

**Valid state to valid state**: Database remains in consistent state.

**Enforces:**
- Constraints (PRIMARY KEY, FOREIGN KEY, UNIQUE)
- Data types
- Triggers
- Custom business rules

**Example:**

```sql
-- Constraint: balance >= 0
UPDATE accounts SET balance = balance - 100 WHERE id = 1;

-- If balance would become negative, transaction fails
-- Maintains invariant: balance >= 0
```

**Consistency Rules:**
```
Before transaction: Sum(all balances) = $10,000
After transaction:  Sum(all balances) = $10,000
```

### I - Isolation

**Concurrent transactions don't interfere** with each other.

**Without Isolation:**
```
Time  Transaction 1              Transaction 2
----  -------------------------  -----------------------
T1    READ balance (= $100)
T2                               READ balance (= $100)
T3    UPDATE balance = $150
T4                               UPDATE balance = $200
T5    COMMIT
T6                               COMMIT

Result: Balance = $200 (lost update from T1!)
```

**With Isolation:**
```
Time  Transaction 1              Transaction 2
----  -------------------------  -----------------------
T1    READ balance (= $100)
T2                               WAIT (locked)
T3    UPDATE balance = $150
T4                               WAIT
T5    COMMIT
T6                               READ balance (= $150)
T7                               UPDATE balance = $200
T8                               COMMIT

Result: Balance = $200 (both updates applied correctly)
```

**Isolation Levels:** See [Transaction Isolation Levels](#transaction-isolation-levels)

### D - Durability

**Committed data survives failures** (crash, power loss, etc.)

**Guarantees:**
- Committed transactions persist
- Data survives system crashes
- No data loss after commit

**Implementation:**
```
1. Write transaction to log (on disk)
2. Flush log to persistent storage
3. Acknowledge commit to client
4. Apply changes to database (can be async)
```

**Example:**

```
T1: BEGIN
T2: UPDATE accounts SET balance = 1000
T3: COMMIT → Written to disk
T4: SYSTEM CRASH
T5: Recovery: Replay transaction log
Result: Update persists
```

**Technologies:**
- Write-Ahead Logging (WAL)
- fsync() system call
- Battery-backed cache
- Replication

## BASE Properties

BASE favors **availability and performance** over strong consistency.

### BA - Basically Available

**System remains operational** despite failures.

**Characteristics:**
- Partial failures tolerated
- Graceful degradation
- Not all data always available
- Eventual response

**Example:**
```
E-commerce site:
✅ Product browsing works (cached data)
✅ Search works (slightly stale index)
⚠️  Checkout may be slow (high load)
⚠️  Inventory might be approximate
```

### S - Soft State

**State may change without input** due to eventual consistency.

**Characteristics:**
- Data may be in flux
- Background reconciliation
- No strong guarantees at any moment
- State converges over time

**Example:**
```
Social media likes count:
User A sees: 1,234 likes
User B sees: 1,237 likes (few seconds later)
System reconciling... eventually both see same count
```

### E - Eventual Consistency

**System becomes consistent over time** if no new updates.

**Guarantees:**
- All replicas eventually agree
- No guarantee when
- Reads may return stale data

**Example:**

```
Time    US Database    EU Database    Asia Database
----    ------------   ------------   --------------
T1      User: John     User: John     User: John
T2      Update → Jane  User: John     User: John
T3      User: Jane     User: John     User: John
T4      User: Jane     Update → Jane  User: John
T5      User: Jane     User: Jane     Update → Jane
T6      User: Jane     User: Jane     User: Jane ✅
```

**Convergence Time:**
- Milliseconds to seconds (typically)
- Can be minutes in extreme cases
- Network partition may delay

## ACID vs BASE Comparison

| Aspect | ACID | BASE |
|--------|------|------|
| **Consistency** | Strong (immediate) | Weak (eventual) |
| **Availability** | Lower (during failures) | Higher (always on) |
| **Partition Tolerance** | Lower | Higher |
| **Performance** | Lower (coordination overhead) | Higher (no locks) |
| **Complexity** | Lower (DB handles it) | Higher (app handles it) |
| **Use Cases** | Financial, critical data | Social media, analytics |
| **Example DBs** | PostgreSQL, MySQL | Cassandra, DynamoDB |

### CAP Theorem Connection

**CAP Theorem:** Can only have 2 of 3:
- **C**onsistency: All nodes see same data
- **A**vailability: Every request gets response
- **P**artition tolerance: System works despite network splits

**ACID:** Chooses **C** and **P** (sacrifices A during partitions)
**BASE:** Chooses **A** and **P** (sacrifices strong C)

```
         CAP Theorem
            /\
           /  \
          /    \
         /  AP  \     (BASE systems)
        /________\
       /\   CP   /\   (ACID systems)
      /  \      /  \
     /    \    /    \
    /  CA  \  /      \
   /________\/________\
  (Not partition-tolerant)
```

## Transaction Isolation Levels

SQL standard defines 4 isolation levels (weakest to strongest):

### 1. Read Uncommitted

**Lowest isolation** - can read uncommitted changes.

**Allows:**
- Dirty reads ✗

**Example:**
```
T1: BEGIN
T2: UPDATE accounts SET balance = 1000 WHERE id = 1
    (not committed)
T3:                                        SELECT balance FROM accounts WHERE id = 1
                                          → Returns 1000 (uncommitted!)
T4: ROLLBACK (oops, error occurred)
T5:                                        Data is now invalid
```

**Use Cases:**
- Approximate analytics
- Not production-critical
- Rarely used in practice

### 2. Read Committed

**Most common default** - only read committed data.

**Prevents:**
- Dirty reads ✓

**Allows:**
- Non-repeatable reads ✗
- Phantom reads ✗

**Example (Non-repeatable read):**
```
T1: BEGIN
T2: SELECT balance FROM accounts WHERE id = 1
    → Returns $100
T3:                                        UPDATE accounts SET balance = 200 WHERE id = 1
T4:                                        COMMIT
T5: SELECT balance FROM accounts WHERE id = 1
    → Returns $200 (different from T2!)
```

**Use Cases:**
- Default for PostgreSQL, Oracle, SQL Server
- Good balance for most apps

### 3. Repeatable Read

**Stronger guarantee** - consistent reads within transaction.

**Prevents:**
- Dirty reads ✓
- Non-repeatable reads ✓

**Allows:**
- Phantom reads ✗ (in some implementations)

**Example:**
```
T1: BEGIN
T2: SELECT * FROM accounts WHERE balance > 100
    → Returns 5 rows
T3:                                        INSERT INTO accounts VALUES (6, 150)
T4:                                        COMMIT
T5: SELECT * FROM accounts WHERE balance > 100
    → Returns 5 rows (same as T2, no phantom)
```

**MySQL InnoDB:** Prevents phantom reads (uses MVCC)
**PostgreSQL:** Repeatable Read is default

### 4. Serializable

**Strongest isolation** - as if transactions ran serially.

**Prevents:**
- Dirty reads ✓
- Non-repeatable reads ✓
- Phantom reads ✓

**Guarantees:**
- Same result as sequential execution
- Complete isolation

**Cost:**
- Significant performance overhead
- More lock contention
- Possible transaction aborts

**Implementation:**
- Pessimistic: Locks
- Optimistic: Snapshot isolation + validation

**Use Cases:**
- Financial transactions
- Critical operations
- When correctness > performance

### Isolation Level Summary

| Level | Dirty Read | Non-Repeatable Read | Phantom Read | Performance |
|-------|------------|---------------------|--------------|-------------|
| Read Uncommitted | ✗ | ✗ | ✗ | Highest |
| Read Committed | ✓ | ✗ | ✗ | High |
| Repeatable Read | ✓ | ✓ | ✗ | Medium |
| Serializable | ✓ | ✓ | ✓ | Lowest |

### Setting Isolation Level

```sql
-- PostgreSQL
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- MySQL
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;

-- SQL Server
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
```

## Choosing Between ACID and BASE

### Use ACID When:

✅ **Financial Transactions**
- Banking, payments
- Money transfers
- Billing systems

✅ **Inventory Management**
- Stock levels
- Order fulfillment
- Reservations

✅ **Critical Business Data**
- User accounts
- Compliance data
- Audit logs

✅ **Strong Consistency Required**
- Regulatory requirements
- Data integrity critical
- Sequential operations

### Use BASE When:

✅ **High Availability Priority**
- 24/7 uptime required
- Global distribution
- Can't afford downtime

✅ **Massive Scale**
- Millions of users
- High throughput
- Distributed systems

✅ **Eventual Consistency Acceptable**
- Social media feeds
- Analytics dashboards
- Recommendation engines
- View counts, likes

✅ **Performance Critical**
- Real-time systems
- Low latency required
- Read-heavy workloads

### Hybrid Approaches

Many modern systems use **both**:

**Example: E-Commerce**
```
ACID (PostgreSQL):
- Orders
- Payments
- Inventory critical data

BASE (Cassandra):
- Product views
- Search history
- Recommendations
- User activity logs
```

**Example: Social Media**
```
ACID (MySQL):
- User accounts
- Authentication
- Relationships

BASE (Cassandra):
- Timeline feeds
- Likes, comments
- Notifications
- Activity streams
```

## Real-World Examples

### ACID Systems

**PostgreSQL:**
```sql
BEGIN;
  UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  UPDATE accounts SET balance = balance + 100 WHERE id = 2;
COMMIT; -- Atomic, consistent, isolated, durable
```

**Use Case:** Banking application

### BASE Systems

**Cassandra:**
```sql
INSERT INTO user_timeline (user_id, post_id, timestamp)
VALUES (123, 'post-456', '2024-01-15 10:30:00');
-- Replicates to 3 nodes
-- Eventually consistent across data centers
```

**Use Case:** Twitter timeline

### Eventual Consistency Patterns

**Read Your Own Writes:**
```
Client writes to primary → redirect reads to primary
After replication lag → read from replicas
```

**Session Consistency:**
```
Same user session → same replica
Sees own writes
```

**Monotonic Reads:**
```
Once you see version N, never see version < N
```

## Common Pitfalls

### 1. Assuming Strong Consistency

```javascript
// ❌ Dangerous with eventual consistency
const count = await db.get('likes_count');
await db.set('likes_count', count + 1);
// Another client may have updated in between!

// ✅ Use atomic operations
await db.increment('likes_count', 1);
```

### 2. Ignoring Isolation Levels

```sql
-- ❌ Race condition at Read Committed
BEGIN;
  SELECT balance FROM accounts WHERE id = 1; -- $100
  -- Another transaction updates balance to $50
  UPDATE accounts SET balance = balance - 60; -- $40 (should fail!)
COMMIT;

-- ✅ Use Serializable for critical operations
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;
  SELECT balance FROM accounts WHERE id = 1;
  UPDATE accounts SET balance = balance - 60;
COMMIT;
```

### 3. Not Handling Conflicts

```javascript
// BASE system - handle conflicts
try {
  await replicatedDB.write(key, value);
} catch (ConflictError) {
  // Implement conflict resolution
  const merged = resolveConflict(localVersion, remoteVersion);
  await replicatedDB.write(key, merged);
}
```

## Best Practices

1. **Choose Based on Use Case:** Don't use ACID for everything or BASE for everything
2. **Understand Trade-offs:** Consistency vs availability vs performance
3. **Test Isolation Levels:** Verify behavior under concurrent load
4. **Monitor Replication Lag:** Alert on excessive lag in BASE systems
5. **Implement Idempotency:** Make operations safe to retry
6. **Use Appropriate Timeouts:** Prevent indefinite blocking
7. **Document Consistency Guarantees:** Make expectations clear to developers

## Further Reading

- [ACID Properties Explained](https://www.databricks.com/glossary/acid-transactions)
- [BASE: An Alternative to ACID](https://queue.acm.org/detail.cfm?id=1394128)
- [CAP Theorem](https://en.wikipedia.org/wiki/CAP_theorem)
- [Transaction Isolation Levels](https://www.postgresql.org/docs/current/transaction-iso.html)
- [Jepsen: Testing Distributed Systems](https://jepsen.io/)
