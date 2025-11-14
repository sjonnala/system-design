# Typeahead Search - Scale Estimation Guide

## Overview

Back-of-the-envelope calculations for designing a typeahead/autocomplete system like Google Search suggestions or Amazon product search.

---

## Step 1: Requirements & Assumptions

### Functional Requirements
- Provide search suggestions as user types
- Return top 10 suggestions per query
- Low latency (< 100ms)
- Support multiple languages
- Rank by popularity and relevance

### Non-Functional Requirements
- **Latency:** < 100ms (p95)
- **Availability:** 99.9% (3 nines)
- **Scale:** Global service

### Traffic Assumptions
- **Daily Active Users (DAU):** 500 million
- **Searches per user per day:** 10
- **Avg characters typed per search:** 15
- **Queries with suggestions:** 80% (some users type full query fast)

---

## Step 2: Traffic Estimates

### Read Traffic (Suggestion Requests)

```
DAU: 500M users
Searches per user per day: 10
Total searches per day: 5B

Characters typed per search: 15
Requests per search (with debouncing): 3-4 API calls
- User types: "p" → "py" → "pyt" → "python" (4 chars, but 3-4 calls after debounce)

Suggestion requests per day: 5B searches × 3 requests = 15B requests/day

Requests per second:
15B / (24 × 3600) = ~173,000 requests/sec

Peak traffic (2x): ~350,000 requests/sec
```

**Key Metric:** 173K-350K requests/second

### Write Traffic (Query Logging)

```
Actual searches performed: 5B/day
Log entries per second: 5B / (24 × 3600) = ~58,000 writes/sec

Peak: ~120,000 writes/sec
```

**Key Metric:** 58K-120K writes/second to Kafka

---

## Step 3: Storage Estimates

### Suggestions Database

**Unique Queries:**
```
Assume 1B unique queries globally (all languages)
- Popular queries: 10M (frequently searched)
- Medium queries: 90M (occasionally searched)
- Rare queries: 900M (searched infrequently)

Focus on top 10M queries for typeahead (filter low frequency)
```

**Per Suggestion Entry:**
```
query:          50 bytes (average query length)
frequency:      8 bytes (BIGINT)
category:       20 bytes (VARCHAR)
language:       10 bytes
metadata:       20 bytes
-------------------------------------------
Total per entry: ~100 bytes

Total storage for 10M queries:
10M × 100 bytes = 1GB

With indexes and overhead: ~3GB
```

**With full 1B queries (for analytics/long-tail):**
```
1B × 100 bytes = 100GB
With overhead: ~300GB
```

### Search Logs (Kafka)

**Per Log Entry:**
```
query:          50 bytes
user_id:        16 bytes (UUID)
timestamp:      8 bytes
session_id:     16 bytes
ip_address:     16 bytes
user_agent:     100 bytes
result_clicked: 1 byte
-------------------------------------------
Total per log:  ~200 bytes
```

**Daily Log Storage:**
```
Suggestion requests: 15B/day
15B × 200 bytes = 3TB/day

Actual searches: 5B/day
5B × 200 bytes = 1TB/day

Total logs: 4TB/day
```

**Retention:**
- Real-time processing: 7 days retention in Kafka = 28TB
- Long-term analytics: Move to S3 after 7 days
- 1 year of logs: 4TB × 365 = ~1.5PB (with compression: ~500TB)

### Trie Memory Size

**In-Memory Trie (per service node):**
```
Queries stored in Trie: 10M (top queries only)
Average query length: 10 characters
Trie nodes per query: ~8 (shared prefixes)

Total Trie nodes: 10M × 8 = 80M nodes

Per Trie Node:
- children map: 48 bytes (avg 3 children)
- isEndOfWord: 1 byte
- frequency: 8 bytes
- top suggestions: 80 bytes (10 suggestions × 8 byte pointers)
-------------------------------------------
Total per node: ~140 bytes

Total Trie size: 80M nodes × 140 bytes = 11.2GB
With overhead: ~15GB per service node
```

**Memory per service node:**
- Trie: 15GB
- Application: 2GB
- OS/Buffers: 1GB
- **Total: 18-20GB RAM per node**

