# 🎯 TinyURL System Design: Scale Estimation Masterclass

## The POWER Technique for Scale Math
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This is a **mental framework** you can apply to ANY system design problem.

---

## 📊 PART 1: Users & Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 500M | Similar to Twitter scale |
| | Daily Active Users (DAU) | 100M | ~20% of total (Pareto!) |
| | Power Users (Write Heavy) | 10M | ~10% create 90% content |
| | Read:Write Ratio | 100:1 | Most people click, few create |
| **Time Distribution** | Peak Hours | 8 hours/day | Concentrated usage pattern |
| | Peak Traffic Multiplier | 2x | Double the average load |
| **URL Operations** | URLs shortened/day | 10M | 100 URLs per DAU (1%) |
| | URL clicks/day | 1B | 100:1 read ratio |
| | Avg URL lifetime | 5 years | Long-lived links |

---

## 🧮 PART 2: The "Coffee Shop Calculator" - Your Mental Math Toolkit

### Rule #1: **The Power of 10 Ladder**
```
Remember these anchors:
• 1 Million = 10^6     (6 zeros)  → "M for Million"
• 1 Billion = 10^9     (9 zeros)  → "B for Billion"  
• 1 Day = ~100K seconds (86,400) → "Day ≈ 10^5 sec"
• 1 Month = ~2.5M seconds        → "Month ≈ 2.5 * 10^6"
• 1 Year = ~30M seconds          → "Year ≈ 3 * 10^7"
```

### Rule #2: **The Division Shortcut**
Instead of dividing by complex numbers:
```
✗ BAD:  1,000,000,000 ÷ 86,400
✓ GOOD: 1B ÷ 100K = 10,000 QPS
        (Just subtract the zeros: 9 zeros - 5 zeros = 4 zeros = 10K)
```

### Rule #3: **The Doubling Trick**
When calculating storage:
```
Don't multiply: 10M × 500 bytes
Instead think: ~5GB per day (10M is ~10^7, 500 ≈ 0.5K, so 5 * 10^9 bytes)
```

---

## 📈 PART 3: Quick Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│  🎯 THE NAPKIN MATH TEMPLATE - Universal System Design  │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Write Operations/Day:    [____] M
Read Operations/Day:     [____] M  
Read:Write Ratio:        [____]:1

→ Write QPS = Writes/Day ÷ 100K  = [____] 
→ Read QPS  = Reads/Day ÷ 100K   = [____]
→ Peak QPS  = Average QPS × 2     = [____]

STEP 2: STORAGE ESTIMATION  
───────────────────────────
Data Size per Item:      [____] bytes
Items Created/Day:       [____] M
Retention Period:        [____] years

→ Daily Storage = Items × Size      = [____] GB
→ 1 Year Storage = Daily × 365      = [____] TB
→ Total Storage = Yearly × Years    = [____] TB

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
→ Write Bandwidth = Write QPS × Size = [____] MB/s
→ Read Bandwidth  = Read QPS × Size  = [____] MB/s

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Using 80-20 Rule (Pareto!):
→ 20% of URLs = 80% of traffic
→ Cache Size = Total URLs × 0.2 × Item Size
```

---

## 💾 PART 4: TinyURL Filled Template

```
┌─────────────────────────────────────────────────────────┐
│         TINYURL SYSTEM - NAPKIN MATH SOLUTION           │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Write Operations/Day:    10 M (URL shortening)
Read Operations/Day:     1000 M (URL clicks)  
Read:Write Ratio:        100:1

→ Write QPS = 10M ÷ 100K     = 100 QPS
→ Read QPS  = 1000M ÷ 100K   = 10,000 QPS
→ Peak QPS  = 10K × 2        = 20,000 QPS

STEP 2: STORAGE ESTIMATION  
───────────────────────────
Data per URL:
  - Short URL (7 chars)      = 7 bytes
  - Long URL (avg 500 chars) = 500 bytes
  - Metadata (created, user) = 100 bytes
  - Total per URL            = ~600 bytes

