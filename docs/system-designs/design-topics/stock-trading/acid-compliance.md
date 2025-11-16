# ACID Compliance & Financial Constraints in Trading Systems

## 🎯 Why ACID is Non-Negotiable in Financial Systems

In a trading system, **every penny must be accounted for, every trade must be correct, and no data can be lost**. ACID properties are not optional—they are the foundation of financial integrity.

---

## 1. The ACID Properties Explained

### A - Atomicity: All or Nothing

**Definition**: A transaction is an indivisible unit of work. Either all operations succeed, or all fail.

**Trading Example**:
```
User buys 100 shares of AAPL @ $150:
1. Debit $15,050 from cash balance
2. Credit 100 shares to position
3. Record transaction in ledger
4. Update order status to FILLED

If ANY step fails → ALL steps are rolled back
```

**Without Atomicity** (Disaster Scenario):
```
Step 1 succeeds: Cash deducted ($15,050 gone)
Step 2 fails: Shares not credited
Result: User loses money, gets no shares! 😱
```

**Implementation**:
```sql
BEGIN TRANSACTION;
  UPDATE accounts SET balance = balance - 15050 WHERE user_id = 123;
  INSERT INTO positions VALUES (123, 'AAPL', 100, 150.00);
  INSERT INTO transactions VALUES (...);
  UPDATE orders SET status = 'FILLED' WHERE order_id = 'ORD-123';
COMMIT;

-- If ANY statement fails, ROLLBACK undoes everything
```

**Database Support**:
- ✅ PostgreSQL, MySQL (InnoDB), Oracle, SQL Server
- ✅ CockroachDB, Google Spanner
- ❌ MongoDB (multi-document transactions are limited)
- ❌ Cassandra, DynamoDB (no multi-row transactions)

---

### C - Consistency: Valid State Always

**Definition**: Transactions move the database from one valid state to another. Invariants are always maintained.

**Trading Invariants**:
```
1. Account Value = Cash + Sum(Position Values)
2. Total Cash (all accounts) = Constant (money doesn't appear/disappear)
3. Shares Bought = Shares Sold (zero-sum for each symbol)
4. Balance >= 0 (no negative cash unless margin allowed)
```

**Example: Preventing Overdraft**:
```sql
CREATE TABLE accounts (
    user_id BIGINT PRIMARY KEY,
    balance DECIMAL(15,2) NOT NULL,
    CONSTRAINT positive_balance CHECK (balance >= 0)
);

-- Attempt to withdraw more than available
UPDATE accounts SET balance = balance - 20000 WHERE user_id = 123;
-- If balance was $10,000 → CHECK constraint violation → ROLLBACK
```

**Example: Double-Entry Accounting**:
```sql
-- Every trade has a buyer and seller (zero-sum)
BEGIN TRANSACTION;
  -- Buyer: Debit cash, Credit shares
  UPDATE accounts SET balance = balance - 15000 WHERE user_id = 123;
  UPDATE positions SET quantity = quantity + 100 WHERE user_id = 123 AND symbol = 'AAPL';
  
  -- Seller: Credit cash, Debit shares
  UPDATE accounts SET balance = balance + 15000 WHERE user_id = 456;
  UPDATE positions SET quantity = quantity - 100 WHERE user_id = 456 AND symbol = 'AAPL';
COMMIT;

-- Sum of all cash changes = 0 (consistency maintained)
```

**Database Support**:
- ✅ All SQL databases (with CHECK constraints, foreign keys)
- ⚠️ NoSQL databases (application-enforced, not DB-enforced)

---

### I - Isolation: Concurrent Transactions Don't Interfere

**Definition**: Concurrent transactions execute as if they ran serially. No race conditions.

**Problem Without Isolation** (Race Condition):
```
User has $10,000 balance.
Transaction A: Buy AAPL for $8,000
Transaction B: Buy TSLA for $7,000

Without isolation:
T1: Read balance = $10,000 ✓
T2: Read balance = $10,000 ✓
T1: Deduct $8,000 → balance = $2,000
T2: Deduct $7,000 → balance = $3,000 (WRONG! Should be -$5,000)

Result: User "double-spent" their balance! 😱
```

