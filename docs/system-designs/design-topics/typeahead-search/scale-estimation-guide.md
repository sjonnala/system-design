# 🔍 Typeahead + Search System: Scale Estimation Masterclass

## The SEARCH Technique for Scale Math
**(S)cope → (E)stimate Users → (A)nalyze Patterns → (R)ank Priorities → (C)alculate Resources → (H)euristics Check**

This framework helps you systematically approach search system capacity planning.

---

## 📊 PART 1: Users & Traffic Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 500M | Google-scale search |
| | Daily Active Users (DAU) | 100M | ~20% engagement |
| | Searches per DAU | 10 | Industry average |
| | Typeahead requests per search | 10 | User types ~10 chars |
| **Traffic Distribution** | Peak:Average Ratio | 3x | Lunch/evening spikes |
| | Read:Write Ratio | 1000:1 | Searches >> Indexing |
| **Query Characteristics** | Avg query length | 3-5 words | Typical search |
| | Unique queries per day | 500M | 50% unique |
| **Content Scale** | Total documents | 10B | Web-scale corpus |
| | New docs per day | 100M | Fresh content |
| | Avg document size | 5KB | Typical web page |

---

## 🧮 PART 2: The "Search Engineer's Calculator"

### Rule #1: **The Query Traffic Ladder**

```
Remember these conversions:
• 1 Search = 10 Typeahead requests
• 100M DAU × 10 searches = 1B searches/day
• 1B searches × 10 typeahead = 10B typeahead/day
• 1 Day ≈ 100K seconds
```

### Rule #2: **The Index Size Formula**

```
Total Index Size = Docs × Avg Size × Expansion Factor

Expansion Factor:
- Inverted Index: 1.5x (tokenization overhead)
- With replicas (3x): 4.5x total
- With sharding metadata: 5x final

Example:
10B docs × 5KB × 5 = 250TB total storage
```

### Rule #3: **The Latency Budget**

```
Typeahead Latency Budget (100ms):
- Network: 20ms
- Load Balancer: 5ms
- Application Logic: 10ms
- Cache Lookup (Redis): 5ms
- Trie Traversal: 10ms
- Serialization: 10ms
- Buffer: 40ms

Search Latency Budget (500ms):
- Network: 50ms
- Application: 30ms
- Elasticsearch Query: 200ms
- Ranking Service: 100ms
- Aggregations: 70ms
- Buffer: 50ms
```

---

## 📈 PART 3: Capacity Planning Template

```
┌─────────────────────────────────────────────────────────┐
│  🎯 TYPEAHEAD + SEARCH NAPKIN MATH TEMPLATE             │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users:         100 M
Searches per user:          10
Typeahead per search:       10

→ Searches/Day   = 100M × 10       = 1B
→ Typeahead/Day  = 1B × 10         = 10B

→ Search QPS     = 1B ÷ 100K       = 10,000 QPS
→ Typeahead QPS  = 10B ÷ 100K      = 100,000 QPS
→ Peak QPS       = 100K × 3        = 300,000 QPS

STEP 2: STORAGE ESTIMATION
───────────────────────────
Documents:                  10 B
Avg document size:          5 KB
Index expansion:            5x (inverted index + replicas)

→ Raw Data       = 10B × 5KB       = 50 TB
→ Index Size     = 50TB × 5        = 250 TB
→ Query Logs     = 1B × 100B × 365 = 36.5 TB/year

Total Storage (1 year):              ~300 TB

STEP 3: CACHE SIZING (Typeahead)
───────────────────────────────
Unique prefixes (1-3 chars):   62^3 = 238K
Top queries per prefix:        10
Avg query size:                50 bytes

→ Trie Size      = 238K × 10 × 50  = ~120 MB (fits in memory!)

For all prefixes (up to 5 chars):
→ Trie Size      = 62^5 × 10 × 50  = ~45 GB

Redis Cache:                        100 GB (includes metadata)

STEP 4: BANDWIDTH ESTIMATION
───────────────────────────
Typeahead:
  Request:  100K QPS × 50B         = 5 MB/s
  Response: 100K QPS × 500B        = 50 MB/s

Search:
  Request:  10K QPS × 200B         = 2 MB/s
  Response: 10K QPS × 5KB          = 50 MB/s

→ Total Bandwidth                   = ~110 MB/s = 0.9 Gbps

STEP 5: COMPUTE RESOURCES
───────────────────────────
Typeahead Servers:
  Each server: 10K QPS capacity
  Required: 300K ÷ 10K             = 30 servers (peak)
  With buffer (30%):                 40 servers

Elasticsearch Nodes:
  Each node: 2K QPS search capacity
  Required: 30K ÷ 2K               = 15 nodes
  With replicas (3x):                45 nodes

Total Servers:                       ~100 (all services)
```

