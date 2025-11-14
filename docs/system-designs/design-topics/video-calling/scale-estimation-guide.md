# 🎯 Video Calling System Scale Estimation Masterclass

## The POWER Technique for Video Calling Scale Math
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This framework applies to ANY video calling system: Zoom, Google Meet, Microsoft Teams, WebEx, etc.

---

## 📊 PART 1: Users & Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 300 Million | Zoom-scale (300M daily participants) |
| | Peak Concurrent Meetings | 10 Million | ~3% of users in meetings simultaneously |
| | Average Meeting Size | 5 participants | Mix of 1-on-1 and small groups |
| | Large Meetings (50+ ppl) | 1% of meetings | Webinars, all-hands |
| **Meeting Distribution** | 1-on-1 calls | 40% | Most common type |
| | Small groups (3-10) | 50% | Team meetings |
| | Large groups (10-50) | 9% | Department meetings |
| | Webinars (50+) | 1% | Company-wide events |
| **Duration** | Average meeting length | 45 minutes | Typical business meeting |
| | Peak hours | 8 hours/day | Business hours (9am-5pm) |
| | Peak traffic multiplier | 2x | Start of hour (meetings begin) |
| **Quality Settings** | HD (720p) usage | 60% | Default for most users |
| | FHD (1080p) usage | 10% | Premium users |
| | SD (480p) usage | 30% | Low bandwidth users |
| **Recording** | Meetings recorded | 20% | Cloud recording feature |
| | Recording retention | 90 days | Default retention policy |

---

## 🧮 PART 2: The "Video Bandwidth Calculator" - Your Mental Math Toolkit

### Rule #1: **The Bandwidth Ladder**
```
Remember these anchors for video calling:
• Audio only: 50 Kbps (Opus codec, good quality)
• 360p video: 500 Kbps
• 480p (SD): 1 Mbps
• 720p (HD): 2.5 Mbps
• 1080p (FHD): 4 Mbps
• 4K: 15 Mbps (rarely used in conferencing)

Screen Share: 1-2 Mbps (lower framerate)
```

### Rule #2: **The P2P vs SFU Decision**
```
1-on-1 call (P2P - Peer to Peer):
  Upload: 2.5 Mbps (720p)
  Download: 2.5 Mbps (720p)
  Total: 5 Mbps symmetric
  Server cost: $0 (direct connection)

Group call with 10 people (SFU - Selective Forwarding Unit):
  Upload per person: 2.5 Mbps (1 stream to SFU)
  Download per person: 9 × 2.5 Mbps = 22.5 Mbps (Gallery view)
  Server bandwidth: 10 × 2.5 Mbps (in) + 90 × 2.5 Mbps (out) = 250 Mbps

  Why SFU wins:
  ✓ Upload stays constant (2.5 Mbps) regardless of group size
  ✓ Server doesn't transcode (low CPU)
  ✗ High server bandwidth
```

### Rule #3: **The Recording Storage Formula**
```
Video recording storage per hour:
• 480p: ~200 MB/hour (H.264 compression)
• 720p: ~400 MB/hour
• 1080p: ~800 MB/hour

Audio recording: ~10 MB/hour (Opus)

Quick calc: 720p meeting ≈ 400 MB/hour ≈ 0.4 GB/hour
```

---

## 📈 PART 3: Quick Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│  🎯 VIDEO CALLING NAPKIN MATH TEMPLATE - Universal     │
└─────────────────────────────────────────────────────────┘

STEP 1: CONCURRENT PARTICIPANTS
───────────────────────────
Total Users:                [____] M
Peak Concurrent %:          [____] %
Avg Meeting Size:           [____] participants

→ Concurrent Participants = Users × Peak% = [____] M
→ Concurrent Meetings = Participants ÷ Avg Size = [____] M

STEP 2: BANDWIDTH ESTIMATION
───────────────────────────
Video Quality:              [____] p (resolution)
Bitrate per stream:         [____] Mbps

For SFU-based (groups):
→ Upload per user = Bitrate                    = [____] Mbps
→ Download per user = (Size - 1) × Bitrate     = [____] Mbps
→ SFU bandwidth = Participants × Upload + 
                  Participants × (Size-1) × Bitrate = [____] Gbps

