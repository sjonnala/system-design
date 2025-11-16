# Design a Notification Service

**Companies**: Facebook, Uber, DoorDash, Airbnb, Stripe
**Difficulty**: Intermediate
**Time**: 45-60 minutes

## Problem Statement

Design a scalable, multi-channel notification service that can send millions of notifications per day across email, SMS, push notifications, and other channels. The system should be reliable, cost-effective, and support real-time delivery.

---

## Step 1: Requirements (5-7 min)

### Functional Requirements

1. **Multi-Channel Support**: Send notifications via email, SMS, push (iOS/Android), in-app
2. **Template Management**: Support dynamic templates with variable substitution
3. **User Preferences**: Honor user channel preferences and opt-outs
4. **Priority Handling**: Critical notifications (OTP, security) get priority over marketing
5. **Delivery Tracking**: Track delivery status, opens, clicks
6. **Retry Logic**: Automatic retries with exponential backoff on failures
7. **Rate Limiting**: Prevent notification fatigue, respect quiet hours

**Prioritize for MVP**: Multi-channel sending, basic templates, delivery tracking

### Non-Functional Requirements

1. **High Availability**: 99.99% uptime (financial/critical alerts)
2. **Low Latency**: <5 seconds P95 delivery time for critical notifications
3. **Scalable**: Handle 100M+ notifications per day, 10K+ QPS peaks
4. **Cost-Efficient**: Optimize for channel costs (SMS most expensive)
5. **Fault-Tolerant**: Graceful degradation, automatic provider fallback
6. **Idempotent**: No duplicate notifications to users

### Capacity Estimation

**Assumptions**:
- 100M total users
- 20M Daily Active Users (DAU)
- 10 notifications per DAU per day
- Channel mix: 60% push, 30% email, 10% SMS

**Volume**:
```
Total notifications/day = 20M × 10 = 200M notifications
Peak multiplier: 4x (concentrated in morning/evening)

Avg QPS = 200M ÷ 86,400 sec ≈ 2,300 QPS
Peak QPS = 2,300 × 4 ≈ 10,000 QPS
```

**Channel Breakdown**:
```
Push:  200M × 60% = 120M push notifications/day
Email: 200M × 30% = 60M emails/day
SMS:   200M × 10% = 20M SMS/day
```

**Cost Estimation** (per notification):
```
Push:  $0 (free via FCM/APNS)
Email: $0.001 per email (SendGrid/AWS SES)
SMS:   $0.0075 per SMS (Twilio, varies by region)

Daily cost:
  Push:  120M × $0 = $0
  Email: 60M × $0.001 = $60
  SMS:   20M × $0.0075 = $150
  Total: $210/day ≈ $6,300/month

Yearly channel cost: ~$75,000
```

**Storage**:
```
Notification log per entry: 700 bytes
  - UUID (16) + User ID (8) + Type (50) + Payload (500) 
  - Timestamps (16) + Status (100) + Metadata (10)

Daily storage = 200M × 700 bytes = 140 GB/day
90-day retention = 140 GB × 90 = 12.6 TB
```

**Bandwidth**:
```
Write: 2,300 QPS × 700 bytes ≈ 1.6 MB/s
Read: Minimal (delivery receipt callbacks)
```

---

## Step 2: High-Level Architecture (15 min)

### System Architecture

```
┌──────────────────────────────────────────────────────────────┐
│                     CLIENT APPLICATIONS                      │
│  (Order Service, Payment Service, User Service, etc.)       │
└────────────────────────┬─────────────────────────────────────┘
                         │ REST/gRPC
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     API GATEWAY                              │
│  • Authentication (API Keys, JWT)                            │
│  • Rate Limiting (1000 req/min per service)                 │
│  • Request Validation                                        │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              NOTIFICATION API SERVICE                        │
│  • Validate request                                          │
│  • Check user preferences (opt-out, quiet hours)            │
│  • Deduplication (prevent spam)                             │
│  • Enrich with template data                                │
└────────────────────────┬─────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│              NOTIFICATION ORCHESTRATOR                       │
│  • Channel selection based on priority & preference         │
│  • Batching for non-critical notifications                  │
│  • Rate limiting (prevent notification fatigue)             │
└────────────────────────┬─────────────────────────────────────┘
                         │
            ┌────────────┼────────────┐
            │            │            │
            ▼            ▼            ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │  Email   │  │   SMS    │  │   Push   │
    │  Queue   │  │  Queue   │  │  Queue   │
    │ (Kafka)  │  │ (Kafka)  │  │ (Kafka)  │
    └─────┬────┘  └─────┬────┘  └─────┬────┘
          │             │             │
          ▼             ▼             ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │  Email   │  │   SMS    │  │   Push   │
    │ Workers  │  │ Workers  │  │ Workers  │
    │(10 inst) │  │(5 inst)  │  │(20 inst) │
    └─────┬────┘  └─────┬────┘  └─────┬────┘
          │             │             │
          ▼             ▼             ▼
    ┌──────────┐  ┌──────────┐  ┌──────────┐
    │SendGrid/ │  │ Twilio/  │  │FCM/APNS  │
    │AWS SES   │  │  Vonage  │  │          │
    │(+fallback)│  │(+fallback)│  │          │
    └──────────┘  └──────────┘  └──────────┘


Supporting Services:
┌────────────┐  ┌─────────────┐  ┌──────────────┐
│ Template   │  │   User      │  │  Analytics   │
│  Engine    │  │ Preferences │  │   Service    │
│(Redis+PG)  │  │  (Redis)    │  │ (ClickHouse) │
└────────────┘  └─────────────┘  └──────────────┘
```

