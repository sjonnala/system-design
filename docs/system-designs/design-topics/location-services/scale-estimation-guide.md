# 🎯 Location-Based Services Scale Estimation Masterclass

## The GEO-SCALE Framework
**(G)eography → (E)stimate → (O)ptimize → (S)hard → (C)ache → (A)ggregate → (L)ocalize → (E)valuate**

This mental framework is specifically designed for **geospatial systems at scale**.

---

## 📊 PART 1: Scale Assumptions for Yelp/Google Places

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 500M | Yelp scale globally |
| | Daily Active Users (DAU) | 100M | ~20% engagement rate |
| | Peak Concurrent Users | 5M | ~5% of DAU online simultaneously |
| **Business Data** | Total Businesses | 200M | Global coverage |
| | Active Businesses | 150M | 75% actively updated |
| | New Businesses/Day | 50K | Organic growth |
| **Search Pattern** | Searches per DAU | 5 | Average user searches 5x/day |
| | Total Searches/Day | 500M | 100M DAU × 5 searches |
| | Read:Write Ratio | 50:1 | Mostly search, fewer reviews |
| **Review Activity** | Users Who Review | 10% | Only power users review |
| | Reviews/Day | 10M | 10M users × 1 review avg |
| | Average Review Size | 500 bytes | Text + metadata |
| **Photo Data** | Photos per Business | 3 avg | Some have 100+, many have 0 |
| | Photo Size | 2 MB | Compressed JPEG/WebP |
| | Photo Uploads/Day | 100K | User-generated content |

---

## 🧮 PART 2: The Napkin Math for Location Services

### Rule #1: **Geographic Distribution Matters**

```
US/Canada:     30% of traffic (high density)
Europe:        25% of traffic (medium density)
Asia:          35% of traffic (very high density)
Rest of World: 10% of traffic (sparse)

Important: Density affects:
- Geohash precision needed
- Number of results per search
- Cache efficiency
```

### Rule #2: **The Search Radius Ladder**

```
Common Search Radii:
  500m:   Walking distance → Geohash precision 7 (~76m)
  2km:    Biking distance → Geohash precision 6 (~610m)
  5km:    Driving distance → Geohash precision 5 (~2.4km)
  20km:   Extended area    → Geohash precision 4 (~20km)

Mental shortcut: Each precision level ≈ 5-8x larger area
```

### Rule #3: **The Photo Storage Reality**

```
Photos dominate storage:

Business Data:    200M × 5KB     = 1 TB
Reviews:          10B × 500B     = 5 TB
Indexes:          Various        = 0.5 TB
Photos:           600M × 2MB     = 1.2 PB ← 99% of storage!

Lesson: Separate photo storage (S3 + CDN) from operational data
```

---

## 📈 PART 3: Complete Scale Estimation Template

```
┌─────────────────────────────────────────────────────────┐
│   LOCATION-BASED SERVICES - NAPKIN MATH SOLUTION       │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Search Operations/Day:     500 M (100M DAU × 5 searches)
Review Writes/Day:         10 M
Photo Uploads/Day:         100 K
Business Updates/Day:      1 M
Read:Write Ratio:          50:1

→ Search QPS    = 500M ÷ 100K     = 5,000 QPS
→ Review QPS    = 10M ÷ 100K      = 100 QPS
→ Photo QPS     = 100K ÷ 100K     = 1 QPS
→ Peak QPS      = 5K × 2          = 10,000 QPS

STEP 2: STORAGE ESTIMATION
───────────────────────────
Business Data (PostgreSQL + PostGIS):
  - 200M businesses × 5KB          = 1 TB
  - Geospatial indexes (GIST)      = 200 GB
  - Subtotal                       = 1.2 TB

Reviews (MongoDB):
  - 10M/day × 365 days × 3 years   = 11B reviews
  - 11B × 500 bytes                = 5.5 TB

Photos (S3):
  - 200M businesses × 3 photos     = 600M photos
  - 600M × 2 MB                    = 1.2 PB
  - Thumbnails (3 sizes)           = +300 GB

Search Index (Elasticsearch):
  - 200M businesses × 2KB          = 400 GB
  - 3 replicas                     = 1.2 TB

Total Storage:
  - Operational Data (hot)         = ~7 TB
  - Photo Storage (cold/CDN)       = 1.2 PB
  - Total                          = ~1.2 PB

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
Search Bandwidth:
  - 5K QPS × 5KB (JSON response)   = 25 MB/sec
  - Peak: 50 MB/sec

Photo Bandwidth (CDN):
  - 2K concurrent photo viewers
  - 2K × 2 MB                      = 4 GB/sec
  - This is why CDN is critical!

Upload Bandwidth:
  - 1 QPS × 2 MB                   = 2 MB/sec (manageable)

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Using 80-20 Rule:
  - 20% of searches = 80% of traffic
  - Hot geohashes (popular locations)

Cache Strategy:
  - L1 (Application): 1 GB per instance
  - L2 (Redis Cluster): 100 GB
    → Popular geohashes + results
    → Business details for hot locations
  - L3 (CDN): 10 TB
    → Photos, map tiles

Cache Hit Rate Target: 85%
```