STEP 3: SERVER CAPACITY (SFU)
───────────────────────────
Participants per SFU:       [____] (typically 200-500)
Bandwidth per SFU:          [____] Gbps

→ SFU servers needed = Concurrent ÷ Participants_per_SFU = [____]

STEP 4: RECORDING STORAGE
───────────────────────────
Meetings recorded:          [____] %
Avg meeting duration:       [____] minutes
Recording bitrate:          [____] MB/hour

→ Daily recordings = Meetings × Recorded% × Duration × Bitrate
→ 90-day storage = Daily × 90 = [____] PB

STEP 5: TURN SERVER BANDWIDTH
───────────────────────────
TURN usage rate:            [____] % (typically 10-15%)
Bandwidth multiplier:       2× (upload + download relay)

→ TURN bandwidth = Concurrent × TURN% × Bitrate × 2 = [____] Gbps
```

---

## 💾 PART 4: Zoom-Scale System Filled Template

```
┌─────────────────────────────────────────────────────────┐
│      ZOOM-SCALE VIDEO CALLING - NAPKIN MATH SOLUTION   │
└─────────────────────────────────────────────────────────┘

STEP 1: CONCURRENT PARTICIPANTS
───────────────────────────
Total Users:                300 M daily participants
Peak Concurrent %:          3%
Avg Meeting Size:           5 participants

→ Concurrent Participants = 300M × 3% = 10 M participants
→ Concurrent Meetings = 10M ÷ 5 = 2 M meetings

STEP 2: BANDWIDTH ESTIMATION
───────────────────────────
Video Quality:              720p (HD, most common)
Bitrate per stream:         2.5 Mbps

Breakdown:
• 1-on-1 (40%): 4M participants → 2M calls
  - Bandwidth: 2M × 5 Mbps (P2P) = 10,000 Gbps (user bandwidth, not server)

• Small groups (50%): 5M participants → 1M meetings (avg 5 ppl)
  - Upload per user: 2.5 Mbps
  - Download per user: 4 × 2.5 = 10 Mbps
  - SFU bandwidth: 5M × 2.5 Mbps (in) + 5M × 10 Mbps (out)
    = 12.5 Gbps + 50 Gbps = 62.5 Gbps per region

• Large groups (10%): 1M participants → 50K meetings (avg 20 ppl)
  - SFU bandwidth: More complex (simulcast + selective forwarding)
  - Estimated: ~20 Gbps per region

→ Total SFU Bandwidth (per region): ~85 Gbps
→ Multi-region (5 regions): 85 × 5 = 425 Gbps global

STEP 3: SERVER CAPACITY (SFU)
───────────────────────────
Participants per SFU:       300 (conservative estimate)
Bandwidth per SFU:          10 Gbps NIC

→ SFU servers needed = 10M ÷ 300 = 33,333 SFU instances
→ With multi-region: ~6,700 per region (5 regions)
→ With auto-scaling + overhead: ~40,000 total servers

STEP 4: RECORDING STORAGE
───────────────────────────
Meetings recorded:          20%
Avg meeting duration:       45 minutes = 0.75 hours
Recording bitrate:          400 MB/hour (720p)

→ Daily recordings = 2M meetings × 20% × 0.75h × 400MB
                   = 400,000 × 0.75 × 400MB
                   = 120,000 GB/day = 120 TB/day

→ 90-day storage = 120 TB × 90 = 10,800 TB ≈ 11 PB

With compression & lifecycle (move to Glacier):
→ Active (30 days): 3.6 PB (S3 Standard)
→ Archive (60 days): 7.2 PB (S3 Glacier)

STEP 5: TURN SERVER BANDWIDTH
───────────────────────────
TURN usage rate:            12% (typical in enterprise)
Bandwidth multiplier:       2× (relay traffic both ways)

→ TURN users = 10M × 12% = 1.2M participants
→ TURN bandwidth = 1.2M × 2.5 Mbps (upload) × 2
                 = 1.2M × 5 Mbps = 6,000 Gbps = 6 Tbps

This is HUGE! TURN is expensive.
Optimization: Use TURN only when P2P fails.

STEP 6: SIGNALING SERVERS
───────────────────────────
Signaling is lightweight (WebSocket):
→ 10M concurrent connections
→ 100K connections per server (Node.js/Go)
→ Servers needed: 10M ÷ 100K = 100 signaling servers

