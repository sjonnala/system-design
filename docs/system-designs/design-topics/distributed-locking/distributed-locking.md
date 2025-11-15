# Design a Distributed Locking System

**Companies**: Google (Chubby), Apache (ZooKeeper), HashiCorp (Consul), etcd (Kubernetes)
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a distributed locking service that allows multiple clients in a distributed system to coordinate access to shared resources. The system must provide strong consistency guarantees, handle failures gracefully, and scale to thousands of concurrent locks.

---

## Step 1: Requirements (10 min)

### Functional Requirements

1. **Acquire Lock**: Client can acquire exclusive lock on a named resource
2. **Release Lock**: Lock owner can explicitly release lock
3. **Auto-Expiry**: Locks automatically expire after TTL (prevent deadlocks)
4. **Lock Renewal**: Clients can extend lock TTL (heartbeat mechanism)
5. **Fair Queuing** (optional): FIFO waiting queue for contending clients
6. **Fencing Tokens**: Monotonically increasing tokens to prevent stale operations

**Prioritize**: Focus on acquire, release, and auto-expiry for MVP

### Non-Functional Requirements

1. **Strong Consistency**: Linearizable lock operations (CP in CAP theorem)
2. **High Availability**: 99.99% uptime, tolerate node failures
3. **Low Latency**: <10ms P99 for lock acquisition (same datacenter)
4. **Fault Tolerant**: Survive network partitions, node crashes
5. **Deadlock Free**: No permanent lock blocking via TTL
6. **Safety**: At most one lock holder at any time (no split-brain)

### Capacity Estimation

**Assumptions**:
- 2,000 microservice instances
- 10 lock operations per instance per minute
- Average lock hold duration: 5 minutes (300 seconds)
- Heartbeat every 15 seconds (TTL/2)

**Lock Operations**:
```
Acquire QPS:  2000 instances × 10 locks/min ÷ 60 = 333 QPS
Renew QPS:    333 × (300s / 15s heartbeat) = 6,660 QPS (dominant!)
Release QPS:  333 QPS
Total Write:  333 + 6,660 + 333 = 7,326 QPS (avg), ~22K QPS (peak 3x)
```

**Concurrent Locks**:
```
Active locks = Acquire rate × Hold duration
= 333 QPS × 300s = ~100,000 concurrent locks
```

**Storage**:
```
Lock entry: ~200 bytes (resource name + owner + metadata)
Active storage: 100K × 200B = 20 MB (negligible)
History (30 days): 7.3K QPS × 86,400s × 30d × 200B = 3.8 TB (audit logs)
```

**Consensus Cluster**:
```
5-node Raft cluster (tolerates 2 failures)
Leader capacity: ~10K write QPS (etcd benchmark)
Our load: 7.3K avg, 22K peak → single cluster (with sharding for peak)
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│          Microservices (Lock Clients)               │
│  Payment Service │ Inventory │ Scheduler │ ...      │
└──────────────────────┬──────────────────────────────┘
                       │ gRPC/HTTP
                       ▼
┌─────────────────────────────────────────────────────┐
│              API Gateway / Load Balancer            │
│  Auth • Rate Limiting • Request Routing             │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Lock Manager 1  │  │  Lock Manager 2  │  (stateless)
│  (Leader)        │  │  (Follower)      │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │   Raft Consensus    │
         │  (5-node cluster)   │
         │  etcd / Consul      │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌─────────────────┐  ┌─────────────────┐
│  Persistent DB  │  │  Redis Cache    │
│  (PostgreSQL)   │  │  (Hot locks)    │
└─────────────────┘  └─────────────────┘
```

### Components

**Lock Manager (Stateless)**:
- Handle client requests (acquire, release, renew)
- Validate ownership, check expiry
- Interact with consensus layer
- Background cleanup of expired locks

**Consensus Layer (Raft/etcd/Consul)**:
- Single source of truth for lock state
- Provides linearizable writes (strong consistency)
- Leader election, log replication
- Quorum-based commits (3/5 nodes)

**Persistent Storage**:
- Durable lock history (audit trail)
- Metadata (owner, timestamps, fencing tokens)
- Can recover from snapshots

