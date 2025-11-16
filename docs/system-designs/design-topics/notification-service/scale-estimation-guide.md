# 🎯 Notification Service Scale Estimation Masterclass

## The POWER Technique for Notification Systems
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

Apply this mental framework to ANY notification system - from startup MVPs to billion-user platforms.

---

## 📊 PART 1: Users & Notification Patterns

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 100M | Mid-sized social/e-commerce platform |
| | Daily Active Users (DAU) | 20M | ~20% daily engagement rate |
| | Power Users | 2M | ~10% of DAU, generate 60% notifications |
| **Notification Patterns** | Notifications/user/day | 10 | Industry average (3-20 range) |
| | Channel Distribution | 60% push, 30% email, 10% SMS | Cost and preference based |
| | Critical vs Standard | 10% critical, 90% standard | Priority distribution |
| **Time Distribution** | Peak Hours | 6 hours/day | Morning (7-9 AM), Evening (6-9 PM) |
| | Peak Traffic Multiplier | 4x | Concentrated during active hours |
| **Notification Types** | Transactional | 70% | Orders, payments, security |
| | Marketing | 20% | Promotions, recommendations |
| | Social | 10% | Likes, follows, comments |

---

## 🧮 PART 2: The "Real-World Calculator" - Notification Edition

### Rule #1: **Notification Volume Estimation**
```
Remember these patterns:
• E-commerce: 5-15 notifications/user/day (order updates, tracking)
• Social Media: 10-50 notifications/user/day (engagement heavy)
• Banking: 2-8 notifications/user/day (transactions, security)
• Ride-sharing: 3-10 notifications/user/day (ride lifecycle)

Golden Rule: Start with 10 notifications/user/day as baseline
```

### Rule #2: **Channel Cost Estimation**
```
Cost per notification (industry averages):
• Push Notification: $0 (FREE via FCM/APNS)
• Email: $0.001 per email (SendGrid/AWS SES)
• SMS: $0.0075 per SMS (Twilio, varies by country)
• WhatsApp: $0.005-$0.01 per message (Business API)

Cost Optimization Insight:
If 60% push, 30% email, 10% SMS:
Avg cost = 0.6×$0 + 0.3×$0.001 + 0.1×$0.0075
         = $0 + $0.0003 + $0.00075
         = $0.00105 per notification
```

### Rule #3: **The Channel Mix Calculation**
```
For 100M notifications/day:
Push:  60M notifications × $0 = $0
Email: 30M notifications × $0.001 = $30
SMS:   10M notifications × $0.0075 = $75

Daily cost: $105
Monthly cost: $3,150
Yearly cost: $38,325

🎯 Cost Optimization: Shifting 5% from SMS to push saves ~$14K/year!
```

---

## 📈 PART 3: Notification System Scale Template

```
┌─────────────────────────────────────────────────────────┐
│  🔔 NOTIFICATION SYSTEM - NAPKIN MATH TEMPLATE          │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):      [____] M
Notifications/user/day:        [____]
Total notifications/day:       [____] M

→ Avg QPS = Total/day ÷ 100K     = [____]
→ Peak QPS = Avg QPS × Peak Mult  = [____]

Channel Breakdown:
→ Push notifications/day = Total × [__]%  = [____] M
→ Email notifications/day = Total × [__]% = [____] M
→ SMS notifications/day = Total × [__]%   = [____] M

STEP 2: STORAGE ESTIMATION
───────────────────────────
Data per notification:
  - Notification metadata      = [____] bytes
  - User preferences           = [____] bytes
  - Delivery logs              = [____] bytes
  
Retention period:              [____] days

→ Daily Storage = Notifs × Size      = [____] GB
→ Monthly Storage = Daily × 30        = [____] TB
→ Yearly Storage = Monthly × 12       = [____] TB

STEP 3: COST ESTIMATION
───────────────────────────
Channel costs:
→ Push cost = Push/day × $0           = $0
→ Email cost = Email/day × $0.001     = $[____]
→ SMS cost = SMS/day × $0.0075        = $[____]

→ Total Daily Cost = [____]
→ Total Monthly Cost = Daily × 30     = $[____]

STEP 4: PROCESSING CAPACITY
───────────────────────────
→ Queue throughput needed = Peak QPS  = [____]
→ Worker capacity = QPS / Workers     = [____] per worker
→ Provider API calls = Total/day      = [____] M
```

