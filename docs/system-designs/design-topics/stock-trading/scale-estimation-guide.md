# 💹 Stock Trading System: Scale Estimation Masterclass

## The FINANCE Technique for Trading Systems
**(F)inancial constraints → (I)nventory math → (N)etwork capacity → (A)CID overhead → (N)umbers validation → (C)oncurrency → (E)xplosive growth**

This framework ensures you account for the unique demands of financial systems where **correctness > speed** but **both are required**.

---

## 📊 PART 1: Trading System Scale Characteristics

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Active Traders | 10M | Mid-sized brokerage (Robinhood-scale) |
| | Daily Active Traders | 2M | ~20% of total (active trading) |
| | Institutional vs Retail | 80:20 | Retail-focused platform |
| **Trading Patterns** | Avg trades per user/day | 5 | Active trader average |
| | Orders per trade | 2 | Including cancellations/modifications |
| | Peak:Average ratio | 10x | Market open (9:30 AM ET) surge |
| | Read:Write ratio | 100:1 | Viewing >> Trading |
| **Market Data** | Symbols tracked | 10,000 | US stocks + ETFs |
| | Updates per symbol/sec | 10 | Active trading hours |
| | Market hours | 6.5 hrs/day | 9:30 AM - 4:00 PM ET |
| **Financial Constraints** | ACID transaction overhead | 3-5x | Compared to non-ACID systems |
| | Zero data loss tolerance | RPO = 0 | Regulatory requirement |
| | Audit trail retention | 7 years | SEC Rule 17a-4 |

---

## 🧮 PART 2: Financial Systems Calculator - Mental Math for Money

### Rule #1: **The Trading Day Ladder**
```
Market Hours = 6.5 hours = 390 minutes = 23,400 seconds

Remember these anchors:
• Trading day = ~23K seconds (23,400 exactly)
• Trading week = 5 days = ~117K seconds
• Trading year = 252 days (exclude weekends + holidays)
• 1 order = ~500 bytes (order object + metadata)
• 1 trade = ~1KB (includes both sides + execution details)
```

### Rule #2: **The Peak Multiplier**
```
Market Open (9:30-10:00 AM): 10x average traffic
Market Close (3:30-4:00 PM): 5x average traffic
Mid-day (11:00 AM-2:00 PM): 0.5x average traffic

ALWAYS size for peak, not average!
```

### Rule #3: **The ACID Tax**
```
Traditional System:
- Write latency: 1ms
- Throughput: 100K writes/sec

ACID-Compliant Financial System:
- Write latency: 3-5ms (3-5x overhead)
- Throughput: 20-30K writes/sec (due to locks, WAL)
- BUT: Zero data loss, strong consistency

Mental shortcut: Multiply capacity requirements by 3-4x for ACID
```

---

## 📈 PART 3: Stock Trading Scale Math Template

