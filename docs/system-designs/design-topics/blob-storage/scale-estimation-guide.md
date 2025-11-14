# 🎯 Blob Storage System Design: Scale Estimation Masterclass

## The STORAGE Technique for File Storage Scale
**(S)torage capacity → (T)hroughput → (O)perations → (R)edundancy → (A)vailability → (G)rowth → (E)stimation**

Master the art of estimating for petabyte-scale file storage platforms like Dropbox, Google Drive, and OneDrive.

---

## 📊 PART 1: Users & Storage Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 500M | Dropbox-scale platform |
| | Active Users (MAU) | 200M | ~40% active monthly |
| | Daily Active Users (DAU) | 50M | ~25% of MAU |
| | Paying Users | 20M | ~10% conversion rate |
| **Storage Per User** | Free tier | 2 GB | Limited free storage |
| | Paid tier | 2 TB average | Mix of plans (100GB to unlimited) |
| | Average across all | 100 GB | Weighted average |
| **File Characteristics** | Avg file size | 5 MB | Mix of docs, photos, videos |
| | Files per user | 2,000 | ~20 files per GB |
| | Large files (>100MB) | 10% | Videos, archives |
| **Operations** | Uploads per user/day | 5 | Mix of manual + auto-sync |
| | Downloads per user/day | 20 | Higher read than write |
| | Syncs per user/day | 50 | Background sync checks |
| **Data Patterns** | Deduplication ratio | 50% | Cross-user dedup |
| | Compression ratio | 30% | Additional savings |
| | Hot data (< 30 days) | 20% | Recent files |

---

## 🧮 PART 2: Storage Math Fundamentals

### Rule #1: **The Storage Unit Ladder**
```
MEMORIZE THIS:
• 1 KB = 10^3 bytes   = 1,000 bytes
• 1 MB = 10^6 bytes   = 1,000,000 bytes (1 million)
• 1 GB = 10^9 bytes   = 1,000,000,000 bytes (1 billion)
• 1 TB = 10^12 bytes  = 1,000,000,000,000 bytes (1 trillion)
• 1 PB = 10^15 bytes  = 1,000 TB (petabyte!)
• 1 EB = 10^18 bytes  = 1,000 PB (exabyte!)

Dropbox/Google Drive operate at EXABYTE scale!
```

### Rule #2: **The Storage Savings Stack**
```
Raw Storage = User Data
↓ Deduplication (50% savings)
= 0.5 × User Data
↓ Compression (30% savings on remaining)
= 0.5 × 0.7 × User Data = 0.35 × User Data

EXAMPLE: 100 GB user data → 35 GB actual storage
```

### Rule #3: **The Redundancy Multiplier**
```
Actual Storage = Logical Storage × Redundancy Factor

Redundancy strategies:
• 3× Replication: 3 copies of every block
• Erasure Coding (6+3): 1.5× overhead
• S3 Standard: ~3× (99.999999999% durability)

EXAMPLE: 35 GB logical → 105 GB physical (3× replication)
         or → 52.5 GB physical (erasure coding)
```

---

## 📈 PART 3: Blob Storage Scale Math Template

```
┌─────────────────────────────────────────────────────────┐
│     BLOB STORAGE NAPKIN MATH - Template                 │
└─────────────────────────────────────────────────────────┘

STEP 1: USER STORAGE ESTIMATION
───────────────────────────────
Total Users:                 [____] M
Average Storage/User:        [____] GB

→ Raw Storage = Users × Avg Storage = [____] PB

STEP 2: APPLY SAVINGS
───────────────────────────────
Deduplication (50%):         [____] PB × 0.5 = [____] PB
Compression (30% on remaining): [____] PB × 0.7 = [____] PB

→ Logical Storage = [____] PB

STEP 3: APPLY REDUNDANCY
───────────────────────────────
Redundancy Factor:           [____]× (3× or 1.5× for erasure)

→ Physical Storage = [____] PB × [____] = [____] PB

STEP 4: TRAFFIC ESTIMATION
───────────────────────────────
Daily Uploads:               
  DAU × Uploads/User/Day = [____] M × [____] = [____] M uploads
  Avg file size: [____] MB
  Daily upload volume: [____] M × [____] MB = [____] TB

Daily Downloads:
  DAU × Downloads/User/Day = [____] M × [____] = [____] M downloads
  Daily download volume: [____] M × [____] MB = [____] TB

→ Upload QPS = Uploads/Day ÷ 86,400 = [____]
→ Download QPS = Downloads/Day ÷ 86,400 = [____]

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────────
Upload Bandwidth:
  Upload QPS × Avg File Size = [____] QPS × [____] MB = [____] GB/s

Download Bandwidth:
  Download QPS × Avg File Size = [____] QPS × [____] MB = [____] GB/s

STEP 6: METADATA DATABASE SIZING
───────────────────────────────
Files per User:              [____]
Total Files:                 [____] M × [____] = [____] B files

Metadata per File:           ~500 bytes (file_id, name, size, chunks, etc.)
Total Metadata:              [____] B × 500 B = [____] TB

Chunks (4MB chunks):
  Total Storage ÷ Chunk Size = [____] PB ÷ 4 MB = [____] B chunks
  Chunk Metadata:            [____] B × 100 B = [____] TB
```

