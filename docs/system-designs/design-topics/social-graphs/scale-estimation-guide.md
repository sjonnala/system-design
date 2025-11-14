# 🎯 Social Graphs System Design: Scale Estimation Masterclass

## The POWER Technique for Social Media Scale
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

Master the art of estimating for billion-user platforms like Facebook, Instagram, Twitter, and Reddit.

---

## 📊 PART 1: Users & Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 2B | Facebook-scale platform |
| | Monthly Active Users (MAU) | 1.5B | ~75% of total users |
| | Daily Active Users (DAU) | 500M | ~33% engagement (Pareto!) |
| | Power Users | 50M | ~10% create 90% content |
| **Content Generation** | Posts/day | 50M | 1 in 10 DAU posts daily |
| | Comments/day | 200M | 4× posts (engagement) |
| | Likes/day | 2B | 40× posts (easy action) |
| | Follows/day | 10M | Relationship changes |
| **Time Distribution** | Peak Hours | 6 hours/day | Morning + evening |
| | Peak Traffic Multiplier | 3x | Higher for social (events) |
| **Feed Behavior** | Feed refreshes/user/day | 20 | Check feed frequently |
| | Posts per feed | 50 | Typical feed page size |
| **Media** | Posts with images | 80% | Visual-first platform |
| | Posts with video | 20% | Growing trend |
| | Avg image size | 500 KB | Compressed JPEG |
| | Avg video size | 5 MB | Short-form video |

---

## 🧮 PART 2: The "Billion User Calculator" - Mental Math for Massive Scale

### Rule #1: **The Billion-Second Ladder**
```
Remember these anchors for social scale:
• 1 Million = 10^6     (6 zeros)  → "M for Million"
• 1 Billion = 10^9     (9 zeros)  → "B for Billion"
• 1 Day = ~100K seconds (86,400) → "Day ≈ 10^5 sec"
• 1 Month = ~2.5M seconds        → "Month ≈ 2.5 * 10^6"
• 1 Year = ~30M seconds          → "Year ≈ 3 * 10^7"

CRITICAL for social:
• 500M DAU × 20 feed refreshes = 10B feed requests/day
• 10B ÷ 100K = 100,000 QPS (100K QPS!)
```

### Rule #2: **The Social Media Multipliers**
```
Content Engagement Ratios (Remember these!):
• 1 Post → 4 Comments → 40 Likes (1:4:40 ratio)
• 1 Influencer Post → 10,000 Likes (power law!)
• Fan-out Factor: Avg 200 followers per user
  → 1 post = 200 feed updates (fan-out on write)
```

### Rule #3: **The Media Storage Explosion**
```
Images dominate storage:
• Text post: ~1 KB
• Image post: ~500 KB (500× larger!)
• Video post: ~5 MB (5000× larger!)

80% of posts have images:
→ Storage is dominated by media, not text!
```

---

## 📈 PART 3: Social Graphs Scale Math Template

```
┌─────────────────────────────────────────────────────────┐
│     THE SOCIAL MEDIA NAPKIN MATH - Template             │
└─────────────────────────────────────────────────────────┘

STEP 1: USER SCALE ESTIMATION
───────────────────────────
Total Users:                 [____] B
Monthly Active (MAU):        [____] B (75% of total)
Daily Active (DAU):          [____] M (33% of total)

STEP 2: CONTENT GENERATION
───────────────────────────
Posts/day:                   [____] M
Comments/day:                [____] M (4× posts)
Likes/day:                   [____] M (40× posts)
Follows/day:                 [____] M

STEP 3: TRAFFIC ESTIMATION
───────────────────────────
Feed Refreshes/day:          DAU × 20 = [____] B
Feed QPS:                    Refreshes ÷ 100K = [____] K

Post Writes/day:             [____] M
Post Write QPS:              Writes ÷ 100K = [____]

Like Writes/day:             [____] M
Like Write QPS:              Writes ÷ 100K = [____] K

Peak Traffic:                Average QPS × 3 = [____]

STEP 4: STORAGE ESTIMATION
───────────────────────────
Text Storage:
  Posts/day:                 [____] M
  Size per post:             1 KB
  Daily:                     [____] M × 1 KB = [____] GB

Image Storage (80% of posts):
  Images/day:                [____] M × 0.8 = [____] M
  Size per image:            500 KB
  Daily:                     [____] M × 500 KB = [____] TB

Video Storage (20% of posts):
  Videos/day:                [____] M × 0.2 = [____] M
  Size per video:            5 MB
  Daily:                     [____] M × 5 MB = [____] TB

Total Daily Storage:         Text + Image + Video = [____] TB
Yearly Storage:              Daily × 365 = [____] PB
5-Year Storage:              Yearly × 5 = [____] PB

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────
Feed Read Bandwidth:
  Feed QPS × Avg Feed Size (50 posts × 1KB metadata)
  = [____] K QPS × 50 KB = [____] GB/s

Media Bandwidth (images):
  80% of feed has images
  = [____] K QPS × 40 images × 500 KB = [____] TB/s
  → MUST USE CDN!

STEP 6: GRAPH COMPLEXITY
───────────────────────────
Total Users:                 [____] B
Avg Followers:               200
Total Follow Relationships:  [____] B × 200 = [____] B edges

Graph Storage:
  Each edge: 16 bytes (follower_id + following_id)
  Total: [____] B × 16 B = [____] TB
```

