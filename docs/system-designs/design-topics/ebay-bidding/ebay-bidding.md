# Design an eBay-like Bidding System

**Companies**: eBay, Christie's, Sotheby's, DealDash
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a real-time online auction bidding system like eBay where users can create auctions, place bids on items, receive real-time notifications, and handle concurrent bidding scenarios with strong consistency guarantees.

---

## Step 1: Requirements (8 min)

### Functional Requirements

1. **Create Auction**: Sellers can list items with starting price, buy-now price, end time
2. **Place Bid**: Users can bid on active auctions with bid increment validation
3. **Real-time Updates**: Bidders receive instant notifications when outbid
4. **Auction End**: Automatic auction closure and winner determination
5. **Bid History**: View all bids placed on an item
6. **Search & Discovery**: Find auctions by category, price range, ending soon
7. **Auto-bidding** (optional): Set max price, system bids automatically
8. **Buy Now** (optional): Instant purchase at fixed price

**Prioritize**: Focus on core bidding flow, real-time updates, and consistency for MVP

### Non-Functional Requirements

1. **Strong Consistency**: No double-wins, accurate bid ordering
2. **High Availability**: 99.95% uptime (can have brief unavailability for consistency)
3. **Low Latency**: <100ms for bid placement, <500ms for notifications
4. **Scalability**: Handle 10M concurrent auctions, 100K bids/sec
5. **Fairness**: Last-second bids processed fairly (no sniping issues)
6. **Security**: Prevent bid manipulation, fraud detection

### Capacity Estimation

**Assumptions**:
- 100M total users, 10M DAU
- 10M active auctions at any time
- 1M new auctions created daily
- 100 bids per auction average
- 10M bids placed daily
- Auction duration: 7 days average
- 100K auctions ending per hour (peak: 4PM-8PM)

**Write Load**:
```
New Auctions: 1M/day ÷ 86.4K sec = ~12 auctions/sec
Bids: 10M/day ÷ 86.4K sec = ~120 bids/sec
Peak: 120 × 5 = ~600 bids/sec (evening hours)
```

**Read Load**:
```
Auction views: 10M DAU × 20 views/user = 200M views/day
View QPS: 200M ÷ 86.4K = ~2,300 QPS
Peak: ~5,000 QPS
Bid history reads: 10M × 3 = 30M reads/day = ~350 QPS
```

**Storage**:
```
Auctions: 10M active × 2KB = 20GB
Bids: 10M auctions × 100 bids × 500 bytes = 500GB
1 year: 365M auctions × 2KB = 730GB
1 year bids: 3.65B bids × 500 bytes = 1.8TB
Total (3 years): ~6TB
```

**Bandwidth**:
```
Write: 120 bids/sec × 500 bytes = 60KB/sec
Read: 2,300 QPS × 2KB = 4.6MB/sec
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
                    ┌────────────────┐
                    │    Clients     │
                    │ Web/Mobile/App │
                    └────────┬───────┘
                             │
                    ┌────────▼────────┐
                    │  Load Balancer  │
                    │  (AWS ALB)      │
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
    ┌─────▼─────┐     ┌─────▼─────┐     ┌─────▼─────┐
    │  Auction  │     │   Bid     │     │  Search   │
    │  Service  │     │  Service  │     │  Service  │
    └─────┬─────┘     └─────┬─────┘     └─────┬─────┘
          │                 │                  │
          └─────────┬───────┴──────────────────┘
                    │
          ┌─────────┴──────────┐
          │                    │
    ┌─────▼──────┐      ┌─────▼────────┐
    │  Primary   │      │   Message    │
    │    DB      │      │    Queue     │
    │ PostgreSQL │      │    Kafka     │
    └─────┬──────┘      └──────┬───────┘
          │                    │
    ┌─────▼──────┐      ┌─────▼────────┐
    │   Redis    │      │  Notification│
    │   Cache    │      │   Service    │
    └────────────┘      └──────────────┘
                               │
                        ┌──────▼───────┐
                        │  WebSocket   │
                        │   Server     │
                        └──────────────┘
```

### Components

**Load Balancer**:
- Distribute traffic across services
- SSL termination
- Health checks
- Sticky sessions for WebSocket connections

**Auction Service**:
- Create/update/delete auctions
- Auction lifecycle management
- End-time scheduling
- Buy-now processing

