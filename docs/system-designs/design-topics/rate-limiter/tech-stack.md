# 🛠️ Rate Limiter Tech Stack & DevOps Solutions

## Enterprise-Grade Technology Stack for Distributed Rate Limiting

This document covers the **production-ready technologies**, **DevOps tools**, and **industry best practices** used by companies like Stripe, AWS, GitHub, and Twitter for building scalable rate limiting systems.

---

## 📦 Core Technology Stack

### 1. **Storage & Caching Layer**

#### Redis (Primary Choice ⭐)
**Why Redis?**
- **Throughput**: 100K+ operations/sec per instance
- **Atomic Operations**: INCR, Lua scripts for race-free updates
- **Data Structures**: Strings, Hashes, Sorted Sets for different algorithms
- **Persistence**: RDB snapshots + AOF for durability
- **Clustering**: Horizontal scaling with Redis Cluster (16K hash slots)

**Production Configuration**:
```yaml
# redis.conf
maxmemory 10gb
maxmemory-policy allkeys-lru  # Evict least recently used keys
appendonly yes  # Enable AOF for durability
auto-aof-rewrite-percentage 100
save 900 1  # RDB snapshot every 15 min if 1 key changed
tcp-backlog 511
timeout 0
tcp-keepalive 300
```

**Deployment Options**:
- **AWS ElastiCache for Redis**: Managed service, automatic failover
- **Redis Enterprise**: Multi-region active-active, CRDT support
- **Self-Hosted**: Kubernetes StatefulSets with Redis Sentinel
- **Upstash**: Serverless Redis (good for edge deployments)

**Cost** (AWS ElastiCache):
```
cache.r6g.large   (13.5 GB RAM):  ~$150/month
cache.r6g.xlarge  (26.2 GB RAM):  ~$300/month
cache.r6g.2xlarge (52.8 GB RAM):  ~$600/month
```

---

#### Alternative: Memcached
**When to Use**:
- Simpler use case (no need for complex data structures)
- Pure caching (no persistence needed)
- Slightly lower latency than Redis

**Trade-offs**:
- ❌ No Lua scripts (harder to implement atomic ops)
- ❌ No persistence
- ✅ Multi-threaded (better CPU utilization)

---

#### Alternative: DynamoDB (AWS)
**When to Use**:
- Multi-region active-active
- Global quotas across continents
- Serverless architecture

**Configuration**:
```python
# DynamoDB table for rate limiting
Table: RateLimitCounters
Partition Key: identifier (string)  # user_id, api_key, ip
Sort Key: window_timestamp (number)
Attributes:
  - count (number)
  - ttl (number, for auto-expiration)

# Global Secondary Index for tenant queries
GSI: tenant_id-index
```

**Trade-offs**:
- ✅ Global tables (multi-region replication)
- ✅ Serverless (no infrastructure management)
- ❌ Higher latency than Redis (5-10ms vs <1ms)
- ❌ More expensive at high throughput

---

### 2. **Coordination & Consensus**

#### Apache ZooKeeper
**Use Cases**:
- Leader election (for quota reset jobs)
- Configuration distribution
- Service discovery
- Distributed locks

**Production Setup**:
```yaml
# ZooKeeper ensemble (3 or 5 nodes for fault tolerance)
Nodes: 3 (can tolerate 1 failure)
Deployment: zk-1, zk-2, zk-3

# Data structure
/rate-limiter
  /config
    /tenants
      /customer_123
        limits: {"per_second": 100, "per_minute": 5000}
  /leader
    current_leader: "rate-limiter-node-5"
  /quotas
    /daily
      /customer_123
        allocated: 1000000
        used: 523000
```

**Alternatives**:
- **etcd**: More modern, used in Kubernetes, gRPC-based
- **Consul**: Service mesh + KV store + health checks
- **Redis**: Can be used for simple leader election (not recommended for critical coordination)

---

### 3. **API Gateway Layer**

#### Kong (Open Source ⭐)
**Why Kong?**
- Built-in rate limiting plugins
- Extensible with Lua scripts
- High performance (NGINX-based)
- Active community

