# Design a Stock Trading System

**Companies**: Robinhood, E*TRADE, Interactive Brokers, Coinbase
**Difficulty**: Advanced
**Time**: 60 minutes
**Focus**: ACID compliance, strong consistency, ultra-low latency

## Problem Statement

Design a real-time stock trading platform where users can buy and sell stocks, view live market data, manage portfolios, and execute trades with **zero financial discrepancies**. The system must guarantee ACID properties for all financial transactions.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **User Management**: Registration, KYC verification, account management
2. **Order Placement**: Market orders, limit orders, stop orders, stop-limit orders
3. **Order Modification**: Cancel orders, modify price/quantity
4. **Trade Execution**: Match buyers with sellers, execute trades
5. **Portfolio Management**: Track positions, calculate P&L, display holdings
6. **Market Data**: Real-time price quotes, order book depth, trade history
7. **Settlement**: T+2 settlement with clearinghouse integration
8. **Reporting**: Tax documents, trade confirmations, account statements

**Prioritize**: Focus on order placement, execution, and portfolio management for MVP

### Non-Functional Requirements

1. **ACID Compliance**: Atomicity, Consistency, Isolation, Durability for all financial transactions
2. **Strong Consistency**: Account balances and positions must be accurate at all times
3. **Ultra-Low Latency**: <100μs matching engine, <10ms end-to-end order placement
4. **High Availability**: 99.99% uptime (4 nines), <30s failover
5. **Scalability**: Handle market open surges (10x average traffic)
6. **Security**: Multi-factor authentication, encryption, DDoS protection
7. **Regulatory Compliance**: SEC Rule 17a-4 (7-year retention), FINRA reporting

### Capacity Estimation

**Assumptions**:
- 10M total users, 2M daily active traders
- 5 trades per trader per day
- 2 orders per trade (including cancellations)
- 100:1 read-to-write ratio
- 10,000 symbols (stocks + ETFs)
- 10 market data updates per symbol per second

**Order Load**:
```
Orders/day: 2M × 5 × 2 = 20M orders/day
Trading day: 6.5 hours = 23,400 seconds
Average: 20M ÷ 23.4K = 855 orders/sec
Peak (10x at market open): 8,550 orders/sec
```

**Market Data Load**:
```
Symbols: 10,000
Updates/sec per symbol: 10
Total: 100,000 market data updates/sec
```

**Storage**:
```
Orders: 20M/day × 500 bytes × 252 days × 7 years = 17.6 TB
Trades: 10M/day × 1KB × 252 days × 7 years = 17.6 TB
Market data: 100K/sec × 200 bytes × 23.4K sec × 252 days = 11.8 TB/year
Total (7 years): ~120 TB
```

**Network Bandwidth**:
```
Market data: 100K updates/sec × 200 bytes = 20 MB/sec
Orders: 8.55K orders/sec × 500 bytes = 4.3 MB/sec
Total: ~25 MB/sec = 200 Mbps
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│              Trading Clients                        │
│     (Web, Mobile, Desktop, API, FIX Protocol)       │
└──────────────────────┬──────────────────────────────┘
                       │
┌──────────────────────▼──────────────────────────────┐
│              API Gateway / Load Balancer            │
│     (Kong, Rate Limiting, SSL Termination)          │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Auth Service    │  │  Order Service   │
│  (OAuth2, 2FA)   │  │  (Validation)    │
└──────────────────┘  └────────┬─────────┘
                               │
                    ┌──────────┴──────────┐
                    ▼                     ▼
         ┌─────────────────┐   ┌─────────────────┐
         │  Risk Engine    │   │ Matching Engine │
         │  (Pre-trade)    │   │  (C++/Rust)     │
         └─────────────────┘   └────────┬────────┘
                                        │
                    ┌───────────────────┴─────────┐
                    ▼                             ▼
         ┌─────────────────┐         ┌──────────────────┐
         │  PostgreSQL/    │         │  Kafka / Event   │
         │  CockroachDB    │         │   Streaming      │
         │  (ACID Store)   │         │                  │
         └─────────────────┘         └──────────────────┘
```