---

## 💾 PART 4: Detailed Breakdown by Component

### **A. Typeahead Service**

```
┌─────────────────────────────────────────────┐
│         TYPEAHEAD CAPACITY PLANNING         │
└─────────────────────────────────────────────┘

Traffic: 100,000 QPS average, 300,000 peak

MEMORY REQUIREMENTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Trie Structure:
  - Short prefixes (1-3 chars): 120 MB
  - Medium prefixes (4-5 chars): 45 GB
  - Long tail (6+ chars): Elasticsearch fallback

Redis Cache:
  - Hot queries (1M): 50 MB
  - Warm queries (10M): 500 MB
  - Total with overhead: 100 GB

Per-Server Memory: 16 GB RAM
  - Trie: 10 GB
  - Application: 2 GB
  - OS: 2 GB
  - Buffer: 2 GB

COMPUTE REQUIREMENTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Single Server Capacity:
  - CPU: 8 cores
  - Throughput: 10,000 QPS
  - Latency: P95 < 50ms

Servers Needed (Peak):
  300K QPS ÷ 10K = 30 servers
  With HA (N+2): 32 servers
  With deployments (20% extra): 40 servers

NETWORK:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Per Server:
  Inbound: 300K × 50B ÷ 40 = 375 KB/s
  Outbound: 300K × 500B ÷ 40 = 3.75 MB/s
  Total: ~4 MB/s per server (~32 Mbps)
```

### **B. Search Service (Elasticsearch)**

```
┌─────────────────────────────────────────────┐
│      ELASTICSEARCH CLUSTER PLANNING         │
└─────────────────────────────────────────────┘

Traffic: 10,000 QPS average, 30,000 peak

INDEX SIZE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Documents:        10 Billion
Avg size:         5 KB
Raw data:         50 TB

Inverted Index:   50TB × 1.5 = 75 TB
With metadata:    75TB × 1.2 = 90 TB

Shard Size (Recommended): 50 GB per shard
Number of Shards: 90TB ÷ 50GB = 1,800 shards

Replicas: 2 (total 3 copies)
Total Storage: 90TB × 3 = 270 TB

NODE SIZING:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Data Node Specs:
  - RAM: 64 GB (32 GB heap, 32 GB OS cache)
  - Disk: 6 TB SSD
  - CPU: 16 cores

Shards per Node: 6TB ÷ 50GB = ~100 shards

Total Data Nodes:
  Storage-based: 270TB ÷ 6TB = 45 nodes
  Performance-based: 1800 shards ÷ 100 = 18 nodes

  Choose max: 45 data nodes

Master Nodes: 3 (dedicated)
Coordinating Nodes: 5 (query routing)

Total Cluster: 53 nodes

MEMORY BREAKDOWN (per data node):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
JVM Heap:         32 GB (ES recommendation: ≤32GB)
  - Segment Memory: 20 GB
  - Field Cache: 5 GB
  - Filter Cache: 5 GB
  - Buffer: 2 GB

OS Page Cache:    32 GB (for Lucene segments)

QPS CAPACITY:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Per Data Node: 500 QPS
Cluster Total: 45 × 500 = 22,500 QPS
Peak Capacity: 22.5K QPS < 30K (need more nodes OR caching!)

Solution: Add Redis cache layer (80% hit rate)
  Actual ES load: 30K × 0.2 = 6K QPS ✓
```

### **C. Redis Cache Layer**

```
┌─────────────────────────────────────────────┐
│          REDIS CLUSTER SIZING               │
└─────────────────────────────────────────────┘

TYPEAHEAD CACHE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Hot Queries: 1M queries
Avg size: 500 bytes (query + top 10 suggestions)
Total: 1M × 500B = 500 MB

SEARCH CACHE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Hot Searches: 10M unique queries/day
Cache 20%: 2M queries
Avg response: 5KB (top 20 results + metadata)
Total: 2M × 5KB = 10 GB

TOTAL CACHE SIZE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Data: 10.5 GB
Overhead (30%): 3 GB
Total: ~15 GB

Redis Cluster:
  - Nodes: 3 masters + 3 replicas = 6 nodes
  - Memory per node: 32 GB
  - Total capacity: 96 GB (plenty of headroom)

THROUGHPUT:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Single Redis Node: 100K ops/sec
Cluster (3 masters): 300K ops/sec
Required: 100K typeahead + 10K search = 110K ops/sec ✓
```