---

## 💾 PART 4: Social Graphs Filled Template (Instagram/Facebook Scale)

```
┌─────────────────────────────────────────────────────────┐
│         SOCIAL GRAPHS - NAPKIN MATH SOLUTION            │
└─────────────────────────────────────────────────────────┘

STEP 1: USER SCALE ESTIMATION
───────────────────────────
Total Users:                 2 B
Monthly Active (MAU):        1.5 B (75%)
Daily Active (DAU):          500 M (33%)

STEP 2: CONTENT GENERATION
───────────────────────────
Posts/day:                   50 M (10% of DAU post)
Comments/day:                200 M (4× posts)
Likes/day:                   2 B (40× posts)
Follows/day:                 10 M (relationship changes)

STEP 3: TRAFFIC ESTIMATION
───────────────────────────
Feed Refreshes/day:          500M × 20 = 10 B
Feed QPS:                    10B ÷ 100K = 100,000 QPS

Post Writes/day:             50 M
Post Write QPS:              50M ÷ 100K = 500 QPS

Like Writes/day:             2 B
Like Write QPS:              2B ÷ 100K = 20,000 QPS

Peak Traffic:                100K QPS × 3 = 300,000 QPS

STEP 4: STORAGE ESTIMATION
───────────────────────────
Text Storage:
  Posts/day:                 50 M
  Size per post:             1 KB (text + metadata)
  Daily:                     50M × 1 KB = 50 GB

Image Storage (80% of posts):
  Images/day:                50M × 0.8 = 40 M
  Size per image:            500 KB
  Daily:                     40M × 500 KB = 20 TB

Video Storage (20% of posts):
  Videos/day:                50M × 0.2 = 10 M
  Size per video:            5 MB
  Daily:                     10M × 5 MB = 50 TB

Total Daily Storage:         50 GB + 20 TB + 50 TB ≈ 70 TB/day
Yearly Storage:              70 TB × 365 ≈ 25 PB/year
5-Year Storage:              25 PB × 5 = 125 PB total

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────
Feed Read Bandwidth (metadata only):
  100K QPS × 50 posts × 1 KB = 5 GB/s

Media Bandwidth (images via CDN):
  100K QPS × 40 images/feed × 500 KB = 2 TB/s
  → CDN serves 99%, origin serves 1% = 20 GB/s origin

Video Bandwidth:
  Assume 10% of users watch videos
  = 10K QPS × 5 MB/video = 50 GB/s

STEP 6: GRAPH COMPLEXITY
───────────────────────────
Total Users:                 2 B
Avg Followers:               200
Total Follow Relationships:  2B × 200 = 400 B edges

Graph Storage:
  Each edge: 16 bytes
  Total: 400B × 16 B = 6.4 TB
```

---

## 🧠 PART 5: Mental Math Techniques for Billion-User Scale

### **Technique 1: The "Pareto Social Rule"**
```
In social media, Pareto is EVERYWHERE:
• 10% of users create 90% of content
• 1% of users are influencers (>10K followers)
• 20% of posts get 80% of engagement
• 1% of videos get 50% of views (viral content)

Use this for:
- Cache sizing: Cache top 1% of content = 50% cache hit
- Fan-out optimization: Treat 1% differently
```