### Components

**1. API Gateway**:
- Protocol translation (REST, WebSocket, FIX)
- Rate limiting (100 orders/min free, 1000 orders/min pro)
- SSL/TLS termination
- Request validation
- Circuit breaker (prevent cascading failures)

**2. Authentication Service**:
- Multi-factor authentication (2FA, biometric)
- OAuth2 / JWT tokens (15-min expiry)
- Session management (30-min timeout)
- IP whitelisting for institutional clients
- Hardware token support (YubiKey)

**3. Order Management Service**:
- Order validation (symbol, quantity, price)
- Order state machine (PENDING → ACCEPTED → FILLED/CANCELLED)
- Order modification and cancellation
- Order routing to matching engine

**4. Risk Management Engine**:
- **Pre-trade checks**:
  - Buying power verification
  - Position limits (max shares per symbol)
  - Concentration limits (max % in single stock)
  - Order value limits (max single order size)
- **Post-trade monitoring**:
  - Margin calls (real-time)
  - Portfolio VaR (Value at Risk)
  - Circuit breakers

**5. Matching Engine** (Core component):
- **Implementation**: C++ or Rust for performance
- **Data structure**: In-memory order book (lock-free)
- **Matching algorithm**: Price-Time Priority (FIFO at same price)
- **Order types**: Market, Limit, Stop, Stop-Limit, IOC, FOK
- **Latency**: <100 microseconds (P99)
- **Throughput**: 1M+ orders per second per symbol

**6. Database (ACID-Compliant)**:
- **Choice**: PostgreSQL (SERIALIZABLE isolation) or CockroachDB (distributed ACID)
- **Sharding**: By user_id or symbol for horizontal scaling
- **Replication**: Synchronous replication (2-3 replicas)
- **Write-Ahead Logging**: Crash recovery and durability

**7. Market Data Service**:
- FIX protocol handler for exchange feeds
- Real-time price updates (Level 1: best bid/ask, Level 2: order book depth)
- WebSocket broadcast to subscribed clients
- Historical data storage (TimescaleDB)

**8. Event Streaming (Kafka)**:
- Topics: order.placed, order.filled, order.cancelled, market.data
- Exactly-once semantics (idempotent producers)
- Consumers: Analytics, Notifications, Compliance

---

## Step 3: Data Model (10 min)

### Database Choice

**PostgreSQL** (Strong ACID compliance) vs **CockroachDB** (Distributed ACID):
- PostgreSQL: Proven, mature, excellent for single-region with replicas
- CockroachDB: Multi-region, auto-sharding, built-in distributed transactions
- **Choice**: Start with PostgreSQL SERIALIZABLE, migrate to CockroachDB if multi-region needed

### Schema

