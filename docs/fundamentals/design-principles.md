# System Design Principles

## Core Principles

### 1. Start Simple, Scale as Needed

**Principle**: Don't over-engineer. Begin with the simplest solution that meets requirements.

**Why It Matters**:
- Premature optimization wastes time
- Complex systems are harder to debug and maintain
- Requirements change - flexibility is valuable

**Example**:
```
Bad: "We'll start with microservices, Kubernetes, and event sourcing"
Good: "Let's start with a monolith, prove the concept, then split
      services when we hit specific bottlenecks"
```

**In Practice**:
- Start with monolith → Split to microservices when needed
- Start with single database → Shard when volume demands
- Start with synchronous → Add async when latency is issue

### Interview Tip
Explain your scaling path: "We'll start with X, monitor Y metric, and migrate to Z when we hit threshold N."

---

### 2. Design for Failure

**Principle**: Assume everything will fail. Plan for it.

**Components That Fail**:
- Servers crash
- Networks partition
- Disks fail
- Databases become unavailable
- Deployments have bugs
- Dependencies timeout
- Humans make mistakes

**Strategies**:

#### Redundancy
```
Single Point of Failure:
[Client] → [Server] → [Database]
           ↑ RISK

With Redundancy:
                  ┌→ [Server A] ┐
[Client] → [LB] ──┼→ [Server B] ├→ [Primary DB]
                  └→ [Server C] ┘      ↓
                                    [Replica DB]
```

#### Circuit Breaker
```python
class CircuitBreaker:
    states = ['CLOSED', 'OPEN', 'HALF_OPEN']

    def call(self, service):
        if self.state == 'OPEN':
            return fallback_response()  # Fail fast

        try:
            response = service.call()
            self.on_success()
            return response
        except Exception:
            self.on_failure()
            raise
```

#### Graceful Degradation
```
Full Service:
- Personalized recommendations
- Real-time inventory
- High-res images
- Multiple payment options

Degraded Mode (during high load):
- Generic recommendations
- Cached inventory
- Lower-res images
- Primary payment only
```

#### Timeouts and Retries
```
Configure timeouts everywhere:
- Database queries: 1s timeout
- API calls: 3s timeout
- External services: 5s timeout

Exponential backoff for retries:
- Retry 1: Wait 100ms
- Retry 2: Wait 200ms
- Retry 3: Wait 400ms
- Give up after 3 attempts
```

**Interview Tip**: Walk through failure scenarios: "What happens if this database goes down? If this API is slow? If this datacenter loses power?"

---

### 3. Separation of Concerns

**Principle**: Divide system into distinct components with specific responsibilities.

**Benefits**:
- Easier to understand and maintain
- Can scale components independently
- Can change implementation without affecting others
- Better fault isolation

**Layers**:
```
┌─────────────────────────────────┐
│   Presentation Layer            │  (UI, API Gateway)
├─────────────────────────────────┤
│   Business Logic Layer          │  (Services, Domain Logic)
├─────────────────────────────────┤
│   Data Access Layer             │  (Repositories, ORMs)
├─────────────────────────────────┤
│   Data Layer                    │  (Databases, Caches)
└─────────────────────────────────┘
```

**Examples**:

#### MVC Pattern
- Model: Data and business logic
- View: Presentation
- Controller: Request handling

#### Microservices
- User Service: Authentication, profiles
- Order Service: Order management
- Payment Service: Payment processing
- Inventory Service: Stock management

**Anti-Pattern**: God Object / Big Ball of Mud
```
Bad: Single service doing everything
- User management
- Orders
- Payments
- Inventory
- Recommendations
- Analytics
```

---

### 4. Loose Coupling, High Cohesion

**Loose Coupling**: Components depend minimally on each other

**High Cohesion**: Related functionality grouped together

**Techniques**:

#### Event-Driven Architecture
```
Tight Coupling:
Order Service → calls → Inventory Service
              → calls → Payment Service
              → calls → Email Service

Loose Coupling:
Order Service → publishes "OrderPlaced" event
              ↓
           [Event Bus]
              ↓
   ┌──────────┼──────────┐
   ↓          ↓          ↓
Inventory  Payment    Email
Service    Service    Service
```

#### Interface-Based Design
```
interface PaymentProcessor {
    processPayment(amount, method)
}

class StripePaymentProcessor implements PaymentProcessor
class PayPalPaymentProcessor implements PaymentProcessor

// Easy to swap implementations
```

#### API Contracts
```
RESTful APIs with versioning:
/api/v1/users
/api/v2/users  (backward compatible or separate)

Benefits:
- Services evolve independently
- No breaking changes
```

---

### 5. Data Locality

**Principle**: Keep data close to where it's processed to minimize latency.

**Strategies**:

#### Caching
```
User in US East:
Without Cache: 200ms (round trip to database in US West)
With Regional Cache: 5ms (from nearby cache)
```

