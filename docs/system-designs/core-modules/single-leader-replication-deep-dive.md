# Single Leader Replication Deep Dive - How It Works

## Contents

- [Single Leader Replication Deep Dive - How It Works](#single-leader-replication-deep-dive---how-it-works)
    - [Core Mental Model](#core-mental-model)
    - [Replication Methods - How Changes Propagate](#replication-methods---how-changes-propagate)
    - [Statement-Based Replication](#statement-based-replication)
    - [Write-Ahead Log (WAL) Shipping](#write-ahead-log-wal-shipping)
    - [Logical (Row-Based) Replication](#logical-row-based-replication)
    - [Setting Up New Replicas](#setting-up-new-replicas)
    - [Handling Node Failures](#handling-node-failures)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: SINGLE-LEADER REPLICATION](#mind-map-single-leader-replication)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Single Leader Replication = Centralized Write Authority

                ┌──────────────┐
                │   PRIMARY    │ ← Single source of truth
                │  (Leader)    │   for ALL writes
                └──────┬───────┘
                       │
           Write-Ahead Log (WAL) / Replication Stream
                       │
         ┌─────────────┼─────────────┐
         ↓             ↓             ↓
    ┌────────┐    ┌────────┐    ┌────────┐
    │REPLICA1│    │REPLICA2│    │REPLICA3│
    │(Follower)│  │(Follower)│  │(Follower)│
    └────────┘    └────────┘    └────────┘
    Read-only     Read-only     Read-only
```

**Core Principles:**
1. **Single Write Path**: All writes go through one node (leader)
2. **Ordered Replication**: Changes applied in same order on all replicas
3. **Read Scalability**: Distribute reads across replicas
4. **Write Bottleneck**: Leader is the scaling limit for writes

---

## Replication Methods - How Changes Propagate

🎓 **PROFESSOR**: There are three fundamental approaches to transmitting changes from leader to followers:

```
┌────────────────────────────────────────────────────────────┐
│                                                             │
│  1. STATEMENT-BASED                                         │
│     Send SQL statements: INSERT, UPDATE, DELETE            │
│     Replica re-executes the SQL                            │
│                                                             │
│  2. WRITE-AHEAD LOG (WAL) SHIPPING                          │
│     Send low-level disk block changes                      │
│     Replica applies exact same bytes                       │
│                                                             │
│  3. LOGICAL (ROW-BASED)                                     │
│     Send row-level changes in database-agnostic format     │
│     Replica interprets and applies                         │
│                                                             │
└────────────────────────────────────────────────────────────┘
```

Each method has different trade-offs in terms of:
- **Fidelity**: How precisely does replica match leader?
- **Coupling**: Can replica be different version/engine?
- **Performance**: Overhead of generating and applying changes
- **Debuggability**: How easy to understand what changed?

---

## Statement-Based Replication

🎓 **PROFESSOR**: The conceptually simplest approach - ship the actual SQL statements.

### How It Works

```
PRIMARY:                    REPLICA:
────────                    ────────
1. Client executes:
   INSERT INTO users
   VALUES ('alice', NOW())

2. Log SQL statement ──────→ 3. Receive SQL statement

                            4. Re-execute:
                               INSERT INTO users
                               VALUES ('alice', NOW())
```

🏗️ **ARCHITECT**: MySQL's original replication (pre-5.1):

```sql
-- On Primary
INSERT INTO orders (id, user_id, created_at, total)
VALUES (UUID(), 123, NOW(), 99.99);

-- Logged to binlog:
-- Statement: INSERT INTO orders (id, user_id, created_at, total)
--            VALUES (UUID(), 123, NOW(), 99.99)

-- Replica receives and executes the SAME SQL
```

### The Problem: Non-Deterministic Functions

```python
class StatementReplicationProblem:
    """
    Demonstrating why statement-based replication is dangerous
    """

    def example_nondeterministic(self):
        # PRIMARY executes at 2024-01-01 10:00:00
        primary_sql = "INSERT INTO logs (msg, ts) VALUES ('event', NOW())"
        # Result: ts = '2024-01-01 10:00:00'

        # REPLICA executes at 2024-01-01 10:00:05 (5 second lag)
        replica_sql = "INSERT INTO logs (msg, ts) VALUES ('event', NOW())"
        # Result: ts = '2024-01-01 10:00:05'

        # PRIMARY != REPLICA → Data divergence!

    def example_random(self):
        # PRIMARY
        primary_sql = "UPDATE users SET token = UUID() WHERE id = 123"
        # Result: token = 'a1b2c3d4-...'

        # REPLICA
        replica_sql = "UPDATE users SET token = UUID() WHERE id = 123"
        # Result: token = 'e5f6g7h8-...'  (DIFFERENT!)

        # PRIMARY != REPLICA → Broken replication
```

### The Hacks (How MySQL 5.0 Tried to Fix It)

```sql
-- MySQL logged context along with statement:
SET timestamp=1234567890;  -- Fix NOW()
SET insert_id=42;          -- Fix AUTO_INCREMENT
-- Then execute the statement

-- But this didn't work for:
-- - UUID() / RAND()
-- - Triggers with non-deterministic logic
-- - Stored procedures
```

**Interview Gold**: "Statement-based replication is brittle. It works for simple INSERT/UPDATE/DELETE but breaks with triggers, stored procedures, or non-deterministic functions. That's why modern databases moved to row-based or WAL shipping."

### When It's Still Used

```java
// Read-only analytics replicas with transformed data
public class StatementReplicationUseCase {

    /**
     * Use case: Replicate with data masking
     */
    public void replicateWithMasking() {
        // PRIMARY: Full data
        String primarySQL = "INSERT INTO users (name, ssn) VALUES ('Alice', '123-45-6789')";

        // REPLICA: Masked data (modify statement before applying)
        String replicaSQL = "INSERT INTO users (name, ssn) VALUES ('Alice', 'XXX-XX-XXXX')";

        // This intentional divergence is useful for compliance!
    }
}
```

---

## Write-Ahead Log (WAL) Shipping

🎓 **PROFESSOR**: Ship the actual **physical storage changes** from leader to followers.

### How Databases Actually Store Data

```
Database storage is append-only log of changes:

┌─────────────────────────────────────────┐
│  Write-Ahead Log (WAL)                  │
│  ─────────────────────────────────      │
│  [Offset 1000] Page 42, Byte 100: "Alice" → "Bob"  │
│  [Offset 1001] Page 43, Byte 50: 123 → 456        │
│  [Offset 1002] Page 42, Byte 200: NULL → "email@" │
│  ...                                     │
└─────────────────────────────────────────┘
                    ↓
         Periodically flush to disk
                    ↓
┌─────────────────────────────────────────┐
│  Data Pages (B-tree blocks)             │
│  ─────────────────────────────────      │
│  Page 42: [Row1: "Bob", Row2: ...]      │
│  Page 43: [Index node: 456 → Page 78]   │
└─────────────────────────────────────────┘
```

### WAL Shipping Process

```
PRIMARY:                           REPLICA:
────────                           ────────
1. Write to WAL
   [Off: 1000] Page 42: "Alice"→"Bob"

2. Ship WAL record ──────────────→ 3. Receive WAL record

                                   4. Apply to local storage:
                                      Page 42: "Alice"→"Bob"

5. Eventually flush to disk        6. Eventually flush to disk
   (checkpoint)                       (checkpoint)
```

🏗️ **ARCHITECT**: PostgreSQL streaming replication:

```python
class PostgresWALShipping:
    """
    PostgreSQL's WAL-based replication
    """

    def configure_primary(self):
        """
        postgresql.conf on primary
        """
        config = {
            "wal_level": "replica",  # Generate enough WAL for replication
            "max_wal_senders": 5,    # Allow 5 concurrent replicas
            "wal_keep_size": "1GB"   # Keep 1GB of WAL for lagging replicas
        }

    def stream_wal(self):
        """
        Streaming replication: Real-time WAL shipping
        """
        # PRIMARY sends WAL records as they're generated
        while True:
            wal_record = self.wal_buffer.read_next()

            # Send to all connected replicas (non-blocking)
            for replica in self.connected_replicas:
                replica.send_async(wal_record)

            # Optional: Wait for sync replica to ACK
            if self.sync_replica:
                self.sync_replica.wait_for_ack(timeout=1.0)

    def apply_wal_on_replica(self, wal_record):
        """
        Replica applies WAL record
        """
        # WAL record format:
        # [LSN: 0/1234ABCD] [Type: UPDATE] [Page: 42] [Offset: 100] [Data: ...]

        # Apply directly to storage (no SQL parsing needed)
        self.buffer_pool.write_page(
            page_id=wal_record.page_id,
            offset=wal_record.offset,
            data=wal_record.data
        )

        # Update replica's LSN (log sequence number)
        self.current_lsn = wal_record.lsn
```

### Advantages of WAL Shipping

```java
/**
 * Why WAL shipping is powerful
 */
public class WALAdvantages {

    // 1. Exact replication (byte-for-byte copy)
    public void exactCopy() {
        // PRIMARY and REPLICA have IDENTICAL storage
        // No non-determinism issues
    }

    // 2. High performance
    public void performance() {
        // No need to re-parse SQL
        // No need to re-plan queries
        // Just copy bytes to disk
        // Throughput: 100MB/s+ on modern SSDs
    }

    // 3. Transactional consistency
    public void consistency() {
        // WAL records are already ordered by transaction commit
        // Replica sees transactions in same order as primary
    }
}
```

### The Downside: Tight Coupling

```
Problem: WAL is storage-engine specific

PostgreSQL 14 WAL !== PostgreSQL 15 WAL
 ↓                      ↓
Cannot replicate across major versions!

MySQL 5.7 WAL !== MySQL 8.0 WAL
 ↓                 ↓
Zero-downtime upgrades are HARD
```

**Interview Gold**: "WAL shipping gives you perfect replication but at the cost of version lock-in. You can't have a replica on newer version than primary. This makes rolling upgrades challenging."

---

## Logical (Row-Based) Replication

🎓 **PROFESSOR**: Decouple replication from storage engine by using a **logical format** that describes changes at the row level.

### How It Works

```
PRIMARY:                                 REPLICA:
────────                                 ────────
1. Execute:
   UPDATE users SET age=31 WHERE id=123

2. Translate to logical log:
   Table: users
   Row ID: 123
   Before: {id: 123, name: "Alice", age: 30}
   After:  {id: 123, name: "Alice", age: 31}

3. Send logical record ────────────────→ 4. Receive logical record

                                         5. Apply change:
                                            UPDATE users
                                            SET age=31
                                            WHERE id=123
```

🏗️ **ARCHITECT**: MySQL binlog in ROW format:

```python
class MySQLBinlogRow:
    """
    MySQL's row-based replication format
    """

    def parse_binlog_event(self, event):
        """
        Binlog event structure
        """
        return {
            "type": "UPDATE_ROWS_EVENT",
            "timestamp": 1234567890,
            "server_id": 1,
            "database": "production",
            "table": "users",
            "changes": [
                {
                    "before": {"id": 123, "name": "Alice", "age": 30, "email": "alice@example.com"},
                    "after":  {"id": 123, "name": "Alice", "age": 31, "email": "alice@example.com"}
                }
            ]
        }

    def apply_to_replica(self, event):
        """
        Replica can be different storage engine!
        """
        for change in event["changes"]:
            # Replica translates logical change to its own storage format
            self.replica_db.update(
                table=event["table"],
                row_id=change["after"]["id"],
                new_values=change["after"]
            )
```

### Real-World Example: Change Data Capture (CDC)

```java
/**
 * Debezium: Stream database changes to Kafka
 */
@Service
public class ChangeCaptureService {

    @Autowired
    private KafkaProducer<String, ChangeEvent> kafkaProducer;

    /**
     * MySQL binlog → Kafka topic
     * This enables event-driven architectures!
     */
    public void streamChangesToKafka() {
        // Debezium reads MySQL binlog (row-based format)
        BinlogReader reader = new BinlogReader("mysql-primary:3306");

        reader.onChangeEvent(event -> {
            // Event: {table: "orders", type: "INSERT", after: {...}}

            // Publish to Kafka
            kafkaProducer.send(
                "database.changes.orders",  // Topic
                event.getAfter().get("order_id"),  // Key
                event  // Value
            );

            // Now other microservices can react to database changes!
            // - Inventory service decrements stock
            // - Email service sends confirmation
            // - Analytics service updates metrics
        });
    }
}
```

### Advantages of Logical Replication

```
1. Version Independence
   ┌─────────────┐         ┌─────────────┐
   │ MySQL 5.7   │ ──────→ │ MySQL 8.0   │  ✓ Works!
   └─────────────┘         └─────────────┘

2. Cross-Engine Replication
   ┌─────────────┐         ┌─────────────┐
   │ PostgreSQL  │ ──────→ │ Elasticsearch│  ✓ Works!
   └─────────────┘         └─────────────┘

3. Selective Replication
   - Replicate only specific tables
   - Replicate only specific columns (data masking)
   - Transform data during replication
```

**Interview Gold**: "Logical replication is the foundation of modern data pipelines. It's how we stream database changes to Kafka, Elasticsearch, or data warehouses in real-time."

---

## Setting Up New Replicas

🎓 **PROFESSOR**: The chicken-and-egg problem of replication:

```
Problem: New replica needs to catch up to primary

Primary has been running for months:
- 10 billion rows
- 500GB of data
- WAL position: 0xABCD1234

New replica starts:
- 0 rows
- 0GB of data
- WAL position: 0x00000000

How do we get from 0 → 10 billion rows efficiently?
```

### The Process

```
┌──────────────────────────────────────────────────────────┐
│ Step 1: Take Snapshot of Primary (without locking)      │
└────────────────────────┬─────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│ Step 2: Record WAL position at snapshot time             │
│         (e.g., LSN = 0xABCD1234)                         │
└────────────────────────┬─────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│ Step 3: Copy snapshot to new replica                     │
│         (can take hours for large databases)             │
└────────────────────────┬─────────────────────────────────┘
                         ↓
┌──────────────────────────────────────────────────────────┐
│ Step 4: Start streaming from WAL position                │
│         (catch up on changes during copy)                │
└──────────────────────────────────────────────────────────┘
```

🏗️ **ARCHITECT**: PostgreSQL pg_basebackup:

```bash
#!/bin/bash
# Setting up new PostgreSQL replica

# Step 1: Take base backup from primary (online, no lock)
pg_basebackup \
  -h primary.db.example.com \
  -D /var/lib/postgresql/data \
  -U replication \
  -P \                          # Show progress
  -v \                          # Verbose
  --wal-method=stream           # Stream WAL during backup

# Step 2: Configure replica
cat > /var/lib/postgresql/data/postgresql.conf << EOF
hot_standby = on                # Allow read queries on replica
EOF

cat > /var/lib/postgresql/data/recovery.conf << EOF
standby_mode = 'on'
primary_conninfo = 'host=primary.db.example.com port=5432 user=replication'
restore_command = 'cp /var/lib/postgresql/wal_archive/%f %p'
EOF

# Step 3: Start replica (automatically catches up)
pg_ctl start -D /var/lib/postgresql/data

# The replica now:
# - Starts from snapshot
# - Streams WAL from where snapshot left off
# - Catches up to primary (may take minutes to hours)
```

### Handling Snapshots Without Downtime

```python
class OnlineSnapshotStrategy:
    """
    Taking consistent snapshot without locking database
    """

    def snapshot_with_mvcc(self):
        """
        Method 1: Use MVCC (Multi-Version Concurrency Control)
        """
        # Start transaction with snapshot isolation
        conn = self.primary_db.connection()
        conn.execute("BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ")

        # Record snapshot timestamp/LSN
        snapshot_lsn = conn.execute("SELECT pg_current_wal_lsn()")[0][0]

        # Export snapshot (PostgreSQL 9.2+)
        snapshot_id = conn.execute("SELECT pg_export_snapshot()")[0][0]

        # Another session can now import this snapshot
        # and see consistent view of data

        return snapshot_id, snapshot_lsn

    def snapshot_with_filesystem(self):
        """
        Method 2: Use filesystem snapshots (LVM, ZFS, EBS)
        """
        # Flush all dirty pages to disk
        self.primary_db.execute("CHECKPOINT")

        # Take instant filesystem snapshot
        # (works for LVM, ZFS, AWS EBS)
        os.system("lvcreate --snapshot --size 10G --name db-snapshot /dev/vg0/db-data")

        # Snapshot is consistent because:
        # 1. WAL ensures consistency
        # 2. Snapshot is atomic at filesystem level

        return "db-snapshot"
```

---

## Handling Node Failures

🎓 **PROFESSOR**: Single-leader replication has three failure scenarios:

### 1. Follower (Replica) Failure - Easy ✓

```
Before:                    After Replica2 Fails:
┌─────────┐               ┌─────────┐
│ PRIMARY │               │ PRIMARY │
└────┬────┘               └────┬────┘
     │                         │
  ┌──┼──┐                   ┌──┘
  ↓  ↓  ↓                   ↓
 R1 R2 R3                  R1  R3
    X (failed)
```

**Recovery:**
```python
def recover_failed_replica():
    # 1. Replica restarts
    # 2. Reads last applied WAL position from disk (e.g., LSN = 0x1234)
    last_lsn = read_from_disk("last_applied_lsn")

    # 3. Connects to primary
    # 4. Requests WAL from last_lsn onwards
    primary.stream_wal(start_from=last_lsn)

    # 5. Catches up (seconds to minutes)
    # 6. Resumes normal replication
```

**Interview Gold**: "Replica failure is benign. It just falls behind and catches up when it comes back. No data loss, no user impact (if you have other replicas)."

### 2. Leader Failure - Complex ⚠️

```
Before:                    Leader Fails:              After Failover:
┌─────────┐               ┌─────────┐               ┌─────────┐
│ PRIMARY │               │ PRIMARY │               │   R1    │
└────┬────┘               └────┬────┘               │(promoted│
     │                         X                    │ to new  │
  ┌──┼──┐                                          │ primary)│
  ↓  ↓  ↓                                          └────┬────┘
 R1 R2 R3                                               │
                                                     ┌──┘
                                                     ↓
                                                    R2  R3
```

**Failover Process:**

```java
public class FailoverOrchestrator {

    public void handleLeaderFailure() {
        // Step 1: DETECT failure
        if (primaryHealthCheck.failed(timeout = 30_seconds)) {

            // Step 2: CHOOSE new leader
            Replica newLeader = this.electNewLeader();

            // Step 3: RECONFIGURE system
            newLeader.promoteToLeader();

            // Step 4: UPDATE clients
            dnsService.updatePrimaryEndpoint(newLeader.getAddress());

            // Step 5: HANDLE old leader
            // When it comes back, demote to replica
        }
    }

    private Replica electNewLeader() {
        /**
         * Choosing new leader: Pick replica with most up-to-date data
         */
        List<Replica> replicas = getAllReplicas();

        // Option 1: Pick replica with highest LSN (most caught up)
        return replicas.stream()
            .max(Comparator.comparing(Replica::getLastAppliedLSN))
            .orElseThrow();

        // Option 2: Use consensus (Raft, Paxos)
        // Requires 3+ nodes for quorum
    }
}
```

### The Dangers of Failover

```python
class FailoverDangers:
    """
    Real-world failover horror stories
    """

    def split_brain(self):
        """
        DANGER 1: Split-brain - Two nodes think they're primary
        """
        # Old primary didn't actually die, just network partition
        # New primary was elected
        # Both accept writes
        # → DATA DIVERGENCE!

        # Solution: Fencing - actively kill old primary
        # - STONITH (Shoot The Other Node In The Head)
        # - Cloud API to terminate instance
        # - Network isolation

    def data_loss(self):
        """
        DANGER 2: Data loss during failover
        """
        # Scenario:
        # 1. Client writes to primary (async replication)
        # 2. Primary ACKs immediately
        # 3. Primary crashes BEFORE replicating
        # 4. New primary elected (doesn't have this write)
        # 5. → WRITE LOST!

        # Solution: Use synchronous or semi-sync replication
        # for critical writes

    def thundering_herd(self):
        """
        DANGER 3: Failover storm
        """
        # Primary fails
        # 10,000 clients immediately reconnect to new primary
        # New primary overwhelmed and crashes
        # Failover to another replica
        # Repeat → cascading failure

        # Solution: Connection rate limiting, backoff, connection pooling
```

**Real-World Example: GitHub 2018 Outage**

```
Timeline:
────────
10:52 AM: Network glitch between US East and West datacenters
          Both datacenters think they're primary (split-brain)

10:53 AM: Writes going to BOTH primaries
          Data diverging

10:54 AM: Automated failover detects issue, shuts down one primary
          But data has already diverged

11:00 AM: Engineers manually merge conflicting data
          (took 24 hours to fully resolve)

Lesson: Automated failover is dangerous without proper fencing
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification

```
Q: "Design a database replication system for our e-commerce platform"

Clarify:
- Read:Write ratio? (Determines number of replicas)
- Acceptable replication lag? (Sync vs async)
- Geographic distribution? (Single-region vs multi-region)
- Failover requirements? (Automatic vs manual)
- Budget for downtime? (Influences sync replication choice)
```

### 2. High-Level Design

```
                 WRITE PATH
                     ↓
              ┌──────────────┐
              │   PRIMARY    │
              │  (Leader)    │
              │              │
              │ - Accepts writes
              │ - Generates WAL
              │ - Sends to replicas
              └──────┬───────┘
                     │
          ┌──────────┼──────────┐
          ↓          ↓          ↓
     ┌────────┐ ┌────────┐ ┌────────┐
     │REPLICA1│ │REPLICA2│ │REPLICA3│
     │        │ │        │ │        │
     │ - Apply│ │ - Apply│ │ - Apply│
     │   WAL  │ │   WAL  │ │   WAL  │
     │ - Serve│ │ - Serve│ │ - Serve│
     │   reads│ │   reads│ │   reads│
     └────────┘ └────────┘ └────────┘
                     ↑
                READ PATH
```

### 3. Deep Dive: Monitoring

```python
class ReplicationMonitoring:
    """
    Production monitoring for single-leader replication
    """

    def monitor_replication_health(self):
        metrics = {
            # 1. Replication lag per replica
            "replica_lag_seconds": self.measure_lag(),

            # 2. Replication throughput
            "wal_bytes_per_second": self.measure_throughput(),

            # 3. Replica connectivity
            "connected_replicas": len(self.get_connected_replicas()),

            # 4. Failover readiness
            "most_caught_up_replica": self.get_best_failover_candidate(),

            # 5. Replication queue depth
            "pending_wal_segments": self.get_queue_depth()
        }

        # Alert conditions
        if metrics["replica_lag_seconds"] > 60:
            self.alert("High replication lag detected")

        if metrics["connected_replicas"] < 2:
            self.alert("Low replica count - failover impossible")

        return metrics
```

---

## 🧠 MIND MAP: SINGLE-LEADER REPLICATION

```
           SINGLE-LEADER REPLICATION
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓              ↓
    METHODS      OPERATIONS      FAILURES
        |             |              |
   ┌────┼────┐   ┌───┼───┐      ┌───┼───┐
   ↓    ↓    ↓   ↓   ↓   ↓      ↓   ↓   ↓
 State WAL Logic New Sync/  Follower Leader Split
 ment      Row   Replica Async  Failure Failure Brain
 Based     Based
  |         |        |      |        |       |
Brittle  Exact   Snapshot  Fast   Auto   Manual  Fencing
        Copy    +Catchup  /Slow  Recover Failover Needed
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **Single-Leader = Professor & TAs 👨‍🏫**
- Professor (leader) is the only one who writes official lecture notes
- TAs (replicas) make copies for students to read
- If TAs fall behind, they catch up from professor's notes
- If professor sick, promote a TA to professor (failover)

### 2. **WAL Shipping = Carbon Copy Forms 📋**
- Old-school forms with carbon paper
- You write on top sheet, exact copy appears on bottom sheets
- Replicas get exact byte-for-byte copy (no interpretation needed)

### 3. **Logical Replication = Meeting Minutes ✍️**
- You don't record every keystroke (WAL)
- You record the decisions made (row changes)
- Others can interpret and apply to their context

### 4. **Failover = Vice President Succession 🇺🇸**
- If President (leader) dies, VP (replica) becomes President
- But there's a process, not instant
- Risk of confusion during transition (split-brain)
- Succession order prevents ambiguity (LSN-based election)

---

## 🔑 Key Takeaways

1. **Statement-based replication is dangerous**
   - Non-deterministic functions break it
   - Only use for simple, deterministic workloads

2. **WAL shipping is most common in production**
   - Byte-perfect replication
   - High performance
   - But tight version coupling

3. **Logical replication enables data pipelines**
   - CDC (Change Data Capture) is powered by logical replication
   - Foundation of event-driven architectures

4. **Failover is the hard part**
   - Automated failover can cause split-brain
   - Manual failover is slow but safer
   - Always have fencing mechanism

5. **Monitor replication lag religiously**
   - Lag spikes predict future problems
   - Set alerts: lag > 5s (warning), lag > 60s (critical)