```sql
-- Users Table
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    kyc_status VARCHAR(20) NOT NULL, -- PENDING, VERIFIED, REJECTED
    account_tier VARCHAR(20) NOT NULL, -- FREE, PRO, INSTITUTIONAL
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Accounts Table (Financial)
CREATE TABLE accounts (
    account_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    cash_balance DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    buying_power DECIMAL(15,2) NOT NULL DEFAULT 0.00, -- cash + margin
    margin_used DECIMAL(15,2) NOT NULL DEFAULT 0.00,
    account_value DECIMAL(15,2) NOT NULL DEFAULT 0.00, -- cash + positions
    updated_at TIMESTAMP DEFAULT NOW(),
    CONSTRAINT positive_balance CHECK (cash_balance >= 0)
);

CREATE INDEX idx_accounts_user ON accounts(user_id);

-- Orders Table
CREATE TABLE orders (
    order_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    symbol VARCHAR(10) NOT NULL,
    side VARCHAR(4) NOT NULL, -- BUY, SELL
    order_type VARCHAR(20) NOT NULL, -- MARKET, LIMIT, STOP, STOP_LIMIT
    quantity INTEGER NOT NULL,
    price DECIMAL(10,2), -- NULL for market orders
    stop_price DECIMAL(10,2), -- For stop orders
    time_in_force VARCHAR(10) NOT NULL DEFAULT 'DAY', -- DAY, GTC, IOC, FOK
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    filled_quantity INTEGER NOT NULL DEFAULT 0,
    avg_fill_price DECIMAL(10,2),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    filled_at TIMESTAMP,
    CONSTRAINT positive_quantity CHECK (quantity > 0),
    CONSTRAINT valid_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT valid_status CHECK (status IN ('PENDING', 'ACCEPTED', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'REJECTED'))
);

CREATE INDEX idx_orders_user ON orders(user_id, created_at DESC);
CREATE INDEX idx_orders_symbol ON orders(symbol, created_at DESC);
CREATE INDEX idx_orders_status ON orders(status);

-- Trades Table (Executions)
CREATE TABLE trades (
    trade_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    buy_order_id UUID NOT NULL REFERENCES orders(order_id),
    sell_order_id UUID NOT NULL REFERENCES orders(order_id),
    buyer_id BIGINT NOT NULL REFERENCES users(user_id),
    seller_id BIGINT NOT NULL REFERENCES users(user_id),
    symbol VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL,
    price DECIMAL(10,2) NOT NULL,
    amount DECIMAL(15,2) NOT NULL, -- quantity × price
    executed_at TIMESTAMP DEFAULT NOW(),
    settlement_date DATE NOT NULL -- T+2
);

CREATE INDEX idx_trades_buyer ON trades(buyer_id, executed_at DESC);
CREATE INDEX idx_trades_seller ON trades(seller_id, executed_at DESC);
CREATE INDEX idx_trades_symbol ON trades(symbol, executed_at DESC);

-- Positions Table (Holdings)
CREATE TABLE positions (
    position_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    symbol VARCHAR(10) NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 0,
    avg_cost DECIMAL(10,2) NOT NULL,
    market_value DECIMAL(15,2) NOT NULL,
    unrealized_pnl DECIMAL(15,2) NOT NULL,
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(account_id, symbol)
);

CREATE INDEX idx_positions_user ON positions(user_id);

-- Transactions Table (Ledger - Immutable Audit Trail)
CREATE TABLE transactions (
    txn_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_id BIGINT NOT NULL REFERENCES accounts(account_id),
    user_id BIGINT NOT NULL REFERENCES users(user_id),
    order_id UUID REFERENCES orders(order_id),
    trade_id UUID REFERENCES trades(trade_id),
    type VARCHAR(20) NOT NULL, -- BUY, SELL, DEPOSIT, WITHDRAWAL, FEE, DIVIDEND
    symbol VARCHAR(10),
    quantity INTEGER,
    price DECIMAL(10,2),
    amount DECIMAL(15,2) NOT NULL, -- Positive for credit, negative for debit
    balance_after DECIMAL(15,2) NOT NULL,
    description TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_transactions_account ON transactions(account_id, created_at DESC);
CREATE INDEX idx_transactions_user ON transactions(user_id, created_at DESC);

-- Market Data Table (Time-series)
CREATE TABLE market_data (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    open DECIMAL(10,2) NOT NULL,
    high DECIMAL(10,2) NOT NULL,
    low DECIMAL(10,2) NOT NULL,
    close DECIMAL(10,2) NOT NULL,
    volume BIGINT NOT NULL,
    PRIMARY KEY (symbol, timestamp)
);

-- Use TimescaleDB extension for efficient time-series queries
CREATE INDEX idx_market_data_symbol_time ON market_data(symbol, timestamp DESC);
```

---

## Step 4: APIs (5 min)

### Order Placement

