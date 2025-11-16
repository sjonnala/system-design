# 🎯 Stock Exchange System Design: Scale Estimation Masterclass

## The TRADES Technique for Financial System Estimation
**(T)hroughput → (R)isk → (A)vailability → (D)ata → (E)xecution → (S)ettlement**

This framework is specifically designed for **financial systems** where **correctness > performance**.

---

## 📊 PART 1: Users & Trading Volume Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 10M | Similar to Robinhood scale |
| | Active Traders (Monthly) | 2M | ~20% active monthly |
| | Daily Active Users (DAU) | 500K | ~5% of total users |
| | Concurrent Users (Peak) | 100K | Market open/close spikes |
| **Trading Patterns** | Orders per Trader/Day | 10 | Retail: 10, HFT: 10,000+ |
| | Fill Rate | 50% | Half of orders execute |
| | Read:Write Ratio | 10:1 | 10 quotes per order |
| | Symbols Traded | 5,000 | US equities only |
| **Time Distribution** | Market Hours | 6.5 hrs/day | 9:30 AM - 4:00 PM ET |
| | Peak Traffic Window | 30 min | Open & close |
| | Peak Multiplier | 10x | Extreme concentration |

---

## 🧮 PART 2: Trading Volume Calculations

### Step 1: Order Volume

```
Daily Active Traders: 500K
Orders per Trader: 10
Total Orders/Day: 500K × 10 = 5M orders/day

Market Hours: 6.5 hours = 23,400 seconds

Average Order Rate:
5M orders ÷ 23,400 sec = ~213 orders/sec

Peak Order Rate (market open/close):
213 orders/sec × 10 = 2,130 orders/sec

Conservative Peak (safety margin):
2,130 × 2 = ~4,000 orders/sec

HFT Addition (10% of volume):
4,000 × 1.1 = ~4,500 orders/sec peak
```

**Professor's Note**: Unlike web apps with 24/7 traffic, stock exchanges have **extreme temporal concentration**. 40% of daily volume occurs in the first and last 30 minutes. Plan for **10-20x average** during peaks.

---

### Step 2: Execution Volume

```
Orders per Day: 5M
Fill Rate: 50% (limit orders may not match)
Executions per Day: 5M × 0.5 = 2.5M executions/day

Average Execution Rate:
2.5M ÷ 23,400 sec = ~107 executions/sec

Peak Execution Rate:
107 × 10 = 1,070 executions/sec

Safety Margin:
1,070 × 2 = ~2,000 executions/sec peak
```

**Why 50% Fill Rate?**
- Market orders: ~100% fill (execute immediately)
- Limit orders: ~30% fill (price must match)
- Stop orders: ~50% fill (conditional)
- Blended: ~50% overall

---

### Step 3: Market Data Volume

```
Symbols: 5,000
Ticks per Symbol per Second: 1 (during active trading)
Market Hours: 23,400 seconds

Market Data Events per Day:
5,000 symbols × 1 tick/sec × 23,400 sec = 117M events/day

BUT: Market data is heavily skewed
- Top 10% symbols (500): 80% of activity = 93.6M events
- Bottom 90% symbols (4,500): 20% of activity = 23.4M events

Subscribers per Event (multicast):
- Average: 100 subscribers per popular symbol
- Peak (AAPL, TSLA): 10,000+ subscribers

Bandwidth Calculation:
- Event size: 100 bytes (symbol, bid, ask, last, volume, timestamp)
- Events/sec (peak): 5,000 symbols × 10 ticks = 50,000 events/sec
- Bandwidth: 50K × 100 bytes = 5 MB/sec
- With multicast fanout: Minimal (one packet, many receivers)
```

**Mental Math Trick**:
```
"5K symbols × 1 tick/sec × 100 bytes = 500 KB/sec"
(Only during market hours, zero overnight)
```

---

## 💾 PART 3: Storage Estimation

### Orders Table

