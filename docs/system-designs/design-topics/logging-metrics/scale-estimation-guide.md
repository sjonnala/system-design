# 🎯 Logging & Metrics System: Scale Estimation Masterclass

## The DELTA Technique for Observability Scale Math
**(D)ata volume → (E)vents per second → (L)atency targets → (T)hroughput capacity → (A)rchive requirements**

This mental framework applies specifically to data-intensive observability systems where **volume** is the primary driver.

---

## 📊 PART 1: System Scale Assumptions

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Infrastructure** | Total Servers | 10,000 | Medium-large company (similar to Airbnb, Pinterest scale) |
| | Microservices | 500 | Distributed architecture |
| | Containers (K8s pods) | 50,000 | Avg 5 pods per service |
| **Logging** | Log lines per server/sec | 100 | Moderate logging |
| | Avg log line size | 512 bytes | JSON structured logs |
| | Daily Active Users | 100M | For user event tracking |
| **Metrics** | Metrics per server | 200 | CPU, mem, disk, network, app |
| | Scrape interval | 15 seconds | Prometheus default |
| | Metric cardinality | 10M | Total unique time series |
| **Retention** | Hot logs retention | 90 days | Searchable in Elasticsearch |
| | Cold logs retention | 1 year | S3 archive |
| | Metrics retention (raw) | 15 days | Prometheus local |
| | Metrics retention (long-term) | 5 years | Thanos/S3 with downsampling |

---

## 🧮 PART 2: The "Data Pipeline Calculator" - Mental Math for Observability

### Rule #1: **Events Per Second Foundation**
```
Key Anchor: 10,000 servers × 100 logs/sec = 1 million log events/sec

Quick math:
• Small scale: 1,000 servers × 100 logs/sec = 100K events/sec
• Medium scale: 10,000 servers × 100 logs/sec = 1M events/sec
• Large scale: 100,000 servers × 100 logs/sec = 10M events/sec
```

### Rule #2: **Storage Explosion Formula**
```
EMOTION TRIGGER: "Logs grow FAST - a million events per second is 40TB per day!"

Daily storage = Events/sec × Seconds/day × Size/event
             = 1M × 86,400 × 512 bytes
             = 44 TB/day raw logs

With compression (5x): 44TB ÷ 5 = ~9 TB/day
```

### Rule #3: **Metrics Are Small But Numerous**
```
Metrics calculation is simpler:
• 1 data point = ~16 bytes (timestamp + value)
• 10M metrics × 4 samples/min × 16 bytes = 640 MB/min = ~1TB/day

But cardinality explosion is the REAL challenge!
```

---

## 📈 PART 3: Scale Math Template for Observability

```
┌─────────────────────────────────────────────────────────┐
│  🎯 OBSERVABILITY NAPKIN MATH - Logging & Metrics       │
└─────────────────────────────────────────────────────────┘

STEP 1: LOG VOLUME ESTIMATION
───────────────────────────────
Servers/Containers:      [____]
Log lines per server/sec:[____]
Avg log size:            [____] bytes

→ Log events/sec = Servers × Lines/sec      = [____]
→ Peak events/sec = Avg × 2                 = [____]
→ Daily volume = Events/sec × 86,400 × Size = [____] TB
→ With compression (5x) = Daily ÷ 5         = [____] TB

STEP 2: METRICS VOLUME ESTIMATION
───────────────────────────────
Active metrics (cardinality):    [____] M
Scrape interval:                 [____] seconds
Data point size:                 16 bytes (fixed)

→ Samples/min = Metrics × (60/interval)     = [____] M
→ Storage/min = Samples × 16 bytes          = [____] MB
→ Daily storage = Storage/min × 1440        = [____] GB
→ Yearly storage (raw) = Daily × 365        = [____] TB

STEP 3: STORAGE WITH RETENTION
───────────────────────────────
Log retention (hot):     [____] days
Metrics retention (raw): [____] days

→ Total log storage = Daily × Retention     = [____] TB
→ Total metric storage = Daily × Retention  = [____] TB

STEP 4: PROCESSING CAPACITY
───────────────────────────────
→ Kafka partitions needed = Events/sec ÷ 50K  = [____]
→ ES shards needed = Total storage ÷ 50GB     = [____]
→ Prometheus instances = Metrics ÷ 1M         = [____]

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────────
→ Ingest bandwidth = Events/sec × Size       = [____] MB/s
→ Query bandwidth (estimate 10% of ingest)   = [____] MB/s
```