---

## Step 4: Bandwidth Estimates

### Incoming Bandwidth (Suggestion Requests)

```
Request size:
- GET /api/suggestions?q=python&limit=10
- Headers: 500 bytes
- Query params: 50 bytes
Total request: ~550 bytes

Incoming bandwidth:
173K req/sec × 550 bytes = 95MB/sec
Peak: 350K req/sec × 550 bytes = 192MB/sec
```

**Average:** ~100MB/sec = ~800Mbps
**Peak:** ~200MB/sec = ~1.6Gbps

### Outgoing Bandwidth (Responses)

```
Response size:
- 10 suggestions × 30 bytes each = 300 bytes
- JSON overhead: 200 bytes
- Headers: 500 bytes
Total response: ~1KB

Outgoing bandwidth:
173K req/sec × 1KB = 173MB/sec
Peak: 350K req/sec × 1KB = 350MB/sec
```

**Average:** ~175MB/sec = ~1.4Gbps
**Peak:** ~350MB/sec = ~2.8Gbps

---

## Step 5: Cache Estimates

### Redis Cache Size

**Cache Strategy:** Cache popular query results

```
Cache hit ratio target: 95%
Queries cached: Most popular 20% of queries = 2M queries

Per cache entry:
- Key: "suggestions:{query}" = 30 bytes
- Value: JSON array of 10 suggestions = 500 bytes
Total per entry: ~530 bytes

Total cache size: 2M × 530 bytes = 1.06GB
With Redis overhead (2x): ~2GB
Add 50% buffer: ~3GB per Redis node
```

**Redis Cluster:**
- 3 master nodes: 3GB each = 9GB
- 3 replica nodes: 3GB each = 9GB
- **Total Redis: 18GB across 6 nodes**

---

## Step 6: Server Capacity Planning

### Typeahead Service Nodes

**Single Node Capacity:**
```
Assuming Go service on m5.2xlarge (8 vCPU, 32GB RAM):
- Can handle ~3,000-5,000 req/sec
- With caching: ~5,000 req/sec
- Trie memory: 20GB RAM
- Remaining for OS: 12GB
```

**Servers Needed:**
```
Average QPS: 173K
Server capacity: 5K req/sec
Servers needed: 173K / 5K = 35 servers

With 50% buffer: 53 servers
With failover capacity: 60 servers

Auto-scaling range: 40-80 servers
```

### Load Balancers

```
AWS ALB capacity: 100K req/sec per AZ
Our traffic: 173K req/sec average

Deploy across 3 AZs:
- Each AZ: ~60K req/sec
- Well within limits
```

---

## Step 7: Database & Processing

### Suggestions Database

```
Database: PostgreSQL or Elasticsearch

PostgreSQL:
- Instance: db.r5.xlarge (4 vCPU, 32GB RAM)
- Storage: 500GB SSD
- Read-heavy workload (mostly offline batch updates)
- Can handle 10K reads/sec easily

Our load: Mostly offline batch updates (hourly)
Database is not hot path (Trie serves suggestions)
```

### Kafka Cluster

```
Write throughput: 58K-120K messages/sec
Message size: 200 bytes
Throughput: 12-24MB/sec

Kafka cluster:
- 5 broker nodes (kafka.m5.large)
- 3 ZooKeeper nodes
- Replication factor: 3
- Can handle 100MB/sec easily
```

### Spark Processing

```
Hourly batch job:
- Process 625M log entries per hour
- Input size: 625M × 200 bytes = 125GB
- Processing time target: < 30 minutes

Spark cluster:
- 10 worker nodes (m5.2xlarge)
- Can process ~5GB/min
- 125GB / 5GB/min = 25 minutes ✓
```

---

## Step 8: Latency Breakdown

### User-Perceived Latency

```
Component                  Latency
---------------------------------
User types character       0ms
Debounce wait             300ms
Network (client → LB)      5ms
Load Balancer              1ms
Typeahead Service:
  - Redis cache lookup     1ms (95% cache hit)
  - Trie lookup            1ms (5% cache miss)
Network (LB → client)      5ms
Browser render            10ms
---------------------------------
Total (cache hit):        ~320ms
Total (cache miss):       ~325ms

User perceives: 300-330ms
(Majority is intentional debounce delay)
```