### Key Components

**1. API Gateway**:
- Entry point for all notification requests
- Authentication, rate limiting, request validation
- Circuit breaker for downstream services

**2. Notification API Service** (Stateless):
- Validates notification requests
- Checks user preferences from cache
- Applies deduplication logic
- Publishes to message queues

**3. Message Queues (Kafka/RabbitMQ)**:
- Decouples producers from consumers
- Separate queues per channel (email, SMS, push)
- Enables horizontal scaling of workers
- Provides durability and replay capability

**4. Channel Workers**:
- Dedicated workers per channel type
- Poll from queues, call provider APIs
- Handle retries with exponential backoff
- Track delivery status

**5. Third-Party Providers**:
- Email: SendGrid (primary), AWS SES (fallback)
- SMS: Twilio (primary), Vonage (fallback)
- Push: FCM (Android), APNS (iOS)

**6. Supporting Services**:
- Template Engine: Store and render notification templates
- User Preferences: Manage opt-outs, quiet hours, channel preferences
- Analytics: Track delivery metrics, engagement

---

## Step 3: Data Model (10 min)

### Database Choice

**SQL (PostgreSQL) for**:
- Notification logs (audit trail)
- User preferences (ACID compliance)
- Templates (versioning support)

**NoSQL (Cassandra/DynamoDB) Alternative**:
- Better for write-heavy workloads
- Easier horizontal scaling
- Good for time-series notification logs

**Going with PostgreSQL** (easier to reason about, can migrate later)

### Schema Design

```sql
-- Notification Log Table
CREATE TABLE notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,  -- order_placed, payment_received
    channel VARCHAR(50) NOT NULL,       -- EMAIL, SMS, PUSH
    priority VARCHAR(20) NOT NULL,      -- CRITICAL, HIGH, MEDIUM, LOW
    status VARCHAR(50) NOT NULL,        -- QUEUED, SENT, DELIVERED, FAILED
    
    -- Content
    subject TEXT,
    body TEXT,
    metadata JSONB,                     -- Event-specific data
    
    -- Timing
    created_at TIMESTAMP DEFAULT NOW(),
    queued_at TIMESTAMP,
    sent_at TIMESTAMP,
    delivered_at TIMESTAMP,
    
    -- Tracking
    provider VARCHAR(100),               -- sendgrid, twilio, fcm
    provider_message_id VARCHAR(255),
    error_message TEXT,
    retry_count INT DEFAULT 0,
    
    -- Indexes
    CONSTRAINT fk_user FOREIGN KEY (user_id) REFERENCES users(id)
);

-- Indexes for common queries
CREATE INDEX idx_user_id_created ON notifications(user_id, created_at DESC);
CREATE INDEX idx_status_created ON notifications(status, created_at);
CREATE INDEX idx_channel_created ON notifications(channel, created_at);

-- Partitioning by month (for performance)
CREATE TABLE notifications_2025_01 PARTITION OF notifications
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
-- ... more partitions


-- User Preferences Table
CREATE TABLE user_preferences (
    user_id BIGINT PRIMARY KEY,
    
    -- Channel preferences
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT TRUE,
    push_enabled BOOLEAN DEFAULT TRUE,
    in_app_enabled BOOLEAN DEFAULT TRUE,
    
    -- Quiet hours (JSON for flexibility)
    quiet_hours JSONB,  -- { "enabled": true, "start": "22:00", "end": "08:00", "timezone": "America/New_York" }
    
    -- Frequency limits
    frequency_limits JSONB,  -- { "marketing": "weekly", "transactional": "unlimited" }
    
    -- Opt-outs
    opt_out_categories TEXT[],  -- ['marketing', 'social']
    
    updated_at TIMESTAMP DEFAULT NOW()
);


-- Notification Templates
CREATE TABLE notification_templates (
    id SERIAL PRIMARY KEY,
    name VARCHAR(200) UNIQUE NOT NULL,
    channel VARCHAR(50) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    
    -- Template content
    subject_template TEXT,
    body_template TEXT,
    
    -- Metadata
    version INT DEFAULT 1,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Template variables
CREATE TABLE template_variables (
    id SERIAL PRIMARY KEY,
    template_id INT REFERENCES notification_templates(id),
    variable_name VARCHAR(100) NOT NULL,
    variable_type VARCHAR(50),  -- string, number, date
    default_value TEXT,
    is_required BOOLEAN DEFAULT FALSE
);


-- Dead Letter Queue (Failed notifications)
CREATE TABLE notification_dlq (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_notification_id UUID REFERENCES notifications(id),
    failure_reason TEXT,
    retry_count INT,
    payload JSONB,
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Redis Cache Schema

```
# User preferences (hot data)
Key: user:prefs:{user_id}
Value: JSON { emailEnabled: true, smsEnabled: false, ... }
TTL: 3600 seconds (1 hour)

