# Design a Location-Based Service (Yelp / Google Places)

**Companies**: Yelp, Google Maps, Foursquare, TripAdvisor
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a location-based service like Yelp or Google Places that allows users to discover nearby businesses, restaurants, and points of interest. The system should support searching by location, filtering by categories, viewing reviews and ratings, and handling real-time location updates.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Nearby Search**: Find businesses within a radius (e.g., restaurants within 5 km)
2. **Category Filtering**: Filter by business type (restaurants, hotels, gas stations, etc.)
3. **Reviews & Ratings**: Users can read and write reviews, see aggregated ratings
4. **Business Details**: View business info (hours, photos, contact, amenities)
5. **Search Ranking**: Results ranked by distance, rating, popularity, relevance
6. **Check-ins** (optional): Users can check into locations
7. **Real-time Updates** (optional): Live business hours, wait times, crowd levels

**Prioritize**: Focus on nearby search, category filtering, and reviews for MVP

### Non-Functional Requirements

1. **Low Latency**: Search results <200ms (P95)
2. **High Availability**: 99.95% uptime (critical for travelers)
3. **Geo-Distributed**: Serve users globally with regional data
4. **Accuracy**: Location precision within 10 meters
5. **Scalable**: Support millions of concurrent searches
6. **Consistency**: Reviews eventually consistent (acceptable delay)

### Capacity Estimation

**Assumptions**:
- 500M total users, 100M DAU
- 200M businesses globally
- Each user performs 5 searches per day
- 10% of users write reviews (10M reviews/day)
- Average 3 photos per business

**Read Load (Searches)**:
```
500M searches/day (100M users × 5 searches)
500M ÷ 100K = 5,000 QPS
Peak: 10,000 QPS
```

**Write Load (Reviews/Check-ins)**:
```
10M reviews/day
10M ÷ 100K = 100 writes/sec
Peak: 200 writes/sec
```

**Storage**:
```
Business Data:
  - 200M businesses × 5KB avg = 1 TB

Reviews:
  - 10M/day × 365 days × 3 years = 11B reviews
  - 11B × 500 bytes = 5.5 TB

Photos:
  - 200M businesses × 3 photos × 2 MB = 1.2 PB

Geospatial Indexes:
  - Quadtree/R-tree indexes: ~500 GB

Total: ~1.2 PB (mostly photos)
```

**Bandwidth**:
```
Read: 5K QPS × 5KB = 25 MB/sec
Photos: 2K photo requests/sec × 2MB = 4 GB/sec (CDN critical!)
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                   Mobile/Web Client                 │
│              (Maps, Search, Reviews)                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│               CDN (CloudFront/Fastly)               │
│          Static Content: Photos, Map Tiles          │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              API Gateway / Load Balancer            │
│          (Geo-routing, Rate Limiting)               │
└──────────────────────┬──────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼                             ▼
┌──────────────────┐       ┌──────────────────┐
│ Search Service   │       │  Business Service│
│ (Proximity Query)│       │  (CRUD, Details) │
└────────┬─────────┘       └────────┬─────────┘
         │                          │
         ▼                          ▼
┌──────────────────┐       ┌──────────────────┐
│ Geospatial DB    │       │  PostgreSQL      │
│ (PostGIS/ES)     │       │  (Business Data) │
└──────────────────┘       └──────────────────┘
         │
         ▼
┌──────────────────┐       ┌──────────────────┐
│  Redis Cache     │       │  Review Service  │
│  (Hot Locations) │       │  (Ratings, Text) │
└──────────────────┘       └────────┬─────────┘
                                    │
                                    ▼
                           ┌──────────────────┐
                           │   MongoDB        │
                           │   (Reviews)      │
                           └──────────────────┘
```

### Components

**API Gateway**:
- Geo-based routing (route to nearest regional cluster)
- Rate limiting per user/IP
- Authentication (OAuth2, JWT)

**Search Service**:
- Core proximity search logic
- Ranking algorithm (distance + rating + popularity)
- Filter by category, price, hours

**Geospatial Database**:
- **PostGIS** (PostgreSQL extension) or **Elasticsearch** with geo queries
- Supports radius searches, bounding box queries
- Geohashing or Quadtree indexing

**Business Service**:
- CRUD for business listings
- Business hours, amenities, contact info
- Owner verification

