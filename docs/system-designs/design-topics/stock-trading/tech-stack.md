# Stock Trading System: Industry Tech Stack & DevOps Solutions

## 🏗️ Production Tech Stack Used by Leading Trading Platforms

This document provides real-world technology choices used by Robinhood, Interactive Brokers, Coinbase, and other major trading platforms.

---

## 1. Programming Languages

### Core Services

| **Component** | **Language** | **Why** | **Used By** |
|--------------|--------------|---------|-------------|
| **Matching Engine** | C++, Rust | Ultra-low latency (<100μs), zero-cost abstractions, manual memory management | Interactive Brokers, Nasdaq, CME Group |
| **Order Management** | Java (Spring Boot), Go | Strong typing, ACID support, mature ecosystem, excellent concurrency | Robinhood, E*TRADE, TD Ameritrade |
| **API Gateway** | Go, Node.js | High concurrency, low memory footprint, async I/O | Coinbase (Go), Robinhood (Python/Go) |
| **Market Data Feed** | C++, Java | High throughput, low latency, FIX protocol support | Bloomberg, Reuters, NYSE |
| **Web Frontend** | TypeScript + React | Type safety, component reusability, rich ecosystem | Robinhood, Coinbase, Webull |
| **Mobile Apps** | Swift (iOS), Kotlin (Android), React Native | Native performance, platform-specific features | Most modern brokerages |
| **Analytics/ML** | Python (NumPy, Pandas) | Data science libraries, rapid prototyping | All platforms (risk models, fraud detection) |

### Specific Choices by Company

**Robinhood**:
- Backend: Python (Django), Go (microservices)
- Frontend: React (web), Swift/Kotlin (mobile)
- Data Pipeline: Python, Spark

**Interactive Brokers**:
- Matching Engine: C++
- Trading Platform: Java
- Mobile: Native (Swift/Kotlin)

**Coinbase**:
- Backend: Go, Ruby (legacy), Node.js
- Frontend: React
- Mobile: React Native

---

## 2. Databases

### Transactional Databases (ACID-compliant)

| **Database** | **Use Case** | **Why** | **Scale** |
|--------------|-------------|---------|-----------|
| **PostgreSQL** | Orders, Accounts, Transactions | SERIALIZABLE isolation, mature, ACID compliant, excellent query optimizer | <100K QPS writes |
| **CockroachDB** | Distributed ACID transactions | PostgreSQL-compatible, multi-region, auto-sharding, strong consistency | 100K+ QPS, global |
| **MySQL (InnoDB)** | User data, KYC information | Widely adopted, ACID compliant, good tooling | <50K QPS writes |
| **Oracle Database** | Legacy enterprise systems | Enterprise support, RAC clustering, high availability | Large institutions |
| **Google Spanner** | Global consistency | True global ACID, TrueTime API, synchronous replication | Google-scale fintech |

**Industry Usage**:
- **Robinhood**: PostgreSQL (orders), DynamoDB (market data cache)
- **Coinbase**: PostgreSQL (primary), MongoDB (analytics)
- **Interactive Brokers**: Oracle, SQL Server (legacy), PostgreSQL (modern)

### Time-Series Databases

| **Database** | **Use Case** | **Why** |
|--------------|-------------|---------|
| **TimescaleDB** | Market data (OHLCV), price history | PostgreSQL extension, SQL queries, compression | 
| **InfluxDB** | Metrics, monitoring | High write throughput, retention policies |
| **ClickHouse** | Analytics, reporting | Columnar storage, fast aggregations, SQL support |
| **Cassandra** | High-volume time-series | Distributed, high write throughput (eventual consistency - use carefully!) |

**Industry Usage**:
- **Trading Platforms**: TimescaleDB for market data, ClickHouse for analytics
- **Monitoring**: InfluxDB + Grafana for system metrics

### Caching

| **Technology** | **Use Case** | **Why** |
|----------------|-------------|---------|
| **Redis Enterprise** | Account balances, sessions, real-time quotes | In-memory, sub-ms latency, persistence options, clustering |
| **Memcached** | Simple key-value caching | Extremely fast, simple, no persistence |
| **Hazelcast** | Distributed caching | Java-native, in-memory data grid, distributed computing |

