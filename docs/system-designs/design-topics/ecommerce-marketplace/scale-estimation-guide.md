# 🎯 E-Commerce & Marketplace: Scale Estimation Masterclass

## The COMMERCE Framework for E-Commerce Scale Math
**(C)apacity → (O)rders → (M)etrics → (M)emory → (E)stimation → (R)ound → (C)heck → (E)valuate**

This framework helps you estimate scale for **complex multi-component systems** like Amazon/Flipkart.

---

## 📊 PART 1: Understanding E-Commerce Scale

### The 3 Traffic Patterns

E-commerce has **3 distinct traffic patterns** that require different scaling strategies:

```
1. BROWSING (Read-Heavy)
   └─> Product views, search, recommendations
   └─> 100:1 read:write ratio
   └─> Cache-friendly (products don't change often)

2. TRANSACTIONAL (Write-Heavy)
   └─> Orders, payments, inventory updates
   └─> 1:10 read:write ratio
   └─> ACID transactions required

3. BURST (Flash Sales)
   └─> 1000x normal traffic for 1 hour
   └─> Inventory contention (race conditions)
   └─> Queue-based processing
```

### Core Assumptions Table (Amazon-Scale)

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 300M | Monthly active users |
| | Daily Active Users (DAU) | 50M | ~16% of monthly (e-commerce pattern) |
| | Peak Concurrent Users | 500K | During flash sales / holiday seasons |
| | Conversion Rate | 2% | Industry standard for e-commerce |
| **Products** | Total SKUs | 500M | Amazon-scale catalog |
| | Active Sellers | 2M | Marketplace model |
| | Avg Products per Seller | 250 | Long-tail distribution |
| **Orders** | Orders per Day | 10M | 50M DAU × 2% conversion × 10 orders/day |
| | Items per Order | 2.5 | Average basket size |
| | Read:Write Ratio (Browse) | 100:1 | Most users browse, few buy |
| **Sessions** | Avg Session Duration | 15 min | Browsing multiple products |
| | Page Views per Session | 10 | Product views, search, cart |
| | Sessions per DAU | 3 | Morning, lunch, evening |

---

## 🧮 PART 2: The "E-Commerce Calculator" - Your Mental Math Toolkit

### Rule #1: **The Traffic Multiplier**
```
E-commerce traffic is NOT uniform!

Peak Traffic Multipliers:
• Normal Day Peak (6-9 PM): 2-3x average
• Flash Sale (12 PM): 10-20x average
• Black Friday / Cyber Monday: 50-100x average

ALWAYS design for peak, not average!
```

### Rule #2: **The Conversion Funnel**
```
100 Visitors → 10 Search → 5 Product Views → 1 Add to Cart → 0.5 Orders

Conversion rates:
• Browse → Search: 10%
• Search → Product View: 50%
• Product View → Add to Cart: 20%
• Cart → Order: 50% (cart abandonment rate = 50%)
```

### Rule #3: **The Inventory Constraint**
```
Unlike social media (infinite posts), e-commerce has FINITE inventory.

Inventory Turnover Rate: 6x per year (restock every 2 months)
Stock-out Rate: 5% (lost sales due to unavailability)
Overselling Risk: 0% (MUST be prevented with strong consistency)
```

---

## 📈 PART 3: E-Commerce Scale Math Template