---

## 💾 PART 4: Dropbox Scale Filled Template

```
┌─────────────────────────────────────────────────────────┐
│         DROPBOX-SCALE - NAPKIN MATH SOLUTION            │
└─────────────────────────────────────────────────────────┘

STEP 1: USER STORAGE ESTIMATION
───────────────────────────────
Total Users:                 500 M
Average Storage/User:        100 GB (weighted: free + paid)

→ Raw Storage = 500M × 100 GB = 50,000 PB = 50 Exabytes

STEP 2: APPLY SAVINGS
───────────────────────────────
Deduplication (50%):         50 EB × 0.5 = 25 EB
Compression (30% on remaining): 25 EB × 0.7 = 17.5 EB

→ Logical Storage = 17.5 Exabytes

STEP 3: APPLY REDUNDANCY
───────────────────────────────
Redundancy Factor:           1.5× (erasure coding 6+3)

→ Physical Storage = 17.5 EB × 1.5 = 26.25 Exabytes
→ ~26,000 Petabytes actual storage needed

STEP 4: TRAFFIC ESTIMATION
───────────────────────────────
Daily Uploads:               
  50M DAU × 5 uploads/day = 250 M uploads/day
  Avg file size: 5 MB
  Daily upload volume: 250M × 5 MB = 1,250 TB/day = 1.25 PB/day

Daily Downloads:
  50M DAU × 20 downloads/day = 1,000 M downloads/day
  Daily download volume: 1B × 5 MB = 5,000 TB/day = 5 PB/day

→ Upload QPS = 250M ÷ 86,400 = 2,900 uploads/sec
→ Download QPS = 1B ÷ 86,400 = 11,600 downloads/sec

STEP 5: BANDWIDTH ESTIMATION
───────────────────────────────
Upload Bandwidth:
  2,900 uploads/s × 5 MB = 14,500 MB/s ≈ 15 GB/s = 120 Gbps

Download Bandwidth:
  11,600 downloads/s × 5 MB = 58,000 MB/s ≈ 58 GB/s = 464 Gbps

Peak (3× average):
  Upload: 360 Gbps
  Download: 1.4 Tbps (MASSIVE!)

CDN offloads 99%:
  Origin bandwidth: ~14 Gbps (manageable)

STEP 6: METADATA DATABASE SIZING
───────────────────────────────
Files per User:              2,000 files
Total Files:                 500M × 2,000 = 1 Trillion files

Metadata per File:           ~500 bytes
Total Metadata:              1T × 500 B = 500 TB

Chunks (4MB chunks):
  17.5 EB ÷ 4 MB = 4.375 Trillion chunks
  Chunk Metadata (hash, location, ref count):
    4.375T × 100 B = 437.5 TB

Total Metadata DB:           500 TB + 437.5 TB ≈ 1 PB
Sharded across:              64 shards = ~16 TB per shard
```

---

## 🎯 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Total Users** | 500M | User base size |
| **Raw Storage** | 50 EB | Before optimization |
| **Logical Storage** | 17.5 EB | After dedup + compression |
| **Physical Storage** | 26 EB | With redundancy |
| **Daily Uploads** | 1.25 PB | Growth rate |
| **Daily Downloads** | 5 PB | Bandwidth planning |
| **Upload QPS** | 2,900 | API capacity |
| **Download QPS** | 11,600 | CDN sizing |
| **Download Bandwidth** | 464 Gbps | Network infrastructure |
| **Metadata DB** | 1 PB | Database sizing |
| **Total Files** | 1 Trillion | Scale of operations |

