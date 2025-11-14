# Core System Design Concepts

## Scalability

### Definition
The ability of a system to handle increased load by adding resources.

### Types

#### Vertical Scaling (Scale Up)
- Adding more power (CPU, RAM, Storage) to existing machines
- **Pros**: Simple, no application changes needed
- **Cons**: Hardware limits, single point of failure, expensive
- **Use Case**: Legacy applications, databases requiring strong consistency

#### Horizontal Scaling (Scale Out)
- Adding more machines to the system
- **Pros**: No theoretical limit, fault tolerance, cost-effective
- **Cons**: Complexity, data consistency challenges, need load balancing
- **Use Case**: Web servers, microservices, distributed systems

### Interview Tip
Always discuss both options and explain trade-offs based on requirements.

---

## Availability

### Definition
The percentage of time a system is operational and accessible.

### Measuring Availability
```
Availability = (Uptime / (Uptime + Downtime)) × 100%
```

### High Availability Tiers
| Availability | Downtime/Year | Downtime/Month | Use Case |
|--------------|---------------|----------------|----------|
| 99% (2 nines) | 3.65 days | 7.3 hours | Internal tools |
| 99.9% (3 nines) | 8.76 hours | 43.8 minutes | Most web apps |
| 99.99% (4 nines) | 52.56 minutes | 4.38 minutes | Payment systems |
| 99.999% (5 nines) | 5.26 minutes | 26.3 seconds | Critical infrastructure |

### Achieving High Availability
- **Redundancy**: Multiple instances, no single point of failure
- **Failover**: Automatic switching to backup systems
- **Health Checks**: Continuous monitoring and detection
- **Geographic Distribution**: Multi-region deployment
- **Graceful Degradation**: Partial functionality during failures

### Interview Tip
Quantify availability requirements early in the design discussion.

---

## Reliability

### Definition
The probability that a system will function correctly over a given time period.

### Key Metrics
- **MTBF (Mean Time Between Failures)**: Average time between failures
- **MTTR (Mean Time To Recovery)**: Average time to restore service
- **Error Rate**: Percentage of failed requests

### Reliability vs Availability
- A system can be available but unreliable (frequently failing and recovering)
- A reliable system is typically also highly available
- Focus on reducing both failure frequency and recovery time

### Building Reliable Systems
1. **Redundancy**: Eliminate single points of failure
2. **Testing**: Chaos engineering, failure injection
3. **Monitoring**: Real-time alerting and metrics
4. **Gradual Rollouts**: Canary deployments, feature flags
5. **Rollback Capability**: Quick reversion to stable state

---

## Latency vs Throughput

### Latency
**Definition**: Time to complete a single operation (response time)

**Measured in**: milliseconds (ms)

**Optimization Techniques**:
- Caching frequently accessed data
- CDN for static content
- Database query optimization
- Reducing network hops
- Connection pooling

### Throughput
**Definition**: Number of operations completed per unit time

**Measured in**: requests/second, queries/second, GB/second

**Optimization Techniques**:
- Horizontal scaling
- Asynchronous processing
- Batch operations
- Load balancing
- Resource pooling

### The Trade-off
```
Increasing throughput can increase latency (queueing delay)
Optimizing for low latency might reduce throughput (fewer concurrent operations)
```

### Interview Tip
Clarify which metric is more critical for the use case. Gaming requires low latency; batch processing requires high throughput.

---

## Consistency

### Definition
All nodes in a distributed system have the same data at the same time.

### Levels of Consistency

#### Strong Consistency
- All reads see the most recent write
- Typically achieved through synchronous replication
- **Trade-off**: Higher latency, lower availability
- **Example**: Banking systems, inventory management

#### Eventual Consistency
- Reads may see stale data temporarily
- System eventually becomes consistent
- **Trade-off**: Lower latency, higher availability
- **Example**: Social media feeds, DNS, shopping cart

#### Causal Consistency
- Related operations are seen in order
- Unrelated operations can be seen in any order
- **Use Case**: Messaging systems, collaborative editing

### Interview Tip
Most systems don't need strong consistency everywhere. Identify which parts truly need it.

---

## Durability

### Definition
Data once written will persist and not be lost.

### Achieving Durability
1. **Replication**: Multiple copies across different nodes
2. **Persistent Storage**: Write to disk, not just memory
3. **Write-Ahead Logging (WAL)**: Log changes before applying
4. **Snapshots**: Regular backups of system state
5. **Geographic Distribution**: Data centers in multiple regions