---

## 💾 PART 4: Logging & Metrics System Filled Template

```
┌─────────────────────────────────────────────────────────┐
│    LOGGING & METRICS SYSTEM - NAPKIN MATH SOLUTION      │
└─────────────────────────────────────────────────────────┘

STEP 1: LOG VOLUME ESTIMATION
───────────────────────────────
Servers/Containers:      10,000 servers + 50,000 containers = 60,000 sources
Log lines per source/sec: 100 (avg)
Avg log size:            512 bytes (JSON structured)

→ Log events/sec = 60,000 × 100           = 6M events/sec
→ Peak events/sec = 6M × 2                = 12M events/sec
→ Daily volume = 6M × 86,400 × 512 bytes  = 265 TB/day (raw)
→ With compression (5x) = 265TB ÷ 5       = 53 TB/day (compressed)

STEP 2: METRICS VOLUME ESTIMATION
───────────────────────────────
Active metrics (cardinality):    10M time series
Scrape interval:                 15 seconds
Data point size:                 16 bytes (timestamp + float64)

→ Samples/min = 10M × (60/15)             = 40M samples/min
→ Storage/min = 40M × 16 bytes            = 640 MB/min
→ Daily storage = 640MB × 1440            = 922 GB/day ≈ 1 TB/day
→ Yearly storage (raw) = 1TB × 365        = 365 TB/year

With Prometheus compression (~1.3 bytes/sample):
→ Daily storage = 40M samples/min × 1440 × 1.3 bytes = 75 GB/day
→ 15-day retention = 75GB × 15            = ~1.1 TB (Prometheus local)

STEP 3: STORAGE WITH RETENTION
───────────────────────────────
Logs:
  Hot (Elasticsearch, 90 days):  53TB/day × 90    = 4,770 TB ≈ 5 PB
  Warm (ES, days 30-90):         53TB/day × 60    = 3,180 TB ≈ 3 PB
  Cold (S3 archive, 1 year):     53TB/day × 365   = 19,345 TB ≈ 19 PB

Metrics:
  Raw (Prometheus, 15 days):     75GB/day × 15    = 1.1 TB
  Long-term (Thanos/S3, 5 years):
    - 5min avg (90 days):        15GB/day × 90    = 1.4 TB
    - 1hr avg (1 year):          2GB/day × 365    = 730 GB
    - 1day avg (5 years):        100MB/day × 1825 = 183 GB
  Total long-term:                                = ~2.3 TB

STEP 4: PROCESSING CAPACITY
───────────────────────────────
Kafka:
  → Partitions needed = 6M events/sec ÷ 50K/partition = 120 partitions
  → With 3x replication & 30 topics                   = 360 total partitions
  → Kafka cluster size: 30-50 brokers (r5.2xlarge)

Elasticsearch:
  → Shards needed = 5 PB ÷ 50GB/shard                 = 100,000 shards
  → With 1 replica                                    = 200,000 total shards
  → Index strategy: Daily indices (logs-2025.11.15)
  → ES cluster size: 200-300 data nodes (i3.4xlarge)
    - Hot nodes (SSD): 50 nodes
    - Warm nodes (HDD): 100 nodes
    - Cold nodes (archive): 50 nodes

Prometheus:
  → Instances needed = 10M metrics ÷ 1M/instance      = 10 instances
  → With HA (2x)                                      = 20 instances
  → Instance size: r5.4xlarge (16 vCPU, 128GB RAM)

Stream Processing (Flink):
  → Task parallelism = 6M events/sec ÷ 100K/task      = 60 parallel tasks
  → Flink cluster: 20 task managers (m5.2xlarge)

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────────
Ingestion:
  → Log bandwidth = 6M events/sec × 512 bytes         = 3,072 MB/s ≈ 24 Gbps
  → Peak bandwidth = 24 Gbps × 2                      = 48 Gbps

Query (estimate 10% of ingest):
  → Query bandwidth                                   = 2.4 Gbps

Network requirements:
  → Total bandwidth capacity needed                   = 50+ Gbps
  → Use 100 Gbps network backbone
```