### API Response Time (excluding debounce)

```
Best case (local cache):  0ms (no API call)
Cache hit (Redis):        < 5ms (95%)
Cache miss (Trie):        < 10ms (5%)

p50: ~3ms
p95: ~8ms
p99: ~15ms
```

---

## Step 9: Cost Estimates (AWS)

### Compute (Typeahead Services)

```
Instance type: m5.2xlarge (8 vCPU, 32GB RAM)
Count: 60 instances (average)
Cost: $0.384/hour × 60 × 730 hours = $16,819/month

With auto-scaling (avg 50, peak 80):
Average cost: ~$14,000/month
```

### Redis Cache

```
ElastiCache Redis Cluster:
- 6 nodes: cache.r5.large (13.5GB RAM each)
- Cost: 6 × $0.238/hour × 730 hours = $1,042/month
```

### Database

```
PostgreSQL: db.r5.xlarge
Cost: $0.504/hour × 730 hours = $368/month

Elasticsearch (alternative):
- 3 nodes: r5.xlarge.elasticsearch
- Cost: 3 × $0.278/hour × 730 hours = $609/month
```

### Kafka Cluster

```
5 Kafka brokers: kafka.m5.large
Cost: 5 × $0.186/hour × 730 hours = $679/month

3 ZooKeeper nodes: t3.medium
Cost: 3 × $0.0416/hour × 730 hours = $91/month

Total Kafka: ~$770/month
```

### Spark Processing

```
EMR Cluster (runs hourly for 30 min):
- 10 worker nodes × 1 hour/day × 30 days
- m5.2xlarge: $0.384/hour
- Cost: 10 × 1 × 30 × $0.384 = $115/month
```

### Storage

```
S3 (logs, backups):
- 100TB × $0.023/GB = $2,300/month

RDS Storage:
- 500GB × $0.115/GB = $58/month

Total storage: ~$2,400/month
```

### Data Transfer

```
Outbound data transfer:
- 175MB/sec × 86,400 sec/day × 30 days = ~450TB/month
- First 10TB: $0.09/GB = $900
- Next 40TB: $0.085/GB = $3,400
- Next 100TB: $0.07/GB = $7,000
- Next 300TB: $0.05/GB = $15,000
Total: ~$26,000/month

(Note: Can reduce with CDN, regional deployments)
```

### Load Balancer

```
AWS ALB: $0.0225/hour × 3 AZs + LCU charges
Estimated: ~$500/month
```

### Total Monthly Cost

```
Compute (Typeahead):   $14,000
Redis Cache:            $1,042
Database:                 $368
Kafka:                    $770
Spark (EMR):              $115
Storage:                $2,400
Data Transfer:         $26,000
Load Balancer:            $500
Misc (monitoring):        $500
-------------------------------
Total:                 ~$45,700/month

With reserved instances (30% savings on compute):
Total: ~$40,000/month
```

**Cost Reduction Strategies:**
- Use CDN to cache popular queries (reduce data transfer)
- Multi-region deployment (serve users from nearest region)
- Reserved instances for predictable workload
- Spot instances for Spark batch processing
- Compression for logs and data transfer

---

## Step 10: Scaling Milestones

### 10x Growth (5B DAU, 50B searches/day)

**What Changes:**
```
Request QPS: 1.73M req/sec
Service nodes: 600 servers
Redis cache: 60 nodes (20GB each)
Kafka: 20 brokers
Spark: 50 workers
Logs: 40TB/day
Cost: ~$300K/month
```

**Actions Required:**
- Multi-region deployment (5 regions)
- CDN for API responses
- Larger Trie or sharded Trie
- Separate analytics data warehouse
- Dedicated team for operations

### 100x Growth (50B DAU, 500B searches/day)

**What Changes:**
```
Request QPS: 17M req/sec
Service nodes: 6,000 servers across 10 regions
Cost: ~$2-3M/month
```