### **Technique 2: The "Engagement Pyramid"**
```
VISUALIZE this pyramid:

         /\        1 POST
        /  \
       /----\      4 COMMENTS
      /------\
     /--------\    40 LIKES
    /----------\
   /------------\  200 VIEWS

Ratio: 1:4:40:200 (post:comment:like:view)
MEMORIZE THIS - it drives your traffic estimates!
```

### **Technique 3: The "Fan-out Explosion Calculator"**
```
Fan-out on Write Problem:
  Influencer with 10M followers posts
  → Must write to 10M timelines
  → At 1ms per write = 10,000 seconds = 2.7 HOURS!

Solution:
  Hybrid approach - celebrities use fan-out on read
  Regular users (<10K followers) use fan-out on write

Threshold Calculation:
  If write takes >100ms, switch to fan-out on read
  100ms ÷ 1ms per write = 100 followers max for fan-out write
  (In practice, use 10K as threshold)
```

### **Technique 4: The "Media Dominance Rule"**
```
EMOTION TRIGGER: "Text is FREE, Media COSTS!"

Example:
  1 billion text posts = 1 TB (cheap!)
  1 million images = 500 GB (expensive!)
  10,000 videos = 50 GB (very expensive!)

Always separate:
  - Metadata storage (cheap, in DB)
  - Media storage (expensive, in S3/CDN)
```

---

## 🎨 PART 6: The Visual Scale Map

```
                    📱 SOCIAL MEDIA PLATFORM
                            |
        ┌───────────────────┼───────────────────┐
        |                   |                   |
    👥 USERS           📊 CONTENT          🔧 INFRA
        |                   |                   |
    ┌───┴───┐         ┌─────┴─────┐       ┌─────┴─────┐
   DAU   MAU       Posts  Likes     Servers  Storage
  500M   1.5B      50M    2B        10K     125PB
```

**Memory Trigger**: Think **"U.C.I."** = Users, Content, Infrastructure

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **DAU** | 500M | Core active user base |
| **Feed Read QPS** | 100,000 | Size your feed infrastructure |
| **Post Write QPS** | 500 | Database write capacity |
| **Like Write QPS** | 20,000 | Hot-path optimization |
| **Peak QPS** | 300,000 | Infrastructure for events/launches |
| **Daily Storage** | 70 TB | Media dominates (99% of storage) |
| **5-Year Storage** | 125 PB | Petabyte-scale storage planning |
| **CDN Bandwidth** | 2 TB/s | Global media delivery |
| **Graph Edges** | 400B | Social graph complexity |

---

## 💡 Pro Architect Tips for Social Scale

### **Tip 1: The "1% Rule" for Caching**
```
Question: How much cache do we need for 2B users?

BAD Answer: Cache all user profiles = 2B × 1KB = 2TB cache
GOOD Answer: Cache hot 1% = 20M × 1KB = 20GB cache
            → Serves 50% of traffic!

Principle: Cache top 1% of content, NOT all content
```

### **Tip 2: The "Write Amplification" Trap**
```
Question: User with 10M followers posts. How many writes?

Fan-out on Write: 10M writes (SLOW!)
Fan-out on Read: 1 write + 10M reads (cached)

Decision: Use fan-out on read for >10K followers
```

### **Tip 3: The "Eventual Consistency" Mindset**
```
STRONG consistency needed:
  - Follow/unfollow
  - Account balance
  - Payments

EVENTUAL consistency OK:
  - Like counts (347 vs 348 doesn't matter)
  - Follower counts
  - Feed updates (few seconds delay OK)

Trade-off: Speed vs Correctness
For social media → Speed wins!
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Twitter-like Platform
```
Given:
- 200M DAU
- Avg 5 tweets/day per user
- 100:1 read-write ratio
- Avg 200 followers per user
- Tweet: 280 chars + metadata = 1 KB

Calculate:
1. Tweet write QPS
2. Timeline read QPS
3. Fan-out on write: writes per tweet
4. Daily storage
5. 1-year storage

[Try it yourself!]
```

<details>
<summary>Answer</summary>

```
1. TWEET WRITE QPS:
   Tweets/day = 200M × 5 = 1B tweets
   Write QPS = 1B ÷ 100K = 10,000 QPS

2. TIMELINE READ QPS:
   Reads = 1B × 100 = 100B reads/day
   Read QPS = 100B ÷ 100K = 1M QPS (!!!)

