# Rate Limiting

Controlling the rate of requests to protect services from overload, abuse, and ensure fair usage.

## Contents
- [What is Rate Limiting?](#what-is-rate-limiting)
- [Why Rate Limiting Matters](#why-rate-limiting-matters)
- [Rate Limiting Algorithms](#rate-limiting-algorithms)
- [Implementation Strategies](#implementation-strategies)
- [Response Strategies](#response-strategies)
- [Distributed Rate Limiting](#distributed-rate-limiting)
- [Best Practices](#best-practices)

## What is Rate Limiting?

Rate limiting restricts the number of requests a user/client can make to an API within a time window.

### Basic Concept

```
User makes 100 requests/second
Rate limit: 10 requests/second
Result: Accept 10, reject 90
```

### Common Rate Limit Rules

```
# Per user
- 100 requests per minute
- 1000 requests per hour
- 10000 requests per day

# Per API endpoint
- /api/search: 10 requests/minute (expensive)
- /api/users: 100 requests/minute (cheap)

# By tier
- Free tier: 60 requests/hour
- Paid tier: 1000 requests/hour
- Enterprise: 10000 requests/hour
```

## Why Rate Limiting Matters

### 1. Prevent Service Overload

```
Scenario: Black Friday sale
Without rate limiting:
- 1M users × 100 req/sec = 100M req/sec
- Servers crash, nobody can buy

With rate limiting:
- Cap at 10K req/sec per user
- Service stays up, most users succeed
```

### 2. Protect Against Abuse

```
Attack: Credential stuffing
Attacker tries 10,000 passwords/second

Rate limit: 5 login attempts/minute
Result: Attack slowed to 5/min (impractical)
```

### 3. Fair Resource Allocation

```
Without limiting:
- User A: 10,000 requests (consuming resources)
- User B: 100 requests (suffering slow responses)

With limiting:
- User A: 1,000 requests (capped)
- User B: 100 requests (normal performance)
```

### 4. Cost Control

```
Third-party API costs $0.001 per request

Without limiting:
- Bug in code → infinite loop
- 10M requests in 1 hour
- Cost: $10,000

With limiting:
- Cap at 10K requests/hour
- Cost: $10 (limited damage)
```

### 5. Compliance

```
Terms of Service enforcement:
- Free tier: 100 requests/hour
- Prevent abuse of free tier
- Encourage upgrade to paid plans
```

## Rate Limiting Algorithms

### 1. Token Bucket

Most flexible and commonly used algorithm.

#### How It Works

```
Bucket capacity: 10 tokens
Refill rate: 1 token/second

Each request consumes 1 token:
1. Check if bucket has ≥1 token
2. If yes: consume token, allow request
3. If no: reject request
4. Tokens refill at steady rate
```

#### Implementation

```python
import time

class TokenBucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity
        self.tokens = capacity
        self.refill_rate = refill_rate  # tokens per second
        self.last_refill = time.time()

    def allow_request(self):
        # Refill tokens based on time elapsed
        now = time.time()
        elapsed = now - self.last_refill
        tokens_to_add = elapsed * self.refill_rate
        self.tokens = min(self.capacity, self.tokens + tokens_to_add)
        self.last_refill = now

        # Check if request can be allowed
        if self.tokens >= 1:
            self.tokens -= 1
            return True
        return False

# Usage
bucket = TokenBucket(capacity=10, refill_rate=1)  # 1 token/sec

if bucket.allow_request():
    process_request()
else:
    return_429_error()
```

#### Characteristics

**Pros:**
- Allows bursts (up to bucket capacity)
- Smooth refilling
- Memory efficient

**Cons:**
- Bursts can overwhelm if many buckets burst simultaneously

**Best for:** APIs that should allow short bursts

**Example:**
```
Limit: 100 requests/hour (1.67 requests/min)
Bucket: 100 tokens, refill 1.67/min

User can:
- Make 100 requests immediately (burst)
- Then 1.67 requests/min steady state
```

### 2. Leaky Bucket

Processes requests at a constant rate, smoothing bursts.

#### How It Works

```
Bucket capacity: 10 requests
Process rate: 1 request/second

Requests enter bucket:
1. If bucket full: reject
2. If space: add to bucket
3. Process at constant rate (1/sec)
```

#### Implementation

```python
from collections import deque
import time
import threading

class LeakyBucket:
    def __init__(self, capacity, leak_rate):
        self.capacity = capacity
        self.queue = deque()
        self.leak_rate = leak_rate  # requests per second

    def allow_request(self):
        # Remove old requests (leak)
        now = time.time()
        while self.queue and now - self.queue[0] > 1/self.leak_rate:
            self.queue.popleft()

        # Check capacity
        if len(self.queue) < self.capacity:
            self.queue.append(now)
            return True
        return False

# Usage
bucket = LeakyBucket(capacity=10, leak_rate=1)

if bucket.allow_request():
    process_request()
else:
    return_429_error()
```

#### Characteristics

**Pros:**
- Smooth output rate (no bursts)
- Protects backend from spikes

**Cons:**
- May drop requests during bursts
- More complex implementation

**Best for:** Protecting backend from traffic spikes

**Difference from Token Bucket:**
```
Token Bucket: Allows bursts, refills tokens
Leaky Bucket: Constant rate, smooths bursts
```

### 3. Fixed Window Counter

Count requests in fixed time windows.

#### How It Works

```
Window: 1 minute (e.g., 12:00:00 - 12:01:00)
Limit: 100 requests/minute

12:00:00 - 12:01:00 → 100 requests allowed
12:01:00 - 12:02:00 → new window, reset to 100
```

#### Implementation

```python
import time
from collections import defaultdict

class FixedWindowCounter:
    def __init__(self, limit, window_size):
        self.limit = limit
        self.window_size = window_size  # in seconds
        self.windows = defaultdict(int)

    def allow_request(self, user_id):
        now = time.time()
        window = int(now / self.window_size)
        key = f"{user_id}:{window}"

        if self.windows[key] < self.limit:
            self.windows[key] += 1
            return True
        return False

# Usage
limiter = FixedWindowCounter(limit=100, window_size=60)

if limiter.allow_request(user_id="user123"):
    process_request()
else:
    return_429_error()
```

#### Characteristics

**Pros:**
- Simple to implement
- Memory efficient (one counter per window)
- Easy to understand

**Cons:**
- **Burst at window edges** (major problem)

**Edge Case Problem:**
```
Limit: 100 requests/minute

12:00:30 - 12:01:00 → 100 requests (allowed)
12:01:00 - 12:01:30 → 100 requests (allowed)

Result: 200 requests in 1 minute (12:00:30 - 12:01:30)
2x the intended limit!
```

**Best for:** Simple use cases where edge bursts are acceptable

### 4. Sliding Window Log

Track timestamp of each request.

#### How It Works

```
Limit: 100 requests/hour
Store: Timestamp of each request

New request at 3:15 PM:
1. Remove requests older than 1 hour
2. Count remaining requests
3. If < 100: allow, if ≥ 100: reject
```

#### Implementation

```python
import time
from collections import defaultdict, deque

class SlidingWindowLog:
    def __init__(self, limit, window_size):
        self.limit = limit
        self.window_size = window_size  # in seconds
        self.logs = defaultdict(deque)

    def allow_request(self, user_id):
        now = time.time()
        log = self.logs[user_id]

        # Remove old entries
        while log and now - log[0] > self.window_size:
            log.popleft()

        # Check limit
        if len(log) < self.limit:
            log.append(now)
            return True
        return False

# Usage
limiter = SlidingWindowLog(limit=100, window_size=3600)

if limiter.allow_request(user_id="user123"):
    process_request()
else:
    return_429_error()
```

#### Characteristics

**Pros:**
- Accurate (no edge burst problem)
- Precise rate limiting

**Cons:**
- Memory intensive (store every timestamp)
- Expensive for high-traffic systems

**Best for:** Low to medium traffic with strict rate limiting requirements

**Memory Usage:**
```
1M users × 100 requests/hour × 8 bytes/timestamp
= 800 MB memory
```

### 5. Sliding Window Counter

Hybrid: Fixed windows + weighted count.

#### How It Works

```
Limit: 100 requests/hour
Current time: 3:15 PM (15 minutes into window)

Previous window (2:00-3:00): 80 requests
Current window (3:00-4:00): 40 requests

Weighted count:
= (previous × (60-15)/60) + (current)
= (80 × 0.75) + 40
= 60 + 40
= 100 requests

If 100 < limit: allow, else: reject
```

#### Implementation

```python
import time
from collections import defaultdict

class SlidingWindowCounter:
    def __init__(self, limit, window_size):
        self.limit = limit
        self.window_size = window_size
        self.windows = defaultdict(lambda: {'prev': 0, 'curr': 0, 'curr_start': 0})

    def allow_request(self, user_id):
        now = time.time()
        window_start = int(now / self.window_size) * self.window_size
        data = self.windows[user_id]

        # Rotate windows if needed
        if window_start != data['curr_start']:
            data['prev'] = data['curr']
            data['curr'] = 0
            data['curr_start'] = window_start

        # Calculate weighted count
        elapsed = now - window_start
        weight = 1 - (elapsed / self.window_size)
        estimated_count = data['prev'] * weight + data['curr']

        if estimated_count < self.limit:
            data['curr'] += 1
            return True
        return False

# Usage
limiter = SlidingWindowCounter(limit=100, window_size=3600)

if limiter.allow_request(user_id="user123"):
    process_request()
else:
    return_429_error()
```

#### Characteristics

**Pros:**
- More accurate than fixed window
- Memory efficient (only 2 counters)
- Reduces edge burst problem

**Cons:**
- Approximation (not perfectly accurate)
- More complex than fixed window

**Best for:** Production systems (good balance of accuracy and efficiency)

### Algorithm Comparison

| Algorithm | Accuracy | Memory | Bursts | Complexity |
|-----------|----------|--------|--------|------------|
| **Token Bucket** | Good | Low | Allows | Medium |
| **Leaky Bucket** | Good | Low | Smooths | Medium |
| **Fixed Window** | Poor (edge bursts) | Very Low | Allows 2x | Low |
| **Sliding Log** | Perfect | High | Prevents | Low |
| **Sliding Counter** | Good | Low | Reduces | Medium |

### Which to Choose?

```
Token Bucket: Default choice for APIs
Leaky Bucket: Protecting backend services
Fixed Window: Simple cases, internal services
Sliding Log: Strict limits, low traffic
Sliding Counter: Production APIs (best balance)
```

## Implementation Strategies

### 1. In-Memory (Single Server)

```python
# Simple dictionary-based
rate_limits = {}

def check_rate_limit(user_id):
    if user_id not in rate_limits:
        rate_limits[user_id] = TokenBucket(100, 1)
    return rate_limits[user_id].allow_request()
```

**Pros:** Fast, simple
**Cons:** Lost on restart, doesn't work across servers

### 2. Redis (Distributed)

```python
import redis
import time

redis_client = redis.Redis()

def check_rate_limit(user_id, limit=100, window=60):
    key = f"rate_limit:{user_id}"
    now = int(time.time())
    window_start = now - window

    # Remove old entries
    redis_client.zremrangebyscore(key, 0, window_start)

    # Count current requests
    count = redis_client.zcard(key)

    if count < limit:
        # Add current request
        redis_client.zadd(key, {now: now})
        redis_client.expire(key, window)
        return True
    return False
```

**Pros:** Works across servers, persistent
**Cons:** Network latency, single point of failure (use Redis cluster)

### 3. Redis with Token Bucket (Efficient)

```lua
-- Lua script (executes atomically in Redis)
local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])

local bucket = redis.call('HMGET', key, 'tokens', 'last_refill')
local tokens = tonumber(bucket[1]) or capacity
local last_refill = tonumber(bucket[2]) or now

-- Refill tokens
local elapsed = now - last_refill
local tokens_to_add = elapsed * rate
tokens = math.min(capacity, tokens + tokens_to_add)

-- Try to consume token
if tokens >= 1 then
    tokens = tokens - 1
    redis.call('HMSET', key, 'tokens', tokens, 'last_refill', now)
    redis.call('EXPIRE', key, 3600)
    return 1  -- Allow
else
    return 0  -- Deny
end
```

```python
def check_rate_limit_redis(user_id):
    key = f"rate_limit:{user_id}"
    result = redis_client.eval(
        lua_script,
        1,  # number of keys
        key,  # KEYS[1]
        100,  # ARGV[1] - capacity
        1,    # ARGV[2] - rate (tokens/sec)
        time.time()  # ARGV[3] - now
    )
    return result == 1
```

**Pros:** Atomic, fast, distributed
**Best practice:** Use this for production

## Response Strategies

### 1. Reject with 429 Status

```http
HTTP/1.1 429 Too Many Requests
Retry-After: 60
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1640000000

{
  "error": "Rate limit exceeded",
  "retry_after": 60
}
```

### 2. Standard Headers

```http
X-RateLimit-Limit: 100        # Total allowed
X-RateLimit-Remaining: 37     # Requests left
X-RateLimit-Reset: 1640000000 # Unix timestamp when reset
Retry-After: 60               # Seconds until can retry
```

### 3. Exponential Backoff

```python
def retry_with_backoff(func, max_retries=5):
    for i in range(max_retries):
        try:
            return func()
        except RateLimitError:
            wait = (2 ** i) + random.random()  # 1, 2, 4, 8, 16 seconds
            time.sleep(wait)
    raise MaxRetriesExceeded()
```

### 4. Queue Requests

```python
from celery import Celery

app = Celery('tasks')

@app.task(rate_limit='100/m')  # 100 per minute
def process_api_request(data):
    # Process request
    pass

# Requests over limit are queued
process_api_request.delay(data)
```

### 5. Throttle (Slow Down)

```python
def throttled_request():
    if is_rate_limited():
        time.sleep(1)  # Slow down instead of reject
    return process_request()
```

## Distributed Rate Limiting

### Challenge: Multiple Servers

```
User makes 60 requests/min to Server 1 (allowed)
User makes 60 requests/min to Server 2 (allowed)
Total: 120 requests/min (exceeds 100/min limit!)
```

### Solution 1: Centralized Counter (Redis)

```
All servers check same Redis counter
Ensures accurate rate limiting
```

**Pros:** Accurate
**Cons:** Redis becomes bottleneck, single point of failure

### Solution 2: Sticky Sessions

```
Load balancer routes user to same server
Each server tracks its own rate limits
```

**Pros:** No coordination needed
**Cons:** Uneven load, lost on server restart

### Solution 3: Distributed Counters with Sync

```
Each server tracks locally
Periodically sync with central store
Accept some inaccuracy for better performance
```

### Solution 4: Redis Cluster

```
Shard rate limit data across Redis cluster
High availability, scalable
```

**Implementation:**
```python
import rediscluster

startup_nodes = [
    {"host": "redis1", "port": "7000"},
    {"host": "redis2", "port": "7000"},
    {"host": "redis3", "port": "7000"}
]

rc = rediscluster.RedisCluster(startup_nodes=startup_nodes)

def check_rate_limit(user_id):
    key = f"rate_limit:{user_id}"
    # Redis cluster automatically shards by key
    return rc.incr(key) <= 100
```

## Best Practices

### 1. Multiple Limits

```python
limits = {
    '/api/search': (10, 60),      # 10/min (expensive)
    '/api/users': (100, 60),      # 100/min
    'default': (60, 60)            # 60/min
}

def get_limit(endpoint, tier):
    base_limit = limits.get(endpoint, limits['default'])
    if tier == 'premium':
        return (base_limit[0] * 10, base_limit[1])
    return base_limit
```

### 2. Different Limits by User Tier

```python
tier_limits = {
    'free': 60,
    'basic': 600,
    'premium': 6000,
    'enterprise': 60000
}

limit = tier_limits[user.tier]
```

### 3. Per-Endpoint Limits

```python
@app.route('/api/search')
@rate_limit(10, per=60)  # 10/min
def search():
    pass

@app.route('/api/users')
@rate_limit(100, per=60)  # 100/min
def get_users():
    pass
```

### 4. Whitelisting

```python
WHITELIST = ['admin_user', 'monitoring_service']

def check_rate_limit(user_id):
    if user_id in WHITELIST:
        return True
    return normal_rate_limit_check(user_id)
```

### 5. Informative Errors

```python
if not check_rate_limit(user_id):
    return {
        'error': 'Rate limit exceeded',
        'limit': 100,
        'window': '1 minute',
        'retry_after': 42,  # seconds
        'upgrade_url': '/pricing'
    }, 429
```

### 6. Monitoring & Alerts

```python
if rate_limited:
    metrics.increment('rate_limit.exceeded', tags=[f'user:{user_id}'])

    # Alert if too many users hitting limit
    if metrics.get('rate_limit.exceeded.count') > 1000:
        alert('High rate limiting events')
```

### 7. Gradual Rollout

```python
# Start with warning only
if not check_rate_limit(user_id):
    log.warning(f'User {user_id} would be rate limited')
    # Still allow request
    return process_request()

# Later: enforce
if not check_rate_limit(user_id):
    return 429_error()
```

## Common Use Cases

### 1. API Rate Limiting

```python
@app.route('/api/data')
@rate_limit(100, per=3600, key='user_id')
def get_data():
    return jsonify(data)
```

### 2. Login Attempt Limiting

```python
@app.route('/login', methods=['POST'])
@rate_limit(5, per=300, key='ip_address')  # 5 attempts per 5 min
def login():
    # Prevent brute force attacks
    pass
```

### 3. Webhook Callbacks

```python
@app.route('/webhook', methods=['POST'])
@rate_limit(10, per=60, key='source_ip')
def webhook():
    # Prevent webhook spam
    pass
```

### 4. Resource-Intensive Operations

```python
@app.route('/api/export', methods=['POST'])
@rate_limit(1, per=3600, key='user_id')  # 1 per hour
def export_data():
    # Expensive operation
    generate_csv_export()
```

### 5. Scraping Prevention

```python
@app.route('/products/<id>')
@rate_limit(20, per=60, key='ip_address')
def product_detail(id):
    # Prevent automated scraping
    pass
```

## Interview Tips

When discussing rate limiting:

1. **Start with why**: Explain the problem it solves
2. **Discuss algorithms**: Know token bucket and sliding window
3. **Consider distribution**: How to handle multiple servers
4. **Think about UX**: How to communicate limits to users
5. **Real-world examples**: GitHub API (5000/hour), Twitter (300/15min)

### Sample Interview Questions

**Q: Design a rate limiter for a REST API**

```
1. Clarify requirements:
   - Limits per user or IP?
   - Different limits per endpoint?
   - Distributed system?

2. Choose algorithm:
   - Token bucket (allow bursts)
   - Or sliding window counter (accurate + efficient)

3. Storage:
   - Redis for distributed rate limiting
   - Lua scripts for atomicity

4. Response:
   - 429 status code
   - Include rate limit headers
   - Retry-After header

5. Monitoring:
   - Track rate limit hits
   - Alert on unusual patterns
```

## Key Takeaways

1. Rate limiting protects services from overload and abuse
2. Token bucket and sliding window counter are best for most use cases
3. Use Redis for distributed rate limiting
4. Always include rate limit headers in responses
5. Different endpoints/tiers should have different limits
6. Monitor rate limiting metrics to tune limits

## Further Reading

- [Scaling Strategies](scaling-strategies.md) - Handling growth
- [Performance Optimization](performance.md) - Making systems faster
- [Caching Strategies](caching.md) - Reducing load
