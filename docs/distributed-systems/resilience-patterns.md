# Circuit Breakers & Resilience Patterns

## Why Resilience Matters

**Problem**: In distributed systems, failures cascade

```
User Request → Service A → Service B → Service C
                              ↓ (slow/failing)
                          (timeout)
                              ↑
                    (threads blocked)
                              ↑
              (Service A becomes slow)
                              ↑
          (All requests to A timeout)
                              ↑
                  (System-wide failure!)
```

**Resilience**: Design systems to handle and recover from failures gracefully

---

## Circuit Breaker Pattern

### Concept

**Inspired by Electrical Circuit Breakers**: Stop flow when detecting fault

**States**:
```
 [CLOSED] ─(failures exceed threshold)─→ [OPEN] ─(timeout)─→ [HALF-OPEN]
     ↑                                      │                      │
     │                                      │                      │
     └────────────────(success)─────────────┴──(failure)───────────┘
```

**CLOSED** (Normal Operation):
```
- Requests pass through
- Track failures
- If failures exceed threshold → OPEN
```

**OPEN** (Circuit Tripped):
```
- Requests fail immediately (fail-fast)
- No calls to downstream service
- After timeout period → HALF-OPEN
```

**HALF-OPEN** (Testing):
```
- Allow limited test requests
- If successful → CLOSED
- If failed → OPEN
```

### Implementation

```python
import time
from enum import Enum
from threading import Lock

class CircuitState(Enum):
    CLOSED = 1
    OPEN = 2
    HALF_OPEN = 3

class CircuitBreaker:
    def __init__(self, failure_threshold=5, timeout=60, half_open_max_calls=3):
        self.failure_threshold = failure_threshold
        self.timeout = timeout  # seconds to wait before HALF-OPEN
        self.half_open_max_calls = half_open_max_calls

        self.failure_count = 0
        self.last_failure_time = None
        self.state = CircuitState.CLOSED
        self.half_open_calls = 0
        self.lock = Lock()

    def call(self, func, *args, **kwargs):
        with self.lock:
            if self.state == CircuitState.OPEN:
                if time.time() - self.last_failure_time > self.timeout:
                    # Transition to HALF-OPEN
                    self.state = CircuitState.HALF_OPEN
                    self.half_open_calls = 0
                else:
                    raise CircuitBreakerOpenError("Circuit breaker is OPEN")

            if self.state == CircuitState.HALF_OPEN:
                if self.half_open_calls >= self.half_open_max_calls:
                    raise CircuitBreakerOpenError("Circuit breaker is HALF-OPEN (max calls)")

                self.half_open_calls += 1

        # Execute function
        try:
            result = func(*args, **kwargs)
            self._on_success()
            return result
        except Exception as e:
            self._on_failure()
            raise e

    def _on_success(self):
        with self.lock:
            if self.state == CircuitState.HALF_OPEN:
                # Success in HALF-OPEN → Close circuit
                self.state = CircuitState.CLOSED
                self.failure_count = 0

    def _on_failure(self):
        with self.lock:
            self.failure_count += 1
            self.last_failure_time = time.time()

            if self.state == CircuitState.HALF_OPEN:
                # Failure in HALF-OPEN → Re-open circuit
                self.state = CircuitState.OPEN
            elif self.failure_count >= self.failure_threshold:
                # Too many failures in CLOSED → Open circuit
                self.state = CircuitState.OPEN

class CircuitBreakerOpenError(Exception):
    pass

# Usage
breaker = CircuitBreaker(failure_threshold=5, timeout=60)

def call_external_api():
    response = requests.get("https://api.example.com/data", timeout=2)
    response.raise_for_status()
    return response.json()

try:
    data = breaker.call(call_external_api)
except CircuitBreakerOpenError:
    # Circuit is open, use fallback
    data = get_cached_data()
except Exception as e:
    # API call failed
    data = get_default_data()
```

### Real-World Example: Netflix Hystrix

