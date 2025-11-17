# Read Committed Isolation Deep Dive

## Contents

- [Read Committed Isolation Deep Dive](#read-committed-isolation-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Read Committed Guarantee](#the-read-committed-guarantee)
    - [Implementation Mechanisms](#implementation-mechanisms)
    - [Allowed Anomalies and Implications](#allowed-anomalies-and-implications)
    - [Real-World Use Cases](#real-world-use-cases)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: READ COMMITTED](#mind-map-read-committed)

## Core Mental Model

```text
READ COMMITTED = "Only see data that has been successfully committed"

The Two Fundamental Rules:
1. No Dirty Reads:  You never see uncommitted changes from other transactions
2. No Dirty Writes: You never overwrite uncommitted changes from other transactions

What's NOT Guaranteed:
❌ Repeatable Reads (same query can return different results)
❌ Phantom Prevention (new rows can appear in range queries)
```

**The Default Choice:**
```
┌──────────────────────────────────────────────────┐
│ PostgreSQL      → READ COMMITTED (default)       │
│ Oracle          → READ COMMITTED (default)       │
│ SQL Server      → READ COMMITTED (default)       │
│ MySQL InnoDB    → REPEATABLE READ (exception!)   │
└──────────────────────────────────────────────────┘
```

**Why Default?** Perfect balance of **correctness** and **performance** for **stateless applications** (REST APIs, web services).

---

## The Read Committed Guarantee

🎓 **PROFESSOR**: Read Committed provides **two critical guarantees** that prevent the most dangerous anomalies.

### 1. **No Dirty Reads**

**Definition**: A transaction cannot read data written by another transaction that hasn't committed yet.

```sql
-- Timeline: What happens in READ COMMITTED

Time | Transaction A                  | Transaction B
-----|-------------------------------|--------------------------------
t1   | BEGIN;                        |
t2   |                               | BEGIN;
t3   | UPDATE accounts               |
     | SET balance = 500             |
     | WHERE id = 1;                 |
     | (uncommitted)                 |
t4   |                               | SELECT balance FROM accounts
     |                               | WHERE id = 1;
     |                               | → Returns 1000 (original value!)
     |                               | NOT 500 (dirty value)
t5   | COMMIT;                       |
t6   |                               | SELECT balance FROM accounts
     |                               | WHERE id = 1;
     |                               | → Returns 500 (now committed)
```

🏗️ **ARCHITECT**: This prevents **catastrophic cascading rollbacks**.

```java
// Scenario: Payment Processing
@Service
public class PaymentService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processPayment(Long orderId, BigDecimal amount) {

        // Transaction A: Process payment
        Payment payment = new Payment(orderId, amount, Status.PENDING);
        paymentRepo.save(payment);

        // Call external payment gateway
        PaymentResult result = paymentGateway.charge(amount);

        if (result.isSuccess()) {
            payment.setStatus(Status.COMPLETED);
            paymentRepo.save(payment);
            // COMMIT
        } else {
            // ROLLBACK - payment status never becomes visible
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void checkPaymentStatus(Long orderId) {
        // This will NEVER see Status.PENDING from concurrent transaction
        // Only sees COMPLETED (committed) or nothing (not yet committed)
        Payment payment = paymentRepo.findByOrderId(orderId);

        // Safe to make decisions based on status
        if (payment.getStatus() == Status.COMPLETED) {
            shipmentService.shipOrder(orderId);
        }
    }
}
```

**What if we allowed dirty reads?**
```java
// DISASTER SCENARIO (if dirty reads were allowed)

// Transaction A starts payment, writes PENDING (not committed)
// Transaction B reads PENDING, ships product
// Transaction A payment fails, ROLLBACK
// → Product shipped but payment never went through! 💥
```

---

### 2. **No Dirty Writes**

**Definition**: A transaction cannot overwrite data written by another uncommitted transaction.

```sql
-- Timeline: Preventing Lost Updates

Time | Transaction A                  | Transaction B
-----|-------------------------------|--------------------------------
t1   | BEGIN;                        |
t2   | UPDATE accounts               |
     | SET balance = balance - 100   |
     | WHERE id = 1;                 |
     | (lock acquired, uncommitted)  |
t3   |                               | BEGIN;
     |                               | UPDATE accounts
     |                               | SET balance = balance + 50
     |                               | WHERE id = 1;
     |                               | (BLOCKED - waits for lock)
t4   | COMMIT;                       |
     | (lock released)               |
t5   |                               | (now proceeds with update)
     |                               | COMMIT;
```

🏗️ **ARCHITECT**: Without this, **concurrent updates would be lost**.

```java
// Example: Concurrent Inventory Updates
@Service
public class InventoryService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void reserveItem(Long productId, int quantity) {

        Product product = productRepo.findById(productId)
            .orElseThrow();

        // This UPDATE acquires a write lock
        // Prevents other transactions from updating until commit
        product.setStock(product.getStock() - quantity);
        productRepo.save(product);

        // COMMIT releases lock
    }

    // Concurrent calls to reserveItem() will serialize
    // preventing lost updates
}
```

**Preventing Lost Update Anomaly:**
```
Initial stock: 100

WITHOUT dirty write prevention:
- Transaction A: stock = 100 - 10 = 90 (uncommitted)
- Transaction B: stock = 100 - 20 = 80 (overwrites A's change!)
- Transaction A: COMMIT (result: 90)
- Transaction B: COMMIT (result: 80)
- Expected: 70, Actual: 80 → Lost 10 units! ❌

WITH dirty write prevention (READ COMMITTED):
- Transaction A: stock = 100 - 10 = 90, acquires lock
- Transaction B: WAITS for lock
- Transaction A: COMMIT, releases lock (stock now 90)
- Transaction B: stock = 90 - 20 = 70, COMMIT
- Result: 70 ✅
```

---

## Implementation Mechanisms

🎓 **PROFESSOR**: Databases implement Read Committed using **two primary mechanisms**.

### Mechanism 1: **Row-Level Locks** (Traditional Approach)

```
┌─────────────────────────────────────────────────┐
│ WRITE OPERATIONS (UPDATE/DELETE/INSERT)         │
│ → Acquire EXCLUSIVE LOCK on row                 │
│ → Hold lock until COMMIT/ROLLBACK               │
│ → Other transactions WAIT                       │
└─────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────┐
│ READ OPERATIONS (SELECT)                        │
│ → Acquire SHARED LOCK on row                    │
│ → Release lock IMMEDIATELY after read           │
│ → No long-held locks (prevents deadlocks)       │
└─────────────────────────────────────────────────┘
```

**Code Visualization:**
```python
class ReadCommittedLockBased:
    """
    Simplified implementation using locks
    """

    def __init__(self):
        self.locks = {}  # row_id -> (lock_type, owner_tx)
        self.data = {}   # row_id -> committed_value

    def read(self, tx_id: int, row_id: str):
        """
        Read with short-duration shared lock
        """
        # Acquire shared lock
        self._acquire_shared_lock(row_id, tx_id)

        # Read committed value
        value = self.data.get(row_id)

        # Release lock immediately (key difference from REPEATABLE READ)
        self._release_lock(row_id, tx_id)

        return value

    def write(self, tx_id: int, row_id: str, new_value):
        """
        Write with exclusive lock held until commit
        """
        # Acquire exclusive lock (blocks if other tx has lock)
        self._acquire_exclusive_lock(row_id, tx_id)

        # Write to transaction-local buffer (not visible yet)
        self._write_to_tx_buffer(tx_id, row_id, new_value)

        # Lock held until commit()

    def commit(self, tx_id: int):
        """
        Make changes visible and release locks
        """
        # Move buffered writes to committed storage
        for row_id, value in self._get_tx_buffer(tx_id):
            self.data[row_id] = value

        # Release all locks held by this transaction
        self._release_all_locks(tx_id)
```

---

### Mechanism 2: **Multi-Version Concurrency Control (MVCC)** (Modern Approach)

🏗️ **ARCHITECT**: PostgreSQL, Oracle, MySQL all use **MVCC** for better performance.

**Key Idea**: Keep **multiple versions** of each row. Readers see appropriate version.

```
┌─────────────────────────────────────────────────────────────┐
│ Row Versions Timeline:                                      │
│                                                              │
│ accounts (id=1):                                             │
│   v1: balance=1000, xmin=100, xmax=∞       (committed)      │
│   v2: balance=900,  xmin=105, xmax=∞       (committed)      │
│   v3: balance=800,  xmin=110, xmax=NULL    (uncommitted)    │
│                                                              │
│ Transaction 112 reads:                                       │
│   → Sees v2 (balance=900)                                   │
│   → v3 is invisible (uncommitted by tx 110)                 │
│                                                              │
│ Transaction 110 commits:                                     │
│   → v3 becomes visible to new transactions                  │
└─────────────────────────────────────────────────────────────┘
```

**Implementation:**
```java
// Simplified MVCC for Read Committed
public class MVCCReadCommitted {

    static class RowVersion {
        Object value;
        long xmin;  // Transaction ID that created this version
        long xmax;  // Transaction ID that deleted/updated this version

        boolean isVisibleTo(long readerTxId, Set<Long> committedTxIds) {
            // Version created by committed transaction
            boolean created = committedTxIds.contains(xmin);

            // Version not yet deleted, or deleted by uncommitted tx
            boolean notDeleted = (xmax == Long.MAX_VALUE) ||
                                 !committedTxIds.contains(xmax);

            return created && notDeleted;
        }
    }

    private Map<String, List<RowVersion>> versionChains = new ConcurrentHashMap<>();
    private Set<Long> committedTransactions = ConcurrentHashMap.newKeySet();

    public Object read(long txId, String rowId) {
        List<RowVersion> versions = versionChains.get(rowId);

        if (versions == null) {
            return null;
        }

        // Find the latest version visible to this transaction
        // In READ COMMITTED, we check committed status at READ time (not tx start)
        Set<Long> committed = new HashSet<>(committedTransactions);

        for (int i = versions.size() - 1; i >= 0; i--) {
            RowVersion version = versions.get(i);
            if (version.isVisibleTo(txId, committed)) {
                return version.value;
            }
        }

        return null;  // No visible version
    }

    public void write(long txId, String rowId, Object newValue) {
        List<RowVersion> versions = versionChains.computeIfAbsent(
            rowId, k -> new CopyOnWriteArrayList<>()
        );

        // Mark previous version as deleted by this transaction
        if (!versions.isEmpty()) {
            RowVersion latest = versions.get(versions.size() - 1);
            latest.xmax = txId;
        }

        // Create new version
        RowVersion newVersion = new RowVersion();
        newVersion.value = newValue;
        newVersion.xmin = txId;
        newVersion.xmax = Long.MAX_VALUE;

        versions.add(newVersion);
    }

    public void commit(long txId) {
        committedTransactions.add(txId);
    }

    public void rollback(long txId) {
        // Remove versions created by this transaction
        versionChains.values().forEach(versions ->
            versions.removeIf(v -> v.xmin == txId)
        );
    }
}
```

**MVCC Benefits:**
```
✅ Readers don't block writers
✅ Writers don't block readers
✅ No shared locks needed for SELECT
✅ Better concurrency than lock-based approach

Trade-off:
❌ More storage (multiple versions)
❌ Vacuum/cleanup needed (remove old versions)
```

---

## Allowed Anomalies and Implications

🎓 **PROFESSOR**: READ COMMITTED **allows two anomalies** that can surprise developers.

### Anomaly 1: **Non-Repeatable Reads** (Read Skew)

**Definition**: Reading the same row twice in a transaction returns different values.

```sql
-- Transaction A
BEGIN;
SELECT balance FROM accounts WHERE id = 1;  -- Returns $1000

-- Transaction B updates and commits
-- UPDATE accounts SET balance = 500 WHERE id = 1; COMMIT;

SELECT balance FROM accounts WHERE id = 1;  -- Returns $500 (different!)
COMMIT;
```

🏗️ **ARCHITECT**: This causes **subtle bugs** in multi-step operations.

**Real-World Bug - Banking Transfer:**
```java
@Service
public class BuggyTransferService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        // Step 1: Check balance
        Account from = accountRepo.findById(fromId).orElseThrow();
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        // ⚠️ CONCURRENT TRANSACTION: Withdraws $900 from 'from' account

        // Step 2: Perform transfer (balance changed since check!)
        from.setBalance(from.getBalance().subtract(amount));  // May go negative!
        Account to = accountRepo.findById(toId).orElseThrow();
        to.setBalance(to.getBalance().add(amount));

        accountRepo.saveAll(List.of(from, to));
        // COMMIT - Account can now be negative! 💥
    }
}
```

**Fix Options:**

```java
// Option 1: Use REPEATABLE READ isolation
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void transfer(...) {
    // Snapshot established at BEGIN - balance won't change
}

// Option 2: Use SELECT FOR UPDATE (pessimistic locking)
@Transactional(isolation = Isolation.READ_COMMITTED)
public void transfer(Long fromId, Long toId, BigDecimal amount) {
    // Lock the row for duration of transaction
    Account from = accountRepo.findByIdForUpdate(fromId);
    // No other transaction can modify 'from' until we commit
    // ...
}

// Option 3: Use optimistic locking with @Version
@Entity
public class Account {
    @Version
    private Long version;  // Incremented on each update

    // Save will throw OptimisticLockException if version changed
}

// Option 4: Database constraint (last resort)
ALTER TABLE accounts ADD CONSTRAINT check_positive_balance
CHECK (balance >= 0);
```

---

### Anomaly 2: **Phantom Reads**

**Definition**: Range queries can return different rows on repeated execution.

```sql
-- Transaction A
BEGIN;
SELECT COUNT(*) FROM accounts WHERE balance > 1000;  -- Returns 5

-- Transaction B inserts new row
-- INSERT INTO accounts (id, balance) VALUES (999, 2000); COMMIT;

SELECT COUNT(*) FROM accounts WHERE balance > 1000;  -- Returns 6 (phantom!)
COMMIT;
```

🏗️ **ARCHITECT**: This affects **reporting** and **aggregation logic**.

**Real-World Issue - Report Generation:**
```java
@Service
public class ReportService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public FinancialReport generateMonthlyReport(YearMonth month) {

        // Query 1: Count transactions
        long txCount = transactionRepo.countByMonth(month);

        // Query 2: Sum amounts
        BigDecimal totalAmount = transactionRepo.sumAmountsByMonth(month);

        // ⚠️ NEW TRANSACTION committed between queries!

        // Query 3: Average
        BigDecimal average = totalAmount.divide(
            BigDecimal.valueOf(txCount), 2, RoundingMode.HALF_UP
        );  // Division by zero or incorrect average! 💥

        return new FinancialReport(txCount, totalAmount, average);
    }
}
```

**Fix:**
```java
// Use REPEATABLE READ to ensure consistent snapshot
@Transactional(isolation = Isolation.REPEATABLE_READ)
public FinancialReport generateMonthlyReport(YearMonth month) {
    // All queries see same snapshot - no phantoms
    // ...
}

// OR: Use single query to avoid multi-step issue
@Transactional(isolation = Isolation.READ_COMMITTED)
public FinancialReport generateMonthlyReport(YearMonth month) {
    // Atomic query - no inconsistency possible
    return transactionRepo.getMonthlyStatistics(month);
}
```

---

## Real-World Use Cases

🏗️ **ARCHITECT**: When to use **READ COMMITTED**?

### ✅ Perfect For:

**1. Stateless REST APIs**
```java
@RestController
public class UserController {

    // Each request is independent transaction
    @GetMapping("/users/{id}")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserDTO getUser(@PathVariable Long id) {
        User user = userRepo.findById(id).orElseThrow();
        return new UserDTO(user);
    }

    // No cross-request consistency needed
    @PostMapping("/users")
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public UserDTO createUser(@RequestBody CreateUserRequest request) {
        User user = new User(request.getName(), request.getEmail());
        return new UserDTO(userRepo.save(user));
    }
}
```

**2. High-Throughput Write Systems**
```java
@Service
public class EventIngestionService {

    // Millions of events/sec - need maximum throughput
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void ingestEvent(Event event) {
        eventRepo.save(event);
        // No consistency required across events
    }
}
```

**3. Eventually Consistent Systems**
```java
@Service
public class CacheWarmerService {

    // Approximate counts acceptable
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void updateUserCountCache() {
        long count = userRepo.count();  // May be slightly stale
        cacheService.set("user_count", count, Duration.ofMinutes(5));
    }
}
```

---

### ❌ NOT Suitable For:

**1. Multi-Step Workflows Requiring Consistency**
```java
// BAD: Using READ COMMITTED for financial calculation
@Transactional(isolation = Isolation.READ_COMMITTED)
public void calculatePortfolioValue(Long userId) {
    // Query 1: Get all holdings
    List<Holding> holdings = holdingRepo.findByUserId(userId);

    BigDecimal total = BigDecimal.ZERO;
    for (Holding holding : holdings) {
        // Query 2: Get current price (may have changed since query 1!)
        BigDecimal price = priceService.getCurrentPrice(holding.getSymbol());
        total = total.add(price.multiply(holding.getQuantity()));
    }

    // Inconsistent total! Prices changed during calculation
}

// BETTER: Use REPEATABLE READ
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void calculatePortfolioValue(Long userId) {
    // Snapshot ensures consistent prices
}
```

**2. Reporting / Analytics Requiring Point-in-Time Snapshot**
```java
// BAD: Quarterly financial report with READ COMMITTED
@Transactional(isolation = Isolation.READ_COMMITTED)
public QuarterlyReport generateReport(Quarter quarter) {
    // Each query sees different data - report inconsistent!
    long revenue = revenueRepo.sumByQuarter(quarter);
    long expenses = expenseRepo.sumByQuarter(quarter);
    long profit = revenue - expenses;  // Incorrect!
}

// BETTER: Use REPEATABLE READ for consistent snapshot
```

**3. Distributed Transactions Requiring Serializability**
```java
// BAD: Seat booking with READ COMMITTED
@Transactional(isolation = Isolation.READ_COMMITTED)
public void bookSeat(Long flightId, String seatNumber, Long userId) {
    // Check availability
    boolean available = !reservationRepo.existsByFlightAndSeat(flightId, seatNumber);

    // ⚠️ RACE CONDITION: Another transaction can book between check and insert

    if (available) {
        reservationRepo.save(new Reservation(flightId, seatNumber, userId));
    }
}

// BETTER: Use SERIALIZABLE or SELECT FOR UPDATE
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Key Questions:
1. Are operations stateless (REST API) or stateful (multi-step wizard)?
   → Stateless: READ COMMITTED works
   → Stateful: Consider REPEATABLE READ

2. Can we tolerate non-repeatable reads?
   → Yes: READ COMMITTED
   → No: Higher isolation

3. What's the read:write ratio?
   → Read-heavy: MVCC-based READ COMMITTED excellent
   → Write-heavy: Minimal overhead from READ COMMITTED

4. Are there critical invariants to maintain?
   → Yes: May need SELECT FOR UPDATE or higher isolation
   → No: READ COMMITTED sufficient

Example:
"For a social media feed API, READ COMMITTED is perfect.
Each request is independent, and feed consistency across
multiple loads is not critical."
```

### 2. Capacity Estimation (RADIO: Scale)

```
Transaction Throughput (READ COMMITTED):
- Baseline: 10,000 TPS (lock-based)
- MVCC optimization: 15,000 TPS (30% improvement)
- Compared to SERIALIZABLE: 3x higher throughput

Lock Contention:
- Hot row updates: 100/sec
- Average lock hold time: 10ms
- Concurrent writers: ~1-2 (minimal blocking)

MVCC Storage Overhead:
- Average row size: 500 bytes
- Versions per row: 2-3 (with aggressive vacuuming)
- Overhead: 1000-1500 bytes per row
- Total storage: 2-3x base data size
```

### 3. Data Model (RADIO: Data-Model)

```java
// Domain Model: Optimized for Read Committed

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private BigDecimal balance;

    @Version  // Optimistic locking (alternative to higher isolation)
    private Long version;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    // Business methods with explicit locking when needed
    public void debit(BigDecimal amount) {
        if (this.balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        this.balance = this.balance.subtract(amount);
    }
}

// Repository with explicit lock control
@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    // Default: No lock (READ COMMITTED reads latest committed)
    Optional<Account> findById(Long id);

    // Pessimistic lock when needed
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    // Optimistic lock (version check)
    @Modifying
    @Query("UPDATE Account a SET a.balance = :balance, a.version = a.version + 1 " +
           "WHERE a.id = :id AND a.version = :version")
    int updateBalance(@Param("id") Long id,
                      @Param("balance") BigDecimal balance,
                      @Param("version") Long version);
}

// Service layer: Explicit transaction control
@Service
public class AccountService {

    // Fast read - READ COMMITTED default
    @Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
    public AccountDTO getAccount(Long id) {
        Account account = accountRepo.findById(id).orElseThrow();
        return new AccountDTO(account);
    }

    // Write with pessimistic lock when needed
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        // Acquire locks to prevent non-repeatable reads
        Account from = accountRepo.findByIdForUpdate(fromId).orElseThrow();
        Account to = accountRepo.findByIdForUpdate(toId).orElseThrow();

        from.debit(amount);
        to.credit(amount);

        accountRepo.saveAll(List.of(from, to));
    }

    // Optimistic locking approach
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void transferOptimistic(Long fromId, Long toId, BigDecimal amount) {
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            try {
                Account from = accountRepo.findById(fromId).orElseThrow();
                Account to = accountRepo.findById(toId).orElseThrow();

                from.debit(amount);
                to.credit(amount);

                accountRepo.saveAll(List.of(from, to));
                return;  // Success

            } catch (OptimisticLockException e) {
                if (i == maxRetries - 1) throw e;
                // Retry with exponential backoff
                Thread.sleep((long) Math.pow(2, i) * 100);
            }
        }
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────────────────┐
│                     APPLICATION TIER                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ REST API   │  │ Service    │  │ Repository │            │
│  │ Controller │→ │ Layer      │→ │ (JPA)      │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└────────────────────────────┬────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│                    CONNECTION POOL                          │
│  [Conn1: TX1] [Conn2: TX2] [Conn3: TX3] ... [ConnN: TXN]   │
└────────────────────────────┬────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│                  DATABASE (PostgreSQL)                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  MVCC Engine (Read Committed Implementation)        │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐    │   │
│  │  │  Row       │  │ Visibility │  │   Lock     │    │   │
│  │  │  Versions  │  │ Checker    │  │   Manager  │    │   │
│  │  │ (xmin/max) │  │ (xid check)│  │ (X locks)  │    │   │
│  │  └────────────┘  └────────────┘  └────────────┘    │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Storage Layer                                       │   │
│  │  [Table Pages] [WAL] [Visibility Map] [FSM]         │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

Transaction Flow:
1. BEGIN → Assign transaction ID (xid)
2. SELECT → Read latest committed version (check xmin/xmax)
3. UPDATE → Create new version, set xmin=current_xid
4. COMMIT → Mark xid as committed in CLOG
```

### 5. Deep Dives (RADIO: Optimize)

**A. Handling Non-Repeatable Reads**

```java
// Pattern 1: Batch operations in single query
@Transactional(isolation = Isolation.READ_COMMITTED)
public void processBatch(List<Long> ids) {
    // GOOD: Single query - no non-repeatable read issue
    List<Account> accounts = accountRepo.findAllById(ids);
    accounts.forEach(account -> account.applyInterest());
    accountRepo.saveAll(accounts);
}

// Pattern 2: Use SELECT FOR UPDATE for critical sections
@Transactional(isolation = Isolation.READ_COMMITTED)
public void criticalUpdate(Long id) {
    // Lock row for transaction duration
    Account account = accountRepo.findByIdForUpdate(id).orElseThrow();
    account.performCriticalOperation();
    accountRepo.save(account);
}

// Pattern 3: Application-level snapshot
@Service
public class SnapshotService {

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void processWithSnapshot(Long userId) {
        // Create in-memory snapshot at transaction start
        UserSnapshot snapshot = new UserSnapshot(
            userRepo.findById(userId).orElseThrow()
        );

        // Use snapshot for all operations
        // Insulates from non-repeatable reads
        BigDecimal total = calculateTotal(snapshot);
        updateUser(userId, total);
    }
}
```

**B. Monitoring and Observability**

```java
@Component
public class ReadCommittedMetrics {

    private final MeterRegistry registry;

    @EventListener
    public void onTransaction(TransactionEvent event) {
        Timer.Sample sample = Timer.start(registry);

        event.onComplete(() -> {
            sample.stop(Timer.builder("db.transaction.duration")
                .tag("isolation", "READ_COMMITTED")
                .tag("status", event.getStatus())
                .register(registry));
        });
    }

    @Scheduled(fixedRate = 60000)
    public void recordActiveTransactions() {
        long count = dataSource.getActiveTransactionCount();
        registry.gauge("db.transactions.active",
            Tags.of("isolation", "READ_COMMITTED"), count);
    }

    @EventListener
    public void onOptimisticLockFailure(OptimisticLockEvent event) {
        registry.counter("db.optimistic_lock.failures",
            "entity", event.getEntityName()
        ).increment();
    }
}
```

**C. Database-Specific Optimizations**

```sql
-- PostgreSQL: Enable synchronous commit for durability
SET synchronous_commit = ON;

-- PostgreSQL: Adjust autovacuum for MVCC cleanup
ALTER TABLE accounts SET (
    autovacuum_vacuum_scale_factor = 0.1,
    autovacuum_analyze_scale_factor = 0.05
);

-- MySQL InnoDB: Adjust transaction isolation (default is REPEATABLE READ)
SET SESSION TRANSACTION ISOLATION LEVEL READ COMMITTED;

-- SQL Server: Enable row versioning for better concurrency
ALTER DATABASE MyDB
SET READ_COMMITTED_SNAPSHOT ON;
-- Now SELECT doesn't block UPDATE (MVCC behavior)

-- Oracle: Already uses MVCC by default for READ COMMITTED
-- No configuration needed
```

---

## 🧠 MIND MAP: READ COMMITTED

```
                    READ COMMITTED
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   GUARANTEES        IMPLEMENTATION      USE CASES
        |                 |                  |
   ┌────┴────┐       ┌────┴────┐       ┌────┴────┐
   ↓         ↓       ↓         ↓       ↓         ↓
  No      No Dirty  MVCC     Lock    REST API  Event
 Dirty    Writes   (Postgres) Based           Ingestion
 Reads            (xmin/xmax) (MySQL)
   |                 |                  |
Committed  Exclusive Row      Default   High
 Only      Lock      Versions  Choice   Throughput

        ┌─────────────────┐
        ↓                 ↓
    ALLOWED         MITIGATIONS
    ANOMALIES
        |                 |
   ┌────┴────┐       ┌────┴────┐
   ↓         ↓       ↓         ↓
Non-Rep   Phantom  SELECT   Optimistic
Reads     Reads    FOR      Locking
                   UPDATE   (@Version)
```

### 💡 EMOTIONAL ANCHORS (For Subconscious Power)

1. **Read Committed = News Website 📰**
   - You only see published articles (committed)
   - Articles can update between page loads (non-repeatable)
   - New articles appear (phantoms)
   - Good enough for browsing!

2. **MVCC = Newspaper Editions 🗞️**
   - Morning edition: Version 1
   - Evening edition: Version 2
   - You read morning edition (old version)
   - Others read evening edition (new version)
   - Both valid simultaneously!

3. **No Dirty Reads = Published Books Only 📚**
   - You can't read author's draft (uncommitted)
   - Only see books that passed editing (committed)
   - Prevents reading content that might be removed

4. **SELECT FOR UPDATE = Reserving Library Book 📖**
   - Put "on hold" sign (lock)
   - Nobody else can take it
   - Return when done (commit)

---

## 🎯 INTERVIEW GOLD: KEY TALKING POINTS

1. **Why it's the default**: "READ COMMITTED is the sweet spot - prevents the most dangerous anomaly (dirty reads) while allowing high concurrency."

2. **MVCC advantage**: "PostgreSQL's MVCC implementation means readers never block writers. A long-running SELECT won't stop UPDATE queries."

3. **When to upgrade**: "I use READ COMMITTED for stateless APIs. For reports or multi-step business logic, I upgrade to REPEATABLE READ."

4. **Real bug story**: "I once debugged a race condition in account transfer code. Balance check passed, but concurrent withdrawal happened before debit. Solution: SELECT FOR UPDATE."

5. **Performance numbers**: "In our benchmarks, MVCC-based READ COMMITTED achieved 15K TPS vs 10K with lock-based approach - 50% improvement."

6. **Practical pattern**: "For occasional consistency needs, I use SELECT FOR UPDATE on specific rows rather than raising isolation level globally."

---

## 📚 FURTHER READING

**Database Documentation:**
- PostgreSQL MVCC: https://www.postgresql.org/docs/current/mvcc-intro.html
- MySQL InnoDB Locking: https://dev.mysql.com/doc/refman/8.0/en/innodb-locking.html

**Academic Papers:**
- "Concurrency Control and Recovery in Database Systems" (Bernstein & Goodman)

**Books:**
- "Designing Data-Intensive Applications" by Martin Kleppmann (Chapter 7)
