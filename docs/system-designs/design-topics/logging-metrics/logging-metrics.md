# Design a Distributed Logging & Metrics System

**Companies**: Datadog, New Relic, Splunk, Elastic, Grafana Labs
**Difficulty**: Advanced
**Time**: 60 minutes
**Pattern**: Data Processing & Analytics

## Problem Statement

Design a scalable observability platform that collects, stores, and analyzes logs and metrics from distributed systems at massive scale. The system should support real-time monitoring, historical analysis, alerting, and provide sub-second query latency.

---

## Step 1: Requirements (10 min)

### Functional Requirements

**Logging:**
1. **Ingest logs**: Accept logs from multiple sources (applications, containers, infrastructure)
2. **Search logs**: Full-text search with filters (service, time range, log level)
3. **Real-time tailing**: Stream logs in real-time (like `tail -f`)
4. **Log aggregation**: Group and aggregate logs by patterns
5. **Retention management**: Hot/warm/cold storage tiers

**Metrics:**
1. **Collect metrics**: Pull or push metrics from services
2. **Time-series storage**: Efficiently store time-series data
3. **Query metrics**: PromQL-like queries for dashboards
4. **Alerting**: Threshold-based and anomaly detection alerts
5. **Long-term storage**: Downsampling and archival

**Visualization & Alerting:**
1. **Dashboards**: Real-time and historical visualizations
2. **Alert rules**: Configurable thresholds and conditions
3. **Notification**: Multi-channel (Slack, PagerDuty, Email)

### Non-Functional Requirements

1. **High Availability**: 99.9% uptime
2. **Scalability**: Handle 1M+ events/sec
3. **Low Latency**: <500ms query response (P95)
4. **Durability**: No data loss
5. **Cost-effective**: Tiered storage to optimize costs

### Capacity Estimation

**Assumptions**:
- 10,000 servers + 50,000 containers
- 100 log lines/sec per source
- 200 metrics per source
- 15-second metric scrape interval
- 90-day hot retention (logs), 15-day raw metrics

**Log Volume**:
```
Sources: 10K servers + 50K containers = 60K sources
Log events/sec: 60K × 100 = 6M events/sec
Event size: 512 bytes (JSON logs)
Daily volume: 6M × 86,400 × 512 bytes = 265 TB/day (raw)
Compressed (5x): 265 ÷ 5 = 53 TB/day
90-day retention: 53 TB × 90 = 4,770 TB ≈ 5 PB
```

**Metric Volume**:
```
Active metrics: 60K sources × 200 = 12M time series (but with cardinality: ~10M unique)
Samples/min: 10M × (60/15) = 40M samples/min
Storage/day: 40M × 1,440 × 1.3 bytes = 75 GB/day (Prometheus compression)
15-day retention: 75 GB × 15 = 1.1 TB
```

**Ingestion Rate**:
```
Logs: 6M events/sec (peak: 12M/sec)
Metrics: 40M samples/min = 667K samples/sec
Total bandwidth: 6M × 512 bytes = 3 GB/sec ≈ 24 Gbps
```

---

## Step 2: High-Level Architecture (15 min)

### System Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                   DATA SOURCES                              │
│   Apps │ Containers │ Servers │ Databases │ Cloud Services │
└──────────────────────┬──────────────────────────────────────┘
                       │
        ┌──────────────┴──────────────┐
        ▼                             ▼
┌─────────────────┐          ┌─────────────────┐
│   LOG AGENTS    │          │ METRIC EXPORTERS│
│ Filebeat/Fluent │          │  Prometheus     │
└────────┬────────┘          └────────┬────────┘
         │                            │
         ▼                            ▼
┌─────────────────┐          ┌─────────────────┐
│ LOG COLLECTORS  │          │ PROMETHEUS      │
│ Fluentd/Logstash│          │   SERVERS       │
└────────┬────────┘          └────────┬────────┘
         │                            │
         ▼                            ▼
┌──────────────────────────────────────────────┐
│            KAFKA / KINESIS                   │
│         Message Streaming Layer              │
└───────────────────┬──────────────────────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌──────────────────┐  ┌────────────────────┐
│ STREAM PROCESSOR │  │  BATCH PROCESSOR   │
│ Flink / Spark    │  │  Spark Batch       │
└────────┬─────────┘  └──────────┬─────────┘
         │                       │
    ┌────┴────┐             ┌────┴────┐
    ▼         ▼             ▼         ▼
