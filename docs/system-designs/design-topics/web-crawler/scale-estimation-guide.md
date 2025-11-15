# 🎯 Web Crawler System Design: Scale Estimation Masterclass

## The POWER Technique for Scale Math
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This is a **mental framework** you can apply to ANY distributed system design problem.

---

## 📊 PART 1: Web Crawling Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **Crawl Scope** | Total web pages (indexed) | 50 Billion | Similar to Google's index size |
| | New pages per day | 5 Million | Growing web content |
| | Re-crawl frequency (popular) | 1 day | News sites, trending content |
| | Re-crawl frequency (normal) | 7 days | Regular websites |
| | Re-crawl frequency (archive) | 30 days | Old, stable content |
| **Distribution** | Popular pages (daily crawl) | 10% | News, social, trending |
| | Normal pages (weekly crawl) | 60% | Regular content |
| | Archive pages (monthly crawl) | 30% | Old, stable pages |
| **Page Characteristics** | Average page size | 100 KB | HTML + text + metadata |
| | Average links per page | 100 | Typical web page |
| | Avg fetch latency | 500ms | Network + server response |

---

## 🧮 PART 2: The "Coffee Shop Calculator" - Your Mental Math Toolkit

### Rule #1: **The Power of 10 Ladder**
```
Remember these anchors:
• 1 Thousand = 10^3    (3 zeros)  → "K for Thousand"
• 1 Million = 10^6     (6 zeros)  → "M for Million"
• 1 Billion = 10^9     (9 zeros)  → "B for Billion"
• 1 Day = ~100K seconds (86,400) → "Day ≈ 10^5 sec"
• 1 Week = ~600K seconds         → "Week ≈ 6 * 10^5"
• 1 Month = ~2.5M seconds        → "Month ≈ 2.5 * 10^6"
```

### Rule #2: **The Division Shortcut**
Instead of dividing by complex numbers:
```
✗ BAD:  5,000,000 ÷ 86,400
✓ GOOD: 5M ÷ 100K = 50 pages/sec
        (Just subtract the zeros: 6 zeros - 5 zeros = 1 zero = 50)
```

### Rule #3: **The Parallel Scaling Trick**
When calculating throughput:
```
Don't think: "How fast can ONE crawler go?"
Instead think: "How many crawlers do I need for target throughput?"

Target: 1,500 pages/sec
Single crawler: 50 pages/sec
Crawlers needed: 1,500 ÷ 50 = 30 crawler instances
```

---

## 📈 PART 3: Quick Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│  🎯 THE NAPKIN MATH TEMPLATE - Web Crawler Edition     │
└─────────────────────────────────────────────────────────┘

STEP 1: CRAWL RATE ESTIMATION
───────────────────────────
Total Pages to Maintain:   [____] B
Daily Recrawl (10%):       [____] M
Weekly Recrawl (60%):      [____] M/7 daily
Monthly Recrawl (30%):     [____] M/30 daily

→ Total Pages/Day = Daily + Weekly + Monthly = [____] M
→ Pages/Sec = Total Pages/Day ÷ 100K = [____] pages/sec
→ Peak Pages/Sec = Avg × 2 = [____] pages/sec

STEP 2: STORAGE ESTIMATION
───────────────────────────
Avg Page Size:             [____] KB
Total Pages:               [____] B
Storage Format:            Compressed (3:1 ratio)

→ Raw Storage = Pages × Size       = [____] TB
→ Compressed = Raw ÷ 3             = [____] TB
→ With Replicas (3x) = Compressed × 3 = [____] PB

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
→ Ingress (Download) = Pages/Sec × Avg Size = [____] MB/s = [____] Gbps
→ Egress (Storage writes) = Download ÷ 3 (compressed) = [____] MB/s

STEP 4: COMPUTE (CRAWLER INSTANCES)
───────────────────────────
Single Crawler Rate:       [____] pages/sec (limited by network)
Target Rate:               [____] pages/sec

→ Crawlers Needed = Target ÷ Single Rate = [____] instances
→ With 30% headroom = Crawlers × 1.3 = [____] instances
```

---

## 💾 PART 4: Web Crawler Filled Template

```
┌─────────────────────────────────────────────────────────┐
│      WEB CRAWLER SYSTEM - NAPKIN MATH SOLUTION          │
└─────────────────────────────────────────────────────────┘

