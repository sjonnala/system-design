# Snapshot Isolation Deep Dive

## Contents

- [Snapshot Isolation Deep Dive](#snapshot-isolation-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Snapshot Isolation Guarantee](#the-snapshot-isolation-guarantee)
    - [MVCC Implementation Details](#mvcc-implementation-details)
    - [Write Skew Anomaly](#write-skew-anomaly)
    - [Performance Characteristics](#performance-characteristics)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: SNAPSHOT ISOLATION](#mind-map-snapshot-isolation)

## Core Mental Model

```text
SNAPSHOT ISOLATION = "Every transaction operates on a consistent snapshot of the database"

The Core Promise:
┌──────────────────────────────────────────────────────────┐
│ A transaction sees the database as it was at             │
│ transaction START, frozen in time.                       │
│                                                           │
│ - Reads always return consistent snapshot                │
│ - No dirty reads, no non-repeatable reads, no phantoms  │
│ - Writes only visible after commit                       │
└──────────────────────────────────────────────────────────┘

BUT: NOT equivalent to SERIALIZABLE!
❌ Still allows Write Skew anomaly
```

**Relationship to SQL Standards:**
```
┌────────────────────────────────────────────────────┐
│ SQL Standard          Database Implementation     │
├────────────────────────────────────────────────────┤
│ REPEATABLE READ   →   Snapshot Isolation          │
│                       (PostgreSQL, Oracle, MSSQL) │
│                                                    │
│ MySQL InnoDB      →   Snapshot Isolation          │
│ (REPEATABLE READ)     + Next-Key Locks            │
│                       (prevents some write skews) │
└────────────────────────────────────────────────────┘
```

---

## The Snapshot Isolation Guarantee

🎓 **PROFESSOR**: Snapshot Isolation (SI) provides **three strong guarantees**:

### 1. **Consistent Read View**

Every transaction operates on a **point-in-time snapshot** taken at `BEGIN TRANSACTION`.

```sql
-- Transaction A
BEGIN;  -- Snapshot taken at timestamp T=100

SELECT balance FROM accounts WHERE id = 1;  -- Returns $1000

-- Transaction B (concurrent): Updates and commits at T=105
-- UPDATE accounts SET balance = 500 WHERE id = 1; COMMIT;

SELECT balance FROM accounts WHERE id = 1;  -- Still $1000 (from snapshot!)

SELECT COUNT(*) FROM accounts WHERE balance > 500;  -- Consistent with first query

COMMIT;
```

**The Guarantee:** All reads see a **transactionally consistent** state.

```java
@Transactional(isolation = Isolation.REPEATABLE_READ)  // Maps to SI
public FinancialReport generateReport(Long userId) {

    // Snapshot established here - all queries see same point in time

    // Query 1: Sum of all account balances
    BigDecimal totalBalance = accountRepo.sumBalancesByUser(userId);

    // Query 2: Count of accounts
    long accountCount = accountRepo.countByUser(userId);

    // Query 3: Average balance
    BigDecimal avgBalance = totalBalance.divide(
        BigDecimal.valueOf(accountCount),
        RoundingMode.HALF_UP
    );

    // ✅ All queries consistent - no phantoms, no skew
    // Even if concurrent transactions commit, we see snapshot from BEGIN

    return new FinancialReport(totalBalance, accountCount, avgBalance);
}
```

---

### 2. **No Dirty Reads**

Transactions **never** see uncommitted changes from other transactions.

```
Timeline:
T=100: TX1 BEGIN (snapshot = T100)
T=101: TX2 BEGIN (snapshot = T101)
T=102: TX2 UPDATE accounts SET balance = 500 WHERE id = 1 (uncommitted)
T=103: TX1 SELECT balance FROM accounts WHERE id = 1
       → Returns value from snapshot (T=100), NOT TX2's update
T=104: TX2 COMMIT
T=105: TX1 SELECT balance FROM accounts WHERE id = 1
       → Still returns T=100 snapshot (TX2's commit at T=104 not visible)
```

---

### 3. **First-Committer-Wins Rule**

When two transactions **concurrently modify the same row**, one must abort.

```sql
-- Transaction A
BEGIN;  -- Snapshot at T=100
UPDATE accounts SET balance = balance - 100 WHERE id = 1;
-- Lock acquired on row (id=1)

-- Transaction B (concurrent)
BEGIN;  -- Snapshot at T=101
UPDATE accounts SET balance = balance + 50 WHERE id = 1;
-- BLOCKED waiting for TX A's lock

-- Transaction A commits
COMMIT;  -- T=105, lock released

-- Transaction B now proceeds
-- But sees conflict: row modified since its snapshot (T=101)
-- ABORTS with: "could not serialize access due to concurrent update"
```

🏗️ **ARCHITECT**: This prevents **lost updates** automatically!

```java
@Service
public class AccountService {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void withdraw(Long accountId, BigDecimal amount) {

        Account account = accountRepo.findById(accountId).orElseThrow();

        // Check balance from snapshot
        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        // Update: creates new version with write lock
        account.setBalance(account.getBalance().subtract(amount));
        accountRepo.save(account);

        // COMMIT: If another transaction modified this row
        // since our snapshot, we'll get serialization failure
    }

    // Caller should handle retry
    @Retryable(
        value = { SerializationException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void withdrawWithRetry(Long accountId, BigDecimal amount) {
        withdraw(accountId, amount);
    }
}
```

---

## MVCC Implementation Details

🎓 **PROFESSOR**: Snapshot Isolation is implemented via **Multi-Version Concurrency Control (MVCC)**.

### Core MVCC Concepts:

```
┌──────────────────────────────────────────────────────────┐
│ 1. Each row has MULTIPLE VERSIONS                        │
│ 2. Each version tagged with transaction ID (xid)         │
│ 3. Each transaction gets a "snapshot" (list of xids)     │
│ 4. Visibility rules determine which version to read      │
└──────────────────────────────────────────────────────────┘
```

**Row Version Structure:**
```java
public class RowVersion {
    long xmin;      // Transaction ID that created this version
    long xmax;      // Transaction ID that deleted/updated (or MAX_VALUE)
    byte[] data;    // Actual row data

    boolean isVisibleTo(long snapshotXid, Set<Long> activeXids) {
        // Version created by committed transaction before snapshot
        boolean createdBefore = (xmin < snapshotXid) &&
                                !activeXids.contains(xmin);

        // Version not deleted, or deleted after snapshot
        boolean notDeleted = (xmax == Long.MAX_VALUE) ||
                             (xmax >= snapshotXid) ||
                             activeXids.contains(xmax);

        return createdBefore && notDeleted;
    }
}
```

### PostgreSQL MVCC Implementation:

```python
class PostgreSQLSnapshotIsolation:
    """
    Simplified implementation of PostgreSQL's MVCC
    """

    def __init__(self):
        self.xid_counter = 0  # Global transaction ID counter
        self.version_store = {}  # row_id -> [RowVersion]
        self.active_snapshots = {}  # tx_id -> Snapshot

    def begin_transaction(self):
        """
        Create snapshot at transaction start
        """
        xid = self.next_xid()

        # Snapshot = current xid + list of active transactions
        snapshot = Snapshot(
            xid=xid,
            xmin=min(active_tx.xid for active_tx in self.active_snapshots.values())
                if self.active_snapshots else xid,
            xmax=xid,
            active_xids=set(self.active_snapshots.keys())
        )

        self.active_snapshots[xid] = snapshot
        return xid, snapshot

    def read(self, tx_id: int, row_id: str):
        """
        Read with snapshot isolation
        """
        snapshot = self.active_snapshots[tx_id]
        versions = self.version_store.get(row_id, [])

        # Find latest visible version
        for version in reversed(versions):  # Newest to oldest
            if self.is_visible(version, snapshot):
                return version.data

        return None  # No visible version

    def is_visible(self, version: RowVersion, snapshot: Snapshot) -> bool:
        """
        PostgreSQL visibility rules
        """
        # Rule 1: Version created by our own transaction
        if version.xmin == snapshot.xid:
            return version.xmax == float('inf')

        # Rule 2: Version created by transaction that committed before snapshot
        if version.xmin < snapshot.xmin:
            if version.xmax == float('inf'):
                return True
            if version.xmax >= snapshot.xmax:
                return True
            if version.xmax in snapshot.active_xids:
                return True
            return False

        # Rule 3: Version created by active transaction (not visible)
        if version.xmin in snapshot.active_xids:
            return False

        # Rule 4: Version created after snapshot
        if version.xmin >= snapshot.xmax:
            return False

        # Rule 5: Version created by committed transaction in snapshot range
        if version.xmax == float('inf'):
            return True
        if version.xmax >= snapshot.xmax:
            return True
        if version.xmax in snapshot.active_xids:
            return True

        return False

    def write(self, tx_id: int, row_id: str, new_data: bytes):
        """
        Create new version for update
        """
        versions = self.version_store.get(row_id, [])

        # Mark current version as deleted by this transaction
        if versions:
            current = versions[-1]
            current.xmax = tx_id

        # Create new version
        new_version = RowVersion(
            xmin=tx_id,
            xmax=float('inf'),
            data=new_data
        )

        versions.append(new_version)
        self.version_store[row_id] = versions

    def commit(self, tx_id: int):
        """
        Commit transaction - versions become visible
        """
        del self.active_snapshots[tx_id]
        # Versions with xmin=tx_id now visible to future transactions

    def rollback(self, tx_id: int):
        """
        Rollback - remove versions created by this transaction
        """
        for row_id, versions in self.version_store.items():
            # Remove versions created by this transaction
            self.version_store[row_id] = [
                v for v in versions if v.xmin != tx_id
            ]
            # Restore xmax for versions deleted by this transaction
            for v in self.version_store[row_id]:
                if v.xmax == tx_id:
                    v.xmax = float('inf')

        del self.active_snapshots[tx_id]
```

### MVCC Benefits:

```
✅ Readers NEVER block writers
✅ Writers NEVER block readers
✅ No shared locks for SELECT
✅ Consistent reads without locking
✅ High concurrency

Trade-offs:
❌ Storage overhead (multiple versions)
❌ Vacuum/cleanup required
❌ Write-write conflicts abort transactions
```

---

## Write Skew Anomaly

🎓 **PROFESSOR**: Snapshot Isolation is **NOT serializable** due to **write skew**.

### What is Write Skew?

**Definition**: Two transactions read **overlapping data**, make **non-overlapping writes**, violating a **constraint** that depends on both writes.

**Classic Example - On-Call Doctors:**

```sql
-- Constraint: At least 1 doctor must be on-call

-- Initial state:
-- doctors: id=1 (Alice, on_call=true), id=2 (Bob, on_call=true)

-- Transaction A (Alice requests off)
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT COUNT(*) FROM doctors WHERE on_call = true;  -- Returns 2 (Alice + Bob)
-- Alice thinks: "Bob is on-call, I can go off"
UPDATE doctors SET on_call = false WHERE id = 1;  -- Alice off
COMMIT;

-- Transaction B (Bob requests off) - CONCURRENT
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ;
SELECT COUNT(*) FROM doctors WHERE on_call = true;  -- Returns 2 (from snapshot!)
-- Bob thinks: "Alice is on-call, I can go off"
UPDATE doctors SET on_call = false WHERE id = 2;  -- Bob off
COMMIT;

-- Result: 0 doctors on-call! Constraint violated! ❌
```

**Why Snapshot Isolation Allows This:**
1. Both transactions read at their snapshot times (both see 2 doctors)
2. Writes are to **different rows** (no write-write conflict)
3. First-committer-wins only detects **same-row conflicts**
4. Constraint spans multiple rows - not detected!

🏗️ **ARCHITECT**: Real-world write skew scenarios:

**1. Meeting Room Booking:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void bookRoom(Long roomId, LocalDateTime start, LocalDateTime end) {

    // Check for conflicts
    boolean hasConflict = bookingRepo.existsConflictingBooking(roomId, start, end);

    if (!hasConflict) {
        // ⚠️ WRITE SKEW: Two concurrent transactions
        // Both read no conflict (from snapshot)
        // Both insert new booking
        // Result: Double booking!
        bookingRepo.save(new Booking(roomId, start, end));
    }
}
```

**Fix - Use SERIALIZABLE or Explicit Locking:**
```java
// Option 1: SERIALIZABLE isolation (detects write skew)
@Transactional(isolation = Isolation.SERIALIZABLE)
public void bookRoom(...) {
    // Transaction will abort with serialization error
}

// Option 2: Explicit table-level lock
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void bookRoom(Long roomId, LocalDateTime start, LocalDateTime end) {
    // Lock the room for duration of transaction
    jdbcTemplate.execute("LOCK TABLE rooms IN EXCLUSIVE MODE");

    boolean hasConflict = bookingRepo.existsConflictingBooking(roomId, start, end);
    if (!hasConflict) {
        bookingRepo.save(new Booking(roomId, start, end));
    }
}

// Option 3: Materialized conflict (create dummy row to conflict on)
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void bookRoom(Long roomId, LocalDateTime start, LocalDateTime end) {
    // Create/update a "lock" row for this time slot
    TimeSlotLock lock = lockRepo.findOrCreateForSlot(roomId, start, end);
    lock.incrementBookingCount();
    lockRepo.save(lock);  // Write-write conflict if concurrent!

    bookingRepo.save(new Booking(roomId, start, end));
}
```

**2. Bank Account Constraint - Total Balance:**
```java
// Constraint: Sum of checking + savings >= 0

@Transactional(isolation = Isolation.REPEATABLE_READ)
public void withdrawFromChecking(Long userId, BigDecimal amount) {

    Account checking = accountRepo.findByUserAndType(userId, CHECKING);
    Account savings = accountRepo.findByUserAndType(userId, SAVINGS);

    // Check total balance
    BigDecimal total = checking.getBalance().add(savings.getBalance());

    if (total.compareTo(amount) >= 0) {
        // ⚠️ WRITE SKEW: Concurrent withdrawal from savings
        // Both see sufficient total balance
        // Both withdraw, violating constraint
        checking.setBalance(checking.getBalance().subtract(amount));
        accountRepo.save(checking);
    }
}
```

**Fix - Select Both Accounts for Update:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void withdrawFromChecking(Long userId, BigDecimal amount) {

    // Lock BOTH accounts
    Account checking = accountRepo.findByUserAndTypeForUpdate(userId, CHECKING);
    Account savings = accountRepo.findByUserAndTypeForUpdate(userId, SAVINGS);

    BigDecimal total = checking.getBalance().add(savings.getBalance());

    if (total.compareTo(amount) >= 0) {
        checking.setBalance(checking.getBalance().subtract(amount));
        accountRepo.save(checking);
    }
}
```

---

## Performance Characteristics

🏗️ **ARCHITECT**: Snapshot Isolation performance profile:

### Throughput Comparison:

```
┌────────────────────────────────────────────────────┐
│ Isolation Level      Throughput (relative)         │
├────────────────────────────────────────────────────┤
│ READ COMMITTED       100%    (baseline)            │
│ SNAPSHOT ISOLATION    85%    (15% overhead)        │
│ SERIALIZABLE (2PL)    40%    (60% overhead)        │
│ SERIALIZABLE (SSI)    70%    (30% overhead)        │
└────────────────────────────────────────────────────┘
```

### Storage Overhead:

```python
class MVCCStorageAnalysis:
    """
    Storage overhead analysis for Snapshot Isolation
    """

    def calculate_storage(self,
                          base_data_size: int,
                          avg_update_rate: float,
                          vacuum_interval: int) -> dict:
        """
        Estimate storage overhead

        Args:
            base_data_size: Original data size (bytes)
            avg_update_rate: Updates per second
            vacuum_interval: Seconds between vacuum runs
        """
        # Number of old versions accumulated
        versions_per_vacuum = avg_update_rate * vacuum_interval

        # Average version overhead (assuming 50% row churn)
        overhead_ratio = versions_per_vacuum * 0.5 / base_data_size

        total_storage = base_data_size * (1 + overhead_ratio)

        return {
            'base_size': base_data_size,
            'overhead_size': total_storage - base_data_size,
            'total_size': total_storage,
            'overhead_percentage': overhead_ratio * 100
        }

# Example:
analyzer = MVCCStorageAnalysis()
result = analyzer.calculate_storage(
    base_data_size=10_000_000,      # 10 GB
    avg_update_rate=1000,            # 1K updates/sec
    vacuum_interval=3600             # 1 hour vacuum
)
# Result: ~13.6 GB total (36% overhead)
```

### Vacuum/Cleanup Impact:

```java
@Component
public class MVCCMaintenanceMonitor {

    @Scheduled(cron = "0 0 * * * *")  // Every hour
    public void monitorVacuumHealth() {

        // Check dead tuple ratio
        String query = """
            SELECT schemaname, relname,
                   n_dead_tup,
                   n_live_tup,
                   round(n_dead_tup * 100.0 / NULLIF(n_live_tup + n_dead_tup, 0), 2) as dead_ratio
            FROM pg_stat_user_tables
            WHERE n_dead_tup > 1000
            ORDER BY n_dead_tup DESC
            LIMIT 10
            """;

        List<TableStats> stats = jdbcTemplate.query(query, this::mapTableStats);

        for (TableStats stat : stats) {
            if (stat.deadRatio > 20) {
                // High dead tuple ratio - vacuum needed
                logger.warn("Table {} has {}% dead tuples, consider vacuum",
                            stat.tableName, stat.deadRatio);

                // Optional: Trigger manual vacuum
                if (stat.deadRatio > 50) {
                    jdbcTemplate.execute("VACUUM ANALYZE " + stat.tableName);
                }
            }
        }
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Key Questions:
1. Need consistent multi-query reads?
   → Yes: Snapshot Isolation
   → No: READ COMMITTED sufficient

2. Can we tolerate write skew anomalies?
   → Yes: Snapshot Isolation OK
   → No: Need SERIALIZABLE

3. Report generation or analytics?
   → Yes: Perfect for Snapshot Isolation

4. High write contention on same rows?
   → Snapshot Isolation will have high abort rate
   → Consider optimistic locking or sharding

Example:
"For generating monthly financial reports, we need consistent
snapshots across multiple queries. Snapshot Isolation (REPEATABLE READ)
is perfect - all queries see same point-in-time view."
```

### 2. Capacity Estimation (RADIO: Scale)

```
Transaction Characteristics:
- 5,000 long-running read transactions/sec (reports, analytics)
- 2,000 short write transactions/sec
- Average transaction duration: 500ms

MVCC Storage:
- Base data: 500 GB
- Update rate: 2,000 updates/sec
- Vacuum interval: 1 hour (3,600 sec)
- Dead tuples: 2,000 * 3,600 = 7.2M versions
- Storage overhead: ~20-30% → Total: 650 GB

Memory for Snapshots:
- Active snapshots: 5,000 (concurrent transactions)
- Snapshot size: ~1 KB (xid + active_xids list)
- Memory: 5,000 * 1 KB = 5 MB (negligible)

Abort Rate (write-write conflicts):
- Hot rows: 100 rows
- Concurrent updates: 10/sec per row
- Conflict probability: ~5-10%
- Retries needed: ~1.05-1.1x work
```

### 3. Data Model (RADIO: Data-Model)

```java
// Domain Model: Optimized for Snapshot Isolation

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;

    private Long customerId;

    @Enumerated(EnumType.STRING)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;

    private BigDecimal totalAmount;

    @Version
    private Long version;  // Optimistic lock version

    private Instant createdAt;
    private Instant updatedAt;
}

@Service
public class OrderService {

    /**
     * Generate order summary report with consistent snapshot
     */
    @Transactional(
        readOnly = true,
        isolation = Isolation.REPEATABLE_READ
    )
    public OrderSummaryReport generateReport(Long customerId, YearMonth month) {

        // All queries see consistent snapshot

        // Query 1: Count orders
        long orderCount = orderRepo.countByCustomerAndMonth(customerId, month);

        // Query 2: Sum totals
        BigDecimal totalSpent = orderRepo.sumAmountByCustomerAndMonth(
            customerId, month
        );

        // Query 3: Get order details
        List<Order> orders = orderRepo.findByCustomerAndMonth(customerId, month);

        // Query 4: Count items across all orders
        long itemCount = orderItemRepo.countByCustomerAndMonth(customerId, month);

        // ✅ All queries consistent - no phantoms, no skew
        return new OrderSummaryReport(
            orderCount, totalSpent, orders, itemCount
        );
    }

    /**
     * Update order with automatic conflict detection
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @Retryable(
        value = { OptimisticLockException.class, SerializationException.class },
        maxAttempts = 3
    )
    public Order updateOrderStatus(Long orderId, OrderStatus newStatus) {

        Order order = orderRepo.findById(orderId)
            .orElseThrow(() -> new OrderNotFoundException(orderId));

        // Validate state transition
        if (!order.canTransitionTo(newStatus)) {
            throw new InvalidStateTransitionException();
        }

        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());

        // If another transaction modified this order since our snapshot,
        // OptimisticLockException or SerializationException thrown
        return orderRepo.save(order);
    }

    /**
     * Prevent write skew in inventory allocation
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void allocateInventory(Long orderId) {

        Order order = orderRepo.findById(orderId).orElseThrow();

        for (OrderItem item : order.getItems()) {
            // Lock product row to prevent write skew
            Product product = productRepo.findByIdForUpdate(item.getProductId());

            if (product.getStock() < item.getQuantity()) {
                throw new InsufficientStockException();
            }

            // Concurrent allocations blocked by lock
            product.setStock(product.getStock() - item.getQuantity());
            productRepo.save(product);
        }

        order.setStatus(OrderStatus.ALLOCATED);
        orderRepo.save(order);
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │ Reporting  │  │ OLTP       │  │ Analytics  │            │
│  │ Service    │  │ Service    │  │ Service    │            │
│  │(SNAPSHOT)  │  │(READ_COM)  │  │(SNAPSHOT)  │            │
│  └────────────┘  └────────────┘  └────────────┘            │
└────────────────────────────┬────────────────────────────────┘
                             ↓
┌─────────────────────────────────────────────────────────────┐
│                  DATABASE (PostgreSQL)                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  MVCC Engine (Snapshot Isolation)                   │   │
│  │  ┌────────────┐  ┌────────────┐  ┌────────────┐    │   │
│  │  │ Snapshot   │  │ Version    │  │ Visibility │    │   │
│  │  │ Manager    │  │ Store      │  │ Checker    │    │   │
│  │  │            │  │            │  │            │    │   │
│  │  │ xid=100    │  │ v1: xmin=90│  │ Rules:     │    │   │
│  │  │ xmin=95    │  │ v2: xmin=98│  │ - xmin < ? │    │   │
│  │  │ xmax=100   │  │ v3: xmin=99│  │ - xmax > ? │    │   │
│  │  │ active=[96]│  │            │  │ - !active  │    │   │
│  │  └────────────┘  └────────────┘  └────────────┘    │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Vacuum Process (Background Cleanup)                 │   │
│  │  [Remove old versions] [Update visibility map]       │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘

Transaction Flow:
1. BEGIN → Create snapshot (xid, xmin, xmax, active_xids)
2. SELECT → Find visible version using snapshot
3. UPDATE → Create new version, detect conflicts
4. COMMIT → Versions visible to new snapshots
5. VACUUM → Clean up old versions (async)
```

### 5. Deep Dives (RADIO: Optimize)

**A. Detecting and Preventing Write Skew**

```java
@Component
public class WriteSkewDetector {

    /**
     * Pattern 1: Explicit range locks
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void bookMeetingRoom(Long roomId, LocalDateTime start, LocalDateTime end) {

        // Lock entire range by selecting all potentially conflicting rows
        List<Booking> conflicts = jdbcTemplate.query(
            "SELECT * FROM bookings WHERE room_id = ? " +
            "AND (start_time, end_time) OVERLAPS (?, ?) " +
            "FOR UPDATE",
            bookingMapper,
            roomId, start, end
        );

        if (!conflicts.isEmpty()) {
            throw new BookingConflictException();
        }

        bookingRepo.save(new Booking(roomId, start, end));
    }

    /**
     * Pattern 2: Materialize conflicts
     */
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void bookMeetingRoomMaterialized(
        Long roomId, LocalDateTime start, LocalDateTime end
    ) {
        // Create time slot buckets (e.g., 30-minute slots)
        List<TimeSlot> slots = TimeSlot.split(start, end, Duration.ofMinutes(30));

        for (TimeSlot slot : slots) {
            // Try to insert/update a "lock" row for this slot
            SlotLock lock = slotLockRepo.findByRoomAndSlot(roomId, slot);

            if (lock == null) {
                lock = new SlotLock(roomId, slot);
            } else {
                // Concurrent booking will conflict on this UPDATE
                lock.incrementBookingCount();
            }

            slotLockRepo.save(lock);  // Write-write conflict if concurrent
        }

        bookingRepo.save(new Booking(roomId, start, end));
    }

    /**
     * Pattern 3: Application-level validation with retry
     */
    @Retryable(maxAttempts = 5)
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void enforceConstraint(Long userId) {

        // Read multiple rows involved in constraint
        List<Account> accounts = accountRepo.findAllByUserForUpdate(userId);

        // Validate constraint
        BigDecimal totalBalance = accounts.stream()
            .map(Account::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new ConstraintViolationException("Total balance negative");
        }

        // Proceed with operation
        // If constraint violated by concurrent transaction, retry
    }
}
```

**B. Vacuum Tuning**

```sql
-- PostgreSQL: Aggressive autovacuum for high-update tables
ALTER TABLE orders SET (
    autovacuum_vacuum_scale_factor = 0.05,  -- Vacuum when 5% dead
    autovacuum_vacuum_cost_delay = 5,       -- Faster vacuum
    autovacuum_vacuum_cost_limit = 500      -- Higher work limit
);

-- Monitor vacuum effectiveness
SELECT schemaname, relname, last_vacuum, last_autovacuum,
       n_dead_tup, n_live_tup
FROM pg_stat_user_tables
WHERE n_dead_tup > 10000
ORDER BY n_dead_tup DESC;

-- Manual vacuum for critical tables
VACUUM ANALYZE orders;
```

**C. Monitoring Snapshot Isolation**

```java
@Component
public class SnapshotIsolationMetrics {

    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {

        // Oldest active snapshot age
        String query = """
            SELECT EXTRACT(EPOCH FROM (NOW() - xact_start)) as age_seconds
            FROM pg_stat_activity
            WHERE state = 'active'
            ORDER BY xact_start ASC
            LIMIT 1
            """;

        Long oldestSnapshot = jdbcTemplate.queryForObject(query, Long.class);

        registry.gauge("db.snapshot.oldest_age_seconds", oldestSnapshot);

        // Dead tuple count
        Long deadTuples = jdbcTemplate.queryForObject(
            "SELECT SUM(n_dead_tup) FROM pg_stat_user_tables",
            Long.class
        );

        registry.gauge("db.mvcc.dead_tuples", deadTuples);

        // Transaction abort rate
        Long aborts = jdbcTemplate.queryForObject(
            "SELECT xact_rollback FROM pg_stat_database WHERE datname = current_database()",
            Long.class
        );

        registry.counter("db.transactions.aborted").increment(aborts);
    }
}
```

---

## 🧠 MIND MAP: SNAPSHOT ISOLATION

```
                  SNAPSHOT ISOLATION
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   GUARANTEES        IMPLEMENTATION      ANOMALIES
        |                 |                  |
   ┌────┴────┐       ┌────┴────┐       ┌────┴────┐
   ↓         ↓       ↓         ↓       ↓         ↓
Consistent MVCC     Version   Snapshot Write    Lost
Snapshot   (xmin/  Chain     (xid,     Skew     Update
           xmax)              active)           Prevented
   |                 |                  |
No Dirty  First     Vacuum   Visibility Read-Only
Reads     Commit    Process  Rules      Safe
No Non-   Wins
Repeat
```

### 💡 EMOTIONAL ANCHORS (For Subconscious Power)

1. **Snapshot Isolation = Time Travel Photo 📸**
   - You take photo at 10am (snapshot)
   - Look at photo at 11am
   - Photo still shows 10am state (consistent)
   - Real world changed, photo didn't

2. **MVCC = Document Versions 📄**
   - v1: Original draft
   - v2: After edits
   - v3: Final version
   - Different people read different versions simultaneously

3. **Write Skew = Two Friends Leaving Party 🎉**
   - Rule: At least 1 friend must stay
   - Alice checks: "Bob is here, I can leave"
   - Bob checks: "Alice is here, I can leave"
   - Both leave simultaneously → Rule violated!

4. **Vacuum = Garbage Collection 🗑️**
   - Old versions pile up
   - Periodically clean up unreachable versions
   - Free up space

---

## 🎯 INTERVIEW GOLD: KEY TALKING POINTS

1. **Snapshot consistency**: "Snapshot Isolation gives us repeatable reads and phantom prevention automatically through MVCC - perfect for reports."

2. **Performance advantage**: "Unlike lock-based REPEATABLE READ, MVCC means readers never block writers. Report generation doesn't slow down OLTP traffic."

3. **Write skew caveat**: "SI isn't serializable. Classic example: on-call doctors both requesting off. Need explicit locks or SERIALIZABLE for such cases."

4. **PostgreSQL default**: "PostgreSQL REPEATABLE READ is actually Snapshot Isolation, not the ANSI standard. It's stronger than spec requires."

5. **Storage trade-off**: "MVCC requires ~20-30% extra storage for old versions. We tune autovacuum aggressively on high-churn tables."

6. **When to upgrade**: "Most apps use READ COMMITTED for OLTP, SNAPSHOT for reports. Only critical operations need SERIALIZABLE."

---

## 📚 FURTHER READING

**Academic Papers:**
- "A Critique of ANSI SQL Isolation Levels" (Berenson et al., 1995)
- "Generalized Isolation Level Definitions" (Adya, 1999)

**Database Documentation:**
- PostgreSQL MVCC: https://www.postgresql.org/docs/current/mvcc.html
- Oracle Flashback: https://docs.oracle.com/en/database/oracle/oracle-database/19/adfns/flashback.html

**Books:**
- "Database Internals" by Alex Petrov (Chapter 5: Transaction Processing)
- "Designing Data-Intensive Applications" by Martin Kleppmann (Chapter 7: Transactions)