```
┌─────────────────────────────────────────────────────────┐
│  🛒 E-COMMERCE NAPKIN MATH TEMPLATE                     │
└─────────────────────────────────────────────────────────┘

STEP 1: USER & SESSION ESTIMATION
───────────────────────────
Daily Active Users (DAU):    [____] M
Sessions per DAU:            [____]
Page views per session:      [____]

→ Total Page Views/Day = DAU × Sessions × Pages = [____] M
→ Page Views/Sec = Total ÷ 100K = [____] K QPS
→ Peak QPS = Page Views/Sec × 3 = [____] K QPS

STEP 2: ORDER & TRANSACTION ESTIMATION
───────────────────────────
Conversion Rate:             [____] %
Orders/Day:                  [____] M
Items per Order:             [____]

→ Order QPS = Orders/Day ÷ 100K = [____]
→ Peak Order QPS = Order QPS × 10 (flash sale) = [____]
→ Inventory Updates/Sec = Order QPS × Items = [____]

STEP 3: STORAGE ESTIMATION (Multi-Component)
───────────────────────────
Products:
- Total SKUs:                [____] M
- Data per SKU:              10KB (metadata, specs)
- Images per SKU:            5 × 200KB = 1MB
→ Product Metadata = SKUs × 10KB = [____] TB
→ Product Images = SKUs × 1MB = [____] TB

Users:
- Total Users:               [____] M
- Data per User:             1KB (profile)
→ User Data = Users × 1KB = [____] GB

Orders (5 years):
- Orders/Day:                [____] M
- Data per Order:            5KB (items, payment, address)
→ Orders Storage = 10M × 5KB × 365 × 5 = [____] TB

Reviews:
- Reviews:                   [____] M
- Data per Review:           2KB
→ Reviews Storage = Reviews × 2KB = [____] GB

TOTAL STORAGE: [____] TB (excluding CDN images)

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
Read Bandwidth:
- Page Views QPS:            [____] K
- Avg Response Size:         100KB (with images)
→ Read BW = QPS × Size = [____] GB/s

Write Bandwidth:
- Order QPS:                 [____]
- Order Size:                5KB
→ Write BW = QPS × Size = [____] MB/s

STEP 5: CACHE ESTIMATION (Multi-Tier)
───────────────────────────
Hot Products (80/20 rule):
- Total Products:            [____] M
- Hot Products (20%):        [____] M
- Cache per Product:         10KB
→ Product Cache = Hot × 10KB = [____] GB

User Sessions:
- Concurrent Users:          [____] K
- Session Data:              10KB (cart, preferences)
→ Session Cache = Concurrent × 10KB = [____] GB

Search Results:
- Popular Searches:          10M
- Results per Search:        1KB
→ Search Cache = 10M × 1KB = [____] GB

TOTAL CACHE: [____] TB
```

---

## 💾 PART 4: E-Commerce Filled Template (Amazon-Scale)

```
┌─────────────────────────────────────────────────────────┐
│         AMAZON-SCALE E-COMMERCE SOLUTION                │
└─────────────────────────────────────────────────────────┘

STEP 1: USER & SESSION ESTIMATION
───────────────────────────
Daily Active Users (DAU):    50 M
Sessions per DAU:            3
Page views per session:      10

→ Total Page Views/Day = 50M × 3 × 10 = 1.5 B
→ Page Views/Sec = 1.5B ÷ 100K = 15,000 QPS
→ Peak QPS (3x) = 15K × 3 = 45,000 QPS

STEP 2: ORDER & TRANSACTION ESTIMATION
───────────────────────────
Conversion Rate:             2%
Orders/Day:                  10 M (50M DAU × 2% × 10 sessions)
Items per Order:             2.5

→ Order QPS = 10M ÷ 100K = 100 orders/sec
→ Peak Order QPS = 100 × 10 (flash sale) = 1,000 orders/sec
→ Inventory Updates/Sec = 100 × 2.5 = 250 updates/sec

STEP 3: STORAGE ESTIMATION
───────────────────────────
Products:
- Total SKUs:                500 M
- Metadata per SKU:          10KB
- Images per SKU:            5 × 200KB = 1MB
→ Product Metadata = 500M × 10KB = 5 TB
→ Product Images = 500M × 1MB = 500 TB

Users:
- Total Users:               300 M
- Data per User:             1KB
→ User Data = 300M × 1KB = 300 GB

Orders (5 years):
- Orders/Day:                10 M
- Data per Order:            5KB
→ Orders Storage = 10M × 5KB × 365 × 5 = 90 TB

Reviews:
- Reviews:                   100 M
- Data per Review:           2KB
→ Reviews Storage = 100M × 2KB = 200 GB

TOTAL STORAGE: ~600 TB (excluding CDN images)

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
Read Bandwidth:
- Page Views QPS:            45 K (peak)
- Avg Response Size:         100KB
→ Read BW = 45K × 100KB = 4.5 GB/s = 36 Gbps

Write Bandwidth:
- Order QPS:                 1,000 (peak)
- Order Size:                5KB
→ Write BW = 1K × 5KB = 5 MB/s (negligible)

STEP 5: CACHE ESTIMATION
───────────────────────────
Hot Products (80/20):
- Total Products:            500 M
- Hot Products (20%):        100 M
- Cache per Product:         10KB
→ Product Cache = 100M × 10KB = 1 TB

User Sessions:
- Concurrent Users:          500 K (peak)
- Session Data:              10KB
→ Session Cache = 500K × 10KB = 5 GB

Search Results:
- Popular Searches:          10 M
- Results per Search:        1KB
→ Search Cache = 10M × 1KB = 10 GB

TOTAL CACHE: ~1 TB (Redis Cluster)
```

---