---

## 💡 Mental Math Techniques for Storage Scale

### **Technique 1: The "User Storage Pyramid"**
```
VISUALIZE this distribution:

         /\        10% power users (1TB+ each)
        /  \       → 50M users × 1TB = 50 EB
       /----\      30% regular users (100GB each)
      /------\     → 150M users × 100GB = 15 EB
     /--------\    60% light users (10GB each)
    /----------\   → 300M users × 10GB = 3 EB
                   
Total: 68 EB raw (close to our 50 EB average estimate)
```

### **Technique 2: The "Dedup Reality Check"**
```
Deduplication varies by use case:

Consumer (Dropbox):       50% dedup
  → Users have similar photos, documents

Enterprise (Box):         60-70% dedup
  → Lots of shared corporate files

Developer (GitHub):       20-30% dedup
  → Unique code, binaries

Media (YouTube):          10-20% dedup
  → Mostly unique videos
```

### **Technique 3: The "Storage Cost Calculator"**
```
S3 pricing (2025 estimate):
• Standard: $0.023/GB/month
• Infrequent Access: $0.0125/GB/month
• Glacier: $0.004/GB/month

For 26 EB physical storage:
  100% Standard: 26M TB × $23 = $598M/month (!)
  80% IA, 20% Standard: $390M/month
  50% Glacier, 30% IA, 20% Standard: $234M/month

Dropbox Magic Pocket (custom):
  Estimated 50% cost reduction → $117M/month
  Annual: $1.4 BILLION just for storage!
```

---

## 🧠 PART 5: Practice Problems

### Problem 1: Google Drive Scale
```
Given:
- 1 billion users
- Average 15 GB per user
- 60% deduplication
- 40% compression on remaining
- 2× replication

Calculate:
1. Raw storage
2. Logical storage (after dedup + compression)
3. Physical storage (with replication)
4. Monthly storage cost (assume $0.02/GB)

[Try it yourself!]
```

<details>
<summary>Answer</summary>

```
1. RAW STORAGE:
   1B users × 15 GB = 15,000 PB = 15 EB

2. LOGICAL STORAGE:
   Dedup: 15 EB × 0.4 (60% saved) = 6 EB
   Compression: 6 EB × 0.6 (40% saved) = 3.6 EB

3. PHYSICAL STORAGE:
   3.6 EB × 2 (replication) = 7.2 EB = 7,200 PB

4. MONTHLY COST:
   7,200,000 TB × $20/TB = $144 million/month
   Annual: $1.7 billion
```
</details>

---

### Problem 2: Enterprise File Share (Box/OneDrive)
```
Given:
- 10 million users (enterprise)
- Average 500 GB per user (lots of shared folders)
- 70% deduplication (high enterprise sharing)
- 30% compression
- Erasure coding 6+3 (1.5× overhead)

Calculate:
1. Raw storage
2. After dedup
3. After compression
4. Physical storage
5. Cost savings from dedup + compression

[Try it yourself!]
```

<details>
<summary>Answer</summary>

```
1. RAW STORAGE:
   10M × 500 GB = 5,000 PB = 5 EB

2. AFTER DEDUP:
   5 EB × 0.3 (70% saved) = 1.5 EB

3. AFTER COMPRESSION:
   1.5 EB × 0.7 (30% saved) = 1.05 EB

4. PHYSICAL STORAGE:
   1.05 EB × 1.5 (erasure) = 1.575 EB

5. COST SAVINGS:
   Raw: 5 EB
   Physical: 1.575 EB
   Savings: 68.5% reduction!
   
   If $0.02/GB:
   Without optimization: 5M TB × $20 = $100M/month
   With optimization: 1.575M TB × $20 = $31.5M/month
   Savings: $68.5M/month ($822M/year!)
```
</details>

---

## 🎁 Bonus: Storage Tier Strategy