#### CDN for Static Content
```
User in Tokyo:
Without CDN: 300ms (from US origin server)
With CDN: 20ms (from Tokyo edge server)
```

#### Data Partitioning by Geography
```
US users → US database
EU users → EU database
APAC users → APAC database

Benefits:
- Lower latency
- Data sovereignty compliance
```

#### Denormalization
```
Normalized:
SELECT users.*, orders.*, items.*
FROM users
JOIN orders ON users.id = orders.user_id
JOIN items ON orders.id = items.order_id

Denormalized:
SELECT * FROM order_details  // Pre-joined data
```

**Trade-off**: Storage vs Performance

---

### 6. Idempotency

**Principle**: Operations produce same result when executed multiple times.

**Why Critical**:
- Network failures cause retries
- Message queues may deliver duplicates
- Prevents double-charging, duplicate records

**Implementation Patterns**:

#### Idempotency Keys
```
POST /api/charges
{
  "amount": 100,
  "idempotency_key": "uuid-123-456"
}

Server tracks processed keys:
1. Check if key exists → Return cached result
2. If new → Process and cache result
3. Return result
```

#### Natural Idempotency
```
Idempotent:
PUT /users/123 {"name": "John"}  // Set name to John (always same result)
DELETE /users/123                // Delete user (deleting again is safe)

Not Idempotent:
POST /users {"name": "John"}     // Creates new user each time
POST /accounts/123/debit {"amount": 100}  // Debits $100 each time
```

#### Check-Before-Write
```
UPDATE accounts
SET balance = balance - 100
WHERE id = 123 AND balance >= 100 AND last_transaction_id != 'tx-456'
```

---

### 7. Asynchronous Processing

**Principle**: Decouple time-sensitive operations from background work.

**Benefits**:
- Improved response times
- Better resource utilization
- Resilience to traffic spikes
- Temporal decoupling

**Use Cases**:
- Email sending
- Image processing
- Report generation
- Data aggregation
- Batch operations

**Patterns**:

#### Task Queue
```
User uploads image:
1. Accept upload → Return 202 Accepted (< 100ms)
2. Queue task: Resize, compress, generate thumbnails
3. Process asynchronously
4. Notify user when complete

Benefits:
- User doesn't wait for processing
- Can retry failed tasks
- Can scale workers independently
```

#### Message Queue
```
Order Service publishes "OrderPlaced":
  → Inventory Service (decrement stock)
  → Email Service (send confirmation)
  → Analytics Service (track metrics)
  → Shipping Service (prepare shipping)

All consumers process independently
```

**When NOT to Use Async**:
- User needs immediate result (payment authorization)
- Operations must complete before proceeding
- Strong consistency required

---

### 8. Design for Observability

**Principle**: Build systems that expose their internal state for monitoring and debugging.

**Three Pillars**:

#### 1. Metrics
```
What to Track:
- Request rate (requests/second)
- Error rate (% of failed requests)
- Latency (p50, p95, p99)
- Saturation (CPU, memory, disk usage)

Tools: Prometheus, Datadog, CloudWatch
```

#### 2. Logging
```
Structured Logging:
{
  "timestamp": "2025-11-14T10:30:00Z",
  "level": "ERROR",
  "service": "payment-service",
  "trace_id": "abc123",
  "user_id": "user456",
  "message": "Payment failed",
  "error_code": "INSUFFICIENT_FUNDS"
}

Tools: ELK Stack, Splunk, CloudWatch Logs
```

#### 3. Tracing
```
Distributed Tracing:
Request ID: abc-123

[API Gateway] → 5ms
  └→ [User Service] → 10ms
      └→ [Database] → 30ms
  └→ [Order Service] → 50ms
      └→ [Inventory Service] → 20ms
      └→ [Payment Service] → 100ms
          └→ [Stripe API] → 95ms

Identify: Payment Service slowest due to Stripe

Tools: Jaeger, Zipkin, AWS X-Ray
```

**Interview Tip**: Always mention observability when discussing system design. Show awareness of operational concerns.

---

### 9. Security by Design

**Principle**: Build security into architecture from the start.

**Key Practices**:

#### Defense in Depth
```
Multiple Security Layers:
1. Network: Firewall, VPC, security groups
2. Application: Authentication, authorization
3. Data: Encryption at rest and in transit
4. Access: MFA, least privilege IAM
```

#### Principle of Least Privilege
```
Bad:
Service has full database access (SELECT, INSERT, UPDATE, DELETE, DROP)

Good:
Read Service: SELECT only
Write Service: SELECT, INSERT, UPDATE
Admin Service: Full access with MFA
```

#### Input Validation
```
Always validate:
- Type checking
- Range checking
- Format validation
- Sanitization (prevent injection)

Defense points:
- Client side (UX)
- API Gateway (first line of defense)
- Service layer (authoritative validation)
```

#### Secrets Management
```
Bad:
Hardcoded passwords in code
Environment variables in plain text

Good:
AWS Secrets Manager
HashiCorp Vault
Encrypted environment variables
```

