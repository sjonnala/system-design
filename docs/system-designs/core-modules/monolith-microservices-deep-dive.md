# Monoliths vs. Microservices: Deep Dive

## Contents

- [Monoliths vs. Microservices: Deep Dive](#monoliths-vs-microservices-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [The Conway's Law Foundation](#1-the-conways-law-foundation)
    - [Data Architecture: The Critical Decision](#2-data-architecture-the-critical-decision)
    - [Communication Patterns: Sync vs Async](#3-communication-patterns-sync-vs-async)
    - [Deployment & DevOps Complexity](#4-deployment--devops-complexity)
    - [Failure Modes & Distributed System Challenges](#5-failure-modes--distributed-system-challenges)
    - [Migration Patterns: Strangler Fig](#6-migration-patterns-strangler-fig)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification](#1-requirements-clarification)
        - [Architecture Decision Criteria](#2-architecture-decision-criteria)
        - [High-Level Design](#3-high-level-design)
        - [Deep Dives](#4-deep-dives)
    - [MIND MAP: MONOLITH VS MICROSERVICES](#mind-map-monolith-vs-microservices)
    - [DECISION MATRIX](#decision-matrix)

## Core Mental Model

```text
Complexity = Essential Complexity + Accidental Complexity

Monolith:  Low Accidental Complexity, High Change Coupling
Microservices: High Accidental Complexity, Low Change Coupling

Where:
- Essential Complexity: Inherent to the business problem
- Accidental Complexity: From our architecture choices
- Change Coupling: How much changing A forces changing B
```

**The Fundamental Trade-off:**
```
┌──────────────────────────────────────────────────────────┐
│  MONOLITH                    vs          MICROSERVICES   │
├──────────────────────────────────────────────────────────┤
│  ✓ Simpler deployment                  ✗ Complex ops    │
│  ✓ Strong consistency (ACID)           ✗ Eventual       │
│  ✓ Easy refactoring                    ✗ Distributed    │
│  ✓ Lower latency (in-proc)             ✗ Network calls  │
│  ✓ Simpler debugging                   ✗ Distributed    │
│                                             tracing      │
│  ✗ Tight coupling                      ✓ Independence   │
│  ✗ Scaling constraints                 ✓ Fine-grained   │
│  ✗ Technology lock-in                  ✓ Polyglot       │
│  ✗ Deploy all or nothing               ✓ Independent    │
│  ✗ Large team coordination             ✓ Team autonomy  │
└──────────────────────────────────────────────────────────┘
```

**🎓 PROFESSOR**: This is fundamentally about **modularity and boundaries**:

```
Modularity Spectrum:
Functions → Classes → Modules → Libraries → Services → Systems

Key property: As you move right, boundaries become HARDER
- Function call: nanoseconds, same process
- HTTP call: milliseconds, network failures possible
- Service call: Can't share transactions, eventual consistency
```

**🏗️ ARCHITECT**: Start with a **well-structured monolith**. Extract microservices when:
1. Team size > 10-12 (communication overhead)
2. Different scaling needs (CPU-bound vs IO-bound)
3. Need independent deployment velocity
4. Proven domain boundaries exist

**Anti-pattern**: Starting with microservices for a greenfield project is premature optimization.

---

## 1. The Conway's Law Foundation

🎓 **PROFESSOR**: Conway's Law states:
> "Organizations design systems that mirror their communication structure"

**Mathematical formulation:**
```
System Interfaces ≈ Team Communication Paths

If teams are organized as:
- 3 teams → 3-tier architecture (UI, Business, Data)
- Functional silos → Service-oriented architecture
- Product teams → Domain-driven microservices
```

🏗️ **ARCHITECT**: Real-world manifestation:

```java
// MONOLITH STRUCTURE (Single team, shared codebase)
com.company.ecommerce/
├── controller/
│   ├── ProductController.java      // All controllers together
│   ├── OrderController.java
│   ├── PaymentController.java
├── service/
│   ├── ProductService.java         // All services together
│   ├── OrderService.java
│   ├── PaymentService.java
└── repository/
    ├── ProductRepository.java      // Shared database
    ├── OrderRepository.java
    ├── PaymentRepository.java

// Technical coupling: Easy to call OrderService from ProductService
// Ownership coupling: No clear boundaries, everyone touches everything
```

```java
// MICROSERVICES STRUCTURE (Separate teams, bounded contexts)

product-service/          // Team: Catalog Squad
├── api/
│   └── ProductAPI.java
├── domain/
│   └── Product.java
└── database/
    └── product_db (PostgreSQL)

order-service/           // Team: Checkout Squad
├── api/
│   └── OrderAPI.java
├── domain/
│   └── Order.java
└── database/
    └── order_db (PostgreSQL)

payment-service/         // Team: Payments Squad
├── api/
│   └── PaymentAPI.java
├── domain/
│   └── Payment.java
└── database/
    └── payment_db (PostgreSQL)

// Clear boundaries: Cannot call OrderService.java directly
// Must use HTTP/gRPC APIs, forcing explicit contracts
```

**Key Insight**: Microservices enforce team boundaries through technical constraints.

---

## 2. Data Architecture: The Critical Decision

🎓 **PROFESSOR**: The **shared database pattern** is what truly defines a monolith, not code structure.

```
┌─────────────────────────────────────────────────────────────┐
│  DATA COUPLING = STRONGEST FORM OF COUPLING                 │
│                                                              │
│  Shared DB: Changes to schema require coordinating ALL      │
│             services using that table                       │
│                                                              │
│  Database per Service: Each service owns its data           │
│                        Changes are localized                │
└─────────────────────────────────────────────────────────────┘
```

**The CAP Theorem Connection:**
```
Monolith (Shared DB):
- Consistency: ✓ (ACID transactions)
- Availability: ✓ (within single DB)
- Partition Tolerance: ✗ (single DB is a single point)

Microservices (Distributed):
- Consistency: ✗ (eventual consistency)
- Availability: ✓ (can survive service failures)
- Partition Tolerance: ✓ (services can be partitioned)
```

🏗️ **ARCHITECT**: Real scenario - **Order Processing**:

### Monolith with Shared Database:
```java
@Service
public class OrderService {

    @Autowired
    private ProductRepository productRepo;

    @Autowired
    private OrderRepository orderRepo;

    @Autowired
    private PaymentRepository paymentRepo;

    @Transactional  // ACID guarantees! Either all succeed or all fail
    public Order createOrder(OrderRequest request) {
        // 1. Check inventory
        Product product = productRepo.findById(request.getProductId());
        if (product.getStock() < request.getQuantity()) {
            throw new OutOfStockException();
        }

        // 2. Reserve inventory
        product.setStock(product.getStock() - request.getQuantity());
        productRepo.save(product);

        // 3. Create order
        Order order = new Order(request);
        orderRepo.save(order);

        // 4. Process payment
        Payment payment = paymentService.charge(request.getPaymentInfo());
        paymentRepo.save(payment);

        return order;
        // If ANY step fails, entire transaction rolls back
        // No orphaned data, no inconsistent state
    }
}
```

### Microservices with Database per Service:
```java
@Service
public class OrderService {

    private final ProductServiceClient productClient;
    private final PaymentServiceClient paymentClient;

    // NO @Transactional - can't span multiple databases!
    public Order createOrder(OrderRequest request) {
        String sagaId = UUID.randomUUID().toString();

        try {
            // 1. Reserve inventory (external HTTP call)
            ReservationResponse reservation = productClient.reserveInventory(
                request.getProductId(),
                request.getQuantity(),
                sagaId
            );

            // 2. Create order (local database)
            Order order = new Order(request, sagaId);
            order.setReservationId(reservation.getId());
            orderRepo.save(order);

            // 3. Process payment (external HTTP call)
            PaymentResponse payment = paymentClient.charge(
                request.getPaymentInfo(),
                sagaId
            );

            // 4. Commit saga
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepo.save(order);

            return order;

        } catch (Exception e) {
            // MANUAL COMPENSATION REQUIRED!
            compensateSaga(sagaId, e);
            throw new OrderFailedException(e);
        }
    }

    private void compensateSaga(String sagaId, Exception error) {
        // Rollback is manual and complex!
        // What if compensation fails? Need retry logic, idempotency...

        // Release inventory reservation
        try {
            productClient.releaseReservation(sagaId);
        } catch (Exception e) {
            // Log to dead letter queue for manual intervention
            deadLetterQueue.send(new CompensationFailed(sagaId, e));
        }

        // Refund payment if charged
        try {
            paymentClient.refund(sagaId);
        } catch (Exception e) {
            deadLetterQueue.send(new RefundFailed(sagaId, e));
        }
    }
}
```

**Interview Gold**: "The hardest part of microservices isn't writing services—it's managing distributed transactions. You lose ACID, gain eventual consistency, and must handle partial failures with Sagas or Event Sourcing."

---

## 3. Communication Patterns: Sync vs Async

🎓 **PROFESSOR**: **Fallacies of Distributed Computing** (Peter Deutsch, 1994):
```
1. The network is reliable          → FALSE
2. Latency is zero                  → FALSE
3. Bandwidth is infinite            → FALSE
4. The network is secure            → FALSE
5. Topology doesn't change          → FALSE
6. There is one administrator       → FALSE
7. Transport cost is zero           → FALSE
8. The network is homogeneous       → FALSE
```

**Latency Comparison:**
```
Function call:       ~1 nanosecond
Shared memory:       ~100 nanoseconds
HTTP call (same DC): ~1-10 milliseconds (10,000x slower!)
HTTP call (cross DC): ~50-200 milliseconds
```

🏗️ **ARCHITECT**: Communication pattern decision tree:

```
┌────────────────────────────────────────────────────┐
│  SYNCHRONOUS (REST/gRPC)                           │
├────────────────────────────────────────────────────┤
│  ✓ Simple request-response                         │
│  ✓ Immediate consistency                           │
│  ✓ Easy to debug                                   │
│                                                     │
│  ✗ Coupling: Caller blocked until response         │
│  ✗ Cascading failures (if service B down, A fails) │
│  ✗ Latency adds up (A→B→C = sum of latencies)      │
└────────────────────────────────────────────────────┘

┌────────────────────────────────────────────────────┐
│  ASYNCHRONOUS (Message Queue/Event Bus)            │
├────────────────────────────────────────────────────┤
│  ✓ Decoupling: Producer doesn't know consumers     │
│  ✓ Resilience: Queue buffers during downtime       │
│  ✓ Scalability: Multiple consumers can process     │
│                                                     │
│  ✗ Eventual consistency                            │
│  ✗ Complexity: Message ordering, deduplication     │
│  ✗ Harder debugging: No stack trace across services│
└────────────────────────────────────────────────────┘
```

### Code Example: Synchronous REST

```java
// Order Service calling Payment Service via REST
@Service
public class OrderService {

    private final RestTemplate restTemplate;

    @CircuitBreaker(name = "payment-service",
                    fallbackMethod = "paymentFallback")
    @Retry(maxAttempts = 3, backoff = @Backoff(delay = 1000))
    public Order createOrder(OrderRequest request) {

        // Synchronous HTTP call - we wait for response
        PaymentResponse payment = restTemplate.postForObject(
            "http://payment-service/api/v1/payments",
            request.getPaymentInfo(),
            PaymentResponse.class
        );

        // Order service is BLOCKED until payment service responds
        // If payment service is slow, we're slow
        // If payment service is down, we fail (unless fallback works)

        Order order = new Order(request);
        order.setPaymentId(payment.getId());
        return orderRepo.save(order);
    }

    // Fallback: What to do when payment service is down?
    public Order paymentFallback(OrderRequest request, Exception e) {
        // Option 1: Fail fast
        throw new ServiceUnavailableException("Payment service down");

        // Option 2: Degrade gracefully (process payment async later)
        Order order = new Order(request);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        orderRepo.save(order);

        // Emit event for async payment processing
        eventPublisher.publish(new PaymentPendingEvent(order.getId()));

        return order;
    }
}
```

### Code Example: Asynchronous Event-Driven

```java
// Order Service publishes events, doesn't call Payment Service directly
@Service
public class OrderService {

    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public Order createOrder(OrderRequest request) {

        // 1. Create order in PENDING state
        Order order = new Order(request);
        order.setStatus(OrderStatus.PENDING);
        orderRepo.save(order);

        // 2. Publish event (fire and forget)
        OrderCreatedEvent event = new OrderCreatedEvent(
            order.getId(),
            request.getPaymentInfo()
        );

        kafkaTemplate.send("order-events", event);

        // 3. Return immediately (don't wait for payment)
        return order;
        // Payment happens asynchronously by Payment Service consumer
    }
}

// Payment Service listens to events
@Service
public class PaymentEventHandler {

    @KafkaListener(topics = "order-events")
    public void handleOrderCreated(OrderCreatedEvent event) {

        // Process payment asynchronously
        Payment payment = paymentGateway.charge(event.getPaymentInfo());

        // Publish result event
        if (payment.isSuccessful()) {
            kafkaTemplate.send("payment-events",
                new PaymentSuccessEvent(event.getOrderId(), payment.getId())
            );
        } else {
            kafkaTemplate.send("payment-events",
                new PaymentFailedEvent(event.getOrderId(), payment.getError())
            );
        }
    }
}

// Order Service reacts to payment events
@Service
public class OrderEventHandler {

    @KafkaListener(topics = "payment-events")
    public void handlePaymentSuccess(PaymentSuccessEvent event) {
        Order order = orderRepo.findById(event.getOrderId());
        order.setStatus(OrderStatus.CONFIRMED);
        order.setPaymentId(event.getPaymentId());
        orderRepo.save(order);

        // Trigger next step in saga
        kafkaTemplate.send("order-events",
            new OrderConfirmedEvent(order.getId())
        );
    }

    @KafkaListener(topics = "payment-events")
    public void handlePaymentFailure(PaymentFailedEvent event) {
        Order order = orderRepo.findById(event.getOrderId());
        order.setStatus(OrderStatus.FAILED);
        order.setFailureReason(event.getError());
        orderRepo.save(order);
    }
}
```

**Key Difference:**
- Synchronous: Order service waits for payment service → tight coupling
- Asynchronous: Order service publishes event and continues → loose coupling, but eventual consistency

---

## 4. Deployment & DevOps Complexity

🎓 **PROFESSOR**: **State space explosion in distributed systems**:

```
Monolith: 1 service with 3 states (starting, running, stopped) = 3 states

Microservices: N services with 3 states each = 3^N possible states
- 5 services: 243 possible states
- 10 services: 59,049 possible states
- 20 services: 3,486,784,401 possible states

This is why distributed systems are exponentially harder to debug.
```

🏗️ **ARCHITECT**: Deployment complexity comparison:

### Monolith Deployment

```yaml
# Simple deployment - one artifact
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ecommerce-monolith
spec:
  replicas: 3
  template:
    spec:
      containers:
      - name: app
        image: ecommerce:v1.2.3
        ports:
        - containerPort: 8080
        env:
        - name: DATABASE_URL
          value: jdbc:postgresql://db:5432/ecommerce

        resources:
          requests:
            memory: "2Gi"      # One size for all features
            cpu: "1000m"

        livenessProbe:         # Single health check
          httpGet:
            path: /actuator/health
            port: 8080

# Pros:
# - Deploy once, all features go live together
# - Simple rollback: previous version had everything
# - One config, one image, one database connection

# Cons:
# - Must deploy entire app for small change
# - Downtime affects all features
# - Scaling is all-or-nothing
```

### Microservices Deployment

```yaml
# Complex deployment - orchestrate multiple services

# 1. Product Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 5              # Scale based on read-heavy load
  template:
    spec:
      containers:
      - name: product-service
        image: product-service:v2.1.0
        resources:
          requests:
            memory: "512Mi"    # Lightweight service
            cpu: "250m"

---
# 2. Order Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3              # Moderate scaling
  template:
    spec:
      containers:
      - name: order-service
        image: order-service:v1.5.2    # Different version!
        resources:
          requests:
            memory: "1Gi"      # Memory-intensive
            cpu: "500m"
        env:
        - name: PRODUCT_SERVICE_URL
          value: http://product-service:8080
        - name: PAYMENT_SERVICE_URL
          value: http://payment-service:8080

---
# 3. Payment Service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
spec:
  replicas: 10             # High availability requirement
  template:
    spec:
      containers:
      - name: payment-service
        image: payment-service:v3.0.1  # Totally different version!
        resources:
          requests:
            memory: "256Mi"    # CPU-bound
            cpu: "1000m"

# Pros:
# - Independent deployment: Update payment without touching product
# - Independent scaling: 10 payment instances, 3 order instances
# - Technology diversity: Different languages/frameworks possible

# Cons:
# - Version compatibility matrix: Which order-service works with which payment-service?
# - Deployment orchestration: What order to deploy? Blue-green for each?
# - Configuration explosion: 3 services × 3 environments = 9 configs
# - Distributed tracing required: Need Jaeger/Zipkin to debug issues
```

**DevOps Requirements Comparison:**

```
┌──────────────────────────────────────────────────────────────┐
│  TOOLING COMPLEXITY                                          │
├──────────────────────────────────────────────────────────────┤
│  MONOLITH:                                                   │
│  - 1 CI/CD pipeline                                          │
│  - 1 Docker image                                            │
│  - 1 database migration script                               │
│  - 1 health check endpoint                                   │
│  - Standard logging (log files)                              │
│                                                               │
│  MICROSERVICES:                                              │
│  - N CI/CD pipelines (one per service)                       │
│  - N Docker images to build/scan/store                       │
│  - N database schemas to migrate                             │
│  - N health checks to monitor                                │
│  - Centralized logging (ELK/Splunk) - REQUIRED               │
│  - Distributed tracing (Jaeger/Zipkin) - REQUIRED            │
│  - Service mesh (Istio/Linkerd) - Recommended                │
│  - API gateway (Kong/Ambassador) - REQUIRED                  │
│  - Service registry (Consul/Eureka) - REQUIRED               │
│  - Configuration management (Vault/ConfigMap) - REQUIRED     │
└──────────────────────────────────────────────────────────────┘
```

---

## 5. Failure Modes & Distributed System Challenges

🎓 **PROFESSOR**: **Byzantine Generals Problem** - distributed systems can fail in complex ways:

```
Failure Types:
1. Fail-stop: Service crashes (easy to detect)
2. Network partition: Service alive but unreachable
3. Byzantine failure: Service gives wrong results
4. Cascading failure: One failure triggers others

In monolith: Mainly fail-stop (process crashes)
In microservices: ALL of the above
```

🏗️ **ARCHITECT**: Real-world failure scenarios:

### Scenario 1: Cascading Failures (Synchronous Calls)

```java
// Service dependency chain: API Gateway → Order → Product → Inventory

// Without circuit breaker
@Service
public class ProductService {

    @Autowired
    private RestTemplate restTemplate;

    public Product getProduct(String id) {
        // Call inventory service (deep dependency)
        InventoryResponse inventory = restTemplate.getForObject(
            "http://inventory-service/api/v1/inventory/" + id,
            InventoryResponse.class
        );

        // If inventory service is slow (3 second timeout):
        // - Product service threads block waiting
        // - Thread pool exhaustion: 200 threads × 3s = can only handle 66 req/s
        // - Product service becomes slow
        // - Order service (caller) threads block
        // - Order service becomes slow
        // - API Gateway threads block
        // - ENTIRE SYSTEM GRINDS TO HALT

        return buildProduct(id, inventory);
    }
}

// With circuit breaker (resilience)
@Service
public class ResilientProductService {

    @CircuitBreaker(
        name = "inventory-service",
        fallbackMethod = "getProductFallback"
    )
    @Timeout(value = 1000, timeoutProperty = "1s")
    @Bulkhead(name = "inventory-service", maxConcurrentCalls = 10)
    public Product getProduct(String id) {
        InventoryResponse inventory = restTemplate.getForObject(
            "http://inventory-service/api/v1/inventory/" + id,
            InventoryResponse.class
        );

        return buildProduct(id, inventory);
    }

    // Circuit breaker opens after 50% failures
    // Subsequent calls fail fast (no waiting)
    // Fallback provides degraded experience
    public Product getProductFallback(String id, Exception e) {
        logger.warn("Inventory service unavailable, using cached data");

        // Return product without real-time inventory
        Product product = productCache.get(id);
        product.setInventoryStatus("Unknown (service unavailable)");
        product.setAvailableForPurchase(false);

        return product;
    }
}
```

### Scenario 2: Distributed Transaction Failure (Saga Pattern)

```java
// Booking system: Reserve flight + Reserve hotel + Charge payment
// Problem: What if payment fails after reservations succeed?

@Service
public class BookingOrchestratorService {

    private final FlightServiceClient flightService;
    private final HotelServiceClient hotelService;
    private final PaymentServiceClient paymentService;

    /**
     * Orchestration Saga: Coordinator knows all steps
     */
    public Booking createBooking(BookingRequest request) {
        String sagaId = UUID.randomUUID().toString();
        SagaState state = new SagaState(sagaId);

        try {
            // Step 1: Reserve flight
            FlightReservation flight = flightService.reserve(
                request.getFlight(), sagaId
            );
            state.recordStep("flight", flight.getId());

            // Step 2: Reserve hotel
            HotelReservation hotel = hotelService.reserve(
                request.getHotel(), sagaId
            );
            state.recordStep("hotel", hotel.getId());

            // Step 3: Charge payment
            Payment payment = paymentService.charge(
                request.getPaymentInfo(),
                flight.getAmount() + hotel.getAmount(),
                sagaId
            );
            state.recordStep("payment", payment.getId());

            // All steps succeeded
            state.markCompleted();
            return new Booking(sagaId, flight, hotel, payment);

        } catch (Exception e) {
            // Failure: Compensate in reverse order
            logger.error("Saga {} failed at step {}, compensating",
                        sagaId, state.getCurrentStep(), e);

            compensate(state);

            throw new BookingFailedException(
                "Failed to complete booking: " + e.getMessage()
            );
        }
    }

    private void compensate(SagaState state) {
        // Undo in reverse order (LIFO)
        List<String> completedSteps = state.getCompletedSteps();
        Collections.reverse(completedSteps);

        for (String step : completedSteps) {
            try {
                switch (step) {
                    case "payment":
                        paymentService.refund(state.getStepData(step));
                        break;
                    case "hotel":
                        hotelService.cancel(state.getStepData(step));
                        break;
                    case "flight":
                        flightService.cancel(state.getStepData(step));
                        break;
                }
            } catch (Exception e) {
                // Compensation failed! This is critical.
                // Must retry or send to dead letter queue
                logger.error("COMPENSATION FAILED for step {}: {}",
                            step, e.getMessage());

                // Store in DB for manual retry
                sagaFailureRepo.save(new SagaCompensationFailure(
                    state.getSagaId(),
                    step,
                    state.getStepData(step),
                    e.getMessage()
                ));
            }
        }
    }
}
```

**Interview Point**: "Saga pattern requires idempotent operations. If compensation fails and we retry, we can't double-refund. Need unique saga IDs and deduplication."

### Scenario 3: Network Partition (Split Brain)

```python
# Two instances of Order Service both think they're the leader
# Both accept writes to same order, creating conflict

class OrderService:

    def __init__(self):
        self.is_leader = False
        self.zookeeper = ZookeeperClient()

    def start(self):
        """Leader election using ZooKeeper"""
        try:
            # Try to create ephemeral node
            self.zookeeper.create("/leader", ephemeral=True)
            self.is_leader = True
            logger.info("I am the leader")
        except NodeExistsError:
            self.is_leader = False
            logger.info("Another instance is leader, I'm follower")

            # Watch for leader failure
            self.zookeeper.watch("/leader", self.on_leader_change)

    def create_order(self, order_data):
        """Only leader can accept writes"""
        if not self.is_leader:
            # Redirect to leader
            leader_url = self.zookeeper.get("/leader")
            return redirect(leader_url + "/orders", order_data)

        # Leader processes write
        order = Order(**order_data)
        self.db.save(order)
        return order

    def on_leader_change(self, event):
        """Network partition resolved, re-elect"""
        if event.type == EventType.DELETED:
            # Leader died, try to become leader
            self.start()
```

**Monolith equivalent**: No network partition within single process. Much simpler.

---

## 6. Migration Patterns: Strangler Fig

🎓 **PROFESSOR**: The **Strangler Fig Pattern** (Martin Fowler):
> Gradually replace a monolith by putting a facade in front and routing new features to microservices

Named after strangler fig trees that grow around host trees, eventually replacing them.

🏗️ **ARCHITECT**: Real-world migration strategy:

```
Phase 1: Preparation (Months 1-3)
┌──────────────────────────────────────┐
│     MONOLITH                         │
│  ┌─────────────────────────────┐     │
│  │  ProductService (in-proc)   │     │
│  │  OrderService (in-proc)     │     │
│  │  PaymentService (in-proc)   │     │
│  └─────────────────────────────┘     │
│         ↓                             │
│  [Shared PostgreSQL Database]        │
└──────────────────────────────────────┘

Actions:
- Identify bounded contexts (Domain-Driven Design)
- Separate database schemas logically
- Introduce internal module boundaries
- Add feature flags for gradual rollout
```

```java
// Step 1: Create seams in monolith
@Service
public class OrderService {

    @Autowired
    private PaymentService paymentService;  // Still in-process

    @Value("${feature.payment-microservice.enabled:false}")
    private boolean usePaymentMicroservice;

    public Order createOrder(OrderRequest request) {
        // ... order creation logic ...

        Payment payment;
        if (usePaymentMicroservice) {
            // Route to microservice (behind feature flag)
            payment = paymentMicroserviceClient.charge(request);
        } else {
            // Old code path (in-process)
            payment = paymentService.charge(request);
        }

        return order;
    }
}
```

```
Phase 2: Extract First Service (Months 4-6)
┌────────────────────────────────────────────────────────┐
│               API GATEWAY (NEW)                        │
│         Routes traffic based on path                   │
└─────────────┬──────────────────────┬───────────────────┘
              │                      │
              ↓                      ↓
┌─────────────────────────┐  ┌──────────────────────┐
│   MONOLITH              │  │  PAYMENT SERVICE     │
│  - ProductService       │  │  (Microservice)      │
│  - OrderService         │  │                      │
│  - PaymentService (old) │  │  [payment_db]        │
│                         │  │                      │
│  [main_db]              │  └──────────────────────┘
└─────────────────────────┘

Actions:
- Extract Payment Service code to separate repo
- Create separate payment database
- Dual-write: Write to both old and new DB (temporary)
- Route 10% traffic to new service, monitor
- Gradually increase to 100%
```

```java
// API Gateway routing logic
@Configuration
public class GatewayRoutes {

    @Bean
    public RouteLocator customRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
            // Payment routes go to microservice
            .route("payment-service", r -> r
                .path("/api/v1/payments/**")
                .uri("http://payment-service:8080"))

            // Everything else goes to monolith
            .route("monolith", r -> r
                .path("/**")
                .uri("http://monolith:8080"))

            .build();
    }
}
```

```
Phase 3: Data Migration (Months 7-9)
┌────────────────────────────────────────────────────────┐
│               API GATEWAY                              │
└─────────────┬──────────────────────┬───────────────────┘
              │                      │
              ↓                      ↓
┌─────────────────────────┐  ┌──────────────────────┐
│   MONOLITH              │  │  PAYMENT SERVICE     │
│  - ProductService       │  │                      │
│  - OrderService         │  │  Reads from new DB   │
│                         │  │  Dual-write to both  │
│  [main_db] ←───────────────│  [payment_db]        │
│    (payment tables)     │  │                      │
└─────────────────────────┘  └──────────────────────┘

Actions:
- Migrate historical payment data to new database
- Payment service writes to both databases (consistency)
- Monolith reads from new payment service API
- Verify data consistency
- Remove payment tables from monolith DB
```

```
Phase 4: Complete Extraction (Months 10-12)
┌────────────────────────────────────────────────────────┐
│               API GATEWAY                              │
└─────────┬──────────────────┬────────────────┬─────────┘
          │                  │                │
          ↓                  ↓                ↓
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  PRODUCT     │  │  ORDER       │  │  PAYMENT     │
│  SERVICE     │  │  SERVICE     │  │  SERVICE     │
│              │  │              │  │              │
│ [product_db] │  │ [order_db]   │  │ [payment_db] │
└──────────────┘  └──────────────┘  └──────────────┘

Actions:
- Repeat process for Product and Order services
- Monolith fully decomposed
- Each service owns its database
- Event-driven communication via Kafka
```

**Key Metrics to Track During Migration:**
```java
@Component
public class MigrationMetrics {

    @Autowired
    private MeterRegistry meterRegistry;

    public void recordRequest(String service, boolean isSuccess) {
        meterRegistry.counter("migration.requests",
            "service", service,
            "success", String.valueOf(isSuccess)
        ).increment();
    }

    public void recordLatency(String service, long latencyMs) {
        meterRegistry.timer("migration.latency",
            "service", service
        ).record(Duration.ofMillis(latencyMs));
    }

    public void recordDataConsistency(String entity, boolean isConsistent) {
        // Compare old DB vs new DB
        meterRegistry.gauge("migration.data.consistency",
            Tags.of("entity", entity),
            isConsistent ? 1.0 : 0.0
        );
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

When asked *"When would you choose a monolith vs microservices?"* or *"Design a system using microservices"*:

### 1. Requirements Clarification

```
Questions to ask:
1. Team size? (1-5: monolith, 10+: microservices viable)
2. Expected scale? (< 1M users: monolith often sufficient)
3. Deployment frequency? (Daily: microservices help, Weekly: monolith ok)
4. Different scaling needs? (Some services CPU-heavy, some IO-heavy?)
5. Technology diversity needed? (Different languages/frameworks?)
6. Organizational structure? (Distributed teams favor microservices)
7. Domain maturity? (Well-understood: ok to split, Greenfield: start monolith)
```

### 2. Architecture Decision Criteria

Use this decision matrix:

```
┌─────────────────────────────────────────────────────────────┐
│  CHOOSE MONOLITH WHEN:                                      │
├─────────────────────────────────────────────────────────────┤
│  ✓ Team size < 10 developers                                │
│  ✓ Greenfield project (domain boundaries unclear)           │
│  ✓ Need strong consistency (ACID transactions)              │
│  ✓ Limited DevOps resources                                 │
│  ✓ Tight deadline (faster initial development)              │
│  ✓ Startup/MVP phase (iterate quickly)                      │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  CHOOSE MICROSERVICES WHEN:                                 │
├─────────────────────────────────────────────────────────────┤
│  ✓ Large team (> 20 developers)                             │
│  ✓ Clear domain boundaries exist                            │
│  ✓ Different scaling requirements (CPU vs memory vs IO)     │
│  ✓ Need independent deployment                              │
│  ✓ Polyglot persistence needed (SQL, NoSQL, graph, etc)     │
│  ✓ Strong DevOps culture (CI/CD, monitoring, automation)    │
│  ✓ Mature product (proven business model)                   │
└─────────────────────────────────────────────────────────────┘
```

**Interview Answer Template:**
> "I'd start with a **well-structured modular monolith**. Here's why:
>
> 1. Lower operational complexity (one deployment, one database)
> 2. Faster iteration (no distributed debugging)
> 3. Strong consistency (ACID transactions)
> 4. Easy refactoring (IDE-supported renames)
>
> I'd use **bounded contexts** from Domain-Driven Design to create clear module boundaries. This gives us:
> - Logical separation (like microservices)
> - Option to extract later (Strangler Fig pattern)
> - Flexibility to stay monolith if it works
>
> **Trigger to migrate**: When team grows beyond 10-12 people OR we need independent scaling of specific components."

### 3. High-Level Design

#### Monolith Architecture

```
┌────────────────────────────────────────────────────────────┐
│                    LOAD BALANCER                           │
└─────────────┬──────────────────────────────────────────────┘
              │
    ┌─────────┴─────────┐
    ↓                   ↓
┌─────────────────┐  ┌─────────────────┐
│   MONOLITH 1    │  │   MONOLITH 2    │  ← Horizontal scaling
│                 │  │                 │
│  ┌───────────┐  │  │  ┌───────────┐  │
│  │ Product   │  │  │  │ Product   │  │
│  │ Module    │  │  │  │ Module    │  │
│  ├───────────┤  │  │  ├───────────┤  │
│  │ Order     │  │  │  │ Order     │  │
│  │ Module    │  │  │  │ Module    │  │
│  ├───────────┤  │  │  ├───────────┤  │
│  │ Payment   │  │  │  │ Payment   │  │
│  │ Module    │  │  │  │ Module    │  │
│  └───────────┘  │  │  └───────────┘  │
└────────┬────────┘  └────────┬────────┘
         │                    │
         └────────┬───────────┘
                  ↓
        ┌──────────────────┐
        │  PostgreSQL DB   │
        │  (Primary)       │
        └────────┬─────────┘
                 │
        ┌────────┴─────────┐
        ↓                  ↓
  [Read Replica 1]  [Read Replica 2]

Pros:
- Simple deployment
- Single database transaction
- Easy debugging (single stack trace)
- Lower latency (in-memory calls)

Cons:
- Must scale entire app
- Coupled deployment
- Technology lock-in
```

#### Microservices Architecture

```
┌────────────────────────────────────────────────────────────┐
│                     API GATEWAY                            │
│  - Rate limiting                                           │
│  - Authentication                                          │
│  - Request routing                                         │
└─────┬──────────────┬──────────────┬───────────────────────┘
      │              │              │
      ↓              ↓              ↓
┌──────────┐  ┌──────────┐  ┌──────────┐
│ Product  │  │  Order   │  │ Payment  │
│ Service  │  │ Service  │  │ Service  │
│          │  │          │  │          │
│ [5 pods] │  │ [3 pods] │  │ [10 pods]│ ← Independent scaling
└────┬─────┘  └────┬─────┘  └────┬─────┘
     │             │             │
     ↓             ↓             ↓
┌──────────┐  ┌──────────┐  ┌──────────┐
│product_db│  │ order_db │  │payment_db│ ← Database per service
│(Postgres)│  │(Postgres)│  │(Postgres)│
└──────────┘  └──────────┘  └──────────┘

      ↓              ↓             ↓
┌────────────────────────────────────────┐
│        MESSAGE BUS (Kafka)             │
│  - Order events                        │
│  - Payment events                      │
│  - Saga orchestration                  │
└────────────────────────────────────────┘

      ↓
┌────────────────────────────────────────┐
│    OBSERVABILITY STACK                 │
│  - Logs: ELK Stack                     │
│  - Metrics: Prometheus + Grafana       │
│  - Traces: Jaeger                      │
└────────────────────────────────────────┘

Pros:
- Independent scaling
- Independent deployment
- Technology freedom
- Team autonomy

Cons:
- Complex operations
- Distributed debugging
- Eventual consistency
- Higher latency
```

### 4. Deep Dives

**A. Data Consistency Pattern**

```java
// Saga Pattern with Outbox
@Service
public class OrderService {

    @Transactional
    public Order createOrder(OrderRequest request) {
        // 1. Create order in database
        Order order = new Order(request);
        orderRepo.save(order);

        // 2. Store event in outbox table (same transaction!)
        OutboxEvent event = new OutboxEvent(
            "OrderCreated",
            order.getId(),
            serialize(order)
        );
        outboxRepo.save(event);

        // Both writes succeed or fail together (ACID)
        return order;
    }
}

// Separate process publishes events from outbox
@Scheduled(fixedDelay = 1000)
public void publishOutboxEvents() {
    List<OutboxEvent> pendingEvents = outboxRepo.findUnpublished();

    for (OutboxEvent event : pendingEvents) {
        try {
            kafkaTemplate.send("order-events", event.getPayload());
            event.markPublished();
            outboxRepo.save(event);
        } catch (Exception e) {
            // Retry later
            logger.error("Failed to publish event {}", event.getId(), e);
        }
    }
}
```

**B. Service Discovery Pattern**

```java
// Eureka service registration
@SpringBootApplication
@EnableEurekaClient
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}

// Client-side load balancing
@Service
public class OrderService {

    @Autowired
    @LoadBalanced  // Ribbon client-side load balancer
    private RestTemplate restTemplate;

    public Product getProduct(String id) {
        // "product-service" resolves to actual instances via Eureka
        // Ribbon picks one instance (round-robin, random, etc)
        return restTemplate.getForObject(
            "http://product-service/api/v1/products/" + id,
            Product.class
        );
    }
}
```

**C. Monitoring & Observability**

```
Key Metrics:
┌──────────────────────────────────────────────────────┐
│ 1. RED Metrics (for each service):                  │
│    - Rate: Requests per second                      │
│    - Errors: Error rate                             │
│    - Duration: Latency (P50, P95, P99)              │
│                                                      │
│ 2. USE Metrics (for infrastructure):                │
│    - Utilization: CPU, memory usage                 │
│    - Saturation: Queue depth                        │
│    - Errors: Failed requests                        │
│                                                      │
│ 3. Business Metrics:                                │
│    - Orders per minute                              │
│    - Revenue per second                             │
│    - Cart abandonment rate                          │
└──────────────────────────────────────────────────────┘
```

---

## 🧠 MIND MAP: MONOLITH VS MICROSERVICES

```
                    ARCHITECTURE PATTERNS
                            |
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
   MONOLITH          MICROSERVICES        SERVERLESS
        |                   |                   |
   ┌────┴────┐         ┌────┴────┐        ┌────┴────┐
   ↓         ↓         ↓         ↓        ↓         ↓
MODULAR  SHARED DB  DB PER    EVENT    LAMBDA  STATELESS
DESIGN            SERVICE   DRIVEN

  ↓                   ↓                   ↓
BOUNDED        SAGA PATTERN         AUTO-SCALING
CONTEXTS       CIRCUIT BREAKER      PAY-PER-USE
LAYERS         SERVICE MESH
               API GATEWAY

Trade-offs:
- Simplicity ←→ Scalability
- Consistency ←→ Availability
- Coupling ←→ Latency
- Single Team ←→ Multiple Teams
```

---

## 📊 DECISION MATRIX

Use this scoring system to decide architecture:

```
┌───────────────────────────────────────────────────────────────┐
│  FACTOR                     | MONOLITH | MICROSERVICES        │
├───────────────────────────────────────────────────────────────┤
│ Team size                   |          |                      │
│  - Small (< 10)             |   +3     |    -2                │
│  - Medium (10-50)           |   +1     |    +1                │
│  - Large (> 50)             |   -2     |    +3                │
├───────────────────────────────────────────────────────────────┤
│ Domain clarity              |          |                      │
│  - Unclear boundaries       |   +3     |    -3                │
│  - Some clarity             |   +1     |    0                 │
│  - Well-defined             |   0      |    +2                │
├───────────────────────────────────────────────────────────────┤
│ Consistency requirements    |          |                      │
│  - Strong (ACID needed)     |   +3     |    -2                │
│  - Eventual OK              |   0      |    +2                │
├───────────────────────────────────────────────────────────────┤
│ Scaling requirements        |          |                      │
│  - Uniform                  |   +2     |    -1                │
│  - Mixed (CPU/IO/Memory)    |   -2     |    +3                │
├───────────────────────────────────────────────────────────────┤
│ Deployment frequency        |          |                      │
│  - Weekly/Monthly           |   +2     |    0                 │
│  - Daily/Continuous         |   -1     |    +3                │
├───────────────────────────────────────────────────────────────┤
│ DevOps maturity             |          |                      │
│  - Low (manual deploys)     |   +3     |    -3                │
│  - Medium (some automation) |   +1     |    0                 │
│  - High (full CI/CD)        |   0      |    +2                │
├───────────────────────────────────────────────────────────────┤
│ Business maturity           |          |                      │
│  - Startup/MVP              |   +3     |    -2                │
│  - Growth phase             |   +1     |    +1                │
│  - Mature product           |   -1     |    +2                │
└───────────────────────────────────────────────────────────────┘

Score: Sum all factors
- If score > +5: Strong case for Monolith
- If score between -5 and +5: Either works, prefer Monolith (simpler)
- If score < -5: Strong case for Microservices
```

---

## 💡 EMOTIONAL ANCHORS

**1. Monolith = Single Restaurant Kitchen 🍳**
- One kitchen, all dishes prepared in same space
- Fast communication (shout across room)
- Shared ingredients (database)
- If kitchen closes, entire restaurant closes
- Can't have Italian chef and Chinese chef using different tools

**2. Microservices = Food Court 🍔🍕🍜**
- Multiple independent restaurants
- Each has own kitchen, own ingredients
- If pizza place closes, burger place still serves
- Can hire specialist chefs for each cuisine
- Coordination is harder (customer walks between stalls)

**3. Distributed Transaction = Group Dinner Split 💰**
- Monolith: One person pays, others Venmo them later (simple)
- Microservices: Everyone pays their own bill (complex)
  - What if someone's card declines?
  - What if you already ordered appetizers to share?
  - Need to track who owes what (Saga pattern)

**4. Circuit Breaker = Electrical Circuit Breaker 🔌**
- When downstream service fails (power surge)
- Circuit breaker trips (opens)
- Prevent cascading damage (house fire)
- Try to reset periodically (half-open state)

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Fundamental Computer Science Principles at Play:**

```
1. LOCALITY OF REFERENCE
   - Monolith: High locality (same memory space)
   - Microservices: Low locality (network calls)
   - Impact: 10,000x latency difference

2. COUPLING & COHESION
   - Monolith: High coupling risk (can call anything)
   - Microservices: Enforced decoupling (network boundary)
   - Impact: Organizational scaling

3. CAP THEOREM
   - Monolith: CA (Consistency + Availability within single DB)
   - Microservices: AP (Availability + Partition tolerance)
   - Impact: Must handle eventual consistency

4. FALLACIES OF DISTRIBUTED COMPUTING
   - Monolith: Doesn't suffer from these
   - Microservices: All 8 fallacies apply
   - Impact: Complex failure modes

5. AMDAHL'S LAW
   - Monolith: Serial bottlenecks limit scaling
   - Microservices: Parallel execution, but coordination overhead
   - Impact: Speedup = 1 / (S + P/N + C)
     Where S=serial, P=parallel, N=cores, C=coordination
```

## 🏗️ ARCHITECT'S FINAL WISDOM

**Real-World Decision Framework:**

```
ALWAYS ASK:
1. "Can we solve this by scaling the monolith vertically?"
   (Bigger servers are simpler than microservices)

2. "Do we have the DevOps expertise to run microservices?"
   (Need: K8s, service mesh, observability, on-call rotation)

3. "Are our domain boundaries stable?"
   (If boundaries keep changing, microservices are pain)

4. "What's the cost of being wrong?"
   - Wrong choice: Monolith → Can migrate to microservices
   - Wrong choice: Microservices → Very expensive to merge back

5. "What does our team want to maintain in 2 years?"
   (Developer happiness matters!)
```

**The Pragmatic Path:**
```
Phase 1: Modular Monolith (Months 0-12)
↓
Phase 2: Monolith + 1-2 extracted services (Months 12-24)
         (Extract only clear, stable boundaries)
↓
Phase 3: Decision point (Months 24+)
         - If monolith working: Keep it!
         - If scaling pain: Continue extraction
         - If team growing: Microservices enable autonomy
```

---

## 📚 INTERVIEW CHEAT SHEET

**When asked: "Design an e-commerce system"**

```
1. ASK: "What's the team size and org structure?"
   - Small team → Monolith
   - Multiple teams → Microservices feasible

2. ASK: "What are the scale requirements?"
   - < 1M users → Monolith sufficient
   - > 10M users → Consider microservices for scaling

3. ASK: "What's the consistency requirement?"
   - Strong consistency → Favor monolith
   - Eventual OK → Microservices viable

4. PROPOSE: "I'd start with a modular monolith using DDD"
   - Bounded contexts: Catalog, Orders, Payments, Users
   - Shared database with logical schemas
   - Clear module interfaces (like microservices, but in-process)

5. DISCUSS: "Migration path to microservices"
   - Strangler Fig pattern
   - Extract Payment service first (PCI compliance, independent scaling)
   - Use feature flags for gradual rollout
   - Saga pattern for distributed transactions

6. DEEP DIVE: Pick one based on interviewer interest
   - Data consistency (Saga, Outbox pattern)
   - Service communication (Sync vs Async)
   - Failure handling (Circuit breaker, bulkhead)
   - Observability (Distributed tracing)
```

---

**Key Takeaway**: There is no universal answer. Context matters. A well-designed monolith beats a poorly-designed microservices architecture every time.

> "Microservices are not a free lunch. They are a trade-off. You pay in operational complexity to gain organizational scalability."
> — Martin Fowler