3. FAN-OUT ON WRITE:
   Each tweet → 200 followers
   Total writes = 1B tweets × 200 = 200B timeline writes
   Write QPS = 200B ÷ 100K = 2M QPS (INSANE!)
   → Must use hybrid approach!

4. DAILY STORAGE:
   1B tweets × 1 KB = 1 TB/day (metadata only)
   If 50% have images: +500M × 500KB = 250 TB
   Total: ~250 TB/day

5. 1-YEAR STORAGE:
   250 TB × 365 = ~90 PB/year
```
</details>

---

### Problem 2: Instagram-like Platform
```
Given:
- 500M DAU
- 1 post/user/day
- 80% posts have 1 image (500 KB)
- 20% posts have 1 video (5 MB)
- Each user views 100 posts/day
- Avg post has 50 likes

Calculate:
1. Post write QPS
2. Like write QPS
3. Feed read QPS
4. Daily media storage
5. CDN bandwidth requirements

[Try it yourself!]
```

<details>
<summary>Answer</summary>

```
1. POST WRITE QPS:
   Posts/day = 500M × 1 = 500M
   Write QPS = 500M ÷ 100K = 5,000 QPS

2. LIKE WRITE QPS:
   Likes/day = 500M posts × 50 = 25B likes
   Write QPS = 25B ÷ 100K = 250,000 QPS

3. FEED READ QPS:
   Feed requests = 500M × views = assume 20 refreshes/day
   = 500M × 20 = 10B requests
   Read QPS = 10B ÷ 100K = 100,000 QPS

4. DAILY MEDIA STORAGE:
   Images: 500M × 0.8 × 500KB = 200 TB
   Videos: 500M × 0.2 × 5MB = 500 TB
   Total: ~700 TB/day

5. CDN BANDWIDTH:
   Assume each feed request loads 20 images
   = 100K QPS × 20 images × 500KB = 1 TB/s
   (99% CDN hit → origin serves 10 GB/s)
```
</details>

---

### Problem 3: Reddit-like Platform (Text-Heavy)
```
Given:
- 50M DAU
- 10 posts/user/day
- 5:1 read-write ratio (lower than visual platforms)
- Avg post: 500 chars text + metadata = 2 KB
- 90% text, 10% have 1 image

Calculate:
1. Post write QPS
2. Post read QPS
3. Daily storage (text vs media)
4. Why is storage so much lower than Instagram?

[Try it yourself!]
```

<details>
<summary>Answer</summary>

```
1. POST WRITE QPS:
   Posts/day = 50M × 10 = 500M
   Write QPS = 500M ÷ 100K = 5,000 QPS

2. POST READ QPS:
   Reads = 500M × 5 = 2.5B reads/day
   Read QPS = 2.5B ÷ 100K = 25,000 QPS

3. DAILY STORAGE:
   Text: 500M × 2KB = 1 TB/day
   Images (10%): 50M × 500KB = 25 TB/day
   Total: ~26 TB/day

4. WHY LOWER?
   Reddit is text-first (90% text)
   Instagram is visual-first (80% images/video)

   Reddit: 26 TB/day
   Instagram: 700 TB/day
   → 27× difference due to media!
```
</details>

---

## 🎁 Bonus: Platform Comparison Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║           SOCIAL MEDIA SCALE COMPARISON                ║
╚════════════════════════════════════════════════════════╝

Platform      DAU      Posts/Day  Storage/Day  Key Feature
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Twitter       200M     1B         250 TB       Real-time feed
Instagram     500M     500M       700 TB       Visual-first
Facebook      2B       1B         1 PB         Social graph
Reddit        50M      500M       26 TB        Text-heavy
TikTok        1B       500M       2 PB         Video-first

KEY INSIGHTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Text platforms (Reddit): ~25-50 TB/day
• Image platforms (Instagram): ~500-700 TB/day
• Video platforms (TikTok): ~1-2 PB/day
• Mixed platforms (Facebook): ~1 PB/day

STORAGE SCALING:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Text:  1 KB per post
Image: 500 KB per image (500× larger)
Video: 5 MB per video (5000× larger)
```

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting the Fan-out Problem**
```
✗ BAD:  "Fan-out on write for all users"
✓ GOOD: "Fan-out on write for <10K followers,
         fan-out on read for influencers"
```

### Mistake 2: **Treating All Writes the Same**
```
✗ BAD:  "We have 20K write QPS total"
✓ GOOD: "We have:
         - 500 post writes/sec (slow, complex)
         - 20K like writes/sec (fast, simple)
         Different optimization strategies!"
```