**Bid Service** (Critical):
- Validate bid amount (increment, user balance)
- **Optimistic locking** for concurrency control
- Bid placement with strong consistency
- Auto-bidding logic

**Search Service**:
- Elasticsearch for full-text search
- Category browsing
- Filter by price, time remaining
- Trending auctions

**Real-time Notification Service**:
- WebSocket connections for live updates
- Push notifications for outbid events
- Email notifications for auction end

**Cache (Redis)**:
- Hot auction data
- Current highest bid (with TTL)
- User watch lists
- Leaderboard for popular items

**Database**:
- PostgreSQL for ACID guarantees
- Sharded by auction_id
- Read replicas for queries

**Message Queue (Kafka)**:
- Bid events stream
- Auction end events
- Notification events
- Analytics pipeline

---

## Step 3: Data Model (10 min)

### Database Choice

**SQL (PostgreSQL)** - REQUIRED for this use case

**Why SQL?**:
- Strong consistency needed (no two winners)
- ACID transactions critical
- Complex joins (users, bids, auctions)
- Optimistic locking support
- Proven at eBay scale

### Schema

```sql
-- Auctions Table
CREATE TABLE auctions (
    id BIGSERIAL PRIMARY KEY,
    seller_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    starting_price DECIMAL(10,2) NOT NULL,
    current_price DECIMAL(10,2) NOT NULL,
    buy_now_price DECIMAL(10,2),
    reserve_price DECIMAL(10,2), -- minimum to sell
    bid_increment DECIMAL(10,2) DEFAULT 1.00,

    start_time TIMESTAMP NOT NULL,
    end_time TIMESTAMP NOT NULL,

    status VARCHAR(20) NOT NULL, -- active, ended, cancelled
    winner_id BIGINT,
    total_bids INTEGER DEFAULT 0,

    category_id INTEGER,
    shipping_cost DECIMAL(10,2),

    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    version INTEGER DEFAULT 0, -- for optimistic locking

    CONSTRAINT check_prices CHECK (
        current_price >= starting_price AND
        (buy_now_price IS NULL OR buy_now_price > starting_price)
    )
);

CREATE INDEX idx_auctions_status_end_time ON auctions(status, end_time);
CREATE INDEX idx_auctions_seller ON auctions(seller_id);
CREATE INDEX idx_auctions_category ON auctions(category_id);
CREATE INDEX idx_auctions_ending_soon ON auctions(end_time)
    WHERE status = 'active';

-- Bids Table
CREATE TABLE bids (
    id BIGSERIAL PRIMARY KEY,
    auction_id BIGINT NOT NULL REFERENCES auctions(id),
    bidder_id BIGINT NOT NULL,
    bid_amount DECIMAL(10,2) NOT NULL,
    max_bid_amount DECIMAL(10,2), -- for auto-bidding
    bid_type VARCHAR(20) DEFAULT 'manual', -- manual, auto

    placed_at TIMESTAMP DEFAULT NOW(),
    ip_address INET,

    is_winning BOOLEAN DEFAULT false,

    CONSTRAINT check_bid_positive CHECK (bid_amount > 0)
);

CREATE INDEX idx_bids_auction ON bids(auction_id, placed_at DESC);
CREATE INDEX idx_bids_user ON bids(bidder_id, placed_at DESC);
CREATE INDEX idx_bids_winning ON bids(auction_id)
    WHERE is_winning = true;

-- Users Table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    balance DECIMAL(10,2) DEFAULT 0.00, -- for bid validation
    reputation_score INTEGER DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW(),
    last_login TIMESTAMP
);

-- Watchlist Table
CREATE TABLE watchlist (
    user_id BIGINT NOT NULL,
    auction_id BIGINT NOT NULL,
    added_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (user_id, auction_id)
);

-- Notifications Table
CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    auction_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL, -- outbid, auction_won, auction_ended
    message TEXT,
    read BOOLEAN DEFAULT false,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read, created_at DESC);
```

---

## Step 4: APIs (5 min)

### Create Auction

