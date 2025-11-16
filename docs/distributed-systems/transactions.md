# Distributed Transactions

## The Distributed Transaction Problem

**Definition**: Executing a transaction that spans multiple databases/services while maintaining ACID properties.

**Why It's Hard**:
- Network failures between participants
- Partial failures (some nodes succeed, others fail)
- No global transaction coordinator
- Maintaining consistency across systems

**Real-World Example**:
```
Transfer $100 from Account A (Bank DB1) to Account B (Bank DB2):
- Debit $100 from A in DB1
- Credit $100 to B in DB2

Either BOTH succeed or BOTH fail (atomicity)
```

---

## ACID in Distributed Systems

### Traditional ACID (Single Database)

**Atomicity**: All or nothing
**Consistency**: Valid state before and after
**Isolation**: Concurrent transactions don't interfere
**Durability**: Committed data persists

### Challenges in Distributed Systems

```
Single DB:
[Transaction] → [Database] → Commit/Abort

Distributed:
[Transaction] → [DB1, DB2, DB3] → How to coordinate?
```

**Problems**:
1. Partial failures
2. Network delays/partitions
3. No shared memory or clock
4. Coordinating commit across nodes

---

## Two-Phase Commit (2PC)

### Protocol Overview

**Roles**:
- **Coordinator**: Manages transaction
- **Participants**: Execute local transactions

**Phases**:
1. **Prepare Phase**: Can you commit?
2. **Commit Phase**: Actually commit (or abort)

### Detailed Flow

```
Happy Path:

Coordinator                Participant 1        Participant 2
    |                           |                    |
    |--- BEGIN TRANSACTION ---->|                    |
    |--- BEGIN TRANSACTION -------------------->     |
    |                           |                    |
    | (Application executes local operations)        |
    |                           |                    |
    |--- PREPARE -------------->|                    |
    |--- PREPARE ------------------------------>     |
    |                           |                    |
    |                      [Lock resources]          |
    |                      [Write to log]            |
    |                           |                    |
    |<-- YES (prepared) --------|                    |
    |<-- YES (prepared) ------------------------|    |
    |                           |                    |
    | (All participants ready)  |                    |
    |                           |                    |
    |--- COMMIT --------------->|                    |
    |--- COMMIT ---------------------------->        |
    |                           |                    |
    |                      [Apply changes]           |
    |                      [Release locks]           |
    |                           |                    |
    |<-- ACK -------------------|                    |
    |<-- ACK -----------------------------------|    |
    |                           |                    |
    |--- TRANSACTION COMPLETE --|                    |
```

### Failure Scenarios

**Participant Votes No**:
```
Coordinator               Participant 1        Participant 2
    |                          |                    |
    |--- PREPARE ------------->|                    |
    |--- PREPARE ---------------------------->      |
    |                          |                    |
    |<-- YES ------------------|                    |
    |<-- NO -----------------------------------|    |
    |                          |                    |
    |--- ABORT --------------->|                    |
    |--- ABORT ------------------------------>      |
    |                          |                    |
    | (All rollback)           |                    |
```

**Coordinator Crashes After Prepare**:
```
Coordinator               Participant 1        Participant 2
    |                          |                    |
    |--- PREPARE ------------->|                    |
    |--- PREPARE ---------------------------->      |
    |                          |                    |
    |<-- YES ------------------|                    |
    |<-- YES ----------------------------------|    |
    |                          |                    |
    X (CRASH)                  |                    |
                               |                    |
                          [BLOCKED!]           [BLOCKED!]
                     (Resources locked)    (Resources locked)
                     (Cannot commit)       (Cannot commit)
                     (Cannot abort)        (Cannot abort)
```

### The Blocking Problem

**Critical Issue**: Participants can be blocked indefinitely

```
Scenario:
1. Coordinator sends PREPARE
2. Participants vote YES, lock resources, wait
3. Coordinator crashes before sending COMMIT/ABORT
4. Participants are stuck:
   - Don't know if others voted YES or NO
   - Can't unilaterally decide to commit or abort
   - Resources remain locked
   - System unavailable
```

**Timeout Solution**:
```
Participant timeout after PREPARE:
- If no COMMIT/ABORT received within timeout
- Contact other participants to determine decision
- If all voted YES and coordinator down: elect new coordinator
- If any voted NO: abort

Problem: What if network partition prevents communication?
Still blocked!
```

### 2PC in Practice

**When to Use**:
- Strong consistency required
- Controlled environment (single datacenter)
- Limited number of participants (< 10)
- Can tolerate blocking

**Examples**:
- **Database transactions across shards** (MySQL XA)
- **Microservices transactions** (rare, usually avoided)
- **Message queue + database** (transactional outbox pattern)

**Optimizations**:
- **Presumed Abort**: If coordinator crashes, assume abort
- **Coordinator Failover**: Backup coordinator takes over
- **Persistent Logging**: Recovery from crash using logs

---

## Three-Phase Commit (3PC)

### Motivation

**Goal**: Eliminate blocking problem of 2PC

### Protocol

**Phases**:
1. **CanCommit**: Can you commit?
2. **PreCommit**: Prepare to commit
3. **DoCommit**: Actually commit

