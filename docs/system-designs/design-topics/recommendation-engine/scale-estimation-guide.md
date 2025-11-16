# 🎯 Recommendation Engine: Scale Estimation Masterclass

## The POWER Technique for ML Systems Scale Math
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This is a **mental framework** for estimating scale in ML-powered systems.

---

## 📊 PART 1: Users, Items & Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 1B | Netflix/Amazon/YouTube scale |
| | Daily Active Users (DAU) | 200M | ~20% of total (Pareto!) |
| | Power Users (Heavy engagement) | 50M | ~25% generate 75% interactions |
| **Item Catalog** | Total Items | 100M | Products/videos/music/articles |
| | New Items/day | 100K | Catalog growth rate |
| | Active Items (< 1 year old) | 30M | 30% of catalog |
| **Interactions** | Interactions per DAU/day | 50 | Views, clicks, purchases, likes |
| | Total Interactions/day | 10B | 200M DAU × 50 |
| | Read:Write Ratio | N/A | Recommendation requests vs events |
| **Recommendations** | Rec requests per DAU/day | 2.5 | Average session count |
| | Total Rec requests/day | 500M | 200M DAU × 2.5 |
| **Time Distribution** | Peak Hours | 6-8 hours/day | Evening + commute times |
| | Peak Traffic Multiplier | 2.5x | Higher than typical systems |

---

## 🧮 PART 2: The "ML System Calculator" - Your Mental Math Toolkit

### Rule #1: **The ML Data Scale Ladder**
```
ML System Data Hierarchy:
• User-Item Matrix: Users × Items interactions
• Embeddings: Users × Dims + Items × Dims
• Features: (Users + Items) × Feature_Count
• Training Data: Interactions × (1 + Negative_Samples)
• Model Parameters: Layers × Neurons × Weights

Key insight: ML systems have 3 storage layers:
1. Raw Data (events, interactions)
2. Processed Features (engineered, aggregated)
3. Model Artifacts (weights, embeddings)
```

### Rule #2: **The Embedding Math Shortcut**
```
Embedding Storage = Count × Dimensions × 4 bytes (float32)

Example: 1B users × 128 dims × 4 bytes = 512 GB

Quick calc: 1B × 128 × 4 = 1B × 512 = 512 GB
(Think: "half a KB per user" → 1B × 0.5 KB = 500 GB)
```

### Rule #3: **The GPU Hour Conversion**
```
Training Time Estimation:
• Small dataset (1M samples): ~1 GPU hour
• Medium dataset (100M samples): ~10-20 GPU hours
• Large dataset (10B samples): ~100-200 GPU hours

With 8 GPUs in parallel: Divide by 8
With data parallelism overhead: Add 20%
```

---

## 📈 PART 3: Quick Scale Math Template for ML Systems

```
┌─────────────────────────────────────────────────────────────┐
│    THE ML SYSTEM NAPKIN MATH TEMPLATE - Universal RecSys    │
└─────────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Users (DAU):              [____] M
Items in Catalog:         [____] M
Interactions/user/day:    [____]
Rec requests/user/day:    [____]

→ Total Interactions/day  = DAU × Interactions   = [____] B
→ Interaction Event QPS   = Interactions ÷ 100K  = [____] K
→ Rec Request QPS         = Requests ÷ 100K      = [____] K
→ Peak QPS                = Average QPS × 2.5    = [____] K

STEP 2: DATA STORAGE ESTIMATION
───────────────────────────
A. Raw Event Storage:
   Events/day:            [____] B
   Bytes per event:       [____] bytes (200-500 typical)
   Retention:             [____] years

   → Daily Storage   = Events × Bytes      = [____] TB
   → Yearly Storage  = Daily × 365         = [____] TB
   → Total Storage   = Yearly × Years      = [____] TB

B. User/Item Profile Storage:
   Users:                 [____] B
   Items:                 [____] M
   Profile size:          [____] KB

   → User Storage    = Users × Profile     = [____] TB
   → Item Storage    = Items × Profile     = [____] GB

C. Embedding Storage:
   User embeddings:       [____] B users × [____] dims × 4 bytes
   Item embeddings:       [____] M items × [____] dims × 4 bytes

   → User Emb Storage = [____] GB
   → Item Emb Storage = [____] GB

STEP 3: COMPUTE REQUIREMENTS
───────────────────────────
A. Inference (Real-time):
   Rec QPS:               [____] K
   Latency budget:        [____] ms
   CPU cores per request: [____]

   → Concurrent Requests  = QPS × Latency_sec     = [____]
   → Total CPU Cores      = Concurrent × Cores    = [____]
   → Servers (32 cores)   = Total Cores ÷ 32      = [____]

B. Training (Offline):
   Training samples:      [____] B
   Epochs:                [____]
   GPU hours per epoch:   [____]

   → Total GPU Hours  = Samples × Epochs ÷ Throughput = [____]
   → With 8 GPUs      = GPU Hours ÷ 8                 = [____] hours
   → Training Freq    = Weekly/Daily                  = [____]

STEP 4: FEATURE STORE
───────────────────────────
User Features:         [____] (count)
Item Features:         [____] (count)
Feature Store Size:    Users × Features × Bytes

→ Feature Store Total = [____] TB

STEP 5: CACHE (Redis)
───────────────────────────
Using 80-20 Rule:
→ Hot Users (20%) = Total Users × 0.2              = [____] M
→ Cache Size      = Hot Users × Rec_Count × Bytes  = [____] GB

Practical cache: 10-20% of hot data               = [____] GB
```