**Isolation Levels** (Weakest to Strongest):

| **Level** | **Prevents** | **Use Case** | **Risk** |
|-----------|-------------|-------------|----------|
| **READ UNCOMMITTED** | Nothing | Never use in finance | Dirty reads, lost updates |
| **READ COMMITTED** | Dirty reads | Default in many DBs | Non-repeatable reads |
| **REPEATABLE READ** | Dirty reads, non-repeatable reads | MySQL default | Phantom reads |
| **SERIALIZABLE** | All anomalies | **Financial systems** | Slowest (but correct!) |

**SERIALIZABLE Isolation** (Required for Trading):
```sql
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- User A tries to buy AAPL
SELECT balance FROM accounts WHERE user_id = 123 FOR UPDATE;
-- Balance = $10,000

-- User A (concurrent) tries to buy TSLA
SELECT balance FROM accounts WHERE user_id = 123 FOR UPDATE;
-- BLOCKS until first transaction commits

UPDATE accounts SET balance = balance - 8000 WHERE user_id = 123;
COMMIT;

-- Now second transaction can proceed
SELECT balance FROM accounts WHERE user_id = 123 FOR UPDATE;
-- Balance = $2,000 (correct!)
```

**Implementation Techniques**:
- **Pessimistic Locking**: `SELECT FOR UPDATE` (used above)
- **Optimistic Locking**: Version numbers, retry on conflict
- **Multi-Version Concurrency Control (MVCC)**: PostgreSQL, CockroachDB

**Database Support**:
- ✅ PostgreSQL (SERIALIZABLE)
- ✅ CockroachDB (SERIALIZABLE by default)
- ⚠️ MySQL (REPEATABLE READ, not SERIALIZABLE by default)
- ❌ Most NoSQL databases (eventual consistency)

---

### D - Durability: Committed = Permanent

**Definition**: Once a transaction is committed, it survives crashes, power failures, and disasters.

**Without Durability** (Nightmare Scenario):
```
User buys 100 AAPL @ $150
Transaction commits: "Trade successful!"
Server crashes 1ms later
User's balance: -$15,000
User's shares: 0 (trade lost!)
Result: Money disappeared into the void! 😱
```

**Implementation: Write-Ahead Logging (WAL)**:
```
1. Transaction begins
2. Write changes to WAL (append-only log on disk)
3. Flush WAL to disk (fsync)
4. Acknowledge commit to client
5. Later: Apply changes to database (can be async)

If crash occurs:
- Replay WAL to recover committed transactions
- Rollback uncommitted transactions
```

**PostgreSQL WAL Configuration**:
```sql
-- postgresql.conf
wal_level = replica
fsync = on  -- NEVER disable in production!
synchronous_commit = on  -- Wait for WAL flush before COMMIT
wal_sync_method = fdatasync  -- OS-specific optimization
```

**Replication for Durability**:
```
Primary Database:
  ↓ Synchronous replication
Replica 1 (same AZ): Committed when both write WAL
  ↓ Asynchronous replication
Replica 2 (different AZ): For disaster recovery
```

**Durability Levels**:
- **Single Node + WAL**: Survives process crash
- **Synchronous Replication (2 nodes)**: Survives node failure
- **Multi-AZ Replication (3+ nodes)**: Survives AZ failure
- **Cross-Region Replication**: Survives region failure

**Database Support**:
- ✅ PostgreSQL (WAL, synchronous replication)
- ✅ CockroachDB (Raft consensus, 3+ replicas)
- ✅ MySQL (binary log, replication)
- ⚠️ Redis (AOF persistence, can lose seconds of data)
- ❌ Memcached (no persistence)

---

## 2. Financial Constraints Beyond ACID

### Constraint 1: BigDecimal for Money (No Floating Point!)

**The Problem with Float**:
```java
// NEVER DO THIS
double price = 150.25;
double quantity = 100;
double total = price * quantity;
System.out.println(total);  // 15025.000000000002 😱

// Accounting system now off by $0.000000000002
// Multiply by 1 billion transactions = $2,000 discrepancy!
```