**Cache (Redis)**:
- Fast lookups for active locks
- Reduce consensus layer read load
- Write-through caching

---

## Step 3: Data Model (10 min)

### Lock Entry Schema

```sql
-- Primary lock table (in consensus layer)
CREATE TABLE locks (
    resource_name VARCHAR(255) PRIMARY KEY,    -- e.g., "payment:order:12345"
    owner_id VARCHAR(128) NOT NULL,            -- "payment-service-pod-42"
    fencing_token BIGINT NOT NULL,             -- Monotonic counter
    acquired_at BIGINT NOT NULL,               -- Unix timestamp (ms)
    expires_at BIGINT NOT NULL,                -- acquired_at + ttl
    ttl_ms INT NOT NULL DEFAULT 30000,         -- 30 seconds
    renewal_count INT DEFAULT 0,               -- Heartbeat counter
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_expires_at ON locks(expires_at);  -- For cleanup
CREATE INDEX idx_owner_id ON locks(owner_id);      -- Query by owner

-- Lock history (audit trail)
CREATE TABLE lock_history (
    id BIGSERIAL PRIMARY KEY,
    resource_name VARCHAR(255) NOT NULL,
    owner_id VARCHAR(128),
    action VARCHAR(20),                        -- ACQUIRE, RELEASE, EXPIRE
    fencing_token BIGINT,
    timestamp BIGINT NOT NULL,
    duration_ms INT,                           -- Lock hold time
    success BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_history_resource ON lock_history(resource_name, timestamp DESC);
CREATE INDEX idx_history_timestamp ON lock_history(timestamp DESC);

-- Fencing token counter (global)
CREATE TABLE fencing_counter (
    id INT PRIMARY KEY DEFAULT 1,
    current_token BIGINT NOT NULL DEFAULT 0
);
```

### In-Memory State (Redis)

```
# Active lock (hot path)
SET lock:{resource_name} "{owner_id}:{fencing_token}:{expires_at}" EX {ttl_seconds}

# Example:
SET lock:payment:order:12345 "payment-svc-pod-42:12345:1699900000000" EX 30

# Wait queue (optional, for fairness)
LPUSH lock_queue:{resource_name} "{client_id}:{timestamp}"

# Metrics
INCR lock_acquire_total
INCR lock_acquire_failed
INCR lock_renew_total
```

---

## Step 4: APIs (5 min)

### Acquire Lock

```protobuf
// gRPC Definition
service LockService {
  rpc AcquireLock(AcquireLockRequest) returns (AcquireLockResponse);
  rpc ReleaseLock(ReleaseLockRequest) returns (ReleaseLockResponse);
  rpc RenewLock(RenewLockRequest) returns (RenewLockResponse);
  rpc CheckLock(CheckLockRequest) returns (CheckLockResponse);
}

message AcquireLockRequest {
  string resource_name = 1;       // "payment:order:12345"
  string owner_id = 2;            // "payment-service-pod-42"
  int32 ttl_ms = 3;               // 30000 (30 seconds)
  bool wait = 4;                  // Block until available (optional)
  int32 wait_timeout_ms = 5;      // Max wait time (optional)
}

message AcquireLockResponse {
  bool success = 1;
  int64 fencing_token = 2;        // Use this for operations
  int64 expires_at = 3;           // Unix timestamp (ms)
  string error_message = 4;       // If failed
  string current_owner = 5;       // If lock held by another
}
```

### Release Lock

```protobuf
message ReleaseLockRequest {
  string resource_name = 1;
  string owner_id = 2;            // Must match lock owner
}

message ReleaseLockResponse {
  bool success = 1;
  string error_message = 2;       // "Unauthorized" if not owner
}
```

### Renew Lock (Heartbeat)

```protobuf
message RenewLockRequest {
  string resource_name = 1;
  string owner_id = 2;
  int32 additional_ttl_ms = 3;    // Extend by this amount
}

message RenewLockResponse {
  bool success = 1;
  int64 new_expires_at = 2;
  string error_message = 3;
}
```

---

## Step 5: Core Algorithm - Lock Acquisition