STEP 1: CRAWL RATE ESTIMATION
───────────────────────────
Total Pages to Maintain:   50 Billion
Daily Recrawl (10%):       5 Billion pages (every day)
Weekly Recrawl (60%):      30 Billion pages ÷ 7 = 4.3 Billion/day
Monthly Recrawl (30%):     15 Billion pages ÷ 30 = 0.5 Billion/day

→ Total Pages/Day = 5B + 4.3B + 0.5B = ~10 Billion pages/day
→ Pages/Sec = 10B ÷ 100K = 100,000 pages/sec
→ Peak Pages/Sec = 100K × 2 = 200,000 pages/sec

Wait! This is TOO HIGH for real-world politeness constraints.

ADJUSTED (With Politeness):
──────────────────────────
Politeness: 1 req/sec per domain
Unique Domains: ~200 Million
Parallelizable Requests: 200M domains × 1 req/sec = 200M req/sec (theoretical max)

Realistic Target: 1,500 pages/sec (with 200M domains, plenty of headroom)
This means: 1,500 ÷ 200M = 0.0000075 req/sec per domain (very polite!)

→ Pages/Day = 1,500 × 100K = 150 Million pages/day
→ Pages/Month = 150M × 30 = 4.5 Billion pages/month

STEP 2: STORAGE ESTIMATION
───────────────────────────
Avg Page Size:
  - HTML content:            80 KB
  - Extracted text:          15 KB
  - Metadata (URL, timestamp): 5 KB
  - Total per page:          100 KB

Total Pages:               50 Billion
Storage Format:            Compressed gzip (3:1 ratio)

→ Raw Storage = 50B × 100KB    = 5,000 PB = 5 EB
→ Compressed = 5 EB ÷ 3        = 1.67 EB ≈ 1.7 EB
→ With Replicas (3x) = 1.7 EB × 3 = ~5 EB total

Reality Check: For a crawler like Google's scale
For smaller crawlers (1B pages): 100 TB compressed

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
Crawl Rate: 1,500 pages/sec
Avg Page Size: 100 KB

→ Ingress (Download) = 1,500 × 100KB  = 150 MB/s = 1.2 Gbps
→ Compressed writes = 150 MB/s ÷ 3    = 50 MB/s = 400 Mbps
→ Peak (2x) = 1.2 Gbps × 2            = 2.4 Gbps

STEP 4: COMPUTE (CRAWLER INSTANCES)
───────────────────────────
Single Crawler Rate:       50 pages/sec (network limited, politeness)
Target Rate:               1,500 pages/sec

→ Crawlers Needed = 1,500 ÷ 50      = 30 instances
→ With 30% headroom = 30 × 1.3      = 40 instances
→ CPU per instance: 4 cores, 16GB RAM
→ Total: 160 cores, 640GB RAM

STEP 5: URL FRONTIER ESTIMATION
───────────────────────────
Frontier Size (pending URLs): 50 Million URLs
URL Entry Size:
  - URL (avg 100 chars):     100 bytes
  - Priority score:          4 bytes
  - Timestamp:               8 bytes
  - Domain hash:             8 bytes
  - Total per URL:           120 bytes

→ Frontier Memory = 50M × 120 bytes = 6 GB
→ Redis cluster (with overhead) = 10 GB

STEP 6: DEDUPLICATION STORAGE
───────────────────────────
Total URLs (seen): 100 Billion (2x page count, including duplicates)
Storage per URL hash: 32 bytes (SHA-256 hash)

→ Hash Table = 100B × 32 bytes = 3.2 TB
→ Bloom Filter (10x compression) = 320 GB

Using Bloom filter + Redis:
→ Bloom Filter: 320 GB (in-memory)
→ Exact hashes: 3.2 TB (distributed Redis shards)
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
2^50 = 1 PB   (1 Quadrillion bytes)
2^60 = 1 EB   (1 Quintillion bytes)

Pro Tip: Each step is ~1000× the previous
```

### **Technique 2: The "Pages Per Second" Shortcut**
*(For Throughput Calculations)*
```
EMOTION TRIGGER: "A day is almost 100K seconds!"

Pages per day → Pages per second:
150M pages/day ÷ 100K = 1,500 pages/sec

Easy!
```

### **Technique 3: The "Parallel Domains" Insight**
*(For Understanding Politeness Constraints)*
```
KEY INSIGHT: With millions of unique domains, you can crawl
             1 page/sec from EACH domain simultaneously!

Domains: 200M
Rate per domain: 1 req/sec
Theoretical max: 200M req/sec

But realistic (with overhead): 1K - 100K pages/sec
```

### **Technique 4: The "Compression Multiplier"**
```
🎯 WEB CONTENT COMPRESSES ~3:1 with gzip

