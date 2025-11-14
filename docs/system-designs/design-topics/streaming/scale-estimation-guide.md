# Video Streaming Scale Estimation Guide

Comprehensive capacity planning and scale calculations for Netflix/YouTube-scale video streaming platforms.

---

## Table of Contents
1. [Overview & Assumptions](#overview--assumptions)
2. [User & Traffic Estimation](#user--traffic-estimation)
3. [Storage Calculations](#storage-calculations)
4. [Bandwidth & CDN Requirements](#bandwidth--cdn-requirements)
5. [Transcoding Capacity](#transcoding-capacity)
6. [Database & Metadata Storage](#database--metadata-storage)
7. [Infrastructure Sizing](#infrastructure-sizing)
8. [Cost Analysis](#cost-analysis)
9. [Real-World Examples](#real-world-examples)

---

## Overview & Assumptions

### Platform Profile: Netflix-Scale Service

| Metric | Value |
|--------|-------|
| **Total Users** | 300 million |
| **Daily Active Users (DAU)** | 100 million (33% DAU/MAU ratio) |
| **Concurrent Peak Users** | 10 million (10% of DAU) |
| **Video Catalog** | 50,000 titles |
| **Average Video Length** | 45 minutes (mix of movies & TV episodes) |
| **New Content Upload Rate** | 500 videos/day |

### Platform Profile: YouTube-Scale Service

| Metric | Value |
|--------|-------|
| **Total Users** | 2.7 billion |
| **Daily Active Users (DAU)** | 1 billion |
| **Concurrent Peak Users** | 50 million (5% of DAU) |
| **Video Catalog** | 800 million videos |
| **Average Video Length** | 12 minutes (mix of short & long-form) |
| **New Content Upload Rate** | 500,000 videos/day (720,000 hours/day) |

**We'll use Netflix-scale for detailed calculations, with YouTube comparisons.**

---

## User & Traffic Estimation

### Daily Video Consumption

#### Netflix-Scale:
- **DAU:** 100 million
- **Average watch time per user:** 2 hours/day
- **Total daily watch hours:** 100M × 2h = **200 million hours/day**

#### YouTube-Scale:
- **DAU:** 1 billion
- **Average watch time per user:** 1 hour/day
- **Total daily watch hours:** 1B × 1h = **1 billion hours/day**

### Request Distribution (Netflix-Scale)

| Time Period | % of Daily Traffic | Concurrent Users | Watch Hours/Hour |
|-------------|-------------------|------------------|------------------|
| **Peak (8-11 PM)** | 40% | 10 million | 26.7 million |
| **Evening (5-8 PM)** | 25% | 6 million | 16.7 million |
| **Afternoon (12-5 PM)** | 20% | 4 million | 13.3 million |
| **Off-Peak (11 PM-12 PM)** | 15% | 2 million | 10 million |

**Peak concurrent users:** 10 million (worst-case scenario for capacity planning)

### QPS (Queries Per Second) Estimation

#### Video Playback Requests

```
Peak concurrent users: 10 million
Average session duration: 90 minutes
Session initiation rate = 10M / (90 min) = 10M / 5,400s ≈ 1,850 sessions/second

Each session:
- 1 playback initiation request
- ~3 API calls (auth, manifest, analytics setup)

Peak Playback QPS: 1,850 × 3 ≈ 5,500 QPS
```

#### Segment Requests (to CDN)

```
10 million concurrent viewers
Average video bitrate: 5 Mbps (1080p)
Segment duration: 6 seconds
Segment size: (5 Mbps × 6s) / 8 = 3.75 MB

Segment request rate = 10M users × (1 segment / 6 seconds)
                     = 10M / 6
                     ≈ 1.67 million requests/second to CDN
```

#### Metadata & Search Requests

```
DAU browsing: 100 million
Average API calls per user/day: 50 (browse, search, thumbnails)

Total API calls: 100M × 50 = 5 billion/day
QPS (uniform): 5B / 86,400 ≈ 58,000 QPS
Peak QPS (3× average): 174,000 QPS
```

**Summary QPS:**
- **Playback API:** 5,500 QPS (peak)
- **CDN Segment Requests:** 1.67 million RPS
- **Metadata/Search API:** 174,000 QPS (peak)

---

## Storage Calculations

### Raw Video Storage

#### Single Video Storage (Netflix):

```
Original 4K Source: 1 hour @ 50 Mbps
Size = (50 Mbps × 3,600s) / 8 = 22.5 GB per hour
```

#### Multi-Resolution Encoded Versions:

| Resolution | Bitrate | Storage (1 hour) |
|------------|---------|------------------|
| 4K (3840×2160) | 12 Mbps | 5.4 GB |
| 1080p (1920×1080) | 5 Mbps | 2.25 GB |
| 720p (1280×720) | 2.5 Mbps | 1.125 GB |
| 480p (854×480) | 1 Mbps | 450 MB |
| 360p (640×360) | 700 Kbps | 315 MB |
| 240p (426×240) | 400 Kbps | 180 MB |

**Total encoded storage per hour:** 5.4 + 2.25 + 1.125 + 0.45 + 0.315 + 0.18 = **9.72 GB**

For 45-minute average video: 9.72 × 0.75 = **7.29 GB per title**

### Total Catalog Storage (Netflix-Scale)

```
Total titles: 50,000
Average storage per title: 7.29 GB

Raw encoded storage: 50,000 × 7.29 GB = 364.5 TB

Add redundancy (3× replication or 1.5× erasure coding):
Physical storage: 364.5 TB × 1.5 = 546.75 TB ≈ 550 TB

Add original masters (for re-encoding):
Original storage: 50,000 × (22.5 GB × 0.75) = 843.75 TB

Total origin storage: 550 TB (encoded) + 844 TB (originals) = 1,394 TB ≈ 1.4 PB
```

### Total Catalog Storage (YouTube-Scale)

```
Total videos: 800 million
Average length: 12 minutes = 0.2 hours
Average storage per video: 9.72 GB × 0.2 = 1.944 GB

Raw encoded storage: 800M × 1.944 GB = 1,555,200 TB = 1,555 PB

With 1.5× erasure coding: 1,555 PB × 1.5 = 2,333 PB ≈ 2.3 Exabytes
```

**Netflix Origin Storage:** ~1.4 PB  
**YouTube Origin Storage:** ~2.3 Exabytes

### Daily Upload Storage Growth

#### Netflix-Scale:
```
Daily uploads: 500 videos
Average duration: 45 minutes
Daily growth: 500 × 7.29 GB = 3.645 TB/day raw
With redundancy: 3.645 TB × 1.5 = 5.47 TB/day ≈ 164 TB/month
```

#### YouTube-Scale:
```
Daily uploads: 720,000 hours = 500,000 videos
Average storage per video: 1.944 GB
Daily growth: 500,000 × 1.944 GB = 972 TB/day raw
With redundancy: 972 TB × 1.5 = 1,458 TB/day ≈ 43.7 PB/month
```

---

## Bandwidth & CDN Requirements

### Peak Bandwidth (Netflix-Scale)

```
Concurrent viewers: 10 million
Average bitrate: 5 Mbps (1080p dominant)

Total bandwidth: 10M × 5 Mbps = 50 million Mbps = 50 Tbps
```

### CDN Cache Hit Ratio Impact

| CDN Hit % | Origin Bandwidth | Edge Bandwidth |
|-----------|------------------|----------------|
| 95% | 2.5 Tbps | 47.5 Tbps |
| 98% | 1 Tbps | 49 Tbps |
| 99% | 500 Gbps | 49.5 Tbps |

**Target:** 98%+ cache hit ratio → **Origin serves <1 Tbps, CDN edges serve 49 Tbps**

### Bandwidth by Quality Distribution

Assuming viewer quality distribution:
- 20% watch 4K (12 Mbps): 2M × 12 = 24 Tbps
- 40% watch 1080p (5 Mbps): 4M × 5 = 20 Tbps
- 25% watch 720p (2.5 Mbps): 2.5M × 2.5 = 6.25 Tbps
- 15% watch SD (1 Mbps): 1.5M × 1 = 1.5 Tbps

**Total:** 51.75 Tbps (peak)

### Monthly Data Transfer

```
Daily watch hours: 200 million
Average bitrate: 5 Mbps

Daily data transfer: 200M hours × 5 Mbps × 3,600s / 8
                   = 200M × 2.25 GB
                   = 450,000 TB/day
                   = 450 PB/day

Monthly: 450 PB × 30 = 13,500 PB = 13.5 Exabytes/month
```

**Note:** CDN egress costs are a major expense (see cost section).

---

## Transcoding Capacity

### Daily Transcoding Requirements (Netflix-Scale)

```
Daily uploads: 500 videos
Average duration: 45 minutes

Total input hours: 500 × 0.75 = 375 hours/day
```

### Transcoding Time Per Video

Using high-end transcoding servers (c5.9xlarge: 36 vCPU):

| Resolution | Encoding Speed | Time for 45-min video |
|------------|----------------|----------------------|
| 4K | 0.5× real-time | 90 minutes |
| 1080p | 1× real-time | 45 minutes |
| 720p | 2× real-time | 22.5 minutes |
| 480p | 3× real-time | 15 minutes |
| 360p | 4× real-time | 11.25 minutes |
| 240p | 5× real-time | 9 minutes |

**Total encoding time per video (parallel):** ~90 minutes (limited by 4K encoding)

### Transcoding Fleet Sizing

```
Daily uploads: 500 videos
Encoding time per video: 90 minutes

Total encoding time needed: 500 × 90 min = 45,000 minutes = 750 hours

Desired processing window: 12 hours (to process daily batch)

Required instances: 750 hours / 12 hours = 62.5 ≈ 65 instances

Instance type: c5.9xlarge (36 vCPU, 72 GB RAM)
Cost per instance: $1.53/hour (on-demand)

Alternative: Use AWS MediaConvert or GPU instances (faster, but pricier)
```

**Transcoding fleet:** 65× c5.9xlarge instances (can use spot instances for 70% cost savings)

### YouTube-Scale Transcoding

```
Daily uploads: 720,000 hours of content
Encoding time (parallel): 1.5× input hours = 1,080,000 hours
Processing window: 24 hours

Required capacity: 1,080,000 / 24 = 45,000 instances (c5.9xlarge equivalent)

YouTube uses custom-built encoding infrastructure + TPUs
```

---

## Database & Metadata Storage

### Video Metadata (Cassandra/PostgreSQL)

**Schema per video:**
```sql
video_id (UUID): 16 bytes
title (VARCHAR 200): ~100 bytes
description (TEXT): ~500 bytes
uploader_id: 8 bytes
duration: 4 bytes
upload_date: 8 bytes
view_count: 8 bytes
like_count: 4 bytes
tags (array): ~100 bytes
thumbnail_urls: ~200 bytes
status, visibility, etc.: ~50 bytes

Total per video: ~1,000 bytes = 1 KB
```

**Total metadata storage:**
- **Netflix:** 50,000 videos × 1 KB = 50 MB (negligible)
- **YouTube:** 800M videos × 1 KB = 800 GB (manageable)

### Video Variants Table

Each video has 6 resolutions × 2 codecs (H.264, H.265) = 12 variants

```sql
Per variant: video_id (16) + quality (20) + codec (20) + bitrate (4) 
           + manifest_url (200) + storage_location (200) + file_size (8)
         = ~470 bytes

Per video: 12 variants × 470 bytes = 5.64 KB
```

**Total variants storage:**
- **Netflix:** 50,000 × 5.64 KB = 282 MB
- **YouTube:** 800M × 5.64 KB = 4.5 TB

### User Data (PostgreSQL - Sharded)

**Per user:**
```sql
user_id: 8 bytes
email: ~30 bytes
username: ~20 bytes
password_hash: 60 bytes
subscription_tier: 20 bytes
created_at, last_login: 16 bytes
profile_data (JSON): ~200 bytes

Total: ~354 bytes
```

**Total user data:**
- **Netflix:** 300M users × 354 bytes = 106 GB
- **YouTube:** 2.7B users × 354 bytes = 955 GB

### Watch History (Cassandra - Time-series)

**Per watch event:**
```sql
user_id (8) + video_id (16) + watched_at (8) + watch_duration (4) 
+ completion_rate (4) + device_type (20) = 60 bytes
```

**Netflix daily watch history:**
```
Daily sessions: 100M DAU × 4 videos = 400M events/day
Daily storage: 400M × 60 bytes = 24 GB/day
Monthly: 24 GB × 30 = 720 GB/month
Yearly: 720 GB × 12 = 8.64 TB/year

With TTL (retain 2 years): 8.64 TB × 2 = 17.3 TB
```

### Database Summary (Netflix-Scale)

| Database | Purpose | Size | Sharding Strategy |
|----------|---------|------|-------------------|
| **PostgreSQL** | Users, subscriptions | 106 GB | Shard by user_id (64 shards) |
| **Cassandra** | Video metadata, watch history | 18 TB | Partition by video_id, user_id |
| **Redis** | Cache, sessions | 500 GB | Cluster mode (16 nodes) |
| **Elasticsearch** | Search, logs | 2 TB | Distributed index (12 nodes) |

**Total database storage:** ~20.6 TB

---

## Infrastructure Sizing

### Application Servers (API Gateway + Microservices)

**Peak QPS:** 174,000 (metadata/search) + 5,500 (playback) = 179,500 QPS

Assuming each server handles 1,000 QPS:
```
Required servers: 179,500 / 1,000 = 180 servers

Instance type: c5.2xlarge (8 vCPU, 16 GB RAM)
Cost: $0.34/hour × 180 = $61.2/hour = $44,064/month

With auto-scaling (average 60% of peak): $26,438/month
```

### Database Cluster

#### PostgreSQL (Users, Subscriptions):
```
Data size: 106 GB
Shards: 64 (for horizontal scaling)

Instance per shard: db.r5.large (2 vCPU, 16 GB RAM, $0.24/hour)
Total instances: 64 × 3 (primary + 2 replicas) = 192 instances
Monthly cost: 192 × $0.24 × 730 = $33,638
```

#### Cassandra (Metadata, Watch History):
```
Data size: 18 TB
Replication factor: 3

Storage needed: 18 TB × 3 = 54 TB
Nodes: 54 TB / 1 TB per node = 54 nodes

Instance: i3.2xlarge (8 vCPU, 61 GB RAM, 1.9 TB NVMe SSD, $0.624/hour)
Monthly cost: 54 × $0.624 × 730 = $24,570
```

#### Redis Cache:
```
Cache size: 500 GB (hot data)
Instance: cache.r5.12xlarge (48 vCPU, 385 GB RAM, $3.36/hour)
Cluster: 2 nodes (primary + replica)
Monthly cost: 2 × $3.36 × 730 = $4,906
```

**Total database monthly cost:** $63,114

### CDN Costs (Largest Expense)

**Monthly data transfer:** 13.5 Exabytes

#### Scenario 1: Third-Party CDN (Cloudflare/Akamai)

```
Cloudflare pricing (approximate):
- $0.04/GB for first 500 TB
- $0.02/GB for 500 TB - 5 PB
- $0.01/GB for >5 PB

13.5 EB = 13,500 PB = 13,500,000 TB

Cost ≈ 13,500,000 TB × $0.01/GB × 1,000 GB/TB
     = $135,000,000/month (blended rate)
```

#### Scenario 2: Own CDN (Netflix Open Connect Model)

```
Netflix Open Connect: 17,000 servers globally
Server cost: ~$5,000 each (one-time)
Capital investment: 17,000 × $5,000 = $85 million

Colocation cost: ~$200/month per server
Monthly colocation: 17,000 × $200 = $3.4 million/month

Power, maintenance, bandwidth (ISP peering): ~$1.6 million/month

Total monthly (amortized CapEx over 5 years):
CapEx: $85M / 60 months = $1.42M/month
OpEx: $3.4M + $1.6M = $5M/month

Total: $6.42 million/month (vs $135M with third-party CDN)
```

**Own CDN saves ~95% vs third-party at Netflix scale.**

---

## Cost Analysis

### Monthly Cost Breakdown (Netflix-Scale)

| Component | Cost | % of Total |
|-----------|------|------------|
| **CDN (own infrastructure)** | $6,420,000 | 86.4% |
| **Storage (S3 + EBS)** | $350,000 | 4.7% |
| **Database cluster** | $63,114 | 0.85% |
| **Application servers** | $26,438 | 0.36% |
| **Transcoding (spot instances)** | $33,696 | 0.45% |
| **Load balancers, NAT gateways** | $15,000 | 0.2% |
| **Monitoring, logging** | $25,000 | 0.34% |
| **Other (DNS, SSL, etc.)** | $10,000 | 0.13% |
| **Engineering & support** | $500,000 | 6.7% |

**Total Monthly Cost:** ~$7.44 million  
**Annual Cost:** ~$89.3 million

### Cost Per User Metrics

```
Total cost: $7.44 million/month
Total users: 300 million
DAU: 100 million

Cost per total user: $7.44M / 300M = $0.0248/month = $0.30/year
Cost per DAU: $7.44M / 100M = $0.074/month = $0.89/year
Cost per watch hour: $7.44M / 200M hours = $0.037/hour
```

### Revenue Requirements

```
Monthly cost: $7.44 million
Target margin: 20%

Required monthly revenue: $7.44M / 0.8 = $9.3 million

Subscription price: $15/month
Required paying users: $9.3M / $15 = 620,000 users

% of total users needed: 620K / 300M = 0.2% (very achievable)
% of DAU needed: 620K / 100M = 0.62%
```

**Break-even at <1% conversion rate makes streaming viable.**

---

## Real-World Examples

### Netflix (2024 Data)

| Metric | Value |
|--------|-------|
| **Subscribers** | 260 million (paid) |
| **Daily Watch Hours** | ~600 million |
| **Content Library** | 15,000+ titles |
| **Peak Traffic** | 15%+ of global internet traffic |
| **CDN** | Open Connect (17,000+ servers) |
| **Monthly Revenue** | ~$3.5 billion |
| **Monthly Costs** | ~$2.5 billion (content + ops) |
| **Infrastructure Cost** | ~$100-150 million/month |

### YouTube (2024 Data)

| Metric | Value |
|--------|-------|
| **Monthly Active Users** | 2.7 billion |
| **Daily Watch Hours** | 1+ billion |
| **Videos Uploaded Daily** | 720,000 hours |
| **Total Video Count** | 800+ million |
| **CDN** | Google Global Cache (GGC) |
| **Annual Revenue** | ~$30 billion (ads) |
| **Infrastructure** | Google's global datacenter network |

### Twitch (Live Streaming)

| Metric | Value |
|--------|-------|
| **Daily Active Users** | 30 million |
| **Concurrent Viewers (Peak)** | 6-8 million |
| **Average Bitrate** | 3-6 Mbps (1080p 60fps) |
| **Peak Bandwidth** | ~48 Tbps |
| **Latency** | 2-5 seconds (standard), <1s (low-latency) |

---

## Scaling Strategies

### Horizontal Scaling Approach

1. **Microservices:** Independently scale each service based on load
2. **Database Sharding:** Partition by user_id, video_id
3. **CDN Expansion:** Add edge locations in high-traffic regions
4. **Auto-scaling:** Dynamic instance count based on metrics

### Vertical Scaling Limits

- **Database:** Max out at 96 vCPU instances, then shard
- **Cache:** Use Redis Cluster for horizontal scaling
- **Encoding:** Prefer horizontal (more instances) over vertical (bigger instances)

### Geographic Distribution

| Region | User % | CDN PoPs | Edge Servers |
|--------|--------|----------|--------------|
| **North America** | 30% | 50 | 5,000 |
| **Europe** | 25% | 40 | 4,000 |
| **Asia Pacific** | 35% | 60 | 6,000 |
| **Latin America** | 7% | 10 | 1,500 |
| **Other** | 3% | 5 | 500 |

**Total:** 165 PoPs, 17,000 edge servers

---

## Key Takeaways

### Critical Scale Metrics (Netflix-Scale)

- **Peak Concurrent Users:** 10 million
- **Peak Bandwidth:** 50 Tbps (CDN edges)
- **Storage:** 1.4 PB (origin), growing 164 TB/month
- **Database:** 20.6 TB (metadata, users, history)
- **QPS:** 180,000 (APIs), 1.67M RPS (CDN segments)
- **Monthly Cost:** $7.4M (~$90M/year)

### Optimization Priorities

1. **CDN Efficiency:** Own CDN saves 95% vs third-party
2. **Storage Tiering:** Move old content to Glacier (10% of S3 cost)
3. **Encoding:** Use spot instances (70% savings vs on-demand)
4. **Database:** Aggressive caching reduces DB load by 90%
5. **Bandwidth:** Per-title encoding saves 30% bandwidth

### Growth Projections (5-Year)

```
Year 1: 300M users, 1.4 PB storage, $90M/year
Year 3: 500M users, 2.5 PB storage, $150M/year
Year 5: 800M users, 4.0 PB storage, $240M/year

Scaling is sub-linear due to:
- Content library grows slower than user base
- CDN efficiency improves with scale
- Database/cache hit ratios improve
```

---

**This guide provides comprehensive scale estimation for building Netflix/YouTube-scale video streaming platforms. Actual numbers vary based on content mix, geographic distribution, and business model.**