# Rate limiting (sliding window)
Key: ratelimit:{user_id}:{event_type}
Value: Counter
TTL: 3600 seconds

# Deduplication
Key: dedup:{user_id}:{event_type}:{hash}
Value: notification_id
TTL: 300 seconds (5 minutes)

# Templates (frequently used)
Key: template:{template_name}:{version}
Value: Compiled template
TTL: 86400 seconds (24 hours)
```

---

## Step 4: API Design (5 min)

### Send Notification API

```http
POST /api/v1/notifications/send
Content-Type: application/json
Authorization: Bearer <api_key>

Request:
{
  "user_id": 123456,
  "event_type": "order_shipped",
  "priority": "high",
  "channels": ["email", "push"],  // Optional: override defaults
  "template_data": {
    "order_id": "ORD-2024-5678",
    "tracking_url": "https://track.example.com/5678",
    "estimated_delivery": "2025-11-18"
  },
  "metadata": {
    "campaign_id": "spring_sale_2024",
    "ab_test_variant": "A"
  }
}

Response: 201 Created
{
  "notification_id": "550e8400-e29b-41d4-a716-446655440000",
  "status": "queued",
  "channels_selected": ["email", "push"],
  "estimated_delivery_time": "2025-11-16T10:05:00Z"
}

Error Response: 429 Too Many Requests
{
  "error": "rate_limit_exceeded",
  "message": "Maximum 1000 notifications per minute exceeded",
  "retry_after": 60
}
```

### Batch Send API (for efficiency)

```http
POST /api/v1/notifications/batch
Content-Type: application/json
Authorization: Bearer <api_key>

Request:
{
  "notifications": [
    {
      "user_id": 123,
      "event_type": "daily_digest",
      "template_data": { ... }
    },
    {
      "user_id": 456,
      "event_type": "daily_digest",
      "template_data": { ... }
    }
    // ... up to 1000 notifications
  ]
}

Response: 202 Accepted
{
  "batch_id": "batch_abc123",
  "total_count": 1000,
  "accepted_count": 995,
  "rejected_count": 5,
  "rejected_notifications": [
    {
      "user_id": 789,
      "reason": "user_opted_out"
    }
  ]
}
```

### Get Notification Status

```http
GET /api/v1/notifications/{notification_id}

Response: 200 OK
{
  "notification_id": "550e8400-e29b-41d4-a716-446655440000",
  "user_id": 123456,
  "event_type": "order_shipped",
  "channel": "email",
  "status": "delivered",
  "created_at": "2025-11-16T10:00:00Z",
  "sent_at": "2025-11-16T10:00:02Z",
  "delivered_at": "2025-11-16T10:00:05Z",
  "provider": "sendgrid",
  "engagement": {
    "opened_at": "2025-11-16T10:15:00Z",
    "clicked_at": null
  }
}
```

### User Preferences API

```http
GET /api/v1/users/{user_id}/preferences

Response: 200 OK
{
  "user_id": 123456,
  "channels": {
    "email": true,
    "sms": false,
    "push": true,
    "in_app": true
  },
  "quiet_hours": {
    "enabled": true,
    "start": "22:00",
    "end": "08:00",
    "timezone": "America/New_York"
  },
  "opt_out_categories": ["marketing"]
}

PUT /api/v1/users/{user_id}/preferences
{
  "channels": {
    "sms": false
  }
}

