# Live Streaming Scale Estimation Guide

Comprehensive capacity planning and scale calculations for Twitch/YouTube Live-scale platforms.

---

## Table of Contents
1. [Overview & Assumptions](#overview--assumptions)
2. [User & Traffic Estimation](#user--traffic-estimation)
3. [Bandwidth Requirements](#bandwidth-requirements)
4. [Chat System Scale](#chat-system-scale)
5. [Transcoding Capacity](#transcoding-capacity)
6. [Storage Requirements](#storage-requirements)
7. [Database & Metadata](#database--metadata)
8. [Infrastructure Sizing](#infrastructure-sizing)
9. [Cost Analysis](#cost-analysis)
10. [Real-World Examples](#real-world-examples)

---

## Overview & Assumptions

### Platform Profile: Twitch-Scale Service

| Metric | Value |
|--------|-------|
| **Monthly Active Streamers** | 8 million |
| **Daily Active Viewers** | 30 million |
| **Concurrent Viewers (Peak)** | 6-8 million |
| **Concurrent Live Channels** | 100,000+ |
| **Average Stream Duration** | 3 hours |
| **Chat Messages** | 15+ billion/day |

### Platform Profile: YouTube Live-Scale

| Metric | Value |
|--------|-------|
| **Monthly Active Streamers** | 2 million |
| **Daily Active Viewers** | 500 million (includes VOD) |
| **Concurrent Viewers (Peak)** | 50+ million |
| **Concurrent Live Streams** | 500,000+ |
| **Average Stream Duration** | 2 hours |

**We'll use Twitch-scale for detailed calculations, with YouTube comparisons.**

---

## User & Traffic Estimation

### Concurrent Viewers Distribution

#### Peak Hours (6-11 PM):

```
Total concurrent viewers: 6 million
Distribution by quality:
- Source (1080p60): 20% = 1.2M viewers @ 6 Mbps = 7.2 Tbps
- 720p60: 30% = 1.8M viewers @ 4.5 Mbps = 8.1 Tbps
- 720p: 25% = 1.5M viewers @ 3 Mbps = 4.5 Tbps
- 480p: 15% = 900K viewers @ 1.5 Mbps = 1.35 Tbps
- 360p: 8% = 480K viewers @ 800 Kbps = 384 Gbps
- 160p (audio): 2% = 120K viewers @ 64 Kbps = 7.7 Gbps

Total peak bandwidth: ~21.5 Tbps
```

### Concurrent Streamers

```
Peak concurrent streamers: 100,000
Average bitrate per streamer: 6 Mbps (1080p60 upload)

Ingest bandwidth: 100K × 6 Mbps = 600 Gbps
Transcoding output: 600 Gbps input → 3-4 Tbps output (6 quality variants)
```

### Daily Streaming Hours

```
Daily active streamers: 1 million
Average stream duration: 3 hours

Total streaming hours: 1M × 3h = 3 million hours/day
Total streaming minutes: 3M × 60 = 180 million minutes/day
```

---

## Bandwidth Requirements

### Ingest Bandwidth (From Streamers)

```
Peak concurrent streamers: 100,000
Average upload bitrate: 6 Mbps

Peak ingest: 100K × 6 Mbps = 600 Gbps = 0.6 Tbps
```

### Egress Bandwidth (To Viewers)

#### Peak (6-8 million concurrent viewers):

```
Quality Distribution:
- Source: 1.2M @ 6 Mbps = 7,200 Gbps
- 720p60: 1.8M @ 4.5 Mbps = 8,100 Gbps
- 720p: 1.5M @ 3 Mbps = 4,500 Gbps
- 480p: 900K @ 1.5 Mbps = 1,350 Gbps
- 360p: 480K @ 800 Kbps = 384 Gbps
- Audio: 120K @ 64 Kbps = 7.7 Gbps

Total: 21,541.7 Gbps ≈ 21.5 Tbps
```

#### Average (daily):

```
Average concurrent viewers: 3 million (50% of peak)
Average bandwidth: 21.5 Tbps × 0.5 = 10.75 Tbps
```

### Monthly Data Transfer

```
Daily average: 10.75 Tbps
Hours per day: 24

Daily data: 10.75 Tbps × 24h × 3600s / 8 bits/byte
           = 10,750 Gbps × 86,400s / 8
           = 11,610,000 GB
           = 11.6 PB/day

Monthly: 11.6 PB × 30 = 348 PB/month = 0.348 EB/month
```

**Note:** CDN cache hit ratio for live content is lower than VOD (~70-80% vs 95%+)

---

## Chat System Scale

### Message Volume

```
Daily active chatters: 10 million (subset of 30M daily viewers)
Average messages per user: 50/day

Daily messages: 10M × 50 = 500 million messages/day

Industry data (Twitch): 15+ billion messages/day
(Our estimate is conservative; includes bots, power users)

Using industry data:
Messages per second (average): 15B / 86,400 = 173,611 msg/s
Messages per second (peak 3× avg): 520,833 msg/s
```

### Chat Server Capacity

```
Single Elixir/Phoenix process handles: ~10,000 concurrent connections
Target: 100,000 concurrent channels

Connections per channel (average): 50 viewers
Total connections: 100K × 50 = 5 million

Required processes: 5M / 10K = 500 processes
With 20% overhead: 600 processes

Server sizing (64GB RAM, 32 vCPU):
Processes per server: 100
Required servers: 600 / 100 = 6 chat servers (minimum)
With 3× redundancy: 18 chat servers
```

### Chat Message Storage

```
Daily messages: 15 billion
Average message size: 100 bytes (username, timestamp, message, metadata)

Daily storage: 15B × 100 bytes = 1.5 TB/day
Monthly: 1.5 TB × 30 = 45 TB/month
Yearly: 45 TB × 12 = 540 TB/year

With retention (30 days for moderation):
Storage needed: 1.5 TB × 30 = 45 TB
```

---

## Transcoding Capacity

### Real-time Transcoding Requirements

```
Peak concurrent streamers: 100,000
Transcoding requirement: Real-time (1× speed)

Single transcoding instance (NVIDIA T4 GPU):
- Capacity: 4-6 concurrent 1080p60 → 6 quality streams
- Cost: $0.35/hour (on-demand)

Required GPUs: 100,000 / 5 = 20,000 GPU instances

Using AWS EC2 g4dn.xlarge (1× T4 GPU, 4 vCPU, 16GB RAM):
Cost per instance: $0.526/hour
Peak cost: 20,000 × $0.526 = $10,520/hour
Monthly (peak 6 hours/day): $10,520 × 6 × 30 = $1,893,600/month
Monthly (average load 50%): $946,800/month
```

**Optimization: Use spot instances (70% cheaper)**
```
Spot price: $0.158/hour
Monthly cost (average): $946,800 × 0.3 = $284,040/month
```

### Transcoding vs Source-Only

**Cost savings if offering only source quality (no transcoding):**
- Partners/affiliates only get transcoding
- Non-partners stream source only
- Reduces transcoding to 20% of streams: 20,000 streamers

```
GPU instances needed: 4,000
Monthly cost (spot): $284,040 × 0.2 = $56,808/month
Savings: $227,232/month
```

---

## Storage Requirements

### Live Stream Segments (Temporary)

```
Concurrent streams: 100,000
Segment duration: 6 seconds
Segment sizes by quality:
- Source (1080p60 @ 6 Mbps): 4.5 MB
- 720p60 @ 4.5 Mbps: 3.4 MB
- 720p @ 3 Mbps: 2.25 MB
- 480p @ 1.5 Mbps: 1.125 MB
- 360p @ 800 Kbps: 600 KB
- 160p @ 64 Kbps: 48 KB

Total per 6s segment: 11.9 MB

Segments retained (rolling 2 minutes for DVR):
Segments: 2 min / 6s = 20 segments
Storage per stream: 11.9 MB × 20 = 238 MB

Total live segment storage: 100K streams × 238 MB = 23.8 TB
```

### VOD Storage (Archives)

```
Daily streaming hours: 3 million hours
VOD retention: Partners (60 days), Affiliates (14 days), Non-partners (0 days)

Assuming 20% partners (60d), 30% affiliates (14d), 50% no VOD:
- Partners: 3M × 0.2 = 600K hours/day, retention 60 days
- Affiliates: 3M × 0.3 = 900K hours/day, retention 14 days

Storage per hour (1080p60 @ 6 Mbps):
6 Mbps × 3600s / 8 = 2.7 GB/hour

Partners: 600K hours/day × 2.7 GB × 60 days = 97,200 TB = 97.2 PB
Affiliates: 900K hours/day × 2.7 GB × 14 days = 34,020 TB = 34 PB

Total VOD storage: 97.2 PB + 34 PB = 131.2 PB
```

---

## Database & Metadata

### User Database (PostgreSQL)

```
Total users: 300 million
Daily active: 30 million

User record: ~500 bytes (id, username, email, settings, etc.)
Total: 300M × 500 bytes = 150 GB

Sharding: 64 shards (user_id % 64)
Per shard: 150 GB / 64 = 2.3 GB
```

### Channel Metadata (DynamoDB)

```
Total channels: 8 million
Active channels: 100,000 concurrent

Channel record: ~2 KB (stream metadata, settings, stats)
Total: 8M × 2 KB = 16 GB
```

### Live Stream State (Redis)

```
Concurrent streams: 100,000

Stream state per channel: ~10 KB (viewer count, health metrics, chat stats)
Total: 100K × 10 KB = 1 GB

Redis cluster: 3 nodes (primary + 2 replicas)
Memory per node: 2 GB (with overhead)
```

### Chat History (DynamoDB)

```
Daily messages: 15 billion
Message size: 100 bytes
Retention: 30 days

Storage: 15B × 100 bytes × 30 days = 45 TB

DynamoDB pricing: $0.25/GB/month
Cost: 45,000 GB × $0.25 = $11,250/month
```

---

## Infrastructure Sizing

### Ingest Servers

```
Peak ingest: 600 Gbps
Per server capacity: 10 Gbps (40 Gbps NIC, 25% utilization)

Required servers: 600 / 10 = 60 ingest servers
With redundancy (2×): 120 ingest servers

Geographic distribution (100 locations):
Servers per location: 120 / 100 = 1.2 ≈ 2 servers/location

Instance: c5n.4xlarge (16 vCPU, 42 GB RAM, 25 Gbps network)
Cost: $0.864/hour × 120 = $103.68/hour
Monthly: $103.68 × 730 = $75,686/month
```

### Transcoding Fleet

```
(Covered in Transcoding section)
GPU instances: 20,000 (peak), 10,000 (average)
Monthly cost (spot): $284,040/month
```

### Chat Servers

```
(Covered in Chat section)
Servers: 18 (with redundancy)
Instance: c5.4xlarge (16 vCPU, 32 GB RAM)
Cost: $0.68/hour × 18 = $12.24/hour
Monthly: $12.24 × 730 = $8,935/month
```

### API/Metadata Servers

```
Peak QPS: 500,000 (viewer joins, metadata updates, analytics)
Per server capacity: 5,000 QPS

Required servers: 500,000 / 5,000 = 100 servers
With auto-scaling (average 40%): 40 servers

Instance: c5.2xlarge (8 vCPU, 16 GB RAM)
Cost: $0.34/hour × 40 = $13.6/hour
Monthly: $13.6 × 730 = $9,928/month
```

### Database Cluster

```
PostgreSQL (Users):
- Shards: 64
- Instance: db.r5.large (2 vCPU, 16 GB RAM)
- Replicas: 2 per shard
- Total instances: 64 × 3 = 192
- Cost: $0.24/hour × 192 = $46.08/hour
- Monthly: $46.08 × 730 = $33,638/month

Redis (Cache):
- Cluster: 3 nodes (16 GB RAM each)
- Instance: cache.r5.large
- Cost: $0.173/hour × 3 = $0.519/hour
- Monthly: $0.519 × 730 = $379/month

DynamoDB (Chat, Metadata):
- On-demand pricing
- Chat: $11,250/month (calculated above)
- Metadata: ~$500/month
- Total: $11,750/month
```

---

## Cost Analysis

### Monthly Cost Breakdown (Twitch-Scale)

| Component | Cost | % of Total |
|-----------|------|------------|
| **CDN (bandwidth)** | $8,700,000 | 88.5% |
| **Transcoding (GPU instances)** | $284,040 | 2.9% |
| **Ingest servers** | $75,686 | 0.8% |
| **VOD storage (S3)** | $3,017,600 | 30.7% |
| **Database cluster** | $45,767 | 0.5% |
| **API/metadata servers** | $9,928 | 0.1% |
| **Chat servers** | $8,935 | 0.1% |
| **Load balancers, monitoring** | $25,000 | 0.25% |
| **Other** | $20,000 | 0.2% |

**Total Monthly Cost:** ~$12.2 million  
**Annual Cost:** ~$146 million

**Note:** CDN is by far the largest expense (88.5% of infrastructure cost)

### CDN Cost Calculation

```
Monthly data transfer: 348 PB

CloudFront pricing (blended for 348 PB):
~$0.025/GB

Cost: 348,000 TB × $0.025 × 1000 = $8,700,000/month
```

**Own CDN savings (Netflix model):**
```
Own infrastructure: ~$1.5M/month (17% of CloudFront cost)
Savings: $7.2M/month (83% reduction)
```

### VOD Storage Cost (S3)

```
Storage: 131.2 PB
S3 Standard: $0.023/GB/month

Cost: 131,200 TB × $0.023 × 1000 = $3,017,600/month
```

**Storage tiering optimization:**
```
- Recent VODs (7 days): S3 Standard: 20% of data
- Older VODs (7-60 days): S3 IA: 80% of data

Cost:
- Standard: 131.2 PB × 0.2 × $0.023 = $603,520
- IA: 131.2 PB × 0.8 × $0.0125 = $1,312,000
Total: $1,915,520/month

Savings: $1,102,080/month (37%)
```

### Cost Per User Metrics

```
Total monthly cost: $12.2M
Daily active users: 30M

Cost per DAU: $12.2M / 30M = $0.407/month = $4.88/year
Cost per concurrent viewer (peak): $12.2M / 6M = $2.03/month
```

### Revenue Requirements

```
Monthly cost: $12.2M
Target margin: 30%

Required monthly revenue: $12.2M / 0.7 = $17.4M

Revenue sources:
1. Subscriptions:
   - 500K Tier 1 subs @ $4.99 × 50% split = $1.25M
   - 200K Tier 2/3 subs avg $12 × 50% = $1.2M
   - Total: $2.45M

2. Bits:
   - 1 billion bits/month × $0.01 = $10M

3. Ads:
   - 30M DAU, 10 ad views/day, $3 CPM
   - 30M × 10 × 30 days = 9B impressions
   - 9B / 1000 × $3 = $27M (gross)
   - Platform share (30%): $8.1M

Total revenue: $2.45M + $10M + $8.1M = $20.55M/month
Profit: $20.55M - $12.2M = $8.35M/month (41% margin)
```

---

## Real-World Examples

### Twitch (Amazon, 2024)

| Metric | Value |
|--------|-------|
| **Monthly Active Users** | 140+ million |
| **Daily Active Users** | 30+ million |
| **Concurrent Viewers (Peak)** | 6-8 million |
| **Active Streamers** | 8+ million/month |
| **Chat Messages** | 15+ billion/day |
| **Peak Bandwidth** | ~50 Tbps (estimated) |
| **Infrastructure** | AWS (CloudFront, ECS, DynamoDB, Kinesis) |
| **Annual Revenue** | $2.8 billion (2023) |

### YouTube Live (Google, 2024)

| Metric | Value |
|--------|-------|
| **Monthly Active Users** | 2.7 billion (total YouTube) |
| **Live Streamers** | 2+ million/month |
| **Concurrent Viewers (Peak)** | 50+ million (major events) |
| **Infrastructure** | Google Global Cache, Bigtable, Spanner |
| **Largest Event** | 8 million concurrent (World Cup) |

### Facebook Gaming (Meta, 2024)

| Metric | Value |
|--------|-------|
| **Monthly Active Streamers** | 1+ million |
| **Watch Hours** | 1.3 billion/quarter |
| **Concurrent Viewers (Peak)** | 3-4 million |
| **Infrastructure** | Meta datacenters, Haystack, TAO |

---

## Scaling Strategies

### Horizontal Scaling

```
Component-wise scaling:

1. Ingest Servers:
   - Add servers per geographic location
   - Auto-scale based on streamer count
   - Target: <100ms RTT for all streamers

2. Transcoding:
   - Auto-scaling GPU instances (spot)
   - Priority queue (partners first)
   - Graceful degradation (source-only fallback)

3. CDN:
   - Add edge PoPs in high-traffic regions
   - Own CDN for cost savings at scale
   - Multi-CDN for redundancy

4. Chat:
   - Shard by channel
   - Redis Cluster for message fanout
   - Elixir/Phoenix for horizontal scaling

5. Databases:
   - PostgreSQL sharding (user_id % N)
   - DynamoDB auto-scaling
   - Redis Cluster for cache
```

### Geographic Distribution

| Region | User % | Ingest PoPs | CDN Edges | Chat Servers |
|--------|--------|-------------|-----------|--------------|
| **North America** | 40% | 30 | 80 | 8 |
| **Europe** | 30% | 25 | 60 | 6 |
| **Asia** | 20% | 25 | 50 | 4 |
| **Latin America** | 7% | 10 | 20 | 2 |
| **Other** | 3% | 10 | 15 | 1 |
| **Total** | 100% | 100 | 225 | 21 |

---

## Key Takeaways

### Critical Scale Metrics (Twitch-Scale)

- **Concurrent viewers (peak):** 6-8 million
- **Peak bandwidth:** 21.5 Tbps (egress), 0.6 Tbps (ingest)
- **Chat messages:** 15+ billion/day, 520K msg/s peak
- **Transcoding:** 20,000 GPU instances (peak)
- **VOD storage:** 131 PB (60-day retention)
- **Monthly cost:** $12.2M (~$146M/year)

### Cost Optimization Priorities

1. **Own CDN:** 83% savings vs CloudFront ($7.2M/month)
2. **Spot Instances:** 70% savings on transcoding ($663K/month)
3. **Storage Tiering:** 37% savings on S3 ($1.1M/month)
4. **Source-Only Fallback:** 80% savings on transcoding for non-partners
5. **Cache Optimization:** Increase live segment cache hit ratio

### Growth Projections (5-Year)

```
Year 1: 30M DAU, 348 PB/month, $146M/year cost
Year 3: 50M DAU, 580 PB/month, $243M/year cost
Year 5: 80M DAU, 928 PB/month, $389M/year cost

Revenue grows faster than costs (network effects):
Year 1: $246M revenue, 68% margin
Year 3: $487M revenue, 75% margin
Year 5: $853M revenue, 78% margin
```

---

**This guide provides comprehensive scale estimation for building Twitch/YouTube Live-scale streaming platforms.**
