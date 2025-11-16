# Design a Stock Exchange System

**Companies**: NASDAQ, NYSE, CME Group, Interactive Brokers, Robinhood
**Difficulty**: Advanced
**Time**: 60 minutes
**Focus**: ACID Transactions, Consistency, Financial Constraints, Low-Latency

## Problem Statement

Design a stock exchange platform that handles order placement, matching, and execution. The system must guarantee **ACID transactions**, maintain **strict consistency**, handle **high-frequency trading (HFT)**, and comply with financial regulations.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Order Placement**: Users can place buy/sell orders (market, limit, stop-loss)
2. **Order Matching**: Match buy and sell orders based on price-time priority
3. **Trade Execution**: Execute matched orders atomically
4. **Order Book Management**: Real-time order book with bid/ask spreads
5. **Portfolio Management**: Track user holdings, cash balance
6. **Market Data**: Real-time quotes, historical data, charts
7. **Risk Management**: Pre-trade risk checks, margin requirements
8. **Audit Trail**: Complete audit log of all transactions
9. **Settlement**: T+2 settlement cycle (trade date + 2 business days)

**Prioritize**: Order placement, matching, and execution with ACID guarantees

### Non-Functional Requirements

1. **ACID Compliance**:
   - **Atomicity**: All-or-nothing trade execution
   - **Consistency**: Account balances always accurate
   - **Isolation**: Concurrent trades don't interfere
   - **Durability**: Trades never lost after confirmation

2. **Performance**:
   - **Latency**: <1ms for order matching (HFT requirement)
   - **Throughput**: 100,000+ orders/sec per symbol
   - **Availability**: 99.99% uptime during market hours

3. **Consistency Model**:
   - **Strong consistency** for account balances
   - **Linearizability** for order book operations
   - **Sequential consistency** for market data

4. **Financial Constraints**:
   - **No overdrafts**: Users cannot spend more than they have
   - **No short selling** without collateral
   - **Double-entry bookkeeping**: Every debit has a credit
   - **Regulatory compliance**: SEC, FINRA regulations

### Capacity Estimation

**Assumptions**:
- 10M active traders
- 1M concurrent users during peak
- 100 orders per active trader per day
- Average 50% order fill rate
- 5,000 tradable symbols

**Trade Volume**:
```
Daily orders: 10M traders × 100 orders = 1B orders/day
Orders per second: 1B ÷ 86,400 = ~12,000 orders/sec
Peak: 12K × 10 = 120,000 orders/sec (market open/close)
Executions: 1B × 50% = 500M executions/day
Execution rate: ~6,000 executions/sec
```

**Storage**:
```
Order record: ~500 bytes (order details + metadata)
Daily storage: 1B orders × 500 bytes = 500 GB/day
Yearly storage: 500 GB × 250 trading days = 125 TB/year
With 7-year retention: 125 TB × 7 = 875 TB

Market data (tick-by-tick):
5,000 symbols × 1 tick/sec × 6.5 hours × 100 bytes = 11.7 GB/day
Yearly: 11.7 GB × 250 = 2.9 TB/year
```

**Memory**:
```
Order book per symbol: ~10MB (hot orders)
Total order books: 5,000 × 10MB = 50 GB
Real-time positions: 10M users × 1KB = 10 GB
Total hot memory: ~100 GB
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                  Trading Clients                     │
│         (Web, Mobile, FIX API, Algo Trading)        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              API Gateway + Load Balancer             │
│           (Rate Limiting, Authentication)           │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
    ┌──────────┐ ┌──────────┐ ┌──────────┐
    │  Order   │ │  Market  │ │Portfolio │
    │ Service  │ │  Data    │ │ Service  │
    └────┬─────┘ └────┬─────┘ └────┬─────┘
         │            │            │
         └────────┬───┴────────────┘
                  ▼
    ┌─────────────────────────────┐
    │    Matching Engine Cluster   │
    │  (In-Memory, Lock-Free)     │
    └────────────┬─────────────────┘
                 │
         ┌───────┼───────┐
         ▼       ▼       ▼
    ┌────────┐ ┌────────┐ ┌────────┐
    │ Order  │ │ Trade  │ │ Market │
    │   DB   │ │  DB    │ │Data DB │
    │(OLTP)  │ │(OLTP)  │ │(TSDB)  │
    └────────┘ └────────┘ └────────┘
```

### Components

**1. API Gateway**:
- Authentication (JWT, OAuth2, FIX protocol)
- Rate limiting (prevent market manipulation)
- Request validation
- WebSocket for real-time updates

**2. Order Service**:
- Receives and validates orders
- Pre-trade risk checks
- Publishes orders to matching engine
- Order status updates

