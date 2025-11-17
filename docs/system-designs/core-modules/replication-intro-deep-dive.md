# Introduction to Replication Deep Dive

## Contents

- [Introduction to Replication Deep Dive](#introduction-to-replication-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Why Replication? (The Three Horsemen of Distributed Systems)](#why-replication-the-three-horsemen-of-distributed-systems)
    - [Replication Lag - The Fundamental Challenge](#replication-lag---the-fundamental-challenge)
    - [Synchronous vs Asynchronous Replication](#synchronous-vs-asynchronous-replication)
    - [Replication Topologies](#replication-topologies)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification](#1-requirements-clarification)
        - [Capacity Estimation](#2-capacity-estimation)
        - [Data Model Considerations](#3-data-model-considerations)
        - [High-Level Design](#4-high-level-design)
        - [Deep Dives](#5-deep-dives)
    - [MIND MAP: REPLICATION CONCEPTS](#mind-map-replication-concepts)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Single Truth Problem: One piece of data, multiple locations
                      ↓
            How do we keep them synchronized?

The Fundamental Tension:
┌──────────────────────────────────────────────────────────┐
│ Consistency ←→ Availability ←→ Latency                   │
│                                                           │
│ You can optimize for 2, but not all 3 simultaneously     │
└──────────────────────────────────────────────────────────┘
```

**The Replication Equation:**
```
Consistency = f(Replication Lag, Coordination Overhead)

Where:
- Replication Lag: Time for data to propagate across replicas
- Coordination Overhead: Cost of ensuring replicas agree
- Trade-off: Low lag OR low overhead, rarely both
```

---

## Why Replication? (The Three Horsemen of Distributed Systems)

🎓 **PROFESSOR**: Replication solves three fundamental problems in distributed systems:

### 1. **Fault Tolerance** (Availability)
```
Single Node:
┌──────────┐
│   DB     │  ← Single Point of Failure (SPOF)
└──────────┘
Availability: 99.9% (8.76 hours downtime/year)

Replicated:
┌──────────┐   ┌──────────┐   ┌──────────┐
│   DB1    │   │   DB2    │   │   DB3    │
└──────────┘   └──────────┘   └──────────┘
Availability: 99.999% (5.26 minutes downtime/year)
```

### 2. **Scalability** (Read Performance)
```
Write: 1000 TPS → Must go to source of truth
Read:  50,000 TPS → Can distribute across replicas

Read-Heavy Workload (95% reads):
┌──────────────────────────────────────┐
│  1 Primary (writes)                  │
│  9 Replicas (reads)                  │
│  = 10x read capacity                 │
└──────────────────────────────────────┘
```

### 3. **Latency** (Geographic Distribution)
```
User in Singapore → Replica in Singapore (10ms)
                    vs
                    Primary in Virginia (250ms)

25x latency improvement!
```

🏗️ **ARCHITECT**: Real-world example from Netflix:
```java
/**
 * Netflix uses 3 AWS regions with read replicas
 * - Primary: us-east-1 (Virginia)
 * - Replica: eu-west-1 (Ireland)
 * - Replica: ap-southeast-1 (Singapore)
 *
 * Pattern: Route reads to nearest replica
 */
@Service
public class GeographicReplicationService {

    @Autowired
    private DataSourceRouter router;

    public Movie getMovie(String movieId, String userRegion) {
        // Read from nearest replica
        DataSource replica = router.getNearestReadReplica(userRegion);

        return replica.query(
            "SELECT * FROM movies WHERE id = ?",
            movieId
        );
    }

    public void updateMovie(Movie movie) {
        // Writes ALWAYS go to primary
        DataSource primary = router.getPrimaryDataSource();

        primary.update(
            "UPDATE movies SET title = ?, rating = ? WHERE id = ?",
            movie.getTitle(), movie.getRating(), movie.getId()
        );

        // Asynchronously replicate to other regions (lag: 100-500ms)
    }
}
```

**Interview Gold**: "Replication is NOT just backup. Backup is point-in-time snapshot for disaster recovery. Replication is live, continuously synchronized copies for availability and performance."

---

## Replication Lag - The Fundamental Challenge

🎓 **PROFESSOR**: The **replication lag** is the time difference between:
```
t1: Write committed on primary
t2: Write visible on replica

Replication Lag = t2 - t1
```

**Sources of Lag:**
```
1. Network Latency: Physical distance (speed of light limit)
2. Processing Time: Replica must parse and apply the change
3. Queue Depth: Backlog of pending changes
4. Resource Contention: CPU/disk busy on replica

Total Lag = Network + Processing + Queuing + Contention
```

🏗️ **ARCHITECT**: Measuring replication lag in production:

```python
class ReplicationLagMonitor:
    """
    Production-grade replication lag monitoring
    """

    def measure_lag(self, primary_conn, replica_conn):
        """
        Method 1: Heartbeat table (most accurate)
        """
        # Primary writes current timestamp
        primary_conn.execute("""
            INSERT INTO heartbeat (node_id, ts)
            VALUES ('primary', NOW())
            ON DUPLICATE KEY UPDATE ts = NOW()
        """)

        time.sleep(0.1)  # Give replication time to catch up

        # Read from replica
        result = replica_conn.query("""
            SELECT TIMESTAMPDIFF(SECOND, ts, NOW()) as lag_seconds
            FROM heartbeat
            WHERE node_id = 'primary'
        """)

        lag_seconds = result[0]['lag_seconds']

        # Alert if lag > 5 seconds
        if lag_seconds > 5:
            self.alert_on_call(f"Replication lag: {lag_seconds}s")

        return lag_seconds

    def measure_lag_mysql(self, replica_conn):
        """
        Method 2: MySQL-specific (built-in)
        """
        result = replica_conn.query("SHOW SLAVE STATUS")
        return result[0]['Seconds_Behind_Master']

    def measure_lag_postgres(self, primary_conn, replica_conn):
        """
        Method 3: PostgreSQL WAL-based
        """
        # Get current WAL position on primary
        primary_lsn = primary_conn.query(
            "SELECT pg_current_wal_lsn()"
        )[0][0]

        # Get replay position on replica
        replica_lsn = replica_conn.query(
            "SELECT pg_last_wal_replay_lsn()"
        )[0][0]

        # Calculate byte difference (converted to lag estimate)
        byte_lag = self.lsn_diff(primary_lsn, replica_lsn)

        # Assuming 10MB/s replication throughput
        estimated_lag_seconds = byte_lag / (10 * 1024 * 1024)

        return estimated_lag_seconds
```

**Real-World Scenario:**
```
Instagram's Replication Lag Problem (2012):
- User uploads photo
- Write goes to primary (Virginia)
- User refreshed page immediately
- Read from replica (still lagging by 200ms)
- Photo not visible → User uploads again → Duplicate!

Solution: Read-after-write consistency
- For user's OWN data, read from primary for 1 second after write
- For other users' data, replicas are fine
```

---

## Synchronous vs Asynchronous Replication

🎓 **PROFESSOR**: The spectrum of replication guarantees:

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  SYNCHRONOUS          SEMI-SYNC         ASYNCHRONOUS        │
│      │                    │                   │             │
│      ├─ Wait for ALL      ├─ Wait for 1       ├─ Fire and   │
│      │  replicas          │  replica          │  forget     │
│      │                    │                   │             │
│      ├─ Consistent        ├─ Balanced         ├─ Fast       │
│      ├─ SLOW              ├─ Moderate         ├─ Eventual   │
│      ├─ Blocking          ├─ Partial block    ├─ Non-block  │
│      │                    │                   │             │
└─────────────────────────────────────────────────────────────┘
```

### Synchronous Replication

```
Client → Primary: WRITE X=5
         Primary → Replica1: WRITE X=5
                   Replica1: ACK ✓
         Primary → Replica2: WRITE X=5
                   Replica2: ACK ✓
         Primary: COMMIT
Client ← Primary: SUCCESS

Latency: Write_time + 2*(Network_RTT + Replica_processing)
```

**Pros:**
- Strong consistency guarantee
- No data loss if primary fails

**Cons:**
- High latency (must wait for slowest replica)
- Availability: If ANY replica down, writes BLOCKED

🏗️ **ARCHITECT**: When to use synchronous replication:
```java
/**
 * Financial transactions: MUST be durable across replicas
 */
@Transactional
public class BankTransferService {

    @ReplicationMode(SYNCHRONOUS)  // Wait for all replicas
    public void transfer(Account from, Account to, Money amount) {
        // Deduct from source
        from.deduct(amount);

        // Add to destination
        to.add(amount);

        // This BLOCKS until all replicas acknowledge
        // Typically 50-200ms additional latency
        // But we GUARANTEE no money lost if primary crashes
    }
}
```

### Asynchronous Replication

```
Client → Primary: WRITE X=5
         Primary: COMMIT (immediately)
         Primary → Replica1: WRITE X=5 (background)
         Primary → Replica2: WRITE X=5 (background)
Client ← Primary: SUCCESS (without waiting)

Latency: Write_time only (no replication wait)
```

**Pros:**
- Low latency (no blocking)
- High availability (replicas can be down)

**Cons:**
- Eventual consistency (temporary inconsistency)
- Data loss window: If primary crashes before replication

🏗️ **ARCHITECT**: Most common in production:
```python
class SocialMediaPost:
    """
    Twitter/Instagram pattern: Async replication is fine
    """

    def create_post(self, user_id: str, content: str):
        # Write to primary (returns immediately)
        post_id = self.primary_db.insert(
            "posts",
            {"user_id": user_id, "content": content, "created_at": time.now()}
        )

        # Replication happens asynchronously (100-500ms lag)
        # Users might not see post immediately on other devices
        # But that's acceptable for social media

        return post_id
```

### Semi-Synchronous (The Pragmatic Middle Ground)

```
Wait for at least 1 replica, then commit
- Durability: Data on 2 nodes (primary + 1 replica)
- Performance: Only wait for fastest replica
```

```sql
-- MySQL configuration
SET GLOBAL rpl_semi_sync_master_enabled = 1;
SET GLOBAL rpl_semi_sync_master_timeout = 1000;  -- 1 second timeout

-- If replica doesn't ACK within 1s, degrade to async
```

**Interview Gold**: "In production, we use semi-sync for critical writes, async for everything else. Example: User password change = semi-sync. Profile picture update = async."

---

## Replication Topologies

🎓 **PROFESSOR**: How replicas are arranged affects failure modes and performance:

### 1. Single-Leader (Master-Slave)
```
        ┌──────────┐
        │  PRIMARY │ ← All writes
        └─────┬────┘
              │
      ┌───────┼───────┐
      ↓       ↓       ↓
   ┌─────┐ ┌─────┐ ┌─────┐
   │ R1  │ │ R2  │ │ R3  │ ← Read replicas
   └─────┘ └─────┘ └─────┘
```
- Most common pattern
- Simple mental model
- Single point of contention (primary)

### 2. Multi-Leader (Master-Master)
```
   ┌──────────┐         ┌──────────┐
   │ PRIMARY1 │ ←─────→ │ PRIMARY2 │
   │(US-East) │         │(EU-West) │
   └──────────┘         └──────────┘
        ↓                     ↓
   [Replicas]            [Replicas]
```
- Each datacenter has its own leader
- Writes can go to nearest leader (low latency)
- Complex conflict resolution needed

### 3. Leaderless (Dynamo-style)
```
   ┌─────┐
   │ N1  │ ←─┐
   └─────┘   │
             │
   ┌─────┐   │   ┌─────┐
   │ N2  │ ←─┼──→│ N3  │
   └─────┘   │   └─────┘
             │
   ┌─────┐   │
   │ N4  │ ←─┘
   └─────┘
```
- No designated leader
- Client writes to multiple nodes
- Quorum-based consistency

🏗️ **ARCHITECT**: Choosing replication topology:

```python
class ReplicationTopologySelector:
    """
    Decision framework for choosing replication pattern
    """

    def recommend(self, requirements):
        # Single-leader: Most use cases
        if (requirements.consistency == "strong" and
            requirements.write_pattern == "centralized" and
            requirements.regions == 1):
            return TopologyType.SINGLE_LEADER

        # Multi-leader: Multi-region writes
        if (requirements.regions > 1 and
            requirements.write_latency_sensitive and
            requirements.can_tolerate_conflicts):
            return TopologyType.MULTI_LEADER

        # Leaderless: High availability, eventual consistency ok
        if (requirements.availability_target > 0.9999 and
            requirements.consistency == "eventual" and
            requirements.partition_tolerance_critical):
            return TopologyType.LEADERLESS

        # Default: Single-leader with async replicas
        return TopologyType.SINGLE_LEADER
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification

```
Functional:
- Read/write ratio? (Determines replica count)
- Geographic distribution? (Multi-region?)
- Data size? (Affects replication method)

Non-Functional:
- Consistency needs? (Strong vs eventual)
- Availability target? (99.9% vs 99.99%)
- Acceptable replication lag? (milliseconds vs seconds)
- Budget for infrastructure?
```

### 2. Capacity Estimation

```
Traffic:
- 10,000 writes/sec
- 100,000 reads/sec
- Read:Write ratio = 10:1

Replication Strategy:
- 1 Primary (handles all writes)
- 10 Read Replicas (each handles ~10K reads/sec)

Replication Bandwidth:
- Average write size: 1KB
- Replication traffic: 10K writes/sec * 1KB * 10 replicas = 100MB/sec
- Daily: 100MB/s * 86400s = 8.4TB/day replication traffic
```

### 3. Data Model Considerations

```java
@Entity
public class ReplicatedEntity {

    @Id
    private UUID id;  // Globally unique (important for multi-leader)

    @Version
    private Long version;  // Optimistic locking for conflict detection

    @Column
    private Instant lastModified;  // Timestamp for conflict resolution

    @Column
    private String dataCenter;  // Track write origin

    // Business data
    private String content;
}
```

### 4. High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                    LOAD BALANCER                        │
│         (Routing: writes→primary, reads→replicas)       │
└────────────┬────────────────────────────┬───────────────┘
             │                            │
             ↓                            ↓
      ┌──────────┐               ┌──────────────┐
      │ PRIMARY  │──────────────→│   REPLICA 1  │
      │  (RW)    │   Replication │   (Read-only)│
      └──────────┘      Log      └──────────────┘
             │
             │                    ┌──────────────┐
             └───────────────────→│   REPLICA 2  │
                  Replication     │   (Read-only)│
                     Log          └──────────────┘
```

### 5. Deep Dives

**A. Handling Replication Lag**
```python
class ReadAfterWriteConsistency:
    """
    Ensure user sees their own writes immediately
    """

    def read(self, user_id: str, resource_id: str):
        # Check if user recently wrote this resource
        recent_write = self.write_cache.get(f"{user_id}:{resource_id}")

        if recent_write and recent_write.timestamp > (time.now() - 1.0):
            # Read from primary (guaranteed fresh)
            return self.primary_db.read(resource_id)
        else:
            # Read from replica (may be stale, but that's ok)
            return self.replica_db.read(resource_id)
```

**B. Monitoring**
```
Key Metrics:
1. Replication Lag (per replica)
   - Target: < 1 second for async, < 100ms for semi-sync

2. Replication Throughput
   - Bytes/sec replicated

3. Replica Health
   - Connection status, last heartbeat

4. Failover Time
   - Time to promote replica to primary
```

---

## 🧠 MIND MAP: REPLICATION CONCEPTS

```
                    REPLICATION
                         |
        ┌────────────────┼────────────────┐
        ↓                ↓                 ↓
    WHY REPLICATE?   HOW REPLICATE?   WHAT PROBLEMS?
        |                |                 |
   ┌────┼────┐      ┌────┼────┐      ┌────┼────┐
   ↓    ↓    ↓      ↓    ↓    ↓      ↓    ↓    ↓
 Fault Scale Geo  Sync Semi Async   Lag Conflicts Failures
 Tolerance        Slow Fast Fast    Stale Chaos  Failover

                ↓ Topologies ↓
         ┌──────┼──────┬──────┐
         ↓      ↓      ↓      ↓
      Single  Multi  Leader  P2P
      Leader  Leader  less   (Gossip)
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **Replication = Photocopier Network 📠**
- You have one master document (primary)
- Make copies for different offices (replicas)
- Copies might be slightly outdated (replication lag)
- If master burns, promote a copy to master (failover)

### 2. **Synchronous vs Async = Group Text 📱**
- **Synchronous**: Wait for "Read" receipts from ALL friends before sending next message
  - Slow but everyone definitely got it
- **Asynchronous**: Send and forget
  - Fast but maybe some friends' phones are off

### 3. **Replication Lag = News Propagation 📰**
- Event happens in New York (write to primary)
- Takes time for news to reach California (replication lag)
- Radio is faster (low lag) but might miss details
- Newspaper is slower (high lag) but guaranteed complete

### 4. **Read-After-Write Consistency = Your Own Diary 📔**
- You write in your diary (write to primary)
- You should ALWAYS see your latest entry (read from primary for your own writes)
- But your friend reading your diary might see yesterday's version (read from replica)

---

## 🔑 Key Takeaways

1. **Replication trades consistency for availability/performance**
   - CAP theorem in action

2. **Async replication is most common in production**
   - Low latency, high availability
   - Accept eventual consistency

3. **Monitor replication lag religiously**
   - Lag > 5 seconds = investigate
   - Lag > 60 seconds = page on-call

4. **Different data needs different guarantees**
   - Financial: Synchronous or semi-sync
   - Social media: Async is fine
   - User preferences: Read-after-write consistency

5. **Topology choice depends on requirements**
   - Single region → Single-leader
   - Multi-region writes → Multi-leader
   - Extreme availability → Leaderless
