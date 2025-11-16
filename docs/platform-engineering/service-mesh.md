# Service Mesh

A service mesh is an infrastructure layer that handles service-to-service communication in microservices architectures, providing observability, security, and traffic management without requiring application code changes.

## Core Concepts

### What is a Service Mesh?

**Definition**: A dedicated infrastructure layer for managing service-to-service communication with features like:
- Traffic management (routing, load balancing)
- Security (mTLS, authentication, authorization)
- Observability (metrics, logs, tracing)
- Resilience (retries, timeouts, circuit breaking)

### Architecture

```
┌─────────────────────────────────────────────────────────┐
│                  Control Plane                          │
│  - Configuration management                             │
│  - Service discovery                                    │
│  - Certificate management                               │
│  - Policy enforcement                                   │
└─────────────────┬───────────────────────────────────────┘
                  │ Configuration & Policies
                  │
        ┌─────────┼─────────┬─────────┐
        │         │         │         │
        ▼         ▼         ▼         ▼
┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
│  Pod A   │ │  Pod B   │ │  Pod C   │ │  Pod D   │
│┌────────┐│ │┌────────┐│ │┌────────┐│ │┌────────┐│
││  App   ││ ││  App   ││ ││  App   ││ ││  App   ││
│└────────┘│ │└────────┘│ │└────────┘│ │└────────┘│
│┌────────┐│ │┌────────┐│ │┌────────┐│ │┌────────┐│
││ Sidecar││◄┼┼►Sidecar││◄┼┼►Sidecar││◄┼┼►Sidecar││ ← Data Plane
││ Proxy  ││ ││ Proxy  ││ ││ Proxy  ││ ││ Proxy  ││
│└────────┘│ │└────────┘│ │└────────┘│ │└────────┘│
└──────────┘ └──────────┘ └──────────┘ └──────────┘
```

**Data Plane**:
- Sidecar proxies (Envoy, linkerd-proxy)
- Handle actual traffic between services
- Enforce policies
- Collect telemetry

**Control Plane**:
- Configuration distribution
- Service discovery
- Certificate authority
- Policy management
- Telemetry aggregation

## Major Service Mesh Solutions

### 1. Istio

**Overview**:
- Most popular service mesh
- Uses Envoy proxy
- Rich feature set
- Kubernetes-native

**Architecture Components**:
```
┌─────────────────────────────────────────┐
│         Control Plane (istiod)          │
│  ┌──────────┐  ┌──────────┐  ┌────────┐│
│  │  Pilot   │  │ Citadel  │  │ Galley ││
│  │(Traffic) │  │  (CA)    │  │(Config)││
│  └──────────┘  └──────────┘  └────────┘│
└─────────────────────────────────────────┘
```

**Basic Setup**:
```yaml
# Install Istio
istioctl install --set profile=demo

# Enable sidecar injection for namespace
kubectl label namespace default istio-injection=enabled

# Deploy application (sidecar auto-injected)
kubectl apply -f app.yaml
```

**Traffic Management**:

**VirtualService** (routing rules):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: reviews
spec:
  hosts:
  - reviews
  http:
  - match:
    - headers:
        end-user:
          exact: jason
    route:
    - destination:
        host: reviews
        subset: v2
  - route:
    - destination:
        host: reviews
        subset: v1
      weight: 90
    - destination:
        host: reviews
        subset: v2
      weight: 10
```

**DestinationRule** (traffic policies):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: reviews
spec:
  host: reviews
  trafficPolicy:
    loadBalancer:
      simple: LEAST_REQUEST
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 1
        http2MaxRequests: 100
        maxRequestsPerConnection: 2
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
  subsets:
  - name: v1
    labels:
      version: v1
  - name: v2
    labels:
      version: v2
```

**Gateway** (ingress/egress):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: Gateway
metadata:
  name: app-gateway
