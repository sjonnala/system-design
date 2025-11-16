# 🎮 Battle Royale Game: Scale Estimation Masterclass

## Gaming-Specific Capacity Planning

This guide applies the POWER technique to real-time multiplayer gaming with a focus on **latency-sensitive, state-synchronization systems**.

---

## 📊 PART 1: Gaming Scale Fundamentals

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Player Base** | Total Registered Users | 50M | Medium-sized BR game |
| | Daily Active Users (DAU) | 10M | ~20% engagement (typical) |
| | Peak Concurrent Users (PCU) | 2M | ~20% of DAU online at peak |
| | Average Session Length | 2 hours | Multiple matches per session |
| **Match Metrics** | Players per Match | 100 | Standard BR format |
| | Average Match Duration | 20 minutes | From plane to victory royale |
| | Server Tick Rate | 60 Hz | 60 state updates/second |
| | Matches per Player/Day | 6 matches | 2hr session ÷ 20min match |
| **Network** | Player RTT (P95) | <100ms | Competitive gaming standard |
| | Packet Loss Tolerance | <1% | Critical for gameplay |
| | Bandwidth per Player | 100 Kbps | Bidirectional average |

---

## 🧮 PART 2: Gaming-Specific Calculator

### Rule #1: **The Concurrent Player Formula**
```
THE GOLDEN RULE FOR GAMING:
Concurrent Matches = Concurrent Players ÷ Players per Match

Example:
2M concurrent players ÷ 100 players/match = 20,000 active matches
```

### Rule #2: **The Tick Rate Multiplier**
```
State Updates per Match = Players × Tick Rate
                        = 100 × 60 = 6,000 updates/second per match

Total State Updates = Matches × Updates per Match
                    = 20K matches × 6K = 120 Million updates/second
```

### Rule #3: **The Daily Match Throughput**
```
Daily Active Players = 10M
Average Matches per Player = 6
Daily Match Starts = 10M × 6 = 60 Million matches/day

Matches per Second = 60M ÷ 86,400 seconds = ~695 matches/sec
Peak Matches/Sec = 695 × 2 (peak multiplier) = ~1,400 matches/sec
```

---

## 📈 PART 3: Battle Royale Napkin Math Template

```
┌─────────────────────────────────────────────────────────┐
│   BATTLE ROYALE SCALE ESTIMATION - FILLED TEMPLATE      │
└─────────────────────────────────────────────────────────┘

STEP 1: PLAYER & MATCH LOAD
───────────────────────────
Daily Active Users (DAU):     10 M
Peak Concurrent Users (PCU):  2 M (20% of DAU)
Players per Match:            100

→ Concurrent Matches = 2M ÷ 100       = 20,000 matches
→ Daily Match Starts = 10M × 6        = 60 M matches
→ Matches per Second = 60M ÷ 100K     = 600 matches/sec avg
→ Peak Matches/Sec   = 600 × 2        = 1,200 matches/sec

STEP 2: NETWORK TRAFFIC ESTIMATION
───────────────────────────
Tick Rate:                    60 Hz (updates per second)
Players per Match:            100
State Update Size:            40 bytes per player

→ Updates per Match/Sec = 100 × 60           = 6,000 updates
→ Total Updates/Sec     = 20K matches × 6K   = 120 M updates/sec
→ Bandwidth per Match   = 6K × 40 bytes      = 240 KB/sec = ~2 Mbps
→ Total Bandwidth       = 20K × 2 Mbps       = 40 Gbps per region

STEP 3: SERVER COMPUTE
───────────────────────────
Matches per Server Instance:  20 matches (containerized)
Concurrent Matches:           20,000

→ Game Servers Needed = 20K ÷ 20             = 1,000 servers
→ Warm Pool (Standby) = 1K × 0.5             = 500 standby servers
→ Total Fleet Size    = 1,000 + 500          = 1,500 servers per region

STEP 4: STORAGE (REPLAYS & ANALYTICS)
───────────────────────────
Match Duration:               20 minutes
State Snapshots:              20 min × 60 sec × 60 ticks = 72,000 snapshots
Snapshot Size:                100 players × 40 bytes = 4 KB per snapshot

→ Raw Replay Size  = 72K × 4 KB              = 288 MB per match
→ Compressed (10:1)= 288 MB ÷ 10             = ~29 MB per match
→ Daily Storage    = 60M matches × 29 MB     = 1.74 PB/day
→ 30-day Retention = 1.74 PB × 30            = 52 PB

Analytics Data (Per Match):
→ Player Stats = 100 players × 10 KB         = 1 MB per match
→ Daily Stats  = 60M × 1 MB                  = 60 TB/day

STEP 5: REGIONAL DISTRIBUTION
───────────────────────────
Assume 3 regions: US-West (30%), EU-West (40%), Asia-SE (30%)

US-West:
→ Concurrent Players = 2M × 0.30             = 600K
→ Concurrent Matches = 600K ÷ 100            = 6,000 matches
→ Game Servers       = 6K ÷ 20               = 300 + 150 standby = 450

EU-West:
→ Concurrent Players = 2M × 0.40             = 800K
→ Concurrent Matches = 800K ÷ 100            = 8,000 matches
→ Game Servers       = 8K ÷ 20               = 400 + 200 standby = 600

Asia-SE:
→ Concurrent Players = 2M × 0.30             = 600K
→ Concurrent Matches = 600K ÷ 100            = 6,000 matches
→ Game Servers       = 6K ÷ 20               = 300 + 150 standby = 450

Total Global:                                = 1,500 servers
```

