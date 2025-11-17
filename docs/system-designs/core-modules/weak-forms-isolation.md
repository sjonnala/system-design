# Weak Forms of Isolation Deep Dive

## Contents

- [Weak Forms of Isolation Deep Dive](#weak-forms-of-isolation-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Spectrum of Weak Isolation](#the-spectrum-of-weak-isolation)
    - [Read Phenomena and Anomalies](#read-phenomena-and-anomalies)
    - [Real-World Weak Isolation Models](#real-world-weak-isolation-models)
    - [When Weak Isolation is Acceptable](#when-weak-isolation-is-acceptable)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: WEAK ISOLATION](#mind-map-weak-isolation)

## Core Mental Model

```text
WEAK ISOLATION = Trading correctness for performance/availability

The Fundamental Trade-off:
┌──────────────────────────────────────────────────────────┐
│ Strong Isolation → Correct, Slow, Less Available         │
│ Weak Isolation   → Fast, Highly Available, Anomalies!    │
└──────────────────────────────────────────────────────────┘

Why accept weak isolation?
✅ Higher throughput (10-100x in some cases)
✅ Lower latency (no blocking, no coordination)
✅ Better availability (no distributed coordination)
✅ Geo-distribution friendly (cross-datacenter)

Cost:
❌ Application must handle inconsistencies
❌ Surprising behaviors (race conditions, stale reads)
❌ Harder to reason about correctness
```

**The Isolation Spectrum:**
```
┌────────────────────────────────────────────────────────┐
│ STRONGEST (Most Guarantees, Worst Performance)         │
├────────────────────────────────────────────────────────┤
│ Strict Serializability (Linearizability + Serializable)│
│ ↓                                                       │
│ Serializability (SSI, 2PL)                             │
│ ↓                                                       │
│ Snapshot Isolation                                     │
│ ↓                                                       │
│ Repeatable Read                                        │
│ ↓                                                       │
│ Read Committed                                         │
│ ↓                                                       │
│ Read Uncommitted                                       │
│ ↓                                                       │
│ Eventual Consistency                                   │
│ ↓                                                       │
│ Causal Consistency                                     │
│ ↓                                                       │
│ Read Your Writes                                       │
│ ↓                                                       │
│ Monotonic Reads/Writes                                 │
├────────────────────────────────────────────────────────┤
│ WEAKEST (Fewest Guarantees, Best Performance)          │
└────────────────────────────────────────────────────────┘
```

---

## The Spectrum of Weak Isolation

🎓 **PROFESSOR**: Weak isolation encompasses **many different guarantees** beyond ANSI SQL standards.

### 1. **Read Uncommitted** (Weakest SQL Standard)

**Guarantees**: Almost none!

**Allows:**
- ✗ Dirty Reads
- ✗ Non-Repeatable Reads
- ✗ Phantom Reads
- ✗ Write Skew

```sql
-- Transaction T1
BEGIN TRANSACTION ISOLATION LEVEL READ UNCOMMITTED;
SELECT balance FROM accounts WHERE id = 1;  -- Returns $500

-- Transaction T2 (uncommitted!)
BEGIN TRANSACTION;
UPDATE accounts SET balance = 1000 WHERE id = 1;
-- NOT committed yet!

-- Transaction T1 continues
SELECT balance FROM accounts WHERE id = 1;  -- Returns $1000 (dirty!)

-- Transaction T2 rolls back
ROLLBACK;  -- T1 saw data that never existed!
```

🏗️ **ARCHITECT**: **When to use (rare):**

```java
@Service
public class MetricsService {

    /**
     * Approximate real-time metrics - dirty reads acceptable
     */
    @Transactional(isolation = Isolation.READ_UNCOMMITTED)
    public DashboardMetrics getRealTimeMetrics() {

        // Fast, no blocking, approximate counts OK
        long activeUsers = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM active_sessions",
            Long.class
        );

        long pendingOrders = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM orders WHERE status = 'PENDING'",
            Long.class
        );

        // ±5% accuracy acceptable for dashboard
        return new DashboardMetrics(activeUsers, pendingOrders);
    }
}
```

---

### 2. **Eventual Consistency** (Distributed Systems)

**Guarantee**: If no new updates, **eventually** all replicas converge to same value.

**Timeline:**
```
Time: t0    t1    t2    t3    t4    t5
──────────────────────────────────────→
Write(x=1)
Replica A:  1     1     1     1     1
Replica B:  0     0     1     1     1  ← Eventually catches up
Replica C:  0     0     0     1     1  ← Eventually catches up

At t5: All replicas consistent (eventual)
```

**Real-World Example - DynamoDB:**

```java
@Service
public class UserProfileService {

    @Autowired
    private DynamoDBMapper dynamoMapper;

    /**
     * Eventually consistent read (default in DynamoDB)
     */
    public UserProfile getProfile(String userId) {

        // Reads from random replica (might be stale)
        UserProfile profile = dynamoMapper.load(UserProfile.class, userId);

        // May see old data if recent update on another replica
        return profile;
    }

    /**
     * Strongly consistent read (costs 2x read capacity)
     */
    public UserProfile getProfileConsistent(String userId) {

        DynamoDBMapperConfig config = DynamoDBMapperConfig.builder()
            .withConsistentReads(DynamoDBMapperConfig.ConsistentReads.CONSISTENT)
            .build();

        // Reads from primary replica (latest data, but slower)
        return dynamoMapper.load(UserProfile.class, userId, config);
    }

    /**
     * Write (always goes to primary)
     */
    public void updateProfile(UserProfile profile) {
        dynamoMapper.save(profile);
        // Asynchronously replicates to other regions
    }
}
```

**Anomaly Example:**
```java
// User updates profile picture
userService.updateProfile(userId, newPictureUrl);

// Immediately refresh page
UserProfile profile = userService.getProfile(userId);
// Might see OLD picture (stale replica)! 😱

// Solution: Read your own writes guarantee (see below)
```

---

### 3. **Causal Consistency**

**Guarantee**: If operation A **causally precedes** operation B, all processes see A before B.

```
Causal Order Example:

Thread 1: Write(x=1)  ────→  Write(y=2)
              │                  ↑
              │ (causally        │
              │  precedes)       │
              └──────────────────┘

All replicas must see: x=1 before y=2

Thread 2: Write(z=3)  (concurrent with x, y - no causal order)
```

**Implementation:**
```python
class CausalConsistency:
    """
    Simplified causal consistency using vector clocks
    """

    def __init__(self, replica_id, num_replicas):
        self.replica_id = replica_id
        self.vector_clock = [0] * num_replicas  # Vector clock
        self.data_store = {}
        self.pending_writes = []  # Writes waiting for causal dependencies

    def write(self, key, value):
        """
        Write with causal timestamp
        """
        # Increment our clock
        self.vector_clock[self.replica_id] += 1

        # Create versioned write
        write = {
            'key': key,
            'value': value,
            'timestamp': self.vector_clock.copy()  # Causal timestamp
        }

        self.data_store[key] = write
        return write

    def read(self, key):
        """
        Read with causal consistency
        """
        write = self.data_store.get(key)

        if write is None:
            return None

        # Update our knowledge of causal history
        self.merge_clocks(write['timestamp'])

        return write['value']

    def receive_remote_write(self, write):
        """
        Receive write from another replica
        """
        # Check if we've seen all causal dependencies
        if self.has_seen_dependencies(write['timestamp']):
            # Safe to apply
            self.data_store[write['key']] = write
            self.merge_clocks(write['timestamp'])
        else:
            # Wait until dependencies arrive
            self.pending_writes.append(write)

        # Process pending writes that now have dependencies satisfied
        self.process_pending_writes()

    def has_seen_dependencies(self, timestamp):
        """
        Check if we've seen all events that causally precede this write
        """
        for i, t in enumerate(timestamp):
            if i == self.replica_id:
                continue
            if t > self.vector_clock[i]:
                return False  # Missing dependency
        return True

    def merge_clocks(self, other_clock):
        """
        Update vector clock with knowledge from other operation
        """
        for i in range(len(self.vector_clock)):
            self.vector_clock[i] = max(self.vector_clock[i], other_clock[i])
```

**Real-World Use - Social Media:**
```java
@Service
public class SocialMediaService {

    /**
     * Causal consistency ensures reply appears after parent post
     */
    public void replyToPost(Long postId, Long userId, String content) {

        // Read parent post (establishes causal dependency)
        Post parentPost = postRepo.findById(postId);

        // Create reply with causal timestamp
        Reply reply = new Reply(postId, userId, content);
        reply.setCausalTimestamp(parentPost.getTimestamp());

        replyRepo.save(reply);

        // All replicas will show parent before reply (causal order)
    }

    /**
     * Concurrent posts can appear in any order (no causal relationship)
     */
    public void createPost(Long userId, String content) {
        // Independent posts, no causal order required
        Post post = new Post(userId, content);
        postRepo.save(post);
    }
}
```

---

### 4. **Read Your Writes** (Session Consistency)

**Guarantee**: After writing, **you** will see your write in subsequent reads.

```
User Session:
t1: WRITE(profile_pic = new.jpg)
t2: READ(profile_pic)
    → Must return new.jpg (even if replicas stale)

Different User:
t2: READ(profile_pic)
    → May return old.jpg (no guarantee for other users)
```

**Implementation with Sticky Sessions:**

```java
@Service
public class ReadYourWritesService {

    @Autowired
    private ReplicaManager replicaManager;

    /**
     * Write - track which replica has latest data
     */
    public void updateProfile(String userId, ProfileUpdate update) {

        // Write to primary replica
        Replica primary = replicaManager.getPrimary();
        primary.write(userId, update);

        // Store replica ID in user session
        UserSession session = SessionContext.getCurrentSession();
        session.setAttribute("last_write_replica", primary.getId());
        session.setAttribute("last_write_timestamp", System.currentTimeMillis());
    }

    /**
     * Read - route to replica with latest data
     */
    public Profile getProfile(String userId) {

        UserSession session = SessionContext.getCurrentSession();
        String lastWriteReplica = session.getAttribute("last_write_replica");
        Long lastWriteTimestamp = session.getAttribute("last_write_timestamp");

        if (lastWriteReplica != null) {
            // Route to same replica (sticky session)
            Replica replica = replicaManager.getReplica(lastWriteReplica);

            // Verify replica has replicated our write
            if (replica.getReplicationLag() < System.currentTimeMillis() - lastWriteTimestamp) {
                return replica.read(userId);
            }
        }

        // Fallback: read from primary (guaranteed latest)
        return replicaManager.getPrimary().read(userId);
    }
}
```

**Real-World - AWS DynamoDB:**
```java
public class DynamoDBReadYourWrites {

    @Autowired
    private AmazonDynamoDB dynamoDB;

    public void updateAndRead(String userId, String newEmail) {

        // Write
        UpdateItemRequest updateRequest = new UpdateItemRequest()
            .withTableName("Users")
            .withKey(Map.of("userId", new AttributeValue(userId)))
            .withUpdateExpression("SET email = :email")
            .withExpressionAttributeValues(Map.of(
                ":email", new AttributeValue(newEmail)
            ));

        dynamoDB.updateItem(updateRequest);

        // Read your write: Use consistent read
        GetItemRequest getRequest = new GetItemRequest()
            .withTableName("Users")
            .withKey(Map.of("userId", new AttributeValue(userId)))
            .withConsistentRead(true);  // Read from primary

        GetItemResult result = dynamoDB.getItem(getRequest);
        // Guaranteed to see newEmail
    }
}
```

---

### 5. **Monotonic Reads**

**Guarantee**: If you read value `v1`, subsequent reads won't return older value `v0`.

**Violation Example:**
```
t1: READ from Replica A → version 5
t2: READ from Replica B → version 3 (went backwards!)
```

**With Monotonic Reads:**
```
t1: READ from Replica A → version 5
t2: READ from Replica B → WAIT until version ≥ 5
```

**Implementation:**
```python
class MonotonicReads:
    """
    Ensure reads never go backwards in time
    """

    def __init__(self):
        self.client_versions = {}  # client_id -> last_seen_version

    def read(self, client_id, key, replica):
        """
        Read with monotonic guarantee
        """
        last_seen = self.client_versions.get(client_id, 0)

        while True:
            value, version = replica.read_with_version(key)

            if version >= last_seen:
                # Safe: not going backwards
                self.client_versions[client_id] = version
                return value
            else:
                # Replica too stale, retry or switch replica
                time.sleep(0.1)
                # Or: Try different replica
```

---

### 6. **Monotonic Writes**

**Guarantee**: Writes from same client are applied in order.

**Example:**
```java
// Client writes twice
client.write("counter", 1);
client.write("counter", 2);

// All replicas must see: 1 → 2
// Never: 2 → 1 (out of order)
```

**Implementation - Sequence Numbers:**
```java
public class MonotonicWriteClient {

    private final AtomicLong sequenceNumber = new AtomicLong(0);
    private final String clientId = UUID.randomUUID().toString();

    public void write(String key, String value) {

        long seq = sequenceNumber.incrementAndGet();

        WriteRequest request = new WriteRequest(
            clientId,
            seq,
            key,
            value,
            System.currentTimeMillis()
        );

        // Replicas reject writes with out-of-order sequence numbers
        replicaManager.write(request);
    }
}

// Replica side
public class ReplicaWriteHandler {

    private Map<String, Long> clientSequences = new ConcurrentHashMap<>();

    public void handleWrite(WriteRequest request) {

        Long lastSeq = clientSequences.getOrDefault(request.getClientId(), 0L);

        if (request.getSequence() <= lastSeq) {
            // Duplicate or out-of-order, reject
            throw new OutOfOrderWriteException();
        }

        if (request.getSequence() > lastSeq + 1) {
            // Gap in sequence, wait for missing writes
            pendingWrites.add(request);
            return;
        }

        // In order, apply
        applyWrite(request);
        clientSequences.put(request.getClientId(), request.getSequence());
    }
}
```

---

## Read Phenomena and Anomalies

🎓 **PROFESSOR**: Understanding **anomalies** helps choose appropriate isolation level.

### The Extended Anomaly Catalog:

```
┌────────────────────────────────────────────────────────────┐
│ Anomaly               Description                          │
├────────────────────────────────────────────────────────────┤
│ P0: Dirty Write       Overwrite uncommitted data           │
│ P1: Dirty Read        Read uncommitted data                │
│ P2: Non-Repeatable    Read different committed values      │
│ P3: Phantom           New rows appear in range             │
│ P4: Lost Update       Concurrent updates lost              │
│ A5A: Read Skew        Inconsistent reads across rows       │
│ A5B: Write Skew       Inconsistent writes across rows      │
│ G0: Write Cycle       Dependency cycle in writes           │
│ G1: Intermediate Read Read intermediate state              │
│ G2: Anti-Dependency   Read-write dependency cycle          │
└────────────────────────────────────────────────────────────┘
```

### Lost Update Anomaly (P4)

**Scenario**: Classic read-modify-write race condition.

```java
// WITHOUT proper isolation - LOST UPDATE

@Transactional(isolation = Isolation.READ_COMMITTED)
public void incrementCounter(String counterId) {

    // Thread A reads: counter = 100
    Counter counter = counterRepo.findById(counterId);

    // Thread B reads: counter = 100 (same value!)
    // Both threads read before either writes

    int newValue = counter.getValue() + 1;  // A: 101, B: 101

    // Thread A writes: counter = 101
    counter.setValue(newValue);
    counterRepo.save(counter);

    // Thread B writes: counter = 101 (overwrites A's increment!)
    // Expected: 102, Actual: 101
    // Lost update! ❌
}
```

**Solutions:**

```java
// Solution 1: Atomic increment (best)
@Transactional(isolation = Isolation.READ_COMMITTED)
public void incrementCounterAtomic(String counterId) {
    jdbcTemplate.update(
        "UPDATE counters SET value = value + 1 WHERE id = ?",
        counterId
    );
    // Database ensures atomicity
}

// Solution 2: SELECT FOR UPDATE
@Transactional(isolation = Isolation.READ_COMMITTED)
public void incrementCounterLocked(String counterId) {
    Counter counter = counterRepo.findByIdForUpdate(counterId);
    // Locked - other threads wait
    counter.setValue(counter.getValue() + 1);
    counterRepo.save(counter);
}

// Solution 3: Optimistic locking
@Entity
public class Counter {
    @Id private String id;
    private int value;
    @Version private Long version;  // JPA optimistic lock
}

@Transactional(isolation = Isolation.READ_COMMITTED)
@Retryable(value = OptimisticLockException.class, maxAttempts = 5)
public void incrementCounterOptimistic(String counterId) {
    Counter counter = counterRepo.findById(counterId).orElseThrow();
    counter.setValue(counter.getValue() + 1);
    counterRepo.save(counter);
    // Throws OptimisticLockException if version changed
}

// Solution 4: REPEATABLE READ or SERIALIZABLE
@Transactional(isolation = Isolation.REPEATABLE_READ)
public void incrementCounterIsolated(String counterId) {
    Counter counter = counterRepo.findById(counterId).orElseThrow();
    counter.setValue(counter.getValue() + 1);
    counterRepo.save(counter);
    // Serialization error if conflict
}
```

---

### Read Skew (A5A)

**Scenario**: Reading related data at different times sees inconsistent state.

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public BigDecimal getTotalBalance(Long userId) {

    // Read checking account
    Account checking = accountRepo.findByUserAndType(userId, CHECKING);
    BigDecimal checkingBalance = checking.getBalance();  // $100

    // Concurrent transfer: $50 from checking → savings
    // (commits between our two reads)

    // Read savings account
    Account savings = accountRepo.findByUserAndType(userId, SAVINGS);
    BigDecimal savingsBalance = savings.getBalance();  // $0 (before transfer)

    // Total: $100 (WRONG! Should be $150 or $100, not in-between)
    return checkingBalance.add(savingsBalance);
}
```

**Fix: Use REPEATABLE READ:**
```java
@Transactional(isolation = Isolation.REPEATABLE_READ)
public BigDecimal getTotalBalance(Long userId) {
    // Snapshot at transaction start - consistent view
    // Total will be correct
}
```

---

## When Weak Isolation is Acceptable

🏗️ **ARCHITECT**: **Business context** determines acceptable isolation level.

### Decision Matrix:

```
┌────────────────────────────────────────────────────────────┐
│ Use Case                     Acceptable Isolation          │
├────────────────────────────────────────────────────────────┤
│ View counters                READ UNCOMMITTED / Eventual   │
│ Social media likes           Eventual Consistency          │
│ Comment threads              Causal Consistency            │
│ User profile updates         Read Your Writes              │
│ Shopping cart                Monotonic Reads/Writes        │
│ Product catalog              READ COMMITTED                │
│ Order processing             REPEATABLE READ               │
│ Financial transactions       SERIALIZABLE                  │
│ Inventory management         SERIALIZABLE                  │
│ Seat booking                 SERIALIZABLE                  │
└────────────────────────────────────────────────────────────┘
```

### Example - Social Media Platform:

```java
@Service
public class SocialMediaPlatform {

    /**
     * Like counter - eventual consistency OK
     */
    public void likePost(Long postId, Long userId) {
        // Asynchronous, eventually consistent
        likeCounterService.increment(postId);

        // ±100 likes discrepancy acceptable for display
    }

    /**
     * Comments - causal consistency (replies after parent)
     */
    @CausalConsistent
    public void addComment(Long postId, Long userId, String text, Long parentCommentId) {

        if (parentCommentId != null) {
            // Ensure parent visible before reply
            Comment parent = commentRepo.findById(parentCommentId);
            // Causal dependency established
        }

        Comment comment = new Comment(postId, userId, text, parentCommentId);
        commentRepo.save(comment);
    }

    /**
     * User profile - read your writes
     */
    @ReadYourWrites
    public void updateProfile(Long userId, ProfileUpdate update) {
        profileRepo.save(userId, update);

        // User must see their own changes immediately
    }

    /**
     * Payments - strong consistency
     */
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public void processPayment(Long userId, BigDecimal amount) {

        // Money transfer requires serializability
        Account account = accountRepo.findByIdForUpdate(userId);

        if (account.getBalance().compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }

        account.debit(amount);
        accountRepo.save(account);
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification (RADIO: Requirements)

```
Critical Questions:
1. What's the cost of showing stale data?
   → Low cost: Weak isolation OK
   → High cost: Strong isolation required

2. Geographic distribution?
   → Multi-region: Consider eventual consistency
   → Single region: Stronger isolation feasible

3. Read:Write ratio?
   → Read-heavy: Eventual consistency benefits
   → Write-heavy: Coordination costs increase

4. Availability vs Consistency (CAP theorem)?
   → Favor availability: Weak isolation
   → Favor consistency: Strong isolation

Example:
"For a Twitter-like feed, we can use eventual consistency.
Seeing a tweet 100ms late is acceptable. But for payments,
we need strong consistency to prevent double-spending."
```

### 2. Capacity Estimation (RADIO: Scale)

```
Throughput with Weak Isolation:
- Eventual Consistency: 100,000 writes/sec (no coordination)
- Causal Consistency: 50,000 writes/sec (vector clock overhead)
- Strong Consistency: 5,000 writes/sec (consensus required)

Replication Lag:
- Cross-region: 100-500ms typical
- Same region: 10-50ms typical
- Synchronous replication: 0ms but 50% throughput

Storage for Weak Isolation:
- Multi-version storage: 2-3x base size
- Conflict resolution logs: +10-20% storage
- Causal metadata: +5% storage
```

### 3. Data Model (RADIO: Data-Model)

```java
// Domain Model: Designed for Weak Isolation

@Document(collection = "posts")
public class Post {

    @Id
    private String id;

    private String userId;
    private String content;

    // Eventual consistency metadata
    private long version;  // Increment on each update
    private Instant lastModified;

    // Causal consistency - vector clock
    private Map<String, Long> vectorClock;

    // Conflict resolution - Last Write Wins
    @JsonProperty("_ts")  // DynamoDB timestamp
    private long timestamp;

    // Application-level tombstone for deletes
    private boolean deleted;
    private Instant deletedAt;
}

@Service
public class PostService {

    /**
     * Write with eventual consistency
     */
    public void createPost(Post post) {

        post.setVersion(1);
        post.setLastModified(Instant.now());
        post.setTimestamp(System.currentTimeMillis());

        // Async replication to all regions
        postRepo.save(post);

        // Replicas eventually converge using Last Write Wins (timestamp)
    }

    /**
     * Read with monotonic reads guarantee
     */
    public Post getPost(String postId, String clientId) {

        // Track client's read version
        Long lastSeenVersion = clientReadVersions.get(clientId);

        Post post = postRepo.findById(postId);

        if (lastSeenVersion != null && post.getVersion() < lastSeenVersion) {
            // Replica too stale, read from primary or wait
            post = postRepo.findByIdFromPrimary(postId);
        }

        // Update client's version
        clientReadVersions.put(clientId, post.getVersion());

        return post;
    }

    /**
     * Conflict resolution for concurrent updates
     */
    public void handleConflict(Post version1, Post version2) {

        // Strategy 1: Last Write Wins (timestamp)
        if (version1.getTimestamp() > version2.getTimestamp()) {
            return version1;
        }

        // Strategy 2: Custom merge (e.g., combine edits)
        Post merged = new Post();
        merged.setContent(mergeContent(version1.getContent(), version2.getContent()));

        // Strategy 3: Expose conflict to user
        conflictResolutionService.notifyUser(version1, version2);
    }
}
```

### 4. High-Level Design (RADIO: Initial Design)

```
┌─────────────────────────────────────────────────────────────┐
│                    GLOBAL ARCHITECTURE                      │
│                                                              │
│   US-East Region          EU Region          Asia Region    │
│   ┌────────────┐       ┌────────────┐    ┌────────────┐    │
│   │ App Server │       │ App Server │    │ App Server │    │
│   └─────┬──────┘       └─────┬──────┘    └─────┬──────┘    │
│         │                    │                  │            │
│   ┌─────▼──────┐       ┌─────▼──────┐    ┌─────▼──────┐    │
│   │  Replica   │◄─────►│  Replica   │◄──►│  Replica   │    │
│   │ (Primary)  │       │ (Eventual) │    │ (Eventual) │    │
│   └────────────┘       └────────────┘    └────────────┘    │
│         │                    ↑                  ↑            │
│         │                    │                  │            │
│         └────────────────────┴──────────────────┘            │
│              Asynchronous Replication                        │
│              (100-500ms lag)                                 │
└─────────────────────────────────────────────────────────────┘

Consistency Guarantees:
1. Writes → Primary replica (strong consistency)
2. Reads → Local replica (eventual consistency)
3. Read Your Writes → Sticky session to primary
4. Causal → Vector clock propagation
```

### 5. Deep Dives (RADIO: Optimize)

**A. Implementing Causal Consistency**

```java
@Service
public class CausalConsistencyService {

    /**
     * Vector clock implementation for causal ordering
     */
    public static class VectorClock {

        private final Map<String, Long> clock;

        public VectorClock() {
            this.clock = new ConcurrentHashMap<>();
        }

        public void increment(String nodeId) {
            clock.merge(nodeId, 1L, Long::sum);
        }

        public boolean happensBefore(VectorClock other) {
            // This happens before other if:
            // - All our entries ≤ other's entries
            // - At least one entry < other's entry

            boolean hasLess = false;

            for (Map.Entry<String, Long> entry : clock.entrySet()) {
                long otherValue = other.clock.getOrDefault(entry.getKey(), 0L);

                if (entry.getValue() > otherValue) {
                    return false;  // Not before
                }

                if (entry.getValue() < otherValue) {
                    hasLess = true;
                }
            }

            return hasLess;
        }

        public void merge(VectorClock other) {
            for (Map.Entry<String, Long> entry : other.clock.entrySet()) {
                clock.merge(entry.getKey(), entry.getValue(), Math::max);
            }
        }
    }

    /**
     * Causal write
     */
    public void writeWithCausal(String key, String value, VectorClock causalDeps) {

        VectorClock writeTimestamp = new VectorClock();
        writeTimestamp.merge(causalDeps);
        writeTimestamp.increment(localNodeId);

        Write write = new Write(key, value, writeTimestamp);

        // Propagate to all replicas
        replicaManager.broadcast(write);
    }

    /**
     * Causal read - wait for dependencies
     */
    public String readWithCausal(String key) {

        Write write = localStorage.get(key);

        if (write == null) {
            return null;
        }

        // Ensure we've seen all causal dependencies
        while (!hasCausalDependencies(write.getTimestamp())) {
            Thread.sleep(10);  // Wait for replication
        }

        return write.getValue();
    }
}
```

**B. Monitoring Weak Isolation**

```java
@Component
public class WeakIsolationMetrics {

    @Scheduled(fixedRate = 60000)
    public void recordMetrics() {

        // Replication lag by region
        Map<String, Long> replicationLag = replicaManager.getReplicationLag();

        for (Map.Entry<String, Long> entry : replicationLag.entrySet()) {
            registry.gauge("replication.lag_ms",
                Tags.of("region", entry.getKey()),
                entry.getValue()
            );

            // Alert on high lag
            if (entry.getValue() > 1000) {
                alertService.send("High replication lag in " + entry.getKey());
            }
        }

        // Conflict rate (for eventual consistency)
        long conflicts = conflictResolver.getConflictCount();
        registry.counter("eventual_consistency.conflicts").increment(conflicts);

        // Stale read rate
        long staleReads = staleReadDetector.getStaleReadCount();
        registry.counter("weak_isolation.stale_reads").increment(staleReads);
    }
}
```

---

## 🧠 MIND MAP: WEAK ISOLATION

```
                WEAK ISOLATION
                      |
        ┌─────────────┼─────────────┐
        ↓             ↓             ↓
    ANOMALIES     GUARANTEES    USE CASES
        |             |             |
   ┌────┴────┐   ┌────┴────┐   ┌────┴────┐
   ↓         ↓   ↓         ↓   ↓         ↓
 Dirty    Lost   Read    Causal Social   View
 Read     Update Your             Media   Counters
                 Writes
   |         |       |         |       |
 See      Read-  Sticky   Vector   Likes Analytics
 Uncommit Modify Session  Clock    Comments
 Data     Write
```

### 💡 EMOTIONAL ANCHORS (For Subconscious Power)

1. **Eventual Consistency = Mail Delivery 📬**
   - Letter sent from NYC to LA
   - Takes time to arrive
   - Eventually delivered (unless lost!)
   - Not instant, but reliable

2. **Read Your Writes = Mirror 🪞**
   - You always see your own reflection
   - Others might see you differently (angle, delay)
   - Your view is consistent with your actions

3. **Causal Consistency = Email Thread 📧**
   - Reply must come after original
   - Side conversations can interleave
   - Causal order preserved

4. **Monotonic Reads = Time Travel Ban ⏰**
   - Can't go backwards in time
   - Always move forward or stay same
   - No seeing "older" versions after "newer"

---

## 🎯 INTERVIEW GOLD: KEY TALKING POINTS

1. **CAP theorem**: "In distributed systems, we often choose AP (availability + partition tolerance) over CP (consistency). This means accepting weak isolation."

2. **Real-world trade-off**: "Facebook uses eventual consistency for likes. Seeing 99 vs 100 likes doesn't matter. But payment systems need strong consistency."

3. **Performance gain**: "Eventual consistency can achieve 10-100x higher throughput than strong consistency. No coordination overhead."

4. **Conflict resolution**: "With eventual consistency, we need conflict resolution strategies: Last Write Wins (timestamp), Custom Merge, or Expose to User."

5. **Read your writes**: "Critical for UX. User must see their own changes immediately, even if others see stale data."

6. **Causal vs eventual**: "Causal consistency is stronger than eventual, weaker than sequential. Perfect for social media (reply after post)."

---

## 📚 FURTHER READING

**Academic Papers:**
- "Replicated Data Consistency Explained Through Baseball" (Terry, 2013) - Excellent!
- "Consistency in Non-Transactional Distributed Storage Systems" (Viotti & Vukolić, 2016)

**Books:**
- "Designing Data-Intensive Applications" by Martin Kleppmann (Chapter 5, 9)
- "Database Internals" by Alex Petrov (Chapter 12: Distributed Transactions)