```http
POST /api/v1/orders
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json

Request:
{
  "symbol": "AAPL",
  "side": "BUY",
  "order_type": "LIMIT",
  "quantity": 100,
  "price": 150.00,
  "time_in_force": "DAY"
}

Response: 201 Created
{
  "order_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "PENDING",
  "symbol": "AAPL",
  "side": "BUY",
  "order_type": "LIMIT",
  "quantity": 100,
  "price": 150.00,
  "filled_quantity": 0,
  "created_at": "2025-11-15T09:30:00.123Z"
}
```

### Order Cancellation

```http
DELETE /api/v1/orders/{order_id}
Authorization: Bearer <JWT_TOKEN>

Response: 200 OK
{
  "order_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "CANCELLED",
  "cancelled_at": "2025-11-15T09:31:00.456Z"
}
```

### Market Data (WebSocket)

```javascript
// Subscribe to real-time quotes
ws.send(JSON.stringify({
  "action": "subscribe",
  "symbols": ["AAPL", "TSLA", "MSFT"]
}));

// Receive updates
{
  "type": "quote",
  "symbol": "AAPL",
  "bid": 149.95,
  "ask": 150.05,
  "last": 150.00,
  "volume": 1250000,
  "timestamp": "2025-11-15T09:30:01.789Z"
}
```

### Portfolio

```http
GET /api/v1/portfolio
Authorization: Bearer <JWT_TOKEN>

Response: 200 OK
{
  "account_value": 125000.00,
  "cash_balance": 25000.00,
  "buying_power": 50000.00,
  "positions": [
    {
      "symbol": "AAPL",
      "quantity": 100,
      "avg_cost": 140.00,
      "current_price": 150.00,
      "market_value": 15000.00,
      "unrealized_pnl": 1000.00,
      "unrealized_pnl_percent": 7.14
    },
    {
      "symbol": "TSLA",
      "quantity": 50,
      "avg_cost": 200.00,
      "current_price": 220.00,
      "market_value": 11000.00,
      "unrealized_pnl": 1000.00,
      "unrealized_pnl_percent": 10.00
    }
  ]
}
```

---

## Step 5: Core Algorithm - Order Matching Engine

### Matching Algorithm: Price-Time Priority

```python
from dataclasses import dataclass
from typing import List, Optional
from decimal import Decimal
import heapq
from collections import deque

@dataclass
class Order:
    order_id: str
    symbol: str
    side: str  # 'BUY' or 'SELL'
    quantity: int
    price: Optional[Decimal]  # None for market orders
    timestamp: int
    user_id: int

class OrderBook:
    def __init__(self, symbol: str):
        self.symbol = symbol
        # Buy orders: max heap (highest price first)
        self.bids = []  # [(-price, timestamp, order)]
        # Sell orders: min heap (lowest price first)
        self.asks = []  # [(price, timestamp, order)]
        
    def add_order(self, order: Order) -> List[tuple]:
        """Add order and return list of trades (matches)"""
        trades = []
        
        if order.side == 'BUY':
            # Try to match with sell orders
            while self.asks and order.quantity > 0:
                ask_price, ask_time, sell_order = self.asks[0]
                
                # Market order or limit price >= ask price
                if order.price is None or order.price >= ask_price:
                    # Match found!
                    trade_quantity = min(order.quantity, sell_order.quantity)
                    trade_price = ask_price  # Aggressor takes maker's price
                    
                    trades.append((order, sell_order, trade_quantity, trade_price))
                    
                    order.quantity -= trade_quantity
                    sell_order.quantity -= trade_quantity
                    
                    if sell_order.quantity == 0:
                        heapq.heappop(self.asks)
                else:
                    break
            
            # Add remaining quantity to order book
            if order.quantity > 0 and order.price is not None:
                heapq.heappush(self.bids, (-order.price, order.timestamp, order))
                
        else:  # SELL
            # Try to match with buy orders
            while self.bids and order.quantity > 0:
                neg_bid_price, bid_time, buy_order = self.bids[0]
                bid_price = -neg_bid_price
                
                # Market order or limit price <= bid price
                if order.price is None or order.price <= bid_price:
                    # Match found!
                    trade_quantity = min(order.quantity, buy_order.quantity)
                    trade_price = bid_price  # Aggressor takes maker's price
                    
                    trades.append((buy_order, order, trade_quantity, trade_price))
                    
                    order.quantity -= trade_quantity
                    buy_order.quantity -= trade_quantity
                    
                    if buy_order.quantity == 0:
                        heapq.heappop(self.bids)
                else:
                    break
            
            # Add remaining quantity to order book
            if order.quantity > 0 and order.price is not None:
                heapq.heappush(self.asks, (order.price, order.timestamp, order))
        
        return trades
    
    def cancel_order(self, order_id: str) -> bool:
        """Cancel order by removing from order book"""
        # In production, use OrderId → Order mapping for O(1) cancellation
        # This is simplified for illustration
        pass
```

