# 🛠️ Notification Service: Tech Stack & DevOps Solutions

## Industry-Standard Technology Choices for Notification Systems

This document provides a comprehensive overview of the technology stack used in production notification systems at scale, based on practices from companies like Facebook, Uber, Airbnb, and DoorDash.

---

## 📋 Table of Contents

1. [Application Layer](#application-layer)
2. [Message Queue & Event Streaming](#message-queue--event-streaming)
3. [Databases & Data Storage](#databases--data-storage)
4. [Caching Layer](#caching-layer)
5. [Third-Party Provider SDKs](#third-party-provider-sdks)
6. [Infrastructure & Orchestration](#infrastructure--orchestration)
7. [Monitoring & Observability](#monitoring--observability)
8. [DevOps & CI/CD](#devops--cicd)
9. [Security & Compliance](#security--compliance)
10. [Cost Comparison](#cost-comparison)

---

## 1. Application Layer

### Backend Services

| **Technology** | **Use Case** | **Pros** | **Cons** | **Companies Using** |
|----------------|--------------|----------|----------|---------------------|
| **Node.js (Express/Fastify)** | API Gateway, Workers | Fast I/O, async-friendly, large ecosystem | Single-threaded, memory leaks | Netflix, Uber, LinkedIn |
| **Go (Gin/Echo)** | High-performance workers | Excellent concurrency, low memory, fast | Smaller ecosystem, verbose error handling | Uber, Twitch, Dropbox |
| **Python (FastAPI/Django)** | Orchestration, ML models | Rapid development, ML libraries | Slower than Go/Node, GIL limitations | Instagram, Spotify, Airbnb |
| **Java (Spring Boot)** | Enterprise services | Mature ecosystem, strong typing, JVM performance | Verbose, slower startup | LinkedIn, Twitter, Amazon |
| **Rust** | Ultra-low latency workers | Memory safe, no GC, blazing fast | Steep learning curve, smaller ecosystem | Discord, Cloudflare |

**Recommendation**: 
- **API Gateway**: Node.js (async I/O) or Go (raw performance)
- **Workers**: Go (concurrency) or Java (maturity)
- **ML Features**: Python (ecosystem)

### Example: Node.js Notification Worker

```javascript
// High-performance worker with Node.js
const { Kafka } = require('kafkajs');
const Fastify = require('fastify');

const kafka = new Kafka({
  clientId: 'notification-worker',
  brokers: ['kafka1:9092', 'kafka2:9092', 'kafka3:9092']
});

const consumer = kafka.consumer({ groupId: 'email-workers' });

async function processNotification(message) {
  const notification = JSON.parse(message.value);
  
  // Send via provider
  await sendgridClient.send({
    to: notification.email,
    subject: notification.subject,
    html: notification.body
  });
  
  // Update delivery status
  await updateStatus(notification.id, 'DELIVERED');
}

async function startWorker() {
  await consumer.connect();
  await consumer.subscribe({ topic: 'email-notifications', fromBeginning: false });
  
  await consumer.run({
    eachMessage: async ({ message }) => {
      await processNotification(message);
    }
  });
}

startWorker();
```

---

## 2. Message Queue & Event Streaming

### Comparison Table

| **Technology** | **Throughput** | **Latency** | **Use Case** | **Cost (AWS)** | **Companies** |
|----------------|----------------|-------------|--------------|----------------|---------------|
| **Apache Kafka** | 1M+ msg/sec | <10ms | High-throughput, event streaming, log aggregation | $0.75/GB ingress | Uber, LinkedIn, Netflix |
| **RabbitMQ** | 50K msg/sec | <1ms | Task queues, request-reply, RPC | Self-hosted | Robinhood, Mozilla |
| **AWS SQS** | 300K msg/sec | ~100ms | Managed queues, serverless | $0.40/1M requests | Airbnb, Lyft |
| **AWS SNS** | 300K msg/sec | <100ms | Pub-sub, fan-out | $0.50/1M requests | AWS-native apps |
| **Redis Streams** | 100K msg/sec | <1ms | Lightweight streaming, caching + queue | Self-hosted | Stack Overflow, GitHub |
| **Google Pub/Sub** | 100M+ msg/sec | <100ms | GCP-native, global scale | $40/TB ingress | Spotify, Snap |

**Recommendation**: 
- **Large scale (>1M msg/day)**: Kafka (best throughput, replay capability)
- **Serverless/AWS-native**: SQS + SNS (no ops, auto-scaling)
- **Low latency (<1ms)**: RabbitMQ or Redis Streams

### Kafka Configuration for Notifications

```yaml
# Kafka cluster configuration
kafka:
  brokers: 3
  topics:
    - name: email-notifications
      partitions: 20  # For parallelism
      replication: 3
      retention: 168h  # 7 days
      
    - name: sms-notifications
      partitions: 10
      replication: 3
      retention: 168h
      
    - name: push-notifications
      partitions: 30  # Highest volume
      replication: 3
      retention: 168h
      
    - name: notification-dlq
      partitions: 5
      replication: 3
      retention: 720h  # 30 days for debugging

  consumer-groups:
    - name: email-workers
      instances: 10
      max-poll-records: 100
      
    - name: sms-workers
      instances: 5
      max-poll-records: 50
      
    - name: push-workers
      instances: 20
      max-poll-records: 200
```

---

## 3. Databases & Data Storage

### Primary Database

| **Database** | **Type** | **Best For** | **Throughput** | **Cost** | **Companies** |
|--------------|----------|--------------|----------------|----------|---------------|
| **PostgreSQL** | SQL | ACID compliance, complex queries, JSON support | 50K writes/sec | $200-2K/mo | Instagram, Reddit, Spotify |
| **MySQL** | SQL | Read-heavy, mature ecosystem | 40K writes/sec | $150-1.5K/mo | Facebook, Twitter, Uber |
| **Cassandra** | NoSQL | Write-heavy, time-series, high availability | 1M writes/sec | $500-5K/mo | Netflix, Apple, Discord |
| **DynamoDB** | NoSQL | Serverless, auto-scaling, AWS-native | 100K writes/sec | $1.25/1M writes | Amazon, Lyft, Airbnb |
| **MongoDB** | Document DB | Flexible schema, rapid development | 100K writes/sec | $300-3K/mo | Uber, eBay, Adobe |

**Recommendation**:
- **Structured data, audit trail**: PostgreSQL (with partitioning)
- **Massive write volume**: Cassandra or DynamoDB
- **Flexible schema, rapid iteration**: MongoDB

### PostgreSQL Setup for Notifications

```sql
-- Production-ready PostgreSQL configuration

-- Enable partitioning extension
CREATE EXTENSION IF NOT EXISTS pg_partman;

-- Create partitioned table by month
CREATE TABLE notifications (
    id UUID DEFAULT gen_random_uuid(),
    user_id BIGINT NOT NULL,
    channel VARCHAR(50),
    status VARCHAR(50),
    created_at TIMESTAMP DEFAULT NOW(),
    -- ... other fields
) PARTITION BY RANGE (created_at);

-- Create monthly partitions
CREATE TABLE notifications_2025_01 PARTITION OF notifications
    FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');
    
CREATE TABLE notifications_2025_02 PARTITION OF notifications
    FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');

-- Indexes for common query patterns
CREATE INDEX CONCURRENTLY idx_user_created 
    ON notifications(user_id, created_at DESC);
    
CREATE INDEX CONCURRENTLY idx_status_created 
    ON notifications(status, created_at) 
    WHERE status IN ('QUEUED', 'FAILED');

-- Connection pooling (PgBouncer)
# pgbouncer.ini
[databases]
notifications = host=pg-primary.internal port=5432 dbname=notifications

[pgbouncer]
pool_mode = transaction
max_client_conn = 1000
default_pool_size = 25
reserve_pool_size = 5
```

### Time-Series Analytics Database

| **Database** | **Strength** | **Query Speed** | **Compression** | **Cost** | **Companies** |
|--------------|--------------|-----------------|-----------------|----------|---------------|
| **ClickHouse** | OLAP queries, real-time analytics | 100x faster than MySQL | 10:1 ratio | $500-3K/mo | Uber, Cloudflare, Bloomberg |
| **TimescaleDB** | PostgreSQL extension, SQL familiar | 20x faster than PostgreSQL | 95% storage reduction | $300-2K/mo | Comcast, IBM |
| **Druid** | Real-time ingestion, sub-second queries | Very fast | Good | $1K-5K/mo | Netflix, PayPal |

**Recommendation**: ClickHouse (best analytics performance for notification metrics)

---

## 4. Caching Layer

### Cache Solutions

| **Technology** | **Throughput** | **Latency** | **Use Case** | **Cost** | **Companies** |
|----------------|----------------|-------------|--------------|----------|---------------|
| **Redis** | 100K ops/sec | <1ms | Hot data, session, rate limiting | $50-500/mo | Twitter, GitHub, Stack Overflow |
| **Memcached** | 500K ops/sec | <1ms | Simple key-value, high throughput | $30-300/mo | Facebook, YouTube |
| **Redis Cluster** | 1M+ ops/sec | <1ms | Distributed, HA, sharding | $500-5K/mo | Uber, Lyft, Airbnb |

**Recommendation**: Redis (rich data structures for preferences, rate limiting)

### Redis Configuration

```yaml
# Redis cluster for high availability
redis:
  mode: cluster
  nodes: 6  # 3 masters + 3 replicas
  max-memory: 10GB
  eviction-policy: allkeys-lru
  
  persistence:
    rdb: enabled
    aof: enabled
    aof-fsync: everysec
  
  use-cases:
    - User preferences (Hash)
    - Rate limiting (Sorted Set)
    - Deduplication (String with TTL)
    - Templates (String)
    - Session data (Hash)
```

---

## 5. Third-Party Provider SDKs

### Email Providers

| **Provider** | **Cost** | **Deliverability** | **Features** | **SDK Quality** | **Companies** |
|--------------|----------|-------------------|--------------|-----------------|---------------|
| **SendGrid** | $0.001/email | 98%+ | Templates, A/B testing, analytics | Excellent | Uber, Airbnb, Spotify |
| **AWS SES** | $0.0001/email | 95%+ | Bare-bones, needs setup | Good | AWS-native apps |
| **Mailgun** | $0.0008/email | 97%+ | Email validation, routing | Excellent | Lyft, GitHub |
| **Postmark** | $0.0125/email | 99%+ | Transactional focus, fast | Excellent | Basecamp, Shopify |

**Recommendation**: SendGrid (primary) + AWS SES (fallback)

### SMS Providers

| **Provider** | **Cost (US)** | **Global Coverage** | **Features** | **SDK** | **Companies** |
|--------------|---------------|---------------------|--------------|---------|---------------|
| **Twilio** | $0.0075/SMS | 180+ countries | Voice, video, WhatsApp, verify | Excellent | Uber, Airbnb, Lyft |
| **AWS SNS** | $0.0065/SMS | 200+ countries | Integrated with AWS | Good | AWS-native |
| **Vonage (Nexmo)** | $0.0072/SMS | 190+ countries | Verify, number insight | Good | LinkedIn |
| **Plivo** | $0.0070/SMS | 190+ countries | Voice, SIP trunking | Good | IBM, Workday |

**Recommendation**: Twilio (best reliability) + Vonage (fallback)

### Push Notification Providers

| **Provider** | **Cost** | **Platforms** | **Features** | **SDK** | **Companies** |
|--------------|----------|---------------|--------------|---------|---------------|
| **Firebase Cloud Messaging (FCM)** | FREE | Android, iOS, Web | Google-backed, reliable | Excellent | Most Android apps |
| **Apple Push Notification Service (APNS)** | FREE | iOS, macOS, watchOS | Required for iOS | Good | All iOS apps |
| **OneSignal** | FREE (self-serve) | All platforms | Analytics, segmentation, A/B | Excellent | HubSpot, Starbucks |
| **AWS SNS** | $0.50/1M | All platforms | AWS integration | Good | AWS-native |

**Recommendation**: FCM + APNS (direct, free) or OneSignal (unified SDK)

---

## 6. Infrastructure & Orchestration

### Container Orchestration

| **Platform** | **Complexity** | **Features** | **Cost** | **Best For** | **Companies** |
|--------------|----------------|--------------|----------|--------------|---------------|
| **Kubernetes** | High | Auto-scaling, self-healing, service mesh | $200-5K/mo | Large scale, multi-cloud | Google, Spotify, Uber |
| **AWS ECS/Fargate** | Medium | Serverless containers, AWS-native | $150-3K/mo | AWS users, less ops | Expedia, Samsung |
| **Docker Swarm** | Low | Simple orchestration | $50-500/mo | Small teams | Startups |
| **Nomad (HashiCorp)** | Medium | Simple, multi-workload | $100-1K/mo | Mixed workloads | Cloudflare, Roblox |

**Recommendation**: Kubernetes (industry standard) or ECS Fargate (less ops overhead)

### Kubernetes Setup

```yaml
# Kubernetes deployment for notification workers
apiVersion: apps/v1
kind: Deployment
metadata:
  name: email-worker
spec:
  replicas: 10
  selector:
    matchLabels:
      app: email-worker
  template:
    metadata:
      labels:
        app: email-worker
    spec:
      containers:
      - name: worker
        image: notif-service/email-worker:v1.2.3
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "1000m"
            memory: "1Gi"
        env:
        - name: KAFKA_BROKERS
          value: "kafka1:9092,kafka2:9092,kafka3:9092"
        - name: SENDGRID_API_KEY
          valueFrom:
            secretKeyRef:
              name: provider-secrets
              key: sendgrid-key

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: email-worker-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: email-worker
  minReplicas: 5
  maxReplicas: 50
  metrics:
  - type: External
    external:
      metric:
        name: kafka_consumer_lag
      target:
        type: AverageValue
        averageValue: "1000"  # Scale when lag > 1000 messages
```

### Infrastructure as Code (IaC)

| **Tool** | **Language** | **Strength** | **Learning Curve** | **Companies** |
|----------|--------------|--------------|-------------------|---------------|
| **Terraform** | HCL | Multi-cloud, mature, large community | Medium | Uber, Slack, GitHub |
| **AWS CDK** | TypeScript/Python | Type-safe, AWS-native | Medium | AWS users |
| **Pulumi** | Real languages (Python/TS/Go) | Flexible, multi-cloud | Medium | Snowflake, Mercedes-Benz |
| **CloudFormation** | YAML/JSON | AWS-native, free | High (verbose) | AWS-only apps |

**Recommendation**: Terraform (multi-cloud) or AWS CDK (AWS-only, better DX)

---

## 7. Monitoring & Observability

### Metrics & Monitoring

| **Tool** | **Type** | **Strength** | **Cost** | **Companies** |
|----------|----------|--------------|----------|---------------|
| **Prometheus + Grafana** | Metrics + Visualization | Open-source, flexible, PromQL | Free (self-hosted) | SoundCloud, GitLab, DigitalOcean |
| **Datadog** | All-in-one APM | Easy setup, great UX, integrations | $15-31/host/mo | Airbnb, Peloton, Samsung |
| **New Relic** | All-in-one APM | Deep insights, AI alerts | $25-99/host/mo | DoorDash, GitHub, Lyft |
| **AWS CloudWatch** | Metrics + Logs | AWS-native, integrated | $0.30/custom metric | AWS users |

**Recommendation**: Prometheus + Grafana (cost-effective) or Datadog (ease of use)

### Logging

| **Stack** | **Components** | **Search Speed** | **Cost** | **Companies** |
|-----------|----------------|------------------|----------|---------------|
| **ELK (Elasticsearch, Logstash, Kibana)** | E+L+K | Very fast | $500-5K/mo | LinkedIn, Netflix, Walmart |
| **EFK (Elasticsearch, Fluentd, Kibana)** | E+F+K | Very fast | $400-4K/mo | Google, Microsoft |
| **Loki (Grafana)** | Loki + Promtail + Grafana | Fast | Free (self-hosted) | Grafana Labs |
| **AWS CloudWatch Logs** | CloudWatch | Moderate | $0.50/GB ingested | AWS-native |
| **Datadog Logs** | Datadog | Fast | $0.10/GB ingested | Datadog users |

**Recommendation**: Loki (cost-effective) or ELK (powerful search)

### Distributed Tracing

| **Tool** | **Protocol** | **Features** | **Cost** | **Companies** |
|----------|--------------|--------------|----------|---------------|
| **Jaeger** | OpenTelemetry | Open-source, Uber-built | Free | Uber, Red Hat |
| **Zipkin** | OpenZipkin | Simple, mature | Free | Twitter, SoundCloud |
| **Datadog APM** | Proprietary | Integrated, easy | $31-40/host/mo | Datadog users |
| **AWS X-Ray** | AWS X-Ray | AWS-native | $5/1M traces | AWS users |

**Recommendation**: Jaeger (open-source standard) or Datadog APM (all-in-one)

---

## 8. DevOps & CI/CD

### CI/CD Platforms

| **Platform** | **Strength** | **Integration** | **Cost** | **Companies** |
|--------------|--------------|-----------------|----------|---------------|
| **GitHub Actions** | GitHub integration, marketplace | Excellent | Free (public), $0.008/min (private) | Microsoft, Shopify |
| **GitLab CI/CD** | Built-in, powerful, self-hosted option | Excellent | Free tier, $19/user/mo | Ticketmaster, Sony |
| **Jenkins** | Highly customizable, plugins | Good | Free (self-hosted) | Netflix, LinkedIn |
| **CircleCI** | Fast, Docker-native | Excellent | Free tier, $15-60/mo | Spotify, Coinbase |
| **AWS CodePipeline** | AWS-native, ECS/Lambda integration | Good (AWS) | $1/pipeline/mo | AWS-heavy shops |

**Recommendation**: GitHub Actions (simplicity) or GitLab CI/CD (feature-rich)

### Example: GitHub Actions Pipeline

```yaml
name: Notification Service CI/CD

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
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      
      - name: Install dependencies
        run: npm ci
      
      - name: Run tests
        run: npm test
      
      - name: Code coverage
        run: npm run coverage
      
      - name: Upload to Codecov
        uses: codecov/codecov-action@v3

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      
      - name: Build Docker image
        run: |
          docker build -t notif-service:${{ github.sha }} .
      
      - name: Push to ECR
        env:
          AWS_REGION: us-east-1
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_REGISTRY
          docker tag notif-service:${{ github.sha }} $ECR_REGISTRY/notif-service:${{ github.sha }}
          docker push $ECR_REGISTRY/notif-service:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    if: github.ref == 'refs/heads/main'
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/email-worker \
            worker=$ECR_REGISTRY/notif-service:${{ github.sha }} \
            --record
```

---

## 9. Security & Compliance

### Secrets Management

| **Tool** | **Encryption** | **Audit** | **Cost** | **Companies** |
|----------|----------------|-----------|----------|---------------|
| **HashiCorp Vault** | AES-256 | Excellent | Free (self-hosted) | Adobe, Barclays, Snowflake |
| **AWS Secrets Manager** | AWS KMS | Good | $0.40/secret/mo | AWS users |
| **AWS Parameter Store** | AWS KMS | Basic | Free (standard), $0.05/param (advanced) | AWS users |
| **Google Secret Manager** | Google KMS | Good | $0.06/secret/mo | GCP users |

**Recommendation**: Vault (multi-cloud) or AWS Secrets Manager (AWS-only)

### Compliance Tools

| **Requirement** | **Tool** | **Purpose** |
|-----------------|----------|-------------|
| **GDPR** | OneTrust, TrustArc | Data privacy, consent management |
| **SOC 2** | Vanta, Drata | Automated compliance, continuous monitoring |
| **PCI-DSS** | SecurityScorecard | Payment card data security |
| **HIPAA** | Aptible, Datica | Healthcare data compliance |

---

## 10. Cost Comparison

### Monthly Infrastructure Cost Estimate (200M notifications/day)

| **Component** | **Technology** | **Configuration** | **Monthly Cost** |
|---------------|----------------|-------------------|------------------|
| **Message Queue** | Kafka (AWS MSK) | 3 brokers (kafka.m5.large) | $600 |
| **Database** | PostgreSQL (RDS) | db.r5.xlarge (primary) + 2 replicas | $1,200 |
| **Analytics DB** | ClickHouse (self-hosted) | 3x c5.2xlarge EC2 | $800 |
| **Cache** | Redis (ElastiCache) | 3-node cluster (cache.r5.large) | $450 |
| **Workers** | Kubernetes (EKS) | 30x t3.medium nodes | $1,300 |
| **Load Balancer** | AWS ALB | 2 load balancers | $40 |
| **Object Storage** | S3 | 15 TB storage + requests | $350 |
| **Monitoring** | Prometheus + Grafana | Self-hosted on t3.small | $30 |
| **CI/CD** | GitHub Actions | 5000 build minutes/mo | $50 |
| **Secrets** | AWS Secrets Manager | 50 secrets | $20 |
| **Total Infrastructure** | | | **$4,840/mo** |
| | | | |
| **Channel Costs** | | | |
| Push (120M/day) | FCM/APNS | Free | $0 |
| Email (60M/day) | SendGrid | $0.001/email | $1,800 |
| SMS (20M/day) | Twilio | $0.0075/SMS | $4,500 |
| **Total Channel Costs** | | | **$6,300/mo** |
| | | | |
| **GRAND TOTAL** | | | **$11,140/mo** |

### Cost Optimization Strategies

1. **Use Spot Instances**: Save 70% on worker nodes (for non-critical workloads)
2. **Reserved Instances**: Save 30-50% on database, cache (commit to 1-3 years)
3. **S3 Lifecycle Policies**: Move old logs to Glacier → save 90%
4. **Right-size instances**: Monitor CPU/memory, downsize underutilized resources
5. **Channel optimization**: Shift marketing from SMS → email → push (save thousands)

---

## 📊 Technology Decision Matrix

### Quick Selection Guide

| **Requirement** | **Recommended Stack** | **Alternative** |
|-----------------|----------------------|-----------------|
| **High throughput (>1M msg/day)** | Kafka + Go workers | SQS + Lambda |
| **Low latency (<100ms)** | Redis + Node.js + RabbitMQ | Kafka + Go |
| **Serverless/low ops** | SQS + Lambda + DynamoDB | Cloud Run + Pub/Sub |
| **Tight budget** | PostgreSQL + Redis + RabbitMQ (self-hosted) | Managed services |
| **AWS-only** | SQS + SNS + Lambda + DynamoDB + SES | ECS + Kafka + RDS |
| **Multi-cloud** | Kubernetes + Kafka + PostgreSQL + Terraform | Nomad + Consul |
| **Startup MVP** | Firebase + SendGrid + Render | Supabase + Vercel |

---

## 🎯 Production Checklist

Before going live with your notification service:

- [ ] **Message Queue**: Kafka cluster with 3+ brokers, replication factor 3
- [ ] **Database**: Primary + 2 read replicas, automated backups, point-in-time recovery
- [ ] **Cache**: Redis cluster with 3 masters + 3 replicas
- [ ] **Workers**: Auto-scaling (min 5, max 100), health checks every 30s
- [ ] **Monitoring**: Prometheus + Grafana dashboards, PagerDuty alerts
- [ ] **Logging**: Centralized logging (ELK or Loki), 30-day retention
- [ ] **Secrets**: All API keys in Vault/Secrets Manager, rotated quarterly
- [ ] **CI/CD**: Automated tests, canary deployments, rollback capability
- [ ] **Security**: WAF, DDoS protection, TLS 1.3, security scans
- [ ] **Compliance**: GDPR data retention policies, user consent tracking
- [ ] **Cost Alerts**: CloudWatch/Datadog budget alerts at 80% threshold
- [ ] **Disaster Recovery**: Multi-AZ deployment, backup region standby

---

## 📚 Further Resources

- **Architecture Blogs**: Uber Engineering, Netflix TechBlog, Airbnb Engineering
- **Documentation**: Kafka Docs, Redis Docs, PostgreSQL Docs, Kubernetes Docs
- **Books**: "Designing Data-Intensive Applications", "Site Reliability Engineering"
- **Courses**: AWS Solutions Architect, Kubernetes Certified Administrator

---

**Last Updated**: November 2025  
**Maintained by**: System Design Team

---

*This tech stack represents industry best practices as of 2025. Always evaluate based on your specific requirements, team expertise, and budget constraints.*