```http
POST /api/v1/auctions
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "title": "Vintage Rolex Watch",
  "description": "1960s Submariner, excellent condition",
  "starting_price": 5000.00,
  "buy_now_price": 15000.00,
  "reserve_price": 8000.00,
  "bid_increment": 100.00,
  "duration_hours": 168, // 7 days
  "category_id": 42,
  "images": ["url1", "url2"],
  "shipping_cost": 25.00
}

Response: 201 Created
{
  "auction_id": "a9b8c7d6",
  "start_time": "2025-11-15T10:00:00Z",
  "end_time": "2025-11-22T10:00:00Z",
  "status": "active",
  "current_price": 5000.00
}
```

### Place Bid

```http
POST /api/v1/bids
Authorization: Bearer {token}
Content-Type: application/json

Request:
{
  "auction_id": "a9b8c7d6",
  "bid_amount": 5100.00,
  "max_bid_amount": 7000.00  // for auto-bidding (optional)
}

Response: 200 OK
{
  "bid_id": "b1c2d3e4",
  "auction_id": "a9b8c7d6",
  "bid_amount": 5100.00,
  "is_winning": true,
  "current_price": 5100.00,
  "next_minimum_bid": 5200.00,
  "placed_at": "2025-11-15T14:30:00Z"
}

Error Response: 409 Conflict
{
  "error": "BID_TOO_LOW",
  "message": "Minimum bid is $5200.00",
  "current_price": 5100.00,
  "minimum_bid": 5200.00
}
```

### Get Auction Details

```http
GET /api/v1/auctions/{auction_id}

Response: 200 OK
{
  "auction_id": "a9b8c7d6",
  "title": "Vintage Rolex Watch",
  "current_price": 5100.00,
  "total_bids": 23,
  "time_remaining": "6d 19h 30m",
  "end_time": "2025-11-22T10:00:00Z",
  "is_watching": false,
  "seller": {
    "id": "u123",
    "username": "watchcollector",
    "reputation": 4.8
  },
  "highest_bidder": {
    "username": "bidder***" // masked for privacy
  },
  "shipping_cost": 25.00
}
```

### Get Bid History

```http
GET /api/v1/auctions/{auction_id}/bids
Query params: ?limit=50&offset=0

Response: 200 OK
{
  "auction_id": "a9b8c7d6",
  "total_bids": 23,
  "bids": [
    {
      "bid_id": "b1c2d3e4",
      "bidder": "user***",  // masked
      "bid_amount": 5100.00,
      "placed_at": "2025-11-15T14:30:00Z",
      "is_winning": true
    },
    {
      "bid_id": "b0a9b8c7",
      "bidder": "user***",
      "bid_amount": 5000.00,
      "placed_at": "2025-11-15T11:20:00Z",
      "is_winning": false
    }
  ]
}
```

### WebSocket - Real-time Updates

```javascript
// Subscribe to auction updates
ws.send(JSON.stringify({
  "action": "subscribe",
  "auction_id": "a9b8c7d6"
}));

// Receive updates
{
  "type": "bid_placed",
  "auction_id": "a9b8c7d6",
  "current_price": 5200.00,
  "total_bids": 24,
  "next_minimum_bid": 5300.00,
  "timestamp": "2025-11-15T14:35:00Z"
}

{
  "type": "outbid",
  "auction_id": "a9b8c7d6",
  "message": "You've been outbid!",
  "current_price": 5200.00,
  "your_bid": 5100.00
}

{
  "type": "auction_ending_soon",
  "auction_id": "a9b8c7d6",
  "time_remaining": "5m",
  "current_price": 5500.00
}
```

---

## Step 5: Core Algorithm - Bid Placement (CRITICAL)

### Challenge: Concurrent Bids

Multiple users bidding on same auction simultaneously requires careful handling to prevent:
- Two users thinking they won
- Incorrect bid ordering
- Lost updates

### Approach 1: Pessimistic Locking (Simple but slow)