### Pseudocode

```python
class DistributedLockManager:
    def __init__(self, consensus_client, db, cache):
        self.consensus = consensus_client  # etcd, Consul, ZK
        self.db = db
        self.cache = cache
        self.fencing_counter = AtomicCounter()

    def acquire_lock(self, resource: str, owner: str, ttl_ms: int) -> LockResponse:
        """
        Acquire exclusive lock with strong consistency guarantees.
        """
        # 1. Check cache first (fast path)
        cached_lock = self.cache.get(f"lock:{resource}")
        if cached_lock:
            cached_owner, expires_at = self._parse_cached_lock(cached_lock)
            if expires_at > current_time_ms():
                # Lock still held
                if cached_owner == owner:
                    # Same owner, allow renewal
                    return self.renew_lock(resource, owner, ttl_ms)
                else:
                    return LockResponse(success=False, current_owner=cached_owner)
            # Expired in cache, check consensus layer

        # 2. Generate fencing token (monotonically increasing)
        fencing_token = self.fencing_counter.increment()

        # 3. Try to acquire via consensus (strong consistency)
        acquired_at = current_time_ms()
        expires_at = acquired_at + ttl_ms

        lock_entry = {
            "resource_name": resource,
            "owner_id": owner,
            "fencing_token": fencing_token,
            "acquired_at": acquired_at,
            "expires_at": expires_at,
            "ttl_ms": ttl_ms
        }

        # 4. Consensus write (Raft/Paxos)
        # Uses compare-and-swap (CAS) to prevent races
        try:
            success = self.consensus.create_if_not_exists(
                key=f"/locks/{resource}",
                value=lock_entry,
                lease_ttl=ttl_ms // 1000  # seconds
            )

            if not success:
                # Lock exists, check if expired
                existing = self.consensus.get(f"/locks/{resource}")
                if existing["expires_at"] < current_time_ms():
                    # Expired, force delete and retry
                    self.consensus.delete(f"/locks/{resource}")
                    return self.acquire_lock(resource, owner, ttl_ms)
                else:
                    return LockResponse(
                        success=False,
                        current_owner=existing["owner_id"]
                    )

        except ConsensusException as e:
            # Quorum not reached, cluster unhealthy
            raise LockServiceUnavailableError(f"Consensus failure: {e}")

        # 5. Cache for fast lookups
        self.cache.setex(
            f"lock:{resource}",
            ttl_ms // 1000,
            f"{owner}:{fencing_token}:{expires_at}"
        )

        # 6. Log to audit trail (async)
        self._log_lock_event(resource, owner, "ACQUIRE", fencing_token, ttl_ms)

        # 7. Schedule heartbeat reminder (client responsibility)
        # Client should renew at ttl_ms / 2

        return LockResponse(
            success=True,
            fencing_token=fencing_token,
            expires_at=expires_at
        )

    def release_lock(self, resource: str, owner: str) -> bool:
        """
        Release lock only if caller is the owner.
        """
        # 1. Get current lock from consensus layer
        lock = self.consensus.get(f"/locks/{resource}")
        if not lock:
            return False  # Lock doesn't exist

        # 2. Verify ownership
        if lock["owner_id"] != owner:
            raise UnauthorizedError(f"Lock owned by {lock['owner_id']}, not {owner}")

        # 3. Delete from consensus (atomic)
        self.consensus.delete(f"/locks/{resource}")

        # 4. Invalidate cache
        self.cache.delete(f"lock:{resource}")

        # 5. Log release
        self._log_lock_event(resource, owner, "RELEASE", lock["fencing_token"],
                            current_time_ms() - lock["acquired_at"])

        # 6. Notify waiters (if fair queue enabled)
        self._notify_next_waiter(resource)

        return True

    def renew_lock(self, resource: str, owner: str, additional_ttl_ms: int) -> LockResponse:
        """
        Extend lock TTL (heartbeat mechanism).
        """
        lock = self.consensus.get(f"/locks/{resource}")
        if not lock:
            return LockResponse(success=False, error="Lock not found")

        if lock["owner_id"] != owner:
            return LockResponse(success=False, error="Unauthorized")

        # Extend expiration
        new_expires_at = current_time_ms() + additional_ttl_ms
        lock["expires_at"] = new_expires_at
        lock["renewal_count"] += 1

        # Update consensus
        self.consensus.put(f"/locks/{resource}", lock)

        # Update cache
        self.cache.setex(
            f"lock:{resource}",
            additional_ttl_ms // 1000,
            f"{owner}:{lock['fencing_token']}:{new_expires_at}"
        )

        return LockResponse(success=True, new_expires_at=new_expires_at)

    def _cleanup_expired_locks(self):
        """
        Background job to remove expired locks (safety net).
        Runs every 5 seconds.
        """
        current_time = current_time_ms()
        expired_locks = self.db.query(
            "SELECT resource_name FROM locks WHERE expires_at < ? LIMIT 100",
            current_time
        )

        for lock in expired_locks:
            try:
                self.consensus.delete(f"/locks/{lock['resource_name']}")
                self.cache.delete(f"lock:{lock['resource_name']}")
                self._log_lock_event(lock['resource_name'], None, "EXPIRE", None, None)
            except Exception as e:
                logger.error(f"Failed to cleanup lock {lock['resource_name']}: {e}")
```