**Actions Required:**
- Global CDN for all requests
- Sharded Trie by region/language
- Distributed Trie across multiple nodes
- Real-time updates (not hourly batch)
- Machine learning for personalization

---

## Step 11: Capacity Planning Summary

### Current Scale (500M DAU, 5B searches/day)

| Component | Specification | Quantity | Cost/Month |
|-----------|--------------|----------|------------|
| Typeahead Service | m5.2xlarge | 60 | $14,000 |
| Redis Cache | cache.r5.large | 6 | $1,042 |
| Database | db.r5.xlarge | 1 | $368 |
| Kafka | kafka.m5.large | 5 | $679 |
| Spark (EMR) | m5.2xlarge | 10 | $115 |
| Storage (S3) | Standard | 100TB | $2,300 |
| Data Transfer | Outbound | 450TB | $26,000 |
| Load Balancer | ALB | 3 AZs | $500 |
| **Total** | | | **~$45,000** |

### Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| API Latency (p95) | < 100ms | ~10ms (excluding debounce) |
| User Latency (p95) | < 500ms | ~320ms (with debounce) |
| Cache Hit Ratio | > 90% | 95% |
| Availability | 99.9% | 99.95% |
| Throughput | 350K QPS | 350K QPS (peak) |

---

## Key Formulas Reference

### QPS Calculation
```
QPS = (Daily Requests) / (24 hours × 3600 seconds)
Peak QPS = Average QPS × Peak Multiplier (typically 2x)
```

### Trie Memory Calculation
```
Trie Memory = (Num Queries × Avg Nodes per Query × Bytes per Node)
With Overhead = Trie Memory × 1.5
```

### Cache Size Calculation
```
Cache Size = (Num Cached Queries × Entry Size)
With Redis Overhead = Cache Size × 2
```

### Server Calculation
```
Servers Needed = Peak QPS / (Server Capacity QPS)
With Buffer = Servers Needed × 1.5
```

---

## Interview Tips

1. **Start with DAU:** Always begin with number of users
2. **Calculate QPS:** Most important metric for system design
3. **Debouncing Impact:** Explain 80-85% reduction in API calls
4. **Cache Hit Ratio:** Aim for 90-95% for typeahead
5. **Trie vs Database:** Explain why Trie is necessary (< 1ms lookups)
6. **Memory Constraints:** 10-20GB Trie fits in modern server RAM
7. **Batch vs Real-time:** Hourly updates acceptable for most use cases

## Common Interview Questions

**Q: "Why use Trie instead of database queries?"**
```
A: "Database queries with LIKE prefix% would be too slow:
- Database: ~10-50ms even with index
- Trie lookup: < 1ms (in-memory, O(k) complexity)

At 173K QPS, database would be bottleneck:
- Database: 173K × 10ms = needs 1,730 parallel connections
- Trie: 173K × 1ms = needs 173 parallel nodes

Trie is 10-50x faster and scales better."
```

**Q: "How do you handle Trie updates without downtime?"**
```
A: "Rolling update strategy:
1. Build new Trie offline (10 min)
2. Upload to S3 (2 min)
3. Rolling update to service nodes:
   - Update 10% of nodes at a time
   - Each node downloads and loads new Trie
   - Total time: ~30 min for all nodes
4. Old nodes continue serving with old Trie
5. No user-facing downtime

Latency of new trending queries: up to 1 hour (hourly updates)
For critical trending queries: can trigger manual update"
```

**Q: "How do you optimize for 10x traffic?"**
```
A: "Current: 173K QPS, 60 servers
10x: 1.73M QPS

Optimizations:
1. Multi-region deployment (3-5 regions)
   - Serve users from nearest region
   - 1.73M / 5 regions = 346K QPS per region
   - ~70 servers per region

2. CDN for popular queries
   - Cache top 10K queries at edge
   - Offload 50% of traffic
   - Remaining: 865K QPS

3. Larger Trie (20M queries instead of 10M)
   - Still fits in 30GB RAM
   - m5.4xlarge instances (64GB RAM)

Total: ~350 servers globally, ~$120K/month"
```

---

This estimation guide provides realistic numbers based on industry experience. Adjust based on specific requirements discussed in the interview!