**3. Matching Engine** (CRITICAL COMPONENT):
- **In-memory order book** per symbol
- **Price-time priority** algorithm
- **Lock-free** concurrent data structures
- <1ms matching latency
- Runs on dedicated hardware (low-latency servers)

**4. Trade Service**:
- Executes matched trades
- Updates account balances (ACID transaction)
- Generates trade confirmations
- Settlement processing

**5. Portfolio Service**:
- Real-time portfolio valuation
- Position tracking
- Margin calculations
- P&L reporting

**6. Market Data Service**:
- Real-time quotes (L1, L2, L3)
- Order book snapshots
- Trade tape (time & sales)
- Historical data

**7. Risk Management**:
- Pre-trade checks (buying power, margin)
- Position limits
- Circuit breakers (halt trading)
- Wash sale detection

---

## Step 3: Data Model (10 min)

### Database Choice

**ACID Requirements Demand**:
- **Primary Database**: PostgreSQL (ACID compliant)
- **In-Memory Cache**: Redis (order book cache)
- **Time-Series DB**: TimescaleDB (market data)
- **Event Store**: Kafka (audit trail)

### Core Schema

```sql
-- Users and Accounts
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE NOT NULL,
    kyc_status VARCHAR(20), -- Know Your Customer
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    account_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    account_type VARCHAR(20), -- CASH, MARGIN
    cash_balance DECIMAL(20, 2) NOT NULL DEFAULT 0,
    buying_power DECIMAL(20, 2) NOT NULL DEFAULT 0,
    -- Constraints: cash_balance >= 0 (no overdrafts)
    CONSTRAINT positive_balance CHECK (cash_balance >= 0)
);

-- Positions (Holdings)
CREATE TABLE positions (
    position_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(account_id),
    symbol VARCHAR(10) NOT NULL,
    quantity BIGINT NOT NULL, -- Can be negative for short positions
    average_cost DECIMAL(20, 4),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(account_id, symbol)
);

-- Orders
CREATE TABLE orders (
    order_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(account_id),
    symbol VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL, -- BUY, SELL
    order_type VARCHAR(10) NOT NULL, -- MARKET, LIMIT, STOP
    quantity BIGINT NOT NULL,
    price DECIMAL(20, 4), -- NULL for market orders
    status VARCHAR(20) NOT NULL, -- NEW, PARTIAL, FILLED, CANCELLED
    filled_quantity BIGINT DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Index for fast order book queries
    INDEX idx_symbol_side_price (symbol, side, price, created_at)
);

-- Trades (Executions)
CREATE TABLE trades (
    trade_id BIGSERIAL PRIMARY KEY,
    symbol VARCHAR(10) NOT NULL,
    buy_order_id BIGINT REFERENCES orders(order_id),
    sell_order_id BIGINT REFERENCES orders(order_id),
    price DECIMAL(20, 4) NOT NULL,
    quantity BIGINT NOT NULL,
    trade_value DECIMAL(20, 2) NOT NULL, -- price × quantity
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    settlement_date DATE, -- T+2

    -- Immutable: trades cannot be modified
    -- Audit trail maintained
);

-- Ledger (Double-Entry Bookkeeping)
CREATE TABLE ledger_entries (
    entry_id BIGSERIAL PRIMARY KEY,
    account_id BIGINT REFERENCES accounts(account_id),
    trade_id BIGINT REFERENCES trades(trade_id),
    entry_type VARCHAR(10) NOT NULL, -- DEBIT, CREDIT
    amount DECIMAL(20, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    -- Every trade generates 2+ ledger entries
    -- Sum of all entries for an account = account balance
);

-- Market Data (Time-Series)
CREATE TABLE market_data (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open DECIMAL(20, 4),
    high DECIMAL(20, 4),
    low DECIMAL(20, 4),
    close DECIMAL(20, 4),
    volume BIGINT,
    PRIMARY KEY (symbol, timestamp)
);

-- Convert to TimescaleDB hypertable
SELECT create_hypertable('market_data', 'timestamp');
```

### In-Memory Order Book Structure

```java
// Lock-free order book using concurrent data structures
public class OrderBook {
    private final String symbol;

    // Buy orders: sorted descending by price (highest first)
    // Tie-breaker: time (FIFO)
    private final ConcurrentSkipListMap<PriceLevel, Queue<Order>> bids;

    // Sell orders: sorted ascending by price (lowest first)
    private final ConcurrentSkipListMap<PriceLevel, Queue<Order>> asks;

    // Fast lookup by order ID
    private final ConcurrentHashMap<Long, Order> orderIndex;

    public MatchResult addOrder(Order order) {
        // Lock-free insertion
        // Returns immediate matches if any
    }

    public void cancelOrder(long orderId) {
        // Lock-free removal
    }
}

class PriceLevel implements Comparable<PriceLevel> {
    private final BigDecimal price;
    private final long timestamp; // For time priority

    // Price-time priority
    public int compareTo(PriceLevel other) {
        int priceCompare = price.compareTo(other.price);
        return (priceCompare != 0) ? priceCompare :
               Long.compare(timestamp, other.timestamp);
    }
}
```