---

## 💾 PART 4: Recommendation Engine Filled Template

```
┌─────────────────────────────────────────────────────────────┐
│      RECOMMENDATION ENGINE - NAPKIN MATH SOLUTION           │
└─────────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Users (DAU):              200 M
Items in Catalog:         100 M
Interactions/user/day:    50 (views, clicks, purchases)
Rec requests/user/day:    2.5 (sessions)

→ Total Interactions/day  = 200M × 50         = 10 B events
→ Interaction Event QPS   = 10B ÷ 100K        = 100K events/sec
→ Rec Request QPS         = 500M ÷ 100K       = 5K requests/sec
→ Peak QPS                = 5K × 2.5          = 12.5K requests/sec

STEP 2: DATA STORAGE ESTIMATION
───────────────────────────
A. Raw Event Storage:
   Events/day:            10 B
   Bytes per event:       200 bytes (user_id, item_id, event_type, timestamp, context)
   Retention:             1 year (for training)

   → Daily Storage   = 10B × 200B       = 2 TB/day
   → Yearly Storage  = 2TB × 365        = 730 TB/year

B. User/Item Profile Storage:
   Users:                 1 B
   User profile:          10 KB (demographics, preferences, history summary)
   Items:                 100 M
   Item metadata:         5 KB (title, description, category, tags)

   → User Storage    = 1B × 10KB        = 10 TB
   → Item Storage    = 100M × 5KB       = 500 GB

C. Embedding Storage:
   User embeddings:       1B users × 128 dims × 4 bytes
   Item embeddings:       100M items × 128 dims × 4 bytes

   → User Emb Storage = 1B × 128 × 4    = 512 GB
   → Item Emb Storage = 100M × 128 × 4  = 51 GB
   → Total Embeddings                   = 563 GB

D. Feature Store:
   User features:         1B users × 200 features × 4 bytes (avg)
   Item features:         100M items × 150 features × 4 bytes

   → User Features   = 1B × 200 × 4     = 800 GB
   → Item Features   = 100M × 150 × 4   = 60 GB
   → Total Features                     = 860 GB (compressed ~400 GB)

TOTAL STORAGE SUMMARY:
───────────────────────────
• Raw Events (1 year):     730 TB
• User Profiles:           10 TB
• Item Metadata:           0.5 TB
• Embeddings:              0.56 TB
• Feature Store:           0.4 TB (compressed)
• Model Artifacts:         0.05 TB (models, checkpoints)
───────────────────────────
TOTAL:                     ~741 TB for 1 year

STEP 3: COMPUTE REQUIREMENTS
───────────────────────────
A. Inference (Real-time):
   Rec QPS:               12.5K (peak)
   Latency budget:        100 ms (P95)
   Candidate Gen:         20-30 ms (ANN search)
   Ranking:               50-70 ms (ML model inference)

   Concurrent Requests:
   → QPS × Latency = 12.5K × 0.1 sec = 1,250 concurrent

   CPU Requirements (for 1,250 concurrent):
   → Assuming 2 CPU cores per request
   → Total Cores = 1,250 × 2 = 2,500 cores
   → Servers (32 cores each) = 2,500 ÷ 32 = ~80 servers

   GPU Requirements (for model serving):
   → GPU throughput: ~500 inferences/sec per GPU
   → Total GPUs = 12.5K ÷ 500 = ~25 GPUs (T4/V100)

B. Training (Offline):
   Training samples:      10B interactions (1 year data)
   Negative sampling:     1:4 ratio (4 negatives per positive)
   Total samples:         10B × 5 = 50B samples
   Epochs:                10
   GPU throughput:        1M samples/sec (on A100)

   → Total Samples × Epochs = 50B × 10 = 500B samples
   → GPU Hours = 500B ÷ 1M = 500K seconds = 139 GPU hours
   → With 8 GPUs parallel = 139 ÷ 8 = ~17.5 hours
   → Training Frequency = Weekly (manageable)

C. Feature Engineering (Spark):
   Data to process:       2 TB/day (raw events)
   Spark cluster:         50 nodes × 32 cores = 1,600 cores
   Processing time:       ~2 hours for daily batch job

STEP 4: BANDWIDTH
───────────────────────────
Event Ingestion:
→ Write BW = 100K events/sec × 200 bytes = 20 MB/sec ≈ 160 Mbps

Recommendation Serving:
→ Read BW  = 5K requests/sec × 500 bytes (response) = 2.5 MB/sec ≈ 20 Mbps

Model Downloads (deployment):
→ Model size: 5 GB per model × 4 models = 20 GB
→ Deployment freq: Weekly
→ Bandwidth spike: 20 GB ÷ 3600 sec = ~5 MB/sec

STEP 5: CACHE (Redis)
───────────────────────────
Using 80-20 Rule:
→ Hot Users (20%) = 1B × 0.2 = 200M users

Pre-computed recommendations per user:
→ Top 50 items × 8 bytes (item ID) = 400 bytes per user
→ Cache Size = 200M × 400 bytes = 80 GB

With metadata (scores, features):
→ Enhanced cache = 80 GB × 2 = 160 GB

Practical Redis Cluster:
→ 3 masters + 3 replicas
→ 200 GB total (with overhead)
→ Cost: ~$500/month (ElastiCache)
```

