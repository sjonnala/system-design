# 💳 Payment Gateway System: Scale Estimation Masterclass

## The FINANCE Technique for Payment Scale Math
**(F)inancial Integrity → (I)nferring Peak Load → (N)umber Crunching → (A)udit Requirements → (N)etwork Overhead → (C)ompliance Storage → (E)xternal Dependencies**

This framework is specifically designed for **financial systems** where consistency and compliance are critical.

---

## 📊 PART 1: Payment System Assumptions

### Core Metrics Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Merchant Base** | Total Merchants | 100,000 | Mid-size payment gateway |
| | Active Merchants/Day | 20,000 | ~20% active daily (Pareto) |
| **Transaction Volume** | Transactions/Day | 10M | Average payment gateway |
| | Peak Multiplier | 3x | Black Friday, Cyber Monday |
| | Average Transaction | $50 | E-commerce average |
| **Read:Write Ratio** | Status Checks:Payments | 5:1 | Webhooks, polling, retries |
| **Success Rate** | Authorization Success | 85% | Industry average (fraud + declines) |
| | Refund Rate | 5% | ~500K refunds/day |
| **Payment Methods** | Credit Cards | 70% | Dominant payment method |
| | Digital Wallets | 20% | PayPal, Apple Pay, Google Pay |
| | Bank Transfers | 10% | ACH, SEPA |
| **Geographic** | Domestic | 80% | US-based transactions |
| | International | 20% | Cross-border, multi-currency |
| **3DS Authentication** | 3DS Required | 30% | PSD2 compliance (EU) |
| | Frictionless | 85% | Of 3DS transactions |
| | Challenge | 15% | Require customer action |

---

## 🧮 PART 2: Payment-Specific Scale Calculations

### Rule #1: **The Transaction Per Second (TPS) Formula**

```
TPS = Daily Transactions ÷ 86,400 seconds

BUT for payment systems, use:
Peak TPS = (Daily Transactions ÷ 86,400) × Peak Multiplier

Example:
Average TPS = 10M ÷ 86,400 = ~116 TPS
Peak TPS (Black Friday) = 116 × 3 = ~350 TPS
```

### Rule #2: **The Authorization vs Capture Split**

```
In two-phase commit:
- Authorization requests: 100% of attempts
- Capture requests: 85% of authorizations (after fraud filtering)
- Refunds: 5% of captures

Daily breakdown:
- Authorization attempts: 10M
- Successful authorizations: 8.5M (85% success)
- Captures: 8.5M (assume all auth'd transactions captured)
- Refunds: 425K (5% of captures)
```

### Rule #3: **The Fraud Detection Overhead**

```
Every payment authorization triggers:
1. Fraud ML model inference: +50ms
2. Velocity checks (Redis): +10ms
3. Device fingerprinting: +20ms
4. Rule engine evaluation: +30ms

Total fraud detection: ~110ms overhead

At 350 TPS peak:
Fraud service must handle: 350 req/sec × 110ms = sustained load
```

---

## 📈 PART 3: Payment Gateway Napkin Math Template