---

## 💾 PART 4: Geospatial-Specific Calculations

### **Geohash Cell Count Estimation**

```python
# How many geohash cells to cover the world?

Earth surface area: 510 million km²

Geohash precision 4 (±20 km):
  - Cell area: ~400 km²
  - Cells needed: 510M / 400 = 1.3M cells

Geohash precision 5 (±2.4 km):
  - Cell area: ~6 km²
  - Cells needed: 510M / 6 = 85M cells

Geohash precision 6 (±610 m):
  - Cell area: ~0.37 km²
  - Cells needed: 510M / 0.37 = 1.4B cells

Mental Shortcut: Each precision level = 32× more cells
```

### **Database Shard Planning**

```
If sharding by geohash prefix (1 char):

32 possible values: [0-9, a-z except a,i,l,o]
- '9': North America West
- 'c': North America East
- 'd': US East Coast
- 'f': Europe North
- 'u': Europe Central
- 'w': Asia East
- ...

Each shard handles:
  - Businesses: 200M / 32 = 6.25M per shard
  - Storage: 1TB / 32 = 31 GB per shard
  - Traffic: Varies by region density
```

### **Proximity Query Performance**

```
Typical search: "restaurants within 5km"

Method 1: PostGIS (single server)
  - ST_DWithin query on 200M rows
  - With GIST index: ~100-200ms
  - Bottleneck at >10M businesses per shard

Method 2: Geohash + Elasticsearch
  - Filter by geohash prefix: <10ms
  - Geospatial query on subset: ~20-50ms
  - Scales to billions of points

Recommendation: Elasticsearch for >100M points
```

---

## 🧠 PART 5: Mental Math for Geospatial Systems

### **Technique 1: The "Radius to Precision" Calculator**

```
Memorize this mapping:

Search Radius    →    Geohash Precision
  100m                     7 chars
  500m                     6 chars
  2km                      5 chars
  10km                     4 chars
  50km                     3 chars

Formula in interviews:
  precision = 7 - log₅(radius_km)
  (rough approximation)
```

### **Technique 2: The "Search Result Count" Estimator**

```
Average business density (urban areas):
  - 100 restaurants per km²
  - 500 businesses per km²

For 5km radius search:
  - Area = π × r² = 3.14 × 25 = ~80 km²
  - Expected results = 80 × 500 = 40,000 businesses!

Filters reduce this:
  - Category (restaurants): ÷ 5 = 8,000
  - Rating >4.0: ÷ 3 = 2,667
  - Open now: ÷ 2 = 1,333
  - Return top 20 results

Lesson: Always filter before sorting
```

### **Technique 3: The "Distance Calculation Cost"**

```
Haversine distance (spherical Earth):
  - 1 calculation: ~10 CPU instructions
  - 1 server: ~1M calculations/sec

If checking 10,000 businesses:
  - 10K calculations = 10ms
  - Acceptable for real-time

If checking 1M businesses:
  - 1M calculations = 1 second
  - Too slow! Use geohash filtering first
```

---

## 🎯 PART 6: Regional Capacity Planning

### **Traffic Distribution by Region**

```
┌─────────────────────────────────────────────────┐
│  GLOBAL TRAFFIC SHARDING STRATEGY               │
└─────────────────────────────────────────────────┘

Region          Traffic %   Searches/Day   Peak QPS   Servers
──────────────────────────────────────────────────────────────
US-West         15%         75M            900        12
US-East         15%         75M            900        12
Europe-West     20%         100M           1,200      16
Europe-East     5%          25M            300        4
Asia-Pacific    30%         150M           1,800      24
South America   5%          25M            300        4
Others          10%         50M            600        8
──────────────────────────────────────────────────────────────
TOTAL           100%        500M           6,000      80

Over-provisioning for peak: ×2 = 160 servers total

Cost Estimation:
  - 160 × m5.2xlarge (8 vCPU, 32GB) = $80K/month
  - Database (RDS): $20K/month
  - Elasticsearch cluster: $15K/month
  - Redis cluster: $5K/month
  - S3 + CloudFront: $50K/month
  - Total: ~$170K/month infrastructure
```