---

## 💾 PART 4: Notification Service Filled Example

```
┌─────────────────────────────────────────────────────────┐
│      NOTIFICATION SYSTEM - NAPKIN MATH SOLUTION         │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):      20 M users
Notifications/user/day:        10 notifications
Total notifications/day:       200 M notifications

→ Avg QPS = 200M ÷ 100K          = 2,000 QPS
→ Peak QPS = 2K × 4              = 8,000 QPS

Channel Breakdown:
→ Push notifications/day = 200M × 60%  = 120 M
→ Email notifications/day = 200M × 30% = 60 M
→ SMS notifications/day = 200M × 10%   = 20 M

STEP 2: STORAGE ESTIMATION
───────────────────────────
Data per notification:
  - Notification ID (UUID)       = 16 bytes
  - User ID                      = 8 bytes
  - Notification type            = 50 bytes
  - Payload (JSON)               = 500 bytes
  - Timestamps (sent, delivered) = 16 bytes
  - Status & metadata            = 100 bytes
  - Total per notification       = ~700 bytes

Retention period: 90 days (compliance requirement)

→ Daily Storage = 200M × 700B        = 140 GB/day
→ Monthly Storage = 140GB × 30        = 4.2 TB/month
→ 90-day Storage = 140GB × 90         = 12.6 TB total

User Preferences Storage:
→ 100M users × 1KB (prefs)           = 100 GB (one-time)

Total Storage: ~13 TB (logs) + 100 GB (preferences) = 13.1 TB

STEP 3: COST ESTIMATION
───────────────────────────
Channel costs (daily):
→ Push cost = 120M × $0              = $0
→ Email cost = 60M × $0.001          = $60
→ SMS cost = 20M × $0.0075           = $150

→ Total Daily Cost = $210
→ Total Monthly Cost = $210 × 30     = $6,300
→ Total Yearly Cost = $6,300 × 12    = $75,600

Infrastructure costs (monthly):
→ Kafka cluster (6 nodes)            = $2,000
→ PostgreSQL (primary + 3 replicas)  = $1,500
→ Redis cluster (3 nodes)            = $500
→ Worker instances (50 instances)    = $2,500
→ Load balancers                     = $200
→ S3 storage (13 TB)                 = $300
→ Monitoring & logging               = $500

→ Monthly Infrastructure = $7,500
→ Yearly Infrastructure = $90,000

TOTAL YEARLY COST: $75,600 (channels) + $90,000 (infra) = $165,600

STEP 4: PROCESSING CAPACITY
───────────────────────────
→ Queue throughput needed = 8K QPS peak
→ Worker capacity = 8K QPS ÷ 50 workers = 160 notifications/sec/worker
→ Provider API calls = 200M/day = 2,300 calls/sec avg
→ Database writes = 200M/day = 2,000 writes/sec avg
```

---

## 🧠 PART 5: Mental Math for Notification Systems

### **Technique 1: The "User → Notification → Cost" Pipeline**
```
Memorize this calculation flow:

Users → Notifications → Channels → Cost

Example:
10M users × 8 notifs/day = 80M notifications
80M × 70% email = 56M emails × $0.001 = $56/day

Quick mental calculation:
"10 million users, 8 per day, mostly email"
→ ~80M notifications
→ ~$60/day email cost
→ ~$2K/month
```