---

## 🧠 PART 5: ML-Specific Mental Math Techniques

### **Technique 1: The Embedding Dimension Rule**
```
Common embedding dimensions:
• Small models: 32-64 dims (mobile, edge devices)
• Standard: 128 dims (most production systems)
• Large: 256-512 dims (research, high accuracy)

Memory estimation:
32 dims   → ~128 bytes per entity (32 × 4)
128 dims  → ~512 bytes per entity (128 × 4)
256 dims  → ~1 KB per entity

Quick calc for 1B users with 128-dim embeddings:
1B × 512 bytes = 512 GB
```

### **Technique 2: The Training Data Multiplier**
```
Training data expansion due to negative sampling:

Positive samples: P
Negative sampling ratio: N (typically 1:4 or 1:10)

Total training samples = P × (1 + N)

Example:
10B interactions × (1 + 4) = 50B training samples

Each sample = (user_id, item_id, label, features) ≈ 1 KB
Storage = 50B × 1 KB = 50 TB
```

### **Technique 3: The GPU Throughput Shortcut**
```
Training throughput (samples/sec):
• CPU (32 cores): ~1K samples/sec
• Single GPU (V100): ~50K samples/sec
• Single GPU (A100): ~100K samples/sec

Inference throughput:
• CPU: ~100 inferences/sec
• GPU (T4): ~500 inferences/sec
• GPU (V100): ~1000 inferences/sec

Example:
Train 10B samples on 8× A100 GPUs:
→ Throughput = 8 × 100K = 800K samples/sec
→ Time = 10B ÷ 800K = 12,500 sec = ~3.5 hours (single epoch)
```

### **Technique 4: The Feature Store Size Formula**
```
Feature Store Size =
    (Users × User_Features + Items × Item_Features) × Bytes_Per_Feature

Typical feature sizes:
• Numerical: 4 bytes (float32)
• Categorical (embedding): 4-8 bytes (int32/int64)
• Text features (pre-processed): 512 bytes (128-dim embedding)

Example:
1B users × 200 features × 4 bytes = 800 GB (user features)
100M items × 150 features × 4 bytes = 60 GB (item features)
Total = 860 GB → Compressed (Parquet) = ~400 GB
```

---