## 🎯 PART 5: Component-Specific Calculations

### A. Search Service Sizing

**Elasticsearch Cluster:**
```
Documents:           500M products
Index Size:          1TB (with all fields)
Shards:              6 (optimal for 1TB)
Replicas:            1 (for HA)
Total Nodes:         12 (6 primary + 6 replicas)

Query Load:
- Search QPS:        5,000 (peak)
- Latency Target:    <200ms (P95)

Node Sizing:
- RAM per Node:      64GB (Elasticsearch loves RAM)
- CPU:               16 cores (parallel query execution)
- Disk:              1TB SSD (fast I/O)

Total Cost (AWS):    12 × r5.4xlarge = ~$12,000/month
```

### B. Database Sizing (PostgreSQL)

**Orders Database:**
```
Orders per Year:     10M/day × 365 = 3.65B orders
Order Size:          5KB
Total Data:          3.65B × 5KB = 18TB/year

With Partitioning (by month):
- Partitions:        60 (5 years × 12 months)
- Data per Partition: 300GB

Sharding (by user_id):
- Shards:            10
- Data per Shard:    1.8TB/year

Writes:              100 orders/sec (avg), 1K/sec (peak)
Reads:               1K queries/sec (order history, tracking)

Node Sizing (per shard):
- RAM:               128GB (cache hot data)
- CPU:               32 cores
- Disk:              5TB SSD (with growth buffer)
```

**Inventory Database:**
```
SKUs:                500M
Warehouses:          100
Total Rows:          500M × 100 = 50B rows

BUT: Not all products in all warehouses!
Actual Rows:         500M × 5 (avg warehouses) = 2.5B rows

Row Size:            100 bytes (sku, warehouse, qty, reserved)
Total Data:          2.5B × 100B = 250GB

Updates per Sec:     250 (every order updates inventory)

Consistency:         STRONG (PostgreSQL with row locks)
Latency:             <50ms (critical for checkout)
```

### C. Cache Sizing (Redis)

**Multi-Tier Cache Strategy:**
```
TIER 1: Session Cache (Redis)
- Concurrent Sessions: 500K
- Data per Session:    10KB (cart, user prefs)
- Total:               5GB
- TTL:                 30 minutes (rolling)

TIER 2: Product Cache (Redis)
- Hot Products:        100M (20% of catalog)
- Data per Product:    10KB
- Total:               1TB
- TTL:                 1 hour (or event-based invalidation)

TIER 3: Search Cache (Redis)
- Popular Queries:     10M
- Results per Query:   1KB (product IDs only)
- Total:               10GB
- TTL:                 5 minutes

TIER 4: Inventory Availability (Redis)
- All SKUs:            500M
- Data per SKU:        20 bytes (qty, warehouse)
- Total:               10GB
- TTL:                 5 seconds (high churn)

TOTAL REDIS:           ~1TB
Cluster Size:          6 masters + 6 replicas = 12 nodes
Node RAM:              128GB each
Total Cost (AWS):      12 × r6g.4xlarge = ~$6,000/month
```

---

## 🧠 PART 6: E-Commerce Mental Math Shortcuts

### Shortcut 1: **The DAU → Orders Funnel**
```
DAU → Orders: Multiply by 0.2 (20% of DAU order something eventually)

50M DAU → 10M orders per day
(50M × 0.2 = 10M)

Quick Check: Does 20% conversion make sense? YES
(Not 2% of DAU, but 2% × 10 page views = 20% cumulative)
```

### Shortcut 2: **The Storage Explosion**
```
E-commerce has 3 storage tiers:

HOT:     Recent data (last 30 days)    → SSD, <100ms latency
WARM:    Historical (1 year)            → HDD, <1s latency
COLD:    Archives (5+ years)            → S3 Glacier, <hours

Example (Orders):
- HOT:  10M/day × 30 days × 5KB = 1.5TB (PostgreSQL SSD)
- WARM: 10M/day × 335 days × 5KB = 16TB (PostgreSQL HDD)
- COLD: 10M/day × 1460 days × 5KB = 73TB (S3 Glacier)
```

### Shortcut 3: **The Flash Sale Multiplier**
```
Normal:       100 orders/sec
Flash Sale:   100 × 10 = 1,000 orders/sec (10x spike)

But inventory is FINITE!
If 10,000 items in flash sale:
  1,000 orders/sec × 10 seconds = ALL SOLD OUT

Solution: Queue-based processing
  Acceptance Rate: 10K items ÷ 60 sec = 167 orders/sec max
  Reject:          833 orders/sec (show "sold out")
```

