# Load Balancing

## What is Load Balancing?

**Definition**: Distributing network traffic across multiple servers to ensure no single server becomes overwhelmed

**Goals**:
- **High availability**: If one server fails, others handle traffic
- **Scalability**: Add more servers to handle increased load
- **Performance**: Distribute load for optimal response times
- **Redundancy**: Eliminate single points of failure

**Basic Concept**:
```
              ┌──────────────┐
              │Load Balancer │
              └───────┬──────┘
                      │
       ┌──────────────┼──────────────┐
       │              │              │
   ┌───▼────┐    ┌───▼────┐    ┌───▼────┐
   │Server 1│    │Server 2│    │Server 3│
   └────────┘    └────────┘    └────────┘
```

---

## Load Balancing Algorithms

### 1. Round Robin

**Concept**: Distribute requests sequentially across servers

```
Request 1 → Server 1
Request 2 → Server 2
Request 3 → Server 3
Request 4 → Server 1 (cycle repeats)
Request 5 → Server 2
...
```

**Implementation**:
```python
class RoundRobinBalancer:
    def __init__(self, servers):
        self.servers = servers
        self.current = 0

    def get_server(self):
        server = self.servers[self.current]
        self.current = (self.current + 1) % len(self.servers)
        return server
```

**Pros**:
- Simple
- Fair distribution
- No server state needed

**Cons**:
- Ignores server capacity
- Ignores current load
- Assumes servers are identical

**When to Use**: Servers have equal capacity, requests are similar

### 2. Weighted Round Robin

**Concept**: Servers with more capacity get more requests

```
Servers:
- Server 1: weight=3 (powerful)
- Server 2: weight=2 (medium)
- Server 3: weight=1 (small)

Distribution pattern:
S1, S1, S1, S2, S2, S3, S1, S1, S1, S2, S2, S3, ...
```

**Implementation**:
```python
class WeightedRoundRobinBalancer:
    def __init__(self, servers_with_weights):
        # servers_with_weights = [(server1, 3), (server2, 2), (server3, 1)]
        self.servers = []
        for server, weight in servers_with_weights:
            self.servers.extend([server] * weight)
        self.current = 0

    def get_server(self):
        server = self.servers[self.current]
        self.current = (self.current + 1) % len(self.servers)
        return server
```

**When to Use**: Servers have different capacities

### 3. Least Connections

**Concept**: Send request to server with fewest active connections

```
Server 1: 10 active connections
Server 2: 5 active connections  ← Choose this
Server 3: 8 active connections

New request → Server 2
```

**Implementation**:
```python
class LeastConnectionsBalancer:
    def __init__(self, servers):
        self.servers = servers
        self.connections = {server: 0 for server in servers}

    def get_server(self):
        # Find server with minimum connections
        return min(self.connections.items(), key=lambda x: x[1])[0]

    def connection_opened(self, server):
        self.connections[server] += 1

    def connection_closed(self, server):
        self.connections[server] -= 1
```

**Pros**:
- Accounts for long-running requests
- Better for heterogeneous traffic
- Dynamic load adaptation

**Cons**:
- Requires state tracking
- More complex

**When to Use**: Requests have varying durations (database queries, file uploads)

### 4. Least Response Time

**Concept**: Send to server with lowest response time and fewest connections

```
Server 1: avg_response=50ms, connections=10
Server 2: avg_response=30ms, connections=5  ← Choose this
Server 3: avg_response=40ms, connections=8

New request → Server 2 (best combo of speed + availability)
```

**Implementation**:
```python
class LeastResponseTimeBalancer:
    def __init__(self, servers):
        self.servers = servers
        self.response_times = {server: deque(maxlen=100) for server in servers}
        self.connections = {server: 0 for server in servers}

    def get_server(self):
        def score(server):
            avg_response = statistics.mean(self.response_times[server]) \
                           if self.response_times[server] else 0
            return avg_response * (1 + self.connections[server])

        return min(self.servers, key=score)

    def record_response(self, server, response_time):
        self.response_times[server].append(response_time)
```

**When to Use**: Performance-critical applications, varying server performance

### 5. IP Hash / Consistent Hashing

**Concept**: Route same client IP to same server

```
hash(client_ip) % num_servers = server_id

Client 192.168.1.10 → hash → Server 2 (always)
Client 192.168.1.20 → hash → Server 1 (always)
```

**Implementation**:
```python
import hashlib

class IPHashBalancer:
    def __init__(self, servers):
        self.servers = servers

    def get_server(self, client_ip):
        hash_value = int(hashlib.md5(client_ip.encode()).hexdigest(), 16)
        return self.servers[hash_value % len(self.servers)]
```