---

## Step 4: APIs (5 min)

### REST APIs

#### Place Order

```http
POST /api/v1/orders
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

Request:
{
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": 100,
  "price": 150.50,
  "timeInForce": "DAY" // DAY, GTC (Good Till Cancel), IOC (Immediate or Cancel)
}

Response: 201 Created
{
  "orderId": "ORD-123456789",
  "status": "NEW",
  "symbol": "AAPL",
  "side": "BUY",
  "type": "LIMIT",
  "quantity": 100,
  "filledQuantity": 0,
  "price": 150.50,
  "averageFillPrice": null,
  "createdAt": "2025-11-15T09:30:00Z",
  "estimatedValue": 15050.00
}
```

#### Cancel Order

```http
DELETE /api/v1/orders/{orderId}

Response: 200 OK
{
  "orderId": "ORD-123456789",
  "status": "CANCELLED",
  "cancelledAt": "2025-11-15T09:31:00Z"
}
```

#### Get Order Book (Market Depth)

```http
GET /api/v1/orderbook/{symbol}?depth=10

Response: 200 OK
{
  "symbol": "AAPL",
  "timestamp": "2025-11-15T09:30:05Z",
  "bids": [
    {"price": 150.50, "quantity": 1000, "orders": 5},
    {"price": 150.45, "quantity": 2500, "orders": 12},
    {"price": 150.40, "quantity": 5000, "orders": 20}
  ],
  "asks": [
    {"price": 150.55, "quantity": 800, "orders": 3},
    {"price": 150.60, "quantity": 1500, "orders": 7},
    {"price": 150.65, "quantity": 3000, "orders": 15}
  ],
  "lastTrade": {"price": 150.52, "quantity": 100, "time": "09:30:04"}
}
```

### WebSocket API (Real-Time Updates)

```javascript
// Subscribe to order updates
ws.send({
  "action": "subscribe",
  "channel": "orders",
  "accountId": "ACC-123456"
});

// Server push: Order filled
{
  "channel": "orders",
  "event": "FILL",
  "data": {
    "orderId": "ORD-123456789",
    "status": "FILLED",
    "filledQuantity": 100,
    "averageFillPrice": 150.51,
    "fills": [
      {"quantity": 50, "price": 150.50, "time": "09:30:05.123"},
      {"quantity": 50, "price": 150.52, "time": "09:30:05.456"}
    ]
  }
}

// Subscribe to market data
ws.send({
  "action": "subscribe",
  "channel": "quotes",
  "symbols": ["AAPL", "GOOGL", "MSFT"]
});

// Server push: Quote update (every tick)
{
  "channel": "quotes",
  "symbol": "AAPL",
  "bid": 150.50,
  "ask": 150.55,
  "last": 150.52,
  "volume": 1234567,
  "timestamp": "09:30:05.789"
}
```

### FIX Protocol API

```
// FIX 4.4 New Order Single
8=FIX.4.4|9=178|35=D|49=CLIENT123|56=EXCHANGE|
34=1|52=20251115-09:30:00|
11=ORD-123456789|21=1|55=AAPL|54=1|60=20251115-09:30:00|
38=100|40=2|44=150.50|59=0|10=123|

// Response: Execution Report
8=FIX.4.4|9=256|35=8|49=EXCHANGE|56=CLIENT123|
34=1|52=20251115-09:30:05|
11=ORD-123456789|37=EXG-987654321|17=EXEC-001|
150=2|39=2|55=AAPL|54=1|
38=100|32=100|31=150.51|151=0|14=100|6=150.51|10=234|
```

---

## Step 5: Core Algorithms

### Order Matching Algorithm (Price-Time Priority)