```
┌─────────────────────────────────────────────────────────┐
│  🎯 PAYMENT GATEWAY SCALE ESTIMATION TEMPLATE           │
└─────────────────────────────────────────────────────────┘

STEP 1: TRANSACTION VOLUME ESTIMATION
───────────────────────────────────────────────────
Daily Transactions:         10 M
Peak Multiplier:            3x
Read:Write Ratio:           5:1

→ Average TPS    = 10M ÷ 86,400     = 116 TPS
→ Peak TPS       = 116 × 3          = 350 TPS
→ Status Checks  = 350 × 5          = 1,750 reads/sec

STEP 2: PAYMENT FLOW BREAKDOWN
───────────────────────────────────────────────────
Authorization Attempts:     10 M/day
Success Rate:              85%
Successful Authorizations: 8.5 M/day
Capture Rate:              100% (of auth)
Captured Payments:         8.5 M/day
Refund Rate:               5%
Refunds:                   425 K/day

→ Authorization TPS = 10M ÷ 86,400  = 116 TPS
→ Capture TPS       = 8.5M ÷ 86,400 = 98 TPS
→ Refund TPS        = 425K ÷ 86,400 = 5 TPS

STEP 3: FINANCIAL STORAGE ESTIMATION
───────────────────────────────────────────────────
Data per transaction:
  - Transaction record:      2 KB
  - Event log (audit):       5 KB
  - Fraud signals:           3 KB
  - Total per transaction:   10 KB

Retention periods:
  - Active transactions:     7 years (PCI, SOX compliance)
  - Audit logs:              7 years (regulatory)

→ Daily Storage = 8.5M × 10 KB        = 85 GB/day
→ Yearly Storage = 85 GB × 365        = 31 TB/year
→ 7-Year Storage = 31 TB × 7          = 217 TB

Plus backups (3x):
→ Total Storage = 217 TB × 3          = 651 TB

STEP 4: PAYMENT PROCESSOR API CALLS
───────────────────────────────────────────────────
Per authorization:
  - Create payment intent:   1 call
  - Confirm payment:         1 call
  - Fraud check (3DS):       0.3 calls (30% of txns)
  - Capture:                 1 call
  - Refund:                  0.05 calls (5% of txns)

Total processor calls/day:
  10M × (1 + 1 + 0.3 + 1 + 0.05) = 33.5M API calls/day

→ Processor Call Rate = 33.5M ÷ 86,400 = 388 calls/sec
→ Peak = 388 × 3                       = 1,164 calls/sec

STEP 5: WEBHOOK DELIVERY
───────────────────────────────────────────────────
Events per transaction:
  - payment_intent.created:  1
  - payment_intent.succeeded: 1
  - charge.captured:         1
  - (Optional) charge.refunded: 0.05

Total events/day = 8.5M × (1 + 1 + 1 + 0.05) = 26M events

With retries (avg 1.5 attempts per event):
→ Webhook HTTP calls = 26M × 1.5     = 39M/day
→ Webhook TPS        = 39M ÷ 86,400  = 451 TPS

STEP 6: DATABASE QUERIES
───────────────────────────────────────────────────
Per transaction (estimated):
  - INSERT payment:          1 write
  - INSERT events (audit):   3 writes (created, auth, captured)
  - UPDATE status:           2 writes (auth, capture)
  - SELECT for validation:   3 reads
  - SELECT for idempotency:  1 read

Total queries/day:
  Writes = 8.5M × (1 + 3 + 2)         = 51M writes/day
  Reads  = 8.5M × (3 + 1) + Status    = 34M + 43M = 77M reads/day

→ Write QPS = 51M ÷ 86,400            = 590 writes/sec
→ Read QPS  = 77M ÷ 86,400            = 891 reads/sec
→ Peak Write QPS = 590 × 3            = 1,770/sec
→ Peak Read QPS  = 891 × 3            = 2,673/sec

STEP 7: BANDWIDTH ESTIMATION
───────────────────────────────────────────────────
Request/Response sizes:
  - Authorization request:   1 KB
  - Authorization response:  0.5 KB
  - Processor API call:      2 KB (request + response)
  - Webhook delivery:        1 KB

Total bandwidth/day:
  Incoming = 10M × 1 KB                = 10 GB
  Outgoing = 10M × 0.5 KB              = 5 GB
  Processor = 33.5M × 2 KB             = 67 GB
  Webhooks = 39M × 1 KB                = 39 GB

→ Total Daily Bandwidth = 121 GB
→ Average = 121 GB ÷ 86,400           = ~1.4 MB/sec
→ Peak = 1.4 MB/sec × 3               = ~4.2 MB/sec

STEP 8: CACHE (REDIS) SIZING
───────────────────────────────────────────────────
Use cases:
  - Idempotency keys (24h TTL):  10M × 1 KB   = 10 GB
  - Rate limiting counters:      100K × 1 KB  = 100 MB
  - Session storage:             1M × 5 KB    = 5 GB
  - Velocity tracking:           1M × 2 KB    = 2 GB

→ Total Redis = 17.1 GB
→ With overhead (1.5x) = 26 GB
→ Recommended: 32 GB cluster (with headroom)

STEP 9: KAFKA THROUGHPUT
───────────────────────────────────────────────────
Events published:
  - payment.created:         10M
  - payment.authorized:      8.5M
  - payment.captured:        8.5M
  - fraud.checked:           10M
  - webhook.delivery:        26M

Total events/day = 63M

→ Kafka Event Rate = 63M ÷ 86,400    = 729 events/sec
→ Peak = 729 × 3                     = 2,187 events/sec

Message size: ~1 KB avg
→ Kafka Throughput = 729 KB/sec avg, 2.1 MB/sec peak
```

