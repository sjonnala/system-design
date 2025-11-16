# Failure Detection

## The Failure Detection Problem

**Challenge**: In distributed systems, how to reliably detect when a node has failed?

**Why It's Hard**:
- Can't distinguish slow from crashed
- Network delays are unpredictable
- Partial failures (some nodes see failure, others don't)
- False positives can cause cascading failures

**Critical Use Cases**:
- Leader election
- Load balancing
- Service discovery
- Consensus algorithms
- Health monitoring

---

## Types of Failures

### 1. Crash-Stop Failures

**Definition**: Node stops executing and never recovers

```
Normal Operation → Node Crashes → Stays Down

Timeline:
[Running] → [CRASH] → [Dead]
```

**Characteristics**:
- Easiest to handle
- Node simply stops responding
- No partial/corrupted responses

**Example**: Server power loss

### 2. Crash-Recovery Failures

**Definition**: Node crashes but can restart

```
[Running] → [CRASH] → [Restarting] → [Running]
```

**Challenges**:
- Lost in-memory state
- Need to recover from persistent storage
- Other nodes may have moved on

**Example**: Application crash with auto-restart

### 3. Omission Failures

**Definition**: Node drops messages

```
Node A → (message lost) → Node B

Types:
- Send omission: Failure to send
- Receive omission: Failure to receive
```

**Causes**:
- Network congestion
- Buffer overflow
- Network interface issues

### 4. Byzantine Failures

**Definition**: Node behaves arbitrarily (malicious or buggy)

```
Examples:
- Sends different values to different nodes
- Sends corrupted data
- Claims false information
```

**Why It's Hard**: Can't trust anything from failed node

**Use Cases**:
- Blockchain consensus
- Security-critical systems
- Handling malicious actors

---

## Failure Detection Approaches

### Heartbeat-Based Detection

**Concept**: Nodes periodically send "I'm alive" messages

**Basic Heartbeat**:
```
Node A                          Node B (Monitor)
  |                                  |
  |------- Heartbeat -------------->  | (receive)
  |                                  |
  |  (wait interval)                 |
  |                                  |
  |------- Heartbeat -------------->  | (receive)
  |                                  |
  |                                  |
  X (crash)                          |
  |                                  |
  |                                  | (timeout!)
  |                                  | → Declare A failed
```

**Implementation**:
```python
class HeartbeatMonitor:
    def __init__(self, timeout=5.0):
        self.last_heartbeat = {}
        self.timeout = timeout

    def receive_heartbeat(self, node_id):
        self.last_heartbeat[node_id] = current_time()

    def check_failures(self):
        failed = []
        now = current_time()
        for node_id, last_time in self.last_heartbeat.items():
            if now - last_time > self.timeout:
                failed.append(node_id)
        return failed
```

**Pros**:
- Simple to implement
- Low overhead
- Works well in practice

**Cons**:
- False positives (network delay)
- Fixed timeout (hard to tune)
- Doesn't scale to many nodes

### Ping-Based Detection

**Concept**: Monitor actively pings node

```
Monitor                         Node
  |                              |
  |-------- PING -------------->  |
  |                              |
  |<------- PONG --------------- |
  |                              |
  | (measure round-trip time)    |
```

**Adaptive Timeout**:
```python
class AdaptivePing:
    def __init__(self):
        self.rtt_history = []
        self.alpha = 0.125  # smoothing factor
        self.estimated_rtt = 100  # ms

    def update_rtt(self, measured_rtt):
        # Exponential moving average
        self.estimated_rtt = (1 - self.alpha) * self.estimated_rtt + \
                             self.alpha * measured_rtt

        # Timeout = estimated RTT + 4 * deviation
        timeout = self.estimated_rtt + 4 * self.deviation()
        return timeout
```

**Used By**:
- TCP retransmission
- Service health checks
- Load balancers

### All-to-All Heartbeating

**Concept**: Every node monitors every other node

```
Cluster with 4 nodes:

Node 1 → sends heartbeat to → Node 2, Node 3, Node 4
Node 2 → sends heartbeat to → Node 1, Node 3, Node 4
Node 3 → sends heartbeat to → Node 1, Node 2, Node 4
Node 4 → sends heartbeat to → Node 1, Node 2, Node 3
```

**Message Complexity**: O(N²) per heartbeat interval

**Pros**:
- Every node has full view
- No single point of failure
- Quick detection

**Cons**:
- Doesn't scale (100 nodes = 10,000 messages)
- Network overhead
- Coordination complexity

**Use Cases**: Small clusters (< 10 nodes)

### Ring-Based Heartbeating

**Concept**: Nodes monitor successor in ring

```
Ring Topology:

    [1] → [2]
     ↑     ↓
    [4] ← [3]

Node 1 monitors Node 2
Node 2 monitors Node 3
Node 3 monitors Node 4
Node 4 monitors Node 1
```

**Message Complexity**: O(N) per interval

**Failure Detection**:
```
If Node 2 fails:
- Node 1 detects (no heartbeat from 2)
- Node 1 starts monitoring Node 3
- Information propagates around ring
```

**Pros**:
- Scalable
- Low overhead
- Simple topology

**Cons**:
- Slower failure detection
- Multiple hops to propagate
- Ring maintenance on failures

### Gossip-Based Detection

**Concept**: Nodes randomly gossip about others' health

```
Round 1:
Node 1: "I'm alive, saw 2 recently, 3 is slow"
Node 2: "I'm alive, saw 3 recently, 1 is fine"

Round 2:
Node 1 picks random node (Node 3)
Exchanges health information
Updates local view

Round 3:
Node 2 picks random node (Node 1)
Exchanges health information
...
```

**Implementation**:
```python
class GossipFailureDetector:
    def __init__(self):
        self.heartbeat_counter = {}  # node_id -> counter
        self.last_update = {}        # node_id -> timestamp

    def gossip_round(self):
        # Increment own heartbeat
        self.heartbeat_counter[self.id] += 1

        # Pick random peer
        peer = random.choice(self.peers)

        # Exchange heartbeat info
        peer_info = peer.get_heartbeat_info()

        # Merge information
        for node_id, counter in peer_info.items():
            if counter > self.heartbeat_counter.get(node_id, 0):
                self.heartbeat_counter[node_id] = counter
                self.last_update[node_id] = current_time()

    def detect_failures(self):
        failed = []
        now = current_time()
        for node_id, last_time in self.last_update.items():
            if now - last_time > TIMEOUT:
                failed.append(node_id)
        return failed
```

**Pros**:
- Scalable (O(log N) propagation time)
- Fault-tolerant
- No single point of failure
- Self-healing

**Cons**:
- Eventual consistency (slower detection)
- Probabilistic guarantees
- More complex

**Used By**:
- **Cassandra**: Gossip protocol
- **Consul**: Serf gossip
- **Amazon DynamoDB**: Epidemic protocols

---

## SWIM (Scalable Weakly-consistent Infection-style Process Group Membership)

### Architecture

**Hybrid Approach**: Combines ping and gossip

**Components**:
1. **Failure Detection**: Ping/ping-req protocol
2. **Dissemination**: Gossip for membership updates

### Ping-Req Protocol

```
Node A wants to check Node B:

Step 1: Direct Ping
Node A → PING → Node B
         (wait timeout)

If no response:

Step 2: Indirect Ping (via K random nodes)
Node A → PING-REQ(B) → Node C
Node C → PING → Node B
Node B → ACK → Node C
Node C → ACK → Node A

If still no response:
→ Declare B as suspected
```

**Diagram**:
```
Scenario: B is slow, not failed

Direct Ping:
A → B (timeout, no response)

Indirect Ping:
A → C: "Can you ping B?"
C → B: PING
B → C: ACK (B is alive!)
C → A: ACK (B responded)

Result: A knows B is alive (network issue between A-B)
```

### Why It Works

**Reduces False Positives**:
- Direct ping might fail (network partition between A-B)
- Indirect ping succeeds (B reachable via C)
- Distinguishes node failure from network partition

**Scalable**:
- Fixed overhead per node (K indirect pings)
- Independent of cluster size
- Parallel detection

### SWIM Failure States

```
States:
1. Alive: Node is healthy
2. Suspected: Might be failed (no ping response)
3. Failed: Confirmed failed
4. Left: Node intentionally left

Transitions:
Alive → Suspected (ping timeout)
Suspected → Alive (indirect ping succeeds)
Suspected → Failed (suspicion timeout)
Alive/Suspected → Left (graceful shutdown)
```

### Dissemination Component

**Piggyback on Messages**:
```
Every ping/ack message includes:
- Membership updates
- Suspicion notifications
- Failure declarations

Example:
PING message from A to B:
{
  type: "PING",
  from: "A",
  updates: [
    {node: "C", state: "alive", incarnation: 5},
    {node: "D", state: "suspected", incarnation: 3}
  ]
}
```

**Infection-Style Spreading**:
- Each node stores updates
- Piggybacks recent updates on messages
- Exponential spread (O(log N) rounds)

### Used By

- **HashiCorp Consul**: SWIM for cluster membership
- **HashiCorp Serf**: Standalone SWIM implementation
- **Apache Cassandra**: Modified SWIM (gossip)

---

## Phi Accrual Failure Detector

### Key Idea

**Instead of Binary (Alive/Dead)**: Provide suspicion level (0 to ∞)

**Phi (Φ) Value**:
- Φ = 0: Node definitely alive
- Φ = 1: Low suspicion
- Φ = 5: Moderate suspicion
- Φ = 10+: High suspicion (likely failed)

**Application decides threshold**:
```python
if phi > THRESHOLD:
    mark_as_failed(node)
```

### How It Works

**Track Inter-Arrival Times**:
```
Heartbeat arrivals:
t1 = 0ms
t2 = 100ms   (interval: 100ms)
t3 = 200ms   (interval: 100ms)
t4 = 310ms   (interval: 110ms)
t5 = 395ms   (interval: 85ms)

Average interval: ~100ms
Variance: ~10ms
```

**Calculate Phi**:
```
Current time: 500ms
Last heartbeat: 395ms
Elapsed: 105ms

Expected: ~100ms (based on history)
Phi = -log10(probability of this delay)

If delay unusual: Phi increases
```

**Mathematical Formula**:
```
Φ(t) = -log₁₀(P(T > t))

Where:
- T = random variable for inter-arrival time
- t = current elapsed time since last heartbeat
- P(T > t) = probability heartbeat arrives after t
```

### Adaptive Nature

**Adjusts to Network Conditions**:
```
Stable network:
- Heartbeats regular (100ms ± 5ms)
- High variance → Higher Phi sooner
- Quick failure detection

Unstable network:
- Heartbeats irregular (100ms ± 50ms)
- High variance → Lower Phi for same delay
- Slower detection, fewer false positives
```

### Implementation

```python
import math
from collections import deque

class PhiAccrualDetector:
    def __init__(self, window_size=1000):
        self.intervals = deque(maxlen=window_size)
        self.last_heartbeat = None

    def heartbeat(self, timestamp):
        if self.last_heartbeat:
            interval = timestamp - self.last_heartbeat
            self.intervals.append(interval)
        self.last_heartbeat = timestamp

    def phi(self, current_time):
        if not self.last_heartbeat:
            return 0.0

        elapsed = current_time - self.last_heartbeat

        # Calculate mean and variance
        mean = sum(self.intervals) / len(self.intervals)
        variance = sum((x - mean)**2 for x in self.intervals) / len(self.intervals)

        # Probability of delay greater than elapsed
        # (Assuming normal distribution)
        std_dev = math.sqrt(variance)
        probability = normal_cdf(elapsed, mean, std_dev)

        # Phi value
        if probability > 0:
            return -math.log10(probability)
        return float('inf')
```

### Used By

- **Akka**: Actor system failure detection
- **Cassandra**: Adaptive failure detector
- **Riak**: Cluster membership

---

## Failure Detection in Practice

### Kubernetes Health Checks

**Three Types**:

**1. Liveness Probe**: Is container alive?
```yaml
livenessProbe:
  httpGet:
    path: /healthz
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  timeoutSeconds: 5
  failureThreshold: 3

If fails 3 times: Restart container
```

**2. Readiness Probe**: Is container ready for traffic?
```yaml
readinessProbe:
  httpGet:
    path: /ready
    port: 8080
  periodSeconds: 5

If fails: Remove from service endpoints
```

**3. Startup Probe**: Is container finished starting?
```yaml
startupProbe:
  httpGet:
    path: /startup
    port: 8080
  failureThreshold: 30
  periodSeconds: 10

For slow-starting apps (up to 300s startup time)
```

### Load Balancer Health Checks

**Active Health Checks**:
```
Load Balancer → HTTP GET /health → Backend Server

Healthy Response:
- Status: 200 OK
- Response time: < timeout
- Valid response body

Unhealthy:
- Status: 500+ (server error)
- Timeout
- Connection refused
→ Remove from pool
```

**Passive Health Checks**:
```
Monitor actual traffic:
- Track error rates
- Track response times
- Track connection failures

If error rate > threshold:
→ Mark unhealthy
→ Circuit breaker opens
```

**Used By**:
- **NGINX**: Active + passive checks
- **HAProxy**: HTTP/TCP health checks
- **AWS ELB**: Health check configuration

### Service Mesh (Istio)

**Outlier Detection**:
```yaml
outlierDetection:
  consecutiveErrors: 5
  interval: 30s
  baseEjectionTime: 30s
  maxEjectionPercent: 50

If 5 consecutive errors:
- Eject pod from pool for 30s
- Retry with exponential backoff
- Max 50% of pods ejected
```

**Circuit Breaker**:
```yaml
connectionPool:
  tcp:
    maxConnections: 100
  http:
    http1MaxPendingRequests: 50
    maxRequestsPerConnection: 2

If limits exceeded:
→ Fast fail (don't wait for timeout)
→ Return error immediately
```

---

## Interview Q&A

**Q: "How would you implement failure detection for a distributed cache cluster?"**

**Answer**:
```
I'd use a multi-layered approach:

1. Gossip-Based Detection (Primary):
   - Each node gossips with random peers
   - Share heartbeat counters
   - Scalable to 100+ nodes
   - Eventually consistent view

   Implementation:
   - Every 1s: gossip with 3 random nodes
   - Share: {node_id: heartbeat_counter, timestamp}
   - Detect: If no update in 10s → suspect
   - Confirm: If no update in 30s → failed

2. Direct Health Checks (Secondary):
   - Clients ping cache nodes directly
   - Fast local detection
   - Independent of gossip

   Implementation:
   - Client sends: GET request
   - Timeout: 100ms
   - 3 failures → mark node down locally
   - Route requests to other nodes

3. Active Monitoring (Background):
   - Dedicated monitor checks all nodes
   - Metrics: response time, error rate
   - Alerts: Degradation before failure

Why This Approach:
- Gossip: Scalable cluster membership
- Direct checks: Fast client-side failover
- Monitoring: Proactive detection

Trade-offs:
- Complexity: Multiple detection mechanisms
- Eventual consistency: Gossip takes time
- But: High availability, no single point of failure

Example: Cassandra uses similar approach
```

**Q: "What's the difference between failure detection and failure recovery?"**

**Answer**:
```
Failure Detection: Identifying that node failed
Failure Recovery: Taking action after detection

Failure Detection:
- Heartbeats, pings, health checks
- Determining node state (alive/dead)
- Propagating failure information
- Challenge: False positives vs detection speed

Failure Recovery:
- Leader election (if leader failed)
- Data replication (recover lost data)
- Request rerouting (failover)
- State reconstruction
- Challenge: Maintaining consistency

Example - Distributed Database:

Detection:
1. Node B misses heartbeat
2. Cluster suspects B failed
3. Wait timeout period
4. Confirm: B is down

Recovery:
1. Elect new owner for B's data partition
2. Replicas take over (read/write)
3. Redistribute B's data if needed
4. Update routing tables
5. Resume operations

Both Are Critical:
- Fast detection: Minimize downtime
- Correct recovery: Maintain consistency
- False positives: Trigger unnecessary recovery
- Balance: Accuracy vs speed
```

**Q: "How do you tune failure detection timeouts?"**

**Answer**:
```
Timeout Tuning Trade-off:

Short Timeout:
- Pro: Fast failure detection
- Con: More false positives (network delays)

Long Timeout:
- Pro: Fewer false positives
- Con: Slow failure detection

Tuning Approach:

1. Measure Baseline:
   - Network RTT: 10-50ms (same datacenter)
   - Heartbeat interval: 1s
   - Variance: ±20ms

2. Calculate Timeout:
   Rule of thumb: Timeout = 3-5x heartbeat interval

   Example:
   - Heartbeat: 1s
   - Timeout: 3s (3 missed heartbeats)

3. Adaptive Timeouts:
   - Track heartbeat intervals
   - Calculate mean + std deviation
   - Timeout = mean + 4σ (99.99% confidence)

   Example:
   Mean RTT: 100ms
   Std dev: 20ms
   Timeout: 100 + 4(20) = 180ms

4. Context Matters:
   - Same datacenter: 1-3s timeout
   - Cross-region: 5-10s timeout
   - Internet: 30s+ timeout

5. Phi Accrual Approach:
   - Don't use fixed timeout
   - Use suspicion level (Φ)
   - Adaptive to network conditions

Example Configuration (Kubernetes):

livenessProbe:
  periodSeconds: 10         # Check every 10s
  timeoutSeconds: 1         # Each probe timeout: 1s
  failureThreshold: 3       # 3 failures = 30s total
  successThreshold: 1       # 1 success = alive

Result: 30s detection time, tolerates 2 transient failures

Best Practice:
- Start conservative (higher timeout)
- Monitor false positive rate
- Gradually reduce if needed
- Use adaptive algorithms (Phi Accrual)
```

---

## Key Takeaways

1. **No Perfect Detection**: Can't distinguish slow from crashed in asynchronous systems
2. **Trade-offs**: Detection speed vs false positive rate
3. **Scalability**: All-to-all doesn't scale, use gossip or hierarchical
4. **Adaptive**: Phi accrual adjusts to network conditions
5. **Layered Approach**: Combine multiple detection mechanisms
6. **Context Matters**: Tune timeouts based on environment

---

## Further Reading

- "SWIM: Scalable Weakly-consistent Infection-style Process Group Membership Protocol"
- "The Phi Accrual Failure Detector" - Hayashibara et al.
- "Designing Data-Intensive Applications" - Chapter 8
- Cassandra Architecture: Gossip and Failure Detection
- Akka Documentation: Phi Accrual Failure Detector
- Kubernetes: Container Probes
- "Unreliable Failure Detectors for Reliable Distributed Systems" - Chandra and Toueg
