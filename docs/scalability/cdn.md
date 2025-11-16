# CDN Architecture

Content Delivery Networks (CDNs) deliver content to users from servers geographically closer to them, reducing latency and improving performance.

## Contents
- [What is a CDN?](#what-is-a-cdn)
- [How CDNs Work](#how-cdns-work)
- [CDN Benefits](#cdn-benefits)
- [Cache Strategies](#cache-strategies)
- [Push vs Pull CDN](#push-vs-pull-cdn)
- [CDN Features](#cdn-features)
- [Popular CDN Providers](#popular-cdn-providers)
- [CDN Architecture Patterns](#cdn-architecture-patterns)

## What is a CDN?

A Content Delivery Network is a geographically distributed network of servers that cache and deliver content to users from the nearest location.

### Without CDN
```
User in Tokyo → Request travels to US server (200ms latency)
User in London → Request travels to US server (150ms latency)
User in Sydney → Request travels to US server (250ms latency)
```

### With CDN
```
User in Tokyo → Tokyo Edge Server (10ms latency)
User in London → London Edge Server (15ms latency)
User in Sydney → Sydney Edge Server (8ms latency)
```

### Key Components

1. **Origin Server**: Your actual application/database servers
2. **Edge Servers (PoPs)**: Distributed cache servers close to users
3. **DNS/Routing**: Directs users to nearest edge server
4. **Cache Invalidation**: Mechanism to update/clear cached content

## How CDNs Work

### Basic Flow

```
1. User requests https://example.com/image.jpg

2. DNS resolves to nearest CDN edge server
   example.com → cdn-tokyo.example.com (for Tokyo user)

3. Edge server checks cache:
   a. If cached (HIT): Return immediately
   b. If not cached (MISS): Fetch from origin, cache, return

4. Subsequent requests from nearby users: Cache HIT (fast!)
```

### Detailed Request Flow

```
User Browser
    |
    v
DNS Resolution
    |
    v
Nearest Edge Server (Tokyo PoP)
    |
    +--> Cache HIT? --> Return content (fast path)
    |
    +--> Cache MISS?
            |
            v
        Fetch from Origin (US)
            |
            v
        Cache at Edge
            |
            v
        Return to User
```

### Geographic Routing

CDNs use several methods to route users:

#### 1. Anycast Routing
```
All edge servers advertise same IP address
Network automatically routes to topologically nearest server
```

#### 2. DNS-based Routing
```python
def resolve_cdn(user_ip):
    location = geolocate(user_ip)
    if location == "Asia":
        return "cdn-asia.example.com"
    elif location == "Europe":
        return "cdn-europe.example.com"
    else:
        return "cdn-us.example.com"
```

#### 3. HTTP Redirect
```
User → cdn.example.com
     → 302 Redirect → cdn-tokyo.example.com
```

## CDN Benefits

### 1. Performance

**Lower Latency**
- Content served from nearby servers
- Reduced network hops
- Faster TCP handshakes

**Example Impact:**
```
Without CDN: 200ms latency
With CDN: 20ms latency
90% improvement!
```

### 2. Scalability

**Traffic Offloading**
```
Black Friday Traffic:
Without CDN: All 1M requests → Origin servers
With CDN: 95% cached → 50K requests to origin

Origin load reduced by 95%!
```

### 3. Availability

**Redundancy**
- Multiple edge servers
- If one fails, route to next nearest
- DDoS mitigation (traffic absorbed at edge)

### 4. Cost Savings

**Reduced Bandwidth Costs**
```
Monthly bandwidth from origin:
Without CDN: 10 TB × $0.10/GB = $1,000
With CDN (5% origin requests): 500 GB × $0.10/GB = $50

Savings: $950/month
```

### 5. Security

- **DDoS Protection**: Absorb attacks at edge
- **SSL/TLS Termination**: Offload encryption
- **WAF**: Web Application Firewall at edge
- **Bot Mitigation**: Block malicious traffic

## Cache Strategies

### Cache-Control Headers

Control how content is cached.

```http
# Static assets (images, CSS, JS) - cache aggressively
Cache-Control: public, max-age=31536000, immutable

# HTML pages - short cache or no cache
Cache-Control: public, max-age=300, must-revalidate

# User-specific content - don't cache
Cache-Control: private, no-cache

# Sensitive data - never cache
Cache-Control: no-store
```

### Cache Key Design

Determine what makes content unique.

**Basic Cache Key:**
```
URL: https://example.com/image.jpg
Cache Key: /image.jpg
```

**Advanced Cache Keys:**
```
# Include query parameters
URL: /api/products?category=electronics&page=2
Cache Key: /api/products?category=electronics&page=2

# Include headers (device type, language)
URL: /index.html
Headers: User-Agent: Mobile, Accept-Language: ja
Cache Key: /index.html:mobile:ja

# Custom cache key
Cache Key: /api/user/profile:{user_id}
```

### Cache Invalidation

#### 1. Time-based (TTL)
```http
Cache-Control: max-age=3600  # Cache for 1 hour
```

**Pros:** Simple, automatic
**Cons:** May serve stale content

#### 2. Purge/Invalidation API
```bash
# Purge specific file
curl -X PURGE https://cdn.example.com/style.css

# Purge by tag
curl -X POST https://api.cdn.com/purge \
  -d '{"tags": ["product-123"]}'

# Purge all
curl -X POST https://api.cdn.com/purge-all
```

**Pros:** Immediate update
**Cons:** Requires manual action

#### 3. Versioned URLs
```html
<!-- Old version -->
<link rel="stylesheet" href="/style.css?v=1">

<!-- New version -->
<link rel="stylesheet" href="/style.css?v=2">
```

**Pros:** Perfect cache control, old and new coexist
**Cons:** Requires build process

#### 4. Immutable Content + Cache Busting
```html
<!-- Best practice: content-hash in filename -->
<link rel="stylesheet" href="/style.abc123.css">
<script src="/app.def456.js"></script>
```

```http
Cache-Control: public, max-age=31536000, immutable
```

**Pros:** Maximum caching, instant updates
**Best practice:** Use this for production!

### Stale-While-Revalidate

Serve stale content while fetching fresh content in background.

```http
Cache-Control: max-age=3600, stale-while-revalidate=86400
```

```
Request at t=0: Fetch from origin, cache (max-age=3600)
Request at t=30min: Serve from cache (fresh)
Request at t=90min: Serve stale from cache + refetch in background
Request at t=25hr: Serve from origin (stale period expired)
```

## Push vs Pull CDN

### Pull CDN (Origin Pull)

Edge servers fetch content from origin on-demand.

```
User Request → Edge Server
               ↓ (if cache miss)
            Origin Server
               ↓
            Cache at Edge
               ↓
            Return to User
```

**How it works:**
1. Configure origin server URL
2. Point DNS to CDN
3. CDN fetches content as needed

**Pros:**
- Easy setup (just point DNS)
- Automatic caching
- Origin is source of truth

**Cons:**
- First request is slow (cold cache)
- Origin must be accessible to CDN

**Best for:**
- Websites, blogs
- User-generated content
- Dynamic content with caching

**Example: Cloudflare**
```
1. Add website to Cloudflare
2. Update nameservers
3. Cloudflare automatically caches content
```

### Push CDN

You manually upload content to CDN.

```
Developer → Upload content → CDN Storage
                              ↓
            User Request → Edge Server → Return cached content
```

**How it works:**
1. Upload files to CDN via API/FTP
2. CDN distributes to all edge servers
3. Content served directly from edge

**Pros:**
- No cold cache (pre-warmed)
- Fine control over what's cached
- Origin doesn't need to be public

**Cons:**
- Manual upload process
- Need to manage two copies
- More complex deployments

**Best for:**
- Video/media files
- Software downloads
- Large static assets
- When origin is private

**Example: AWS S3 + CloudFront**
```bash
# Upload to S3
aws s3 cp video.mp4 s3://my-bucket/

# Create CloudFront distribution pointing to S3
# Files served from edge locations
```

### Comparison

| Feature | Pull CDN | Push CDN |
|---------|----------|----------|
| **Setup** | Easy | Moderate |
| **First request** | Slow (miss) | Fast (pre-cached) |
| **Storage** | As needed | All content |
| **Cost** | Origin traffic | Storage + transfer |
| **Use case** | Websites | Large files |

## CDN Features

### 1. Edge Computing

Run code at edge servers (closer to users).

```javascript
// Cloudflare Workers example
addEventListener('fetch', event => {
  event.respondWith(handleRequest(event.request))
})

async function handleRequest(request) {
  // A/B testing at edge
  const cookie = request.headers.get('cookie')
  if (cookie?.includes('experiment=B')) {
    return fetch('https://origin.com/variant-b.html')
  }
  return fetch('https://origin.com/variant-a.html')
}
```

**Use cases:**
- A/B testing
- Authentication
- Request/response modification
- API gateway
- Server-side rendering

### 2. Image Optimization

On-the-fly image transformation.

```html
<!-- Original -->
<img src="https://cdn.example.com/photo.jpg">

<!-- Automatic optimization -->
<img src="https://cdn.example.com/photo.jpg?width=400&format=webp&quality=80">
```

**Features:**
- Format conversion (WebP, AVIF)
- Resizing
- Compression
- Lazy loading

**Example: Cloudflare Images**
```html
<img src="https://example.com/cdn-cgi/image/width=400,quality=80,format=auto/photo.jpg">
```

### 3. Video Streaming

Adaptive bitrate streaming.

```
Original: video.mp4 (1080p, 5 Mbps)

CDN generates:
- 1080p @ 5 Mbps (high quality)
- 720p @ 2.5 Mbps (medium)
- 480p @ 1 Mbps (low)
- 360p @ 500 Kbps (very low)

Player: Adapts quality based on bandwidth
```

### 4. SSL/TLS

Free SSL certificates and automatic renewal.

```
User (HTTPS) → Edge Server (terminate SSL) → Origin (HTTP/HTTPS)
```

**Benefits:**
- Offload SSL processing from origin
- Automatic certificate renewal
- Support for modern protocols (TLS 1.3)

### 5. DDoS Protection

Absorb attacks at edge layer.

```
Attack: 1 Tbps DDoS
CDN: Distributed across 200+ PoPs
Per PoP: 5 Gbps (manageable)
Origin: Protected (only legitimate traffic)
```

### 6. Analytics

Detailed metrics at edge.

```
- Request volume by geography
- Cache hit ratio
- Bandwidth usage
- Error rates
- Top requested content
```

## Popular CDN Providers

### Cloudflare

**Pros:**
- Free tier available
- DDoS protection included
- Edge workers
- Fast global network

**Pricing:** Free → $20/month → Custom

**Best for:** Websites, APIs, small to medium businesses

### AWS CloudFront

**Pros:**
- Integrates with AWS services
- Edge Lambda functions
- Pay-as-you-go pricing

**Pricing:** $0.085/GB (varies by region)

**Best for:** AWS-native applications, enterprises

### Fastly

**Pros:**
- Instant purging (150ms)
- Powerful edge computing (VCL)
- Real-time analytics

**Pricing:** $0.12/GB + $0.0075/request

**Best for:** High-traffic sites, media companies

### Akamai

**Pros:**
- Largest CDN network
- Enterprise features
- Media delivery optimized

**Pricing:** Custom (expensive)

**Best for:** Large enterprises, video streaming

### Google Cloud CDN

**Pros:**
- Integrates with GCP
- Anycast IP
- Global load balancing

**Pricing:** $0.08/GB (varies)

**Best for:** GCP-native applications

### Comparison

| Provider | PoPs | Free Tier | Edge Compute | Best For |
|----------|------|-----------|--------------|----------|
| Cloudflare | 300+ | Yes | Workers | General use |
| CloudFront | 400+ | No | Lambda@Edge | AWS users |
| Fastly | 60+ | No | VCL | High traffic |
| Akamai | 4000+ | No | EdgeWorkers | Enterprise |
| GCP CDN | 100+ | No | Cloud Functions | GCP users |

## CDN Architecture Patterns

### 1. Static Site Hosting

```
S3 Bucket (Origin)
    ↓
CloudFront (CDN)
    ↓
Route 53 (DNS)
    ↓
Users worldwide
```

**Setup:**
```bash
# Upload site to S3
aws s3 sync ./dist s3://my-site-bucket

# Create CloudFront distribution
aws cloudfront create-distribution \
  --origin-domain-name my-site-bucket.s3.amazonaws.com

# Point DNS to CloudFront
example.com → CNAME → d123.cloudfront.net
```

### 2. Multi-Tier Caching

```
User
  ↓
CDN Edge (cache: 1 hour)
  ↓
CDN Shield/Origin Shield (cache: 6 hours)
  ↓
Origin Server (cache: in-memory)
  ↓
Database
```

**Benefits:**
- Reduced origin requests
- Better cache hit ratio
- Protection from cache stampede

### 3. Microservices with CDN

```
                   CDN
                    |
        +-----------+-----------+
        |           |           |
    API Gateway  Static    Edge Functions
        |        Assets    (Auth, Routing)
        |
   +----+----+
   |    |    |
Service Service Service
   A     B     C
```

### 4. Hybrid: CDN + Application Load Balancer

```
User
  ↓
CDN (cache static assets)
  |
  +--> /static/* → Serve from cache
  |
  +--> /api/* → Pass to origin
                   ↓
              Load Balancer
                   ↓
              App Servers
```

**Configuration:**
```nginx
# CDN behavior
/static/*    → Cache everything, TTL: 1 year
/api/*       → No cache, pass to origin
/index.html  → Cache 5 minutes
```

## Common Use Cases

### 1. Website Acceleration

```
Before CDN:
- HTML load: 800ms
- Images load: 1200ms
- Total: 2000ms

After CDN:
- HTML load: 100ms
- Images load: 50ms (cached)
- Total: 150ms

93% faster!
```

### 2. API Response Caching

```javascript
// Cache API responses at CDN
app.get('/api/products/:id', (req, res) => {
  res.set('Cache-Control', 'public, max-age=300');
  res.json(getProduct(req.params.id));
});
```

### 3. Video Streaming

```
Netflix architecture:
1. Encode video in multiple bitrates
2. Push to CDN (Open Connect)
3. Users stream from nearby cache
4. Handles 200+ million users
```

### 4. Software Distribution

```
Example: Node.js package downloads
registry.npmjs.org → Fastly CDN
- Billions of downloads/month
- Global distribution
- High availability
```

### 5. Mobile App Assets

```
Mobile App → CDN API
- User avatars
- Images
- Videos
- App bundles

Benefits:
- Fast loading
- Reduced backend load
- Offline caching
```

## Best Practices

### 1. Cache-Control Headers

```http
# Static assets
Cache-Control: public, max-age=31536000, immutable

# API responses
Cache-Control: public, max-age=300, stale-while-revalidate=3600

# HTML
Cache-Control: public, max-age=0, must-revalidate
```

### 2. Use Content Hashing

```html
<!-- Bad: requires manual cache busting -->
<link rel="stylesheet" href="/style.css?v=2">

<!-- Good: automatic cache busting -->
<link rel="stylesheet" href="/style.f82b3.css">
```

### 3. Separate Static and Dynamic Content

```
Static (cache aggressively):
- CSS, JS, images
- Fonts, icons

Dynamic (cache carefully or not at all):
- User profiles
- Shopping carts
- Real-time data
```

### 4. Monitor Cache Hit Ratio

```
Target: > 90% cache hit ratio

If lower:
- Check cache headers
- Verify cache key design
- Look for query string variations
- Review purging frequency
```

### 5. Use CDN for Security

```
DDoS Protection: At edge
WAF: Block malicious requests
Rate Limiting: Prevent abuse
SSL/TLS: Modern protocols
```

## Interview Tips

When discussing CDNs:

1. **Explain the basics**: Geographic distribution, caching, latency reduction
2. **Discuss tradeoffs**: Consistency vs performance, cost vs speed
3. **Know cache strategies**: TTL, purging, versioning
4. **Understand push vs pull**: Different use cases
5. **Real-world examples**: Netflix, npm, Cloudflare

## Common Pitfalls

### 1. Over-caching Dynamic Content
```
Problem: User sees stale data
Solution: Proper cache headers, short TTLs for dynamic content
```

### 2. Cache Key Issues
```
Problem: Different users get same cached response
Solution: Include user ID in cache key or use Vary header
```

### 3. Ignoring Cache Busting
```
Problem: Users stuck with old CSS/JS after deployment
Solution: Content hashing in filenames
```

### 4. Not Monitoring Hit Ratio
```
Problem: Low hit ratio → paying for CDN without benefits
Solution: Monitor metrics, optimize cache headers
```

## Key Takeaways

1. CDNs reduce latency by serving content from nearby locations
2. Use pull CDN for websites, push CDN for large static files
3. Cache-Control headers are critical for proper caching
4. Version static assets with content hashing
5. CDNs provide more than caching: security, edge compute, optimization
6. Monitor cache hit ratio to ensure effectiveness

## Further Reading

- [Caching Strategies](caching.md) - Detailed caching patterns
- [Performance Optimization](performance.md) - Overall performance techniques
- [Load Balancing](load-balancing.md) - Distributing traffic
