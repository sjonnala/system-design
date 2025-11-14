# URL Shortener - Scale Estimation Guide

## Overview

This guide provides back-of-the-envelope calculations for designing a URL shortener at scale, similar to Bitly or TinyURL.

---

## Step 1: Requirements & Assumptions

### Functional Requirements
- Create short URLs from long URLs
- Redirect short URLs to original URLs
- Track basic analytics (click counts)
- Optional: Custom short URLs
- Optional: Expiration

### Non-Functional Requirements
- **Availability:** 99.9% (3 nines)
- **Latency:** < 100ms for redirects
- **Durability:** URLs should never be lost
- **Scale:** Handle millions of users

### Traffic Assumptions
- **New URLs per month:** 100 million
- **Read:Write Ratio:** 100:1 (typical for URL shorteners)
- **Redirects per month:** 10 billion

---

## Step 2: Traffic Estimates

### Write Traffic (URL Creation)

```
New URLs per month: 100M
New URLs per day:   100M / 30 = 3.3M
New URLs per hour:  3.3M / 24 = 138K
New URLs per second: 138K / 3600 = ~40 writes/sec

Peak traffic (2x average): ~80 writes/sec
```

**Key Metric:** 40-80 writes/second

### Read Traffic (Redirects)

```
Redirects per month:  10B
Redirects per day:    10B / 30 = 333M
Redirects per hour:   333M / 24 = 14M
Redirects per second: 14M / 3600 = ~4,000 reads/sec

Peak traffic (2x average): ~8,000 reads/sec
```

**Key Metric:** 4,000-8,000 reads/second

### Total Queries Per Second (QPS)

```
Average QPS: 40 writes + 4,000 reads = 4,040 QPS
Peak QPS:    80 writes + 8,000 reads = 8,080 QPS
```

---

## Step 3: Storage Estimates

### URLs Storage

**Per URL Storage:**
```
short_code:    7 bytes (7 characters)
long_url:      ~100 bytes (average URL length)
user_id:       8 bytes (BIGINT)
created_at:    8 bytes (TIMESTAMP)
expires_at:    8 bytes (TIMESTAMP, nullable)
click_count:   4 bytes (INTEGER)
metadata:      ~15 bytes (indexes, overhead)
-------------------------------------------
Total per URL: ~150 bytes (conservative: 500 bytes with overhead)
```

**Total URLs Storage:**
```
Monthly new URLs: 100M
Annual new URLs:  100M × 12 = 1.2B
5-year storage:   1.2B × 5 = 6B URLs

Total storage: 6B URLs × 500 bytes = 3TB
```

**Storage Growth Rate:** ~200GB per month

### Analytics Storage

**Per Click Event:**
```
short_code:    7 bytes
clicked_at:    8 bytes (TIMESTAMP)
ip_address:    16 bytes (IPv6)
user_agent:    ~100 bytes
referrer:      ~50 bytes
-------------------------------------------
Total per event: ~200 bytes
```

**Total Analytics Storage:**
```
Monthly clicks: 10B
Annual clicks:  10B × 12 = 120B

Storage per year: 120B × 200 bytes = 24TB
5-year storage:   24TB × 5 = 120TB
```

**With Compression:** ~60TB (50% compression)

### Total Storage

```
URLs:       3TB
Analytics:  60TB (compressed)
Backups:    63TB (1x)
Total:      ~126TB

Add 50% buffer: ~190TB
```

---

## Step 4: Bandwidth Estimates

### Incoming Bandwidth (Writes)

```
Write requests: 40 req/sec
Request size:   ~1KB (JSON payload with long URL)
Incoming bandwidth: 40 × 1KB = 40KB/sec = ~0.32Mbps
```

**Negligible** - not a concern

### Outgoing Bandwidth (Reads)

```
Redirect requests: 4,000 req/sec
Response size:     ~500 bytes (headers + redirect)
Outgoing bandwidth: 4,000 × 500 bytes = 2MB/sec = ~16Mbps
```

**Peak:** ~32Mbps

---

## Step 5: Memory/Cache Estimates

### Caching Strategy

**80/20 Rule:** 20% of URLs generate 80% of traffic

```
Daily redirects: 333M
Unique URLs getting 80% traffic: 333M × 0.8 / 5 (avg clicks) = ~53M URLs
20% of all URLs: Let's cache hot URLs
```

**Cache Size Calculation:**

```
Hot URLs to cache: 20M URLs (most popular)
Per URL in cache:  7 bytes (key: short_code) + 100 bytes (value: long_url) = ~150 bytes
Total cache size:  20M × 150 bytes = 3GB

With Redis overhead (2x): ~6GB
Add buffer (50%): ~10GB
```