Items Created/Day:         10 M URLs
Retention Period:          5 years

→ Daily Storage = 10M × 600B      = 6 GB/day
→ 1 Year Storage = 6GB × 365      = ~2.2 TB/year
→ 5 Year Storage = 2.2TB × 5      = ~11 TB total

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
→ Write Bandwidth = 100 QPS × 600B  = 60 KB/s  ≈ 0.5 Mbps
→ Read Bandwidth  = 10K QPS × 600B  = 6 MB/s   ≈ 50 Mbps

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Total URLs after 5 years = 10M/day × 365 × 5 = 18 Billion URLs

Using 80-20 Rule:
→ Hot URLs (20%) = 3.6 Billion URLs
→ Cache Size = 3.6B × 600 bytes = ~2 TB

But we only cache the most recent/popular:
→ Practical Cache = 1% of hot data = 20 GB (Redis)
```

---

## 🧠 PART 5: Mental Math Techniques You MUST Master

### **Technique 1: The "Powers of 2" Ladder**
*(For Storage Calculations)*
```
Memorize this progression:
2^10 = 1 KB   (1,024 bytes)
2^20 = 1 MB   (1 Million bytes)
2^30 = 1 GB   (1 Billion bytes)
2^40 = 1 TB   (1 Trillion bytes)

Pro Tip: Each step is ~1000× the previous
```

### **Technique 2: The "Seconds Shortcut"**
*(For QPS Calculations)*
```
EMOTION TRIGGER: "A day is almost 100K seconds!"

1 day     = 24 hrs × 3600s   ≈ 100,000 seconds (86.4K)
1 month   = 30 days           ≈ 2.5 million seconds
1 year    = 365 days          ≈ 30 million seconds

So: Daily requests ÷ 100K = Requests per second
```

### **Technique 3: The "Zero Game"**
*(For Quick Division)*
```
When dividing by millions/billions:
1. Count zeros in dividend (top)
2. Count zeros in divisor (bottom)  
3. Subtract: Result has (top - bottom) zeros

Example: 1,000,000,000 ÷ 100,000
         9 zeros       -  5 zeros  = 4 zeros = 10,000
```

### **Technique 4: The "Approximation Mantra"**
```
🎯 REPEAT THIS WITH EMOTION:
"I round to nearest 10, 100, 1K, 10K, 100K, 1M, 10M"

- 86,400 seconds? → 100K seconds ✓
- 2.2 TB?         → 2 TB ✓
- 18.25 Billion?  → 20 Billion ✓

NO ONE CARES about precision in system design!
```

---

## 🎨 PART 6: The Visual Mind Map Approach

```
                    🌐 TINYURL SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 SCALE          💾 STORAGE        🔧 COMPUTE
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
   DAU    QPS       Size    Time     Servers  Cache
   100M   10K       11TB    5yr       20      20GB
```

**Memory Trigger**: Think **"S.S.C."** = Scale, Storage, Compute

---

## 🏗️ PART 7: Domain Model for TinyURL

```java
// Think in terms of domain entities first!

@Entity
class UrlMapping {
    // THINK: What's the WRITE pattern?
    String shortCode;   // 7 chars → 62^7 = 3.5 Trillion URLs
    String longUrl;     // Variable, avg 500 bytes
    LocalDateTime createdAt;
    Long userId;
    Integer clickCount; // For analytics
    
    // Scale Insight: This drives our 11TB storage calc!
}

