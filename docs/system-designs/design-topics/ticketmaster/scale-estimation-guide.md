# 🎯 TicketMaster System Design: Scale Estimation Masterclass

## The POWER Technique for Scale Math
**(P)rinciples → (O)rder of magnitude → (W)rite it down → (E)stimate → (R)ound ruthlessly**

This is a **mental framework** you can apply to ANY high-concurrency system design problem.

---

## 📊 PART 1: Users & Scale Estimation

### Core Assumptions Table

| **Category** | **Metric** | **Value** | **Justification** |
|--------------|------------|-----------|-------------------|
| **User Base** | Total Users | 200M | Similar to Ticketmaster/Eventbrite scale |
| | Daily Active Users (DAU) | 10M | ~5% browse events daily |
| | Peak Event Users | 100K | Taylor Swift concert release |
| | Read:Write Ratio | 1000:1 | Most browse, few actually book |
| **Time Distribution** | Peak Hours | 4 hours/day | Evening (6-10 PM) concentrated usage |
| | Peak Traffic Multiplier | 10x | Major event releases spike dramatically |
| **Events & Bookings** | Events in system | 500K | Sports, concerts, theater, festivals |
| | Events created/day | 5K | Promoters add new events |
| | Bookings/day | 500K | Successful ticket purchases |
| | Peak bookings/minute | 10K | During Taylor Swift release |
| | Avg seats per venue | 10K | Range: 100 (theater) to 100K (stadium) |
| **Booking Behavior** | Cart abandonment | 70% | Users reserve but don't pay |
| | Avg booking attempt time | 10 min | From search to payment |
| | Seats per booking | 2.5 | Average group size |

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
✗ BAD:  500,000 ÷ 86,400
✓ GOOD: 500K ÷ 100K = 5 QPS
        (Just subtract the zeros: 6 zeros - 5 zeros = 1 zero = 10 = ~5)
```

### Rule #3: **The Peak vs Average Rule**
For ticketing systems, peaks matter MORE than average:
```
Average booking QPS: 5
Peak booking QPS: 5 × 10 = 50 (typical)
Taylor Swift release: 5 × 100 = 500 (extreme spike)

ALWAYS design for peak, not average!
```

---

## 📈 PART 3: Quick Scale Math Template (COPY THIS!)

```
┌─────────────────────────────────────────────────────────┐
│  🎯 TICKETMASTER NAPKIN MATH TEMPLATE                   │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Search/Browse Operations/Day:  [____] M
Booking Attempts/Day:          [____] K
Successful Bookings/Day:       [____] K
Payment Transactions/Day:      [____] K
Read:Write Ratio:              [____]:1

→ Search QPS = Searches/Day ÷ 100K      = [____]
→ Booking QPS = Bookings/Day ÷ 100K     = [____]
→ Payment QPS = Payments/Day ÷ 100K     = [____]
→ Peak QPS = Average × Peak Multiplier  = [____]

STEP 2: STORAGE ESTIMATION
───────────────────────────
Events:          [____] K events × [____] KB  = [____] GB
Seats:           [____] M seats × [____] bytes = [____] GB
Bookings:        [____] M bookings × [____] KB = [____] TB
Users:           [____] M users × [____] KB    = [____] GB
Total Storage:                                  = [____] TB

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
→ Search Bandwidth = Search QPS × Response Size  = [____] MB/s
→ Booking Bandwidth = Booking QPS × Request Size = [____] MB/s
→ Peak Bandwidth = Normal × 10                   = [____] GB/s

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Hot Events (trending): 1% of events
→ Cache Size = 1% × Event Data + Popular Searches
```

---

## 💾 PART 4: TicketMaster Filled Template

```
┌─────────────────────────────────────────────────────────┐
│      TICKETMASTER SYSTEM - NAPKIN MATH SOLUTION         │
└─────────────────────────────────────────────────────────┘

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Search/Browse Operations/Day:  50 M (users browsing events)
Booking Attempts/Day:          1.5 M (users try to book)
Successful Bookings/Day:       500 K (30% success rate)
Payment Transactions/Day:      500 K (same as bookings)
Read:Write Ratio:              100:1 (mostly browsing)

→ Search QPS = 50M ÷ 100K          = 500 QPS
→ Booking QPS = 500K ÷ 100K        = 5 QPS
→ Payment QPS = 500K ÷ 100K        = 5 QPS
→ Peak QPS = 500 × 10              = 5,000 QPS (normal peak)
→ Extreme Peak (Taylor Swift) = 500 × 100 = 50,000 QPS