---

## 🧠 PART 5: Mental Math Techniques for Observability

### **Technique 1: The "Log Size Ladder"**
```
Memorize typical log sizes:
• Minimal (syslog):      ~200 bytes
• Structured JSON:       ~500 bytes
• Verbose app logs:      ~1 KB
• Stack traces:          ~5-10 KB

Pro Tip: Most production logs are 400-600 bytes (use 500 as default)
```

### **Technique 2: The "Compression Trick"**
```
ALWAYS apply compression in your calculations:

Text logs compress VERY well:
• gzip compression: 5-10x reduction
• Use 5x for estimation (conservative)

Example: 100TB raw logs → 20TB compressed
```

### **Technique 3: The "Cardinality Explosion"**
```
Metrics cardinality kills systems!

Example explosion:
Base metric: http_requests_total
Labels:
  - service: 100 values
  - endpoint: 50 values
  - method: 4 values (GET, POST, PUT, DELETE)
  - status: 10 values (200, 201, 400, 404, 500, etc.)

Total cardinality = 100 × 50 × 4 × 10 = 200,000 time series
From ONE metric type!

RULE: Limit high-cardinality labels (user_id, request_id are DANGEROUS!)
```

### **Technique 4: The "15-Second Rule"**
```
Prometheus default scrape = 15 seconds

Quick samples calculation:
• 1 minute = 4 samples
• 1 hour = 240 samples
• 1 day = 5,760 samples
• 15 days = 86,400 samples

For 10M metrics: 10M × 86,400 = 864 billion data points!
But compression helps: 864B × 1.3 bytes = 1.1 TB
```

### **Technique 5: The "Tier Memory Map"**
```
Storage tiers DRAMATICALLY reduce costs:

Hot tier (0-7 days):    SSD, full search, high IOPS    = EXPENSIVE
Warm tier (8-30 days):  HDD, reduced search, lower IOPS = MODERATE
Cold tier (31-90 days): Compressed, frozen, limited     = CHEAP
Archive (90+ days):     S3/Glacier, no search           = VERY CHEAP

Cost ratio: Hot:Warm:Cold:Archive = 10:3:1:0.1
```

---

## 🎨 PART 6: The Visual Mind Map for Observability

```
                🌐 OBSERVABILITY SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📝 LOGS           📊 METRICS        🔍 TRACES
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
   Vol  Ret        Card  Scrape      Spans  Sample
   53TB  90d       10M   15s         1M     10%
```

**Memory Trigger**: Think **"L.M.T."** = Logs (Volume), Metrics (Cardinality), Traces (Sampling)

---

## 🚨 PART 7: Common Mistakes in Observability Estimation

### Mistake 1: **Underestimating Log Volume**
```
✗ BAD:  "1000 servers × 10 logs/sec = 10K logs/sec"
✓ GOOD: "1000 servers × 100 logs/sec + spikes = 200K logs/sec peak"

Logs spike during:
• Deployments (health checks)
• Errors (stack traces, retries)
• High traffic events
→ Always plan for 2-3x average
```

### Mistake 2: **Forgetting Compression**
```
✗ BAD:  "100TB/day = $5000/day in S3"
✓ GOOD: "100TB/day compressed to 20TB = $1000/day in S3"

Compression is FREE and saves MASSIVE costs!
```

### Mistake 3: **Ignoring Cardinality Growth**
```
✗ BAD:  "We have 100 metrics, that's easy"
✓ GOOD: "100 base metrics × 1000 labels = 100K time series"

Cardinality explosion is the #1 Prometheus killer:
• Plan for 10K-1M active series (small/medium)
• 1M-10M for large deployments
• Beyond 10M: Federation/sharding required
```