```java
public class MatchingEngine {

    public List<Trade> matchOrder(Order incomingOrder) {
        List<Trade> trades = new ArrayList<>();
        OrderBook book = getOrderBook(incomingOrder.getSymbol());

        if (incomingOrder.getSide() == Side.BUY) {
            // Match against sell orders (asks)
            while (incomingOrder.getRemainingQuantity() > 0) {
                PriceLevel bestAsk = book.getBestAsk();

                if (bestAsk == null) break; // No sellers

                // Check price match
                if (incomingOrder.getType() == OrderType.LIMIT &&
                    incomingOrder.getPrice().compareTo(bestAsk.getPrice()) < 0) {
                    break; // Price not acceptable
                }

                // Match!
                Order restingOrder = bestAsk.getFirstOrder();
                long matchedQty = Math.min(
                    incomingOrder.getRemainingQuantity(),
                    restingOrder.getRemainingQuantity()
                );

                // Execute trade
                Trade trade = executeTrade(
                    incomingOrder,
                    restingOrder,
                    bestAsk.getPrice(),
                    matchedQty
                );
                trades.add(trade);

                // Update orders
                incomingOrder.fill(matchedQty);
                restingOrder.fill(matchedQty);

                // Remove fully filled resting order
                if (restingOrder.isFilled()) {
                    book.removeOrder(restingOrder);
                }
            }

            // Add remaining quantity to order book
            if (incomingOrder.getRemainingQuantity() > 0) {
                book.addBuyOrder(incomingOrder);
            }

        } else {
            // Similar logic for sell orders matching against bids
            // ...
        }

        return trades;
    }

    private Trade executeTrade(Order buy, Order sell,
                               BigDecimal price, long quantity) {
        // ACID transaction begins
        try {
            // 1. Create trade record
            Trade trade = new Trade(buy, sell, price, quantity);
            tradeRepository.save(trade);

            // 2. Update buyer's account (double-entry)
            Account buyerAccount = accountRepository
                .findByIdWithLock(buy.getAccountId());
            buyerAccount.debit(trade.getValue()); // Cash decreases
            buyerAccount.increasePosition(trade.getSymbol(), quantity);

            // 3. Update seller's account
            Account sellerAccount = accountRepository
                .findByIdWithLock(sell.getAccountId());
            sellerAccount.credit(trade.getValue()); // Cash increases
            sellerAccount.decreasePosition(trade.getSymbol(), quantity);

            // 4. Create ledger entries (audit trail)
            ledgerService.recordTrade(trade, buyerAccount, sellerAccount);

            // 5. Commit transaction
            transactionManager.commit();

            // 6. Publish trade event (async)
            eventPublisher.publishTrade(trade);

            return trade;

        } catch (Exception e) {
            // Rollback on any failure
            transactionManager.rollback();
            throw new TradeExecutionException(e);
        }
    }
}
```

### Circuit Breaker (Market Volatility Control)

```java
public class CircuitBreaker {
    private static final BigDecimal HALT_THRESHOLD = new BigDecimal("0.10"); // 10%

    public boolean shouldHalt(String symbol, BigDecimal newPrice) {
        BigDecimal referencePrice = marketDataService
            .getOpeningPrice(symbol);

        BigDecimal priceChange = newPrice.subtract(referencePrice)
            .divide(referencePrice, 4, RoundingMode.HALF_UP)
            .abs();

        if (priceChange.compareTo(HALT_THRESHOLD) > 0) {
            // Halt trading for this symbol
            tradingHaltService.haltSymbol(symbol, Duration.ofMinutes(5));
            notificationService.alertCircuitBreaker(symbol, priceChange);
            return true;
        }

        return false;
    }
}
```

---

## Step 6: Distributed Systems Primitives

### 1. Consensus for Leader Election (Matching Engine)

```java
// Using Raft consensus for matching engine leader election
public class MatchingEngineCluster {
    private RaftNode raftNode;
    private MatchingEngine engine;

    public void start() {
        raftNode = new RaftNode(nodeId, peers);

        raftNode.onBecomeLeader(() -> {
            // This node is now the leader for matching
            engine.activate();
            log.info("Node {} became matching engine leader", nodeId);
        });

        raftNode.onBecomeFollower(() -> {
            // Standby mode - replicate state only
            engine.deactivate();
            log.info("Node {} is now follower", nodeId);
        });

        raftNode.start();
    }

    // All orders go through leader
    public OrderResult submitOrder(Order order) {
        if (!raftNode.isLeader()) {
            // Redirect to current leader
            String leaderAddress = raftNode.getLeaderAddress();
            throw new NotLeaderException(leaderAddress);
        }

        // Replicate order to followers before matching
        raftNode.replicate(order);

        // Execute on leader
        return engine.matchOrder(order);
    }
}
```

### 2. Event Sourcing for Audit Trail