## 🎯 PART 6: Real-World ML System Scale Benchmarks

| **System** | **DAU** | **Items** | **Events/day** | **Rec QPS** | **Training Freq** | **Storage** |
|------------|---------|-----------|----------------|-------------|-------------------|-------------|
| **Netflix** | 200M | 10K+ titles | 5B+ | 10K+ | Daily | 100+ PB |
| **YouTube** | 2B | 800M videos | 100B+ | 100K+ | Hourly | 1+ EB |
| **Amazon** | 300M | 500M products | 50B+ | 50K+ | Daily | 100+ PB |
| **Spotify** | 400M | 100M tracks | 20B+ | 20K+ | Daily | 50+ PB |
| **TikTok** | 1B | 1B+ videos | 200B+ | 200K+ | Hourly | 500+ PB |
| **LinkedIn** | 300M | 100M+ users | 10B+ | 15K+ | Daily | 50+ PB |
| **Our Design** | 200M | 100M items | 10B | 5-10K | Weekly | ~1 PB |

---

## 💡 PART 7: ML System Scale Estimation - Practice Problems

### Problem 1: E-commerce Product Recommendations

```
Given:
- 500M registered users
- 100M DAU
- 50M products in catalog
- Each user views 20 products/day
- Each user requests recommendations 3 times/day
- Purchase rate: 2% of viewed products
- Item embeddings: 256 dimensions

Calculate:
1. Event Ingestion QPS
2. Recommendation Request QPS
3. Embedding Storage (users + items)
4. Training dataset size (with 1:5 negative sampling)
5. GPU hours for weekly training (assume A100)

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. EVENT INGESTION QPS:
   - Product views/day = 100M × 20 = 2B views
   - Purchases/day = 2B × 0.02 = 40M purchases
   - Total events = 2B + 40M = 2.04B events/day
   - Event QPS = 2.04B ÷ 100K = ~20K events/sec
   - Peak QPS = 20K × 2 = ~40K events/sec

2. RECOMMENDATION REQUEST QPS:
   - Requests/day = 100M × 3 = 300M requests
   - Request QPS = 300M ÷ 100K = ~3K requests/sec
   - Peak QPS = 3K × 2 = ~6K requests/sec

3. EMBEDDING STORAGE:
   - User embeddings: 500M × 256 dims × 4 bytes = 512 GB
   - Item embeddings: 50M × 256 dims × 4 bytes = 51.2 GB
   - Total: ~563 GB

4. TRAINING DATASET SIZE:
   - Positive samples (1 year): 2B views/day × 365 = 730B samples
   - With 1:5 negative sampling: 730B × 6 = 4.38 TB samples
   - Per sample size: ~500 bytes (user_id, item_id, features, label)
   - Storage: 4.38TB × 500 bytes = ~2.2 PB raw
   - After feature engineering: ~100 TB (compressed)

5. GPU HOURS FOR TRAINING:
   - Training samples: 4.38 TB
   - Epochs: 5
   - Total: 4.38TB × 5 = 21.9 TB samples
   - A100 throughput: ~100K samples/sec
   - GPU seconds: 21.9TB ÷ 100K ≈ 60 hours (single GPU)
   - With 8 GPUs: 60 ÷ 8 = ~7.5 hours
```
</details>

---

### Problem 2: Video Streaming Recommendations (YouTube-like)

```
Given:
- 2B total users
- 500M DAU
- 800M videos in catalog
- 100K new videos uploaded/day
- Each user watches 10 videos/day (avg 10 min each)
- Each user gets recommendations 5 times/day
- Video embeddings: 128 dimensions
- Retention: 2 years

Calculate:
1. Video view event QPS
2. Storage for interaction events (2 years)
3. Embedding storage for videos
4. Feature store size (200 user features, 100 video features)
5. Daily batch processing time (Spark cluster: 100 nodes)

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. VIDEO VIEW EVENT QPS:
   - Views/day = 500M × 10 = 5B video views
   - View event QPS = 5B ÷ 100K = ~50K events/sec
   - Peak QPS = 50K × 2.5 = ~125K events/sec

2. STORAGE FOR INTERACTION EVENTS (2 years):
   - Events/day = 5B
   - Event size = 300 bytes (user_id, video_id, watch_time, context)
   - Daily storage = 5B × 300 bytes = 1.5 TB/day
   - Yearly storage = 1.5 TB × 365 = 547.5 TB/year
   - 2 years = 547.5 × 2 = ~1.1 PB

3. EMBEDDING STORAGE FOR VIDEOS:
   - Video embeddings: 800M × 128 dims × 4 bytes = 409.6 GB
   - With metadata: ~500 GB

4. FEATURE STORE SIZE:
   - User features: 2B × 200 features × 4 bytes = 1.6 TB
   - Video features: 800M × 100 features × 4 bytes = 320 GB
   - Total: ~1.92 TB → Compressed: ~900 GB

5. DAILY BATCH PROCESSING TIME:
   - Data to process: 1.5 TB/day
   - Spark cluster: 100 nodes × 32 cores = 3,200 cores
   - Processing throughput: ~500 GB/hour (typical)
   - Time: 1.5 TB ÷ 500 GB/hour = ~3 hours
```
</details>