- Raw HTML: 100 KB
- Compressed: ~33 KB
- Storage needed: 1/3 of raw size

ALWAYS account for compression in storage estimates!
```

---

## 🎨 PART 6: The Visual Mind Map Approach

```
                🕷️ WEB CRAWLER SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 CRAWL          💾 STORAGE        🔧 COMPUTE
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
  Rate   Domains   Pages   Size    Instances  Memory
 1500/s   200M      50B    1.7EB     40      640GB
```

**Memory Trigger**: Think **"C.S.C."** = Crawl rate, Storage, Compute

---

## 🏗️ PART 7: Domain Model for Web Crawler

```python
# Think in terms of domain entities first!

@dataclass
class CrawledPage:
    # THINK: What's the STORAGE pattern?
    url: str                  # 100 bytes avg
    url_hash: str             # 32 bytes (SHA-256)
    html_content: str         # 80 KB avg (compressed: 27 KB)
    extracted_text: str       # 15 KB avg (compressed: 5 KB)
    metadata: dict            # timestamp, status, content_type
    outgoing_links: list[str] # 100 links × 100 bytes = 10 KB

    # Scale Insight: 50B pages × 100KB = 5 EB raw storage!

@dataclass
class URLFrontierEntry:
    # THINK: What's the QUEUE pattern?
    url: str                  # 100 bytes
    priority_score: float     # 4 bytes
    domain: str               # 50 bytes
    scheduled_time: datetime  # 8 bytes
    depth: int                # 4 bytes

    # Scale Insight: 50M URLs in frontier × 166 bytes = 8.3 GB

@dataclass
class CrawlerWorker:
    # THINK: What's the THROUGHPUT pattern?
    worker_id: str
    assigned_domains: list[str]  # Domain partitioning
    fetch_rate: int = 50         # pages/sec per worker

    # Scale Insight: 1,500 pages/sec ÷ 50 = 30 workers minimum
```

---

## 🎯 PART 8: The Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│ SYSTEM DESIGN SCALE ESTIMATION - 5 MIN RITUAL   │
│              WEB CRAWLER EDITION                 │
└──────────────────────────────────────────────────┘

[ ] 1. Clarify: Total pages? New pages/day? Re-crawl frequency?
[ ] 2. Calculate Pages/Sec: Pages/day ÷ 100K
[ ] 3. Adjust for Politeness: Limit by domains, not raw throughput
[ ] 4. Calculate Storage: Pages × Size × Compression
[ ] 5. Calculate Crawlers: Target rate ÷ Single crawler rate
[ ] 6. Smell Test: Does 1,500 pages/sec sound reasonable? YES!
        (Not 1M pages/sec - too high, politeness violated)
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Crawl Rate** | 1,500 pages/sec | Determines crawler fleet size |
| **Pages/Day** | 150 Million | Daily throughput capacity |
| **Total Pages** | 50 Billion | Index size (like Google) |
| **Storage** | 1.7 EB compressed | S3/HDFS sizing (for 50B pages) |
| **Bandwidth** | 1.2 Gbps ingress | Network capacity needed |
| **Crawler Instances** | 30-40 workers | Horizontal scaling |
| **URL Frontier** | 50M URLs, 10 GB | Redis/Kafka sizing |
| **Dedup Storage** | 320 GB Bloom + 3.2 TB hashes | Memory + distributed cache |

---

## 💡 Pro Architect Tips

### **Tip 1: The Politeness Reality Check**
After throughput calculations, ask:
- "Can I actually crawl this fast without violating politeness?" → Probably NOT!
- "With 200M domains at 1 req/sec each, what's my theoretical max?" → 200M req/sec
- "What's realistic with overhead?" → 1K-100K pages/sec

### **Tip 2: The Compression Savings**
Always remember:
- "HTML compresses ~3:1 with gzip"
- "5 EB raw → 1.7 EB compressed → HUGE cost savings!"

### **Tip 3: Start with Constraints**
Always ask first:
1. How many pages to index?
2. How often to re-crawl?
3. Politeness requirements? (req/sec per domain)
4. Storage retention period?

---

## 🎓 Professor's Final Wisdom

> **"In web crawler design, POLITENESS beats SPEED. A slow, respectful crawler is better than a fast, banned crawler!"**

Your interviewer wants to see:
1. ✅ Understanding of politeness constraints
2. ✅ Realistic throughput estimates (not theoretical max)
3. ✅ Storage compression awareness
4. ✅ Distributed system thinking (workers, sharding)

**NOT NEEDED:**
- ❌ Exact page counts
- ❌ Complex politeness algorithms
- ❌ Memorized web statistics

---

## 🔁 Repetition Backed by Emotion (Your Power Principle!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"Politeness limits my throughput - not bandwidth, not CPU!"*
2. *"Compression saves 3x on storage - always factor it in!"*
3. *"Distribute by domain - each worker owns specific domains!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say: "So with 200 million domains and 1 request/sec per domain, we have plenty of headroom for 1,500 pages/sec..."

---

## 📚 Quick Reference: Crawler Scale Benchmarks

| **Crawler Type** | **Pages Indexed** | **Crawl Rate** | **Storage** | **Workers** |
|------------------|-------------------|----------------|-------------|-------------|
| Small (Focused) | 1-10 Million | 10-50 pages/sec | 1-10 TB | 5-10 |
| Medium (Enterprise) | 100M - 1B | 100-500 pages/sec | 10-100 TB | 20-50 |
| Large (Google-scale) | 10B - 100B | 1K-10K pages/sec | 1-10 EB | 100-1000 |
| Specialized (News) | 10-100 Million | 50-200 pages/sec | 5-50 TB | 10-30 |

---

## 🔧 Practical Application: Adapting This Template

### For a **News Crawler** (high freshness requirement):
```
STEP 1: CRAWL RATE
- Focus: News sites (1M pages)
- Re-crawl: Every 15 minutes
- Pages/Day: 1M × (24 × 4) = 96M pages/day
- Pages/Sec: 96M ÷ 100K = 960 pages/sec

