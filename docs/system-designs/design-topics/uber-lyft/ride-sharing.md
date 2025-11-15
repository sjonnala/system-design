# Design a Ride-Sharing Service (Uber / Lyft)

**Companies**: Uber, Lyft, Grab, DiDi, Ola
**Difficulty**: Expert
**Time**: 60-90 minutes

## Problem Statement

Design a ride-sharing platform like Uber or Lyft that connects riders with nearby drivers in real-time. The system should handle millions of concurrent users, support real-time location tracking, efficient driver-rider matching, dynamic pricing, and provide a seamless experience for both riders and drivers.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Rider Flow**:
   - Request a ride (select pickup/destination, car type)
   - View nearby available drivers in real-time
   - Get fare estimate before confirming
   - Track driver location during pickup
   - Rate driver after trip completion

2. **Driver Flow**:
   - Go online/offline
   - Receive ride requests
   - Accept/reject rides
   - Navigate to pickup location
   - Start/end trip
   - View earnings

3. **Core Features**:
   - **Real-time Location Tracking**: Track riders and drivers with <1s delay
   - **Driver-Rider Matching**: Match riders with optimal nearby drivers
   - **ETA Calculation**: Accurate arrival time predictions
   - **Dynamic Pricing**: Surge pricing during high demand
   - **Trip Management**: Start, navigate, complete trips
   - **Payment Processing**: Multiple payment methods
   - **Ratings & Reviews**: Two-way rating system
   - **Trip History**: Past trips for riders and drivers

**Prioritize**: Real-time tracking, matching algorithm, and ETA calculation for MVP

### Non-Functional Requirements

1. **Low Latency**:
   - Location updates: <1 second
   - Driver matching: <5 seconds
   - ETA calculation: <2 seconds

2. **High Availability**: 99.99% uptime (critical for transportation)

3. **Scalability**:
   - Support 100M+ active users
   - 10M+ concurrent rides
   - 500K location updates/second

4. **Accuracy**:
   - Location precision: <10 meters
   - ETA accuracy: ±3 minutes
   - Price calculation: 100% accurate

5. **Reliability**: No lost ride requests, guaranteed payment processing

6. **Data Consistency**: Eventually consistent for non-critical data (trip history), strongly consistent for payments

---

## Step 2: Capacity Estimation

### Assumptions

**User Base**:
- Total users: 100M (riders + drivers)
- Daily Active Users (DAU): 20M riders, 2M drivers
- Concurrent rides at peak: 2M
- Average trip duration: 20 minutes
- Peak hours: 8-9 AM, 6-8 PM (2× average load)

**Traffic Patterns**:
```
Read Operations:
  - Location updates: 2M drivers × 1 update/5sec = 400K updates/sec
  - Driver location queries (from riders): 2M riders × 1 query/2sec = 1M queries/sec
  - Total read: ~1.4M QPS

Write Operations:
  - Trip creation: 2M trips/20min = 1,700 trips/sec
  - Location updates: 400K updates/sec
  - Payment transactions: 1,700/sec
  - Total write: ~402K QPS

Read:Write Ratio: ~3.5:1
```

**Storage Estimation**:
```
User Data:
  - 100M users × 1KB = 100 GB

Driver Location Data:
  - 2M active drivers × 100 bytes (lat, lon, heading, speed, timestamp)
  - Updates every 5 seconds
  - Store last 1 hour: 2M × 720 updates × 100 bytes = 144 GB
  - Use time-series DB with compression: ~30 GB active storage

Trip Data:
  - 10M trips/day × 365 days × 2 years = 7.3B trips
  - Per trip: 2KB (route, fare, duration, ratings)
  - Total: 7.3B × 2KB = 14.6 TB

Map Data:
  - Road network, traffic data: ~500 GB (cached from third-party)

Total Storage: ~15 TB (operational data)
```

**Bandwidth**:
```
Location Updates (Upload):
  - 400K updates/sec × 100 bytes = 40 MB/sec

Driver Location Queries (Download):
  - 1M queries/sec × 500 bytes (multiple drivers) = 500 MB/sec

Map Tiles:
  - 2M concurrent users × 50 KB/update × 1 update/min
  - = 1.7 GB/sec (must use CDN!)

Total Bandwidth: ~2.3 GB/sec (ingress + egress)
```

---

## Step 3: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│              RIDER APP          DRIVER APP              │
│           (iOS/Android)       (iOS/Android)             │
└────────────────────┬─────────────────┬──────────────────┘
                     │                 │
                     ▼                 ▼