### Mistake 4: **Not Planning for Retention Tiers**
```
✗ BAD:  "Store all logs in Elasticsearch for 1 year"
✓ GOOD: "Hot (7d ES SSD) → Warm (30d ES HDD) → Cold (90d frozen) → Archive (1yr S3)"

This reduces costs by 10-20x!
```

### Mistake 5: **Underestimating Query Load**
```
✗ BAD:  "Ingestion is 10 Gbps, so 10 Gbps network is enough"
✓ GOOD: "Ingestion 10 Gbps + Query 5 Gbps + Replication 10 Gbps = 25 Gbps needed"

Queries can be MORE expensive than ingestion:
• Dashboard with 50 panels = 50 concurrent queries
• 100 users × 50 panels = 5000 queries/min!
```

---

## 📝 PART 8: Real-World Capacity Planning Examples

### Example 1: Startup (100 servers)
```
Infrastructure:
  - Servers: 100
  - Microservices: 20
  - Containers: 500

Logs:
  - Events/sec: 100 servers × 100 = 10K events/sec
  - Daily volume: 10K × 86,400 × 500 bytes = 432 GB/day (raw)
  - Compressed: 432GB ÷ 5 = 86 GB/day
  - 90-day retention: 86GB × 90 = 7.7 TB

Storage:
  - Single ES cluster: 5 nodes (i3.xlarge)
  - Kafka: 3 brokers (r5.large)
  - Prometheus: 2 instances (r5.xlarge)

Cost estimate: ~$5K/month
```

### Example 2: Mid-size Company (1,000 servers)
```
Infrastructure:
  - Servers: 1,000
  - Microservices: 100
  - Containers: 5,000

Logs:
  - Events/sec: 1K servers × 100 = 100K events/sec
  - Daily volume: 100K × 86,400 × 500 = 4.3 TB/day (raw)
  - Compressed: 4.3TB ÷ 5 = 860 GB/day
  - 90-day retention: 860GB × 90 = 77 TB

Metrics:
  - Cardinality: 1M time series
  - Daily storage: 10 GB/day (compressed)
  - 15-day retention: 150 GB

Storage:
  - ES cluster: 20 nodes (hot: 5 i3.2xlarge, warm: 10 i3.xlarge, cold: 5)
  - Kafka: 9 brokers (r5.xlarge)
  - Prometheus: 5 instances (r5.2xlarge)

Cost estimate: ~$30K/month
```

### Example 3: Large Enterprise (10,000 servers)
```
(This is our main example from Part 4)

Infrastructure:
  - Servers: 10,000
  - Microservices: 500
  - Containers: 50,000

Logs:
  - Events/sec: 6M events/sec
  - Daily volume: 53 TB/day (compressed)
  - 90-day retention: 5 PB

Metrics:
  - Cardinality: 10M time series
  - Daily storage: 75 GB/day (compressed)
  - 15-day retention: 1.1 TB

Storage:
  - ES cluster: 200-300 nodes (tiered architecture)
  - Kafka: 30-50 brokers (r5.2xlarge)
  - Prometheus: 20 instances (r5.4xlarge) with Thanos

Cost estimate: ~$500K-800K/month
```

---

## 🔧 PART 9: Cost Optimization Strategies

### Strategy 1: **Sampling for Non-Critical Logs**
```
Not all logs need 100% capture:

• ERROR/FATAL: 100% (critical!)
• WARN: 100% (important)
• INFO: 10-50% (sample in production)
• DEBUG: 1% or OFF (dev only)

Savings: 50-70% reduction in log volume
```

### Strategy 2: **Metric Downsampling**
```
Raw retention: 15 days (full fidelity)
5-min avg: 90 days (20x reduction)
1-hr avg: 1 year (288x reduction)
1-day avg: 5 years (1440x reduction)

From 365 TB/year raw → ~10 TB/year long-term
Savings: 97% storage reduction!
```