spec:
  selector:
    istio: ingressgateway
  servers:
  - port:
      number: 80
      name: http
      protocol: HTTP
    hosts:
    - "*"
  - port:
      number: 443
      name: https
      protocol: HTTPS
    tls:
      mode: SIMPLE
      credentialName: app-tls-cert
    hosts:
    - app.example.com

---
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: app
spec:
  hosts:
  - app.example.com
  gateways:
  - app-gateway
  http:
  - match:
    - uri:
        prefix: /api
    route:
    - destination:
        host: api-service
        port:
          number: 8080
```

**Security - mTLS**:
```yaml
# Enable strict mTLS for namespace
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: production
spec:
  mtls:
    mode: STRICT

---
# Authorization policy
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: api-policy
  namespace: production
spec:
  selector:
    matchLabels:
      app: api
  action: ALLOW
  rules:
  - from:
    - source:
        principals: ["cluster.local/ns/frontend/sa/frontend-sa"]
    to:
    - operation:
        methods: ["GET", "POST"]
        paths: ["/api/*"]
```

**Circuit Breaking**:
```yaml
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: circuit-breaker
spec:
  host: backend-service
  trafficPolicy:
    connectionPool:
      tcp:
        maxConnections: 100
      http:
        http1MaxPendingRequests: 10
        http2MaxRequests: 100
        maxRequestsPerConnection: 2
    outlierDetection:
      consecutiveErrors: 5
      interval: 30s
      baseEjectionTime: 30s
      maxEjectionPercent: 50
      minHealthPercent: 40
```

**Retries & Timeouts**:
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: resilient-service
spec:
  hosts:
  - backend-service
  http:
  - route:
    - destination:
        host: backend-service
    timeout: 10s
    retries:
      attempts: 3
      perTryTimeout: 2s
      retryOn: 5xx,reset,connect-failure,refused-stream
```

### 2. Linkerd

**Overview**:
- Lightweight and simple
- Rust-based proxy (linkerd-proxy)
- Focus on simplicity and performance
- CNCF graduated project

**Installation**:
```bash
# Install CLI
curl -sL https://run.linkerd.io/install | sh

# Install control plane
linkerd install | kubectl apply -f -

# Verify installation
linkerd check

# Inject sidecar
kubectl get deploy -o yaml | linkerd inject - | kubectl apply -f -
```

**Traffic Splitting**:
```yaml
apiVersion: split.smi-spec.io/v1alpha1
kind: TrafficSplit
metadata:
  name: ab-test
spec:
  service: web-service
  backends:
  - service: web-service-v1
    weight: 900m  # 90%
  - service: web-service-v2
    weight: 100m  # 10%
```

**Service Profiles** (retries, timeouts):
```yaml
apiVersion: linkerd.io/v1alpha2
kind: ServiceProfile
metadata:
  name: backend-service.production.svc.cluster.local
  namespace: production
spec:
  routes:
  - name: GET /api/users
    condition:
      method: GET
      pathRegex: /api/users
    timeout: 5s
    retryBudget:
      retryRatio: 0.2
      minRetriesPerSecond: 10
      ttl: 10s
  - name: POST /api/users
    condition:
      method: POST
      pathRegex: /api/users
    timeout: 10s
```

### 3. Consul Connect (HashiCorp)

**Overview**:
- Part of HashiCorp Consul
- Service discovery + mesh
- Multi-platform (not just Kubernetes)
- Integrated with Vault

**Configuration**:
```hcl
# Service definition
service {
  name = "web"
  port = 8080

  connect {
    sidecar_service {
      proxy {
        upstreams = [
          {
            destination_name = "backend"
            local_bind_port  = 8081
          }
        ]
      }
    }
  }
}

# Intention (authorization)
service "web" {
  intention {
    source      = "frontend"
    action      = "allow"
    description = "Allow frontend to call web"
  }
}
```

### 4. AWS App Mesh

**Overview**:
- AWS-managed service mesh
- Uses Envoy proxy
- Integrated with AWS services (ECS, EKS, EC2)