---

## Step 6: Fencing Tokens - Preventing Stale Operations

### The Problem

```
Timeline:
t=0:  Client A acquires lock, gets token=100
t=10: Client A pauses (GC, network hiccup)
t=30: Lock expires (TTL=30s), auto-released
t=31: Client B acquires lock, gets token=101
t=40: Client A resumes, tries to write with token=100 (stale!)

Without fencing: Client A's stale write could corrupt data! ❌
```

### The Solution: Fencing Tokens

```python
# Storage service (e.g., database, file system)
class FencedResourceStorage:
    def __init__(self):
        self.max_fencing_token = {}  # resource -> max seen token

    def write(self, resource: str, data: Any, fencing_token: int) -> bool:
        """
        Only accept writes with higher fencing tokens.
        """
        current_max = self.max_fencing_token.get(resource, 0)

        if fencing_token <= current_max:
            # Stale token, reject!
            raise StaleFencingTokenError(
                f"Token {fencing_token} <= current {current_max}"
            )

        # Accept write, update max token
        self._perform_write(resource, data)
        self.max_fencing_token[resource] = fencing_token
        return True

# Client usage
lock_response = lock_service.acquire_lock("payment:order:123", "client-A", 30000)
fencing_token = lock_response.fencing_token  # 100

try:
    storage.write("payment:order:123", payment_data, fencing_token)
except StaleFencingTokenError:
    # Lock was lost, abort operation!
    logger.error("Lost lock, aborting write")
    return
```

**Key Insight**: Fencing tokens provide **safety** even when locks expire prematurely or network partitions occur.

---

## Step 7: Optimizations & Trade-offs (15 min)

### 1. Consensus Layer Choice

**Option A: etcd (Raft consensus)**
```
Pros:
+ Battle-tested (Kubernetes uses it)
+ Simple mental model (leader + followers)
+ Good performance: 10K write QPS
+ Strong consistency (linearizability)
+ Watch API for notifications

Cons:
- Leader bottleneck for writes
- Cross-region latency (50-100ms)

Best for: Strong consistency requirements, same-DC deployment
```

**Option B: Redis with Redlock Algorithm**
```
Pros:
+ Very fast (< 1ms latency)
+ Simple to deploy
+ Familiar tool

Cons:
- NOT strongly consistent (AP, not CP)
- Redlock algorithm debated (see Martin Kleppmann's critique)
- Clock skew issues
- Not safe under all failure scenarios

Best for: Best-effort locks, non-critical sections, high performance
```

**Option C: ZooKeeper**
```
Pros:
+ Mature, proven at scale (Hadoop, Kafka use it)
+ Strong consistency
+ Ephemeral nodes (auto-cleanup)
+ Watch mechanism

Cons:
- Complex to operate
- Lower write throughput (~5K QPS)
- Older technology (less active development)

Best for: Legacy systems, Hadoop ecosystem integration
```