```java
// Every state change is an event
public class OrderEventStore {
    private KafkaProducer<String, OrderEvent> eventProducer;

    public void recordOrderPlaced(Order order) {
        OrderEvent event = OrderEvent.builder()
            .eventId(UUID.randomUUID())
            .eventType(EventType.ORDER_PLACED)
            .orderId(order.getOrderId())
            .accountId(order.getAccountId())
            .symbol(order.getSymbol())
            .side(order.getSide())
            .quantity(order.getQuantity())
            .price(order.getPrice())
            .timestamp(Instant.now())
            .build();

        // Kafka provides durability and ordering
        eventProducer.send("order-events", event);

        // Can rebuild entire state from event log
    }

    // Rebuild order state from events
    public Order rebuildOrder(long orderId) {
        List<OrderEvent> events = eventStore.getEvents(orderId);
        Order order = new Order();

        for (OrderEvent event : events) {
            order.apply(event); // Apply each event
        }

        return order;
    }
}
```

### 3. Two-Phase Commit for Trade Settlement

```java
// Distributed transaction across multiple databases
public class SettlementService {

    @Transactional
    public void settleTradesT2() {
        LocalDate settlementDate = LocalDate.now().minusDays(2);
        List<Trade> tradesToSettle = tradeRepository
            .findBySettlementDate(settlementDate);

        // Phase 1: Prepare
        TransactionCoordinator coordinator = new TransactionCoordinator();

        for (Trade trade : tradesToSettle) {
            // Prepare buyer's clearing broker
            boolean buyerReady = clearingBrokerService
                .prepare(trade.getBuyerClearingId(), trade);

            // Prepare seller's clearing broker
            boolean sellerReady = clearingBrokerService
                .prepare(trade.getSellerClearingId(), trade);

            coordinator.addParticipant(trade.getBuyerClearingId(), buyerReady);
            coordinator.addParticipant(trade.getSellerClearingId(), sellerReady);
        }

        // Phase 2: Commit or Abort
        if (coordinator.allPrepared()) {
            coordinator.commit();
            log.info("Settled {} trades for date {}",
                     tradesToSettle.size(), settlementDate);
        } else {
            coordinator.abort();
            log.error("Settlement failed for date {}", settlementDate);
            alertService.notifySettlementFailure(settlementDate);
        }
    }
}
```

### 4. Optimistic Concurrency Control

```java
// Use version numbers to detect conflicts
@Entity
public class Account {
    @Id
    private Long accountId;

    @Column(precision = 20, scale = 2)
    private BigDecimal cashBalance;

    @Version // JPA optimistic locking
    private Long version;

    public void debit(BigDecimal amount) {
        if (cashBalance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        cashBalance = cashBalance.subtract(amount);
        // Version automatically incremented on commit
    }
}

// Usage
public void executeTrade(Trade trade) {
    try {
        Account buyerAccount = accountRepository
            .findById(trade.getBuyerId());
        buyerAccount.debit(trade.getValue());
        accountRepository.save(buyerAccount);

    } catch (OptimisticLockException e) {
        // Another transaction modified this account
        // Retry with latest version
        executeTrade(trade);
    }
}
```

---

## Step 7: Performance Optimizations

### 1. Lock-Free Data Structures

```java
// Lock-free order book using CAS (Compare-And-Swap)
public class LockFreeOrderBook {
    private final AtomicReference<OrderBookSnapshot> snapshot;

    public void addOrder(Order order) {
        while (true) {
            OrderBookSnapshot current = snapshot.get();
            OrderBookSnapshot updated = current.withOrder(order);

            // CAS: only succeeds if snapshot unchanged
            if (snapshot.compareAndSet(current, updated)) {
                return; // Success
            }
            // Retry if another thread modified snapshot
        }
    }

    // Immutable snapshot for consistent reads
    record OrderBookSnapshot(
        TreeMap<BigDecimal, List<Order>> bids,
        TreeMap<BigDecimal, List<Order>> asks
    ) {
        OrderBookSnapshot withOrder(Order order) {
            // Create new snapshot with order added
            // Original snapshot unchanged
        }
    }
}
```

### 2. CPU Affinity and NUMA Awareness

```java
// Pin matching engine threads to specific CPU cores
public class MatchingEngineOptimized {

    public void start() {
        // Get CPU topology
        int numCores = Runtime.getRuntime().availableProcessors();

        // Pin matching thread to isolated core
        Thread matchingThread = new Thread(this::matchOrders);
        matchingThread.setPriority(Thread.MAX_PRIORITY);

        // Use JNI to set CPU affinity (Linux)
        setCpuAffinity(matchingThread, ISOLATED_CORE);

        // Allocate memory on local NUMA node
        allocateNumaLocal(orderBookMemory);

        matchingThread.start();
    }

    // Reduce context switches and cache misses
    private native void setCpuAffinity(Thread thread, int core);
}
```

### 3. Mechanical Sympathy (Hardware-Aware Design)