**Rate Limiting Plugin**:
```yaml
# kong.yml
plugins:
  - name: rate-limiting
    config:
      minute: 100
      hour: 5000
      policy: redis  # or local, cluster
      redis_host: redis.example.com
      redis_port: 6379
      fault_tolerant: true  # fail-open if Redis down
      hide_client_headers: false
```

**Deployment**:
- **Kong Gateway (OSS)**: Free, self-hosted
- **Kong Konnect**: Enterprise with analytics, multi-region
- **Kubernetes**: Kong Ingress Controller

---

#### AWS API Gateway
**Features**:
- Built-in throttling (10K req/sec default)
- Per-stage, per-method limits
- Token bucket algorithm
- Integrated with AWS WAF

**Configuration**:
```yaml
# CloudFormation
ApiGateway:
  Type: AWS::ApiGateway::RestApi
  Properties:
    Name: MyAPI
    ThrottleSettings:
      BurstLimit: 5000
      RateLimit: 10000  # 10K req/sec
    UsagePlans:
      - Name: PremiumPlan
        Throttle:
          BurstLimit: 10000
          RateLimit: 50000
```

**Cost**:
- $3.50 per million requests
- Throttling included (no extra cost)

---

#### Envoy Proxy
**Why Envoy?**
- Service mesh integration (Istio, Consul Connect)
- Advanced traffic management
- gRPC support
- Cloud-native (CNCF project)

**Rate Limiting with Envoy**:
```yaml
# Envoy filter for rate limiting
http_filters:
  - name: envoy.filters.http.ratelimit
    typed_config:
      "@type": type.googleapis.com/envoy.extensions.filters.http.ratelimit.v3.RateLimit
      domain: rate_limit_domain
      stage: 0
      rate_limit_service:
        grpc_service:
          envoy_grpc:
            cluster_name: rate_limit_cluster
        transport_api_version: V3
```

---

### 4. **Message Queue / Event Streaming**

#### Apache Kafka
**Use Cases**:
- Stream rate limit events (violations, resets)
- Analytics pipeline
- Audit logging

**Topics**:
```
rate-limit.exceeded      - Throttled requests
rate-limit.quota-reset   - Daily/monthly quota reset events
rate-limit.config-update - Limit configuration changes
rate-limit.anomaly       - Unusual traffic patterns
```

**Production Config**:
```properties
# server.properties
num.partitions=12  # Parallelism for consumers
replication.factor=3  # Fault tolerance
min.insync.replicas=2  # Durability
compression.type=lz4  # Reduce storage
retention.ms=2592000000  # 30 days
```

**Deployment**:
- **Confluent Cloud**: Managed Kafka (easiest)
- **AWS MSK**: Managed Kafka on AWS
- **Self-Hosted**: Kubernetes with Strimzi operator

**Alternatives**:
- **AWS Kinesis**: Serverless, easier to manage, less flexible
- **RabbitMQ**: Lower throughput, better for complex routing
- **Google Cloud Pub/Sub**: Serverless, good for GCP users

---

### 5. **Time-Series Database (Analytics)**

#### TimescaleDB
**Why TimescaleDB?**
- PostgreSQL extension (familiar SQL)
- Automatic partitioning by time
- Continuous aggregates for rollups
- Compression (10:1 ratio)