### **D. Query Logging & Analytics**

```
┌─────────────────────────────────────────────┐
│        ANALYTICS STORAGE (ClickHouse)       │
└─────────────────────────────────────────────┘

Query Volume: 1B queries/day

LOG ENTRY SIZE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
{
  query_id: 16 bytes (UUID)
  query_text: 50 bytes
  user_id: 8 bytes
  timestamp: 8 bytes
  latency_ms: 4 bytes
  results_count: 2 bytes
  clicked_doc: 20 bytes
  metadata: 30 bytes (location, device, etc.)
}
Total: ~140 bytes per query

STORAGE (1 year):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Raw: 1B × 140B × 365 = 51 TB/year

ClickHouse Compression (10x): 5.1 TB/year

With replicas (2x): 10 TB/year

INGEST RATE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1B logs/day ÷ 100K sec = 10,000 rows/sec
Peak (3x): 30,000 rows/sec

ClickHouse Capacity: 100K+ rows/sec per node ✓

Cluster Size:
  - 3 nodes (sharded)
  - 4 TB storage per node
  - 32 GB RAM per node
```

---

## 🧠 PART 5: Mental Math Shortcuts for Search Systems

### **Shortcut 1: The "Character Set Rule"**
```
Prefix Space Calculation:

Alphanumeric: 62 characters (a-z, A-Z, 0-9)

1-char prefixes: 62^1 = 62
2-char prefixes: 62^2 = 3,844
3-char prefixes: 62^3 = 238,328
4-char prefixes: 62^4 = 14.7M
5-char prefixes: 62^5 = 916M

RULE OF THUMB:
- Store 1-3 char prefixes in memory (Trie): 238K entries
- Store 4-5 char prefixes in Redis: 15M entries
- Store 6+ char prefixes in Elasticsearch: Long tail
```

### **Shortcut 2: The "80-20 Cache Rule"**
```
For Search Systems:
- 20% of queries = 80% of traffic
- Cache the hot 20%

Example:
500M unique queries/day
Cache: 500M × 0.2 = 100M queries
Avg response: 5KB
Cache size: 100M × 5KB = 500 GB

BUT with TTL (1 hour):
Active queries in 1 hour: 1B ÷ 24 = 42M queries
Unique (50%): 21M queries
Cache size: 21M × 5KB = 105 GB ✓ (much better!)
```

### **Shortcut 3: The "Shard Size Rule"**
```
Elasticsearch Best Practice:
- Shard size: 30-50 GB (optimal)
- Shards per node: 20-100
- Heap memory: 50% of RAM (max 32GB)

Quick Calculation:
Index size: 90 TB
Shard size: 50 GB
Shards needed: 90,000 GB ÷ 50 GB = 1,800 shards

Nodes needed: 1,800 ÷ 40 shards/node = 45 nodes
```

### **Shortcut 4: The "QPS to Servers" Formula**
```
Generic Formula:
Servers = (Peak QPS × Safety Factor) ÷ Server Capacity

Typeahead Example:
Peak: 300K QPS
Server capacity: 10K QPS
Safety factor: 1.3 (30% buffer)
Servers = (300K × 1.3) ÷ 10K = 39 → round to 40 servers

Search Example:
Peak: 30K QPS (after 80% cache hit)
  ES sees: 30K × 0.2 = 6K QPS
ES node capacity: 500 QPS
Nodes = (6K × 1.3) ÷ 500 = 16 → round to 20 data nodes
```

---

## 🎨 PART 6: Visual Capacity Map

```
                    🔍 TYPEAHEAD + SEARCH SYSTEM
                               |
        ┌──────────────────────┼──────────────────────┐
        |                      |                      |
    💻 TYPEAHEAD           🔎 SEARCH             📊 ANALYTICS
        |                      |                      |
    ┌───┴───┐              ┌───┴───┐             ┌───┴───┐
   QPS   MEM             QPS   Storage        Volume  Storage
  100K   45GB           10K    270TB          1B/day   10TB

  40     100GB          45      -              3       4TB
 Servers Redis        Nodes                  Nodes    /node
```

---

## 🏗️ PART 7: Scaling Strategies

