# 🛠️ Location-Based Services: Tech Stack & DevOps Guide

## Complete Technology Stack Used by Industry Leaders

This guide covers the **actual technologies** used by Yelp, Google Maps, Uber, Airbnb, and similar location-based services, along with **DevOps best practices** for production deployment.

---

## 📚 Table of Contents

1. [Backend Technologies](#backend-technologies)
2. [Databases & Storage](#databases--storage)
3. [Caching & In-Memory Stores](#caching--in-memory-stores)
4. [Search & Indexing](#search--indexing)
5. [Message Queues & Streaming](#message-queues--streaming)
6. [Frontend Technologies](#frontend-technologies)
7. [Cloud Infrastructure](#cloud-infrastructure)
8. [DevOps & CI/CD](#devops--cicd)
9. [Monitoring & Observability](#monitoring--observability)
10. [Security & Compliance](#security--compliance)

---

## 1. Backend Technologies

### **Application Frameworks**

#### **Java / Spring Boot** (Yelp, LinkedIn)
```xml
<!-- pom.xml -->
<dependencies>
    <!-- Spring Boot Starter -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.0</version>
    </dependency>

    <!-- Spring Data JPA for PostgreSQL -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <!-- PostGIS for geospatial queries -->
    <dependency>
        <groupId>org.hibernate</groupId>
        <artifactId>hibernate-spatial</artifactId>
    </dependency>

    <!-- Redis for caching -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-redis</artifactId>
    </dependency>
</dependencies>
```

**Why Java/Spring Boot?**
- ✅ Mature ecosystem for enterprise applications
- ✅ Excellent concurrency (Virtual Threads in Java 21)
- ✅ Strong typing prevents geo-coordinate bugs
- ✅ Hibernate Spatial for PostGIS integration
- ❌ Slower development than Python/Node.js

---

#### **Python / FastAPI** (Uber, Lyft)
```python
# main.py
from fastapi import FastAPI, Query
from pydantic import BaseModel, Field
from sqlalchemy import create_engine, func
from geoalchemy2 import Geography
import redis

app = FastAPI()

# Database connection with PostGIS support
engine = create_engine("postgresql://user:pass@localhost/places")

# Redis cache
cache = redis.Redis(host='localhost', port=6379, decode_responses=True)

class SearchRequest(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    radius: int = Field(5000, ge=100, le=50000)  # meters
    category: str | None = None

@app.get("/api/v1/search/nearby")
async def search_nearby(req: SearchRequest):
    # Geohash-based cache key
    cache_key = f"search:{geohash(req.latitude, req.longitude, 5)}:{req.category}:{req.radius}"

    # Check cache
    if cached := cache.get(cache_key):
        return json.loads(cached)

    # Query PostGIS
    point = f"POINT({req.longitude} {req.latitude})"
    results = session.query(Business).filter(
        func.ST_DWithin(
            Business.location,
            func.ST_GeogFromText(point),
            req.radius
        )
    ).all()

    # Cache for 1 hour
    cache.setex(cache_key, 3600, json.dumps(results))

    return results
```

**Why Python/FastAPI?**
- ✅ Rapid development and prototyping
- ✅ Excellent ML integration (recommender systems)
- ✅ GeoAlchemy2 for PostGIS
- ✅ Type hints with Pydantic for validation
- ❌ Slower than Java/Go for CPU-intensive tasks

---

#### **Node.js / Express** (Foursquare)
```javascript
// server.js
const express = require('express');
const { Client } = require('pg');
const redis = require('redis');
const geohash = require('ngeohash');

const app = express();
const pgClient = new Client({
  connectionString: 'postgresql://localhost/places',
});
const redisClient = redis.createClient();

app.get('/api/v1/search/nearby', async (req, res) => {
  const { lat, lon, radius = 5000, category } = req.query;

  // Generate cache key
  const hash = geohash.encode(lat, lon, 5);
  const cacheKey = `search:${hash}:${category}:${radius}`;

  // Check cache
  const cached = await redisClient.get(cacheKey);
  if (cached) {
    return res.json(JSON.parse(cached));
  }

  // Query PostGIS
  const query = `
    SELECT * FROM businesses
    WHERE ST_DWithin(
      location,
      ST_MakePoint($1, $2)::geography,
      $3
    )
    ORDER BY location <-> ST_MakePoint($1, $2)::geography
    LIMIT 20
  `;

  const result = await pgClient.query(query, [lon, lat, radius]);

  // Cache for 1 hour
  await redisClient.setEx(cacheKey, 3600, JSON.stringify(result.rows));

  res.json(result.rows);
});
```

**Why Node.js?**
- ✅ Event-driven, great for I/O-heavy workloads
- ✅ Fast development with JavaScript
- ✅ Large npm ecosystem
- ❌ Single-threaded (use worker threads for CPU tasks)

---

#### **Go / Gin** (Uber, DoorDash)
```go
// main.go
package main

import (
    "github.com/gin-gonic/gin"
    "github.com/go-redis/redis/v8"
    "gorm.io/gorm"
    "gorm.io/driver/postgres"
    "github.com/mmcloughlin/geohash"
)

type Business struct {
    ID        uint
    Name      string
    Location  string `gorm:"type:geography(POINT,4326)"`
    Category  string
}

func main() {
    // PostgreSQL connection
    db, _ := gorm.Open(postgres.Open("host=localhost user=postgres dbname=places"), &gorm.Config{})

    // Redis client
    rdb := redis.NewClient(&redis.Options{
        Addr: "localhost:6379",
    })

    router := gin.Default()

    router.GET("/api/v1/search/nearby", func(c *gin.Context) {
        lat := c.Query("lat")
        lon := c.Query("lon")
        radius := c.DefaultQuery("radius", "5000")

        // Cache key
        hash := geohash.Encode(lat, lon)
        cacheKey := fmt.Sprintf("search:%s:%s", hash[:5], radius)

        // Check cache
        if val, err := rdb.Get(ctx, cacheKey).Result(); err == nil {
            c.JSON(200, val)
            return
        }

        // Query database (using raw SQL for PostGIS)
        var businesses []Business
        db.Raw(`
            SELECT * FROM businesses
            WHERE ST_DWithin(
                location,
                ST_MakePoint(?, ?)::geography,
                ?
            )
        `, lon, lat, radius).Scan(&businesses)

        // Cache result
        rdb.Set(ctx, cacheKey, businesses, 1*time.Hour)

        c.JSON(200, businesses)
    })

    router.Run(":8080")
}
```

**Why Go?**
- ✅ High performance (compiled, concurrent)
- ✅ Low memory footprint
- ✅ Excellent for microservices
- ✅ Great concurrency primitives (goroutines)
- ❌ Verbose error handling

---

## 2. Databases & Storage

### **PostgreSQL + PostGIS** (Primary Database)

**Why PostgreSQL?**
- ✅ ACID transactions for business-critical data
- ✅ PostGIS extension for geospatial queries
- ✅ Mature, battle-tested (used by Instagram, Uber)
- ✅ Excellent indexing (B-tree, GIST, GIN)

**Installation & Configuration:**
```bash
# Install PostgreSQL 16 with PostGIS
sudo apt-get install postgresql-16 postgresql-16-postgis-3

# Enable PostGIS extension
sudo -u postgres psql -d places -c "CREATE EXTENSION postgis;"

# Optimize for geospatial workloads
sudo nano /etc/postgresql/16/main/postgresql.conf
```

**postgresql.conf optimizations:**
```conf
# Memory
shared_buffers = 16GB              # 25% of total RAM
effective_cache_size = 48GB        # 75% of total RAM
work_mem = 256MB                   # For complex geo queries
maintenance_work_mem = 2GB         # For VACUUM, CREATE INDEX

# Query planner
random_page_cost = 1.1             # For SSD (default 4.0 is for HDD)
effective_io_concurrency = 200     # For SSD

# Write-ahead log
wal_buffers = 16MB
checkpoint_completion_target = 0.9
max_wal_size = 4GB
min_wal_size = 1GB

# Parallelism
max_parallel_workers_per_gather = 4
max_parallel_workers = 8
max_worker_processes = 8
```

**Example Schema with Partitioning:**
```sql
-- Enable PostGIS
CREATE EXTENSION IF NOT EXISTS postgis;

-- Main business table (partitioned by geohash)
CREATE TABLE businesses (
    id BIGSERIAL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(50),
    location GEOGRAPHY(POINT, 4326),
    geohash VARCHAR(12),
    address TEXT,
    phone VARCHAR(20),
    hours JSONB,
    rating_avg DECIMAL(2,1),
    review_count INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
) PARTITION BY LIST (geohash);

-- Create partitions by geohash prefix (regional sharding)
CREATE TABLE businesses_us_west PARTITION OF businesses
    FOR VALUES IN ('9q', '9r', '9x', '9z');

CREATE TABLE businesses_us_east PARTITION OF businesses
    FOR VALUES IN ('dr', 'dq', 'dx');

-- Indexes on each partition
CREATE INDEX idx_businesses_us_west_location
    ON businesses_us_west USING GIST (location);

CREATE INDEX idx_businesses_us_west_category
    ON businesses_us_west (category);

-- Trigger to auto-calculate geohash
CREATE OR REPLACE FUNCTION update_geohash()
RETURNS TRIGGER AS $$
BEGIN
    NEW.geohash := ST_GeoHash(NEW.location, 7);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_geohash
    BEFORE INSERT OR UPDATE ON businesses
    FOR EACH ROW
    EXECUTE FUNCTION update_geohash();
```

---

### **MongoDB** (Reviews & User-Generated Content)

**Why MongoDB?**
- ✅ Flexible schema for varying review content
- ✅ Horizontal sharding out-of-the-box
- ✅ Fast writes for high-volume user content
- ✅ Change streams for real-time updates

**Deployment (Replica Set + Sharding):**
```yaml
# docker-compose.yml
version: '3.8'
services:
  mongo-config-1:
    image: mongo:7.0
    command: mongod --configsvr --replSet configReplSet
    volumes:
      - mongo-config-1:/data/configdb

  mongo-shard-1:
    image: mongo:7.0
    command: mongod --shardsvr --replSet shard1ReplSet
    volumes:
      - mongo-shard-1:/data/db

  mongo-shard-2:
    image: mongo:7.0
    command: mongod --shardsvr --replSet shard2ReplSet
    volumes:
      - mongo-shard-2:/data/db

  mongos:
    image: mongo:7.0
    command: mongos --configdb configReplSet/mongo-config-1:27019
    ports:
      - "27017:27017"
    depends_on:
      - mongo-config-1
      - mongo-shard-1
      - mongo-shard-2
```

**Sharding Configuration:**
```javascript
// Connect to mongos
mongo --host localhost:27017

// Enable sharding on database
sh.enableSharding("yelp");

// Shard reviews collection by business_id (hash-based)
sh.shardCollection(
  "yelp.reviews",
  { "business_id": "hashed" }
);

// Create indexes
db.reviews.createIndex({ "business_id": 1, "created_at": -1 });
db.reviews.createIndex({ "user_id": 1 });
db.reviews.createIndex({ "rating": 1 });

// TTL index (auto-delete old reviews if needed)
db.reviews.createIndex(
  { "created_at": 1 },
  { expireAfterSeconds: 94608000 }  // 3 years
);
```

**Review Schema:**
```javascript
{
  "_id": ObjectId("..."),
  "business_id": NumberLong(12345),
  "user_id": NumberLong(9876),
  "rating": 5,  // 1-5 stars
  "review_text": "Amazing experience! Best meal of my life.",
  "photos": [
    "https://cdn.yelp.com/photos/abc123.webp",
    "https://cdn.yelp.com/photos/def456.webp"
  ],
  "helpful_count": 23,
  "funny_count": 5,
  "cool_count": 8,
  "created_at": ISODate("2025-11-15T10:30:00Z"),
  "updated_at": ISODate("2025-11-15T10:30:00Z"),
  "metadata": {
    "ip_address": "192.168.1.1",
    "user_agent": "Mozilla/5.0...",
    "device": "mobile"
  }
}
```

---

## 3. Caching & In-Memory Stores

### **Redis Cluster** (Industry Standard)

**Why Redis?**
- ✅ Sub-millisecond latency (100K+ ops/sec)
- ✅ Geospatial commands (GEOADD, GEORADIUS)
- ✅ Cluster mode for horizontal scaling
- ✅ Pub/Sub for real-time features

**Deployment (Redis Cluster with Sentinel):**
```yaml
# docker-compose-redis.yml
version: '3.8'
services:
  redis-master:
    image: redis:7.2-alpine
    command: redis-server --appendonly yes --requirepass strongpassword
    ports:
      - "6379:6379"
    volumes:
      - redis-master-data:/data

  redis-replica-1:
    image: redis:7.2-alpine
    command: redis-server --slaveof redis-master 6379 --masterauth strongpassword
    depends_on:
      - redis-master

  redis-sentinel-1:
    image: redis:7.2-alpine
    command: redis-sentinel /etc/redis/sentinel.conf
    volumes:
      - ./sentinel.conf:/etc/redis/sentinel.conf
    depends_on:
      - redis-master
```

**Caching Strategies:**
```python
import redis
import geohash

# Connect to Redis Cluster
redis_client = redis.RedisCluster(
    host='localhost',
    port=6379,
    decode_responses=True
)

# Cache search results (geohash-based key)
def cache_search_results(lat, lon, radius, category, results):
    # Generate cache key
    hash_prefix = geohash.encode(lat, lon, precision=5)
    cache_key = f"search:{hash_prefix}:{category}:{radius}"

    # Store with 1-hour TTL
    redis_client.setex(
        cache_key,
        3600,  # 1 hour
        json.dumps(results)
    )

# Cache business details
def cache_business(business_id, data):
    redis_client.setex(
        f"business:{business_id}",
        86400,  # 24 hours
        json.dumps(data)
    )

# Store trending businesses (sorted set)
def update_trending(business_id, score):
    redis_client.zadd("trending:businesses", {business_id: score})

    # Keep only top 100
    redis_client.zremrangebyrank("trending:businesses", 0, -101)

# Geospatial caching (Redis Geo commands)
def cache_nearby_locations(lat, lon, radius):
    # Add businesses to geospatial index
    redis_client.geoadd(
        "locations",
        (lon, lat, business_id)
    )

    # Query nearby (radius in meters)
    nearby = redis_client.georadius(
        "locations",
        lon, lat,
        radius,
        unit='m',
        withdist=True,
        sort='ASC'
    )
    return nearby
```

---

## 4. Search & Indexing

### **Elasticsearch** (Geospatial + Full-Text Search)

**Why Elasticsearch?**
- ✅ Built-in geospatial queries (geo_distance, geo_bounding_box)
- ✅ Full-text search on business names, descriptions
- ✅ Aggregations for faceted search
- ✅ Near real-time indexing

**Cluster Deployment:**
```yaml
# elasticsearch-cluster.yml
version: '3.8'
services:
  es-master-1:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - node.name=es-master-1
      - cluster.name=yelp-cluster
      - node.roles=master
      - discovery.seed_hosts=es-master-1,es-data-1
      - cluster.initial_master_nodes=es-master-1
      - xpack.security.enabled=false
    ports:
      - "9200:9200"

  es-data-1:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - node.name=es-data-1
      - cluster.name=yelp-cluster
      - node.roles=data,ingest
      - discovery.seed_hosts=es-master-1
      - ES_JAVA_OPTS=-Xms8g -Xmx8g
    volumes:
      - es-data-1:/usr/share/elasticsearch/data

  es-data-2:
    image: docker.elastic.co/elasticsearch/elasticsearch:8.11.0
    environment:
      - node.name=es-data-2
      - cluster.name=yelp-cluster
      - node.roles=data,ingest
      - discovery.seed_hosts=es-master-1
      - ES_JAVA_OPTS=-Xms8g -Xmx8g
    volumes:
      - es-data-2:/usr/share/elasticsearch/data
```

**Index Mapping:**
```json
PUT /businesses
{
  "settings": {
    "number_of_shards": 6,
    "number_of_replicas": 2,
    "refresh_interval": "1s",
    "index": {
      "max_result_window": 10000
    }
  },
  "mappings": {
    "properties": {
      "business_id": {
        "type": "long"
      },
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": {
            "type": "keyword"
          },
          "autocomplete": {
            "type": "text",
            "analyzer": "autocomplete",
            "search_analyzer": "standard"
          }
        }
      },
      "category": {
        "type": "keyword"
      },
      "location": {
        "type": "geo_point"
      },
      "rating_avg": {
        "type": "float"
      },
      "review_count": {
        "type": "integer"
      },
      "price_range": {
        "type": "integer"
      },
      "tags": {
        "type": "keyword"
      },
      "hours": {
        "type": "object",
        "enabled": false
      }
    }
  }
}
```

**Search Query Example:**
```json
POST /businesses/_search
{
  "query": {
    "bool": {
      "must": {
        "multi_match": {
          "query": "coffee",
          "fields": ["name^3", "category^2", "tags"]
        }
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
          "term": {
            "category": "cafe"
          }
        },
        {
          "range": {
            "rating_avg": {
              "gte": 4.0
            }
          }
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
    },
    {
      "rating_avg": {
        "order": "desc"
      }
    }
  ],
  "size": 20
}
```

---

## 5. Message Queues & Streaming

### **Apache Kafka** (Event Streaming)

**Why Kafka?**
- ✅ High throughput (millions of messages/sec)
- ✅ Durable, replicated logs
- ✅ Event sourcing for analytics
- ✅ Used by Uber, LinkedIn, Netflix

**Deployment (Kafka + Zookeeper):**
```yaml
# kafka-cluster.yml
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9093
```

**Topics & Event Schema:**
```bash
# Create topics
kafka-topics --bootstrap-server localhost:9092 --create \
  --topic review.created --partitions 12 --replication-factor 3

kafka-topics --bootstrap-server localhost:9092 --create \
  --topic business.updated --partitions 6 --replication-factor 3

kafka-topics --bootstrap-server localhost:9092 --create \
  --topic search.performed --partitions 12 --replication-factor 3
```

**Producer (Python):**
```python
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Publish review event
event = {
    "event_type": "review.created",
    "business_id": 12345,
    "user_id": 9876,
    "rating": 5,
    "review_text": "Amazing!",
    "timestamp": "2025-11-15T10:30:00Z"
}

producer.send('review.created', value=event)
producer.flush()
```

**Consumer (Python):**
```python
from kafka import KafkaConsumer

consumer = KafkaConsumer(
    'review.created',
    bootstrap_servers=['localhost:9092'],
    group_id='review-aggregation-service',
    auto_offset_reset='earliest',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

for message in consumer:
    event = message.value
    # Update aggregated rating in PostgreSQL
    update_business_rating(event['business_id'], event['rating'])
```

---

## 6. Frontend Technologies

### **Web Frontend**

**React + TypeScript + Mapbox GL**
```typescript
// SearchMap.tsx
import React, { useState, useEffect } from 'react';
import mapboxgl from 'mapbox-gl';
import 'mapbox-gl/dist/mapbox-gl.css';

interface Business {
  id: number;
  name: string;
  location: { lat: number; lon: number };
  rating: number;
}

const SearchMap: React.FC = () => {
  const [map, setMap] = useState<mapboxgl.Map | null>(null);
  const [businesses, setBusinesses] = useState<Business[]>([]);

  useEffect(() => {
    // Initialize map
    const mapInstance = new mapboxgl.Map({
      container: 'map',
      style: 'mapbox://styles/mapbox/streets-v12',
      center: [-122.4194, 37.7749],
      zoom: 13
    });

    // Add user location marker
    navigator.geolocation.getCurrentPosition((position) => {
      new mapboxgl.Marker({ color: 'blue' })
        .setLngLat([position.coords.longitude, position.coords.latitude])
        .addTo(mapInstance);
    });

    setMap(mapInstance);
  }, []);

  const searchNearby = async (lat: number, lon: number) => {
    const response = await fetch(
      `/api/v1/search/nearby?lat=${lat}&lon=${lon}&radius=5000`
    );
    const data = await response.json();
    setBusinesses(data.results);

    // Add markers
    data.results.forEach((business: Business) => {
      const el = document.createElement('div');
      el.className = 'marker';
      el.innerHTML = `⭐ ${business.rating}`;

      new mapboxgl.Marker(el)
        .setLngLat([business.location.lon, business.location.lat])
        .setPopup(new mapboxgl.Popup().setHTML(`<h3>${business.name}</h3>`))
        .addTo(map!);
    });
  };

  return (
    <div>
      <div id="map" style={{ width: '100%', height: '600px' }} />
      <div className="results">
        {businesses.map(b => (
          <div key={b.id}>{b.name} - {b.rating}⭐</div>
        ))}
      </div>
    </div>
  );
};
```

---

### **Mobile Apps**

**React Native (Cross-Platform)**
```typescript
// SearchScreen.tsx
import React, { useState } from 'react';
import { View, FlatList } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import Geolocation from '@react-native-community/geolocation';

const SearchScreen = () => {
  const [region, setRegion] = useState({
    latitude: 37.7749,
    longitude: -122.4194,
    latitudeDelta: 0.0922,
    longitudeDelta: 0.0421,
  });
  const [businesses, setBusinesses] = useState([]);

  useEffect(() => {
    Geolocation.getCurrentPosition((position) => {
      setRegion({
        ...region,
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
      });
      searchNearby(position.coords.latitude, position.coords.longitude);
    });
  }, []);

  const searchNearby = async (lat, lon) => {
    const res = await fetch(`API_URL/search/nearby?lat=${lat}&lon=${lon}`);
    const data = await res.json();
    setBusinesses(data.results);
  };

  return (
    <View style={{ flex: 1 }}>
      <MapView style={{ flex: 1 }} region={region}>
        {businesses.map((b) => (
          <Marker
            key={b.id}
            coordinate={{ latitude: b.location.lat, longitude: b.location.lon }}
            title={b.name}
          />
        ))}
      </MapView>
      <FlatList
        data={businesses}
        renderItem={({ item }) => <BusinessCard business={item} />}
      />
    </View>
  );
};
```

---

## 7. Cloud Infrastructure

### **AWS (Most Common for Location Services)**

**Complete AWS Architecture:**
```
┌─────────────────────────────────────────────────┐
│  Route 53 (Geo-Routing DNS)                     │
└──────────────────┬──────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ CloudFront   │      │  CloudFront  │
│ (US Region)  │      │ (EU Region)  │
└──────┬───────┘      └──────┬───────┘
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ ALB (US-West)│      │ ALB (EU-West)│
└──────┬───────┘      └──────┬───────┘
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ ECS Fargate  │      │ ECS Fargate  │
│ (Containers) │      │ (Containers) │
└──────┬───────┘      └──────┬───────┘
       │                     │
       └──────────┬──────────┘
                  ▼
    ┌─────────────────────────┐
    │  ElastiCache (Redis)    │
    └─────────────────────────┘
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
┌──────────────┐    ┌──────────────┐
│ RDS          │    │ Elasticsearch│
│ PostgreSQL   │    │ Service      │
└──────────────┘    └──────────────┘
        │
        ▼
┌──────────────┐    ┌──────────────┐
│ S3 (Photos)  │    │ MSK (Kafka)  │
└──────────────┘    └──────────────┘
```

**Terraform Infrastructure as Code:**
```hcl
# main.tf
provider "aws" {
  region = "us-west-2"
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true

  tags = {
    Name = "yelp-vpc"
  }
}

# RDS PostgreSQL with PostGIS
resource "aws_db_instance" "postgres" {
  identifier           = "yelp-postgres"
  engine               = "postgres"
  engine_version       = "16.1"
  instance_class       = "db.r5.4xlarge"
  allocated_storage    = 2000
  storage_type         = "gp3"
  iops                 = 12000

  db_name  = "places"
  username = "admin"
  password = var.db_password

  multi_az               = true
  backup_retention_period = 7

  vpc_security_group_ids = [aws_security_group.db.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name

  # Enable PostGIS
  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Name = "yelp-postgres"
  }
}

# Elasticsearch Domain
resource "aws_elasticsearch_domain" "search" {
  domain_name           = "yelp-search"
  elasticsearch_version = "8.11"

  cluster_config {
    instance_type            = "i3.2xlarge.elasticsearch"
    instance_count           = 9
    dedicated_master_enabled = true
    dedicated_master_type    = "m5.large.elasticsearch"
    dedicated_master_count   = 3
    zone_awareness_enabled   = true

    zone_awareness_config {
      availability_zone_count = 3
    }
  }

  ebs_options {
    ebs_enabled = false  # Using i3 instances with instance storage
  }

  vpc_options {
    subnet_ids         = aws_subnet.private[*].id
    security_group_ids = [aws_security_group.es.id]
  }
}

# ElastiCache Redis Cluster
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "yelp-redis"
  replication_group_description = "Redis cluster for caching"

  engine               = "redis"
  engine_version       = "7.0"
  node_type            = "cache.r6g.2xlarge"
  num_cache_clusters   = 6
  parameter_group_name = "default.redis7.cluster.on"
  port                 = 6379

  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name  = aws_elasticache_subnet_group.main.name
  security_group_ids = [aws_security_group.redis.id]
}

# S3 Bucket for Photos
resource "aws_s3_bucket" "photos" {
  bucket = "yelp-photos-prod"

  tags = {
    Name = "yelp-photos"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "photos_lifecycle" {
  bucket = aws_s3_bucket.photos.id

  rule {
    id     = "transition-to-ia"
    status = "Enabled"

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }
  }
}
```

---

## 8. DevOps & CI/CD

### **Kubernetes Deployment**

**Deployment Manifest:**
```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: search-service
  namespace: production
spec:
  replicas: 10
  selector:
    matchLabels:
      app: search-service
  template:
    metadata:
      labels:
        app: search-service
    spec:
      containers:
      - name: search-service
        image: yelp/search-service:v1.2.3
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: url
        - name: REDIS_URL
          value: "redis://redis-cluster:6379"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5
---
apiVersion: v1
kind: Service
metadata:
  name: search-service
spec:
  selector:
    app: search-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: search-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: search-service
  minReplicas: 10
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

### **GitHub Actions CI/CD Pipeline**

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy

on:
  push:
    branches: [main]

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 21
        uses: actions/setup-java@v3
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Build with Maven
        run: mvn clean package -DskipTests

      - name: Run Tests
        run: mvn test

      - name: Build Docker Image
        run: docker build -t yelp/search-service:${{ github.sha }} .

      - name: Push to ECR
        run: |
          aws ecr get-login-password --region us-west-2 | docker login --username AWS --password-stdin 123456789.dkr.ecr.us-west-2.amazonaws.com
          docker tag yelp/search-service:${{ github.sha }} 123456789.dkr.ecr.us-west-2.amazonaws.com/search-service:${{ github.sha }}
          docker push 123456789.dkr.ecr.us-west-2.amazonaws.com/search-service:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/search-service search-service=123456789.dkr.ecr.us-west-2.amazonaws.com/search-service:${{ github.sha }} -n production
          kubectl rollout status deployment/search-service -n production
```

---

## 9. Monitoring & Observability

### **Prometheus + Grafana**

**Prometheus Configuration:**
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'search-service'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: search-service
        action: keep
```

**Key Metrics to Track:**
```python
# metrics.py
from prometheus_client import Counter, Histogram, Gauge

# Request metrics
search_requests = Counter(
    'search_requests_total',
    'Total search requests',
    ['method', 'endpoint', 'status']
)

search_latency = Histogram(
    'search_latency_seconds',
    'Search request latency',
    buckets=[0.01, 0.05, 0.1, 0.2, 0.5, 1.0, 2.0]
)

# Database metrics
db_query_duration = Histogram(
    'db_query_duration_seconds',
    'Database query duration',
    ['query_type']
)

# Cache metrics
cache_hits = Counter('cache_hits_total', 'Cache hits')
cache_misses = Counter('cache_misses_total', 'Cache misses')

# Business metrics
active_businesses = Gauge('active_businesses_count', 'Number of active businesses')
```

---

### **ELK Stack (Logging)**

**Filebeat Configuration:**
```yaml
# filebeat.yml
filebeat.inputs:
- type: container
  paths:
    - /var/lib/docker/containers/*/*.log
  processors:
    - add_kubernetes_metadata:
        host: ${NODE_NAME}
        matchers:
        - logs_path:
            logs_path: "/var/lib/docker/containers/"

output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "yelp-logs-%{+yyyy.MM.dd}"
```

---

## 10. Security & Compliance

### **Security Best Practices**

**1. API Authentication (JWT)**
```python
from fastapi import Depends, HTTPException
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
import jwt

security = HTTPBearer()

def verify_token(credentials: HTTPAuthorizationCredentials = Depends(security)):
    try:
        payload = jwt.decode(
            credentials.credentials,
            SECRET_KEY,
            algorithms=["HS256"]
        )
        return payload
    except jwt.ExpiredSignatureError:
        raise HTTPException(status_code=401, detail="Token expired")
    except jwt.InvalidTokenError:
        raise HTTPException(status_code=401, detail="Invalid token")

@app.get("/api/v1/search/nearby")
async def search_nearby(user = Depends(verify_token)):
    # Only authenticated users can search
    pass
```

**2. Rate Limiting**
```python
from slowapi import Limiter
from slowapi.util import get_remote_address

limiter = Limiter(key_func=get_remote_address)

@app.get("/api/v1/search/nearby")
@limiter.limit("100/hour")  # 100 requests per hour
async def search_nearby():
    pass
```

**3. Input Validation**
```python
from pydantic import BaseModel, Field, validator

class SearchRequest(BaseModel):
    latitude: float = Field(..., ge=-90, le=90)
    longitude: float = Field(..., ge=-180, le=180)
    radius: int = Field(5000, ge=100, le=50000)

    @validator('latitude', 'longitude')
    def validate_coordinates(cls, v):
        if not isinstance(v, (int, float)):
            raise ValueError('Coordinate must be a number')
        return v
```

---

## 📚 Summary: Complete Tech Stack

### **Backend**
- **Languages**: Java, Python, Go, Node.js
- **Frameworks**: Spring Boot, FastAPI, Gin, Express

### **Databases**
- **Relational**: PostgreSQL + PostGIS
- **NoSQL**: MongoDB (reviews), DynamoDB
- **Search**: Elasticsearch
- **Cache**: Redis Cluster

### **Infrastructure**
- **Cloud**: AWS (EC2, RDS, ElastiCache, S3, CloudFront)
- **Container Orchestration**: Kubernetes (EKS)
- **CI/CD**: GitHub Actions, Jenkins
- **IaC**: Terraform, CloudFormation

### **Monitoring**
- **Metrics**: Prometheus + Grafana
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **Tracing**: Jaeger, AWS X-Ray
- **Alerting**: PagerDuty, Opsgenie

### **DevOps Tools**
- **Version Control**: Git, GitHub
- **Containerization**: Docker
- **Orchestration**: Kubernetes, Docker Swarm
- **Service Mesh**: Istio (advanced)

---

**This tech stack powers location-based services at massive scale for companies like Yelp, Uber, Airbnb, and DoorDash!** 🚀🗺️
