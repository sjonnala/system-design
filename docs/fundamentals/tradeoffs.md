# Trade-offs in System Design

> "In system design, there are no perfect solutions - only trade-offs that align with your specific requirements."

## Fundamental Trade-offs

### 1. Consistency vs Availability (CAP Theorem)

**The Trade-off**:
During network partition, you must choose between consistency or availability.

| Choose Consistency (CP) | Choose Availability (AP) |
|------------------------|-------------------------|
| Refuse requests during partition | Serve potentially stale data |
| Prevent split-brain scenarios | Allow divergent states |
| Guarantee correct data | Guarantee system uptime |

**When to Choose CP**:
- Financial transactions
- Inventory management
- Booking systems
- Critical state management

**When to Choose AP**:
- Social media feeds
- Analytics data
- Caching layers
- Non-critical user data

**Interview Example**:
```
"For an e-commerce inventory system, I'd choose consistency (CP).
It's better to show 'out of stock' error than to accept orders
we can't fulfill. Users understand temporary unavailability,
but overselling damages trust and costs money in compensation."

"For a news feed, I'd choose availability (AP). Users tolerate
seeing slightly outdated content, but they won't tolerate a
completely unavailable app. We can resolve inconsistencies
asynchronously."
```

---

### 2. Latency vs Throughput

**The Trade-off**:
Optimizing for low latency can reduce throughput, and vice versa.

**Latency-Optimized**:
- Small batch sizes
- Immediate processing
- Fewer concurrent operations
- Lower throughput

**Throughput-Optimized**:
- Large batch sizes
- Buffered processing
- Many concurrent operations
- Higher latency

**Real-World Examples**:

```
Gaming Server (Low Latency):
- Process each action immediately
- Target: <50ms response time
- Trade-off: Lower requests/second per server

Analytics Pipeline (High Throughput):
- Batch process every 5 minutes
- Target: 1M events/second
- Trade-off: 5-minute processing delay
```

**Interview Tip**:
Ask which matters more for the use case. Most systems need both, so find the balance point.

---

### 3. Consistency vs Performance

**The Trade-off**:
Strong consistency requires coordination, which adds latency.