STEP 2: STORAGE
- News retention: 30 days
- Size: 1M × 100KB × 30 = 3 TB (uncompressed)
- Compressed: 1 TB

STEP 3: CRAWLERS
- Target: 960 pages/sec
- Single crawler: 50 pages/sec
- Needed: 20 crawlers
```

### For a **E-commerce Product Crawler**:
```
STEP 1: CRAWL RATE
- Products: 100M (Amazon, eBay, etc.)
- Re-crawl: Daily (prices change)
- Pages/Day: 100M
- Pages/Sec: 100M ÷ 100K = 1,000 pages/sec

STEP 2: STORAGE
- Product page: 50 KB (smaller than general web)
- Compressed: ~17 KB
- Total: 100M × 17KB = 1.7 TB

STEP 3: PARSING
- Extract: Price, title, reviews, images
- Structured data (JSON) storage
```

### For a **Social Media Crawler** (Twitter, Reddit):
```
STEP 1: CRAWL RATE
- Public posts: Real-time stream (10K posts/sec)
- User profiles: 500M (weekly re-crawl)
- Pages/Day: (10K × 86.4K) + (500M ÷ 7) = 864M + 71M = 935M

STEP 2: STORAGE
- Post: 1 KB (text, metadata)
- Daily: 864M × 1KB = 864 GB/day
- 30-day retention: 25 TB
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Academic Research Crawler
```
Given:
- Academic papers: 200M papers
- New papers/year: 5M
- Re-crawl: Quarterly (every 90 days)
- Paper size: 2 MB (PDF)
- Extract: Abstract, citations (50 KB)

Calculate:
1. Crawl rate (pages/sec)
2. Storage for PDFs (compressed)
3. Storage for extracted data
4. Number of crawler workers

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. CRAWL RATE:
   - Total papers: 200M
   - Re-crawl frequency: Quarterly
   - Papers/Quarter: 200M
   - Pages/Day = 200M ÷ 90 = 2.2M/day
   - Pages/Sec = 2.2M ÷ 100K = 22 pages/sec

2. STORAGE (PDFs):
   - Size per PDF: 2 MB
   - Compression: ~2:1 for PDFs
   - Compressed size: 1 MB
   - Total: 200M × 1MB = 200 TB

3. STORAGE (Extracted):
   - Per paper: 50 KB
   - Total: 200M × 50KB = 10 TB

4. CRAWLERS:
   - Target rate: 22 pages/sec
   - Single crawler: 10 pages/sec (PDFs are slower)
   - Needed: 22 ÷ 10 = 3 crawlers (round to 5 with headroom)
```
</details>

---