### Mistake 3: **Ignoring Media Dominance**
```
✗ BAD:  "Storage = posts × size = 1B × 1KB = 1TB"
✓ GOOD: "Storage = text (1TB) + images (200TB) + videos (500TB)
         Media is 99.5% of storage!"
```

### Mistake 4: **Underestimating Peak Traffic**
```
✗ BAD:  "Average QPS = 100K"
✓ GOOD: "Average = 100K, Peak = 300K (during events),
         Launch peak = 500K (new feature release)"
```

### Mistake 5: **Forgetting Graph Complexity**
```
✗ BAD:  "Follow/unfollow is just a DB write"
✓ GOOD: "Follow/unfollow affects:
         - Graph database (relationship)
         - User's following list
         - Target's follower list
         - Feed generation logic
         - Recommendation model
         Multiple systems must stay in sync!"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
PLATFORM: ___________________
Target Scale: Twitter / Instagram / Facebook / Reddit (circle one)

STEP 1: USER SCALE
───────────────────────────
Total Users:              [____] B
DAU:                      [____] M
Content Creators (10%):   [____] M

STEP 2: CONTENT GENERATION
───────────────────────────
Posts/day:                [____] M
Comments/day:             [____] M (4× posts)
Likes/day:                [____] M (40× posts)

STEP 3: TRAFFIC
───────────────────────────
Feed Requests/day:        DAU × 20 = [____] B
Feed QPS:                 [____] B ÷ 100K = [____] K

Post Write QPS:           [____]
Like Write QPS:           [____]
Peak QPS:                 [____] × 3 = [____]

STEP 4: STORAGE
───────────────────────────
Text/day:                 [____] TB
Images/day:               [____] TB (80% of posts)
Videos/day:               [____] TB (20% of posts)
Total/day:                [____] TB
5-year total:             [____] PB

STEP 5: FAN-OUT STRATEGY
───────────────────────────
Avg followers:            [____]
Threshold for hybrid:     [____] followers
Influencers (>threshold): [____] M

Fan-out on Write for:     [____] M users
Fan-out on Read for:      [____] M influencers

STEP 6: CACHING
───────────────────────────
Hot content (1%):         [____] M items
Cache size:               [____] GB
Expected hit rate:        [____] %
```

---

## 🎯 Final Wisdom: The Social Media Scale Manifesto

> **"At billion-user scale, EVERY decision is a trade-off"**

1. **Consistency vs Speed**: Choose speed for social media
2. **Storage vs Compute**: Media storage DOMINATES costs
3. **Fan-out on Write vs Read**: Hybrid approach is the ONLY way
4. **Cache Everything**: Top 1% of content = 50% of traffic
5. **CDN is NOT Optional**: It's REQUIRED for media

### The Golden Rules:
```
1. Pareto applies to EVERYTHING (10% users, 90% content)
2. Media is 99% of storage, 1% of requests
3. Likes are 10× more frequent than posts
4. Feed reads are 100× more frequent than writes
5. Influencers break all your assumptions
```

---

## 📚 Interview Success Checklist

```
Before the Interview:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
□ Memorize: DAU, QPS, Storage formulas
□ Memorize: 1:4:40 engagement ratio (post:comment:like)
□ Memorize: Fan-out threshold (10K followers)
□ Memorize: Media sizes (500KB image, 5MB video)
□ Practice: 3 scale estimation problems

During the Interview:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
□ Clarify scale: Twitter (200M) vs Facebook (2B)?
□ Clarify content: Text-heavy or visual-first?
□ State assumptions LOUDLY before calculating
□ Separate text storage from media storage
□ Discuss fan-out strategy (write vs read)
□ Mention peak traffic (3× average)
□ Round numbers aggressively (no decimals!)

Red Flags to Avoid:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
□ Don't forget media in storage calculations
□ Don't use fan-out on write for all users
□ Don't ignore the graph database for social
□ Don't treat all writes the same (posts vs likes)
□ Don't forget CDN for media delivery
```

---

**Remember the Social Media Mantra:**
> "Text is cheap, Media is expensive, Graph is complex, Fan-out is hard, Cache is king!"

**Now go crush those FAANG interviews!** 🚀

---

*Created with the POWER technique for billion-user platforms*
*Perfect for: Meta, Twitter, Instagram, TikTok, Reddit system design interviews*