Response: 200 OK
```

---

## Step 5: Core Algorithms & Logic

### 1. Channel Selection Algorithm

```python
def select_channels(user_id, event_type, priority, requested_channels=None):
    """
    Intelligently select notification channels based on:
    - User preferences
    - Event type
    - Priority
    - Cost optimization
    """
    # Fetch user preferences (from Redis cache)
    prefs = redis.get(f"user:prefs:{user_id}")
    if not prefs:
        prefs = db.query("SELECT * FROM user_preferences WHERE user_id = ?", user_id)
        redis.setex(f"user:prefs:{user_id}", 3600, json.dumps(prefs))
    
    selected = []
    
    # Critical notifications override opt-outs
    if priority == "CRITICAL":
        # Security alerts, OTP - always send via most reliable
        if prefs.sms_enabled or priority_overrides_optout():
            selected.append("SMS")
        if prefs.push_enabled:
            selected.append("PUSH")
        return selected
    
    # Honor user preferences for non-critical
    if requested_channels:
        for channel in requested_channels:
            if is_channel_enabled(prefs, channel):
                selected.append(channel)
    else:
        # Default channel selection logic
        if event_type in ["transactional", "order_update"]:
            if prefs.email_enabled:
                selected.append("EMAIL")
            if prefs.push_enabled:
                selected.append("PUSH")
        elif event_type == "marketing":
            # Cost optimization: prefer free channels
            if prefs.push_enabled:
                selected.append("PUSH")
            elif prefs.email_enabled:
                selected.append("EMAIL")
    
    # Check quiet hours
    if is_quiet_hours(prefs) and priority != "CRITICAL":
        # Delay notification or only use silent channels (email)
        selected = [ch for ch in selected if ch == "EMAIL"]
    
    return selected


def is_channel_enabled(prefs, channel):
    """Check if user has enabled this channel and not opted out"""
    if channel == "EMAIL" and not prefs.email_enabled:
        return False
    if channel == "SMS" and not prefs.sms_enabled:
        return False
    if channel == "PUSH" and not prefs.push_enabled:
        return False
    
    # Check opt-out categories
    event_category = get_event_category(event_type)
    if event_category in prefs.opt_out_categories:
        return False
    
    return True
```

### 2. Deduplication Logic

```python
def check_deduplication(user_id, event_type, template_data):
    """
    Prevent sending duplicate notifications within short time window
    
    Example: User clicks "resend OTP" multiple times rapidly
    """
    # Create hash of notification content
    content_hash = hashlib.sha256(
        f"{user_id}:{event_type}:{json.dumps(template_data, sort_keys=True)}".encode()
    ).hexdigest()[:16]
    
    dedup_key = f"dedup:{user_id}:{event_type}:{content_hash}"
    
    # Check if we've seen this exact notification recently
    existing = redis.get(dedup_key)
    if existing:
        logger.info(f"Duplicate notification detected: {existing}")
        return existing  # Return existing notification_id
    
    # Not a duplicate, create new notification
    notification_id = str(uuid.uuid4())
    
    # Store for 5 minutes to catch rapid duplicates
    redis.setex(dedup_key, 300, notification_id)
    
    return notification_id
```

### 3. Retry Logic with Exponential Backoff

```python
import time
import random

def send_with_retry(notification, channel, max_retries=5):
    """
    Send notification with exponential backoff retry
    
    Retry intervals: 2s, 4s, 8s, 16s, 32s (with jitter)
    """
    attempt = 0
    base_delay = 2  # seconds
    
    while attempt < max_retries:
        try:
            # Attempt to send via provider
            result = send_to_provider(notification, channel)
            
            if result.status == "success":
                # Update DB: status = DELIVERED
                update_notification_status(notification.id, "DELIVERED", 
                                          sent_at=now(), 
                                          provider_message_id=result.message_id)
                return result
            
            # Provider returned error
            if result.is_permanent_failure():
                # Don't retry: invalid recipient, malformed content
                move_to_dead_letter_queue(notification, result.error)
                return None
            
        except ProviderException as e:
            logger.error(f"Provider error on attempt {attempt + 1}: {e}")
            
            if e.is_rate_limit():
                # Provider is rate limiting us, back off longer
                delay = base_delay * (2 ** (attempt + 2))
            else:
                # Exponential backoff with jitter
                delay = base_delay * (2 ** attempt)
                jitter = random.uniform(0, delay * 0.5)
                delay += jitter
            
            attempt += 1
            
            if attempt < max_retries:
                logger.info(f"Retrying in {delay:.2f}s (attempt {attempt}/{max_retries})")
                time.sleep(delay)
            else:
                # Max retries exceeded
                logger.error(f"Max retries exceeded for notification {notification.id}")
                move_to_dead_letter_queue(notification, str(e))
                return None
    
    return None
```


### 4. Circuit Breaker Pattern

```python
class CircuitBreaker:
    """
    Prevent cascading failures when provider is down
    
    States: CLOSED (normal) → OPEN (failing) → HALF_OPEN (testing)
    """
    def __init__(self, failure_threshold=5, timeout=30):
        self.failure_threshold = failure_threshold
        self.timeout = timeout  # seconds
        self.failures = 0
        self.last_failure_time = None
        self.state = "CLOSED"  # CLOSED, OPEN, HALF_OPEN
    
    def call(self, func, *args, **kwargs):
        if self.state == "OPEN":
            # Check if timeout has passed
            if time.time() - self.last_failure_time > self.timeout:
                self.state = "HALF_OPEN"
                logger.info("Circuit breaker entering HALF_OPEN state")
            else:
                raise CircuitBreakerOpenException("Circuit breaker is OPEN, using fallback")
        
        try:
            result = func(*args, **kwargs)
            self.on_success()
            return result
        except Exception as e:
            self.on_failure()
            raise e
    
    def on_success(self):
        self.failures = 0
        if self.state == "HALF_OPEN":
            self.state = "CLOSED"
            logger.info("Circuit breaker recovered, now CLOSED")
    
    def on_failure(self):
        self.failures += 1
        self.last_failure_time = time.time()
        
        if self.failures >= self.failure_threshold:
            self.state = "OPEN"
            logger.error(f"Circuit breaker OPEN after {self.failures} failures")