Messages per second (join/leave/mute/etc.):
→ Assume 10 messages/min per participant
→ 10M × 10 ÷ 60 = 1.6M messages/second
→ Easily handled by 100 servers

STEP 7: DATABASE & CACHE
───────────────────────────
PostgreSQL (Metadata):
• User accounts: 300M users × 1KB = 300 GB
• Meeting history: 2M meetings/day × 365 days × 2KB = 1.5 TB/year
• Total: ~2 TB (easily fits in single DB with replicas)

Redis (Active meetings, presence):
• Active meetings: 2M meetings × 10 KB = 20 GB
• User presence: 10M users × 1 KB = 10 GB
• Total: ~30 GB (single Redis instance or small cluster)
```

---

## 🧠 PART 5: Video-Specific Mental Math Techniques

### **Technique 1: The "Group Size Multiplier"**
*(For Bandwidth)*
```
EMOTION TRIGGER: "Video bandwidth grows with N²!"

1-on-1: 2 people × 2.5 Mbps = 5 Mbps total traffic
4 people: 4 upload + 12 download = 16 Mbps total traffic
10 people: 10 upload + 90 download = 100 Mbps total traffic

Formula for N people:
- Total connections: N × (N-1) 
- Total bandwidth: N × (N-1) × Bitrate

But with SFU:
- Upload: N × Bitrate (linear growth!)
- Download: N × (N-1) × Bitrate
- Much more scalable
```

### **Technique 2: The "Simulcast Savings"**
*(For Bandwidth Optimization)*
```
Without Simulcast:
- 10 people, each sends 720p (2.5 Mbps)
- Download per user: 9 × 2.5 = 22.5 Mbps
- Gallery view: All streams shown → 22.5 Mbps needed

With Simulcast (3 layers):
- Each user sends: 360p (0.5 Mbps) + 480p (1 Mbps) + 720p (2.5 Mbps)
- Upload: 4 Mbps per user (slightly higher)
- SFU selects quality per receiver:
  - Gallery view: Send 360p for thumbnails → 9 × 0.5 = 4.5 Mbps
  - Speaker view: Send 720p for active speaker → 2.5 Mbps + 8 × 0.5 = 6.5 Mbps

Savings: 22.5 Mbps → 6.5 Mbps (70% reduction!)
```

### **Technique 3: The "Recording Cost Calculator"**
*(For Storage)*
```
Key insight: Recordings compress well!

Live stream: 2.5 Mbps (for realtime)
Recorded file: 400 MB/hour (H.264 optimized)

Conversion:
2.5 Mbps × 3600 seconds = 9000 Mb = 1125 MB/hour (live)
After compression: 400 MB/hour (recorded)
Compression ratio: 65% savings

Quick rule: 400 MB/hour ≈ 0.4 GB/hour for 720p
```

### **Technique 4: The "Server Capacity Estimate"**
*(For SFU Sizing)*
```
Server capacity depends on:
1. Network bandwidth (10 Gbps NIC typical)
2. CPU (encode/decode if needed)

SFU doesn't transcode → Network-bound, not CPU-bound

10 Gbps NIC:
- 10,000 Mbps ÷ 2.5 Mbps = 4,000 streams theoretical
- With overhead (50%): 2,000 streams practical
- Avg meeting size 5 → 400 meetings per SFU
- Participants: 2,000 total per SFU

But Zoom uses 300-500 participants per SFU (conservative)
```

---

## 🎯 PART 6: The Visual Mind Map Approach

```
                    🎥 VIDEO CALLING SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 USERS          🌐 BANDWIDTH      ☁️ SERVERS
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
   DAU   Conc       Mbps   Tbps      SFU    TURN
   300M   10M       2.5    0.5       40K    1K
