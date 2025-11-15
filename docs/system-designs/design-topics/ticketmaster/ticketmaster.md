# Design a Ticketing Platform (Ticketmaster/Eventbrite)

**Companies**: Ticketmaster, Eventbrite, StubHub, SeatGeek
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a high-scale ticketing platform like Ticketmaster. Users can search for events, select seats, book tickets, and make payments. The system must handle high concurrency during popular event releases (concerts, sports) and prevent double booking.

---

## Step 1: Requirements (10 min)

### Functional Requirements

1. **Event Management**: Create, update, browse events (concerts, sports, theater)
2. **Search**: Full-text search with filters (location, date, category, price)
3. **Seat Selection**: Interactive venue map, real-time availability
4. **Booking**: Reserve seats with timeout (10 minutes)
5. **Payment**: Process payments via Stripe/PayPal
6. **Tickets**: Generate QR code tickets, send via email/SMS
7. **Waitlist** (optional): Join waitlist for sold-out events
8. **Virtual Queue** (optional): Manage high-demand event releases

**Prioritize**: Focus on search, seat selection, booking, and payment for MVP

### Non-Functional Requirements

1. **Strong Consistency**: Zero double booking (CRITICAL)
2. **High Availability**: 99.95% uptime
3. **Low Latency**: <200ms for booking operations
4. **Scalability**: Handle 100K concurrent users during peak releases
5. **Security**: PCI-DSS compliance for payments, fraud detection

### Capacity Estimation

**Assumptions**:
- 10M DAU (Daily Active Users)
- 500K events in system
- 500K successful bookings per day
- 100:1 read-write ratio (mostly browsing)
- Peak: Taylor Swift concert → 100K concurrent users

**Read Load**:
```
50M searches/day ÷ 86,400 sec = ~580 searches/sec
Peak: 580 × 10 = 5,800 searches/sec
```

**Write Load**:
```
500K bookings/day ÷ 86,400 sec = ~6 bookings/sec
Peak (extreme event): 10K bookings/min = 167 bookings/sec
```

**Storage**:
```
Events: 500K × 10 KB = 5 GB
Seats: 500K events × 10K seats × 100 bytes = 500 GB
Bookings (5 years): 500K/day × 365 × 5 × 500 bytes = 456 GB
Users: 200M × 2 KB = 400 GB
Total: ~1.5 TB
```

**Bandwidth**:
```
Search: 580 QPS × 50 KB = 29 MB/s
Booking: 6 QPS × 10 KB = 60 KB/s
Peak: 29 MB/s × 10 = 290 MB/s ≈ 2.5 Gbps
```

---

## Step 2: Architecture (25 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│              Clients (Web/Mobile)                   │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│             CDN (CloudFront/Akamai)                 │
│    • Static content (images, venue maps)            │
│    • API caching (event listings, short TTL)        │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│         Virtual Waiting Room (Queue-It)             │
│    • For high-demand events (Taylor Swift)          │
│    • Throttled admission: 1000 users/min            │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│        Application Load Balancer (AWS ALB)          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│        API Gateway (Kong / AWS API Gateway)         │
│    • Authentication (JWT, OAuth2)                   │
│    • Rate limiting (100 req/min per user)           │
│    • Request validation                             │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌────────────┐  ┌────────────┐  ┌────────────┐
│   Search   │  │  Booking   │  │  Payment   │
│  Service   │  │  Service   │  │  Service   │
└─────┬──────┘  └─────┬──────┘  └─────┬──────┘
      │               │               │
      ▼               ▼               ▼
┌────────────┐  ┌────────────┐  ┌────────────┐
│Elastic-    │  │   Redis    │  │   Stripe   │
│ search     │  │  (Locks)   │  │    API     │
└────────────┘  └─────┬──────┘  └────────────┘
                      │
                      ▼
              ┌────────────────┐
              │   PostgreSQL   │
              │   (Sharded)    │
              └────────┬───────┘
                       │
                       ▼
              ┌────────────────┐
              │  Read Replicas │
              └────────────────┘
