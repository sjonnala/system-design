
#  Content Delivery Network (CDN) Deep Dive

## Contents

- [Content Delivery Network (CDN) Deep Dive](#content-delivery-network-cdn-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Routing & Anycast DNS (How Traffic Finds Nearest Edge)](#2-routing--anycast-dns-how-traffic-finds-nearest-edge)
    - [Origin Shield Pattern (Cost Optimization)](#3-origin-shield-pattern-cost-optimization)
    - [Dynamic Content Acceleration (Beyond Static Caching)](#4-dynamic-content-acceleration-beyond-static-caching)
    - [Failure Modes & Resilience Patterns](#5-failure-modes--resilience-patterns)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [API Design (RADIO: API)](#3-api-design-radio-api)
        - [Data Model (RADIO: Data-Model)](#4-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#5-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#6-deep-dives-radio-optimize)
    - [MIND MAP: CDN CONCEPTS](#mind-map-cdn-concepts)

## Core Mental Model

```text
Latency = Propagation Delay + Transmission Delay + Processing Delay + Queuing Delay

Where Propagation Delay ∝ Distance
```


**Three primary strategies:**
```
┌─────────────────────────────────────────────┐
│ TTL (Time To Live)                          │
│ ├─ Push: Origin pushes invalidation         │
│ ├─ Pull: Edge polls for updates             │
│ └─ Hybrid: Event-driven + TTL fallback      │
└─────────────────────────────────────────────┘
```


**Real World Patterns:**
```java
// Domain Model: Content Invalidation
public class EdgeCacheStrategy {
    
    @Value("${cache.ttl.static:86400}")  // 24 hours for static
    private long staticTTL;
    
    @Value("${cache.ttl.dynamic:60}")    // 1 min for dynamic
    private long dynamicTTL;
    
    public CacheDecision decide(Content content) {
        if (content.isPersonalized()) {
            return CacheDecision.NO_CACHE; // Never cache user-specific
        }
        
        if (content.hasHighChurnRate()) {
            // Use short TTL + streaming invalidation via Kafka
            return new CacheDecision(
                CacheMode.EDGE,
                Duration.seconds(dynamicTTL),
                InvalidationMode.STREAMING
            );
        }
        
        // Static assets: aggressive caching
        return new CacheDecision(
            CacheMode.EDGE_AND_BROWSER,
            Duration.days(1),
            InvalidationMode.VERSION_BASED
        );
    }
}
```

**Key insight**: Use **content versioning** (`/assets/v123/style.css`) to avoid invalidation entirely for immutable content.

---

### 2. **Routing & Anycast DNS** (How Traffic Finds Nearest Edge)

🎓 **PROFESSOR**: This involves **three layers of routing decisions**:
```
Layer 1: DNS Resolution (Anycast/GeoDNS)
         ↓
Layer 2: BGP Routing (Network layer)
         ↓
Layer 3: Application Load Balancing (Edge POP selection)
```

#### Anycast: Multiple servers share same IP. BGP routes to topologically nearest.

```python
# Simplified Edge Routing Logic: routing decision tree
class EdgeRouter:
    
    def route_request(self, request: HttpRequest) -> EdgePOP:
        """
        Real-world routing considers multiple factors
        """
        candidate_pops = self.get_geographic_candidates(request.client_ip)
        
        # Health check filter
        healthy_pops = [p for p in candidate_pops if p.health_score > 0.8]
        
        # Capacity filter (avoid overloaded POPs)
        available_pops = [p for p in healthy_pops 
                          if p.current_load < p.capacity * 0.85]
        
        if not available_pops:
            # Fallback: sacrifice latency for availability
            return self.get_origin_pop()
        
        # Cost optimization: prefer POPs with cached content
        cache_hits = [(p, p.cache_hit_rate(request.url)) 
                      for p in available_pops]
        
        # Weighted decision: 60% latency, 30% cache hit, 10% cost
        return self.weighted_selection(cache_hits, 
                                       weights=[0.6, 0.3, 0.1])
```

**Interview talking point**: "In production, we don't just route to nearest POP. We consider cache warmth, capacity, and cost. A cache miss at nearby POP might be slower than cache hit at farther POP."

---

### 3. **Origin Shield Pattern** (Cost Optimization)

🎓 **PROFESSOR**: This is a **hierarchical caching architecture**:
```
         [Origin Server]
                ↑
         [Origin Shield]  ← Single chokepoint
          ↙    ↓    ↘
    [Edge POP] [Edge POP] [Edge POP]
```

**Why?** Prevent **cache stampede** - when cache expires, don't let 1000 edge POPs hammer origin simultaneously.

🏗️ **ARCHITECT**: Real AWS CloudFront + S3 scenario:
```
Problem: Video file (2GB) cached at 200 edge locations
- Cache expires at same time
- 200 simultaneous requests hit S3 origin
- Cost: 200 * 2GB * $0.09/GB = $36 per cache refresh!

Solution: Origin Shield in us-east-1
- Only 1 request to S3 (costs $0.18)
- Shield serves all 200 edge POPs
- 200x cost reduction
```


## Domain Model:
```java
public class OriginShieldService {
    
    private final LoadingCache<String, Content> shieldCache;
    
    public Content getContent(String url) {
        return shieldCache.get(url, () -> {
            // Only ONE thread/POP fetches from origin
            // Others wait on this future
            return originService.fetch(url);
        });
    }
    
    // Coalesce concurrent requests for same content
    private final RequestCoalescer coalescer;
}
```

---

### 4. **Dynamic Content Acceleration** (Beyond Static Caching)

🎓 **PROFESSOR**: Not all edge computing is about caching! **Edge computing** includes:

1. **Protocol optimization**: HTTP/2, HTTP/3 (QUIC), TCP optimization
2. **Route optimization**: Better paths than public internet
3. **Computation**: Run code at edge (Lambda@Edge, Cloudflare Workers)

🏗️ **ARCHITECT**: Example - **API acceleration** pattern:
```
Client (Singapore) → Edge (Singapore) → Origin (Virginia)

Without CDN:
- RTT: 250ms
- TLS handshake: 3 RTTs = 750ms
- Request: 250ms
- Total: 1000ms

With CDN:
- Client → Edge: 5ms (TLS handshake: 15ms)
- Edge → Origin: Persistent connection (0ms handshake)
- Edge → Origin: Optimized backbone (150ms vs 250ms)
- Total: 170ms (6x improvement!)
```

## Code example - Edge compute for personalization:

```python
# Cloudflare Worker / Lambda@Edge
async def handle_request(request):
    """
    Run at edge: Combine cached content + personalized data
    """
    # Fetch static HTML from edge cache (fast)
    html_template = await cache.get(request.path)
    
    # Fetch user data from origin (slow, but small payload)
    user_data = await origin_api.get_user(request.cookies['user_id'])
    
    # Server-side render at edge
    personalized_html = render_template(html_template, user_data)
    
    return Response(personalized_html, headers={
        'Cache-Control': 'private, no-cache'  # Don't cache personalized
    })
```

---

### 5. **Failure Modes & Resilience Patterns**

🎓 **PROFESSOR**: Distributed systems fail in interesting ways. **Edge failure taxonomy**:
```
┌─────────────────────────────────────────────────┐
│ 1. Edge POP failure     → Route to next nearest │
│ 2. Origin failure       → Serve stale content   │
│ 3. Shield failure       → Direct to origin      │
│ 4. DNS failure          → Multi-CDN failover    │
│ 5. Cache corruption     → Bypass + purge        │
└─────────────────────────────────────────────────┘


## 🏗️ ARCHITECT: The circuit breaker pattern for origin protection:
```java
@Service
public class ResilientOriginService {
    
    @CircuitBreaker(name = "origin", 
                    fallbackMethod = "serveStaleContent")
    @Retry(name = "origin", maxAttempts = 3)
    @RateLimiter(name = "origin")
    public Content fetchFromOrigin(String url) {
        return originClient.get(url);
    }
    
    // Fallback: serve stale content rather than failing
    public Content serveStaleContent(String url, Exception ex) {
        logger.warn("Origin unhealthy, serving stale: {}", url);
        
        Content stale = cache.get(url);
        if (stale != null && stale.isWithinGracePeriod()) {
            stale.addHeader("Warning", "110 - Response is Stale");
            return stale;
        }
        
        throw new ServiceUnavailableException("Origin and cache unavailable");
    }
}
```

**Interview gold**: "I prefer serving stale content over failing requests. We call it *stale-while-revalidate*. It's better to show 5-minute-old stock prices than error page."

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

When asked to *"Design a CDN"*, use this structure:

### 1. Requirements Clarification** (RADIO: Requirements)
```
Functional:
- Static content delivery (images, videos, JS/CSS)
- Dynamic content acceleration
- Geographic distribution (which regions?)
- Content types (streaming vs download?)

Non-Functional:
- Scale: X requests/sec, Y GB bandwidth
- Latency: P95 < 100ms
- Availability: 99.99%
- Cost considerations
```

### 2. Capacity Estimation** (RADIO: Scale)
```
Traffic:
- 1B requests/day = ~12K requests/sec
- Average object size: 500KB
- Bandwidth: 12K * 500KB = 6GB/sec = 48Gbps

Storage (for edge caching):
- Cache 10% of popular content
- Total content: 100TB
- Per POP cache: 100TB / 50 POPs = 2TB per POP
- With replication: 2TB * 3 = 6TB per POP
```

### 3. API Design** (RADIO: API)
```
Content Publisher API:
- POST /v1/content        → Upload content
- PUT /v1/content/{id}    → Update content
- DELETE /v1/content/{id} → Delete + invalidate
- POST /v1/purge          → Bulk cache invalidation

Content Consumer API:
- GET /{path}             → Fetch content
- HEAD /{path}            → Check existence

Configuration API:
- POST /v1/origins        → Register origin
- PUT /v1/cache-rules     → Configure caching behavior
```

### 4. Data Model (RADIO: Data Model)
```java
// Domain-Driven Design approach

@Entity
public class Content {
    private String id;
    private String url;
    private ContentType type;
    private byte[] data;           // Or S3 reference
    private CachePolicy policy;
    private Map<String, String> metadata;
}

@Entity
public class CachePolicy {
    private Duration ttl;
    private List<String> varyHeaders;  // Vary by: User-Agent, Accept-Language
    private boolean canServeStale;
    private PurgeStrategy purgeStrategy;
}

@Entity
public class EdgePOP {
    private String id;
    private GeoLocation location;
    private int capacity;           // Requests/sec
    private HealthStatus health;
    private CacheStorage localStorage;
}
```

### 5. High-Level Design** (RADIO: Initial Design)
```
┌──────────────────────────────────────────────────────────────┐
│                      CLIENT REQUEST                          │
└────────────────────┬─────────────────────────────────────────┘
                     ↓
          ┌──────────────────────┐
          │   DNS / Anycast      │ ← GeoDNS resolves to nearest POP
          └──────────┬───────────┘
                     ↓
          ┌──────────────────────┐
          │   Edge POP (CDN)     │
          │  ┌───────────────┐   │
          │  │  L1: Memory   │   │ ← Hot cache (Redis/Varnish)
          │  ├───────────────┤   │
          │  │  L2: SSD      │   │ ← Warm cache (larger, slower)
          │  └───────────────┘   │
          └──────────┬───────────┘
                     ↓ (on cache miss)
          ┌──────────────────────┐
          │   Origin Shield      │ ← Collapse requests
          └──────────┬───────────┘
                     ↓
          ┌──────────────────────┐
          │   Origin Servers     │
          │   (S3 / Your API)    │
          └──────────────────────┘
```

### 6. Deep Dives** (RADIO: Optimize)

**A. Caching Strategy**
```text
Cache-Control header decision tree:
- User-specific content?    → private, no-cache
- Updates frequently?        → max-age=60
- Static/versioned assets?   → max-age=31536000, immutable
- API responses?             → max-age=300, stale-while-revalidate=3600
```

**B. Consistent Hashing for Cache Distribution**
```python
class ConsistentHash:
    """
    Ensures minimal cache invalidation when POPs are added/removed
    """
    def __init__(self, virtual_nodes=150):
        self.ring = {}  # hash -> node
        self.sorted_keys = []
        self.virtual_nodes = virtual_nodes
    
    def get_node(self, key: str) -> str:
        """Find which POP should cache this content"""
        if not self.ring:
            return None
        
        hash_val = self.hash(key)
        # Binary search for nearest node
        idx = bisect.bisect(self.sorted_keys, hash_val)
        if idx == len(self.sorted_keys):
            idx = 0
        
        return self.ring[self.sorted_keys[idx]]
```

**C. Metrics & Monitoring**
```
Key metrics to track:
1. Cache Hit Ratio: hits / (hits + misses)
   - Target: > 85% for static, > 60% for dynamic

2. Origin Offload: 1 - (origin_requests / total_requests)
   - Target: > 90%

3. P50, P95, P99 latency
   - Target: P95 < 100ms

4. Bytes served from edge vs origin
   - Cost optimization metric

5. Error rates by POP
   - 5xx errors, timeouts, connection failures
```

---

## 🧠 **MIND MAP: CDN CONCEPTS**
```
                    CDN/Edge Network
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
    ROUTING          CACHING            RESILIENCE
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
 Anycast  BGP     TTL     Invalidate  Circuit  Failover
  DNS           Policies   Strategies  Breaker
   |              |           |           |         |
GeoDNS      Versioning   Push/Pull   Stale    Multi-CDN
Health       Immutable   Streaming    While
Checks       Assets      (Kafka)    Revalidate
```

### 💡 **EMOTIONAL ANCHORS (For Subconscious Power)**


Create strong memories with these analogies:

1. **CDN = Global Warehouse Network 🏭**

- Amazon doesn't ship everything from Seattle
- They cache (stock) popular items in regional warehouses
- You get fast delivery because it's nearby


2. **Cache Invalidation = Recall Notice 📢**

- When there's a product defect, must notify all warehouses
- Can't let old inventory stay on shelves
- Push (immediate) vs Pull (periodic checks)


3. **Origin Shield = Buyer Consolidation 🛒**

- Instead of 100 stores ordering separately from manufacturer
- One central buyer places bulk orders
- Reduces cost and load on manufacturer


4. **Anycast = "Call the nearest Starbucks" ☕**

- You dial same number, routed to nearest location
- Same IP address, different physical servers
- Network layer handles routing