---

## 💡 PART 7: Common E-Commerce Patterns

### Pattern 1: **Eventual Consistency Where Possible**

```
STRONG CONSISTENCY (PostgreSQL):
✓ Inventory (prevent overselling)
✓ Payments (no double charging)
✓ Orders (ACID transactions)

EVENTUAL CONSISTENCY (MongoDB, Cassandra):
✓ Product catalog (stale data OK for 1 minute)
✓ Reviews (delayed reviews acceptable)
✓ Recommendations (can be hours old)
✓ Analytics (daily aggregations fine)
```

### Pattern 2: **Read/Write Separation (CQRS)**

```
WRITE PATH (Strong Consistency):
Order Service → PostgreSQL Primary → Kafka Event

READ PATH (Eventual Consistency):
User Query → Redis Cache → PostgreSQL Replicas → Response

Benefits:
- Optimize read and write independently
- Scale reads (replicas) and writes (sharding) separately
- Different data models for read/write
```

### Pattern 3: **The Inventory Reservation Pattern**

```
Problem: User adds to cart, but doesn't checkout immediately.
         Meanwhile, another user tries to buy the same item.

Solution:
1. Add to Cart → Reserve inventory (10 min TTL)
2. During 10 min → Item unavailable to others
3. If checkout → Deduct inventory
4. If timeout → Release reservation (cron job)

Storage:
- Reservations Table (PostgreSQL)
- Reserved items: 50M DAU × 5 items = 250M rows
- Row size: 100 bytes
- Total: 25GB (fits in RAM)
```

---

## 🎓 PART 8: E-Commerce Scale Interview Template

### The 5-Minute Drill

**Question:** Design Amazon.

**Answer Structure:**

```
1. CLARIFY SCOPE (1 min):
   - User scale? → 50M DAU
   - Product catalog? → 500M SKUs
   - Orders/day? → 10M
   - Global or regional? → Global (multi-region)

2. HIGH-LEVEL NUMBERS (2 min):
   - Page Views: 50M × 3 × 10 = 1.5B/day → 15K QPS
   - Orders: 10M/day → 100 orders/sec (1K peak)
   - Storage: Products(500TB) + Orders(90TB) + Users(300GB) = ~600TB
   - Cache: 1TB (hot products + sessions)

3. COMPONENTS (1 min):
   - Frontend: React, Mobile Apps, CDN (CloudFront)
   - API Gateway: Kong/NGINX (rate limiting, auth)
   - Services: Product, Search, Cart, Inventory, Order, Payment (microservices)
   - Databases: PostgreSQL (orders, inventory), MongoDB (catalog), Elasticsearch (search)
   - Cache: Redis Cluster (1TB)
   - Queue: Kafka (async events)

4. CRITICAL PATHS (1 min):
   - SEARCH: User → CDN → Elasticsearch → Enrich → Cache → Response (<200ms)
   - ORDER: Cart → Inventory Reserve → Payment → Deduct → Confirm (Saga pattern)
   - INVENTORY: Strong consistency (PostgreSQL row locks) to prevent overselling
```

---

## 🚨 PART 9: Common E-Commerce Scaling Mistakes

### Mistake 1: **Using Eventual Consistency for Inventory**
```
✗ BAD:  Store inventory in MongoDB/Cassandra (eventual consistency)
        → Race condition: 2 users buy the last item
        → Overselling!

✓ GOOD: Use PostgreSQL with row-level locks (strong consistency)
        → SELECT ... FOR UPDATE
        → Only 1 user succeeds
```

### Mistake 2: **Not Planning for Flash Sales**
```
✗ BAD:  Design for average load (100 orders/sec)
        → Flash sale hits: 10K orders/sec
        → System crashes

✓ GOOD: Design for 10-100x peak load
        → Use queue-based processing
        → Rate limit to sustainable throughput
```

### Mistake 3: **Caching Everything Forever**
```
✗ BAD:  Cache product prices with no TTL
        → Price changes don't reflect
        → Users see wrong prices

✓ GOOD: Cache with appropriate TTL
        → Product metadata: 1 hour
        → Prices: 5 minutes (can change in flash sales)
        → Inventory: 5 seconds (high churn)
```

### Mistake 4: **Ignoring CAP Theorem**
```
In distributed systems, you can have 2 of 3:
- Consistency (C): All nodes see same data
- Availability (A): System always responds
- Partition Tolerance (P): Works despite network failures

E-commerce choices:
✓ Inventory & Payments: CP (strong consistency, may be unavailable during partition)
✓ Product Catalog & Search: AP (always available, may show stale data)
```