```python
from sqlalchemy import select
from sqlalchemy.orm import Session

def place_bid(session: Session, auction_id: int, bidder_id: int, bid_amount: Decimal):
    # Lock the auction row (FOR UPDATE)
    auction = session.execute(
        select(Auction).where(Auction.id == auction_id).with_for_update()
    ).scalar_one()

    # Validate bid
    if auction.status != 'active':
        raise AuctionNotActiveError()

    if bid_amount < auction.current_price + auction.bid_increment:
        raise BidTooLowError()

    if bidder_id == auction.seller_id:
        raise CannotBidOnOwnAuction()

    # Create bid
    bid = Bid(
        auction_id=auction_id,
        bidder_id=bidder_id,
        bid_amount=bid_amount,
        is_winning=True
    )
    session.add(bid)

    # Update auction
    prev_winner = auction.winner_id
    auction.current_price = bid_amount
    auction.winner_id = bidder_id
    auction.total_bids += 1

    # Mark previous bids as not winning
    if prev_winner:
        session.execute(
            update(Bid)
            .where(Bid.auction_id == auction_id, Bid.is_winning == True)
            .values(is_winning=False)
        )

    session.commit()

    # Send notification to previous winner (async)
    if prev_winner:
        notify_outbid(prev_winner, auction_id)

    return bid
```

**Pros**: Simple, guaranteed consistency
**Cons**: Lock contention, slow under high load

### Approach 2: Optimistic Locking (RECOMMENDED)

```python
from sqlalchemy.orm.exc import StaleDataError

def place_bid_optimistic(session: Session, auction_id: int, bidder_id: int, bid_amount: Decimal, max_retries=3):
    for attempt in range(max_retries):
        try:
            # Read auction WITHOUT lock
            auction = session.query(Auction).get(auction_id)
            current_version = auction.version

            # Validate (same as before)
            if auction.status != 'active':
                raise AuctionNotActiveError()

            if bid_amount < auction.current_price + auction.bid_increment:
                raise BidTooLowError(
                    f"Minimum bid: {auction.current_price + auction.bid_increment}"
                )

            # Create bid
            bid = Bid(
                auction_id=auction_id,
                bidder_id=bidder_id,
                bid_amount=bid_amount,
                is_winning=True
            )
            session.add(bid)

            # Update auction with version check
            prev_winner = auction.winner_id
            rows_updated = session.execute(
                update(Auction)
                .where(
                    Auction.id == auction_id,
                    Auction.version == current_version  # CRITICAL: version check
                )
                .values(
                    current_price=bid_amount,
                    winner_id=bidder_id,
                    total_bids=Auction.total_bids + 1,
                    version=Auction.version + 1  # Increment version
                )
            ).rowcount

            if rows_updated == 0:
                # Version mismatch - concurrent update
                session.rollback()
                raise StaleDataError("Concurrent bid detected, retrying...")

            # Mark previous bids as not winning
            if prev_winner:
                session.execute(
                    update(Bid)
                    .where(
                        Bid.auction_id == auction_id,
                        Bid.bidder_id == prev_winner,
                        Bid.is_winning == True
                    )
                    .values(is_winning=False)
                )

            session.commit()

            # Async notifications
            publish_bid_event(auction_id, bid_amount, bidder_id)
            if prev_winner:
                notify_outbid(prev_winner, auction_id)

            return bid

        except StaleDataError:
            if attempt == max_retries - 1:
                raise ConcurrentBidError("Too many concurrent bids, try again")
            # Exponential backoff
            time.sleep(0.01 * (2 ** attempt))
            continue
```

**Pros**:
- Better concurrency (no locks held)
- Automatic retry on conflict
- Higher throughput

**Cons**:
- Retries needed on contention
- Slightly more complex

### Approach 3: Redis-based Distributed Lock (High scale)

```python
import redis
from contextlib import contextmanager

redis_client = redis.Redis(host='localhost', port=6379, decode_responses=True)

@contextmanager
def auction_lock(auction_id: int, timeout=2):
    """Distributed lock using Redis"""
    lock_key = f"auction_lock:{auction_id}"
    lock_id = str(uuid.uuid4())

    # Try to acquire lock
    acquired = redis_client.set(
        lock_key,
        lock_id,
        nx=True,  # Only set if not exists
        ex=timeout  # Expire after timeout
    )

    if not acquired:
        raise BidInProgressError("Another bid is being processed")

    try:
        yield
    finally:
        # Release lock (with Lua script for atomicity)
        release_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """
        redis_client.eval(release_script, 1, lock_key, lock_id)

def place_bid_with_redis_lock(auction_id: int, bidder_id: int, bid_amount: Decimal):
    with auction_lock(auction_id):
        # Now safe to proceed with bid placement
        return place_bid_optimistic(session, auction_id, bidder_id, bid_amount)
```