---

### 10. Optimize for Cost

**Principle**: Design with cost-efficiency in mind.

**Strategies**:

#### Right-Sizing
```
Don't over-provision:
- Monitor actual usage
- Scale down unused resources
- Use auto-scaling
```

#### Storage Tiering
```
Hot Data (frequent access):
- SSD storage
- In-memory cache
- Higher cost

Cold Data (infrequent access):
- S3 Glacier
- Compressed formats
- Lower cost

Example: Move data older than 90 days to cold storage
```

#### Reserved Capacity
```
Predictable workload:
- Reserve instances (30-70% discount)
- Savings plans

Variable workload:
- Spot instances (up to 90% discount)
- Auto-scaling on-demand
```

#### Serverless for Variable Load
```
Traditional:
Always-on servers, pay even when idle

Serverless:
Pay per request, scales to zero

Good for: APIs with variable traffic, batch jobs
```

---

### 11. Design for Scalability

**Principle**: System should handle growth without major rearchitecture.

**Dimensions of Scale**:

#### Request Volume
```
Strategy: Horizontal scaling, load balancing
Example: Add more web servers as traffic grows
```

#### Data Volume
```
Strategy: Database sharding, partitioning
Example: Partition users by user_id ranges or geography
```

#### Computational Complexity
```
Strategy: Async processing, distributed computing
Example: Map-Reduce for large-scale data processing
```

**Scalability Patterns**:

#### Stateless Services
```
Makes horizontal scaling easy:
- Any request can go to any server
- No session affinity needed
- Easy to add/remove servers

Store state externally:
- Database for persistent state
- Cache for session state
```

#### Database Read Replicas
```
Read-Heavy Workload:
[Primary DB] ← Writes
    ↓
[Replica 1] ← Reads
[Replica 2] ← Reads
[Replica 3] ← Reads

Scales read capacity linearly
```

#### Caching
```
Cache hit = fast response, low load on database
Cache miss = slow response, high load on database

Optimize for high cache hit ratio (>90%)
```

---

### 12. Design for Maintainability

**Principle**: Code and architecture should be easy to understand and modify.

**Practices**:

#### Clear Abstractions
```
Good:
userService.createUser(userData)
paymentService.processPayment(amount)

Bad:
doStuff(param1, param2, flag1, flag2)
```

#### Documentation
```
Essential Documentation:
- Architecture diagrams
- API specifications
- Runbooks for operations
- Decision records (why certain choices made)
```

#### Automated Testing
```
Test Pyramid:
       /\
      /E2E\
     /─────\
    /Integ-\
   /─ration\
  /──────────\
 /Unit Tests \
/─────────────\

Most tests should be fast unit tests
```

#### Continuous Integration/Deployment
```
Automated Pipeline:
Code commit → Tests → Build → Deploy to staging → Tests → Deploy to prod

Benefits:
- Fast feedback
- Reduced manual errors
- Easier rollbacks
```

---

## Interview Application

### Framework for Using Principles

1. **State Requirements First**
   - "We need to handle 10K requests/second"
   - "99.9% availability required"
   - "Sub-100ms latency target"

2. **Apply Relevant Principles**
   - "I'll design stateless services for easy horizontal scaling"
   - "Use circuit breakers to handle dependency failures"
   - "Implement caching for data locality"

3. **Explain Trade-offs**
   - "Eventual consistency gives us better availability"
   - "Denormalization improves read performance but complicates writes"

4. **Justify Decisions**
   - "Given read-heavy workload, I'm prioritizing read optimization"
   - "Since failures are inevitable, I'm designing for graceful degradation"

### Common Interview Scenarios

**Scenario 1**: "Design a system handling 100K requests/second"
```
Apply:
- Horizontal scaling (scalability)
- Load balancing (scalability)
- Caching (data locality)
- Async processing (performance)
- Circuit breakers (failure handling)
```

**Scenario 2**: "Design a payment system"
```
Apply:
- Strong consistency (correctness)
- Idempotency (reliability)
- Security by design (PCI compliance)
- Observability (audit trail)
- Design for failure (no lost payments)
```

**Scenario 3**: "Design a social media feed"
```
Apply:
- Eventual consistency (acceptable trade-off)
- Caching heavily (performance)
- Async processing (post ingestion)
- Horizontal scaling (user growth)
```

---

## Key Takeaways

1. **Start Simple**: Don't over-engineer from day one
2. **Expect Failure**: Design for it, not around it
3. **Think Long-term**: Maintainability matters
4. **Be Pragmatic**: Apply principles based on actual requirements
5. **Know Trade-offs**: Every principle has costs

---

## Practice Exercise

Design a URL shortener applying these principles:
- Which principles are most relevant?
- How would you apply them?
- What trade-offs would you make?

**Think about**:
- Scalability (millions of URLs)
- Availability (always accessible)
- Performance (low latency redirects)
- Security (no malicious URLs)
- Observability (track clicks)