┌─────────────────────────────────────────────────────────┐
│                  CDN (Map Tiles, Static Assets)         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│         API Gateway + Load Balancer (Geo-Routing)       │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┴────────────┐
        ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│  Rider Service   │      │  Driver Service  │
└────────┬─────────┘      └────────┬─────────┘
         │                         │
         └──────────┬──────────────┘
                    ▼
         ┌─────────────────────┐
         │  Matching Service   │
         │  (Core Algorithm)   │
         └──────────┬──────────┘
                    │
    ┌───────────────┼───────────────┐
    ▼               ▼               ▼
┌─────────┐  ┌─────────┐   ┌────────────┐
│Location │  │  Trip   │   │  Payment   │
│Service  │  │ Service │   │  Service   │
└────┬────┘  └────┬────┘   └─────┬──────┘
     │            │              │
     ▼            ▼              ▼
┌─────────┐  ┌─────────┐   ┌─────────┐
│Redis    │  │PostgreSQL│  │Stripe/  │
│(Geo)    │  │(Trips)  │  │Braintree│
└─────────┘  └─────────┘   └─────────┘
     │
     ▼
┌─────────────────────┐
│   Kafka (Events)    │
│ - location.updated  │
│ - trip.created      │
│ - trip.completed    │
└─────────────────────┘
```

### Key Components

#### 1. **Location Service** (Critical Component)

**Responsibilities**:
- Receive driver location updates (400K/sec)
- Store in geospatial database (Redis with Geo commands)
- Provide nearby driver queries
- Broadcast location to connected riders via WebSocket

**Technology**:
- **Redis Geospatial** for active driver locations
- **WebSocket** for real-time push to riders
- **Kafka** for location event streaming

**Implementation**:
```python
# Store driver location in Redis
def update_driver_location(driver_id, lat, lon, heading, speed):
    # Add to geospatial index
    redis.geoadd("drivers:online", lon, lat, driver_id)

    # Store additional metadata
    redis.hmset(f"driver:{driver_id}", {
        "lat": lat,
        "lon": lon,
        "heading": heading,
        "speed": speed,
        "updated_at": timestamp()
    })

    # Set TTL (if no update in 30s, driver is offline)
    redis.expire(f"driver:{driver_id}", 30)

    # Publish to WebSocket subscribers (riders tracking this driver)
    websocket.broadcast(f"driver:{driver_id}:location", {
        "lat": lat,
        "lon": lon,
        "heading": heading
    })

# Query nearby drivers
def get_nearby_drivers(lat, lon, radius_km):
    # Geo radius search in Redis
    drivers = redis.georadius(
        "drivers:online",
        lon, lat,
        radius_km,
        unit='km',
        withdist=True,
        withcoord=True,
        sort='ASC'
    )

    # Enrich with driver details
    result = []
    for driver_id, distance, (lat, lon) in drivers:
        driver_info = redis.hgetall(f"driver:{driver_id}")
        result.append({
            "driver_id": driver_id,
            "distance": distance,
            "location": {"lat": lat, "lon": lon},
            "heading": driver_info.get("heading"),
            "car_type": driver_info.get("car_type")
        })

    return result
```

---

#### 2. **Matching Service** (Core Algorithm)

**Challenge**: Match riders with optimal drivers in <5 seconds

**Factors for Matching**:
1. **Distance**: Primary factor (closest drivers)
2. **ETA**: Time to pickup location
3. **Driver Rating**: Quality filter (>4.5 stars)
4. **Driver Acceptance Rate**: Prefer reliable drivers
5. **Car Type**: Match rider preference (UberX, UberXL, etc.)
6. **Driver Direction**: Heading towards pickup (bonus)

**Algorithm**:
```python
def find_best_driver(pickup_lat, pickup_lon, car_type, max_radius=5):
    # Step 1: Get nearby drivers (radius search)
    candidates = get_nearby_drivers(pickup_lat, pickup_lon, max_radius)

    # Step 2: Filter by car type and availability
    candidates = [d for d in candidates
                  if d['car_type'] == car_type and d['status'] == 'available']

    if not candidates:
        return None  # No drivers available

    # Step 3: Calculate ETA for each driver
    for driver in candidates:
        driver['eta'] = calculate_eta(
            driver['location'],
            {'lat': pickup_lat, 'lon': pickup_lon},
            driver['heading'],
            driver['speed']
        )

    # Step 4: Score drivers (multi-factor)
    for driver in candidates:
        score = (
            100 / (1 + driver['distance']) +           # Distance: 40%
            50 / (1 + driver['eta']) +                 # ETA: 25%
            driver['rating'] * 10 +                    # Rating: 25%
            driver['acceptance_rate'] * 10 +           # Reliability: 10%
            (10 if is_heading_towards(driver) else 0)  # Bonus
        )
        driver['match_score'] = score

    # Step 5: Sort by score and return top driver
    candidates.sort(key=lambda d: d['match_score'], reverse=True)

    # Step 6: Send request to top 3 drivers (parallel dispatch)
    # First driver to accept wins
    return dispatch_to_drivers(candidates[:3])

