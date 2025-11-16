# 🛠️ TicketMaster Technology Stack & DevOps Solutions

## Complete Industry-Standard Architecture

This document covers the **real-world technologies** used by companies like Ticketmaster, Eventbrite, StubHub, and SeatGeek for building high-scale ticketing platforms.

---

## 📚 Table of Contents

1. [Application Layer](#application-layer)
2. [Database & Storage](#database--storage)
3. [Caching & In-Memory](#caching--in-memory)
4. [Message Queue & Event Streaming](#message-queue--event-streaming)
5. [Search & Analytics](#search--analytics)
6. [Payment Processing](#payment-processing)
7. [Infrastructure & Cloud](#infrastructure--cloud)
8. [DevOps & CI/CD](#devops--cicd)
9. [Monitoring & Observability](#monitoring--observability)
10. [Security & Compliance](#security--compliance)
11. [CDN & Edge Computing](#cdn--edge-computing)
12. [Development Tools](#development-tools)

---

## 1. Application Layer

### Backend Frameworks

| Technology | Use Case | Why Ticketmaster Uses It | Alternatives |
|------------|----------|--------------------------|--------------|
| **Java / Spring Boot** | Core booking service, transactional APIs | ACID guarantees, mature ecosystem, excellent transaction management | Node.js, Go, Python/Django |
| **Node.js / Express** | Real-time services (WebSocket, seat availability updates) | Event-driven, non-blocking I/O for real-time updates | Go (Gin), Python (FastAPI) |
| **Python / Django** | Admin panel, content management, internal tools | Rapid development, Django Admin out-of-the-box | Ruby on Rails, PHP/Laravel |
| **Go (Golang)** | High-performance services (queue management, virtual waiting room) | Low latency, efficient concurrency (goroutines) | Rust, C++ |

### Microservices Architecture

```
ticketmaster-platform/
├── booking-service/          (Java Spring Boot)
│   ├── Handles seat reservations
│   ├── Distributed locking
│   └── Transaction management
│
├── payment-service/          (Java Spring Boot)
│   ├── Stripe/PayPal integration
│   ├── PCI-DSS compliant
│   └── Payment reconciliation
│
├── search-service/           (Node.js)
│   ├── Elasticsearch integration
│   ├── Auto-complete suggestions
│   └── Faceted search
│
├── inventory-service/        (Go)
│   ├── Real-time seat availability
│   ├── Seat status management
│   └── Dynamic pricing engine
│
├── notification-service/     (Python)
│   ├── Email (SendGrid)
│   ├── SMS (Twilio)
│   └── Push notifications (Firebase)
│
├── queue-service/            (Go)
│   ├── Virtual waiting room
│   ├── Token generation
│   └── Admission throttling
│
└── analytics-service/        (Python)
    ├── Event tracking
    ├── User behavior analysis
    └── Revenue reporting
```

### API Gateway

| Solution | Features | When to Use |
|----------|----------|-------------|
| **Kong** ⭐ | Open-source, plugin ecosystem, rate limiting, auth | Production-grade, high customization needs |
| **AWS API Gateway** | Managed service, auto-scaling, AWS integration | AWS-first architecture, quick setup |
| **NGINX Plus** | High performance, load balancing, caching | On-premise or custom infrastructure |
| **Apigee (Google)** | Enterprise-grade, analytics, developer portal | Large organizations, API monetization |

**Ticketmaster Choice**: **Kong** + **AWS ALB**
- Kong for API management (auth, rate limiting, transformations)
- ALB for load balancing across services

---

## 2. Database & Storage

### Transactional Database (OLTP)

| Database | Use Case | Why Chosen | Configuration |
|----------|----------|------------|---------------|
| **PostgreSQL** ⭐ | Primary database for bookings, events, users | ACID guarantees, excellent JSON support, mature replication | Master + 3-5 read replicas, pg_bouncer for connection pooling |
| **MySQL (Aurora)** | Alternative for AWS-centric architecture | AWS Aurora provides auto-scaling, excellent availability | Aurora cluster with 15 read replicas |
| **CockroachDB** | Multi-region, globally distributed | Geo-distributed events (Olympics, global concerts) | 3-5 node cluster per region |

**Ticketmaster Production Setup**:
```yaml
PostgreSQL Configuration:
  Version: PostgreSQL 15
  Instance Type: db.r6g.4xlarge (128 GB RAM, 16 vCPU)
  Sharding: 16 shards (by event_id hash)
  Replication: Master-Replica (3 replicas per shard)
  Connection Pooling: PgBouncer (10,000 max connections)
  Backup: Daily snapshots + PITR (Point-in-time recovery)
  Extensions:
    - pg_partman (partition management)
    - pgcrypto (encryption)
    - pg_stat_statements (query monitoring)
```

### NoSQL Databases

| Database | Use Case | Data Model | Scale |
|----------|----------|------------|-------|
| **Cassandra** ⭐ | Time-series data (click streams, analytics, audit logs) | Column-family | Multi-datacenter, petabyte-scale |
| **MongoDB** | Event catalog, flexible schemas (different event types) | Document | Horizontal sharding |
| **DynamoDB** | Session storage, user preferences, shopping cart state | Key-value | Auto-scaling, serverless |
| **Redis** | See caching section | - | - |

### Object Storage

| Solution | Use Case | Cost (approx) |
|----------|----------|---------------|
| **Amazon S3** ⭐ | Ticket PDFs, QR codes, event images, venue maps, backups | $0.023/GB/month |
| **CloudFront (CDN)** | Fast delivery of tickets, images globally | $0.085/GB transferred |
| **Glacier** | Long-term archive (old tickets, compliance logs) | $0.004/GB/month |

**Storage Lifecycle**:
```
Ticket PDF generated
  → S3 Standard (30 days)
  → S3 Infrequent Access (90 days)
  → S3 Glacier (5 years for compliance)
```

---

## 3. Caching & In-Memory

### Redis Use Cases

| Use Case | Redis Data Structure | TTL | Example |
|----------|---------------------|-----|---------|
| **Distributed Locks** ⭐ | String (SETNX) | 30 seconds | `SETNX lock:seat:A-101 uuid EX 30` |
| **Session Storage** | Hash | 24 hours | `HSET session:abc123 user_id 456` |
| **Rate Limiting** | String (INCR) | 60 seconds | `INCR ratelimit:user:456:minute` |
| **Hot Event Cache** | String (JSON) | 5 minutes | `SET event:123 '{"name":"Concert"}' EX 300` |
| **Queue Positions** | Sorted Set | 1 hour | `ZADD queue:evt-123 timestamp user-456` |
| **Seat Availability Count** | String (counter) | N/A (invalidate on booking) | `GET event:123:available_seats` |

**Redis Production Setup**:
```yaml
Redis Cluster:
  Mode: Cluster (sharded)
  Nodes: 6 (3 masters, 3 replicas)
  Instance Type: cache.r6g.2xlarge (52 GB RAM)
  Persistence:
    - RDB snapshots: Every 15 minutes
    - AOF (Append-Only File): Every second
  High Availability: Redis Sentinel
  Eviction Policy: allkeys-lru
  Max Memory: 50 GB per node
```

### Alternative Caching Solutions

| Solution | When to Use | Pros | Cons |
|----------|-------------|------|------|
| **Memcached** | Simple key-value cache, no persistence needed | Faster than Redis for pure caching | No data structures, no persistence |
| **Hazelcast** | Java-based, in-process cache | JVM integration, distributed compute | Java-only, larger memory footprint |
| **Apache Ignite** | Distributed SQL cache | ACID transactions on cache, SQL support | Complex setup, higher resource usage |

---

## 4. Message Queue & Event Streaming

### Apache Kafka (Primary Choice) ⭐

**Why Kafka for Ticketing**:
- High throughput (millions of events/sec)
- Event sourcing (complete audit trail)
- Replayability (reprocess past bookings for analytics)
- Durability (persistent storage)

**Topics Architecture**:
```
Kafka Topics:
├── booking.created          (Partition: 16, Replication: 3)
│   └── Consumers: Analytics, Email, Inventory
│
├── booking.confirmed        (Partition: 16, Replication: 3)
│   └── Consumers: Ticket Generator, Notification, Revenue
│
├── booking.cancelled        (Partition: 16, Replication: 3)
│   └── Consumers: Refund, Inventory, Waitlist
│
├── payment.completed        (Partition: 16, Replication: 3)
│   └── Consumers: Booking Confirmation, Fraud Detection
│
├── seat.released            (Partition: 16, Replication: 3)
│   └── Consumers: Waitlist Notification, Inventory
│
└── user.activity            (Partition: 32, Replication: 3)
    └── Consumers: Analytics, Recommendations, Fraud ML
```

**Production Configuration**:
```yaml
Kafka Cluster:
  Brokers: 9 (3 per availability zone)
  Instance Type: m5.2xlarge (8 vCPU, 32 GB RAM)
  Zookeeper: 3 nodes (or KRaft mode in Kafka 3.3+)
  Retention: 7 days (configurable per topic)
  Partitioning: By event_id for ordering guarantees
  Replication Factor: 3
  Min In-Sync Replicas: 2
```

### Alternative Message Queues

| Solution | Use Case | Pros | Cons |
|----------|----------|------|------|
| **RabbitMQ** | Complex routing, priority queues | Advanced routing, management UI | Lower throughput than Kafka |
| **AWS SQS** | Simple queues, serverless | Fully managed, auto-scaling | No ordering guarantees (standard), higher latency |
| **AWS Kinesis** | AWS-native streaming | AWS integration, managed service | More expensive than self-hosted Kafka |
| **Google Pub/Sub** | GCP-native | Global scale, easy setup | GCP-only, less control |

---

## 5. Search & Analytics

### Elasticsearch ⭐

**Use Cases**:
- Event search (full-text, filters)
- Auto-complete suggestions
- User activity logs (7-day retention)
- Anomaly detection (fraud patterns)

**Index Structure**:
```json
{
  "index": "events",
  "mappings": {
    "properties": {
      "name": {"type": "text", "analyzer": "english"},
      "description": {"type": "text"},
      "category": {"type": "keyword"},
      "venue": {
        "properties": {
          "name": {"type": "text"},
          "city": {"type": "keyword"},
          "location": {"type": "geo_point"}
        }
      },
      "event_date": {"type": "date"},
      "price_range": {
        "properties": {
          "min": {"type": "float"},
          "max": {"type": "float"}
        }
      },
      "availability": {"type": "keyword"},
      "popularity_score": {"type": "float"}
    }
  }
}
```

**Production Setup**:
```yaml
Elasticsearch Cluster:
  Nodes: 9 (3 master, 6 data)
  Instance Type: r6g.2xlarge (64 GB RAM, 8 vCPU)
  Indices: events, users, bookings_logs
  Shards: 5 primary, 1 replica per index
  Snapshot: Daily to S3
  ILM Policy:
    - Hot: 7 days (frequent searches)
    - Warm: 30 days (occasional queries)
    - Cold: 90 days (archived)
    - Delete: After 1 year
```

### Analytics Databases

| Database | Use Case | Query Speed | Cost |
|----------|----------|-------------|------|
| **ClickHouse** ⭐ | Real-time analytics dashboards, aggregations | <1 second for 100M rows | Low (open-source) |
| **Snowflake** | Data warehouse, BI reporting, ML training | Seconds to minutes | High (pay-per-query) |
| **BigQuery (GCP)** | Ad-hoc analytics, serverless | Seconds | Medium (pay-per-query) |
| **Amazon Redshift** | Data warehouse, AWS integration | Minutes | Medium |

**ClickHouse for Real-Time Analytics**:
```sql
-- Schema for click events
CREATE TABLE click_events (
    timestamp DateTime,
    event_id UUID,
    user_id UUID,
    action String, -- view, search, book, payment
    session_id String,
    ip_address String,
    user_agent String,
    city String,
    country String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, event_id, user_id);

-- Real-time dashboard query (<500ms)
SELECT
    toStartOfHour(timestamp) as hour,
    event_id,
    count(*) as views,
    countIf(action = 'book') as bookings,
    bookings / views as conversion_rate
FROM click_events
WHERE timestamp >= now() - INTERVAL 24 HOUR
GROUP BY hour, event_id
ORDER BY bookings DESC
LIMIT 100;
```

---

## 6. Payment Processing

### Payment Gateways

| Provider | Market Share | Transaction Fee | Features |
|----------|--------------|-----------------|----------|
| **Stripe** ⭐ | 35% | 2.9% + $0.30 | Excellent API, fraud detection, global |
| **PayPal** | 40% | 2.9% + $0.30 | Buyer protection, global brand trust |
| **Braintree** | 15% | 2.9% + $0.30 | PayPal-owned, Venmo integration |
| **Adyen** | 5% | 0.6-3.0% | Enterprise-grade, multi-currency |
| **Square** | 3% | 2.6% + $0.10 | POS integration, simple setup |

**Stripe Integration**:
```javascript
// Create payment intent
const stripe = require('stripe')('sk_live_...');

const paymentIntent = await stripe.paymentIntents.create({
  amount: 15000, // $150.00 in cents
  currency: 'usd',
  payment_method: 'pm_card_visa',
  confirm: true,
  metadata: {
    booking_id: 'bkg-abc123',
    event_id: 'evt-456',
    user_id: 'user-789'
  },
  idempotency_key: 'booking-bkg-abc123' // Prevent duplicate charges
});

// Webhook for async updates
app.post('/webhooks/stripe', (req, res) => {
  const event = req.body;

  switch (event.type) {
    case 'payment_intent.succeeded':
      confirmBooking(event.data.object.metadata.booking_id);
      break;
    case 'payment_intent.payment_failed':
      releaseSeats(event.data.object.metadata.booking_id);
      break;
  }

  res.json({received: true});
});
```

### Fraud Detection

| Solution | Features | Cost |
|----------|----------|------|
| **Stripe Radar** | ML-based fraud detection, 3D Secure | Included with Stripe |
| **Sift** | Account takeover detection, chargeback prevention | $300/month + |
| **Signifyd** | Chargeback guarantee | Per-transaction fee |

---

## 7. Infrastructure & Cloud

### Cloud Providers

| Provider | Market Share | When to Use | Ticketmaster Uses |
|----------|--------------|-------------|-------------------|
| **AWS** ⭐ | 32% | Most mature services, widest adoption | EC2, RDS, S3, CloudFront, Lambda, ECS |
| **Google Cloud** | 10% | AI/ML workloads, BigQuery analytics | BigQuery, Kubernetes Engine |
| **Azure** | 23% | Microsoft ecosystem integration | Rare for startups, common in enterprise |
| **Multi-Cloud** | Growing | Avoid vendor lock-in, geo-distribution | AWS (primary) + GCP (analytics) |

### Container Orchestration

| Solution | Use Case | Learning Curve | Ticketmaster Uses |
|----------|----------|----------------|-------------------|
| **Kubernetes (EKS)** ⭐ | Production microservices | High | All microservices run on K8s |
| **Docker Swarm** | Simple deployments, small teams | Low | Deprecated in favor of K8s |
| **ECS (AWS)** | AWS-native, simpler than K8s | Medium | Alternative for AWS-only shops |
| **Nomad (HashiCorp)** | Lightweight alternative to K8s | Medium | Rare, niche use cases |

**Kubernetes Cluster Setup**:
```yaml
EKS Cluster:
  Name: ticketmaster-prod
  Version: 1.28
  Node Groups:
    - Name: app-nodes
      Instance Type: m5.2xlarge
      Min: 10, Max: 100, Desired: 20
      Auto-scaling: Cluster Autoscaler + HPA
    - Name: kafka-nodes
      Instance Type: m5.4xlarge (Kafka brokers)
      Min: 9, Max: 9 (fixed)
    - Name: db-nodes
      Instance Type: r6g.4xlarge (PostgreSQL)
      Min: 16, Max: 16 (shards)
  Networking: VPC CNI
  Ingress: NGINX Ingress Controller
  Service Mesh: Istio
  Secrets: AWS Secrets Manager + External Secrets Operator
```

### Infrastructure as Code (IaC)

| Tool | Use Case | Popularity | Example |
|------|----------|------------|---------|
| **Terraform** ⭐ | Multi-cloud, declarative | Very High | Define AWS, GCP, Datadog, PagerDuty together |
| **CloudFormation** | AWS-only | High | Native AWS integration |
| **Pulumi** | Infrastructure as code in real programming languages | Growing | TypeScript, Python, Go |
| **Ansible** | Configuration management | Medium | Server provisioning, app deployment |

**Terraform Example**:
```hcl
# terraform/environments/prod/main.tf

module "vpc" {
  source = "../../modules/vpc"
  cidr_block = "10.0.0.0/16"
  availability_zones = ["us-east-1a", "us-east-1b", "us-east-1c"]
}

module "eks_cluster" {
  source = "../../modules/eks"
  cluster_name = "ticketmaster-prod"
  node_groups = {
    app = { instance_type = "m5.2xlarge", min = 10, max = 100 }
    kafka = { instance_type = "m5.4xlarge", min = 9, max = 9 }
  }
}

module "rds_postgres" {
  source = "../../modules/rds"
  instance_class = "db.r6g.4xlarge"
  allocated_storage = 1000 # GB
  multi_az = true
  read_replicas = 3
}

module "redis_cluster" {
  source = "../../modules/elasticache"
  node_type = "cache.r6g.2xlarge"
  num_cache_nodes = 6
  cluster_mode = "enabled"
}
```

---

## 8. DevOps & CI/CD

### CI/CD Pipeline

| Tool | Purpose | Ticketmaster Uses |
|------|---------|-------------------|
| **GitHub Actions** ⭐ | CI/CD automation | Build, test, deploy on every commit |
| **Jenkins** | Self-hosted CI/CD | Legacy, being phased out |
| **GitLab CI** | Integrated with GitLab | Full DevOps platform |
| **CircleCI** | Cloud CI/CD | Fast builds, Docker-native |
| **ArgoCD** | Kubernetes GitOps | Declarative K8s deployments |

**GitHub Actions Workflow**:
```yaml
# .github/workflows/deploy-booking-service.yml

name: Deploy Booking Service

on:
  push:
    branches: [main]
    paths:
      - 'services/booking-service/**'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run unit tests
        run: mvn test

      - name: Run integration tests
        run: mvn verify

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: docker build -t booking-service:${{ github.sha }} .

      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin
          docker push 123456789.dkr.ecr.us-east-1.amazonaws.com/booking-service:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Update Kubernetes deployment
        run: |
          kubectl set image deployment/booking-service \
            booking-service=123456789.dkr.ecr.us-east-1.amazonaws.com/booking-service:${{ github.sha }}

      - name: Wait for rollout
        run: kubectl rollout status deployment/booking-service
```

### GitOps with ArgoCD

```yaml
# k8s/apps/booking-service.yaml

apiVersion: argoproj.io/v1alpha1
kind: Application
metadata:
  name: booking-service
  namespace: argocd
spec:
  project: default
  source:
    repoURL: https://github.com/ticketmaster/k8s-manifests
    targetRevision: HEAD
    path: apps/booking-service
  destination:
    server: https://kubernetes.default.svc
    namespace: production
  syncPolicy:
    automated:
      prune: true # Delete resources removed from git
      selfHeal: true # Auto-sync if kubectl changes made
    syncOptions:
      - CreateNamespace=true
```

### Deployment Strategies

| Strategy | Downtime | Risk | Use Case |
|----------|----------|------|----------|
| **Blue-Green** | Zero | Low | Major releases, safe rollback |
| **Canary** | Zero | Very Low | Gradual rollout, high-risk changes |
| **Rolling** | Zero | Medium | Standard deployments |
| **Recreate** | Yes | High | Development only |

**Canary Deployment (Istio)**:
```yaml
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: booking-service
spec:
  hosts:
    - booking-service
  http:
    - match:
        - headers:
            canary:
              exact: "true"
      route:
        - destination:
            host: booking-service
            subset: v2 # New version
          weight: 100
    - route:
        - destination:
            host: booking-service
            subset: v1 # Old version
          weight: 95
        - destination:
            host: booking-service
            subset: v2 # New version
          weight: 5 # 5% canary traffic
```

---

## 9. Monitoring & Observability

### Metrics & Dashboards

| Tool | Purpose | Retention | Cost |
|------|---------|-----------|------|
| **Prometheus** ⭐ | Metrics collection & alerting | 15 days | Free (self-hosted) |
| **Grafana** ⭐ | Visualization dashboards | N/A (queries Prometheus) | Free (self-hosted) |
| **Datadog** | All-in-one (metrics, logs, APM) | 15 months | $15/host/month |
| **New Relic** | APM, infra monitoring | 30 days | $99/month + |
| **AWS CloudWatch** | AWS-native monitoring | Configurable | Pay-per-metric |

**Prometheus Metrics (Booking Service)**:
```go
// metrics.go
var (
    bookingAttempts = prometheus.NewCounterVec(
        prometheus.CounterOpts{
            Name: "booking_attempts_total",
            Help: "Total booking attempts",
        },
        []string{"status"}, // success, failed, timeout
    )

    bookingDuration = prometheus.NewHistogramVec(
        prometheus.HistogramOpts{
            Name: "booking_duration_seconds",
            Help: "Booking operation duration",
            Buckets: prometheus.LinearBuckets(0.1, 0.1, 10), // 0.1s to 1s
        },
        []string{"operation"}, // lock_acquire, db_query, total
    )

    availableSeats = prometheus.NewGaugeVec(
        prometheus.GaugeOpts{
            Name: "available_seats",
            Help: "Number of available seats per event",
        },
        []string{"event_id"},
    )
)
```

**Grafana Dashboard JSON**:
```json
{
  "dashboard": {
    "title": "Booking Service Metrics",
    "panels": [
      {
        "title": "Booking Success Rate",
        "targets": [
          {
            "expr": "rate(booking_attempts_total{status=\"success\"}[5m]) / rate(booking_attempts_total[5m])"
          }
        ]
      },
      {
        "title": "P95 Booking Latency",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, booking_duration_seconds_bucket)"
          }
        ]
      }
    ]
  }
}
```

### Logging

| Tool | Purpose | Retention | Search Speed |
|------|---------|-----------|--------------|
| **ELK Stack** ⭐ | Centralized logging (Elasticsearch, Logstash, Kibana) | 30 days | Fast (Elasticsearch) |
| **Splunk** | Enterprise logging & SIEM | 90 days | Very Fast |
| **Loki (Grafana)** | Lightweight alternative to ELK | 7-30 days | Medium |
| **AWS CloudWatch Logs** | AWS-native | Configurable | Slow for large queries |

**Structured Logging (JSON)**:
```javascript
// Node.js with Winston
const winston = require('winston');

const logger = winston.createLogger({
  format: winston.format.json(),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: 'booking-service.log' })
  ]
});

logger.info('Booking created', {
  booking_id: 'bkg-abc123',
  user_id: 'user-456',
  event_id: 'evt-789',
  seats: ['A-101', 'A-102'],
  amount: 150.00,
  duration_ms: 234,
  timestamp: new Date().toISOString()
});
```

### Distributed Tracing

| Tool | Features | Cost |
|------|----------|------|
| **Jaeger** ⭐ | OpenTelemetry-compatible, self-hosted | Free |
| **Zipkin** | Simple setup, Twitter-origin | Free |
| **AWS X-Ray** | AWS integration, service maps | Pay-per-trace |
| **Datadog APM** | All-in-one, automatic instrumentation | $31/host/month |

**Jaeger Trace Example**:
```
Trace: User books tickets (500ms total)
├─ API Gateway (10ms)
├─ Booking Service (300ms)
│  ├─ Redis Lock Acquire (50ms)
│  ├─ Database Query (200ms)
│  └─ Kafka Publish (50ms)
├─ Payment Service (150ms)
│  ├─ Stripe API Call (120ms)
│  └─ Database Update (30ms)
└─ Notification Service (40ms)
   ├─ SendGrid Email (25ms)
   └─ Twilio SMS (15ms)
```

### Alerting

| Tool | Integration | Cost |
|------|-------------|------|
| **PagerDuty** ⭐ | On-call management, escalation policies | $25/user/month |
| **Opsgenie** | Atlassian-owned, Jira integration | $9/user/month |
| **Slack** | Notifications only (no escalation) | Free |
| **Email/SMS** | Basic alerting | Free |

**Alerting Rules (Prometheus AlertManager)**:
```yaml
groups:
  - name: booking_alerts
    rules:
      - alert: HighBookingFailureRate
        expr: |
          rate(booking_attempts_total{status="failed"}[5m]) /
          rate(booking_attempts_total[5m]) > 0.10
        for: 2m
        labels:
          severity: critical
          team: booking
        annotations:
          summary: "Booking failure rate > 10%"
          description: "{{ $value | humanizePercentage }} of bookings are failing"
          runbook: "https://wiki.company.com/runbooks/booking-failures"

      - alert: PaymentGatewayDown
        expr: up{job="stripe_health_check"} == 0
        for: 1m
        labels:
          severity: critical
          team: payments
        annotations:
          summary: "Stripe payment gateway is DOWN"
          description: "Payments cannot be processed!"
```

---

## 10. Security & Compliance

### Authentication & Authorization

| Solution | Use Case | Standard |
|----------|----------|----------|
| **OAuth 2.0** | Third-party login (Google, Facebook) | Industry standard |
| **JWT** | Stateless API authentication | Self-contained tokens |
| **Auth0** | Managed auth service | OAuth2 + OIDC |
| **AWS Cognito** | AWS-native user pools | OAuth2 + MFA |
| **Keycloak** | Open-source identity management | Self-hosted |

**JWT Implementation**:
```javascript
const jwt = require('jsonwebtoken');

// Generate token (login)
const token = jwt.sign(
  {
    user_id: 'user-456',
    email: 'john@example.com',
    role: 'customer'
  },
  process.env.JWT_SECRET,
  { expiresIn: '24h' }
);

// Verify token (API requests)
function authenticateToken(req, res, next) {
  const token = req.headers['authorization']?.split(' ')[1];

  if (!token) return res.sendStatus(401);

  jwt.verify(token, process.env.JWT_SECRET, (err, user) => {
    if (err) return res.sendStatus(403);
    req.user = user;
    next();
  });
}
```

### Secrets Management

| Tool | Features | Cost |
|------|----------|------|
| **AWS Secrets Manager** | Auto-rotation, AWS integration | $0.40/secret/month |
| **HashiCorp Vault** | Dynamic secrets, encryption as a service | Free (self-hosted) |
| **Azure Key Vault** | Azure-native | $0.03/10K operations |
| **Doppler** | Developer-friendly, syncs to CI/CD | $5/user/month |

### DDoS Protection

| Solution | Layer | Cost |
|----------|-------|------|
| **CloudFlare** ⭐ | L3-L7 DDoS protection, WAF | $200/month (Pro) |
| **AWS Shield** | L3-L4 DDoS protection | $3,000/month (Advanced) |
| **Akamai** | Enterprise-grade, global | $10,000+/month |

### WAF (Web Application Firewall)

| Solution | Rules | Cost |
|----------|-------|------|
| **CloudFlare WAF** | OWASP Top 10, custom rules | Included with Pro plan |
| **AWS WAF** | Managed rules, rate limiting | $5/month + per-rule cost |
| **Imperva** | Enterprise, bot management | Custom pricing |

### Compliance

| Standard | Requirement | Ticketmaster Must Comply |
|----------|-------------|--------------------------|
| **PCI-DSS** | Payment card data security | ✅ Yes (credit card payments) |
| **GDPR** | EU user data protection | ✅ Yes (European users) |
| **CCPA** | California consumer privacy | ✅ Yes (California users) |
| **SOC 2** | Security, availability, confidentiality | ✅ Yes (enterprise customers) |

---

## 11. CDN & Edge Computing

### CDN Providers

| Provider | Global PoPs | Use Case | Cost |
|----------|-------------|----------|------|
| **CloudFront (AWS)** ⭐ | 450+ | AWS integration, signed URLs for tickets | $0.085/GB |
| **Akamai** | 4,100+ | Largest CDN, enterprise-grade | $0.10-0.15/GB |
| **Cloudflare** | 300+ | DDoS protection included, cheaper | $0.05/GB (Pro plan) |
| **Fastly** | 80+ | Real-time purging, VCL customization | $0.12/GB |

**CloudFront Configuration**:
```yaml
Distribution:
  Origins:
    - DomainName: ticketmaster-api.com
      CustomHeaders:
        X-CDN-Secret: ${secret} # Prevent direct origin access
  Behaviors:
    - PathPattern: /api/*
      CachePolicyId: Managed-CachingDisabled # Dynamic API calls
    - PathPattern: /images/*
      CachePolicyId: Managed-CachingOptimized # Static images
      Compress: true
      ViewerProtocolPolicy: redirect-to-https
  CustomErrorResponses:
    - ErrorCode: 503
      ResponseCode: 503
      ResponsePagePath: /maintenance.html
```

### Edge Computing

| Solution | Use Case | Language |
|----------|----------|----------|
| **Cloudflare Workers** | A/B testing, bot detection at edge | JavaScript |
| **AWS Lambda@Edge** | Request/response transformation | Node.js, Python |
| **Fastly Compute@Edge** | Edge logic | Rust, JavaScript |

---

## 12. Development Tools

### Version Control & Collaboration

| Tool | Purpose |
|------|---------|
| **GitHub** ⭐ | Source code hosting, code reviews, Actions CI/CD |
| **GitLab** | Complete DevOps platform (source, CI/CD, registry) |
| **Bitbucket** | Atlassian integration (Jira, Confluence) |

### Code Quality & Testing

| Tool | Purpose | Language |
|------|---------|----------|
| **SonarQube** | Code quality, security vulnerabilities | Multi-language |
| **ESLint** | JavaScript/TypeScript linting | JavaScript |
| **Checkstyle** | Java code style | Java |
| **JUnit / TestNG** | Unit testing | Java |
| **Jest** | Unit testing | JavaScript |
| **Selenium** | E2E browser testing | Multi-language |
| **Postman** | API testing | REST APIs |
| **K6** | Load testing | JavaScript |

### Load Testing

| Tool | Max RPS | Use Case | Cost |
|------|---------|----------|------|
| **K6** ⭐ | 100K+ | Scripted load tests, CI/CD integration | Free (self-hosted) |
| **Gatling** | 100K+ | Scala-based, detailed reports | Free (open-source) |
| **JMeter** | 10K | Traditional load testing | Free |
| **Locust** | 50K | Python-based, distributed | Free |
| **AWS Load Testing** | 1M+ | Managed service | Pay-per-test |

**K6 Test Script**:
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '2m', target: 100 }, // Ramp up
    { duration: '5m', target: 1000 }, // Peak load
    { duration: '2m', target: 0 }, // Ramp down
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'], // 95% requests < 500ms
    http_req_failed: ['rate<0.01'], // Error rate < 1%
  },
};

export default function () {
  // Search events
  let res = http.get('https://api.ticketmaster.com/events/search?q=concert');
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);

  // Book seats (requires auth)
  res = http.post('https://api.ticketmaster.com/bookings', JSON.stringify({
    eventId: 'evt-123',
    seatIds: ['seat-789', 'seat-790']
  }), {
    headers: { 'Authorization': `Bearer ${__ENV.JWT_TOKEN}` }
  });
  check(res, { 'booking created': (r) => r.status === 201 });
  sleep(2);
}
```

---

## Summary: Recommended Tech Stack

### Startup (MVP)

```
Frontend: React + Next.js
Backend: Node.js (Express) or Python (FastAPI)
Database: PostgreSQL (managed: AWS RDS)
Cache: Redis (managed: ElastiCache)
Queue: AWS SQS (simple, serverless)
Payments: Stripe
Search: Elasticsearch (managed: AWS OpenSearch)
Hosting: AWS (EC2 or ECS)
Monitoring: CloudWatch + Sentry
CI/CD: GitHub Actions
```

**Cost**: ~$2,000/month for 10K users

### Mid-Size (Scaling)

```
Frontend: React + Next.js (CDN: CloudFront)
Backend: Java/Spring Boot + Node.js microservices
Database: PostgreSQL (multi-AZ, read replicas)
Cache: Redis Cluster
Queue: Kafka (self-hosted or MSK)
Payments: Stripe + fraud detection
Search: Elasticsearch cluster (3 nodes)
Hosting: Kubernetes (EKS)
Monitoring: Prometheus + Grafana + Datadog
CI/CD: GitHub Actions + ArgoCD
```

**Cost**: ~$15,000/month for 100K users

### Enterprise (TicketMaster Scale)

```
Frontend: React + Next.js (multi-region CDN)
Backend: Java/Spring Boot microservices (100+ services)
Database: PostgreSQL (16 shards) + Cassandra (analytics)
Cache: Redis Cluster (6-node per region)
Queue: Kafka (9 brokers, multi-region)
Payments: Stripe + Adyen (multi-gateway)
Search: Elasticsearch (9 nodes, multi-AZ)
Analytics: ClickHouse + Snowflake
Hosting: Kubernetes (multi-region, 200+ nodes)
Monitoring: Datadog + PagerDuty + Custom dashboards
CI/CD: GitHub Actions + ArgoCD + Spinnaker
Security: CloudFlare + AWS WAF + Vault
```

**Cost**: $200,000+/month for 10M users

---

## 📚 Additional Resources

- **Engineering Blogs**:
  - [Ticketmaster Tech Blog](https://tech.ticketmaster.com/)
  - [Eventbrite Engineering Blog](https://www.eventbrite.com/engineering/)
  - [StubHub Tech Blog](https://tech.stubhub.com/)

- **Open Source Tools**:
  - [Kubernetes](https://kubernetes.io/)
  - [Prometheus](https://prometheus.io/)
  - [Kafka](https://kafka.apache.org/)
  - [Redis](https://redis.io/)

- **Learning Platforms**:
  - [AWS Well-Architected Framework](https://aws.amazon.com/architecture/well-architected/)
  - [Kubernetes Patterns](https://www.redhat.com/en/resources/oreilly-kubernetes-patterns-ebook)
  - [System Design Primer](https://github.com/donnemartin/system-design-primer)

---

**Built for**: System design interviews, architecture planning, technology selection
**Last Updated**: 2025

🎫 **Go build scalable ticketing platforms!** 🚀