---

## 🎯 PART 4: Payment Gateway Scale Template (Filled)

```
┌──────────────────────────────────────────────────────────┐
│         PAYMENT GATEWAY - COMPLETE ESTIMATION            │
└──────────────────────────────────────────────────────────┘

TRAFFIC SUMMARY:
═══════════════════════════════════════════════════════════
• Authorization TPS:    116 avg, 350 peak
• Capture TPS:           98 avg, 294 peak
• Refund TPS:             5 avg,  15 peak
• Status Check TPS:     580 avg, 1740 peak
• Webhook TPS:          451 avg, 1353 peak

STORAGE SUMMARY:
═══════════════════════════════════════════════════════════
• Daily Storage:        85 GB
• Yearly Storage:       31 TB
• 7-Year Storage:      217 TB
• With Backups:        651 TB

DATABASE SUMMARY:
═══════════════════════════════════════════════════════════
• Write QPS:           590 avg, 1770 peak
• Read QPS:            891 avg, 2673 peak
• Total QPS:          1481 avg, 4443 peak

Recommended:
• PostgreSQL: db.r6g.2xlarge (8 vCPU, 64 GB RAM)
• Read replicas: 3x (for 2673 peak read QPS)
• Storage: 1 TB provisioned IOPS (io2, 10K IOPS)

PROCESSOR API CALLS:
═══════════════════════════════════════════════════════════
• Total calls/day:     33.5 M
• Calls/sec:           388 avg, 1164 peak
• Rate limit budget:   2000 req/sec (Stripe limit)
• Headroom:            72% at peak

CACHE (REDIS):
═══════════════════════════════════════════════════════════
• Required size:       26 GB
• Recommended:         32 GB cluster
• Nodes:               6 (3 masters, 3 replicas)
• Instance:            cache.r6g.xlarge

KAFKA:
═══════════════════════════════════════════════════════════
• Events/day:          63 M
• Events/sec:          729 avg, 2187 peak
• Throughput:          729 KB/sec avg, 2.1 MB/sec peak
• Brokers:             3
• Partitions:          12 per topic
• Replication:         3x

BANDWIDTH:
═══════════════════════════════════════════════════════════
• Daily total:         121 GB
• Average rate:        1.4 MB/sec
• Peak rate:           4.2 MB/sec

COST ESTIMATION (AWS):
═══════════════════════════════════════════════════════════
• PostgreSQL (r6g.2xlarge):       $450/month
• Read replicas (3x):            $1,350/month
• Redis (6 nodes, r6g.xlarge):     $900/month
• EBS storage (1TB io2):           $125/month
• Kafka (3 brokers, m5.xlarge):    $360/month
• Data transfer:                   $100/month
• S3 backups (651 TB):            $1,500/month

Total Infrastructure:            ~$4,785/month

Processor fees (Stripe):
  $500M/day × 2.9% = $14.5M/day
  (Not infrastructure cost, but important!)
```

---

## 💡 PART 5: Payment-Specific Scaling Insights

### Insight 1: **The 7-Year Storage Trap**