**Review Service**:
- User-generated reviews and ratings
- Aggregation for business scores
- Moderation and spam detection

**Cache Layer**:
- Cache hot search results (popular locations)
- Cache business details for frequently viewed places
- TTL: 1 hour (balance freshness vs performance)

---

## Step 3: Data Model (10 min)

### Database Choice

**For Business Data: PostgreSQL with PostGIS**
- ACID transactions for business updates
- PostGIS extension for geospatial queries
- Proven at scale (used by Foursquare, Mapbox)

**For Reviews: MongoDB**
- Flexible schema for varying review content
- Fast writes for high-volume user content
- Easy to shard by business_id

**For Search Index: Elasticsearch**
- Full-text search on business names, descriptions
- Geospatial queries (geo_distance, geo_bounding_box)
- Fast aggregations for faceted search

### Schema Design

#### PostgreSQL (Business Data)

```sql
-- Business Table
CREATE TABLE businesses (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50),
    location GEOGRAPHY(Point, 4326),  -- PostGIS type (lat, lon)
    address TEXT,
    phone VARCHAR(20),
    hours JSONB,
    amenities TEXT[],
    price_range INT,  -- 1-4 ($, $$, $$$, $$$$)
    rating_avg DECIMAL(2,1),  -- Denormalized for performance
    review_count INT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

-- Geospatial index for proximity queries
CREATE INDEX idx_location ON businesses USING GIST (location);
CREATE INDEX idx_category ON businesses(category);
CREATE INDEX idx_rating ON businesses(rating_avg DESC);

-- Example query: Find restaurants within 5km
-- SELECT * FROM businesses
-- WHERE category = 'restaurant'
-- AND ST_DWithin(location, ST_MakePoint(-122.4194, 37.7749)::geography, 5000)
-- ORDER BY location <-> ST_MakePoint(-122.4194, 37.7749)::geography
-- LIMIT 20;
```

#### MongoDB (Reviews)

```javascript
// Reviews Collection
{
  _id: ObjectId,
  business_id: Long,
  user_id: Long,
  rating: Int,  // 1-5 stars
  review_text: String,
  photos: [String],  // S3 URLs
  helpful_count: Int,
  created_at: ISODate,
  updated_at: ISODate
}

// Indexes
db.reviews.createIndex({ business_id: 1, created_at: -1 });
db.reviews.createIndex({ user_id: 1 });
db.reviews.createIndex({ created_at: -1 });  // Recent reviews
```

#### Elasticsearch (Search Index)

```json
{
  "mappings": {
    "properties": {
      "business_id": { "type": "long" },
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "category": { "type": "keyword" },
      "location": { "type": "geo_point" },
      "rating_avg": { "type": "float" },
      "review_count": { "type": "integer" },
      "price_range": { "type": "integer" },
      "tags": { "type": "keyword" }
    }
  }
}
```

---

## Step 4: APIs (5 min)

### Search Nearby Businesses

```http
GET /api/v1/search/nearby

Query Parameters:
  - latitude: float (required)
  - longitude: float (required)
  - radius: int (meters, default: 5000, max: 50000)
  - category: string (optional, e.g., "restaurant")
  - min_rating: float (optional, e.g., 4.0)
  - price_range: string (optional, e.g., "2,3")
  - limit: int (default: 20, max: 100)
  - offset: int (pagination)

Response: 200 OK
{
  "results": [
    {
      "business_id": 12345,
      "name": "The French Laundry",
      "category": "restaurant",
      "distance_meters": 1234,
      "rating_avg": 4.8,
      "review_count": 5430,
      "price_range": 4,
      "address": "6640 Washington St, Yountville, CA",
      "location": {
        "lat": 38.4044,
        "lon": -122.3632
      },
      "photo_url": "https://cdn.yelp.com/photos/..."
    }
  ],
  "total": 342,
  "page": 1,
  "per_page": 20
}
```

### Get Business Details

```http
GET /api/v1/businesses/{business_id}

Response: 200 OK
{
  "business_id": 12345,
  "name": "The French Laundry",
  "category": "restaurant",
  "rating_avg": 4.8,
  "review_count": 5430,
  "price_range": 4,
  "address": "6640 Washington St, Yountville, CA 94599",
  "phone": "+1 707-944-2380",
  "website": "https://www.thomaskeller.com/tfl",
  "hours": {
    "monday": "Closed",
    "tuesday": "5:30 PM - 9:00 PM",
    ...
  },
  "amenities": ["reservations", "fine_dining", "wheelchair_accessible"],
  "photos": ["url1", "url2", ...],
  "location": {
    "lat": 38.4044,
    "lon": -122.3632
  }
}
```