**Schema**:
```sql
CREATE TABLE rate_limit_events (
    time TIMESTAMPTZ NOT NULL,
    tenant_id TEXT NOT NULL,
    api_key TEXT,
    endpoint TEXT,
    status TEXT,  -- 'allowed', 'rejected'
    limit_type TEXT,  -- 'per_second', 'per_minute'
    request_count INT
);

-- Convert to hypertable (automatic time-based partitioning)
SELECT create_hypertable('rate_limit_events', 'time');

-- Continuous aggregate for hourly rollups
CREATE MATERIALIZED VIEW rate_limit_hourly
WITH (timescaledb.continuous) AS
SELECT 
    time_bucket('1 hour', time) AS hour,
    tenant_id,
    endpoint,
    SUM(CASE WHEN status = 'allowed' THEN request_count ELSE 0 END) as allowed,
    SUM(CASE WHEN status = 'rejected' THEN request_count ELSE 0 END) as rejected
FROM rate_limit_events
GROUP BY hour, tenant_id, endpoint;

-- Enable compression (save 90% storage)
ALTER TABLE rate_limit_events SET (
    timescaledb.compress,
    timescaledb.compress_segmentby = 'tenant_id,endpoint'
);

-- Auto-compress data older than 7 days
SELECT add_compression_policy('rate_limit_events', INTERVAL '7 days');
```

**Alternatives**:
- **InfluxDB**: Purpose-built for time-series, higher performance
- **ClickHouse**: Columnar, extremely fast aggregations
- **Prometheus**: Metrics-focused, not good for high-cardinality events

---

### 6. **Monitoring & Observability**

#### Prometheus + Grafana
**Metrics to Collect**:
```yaml
# Prometheus scrape config
scrape_configs:
  - job_name: 'rate-limiter'
    static_configs:
      - targets: ['rate-limiter:9090']
    
    # Metrics exposed
    metrics:
      - rate_limiter_requests_total{status, tenant_id, endpoint}
      - rate_limiter_latency_seconds{quantile}
      - rate_limiter_redis_ops_total{operation}
      - rate_limiter_redis_errors_total
      - rate_limiter_active_keys_gauge
      - rate_limiter_cache_hit_ratio
```

**Grafana Dashboard**:
```
Panels:
  1. QPS by status (allowed vs rejected)
  2. Latency heatmap (P50, P95, P99)
  3. Top throttled tenants
  4. Redis connection pool utilization
  5. Cache hit ratio over time
  6. Error rate by error type
```

**Alerting Rules**:
```yaml
groups:
  - name: rate_limiter_alerts
    interval: 30s
    rules:
      - alert: HighRejectionRate
        expr: rate(rate_limiter_requests_total{status="rejected"}[5m]) / rate(rate_limiter_requests_total[5m]) > 0.15
        for: 5m
        annotations:
          summary: "Rejection rate > 15% for 5 minutes"
      
      - alert: HighLatency
        expr: histogram_quantile(0.99, rate(rate_limiter_latency_seconds_bucket[5m])) > 0.005
        for: 2m
        annotations:
          summary: "P99 latency > 5ms"
      
      - alert: RedisDown
        expr: up{job="redis"} == 0
        for: 1m
        annotations:
          summary: "Redis instance down!"
```

**Alternatives**:
- **Datadog**: All-in-one (metrics + logs + traces), expensive
- **New Relic**: Similar to Datadog
- **CloudWatch**: AWS-native, good for AWS-only deployments

---

#### Distributed Tracing (Jaeger / Zipkin)
**Trace Spans**:
```
Request Trace:
  └─ rate_limit_check (5.2ms)
      ├─ extract_identifier (0.1ms)
      ├─ load_config (0.3ms)
      ├─ redis_query (0.8ms)
      │   ├─ redis_connect (0.1ms)
      │   └─ lua_script_exec (0.5ms)
      ├─ algorithm_execute (0.2ms)
      └─ publish_event (3.5ms)
          └─ kafka_produce (3.2ms)
```

---

### 7. **Programming Languages & Frameworks**

#### Go (Recommended for High Performance)
**Why Go?**
- High concurrency (goroutines)
- Low latency (compiled)
- Simple deployment (single binary)
- Excellent Redis libraries (`go-redis`)

**Example Libraries**:
```go
import (
    "github.com/go-redis/redis/v8"
    "github.com/samuel/go-zookeeper/zk"
    "github.com/prometheus/client_golang/prometheus"
)
```

**Popular Frameworks**:
- **Gin**: Fast HTTP router
- **Fiber**: Express-like, very fast
- **gRPC-Go**: For microservices communication

---