---

## 🧠 PART 4: Gaming-Specific Mental Math

### **Technique 1: The 100-Player Divisor**
```
For BR games, think in multiples of 100:
- 1M players = 10,000 matches
- 2M players = 20,000 matches
- 10M players = 100,000 matches

Pro Tip: Just divide player count by 100!
```

### **Technique 2: The Tick Rate Calculator**
```
60 Hz = 60 updates per second
100 players × 60 = 6,000 updates per match per second

Shortcut: Players × 60 = Updates/sec
Then multiply by number of matches for total load
```

### **Technique 3: The Match Duration to Daily Matches**
```
Average session: 2 hours
Average match: 20 minutes

Matches per session = 120 min ÷ 20 min = 6 matches

Daily matches = DAU × 6
              = 10M × 6 = 60M matches
```

### **Technique 4: The Server Packing Efficiency**
```
Modern servers (8-core, 16GB RAM) can handle:
- 20 matches per instance (containerized with Kubernetes)
- Each match: 100 players @ 60Hz

Calculate servers needed:
Concurrent Matches ÷ 20 = Server Fleet Size
```

---

## 💾 PART 5: Storage Deep Dive

### Replay System

```
┌─────────────────────────────────────────────────────────┐
│                REPLAY STORAGE BREAKDOWN                  │
└─────────────────────────────────────────────────────────┘

MATCH REPLAY DATA:
──────────────────
Match Duration:           20 minutes = 1,200 seconds
Tick Rate:                60 Hz
Total Ticks:              1,200 × 60 = 72,000 ticks

Per-Tick Data:
- Player Positions:       100 × 12 bytes (x,y,z) = 1.2 KB
- Player Rotations:       100 × 8 bytes (pitch,yaw) = 0.8 KB
- Player Health/Shield:   100 × 8 bytes = 0.8 KB
- Actions/Events:         ~1 KB (shots, looting, building)
Total per Tick:           ~4 KB

→ Raw Replay Size = 72,000 × 4 KB = 288 MB

COMPRESSION:
────────────
- Delta Compression:      5:1 ratio (only store changes)
- GZIP Compression:       2:1 ratio (text compression)
- Total Compression:      10:1 ratio

→ Compressed Size = 288 MB ÷ 10 = ~29 MB per match

DAILY STORAGE:
──────────────
Daily Matches:            60 M
Storage per Match:        29 MB

→ Daily Storage = 60M × 29 MB = 1.74 PB/day

RETENTION POLICY:
─────────────────
Tier 1 (Hot - S3 Standard):        Last 7 days  = 12.2 PB
Tier 2 (Warm - S3 IA):              8-30 days    = 40 PB
Tier 3 (Cold - S3 Glacier):         30+ days     = Archived

Cost Optimization:
- S3 Standard: $0.023/GB × 12.2 PB = $280,600/month
- S3 IA:       $0.0125/GB × 40 PB = $512,000/month
- S3 Glacier:  $0.004/GB (long-term archive)

Total Monthly Cost (90 days):     ~$1.1 M/month
```

