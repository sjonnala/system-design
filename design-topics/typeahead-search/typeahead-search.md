# Typeahead Search / Autocomplete - Complete System Design

> A comprehensive guide to designing a scalable typeahead/autocomplete system like Google Search or Amazon Product Search

## 📋 Table of Contents

1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [System Architecture](#system-architecture)
5. [Core Algorithm: Trie Data Structure](#core-algorithm-trie-data-structure)
6. [API Design](#api-design)
7. [Client-Side Optimizations](#client-side-optimizations)
8. [Caching Strategy](#caching-strategy)
9. [Data Pipeline](#data-pipeline)
10. [Scalability](#scalability)
11. [Trade-offs](#trade-offs)

---

## Problem Statement

Design a typeahead/autocomplete system that:
- Provides real-time search suggestions as user types
- Returns relevant suggestions with minimal latency (< 100ms)
- Handles millions of queries per second globally
- Updates suggestions based on search trends
- Supports multiple languages and personalization

**Examples:** Google Search, Amazon Product Search, YouTube Search, LinkedIn Search

---

## Requirements

### Functional Requirements

✅ **Must Have:**
1. Provide top 10 suggestions for user's partial query
2. Match prefix of query (user types "ipho" → suggest "iphone 15", "iphone 15 pro", etc.)
3. Rank suggestions by popularity/relevance
4. Support fast lookups (< 100ms)
5. Update suggestions based on trending queries

🎯 **Nice to Have:**
1. Personalized suggestions based on user history
2. Typo tolerance (fuzzy matching)
3. Multi-language support
4. Category-specific suggestions
5. Image previews with suggestions

### Non-Functional Requirements

- **Latency:** < 100ms API response time (p95)
- **User-Perceived Latency:** < 500ms (including debouncing)
- **Availability:** 99.9% (High availability)
- **Scalability:** Handle 100K-500K requests/second
- **Data Freshness:** Trending queries reflected within 1 hour

---

## Capacity Estimation

### Traffic

```
Daily Active Users: 500 million
Searches per user per day: 10
Total searches: 5 billion/day

Suggestion requests (with debouncing):
- ~3-4 API calls per search
- 15 billion requests/day
- ~173K requests/second average
- ~350K requests/second peak

Query logs (actual searches):
- 5 billion/day
- ~58K writes/second to Kafka
```

### Storage

```
Suggestions Database:
- 10 million popular queries (filter low frequency)
- ~100 bytes per entry
- Total: 1GB (with indexes: ~3GB)

Search Logs:
- 4TB/day (15B suggestion requests + 5B searches)
- 7 days in Kafka: 28TB
- 1 year in S3: ~500TB (compressed)

Trie Memory (per service node):
- 10M queries in Trie
- ~15GB RAM per node
```

### Bandwidth

```
Incoming: ~100MB/sec average, ~200MB/sec peak
Outgoing: ~175MB/sec average, ~350MB/sec peak
```

**📊 Detailed Calculations:** See [scale-estimation-guide.md](scale-estimation-guide.md)

---

## System Architecture

```
                          [Client]
                             |
                   (Debounce 300ms)
                             |
                             v
                      [CDN / API Gateway]
                             |
                             v
                   [Load Balancer (ALB)]
                             |
              +--------------+--------------+
              |              |              |
              v              v              v
       [Typeahead]    [Typeahead]    [Typeahead]
       [Service 1]    [Service 2]    [Service 3]
              |              |              |
       [Trie in RAM]  [Trie in RAM]  [Trie in RAM]
              |              |              |
              +------+-------+------+-------+
                     |              |
                     v              v
             [Redis Cluster]   [Kafka Stream]
                     |              |
                     v              v
         [PostgreSQL/ES]    [Spark Processing]
                                    |
                                    v
                              [Trie Builder]
```

### Components

1. **Client:** Debouncing, request cancellation, local cache
2. **Typeahead Service:** Go/Node.js service with in-memory Trie
3. **Redis Cache:** 95% cache hit ratio for popular queries
4. **Trie:** Prefix tree data structure for O(k) lookups
5. **Kafka:** Real-time search logs
6. **Spark:** Batch processing for query aggregation (hourly)
7. **Trie Builder:** Rebuilds Trie from updated data (hourly)

**🏗️ Detailed Architecture:** See [architecture.html](architecture.html)

---

## Core Algorithm: Trie Data Structure

### What is a Trie?

A Trie (prefix tree) is a tree data structure used for efficient string prefix matching.

```
Example Trie for: "cat", "cap", "car", "dog", "door"

                    root
                   /    \
                  c      d
                  |      |
                  a      o
                /|\      |\
               t p r     g r
             (cat)(cap)(car)(dog)(door)

Search "ca" → Traverse: root→c→a
           → Return all children: "cat", "cap", "car"
```

### Why Trie?

**Time Complexity:**
- Insert: O(k) where k = length of word
- Search: O(k) where k = length of prefix
- **Much faster than database queries!**

**Database Query (Slow):**
```sql
SELECT * FROM suggestions
WHERE query LIKE 'ipho%'
ORDER BY frequency DESC
LIMIT 10;
-- Time: 10-50ms even with index
```

**Trie Lookup (Fast):**
```go
suggestions := trie.FindByPrefix("ipho")
// Time: < 1ms (in-memory)
```

### Trie Implementation

```go
type TrieNode struct {
    children     map[rune]*TrieNode
    isEndOfWord  bool
    frequency    int64
    suggestions  []string  // Pre-computed top 10
}

type Trie struct {
    root *TrieNode
}

// Insert word with frequency
func (t *Trie) Insert(word string, freq int64) {
    node := t.root
    for _, char := range word {
        if node.children[char] == nil {
            node.children[char] = &TrieNode{
                children: make(map[rune]*TrieNode),
            }
        }
        node = node.children[char]
    }
    node.isEndOfWord = true
    node.frequency = freq
}

// Find suggestions by prefix
func (t *Trie) FindByPrefix(prefix string) []string {
    node := t.root

    // Traverse to prefix node
    for _, char := range prefix {
        if node.children[char] == nil {
            return []string{} // No matches
        }
        node = node.children[char]
    }

    // Return pre-computed suggestions
    return node.suggestions
}

// Pre-compute top 10 suggestions at each node (offline)
func (t *Trie) PrecomputeSuggestions(node *TrieNode) {
    allSuggestions := collectAllSuggestions(node)
    sortByFrequency(allSuggestions)
    node.suggestions = allSuggestions[:10] // Top 10
}
```

### Optimization: Pre-compute Suggestions

Instead of collecting and sorting suggestions at query time, **pre-compute and store** top 10 suggestions at each Trie node during build time.

**Benefit:**
- Query time: O(k) to traverse + O(1) to return pre-computed list
- No runtime sorting needed
- **Total: < 1ms**

---

## API Design

### 1. Get Suggestions

```http
GET /api/v1/suggestions?q={query}&limit={N}&lang={language}

Query Parameters:
- q: User's partial query (required, min 2 chars)
- limit: Number of suggestions (default: 10, max: 20)
- lang: Language code (default: 'en')

Response: 200 OK
{
  "query": "ipho",
  "suggestions": [
    {
      "text": "iphone 15",
      "category": "electronics",
      "frequency": 5000000
    },
    {
      "text": "iphone 15 pro",
      "category": "electronics",
      "frequency": 3500000
    },
    {
      "text": "iphone 14",
      "category": "electronics",
      "frequency": 2500000
    },
    ...
  ],
  "latency_ms": 3
}

Response: 400 Bad Request
{
  "error": "invalid_query",
  "message": "Query must be at least 2 characters"
}
```

### 2. Log Search Event (Async)

```http
POST /api/v1/search-events
Content-Type: application/json

Request:
{
  "query": "iphone 15",
  "user_id": "uuid-123-456",
  "session_id": "session-abc",
  "timestamp": "2025-11-14T10:30:00Z",
  "result_clicked": true,
  "position": 1
}

Response: 202 Accepted
```

### Rate Limiting

```
Unauthenticated: 100 requests/minute per IP
Authenticated: 1,000 requests/minute per user
Premium: 10,000 requests/minute

Headers:
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 847
X-RateLimit-Reset: 1634567890
```

---

## Client-Side Optimizations

### 1. Debouncing

**Problem:** User types "iphone" → 6 characters → 6 API calls

**Solution:** Wait 300ms after user stops typing before sending request

```javascript
let debounceTimer;
function onSearchInput(query) {
    // Cancel previous timer
    clearTimeout(debounceTimer);

    // Set new timer
    debounceTimer = setTimeout(() => {
        fetchSuggestions(query);
    }, 300); // 300ms delay
}
```

**Impact:** 80-85% reduction in API calls

### 2. Request Cancellation

**Problem:** User types fast → multiple requests in flight → race condition

**Solution:** Cancel previous request when new character typed

```javascript
let controller = new AbortController();

function fetchSuggestions(query) {
    // Cancel previous request
    controller.abort();

    // Create new controller
    controller = new AbortController();

    fetch(`/api/suggestions?q=${query}`, {
        signal: controller.signal
    })
    .then(response => response.json())
    .then(suggestions => displaySuggestions(suggestions))
    .catch(error => {
        if (error.name === 'AbortError') {
            // Request was cancelled, ignore
        }
    });
}
```

### 3. Local Caching (Browser Storage)

```javascript
function getSuggestions(query) {
    // Check localStorage first
    const cached = localStorage.getItem(`suggestions:${query}`);
    if (cached) {
        const data = JSON.parse(cached);
        if (Date.now() - data.timestamp < 3600000) { // 1 hour
            return data.suggestions;
        }
    }

    // Fetch from API
    return fetch(`/api/suggestions?q=${query}`)
        .then(response => response.json())
        .then(suggestions => {
            // Store in localStorage
            localStorage.setItem(`suggestions:${query}`, JSON.stringify({
                suggestions: suggestions,
                timestamp: Date.now()
            }));
            return suggestions;
        });
}
```

**Impact:** 50% of queries served from local cache (no API call)

### 4. Keyboard Navigation

```javascript
// Arrow keys to navigate suggestions
// Enter to select
// Escape to close

let selectedIndex = -1;

document.addEventListener('keydown', (e) => {
    if (e.key === 'ArrowDown') {
        selectedIndex = Math.min(selectedIndex + 1, suggestions.length - 1);
        highlightSuggestion(selectedIndex);
    } else if (e.key === 'ArrowUp') {
        selectedIndex = Math.max(selectedIndex - 1, -1);
        highlightSuggestion(selectedIndex);
    } else if (e.key === 'Enter') {
        if (selectedIndex >= 0) {
            selectSuggestion(suggestions[selectedIndex]);
        }
    } else if (e.key === 'Escape') {
        closeSuggestions();
    }
});
```

---

## Caching Strategy

### Multi-Level Caching

```
Level 1: Browser localStorage (50% hit rate)
          ↓ (miss)
Level 2: Redis Cache (95% hit rate of API calls)
          ↓ (miss)
Level 3: In-Memory Trie (100% hit rate of cache misses)
```

### Redis Cache

**Strategy:** Cache-aside pattern

```python
def get_suggestions(query):
    # 1. Check Redis
    cache_key = f"suggestions:{query}"
    cached = redis.get(cache_key)

    if cached:
        return json.loads(cached)  # Cache hit

    # 2. Cache miss - query Trie
    suggestions = trie.find_by_prefix(query)

    # 3. Store in Redis (1 hour TTL)
    redis.setex(cache_key, 3600, json.dumps(suggestions))

    return suggestions
```

**Cache Configuration:**
```
Key Pattern: suggestions:{query}
Value: JSON array of suggestions
TTL: 3600 seconds (1 hour)
Eviction: LRU (Least Recently Used)
Memory: ~3GB per Redis node
Cluster: 6 nodes (3 masters + 3 replicas)
```

**Cache Metrics:**
- Hit Ratio: 95%
- Miss Penalty: < 5ms (Trie lookup)
- Cache Size: 2M most popular queries

---

## Data Pipeline

### Real-Time Logging

```
User searches → Typeahead Service → Kafka Topic "search-events"

Message format:
{
  "query": "iphone 15",
  "user_id": "uuid",
  "timestamp": 1699972800,
  "result_clicked": true,
  "position": 1,
  "session_id": "session-abc"
}
```

### Hourly Batch Processing

```
1. Spark Job (runs every hour at :00)
   ├─ Read last hour of Kafka logs
   ├─ Aggregate: GROUP BY query, COUNT(*)
   ├─ Calculate relevance score
   └─ Filter: frequency > 10

2. Update Suggestions Database
   ├─ UPSERT into suggestions table
   └─ Update query frequencies

3. Rebuild Trie
   ├─ Read top 10M queries from database
   ├─ Build Trie in memory
   ├─ Pre-compute top 10 suggestions per node
   └─ Serialize Trie to binary format

4. Deploy New Trie
   ├─ Upload to S3
   ├─ Rolling update to service nodes
   │  └─ Each node downloads and hot-reloads
   └─ Zero downtime (old Trie serves during update)

Total time: ~30-40 minutes
```

### Why Hourly (Not Real-Time)?

**Pros of Hourly:**
- Simpler architecture
- Lower cost (batch processing)
- Sufficient for most use cases
- Prevents spam/abuse from affecting suggestions

**Cons:**
- Trending queries take up to 1 hour to appear
- Manual intervention needed for critical trends

**For Real-Time (if needed):**
- Use streaming (Kafka + Flink)
- Incremental Trie updates
- 10x higher complexity and cost

---

## Scalability

### Horizontal Scaling

**Stateless Services:**
- Each Typeahead Service node has full Trie in memory
- Load balancer distributes traffic across all nodes
- Easy to scale: add/remove nodes dynamically
- Auto-scaling: 40-80 nodes based on traffic

**Redis Cluster:**
- Sharded across 3 master nodes
- 3 replica nodes for failover
- Consistent hashing for key distribution

**Kafka Cluster:**
- 5 broker nodes
- Replication factor: 3
- Can handle 100MB/sec throughput

### Geographic Distribution

```
US Region:
  - 20 Typeahead Service nodes
  - Redis Cluster (local)
  - Same Trie (replicated)
  - Serves US traffic

EU Region:
  - 10 Typeahead Service nodes
  - Redis Cluster (local)
  - Same or localized Trie
  - Serves EU traffic

Asia Region:
  - 15 Typeahead Service nodes
  - Redis Cluster (local)
  - Localized Trie (Chinese, Japanese queries)
  - Serves Asia traffic
```

**Benefits:**
- Lower latency (serve from nearest region)
- Data sovereignty compliance
- Fault isolation

### Trie Memory Optimization

**Problem:** 1 billion unique queries → 500GB Trie (doesn't fit in RAM)

**Solution:** Filter and prioritize

```
All queries: 1 billion
├─ Filter: frequency > 100 → 50 million queries
├─ Filter: frequency > 1000 → 10 million queries
└─ Store top 10M in Trie → 15GB RAM ✓

Long-tail queries (rare, low frequency):
└─ Serve from Elasticsearch fallback (slower, acceptable)
```

### Database Sharding (if needed)

```
Partition by: First character of query
- Shard 'a': queries starting with 'a'
- Shard 'b': queries starting with 'b'
- ...
- Shard '0': queries starting with numbers

Or: Hash-based sharding
- shard_id = hash(query) % num_shards
```

**🔄 Detailed Flows:** See [flow-diagram.html](flow-diagram.html)

---

## Trade-offs

### 1. Trie vs Database Queries

| Trie (Chosen) | Database |
|---------------|----------|
| ✅ < 1ms lookups | ❌ 10-50ms queries |
| ✅ O(k) complexity | ❌ Full table scan or index |
| ❌ Memory intensive (15GB) | ✅ Disk-based (cheap) |
| ❌ Reload needed for updates | ✅ Real-time updates |

**Decision:** Trie for low latency, batch updates hourly

### 2. Hourly vs Real-Time Updates

| Hourly (Chosen) | Real-Time |
|-----------------|-----------|
| ✅ Simpler architecture | ❌ Complex streaming pipeline |
| ✅ Lower cost | ❌ 10x higher cost |
| ❌ 1-hour lag for trending | ✅ Immediate trending |
| ✅ Prevents spam | ❌ Vulnerable to spam |

**Decision:** Hourly for most use cases, real-time if critical

### 3. Pre-compute vs Runtime Sorting

| Pre-compute (Chosen) | Runtime Sorting |
|----------------------|-----------------|
| ✅ < 1ms response | ❌ 5-10ms sorting |
| ✅ Consistent latency | ❌ Variable latency |
| ❌ More memory (store top 10) | ✅ Less memory |
| ❌ Update overhead | ✅ Always fresh ranking |

**Decision:** Pre-compute for predictable performance

### 4. Client Debouncing

| With Debouncing (Chosen) | Without |
|--------------------------|---------|
| ✅ 85% fewer API calls | ❌ API call per character |
| ✅ Lower server load | ❌ High server load |
| ❌ 300ms delay | ✅ Instant (but laggy UI) |
| ✅ Better UX (less flickering) | ❌ Flickering dropdown |

**Decision:** 300ms debounce (good UX, lower cost)

---

## Performance Optimization Summary

| Optimization | Impact |
|------------- |--------|
| Client Debouncing (300ms) | 85% reduction in API calls |
| Browser localStorage Cache | 50% of queries need no API call |
| Request Cancellation | Prevents race conditions, reduces load |
| Redis Caching | 95% cache hit, < 5ms response |
| In-Memory Trie | O(k) lookup, < 1ms |
| Pre-computed Suggestions | No runtime sorting, consistent latency |

**Overall User-Perceived Latency:** ~320ms (mostly debounce delay)
**API Response Time (p95):** < 10ms

---

## Monitoring & Observability

### Key Metrics

**Application:**
- Request rate (req/sec)
- Latency (p50, p95, p99)
- Error rate (%)
- Cache hit ratio (%)

**Infrastructure:**
- Trie memory usage (GB)
- Redis memory usage (%)
- Kafka lag (messages)
- CPU/Memory per node

**Business:**
- Most popular queries
- Trending queries (hourly)
- Zero-result queries (improve coverage)
- Click-through rate per position

### Alerts

```
Critical:
- API Latency p95 > 100ms
- Error rate > 1%
- Cache hit ratio < 80%
- Trie memory > 90%

Warning:
- API Latency p95 > 50ms
- Cache hit ratio < 90%
- Kafka lag > 1000 messages
```

---

## Interview Tips

1. **Start with Trie:** Core data structure, explain why (O(k) vs database)
2. **Explain Debouncing:** Critical for reducing API load
3. **Multi-Level Caching:** Browser → Redis → Trie
4. **Pre-compute:** Show understanding of time/space trade-offs
5. **Batch Updates:** Hourly is sufficient, explain trade-offs
6. **Numbers Matter:** 173K QPS, 15GB Trie, 95% cache hit ratio
7. **Client Optimizations:** Debouncing, cancellation, local cache

## Common Follow-up Questions

**Q: How do you handle typos? (fuzzy matching)**
```
A: Several approaches:
1. Edit distance (Levenshtein): Expensive at query time
2. Phonetic algorithms (Soundex, Metaphone): Pre-compute variants
3. N-gram indexing: Store 2-3 character combinations
4. Machine Learning: "Did you mean?" suggestions

For typeahead, simple approach:
- Store common misspellings in Trie (ipone → iphone)
- Post-processing: If no results, try fuzzy match (fallback)
```

**Q: How do you personalize suggestions?**
```
A: Hybrid approach:
1. Global Trie: Base suggestions (same for all users)
2. User history: Last 10 searches cached per user
3. Merge at query time:
   - 70% from global Trie (popular queries)
   - 30% from user history (personalized)
4. Re-rank based on user's past clicks

Trade-off: Adds 2-3ms latency but better relevance
```

**Q: How do you handle real-time trending queries (viral events)?**
```
A: Two-tier approach:
1. Normal: Hourly batch updates (covers 99% of cases)
2. Trending: Manual trigger for critical events
   - Monitor social media APIs
   - Detect sudden query spikes
   - Manually add to Trie (takes 10 minutes)
   - Examples: Breaking news, product launches

Alternative: Real-time pipeline (10x cost)
- Kafka + Flink streaming
- Incremental Trie updates
- < 1 minute latency for trends
```

---

## Further Reading

- [System Architecture](architecture.html) - Component details
- [Flow Diagrams](flow-diagram.html) - Request/response flows
- [Scale Estimation Guide](scale-estimation-guide.md) - Capacity planning

## Key Takeaways

1. **Trie is Essential:** O(k) lookups enable < 1ms response times
2. **Client Optimizations:** Debouncing reduces API load by 85%
3. **Multi-Level Caching:** 50% browser cache, 95% Redis cache
4. **Pre-compute Suggestions:** Store top 10 at each Trie node
5. **Batch Updates:** Hourly updates sufficient for most use cases
6. **Memory-Efficient:** 10M queries fit in 15GB RAM per node
7. **Horizontal Scaling:** Stateless services, easy to scale

---

**Designed by:** System Design Reference Guide
**Last Updated:** November 14, 2025
**Difficulty:** Intermediate-Advanced
**Time to Complete:** 45-60 minutes in interview