### Problem 2: Government Website Archiver
```
Given:
- Government sites: 10,000 sites
- Total pages: 50M
- Re-crawl: Monthly (compliance)
- Avg page: 150 KB
- Retention: 10 years (for legal compliance)
- Must keep historical versions

Calculate:
1. Crawl rate
2. Monthly storage delta
3. Total storage after 10 years
4. Bandwidth requirements

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. CRAWL RATE:
   - Pages: 50M
   - Re-crawl: Monthly
   - Pages/Day = 50M ÷ 30 = 1.67M/day
   - Pages/Sec = 1.67M ÷ 100K = 17 pages/sec

2. MONTHLY STORAGE:
   - Pages: 50M
   - Size: 150 KB
   - Compressed (3:1): 50 KB
   - Monthly: 50M × 50KB = 2.5 TB/month

3. TOTAL STORAGE (10 years):
   - Keeping all versions (12 × 10 = 120 snapshots)
   - Total: 2.5 TB × 120 = 300 TB
   - With deduplication (60% savings): 120 TB

4. BANDWIDTH:
   - Crawl: 17 pages/sec × 150 KB = 2.55 MB/s = 20 Mbps
   - Storage writes: 2.55 MB/s ÷ 3 (compressed) = 850 KB/s = 7 Mbps
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Ignoring Politeness**
```
✗ BAD:  "We can crawl 1M pages/sec with enough machines!"
✓ GOOD: "Politeness limits us to 1-5 req/sec per domain.
         With 200M domains, realistic max is 10K-50K pages/sec"
```

### Mistake 2: **Forgetting Compression**
```
✗ BAD:  "50B pages × 100KB = 5 EB storage needed"
✓ GOOD: "50B pages × 100KB = 5 EB raw, but compressed
         (3:1) = 1.7 EB, still massive but 3x cheaper!"
```

### Mistake 3: **Not Considering Re-crawl Frequency**
```
✗ BAD:  "Crawl 50B pages once, done!"
✓ GOOD: "Different pages have different freshness needs:
         - News: Daily (10%)
         - Normal: Weekly (60%)
         - Archive: Monthly (30%)"
```

### Mistake 4: **Over-precision with Domains**
```
✗ BAD:  "Exactly 247,382,941 domains to crawl"
✓ GOOD: "Roughly 200-250 million unique domains, call it 200M"
```

### Mistake 5: **Forgetting URL Frontier Size**
```
✗ BAD:  "Just crawl URLs from database"
✓ GOOD: "URL Frontier queue with 50M pending URLs,
         needs 10 GB RAM, distributed across Kafka/Redis"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
WEB CRAWLER: ___________________

STEP 1: CRAWL RATE ESTIMATION
───────────────────────────
Total Pages to Index:     [____] M/B
New Pages/Day:            [____] M
Re-crawl Frequency:
  - Hot (daily):          [____]% = [____] pages
  - Normal (weekly):      [____]% = [____] pages
  - Archive (monthly):    [____]% = [____] pages

Total Pages/Day:          [____] M
→ Pages/Sec = [____] M ÷ 100K = [____] pages/sec

Politeness Check:
  - Unique domains:       [____] M
  - Rate per domain:      [____] req/sec
  - Theoretical max:      [____] req/sec
  - Realistic target:     [____] pages/sec ✓

STEP 2: STORAGE ESTIMATION
───────────────────────────
Avg Page Size:            [____] KB
Total Pages:              [____] B
Compression Ratio:        3:1

→ Raw Storage = [____] B × [____] KB = [____] TB/PB/EB
→ Compressed = [____] ÷ 3             = [____] TB/PB/EB
→ With Replicas (3x) = [____] × 3     = [____] TB/PB/EB

STEP 3: BANDWIDTH
───────────────────────────
→ Ingress = [____] pages/sec × [____] KB = [____] MB/s = [____] Gbps
→ Egress (compressed) = [____] MB/s ÷ 3 = [____] MB/s

STEP 4: COMPUTE (CRAWLERS)
───────────────────────────
Single Crawler Rate:      [____] pages/sec
Target Rate:              [____] pages/sec

→ Crawlers = [____] ÷ [____]     = [____] instances
→ With headroom (1.3x) = [____]  = [____] instances

STEP 5: URL FRONTIER
───────────────────────────
Frontier Size:            [____] M URLs
Size per URL:             120 bytes

→ Frontier Memory = [____] M × 120B = [____] GB

STEP 6: DEDUPLICATION
───────────────────────────
Total URLs (seen):        [____] B
Hash size:                32 bytes

→ Hash table = [____] B × 32B = [____] TB
→ Bloom filter = [____] TB ÷ 10 = [____] GB