**Recommendation**: Use **etcd** for new systems (simplicity + performance), Redis for fast non-critical locks.

---

### 2. Handling Network Partitions

**Scenario**: Client acquires lock, then gets partitioned from consensus cluster.

```
┌─────────────┐                      ┌─────────────┐
│  Client A   │   PARTITION!         │   Cluster   │
│ (has lock)  │  ←××××××××××××→      │ (3/5 nodes) │
└─────────────┘                      └─────────────┘

What happens:
1. Client A cannot renew lock (heartbeat fails)
2. Lock expires after TTL (e.g., 30s)
3. Cluster auto-releases lock
4. Client B acquires lock
5. Client A eventually reconnects → discovers lock lost
```

**Safety Mechanisms**:
1. **TTL expiry**: Lock auto-released if not renewed
2. **Fencing tokens**: Client A's operations rejected (token too old)
3. **Client must check**: Before each operation, verify lock still held

```python
# Client-side safeguard
class LockClient:
    def __init__(self, lock_service):
        self.lock_service = lock_service
        self.owned_locks = {}  # resource -> fencing_token

    def do_critical_work(self, resource, work_fn):
        # Acquire lock
        resp = self.lock_service.acquire_lock(resource, self.client_id, 30000)
        if not resp.success:
            raise LockAcquisitionError()

        self.owned_locks[resource] = resp.fencing_token

        # Start heartbeat thread
        heartbeat_thread = threading.Thread(
            target=self._heartbeat_loop,
            args=(resource, 15000)  # Renew every 15s
        )
        heartbeat_thread.start()

        try:
            # Perform work with fencing token
            work_fn(fencing_token=resp.fencing_token)
        finally:
            # Always release
            heartbeat_thread.cancel()
            self.lock_service.release_lock(resource, self.client_id)
            del self.owned_locks[resource]

    def _heartbeat_loop(self, resource, interval_ms):
        while resource in self.owned_locks:
            time.sleep(interval_ms / 1000)
            try:
                resp = self.lock_service.renew_lock(resource, self.client_id, 30000)
                if not resp.success:
                    # Lost lock!
                    logger.error(f"Lost lock on {resource}, ABORT WORK!")
                    del self.owned_locks[resource]
                    raise LockLostError()
            except Exception as e:
                logger.error(f"Heartbeat failed: {e}")
                del self.owned_locks[resource]
                raise
```

---

### 3. Lock Granularity Design

**Coarse-Grained Locks** (Bad):
```python
# Lock entire orders table
lock("orders")  # Blocks ALL order operations! ❌
```

**Fine-Grained Locks** (Good):
```python
# Lock specific order
lock("orders:12345")  # Only blocks this order ✅
```

**Hierarchical Locks** (Advanced):
```python
# Intent lock + row lock
lock("orders:INTENT_WRITE")  # Shared
lock("orders:12345")          # Exclusive

# Allows concurrent reads on other orders
```

**Rule of Thumb**: Lock smallest resource needed, reduce contention.

---

### 4. Deadlock Prevention

**Problem**: Two clients each hold a lock and wait for the other's lock.

```
Client A: has lock("resource-1"), waits for lock("resource-2")
Client B: has lock("resource-2"), waits for lock("resource-1")
→ DEADLOCK! ❌
```

**Solutions**:

**A. Lock Ordering** (Prevent):
```python
# Always acquire locks in sorted order
resources = sorted(["user:123", "account:456"])
for resource in resources:
    acquire_lock(resource)
```

**B. Timeout** (Detect & Recover):
```python
# Max wait time
if not acquire_lock(resource, wait_timeout=5000):
    # Timeout, release held locks, retry
    release_all_locks()
    raise LockTimeoutError()
```

**C. TTL** (Automatic Recovery):
```python
# Locks expire automatically after TTL
# No lock held forever → no permanent deadlock
```

**D. Deadlock Detection** (Advanced):
```python
# Build wait-for graph, detect cycles
# If cycle found, abort youngest transaction
```

---

## Step 8: Advanced Considerations

### Leader Election Pattern