---

## 🚨 ML-Specific Common Mistakes to Avoid

### Mistake 1: **Forgetting Model Update Frequency**
```
✗ BAD:  "We train once and deploy"
✓ GOOD: "Daily incremental training + weekly full retraining"

Impact: Model staleness → CTR degrades 10-20% per week without updates
```

### Mistake 2: **Underestimating Feature Store Size**
```
✗ BAD:  "Feature store = user count × feature count × 4 bytes"
✓ GOOD: "Feature store includes:
         - Raw features (numerical, categorical)
         - Derived features (aggregations, time-windows)
         - Embedding features (high-dimensional)
         - Versioning overhead (2-3x for multiple versions)"

Actual size: 2-3x initial estimate
```

### Mistake 3: **Ignoring Negative Sampling Overhead**
```
✗ BAD:  "Training data = number of positive interactions"
✓ GOOD: "Training data = positive × (1 + negative_ratio)
         With 1:10 negative sampling:
         10B interactions → 110B training samples"
```

### Mistake 4: **Not Accounting for A/B Testing Infrastructure**
```
✗ BAD:  "One model in production"
✓ GOOD: "Multiple model variants:
         - Control (baseline): 50% traffic
         - Variant A (new model): 25% traffic
         - Variant B (experimental): 25% traffic

         Infrastructure overhead: 3× model serving capacity"
```

### Mistake 5: **Underestimating Inference Latency**
```
✗ BAD:  "Model inference = 10ms"
✓ GOOD: "End-to-end latency breakdown:
         - Feature retrieval: 20ms
         - Candidate generation (ANN): 30ms
         - Model inference: 10ms
         - Post-processing: 10ms
         - Network overhead: 20ms
         - Total: 90ms (P50), 150ms (P95)"
```

---

## 📝 Your ML System Practice Template (Fill-in-the-Blank)

```
SYSTEM: ___________________

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Users (Total):            [____] M/B
Users (DAU):              [____] M
Items (Catalog):          [____] M
Interactions per user/day:[____]
Rec requests per user/day:[____]

Total Interactions/Day:   [____] B
Total Rec Requests/Day:   [____] M

→ Interaction QPS = [____] B ÷ 100K = [____] K
→ Rec Request QPS = [____] M ÷ 100K = [____] K
→ Peak QPS        = [____] K × 2.5   = [____] K

STEP 2: STORAGE ESTIMATION
───────────────────────────
A. Raw Events:
   Events/day:            [____] B
   Bytes per event:       [____] bytes
   Retention:             [____] years

   → Daily Storage  = [____] TB
   → Yearly Storage = [____] TB

B. Embeddings:
   User embeddings:       [____] B users × [____] dims × 4 bytes
   Item embeddings:       [____] M items × [____] dims × 4 bytes

   → User Emb  = [____] GB
   → Item Emb  = [____] GB

C. Feature Store:
   User features:         [____] features
   Item features:         [____] features

   → Feature Store = [____] TB

STEP 3: COMPUTE
───────────────────────────
A. Inference Servers:
   Peak QPS:              [____] K
   Latency budget:        [____] ms
   Concurrent requests:   QPS × Latency = [____]

   → Servers (32 cores) = [____]
   → GPUs (for serving) = [____]

B. Training (Weekly):
   Training samples:      [____] B
   GPU hours per epoch:   [____]
   Epochs:                [____]

   → Total GPU hours = [____]
   → With 8 GPUs     = [____] hours

STEP 4: CACHE
───────────────────────────
Hot users (20%):          [____] M
Recs per user:            [____]
Bytes per rec:            [____]

→ Cache Size = [____] GB

SMELL TEST:
───────────────────────────
□ QPS reasonable? (compare to known ML systems)
□ Storage in PB range? (typical for large-scale RecSys)
□ Training time < 1 day? (for weekly updates)
□ Cache size practical? (hundreds of GB for Redis cluster)
□ Inference latency < 100ms? (P95 target)
```