**Note**: Production matching engines use:
- **C++/Rust** for ultra-low latency (<100μs)
- **Lock-free data structures** (concurrent queues)
- **CPU pinning** (dedicated cores)
- **DPDK** (kernel bypass networking)
- **RDMA** (remote direct memory access)

---

## Step 6: ACID Transaction Example

### Trade Execution (Buyer & Seller Update)

```sql
-- Execute trade: Buyer purchases 100 AAPL @ $150 from Seller
-- Both accounts must be updated atomically

BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- 1. Debit buyer's cash
UPDATE accounts
SET cash_balance = cash_balance - 15050.00,  -- $150 × 100 + $50 fee
    buying_power = buying_power - 15050.00,
    updated_at = NOW()
WHERE user_id = 123 AND cash_balance >= 15050.00;
-- If row not affected → insufficient funds → ROLLBACK

-- 2. Credit buyer's position
INSERT INTO positions (user_id, account_id, symbol, quantity, avg_cost, market_value)
VALUES (123, 456, 'AAPL', 100, 150.00, 15000.00)
ON CONFLICT (account_id, symbol)
DO UPDATE SET
    quantity = positions.quantity + 100,
    avg_cost = ((positions.quantity * positions.avg_cost) + 15000.00) / (positions.quantity + 100),
    market_value = positions.market_value + 15000.00,
    updated_at = NOW();

-- 3. Credit seller's cash
UPDATE accounts
SET cash_balance = cash_balance + 14950.00,  -- $15,000 - $50 fee
    buying_power = buying_power + 14950.00,
    updated_at = NOW()
WHERE user_id = 789;

-- 4. Debit seller's position
UPDATE positions
SET quantity = quantity - 100,
    market_value = market_value - 15000.00,
    updated_at = NOW()
WHERE user_id = 789 AND symbol = 'AAPL' AND quantity >= 100;
-- If row not affected → insufficient shares → ROLLBACK

-- 5. Record trade
INSERT INTO trades (buy_order_id, sell_order_id, buyer_id, seller_id, symbol, quantity, price, amount, settlement_date)
VALUES ('ord-buyer-123', 'ord-seller-789', 123, 789, 'AAPL', 100, 150.00, 15000.00, CURRENT_DATE + INTERVAL '2 days');

-- 6. Record transactions (immutable ledger)
INSERT INTO transactions (account_id, user_id, order_id, trade_id, type, symbol, quantity, price, amount, balance_after)
VALUES
  (456, 123, 'ord-buyer-123', 'trade-abc', 'BUY', 'AAPL', 100, 150.00, -15050.00, 
   (SELECT cash_balance FROM accounts WHERE user_id = 123)),
  (789, 789, 'ord-seller-789', 'trade-abc', 'SELL', 'AAPL', 100, 150.00, 14950.00,
   (SELECT cash_balance FROM accounts WHERE user_id = 789));

-- 7. Update order statuses
UPDATE orders SET status = 'FILLED', filled_quantity = quantity, filled_at = NOW() WHERE order_id IN ('ord-buyer-123', 'ord-seller-789');

COMMIT;
-- If ANY step fails, entire transaction rolls back - ACID Atomicity
```