**Features**:
```java
@HystrixCommand(
    fallbackMethod = "getFallbackUser",
    commandProperties = {
        @HystrixProperty(name = "circuitBreaker.requestVolumeThreshold", value = "10"),
        @HystrixProperty(name = "circuitBreaker.errorThresholdPercentage", value = "50"),
        @HystrixProperty(name = "circuitBreaker.sleepWindowInMilliseconds", value = "5000")
    }
)
public User getUser(Long userId) {
    return userService.getUser(userId);
}

public User getFallbackUser(Long userId) {
    return new User(userId, "Default User");
}
```

**Configuration**:
- `requestVolumeThreshold`: Minimum 10 requests before calculating error rate
- `errorThresholdPercentage`: Open if > 50% errors
- `sleepWindowInMilliseconds`: Wait 5s before HALF-OPEN

---

## Retry Pattern

### Exponential Backoff

**Concept**: Retry with increasing delays

```
Attempt 1: Immediate
Attempt 2: Wait 1s
Attempt 3: Wait 2s
Attempt 4: Wait 4s
Attempt 5: Wait 8s
...
```

**Implementation**:
```python
import time
import random

def retry_with_exponential_backoff(
    func,
    max_retries=5,
    base_delay=1,
    max_delay=60,
    exponential_base=2,
    jitter=True
):
    for attempt in range(max_retries):
        try:
            return func()
        except Exception as e:
            if attempt == max_retries - 1:
                raise

            # Calculate delay
            delay = min(base_delay * (exponential_base ** attempt), max_delay)

            # Add jitter to prevent thundering herd
            if jitter:
                delay = delay * (0.5 + random.random())

            print(f"Attempt {attempt + 1} failed. Retrying in {delay:.2f}s...")
            time.sleep(delay)

# Usage
def flaky_api_call():
    response = requests.get("https://api.example.com/data")
    response.raise_for_status()
    return response.json()

data = retry_with_exponential_backoff(flaky_api_call)
```

### Retry with Circuit Breaker

**Combine both patterns**:
```python
def call_with_resilience(func):
    # First: Circuit breaker (fail-fast if service is down)
    try:
        # Second: Retry (transient failures)
        return retry_with_exponential_backoff(
            lambda: circuit_breaker.call(func),
            max_retries=3
        )
    except CircuitBreakerOpenError:
        # Circuit is open, use fallback
        return get_fallback_data()
```