---

## 📊 PART 7: Database Sizing Worksheet

### **PostgreSQL + PostGIS Sizing**

```
Business Table:
  - Rows: 200M
  - Row size: 5KB
  - Total: 1 TB
  - Indexes (GIST, B-tree): 200 GB
  - Working set (hot data): 100 GB

Recommended Instance:
  - AWS RDS: db.r5.4xlarge (16 vCPU, 128 GB RAM)
  - Storage: 2 TB SSD (gp3)
  - Cost: ~$3,000/month (on-demand)

Sharding Strategy:
  - 8 shards by geohash prefix
  - Each shard: 25M businesses, 125 GB data
  - Instance per shard: db.r5.xlarge ($1K/month)
  - Total: 8 × $1K = $8K/month
```

### **MongoDB (Reviews) Sizing**

```
Reviews Collection:
  - Documents: 11B (3 years)
  - Document size: 500 bytes
  - Total: 5.5 TB
  - Indexes: 1 TB
  - Working set: 500 GB (recent reviews)

Sharding:
  - Shard key: business_id (ensures co-location)
  - 12 shards
  - Each shard: ~550 GB

Cluster Config:
  - 12 × m5.xlarge (4 vCPU, 16 GB) = $6K/month
  - Replica set (3 nodes per shard)
  - Total nodes: 36
  - Total cost: ~$18K/month
```

### **Elasticsearch Sizing**

```
Business Index:
  - Documents: 200M
  - Document size: 2KB
  - Total: 400 GB
  - Replication (3 copies): 1.2 TB

Cluster Config:
  - 9 data nodes: i3.2xlarge (8 vCPU, 61 GB, 1.9 TB NVMe)
  - 3 master nodes: m5.large (dedicated masters)
  - Total cost: ~$12K/month

Performance:
  - Geospatial queries: <50ms (P95)
  - Indexing rate: 10K docs/sec
  - Search throughput: 5K QPS
```

---

## 🚀 PART 8: Performance Benchmarks

### **Latency Budget Breakdown**

```
Target: <200ms end-to-end latency (P95)

┌─────────────────────────────────────────────────┐
│  LATENCY BREAKDOWN (Proximity Search)           │
└─────────────────────────────────────────────────┘

Component                    P50      P95      P99
──────────────────────────────────────────────────
Network (client → CDN)       10ms     30ms     60ms
CDN → Load Balancer          5ms      10ms     20ms
LB → API Gateway             2ms      5ms      10ms
Auth + Rate Limit            5ms      10ms     20ms
Search Service Logic         5ms      15ms     30ms
Cache Check (Redis)          1ms      3ms      8ms
Elasticsearch Query          20ms     80ms     150ms
PostGIS Fallback             50ms     120ms    250ms
Ranking Algorithm            10ms     20ms     40ms
Response Serialization       3ms      8ms      15ms
──────────────────────────────────────────────────
TOTAL (Cache Hit)            41ms     101ms    193ms ✓
TOTAL (ES Hit)               61ms     181ms    353ms
TOTAL (DB Hit)               91ms     231ms    453ms ✗

Optimization Goal: 85% cache hit rate → P95 <150ms
```

### **Throughput Benchmarks**

```
Single Server Capacity (m5.2xlarge):

Component                Throughput       Notes
────────────────────────────────────────────────────────
PostgreSQL + PostGIS     1,000 QPS        Geo queries
Elasticsearch Node       500-1,000 QPS    Depends on query
MongoDB                  10,000 QPS       Read from secondary
Redis                    100,000 QPS      GET operations
Application Server       2,000 QPS        Stateless service

Scaling Strategy:
  - Horizontal: Add more app servers (easy)
  - Database: Read replicas (up to 5x read capacity)
  - Elasticsearch: More data nodes (linear scaling)
  - Redis: Cluster mode (16K slots, sharded)
```

---

## 🎨 PART 9: Cost Optimization Strategies