```

### Components

**Load Balancer**:
- Distribute traffic across app servers
- Session affinity for booking flow (sticky sessions)
- Health checks every 10 seconds
- Auto-scaling: Min 10, Max 500 instances

**API Gateway**:
- Authentication (OAuth2, JWT)
- Rate limiting (token bucket algorithm)
- Request/response transformation
- Circuit breaker for downstream failures

**Microservices**:
1. **Search Service**: Elasticsearch for event discovery
2. **Booking Service**: Core logic for seat reservation
3. **Payment Service**: Stripe/PayPal integration
4. **Inventory Service**: Real-time seat availability
5. **Notification Service**: Email/SMS via SendGrid/Twilio
6. **User Service**: Authentication, profiles

**Cache (Redis)**:
- Distributed locks (Redlock algorithm)
- Session storage
- Rate limiting counters
- Hot event data

**Database (PostgreSQL)**:
- ACID transactions for bookings
- Sharding by event_id
- Read replicas for reporting

---

## Step 3: Data Model (10 min)

### Database Choice

**Why PostgreSQL?**
- Need ACID transactions (prevent double booking)
- Complex queries (joins, filters)
- Strong consistency required
- Mature sharding solutions (Citus, Vitess)

Alternative: **Hybrid approach**
- PostgreSQL for transactional data (bookings)
- Cassandra for analytics (click streams)
- Elasticsearch for search

### Schema

```sql
-- Events Table
CREATE TABLE events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    description TEXT,
    category VARCHAR(50), -- concert, sports, theater
    venue_id UUID REFERENCES venues(id),
    event_date TIMESTAMP NOT NULL,
    sale_start_date TIMESTAMP,
    total_seats INTEGER,
    available_seats INTEGER, -- denormalized for quick check
    status VARCHAR(20) DEFAULT 'UPCOMING', -- UPCOMING, ON_SALE, SOLD_OUT, CANCELLED
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_events_date ON events(event_date);
CREATE INDEX idx_events_category ON events(category);
CREATE INDEX idx_events_status ON events(status);

-- Venues Table
CREATE TABLE venues (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    city VARCHAR(100),
    country VARCHAR(100),
    capacity INTEGER,
    address TEXT,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    seat_map_url TEXT -- SVG/JSON seat map
);

CREATE INDEX idx_venues_location ON venues(city, country);

-- Seats Table (partitioned by event_id)
CREATE TABLE seats (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID REFERENCES events(id),
    section VARCHAR(50) NOT NULL,
    row VARCHAR(10) NOT NULL,
    seat_number VARCHAR(10) NOT NULL,
    price DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'AVAILABLE', -- AVAILABLE, RESERVED, SOLD, BLOCKED
    reserved_by UUID REFERENCES users(id),
    reserved_until TIMESTAMP, -- auto-release after 10 minutes
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(event_id, section, row, seat_number)
) PARTITION BY HASH (event_id);

CREATE INDEX idx_seats_event_status ON seats(event_id, status);
CREATE INDEX idx_seats_reserved ON seats(reserved_until)
    WHERE status = 'RESERVED';

-- Bookings Table
CREATE TABLE bookings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_reference VARCHAR(20) UNIQUE NOT NULL, -- BKG-ABC123
    user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    seat_ids UUID[], -- array of seat IDs
    total_amount DECIMAL(10, 2) NOT NULL,
    status VARCHAR(20) DEFAULT 'PENDING', -- PENDING, CONFIRMED, CANCELLED, EXPIRED
    payment_id VARCHAR(100), -- Stripe payment intent ID
    created_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP, -- 10 minutes from creation
    confirmed_at TIMESTAMP
);

CREATE INDEX idx_bookings_user ON bookings(user_id);
CREATE INDEX idx_bookings_event ON bookings(event_id);
CREATE INDEX idx_bookings_status ON bookings(status);
CREATE INDEX idx_bookings_expires ON bookings(expires_at)
    WHERE status = 'PENDING';

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    phone VARCHAR(20),
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);

-- Waitlist Table
CREATE TABLE waitlist (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    event_id UUID REFERENCES events(id),
    seats_requested INTEGER,
    created_at TIMESTAMP DEFAULT NOW(),
    notified_at TIMESTAMP,
    UNIQUE(user_id, event_id)
);

CREATE INDEX idx_waitlist_event ON waitlist(event_id, created_at);