```
╔════════════════════════════════════════════════════════╗
║           INTELLIGENT STORAGE TIERING                  ║
╚════════════════════════════════════════════════════════╝

Data Access Patterns:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Hot (< 30 days old):      20% of data, 80% of access
• Warm (30-180 days):       30% of data, 15% of access
• Cold (> 180 days):        50% of data, 5% of access

Storage Tier Mapping:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Hot  → S3 Standard        ($0.023/GB)  → 20% × 26 EB = 5.2 EB
Warm → S3 IA              ($0.0125/GB) → 30% × 26 EB = 7.8 EB
Cold → S3 Glacier Flexible ($0.004/GB) → 50% × 26 EB = 13 EB

Cost Calculation:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Hot:  5.2M TB × $23 = $119.6M/month
Warm: 7.8M TB × $12.5 = $97.5M/month
Cold: 13M TB × $4 = $52M/month
Total: $269M/month

Savings vs All-Hot:
  All-Hot: 26M TB × $23 = $598M/month
  Tiered: $269M/month
  Savings: $329M/month (55% reduction!)
```

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Forgetting Metadata Overhead**
```
✗ BAD:  "Storage = Files × File Size"
✓ GOOD: "Storage = (Files × File Size) + (Files × Metadata) + 
         (Chunks × Chunk Metadata)"

For 1T files:
  File data: 50 EB
  File metadata: 500 TB
  Chunk metadata: 438 TB
  Total: 50 EB + 1 PB (metadata is tiny but CRITICAL!)
```

### Mistake 2: **Overestimating Deduplication**
```
✗ BAD:  "90% dedup is normal"
✓ GOOD: "Dedup varies: Consumer (50%), Enterprise (60-70%), 
         Media (10-20%)"

Claiming 90% dedup is unrealistic unless very specific use case
(e.g., backup systems with many identical backups)
```

### Mistake 3: **Ignoring Growth Rate**
```
✗ BAD:  "We need 26 EB storage"
✓ GOOD: "We need 26 EB today, growing at 1.25 PB/day = 456 PB/year
         In 3 years: 26 EB + 1.4 EB = 27.4 EB
         Plan for 30 EB capacity"
```

### Mistake 4: **Forgetting Peak Traffic**
```
✗ BAD:  "We handle 2,900 upload QPS"
✓ GOOD: "Average 2,900 QPS, but peak (launch/marketing) is 3-5×
         = 9,000-15,000 QPS. Need auto-scaling!"
```

### Mistake 5: **Single Region Planning**
```
✗ BAD:  "Store all 26 EB in US-EAST-1"
✓ GOOD: "Multi-region:
         - US: 40% (10.4 EB)
         - EU: 35% (9.1 EB)
         - APAC: 25% (6.5 EB)
         For latency + data residency compliance"
```

---

## 📝 Interview Cheat Sheet

```
┌──────────────────────────────────────────────────┐
│  BLOB STORAGE SCALE ESTIMATION - 5 MIN RITUAL   │
└──────────────────────────────────────────────────┘

[ ] 1. Users × Storage/User = Raw Storage
[ ] 2. Apply Dedup (0.5×) and Compression (0.7×) = Logical
[ ] 3. Apply Redundancy (1.5-3×) = Physical
[ ] 4. Calculate Traffic (Uploads, Downloads)
[ ] 5. QPS = Operations/Day ÷ 86,400
[ ] 6. Bandwidth = QPS × Avg File Size
[ ] 7. Metadata = Files × 500 bytes + Chunks × 100 bytes
[ ] 8. Don't forget: CDN, Tiering, Multi-region

ALWAYS state:
  "With deduplication and compression, we save 65%"
  "CDN handles 99% of downloads, reducing origin load"
  "Tiering reduces storage cost by 50%"
```

---

## 🎓 Final Wisdom

> **"Storage is EASY to calculate but HARD to optimize"**

Key Principles:
1. **User storage varies wildly** (10GB to 10TB per user)
2. **Dedup is your friend** (50% savings is normal)
3. **Tiering is critical** (50% cost reduction)
4. **CDN is mandatory** (99% offload)
5. **Metadata matters** (1 PB for 1 trillion files)
6. **Growth is exponential** (plan for 2-3× in 3 years)

---

**Remember the Storage Mantra:**
> "Raw × Dedup × Compression × Redundancy = Physical Storage"
> "Dedup + Compression + Tiering = 70% cost savings"

**Now go crush those storage interviews!** 🚀