### **Strategy 1: Tiered Storage for Photos**

```
Photo Lifecycle:
  0-7 days:    S3 Standard      ($0.023/GB)  → Hot
  7-90 days:   S3 IA            ($0.0125/GB) → Warm
  90+ days:    S3 Glacier       ($0.004/GB)  → Cold

For 1.2 PB of photos:
  - Hot (10%):   120 TB × $0.023 = $2,760/month
  - Warm (30%):  360 TB × $0.0125 = $4,500/month
  - Cold (60%):  720 TB × $0.004  = $2,880/month
  - Total: $10,140/month

vs. All S3 Standard: $27,600/month
Savings: 63% ($17K/month)
```

### **Strategy 2: Reserved Instances**

```
Infrastructure Mix:
  - 80% predictable baseline: Reserved Instances (1-year)
  - 20% variable peak: Spot Instances + On-Demand

Example:
  - 128 servers needed
  - 100 Reserved (1-year): 40% discount = $48K saved/year
  - 28 On-Demand for peaks

Total savings: ~$50K/year
```

### **Strategy 3: CDN Optimization**

```
Photo bandwidth: 4 GB/sec × 86,400 sec/day = 345 TB/day

CloudFront pricing (simplified):
  - First 10 TB:     $0.085/GB
  - Next 40 TB:      $0.080/GB
  - Next 100 TB:     $0.060/GB
  - Next 350 TB:     $0.040/GB
  - Over 500 TB:     $0.020/GB

Monthly (10 PB transferred):
  - Without CDN (origin bandwidth): $150K
  - With CDN (95% hit rate):        $20K
  - Savings: $130K/month!

Lesson: CDN is not optional for photo-heavy apps
```

---

## 🔁 PART 10: Practice Problems

### **Problem 1: Uber-Style Real-Time Location Tracking**

```
Given:
  - 10M active riders simultaneously
  - Location update every 5 seconds
  - Each update: 100 bytes (lat, lon, heading, speed)
  - Drivers query nearby riders every 2 seconds
  - Retention: 1 hour

Calculate:
1. Write QPS
2. Read QPS
3. Storage needed
4. Bandwidth

[Try it yourself, then check answer below]
```

<details>
<summary>Answer</summary>

```
1. WRITE QPS:
   - 10M riders × 1 update/5sec = 2M updates/sec
   - Peak (3× avg): 6M writes/sec

2. READ QPS:
   - 1M active drivers × 1 query/2sec = 500K queries/sec
   - Each query checks ~50 nearby riders
   - Actual reads: 500K × 50 = 25M reads/sec

3. STORAGE (1 hour):
   - Updates/hour: 2M/sec × 3,600 = 7.2B updates
   - 7.2B × 100 bytes = 720 GB/hour
   - Use time-series DB (InfluxDB, TimescaleDB)
   - Compress + rolling window: ~100 GB active

4. BANDWIDTH:
   - Write: 2M × 100B = 200 MB/sec
   - Read: 25M × 100B = 2.5 GB/sec
   - Need Redis for caching active locations!
```
</details>

---

### **Problem 2: Airbnb-Style Listing Search**

```
Given:
  - 7M listings globally
  - 50M searches/day
  - Each listing: 10KB (details, photos metadata)
  - Average search results: 30 listings
  - Filters: price, dates, amenities (20 options)

Calculate:
1. Search QPS
2. Storage for listings
3. Elasticsearch index size
4. Cache size for hot searches

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. SEARCH QPS:
   - 50M/day ÷ 100K = 500 QPS
   - Peak (2×): 1,000 QPS

2. STORAGE:
   - 7M listings × 10KB = 70 GB (manageable!)

3. ELASTICSEARCH INDEX:
   - Indexed fields: location, price, dates, amenities
   - Estimated: 2KB per document
   - 7M × 2KB = 14 GB per index
   - With 3 replicas: 42 GB total

4. CACHE SIZE:
   - 20% of searches are duplicates (same params)
   - Cache key: geohash + filters hash
   - Unique searches/day: 50M × 0.8 = 40M
   - Hot searches (1 hour): 40M / 24 = 1.7M
   - Each cached result: 30 listings × 1KB = 30KB
   - Total: 1.7M × 30KB = 51 GB

   Practical cache: 20 GB (most popular searches)
```
</details>

---

## 📝 PART 11: Interview Cheat Sheet

