# Database Internals: Two Phase Locking (2PL) Deep Dive

## Contents

- [Database Internals: Two Phase Locking (2PL) Deep Dive](#database-internals-two-phase-locking-2pl-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Two Phases](#the-two-phases)
    - [Lock Types and Compatibility](#lock-types-and-compatibility)
    - [2PL Variants](#2pl-variants)
    - [Deadlock Detection and Prevention](#deadlock-detection-and-prevention)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: TWO PHASE LOCKING](#mind-map-two-phase-locking)

## Core Mental Model

```text
TWO PHASE LOCKING (2PL) = The classic pessimistic concurrency control protocol

The Fundamental Rule:
┌──────────────────────────────────────────────────────────┐
│ A transaction CANNOT acquire new locks after releasing   │
│ ANY lock.                                                 │
│                                                           │
│ Phase 1 (GROWING):  Acquire locks, never release         │
│ Phase 2 (SHRINKING): Release locks, never acquire        │
└──────────────────────────────────────────────────────────┘

Why 2PL guarantees SERIALIZABILITY:
- Prevents transactions from seeing intermediate states
- Creates a total order of conflicting operations
- Equivalent to some serial execution
```

**The Two Phases Visualized:**
```
Timeline of Transaction T1:

BEGIN ─────────────────────────────────────────── COMMIT
      │                                           │
      │◄─────── GROWING PHASE ───────►│◄─ SHRINK │
      │                               │          │
      Acquire   Acquire   Acquire   Lock Point  Release
      Lock 1    Lock 2    Lock 3      ▲         All Locks
                                       │
                              (no more acquires
                               after this!)

Lock Point = Moment of maximum locks held
```

---

## The Two Phases

🎓 **PROFESSOR**: The **two-phase property** is what guarantees serializability.

### Phase 1: **Growing Phase** (Acquiring Locks)

**Rules:**
- ✅ Can acquire new locks (shared or exclusive)
- ❌ Cannot release any locks
- Continues until first lock release

```java
// Growing Phase Example
@Transactional
public void transferMoney(Long fromId, Long toId, BigDecimal amount) {

    // BEGIN TRANSACTION - Start of Growing Phase

    // Acquire Lock 1: Exclusive lock on 'from' account
    Account from = accountRepo.findByIdForUpdate(fromId);  // X-Lock

    // Acquire Lock 2: Exclusive lock on 'to' account
    Account to = accountRepo.findByIdForUpdate(toId);  // X-Lock

    // Lock Point reached (all locks acquired)

    // Perform operations
    from.debit(amount);
    to.credit(amount);

    accountRepo.saveAll(List.of(from, to));

    // COMMIT - Start of Shrinking Phase (all locks released)
}
```

---

### Phase 2: **Shrinking Phase** (Releasing Locks)

**Rules:**
- ❌ Cannot acquire new locks
- ✅ Can release locks
- Starts at first lock release, continues to transaction end

```
Transaction Execution:

T1: BEGIN
    LOCK(A)      ◄─┐
    READ(A)        │ Growing Phase
    LOCK(B)        │
    READ(B)        │
    WRITE(A)     ◄─┘ Lock Point
    UNLOCK(B)    ◄─┐
    WRITE(B)       │ Shrinking Phase
    UNLOCK(A)      │
    COMMIT       ◄─┘
```

**Why this matters:**
```
BAD (Not 2PL):
T1: LOCK(A), READ(A), UNLOCK(A), LOCK(B), READ(B)
    ^                ^           ^
    Growing          Shrinking   Growing again! (ILLEGAL)

This violates 2PL and can lead to non-serializable execution!
```

---

## Lock Types and Compatibility

🎓 **PROFESSOR**: 2PL uses **multiple lock modes** for efficiency.

### Basic Lock Modes:

```
┌─────────────────────────────────────────────────────┐
│ Lock Mode        When Acquired      Conflicts With  │
├─────────────────────────────────────────────────────┤
│ Shared (S)       READ operations    X (Exclusive)   │
│ Exclusive (X)    WRITE operations   S, X (Both)     │
└─────────────────────────────────────────────────────┘
```

**Compatibility Matrix:**
```
         │  S  │  X  │
    ─────┼─────┼─────┤
      S  │  ✓  │  ✗  │
    ─────┼─────┼─────┤
      X  │  ✗  │  ✗  │
    ─────┴─────┴─────┘

✓ = Compatible (can coexist)
✗ = Incompatible (one must wait)
```

### Extended Lock Modes:

```
┌────────────────────────────────────────────────────────────┐
│ Intent Locks (for hierarchical locking):                   │
│                                                             │
│ IS (Intent Shared)      - Intent to acquire S at lower lvl │
│ IX (Intent Exclusive)   - Intent to acquire X at lower lvl │
│ SIX (Shared + Intent X) - S on node + IX on children       │
└────────────────────────────────────────────────────────────┘
```

**Full Compatibility Matrix:**
```
         │  IS │  IX │  S  │ SIX │  X  │
    ─────┼─────┼─────┼─────┼─────┼─────┤
     IS  │  ✓  │  ✓  │  ✓  │  ✓  │  ✗  │
    ─────┼─────┼─────┼─────┼─────┼─────┤
     IX  │  ✓  │  ✓  │  ✗  │  ✗  │  ✗  │
    ─────┼─────┼─────┼─────┼─────┼─────┤
      S  │  ✓  │  ✗  │  ✓  │  ✗  │  ✗  │
    ─────┼─────┼─────┼─────┼─────┼─────┤
    SIX  │  ✓  │  ✗  │  ✗  │  ✗  │  ✗  │
    ─────┼─────┼─────┼─────┼─────┼─────┤
      X  │  ✗  │  ✗  │  ✗  │  ✗  │  ✗  │
    ─────┴─────┴─────┴─────┴─────┴─────┘
```

---

### Hierarchical Locking Example:

```java
public class HierarchicalLockManager {
    /**
     * Lock hierarchy: Database → Table → Page → Row
     */

    public void lockRowForRead(String dbName, String tableName, String rowId) {

        // Acquire intent locks down the hierarchy
        lockManager.acquireLock(dbName, LockMode.IS);        // DB level
        lockManager.acquireLock(tableName, LockMode.IS);     // Table level
        lockManager.acquireLock(rowId, LockMode.S);          // Row level (actual read)

        // Now can read row
    }

    public void lockRowForWrite(String dbName, String tableName, String rowId) {

        // Acquire intent exclusive locks
        lockManager.acquireLock(dbName, LockMode.IX);        // DB level
        lockManager.acquireLock(tableName, LockMode.IX);     // Table level
        lockManager.acquireLock(rowId, LockMode.X);          // Row level (actual write)

        // Now can write row
    }

    public void lockTableForFullScan(String dbName, String tableName) {

        // Shared lock on entire table (no row locks needed!)
        lockManager.acquireLock(dbName, LockMode.IS);
        lockManager.acquireLock(tableName, LockMode.S);      // Table-level S lock

        // Can read all rows without individual row locks
    }
}
```

**Why Intent Locks?**
```
Without Intent Locks:
- Transaction T1 wants to X-lock entire table
- Must check EVERY row for existing locks (slow!)

With Intent Locks:
- T2 has X-lock on one row → Sets IX lock on table
- T1 sees IX lock on table → Knows rows are locked
- No need to check individual rows!
```

---

## 2PL Variants

🏗️ **ARCHITECT**: Different **2PL variants** optimize for different trade-offs.

### 1. **Basic 2PL** (Textbook Version)

**Properties:**
- Locks released after transaction completes
- Serializable but NOT deadlock-free
- NOT cascadeless (cascading aborts possible)

```java
// Basic 2PL - can have cascading aborts

@Transactional
public void basic2PL() {

    // Growing Phase
    Lock lock1 = lockManager.acquire("resource1", LockMode.X);
    resource1.update();

    // Start Shrinking Phase early
    lockManager.release(lock1);  // Release before commit!

    // PROBLEM: If transaction aborts here, other transactions
    // that read resource1 must also abort (cascading abort)

    Lock lock2 = lockManager.acquire("resource2", LockMode.X);  // ILLEGAL - already shrinking!
}
```

---

### 2. **Strict 2PL (S2PL)** - Most Common in Practice

**Properties:**
- **ALL locks held until COMMIT/ABORT**
- Serializable AND cascadeless
- Prevents dirty reads
- **Default in most databases**

```java
// Strict 2PL - Industry Standard
public class Strict2PLTransaction {

    private Set<Lock> heldLocks = new HashSet<>();
    private boolean committed = false;

    public void execute() {
        try {
            // Growing Phase - acquire all locks
            Lock lock1 = lockManager.acquire("account1", LockMode.X);
            heldLocks.add(lock1);

            Lock lock2 = lockManager.acquire("account2", LockMode.X);
            heldLocks.add(lock2);

            // Perform operations
            performTransfer();

            // Commit
            committed = true;

        } finally {
            // Shrinking Phase - release ALL locks at once
            // Happens ONLY at commit/abort
            releaseAllLocks();
        }
    }

    private void releaseAllLocks() {
        for (Lock lock : heldLocks) {
            lockManager.release(lock);
        }
        heldLocks.clear();
    }
}
```

**Benefits of Strict 2PL:**
```
1. No Dirty Reads: Other transactions never see uncommitted data
2. No Cascading Aborts: Abort of T1 doesn't force abort of T2
3. Simpler Recovery: Undo operations only for aborted transaction
```

---

### 3. **Rigorous 2PL** (Strongest Variant)

**Properties:**
- ALL locks held until **COMMIT** (not just abort)
- Read locks also held until commit
- Even stricter than S2PL

```
Comparison:

Strict 2PL:
- X-locks held until commit/abort
- S-locks can be released earlier (but usually held)

Rigorous 2PL:
- ALL locks (S and X) held until commit
- No early release ever
```

---

### 4. **Conservative 2PL** (Deadlock-Free!)

**Properties:**
- Acquire **ALL locks at BEGIN** (before reading data)
- Never blocks during execution
- Deadlock-free but impractical

```java
public class Conservative2PL {

    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        // Acquire ALL locks BEFORE reading data
        Set<Long> allResources = Set.of(fromId, toId);
        Map<Long, Lock> locks = lockManager.acquireAll(allResources, LockMode.X);

        try {
            // Now execute transaction (never blocks!)
            Account from = accountRepo.findById(fromId);
            Account to = accountRepo.findById(toId);

            from.debit(amount);
            to.credit(amount);

            accountRepo.saveAll(List.of(from, to));

        } finally {
            lockManager.releaseAll(locks.values());
        }
    }
}
```

**Problem:** Must know all resources in advance (often impossible!).

---

## Deadlock Detection and Prevention

🎓 **PROFESSOR**: **Deadlocks** are the Achilles' heel of 2PL.

### What is Deadlock?

**Definition**: Circular wait where transactions block each other indefinitely.

```
Classic Deadlock Scenario:

T1: LOCK(A) ──────┐
    ...           │
    LOCK(B) ──┐   │ (T1 waits for B, held by T2)
              │   │
T2: LOCK(B) ──┼───┘
    ...       │
    LOCK(A) ──┘ (T2 waits for A, held by T1)

→ DEADLOCK! Both wait forever 💀
```

**Wait-for Graph:**
```
    T1 ──waits for──→ T2
     ↑                 │
     │                 │
     │                 ↓
     └───waits for──── (cycle!)

Cycle in wait-for graph = Deadlock
```

---

### Deadlock Prevention Strategies:

**1. Lock Ordering (Application-Level)**

```java
@Service
public class DeadlockPreventionService {

    /**
     * Always acquire locks in same order (prevents cycles)
     */
    @Transactional
    public void transferOrdered(Long fromId, Long toId, BigDecimal amount) {

        // Order lock acquisition by ID (consistent across all transactions)
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        // Always lock lower ID first
        Account first = accountRepo.findByIdForUpdate(firstId);
        Account second = accountRepo.findByIdForUpdate(secondId);

        // Now determine which is 'from' and which is 'to'
        Account from = (fromId.equals(firstId)) ? first : second;
        Account to = (fromId.equals(firstId)) ? second : first;

        from.debit(amount);
        to.credit(amount);

        accountRepo.saveAll(List.of(from, to));
    }
}
```

**Why this works:**
```
All transactions acquire: A → B (in order)

T1: LOCK(A) → LOCK(B) ✓
T2: LOCK(A) [waits for T1] → LOCK(B) ✓

No cycle possible!
```

---

**2. Timeout (Practical Approach)**

```java
public class TimeoutDeadlockPrevention {

    @Transactional(timeout = 5)  // 5 second timeout
    @Retryable(
        value = { DeadlockLoserDataAccessException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2)
    )
    public void transferWithTimeout(Long fromId, Long toId, BigDecimal amount) {

        try {
            Account from = accountRepo.findByIdForUpdate(fromId);
            Account to = accountRepo.findByIdForUpdate(toId);

            from.debit(amount);
            to.credit(amount);

            accountRepo.saveAll(List.of(from, to));

        } catch (LockTimeoutException e) {
            // Timeout occurred - possible deadlock
            logger.warn("Lock timeout, retrying...");
            throw new DeadlockLoserDataAccessException("Deadlock suspected", e);
        }
    }
}
```

---

**3. Wait-Die / Wound-Wait (Timestamp Ordering)**

```python
class WaitDieProtocol:
    """
    Older transactions have priority
    """

    def request_lock(self, transaction, resource):
        holder = self.lock_holders.get(resource)

        if holder is None:
            # Grant lock
            self.grant_lock(transaction, resource)
            return True

        if transaction.timestamp < holder.timestamp:
            # Requesting transaction is OLDER → WAIT
            return False  # Block until lock available

        else:
            # Requesting transaction is YOUNGER → DIE
            self.abort_transaction(transaction)
            raise TransactionAbortedException("Younger transaction must die")


class WoundWaitProtocol:
    """
    Older transactions can preempt younger ones
    """

    def request_lock(self, transaction, resource):
        holder = self.lock_holders.get(resource)

        if holder is None:
            self.grant_lock(transaction, resource)
            return True

        if transaction.timestamp < holder.timestamp:
            # Requesting transaction is OLDER → WOUND holder
            self.abort_transaction(holder)  # Force holder to abort
            self.grant_lock(transaction, resource)
            return True

        else:
            # Requesting transaction is YOUNGER → WAIT
            return False  # Block
```

---

**4. Deadlock Detection (Cycle Detection)**

```java
@Component
public class DeadlockDetector {

    @Scheduled(fixedRate = 1000)  // Check every second
    public void detectDeadlocks() {

        // Build wait-for graph
        Map<Long, Set<Long>> waitForGraph = buildWaitForGraph();

        // Detect cycles using DFS
        Set<Long> deadlockedTransactions = detectCycles(waitForGraph);

        if (!deadlockedTransactions.isEmpty()) {
            // Choose victim and abort
            Long victim = chooseVictim(deadlockedTransactions);

            logger.warn("Deadlock detected, aborting transaction {}", victim);
            transactionManager.abort(victim);

            // Metrics
            metrics.counter("db.deadlocks").increment();
        }
    }

    private Map<Long, Set<Long>> buildWaitForGraph() {
        Map<Long, Set<Long>> graph = new HashMap<>();

        // Query database lock tables
        String query = """
            SELECT
                waiting_tx.transaction_id AS waiter,
                holding_tx.transaction_id AS holder
            FROM pg_locks waiting
            JOIN pg_locks holding ON waiting.locktype = holding.locktype
                AND waiting.relation = holding.relation
            WHERE NOT waiting.granted AND holding.granted
            """;

        jdbcTemplate.query(query, rs -> {
            Long waiter = rs.getLong("waiter");
            Long holder = rs.getLong("holder");

            graph.computeIfAbsent(waiter, k -> new HashSet<>()).add(holder);
        });

        return graph;
    }

    private Set<Long> detectCycles(Map<Long, Set<Long>> graph) {
        Set<Long> visited = new HashSet<>();
        Set<Long> recursionStack = new HashSet<>();
        Set<Long> cycleNodes = new HashSet<>();

        for (Long node : graph.keySet()) {
            if (hasCycleDFS(node, graph, visited, recursionStack, cycleNodes)) {
                return cycleNodes;
            }
        }

        return Collections.emptySet();
    }

    private boolean hasCycleDFS(
        Long node,
        Map<Long, Set<Long>> graph,
        Set<Long> visited,
        Set<Long> recursionStack,
        Set<Long> cycleNodes
    ) {
        if (recursionStack.contains(node)) {
            // Cycle found!
            cycleNodes.add(node);
            return true;
        }

        if (visited.contains(node)) {
            return false;
        }

        visited.add(node);
        recursionStack.add(node);

        for (Long neighbor : graph.getOrDefault(node, Collections.emptySet())) {
            if (hasCycleDFS(neighbor, graph, visited, recursionStack, cycleNodes)) {
                cycleNodes.add(node);
                return true;
            }
        }

        recursionStack.remove(node);
        return false;
    }

    private Long chooseVictim(Set<Long> deadlockedTransactions) {
        // Strategy 1: Youngest transaction (least work done)
        // Strategy 2: Transaction with fewest locks
        // Strategy 3: Transaction that's been waiting shortest time

        return deadlockedTransactions.stream()
            .min(Comparator.comparing(txId ->
                transactionManager.getTransaction(txId).getStartTime()
            ))
            .orElseThrow();
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Key Questions:
1. Need true serializability?
   → Yes: 2PL (or SSI as alternative)
   → No: Weaker isolation sufficient

2. Can tolerate deadlocks?
   → Yes: S2PL with deadlock detection
   → No: Consider SSI or application-level coordination

3. Workload characteristics?
   → High contention: 2PL struggles (low throughput)
   → Low contention: 2PL works well

4. Read:Write ratio?
   → Read-heavy: Shared locks help, but MVCC better
   → Write-heavy: 2PL performs poorly

Example:
"For a banking core system with high correctness requirements
and moderate concurrency, Strict 2PL is appropriate. We'll
implement deadlock detection with victim selection."
```

### 2. Capacity Estimation (RADIO: Scale)

```
Lock Manager Overhead:
- Locks per transaction: 10-100 (average 30)
- Concurrent transactions: 1,000
- Total locks: 1,000 * 30 = 30,000 locks
- Lock memory: 30,000 * 64 bytes = 1.92 MB

Blocking Time:
- Average lock hold time: 50ms
- Lock contention rate: 10%
- Average wait time: 5ms (10% of 50ms)
- Effective transaction latency: 50ms + 5ms = 55ms

Deadlock Rate:
- Expected: 1-5% with high contention
- Detection cycle: 1 second
- Victim retry overhead: 1.01-1.05x work

Throughput:
- Optimistic (no contention): 10,000 TPS
- Realistic (10% contention): 7,000 TPS
- Pessimistic (30% contention): 3,000 TPS
```

### 3. Data Model (RADIO: Data-Model)

```java
// Domain Model: Explicit 2PL Lock Management

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private Long id;

    private BigDecimal balance;

    @Version  // Optimistic version (backup to 2PL)
    private Long version;

    private Instant lastModified;
}

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    /**
     * Pessimistic write lock (X-lock in 2PL)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * Pessimistic read lock (S-lock in 2PL)
     */
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForRead(@Param("id") Long id);

    /**
     * Lock with timeout (deadlock prevention)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({
        @QueryHint(name = "javax.persistence.lock.timeout", value = "5000")
    })
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> findByIdForUpdateWithTimeout(@Param("id") Long id);
}

@Service
public class AccountService {

    /**
     * Transfer with Strict 2PL semantics
     */
    @Transactional(
        isolation = Isolation.SERIALIZABLE,
        timeout = 10
    )
    @Retryable(
        value = { DeadlockLoserDataAccessException.class },
        maxAttempts = 3
    )
    public void transfer(Long fromId, Long toId, BigDecimal amount) {

        // Deadlock prevention: Lock in consistent order
        Long firstId = Math.min(fromId, toId);
        Long secondId = Math.max(fromId, toId);

        // Growing Phase: Acquire locks
        Account first = accountRepo.findByIdForUpdate(firstId)
            .orElseThrow(() -> new AccountNotFoundException(firstId));

        Account second = accountRepo.findByIdForUpdate(secondId)
            .orElseThrow(() -> new AccountNotFoundException(secondId));

        // Determine from/to based on original IDs
        Account from = fromId.equals(firstId) ? first : second;
        Account to = fromId.equals(firstId) ? second : first;

        // Execute transfer
        if (from.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        from.setBalance(from.getBalance().subtract(amount));
        to.setBalance(to.getBalance().add(amount));

        accountRepo.saveAll(List.of(from, to));

        // Shrinking Phase: All locks released at COMMIT
    }

    /**
     * Read balance (shared lock)
     */
    @Transactional(
        readOnly = true,
        isolation = Isolation.SERIALIZABLE
    )
    public BigDecimal getBalance(Long accountId) {

        // Acquire S-lock (shared)
        Account account = accountRepo.findByIdForRead(accountId)
            .orElseThrow();

        return account.getBalance();
        // S-lock released at commit (Strict 2PL)
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER                        │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐            │
│  │Transaction │  │Transaction │  │Transaction │            │
│  │    T1      │  │    T2      │  │    T3      │            │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘            │
│        │                │                │                   │
│        └────────────────┼────────────────┘                   │
│                         ↓                                    │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                   LOCK MANAGER                              │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Lock Table                                          │   │
│  │  ┌────────────┬──────────┬─────────────┬──────────┐ │   │
│  │  │ Resource   │ Mode     │ Holder      │ Waiters  │ │   │
│  │  ├────────────┼──────────┼─────────────┼──────────┤ │   │
│  │  │ account:1  │ X        │ T1          │ [T3]     │ │   │
│  │  │ account:2  │ S        │ [T1, T2]    │ []       │ │   │
│  │  │ account:3  │ X        │ T2          │ [T1]     │ │   │
│  │  └────────────┴──────────┴─────────────┴──────────┘ │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  Deadlock Detector                                   │   │
│  │  Wait-for Graph: T1 → T2 → T3 → T1 (CYCLE!)        │   │
│  │  Action: Abort youngest transaction (T3)            │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│                  STORAGE LAYER                              │
│  [Data Pages] [Undo Log] [Redo Log]                        │
└─────────────────────────────────────────────────────────────┘

Transaction Flow:
1. BEGIN → Enter growing phase
2. SELECT FOR UPDATE → Acquire locks, wait if blocked
3. UPDATE → Modify data
4. COMMIT → Release all locks (shrinking phase)
5. Deadlock detector runs periodically, aborts victims
```

### 5. Deep Dives (RADIO: Optimize)

**A. Lock Manager Implementation**

```java
@Component
public class LockManager {

    private final ConcurrentMap<String, LockEntry> locks = new ConcurrentHashMap<>();

    static class LockEntry {
        LockMode mode;
        Set<Long> holders = ConcurrentHashMap.newKeySet();
        Queue<LockRequest> waiters = new ConcurrentLinkedQueue<>();
    }

    static class LockRequest {
        long txId;
        LockMode mode;
        CompletableFuture<Boolean> future = new CompletableFuture<>();
    }

    /**
     * Acquire lock (blocking)
     */
    public boolean acquireLock(long txId, String resource, LockMode mode, long timeoutMs) {

        LockEntry entry = locks.computeIfAbsent(resource, k -> new LockEntry());

        synchronized (entry) {
            // Check compatibility
            if (isCompatible(entry, mode)) {
                // Grant immediately
                grantLock(entry, txId, mode);
                return true;
            } else {
                // Must wait
                LockRequest request = new LockRequest();
                request.txId = txId;
                request.mode = mode;

                entry.waiters.add(request);

                // Wait with timeout
                try {
                    return request.future.get(timeoutMs, TimeUnit.MILLISECONDS);
                } catch (TimeoutException e) {
                    entry.waiters.remove(request);
                    throw new LockTimeoutException("Lock timeout for " + resource);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    /**
     * Release lock
     */
    public void releaseLock(long txId, String resource) {

        LockEntry entry = locks.get(resource);
        if (entry == null) {
            return;
        }

        synchronized (entry) {
            entry.holders.remove(txId);

            if (entry.holders.isEmpty()) {
                entry.mode = null;
            }

            // Wake up waiters
            processWaiters(entry);

            // Clean up if no holders and waiters
            if (entry.holders.isEmpty() && entry.waiters.isEmpty()) {
                locks.remove(resource);
            }
        }
    }

    /**
     * Release all locks for transaction (called at commit/abort)
     */
    public void releaseAllLocks(long txId) {
        // Iterate over all locks and release
        locks.forEach((resource, entry) -> {
            synchronized (entry) {
                if (entry.holders.contains(txId)) {
                    releaseLock(txId, resource);
                }
            }
        });
    }

    private boolean isCompatible(LockEntry entry, LockMode requestMode) {
        if (entry.mode == null || entry.holders.isEmpty()) {
            return true;  // No current lock
        }

        // Check compatibility matrix
        return COMPATIBILITY_MATRIX[entry.mode.ordinal()][requestMode.ordinal()];
    }

    private void grantLock(LockEntry entry, long txId, LockMode mode) {
        if (entry.mode == null) {
            entry.mode = mode;
        } else if (mode == LockMode.SHARED && entry.mode == LockMode.SHARED) {
            // Multiple S-locks allowed
        } else {
            // Should not happen if compatibility checked
            throw new IllegalStateException("Incompatible lock granted");
        }

        entry.holders.add(txId);
    }

    private void processWaiters(LockEntry entry) {
        Iterator<LockRequest> it = entry.waiters.iterator();

        while (it.hasNext()) {
            LockRequest request = it.next();

            if (isCompatible(entry, request.mode)) {
                it.remove();
                grantLock(entry, request.txId, request.mode);
                request.future.complete(true);
            } else {
                // First incompatible waiter blocks rest
                break;
            }
        }
    }

    private static final boolean[][] COMPATIBILITY_MATRIX = {
        // S, X
        { true, false },  // S
        { false, false }  // X
    };

    public enum LockMode {
        SHARED, EXCLUSIVE
    }
}
```

**B. Monitoring 2PL Performance**

```java
@Component
public class TwoPhaseLocklockingMetrics {

    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {

        // Lock wait time
        String query = """
            SELECT
                relation::regclass AS table_name,
                mode,
                COUNT(*) AS lock_count,
                AVG(EXTRACT(EPOCH FROM (NOW() - locktype))) AS avg_wait_seconds
            FROM pg_locks
            WHERE NOT granted
            GROUP BY relation, mode
            """;

        jdbcTemplate.query(query, rs -> {
            String table = rs.getString("table_name");
            String mode = rs.getString("mode");
            long count = rs.getLong("lock_count");
            double avgWait = rs.getDouble("avg_wait_seconds");

            registry.gauge("db.lock.waiting",
                Tags.of("table", table, "mode", mode),
                count
            );

            registry.gauge("db.lock.wait_time_seconds",
                Tags.of("table", table, "mode", mode),
                avgWait
            );
        });

        // Deadlock count
        Long deadlocks = jdbcTemplate.queryForObject(
            "SELECT deadlocks FROM pg_stat_database WHERE datname = current_database()",
            Long.class
        );

        registry.counter("db.deadlocks").increment(deadlocks);

        // Lock table size
        Long lockCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM pg_locks",
            Long.class
        );

        registry.gauge("db.locks.total", lockCount);
    }
}
```

---

## 🧠 MIND MAP: TWO PHASE LOCKING

```
                TWO PHASE LOCKING (2PL)
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
    PHASES           LOCK MODES          DEADLOCKS
        |                 |                  |
   ┌────┴────┐       ┌────┴────┐       ┌────┴────┐
   ↓         ↓       ↓         ↓       ↓         ↓
Growing  Shrinking  Shared  Exclusive Detection Prevention
 Phase     Phase    (S)       (X)
   |         |                              |
Acquire  Release   Intent   Compat    Wait-For  Lock
Locks    Locks     Locks    Matrix    Graph    Ordering
         @COMMIT
                            IS,IX,
                            SIX
```

### 💡 EMOTIONAL ANCHORS (For Subconscious Power)

1. **2PL = Classroom Door Lock 🚪**
   - Growing: Students enter (locks acquired)
   - Shrinking: Students leave (locks released)
   - Can't re-enter once class dismisses (no acquire after release)

2. **Deadlock = Traffic Intersection Jam 🚗**
   - 4 cars, each blocking another
   - Circular wait, nobody moves
   - Solution: One car backs up (victim)

3. **Lock Compatibility = Shared Reading Room 📖**
   - Multiple readers OK (S + S compatible)
   - Writer needs exclusive access (X incompatible with all)
   - Intent locks = "Reserved" sign

4. **Strict 2PL = Hold Until Graduation 🎓**
   - Keep all resources until completion
   - No early release
   - Clean, simple, safe

---

## 🎯 INTERVIEW GOLD: KEY TALKING POINTS

1. **2PL vs MVCC**: "2PL is pessimistic (lock first), MVCC is optimistic (detect conflict later). 2PL has lower throughput but predictable latency."

2. **Strict 2PL default**: "Most databases use Strict 2PL - hold all locks until commit. Prevents dirty reads and cascading aborts."

3. **Deadlock inevitability**: "With 2PL, deadlocks are inevitable under contention. Need detection (cycle) or prevention (timeout, ordering)."

4. **Lock granularity**: "Hierarchical locking (DB → Table → Page → Row) with intent locks prevents checking every row for conflicts."

5. **Performance**: "2PL throughput degrades significantly under contention. At 30% conflict rate, can see 50% throughput reduction."

6. **Modern alternative**: "PostgreSQL's SSI often outperforms 2PL for serializable isolation while avoiding deadlocks."

---

## 📚 FURTHER READING

**Academic Papers:**
- "Concurrency Control and Recovery in Database Systems" (Bernstein, Hadzilacos, Goodman, 1987) - **Classic**
- "Granularity of Locks in a Shared Data Base" (Gray et al., 1975)

**Database Documentation:**
- PostgreSQL Explicit Locking: https://www.postgresql.org/docs/current/explicit-locking.html

**Books:**
- "Transaction Processing" by Jim Gray & Andreas Reuter - **Essential**
- "Database Internals" by Alex Petrov (Chapter 5)