```

**Memory Trigger**: Think **"U.B.S."** = Users, Bandwidth, Servers

---

## 🎯 PART 7: The Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│  VIDEO CALLING SCALE ESTIMATION - 5 MIN RITUAL  │
└──────────────────────────────────────────────────┘

[ ] 1. Concurrent users (DAU × peak %, typically 3-5%)
[ ] 2. Meeting size (1-on-1, small group, large group breakdown)
[ ] 3. Bandwidth per stream (720p = 2.5 Mbps)
[ ] 4. SFU vs P2P decision (P2P for 1-on-1, SFU for groups)
[ ] 5. SFU servers (10M concurrent ÷ 300/server = 33K servers)
[ ] 6. Recording storage (20% recorded × 400MB/hour × 90 days)
[ ] 7. TURN bandwidth (10-15% users × 2× relay = expensive!)
[ ] 8. Smell test: Does 40K servers for 300M users sound right? YES!
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Concurrent Participants** | 10M | Size your infrastructure |
| **SFU Servers** | 40K | Server capacity planning |
| **SFU Bandwidth (total)** | 500 Gbps | Network backbone capacity |
| **TURN Bandwidth** | 6 Tbps | Most expensive component! |
| **Recording Storage (90d)** | 11 PB | S3/GCS storage costs |
| **Signaling Servers** | 100 | WebSocket connection handling |
| **Avg Bandwidth (per user)** | 10-15 Mbps | User internet requirement |

---

## 💡 Pro Architect Tips

### **Tip 1: The Smell Test**
After calculations, ask:
- "Can 1 SFU server handle 300 participants?" → YES (network-bound)
- "Is 11 PB storage for 90 days recordings reasonable?" → YES (Zoom stores petabytes)
- "Does 6 Tbps TURN bandwidth sound expensive?" → YES (this is why TURN is fallback only!)

### **Tip 2: The Comparison Anchor**
Always compare to known systems:
- "Zoom: 300M daily participants (our calc: same) ✓"
- "Google Meet: Similar scale, integrated with Workspace"
- "Microsoft Teams: 250M+ users"

### **Tip 3: Start with Constraints**
Always ask first:
1. Expected concurrent users?
2. Average meeting size?
3. Video quality requirements (SD, HD, FHD)?
4. Recording needed? Retention period?
5. Geographic distribution (multi-region)?

---

## 📚 Quick Reference: Common Video Calling Benchmarks

| **System** | **DAU** | **Concurrent** | **Bandwidth/user** | **Infrastructure** |
|------------|---------|----------------|--------------------|--------------------|
| **Zoom** | 300M | 10M | 10-15 Mbps | 40K+ servers, multi-region |
| **Google Meet** | 250M | 8M | 10-15 Mbps | Google Cloud infrastructure |
| **Microsoft Teams** | 250M | 8M | 10-15 Mbps | Azure datacenters |
| **WebEx** | 150M | 5M | 10-15 Mbps | Cisco infrastructure |
| **Discord** | 150M | 4M (voice heavy) | 5-10 Mbps | Self-hosted + AWS |

---

## 🔧 Practical Application: Adapting This Template

### For a **Startup Video App** (1M users):
```
STEP 1: TRAFFIC
- 1M users × 5% concurrent = 50K participants
- Avg meeting size: 3 people → 17K meetings

STEP 2: BANDWIDTH
- 720p SFU: 50K × 2.5 Mbps (up) + 50K × 5 Mbps (down) = 375 Gbps
- Single region: Manageable with cloud providers

STEP 3: SERVERS
- SFU: 50K ÷ 300 = 167 servers
- Signaling: 50K ÷ 100K = 1 server
- Cost: ~$10K-20K/month (AWS/GCP)

STEP 4: STORAGE
- 20% recorded: 3,400 meetings × 0.75h × 400MB = 1 TB/day
- 90 days: 90 TB (S3 costs ~$2K/month)
```

### For an **Enterprise Solution** (10M users):
```
STEP 1: TRAFFIC
- 10M users × 3% concurrent = 300K participants
- Avg meeting size: 8 people (larger teams) → 37.5K meetings

STEP 2: BANDWIDTH
- Higher quality (1080p): 4 Mbps per stream
- SFU bandwidth: More complex (simulcast layers)
- Estimated: 1.5 Tbps (multi-region)

STEP 3: SERVERS
- SFU: 300K ÷ 300 = 1,000 servers (multi-region)
- Redundancy + auto-scaling: 1,500 servers
- Cost: ~$150K-200K/month