# Usage
sendgrid_circuit = CircuitBreaker(failure_threshold=5, timeout=30)

def send_email(notification):
    try:
        return sendgrid_circuit.call(sendgrid.send, notification)
    except CircuitBreakerOpenException:
        # Fallback to secondary provider
        logger.info("Using fallback provider: AWS SES")
        return ses_client.send(notification)
```

---

## Step 6: Optimizations & Scaling (15 min)

### 1. Template Caching & Pre-compilation

```python
class TemplateEngine:
    """
    Cache and pre-compile frequently used templates
    """
    def __init__(self):
        self.redis = redis.StrictRedis()
        self.jinja_env = jinja2.Environment()
    
    def get_template(self, template_name, version="latest"):
        # Check Redis cache first
        cache_key = f"template:{template_name}:{version}"
        cached = self.redis.get(cache_key)
        
        if cached:
            return self.jinja_env.from_string(cached)
        
        # Cache miss - load from database
        template_obj = db.query(
            "SELECT body_template FROM notification_templates "
            "WHERE name = ? AND version = ?",
            template_name, version
        )
        
        if not template_obj:
            raise TemplateNotFoundException(template_name)
        
        # Cache for 24 hours
        self.redis.setex(cache_key, 86400, template_obj.body_template)
        
        return self.jinja_env.from_string(template_obj.body_template)
    
    def render(self, template_name, data):
        template = self.get_template(template_name)
        return template.render(**data)


# Example usage
engine = TemplateEngine()
body = engine.render("order_shipped", {
    "user_name": "John Doe",
    "order_id": "ORD-123",
    "tracking_url": "https://track.example.com/123",
    "delivery_date": "Nov 18, 2025"
})
```

### 2. Batching for Cost Optimization

```python
class NotificationBatcher:
    """
    Batch non-urgent notifications to reduce provider API calls
    
    Benefits:
    - Reduce API overhead (fewer HTTP requests)
    - Take advantage of bulk pricing (some providers)
    - Smooth out traffic spikes
    """
    def __init__(self, batch_size=100, flush_interval=60):
        self.batch_size = batch_size
        self.flush_interval = flush_interval  # seconds
        self.batches = defaultdict(list)  # {user_id: [notifications]}
        self.last_flush = time.time()
    
    def add(self, notification):
        if notification.priority == "CRITICAL":
            # Don't batch critical notifications
            send_immediately(notification)
            return
        
        user_id = notification.user_id
        self.batches[user_id].append(notification)
        
        # Flush conditions
        if len(self.batches[user_id]) >= self.batch_size:
            self.flush_user(user_id)
        elif time.time() - self.last_flush > self.flush_interval:
            self.flush_all()
    
    def flush_user(self, user_id):
        notifications = self.batches.pop(user_id, [])
        if notifications:
            send_batch_to_provider(notifications)
    
    def flush_all(self):
        for user_id in list(self.batches.keys()):
            self.flush_user(user_id)
        self.last_flush = time.time()
```

### 3. Smart Send-Time Optimization (ML-based)

```python
class SendTimeOptimizer:
    """
    ML model to predict optimal send time for each user
    
    Features:
    - Historical open rates by hour
    - User timezone
    - Day of week patterns
    - Device usage patterns
    
    Algorithm: Time-series forecasting (Prophet/LSTM)
    """
    def __init__(self):
        self.model = load_trained_model("send_time_predictor.pkl")
    
    def get_optimal_send_time(self, user_id, notification_type):
        # Fetch user engagement history
        history = get_user_engagement_history(user_id)
        
        # Predict best send time
        features = {
            'user_id': user_id,
            'notification_type': notification_type,
            'day_of_week': datetime.now().weekday(),
            'user_timezone': get_user_timezone(user_id),
            'historical_open_rate_by_hour': history['hourly_open_rates']
        }
        
        optimal_hour = self.model.predict(features)
        
        # Schedule for optimal time (if not urgent)
        if notification_type != "transactional":
            scheduled_time = get_next_occurrence(optimal_hour)
            return scheduled_time
        
        return datetime.now()  # Send immediately for transactional