```python
# Use locks for leader election among service replicas
class LeaderElection:
    def __init__(self, lock_service, service_id):
        self.lock_service = lock_service
        self.service_id = service_id
        self.is_leader = False

    def run_election(self):
        """
        Try to become leader by acquiring lock.
        """
        while True:
            try:
                resp = self.lock_service.acquire_lock(
                    resource="service:scheduler:leader",
                    owner=self.service_id,
                    ttl_ms=30000,
                    wait=True  # Block until available
                )

                if resp.success:
                    self.is_leader = True
                    logger.info(f"{self.service_id} became leader")
                    self._run_as_leader()
                else:
                    self.is_leader = False
                    time.sleep(5)  # Retry
            except Exception as e:
                logger.error(f"Election failed: {e}")
                self.is_leader = False
                time.sleep(5)

    def _run_as_leader(self):
        """
        Execute leader-only logic while holding lock.
        """
        # Start heartbeat
        heartbeat_thread = threading.Thread(target=self._heartbeat)
        heartbeat_thread.start()

        try:
            while self.is_leader:
                # Do leader work (schedule jobs, coordinate workers, etc.)
                self._schedule_next_job()
                time.sleep(1)
        finally:
            heartbeat_thread.cancel()
            self.lock_service.release_lock("service:scheduler:leader", self.service_id)
```

---

### Monitoring & Observability

**Key Metrics to Track**:

```python
# Lock service metrics
metrics = {
    # Throughput
    "lock_acquire_total": Counter,
    "lock_acquire_success": Counter,
    "lock_acquire_failed": Counter,
    "lock_release_total": Counter,
    "lock_renew_total": Counter,

    # Latency
    "lock_acquire_latency_ms": Histogram,  # P50, P95, P99
    "consensus_commit_latency_ms": Histogram,

    # Lock state
    "active_locks": Gauge,
    "expired_locks_total": Counter,
    "lock_contention_rate": Gauge,  # Failed / Total

    # Lock hold duration
    "lock_hold_duration_ms": Histogram,

    # Consensus cluster
    "raft_leader_elections": Counter,
    "raft_log_entries": Counter,
    "raft_commit_latency_ms": Histogram,

    # Errors
    "consensus_errors_total": Counter,
    "stale_fencing_token_errors": Counter,
}

# Alerts
alerts = {
    "HighLockContention": "contention_rate > 20% for 5min",
    "SlowConsensus": "P99 consensus latency > 50ms",
    "FrequentLeaderElections": "leader_elections > 2 per hour",
    "HighExpiredLocks": "expired_locks > 10/min (leaky clients)",
}
```

**Distributed Tracing**:
```python
# Trace lock acquisition flow
@trace_span("lock.acquire")
def acquire_lock(resource, owner, ttl_ms):
    span.set_attribute("resource", resource)
    span.set_attribute("owner", owner)

    # Trace consensus call
    with trace_span("consensus.create"):
        result = consensus.create_if_not_exists(...)

    span.set_attribute("success", result.success)
    return result
```

---

### Security Considerations

1. **Authentication**:
   ```python
   # Verify client identity
   if not verify_api_key(request.api_key):
       raise UnauthorizedError()

   # Owner ID derived from authenticated identity
   owner_id = f"{service_name}:{pod_name}"
   ```

2. **Authorization**:
   ```python
   # Resource-based access control
   if not has_permission(user, resource, "LOCK"):
       raise ForbiddenError()
   ```

3. **Rate Limiting**:
   ```python
   # Per-client rate limits
   @rate_limit(max_requests=1000, window=60)  # 1K req/min
   def acquire_lock(request):
       ...
   ```

4. **Audit Logging**:
   ```python
   # Log all lock operations
   audit_log.info({
       "action": "LOCK_ACQUIRE",
       "resource": resource,
       "owner": owner,
       "success": True,
       "fencing_token": token,
       "timestamp": time.now(),
       "client_ip": request.ip
   })
   ```

---

## Complete System Design Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                     CLIENT APPLICATIONS                        │
│  Payment Service │ Inventory │ Scheduler │ Order Processing   │
└────────────────────────┬───────────────────────────────────────┘
                         │ gRPC (mTLS)
                         ▼
