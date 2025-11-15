# 🛠️ Messaging System Tech Stack & DevOps Guide

Complete technology stack, infrastructure, and DevOps solutions used by industry leaders (WhatsApp, Messenger, Telegram, Signal) for building scalable real-time messaging systems.

---

## 📋 Table of Contents

1. [Application Layer](#application-layer)
2. [Real-Time Communication](#real-time-communication)
3. [Databases & Storage](#databases--storage)
4. [Caching & In-Memory](#caching--in-memory)
5. [Message Queues](#message-queues)
6. [Security & Encryption](#security--encryption)
7. [DevOps & Infrastructure](#devops--infrastructure)
8. [Monitoring & Observability](#monitoring--observability)
9. [CDN & Media Delivery](#cdn--media-delivery)
10. [Industry Comparisons](#industry-comparisons)

---

## 🎯 Application Layer

### Backend Services

| **Technology** | **Use Case** | **Used By** | **Why?** |
|----------------|--------------|-------------|----------|
| **Erlang/OTP** | WebSocket servers, real-time messaging | WhatsApp, Discord | Designed for concurrency, fault-tolerance, hot-swapping |
| **Node.js** | WebSocket gateway, API services | Slack, many startups | Event-driven, perfect for I/O-heavy workloads |
| **Go (Golang)** | WebSocket servers, microservices | Telegram, Discord | Efficient concurrency (goroutines), low latency |
| **Java/Spring Boot** | Business logic, message processing | LinkedIn, enterprise apps | Mature ecosystem, strong typing, JVM performance |
| **Python/FastAPI** | API services, ML features | Many AI-enhanced chat apps | Rapid development, ML libraries |
| **Elixir/Phoenix** | Real-time channels, WebSocket | Modern chat apps | Built on Erlang VM, better DX than Erlang |

### WhatsApp Stack Deep Dive

```
Backend: Erlang/OTP
- Custom Erlang clustering for 100M+ connections
- Modified BEAM VM for performance
- Hot code reloading (zero downtime deployments)

Why Erlang for WhatsApp?
✓ Lightweight processes (millions per server)
✓ Built-in fault tolerance (OTP supervision trees)
✓ Distributed by design
✓ Low latency (~50ms P99)
✓ Battle-tested in telecom (Ericsson heritage)
```

### Technology Comparison

```
┌──────────────────────────────────────────────────────┐
│          WebSocket Server Performance                │
├──────────────┬───────────┬──────────┬───────────────┤
│ Technology   │ Conn/Core │ Latency  │ Memory/Conn   │
├──────────────┼───────────┼──────────┼───────────────┤
│ Erlang/OTP   │ 100K      │ <10ms    │ 2-3 KB        │
│ Node.js      │ 50K       │ <20ms    │ 5-8 KB        │
│ Go           │ 80K       │ <15ms    │ 3-5 KB        │
│ Java/Netty   │ 60K       │ <25ms    │ 8-12 KB       │
│ C++/Boost    │ 150K      │ <5ms     │ 1-2 KB        │
└──────────────┴───────────┴──────────┴───────────────┘
```

---

## 🔌 Real-Time Communication

### WebSocket Libraries & Frameworks

| **Technology** | **Language** | **Features** | **Used By** |
|----------------|--------------|--------------|-------------|
| **Socket.IO** | Node.js | Auto-reconnect, fallback to polling, room support | Slack, many SaaS apps |
| **WS** | Node.js | Lightweight, RFC 6455 compliant | Custom implementations |
| **Cowboy** | Erlang | HTTP/WebSocket server, very efficient | WhatsApp, Discord (historical) |
| **Gorilla WebSocket** | Go | Clean API, production-ready | Telegram, Go services |
| **Netty** | Java | High-performance, async I/O | Enterprise messaging |
| **Channels** | Phoenix/Elixir | PubSub, presence tracking built-in | Modern Elixir apps |

### WebRTC (Voice/Video Calls)

| **Component** | **Technology** | **Purpose** |
|---------------|----------------|-------------|
| **Signaling Server** | Node.js/Go + WebSocket | Coordinate call setup |
| **STUN Server** | coturn, Jitsi | NAT traversal (public IP discovery) |
| **TURN Server** | coturn, Xirsys | Relay traffic when P2P fails |
| **Media Server** | Jitsi, Janus, Kurento | Mix streams, recording, transcoding |
| **SFU (Selective Forwarding)** | Mediasoup, Janus | Efficient multi-party video |

**WhatsApp Voice/Video**:
```
- Signaling: Custom protocol over existing WebSocket connection
- STUN/TURN: Self-hosted coturn servers globally distributed
- Codec: Opus (audio), VP8/H.264 (video)
- E2E Encryption: SRTP with Signal Protocol keys
```

---

## 🗄️ Databases & Storage

### Primary Databases

| **Database** | **Use Case** | **Used By** | **Why?** |
|--------------|--------------|-------------|----------|
| **Apache Cassandra** | Message storage (time-series) | WhatsApp, Instagram, Discord | Write-optimized, horizontal scaling, multi-DC |
| **PostgreSQL** | Users, groups, metadata | Signal, Telegram | ACID, complex queries, battle-tested |
| **MongoDB** | Document storage, flexible schema | Many startups | Easy to start, JSON-like documents |
| **DynamoDB** | Managed NoSQL, serverless | AWS-based apps | Fully managed, auto-scaling, low ops |
| **ScyllaDB** | Cassandra-compatible, faster | Discord (migrated from Cassandra) | 10x faster than Cassandra, lower latency |
| **CockroachDB** | Distributed SQL | Modern apps needing SQL + scale | SQL interface, distributed, strong consistency |

### Cassandra for Messages - Best Practices

```cql
-- Time-series optimized schema
CREATE TABLE messages (
    chat_id UUID,              -- Partition key
    bucket INT,                -- Bucket by month (prevent hot partitions)
    message_id TIMEUUID,       -- Clustering key
    sender_id UUID,
    content TEXT,
    encrypted_content BLOB,
    created_at TIMESTAMP,
    PRIMARY KEY ((chat_id, bucket), message_id)
) WITH CLUSTERING ORDER BY (message_id DESC)
  AND compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_size': 1,
    'compaction_window_unit': 'DAYS'
  };

-- Why Cassandra?
✓ Write throughput: Millions of messages/sec
✓ Time-series optimization (TWCS compaction)
✓ Tunable consistency (LOCAL_QUORUM)
✓ Multi-datacenter replication
✓ Linear scalability (just add nodes)
✓ No single point of failure
```

### Database Sharding Strategies

```
1. By User ID (Users/Contacts):
   shard = hash(user_id) % num_shards
   Example: 16 shards → handle 10B users

2. By Chat ID (Messages):
   shard = hash(chat_id) % num_shards
   Example: Distribute load evenly across chats

3. By Geography (Multi-Region):
   US users → US datacenter
   EU users → EU datacenter
   Comply with data residency laws (GDPR)

4. By Time (Cold Storage):
   Recent (90 days): Hot storage (Cassandra)
   Old (90+ days): Cold storage (S3 Glacier)
```

---

## 💾 Caching & In-Memory

### Redis Use Cases

| **Use Case** | **Data Structure** | **TTL** | **Example** |
|--------------|-------------------|---------|-------------|
| **Online Presence** | String | 5 min | `SET user:123:online 1 EX 300` |
| **Last Seen** | String | No expiry | `SET user:123:last_seen 2025-11-14T10:30:00Z` |
| **Typing Indicator** | String | 5 sec | `SETEX chat:456:typing:user123 5 1` |
| **Recent Messages** | List | 24 hours | `LPUSH chat:789:messages {json}` |
| **Unread Count** | Hash | No expiry | `HINCRBY user:123:unread chat:456 1` |
| **Active Chats** | Sorted Set | No expiry | `ZADD user:123:chats 1731574200 chat:456` |
| **Session Data** | Hash | 7 days | `HSET session:xyz user_id 123` |

### Redis Deployment

```yaml
# Redis Cluster (Sharded)
Architecture: 6 nodes (3 masters + 3 replicas)
Sharding: Consistent hashing (16384 hash slots)
Memory: 64 GB per node
Persistence: RDB snapshots every 5 min + AOF
Replication: Async (replicas lag <100ms)

# Redis Sentinel (High Availability)
Monitors: Redis master nodes
Auto-failover: Promote replica to master on failure
Quorum: 2 sentinels must agree

# Alternative: Redis Enterprise / AWS ElastiCache
- Fully managed
- Auto-scaling
- Active-active geo-replication
```

### Memcached vs Redis

```
Use Memcached when:
✓ Simple key-value cache
✓ Multi-threaded performance needed
✓ Simplicity over features

Use Redis when:
✓ Rich data structures (lists, sets, sorted sets)
✓ Pub/Sub messaging
✓ Persistence required
✓ TTL per key (not just global)
✓ Scripting with Lua

Messaging systems → Redis (99% of the time)
```

---

## 📬 Message Queues

### Queue Technologies

| **Technology** | **Type** | **Used By** | **Best For** |
|----------------|----------|-------------|--------------|
| **Apache Kafka** | Distributed log | WhatsApp, LinkedIn, Uber | High throughput, event streaming, replay |
| **RabbitMQ** | Message broker | Many enterprise apps | Complex routing, AMQP protocol |
| **AWS SQS** | Managed queue | AWS-based apps | Simple, fully managed, serverless |
| **Redis Streams** | Log-based queue | Redis-heavy stacks | Low latency, simple setup |
| **NATS** | Lightweight pub/sub | Microservices | Extremely fast, simple |
| **Apache Pulsar** | Multi-tenant queue | Yahoo, Tencent | Kafka alternative, better multi-tenancy |

### Kafka for Messaging Systems

```yaml
Topics:
  - message.sent           # New message created
  - message.delivered      # Message delivered to recipient
  - message.read           # Message read by recipient
  - notification.push      # Push notification queue
  - media.processing       # Media upload/compression
  - analytics.events       # User analytics

Partitions: 100 per topic
Replication Factor: 3
Retention: 7 days (for replay/debugging)

Consumer Groups:
  - delivery-service       # Deliver messages to recipients
  - notification-service   # Send push notifications
  - analytics-processor    # Process events for analytics
  - audit-logger           # Compliance & audit logs
```

**Why Kafka for Messaging?**
```
✓ Durability: Messages persisted to disk
✓ Replay: Can reprocess events from any point
✓ Ordering: Partitions guarantee order
✓ Scalability: 100K+ messages/sec per broker
✓ Exactly-once: Idempotent producers

Example Flow:
1. Message sent → Kafka (message.sent topic)
2. Delivery service consumes → delivers to recipient
3. Analytics service consumes → tracks metrics
4. If delivery fails → retry from Kafka (replay)
```

---

## 🔐 Security & Encryption

### End-to-End Encryption

| **Protocol/Library** | **Used By** | **Features** |
|---------------------|-------------|--------------|
| **Signal Protocol** | WhatsApp, Signal, Messenger | E2E encryption, forward secrecy, deniability |
| **OMEMO** | XMPP apps (Conversations) | Signal Protocol for XMPP |
| **Matrix/Olm** | Matrix/Element | Double Ratchet, group E2E |
| **MTProto** | Telegram | Custom protocol (controversial) |

### Signal Protocol Implementation

```javascript
// Libraries
- JavaScript: @privacyresearch/libsignal-protocol-typescript
- Java: signal-protocol-java (official)
- Swift: SignalProtocolSwift
- Python: python-axolotl

// Key Components
1. Identity Keys: Long-term key pair per user
2. Pre-keys: One-time keys for initial handshake
3. Session Keys: Ephemeral keys for each message
4. Ratcheting: Keys evolve with each message (forward secrecy)

// Implementation Example (Simplified)
const signal = require('@signalapp/libsignal-client');

// Registration
const identityKeyPair = signal.PrivateKey.generate();
const preKeys = generatePreKeys(100);
uploadKeys(userId, identityKeyPair.publicKey, preKeys);

// Encrypt
const session = await signal.SessionBuilder.processPreKey(bundle);
const ciphertext = await session.encrypt(message);

// Decrypt
const plaintext = await session.decrypt(ciphertext);
```

### Transport Security

```yaml
TLS Configuration:
  Version: TLS 1.3 (minimum 1.2)
  Cipher Suites:
    - TLS_AES_256_GCM_SHA384
    - TLS_CHACHA20_POLY1305_SHA256
  Certificate: Let's Encrypt (auto-renewal)
  HSTS: Enabled (max-age=31536000)
  OCSP Stapling: Enabled

API Authentication:
  - JWT (JSON Web Tokens) for sessions
  - Phone number + OTP for registration
  - Public key cryptography for device linking

Rate Limiting (DDoS Protection):
  - Per IP: 100 requests/min
  - Per User: 1000 messages/hour
  - Cloudflare / AWS Shield for DDoS mitigation
```

---

## ☁️ DevOps & Infrastructure

### Container Orchestration

| **Platform** | **Used By** | **Pros** | **Cons** |
|--------------|-------------|----------|----------|
| **Kubernetes** | Most large-scale apps | Industry standard, mature ecosystem | Complex, steep learning curve |
| **Docker Swarm** | Smaller deployments | Simple, Docker-native | Less features than K8s |
| **ECS (AWS)** | AWS-native apps | Tight AWS integration | Vendor lock-in |
| **Nomad** | HashiCorp stack users | Simple, multi-workload | Smaller ecosystem |

### Kubernetes Deployment

```yaml
# WebSocket Server Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: websocket-server
spec:
  replicas: 100                    # 100 servers × 100K conn = 10M connections
  selector:
    matchLabels:
      app: websocket-server
  template:
    metadata:
      labels:
        app: websocket-server
    spec:
      containers:
      - name: websocket
        image: myapp/websocket:v1.2.3
        resources:
          requests:
            memory: "2Gi"           # 100K × 10KB = 1GB + 1GB overhead
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: MAX_CONNECTIONS
          value: "100000"
        - name: REDIS_URL
          valueFrom:
            secretKeyRef:
              name: redis-credentials
              key: url
        ports:
        - containerPort: 8080
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
          initialDelaySeconds: 10
          periodSeconds: 5

---
# Service with Session Affinity (Sticky Sessions)
apiVersion: v1
kind: Service
metadata:
  name: websocket-service
spec:
  type: LoadBalancer
  sessionAffinity: ClientIP         # Sticky sessions
  sessionAffinityConfig:
    clientIP:
      timeoutSeconds: 3600
  selector:
    app: websocket-server
  ports:
  - protocol: TCP
    port: 443
    targetPort: 8080
```

### Infrastructure as Code (IaC)

```hcl
# Terraform Example: AWS Infrastructure

# VPC & Networking
module "vpc" {
  source = "terraform-aws-modules/vpc/aws"

  name = "messaging-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["us-east-1a", "us-east-1b", "us-east-1c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway = true
  enable_vpn_gateway = false
}

# EKS Cluster
module "eks" {
  source = "terraform-aws-modules/eks/aws"

  cluster_name    = "messaging-cluster"
  cluster_version = "1.28"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    websocket_servers = {
      min_size     = 50
      max_size     = 200
      desired_size = 100

      instance_types = ["c5.2xlarge"]  # 8 vCPU, 16 GB RAM
      capacity_type  = "SPOT"           # 70% cost savings
    }

    api_services = {
      min_size     = 10
      max_size     = 50
      desired_size = 20

      instance_types = ["m5.xlarge"]
      capacity_type  = "ON_DEMAND"
    }
  }
}

# RDS PostgreSQL (Users/Groups)
resource "aws_db_instance" "users_db" {
  identifier           = "messaging-users-db"
  engine              = "postgres"
  engine_version      = "15.4"
  instance_class      = "db.r6g.xlarge"
  allocated_storage   = 500
  storage_type        = "gp3"
  storage_encrypted   = true

  multi_az            = true
  publicly_accessible = false

  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "sun:04:00-sun:05:00"
}

# ElastiCache Redis (Presence & Cache)
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "messaging-redis"
  replication_group_description = "Redis for presence and caching"

  engine               = "redis"
  engine_version       = "7.0"
  node_type            = "cache.r6g.xlarge"
  number_cache_clusters = 3

  automatic_failover_enabled = true
  multi_az_enabled           = true

  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
}

# S3 for Media
resource "aws_s3_bucket" "media" {
  bucket = "messaging-media-files"

  lifecycle_rule {
    id      = "archive-old-media"
    enabled = true

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 730  # Delete after 2 years
    }
  }
}

# CloudFront CDN
resource "aws_cloudfront_distribution" "media_cdn" {
  origin {
    domain_name = aws_s3_bucket.media.bucket_regional_domain_name
    origin_id   = "S3-media"
  }

  enabled             = true
  price_class         = "PriceClass_All"

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-media"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400    # 1 day
    max_ttl                = 31536000 # 1 year
  }
}
```

### CI/CD Pipeline

```yaml
# GitHub Actions Example
name: Deploy WebSocket Server

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run tests
        run: |
          npm install
          npm test
      - name: Load tests
        run: |
          npm run load-test  # Simulate 100K connections

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker image
        run: |
          docker build -t myapp/websocket:${{ github.sha }} .
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin
          docker push myapp/websocket:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/websocket-server \
            websocket=myapp/websocket:${{ github.sha }}
          kubectl rollout status deployment/websocket-server
      - name: Health check
        run: |
          sleep 60
          kubectl get pods -l app=websocket-server
          curl https://api.messaging.com/health
```

---

## 📊 Monitoring & Observability

### Monitoring Stack

| **Component** | **Technology** | **Purpose** |
|---------------|----------------|-------------|
| **Metrics** | Prometheus + Grafana | Time-series metrics, dashboards |
| **Logging** | ELK Stack (Elasticsearch, Logstash, Kibana) | Centralized logging |
| **Tracing** | Jaeger / Zipkin | Distributed tracing |
| **APM** | New Relic / Datadog | Application performance monitoring |
| **Alerting** | PagerDuty / OpsGenie | On-call alerts |
| **Uptime** | Pingdom / UptimeRobot | External monitoring |

### Key Metrics to Track

```yaml
WebSocket Metrics:
  - active_connections (gauge)
  - connection_duration_seconds (histogram)
  - messages_sent_total (counter)
  - messages_received_total (counter)
  - connection_errors_total (counter)

Message Delivery:
  - message_send_latency_seconds (histogram)
    - Buckets: [0.01, 0.05, 0.1, 0.5, 1, 5]
  - message_delivery_success_total (counter)
  - message_delivery_failure_total (counter)
  - message_delivery_latency_seconds (histogram)

Database:
  - cassandra_write_latency_ms (histogram)
  - cassandra_read_latency_ms (histogram)
  - postgres_query_duration_seconds (histogram)
  - database_connection_pool_active (gauge)

Cache:
  - redis_hit_rate (gauge)
  - redis_memory_usage_bytes (gauge)
  - redis_commands_per_second (gauge)

Queue:
  - kafka_consumer_lag (gauge)
  - kafka_messages_per_second (gauge)
  - queue_depth (gauge)
```

### Grafana Dashboard Example

```json
{
  "dashboard": {
    "title": "Messaging System Overview",
    "panels": [
      {
        "title": "Active WebSocket Connections",
        "targets": [
          {
            "expr": "sum(websocket_connections_active)"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Message Delivery Latency (P95)",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, message_delivery_latency_seconds_bucket)"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "rate(message_delivery_failure_total[5m]) / rate(message_delivery_total[5m])"
          }
        ],
        "type": "singlestat",
        "thresholds": "0.01,0.05"  # Alert if >1% or >5%
      }
    ]
  }
}
```

---

## 🌐 CDN & Media Delivery

### CDN Providers

| **Provider** | **Used By** | **Strengths** |
|--------------|-------------|---------------|
| **Cloudflare** | WhatsApp, Discord | DDoS protection, global PoPs, free tier |
| **AWS CloudFront** | AWS-based apps | Deep AWS integration, Lambda@Edge |
| **Fastly** | Slack, GitHub | Real-time purging, VCL customization |
| **Akamai** | Enterprise | Largest network, premium service |
| **BunnyCDN** | Startups | Low cost, good performance |

### Media Processing Pipeline

```
Upload Flow:
┌──────────┐     ┌──────────┐     ┌──────────┐     ┌──────────┐
│  Client  │────▶│   API    │────▶│   S3     │────▶│  Kafka   │
│          │     │ Gateway  │     │  Upload  │     │  Event   │
└──────────┘     └──────────┘     └──────────┘     └──────────┘
                                                          │
                                  ┌───────────────────────┘
                                  ▼
                            ┌──────────┐
                            │  Media   │
                            │Processor │
                            └────┬─────┘
                                 │
                    ┌────────────┼────────────┐
                    ▼            ▼            ▼
              ┌─────────┐  ┌─────────┐  ┌─────────┐
              │  Image  │  │  Video  │  │  Audio  │
              │Compress │  │Transcode│  │Compress │
              └────┬────┘  └────┬────┘  └────┬────┘
                   │            │            │
                   └────────────┼────────────┘
                                ▼
                          ┌──────────┐
                          │    S3    │
                          │ (Final)  │
                          └────┬─────┘
                               │
                               ▼
                        ┌──────────┐
                        │CloudFront│
                        │   CDN    │
                        └──────────┘

Processing:
- Images: Compress (80% JPEG), resize (thumbnail, medium, full)
- Videos: Transcode to multiple bitrates (360p, 720p, 1080p)
- Audio: Compress with Opus codec
- Generate thumbnails for all media types
```

---

## 🏢 Industry Comparisons

### WhatsApp Tech Stack

```yaml
Language: Erlang/OTP
WebSocket: Custom Erlang implementation
Database:
  - Mnesia (Erlang DB) for routing metadata
  - Custom storage layer built on Erlang
Encryption: Signal Protocol
Infrastructure: FreeBSD servers
Deployment: Custom tooling (pre-Kubernetes era)
Monitoring: Custom Erlang instrumentation

Peak Stats:
  - 100M+ concurrent connections
  - 100 billion messages/day
  - 50 engineers to run (before Meta acquisition)
  - ~550 servers for billions of users

Why So Efficient?
  ✓ Erlang designed for telecom (millions of concurrent calls)
  ✓ Shared-nothing architecture
  ✓ Lightweight processes (2-3 KB per connection)
  ✓ Built-in distribution and fault tolerance
```

### Discord Tech Stack

```yaml
Language: Elixir (Phoenix), Go, Python, Rust
WebSocket: Phoenix Channels (Elixir)
Database:
  - ScyllaDB (migrated from Cassandra for 10x performance)
  - PostgreSQL (users, guilds)
Database Migration Story:
  - Started: MongoDB
  - Migrated to: Cassandra (2017)
  - Migrated to: ScyllaDB (2022) - 10x lower latency
Cache: Redis
Queue: Custom Rust-based message queue
Infrastructure: Google Cloud Platform (GKE)

Peak Stats:
  - 150M+ monthly active users
  - 15B messages/day
  - 500K concurrent voice channels

Tech Choices:
  ✓ Elixir for real-time (Phoenix Channels)
  ✓ Go for performance-critical services
  ✓ Rust for ultra-low-latency components
  ✓ ScyllaDB for 10x better P99 latency than Cassandra
```

### Telegram Tech Stack

```yaml
Language: C++, Java (Android), Swift (iOS)
Database: Custom distributed key-value store
Encryption: MTProto (custom protocol)
Infrastructure: Own servers in multiple countries
CDN: Custom CDN with 300+ PoPs

Unique Approach:
  - No third-party infrastructure (no AWS, GCP)
  - Own data centers for data sovereignty
  - Custom protocols (MTProto)
  - Emphasis on speed and security

Peak Stats:
  - 700M+ monthly active users
  - 50B messages/day
  - Custom infrastructure (not cloud-based)
```

### Slack Tech Stack

```yaml
Language: PHP, Node.js, Java, Go
WebSocket: Socket.IO (Node.js)
Database:
  - MySQL (sharded)
  - Vitess (MySQL sharding layer)
Cache: Redis, Memcached
Queue: Kafka, RabbitMQ
Infrastructure: AWS (EC2, RDS, ElastiCache)
Search: Elasticsearch

Enterprise Focus:
  ✓ Strong SQL (transactions for enterprise features)
  ✓ Comprehensive audit logs
  ✓ Advanced permissions and security
  ✓ Integration ecosystem (1000+ apps)
```

---

## 🎓 Best Practices Summary

### Technology Selection Criteria

```
Choose based on:

1. Scale Requirements:
   - <1M users: Node.js + PostgreSQL + Redis
   - 1M-10M users: Go/Java + PostgreSQL + Redis + Cassandra
   - 10M-100M users: Erlang/Elixir + Cassandra + Redis Cluster
   - 100M+ users: Custom optimizations, consider Erlang/OTP

2. Team Expertise:
   - Strong Java team → Spring Boot
   - Node.js expertise → Express/Fastify
   - Real-time focus → Erlang/Elixir
   - Performance critical → Go/Rust

3. Infrastructure:
   - Cloud-native → Kubernetes + managed services
   - Cost-sensitive → Serverless (Lambda + DynamoDB)
   - Control needed → Self-hosted

4. Feature Requirements:
   - E2E encryption → Signal Protocol (libsignal)
   - Voice/Video → WebRTC + STUN/TURN
   - File sharing → S3 + CloudFront
   - Search → Elasticsearch
```

### Deployment Checklist

```
□ Multi-region deployment (US, EU, APAC)
□ Auto-scaling configured (CPU >70%, connections >80K)
□ Database replication (multi-AZ minimum)
□ Redis cluster (3 masters + 3 replicas)
□ CDN configured for media
□ TLS 1.3 enforced
□ Rate limiting enabled
□ DDoS protection active
□ Monitoring & alerting configured
□ Backup strategy tested (RPO <1 hour, RTO <30 min)
□ Load testing completed (2x expected peak load)
□ Disaster recovery plan documented
□ Security audit completed
□ Compliance verified (GDPR, HIPAA if needed)
```

---

## 📚 Further Resources

### Official Documentation
- **Erlang/OTP**: https://www.erlang.org/docs
- **Phoenix Framework**: https://hexdocs.pm/phoenix/
- **Signal Protocol**: https://signal.org/docs/
- **Apache Cassandra**: https://cassandra.apache.org/doc/
- **Kubernetes**: https://kubernetes.io/docs/

### Engineering Blogs
- **WhatsApp Engineering**: https://engineering.fb.com/category/whatsapp/
- **Discord Engineering**: https://discord.com/category/engineering
- **Slack Engineering**: https://slack.engineering/
- **Telegram**: https://core.telegram.org/

### Books
- "Designing Data-Intensive Applications" by Martin Kleppmann
- "Release It!" by Michael Nygard (resilience patterns)
- "The Phoenix Project" (DevOps practices)

---

## 🎯 Key Takeaways

1. **Erlang/Elixir** dominates real-time messaging for good reason (concurrency, fault-tolerance)
2. **Cassandra/ScyllaDB** is the go-to for time-series message storage
3. **Redis** is essential for presence, caching, and real-time features
4. **Signal Protocol** is the industry standard for E2E encryption
5. **Kubernetes** for orchestration, but consider operational complexity
6. **CDN** is non-negotiable for media-heavy messaging apps
7. **Monitoring** is critical - you can't fix what you can't measure

---

*Last Updated: 2025-11-14*
*This guide covers production-grade technologies used by leading messaging platforms at scale.*