**Pros**:
- Fast distributed locking
- Prevents thundering herd
- Scales horizontally

**Cons**:
- Additional Redis dependency
- Network latency

---

## Step 6: Auto-bidding Algorithm

Auto-bidding allows users to set a maximum bid, and the system automatically bids the minimum amount needed to stay winning.

```python
def process_bid_with_auto_bidding(auction_id: int, new_bid: Bid):
    """
    When a manual bid comes in, check if any auto-bidders
    should counter-bid
    """
    auction = get_auction(auction_id)

    # Get highest auto-bid amount
    highest_auto_bid = db.query(Bid).filter(
        Bid.auction_id == auction_id,
        Bid.max_bid_amount.isnot(None),
        Bid.max_bid_amount > new_bid.bid_amount,
        Bid.bidder_id != new_bid.bidder_id
    ).order_by(Bid.max_bid_amount.desc()).first()

    if highest_auto_bid:
        # Auto-bidder can still win
        auto_bid_amount = min(
            new_bid.bid_amount + auction.bid_increment,
            highest_auto_bid.max_bid_amount
        )

        # Place automatic counter-bid
        counter_bid = Bid(
            auction_id=auction_id,
            bidder_id=highest_auto_bid.bidder_id,
            bid_amount=auto_bid_amount,
            max_bid_amount=highest_auto_bid.max_bid_amount,
            bid_type='auto',
            is_winning=True
        )

        db.add(counter_bid)

        # Update auction
        auction.current_price = auto_bid_amount
        auction.winner_id = highest_auto_bid.bidder_id

        # Notify manual bidder they're outbid
        notify_outbid(new_bid.bidder_id, auction_id)

        db.commit()
        return counter_bid

    return new_bid
```

---

## Step 7: Auction End Processing

Auctions need to end precisely on time and determine winners.

### Approach: Scheduled Job + Message Queue

```python
from apscheduler.schedulers.background import BackgroundScheduler
import time

# Scheduler checks every minute for ending auctions
scheduler = BackgroundScheduler()

@scheduler.scheduled_job('interval', minutes=1)
def check_ending_auctions():
    """
    Find auctions ending in next minute and schedule precise end
    """
    now = datetime.utcnow()
    ending_soon = db.query(Auction).filter(
        Auction.status == 'active',
        Auction.end_time > now,
        Auction.end_time <= now + timedelta(minutes=1)
    ).all()

    for auction in ending_soon:
        # Calculate precise delay
        delay_seconds = (auction.end_time - now).total_seconds()

        # Schedule precise end job
        scheduler.add_job(
            end_auction,
            'date',
            run_date=auction.end_time,
            args=[auction.id]
        )

def end_auction(auction_id: int):
    """
    End auction and determine winner
    """
    with transaction():
        auction = db.query(Auction).with_for_update().get(auction_id)

        if auction.status != 'active':
            return  # Already ended

        # Update status
        auction.status = 'ended'

        # Determine winner
        if auction.current_price >= auction.reserve_price:
            # Auction successful
            winning_bid = db.query(Bid).filter(
                Bid.auction_id == auction_id,
                Bid.is_winning == True
            ).first()

            if winning_bid:
                auction.winner_id = winning_bid.bidder_id

                # Publish events
                publish_event('auction.ended', {
                    'auction_id': auction_id,
                    'winner_id': winning_bid.bidder_id,
                    'final_price': auction.current_price
                })

                # Notify winner
                notify_auction_won(winning_bid.bidder_id, auction_id)

                # Notify all watchers
                notify_auction_ended(auction_id)

                # Initiate payment process
                initiate_payment(auction_id, winning_bid.bidder_id)
        else:
            # Reserve not met
            auction.status = 'ended_no_winner'
            notify_seller_reserve_not_met(auction.seller_id, auction_id)

        db.commit()

scheduler.start()
```

---

## Step 8: Real-time Notifications

### WebSocket Server