def dispatch_to_drivers(drivers):
    # Send ride request to multiple drivers simultaneously
    # First to accept gets the ride (reduces wait time)
    for driver in drivers:
        send_push_notification(driver['id'], ride_request)

    # Wait for first acceptance (with 15s timeout)
    accepted_driver = wait_for_acceptance(timeout=15)

    if accepted_driver:
        # Cancel requests to other drivers
        for d in drivers:
            if d['id'] != accepted_driver['id']:
                cancel_request(d['id'])
        return accepted_driver

    # If no one accepts, expand radius and retry
    return find_best_driver(pickup_lat, pickup_lon, car_type, max_radius * 2)
```

**Optimization: Quadtree/S2 for Large Cities**

For dense cities (NYC, Tokyo), use hierarchical spatial index:
```python
# S2 Cell-based matching (Google's approach)
from s2sphere import CellId, LatLng

def find_drivers_s2(pickup_lat, pickup_lon):
    # Convert pickup to S2 cell
    pickup_cell = CellId.from_lat_lng(
        LatLng.from_degrees(pickup_lat, pickup_lon)
    ).parent(15)  # Level 15 ≈ 100m precision

    # Get neighboring cells
    neighbors = pickup_cell.get_all_neighbors(15)
    search_cells = [pickup_cell] + neighbors

    # Query drivers in these cells
    drivers = []
    for cell in search_cells:
        drivers.extend(get_drivers_in_cell(cell.id()))

    return drivers
```

---

#### 3. **ETA Calculation Service**

**Challenge**: Calculate accurate arrival time considering:
- Real-time traffic
- Road conditions
- Driver speed
- Historical patterns

**Implementation**:
```python
def calculate_eta(from_location, to_location, current_speed, heading):
    # Step 1: Get route from mapping service (Google Maps, Mapbox)
    route = map_service.get_route(
        from_location,
        to_location,
        mode='driving',
        traffic='best_guess'
    )

    # Step 2: Apply ML model for traffic prediction
    predicted_duration = ml_model.predict_duration(
        route=route,
        time_of_day=current_time(),
        day_of_week=current_day(),
        weather=get_weather(),
        historical_data=get_historical_traffic(route)
    )

    # Step 3: Factor in driver's current speed
    if current_speed > 0:
        # Driver is moving, adjust ETA
        distance_to_route = haversine_distance(
            from_location,
            route.start_point
        )
        time_to_route = distance_to_route / current_speed
        predicted_duration += time_to_route

    return {
        'eta_seconds': predicted_duration,
        'eta_minutes': round(predicted_duration / 60),
        'distance_km': route.distance / 1000,
        'route_polyline': route.polyline  # For map display
    }

# ML Model for ETA Prediction
class ETAPredictionModel:
    def __init__(self):
        # Trained on historical trip data
        self.model = load_trained_model('eta_model_v2.pkl')

    def predict_duration(self, route, time_of_day, day_of_week, weather, historical_data):
        features = {
            'distance': route.distance,
            'num_turns': len(route.turns),
            'num_traffic_lights': route.traffic_lights,
            'hour': time_of_day.hour,
            'day_of_week': day_of_week,
            'is_rush_hour': is_rush_hour(time_of_day),
            'weather_condition': weather.condition,
            'avg_historical_duration': historical_data.avg_duration,
            'current_traffic_level': get_traffic_level(route)
        }

        prediction = self.model.predict([features])
        return prediction[0]
