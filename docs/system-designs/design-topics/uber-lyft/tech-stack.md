# 🛠️ Ride-Sharing Platform: Tech Stack & DevOps Guide

## Complete Technology Stack Used by Uber, Lyft, and Industry Leaders

This guide covers the **actual technologies** used by Uber, Lyft, Grab, Didi, and similar ride-sharing platforms, along with **DevOps best practices** for production deployment at massive scale.

---

## 📚 Table of Contents

1. [Backend Technologies](#backend-technologies)
2. [Databases & Storage](#databases--storage)
3. [Real-Time Communication](#real-time-communication)
4. [Caching & In-Memory Stores](#caching--in-memory-stores)
5. [Message Queues & Event Streaming](#message-queues--event-streaming)
6. [Geospatial & Routing](#geospatial--routing)
7. [Payment Processing](#payment-processing)
8. [Machine Learning & AI](#machine-learning--ai)
9. [Mobile Technologies](#mobile-technologies)
10. [Cloud Infrastructure](#cloud-infrastructure)
11. [DevOps & CI/CD](#devops--cicd)
12. [Monitoring & Observability](#monitoring--observability)
13. [Security & Compliance](#security--compliance)

---

## 1. Backend Technologies

### **Application Frameworks**

#### **Go / Microservices** (Uber's Primary Language)

Uber migrated from Node.js to Go for better performance and reliability.

```go
// ride-matching-service/main.go
package main

import (
    "context"
    "encoding/json"
    "github.com/gin-gonic/gin"
    "github.com/go-redis/redis/v8"
    "gorm.io/gorm"
    "github.com/mmcloughlin/geohash"
    "time"
)

type Driver struct {
    ID        string    `json:"id"`
    Location  Location  `json:"location"`
    Status    string    `json:"status"` // online, busy, offline
    Rating    float64   `json:"rating"`
    Heading   float64   `json:"heading"`
    Speed     float64   `json:"speed"`
    UpdatedAt time.Time `json:"updated_at"`
}

type Location struct {
    Lat float64 `json:"lat"`
    Lon float64 `json:"lon"`
}

type MatchingService struct {
    redis *redis.Client
    db    *gorm.DB
}

// Update driver location (called every 5 seconds by driver app)
func (s *MatchingService) UpdateDriverLocation(c *gin.Context) {
    var req struct {
        DriverID string   `json:"driver_id"`
        Location Location `json:"location"`
        Heading  float64  `json:"heading"`
        Speed    float64  `json:"speed"`
    }

    if err := c.BindJSON(&req); err != nil {
        c.JSON(400, gin.H{"error": "Invalid request"})
        return
    }

    ctx := context.Background()

    // Store in Redis Geospatial index
    s.redis.GeoAdd(ctx, "drivers:online", &redis.GeoLocation{
        Name:      req.DriverID,
        Longitude: req.Location.Lon,
        Latitude:  req.Location.Lat,
    })

    // Store additional metadata with TTL
    driverData := map[string]interface{}{
        "lat":        req.Location.Lat,
        "lon":        req.Location.Lon,
        "heading":    req.Heading,
        "speed":      req.Speed,
        "updated_at": time.Now().Unix(),
    }

    s.redis.HSet(ctx, "driver:"+req.DriverID, driverData)
    s.redis.Expire(ctx, "driver:"+req.DriverID, 30*time.Second)

    c.JSON(200, gin.H{"status": "updated"})
}

// Find nearby drivers for ride matching
func (s *MatchingService) FindNearbyDrivers(lat, lon, radius float64) ([]Driver, error) {
    ctx := context.Background()

    // Query Redis GEORADIUS
    results, err := s.redis.GeoRadius(ctx, "drivers:online", lon, lat, &redis.GeoRadiusQuery{
        Radius:      radius,
        Unit:        "km",
        WithCoord:   true,
        WithDist:    true,
        Count:       50,
        Sort:        "ASC", // Nearest first
    }).Result()

    if err != nil {
        return nil, err
    }

    var drivers []Driver
    for _, result := range results {
        // Get driver details from hash
        driverData, _ := s.redis.HGetAll(ctx, "driver:"+result.Name).Result()
        
        driver := Driver{
            ID: result.Name,
            Location: Location{
                Lat: result.Latitude,
                Lon: result.Longitude,
            },
            // Parse other fields from driverData
        }
        drivers = append(drivers, driver)
    }

    return drivers, nil
}

// Score and rank drivers based on multiple factors
func (s *MatchingService) ScoreDriver(driver Driver, riderLocation Location, heading float64) float64 {
    distance := haversineDistance(driver.Location, riderLocation)
    
    score := 0.0
    
    // Distance component (40%)
    score += 100.0 / (1.0 + distance)
    
    // Rating component (25%)
    score += driver.Rating * 10.0
    
    // Heading alignment (15%)
    if isHeadingTowards(driver, riderLocation) {
        score += 15.0
    }
    
    // Speed component (10%) - prefer moving drivers
    if driver.Speed > 5.0 && driver.Speed < 50.0 {
        score += 10.0
    }
    
    // Freshness of location (10%)
    age := time.Since(driver.UpdatedAt).Seconds()
    if age < 10 {
        score += 10.0
    }
    
    return score
}

func main() {
    router := gin.Default()
    
    // Initialize Redis
    rdb := redis.NewClient(&redis.Options{
        Addr:     "localhost:6379",
        Password: "",
        DB:       0,
    })
    
    // Initialize database
    db, _ := gorm.Open(postgres.Open("postgresql://..."), &gorm.Config{})
    
    service := &MatchingService{redis: rdb, db: db}
    
    // Routes
    router.POST("/v1/driver/location", service.UpdateDriverLocation)
    router.GET("/v1/rides/match", service.MatchRide)
    
    router.Run(":8080")
}
```

**Why Go for Ride-Sharing?**
- ✅ High concurrency (goroutines for handling 100K+ location updates/sec)
- ✅ Low latency (compiled, no GC pauses like Java)
- ✅ Excellent for microservices architecture
- ✅ Strong standard library (networking, JSON, crypto)
- ✅ Used by Uber for 70%+ of their backend services

---

#### **Python / FastAPI** (ML Services, Surge Pricing)

Python is ideal for ML-heavy components like ETA prediction, surge pricing, fraud detection.

```python
# surge-pricing-service/main.py
from fastapi import FastAPI, BackgroundTasks
from pydantic import BaseModel
from redis import Redis
from typing import Dict, List
import numpy as np
from datetime import datetime, timedelta

app = FastAPI()
redis_client = Redis(host='localhost', port=6379, decode_responses=True)

class GeohashCell(BaseModel):
    geohash: str
    active_requests: int
    available_drivers: int
    historical_demand: float
    nearby_events: int

def calculate_surge_multiplier(cell: GeohashCell) -> float:
    """
    Calculate surge pricing multiplier based on supply/demand dynamics
    
    Formula: 
    surge = base + demand_factor + event_factor + historical_factor
    """
    base = 1.0
    
    # Demand/supply ratio
    if cell.available_drivers == 0:
        demand_factor = 3.0  # Max surge
    else:
        ratio = cell.active_requests / cell.available_drivers
        demand_factor = min(ratio * 0.5, 3.0)
    
    # Event-based surge (concerts, sports games, airport rush)
    event_factor = cell.nearby_events * 0.3
    
    # Historical demand (time-of-day, day-of-week patterns)
    historical_factor = cell.historical_demand * 0.2
    
    surge = base + demand_factor + event_factor + historical_factor
    
    # Cap at 5× surge (Uber's max is typically 3-5×)
    return min(surge, 5.0)

@app.post("/v1/surge/calculate")
async def calculate_surge_for_all_cells():
    """
    Calculate surge pricing for all active geohash cells
    Runs every 30 seconds via background job
    """
    # Get all active geohash cells
    active_cells = redis_client.smembers("active_geohash_cells")
    
    results = {}
    
    for geohash in active_cells:
        # Get metrics from Redis
        requests = int(redis_client.get(f"geohash:{geohash}:requests") or 0)
        drivers = int(redis_client.get(f"geohash:{geohash}:drivers") or 0)
        
        # Get historical demand (from time-series DB or cache)
        hour = datetime.now().hour
        dow = datetime.now().weekday()
        historical_key = f"historical:{geohash}:{dow}:{hour}"
        historical = float(redis_client.get(historical_key) or 1.0)
        
        # Check nearby events
        events = int(redis_client.get(f"geohash:{geohash}:events") or 0)
        
        cell = GeohashCell(
            geohash=geohash,
            active_requests=requests,
            available_drivers=drivers,
            historical_demand=historical,
            nearby_events=events
        )
        
        surge = calculate_surge_multiplier(cell)
        
        # Store in Redis with 60 second TTL
        redis_client.setex(f"surge:{geohash}", 60, surge)
        
        results[geohash] = surge
    
    return {"surge_updated": len(results), "results": results}

@app.get("/v1/surge/{geohash}")
async def get_surge_for_location(geohash: str):
    """
    Get current surge multiplier for a specific location
    """
    surge = redis_client.get(f"surge:{geohash}")
    
    if surge is None:
        return {"geohash": geohash, "surge": 1.0, "message": "No surge"}
    
    return {
        "geohash": geohash,
        "surge": float(surge),
        "expires_in": redis_client.ttl(f"surge:{geohash}")
    }

# ETA Prediction using ML model
from sklearn.ensemble import GradientBoostingRegressor
import joblib

# Load pre-trained model
eta_model = joblib.load('models/eta_prediction_model.pkl')

@app.post("/v1/eta/predict")
async def predict_eta(
    pickup_lat: float,
    pickup_lon: float,
    dropoff_lat: float,
    dropoff_lon: float,
    current_hour: int,
    day_of_week: int
):
    """
    Predict ETA using ML model trained on historical trip data
    
    Features:
    - Distance (haversine)
    - Time of day
    - Day of week
    - Historical traffic patterns
    - Weather conditions
    """
    # Calculate straight-line distance
    distance = haversine_distance(pickup_lat, pickup_lon, dropoff_lat, dropoff_lon)
    
    # Get traffic factor from cache
    traffic_key = f"traffic:{current_hour}:{day_of_week}"
    traffic_factor = float(redis_client.get(traffic_key) or 1.0)
    
    # Feature vector for ML model
    features = np.array([[
        distance,
        current_hour,
        day_of_week,
        traffic_factor,
        pickup_lat,
        pickup_lon,
        dropoff_lat,
        dropoff_lon
    ]])
    
    # Predict ETA in minutes
    eta_minutes = eta_model.predict(features)[0]
    
    return {
        "eta_minutes": round(eta_minutes, 1),
        "distance_km": round(distance, 2),
        "traffic_factor": traffic_factor
    }
```

**Why Python for ML Components?**
- ✅ Best ecosystem for ML (scikit-learn, TensorFlow, PyTorch)
- ✅ Rapid prototyping and experimentation
- ✅ NumPy/Pandas for data processing
- ✅ Easy integration with data pipelines
- ❌ Slower than Go/Java for high-throughput services

---

#### **Node.js / Socket.io** (WebSocket Servers)

Node.js excels at handling thousands of concurrent WebSocket connections.

```javascript
// websocket-server/server.js
const express = require('express');
const http = require('http');
const socketIO = require('socket.io');
const redis = require('redis');
const { promisify } = require('util');

const app = express();
const server = http.createServer(app);
const io = socketIO(server, {
  cors: { origin: "*" },
  transports: ['websocket', 'polling']
});

// Redis for pub/sub
const redisClient = redis.createClient({ host: 'localhost', port: 6379 });
const subscriber = redis.createClient({ host: 'localhost', port: 6379 });

// Socket.io namespaces
const driverNamespace = io.of('/driver');
const riderNamespace = io.of('/rider');

// Driver connections
driverNamespace.on('connection', (socket) => {
  const driverId = socket.handshake.query.driverId;
  
  console.log(`Driver ${driverId} connected`);
  
  // Join driver to their own room
  socket.join(`driver:${driverId}`);
  
  // Listen for location updates from driver app
  socket.on('location_update', async (data) => {
    const { lat, lon, heading, speed } = data;
    
    // Store in Redis
    await redisClient.geoadd('drivers:online', lon, lat, driverId);
    await redisClient.hset(`driver:${driverId}`, {
      lat, lon, heading, speed,
      updated_at: Date.now()
    });
    await redisClient.expire(`driver:${driverId}`, 30);
    
    // If driver is on a trip, update rider in real-time
    const tripId = await redisClient.get(`driver:${driverId}:active_trip`);
    if (tripId) {
      const riderId = await redisClient.get(`trip:${tripId}:rider_id`);
      
      // Send location to rider via WebSocket
      riderNamespace.to(`rider:${riderId}`).emit('driver_location', {
        lat, lon, heading, speed
      });
    }
  });
  
  // Listen for trip status changes
  socket.on('trip_status', async (data) => {
    const { tripId, status } = data;
    
    // Update trip status in database (via API call or direct DB)
    await updateTripStatus(tripId, status);
    
    // Notify rider
    const riderId = await redisClient.get(`trip:${tripId}:rider_id`);
    riderNamespace.to(`rider:${riderId}`).emit('trip_status_changed', {
      tripId, status, timestamp: Date.now()
    });
  });
  
  socket.on('disconnect', () => {
    console.log(`Driver ${driverId} disconnected`);
    // Mark driver as offline
    redisClient.zrem('drivers:online', driverId);
  });
});

// Rider connections
riderNamespace.on('connection', (socket) => {
  const riderId = socket.handshake.query.riderId;
  
  console.log(`Rider ${riderId} connected`);
  socket.join(`rider:${riderId}`);
  
  socket.on('request_ride', async (data) => {
    const { pickup_lat, pickup_lon, dropoff_lat, dropoff_lon } = data;
    
    // Publish to ride matching queue (Kafka or Redis Pub/Sub)
    await redisClient.publish('ride_requests', JSON.stringify({
      riderId,
      pickup: { lat: pickup_lat, lon: pickup_lon },
      dropoff: { lat: dropoff_lat, lon: dropoff_lon },
      timestamp: Date.now()
    }));
    
    socket.emit('ride_request_received', { 
      message: 'Finding drivers nearby...' 
    });
  });
  
  socket.on('disconnect', () => {
    console.log(`Rider ${riderId} disconnected`);
  });
});

// Subscribe to Redis pub/sub for ride events
subscriber.subscribe('ride_matched');
subscriber.subscribe('trip_updates');

subscriber.on('message', (channel, message) => {
  const data = JSON.parse(message);
  
  if (channel === 'ride_matched') {
    // Notify rider about matched driver
    riderNamespace.to(`rider:${data.riderId}`).emit('driver_matched', {
      driverId: data.driverId,
      driverName: data.driverName,
      driverRating: data.driverRating,
      eta: data.eta,
      carModel: data.carModel
    });
    
    // Notify driver about new ride
    driverNamespace.to(`driver:${data.driverId}`).emit('new_ride', {
      riderId: data.riderId,
      pickup: data.pickup,
      dropoff: data.dropoff
    });
  }
});

const PORT = process.env.PORT || 3000;
server.listen(PORT, () => {
  console.log(`WebSocket server running on port ${PORT}`);
});
```

**Why Node.js for WebSocket?**
- ✅ Event-driven, non-blocking I/O (perfect for WebSocket)
- ✅ Socket.io library (fallbacks, reconnection, rooms)
- ✅ Can handle 10K+ concurrent connections per server
- ✅ Low memory footprint per connection
- ❌ Single-threaded (use cluster mode for multi-core)

---

## 2. Databases & Storage

### **PostgreSQL** (User Data, Trips, Transactions)

**Schema Design with Partitioning:**

```sql
-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis";

-- Users table (riders + drivers)
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    uuid UUID DEFAULT uuid_generate_v4() UNIQUE,
    email VARCHAR(255) UNIQUE NOT NULL,
    phone VARCHAR(20) UNIQUE NOT NULL,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    user_type VARCHAR(10) CHECK (user_type IN ('rider', 'driver')),
    rating_avg DECIMAL(3,2) DEFAULT 5.00,
    total_trips INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_phone ON users(phone);
CREATE INDEX idx_users_uuid ON users(uuid);

-- Driver details
CREATE TABLE drivers (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id),
    license_number VARCHAR(50) UNIQUE,
    car_model VARCHAR(100),
    car_plate VARCHAR(20),
    car_color VARCHAR(30),
    car_year INT,
    status VARCHAR(20) DEFAULT 'offline',
    current_location GEOGRAPHY(POINT, 4326),
    acceptance_rate DECIMAL(5,2) DEFAULT 100.00,
    cancellation_rate DECIMAL(5,2) DEFAULT 0.00,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_drivers_user_id ON drivers(user_id);
CREATE INDEX idx_drivers_location ON drivers USING GIST(current_location);

-- Trips table (partitioned by month)
CREATE TABLE trips (
    id BIGSERIAL,
    uuid UUID DEFAULT uuid_generate_v4() UNIQUE,
    rider_id BIGINT REFERENCES users(id),
    driver_id BIGINT REFERENCES users(id),
    status VARCHAR(20) DEFAULT 'REQUESTED',
    pickup_location GEOGRAPHY(POINT, 4326),
    pickup_address TEXT,
    dropoff_location GEOGRAPHY(POINT, 4326),
    dropoff_address TEXT,
    requested_at TIMESTAMP,
    accepted_at TIMESTAMP,
    arrived_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    distance_km DECIMAL(8,2),
    duration_minutes INT,
    base_fare DECIMAL(10,2),
    surge_multiplier DECIMAL(3,2) DEFAULT 1.00,
    total_fare DECIMAL(10,2),
    payment_method VARCHAR(20),
    payment_status VARCHAR(20) DEFAULT 'PENDING',
    created_at TIMESTAMP DEFAULT NOW(),
    PRIMARY KEY (id, created_at)
) PARTITION BY RANGE (created_at);

-- Create monthly partitions (automate this with pg_partman)
CREATE TABLE trips_2024_11 PARTITION OF trips
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');

CREATE TABLE trips_2024_12 PARTITION OF trips
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

-- Indexes on partitions
CREATE INDEX idx_trips_2024_11_rider ON trips_2024_11(rider_id);
CREATE INDEX idx_trips_2024_11_driver ON trips_2024_11(driver_id);
CREATE INDEX idx_trips_2024_11_status ON trips_2024_11(status);
CREATE INDEX idx_trips_2024_11_created ON trips_2024_11(created_at DESC);

-- Payment transactions
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT NOT NULL,
    amount DECIMAL(10,2),
    currency VARCHAR(3) DEFAULT 'USD',
    payment_method VARCHAR(20),
    stripe_payment_id VARCHAR(100),
    status VARCHAR(20) DEFAULT 'PENDING',
    idempotency_key UUID UNIQUE,
    retries INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_payments_trip_id ON payments(trip_id);
CREATE INDEX idx_payments_status ON payments(status);
CREATE INDEX idx_payments_idempotency ON payments(idempotency_key);
```

**PostgreSQL Configuration for High Write Throughput:**

```conf
# postgresql.conf

# Memory
shared_buffers = 32GB              # 25% of RAM
effective_cache_size = 96GB        # 75% of RAM
work_mem = 128MB
maintenance_work_mem = 4GB

# Checkpoints
checkpoint_completion_target = 0.9
max_wal_size = 8GB
min_wal_size = 2GB
wal_buffers = 64MB

# Parallelism
max_parallel_workers_per_gather = 4
max_parallel_workers = 16
max_worker_processes = 16

# Connections
max_connections = 500
```

---

### **TimescaleDB** (Time-Series Location Data)

Hyper table for storing driver and rider location history.

```sql
-- Enable TimescaleDB extension
CREATE EXTENSION IF NOT EXISTS timescaledb;

-- Location history table
CREATE TABLE location_history (
    time TIMESTAMPTZ NOT NULL,
    user_id BIGINT NOT NULL,
    user_type VARCHAR(10),
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    heading DOUBLE PRECISION,
    speed DOUBLE PRECISION,
    accuracy DOUBLE PRECISION,
    trip_id BIGINT
);

-- Convert to hypertable (partitioned by time)
SELECT create_hypertable('location_history', 'time', 
    chunk_time_interval => INTERVAL '1 hour');

-- Create indexes
CREATE INDEX idx_location_user_time ON location_history (user_id, time DESC);
CREATE INDEX idx_location_trip ON location_history (trip_id, time DESC);

-- Compression policy (compress chunks older than 24 hours)
ALTER TABLE location_history SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'user_id'
);

SELECT add_compression_policy('location_history', INTERVAL '24 hours');

-- Retention policy (drop chunks older than 30 days)
SELECT add_retention_policy('location_history', INTERVAL '30 days');

-- Query example: Get driver's trip route
SELECT 
    time,
    latitude,
    longitude,
    speed
FROM location_history
WHERE user_id = 12345 
  AND trip_id = 67890
ORDER BY time ASC;
```

---

### **MongoDB** (Flexible Schema Data)

Used for reviews, driver earnings, notifications, etc.

```javascript
// Reviews collection
db.createCollection("reviews");

db.reviews.createIndex({ "trip_id": 1 });
db.reviews.createIndex({ "rider_id": 1, "created_at": -1 });
db.reviews.createIndex({ "driver_id": 1, "created_at": -1 });

// Sample review document
{
  "_id": ObjectId("..."),
  "trip_id": NumberLong(123456),
  "rider_id": NumberLong(789),
  "driver_id": NumberLong(456),
  "rating": 5,
  "review_text": "Great driver, very professional!",
  "tags": ["clean car", "safe driving", "friendly"],
  "created_at": ISODate("2024-11-15T10:30:00Z")
}

// Driver earnings collection (sharded by driver_id)
sh.enableSharding("uber");
sh.shardCollection("uber.earnings", { "driver_id": "hashed" });

db.earnings.createIndex({ "driver_id": 1, "date": -1 });

{
  "_id": ObjectId("..."),
  "driver_id": NumberLong(456),
  "date": ISODate("2024-11-15"),
  "trips": [
    {
      "trip_id": NumberLong(123456),
      "fare": 15.50,
      "commission": 3.10,
      "earnings": 12.40,
      "timestamp": ISODate("2024-11-15T08:30:00Z")
    }
  ],
  "total_earnings": 245.60,
  "total_trips": 18,
  "online_hours": 8.5
}
```

---

## 3. Real-Time Communication

### **WebSocket Infrastructure**

**Scaling WebSocket Servers:**

```yaml
# kubernetes/websocket-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: websocket-server
spec:
  replicas: 50  # 50 servers × 10K connections = 500K concurrent
  selector:
    matchLabels:
      app: websocket-server
  template:
    metadata:
      labels:
        app: websocket-server
    spec:
      containers:
      - name: websocket
        image: uber/websocket-server:v1.0.0
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: REDIS_URL
          value: "redis://redis-cluster:6379"
        - name: MAX_CONNECTIONS
          value: "10000"
        ports:
        - containerPort: 3000
          name: websocket
---
apiVersion: v1
kind: Service
metadata:
  name: websocket-service
spec:
  type: LoadBalancer
  sessionAffinity: ClientIP  # Sticky sessions for WebSocket
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 3600
  selector:
    app: websocket-server
  ports:
  - protocol: TCP
    port: 80
    targetPort: 3000
```

---

## 4. Caching & In-Memory Stores

### **Redis Cluster** (Geospatial + Session Storage)

**Redis Cluster Configuration:**

```conf
# redis.conf
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
maxmemory 64gb
maxmemory-policy allkeys-lru

# Optimize for geospatial queries
hash-max-ziplist-entries 512
hash-max-ziplist-value 64
```

**Redis Usage Patterns:**

```python
import redis
from redis.cluster import RedisCluster

# Connect to Redis Cluster
redis_cluster = RedisCluster(
    host='redis-cluster.default.svc.cluster.local',
    port=6379,
    decode_responses=True
)

# 1. Geospatial indexing for drivers
def add_driver_location(driver_id, lat, lon):
    redis_cluster.geoadd("drivers:online", (lon, lat, driver_id))
    redis_cluster.expire("drivers:online:" + driver_id, 30)

def find_nearby_drivers(lat, lon, radius_km=5):
    return redis_cluster.georadius(
        "drivers:online",
        lon, lat,
        radius_km,
        unit='km',
        withdist=True,
        sort='ASC'
    )

# 2. Session management
def create_user_session(user_id, token):
    session_data = {
        "user_id": user_id,
        "logged_in_at": time.time(),
        "device_type": "mobile"
    }
    redis_cluster.setex(f"session:{token}", 86400, json.dumps(session_data))

# 3. Rate limiting
def check_rate_limit(user_id, limit=100, window=3600):
    key = f"rate_limit:{user_id}"
    current = redis_cluster.incr(key)
    
    if current == 1:
        redis_cluster.expire(key, window)
    
    return current <= limit

# 4. Distributed locking (prevent double-booking)
from redis.lock import Lock

def acquire_driver_lock(driver_id, timeout=10):
    lock = redis_cluster.lock(f"lock:driver:{driver_id}", timeout=timeout)
    return lock.acquire(blocking=True, blocking_timeout=5)
```

---

## 5. Message Queues & Event Streaming

### **Apache Kafka** (Event Sourcing)

**Kafka Topics for Ride-Sharing:**

```bash
# Create topics with appropriate partitions
kafka-topics --create --topic ride.requested --partitions 24 --replication-factor 3
kafka-topics --create --topic ride.matched --partitions 24 --replication-factor 3
kafka-topics --create --topic ride.started --partitions 12 --replication-factor 3
kafka-topics --create --topic ride.completed --partitions 12 --replication-factor 3
kafka-topics --create --topic payment.initiated --partitions 12 --replication-factor 3
kafka-topics --create --topic payment.completed --partitions 12 --replication-factor 3
kafka-topics --create --topic driver.location --partitions 48 --replication-factor 3
```

**Kafka Producer (Go):**

```go
package main

import (
    "encoding/json"
    "github.com/confluentinc/confluent-kafka-go/kafka"
)

type RideRequestedEvent struct {
    TripID      string  `json:"trip_id"`
    RiderID     string  `json:"rider_id"`
    PickupLat   float64 `json:"pickup_lat"`
    PickupLon   float64 `json:"pickup_lon"`
    DropoffLat  float64 `json:"dropoff_lat"`
    DropoffLon  float64 `json:"dropoff_lon"`
    RequestedAt int64   `json:"requested_at"`
}

func publishRideRequest(producer *kafka.Producer, event RideRequestedEvent) error {
    topic := "ride.requested"
    
    // Serialize event
    value, _ := json.Marshal(event)
    
    // Produce message with key = rider_id (for ordering)
    return producer.Produce(&kafka.Message{
        TopicPartition: kafka.TopicPartition{
            Topic:     &topic,
            Partition: kafka.PartitionAny,
        },
        Key:   []byte(event.RiderID),
        Value: value,
    }, nil)
}
```

**Kafka Consumer (Go):**

```go
func consumeRideRequests() {
    consumer, _ := kafka.NewConsumer(&kafka.ConfigMap{
        "bootstrap.servers": "kafka:9092",
        "group.id":          "matching-service",
        "auto.offset.reset": "earliest",
    })
    
    consumer.Subscribe("ride.requested", nil)
    
    for {
        msg, err := consumer.ReadMessage(-1)
        if err != nil {
            continue
        }
        
        var event RideRequestedEvent
        json.Unmarshal(msg.Value, &event)
        
        // Process ride request (find drivers, match, dispatch)
        go handleRideRequest(event)
    }
}
```

---

## 6. Geospatial & Routing

### **OpenStreetMap + OSRM** (Self-Hosted Routing Engine)

Uber and Lyft build their own routing engines to avoid expensive API costs.

**OSRM Setup (Docker):**

```yaml
# docker-compose-osrm.yml
version: '3'
services:
  osrm-backend:
    image: osrm/osrm-backend:latest
    volumes:
      - ./osm-data:/data
    command: osrm-routed --algorithm mld /data/us-west.osrm
    ports:
      - "5000:5000"
```

**Download OSM Data and Build:**

```bash
# Download OpenStreetMap data for US West
wget https://download.geofabrik.de/north-america/us-west-latest.osm.pbf

# Extract, partition, and customize
docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-extract -p /opt/car.lua /data/us-west-latest.osm.pbf
docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-partition /data/us-west-latest.osrm
docker run -t -v "${PWD}:/data" osrm/osrm-backend osrm-customize /data/us-west-latest.osrm

# Run OSRM server
docker run -t -i -p 5000:5000 -v "${PWD}:/data" osrm/osrm-backend osrm-routed --algorithm mld /data/us-west-latest.osrm
```

**OSRM API Usage:**

```python
import requests

def get_route_osrm(pickup_lat, pickup_lon, dropoff_lat, dropoff_lon):
    """
    Get route from self-hosted OSRM server
    """
    url = f"http://osrm-server:5000/route/v1/driving/{pickup_lon},{pickup_lat};{dropoff_lon},{dropoff_lat}"
    
    params = {
        "overview": "full",
        "geometries": "geojson",
        "steps": "true"
    }
    
    response = requests.get(url, params=params)
    data = response.json()
    
    if data['code'] == 'Ok':
        route = data['routes'][0]
        return {
            "distance_km": route['distance'] / 1000,
            "duration_sec": route['duration'],
            "geometry": route['geometry'],
            "steps": route['legs'][0]['steps']
        }
    
    return None

# Calculate ETA
route = get_route_osrm(37.7749, -122.4194, 37.8049, -122.4194)
print(f"Distance: {route['distance_km']:.2f} km")
print(f"ETA: {route['duration_sec'] / 60:.1f} minutes")
```

**Cost Savings:**

- Google Maps Directions API: $5 per 1,000 requests
- Self-hosted OSRM: ~$500/month infrastructure (for US coverage)
- Break-even: 100K requests/day
- Uber scale: 2B requests/day → saves $10M/day!

---

### **S2 Geometry** (Uber's Geospatial Library)

```go
import "github.com/golang/geo/s2"

// Convert lat/lon to S2 Cell ID
func latLonToS2Cell(lat, lon float64, level int) s2.CellID {
    latLng := s2.LatLngFromDegrees(lat, lon)
    cellID := s2.CellIDFromLatLng(latLng).Parent(level)
    return cellID
}

// Find nearby cells for querying
func getNearbyS2Cells(lat, lon float64, radius float64) []s2.CellID {
    center := s2.PointFromLatLng(s2.LatLngFromDegrees(lat, lon))
    coverer := &s2.RegionCoverer{
        MaxLevel: 15,
        MaxCells: 8,
    }
    
    cap := s2.CapFromCenterAngle(center, s1.Angle(radius/6371)) // radius in km
    covering := coverer.Covering(cap)
    
    return covering
}
```

---

## 7. Payment Processing

### **Stripe Integration**

```python
import stripe
from uuid import uuid4

stripe.api_key = "sk_live_..."

def charge_rider(trip_id, rider_id, amount, currency="USD"):
    """
    Charge rider with idempotency to prevent double charging
    """
    # Idempotency key ensures charging only once even if retried
    idempotency_key = f"trip:{trip_id}"
    
    try:
        # Get rider's payment method from database
        payment_method = get_rider_payment_method(rider_id)
        
        # Create payment intent
        intent = stripe.PaymentIntent.create(
            amount=int(amount * 100),  # Convert to cents
            currency=currency,
            payment_method=payment_method,
            customer=get_rider_stripe_customer_id(rider_id),
            confirm=True,
            description=f"Ride payment for trip {trip_id}",
            idempotency_key=idempotency_key
        )
        
        if intent.status == 'succeeded':
            # Update payment status in database
            update_payment_status(trip_id, 'COMPLETED', intent.id)
            return {"success": True, "payment_id": intent.id}
        else:
            return {"success": False, "status": intent.status}
            
    except stripe.error.CardError as e:
        # Card declined
        return {"success": False, "error": str(e)}
    
    except stripe.error.StripeError as e:
        # Other Stripe errors
        return {"success": False, "error": str(e)}

# Webhook handler for async payment confirmations
@app.post("/webhooks/stripe")
def stripe_webhook(request):
    payload = request.body
    sig_header = request.headers.get('Stripe-Signature')
    
    try:
        event = stripe.Webhook.construct_event(
            payload, sig_header, STRIPE_WEBHOOK_SECRET
        )
    except ValueError:
        return {"error": "Invalid payload"}, 400
    
    if event.type == 'payment_intent.succeeded':
        payment_intent = event.data.object
        # Update database
        mark_payment_completed(payment_intent.id)
    
    elif event.type == 'payment_intent.payment_failed':
        payment_intent = event.data.object
        # Retry or notify rider
        handle_payment_failure(payment_intent.id)
    
    return {"success": True}
```

---

## 8. Machine Learning & AI

### **ETA Prediction Model**

```python
# train_eta_model.py
import pandas as pd
import numpy as np
from sklearn.ensemble import GradientBoostingRegressor
from sklearn.model_selection import train_test_split
import joblib

# Load historical trip data
df = pd.read_parquet('s3://uber-data/trips/2024/*.parquet')

# Feature engineering
df['hour'] = pd.to_datetime(df['pickup_time']).dt.hour
df['day_of_week'] = pd.to_datetime(df['pickup_time']).dt.dayofweek
df['distance'] = haversine_distance(
    df['pickup_lat'], df['pickup_lon'],
    df['dropoff_lat'], df['dropoff_lon']
)

# Features
features = [
    'distance', 'hour', 'day_of_week',
    'pickup_lat', 'pickup_lon',
    'dropoff_lat', 'dropoff_lon',
    'traffic_factor', 'weather_condition'
]

X = df[features]
y = df['actual_duration_minutes']

# Split
X_train, X_test, y_train, y_test = train_test_split(X, y, test_size=0.2)

# Train model
model = GradientBoostingRegressor(
    n_estimators=500,
    learning_rate=0.1,
    max_depth=5,
    random_state=42
)

model.fit(X_train, y_train)

# Evaluate
score = model.score(X_test, y_test)
print(f"R² Score: {score:.3f}")

# Save model
joblib.dump(model, 'models/eta_prediction_model.pkl')
```

---

## 9. Mobile Technologies

### **React Native** (Cross-Platform Mobile App)

```typescript
// RideRequestScreen.tsx
import React, { useState, useEffect } from 'react';
import { View, Button } from 'react-native';
import MapView, { Marker } from 'react-native-maps';
import io from 'socket.io-client';

const RideRequestScreen = ({ userId }) => {
  const [socket, setSocket] = useState(null);
  const [currentLocation, setCurrentLocation] = useState(null);
  const [driverLocation, setDriverLocation] = useState(null);
  
  useEffect(() => {
    // Connect to WebSocket server
    const ws = io('wss://websocket.uber.com/rider', {
      query: { riderId: userId },
      transports: ['websocket']
    });
    
    ws.on('connect', () => {
      console.log('Connected to WebSocket');
    });
    
    // Listen for driver location updates
    ws.on('driver_location', (data) => {
      setDriverLocation({ latitude: data.lat, longitude: data.lon });
    });
    
    setSocket(ws);
    
    return () => ws.disconnect();
  }, []);
  
  const requestRide = () => {
    socket.emit('request_ride', {
      pickup_lat: currentLocation.latitude,
      pickup_lon: currentLocation.longitude,
      dropoff_lat: destination.latitude,
      dropoff_lon: destination.longitude
    });
  };
  
  return (
    <View>
      <MapView>
        <Marker coordinate={currentLocation} title="You" />
        {driverLocation && (
          <Marker coordinate={driverLocation} title="Driver" />
        )}
      </MapView>
      <Button title="Request Ride" onPress={requestRide} />
    </View>
  );
};
```

---

## 10. Cloud Infrastructure

### **AWS Multi-Region Architecture**

```
┌─────────────────────────────────────────────────┐
│  Route 53 (Latency-based Routing)               │
└──────────────────┬──────────────────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ US-West      │      │ US-East      │
│ Region       │      │ Region       │
└──────┬───────┘      └──────┬───────┘
       │                     │
       ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ ALB          │      │ ALB          │
└──────┬───────┘      └──────┬───────┘
       │                     │
       ▼                     ▼
┌──────────────────────────────────────┐
│ EKS Cluster (Kubernetes)             │
│ - API Services (50 pods)             │
│ - WebSocket Servers (100 pods)       │
│ - Matching Service (20 pods)         │
│ - Payment Service (10 pods)          │
└──────────────┬───────────────────────┘
               │
     ┌─────────┴─────────┐
     ▼                   ▼
┌──────────┐      ┌──────────────┐
│ RDS      │      │ ElastiCache  │
│ Postgres │      │ Redis        │
└──────────┘      └──────────────┘
     │                   │
     ▼                   ▼
┌──────────┐      ┌──────────────┐
│ Aurora   │      │ MSK (Kafka)  │
│ (Replica)│      └──────────────┘
└──────────┘
```

---

## 11. DevOps & CI/CD

### **GitHub Actions Pipeline**

```yaml
# .github/workflows/deploy.yml
name: Deploy Ride Matching Service

on:
  push:
    branches: [main]
    paths:
      - 'services/matching/**'

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Go
        uses: actions/setup-go@v3
        with:
          go-version: 1.21
      
      - name: Run Tests
        run: |
          cd services/matching
          go test ./... -v -cover
      
      - name: Build Docker Image
        run: |
          docker build -t uber/matching-service:${{ github.sha }} .
      
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker push uber/matching-service:${{ github.sha }}
      
      - name: Deploy to EKS
        run: |
          kubectl set image deployment/matching-service matching=uber/matching-service:${{ github.sha }}
          kubectl rollout status deployment/matching-service
```

---

## 12. Monitoring & Observability

### **Prometheus Metrics**

```go
package metrics

import (
    "github.com/prometheus/client_golang/prometheus"
    "github.com/prometheus/client_golang/prometheus/promauto"
)

var (
    // Location update metrics
    LocationUpdatesTotal = promauto.NewCounter(prometheus.CounterOpts{
        Name: "location_updates_total",
        Help: "Total number of location updates received",
    })
    
    // Ride matching metrics
    RideMatchingDuration = promauto.NewHistogram(prometheus.HistogramOpts{
        Name:    "ride_matching_duration_seconds",
        Help:    "Time taken to match rider with driver",
        Buckets: prometheus.ExponentialBuckets(0.1, 2, 10),
    })
    
    // Payment metrics
    PaymentAttempts = promauto.NewCounterVec(prometheus.CounterOpts{
        Name: "payment_attempts_total",
        Help: "Total payment attempts",
    }, []string{"status"})
    
    // Active rides gauge
    ActiveRides = promauto.NewGauge(prometheus.GaugeOpts{
        Name: "active_rides_count",
        Help: "Current number of active rides",
    })
)
```

---

## 13. Security & Compliance

### **API Authentication with JWT**

```go
package auth

import (
    "github.com/golang-jwt/jwt/v5"
    "time"
)

func GenerateToken(userID string, userType string) (string, error) {
    claims := jwt.MapClaims{
        "user_id":   userID,
        "user_type": userType,
        "exp":       time.Now().Add(24 * time.Hour).Unix(),
    }
    
    token := jwt.NewWithClaims(jwt.SigningMethodHS256, claims)
    return token.SignedString([]byte(JWT_SECRET))
}

func ValidateToken(tokenString string) (*jwt.MapClaims, error) {
    token, err := jwt.Parse(tokenString, func(token *jwt.Token) (interface{}, error) {
        return []byte(JWT_SECRET), nil
    })
    
    if err != nil || !token.Valid {
        return nil, err
    }
    
    claims := token.Claims.(jwt.MapClaims)
    return &claims, nil
}
```

---

## 📚 Summary: Complete Tech Stack

### **Backend Services**
- **Languages**: Go (70%), Python (20%), Node.js (10%)
- **Frameworks**: Gin, FastAPI, Socket.io
- **Microservices**: 50+ services (matching, pricing, payment, etc.)

### **Databases**
- **Relational**: PostgreSQL (user data, trips)
- **Time-Series**: TimescaleDB (location history)
- **NoSQL**: MongoDB (reviews, earnings)
- **Cache**: Redis Cluster (geospatial, sessions)

### **Real-Time**
- **WebSocket**: Socket.io on Node.js
- **Pub/Sub**: Redis Pub/Sub, Kafka
- **Event Streaming**: Apache Kafka

### **Geospatial**
- **Routing**: OSRM (self-hosted)
- **Geometry**: S2 Geometry Library
- **Indexing**: Redis Geospatial, PostGIS

### **Infrastructure**
- **Cloud**: AWS (primary), GCP (backup)
- **Orchestration**: Kubernetes (EKS)
- **CI/CD**: GitHub Actions, Spinnaker
- **IaC**: Terraform

### **Monitoring**
- **Metrics**: Prometheus + Grafana
- **Logging**: ELK Stack
- **Tracing**: Jaeger
- **Alerting**: PagerDuty

---

**This tech stack powers ride-sharing platforms at global scale handling 40M+ rides per day!** 🚗💨