```
┌─────────────────────────────────────────────────────────┐
│  💹 TRADING SYSTEM NAPKIN MATH - Universal Template     │
└─────────────────────────────────────────────────────────┘

STEP 1: ORDER VOLUME ESTIMATION
────────────────────────────────
Active Traders (DAU):        [____] M
Trades per Trader/Day:       [____]
Orders per Trade:            [____] (include cancellations)

→ Total Orders/Day = DAU × Trades × Orders = [____] M
→ Trading Day = 23K seconds
→ Average Order QPS = Orders/Day ÷ 23K = [____]
→ Peak QPS (10x) = Avg × 10 = [____]

STEP 2: MARKET DATA ESTIMATION
────────────────────────────────
Symbols Tracked:             [____] K
Updates per Symbol/Sec:      [____]
Market Hours:                6.5 hrs = 23K sec

→ Market Data QPS = Symbols × Updates = [____] K
→ Data Size per Update = ~200 bytes (ticker + OHLCV)
→ Bandwidth = QPS × Size = [____] MB/sec

STEP 3: STORAGE ESTIMATION (7-year retention)
────────────────────────────────
Order Size:                  ~500 bytes
Trade Size:                  ~1 KB
Orders per Day:              [____] M
Trades per Day:              [____] M (50% fill rate)

→ Daily Storage = (Orders × 500B) + (Trades × 1KB) = [____] GB
→ Yearly Storage = Daily × 252 trading days = [____] TB
→ 7-Year Storage = Yearly × 7 = [____] TB

STEP 4: DATABASE CAPACITY (ACID requirements)
────────────────────────────────
Peak Order QPS:              [____]
ACID Overhead:               3-5x slower than non-ACID

→ Database Write Capacity = Peak QPS × 5 = [____]
→ Connection Pool Size = Peak QPS × 0.1 = [____]
→ WAL Size = Daily Orders × 1KB = [____] GB/day

STEP 5: NETWORK CAPACITY
────────────────────────────────
WebSocket Connections:       [____] (1 per active user)
Market Data per Connection:  [____] symbols subscribed
Updates per Symbol:          10/sec

→ WebSocket Bandwidth = Connections × Symbols × 10 × 200B = [____] MB/s
→ Order Traffic = Peak Order QPS × 500B = [____] MB/s
→ Total Bandwidth = WebSocket + Orders = [____] MB/s
```

---

## 💾 PART 4: Stock Trading System - Filled Example

```
┌─────────────────────────────────────────────────────────┐
│      ROBINHOOD-SCALE TRADING SYSTEM - NAPKIN MATH       │
└─────────────────────────────────────────────────────────┘

STEP 1: ORDER VOLUME ESTIMATION
────────────────────────────────
Active Traders (DAU):        2 M
Trades per Trader/Day:       5
Orders per Trade:            2 (1 placement + 1 cancel/modify)

→ Total Orders/Day = 2M × 5 × 2 = 20 M orders/day
→ Average Order QPS = 20M ÷ 23K = ~870 QPS
→ Peak QPS (10x at market open) = 870 × 10 = 8,700 QPS

STEP 2: MARKET DATA ESTIMATION
────────────────────────────────
Symbols Tracked:             10,000
Updates per Symbol/Sec:      10 (during active trading)
Market Hours:                6.5 hrs = 23K sec

→ Market Data QPS = 10K × 10 = 100,000 updates/sec
→ Data Size per Update = ~200 bytes
→ Bandwidth = 100K × 200B = 20 MB/sec = 160 Mbps

STEP 3: STORAGE ESTIMATION (7-year retention)
────────────────────────────────
Order Size:                  500 bytes
Trade Size:                  1 KB
Orders per Day:              20 M
Trades per Day:              10 M (50% fill rate)

→ Daily Storage = (20M × 500B) + (10M × 1KB)
                = 10 GB + 10 GB = 20 GB/day
→ Yearly Storage = 20 GB × 252 days = 5 TB/year
→ 7-Year Storage = 5 TB × 7 = 35 TB
→ Add 50% buffer for indexes, audit logs = 52 TB total

STEP 4: DATABASE CAPACITY (ACID requirements)
────────────────────────────────
Peak Order QPS:              8,700
ACID Overhead:               3-5x (using PostgreSQL SERIALIZABLE)

→ Database must handle = 8,700 × 5 = 43,500 ops/sec
→ Sharding required: 10 shards @ 4,350 ops/sec each
→ Connection Pool: 8,700 × 0.1 = 870 connections
→ WAL Size = 20M × 1KB = 20 GB/day (write-ahead log)

STEP 5: NETWORK CAPACITY
────────────────────────────────
WebSocket Connections:       2 M (1 per active trader)
Avg Symbols per User:        5
Updates per Symbol:          10/sec

→ Total Market Data Stream = 2M × 5 × 10 × 200B = 20 GB/s
   (This is impractical! Use topic-based pub/sub)
→ Realistic (with pub/sub): 100K concurrent × 5 × 10 × 200B = 100 MB/s
→ Order Traffic = 8.7K QPS × 500B = 4.35 MB/s
→ Total Bandwidth = 100 + 4.35 = ~105 MB/s = 840 Mbps
```