**The Correct Way**:
```java
// ALWAYS USE BIGDECIMAL
BigDecimal price = new BigDecimal("150.25");
BigDecimal quantity = new BigDecimal("100");
BigDecimal total = price.multiply(quantity);
System.out.println(total);  // 15025.00 exactly ✓
```

**Database Types**:
```sql
-- ✅ CORRECT
CREATE TABLE orders (
    price DECIMAL(10,2),   -- Exact precision
    amount DECIMAL(15,2)
);

-- ❌ WRONG
CREATE TABLE orders (
    price DOUBLE,   -- Floating point errors!
    amount FLOAT
);
```

---

### Constraint 2: Idempotency (Safe Retries)

**Problem**: Network failures can cause duplicate requests.
```
Client → Server: "Buy 100 AAPL"
Server: "OK" (executes trade)
Network fails before response reaches client
Client retries: "Buy 100 AAPL" (thinks first request failed)
Result: User bought 200 shares instead of 100! 😱
```

**Solution: Idempotency Keys**:
```java
@PostMapping("/orders")
public OrderResponse placeOrder(@RequestBody OrderRequest request,
                                @Header("Idempotency-Key") String idempotencyKey) {
    // Check if we've already processed this request
    Optional<Order> existing = orderRepo.findByIdempotencyKey(idempotencyKey);
    if (existing.isPresent()) {
        return OrderResponse.from(existing.get());  // Return cached response
    }
    
    // Process new order
    Order order = orderService.placeOrder(request, idempotencyKey);
    return OrderResponse.from(order);
}
```

**Database Schema**:
```sql
CREATE TABLE orders (
    order_id UUID PRIMARY KEY,
    idempotency_key VARCHAR(64) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    -- ... other fields
);

CREATE UNIQUE INDEX idx_idempotency ON orders(idempotency_key);
```

**Best Practices**:
- Client generates UUID for each request
- Server stores idempotency key with order
- Safe to retry with same key (gets same result)

---

### Constraint 3: Audit Trail (Immutability)

**Regulatory Requirement**: SEC Rule 17a-4
- Every order, trade, and balance change must be logged
- Logs must be **immutable** (WORM: Write-Once-Read-Many)
- Retention: 7 years minimum

**Implementation**:
```sql
-- Ledger table: APPEND-ONLY, NO UPDATES/DELETES
CREATE TABLE transactions (
    txn_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id UUID,
    type VARCHAR(20) NOT NULL,  -- BUY, SELL, DEPOSIT, FEE
    amount DECIMAL(15,2) NOT NULL,
    balance_before DECIMAL(15,2) NOT NULL,
    balance_after DECIMAL(15,2) NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

-- NO UPDATE or DELETE statements allowed!
-- Application-level enforcement + database triggers

-- Database trigger to prevent modifications
CREATE TRIGGER prevent_transaction_modifications
BEFORE UPDATE OR DELETE ON transactions
FOR EACH ROW
EXECUTE FUNCTION abort_modification();
```

**Blockchain Alternative**:
```
Some financial systems use blockchain for audit trails:
- Cryptographic hash of each transaction
- Hash includes previous transaction hash (chain)
- Tampering breaks the chain (detectable)
```

---

### Constraint 4: Reconciliation (Double-Entry Accounting)

**Principle**: Every debit has a corresponding credit.
```
User A buys 100 AAPL from User B:
- A: Debit $15,000 (outflow)
- B: Credit $15,000 (inflow)
- Net: $0 (system is balanced)
```

**Daily Reconciliation**:
```sql
-- Run this nightly to verify system integrity
SELECT SUM(amount) AS net_change
FROM transactions
WHERE DATE(created_at) = CURRENT_DATE;

-- Should equal 0 (or exactly match deposits/withdrawals)

-- If not 0 → DATA CORRUPTION → ALERT! 🚨
```