STEP 2: STORAGE ESTIMATION
───────────────────────────
A. Events Data:
  - Events: 500K events
  - Per event: name, description, date, venue, images (10 KB avg)
  - Total: 500K × 10 KB = 5 GB

B. Seats Data:
  - Avg venue size: 10K seats
  - Total seats: 500K events × 10K = 5 Billion seats
  - Per seat: seat_id, section, row, number, price, status (100 bytes)
  - Total: 5B × 100 bytes = 500 GB

C. Bookings Data:
  - Bookings/day: 500K
  - Retention: 5 years
  - Total bookings: 500K × 365 × 5 = 912 Million bookings
  - Per booking: user, event, seats, amount, status, timestamps (500 bytes)
  - Total: 912M × 500 bytes = 456 GB ≈ 0.5 TB

D. Users Data:
  - Total users: 200M
  - Per user: profile, email, payment methods, history (2 KB)
  - Total: 200M × 2 KB = 400 GB

E. Analytics/Logs:
  - Click streams, searches, page views
  - 50M operations/day × 365 × 1 year × 200 bytes = 3.6 TB

TOTAL STORAGE: 5 GB + 500 GB + 500 GB + 400 GB + 3.6 TB ≈ 5 TB

STEP 3: BANDWIDTH ESTIMATION
───────────────────────────
→ Search Bandwidth = 500 QPS × 50 KB     = 25 MB/s
→ Booking Bandwidth = 5 QPS × 10 KB      = 50 KB/s
→ Payment Bandwidth = 5 QPS × 5 KB       = 25 KB/s
→ Peak Bandwidth = 25 MB/s × 10          = 250 MB/s ≈ 2 Gbps

STEP 4: MEMORY (CACHE) ESTIMATION
───────────────────────────
Hot Events (trending concerts, sports):
→ 1% of 500K events = 5K hot events
→ Each event + seat map: 10 KB + 100 KB = 110 KB
→ Cache Size = 5K × 110 KB = 550 MB

Popular Searches:
→ Cache 10K popular queries × 50 KB = 500 MB

Session Data (active bookings in progress):
→ Peak concurrent bookings: 10K users × 50 KB = 500 MB

Total Redis Cache: 550 MB + 500 MB + 500 MB ≈ 1.5 GB
```

---

## 🧠 PART 5: Mental Math Techniques You MUST Master

### **Technique 1: The "Concurrency Factor"**
*(For Ticketing Systems)*
```
EMOTION TRIGGER: "Peak matters more than average!"

Normal traffic:     500 QPS search
Peak (weekend):     5,000 QPS (10× spike)
Event release:      50,000 QPS (100× spike!)

So: ALWAYS multiply average by 10-100× for infrastructure sizing
```

### **Technique 2: The "Abandonment Rate"**
*(For Booking Funnels)*
```
Booking funnel:
100 users search → 10 users try to book → 3 users complete payment

Conversion rate: 3% (typical for high-demand events)
Cart abandonment: 70% (users reserve but timeout)

Key Insight: Design for 10× more booking attempts than successful purchases!
```

### **Technique 3: The "Lock Contention Math"**
*(For Seat Reservations)*
```
Scenario: Taylor Swift concert, 50K seats, 100K concurrent users

Contention: 100K users ÷ 50K seats = 2× oversubscription
Average lock hold time: 5 seconds (DB query + update)
Lock timeouts expected: YES (design retry + waitlist)

Formula: If users > seats AND high concurrency → Use distributed locks!
```

### **Technique 4: The "Timeout Cascade"**
```
🎯 REPEAT THIS WITH EMOTION:
"10 minutes to book, 30 seconds for lock, 5 seconds for DB query"

- User booking timeout: 10 minutes (business requirement)
- Distributed lock timeout: 30 seconds (technical safeguard)
- Database query timeout: 5 seconds (prevent deadlocks)

Cascade: Each layer has shorter timeout than the one above!
```

---

## 🎨 PART 6: The Visual Mind Map Approach

```
                 🎫 TICKETMASTER SYSTEM
                          |
        ┌─────────────────┼─────────────────┐
        |                 |                 |
    📊 SCALE          💾 STORAGE        🔧 COMPUTE
        |                 |                 |
    ┌───┴───┐         ┌───┴───┐        ┌───┴───┐
   DAU    QPS       Size    Time     Servers  Cache
   10M    5K        5TB     5yr       100     1.5GB
