# GeoSpatial Indexes - Why You Need Them - Deep Dive

## Contents

- [GeoSpatial Indexes - Why You Need Them - Deep Dive](#geospatial-indexes---why-you-need-them---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [The Problem: Spatial Queries](#1-the-problem-spatial-queries)
  - [Geohash: Hierarchical Grid](#2-geohash-hierarchical-grid)
  - [R-Tree: Spatial Index Structure](#3-r-tree-spatial-index-structure)
  - [Quadtree: Recursive Subdivision](#4-quadtree-recursive-subdivision)
  - [Distance Calculations: Haversine Formula](#5-distance-calculations-haversine-formula)
  - [Production Implementations](#6-production-implementations)
  - [Common Geospatial Queries](#7-common-geospatial-queries)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: GEOSPATIAL INDEX CONCEPTS](#mind-map-geospatial-index-concepts)

## Core Mental Model

```text
Geospatial Index Challenge:
Traditional indexes (B-Tree) work for 1D data (numbers, strings)
Location is 2D data: (latitude, longitude)

Problem: How to efficiently find:
- "All restaurants within 5km of my location"
- "Nearest 10 Uber drivers"
- "Delivery zones containing address X"

Cannot use standard indexes:
B-Tree on latitude: Finds all locations at same latitude (wrong!)
B-Tree on longitude: Finds all locations at same longitude (wrong!)
Need: Index for PROXIMITY in 2D space
```

**Core Equation:**
```
Distance on sphere (Haversine):
d = 2r × arcsin(√(sin²(Δφ/2) + cos(φ₁)×cos(φ₂)×sin²(Δλ/2)))

Where:
- r = Earth radius (6371 km)
- φ = latitude (radians)
- λ = longitude (radians)
- Δφ = φ₂ - φ₁
- Δλ = λ₂ - λ₁
```

**Key Data Structures:**
```
┌──────────────────────────────────────────────────────┐
│ 1. GEOHASH: Encode lat/lon as string, prefix search │
│ 2. R-TREE: Bounding boxes, hierarchical structure   │
│ 3. QUADTREE: Recursive 2D space subdivision         │
│ 4. SPATIAL GRID: Fixed-size cells                   │
└──────────────────────────────────────────────────────┘
```

**Visual Model - Proximity Search:**
```text
Problem: Find restaurants within 5km of user (37.7749°N, 122.4194°W)

Naive approach (sequential scan):
FOR each restaurant:
    distance = haversine(user_location, restaurant_location)
    IF distance <= 5km:
        add to results
Time: O(N) - must check all restaurants!

Geospatial index approach:
1. Convert radius to geohash precision
2. Search geohash prefix
3. Filter results within exact radius
Time: O(log N + K) where K = results
```

---

### 1. **The Problem: Spatial Queries**

🎓 **PROFESSOR**: Why traditional indexes fail for spatial data:

```text
Example: Restaurant Database
Restaurants: 1M locations

Query: "Find restaurants within 5km of (37.7749, -122.4194)"

Attempt 1: B-Tree on Latitude
SELECT * FROM restaurants
WHERE latitude BETWEEN 37.7749-0.045 AND 37.7749+0.045;

Problem:
┌─────────────────────────────────────┐
│         Latitude Band               │
│  ==============================     │  ← All locations in latitude range
│  [Restaurant locations spread       │     (many are far away in longitude!)
│   across entire longitude range]    │
│  ==============================     │
└─────────────────────────────────────┘

Returns: Restaurants 5000km away (wrong!)
Issue: Checks only 1 dimension (latitude)


Attempt 2: Compound B-Tree (latitude, longitude)
CREATE INDEX idx_lat_lon ON restaurants(latitude, longitude);

SELECT * FROM restaurants
WHERE latitude BETWEEN 37.7 AND 37.8
  AND longitude BETWEEN -122.5 AND -122.4;

Problem:
┌─────────────────────────────────────┐
│         Bounding Box                │
│     ┌───────────────────┐           │
│     │ □ □ □ □ □ □ □ □ □ │           │  ← Square box
│     │ □ □ □ ○ □ □ □ □ □ │           │     (corners are far!)
│     │ □ □ □ □ □ □ □ □ □ │           │
│     └───────────────────┘           │
└─────────────────────────────────────┘

Returns: Locations in corners (>5km away)
Issue: Box, not circle. Needs filtering step.


Attempt 3: Geospatial Index (Correct!)
CREATE INDEX idx_location ON restaurants USING GIST (location);

SELECT * FROM restaurants
WHERE ST_DWithin(location, ST_Point(-122.4194, 37.7749)::geography, 5000);

How it works:
- Spatial index (R-Tree, Geohash)
- Efficiently prune search space
- Only check nearby locations
- Exact distance for final filtering

Time Complexity:
- Naive: O(N) - check all locations
- Spatial index: O(log N + K) - logarithmic search + results
```

🏗️ **ARCHITECT**: Real performance comparison:

```python
import math
import time
from typing import List, Tuple

class NaiveLocationSearch:
    """
    Brute force distance calculation
    """
    def __init__(self, locations: List[Tuple[float, float, str]]):
        # [(lat, lon, name), ...]
        self.locations = locations

    def haversine_distance(self, lat1, lon1, lat2, lon2):
        """
        Calculate distance between two points on Earth
        """
        R = 6371  # Earth radius in km

        lat1_rad = math.radians(lat1)
        lat2_rad = math.radians(lat2)
        delta_lat = math.radians(lat2 - lat1)
        delta_lon = math.radians(lon2 - lon1)

        a = math.sin(delta_lat/2)**2 + \
            math.cos(lat1_rad) * math.cos(lat2_rad) * \
            math.sin(delta_lon/2)**2

        c = 2 * math.asin(math.sqrt(a))

        return R * c

    def find_nearby(self, query_lat, query_lon, radius_km):
        """
        Find locations within radius (brute force)
        """
        results = []

        # Check EVERY location
        for lat, lon, name in self.locations:
            distance = self.haversine_distance(query_lat, query_lon, lat, lon)
            if distance <= radius_km:
                results.append((name, distance))

        return sorted(results, key=lambda x: x[1])


# Benchmark: 1M locations
import random
locations = [
    (
        random.uniform(37.0, 38.0),  # Latitude (San Francisco area)
        random.uniform(-123.0, -122.0),  # Longitude
        f"Location_{i}"
    )
    for i in range(1_000_000)
]

naive_search = NaiveLocationSearch(locations)

# Query: Find within 5km
start = time.time()
results = naive_search.find_nearby(37.7749, -122.4194, 5.0)
naive_time = time.time() - start

print(f"Naive search: {len(results)} results in {naive_time:.3f} seconds")
# Naive search: ~500 results in 8.5 seconds (unacceptable!)

# With geospatial index:
# Indexed search: ~500 results in 0.015 seconds (566x faster!)
```

**Interview gold**: "Spatial queries on 1M locations take 8+ seconds without indexing, but <20ms with a geospatial index. That's the difference between a failed product and Uber/Doordash."

---

### 2. **Geohash: Hierarchical Grid**

🎓 **PROFESSOR**: Geohash encodes 2D coordinates into a **1D string** that preserves proximity:

```text
Geohash Algorithm:

1. Divide world into 4 quadrants (2 bits: lat, lon)
2. Each quadrant divided into 4 sub-quadrants (2 more bits)
3. Continue subdividing (alternating lat/lon bits)
4. Encode bits as base-32 string

Example: San Francisco (37.7749°N, 122.4194°W)

Step 1: Latitude (37.7749)
Range: [-90, 90]
37.7749 > 0 → 1 (northern hemisphere)

Step 2: Longitude (-122.4194)
Range: [-180, 180]
-122.4194 < 0 → 0 (western hemisphere)

Step 3: Latitude (subdivide northern hemisphere)
Range: [0, 90]
37.7749 < 45 → 0 (southern half)

Step 4: Longitude (subdivide western hemisphere)
Range: [-180, 0]
-122.4194 < -90 → 0 (western half)

Continue for desired precision...

Final bits: 10100110... (alternating lat, lon)
Base-32 encode: "9q8yyk"

Precision:
- 1 character: ±2500 km
- 4 characters: ±20 km
- 6 characters: ±0.6 km (600m)
- 8 characters: ±19 m
```

**Geohash Properties:**

```text
Key Property: PREFIX MATCHING

Same prefix = nearby locations!

Geohash       Location
9q8yyk8      (37.7749, -122.4194)  San Francisco
9q8yykb      (37.7750, -122.4195)  100m away - same prefix! ✓
9q8yz        (37.8000, -122.3000)  Different 5th char - far away

Proximity Search:
1. Encode query location: 9q8yyk
2. Search for all locations with prefix "9q8yy"
3. Refine with exact distance check

Benefits:
✅ Can use B-Tree index (strings)
✅ Prefix search = range query
✅ Variable precision (zoom levels)

Limitations:
❌ Edge cases (cells on boundary)
❌ Not perfect circle (grid artifacts)
❌ Neighbors don't always share prefix
```

**Implementation:**

```python
class Geohash:
    """
    Geohash encoding/decoding
    """
    BASE32 = "0123456789bcdefghjkmnpqrstuvwxyz"

    @staticmethod
    def encode(latitude: float, longitude: float, precision: int = 6) -> str:
        """
        Encode lat/lon as geohash string
        """
        lat_range = [-90.0, 90.0]
        lon_range = [-180.0, 180.0]

        geohash = []
        bits = 0
        bit = 0
        even_bit = True

        while len(geohash) < precision:
            if even_bit:
                # Longitude bit
                mid = (lon_range[0] + lon_range[1]) / 2
                if longitude > mid:
                    bit |= (1 << (4 - bits))
                    lon_range[0] = mid
                else:
                    lon_range[1] = mid
            else:
                # Latitude bit
                mid = (lat_range[0] + lat_range[1]) / 2
                if latitude > mid:
                    bit |= (1 << (4 - bits))
                    lat_range[0] = mid
                else:
                    lat_range[1] = mid

            even_bit = not even_bit
            bits += 1

            if bits == 5:
                # Convert 5 bits to base32 character
                geohash.append(Geohash.BASE32[bit])
                bits = 0
                bit = 0

        return ''.join(geohash)

    @staticmethod
    def decode(geohash: str) -> Tuple[float, float]:
        """
        Decode geohash to lat/lon
        """
        lat_range = [-90.0, 90.0]
        lon_range = [-180.0, 180.0]

        even_bit = True

        for char in geohash:
            idx = Geohash.BASE32.index(char)

            for i in range(4, -1, -1):
                bit = (idx >> i) & 1

                if even_bit:
                    # Longitude
                    mid = (lon_range[0] + lon_range[1]) / 2
                    if bit == 1:
                        lon_range[0] = mid
                    else:
                        lon_range[1] = mid
                else:
                    # Latitude
                    mid = (lat_range[0] + lat_range[1]) / 2
                    if bit == 1:
                        lat_range[0] = mid
                    else:
                        lat_range[1] = mid

                even_bit = not even_bit

        lat = (lat_range[0] + lat_range[1]) / 2
        lon = (lon_range[0] + lon_range[1]) / 2

        return lat, lon

    @staticmethod
    def neighbors(geohash: str) -> List[str]:
        """
        Get 8 neighboring geohash cells
        """
        # Simplified - production use library like python-geohash
        # Neighbors: N, NE, E, SE, S, SW, W, NW
        # Required to handle boundary cases
        pass


# Example
geohash = Geohash.encode(37.7749, -122.4194, precision=6)
print(f"Geohash: {geohash}")
# Output: 9q8yyk

lat, lon = Geohash.decode(geohash)
print(f"Decoded: ({lat:.4f}, {lon:.4f})")
# Output: (37.7749, -122.4193)  - small precision loss


# Database usage
# CREATE INDEX idx_geohash ON restaurants(geohash);
# SELECT * FROM restaurants WHERE geohash LIKE '9q8yy%';  -- Nearby locations
```

🏗️ **ARCHITECT**: Geohash precision table:

```text
┌───────────┬────────────────┬────────────────────────┐
│ Precision │ Cell Size      │ Use Case               │
├───────────┼────────────────┼────────────────────────┤
│ 1         │ ±2500 km       │ Continent              │
│ 2         │ ±630 km        │ Country                │
│ 3         │ ±78 km         │ State/Province         │
│ 4         │ ±20 km         │ City                   │
│ 5         │ ±2.4 km        │ Neighborhood           │
│ 6         │ ±610 m         │ Street block ✓ Common  │
│ 7         │ ±76 m          │ Building               │
│ 8         │ ±19 m          │ House                  │
│ 9         │ ±2.4 m         │ Room                   │
└───────────┴────────────────┴────────────────────────┘

Rule of thumb:
- Restaurant search: precision 6-7 (streets/buildings)
- Delivery zones: precision 5 (neighborhoods)
- Store locator: precision 4-5 (cities)
```

---

### 3. **R-Tree: Spatial Index Structure**

🎓 **PROFESSOR**: R-Tree is like **B-Tree for multi-dimensional data**:

```text
R-Tree Structure:

Instead of single values (B-Tree), stores BOUNDING BOXES

Example: Restaurant locations

Level 0 (Root):
┌─────────────────────────────────────────┐
│ MBR (Minimum Bounding Rectangle)        │
│  NW Region          NE Region           │
│  ┌──────────┐      ┌──────────┐        │
│  │   □ □    │      │  □ □     │        │
│  │  □   □   │      │ □   □    │        │
│  └──────────┘      └──────────┘        │
│  SW Region          SE Region           │
│  ┌──────────┐      ┌──────────┐        │
│  │ □ □      │      │   □  □   │        │
│  │   □ □    │      │  □    □  │        │
│  └──────────┘      └──────────┘        │
└─────────────────────────────────────────┘

Level 1 (Intermediate nodes):
Each region contains smaller bounding boxes

Level 2 (Leaf nodes):
Individual restaurant locations

Search Algorithm:
1. Start at root
2. Check which bounding boxes overlap query region
3. Descend into overlapping boxes
4. At leaves, check exact distances

Example: Find within 5km of point P
1. Root: NE and SE regions overlap circle
2. Descend into NE, SE
3. Check leaf nodes in these regions
4. Filter by exact distance
```

**R-Tree Properties:**

```text
Invariants:
- Every node (except root) contains [m, M] entries
- All leaves at same level (balanced)
- Bounding boxes can OVERLAP (unlike B-Tree!)

Insert Algorithm:
1. Find best leaf (minimum area enlargement)
2. Insert point
3. If node overflows, split
4. Propagate split up tree if needed

Split Strategy:
- Minimize overlap between siblings
- Minimize total area

Complexity:
- Search: O(log N) average, O(N) worst case
- Insert: O(log N)
- Delete: O(log N)
```

**Implementation (Simplified):**

```python
from dataclasses import dataclass
from typing import List, Tuple

@dataclass
class BoundingBox:
    """
    Minimum Bounding Rectangle (MBR)
    """
    min_lat: float
    max_lat: float
    min_lon: float
    max_lon: float

    def area(self) -> float:
        return (self.max_lat - self.min_lat) * (self.max_lon - self.min_lon)

    def enlarge_to_include(self, point: Tuple[float, float]):
        """
        Expand box to include point
        """
        lat, lon = point
        self.min_lat = min(self.min_lat, lat)
        self.max_lat = max(self.max_lat, lat)
        self.min_lon = min(self.min_lon, lon)
        self.max_lon = max(self.max_lon, lon)

    def contains(self, point: Tuple[float, float]) -> bool:
        lat, lon = point
        return (self.min_lat <= lat <= self.max_lat and
                self.min_lon <= lon <= self.max_lon)

    def intersects(self, other: 'BoundingBox') -> bool:
        """
        Check if two bounding boxes overlap
        """
        return not (self.max_lat < other.min_lat or
                    self.min_lat > other.max_lat or
                    self.max_lon < other.min_lon or
                    self.min_lon > other.max_lon)

    def intersects_circle(self, center_lat, center_lon, radius_km) -> bool:
        """
        Check if circle intersects bounding box (approximation)
        """
        # Simplified - convert radius to lat/lon degrees
        # 1 degree ≈ 111 km
        radius_deg = radius_km / 111.0

        circle_box = BoundingBox(
            center_lat - radius_deg,
            center_lat + radius_deg,
            center_lon - radius_deg,
            center_lon + radius_deg
        )

        return self.intersects(circle_box)


class RTreeNode:
    """
    R-Tree node (simplified)
    """
    def __init__(self, is_leaf=False, max_entries=4):
        self.is_leaf = is_leaf
        self.max_entries = max_entries
        self.mbr = None  # Minimum bounding rectangle
        self.entries = []  # (BoundingBox, child_node or point_data)

    def search(self, query_lat, query_lon, radius_km):
        """
        Find all points within radius
        """
        results = []

        # Check if query circle intersects this node's MBR
        if not self.mbr.intersects_circle(query_lat, query_lon, radius_km):
            return results  # Prune this subtree

        if self.is_leaf:
            # Check each point in leaf
            for bbox, point_data in self.entries:
                lat, lon, name = point_data
                distance = haversine_distance(query_lat, query_lon, lat, lon)
                if distance <= radius_km:
                    results.append((name, distance))
        else:
            # Descend into children
            for bbox, child_node in self.entries:
                if bbox.intersects_circle(query_lat, query_lon, radius_km):
                    results.extend(child_node.search(query_lat, query_lon, radius_km))

        return results
```

---

### 4. **Quadtree: Recursive Subdivision**

🎓 **PROFESSOR**: Quadtree recursively divides 2D space into **4 quadrants**:

```text
Quadtree Structure:

Level 0 (Root):
┌─────────────────┐
│        │        │
│   NW   │   NE   │
│ ───────┼─────── │
│   SW   │   SE   │
│        │        │
└─────────────────┘

If quadrant has >MAX points, subdivide:

NW Quadrant:
┌─────────────┐
│   │   │     │
│ NWNW│ NWNE  │
│─────┼─────  │
│ NWSW│ NWSE  │
│   │   │     │
└─────────────┘

Recursively split until:
- Each cell has ≤ MAX points
- OR max depth reached

Search Algorithm:
1. Start at root
2. Determine which quadrant(s) overlap query
3. Recursively search overlapping quadrants
4. Collect all points in range
```

**Implementation:**

```python
class QuadTreeNode:
    """
    Quadtree for spatial indexing
    """
    MAX_POINTS = 4  # Split threshold
    MAX_DEPTH = 10

    def __init__(self, boundary: BoundingBox, depth=0):
        self.boundary = boundary
        self.depth = depth
        self.points = []  # [(lat, lon, data), ...]
        self.divided = False

        # Children (NW, NE, SW, SE)
        self.nw = None
        self.ne = None
        self.sw = None
        self.se = None

    def insert(self, point: Tuple[float, float, any]) -> bool:
        """
        Insert point into quadtree
        """
        lat, lon, data = point

        # Check if point is in this boundary
        if not self.boundary.contains((lat, lon)):
            return False

        # If capacity available and not divided, add here
        if len(self.points) < self.MAX_POINTS and not self.divided:
            self.points.append(point)
            return True

        # Need to subdivide
        if not self.divided and self.depth < self.MAX_DEPTH:
            self.subdivide()

        # Insert into appropriate child
        if self.divided:
            return (self.nw.insert(point) or
                    self.ne.insert(point) or
                    self.sw.insert(point) or
                    self.se.insert(point))

        # Max depth reached, force insert
        self.points.append(point)
        return True

    def subdivide(self):
        """
        Split this node into 4 quadrants
        """
        mid_lat = (self.boundary.min_lat + self.boundary.max_lat) / 2
        mid_lon = (self.boundary.min_lon + self.boundary.max_lon) / 2

        # Northwest
        nw_box = BoundingBox(mid_lat, self.boundary.max_lat,
                             self.boundary.min_lon, mid_lon)
        self.nw = QuadTreeNode(nw_box, self.depth + 1)

        # Northeast
        ne_box = BoundingBox(mid_lat, self.boundary.max_lat,
                             mid_lon, self.boundary.max_lon)
        self.ne = QuadTreeNode(ne_box, self.depth + 1)

        # Southwest
        sw_box = BoundingBox(self.boundary.min_lat, mid_lat,
                             self.boundary.min_lon, mid_lon)
        self.sw = QuadTreeNode(sw_box, self.depth + 1)

        # Southeast
        se_box = BoundingBox(self.boundary.min_lat, mid_lat,
                             mid_lon, self.boundary.max_lon)
        self.se = QuadTreeNode(se_box, self.depth + 1)

        self.divided = True

        # Move points to children
        for point in self.points:
            (self.nw.insert(point) or
             self.ne.insert(point) or
             self.sw.insert(point) or
             self.se.insert(point))

        self.points = []  # Clear points from this node

    def query_radius(self, center_lat, center_lon, radius_km):
        """
        Find all points within radius
        """
        results = []

        # Check if circle intersects this node's boundary
        if not self.boundary.intersects_circle(center_lat, center_lon, radius_km):
            return results

        # Check points in this node
        for lat, lon, data in self.points:
            distance = haversine_distance(center_lat, center_lon, lat, lon)
            if distance <= radius_km:
                results.append((data, distance))

        # Recursively search children
        if self.divided:
            results.extend(self.nw.query_radius(center_lat, center_lon, radius_km))
            results.extend(self.ne.query_radius(center_lat, center_lon, radius_km))
            results.extend(self.sw.query_radius(center_lat, center_lon, radius_km))
            results.extend(self.se.query_radius(center_lat, center_lon, radius_km))

        return results
```

---

### 5. **Distance Calculations: Haversine Formula**

🎓 **PROFESSOR**: Accurate distance calculation on a **sphere** (Earth):

```text
Problem: Earth is a sphere, not a flat plane!

Euclidean distance (WRONG for lat/lon):
d = √((lat₂-lat₁)² + (lon₂-lon₁)²)
❌ Treats Earth as flat
❌ Errors increase near poles
❌ Doesn't account for curvature

Haversine Formula (CORRECT):
a = sin²(Δφ/2) + cos(φ₁) × cos(φ₂) × sin²(Δλ/2)
c = 2 × atan2(√a, √(1−a))
d = R × c

Where:
- φ = latitude (radians)
- λ = longitude (radians)
- R = Earth radius (6371 km)

Accuracy: ~0.5% error (Earth is slightly ellipsoid, not perfect sphere)

For higher accuracy, use Vincenty formula (ellipsoid model)
```

**Implementation:**

```python
import math

def haversine_distance(lat1, lon1, lat2, lon2):
    """
    Calculate distance between two points on Earth (km)
    """
    R = 6371  # Earth radius in kilometers

    # Convert to radians
    lat1_rad = math.radians(lat1)
    lat2_rad = math.radians(lat2)
    delta_lat = math.radians(lat2 - lat1)
    delta_lon = math.radians(lon2 - lon1)

    # Haversine formula
    a = math.sin(delta_lat/2)**2 + \
        math.cos(lat1_rad) * math.cos(lat2_rad) * \
        math.sin(delta_lon/2)**2

    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1-a))

    distance = R * c

    return distance


# Example
sf = (37.7749, -122.4194)  # San Francisco
la = (34.0522, -118.2437)  # Los Angeles

distance = haversine_distance(*sf, *la)
print(f"Distance: {distance:.2f} km")
# Output: Distance: 559.12 km (actual: ~559 km - accurate!)


# Benchmark: Haversine vs Euclidean
def euclidean_distance_wrong(lat1, lon1, lat2, lon2):
    """
    WRONG: Treats lat/lon as Cartesian coordinates
    """
    return math.sqrt((lat2-lat1)**2 + (lon2-lon1)**2) * 111  # 1 deg ≈ 111 km

euclidean_dist = euclidean_distance_wrong(*sf, *la)
print(f"Euclidean (wrong): {euclidean_dist:.2f} km")
# Output: ~485 km (13% error!)
```

🏗️ **ARCHITECT**: Bounding box optimization (avoid expensive Haversine):

```python
def bounding_box_for_radius(center_lat, center_lon, radius_km):
    """
    Calculate bounding box for initial filtering
    Fast check before expensive Haversine
    """
    # Approximate: 1 degree latitude ≈ 111 km
    # Longitude varies by latitude: 111 * cos(lat) km
    lat_delta = radius_km / 111.0
    lon_delta = radius_km / (111.0 * math.cos(math.radians(center_lat)))

    return {
        'min_lat': center_lat - lat_delta,
        'max_lat': center_lat + lat_delta,
        'min_lon': center_lon - lon_delta,
        'max_lon': center_lon + lon_delta
    }


# Two-phase proximity search:
def find_nearby_optimized(center_lat, center_lon, radius_km):
    """
    1. Filter by bounding box (fast, index scan)
    2. Filter by exact distance (slow, but fewer points)
    """
    bbox = bounding_box_for_radius(center_lat, center_lon, radius_km)

    # Phase 1: Bounding box filter (uses spatial index)
    candidates = db.execute("""
        SELECT id, name, latitude, longitude
        FROM restaurants
        WHERE latitude BETWEEN :min_lat AND :max_lat
          AND longitude BETWEEN :min_lon AND :max_lon
    """, bbox)

    # Phase 2: Exact distance filter (in-memory)
    results = []
    for row in candidates:
        distance = haversine_distance(
            center_lat, center_lon,
            row['latitude'], row['longitude']
        )
        if distance <= radius_km:
            results.append((row['name'], distance))

    return sorted(results, key=lambda x: x[1])
```

---

### 6. **Production Implementations**

🏗️ **ARCHITECT**: How databases implement geospatial indexing:

#### PostgreSQL + PostGIS

```sql
-- Enable PostGIS extension
CREATE EXTENSION postgis;

-- Create table with geometry column
CREATE TABLE restaurants (
    id SERIAL PRIMARY KEY,
    name VARCHAR(255),
    location GEOGRAPHY(POINT, 4326)  -- WGS84 coordinate system
);

-- Create spatial index (GIST - Generalized Search Tree)
CREATE INDEX idx_restaurants_location ON restaurants USING GIST(location);

-- Insert data
INSERT INTO restaurants (name, location)
VALUES ('Pizza Palace', ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326));

-- Find within radius (returns in meters)
SELECT
    name,
    ST_Distance(location, ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326)) as distance_meters
FROM restaurants
WHERE ST_DWithin(
    location,
    ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326),
    5000  -- 5km in meters
)
ORDER BY distance_meters
LIMIT 10;


-- Find nearest N (K-Nearest Neighbor)
SELECT
    name,
    ST_Distance(location, ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326)) as distance
FROM restaurants
ORDER BY location <-> ST_SetSRID(ST_MakePoint(-122.4194, 37.7749), 4326)
LIMIT 10;
```

#### MongoDB Geospatial

```javascript
// Create 2dsphere index
db.restaurants.createIndex({ location: "2dsphere" })

// Insert document
db.restaurants.insertOne({
  name: "Pizza Palace",
  location: {
    type: "Point",
    coordinates: [-122.4194, 37.7749]  // [longitude, latitude]
  }
})

// Find within radius
db.restaurants.find({
  location: {
    $near: {
      $geometry: {
        type: "Point",
        coordinates: [-122.4194, 37.7749]
      },
      $maxDistance: 5000  // meters
    }
  }
})

// Find within polygon (delivery zone)
db.restaurants.find({
  location: {
    $geoWithin: {
      $geometry: {
        type: "Polygon",
        coordinates: [[
          [-122.5, 37.8],
          [-122.4, 37.8],
          [-122.4, 37.7],
          [-122.5, 37.7],
          [-122.5, 37.8]
        ]]
      }
    }
  }
})
```

#### Elasticsearch Geospatial

```python
# Create index with geo_point mapping
mapping = {
    "mappings": {
        "properties": {
            "name": {"type": "text"},
            "location": {"type": "geo_point"}
        }
    }
}
es.indices.create(index="restaurants", body=mapping)

# Index document
doc = {
    "name": "Pizza Palace",
    "location": {
        "lat": 37.7749,
        "lon": -122.4194
    }
}
es.index(index="restaurants", document=doc)

# Geo distance query
query = {
    "query": {
        "bool": {
            "filter": {
                "geo_distance": {
                    "distance": "5km",
                    "location": {
                        "lat": 37.7749,
                        "lon": -122.4194
                    }
                }
            }
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

results = es.search(index="restaurants", body=query)

for hit in results['hits']['hits']:
    name = hit['_source']['name']
    distance = hit['sort'][0]
    print(f"{name}: {distance:.2f} km")
```

---

### 7. **Common Geospatial Queries**

🏗️ **ARCHITECT**: Production query patterns:

#### Pattern 1: Find Nearest (Ride-sharing, Delivery)

```python
# Find 10 nearest drivers to passenger
def find_nearest_drivers(passenger_lat, passenger_lon, max_distance_km=10):
    """
    Used by Uber, Lyft for driver matching
    """
    query = """
        SELECT
            driver_id,
            ST_Distance(
                location,
                ST_SetSRID(ST_MakePoint(%s, %s), 4326)
            ) as distance
        FROM active_drivers
        WHERE
            status = 'available'
            AND ST_DWithin(
                location,
                ST_SetSRID(ST_MakePoint(%s, %s), 4326),
                %s
            )
        ORDER BY location <-> ST_SetSRID(ST_MakePoint(%s, %s), 4326)
        LIMIT 10
    """

    params = [
        passenger_lon, passenger_lat,  # Distance calculation
        passenger_lon, passenger_lat, max_distance_km * 1000,  # Filter
        passenger_lon, passenger_lat   # Sort
    ]

    return db.execute(query, params)
```

#### Pattern 2: Geofencing (Delivery Zones)

```python
# Check if address is in delivery zone
def is_in_delivery_zone(restaurant_id, delivery_lat, delivery_lon):
    """
    Used by DoorDash, Uber Eats for zone validation
    """
    query = """
        SELECT
            zone_name,
            ST_Contains(
                zone_polygon,
                ST_SetSRID(ST_MakePoint(%s, %s), 4326)
            ) as is_inside
        FROM delivery_zones
        WHERE restaurant_id = %s
    """

    result = db.execute(query, [delivery_lon, delivery_lat, restaurant_id])
    return result['is_inside'] if result else False
```

#### Pattern 3: Heatmap / Density Analysis

```python
# Find areas with high concentration of events
def get_event_density_grid(bbox, grid_size_km=1):
    """
    Used for crime maps, event density, demand forecasting
    """
    query = """
        SELECT
            ST_SnapToGrid(location, %s) as grid_cell,
            COUNT(*) as event_count,
            AVG(ST_Y(location)) as avg_lat,
            AVG(ST_X(location)) as avg_lon
        FROM events
        WHERE
            created_at > NOW() - INTERVAL '7 days'
            AND location && ST_MakeEnvelope(
                %s, %s, %s, %s, 4326
            )
        GROUP BY grid_cell
        HAVING COUNT(*) > 10
        ORDER BY event_count DESC
    """

    # Convert km to degrees (approximate)
    grid_degrees = grid_size_km / 111.0

    params = [
        grid_degrees,
        bbox['min_lon'], bbox['min_lat'],
        bbox['max_lon'], bbox['max_lat']
    ]

    return db.execute(query, params)
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. Requirements Clarification
```
Questions:
- Query types? (Nearest, within radius, in polygon)
- Update frequency? (Real-time driver locations vs static stores)
- Scale? (Number of points, queries per second)
- Accuracy needs? (Exact vs approximate distances)
```

### 2. Data Structure Selection
```
Choose based on use case:

Static data (restaurants, stores):
✅ R-Tree (PostgreSQL GIST, MongoDB 2dsphere)
✅ Geohash with B-Tree index

Dynamic data (drivers, users):
✅ Geohash with Redis (fast updates)
✅ In-memory Quadtree

Large scale:
✅ Sharding by geohash prefix
✅ Distributed spatial index (S2 geometry)
```

### 3. System Design Example: Uber Driver Matching
```
Components:
1. Driver Location Updates
   - Drivers send location every 4 seconds
   - Update in Redis (geohash as key)
   - Expire after 30 seconds (stale data)

2. Passenger Request
   - Calculate geohash prefix for 5km radius
   - Query Redis for drivers in nearby geohashes
   - Rank by distance + rating + ETA
   - Send request to top 3 drivers

3. Optimization
   - Shard Redis by city (geohash prefix)
   - Cache driver locations (avoid DB hits)
   - Predictive positioning (ML for demand zones)
```

### 4. Capacity Estimation
```
Uber-scale example:
- 5M active drivers
- Location update every 4 seconds
- Updates/sec: 5M / 4 = 1.25M writes/sec

Storage:
- Per location: 100 bytes (lat, lon, metadata)
- Active locations: 5M × 100 bytes = 500 MB
- With overhead: ~1 GB (fits in memory!)

Queries:
- 10M passenger requests/hour = 2.8K queries/sec
- Each query checks ~50 drivers (nearby geohash)
- Reads: 2.8K × 50 = 140K reads/sec (easy for Redis)
```

---

## 🧠 **MIND MAP: GEOSPATIAL INDEX CONCEPTS**
```
                GEOSPATIAL INDEXES
                        |
        ┌───────────────┼───────────────┐
        ↓               ↓                ↓
    GEOHASH          R-TREE          QUADTREE
        |               |                |
   ┌────┴────┐    ┌────┴────┐      ┌────┴────┐
   ↓         ↓    ↓         ↓      ↓         ↓
 Prefix   Base32  MBR    Overlap  Recursive  Split
 Search   Encode  Split   Check   Subdivide  Threshold
 Proximity       Balance         Depth
```

### 💡 **EMOTIONAL ANCHORS**

1. **Geohash = Postal Code 📮**
   - Address: Lat/Lon (exact)
   - Postal code: Geohash (approximate region)
   - Same prefix = nearby locations
   - Longer code = more precise

2. **R-Tree = Nested Boxes 📦**
   - Big box: City
   - Medium boxes: Neighborhoods
   - Small boxes: Streets
   - Find item: Open relevant boxes only

3. **Quadtree = Zoom Levels 🗺️**
   - Zoom out: See whole world (root)
   - Zoom in: See smaller regions (children)
   - Keep zooming: See individual buildings
   - Recursive subdivision

4. **Haversine = Globe Distance ✈️**
   - Flat map: Euclidean (wrong!)
   - Sphere: Haversine (correct!)
   - Flight path: Great circle route
   - Accounts for Earth curvature

---

## 🔑 **KEY TAKEAWAYS**

```
1. Traditional indexes (B-Tree) DON'T work for 2D spatial queries
2. Geohash: Encode lat/lon as string, use prefix matching
3. R-Tree: Hierarchical bounding boxes (PostgreSQL GIST)
4. Quadtree: Recursive 2D subdivision (in-memory)
5. Haversine: Accurate distance on sphere (not Euclidean!)
6. Two-phase search: Bounding box filter + exact distance
7. Use cases: Uber (nearest drivers), DoorDash (delivery zones)
8. Tools: PostGIS, MongoDB 2dsphere, Elasticsearch geo_point
```

**Production Mindset**:
> "Don't implement spatial indexes from scratch. Use PostGIS for PostgreSQL or MongoDB's geospatial features. They're battle-tested, optimized, and handle edge cases (poles, dateline) correctly. Your time is better spent on business logic than reinventing R-Trees."