---

## 🧠 PART 5: Financial Systems Mental Math Techniques

### **Technique 1: The "Trading Day Simplifier"**
```
Instead of: 20M orders/day ÷ 23,400 seconds
Think:      20M ÷ 20K ≈ 1,000 orders/sec

Trading day ≈ 20K seconds (easier than 23.4K)
Error margin: ~15% underestimate (conservative sizing)
```

### **Technique 2: The "Fill Rate Assumption"**
```
Orders placed: 100%
Orders filled: 40-60% (assume 50%)
Orders cancelled: 30%
Orders expired: 10-20%

Quick calc: Trades = Orders × 0.5
```

### **Technique 3: The "Peak Hour Multiplier Stack"**
```
Base QPS: 1,000
Market open (9:30 AM): 1,000 × 10 = 10,000 QPS
Earnings announcements: 10,000 × 2 = 20,000 QPS
Flash crash / black swan: 20,000 × 5 = 100,000 QPS

Design for 3 tiers:
- Normal peak: 10x average
- Exceptional events: 20x average  
- Circuit breakers: Halt trading at 25x average
```

### **Technique 4: The "ACID Overhead Factor"**
```
NO ACID (NoSQL, eventual consistency):
- Latency: 1ms
- Throughput: 100K writes/sec

WITH ACID (PostgreSQL SERIALIZABLE):
- Latency: 5ms (5x slower)
- Throughput: 20K writes/sec (5x less)

Mental shortcut:
Multiply latency by 5
Divide throughput by 5
```

---

## 🎯 PART 6: The Visual Trading System Map

```
                    💹 TRADING SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 SCALE          💾 STORAGE        🔧 COMPUTE
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
   DAU   Peak       Size    Years    Servers  ACID
   2M    8.7K       35TB     7yr       100    5x tax
```

**Memory Trigger**: Think **"S.S.C. + ACID"** = Scale, Storage, Compute + ACID overhead

---

## 🏗️ PART 7: Domain Model for Trading System

```java
// Financial domain entities - think ACID first!

@Entity
@Table(name = "orders")
class Order {
    UUID orderId;           // Unique, immutable
    Long userId;
    String symbol;          // AAPL, TSLA, etc.
    OrderSide side;         // BUY, SELL
    OrderType type;         // MARKET, LIMIT, STOP
    BigDecimal quantity;    // Use BigDecimal for money!
    BigDecimal price;       // NEVER use float/double
    OrderStatus status;     // PENDING, FILLED, CANCELLED
    Instant createdAt;
    Instant updatedAt;
    
    // Scale Insight: 20M of these per day!
    // Storage: ~500 bytes each = 10 GB/day
}

@Entity
@Table(name = "trades")
class Trade {
    UUID tradeId;
    UUID buyOrderId;
    UUID sellOrderId;
    String symbol;
    BigDecimal quantity;
    BigDecimal price;
    BigDecimal amount;      // quantity × price
    Instant executedAt;
    
    // Scale Insight: 10M trades/day
    // Storage: ~1 KB each = 10 GB/day
    // ACID: Both buyer and seller accounts updated atomically!
}

@Entity
@Table(name = "accounts")
class Account {
    Long userId;
    BigDecimal cashBalance;     // Available cash
    BigDecimal buyingPower;     // Cash + margin
    Instant lastUpdated;
    
    // CRITICAL: Every trade updates 2 accounts atomically
    // Isolation level: SERIALIZABLE (prevent double-spend)
}
```

---

## 🎯 PART 8: Trading System Interview Cheat Sheet