### Analytics Data

```
PLAYER MATCH STATS:
──────────────────
Per Player per Match:
- Placement:              4 bytes
- Kills/Deaths/Assists:   12 bytes
- Damage Dealt/Taken:     8 bytes
- Distance Traveled:      4 bytes
- Weapons Used:           20 bytes (array)
- Items Looted:           4 bytes
- Survival Time:          4 bytes
- Accuracy Stats:         20 bytes
Total:                    ~76 bytes → round to 100 bytes

Per Match (100 players):
→ Stats Storage = 100 × 100 bytes = 10 KB

Daily Stats:
→ 60M matches × 10 KB = 600 GB/day = ~18 TB/month

TIME-SERIES EVENTS (ClickHouse):
──────────────────────────────
Events per Match:
- Kill Events:            ~99 events (100 → 1 survivor)
- Damage Events:          ~500 events (avg 5 per player)
- Loot Events:            ~300 events
- Movement Checkpoints:   ~1,000 events
Total:                    ~2,000 events per match

Event Size:               ~200 bytes (JSON)
Storage per Match:        2K × 200 = 400 KB

→ Daily Events = 60M × 400 KB = 24 TB/day
→ Monthly Events = 24 TB × 30 = 720 TB

ClickHouse Compression:   ~5:1
→ Compressed = 720 TB ÷ 5 = 144 TB/month
```

---

## 🌍 PART 6: Regional Architecture Math

```
┌─────────────────────────────────────────────────────────┐
│           GLOBAL REGIONAL DEPLOYMENT STRATEGY            │
└─────────────────────────────────────────────────────────┘

REGION SELECTION CRITERIA:
─────────────────────────
1. Player Population:     30-40% per major region
2. Latency Requirement:   <30ms to nearest data center
3. Data Residency:        GDPR, local regulations

REGION: US-WEST (Oregon)
────────────────────────
Player Distribution:      30% = 600K concurrent
Concurrent Matches:       6,000 matches
Game Server Fleet:        300 active + 150 standby = 450 servers

Infrastructure:
- EC2 c5.2xlarge (8 vCPU, 16GB): $0.15/hour spot pricing
- Cost: 450 × $0.15 × 730 hours = $49,275/month
- Bandwidth: 6K matches × 2 Mbps = 12 Gbps
- Bandwidth Cost: 12 Gbps × 2.6 TB/hour × 730 hours × $0.09/GB = $20,800/month

Total Region Cost:        ~$70K/month

REGION: EU-WEST (Ireland)
────────────────────────
Player Distribution:      40% = 800K concurrent
Concurrent Matches:       8,000 matches
Game Server Fleet:        400 active + 200 standby = 600 servers

Infrastructure:
- Cost: 600 × $0.15 × 730 hours = $65,700/month
- Bandwidth: 8K matches × 2 Mbps = 16 Gbps
- Bandwidth Cost: ~$27,700/month

Total Region Cost:        ~$93K/month

REGION: ASIA-SE (Singapore)
────────────────────────
Player Distribution:      30% = 600K concurrent
Concurrent Matches:       6,000 matches
Game Server Fleet:        300 active + 150 standby = 450 servers

Infrastructure:
- Cost: 450 × $0.15 × 730 hours = $49,275/month
- Bandwidth Cost: ~$20,800/month

Total Region Cost:        ~$70K/month

GLOBAL TOTAL:
─────────────
Compute:                  $164K/month
Bandwidth:                $69K/month
Storage (S3 + Analytics): $1.2M/month
Database (DynamoDB):      $50K/month
Redis/ElastiCache:        $30K/month
Monitoring/Logging:       $20K/month

TOTAL INFRASTRUCTURE:     ~$1.5M/month for 10M DAU
                          = $0.15 per DAU per month
                          = $0.005 per DAU per day
```