### Submit Review

```http
POST /api/v1/reviews

Request:
{
  "business_id": 12345,
  "rating": 5,
  "review_text": "Amazing experience! Best meal of my life.",
  "photos": ["base64_or_url"]
}

Response: 201 Created
{
  "review_id": "abc123",
  "business_id": 12345,
  "user_id": 9876,
  "rating": 5,
  "created_at": "2025-11-15T10:30:00Z"
}
```

### Get Reviews for Business

```http
GET /api/v1/businesses/{business_id}/reviews

Query Parameters:
  - sort: string (newest, highest, lowest, helpful)
  - limit: int
  - offset: int

Response: 200 OK
{
  "reviews": [
    {
      "review_id": "abc123",
      "user_name": "John D.",
      "rating": 5,
      "review_text": "...",
      "photos": [],
      "helpful_count": 23,
      "created_at": "2025-11-15T10:30:00Z"
    }
  ],
  "total": 5430
}
```

---

## Step 5: Core Algorithm - Geospatial Indexing

### Approach 1: Database Geospatial Queries (PostGIS)

**How it works**:
- Store lat/lon as `GEOGRAPHY` type in PostgreSQL
- Use PostGIS functions for radius queries
- Leverage GIST spatial indexes for fast lookups

```sql
-- Find businesses within 5km radius
SELECT
    id,
    name,
    ST_Distance(location, ST_MakePoint(-122.4194, 37.7749)::geography) as distance
FROM businesses
WHERE
    category = 'restaurant'
    AND ST_DWithin(
        location,
        ST_MakePoint(-122.4194, 37.7749)::geography,
        5000  -- 5000 meters
    )
ORDER BY distance
LIMIT 20;
```

**Pros**:
- Simple to implement
- ACID guarantees
- Works for moderate scale (<10M locations)

**Cons**:
- Slower for very large datasets (>100M points)
- Limited horizontal scaling

---

### Approach 2: Geohashing (RECOMMENDED for Scale)

**How it works**:
- Convert lat/lon to geohash string (precision-based grid)
- Store geohash in database (indexed)
- Search by geohash prefix for nearby locations

**Geohash Example**:
```
Lat: 37.7749, Lon: -122.4194
Geohash (precision 6): "9q8yy"

Precision levels:
  1 char: ±2500 km
  2 char: ±630 km
  3 char: ±78 km
  4 char: ±20 km
  5 char: ±2.4 km
  6 char: ±610 m
  7 char: ±76 m
```

**Implementation**:

```python
import geohash

def search_nearby(lat, lon, radius_km):
    # Get geohash at appropriate precision
    precision = get_precision_for_radius(radius_km)
    center_hash = geohash.encode(lat, lon, precision)

    # Get neighboring geohash cells
    neighbors = geohash.neighbors(center_hash)
    search_hashes = [center_hash] + neighbors

    # Query database
    results = db.query("""
        SELECT * FROM businesses
        WHERE geohash IN (%s)
        AND ST_DWithin(location, ST_MakePoint(?, ?)::geography, ?)
        ORDER BY location <-> ST_MakePoint(?, ?)::geography
        LIMIT 20
    """, search_hashes, lon, lat, radius_km * 1000, lon, lat)

    return results

def get_precision_for_radius(radius_km):
    if radius_km <= 0.61: return 7
    elif radius_km <= 2.4: return 6
    elif radius_km <= 20: return 5
    elif radius_km <= 78: return 4
    else: return 3
```

**Database Schema**:
```sql
ALTER TABLE businesses ADD COLUMN geohash VARCHAR(12);
CREATE INDEX idx_geohash ON businesses(geohash);

-- Update geohashes
UPDATE businesses
SET geohash = ST_GeoHash(location, 7);
```

**Pros**:
- Fast lookups (simple string prefix match)
- Easy to cache (geohash -> results)
- Scales horizontally (shard by geohash prefix)
- Used by Uber, Redis, DynamoDB