**Consistent Hashing** (handles server changes better):
```python
class ConsistentHashBalancer:
    def __init__(self, servers, virtual_nodes=150):
        self.ring = {}
        self.sorted_keys = []

        for server in servers:
            for i in range(virtual_nodes):
                key = self._hash(f"{server}:{i}")
                self.ring[key] = server
                self.sorted_keys.append(key)

        self.sorted_keys.sort()

    def _hash(self, key):
        return int(hashlib.md5(key.encode()).hexdigest(), 16)

    def get_server(self, client_ip):
        if not self.ring:
            return None

        hash_value = self._hash(client_ip)

        # Find first server >= hash_value
        for key in self.sorted_keys:
            if key >= hash_value:
                return self.ring[key]

        # Wrap around to first server
        return self.ring[self.sorted_keys[0]]
```

**Pros**:
- Session persistence (sticky sessions)
- Cache locality
- Minimal redistribution when servers change

**When to Use**: Need session affinity, stateful applications

### 6. Random

**Concept**: Pick random server

```python
import random

class RandomBalancer:
    def __init__(self, servers):
        self.servers = servers

    def get_server(self):
        return random.choice(self.servers)
```

**Pros**: Simple, works well at scale
**Cons**: Can have uneven distribution short-term

### 7. Power of Two Choices

**Concept**: Pick 2 random servers, choose the one with fewer connections

```python
class PowerOfTwoBalancer:
    def __init__(self, servers):
        self.servers = servers
        self.connections = {server: 0 for server in servers}

    def get_server(self):
        # Pick 2 random servers
        candidates = random.sample(self.servers, 2)

        # Choose the one with fewer connections
        return min(candidates, key=lambda s: self.connections[s])
```

**Pros**: Nearly optimal with minimal overhead
**Research**: Significantly better than pure random

---

## Layer 4 vs Layer 7 Load Balancing

### Layer 4 (Transport Layer)

**Routes based on**: IP address and TCP/UDP port

```
Client → [L4 LB] → Server
         (Looks at: IP, Port only)

Example:
Source: 192.168.1.10:54321
Destination: 10.0.0.5:80
→ Route to Server 2
```

**Characteristics**:
- Fast (simple packet forwarding)
- Low latency
- No application awareness
- Cannot route based on URL, headers, cookies

**Implementation**: Network Address Translation (NAT)

**Use Cases**:
- High throughput requirements
- Non-HTTP protocols
- Simple traffic distribution

**Examples**: AWS Network Load Balancer, NGINX (stream mode), HAProxy (TCP mode)

### Layer 7 (Application Layer)

**Routes based on**: HTTP headers, URL path, cookies, request content

```
Client → [L7 LB] → Server
         (Looks at: URL, headers, cookies, etc.)

Example:
GET /api/users HTTP/1.1
Host: example.com
Cookie: session=abc123

→ Route based on:
  - Path (/api/* → API servers)
  - Cookie (session → sticky server)
  - Header (X-Version: 2 → v2 servers)
```

**Routing Rules**:
```nginx
# NGINX L7 Load Balancing
upstream api_servers {
    server api1.example.com;
    server api2.example.com;
}

upstream web_servers {
    server web1.example.com;
    server web2.example.com;
}

server {
    location /api/ {
        proxy_pass http://api_servers;
    }

    location / {
        proxy_pass http://web_servers;
    }
}
```

**Advanced Features**:
- URL-based routing
- A/B testing (route based on user segment)
- SSL termination
- Request modification
- Rate limiting
- Caching

**Use Cases**:
- Microservices (route by path)
- A/B testing
- Canary deployments
- SSL offloading

**Examples**: AWS Application Load Balancer, NGINX, HAProxy (HTTP mode), Envoy

---

## Health Checks

### Active Health Checks

**HTTP Health Check**:
```nginx
upstream backend {
    server backend1.example.com;
    server backend2.example.com;

    # Health check
    check interval=3000 rise=2 fall=3 timeout=1000 type=http;
    check_http_send "GET /health HTTP/1.0\r\n\r\n";
    check_http_expect_alive http_2xx http_3xx;
}
```

**Parameters**:
- `interval`: Check every 3 seconds
- `rise`: 2 successful checks → mark healthy
- `fall`: 3 failed checks → mark unhealthy
- `timeout`: 1 second timeout

**Health Endpoint**:
```python
@app.route('/health')
def health_check():
    # Check database
    try:
        db.execute("SELECT 1")
    except:
        return {"status": "unhealthy"}, 503

    # Check cache
    try:
        cache.ping()
    except:
        return {"status": "degraded"}, 200

    return {"status": "healthy"}, 200
```