**Industry Usage**:
- **Robinhood**: Redis (sessions, quotes)
- **Coinbase**: Redis Cluster (price cache)

---

## 3. Message Queues & Event Streaming

| **Technology** | **Use Case** | **Why** | **Industry Usage** |
|----------------|-------------|---------|-------------------|
| **Apache Kafka** | Event streaming, order flow, audit trail | Durability, exactly-once semantics, high throughput, log compaction | Robinhood, Coinbase, most fintech |
| **RabbitMQ** | Task queues, notifications | AMQP protocol, flexible routing, good for RPC | Smaller platforms |
| **Amazon SQS** | Async processing, decoupling services | Managed service, auto-scaling, AWS integration | Cloud-native platforms |
| **NATS** | Low-latency messaging | Extremely fast, simple, good for microservices | High-frequency trading firms |
| **Pulsar** | Multi-tenancy, geo-replication | Segment storage, tiered architecture | Yahoo (original developer) |

**Kafka Configuration for Trading**:
```yaml
# Production Kafka config for financial systems
min.insync.replicas: 2  # Require 2 replicas ACK before success
acks: all               # Wait for all in-sync replicas
enable.idempotence: true  # Exactly-once semantics
max.in.flight.requests.per.connection: 1  # Ordering guarantee
compression.type: lz4   # Fast compression
log.retention.hours: 168  # 7 days (regulatory)
```

**Industry Usage**:
- **Robinhood**: Kafka (order events, analytics pipeline)
- **Coinbase**: Kafka (trade execution, price updates)

---

## 4. Networking & API

### API Gateway / Load Balancer

| **Technology** | **Use Case** | **Why** |
|----------------|-------------|---------|
| **Kong** | API Gateway | Open-source, plugin ecosystem, rate limiting, auth |
| **NGINX** | Load balancer, reverse proxy | High performance, battle-tested, simple config |
| **HAProxy** | L4/L7 load balancing | Excellent for TCP (FIX protocol), health checks |
| **AWS ALB/NLB** | Managed load balancing | Auto-scaling, integrated with AWS, SSL offload |
| **Envoy** | Service mesh | Observability, modern protocols (gRPC, HTTP/2) |

### WebSocket Servers

| **Technology** | **Use Case** | **Why** |
|----------------|-------------|---------|
| **Socket.IO (Node.js)** | Real-time market data | Easy to use, fallback mechanisms |
| **Gorilla WebSocket (Go)** | High-performance WebSocket | Low latency, efficient, simple API |
| **Netty (Java)** | Enterprise WebSocket | Non-blocking I/O, used in financial systems |
| **AWS API Gateway WebSocket** | Managed WebSocket | Serverless, auto-scaling |

**Industry Usage**:
- **Robinhood**: Custom Go WebSocket server
- **Coinbase**: Go-based WebSocket API

### Communication Protocols

- **REST API**: Standard HTTP/JSON for most operations
- **WebSocket**: Real-time market data, order updates
- **gRPC**: Internal microservice communication (low latency)
- **FIX Protocol**: Industry standard for institutional trading (Financial Information eXchange)

---

## 5. Cloud Infrastructure

### Cloud Providers

| **Provider** | **Strengths** | **Used By** |
|--------------|--------------|-------------|
| **AWS** | Comprehensive services, financial compliance (PCI DSS, SOC 2) | Robinhood, Coinbase, most fintech |
| **Google Cloud** | BigQuery (analytics), Spanner (global ACID) | Fintechs with global needs |
| **Azure** | Enterprise integration, .NET ecosystem | Legacy financial institutions |
| **Multi-Cloud** | Avoid vendor lock-in, disaster recovery | Large institutions |

### Infrastructure as Code

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **Terraform** | Multi-cloud provisioning | Declarative, state management, wide support |
| **Kubernetes** | Container orchestration | Industry standard, auto-scaling, self-healing |
| **Helm** | Kubernetes package management | Reusable charts, versioning |
| **AWS CloudFormation** | AWS-native IaC | Native integration, no additional tools |

---