### **Horizontal Scaling**

```
┌─────────────────────────────────────────────────┐
│          SCALING DECISION MATRIX                │
└─────────────────────────────────────────────────┘

When to Scale Typeahead:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Trigger: P95 latency > 100ms OR CPU > 70%
Action: Add servers (horizontal scaling)
Cost: $500/month per server (c5.2xlarge)

When to Scale Elasticsearch:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Trigger: Disk > 85% OR Heap > 75% OR QPS degradation
Action:
  - Storage issue: Add data nodes
  - CPU issue: Add coordinating nodes
  - Memory issue: Increase heap (up to 32GB)
Cost: $2,000/month per data node (r5.4xlarge + 6TB SSD)

When to Scale Redis:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Trigger: Memory > 80% OR Hit rate < 70%
Action:
  - Memory issue: Add shards (resharding)
  - Hit rate: Increase TTL or cache size
Cost: $300/month per node (r5.xlarge)
```

### **Geographic Distribution**

```
Multi-Region Deployment:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Region 1 (US-East): 40% traffic
  - Typeahead: 16 servers
  - ES: 18 nodes
  - Redis: 3 masters

Region 2 (EU-West): 35% traffic
  - Typeahead: 14 servers
  - ES: 16 nodes
  - Redis: 3 masters

Region 3 (Asia): 25% traffic
  - Typeahead: 10 servers
  - ES: 11 nodes
  - Redis: 3 masters

Total Cost: 3x regional deployment + cross-region replication
```

---

## 🎯 PART 8: Cost Analysis

### **Monthly Infrastructure Cost Breakdown**

```
┌─────────────────────────────────────────────────┐
│         AWS COST ESTIMATION (Monthly)           │
└─────────────────────────────────────────────────┘

COMPUTE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Typeahead Servers (40 × c5.2xlarge):
  $0.34/hr × 40 × 730hrs = $9,928/mo

Elasticsearch Data Nodes (45 × r5.4xlarge):
  $1.008/hr × 45 × 730hrs = $33,082/mo

Elasticsearch Master/Coord (8 × r5.xlarge):
  $0.252/hr × 8 × 730hrs = $1,472/mo

Redis Cluster (6 × r5.xlarge):
  $0.252/hr × 6 × 730hrs = $1,104/mo

ClickHouse (3 × r5.2xlarge):
  $0.504/hr × 3 × 730hrs = $1,104/mo

STORAGE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
EBS SSD (270 TB for ES):
  270TB × $0.10/GB = $27,000/mo

S3 (Backups, 50TB):
  50TB × $0.023/GB = $1,150/mo

NETWORK:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Data Transfer Out (10TB/month):
  10TB × $0.09/GB = $900/mo

Load Balancer:
  ALB: $16.20/mo + $0.008/LCU
  ~$500/mo

TOTAL:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Compute:        $46,690/mo
Storage:        $28,150/mo
Network:        $1,400/mo
───────────────────────────
TOTAL:          ~$76,240/mo  (~$915K/year)

Cost per Query:
  $76,240 ÷ (1B searches × 30) = $0.0025 per search
  $76,240 ÷ (10B typeahead × 30) = $0.00025 per typeahead
```

---

## 💡 PART 9: Optimization Opportunities

### **Cost Optimization Strategies**

```
1. RESERVED INSTANCES:
   Save 40-60% on compute
   Estimated savings: $20K/mo

2. SPOT INSTANCES (for batch indexing):
   Save 70% on indexing workers
   Estimated savings: $3K/mo

3. S3 INTELLIGENT TIERING:
   Auto-move cold data to Glacier
   Estimated savings: $500/mo

4. COMPRESSION:
   ClickHouse: 10x compression
   ES: Enable best_compression
   Estimated savings: Covered in calculations

5. CACHE WARMING:
   Preload hot queries at startup
   Reduces cold-start ES load
   Cost: $0 (operational improvement)

Total Potential Savings: ~$24K/mo (30% reduction)
Optimized Cost: ~$52K/mo (~$624K/year)
```

### **Performance Optimization ROI**

```
Investment: Add Redis Cache Layer ($1,104/mo)
Impact: Reduce ES load by 80%
  Before: 45 ES nodes
  After: 15 ES nodes (keep 30 for storage)

Savings: 30 nodes × $735/mo = $22K/mo
ROI: $22K - $1.1K = $20.9K/mo saved!
```

---