```

---

#### 4. **Dynamic Pricing Service** (Surge Pricing)

**Algorithm**:
```python
def calculate_surge_multiplier(location, time):
    # Step 1: Get supply and demand
    num_riders_waiting = get_waiting_riders_count(location, radius=2km)
    num_available_drivers = get_available_drivers_count(location, radius=2km)

    # Step 2: Calculate demand/supply ratio
    if num_available_drivers == 0:
        return 3.0  # Max surge (no drivers)

    demand_supply_ratio = num_riders_waiting / num_available_drivers

    # Step 3: Apply surge formula
    if demand_supply_ratio < 1.0:
        surge = 1.0  # No surge (supply > demand)
    elif demand_supply_ratio < 2.0:
        surge = 1.0 + (demand_supply_ratio - 1.0) * 0.5  # 1.0 - 1.5×
    elif demand_supply_ratio < 3.0:
        surge = 1.5 + (demand_supply_ratio - 2.0) * 0.75  # 1.5 - 2.25×
    else:
        surge = 2.25 + min((demand_supply_ratio - 3.0) * 0.25, 0.75)  # 2.25 - 3.0× (capped)

    # Step 4: Apply historical patterns
    historical_surge = get_historical_surge(location, time.hour, time.day_of_week)
    surge = (surge + historical_surge) / 2  # Average with historical

    # Step 5: Cap maximum surge
    surge = min(surge, 3.0)

    return round(surge, 1)

# Real-time monitoring for surge activation
def monitor_surge_conditions():
    # Check every 30 seconds
    while True:
        for city_zone in all_zones:
            surge = calculate_surge_multiplier(city_zone, now())
            if surge > 1.0:
                activate_surge_pricing(city_zone, surge)
                notify_nearby_drivers(city_zone, surge)  # Incentivize drivers

        time.sleep(30)
```

---

#### 5. **Trip Management Service**

**State Machine**:
```
TRIP STATES:
  REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVED →
  TRIP_STARTED → TRIP_COMPLETED → PAYMENT_PROCESSED → RATED

Transitions:
  - REQUESTED: Rider requests ride
  - DRIVER_ASSIGNED: Driver accepts
  - DRIVER_ARRIVED: Driver reaches pickup
  - TRIP_STARTED: Rider gets in car
  - TRIP_COMPLETED: Destination reached
  - PAYMENT_PROCESSED: Payment successful
  - RATED: Both parties rated each other
```

**Implementation**:
```python
class TripStateMachine:
    def __init__(self, trip_id):
        self.trip_id = trip_id
        self.state = 'REQUESTED'

    def assign_driver(self, driver_id):
        if self.state != 'REQUESTED':
            raise InvalidStateTransition()

        # Update database
        db.update_trip(self.trip_id, {
            'driver_id': driver_id,
            'state': 'DRIVER_ASSIGNED',
            'assigned_at': timestamp()
        })

        # Notify rider via WebSocket
        websocket.send(rider_id, {
            'event': 'driver_assigned',
            'driver': get_driver_info(driver_id)
        })

        # Start ETA timer
        start_eta_tracking(self.trip_id)

        self.state = 'DRIVER_ASSIGNED'

    def start_trip(self):
        if self.state != 'DRIVER_ARRIVED':
            raise InvalidStateTransition()

        db.update_trip(self.trip_id, {
            'state': 'TRIP_STARTED',
            'started_at': timestamp()
        })

        # Start fare meter
        start_fare_calculation(self.trip_id)

        self.state = 'TRIP_STARTED'

    def complete_trip(self, final_location):
        if self.state != 'TRIP_STARTED':
            raise InvalidStateTransition()

        # Calculate final fare
        fare = calculate_final_fare(
            trip_id=self.trip_id,
            end_location=final_location
        )

        db.update_trip(self.trip_id, {
            'state': 'TRIP_COMPLETED',
            'completed_at': timestamp(),
            'final_fare': fare
        })

        # Trigger payment
        process_payment(self.trip_id, fare)

        self.state = 'TRIP_COMPLETED'
```

---

## Step 4: Data Model (10 min)

### Database Choice

**Primary Database: PostgreSQL**
- ACID transactions for payments
- Trip history with complex queries
- User profiles and authentication

**Redis (Geospatial + Cache)**
- Real-time driver locations (Redis Geo)
- Active trip state
- Driver availability status

**TimescaleDB (Time-Series)**
- Historical location data
- Analytics and reporting

### Schema Design

#### PostgreSQL Schema

```sql
-- Users table
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    phone VARCHAR(20) UNIQUE NOT NULL,
    email VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    user_type VARCHAR(10) CHECK (user_type IN ('rider', 'driver')),
    created_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE
);

-- Riders table
CREATE TABLE riders (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    payment_methods JSONB,  -- Stripe customer IDs, cards
    home_address JSONB,
    work_address JSONB,
    preferences JSONB
);