```
Records per Day: 5M orders
Order Record Size:
  - Order ID (BIGINT): 8 bytes
  - Account ID (BIGINT): 8 bytes
  - Symbol (VARCHAR(10)): 10 bytes
  - Side (BUY/SELL): 4 bytes
  - Type (LIMIT/MARKET): 10 bytes
  - Quantity (BIGINT): 8 bytes
  - Price (DECIMAL 20,4): 16 bytes
  - Status (VARCHAR(20)): 20 bytes
  - Timestamps (2 × TIMESTAMP): 16 bytes
  - Metadata (JSON): 400 bytes
  ─────────────────────────────
  Total per Order: ~500 bytes

Daily Storage:
5M orders × 500 bytes = 2.5 GB/day

Annual Storage (250 trading days):
2.5 GB × 250 = 625 GB/year

7-Year Retention (regulatory):
625 GB × 7 = 4.375 TB ≈ 4.5 TB
```

### Trades Table

```
Records per Day: 2.5M executions
Trade Record Size:
  - Trade ID: 8 bytes
  - Order IDs (2): 16 bytes
  - Symbol: 10 bytes
  - Price: 16 bytes
  - Quantity: 8 bytes
  - Trade Value: 16 bytes
  - Timestamps: 16 bytes
  - Metadata: 400 bytes
  ─────────────────────────────
  Total per Trade: ~490 bytes

Daily Storage:
2.5M trades × 490 bytes = 1.225 GB/day

Annual: 1.225 GB × 250 = 306 GB/year
7-Year: 306 GB × 7 = 2.1 TB
```

### Market Data (Time-Series)

```
Ticks per Day: 117M
Tick Record Size:
  - Symbol ID: 4 bytes
  - Timestamp (nanosecond): 8 bytes
  - OHLC (4 × DECIMAL): 64 bytes
  - Volume: 8 bytes
  - Bid/Ask: 32 bytes
  ─────────────────────────────
  Total per Tick: ~116 bytes

Daily Storage:
117M ticks × 116 bytes = 13.6 GB/day

Annual: 13.6 GB × 250 = 3.4 TB/year

With Compression (TimescaleDB):
3.4 TB × 0.1 = 340 GB/year (10x compression)
```

### Ledger Entries (Audit Trail)

```
Entries per Trade: 4 (buyer debit, buyer credit, seller debit, seller credit)
Trades per Day: 2.5M
Ledger Entries per Day: 2.5M × 4 = 10M entries

Entry Size:
  - Entry ID: 8 bytes
  - Account ID: 8 bytes
  - Trade ID: 8 bytes
  - Entry Type: 6 bytes
  - Amount: 16 bytes
  - Currency: 3 bytes
  - Timestamp: 8 bytes
  ─────────────────────────────
  Total: ~57 bytes

Daily Storage:
10M entries × 57 bytes = 570 MB/day

Annual: 570 MB × 250 = 142.5 GB/year
7-Year: 142.5 GB × 7 = ~1 TB
```

### Total Storage Summary

```
┌──────────────────────┬──────────┬──────────┬──────────┐
│ Table                │ Daily    │ Annual   │ 7-Year   │
├──────────────────────┼──────────┼──────────┼──────────┤
│ Orders               │ 2.5 GB   │ 625 GB   │ 4.5 TB   │
│ Trades               │ 1.2 GB   │ 306 GB   │ 2.1 TB   │
│ Market Data          │ 13.6 GB  │ 3.4 TB   │ 24 TB    │
│ (compressed)         │ 1.4 GB   │ 340 GB   │ 2.4 TB   │
│ Ledger Entries       │ 0.6 GB   │ 142 GB   │ 1.0 TB   │
│ User/Account Data    │ 0.1 GB   │ 25 GB    │ 175 GB   │
├──────────────────────┼──────────┼──────────┼──────────┤
│ TOTAL (compressed)   │ 5.8 GB   │ 1.4 TB   │ 10 TB    │
└──────────────────────┴──────────┴──────────┴──────────┘

With Replication (3x for ACID):
10 TB × 3 = 30 TB total storage
```