# Example: 30-40% improvement in open rates
optimizer = SendTimeOptimizer()
send_time = optimizer.get_optimal_send_time(user_id=123, notification_type="marketing")
schedule_notification(notification, send_at=send_time)
```

### 4. Database Sharding Strategy

```python
"""
Shard notification logs by user_id for even distribution

Sharding function:
    shard_id = hash(user_id) % num_shards

Example with 16 shards:
    user_id=12345 → hash → shard_7
    user_id=67890 → hash → shard_3

Benefits:
- Even data distribution
- Parallel queries
- Independent scaling of shards
"""

def get_shard_id(user_id, num_shards=16):
    return hash(user_id) % num_shards

def get_db_connection(user_id):
    shard_id = get_shard_id(user_id)
    return db_pool.get_connection(f"notifications_shard_{shard_id}")

# Query example
def get_user_notifications(user_id, limit=100):
    conn = get_db_connection(user_id)
    shard_id = get_shard_id(user_id)
    
    return conn.query(
        f"SELECT * FROM notifications_shard_{shard_id} "
        "WHERE user_id = ? ORDER BY created_at DESC LIMIT ?",
        user_id, limit
    )
```

### 5. Rate Limiting Implementation

```python
class SlidingWindowRateLimiter:
    """
    Prevent notification fatigue with sliding window rate limiting
    
    Limits:
    - Marketing: 3 per day per user
    - Social: 50 per day per user
    - Transactional: Unlimited
    """
    def __init__(self, redis_client):
        self.redis = redis_client
    
    def is_allowed(self, user_id, notification_type, limit, window):
        """
        Sliding window rate limiting using sorted sets
        
        Args:
            user_id: User ID
            notification_type: marketing, social, transactional
            limit: Max notifications in window
            window: Time window in seconds
        
        Returns:
            True if allowed, False otherwise
        """
        if notification_type == "transactional":
            return True  # No limit on transactional
        
        key = f"ratelimit:{user_id}:{notification_type}"
        now = time.time()
        window_start = now - window
        
        # Remove old entries outside window
        self.redis.zremrangebyscore(key, 0, window_start)
        
        # Count current entries in window
        count = self.redis.zcard(key)
        
        if count >= limit:
            return False
        
        # Add current timestamp
        self.redis.zadd(key, {now: now})
        self.redis.expire(key, window)
        
        return True


# Usage
rate_limiter = SlidingWindowRateLimiter(redis_client)

if rate_limiter.is_allowed(user_id=123, notification_type="marketing", 
                          limit=3, window=86400):  # 3 per day
    send_notification(notification)
else:
    logger.info(f"Rate limit exceeded for user {user_id}")
```

---

## Step 7: Monitoring & Observability

### Key Metrics to Track

```python
"""
Prometheus metrics for notification service
"""
from prometheus_client import Counter, Histogram, Gauge

# Notification metrics
notifications_sent = Counter(
    'notifications_sent_total',
    'Total notifications sent',
    ['channel', 'priority', 'status']
)

notifications_latency = Histogram(
    'notification_delivery_latency_seconds',
    'Time from queue to delivery',
    ['channel'],
    buckets=[0.1, 0.5, 1, 2, 5, 10, 30, 60]
)

notifications_failed = Counter(
    'notifications_failed_total',
    'Total failed notifications',
    ['channel', 'error_type']
)

# Queue metrics
queue_depth = Gauge(
    'notification_queue_depth',
    'Current queue depth',
    ['channel']
)

# Provider metrics
provider_api_latency = Histogram(
    'provider_api_latency_seconds',
    'Provider API call latency',
    ['provider', 'operation']
)

circuit_breaker_state = Gauge(
    'circuit_breaker_state',
    'Circuit breaker state (0=closed, 1=open, 2=half_open)',
    ['provider']
)


# Instrumentation example
@notifications_latency.labels(channel='email').time()
def send_email(notification):
    try:
        result = sendgrid.send(notification)
        notifications_sent.labels(
            channel='email',
            priority=notification.priority,
            status='success'
        ).inc()
        return result
    except Exception as e:
        notifications_failed.labels(
            channel='email',
            error_type=type(e).__name__
        ).inc()
        raise
```

### Alerting Rules

```yaml
# Prometheus alerting rules
groups:
  - name: notification_service
    interval: 30s
    rules:
      - alert: HighNotificationFailureRate
        expr: |
          rate(notifications_failed_total[5m]) / rate(notifications_sent_total[5m]) > 0.05
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High notification failure rate: {{ $value | humanizePercentage }}"
      
      - alert: NotificationQueueBacklog
        expr: notification_queue_depth > 10000
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "Notification queue depth: {{ $value }}"
      
      - alert: NotificationLatencyHigh
        expr: |
          histogram_quantile(0.95, rate(notification_delivery_latency_seconds_bucket[5m])) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "P95 latency {{ $value }}s exceeds 5s SLA"
      
      - alert: CircuitBreakerOpen
        expr: circuit_breaker_state == 1
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Circuit breaker open for {{ $labels.provider }}"
```

---

## Step 8: Security & Compliance

### 1. Data Privacy (GDPR, CCPA)

```python
"""
GDPR compliance for notification service
"""