```
┌──────────────────────────────────────────────────┐
│  TRADING SYSTEM SCALE ESTIMATION - 5 MIN RITUAL  │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify user base: DAU, institutional vs retail
[ ] 2. Trading patterns: Orders/user, peak hours
[ ] 3. Calculate order QPS: Orders/day ÷ 23K × 10 (peak)
[ ] 4. Market data: Symbols × Updates/sec
[ ] 5. Storage: 7-year retention (regulatory)
[ ] 6. ACID tax: 5x database capacity
[ ] 7. Network: WebSocket for market data streaming
[ ] 8. Smell test: Can system handle flash crash?
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Peak Order QPS** | 8,700 | Size matching engine, order service |
| **Market Data QPS** | 100,000 | WebSocket server capacity |
| **Storage (7 years)** | 52 TB | Regulatory compliance (SEC 17a-4) |
| **Database Shards** | 10 | ACID transactions at scale |
| **WebSocket Connections** | 100K concurrent | Real-time data delivery |
| **ACID Overhead** | 5x | Latency and throughput impact |
| **Matching Latency** | <100μs | Competitive advantage |
| **RPO** | ZERO | No data loss tolerance |

---

## 💡 Senior Architect Tips for Financial Systems

### **Tip 1: The Money Smell Test**
After calculations, ask:
- "Can we lose a single penny?" → NO (ACID required)
- "Can we show wrong balance?" → NO (strong consistency)
- "Can we execute at wrong price?" → NO (serializable isolation)
- "Can we survive flash crash?" → YES (circuit breakers)

### **Tip 2: The Regulatory Reality Check**
- **7-year retention**: Not negotiable (SEC Rule 17a-4)
- **WORM storage**: Write-Once-Read-Many for audit trails
- **Sub-second recovery**: RTO < 30s, RPO = 0
- **Audit everything**: Every state change logged

### **Tip 3: The Latency Hierarchy**
```
Matching Engine:    <100 microseconds  (C++, in-memory)
Order Placement:    <10 milliseconds   (Java, ACID DB)
Market Data:        <10 milliseconds   (WebSocket)
Settlement:         2 business days    (T+2, clearinghouse)
Reporting:          1 minute           (Analytics, batch)
```

### **Tip 4: The Peak Patterns**
```
Daily Peaks:
09:30 AM ET: Market open (10x surge)
04:00 PM ET: Market close (5x surge)
10:00 AM ET: Economic data releases (3x surge)

Weekly Peaks:
Monday: Earnings reactions
Friday: Options expiration (monthly: 3rd Friday)

Annual Peaks:
Q4 earnings season
Tax loss harvesting (December)
```

---

## 🎓 Professor's Financial Systems Wisdom

> **"In financial systems, being WRONG costs money. Being SLOW costs customers. Being DOWN costs your business. ACID compliance is the price of admission."**

Your interviewer wants to see:
1. ✅ Understanding of ACID constraints
2. ✅ Peak hour planning (not just average)
3. ✅ Regulatory awareness (7-year retention)
4. ✅ Money is BigDecimal (NEVER float!)
5. ✅ Zero data loss tolerance

**NOT NEEDED:**
- ❌ Sub-microsecond latency (unless HFT)
- ❌ Eventual consistency (too risky)
- ❌ 100% uptime (99.99% is acceptable)

---

## 📚 Quick Reference: Trading System Benchmarks

| **Platform** | **DAU** | **Peak Order QPS** | **Market Data QPS** | **Latency (P99)** |
|--------------|---------|-------------------|---------------------|-------------------|
| **Robinhood** | 10M | ~50K | 500K | 50ms |
| **Coinbase** | 5M | ~30K | 200K | 100ms |
| **Interactive Brokers** | 1M | ~20K | 100K | 10ms (pro) |
| **Nasdaq Exchange** | N/A | 1M+ | 10M+ | 100μs |
| **NYSE** | N/A | 500K | 5M | 200μs |

---

## 🔧 Practical Application: Scale Planning

### **Small Trading Platform** (100K DAU)
```
Orders/day:        1M
Peak QPS:          ~500
Market data:       10K updates/sec
Storage (7yr):     5 TB
Database:          Single PostgreSQL (with replicas)
Matching:          In-memory, single instance
Cost:              ~$50K/month
```

### **Medium Trading Platform** (1M DAU) 
```
Orders/day:        10M
Peak QPS:          ~5K
Market data:       50K updates/sec
Storage (7yr):     30 TB
Database:          Sharded PostgreSQL (5 shards)
Matching:          Distributed, per-symbol routing
Cost:              ~$200K/month
```

### **Large Trading Platform** (10M DAU) - Robinhood Scale
```
Orders/day:        100M
Peak QPS:          ~50K
Market data:       500K updates/sec
Storage (7yr):     300 TB
Database:          Sharded CockroachDB (20+ shards)
Matching:          Distributed C++ engine cluster
Cost:              ~$1M/month
```

---

## 🎯 Mental Math Practice Problem: Options Trading

### Problem: Design options trading extension
```
Given:
- Existing stock platform: 2M DAU
- Options expiration: Every Friday (weekly) + 3rd Friday (monthly)
- Options contracts: 50,000 (stocks × strikes × expiries)
- Options trades: 10% of stock order volume normally
- Options expiration day: 100x normal volume (!!!)

