# Unique Active Users Analytics - Complete Tech Stack & DevOps Guide

## Industry-Standard Technology Stack

This document provides a comprehensive overview of the technology stack, DevOps tools, and infrastructure solutions used in production-grade analytics systems like those at Facebook, Google Analytics, Mixpanel, and Amplitude.

---

## 📊 Table of Contents

1. [Data Ingestion Layer](#data-ingestion-layer)
2. [Message Queue & Streaming](#message-queue--streaming)
3. [Stream Processing](#stream-processing)
4. [Batch Processing](#batch-processing)
5. [Storage Solutions](#storage-solutions)
6. [Query & Serving Layer](#query--serving-layer)
7. [Caching & Performance](#caching--performance)
8. [Orchestration & Scheduling](#orchestration--scheduling)
9. [Monitoring & Observability](#monitoring--observability)
10. [Infrastructure & DevOps](#infrastructure--devops)
11. [Security & Compliance](#security--compliance)
12. [Cost Optimization](#cost-optimization)

---

## 1. Data Ingestion Layer

### API Gateway

| **Tool** | **Use Case** | **Pros** | **Cons** | **Industry Usage** |
|----------|-------------|----------|----------|-------------------|
| **Kong** | API Gateway, Rate Limiting, Auth | Plugin ecosystem, High performance | Complex setup | Uber, Netflix |
| **AWS API Gateway** | Serverless API management | Fully managed, Auto-scaling | Vendor lock-in | Startups, AWS shops |
| **NGINX** | Reverse proxy, Load balancing | Lightweight, Fast | Limited features | LinkedIn, Dropbox |
| **Envoy** | Service mesh, Modern L7 proxy | Advanced routing, gRPC support | Steep learning curve | Lyft, Google |

**Recommended**: **Kong** for flexibility, **AWS API Gateway** for simplicity

### Event Tracking SDKs

```javascript
// JavaScript SDK Example (Segment-style)
analytics.track('Page Viewed', {
  page: '/dashboard',
  referrer: 'google.com',
  user_id: 'user_123',
  timestamp: Date.now()
});
```

**Production SDKs**:
- **Segment**: Multi-platform SDK, 300+ integrations
- **Amplitude SDK**: Mobile-first, automatic event tracking
- **Snowplow**: Open-source, full data ownership
- **Custom SDK**: Using Protobuf for schema validation

---

## 2. Message Queue & Streaming

### Apache Kafka (RECOMMENDED)

**Version**: 3.6+
**Configuration**:
```properties
# High-throughput configuration
num.partitions=100
replication.factor=3
min.insync.replicas=2
compression.type=lz4
log.retention.hours=168  # 7 days
log.segment.bytes=1073741824  # 1GB

# Performance tuning
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
```

**Monitoring**:
- **Kafka Manager** (LinkedIn): Cluster management
- **Confluent Control Center**: Enterprise monitoring
- **Burrow**: Consumer lag monitoring
- **Kafka Exporter**: Prometheus metrics

**Alternatives**:

| **Tool** | **Throughput** | **Latency** | **Use Case** |
|----------|---------------|------------|--------------|
| **Apache Pulsar** | Very High | Low | Multi-tenancy, geo-replication |
| **AWS Kinesis** | High | Medium | AWS-native, managed |
| **RabbitMQ** | Medium | Low | Traditional messaging, complex routing |
| **Redis Streams** | High | Very Low | Lightweight, in-memory |

**When to use what**:
- **Kafka**: High-throughput analytics (100K+ msg/sec)
- **Pulsar**: Multi-datacenter replication
- **Kinesis**: AWS-only deployments
- **RabbitMQ**: Complex routing logic

---

## 3. Stream Processing

### Apache Flink (RECOMMENDED for Real-time Analytics)

**Version**: 1.18+
**Why Flink?**
- True event-time processing (vs processing-time in Spark Streaming)
- Low latency (<1 second)
- Exactly-once semantics
- Rich windowing support

**Deployment**:
```yaml
# Flink on Kubernetes
apiVersion: flink.apache.org/v1beta1
kind: FlinkDeployment
metadata:
  name: dau-calculator
spec:
  image: flink:1.18-scala_2.12
  flinkVersion: v1_18
  jobManager:
    resource:
      memory: "4096m"
      cpu: 2
  taskManager:
    resource:
      memory: "8192m"
      cpu: 4
    replicas: 20  # 20 task managers
```

**Key Features for Analytics**:
- **HyperLogLog Support**: Via `stream-lib` or custom aggregator
- **Stateful Processing**: Managed state with RocksDB backend
- **Checkpointing**: Fault tolerance with S3/HDFS
- **Watermarks**: Handle late-arriving events

**Alternatives**:

| **Tool** | **Latency** | **Complexity** | **Best For** |
|----------|------------|----------------|--------------|
| **Apache Spark Streaming** | 1-10s | Medium | Batch + streaming hybrid |
| **Kafka Streams** | <100ms | Low | Kafka-native apps |
| **Apache Storm** | <100ms | High | Legacy systems |
| **AWS Kinesis Analytics** | 1-5s | Low | Managed SQL streaming |

---

## 4. Batch Processing

### Apache Spark (RECOMMENDED)

**Version**: 3.5+
**Use Cases**:
- Daily aggregations (DAU rollups)
- Cohort retention analysis
- Historical backfills
- Complex analytics (funnel analysis)

**Deployment on AWS EMR**:
```bash
aws emr create-cluster \
  --name "Analytics Batch Jobs" \
  --release-label emr-7.0.0 \
  --applications Name=Spark Name=Hadoop \
  --instance-type m5.4xlarge \
  --instance-count 20 \
  --use-default-roles \
  --ec2-attributes KeyName=my-key \
  --configurations file://spark-config.json \
  --auto-scaling-role EMR_AutoScaling_DefaultRole
```

**spark-config.json**:
```json
[
  {
    "Classification": "spark-defaults",
    "Properties": {
      "spark.executor.memory": "24G",
      "spark.executor.cores": "4",
      "spark.driver.memory": "8G",
      "spark.sql.adaptive.enabled": "true",
      "spark.sql.adaptive.coalescePartitions.enabled": "true",
      "spark.serializer": "org.apache.spark.serializer.KryoSerializer"
    }
  }
]
```

**Alternatives**:
- **Apache Beam**: Multi-engine support (Flink, Spark, Dataflow)
- **dbt**: SQL-first transformations (ELT pattern)
- **AWS Glue**: Serverless ETL (Spark under the hood)
- **Google Dataflow**: Managed Beam execution

---

## 5. Storage Solutions

### 5.1 OLAP Database

#### ClickHouse (RECOMMENDED for Analytics)

**Why ClickHouse?**
- 100× faster than traditional RDBMS for analytics
- Columnar storage with LZ4 compression (5:1 ratio)
- Excellent for pre-aggregations
- SQL interface (familiar to analysts)

**Deployment**:
```yaml
# ClickHouse on Kubernetes (using Altinity Operator)
apiVersion: "clickhouse.altinity.com/v1"
kind: "ClickHouseInstallation"
metadata:
  name: "analytics-cluster"
spec:
  configuration:
    clusters:
      - name: "cluster"
        layout:
          shardsCount: 5
          replicasCount: 2
    zookeeper:
      nodes:
        - host: zk-0.zk-headless
        - host: zk-1.zk-headless
        - host: zk-2.zk-headless
  templates:
    podTemplates:
      - name: clickhouse
        spec:
          containers:
            - name: clickhouse
              image: clickhouse/clickhouse-server:23.8
              resources:
                requests:
                  memory: "32Gi"
                  cpu: "8"
                limits:
                  memory: "64Gi"
                  cpu: "16"
```

**Schema Best Practices**:
```sql
-- Use AggregatingMergeTree for pre-aggregations
CREATE TABLE dau_distributed AS dau
ENGINE = Distributed('cluster', 'default', 'dau', rand());

-- Partition by month for efficient pruning
PARTITION BY toYYYYMM(date)

-- Order by most frequent query dimensions
ORDER BY (date, country, platform)

-- Use LZ4 compression (default)
SETTINGS index_granularity = 8192;
```

**Alternatives**:

| **Database** | **Speed** | **Scalability** | **SQL Support** | **Best For** |
|--------------|-----------|-----------------|-----------------|--------------|
| **Apache Druid** | Very Fast | Excellent | Limited | Sub-second queries, high concurrency |
| **Apache Pinot** | Very Fast | Excellent | Good | Real-time OLAP, user-facing analytics |
| **TimescaleDB** | Fast | Good | Full PostgreSQL | Time-series + relational hybrid |
| **BigQuery** | Fast | Excellent | Full | Serverless, pay-per-query |
| **Snowflake** | Fast | Excellent | Full | Data warehousing, multi-cloud |

**When to use what**:
- **ClickHouse**: Self-hosted, cost-conscious, high performance
- **Druid**: Sub-second queries, high concurrency dashboards
- **BigQuery**: Serverless, ad-hoc analysis, GCP ecosystem
- **Snowflake**: Enterprise data warehouse, complex joins

### 5.2 Data Lake

#### Amazon S3 / MinIO (RECOMMENDED)

**Purpose**: Long-term storage of raw events (Parquet format)

**S3 Bucket Structure**:
```
s3://analytics-datalake/
├── raw/events/
│   └── year=2025/month=11/day=15/hour=14/
│       ├── part-00001.parquet
│       └── part-00002.parquet
├── aggregated/daily/
│   └── year=2025/month=11/
│       └── dau_2025_11_15.parquet
└── ml/features/
    └── user_features_2025_11.parquet
```

**Lifecycle Policy**:
```json
{
  "Rules": [
    {
      "Id": "MoveToIA",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 90,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 365,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

**Alternatives**:
- **Azure Blob Storage**: Azure ecosystem
- **Google Cloud Storage**: GCP ecosystem
- **HDFS**: On-premise Hadoop clusters
- **MinIO**: Self-hosted S3-compatible (Kubernetes-native)

---

## 6. Query & Serving Layer

### GraphQL API (RECOMMENDED)

**Why GraphQL?**
- Flexible queries (clients request exactly what they need)
- Type safety (schema validation)
- Single endpoint (vs multiple REST endpoints)
- Batching & caching (DataLoader)

**Stack**:
```javascript
// Node.js GraphQL Server
import { ApolloServer } from '@apollo/server';
import { Redis } from 'ioredis';
import { ClickHouse } from 'clickhouse';

const typeDefs = `
  type Query {
    analytics(
      metrics: [Metric!]!
      dimensions: [Dimension!]!
      filters: FilterInput!
      granularity: Granularity!
    ): [AnalyticsRow!]!
  }

  enum Metric { DAU, WAU, MAU, RETENTION }
  enum Dimension { COUNTRY, PLATFORM, DEVICE }
  enum Granularity { HOUR, DAY, WEEK, MONTH }
`;

const resolvers = {
  Query: {
    analytics: async (_, args, { dataSources }) => {
      // Check Redis cache
      const cacheKey = hashQuery(args);
      const cached = await redis.get(cacheKey);
      if (cached) return JSON.parse(cached);

      // Query ClickHouse
      const result = await clickhouse.query(buildSQL(args));

      // Cache for 5 minutes
      await redis.setex(cacheKey, 300, JSON.stringify(result));

      return result;
    }
  }
};
```

**Alternatives**:
- **REST API**: Simpler, more widely known
- **gRPC**: Higher performance, binary protocol
- **Presto/Trino**: SQL interface to data lake

---

## 7. Caching & Performance

### Redis (RECOMMENDED)

**Use Cases**:
1. **Real-time DAU (HyperLogLog)**:
```bash
PFADD active:users:2025-11-15 "user_123"
PFCOUNT active:users:2025-11-15  # ~100M
```

2. **Query Result Cache**:
```bash
SET query:abc123 '{"dau": 100000000}' EX 300  # 5 min TTL
```

3. **Deduplication**:
```bash
SET evt:abc123 1 EX 3600  # 1 hour TTL
```

**Deployment (Redis Cluster)**:
```yaml
# Redis Cluster on Kubernetes
apiVersion: redis.redis.opstreelabs.in/v1beta1
kind: RedisCluster
metadata:
  name: analytics-cache
spec:
  clusterSize: 6
  redisExporter:
    enabled: true
  storage:
    volumeClaimTemplate:
      spec:
        accessModes: ["ReadWriteOnce"]
        resources:
          requests:
            storage: 100Gi
```

**Alternatives**:
- **Memcached**: Simpler, multi-threaded (but no HyperLogLog)
- **DragonflyDB**: Modern Redis replacement (faster, multi-threaded)
- **Apache Ignite**: Distributed cache + compute

---

## 8. Orchestration & Scheduling

### Apache Airflow (RECOMMENDED)

**Use Cases**:
- Schedule daily batch jobs
- Backfill historical data
- Monitor data quality
- Coordinate multi-step workflows

**DAG Example**:
```python
from airflow import DAG
from airflow.operators.python import PythonOperator
from datetime import datetime, timedelta

default_args = {
    'owner': 'analytics',
    'retries': 3,
    'retry_delay': timedelta(minutes=5),
}

with DAG(
    'daily_dau_aggregation',
    default_args=default_args,
    schedule_interval='@daily',
    start_date=datetime(2025, 1, 1),
    catchup=False
) as dag:

    extract_events = PythonOperator(
        task_id='extract_events_from_s3',
        python_callable=extract_from_s3,
    )

    aggregate_dau = PythonOperator(
        task_id='aggregate_dau',
        python_callable=run_spark_job,
    )

    load_to_clickhouse = PythonOperator(
        task_id='load_to_clickhouse',
        python_callable=load_data,
    )

    data_quality_check = PythonOperator(
        task_id='data_quality_check',
        python_callable=validate_counts,
    )

    extract_events >> aggregate_dau >> load_to_clickhouse >> data_quality_check
```

**Alternatives**:
- **Prefect**: Modern, Pythonic, better UI
- **Dagster**: Data-aware, asset-based
- **AWS Step Functions**: Serverless orchestration
- **Argo Workflows**: Kubernetes-native

---

## 9. Monitoring & Observability

### 9.1 Metrics (Prometheus + Grafana)

**Prometheus Exporters**:
- **Kafka Exporter**: Lag, throughput, partition metrics
- **ClickHouse Exporter**: Query performance, disk usage
- **Redis Exporter**: Hit rate, memory usage
- **Node Exporter**: CPU, memory, disk

**Key Metrics to Track**:
```yaml
# Prometheus recording rules
groups:
  - name: analytics_metrics
    interval: 30s
    rules:
      - record: kafka:lag:sum
        expr: sum(kafka_consumergroup_lag) by (consumergroup)

      - record: clickhouse:query:p95
        expr: histogram_quantile(0.95,
          sum(rate(clickhouse_query_duration_seconds_bucket[5m])) by (le))

      - record: redis:hit_rate
        expr: rate(redis_keyspace_hits_total[5m]) /
          (rate(redis_keyspace_hits_total[5m]) +
           rate(redis_keyspace_misses_total[5m]))
```

**Grafana Dashboards**:
1. **Executive Dashboard**: DAU/MAU trends, growth rate
2. **Engineering Dashboard**: System health, latency, errors
3. **Data Quality Dashboard**: Missing data, anomalies

### 9.2 Logging (ELK Stack)

**Components**:
- **Filebeat**: Log shipping
- **Logstash**: Log parsing & enrichment
- **Elasticsearch**: Log storage & search
- **Kibana**: Log visualization

**Structured Logging**:
```json
{
  "timestamp": "2025-11-15T10:30:00Z",
  "level": "INFO",
  "service": "ingestion-service",
  "message": "Event processed",
  "user_id": "user_123",
  "event_id": "evt_abc",
  "processing_time_ms": 15,
  "trace_id": "trace_xyz"
}
```

**Alternatives**:
- **Grafana Loki**: Lightweight, cost-effective
- **Splunk**: Enterprise logging (expensive)
- **Datadog**: All-in-one observability (SaaS)

### 9.3 Distributed Tracing (Jaeger)

**Trace Example**:
```
Trace: event_ingestion (150ms)
├─ API Gateway (10ms)
├─ Ingestion Service (30ms)
│  ├─ Schema Validation (5ms)
│  ├─ GeoIP Lookup (10ms)
│  └─ Redis Dedup (5ms)
├─ Kafka Write (20ms)
└─ Flink Processing (90ms)
   ├─ Deserialization (10ms)
   ├─ Windowing (20ms)
   ├─ HLL Aggregation (30ms)
   └─ ClickHouse Sink (30ms)
```

**Alternatives**:
- **Zipkin**: Simpler, open-source
- **AWS X-Ray**: AWS-native tracing
- **OpenTelemetry**: Vendor-neutral standard

---

## 10. Infrastructure & DevOps

### 10.1 Container Orchestration

#### Kubernetes (RECOMMENDED)

**Why Kubernetes?**
- Auto-scaling based on load (HPA, VPA)
- Self-healing (pod restarts)
- Service discovery & load balancing
- Multi-cloud portability

**Namespaces**:
```bash
# Separate environments
kubectl create namespace analytics-prod
kubectl create namespace analytics-staging
kubectl create namespace analytics-dev
```

**Key Resources**:
```yaml
# HorizontalPodAutoscaler for Ingestion Service
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: ingestion-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: ingestion-service
  minReplicas: 10
  maxReplicas: 100
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
          name: kafka_lag
        target:
          type: AverageValue
          averageValue: "10000"
```

**Alternatives**:
- **AWS ECS/Fargate**: Simpler, AWS-native
- **Docker Swarm**: Lightweight (not recommended for prod)
- **Nomad**: HashiCorp's orchestrator

### 10.2 Infrastructure as Code

#### Terraform (RECOMMENDED)

**Example**: Provision ClickHouse cluster on AWS
```hcl
module "clickhouse_cluster" {
  source = "./modules/clickhouse"

  cluster_name    = "analytics-prod"
  instance_type   = "r5.4xlarge"
  instance_count  = 10
  disk_size_gb    = 2000
  vpc_id          = aws_vpc.main.id
  subnet_ids      = aws_subnet.private[*].id

  tags = {
    Environment = "production"
    Team        = "analytics"
  }
}
```

**Alternatives**:
- **Pulumi**: Multi-language IaC (TypeScript, Python, Go)
- **AWS CloudFormation**: AWS-native (YAML/JSON)
- **Ansible**: Configuration management + provisioning

### 10.3 CI/CD

#### GitHub Actions (RECOMMENDED for simplicity)

**Pipeline Example**:
```yaml
name: Deploy Analytics Pipeline

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
          pytest tests/
      - name: Run integration tests
        run: |
          docker-compose up -d
          pytest integration_tests/

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Build Docker images
        run: |
          docker build -t analytics/ingestion:${{ github.sha }} .
      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin
          docker push analytics/ingestion:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/ingestion-service \
            ingestion=analytics/ingestion:${{ github.sha }} \
            -n analytics-prod
          kubectl rollout status deployment/ingestion-service
```

**Alternatives**:
- **GitLab CI**: Full DevOps platform
- **Jenkins**: Enterprise standard (older)
- **CircleCI**: Fast, cloud-native
- **Argo CD**: GitOps for Kubernetes

---

## 11. Security & Compliance

### 11.1 Authentication & Authorization

**API Authentication**:
- **API Keys**: Simple, rate-limited per key
- **JWT Tokens**: Stateless, scalable
- **OAuth 2.0**: Delegated authorization

**Example (Kong + JWT)**:
```bash
# Enable JWT plugin
curl -X POST http://kong:8001/services/analytics-api/plugins \
  --data "name=jwt"

# Create consumer
curl -X POST http://kong:8001/consumers \
  --data "username=dashboard_app"

# Issue JWT credential
curl -X POST http://kong:8001/consumers/dashboard_app/jwt
```

### 11.2 Data Privacy (GDPR)

**User Deletion**:
```python
def delete_user_data(user_id):
    # 1. Mark user as deleted
    db.execute("UPDATE users SET deleted_at = NOW() WHERE user_id = ?", user_id)

    # 2. Stop collecting events
    cache.set(f"deleted_user:{user_id}", 1, ttl=None)

    # 3. Anonymize in S3 data lake (batch job)
    spark.sql(f"""
        UPDATE events_parquet
        SET user_id = 'DELETED_{hash(user_id)}'
        WHERE user_id = '{user_id}'
    """)

    # 4. Aggregates don't need deletion (no PII)
    # DAU/MAU counts remain unchanged
```

**Data Retention Policies**:
```yaml
# Airflow DAG for automatic deletion
retention_policies:
  - dataset: raw_events
    retention_days: 90
    action: delete_s3_prefix

  - dataset: aggregated_dau
    retention_days: 1825  # 5 years
    action: archive_to_glacier

  - dataset: user_pii
    retention_days: 365
    action: anonymize
```

### 11.3 Encryption

**At Rest**:
- S3: AES-256 encryption (server-side)
- ClickHouse: Disk-level encryption (LUKS)
- Kafka: Encrypted volumes

**In Transit**:
- TLS 1.3 for all API communication
- Kafka: SSL/SASL authentication
- ClickHouse: TLS for inter-node communication

---

## 12. Cost Optimization

### 12.1 Compute Optimization

**Strategy**:
1. **Spot Instances for Batch Jobs**: 70% savings
```bash
# EMR with spot instances
aws emr create-cluster \
  --instance-groups \
    InstanceGroupType=MASTER,InstanceType=m5.xlarge,InstanceCount=1 \
    InstanceGroupType=CORE,InstanceType=m5.4xlarge,InstanceCount=5,BidPrice=0.50 \
    InstanceGroupType=TASK,InstanceType=m5.4xlarge,InstanceCount=10,BidPrice=0.50,Market=SPOT
```

2. **Auto-scaling**: Scale down during off-peak hours
3. **Reserved Instances**: 40% savings for steady-state workloads

### 12.2 Storage Optimization

**Compression**:
- Raw JSON: 5 TB/day
- LZ4 Compressed: 1 TB/day (5:1)
- Parquet: 500 GB/day (10:1)
- **Savings**: 90% storage cost

**Tiered Storage**:
| **Tier** | **Age** | **Storage Class** | **Cost/GB/month** |
|----------|---------|-------------------|-------------------|
| Hot | 0-7 days | SSD (EBS gp3) | $0.08 |
| Warm | 7-90 days | HDD (EBS st1) | $0.045 |
| Cold | 90-365 days | S3 Standard | $0.023 |
| Archive | >365 days | S3 Glacier | $0.004 |

**Total Cost Example** (100M DAU, 5B events/day):
```
Hot (7 days): 7 TB × $0.08 = $560/month
Warm (90 days): 90 TB × $0.045 = $4,050/month
Cold (365 days): 183 TB × $0.023 = $4,209/month
Archive (5 years): 1.8 PB × $0.004 = $7,200/month
---
Total Storage: ~$16,000/month
```

---

## Complete Technology Stack Summary

### Production-Grade Stack (100M DAU)

```yaml
Data Ingestion:
  - API Gateway: Kong (self-hosted) or AWS API Gateway
  - SDKs: Segment SDK, Custom Protobuf SDK
  - Languages: Go (high throughput), Python (flexibility)

Message Queue:
  - Primary: Apache Kafka (100 partitions, 3× replication)
  - Monitoring: Confluent Control Center, Burrow

Stream Processing:
  - Real-time: Apache Flink (20 task managers)
  - State Backend: RocksDB with S3 checkpoints

Batch Processing:
  - Framework: Apache Spark on AWS EMR
  - Scheduling: Apache Airflow (20 DAGs)

Storage:
  - OLAP: ClickHouse (10-node cluster, 2× replication)
  - Data Lake: S3 (Parquet format, partitioned by date)
  - Cache: Redis Cluster (6 nodes, HyperLogLog)

Query Layer:
  - API: GraphQL (Apollo Server on Node.js)
  - Caching: Redis (5 min TTL, 70% hit rate)

Visualization:
  - Dashboards: Grafana (real-time), Tableau (ad-hoc)
  - Custom: React + D3.js

Infrastructure:
  - Orchestration: Kubernetes (EKS on AWS)
  - IaC: Terraform
  - CI/CD: GitHub Actions

Monitoring:
  - Metrics: Prometheus + Grafana
  - Logging: ELK Stack (Filebeat, Elasticsearch, Kibana)
  - Tracing: Jaeger
  - Alerting: PagerDuty

Security:
  - Auth: Kong (JWT, API Keys)
  - Encryption: TLS 1.3, AES-256
  - Compliance: GDPR automation (user deletion, anonymization)

Cost:
  - Compute: ~$30K/month (spot instances)
  - Storage: ~$16K/month (tiered)
  - Total: ~$50K/month for 100M DAU
```

---

## Getting Started Checklist

### Phase 1: MVP (1-10M DAU)
- [ ] Set up Kafka (single broker → 3-node cluster)
- [ ] Deploy Flink for real-time DAU (single job)
- [ ] Set up ClickHouse (single node → 3-node cluster)
- [ ] Implement Redis HyperLogLog for fast path
- [ ] Build GraphQL API for queries
- [ ] Deploy Grafana dashboards

### Phase 2: Scale (10-100M DAU)
- [ ] Scale Kafka to 100 partitions
- [ ] Add Flink parallelism (10-20 task managers)
- [ ] Shard ClickHouse (5-10 nodes)
- [ ] Implement batch processing (Spark on EMR)
- [ ] Add Airflow for orchestration
- [ ] Set up comprehensive monitoring (Prometheus, Jaeger)

### Phase 3: Optimize (100M+ DAU)
- [ ] Implement Lambda architecture (speed + batch)
- [ ] Multi-region deployment
- [ ] Advanced caching strategies
- [ ] Cost optimization (spot instances, tiered storage)
- [ ] Data quality monitoring
- [ ] SLA-based alerting

---

## Industry Comparisons

### Facebook/Meta
- **Scale**: 3B DAU
- **Stack**: Custom (Scuba for real-time, Presto for batch)
- **Storage**: Custom columnar format

### Google Analytics
- **Scale**: 30M websites
- **Stack**: BigQuery, Dataflow
- **Storage**: Dremel (columnar)

### Amplitude
- **Scale**: 10B events/day
- **Stack**: ClickHouse, Kafka, Flink
- **Storage**: S3 + ClickHouse

### Mixpanel
- **Scale**: 50B events/day
- **Stack**: Custom (formerly ClickHouse)
- **Storage**: Proprietary

---

## Conclusion

This tech stack is battle-tested at scale and represents industry best practices for analytics systems. Start simple (single-node deployments) and scale horizontally as your DAU grows.

**Key Principles**:
1. **Pre-aggregate everything** (30,000× storage savings)
2. **Use HyperLogLog for counting** (99.9% memory savings)
3. **Lambda architecture for best of both worlds** (real-time + historical)
4. **Monitor obsessively** (what you can't measure, you can't improve)
5. **Optimize for cost** (70% savings with spot instances and compression)

**Next Steps**:
- Read vendor documentation for chosen tools
- Set up POC with sample data
- Benchmark with your expected load
- Iterate and optimize based on metrics