**Cons**:
- Edge cases at geohash boundaries (need to check neighbors)
- Not perfectly circular (grid approximation)

---

### Approach 3: Quadtree / S2 Geometry (Google's Approach)

**Quadtree**:
- Recursively divide map into 4 quadrants
- Each leaf node contains businesses
- Traverse tree to find nearby locations

**S2 Geometry** (Google Maps):
- Divide sphere into hierarchical cells
- Each cell has unique 64-bit ID
- Supports complex shapes, accurate distance

```python
from s2sphere import CellId, LatLng

def search_nearby_s2(lat, lon, radius_meters):
    center = LatLng.from_degrees(lat, lon)

    # Get S2 cell covering the radius
    level = get_s2_level_for_radius(radius_meters)
    cell_id = CellId.from_lat_lng(center).parent(level)

    # Get neighboring cells
    neighbors = cell_id.get_all_neighbors(level)
    search_cells = [cell_id] + neighbors

    # Query database
    results = db.query("""
        SELECT * FROM businesses
        WHERE s2_cell_id IN (%s)
    """, search_cells)

    # Filter by exact distance
    return [r for r in results if haversine_distance(r, center) <= radius_meters]
```

**Pros**:
- Most accurate (handles spherical geometry)
- Efficient for complex regions
- Used by Google, Uber, Lyft

**Cons**:
- More complex to implement
- Requires specialized libraries

---

## Step 6: Ranking Algorithm

### Multi-Factor Ranking

```python
def rank_search_results(businesses, user_location, user_preferences):
    """
    Score = w1 * distance_score + w2 * rating_score + w3 * popularity_score
    """
    for business in businesses:
        # Distance score (inverse, closer = better)
        distance_km = haversine_distance(user_location, business.location)
        distance_score = 1 / (1 + distance_km)  # 0-1 range

        # Rating score (normalized)
        rating_score = business.rating_avg / 5.0  # 0-1 range

        # Popularity score (log scale to reduce outliers)
        popularity_score = math.log10(business.review_count + 1) / 5  # 0-1 range

        # Recency score (boost recently reviewed)
        days_since_last_review = (now() - business.last_review_date).days
        recency_score = 1 / (1 + days_since_last_review / 30)

        # Personalization (match user preferences)
        preference_score = match_preferences(business, user_preferences)

        # Weighted combination
        weights = {
            'distance': 0.4,
            'rating': 0.25,
            'popularity': 0.15,
            'recency': 0.1,
            'preference': 0.1
        }

        business.score = (
            weights['distance'] * distance_score +
            weights['rating'] * rating_score +
            weights['popularity'] * popularity_score +
            weights['recency'] * recency_score +
            weights['preference'] * preference_score
        )

    return sorted(businesses, key=lambda b: b.score, reverse=True)
```

**Factors**:
1. **Distance**: Primary factor (40% weight)
2. **Rating**: Quality signal (25%)
3. **Popularity**: Social proof (15%)
4. **Recency**: Freshness of reviews (10%)
5. **Personalization**: User preferences, past behavior (10%)

---

## Step 7: Optimizations & Scaling (15 min)

### 1. Caching Strategy

**Multi-Tier Caching**:

```python
# Layer 1: Application Cache (In-Memory)
@cache(ttl=300)  # 5 minutes
def get_nearby_businesses(lat, lon, radius, category):
    cache_key = f"nearby:{geohash.encode(lat, lon, 4)}:{category}:{radius}"
    return redis.get(cache_key) or fetch_from_db()

# Layer 2: Redis Cache (Hot Locations)
# Cache popular search results (e.g., "restaurants in Manhattan")
redis.setex(f"search:{geohash}:{filters}", 3600, json.dumps(results))

# Layer 3: CDN Cache (Static Content)
# Cache business photos, map tiles
```

**Cache Invalidation**:
- Business updates: Invalidate specific business cache
- New reviews: Update aggregated rating (async, eventual consistency)
- TTL: 1 hour for search results, 24 hours for static business data

---

### 2. Database Sharding

**Shard by Geohash Prefix**:

```
US West:  Geohash prefix "9" (California, Oregon, etc.)
US East:  Geohash prefix "d" (New York, DC, etc.)
Europe:   Geohash prefix "u" (UK, France, Germany, etc.)
Asia:     Geohash prefix "w" (China, Japan, etc.)
```