Calculate:
1. Normal options order QPS
2. Expiration Friday peak QPS
3. Additional storage requirements
4. Database capacity increase

[Try it yourself, then check answer below]
```

<details>
<summary>Answer</summary>

```
1. NORMAL OPTIONS ORDER QPS:
   - Base stock orders: 20M/day → 870 QPS avg
   - Options: 10% → 2M/day → 87 QPS avg
   - Peak (10x): 870 QPS

2. EXPIRATION FRIDAY PEAK:
   - 100x normal options volume
   - 87 QPS × 100 = 8,700 QPS (just for options!)
   - Plus regular stock trading: 870 QPS
   - Total peak: 9,570 QPS

3. STORAGE (options are complex):
   - Options order: ~800 bytes (more fields)
   - 2M options/day × 800B = 1.6 GB/day
   - Yearly: 1.6GB × 252 = 403 GB
   - 7-year: 2.8 TB (just options)

4. DATABASE CAPACITY:
   - Need to handle 9,570 QPS × 5 (ACID) = 47,850 ops/sec
   - Increase from 43,500 to 47,850 = +10% capacity
   - BUT: Expiration day is predictable, can scale temporarily
```
</details>

---

## 🚨 Common Mistakes in Trading System Design

### Mistake 1: **Using Float for Money**
```
✗ BAD:  double price = 150.25;
        double quantity = 100;
        double total = price * quantity; // 15025.000000001 😱

✓ GOOD: BigDecimal price = new BigDecimal("150.25");
        BigDecimal quantity = new BigDecimal("100");
        BigDecimal total = price.multiply(quantity); // 15025.00 exactly
```

### Mistake 2: **Ignoring Peak Hours**
```
✗ BAD:  "We need 1,000 QPS capacity" (average load)
✓ GOOD: "We need 10,000 QPS for market open, 20K for earnings"
```

### Mistake 3: **Eventual Consistency for Money**
```
✗ BAD:  "Use Cassandra for orders" (eventual consistency)
✓ GOOD: "Use PostgreSQL SERIALIZABLE for orders" (strong consistency)
```

### Mistake 4: **Forgetting Regulatory Requirements**
```
✗ BAD:  "Store orders for 1 year"
✓ GOOD: "7-year retention per SEC Rule 17a-4, WORM storage"
```

### Mistake 5: **Under-estimating ACID Overhead**
```
✗ BAD:  "PostgreSQL can handle 100K writes/sec"
✓ GOOD: "PostgreSQL SERIALIZABLE: 20K writes/sec realistically"
```

---

## 📝 Trading System Template (Fill-in-the-Blank)

```
TRADING SYSTEM: ___________________

STEP 1: ORDER VOLUME
────────────────────────────────
Active Traders (DAU):        [____] M
Trades per Trader/Day:       [____]
Orders per Trade:            [____]

Total Orders/Day:            [____] M
Average Order QPS:           [____]
Peak QPS (10x):              [____]

STEP 2: MARKET DATA
────────────────────────────────
Symbols:                     [____] K
Updates per Symbol/Sec:      [____]
Market Data QPS:             [____] K
Bandwidth:                   [____] MB/s