**Position Reconciliation**:
```sql
-- Verify positions match transaction history
SELECT
    user_id,
    symbol,
    SUM(CASE WHEN type = 'BUY' THEN quantity ELSE -quantity END) AS calculated_quantity,
    (SELECT quantity FROM positions WHERE user_id = t.user_id AND symbol = t.symbol) AS recorded_quantity
FROM transactions t
GROUP BY user_id, symbol
HAVING calculated_quantity != recorded_quantity;

-- Any rows returned = DISCREPANCY → INVESTIGATE!
```

---

## 3. Database Comparison for Trading Systems

| **Database** | **ACID** | **SERIALIZABLE** | **Performance** | **Verdict** |
|--------------|----------|-----------------|----------------|-------------|
| **PostgreSQL** | ✅ Full | ✅ Yes | 20K writes/sec | ✅ Excellent for trading |
| **CockroachDB** | ✅ Full | ✅ Yes (default) | 50K writes/sec | ✅ Best for multi-region |
| **MySQL (InnoDB)** | ✅ Full | ⚠️ Partial | 30K writes/sec | ⚠️ Use with caution |
| **MongoDB** | ⚠️ Limited | ❌ No | 100K writes/sec | ❌ Not recommended |
| **Cassandra** | ❌ No | ❌ No | 1M writes/sec | ❌ Never for money |
| **DynamoDB** | ⚠️ Single-item | ❌ No | 100K writes/sec | ❌ Not for multi-step txns |
| **Redis** | ⚠️ Single-key | ❌ No | 1M+ writes/sec | ❌ Cache only, not primary |

---

## 4. Real-World ACID Violations (Cautionary Tales)

### Case Study 1: Knight Capital ($440 Million Loss in 45 Minutes)
**What Happened** (2012):
- Deployment bug caused duplicate orders
- No idempotency checks
- System sent 4 million orders instead of 1 million
- Bought at high prices, sold at low prices

**Root Cause**: No idempotency, no proper testing
**Lesson**: Always use idempotency keys, test thoroughly

### Case Study 2: Robinhood Negative Balance Bug
**What Happened** (2019):
- Infinite leverage glitch
- Users could borrow unlimited money
- Race condition in margin calculation

**Root Cause**: Insufficient isolation level, race condition
**Lesson**: Use SERIALIZABLE isolation, test concurrent scenarios

### Case Study 3: Coinbase Double-Spend
**What Happened** (2018):
- Users could withdraw same funds twice
- Race condition in balance check

**Root Cause**: Optimistic locking without retry, race condition
**Lesson**: Use pessimistic locking (SELECT FOR UPDATE) for critical sections

---

## 5. Testing ACID Compliance

### Test 1: Atomicity Test
```java
@Test
public void testAtomicityOnFailure() {
    // Given: User has $10,000
    Account account = createAccount(userId, 10000);
    
    // When: Place order that will fail (insufficient shares to sell)
    try {
        orderService.sellShares(userId, "AAPL", 100, 150.00);
    } catch (InsufficientSharesException e) {
        // Expected
    }
    
    // Then: Balance should be unchanged (rollback occurred)
    Account finalAccount = accountRepo.findByUserId(userId);
    assertEquals(10000, finalAccount.getBalance());
}
```

### Test 2: Isolation Test (Race Condition)
```java
@Test
public void testConcurrentOrdersIsolation() throws InterruptedException {
    // Given: User has $10,000
    Account account = createAccount(userId, 10000);
    
    // When: Place two concurrent orders totaling $18,000
    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<Order> order1 = executor.submit(() ->
        orderService.buyShares(userId, "AAPL", 100, 100.00));  // $10,000
    Future<Order> order2 = executor.submit(() ->
        orderService.buyShares(userId, "TSLA", 80, 100.00));   // $8,000
    
    // Then: One order should succeed, one should fail
    int successCount = 0;
    try { order1.get(); successCount++; } catch (Exception e) {}
    try { order2.get(); successCount++; } catch (Exception e) {}
    
    assertEquals(1, successCount);  // Only 1 should succeed
    
    Account finalAccount = accountRepo.findByUserId(userId);
    assertTrue(finalAccount.getBalance() >= 0);  // No overdraft
}
```