**Virtual Service**:
```yaml
apiVersion: appmesh.k8s.aws/v1beta2
kind: VirtualService
metadata:
  name: my-service
spec:
  awsName: my-service.default.svc.cluster.local
  provider:
    virtualRouter:
      virtualRouterRef:
        name: my-service-router

---
apiVersion: appmesh.k8s.aws/v1beta2
kind: VirtualRouter
metadata:
  name: my-service-router
spec:
  routes:
    - name: route
      http:
        match:
          prefix: /
        action:
          weightedTargets:
            - virtualNodeRef:
                name: my-service-v1
              weight: 75
            - virtualNodeRef:
                name: my-service-v2
              weight: 25
```

## Service Mesh Capabilities

### 1. Traffic Management

**Canary Deployments**:
```yaml
# Istio - Gradually shift traffic
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: canary-rollout
spec:
  hosts:
  - app-service
  http:
  - match:
    - headers:
        x-canary:
          exact: "true"
    route:
    - destination:
        host: app-service
        subset: v2
  - route:
    - destination:
        host: app-service
        subset: v1
      weight: 90
    - destination:
        host: app-service
        subset: v2
      weight: 10
```

**A/B Testing**:
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: ab-test
spec:
  hosts:
  - web-app
  http:
  - match:
    - headers:
        user-agent:
          regex: ".*Mobile.*"
    route:
    - destination:
        host: web-app
        subset: mobile
  - route:
    - destination:
        host: web-app
        subset: desktop
```

**Traffic Mirroring** (shadow traffic):
```yaml
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: mirror-traffic
spec:
  hosts:
  - api-service
  http:
  - route:
    - destination:
        host: api-service
        subset: v1
      weight: 100
    mirror:
      host: api-service
      subset: v2
    mirrorPercentage:
      value: 10.0
```

### 2. Security

**Automatic mTLS**:
```
Without Service Mesh:
App A ──────HTTP──────► App B  ❌ Unencrypted

With Service Mesh:
App A ──► Proxy A ──mTLS──► Proxy B ──► App B  ✓ Encrypted
```

**JWT Authentication**:
```yaml
apiVersion: security.istio.io/v1beta1
kind: RequestAuthentication
metadata:
  name: jwt-auth
spec:
  selector:
    matchLabels:
      app: api
  jwtRules:
  - issuer: "https://auth.example.com"
    jwksUri: "https://auth.example.com/.well-known/jwks.json"

---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: require-jwt
spec:
  selector:
    matchLabels:
      app: api
  action: ALLOW
  rules:
  - from:
    - source:
        requestPrincipals: ["*"]
```

**Service-to-Service Authorization**:
```yaml
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: frontend-to-backend
spec:
  selector:
    matchLabels:
      app: backend
  action: ALLOW
  rules:
  - from:
    - source:
        principals:
        - "cluster.local/ns/default/sa/frontend"
    to:
    - operation:
        methods: ["GET", "POST"]
        paths: ["/api/*"]
```

### 3. Observability

**Metrics** (via Prometheus):
```yaml
# Istio automatically exposes metrics
# Example metrics:
# - istio_requests_total
# - istio_request_duration_milliseconds
# - istio_request_bytes
# - istio_response_bytes

# Query example
sum(rate(istio_requests_total{
  destination_service="backend.default.svc.cluster.local",
  response_code="200"
}[5m]))
```

**Distributed Tracing** (Jaeger/Zipkin):
```yaml
# Istio configuration for tracing
apiVersion: install.istio.io/v1alpha1
kind: IstioOperator
spec:
  meshConfig:
    enableTracing: true
    defaultConfig:
      tracing:
        sampling: 100
        zipkin:
          address: jaeger-collector.istio-system:9411
```

**Service Graph**:
```bash
# View service topology
istioctl dashboard kiali
```

### 4. Resilience

**Fault Injection**:
```yaml
# Test resilience by injecting faults
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: fault-injection
spec:
  hosts:
  - backend-service
  http:
  - fault:
      delay:
        percentage:
          value: 10.0
        fixedDelay: 5s
      abort:
        percentage:
          value: 5.0
        httpStatus: 503
    route:
    - destination:
        host: backend-service