STEP 3: STORAGE (7 YEARS)
────────────────────────────────
Orders per Day:              [____] M
Trades per Day:              [____] M
Daily Storage:               [____] GB
7-Year Storage:              [____] TB

STEP 4: DATABASE (ACID)
────────────────────────────────
Peak QPS:                    [____]
ACID Multiplier:             5x
Required Capacity:           [____] ops/sec
Shards Needed:               [____]

STEP 5: NETWORK
────────────────────────────────
WebSocket Connections:       [____] K
Market Data Bandwidth:       [____] MB/s
Order Bandwidth:             [____] MB/s
Total Bandwidth:             [____] MB/s

REGULATORY CHECKLIST:
────────────────────────────────
□ 7-year retention (SEC 17a-4)
□ ACID compliance (money accuracy)
□ Audit trail (immutable ledger)
□ RPO = ZERO (no data loss)
□ RTO < 30 seconds (failover)
□ BigDecimal for all money math
```

---

## 🎁 Bonus: One-Page Trading System Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║        TRADING SYSTEM SCALE CHEAT SHEET               ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Trading Day   = 23K seconds (6.5 hours)
• Trading Year  = 252 days (exclude weekends/holidays)
• Peak Hour     = 10x average (market open)
• ACID Tax      = 5x slower (strong consistency cost)
• Fill Rate     = 50% (half of orders execute)

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Order QPS    = (DAU × Trades × Orders) ÷ 23K × 10
Market QPS   = Symbols × Updates_per_sec
Storage      = Orders × 500B × 252 × 7
DB Capacity  = Peak_QPS × 5 (ACID overhead)

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Read:Write        = 100:1 (viewing >> trading)
• Peak:Average      = 10:1 (market open surge)
• Orders:Trades     = 2:1 (50% fill rate)
• ACID:NoSQL Perf   = 1:5 (5x slower)

REGULATORY MUST-HAVES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ 7-year retention (SEC Rule 17a-4)
✓ WORM audit trail (Write-Once-Read-Many)
✓ ACID transactions (no eventual consistency)
✓ BigDecimal arithmetic (no floating point)
✓ RPO = ZERO (no data loss tolerated)
✓ Synchronous replication (durability)

LATENCY TIERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Matching:     <100 μs   (C++, in-memory, lock-free)
Orders:       <10 ms    (Java, ACID DB, network)
Market Data:  <10 ms    (WebSocket, pub/sub)
Analytics:    <1 sec    (Batch, time-series DB)

PEAK PATTERNS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
09:30 AM ET: 10x surge  (market open)
10:00 AM ET: 3x surge   (economic data)
04:00 PM ET: 5x surge   (market close)
Earnings:    2x surge   (quarterly)
Black Swan:  100x surge (circuit breakers!)

SMELL TEST:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can DB handle 10K ACID writes/sec? (shard it)
✓ Can matching handle 1M orders/sec? (C++/Rust)
✓ Can we lose data in crash? NO (WAL + replication)
✓ Can balance be wrong? NO (SERIALIZABLE)
✓ Float for money? NEVER (BigDecimal only)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Design This System

**Crypto Exchange (24/7 trading, global)**
```
Given:
- 5M daily active traders
- 24/7 trading (no market hours)
- 20 trading pairs (BTC/USD, ETH/USD, etc.)
- 100 trades per user per day (high frequency)
- Flash crashes common (100x surge)
- Global users (latency sensitive)

Calculate full scale (use template above):
Time limit: 7 minutes

[Hint: No market hours = 86,400 seconds, not 23K!]
```

---

**Remember**:
> "In trading systems, every penny matters, every millisecond counts, and every transaction must be perfect. ACID compliance isn't optional - it's the foundation."

**Now go design billion-dollar financial systems!** 💰

---

*Created with the FINANCE technique: Financial constraints → Inventory → Network → ACID → Numbers → Concurrency → Explosive growth*
*Perfect for: Trading platform interviews, Fintech system design, High-frequency trading architecture*