### Strategy 3: **Intelligent Retention Policies**
```
Service-based retention:

Production services:
  - ERROR logs: 1 year
  - INFO logs: 90 days
  - DEBUG logs: 7 days

Non-production:
  - All logs: 7 days

Metrics:
  - Critical SLIs: 5 years
  - Standard metrics: 1 year
  - Debug metrics: 30 days

Savings: 30-50% storage costs
```

### Strategy 4: **Compression & Encoding**
```
Logs:
  - Use structured logging (JSON)
  - Apply gzip/zstd compression
  - Savings: 5-10x

Metrics:
  - Use Prometheus TSDB (1.3 bytes/sample)
  - Enable compression in Thanos
  - Savings: 10-20x vs uncompressed
```

### Strategy 5: **Query Optimization**
```
Expensive:
  - Full-text search across all indices
  - Aggregations without time bounds
  - Wildcard queries

Cheap:
  - Time-bounded queries (last 1 hour)
  - Indexed field searches
  - Pre-computed dashboards (cached)

Use Redis caching for dashboards: 90% query reduction!
```

---

## 🎯 PART 10: Interview-Ready Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║      OBSERVABILITY SYSTEM SCALE CHEAT SHEET            ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 server = ~100 log lines/sec
• 1 log line = ~500 bytes (structured JSON)
• 1 metric sample = ~16 bytes (or 1.3 with Prometheus compression)
• Compression ratio = 5x for logs, 10x for metrics
• Scrape interval = 15 seconds (Prometheus default)

QUICK FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Log volume/day = Servers × 100 logs/sec × 86,400 × 500 bytes ÷ 5
Metric storage/day = Cardinality × (86,400/15) × 1.3 bytes
Kafka partitions = Events/sec ÷ 50K
ES shards = Storage ÷ 50GB
Bandwidth = Events/sec × Event_size

TYPICAL SCALES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:  100 servers,    10K events/sec,   <1TB/day
Medium: 1K servers,     100K events/sec,  ~1TB/day
Large:  10K servers,    1M events/sec,    ~10TB/day
Huge:   100K+ servers,  10M+ events/sec,  100+ TB/day

RETENTION STRATEGIES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Logs:
  Hot (0-7d):    ES SSD, full search
  Warm (8-30d):  ES HDD, reduced replicas
  Cold (31-90d): ES frozen, S3 snapshot
  Archive (90d+): S3 Glacier, compliance

Metrics:
  Raw (15d):     Prometheus local
  5min (90d):    Thanos/S3
  1hr (1yr):     Downsampled
  1day (5yr):    Long-term trends

COST OPTIMIZATION:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Sample non-critical logs (50-70% savings)
✓ Use tiered storage (10x cost reduction)
✓ Enable compression (5-10x savings)
✓ Downsample metrics (97% storage savings)
✓ Cache dashboard queries (90% query reduction)

ANTI-PATTERNS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✗ Storing all logs in single ES tier
✗ Ignoring cardinality explosion
✗ Not compressing logs
✗ Using user_id/request_id as metric labels
✗ Full-text search without time bounds
✗ Forgetting to plan for peak traffic (2-3x avg)

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ 1K servers → ~100K events/sec (reasonable)
✓ 100TB/day logs → $3-5K/month S3 (compressed)
✓ 1M time series → ~75GB/day metrics (with compression)
✓ Elasticsearch can handle 10-20K writes/sec per node
✓ Kafka partition can handle 50-100K events/sec
✓ Prometheus can handle 1M active series

✗ 100K time series → 1TB/day (too high, check calculation!)
✗ Single ES node for 10M events/sec (impossible!)
✗ 50M cardinality in single Prometheus (need sharding!)
╚════════════════════════════════════════════════════════╝
```

---

## 🎁 PART 11: Practice Problems

### Problem 1: E-commerce Platform
```
Given:
- 5,000 backend servers
- 20,000 Kubernetes pods
- 500 microservices
- Peak traffic: 100K requests/sec
- Each request generates 5 log lines
- Metric cardinality: 5M time series