### **Technique 2: The "Peak Hour" Reality Check**
```
EMOTION TRIGGER: "Notifications spike during user activity!"

If DAU = 20M and peak hours = 6 hours (7-9 AM, 6-9 PM):
- 60% of daily notifications happen in peak 6 hours
- Peak hour QPS = Avg QPS × 4 to 6x

Example:
200M notifications/day
Avg QPS = 200M ÷ 100K = 2K QPS
Peak QPS = 2K × 4 = 8K QPS

Design for PEAK, not average!
```

### **Technique 3: The "Channel Mix" Optimization**
```
When given flexibility, optimize for cost:

SMS: $0.0075 (most expensive)
Email: $0.001 (cheap)
Push: $0 (FREE!)

Strategy:
- Critical alerts → SMS (reliability worth cost)
- Transactional → Email (rich content, audit trail)
- Real-time updates → Push (free, instant)
- Marketing → Email (lowest cost per conversion)

Cost savings example:
100M marketing notifications:
SMS: 100M × $0.0075 = $750
Email: 100M × $0.001 = $100
Savings: $650/day = $19,500/month!
```

### **Technique 4: The "Storage Retention" Trade-off**
```
Storage grows linearly with retention period!

Example:
200M notifications/day × 700 bytes = 140 GB/day

Retention options:
30 days:  140 GB × 30  = 4.2 TB   (legal minimum)
90 days:  140 GB × 90  = 12.6 TB  (compliance sweet spot)
365 days: 140 GB × 365 = 51 TB    (full year audit)

Cost on S3 Standard:
30 days:  4.2 TB × $0.023/GB  = $97/month
90 days:  12.6 TB × $0.023/GB = $290/month
365 days: 51 TB × $0.023/GB   = $1,173/month

Optimization: Archive to Glacier after 30 days
→ 30 days S3 Standard: $97
→ 60 days Glacier: (8.4 TB × $0.004/GB) = $34
→ Total: $131/month (saves $159/month vs all Standard)
```

---

## 🎨 PART 6: The Visual Mind Map

```
              🔔 NOTIFICATION SERVICE
                        |
        ┌───────────────┼───────────────┐
        |               |               |
    📊 SCALE        💾 STORAGE      💰 COST
        |               |               |
    ┌───┴───┐      ┌────┴────┐     ┌───┴───┐
  DAU   QPS      Size   Days    Push  Email SMS
  20M   8K      13TB   90d      $0   $60  $150/day
```

**Memory Trigger**: Think **"S.S.C."** = Scale, Storage, Cost

---

## 🏗️ PART 7: Domain Model for Notification Service

```typescript
// Domain-Driven Design: Notification Context

@Entity
class Notification {
    id: UUID;                    // 16 bytes
    userId: number;              // 8 bytes
    eventType: string;           // "order_placed", "payment_received"
    channels: Channel[];         // [EMAIL, PUSH, SMS]
    priority: Priority;          // CRITICAL, HIGH, MEDIUM, LOW
    payload: JSON;               // Event-specific data (500 bytes avg)
    status: NotificationStatus;  // QUEUED, SENT, DELIVERED, FAILED
    
    sentAt: Timestamp;
    deliveredAt: Timestamp;
    
    // Scale Insight: 700 bytes per notification
    // 200M/day × 700 bytes = 140 GB/day
}

@Entity
class UserPreference {
    userId: number;
    emailEnabled: boolean;
    smsEnabled: boolean;
    pushEnabled: boolean;
    quietHours: QuietHoursConfig;
    frequencyLimits: FrequencyConfig;
    
    // Scale Insight: ~1KB per user
    // 100M users × 1KB = 100 GB total
}

@ValueObject
class Channel {
    type: ChannelType;  // EMAIL, SMS, PUSH, IN_APP
    cost: number;       // $0, $0.001, $0.0075
    latency: number;    // Expected delivery time (ms)
}

@Service
class NotificationOrchestrator {
    // Scale Insight: Must handle 8K QPS peak
    // Each request:
    // 1. Check user preferences (Redis: <1ms)
    // 2. Apply deduplication (Redis: <1ms)
    // 3. Publish to Kafka (Kafka: <5ms)
    // Total: <10ms per notification
}
```