## 6. Observability & Monitoring

### Metrics & Monitoring

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **Prometheus** | Metrics collection | Pull-based, time-series, PromQL, alerting |
| **Grafana** | Visualization, dashboards | Beautiful dashboards, alerting, wide data source support |
| **Datadog** | All-in-one observability | APM, logs, metrics, traces in one platform |
| **New Relic** | Application monitoring | Deep insights, AI-powered alerts |
| **CloudWatch** | AWS-native monitoring | Integrated with AWS services |

**Key Metrics for Trading Systems**:
```
# RED Method (Rate, Errors, Duration)
- Order placement rate (QPS)
- Order error rate (4xx, 5xx)
- Order latency (P50, P95, P99)

# Market Data
- WebSocket connection count
- Message publish rate
- Lag between exchange and client

# Financial
- Trade execution rate
- Failed trades (ACID rollbacks)
- Account balance accuracy checks
```

### Logging

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **ELK Stack** (Elasticsearch, Logstash, Kibana) | Centralized logging | Powerful search, visualization, alerting |
| **Splunk** | Enterprise log management | Advanced analytics, compliance reporting |
| **Fluentd** | Log aggregation | Unified logging layer, plugin ecosystem |
| **AWS CloudWatch Logs** | AWS-native logging | Integrated, simple setup |

**Regulatory Logging Requirements**:
- **7-year retention** (SEC Rule 17a-4)
- **Immutable audit trail** (WORM storage)
- **Structured logging** (JSON format for parsing)
- **PII redaction** (GDPR compliance)

### Distributed Tracing

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **Jaeger** | Distributed tracing | OpenTelemetry support, visualization |
| **Zipkin** | Trace correlation | Simple setup, proven |
| **AWS X-Ray** | AWS-native tracing | Integrated with AWS services |
| **Lightstep** | Enterprise tracing | Advanced analytics, service dependency graphs |

---

## 7. Security

### Authentication & Authorization

| **Technology** | **Use Case** | **Why** |
|----------------|-------------|---------|
| **OAuth 2.0 / OpenID Connect** | Third-party auth | Industry standard, secure delegation |
| **JWT** | Stateless auth tokens | Self-contained, scalable, no DB lookup |
| **Auth0 / Okta** | Managed auth | 2FA, SSO, compliance, reduced development time |
| **AWS Cognito** | AWS-native auth | User pools, federated identities |

### Secrets Management

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **HashiCorp Vault** | Secrets storage | Dynamic secrets, encryption as a service, audit |
| **AWS Secrets Manager** | AWS-native secrets | Automatic rotation, integrated with RDS/ECS |
| **Azure Key Vault** | Azure-native secrets | HSM-backed, compliance (FIPS 140-2) |

### Network Security

- **WAF** (Web Application Firewall): AWS WAF, Cloudflare
- **DDoS Protection**: AWS Shield, Cloudflare, Akamai
- **VPN**: WireGuard, OpenVPN for admin access
- **Zero Trust**: BeyondCorp, Cloudflare Access

---

## 8. DevOps & CI/CD

### CI/CD Pipelines

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **GitHub Actions** | CI/CD workflows | Native GitHub integration, YAML config |
| **GitLab CI/CD** | Full DevOps platform | Built-in, auto DevOps, security scanning |
| **Jenkins** | Legacy CI/CD | Highly customizable, plugin ecosystem |
| **CircleCI** | Cloud-native CI/CD | Fast, parallel builds, Docker support |
| **Argo CD** | GitOps for Kubernetes | Declarative, automated sync, rollback |