STEP 4: SPECIAL REQUIREMENTS
- SSO integration (OAuth, SAML)
- Compliance (HIPAA, SOC 2, GDPR)
- Dedicated infrastructure (VPC, private subnets)
- Advanced analytics and admin controls
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Small Business Video Platform
```
Given:
- 5M registered users
- 10% daily active
- 5% in meetings at peak
- Average meeting: 4 people
- 720p default, 480p fallback for 30%
- 15% of meetings recorded
- 1-hour average meeting duration

Calculate:
1. Concurrent participants at peak
2. SFU servers needed (300 ppl/server)
3. Total SFU bandwidth
4. Recording storage per day
5. 30-day storage requirement

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. CONCURRENT PARTICIPANTS:
   - DAU: 5M × 10% = 500K daily active users
   - Peak concurrent: 500K × 5% = 25K participants

2. SFU SERVERS:
   - Participants per server: 300
   - Servers needed: 25K ÷ 300 = 84 servers
   - With redundancy: ~100 servers

3. SFU BANDWIDTH:
   - Avg meeting size: 4 people
   - Upload per user: 2.5 Mbps (720p weighted avg)
   - Download per user: 3 × 2.5 = 7.5 Mbps
   - Total upload: 25K × 2.5 = 62.5 Gbps
   - Total download: 25K × 7.5 = 187.5 Gbps
   - Total SFU: 250 Gbps

4. RECORDING STORAGE (PER DAY):
   - Meetings per day: 500K DAU ÷ 4 (avg size) = 125K meetings
   - Recorded: 125K × 15% = 18,750 meetings
   - Duration: 1 hour
   - Size: 400 MB/hour (720p)
   - Total: 18,750 × 400 MB = 7,500 GB = 7.5 TB/day

5. 30-DAY STORAGE:
   - 7.5 TB × 30 = 225 TB
```
</details>

---

