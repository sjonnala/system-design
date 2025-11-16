# Service Discovery & Service Mesh

## The Service Discovery Problem

**Challenge**: In dynamic distributed systems, how do services find each other?

**Traditional Approach** (Hardcoded):
```
app.config:
  database_host: "db.example.com:5432"
  cache_host: "cache.example.com:6379"
  api_host: "api.example.com:8080"
```

**Problem**:
- Hardcoded addresses
- Manual updates when services move
- No automatic failover
- Doesn't scale in cloud/containers

**Modern Challenge** (Microservices):
```
100 services × 10 instances each = 1,000 endpoints
Instances constantly:
- Starting (auto-scaling)
- Stopping (failures)
- Moving (rebalancing)
- Changing (deployments)

How does Service A find Service B?
```

---

## Service Discovery Patterns

### Client-Side Discovery

**Pattern**: Client queries service registry and load balances

```
┌────────┐
│ Client │
└───┬────┘
    │ 1. Query "api-service"
    ▼
┌─────────────────┐
│Service Registry │
│(Consul/Eureka) │
└─────────────────┘
    │ 2. Returns: [10.0.1.5:8080, 10.0.1.6:8080, 10.0.1.7:8080]
    ▼
┌────────┐
│ Client │──┐
└────────┘  │ 3. Pick instance (load balance)
            │ 4. Call directly
            ▼
    ┌──────────────┐
    │API Instance 2│
    │ 10.0.1.6     │
    └──────────────┘
```

**Implementation**:
```python
class ServiceDiscoveryClient:
    def __init__(self, registry):
        self.registry = registry  # Consul, Eureka, etc.
        self.cache = {}

    def get_service(self, service_name):
        # Query registry
        instances = self.registry.get_instances(service_name)

        # Client-side load balancing
        return self.load_balance(instances)

    def load_balance(self, instances):
        # Round-robin, random, least connections, etc.
        return random.choice(instances)

# Usage
client = ServiceDiscoveryClient(consul)
api_instance = client.get_service("api-service")
response = http.get(f"http://{api_instance}/endpoint")
```

**Pros**:
- Simple architecture
- Client controls load balancing
- No extra network hop

**Cons**:
- Client complexity (every client needs discovery logic)
- Couples clients to registry
- Load balancing logic in every service

**Used By**: Netflix Eureka, HashiCorp Consul (client mode)

### Server-Side Discovery

**Pattern**: Load balancer queries registry and routes

```
┌────────┐
│ Client │
└───┬────┘
    │ 1. Call "api-service" (logical name)
    ▼
┌──────────────┐
│Load Balancer │
│ (AWS ELB)    │
└───┬──────────┘
    │ 2. Query registry: Where is "api-service"?
    ▼
┌─────────────────┐
│Service Registry │
└─────────────────┘
    │ 3. Returns instances
    ▼
┌──────────────┐
│Load Balancer │
└───┬──────────┘
    │ 4. Route to instance
    ▼
┌──────────────┐
│API Instance 3│
└──────────────┘
```

**Implementation** (Kubernetes):
```yaml
# Service definition
apiVersion: v1
kind: Service
metadata:
  name: api-service
spec:
  selector:
    app: api
  ports:
  - port: 80
    targetPort: 8080

# Client code
http.get("http://api-service/endpoint")
# Kubernetes DNS + Service handles discovery
```

**Pros**:
- Simple clients (just use service name)
- Centralized load balancing
- Language-agnostic

**Cons**:
- Extra network hop (through load balancer)
- Load balancer can be bottleneck
- Platform-dependent (Kubernetes, AWS)

**Used By**: Kubernetes Services, AWS ELB + Route53, Azure Load Balancer

---

## Service Registry Implementations

### Consul (HashiCorp)

**Architecture**:
```
┌─────────────┐     ┌─────────────┐     ┌─────────────┐
│Consul Server│◄───►│Consul Server│◄───►│Consul Server│
│   (Leader)  │     │  (Follower) │     │  (Follower) │
└──────▲──────┘     └──────▲──────┘     └──────▲──────┘
       │                   │                   │
       │                   │                   │
┌──────┴──────┐     ┌──────┴──────┐     ┌──────┴──────┐
│Consul Agent │     │Consul Agent │     │Consul Agent │
│  (Client)   │     │  (Client)   │     │  (Client)   │
└──────┬──────┘     └──────┬──────┘     └──────┬──────┘
       │                   │                   │
┌──────▼──────┐     ┌──────▼──────┐     ┌──────▼──────┐
│   Service   │     │   Service   │     │   Service   │
│  Instance   │     │  Instance   │     │  Instance   │
└─────────────┘     └─────────────┘     └─────────────┘
```