**Benefits**:
- Geo-colocated data (low latency)
- Regional failures don't affect global service
- Can scale per region independently

**Implementation**:
```python
def get_shard_for_location(lat, lon):
    geohash_prefix = geohash.encode(lat, lon, precision=1)
    shard_map = {
        '9': 'us-west-db',
        'd': 'us-east-db',
        'u': 'europe-db',
        'w': 'asia-db'
    }
    return shard_map.get(geohash_prefix, 'default-db')
```

---

### 3. Read Replicas for Reviews

**CQRS Pattern**:
- **Write**: Reviews go to MongoDB primary
- **Read**: Serve from read replicas (eventual consistency OK)

```
[User Write Review]
       ↓
[MongoDB Primary] ──replication──> [Replica 1]
                                    [Replica 2]
                                    [Replica 3]
       ↓
[Kafka Event: review.created]
       ↓
[Aggregation Service]
       ↓
[Update business.rating_avg in PostgreSQL]
```

---

### 4. Elasticsearch for Full-Text Search

**Why Elasticsearch?**
- Fast geospatial queries (geo_distance)
- Full-text search on business names, descriptions
- Aggregations for filters (category facets, price ranges)

**Query Example**:
```json
{
  "query": {
    "bool": {
      "must": {
        "match": { "name": "pizza" }
      },
      "filter": [
        {
          "geo_distance": {
            "distance": "5km",
            "location": {
              "lat": 37.7749,
              "lon": -122.4194
            }
          }
        },
        {
          "term": { "category": "restaurant" }
        },
        {
          "range": { "rating_avg": { "gte": 4.0 } }
        }
      ]
    }
  },
  "sort": [
    {
      "_geo_distance": {
        "location": {
          "lat": 37.7749,
          "lon": -122.4194
        },
        "order": "asc",
        "unit": "km"
      }
    }
  ]
}
```

---

### 5. Photo Storage & CDN

**Architecture**:
```
[User Upload Photo]
       ↓
[API Gateway]
       ↓
[Image Processing Service]
   - Resize (thumbnail, medium, large)
   - Compress (WebP, JPEG)
   - Virus scan
       ↓
[Amazon S3] ──> [CloudFront CDN]
       ↓
[Update business.photos in DB]
```

**S3 Bucket Structure**:
```
s3://yelp-photos/
  ├── businesses/
  │   ├── 12345/
  │   │   ├── thumb_001.webp
  │   │   ├── medium_001.webp
  │   │   └── large_001.webp
  │   └── 12346/
  └── users/
      └── 9876/
```

**Optimization**:
- Lazy loading (load images as user scrolls)
- Responsive images (serve appropriate size for device)
- WebP format (30-40% smaller than JPEG)

---

### 6. Real-Time Features

**Live Updates (WebSocket)**:

```python
# Notify users of new reviews, check-ins
@websocket_route('/businesses/{business_id}/live')
def business_updates(business_id):
    # Subscribe to Kafka topic
    for message in kafka_consumer.subscribe(f'business.{business_id}.updates'):
        yield {
            'type': message.type,  # 'new_review', 'check_in', 'hours_update'
            'data': message.data
        }
```

**Use Cases**:
- Live wait times (crowd-sourced from check-ins)
- Real-time business hours updates
- Popular times graph

---

## Step 8: Advanced Considerations

### Security

**1. API Rate Limiting**:
```python
@rate_limit(max_requests=100, window=60)  # 100 req/min per user
def search_nearby():
    ...
```

**2. Review Spam Detection**:
- ML model to detect fake reviews
- Check for suspicious patterns (same IP, rapid reviews)
- User reputation scores

**3. Data Privacy**:
- Anonymize user location data
- GDPR compliance for EU users
- Opt-in for location tracking

---

### Monitoring & Observability

**Key Metrics**:
- Search latency (P50, P95, P99)
- Geospatial query performance
- Cache hit ratio per region
- Database shard load distribution
- Photo CDN hit rate

**Alerts**:
- Search latency > 200ms
- Database replica lag > 5 seconds
- Elasticsearch cluster health degraded
- CDN error rate > 1%

---

### High Availability

**Multi-Region Deployment**:
```
US-West Region:
  - App Servers: 3 AZs
  - PostgreSQL: Primary in us-west-1, Replica in us-west-2
  - Elasticsearch: 6-node cluster (2 per AZ)
  - Redis: Cluster with sentinel

US-East Region:
  - Same setup (failover if West fails)
```