```
Most systems: 1-2 year retention
Payment systems: 7 YEARS (PCI-DSS, SOX, regulatory)

Traditional calc:
10M txns/day × 2 KB × 365 × 2 years = 14.6 TB

Payment reality:
10M txns/day × 10 KB × 365 × 7 years = 255.5 TB

❌ MISTAKE: Planning for 15 TB
✅ CORRECT: Planning for 650+ TB (with backups)

Cost impact: $1,500/month vs $12,000/month
```

### Insight 2: **The Peak Load Multiplier**

```
Black Friday 2023 (Shopify):
- Peak: 11.5M req/min = 191,667 req/sec
- Average: ~30,000 req/sec
- Multiplier: 6.4x (not 2x!)

For payment gateways:
- Black Friday / Cyber Monday: 3-5x
- Amazon Prime Day: 2-3x
- Flash sales: 10x (short duration)

❌ BAD:  Plan for 2x average
✅ GOOD: Plan for 5x average with auto-scaling to 10x
```

### Insight 3: **The Processor Rate Limit Wall**

```
Stripe rate limits:
- Test mode: 25 req/sec
- Live mode: 100 req/sec (default)
- High volume: 2000 req/sec (negotiated)

Your calculation:
Peak TPS = 350 × 3 (retry + fallback) = 1,050 req/sec

✓ Under limit (2000 req/sec)

But consider:
- Multiple merchants hitting same limit
- Need for retry budget
- Failover scenarios

✅ Best practice: Assume 50% headroom
   Effective limit = 1,000 req/sec for planning
```

### Insight 4: **The Audit Log Explosion**

```
Transaction record: 2 KB
But audit requirements demand:

- Every state change logged (6+ events)
- Every API call logged (10+ calls)
- Every fraud check logged (3+ signals)

Audit data per transaction: 5 KB (not 2 KB!)

Impact:
10M txns/day × 5 KB × 365 × 7 = 127.75 TB (just audit logs!)
```

### Insight 5: **The 3DS Latency Tax**

```
Standard payment:
  Authorization: 300ms avg

With 3DS 2.0:
  Frictionless (85%): 300ms + 200ms = 500ms
  Challenge (15%):    300ms + 5,000ms = 5,300ms

Weighted average:
  (0.85 × 500ms) + (0.15 × 5300ms) = 1,220ms

For 30% of transactions requiring 3DS:
  Overall latency impact: +276ms on average

Must plan for:
  P95 latency < 500ms → difficult with 3DS!
  P99 latency < 2000ms → realistic target
```

---

## 🔧 PART 6: Scaling Strategies for Payment Systems

### Strategy 1: **Database Sharding by Merchant**

```
Problem: 10M txns/day on single PostgreSQL

Solution: Shard by merchant_id
  - Shard 1-16: Each handles 625K txns/day
  - Query routing based on merchant_id hash
  - Cross-shard queries minimized

Benefits:
  - Linear scaling (add shards as merchants grow)
  - Merchant isolation (one merchant can't impact others)
  - Easier compliance (data residency per shard)

Tradeoff:
  - Complex queries across merchants
  - Rebalancing when adding shards
```

### Strategy 2: **Read Replica Strategy**

```
Write load: 1,770 QPS peak
Read load:  2,673 QPS peak

Ratio: 1.5:1 (read-heavy)

Architecture:
  1 Primary (writes only)
  3 Read replicas (distributed across AZs)

Load distribution:
  Primary:     1,770 writes/sec
  Replica 1:     891 reads/sec
  Replica 2:     891 reads/sec
  Replica 3:     891 reads/sec

Headroom: Each replica at 60% capacity (allows failover)
```

### Strategy 3: **Cache Warming for Idempotency**

```
Problem: Cold cache → all idempotency checks hit DB

Solution: Pre-warm cache with recent requests
  - Keep last 1 hour of requests in Redis
  - 10M txns/day ÷ 24 hours = 417K txns/hour
  - 417K × 1 KB = 417 MB

Hit rate:
  - Retries (within 1 hour): 95% hit rate
  - Older retries: DB fallback

Benefit:
  - 95% of idempotency checks: <1ms (Redis)
  - 5% of checks: ~50ms (DB)
  - Avg latency: 0.95 × 1ms + 0.05 × 50ms = 3.45ms
```