**When to Retry**:
- ✅ Network timeouts
- ✅ 503 Service Unavailable
- ✅ 429 Too Many Requests
- ❌ 400 Bad Request (won't fix itself)
- ❌ 401 Unauthorized (won't fix itself)
- ❌ 404 Not Found (won't fix itself)

---

## Timeout Pattern

### Problem

**Without Timeout**:
```
Client → Service A → Service B (hangs)
                     ↓
            (waits indefinitely)
                     ↓
          (resources exhausted)
```

### Solution

**Set Aggressive Timeouts**:
```python
import requests

# Bad: No timeout
response = requests.get("https://api.example.com/data")

# Good: With timeout
response = requests.get(
    "https://api.example.com/data",
    timeout=2.0  # 2 seconds
)
```

**Layered Timeouts**:
```
User Request (10s timeout)
  → Service A (5s timeout)
      → Service B (2s timeout)
          → Service C (1s timeout)

Each layer has stricter timeout to ensure upper layers don't wait forever
```

**Implementation**:
```python
from functools import wraps
import signal

def timeout(seconds):
    def decorator(func):
        def _handle_timeout(signum, frame):
            raise TimeoutError(f"Function call timed out after {seconds}s")

        @wraps(func)
        def wrapper(*args, **kwargs):
            signal.signal(signal.SIGALRM, _handle_timeout)
            signal.alarm(seconds)
            try:
                result = func(*args, **kwargs)
            finally:
                signal.alarm(0)
            return result

        return wrapper
    return decorator

@timeout(5)
def slow_operation():
    # Will be interrupted after 5 seconds
    time.sleep(10)
```

---

## Bulkhead Pattern

### Concept

**Inspired by Ship Bulkheads**: Isolate failures to prevent cascading

**Problem**:
```
Thread Pool (100 threads):
- 95 threads: Blocked calling slow Service B
- 5 threads: Available for Service A
→ Service B failure impacts all services
```

**Solution**: Separate thread pools per dependency

```
┌─────────────────────────┐
│   Application           │
│                         │
│ ┌──────────┐            │
│ │Thread    │            │
│ │Pool A    │→ Service A │
│ │(50)      │            │
│ └──────────┘            │
│                         │
│ ┌──────────┐            │
│ │Thread    │            │
│ │Pool B    │→ Service B │
│ │(30)      │            │
│ └──────────┘            │
│                         │
│ ┌──────────┐            │
│ │Thread    │            │
│ │Pool C    │→ Service C │
│ │(20)      │            │
│ └──────────┘            │
└─────────────────────────┘

If Service B is slow:
- Only Pool B is exhausted
- Pool A and Pool C still operational
```

**Implementation**:
```python
from concurrent.futures import ThreadPoolExecutor
import threading

class Bulkhead:
    def __init__(self, max_concurrent_calls=10):
        self.executor = ThreadPoolExecutor(max_workers=max_concurrent_calls)
        self.semaphore = threading.Semaphore(max_concurrent_calls)

    def call(self, func, *args, **kwargs):
        if not self.semaphore.acquire(blocking=False):
            raise BulkheadFullError("Bulkhead is full")

        try:
            future = self.executor.submit(func, *args, **kwargs)
            return future.result()
        finally:
            self.semaphore.release()

# Separate bulkheads per service
bulkhead_service_a = Bulkhead(max_concurrent_calls=50)
bulkhead_service_b = Bulkhead(max_concurrent_calls=30)

# Service B failure doesn't affect Service A
try:
    result_a = bulkhead_service_a.call(call_service_a)
    result_b = bulkhead_service_b.call(call_service_b)  # Might be slow/fail
except BulkheadFullError:
    # Shed load
    pass
```

---

## Rate Limiting / Throttling

### Token Bucket Algorithm

**Concept**: Bucket holds tokens, consume token per request

```
Bucket capacity: 100 tokens
Refill rate: 10 tokens/second

Request arrives:
- If token available: Process (remove 1 token)
- If no token: Reject (429 Too Many Requests)
```

**Implementation**:
```python
import time

class TokenBucket:
    def __init__(self, capacity, refill_rate):
        self.capacity = capacity
        self.tokens = capacity
        self.refill_rate = refill_rate  # tokens per second
        self.last_refill = time.time()

    def consume(self, tokens=1):
        self._refill()

        if self.tokens >= tokens:
            self.tokens -= tokens
            return True
        else:
            return False

    def _refill(self):
        now = time.time()
        elapsed = now - self.last_refill

        # Add tokens based on elapsed time
        tokens_to_add = elapsed * self.refill_rate
        self.tokens = min(self.capacity, self.tokens + tokens_to_add)
        self.last_refill = now

# Usage
rate_limiter = TokenBucket(capacity=100, refill_rate=10)

@app.route('/api/endpoint')
def api_endpoint():
    if not rate_limiter.consume():
        return {"error": "Rate limit exceeded"}, 429

    return process_request()
```

### Leaky Bucket Algorithm

**Concept**: Requests leak out at constant rate

```
Requests enter bucket (queue)
Process at fixed rate (10 req/sec)
If bucket full: Reject
```

### Sliding Window Counter

**Concept**: Count requests in sliding time window

```python
from collections import deque
import time

class SlidingWindowRateLimiter:
    def __init__(self, max_requests, window_size):
        self.max_requests = max_requests
        self.window_size = window_size  # seconds
        self.requests = deque()

    def is_allowed(self):
        now = time.time()

        # Remove old requests outside window
        while self.requests and self.requests[0] < now - self.window_size:
            self.requests.popleft()

        if len(self.requests) < self.max_requests:
            self.requests.append(now)
            return True

        return False

# Usage: 100 requests per minute
limiter = SlidingWindowRateLimiter(max_requests=100, window_size=60)

if limiter.is_allowed():
    process_request()
else:
    return "Rate limit exceeded", 429
```

---

## Fallback Pattern

### Strategies

**1. Default Value**:
```python
def get_user_preferences(user_id):
    try:
        return api.get_preferences(user_id)
    except:
        return {"theme": "light", "language": "en"}  # Defaults
```

**2. Cached Value**:
```python
def get_product_info(product_id):
    try:
        product = api.get_product(product_id)
        cache.set(f"product:{product_id}", product, ttl=3600)
        return product
    except:
        # Use cached data even if stale
        cached = cache.get(f"product:{product_id}")
        if cached:
            return cached
        raise
```

**3. Degraded Functionality**:
```python
def get_recommendations(user_id):
    try:
        # Personalized recommendations (ML model)
        return ml_service.get_recommendations(user_id)
    except:
        # Fallback: Popular items
        return db.query("SELECT * FROM products ORDER BY sales DESC LIMIT 10")
```

**4. Empty Response**:
```python
def get_ads(page):
    try:
        return ad_service.get_ads(page)
    except:
        # Ads are optional, return empty
        return []
```

---

## Interview Q&A

**Q: "Explain how you would make a microservices system resilient"**

**Answer**:
```
I'd implement multiple resilience patterns:

1. Circuit Breaker (Prevent Cascading Failures):
   - Wrap all inter-service calls
   - Configuration: 50% error rate over 10 requests → OPEN
   - Timeout: 30s before HALF-OPEN
   - Fail fast when circuit open

2. Retry with Exponential Backoff (Handle Transient Failures):
   - Max 3 retries
   - Delays: 1s, 2s, 4s
   - Add jitter (prevent thundering herd)
   - Only retry: 503, 429, timeouts

3. Timeouts (Prevent Resource Exhaustion):
   - Aggressive timeouts at each layer
   - Example: User request 10s → Service A 5s → Service B 2s
   - Use connection pool timeouts

4. Bulkhead (Isolate Failures):
   - Separate thread pools per dependency
   - Service A pool: 50 threads
   - Service B pool: 30 threads
   - If B is slow, doesn't affect A

5. Rate Limiting (Protect Services):
   - Token bucket: 1000 req/sec per service
   - Per-user limits: 100 req/min
   - Shed load gracefully (429 response)

6. Fallbacks (Graceful Degradation):
   - Cached data for read operations
   - Default values for non-critical features
   - Simplified experience when dependencies down

7. Health Checks (Early Detection):
   - Deep health checks (/health)
   - Check dependencies (DB, cache, APIs)
   - Remove unhealthy instances from load balancer

Implementation Example (Python with Resilience4j pattern):
```python
from circuit_breaker import CircuitBreaker
from retry import retry_with_backoff

breaker = CircuitBreaker(failure_threshold=5, timeout=30)
cache = RedisCache()

@retry_with_backoff(max_retries=3)
def get_user_data(user_id):
    try:
        # Circuit breaker + retry
        return breaker.call(
            lambda: user_service.get_user(user_id)
        )
    except CircuitBreakerOpenError:
        # Fallback to cache
        cached = cache.get(f"user:{user_id}")
        if cached:
            return cached
        # Ultimate fallback
        return {"id": user_id, "name": "Unknown"}
```

Monitoring:
- Track circuit breaker state changes
- Alert on high error rates
- Dashboard: Latency, error rate, circuit state

Result:
- Service failures isolated
- No cascading failures
- Graceful degradation
- Fast failure detection
```

---

## Key Takeaways

1. **Circuit Breaker**: Fail fast when downstream service is unhealthy
2. **Retry**: Handle transient failures with exponential backoff
3. **Timeout**: Prevent resource exhaustion from slow services
4. **Bulkhead**: Isolate failures to prevent cascade
5. **Fallback**: Graceful degradation when dependencies fail
6. **Rate Limiting**: Protect services from overload

---

## Further Reading

- "Release It!" - Michael Nygard (Patterns for resilient systems)
- Netflix Hystrix Documentation
- Resilience4j Documentation
- "Designing Data-Intensive Applications" - Martin Kleppmann
- AWS Well-Architected Framework: Reliability Pillar
- Google SRE Book: Handling Overload