# Right to be forgotten
def delete_user_data(user_id):
    """
    Anonymize or delete all user notification data
    """
    # Anonymize notification logs (keep for analytics)
    db.execute(
        "UPDATE notifications SET user_id = 0, metadata = '{}' "
        "WHERE user_id = ?",
        user_id
    )
    
    # Delete user preferences
    db.execute("DELETE FROM user_preferences WHERE user_id = ?", user_id)
    
    # Clear cache
    redis.delete(f"user:prefs:{user_id}")
    
    logger.info(f"User data deleted for user_id={user_id} (GDPR compliance)")


# Data retention policy
def cleanup_old_notifications():
    """
    Delete notifications older than retention period
    
    Transactional: 90 days (compliance)
    Marketing: 30 days (cost optimization)
    """
    cutoff_transactional = datetime.now() - timedelta(days=90)
    cutoff_marketing = datetime.now() - timedelta(days=30)
    
    db.execute(
        "DELETE FROM notifications "
        "WHERE event_type = 'marketing' AND created_at < ?",
        cutoff_marketing
    )
    
    db.execute(
        "DELETE FROM notifications "
        "WHERE event_type != 'marketing' AND created_at < ?",
        cutoff_transactional
    )
```

### 2. Encryption

```python
"""
Encryption at rest and in transit
"""

# Encrypt sensitive notification content
from cryptography.fernet import Fernet

class NotificationEncryption:
    def __init__(self, encryption_key):
        self.cipher = Fernet(encryption_key)
    
    def encrypt_payload(self, data):
        """Encrypt notification payload (PII, sensitive data)"""
        json_data = json.dumps(data)
        encrypted = self.cipher.encrypt(json_data.encode())
        return encrypted
    
    def decrypt_payload(self, encrypted_data):
        """Decrypt for sending"""
        decrypted = self.cipher.decrypt(encrypted_data)
        return json.loads(decrypted.decode())


# SSL/TLS for all provider connections
import requests

session = requests.Session()
session.verify = True  # Verify SSL certificates
session.headers.update({'User-Agent': 'NotificationService/1.0'})

# Example: SendGrid API with TLS
response = session.post(
    'https://api.sendgrid.com/v3/mail/send',
    headers={'Authorization': f'Bearer {SENDGRID_API_KEY}'},
    json=email_payload
)
```

---

## Step 9: Cost Optimization Strategies

### Channel Cost Analysis

```python
"""
Real-time cost tracking and optimization
"""

class CostOptimizer:
    """
    Optimize notification costs by intelligent channel selection
    
    Cost hierarchy (per notification):
    1. Push: $0 (FREE)
    2. Email: $0.001
    3. WhatsApp: $0.005
    4. SMS: $0.0075 (varies by country)
    
    Strategy:
    - Prefer push for real-time, non-critical
    - Use email for rich content, marketing
    - Reserve SMS for critical, high-value events
    """
    
    CHANNEL_COSTS = {
        'PUSH': 0.0,
        'EMAIL': 0.001,
        'WHATSAPP': 0.005,
        'SMS': 0.0075
    }
    
    def select_cost_optimal_channel(self, user_prefs, notification_type, priority):
        available = get_enabled_channels(user_prefs)
        
        if priority == "CRITICAL":
            # For critical, choose most reliable (cost secondary)
            if 'SMS' in available:
                return 'SMS'
            elif 'PUSH' in available:
                return 'PUSH'
        
        # For non-critical, optimize for cost
        if notification_type == "marketing":
            # Marketing: Push (free) > Email (cheap)
            if 'PUSH' in available:
                return 'PUSH'
            elif 'EMAIL' in available:
                return 'EMAIL'
        
        elif notification_type == "transactional":
            # Transactional: Email (audit trail) or Push (instant)
            if 'EMAIL' in available:
                return 'EMAIL'
            elif 'PUSH' in available:
                return 'PUSH'
        
        # Fallback to cheapest available
        return min(available, key=lambda ch: self.CHANNEL_COSTS.get(ch, 999))
    
    def estimate_monthly_cost(self, daily_volume, channel_mix):
        """
        Estimate monthly channel costs
        
        Example:
            daily_volume = 200M
            channel_mix = {'PUSH': 0.6, 'EMAIL': 0.3, 'SMS': 0.1}
            
            Monthly cost = 30 days × (
                120M × $0 + 
                60M × $0.001 + 
                20M × $0.0075
            ) = 30 × $210 = $6,300
        """
        daily_cost = sum(
            daily_volume * percentage * self.CHANNEL_COSTS[channel]
            for channel, percentage in channel_mix.items()
        )
        return daily_cost * 30


# Real-world savings example
optimizer = CostOptimizer()

# Baseline: 100M marketing notifications via SMS
baseline_cost = 100_000_000 * 0.0075  # $750,000/day