-- Drivers table
CREATE TABLE drivers (
    user_id BIGINT PRIMARY KEY REFERENCES users(id),
    license_number VARCHAR(50) UNIQUE,
    car_type VARCHAR(20),  -- uberx, uberxl, uberblack
    car_make VARCHAR(50),
    car_model VARCHAR(50),
    car_year INT,
    license_plate VARCHAR(20),
    rating_avg DECIMAL(2,1) DEFAULT 5.0,
    total_trips INT DEFAULT 0,
    acceptance_rate DECIMAL(3,2) DEFAULT 1.0,
    is_online BOOLEAN DEFAULT FALSE,
    current_location GEOGRAPHY(POINT, 4326),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_drivers_online ON drivers(is_online) WHERE is_online = TRUE;
CREATE INDEX idx_drivers_location ON drivers USING GIST (current_location) WHERE is_online = TRUE;

-- Trips table (partitioned by month)
CREATE TABLE trips (
    id BIGSERIAL,
    rider_id BIGINT REFERENCES riders(user_id),
    driver_id BIGINT REFERENCES drivers(user_id),
    state VARCHAR(20),
    car_type VARCHAR(20),

    -- Locations
    pickup_location GEOGRAPHY(POINT, 4326),
    pickup_address TEXT,
    dropoff_location GEOGRAPHY(POINT, 4326),
    dropoff_address TEXT,

    -- Route
    route_polyline TEXT,  -- Encoded polyline
    distance_km DECIMAL(10,2),

    -- Pricing
    base_fare DECIMAL(10,2),
    surge_multiplier DECIMAL(3,1),
    final_fare DECIMAL(10,2),

    -- Timestamps
    requested_at TIMESTAMP,
    assigned_at TIMESTAMP,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,

    -- Ratings
    rider_rating INT CHECK (rider_rating BETWEEN 1 AND 5),
    driver_rating INT CHECK (driver_rating BETWEEN 1 AND 5),

    created_at TIMESTAMP DEFAULT NOW()
) PARTITION BY RANGE (created_at);

-- Create partitions by month
CREATE TABLE trips_2025_01 PARTITION OF trips
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE INDEX idx_trips_rider ON trips(rider_id, created_at DESC);
CREATE INDEX idx_trips_driver ON trips(driver_id, created_at DESC);
CREATE INDEX idx_trips_state ON trips(state) WHERE state IN ('REQUESTED', 'DRIVER_ASSIGNED');

-- Payments table
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    trip_id BIGINT REFERENCES trips(id),
    amount DECIMAL(10,2),
    payment_method VARCHAR(20),  -- card, wallet, cash
    stripe_charge_id VARCHAR(100),
    status VARCHAR(20),  -- pending, completed, failed, refunded
    processed_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);
```

#### Redis Data Structures

```python
# Driver location (Geospatial)
# Key: "drivers:online"
# Data: GEOADD drivers:online <lon> <lat> <driver_id>

# Driver metadata
# Key: "driver:{driver_id}"
# Data: HASH {lat, lon, heading, speed, car_type, rating, updated_at}

# Active trip state
# Key: "trip:{trip_id}"
# Data: HASH {rider_id, driver_id, state, pickup_lat, pickup_lon, ...}

# Rider's pending request
# Key: "rider:{rider_id}:request"
# Data: STRING {trip_id}
# TTL: 5 minutes (auto-expire if not matched)

# Waiting riders in an area (for surge calculation)
# Key: "waiting_riders:{geohash_prefix}"
# Data: SET {rider_id1, rider_id2, ...}
# TTL: 10 minutes
```

---

## Step 5: APIs (5 min)

### Rider APIs

#### Request Ride
```http
POST /api/v1/rides/request

Request:
{
  "pickup": {
    "lat": 37.7749,
    "lon": -122.4194,
    "address": "123 Market St, San Francisco"
  },
  "dropoff": {
    "lat": 37.7849,
    "lon": -122.4094,
    "address": "456 Mission St, San Francisco"
  },
  "car_type": "uberx",
  "payment_method": "card_ending_1234"
}

Response: 201 Created
{
  "trip_id": "trip_abc123",
  "state": "REQUESTED",
  "estimated_fare": {
    "base": 8.50,
    "surge_multiplier": 1.5,
    "total": 12.75
  },
  "estimated_wait_time": 3,  // minutes
  "nearby_drivers_count": 5
}
```

#### Get Nearby Drivers (for map display)
```http
GET /api/v1/drivers/nearby?lat=37.7749&lon=-122.4194&radius=2