```

**Memory Trigger**: Think **"S.S.C."** = Scale, Storage, Compute

---

## 🏗️ PART 7: Domain Model for TicketMaster

```java
// Think in terms of domain entities first!

@Entity
class Event {
    UUID id;
    String name;
    Venue venue;
    LocalDateTime eventDate;
    int totalSeats;
    int availableSeats;  // Denormalized for quick check

    // Scale Insight: 500K events × 10 KB = 5 GB
}

@Entity
class Seat {
    UUID id;
    UUID eventId;
    String section;
    String row;
    String seatNumber;
    BigDecimal price;
    SeatStatus status;  // AVAILABLE, RESERVED, SOLD, BLOCKED
    LocalDateTime reservedUntil;  // Auto-release mechanism

    // Scale Insight: 5B seats × 100 bytes = 500 GB
    // Critical: Index on (eventId, status) for fast queries
}

@Entity
class Booking {
    UUID id;
    UUID userId;
    UUID eventId;
    List<UUID> seatIds;
    BigDecimal totalAmount;
    BookingStatus status;  // PENDING, CONFIRMED, CANCELLED
    LocalDateTime createdAt;
    LocalDateTime expiresAt;  // 10-minute timeout

    // Scale Insight: 912M bookings × 500 bytes = 0.5 TB
    // High write load: 5 QPS average, 500 QPS peak
}

@Service
class BookingService {
    // THINK: How do we prevent double booking?
    // Answer: Distributed lock (Redis) + Database transaction

    // THINK: What happens when user times out?
    // Answer: Background job releases seats every minute
}
```

---

## 🎯 PART 8: The Interview Cheat Sheet (Print This!)

```
┌──────────────────────────────────────────────────┐
│ TICKETMASTER SCALE ESTIMATION - 5 MIN RITUAL     │
└──────────────────────────────────────────────────┘

[ ] 1. Write down: DAU, Events, Seats per event
[ ] 2. Calculate QPS: Operations/day ÷ 100K
[ ] 3. Calculate Peak QPS: Normal × 10 (or × 100 for extreme)
[ ] 4. Calculate Storage: Events + Seats + Bookings + Users
[ ] 5. Calculate Cache: 1% hot events + session data
[ ] 6. Concurrency Check: Users > Seats? → Need locks!
[ ] 7. Timeout Design: Booking (10min) > Lock (30s) > DB (5s)
[ ] 8. Smell Test: Can 100 servers handle 50K QPS? YES!
```

---

## 🚀 Key Metrics Summary Table

| **Metric** | **Value** | **Why It Matters** |
|------------|-----------|-------------------|
| **Search QPS** | 500 (avg), 5K (peak) | Elasticsearch cluster sizing |
| **Booking QPS** | 5 (avg), 500 (peak) | Redis lock capacity planning |
| **Payment QPS** | 5 (avg), 500 (peak) | Stripe API rate limits |
| **Peak Concurrent Users** | 100K | Virtual queue sizing |
| **Storage (5yr)** | 5 TB | Database + S3 sizing |
| **Cache Size** | 1.5 GB | Redis instance sizing |
| **Bandwidth** | 2 Gbps (peak) | Network infrastructure |
| **Lock Hold Time** | <5 sec | Critical for throughput |
| **Booking Timeout** | 10 min | Business requirement |

---

## 💡 Pro Architect Tips

### **Tip 1: The Spike Test**
After calculations, ask:
- "Can the system handle Taylor Swift ticket release?" → YES (designed for 100× spike)
- "Will distributed locks prevent double booking?" → YES (Redis Redlock)
- "What happens if payment gateway is slow?" → Circuit breaker + retry

### **Tip 2: The Comparison Anchor**
Always compare to known systems:
- "TicketMaster peak ≈ Black Friday on e-commerce"
- "Booking contention ≈ Airline seat selection"
- "Queue system ≈ Apple iPhone launch"

### **Tip 3: Start with Constraints**
Always ask first:
1. How many concurrent users during peak event?
2. How many seats per venue (affects lock contention)?
3. How long can users hold seats (timeout policy)?
4. What's acceptable double-booking rate? (ZERO!)

---

## 🎓 Professor's Final Wisdom

> **"In ticketing systems, CONSISTENCY beats AVAILABILITY - can't sell same seat twice!"**

Your interviewer wants to see:
1. ✅ Understanding of CAP theorem (choosing CP over AP)
2. ✅ Concurrency control mechanisms (locks, transactions)
3. ✅ Peak vs average traffic planning
4. ✅ Timeout cascades and failure handling

**NOT NEEDED:**
- ❌ Exact QPS numbers (order of magnitude is enough)
- ❌ Perfect cache hit ratios (80% is fine)
- ❌ Zero downtime (99.95% SLA is realistic)

---

## 🔁 Repetition Backed by Emotion (Your Power Principle!)

**REPEAT 3 TIMES OUT LOUD:**
1. *"Peak traffic is 100× average - I must design for spikes!"*
2. *"Distributed locks prevent double booking - consistency is CRITICAL!"*
3. *"Timeout cascade: 10min > 30s > 5s - each layer protects the next!"*

**VISUALIZE:** You're at the whiteboard, the interviewer nods as you confidently say: "So Taylor Swift has 50K seats, but 100K users will try to book simultaneously. We need distributed locks with 30-second timeout, and the first 100K users wait in a virtual queue..."

---

## 📚 Quick Reference: Common System Scale Benchmarks

| **System Type** | **DAU** | **Peak QPS** | **Storage (1yr)** | **Key Challenge** |
|-----------------|---------|--------------|-------------------|-------------------|
| TicketMaster | 10M | 50K | 1 TB | Concurrency, double booking |
| Uber | 100M | 1M | 100 TB | Geo-spatial, real-time matching |
| Netflix | 200M | 1M | 10+ PB | Video streaming, CDN |
| Twitter | 200M | 300K | 10 PB | Timeline generation, fan-out |
| Instagram | 500M | 50K | 100 PB | Image storage, feed ranking |

---

## 🔧 Practical Application: Adapting This Template

### For a **Concert Ticket Release** (Taylor Swift):
```
STEP 1: TRAFFIC
- Expected users: 100K concurrent
- Seats available: 50K
- Contention ratio: 2:1 (high!)
- Queue needed: YES (throttle to 1K users/min)