#### Java/Kotlin (Enterprise Standard)
**Why Java?**
- Spring ecosystem
- Battle-tested in enterprises
- Rich library support

**Libraries**:
```xml
<!-- Spring Boot Rate Limiter -->
<dependency>
    <groupId>com.github.vladimir-bukhtoyarov</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.0.0</version>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- ZooKeeper -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-framework</artifactId>
    <version>5.5.0</version>
</dependency>
```

---

#### Rust (Emerging for Ultra-Low Latency)
**Why Rust?**
- Memory safety without GC
- Zero-cost abstractions
- Fastest performance

**Libraries**:
- `redis-rs`: Redis client
- `governor`: Rate limiting library (Token bucket, GCRA)
- `tokio`: Async runtime

**Use Case**: Edge rate limiting (Cloudflare Workers, Fastly Compute@Edge)

---

## 🚀 DevOps & Deployment

### 1. **Container Orchestration (Kubernetes)**

**Deployment Manifests**:
```yaml
# rate-limiter-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: rate-limiter
spec:
  replicas: 5
  selector:
    matchLabels:
      app: rate-limiter
  template:
    metadata:
      labels:
        app: rate-limiter
    spec:
      containers:
      - name: rate-limiter
        image: company/rate-limiter:v2.3.1
        ports:
        - containerPort: 8080
        env:
        - name: REDIS_HOST
          value: "redis-cluster.default.svc.cluster.local"
        - name: ZOOKEEPER_SERVERS
          value: "zk-0.zk-headless:2181,zk-1.zk-headless:2181,zk-2.zk-headless:2181"
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "2000m"
            memory: "2Gi"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 10
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
  name: rate-limiter
spec:
  selector:
    app: rate-limiter
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
```

**Horizontal Pod Autoscaler**:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: rate-limiter-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: rate-limiter
  minReplicas: 5
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: rate_limiter_qps
      target:
        type: AverageValue
        averageValue: "10000"  # Scale if QPS > 10K per pod
```

---

### 2. **Infrastructure as Code (Terraform)**

```hcl
# terraform/redis.tf
resource "aws_elasticache_replication_group" "rate_limiter" {
  replication_group_id       = "rate-limiter-redis"
  replication_group_description = "Redis cluster for rate limiting"
  
  engine                     = "redis"
  engine_version             = "7.0"
  node_type                  = "cache.r6g.xlarge"
  number_cache_clusters      = 3  # 1 master + 2 replicas
  
  parameter_group_name       = "default.redis7"
  port                       = 6379
  
  automatic_failover_enabled = true
  multi_az_enabled          = true
  
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  
  snapshot_retention_limit   = 7
  snapshot_window            = "03:00-05:00"
  
  tags = {
    Name        = "rate-limiter-redis"
    Environment = "production"
  }
}

# terraform/eks.tf
module "eks" {
  source          = "terraform-aws-modules/eks/aws"
  version         = "19.0.0"
  
  cluster_name    = "rate-limiter-cluster"
  cluster_version = "1.28"
  
  vpc_id          = module.vpc.vpc_id
  subnet_ids      = module.vpc.private_subnets
  
  eks_managed_node_groups = {
    rate_limiter = {
      min_size     = 3
      max_size     = 20
      desired_size = 5
      
      instance_types = ["c6i.2xlarge"]  # Compute optimized
      capacity_type  = "ON_DEMAND"
    }
  }
}
```

---

### 3. **CI/CD Pipeline (GitHub Actions)**

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy Rate Limiter

on:
  push:
    branches: [main]
  pull_request:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Set up Go
        uses: actions/setup-go@v4
        with:
          go-version: '1.21'
      
      - name: Run unit tests
        run: go test -v -race -coverprofile=coverage.txt ./...
      
      - name: Run integration tests
        run: |
          docker-compose up -d redis zookeeper
          go test -v -tags=integration ./...
      
      - name: Upload coverage
        uses: codecov/codecov-action@v3

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: docker build -t rate-limiter:${{ github.sha }} .
      
      - name: Push to ECR
        run: |
          aws ecr get-login-password --region us-east-1 | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker tag rate-limiter:${{ github.sha }} $ECR_REGISTRY/rate-limiter:${{ github.sha }}
          docker push $ECR_REGISTRY/rate-limiter:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/rate-limiter \
            rate-limiter=$ECR_REGISTRY/rate-limiter:${{ github.sha }} \
            --record
          kubectl rollout status deployment/rate-limiter
```