SMELL TEST:
───────────────────────────
□ Crawl rate respects politeness? (<1% of max)
□ Storage reasonable? (TB for small, PB for large, EB for Google-scale)
□ Bandwidth achievable? (Gbps range typical for medium crawlers)
□ Crawler count realistic? (10s-100s for medium scale)
```

---

## 🎁 Bonus: Web Crawler Scale Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║        WEB CRAWLER SCALE ESTIMATION CHEAT SHEET        ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 Day      = 100K seconds (~86.4K)
• Avg Page   = 100 KB (HTML + text)
• Compression = 3:1 (gzip)
• Politeness = 1-5 req/sec per domain
• Unique Domains ≈ 200M (global web)

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Pages/Sec = Pages/Day ÷ 100K
Storage = Pages × Page_Size ÷ 3 (compression)
Crawlers = Target_Rate ÷ Single_Crawler_Rate
Frontier Memory = Pending_URLs × 120 bytes

POLITENESS CONSTRAINTS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Rate Limit: 1-5 req/sec per domain (typical)
• Respect robots.txt: ALWAYS
• Crawl-delay: Honor directive
• Realistic Max: 1K-100K pages/sec (not millions!)

TYPICAL SCALES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:    1M-10M pages,    10-50 pages/sec,  1-10 TB
Medium:   100M-1B pages,   100-500 pages/sec, 10-100 TB
Large:    10B-100B pages,  1K-10K pages/sec,  1-10 EB

QUICK ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1B pages:
  - Storage (compressed): 100 TB ÷ 3 = 33 TB
  - Re-crawl weekly: 1B ÷ 7 ÷ 100K = 1.4 pages/sec
  - Crawlers needed: 1.4 ÷ 50 = 1 crawler (start small!)

10B pages (Google-scale):
  - Storage: 1 EB ÷ 3 = 333 PB
  - Re-crawl (mixed): ~10B/day ÷ 100K = 100K pages/sec (theoretical)
  - Realistic (politeness): 1K-10K pages/sec
  - Crawlers: 1K ÷ 50 = 20-200 instances

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify requirements (5 min)
   → How many pages? Re-crawl frequency? Freshness needs?

2. High-level estimates (5 min)
   → Pages/sec (with politeness!), Storage, Bandwidth
   → "Let me do some napkin math..."

3. System design (20 min)
   → URL Frontier, Crawlers, Storage, Deduplication
   → "Since we need politeness, we'll use domain-based queues..."

4. Deep dives (10 min)
   → Based on scale, what's critical?
   → "At this scale, deduplication is crucial - Bloom filter + Redis..."

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Crawl rate < 1% of theoretical max (politeness)? YES
✓ Storage in TB-PB range (not GB or EB for normal scale)? YES
✓ Compression applied (3:1 for HTML)? YES
✓ Crawler count reasonable (10s-100s for medium)? YES

✗ Crawling 1M pages/sec (without billions of domains)? NO
✗ Storing uncompressed HTML? NO (wasteful!)
✗ Single crawler handling 10K pages/sec? NO (network limits)
✗ No URL deduplication (will crawl duplicates)? NO (critical!)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these crawler types and practice the full estimation:

1. **E-commerce Price Tracker** - Track prices across 1M products
2. **Social Media Archiver** - Archive public posts from Twitter/Reddit
3. **Academic Paper Crawler** - Index 100M research papers
4. **Job Board Aggregator** - Crawl job postings from 1000 sites
5. **News Aggregator** - Real-time crawling of news websites

Use the blank template above and time yourself: **Can you complete it in 5-7 minutes?**

---

## 📚 Additional Resources

- **Books**:
  - "Web Crawling and Data Mining with Apache Nutch" by Zakir Laliwala
  - "Mining the Web: Discovering Knowledge from Hypertext Data" by Soumen Chakrabarti
- **Papers**:
  - "Mercator: A Scalable, Extensible Web Crawler" (Google)
  - "The Anatomy of a Large-Scale Hypertextual Web Search Engine" (Google)
- **Open Source**: Apache Nutch, Scrapy, Heritrix, Colly
- **Practice**: System Design Interview questions, real crawler implementations

---

**Remember**:
> "The goal isn't perfection - it's demonstrating systematic thinking, understanding of politeness constraints, and realistic capacity planning for distributed crawling at scale."

**Now go build that crawler!** 🕷️

---

*Created with the POWER technique: Principles → Order → Write → Estimate → Round*
*Perfect for: FAANG interviews, Distributed Systems rounds, Big Data Architecture discussions*