**Recommended Cache Size:** 10-20GB Redis cluster

### Cache Hit Ratio Target

```
With good caching strategy: 90% cache hit ratio
Cache hits:  3,600 req/sec (from cache)
Cache miss:  400 req/sec (from database)
```

---

## Step 6: Database Estimates

### Database Sizing

**Primary Database (Writes):**
```
Write QPS: 40-80 writes/sec
Database: PostgreSQL on m5.xlarge
- 4 vCPUs, 16GB RAM
- Can handle 1,000+ writes/sec
- Plenty of headroom
```

**Read Replicas (Reads):**
```
Read QPS with 90% cache hit: 400 reads/sec
Single replica can handle: ~5,000 reads/sec

Number of replicas needed: 1 (with headroom)
Recommended: 3 replicas (for availability and load distribution)
```

### Database Connections

```
App servers: 10 instances
Connections per instance: 20
Total connections: 200

With PgBouncer (connection pooling):
- Max connections to DB: 50
- Efficient connection reuse
```

### Database Index Size

```
Primary key index: 6B URLs × 8 bytes = 48GB
Short code index:  6B URLs × 7 bytes = 42GB
User ID index:     6B URLs × 8 bytes = 48GB
Created_at index:  6B URLs × 8 bytes = 48GB
Total indexes:     ~186GB

Fits in memory of db.r5.2xlarge (64GB RAM) with some spillover to disk
```

---

## Step 7: Short Code Length Calculation

### Base62 Encoding

**Character Set:** [a-z, A-Z, 0-9] = 62 characters

**Possible Combinations:**
```
6 characters: 62^6 = 56.8 billion combinations
7 characters: 62^7 = 3.5 trillion combinations
8 characters: 62^8 = 218 trillion combinations
```

**Our Requirement:** 6 billion URLs over 5 years

**Chosen Length:** 7 characters
- Provides 3.5 trillion combinations
- Plenty of room for growth (500x current needs)
- Short enough for user experience

---

## Step 8: Server Capacity Planning

### Application Servers

**Single Server Capacity:**
```
Assuming Node.js/Python on m5.large (2 vCPU, 8GB RAM):
- Can handle ~500 req/sec
- With caching: can handle ~1,000 req/sec
```

**Servers Needed:**
```
Peak QPS: 8,000
Server capacity: 1,000 req/sec
Servers needed: 8,000 / 1,000 = 8 servers

With 50% buffer: 12 servers
Auto-scaling range: 4-20 servers
```

### Load Balancer Capacity

```
AWS Application Load Balancer:
- Can handle 100,000 requests/sec per AZ
- Our 8,000 req/sec is well within limits
- Deploy across 3 AZs for redundancy
```

---

## Step 9: Cost Estimates (AWS)

### Compute (Application Servers)

```
Instance type: t3.large (2 vCPU, 8GB RAM)
Count: 10 instances (average)
Cost: $0.0832/hour × 10 × 730 hours = $607/month

Auto-scaling (average 8, peak 15):
Average cost: ~$500/month
```

### Database

```
Primary: db.m5.xlarge (4 vCPU, 16GB RAM)
Cost: $0.372/hour × 730 hours = $272/month

Read Replicas: 3 × db.m5.large
Cost: 3 × $0.186/hour × 730 hours = $407/month

Total database: ~$680/month
```

### Cache (Redis)

```
ElastiCache Redis: cache.r5.large (2 vCPU, 13.5GB RAM)
Cost: $0.238/hour × 730 hours = $174/month

With cluster mode (3 nodes): ~$520/month
```

### Storage

```
RDS Storage: 5TB × $0.115/GB = $575/month
S3 (analytics, backups): 100TB × $0.023/GB = $2,300/month
EBS (snapshots): ~$100/month

Total storage: ~$3,000/month
```

### Data Transfer

```
Outbound data transfer:
Peak bandwidth: 32Mbps = ~10TB/month
Cost: First 10TB: $0.09/GB = $900/month
```

### Load Balancer

```
AWS ALB: $0.0225/hour + $0.008/LCU
Estimated: ~$100/month
```

### Total Monthly Cost

```
Compute:         $500
Database:        $680
Cache:           $520
Storage:         $3,000
Data Transfer:   $900
Load Balancer:   $100
Misc (CDN, etc): $200
---------------------------
Total:           ~$5,900/month

With reserved instances (30% savings): ~$4,500/month
```

---

## Step 10: Scaling Milestones

### 10x Growth (1B URLs/month, 100B redirects/month)

