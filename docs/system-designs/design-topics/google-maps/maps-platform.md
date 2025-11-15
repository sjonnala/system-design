# Google Maps Platform: System Design

## Overview

Google Maps is one of the most complex and widely-used mapping platforms globally, serving **over 1 billion users** and handling **billions of requests per day**. The platform encompasses multiple services: map tiles, routing/directions, geocoding, places search, real-time traffic, Street View, and satellite imagery.

**Key Metrics:**
- **1B+ users** worldwide
- **5M+ websites and apps** use Google Maps APIs
- **25M+ map updates per day** from users
- **99% of inhabited Earth** covered
- **220+ countries and territories**
- **Real-time traffic** for 60+ countries

---

## Table of Contents

1. [Requirements](#requirements)
2. [Capacity Estimation](#capacity-estimation)
3. [High-Level Architecture](#high-level-architecture)
4. [Data Models](#data-models)
5. [Core Components](#core-components)
6. [APIs](#apis)
7. [Map Tile System](#map-tile-system)
8. [Routing Engine](#routing-engine)
9. [Real-Time Traffic](#real-time-traffic)
10. [Scalability & Optimizations](#scalability--optimizations)

---

## 1. Requirements

### Functional Requirements

1. **Map Rendering**
   - Display interactive maps with zoom levels 0-22
   - Support both raster and vector tiles
   - Pan, zoom, rotate, tilt capabilities
   - Multiple map types (roadmap, satellite, terrain, hybrid)

2. **Directions & Routing**
   - Calculate optimal routes between two or more points
   - Support multiple travel modes (driving, walking, bicycling, transit)
   - Provide turn-by-turn navigation
   - Show alternative routes
   - Real-time ETA updates

3. **Geocoding**
   - Convert addresses to coordinates (geocoding)
   - Convert coordinates to addresses (reverse geocoding)
   - Handle fuzzy address matching
   - Support international address formats

4. **Places API**
   - Search for businesses, landmarks, POIs
   - Autocomplete for place search
   - Place details (photos, reviews, hours, contact)
   - Nearby search based on location

5. **Real-Time Traffic**
   - Display current traffic conditions
   - Predict future traffic based on historical data
   - Incident reporting (accidents, road closures)
   - Live location sharing

6. **Street View**
   - 360° panoramic street-level imagery
   - Navigate through connected panoramas
   - Historical Street View
   - Indoor imagery for select locations

### Non-Functional Requirements

1. **Performance**
   - Map tiles: <200ms load time (P95)
   - Directions API: <500ms response time
   - Geocoding: <300ms response time
   - 99.9% uptime SLA

2. **Scalability**
   - Handle 10B+ tile requests/day
   - Support 100M+ concurrent users
   - Serve maps for entire world (510M km²)

3. **Consistency**
   - Map data updates propagate within 24 hours
   - Traffic data updates every 30-60 seconds
   - Eventually consistent for user-contributed data

4. **Availability**
   - 99.99% availability for critical services
   - Multi-region redundancy
   - Graceful degradation

5. **Accuracy**
   - GPS accuracy: <10 meters
   - Address accuracy: >95%
   - ETA accuracy: ±15% under normal conditions

---

## 2. Capacity Estimation

### Traffic Assumptions

| Metric | Value | Notes |
|--------|-------|-------|
| **Daily Active Users (DAU)** | 200M | Conservative estimate |
| **Map Sessions/User/Day** | 3 | Average usage |
| **Session Duration** | 5 min | Average |
| **Tile Requests/Session** | 50 | Pan, zoom interactions |
| **Directions Requests/Day** | 100M | ~50% of users request directions |
| **Geocoding Requests/Day** | 500M | Search, autocomplete |
| **Places API Requests/Day** | 300M | Business search |
| **Traffic Updates/sec** | 1M | From mobile devices |

### Storage Estimation

**Map Tiles:**
- Zoom levels: 0-22 (23 levels)
- Tiles at zoom 22: 4^22 ≈ 17.5 trillion tiles (entire world)
- Average tile size: 50 KB (vector) or 15 KB (raster, compressed)
- **Total (if fully rendered):** 17.5T × 15 KB = 262 PB

**Realistic Storage (selective pre-rendering):**
- Pre-render zoom 0-15 globally: ~1 PB
- On-demand render zoom 16-22 for populated areas
- Cache hot tiles: 100 TB
- **Total map tiles:** ~1.1 PB

**Street View:**
- 10M miles of streets captured
- 360° panoramas every 10 meters
- ~50M panoramas
- Each panorama: 2 MB (compressed)
- **Total:** 50M × 2 MB = 100 TB

**Road Network Graph:**
- 50M miles of roads globally
- Each road segment: 500 bytes (geometry + metadata)
- ~100M road segments
- **Total:** 100M × 500 bytes = 50 GB

**Places Database:**
- 200M businesses/POIs worldwide
- Each place: 5 KB (name, address, coords, photos, reviews)
- **Total:** 200M × 5 KB = 1 TB

**Traffic Data:**
- Active road segments: 10M (high traffic areas)
- Speed data per segment: 100 bytes
- Historical data: 90 days retention
- **Total:** 10M × 100 bytes × (86,400 / 60) × 90 = 1.3 TB

**Total Storage:** ~1.2 PB (mostly map tiles)

### Bandwidth Estimation

**Tile Requests:**
- 200M users × 3 sessions/day × 50 tiles = 30B tiles/day
- 30B / 86,400 sec ≈ 350K tiles/sec
- 350K × 15 KB = 5.25 GB/sec
- **Daily bandwidth:** 5.25 GB/sec × 86,400 = 454 TB/day

**CDN reduces this by 95%:**
- Origin servers: 23 TB/day (cache misses)
- **CDN:** 431 TB/day (served from edge)

### QPS Estimation

| API | Requests/Day | QPS (avg) | Peak QPS |
|-----|--------------|-----------|----------|
| Map Tiles | 30B | 350K | 700K |
| Directions | 100M | 1,157 | 2,500 |
| Geocoding | 500M | 5,787 | 12,000 |
| Places API | 300M | 3,472 | 7,000 |
| Traffic Updates | 86.4M | 1M writes/sec | 3M |

---

## 3. High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                     CLIENT APPLICATIONS                      │
│  Web (maps.google.com) | Mobile Apps | Embedded Maps        │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                    GLOBAL CDN (Edge Caching)                 │
│  CloudFlare / Google CDN - Serves 95% of tile requests      │
└────────────────────────┬────────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────────┐
│                     API GATEWAY / LOAD BALANCER              │
│  Rate Limiting | Auth | Routing | Monitoring                │
└────┬─────────┬──────────┬───────────┬───────────┬──────────┘
     │         │          │           │           │
     ▼         ▼          ▼           ▼           ▼
┌─────────┐┌──────────┐┌──────────┐┌─────────┐┌──────────┐
│  TILES  ││DIRECTIONS││GEOCODING ││ PLACES  ││ TRAFFIC  │
│ SERVICE ││ SERVICE  ││ SERVICE  ││ SERVICE ││ SERVICE  │
└────┬────┘└─────┬────┘└─────┬────┘└────┬────┘└─────┬────┘
     │           │            │          │           │
     ▼           ▼            ▼          ▼           ▼
┌──────────────────────────────────────────────────────────┐
│                    CACHING LAYER (Redis Cluster)          │
│  Tile Cache | Route Cache | Geocode Cache | Place Cache  │
└────────────────────┬─────────────────────────────────────┘
                     │
        ┌────────────┼────────────┬───────────┐
        ▼            ▼            ▼           ▼
┌────────────┐┌──────────┐┌────────────┐┌──────────┐
│   Tile     ││  Graph   ││  Places    ││ Traffic  │
│  Storage   ││ Database ││  Database  ││   DB     │
│  (S3/GCS)  ││(Neo4j/PG)││(Elasticsearch)│(TimescaleDB)
└────────────┘└──────────┘└────────────┘└──────────┘
        │                       │
        ▼                       ▼
┌────────────┐          ┌────────────┐
│  Street    │          │   User     │
│   View     │          │ Locations  │
│  Storage   │          │   (Kafka)  │
└────────────┘          └────────────┘
```

---

## 4. Data Models

### Map Tile Schema

```javascript
// Tile Coordinate System
{
  "z": 15,           // Zoom level (0-22)
  "x": 16384,        // Column (0 to 2^z - 1)
  "y": 10896,        // Row (0 to 2^z - 1)
  "format": "mvt",   // Mapbox Vector Tile or "png" for raster
  "url": "https://mt1.google.com/vt?x=16384&y=10896&z=15",
  "etag": "abc123",  // Cache validation
  "last_modified": "2024-11-15T10:00:00Z"
}

// Vector Tile Data (MVT format)
{
  "layers": [
    {
      "name": "roads",
      "features": [
        {
          "geometry": {
            "type": "LineString",
            "coordinates": [[lon, lat], [lon, lat], ...]
          },
          "properties": {
            "name": "Main Street",
            "type": "primary",
            "oneway": true,
            "speed_limit": 50
          }
        }
      ]
    },
    {
      "name": "buildings",
      "features": [...]
    },
    {
      "name": "water",
      "features": [...]
    }
  ]
}
```

### Road Network Graph (Neo4j / PostgreSQL)

```sql
-- Nodes table (intersections, junctions)
CREATE TABLE road_nodes (
    id BIGSERIAL PRIMARY KEY,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    geom GEOGRAPHY(POINT, 4326),
    node_type VARCHAR(20), -- intersection, junction, toll
    elevation INT,
    created_at TIMESTAMP
);

-- Edges table (road segments)
CREATE TABLE road_edges (
    id BIGSERIAL PRIMARY KEY,
    from_node_id BIGINT REFERENCES road_nodes(id),
    to_node_id BIGINT REFERENCES road_nodes(id),
    geometry GEOGRAPHY(LINESTRING, 4326),
    distance_meters DECIMAL(10, 2),
    road_name VARCHAR(255),
    road_type VARCHAR(50), -- highway, arterial, local
    speed_limit INT,
    oneway BOOLEAN DEFAULT false,
    toll BOOLEAN DEFAULT false,
    travel_time_seconds INT, -- base travel time
    live_speed INT, -- current speed (updated by traffic)
    restriction JSONB, -- turn restrictions, time restrictions
    created_at TIMESTAMP
);

CREATE INDEX idx_road_edges_from ON road_edges(from_node_id);
CREATE INDEX idx_road_edges_to ON road_edges(to_node_id);
CREATE INDEX idx_road_edges_geom ON road_edges USING GIST(geometry);
```

### Places Database (Elasticsearch)

```json
{
  "place_id": "ChIJ...",
  "name": "Golden Gate Bridge",
  "location": {
    "lat": 37.8199,
    "lon": -122.4783
  },
  "address": {
    "street": "Golden Gate Bridge",
    "city": "San Francisco",
    "state": "CA",
    "country": "US",
    "postal_code": "94129"
  },
  "types": ["landmark", "tourist_attraction", "point_of_interest"],
  "rating": 4.8,
  "user_ratings_total": 125643,
  "photos": [
    "https://maps.googleapis.com/photo?photoreference=abc123"
  ],
  "opening_hours": {
    "open_now": true,
    "periods": [...]
  },
  "phone": "+1 415-555-1234",
  "website": "https://goldengatebridge.org",
  "price_level": 0
}
```

### Traffic Data (TimescaleDB)

```sql
CREATE TABLE traffic_speeds (
    time TIMESTAMPTZ NOT NULL,
    edge_id BIGINT NOT NULL,
    average_speed INT, -- km/h
    congestion_level INT, -- 0-4 (free flow to severe)
    incident BOOLEAN DEFAULT false,
    num_probes INT, -- number of GPS samples
    PRIMARY KEY (time, edge_id)
);

SELECT create_hypertable('traffic_speeds', 'time', 
    chunk_time_interval => INTERVAL '1 hour');

CREATE INDEX idx_traffic_edge_time ON traffic_speeds (edge_id, time DESC);

-- Retention policy (30 days of granular data)
SELECT add_retention_policy('traffic_speeds', INTERVAL '30 days');

-- Continuous aggregate for historical patterns
CREATE MATERIALIZED VIEW traffic_hourly
WITH (timescaledb.continuous) AS
SELECT 
    time_bucket('1 hour', time) AS hour,
    edge_id,
    AVG(average_speed) AS avg_speed,
    MIN(average_speed) AS min_speed,
    MAX(average_speed) AS max_speed,
    AVG(congestion_level) AS avg_congestion
FROM traffic_speeds
GROUP BY hour, edge_id;
```

---

## 5. Core Components

### 5.1 Tile Service

**Responsibilities:**
- Serve map tiles at various zoom levels
- Support both raster (PNG) and vector (MVT) formats
- Handle tile coordinate transformations
- Cache tiles at edge locations

**Tile Coordinate System:**

Google Maps uses the **Mercator projection** with a tile pyramid:
- Zoom 0: 1 tile (entire world)
- Zoom z: 2^z × 2^z tiles

```
Tile coordinates (x, y, z) to lat/lon:
  n = 2^z
  lon = x / n * 360.0 - 180.0
  lat_rad = atan(sinh(π * (1 - 2 * y / n)))
  lat = lat_rad * 180.0 / π
```

**Tile Generation:**

```python
from PIL import Image
import mapnik

def generate_raster_tile(z, x, y):
    """
    Generate raster PNG tile for given coordinates
    """
    # Create map object
    m = mapnik.Map(256, 256)
    mapnik.load_map(m, 'styles/openstreetmap.xml')
    
    # Calculate bounding box
    bbox = tile_to_bbox(z, x, y)
    m.zoom_to_box(bbox)
    
    # Render to image
    img = mapnik.Image(256, 256)
    mapnik.render(m, img)
    
    # Save to PNG
    return img.tostring('png')

def tile_to_bbox(z, x, y):
    """Convert tile coordinates to geographic bounding box"""
    n = 2 ** z
    lon_deg_left = x / n * 360.0 - 180.0
    lon_deg_right = (x + 1) / n * 360.0 - 180.0
    lat_rad_top = math.atan(math.sinh(math.pi * (1 - 2 * y / n)))
    lat_rad_bottom = math.atan(math.sinh(math.pi * (1 - 2 * (y + 1) / n)))
    lat_deg_top = math.degrees(lat_rad_top)
    lat_deg_bottom = math.degrees(lat_rad_bottom)
    
    return mapnik.Box2d(lon_deg_left, lat_deg_bottom, lon_deg_right, lat_deg_top)
```

### 5.2 Routing Engine

**Algorithm: A* with Contraction Hierarchies**

Google Maps uses advanced routing algorithms for fast shortest path computation:

1. **Preprocessing:**
   - Build Contraction Hierarchies (CH) for road network
   - Pre-compute shortcuts for frequently traveled routes
   - Store hierarchical graph (reduces search space by 1000×)

2. **Query-time:**
   - Bidirectional A* search
   - Use live traffic data as edge weights
   - Consider turn restrictions, time-dependent routing

```python
import heapq
from collections import defaultdict

class RoutingEngine:
    def __init__(self, graph):
        self.graph = graph  # Adjacency list with edge weights
        
    def a_star_routing(self, start, goal):
        """
        A* algorithm for shortest path routing
        """
        # Priority queue: (f_score, node)
        open_set = [(0, start)]
        came_from = {}
        
        g_score = defaultdict(lambda: float('inf'))
        g_score[start] = 0
        
        f_score = defaultdict(lambda: float('inf'))
        f_score[start] = self.heuristic(start, goal)
        
        while open_set:
            current_f, current = heapq.heappop(open_set)
            
            if current == goal:
                return self.reconstruct_path(came_from, current)
            
            for neighbor, edge_data in self.graph[current]:
                # Edge weight considers: distance, traffic, road type
                weight = self.calculate_edge_cost(edge_data)
                tentative_g = g_score[current] + weight
                
                if tentative_g < g_score[neighbor]:
                    came_from[neighbor] = current
                    g_score[neighbor] = tentative_g
                    f_score[neighbor] = tentative_g + self.heuristic(neighbor, goal)
                    heapq.heappush(open_set, (f_score[neighbor], neighbor))
        
        return None  # No path found
    
    def heuristic(self, node, goal):
        """Haversine distance heuristic"""
        lat1, lon1 = node.lat, node.lon
        lat2, lon2 = goal.lat, goal.lon
        return haversine_distance(lat1, lon1, lat2, lon2)
    
    def calculate_edge_cost(self, edge_data):
        """
        Calculate edge cost considering:
        - Base distance
        - Current traffic speed
        - Road type
        - Tolls
        """
        base_time = edge_data['distance'] / edge_data['speed_limit']
        
        # Adjust for live traffic
        if edge_data.get('live_speed'):
            actual_time = edge_data['distance'] / edge_data['live_speed']
        else:
            actual_time = base_time
        
        # Penalty for tolls
        if edge_data.get('toll'):
            actual_time *= 1.2
        
        return actual_time
    
    def reconstruct_path(self, came_from, current):
        """Build path from start to goal"""
        path = [current]
        while current in came_from:
            current = came_from[current]
            path.append(current)
        return path[::-1]
```

### 5.3 Geocoding Service

**Forward Geocoding (Address → Coordinates):**

```python
from elasticsearch import Elasticsearch

es = Elasticsearch(['localhost:9200'])

def geocode_address(address_string):
    """
    Convert address to coordinates using fuzzy matching
    """
    # Clean and parse address
    parsed = parse_address(address_string)
    
    # Elasticsearch query with fuzzy matching
    query = {
        "query": {
            "bool": {
                "should": [
                    {
                        "match": {
                            "full_address": {
                                "query": address_string,
                                "fuzziness": "AUTO",
                                "boost": 3
                            }
                        }
                    },
                    {
                        "multi_match": {
                            "query": parsed['street'],
                            "fields": ["street_name^2", "street_number"],
                            "fuzziness": 1
                        }
                    },
                    {
                        "term": {
                            "postal_code": parsed.get('postal_code')
                        }
                    }
                ],
                "filter": [
                    {
                        "term": {
                            "country": parsed.get('country', 'US')
                        }
                    }
                ]
            }
        },
        "size": 5
    }
    
    results = es.search(index='addresses', body=query)
    
    if results['hits']['total']['value'] > 0:
        top_result = results['hits']['hits'][0]['_source']
        return {
            "lat": top_result['location']['lat'],
            "lon": top_result['location']['lon'],
            "formatted_address": top_result['full_address'],
            "confidence": results['hits']['hits'][0]['_score']
        }
    
    return None
```

**Reverse Geocoding (Coordinates → Address):**

```python
def reverse_geocode(lat, lon):
    """
    Find nearest address to given coordinates
    """
    query = {
        "query": {
            "bool": {
                "filter": {
                    "geo_distance": {
                        "distance": "100m",
                        "location": {
                            "lat": lat,
                            "lon": lon
                        }
                    }
                }
            }
        },
        "sort": [
            {
                "_geo_distance": {
                    "location": {
                        "lat": lat,
                        "lon": lon
                    },
                    "order": "asc",
                    "unit": "m"
                }
            }
        ],
        "size": 1
    }
    
    results = es.search(index='addresses', body=query)
    
    if results['hits']['total']['value'] > 0:
        return results['hits']['hits'][0]['_source']
    
    return {"formatted_address": "Unknown location"}
```

### 5.4 Real-Time Traffic Processing

**Data Sources:**
1. **Mobile GPS probes** (anonymized location data from Android devices)
2. **Historical traffic patterns**
3. **User-reported incidents**
4. **Road sensors** (in some regions)

**Traffic Processing Pipeline:**

```python
from kafka import KafkaConsumer, KafkaProducer
import json
from collections import defaultdict
import time

class TrafficProcessor:
    def __init__(self):
        self.consumer = KafkaConsumer(
            'user_locations',
            bootstrap_servers=['localhost:9092'],
            value_deserializer=lambda m: json.loads(m.decode('utf-8'))
        )
        self.producer = KafkaProducer(
            bootstrap_servers=['localhost:9092'],
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )
        
        # Buffer for aggregating GPS samples
        self.edge_samples = defaultdict(list)
    
    def process_location_updates(self):
        """
        Process streaming GPS location data from mobile devices
        """
        for message in self.consumer:
            location_data = message.value
            
            # Map-match location to road edge
            edge_id = self.map_match(
                location_data['lat'],
                location_data['lon'],
                location_data['heading']
            )
            
            if edge_id:
                # Calculate speed
                speed = location_data.get('speed', 0)  # m/s
                speed_kmh = speed * 3.6
                
                # Add sample to buffer
                self.edge_samples[edge_id].append({
                    'speed': speed_kmh,
                    'timestamp': time.time()
                })
                
                # Process buffer every 30 seconds
                if self.should_process_buffer(edge_id):
                    self.calculate_traffic_speed(edge_id)
    
    def map_match(self, lat, lon, heading):
        """
        Match GPS point to road network (simplified)
        """
        # Find nearby road edges within 50 meters
        nearby_edges = self.find_nearby_edges(lat, lon, radius=50)
        
        if not nearby_edges:
            return None
        
        # Filter by heading (if available)
        if heading is not None:
            nearby_edges = [
                e for e in nearby_edges
                if abs(e['heading'] - heading) < 45
            ]
        
        # Return closest edge
        return min(nearby_edges, key=lambda e: e['distance'])['edge_id']
    
    def calculate_traffic_speed(self, edge_id):
        """
        Calculate average speed for road segment
        """
        samples = self.edge_samples[edge_id]
        
        if len(samples) < 3:
            return  # Not enough data
        
        # Calculate median speed (more robust than mean)
        speeds = sorted([s['speed'] for s in samples])
        median_speed = speeds[len(speeds) // 2]
        
        # Determine congestion level
        edge_data = self.get_edge_data(edge_id)
        speed_limit = edge_data['speed_limit']
        
        congestion = self.classify_congestion(median_speed, speed_limit)
        
        # Publish to traffic topic
        self.producer.send('traffic_updates', {
            'edge_id': edge_id,
            'average_speed': median_speed,
            'congestion_level': congestion,
            'num_samples': len(samples),
            'timestamp': time.time()
        })
        
        # Clear buffer
        self.edge_samples[edge_id] = []
    
    def classify_congestion(self, actual_speed, speed_limit):
        """
        Classify congestion level based on speed ratio
        """
        ratio = actual_speed / speed_limit if speed_limit > 0 else 1
        
        if ratio >= 0.85:
            return 0  # Free flow (green)
        elif ratio >= 0.65:
            return 1  # Moderate (yellow)
        elif ratio >= 0.45:
            return 2  # Heavy (orange)
        elif ratio >= 0.25:
            return 3  # Severe (red)
        else:
            return 4  # Standstill (dark red)
```

---

## 6. APIs

### 6.1 Maps JavaScript API

```html
<!DOCTYPE html>
<html>
<head>
  <script src="https://maps.googleapis.com/maps/api/js?key=YOUR_API_KEY"></script>
  <script>
    function initMap() {
      const map = new google.maps.Map(document.getElementById('map'), {
        center: { lat: 37.7749, lng: -122.4194 },
        zoom: 13,
        mapTypeId: 'roadmap'
      });
      
      // Add marker
      const marker = new google.maps.Marker({
        position: { lat: 37.7749, lng: -122.4194 },
        map: map,
        title: 'San Francisco'
      });
      
      // Add traffic layer
      const trafficLayer = new google.maps.TrafficLayer();
      trafficLayer.setMap(map);
    }
  </script>
</head>
<body onload="initMap()">
  <div id="map" style="height: 600px; width: 100%;"></div>
</body>
</html>
```

### 6.2 Directions API

```bash
# Request
GET https://maps.googleapis.com/maps/api/directions/json?
  origin=San+Francisco,CA&
  destination=Los+Angeles,CA&
  mode=driving&
  departure_time=now&
  traffic_model=best_guess&
  key=YOUR_API_KEY

# Response
{
  "routes": [
    {
      "summary": "I-5 S",
      "legs": [
        {
          "distance": {
            "text": "383 mi",
            "value": 616293
          },
          "duration": {
            "text": "5 hours 47 mins",
            "value": 20820
          },
          "duration_in_traffic": {
            "text": "6 hours 15 mins",
            "value": 22500
          },
          "start_address": "San Francisco, CA, USA",
          "end_address": "Los Angeles, CA, USA",
          "steps": [
            {
              "html_instructions": "Head <b>south</b> on <b>Van Ness Ave</b>",
              "distance": { "text": "0.3 mi", "value": 482 },
              "duration": { "text": "2 mins", "value": 120 },
              "polyline": { "points": "encoded_polyline_string" }
            }
          ]
        }
      ],
      "overview_polyline": { "points": "encoded_route_string" }
    }
  ]
}
```

### 6.3 Geocoding API

```bash
# Forward Geocoding
GET https://maps.googleapis.com/maps/api/geocode/json?
  address=1600+Amphitheatre+Parkway,+Mountain+View,+CA&
  key=YOUR_API_KEY

# Response
{
  "results": [
    {
      "formatted_address": "1600 Amphitheatre Parkway, Mountain View, CA 94043, USA",
      "geometry": {
        "location": {
          "lat": 37.4224764,
          "lng": -122.0842499
        },
        "location_type": "ROOFTOP"
      },
      "place_id": "ChIJ2eUgeAK6j4ARbn5u_wAGqWA",
      "types": ["street_address"]
    }
  ]
}
```

---

## 7. Map Tile System

### Tile Pyramid Structure

```
Zoom 0:  1 tile     (entire world)
Zoom 1:  4 tiles    (2×2)
Zoom 2:  16 tiles   (4×4)
...
Zoom 15: 1,073,741,824 tiles (32,768×32,768)
Zoom 22: 17,592,186,044,416 tiles (4,194,304×4,194,304)
```

### Tile URL Pattern

```
https://mt{0-3}.google.com/vt?x={x}&y={y}&z={z}

Example:
https://mt1.google.com/vt?x=16384&y=10896&z=15
```

**Load balancing across 4 subdomains:** `mt0`, `mt1`, `mt2`, `mt3`

### Vector Tiles vs Raster Tiles

| Aspect | Raster Tiles | Vector Tiles |
|--------|--------------|--------------|
| **Format** | PNG, JPEG | Mapbox Vector Tile (MVT), GeoJSON |
| **File Size** | 10-30 KB | 20-100 KB |
| **Quality** | Fixed resolution, pixelated on zoom | Scalable, crisp at any zoom |
| **Styling** | Pre-rendered | Client-side styling (dynamic) |
| **Bandwidth** | Higher (more tiles needed) | Lower (fewer requests) |
| **Use Case** | Satellite imagery | Road maps, custom themes |

---

## 8. Routing Engine

### Dijkstra's Algorithm (Baseline)

```
Time Complexity: O(E + V log V) with binary heap
Space Complexity: O(V)

For Google Maps scale:
  V = 100M nodes (intersections)
  E = 200M edges (road segments)
  
Query time: ~10 seconds (too slow!)
```

### Contraction Hierarchies (Production)

**Preprocessing (done once):**
1. Iteratively remove least important nodes
2. Add shortcuts to preserve shortest paths
3. Build node hierarchy

**Query time:** O(log V) = ~27 hops for 100M nodes
**Speed improvement:** 1000× faster than Dijkstra

```python
class ContractionHierarchies:
    def __init__(self, graph):
        self.graph = graph
        self.shortcuts = {}
        self.node_levels = {}
        
    def preprocess(self):
        """
        Build contraction hierarchies (offline)
        """
        remaining_nodes = set(self.graph.nodes())
        level = 0
        
        while remaining_nodes:
            # Find least important node
            node = self.select_node_to_contract(remaining_nodes)
            
            # Contract node (add shortcuts)
            self.contract_node(node, level)
            
            self.node_levels[node] = level
            remaining_nodes.remove(node)
            level += 1
    
    def query(self, start, goal):
        """
        Bidirectional search on contracted graph
        """
        # Forward search (only upward edges)
        forward_dist, forward_parent = self.dijkstra_upward(start)
        
        # Backward search (only upward edges)
        backward_dist, backward_parent = self.dijkstra_upward(goal)
        
        # Find meeting point with minimum distance
        min_dist = float('inf')
        meeting_point = None
        
        for node in forward_dist:
            if node in backward_dist:
                total_dist = forward_dist[node] + backward_dist[node]
                if total_dist < min_dist:
                    min_dist = total_dist
                    meeting_point = node
        
        # Reconstruct path
        return self.reconstruct_path(
            start, meeting_point, goal,
            forward_parent, backward_parent
        )
```

---

## 9. Real-Time Traffic

### Traffic Data Collection

**Sources:**
1. **GPS probes from mobile devices** (primary source)
   - 1M+ location updates/second
   - Anonymized, aggregated by road segment
   
2. **Historical patterns**
   - Average speeds by hour, day of week
   - Predictive modeling

3. **User-reported incidents**
   - Accidents, construction, road closures

### Traffic Visualization

**Color Coding:**
- 🟢 **Green:** Free flow (>85% of speed limit)
- 🟡 **Yellow:** Moderate traffic (65-85%)
- 🟠 **Orange:** Heavy traffic (45-65%)
- 🔴 **Red:** Severe congestion (25-45%)
- 🔴 **Dark Red:** Standstill (<25%)

---

## 10. Scalability & Optimizations

### 10.1 CDN Strategy

- **Edge caching:** 95% cache hit rate for tiles
- **Geographic distribution:** 200+ edge locations globally
- **Tile pre-warming:** Pre-cache popular tiles during off-peak
- **Adaptive TTL:** High-zoom tiles (cities) = 1 hour, low-zoom = 1 week

### 10.2 Database Sharding

**Road Graph Sharding:**
- Shard by geographic region (S2 cells)
- Each shard covers ~1000 km² region
- Co-locate related shards in same datacenter

**Places Database:**
- Shard by country or geohash prefix
- Elasticsearch with 12 shards per index

### 10.3 Caching Strategy

**Multi-Tier Caching:**

```
L1: Browser cache (tiles, routes)     → 1-hour TTL
L2: CDN edge (CloudFlare)              → 24-hour TTL
L3: Redis cluster (directions, geocoding) → 5-minute TTL
L4: Database (source of truth)
```

### 10.4 Rendering Optimization

**Vector Tiles:**
- Client-side rendering using WebGL (GPU acceleration)
- Reduces server load by 10×
- Enables dynamic styling, rotation, 3D buildings

**Level of Detail (LOD):**
- Zoom 0-5: Show only major highways, countries
- Zoom 6-10: Add cities, primary roads
- Zoom 11-15: Add local streets, POIs
- Zoom 16-22: Add building footprints, house numbers

---

## Summary

Google Maps is a **massively distributed system** handling:
- **10B+ tile requests/day**
- **100M+ direction queries/day**
- **1M+ traffic updates/second**
- **1.2 PB of map data**

**Key Technologies:**
- **Tile Pyramid:** Mercator projection, 23 zoom levels
- **Vector Tiles:** MVT format, client-side rendering
- **Routing:** Contraction Hierarchies (1000× faster than Dijkstra)
- **Traffic:** Real-time GPS aggregation, ML-based prediction
- **CDN:** 95% cache hit rate, 200+ edge locations

**Challenges:**
1. **Scale:** Serving entire world with <200ms latency
2. **Freshness:** Map updates propagate globally in 24 hours
3. **Accuracy:** 10-meter GPS precision, >95% address matching
4. **Complexity:** Integrating maps, routing, traffic, places, Street View

This design represents one of the most complex distributed systems ever built, powering navigation for over 1 billion users daily.

---

**Related System Designs:**
- [Uber/Lyft Ride-Sharing](../uber-lyft/ride-sharing.md)
- [Location-Based Services (Yelp)](../location-services/)
- [Content Delivery Network (CDN)](../cdn/)