---

## 🎯 PART 7: Matchmaking Queue Math

```
MATCHMAKING PERFORMANCE ESTIMATION:
──────────────────────────────────

INPUTS:
- Concurrent Queue:       100K players waiting
- Target Match Size:      100 players
- Skill Brackets:         10 brackets (MMR ranges)
- Regional Queues:        3 regions

DISTRIBUTION:
────────────
Per Region per Skill Bracket:
100K ÷ 3 regions ÷ 10 brackets = ~3,333 players per queue

Matches from this bucket:
3,333 ÷ 100 = ~33 matches immediately

TIME TO MATCH:
──────────────
Best Case (Popular bracket, peak time):
- 100 players available instantly
- Match Time: <10 seconds (queue + server allocation)

Average Case (Medium bracket):
- 50 players available, need 50 more
- Wait for 30 seconds (expand skill tolerance)
- Match Time: 30-60 seconds

Worst Case (Low population bracket, off-peak):
- 20 players available
- Expand tolerance every 30 seconds: MMR ±200 → ±250 → ±300
- After 120 seconds: Match with wider skill range
- Match Time: 60-180 seconds (95th percentile)

TARGET METRICS:
──────────────
P50 Match Time:           <30 seconds
P95 Match Time:           <180 seconds (3 minutes)
P99 Match Time:           <300 seconds (5 minutes)

If P99 > 300s: Add bots to fill lobby (controversial but keeps players engaged)
```

---

## 🔥 PART 8: Anti-Cheat Processing Load

```
ANTI-CHEAT OVERHEAD ESTIMATION:
──────────────────────────────

SERVER-SIDE VALIDATION (Per Match):
───────────────────────────────────
Validations per Player per Second:
- Movement Speed Check:      60 per sec (every tick)
- Action Legality:           ~10 per sec (shots, jumps, etc.)
- Impossible Actions:        60 per sec (wall checks, etc.)
Total Validations:           ~130 per player per second

Per Match:
130 × 100 players = 13,000 validations/second
Overhead: ~5% CPU (optimized validation logic)

BEHAVIORAL ANALYSIS (Post-Match):
─────────────────────────────────
Metrics Collected per Player:
- Headshot Percentage
- Reaction Times (histogram)
- Crosshair Placement (pre-aiming)
- Recoil Compensation Accuracy
- Wall Tracking Incidents

Processing Time:      ~2 seconds per player per match
Concurrent Processing: 60M matches/day × 100 players
                     = 6B player-match records/day

Kafka Event Stream:   6B events/day ÷ 86,400 sec = ~70K events/sec
ClickHouse Ingestion: 70K events × 500 bytes = 35 MB/sec

ML Model Inference (Suspicious Players):
- Flagged Players:    ~1% of matches = 600K players/day
- Inference Time:     50ms per player
- GPU Instances:      10 × p3.2xlarge (1 GPU each)
- Cost:               10 × $3.06/hour × 730 hours = $22,338/month
```

---

## 📊 PART 9: Latency Budget Breakdown

```
┌─────────────────────────────────────────────────────────┐
│         END-TO-END LATENCY BUDGET (TARGET: <100ms)      │
└─────────────────────────────────────────────────────────┘

CLIENT TO SERVER (Input Processing):
────────────────────────────────────
1. Input Capture:                 1ms (keyboard/mouse)
2. Client Processing:             2ms (input validation)
3. Packet Serialization:          1ms (UDP packet creation)
4. Network Transit (Client→Server): 20ms (P50, regional server)
──────────────────────────────────────
Subtotal (Input):                 24ms

SERVER PROCESSING:
──────────────────
5. Packet Deserialization:        0.5ms
6. Input Validation:              0.5ms (anti-cheat checks)
7. Physics Simulation:            10ms (60Hz = 16.6ms budget)
8. Game Logic (combat, loot):     3ms
9. State Serialization:           1ms (delta compression)
──────────────────────────────────────
Subtotal (Server):                15ms

SERVER TO CLIENT (State Update):
────────────────────────────────
10. Network Transit (Server→Client): 20ms (P50)
11. Packet Deserialization:       0.5ms
12. State Reconciliation:         2ms (prediction correction)
13. Rendering Pipeline:           5ms (update game objects)
──────────────────────────────────────
Subtotal (Response):              27.5ms

TOTAL END-TO-END:                 66.5ms ✓ (Well within 100ms budget)

BUFFER FOR VARIABILITY:           33.5ms (handles P95 cases)

P95 LATENCY TARGETS:
───────────────────
Network Transit:      40ms (vs 20ms P50)
Server Processing:    20ms (vs 15ms P50)
Total P95:            ~95ms ✓ (still within budget)
```