Response: 200 OK
{
  "drivers": [
    {
      "id": "driver_xyz",
      "location": {"lat": 37.7750, "lon": -122.4190},
      "heading": 45,
      "eta_minutes": 2,
      "car_type": "uberx",
      "rating": 4.8
    }
  ]
}
```

### Driver APIs

#### Update Location
```http
POST /api/v1/drivers/location

Request:
{
  "lat": 37.7750,
  "lon": -122.4190,
  "heading": 45,
  "speed": 25
}

Response: 200 OK
{
  "status": "location_updated",
  "timestamp": "2025-11-15T10:30:00Z"
}
```

#### Accept/Reject Ride
```http
POST /api/v1/rides/{trip_id}/accept

Response: 200 OK
{
  "trip_id": "trip_abc123",
  "rider": {
    "name": "John Doe",
    "phone": "+1234567890",
    "rating": 4.9
  },
  "pickup": {
    "lat": 37.7749,
    "lon": -122.4194,
    "address": "123 Market St"
  },
  "eta_to_pickup": 3
}
```

---

## Step 6: Optimizations & Scalability (15 min)

### 1. Real-Time Location Updates (WebSocket)

**Challenge**: 2M concurrent connections

**Solution**:
```python
# Use WebSocket for bidirectional communication
from fastapi import WebSocket

connected_clients = {}  # {user_id: websocket}

@app.websocket("/ws/{user_id}")
async def websocket_endpoint(websocket: WebSocket, user_id: str):
    await websocket.accept()
    connected_clients[user_id] = websocket

    try:
        while True:
            # Receive location updates from driver
            data = await websocket.receive_json()

            if data['type'] == 'location_update':
                # Update Redis
                update_driver_location(
                    driver_id=user_id,
                    lat=data['lat'],
                    lon=data['lon'],
                    heading=data['heading'],
                    speed=data['speed']
                )

                # Broadcast to rider tracking this driver
                trip = get_active_trip(driver_id=user_id)
                if trip:
                    rider_ws = connected_clients.get(trip.rider_id)
                    if rider_ws:
                        await rider_ws.send_json({
                            'type': 'driver_location',
                            'lat': data['lat'],
                            'lon': data['lon'],
                            'eta': calculate_eta_to_pickup(trip)
                        })

    except WebSocketDisconnect:
        del connected_clients[user_id]
```

**Scaling WebSockets**:
- Use Socket.IO with Redis adapter for horizontal scaling
- Shard connections by user_id hash
- Each WebSocket server handles 100K connections
- Need ~20 WebSocket servers for 2M connections

---

### 2. Geospatial Sharding

**Problem**: Single Redis instance can't handle 400K location updates/sec

**Solution**: Shard by geographic region

```python
# Geographic sharding (consistent hashing by lat/lon)
def get_redis_shard(lat, lon):
    # Divide world into regions
    region = get_region(lat, lon)  # e.g., "us_west", "eu_central"
    return redis_clusters[region]

def update_driver_location_sharded(driver_id, lat, lon, heading, speed):
    # Route to appropriate shard
    redis = get_redis_shard(lat, lon)
    redis.geoadd("drivers:online", lon, lat, driver_id)
    redis.hmset(f"driver:{driver_id}", {...})

# Each region has its own Redis cluster
redis_clusters = {
    "us_west": RedisCluster(nodes=["redis-us-west-1", "redis-us-west-2"]),
    "us_east": RedisCluster(nodes=["redis-us-east-1", "redis-us-east-2"]),
    "eu_central": RedisCluster(nodes=["redis-eu-1", "redis-eu-2"]),
    # ... more regions
}
```

---

### 3. Kafka for Event Streaming

**Use Cases**:
- Location updates (high volume)
- Trip state changes
- Analytics and monitoring

```python
# Kafka topics
topics = {
    "location.driver.updated": {
        "partitions": 50,  # High throughput
        "replication": 3
    },
    "trip.created": {
        "partitions": 10,
        "replication": 3
    },
    "trip.completed": {
        "partitions": 10,
        "replication": 3
    }
}

# Producer (Location Service)
def publish_location_update(driver_id, lat, lon, heading, speed):
    kafka_producer.send(
        "location.driver.updated",
        key=driver_id.encode(),
        value=json.dumps({
            "driver_id": driver_id,
            "lat": lat,
            "lon": lon,
            "heading": heading,
            "speed": speed,
            "timestamp": time.time()
        }).encode()
    )

