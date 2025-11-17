# Intro to ACID Database Transactions Deep Dive

## Contents

- [Intro to ACID Database Transactions Deep Dive](#intro-to-acid-database-transactions-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Atomicity: All or Nothing](#atomicity-all-or-nothing)
    - [Consistency: Invariants Preserved](#consistency-invariants-preserved)
    - [Isolation: Concurrent Execution](#isolation-concurrent-execution)
    - [Durability: Permanent Storage](#durability-permanent-storage)
    - [ACID vs BASE: The Tradeoff Spectrum](#acid-vs-base-the-tradeoff-spectrum)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Transaction Requirements Clarification](#1-transaction-requirements-clarification)
        - [Isolation Level Selection](#2-isolation-level-selection)
        - [Concurrency Control Mechanism](#3-concurrency-control-mechanism)
        - [Failure Recovery Strategy](#4-failure-recovery-strategy)
        - [Performance Optimization](#5-performance-optimization)
    - [MIND MAP: ACID CONCEPTS](#mind-map-acid-concepts)

## Core Mental Model

```text
ACID = Atomicity + Consistency + Isolation + Durability

A transaction is a unit of work that MUST:
- Execute completely or not at all (Atomicity)
- Preserve database invariants (Consistency)
- Not interfere with other transactions (Isolation)
- Survive system failures (Durability)
```

**The Fundamental Guarantee:**
```
┌────────────────────────────────────────────────┐
│ ACID Properties = Safety in Concurrent         │
│                   Multi-User Systems           │
│                                                │
│ Without ACID:                                  │
│ ├─ Money disappears (lost updates)             │
│ ├─ Inventory goes negative (dirty reads)       │
│ ├─ Duplicate charges (write skew)              │
│ └─ Data loss on crash (no durability)          │
│                                                │
│ With ACID:                                     │
│ └─ Database behaves like single-user,          │
│    serial execution, with crash safety         │
└────────────────────────────────────────────────┘
```

**Real World Example:**
```java
// Bank transfer: THE canonical ACID example
@Transactional
public void transferMoney(
    Long fromAccountId,
    Long toAccountId,
    BigDecimal amount
) {
    // BEGIN TRANSACTION

    // 1. Read balances
    Account fromAccount = accountRepo.findById(fromAccountId);
    Account toAccount = accountRepo.findById(toAccountId);

    // 2. Validate (Consistency)
    if (fromAccount.getBalance().compareTo(amount) < 0) {
        throw new InsufficientFundsException();
    }

    // 3. Update balances
    fromAccount.setBalance(fromAccount.getBalance().subtract(amount));
    toAccount.setBalance(toAccount.getBalance().add(amount));

    // 4. Save changes
    accountRepo.save(fromAccount);
    accountRepo.save(toAccount);

    // 5. Log transaction
    transactionLog.record(fromAccountId, toAccountId, amount);

    // COMMIT TRANSACTION
    // All operations succeed, or ALL are rolled back
}
```

**Key insight**: ACID is expensive but essential for **critical data** (money, inventory, medical records). Not all data needs ACID!

---

## Atomicity: All or Nothing

🎓 **PROFESSOR**: Atomicity means **transactions are indivisible**:

```
Transaction = Atomic unit of work

Either:
  ALL operations succeed → COMMIT
Or:
  ANY operation fails → ROLLBACK (undo everything)

No partial completion allowed!
```

### How Atomicity is Implemented

🏗️ **ARCHITECT**: Three mechanisms ensure atomicity:

#### 1. **Write-Ahead Logging (WAL)**
```python
class TransactionManager:
    """
    Manages transaction atomicity using WAL
    """
    def begin_transaction(self) -> int:
        tx_id = self.next_transaction_id()
        self.active_transactions[tx_id] = {
            'status': 'ACTIVE',
            'operations': [],
            'started_at': time.time()
        }
        # Write BEGIN record to WAL
        self.wal.write(f"BEGIN {tx_id}")
        return tx_id

    def execute_operation(self, tx_id: int, operation: Operation):
        """
        Execute operation and log to WAL BEFORE modifying data
        """
        # 1. Write operation to WAL (with old value for undo)
        self.wal.write(f"UPDATE {tx_id} {operation.table} "
                      f"old={operation.old_value} "
                      f"new={operation.new_value}")

        # 2. Force WAL to disk (fsync)
        self.wal.flush()

        # 3. Now safe to modify data in memory/disk
        self.storage.update(operation.table, operation.new_value)

        # Track operation for rollback
        self.active_transactions[tx_id]['operations'].append(operation)

    def commit(self, tx_id: int):
        """
        Commit transaction: Make changes permanent
        """
        # 1. Write COMMIT record to WAL
        self.wal.write(f"COMMIT {tx_id}")
        self.wal.flush()  # Force to disk

        # 2. Mark transaction as committed
        self.active_transactions[tx_id]['status'] = 'COMMITTED'

        # 3. Eventually flush dirty pages to disk (lazy)
        # (Can delay this because WAL already on disk)

        # 4. Clean up
        del self.active_transactions[tx_id]

    def rollback(self, tx_id: int):
        """
        Rollback transaction: Undo all changes
        """
        # 1. Get all operations in reverse order
        operations = reversed(
            self.active_transactions[tx_id]['operations']
        )

        # 2. Undo each operation
        for op in operations:
            self.storage.update(op.table, op.old_value)

        # 3. Write ABORT record to WAL
        self.wal.write(f"ABORT {tx_id}")
        self.wal.flush()

        # 4. Clean up
        del self.active_transactions[tx_id]
```

#### 2. **Shadow Paging** (Alternative to WAL)
```
Instead of in-place updates:
1. Copy page to new location
2. Modify the copy
3. Update pointer atomically on commit
4. Old page becomes garbage

Used by: SQLite (in some modes)
```

#### 3. **Multi-Version Concurrency Control (MVCC)**
```sql
-- MVCC keeps multiple versions of same row
-- Each transaction sees its own snapshot

-- T1 (tx_id=100): Reads account balance
SELECT balance FROM accounts WHERE id = 1;
-- Reads version with tx_id <= 100

-- T2 (tx_id=101): Updates account (creates new version)
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- Creates new row version with tx_id=101

-- T1 still sees old version (tx_id <= 100)
-- T2 sees new version (tx_id <= 101)

-- On commit: new version becomes visible to future transactions
-- On rollback: new version is garbage collected
```

**Interview talking point**: "WAL is fundamental. We write to log BEFORE modifying data. If crash occurs mid-transaction, we replay log on restart. Uncommitted transactions are rolled back automatically."

---

## Consistency: Invariants Preserved

🎓 **PROFESSOR**: Consistency means **database rules are never violated**:

```
Invariants (rules that must ALWAYS hold):
├─ Balance >= 0 (no negative balances)
├─ Foreign keys valid (referential integrity)
├─ Unique constraints (no duplicates)
├─ Application-level rules (e.g., total debits = total credits)
└─ State machine rules (order: pending → shipped → delivered)
```

🏗️ **ARCHITECT**: Consistency is enforced at **multiple levels**:

### 1. **Database-Level Constraints**
```sql
CREATE TABLE accounts (
    account_id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    balance DECIMAL(15,2) NOT NULL,

    -- Constraint 1: No negative balance
    CHECK (balance >= 0),

    -- Constraint 2: User must exist
    FOREIGN KEY (user_id) REFERENCES users(user_id)
        ON DELETE RESTRICT,

    -- Constraint 3: Unique account per user
    UNIQUE (user_id)
);

CREATE TABLE transfers (
    transfer_id BIGINT PRIMARY KEY,
    from_account_id BIGINT NOT NULL,
    to_account_id BIGINT NOT NULL,
    amount DECIMAL(15,2) NOT NULL,

    -- Constraint: Can't transfer to same account
    CHECK (from_account_id != to_account_id),

    -- Constraint: Positive amount only
    CHECK (amount > 0),

    FOREIGN KEY (from_account_id) REFERENCES accounts(account_id),
    FOREIGN KEY (to_account_id) REFERENCES accounts(account_id)
);
```

### 2. **Application-Level Consistency**
```java
@Service
public class TransferService {

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void transfer(TransferRequest request) {
        // Lock accounts in consistent order (prevent deadlock)
        Long firstId = Math.min(request.getFromId(), request.getToId());
        Long secondId = Math.max(request.getFromId(), request.getToId());

        Account first = accountRepo.findByIdWithLock(firstId);
        Account second = accountRepo.findByIdWithLock(secondId);

        // Determine which is source and destination
        Account from = (first.getId().equals(request.getFromId()))
                       ? first : second;
        Account to = (first.getId().equals(request.getFromId()))
                     ? second : first;

        // Business rule: Validate sufficient funds
        if (from.getBalance().compareTo(request.getAmount()) < 0) {
            throw new InsufficientFundsException(
                String.format("Account %d has insufficient funds", from.getId())
            );
        }

        // Business rule: Validate daily transfer limit
        BigDecimal dailyTotal = transferRepo.getDailyTotal(
            from.getId(),
            LocalDate.now()
        );
        if (dailyTotal.add(request.getAmount())
            .compareTo(from.getDailyLimit()) > 0) {
            throw new DailyLimitExceededException();
        }

        // Execute transfer (maintains invariant: sum of all balances unchanged)
        from.setBalance(from.getBalance().subtract(request.getAmount()));
        to.setBalance(to.getBalance().add(request.getAmount()));

        accountRepo.save(from);
        accountRepo.save(to);

        // Audit log
        auditLog.record(
            "TRANSFER",
            from.getId(),
            to.getId(),
            request.getAmount()
        );
    }
}
```

### 3. **Triggers for Cross-Table Consistency**
```sql
-- Maintain denormalized order count
CREATE TRIGGER update_order_count
AFTER INSERT ON orders
FOR EACH ROW
BEGIN
    UPDATE users
    SET order_count = order_count + 1
    WHERE user_id = NEW.user_id;
END;

CREATE TRIGGER update_order_count_on_delete
AFTER DELETE ON orders
FOR EACH ROW
BEGIN
    UPDATE users
    SET order_count = order_count - 1
    WHERE user_id = OLD.user_id;
END;
```

**Interview gold**: "Consistency is the 'C' everyone forgets! Database constraints catch simple violations, but complex business rules need application-level validation inside transactions."

---

## Isolation: Concurrent Execution

🎓 **PROFESSOR**: Isolation means **concurrent transactions don't interfere**:

```
Without isolation, these anomalies occur:

1. DIRTY READ
   T1: UPDATE balance = 100
   T2: READ balance → sees 100
   T1: ROLLBACK
   T2: Used invalid data!

2. NON-REPEATABLE READ
   T1: READ balance → 100
   T2: UPDATE balance = 200, COMMIT
   T1: READ balance → 200 (changed!)

3. PHANTOM READ
   T1: SELECT COUNT(*) → 10 rows
   T2: INSERT new row, COMMIT
   T1: SELECT COUNT(*) → 11 rows (new row appeared!)

4. LOST UPDATE
   T1: READ balance → 100
   T2: READ balance → 100
   T1: WRITE balance = 150, COMMIT
   T2: WRITE balance = 200, COMMIT
   T1's update lost!
```

### Isolation Levels

🏗️ **ARCHITECT**: SQL standard defines **four isolation levels**:

```
┌─────────────────────┬───────────┬──────────────┬──────────┬──────────┐
│ Isolation Level     │ Dirty Read│ Non-Repeat   │ Phantom  │ Lost Upd │
├─────────────────────┼───────────┼──────────────┼──────────┼──────────┤
│ READ UNCOMMITTED    │    ✗      │      ✗       │    ✗     │    ✗     │
│ READ COMMITTED      │    ✓      │      ✗       │    ✗     │    ✗     │
│ REPEATABLE READ     │    ✓      │      ✓       │    ✗     │    ✓     │
│ SERIALIZABLE        │    ✓      │      ✓       │    ✓     │    ✓     │
└─────────────────────┴───────────┴──────────────┴──────────┴──────────┘

✓ = Prevented
✗ = Possible
```

**Code Examples:**
```sql
-- READ UNCOMMITTED: Fastest, least safe
SET TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
BEGIN;
SELECT * FROM accounts WHERE user_id = 123;
-- Can see uncommitted changes from other transactions!
COMMIT;


-- READ COMMITTED: Default in PostgreSQL, Oracle
SET TRANSACTION ISOLATION LEVEL READ COMMITTED;
BEGIN;
SELECT balance FROM accounts WHERE id = 1;  -- 100
-- Another tx updates to 200 and commits
SELECT balance FROM accounts WHERE id = 1;  -- 200 (changed!)
COMMIT;


-- REPEATABLE READ: Default in MySQL InnoDB
SET TRANSACTION ISOLATION LEVEL REPEATABLE READ;
BEGIN;
SELECT balance FROM accounts WHERE id = 1;  -- 100
-- Another tx updates to 200 and commits
SELECT balance FROM accounts WHERE id = 1;  -- Still 100 (snapshot isolation)
-- But new rows can still appear (phantom reads)
COMMIT;


-- SERIALIZABLE: Strictest, slowest
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
BEGIN;
SELECT * FROM accounts WHERE balance > 1000;
-- Another tx cannot insert/update/delete any matching rows
-- Until this transaction commits
COMMIT;
```

### Implementing Isolation: Locking vs MVCC

```java
// 1. PESSIMISTIC LOCKING (2PL - Two-Phase Locking)
public class TwoPhaseLocking {

    @Transactional
    public void updateWithPessimisticLock(Long accountId, BigDecimal amount) {
        // Acquire lock during read
        Account account = entityManager.find(
            Account.class,
            accountId,
            LockModeType.PESSIMISTIC_WRITE  // X-lock (exclusive)
        );

        // Other transactions BLOCKED until this commits
        account.setBalance(account.getBalance().add(amount));

        entityManager.merge(account);
        // Lock released on COMMIT
    }
}


// 2. OPTIMISTIC LOCKING (MVCC)
@Entity
public class Account {
    @Id
    private Long id;

    private BigDecimal balance;

    @Version  // Optimistic lock version
    private Long version;
}

public class OptimisticLocking {

    @Transactional
    public void updateWithOptimisticLock(Long accountId, BigDecimal amount) {
        // No lock during read
        Account account = accountRepo.findById(accountId);

        account.setBalance(account.getBalance().add(amount));

        try {
            accountRepo.save(account);
            // On save: checks if version changed
            // UPDATE accounts SET balance=?, version=version+1
            // WHERE id=? AND version=?

        } catch (OptimisticLockException e) {
            // Another transaction modified this row
            // Retry or abort
            throw new ConcurrencyException("Account modified by another transaction");
        }
    }
}
```

**Decision Tree:**
```python
class IsolationLevelSelector:
    """
    Choose isolation level based on requirements
    """
    def select_isolation_level(self, operation: Operation) -> IsolationLevel:

        # Financial transactions: SERIALIZABLE
        if operation.category == 'FINANCIAL':
            return IsolationLevel.SERIALIZABLE

        # Inventory management: REPEATABLE_READ
        if operation.category == 'INVENTORY':
            return IsolationLevel.REPEATABLE_READ

        # Analytics/reporting: READ_COMMITTED (or UNCOMMITTED for speed)
        if operation.is_read_only and operation.category == 'ANALYTICS':
            return IsolationLevel.READ_UNCOMMITTED

        # Default: READ_COMMITTED (good balance)
        return IsolationLevel.READ_COMMITTED
```

**Interview talking point**: "I'd use REPEATABLE_READ as default. It prevents most anomalies with good performance via MVCC. Only escalate to SERIALIZABLE for critical operations like financial transfers."

---

## Durability: Permanent Storage

🎓 **PROFESSOR**: Durability means **committed data survives crashes**:

```
Committed Transaction = Permanent

Even if:
├─ Power loss
├─ Kernel panic
├─ Disk failure
└─ Cosmic rays flip bits

Data must survive (with replication)
```

🏗️ **ARCHITECT**: Durability is achieved through:

### 1. **Write-Ahead Logging (WAL)**
```python
class DurabilityManager:
    """
    Ensures durability via WAL and fsync
    """
    def commit_transaction(self, tx_id: int):
        # 1. Write all transaction operations to WAL
        for operation in self.active_transactions[tx_id]['operations']:
            self.wal.append(operation)

        # 2. Write COMMIT record to WAL
        self.wal.append(CommitRecord(tx_id, timestamp=time.time()))

        # 3. CRITICAL: fsync WAL to disk
        # Without this, data in OS page cache (volatile!)
        os.fsync(self.wal.file_descriptor)
        # Now safe: even if crash, WAL on persistent storage

        # 4. Respond to client: "Transaction committed"
        self.send_ack_to_client(tx_id)

        # 5. Lazy: flush dirty pages to data files (async)
        # Can delay because WAL already durable
        self.schedule_checkpoint()
```

**The fsync() System Call:**
```c
// What happens without fsync:
write(fd, data, size);  // Writes to OS page cache (RAM)
// If crash here, data LOST!

// Durable writes require fsync:
write(fd, data, size);
fsync(fd);  // Forces OS to flush to physical disk
// Now safe: data on disk platters
```

### 2. **Replication for Fault Tolerance**
```
┌──────────────────────────────────────────────┐
│ PRIMARY DATABASE                              │
│  1. Receive write                             │
│  2. Write to local WAL + fsync                │
│  3. Send to replicas                          │
│  4. Wait for ACKs (synchronous replication)   │
│  5. Commit transaction                        │
│  6. Respond to client                         │
└────────────────┬─────────────────────────────┘
                 ↓
       ┌─────────┴─────────┐
       ↓                   ↓
┌─────────────┐     ┌─────────────┐
│  REPLICA 1  │     │  REPLICA 2  │
│  (Standby)  │     │  (Standby)  │
└─────────────┘     └─────────────┘

Durability levels:
├─ 0 replicas: Durable on single machine (risky!)
├─ 1 replica:  Can survive 1 machine failure
├─ 2 replicas: Can survive 2 machine failures
└─ N replicas: Can survive N failures
```

**Synchronous vs Asynchronous Replication:**
```java
public enum ReplicationMode {
    SYNCHRONOUS,   // Wait for replica ACKs (durable, slow)
    ASYNCHRONOUS   // Don't wait (fast, risk data loss)
}

@Service
public class ReplicationService {

    public void commitWithReplication(
        Transaction tx,
        ReplicationMode mode
    ) {
        // 1. Write to primary WAL
        primary.writeToWAL(tx);
        primary.fsync();

        // 2. Send to replicas
        List<CompletableFuture<Void>> replicationFutures =
            replicas.stream()
                .map(replica -> replica.replicateAsync(tx))
                .collect(Collectors.toList());

        if (mode == ReplicationMode.SYNCHRONOUS) {
            // Wait for all replicas to ACK
            CompletableFuture.allOf(
                replicationFutures.toArray(new CompletableFuture[0])
            ).join();
            // Now durable on primary + all replicas
        }

        // 3. Mark as committed
        primary.markCommitted(tx.getId());
    }
}
```

### 3. **Checksums for Corruption Detection**
```sql
-- PostgreSQL example: data_checksums
-- Detects bit rot / silent corruption

-- Enable checksums (requires initdb)
initdb -k /var/lib/postgresql/data

-- On every page read, verify checksum
-- If checksum mismatch → corruption detected
-- Restore from replica or backup
```

**Interview gold**: "Durability is all about fsync. Databases must force data to physical storage before saying 'committed'. SSDs help (no rotational latency) but can't skip fsync. Cloud databases replicate to multiple zones for durability."

---

## ACID vs BASE: The Tradeoff Spectrum

🎓 **PROFESSOR**: ACID and BASE are **two philosophies**:

```
┌─────────────────────────────────────────────────┐
│ ACID (Traditional RDBMS)                        │
│ ├─ Strong consistency                           │
│ ├─ Immediate consistency                        │
│ ├─ Pessimistic (assumes conflicts)              │
│ └─ Sacrifice availability for consistency       │
│                                                 │
│ BASE (NoSQL, Distributed Systems)               │
│ ├─ Basically Available                          │
│ ├─ Soft state (can change without input)        │
│ ├─ Eventual consistency                         │
│ ├─ Optimistic (assumes no conflicts)            │
│ └─ Sacrifice consistency for availability       │
└─────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: **Choose based on use case**:

```python
class ConsistencyModelSelector:
    """
    Match data to consistency requirement
    """
    def select_model(self, data_type: str) -> str:

        # Strong consistency required (ACID)
        acid_use_cases = {
            'financial_transactions',   # Money must be exact
            'inventory_management',      # Can't oversell
            'user_authentication',       # Security critical
            'medical_records',           # Life-critical
            'booking_systems'            # No double-booking
        }

        if data_type in acid_use_cases:
            return "ACID_DATABASE"  # PostgreSQL, MySQL

        # Eventual consistency acceptable (BASE)
        base_use_cases = {
            'social_media_likes',        # Approximate count OK
            'view_counters',             # Eventual accuracy fine
            'recommendation_feeds',      # Staleness tolerable
            'analytics_data',            # Aggregate trends
            'user_activity_logs'         # Append-only
        }

        if data_type in base_use_cases:
            return "BASE_DATABASE"  # Cassandra, DynamoDB

        # Default: ACID (safer)
        return "ACID_DATABASE"
```

**Concrete Example:**
```java
// E-commerce: Mixed consistency model

// ACID: Order placement (critical)
@Transactional(isolation = Isolation.SERIALIZABLE)
public Order placeOrder(Cart cart) {
    // Must be atomic: charge payment + reserve inventory
    paymentService.charge(cart.getTotal());
    inventoryService.reserve(cart.getItems());
    return orderRepo.save(new Order(cart));
}

// BASE: Product view counter (non-critical)
@Async
public void incrementViewCount(Long productId) {
    // Eventually consistent: buffered updates
    viewCounterCache.increment(productId);
    // Flushed to database every 5 minutes
}

// BASE: Recommendation feed (staleness OK)
public List<Product> getRecommendations(Long userId) {
    // Pre-computed recommendations (may be hours old)
    return recommendationCache.get(userId);
    // Rebuilt nightly by batch job
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

When asked about *"ACID transactions"*, use this structure:

### 1. Transaction Requirements Clarification

```
Questions to ask:

Consistency:
- What invariants must hold? (balance >= 0, foreign keys, etc.)
- Can we tolerate eventual consistency or need immediate?
- Are there cross-table constraints?

Isolation:
- What's the concurrency level? (users, transactions/sec)
- What anomalies are unacceptable? (lost updates, dirty reads)
- Are read-heavy or write-heavy workloads?

Durability:
- What's acceptable data loss? (RTO/RPO)
- Do we need synchronous replication?
- Multi-region or single-region?

Scale:
- Transactions per second?
- Average transaction size (rows touched)?
- Long-running transactions or short?
```

### 2. Isolation Level Selection

```
Decision Matrix:

SERIALIZABLE:
- Financial transfers
- Inventory with limited stock
- Booking systems (prevent double-booking)
- Trade: Slowest, safest

REPEATABLE READ:
- E-commerce order processing
- User account updates
- Most OLTP applications
- Trade: Good balance

READ COMMITTED:
- Analytics queries
- Reporting dashboards
- Non-critical reads
- Trade: Fast reads, some anomalies

READ UNCOMMITTED:
- Approximate counts
- Real-time dashboards (staleness OK)
- Trade: Fastest, least safe
```

### 3. Concurrency Control Mechanism

```sql
-- Pessimistic Locking (when conflicts expected)
BEGIN;
SELECT * FROM inventory WHERE product_id = 123
FOR UPDATE;  -- Exclusive lock

UPDATE inventory SET quantity = quantity - 1
WHERE product_id = 123;

COMMIT;


-- Optimistic Locking (when conflicts rare)
-- Use version numbers or timestamps
SELECT id, quantity, version FROM inventory WHERE product_id = 123;
-- version = 5

UPDATE inventory
SET quantity = quantity - 1, version = version + 1
WHERE product_id = 123 AND version = 5;
-- If 0 rows updated → conflict, retry
```

### 4. Failure Recovery Strategy

```python
class TransactionRecovery:
    """
    Recover from crashes using WAL
    """
    def recover_from_crash(self):
        """
        ARIES recovery algorithm (simplified)
        """
        # Phase 1: ANALYSIS
        # Scan WAL to find last checkpoint and active transactions
        checkpoint = self.wal.find_last_checkpoint()
        active_txs = self.wal.get_active_transactions(checkpoint)

        # Phase 2: REDO
        # Replay all operations from checkpoint (even uncommitted)
        for log_record in self.wal.scan_from(checkpoint):
            self.redo_operation(log_record)

        # Phase 3: UNDO
        # Rollback all uncommitted transactions
        for tx_id in active_txs:
            if not self.is_committed(tx_id):
                self.undo_transaction(tx_id)

        # Database now in consistent state!
```

### 5. Performance Optimization

```
Techniques:

1. Reduce transaction scope
   - Only wrap critical operations
   - Read outside transaction when possible

2. Use appropriate isolation level
   - Don't default to SERIALIZABLE for everything
   - READ COMMITTED sufficient for many use cases

3. Batch operations
   - Bulk inserts faster than individual
   - Reduces WAL overhead

4. Connection pooling
   - Reuse database connections
   - Avoid transaction startup cost

5. Optimize indexes
   - Speed up locking (find rows faster)
   - Reduce lock contention time
```

**Code Example:**
```java
// ❌ BAD: Long transaction
@Transactional
public void processOrder(Long orderId) {
    Order order = orderRepo.findById(orderId);

    // External API call (slow!)
    paymentGateway.charge(order.getTotal());

    // Send email (slow!)
    emailService.sendConfirmation(order);

    orderRepo.save(order);
}
// Transaction holds locks for seconds!


// ✅ GOOD: Short transaction
public void processOrder(Long orderId) {
    // 1. Charge payment (outside transaction)
    PaymentResult payment = paymentGateway.charge(orderId);

    // 2. Update database (fast transaction)
    updateOrderStatus(orderId, payment);

    // 3. Send email (outside transaction, async)
    emailService.sendConfirmationAsync(orderId);
}

@Transactional
private void updateOrderStatus(Long orderId, PaymentResult payment) {
    Order order = orderRepo.findById(orderId);
    order.setPaymentId(payment.getId());
    order.setStatus(OrderStatus.PAID);
    orderRepo.save(order);
    // Transaction completes in milliseconds
}
```

---

## 🧠 MIND MAP: ACID CONCEPTS

```
                    ACID
                     |
        ┌────────────┼────────────┐
        ↓            ↓            ↓
   ATOMICITY    CONSISTENCY   ISOLATION    DURABILITY
        |            |            |             |
    ┌───┴───┐    ┌──┴──┐     ┌──┴──┐       ┌──┴──┐
    ↓       ↓    ↓     ↓     ↓     ↓       ↓     ↓
   WAL    MVCC  Check  App  Levels Locks  fsync  Repl
    |       |   Constr Rules  |     |       |     |
Undo/  Versions FK   Trigger RC   2PL   WAL   Multi
Redo              PK    |    RR   MVCC  flush  Zone
    |              UK    |   SER   OCC    |     |
Rollback        Trigger  |    |     |   Commit Sync/
Commit              Invariants  Phantom Version Async
                            NoRepeat    |
                            DirtyRead Checksum
                            LostUpdate
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **ACID = Recipe Cooking 👨‍🍳**
- **Atomicity**: Either complete dish or start over (no half-cooked meal)
- **Consistency**: Follow recipe rules (don't skip critical steps)
- **Isolation**: Multiple chefs don't interfere (separate stations)
- **Durability**: Written recipe survives kitchen fire (backup cookbook)

### 2. **Atomicity = Bank Withdrawal 💰**
- Take cash from ATM: either get money OR balance unchanged
- NEVER: balance deducted but no cash (partial failure)
- All-or-nothing guarantee

### 3. **Consistency = Physics Laws 🔬**
- Energy conservation: can't create or destroy
- Database: total money in system conserved
- Before: A=$100, B=$50, Total=$150
- After: A=$80, B=$70, Total=$150 (still)

### 4. **Isolation = Private Rooms 🏠**
- Each transaction in own room
- Can't see others' unfinished work
- No interference until door opens (commit)

### 5. **Durability = Notary Stamp 📜**
- Once stamped (committed), it's official
- Survives fires, floods (replicated)
- Cannot be undone (immutable)

### 6. **WAL = Ship's Log 🚢**
- Captain writes everything in log book BEFORE action
- If ship sinks, log book recovered
- Replay log to reconstruct journey

### 7. **Two-Phase Locking = Traffic Lights 🚦**
- Acquire locks (green light)
- Hold until transaction done
- Release all at once (commit)
- Prevents collisions (conflicts)

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of ACID Transactions:**

1. **ACID is Not Free**
   - Slower than non-transactional systems
   - Use only for data that NEEDS strong guarantees
   - Cache, logs, analytics: consider BASE

2. **Isolation Level Matters**
   - Default to READ COMMITTED or REPEATABLE READ
   - Escalate to SERIALIZABLE only when necessary
   - Understand your database's implementation (locking vs MVCC)

3. **Durability Requires fsync**
   - Without fsync, committed data can be lost
   - Replication helps but doesn't replace fsync
   - Know your RTO/RPO requirements

---

## 🏗️ ARCHITECT'S CHECKLIST

Before going to production:

- [ ] **Isolation Level**: Explicitly set for each transaction type?
- [ ] **Transaction Scope**: Minimized (exclude slow I/O)?
- [ ] **Locking Strategy**: Pessimistic vs optimistic chosen?
- [ ] **Deadlock Handling**: Lock ordering consistent? Retry logic?
- [ ] **WAL Configuration**: fsync enabled? Log size tuned?
- [ ] **Replication**: Synchronous for durability? Lag monitored?
- [ ] **Timeout Settings**: Long transactions killed automatically?
- [ ] **Connection Pool**: Sized correctly for transaction load?
- [ ] **Monitoring**: Transaction latency, lock waits, deadlocks tracked?
- [ ] **Testing**: Concurrent transaction tests? Crash recovery tested?

---

**Interview Closer**: "ACID guarantees are essential for correctness but expensive. I'd identify critical data requiring ACID (payments, inventory), use appropriate isolation levels (REPEATABLE_READ default), minimize transaction scope, and consider eventual consistency for non-critical data. Let's discuss specific scenarios..."