**Features**:
- Service registration & discovery
- Health checking
- Key/value store
- Multi-datacenter support
- DNS interface

**Registration**:
```json
{
  "service": {
    "name": "api-service",
    "tags": ["v1", "production"],
    "port": 8080,
    "check": {
      "http": "http://localhost:8080/health",
      "interval": "10s"
    }
  }
}
```

**Discovery** (HTTP API):
```bash
curl http://consul:8500/v1/catalog/service/api-service

Response:
[
  {
    "ServiceID": "api-1",
    "ServiceName": "api-service",
    "ServiceAddress": "10.0.1.5",
    "ServicePort": 8080,
    "ServiceTags": ["v1"]
  },
  {
    "ServiceID": "api-2",
    "ServiceName": "api-service",
    "ServiceAddress": "10.0.1.6",
    "ServicePort": 8080
  }
]
```

**Discovery** (DNS):
```bash
dig @consul api-service.service.consul

Response:
api-service.service.consul. 0 IN A 10.0.1.5
api-service.service.consul. 0 IN A 10.0.1.6
```

### etcd (CoreOS)

**Used By**: Kubernetes for service discovery

**Architecture**:
- Distributed key-value store
- Raft consensus for consistency
- Watch API for real-time updates

**Kubernetes Integration**:
```
Service Created:
  → Endpoints written to etcd
  → kubelet watches etcd
  → Updates iptables rules
  → Traffic routed to pods
```

**Features**:
- Strong consistency (CP system)
- Watch for changes (reactive)
- TTL for automatic cleanup
- Cluster coordination

### Netflix Eureka

**Architecture**: AP system (availability over consistency)

**Registration**:
```java
@EnableEurekaClient
@SpringBootApplication
public class ApiService {
    public static void main(String[] args) {
        SpringApplication.run(ApiService.class, args);
    }
}

// application.yml
eureka:
  client:
    serviceUrl:
      defaultZone: http://eureka-server:8761/eureka/
  instance:
    leaseRenewalIntervalInSeconds: 30
```

**Discovery**:
```java
@Autowired
private DiscoveryClient discoveryClient;

List<ServiceInstance> instances =
    discoveryClient.getInstances("api-service");

ServiceInstance instance = instances.get(0);
String url = instance.getUri() + "/endpoint";
```

**Features**:
- Self-registration
- Heartbeat-based health
- Client-side caching
- Multi-zone support

---

## Health Checking

### Active Health Checks

**HTTP Health Check**:
```python
@app.route('/health')
def health():
    # Check dependencies
    if not database.ping():
        return {"status": "unhealthy", "reason": "db down"}, 503

    if not cache.ping():
        return {"status": "degraded", "reason": "cache down"}, 200

    return {"status": "healthy"}, 200
```

**TCP Health Check**:
```
Load Balancer → Attempts TCP connection
If successful: Healthy
If fails/timeout: Unhealthy
```

**Script Health Check**:
```bash
#!/bin/bash
# Custom health check script

# Check process running
if ! pgrep -f "java.*app.jar" > /dev/null; then
    exit 1
fi

# Check listening on port
if ! netstat -ln | grep ":8080" > /dev/null; then
    exit 1
fi

# Check application health
if ! curl -f http://localhost:8080/health > /dev/null 2>&1; then
    exit 1
fi

exit 0
```

### Passive Health Checks

**Monitor actual traffic**:
```
Load Balancer observes:
- Request success rate
- Response times
- Error codes (5xx)

If error rate > 10% for 30s:
  → Mark instance unhealthy
  → Remove from rotation
```

**Circuit Breaker Integration**:
```
Service A → Service B:

Track last 100 requests:
- 15 failures
- Failure rate: 15%

If failure rate > 10%:
  → Open circuit breaker
  → Stop sending traffic to B
  → Mark B as unhealthy in discovery
```

---

## Service Mesh