| Strong Consistency | Eventual Consistency |
|-------------------|---------------------|
| Synchronous replication | Asynchronous replication |
| Higher latency (wait for all nodes) | Lower latency (don't wait) |
| Coordination overhead | No coordination needed |
| Read: 100-500ms | Read: 1-10ms |
| Write: 100-500ms | Write: 1-10ms |

**Example**:
```
Banking System:
Read balance: Query primary database (strong consistency)
Latency: 50ms
Trade-off: Correctness over speed

Social Media Like Count:
Read likes: Query nearest replica (eventual consistency)
Latency: 5ms
Trade-off: Speed over perfect accuracy
```

---

### 4. Normalization vs Denormalization

**The Trade-off**:
Storage efficiency and data integrity vs query performance.

**Normalized Data**:
```sql
Users Table:
| user_id | name  |
|---------|-------|
| 1       | Alice |

Orders Table:
| order_id | user_id | amount |
|----------|---------|--------|
| 101      | 1       | 50.00  |

Query for user's orders:
SELECT * FROM orders
JOIN users ON orders.user_id = users.user_id
WHERE users.user_id = 1;
```

**Denormalized Data**:
```sql
OrderDetails Table:
| order_id | user_id | user_name | amount |
|----------|---------|-----------|--------|
| 101      | 1       | Alice     | 50.00  |

Query for user's orders:
SELECT * FROM order_details WHERE user_id = 1;
```

| Normalized | Denormalized |
|------------|--------------|
| Less storage (no duplication) | More storage (data repeated) |
| Slower reads (joins required) | Faster reads (no joins) |
| Faster writes (update one place) | Slower writes (update multiple places) |
| Guaranteed consistency | Risk of inconsistency |

**When to Denormalize**:
- Read-heavy workloads
- Performance-critical queries
- Data rarely changes
- Acceptable slight inconsistency

**When to Keep Normalized**:
- Write-heavy workloads
- Frequent data updates
- Storage constraints
- Strong consistency required

---

### 5. Synchronous vs Asynchronous Processing

**The Trade-off**:
Immediate feedback vs improved throughput and resilience.

**Synchronous**:
```
User Request → Process → Return Result
             [User waits]
- Immediate feedback
- Simpler error handling
- Limited by slowest operation
```

**Asynchronous**:
```
User Request → Queue Task → Return Accepted
                   ↓
              [Process Later]
                   ↓
              Notify User
- Faster response to user
- Better resource utilization
- Complex error handling
- Need status tracking
```

**Examples**:

| Operation | Sync or Async? | Reasoning |
|-----------|----------------|-----------|
| Credit card authorization | Synchronous | User needs immediate answer |
| Email sending | Asynchronous | User doesn't need to wait |
| Image upload | Hybrid | Accept upload sync, process async |
| Database query | Synchronous | Need data to continue |
| Analytics tracking | Asynchronous | Not user-facing |

---

### 6. SQL vs NoSQL

**The Trade-off**:
Rich query capabilities and ACID vs scalability and flexibility.

**SQL (Relational)**:
```
Pros:
- ACID transactions
- Complex queries (joins, aggregations)
- Data integrity constraints
- Mature ecosystem

Cons:
- Vertical scaling limitations
- Schema changes expensive
- Sharding complexity
- Joins expensive at scale
```

**NoSQL (Non-relational)**:
```
Pros:
- Horizontal scaling
- Flexible schema
- High throughput
- Specialized for specific patterns

Cons:
- Limited query capabilities
- No joins (denormalize data)
- Eventual consistency (usually)
- Application-level integrity
```

**Decision Matrix**:

| Use SQL When | Use NoSQL When |
|-------------|----------------|
| Complex relationships | Simple key-value or documents |
| ACID transactions needed | Eventual consistency acceptable |
| Ad-hoc queries required | Access patterns known upfront |
| Data structure stable | Schema evolves frequently |
| Moderate scale (<10TB) | Massive scale (>10TB) |

**Hybrid Approach**:
```
E-commerce Platform:
- User data, orders, payments → PostgreSQL (transactions)
- Product catalog → MongoDB (flexible schema)
- Session data → Redis (fast access)
- Analytics → Cassandra (high write throughput)
```

---

### 7. Monolith vs Microservices

**The Trade-off**:
Simplicity vs scalability and flexibility.

**Monolith**:
```
Pros:
- Simpler deployment
- Easier debugging
- Better performance (no network)
- Easier transactions

Cons:
- Scales as a unit
- Single point of failure
- Technology lock-in
- Slower releases
```

**Microservices**:
```
Pros:
- Independent scaling
- Technology freedom
- Fault isolation
- Parallel development

Cons:
- Operational complexity
- Network overhead
- Distributed transactions hard
- More moving parts
```

**Evolution Path**:
```
Stage 1: Monolith
- Small team, proving concept
- Fast iteration

Stage 2: Modular Monolith
- Clear boundaries within monolith
- Prepare for extraction

Stage 3: Selective Microservices
- Extract high-scale components
- Keep core as monolith

Stage 4: Full Microservices
- When teams and scale demand it
```

---

### 8. Read Optimization vs Write Optimization

**The Trade-off**:
Optimizing one operation often degrades the other.

**Read-Optimized**:
```
Techniques:
- Denormalized data (no joins)
- Materialized views
- Heavy indexing
- Read replicas
- Aggressive caching

Cost:
- Slower writes (update many places)
- More storage (duplicated data)
- Complex write logic
```

**Write-Optimized**:
```
Techniques:
- Normalized data (update once)
- Minimal indexes
- Write-ahead logging
- Append-only structures
- Batch writes

Cost:
- Slower reads (joins, aggregations)
- Less caching effective
- Complex read logic
```

**Decision Based on Ratio**:
```
Read:Write = 100:1 → Optimize for reads
Read:Write = 1:1   → Balance both
Read:Write = 1:100 → Optimize for writes
```

**Examples**:
```
Social Media Feed:
- Read:Write = 1000:1
- Heavily denormalized
- Aggressively cached
- Multiple read replicas

Real-time Analytics:
- Read:Write = 1:1000
- Append-only log structure
- Minimal indexing
- Batch read queries
```

---

### 9. Strong vs Eventual Consistency

**The Trade-off**:
Correctness guarantees vs availability and performance.

| Dimension | Strong Consistency | Eventual Consistency |
|-----------|-------------------|---------------------|
| Latency | Higher (coordination) | Lower (no coordination) |
| Availability | Lower (blocks on partition) | Higher (always available) |
| Complexity | Lower (simpler logic) | Higher (conflict resolution) |
| Scalability | Limited (coordination overhead) | Better (independent nodes) |
| Use Cases | Financial, inventory | Social, analytics |

**Hybrid Approach**:
```
Shopping Cart System:

Strong Consistency:
- Checkout process
- Payment authorization
- Inventory decrement

Eventual Consistency:
- Add to cart
- Wishlist updates
- Browse history
- Product recommendations
```

---

### 10. Batch Processing vs Stream Processing

**The Trade-off**:
Throughput and resource efficiency vs real-time insights.

**Batch Processing**:
```
Process large volumes periodically:
- Every hour/day/week
- High throughput
- Lower infrastructure cost
- Delayed insights

Examples:
- Daily sales reports
- Monthly billing
- Periodic ML model training
```

**Stream Processing**:
```
Process events as they arrive:
- Continuous processing
- Real-time insights
- Higher infrastructure cost
- Lower throughput per resource

Examples:
- Fraud detection
- Real-time recommendations
- Live dashboards
- Alerting systems
```

**Lambda Architecture** (Hybrid):
```
┌──────────────────┐
│  Incoming Data   │
└────────┬─────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 [Batch]  [Stream]
    │         │
    │         │ Real-time
    │         ▼
    │    [Speed Layer]
    │         │
    ▼         │
[Batch Layer] │
    │         │
    └────┬────┘
         │
         ▼
  [Serving Layer]
```

---

### 11. Compute vs Storage vs Network

**The Trade-off**:
Optimize for one resource, consume more of others.

**Compute-Heavy**:
```
Example: Compress data before storage
- More CPU usage
- Less storage needed
- Less network bandwidth

Use when: Storage/bandwidth expensive, CPU cheap
```

**Storage-Heavy**:
```
Example: Pre-compute and cache results
- More storage needed
- Less CPU usage
- Faster response time

Use when: Storage cheap, compute expensive
```

**Network-Heavy**:
```
Example: Transfer raw data, process at destination
- More bandwidth usage
- Less edge compute
- Centralized processing

Use when: Centralized processing preferred
```

**Example Decision**:
```
Video Streaming:

Option 1: Compress videos heavily
- High CPU (encoding)
- Low storage (compressed files)
- Low bandwidth (smaller files)

Option 2: Store multiple resolutions pre-encoded
- Low CPU (no encoding)
- High storage (many versions)
- Variable bandwidth (right size)

Netflix chooses Option 2: Storage is cheap, better user experience
```

---

### 12. Push vs Pull

**The Trade-off**:
Real-time updates vs resource efficiency.

**Push (Server → Client)**:
```
Examples: WebSockets, Server-Sent Events

Pros:
- Real-time updates
- Lower latency
- No polling overhead

Cons:
- Maintain connections
- Doesn't scale to millions
- Client must be online
```

**Pull (Client → Server)**:
```
Examples: HTTP polling, client-initiated requests

Pros:
- Simpler to scale
- Client controls rate
- Works offline (catch up later)

Cons:
- Polling overhead
- Higher latency
- Unnecessary requests
```

**Hybrid: Long Polling / WebSocket Fallback**:
```
1. Try WebSocket (push)
2. Fall back to long polling
3. Graceful degradation
```

**Decision Guide**:
```
Real-time chat → Push (WebSocket)
Email inbox → Pull (periodic polling)
Stock prices → Push (for active traders)
News feed → Pull (periodic refresh)
```

---

### 13. Cost vs Performance

**The Trade-off**:
Higher performance usually means higher cost.

**Performance Optimization Costs**:
```
More servers → Higher compute cost
More caching → Higher memory cost
More replicas → Higher storage cost
Lower latency → More expensive regions/tiers
Higher availability → More redundancy → Higher cost
```

**Cost Optimization Techniques**:
```
1. Auto-scaling (pay for what you use)
2. Reserved instances (commit for discount)
3. Spot instances (interruptible workloads)
4. Tiered storage (hot vs cold data)
5. Compression (CPU for storage/bandwidth)
6. Efficient queries (reduce database load)
```

**Example**:
```
Video Encoding Service

Option 1: Real-time encoding
- CPU-intensive instances
- Always-on capacity
- Cost: $10,000/month
- Latency: 10 seconds

Option 2: Batch encoding
- Scheduled processing
- Spot instances
- Cost: $2,000/month
- Latency: 5 minutes

Decision: For user uploads, Option 2 acceptable
```

---

### 14. Generic vs Specialized

**The Trade-off**:
Flexibility vs optimal performance.

**Generic Solutions**:
```
Examples: General-purpose database, standard VM

Pros:
- Flexible (many use cases)
- Familiar (common skills)
- Lower risk (proven)

Cons:
- Not optimized for specific workload
- May waste resources
```

**Specialized Solutions**:
```
Examples: Time-series DB, GPU instances

Pros:
- Optimized for specific workload
- Better performance
- Lower cost for that workload

Cons:
- Learning curve
- Vendor lock-in risk
- Less flexible
```

**Example**:
```
Time-Series Data

Generic: PostgreSQL
- Familiar SQL
- Flexible
- Slower at scale

Specialized: InfluxDB/TimescaleDB
- 10x better performance
- Purpose-built features
- Less flexible for other queries

Decision: If primarily time-series queries, use specialized
```

---

### 15. Build vs Buy

**The Trade-off**:
Control and customization vs time-to-market and maintenance burden.

**Build**:
```
Pros:
- Perfect fit for requirements
- No licensing costs
- Full control
- Competitive advantage

Cons:
- Development time
- Ongoing maintenance
- Security responsibility
- May reinvent wheel
```

**Buy (Use Existing Solution)**:
```
Pros:
- Faster deployment
- Battle-tested
- Regular updates
- Support available

Cons:
- May not fit perfectly
- Licensing costs
- Less control
- Vendor dependency
```

**Decision Framework**:
```
Buy if:
- Not core differentiator
- Standard problem
- Limited resources
- Quick timeline

Build if:
- Unique requirements
- Competitive advantage
- Long-term investment
- Existing expertise
```

**Examples**:
```
Authentication → Buy (Auth0, AWS Cognito)
Unique ML Algorithm → Build
Payment Processing → Buy (Stripe)
Core Business Logic → Build
```

---

## Interview Strategy for Trade-offs

### 1. Always Acknowledge Trade-offs
```
Bad: "We'll use microservices because they're better"
Good: "Microservices give us independent scaling but add
      operational complexity. Given we have 20+ teams and
      need independent deployment, the trade-off is worth it."
```

### 2. Ask Clarifying Questions
```
"What's more important: consistency or availability?"
"What's the read/write ratio?"
"What's the acceptable latency?"
"What's the budget?"
```

### 3. Quantify When Possible
```
"Moving from monolith to microservices will:
- Increase deployment frequency: 1/week → 10/day
- Increase ops cost: 10% more infrastructure
- Reduce feature coupling: teams move independently
- Add latency: ~5ms per service hop"
```

### 4. Show Awareness of Context
```
"For a startup, I'd start with a monolith because:
- Small team (simplicity matters)
- Uncertain requirements (flexibility needed)
- Limited ops resources (reduce complexity)

As we grow past 50 engineers and identify clear
service boundaries, we can extract microservices."
```

### 5. Propose Hybrid Solutions
```
"We can use:
- Strong consistency for checkout
- Eventual consistency for product catalog
- Read replicas for analytics queries
- Write-through cache for hot data"
```

---

## Common Interview Scenarios

### Scenario: Design a High-Scale System

**Trade-offs to Discuss**:
- Consistency vs Availability
- Cost vs Performance
- Latency vs Throughput
- SQL vs NoSQL
- Normalization vs Denormalization

### Scenario: Design a Real-Time System

**Trade-offs to Discuss**:
- Push vs Pull
- Latency vs Throughput
- Synchronous vs Asynchronous
- Stateful vs Stateless

### Scenario: Design on Limited Budget

**Trade-offs to Discuss**:
- Cost vs Performance
- Build vs Buy
- Generic vs Specialized
- Monolith vs Microservices

---

## Key Takeaways

1. **No Perfect Solution**: Every choice has trade-offs
2. **Requirements Drive Decisions**: Different requirements → different trade-offs
3. **Quantify Impact**: Numbers make trade-offs concrete
4. **Think Holistically**: Consider operational, cost, and complexity trade-offs
5. **Iterate**: Start simple, optimize based on actual bottlenecks
6. **Be Honest**: Acknowledge weaknesses in your design

> "The real skill in system design is not knowing all the answers, but understanding the trade-offs and making informed decisions based on specific requirements."