```python
from fastapi import FastAPI, WebSocket
from typing import Dict, Set
import asyncio

app = FastAPI()

# Connection manager
class ConnectionManager:
    def __init__(self):
        # auction_id -> set of websocket connections
        self.active_connections: Dict[int, Set[WebSocket]] = {}

    async def connect(self, websocket: WebSocket, auction_id: int):
        await websocket.accept()
        if auction_id not in self.active_connections:
            self.active_connections[auction_id] = set()
        self.active_connections[auction_id].add(websocket)

    def disconnect(self, websocket: WebSocket, auction_id: int):
        if auction_id in self.active_connections:
            self.active_connections[auction_id].discard(websocket)

    async def broadcast(self, auction_id: int, message: dict):
        """Send message to all watchers of an auction"""
        if auction_id not in self.active_connections:
            return

        dead_connections = set()
        for connection in self.active_connections[auction_id]:
            try:
                await connection.send_json(message)
            except:
                dead_connections.add(connection)

        # Clean up dead connections
        self.active_connections[auction_id] -= dead_connections

manager = ConnectionManager()

@app.websocket("/ws/auction/{auction_id}")
async def websocket_endpoint(websocket: WebSocket, auction_id: int):
    await manager.connect(websocket, auction_id)
    try:
        while True:
            # Keep connection alive
            data = await websocket.receive_text()
            # Handle ping/pong
    except WebSocketDisconnect:
        manager.disconnect(websocket, auction_id)

# Kafka consumer for bid events
async def consume_bid_events():
    """
    Consume bid events from Kafka and broadcast to WebSocket clients
    """
    consumer = KafkaConsumer(
        'bid-events',
        bootstrap_servers=['localhost:9092'],
        value_deserializer=lambda m: json.loads(m.decode('utf-8'))
    )

    for message in consumer:
        event = message.value
        auction_id = event['auction_id']

        # Broadcast to all watchers
        await manager.broadcast(auction_id, {
            'type': 'bid_placed',
            'auction_id': auction_id,
            'current_price': event['current_price'],
            'total_bids': event['total_bids'],
            'timestamp': event['timestamp']
        })

# Run consumer in background
asyncio.create_task(consume_bid_events())
```

---

## Step 9: Optimizations & Trade-offs (10 min)

### 1. Caching Strategy

**Cache Hot Auctions**:
```python
# Redis cache structure
auction:{auction_id} → hash of auction data
auction:{auction_id}:current_price → current price (sorted set)
auction:{auction_id}:watchers → set of user_ids
trending_auctions → sorted set by bid count
ending_soon → sorted set by end_time
```

**Implementation**:
```python
def get_auction_with_cache(auction_id: int):
    cache_key = f"auction:{auction_id}"

    # Try cache first
    cached = redis.get(cache_key)
    if cached:
        return json.loads(cached)

    # Cache miss - query DB
    auction = db.query(Auction).get(auction_id)

    # Cache for 30 seconds (short TTL due to frequent updates)
    redis.setex(cache_key, 30, json.dumps(auction.to_dict()))

    return auction

def update_price_in_cache(auction_id: int, new_price: Decimal):
    """Update cache on bid placement"""
    cache_key = f"auction:{auction_id}"

    # Invalidate cache (simple approach)
    redis.delete(cache_key)

    # OR update specific field (better)
    redis.hset(cache_key, 'current_price', float(new_price))
    redis.expire(cache_key, 30)
```

### 2. Database Sharding

**Shard by Auction ID**:
```
shard_id = auction_id % num_shards

Shard 0: auctions 0, 4, 8, 12...
Shard 1: auctions 1, 5, 9, 13...
Shard 2: auctions 2, 6, 10, 14...
Shard 3: auctions 3, 7, 11, 15...
```

**Benefits**:
- Bids for same auction always on same shard
- No distributed transactions needed
- Easy to scale horizontally

**Trade-off**:
- Cross-shard queries harder (user bid history)
- Need routing layer

### 3. Read Replicas

```
[Primary] ← writes (bids, auction updates)
   ↓
[Replica 1] ← auction detail reads
[Replica 2] ← search queries
[Replica 3] ← bid history reads
```

### 4. Search Optimization

**Elasticsearch Index**:
```json
{
  "auction_id": "a9b8c7d6",
  "title": "Vintage Rolex Watch",
  "category": "Watches",
  "current_price": 5100.00,
  "end_time": "2025-11-22T10:00:00Z",
  "time_remaining_sec": 604800,
  "total_bids": 23,
  "seller_reputation": 4.8,
  "has_image": true,
  "shipping_cost": 25.00
}
```