**Professor's Insight**: Financial systems prioritize **durability** over space. Triple replication is standard. Cost of storage << cost of data loss.

---

## 🧠 PART 4: Mental Math Techniques for Trading Systems

### Technique 1: The "Trading Day" Shortcut

```
MEMORY ANCHOR:
"A trading day is ~25K seconds (23,400 exactly)"

Daily volume ÷ 25K = Average per second

Example:
5M orders/day ÷ 25K ≈ 200 orders/sec
(Exact: 5M ÷ 23.4K = 213, close enough!)
```

### Technique 2: The "Pareto Peak" Rule

```
For financial systems, peak ≠ average × 2

PEAK FORMULA:
Peak QPS = Average × 10 (first/last 30 min)
         × 2 (safety margin)
         × 1.1 (HFT factor)
         = Average × 20

Example:
Average: 200 orders/sec
Peak: 200 × 20 = 4,000 orders/sec
```

### Technique 3: The "Fill Rate" Factor

```
Orders → Executions conversion:
Total Orders × 0.5 = Executions

Why 0.5?
- Market orders: immediate fill (100%)
- Limit orders: partial fill (~30%)
- Weighted average: ~50%

Example:
5M orders/day → 2.5M executions/day
```

### Technique 4: The "ACID Tax"

```
Every ACID transaction costs 3x storage:
- Primary database
- Synchronous replica (durability)
- Asynchronous replica (HA)

Example:
10 TB logical data → 30 TB physical storage
```

---

## 📈 PART 5: Latency Budget Breakdown

### End-to-End Latency Target: <10ms (p95)

```
┌────────────────────────────┬──────────┬─────────────┐
│ Component                  │ Latency  │ % of Budget │
├────────────────────────────┼──────────┼─────────────┤
│ Network (client → gateway) │ 2 ms     │ 20%         │
│ API Gateway (auth, route)  │ 0.5 ms   │ 5%          │
│ Order Service (validate)   │ 1 ms     │ 10%         │
│ Risk Checks (DB lookup)    │ 2 ms     │ 20%         │
│ Matching Engine (in-mem)   │ 0.1 ms   │ 1%          │
│ Trade Execution (DB write) │ 3 ms     │ 30%         │
│ Response to client         │ 1 ms     │ 10%         │
│ Buffer (safety margin)     │ 0.4 ms   │ 4%          │
├────────────────────────────┼──────────┼─────────────┤
│ TOTAL                      │ 10 ms    │ 100%        │
└────────────────────────────┴──────────┴─────────────┘
```

**Critical Path**: Database writes dominate (30%). Use:
- WAL (Write-Ahead Logging): Write to sequential log, then table
- Group commits: Batch multiple transactions
- NVMe SSD: <100μs write latency

**Matching Engine**: <100μs target requires:
- In-memory data structures
- Lock-free algorithms (CAS)
- CPU pinning (no context switches)
- Kernel bypass networking (DPDK)

---

## 💰 PART 6: Cost Estimation (AWS Example)

### Compute Costs

```
Order Services (Spring Boot):
- Instances: 10 × m5.xlarge (4 vCPU, 16 GB)
- Cost: 10 × $0.192/hour × 24 × 30 = $1,382/month

Matching Engine (Bare Metal):
- Instances: 3 × c5.metal (96 vCPU, 192 GB)
- Cost: 3 × $4.08/hour × 24 × 30 = $8,812/month
- Alternative: Dedicated servers (lower latency)

Load Balancers:
- 2 × Application Load Balancer
- Cost: 2 × $16.20/month + data = ~$100/month

Total Compute: ~$10,000/month
```

### Database Costs