---

## 📊 PART 10: Quick Reference Table

| **Component** | **Technology** | **Size** | **Throughput** | **Latency** | **Consistency** |
|---------------|----------------|----------|----------------|-------------|-----------------|
| Product Catalog | MongoDB | 5TB | 10K writes/sec, 100K reads/sec | <50ms | Eventual |
| Search | Elasticsearch | 1TB | 5K QPS | <200ms | Eventual |
| Cart | Redis | 5GB | 50K ops/sec | <5ms | Strong (session) |
| Inventory | PostgreSQL | 250GB | 250 updates/sec | <50ms | **Strong** |
| Orders | PostgreSQL | 90TB (5yr) | 100 writes/sec, 1K reads/sec | <100ms | **Strong** |
| Payments | PostgreSQL | 10TB (5yr) | 100 txn/sec | <200ms | **Strong** |
| Reviews | MongoDB | 200GB | 100 writes/sec, 10K reads/sec | <100ms | Eventual |
| Product Cache | Redis | 1TB | 200K ops/sec | <1ms | Eventual |
| CDN | CloudFront | ∞ | 100K req/sec | <10ms | Eventual |
| Images | S3 | 500TB | 50K req/sec | <100ms | Eventual |
| Analytics | ClickHouse | 50TB | 1M events/sec | <1s (queries) | Eventual |

---

## 🎯 PART 11: E-Commerce Scale Practice Problem

### Problem: Design Flipkart India

**Given:**
- 100M monthly users
- 20M DAU
- 200M products
- 5M orders/day
- Average Order Value (AOV): ₹2,000
- Flash sales: 10x traffic spike

**Calculate:**
1. Page Views QPS (avg + peak)
2. Order QPS (avg + peak)
3. Storage requirements (5 years)
4. Cache size
5. Database sharding strategy

<details>
<summary>Click to see solution</summary>

```
1. PAGE VIEWS QPS:
   - Page Views/Day = 20M DAU × 3 sessions × 10 pages = 600M
   - Avg QPS = 600M ÷ 100K = 6,000 QPS
   - Peak QPS = 6K × 3 = 18,000 QPS

2. ORDER QPS:
   - Avg = 5M ÷ 100K = 50 orders/sec
   - Peak (flash sale) = 50 × 10 = 500 orders/sec

3. STORAGE (5 years):
   Products:
   - Metadata: 200M × 10KB = 2TB
   - Images: 200M × 1MB = 200TB

   Orders:
   - 5M/day × 5KB × 365 × 5 = 45TB

   Users:
   - 100M × 1KB = 100GB

   Total: ~250TB (excluding CDN images)

4. CACHE:
   - Hot Products: 200M × 20% × 10KB = 400GB
   - Sessions: 200K concurrent × 10KB = 2GB
   - Search: 10GB
   Total: ~500GB Redis

5. SHARDING:
   - Orders: Shard by user_id (10 shards = 4.5TB/shard)
   - Products: Shard by category (20 shards = 100M products/shard)
   - Inventory: Shard by product_id (10 shards)
```
</details>

---

## 📚 Final Wisdom for E-Commerce Scale

> **"In e-commerce, consistency is king for money and inventory, but speed is king for everything else."**

**Key Principles:**

1. **Multi-Tier Architecture**: Different components need different consistency/latency guarantees
2. **Strong Consistency Where It Matters**: Inventory, payments, orders (use PostgreSQL)
3. **Eventual Consistency Everywhere Else**: Catalog, search, recommendations (use NoSQL)
4. **Cache Aggressively**: 80% of traffic hits 20% of products
5. **Plan for Spikes**: Flash sales can be 100x normal traffic
6. **Async Everything (Except Checkout)**: Use Kafka for non-critical paths
7. **Monitor Inventory Closely**: Overselling = customer complaints + losses

**Interview Strategy:**

1. Start with DAU → derive everything else
2. Separate browsing (read-heavy) from checkout (write-heavy)
3. Call out inventory as needing strong consistency
4. Mention CQRS for orders (read/write separation)
5. Discuss caching strategy (multi-tier)
6. Talk about flash sale handling (queues, rate limiting)

---

**Now go design that billion-dollar marketplace!** 🛒🚀

---

*Created with the COMMERCE technique: Capacity → Orders → Metrics → Memory → Estimation → Round → Check → Evaluate*
*Perfect for: FAANG interviews, E-commerce Architecture, Distributed Systems discussions*