---

## 🏢 Industry Implementations Comparison

| **Company** | **Storage** | **Coordination** | **Gateway** | **Language** | **Algorithm** |
|-------------|-------------|------------------|-------------|--------------|---------------|
| **Stripe** | Redis Cluster | etcd | Custom (Ruby) | Ruby, Go | Token Bucket |
| **AWS API Gateway** | DynamoDB | Internal | AWS ALB | Java | Token Bucket |
| **GitHub** | MySQL (custom) | Consul | Nginx + Lua | Ruby, Go | Sliding Window |
| **Twitter** | Manhattan (custom) | ZooKeeper | Custom | Scala, Java | Sliding Window |
| **Cloudflare** | Quicksilver (distributed KV) | Internal | Workers (V8) | Rust, Go | Leaky Bucket |
| **Shopify** | Redis | Consul | Custom | Ruby, Go | Leaky Bucket |

---

## 💰 Cost Breakdown (AWS, 100K QPS)

| **Component** | **Service** | **Specs** | **Monthly Cost** |
|---------------|-------------|-----------|------------------|
| Redis Cluster | ElastiCache r6g.xlarge | 26 GB RAM × 3 nodes | $900 |
| Kubernetes | EKS + EC2 c6i.2xlarge | 5 nodes | $1,500 |
| Load Balancer | AWS ALB | 100K req/sec | $100 |
| TimescaleDB | RDS db.r6g.xlarge | 32 GB RAM | $500 |
| Kafka | AWS MSK | 3 brokers | $600 |
| Monitoring | Grafana Cloud | 100M metrics/month | $300 |
| **Total** | | | **$3,900/month** |

**Cost Optimization Tips**:
- Use Spot instances for non-critical components (-70%)
- Reserved instances for stable workload (-40%)
- Auto-scaling to match traffic patterns
- Data compression in TimescaleDB (-90% storage)

---

## 🎯 Best Practices Summary

### Production Checklist
- ✅ Use Redis (not database) for counters
- ✅ Implement Lua scripts for atomic operations
- ✅ Deploy multi-AZ for high availability
- ✅ Monitor P99 latency (<5ms target)
- ✅ Set up alerts for rejection rate (>15% is bad)
- ✅ Use hierarchical quotas for multi-region
- ✅ Implement graceful degradation (fail-open strategy)
- ✅ Log all throttling events to Kafka
- ✅ Compress time-series data (10:1 ratio)
- ✅ Auto-scale based on QPS metrics

---

## 📚 Additional Resources

**Open Source Projects**:
- [Kong Rate Limiting Plugin](https://github.com/Kong/kong-plugin-rate-limiting)
- [Bucket4j (Java)](https://github.com/vladimir-bukhtoyarov/bucket4j)
- [Governor (Rust)](https://github.com/boinkor-net/governor)
- [go-redis-rate](https://github.com/go-redis/redis_rate)

**Recommended Reading**:
- "Redis Rate Limiting Patterns" - Redis Labs
- "Distributed Rate Limiting at Scale" - Stripe Engineering
- "How Cloudflare Handles 32M HTTP Requests/sec" - Cloudflare Blog
- "Rate Limiting with Redis and Lua" - Kong Engineering

**Managed Solutions** (If you don't want to build):
- **AWS API Gateway**: Built-in throttling
- **Kong Konnect**: Enterprise rate limiting
- **Cloudflare Rate Limiting**: Edge-based
- **Fastly Rate Limiting**: CDN-integrated

---

**Final Recommendation**: For most use cases, **Go + Redis + Kubernetes + Prometheus** is the sweet spot between performance, cost, and operational simplicity.