┌────────────────────────────────────────────────────────────────┐
│                    API GATEWAY LAYER                           │
│  • Authentication (JWT, API Keys)                              │
│  • Rate Limiting (1K req/min per client)                       │
│  • Request Validation                                          │
│  • Load Balancing (Consistent Hashing by resource)             │
└────────────────────────┬───────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    ┌─────────┐    ┌─────────┐   ┌─────────┐
    │ Lock    │    │ Lock    │   │ Lock    │
    │ Manager │    │ Manager │   │ Manager │  (Stateless)
    │  Pod 1  │    │  Pod 2  │   │  Pod 3  │
    └────┬────┘    └────┬────┘   └────┬────┘
         │              │              │
         └──────────────┼──────────────┘
                        ▼
┌────────────────────────────────────────────────────────────────┐
│                   CONSENSUS LAYER (Raft)                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐      │
│  │ Leader   │  │Follower  │  │Follower  │  │Follower  │ ... │
│  │  etcd-1  │  │  etcd-2  │  │  etcd-3  │  │  etcd-4  │      │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘  └────┬─────┘      │
│       │             │              │             │            │
│       └─────────────┴──────────────┴─────────────┘            │
│                    Quorum: 3/5 nodes                          │
└────────────────────────┬───────────────────────────────────────┘
                         │
          ┌──────────────┼──────────────┐
          ▼              ▼              ▼
    ┌──────────┐   ┌──────────┐  ┌──────────┐
    │PostgreSQL│   │  Redis   │  │  Kafka   │
    │ (History)│   │ (Cache)  │  │ (Events) │
    └──────────┘   └──────────┘  └──────────┘
          │              │              │
          ▼              ▼              ▼
    ┌──────────┐   ┌──────────┐  ┌──────────┐
    │Analytics │   │Fast Path │  │Monitoring│
    │Dashboard │   │Lookups   │  │ Alerts   │
    └──────────┘   └──────────┘  └──────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (concurrent locks, QPS)?
- Lock hold duration (seconds vs minutes)?
- Consistency requirements (strong vs eventual)?
- Failure tolerance needed (1 node vs 2 nodes)?
- Same datacenter or cross-region?
- Use cases (leader election, resource coordination, distributed cron)?

**Topics to Cover**:
- Fencing tokens (critical for safety!)
- Consensus algorithm (Raft vs Paxos)
- Failure scenarios (node crash, network partition)
- Deadlock prevention (TTL, ordering, timeouts)
- Heartbeat mechanism (renewal frequency)
- CAP theorem trade-offs (CP for locks)

**Common Follow-ups**:
- "How do you prevent split-brain?" → Quorum requirement (3/5)
- "What if the leader crashes?" → Raft election (<1s), state preserved
- "How do you handle clock skew?" → Logical clocks (fencing tokens), NTP
- "Can you use Redis for locks?" → Redlock algorithm, but not strongly consistent

---

## Key Takeaways

1. **Locks are CP, not AP**: Strong consistency > availability during partitions
2. **Fencing Tokens are Essential**: Prevent stale operations after lock loss
3. **TTL is Safety Net**: Auto-expiry prevents permanent deadlocks
4. **Consensus is Core**: Raft/Paxos provides linearizability
5. **Heartbeat for Liveness**: Clients must prove they're alive
6. **Monitoring is Critical**: Track contention, latency, expirations
7. **Same DC only**: Cross-region consensus too slow (<10ms requirement)

---

## Further Reading

- **Papers**:
  - "The Chubby Lock Service" (Google, 2006)
  - "In Search of an Understandable Consensus Algorithm" (Raft, 2014)
  - "Paxos Made Simple" (Lamport, 2001)
- **Critiques**:
  - "How to do distributed locking" (Martin Kleppmann) - Redlock critique
- **Production Systems**:
  - etcd documentation & best practices
  - Consul architecture guide
  - ZooKeeper recipes (leader election, barriers)
- **Books**:
  - "Designing Data-Intensive Applications" Ch 8-9 (Kleppmann)