# Consumer (Analytics Service)
def consume_location_updates():
    consumer = KafkaConsumer(
        "location.driver.updated",
        group_id="analytics-group",
        auto_offset_reset="latest"
    )

    for message in consumer:
        data = json.loads(message.value)
        # Store in TimescaleDB for historical analysis
        store_location_history(data)
```

---

### 4. Distributed Matching Service

**Problem**: Matching must be fast (<5s) even with millions of requests

**Solution**: Partition by city/region

```python
# City-based matching (each city has its own matching service)
matching_services = {
    "san_francisco": MatchingService(region="sf"),
    "new_york": MatchingService(region="nyc"),
    "london": MatchingService(region="ldn"),
    # ...
}

def request_ride(pickup_lat, pickup_lon, car_type):
    # Determine city from pickup location
    city = reverse_geocode_to_city(pickup_lat, pickup_lon)

    # Route to city-specific matching service
    matcher = matching_services.get(city)
    if not matcher:
        matcher = matching_services["default"]

    return matcher.find_best_driver(pickup_lat, pickup_lon, car_type)
```

---

### 5. Payment Processing (Idempotency)

**Challenge**: Ensure exactly-once payment even if request is retried

```python
def process_payment(trip_id, amount, payment_method):
    # Use trip_id as idempotency key
    idempotency_key = f"payment:{trip_id}"

    # Check if already processed
    if redis.exists(idempotency_key):
        # Return cached result
        return json.loads(redis.get(idempotency_key))

    try:
        # Process payment via Stripe
        charge = stripe.Charge.create(
            amount=int(amount * 100),  # cents
            currency="usd",
            customer=get_customer_id(trip.rider_id),
            idempotency_key=idempotency_key  # Stripe's idempotency
        )

        # Store result
        result = {
            "status": "success",
            "charge_id": charge.id,
            "amount": amount
        }

        # Cache for 24 hours
        redis.setex(idempotency_key, 86400, json.dumps(result))

        # Update database
        db.update_payment(trip_id, {
            "status": "completed",
            "stripe_charge_id": charge.id
        })

        return result

    except stripe.error.CardError as e:
        # Payment failed
        result = {"status": "failed", "error": str(e)}
        redis.setex(idempotency_key, 86400, json.dumps(result))
        return result
```

---

## Step 7: Advanced Features

### 1. Trip Prediction (ML)

**Predict demand hotspots to position drivers**:

```python
class DemandPredictionModel:
    def __init__(self):
        self.model = load_model('demand_prediction_v3.pkl')

    def predict_demand(self, location, time_ahead=30):
        """Predict demand in next 30 minutes"""
        features = {
            'hour': time.hour,
            'day_of_week': time.weekday(),
            'is_weekend': time.weekday() >= 5,
            'is_rush_hour': is_rush_hour(time),
            'weather': get_weather_forecast(location, time),
            'nearby_events': get_nearby_events(location, time),
            'historical_demand': get_historical_demand(location, time)
        }

        predicted_trips = self.model.predict([features])[0]
        return predicted_trips

# Suggest drivers to move to high-demand areas
def suggest_driver_positioning():
    for city in active_cities:
        # Predict demand for next 30 min
        hotspots = predict_demand_hotspots(city, time_ahead=30)

        # Find idle drivers
        idle_drivers = get_idle_drivers(city)

        # Send repositioning suggestions
        for driver in idle_drivers:
            nearest_hotspot = find_nearest(driver.location, hotspots)
            if haversine_distance(driver.location, nearest_hotspot) > 1:  # >1km away
                send_notification(driver.id, {
                    "type": "reposition_suggestion",
                    "location": nearest_hotspot,
                    "expected_wait_reduction": "5 minutes",
                    "incentive": "$3 bonus for next ride"
                })