Calculate:
1. Log events per second
2. Daily log storage (compressed)
3. 90-day log retention storage
4. Metrics storage (15 days)
5. Kafka partition count
6. Elasticsearch cluster size

[Try it yourself, then check below]
```

<details>
<summary>Answer</summary>

```
1. LOG EVENTS/SEC:
   Background logs: 25K sources × 100 logs/sec = 2.5M events/sec
   Request logs: 100K req/sec × 5 logs = 500K events/sec
   Total = 2.5M + 500K = 3M events/sec
   Peak = 3M × 2 = 6M events/sec

2. DAILY LOG STORAGE:
   Raw = 3M × 86,400 × 500 bytes = 130 TB/day
   Compressed = 130TB ÷ 5 = 26 TB/day

3. 90-DAY RETENTION:
   26TB/day × 90 = 2,340 TB ≈ 2.3 PB

4. METRICS STORAGE (15 days):
   Samples/min = 5M × (60/15) = 20M samples/min
   Daily = 20M × 1440 × 1.3 bytes = 37 GB/day
   15-day retention = 37GB × 15 = 555 GB

5. KAFKA PARTITIONS:
   3M events/sec ÷ 50K/partition = 60 partitions
   With 3x replication = 180 partition replicas
   Brokers needed: 20-30 (r5.2xlarge)

6. ELASTICSEARCH CLUSTER:
   2.3 PB ÷ 50GB/shard = 46,000 shards
   With 1 replica = 92,000 total shards
   Cluster size: 100-150 nodes (tiered)
```
</details>

---

### Problem 2: SaaS Platform
```
Given:
- 50,000 tenants
- 200 servers
- Each tenant: 10 metrics
- Each server: 1000 metrics
- Average request: 2 log lines
- Request rate: 10K req/sec

Calculate:
1. Total metric cardinality
2. Is this safe for single Prometheus?
3. Daily log volume
4. Estimated monthly AWS cost (rough)

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. METRIC CARDINALITY:
   Tenant metrics: 50K tenants × 10 = 500K
   Server metrics: 200 servers × 1000 = 200K
   Total = 500K + 200K = 700K time series

2. PROMETHEUS SAFETY:
   700K series is SAFE for single Prometheus instance
   (Prometheus handles up to 1M series comfortably)
   Use r5.2xlarge (8 vCPU, 64GB RAM)

3. DAILY LOG VOLUME:
   Events/sec = 10K req/sec × 2 logs + 200 servers × 100 = 40K events/sec
   Daily = 40K × 86,400 × 500 bytes = 1.7 TB/day (raw)
   Compressed = 1.7TB ÷ 5 = 350 GB/day

4. MONTHLY AWS COST (rough estimate):
   Elasticsearch (10 nodes i3.xlarge): $3,000
   Kafka (3 brokers r5.large): $500
   Prometheus (2 instances r5.2xlarge): $800
   S3 storage (10TB): $250
   Data transfer: $500
   Total: ~$5,000-6,000/month
```
</details>

---

## 📚 PART 12: Additional Resources

**Books:**
- "Distributed Systems Observability" by Cindy Sridharan
- "Observability Engineering" by Charity Majors, Liz Fong-Jones
- "Site Reliability Engineering" by Google

**Industry Benchmarks:**
- Elasticsearch: 10-20K writes/sec per node
- Kafka: 50-100K events/sec per partition
- Prometheus: 1M active series per instance
- Fluentd: 10-20K events/sec per core

**Real-World Examples:**
- Uber: 100M+ metrics, 10PB logs/year
- Netflix: 2.5M metrics, 1 trillion events/day
- Datadog: Handles 1M+ metrics/sec for customers

---

**Remember:**
> "In observability systems, DATA VOLUME is the primary cost driver. Every optimization that reduces volume pays massive dividends."

**Key Insight:**
> "The 80-20 rule applies EVERYWHERE: 20% of logs contain 80% of value. Sample aggressively!"

---

*Created with the DELTA technique: Data → Events → Latency → Throughput → Archive*
*Perfect for: FAANG interviews, SRE roles, Platform Engineering, Observability teams*