**ACID Guarantees**:
- **Atomicity**: All 7 steps succeed together or all fail - no partial execution
- **Consistency**: Account balances + positions always match (invariants maintained)
- **Isolation**: SERIALIZABLE prevents concurrent trades from interfering
- **Durability**: Once committed, trade survives crashes (WAL ensures persistence)

---

## Step 7: Optimizations & Trade-offs (20 min)

### 1. Matching Engine Performance

**C++ Implementation** (Production-grade):
```cpp
// Ultra-low latency matching engine
class OrderBook {
private:
    std::map<Price, std::deque<Order*>, std::greater<>> bids_;  // Sorted buy orders
    std::map<Price, std::deque<Order*>> asks_;                  // Sorted sell orders
    std::unordered_map<OrderId, Order*> order_index_;           // O(1) lookup
    
public:
    std::vector<Trade> AddOrder(Order* order) {
        // Lock-free or use fine-grained locking
        std::vector<Trade> trades;
        
        if (order->side == Side::BUY) {
            while (!asks_.empty() && order->quantity > 0) {
                auto& [ask_price, ask_queue] = *asks_.begin();
                if (order->price >= ask_price || order->type == OrderType::MARKET) {
                    // Match!
                    // ... matching logic
                }
            }
        }
        
        return trades;  // Return trades in <100 microseconds
    }
};
```

**Optimizations**:
- In-memory order book (no disk I/O)
- Lock-free data structures (concurrent access)
- CPU pinning (dedicated cores, no context switching)
- NUMA-aware memory allocation
- Branch prediction optimization
- Cache-line alignment (avoid false sharing)

### 2. Database Sharding Strategy

**Shard by User ID**:
```
Shard 0: user_id % 10 == 0
Shard 1: user_id % 10 == 1
...
Shard 9: user_id % 10 == 9

Benefits:
- User's orders and trades on same shard (query locality)
- Horizontal scaling (add more shards as users grow)
- Simple routing logic

Drawbacks:
- Cross-shard queries for analytics
- Rebalancing when adding shards
```

**Shard by Symbol**:
```
Shard 0: A-C symbols (AAPL, BAC, CSCO)
Shard 1: D-F symbols (DIS, FB, GOOG)
...

Benefits:
- Symbol-specific queries fast (market data, order book)
- Hot symbols isolated (TSLA, GME don't affect others)

Drawbacks:
- User's orders spread across shards
- Cross-shard transactions for trades
```

**Hybrid Approach** (Recommended):
- Shard users by user_id
- Shard market data and analytics by symbol
- Use distributed transactions (2PC) for cross-shard trades

### 3. Caching Strategy

**Redis Caching**:
```python
def get_account_balance(user_id):
    cache_key = f"account:{user_id}:balance"
    
    # Check cache
    cached = redis.get(cache_key)
    if cached:
        return Decimal(cached)
    
    # Cache miss - query database
    balance = db.query("SELECT cash_balance FROM accounts WHERE user_id = ?", user_id)
    
    # Cache with 1-minute TTL (balances change frequently)
    redis.setex(cache_key, 60, str(balance))
    
    return balance

def invalidate_account_cache(user_id):
    """Call this after every trade"""
    redis.delete(f"account:{user_id}:balance")
```

**Cache Invalidation Strategy**:
- **Write-through**: Update cache on every write (strong consistency)
- **Write-behind**: Update cache asynchronously (eventual consistency - NOT for balances!)
- **Event-driven**: Listen to Kafka `order.filled` events, invalidate cache

### 4. Market Data Distribution

