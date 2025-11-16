# Performance Optimization

Techniques and strategies for making systems faster and more efficient.

## Contents
- [Performance Fundamentals](#performance-fundamentals)
- [Database Optimization](#database-optimization)
- [Caching Strategies](#caching-strategies)
- [Network Optimization](#network-optimization)
- [Code-Level Optimization](#code-level-optimization)
- [Frontend Performance](#frontend-performance)
- [Monitoring and Profiling](#monitoring-and-profiling)

## Performance Fundamentals

### Key Metrics

#### 1. Latency
Time to complete a single operation.

```
Database query: 10ms
API call: 50ms
Page load: 2000ms
```

#### 2. Throughput
Number of operations per unit time.

```
1000 requests/second
100 transactions/second
10 GB/second
```

#### 3. Response Time Percentiles

```
p50 (median): 100ms    (50% of requests)
p95: 250ms             (95% of requests)
p99: 500ms             (99% of requests)
p99.9: 1000ms          (99.9% of requests)
```

**Why percentiles matter:**
```
Average: 100ms (looks good!)
But p99: 5000ms (terrible user experience for 1% of users)
```

#### 4. Resource Utilization

```
CPU: 60% average, 90% peak
Memory: 8GB / 16GB (50%)
Disk I/O: 100 IOPS / 1000 IOPS
Network: 100 Mbps / 1 Gbps
```

### Performance Budgets

Set targets and measure against them.

```
Performance Budget:
- Page load: < 2 seconds
- Time to Interactive: < 3 seconds
- API response: < 100ms (p95)
- Database query: < 50ms (p95)
```

### The Performance Optimization Process

1. **Measure**: Establish baseline metrics
2. **Identify**: Find bottlenecks (profiling)
3. **Optimize**: Apply targeted improvements
4. **Validate**: Measure again, verify improvement
5. **Repeat**: Continuous improvement

## Database Optimization

### 1. Indexing

**Before:**
```sql
-- Full table scan: 5000ms
SELECT * FROM users WHERE email = 'john@example.com';
```

**After:**
```sql
-- Create index
CREATE INDEX idx_users_email ON users(email);

-- Index scan: 5ms
SELECT * FROM users WHERE email = 'john@example.com';

1000x faster!
```

#### Common Index Patterns

```sql
-- Single column index
CREATE INDEX idx_users_email ON users(email);

-- Composite index (order matters!)
CREATE INDEX idx_users_name_age ON users(last_name, first_name, age);

-- Covering index (includes all queried columns)
CREATE INDEX idx_users_covering ON users(email) INCLUDE (name, created_at);

-- Partial index (only index subset)
CREATE INDEX idx_active_users ON users(email) WHERE active = true;
```

#### Index Best Practices

```
✓ Index foreign keys
✓ Index columns in WHERE clauses
✓ Index columns in ORDER BY
✓ Use composite indexes for multi-column queries

✗ Don't over-index (slows writes)
✗ Don't index low-cardinality columns (gender, boolean)
✗ Don't index small tables
```

### 2. Query Optimization

#### Use EXPLAIN to Analyze

```sql
EXPLAIN ANALYZE
SELECT u.name, COUNT(o.id)
FROM users u
LEFT JOIN orders o ON u.id = o.user_id
WHERE u.created_at > '2024-01-01'
GROUP BY u.id;

-- Look for:
-- - Sequential Scans (bad, should use index)
-- - Index Scans (good)
-- - High cost estimates
```

#### Avoid N+1 Queries

**Bad: N+1 queries**
```python
# 1 query to get users
users = User.query.all()  # 1 query

# N queries to get orders for each user
for user in users:
    orders = user.orders.all()  # N queries!

Total: 1 + N queries (N=1000 → 1001 queries!)
```

**Good: Single query with join**
```python
users = User.query.options(
    joinedload(User.orders)
).all()

Total: 1 query (1000x better!)
```

#### Use Appropriate JOIN Types

```sql
-- INNER JOIN: Only matching rows
SELECT * FROM users u
INNER JOIN orders o ON u.id = o.user_id;

-- LEFT JOIN: All users, even without orders
SELECT * FROM users u
LEFT JOIN orders o ON u.id = o.user_id;

-- Avoid if not needed (more expensive)
```

#### Limit Results

```sql
-- Bad: fetch everything
SELECT * FROM users;  -- 1M rows, 500MB

-- Good: paginate
SELECT * FROM users
ORDER BY id
LIMIT 20 OFFSET 0;  -- Only 20 rows
```

### 3. Database Connection Pooling

**Without pooling:**
```
Each request:
1. Open TCP connection (100ms)
2. Authenticate (50ms)
3. Execute query (10ms)
4. Close connection (50ms)

Total: 210ms (mostly overhead!)
```

**With pooling:**
```
Connection pool (10 connections):
1. Reuse existing connection (0ms)
2. Execute query (10ms)
3. Return to pool (0ms)

Total: 10ms (20x faster!)
```

**Implementation:**
```python
from sqlalchemy import create_engine

engine = create_engine(
    'postgresql://localhost/mydb',
    pool_size=10,           # Number of connections
    max_overflow=20,        # Extra connections if pool exhausted
    pool_pre_ping=True,     # Check connection health
    pool_recycle=3600       # Recycle connections every hour
)
```

### 4. Database Replication

**Read Replicas:**
```
Primary (writes):
  ↓
  ├→ Replica 1 (reads)
  ├→ Replica 2 (reads)
  └→ Replica 3 (reads)

Write traffic: 10%  → Primary
Read traffic: 90%   → Replicas (load balanced)

Reduces primary load by 90%!
```

**Implementation:**
```python
class DatabaseRouter:
    def db_for_read(self, model):
        return random.choice(['replica1', 'replica2', 'replica3'])

    def db_for_write(self, model):
        return 'primary'
```

### 5. Database Sharding

Partition data across multiple databases.

**Horizontal Sharding (by user ID):**
```
Shard 1: user_id 0-1000000     → DB1
Shard 2: user_id 1000001-2000000 → DB2
Shard 3: user_id 2000001-3000000 → DB3

Each DB handles 1/3 of data and traffic
```

**Implementation:**
```python
def get_shard(user_id):
    shard_num = user_id % NUM_SHARDS
    return f'shard_{shard_num}'

# Query
db = get_shard(user_id)
user = db.query(User).get(user_id)
```

## Caching Strategies

See [Caching Strategies](caching.md) for detailed coverage.

### Quick Reference

#### Cache Levels

```
1. Browser cache (HTML, CSS, JS, images)
2. CDN cache (static assets)
3. Application cache (Redis, Memcached)
4. Database query cache
5. Disk cache
```

#### When to Cache

```
✓ Expensive computations
✓ Frequently accessed data
✓ Slow external API calls
✓ Database query results

✗ User-specific data (or use user-keyed cache)
✗ Rapidly changing data
✗ Data that must be fresh
```

#### Example: Caching API Response

```python
import redis
import json

cache = redis.Redis()

def get_user_profile(user_id):
    # Check cache
    cached = cache.get(f'user:{user_id}')
    if cached:
        return json.loads(cached)

    # Cache miss: query database
    user = db.query(User).get(user_id)

    # Store in cache (TTL: 1 hour)
    cache.setex(
        f'user:{user_id}',
        3600,
        json.dumps(user)
    )

    return user
```

## Network Optimization

### 1. Reduce HTTP Requests

**Before: 50 requests**
```html
<link rel="stylesheet" href="style1.css">
<link rel="stylesheet" href="style2.css">
...
<script src="script1.js"></script>
<script src="script2.js"></script>
...
```

**After: 2 requests (bundling)**
```html
<link rel="stylesheet" href="bundle.css">
<script src="bundle.js"></script>
```

### 2. Compression

**Gzip/Brotli compression:**
```
Original HTML: 100 KB
Gzipped: 20 KB
Brotli: 18 KB

82% reduction!
```

**Server configuration:**
```nginx
# Nginx
gzip on;
gzip_types text/plain text/css application/json application/javascript;
gzip_min_length 1000;
gzip_comp_level 6;
```

### 3. HTTP/2 & HTTP/3

**HTTP/1.1 issues:**
```
- Max 6 connections per host
- Head-of-line blocking
- No header compression
```

**HTTP/2 benefits:**
```
- Multiplexing (many requests over 1 connection)
- Header compression
- Server push
```

**HTTP/3 benefits:**
```
- Based on QUIC (UDP)
- Faster connection setup
- Better mobile performance
```

### 4. Minification

**Before minification:**
```javascript
function calculateTotal(items) {
    let total = 0;
    for (let item of items) {
        total += item.price;
    }
    return total;
}
```

**After minification:**
```javascript
function calculateTotal(t){let e=0;for(let l of t)e+=l.price;return e}
```

```
Original: 150 bytes
Minified: 75 bytes
50% reduction!
```

### 5. Content Delivery Network (CDN)

See [CDN Architecture](cdn.md) for detailed coverage.

```
User in Tokyo → Tokyo edge (10ms) vs US origin (200ms)
95% faster!
```

## Code-Level Optimization

### 1. Algorithmic Complexity

**Bad: O(n²)**
```python
def find_duplicates(arr):
    duplicates = []
    for i in range(len(arr)):
        for j in range(i+1, len(arr)):
            if arr[i] == arr[j]:
                duplicates.append(arr[i])
    return duplicates

# n=10000: ~50 million comparisons
```

**Good: O(n)**
```python
def find_duplicates(arr):
    seen = set()
    duplicates = set()
    for item in arr:
        if item in seen:
            duplicates.add(item)
        seen.add(item)
    return list(duplicates)

# n=10000: 10000 operations (5000x faster!)
```

### 2. Lazy Loading

Load data only when needed.

```python
# Bad: Load everything upfront
class User:
    def __init__(self, id):
        self.id = id
        self.profile = load_profile(id)  # Expensive!
        self.orders = load_orders(id)    # Expensive!
        self.friends = load_friends(id)  # Expensive!

# Good: Load on demand
class User:
    def __init__(self, id):
        self.id = id
        self._profile = None
        self._orders = None

    @property
    def profile(self):
        if self._profile is None:
            self._profile = load_profile(self.id)
        return self._profile

    @property
    def orders(self):
        if self._orders is None:
            self._orders = load_orders(self.id)
        return self._orders
```

### 3. Async/Parallel Processing

**Sequential (slow):**
```python
def process_users(user_ids):
    results = []
    for user_id in user_ids:
        result = fetch_user_data(user_id)  # 100ms each
        results.append(result)
    return results

# 100 users × 100ms = 10 seconds
```

**Parallel (fast):**
```python
import asyncio

async def process_users(user_ids):
    tasks = [fetch_user_data(user_id) for user_id in user_ids]
    results = await asyncio.gather(*tasks)
    return results

# 100 users in parallel = 100ms total (100x faster!)
```

### 4. Batch Operations

**Bad: One at a time**
```python
for user_id in user_ids:
    db.execute("INSERT INTO logs VALUES (?)", (user_id,))

# 1000 users = 1000 database round trips
```

**Good: Batch insert**
```python
values = [(user_id,) for user_id in user_ids]
db.executemany("INSERT INTO logs VALUES (?)", values)

# 1000 users = 1 database round trip (1000x faster!)
```

### 5. Object Pooling

Reuse expensive objects.

```python
# Bad: Create new connection each time
def process_request():
    db = create_database_connection()  # Expensive!
    result = db.query("SELECT ...")
    db.close()
    return result

# Good: Connection pool
from sqlalchemy import create_engine

engine = create_engine('postgresql://...', pool_size=10)

def process_request():
    with engine.connect() as conn:  # Reuse from pool
        result = conn.execute("SELECT ...")
        return result
```

## Frontend Performance

### 1. Critical Rendering Path

Optimize the path from HTML to pixels.

```html
<!-- Bad: Blocking CSS/JS -->
<head>
    <link rel="stylesheet" href="styles.css">  <!-- Blocks rendering -->
    <script src="app.js"></script>              <!-- Blocks parsing -->
</head>

<!-- Good: Optimize loading -->
<head>
    <!-- Critical CSS inline -->
    <style>/* Critical above-fold styles */</style>

    <!-- Non-critical CSS async -->
    <link rel="preload" href="styles.css" as="style" onload="this.rel='stylesheet'">

    <!-- JS deferred -->
    <script src="app.js" defer></script>
</head>
```

### 2. Image Optimization

```html
<!-- Bad: Large unoptimized image -->
<img src="photo.jpg">  <!-- 5MB, 4000x3000 -->

<!-- Good: Optimized, responsive -->
<img
  src="photo-800.webp"
  srcset="
    photo-400.webp 400w,
    photo-800.webp 800w,
    photo-1200.webp 1200w
  "
  sizes="(max-width: 600px) 400px, 800px"
  loading="lazy"
  alt="Description"
>
<!-- 50KB, right size for viewport, modern format, lazy loaded -->
```

**Optimization checklist:**
```
✓ Use WebP/AVIF format (smaller)
✓ Compress images (80-85% quality)
✓ Serve responsive sizes
✓ Lazy load below-the-fold images
✓ Use CDN
```

### 3. Code Splitting

Load only needed code.

```javascript
// Bad: Load everything
import { everything } from 'huge-library';

// Good: Dynamic import
button.addEventListener('click', async () => {
  const module = await import('./feature.js');
  module.doSomething();
});

// Webpack/Vite will create separate bundle
```

### 4. Virtual Scrolling

Render only visible items in long lists.

```javascript
// Bad: Render 10,000 items
{items.map(item => <Item key={item.id} {...item} />)}

// Good: Virtual scrolling (render ~20 visible items)
import { FixedSizeList } from 'react-window';

<FixedSizeList
  height={600}
  itemCount={10000}
  itemSize={50}
>
  {({ index, style }) => (
    <div style={style}>
      <Item {...items[index]} />
    </div>
  )}
</FixedSizeList>

// 500x fewer DOM nodes!
```

### 5. Web Workers

Offload heavy computation from main thread.

```javascript
// Heavy computation blocking UI
function processData(data) {
  // Complex calculations (freezes UI!)
  return result;
}

// Move to Web Worker
// worker.js
self.onmessage = (e) => {
  const result = processData(e.data);
  self.postMessage(result);
};

// main.js
const worker = new Worker('worker.js');
worker.postMessage(data);
worker.onmessage = (e) => {
  updateUI(e.data);  // UI stays responsive!
};
```

## Monitoring and Profiling

### 1. Application Performance Monitoring (APM)

Track real-world performance.

**Tools:**
- New Relic
- Datadog APM
- Dynatrace
- Elastic APM

**Metrics to track:**
```
- Request rate
- Response time (p50, p95, p99)
- Error rate
- Apdex score
- Database query time
- External service latency
```

### 2. Database Profiling

**PostgreSQL:**
```sql
-- Enable slow query log
ALTER DATABASE mydb SET log_min_duration_statement = 100;

-- Find slow queries
SELECT query, mean_exec_time, calls
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

**MySQL:**
```sql
-- Enable slow query log
SET GLOBAL slow_query_log = 1;
SET GLOBAL long_query_time = 0.1;

-- Analyze
SELECT * FROM mysql.slow_log
ORDER BY query_time DESC;
```

### 3. Code Profiling

**Python:**
```python
import cProfile
import pstats

# Profile function
profiler = cProfile.Profile()
profiler.enable()

expensive_function()

profiler.disable()
stats = pstats.Stats(profiler)
stats.sort_stats('cumulative')
stats.print_stats(10)  # Top 10 slowest functions
```

**Node.js:**
```bash
# Built-in profiler
node --prof app.js

# Process profiling data
node --prof-process isolate-*.log > processed.txt
```

### 4. Frontend Profiling

**Chrome DevTools:**
```
1. Performance tab → Record
2. Perform actions
3. Stop recording
4. Analyze:
   - Main thread activity
   - Network waterfall
   - Frame rate
   - Memory usage
```

**Lighthouse:**
```bash
# CLI
npm install -g lighthouse
lighthouse https://example.com

# Metrics:
- First Contentful Paint
- Largest Contentful Paint
- Time to Interactive
- Total Blocking Time
- Cumulative Layout Shift
```

### 5. Load Testing

Simulate traffic to find bottlenecks.

**Tools:**
- Apache JMeter
- k6
- Gatling
- Locust

**Example: k6**
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 },   // Ramp up
    { duration: '5m', target: 100 },   // Steady
    { duration: '2m', target: 200 },   // Spike
    { duration: '5m', target: 200 },   // Steady
    { duration: '2m', target: 0 },     // Ramp down
  ],
};

export default function () {
  let res = http.get('https://api.example.com/users');
  check(res, {
    'status is 200': (r) => r.status === 200,
    'response time < 500ms': (r) => r.timings.duration < 500,
  });
  sleep(1);
}
```

## Performance Optimization Checklist

### Database
- [ ] Add indexes on frequently queried columns
- [ ] Optimize slow queries (use EXPLAIN)
- [ ] Implement connection pooling
- [ ] Set up read replicas
- [ ] Use database caching
- [ ] Avoid N+1 queries

### Caching
- [ ] Cache expensive computations
- [ ] Cache database queries (Redis/Memcached)
- [ ] Use CDN for static assets
- [ ] Implement browser caching
- [ ] Add HTTP cache headers

### Network
- [ ] Enable Gzip/Brotli compression
- [ ] Minimize HTTP requests
- [ ] Use HTTP/2 or HTTP/3
- [ ] Implement CDN
- [ ] Optimize images (WebP, lazy load)

### Code
- [ ] Profile and optimize hot paths
- [ ] Use efficient algorithms (O(n) vs O(n²))
- [ ] Implement async/parallel processing
- [ ] Batch database operations
- [ ] Lazy load resources

### Frontend
- [ ] Minify and bundle assets
- [ ] Code splitting (dynamic imports)
- [ ] Optimize critical rendering path
- [ ] Virtual scrolling for long lists
- [ ] Web Workers for heavy computation
- [ ] Lazy load images and components

### Monitoring
- [ ] Set up APM (New Relic, Datadog)
- [ ] Monitor database performance
- [ ] Track error rates
- [ ] Set up alerts for anomalies
- [ ] Regular load testing

## Common Performance Pitfalls

### 1. Premature Optimization

```
"Premature optimization is the root of all evil" - Donald Knuth

Wrong approach:
1. Optimize everything upfront
2. Complex code, hard to maintain
3. Waste time on non-bottlenecks

Right approach:
1. Build working solution
2. Measure performance
3. Optimize bottlenecks
```

### 2. Not Measuring

```
"If you can't measure it, you can't improve it"

Wrong: Guess at bottlenecks
Right: Use profiling tools to identify actual bottlenecks
```

### 3. Optimizing the Wrong Thing

```
Spending 2 weeks optimizing a function that runs once per day
vs
Ignoring a database query that runs 1000 times/second

Focus on high-impact optimizations!
```

### 4. Over-Caching

```
Problem: Cache everything, cache becomes stale
Solution: Cache strategically, set appropriate TTLs
```

### 5. Ignoring the Frontend

```
Backend API: 10ms response time
Frontend: 5 second load time

Optimize the frontend too!
```

## Interview Tips

When discussing performance optimization:

1. **Ask about current metrics**: "What's the baseline? What are we optimizing for?"
2. **Identify bottlenecks**: "Let's measure first before optimizing"
3. **Discuss tradeoffs**: "Caching improves speed but adds complexity"
4. **Think holistically**: Database, network, code, frontend
5. **Real-world examples**: "At scale, indexing reduced query time from 5s to 50ms"

## Key Takeaways

1. Measure before optimizing (avoid premature optimization)
2. Focus on high-impact bottlenecks
3. Database optimization (indexing, query optimization) often yields biggest gains
4. Caching at multiple levels (browser, CDN, app, database)
5. Network optimization (compression, CDN, HTTP/2)
6. Monitor continuously and iterate

## Further Reading

- [Caching Strategies](caching.md) - Detailed caching patterns
- [CDN Architecture](cdn.md) - Global content delivery
- [Load Balancing](load-balancing.md) - Distributing traffic
- [Scaling Strategies](scaling-strategies.md) - Horizontal and vertical scaling