@Service  
class UrlShortener {
    // THINK: What's the READ pattern?
    // 100:1 read ratio → Cache is CRITICAL
    // 80% traffic hits 20% URLs → Cache those!
}
```

---

## 🎯 PART 8: The Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│  SYSTEM DESIGN SCALE ESTIMATION - 5 MIN RITUAL   │
└──────────────────────────────────────────────────┘

[ ] 1. Write down: DAU, Total Users, Read:Write Ratio
[ ] 2. Calculate QPS: Operations/day ÷ 100K
[ ] 3. Calculate Storage: Items × Size × Time
[ ] 4. Apply Pareto: 20% data = 80% traffic
[ ] 5. Double-check: Does this pass the "smell test"?
        (100 QPS writes is reasonable for TinyURL? YES!)
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Write QPS** | 100 | Determines DB write capacity |
| **Read QPS** | 10,000 | Determines cache/CDN needs |
| **Peak QPS** | 20,000 | Size your infrastructure |
| **Storage (5yr)** | 11 TB | Database sizing |
| **Cache Size** | 20 GB | Redis/Memcached sizing |
| **Bandwidth** | 50 Mbps read | Network planning |
| **URL Length** | 7 chars | 62^7 = 3.5T combinations |

---

## 💡 Pro Architect Tips

### **Tip 1: The Smell Test**
After calculations, ask:
- "Can 10 web servers handle 10K QPS?" → YES (each does 1K)
- "Is 11TB storage reasonable for 5 years?" → YES
- "Does 100 URL creations/sec sound right?" → YES

### **Tip 2: The Comparison Anchor**
Always compare to known systems:
- "TinyURL ≈ 1/10th of Twitter traffic"
- "Storage ≈ Storing 100M songs"

### **Tip 3: Start with Constraints**
Always ask first:
1. How many users?
2. How many requests per user?
3. How long to store data?

---

## 🎓 Professor's Final Wisdom

> **"In system design, WRONG but LOGICAL beats RIGHT but UNEXPLAINED"**

Your interviewer doesn't have a calculator either! They want to see:
1. ✅ Clear assumptions
2. ✅ Logical progression
3. ✅ Awareness of trade-offs
4. ✅ Order of magnitude correctness

**NOT NEEDED:**
- ❌ Exact numbers
- ❌ Complex formulas
- ❌ Memorized statistics

---

## 🔁 Repetition Backed by Emotion (Your Power Principle!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"A day has 100K seconds - I can divide anything!"*
2. *"Pareto's 80-20 rule - 20% of URLs get 80% of clicks!"*
3. *"Scale = Traffic + Storage + Compute - that's it!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say: "So we have 10 million writes per day, that's about 100 per second..."

---

## 📚 Quick Reference: Common System Scale Benchmarks

| **System Type** | **DAU** | **Write QPS** | **Read QPS** | **Storage (1yr)** |
|-----------------|---------|---------------|--------------|-------------------|
| TinyURL | 100M | 100 | 10K | 2.2 TB |
| Twitter | 200M | 6K | 300K | 10+ PB |
| Instagram | 500M | 5K | 50K | 100+ PB |
| WhatsApp | 2B | 1M | 10M | 50+ PB |
| Netflix | 200M | 100 | 1M | 10+ PB |

---

## 🔧 Practical Application: Adapting This Template

### For a **Social Media Feed** (like Twitter):
```
STEP 1: TRAFFIC
- Write: Posts/day (tweets, likes, follows)
- Read: Timeline refreshes, notifications
- Ratio: Usually 100:1 to 1000:1

STEP 2: STORAGE
- Tweet: ~280 chars + metadata = ~500 bytes
- Images/Videos: Add 2MB avg per media post
- Apply retention: 10 years typical

STEP 3: BANDWIDTH
- Media dominates: Calculate separately
- Peak: Major events (elections, sports)

STEP 4: CACHE
- Hot tweets: Last 24 hours = 80% of reads
- User timelines: Pre-compute for active users
```

### For a **Video Streaming** (like YouTube):
```
STEP 1: TRAFFIC
- Write: Video uploads (fewer, but LARGE)
- Read: Video views (MASSIVE read traffic)
- Ratio: 10,000:1 or more

STEP 2: STORAGE
- Video: Multiple qualities (360p to 4K)
- Each video: 100MB to 10GB
- Apply compression: 50% reduction

STEP 3: BANDWIDTH
- THIS IS THE BOTTLENECK!
- 1M concurrent streams × 5 Mbps = 5 Tbps
- CDN is essential