```
┌──────────────────────────────────────────────────┐
│  LOCATION-BASED SERVICES - INTERVIEW CHECKLIST  │
└──────────────────────────────────────────────────┘

CLARIFYING QUESTIONS (5 min):
  □ Geographic scope? (single city, country, global)
  □ Expected user base? (DAU, peak concurrent)
  □ Search patterns? (radius, filters, sorting)
  □ Latency requirements? (<200ms typical)
  □ Write patterns? (reviews, check-ins, updates)
  □ Photo/media support?

SCALE ESTIMATION (5 min):
  □ Traffic: Searches/day → QPS
  □ Storage: Businesses + Reviews + Photos
  □ Bandwidth: Esp. for photos (use CDN!)
  □ Cache: 80-20 rule for hot locations

DESIGN (20 min):
  □ Geospatial indexing: Geohash, PostGIS, or S2
  □ Database choice: PostgreSQL for ACID, MongoDB for flexibility
  □ Search engine: Elasticsearch for full-text + geo
  □ Caching: Multi-tier (CDN, Redis, app)
  □ Ranking: Distance + Rating + Popularity

DEEP DIVES (10 min):
  □ Geohash precision selection
  □ Handling geohash boundaries
  □ Database sharding strategy
  □ Photo storage & CDN
  □ Review spam detection
  □ Real-time updates (WebSocket)

TRADE-OFFS:
  □ Accuracy vs Performance
  □ Consistency vs Availability
  □ Storage Cost vs Query Speed
  □ Global vs Regional deployment
```

---

## 🎁 BONUS: Quick Reference Card

```
╔════════════════════════════════════════════════════════╗
║    LOCATION SERVICES SCALE CHEAT SHEET                 ║
╚════════════════════════════════════════════════════════╝

GEOHASH PRECISION:
  4 chars: ±20 km   (city)
  5 chars: ±2.4 km  (neighborhood)
  6 chars: ±610 m   (street) ← Most common
  7 chars: ±76 m    (building)

TYPICAL SCALE (Yelp-size):
  Businesses:     200M
  DAU:            100M
  Search QPS:     5,000 (avg), 10,000 (peak)
  Review QPS:     100
  Storage:        ~1.2 PB (mostly photos)
  Servers:        ~160 globally

DATABASE CHOICES:
  Business data:  PostgreSQL + PostGIS
  Reviews:        MongoDB (flexible schema)
  Search:         Elasticsearch (geo + full-text)
  Cache:          Redis (hot locations)
  Photos:         S3 + CloudFront CDN

COST BREAKDOWN (monthly):
  Compute:        $80K  (160 servers)
  Database:       $30K  (PostgreSQL + MongoDB)
  Elasticsearch:  $15K  (9-node cluster)
  Storage (S3):   $15K  (with lifecycle)
  CDN:            $20K  (photo delivery)
  Misc:           $10K  (monitoring, backups)
  ────────────────────
  TOTAL:          $170K/month

LATENCY TARGETS:
  P50:  <50ms   (cache hit)
  P95:  <150ms  (Elasticsearch)
  P99:  <300ms  (acceptable)

SANITY CHECKS:
  ✓ Can Redis cache 100GB? YES
  ✓ Can Elasticsearch handle 5K QPS geo queries? YES
  ✓ Can S3 serve 4 GB/sec via CDN? YES
  ✓ Can PostgreSQL store 200M rows with geo index? YES

  ✗ Can single DB handle 10K write QPS? NO (need sharding)
  ✗ Can store 1PB photos in database? NO (use S3)
╚════════════════════════════════════════════════════════╝
```

---

## 🎓 Final Wisdom for Geospatial Systems

> **"In location-based systems, geography IS your sharding strategy"**

**Key Principles:**

1. **Shard by Location**: Use geohash prefixes for natural partitioning
2. **Cache Aggressively**: Popular locations = 80% of searches
3. **CDN is Mandatory**: Photos dominate bandwidth
4. **Index Strategy Matters**: Geohash for scale, PostGIS for accuracy
5. **Eventual Consistency OK**: Reviews can lag, location data cannot

---

**Remember:**
> "The goal is demonstrating geospatial thinking and awareness of distributed systems at global scale."

**Now go crush those location-based system design interviews!** 🚀🗺️

---

*Created with the GEO-SCALE framework: Geography → Estimate → Optimize → Shard → Cache → Aggregate → Localize → Evaluate*
