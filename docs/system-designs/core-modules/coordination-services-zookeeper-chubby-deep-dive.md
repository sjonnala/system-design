# Coordination Services: ZooKeeper and Chubby Deep Dive

## Contents

- [Coordination Services: ZooKeeper and Chubby Deep Dive](#coordination-services-zookeeper-and-chubby-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Coordination Problem](#the-coordination-problem)
    - [ZooKeeper Architecture](#zookeeper-architecture)
    - [ZooKeeper Data Model and API](#zookeeper-data-model-and-api)
    - [Google Chubby Architecture](#google-chubby-architecture)
    - [Coordination Patterns](#coordination-patterns)
    - [ZooKeeper vs Chubby Comparison](#zookeeper-vs-chubby-comparison)
    - [Production Deployment and Operations](#production-deployment-and-operations)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
    - [MIND MAP: COORDINATION SERVICES](#mind-map-coordination-services)
    - [EMOTIONAL ANCHORS](#emotional-anchors)

## Core Mental Model

```text
Coordination Service = Distributed System's Control Plane

Without Coordination:               With Coordination:
──────────────────────             ─────────────────────
┌────┐ ┌────┐ ┌────┐              ┌──────────────┐
│ S1 │ │ S2 │ │ S3 │              │  ZooKeeper   │
└────┘ └────┘ └────┘              │  (Consensus) │
   ↓      ↓      ↓                 └──────┬───────┘
 "I'm     "No,   "We're                   │
 leader!" I am!" both                 Watches &
          leader!" down!            Notifications
                                         │
          CHAOS!                    ┌────┼────┐
                                    ↓    ↓    ↓
                                  ┌────┬────┬────┐
                                  │ S1 │ S2 │ S3 │
                                  │Leader│Follower│Follower│
                                  └────┴────┴────┘

                                   COORDINATION!
```

**The Core Abstraction:**
```
Coordination Service =
    Small, Reliable Key-Value Store +
    Strong Consistency (Linearizable) +
    Watches (Notifications) +
    Ephemeral Nodes (Liveness Detection)

Purpose: Coordinate MANY distributed systems
Not: Store application data (use database for that)
```

---

## The Coordination Problem

🎓 **PROFESSOR**: What problems do distributed systems need coordination for?

### 1. Leader Election

```
Problem: Who is the leader?
────────────────────────────

Scenario: 5 servers need to elect one leader

Without Coordination:
  Server1: "I'm the leader!"
  Server2: "No, I am!"
  Server3: "Both of you are wrong, it's me!"
  → Split-brain: Multiple leaders
  → Data corruption, inconsistency

With ZooKeeper:
  - All servers create ephemeral node in ZooKeeper
  - Lowest sequence number = leader
  - If leader crashes, ZooKeeper detects (ephemeral node gone)
  - Next server becomes leader automatically
  → Exactly one leader at all times
```

### 2. Configuration Management

```
Problem: How to update configuration across 1000s of servers?
────────────────────────────────────────────────────────────

Without Coordination:
  - Restart each server to pick up new config (downtime!)
  - OR: Polling config file every minute (slow, inefficient)
  - OR: Push config via SSH (complex, error-prone)

With ZooKeeper:
  - Store config in ZooKeeper
  - Servers watch config node
  - Config changes → ZooKeeper notifies all watchers instantly
  - Servers reload config (no restart needed)
```

### 3. Distributed Locks

```
Problem: Only one server should process task at a time
─────────────────────────────────────────────────────

Without Coordination:
  - Database lock? (single point of failure)
  - Application-level flag? (race conditions)

With ZooKeeper:
  - Create ephemeral node = acquire lock
  - If lock holder crashes, node deleted automatically
  - Next server acquires lock
  → Automatic deadlock prevention
```

### 4. Group Membership

```
Problem: Which servers are alive in the cluster?
───────────────────────────────────────────────

Without Coordination:
  - Gossip protocol (eventually consistent)
  - Health checks (false positives)

With ZooKeeper:
  - Each server creates ephemeral node
  - Watch /cluster/members directory
  - Server crashes → ephemeral node deleted
  - ZooKeeper notifies all watchers
  → Strong consistency on membership
```

🏗️ **ARCHITECT**: Real-world example - Kafka:

```python
class KafkaWithoutZooKeeper:
    """
    Before KafkaRaft: Kafka relied heavily on ZooKeeper
    """

    def broker_startup(self):
        """
        Kafka broker registering with ZooKeeper
        """
        # 1. Register broker as alive
        zk.create(
            "/brokers/ids/1",  # Broker ID 1
            data={"host": "kafka1.example.com", "port": 9092},
            ephemeral=True  # Auto-deleted if broker crashes
        )

        # 2. Watch for controller election
        zk.watch("/controller", self.on_controller_change)

        # 3. Watch for topic changes
        zk.watch("/brokers/topics", self.on_topic_change)

    def elect_controller(self):
        """
        One broker becomes controller (leader)
        """
        try:
            # Try to create controller node
            zk.create(
                "/controller",
                data={"broker_id": self.broker_id},
                ephemeral=True
            )
            # Success! I'm the controller
            self.is_controller = True
        except NodeExistsException:
            # Someone else is controller
            self.is_controller = False

    """
    ZooKeeper tracks:
    - Which brokers are alive
    - Which broker is controller
    - Partition assignments
    - Topic configurations
    - Consumer group offsets (older versions)
    """
```

---

## ZooKeeper Architecture

🎓 **PROFESSOR**: ZooKeeper is a distributed system ITSELF, using Paxos-variant (ZAB) for consensus:

### High-Level Architecture

```
ZooKeeper Ensemble (3-5 servers for fault tolerance)
─────────────────────────────────────────────────────

                    ┌─────────────┐
                    │   Leader    │
                    │  (Handles   │
                    │   writes)   │
                    └──────┬──────┘
                           │
              Write-Ahead Log (Atomic Broadcast)
                           │
         ┌─────────────────┼─────────────────┐
         ↓                 ↓                  ↓
    ┌────────┐        ┌────────┐        ┌────────┐
    │Follower│        │Follower│        │Observer│
    │ (Vote) │        │ (Vote) │        │(No Vote)│
    └────────┘        └────────┘        └────────┘
         ↑                 ↑                  ↑
         │                 │                  │
    Serve Reads       Serve Reads        Serve Reads

Write Path:
  1. Client → Any ZK server
  2. Server → Forward to leader
  3. Leader → Propose to followers
  4. Followers → ACK if accepted
  5. Leader → Commit when majority ACKs
  6. All servers → Apply to state machine

Read Path:
  1. Client → Any ZK server
  2. Server → Return from local state
  3. (May be slightly stale!)
```

### ZAB (ZooKeeper Atomic Broadcast) Protocol

```java
/**
 * ZAB: Paxos-like consensus protocol
 */
public class ZABProtocol {

    /**
     * Phase 1: Leader Election
     */
    public void leaderElection() {
        /**
         * Each server votes for the server with:
         * 1. Highest epoch (logical clock)
         * 2. If tied, highest transaction ID (most up-to-date)
         *
         * Majority wins → New leader elected
         */

        Election election = new Election();

        // Server broadcasts vote
        Vote myVote = new Vote(
            this.serverId,
            this.currentEpoch,
            this.lastZxid  // Transaction ID
        );

        election.broadcast(myVote);

        // Collect votes from other servers
        Map<ServerId, Vote> votes = election.collectVotes();

        // Determine winner (majority consensus)
        Vote winner = election.countVotes(votes);

        if (winner.serverId == this.serverId) {
            this.becomeLeader();
        } else {
            this.becomeFollower(winner.serverId);
        }
    }

    /**
     * Phase 2: Discovery (Sync with leader)
     */
    public void discovery() {
        /**
         * Followers sync with new leader
         * Ensures all committed transactions are present
         */

        if (this.isFollower()) {
            // Get transaction log from leader
            long myLastZxid = this.getLastZxid();

            TransactionLog missingTxns =
                leader.getTransactionsSince(myLastZxid);

            // Replay transactions
            for (Transaction txn : missingTxns) {
                this.applyTransaction(txn);
            }
        }
    }

    /**
     * Phase 3: Broadcast (Normal operation)
     */
    public void broadcast(Transaction txn) {
        /**
         * Leader broadcasts proposals to followers
         * Commit when majority ACKs
         */

        if (this.isLeader()) {
            // 1. Assign transaction ID (zxid)
            long zxid = this.nextZxid();
            txn.setZxid(zxid);

            // 2. Write to leader's log
            this.writeToLog(txn);

            // 3. Broadcast proposal to followers
            for (Follower follower : this.followers) {
                follower.propose(txn);
            }

            // 4. Wait for majority ACKs
            int acks = 1;  // Leader's ACK
            while (acks < this.quorumSize()) {
                acks += this.waitForAck(txn.zxid);
            }

            // 5. Commit transaction
            this.commit(txn);

            // 6. Notify followers to commit
            for (Follower follower : this.followers) {
                follower.commit(txn.zxid);
            }
        }
    }
}
```

### Guarantees

```
ZooKeeper provides:
──────────────────

1. Linearizable Writes
   - All writes go through leader
   - Ordered by zxid (transaction ID)
   - Appears atomic to all clients

2. FIFO Client Order
   - Client's operations executed in order sent
   - Client sees its own writes

3. Causal Ordering
   - If write A happens-before write B, all servers see A before B

4. Sequential Consistency
   - All clients see operations in same order
   - (But reads may be stale - from local server)

Trade-off:
  ✓ Strong consistency for writes
  ✗ Reads may be stale (read from follower)
  → Use sync() to force fresh read
```

---

## ZooKeeper Data Model and API

🏗️ **ARCHITECT**: ZooKeeper's simple but powerful abstraction:

### Data Model: Hierarchical Namespace

```
ZooKeeper = File System + Watch Notifications
────────────────────────────────────────────

/
├── /app
│   ├── /config
│   │   └── database_url = "mysql://..."
│   ├── /leader         (ephemeral)
│   │   └── server-id = "server-1"
│   └── /workers
│       ├── /worker-1   (ephemeral, sequential)
│       ├── /worker-2   (ephemeral, sequential)
│       └── /worker-3   (ephemeral, sequential)
├── /kafka
│   ├── /brokers
│   │   └── /ids
│   │       ├── /0      (ephemeral)
│   │       ├── /1      (ephemeral)
│   │       └── /2      (ephemeral)
│   └── /controller     (ephemeral)
└── /locks
    └── /my-lock
        ├── /lock-0000000001  (ephemeral, sequential)
        ├── /lock-0000000002  (ephemeral, sequential)
        └── /lock-0000000003  (ephemeral, sequential)

Node Types:
  Persistent: Exists until explicitly deleted
  Ephemeral:  Deleted when client session ends
  Sequential: Auto-appended with monotonic counter
```

### Core API

```python
from kazoo.client import KazooClient

class ZooKeeperAPI:
    """
    ZooKeeper API examples
    """

    def __init__(self):
        self.zk = KazooClient(hosts='zk1:2181,zk2:2181,zk3:2181')
        self.zk.start()

    # 1. CREATE
    def create_node(self):
        """Create a node"""

        # Persistent node
        self.zk.create(
            "/app/config/db_url",
            value=b"mysql://db.example.com",
            makepath=True  # Create parent dirs
        )

        # Ephemeral node (auto-deleted when session ends)
        self.zk.create(
            "/app/workers/worker-1",
            value=b"192.168.1.10",
            ephemeral=True
        )

        # Sequential node (auto-appended with sequence)
        path = self.zk.create(
            "/locks/my-lock/lock-",
            value=b"",
            ephemeral=True,
            sequence=True
        )
        # Returns: "/locks/my-lock/lock-0000000001"

    # 2. READ
    def read_node(self):
        """Read a node"""

        data, stat = self.zk.get("/app/config/db_url")
        print(f"Data: {data.decode()}")
        print(f"Version: {stat.version}")
        print(f"Created: {stat.ctime}")

    # 3. UPDATE
    def update_node(self):
        """Update a node (optimistic concurrency)"""

        # Read current version
        data, stat = self.zk.get("/app/config/db_url")

        # Update with version check
        try:
            self.zk.set(
                "/app/config/db_url",
                b"postgres://new-db.example.com",
                version=stat.version  # Only update if version matches
            )
        except BadVersionError:
            # Someone else modified it → retry
            print("Concurrent modification detected!")

    # 4. DELETE
    def delete_node(self):
        """Delete a node"""

        self.zk.delete("/app/config/db_url", version=-1)  # Delete any version

    # 5. WATCH (Most Powerful Feature!)
    def watch_node(self):
        """Watch for changes"""

        @self.zk.DataWatch("/app/config/db_url")
        def config_changed(data, stat):
            if data is None:
                print("Config deleted!")
            else:
                print(f"New config: {data.decode()}")
                # Reload configuration
                self.reload_config(data)

        # Watch fires:
        # - When node data changes
        # - When node is deleted
        # - ONE TIME only (must re-watch)

    # 6. WATCH CHILDREN
    def watch_children(self):
        """Watch for child node changes"""

        @self.zk.ChildrenWatch("/app/workers")
        def workers_changed(children):
            print(f"Current workers: {children}")
            # Update load balancer pool
            self.update_worker_pool(children)

        # Fires when:
        # - Child added
        # - Child removed

    # 7. EXISTS
    def check_exists(self):
        """Check if node exists"""

        if self.zk.exists("/app/leader"):
            print("Leader exists")
        else:
            print("No leader elected")

    # 8. GET CHILDREN
    def get_children(self):
        """List children"""

        children = self.zk.get_children("/app/workers")
        print(f"Workers: {children}")
```

### Advanced Patterns

```java
/**
 * Implementing common coordination patterns
 */
public class ZooKeeperPatterns {

    /**
     * Pattern 1: Leader Election
     */
    public class LeaderElection {

        public void electLeader() throws Exception {
            // 1. Create ephemeral sequential node
            String path = zk.create(
                "/election/candidate-",
                myServerId.getBytes(),
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL
            );

            // 2. Get all candidates
            List<String> candidates = zk.getChildren("/election", false);
            Collections.sort(candidates);

            // 3. Am I the leader?
            String smallest = candidates.get(0);
            if (path.endsWith(smallest)) {
                // I'm the leader!
                this.isLeader = true;
                this.becomeLeader();
            } else {
                // Watch the candidate just before me
                String watchPath = findPredecessor(candidates, path);

                zk.exists("/election/" + watchPath, event -> {
                    // Predecessor died → re-elect
                    electLeader();
                });
            }
        }

        /**
         * Why this works:
         * - Only smallest sequence number is leader
         * - Each node watches its predecessor
         * - Prevents "herd effect" (all nodes waking up)
         * - Automatic failover when leader dies
         */
    }

    /**
     * Pattern 2: Distributed Lock
     */
    public class DistributedLock {

        public void acquireLock(String lockName) throws Exception {
            // 1. Create ephemeral sequential node
            String myPath = zk.create(
                "/locks/" + lockName + "/lock-",
                new byte[0],
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL
            );

            while (true) {
                // 2. Get all lock candidates
                List<String> locks = zk.getChildren("/locks/" + lockName, false);
                Collections.sort(locks);

                // 3. Am I the first?
                if (myPath.endsWith(locks.get(0))) {
                    // Lock acquired!
                    return;
                }

                // 4. Watch the lock just before me
                String watchPath = findPredecessor(locks, myPath);

                CountDownLatch latch = new CountDownLatch(1);
                zk.exists("/locks/" + lockName + "/" + watchPath, event -> {
                    latch.countDown();
                });

                // Wait for predecessor to release
                latch.await();
            }
        }

        public void releaseLock(String lockName) {
            // Delete my node → next in line acquires lock
            zk.delete(myPath, -1);
        }

        /**
         * Properties:
         * ✓ Exactly one lock holder at a time
         * ✓ FIFO ordering (fairness)
         * ✓ Automatic deadlock prevention (ephemeral nodes)
         * ✓ No polling (event-driven)
         */
    }

    /**
     * Pattern 3: Barrier (Coordination Point)
     */
    public class Barrier {

        private final int size;  // Number of participants

        public void enter(String barrierId, String participantId) throws Exception {
            // 1. Create node for this participant
            zk.create(
                "/barriers/" + barrierId + "/" + participantId,
                new byte[0],
                ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL
            );

            // 2. Wait for all participants
            while (true) {
                List<String> participants =
                    zk.getChildren("/barriers/" + barrierId, false);

                if (participants.size() >= this.size) {
                    // All participants ready!
                    return;
                }

                // Wait for more participants
                CountDownLatch latch = new CountDownLatch(1);
                zk.getChildren("/barriers/" + barrierId, event -> {
                    latch.countDown();
                });

                latch.await();
            }
        }

        /**
         * Use case: Start distributed computation only when
         *          all workers are ready
         */
    }

    /**
     * Pattern 4: Configuration Management
     */
    public class ConfigurationManager {

        private volatile Properties config;

        public void watchConfig(String configPath) throws Exception {
            // Initial load
            loadConfig(configPath);

            // Watch for changes
            zk.getData(configPath, event -> {
                if (event.getType() == EventType.NodeDataChanged) {
                    // Config changed → reload
                    loadConfig(configPath);
                }
            }, null);
        }

        private void loadConfig(String path) throws Exception {
            byte[] data = zk.getData(path, false, null);

            Properties newConfig = new Properties();
            newConfig.load(new ByteArrayInputStream(data));

            // Atomic swap
            this.config = newConfig;

            System.out.println("Configuration reloaded!");
        }

        /**
         * Benefits:
         * ✓ Live configuration updates (no restart)
         * ✓ All servers get update simultaneously
         * ✓ Version tracking (stat.version)
         */
    }
}
```

---

## Google Chubby Architecture

🎓 **PROFESSOR**: Chubby is Google's version, with different design choices:

### Chubby vs ZooKeeper Philosophy

```
Chubby (Google):                ZooKeeper (Apache):
───────────────                 ──────────────────

Design: Coarse-grained locks    Design: Fine-grained coordination
Focus: Long-held locks          Focus: Low-latency operations
Session: Hours to days          Session: Seconds to minutes
Use: Leader election,           Use: General coordination,
     master selection                leader election, config

Key Difference: Lock focus vs. General coordination
```

### Chubby Architecture

```
Chubby Cell (5 replicas)
────────────────────────

                  ┌─────────────┐
                  │   Master    │
                  │  (Elected   │
                  │  via Paxos) │
                  └──────┬──────┘
                         │
                    Paxos Log
                         │
         ┌───────────────┼───────────────┐
         ↓               ↓               ↓
    ┌────────┐      ┌────────┐      ┌────────┐
    │Replica1│      │Replica2│      │Replica3│
    │ (Paxos │      │ (Paxos │      │ (Paxos │
    │  voter)│      │  voter)│      │  voter)│
    └────────┘      └────────┘      └────────┘

Clients:
  ↓
┌─────────────────────────────────────┐
│  Chubby Library (in app process)   │
│  - Caching                          │
│  - Session keep-alive               │
│  - Event notifications              │
└─────────────────────────────────────┘
```

### Chubby Features

```python
class ChubbyFeatures:
    """
    Unique features of Chubby
    """

    def advisory_locks(self):
        """
        1. Advisory Locks (not enforced)
        """
        # Lock doesn't prevent reads/writes
        # Application must check lock before accessing resource

        # Acquire lock
        lock = chubby.acquire("/ls/cell/app/master-lock")

        if lock.is_held():
            # I'm the master
            # But nothing prevents others from reading/writing!
            # Application must cooperate
            pass

    def sequencer(self):
        """
        2. Sequencer: Proof of lock ownership
        """
        # Problem: Lock holder crashes, new holder elected
        #          Old holder recovers, still thinks it has lock
        #          Both send commands to resource!

        # Solution: Sequencer
        lock = chubby.acquire("/ls/cell/app/master-lock")
        sequencer = lock.get_sequencer()
        # sequencer = opaque byte string with:
        #   - Lock name
        #   - Lock generation number
        #   - Signature

        # Send sequencer with every command
        resource.execute(command, sequencer=sequencer)

        # Resource verifies:
        # - Sequencer is valid (signature check)
        # - Sequencer is current (generation check)
        # → Fencing: Old lock holder's commands rejected!

    def caching(self):
        """
        3. Client-side Caching
        """
        # Chubby aggressively caches data
        # Master invalidates cache when data changes

        # Read config (cached)
        config = chubby.read("/ls/cell/app/config")
        # Served from local cache (no RPC)

        # Another client writes config
        # → Master sends KeepAlive response with invalidation
        # → Client's cache invalidated
        # → Next read goes to master

    def session_leases(self):
        """
        4. Session Leases (long-lived)
        """
        # ZooKeeper: ~10s session timeout
        # Chubby: ~10s-60s default, can extend to hours

        # Why?
        # - GC pauses: JVM pause shouldn't lose lock
        # - Network blips: Temporary partition tolerated
        # - Debugging: Can attach debugger without losing lock

    def events(self):
        """
        5. Event Notifications
        """
        # Similar to ZooKeeper watches

        chubby.subscribe("/ls/cell/app/config", callback=on_config_change)

        def on_config_change(event):
            # Events:
            # - File contents modified
            # - Child node added/removed
            # - Master failover
            # - Lock acquired/lost
            # - Handle invalidate
            pass
```

### Chubby Use Cases at Google

```java
/**
 * How Google services use Chubby
 */
public class ChubbyUseCases {

    /**
     * 1. GFS (Google File System) Master Election
     */
    public class GFSMasterElection {

        public void electMaster() {
            // Try to acquire master lock
            ChubbyLock lock = chubby.tryAcquire("/ls/cell/gfs/master");

            if (lock != null) {
                // I'm the GFS master!
                this.isMaster = true;

                // Store master location for clients
                chubby.write(
                    "/ls/cell/gfs/master-location",
                    this.hostname.getBytes()
                );

                // Hold lock for entire master tenure (hours/days)
            }
        }

        /**
         * GFS clients:
         * - Read /ls/cell/gfs/master-location
         * - Connect to master
         * - Cache master location (Chubby caches this)
         */
    }

    /**
     * 2. Bigtable Master Election
     */
    public class BigtableCluster {

        public void startup() {
            // Each tablet server registers with Chubby
            chubby.create(
                "/ls/cell/bigtable/tablets/" + this.serverId,
                ephemeral=true
            );

            // Master watches for tablet servers
            chubby.getChildren(
                "/ls/cell/bigtable/tablets",
                watcher=this::onTabletServersChanged
            );
        }

        /**
         * Master election:
         * - Acquire /ls/cell/bigtable/master lock
         * - If acquired → become master
         * - Master assigns tablets to servers
         * - Master monitors server liveness via Chubby
         */
    }

    /**
     * 3. Name Service (DNS-like)
     */
    public class ChubbyNameService {

        public String resolve(String serviceName) {
            // Read service location from Chubby
            byte[] data = chubby.read(
                "/ls/cell/services/" + serviceName
            );

            // Data cached aggressively
            // → Scales to millions of clients
            // → Master only invalidates on change

            return new String(data);
        }

        /**
         * Advantages over DNS:
         * ✓ Strong consistency
         * ✓ Immediate invalidation
         * ✓ Integrated with lock service
         */
    }

    /**
     * 4. Configuration Distribution
     */
    public class ConfigDistribution {

        public void distributeConfig() {
            // Write config to Chubby
            chubby.write(
                "/ls/cell/app/config",
                configJson.getBytes()
            );

            // All servers watching → notified instantly
            // All servers reload config simultaneously
            // No need to restart!
        }

        /**
         * 10,000+ servers can watch same file
         * Chubby master sends invalidations
         * Servers re-read (from cache or master)
         */
    }
}
```

---

## Coordination Patterns

🏗️ **ARCHITECT**: Implementing distributed patterns with ZooKeeper:

### Pattern: Service Discovery

```python
class ServiceDiscovery:
    """
    Implement service discovery with ZooKeeper
    """

    def __init__(self, zk):
        self.zk = zk

    def register_service(self, service_name, host, port):
        """
        Register service instance
        """
        # Create service node if doesn't exist
        self.zk.ensure_path(f"/services/{service_name}")

        # Register this instance (ephemeral)
        instance_data = json.dumps({
            "host": host,
            "port": port,
            "timestamp": time.time()
        })

        self.zk.create(
            f"/services/{service_name}/instance-",
            value=instance_data.encode(),
            ephemeral=True,
            sequence=True
        )

        # When service crashes → ephemeral node deleted
        # → Clients notified automatically

    def discover_services(self, service_name):
        """
        Discover all instances of a service
        """
        @self.zk.ChildrenWatch(f"/services/{service_name}")
        def watch_instances(children):
            instances = []
            for child in children:
                data, _ = self.zk.get(f"/services/{service_name}/{child}")
                instance = json.loads(data.decode())
                instances.append(instance)

            # Update load balancer
            self.update_load_balancer(instances)

        # Returns current instances
        # AND watches for changes!
```

### Pattern: Distributed Queue

```java
/**
 * Implement work queue with ZooKeeper
 */
public class DistributedQueue {

    private final String queuePath;

    public void enqueue(byte[] data) throws Exception {
        // Create sequential node
        zk.create(
            queuePath + "/task-",
            data,
            ZooDefs.Ids.OPEN_ACL_UNSAFE,
            CreateMode.PERSISTENT_SEQUENTIAL
        );
    }

    public byte[] dequeue() throws Exception {
        while (true) {
            // Get all tasks
            List<String> tasks = zk.getChildren(queuePath, false);

            if (tasks.isEmpty()) {
                // Queue empty → wait
                CountDownLatch latch = new CountDownLatch(1);
                zk.getChildren(queuePath, event -> latch.countDown());
                latch.await();
                continue;
            }

            // Sort tasks (oldest first)
            Collections.sort(tasks);

            // Try to claim first task
            String taskPath = queuePath + "/" + tasks.get(0);

            try {
                byte[] data = zk.getData(taskPath, false, null);
                zk.delete(taskPath, -1);
                return data;
            } catch (NoNodeException e) {
                // Someone else got it → try next
                continue;
            }
        }
    }

    /**
     * Use case: Distributed task processing
     * - Multiple workers dequeue tasks
     * - FIFO ordering guaranteed
     * - No duplicate processing
     */
}
```

### Pattern: Read-Write Lock

```python
class ReadWriteLock:
    """
    Implement read-write lock with ZooKeeper
    """

    def acquire_read_lock(self, lock_name):
        """
        Multiple readers allowed, no writers
        """
        # Create read lock node
        my_path = self.zk.create(
            f"/locks/{lock_name}/read-",
            ephemeral=True,
            sequence=True
        )

        while True:
            children = self.zk.get_children(f"/locks/{lock_name}")

            # Check if any write locks before me
            write_locks = [c for c in children if c.startswith("write-")]

            if not write_locks:
                # No writers → read lock acquired!
                return

            # Wait for all write locks to finish
            for write_lock in write_locks:
                if write_lock < my_path:
                    # Wait for this write lock
                    latch = threading.Event()
                    self.zk.exists(
                        f"/locks/{lock_name}/{write_lock}",
                        watch=lambda e: latch.set()
                    )
                    latch.wait()

    def acquire_write_lock(self, lock_name):
        """
        Exclusive: No readers, no other writers
        """
        # Create write lock node
        my_path = self.zk.create(
            f"/locks/{lock_name}/write-",
            ephemeral=True,
            sequence=True
        )

        while True:
            children = self.zk.get_children(f"/locks/{lock_name}")
            children.sort()

            # Am I first?
            if children[0] == my_path.split("/")[-1]:
                # Write lock acquired!
                return

            # Wait for predecessor
            my_index = children.index(my_path.split("/")[-1])
            predecessor = children[my_index - 1]

            latch = threading.Event()
            self.zk.exists(
                f"/locks/{lock_name}/{predecessor}",
                watch=lambda e: latch.set()
            )
            latch.wait()
```

---

## ZooKeeper vs Chubby Comparison

🎓 **PROFESSOR**: Design philosophy comparison:

```
┌─────────────────────────────────────────────────────────────┐
│                                                              │
│  Dimension       ZooKeeper              Chubby               │
│  ──────────────────────────────────────────────────────────  │
│  Consensus       ZAB (Paxos-like)       Paxos               │
│  Focus           General coordination    Coarse-grained locks│
│  Session         Short (seconds)         Long (hours)        │
│  API             Watch-based             Lock-based          │
│  Caching         Client-driven           Server-driven       │
│  Sequencer       No                      Yes (fencing)       │
│  Open Source     Yes (Apache)            No (Google internal)│
│  Scale           1000s of clients        100,000s of clients │
│  Latency         ~10ms                   ~10ms               │
│  Typical Use     Kafka, HBase,           GFS, Bigtable,      │
│                  Hadoop                  Borg                │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

### When to Choose What

```java
public class CoordinationServiceChoice {

    /**
     * Choose ZooKeeper when:
     */
    public void chooseZooKeeper() {
        /**
         * ✓ Need open-source solution
         * ✓ Fine-grained coordination (frequent updates)
         * ✓ Watch-based notifications important
         * ✓ Integrating with Kafka, HBase, etc.
         * ✓ Moderate scale (< 10,000 clients)
         */
    }

    /**
     * Choose Chubby when:
     */
    public void chooseChubby() {
        /**
         * ✓ Running on Google Cloud
         * ✓ Coarse-grained locks (long-held)
         * ✓ Need fencing tokens (sequencers)
         * ✓ Massive scale (100,000+ clients)
         * ✓ Integration with GCP services
         *
         * Note: Chubby not available outside Google
         */
    }

    /**
     * Modern alternatives:
     */
    public void modernAlternatives() {
        /**
         * etcd (from CoreOS/Kubernetes):
         * - Similar to ZooKeeper
         * - Uses Raft consensus (simpler than ZAB)
         * - gRPC API (vs ZooKeeper's custom protocol)
         * - Better performance
         *
         * Consul (from HashiCorp):
         * - Service discovery focus
         * - Built-in health checking
         * - Multi-datacenter support
         * - DNS interface
         */
    }
}
```

---

## Production Deployment and Operations

🏗️ **ARCHITECT**: Running ZooKeeper in production:

### Cluster Sizing

```
ZooKeeper Ensemble Size:
───────────────────────

3 servers:  Tolerates 1 failure     (minimum production setup)
5 servers:  Tolerates 2 failures    (recommended)
7 servers:  Tolerates 3 failures    (large deployments)

Formula: N servers tolerates (N-1)/2 failures

⚠ Always use ODD numbers!
   - 4 servers: Tolerates 1 failure (same as 3!)
   - 6 servers: Tolerates 2 failures (same as 5!)
   → Even numbers waste resources

⚠ Don't go above 7-9 servers
   - More servers = slower writes (more coordination)
   - Use observers for read scaling instead
```

### Configuration Best Practices

```properties
# zookeeper.properties

# Data directory (SSD recommended)
dataDir=/var/lib/zookeeper

# Transaction log directory (SEPARATE disk!)
dataLogDir=/var/lib/zookeeper-logs

# Client port
clientPort=2181

# Quorum ports
server.1=zk1.example.com:2888:3888
server.2=zk2.example.com:2888:3888
server.3=zk3.example.com:2888:3888

# Tuning
tickTime=2000                    # 2 seconds
initLimit=10                     # 20 seconds for initial sync
syncLimit=5                      # 10 seconds for sync

# Session timeout (client configurable)
minSessionTimeout=4000           # 4 seconds minimum
maxSessionTimeout=40000          # 40 seconds maximum

# Snapshots
autopurge.snapRetainCount=10     # Keep 10 snapshots
autopurge.purgeInterval=24       # Purge every 24 hours

# Performance
maxClientCnxns=60                # Max connections per client IP
```

### Monitoring

```python
class ZooKeeperMonitoring:
    """
    Essential metrics to monitor
    """

    def monitor_latency(self):
        """
        1. Latency metrics
        """
        # Send "ruok" command to ZK port
        response = nc("zk1.example.com", 2181, "ruok")
        # Response: "imok" if healthy

        # Four-letter words (monitoring commands):
        commands = {
            "ruok": "Are you OK?",
            "stat": "Server statistics",
            "srvr": "Server info",
            "mntr": "Monitoring data (key=value format)",
            "cons": "Connection info",
        }

        # Parse metrics
        mntr = nc("zk1.example.com", 2181, "mntr")
        """
        zk_avg_latency 5
        zk_max_latency 100
        zk_min_latency 0
        zk_packets_received 12345
        zk_packets_sent 12340
        zk_num_alive_connections 10
        zk_outstanding_requests 0
        zk_followers 2
        zk_synced_followers 2
        zk_pending_syncs 0
        """

    def alert_conditions(self):
        """
        2. Alert on these conditions
        """
        alerts = {
            "avg_latency > 100ms": "CRITICAL - Slow performance",
            "outstanding_requests > 100": "WARNING - Request queue growing",
            "synced_followers < total_followers": "CRITICAL - Follower lag",
            "zk_open_file_descriptor_count > 1000": "WARNING - Too many files",
            "jvm_memory_heap_usage > 80%": "CRITICAL - Memory pressure",
        }

    def health_check(self):
        """
        3. Health check script
        """
        # Check 1: Is server responding?
        if not self.is_responding():
            return "UNHEALTHY"

        # Check 2: Is server in quorum?
        if not self.in_quorum():
            return "UNHEALTHY"

        # Check 3: Is replication caught up?
        if not self.replication_caught_up():
            return "DEGRADED"

        # Check 4: Are there too many pending requests?
        if self.outstanding_requests() > 100:
            return "DEGRADED"

        return "HEALTHY"
```

### Common Issues and Solutions

```java
/**
 * Troubleshooting ZooKeeper
 */
public class ZooKeeperTroubleshooting {

    /**
     * Issue 1: Split-brain scenario
     */
    public void splitBrain() {
        /**
         * Symptom: Two leaders elected
         *
         * Cause: Network partition
         *
         * Example:
         *   3-server cluster: [A, B, C]
         *   Network split: A | B, C
         *
         *   A thinks: I'm alone, no quorum → OBSERVING mode
         *   B, C: We have majority → elect new leader
         *
         *   ✓ Safe! A won't process writes (no quorum)
         *
         * Prevention:
         *   - Use 5 servers (tolerates 2 failures)
         *   - Monitor quorum status
         */
    }

    /**
     * Issue 2: Session expiration
     */
    public void sessionExpiration() {
        /**
         * Symptom: Client sessions keep expiring
         *
         * Causes:
         *   1. GC pauses in client JVM
         *   2. Network latency spikes
         *   3. ZooKeeper overloaded
         *
         * Solutions:
         *   1. Increase sessionTimeout (default 40s)
         *   2. Tune client JVM GC (use G1GC)
         *   3. Add more ZooKeeper servers/observers
         *   4. Reduce watch count per client
         */
    }

    /**
     * Issue 3: Disk space exhaustion
     */
    public void diskSpace() {
        /**
         * Symptom: ZooKeeper stops accepting writes
         *
         * Cause: Transaction logs filling disk
         *
         * Solution:
         *   1. Enable autopurge:
         *      autopurge.snapRetainCount=10
         *      autopurge.purgeInterval=24
         *
         *   2. Separate dataLogDir from dataDir
         *      (use different disks)
         *
         *   3. Monitor disk usage
         */
    }

    /**
     * Issue 4: Too many watches
     */
    public void tooManyWatches() {
        /**
         * Symptom: High memory usage, slow performance
         *
         * Cause: Clients setting millions of watches
         *
         * Solution:
         *   1. Use fewer, broader watches
         *      Bad:  Watch 1 million individual nodes
         *      Good: Watch parent node, track children
         *
         *   2. Batch watch updates
         *
         *   3. Consider using observer nodes for reads
         */
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### When to Use Coordination Service

```
Decision Framework:
──────────────────

Q: Does your system need distributed coordination?

Examples of YES:
  ✓ Leader election (master-slave architecture)
  ✓ Distributed locking (exclusive resource access)
  ✓ Configuration management (live updates)
  ✓ Service discovery (dynamic cluster membership)
  ✓ Barrier synchronization (wait for all workers)

Examples of NO:
  ✗ Simple load balancing (use DNS or LB)
  ✗ Storing application data (use database)
  ✗ Message passing (use message queue)
  ✗ Metrics collection (use time-series DB)
```

### Designing with ZooKeeper

```
Interview Script:
────────────────

1. Identify Coordination Needs
   "For this distributed cache cluster, we need:
    - Leader election for coordinator role
    - Node liveness tracking
    - Configuration distribution"

2. Choose Ensemble Size
   "We'll use 5 ZooKeeper servers to tolerate 2 failures.
    Servers in different availability zones."

3. Design ZNode Hierarchy
   "/app
    ├── /config       (configuration)
    ├── /leader       (ephemeral, for election)
    └── /nodes        (ephemeral, for membership)
        ├── /node-1
        ├── /node-2
        └── /node-3"

4. Implement Patterns
   "For leader election, we'll use ephemeral sequential nodes.
    Each node creates /leader/candidate-, watches predecessor.
    Lowest sequence number is leader."

5. Handle Failure Modes
   "If leader crashes:
    - Ephemeral node auto-deleted
    - Next candidate notified via watch
    - Automatic failover in < 10 seconds"

6. Monitor and Alert
   "Track metrics:
    - Replication lag between leader/followers
    - Session expiration rate
    - Request latency (alert if > 100ms)"
```

---

## 🧠 MIND MAP: COORDINATION SERVICES

```
    COORDINATION SERVICES
            │
    ┌───────┴────────┐
    ↓                ↓
ZooKeeper        Chubby
    │                │
┌───┼───┐        ┌───┼───┐
↓   ↓   ↓        ↓   ↓   ↓
ZAB API Patterns Paxos Lock Seq
│   │   │        │   │   │
Leader Watch  Election  Master Advisory Fencing
Election Config  Lock    Election  Lock  Token
```

---

## 💡 EMOTIONAL ANCHORS

### 1. **ZooKeeper = Orchestra Conductor 🎼**
- Musicians (servers) need coordination
- Conductor (ZooKeeper) ensures synchronization
- All musicians follow conductor's lead
- If conductor sick, orchestra elects new one

### 2. **Leader Election = Class President 🗳️**
- Students vote for class president
- Winner leads class activities
- If president leaves school, new election
- Only one president at a time

### 3. **Ephemeral Node = Heartbeat 💓**
- Patient connected to heart monitor
- Beep = still alive (session active)
- Flatline = patient died (node deleted)
- Automatic detection, no manual check

### 4. **Watch = Doorbell 🔔**
- You watch for package delivery
- Don't check door every minute (polling)
- Doorbell rings when package arrives (event)
- Efficient, event-driven

### 5. **Distributed Lock = Bathroom Key 🔑**
- Only one person can use bathroom
- Take key = acquire lock
- Return key = release lock
- If you faint holding key, key auto-returns (ephemeral)

### 6. **Configuration = Classroom Bulletin Board 📌**
- Teacher posts announcement
- All students see it immediately
- No need to text each student
- Central, consistent source of truth

---

## 🔑 Key Takeaways

1. **Coordination services solve distributed consensus**
   - Leader election, locking, configuration
   - Use proven consensus protocols (Paxos, Raft, ZAB)
   - Don't build your own!

2. **ZooKeeper is not a database**
   - Store coordination metadata only (< 1MB typical)
   - Don't store application data
   - Not designed for high throughput

3. **Ephemeral nodes are magic**
   - Automatic liveness detection
   - Session expires → node deleted
   - Powers leader election, locks, membership

4. **Watches enable event-driven architecture**
   - No polling needed
   - Instant notifications
   - But watches fire only once (must re-watch)

5. **Ensemble sizing matters**
   - Always use odd numbers (3, 5, 7)
   - 5 servers recommended for production
   - More servers = slower writes

6. **Separate consensus from application**
   - ZooKeeper provides coordination primitive
   - Application implements business logic
   - Clean separation of concerns

7. **Monitor replication lag**
   - Leader-follower lag critical metric
   - High lag = degraded performance
   - Alert if followers can't keep up

8. **Production lessons**
   - Use separate disk for transaction log
   - Enable autopurge (cleanup old snapshots)
   - Monitor session expiration rate
   - Plan for network partitions

9. **Modern alternatives exist**
   - etcd: Raft-based, Kubernetes uses it
   - Consul: Service discovery focus
   - Choose based on ecosystem integration

10. **Real-world usage**
    - Kafka: Broker coordination, controller election
    - HBase: Master election, region assignment
    - Hadoop: NameNode HA coordination
    - Use battle-tested patterns

---

**Final Thought**: Coordination services are the "control plane" of distributed systems. They're small but critical - like the brain stem. When they fail, the whole system fails. Design for reliability, monitor obsessively, and use proven patterns.