### Test 3: Durability Test (Crash Simulation)
```java
@Test
public void testDurabilityAfterCrash() {
    // Given: Place an order
    Order order = orderService.buyShares(userId, "AAPL", 100, 150.00);
    
    // When: Simulate database crash and recovery
    database.crash();
    database.recover();  // Replays WAL
    
    // Then: Order should still exist
    Order recovered = orderRepo.findById(order.getId());
    assertNotNull(recovered);
    assertEquals("FILLED", recovered.getStatus());
}
```

---

## 6. ACID Best Practices for Trading Systems

### ✅ DO:
1. **Use PostgreSQL or CockroachDB** (strong ACID support)
2. **Set SERIALIZABLE isolation** for financial transactions
3. **Use BigDecimal** for all money calculations
4. **Implement idempotency keys** for all state-changing APIs
5. **Maintain immutable audit trail** (append-only ledger)
6. **Enable synchronous replication** (for durability)
7. **Use SELECT FOR UPDATE** for critical sections
8. **Test concurrent scenarios** (race conditions)
9. **Reconcile balances daily** (detect discrepancies)
10. **Monitor transaction rollback rate** (should be <0.1%)

### ❌ DON'T:
1. **Never use eventual consistency** for money
2. **Never use float/double** for money
3. **Never disable fsync** (durability)
4. **Never use READ UNCOMMITTED** isolation
5. **Never skip idempotency checks**
6. **Never allow UPDATE/DELETE on audit tables**
7. **Never trust client-side balance checks**
8. **Never deploy without load testing**
9. **Never ignore transaction errors**
10. **Never use NoSQL as primary database** (unless multi-document ACID)

---

## 7. ACID Overhead: Performance Trade-offs

**ACID Tax**:
```
Without ACID (NoSQL, eventual consistency):
- Latency: 1ms
- Throughput: 100K writes/sec
- Consistency: Eventually ¯\_(ツ)_/¯

With ACID (PostgreSQL SERIALIZABLE):
- Latency: 5ms (5x slower)
- Throughput: 20K writes/sec (5x less)
- Consistency: Strong ✓

Conclusion: You MUST pay the ACID tax for financial systems.
            Speed without correctness is worthless.
```

**Mitigation Strategies**:
1. **Shard database** by user_id or symbol
2. **Use read replicas** for analytics queries
3. **Cache read-heavy data** (market quotes, portfolios)
4. **Optimize queries** (indexes, EXPLAIN ANALYZE)
5. **Batch non-critical writes** (analytics events)

---

## 8. Regulatory Requirements

### SEC Rule 17a-4 (Record Retention)
- **Duration**: 7 years minimum
- **Storage**: WORM (Write-Once-Read-Many)
- **Accessibility**: Retrievable within 24 hours
- **Audit**: Quarterly review for completeness

### FINRA CAT (Consolidated Audit Trail)
- **Scope**: All orders, executions, cancellations
- **Submission**: Within 1 business day
- **Format**: CAT Reporter Technical Specifications
- **Retention**: 6 years

### SOC 2 (Security & Availability)
- **Access Controls**: RBAC, audit logs
- **Change Management**: All DB changes tracked
- **Incident Response**: <1 hour response time
- **Backup & Recovery**: Daily backups, tested quarterly

---

## Conclusion

ACID compliance is the bedrock of financial systems. While it comes with a performance cost (5x slower than eventual consistency), it's **non-negotiable** for trading platforms.

**Remember**:
> "In finance, being WRONG is worse than being SLOW. ACID ensures you're never wrong."

**Key Takeaways**:
1. **Atomicity**: All-or-nothing transactions
2. **Consistency**: Invariants always maintained
3. **Isolation**: SERIALIZABLE for money
4. **Durability**: WAL + synchronous replication
5. **BigDecimal**: No floating point for money
6. **Idempotency**: Safe retries
7. **Audit Trail**: Immutable, 7-year retention
8. **Testing**: Concurrency, crashes, edge cases

---

*This guide is for educational purposes. Always consult compliance experts and thoroughly test financial systems before production deployment.*