```
Coordinator               Participant 1        Participant 2
    |                          |                    |
    |--- CAN-COMMIT ---------->|                    |
    |--- CAN-COMMIT ------------------------->      |
    |                          |                    |
    |<-- YES ------------------|                    |
    |<-- YES ----------------------------------|    |
    |                          |                    |
    |--- PRE-COMMIT ---------->|                    |
    |--- PRE-COMMIT ------------------------->      |
    |                          |                    |
    |                     [Lock resources]          |
    |                     [Ready to commit]         |
    |                          |                    |
    |<-- ACK ------------------|                    |
    |<-- ACK ----------------------------------|    |
    |                          |                    |
    |--- DO-COMMIT ----------->|                    |
    |--- DO-COMMIT --------------------------->     |
    |                          |                    |
    |                     [Commit!]                 |
```

### Key Difference from 2PC

**Timeout Behavior**:
```
2PC: Participant blocks if coordinator crashes after PREPARE
3PC: Participant can proceed after timeout

After PRE-COMMIT timeout:
- If participant receives PRE-COMMIT: commit
- If timeout without PRE-COMMIT: abort
- Participants can make progress independently
```

### Limitations

**Still Not Perfect**:
- Only works in **asynchronous** networks with failure detection
- **Network partitions** can still cause issues
- More complex implementation
- More network round trips (higher latency)

**Rarely Used in Practice**:
- Complexity not worth benefits
- Most systems use eventual consistency instead
- Or use consensus algorithms (Paxos, Raft)

---

## Saga Pattern

### Concept

**Alternative to Distributed Transactions**: Break into local transactions with compensation

**Philosophy**:
- Long-running transactions
- Each step commits locally
- If failure: execute compensating transactions (undo)

### Choreography-Based Saga

```
Book Flight Saga:

Step 1: Reserve Flight
  Service: Flight Booking
  Success → Emit "FlightReserved" event

Step 2: Reserve Hotel
  Listens: "FlightReserved" event
  Service: Hotel Booking
  Success → Emit "HotelReserved" event

Step 3: Reserve Car
  Listens: "HotelReserved" event
  Service: Car Rental
  Success → Emit "CarReserved" event → SAGA COMPLETE

Failure in Step 2 (Hotel):
  Emit "HotelReservationFailed" event

Compensation:
  Flight service listens to "HotelReservationFailed"
  Execute: Cancel flight reservation
```

**Flow Diagram**:
```
[Flight Service] --FlightReserved--> [Hotel Service] --HotelReserved--> [Car Service]
       ↑                                     |
       |                                     |
       +------Cancel Flight <-----------------+ (if hotel fails)
```

### Orchestration-Based Saga

```
Saga Orchestrator controls flow:

Orchestrator:
1. Call Flight.reserve()
   → Success: Continue
   → Failure: End saga

2. Call Hotel.reserve()
   → Success: Continue
   → Failure: Call Flight.cancel(), End saga

3. Call Car.reserve()
   → Success: Saga complete
   → Failure: Call Hotel.cancel(), Flight.cancel()
```

**Flow Diagram**:
```
                    [Orchestrator]
                          |
        +-----------------+-----------------+
        ↓                 ↓                 ↓
   [Flight]          [Hotel]            [Car]
   reserve()         reserve()          reserve()
   cancel()          cancel()           cancel()
```

### Choreography vs Orchestration

| Choreography | Orchestration |
|--------------|---------------|
| Decentralized | Centralized orchestrator |
| Event-driven | Direct calls |
| Loose coupling | Tight coupling to orchestrator |
| Hard to track | Easy to track/debug |
| No single point of failure | Orchestrator is critical |
| Complex for many steps | Scales to many steps |

### Compensation Logic

**Example: E-commerce Order**:
```
Forward Transactions:
T1: Create Order          → Compensation: Delete Order
T2: Reserve Inventory     → Compensation: Release Inventory
T3: Charge Payment        → Compensation: Refund Payment
T4: Ship Product          → Compensation: Initiate Return

Failure at T3 (payment fails):
Execute compensations in reverse:
C2: Release Inventory
C1: Delete Order
```

**Key Principle**: Compensations must be **idempotent**

```
Idempotent Compensation:
cancel_order(order_id)
- Call once: Order cancelled
- Call twice: Still cancelled (no error)
- Call N times: Same result
```

### Challenges

**1. Lack of Isolation**:
```
Saga Step 1: Debit $100 from Account A (commits)
Other Transaction: Reads Account A (sees debit!)
Saga Step 2: Fails → Compensate (credit $100 back)

Problem: Other transaction saw intermediate state
```

**Solution: Semantic Lock**:
```
Mark Account A as "pending" during saga
Other transactions see this and wait/skip
```

**2. Compensation Failures**:
```
What if compensation itself fails?

Solutions:
- Retry compensation until success
- Manual intervention
- Alert/monitoring
- Make compensations idempotent
```

**3. Eventual Consistency**:
```
Sagas don't provide immediate consistency
Time window where data is inconsistent
Application must handle this
```

### Saga in Practice