### Problem 2: Webinar Platform (Large Meetings)
```
Given:
- 50 simultaneous webinars at peak
- Average attendance: 500 people
- 1 presenter (1080p @ 4 Mbps)
- Viewers receive: 720p @ 2.5 Mbps
- 80% of webinars recorded
- Average duration: 1.5 hours

Calculate:
1. Total concurrent participants
2. Bandwidth from SFU to viewers
3. SFU servers needed
4. Daily recording storage
5. Why webinars are different from regular meetings

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. CONCURRENT PARTICIPANTS:
   - 50 webinars × 500 people = 25,000 participants

2. BANDWIDTH (SFU → Viewers):
   - Each viewer downloads 1 stream (presenter only)
   - 25,000 viewers × 2.5 Mbps = 62,500 Mbps = 62.5 Gbps
   - Upload to SFU: 50 presenters × 4 Mbps = 200 Mbps (negligible)
   - Total SFU egress: 62.5 Gbps

3. SFU SERVERS:
   - Webinars use different model (1-to-many)
   - Each webinar = 1 incoming + 500 outgoing
   - Server can handle ~10 webinars (network-bound)
   - Servers needed: 50 ÷ 10 = 5 servers
   
   (Much fewer servers than regular meetings because
    it's broadcast, not mesh!)

4. DAILY RECORDING STORAGE:
   - Assume 200 webinars per day
   - Recorded: 200 × 80% = 160 webinars
   - Duration: 1.5 hours
   - Size: 800 MB/hour (1080p) × 1.5 = 1.2 GB per webinar
   - Total: 160 × 1.2 GB = 192 GB/day

5. WHY DIFFERENT:
   - Webinars are 1-to-many (broadcast model)
   - Viewers don't upload (except Q&A/chat)
   - CDN can be used for delivery (edge caching)
   - Much more efficient than mesh topology
   - Can scale to 10,000+ viewers per webinar with CDN
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting Simulcast**
```
✗ BAD:  "10 people × 2.5 Mbps download = 25 Mbps per user"
✓ GOOD: "With simulcast: 1 × 2.5 Mbps (speaker) + 9 × 0.5 Mbps (thumbnails) = 7 Mbps"
```

### Mistake 2: **Underestimating TURN Costs**
```
✗ BAD:  "TURN is just a fallback, ignore it"
✓ GOOD: "10-15% of traffic uses TURN × 2× bandwidth = major cost driver!"
```

### Mistake 3: **Ignoring Peak Traffic**
```
✗ BAD:  "Average of 5M concurrent users"
✓ GOOD: "Average 5M, but peak at hour start (2×) = 10M. Size for peak!"
```

### Mistake 4: **Wrong Architecture Choice**
```
✗ BAD:  "Use P2P for all calls (save server cost)"
✓ GOOD: "P2P for 1-on-1, SFU for groups (upload bandwidth limited on client)"
```

### Mistake 5: **Not Considering Regional Distribution**
```
✗ BAD:  "Single datacenter with 40K servers"
✓ GOOD: "Multi-region: US (10K), EU (10K), APAC (10K), etc. for latency"
```

---

## 🎁 Bonus: Video Calling Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║        VIDEO CALLING SCALE ESTIMATION CHEAT SHEET      ║
╚════════════════════════════════════════════════════════╝

BANDWIDTH ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Audio: 50 Kbps (Opus)
• 360p: 500 Kbps
• 480p (SD): 1 Mbps
• 720p (HD): 2.5 Mbps ← Most common
• 1080p (FHD): 4 Mbps
• Screen Share: 1-2 Mbps

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Concurrent Users = DAU × Peak%
SFU Upload = Participants × Bitrate
SFU Download = Participants × (Size - 1) × Bitrate
SFU Servers = Concurrent ÷ Participants_per_Server
Recording Storage = Meetings × Duration × 400MB/hour

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Peak Concurrent: 3-5% of DAU
• Avg Meeting Size: 3-5 people
• 1-on-1 Meetings: 40-50% of total
• Recording Rate: 20-30% of meetings
• TURN Usage: 10-15% (expensive!)
• Participants per SFU: 300-500

ARCHITECTURE DECISIONS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1-on-1: P2P (direct connection, no server)
3-10 people: SFU (selective forwarding)
10-50 people: SFU with simulcast
50+ people: SFU + MCU hybrid or webinar mode

QUICK ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:   100K users,  5K concurrent,   20 SFU servers
Medium:  5M users,    250K concurrent, 1K SFU servers
Large:   100M users,  5M concurrent,   20K SFU servers
Massive: 300M users,  10M concurrent,  40K SFU servers

COST DRIVERS (by $$):
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. TURN bandwidth (💰💰💰) - 10-15% users × 2× relay
2. SFU servers (💰💰) - 40K servers for Zoom scale
3. Recording storage (💰) - 11 PB for 90 days
4. Signaling servers (💵) - Lightweight, cheap

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify requirements (5 min)
   → Users? Quality? Group size? Recording?

2. High-level estimates (5 min)
   → Concurrent users, Bandwidth, Servers
   → "Let me do some quick napkin math..."

3. Architecture choice (10 min)
   → P2P vs SFU vs MCU
   → WebRTC, signaling, TURN/STUN
   → "For this scale, we need SFU with simulcast..."

4. Deep dives (20 min)
   → Recording, scaling, multi-region
   → "TURN is expensive, so we optimize for P2P success..."

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can 1 SFU handle 300 participants? YES (network-bound)
✓ Is 10 Mbps enough for HD call? YES (2.5 upload + 7.5 download)
✓ Can use P2P for groups? NO (n² connections, upload limited)
✓ Is 11 PB storage for recordings reasonable? YES (Zoom scale)

✗ Can single server handle 10K participants? NO (need SFU cluster)
✗ Can ignore TURN servers? NO (10-15% of users need it)
✗ Is 1080p the default? NO (most use 720p to save bandwidth)
✗ Can single region serve global users? NO (latency >200ms)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these systems and practice the full estimation:

1. **Online Education Platform** (like Udemy Live)
2. **Telemedicine System** (HIPAA-compliant video)
3. **Gaming Voice Chat** (like Discord voice channels)
4. **Virtual Events Platform** (conferences, exhibitions)
5. **Customer Support Video** (1-on-1 support calls)

Use the blank template above and time yourself: **Can you complete it in 5 minutes?**

---

**Remember**:
> "In video systems, BANDWIDTH is king. Everything else follows from the bits per second!"

**Now go crush those interviews!** 🚀

---

*Created with the POWER technique: Principles → Order → Write → Estimate → Round*
*Perfect for: FAANG interviews, Video Platform Design, WebRTC Architecture discussions*