### Passive Health Checks

**Monitor Real Traffic**:
```nginx
upstream backend {
    server backend1.example.com max_fails=3 fail_timeout=30s;
    server backend2.example.com max_fails=3 fail_timeout=30s;
}

# If 3 requests fail within 30s, mark server down for 30s
```

**Combination** (Active + Passive):
```
Active: Regular pings (every 5s)
Passive: Monitor actual requests

Benefits:
- Active: Catches failures before user impact
- Passive: Catches real-world issues (not just /health)
```

---

## Real-World Implementations

### NGINX

**Configuration**:
```nginx
http {
    upstream backend {
        least_conn;  # Algorithm

        server backend1.example.com weight=3;
        server backend2.example.com weight=2;
        server backend3.example.com weight=1 backup;  # Only if others fail

        keepalive 32;  # Connection pooling
    }

    server {
        listen 80;

        location / {
            proxy_pass http://backend;
            proxy_next_upstream error timeout;  # Retry on error
            proxy_connect_timeout 2s;
            proxy_read_timeout 5s;
        }
    }
}
```

### HAProxy

**Configuration**:
```haproxy
frontend http_front
    bind *:80
    default_backend http_back

backend http_back
    balance roundrobin
    option httpchk GET /health

    server server1 10.0.1.5:8080 check
    server server2 10.0.1.6:8080 check
    server server3 10.0.1.7:8080 check backup
```

### AWS Application Load Balancer

**Features**:
- Layer 7 (HTTP/HTTPS)
- Path-based routing
- Host-based routing
- Automatic scaling
- WAF integration

**Target Groups**:
```
ALB → Target Group 1 (/api/*) → EC2 instances
   → Target Group 2 (/web/*) → EC2 instances
   → Target Group 3 (*.admin.example.com) → EC2 instances
```

---

## Interview Q&A

**Q: "Design a load balancing solution for a web application with 1000 req/sec"**

**Answer**:
```
Multi-layered approach:

1. DNS Load Balancing (Geo-routing):
   - Route53 with latency-based routing
   - US users → US region
   - EU users → EU region
   - Reduces latency, distributes global load

2. Layer 7 Load Balancer (AWS ALB):
   - Terminates SSL
   - Routes /api/* to API tier
   - Routes /static/* to CDN
   - Health checks every 30s
   - Auto-scaling based on request count

3. Algorithm: Least Connections
   - Web requests vary (some long polling)
   - Need dynamic load distribution
   - Better than round-robin for mixed workloads

4. Health Checks:
   - Active: HTTP GET /health every 10s
   - Passive: Remove after 3 consecutive 5xx errors
   - Endpoint checks: DB connection, cache, dependencies

5. High Availability:
   - ALB across 3 availability zones
   - Auto-scaling group: min=4, max=20 instances
   - Scale up: CPU > 70% or request count > 250/instance
   - Scale down: CPU < 30% for 5 minutes

6. Session Management:
   - Sticky sessions via cookies (if needed)
   - Or stateless (JWT tokens, better)
   - Session store in Redis (shared across instances)

Architecture:
```
               [Route53]
                   │
           ┌───────┴────────┐
           │                │
    [ALB - US-East]   [ALB - EU-West]
           │                │
    ┌──────┼──────┐        ...
    │      │      │
 [Web1] [Web2] [Web3]
    │      │      │
    └──────┴──────┘
           │
    [Redis] [RDS]
```

Why This Design:
- DNS: Geographic distribution
- ALB: Managed, auto-scales, Layer 7 routing
- Least Connections: Better for varying request times
- Multi-AZ: High availability
- Auto-scaling: Handle traffic spikes

Expected Performance:
- 1000 req/sec = ~4 instances at 250 req/sec each
- With auto-scaling: Handle 5x spikes
- Latency: <100ms (including LB overhead ~10ms)
```

---

## Key Takeaways

1. **Algorithm Matters**: Choose based on traffic patterns and server characteristics
2. **Layer 4 vs 7**: L4 for speed, L7 for intelligent routing
3. **Health Checks Essential**: Detect and remove unhealthy servers automatically
4. **Session Persistence**: Use when needed, but stateless is better
5. **Multi-Layer**: Combine DNS, L4, and L7 for robust solutions

---

## Further Reading

- NGINX Load Balancing Guide
- HAProxy Documentation
- AWS Elastic Load Balancing
- "The Power of Two Random Choices" - Research paper
- "Designing Data-Intensive Applications" - Chapter 6