### What is a Service Mesh?

**Definition**: Infrastructure layer for service-to-service communication

**Problem It Solves**:
```
Without Service Mesh:
Every service needs:
- Service discovery logic
- Load balancing
- Retries
- Timeouts
- Circuit breakers
- Monitoring
- Mutual TLS
- Distributed tracing

= Code duplication across all services
= Language-specific libraries
= Hard to enforce policies
```

**With Service Mesh**:
```
┌─────────────┐
│   Service   │ ← Business logic only
└──────┬──────┘
       │
┌──────▼──────┐
│Sidecar Proxy│ ← Service mesh (handles all networking)
└─────────────┘

Sidecar handles:
- Discovery
- Load balancing
- Security
- Observability
- Resilience
```

### Istio Architecture

```
                    ┌────────────────────┐
                    │   Control Plane    │
                    │                    │
                    │ ┌────────────────┐ │
                    │ │     Pilot      │ │ ← Service discovery, routing rules
                    │ └────────────────┘ │
                    │ ┌────────────────┐ │
                    │ │    Citadel     │ │ ← Certificate management, mTLS
                    │ └────────────────┘ │
                    │ ┌────────────────┐ │
                    │ │     Galley     │ │ ← Configuration validation
                    │ └────────────────┘ │
                    └──────────┬─────────┘
                               │
                               │ xDS API (configuration)
                               │
        ┌──────────────────────┼──────────────────────┐
        │                      │                      │
┌───────▼────────┐     ┌───────▼────────┐     ┌──────▼─────────┐
│   Data Plane   │     │   Data Plane   │     │   Data Plane   │
│                │     │                │     │                │
│ ┌────────────┐ │     │ ┌────────────┐ │     │ ┌────────────┐ │
│ │  Service A │ │     │ │  Service B │ │     │ │  Service C │ │
│ └─────┬──────┘ │     │ └─────┬──────┘ │     │ └─────┬──────┘ │
│       │        │     │       │        │     │       │        │
│ ┌─────▼──────┐ │     │ ┌─────▼──────┐ │     │ ┌─────▼──────┐ │
│ │Envoy Proxy │◄├─────┼─►Envoy Proxy │◄├─────┼─►Envoy Proxy │ │
│ └────────────┘ │     │ └────────────┘ │     │ └────────────┘ │
└────────────────┘     └────────────────┘     └────────────────┘
```

**Key Components**:

**Envoy Proxy** (Data Plane):
- Layer 7 proxy
- Service discovery
- Load balancing
- TLS termination
- HTTP/2, gRPC support
- Observability (metrics, traces)

**Pilot** (Control Plane):
- Service discovery
- Traffic management
- Routing rules
- Resilience (retries, timeouts)

**Citadel**:
- Certificate authority
- Automatic mTLS between services
- Identity management

### Traffic Management with Istio

**Virtual Service** (Routing Rules):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: api-service
spec:
  hosts:
  - api-service
  http:
  - match:
    - headers:
        version:
          exact: v2
    route:
    - destination:
        host: api-service
        subset: v2
  - route:
    - destination:
        host: api-service
        subset: v1
      weight: 90
    - destination:
        host: api-service
        subset: v2
      weight: 10
```

**Destination Rule** (Load Balancing, Circuit Breaker):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: api-service
spec:
  host: api-service
  trafficPolicy:
    loadBalancer:
      simple: LEAST_REQUEST
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 50
        maxRequestsPerConnection: 2
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
```

**Benefits**:
```
1. Gradual Rollout (Canary):
   - Route 10% to v2, 90% to v1
   - Monitor metrics
   - Gradually increase to 100% v2

2. A/B Testing:
   - Route users with header "beta=true" to v2
   - Others to v1

3. Traffic Mirroring:
   - Send production traffic to v2 (shadow)
   - Don't return responses
   - Test new version with real traffic

4. Fault Injection:
   - Inject delays: Test timeout handling
   - Inject errors: Test retry logic
```

### Observability

**Automatic Metrics**:
```
Envoy automatically tracks:
- Request rate
- Error rate
- Latency (p50, p95, p99)
- Request size/response size

Exposed as Prometheus metrics:
istio_requests_total{...}
istio_request_duration_milliseconds{...}
istio_request_bytes{...}
```

