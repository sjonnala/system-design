# Load Balancing: Deep Dive

## Contents

- [Load Balancing: Deep Dive](#load-balancing-deep-dive)
    - [Core Mental Model](#core-mental-model)
    - [Load Balancing Algorithms](#1-load-balancing-algorithms)
    - [L4 vs L7 Load Balancing](#2-l4-vs-l7-load-balancing)
    - [Health Checks & Failure Detection](#3-health-checks--failure-detection)
    - [Session Persistence (Sticky Sessions)](#4-session-persistence-sticky-sessions)
    - [Global vs Local Load Balancing](#5-global-vs-local-load-balancing)
    - [Consistent Hashing for Distributed Systems](#6-consistent-hashing-for-distributed-systems)
    - [Failure Modes & Resilience Patterns](#7-failure-modes--resilience-patterns)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification](#1-requirements-clarification)
        - [Load Balancer Placement](#2-load-balancer-placement)
        - [High-Level Design](#3-high-level-design)
        - [Deep Dives](#4-deep-dives)
    - [MIND MAP: LOAD BALANCING CONCEPTS](#mind-map-load-balancing-concepts)

## Core Mental Model

🎓 **PROFESSOR**: Load balancing is fundamentally about **queuing theory** and **resource allocation**.

```text
Little's Law:
L = λ × W

Where:
L = Average number of requests in system
λ = Average arrival rate (requests/sec)
W = Average time in system (latency)

Goal of Load Balancing: Minimize W by distributing λ across N servers
Ideal: W_balanced = W_single / N (linear scaling)
Reality: W_balanced = W_single / N + overhead (coordination cost)
```

**The Fundamental Problem:**
```
Without Load Balancer:
┌────────┐
│ Client │──────────────────► [Server 1] ← 100% traffic, overloaded
└────────┘                    [Server 2] ← 0% traffic, idle
                              [Server 3] ← 0% traffic, idle

With Load Balancer:
┌────────┐     ┌──────────────┐
│ Client │────►│Load Balancer │────► [Server 1] ← 33% traffic
└────────┘     └──────────────┘  ├──► [Server 2] ← 33% traffic
                                 └──► [Server 3] ← 34% traffic

Result: 3x capacity, 3x availability, ~3x lower latency
```

**🏗️ ARCHITECT**: Load balancer is the **single most critical** component:
- Single point of failure (needs HA setup)
- Performance bottleneck if not scaled
- Complexity vs capability trade-off (L4 vs L7)

**Key Metrics:**
```
1. Throughput: Requests/sec the LB can handle
   - L4 (TCP): Millions of connections/sec
   - L7 (HTTP): Tens of thousands of requests/sec

2. Latency overhead: Additional delay from LB
   - L4: < 1ms (just TCP forwarding)
   - L7: 1-10ms (needs to parse HTTP headers)

3. Connection distribution: How evenly traffic is spread
   - Perfect: Each server gets 1/N traffic
   - Reality: Depends on algorithm and request patterns
```

---

## 1. Load Balancing Algorithms

🎓 **PROFESSOR**: Each algorithm optimizes for different properties:

```
┌─────────────────────────────────────────────────────────────┐
│  ALGORITHM        │ FAIRNESS │ OVERHEAD │ STATE  │ USE CASE │
├─────────────────────────────────────────────────────────────┤
│ Round Robin       │  Good    │   O(1)   │  O(1)  │ Uniform  │
│ Weighted RR       │  Good    │   O(1)   │  O(N)  │ Hetero   │
│ Least Connections │  Better  │   O(N)   │  O(N)  │ Long con │
│ Least Response    │  Best    │   O(N)   │  O(N)  │ Varying  │
│ Random            │  Fair    │   O(1)   │  O(1)  │ Simple   │
│ IP Hash           │  N/A     │   O(1)   │  O(1)  │ Sticky   │
│ Consistent Hash   │  Good    │   O(logN)│  O(N)  │ Caching  │
└─────────────────────────────────────────────────────────────┘
```

### A. Round Robin

**Simple rotation through server list**

```python
class RoundRobinLoadBalancer:
    """
    Time Complexity: O(1) per request
    Space Complexity: O(1)

    Pros: Simple, fair for identical servers
    Cons: Doesn't account for server capacity or current load
    """

    def __init__(self, servers: List[Server]):
        self.servers = servers
        self.current_index = 0

    def get_next_server(self) -> Server:
        # Atomic operation (use lock in production)
        server = self.servers[self.current_index]
        self.current_index = (self.current_index + 1) % len(self.servers)
        return server

# Example usage
lb = RoundRobinLoadBalancer([
    Server("192.168.1.1"),
    Server("192.168.1.2"),
    Server("192.168.1.3")
])

# Requests go: S1 → S2 → S3 → S1 → S2 → S3 ...
for i in range(6):
    server = lb.get_next_server()
    print(f"Request {i} → {server.ip}")
```

**Output:**
```
Request 0 → 192.168.1.1
Request 1 → 192.168.1.2
Request 2 → 192.168.1.3
Request 3 → 192.168.1.1
Request 4 → 192.168.1.2
Request 5 → 192.168.1.3
```

🏗️ **ARCHITECT**: Round Robin works well when:
- All servers have identical capacity
- Requests take roughly the same time
- No session state (stateless services)

**Problem scenario:**
```
3 servers: S1 (fast), S2 (fast), S3 (slow - 10x slower)
Round Robin: Each gets 33% of requests
Result: S3 becomes bottleneck, 33% of requests are slow!

Solution: Use Weighted Round Robin
```

### B. Weighted Round Robin

**Give more requests to more capable servers**

```java
public class WeightedRoundRobinLoadBalancer {

    private List<ServerWeight> servers;
    private int currentIndex = 0;
    private int currentWeight = 0;
    private int maxWeight;
    private int gcdWeight;

    static class ServerWeight {
        Server server;
        int weight;  // Higher weight = more capacity

        ServerWeight(Server server, int weight) {
            this.server = server;
            this.weight = weight;
        }
    }

    public WeightedRoundRobinLoadBalancer(List<ServerWeight> servers) {
        this.servers = servers;
        this.maxWeight = servers.stream()
            .mapToInt(s -> s.weight)
            .max()
            .orElse(1);
        this.gcdWeight = calculateGCD();
    }

    /**
     * Smooth weighted round-robin algorithm (nginx implementation)
     *
     * Example: S1(weight=5), S2(weight=1), S3(weight=1)
     * Distribution: S1, S1, S1, S2, S1, S3, S1
     */
    public synchronized Server getNextServer() {
        while (true) {
            currentIndex = (currentIndex + 1) % servers.size();

            if (currentIndex == 0) {
                currentWeight = currentWeight - gcdWeight;
                if (currentWeight <= 0) {
                    currentWeight = maxWeight;
                }
            }

            ServerWeight sw = servers.get(currentIndex);
            if (sw.weight >= currentWeight) {
                return sw.server;
            }
        }
    }

    private int calculateGCD() {
        // Calculate GCD of all weights
        return servers.stream()
            .map(s -> s.weight)
            .reduce(this::gcd)
            .orElse(1);
    }

    private int gcd(int a, int b) {
        return b == 0 ? a : gcd(b, a % b);
    }
}

// Usage
WeightedRoundRobinLoadBalancer lb = new WeightedRoundRobinLoadBalancer(
    Arrays.asList(
        new ServerWeight(new Server("192.168.1.1"), 5),  // High capacity
        new ServerWeight(new Server("192.168.1.2"), 1),  // Low capacity
        new ServerWeight(new Server("192.168.1.3"), 1)   // Low capacity
    )
);

// S1 gets 5/7 ≈ 71% of requests, S2 and S3 each get 1/7 ≈ 14%
```

### C. Least Connections

**Send to server with fewest active connections**

```python
class LeastConnectionsLoadBalancer:
    """
    Time Complexity: O(N) per request (find minimum)
    Space Complexity: O(N) (track connections per server)

    Best for: Long-lived connections (WebSocket, database)
    """

    def __init__(self, servers: List[Server]):
        self.servers = servers
        self.connections = {server: 0 for server in servers}
        self.lock = threading.Lock()

    def get_next_server(self) -> Server:
        with self.lock:
            # Find server with minimum active connections
            server = min(self.servers,
                        key=lambda s: self.connections[s])
            self.connections[server] += 1
            return server

    def release_connection(self, server: Server):
        with self.lock:
            self.connections[server] -= 1
            if self.connections[server] < 0:
                self.connections[server] = 0

# Example: WebSocket load balancing
class WebSocketHandler:

    def __init__(self, lb: LeastConnectionsLoadBalancer):
        self.lb = lb

    async def handle_connection(self, websocket):
        # Get least loaded server
        server = self.lb.get_next_server()

        try:
            # Proxy WebSocket connection
            async with websockets.connect(server.url) as backend:
                # Connection stays open for minutes/hours
                await proxy_bidirectional(websocket, backend)
        finally:
            # Release connection when done
            self.lb.release_connection(server)
```

🏗️ **ARCHITECT**: Least Connections is critical for:
- Database connection pools
- WebSocket servers
- Long-polling APIs
- gRPC streaming

**Problem:** Requires tracking state. If load balancer restarts, connection counts reset to 0.

**Solution:** Use external state (Redis) or passive health checks to estimate connections.

### D. Least Response Time (Weighted)

**Send to server with lowest latency × active connections**

```java
@Service
public class LeastResponseTimeLoadBalancer {

    private final List<Server> servers;
    private final Map<Server, ServerMetrics> metrics;

    static class ServerMetrics {
        private final AtomicInteger activeConnections = new AtomicInteger(0);
        private final AtomicLong totalResponseTime = new AtomicLong(0);
        private final AtomicLong requestCount = new AtomicLong(0);

        public double getAverageResponseTime() {
            long count = requestCount.get();
            if (count == 0) return 0.0;
            return (double) totalResponseTime.get() / count;
        }

        public int getActiveConnections() {
            return activeConnections.get();
        }

        /**
         * Score = average_latency × (active_connections + 1)
         * Lower score is better
         */
        public double getScore() {
            return getAverageResponseTime() * (getActiveConnections() + 1);
        }

        public void recordRequest(long responseTimeMs) {
            totalResponseTime.addAndGet(responseTimeMs);
            requestCount.incrementAndGet();
        }
    }

    public Server getNextServer() {
        // Find server with lowest score
        return servers.stream()
            .min(Comparator.comparingDouble(s ->
                metrics.get(s).getScore()))
            .orElseThrow(() -> new NoServersAvailableException());
    }

    public void executeRequest(Callable<Response> request) {
        Server server = getNextServer();
        ServerMetrics metric = metrics.get(server);

        metric.activeConnections.incrementAndGet();
        long startTime = System.currentTimeMillis();

        try {
            Response response = request.call();
            return response;
        } finally {
            long responseTime = System.currentTimeMillis() - startTime;
            metric.recordRequest(responseTime);
            metric.activeConnections.decrementAndGet();
        }
    }
}
```

**Interview Gold**: "Least Response Time combines both server load AND performance. A fast server with 10 connections might be better choice than slow server with 5 connections."

### E. Consistent Hashing

**Maps requests to servers using hash ring**

```python
import bisect
import hashlib

class ConsistentHashingLoadBalancer:
    """
    Time Complexity: O(log N) per request
    Space Complexity: O(N × virtual_nodes)

    Use case: Distributed caching (Redis, Memcached)
    Benefit: Minimal cache invalidation when servers added/removed
    """

    def __init__(self, servers: List[Server], virtual_nodes: int = 150):
        self.virtual_nodes = virtual_nodes
        self.ring = {}  # hash -> server
        self.sorted_keys = []

        for server in servers:
            self.add_server(server)

    def _hash(self, key: str) -> int:
        """MD5 hash returns consistent 128-bit integer"""
        return int(hashlib.md5(key.encode()).hexdigest(), 16)

    def add_server(self, server: Server):
        """Add server with multiple virtual nodes"""
        for i in range(self.virtual_nodes):
            # Create virtual nodes: "192.168.1.1:0", "192.168.1.1:1", ...
            virtual_key = f"{server.ip}:{i}"
            hash_val = self._hash(virtual_key)

            self.ring[hash_val] = server
            bisect.insort(self.sorted_keys, hash_val)

    def remove_server(self, server: Server):
        """Remove server and its virtual nodes"""
        for i in range(self.virtual_nodes):
            virtual_key = f"{server.ip}:{i}"
            hash_val = self._hash(virtual_key)

            del self.ring[hash_val]
            self.sorted_keys.remove(hash_val)

    def get_server(self, key: str) -> Server:
        """
        Find server for given key (user ID, cache key, etc)
        Same key always maps to same server (unless server fails)
        """
        if not self.ring:
            return None

        hash_val = self._hash(key)

        # Binary search for next server clockwise on ring
        idx = bisect.bisect(self.sorted_keys, hash_val)
        if idx == len(self.sorted_keys):
            idx = 0

        return self.ring[self.sorted_keys[idx]]

# Example: Cache sharding
cache_lb = ConsistentHashingLoadBalancer([
    Server("cache-1.example.com"),
    Server("cache-2.example.com"),
    Server("cache-3.example.com")
])

# Same user always goes to same cache server
user_123_server = cache_lb.get_server("user:123")  # → cache-2
user_456_server = cache_lb.get_server("user:456")  # → cache-1

# Add new cache server
cache_lb.add_server(Server("cache-4.example.com"))

# Only ~25% of keys remapped (instead of 100% with simple hash!)
# user:123 still → cache-2 (stable)
```

**Why Virtual Nodes?**
```
Without virtual nodes (3 servers):
[Server1] owns 33% of hash ring

If Server1 fails:
All its traffic goes to Server2 → 66% load on Server2!

With 150 virtual nodes per server:
Server1's traffic distributed among ALL remaining servers
Each gets +16.5% load (much more balanced)
```

---

## 2. L4 vs L7 Load Balancing

🎓 **PROFESSOR**: OSI Model layers determine LB capabilities:

```
┌───────────────────────────────────────────────────────────────┐
│  LAYER 4 (Transport)          │  LAYER 7 (Application)        │
├───────────────────────────────────────────────────────────────┤
│  Works with: TCP/UDP          │  Works with: HTTP, gRPC, etc  │
│  Sees: IP, Port               │  Sees: Headers, Cookies, Path │
│  Routing: IP:Port → IP:Port   │  Routing: URL, Headers, Body  │
│  Throughput: ~10M req/sec     │  Throughput: ~100K req/sec    │
│  Latency: < 1ms               │  Latency: 1-10ms              │
│  SSL: Pass-through or offload │  SSL: Must terminate          │
│  Cost: Low (simple)           │  Cost: Higher (CPU-intensive) │
└───────────────────────────────────────────────────────────────┘
```

### Layer 4 Load Balancing (TCP/UDP)

```python
# Simplified L4 Load Balancer (like HAProxy in TCP mode)
import socket
import select

class L4LoadBalancer:
    """
    Pure TCP proxy - doesn't parse application protocol
    Just forwards bytes between client and server
    """

    def __init__(self, listen_port: int, backend_servers: List[str]):
        self.listen_port = listen_port
        self.backends = backend_servers
        self.lb_algorithm = RoundRobinLoadBalancer(
            [Server(addr) for addr in backend_servers]
        )

    def start(self):
        # Listen for client connections
        listener = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        listener.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        listener.bind(('0.0.0.0', self.listen_port))
        listener.listen(100)

        print(f"L4 LB listening on port {self.listen_port}")

        while True:
            # Accept client connection
            client_sock, client_addr = listener.accept()

            # Choose backend server
            backend = self.lb_algorithm.get_next_server()

            # Connect to backend
            backend_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            backend_sock.connect((backend.ip, backend.port))

            # Proxy bidirectional traffic (no parsing!)
            self.proxy_connection(client_sock, backend_sock)

    def proxy_connection(self, client_sock, backend_sock):
        """
        Bidirectional TCP proxy using select()
        Forwards raw bytes without interpreting them
        """
        sockets = [client_sock, backend_sock]

        while True:
            readable, _, exceptional = select.select(
                sockets, [], sockets, 60.0
            )

            if exceptional:
                break

            for sock in readable:
                data = sock.recv(4096)
                if not data:
                    return  # Connection closed

                # Forward to other socket
                if sock is client_sock:
                    backend_sock.sendall(data)  # Client → Backend
                else:
                    client_sock.sendall(data)   # Backend → Client
```

**L4 Benefits:**
```
✓ Protocol-agnostic: Works with HTTP, MySQL, Redis, anything TCP/UDP
✓ High performance: Just byte forwarding, minimal CPU
✓ SSL pass-through: Can forward encrypted traffic without decrypting
✓ Simple: Less code = fewer bugs
```

**L4 Limitations:**
```
✗ No content-based routing: Can't route /api to one backend, /images to another
✗ No header modification: Can't add X-Forwarded-For
✗ No caching: Doesn't understand HTTP
✗ No request logging: Doesn't see URLs
```

### Layer 7 Load Balancing (HTTP)

```java
// L7 Load Balancer (like nginx, Envoy)
@RestController
public class L7LoadBalancer {

    private final Map<String, LoadBalancingStrategy> routingRules;
    private final RestTemplate restTemplate;

    public L7LoadBalancer() {
        // Content-based routing rules
        routingRules = new HashMap<>();
        routingRules.put("/api/**", new LeastConnectionsLB(apiServers));
        routingRules.put("/images/**", new RoundRobinLB(imageServers));
        routingRules.put("/admin/**", new SpecificServerLB(adminServer));
    }

    /**
     * L7 routing: Parse HTTP request and route based on content
     */
    @RequestMapping("/**")
    public ResponseEntity<String> routeRequest(
            HttpServletRequest request,
            @RequestBody(required = false) String body) {

        String path = request.getRequestURI();
        String method = request.getMethod();

        // 1. CONTENT-BASED ROUTING
        LoadBalancingStrategy strategy = selectStrategy(path);

        // 2. HEADER INSPECTION (A/B testing example)
        String betaFeature = request.getHeader("X-Beta-User");
        if ("true".equals(betaFeature)) {
            strategy = betaServerStrategy;
        }

        // 3. COOKIE-BASED ROUTING (Canary deployment)
        String canary = getCookie(request, "canary");
        if ("v2".equals(canary)) {
            strategy = canaryServerStrategy;
        }

        // 4. GEO-BASED ROUTING
        String clientIP = request.getHeader("X-Forwarded-For");
        if (isEuropeIP(clientIP)) {
            strategy = europeanServerStrategy;
        }

        // Choose backend server
        Server backend = strategy.getNextServer();

        // 5. MODIFY REQUEST (add headers)
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Forwarded-For", clientIP);
        headers.add("X-Forwarded-Proto", request.getScheme());
        headers.add("X-Request-ID", UUID.randomUUID().toString());

        // 6. FORWARD REQUEST
        String backendUrl = String.format(
            "http://%s:%d%s",
            backend.getIp(),
            backend.getPort(),
            path
        );

        HttpEntity<String> entity = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                backendUrl,
                HttpMethod.valueOf(method),
                entity,
                String.class
            );

            // 7. MODIFY RESPONSE (add security headers)
            HttpHeaders responseHeaders = new HttpHeaders();
            responseHeaders.putAll(response.getHeaders());
            responseHeaders.add("X-Content-Type-Options", "nosniff");
            responseHeaders.add("X-Frame-Options", "DENY");

            return new ResponseEntity<>(
                response.getBody(),
                responseHeaders,
                response.getStatusCode()
            );

        } catch (HttpClientErrorException | HttpServerErrorException e) {
            // 8. ERROR HANDLING (retry, circuit breaker)
            return handleError(e, strategy);
        }
    }

    private LoadBalancingStrategy selectStrategy(String path) {
        return routingRules.entrySet().stream()
            .filter(entry -> pathMatches(path, entry.getKey()))
            .map(Map.Entry::getValue)
            .findFirst()
            .orElse(defaultStrategy);
    }
}
```

**L7 Capabilities:**

```
1. Content-based routing:
   /api/*     → API servers
   /static/*  → CDN edge servers
   /admin/*   → Admin servers

2. Header-based routing:
   User-Agent: Mobile → Mobile-optimized servers
   Accept-Language: es → Spanish servers

3. Canary deployments:
   10% traffic → v2.0 (new version)
   90% traffic → v1.0 (stable version)

4. A/B testing:
   Cookie: experiment=A → Feature A servers
   Cookie: experiment=B → Feature B servers

5. Rate limiting:
   Limit requests per IP, per user, per API key

6. SSL termination:
   Client → HTTPS → LB → HTTP → Backend
   (Offload encryption from backends)

7. Request/Response modification:
   Add headers, rewrite URLs, compress responses

8. Caching:
   Cache responses at LB (like Varnish)
```

**🏗️ ARCHITECT Decision Matrix:**

```
Use L4 when:
✓ Extremely high throughput needed (millions req/sec)
✓ Non-HTTP protocols (MySQL, Redis, gRPC)
✓ Minimal latency required
✓ Simple round-robin or least-connections sufficient

Use L7 when:
✓ Need content-based routing
✓ Need SSL termination
✓ Need request modification (headers, auth)
✓ Need caching, compression
✓ Need A/B testing, canary deployments

Common pattern: L4 + L7 hybrid
- L4 for geographic distribution (anycast)
- L7 for application routing within region
```

---

## 3. Health Checks & Failure Detection

🎓 **PROFESSOR**: Failure detection is a **distributed consensus problem**:

```
False positive: Marking healthy server as failed
  → Reduced capacity, potential overload

False negative: Marking failed server as healthy
  → Failed requests, poor user experience

Trade-off: Sensitivity vs Stability
```

### Active Health Checks

**Periodic probes to detect failures**

```python
import asyncio
import time
from enum import Enum

class HealthStatus(Enum):
    HEALTHY = "healthy"
    UNHEALTHY = "unhealthy"
    UNKNOWN = "unknown"

class HealthChecker:
    """
    Active health checking with configurable thresholds
    """

    def __init__(
        self,
        servers: List[Server],
        interval: int = 5,           # Check every 5 seconds
        timeout: int = 2,            # 2 second timeout
        unhealthy_threshold: int = 3, # 3 failures → unhealthy
        healthy_threshold: int = 2    # 2 successes → healthy
    ):
        self.servers = servers
        self.interval = interval
        self.timeout = timeout
        self.unhealthy_threshold = unhealthy_threshold
        self.healthy_threshold = healthy_threshold

        # Track consecutive successes/failures
        self.health_status = {s: HealthStatus.UNKNOWN for s in servers}
        self.consecutive_failures = {s: 0 for s in servers}
        self.consecutive_successes = {s: 0 for s in servers}

    async def check_server_health(self, server: Server) -> bool:
        """
        Perform health check - can be customized per application
        """
        try:
            # HTTP health check
            async with aiohttp.ClientSession() as session:
                async with session.get(
                    f"http://{server.ip}:{server.port}/health",
                    timeout=aiohttp.ClientTimeout(total=self.timeout)
                ) as response:

                    # Check 1: HTTP 200 status
                    if response.status != 200:
                        return False

                    # Check 2: Validate response body
                    data = await response.json()

                    # Check 3: Application-specific checks
                    if not data.get("database_connected"):
                        return False

                    if data.get("cpu_usage", 0) > 95:
                        return False  # Overloaded

                    if data.get("memory_usage", 0) > 90:
                        return False  # Memory pressure

                    return True

        except asyncio.TimeoutError:
            logger.warning(f"Health check timeout for {server.ip}")
            return False
        except Exception as e:
            logger.error(f"Health check failed for {server.ip}: {e}")
            return False

    async def monitor_server(self, server: Server):
        """
        Continuously monitor single server
        """
        while True:
            is_healthy = await self.check_server_health(server)

            if is_healthy:
                self.consecutive_successes[server] += 1
                self.consecutive_failures[server] = 0

                # Transition to healthy after N consecutive successes
                if (self.consecutive_successes[server] >=
                    self.healthy_threshold):
                    if self.health_status[server] != HealthStatus.HEALTHY:
                        logger.info(f"Server {server.ip} marked HEALTHY")
                        self.health_status[server] = HealthStatus.HEALTHY

            else:
                self.consecutive_failures[server] += 1
                self.consecutive_successes[server] = 0

                # Transition to unhealthy after N consecutive failures
                if (self.consecutive_failures[server] >=
                    self.unhealthy_threshold):
                    if self.health_status[server] != HealthStatus.UNHEALTHY:
                        logger.error(f"Server {server.ip} marked UNHEALTHY")
                        self.health_status[server] = HealthStatus.UNHEALTHY

                        # Alert operations team
                        await self.send_alert(server)

            await asyncio.sleep(self.interval)

    async def send_alert(self, server: Server):
        """Send alert when server fails"""
        # PagerDuty, Slack, email, etc.
        pass

    def get_healthy_servers(self) -> List[Server]:
        """Return only healthy servers for load balancing"""
        return [
            server for server in self.servers
            if self.health_status[server] == HealthStatus.HEALTHY
        ]

    async def start(self):
        """Start monitoring all servers"""
        tasks = [
            self.monitor_server(server)
            for server in self.servers
        ]
        await asyncio.gather(*tasks)
```

**Health Check Endpoint (Backend):**

```java
@RestController
public class HealthCheckController {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    /**
     * Shallow health check - just application status
     * Fast: < 10ms
     */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> basicHealth() {
        return ResponseEntity.ok(new HealthResponse(
            "UP",
            System.currentTimeMillis()
        ));
    }

    /**
     * Deep health check - validate all dependencies
     * Slower: 50-200ms, but more thorough
     */
    @GetMapping("/health/deep")
    public ResponseEntity<DeepHealthResponse> deepHealth() {
        DeepHealthResponse response = new DeepHealthResponse();

        // Check 1: Database connectivity
        try {
            dataSource.getConnection().close();
            response.setDatabaseConnected(true);
        } catch (Exception e) {
            response.setDatabaseConnected(false);
            response.setStatus("DEGRADED");
        }

        // Check 2: Cache connectivity
        try {
            redisTemplate.opsForValue().get("health-check");
            response.setCacheConnected(true);
        } catch (Exception e) {
            response.setCacheConnected(false);
            // Degraded but not failed (can run without cache)
        }

        // Check 3: System resources
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        double memoryUsage = (double) usedMemory / runtime.maxMemory() * 100;

        response.setMemoryUsage(memoryUsage);

        // Check 4: CPU usage (via OS metrics)
        double cpuUsage = getCPUUsage();
        response.setCpuUsage(cpuUsage);

        if (memoryUsage > 90 || cpuUsage > 95) {
            response.setStatus("OVERLOADED");
        }

        HttpStatus status = response.getStatus().equals("UP")
            ? HttpStatus.OK
            : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status).body(response);
    }
}
```

### Passive Health Checks (Circuit Breaker Pattern)

**Detect failures based on actual request results**

```java
@Service
public class PassiveHealthChecker {

    private final Map<Server, CircuitBreaker> circuitBreakers;

    static class CircuitBreaker {
        private final int failureThreshold;
        private final int successThreshold;
        private final long timeoutMs;

        private CircuitState state = CircuitState.CLOSED;
        private int consecutiveFailures = 0;
        private int consecutiveSuccesses = 0;
        private long openedAt = 0;

        enum CircuitState {
            CLOSED,      // Normal operation
            OPEN,        // Failing, reject requests immediately
            HALF_OPEN    // Testing if service recovered
        }

        public synchronized boolean allowRequest() {
            switch (state) {
                case CLOSED:
                    return true;  // Normal operation

                case OPEN:
                    // Check if timeout elapsed
                    if (System.currentTimeMillis() - openedAt > timeoutMs) {
                        state = CircuitState.HALF_OPEN;
                        consecutiveSuccesses = 0;
                        return true;  // Try one request
                    }
                    return false;  // Still open, reject

                case HALF_OPEN:
                    return true;  // Allow limited requests to test

                default:
                    return false;
            }
        }

        public synchronized void recordSuccess() {
            consecutiveFailures = 0;
            consecutiveSuccesses++;

            if (state == CircuitState.HALF_OPEN) {
                if (consecutiveSuccesses >= successThreshold) {
                    // Recovered! Close circuit
                    state = CircuitState.CLOSED;
                    logger.info("Circuit breaker CLOSED (recovered)");
                }
            }
        }

        public synchronized void recordFailure() {
            consecutiveSuccesses = 0;
            consecutiveFailures++;

            if (state == CircuitState.CLOSED) {
                if (consecutiveFailures >= failureThreshold) {
                    // Too many failures, open circuit
                    state = CircuitState.OPEN;
                    openedAt = System.currentTimeMillis();
                    logger.error("Circuit breaker OPEN (failing)");
                }
            } else if (state == CircuitState.HALF_OPEN) {
                // Failed during testing, reopen
                state = CircuitState.OPEN;
                openedAt = System.currentTimeMillis();
            }
        }

        public boolean isHealthy() {
            return state == CircuitState.CLOSED;
        }
    }

    public Server getHealthyServer(LoadBalancingStrategy strategy) {
        // Try to get server with closed circuit
        for (int attempt = 0; attempt < 3; attempt++) {
            Server server = strategy.getNextServer();
            CircuitBreaker cb = circuitBreakers.get(server);

            if (cb.allowRequest()) {
                return server;
            }
        }

        throw new NoHealthyServersException();
    }
}
```

**Comparison:**

```
┌──────────────────────────────────────────────────────────────┐
│  ACTIVE HEALTH CHECKS        │  PASSIVE HEALTH CHECKS         │
├──────────────────────────────────────────────────────────────┤
│  Proactive detection         │  Reactive detection            │
│  Extra network traffic       │  No extra traffic              │
│  Can check before routing    │  Learns from real requests     │
│  May miss application issues │  Detects real failure modes    │
│  Fixed interval overhead     │  No overhead when healthy      │
└──────────────────────────────────────────────────────────────┘

Best practice: Use BOTH
- Active: Prevent routing to failed servers
- Passive: Detect failures active checks miss
```

---

## 4. Session Persistence (Sticky Sessions)

🎓 **PROFESSOR**: Session affinity vs stateless design trade-off:

```
Stateless (ideal):
- Any server can handle any request
- Perfect load distribution
- Easy horizontal scaling
- No session loss on server failure

Stateful (reality):
- Some state must be maintained
- Session affinity required
- Uneven load distribution
- Complex failover
```

### A. IP Hash (Source IP Affinity)

```python
class IPHashLoadBalancer:
    """
    Route same client IP to same server (simple sticky sessions)
    """

    def __init__(self, servers: List[Server]):
        self.servers = servers

    def get_server(self, client_ip: str) -> Server:
        # Hash client IP to server index
        hash_value = hash(client_ip)
        index = hash_value % len(self.servers)
        return self.servers[index]

# Problem: Adding/removing server breaks ALL sessions!
lb = IPHashLoadBalancer([server1, server2, server3])

client_a = "192.168.1.100"
server = lb.get_server(client_a)  # → server2

# Add server4
lb = IPHashLoadBalancer([server1, server2, server3, server4])
server = lb.get_server(client_a)  # → server1 (different!)
# Session lost!
```

### B. Cookie-Based Session Affinity

```java
@Component
public class CookieBasedLoadBalancer {

    private final Map<String, Server> sessionMap = new ConcurrentHashMap<>();
    private final LoadBalancingStrategy fallbackStrategy;

    /**
     * Route based on session cookie
     * If no session, create one and set cookie
     */
    public Server getServer(HttpServletRequest request,
                           HttpServletResponse response) {

        // 1. Check for existing session cookie
        String sessionId = getSessionCookie(request);

        if (sessionId != null && sessionMap.containsKey(sessionId)) {
            Server server = sessionMap.get(sessionId);

            // Verify server is still healthy
            if (isHealthy(server)) {
                return server;
            } else {
                // Server failed, need to re-route
                sessionMap.remove(sessionId);
            }
        }

        // 2. No existing session or server failed - create new session
        sessionId = UUID.randomUUID().toString();
        Server server = fallbackStrategy.getNextServer();

        // 3. Store mapping
        sessionMap.put(sessionId, server);

        // 4. Set cookie
        Cookie cookie = new Cookie("LB_SESSION", sessionId);
        cookie.setMaxAge(3600);  // 1 hour
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        response.addCookie(cookie);

        return server;
    }

    /**
     * Clean up expired sessions
     */
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void cleanupExpiredSessions() {
        // In production, store sessions in Redis with TTL
        // Here we'd need to track last access time
        sessionMap.entrySet().removeIf(entry ->
            isSessionExpired(entry.getKey())
        );
    }
}
```

### C. Application-Level Session Clustering

**Better approach: Share session state across servers**

```java
@Configuration
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 1800)
public class SessionConfig {

    /**
     * Store sessions in Redis - any server can access
     * No sticky sessions needed!
     */
    @Bean
    public LettuceConnectionFactory connectionFactory() {
        return new LettuceConnectionFactory(
            new RedisStandaloneConfiguration("redis-cluster", 6379)
        );
    }
}

@RestController
public class UserController {

    @GetMapping("/profile")
    public UserProfile getProfile(HttpSession session) {
        // Session stored in Redis, not in application server memory
        // Request can be handled by ANY server

        String userId = (String) session.getAttribute("userId");
        return userService.getProfile(userId);
    }

    @PostMapping("/login")
    public LoginResponse login(
            @RequestBody LoginRequest request,
            HttpSession session) {

        User user = authService.authenticate(
            request.getUsername(),
            request.getPassword()
        );

        // Store in Redis-backed session
        session.setAttribute("userId", user.getId());
        session.setAttribute("roles", user.getRoles());

        return new LoginResponse(user.getId());
    }
}
```

**Architecture:**

```
Request 1: Client → LB → Server 1 → Redis (create session)
Request 2: Client → LB → Server 2 → Redis (read same session)
Request 3: Client → LB → Server 3 → Redis (read same session)

Benefits:
✓ No sticky sessions needed
✓ Perfect load distribution
✓ No session loss on server failure
✓ Horizontal scaling friendly

Drawbacks:
✗ Redis becomes single point of failure (need HA)
✗ Network latency for session reads
✗ Serialization overhead
```

---

## 5. Global vs Local Load Balancing

🎓 **PROFESSOR**: **Geographic distribution** introduces multi-level load balancing:

```
Layer 1: DNS/Anycast (Global)
         Route to nearest region
         ↓
Layer 2: L4 Load Balancer (Regional)
         Route to availability zone
         ↓
Layer 3: L7 Load Balancer (Local)
         Route to specific server
```

### Global Server Load Balancing (GSLB)

```python
class GlobalLoadBalancer:
    """
    DNS-based global load balancing
    Routes users to nearest data center
    """

    def __init__(self):
        self.regions = {
            "us-east-1": Region("Virginia", ["52.0.0.1", "52.0.0.2"]),
            "us-west-1": Region("California", ["54.0.0.1", "54.0.0.2"]),
            "eu-west-1": Region("Ireland", ["34.0.0.1", "34.0.0.2"]),
            "ap-south-1": Region("Mumbai", ["13.0.0.1", "13.0.0.2"])
        }

    def resolve_dns(self, client_ip: str, domain: str) -> str:
        """
        GeoDNS: Return IP of nearest data center
        """
        # 1. Geo-locate client
        client_location = geoip.lookup(client_ip)

        # 2. Find nearest region
        nearest_region = self.find_nearest_region(client_location)

        # 3. Check region health
        if not nearest_region.is_healthy():
            # Failover to next nearest healthy region
            nearest_region = self.find_next_nearest_region(
                client_location,
                exclude=[nearest_region]
            )

        # 4. Return IP from that region (round-robin within region)
        return nearest_region.get_next_ip()

    def find_nearest_region(self, location: GeoLocation) -> Region:
        """
        Calculate geographic distance to each region
        """
        min_distance = float('inf')
        nearest = None

        for region in self.regions.values():
            distance = self.haversine_distance(
                location,
                region.location
            )
            if distance < min_distance:
                min_distance = distance
                nearest = region

        return nearest

    def haversine_distance(self, loc1, loc2) -> float:
        """
        Calculate distance between two points on Earth
        """
        # Haversine formula for great circle distance
        # ... implementation ...
        pass
```

**DNS Response Example:**

```
User in New York queries api.example.com:
→ GeoDNS returns 52.0.0.1 (us-east-1 Virginia)

User in London queries api.example.com:
→ GeoDNS returns 34.0.0.1 (eu-west-1 Ireland)

User in Tokyo queries api.example.com:
→ GeoDNS returns 13.0.0.1 (ap-south-1 Mumbai)
```

### Complete Multi-Tier Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        CLIENT                               │
└────────────────────────┬────────────────────────────────────┘
                         ↓
                ┌────────────────────┐
                │   DNS / GeoDNS     │  ← Global routing
                │  (Route53, etc)    │
                └─────────┬──────────┘
                          ↓
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   [US-EAST-1]       [EU-WEST-1]        [AP-SOUTH-1]
        │                 │                  │
        ↓                 ↓                  ↓
   ┌─────────┐       ┌─────────┐        ┌─────────┐
   │   L4 LB │       │   L4 LB │        │   L4 LB │  ← Regional
   │(Anycast)│       │(Anycast)│        │(Anycast)│     (HA pair)
   └────┬────┘       └────┬────┘        └────┬────┘
        │                 │                  │
   ┌────┴────┐       ┌────┴────┐        ┌────┴────┐
   ↓         ↓       ↓         ↓        ↓         ↓
[AZ-1a]  [AZ-1b]  [AZ-1a]  [AZ-1b]  [AZ-1a]  [AZ-1b]
   │         │       │         │        │         │
   ↓         ↓       ↓         ↓        ↓         ↓
┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
│ L7LB │ │ L7LB │ │ L7LB │ │ L7LB │ │ L7LB │ │ L7LB │ ← Local
└──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘    (nginx)
   │        │        │        │        │        │
   ↓        ↓        ↓        ↓        ↓        ↓
[10 app] [10 app] [10 app] [10 app] [10 app] [10 app]
[servers][servers][servers][servers][servers][servers]

Total capacity: 6 AZs × 10 servers = 60 application servers
```

---

## 6. Consistent Hashing for Distributed Systems

(Covered earlier in algorithms section, but here's real-world application)

### Cache Sharding with Consistent Hashing

```java
@Service
public class DistributedCacheService {

    private final ConsistentHash<CacheNode> cacheRing;

    public DistributedCacheService(List<String> cacheNodes) {
        this.cacheRing = new ConsistentHash<>(
            150,  // virtual nodes per server
            cacheNodes.stream()
                .map(CacheNode::new)
                .collect(Collectors.toList())
        );
    }

    /**
     * Get from distributed cache
     * Same key always routes to same cache server
     */
    public Optional<String> get(String key) {
        CacheNode node = cacheRing.getNode(key);

        try {
            return Optional.ofNullable(
                node.getClient().get(key)
            );
        } catch (Exception e) {
            // Cache node failed
            logger.error("Cache node {} failed", node.getAddress());

            // Remove from ring
            cacheRing.removeNode(node);

            // Try next node (failover)
            CacheNode fallback = cacheRing.getNode(key);
            return Optional.ofNullable(
                fallback.getClient().get(key)
            );
        }
    }

    public void put(String key, String value, int ttlSeconds) {
        CacheNode node = cacheRing.getNode(key);
        node.getClient().setex(key, ttlSeconds, value);
    }

    /**
     * Handle cache node addition (scale up)
     */
    public void addCacheNode(String address) {
        CacheNode newNode = new CacheNode(address);
        cacheRing.addNode(newNode);

        // Only ~1/N keys need to be remapped
        // (where N = number of nodes)
        logger.info("Added cache node: {}", address);
    }

    /**
     * Handle cache node removal (scale down or failure)
     */
    public void removeCacheNode(String address) {
        CacheNode node = new CacheNode(address);
        cacheRing.removeNode(node);

        // Keys on this node redistributed to other nodes
        logger.info("Removed cache node: {}", address);
    }
}
```

---

## 7. Failure Modes & Resilience Patterns

🎓 **PROFESSOR**: Load balancer failures are catastrophic:

```
Single Load Balancer:
- SPOF (Single Point of Failure)
- If LB fails, entire system unavailable
- Availability = min(LB_availability, Server_availability)

Example:
- Servers: 99.99% uptime (4 nines)
- LB: 99.9% uptime (3 nines)
- System: 99.9% uptime (limited by LB!)
```

### High Availability Load Balancer Setup

```
┌──────────────────────────────────────────────────────────┐
│          ACTIVE-PASSIVE HA PAIR                          │
├──────────────────────────────────────────────────────────┤
│                                                           │
│    ┌──────────────┐          ┌──────────────┐            │
│    │   LB-1       │          │   LB-2       │            │
│    │  (MASTER)    │◄────────►│  (BACKUP)    │            │
│    │  ACTIVE      │ Heartbeat│  STANDBY     │            │
│    └──────┬───────┘          └──────────────┘            │
│           │                                               │
│           ↓                                               │
│    [Virtual IP: 10.0.0.100]  ← Floats between LBs        │
│                                                           │
└───────────────────────────────────────────────────────────┘
```

**Keepalived Configuration (VRRP Protocol):**

```bash
# /etc/keepalived/keepalived.conf on LB-1 (MASTER)

vrrp_instance VI_1 {
    state MASTER
    interface eth0
    virtual_router_id 51
    priority 101              # Higher priority = MASTER
    advert_int 1              # Heartbeat every 1 second

    authentication {
        auth_type PASS
        auth_pass secretpass
    }

    virtual_ipaddress {
        10.0.0.100/24         # Virtual IP (VIP)
    }

    # Health check script
    track_script {
        chk_haproxy
    }
}

vrrp_script chk_haproxy {
    script "/usr/bin/killall -0 haproxy"  # Check if HAProxy running
    interval 2                             # Every 2 seconds
    weight -20                             # Reduce priority if failed
    fall 2                                 # Fail after 2 failures
    rise 2                                 # Recover after 2 successes
}
```

```bash
# /etc/keepalived/keepalived.conf on LB-2 (BACKUP)

vrrp_instance VI_1 {
    state BACKUP
    interface eth0
    virtual_router_id 51
    priority 100              # Lower priority = BACKUP
    advert_int 1

    authentication {
        auth_type PASS
        auth_pass secretpass
    }

    virtual_ipaddress {
        10.0.0.100/24         # Same VIP
    }

    track_script {
        chk_haproxy
    }
}
```

**Failover Behavior:**

```
Normal operation:
- LB-1 (MASTER) sends heartbeat every 1s
- LB-2 (BACKUP) receives heartbeat, stays passive
- VIP 10.0.0.100 assigned to LB-1

LB-1 fails:
- LB-2 stops receiving heartbeat
- After 3 seconds (3 missed heartbeats)
- LB-2 promotes itself to MASTER
- LB-2 assigns VIP to itself
- Clients continue working (transparent failover)

LB-1 recovers:
- LB-1 comes back online
- Sees LB-2 is MASTER with higher priority
- LB-1 becomes MASTER again (preempt mode)
- VIP moves back to LB-1
```

### Load Balancer Failure Scenarios

```java
// Handling backend server failures
@Service
public class ResilientLoadBalancer {

    private final List<Server> allServers;
    private final HealthChecker healthChecker;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    /**
     * Get server with multiple fallback strategies
     */
    public Server getServer() throws NoServersAvailableException {
        // 1. Try primary algorithm (least connections)
        List<Server> healthyServers = healthChecker.getHealthyServers();

        if (healthyServers.isEmpty()) {
            // 2. ALL servers failed health checks
            // Fallback: Try servers with open circuit breakers
            logger.error("No healthy servers, trying degraded mode");

            healthyServers = allServers.stream()
                .filter(s -> circuitBreakerRegistry.get(s).allowRequest())
                .collect(Collectors.toList());
        }

        if (healthyServers.isEmpty()) {
            // 3. Last resort: Return ANY server (fail open)
            logger.error("Complete failure, failing open");

            if (!allServers.isEmpty()) {
                // Better to try and fail than reject all requests
                return allServers.get(0);
            }

            // 4. Total failure
            throw new NoServersAvailableException(
                "All backend servers unavailable"
            );
        }

        // Route to least loaded healthy server
        return healthyServers.stream()
            .min(Comparator.comparingInt(Server::getActiveConnections))
            .get();
    }

    /**
     * Execute request with retries and fallback
     */
    @Retry(name = "backend", maxAttempts = 3)
    @CircuitBreaker(name = "backend", fallbackMethod = "serveCachedResponse")
    public Response executeRequest(Request request) {
        Server server = getServer();

        try {
            return server.execute(request);
        } catch (Exception e) {
            // Mark server as potentially failed
            circuitBreakerRegistry.get(server).recordFailure();
            throw e;  // Retry on different server
        }
    }

    /**
     * Fallback when all retries exhausted
     */
    public Response serveCachedResponse(Request request, Exception e) {
        logger.warn("Serving stale cached response due to: {}",
                   e.getMessage());

        // Serve cached/stale response
        Optional<Response> cached = cache.get(request.getUrl());

        if (cached.isPresent()) {
            Response response = cached.get();
            response.addHeader("Warning", "110 - Response is Stale");
            return response;
        }

        // No cache available
        throw new ServiceUnavailableException(
            "Service temporarily unavailable"
        );
    }
}
```

---

## 🎯 SYSTEM DESIGN INTERVIEW FRAMEWORK

### 1. Requirements Clarification

```
Functional Requirements:
- Distribute traffic across N servers
- Support HTTP/HTTPS (or TCP/UDP?)
- Health checking and automatic failover
- SSL termination needed?
- Session persistence required?
- Content-based routing needed?

Non-Functional Requirements:
- Throughput: X requests/sec
- Latency: P99 < Yms
- Availability: 99.99% (need HA setup)
- Geographic distribution: Single region or multi-region?
- Scale: Number of backend servers (10? 1000?)
```

### 2. Load Balancer Placement

```
Option 1: DNS Load Balancing
Client → DNS → Nearest Server
Pros: Simple, no single point of failure
Cons: Can't detect failures quickly, client caching

Option 2: Hardware Load Balancer
Client → F5/Citrix → Servers
Pros: High performance (10M+ req/sec)
Cons: Expensive, vendor lock-in

Option 3: Software Load Balancer
Client → HAProxy/nginx → Servers
Pros: Flexible, free/cheap, programmable
Cons: Need to handle scaling, HA

Option 4: Cloud Load Balancer
Client → AWS ALB/NLB → Servers
Pros: Managed, auto-scaling, integrated
Cons: Cost, cloud lock-in

Recommendation: Software LB (nginx/HAProxy) in HA pair
```

### 3. High-Level Design

```
┌────────────────────────────────────────────────────────────┐
│                         CLIENT                             │
└──────────────────────────┬─────────────────────────────────┘
                           ↓
                    ┌─────────────┐
                    │     DNS     │
                    │ (Round Robin│
                    │  to LB IPs) │
                    └──────┬──────┘
                           ↓
              ┌────────────┴────────────┐
              ↓                         ↓
     ┌────────────────┐        ┌────────────────┐
     │  Load Balancer │◄──────►│  Load Balancer │
     │   (MASTER)     │ VRRP   │   (BACKUP)     │
     │  - HAProxy     │        │  - HAProxy     │
     │  - Keepalived  │        │  - Keepalived  │
     └────────┬───────┘        └────────────────┘
              │
     [Virtual IP]
              │
     ┌────────┴──────────────────┬───────────────┐
     ↓                           ↓               ↓
┌──────────┐              ┌──────────┐    ┌──────────┐
│ Server 1 │              │ Server 2 │    │ Server 3 │
│          │              │          │    │          │
│ /health  │              │ /health  │    │ /health  │
└──────────┘              └──────────┘    └──────────┘
     ↓                           ↓               ↓
┌──────────────────────────────────────────────────────┐
│            SHARED SESSION STORE (Redis)              │
└──────────────────────────────────────────────────────┘
```

### 4. Deep Dives

**A. Algorithm Selection**

```
For this design, I'd use:

Primary: Least Connections
- Good for varying request durations
- Adapts to server performance differences

Fallback: Weighted Round Robin
- If connection tracking fails
- Weights based on server capacity

Session Affinity: Cookie-based
- For stateful applications
- Fallback to session sharing (Redis)
```

**B. Health Check Strategy**

```java
// Active + Passive hybrid
@Configuration
public class HealthCheckConfig {

    @Bean
    public HealthChecker activeHealthChecker() {
        return HealthChecker.builder()
            .interval(Duration.ofSeconds(5))
            .timeout(Duration.ofSeconds(2))
            .unhealthyThreshold(3)  // 15 seconds to mark unhealthy
            .healthyThreshold(2)    // 10 seconds to recover
            .endpoint("/health")
            .expectedStatus(200)
            .build();
    }

    @Bean
    public CircuitBreakerConfig passiveHealthChecker() {
        return CircuitBreakerConfig.custom()
            .failureRateThreshold(50)        // 50% errors
            .slowCallRateThreshold(50)       // 50% slow calls
            .slowCallDurationThreshold(Duration.ofSeconds(3))
            .waitDurationInOpenState(Duration.ofSeconds(30))
            .build();
    }
}
```

**C. Monitoring & Metrics**

```
Key Metrics to Track:
1. Request rate (req/sec)
2. Error rate (% of 5xx responses)
3. Latency distribution (P50, P95, P99)
4. Backend server health status
5. Connection pool utilization
6. SSL handshake time
7. Load balancer CPU/memory
8. Connection timeouts
9. Retry rate
10. Circuit breaker state

Alerts:
- Error rate > 5%
- P99 latency > 500ms
- Any server unhealthy > 1 minute
- LB CPU > 80%
- Connection pool exhausted
```

---

## 🧠 MIND MAP: LOAD BALANCING CONCEPTS

```
                    LOAD BALANCING
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
   ALGORITHMS         LAYERS            HEALTH CHECKS
        |                 |                  |
   ┌────┴────┐       ┌────┴────┐        ┌────┴────┐
   ↓         ↓       ↓         ↓        ↓         ↓
Round     Least    L4(TCP)  L7(HTTP)  Active   Passive
Robin   Connections         Content   Probes   Circuit
  |         |       |         |        |      Breakers
Weighted  Response Network  Headers  Interval Thresholds
       Consistent  Forwarding Routing
         Hashing
           |
      Cache Sharding
      Minimal Remap
```

---

## 💡 EMOTIONAL ANCHORS

**1. Load Balancer = Restaurant Host 🍽️**
- Greets customers (accepts connections)
- Seats them at tables (routes to servers)
- Avoids overloading one waiter (balanced distribution)
- Doesn't seat at closed section (health checks)
- Remembers your table if you step out (session affinity)

**2. Round Robin = Taking Turns ⚽**
- Kids taking turns on swing
- Fair, but doesn't account for some kids swinging longer
- Weighted round robin = adults get more time than kids

**3. Least Connections = Shortest Checkout Line 🛒**
- Choose register with fewest customers
- But doesn't account for customer with full cart vs. one item
- Least response time = shortest wait time (better metric)

**4. Consistent Hashing = Library Book Storage 📚**
- Each book has designated shelf (hash)
- If shelf removed, books go to next shelf
- Only nearby books affected, not all books

**5. Health Check = Taking Pulse 💓**
- Active: Doctor checks pulse regularly
- Passive: Notice patient fell down (reactive)
- Both together give complete picture

---

## 📊 DECISION MATRIX

```
┌──────────────────────────────────────────────────────────────┐
│  REQUIREMENT             │    L4     │    L7     │   DNS     │
├──────────────────────────────────────────────────────────────┤
│ High throughput          │    ✓✓     │     ✓     │    ✓✓     │
│ Low latency              │    ✓✓     │     ✓     │     ✗     │
│ Content routing          │     ✗     │    ✓✓     │     ✗     │
│ SSL termination          │     ✓     │    ✓✓     │     ✗     │
│ Session persistence      │     ✓     │    ✓✓     │     ✗     │
│ Cost efficiency          │    ✓✓     │     ✓     │    ✓✓     │
│ Easy setup               │    ✓✓     │     ✓     │    ✓✓     │
│ Health checking          │    ✓✓     │    ✓✓     │     ✗     │
│ Protocol support         │   ALL     │   HTTP    │    ALL    │
│ Global distribution      │     ✗     │     ✗     │    ✓✓     │
└──────────────────────────────────────────────────────────────┘

Recommendation:
- Use ALL three in layers
- DNS: Global routing
- L4: Regional distribution
- L7: Application routing
```

---

## 🎓 PROFESSOR'S FINAL WISDOM

**Fundamental Principles:**

```
1. QUEUEING THEORY (M/M/c model)
   Average wait time = λ / (c×μ - λ)
   Where: λ=arrival rate, μ=service rate, c=servers

   Adding servers reduces wait time sublinearly
   10 servers ≠ 10x faster than 1 server

2. AMDAHL'S LAW
   Speedup limited by non-parallelizable parts
   If 5% of work can't be distributed, max speedup = 20x

3. LITTLE'S LAW
   L = λ × W
   To reduce latency (W), must either:
   - Reduce arrival rate (λ) - rate limiting
   - Add capacity - more servers

4. CAP THEOREM
   LB with session affinity sacrifices availability
   for consistency (same user → same server)
```

## 🏗️ ARCHITECT'S FINAL WISDOM

**Production Patterns:**

```
1. ALWAYS use HA pair for load balancers
   Single LB = single point of failure

2. PREFER stateless backends
   Session affinity creates uneven load distribution

3. USE both active and passive health checks
   Active: Prevent routing to failed servers
   Passive: Detect real failure modes

4. IMPLEMENT proper retry logic
   Exponential backoff, circuit breakers
   But beware retry storms!

5. MONITOR everything
   Request rate, error rate, latency
   Can't fix what you can't measure

6. PLAN for failure
   - LB fails → HA pair takeover
   - Server fails → Health check removes it
   - Region fails → DNS failover
   - Everything fails → Serve cached/stale

7. LOAD TEST before production
   Simulate realistic traffic patterns
   Test failure scenarios
```

**Common Pitfalls:**

```
❌ Using round-robin for variable-duration requests
❌ No health checks (routing to dead servers)
❌ Single load balancer (SPOF)
❌ Session affinity without backup strategy
❌ Ignoring connection pool limits
❌ Not monitoring health check false positives
❌ Forgetting about SSL handshake cost
❌ No retry budget (retry storms)
```

---

**Key Takeaway**: Load balancing is not just distributing requests—it's about reliability, performance, and graceful degradation under failure.

> "The load balancer is the most critical component in your architecture. If it fails, everything fails. Plan accordingly."