---

## 🚀 PART 10: Scale Milestones

### **10K Concurrent Players** (Indie Game)
```
Infrastructure:
- Single Region (US-West)
- 100 concurrent matches
- 5 game servers (20 matches each)
- Cost: ~$2K/month

Challenges:
- Basic matchmaking (no skill-based)
- Minimal analytics
- Single region = high latency for distant players
```

### **100K Concurrent Players** (Growing Game)
```
Infrastructure:
- 2 Regions (US + EU)
- 1,000 concurrent matches
- 50 game servers per region = 100 total
- Cost: ~$20K/month

Challenges:
- Implement skill-based matchmaking
- Add basic anti-cheat
- Start analytics pipeline
```

### **1M Concurrent Players** (Popular Game)
```
Infrastructure:
- 3 Regions (US, EU, Asia)
- 10,000 concurrent matches
- 500 servers per region = 1,500 total
- Cost: ~$200K/month

Challenges:
- Advanced anti-cheat (ML-based)
- Replay system
- Complex matchmaking (skill + latency)
- DDoS protection critical
```

### **10M Concurrent Players** (Top-Tier Game - Fortnite Scale)
```
Infrastructure:
- 10+ Regions globally
- 100,000 concurrent matches
- 15,000+ game servers
- Cost: ~$2M/month

Challenges:
- Multi-region coordination
- Global leaderboards
- Advanced anti-cheat at scale
- Content delivery (patches 5GB+)
- Customer support infrastructure
```

---

## 🎮 PART 11: Real-World Benchmarks

### **Fortnite (Epic Games)**
```
Peak Concurrent Players:  15M+ (events like Travis Scott concert)
Regions:                  20+ globally
Match Duration:           20 minutes
Players per Match:        100
Infrastructure:           AWS + Custom
Estimated Cost:           $3-5M/month (estimated)

Key Optimizations:
- Unreal Engine (in-house optimization)
- Global CDN for content delivery
- Advanced anti-cheat (Easy Anti-Cheat)
- Custom matchmaking with SBMM
```

### **PUBG (PUBG Corp)**
```
Peak Concurrent Players:  3M+
Regions:                  15+
Match Duration:           30 minutes
Players per Match:        100
Infrastructure:           Azure
Estimated Cost:           $1-2M/month

Challenges Faced:
- Initial server performance issues (fixed over time)
- Cheating epidemic (BattleEye integration)
- Mobile version separate infrastructure
```

### **Apex Legends (Respawn/EA)**
```
Peak Concurrent Players:  1M+
Regions:                  10+
Match Duration:           20 minutes
Players per Match:        60
Infrastructure:           AWS + Multiplay
Estimated Cost:           $500K-1M/month

Technical Details:
- Modified Source Engine
- 20Hz tick rate (criticized, later improved to 60Hz)
- Respawn beacons add complexity to state
- Fast-paced requires excellent lag compensation
```

---

## 💡 PART 12: Cost Optimization Strategies

### **1. Spot Instances for Game Servers**
```
On-Demand:  $0.50/hour × 1,500 servers × 730 hours = $547,500/month
Spot Price: $0.15/hour × 1,500 servers × 730 hours = $164,250/month

Savings:    $383,250/month (70% reduction)

Trade-off:
- Spot instances can be terminated with 2-min notice
- Mitigation: Graceful shutdown, migrate matches to other servers
- Keep 10% on on-demand for critical matches
```