**Distributed Tracing**:
```
Request flow:
Client → Service A → Service B → Service C

Trace:
┌─────────────────────────────────────────┐
│ Trace ID: abc123                        │
│                                         │
│ Span 1: Service A (100ms)               │
│   ├─ Span 2: Service B (60ms)           │
│   │   └─ Span 3: Service C (30ms)       │
│   └─ Span 4: Database (20ms)            │
└─────────────────────────────────────────┘

Visualized in Jaeger/Zipkin
```

**Service Graph**:
```
Kiali visualizes:
- Service dependencies
- Request flow
- Error rates
- Traffic patterns
```

### Linkerd

**Lighter Alternative to Istio**:

**Architecture**:
```
Similar sidecar pattern:
- linkerd2-proxy (Rust-based, lightweight)
- Control plane (simpler than Istio)
- Automatic mTLS
- Service discovery
- Load balancing
```

**Key Differences**:
- Simpler (easier to learn)
- Lighter weight (less resource usage)
- Faster (Rust proxy)
- Less features (vs Istio)
- Opinionated defaults

**Installation**:
```bash
# Install Linkerd
linkerd install | kubectl apply -f -

# Inject sidecar into namespace
kubectl annotate namespace default linkerd.io/inject=enabled

# Any new pods automatically get sidecar
```

---

## Interview Q&A

**Q: "Design service discovery for a microservices architecture with 100 services"**

**Answer**:
```
I'd use Kubernetes-native service discovery with Consul for cross-cluster:

Architecture:

1. Within Cluster (Kubernetes):
   - Use Kubernetes Services (DNS-based)
   - Example: http://user-service.default.svc.cluster.local
   - Automatic registration (no code changes)
   - kube-dns resolves to pod IPs
   - kube-proxy load balances

2. Cross-Cluster/Multi-Cloud (Consul):
   - Deploy Consul servers (3-5 nodes, Raft quorum)
   - Consul agents on each K8s node
   - Sync K8s services to Consul
   - Federate Consul datacenters

3. Service Mesh (Istio):
   - Envoy sidecars for advanced routing
   - Automatic mTLS between services
   - Traffic management (canary, A/B)
   - Circuit breakers, retries
   - Observability (Prometheus + Jaeger)

Implementation:

Service Registration:
```yaml
# Kubernetes handles automatically
apiVersion: v1
kind: Service
metadata:
  name: user-service
  annotations:
    consul.hashicorp.com/service-sync: "true"
spec:
  selector:
    app: user-service
  ports:
  - port: 80
    targetPort: 8080
```

Service Discovery:
```python
# In-cluster (DNS)
response = requests.get("http://user-service/api/users")

# Cross-cluster (Consul)
import consul
c = consul.Consul()
index, services = c.catalog.service('user-service')
instance = random.choice(services)
response = requests.get(f"http://{instance['Address']}:{instance['ServicePort']}/api/users")
```

Health Checking:
- Kubernetes liveness/readiness probes
- Consul health checks (HTTP, TCP, Script)
- Istio outlier detection

Load Balancing:
- Kubernetes: Round-robin (kube-proxy)
- Istio: Least request, consistent hash, etc.

Why This Design:
- Kubernetes Services: Simple, native, zero code
- Consul: Multi-datacenter, flexibility
- Istio: Advanced features, observability
- Layered approach: Use simple where possible, complex when needed

Trade-offs:
- Complexity: Multiple systems to manage
- But: Each layer has clear purpose
- Kubernetes: 80% of use cases
- Consul: Multi-cluster, hybrid cloud
- Istio: Advanced traffic management
```

---

## Key Takeaways

1. **Service Discovery Essential**: Dynamic systems need automatic service location
2. **Client vs Server Side**: Client-side (more control) vs Server-side (simpler clients)
3. **Health Checking Critical**: Automatically remove unhealthy instances
4. **Service Mesh**: Offload networking concerns from application code
5. **Choose Based on Scale**: DNS for simple, mesh for complex microservices

---

## Further Reading

- "Building Microservices" - Sam Newman (Chapter on Service Discovery)
- Consul Documentation - HashiCorp
- Istio Documentation - istio.io
- "Service Mesh Patterns" - various authors
- Kubernetes Services Documentation
- Envoy Proxy Architecture
- Linkerd Documentation