## 📚 PART 10: Quick Reference Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║      TYPEAHEAD + SEARCH SCALE CHEAT SHEET             ║
╚════════════════════════════════════════════════════════╝

TRAFFIC RULES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 Search = 10 Typeahead requests
• Peak = 3× Average (for search systems)
• Cache hit rate target: 80-90%

CAPACITY RULES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Typeahead:
  • Server capacity: 10K QPS per server
  • Memory: 45 GB for full Trie
  • Latency: P95 < 100ms

Elasticsearch:
  • Shard size: 30-50 GB
  • Shards per node: 20-100
  • Node capacity: 500 QPS
  • Heap: 32 GB max

Redis:
  • Node capacity: 100K ops/sec
  • Memory: 32 GB typical
  • Hit rate: Target 85%

STORAGE FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Index Size = Docs × Size × 1.5 (inverted index)
             × 3 (replicas)
             = Docs × Size × 4.5

Trie Size = Prefixes × Top_K × Avg_Query_Size
          = 62^5 × 10 × 50 bytes
          = ~45 GB

LATENCY BUDGETS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Typeahead: 100ms total
  - Network: 20ms
  - Processing: 60ms
  - Buffer: 20ms

Search: 500ms total
  - Network: 50ms
  - Processing: 350ms
  - Buffer: 100ms

COST ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:  1M QPS,    $10K/mo
Medium: 10M QPS,   $50K/mo
Large:  100M QPS,  $500K/mo

SCALING TRIGGERS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Scale Up:
  • Latency: P95 > target
  • CPU: > 70%
  • Memory: > 80%
  • Disk: > 85%

Scale Down:
  • All metrics < 50% for 7 days
  • Cost optimization opportunity
╚════════════════════════════════════════════════════════╝
```

---

## 🎓 Professor's Final Wisdom

> **"In search systems, LATENCY is the king, RELEVANCE is the queen, and SCALE is the kingdom."**

### Key Principles:

1. **Two-Tier Optimization**:
   - Typeahead: Optimize for latency (<100ms)
   - Search: Optimize for relevance + latency (<500ms)

2. **Cache Aggressively**:
   - 80% cache hit = 5x cost reduction
   - Multi-tier: Trie → Redis → Elasticsearch

3. **Shard Smartly**:
   - ES shards: 30-50 GB sweet spot
   - Too many shards = overhead
   - Too few shards = hot spots

4. **Monitor Everything**:
   - Latency percentiles (P50, P95, P99)
   - Cache hit rates
   - Index lag time
   - Query relevance metrics

5. **Iterate on Ranking**:
   - Start with BM25
   - Add ML models incrementally
   - A/B test everything

---

## 🔄 Practice Exercise

**Challenge**: Design capacity for a **Product Search** system

```
Given:
- 50M products in catalog
- 20M DAU
- 5 searches per user per day
- Average 8 typeahead requests per search

Calculate:
1. Typeahead QPS (peak)
2. Search QPS (peak)
3. Index storage size
4. Number of ES nodes needed
5. Monthly AWS cost

[Try it yourself using the templates above!]
```

<details>
<summary>Answer</summary>

```
1. TYPEAHEAD QPS:
   20M × 5 × 8 = 800M typeahead/day
   QPS = 800M ÷ 100K = 8,000 QPS
   Peak = 8K × 3 = 24,000 QPS

2. SEARCH QPS:
   20M × 5 = 100M searches/day
   QPS = 100M ÷ 100K = 1,000 QPS
   Peak = 1K × 3 = 3,000 QPS

3. INDEX STORAGE:
   50M products × 10KB (product data) = 500 GB
   With index: 500GB × 1.5 = 750 GB
   With replicas: 750GB × 3 = 2.25 TB

4. ES NODES:
   Shards: 2,250 GB ÷ 50 GB = 45 shards
   Nodes: 45 ÷ 20 shards/node = 3 data nodes
   With replicas distributed: 5 nodes total

5. COST:
   Typeahead: 4 servers × $250 = $1K
   ES: 5 nodes × $2K = $10K
   Redis: 3 nodes × $300 = $900
   Storage: 2.25TB × $100/TB = $225
   Total: ~$12K/month
```
</details>

---

**Remember**:
> "Scale estimation is an art + science. Be logical, state assumptions, and demonstrate systematic thinking!"

---

*Created with the SEARCH technique: Scope → Estimate → Analyze → Rank → Calculate → Heuristics*
*Perfect for: FAANG interviews, System Design rounds, Capacity Planning*