---

## 🎯 PART 8: Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│  NOTIFICATION SYSTEM SCALE - 5 MIN RITUAL        │
└──────────────────────────────────────────────────┘

[ ] 1. Ask: DAU, notifications/user/day, channel mix
[ ] 2. Calculate total notifications/day
[ ] 3. Calculate QPS: Total/day ÷ 100K
[ ] 4. Apply peak multiplier (4x typical)
[ ] 5. Estimate cost: Count SMS/email, push is free
[ ] 6. Storage: Notifs/day × 700 bytes × retention days
[ ] 7. Sanity check: Does $6K/month for 200M notifs/day seem right? YES!
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Avg QPS** | 2,000 | Baseline worker capacity |
| **Peak QPS** | 8,000 | Infrastructure sizing (4x avg) |
| **Storage** | 13 TB (90d) | Database and S3 sizing |
| **Daily Cost** | $210 | Channel costs (mostly SMS) |
| **Monthly Infra** | $7,500 | Kafka, DB, workers, Redis |
| **Yearly Total** | $165,600 | Budget planning |
| **Delivery SLA** | <5 sec (P95) | User experience target |
| **Success Rate** | 99.9% | Reliability target with retries |

---

## 💡 Pro Architect Tips

### **Tip 1: The Channel Cost Smell Test**
After calculations, ask:
- "Is SMS 15x more expensive than email?" → YES ($0.0075 vs $0.001)
- "Should we prefer push over SMS for non-critical?" → YES (free vs paid)
- "Can we save $19K/month by moving marketing to email?" → YES

### **Tip 2: The Scalability Anchor**
Compare to known systems:
- "Facebook sends 20 billion notifications/day" (100x our scale)
- "WhatsApp: 100 billion messages/day" (500x our scale)
- "Our 200M/day is like a mid-sized social platform" ✓

### **Tip 3: Peak Hour Reality**
Design for peaks, not averages:
1. E-commerce: 6-9 PM (post-work shopping)
2. Social: 7-9 AM, 6-10 PM (commute times)
3. Banking: 9-11 AM, 6-8 PM (business hours)

---

## 🎓 Professor's Final Wisdom

> **"In notification systems, COST-AWARENESS beats FEATURE-RICHNESS"**

Your interviewer wants to see:
1. ✅ Understanding of channel trade-offs (cost vs latency vs reliability)
2. ✅ Peak traffic handling (4-6x average load)
3. ✅ Storage optimization (retention policies, archival)
4. ✅ Multi-channel orchestration logic

**NOT NEEDED:**
- ❌ Perfect cost calculations (order of magnitude is enough)
- ❌ Every edge case (focus on 80% use case)
- ❌ Implementation details (high-level architecture wins)

---

## 🔁 Repetition Backed by Emotion

**REPEAT 3 TIMES OUT LOUD:**
1. *"Push is FREE, SMS costs money - design accordingly!"*
2. *"Peak traffic is 4x average - never design for average!"*
3. *"Storage = notifications/day × 700 bytes × retention days - it's that simple!"*

**VISUALIZE:** You're at the whiteboard: "We have 20M DAU, 10 notifications each, that's 200M per day. At peak, that's 8,000 per second..."

---

## 📚 Real-World Notification Scale Benchmarks

| **Company** | **DAU** | **Notifs/day** | **Channels** | **Special Notes** |
|-------------|---------|----------------|--------------|-------------------|
| **Facebook** | 2B | 20B | Push, email, in-app | Heavy ML personalization |
| **WhatsApp** | 2B | 100B | In-app messages | End-to-end encrypted |
| **Gmail** | 1.5B | 5B | Email only | Spam filtering critical |
| **Uber** | 100M | 500M | Push, SMS, email | Real-time location updates |
| **Amazon** | 300M | 2B | Email, push, SMS | Order lifecycle tracking |
| **Medium-sized App** | 20M | 200M | Push (60%), email (30%), SMS (10%) | Our example! |