STEP 4: CACHE
- Popular videos: 10% of library = 90% of views
- Cache at edge (CDN locations)
```

### For a **Messaging App** (like WhatsApp):
```
STEP 1: TRAFFIC
- Write: Messages sent (high frequency)
- Read: Messages delivered (slightly higher)
- Ratio: 1:1.5 (read-heavy but close)

STEP 2: STORAGE
- Message: ~1KB (text + metadata)
- Media: 500KB avg (images, voice notes)
- Retention: 30 days on server (then client-only)

STEP 3: BANDWIDTH
- Low per message, but HIGH volume
- Peak: Morning/evening commutes

STEP 4: CACHE
- Active conversations only
- Last 100 messages per chat
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Instagram-like Photo Sharing
```
Given:
- 500M DAU
- Each user uploads 0.5 photos/day
- Each user views 100 photos/day
- Photo size: 2MB
- Retention: 10 years

Calculate:
1. Write QPS
2. Read QPS
3. Storage after 1 year
4. Bandwidth requirements

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. WRITE QPS:
   - Photos/day = 500M × 0.5 = 250M photos
   - Write QPS = 250M ÷ 100K = 2,500 QPS

2. READ QPS:
   - Views/day = 500M × 100 = 50B views
   - Read QPS = 50B ÷ 100K = 500K QPS

3. STORAGE (1 year):
   - Daily = 250M × 2MB = 500 TB/day
   - Yearly = 500TB × 365 = ~180 PB/year

4. BANDWIDTH:
   - Write = 2.5K QPS × 2MB = 5 GB/s
   - Read = 500K QPS × 2MB = 1 TB/s (need CDN!)
```
</details>

---

### Problem 2: Uber-like Ride Sharing
```
Given:
- 100M DAU
- 2 rides/user/day
- Each ride generates 10 location updates
- Each update: 200 bytes
- Real-time tracking for 30 min avg
- Retention: 2 years

Calculate:
1. Location update QPS
2. Storage per year
3. Active rides at any moment

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. LOCATION UPDATE QPS:
   - Rides/day = 100M × 2 = 200M rides
   - Updates/day = 200M × 10 = 2B updates
   - QPS = 2B ÷ 100K = 20K QPS

2. STORAGE (1 year):
   - Daily = 2B × 200 bytes = 400 GB/day
   - Yearly = 400GB × 365 = ~146 TB/year

3. ACTIVE RIDES:
   - Avg ride duration = 30 min
   - Rides/day = 200M
   - Rides/min = 200M ÷ (24 × 60) = ~140K
   - Active at once = 140K × 30 = 4.2M concurrent
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting Peak Traffic**
```
✗ BAD:  "We need 100 QPS capacity"
✓ GOOD: "We need 100 QPS average, so 200-300 QPS for peaks"
```

### Mistake 2: **Ignoring Growth**
```
✗ BAD:  "11 TB storage for 5 years"
✓ GOOD: "11 TB if flat, but assuming 20% YoY growth = 18 TB"
```

### Mistake 3: **Over-precision**
```
✗ BAD:  "We need 2,247.32 GB of storage"
✓ GOOD: "We need about 2-2.5 TB of storage"
```

### Mistake 4: **Not Stating Assumptions**
```
✗ BAD:  "Storage is 10 TB" [how did you get this?]
✓ GOOD: "Assuming 500 bytes per URL, 10M URLs/day, 
         5 years retention: 10M × 500B × 365 × 5 ≈ 10 TB"
```

### Mistake 5: **Forgetting the 80-20 Rule**
```
✗ BAD:  "Cache all 18 billion URLs = 2 TB cache"
✓ GOOD: "Cache hot 20% of URLs = 20 GB cache (handles 80% traffic)"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
SYSTEM: ___________________

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Users (DAU):              [____] M
Operations per user/day:  [____]
Read:Write Ratio:         [____]:1

Total Writes/Day:         [____] M
Total Reads/Day:          [____] M

→ Write QPS = [____] M ÷ 100K = [____]
→ Read QPS  = [____] M ÷ 100K = [____]
→ Peak QPS  = [____] × 2       = [____]