**Queries**:
```
GET /auctions/_search
{
  "query": {
    "bool": {
      "must": [
        {"match": {"category": "Watches"}},
        {"range": {"current_price": {"lte": 10000}}}
      ],
      "filter": [
        {"term": {"status": "active"}}
      ]
    }
  },
  "sort": [
    {"end_time": "asc"}  // Ending soonest first
  ]
}
```

### 5. Preventing Bid Sniping

**Problem**: Users bid in last second to prevent counter-bids

**Solution 1: Automatic Extension**
```python
def extend_auction_if_needed(auction: Auction, bid_time: datetime):
    """
    If bid placed in last 5 minutes, extend auction by 5 minutes
    """
    time_remaining = auction.end_time - bid_time

    if time_remaining < timedelta(minutes=5):
        auction.end_time = bid_time + timedelta(minutes=5)
        db.commit()

        # Notify watchers
        broadcast_auction_extended(auction.id, auction.end_time)
```

**Solution 2: Hard Close with Queue**
```python
def process_last_second_bids(auction_id: int):
    """
    Process all bids received in last second in order
    """
    auction = get_auction(auction_id)

    # Get all bids placed after end_time (with grace period)
    late_bids = db.query(Bid).filter(
        Bid.auction_id == auction_id,
        Bid.placed_at >= auction.end_time,
        Bid.placed_at < auction.end_time + timedelta(seconds=1)
    ).order_by(Bid.placed_at.asc()).all()

    # Process in order of placement
    for bid in late_bids:
        try:
            validate_and_accept_bid(bid)
        except:
            reject_bid(bid)
```

### 6. Fraud Prevention

**Shill Bidding Detection**:
```python
def detect_shill_bidding(auction_id: int):
    """
    Detect if seller is using fake accounts to inflate price
    """
    auction = get_auction(auction_id)

    # Get all bidders
    bidders = db.query(Bid.bidder_id).filter(
        Bid.auction_id == auction_id
    ).distinct().all()

    suspicious_patterns = []

    for bidder_id in bidders:
        # Check if bidder and seller have same IP
        if same_ip(bidder_id, auction.seller_id):
            suspicious_patterns.append('same_ip')

        # Check if bidder only bids on this seller's items
        bid_diversity = get_seller_diversity(bidder_id)
        if bid_diversity < 0.1:  # 90% of bids on same seller
            suspicious_patterns.append('low_diversity')

        # Check for bid retraction pattern
        if has_frequent_retractions(bidder_id):
            suspicious_patterns.append('frequent_retractions')

    if len(suspicious_patterns) >= 2:
        flag_auction_for_review(auction_id)
```

---

## Step 10: Advanced Considerations

### Security

**1. Bid Validation**:
```python
def validate_bid(auction: Auction, bidder_id: int, bid_amount: Decimal):
    # Check auction is active
    if auction.status != 'active':
        raise InvalidBidError("Auction is not active")

    # Check auction hasn't ended
    if datetime.utcnow() > auction.end_time:
        raise InvalidBidError("Auction has ended")

    # Check bidder isn't seller
    if bidder_id == auction.seller_id:
        raise InvalidBidError("Cannot bid on own auction")

    # Check bid meets minimum
    min_bid = auction.current_price + auction.bid_increment
    if bid_amount < min_bid:
        raise InvalidBidError(f"Minimum bid is {min_bid}")

    # Check user has sufficient balance/credit
    user = get_user(bidder_id)
    if user.balance < bid_amount:
        raise InvalidBidError("Insufficient funds")

    # Rate limiting - max 10 bids per minute per user
    recent_bids = count_recent_bids(bidder_id, minutes=1)
    if recent_bids > 10:
        raise RateLimitError("Too many bids, please slow down")
```

**2. CSRF Protection**:
- Use CSRF tokens for bid placement
- Validate Origin/Referer headers
- Short-lived JWT tokens

**3. Bot Prevention**:
- CAPTCHA for suspicious activity
- Rate limiting per IP
- Device fingerprinting

### Monitoring

**Key Metrics**:
```
Business Metrics:
- Active auctions count
- Bids per second
- Average bid amount
- Auction completion rate
- Reserve price met rate

Technical Metrics:
- Bid placement latency (P50, P95, P99)
- WebSocket connection count
- Database lock wait time
- Cache hit ratio
- Failed bid rate

Alerts:
- Bid latency > 500ms
- Failed bid rate > 5%
- WebSocket disconnections > 10%
- Database CPU > 80%
```