---

## 🔧 Practical Application: Different App Types

### For **E-commerce Platform** (like Amazon):
```
STEP 1: TRAFFIC
- Users place orders → 5 notifications per order
  (confirmation, payment, shipped, in-transit, delivered)
- 1M orders/day = 5M transactional notifications
- Marketing emails: 10M/day (promotions, recommendations)
- Total: 15M/day

STEP 2: CHANNEL MIX
- Email: 70% (detailed order info, marketing)
- Push: 20% (real-time delivery updates)
- SMS: 10% (critical: OTP, delivery alerts)

STEP 3: COST
- Email: 10.5M × $0.001 = $10.50/day
- Push: 3M × $0 = $0
- SMS: 1.5M × $0.0075 = $11.25/day
→ Daily: $21.75 | Monthly: ~$650

STEP 4: PEAK PATTERNS
- Peak: 6-9 PM (post-work shopping)
- Peak multiplier: 5x
```

### For **Social Media App** (like Twitter):
```
STEP 1: TRAFFIC
- High engagement: 30 notifications/user/day
- 50M DAU = 1.5 billion notifications/day
- Types: likes, comments, follows, mentions

STEP 2: CHANNEL MIX
- Push: 80% (real-time engagement)
- Email: 15% (digest, highlights)
- In-app: 5% (live feed updates)

STEP 3: COST
- Push: 1.2B × $0 = $0 (FREE!)
- Email: 225M × $0.001 = $225/day
→ Monthly: ~$6,750 (email only)

STEP 4: CHALLENGES
- Spike during viral events (10x traffic)
- Celebrity tweets (1M notifications in seconds)
- Rate limiting critical (prevent spam)
```

### For **Banking App** (like Chase):
```
STEP 1: TRAFFIC
- Transactional: 3-5 notifications/user/day
- 30M active accounts
- 120M notifications/day
- Types: transactions, balance, security, OTP

STEP 2: CHANNEL MIX
- SMS: 40% (OTP, fraud alerts - security critical)
- Push: 35% (transaction confirmations)
- Email: 25% (statements, monthly summaries)

STEP 3: COST (Higher due to SMS!)
- SMS: 48M × $0.0075 = $360/day
- Push: 42M × $0 = $0
- Email: 30M × $0.001 = $30/day
→ Daily: $390 | Monthly: $11,700

STEP 4: RELIABILITY
- SLA: 99.99% (financial compliance)
- Multi-provider redundancy mandatory
- Audit logs: 7 years retention
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Ride-Sharing App (Uber-like)

```
Given:
- 50M DAU
- 2 rides/user/day
- 5 notifications per ride (driver assigned, arrived, started, completed, rated)
- Channel mix: 70% push, 20% SMS, 10% email

Calculate:
1. Total notifications/day
2. Peak QPS (assume 6-hour peak, 5x multiplier)
3. Daily cost
4. Monthly storage (90-day retention, 800 bytes/notification)

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. TOTAL NOTIFICATIONS/DAY:
   - Rides/day = 50M users × 2 rides = 100M rides
   - Notifications = 100M × 5 = 500M notifications/day

2. PEAK QPS:
   - Avg QPS = 500M ÷ 100K = 5,000 QPS
   - Peak QPS = 5K × 5 = 25,000 QPS

3. DAILY COST:
   - Push: 500M × 70% × $0 = $0
   - SMS: 500M × 20% × $0.0075 = $750
   - Email: 500M × 10% × $0.001 = $50
   - Total: $800/day = $24K/month

4. MONTHLY STORAGE (90 days):
   - Daily: 500M × 800 bytes = 400 GB/day
   - 90 days: 400 GB × 90 = 36 TB
