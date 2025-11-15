# Scale Estimation Guide: Collaborative Real-time Editor

## Table of Contents
1. [Introduction](#introduction)
2. [The POWER Technique](#the-power-technique)
3. [Assumptions (Google Docs Scale)](#assumptions-google-docs-scale)
4. [Traffic Estimation](#traffic-estimation)
5. [Storage Estimation](#storage-estimation)
6. [Bandwidth Estimation](#bandwidth-estimation)
7. [Infrastructure Sizing](#infrastructure-sizing)
8. [Caching Strategy](#caching-strategy)
9. [Database Sharding](#database-sharding)
10. [Practice Problems](#practice-problems)

---

## Introduction

Designing a **collaborative real-time editor** like Google Docs requires careful capacity planning to handle:
- **Millions of concurrent users** editing documents simultaneously
- **Sub-100ms latency** for real-time synchronization
- **Billions of operations** per day
- **Petabytes of document storage**

This guide uses **napkin math** and the **POWER technique** to estimate system requirements.

---

## The POWER Technique

**POWER** stands for:
- **P**rinciples: Establish key assumptions
- **O**rder of magnitude: Round to nearest power of 10
- **W**rite down: Document all calculations
- **E**stimate: Calculate step by step
- **R**ound: Final answer in clean numbers

### Example:
```
Problem: How many documents are created per day?
P: 100M daily active users, 20% create new documents
O: ~20M documents/day → 20 * 10^6
W: 100M * 0.2 = 20M
E: 20M documents/day
R: ~20 million documents/day
```

---

## Assumptions (Google Docs Scale)

### User Metrics
- **Total users**: 2 billion (similar to Google Workspace)
- **Daily Active Users (DAU)**: 100 million (5% of total)
- **Peak concurrent users**: 10 million (10% of DAU)
- **Documents per user**: 50 documents on average
- **Active documents**: 20% of documents edited daily

### Usage Patterns
- **Average session duration**: 30 minutes
- **Operations per minute (typing)**: 60 operations/min (1 op/sec)
- **Concurrent editors per document**: 3 users on average (max 100)
- **Document size**: 50 KB average, 10 MB max
- **Document growth**: 1000 documents created per second during peak

### Document Characteristics
- **Average document length**: 5,000 characters (~5 pages)
- **Operation types**: 70% insert, 20% delete, 10% format
- **Retention**: Infinite (documents never deleted unless user requests)
- **Version history**: 30 days of full history, snapshots every 100 operations

---

## Traffic Estimation

### Read vs Write Ratio
- **Writes (edit operations)**: 80% of traffic
- **Reads (view/load documents)**: 20% of traffic

### Operations Per Second (OPS)

**Peak concurrent editors**:
```
10M concurrent users × 60 ops/min = 600M ops/min
= 10M ops/sec (peak)
```

**Average OPS**:
```
Assume peak is 3× average
Average OPS = 10M / 3 = 3.3M ops/sec
```

**Daily operations**:
```
3.3M ops/sec × 86,400 seconds/day = 285 billion operations/day
```

### API Requests

**Document loads** (read operations):
```
100M DAU × 10 document loads/day = 1B document loads/day
= 1B / 86,400 = ~12K loads/sec (average)
= 36K loads/sec (peak, assuming 3× average)
```

**Document saves** (snapshot creation):
```
1 snapshot per 100 operations
285B operations/day ÷ 100 = 2.85B snapshots/day
= 33K saves/sec (average)
```

**Total API QPS**:
```
Document loads: 36K QPS (peak)
Operations: 10M QPS (peak)
Snapshots: 100K QPS (peak)
----------------------------
Total: ~10.1M QPS (peak)
```

### Breakdown by Service

| Service | QPS (Peak) | Percentage |
|---------|------------|------------|
| WebSocket Operations | 10M | 99% |
| Document Load API | 36K | 0.35% |
| Snapshot Creation | 100K | 0.99% |
| Search API | 10K | 0.1% |
| Permissions API | 5K | 0.05% |

---

## Storage Estimation

### Operations Log Storage

**Per operation size**:
```json
{
  "id": "op-1234567890",          // 20 bytes
  "docId": "doc-uuid",            // 16 bytes (UUID)
  "userId": "user-uuid",          // 16 bytes
  "type": "insert",               // 10 bytes
  "position": 1234,               // 8 bytes
  "text": "Hello",                // variable (avg 10 bytes)
  "timestamp": 1700000000,        // 8 bytes
  "version": 156                  // 8 bytes
}
Total: ~100 bytes per operation (with JSON overhead)
```

**Daily operations storage**:
```
285B operations/day × 100 bytes = 28.5 TB/day
```

**30-day retention**:
```
28.5 TB/day × 30 days = 855 TB ≈ 850 TB
```

With compression (3:1 ratio):
```
850 TB / 3 ≈ 280 TB (compressed)
```

### Document Storage

**Total documents**:
```
2B users × 50 documents/user = 100 billion documents
```

**Average document size**: 50 KB

**Total storage**:
```
100B documents × 50 KB = 5 PB (petabytes)
```

### Snapshots Storage

**Snapshot frequency**: Every 100 operations

**Snapshots per day**:
```
285B operations/day ÷ 100 = 2.85B snapshots/day
```

**Snapshot size**: 50 KB average (compressed)

**Daily snapshot storage**:
```
2.85B snapshots × 50 KB = 142.5 TB/day
```

**30-day retention**:
```
142.5 TB/day × 30 days = 4,275 TB ≈ 4.3 PB
```

### Media Assets (Images, Videos)

**Assumption**: 10% of documents contain media

**Average media per document**: 500 KB

**Total media storage**:
```
100B documents × 10% × 500 KB = 5 PB
```

### Total Storage Summary

| Data Type | Storage | Retention |
|-----------|---------|-----------|
| **Operations Log** | 280 TB | 30 days |
| **Documents** | 5 PB | Infinite |
| **Snapshots** | 4.3 PB | 30 days |
| **Media Assets** | 5 PB | Infinite |
| **Indexes (Elasticsearch)** | 500 TB | N/A |
| **Total** | **~15 PB** | Mixed |

---

## Bandwidth Estimation

### Inbound Bandwidth (Operations from Clients)

**Peak operations**: 10M ops/sec

**Per operation payload**: 150 bytes (including WebSocket framing)

**Inbound bandwidth**:
```
10M ops/sec × 150 bytes = 1.5 GB/sec = 12 Gbps
```

### Outbound Bandwidth (Broadcast to Clients)

**Assumption**: Each operation broadcast to 3 users on average

**Outbound operations**:
```
10M ops/sec × 3 users = 30M ops/sec
```

**Outbound bandwidth**:
```
30M ops/sec × 150 bytes = 4.5 GB/sec = 36 Gbps
```

### Document Loads

**Peak document loads**: 36K/sec

**Average document size**: 50 KB

**Document load bandwidth**:
```
36K/sec × 50 KB = 1.8 GB/sec = 14.4 Gbps
```

### Presence Updates (Cursors)

**Assumption**: Cursor updates throttled to 10/sec per user

**Active users**: 10M concurrent

**Cursor updates**:
```
10M users × 10 updates/sec / 3 (avg users per doc) = 33M updates/sec
```

**Cursor payload**: 30 bytes

**Cursor bandwidth**:
```
33M × 30 bytes = 990 MB/sec ≈ 1 GB/sec = 8 Gbps
```

### Total Bandwidth

| Type | Bandwidth (Peak) |
|------|------------------|
| **Inbound Operations** | 12 Gbps |
| **Outbound Operations** | 36 Gbps |
| **Document Loads** | 14.4 Gbps |
| **Presence/Cursors** | 8 Gbps |
| **Total** | **~70 Gbps** |

**Note**: This is aggregated across all data centers. For multi-region deployment (5 regions), each region handles ~14 Gbps.

---

## Infrastructure Sizing

### WebSocket Servers

**Connections per server**: 50,000 concurrent connections (Node.js/Go)

**Peak concurrent users**: 10 million

**Required servers**:
```
10M connections ÷ 50K per server = 200 WebSocket servers
```

**With 50% headroom for failover**:
```
200 × 1.5 = 300 WebSocket servers
```

**Server specs**:
- 16 vCPU
- 32 GB RAM
- 10 Gbps network

**Cost** (AWS c5.4xlarge at $0.68/hr):
```
300 servers × $0.68/hr × 730 hrs/month = $139,320/month
```

### Collaboration Service (OT/CRDT Engine)

**Operations per second**: 10M ops/sec

**OPS per server**: 100K ops/sec (stateless, CPU-intensive)

**Required servers**:
```
10M ÷ 100K = 100 servers
```

**With headroom**:
```
100 × 1.5 = 150 servers
```

**Server specs**:
- 32 vCPU (high CPU for transformation algorithms)
- 64 GB RAM
- SSD storage

**Cost** (AWS c5.9xlarge at $1.53/hr):
```
150 servers × $1.53/hr × 730 hrs/month = $167,895/month
```

### Database (PostgreSQL)

**Operations log writes**: 10M ops/sec

**Assuming sharding across 100 shards**:
```
Each shard handles: 10M ÷ 100 = 100K ops/sec
```

**PostgreSQL can handle ~10K writes/sec per instance**

**Instances per shard** (with replication):
```
1 master + 2 read replicas = 3 instances per shard
Total: 100 shards × 3 = 300 PostgreSQL instances
```

**Instance specs**:
- db.r5.4xlarge (16 vCPU, 128 GB RAM)
- 10 TB SSD storage per instance

**Cost** (AWS RDS):
```
300 instances × $2.72/hr × 730 hrs/month = $595,680/month
```

### Redis Cache

**Cached data**:
- Document snapshots: 20% of active docs cached (~5 TB)
- Presence data: 100 MB
- Operation buffer: 1000 ops × 100B × 10M docs = 1 TB

**Total cache**: ~6 TB

**Redis cluster** (6 nodes, 1 TB each):
```
6 nodes × cache.r6g.4xlarge ($1.01/hr) × 730 hrs/month = $4,424/month
```

### Object Storage (S3)

**Total storage**: 15 PB

**S3 Standard**: First 50 TB = $0.023/GB

**Simplified cost** (mix of Standard, IA, Glacier):
```
15 PB = 15,000 TB
Average cost: $0.015/GB/month
15M GB × $0.015 = $225,000/month
```

### Elasticsearch (Search)

**Indexed documents**: 100B documents

**Index size**: 500 TB (compressed)

**Elasticsearch cluster**:
- 50 nodes × i3.4xlarge (16 vCPU, 122 GB RAM, 2×1.9TB NVMe)
- Cost: 50 nodes × $3.312/hr × 730 hrs/month = $120,888/month

### Load Balancers

**Application Load Balancers**:
- 10 ALBs (multi-region)
- Cost: ~$3,000/month

### Total Monthly Infrastructure Cost

| Component | Monthly Cost | Percentage |
|-----------|--------------|------------|
| **WebSocket Servers** | $139,320 | 11.3% |
| **Collaboration Service** | $167,895 | 13.6% |
| **PostgreSQL (Sharded)** | $595,680 | 48.3% |
| **Redis Cache** | $4,424 | 0.4% |
| **S3 Storage** | $225,000 | 18.2% |
| **Elasticsearch** | $120,888 | 9.8% |
| **Load Balancers** | $3,000 | 0.2% |
| **Bandwidth** | $50,000 | 4.0% |
| **Monitoring** | $10,000 | 0.8% |
| **Total** | **~$1.23M/month** | 100% |

**Annual cost**: ~$14.8 million

**Cost per DAU**: $1.23M / 100M DAU = **$0.0123/DAU/month** (~1.2 cents per user per month)

---

## Caching Strategy

### Document State Cache (Redis)

**What to cache**:
- Latest document snapshot (hot documents)
- Operation buffer (last 1000 ops)
- Presence data (active users, cursors)

**Cache key structure**:
```
doc:{docId}:snapshot → Document JSON (50 KB)
doc:{docId}:ops → List of last 1000 operations
doc:{docId}:presence → Hash of active users
doc:{docId}:version → Current version number
```

**TTL strategy**:
- Document snapshots: 1 hour (refresh on access)
- Presence data: 30 seconds
- Operation buffer: 24 hours

**Cache hit rate target**: 85% (reduces database load significantly)

**Cache eviction**: LRU (Least Recently Used)

### CDN Caching (CloudFront)

**What to cache**:
- Static assets (JS bundles, CSS, fonts)
- Exported documents (PDF, DOCX) for 24 hours
- Media assets (images, videos embedded in docs)

**Cache hit rate**: 95% for static assets

---

## Database Sharding

### Sharding Strategy

**Shard by**: `document_id` (consistent hashing)

**Why document_id**?
- Most queries are document-scoped
- Even distribution of load
- Easy to scale horizontally

**Number of shards**: 100 shards initially (can grow to 1000)

**Shard key calculation**:
```python
import hashlib

def get_shard(doc_id, num_shards=100):
    hash_value = int(hashlib.md5(doc_id.encode()).hexdigest(), 16)
    return hash_value % num_shards
```

### Shard Sizing

**Total operations**: 285B operations/day

**Per shard**:
```
285B / 100 shards = 2.85B operations/shard/day
= 33K ops/sec/shard (average)
= 100K ops/sec/shard (peak)
```

**Storage per shard**:
```
280 TB (ops log) / 100 = 2.8 TB per shard
```

### Replication

**Master-Slave replication**:
- 1 master (writes)
- 2 read replicas (reads)
- Asynchronous replication (acceptable for operation log)

**Failover**:
- Auto-promote replica to master (30s RTO)
- Update shard routing in application config

---

## Practice Problems

### Problem 1: Document Creation Rate

**Question**: If 100M DAU users create an average of 2 new documents per week, how many documents are created per second at peak?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Documents per week = 100M × 2 = 200M documents/week

Documents per day (average) = 200M / 7 = 28.6M documents/day

Documents per second (average) = 28.6M / 86,400 = 331 docs/sec

Assuming peak is 3× average:
Peak = 331 × 3 ≈ 1,000 documents/sec
```

**Answer**: ~1,000 documents/sec at peak
</details>

---

### Problem 2: Operation Bandwidth

**Question**: If each operation is 100 bytes and we need to broadcast to 5 users on average, what is the total outbound bandwidth for 5M ops/sec?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Operations per second = 5M
Broadcast multiplier = 5 users
Payload size = 100 bytes

Total outbound operations = 5M × 5 = 25M ops/sec

Bandwidth = 25M ops/sec × 100 bytes = 2.5 GB/sec

Converting to Gbps:
2.5 GB/sec × 8 bits/byte = 20 Gbps
```

**Answer**: 20 Gbps outbound bandwidth
</details>

---

### Problem 3: Snapshot Storage Growth

**Question**: If we create snapshots every 100 operations and each snapshot is 50 KB, how much storage is needed for 1 billion operations with 30-day retention?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Operations = 1 billion
Snapshot frequency = every 100 operations

Number of snapshots = 1B / 100 = 10 million snapshots

Storage per snapshot = 50 KB

Total storage = 10M × 50 KB = 500 GB

For 30 days:
Assuming daily operations are 1B:
30 days × 500 GB = 15,000 GB = 15 TB
```

**Answer**: 15 TB for 30-day retention
</details>

---

### Problem 4: WebSocket Server Capacity

**Question**: If each WebSocket server can handle 50,000 concurrent connections and you have 8M peak concurrent users, how many servers do you need with 40% headroom?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Peak concurrent users = 8M
Connections per server = 50K

Servers needed (minimum) = 8M / 50K = 160 servers

With 40% headroom:
160 × 1.4 = 224 servers
```

**Answer**: 224 WebSocket servers
</details>

---

### Problem 5: Cache Hit Rate Impact

**Question**: If your database can handle 100K reads/sec and you have 500K document load requests/sec, what cache hit rate do you need to avoid overloading the database?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Total requests = 500K/sec
Database capacity = 100K reads/sec

Cache must handle = 500K - 100K = 400K/sec

Cache hit rate needed = 400K / 500K = 0.8 = 80%
```

**Answer**: Minimum 80% cache hit rate required
</details>

---

### Problem 6: Database Sharding

**Question**: You have 200B operations stored, each 100 bytes. How many database shards do you need if each shard can hold 2 TB?

<details>
<summary>Show Solution</summary>

**Solution**:
```
Total operations = 200 billion
Size per operation = 100 bytes

Total storage = 200B × 100 bytes = 20 TB

Shard capacity = 2 TB

Number of shards = 20 TB / 2 TB = 10 shards (minimum)

With growth and headroom:
10 × 2 = 20 shards
```

**Answer**: 20 shards (with headroom)
</details>

---

### Problem 7: Presence Update Bandwidth

**Question**: 5M concurrent users, cursor updates 10 times/sec, average 3 users per document. Each cursor update is 30 bytes. Calculate the bandwidth.

<details>
<summary>Show Solution</summary>

**Solution**:
```
Concurrent users = 5M
Cursor updates per user = 10/sec
Users per document = 3

Total cursor updates = 5M × 10 = 50M updates/sec

Broadcast multiplier = 3 - 1 = 2 (don't broadcast to self)

Total broadcasts = 50M × 2 = 100M updates/sec

Payload size = 30 bytes

Bandwidth = 100M × 30 bytes = 3 GB/sec = 24 Gbps
```

**Answer**: 24 Gbps for presence updates
</details>

---

## Summary

### Key Takeaways

1. **Traffic Patterns**:
   - 10M ops/sec at peak (99% of traffic)
   - 36K document loads/sec
   - 70 Gbps total bandwidth

2. **Storage**:
   - 15 PB total storage (documents + snapshots + media)
   - 280 TB operations log (30-day retention)
   - 4.3 PB snapshots

3. **Infrastructure**:
   - 300 WebSocket servers
   - 150 Collaboration service instances
   - 100 database shards (300 PostgreSQL instances)
   - 6-node Redis cluster

4. **Cost**:
   - ~$1.23M/month for 100M DAU
   - ~$0.0123 per user per month
   - Database is 48% of total cost (largest component)

5. **Scaling Strategies**:
   - Horizontal scaling of stateless services
   - Database sharding by document_id
   - Multi-region deployment (5 regions)
   - 85% cache hit rate to reduce database load

### Optimization Opportunities

1. **Reduce Database Cost**:
   - Use cheaper storage tiers for old operations (S3)
   - Archive operations older than 30 days
   - More aggressive caching (target 90% hit rate)

2. **Reduce Bandwidth**:
   - Delta compression for operations
   - Batch operations (send every 100ms instead of real-time)
   - Use binary protocol instead of JSON

3. **Improve Latency**:
   - Edge caching for static assets
   - Regional WebSocket servers (reduce round-trip time)
   - In-memory operation buffer (Redis)

---

**Next Steps**: Dive into [collaborative-editor.md](./collaborative-editor.md) for implementation details, or check [tech-stack.md](./tech-stack.md) for industry solutions!