**Trading System CI/CD Pipeline**:
```yaml
# GitHub Actions example
name: Trading System CI/CD

on: [push, pull_request]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout code
        uses: actions/checkout@v3
      
      - name: Run unit tests
        run: mvn test
      
      - name: Run integration tests
        run: mvn verify -Pintegration
      
      - name: SAST (Static Analysis)
        run: sonar-scanner
      
      - name: Dependency scanning
        run: snyk test
  
  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: docker build -t trading-api:${{ github.sha }} .
      
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin
          docker push trading-api:${{ github.sha }}
  
  deploy-staging:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to staging
        run: kubectl set image deployment/trading-api trading-api=trading-api:${{ github.sha }}
      
      - name: Smoke tests
        run: ./scripts/smoke-test.sh
  
  deploy-prod:
    needs: deploy-staging
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - name: Blue-green deployment
        run: ./scripts/blue-green-deploy.sh
      
      - name: Canary deployment (10%)
        run: kubectl set image deployment/trading-api trading-api=trading-api:${{ github.sha }} --replicas=1
      
      - name: Monitor metrics (5 min)
        run: ./scripts/monitor-canary.sh
      
      - name: Full rollout
        run: kubectl scale deployment/trading-api --replicas=10
```

### Deployment Strategies

| **Strategy** | **Description** | **Use Case** |
|--------------|----------------|-------------|
| **Blue-Green** | Maintain two identical environments, switch traffic | Zero-downtime, instant rollback |
| **Canary** | Gradually route traffic to new version (10% → 50% → 100%) | Minimize blast radius, detect issues early |
| **Rolling Update** | Replace instances incrementally | Default Kubernetes strategy |
| **Feature Flags** | Toggle features without deployment | A/B testing, gradual rollout |

**Industry Standard**:
- **Staging environment**: Identical to production
- **Canary deployment**: 10% → 50% → 100% over 1 hour
- **Automated rollback**: If error rate >1% or latency >100ms

---

## 9. Data Engineering

### ETL / Data Pipelines

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **Apache Spark** | Large-scale data processing | Distributed, in-memory, SQL support |
| **Apache Airflow** | Workflow orchestration | DAGs, scheduling, monitoring |
| **dbt** | Data transformation | SQL-based, version control, testing |
| **AWS Glue** | Managed ETL | Serverless, integrated with S3/Redshift |
| **Fivetran** | Data replication | Managed connectors, incremental sync |

### Data Warehousing

| **Tool** | **Use Case** | **Why** |
|----------|-------------|---------|
| **Snowflake** | Cloud data warehouse | Separation of compute/storage, auto-scaling |
| **Amazon Redshift** | AWS-native data warehouse | Columnar storage, integrated with AWS |
| **Google BigQuery** | Serverless analytics | Petabyte-scale, SQL, pay-per-query |
| **ClickHouse** | Real-time analytics | Extremely fast, columnar, open-source |

**Industry Usage**:
- **Robinhood**: Spark + Airflow for analytics, Snowflake for data warehouse
- **Coinbase**: BigQuery for analytics

---

## 10. Testing

### Testing Frameworks

| **Type** | **Tool** | **Language** |
|----------|----------|--------------|
| **Unit Testing** | JUnit, pytest, Jest | Java, Python, JavaScript |
| **Integration Testing** | Testcontainers, WireMock | Java, Go |
| **End-to-End Testing** | Cypress, Selenium | Web browsers |
| **Load Testing** | Gatling, k6, JMeter | Performance testing |
| **Chaos Engineering** | Chaos Monkey, Gremlin | Resilience testing |

**Testing Pyramid for Trading Systems**:
```
         /\
        /  \  E2E (5%)
       /____\
      /      \  Integration (15%)
     /________\
    /          \  Unit Tests (80%)
   /__________\
```

**Key Tests for Financial Systems**:
1. **ACID Compliance Tests**: Verify transactions rollback correctly
2. **Concurrency Tests**: Race conditions, double-spending
3. **Chaos Tests**: Simulate database failures, network partitions
4. **Load Tests**: 10x peak traffic (market open surge)
5. **Regulatory Tests**: 7-year retention, audit trail completeness

---

## 11. Real-World Architecture: Robinhood (Estimated)