### Strategy 4: **Processor Failover**

```
Primary processor (Stripe): 80% of volume
Backup processor (Adyen):   20% of volume

On primary failure:
  1. Circuit breaker opens after 5 failures
  2. Route 100% traffic to backup
  3. Monitor recovery every 30 seconds
  4. Gradual ramp-back (10%, 25%, 50%, 100%)

Capacity planning:
  Backup must handle 100% load, not just 20%!

  Backup rate limit: 1000 req/sec
  Peak load:         350 req/sec
  Headroom:          65% ✓
```

---

## 📊 PART 7: Payment Gateway Metrics Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║         PAYMENT GATEWAY SCALE CHEAT SHEET              ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Average transaction:    $50
• Authorization success:  85%
• Refund rate:            5%
• 3DS required:           30% (EU)
• Storage per txn:        10 KB (with audit)
• Retention:              7 years (compliance)

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
TPS = Daily Transactions ÷ 86,400
Peak TPS = TPS × Peak Multiplier (3x for holidays)
Storage = Daily Txns × 10 KB × 365 × 7 years
Processor Calls = TPS × 3.35 (avg calls per txn)

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Authorization:Capture = 1:0.85 (15% decline)
• Capture:Refund = 1:0.05 (5% refund rate)
• Read:Write DB = 1.5:1 (payment systems)
• Webhook retries = 1.5x (avg attempts)
• Peak multiplier = 3x (Black Friday)

QUICK ESTIMATES BY SCALE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:    100K txns/day    ~1 TPS      ~365 GB/year
Medium:   1M txns/day      ~12 TPS     ~3.6 TB/year
Large:    10M txns/day     ~116 TPS    ~36 TB/year
Huge:     100M txns/day    ~1160 TPS   ~360 TB/year

DATABASE SIZING:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:    db.t3.medium      (2 vCPU, 4 GB)
Medium:   db.r6g.large      (2 vCPU, 16 GB)
Large:    db.r6g.2xlarge    (8 vCPU, 64 GB)
Huge:     db.r6g.8xlarge    (32 vCPU, 256 GB) + sharding

PROCESSOR RATE LIMITS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Stripe:   25 (test), 100 (live), 2000 (enterprise)
PayPal:   50 req/sec (standard)
Adyen:    500 req/sec (standard)

Always plan for 50% headroom!

COMPLIANCE STORAGE REQUIREMENTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
PCI-DSS:  1 year (minimum)
SOX:      7 years (financial records)
GDPR:     6 years (EU tax records)
CCPA:     No specific requirement (use 7 years)

→ Use 7 years for planning
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 PART 8: Real-World Payment System Benchmarks

### Stripe (Industry Leader)

```
Public stats (2023):
- $817 billion payment volume
- Millions of businesses
- 99.99% uptime SLA

Estimated (reverse engineered):
- ~100M transactions/day
- ~1,160 TPS average
- ~3,500 TPS peak
- Database: Sharded PostgreSQL (100+ shards)
- Cache: Redis cluster (TB-scale)
- Storage: Multi-PB (with 7+ year retention)
```

### Square (Mid-Market)

```
Public stats (2023):
- $204 billion payment volume
- 4M active sellers

Estimated:
- ~25M transactions/day
- ~290 TPS average
- ~870 TPS peak
```

### PayPal (Mature Platform)

```
Public stats (2023):
- $1.36 trillion payment volume
- 435M active accounts

Estimated:
- ~180M transactions/day
- ~2,080 TPS average
- ~6,240 TPS peak
```

---

## 🚨 Common Mistakes in Payment Scale Estimation

### Mistake 1: **Forgetting Compliance Storage**

```
✗ BAD:  "We need 36 TB for 5 years"
✓ GOOD: "We need 217 TB for 7 years (compliance)
         Plus 3x backups = 651 TB total"
```

### Mistake 2: **Underestimating Peak Load**