---

## 🎁 Bonus: ML System Scale Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║        ML RECOMMENDATION SYSTEM SCALE CHEAT SHEET       ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• 1 Day      = 100K seconds
• 1 GB       = 1B bytes
• 1 TB       = 1000 GB
• 1 PB       = 1000 TB
• Float32    = 4 bytes
• Embedding  = Dimensions × 4 bytes

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Event QPS       = Events/day ÷ 100K
Rec QPS         = Requests/day ÷ 100K
Embedding Size  = Count × Dimensions × 4 bytes
Training Data   = Positive × (1 + Negative_Ratio)
GPU Hours       = Samples ÷ Throughput ÷ 3600
Feature Store   = (Users + Items) × Features × 4 bytes

TYPICAL RATIOS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• DAU:Total = 20-25% (active users)
• Peak:Avg = 2-3x (higher variability)
• Negative Sampling = 1:4 to 1:10
• Cache Hit Rate = 70-85% (recommendations)
• Feature Store Compression = 2-3x (Parquet)
• Model Update Freq = Daily to Weekly

QUICK ESTIMATES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small RecSys:  1-10M users,   1-10M items,    <1 TB
Medium RecSys: 10-100M users, 10-100M items,  1-10 TB
Large RecSys:  100M-1B users, 100M-1B items,  10-100 TB
Huge RecSys:   >1B users,     >1B items,      100TB-1PB

ML INFERENCE BUDGET:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Feature Lookup:     10-20ms
Candidate Gen:      20-30ms (ANN search)
Model Inference:    10-20ms (per model)
Post-processing:    10-20ms
Network Overhead:   10-20ms
─────────────────────────────
Total (P95):        <100ms target

ML TRAINING BUDGET:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small model (<100M params):  1-10 GPU hours
Medium model (100M-1B):      10-100 GPU hours
Large model (>1B params):    100-1000 GPU hours

With 8× A100 GPUs in parallel: Divide by 6-8 (with overhead)

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Can serve 1K QPS with 10-20 servers? YES (with caching)
✓ Can store 1B embeddings in 1 TB? YES (128-dim, float32)
✓ Can train on 10B samples in 1 day? YES (with GPU cluster)
✓ Can cache 100M users in 100 GB Redis? YES (1 KB per user)
✓ Can achieve <100ms latency? YES (with optimization)

✗ Can single GPU serve 10K QPS? NO (need 20+ GPUs or CPUs)
✗ Can train on 1TB data in 1 hour? NO (need distributed training)
✗ Can store all features in memory? NO (use Feature Store)
✗ Can skip negative sampling? NO (critical for model quality)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final ML System Scale Challenge

Pick one of these ML systems and estimate full scale:

1. **Uber Rides** - Real-time location-based recommendations
2. **Airbnb** - Property recommendations with search constraints
3. **Pinterest** - Visual content recommendations
4. **Twitter/X** - Tweet recommendations in timeline
5. **OpenTable** - Restaurant recommendations

Use the blank template above and time yourself: **Can you complete it in 10 minutes?**

---

## 📚 Additional ML System Resources

- **Papers**: "Deep Neural Networks for YouTube Recommendations" (Google)
- **Papers**: "Wide & Deep Learning for Recommender Systems" (Google)
- **Papers**: "Behavior Sequence Transformer" (Alibaba)
- **Books**: "Recommender Systems Handbook" by Ricci et al.
- **Courses**: Stanford CS246 (Mining Massive Datasets)
- **Tools**: TensorFlow Recommenders, PyTorch Geometric, FAISS

---

**Remember**:
> "In ML system design, understanding the data scale and model complexity trade-offs is more important than exact numbers."

**Now go build scalable recommendation systems!** 🚀

---

*Created with the POWER technique for ML Systems: Principles → Order → Write → Estimate → Round*
*Perfect for: FAANG interviews, ML System Design rounds, RecSys architecture discussions*