```
</details>

---

### Problem 2: Food Delivery App (DoorDash-like)

```
Given:
- 20M DAU
- 1.5 orders/user/day
- 8 notifications per order
  (order placed, restaurant accepted, preparing, ready, driver assigned,
   picked up, in transit, delivered)
- Channel mix: 50% push, 40% SMS, 10% email
- Retention: 60 days

Calculate:
1. Avg and peak QPS (4x peak multiplier)
2. Storage requirements
3. Monthly costs (channels + infrastructure)

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. QPS:
   - Orders/day = 20M × 1.5 = 30M orders
   - Notifications = 30M × 8 = 240M/day
   - Avg QPS = 240M ÷ 100K = 2,400 QPS
   - Peak QPS = 2,400 × 4 = 9,600 QPS

2. STORAGE (60 days):
   - Daily: 240M × 700 bytes = 168 GB/day
   - 60 days: 168 GB × 60 = 10 TB

3. MONTHLY COSTS:
   Channel costs:
   - Push: 120M × $0 = $0
   - SMS: 96M × $0.0075 = $720/day = $21,600/month
   - Email: 24M × $0.001 = $24/day = $720/month
   Total channels: $22,320/month
   
   Infrastructure (estimated):
   - Kafka: $2,000
   - DB: $1,800
   - Redis: $600
   - Workers: $3,000
   - Other: $600
   Total infra: $8,000/month
   
   TOTAL: $30,320/month
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Designing for Average Load**
```
✗ BAD:  "We need 2K QPS capacity"
✓ GOOD: "We need 2K QPS average, so 8-10K QPS for peaks (4-5x)"
```

### Mistake 2: **Ignoring Channel Costs**
```
✗ BAD:  "Just send everything via SMS"
✓ GOOD: "SMS costs $0.0075 each. For 100M notifications, that's $750K/day!
         Use push (free) where possible, SMS only for critical."
```

### Mistake 3: **Underestimating Storage Growth**
```
✗ BAD:  "1TB should be enough"
✓ GOOD: "200M notifications/day × 700 bytes × 90 days = 12.6 TB
         Plus 20% buffer = 15 TB to be safe"
```

### Mistake 4: **Forgetting Delivery Failures**
```
✗ BAD:  "All notifications deliver successfully"
✓ GOOD: "Assume 5% retry rate (provider failures, network issues).
         Need 5% extra capacity + dead-letter queue for permanent failures"
```

### Mistake 5: **Not Accounting for Multi-Channel**
```
✗ BAD:  "One notification = one delivery"
✓ GOOD: "Some users get email AND push AND SMS for critical events.
         1 notification event may = 3 channel deliveries"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
NOTIFICATION SYSTEM: ___________________

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Daily Active Users (DAU):         [____] M
Notifications/user/day:           [____]
Total notifications/day:          [____] M

→ Avg QPS = [____] M ÷ 100K       = [____]
→ Peak QPS = [____] × [____]      = [____]

Channel Breakdown:
→ Push (____%):  [____] M × $0         = $0
→ Email (____%): [____] M × $0.001     = $[____]
→ SMS (____%):   [____] M × $0.0075    = $[____]

STEP 2: STORAGE ESTIMATION
───────────────────────────
Bytes per notification:           [____] bytes
Retention period:                 [____] days

→ Daily Storage = [____] M × [____] B  = [____] GB
→ Total Storage = [____] GB × [____] d = [____] TB

STEP 3: COST ESTIMATION
───────────────────────────
→ Daily channel cost     = $[____]
→ Monthly channel cost   = $[____] × 30 = $[____]

Infrastructure (monthly):
→ Kafka cluster          = $[____]
→ Database               = $[____]
→ Redis                  = $[____]
→ Workers                = $[____]
→ Total infra            = $[____]

TOTAL MONTHLY: $[____] (channels) + $[____] (infra) = $[____]

STEP 4: CAPACITY PLANNING
───────────────────────────
→ Kafka partitions needed   = [____] (for parallelism)
→ Worker instances          = Peak QPS ÷ [____]/worker = [____]
→ Database write capacity   = [____] writes/sec
→ Redis cache size          = [____] GB (preferences)

SMELL TEST:
───────────────────────────
□ QPS reasonable? (compare to benchmarks)
□ Cost makes sense? (SMS >> email > push)
□ Storage reasonable? (TB range for millions of notifs)
□ Peak capacity 4-6x average? (handle traffic spikes)
```