```
PostgreSQL (Primary + Replicas):
- db.r5.4xlarge (16 vCPU, 128 GB) × 3
- Cost: 3 × $1.36/hour × 24 × 30 = $2,937/month

TimescaleDB (Market Data):
- db.r5.2xlarge (8 vCPU, 64 GB) × 2
- Cost: 2 × $0.68/hour × 24 × 30 = $979/month

Total Database: ~$4,000/month
```

### Storage Costs

```
EBS (Database Storage):
- io2 (Provisioned IOPS SSD)
- 30 TB × $0.125/GB/month = $3,750/month
- IOPS: 100,000 × $0.065 = $6,500/month
- Total: $10,250/month

S3 (Archive):
- 100 TB historical data
- S3 Glacier Deep Archive: 100,000 GB × $0.00099 = $99/month

Total Storage: ~$10,350/month
```

### Kafka (Event Streaming)

```
MSK (Managed Kafka):
- kafka.m5.xlarge × 6 brokers
- Cost: 6 × $0.21/hour × 24 × 30 = $907/month

Total: ~$900/month
```

### Monitoring & Logging

```
CloudWatch, Datadog, or Grafana Cloud:
- Metrics: 50K metrics × $0.30 = $15K/month
- Logs: 1 TB/day × $0.50/GB = $500/day = $15K/month

Total Monitoring: ~$30,000/month
(Can optimize with self-hosted Prometheus + Grafana)
```

### Total Monthly Cost

```
┌─────────────────────┬──────────────┐
│ Category            │ Monthly Cost │
├─────────────────────┼──────────────┤
│ Compute             │ $10,000      │
│ Database            │ $4,000       │
│ Storage             │ $10,350      │
│ Kafka               │ $900         │
│ Monitoring          │ $30,000      │
│ Network (egress)    │ $5,000       │
│ Misc (backup, etc)  │ $2,000       │
├─────────────────────┼──────────────┤
│ TOTAL               │ ~$62,000     │
└─────────────────────┴──────────────┘

Annual: $62K × 12 = $744K/year
```

**Optimization Opportunities**:
- Self-host monitoring: Save $25K/month
- Reserved instances: Save 30-50% on compute
- Bare metal servers (colocation): Lower latency, lower cost
- Total optimized: ~$300K/year

**Professor's Note**: In finance, **uptime >> cost**. One hour of downtime during market hours can cost millions in lost revenue and reputation. Over-provision for reliability.

---

## 🎯 PART 7: Capacity Planning Examples

### Example 1: NYSE Scale

```
Given (Real NYSE Data):
- Symbols: 8,000 (US equities + ETFs)
- Daily Volume: 2 billion shares
- Daily Trades: 8 million executions
- Daily Orders: 300 million orders
- Market Hours: 6.5 hours

Calculate QPS:

Order Rate:
300M orders ÷ 23,400 sec = 12,820 orders/sec average
Peak (first/last hour): 12,820 × 10 = 128,200 orders/sec

Execution Rate:
8M trades ÷ 23,400 sec = 342 executions/sec average
Peak: 342 × 10 = 3,420 executions/sec

Market Data:
8K symbols × 100 ticks/sec (active trading) = 800K ticks/sec

Storage (7-year retention):
Orders: 300M/day × 500 bytes × 250 days × 7 years = 262 TB
Trades: 8M/day × 500 bytes × 250 days × 7 years = 7 TB
Market Data: 800K ticks/sec × 116 bytes × 23,400 sec × 250 × 7
           = 3.8 PB (compressed: 380 TB)

Total: ~650 TB (with compression and netting)
```

### Example 2: Crypto Exchange (24/7)

```
Given:
- Symbols: 500 (crypto pairs)
- Trading: 24/7 (no market hours limit)
- Daily Trades: 50M (high frequency)
- Orders: 500M/day

Calculate QPS:

Order Rate:
500M orders ÷ 86,400 sec = 5,787 orders/sec average
Peak: 5,787 × 3 = 17,361 orders/sec (lower peak ratio)

Key Difference: 24/7 trading spreads volume more evenly
Peak multiplier: 3x (vs. 10x for equity markets)

Storage savings: No market hours → 24/7 data
BUT: Higher total volume → net similar storage
```

