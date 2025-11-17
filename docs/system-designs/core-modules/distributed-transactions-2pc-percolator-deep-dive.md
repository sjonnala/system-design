# Distributed Transactions: Two-Phase Commit and Percolator Deep Dive

## Contents

- [Distributed Transactions: Two-Phase Commit and Percolator Deep Dive](#distributed-transactions-two-phase-commit-and-percolator-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Distributed Transaction Problem](#the-distributed-transaction-problem)
    - [Two-Phase Commit (2PC) Protocol](#two-phase-commit-2pc-protocol)
    - [2PC Failure Modes and Recovery](#2pc-failure-modes-and-recovery)
    - [Three-Phase Commit (3PC)](#three-phase-commit-3pc)
    - [Google Percolator - Large-Scale Incremental Processing](#google-percolator---large-scale-incremental-processing)
    - [Percolator Transactions](#percolator-transactions)
    - [Spanner - The Evolution](#spanner---the-evolution)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: DISTRIBUTED TRANSACTIONS](#mind-map-distributed-transactions)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Distributed Transaction = ACID Across Multiple Databases

The Challenge:
┌────────────────────────────────────────────────────────────┐
│ Atomicity: All-or-nothing across multiple systems          │
│ Consistency: Maintain invariants across systems            │
│ Isolation: Concurrent transactions don't interfere         │
│ Durability: Committed changes survive crashes              │
└────────────────────────────────────────────────────────────┘

Example: Transfer $100 from Bank A to Bank B
────────────────────────────────────────────

Without Distributed Transaction:
  1. Deduct $100 from Bank A  ✓
  2. Network failure ✗
  3. Bank B never gets $100
  → Money lost! Inconsistency!

With Distributed Transaction (2PC):
  Phase 1: PREPARE
    Coordinator: "Can you deduct $100, Bank A?"
    Bank A: "Yes, locked and ready"
    Coordinator: "Can you add $100, Bank B?"
    Bank B: "Yes, locked and ready"

  Phase 2: COMMIT
    Coordinator: "Everyone commit!"
    Bank A: Deducts $100 ✓
    Bank B: Adds $100 ✓
  → Atomic! Either both succeed or both abort
```

**The Trade-off:**
```
Single-Database Transaction    Distributed Transaction
───────────────────────────    ──────────────────────
Fast (local operation)         Slow (network roundtrips)
Simple (DBMS handles it)       Complex (coordinator needed)
Always available               Can block indefinitely
Low latency (~1ms)             High latency (~100ms)

When to use distributed transactions:
  ✓ Financial transfers (atomicity critical)
  ✓ Inventory reservation (consistency critical)
  ✗ Social media posts (eventual consistency OK)
  ✗ Analytics (performance > consistency)
```

---

## The Distributed Transaction Problem

🎓 **PROFESSOR**: Why are distributed transactions hard?

### The Atomicity Challenge

```
Problem: Achieving all-or-nothing across multiple nodes
───────────────────────────────────────────────────────

Single Node:
  BEGIN TRANSACTION
    UPDATE accounts SET balance = balance - 100 WHERE id = 1;
    UPDATE accounts SET balance = balance + 100 WHERE id = 2;
  COMMIT;

  → Database guarantees atomicity (both or neither)

Multiple Nodes:
  Node A: UPDATE accounts SET balance = balance - 100 WHERE id = 1;
  Node B: UPDATE accounts SET balance = balance + 100 WHERE id = 2;

  What if:
  ✗ Node A commits, Node B crashes → Inconsistency!
  ✗ Node A commits, network partition → Can't reach Node B
  ✗ Both prepare to commit, coordinator crashes → Uncertainty!

  → Need distributed commit protocol
```

### Real-World Example

```java
/**
 * E-commerce checkout across multiple services
 */
public class DistributedCheckout {

    /**
     * WITHOUT distributed transactions
     */
    public void checkoutWithoutDTx(Order order) {
        // 1. Reserve inventory
        boolean inventoryReserved = inventoryService.reserve(order.items);

        // 2. Charge payment
        boolean paymentSuccess = paymentService.charge(order.total);

        // 3. Create order
        boolean orderCreated = orderService.create(order);

        /**
         * Problem: What if payment succeeds but order creation fails?
         * - Customer charged but no order
         * - Manual reconciliation needed
         * - Customer service nightmare!
         *
         * Common in microservices (no ACID guarantees)
         */
    }

    /**
     * WITH distributed transactions (2PC)
     */
    public void checkoutWith2PC(Order order) throws TransactionException {
        TransactionCoordinator coordinator = new TransactionCoordinator();

        // Phase 1: PREPARE (ask all participants to prepare)
        coordinator.prepare(inventoryService, "reserve", order.items);
        coordinator.prepare(paymentService, "charge", order.total);
        coordinator.prepare(orderService, "create", order);

        // All participants vote YES → proceed to commit
        // Any participant votes NO → abort all

        // Phase 2: COMMIT (tell all participants to commit)
        coordinator.commit();

        /**
         * Guarantee: Either all succeed or all abort
         * - No partial failures
         * - ACID across services
         *
         * Cost: Higher latency, complexity, blocking
         */
    }
}
```

---

## Two-Phase Commit (2PC) Protocol

🎓 **PROFESSOR**: The classic distributed commit protocol:

### Protocol Overview

```
Participants:
  - Coordinator: Orchestrates the transaction
  - Participants: Databases/services involved in transaction

┌────────────────────────────────────────────────────────────┐
│ Phase 1: PREPARE (Voting Phase)                            │
│                                                             │
│ Coordinator → Participants: "Can you commit?"              │
│ Participants → Coordinator: "YES, I can" or "NO, I can't" │
│                                                             │
│ If ALL vote YES:                                           │
│   → Proceed to Phase 2                                     │
│ If ANY votes NO (or timeout):                              │
│   → ABORT transaction                                      │
└────────────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────────────┐
│ Phase 2: COMMIT/ABORT (Decision Phase)                     │
│                                                             │
│ Coordinator → Participants: "COMMIT" (or "ABORT")          │
│ Participants: Execute decision, reply "ACK"                │
│ Coordinator: Transaction complete                          │
└────────────────────────────────────────────────────────────┘
```

### Detailed Protocol

```python
class TwoPhaseCommit:
    """
    Two-Phase Commit implementation
    """

    class Coordinator:
        def __init__(self, participants):
            self.participants = participants
            self.transaction_log = []  # Durable log

        def execute_transaction(self, transaction):
            """
            Execute distributed transaction using 2PC
            """
            # Step 1: Execute transaction on all participants (locally)
            for participant in self.participants:
                participant.execute_locally(transaction)

            # Phase 1: PREPARE
            if not self.prepare_phase():
                # Any participant voted NO → ABORT
                self.abort_phase()
                return False

            # Phase 2: COMMIT
            self.commit_phase()
            return True

        def prepare_phase(self):
            """
            Phase 1: Ask all participants if they can commit
            """
            # Log: PREPARING
            self.transaction_log.append(("PREPARING", time.now()))

            votes = []

            for participant in self.participants:
                # Send PREPARE message
                vote = participant.prepare()

                if vote == "YES":
                    votes.append(True)
                elif vote == "NO":
                    votes.append(False)
                else:
                    # Timeout → treat as NO
                    votes.append(False)

            # Decision: All YES → commit, else abort
            all_yes = all(votes)

            if all_yes:
                # Log decision: COMMIT
                self.transaction_log.append(("COMMIT", time.now()))
            else:
                # Log decision: ABORT
                self.transaction_log.append(("ABORT", time.now()))

            return all_yes

        def commit_phase(self):
            """
            Phase 2: Tell all participants to commit
            """
            for participant in self.participants:
                participant.commit()

            # Log: COMMITTED
            self.transaction_log.append(("COMMITTED", time.now()))

        def abort_phase(self):
            """
            Phase 2 (alternate): Tell all participants to abort
            """
            for participant in self.participants:
                participant.abort()

            # Log: ABORTED
            self.transaction_log.append(("ABORTED", time.now()))

    class Participant:
        def __init__(self, node_id):
            self.node_id = node_id
            self.transaction_log = []  # Durable log
            self.locked_resources = set()
            self.undo_log = []  # For rollback

        def execute_locally(self, transaction):
            """
            Execute transaction operations locally (not committed yet)
            """
            for operation in transaction.operations:
                # Acquire locks on resources
                self.locked_resources.add(operation.resource)

                # Save undo information
                old_value = self.read(operation.resource)
                self.undo_log.append((operation.resource, old_value))

                # Execute operation (but don't commit)
                self.write(operation.resource, operation.new_value)

        def prepare(self):
            """
            Participant receives PREPARE request
            """
            # Can I commit?
            # - Check if all locks acquired
            # - Check if operations are valid
            # - Check if enough resources (disk space, etc.)

            if self.can_commit():
                # Log: PREPARED
                self.transaction_log.append(("PREPARED", time.now()))

                # Force log to disk (CRITICAL!)
                self.flush_log()

                # Vote YES
                return "YES"
            else:
                # Cannot commit → release locks
                self.release_locks()

                # Vote NO
                return "NO"

        def commit(self):
            """
            Participant receives COMMIT request
            """
            # Log: COMMITTING
            self.transaction_log.append(("COMMITTING", time.now()))

            # Make changes durable
            self.flush_changes_to_disk()

            # Log: COMMITTED
            self.transaction_log.append(("COMMITTED", time.now()))
            self.flush_log()

            # Release locks
            self.release_locks()

        def abort(self):
            """
            Participant receives ABORT request
            """
            # Log: ABORTING
            self.transaction_log.append(("ABORTING", time.now()))

            # Rollback using undo log
            for resource, old_value in reversed(self.undo_log):
                self.write(resource, old_value)

            # Log: ABORTED
            self.transaction_log.append(("ABORTED", time.now()))
            self.flush_log()

            # Release locks
            self.release_locks()
```

### Message Flow Diagram

```
Happy Path (All YES):
────────────────────

Coordinator          Participant A       Participant B
    │                     │                    │
    │───PREPARE──────────→│                    │
    │                     │                    │
    │                     │←──Execute locally──│
    │                     │←──Acquire locks────│
    │                     │←──Write undo log───│
    │                     │                    │
    │←──YES───────────────│                    │
    │                                          │
    │───PREPARE───────────────────────────────→│
    │                                          │
    │                                          │←Execute locally
    │                                          │←Acquire locks
    │                                          │←Write undo log
    │                                          │
    │←──YES────────────────────────────────────│
    │                                          │
    │ (Decision: COMMIT)                       │
    │                                          │
    │───COMMIT────────────→│                    │
    │                     │                    │
    │                     │←──Flush to disk────│
    │                     │←──Release locks────│
    │                     │                    │
    │←──ACK───────────────│                    │
    │                                          │
    │───COMMIT─────────────────────────────────→│
    │                                          │
    │                                          │←Flush to disk
    │                                          │←Release locks
    │                                          │
    │←──ACK─────────────────────────────────────│
    │                                          │
    │ TRANSACTION COMMITTED                     │


Abort Path (Any NO):
───────────────────

Coordinator          Participant A       Participant B
    │                     │                    │
    │───PREPARE──────────→│                    │
    │                     │                    │
    │←──YES───────────────│                    │
    │                                          │
    │───PREPARE───────────────────────────────→│
    │                                          │
    │←──NO──────────────────────────────────────│
    │                                          │
    │ (Decision: ABORT)                        │
    │                                          │
    │───ABORT─────────────→│                    │
    │                     │                    │
    │                     │←──Rollback─────────│
    │                     │←──Release locks────│
    │                     │                    │
    │←──ACK───────────────│                    │
    │                                          │
    │───ABORT──────────────────────────────────→│
    │                                          │
    │                                          │←Rollback
    │                                          │←Release locks
    │                                          │
    │←──ACK─────────────────────────────────────│
    │                                          │
    │ TRANSACTION ABORTED                       │
```

---

## 2PC Failure Modes and Recovery

🏗️ **ARCHITECT**: What happens when things go wrong?

### Failure Scenario 1: Coordinator Crashes After PREPARE

```
Timeline:
────────

t0: Coordinator sends PREPARE to all participants
t1: All participants vote YES, enter PREPARED state
t2: Coordinator CRASHES (before sending COMMIT)

State:
  Coordinator: Dead
  Participants: PREPARED (holding locks)

Problem: Participants are BLOCKED!
  - Can't commit (no COMMIT message from coordinator)
  - Can't abort (might be only one who thinks should abort)
  - Holding locks (blocking other transactions)

Recovery:
  1. Detect coordinator failure (timeout)
  2. Elect new coordinator
  3. New coordinator reads old coordinator's log
     - If log says "COMMIT" → send COMMIT to all
     - If log says "ABORT" → send ABORT to all
     - If log says "PREPARING" → send ABORT to all
  4. Participants receive decision, proceed

Duration: Participants blocked until coordinator recovers!
  → Major limitation of 2PC
```

### Failure Scenario 2: Participant Crashes After YES Vote

```python
class ParticipantRecovery:
    """
    How participant recovers after crash
    """

    def recover_after_crash(self):
        """
        Participant crashed after voting YES, recovers
        """
        # 1. Read transaction log from disk
        last_entry = self.read_log()

        if last_entry == "PREPARED":
            # Crashed after voting YES, before receiving decision
            # Don't know if transaction committed or aborted!
            # MUST contact coordinator

            decision = self.ask_coordinator_for_decision()

            if decision == "COMMIT":
                self.commit()
            else:
                self.abort()

        elif last_entry == "COMMITTED":
            # Already committed before crash
            # Nothing to do (changes are durable)
            pass

        elif last_entry == "ABORTED":
            # Already aborted before crash
            # Nothing to do
            pass

        else:
            # Never voted → safe to abort
            self.abort()

    def ask_coordinator_for_decision(self):
        """
        Ask coordinator what was decided
        """
        try:
            return coordinator.get_decision(self.transaction_id)
        except CoordinatorUnavailable:
            # Coordinator also crashed → BLOCKED!
            # Must wait for coordinator to recover
            self.wait_for_coordinator()
```

### Failure Scenario 3: Network Partition

```
Scenario: Partition between coordinator and some participants
─────────────────────────────────────────────────────────────

Initial state:
  Coordinator: {C}
  Participants: {P1, P2, P3}

Network partition:
  Partition 1: {C, P1, P2}
  Partition 2: {P3}

Timeline:
t0: Coordinator sends PREPARE to all
t1: P1, P2 receive PREPARE, vote YES
t2: P3 receives PREPARE, votes YES
t3: Coordinator receives votes from P1, P2 (2/3)
t4: Coordinator CANNOT reach P3 (partition)

Decision:
  Option 1: Timeout → ABORT (conservative)
    - Safe (maintains atomicity)
    - But P3 voted YES and is blocked!

  Option 2: Commit with 2/3 (dangerous!)
    - If P3 actually voted NO → violates atomicity

Correct behavior: ABORT on timeout
  - P3 will eventually time out and abort
  - Atomicity preserved (all abort)
```

### The Blocking Problem

```java
/**
 * 2PC's fundamental limitation: Blocking
 */
public class BlockingProblem {

    /**
     * Participants can be blocked indefinitely
     */
    public void demonstrateBlocking() {
        /**
         * State: Participant voted YES, holding locks
         * Waiting for: COMMIT or ABORT from coordinator
         *
         * If coordinator crashes:
         * - Participant CANNOT unilaterally decide
         * - Cannot commit (others might abort)
         * - Cannot abort (others might commit)
         * - MUST wait for coordinator to recover
         *
         * Meanwhile:
         * - Holding locks on resources
         * - Blocking other transactions
         * - System unavailable!
         *
         * Duration: Until coordinator recovers
         *           (could be hours!)
         */
    }

    /**
     * Real-world impact
     */
    public void realWorldImpact() {
        /**
         * E-commerce example:
         *
         * t0: Start checkout transaction
         * t1: Lock inventory (quantity=5)
         * t2: Lock payment gateway
         * t3: Coordinator crashes
         *
         * Result:
         * - Inventory locked (other customers can't buy)
         * - Payment gateway locked (other payments blocked)
         * - Customer's transaction stuck
         * - All waiting for coordinator to recover!
         *
         * This is why 2PC is rarely used in practice
         * (except when coordinator is highly available)
         */
    }
}
```

---

## Three-Phase Commit (3PC)

🎓 **PROFESSOR**: An attempt to solve 2PC's blocking problem:

### Protocol Overview

```
3PC adds an extra phase: PRE-COMMIT
───────────────────────────────────

Phase 1: CAN-COMMIT (voting)
  Coordinator → Participants: "Can you commit?"
  Participants → Coordinator: "YES" or "NO"

Phase 2: PRE-COMMIT (prepare to commit)
  If all YES:
    Coordinator → Participants: "PRE-COMMIT"
    Participants → Coordinator: "ACK"
  Else:
    ABORT

Phase 3: DO-COMMIT (actual commit)
  Coordinator → Participants: "DO-COMMIT"
  Participants: Commit and ACK

Key insight: PRE-COMMIT phase allows recovery without blocking!
```

### How 3PC Reduces Blocking

```python
class ThreePhaseCommit:
    """
    Three-Phase Commit implementation
    """

    def coordinator_logic(self):
        """
        Coordinator's perspective
        """
        # Phase 1: CAN-COMMIT
        votes = self.send_can_commit()

        if not all(votes):
            self.send_abort()
            return

        # Phase 2: PRE-COMMIT
        self.log("PRE-COMMIT")
        acks = self.send_pre_commit()

        if not all(acks):
            self.send_abort()
            return

        # Phase 3: DO-COMMIT
        self.log("DO-COMMIT")
        self.send_do_commit()

    def participant_recovery(self):
        """
        Participant crashed and recovered
        """
        last_entry = self.read_log()

        if last_entry == "PRE-COMMIT":
            # Key difference from 2PC!
            # If I'm in PRE-COMMIT state,
            # I know coordinator decided to commit

            # Ask other participants: "Are you in PRE-COMMIT state?"
            other_states = self.ask_other_participants()

            if any(state == "PRE-COMMIT" for state in other_states):
                # Someone else is in PRE-COMMIT
                # → Coordinator decided COMMIT
                self.commit()
            else:
                # No one else in PRE-COMMIT yet
                # → Safe to abort
                self.abort()

            # NO BLOCKING! Can decide without coordinator

        # ... other cases similar to 2PC

    """
    Why 3PC reduces blocking:
    ────────────────────────

    2PC: Participant in PREPARED state
         → Cannot decide without coordinator
         → BLOCKED

    3PC: Participant in PRE-COMMIT state
         → Knows coordinator decided to commit
         → Can ask other participants and decide
         → NOT BLOCKED (in theory)
    """
```

### 3PC Limitations

```
3PC's promises don't hold in practice:
─────────────────────────────────────

1. Network Partitions
   ┌─────────────────────────────────────────┐
   │ 3PC assumes: Failed node vs network     │
   │              partition are distinguishable│
   │                                          │
   │ Reality: They're not!                    │
   │                                          │
   │ Result: 3PC can violate safety in        │
   │         network partitions               │
   └─────────────────────────────────────────┘

2. Higher Latency
   ┌─────────────────────────────────────────┐
   │ 2PC: 2 phases (4 network roundtrips)    │
   │ 3PC: 3 phases (6 network roundtrips)    │
   │                                          │
   │ 50% more latency for questionable benefit│
   └─────────────────────────────────────────┘

3. Complexity
   ┌─────────────────────────────────────────┐
   │ More states, more transitions           │
   │ Harder to implement correctly            │
   │ Harder to reason about                   │
   └─────────────────────────────────────────┘

Verdict: 3PC rarely used in practice
         → 2PC with highly available coordinator
         → Or eventual consistency (no distributed txns)
```

---

## Google Percolator - Large-Scale Incremental Processing

🏗️ **ARCHITECT**: Google's approach to distributed transactions at massive scale:

### The Problem Percolator Solves

```
Google's web indexing pipeline:
──────────────────────────────

Input: Billions of web pages
Goal: Incrementally update search index
      (not rebuild from scratch daily)

Challenges:
1. Detect which documents changed
2. Update dependent indexes atomically
3. Handle failures gracefully
4. Scale to petabytes of data

Example: Update index when web page changes
───────────────────────────────────────────

Page A changes → Re-extract links → Update:
  - Forward index (Page A → links)
  - Reverse index (linked pages → Page A)
  - PageRank scores

All updates must be ATOMIC (or index becomes inconsistent)

Traditional approach (MapReduce):
  ✗ Batch processing (daily rebuild)
  ✗ High latency (hours to days)
  ✓ Simple

Percolator approach:
  ✓ Incremental processing (continuous)
  ✓ Low latency (minutes)
  ✗ More complex (distributed transactions)
```

### Percolator Architecture

```
Percolator = Bigtable + Distributed Transactions
────────────────────────────────────────────────

┌─────────────────────────────────────────────────┐
│           Percolator Workers                     │
│  (Thousands of workers processing mutations)    │
└───────────────┬─────────────────────────────────┘
                │
                ↓
┌─────────────────────────────────────────────────┐
│           Percolator Library                     │
│  (Implements distributed transactions)           │
│  - Snapshot isolation                            │
│  - Optimistic concurrency control                │
│  - No centralized coordinator!                   │
└───────────────┬─────────────────────────────────┘
                │
                ↓
┌─────────────────────────────────────────────────┐
│              Bigtable                            │
│  (Distributed storage layer)                     │
│  - Row-level transactions (local)                │
│  - Timestamp oracle (global)                     │
└─────────────────────────────────────────────────┘

Key Insight: No dedicated coordinator!
            → Workers coordinate via Bigtable
            → Uses row-level transactions in Bigtable
```

### Percolator Data Model

```
Percolator extends Bigtable with multiple columns per cell:
──────────────────────────────────────────────────────────

Bigtable: (row, column, timestamp) → value

Percolator: (row, column) → {
  lock:  (start_timestamp, primary_lock_location)
  write: (commit_timestamp, start_timestamp)
  data:  (start_timestamp, actual_value)
  notify: (timestamp, dirty_bit)  # For observers
}

Example: Account balance
────────────────────────

Row: account:alice

Columns:
  balance:lock     → ""                    # Empty if unlocked
  balance:write    → ts=105, start=100     # Committed at ts=105
  balance:data     → ts=100, value=500     # Data written at ts=100

Transaction:
1. Read account:alice@ts=110
   → Sees write@105 → reads data@100 → value=500

2. Write account:alice = 600 (start_ts=120)
   → Write lock@120 = primary
   → Write data@120 = 600
   → No write entry yet (uncommitted)

3. Commit (commit_ts=125)
   → Write write@125 = start_ts=120
   → Delete lock@120
```

---

## Percolator Transactions

🎓 **PROFESSOR**: How Percolator implements distributed transactions without a coordinator:

### Transaction Protocol

```python
class PercolatorTransaction:
    """
    Percolator's optimistic concurrency control
    """

    def __init__(self, timestamp_oracle):
        self.start_ts = timestamp_oracle.get_timestamp()
        self.writes = {}  # Buffer of pending writes
        self.primary_lock = None  # First lock acquired

    def get(self, row, column):
        """
        Read operation (snapshot isolation)
        """
        # 1. Check for locks at or after start_ts
        lock = self.bigtable.read(row, f"{column}:lock", self.start_ts)

        if lock and lock.timestamp >= self.start_ts:
            # Lock exists → conflict! Abort transaction
            raise TransactionConflict()

        # 2. Find latest write before start_ts
        write = self.bigtable.read_latest(
            row,
            f"{column}:write",
            max_ts=self.start_ts
        )

        if not write:
            # No writes → return None
            return None

        # 3. Read data at write's start timestamp
        data = self.bigtable.read(
            row,
            f"{column}:data",
            write.start_timestamp
        )

        return data.value

    def set(self, row, column, value):
        """
        Write operation (buffered)
        """
        # Buffer write (not applied yet)
        self.writes[(row, column)] = value

    def commit(self):
        """
        Two-phase commit WITHOUT coordinator!
        """
        # Get commit timestamp
        commit_ts = self.timestamp_oracle.get_timestamp()

        # Phase 1: Prewrite (acquire locks)
        if not self.prewrite():
            return False  # Conflict, abort

        # Phase 2: Commit (release locks)
        self.commit_phase(commit_ts)

        return True

    def prewrite(self):
        """
        Phase 1: Acquire locks on all written cells
        """
        # Choose primary lock (first write)
        primary_row, primary_col = list(self.writes.keys())[0]
        self.primary_lock = (primary_row, primary_col)

        for (row, column), value in self.writes.items():
            # Check for conflicts
            if self.has_conflict(row, column):
                # Conflict! Abort and release locks
                self.rollback()
                return False

            # Acquire lock (atomic row operation in Bigtable)
            is_primary = (row == primary_row and column == primary_col)

            self.bigtable.atomic_write(row, {
                # Write lock
                f"{column}:lock": {
                    "timestamp": self.start_ts,
                    "primary": (primary_row, primary_col) if not is_primary else None
                },
                # Write data
                f"{column}:data": {
                    "timestamp": self.start_ts,
                    "value": value
                }
            })

        return True

    def commit_phase(self, commit_ts):
        """
        Phase 2: Commit primary, then secondaries
        """
        # 1. Commit PRIMARY lock first (critical!)
        primary_row, primary_col = self.primary_lock

        self.bigtable.atomic_write(primary_row, {
            # Write commit record
            f"{primary_col}:write": {
                "timestamp": commit_ts,
                "start_timestamp": self.start_ts
            },
            # Delete lock
            f"{primary_col}:lock": None  # Delete
        })

        # Once primary committed, transaction IS committed!
        # Even if we crash now, transaction will be visible

        # 2. Commit secondary locks (can be asynchronous)
        for (row, column) in self.writes.keys():
            if (row, column) == self.primary_lock:
                continue  # Already committed

            self.bigtable.atomic_write(row, {
                # Write commit record
                f"{column}:write": {
                    "timestamp": commit_ts,
                    "start_timestamp": self.start_ts
                },
                # Delete lock
                f"{column}:lock": None  # Delete
            })

    def has_conflict(self, row, column):
        """
        Check for write-write conflicts
        """
        # Check for locks or writes after our start_ts
        lock = self.bigtable.read(row, f"{column}:lock", self.start_ts)
        if lock:
            return True  # Another transaction locked this cell

        write = self.bigtable.read_range(
            row,
            f"{column}:write",
            start_ts=self.start_ts,
            end_ts=float('inf')
        )
        if write:
            return True  # Another transaction committed after our start

        return False

    def rollback(self):
        """
        Abort transaction: release all locks
        """
        for (row, column) in self.writes.keys():
            # Delete lock
            self.bigtable.delete(row, f"{column}:lock", self.start_ts)

            # Delete uncommitted data
            self.bigtable.delete(row, f"{column}:data", self.start_ts)
```

### Key Innovations

```
1. No Centralized Coordinator
   ┌─────────────────────────────────────────┐
   │ Traditional 2PC: Dedicated coordinator  │
   │ Percolator: Uses PRIMARY lock as coord  │
   │                                          │
   │ Primary lock = commit point              │
   │ Once primary committed → txn committed   │
   │ Secondaries can be committed async       │
   └─────────────────────────────────────────┘

2. Optimistic Concurrency Control
   ┌─────────────────────────────────────────┐
   │ Don't acquire locks during reads         │
   │ Only lock during commit (prewrite)       │
   │ Conflicts detected at commit time        │
   │                                          │
   │ Good for: Read-heavy workloads           │
   │ Bad for: Write-heavy (many conflicts)    │
   └─────────────────────────────────────────┘

3. Snapshot Isolation
   ┌─────────────────────────────────────────┐
   │ Each transaction sees snapshot at        │
   │ start_timestamp                          │
   │                                          │
   │ No read locks needed!                    │
   │ Readers don't block writers              │
   │ Writers don't block readers              │
   └─────────────────────────────────────────┘

4. Lazy Cleanup
   ┌─────────────────────────────────────────┐
   │ Secondary locks can be cleaned up async  │
   │ Readers clean up stale locks             │
   │ (if find lock, check if primary committed)│
   └─────────────────────────────────────────┘
```

### Conflict Resolution

```java
/**
 * What happens when transactions conflict?
 */
public class PercolatorConflictResolution {

    /**
     * Scenario: Two transactions write same cell
     */
    public void writeWriteConflict() {
        /**
         * Transaction A (start_ts=100):
         *   read(x) → 5
         *   write(x, 10)
         *
         * Transaction B (start_ts=105):
         *   read(x) → 5
         *   write(x, 20)
         *
         * Timeline:
         * t=100: A starts
         * t=105: B starts
         * t=110: A prewrite (acquires lock on x)
         * t=115: B prewrite (tries to lock x)
         *        → Sees A's lock → CONFLICT!
         * t=120: B aborts
         * t=125: A commits
         *
         * Result: A wins, B aborts (first-writer-wins)
         */
    }

    /**
     * Scenario: Read observes uncommitted write
     */
    public void readUncommittedWrite() {
        /**
         * Transaction A (start_ts=100):
         *   write(x, 10)
         *   [Prewrite completes, but not yet committed]
         *
         * Transaction B (start_ts=105):
         *   read(x)
         *   → Sees lock from A
         *   → Wait or abort?
         *
         * Percolator: Check primary lock
         *   If primary committed → clean up secondary, read value
         *   If primary locked → abort or wait
         *   If primary gone (A crashed) → roll forward or back
         */
    }

    /**
     * Scenario: Transaction crashes after prewrite
     */
    public void crashAfterPrewrite() {
        /**
         * Transaction A (start_ts=100):
         *   prewrite(x) → Lock acquired
         *   prewrite(y) → Lock acquired
         *   [CRASH before commit phase]
         *
         * Result: Locks left orphaned!
         *
         * Recovery:
         *   Transaction B encounters lock on x
         *   B checks primary lock (x)
         *   Primary lock still exists, but A crashed
         *   B checks timestamp: If old enough, clean up
         *     → Delete lock
         *     → Delete uncommitted data
         *   B proceeds
         *
         * Lazy cleanup by readers!
         */
    }
}
```

---

## Spanner - The Evolution

🏗️ **ARCHITECT**: Google Spanner builds on Percolator:

### Spanner Improvements

```
Percolator vs Spanner:
─────────────────────

Percolator:
  ✓ Snapshot isolation
  ✓ Distributed transactions
  ✗ Multi-datacenter replication is manual
  ✗ No external consistency
  ✗ Built on Bigtable (limited SQL)

Spanner:
  ✓ External consistency (linearizability)
  ✓ Automatic multi-datacenter replication (Paxos)
  ✓ Full SQL support
  ✓ TrueTime for global ordering
  ✓ Read-only transactions without locking
```

### TrueTime

```python
class TrueTime:
    """
    Google's globally synchronized clock
    """

    def now(self):
        """
        Returns time interval [earliest, latest]
        """
        # TrueTime guarantees: actual time is in this interval

        # Implementation:
        # - GPS receivers in each datacenter
        # - Atomic clocks as backup
        # - Uncertainty: ±7ms (typically)

        current_time = self.get_current_time()
        uncertainty = 7_000_000  # 7ms in nanoseconds

        return TimeInterval(
            earliest=current_time - uncertainty,
            latest=current_time + uncertainty
        )

class SpannerTransaction:
    """
    Spanner uses TrueTime for external consistency
    """

    def commit(self):
        """
        Commit with external consistency
        """
        # 1. Choose commit timestamp
        tt = truetime.now()
        commit_ts = tt.latest

        # 2. Wait until commit_ts is definitely in the past
        # (this is the key innovation!)
        while truetime.now().earliest < commit_ts:
            time.sleep(0.001)  # Wait ~7ms

        # 3. Now safe to commit
        self.do_commit(commit_ts)

        """
        Why this works:

        After wait:
        - commit_ts is in the past on ALL servers
        - Any transaction starting now will see this commit
        - External consistency guaranteed!

        Cost: ~7-10ms commit latency
        """
```

### Read-Only Transactions

```java
/**
 * Spanner's lock-free read-only transactions
 */
public class SpannerReadOnlyTransaction {

    /**
     * Read-only transaction at specific timestamp
     */
    public void readOnlyTransaction() {
        // Choose read timestamp
        long readTimestamp = truetime.now().earliest;

        // Read multiple rows (no locks needed!)
        String name = read("users", "alice", "name", readTimestamp);
        int balance = read("accounts", "alice", "balance", readTimestamp);

        /**
         * Guarantees:
         * - Consistent snapshot across all reads
         * - No locks acquired
         * - Doesn't block writers
         * - Can read from nearby replica (low latency)
         *
         * Perfect for analytics queries!
         */
    }

    /**
     * Read-write transaction
     */
    public void readWriteTransaction() {
        // Start transaction
        long startTimestamp = truetime.now().latest;

        // Reads (snapshot isolation)
        int balance = read("accounts", "alice", "balance", startTimestamp);

        // Write (buffered)
        write("accounts", "alice", "balance", balance + 100);

        // Commit (2PC with Paxos)
        commit();

        /**
         * Uses 2PC for commit:
         * - Coordinator uses Paxos group (no single point of failure)
         * - Participants use Paxos groups (replicated)
         * - TrueTime for external consistency
         */
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### When to Use Distributed Transactions

```
Decision Matrix:
───────────────

Use Distributed Transactions When:
✓ Atomicity is CRITICAL (financial transactions)
✓ Can tolerate higher latency (100ms+ for commit)
✓ Write rate is moderate (< 10,000 TPS)
✓ Strong consistency required

Examples:
  - Bank transfers
  - Inventory reservation
  - Seat booking
  - Order processing

Avoid Distributed Transactions When:
✗ Eventual consistency acceptable
✗ Need high write throughput (>100,000 TPS)
✗ Multi-datacenter writes (high latency)
✗ Can use compensation (sagas)

Examples:
  - Social media posts
  - Metrics collection
  - User activity logs
  - Search indexes (can rebuild)
```

### Alternatives to Distributed Transactions

```python
class AlternativePatterns:
    """
    When to use alternatives to distributed transactions
    """

    def saga_pattern(self):
        """
        Alternative 1: Sagas (compensation)
        """
        # Each step has compensating action
        # If step fails, run compensating actions for previous steps

        # Example: Flight booking
        try:
            flight_id = flight_service.book()  # Step 1
            try:
                hotel_id = hotel_service.book()  # Step 2
                try:
                    car_id = car_service.book()  # Step 3
                    return "Success"
                except:
                    hotel_service.cancel(hotel_id)  # Compensate step 2
                    flight_service.cancel(flight_id)  # Compensate step 1
            except:
                flight_service.cancel(flight_id)  # Compensate step 1
        except:
            return "Failed"

        # Pro: No distributed transaction, no blocking
        # Con: Eventual consistency, complex compensation logic

    def event_sourcing(self):
        """
        Alternative 2: Event Sourcing
        """
        # Emit events, let services react

        # Order Service:
        events.publish("OrderCreated", order_id=123)

        # Inventory Service:
        @events.subscribe("OrderCreated")
        def on_order_created(event):
            self.reserve_inventory(event.order_id)

        # Payment Service:
        @events.subscribe("OrderCreated")
        def on_order_created(event):
            self.charge_payment(event.order_id)

        # Pro: Decoupled, scalable
        # Con: Eventual consistency, idempotency required

    def reservation_pattern(self):
        """
        Alternative 3: Reservations (optimistic locking)
        """
        # Reserve resources, then commit or cancel

        # Reserve inventory (creates reservation record)
        reservation = inventory.reserve(item_id, quantity=1, ttl=5*60)

        try:
            # Try to charge payment
            payment.charge(amount=100)

            # Success! Confirm reservation
            inventory.confirm_reservation(reservation.id)
        except:
            # Failed! Cancel reservation
            inventory.cancel_reservation(reservation.id)

        # Pro: No distributed transaction, resources auto-release (TTL)
        # Con: Eventual consistency

    def idempotency_keys(self):
        """
        Alternative 4: Idempotency (for retries)
        """
        # Client provides idempotency key
        # Server deduplicates based on key

        # Client:
        response = payment_service.charge(
            amount=100,
            idempotency_key="order-123-payment"  # Same key for retries
        )

        # Server:
        def charge(amount, idempotency_key):
            # Check if already processed
            if self.cache.get(idempotency_key):
                return self.cache.get(idempotency_key)

            # Process charge
            result = self.process_charge(amount)

            # Cache result
            self.cache.set(idempotency_key, result, ttl=24*3600)

            return result

        # Pro: Safe retries, no duplicates
        # Con: Requires client cooperation
```

---

## 🧠 MIND MAP: DISTRIBUTED TRANSACTIONS

```
    DISTRIBUTED TRANSACTIONS
            │
    ┌───────┴────────┐
    ↓                ↓
   2PC          PERCOLATOR
    │                │
┌───┼───┐        ┌───┼───┐
↓   ↓   ↓        ↓   ↓   ↓
Prepare Commit Block  Primary Optimistic Snapshot
Phase  Phase           Lock   Locking   Isolation
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **2PC = Wedding Ceremony 💒**
- Officiant (coordinator) asks bride and groom (participants)
- "Do you take...?" = PREPARE phase
- Both say "I do" = YES votes
- "I now pronounce..." = COMMIT phase
- If either says "No" = ABORT!

### 2. **Coordinator Crash = Officiant Fainting 😵**
- Bride and groom said "I do"
- Officiant faints before "I now pronounce..."
- Are they married? Nobody knows!
- Everyone stuck waiting (BLOCKING!)

### 3. **Percolator Primary Lock = First Domino 🎯**
- Line of dominos (locks)
- First domino (primary) is special
- Once first domino falls (primary commits)
- Rest will fall eventually (secondaries)
- Even if you stop watching

### 4. **Snapshot Isolation = Time Machine 🕰️**
- Transaction starts = get in time machine
- Travel to specific time (start_timestamp)
- See world as it was at that time
- Others' changes after you entered = invisible

### 5. **Write-Write Conflict = Parking Space 🚗**
- Two cars arrive at same space
- First to put cone (lock) wins
- Second car must find another space
- No arguing, clear winner

### 6. **Lazy Cleanup = Janitor Cleaning 🧹**
- Someone left trash (orphaned lock)
- Next person who notices cleans it up
- No dedicated janitor needed
- Everyone helps keep clean

---

## 🔑 Key Takeaways

### Two-Phase Commit

1. **2PC provides atomicity but blocks**
   - All participants commit or all abort
   - But can block indefinitely if coordinator fails
   - Rarely used in practice except with highly available coordinators

2. **2PC is synchronous and slow**
   - Multiple network roundtrips
   - Locks held during entire transaction
   - Typical latency: 100ms+ (vs 1ms for local transaction)

3. **3PC doesn't solve the real problems**
   - Designed for crash failures, not network partitions
   - Higher latency, more complexity
   - Not used in practice

### Percolator

4. **Percolator eliminates coordinator**
   - Uses primary lock as commit point
   - No single point of failure
   - Workers coordinate via shared storage

5. **Optimistic concurrency scales better**
   - No locks during reads
   - Conflicts detected at commit time
   - Good for read-heavy workloads

6. **Snapshot isolation enables lock-free reads**
   - Readers see consistent snapshot
   - Don't block writers
   - Don't acquire locks

7. **Lazy cleanup by readers**
   - Stale locks cleaned up on-demand
   - No dedicated cleanup process
   - Readers roll forward incomplete transactions

### General Lessons

8. **Distributed transactions are expensive**
   - Network latency dominates
   - Blocking reduces availability
   - Use only when truly needed

9. **Alternatives often better**
   - Sagas (compensation)
   - Event sourcing
   - Reservations with TTL
   - Idempotency for retries

10. **Real systems use hybrid approaches**
    - Strong consistency for critical data (accounts)
    - Eventual consistency for others (analytics)
    - Different tools for different requirements

---

**Final Thought**: Distributed transactions are like nuclear weapons - powerful but dangerous. Use them only when absolutely necessary, and prefer alternatives when possible. When you must use them, understand the trade-offs: atomicity comes at the cost of availability and performance. Percolator shows that clever design can reduce coordination overhead, but the fundamental limitations (latency, blocking) remain.