```java
// Align data structures to cache lines (64 bytes)
@Contended // JVM annotation to prevent false sharing
public class Order {
    // Frequently accessed fields together
    private volatile long orderId;
    private volatile OrderStatus status;
    private volatile long filledQuantity;

    // Padding to prevent false sharing with next object
    private long p1, p2, p3, p4, p5, p6, p7;
}

// Use LMAX Disruptor for lock-free ring buffer
public class OrderProcessor {
    private Disruptor<OrderEvent> disruptor;

    public void start() {
        // Ring buffer size: power of 2 for fast modulo
        disruptor = new Disruptor<>(
            OrderEvent::new,
            1024 * 1024, // 1M slots
            Executors.defaultThreadFactory(),
            ProducerType.MULTI,
            new BusySpinWaitStrategy() // Low latency
        );

        disruptor.handleEventsWith(this::processOrder);
        disruptor.start();
    }

    public void submitOrder(Order order) {
        RingBuffer<OrderEvent> ringBuffer = disruptor.getRingBuffer();
        long sequence = ringBuffer.next();
        try {
            OrderEvent event = ringBuffer.get(sequence);
            event.set(order);
        } finally {
            ringBuffer.publish(sequence);
        }
    }
}
```

### 4. Database Partitioning

```sql
-- Partition orders by symbol for parallel processing
CREATE TABLE orders (
    order_id BIGSERIAL,
    symbol VARCHAR(10) NOT NULL,
    account_id BIGINT,
    -- other fields...
) PARTITION BY HASH (symbol);

-- Create 16 partitions
CREATE TABLE orders_p0 PARTITION OF orders FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE orders_p1 PARTITION OF orders FOR VALUES WITH (MODULUS 16, REMAINDER 1);
-- ... up to p15

-- Each partition can be on different disk/node
-- Queries for single symbol hit only one partition
```

---

## Step 8: Tech Stack & DevOps Solutions

### Production Tech Stack (Real-World)

#### Core Systems

**Matching Engine**:
- **Language**: C++17, Rust (for ultra-low latency)
- **Framework**: Custom lock-free implementations
- **Hardware**: Bare metal servers, 10GbE/40GbE network
- **OS**: Linux RT (Real-Time kernel)
- **Memory**: Huge pages, NUMA-aware allocation

**Application Services**:
- **Language**: Java 17+ (Spring Boot), Go
- **Framework**:
  - Spring Boot 3.0 (REST APIs)
  - Netty (WebSocket servers)
  - QuickFIX/J (FIX protocol)
- **API Gateway**: Kong, AWS API Gateway

**Databases**:
- **OLTP**: PostgreSQL 15+ (for ACID transactions)
  - Extensions: TimescaleDB (market data)
  - Connection Pooling: PgBouncer, HikariCP
  - Replication: Streaming replication, Patroni
- **Caching**: Redis Cluster, Hazelcast
- **Search**: Elasticsearch (audit logs)
- **Time-Series**: InfluxDB, QuestDB (tick data)

**Message Queue**:
- **Event Streaming**: Apache Kafka 3.0+
  - Topics: orders, trades, market-data, settlement
  - Partitioning: by symbol (parallel processing)
  - Retention: 7-30 days (compliance)
- **Alternative**: Apache Pulsar (geo-replication)

#### DevOps & Infrastructure

**Container Orchestration**:
```yaml
# Kubernetes deployment for order service
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 10
  selector:
    matchLabels:
      app: order-service
  template:
    metadata:
      labels:
        app: order-service
    spec:
      affinity:
        # Spread across availability zones
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - order-service
            topologyKey: topology.kubernetes.io/zone
      containers:
      - name: order-service
        image: exchange/order-service:v1.2.3
        resources:
          requests:
            memory: "4Gi"
            cpu: "2"
          limits:
            memory: "8Gi"
            cpu: "4"
        env:
        - name: JAVA_OPTS
          value: "-Xmx6g -XX:+UseG1GC -XX:MaxGCPauseMillis=20"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 20
          periodSeconds: 5
```

**Monitoring Stack**:
- **Metrics**: Prometheus + Grafana
  - JVM metrics (heap, GC, threads)
  - Order latency (p50, p95, p99)
  - Database connections
  - Kafka lag
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
  - Structured JSON logs
  - Distributed tracing: Jaeger, Zipkin
  - Log correlation by trace-id
- **Alerting**: PagerDuty, OpsGenie
  - Alert on latency > 10ms (p99)
  - Alert on failed trades
  - Alert on circuit breakers