**Disaster Recovery**:
- Database backups: Daily full + hourly incremental
- Cross-region replication for critical data
- RTO: 5 minutes, RPO: 1 hour

---

## Complete System Design Diagram

```
┌────────────────────────────────────────────────────┐
│              USERS (Mobile/Web)                    │
└────────────────┬───────────────────────────────────┘
                 │
        ┌────────┴────────┐
        │                 │
   [Location]        [Search/Browse]
        │                 │
        ▼                 ▼
┌────────────────────────────────────────────────────┐
│         CDN (CloudFront) - Photos, Tiles           │
└────────────────┬───────────────────────────────────┘
                 │
                 ▼
┌────────────────────────────────────────────────────┐
│       Global Load Balancer (Geo-Routing)           │
└────────────────┬───────────────────────────────────┘
                 │
    ┌────────────┼────────────┐
    ▼            ▼            ▼
[US West]    [US East]    [Europe]
    │            │            │
    └────────────┴────────────┘
                 │
        ┌────────┴────────┐
        │                 │
        ▼                 ▼
┌──────────────┐  ┌──────────────┐
│Search Service│  │Business Svc  │
│              │  │              │
│- Proximity   │  │- CRUD        │
│- Ranking     │  │- Details     │
│- Filters     │  │- Photos      │
└──────┬───────┘  └──────┬───────┘
       │                 │
       ▼                 ▼
┌──────────────┐  ┌──────────────┐
│Elasticsearch │  │ PostgreSQL   │
│              │  │  + PostGIS   │
│- Geo queries │  │              │
│- Full-text   │  │- Business    │
│- Aggregations│  │- Metadata    │
└──────┬───────┘  └──────┬───────┘
       │                 │
       └────────┬────────┘
                ▼
        ┌───────────────┐
        │ Redis Cluster │
        │  (Geo Cache)  │
        └───────────────┘
                │
        ┌───────┴───────┐
        │               │
        ▼               ▼
┌──────────────┐  ┌──────────────┐
│Review Service│  │  Analytics   │
│              │  │              │
│- Ratings     │  │- Aggregation │
│- Comments    │  │- Trending    │
│- Moderation  │  │- Reports     │
└──────┬───────┘  └──────────────┘
       │
       ▼
┌──────────────┐
│   MongoDB    │
│  (Reviews)   │
└──────────────┘
       │
       ▼
┌──────────────┐       ┌──────────────┐
│    Kafka     │──────>│  S3 Photos   │
│  (Events)    │       │  + CDN       │
└──────────────┘       └──────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (users, businesses, searches)?
- Geographic coverage (global vs regional)?
- Real-time requirements (live updates)?
- Photo/media handling scope?
- Review moderation requirements?

**Topics to Cover**:
- Geospatial indexing (Geohash, PostGIS, S2)
- Ranking algorithm (multi-factor)
- Caching strategy (multi-tier)
- Database sharding (by geography)
- Consistency trade-offs (eventual for reviews OK)

**Common Follow-ups**:
- "How do you handle cross-geohash boundary searches?"
  → Check neighboring geohashes, then filter by exact distance
- "How do you prevent review spam?"
  → ML models, rate limiting, user reputation scores
- "How do you scale to support real-time wait times?"
  → WebSockets, Redis pub/sub, crowd-sourced data from check-ins

---

## Key Takeaways

1. **Geospatial Indexing is Critical**: Choose right approach (Geohash for scale, PostGIS for simplicity, S2 for accuracy)
2. **Multi-Tier Caching**: CDN for photos, Redis for hot locations, app-level for queries
3. **Eventual Consistency for Reviews**: Trade-off for performance (aggregated ratings can lag)
4. **Shard by Geography**: Natural partitioning, low latency, regional isolation
5. **Ranking Matters**: Distance + Rating + Popularity + Personalization
6. **CDN for Photos**: 90% of bandwidth, must offload to edge

## Further Reading

- Google S2 Geometry Library
- Elasticsearch Geospatial Queries
- PostGIS Documentation
- Yelp Engineering Blog: "Scaling the Social Graph"
- Uber Engineering: "H3: Hexagonal Hierarchical Geospatial Indexing System"