STEP 2: STORAGE
- Event: 1 event × 10 KB = 10 KB
- Seats: 50K seats × 100 bytes = 5 MB
- Bookings: 50K × 500 bytes = 25 MB (negligible)

STEP 3: CONCURRENCY
- Lock contention: VERY HIGH
- Solution: Virtual queue + distributed locks
- Alternative: Lottery system (random selection)

STEP 4: CACHE
- Pre-cache entire venue map in Redis
- Seat availability updates via WebSocket
- Cache invalidation on every booking
```

### For a **Sports Season Tickets**:
```
STEP 1: TRAFFIC
- Lower peak (season long sales window)
- Package deals (multiple games)
- Lower concurrency (no rush)

STEP 2: STORAGE
- 82 games × 20K seats = 1.6M seat-events
- More historical data (multi-season)

STEP 3: CONCURRENCY
- Low contention (spread over weeks)
- Simple optimistic locking sufficient
- No queue needed

STEP 4: FEATURES
- Recommendation engine (based on past attendance)
- Dynamic pricing (demand prediction)
- Flexible payment plans
```

---

## 🎯 Mental Math Practice Problems

### Problem 1: Super Bowl Ticket Release
```
Given:
- Stadium capacity: 70K seats
- Expected concurrent users: 500K
- Tickets on sale: 50K (rest reserved)
- Peak booking window: 30 minutes
- Avg seats per booking: 4

Calculate:
1. Contention ratio
2. Required booking QPS capacity
3. How many users can book successfully?
4. Queue throughput needed

[Try it yourself, then check answers below]
```

<details>
<summary>Answer</summary>

```
1. CONTENTION RATIO:
   - Available seats: 50K
   - Concurrent users: 500K
   - Contention: 500K ÷ 50K = 10:1 (VERY HIGH!)

2. REQUIRED BOOKING QPS:
   - Successful bookings: 50K seats ÷ 4 seats/booking = 12.5K bookings
   - Time window: 30 min = 1,800 seconds
   - Booking QPS needed: 12.5K ÷ 1,800 = ~7 QPS (successful)
   - But attempts: 7 × 10 (contention) = 70 QPS (attempts)
   - Add retry factor (×2): 140 QPS capacity needed

3. SUCCESSFUL BOOKINGS:
   - Only 12.5K out of 500K users (2.5% success rate)
   - Need waitlist for remaining 487.5K users