**Use Cases**:
- **Microservices transactions**
- **Long-running business processes**
- **Cross-service workflows**

**Examples**:
- **E-commerce checkout** (order, payment, shipping)
- **Travel booking** (flight, hotel, car)
- **Uber ride** (match driver, start trip, payment)

**Technologies**:
- **Camunda**: Workflow orchestration
- **Netflix Conductor**: Orchestrator
- **Temporal**: Workflow engine
- **Custom event-driven** (Kafka, RabbitMQ)

---

## Avoiding Distributed Transactions

### Strategy 1: Single Database

```
Keep related data together:

Instead of:
  User Service → User DB
  Order Service → Order DB
  (Need distributed transaction)

Consider:
  Order Service → Single DB (users + orders)
  (Local ACID transactions)
```

### Strategy 2: Eventual Consistency

```
Accept temporary inconsistency:

Order placed → Inventory decremented (eventually)
- Inventory service processes queue
- Retries on failure
- Eventually consistent
```

### Strategy 3: Idempotent Operations

```
Make operations safe to retry:

charge_payment(order_id, amount, idempotency_key)
- First call: Charge succeeds
- Retry with same key: Returns success (already charged)
- No duplicate charge
```

### Strategy 4: Careful Service Boundaries

```
Design services to minimize cross-service transactions:

Bad:
  CreateOrder requires: Users, Products, Inventory, Payments
  (4-way distributed transaction)

Better:
  CreateOrder reads: Users, Products (no transaction needed)
  CreateOrder writes: Orders DB only (local transaction)
  Async: Update inventory, process payment
```

---

## Interview Q&A

**Q: "How would you handle payment + inventory in an e-commerce system?"**

**Answer**:
```
Use Saga pattern with careful ordering:

1. Reserve Inventory (local transaction)
   → If fail: Return error to user

2. Create Order (local transaction)
   → If fail: Compensate - Release inventory

3. Charge Payment (external API)
   → If fail: Compensate - Release inventory, Delete order
   → Retry with idempotency key

4. Confirm Order
   → If fail: Compensate - Refund, Release inventory, Delete order

Why this order?
- Fail fast (check inventory first)
- Payment charged only after inventory confirmed
- Idempotent payment (safe to retry)
- Clear compensation path

Alternative: Eventual Consistency
- Create order immediately
- Process payment async
- If payment fails: Cancel order, notify user
- Trade-off: User sees "pending" state
```

**Q: "Why not use 2PC for microservices?"**

**Answer**:
```
2PC is problematic for microservices:

1. Blocking Problem:
   - Coordinator crash → services locked
   - Reduced availability
   - Violates microservice independence

2. Tight Coupling:
   - Services must support 2PC protocol
   - Harder to evolve independently

3. Performance:
   - Multiple round trips
   - Holding locks across network
   - Latency adds up

4. Doesn't Handle Network Partitions:
   - Can still block during partition
   - CAP theorem: Can't have both consistency and availability

Better Alternatives:
- Saga pattern (eventual consistency)
- Event sourcing (replay events)
- Careful service boundaries (minimize distributed transactions)
- CQRS (separate read/write models)

Use 2PC only when:
- Single datacenter
- Controlled environment
- Strong consistency absolutely required
- Example: Database sharding within same system
```

**Q: "Explain the difference between 2PC and Saga"**

**Answer**:
```
Two-Phase Commit (2PC):
- Atomic: All or nothing across services
- Blocking: Participants wait for coordinator
- Strong consistency: Immediate
- Use case: Database transactions
- Problem: Availability suffers

Saga:
- Sequence of local transactions
- Non-blocking: Each step commits immediately
- Eventual consistency: Temporary inconsistency
- Compensation: Undo on failure
- Use case: Long-running workflows
- Trade-off: Complexity of compensation logic

Example - Transfer money:

2PC:
  Coordinator → Debit A, Credit B
  Both locked until commit
  If failure: Both rollback
  Atomic, but blocks

Saga:
  Step 1: Debit A (commit)
  Step 2: Credit B (commit)
  If Step 2 fails: Compensate (credit A back)
  Non-blocking, but intermediate state visible

Choose 2PC for: Single datacenter, ACID critical
Choose Saga for: Microservices, long workflows, high availability
```

---

## Key Takeaways

1. **Distributed Transactions Are Hard**: Network failures, partial failures, coordination challenges
2. **2PC Has Blocking Problem**: Coordinator failure can block participants indefinitely
3. **3PC Reduces Blocking**: But still vulnerable to network partitions
4. **Saga Pattern for Microservices**: Eventual consistency with compensating transactions
5. **Best Practice**: Avoid distributed transactions when possible through design
6. **Choose Based on Needs**: Strong consistency vs availability vs complexity

---

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 7, 9
- Saga Pattern - Original paper by Hector Garcia-Molina
- "Building Microservices" - Sam Newman (Chapter on Distributed Transactions)
- Google Spanner Paper (TrueTime for distributed transactions)
- Pat Helland: "Life beyond Distributed Transactions"
- Temporal.io documentation (Saga orchestration)
- Apache Camel Saga implementation