---

## 🚀 PART 8: Scale Metrics Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║     STOCK EXCHANGE SCALE ESTIMATION CHEAT SHEET        ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Trading Day    = 25K seconds (~23.4K)
• Fill Rate      = 50% (orders → executions)
• Peak Multiplier = 10x (market open/close)
• ACID Tax       = 3x storage (replication)
• Latency Target = <10ms (p95) for order placement

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Order QPS      = Daily Orders ÷ 25K
Execution QPS  = Order QPS × 0.5
Peak QPS       = Average QPS × 10 × 2 (safety)
Storage (year) = Daily Records × Record Size × 250 days
Storage (7yr)  = Yearly × 7 (regulatory)

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Orders:Executions = 2:1 (50% fill)
• Quotes:Orders = 10:1 (10 quotes per order)
• Peak:Average = 10:1 (temporal concentration)
• Market Hours = 28% of day (6.5 / 24)
• Trading Days = 250/year (not 365)

SCALE TIERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small Exchange:  1K orders/sec,   <10 TB data
Medium Exchange: 10K orders/sec,  10-100 TB
Large (NYSE):    100K orders/sec, 100TB-1PB
HFT Platform:    1M orders/sec,   >1 PB

LATENCY BUDGETS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Retail Trading:  <100ms acceptable
Professional:    <10ms target
HFT:            <1ms required
Co-location:    <100μs expected

COST ESTIMATES (AWS):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:   $10-50K/month
Medium:  $50-200K/month
Large:   $200K-1M/month
(Optimize with bare metal, reserved instances)
╚════════════════════════════════════════════════════════╝
```

---

## 🎓 PART 9: Interview Practice Problems

### Problem 1: Robinhood Scale

```
Given:
- 30M users
- 10% active daily (3M DAU)
- 5 orders/user/day on average
- Market hours: 6.5 hours
- Fill rate: 60%

Calculate:
1. Average order QPS
2. Peak order QPS
3. Execution QPS
4. Annual storage (7-year retention)

[Try yourself, then check answer]
```

<details>
<summary>Solution</summary>

```
1. AVERAGE ORDER QPS:
Daily Orders = 3M DAU × 5 orders = 15M orders/day
Average QPS = 15M ÷ 25K sec ≈ 600 orders/sec

2. PEAK ORDER QPS:
Peak = Average × 10 × 2 (safety) = 600 × 20 = 12,000 orders/sec

3. EXECUTION QPS:
Executions = Orders × 60% = 15M × 0.6 = 9M/day
Avg Exec QPS = 9M ÷ 25K ≈ 360 exec/sec
Peak = 360 × 20 = 7,200 exec/sec

4. ANNUAL STORAGE (7-year):
Orders: 15M/day × 500 bytes × 250 days × 7 years
      = 15M × 500 × 1,750 = 13.125 TB

Trades: 9M/day × 500 bytes × 250 × 7
      = 9M × 500 × 1,750 = 7.875 TB

Total ≈ 21 TB (+ market data, ledger = 25-30 TB)
With 3x replication: 75-90 TB physical storage
```
</details>

---

### Problem 2: HFT Latency Breakdown

```
Your HFT client requires <1ms order-to-execution.
Breakdown your latency budget for:
- Network (co-located server to exchange)
- Order validation
- Risk checks
- Matching
- Trade execution
- Response

Assume:
- Co-location: same datacenter as exchange
- Kernel bypass networking
- In-memory order book
```

<details>
<summary>Solution</summary>

```
Total Budget: 1,000 μs