4. QUEUE THROUGHPUT:
   - Admit users at: 500K users ÷ 1,800 sec = 278 users/sec
   - Throttle to prevent thundering herd: 100 users/sec
   - Extended sale window: 500K ÷ 100 = 5,000 sec ≈ 83 minutes
```
</details>

---

### Problem 2: Broadway Show Ticket System
```
Given:
- Theater capacity: 1,500 seats
- Shows per week: 8 (4 days × 2 shows)
- Active shows: 20 productions
- Avg booking window: 3 months in advance
- Peak time: Sunday morning (new shows announced)
- Expected users: 50K concurrent (peak)

Calculate:
1. Total seats to manage
2. Peak booking QPS
3. Storage requirements
4. Cache strategy

[Try it yourself]
```

<details>
<summary>Answer</summary>

```
1. TOTAL SEATS:
   - Per show: 1,500 seats
   - Shows per week: 8
   - Weeks in advance: 12 (3 months)
   - Active shows: 20
   - Total: 20 × 1,500 × 8 × 12 = 2.88M seats

2. PEAK BOOKING QPS:
   - Concurrent users: 50K
   - Avg booking time: 5 minutes (faster than concerts)
   - Concurrent bookings: 50K ÷ (5 × 60) = 167 bookings/sec
   - System capacity needed: 167 × 2 (safety) = 334 QPS

3. STORAGE:
   - Seats: 2.88M × 100 bytes = 288 MB
   - Bookings (1 year): 1,500 × 8 × 52 × 20 × 500 bytes = 600 MB
   - Total: <1 GB (small system)

4. CACHE STRATEGY:
   - Cache hot shows (new releases, popular): 5 shows
   - Seat maps: 5 × 1,500 × 100 bytes = 750 KB
   - Availability counters in Redis: Real-time updates
   - Session data: 50K users × 10 KB = 500 MB
   - Total cache: ~500 MB (easily fits in single Redis instance)
```
</details>

---

## 🚨 Common Mistakes to Avoid

### Mistake 1: **Designing for Average, Not Peak**
```
✗ BAD:  "Average booking QPS is 5, so 10 servers are enough"
✓ GOOD: "Average is 5, but Taylor Swift spike is 500 QPS, need 100 servers + auto-scaling"
```

### Mistake 2: **Ignoring Lock Contention**
```
✗ BAD:  "Use optimistic locking for seat booking"
✓ GOOD: "Use distributed locks (Redis Redlock) to prevent double booking under high contention"
```

### Mistake 3: **Forgetting Timeout Cascades**
```
✗ BAD:  "User has 10 minutes to book, that's the only timeout"
✓ GOOD: "User: 10min, Lock: 30s, DB query: 5s - each layer times out faster"
```

### Mistake 4: **Not Planning for Failures**
```
✗ BAD:  "Payment succeeded, booking confirmed"
✓ GOOD: "Payment succeeded, but DB update failed → Compensating transaction (refund + release seats)"
```

### Mistake 5: **Underestimating Cart Abandonment**
```
✗ BAD:  "500K bookings/day means 500K payments/day"
✓ GOOD: "70% abandon cart → need 1.5M booking attempts to get 500K successful payments"
```

---

## 📝 Your Practice Template (Fill-in-the-Blank)

```
SYSTEM: TICKETMASTER FOR _________________

STEP 1: TRAFFIC ESTIMATION
───────────────────────────
Venue capacity (seats):       [____]
Expected concurrent users:    [____]
Booking window (minutes):     [____]
Contention ratio:             [____]:1

Total Searches/Day:           [____] M
Total Bookings/Day:           [____] K
Success rate:                 [____] %

→ Search QPS = [____] M ÷ 100K    = [____]
→ Booking QPS = [____] K ÷ 100K   = [____]
→ Peak QPS = [____] × [____]      = [____]

STEP 2: CONCURRENCY CONTROL
───────────────────────────
Contention level:             [ ] Low  [ ] Medium  [ ] HIGH
Lock strategy:                [ ] Optimistic  [ ] Pessimistic (distributed)
Queue needed:                 [ ] No  [ ] Yes (virtual waiting room)

Lock hold time:               [____] seconds
Booking timeout:              [____] minutes
Expected conflicts:           [ ] Few  [ ] Many

STEP 3: STORAGE ESTIMATION
───────────────────────────
Events:          [____] K × [____] KB  = [____] GB
Seats:           [____] M × [____] B   = [____] GB
Bookings:        [____] M × [____] KB  = [____] GB
Total:                                  = [____] TB