### **2. Dynamic Scaling**
```
Off-Peak (2am-8am):  50% of peak load
Peak (6pm-11pm):     100% of peak load
Average:             70% of peak load

Static Fleet:        1,500 servers × 730 hours = 1,095,000 server-hours
Dynamic Fleet:       1,500 × 0.7 × 730 = 766,500 server-hours

Savings:             328,500 server-hours × $0.15 = $49,275/month
```

### **3. Warm Pool Management**
```
Cold Start (Docker pull + app boot):  60-90 seconds
Warm Start (pre-initialized):         5-10 seconds

Strategy:
- Maintain 50% warm pool during off-peak
- Scale to 100% warm pool during peak approach
- Predictive scaling based on historical data

Cost:
- Warm pool cost: 750 servers × $0.15 × 730 = $82,125/month
- Value: Sub-30s match start time (vs 90s cold start)
- Player retention: Worth the cost
```

### **4. Content Delivery Optimization**
```
Game Patch Size:      5 GB
Patch Frequency:      Monthly
Active Players:       10M

WITHOUT CDN:
Bandwidth from origin:  10M × 5 GB = 50 PB
Cost:                   50 PB × $0.09/GB = $4.5M per patch

WITH CDN (CloudFront):
Bandwidth from origin:  5 GB (single copy)
CDN bandwidth:          50 PB × $0.02/GB = $1M
Total Cost:             $1M per patch

Savings:                $3.5M per patch (78% reduction)
```

---

## 🔧 PART 13: Interview Cheat Sheet

```
┌──────────────────────────────────────────────────────────┐
│   BATTLE ROYALE SYSTEM DESIGN - 5 MIN ESTIMATION RITUAL  │
└──────────────────────────────────────────────────────────┘

[ ] 1. Ask about scale:
    → DAU? Peak Concurrent? Players per match?

[ ] 2. Calculate concurrent matches:
    → Concurrent Players ÷ 100 = Concurrent Matches

[ ] 3. Estimate server fleet:
    → Concurrent Matches ÷ 20 = Servers needed

[ ] 4. Network bandwidth:
    → Matches × 100 players × 60 Hz × 40 bytes = Bandwidth

[ ] 5. Storage (replays):
    → Daily Matches × 29 MB (compressed) = Daily Storage

[ ] 6. Regional distribution:
    → Allocate 30-40% per major region

[ ] 7. Cost sanity check:
    → $0.10-0.20 per DAU per month is typical
```

---

## 🎯 Key Gaming Metrics Reference

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Server Tick Rate** | 60 Hz | Gameplay responsiveness |
| **Player RTT (P95)** | <100ms | Competitive fairness |
| **Match Start Time** | <30s | Player patience threshold |
| **Matchmaking Time (P95)** | <180s | Queue abandonment rate |
| **Concurrent Matches** | 20,000 | Infrastructure sizing |
| **Servers per Region** | 1,500 | Regional capacity planning |
| **Bandwidth per Player** | 100 Kbps | Network planning |
| **Replay Storage** | 29 MB/match | Storage cost driver |
| **Cost per DAU** | $0.15/month | Business viability |

---

## 🔁 Repetition for Mastery

**REPEAT OUT LOUD 3 TIMES:**

1. *"Concurrent players divided by 100 equals concurrent matches!"*
2. *"60 Hz tick rate means 6,000 updates per match per second!"*
3. *"Gaming is about latency first, throughput second - always optimize for <100ms!"*

---

## 🎓 Professor's Gaming Wisdom

> **"In gaming systems, 95% of complexity is handling the last 5% of edge cases"**

Your interviewer wants to see:
1. ✅ Understanding of real-time constraints
2. ✅ Awareness of latency budgets
3. ✅ Regional deployment strategy
4. ✅ Cost optimization mindset
5. ✅ Anti-cheat considerations

**NOT NEEDED:**
- ❌ Exact network protocol details
- ❌ Game engine internals
- ❌ Rendering pipeline knowledge

---

**Now go design the next Fortnite!** 🎮🚀
