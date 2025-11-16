# Load Balancing

Distributing incoming network traffic across multiple servers to ensure no single server bears too much load.

## Contents
- [What is Load Balancing?](#what-is-load-balancing)
- [Load Balancing Algorithms](#load-balancing-algorithms)
- [Layer 4 vs Layer 7 Load Balancing](#layer-4-vs-layer-7-load-balancing)
- [Health Checks](#health-checks)
- [Session Persistence](#session-persistence)
- [Common Load Balancers](#common-load-balancers)
- [Advanced Patterns](#advanced-patterns)

## What is Load Balancing?

Load balancing distributes client requests across multiple backend servers, improving:
- **Availability**: If one server fails, traffic routes to healthy servers
- **Scalability**: Add more servers to handle increased load
- **Performance**: No single server gets overwhelmed
- **Reliability**: Redundancy protects against failures

### Basic Architecture

```
                    Internet
                       |
                       v
                 Load Balancer
                       |
        +-------------+-------------+
        |             |             |
        v             v             v
    Server 1      Server 2      Server 3
        |             |             |
        +-------------+-------------+
                       |
                       v
                   Database
```

### Key Benefits

1. **Horizontal Scaling**: Add/remove servers without downtime
2. **High Availability**: Automatic failover to healthy servers
3. **Maintenance**: Take servers offline for updates without service interruption
4. **Geographic Distribution**: Route users to nearest data center
5. **SSL Termination**: Offload encryption/decryption from backend servers

## Load Balancing Algorithms

Choosing the right algorithm depends on your application's needs.

### 1. Round Robin

Distributes requests sequentially across servers.

```
Request 1 → Server 1
Request 2 → Server 2
Request 3 → Server 3
Request 4 → Server 1  (cycles back)
```

**Pros:**
- Simple to implement
- Fair distribution if requests are similar
- No state required

**Cons:**
- Doesn't account for server capacity differences
- Doesn't consider current server load
- May send requests to slow/overloaded servers

**Best for:** Homogeneous servers with similar requests

### 2. Weighted Round Robin

Like round robin but servers get different weights based on capacity.

```
Server 1: Weight 5 (gets 5 requests)
Server 2: Weight 3 (gets 3 requests)
Server 3: Weight 2 (gets 2 requests)

Pattern: 1, 1, 2, 1, 3, 2, 1, 3, 1, 2 ...
```

**Best for:** Heterogeneous servers (different CPU/RAM)

**Configuration Example:**
```nginx
upstream backend {
    server server1.example.com weight=5;
    server server2.example.com weight=3;
    server server3.example.com weight=2;
}
```

### 3. Least Connections

Routes to server with fewest active connections.

```
Server 1: 10 connections  ← Choose this one
Server 2: 15 connections
Server 3: 12 connections
```

**Pros:**
- Handles varying request durations well
- Better load distribution for long-lived connections
- Adapts to actual server load

**Cons:**
- Requires tracking connection state
- More complex than round robin

**Best for:** Applications with variable request processing times (video streaming, file uploads)

### 4. Weighted Least Connections

Combines least connections with server weights.

```
Server 1: 10 connections, weight=2 → ratio: 5
Server 2: 15 connections, weight=3 → ratio: 5
Server 3: 8 connections, weight=1  → ratio: 8

Choose Server 1 or 2 (lowest ratio)
```

### 5. Least Response Time

Routes to server with fastest response time.

```
Server 1: avg 50ms, 10 connections
Server 2: avg 30ms, 15 connections  ← Choose this one
Server 3: avg 80ms, 5 connections
```

**Pros:**
- Optimizes for user-perceived performance
- Accounts for server health/load

**Cons:**
- Requires monitoring response times
- Can create feedback loops

**Best for:** Performance-critical applications

### 6. IP Hash / Source IP Affinity

Routes based on client IP address hash.

```python
def choose_server(client_ip, servers):
    hash_value = hash(client_ip)
    server_index = hash_value % len(servers)
    return servers[server_index]
```

**Pros:**
- Same client always hits same server
- Natural session persistence
- Good for caching per-server

**Cons:**
- Uneven distribution if IP distribution is skewed
- Adding/removing servers disrupts all mappings
- Not good for users behind NAT (same IP)

**Best for:** Applications requiring session affinity without sticky sessions

**Improvement: Consistent Hashing**
```python
# Reduces disruption when servers change
# Only K/n keys need remapping (K=keys, n=servers)
```

### 7. Random

Randomly selects a server for each request.

```python
def choose_server(servers):
    return random.choice(servers)
```

**Pros:**
- Very simple
- Surprisingly effective at scale
- No state needed

**Cons:**
- May have short-term imbalances
- Not optimal for small number of requests

**Best for:** Stateless microservices with many instances

### 8. Least Bandwidth

Routes to server currently serving least amount of traffic (Mbps).

**Best for:** Video/media streaming services

## Layer 4 vs Layer 7 Load Balancing

### Layer 4 (Transport Layer)

Makes decisions based on IP address and TCP/UDP port.

```
Client: 203.0.113.5:54321 → 10.0.1.10:80
Server: 10.0.1.10:80 → 192.168.1.5:8080
```

**Characteristics:**
- Operates at TCP/UDP level
- Cannot inspect HTTP headers/content
- Fast (minimal processing)
- Protocol-agnostic

**Pros:**
- High performance (low latency)
- Less CPU intensive
- Works with any TCP/UDP protocol

**Cons:**
- Cannot route based on URL/headers
- No advanced HTTP features
- Cannot modify requests

**Use cases:**
- Raw TCP services (databases, SSH)
- Maximum performance requirements
- Non-HTTP protocols

**Example: HAProxy L4 Config**
```
frontend tcp_front
    bind *:3306
    mode tcp
    default_backend mysql_servers

backend mysql_servers
    mode tcp
    balance roundrobin
    server mysql1 192.168.1.10:3306
    server mysql2 192.168.1.11:3306
```

### Layer 7 (Application Layer)

Makes decisions based on HTTP content (URLs, headers, cookies).

**Characteristics:**
- Operates at HTTP level
- Can inspect and modify requests
- Content-based routing
- SSL termination

**Pros:**
- Smart routing (e.g., `/api/*` → API servers)
- Header manipulation
- Caching
- Authentication/authorization
- WAF capabilities

**Cons:**
- Higher latency (needs to parse HTTP)
- More CPU intensive
- HTTP-specific

**Use cases:**
- Microservices routing
- A/B testing
- Canary deployments
- Content-based routing

**Example: Nginx L7 Config**
```nginx
http {
    upstream api_servers {
        server api1.example.com;
        server api2.example.com;
    }

    upstream web_servers {
        server web1.example.com;
        server web2.example.com;
    }

    server {
        listen 80;

        location /api/ {
            proxy_pass http://api_servers;
        }

        location / {
            proxy_pass http://web_servers;
        }
    }
}
```

### Comparison Table

| Feature | Layer 4 | Layer 7 |
|---------|---------|---------|
| **Speed** | Faster | Slower |
| **Routing** | IP/Port only | URL/Headers/Cookies |
| **Protocols** | Any TCP/UDP | HTTP/HTTPS |
| **SSL Termination** | No | Yes |
| **Caching** | No | Yes |
| **Content Modification** | No | Yes |
| **Cost** | Lower | Higher |

## Health Checks

Load balancers must detect unhealthy servers and stop routing to them.

### Types of Health Checks

#### 1. Active Health Checks

Load balancer actively probes servers.

```yaml
Health Check Configuration:
  Interval: 10 seconds
  Timeout: 5 seconds
  Unhealthy threshold: 3 consecutive failures
  Healthy threshold: 2 consecutive successes
  Endpoint: /health
```

**Example: Simple HTTP Health Check**
```python
# Server endpoint
@app.route('/health')
def health_check():
    # Check database connection
    if not database.is_connected():
        return "Unhealthy", 503

    # Check critical dependencies
    if not redis.ping():
        return "Unhealthy", 503

    return "Healthy", 200
```

#### 2. Passive Health Checks

Monitor actual traffic to detect failures.

```
If 3 consecutive requests to server fail:
  → Mark server as unhealthy
  → Stop routing traffic
  → Continue active health checks

When active health check passes:
  → Mark server as healthy
  → Resume routing traffic
```

### Health Check Best Practices

1. **Shallow vs Deep Checks**
```python
# Shallow (fast, frequent)
@app.route('/health')
def shallow_health():
    return "OK", 200

# Deep (slow, less frequent)
@app.route('/health/deep')
def deep_health():
    # Check database
    db.execute("SELECT 1")
    # Check cache
    cache.ping()
    # Check downstream services
    check_dependencies()
    return "OK", 200
```

2. **Separate Health Check Endpoint**
```
Don't use: /
Use: /health or /healthz
```

3. **Include Critical Dependencies**
```python
def health_check():
    checks = {
        'database': check_database(),
        'cache': check_cache(),
        'disk_space': check_disk_space()
    }

    all_healthy = all(checks.values())
    status_code = 200 if all_healthy else 503

    return jsonify(checks), status_code
```

## Session Persistence

Ensuring a user's requests go to the same server.

### Methods

#### 1. Sticky Sessions (Session Affinity)

Load balancer routes based on session cookie.

```
First request → Server 1 → Set cookie: server=1
Subsequent requests with cookie → Server 1
```

**Implementation: Cookie-based**
```nginx
upstream backend {
    ip_hash;  # Simple approach
    # Or use sticky cookie
    sticky cookie srv_id expires=1h;
}
```

**Pros:**
- Simple to implement
- Works with stateful applications

**Cons:**
- Defeats some load balancing benefits
- Server failure loses sessions
- Uneven load distribution

#### 2. Distributed Sessions

Store session data externally (Redis, database).

```
Request → Any Server → Read session from Redis
```

**Implementation:**
```python
from redis import Redis

redis = Redis(host='session-store')

@app.route('/profile')
def profile():
    session_id = request.cookies.get('session_id')
    user_data = redis.get(f'session:{session_id}')
    return render_template('profile.html', user=user_data)
```

**Pros:**
- Servers are stateless
- Better load distribution
- Survives server failures

**Cons:**
- Extra network hop (latency)
- Additional infrastructure (Redis)
- Single point of failure (mitigated by Redis cluster)

#### 3. Client-Side Sessions (JWT)

Store session data in client (encrypted/signed).

```python
# No server-side session storage needed
@app.route('/profile')
def profile():
    token = request.headers.get('Authorization')
    user_data = jwt.decode(token, SECRET_KEY)
    return render_template('profile.html', user=user_data)
```

**Pros:**
- Truly stateless servers
- No session storage needed
- Scales infinitely

**Cons:**
- Limited data size (cookies ~4KB)
- Cannot revoke sessions easily
- Data visible to client (if not encrypted)

## Common Load Balancers

### Hardware Load Balancers

- **F5 BIG-IP**: Enterprise-grade, very expensive
- **Citrix ADC**: High performance, advanced features
- **A10 Thunder**: Cost-effective alternative

### Software Load Balancers

#### Nginx
```nginx
http {
    upstream backend {
        least_conn;
        server backend1.example.com:8080;
        server backend2.example.com:8080;
        server backend3.example.com:8080;
    }

    server {
        listen 80;
        location / {
            proxy_pass http://backend;
        }
    }
}
```

**Pros:** Fast, lightweight, widely used
**Cons:** Limited L7 features compared to HAProxy

#### HAProxy
```
frontend http_front
    bind *:80
    default_backend http_back

backend http_back
    balance leastconn
    server server1 10.0.1.3:80 check
    server server2 10.0.1.4:80 check
    server server3 10.0.1.5:80 check
```

**Pros:** Advanced L7 routing, detailed stats
**Cons:** More complex configuration

### Cloud Load Balancers

#### AWS Elastic Load Balancer (ELB)
- **ALB** (Application): L7, HTTP/HTTPS
- **NLB** (Network): L4, high performance
- **CLB** (Classic): Legacy

#### Google Cloud Load Balancer
- Global load balancing
- Anycast IP addresses
- Auto-scaling integration

#### Azure Load Balancer
- Internal and external load balancing
- Zone-redundant

## Advanced Patterns

### 1. Global Server Load Balancing (GSLB)

DNS-based routing to nearest/best data center.

```
User in US → us-east-1.example.com → US Data Center
User in EU → eu-west-1.example.com → EU Data Center
```

### 2. Service Mesh

Sidecar proxies for microservices communication.

```
Service A → Envoy Proxy → Load Balance → Envoy Proxy → Service B
```

**Tools:** Istio, Linkerd, Consul Connect

### 3. Client-Side Load Balancing

Client chooses which server to call.

```java
// Netflix Ribbon example
@LoadBalanced
RestTemplate restTemplate;

// Client queries service registry and load balances
String response = restTemplate.getForObject(
    "http://user-service/api/users/123",
    String.class
);
```

### 4. Blue-Green Deployments

```
Load Balancer → 100% traffic → Blue (v1.0)
                   0% traffic → Green (v2.0)

After testing:
Load Balancer →   0% traffic → Blue (v1.0)
                 100% traffic → Green (v2.0)
```

### 5. Canary Releases

```
Load Balancer → 95% traffic → Stable (v1.0)
                 5% traffic → Canary (v2.0)

Gradually increase if no errors:
→ 90/10 → 75/25 → 50/50 → 0/100
```

## Interview Tips

When discussing load balancing:

1. **Ask about requirements**
   - HTTP or TCP/UDP?
   - Need content-based routing?
   - Session requirements?

2. **Discuss tradeoffs**
   - L4 vs L7 performance/features
   - Algorithm selection based on workload

3. **Consider the full picture**
   - Health checks
   - SSL termination
   - Geographic distribution

4. **Know common solutions**
   - Nginx/HAProxy for self-hosted
   - Cloud load balancers for managed

## Common Pitfalls

### 1. Single Load Balancer (SPOF)
```
Problem: Load balancer itself becomes bottleneck/failure point
Solution: Multiple load balancers with failover (VRRP, keepalived)
```

### 2. Ignoring Health Checks
```
Problem: Routing traffic to failed servers
Solution: Proper active and passive health checks
```

### 3. Wrong Algorithm
```
Problem: Round robin for long-lived connections
Solution: Use least connections or least response time
```

### 4. SSL at Backend Servers
```
Problem: Wasting CPU on SSL for each server
Solution: SSL termination at load balancer
```

## Key Takeaways

1. Load balancing is essential for horizontal scaling
2. Choose L4 for performance, L7 for flexibility
3. Health checks prevent routing to failed servers
4. Avoid sticky sessions; use distributed session storage
5. Different algorithms suit different workloads
6. Cloud load balancers simplify management but cost more

## Further Reading

- [Scaling Strategies](scaling-strategies.md) - When to scale horizontally
- [Performance Optimization](performance.md) - Making systems faster
- [High Availability Patterns](../reliability/high-availability.md)