```

**Rate Limiting**:
```yaml
# Envoy rate limiting
apiVersion: networking.istio.io/v1beta1
kind: EnvoyFilter
metadata:
  name: rate-limit
spec:
  configPatches:
  - applyTo: HTTP_FILTER
    match:
      context: SIDECAR_INBOUND
    patch:
      operation: INSERT_BEFORE
      value:
        name: envoy.filters.http.local_ratelimit
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.http.local_ratelimit.v3.LocalRateLimit
          stat_prefix: http_local_rate_limiter
          token_bucket:
            max_tokens: 100
            tokens_per_fill: 100
            fill_interval: 1s
```

## When to Use a Service Mesh

### Good Use Cases

**Large Microservices Architecture**:
- 50+ microservices
- Complex service dependencies
- Need uniform security/observability

**Multi-Team Organizations**:
- Different teams own different services
- Need consistent policies across teams
- Centralized traffic management

**Security Requirements**:
- Zero-trust networking
- Automatic encryption (mTLS)
- Fine-grained access control

**Advanced Traffic Management**:
- Canary deployments
- A/B testing
- Traffic mirroring
- Sophisticated routing

### When NOT to Use

**Simple Architectures**:
- Monolith or few services
- Simple request patterns
- Low traffic volume

**Resource Constraints**:
- Limited CPU/memory
- Cost-sensitive environments
- Sidecar overhead not acceptable

**Early Stage Startups**:
- Rapidly changing architecture
- Small team
- Added complexity not justified

## Service Mesh Comparison

| Feature | Istio | Linkerd | Consul Connect | AWS App Mesh |
|---------|-------|---------|----------------|--------------|
| Proxy | Envoy | linkerd-proxy | Envoy | Envoy |
| Performance | Medium | Fast | Medium | Medium |
| Complexity | High | Low | Medium | Low |
| Features | Extensive | Core features | Service discovery + mesh | AWS-integrated |
| Multi-platform | Yes | Kubernetes-focused | Yes | AWS-only |
| Maturity | High | High | High | Medium |
| Resource usage | Higher | Lower | Medium | Medium |

## System Design Interview Questions

### Q: Design a service mesh for 200 microservices

**Requirements**:
- Secure service-to-service communication
- Traffic management (canary, blue/green)
- Observability (metrics, tracing)
- High availability
- Multi-region support

**Design**:
```
┌─────────────────────────────────────────────────────────┐
│             Multi-Region Service Mesh                   │
│                                                         │
│  Region 1 (us-west-2)          Region 2 (us-east-1)    │
│  ┌──────────────────┐          ┌──────────────────┐    │
│  │  Control Plane   │          │  Control Plane   │    │
│  │   (HA: 3 nodes)  │◄────────►│   (HA: 3 nodes)  │    │
│  └────────┬─────────┘          └─────────┬────────┘    │
│           │                              │             │
│  ┌────────▼──────────┐        ┌──────────▼────────┐    │
│  │  100 Services     │        │  100 Services     │    │
│  │  with sidecars    │        │  with sidecars    │    │
│  └───────────────────┘        └───────────────────┘    │
└─────────────────────────────────────────────────────────┘
           │                              │
           └──────────────┬───────────────┘
                          ▼
            ┌──────────────────────────┐
            │   Observability Stack    │
            │  - Prometheus (metrics)  │
            │  - Jaeger (tracing)      │
            │  - Grafana (dashboards)  │
            └──────────────────────────┘