**CI/CD Pipeline**:
```yaml
# GitLab CI/CD pipeline
stages:
  - test
  - build
  - deploy-staging
  - integration-test
  - deploy-prod

test:
  stage: test
  script:
    - mvn clean test
    - mvn verify # Integration tests
  coverage: '/Total.*?([0-9]{1,3})%/'

build:
  stage: build
  script:
    - docker build -t $CI_REGISTRY/order-service:$CI_COMMIT_SHA .
    - docker push $CI_REGISTRY/order-service:$CI_COMMIT_SHA

deploy-prod:
  stage: deploy-prod
  script:
    - kubectl set image deployment/order-service
        order-service=$CI_REGISTRY/order-service:$CI_COMMIT_SHA
    - kubectl rollout status deployment/order-service
  only:
    - main
  when: manual # Require manual approval
```

**Networking**:
- **Load Balancer**: F5 BIG-IP, HAProxy, AWS NLB
- **Service Mesh**: Istio, Linkerd (mTLS, traffic shaping)
- **Network**:
  - Low-latency: Solarflare NICs, kernel bypass
  - Multicast for market data distribution
  - Dedicated VLAN for matching engine

**Security**:
- **Authentication**: OAuth2, SAML, Hardware tokens (YubiKey)
- **Encryption**: TLS 1.3, AES-256
- **Secrets Management**: HashiCorp Vault, AWS Secrets Manager
- **DDoS Protection**: Cloudflare, AWS Shield
- **Firewall**: Palo Alto Networks, AWS WAF

**Disaster Recovery**:
- **Backup Strategy**:
  - PostgreSQL: Continuous WAL archiving, pg_basebackup
  - Kafka: Replication factor 3, cross-region replication
  - Redis: RDB snapshots + AOF
- **RTO/RPO**:
  - Recovery Time Objective: 15 minutes
  - Recovery Point Objective: 0 (no data loss)
- **Multi-Region**:
  - Active-Passive: Primary (US-EAST), Standby (US-WEST)
  - Automated failover with health checks

---

## Step 9: Financial Compliance & Regulations

### SEC/FINRA Requirements

**1. Audit Trail (Rule 613 - CAT)**:
```java
// Consolidated Audit Trail - every order must be reported
public class CATReporter {

    public void reportOrder(Order order) {
        CATReport report = CATReport.builder()
            .reportingEntity("EXCH-001")
            .orderEvent(OrderEvent.NEW_ORDER)
            .orderId(order.getOrderId())
            .accountId(hashAccountId(order.getAccountId())) // PII protection
            .symbol(order.getSymbol())
            .side(order.getSide())
            .quantity(order.getQuantity())
            .price(order.getPrice())
            .timestamp(order.getCreatedAt())
            .build();

        // Submit to FINRA CAT system
        catService.submitReport(report);
    }
}
```

**2. Best Execution (Reg NMS)**:
```java
// Must route to exchange with best price
public class SmartOrderRouter {

    public Exchange routeOrder(Order order) {
        // Check NBBO (National Best Bid and Offer)
        Quote nbbo = marketDataService.getNBBO(order.getSymbol());

        List<Exchange> exchanges = getExchanges();

        if (order.getSide() == Side.BUY) {
            // Route to exchange with best ask (lowest)
            return exchanges.stream()
                .filter(e -> e.getAsk().equals(nbbo.getAsk()))
                .findFirst()
                .orElseThrow();
        } else {
            // Route to exchange with best bid (highest)
            return exchanges.stream()
                .filter(e -> e.getBid().equals(nbbo.getBid()))
                .findFirst()
                .orElseThrow();
        }
    }
}
```

**3. Market Manipulation Detection**:
```java
// Detect wash trading, spoofing, layering
public class SurveillanceEngine {

    public void detectWashTrading(Account account, LocalDate date) {
        // Find trades where account is both buyer and seller
        List<Trade> trades = tradeRepository.findByDate(date);

        for (Trade trade : trades) {
            if (trade.getBuyerAccountId().equals(trade.getSellerAccountId())) {
                Alert alert = Alert.builder()
                    .type(AlertType.WASH_TRADING)
                    .accountId(account.getAccountId())
                    .tradeId(trade.getTradeId())
                    .severity(Severity.HIGH)
                    .build();

                complianceService.raiseAlert(alert);
            }
        }
    }

    public void detectSpoofing(List<Order> orders) {
        // Pattern: Large order placed, then cancelled after price moves
        // Indicates intent to manipulate without execution

        Map<String, List<Order>> ordersBySymbol = orders.stream()
            .collect(Collectors.groupingBy(Order::getSymbol));

        for (Map.Entry<String, List<Order>> entry : ordersBySymbol.entrySet()) {
            String symbol = entry.getKey();
            List<Order> symbolOrders = entry.getValue();

            // Check for pattern:
            // 1. Large limit order placed
            // 2. Price moves toward limit
            // 3. Order cancelled before execution
            // 4. Happens repeatedly

            if (isSpoofingPattern(symbolOrders)) {
                complianceService.raiseAlert(
                    Alert.spoofing(symbol, symbolOrders)
                );
            }
        }
    }
}
```