### Durability vs Availability Trade-off
- Synchronous replication: Higher durability, lower availability
- Asynchronous replication: Lower durability, higher availability

---

## Fault Tolerance

### Definition
The ability of a system to continue operating despite failures.

### Types of Failures
1. **Hardware Failures**: Disk crashes, network failures, power outages
2. **Software Failures**: Bugs, memory leaks, crashes
3. **Network Failures**: Partitions, packet loss, high latency
4. **Human Errors**: Misconfigurations, accidental deletions

### Fault Tolerance Strategies

#### Redundancy
```
Active-Active: All instances handle traffic
Active-Passive: Standby instances take over on failure
```

#### Graceful Degradation
- Shed non-critical features under load
- Return cached/stale data instead of errors
- Reduce quality (lower resolution, fewer recommendations)

#### Circuit Breaker Pattern
```
States:
  Closed  → Requests flow normally
  Open    → Requests fail fast (service unavailable)
  Half-Open → Test if service recovered
```

#### Bulkhead Pattern
- Isolate resources (connection pools, thread pools)
- Prevent cascading failures
- One component failure doesn't bring down entire system

### Interview Tip
Discuss how you identify and handle different failure modes. Give examples from past experience.

---

## Idempotency

### Definition
An operation that produces the same result when executed multiple times.

### Why It Matters
- Network failures can cause retries
- Duplicate messages in distributed systems
- Prevents double-charging, duplicate records

### Examples

#### Idempotent
```
PUT /users/123 {"name": "John"}  → Always sets name to John
DELETE /users/123                → User deleted (subsequent calls also succeed)
GET /users/123                   → Always returns same data
```

#### Non-Idempotent
```
POST /users {"name": "John"}     → Creates new user each time
POST /account/charge {"amount": 100} → Charges $100 each time
```

### Making Operations Idempotent
1. **Idempotency Keys**: Client-generated unique ID per operation
2. **Conditional Writes**: Check state before modification
3. **Natural Idempotency**: Design operations to be inherently idempotent

### Interview Tip
Always consider idempotency when designing APIs or distributed workflows.

---

## Partitioning (Sharding)

### Definition
Splitting data across multiple machines to improve scalability.

### Partitioning Strategies

#### Hash-Based Partitioning
```
shard = hash(key) % num_shards
```
- **Pros**: Even distribution
- **Cons**: Difficult to rebalance, range queries span multiple shards

#### Range-Based Partitioning
```
shard_1: A-M
shard_2: N-Z
```
- **Pros**: Range queries efficient, easy to add shards
- **Cons**: Hotspots if data not uniformly distributed

#### Directory-Based Partitioning
- Lookup table maps keys to shards
- **Pros**: Flexible, easy rebalancing
- **Cons**: Lookup overhead, directory can be bottleneck

### Consistent Hashing
- Minimizes data movement when adding/removing nodes
- Used by: DynamoDB, Cassandra, Memcached
- Virtual nodes improve distribution

### Interview Tip
Discuss how to handle cross-shard queries and rebalancing when shards are added/removed.

---

## Replication

### Definition
Maintaining copies of data on multiple nodes for availability and performance.

### Replication Strategies

#### Leader-Follower (Master-Slave)
- One leader handles writes
- Multiple followers handle reads
- **Pros**: Simple, read scalability
- **Cons**: Single write bottleneck, replication lag

#### Multi-Leader
- Multiple nodes accept writes
- **Pros**: Higher write availability, lower latency
- **Cons**: Conflict resolution complexity

#### Leaderless
- Any node can accept reads/writes
- **Pros**: High availability, no single point of failure
- **Cons**: More complex consistency guarantees

### Synchronous vs Asynchronous Replication

#### Synchronous
- Write confirmed only after replicas acknowledge
- **Pros**: Strong consistency, no data loss
- **Cons**: Higher latency, reduced availability

#### Asynchronous
- Write confirmed before replicas acknowledge
- **Pros**: Lower latency, higher availability
- **Cons**: Potential data loss, eventual consistency

---

## Load Balancing

### Definition
Distributing incoming requests across multiple servers.

### Algorithms

#### Round Robin
- Requests distributed sequentially
- **Use Case**: Uniform server capacity