# Optimized: Shift 90% to push, 10% to email
optimized_cost = (
    90_000_000 * 0.0 +      # Push: $0
    10_000_000 * 0.001       # Email: $10
)  # $10/day

savings = baseline_cost - optimized_cost
print(f"Daily savings: ${savings:,.0f}")
print(f"Monthly savings: ${savings * 30:,.0f}")
# Output: Daily savings: $749,990 | Monthly savings: $22,499,700
```

---

## Step 10: Interview Deep Dive Topics

### Topic 1: Handling Notification Storms

**Scenario**: Black Friday sale launches, 10M users need notification simultaneously

**Solutions**:
1. **Queue-based buffering**: Kafka absorbs spike, workers process at sustainable rate
2. **Priority lanes**: Critical (OTP) gets dedicated fast lane, marketing uses slow lane
3. **Auto-scaling**: Horizontal pod autoscaling based on queue depth
4. **Rate limiting**: Smooth out provider API calls to avoid getting throttled

```python
# Priority-based queue consumption
def consume_with_priority():
    while True:
        # Check critical queue first
        message = kafka.poll('notifications.critical', timeout=100)
        if message:
            process_notification(message, priority='CRITICAL')
            continue
        
        # Then high priority
        message = kafka.poll('notifications.high', timeout=100)
        if message:
            process_notification(message, priority='HIGH')
            continue
        
        # Finally standard
        message = kafka.poll('notifications.standard', timeout=1000)
        if message:
            process_notification(message, priority='STANDARD')
```

### Topic 2: Multi-Region Deployment

**Challenge**: Minimize latency for global users

**Architecture**:
```
Region: US-East
- API Gateway (primary)
- Kafka cluster (3 brokers)
- Workers (50 instances)
- PostgreSQL (primary)

Region: EU-West
- API Gateway (read-only for EU users)
- Kafka cluster (replicated from US)
- Workers (30 instances)
- PostgreSQL (read replica)

Region: AP-Southeast
- API Gateway (read-only)
- Workers (20 instances)
- PostgreSQL (read replica)
```

**Data Consistency**:
- Write to primary region (US-East)
- Async replication to other regions (<100ms lag)
- Read from local region for low latency

### Topic 3: Idempotency

**Problem**: API called twice (network retry), don't send duplicate notifications

**Solution**: Idempotency keys

```python
@app.route('/api/v1/notifications/send', methods=['POST'])
def send_notification():
    idempotency_key = request.headers.get('Idempotency-Key')
    
    if idempotency_key:
        # Check if we've seen this request before
        cached = redis.get(f"idempotent:{idempotency_key}")
        if cached:
            # Return same response as original request
            return jsonify(json.loads(cached)), 201
    
    # Process new request
    notification_id = create_notification(request.json)
    response = {'notification_id': notification_id, 'status': 'queued'}
    
    # Cache response for 24 hours
    if idempotency_key:
        redis.setex(f"idempotent:{idempotency_key}", 86400, json.dumps(response))
    
    return jsonify(response), 201
```

---

## Complete System Metrics Summary

| **Metric** | **Value** | **How to Achieve** |
|------------|-----------|-------------------|
| **Throughput** | 10K QPS peak | Kafka partitioning, horizontal worker scaling |
| **Latency (P95)** | <5 seconds | Redis caching, async processing, provider optimization |
| **Availability** | 99.99% | Multi-AZ, auto-scaling, circuit breakers, fallback providers |
| **Delivery Rate** | 99.9% | Retries with exponential backoff, multi-provider redundancy |
| **Cost (daily)** | $210 channels + $250 infra | Channel optimization (prefer push), batching, reserved instances |
| **Storage** | 13 TB (90 days) | Partitioning, archival policies, compression |

---

## Key Takeaways

1. **Queue-Based Architecture**: Decouple producers from consumers for scalability and reliability
2. **Multi-Channel Strategy**: Optimize based on cost, latency, and user preference
3. **Retry with Backoff**: Handle transient failures gracefully
4. **Circuit Breakers**: Fail fast and use fallback providers
5. **Cost Awareness**: Push is free, SMS is expensive - design accordingly
6. **User Preferences**: Respect opt-outs, quiet hours (legal requirement)
7. **Idempotency**: Prevent duplicate notifications
8. **Monitoring**: Track delivery rate, latency, cost in real-time

---

## Further Reading

- **System Design Books**: "Designing Data-Intensive Applications" by Martin Kleppmann
- **Provider Docs**: Twilio, SendGrid, AWS SNS/SES, Firebase Cloud Messaging
- **Real-world examples**: Uber's notification system, Airbnb's notification platform
- **ML optimization**: "Bandit Algorithms for Website Optimization"

---

**Interview Success Formula**:
> Show **cost optimization** + **scalability thinking** + **reliability patterns** = Strong notification system design!