---

## Step 10: Advanced Topics

### High-Frequency Trading (HFT) Support

**Co-location**:
- Traders pay to place servers in exchange data center
- Reduces network latency to <100 microseconds
- Requires dedicated rack space, power, cooling

**Market Data Feeds**:
```java
// Ultra-low latency market data using multicast
public class MulticastMarketData {

    public void start() throws IOException {
        MulticastSocket socket = new MulticastSocket(PORT);
        InetAddress group = InetAddress.getByName(MULTICAST_GROUP);
        socket.joinGroup(group);

        byte[] buffer = new byte[1024];

        while (true) {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
            socket.receive(packet); // Blocking

            // Parse market data message (binary protocol)
            MarketDataMessage msg = parseMessage(packet.getData());

            // Process immediately (no queuing)
            processMarketData(msg);
        }
    }

    // Binary encoding for speed
    private MarketDataMessage parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data);

        return new MarketDataMessage(
            buffer.getLong(),      // timestamp (nanoseconds)
            buffer.getInt(),       // symbol ID
            buffer.getDouble(),    // bid price
            buffer.getDouble(),    // ask price
            buffer.getLong(),      // bid size
            buffer.getLong()       // ask size
        );
    }
}
```

### Dark Pools

```java
// Hidden liquidity pools - orders not displayed publicly
public class DarkPoolMatchingEngine extends MatchingEngine {

    @Override
    public List<Trade> matchOrder(Order order) {
        // Orders never added to public order book
        // Matched at midpoint price

        List<Order> contraOrders = findContraOrders(order);
        List<Trade> trades = new ArrayList<>();

        for (Order contraOrder : contraOrders) {
            // Calculate midpoint price
            BigDecimal midpoint = calculateMidpoint(
                getNBBO(order.getSymbol())
            );

            long matchedQty = Math.min(
                order.getRemainingQuantity(),
                contraOrder.getRemainingQuantity()
            );

            // Execute at midpoint (price improvement)
            Trade trade = executeTrade(
                order, contraOrder, midpoint, matchedQty
            );
            trades.add(trade);

            if (order.isFilled()) break;
        }

        // Unmatched portion may be routed to lit exchange
        if (!order.isFilled()) {
            routeToLitExchange(order);
        }

        return trades;
    }

    private BigDecimal calculateMidpoint(Quote nbbo) {
        return nbbo.getBid()
            .add(nbbo.getAsk())
            .divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }
}
```

---

## Key Takeaways

### ACID in Trading Systems

1. **Atomicity**: Trade execution is all-or-nothing
   - Both buyer and seller accounts updated, or neither
   - Uses database transactions with 2PC if distributed

2. **Consistency**: Account balances always accurate
   - Constraints: `cash_balance >= 0`
   - Double-entry bookkeeping: sum(debits) = sum(credits)
   - Referential integrity maintained

3. **Isolation**: Concurrent trades don't interfere
   - Optimistic locking with version numbers
   - Serializable isolation level for critical operations
   - Lock-free data structures where possible

4. **Durability**: Trades never lost after confirmation
   - WAL (Write-Ahead Logging) in PostgreSQL
   - Synchronous replication to standby
   - Event sourcing for complete audit trail

### Performance vs. Consistency Trade-offs

```
Linearizability (Strictest)
    ↓
Sequential Consistency
    ↓
Causal Consistency
    ↓
Eventual Consistency (Weakest)

Trading System Choices:
- Account balances: Linearizable (ACID)
- Order book: Sequential Consistency
- Market data: Eventual Consistency (acceptable delay)
- Analytics: Eventual Consistency
```

### Latency Breakdown (Target)

```
Total Order-to-Execution Latency: <10ms

Network (client to gateway):     2ms
API Gateway:                      0.5ms
Order validation:                 1ms
Matching engine:                  0.5ms (in-memory)
Database write (async):           3ms
Response to client:               2ms
WebSocket notification:           1ms
```

---

## Further Reading

- **Books**:
  - "Flash Boys" by Michael Lewis (HFT insights)
  - "Trading and Exchanges" by Larry Harris
  - "Designing Data-Intensive Applications" by Martin Kleppmann

- **Academic Papers**:
  - "The Implementation Shortfall" (Almgren & Chriss)
  - "Price Discovery in Financial Markets" (Madhavan)

- **Industry Standards**:
  - FIX Protocol Specification
  - SEC Rule 613 (CAT)
  - Regulation NMS

- **Real Systems**:
  - NASDAQ OMX Platform
  - CME Globex
  - LMAX Exchange (Java-based, open source architecture)