STEP 4: CACHE STRATEGY
───────────────────────────
Hot events (trending):        [____] % = [____] events
Session data (active users):  [____] K users
Lock keys (Redis):            [____] K keys

Total Redis Cache:            [____] GB

SMELL TEST:
───────────────────────────
□ Contention handled? (locks for high, none for low)
□ Peak traffic planned? (10-100× average)
□ Timeouts cascaded? (user > lock > db)
□ Double booking prevented? (distributed locks + transactions)
□ Payment failures handled? (retry + compensating transaction)
```

---

## 🎁 Bonus: Concurrency Control Cheat Sheet

```
╔════════════════════════════════════════════════════════╗
║     TICKETING SYSTEM CONCURRENCY CHEAT SHEET           ║
╚════════════════════════════════════════════════════════╝

CONTENTION LEVELS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Low:     Users < Seats × 0.1   → Optimistic locking OK
Medium:  Users < Seats × 1     → Pessimistic locking
High:    Users > Seats         → Distributed locks + Queue

LOCK PATTERNS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Optimistic:
  1. Read seat (version: 1)
  2. Update seat SET status='SOLD' WHERE version=1
  3. If rows_affected = 0 → Retry

Pessimistic (Database):
  1. SELECT ... FOR UPDATE (row lock)
  2. UPDATE seat SET status='SOLD'
  3. COMMIT (releases lock)

Distributed (Redis Redlock):
  1. SETNX lock:seat:A-101 uuid EX 30
  2. If success → Update DB
  3. DEL lock:seat:A-101 (release)
  4. If timeout → Auto-expires after 30s

TIMEOUT CASCADE:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
User timeout:       10 minutes (booking expiry)
Lock timeout:       30 seconds (prevent deadlock)
DB query timeout:   5 seconds (prevent long transactions)
HTTP timeout:       3 seconds (API response)

Rule: Each layer timeout < Parent layer timeout

QUEUE STRATEGIES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
FIFO Queue:         Fair, but predictable (bots exploit)
Random Queue:       Unpredictable, bot-resistant ✓
Priority Queue:     Verified users first
Lottery:            Random selection (Apple uses this)

FAILURE MODES:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Lock acquisition fails → Retry 3 times, then waitlist
Payment fails → Release seats + notify waitlist
Timeout expires → Background job releases seats
DB deadlock → Rollback + retry with exponential backoff

CAPACITY FORMULAS:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Lock throughput = 1 / avg_lock_hold_time
  Example: 1 / 5s = 0.2 locks/sec per seat

Max concurrent bookings = seats × (1 / lock_hold_time)
  Example: 50K seats × 0.2 = 10K bookings/sec theoretical max

With retries: Actual throughput = Theoretical / (1 + retry_rate)
  Example: 10K / (1 + 0.5) = 6.7K bookings/sec realistic
╚════════════════════════════════════════════════════════╝
```

---

## 🎯 Final Challenge: Apply This Template

Pick one of these scenarios and practice the full estimation:

1. **Olympics Opening Ceremony** - 80K stadium, 5M people want tickets
2. **Hamilton Broadway** - 1,400 seats, very high demand, daily shows
3. **Local Movie Theater** - 200 seats, 8 shows/day, moderate demand
4. **Music Festival** - 3-day event, 100K capacity, multiple stages
5. **Airline Seat Selection** - 300 seats, 1K people on waitlist

Use the blank template above and time yourself: **Can you complete it in 5 minutes?**

---

## 📚 Additional Resources

- **Books**:
  - "Designing Data-Intensive Applications" by Martin Kleppmann
  - "Database Internals" by Alex Petrov (for lock mechanisms)
- **Papers**:
  - "Redlock: Distributed locks with Redis"
  - "Google Spanner: TrueTime for distributed transactions"
- **Real Systems**:
  - Ticketmaster Engineering Blog
  - Stubhub Architecture Talks
  - Eventbrite Tech Blog

---

**Remember**:
> "In ticketing systems, preventing double-booking is MORE important than low latency. Consistency > Availability!"

**Now go design systems that can handle Taylor Swift ticket releases!** 🎫🚀

---

*Created with the POWER technique: Principles → Order → Write → Estimate → Round*
*Perfect for: FAANG interviews, Ticketing Systems, High-Concurrency Design, E-commerce Platforms*