```

---

### 2. Route Optimization (Multi-Stop)

**UberPool / Lyft Shared**:

```python
def optimize_shared_route(active_trips):
    """Optimize route for shared rides"""
    # Problem: Pick up multiple riders with minimal detour
    # Solution: Vehicle Routing Problem (VRP) with time windows

    from ortools.constraint_solver import routing_enums_pb2
    from ortools.constraint_solver import pywrapcp

    # Create routing model
    manager = pywrapcp.RoutingIndexManager(
        len(locations),  # all pickup/dropoff points
        1,  # one vehicle
        0   # depot (current location)
    )
    routing = pywrapcp.RoutingModel(manager)

    # Define cost function (travel time)
    def distance_callback(from_index, to_index):
        from_node = manager.IndexToNode(from_index)
        to_node = manager.IndexToNode(to_index)
        return distance_matrix[from_node][to_node]

    transit_callback_index = routing.RegisterTransitCallback(distance_callback)
    routing.SetArcCostEvaluatorOfAllVehicles(transit_callback_index)

    # Add time window constraints (pickup/dropoff windows)
    time_dimension = routing.GetDimensionOrDie('Time')
    for location_idx, time_window in enumerate(time_windows):
        index = manager.NodeToIndex(location_idx)
        time_dimension.CumulVar(index).SetRange(time_window[0], time_window[1])

    # Solve
    solution = routing.SolveWithParameters(search_parameters)

    # Extract optimal route
    route = []
    index = routing.Start(0)
    while not routing.IsEnd(index):
        route.append(manager.IndexToNode(index))
        index = solution.Value(routing.NextVar(index))

    return route
```

---

## Step 8: Monitoring & Reliability

### Key Metrics

```python
# Prometheus metrics
from prometheus_client import Counter, Histogram, Gauge

# Request metrics
ride_requests = Counter('ride_requests_total', 'Total ride requests', ['status'])
matching_duration = Histogram('matching_duration_seconds', 'Time to match driver')
location_updates = Counter('location_updates_total', 'Driver location updates')

# Business metrics
active_trips = Gauge('active_trips', 'Number of active trips')
available_drivers = Gauge('available_drivers', 'Number of online drivers', ['city'])
avg_wait_time = Histogram('avg_wait_time_minutes', 'Average rider wait time')
surge_areas = Gauge('surge_pricing_areas', 'Number of areas with surge pricing')

# System health
websocket_connections = Gauge('websocket_connections', 'Active WebSocket connections')
redis_latency = Histogram('redis_latency_ms', 'Redis operation latency')
db_query_duration = Histogram('db_query_duration_seconds', 'Database query time')
```

### Alerts

```yaml
# alertmanager.yml
groups:
  - name: uber_critical
    interval: 30s
    rules:
      - alert: HighMatchingLatency
        expr: histogram_quantile(0.95, matching_duration_seconds) > 10
        for: 5m
        annotations:
          summary: "Driver matching taking >10s (P95)"

      - alert: NoAvailableDrivers
        expr: available_drivers{city="san_francisco"} == 0
        for: 2m
        annotations:
          summary: "No drivers available in San Francisco"

      - alert: PaymentFailureRate
        expr: rate(payments_failed_total[5m]) / rate(payments_total[5m]) > 0.05
        for: 5m
        annotations:
          summary: "Payment failure rate >5%"
```

---

## Interview Tips

**Questions to Ask**:
- Scale expectations? (users, trips/day, geographic coverage)
- Real-time requirements? (location update frequency, matching latency)
- Payment processing scope? (integration vs building from scratch)
- Advanced features? (shared rides, scheduled rides, route optimization)

**Topics to Cover**:
1. **Geospatial indexing**: Redis Geo vs PostGIS vs S2
2. **Real-time communication**: WebSockets, long polling, or push notifications
3. **Matching algorithm**: Distance vs ETA vs multi-factor scoring
4. **Scalability**: Sharding strategies, database partitioning
5. **Consistency**: CAP theorem trade-offs, eventual vs strong consistency

**Common Follow-ups**:
- "How do you handle driver going offline mid-trip?"
  → State machine, automatic reassignment, rider notification
- "How do you prevent fraudulent rides?"
  → Device fingerprinting, velocity checks, ML fraud detection
- "How do you scale to 100 cities globally?"
  → Regional clusters, CDN for maps, multi-region deployment

---

## Key Takeaways

1. **Real-Time is Critical**: WebSockets + Redis for <1s location updates
2. **Geospatial Indexing**: Redis Geo for active data, PostGIS for historical
3. **Matching Algorithm**: Multi-factor scoring, parallel driver dispatch
4. **Event-Driven Architecture**: Kafka for scalability and async processing
5. **Sharding Strategy**: Geographic partitioning (city/region-based)
6. **ML for Optimization**: Demand prediction, ETA estimation, fraud detection

## Further Reading

- Uber Engineering Blog: "Engineering Efficiency Through Developer Joy"
- Lyft Engineering: "Building a Scalable Location Service"
- Google S2 Geometry Library
- "Designing Data-Intensive Applications" by Martin Kleppmann
- Redis Geospatial Commands Documentation