**WebSocket Pub/Sub**:
```javascript
// Server-side (Node.js)
const wss = new WebSocketServer({ port: 8080 });
const subscriptions = new Map(); // user_id → Set<symbols>

wss.on('connection', (ws, req) => {
    const userId = authenticateUser(req);
    
    ws.on('message', (data) => {
        const msg = JSON.parse(data);
        
        if (msg.action === 'subscribe') {
            subscriptions.set(userId, new Set(msg.symbols));
        }
    });
});

// Kafka consumer: market.data topic
kafka.consumer.on('message', (msg) => {
    const { symbol, price, volume } = JSON.parse(msg.value);
    
    // Broadcast to subscribed users
    for (const [userId, symbols] of subscriptions.entries()) {
        if (symbols.has(symbol)) {
            const ws = getUserWebSocket(userId);
            ws.send(JSON.stringify({ symbol, price, volume }));
        }
    }
});
```

**Performance**:
- Support 100K+ concurrent WebSocket connections per server
- Use Redis Pub/Sub for multi-server broadcasting
- Compress messages (gzip, LZ4)
- Batch updates (send every 100ms instead of every tick)

### 5. Risk Management

**Pre-Trade Checks** (Sub-millisecond):
```java
@Service
public class RiskEngine {
    public RiskCheckResult checkOrder(Order order, Account account) {
        // 1. Buying power check
        BigDecimal requiredCash = order.getPrice()
            .multiply(BigDecimal.valueOf(order.getQuantity()))
            .add(calculateFees(order));
        
        if (account.getBuyingPower().compareTo(requiredCash) < 0) {
            return RiskCheckResult.reject("Insufficient buying power");
        }
        
        // 2. Position limit check
        Position position = positionRepo.findByUserAndSymbol(order.getUserId(), order.getSymbol());
        int currentQuantity = position != null ? position.getQuantity() : 0;
        int newQuantity = currentQuantity + order.getQuantity();
        
        if (newQuantity > 1000) {  // Max 1000 shares per symbol
            return RiskCheckResult.reject("Position limit exceeded");
        }
        
        // 3. Concentration limit check
        BigDecimal portfolioValue = account.getAccountValue();
        BigDecimal positionValue = order.getPrice().multiply(BigDecimal.valueOf(newQuantity));
        BigDecimal concentration = positionValue.divide(portfolioValue, 4, RoundingMode.HALF_UP);
        
        if (concentration.compareTo(new BigDecimal("0.25")) > 0) {  // Max 25% in one stock
            return RiskCheckResult.reject("Concentration limit exceeded");
        }
        
        return RiskCheckResult.approved();
    }
}
```

---

## Step 8: Advanced Considerations

### 1. Settlement Process (T+2)

**Day T+0 (Trade Date)**:
- Trade executed and confirmed
- Reported to clearinghouse (DTCC/NSCC)
- Clearinghouse performs novation (becomes counterparty)

**Day T+1**:
- Trade comparison and netting
- Calculate net obligations across all trades
- Reduces settlement volume by ~98%

**Day T+2 (Settlement Date)**:
- DTC transfers shares electronically
- Federal Reserve transfers cash (Fedwire)
- Settlement finalized, positions fully settled

**Implementation**:
```java
@Scheduled(cron = "0 0 16 * * ?")  // 4:00 PM daily
public void initiateSettlement() {
    LocalDate settlementDate = LocalDate.now().plusDays(2);
    
    List<Trade> unsettledTrades = tradeRepo.findBySettlementDate(settlementDate);
    
    for (Trade trade : unsettledTrades) {
        // Send to clearinghouse (DTCC)
        clearinghouseClient.submitTrade(trade);
        
        // Update status
        trade.setSettlementStatus(SettlementStatus.PENDING);
        tradeRepo.save(trade);
    }
}
```

### 2. Regulatory Compliance

**SEC Rule 17a-4** (Record Retention):
- 7-year retention for orders, trades, communications
- WORM storage (Write-Once-Read-Many)
- Immutable audit trail (append-only ledger)