-- Tickets Table
CREATE TABLE tickets (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    booking_id UUID REFERENCES bookings(id),
    seat_id UUID REFERENCES seats(id),
    qr_code TEXT, -- base64 encoded QR code
    ticket_url TEXT, -- S3 URL for PDF
    is_used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_tickets_booking ON tickets(booking_id);
```

---

## Step 4: APIs (5 min)

### Search Events

```http
GET /api/v1/events/search?
    q=concert&
    city=new-york&
    date_from=2025-12-01&
    date_to=2025-12-31&
    category=music&
    price_max=200&
    page=1&
    limit=20

Response: 200 OK
{
  "events": [
    {
      "id": "evt-123",
      "name": "Taylor Swift - Eras Tour",
      "venue": "Madison Square Garden",
      "city": "New York",
      "date": "2025-12-15T19:00:00Z",
      "priceRange": {"min": 80, "max": 500},
      "availability": "LOW", // HIGH, MEDIUM, LOW, SOLD_OUT
      "imageUrl": "https://cdn.example.com/taylor-swift.jpg"
    }
  ],
  "pagination": {
    "total": 150,
    "page": 1,
    "pages": 8
  }
}
```

### Get Event Details

```http
GET /api/v1/events/{eventId}

Response: 200 OK
{
  "id": "evt-123",
  "name": "Taylor Swift - Eras Tour",
  "description": "The most anticipated...",
  "venue": {
    "id": "venue-456",
    "name": "Madison Square Garden",
    "capacity": 20000,
    "seatMapUrl": "https://cdn.example.com/msg-seat-map.svg"
  },
  "date": "2025-12-15T19:00:00Z",
  "saleStartDate": "2025-11-01T10:00:00Z",
  "totalSeats": 20000,
  "availableSeats": 150,
  "status": "ON_SALE"
}
```

### Get Available Seats

```http
GET /api/v1/events/{eventId}/seats?section=FLOOR

Response: 200 OK
{
  "seats": [
    {
      "id": "seat-789",
      "section": "FLOOR",
      "row": "A",
      "seatNumber": "101",
      "price": 250.00,
      "status": "AVAILABLE"
    },
    {
      "id": "seat-790",
      "section": "FLOOR",
      "row": "A",
      "seatNumber": "102",
      "price": 250.00,
      "status": "AVAILABLE"
    }
  ]
}
```

### Create Booking (Reserve Seats)

```http
POST /api/v1/bookings
Content-Type: application/json
Authorization: Bearer {jwt_token}

Request:
{
  "eventId": "evt-123",
  "seatIds": ["seat-789", "seat-790"],
  "userId": "user-456"
}

Response: 201 Created
{
  "bookingId": "bkg-abc123",
  "bookingReference": "BKG-ABC123",
  "status": "PENDING",
  "seats": [
    {"section": "FLOOR", "row": "A", "seatNumber": "101"},
    {"section": "FLOOR", "row": "A", "seatNumber": "102"}
  ],
  "totalAmount": 500.00,
  "expiresAt": "2025-11-15T10:10:00Z", // 10 minutes from now
  "timeRemaining": 600 // seconds
}
```

### Complete Payment

```http
POST /api/v1/bookings/{bookingId}/payment
Content-Type: application/json

Request:
{
  "paymentMethodId": "pm_1234567890", // Stripe payment method
  "amount": 500.00
}

Response: 200 OK
{
  "bookingId": "bkg-abc123",
  "status": "CONFIRMED",
  "paymentId": "pi_1234567890",
  "tickets": [
    {
      "ticketId": "tkt-001",
      "seat": {"section": "FLOOR", "row": "A", "seatNumber": "101"},
      "qrCode": "data:image/png;base64,iVBORw0KG...",
      "downloadUrl": "https://s3.amazonaws.com/tickets/tkt-001.pdf"
    }
  ]
}
```

### Join Waitlist

```http
POST /api/v1/events/{eventId}/waitlist
Content-Type: application/json

Request:
{
  "userId": "user-456",
  "seatsRequested": 2
}

Response: 201 Created
{
  "waitlistId": "wl-789",
  "position": 523,
  "estimatedNotification": "We'll notify you if seats become available"
}
```

---

## Step 5: Core Algorithm - Seat Booking with Concurrency Control

### Approach 1: Optimistic Locking (NOT RECOMMENDED for high contention)

```python
def book_seats_optimistic(event_id, seat_ids, user_id):
    # Read seats with version
    seats = db.query("""
        SELECT id, status, version
        FROM seats
        WHERE id IN %(seat_ids)s AND event_id = %(event_id)s
    """, seat_ids=seat_ids, event_id=event_id)

    # Check availability
    if any(seat.status != 'AVAILABLE' for seat in seats):
        return error("Some seats are no longer available")

    # Try to update with version check
    for seat in seats:
        rows_affected = db.execute("""
            UPDATE seats
            SET status = 'RESERVED',
                reserved_by = %(user_id)s,
                reserved_until = NOW() + INTERVAL '10 minutes',
                version = version + 1
            WHERE id = %(seat_id)s AND version = %(version)s
        """, seat_id=seat.id, version=seat.version, user_id=user_id)

        if rows_affected == 0:
            # Conflict - someone else booked
            rollback()
            return error("Seat unavailable due to concurrent booking")

    commit()
    return create_booking(event_id, seat_ids, user_id)
```

**Pros**: No external dependencies
**Cons**: High retry rate under contention (Taylor Swift scenario)

### Approach 2: Distributed Locks (RECOMMENDED)

```python
import redis
from redis.lock import Lock

redis_client = redis.Redis(host='localhost', port=6379)

def book_seats_with_lock(event_id, seat_ids, user_id):
    # Sort seat IDs to prevent deadlocks
    seat_ids = sorted(seat_ids)

    locks = []
    try:
        # Acquire locks for all seats
        for seat_id in seat_ids:
            lock_key = f"lock:seat:{seat_id}"
            lock = Lock(redis_client, lock_key, timeout=30)

            acquired = lock.acquire(blocking=True, blocking_timeout=10)
            if not acquired:
                raise Exception(f"Could not acquire lock for seat {seat_id}")

            locks.append(lock)

        # All locks acquired - now check and update
        seats = db.query("""
            SELECT id, status
            FROM seats
            WHERE id IN %(seat_ids)s AND event_id = %(event_id)s
            FOR UPDATE  -- pessimistic lock at DB level too
        """, seat_ids=seat_ids, event_id=event_id)

        # Verify availability
        if any(seat.status != 'AVAILABLE' for seat in seats):
            raise Exception("Some seats are no longer available")

        # Update seats
        db.execute("""
            UPDATE seats
            SET status = 'RESERVED',
                reserved_by = %(user_id)s,
                reserved_until = NOW() + INTERVAL '10 minutes'
            WHERE id IN %(seat_ids)s
        """, seat_ids=seat_ids, user_id=user_id)

        # Create booking
        booking = create_booking(event_id, seat_ids, user_id)

        db.commit()
        return booking

    except Exception as e:
        db.rollback()
        raise e
    finally:
        # Release all locks
        for lock in locks:
            lock.release()
```

**Pros**:
- Prevents double booking (CRITICAL)
- Works well under high contention
- Clear timeout mechanism

**Cons**:
- Requires Redis cluster
- Lock acquisition latency

### Approach 3: Database-Level Advisory Locks (PostgreSQL specific)

```python
def book_seats_advisory_lock(event_id, seat_ids, user_id):
    seat_ids = sorted(seat_ids)  # Prevent deadlocks

    try:
        db.begin()

        # Acquire advisory locks (released at transaction end)
        for seat_id in seat_ids:
            # Convert UUID to bigint for pg_advisory_xact_lock
            seat_hash = hash(str(seat_id)) % (2**63 - 1)
            db.execute("SELECT pg_advisory_xact_lock(%s)", seat_hash)

        # Check and update seats
        seats = db.query("""
            SELECT id, status
            FROM seats
            WHERE id IN %(seat_ids)s AND status = 'AVAILABLE'
        """, seat_ids=seat_ids)

        if len(seats) != len(seat_ids):
            raise Exception("Not all seats available")

        # Update seats
        db.execute("""
            UPDATE seats
            SET status = 'RESERVED',
                reserved_by = %(user_id)s,
                reserved_until = NOW() + INTERVAL '10 minutes'
            WHERE id IN %(seat_ids)s
        """, seat_ids=seat_ids, user_id=user_id)

        booking = create_booking(event_id, seat_ids, user_id)

        db.commit()  # Releases advisory locks
        return booking

    except Exception as e:
        db.rollback()
        raise e
```

**Pros**:
- No external dependencies
- Atomic with transaction
- Automatic lock release on commit/rollback

**Cons**:
- PostgreSQL-specific
- All locks must be in same database shard

---

## Step 6: Payment Integration (10 min)

### Stripe Payment Flow

```python
import stripe

stripe.api_key = "sk_live_..."

def process_payment(booking_id):
    # 1. Validate booking not expired
    booking = db.query("""
        SELECT * FROM bookings
        WHERE id = %(booking_id)s AND status = 'PENDING'
    """, booking_id=booking_id)

    if not booking:
        return error("Booking not found or expired")

    if booking.expires_at < now():
        release_seats(booking.seat_ids)
        update_booking_status(booking_id, 'EXPIRED')
        return error("Booking expired")

    # 2. Create Stripe payment intent
    try:
        payment_intent = stripe.PaymentIntent.create(
            amount=int(booking.total_amount * 100),  # cents
            currency="usd",
            payment_method=request.payment_method_id,
            confirm=True,
            metadata={
                "booking_id": booking_id,
                "event_id": booking.event_id,
                "user_id": booking.user_id
            }
        )

        # 3. On success, confirm booking
        if payment_intent.status == 'succeeded':
            db.begin()

            # Update booking
            db.execute("""
                UPDATE bookings
                SET status = 'CONFIRMED',
                    payment_id = %(payment_id)s,
                    confirmed_at = NOW()
                WHERE id = %(booking_id)s
            """, booking_id=booking_id, payment_id=payment_intent.id)

            # Update seats to SOLD
            db.execute("""
                UPDATE seats
                SET status = 'SOLD',
                    reserved_until = NULL
                WHERE id IN %(seat_ids)s
            """, seat_ids=booking.seat_ids)

            db.commit()

            # 4. Async tasks (Kafka events)
            publish_event('booking.confirmed', {
                'booking_id': booking_id,
                'user_id': booking.user_id,
                'event_id': booking.event_id
            })

            return success(booking_id)

        else:
            return error("Payment failed: " + payment_intent.status)

    except stripe.error.CardError as e:
        # Payment failed - release seats
        release_seats(booking.seat_ids)
        update_booking_status(booking_id, 'CANCELLED')

        return error("Payment declined: " + str(e))

def release_seats(seat_ids):
    """Release reserved seats back to AVAILABLE"""
    db.execute("""
        UPDATE seats
        SET status = 'AVAILABLE',
            reserved_by = NULL,
            reserved_until = NULL
        WHERE id IN %(seat_ids)s AND status = 'RESERVED'
    """, seat_ids=seat_ids)

    # Notify waitlist
    publish_event('seats.released', {'seat_ids': seat_ids})
```

### Idempotency

```python
def process_payment_idempotent(booking_id, idempotency_key):
    # Check if already processed
    existing = redis_client.get(f"payment:idempotency:{idempotency_key}")
    if existing:
        return json.loads(existing)

    # Process payment
    result = process_payment(booking_id)

    # Store result with 24hr expiry
    redis_client.setex(
        f"payment:idempotency:{idempotency_key}",
        86400,  # 24 hours
        json.dumps(result)
    )

    return result
```

---

## Step 7: Background Jobs & Async Processing

### 1. Expired Booking Cleanup (Cron Job)

```python
from apscheduler.schedulers.background import BackgroundScheduler

scheduler = BackgroundScheduler()

@scheduler.scheduled_job('interval', minutes=1)
def release_expired_bookings():
    """Run every minute to release expired reservations"""

    # Find expired bookings
    expired = db.query("""
        SELECT id, seat_ids
        FROM bookings
        WHERE status = 'PENDING'
        AND expires_at < NOW()
        FOR UPDATE SKIP LOCKED
        LIMIT 1000
    """)

    for booking in expired:
        # Update booking
        db.execute("""
            UPDATE bookings
            SET status = 'EXPIRED'
            WHERE id = %(booking_id)s
        """, booking_id=booking.id)

        # Release seats
        db.execute("""
            UPDATE seats
            SET status = 'AVAILABLE',
                reserved_by = NULL,
                reserved_until = NULL
            WHERE id IN %(seat_ids)s
        """, seat_ids=booking.seat_ids)

        # Notify waitlist
        publish_event('seats.released', {
            'event_id': booking.event_id,
            'seat_ids': booking.seat_ids
        })

    db.commit()
    print(f"Released {len(expired)} expired bookings")

scheduler.start()
```

### 2. Kafka Event Consumers

```python
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    'booking.confirmed',
    bootstrap_servers=['localhost:9092'],
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

def consume_booking_events():
    for message in consumer:
        event = message.value

        # Generate tickets
        generate_tickets(event['booking_id'])

        # Send email
        send_confirmation_email(event['user_id'], event['booking_id'])

        # Update analytics
        track_booking(event)
```

---

## Step 8: Virtual Waiting Room (High-Demand Events)

### Queue System Architecture

```python
import redis
import uuid

redis_client = redis.Redis(decode_responses=True)

def join_queue(event_id, user_id):
    """User joins virtual waiting room"""

    # Generate queue token
    token = str(uuid.uuid4())
    timestamp = time.time()

    # Add to sorted set (score = timestamp for FIFO)
    # But randomize timestamp to prevent bot advantage
    random_offset = random.uniform(0, 60)  # 0-60 second randomization
    score = timestamp + random_offset

    redis_client.zadd(
        f"queue:{event_id}",
        {f"{user_id}:{token}": score}
    )

    # Store token → user mapping
    redis_client.setex(
        f"queue_token:{token}",
        3600,  # 1 hour expiry
        user_id
    )

    # Get position
    position = redis_client.zrank(f"queue:{event_id}", f"{user_id}:{token}")

    return {
        "token": token,
        "position": position + 1,
        "estimatedWait": calculate_wait_time(position)
    }

def admit_users(event_id, count=100):
    """Admit next N users from queue (run every minute)"""

    # Pop lowest N scores (earliest in queue)
    admitted = redis_client.zpopmin(f"queue:{event_id}", count)

    session_tokens = []
    for user_token, score in admitted:
        user_id, queue_token = user_token.split(':')

        # Generate session token (15 minute validity)
        session_token = generate_session_token(user_id, event_id)
        redis_client.setex(
            f"session:{session_token}",
            900,  # 15 minutes
            json.dumps({"user_id": user_id, "event_id": event_id})
        )

        # Notify user (WebSocket)
        notify_user(user_id, {
            "status": "ADMITTED",
            "session_token": session_token,
            "expires_in": 900
        })

        session_tokens.append(session_token)

    return session_tokens

def get_queue_position(token):
    """Real-time position updates via WebSocket"""

    user_id = redis_client.get(f"queue_token:{token}")
    if not user_id:
        return None

    event_id = extract_event_id_from_token(token)
    position = redis_client.zrank(
        f"queue:{event_id}",
        f"{user_id}:{token}"
    )

    return {
        "position": position + 1 if position is not None else None,
        "estimatedWait": calculate_wait_time(position)
    }
```

---

## Step 9: Scalability & Optimizations

### 1. Database Sharding

```python
# Shard by event_id for write distribution

def get_shard(event_id):
    """Route to shard based on event_id hash"""
    shard_count = 16
    shard_id = hash(event_id) % shard_count
    return f"db_shard_{shard_id}"

# Booking service
def create_booking(event_id, seat_ids, user_id):
    shard = get_shard(event_id)
    db_conn = get_db_connection(shard)

    # Execute booking logic on correct shard
    ...
```

### 2. Caching Strategy

```python
import redis

cache = redis.Redis()

def get_event_with_cache(event_id):
    """Cache event details (rarely change)"""

    cache_key = f"event:{event_id}"
    cached = cache.get(cache_key)

    if cached:
        return json.loads(cached)

    # Cache miss - query DB
    event = db.query("SELECT * FROM events WHERE id = %s", event_id)

    # Cache for 1 hour
    cache.setex(cache_key, 3600, json.dumps(event))

    return event

def get_available_seats(event_id):
    """DON'T cache seat availability (changes frequently)"""

    # Always query latest from DB
    # Seat status changes on every booking
    return db.query("""
        SELECT * FROM seats
        WHERE event_id = %s AND status = 'AVAILABLE'
    """, event_id)

def cache_hot_events():
    """Pre-warm cache for trending events"""

    hot_events = db.query("""
        SELECT id FROM events
        WHERE sale_start_date BETWEEN NOW() AND NOW() + INTERVAL '1 day'
        ORDER BY expected_demand DESC
        LIMIT 100
    """)

    for event in hot_events:
        get_event_with_cache(event.id)
```

### 3. Read Replicas

```python
class DatabaseRouter:
    def __init__(self):
        self.primary = get_primary_connection()
        self.replicas = [
            get_replica_connection(1),
            get_replica_connection(2),
            get_replica_connection(3)
        ]
        self.replica_index = 0

    def get_write_db(self):
        """All writes go to primary"""
        return self.primary

    def get_read_db(self):
        """Round-robin across replicas"""
        replica = self.replicas[self.replica_index]
        self.replica_index = (self.replica_index + 1) % len(self.replicas)
        return replica

db_router = DatabaseRouter()

# Usage
def search_events(query):
    """Read-only query → use replica"""
    db = db_router.get_read_db()
    return db.query("SELECT * FROM events WHERE name LIKE %s", f"%{query}%")

def create_booking(event_id, seat_ids, user_id):
    """Write operation → use primary"""
    db = db_router.get_write_db()
    ...
```

---

## Step 10: Monitoring & Alerts

### Key Metrics

```python
from prometheus_client import Counter, Histogram, Gauge

# Booking metrics
bookings_total = Counter(
    'bookings_total',
    'Total number of booking attempts',
    ['status']  # success, failed, expired
)

booking_duration = Histogram(
    'booking_duration_seconds',
    'Time taken to complete booking'
)

# Seat availability
available_seats = Gauge(
    'available_seats',
    'Number of available seats',
    ['event_id']
)

# Lock contention
lock_acquisition_duration = Histogram(
    'lock_acquisition_duration_seconds',
    'Time to acquire distributed lock'
)

# Usage
@booking_duration.time()
def create_booking(event_id, seat_ids, user_id):
    bookings_total.labels(status='attempt').inc()

    try:
        # Booking logic
        ...
        bookings_total.labels(status='success').inc()
    except Exception as e:
        bookings_total.labels(status='failed').inc()
        raise e
```

### Alerts (Prometheus + AlertManager)

```yaml
groups:
  - name: ticketing_alerts
    rules:
      - alert: HighBookingFailureRate
        expr: |
          rate(bookings_total{status="failed"}[5m]) /
          rate(bookings_total[5m]) > 0.10
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "High booking failure rate (> 10%)"

      - alert: LockContentionHigh
        expr: lock_acquisition_duration_seconds > 10
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "Lock acquisition taking > 10 seconds"

      - alert: PaymentGatewayDown
        expr: up{job="stripe_api"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Stripe payment gateway is down"

      - alert: DatabaseReplicationLag
        expr: postgres_replication_lag_seconds > 30
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Database replication lag > 30 seconds"
```

---

## Interview Tips

**Questions to Ask**:
- Expected concurrent users during peak events?
- Acceptable double-booking rate? (Answer: ZERO!)
- Seat reservation timeout policy? (10 minutes typical)
- Payment failure handling strategy?
- Geographic distribution requirements?

**Topics to Cover**:
- Concurrency control (distributed locks vs optimistic locking)
- ACID guarantees for bookings
- Timeout cascades (user > lock > DB)
- Virtual queue for traffic shaping
- Event-driven architecture (Kafka)

**Common Follow-ups**:
- "How do you prevent double booking?"
  → Distributed locks (Redis Redlock) + Database transactions
- "What happens if payment fails?"
  → Release seats + notify waitlist + retry mechanism
- "How do you handle Taylor Swift level traffic?"
  → Virtual waiting room + throttled admission + auto-scaling
- "Database becomes bottleneck?"
  → Sharding by event_id + read replicas + caching

---

## Key Takeaways

1. **Consistency > Availability**: Can't sell same seat twice (choose CP over AP)
2. **Distributed Locks**: Critical for preventing double booking under concurrency
3. **Timeout Cascades**: Each layer (user, lock, DB) has progressively shorter timeouts
4. **Peak Planning**: Design for 10-100× average traffic (event releases spike massively)
5. **Event-Driven**: Async processing for tickets, notifications, analytics
6. **Idempotency**: Payment retries must not cause duplicate charges
7. **Monitoring**: Track booking success rate, lock contention, payment latency

## Further Reading

- Ticketmaster Engineering Blog
- "Designing Data-Intensive Applications" (Chapter on Transactions)
- Redis Redlock Algorithm
- Stripe Payment Intents Documentation
- Queue-It Virtual Waiting Room Architecture