---

## 🎁 Bonus: Notification System Cheat Sheet (1-Page)

```
╔════════════════════════════════════════════════════════╗
║        NOTIFICATION SYSTEM SCALE CHEAT SHEET           ║
╚════════════════════════════════════════════════════════╝

MEMORY ANCHORS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
• Typical user: 10 notifications/day
• Push: FREE | Email: $0.001 | SMS: $0.0075
• Notification size: ~700 bytes (with metadata)
• Peak multiplier: 4-6x average

FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
QPS = (DAU × Notifs/user/day) ÷ 100K
Storage = Notifs/day × 700 bytes × Retention days
Cost/day = (SMS count × $0.0075) + (Email count × $0.001)
Peak QPS = Avg QPS × 4 (design for this!)

CHANNEL SELECTION:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Critical + Real-time:  SMS (expensive but reliable)
Transactional:         Email (audit trail, rich content)
Real-time updates:     Push (free, instant, high engagement)
Marketing/Bulk:        Email (cheapest per impression)

TYPICAL SCALE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Small:   1M notifs/day,   <100 QPS,     $100/month
Medium:  100M notifs/day, 1K-5K QPS,    $10K/month
Large:   1B notifs/day,   10K-50K QPS,  $100K/month
Huge:    10B+ notifs/day, >100K QPS,    $1M+/month

INTERVIEW FLOW:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. Clarify notification types (5 min)
   → Transactional? Marketing? Social?

2. Estimate volume (5 min)
   → DAU × notifs/user → QPS → Peak QPS

3. Choose channels (3 min)
   → Based on cost, latency, reliability

4. Design architecture (20 min)
   → Queue-based, multi-channel workers, retries

5. Cost & scale discussion (7 min)
   → Show channel cost awareness
   → Storage retention trade-offs

SANITY CHECKS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
✓ Is push notification free? YES
✓ Is SMS 7-10x more expensive than email? YES
✓ Should we design for 4x average load? YES
✓ Can Kafka handle 50K QPS? YES (with proper partitioning)

✗ Can we send 1B SMS/day on small budget? NO ($7.5M/day cost!)
✗ Should we use SMS for marketing? NO (use email, it's 7x cheaper)
✗ Design for average QPS? NO (design for peak!)
✗ Keep all logs forever? NO (storage explodes, use retention policies)
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one system and practice full estimation in 10 minutes:

1. **Instagram-like Social App** - Heavy notification traffic
2. **Stripe-like Payment Platform** - Critical transactional alerts
3. **Netflix-like Streaming** - Recommendation and content updates
4. **Airbnb-like Marketplace** - Booking lifecycle notifications
5. **Slack-like Collaboration** - Real-time messaging and @mentions

**Goal:** Complete the template in under 10 minutes with reasonable estimates!

---

## 📚 Additional Resources

- **AWS SNS/SES Documentation** - Learn provider pricing models
- **Twilio Pricing Calculator** - Understand SMS cost by country
- **"Designing Data-Intensive Applications"** - Chapter on message queues
- **Firebase Cloud Messaging Docs** - Push notification best practices

---

**Remember**:
> "The goal is demonstrating COST-AWARENESS, PEAK-PLANNING, and CHANNEL-OPTIMIZATION thinking."

**Now go crush those notification system interviews!** 🚀🔔

---

*Created with the POWER technique for Notification Service Architecture*
*Perfect for: System Design interviews, Technical discussions, Cost optimization analysis*