```

**Key Decisions**:

1. **Mesh Selection**: Istio
   - Rich feature set for complex requirements
   - Strong multi-cluster support
   - Mature ecosystem

2. **High Availability**:
   - 3 control plane replicas
   - Regional load balancing
   - Cross-region failover

3. **Performance**:
   - Resource limits on sidecars
   - Envoy filter optimization
   - Selective sidecar injection (not all pods need it)

4. **Security**:
   - Automatic mTLS
   - RBAC policies
   - Certificate rotation

5. **Observability**:
   - Prometheus for metrics
   - Jaeger for distributed tracing
   - Kiali for service graph

### Q: How do you migrate existing services to a service mesh?

**Strategy**:

**Phase 1: Preparation**
```
1. Inventory services
2. Map service dependencies
3. Identify critical paths
4. Set success criteria
```

**Phase 2: Install Control Plane**
```
1. Deploy mesh control plane
2. Configure observability
3. Set up certificates
4. Test with sample app
```

**Phase 3: Gradual Rollout**
```
1. Start with non-critical services
2. Enable sidecar injection per namespace
3. Monitor metrics and errors
4. Validate mTLS is working
5. Move to next tier of services
```

**Phase 4: Traffic Management**
```
1. Configure VirtualServices
2. Set up DestinationRules
3. Enable advanced features (circuit breaking, retries)
```

**Phase 5: Security Hardening**
```
1. Enable strict mTLS
2. Configure AuthorizationPolicies
3. Remove application-level security (if redundant)
```

**Rollback Plan**:
```yaml
# Quick rollback: disable sidecar injection
kubectl label namespace production istio-injection-

# Remove sidecars
kubectl rollout restart deployment -n production
```

### Q: Service mesh vs API Gateway - when to use which?

**API Gateway**:
- North-South traffic (external → internal)
- Rate limiting for external clients
- API key management
- Request/response transformation
- Protocol translation (REST → gRPC)

**Service Mesh**:
- East-West traffic (service → service)
- mTLS for internal communication
- Service-to-service authorization
- Observability between services
- Traffic shaping (canary, circuit breaking)

**Combined Architecture**:
```
                  ┌─────────────┐
Internet ────────►│ API Gateway │
                  │ (Kong/APIGW)│
                  └──────┬──────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
         ▼               ▼               ▼
    ┌────────┐      ┌────────┐      ┌────────┐
    │Service │      │Service │      │Service │
    │   A    │◄────►│   B    │◄────►│   C    │
    │(+ mesh)│      │(+ mesh)│      │(+ mesh)│
    └────────┘      └────────┘      └────────┘
         ▲
         └─── Service Mesh handles internal traffic
```

## Best Practices

### 1. Start Small
- Begin with non-critical services
- Validate performance impact
- Train team on mesh concepts

### 2. Resource Management
```yaml
# Set sidecar resource limits
apiVersion: install.istio.io/v1alpha1
kind: IstioOperator
spec:
  values:
    global:
      proxy:
        resources:
          requests:
            cpu: 100m
            memory: 128Mi
          limits:
            cpu: 500m
            memory: 512Mi
```

### 3. Observability First
- Set up metrics/tracing before migration
- Establish baselines
- Monitor throughout rollout

### 4. Security Progressive Enhancement
- Start with permissive mTLS
- Move to strict once validated
- Implement AuthZ policies gradually

### 5. Version Control Mesh Config
```
mesh-config/
├── base/
│   ├── gateway.yaml
│   └── virtualservice.yaml
├── staging/
│   └── kustomization.yaml
└── production/
    └── kustomization.yaml
```

## Resources

**Official Documentation**:
- [Istio Docs](https://istio.io/latest/docs/)
- [Linkerd Docs](https://linkerd.io/2/overview/)
- [Consul Connect](https://www.consul.io/docs/connect)

**Learning**:
- [Service Mesh Comparison](https://servicemesh.io/)
- [Istio in Action (book)](https://www.manning.com/books/istio-in-action)
- [CNCF Service Mesh Landscape](https://landscape.cncf.io/card-mode?category=service-mesh)

**Tools**:
- Kiali (Istio observability)
- Meshery (multi-mesh management)
- SMI (Service Mesh Interface)