```
┌─────────────────────────────────────────────────────────┐
│                    ROBINHOOD STACK                      │
└─────────────────────────────────────────────────────────┘

FRONTEND:
- Web: React + TypeScript
- Mobile: Swift (iOS), Kotlin (Android)
- State Management: Redux

BACKEND:
- API: Django (Python), Go microservices
- Matching Engine: C++ (custom)
- Market Data: Go services consuming FIX feeds

DATABASE:
- Transactional: PostgreSQL (orders, accounts)
- Cache: Redis Cluster (quotes, sessions)
- Analytics: Snowflake, Redshift

INFRASTRUCTURE:
- Cloud: AWS (EC2, RDS, ElastiCache, S3)
- Orchestration: Kubernetes (EKS)
- Messaging: Kafka (order events, analytics)

OBSERVABILITY:
- Metrics: Datadog
- Logging: ELK Stack
- Tracing: AWS X-Ray

SECURITY:
- Auth: OAuth 2.0 + 2FA
- Secrets: AWS Secrets Manager
- Network: AWS WAF + Shield
```

---

## 12. Cost Estimation (Robinhood-Scale: 10M Users, 2M DAU)

| **Component** | **Service** | **Monthly Cost** |
|---------------|-------------|------------------|
| **Compute** | 100 EC2 instances (c5.4xlarge) | $50,000 |
| **Database** | PostgreSQL RDS (db.r5.8xlarge) | $15,000 |
| **Cache** | Redis Cluster (cache.r5.4xlarge × 10) | $8,000 |
| **Message Queue** | Kafka cluster (m5.2xlarge × 10) | $10,000 |
| **Storage** | S3 (100 TB), EBS | $3,000 |
| **Network** | Data transfer, NAT gateway | $5,000 |
| **Monitoring** | Datadog (100 hosts) | $2,000 |
| **CDN** | CloudFront (10 TB transfer) | $1,000 |
| **Total** | | **~$100K/month** |

**Note**: This scales roughly linearly with user growth (10x users = 10x cost).

---

## 13. Industry Best Practices

### Development Practices

- **Microservices**: Separate order, account, market data services
- **API-First**: Design APIs before implementation
- **Contract Testing**: Pact for consumer-driven contracts
- **Feature Flags**: LaunchDarkly, Unleash for gradual rollout
- **Code Reviews**: Mandatory, LGTM from 2 engineers
- **Documentation**: OpenAPI/Swagger for APIs, ADRs for architecture decisions

### Deployment Practices

- **Immutable Infrastructure**: Never modify running instances
- **Blue-Green Deployments**: Zero-downtime releases
- **Canary Releases**: Gradual rollout with monitoring
- **Automated Rollback**: If error rate >1% or latency P99 >100ms
- **Database Migrations**: Flyway, Liquibase for versioned migrations

### Operational Practices

- **On-Call Rotation**: 24/7 coverage, PagerDuty integration
- **Incident Management**: Postmortems, blameless culture
- **Disaster Recovery Drills**: Monthly failover tests
- **Capacity Planning**: Quarterly reviews, scale for 3x current load
- **SLOs/SLAs**: 99.99% uptime, <10ms latency (P95)

---

## 14. Compliance & Regulatory Tech

| **Requirement** | **Technology** | **Implementation** |
|-----------------|----------------|-------------------|
| **7-Year Retention** | S3 Glacier, WORM storage | Lifecycle policies, legal holds |
| **Audit Trail** | Immutable ledger (blockchain or append-only DB) | Every transaction logged |
| **Data Encryption** | AWS KMS, HashiCorp Vault | AES-256 at rest, TLS 1.3 in transit |
| **PCI DSS** (Payment Card) | Tokenization, PCI-compliant infrastructure | Use Stripe/Plaid for payment processing |
| **GDPR** (Privacy) | Data anonymization, right to deletion | Pseudonymization, data export APIs |
| **SOC 2** (Security) | Access controls, logging, monitoring | AWS compliance programs |

---

## Conclusion

The tech stack for a production trading system is complex, but each component serves a critical purpose:

- **C++/Rust** for matching engine (latency <100μs)
- **Java/Go** for backend services (ACID transactions)
- **PostgreSQL/CockroachDB** for ACID compliance
- **Kafka** for event streaming and audit trail
- **Redis** for caching and real-time data
- **Kubernetes** for orchestration and auto-scaling
- **Datadog/ELK** for observability and compliance

**Remember**: In financial systems, **correctness > performance**, but **both are required**.

---

*This tech stack guide is based on publicly available information, job postings, and industry best practices. Actual implementations may vary.*