STEP 2: STORAGE ESTIMATION
───────────────────────────
Data Size per Item:       [____] bytes
Items Created/Day:        [____] M
Retention Period:         [____] years

→ Daily Storage  = [____] M × [____] B  = [____] GB
→ Yearly Storage = [____] GB × 365      = [____] TB
→ Total Storage  = [____] TB × [____]   = [____] TB

STEP 3: BANDWIDTH
───────────────────────────
→ Write BW = [____] QPS × [____] B = [____] MB/s
→ Read BW  = [____] QPS × [____] B = [____] MB/s

STEP 4: CACHE (80-20 Rule)
───────────────────────────
→ Total Items = [____]
→ Hot Items (20%) = [____]
→ Cache Size = [____] × [____] B = [____] GB

SMELL TEST:
───────────────────────────
□ QPS reasonable? (compare to known systems)
□ Storage reasonable? (TB not PB for small systems)
□ Bandwidth achievable? (Gbps range typical)
□ Cache practical? (GB to TB range for Redis)
```

---

## 🎁 Bonus: Scale Estimation Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║           SYSTEM DESIGN SCALE CHEAT SHEET              ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 Day      = 100K seconds (~86.4K)
• 1 Million  = 10^6 (6 zeros)
• 1 Billion  = 10^9 (9 zeros)
• 1 GB       = 10^9 bytes
• 1 TB       = 10^12 bytes

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
QPS = Daily Operations ÷ 100K
Storage = Items/day × Item_Size × Days
Bandwidth = QPS × Item_Size
Cache = Total_Items × 0.2 × Item_Size

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Read:Write = 100:1 (content sites)
• Read:Write = 1:1 (messaging apps)
• Peak:Avg = 2x (plan for peaks)
• Hot:Cold = 80:20 (Pareto rule)
• DAU:Total = 20% (engagement)

QUICK ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:  1-100 QPS,    <1 TB,      1-10 servers
Medium: 100-10K QPS,  1-100 TB,   10-100 servers  
Large:  10K-1M QPS,   100TB-10PB, 100-10K servers
Huge:   >1M QPS,      >10PB,      >10K servers

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify requirements (5 min)
   → Users? Operations? Constraints?

2. High-level estimates (5 min)
   → QPS, Storage, Bandwidth
   → "Let me do some quick napkin math..."

3. System design (20 min)
   → Use estimates to drive design decisions
   → "Since we have 10K QPS reads, we need caching..."

4. Deep dives (10 min)
   → Based on scale, what's critical?
   → "At this scale, DB sharding becomes important..."

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can single server handle 1K QPS? YES
✓ Can Redis cache 100GB? YES
✓ Can Postgres store 10TB? YES (with partitioning)
✓ Can network handle 1Gbps? YES (typical datacenter)

✗ Can single server handle 100K QPS? NO
✗ Can Redis cache 10TB? NO (use distributed cache)
✗ Can single DB handle 1M QPS writes? NO (need sharding)
✗ Can single NIC handle 100Gbps? NO (need load balancer)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these systems and practice the full estimation:

1. **Netflix** - Video streaming platform
2. **Twitter** - Social media feed
3. **Uber** - Ride sharing with real-time tracking
4. **Dropbox** - File storage and sync
5. **Zoom** - Video conferencing

Use the blank template above and time yourself: **Can you complete it in 5 minutes?**

---

## 📚 Additional Resources

- **Books**: "Designing Data-Intensive Applications" by Martin Kleppmann
- **Courses**: Grokking the System Design Interview (Educative.io)
- **Practice**: LeetCode System Design problems
- **YouTube**: System Design Interview channel

---

**Remember**:
> "The goal isn't perfection - it's demonstrating systematic thinking and awareness of trade-offs at scale."

**Now go crush those interviews!** 🚀

---

*Created with the POWER technique: Principles → Order → Write → Estimate → Round*
*Perfect for: FAANG interviews, System Design rounds, Technical Architecture discussions*