#### Least Connections
- Route to server with fewest active connections
- **Use Case**: Long-lived connections, varying request complexity

#### Weighted Round Robin
- Servers with different capacities get proportional traffic
- **Use Case**: Heterogeneous hardware

#### IP Hash
- Client IP determines server assignment
- **Use Case**: Session affinity requirements

### Load Balancer Layers
- **L4 (Transport Layer)**: Fast, operates on TCP/UDP
- **L7 (Application Layer)**: Smarter routing, content-based, SSL termination

### Interview Tip
Discuss both client-side and server-side load balancing options.

---

## Caching

### Definition
Storing frequently accessed data in fast storage to reduce latency and load.

### Cache Levels
1. **Client-Side**: Browser cache, mobile app cache
2. **CDN**: Edge locations worldwide
3. **Application**: In-memory cache (Redis, Memcached)
4. **Database**: Query cache, buffer pool

### Eviction Policies
- **LRU (Least Recently Used)**: Removes least recently accessed
- **LFU (Least Frequently Used)**: Removes least frequently accessed
- **FIFO**: First in, first out
- **TTL (Time To Live)**: Expires after time period

### Cache Strategies

#### Cache-Aside (Lazy Loading)
```
1. Check cache
2. If miss, read from database
3. Update cache
```

#### Write-Through
```
1. Write to cache
2. Cache writes to database synchronously
```

#### Write-Back (Write-Behind)
```
1. Write to cache
2. Cache writes to database asynchronously
```

### Cache Invalidation
- **Time-based**: TTL expiration
- **Event-based**: Invalidate on updates
- **Manual**: Explicit invalidation

> "There are only two hard things in Computer Science: cache invalidation and naming things." - Phil Karlton

### Interview Tip
Discuss cache hit ratio, cold start problems, and thundering herd prevention.

---

## Asynchronous Processing

### Definition
Decoupling operations to improve responsiveness and scalability.

### Patterns

#### Message Queue
```
Producer → Queue → Consumer
```
- Buffers requests during spikes
- Examples: RabbitMQ, SQS, Kafka

#### Pub/Sub
```
Publisher → Topic → Multiple Subscribers
```
- Event broadcasting
- Examples: Kafka, SNS, Google Pub/Sub

#### Task Queue
```
Client → Queue → Workers
```
- Background job processing
- Examples: Celery, Bull, Sidekiq

### Benefits
- Improved response times
- Better resource utilization
- Resilience to traffic spikes
- Temporal decoupling

### Considerations
- Eventual consistency
- Message ordering
- Exactly-once vs at-least-once delivery
- Dead letter queues for failed messages

---

## Microservices vs Monolith

### Monolithic Architecture
**Structure**: Single deployable unit

**Pros**:
- Simpler development and deployment
- Easier debugging and testing
- Better performance (no network calls)
- Strong consistency

**Cons**:
- Harder to scale specific components
- Single point of failure
- Longer deployment cycles
- Technology lock-in

### Microservices Architecture
**Structure**: Multiple independent services

**Pros**:
- Independent scaling
- Technology flexibility
- Fault isolation
- Faster deployments

**Cons**:
- Distributed system complexity
- Network overhead
- Data consistency challenges
- Operational overhead

### When to Choose What

**Start with Monolith if**:
- Small team
- Early stage product
- Uncertain requirements
- Strong consistency needs

**Move to Microservices when**:
- Clear service boundaries
- Need independent scaling
- Multiple teams
- Polyglot requirements

### Interview Tip
Don't advocate for microservices blindly. Discuss the trade-offs and migration challenges.

---

## Key Takeaways for Interviews

1. **No Silver Bullet**: Every decision is a trade-off
2. **Requirements First**: Let requirements drive design decisions
3. **Start Simple**: Begin with simple solutions, add complexity only when needed
4. **Think About Failure**: Always discuss what happens when things go wrong
5. **Quantify**: Use numbers (QPS, latency, data volume) to justify decisions
6. **Real-World**: Reference actual systems (how Netflix, Uber, etc. solve problems)

## Practice Questions

1. How would you design a system to handle 100K requests/second?
2. Explain the trade-offs between consistency and availability
3. When would you choose SQL over NoSQL?
4. How do you ensure exactly-once message delivery?
5. Design a system that needs to be available in multiple geographic regions
