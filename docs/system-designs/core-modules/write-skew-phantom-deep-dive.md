# Write Skew and Phantom Writes Deep Dive

## Contents

- [Write Skew and Phantom Writes Deep Dive](#write-skew-and-phantom-writes-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Write Skew Anomaly](#write-skew-anomaly)
    - [Phantom Reads and Writes](#phantom-reads-and-writes)
    - [Real-World Scenarios](#real-world-scenarios)
    - [Detection and Prevention](#detection-and-prevention)
    - [Isolation Levels and Anomalies](#isolation-levels-and-anomalies)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Identifying Write Skew Risks](#1-identifying-write-skew-risks)
        - [Prevention Strategies](#2-prevention-strategies)
        - [Testing for Concurrency Bugs](#3-testing-for-concurrency-bugs)
        - [Production Monitoring](#4-production-monitoring)
    - [MIND MAP: CONCURRENCY ANOMALIES](#mind-map-concurrency-anomalies)

## Core Mental Model

```text
Write Skew = Constraint Violation via Concurrent Writes

Pattern:
1. Two transactions read SAME data
2. Make decisions based on that data
3. Update DIFFERENT rows
4. Result violates constraint (but each tx looks valid)

Key Insight: Standard isolation levels DON'T prevent this!
```

**The Fundamental Problem:**
```
┌────────────────────────────────────────────────┐
│ CLASSIC ANOMALIES (Well-Known)                 │
│                                                │
│ 1. Dirty Read                                  │
│    └─ Prevented by: READ COMMITTED             │
│                                                │
│ 2. Non-Repeatable Read                         │
│    └─ Prevented by: REPEATABLE READ            │
│                                                │
│ 3. Lost Update                                 │
│    └─ Prevented by: REPEATABLE READ + locking  │
│                                                │
│ SUBTLE ANOMALIES (Often Missed!)               │
│                                                │
│ 4. Write Skew                                  │
│    └─ NOT prevented by: REPEATABLE READ        │
│    └─ Prevented by: SERIALIZABLE only          │
│                                                │
│ 5. Phantom Reads/Writes                        │
│    └─ NOT prevented by: REPEATABLE READ*       │
│    └─ Prevented by: SERIALIZABLE               │
│                                                │
│ * MySQL InnoDB prevents phantoms via gap locks │
└────────────────────────────────────────────────┘
```

**Key insight**: Write skew is **invisible** to row-level locking because transactions modify **different rows**.

---

## Write Skew Anomaly

🎓 **PROFESSOR**: Write skew occurs when **constraint spans multiple rows**:

```
Definition:
Write Skew = Two transactions read overlapping data,
             then update DISJOINT sets of rows,
             violating a constraint

Formally:
- T1 reads X and Y
- T2 reads X and Y
- T1 writes X (based on Y's value)
- T2 writes Y (based on X's value)
- Constraint involving X and Y is violated
```

### Classic Example: On-Call Doctors

🏗️ **ARCHITECT**: The **canonical write skew scenario**:

```sql
-- Constraint: At least 1 doctor must be on-call

-- Initial state:
-- doctors table:
-- | id | name    | on_call |
-- |----|---------|---------|
-- | 1  | Alice   | true    |
-- | 2  | Bob     | true    |

-- Transaction 1 (Alice going off-call):
BEGIN;
SELECT COUNT(*) FROM doctors WHERE on_call = true;
-- Returns 2 (Alice and Bob both on-call)

-- Alice sees Bob is on-call, so safe to go off
UPDATE doctors SET on_call = false WHERE id = 1;

-- Transaction 2 (Bob going off-call, concurrent):
BEGIN;
SELECT COUNT(*) FROM doctors WHERE on_call = true;
-- Returns 2 (Alice and Bob both on-call)

-- Bob sees Alice is on-call, so safe to go off
UPDATE doctors SET on_call = false WHERE id = 2;

-- Both commit!
COMMIT;  -- T1
COMMIT;  -- T2

-- RESULT: 0 doctors on-call! Constraint violated!
-- | id | name    | on_call |
-- |----|---------|---------|
-- | 1  | Alice   | false   |
-- | 2  | Bob     | false   |
```

**Why Row-Level Locking Fails:**
```
T1 locks row 1 (Alice)
T2 locks row 2 (Bob)

No conflict! Different rows!

But logical constraint violated:
  SUM(on_call) >= 1
```

**Code Example with JPA:**
```java
@Service
public class OnCallService {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void goOffCall(Long doctorId) {
        // Read: Check if safe to go off-call
        long onCallCount = doctorRepo.countByOnCall(true);

        if (onCallCount <= 1) {
            throw new BusinessException(
                "Cannot go off-call: You are the last doctor"
            );
        }

        // Write: Go off-call
        Doctor doctor = doctorRepo.findById(doctorId)
            .orElseThrow();
        doctor.setOnCall(false);
        doctorRepo.save(doctor);

        // WRITE SKEW POSSIBLE!
        // Two transactions can both see onCallCount=2
        // Both update different doctors
        // Result: 0 doctors on-call
    }
}
```

### More Write Skew Examples

```sql
-- Example 2: Meeting room booking
-- Constraint: Room can't be double-booked for overlapping times

-- T1: Book room 101 from 2-3pm
BEGIN;
SELECT COUNT(*) FROM bookings
WHERE room_id = 101
  AND time_start < '2024-01-15 15:00'
  AND time_end > '2024-01-15 14:00';
-- Returns 0 (no conflicts)

INSERT INTO bookings (room_id, time_start, time_end, user_id)
VALUES (101, '2024-01-15 14:00', '2024-01-15 15:00', 1);

-- T2: Book same room 101 from 2:30-3:30pm (concurrent)
BEGIN;
SELECT COUNT(*) FROM bookings
WHERE room_id = 101
  AND time_start < '2024-01-15 15:30'
  AND time_end > '2024-01-15 14:30';
-- Returns 0 (doesn't see T1's insert yet!)

INSERT INTO bookings (room_id, time_start, time_end, user_id)
VALUES (101, '2024-01-15 14:30', '2024-01-15 15:30', 2);

-- Both commit: Double-booked!
COMMIT;  -- T1
COMMIT;  -- T2


-- Example 3: Username uniqueness (race condition)
-- Constraint: Usernames must be unique

-- T1: Register username "alice"
BEGIN;
SELECT COUNT(*) FROM users WHERE username = 'alice';
-- Returns 0

INSERT INTO users (username, email) VALUES ('alice', 'alice1@example.com');

-- T2: Register username "alice" (concurrent)
BEGIN;
SELECT COUNT(*) FROM users WHERE username = 'alice';
-- Returns 0 (doesn't see T1's insert)

INSERT INTO users (username, email) VALUES ('alice', 'alice2@example.com');

-- Both commit: Duplicate usernames!
COMMIT;  -- T1
COMMIT;  -- T2
```

**Interview talking point**: "Write skew is subtle because each transaction looks correct in isolation. The bug only appears when you consider them together. REPEATABLE READ doesn't help—you need SERIALIZABLE."

---

## Phantom Reads and Writes

🎓 **PROFESSOR**: Phantoms occur when **query results change** during transaction:

```
Phantom Read:
1. T1: SELECT WHERE condition → Returns N rows
2. T2: INSERT/DELETE row matching condition, COMMIT
3. T1: SELECT WHERE condition → Returns N+1 or N-1 rows

The "phantom" row appeared/disappeared!
```

### Phantom Read Example

```sql
-- Transaction 1: Calculate average salary
BEGIN;
SELECT AVG(salary) FROM employees WHERE department = 'Engineering';
-- Returns $100,000 (10 employees, total $1M)

-- Transaction 2: Hire new engineer (concurrent)
BEGIN;
INSERT INTO employees (name, department, salary)
VALUES ('Charlie', 'Engineering', 120000);
COMMIT;

-- Transaction 1: Re-calculate average
SELECT AVG(salary) FROM employees WHERE department = 'Engineering';
-- Returns $101,818 (11 employees, total $1.12M)
-- Different result in same transaction!

COMMIT;

-- With REPEATABLE READ (snapshot isolation):
-- T1 sees consistent snapshot: Both SELECTs return $100,000
-- Phantom prevented by MVCC

-- But phantom WRITE still possible (see below)
```

### Phantom Write (More Dangerous!)

🏗️ **ARCHITECT**: **Phantom write** = Insert/update based on query that could change:

```sql
-- Constraint: Max 100 users per pricing tier

-- T1: Upgrade user to "premium" tier
BEGIN;
SELECT COUNT(*) FROM users WHERE pricing_tier = 'premium';
-- Returns 99

-- Safe to add one more
UPDATE users SET pricing_tier = 'premium' WHERE id = 1;

-- T2: Upgrade different user to "premium" (concurrent)
BEGIN;
SELECT COUNT(*) FROM users WHERE pricing_tier = 'premium';
-- Returns 99 (doesn't see T1's update yet)

-- Safe to add one more
UPDATE users SET pricing_tier = 'premium' WHERE id = 2;

-- Both commit!
COMMIT;  -- T1
COMMIT;  -- T2

-- Result: 101 premium users! Constraint violated!


-- Another example: Inventory over-selling
-- Constraint: Can't sell more than available stock

-- Initial: stock_quantity = 5

-- T1: Sell 3 units
BEGIN;
SELECT stock_quantity FROM products WHERE id = 123;
-- Returns 5

-- Check: 5 >= 3, OK to sell
UPDATE products SET stock_quantity = stock_quantity - 3
WHERE id = 123;

-- T2: Sell 4 units (concurrent)
BEGIN;
SELECT stock_quantity FROM products WHERE id = 123;
-- Returns 5 (snapshot isolation!)

-- Check: 5 >= 4, OK to sell
UPDATE products SET stock_quantity = stock_quantity - 4
WHERE id = 123;

-- Both commit
COMMIT;  -- T1
COMMIT;  -- T2

-- Result: stock_quantity = -2 (sold 7 units from 5!)
-- Oversold by 2 units!
```

**Code Example:**
```java
@Service
public class InventoryService {

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void sellProduct(Long productId, int quantity) {
        // Read current stock
        Product product = productRepo.findById(productId)
            .orElseThrow();

        // Validate
        if (product.getStockQuantity() < quantity) {
            throw new OutOfStockException();
        }

        // Update stock
        product.setStockQuantity(
            product.getStockQuantity() - quantity
        );
        productRepo.save(product);

        // PHANTOM WRITE PROBLEM:
        // Multiple transactions can all see enough stock
        // All update concurrently
        // Result: negative stock!
    }

    // CORRECT VERSION: Use atomic update
    @Transactional
    public void sellProductCorrect(Long productId, int quantity) {
        // Atomic read-modify-write with constraint check
        int rowsUpdated = productRepo.decrementStock(
            productId,
            quantity
        );

        if (rowsUpdated == 0) {
            throw new OutOfStockException();
        }
    }
}

// Repository with atomic operation
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    @Modifying
    @Query("UPDATE Product p SET p.stockQuantity = p.stockQuantity - :quantity " +
           "WHERE p.id = :productId AND p.stockQuantity >= :quantity")
    int decrementStock(@Param("productId") Long productId,
                       @Param("quantity") int quantity);
    // Returns 0 if insufficient stock (constraint in WHERE clause)
    // This is atomic: read-check-write in one operation
}
```

**Interview gold**: "Phantom writes are the reason e-commerce sites oversell during Black Friday. Solution: atomic compare-and-swap in SQL, not application logic."

---

## Real-World Scenarios

🎓 **PROFESSOR**: Write skew appears in **many domains**:

### 1. Financial: Double-Spending Prevention
```sql
-- Constraint: Account balance >= 0

-- Initial: balance = $100

-- T1: Withdraw $80
BEGIN;
SELECT balance FROM accounts WHERE id = 123;  -- $100
-- Check: $100 >= $80, OK
UPDATE accounts SET balance = balance - 80 WHERE id = 123;
COMMIT;

-- T2: Withdraw $60 (concurrent)
BEGIN;
SELECT balance FROM accounts WHERE id = 123;  -- $100
-- Check: $100 >= $60, OK
UPDATE accounts SET balance = balance - 60 WHERE id = 123;
COMMIT;

-- Result: balance = -$40 (overdraft!)
```

### 2. Access Control: Mutual Exclusion
```sql
-- Constraint: File can be edited by at most 1 user

-- T1: User A starts editing
BEGIN;
SELECT COUNT(*) FROM file_locks WHERE file_id = 42;  -- 0
INSERT INTO file_locks (file_id, user_id) VALUES (42, 'userA');
COMMIT;

-- T2: User B starts editing (concurrent)
BEGIN;
SELECT COUNT(*) FROM file_locks WHERE file_id = 42;  -- 0
INSERT INTO file_locks (file_id, user_id) VALUES (42, 'userB');
COMMIT;

-- Result: Two users editing same file!
```

### 3. Resource Allocation: CPU/Memory Limits
```sql
-- Constraint: Total allocated memory <= 64GB

-- T1: Allocate 40GB to job1
BEGIN;
SELECT SUM(allocated_memory) FROM jobs;  -- 30GB
-- Check: 30 + 40 <= 64, OK
INSERT INTO jobs (id, allocated_memory) VALUES ('job1', 40);
COMMIT;

-- T2: Allocate 40GB to job2 (concurrent)
BEGIN;
SELECT SUM(allocated_memory) FROM jobs;  -- 30GB
-- Check: 30 + 40 <= 64, OK
INSERT INTO jobs (id, allocated_memory) VALUES ('job2', 40);
COMMIT;

-- Result: 110GB allocated! OOM!
```

### 4. Game State: Turn-Based Games
```sql
-- Constraint: Players must take turns (no simultaneous moves)

-- T1: Player 1 makes move
BEGIN;
SELECT current_turn FROM games WHERE id = 99;  -- 'player1'
-- It's my turn!
UPDATE games SET board_state = '...', current_turn = 'player2'
WHERE id = 99;
COMMIT;

-- T2: Player 1 makes another move (concurrent)
BEGIN;
SELECT current_turn FROM games WHERE id = 99;  -- 'player1'
-- It's my turn!
UPDATE games SET board_state = '...', current_turn = 'player2'
WHERE id = 99;
COMMIT;

-- Result: Player 1 moved twice in a row!
```

---

## Detection and Prevention

🎓 **PROFESSOR**: Multiple strategies exist:

### 1. SERIALIZABLE Isolation Level

```sql
-- PostgreSQL: Serializable Snapshot Isolation (SSI)
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;

BEGIN;
SELECT COUNT(*) FROM doctors WHERE on_call = true;  -- 2
UPDATE doctors SET on_call = false WHERE id = 1;
COMMIT;
-- If concurrent transaction did the same: ERROR!
-- "could not serialize access due to read/write dependencies"
```

### 2. Explicit Locking

```sql
-- SELECT FOR UPDATE: Lock rows read
BEGIN;
SELECT * FROM doctors
WHERE on_call = true
FOR UPDATE;  -- Exclusive lock on ALL on-call doctors

-- Now safe: other transactions can't modify these rows
UPDATE doctors SET on_call = false WHERE id = 1;
COMMIT;


-- SELECT FOR SHARE: Shared lock (prevent updates)
BEGIN;
SELECT * FROM products WHERE id = 123
FOR SHARE;  -- Other transactions can read but not update

-- Prevents phantom writes
INSERT INTO order_items (product_id, quantity)
VALUES (123, 5);
COMMIT;
```

### 3. Materialized Constraints

```sql
-- Instead of implicit constraint (sum of on_call >= 1)
-- Materialize it in a row!

CREATE TABLE on_call_summary (
    id INT PRIMARY KEY DEFAULT 1,
    on_call_count INT NOT NULL DEFAULT 0,
    CHECK (on_call_count >= 1)
);

-- Now both transactions update SAME row → conflict detected!
BEGIN;
UPDATE on_call_summary SET on_call_count = on_call_count - 1;
-- This will block/conflict with concurrent similar update
UPDATE doctors SET on_call = false WHERE id = 1;
COMMIT;
```

### 4. Application-Level Locking

```java
@Service
public class SafeOnCallService {

    // Use distributed lock (Redis, ZooKeeper)
    @Transactional
    public void goOffCall(Long doctorId) {
        // Acquire exclusive lock
        Lock lock = redisLock.acquire("on_call_lock", timeout = 5_000);

        try {
            // Now serialized: only one transaction at a time
            long onCallCount = doctorRepo.countByOnCall(true);

            if (onCallCount <= 1) {
                throw new BusinessException("Cannot go off-call");
            }

            Doctor doctor = doctorRepo.findById(doctorId).orElseThrow();
            doctor.setOnCall(false);
            doctorRepo.save(doctor);

        } finally {
            lock.release();
        }
    }
}
```

### 5. Compare-and-Swap (Optimistic)

```java
@Entity
public class OnCallSummary {
    @Id
    private Long id = 1L;

    private Integer onCallCount;

    @Version
    private Long version;  // Optimistic lock
}

@Service
public class OptimisticOnCallService {

    @Transactional
    public void goOffCall(Long doctorId) {
        // Read summary with version
        OnCallSummary summary = summaryRepo.findById(1L).orElseThrow();

        if (summary.getOnCallCount() <= 1) {
            throw new BusinessException("Cannot go off-call");
        }

        // Update doctor
        Doctor doctor = doctorRepo.findById(doctorId).orElseThrow();
        doctor.setOnCall(false);

        // Decrement count (with version check!)
        summary.setOnCallCount(summary.getOnCallCount() - 1);
        summaryRepo.save(summary);
        // If version changed: OptimisticLockException
        // Retry logic will handle it
    }
}
```

**Comparison:**
```python
class PreventionStrategy:
    """
    Choose prevention strategy based on requirements
    """
    def select_strategy(self, workload: Workload) -> str:

        if workload.is_low_contention():
            # Few conflicts: optimistic approach
            return "OPTIMISTIC_LOCKING"  # Compare-and-swap

        if workload.database_supports_ssi():
            # PostgreSQL, SQL Server 2012+
            return "SERIALIZABLE_ISOLATION"  # Best solution

        if workload.can_materialize_constraint():
            # Convert multi-row constraint to single row
            return "MATERIALIZED_CONSTRAINT"

        if workload.is_high_contention():
            # Many conflicts: pessimistic approach
            return "EXPLICIT_LOCKING"  # SELECT FOR UPDATE

        # Fallback: application-level serialization
        return "DISTRIBUTED_LOCK"  # Redis lock
```

---

## Isolation Levels and Anomalies

🎓 **PROFESSOR**: Summary of what each level prevents:

```
┌──────────────────┬───────┬─────────┬────────┬──────────┬─────────┐
│ Isolation Level  │ Dirty │ Non-Rep │ Lost   │ Phantom  │ Write   │
│                  │ Read  │  Read   │ Update │ Read     │ Skew    │
├──────────────────┼───────┼─────────┼────────┼──────────┼─────────┤
│ READ UNCOMMITTED │   ✗   │    ✗    │   ✗    │    ✗     │    ✗    │
│ READ COMMITTED   │   ✓   │    ✗    │   ✗    │    ✗     │    ✗    │
│ REPEATABLE READ  │   ✓   │    ✓    │   ✓    │  MySQL:✓ │    ✗    │
│                  │       │         │        │  Pgsql:✗ │         │
│ SERIALIZABLE     │   ✓   │    ✓    │   ✓    │    ✓     │    ✓    │
└──────────────────┴───────┴─────────┴────────┴──────────┴─────────┘

✓ = Prevented
✗ = Possible

MySQL InnoDB: Uses gap locking at REPEATABLE READ (prevents phantoms)
PostgreSQL:   Uses SSI at SERIALIZABLE (detects write skew)
```

**Real-World Recommendation:**
```java
public enum IsolationRecommendation {
    READ_UNCOMMITTED("Analytics, approximate counts"),
    READ_COMMITTED("Default for most OLTP"),
    REPEATABLE_READ("Financial data, inventory"),
    SERIALIZABLE("Critical constraints, low-contention");

    private final String useCase;
}

class IsolationSelector {
    public IsolationLevel select(TransactionType type) {
        switch (type) {
            case FINANCIAL_TRANSFER:
            case INVENTORY_MANAGEMENT:
            case ACCESS_CONTROL:
                // Prevent write skew!
                return IsolationLevel.SERIALIZABLE;

            case USER_PROFILE_UPDATE:
            case ORDER_PLACEMENT:
                // REPEATABLE READ sufficient
                return IsolationLevel.REPEATABLE_READ;

            case ANALYTICS_QUERY:
            case REPORTING:
                // READ COMMITTED adequate
                return IsolationLevel.READ_COMMITTED;

            default:
                // Safe default
                return IsolationLevel.REPEATABLE_READ;
        }
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Identifying Write Skew Risks

```
Checklist: Is write skew possible?

Ask:
1. ✓ Do transactions read data and make decisions?
2. ✓ Do transactions update DIFFERENT rows based on reads?
3. ✓ Is there a constraint spanning multiple rows?
4. ✓ Can multiple transactions run concurrently?

If all YES → Write skew possible!

Examples:
- Constraint: at_least_1_on_call
- Constraint: balance >= 0
- Constraint: no_double_booking
- Constraint: max_users_per_tier
```

### 2. Prevention Strategies

```sql
-- Strategy 1: SERIALIZABLE (if available)
SET TRANSACTION ISOLATION LEVEL SERIALIZABLE;
-- Pros: Automatic detection
-- Cons: Performance overhead, aborts

-- Strategy 2: Explicit locking
BEGIN;
SELECT * FROM critical_table FOR UPDATE;
-- Pros: No aborts (pessimistic)
-- Cons: Deadlocks possible, blocks readers

-- Strategy 3: Materialized constraint
CREATE TABLE constraint_tracker (
    id INT PRIMARY KEY,
    constraint_value INT,
    CHECK (constraint_value >= min_value)
);
-- Pros: Forces single-row update
-- Cons: Requires schema change

-- Strategy 4: Atomic operations
UPDATE table SET col = col + 1
WHERE id = ? AND col >= min_value;
-- Check rows_affected = 1
-- Pros: No explicit transaction needed
-- Cons: Limited to simple cases
```

### 3. Testing for Concurrency Bugs

```python
class ConcurrencyTester:
    """
    Stress test to detect write skew
    """
    def test_concurrent_go_off_call(self):
        # Setup: 2 doctors on-call
        setup_doctors(on_call_count=2)

        # Concurrent execution
        threads = []
        for doctor_id in [1, 2]:
            thread = Thread(
                target=self.go_off_call,
                args=(doctor_id,)
            )
            threads.append(thread)

        # Start simultaneously
        for thread in threads:
            thread.start()

        # Wait for completion
        for thread in threads:
            thread.join()

        # Assert: At least 1 doctor still on-call
        on_call_count = count_doctors_on_call()
        assert on_call_count >= 1, \
            f"Write skew detected! {on_call_count} doctors on-call"

    def test_concurrent_inventory_purchase(self):
        # Setup: 10 units in stock
        setup_product(stock=10)

        # 5 concurrent purchases of 8 units each
        threads = [
            Thread(target=self.purchase, args=(8,))
            for _ in range(5)
        ]

        for thread in threads:
            thread.start()

        for thread in threads:
            thread.join()

        # Count successes
        successful_purchases = count_successful_purchases()
        final_stock = get_stock()

        # Assert: Stock should never go negative
        assert final_stock >= 0, \
            f"Oversold! Stock: {final_stock}"

        # Assert: Sold at most 10 units
        total_sold = 10 - final_stock
        assert total_sold <= 10, \
            f"Sold {total_sold} units from 10!"
```

### 4. Production Monitoring

```java
@Component
public class WriteSkewMonitor {

    @Scheduled(fixedRate = 60_000)  // Every minute
    public void checkConstraints() {
        // Check: At least 1 doctor on-call
        long onCallCount = doctorRepo.countByOnCall(true);
        if (onCallCount < 1) {
            alerting.sendAlert(
                Severity.CRITICAL,
                "Constraint violation: No doctors on-call!"
            );
        }

        // Check: No negative inventory
        long negativeStockCount = productRepo.countByStockLessThan(0);
        if (negativeStockCount > 0) {
            alerting.sendAlert(
                Severity.HIGH,
                "Inventory violation: " + negativeStockCount +
                " products have negative stock"
            );
        }

        // Check: No double-bookings
        long overlapCount = bookingRepo.countOverlappingBookings();
        if (overlapCount > 0) {
            alerting.sendAlert(
                Severity.MEDIUM,
                "Booking conflict: " + overlapCount +
                " double-bookings detected"
            );
        }
    }

    // Audit log for forensics
    @EventListener
    public void onConstraintViolation(ConstraintViolationEvent event) {
        auditLog.write(
            "CONSTRAINT_VIOLATION",
            event.getConstraintName(),
            event.getCurrentValue(),
            event.getExpectedValue(),
            event.getAffectedTransactions()
        );
    }
}
```

---

## 🧠 MIND MAP: CONCURRENCY ANOMALIES

```
        CONCURRENCY ANOMALIES
                |
        ┌───────┴────────┐
        ↓                ↓
    CLASSIC         SUBTLE
        |                |
    ┌───┴───┐        ┌───┴───┐
    ↓       ↓        ↓       ↓
  Dirty  Lost     Write   Phantom
  Read   Update   Skew    Read/Write
    |       |        |         |
Prevent  Prevent  Need     Need
  by      by    Serializ Serializ
   RC      RR      or        or
    |       |     Explicit  Explicit
Uncommit Locking   Lock     Lock
  not       |        |         |
 visible Version  Material  Range
           Check   Constr   Lock
    |       |        |         |
  WAL    MVCC    Summary   FOR UPDATE
 Rollback  or     Table      on
         2PL        |      Query
                Same-Row  Predicate
                Update      |
                         Gap Lock
                          (InnoDB)
```

---

## 💡 EMOTIONAL ANCHORS (For Subconscious Retention)

### 1. **Write Skew = Two Drivers, One Parking Spot 🚗**
- Driver A: "One spot left, I'll take it"
- Driver B: "One spot left, I'll take it"
- Both try to park simultaneously
- Collision! (Constraint violated)

### 2. **Phantom Read = Haunted House 👻**
- Count rooms: 10 rooms
- Ghost appears (INSERT)
- Count again: 11 rooms
- Room "materialized" from nowhere!

### 3. **Materialized Constraint = Shared Counter 🔢**
- Instead of checking multiple rows
- Update single counter
- Forces serialization
- Like ticket dispenser: one at a time

### 4. **SELECT FOR UPDATE = Reserve Your Seat 💺**
- Reading menu doesn't reserve table
- FOR UPDATE = "I'm ordering this, save it for me"
- Others can't modify what you've reserved

### 5. **Write Skew = Prisoner's Dilemma 🤝**
- Each transaction acts rationally
- Based on information available
- Combined result: irrational (violates constraint)
- Need coordination mechanism

### 6. **Atomic Compare-and-Swap = Lost Baggage Tag 🎫**
- Take number (version)
- Do your shopping
- At counter: "I have number 42"
- If still 42: proceed
- If changed to 43: someone went ahead, retry

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Three Laws of Write Skew:**

1. **REPEATABLE READ is Not Enough**
   - Prevents lost updates on SAME row
   - Doesn't prevent write skew (DIFFERENT rows)
   - Need SERIALIZABLE for full protection

2. **Constraints Must Be Enforced Atomically**
   - Multi-row constraints are dangerous
   - Materialize them when possible
   - Or use explicit locking

3. **Test Concurrency Early**
   - Write skew is hard to debug in production
   - Stress tests with concurrent threads
   - Monitor constraint violations in production

---

## 🏗️ ARCHITECT'S CHECKLIST

Audit for write skew vulnerabilities:

- [ ] **Inventory Systems**: Stock checks before sale?
- [ ] **Financial**: Balance checks before withdrawal?
- [ ] **Access Control**: Permission checks before granting?
- [ ] **Resource Allocation**: Quota checks before allocating?
- [ ] **Booking Systems**: Availability checks before booking?
- [ ] **State Machines**: State checks before transition?
- [ ] **Uniqueness**: Check-then-insert patterns?
- [ ] **Limits**: Count checks before adding?
- [ ] **Isolation Level**: Using SERIALIZABLE for critical operations?
- [ ] **Testing**: Concurrent stress tests in CI/CD?

---

## 🛠️ PREVENTION PATTERN LIBRARY

```java
// Pattern 1: SERIALIZABLE isolation
@Transactional(isolation = Isolation.SERIALIZABLE)
public void criticalOperation() { ... }

// Pattern 2: Pessimistic locking
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT e FROM Entity e WHERE ...")
List<Entity> findAndLock();

// Pattern 3: Optimistic locking
@Version
private Long version;

// Pattern 4: Atomic update
@Query("UPDATE Entity SET count = count + 1 WHERE id = :id AND count < :max")
int incrementIfBelowMax(@Param("id") Long id, @Param("max") int max);

// Pattern 5: Distributed lock
Lock lock = redisson.getLock("constraint_lock");
lock.lock();
try { ... } finally { lock.unlock(); }

// Pattern 6: Materialized constraint
@Entity
class ConstraintSummary {
    @Id Long id;
    Integer value;
    // Both transactions update this row → conflict!
}
```

---

**Interview Closer**: "Write skew is the sneakiest concurrency bug. It's prevented by SERIALIZABLE isolation, but that's expensive. For critical paths, I'd use explicit locking or materialized constraints. For example, inventory management: use atomic UPDATE with stock check in WHERE clause, not separate SELECT then UPDATE. Let's discuss specific scenarios in your system..."