**FINRA CAT** (Consolidated Audit Trail):
- Report all orders, executions, cancellations
- Submit within 1 business day
- Include lifecycle events

**Implementation**:
```java
@Service
public class ComplianceService {
    @Async
    public void reportToCAT(Order order) {
        CATRecord record = CATRecord.builder()
            .orderID(order.getOrderId())
            .symbol(order.getSymbol())
            .side(order.getSide())
            .quantity(order.getQuantity())
            .price(order.getPrice())
            .timestamp(order.getCreatedAt())
            .userId(order.getUserId())
            .build();
        
        catClient.submit(record);
    }
    
    @Scheduled(cron = "0 0 1 * * ?")  // 1:00 AM daily
    public void dailyReporting() {
        // Generate T+1 reports for FINRA
        // Archive to S3 Glacier (7-year retention)
    }
}
```

### 3. Disaster Recovery

**Multi-Region Failover**:
```
Primary Region (US-East-1):
- Active-Active: Order Service, API Gateway
- Active: Matching Engine, Database Primary

DR Region (US-West-2):
- Standby: Matching Engine, Database Replica
- Continuous replication (RPO = 0)

Failover Process:
1. Detect primary region failure (health checks)
2. Promote DR database replica to primary (<30s)
3. Redirect traffic to DR region (DNS update)
4. Resume trading (RTO < 60s)
```

**Testing**:
- Monthly DR drills (simulated failover)
- Chaos engineering (inject failures)
- Load testing with 10x peak traffic

### 4. Security

**Authentication**:
- Multi-factor authentication (2FA required for withdrawals)
- Biometric (fingerprint, Face ID)
- Hardware tokens (YubiKey) for institutional clients

**Authorization**:
- RBAC (Role-Based Access Control)
- Least privilege principle
- Separate read/write permissions

**Encryption**:
- TLS 1.3 for data in transit
- AES-256 for data at rest
- HSM (Hardware Security Module) for key storage

**Monitoring**:
- Real-time fraud detection (ML models)
- Anomaly detection (unusual trading patterns)
- IP reputation scoring
- Device fingerprinting

---

## Interview Tips

**Questions to Ask**:
- Expected scale (DAU, order volume)?
- Latency requirements (retail vs HFT)?
- Geographic distribution (single-region vs multi-region)?
- Regulatory jurisdiction (US, EU, Asia)?
- Asset classes (stocks, options, crypto)?

**Topics to Cover**:
- ACID compliance (critical for financial systems)
- Matching engine algorithm (price-time priority)
- Database choice (PostgreSQL SERIALIZABLE)
- Risk management (pre-trade checks)
- Market data distribution (WebSocket pub/sub)

**Common Follow-ups**:
- "How do you prevent double-spending?"
  → SERIALIZABLE isolation + optimistic locking
- "How do you handle flash crashes?"
  → Circuit breakers (halt trading at 10% drop), kill switch
- "How do you ensure zero data loss?"
  → Synchronous replication + WAL + ACID transactions
- "How do you scale matching engine?"
  → Shard by symbol, C++ lock-free data structures

---

## Key Takeaways

1. **ACID is Non-Negotiable**: Every cent must be accounted for - use SERIALIZABLE isolation
2. **Latency Hierarchy**: Matching (<100μs) << Orders (<10ms) << Analytics (<1s)
3. **Peak Planning**: Design for 10x surge at market open, not average load
4. **BigDecimal for Money**: NEVER use float/double for financial calculations
5. **7-Year Retention**: Regulatory requirement (SEC Rule 17a-4)
6. **Zero Data Loss**: RPO = 0 (synchronous replication + WAL)
7. **C++ for Matching**: Ultra-low latency requires systems programming language

---

*Built for: Robinhood-scale trading platforms, Fintech interviews, Real-money gaming systems*