### High Availability

**Multi-Region Deployment**:
```
Primary Region (US-East):
  - Read-Write Database
  - WebSocket servers
  - Bid processing

Secondary Region (EU-West):
  - Read Replicas
  - Auction browsing
  - Search

Failover:
  - DNS failover (5 min RTO)
  - Database promotion (30 sec RTO)
  - WebSocket reconnection
```

**Disaster Recovery**:
- Database backups every 6 hours
- Point-in-time recovery (5 min RPO)
- Cross-region replication
- Auction state snapshots

---

## Complete System Architecture Diagram

```
                    ┌─────────────┐
                    │   Clients   │
                    │ Web/Mobile  │
                    └──────┬──────┘
                           │
                  ┌────────▼────────┐
                  │   CloudFront    │ CDN for static assets
                  │   + Route 53    │ DNS + SSL
                  └────────┬────────┘
                           │
                  ┌────────▼────────┐
                  │      ALB        │ Application Load Balancer
                  │  + WAF Shield   │ DDoS protection
                  └────────┬────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
   ┌────▼─────┐      ┌────▼─────┐      ┌────▼─────┐
   │ Auction  │      │   Bid    │      │  Search  │
   │ Service  │      │ Service  │      │ Service  │
   │(Spring)  │      │ (Go)     │      │(FastAPI) │
   └────┬─────┘      └────┬─────┘      └────┬─────┘
        │                 │                  │
        └────────┬────────┴──────────────────┘
                 │
      ┌──────────┼───────────────┐
      │          │               │
 ┌────▼──┐  ┌───▼────┐    ┌─────▼──────┐
 │Postgres│ │ Redis  │    │   Kafka    │
 │ Master │ │Cluster │    │  Cluster   │
 └────┬───┘ └────────┘    └─────┬──────┘
      │                         │
 ┌────▼────┐              ┌─────▼────────┐
 │Replicas │              │ Notification │
 │ (3x)    │              │   Service    │
 └─────────┘              └──────┬───────┘
                                 │
                          ┌──────▼────────┐
                          │  WebSocket    │
                          │   Servers     │
                          └───────────────┘

Supporting Services:
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│Elasticsearch │  │  ClickHouse  │  │  Prometheus  │
│   Search     │  │  Analytics   │  │  Monitoring  │
└──────────────┘  └──────────────┘  └──────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (concurrent auctions, bids/sec)?
- Consistency vs availability trade-off?
- Auction duration (affects end-time strategy)?
- Auto-bidding required?
- Real-time requirements (<1 sec acceptable)?
- Mobile app support (push notifications)?

**Topics to Cover**:
- Concurrency control (optimistic locking critical!)
- Strong consistency for bid placement
- Real-time updates (WebSocket vs polling)
- Auction end processing (scheduling)
- Database sharding strategy
- Search and discovery

**Common Follow-ups**:
- "How do you handle 1000 concurrent bids on same auction?"
  → Optimistic locking with retry, Redis distributed lock

- "What if auction end time fails to execute?"
  → Scheduled jobs with redundancy, Kafka event sourcing

- "How do you prevent bid sniping?"
  → Automatic extension or hard close with grace period

- "How do you detect fraud?"
  → IP tracking, bid pattern analysis, user reputation

---

## Key Takeaways

1. **Strong Consistency Required**: Unlike many systems, auctions MUST guarantee consistency (no double winners)
2. **Optimistic Locking**: Best balance of consistency and performance
3. **Real-time Critical**: WebSocket for instant updates, latency matters
4. **Scheduled Jobs**: Precise auction end timing needs robust scheduling
5. **Fraud Prevention**: Shill bidding, bid manipulation require detection
6. **Auto-bidding Complexity**: Proxy bidding adds algorithmic challenges
7. **Monitor Everything**: Bid latency, failed bids, lock contention

## Further Reading

- eBay Architecture Blog
- "Designing Online Auction Systems" - ACM Paper
- Optimistic Locking in PostgreSQL
- WebSocket at Scale
- Kafka Event Sourcing Patterns