┌────────┐ ┌──────┐   ┌─────────┐ ┌──────┐
│ Elastic│ │ S3   │   │ Thanos/ │ │  S3  │
│ search │ │Archive│   │ Cortex  │ │      │
└───┬────┘ └──────┘   └────┬────┘ └──────┘
    │                      │
    ▼                      ▼
┌────────────┐      ┌────────────┐
│   Kibana   │      │  Grafana   │
│ Dashboards │      │ Dashboards │
└────────────┘      └────────────┘
         │                │
         └────────┬───────┘
                  ▼
          ┌───────────────┐
          │  ALERTMANAGER │
          │   PagerDuty   │
          └───────────────┘
```

### Components

**1. Data Collection Layer**:
- **Log Agents**: Filebeat, Fluent Bit, Vector (lightweight, low overhead)
- **Log Collectors**: Fluentd, Logstash (parsing, enrichment, routing)
- **Metric Exporters**: Prometheus client libraries, Node exporter, Custom exporters

**2. Message Streaming**:
- **Kafka/Kinesis**: Durable buffering, decouples ingestion from processing
- **Topics**: logs.raw, logs.structured, metrics.raw, alerts

**3. Processing Layer**:
- **Stream Processing**: Apache Flink, Spark Streaming (real-time aggregation)
- **Batch Processing**: Spark (historical analysis, ML)

**4. Storage Layer**:
- **Logs**: Elasticsearch (searchable), S3 (archive)
- **Metrics**: Prometheus (15-day local), Thanos/Cortex (long-term S3)

**5. Visualization & Alerting**:
- **Kibana**: Log search and dashboards
- **Grafana**: Metric dashboards and alerting
- **Alertmanager**: Alert routing, grouping, deduplication

---

## Step 3: Detailed Design (25 min)

### 3.1 Log Ingestion Pipeline

#### Log Collection

**Agent Deployment**:
```yaml
# Filebeat configuration
filebeat.inputs:
  - type: log
    paths:
      - /var/log/app/*.log
    fields:
      service: user-api
      environment: production
      datacenter: us-east-1
    multiline:
      pattern: '^[0-9]{4}-[0-9]{2}-[0-9]{2}'
      negate: true
      match: after

output.kafka:
  hosts: ["kafka1:9092", "kafka2:9092", "kafka3:9092"]
  topic: logs.raw
  partition.round_robin:
    reachable_only: false
  compression: snappy
  max_message_bytes: 1000000
```

**Log Format Standardization**:
```json
{
  "timestamp": "2025-11-15T10:30:45.123Z",
  "level": "ERROR",
  "service": "payment-api",
  "environment": "production",
  "host": "ip-10-0-1-42",
  "message": "Payment processing failed",
  "context": {
    "user_id": "user_12345",
    "transaction_id": "txn_67890",
    "amount": 99.99,
    "currency": "USD",
    "error_code": "INSUFFICIENT_FUNDS"
  },
  "trace_id": "abc123def456",
  "span_id": "789ghi012jkl"
}
```

#### Stream Processing Pipeline

**Fluentd/Logstash Processing**:
```ruby
# Logstash pipeline
input {
  kafka {
    bootstrap_servers => "kafka:9092"
    topics => ["logs.raw"]
    codec => "json"
  }
}

filter {
  # Parse JSON logs
  json {
    source => "message"
  }

  # Add GeoIP enrichment
  geoip {
    source => "client_ip"
    target => "geoip"
  }

  # Filter out debug logs in production
  if [environment] == "production" and [level] == "DEBUG" {
    drop { }
  }

  # Anonymize PII
  mutate {
    gsub => [
      "message", "\b\d{3}-\d{2}-\d{4}\b", "XXX-XX-XXXX"  # SSN
    ]
  }
}

output {
  # Write to Elasticsearch
  elasticsearch {
    hosts => ["es-cluster:9200"]
    index => "logs-%{[environment]}-%{+YYYY.MM.dd}"
  }

  # Archive to S3
  s3 {
    bucket => "logs-archive"
    prefix => "logs/%{[service]}/"
    codec => "json_lines"
    time_file => 15  # 15 minutes per file
  }
}
```

**Apache Flink Real-time Aggregation**:
```java
// Flink stream processing for error rate calculation
DataStream<LogEvent> logs = env
    .addSource(new FlinkKafkaConsumer<>("logs.structured", schema, props))
    .assignTimestampsAndWatermarks(new LogTimestampExtractor());

// Calculate error rate per service
DataStream<ServiceErrorRate> errorRates = logs
    .filter(log -> log.getLevel().equals("ERROR"))
    .keyBy(log -> log.getService())
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .aggregate(new ErrorRateAggregator());

// Trigger alerts for high error rates
errorRates
    .filter(rate -> rate.getErrorsPerMinute() > 100)
    .addSink(new AlertingSink());

// Write to time-series database for dashboards
errorRates.addSink(new InfluxDBSink());
```

### 3.2 Metrics Collection & Storage

#### Prometheus Setup

**Service Discovery** (Kubernetes):
```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      # Only scrape pods with prometheus.io/scrape annotation
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      # Use custom scrape path
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      # Use pod name as instance label
      - source_labels: [__meta_kubernetes_pod_name]
        action: replace
        target_label: instance
      # Add namespace label
      - source_labels: [__meta_kubernetes_namespace]
        action: replace
        target_label: namespace

  - job_name: 'node-exporter'
    static_configs:
      - targets:
          - 'node1:9100'
          - 'node2:9100'
          - 'node3:9100'
    relabel_configs:
      - source_labels: [__address__]
        regex: '(.*):\\d+'
        target_label: instance
```

**Recording Rules** (pre-aggregation for dashboards):
```yaml
# recording_rules.yml
groups:
  - name: api_metrics
    interval: 30s
    rules:
      # Request rate per service
      - record: instance:api_requests:rate5m
        expr: rate(http_requests_total[5m])

      # Error rate
      - record: instance:api_errors:rate5m
        expr: rate(http_errors_total[5m])

      # P95 latency
      - record: instance:api_latency:p95
        expr: histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

      # CPU usage per pod
      - record: pod:cpu_usage:avg
        expr: avg by (pod, namespace) (rate(container_cpu_usage_seconds_total[5m]))
```

#### Thanos for Long-Term Storage

**Architecture**:
```
Prometheus 1 ────┐
                 │
Prometheus 2 ────┼──→ Thanos Sidecar ──→ S3 / GCS
                 │         ↓
Prometheus 3 ────┘    Thanos Store ──→ Thanos Query ──→ Grafana
                                           ↑
                      Thanos Compactor ────┘
```

**Thanos Sidecar Configuration**:
```yaml
# thanos-sidecar.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: thanos-storage-config
data:
  storage.yaml: |
    type: S3
    config:
      bucket: thanos-metrics
      endpoint: s3.amazonaws.com
      region: us-east-1

---
# Prometheus with Thanos sidecar
containers:
  - name: prometheus
    image: prom/prometheus:v2.40.0
    args:
      - --storage.tsdb.path=/prometheus
      - --storage.tsdb.retention.time=15d
      - --storage.tsdb.min-block-duration=2h
      - --storage.tsdb.max-block-duration=2h

  - name: thanos-sidecar
    image: thanosio/thanos:v0.30.0
    args:
      - sidecar
      - --tsdb.path=/prometheus
      - --prometheus.url=http://localhost:9090
      - --objstore.config-file=/etc/thanos/storage.yaml
      - --grpc-address=0.0.0.0:10901
      - --http-address=0.0.0.0:10902
```

**Downsampling Strategy**:
```
Raw data (15 days):     Every data point @ 15s interval
5-minute average (90d): Downsampled to 5min buckets (20x reduction)
1-hour average (1 year): Downsampled to 1hr buckets (240x reduction)
1-day average (5 years): Downsampled to 1day buckets (5760x reduction)

Storage savings: 365 TB/year raw → ~10 TB/year with downsampling = 97% reduction
```

### 3.3 Elasticsearch Configuration

#### Index Lifecycle Management

**ILM Policy**:
```json
{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_primary_shard_size": "50gb",
            "max_age": "1d"
          },
          "set_priority": {
            "priority": 100
          }
        }
      },
      "warm": {
        "min_age": "7d",
        "actions": {
          "shrink": {
            "number_of_shards": 1
          },
          "forcemerge": {
            "max_num_segments": 1
          },
          "set_priority": {
            "priority": 50
          },
          "allocate": {
            "require": {
              "data": "warm"
            }
          }
        }
      },
      "cold": {
        "min_age": "30d",
        "actions": {
          "searchable_snapshot": {
            "snapshot_repository": "s3_repository"
          },
          "set_priority": {
            "priority": 0
          }
        }
      },
      "delete": {
        "min_age": "90d",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}
```

**Index Template**:
```json
{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "number_of_shards": 3,
      "number_of_replicas": 1,
      "refresh_interval": "5s",
      "index.lifecycle.name": "logs-policy",
      "index.lifecycle.rollover_alias": "logs"
    },
    "mappings": {
      "properties": {
        "timestamp": {
          "type": "date",
          "format": "strict_date_optional_time||epoch_millis"
        },
        "level": {
          "type": "keyword"
        },
        "service": {
          "type": "keyword"
        },
        "environment": {
          "type": "keyword"
        },
        "message": {
          "type": "text",
          "analyzer": "standard"
        },
        "trace_id": {
          "type": "keyword"
        },
        "context": {
          "type": "object",
          "dynamic": true
        }
      }
    }
  }
}
```

### 3.4 Query Optimization

#### Elasticsearch Query Best Practices

**Efficient Query**:
```json
{
  "query": {
    "bool": {
      "filter": [
        {
          "range": {
            "timestamp": {
              "gte": "now-1h",
              "lte": "now"
            }
          }
        },
        {
          "term": {
            "service": "payment-api"
          }
        },
        {
          "term": {
            "level": "ERROR"
          }
        }
      ],
      "must": [
        {
          "match": {
            "message": "payment failed"
          }
        }
      ]
    }
  },
  "sort": [
    {
      "timestamp": {
        "order": "desc"
      }
    }
  ],
  "size": 100
}
```

**Query Optimization Techniques**:
1. **Time-based filtering**: Always use time range filters (most selective)
2. **Use filters over queries**: Filters are cached, queries are scored
3. **Limit result size**: Use pagination, not large result sets
4. **Leverage index aliases**: Query across multiple indices efficiently
5. **Use doc values**: For sorting and aggregations

#### PromQL Query Examples

**Basic Queries**:
```promql
# Request rate
rate(http_requests_total[5m])

# Error rate
sum(rate(http_errors_total[5m])) by (service)

# P95 latency
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))

# CPU usage by pod
avg by (pod) (rate(container_cpu_usage_seconds_total[5m]))

# Memory usage
container_memory_usage_bytes / container_spec_memory_limit_bytes * 100
```

**Complex Aggregations**:
```promql
# Error rate percentage
(
  sum(rate(http_errors_total[5m])) by (service)
  /
  sum(rate(http_requests_total[5m])) by (service)
) * 100

# Top 5 services by request rate
topk(5, sum(rate(http_requests_total[5m])) by (service))

# Alert when error rate > 1% for 5 minutes
ALERT HighErrorRate
IF (
  sum(rate(http_errors_total[5m])) by (service)
  /
  sum(rate(http_requests_total[5m])) by (service)
) > 0.01
FOR 5m
LABELS { severity="critical" }
ANNOTATIONS {
  summary = "High error rate on {{ $labels.service }}",
  description = "Error rate is {{ $value }}%"
}
```

### 3.5 Alerting System

#### Alertmanager Configuration

```yaml
# alertmanager.yml
global:
  resolve_timeout: 5m
  slack_api_url: 'https://hooks.slack.com/services/XXX'
  pagerduty_url: 'https://events.pagerduty.com/v2/enqueue'

route:
  group_by: ['alertname', 'cluster', 'service']
  group_wait: 10s
  group_interval: 10s
  repeat_interval: 12h
  receiver: 'default'
  routes:
    - match:
        severity: critical
      receiver: 'pagerduty'
      continue: true
    - match:
        severity: critical
      receiver: 'slack-critical'
    - match:
        severity: warning
      receiver: 'slack-warnings'

receivers:
  - name: 'default'
    webhook_configs:
      - url: 'http://alerting-service:8080/webhook'

  - name: 'pagerduty'
    pagerduty_configs:
      - routing_key: 'YOUR_ROUTING_KEY'
        description: '{{ .GroupLabels.alertname }}'
        severity: '{{ .GroupLabels.severity }}'

  - name: 'slack-critical'
    slack_configs:
      - channel: '#alerts-critical'
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
        color: 'danger'

  - name: 'slack-warnings'
    slack_configs:
      - channel: '#alerts-warnings'
        title: '{{ .GroupLabels.alertname }}'
        text: '{{ range .Alerts }}{{ .Annotations.description }}{{ end }}'
        color: 'warning'

inhibit_rules:
  - source_match:
      severity: 'critical'
    target_match:
      severity: 'warning'
    equal: ['alertname', 'cluster', 'service']
```

---

## Step 4: Scaling Strategies (10 min)

### 4.1 Horizontal Scaling

**Kafka Partitioning**:
```
Topic: logs.raw
Partitions: 120 (handle 6M events/sec @ 50K events/sec per partition)
Replication: 3x
Consumer groups:
  - log-processor (30 consumers, 4 partitions each)
  - archive-writer (10 consumers, 12 partitions each)

Partition key: hash(service_name)  # Co-locate logs from same service
```

**Elasticsearch Sharding**:
```
Index strategy: Daily indices (logs-2025.11.15)
Primary shards: 3 per index
Replica shards: 1 (2 copies total)
Shard size target: 30-50 GB

Total shards for 90 days: 90 indices × 3 shards × 2 copies = 540 shards
```

**Prometheus Federation**:
```
For >10M metrics, use Prometheus federation:

Tier 1 (Leaf): 10 Prometheus instances, each scraping 1M metrics
Tier 2 (Aggregation): 2 Prometheus instances federating from leaf nodes

Or use Thanos for horizontal scaling without federation complexity.
```

### 4.2 Caching Strategy

**Redis for Hot Queries**:
```python
def get_dashboard_data(dashboard_id, time_range):
    cache_key = f"dashboard:{dashboard_id}:{time_range}"

    # Check cache
    cached = redis.get(cache_key)
    if cached:
        return json.loads(cached)

    # Query Elasticsearch/Prometheus
    data = query_backend(dashboard_id, time_range)

    # Cache for 1 minute (dashboards often have 1-min refresh)
    redis.setex(cache_key, 60, json.dumps(data))

    return data
```

**Benefits**:
- Reduces query load by 80-90%
- Sub-millisecond response for cached queries
- Handles dashboard refresh storms

### 4.3 Cost Optimization

**1. Sampling Non-Critical Logs**:
```yaml
# Fluentd sampling
<filter logs.app>
  @type sampling
  interval 10
  sample_rate 0.1  # 10% sampling for INFO logs

  <parse>
    @type none
  </parse>

  # Always keep ERROR/FATAL
  <exclude>
    key level
    pattern /^(ERROR|FATAL)$/
  </exclude>
</filter>
```

**2. Compression**:
```
gzip compression: 5-10x reduction
Snappy (Kafka): 3-5x reduction with low CPU
zstd: 5-8x reduction with fast decompression

Always enable compression at every layer!
```

**3. Tiered Storage Costs**:
```
Hot tier (ES SSD):  $0.10/GB/month (0-7 days)
Warm tier (ES HDD): $0.03/GB/month (8-30 days)
Cold tier (S3):     $0.023/GB/month (31-90 days)
Archive (Glacier):  $0.004/GB/month (90+ days)

For 5 PB over 90 days:
  Hot (7d): 370TB × $0.10 = $37K/month
  Warm (23d): 1,219TB × $0.03 = $36.6K/month
  Cold (60d): 3,180TB × $0.023 = $73K/month
  Total: ~$147K/month (vs $500K for all hot!)
```

---

## Step 5: Advanced Topics

### 5.1 Security & Compliance

**Encryption**:
- **In-transit**: TLS 1.3 for all connections
- **At-rest**: AES-256 for ES, S3
- **PII masking**: Redact sensitive fields before storage

**Access Control**:
```yaml
# Elasticsearch RBAC
roles:
  developer:
    cluster: ['monitor']
    indices:
      - names: ['logs-dev-*']
        privileges: ['read', 'view_index_metadata']

  sre:
    cluster: ['monitor', 'manage_ilm']
    indices:
      - names: ['logs-*']
        privileges: ['read', 'view_index_metadata', 'manage']

  audit_admin:
    cluster: ['all']
    indices:
      - names: ['*']
        privileges: ['all']
```

**Audit Logging**:
```json
{
  "timestamp": "2025-11-15T10:30:45Z",
  "user": "john.doe@company.com",
  "action": "search",
  "resource": "logs-production-2025.11.15",
  "query": "service:payment-api AND level:ERROR",
  "ip": "10.0.1.42",
  "result": "success",
  "rows_returned": 157
}
```

### 5.2 Multi-Tenancy

**Tenant Isolation**:
1. **Index-based**: Separate indices per tenant (`logs-tenant123-*`)
2. **Document-level**: Add `tenant_id` field, filter in queries
3. **Cluster-based**: Dedicated clusters for large tenants

**Resource Quotas**:
```python
# Rate limiting per tenant
@rate_limit(key=lambda: request.tenant_id, limit="100/minute")
def ingest_logs(tenant_id, logs):
    validate_logs(logs)
    kafka_producer.send(f"logs.{tenant_id}", logs)

# Storage quotas
def check_storage_quota(tenant_id):
    usage = elasticsearch.cat.indices(index=f"logs-{tenant_id}-*", bytes='b')
    quota = get_tenant_quota(tenant_id)  # e.g., 100 GB

    if usage > quota:
        raise QuotaExceededException(f"Tenant {tenant_id} exceeded storage quota")
```

### 5.3 Disaster Recovery

**Backup Strategy**:
```yaml
# Elasticsearch snapshot
PUT /_snapshot/s3_repository
{
  "type": "s3",
  "settings": {
    "bucket": "es-backups",
    "region": "us-east-1",
    "base_path": "elasticsearch-snapshots"
  }
}

# Daily snapshots
PUT /_snapshot/s3_repository/daily_%3Cnow%2Fd%3E
{
  "indices": "logs-*",
  "ignore_unavailable": true,
  "include_global_state": false
}
```

**Recovery Procedures**:
1. **ES cluster failure**: Restore from S3 snapshots (RTO: 2-4 hours)
2. **Kafka data loss**: Replay from source (logs) or accept loss
3. **Prometheus**: Rebuild from Thanos long-term storage

**Multi-Region Setup**:
```
Primary: us-east-1
  - Full ES cluster + Kafka + Prometheus
  - Write logs + metrics

Secondary: us-west-2
  - Read-only ES replica (cross-cluster replication)
  - Thanos Store Gateway (reads from same S3)
  - Failover capacity

RTO: <15 minutes (DNS failover)
RPO: <5 minutes (replication lag)
```

---

## Key Takeaways

### Design Principles
1. **Decouple with messaging**: Kafka buffers bursts, enables replay
2. **Tiered storage**: Hot/warm/cold dramatically reduces costs
3. **Compression everywhere**: 5-10x savings at minimal CPU cost
4. **Index by time**: Makes retention management and queries efficient
5. **Cache aggressively**: Dashboards query the same data repeatedly

### Common Pitfalls
1. **Cardinality explosion**: Avoid high-cardinality labels (user_id, request_id)
2. **Full-text search abuse**: Use filters and time ranges
3. **Single-tier storage**: Costs spiral quickly
4. **Insufficient buffering**: Kafka prevents backpressure issues
5. **No sampling**: Costs explode with 100% DEBUG logs

### Interview Tips
- **Start with scale**: Establish volume/cardinality early
- **Discuss trade-offs**: Hot vs cold storage, pull vs push metrics
- **Show depth**: ILM policies, downsampling, partitioning strategies
- **Mention real tools**: Prometheus, Elasticsearch, Kafka, Thanos
- **Cost awareness**: Demonstrate understanding of operational costs

### Further Learning
- **Books**: "Distributed Systems Observability" (Cindy Sridharan)
- **Blogs**: Uber Engineering, Netflix Tech Blog
- **Tools**: Hands-on with ELK Stack, Prometheus, Grafana
- **Courses**: "Observability Engineering" (Honeycomb.io)

---

**Remember**: Observability systems are all about managing VOLUME and CARDINALITY at scale. Every design decision should optimize for these two dimensions.