```
✗ BAD:  "Plan for 2x average"
✓ GOOD: "Plan for 5x average (Black Friday)
         With auto-scaling to 10x (flash sales)"
```

### Mistake 3: **Ignoring Audit Log Size**

```
✗ BAD:  "2 KB per transaction"
✓ GOOD: "2 KB transaction + 5 KB audit = 7 KB total
         Or 10 KB with fraud signals"
```

### Mistake 4: **Forgetting Processor API Calls**

```
✗ BAD:  "1 TPS = 1 processor call"
✓ GOOD: "1 TPS = 3.35 processor calls avg
         (intent + confirm + 3DS + capture + refund)"
```

### Mistake 5: **Not Planning for Retries**

```
✗ BAD:  "100 TPS = 100 webhook calls/sec"
✓ GOOD: "100 TPS = 150 webhook calls/sec
         (1.5x avg with retries)"
```

---

## 📝 Practice Problem: Calculate Scale for Your Payment Gateway

```
Given:
- 50,000 merchants
- Average 200 transactions per merchant per day
- Peak multiplier: 4x (holiday sales)
- Authorization success rate: 80%
- Refund rate: 8%
- 3DS required: 40% (mostly international)
- Data retention: 7 years

Calculate:
1. Daily transaction volume
2. Average and peak TPS
3. Authorization, capture, refund TPS
4. Database storage (7 years)
5. Database QPS (read + write)
6. Processor API call rate
7. Redis cache size
8. Monthly AWS cost estimate

[Try it yourself first!]
```

<details>
<summary>Answer</summary>

```
1. DAILY TRANSACTION VOLUME:
   50,000 merchants × 200 txns = 10M txns/day

2. TPS:
   Average = 10M ÷ 86,400 = 116 TPS
   Peak = 116 × 4 = 464 TPS

3. AUTHORIZATION, CAPTURE, REFUND:
   Auth attempts:  10M
   Auth success:   8M (80%)
   Captures:       8M
   Refunds:        640K (8% of captures)

   Auth TPS:    116 avg, 464 peak
   Capture TPS: 93 avg, 372 peak
   Refund TPS:  7 avg, 28 peak

4. STORAGE (7 years):
   8M captures/day × 10 KB × 365 × 7 = 204.4 TB
   With 3x backups: 613.2 TB

5. DATABASE QPS:
   Writes = 8M × 6 queries = 48M/day = 556 writes/sec
   Reads = 8M × 4 + 10M status = 72M/day = 833 reads/sec
   Total: 1,389 QPS avg, 5,556 QPS peak

6. PROCESSOR API CALLS:
   10M × 3.35 = 33.5M calls/day = 388 calls/sec
   Peak = 1,552 calls/sec

7. REDIS CACHE:
   Idempotency (24h): 10M × 1 KB = 10 GB
   Rate limits: 100 MB
   Sessions: 5 GB
   Total: ~16 GB → Recommend 32 GB cluster

8. AWS COST (monthly):
   PostgreSQL (r6g.4xlarge): $900
   Read replicas (3x): $2,700
   Redis (32 GB cluster): $1,000
   Storage (613 TB): $12,000
   Kafka: $360
   Data transfer: $150
   Total: ~$17,110/month
```
</details>

---

## 🎓 Final Wisdom for Payment Scale Estimation

> **"In payment systems, under-provisioning isn't just bad UX - it's lost revenue and regulatory risk."**

Key principles:
1. ✅ Always plan for 7-year retention (not 1-2 years)
2. ✅ Use 5x peak multiplier (not 2x)
3. ✅ Include audit logs in storage (not just transaction data)
4. ✅ Account for processor API calls (3-4x transaction rate)
5. ✅ Plan for 50% processor rate limit headroom
6. ✅ Budget for compliance (backups, PITR, audit trails)

**Remember**: Payment systems prioritize consistency over availability. Better to slow down than to corrupt financial data!

---

*Built with the FINANCE technique: Financial integrity → Inferring peak → Number crunching → Audit requirements → Network overhead → Compliance storage → External dependencies*
