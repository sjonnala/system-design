# Top K Leaderboard: Tech Stack & DevOps Solutions

## Industry Standard Technology Stack

This document outlines the complete technology stack used by leading gaming and social platforms for building high-performance leaderboard systems.

---

## 📋 Table of Contents

1. [Core Technology Stack](#core-technology-stack)
2. [Infrastructure & Cloud Providers](#infrastructure--cloud-providers)
3. [DevOps & CI/CD](#devops--cicd)
4. [Monitoring & Observability](#monitoring--observability)
5. [Security & Compliance](#security--compliance)
6. [Industry Case Studies](#industry-case-studies)
7. [Cost Analysis](#cost-analysis)

---

## Core Technology Stack

### 1. Primary Data Store: Redis

**Redis Cluster** (In-Memory, Sorted Sets)

```yaml
Version: Redis 7.x
Deployment: Redis Cluster / Redis Enterprise
Data Structure: Sorted Sets (ZSET)
Persistence: AOF + RDB snapshots
Replication: Master-Replica (3 masters, 3 replicas)
```

**Why Redis?**
- **Sorted Sets (ZSET):** Built specifically for leaderboards
- **O(log N) operations:** Lightning-fast updates and queries
- **In-memory:** Sub-millisecond latency (<1ms P99)
- **Atomic operations:** ZINCRBY is thread-safe
- **Battle-tested:** Used by Riot Games, Blizzard, Discord

**Production Configuration:**

```conf
# redis.conf (Production Settings)

# Memory & Eviction
maxmemory 64gb
maxmemory-policy noeviction  # Critical: Don't evict leaderboard data!

# Persistence
save 900 1        # RDB: Save if 1 key changed in 15 min
save 300 10       # RDB: Save if 10 keys changed in 5 min
save 60 10000     # RDB: Save if 10K keys changed in 1 min

appendonly yes    # AOF: Enable append-only file
appendfsync everysec  # AOF: Fsync every second (balance durability/performance)

# Replication
repl-diskless-sync yes
repl-backlog-size 256mb

# Performance
tcp-backlog 511
tcp-keepalive 300
timeout 300

# Limits
maxclients 65000
```

**Managed Redis Options:**
- **AWS ElastiCache for Redis:** Fully managed, auto-failover, 99.99% SLA
- **Google Cloud Memorystore:** Managed Redis, sub-millisecond latency
- **Azure Cache for Redis:** Enterprise-grade, zone redundancy
- **Redis Enterprise Cloud:** Multi-cloud, active-active geo-replication

**Cost (AWS ElastiCache):**
- r7g.large (16 GB): ~$200/month
- r7g.xlarge (32 GB): ~$400/month
- r7g.2xlarge (64 GB): ~$800/month
- r7g.4xlarge (128 GB): ~$1,600/month

---

### 2. Application Layer

#### Option 1: **Go (Golang)**

**Best for:** High-concurrency, low-latency services

```go
// Example: Go service with Redis
package main

import (
    "github.com/go-redis/redis/v8"
    "github.com/gin-gonic/gin"
)

type LeaderboardService struct {
    redisClient *redis.ClusterClient
}

func (s *LeaderboardService) UpdateScore(ctx context.Context, userID string, delta int64) (*RankResponse, error) {
    // Atomic increment
    newScore, err := s.redisClient.ZIncrBy(ctx, "global:alltime:world", float64(delta), userID).Result()
    if err != nil {
        return nil, err
    }

    // Get new rank
    rank, err := s.redisClient.ZRevRank(ctx, "global:alltime:world", userID).Result()
    if err != nil {
        return nil, err
    }

    return &RankResponse{
        UserID:   userID,
        NewScore: int64(newScore),
        Rank:     rank + 1, // Convert to 1-indexed
    }, nil
}
```

**Go Libraries:**
- `go-redis/redis`: Official Redis client for Go
- `gin-gonic/gin`: High-performance web framework
- `uber-go/zap`: Structured logging
- `prometheus/client_golang`: Metrics collection

**Why Go?**
- Native concurrency (goroutines)
- Low memory footprint (~20 MB per service)
- Fast compilation
- Used by: Discord, Uber, Riot Games

---

#### Option 2: **Node.js**

**Best for:** Rapid development, JavaScript ecosystem

```javascript
// Example: Node.js service with Redis
const Redis = require('ioredis');
const express = require('express');

const redis = new Redis.Cluster([
  { host: 'redis-1.example.com', port: 6379 },
  { host: 'redis-2.example.com', port: 6379 },
]);

class LeaderboardService {
  async updateScore(userId, delta) {
    // Atomic increment
    const newScore = await redis.zincrby('global:alltime:world', delta, userId);

    // Get new rank (0-indexed)
    const rank = await redis.zrevrank('global:alltime:world', userId);

    return {
      userId,
      newScore: parseInt(newScore),
      rank: rank + 1
    };
  }

  async getTopK(k = 100) {
    const results = await redis.zrevrange(
      'global:alltime:world',
      0,
      k - 1,
      'WITHSCORES'
    );

    const leaderboard = [];
    for (let i = 0; i < results.length; i += 2) {
      leaderboard.push({
        rank: i / 2 + 1,
        userId: results[i],
        score: parseInt(results[i + 1])
      });
    }

    return leaderboard;
  }
}

module.exports = LeaderboardService;
```

**Node.js Libraries:**
- `ioredis`: Feature-rich Redis client (cluster support)
- `express` or `fastify`: Web frameworks
- `pino`: High-performance logging
- `prom-client`: Prometheus metrics

**Why Node.js?**
- Event-driven, non-blocking I/O
- Rich ecosystem (npm)
- Easy integration with front-end
- Used by: Netflix, LinkedIn, PayPal

---

#### Option 3: **Python (FastAPI)**

**Best for:** Rapid prototyping, data science integration

```python
# Example: Python FastAPI service
from fastapi import FastAPI, HTTPException
from redis.asyncio import Redis
from pydantic import BaseModel

app = FastAPI()
redis_client = None

class ScoreUpdate(BaseModel):
    user_id: str
    delta: int

@app.on_event("startup")
async def startup():
    global redis_client
    redis_client = await Redis.from_url("redis://localhost:6379")

@app.post("/api/v1/scores")
async def update_score(update: ScoreUpdate):
    # Atomic increment
    new_score = await redis_client.zincrby(
        "global:alltime:world",
        update.delta,
        update.user_id
    )

    # Get new rank
    rank = await redis_client.zrevrank("global:alltime:world", update.user_id)

    return {
        "user_id": update.user_id,
        "new_score": int(new_score),
        "rank": rank + 1 if rank is not None else None
    }

@app.get("/api/v1/leaderboard/top")
async def get_top_k(k: int = 100):
    results = await redis_client.zrevrange(
        "global:alltime:world",
        0,
        k - 1,
        withscores=True
    )

    leaderboard = [
        {"rank": i + 1, "user_id": user_id, "score": int(score)}
        for i, (user_id, score) in enumerate(results)
    ]

    return {"top_users": leaderboard}
```

**Python Libraries:**
- `fastapi`: Modern async web framework
- `redis-py`: Official Redis client
- `uvicorn`: ASGI server
- `prometheus-client`: Metrics

**Why Python?**
- Easy to read and maintain
- Rich data science libraries (for analytics)
- Async support with `asyncio`
- Used by: Instagram, Dropbox, Spotify

---

### 3. Persistent Storage: PostgreSQL

**PostgreSQL 15+** (Historical data, audit trail)

```sql
-- Production Schema

CREATE TABLE score_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    score BIGINT NOT NULL,
    delta BIGINT,
    leaderboard_type VARCHAR(50),
    session_id UUID,
    ip_address INET,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) PARTITION BY RANGE (created_at);

-- Monthly partitions for performance
CREATE TABLE score_history_2024_11 PARTITION OF score_history
    FOR VALUES FROM ('2024-11-01') TO ('2024-12-01');

CREATE TABLE score_history_2024_12 PARTITION OF score_history
    FOR VALUES FROM ('2024-12-01') TO ('2025-01-01');

-- Indexes
CREATE INDEX idx_score_user_time ON score_history(user_id, created_at DESC);
CREATE INDEX idx_score_type ON score_history(leaderboard_type);
CREATE INDEX idx_score_time ON score_history(created_at);

-- Daily leaderboard snapshots
CREATE TABLE leaderboard_snapshots (
    id BIGSERIAL PRIMARY KEY,
    leaderboard_key VARCHAR(100),
    user_id BIGINT NOT NULL,
    rank INTEGER NOT NULL,
    score BIGINT NOT NULL,
    snapshot_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshot_key_time ON leaderboard_snapshots(leaderboard_key, snapshot_time);
CREATE INDEX idx_snapshot_user ON leaderboard_snapshots(user_id, snapshot_time);
```

**Managed PostgreSQL Options:**
- **AWS RDS PostgreSQL:** Multi-AZ, automated backups, read replicas
- **Google Cloud SQL:** High availability, point-in-time recovery
- **Azure Database for PostgreSQL:** Zone-redundant, auto-scaling
- **Supabase:** Open-source Firebase alternative with PostgreSQL

**Cost (AWS RDS):**
- db.r6g.large (16 GB): ~$300/month
- db.r6g.xlarge (32 GB): ~$600/month
- db.r6g.2xlarge (64 GB): ~$1,200/month

---

### 4. Event Streaming: Apache Kafka

**Apache Kafka** (Event sourcing, analytics pipeline)

```yaml
# Kafka Configuration
Topic: score.updated
Partitions: 10 (for parallelism)
Replication Factor: 3 (high availability)
Retention: 7 days

# Producer Config
acks: all  # Wait for all replicas
compression.type: snappy
max.in.flight.requests.per.connection: 5
enable.idempotence: true  # Exactly-once semantics

# Consumer Groups
- analytics-processor: Real-time stats
- postgres-writer: Persist to DB
- anti-cheat-detector: Flag suspicious activity
- notification-service: Rank change alerts
```

**Event Schema (Avro):**

```json
{
  "type": "record",
  "name": "ScoreUpdatedEvent",
  "namespace": "com.gaming.leaderboard.events",
  "fields": [
    {"name": "event_id", "type": "string"},
    {"name": "user_id", "type": "string"},
    {"name": "old_score", "type": "long"},
    {"name": "new_score", "type": "long"},
    {"name": "delta", "type": "long"},
    {"name": "leaderboard_type", "type": "string"},
    {"name": "timestamp", "type": "long", "logicalType": "timestamp-millis"},
    {"name": "metadata", "type": {
      "type": "record",
      "name": "EventMetadata",
      "fields": [
        {"name": "session_id", "type": "string"},
        {"name": "ip_address", "type": "string"},
        {"name": "device_type", "type": "string"}
      ]
    }}
  ]
}
```

**Managed Kafka Options:**
- **AWS MSK (Managed Streaming for Kafka):** Fully managed, auto-scaling
- **Confluent Cloud:** Enterprise Kafka, ksqlDB included
- **Azure Event Hubs:** Kafka-compatible, serverless
- **Google Pub/Sub:** Alternative to Kafka, fully managed

**Cost (AWS MSK):**
- kafka.m5.large: ~$400/month (3 brokers)
- kafka.m5.xlarge: ~$800/month (3 brokers)

---

### 5. Analytics Engine

**Option 1: Apache Spark**

```python
# Spark Streaming Job: Real-time anomaly detection
from pyspark.sql import SparkSession
from pyspark.sql.functions import window, avg, stddev

spark = SparkSession.builder.appName("LeaderboardAnalytics").getOrCreate()

# Read from Kafka
df = spark \
    .readStream \
    .format("kafka") \
    .option("kafka.bootstrap.servers", "kafka:9092") \
    .option("subscribe", "score.updated") \
    .load()

# Detect score anomalies
anomalies = df \
    .withWatermark("timestamp", "5 minutes") \
    .groupBy(window("timestamp", "5 minutes"), "user_id") \
    .agg(
        avg("delta").alias("avg_delta"),
        stddev("delta").alias("stddev_delta")
    ) \
    .filter("stddev_delta > 1000")  # Flag high variance

anomalies.writeStream \
    .format("console") \
    .outputMode("update") \
    .start()
```

**Option 2: Apache Flink**

```java
// Flink job: Real-time leaderboard aggregation
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

DataStream<ScoreEvent> events = env
    .addSource(new FlinkKafkaConsumer<>("score.updated", new ScoreEventSchema(), properties));

DataStream<LeaderboardStats> stats = events
    .keyBy(event -> event.getLeaderboardType())
    .timeWindow(Time.minutes(5))
    .aggregate(new LeaderboardAggregator());

stats.addSink(new RedisSink<>());
```

---

## Infrastructure & Cloud Providers

### AWS (Amazon Web Services)

**Complete AWS Stack:**

```yaml
Compute:
  - ECS Fargate: Container orchestration (serverless)
  - EC2 Auto Scaling Groups: Traditional VMs
  - Lambda: Serverless functions (analytics triggers)

Data Stores:
  - ElastiCache for Redis: Managed Redis cluster
  - RDS PostgreSQL: Relational database
  - DynamoDB: Alternative NoSQL option
  - S3: Object storage (backups, snapshots)

Messaging:
  - MSK (Managed Kafka): Event streaming
  - SQS: Simple queue service (dead letter queues)
  - SNS: Pub/Sub notifications

Networking:
  - ALB (Application Load Balancer): Layer 7 load balancing
  - CloudFront: Global CDN
  - Route 53: DNS management
  - VPC: Network isolation

Monitoring:
  - CloudWatch: Metrics, logs, alarms
  - X-Ray: Distributed tracing

Security:
  - WAF: Web application firewall
  - Shield: DDoS protection
  - Secrets Manager: Credential management
  - IAM: Access control
```

**Cost Estimate (AWS, 100M users):**
- ElastiCache (3 × r7g.2xlarge): $2,400/month
- RDS (db.r6g.xlarge): $600/month
- MSK (3 × m5.large): $400/month
- EC2 Auto Scaling (10 × c6g.xlarge): $1,200/month
- ALB + Data Transfer: $300/month
- CloudWatch + X-Ray: $200/month
- **Total: ~$5,100/month**

---

### Google Cloud Platform (GCP)

**Complete GCP Stack:**

```yaml
Compute:
  - GKE (Kubernetes Engine): Container orchestration
  - Cloud Run: Serverless containers
  - Compute Engine: VMs with auto-scaling

Data Stores:
  - Memorystore for Redis: Managed Redis
  - Cloud SQL for PostgreSQL: Managed database
  - Bigtable: Alternative for massive scale
  - Cloud Storage: Object storage

Messaging:
  - Pub/Sub: Real-time messaging
  - Dataflow: Apache Beam for stream processing

Networking:
  - Cloud Load Balancing: Global load balancer
  - Cloud CDN: Content delivery
  - Cloud DNS: DNS management

Monitoring:
  - Cloud Monitoring: Metrics and alerting
  - Cloud Trace: Distributed tracing
  - Cloud Logging: Centralized logs
```

---

### Microsoft Azure

**Complete Azure Stack:**

```yaml
Compute:
  - AKS (Azure Kubernetes Service): Kubernetes
  - Container Apps: Serverless containers
  - Virtual Machine Scale Sets: Auto-scaling VMs

Data Stores:
  - Azure Cache for Redis: Managed Redis
  - Azure Database for PostgreSQL: Managed DB
  - Cosmos DB: Globally distributed NoSQL

Messaging:
  - Event Hubs: Kafka-compatible streaming
  - Service Bus: Enterprise messaging

Networking:
  - Application Gateway: Layer 7 load balancer
  - Front Door: Global CDN
  - Azure DNS: DNS management

Monitoring:
  - Azure Monitor: Metrics and logs
  - Application Insights: APM
```

---

## DevOps & CI/CD

### Container Orchestration: Kubernetes

**Kubernetes Deployment:**

```yaml
# leaderboard-update-service.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: leaderboard-update-service
  labels:
    app: leaderboard
    component: update-service
spec:
  replicas: 10
  selector:
    matchLabels:
      app: leaderboard
      component: update-service
  template:
    metadata:
      labels:
        app: leaderboard
        component: update-service
    spec:
      containers:
      - name: update-service
        image: myregistry/leaderboard-update:v1.2.3
        ports:
        - containerPort: 8080
        env:
        - name: REDIS_CLUSTER_HOSTS
          value: "redis-1:6379,redis-2:6379,redis-3:6379"
        - name: KAFKA_BROKERS
          value: "kafka:9092"
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
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
  name: leaderboard-update-service
spec:
  selector:
    app: leaderboard
    component: update-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: ClusterIP
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: leaderboard-update-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: leaderboard-update-service
  minReplicas: 10
  maxReplicas: 50
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

### CI/CD Pipeline

**GitHub Actions (Example):**

```yaml
# .github/workflows/deploy.yml
name: Build and Deploy Leaderboard Service

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run unit tests
        run: |
          npm install
          npm test
      - name: Run integration tests
        run: npm run test:integration

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker image
        run: docker build -t leaderboard-update:${{ github.sha }} .
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker tag leaderboard-update:${{ github.sha }} $ECR_REGISTRY/leaderboard-update:${{ github.sha }}
          docker push $ECR_REGISTRY/leaderboard-update:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to EKS
        run: |
          kubectl set image deployment/leaderboard-update-service \
            update-service=$ECR_REGISTRY/leaderboard-update:${{ github.sha }}
          kubectl rollout status deployment/leaderboard-update-service
```

---

## Monitoring & Observability

### Metrics: Prometheus + Grafana

**Prometheus Configuration:**

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'leaderboard-services'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: leaderboard
      - source_labels: [__meta_kubernetes_pod_name]
        action: replace
        target_label: pod

  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']
```

**Key Metrics to Track:**

```prometheus
# Request Rate
rate(http_requests_total[5m])

# Latency Percentiles
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
histogram_quantile(0.99, rate(http_request_duration_seconds_bucket[5m]))

# Redis Operations
rate(redis_commands_processed_total[5m])
redis_memory_used_bytes / redis_memory_max_bytes * 100

# Error Rate
rate(http_requests_total{status=~"5.."}[5m]) / rate(http_requests_total[5m]) * 100

# Kafka Lag
kafka_consumer_group_lag
```

**Grafana Dashboard (JSON):** Pre-built dashboards available at [Grafana.com](https://grafana.com/grafana/dashboards/)

---

### Logging: ELK Stack / Loki

**Elasticsearch + Logstash + Kibana (ELK):**

```yaml
# Logstash pipeline
input {
  kafka {
    bootstrap_servers => "kafka:9092"
    topics => ["application-logs"]
    codec => json
  }
}

filter {
  # Parse JSON logs
  json {
    source => "message"
  }

  # Add geo-location
  geoip {
    source => "ip_address"
  }

  # Extract user info
  mutate {
    add_field => { "user_tier" => "%{[user][tier]}" }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "leaderboard-logs-%{+YYYY.MM.dd}"
  }
}
```

**Alternative: Grafana Loki** (lightweight, cost-effective)

---

### Distributed Tracing: Jaeger / Zipkin

**OpenTelemetry Integration:**

```javascript
// Node.js example with OpenTelemetry
const { NodeTracerProvider } = require('@opentelemetry/sdk-trace-node');
const { JaegerExporter } = require('@opentelemetry/exporter-jaeger');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { RedisInstrumentation } = require('@opentelemetry/instrumentation-redis');

const provider = new NodeTracerProvider();
const exporter = new JaegerExporter({
  endpoint: 'http://jaeger:14268/api/traces',
});

provider.addSpanProcessor(new BatchSpanProcessor(exporter));
provider.register();

registerInstrumentations({
  instrumentations: [
    new HttpInstrumentation(),
    new RedisInstrumentation(),
  ],
});
```

---

## Security & Compliance

### Authentication & Authorization

**JWT Token Validation:**

```javascript
// Express middleware
const jwt = require('jsonwebtoken');

function authenticateToken(req, res, next) {
  const token = req.headers['authorization']?.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) {
      return res.status(403).json({ error: 'Invalid token' });
    }
    req.user = user;
    next();
  });
}

// Rate limiting per user
const rateLimit = require('express-rate-limit');

const updateLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 10, // 10 requests per minute per user
  keyGenerator: (req) => req.user.id,
  message: 'Too many score updates, please try again later'
});

app.post('/api/v1/scores', authenticateToken, updateLimiter, updateScore);
```

---

### Data Encryption

**Encryption at Rest:**
- Redis: TLS for transit, disk encryption for RDB/AOF
- PostgreSQL: Transparent Data Encryption (TDE)
- Kafka: Broker-side encryption

**Encryption in Transit:**
```yaml
# Redis TLS Config
tls-port 6379
tls-cert-file /path/to/redis.crt
tls-key-file /path/to/redis.key
tls-ca-cert-file /path/to/ca.crt
tls-auth-clients yes
```

---

## Industry Case Studies

### 1. Riot Games (League of Legends)

**Scale:**
- 150M+ registered players
- 10M+ concurrent players (peak)
- Global ranked leaderboards (by region)

**Tech Stack:**
- **Primary Store:** Redis Cluster (custom-built)
- **Services:** Java (Spring Boot), Python (analytics)
- **Infrastructure:** AWS (multi-region)
- **Observability:** Custom metrics pipeline

**Key Optimizations:**
- Regional sharding (NA, EU, KR, CN)
- Separate leaderboards per queue type (Ranked Solo, Flex, ARAM)
- Lazy computation for lower ranks (only top 1M cached)

---

### 2. King (Candy Crush Saga)

**Scale:**
- 250M+ monthly active users
- Level-based leaderboards (20K+ levels)
- Friends leaderboards

**Tech Stack:**
- **Primary Store:** Redis Sorted Sets
- **Services:** Node.js, Go
- **Infrastructure:** AWS + Google Cloud (multi-cloud)
- **Analytics:** BigQuery, Spark

**Key Optimizations:**
- Per-level leaderboards (not global)
- Friends leaderboards computed on-demand (not materialized)
- Weekly contests with auto-reset

---

### 3. Discord (Server Leaderboards)

**Scale:**
- 150M+ monthly active users
- Per-server leaderboards (activity, XP)

**Tech Stack:**
- **Primary Store:** Redis (custom Sorted Set extensions)
- **Services:** Rust, Elixir
- **Infrastructure:** Google Cloud
- **Database:** ScyllaDB (Cassandra-compatible)

**Key Optimizations:**
- Lazy evaluation: Only compute leaderboard when first queried
- TTL-based cache invalidation
- Partitioning by server ID

---

## Cost Analysis

### Small Scale (1M users, 100 QPS)

**AWS:**
- ElastiCache r7g.large (16 GB): $200/month
- RDS db.t4g.medium: $100/month
- ECS Fargate (2 vCPUs): $150/month
- ALB + Data Transfer: $50/month
- **Total: ~$500/month**

---

### Medium Scale (10M users, 1K QPS)

**AWS:**
- ElastiCache r7g.xlarge (32 GB): $400/month
- RDS db.r6g.large (16 GB): $300/month
- ECS Fargate (10 vCPUs): $700/month
- ALB + Data Transfer: $150/month
- MSK kafka.m5.large (3 brokers): $400/month
- **Total: ~$2,000/month**

---

### Large Scale (100M users, 10K QPS)

**AWS:**
- ElastiCache: 3 × r7g.2xlarge (192 GB): $2,400/month
- RDS: db.r6g.xlarge + 3 replicas: $2,400/month
- ECS Fargate (50 vCPUs): $3,500/month
- ALB + CloudFront: $800/month
- MSK: 3 × kafka.m5.xlarge: $800/month
- S3 + CloudWatch: $300/month
- **Total: ~$10,200/month**

---

## Recommended Starting Stack (MVP)

For a **new leaderboard system (MVP)**:

```yaml
Application: Node.js (Express) or Go (Gin)
Primary Store: Redis (managed - AWS ElastiCache)
Database: PostgreSQL (managed - AWS RDS)
Hosting: AWS ECS Fargate or Google Cloud Run
CI/CD: GitHub Actions
Monitoring: Prometheus + Grafana Cloud (free tier)
Logging: CloudWatch Logs or Loki
Estimated Cost: $500-1,000/month (1M users)
```

**Timeline to Production:**
- Week 1-2: Core API development (update, query endpoints)
- Week 3: Redis integration, testing
- Week 4: CI/CD setup, infrastructure as code (Terraform)
- Week 5: Monitoring, alerting, load testing
- Week 6: Production launch 🚀

---

## Further Resources

**Documentation:**
- [Redis Sorted Sets](https://redis.io/docs/data-types/sorted-sets/)
- [AWS ElastiCache Best Practices](https://aws.amazon.com/elasticache/best-practices/)
- [Kubernetes Horizontal Pod Autoscaling](https://kubernetes.io/docs/tasks/run-application/horizontal-pod-autoscale/)

**Open Source Projects:**
- [redis/redis](https://github.com/redis/redis): Redis source code
- [valkey-io/valkey](https://github.com/valkey-io/valkey): Redis fork (Linux Foundation)
- [prometheus/prometheus](https://github.com/prometheus/prometheus): Monitoring system

**Books:**
- "Designing Data-Intensive Applications" - Martin Kleppmann
- "Redis in Action" - Josiah Carlson
- "Site Reliability Engineering" - Google

---

**This tech stack powers leaderboards at:**
- Riot Games (League of Legends, Valorant)
- King (Candy Crush)
- Blizzard (Overwatch, Hearthstone)
- Discord (Server activity tracking)
- Steam (Global achievements)