**What Changes:**
```
Write QPS: 400-800 writes/sec
Read QPS: 40K-80K reads/sec
Storage: 30TB URLs + 600TB analytics
Cache: 50-100GB
Servers: 80-150 app servers
Database: Sharding required (10 shards)
Cost: ~$40K-50K/month
```

**Actions Required:**
- Implement database sharding (by short_code hash)
- Scale Redis to cluster mode (10+ nodes)
- Increase auto-scaling limits
- Move analytics to separate data warehouse (Redshift/BigQuery)
- Consider multi-region deployment

### 100x Growth (10B URLs/month, 1T redirects/month)

**What Changes:**
```
Write QPS: 4K-8K writes/sec
Read QPS: 400K-800K reads/sec
Storage: 300TB URLs + 6PB analytics
Cache: 500GB-1TB
Servers: 800-1,500 app servers
Database: 100 shards
Cost: ~$400K-500K/month
```

**Actions Required:**
- Multi-region deployment (3-5 regions)
- CDN for static content and API responses
- Separate analytics pipeline (Kafka + Spark)
- NoSQL migration (DynamoDB/Cassandra) for better scalability
- Dedicated analytics data warehouse
- Global load balancing

---

## Step 11: Capacity Planning Summary

### Current Scale (100M URLs/month)

| Component | Specification | Quantity | Cost/Month |
|-----------|--------------|----------|------------|
| App Servers | t3.large | 10 | $500 |
| Database (Primary) | db.m5.xlarge | 1 | $272 |
| Database (Replicas) | db.m5.large | 3 | $407 |
| Redis Cache | cache.r5.large | 3 | $520 |
| Storage (RDS) | gp3 SSD | 5TB | $575 |
| Storage (S3) | Standard | 100TB | $2,300 |
| Bandwidth | Outbound | 10TB | $900 |
| Load Balancer | ALB | 3 AZs | $100 |
| **Total** | | | **$5,574** |

### Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| Write Latency (p95) | < 50ms | ~20ms |
| Read Latency (p95) | < 100ms | ~3ms (cache hit) |
| Availability | 99.9% | 99.95% |
| Cache Hit Ratio | > 85% | 90% |
| Throughput | 8K QPS | 8K QPS (peak) |

---

## Key Formulas Reference

### QPS Calculation
```
QPS = (Monthly Requests) / (30 days × 24 hours × 3600 seconds)
Peak QPS = Average QPS × 2 (or use actual peak multiplier)
```

### Storage Calculation
```
Total Storage = (Number of Records) × (Bytes per Record)
With Overhead = Total Storage × 1.5
With Replication = Total Storage × Replication Factor
```

### Cache Size Calculation
```
Hot Data = Total Data × 0.2 (80/20 rule)
Cache Size = Hot Data × (Key Size + Value Size + Overhead)
Redis Overhead ≈ 2x raw data size
```

### Server Calculation
```
Servers Needed = Peak QPS / (Server Capacity per Second)
With Buffer = Servers Needed × 1.5
```

---

## Interview Tips

1. **Start with Assumptions:** Always clarify traffic numbers with interviewer
2. **Show Your Math:** Write out calculations, don't just state numbers
3. **Round Numbers:** Use 40 instead of 38.58 for simplicity
4. **Explain Ratios:** 100:1 read-write ratio is typical for URL shorteners
5. **Consider Growth:** Plan for 10x growth in your initial design
6. **Cost Awareness:** Mention costs show understanding of business constraints

## Common Interview Questions

**Q: "How did you arrive at 7 characters for short code?"**
```
A: "Using Base62 encoding (62 characters: a-z, A-Z, 0-9):
- 6 chars: 62^6 = 56B combinations
- 7 chars: 62^7 = 3.5T combinations
- We need 6B URLs over 5 years
- 7 characters gives us 500x headroom
- Short enough for good UX"
```

**Q: "Why 90% cache hit ratio?"**
```
A: "Based on 80/20 rule: 20% of URLs generate 80% of traffic.
With 20GB cache holding most popular URLs, we can cache enough
to serve 90% of requests. Remaining 10% are long-tail URLs
accessed infrequently."
```

**Q: "How do you handle 10x traffic growth?"**
```
A: "Current: 40 writes/sec, 4K reads/sec
10x: 400 writes/sec, 40K reads/sec

Actions:
1. Horizontal scale app servers (10 → 100)
2. Database sharding (1 → 10 shards)
3. Redis cluster mode (20GB → 200GB)
4. More read replicas (3 → 10)
5. Multi-region if global traffic
Estimated cost: ~$40K/month"
```

---

This estimation guide provides a solid foundation for discussing scale during system design interviews. Remember to adjust numbers based on specific requirements given by the interviewer!