┌─────────────────────────┬──────────┬─────────────┐
│ Component               │ Latency  │ % of Budget │
├─────────────────────────┼──────────┼─────────────┤
│ Network (kernel bypass) │ 10 μs    │ 1%          │
│ Order validation        │ 50 μs    │ 5%          │
│ Risk check (cache)      │ 100 μs   │ 10%         │
│ Matching (in-memory)    │ 50 μs    │ 5%          │
│ Trade persist (WAL)     │ 500 μs   │ 50%         │
│ Response                │ 10 μs    │ 1%          │
│ Buffer                  │ 280 μs   │ 28%         │
├─────────────────────────┼──────────┼─────────────┤
│ TOTAL                   │ 1,000 μs │ 100%        │
└─────────────────────────┴──────────┴─────────────┘

Key Optimizations Needed:
1. Kernel bypass (DPDK): 10μs network latency
2. Pre-validated accounts: Skip slow risk DB lookups
3. In-memory order book: No disk I/O
4. Batched WAL writes: Group commit for trades
5. Bare metal servers: No virtualization overhead
```
</details>

---

## 💡 PART 10: Professor's Final Wisdom

### The Three Laws of Financial System Scaling

**1. Correctness > Performance**
```
Wrong Answer Fast < Right Answer Slow

Example:
- Retail trading: 100ms latency OK if accurate
- Wrong balance: Catastrophic, regardless of speed

Takeaway: Use ACID transactions, even if slower
```

**2. Availability > Latency**
```
99.99% uptime with 50ms latency
    >
99.9% uptime with 10ms latency

Calculation:
99.9%  = 8.76 hours downtime/year
99.99% = 52.6 minutes downtime/year

During market hours (1,625 hours/year):
99.9%  = 1.6 hours downtime = $MILLIONS lost
99.99% = 10 minutes downtime = Acceptable

Takeaway: Over-provision for HA, not just speed
```

**3. The Pareto Principle is EXTREME in Finance**
```
80-20 Rule in Web Apps:
20% of users → 80% of traffic

99-1 Rule in Trading:
1% of traders (HFT) → 99% of order volume

Corollary:
- Design for HFT latency requirements
- But charge HFT premium pricing
- Revenue: 1% of users = 90% of revenue
```

### Common Interview Mistakes

```
❌ MISTAKE 1: Forgetting temporal concentration
"5M orders/day ÷ 86,400 sec = 58 orders/sec"
✓ CORRECT: "÷ 23,400 sec during market hours = 213/sec"
           "Peak (first 30 min) = 213 × 10 = 2,130/sec"

❌ MISTAKE 2: Using eventual consistency for balances
"We can use Cassandra for high availability"
✓ CORRECT: "Account balances require ACID (PostgreSQL)"
           "Use Cassandra for market data, not transactions"

❌ MISTAKE 3: Underestimating storage for audit trail
"We'll keep 1 year of data"
✓ CORRECT: "SEC requires 7 years. With 3x replication = 21x"

❌ MISTAKE 4: Ignoring settlement cycle
"Trades execute instantly, we're done"
✓ CORRECT: "T+2 settlement: Trade records, pending state,"
           "settlement workflows, DTCC integration"
```

---

## 📚 Further Reading

- **Books**:
  - "Flash Boys" by Michael Lewis (HFT insights)
  - "Trading and Exchanges" by Larry Harris
  - "Designing Data-Intensive Applications" (Kleppmann)

- **Real Systems**:
  - LMAX Disruptor (Java, open source, 6M TPS)
  - NASDAQ OMX (C++, proprietary)
  - CME Globex (proprietary)

- **Papers**:
  - "The LMAX Architecture" (Martin Thompson)
  - "Mechanical Sympathy" (hardware-aware design)

---

**Remember**:
> "In finance, being 99.99% correct means losing $MILLIONS.
> Being 100% correct at 100ms latency beats being 99% correct at 1ms."

**Now go ace that interview!** 🚀

---

*Created with the TRADES technique: Throughput → Risk → Availability → Data → Execution → Settlement*
*Perfect for: Financial system design, Trading platform interviews, Fintech architecture*
