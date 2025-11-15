# 🛠️ Logging & Metrics Tech Stack: Industry Solutions

This document provides a comprehensive overview of technology stacks, tools, and platforms used by companies to build observability systems at scale.

---

## 📊 Table of Contents

1. [Complete Tech Stack Overview](#complete-tech-stack-overview)
2. [Log Collection & Shipping](#log-collection--shipping)
3. [Metrics Collection & Exporters](#metrics-collection--exporters)
4. [Message Streaming & Queues](#message-streaming--queues)
5. [Stream & Batch Processing](#stream--batch-processing)
6. [Storage Solutions](#storage-solutions)
7. [Visualization & Dashboarding](#visualization--dashboarding)
8. [Alerting & Incident Management](#alerting--incident-management)
9. [Cloud Provider Solutions](#cloud-provider-solutions)
10. [Commercial SaaS Platforms](#commercial-saas-platforms)
11. [Industry Implementations](#industry-implementations)
12. [Cost Comparison](#cost-comparison)

---

## Complete Tech Stack Overview

### The Modern Observability Stack (2025)

```
┌─────────────────────────────────────────────────────────────┐
│                     DATA SOURCES                             │
│  Applications • Containers • Servers • Cloud Services        │
└─────────────────────┬───────────────────────────────────────┘
                      │
    ┌─────────────────┴─────────────────┐
    │                                   │
    ▼                                   ▼
┌──────────────────────┐    ┌──────────────────────┐
│   LOG COLLECTION     │    │  METRIC COLLECTION   │
│ • Filebeat           │    │ • Prometheus         │
│ • Fluent Bit         │    │ • OpenTelemetry      │
│ • Vector             │    │ • Telegraf           │
│ • Fluentd            │    │ • StatsD             │
└──────────┬───────────┘    └───────────┬──────────┘
           │                            │
           ▼                            ▼
┌──────────────────────────────────────────────┐
│         MESSAGE STREAMING                     │
│ • Apache Kafka         • AWS Kinesis         │
│ • Google Pub/Sub       • Azure Event Hubs    │
└───────────────────┬──────────────────────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌─────────────────┐   ┌─────────────────┐
│  PROCESSING     │   │    STORAGE      │
│ • Flink         │   │ Logs:           │
│ • Spark         │   │ • Elasticsearch │
│ • Logstash      │   │ • Loki          │
└─────────────────┘   │ • ClickHouse    │
                      │ Metrics:        │
                      │ • Prometheus    │
                      │ • VictoriaMetrics│
                      │ • InfluxDB      │
                      │ • Thanos/Cortex │
                      └────────┬────────┘
                               │
                      ┌────────┴────────┐
                      ▼                 ▼
              ┌──────────────┐  ┌──────────────┐
              │ VISUALIZATION│  │   ALERTING   │
              │ • Grafana    │  │ • Alertmanager│
              │ • Kibana     │  │ • PagerDuty  │
              └──────────────┘  └──────────────┘
```

---

## Log Collection & Shipping

### Open Source Log Shippers

#### 1. **Filebeat** (Elastic)
```yaml
Type: Lightweight log shipper
Language: Go
Memory: ~50MB per instance
CPU: <1% on average
Best for: Tailing log files, forwarding to Logstash/ES/Kafka

Pros:
  ✅ Lightweight and efficient
  ✅ Built-in modules (Nginx, Apache, MySQL, etc.)
  ✅ Backpressure handling
  ✅ At-least-once delivery guarantee

Cons:
  ❌ Limited processing capabilities (use Logstash for parsing)
  ❌ Elasticsearch ecosystem focused

Use cases:
  • Shipping application logs from VMs
  • Forwarding syslogs
  • Kubernetes sidecar for log collection

Companies using: Elastic, Netflix, Slack
```

#### 2. **Fluent Bit** (CNCF)
```yaml
Type: Ultra-lightweight log processor
Language: C
Memory: ~1-2MB per instance
CPU: Minimal
Best for: Kubernetes, embedded systems, high-volume environments

Pros:
  ✅ Extremely lightweight (C-based)
  ✅ Built-in Kubernetes support
  ✅ 70+ plugins (inputs, filters, outputs)
  ✅ Better performance than Fluentd

Cons:
  ❌ Less mature plugin ecosystem than Fluentd
  ❌ Complex configuration syntax

Use cases:
  • Kubernetes logging (DaemonSet)
  • Edge computing / IoT
  • High-performance log forwarding

Companies using: AWS, Microsoft, Datadog
```

#### 3. **Fluentd** (CNCF)
```yaml
Type: Unified logging layer
Language: Ruby (core) + C (performance-critical parts)
Memory: ~40-50MB per instance
CPU: Low-moderate
Best for: Complex log pipelines, multi-destination routing

Pros:
  ✅ 500+ plugins (mature ecosystem)
  ✅ Flexible routing and filtering
  ✅ JSON-native processing
  ✅ CNCF graduated project

Cons:
  ❌ Higher memory usage than Fluent Bit
  ❌ Ruby dependency

Use cases:
  • Centralized log aggregation
  • Multi-cloud log forwarding
  • Complex ETL pipelines

Companies using: Microsoft, Atlassian, Treasure Data
```

#### 4. **Vector** (Datadog)
```yaml
Type: High-performance observability pipeline
Language: Rust
Memory: ~20-30MB
CPU: Low
Best for: High-throughput, modern infrastructure

Pros:
  ✅ Rust-based (memory safe, fast)
  ✅ Excellent performance (3-10x faster than alternatives)
  ✅ Built-in metrics and tracing support
  ✅ Strong typing and schema validation

Cons:
  ❌ Younger project (less mature)
  ❌ Smaller plugin ecosystem

Use cases:
  • High-volume log processing
  • Unified observability data pipeline
  • Replacing multiple agents with one

Companies using: Datadog, Cloudflare
```

#### 5. **Logstash** (Elastic)
```yaml
Type: Data processing pipeline
Language: JRuby (runs on JVM)
Memory: ~1-2GB per instance
CPU: Moderate-high
Best for: ETL, complex transformations, enrichment

Pros:
  ✅ Powerful filtering and transformation (Grok, mutate, etc.)
  ✅ 200+ plugins
  ✅ Tight Elasticsearch integration
  ✅ Rich data enrichment (GeoIP, DNS lookup)

Cons:
  ❌ Heavy resource usage (JVM-based)
  ❌ Slower than lightweight shippers
  ❌ Complex configuration for beginners

Use cases:
  • Central log processing hub
  • Complex parsing (custom formats)
  • Multi-destination log routing

Companies using: Elastic, Lyft, eBay
```

---

## Metrics Collection & Exporters

### 1. **Prometheus** (CNCF)
```yaml
Type: Time-series database and monitoring system
Language: Go
Architecture: Pull-based (scrapes metrics)
Best for: Kubernetes, cloud-native infrastructure

Key Features:
  • Multi-dimensional data model (labels)
  • Powerful query language (PromQL)
  • Service discovery (K8s, Consul, EC2)
  • Local time-series storage (15-day default retention)
  • Alerting with Alertmanager

Exporters (100+):
  - Node Exporter: Host metrics (CPU, memory, disk, network)
  - Blackbox Exporter: Endpoint monitoring (HTTP, TCP, ICMP)
  - JMX Exporter: Java application metrics
  - MySQL Exporter: Database metrics
  - Custom exporters: Application-specific metrics

Limitations:
  ❌ No long-term storage (use Thanos/Cortex)
  ❌ No horizontal scalability (federation required)
  ❌ No high availability (replicas needed)

Companies using: SoundCloud (creator), DigitalOcean, GitLab, Grafana Labs
```

### 2. **OpenTelemetry** (CNCF)
```yaml
Type: Observability framework (metrics, traces, logs)
Language: Multi-language SDKs
Architecture: Vendor-neutral instrumentation
Best for: Unified observability, cloud-native apps

Components:
  • SDK: Instrument applications (auto/manual)
  • Collector: Receive, process, export telemetry data
  • Protocol: OTLP (OpenTelemetry Protocol)

Backends:
  - Prometheus (metrics)
  - Jaeger / Tempo (traces)
  - Loki / Elasticsearch (logs)

Benefits:
  ✅ Vendor-neutral (no lock-in)
  ✅ Unified instrumentation (metrics + traces + logs)
  ✅ Auto-instrumentation for popular frameworks
  ✅ Industry standard (CNCF)

Companies using: Uber, Shopify, Microsoft, Google (co-creators)
```

### 3. **StatsD**
```yaml
Type: Metrics aggregation daemon
Language: Node.js (original), Go (statsd_exporter)
Architecture: Push-based (UDP)
Best for: Application metrics, low-latency collection

Metric Types:
  • Counters: Increment/decrement (e.g., page views)
  • Timers: Measure durations (e.g., API latency)
  • Gauges: Current value (e.g., active connections)
  • Sets: Count unique values (e.g., unique users)

Workflow:
  App → StatsD (UDP) → Aggregation → Backend (Prometheus, Graphite)

Pros:
  ✅ Fire-and-forget (UDP, non-blocking)
  ✅ Simple API
  ✅ Language-agnostic

Cons:
  ❌ UDP unreliable (packet loss)
  ❌ Requires aggregation daemon

Companies using: Etsy (creator), GitHub, Airbnb
```

### 4. **Telegraf** (InfluxData)
```yaml
Type: Metrics collection agent
Language: Go
Architecture: Plugin-based
Best for: Infrastructure monitoring, IoT

Plugins:
  - Inputs: 200+ (Docker, Kubernetes, Nginx, MySQL, etc.)
  - Processors: Transform/filter data
  - Aggregators: Summarize metrics
  - Outputs: 50+ (InfluxDB, Prometheus, Elasticsearch, Kafka)

Use cases:
  • Infrastructure monitoring
  • IoT device metrics
  • Custom application metrics

Companies using: InfluxData, Cisco
```

---

## Message Streaming & Queues

### 1. **Apache Kafka**
```yaml
Type: Distributed event streaming platform
Language: Java/Scala
Throughput: 1M+ messages/sec per broker
Latency: <10ms (P99)
Best for: High-throughput, durable log streaming

Architecture:
  • Topics: Logical channels (logs, metrics, alerts)
  • Partitions: Horizontal scaling unit
  • Replication: 3x default for durability
  • Consumer groups: Parallel processing

Key Features:
  ✅ Extremely high throughput
  ✅ Durable (configurable retention)
  ✅ Replay capability (time-travel)
  ✅ Exactly-once semantics

Use cases:
  - Log aggregation pipeline
  - Event sourcing
  - Real-time analytics

Companies using: LinkedIn (creator), Uber, Netflix, Airbnb
Cost: Open-source / Confluent Cloud ($0.11/GB ingress + storage)
```

### 2. **AWS Kinesis**
```yaml
Type: Managed streaming service
Throughput: 1MB/sec per shard (write), 2MB/sec (read)
Latency: <1 second
Best for: AWS-native applications

Components:
  • Kinesis Data Streams: Real-time data streaming
  • Kinesis Firehose: Load data to S3, Redshift, Elasticsearch
  • Kinesis Analytics: SQL queries on streams

Pros:
  ✅ Fully managed (no ops)
  ✅ Auto-scaling shards
  ✅ Tight AWS integration

Cons:
  ❌ More expensive than Kafka
  ❌ Limited to 24-hour retention (default)

Companies using: Netflix, Lyft, Zillow
Cost: $0.015 per shard-hour + $0.014/GB ingested
```

### 3. **Google Cloud Pub/Sub**
```yaml
Type: Managed message queue
Throughput: Unlimited (auto-scaling)
Latency: <100ms (P99)
Best for: GCP-native, event-driven architectures

Features:
  • At-least-once delivery
  • Auto-scaling (no shard management)
  • Dead letter topics
  • Message ordering (limited)

Companies using: Spotify, The New York Times
Cost: $40/TB ingested + $40/TB delivered
```

### 4. **RabbitMQ**
```yaml
Type: Message broker
Language: Erlang
Throughput: 50K-100K messages/sec
Best for: Task queues, RPC, traditional messaging

Pros:
  ✅ Flexible routing (exchanges, queues)
  ✅ Multi-protocol (AMQP, MQTT, STOMP)
  ✅ Mature and stable

Cons:
  ❌ Lower throughput than Kafka
  ❌ Complex clustering setup

Companies using: T-Mobile, Runtastic
```

---

## Stream & Batch Processing

### 1. **Apache Flink**
```yaml
Type: Stream processing framework
Language: Java/Scala
Latency: Millisecond (true streaming)
Best for: Real-time analytics, stateful processing

Features:
  • Event time processing
  • Exactly-once semantics
  • State management (RocksDB backend)
  • Windowing (tumbling, sliding, session)

Use cases:
  - Real-time log aggregation
  - Anomaly detection
  - Complex event processing

Companies using: Alibaba, Uber, Netflix, ING Bank
```

### 2. **Apache Spark Streaming**
```yaml
Type: Micro-batch stream processing
Language: Scala/Java/Python
Latency: Seconds (micro-batches)
Best for: Near real-time, batch + streaming hybrid

Features:
  • Unified API (batch + streaming)
  • Machine learning (MLlib)
  • SQL queries (Spark SQL)
  • Integration with Hadoop ecosystem

Use cases:
  - ETL pipelines
  - Machine learning on logs
  - Historical analysis

Companies using: Netflix, Airbnb, Pinterest
```

### 3. **ksqlDB** (Confluent)
```yaml
Type: Streaming SQL engine for Kafka
Language: Java
Best for: SQL-based stream processing

Features:
  • Stream and table abstractions
  • Windowed aggregations
  • Joins across streams
  • Push queries (continuous)

Example:
  CREATE STREAM error_logs AS
    SELECT *
    FROM app_logs
    WHERE level = 'ERROR'
    EMIT CHANGES;

Companies using: Confluent, Robinhood
```

---

## Storage Solutions

### Logs

#### 1. **Elasticsearch**
```yaml
Type: Distributed search and analytics engine
Language: Java
Query Language: Query DSL (JSON-based)
Best for: Full-text search, log analytics

Architecture:
  • Indices: Time-based (logs-2025.11.15)
  • Shards: 30-50 GB target size
  • Replicas: 1-2 for HA
  • Index Lifecycle Management (ILM): Hot/warm/cold tiers

Pros:
  ✅ Powerful full-text search
  ✅ Rich aggregations
  ✅ Horizontal scalability
  ✅ Kibana integration

Cons:
  ❌ JVM-based (high memory usage)
  ❌ Expensive at scale (need tiering)
  ❌ Complex tuning required

Companies using: Uber, LinkedIn, Walmart, GitHub
Cost: $0.10-0.15/GB/month (SSD hot tier)
```

#### 2. **Grafana Loki**
```yaml
Type: Log aggregation system
Language: Go
Query Language: LogQL
Best for: Cost-effective log storage, Prometheus-like workflow

Architecture:
  • Indexes: Metadata only (labels), not content
  • Storage: Logs stored in object storage (S3, GCS)
  • Distributor → Ingester → Querier

Pros:
  ✅ 10x cheaper than Elasticsearch (indexes labels, not content)
  ✅ Native Grafana integration
  ✅ Prometheus-like label model
  ✅ Multi-tenancy built-in

Cons:
  ❌ Limited full-text search capabilities
  ❌ Slower queries for complex searches

Companies using: Grafana Labs, Grofers
Cost: ~$0.01/GB/month (object storage + minimal compute)
```

#### 3. **ClickHouse**
```yaml
Type: Columnar OLAP database
Language: C++
Best for: Analytics, high-insert rate, aggregations

Features:
  • Columnar storage (fast aggregations)
  • High compression (10x+)
  • Vectorized query execution
  • Distributed queries

Use cases:
  - Log analytics
  - Time-series analytics
  - Real-time dashboards

Companies using: Cloudflare, Uber, eBay, Spotify
Cost: Open-source / ClickHouse Cloud
```

### Metrics

#### 1. **Prometheus TSDB**
```yaml
Type: Time-series database
Storage: Local disk (SSD recommended)
Retention: 15 days default
Compression: 1.3 bytes/sample

Pros:
  ✅ Efficient compression
  ✅ Fast local queries
  ✅ Pull-based architecture

Cons:
  ❌ Limited retention (needs long-term storage)
  ❌ No clustering (single node)

Companies using: SoundCloud, DigitalOcean, GitLab
```

#### 2. **Thanos** (Long-term Prometheus storage)
```yaml
Type: Prometheus HA and long-term storage
Language: Go
Storage: S3, GCS, Azure Blob
Best for: Multi-cluster Prometheus, unlimited retention

Components:
  • Sidecar: Uploads Prometheus blocks to object storage
  • Store Gateway: Queries historical data from object storage
  • Compactor: Downsampling and compaction
  • Query: Global query view

Downsampling:
  - Raw (15 days): Full resolution
  - 5min avg (90 days): 20x smaller
  - 1hr avg (1 year): 240x smaller
  - 1day avg (5 years): 5760x smaller

Companies using: Improbable, Monzo, Red Hat
```

#### 3. **Cortex** (Prometheus-as-a-Service)
```yaml
Type: Horizontally scalable Prometheus
Language: Go
Architecture: Multi-tenant, distributed
Best for: Large-scale, multi-tenant metrics

Components:
  • Distributor: Ingestion frontend
  • Ingester: Write path, stores in chunks
  • Querier: Read path, queries chunks + long-term storage
  • Compactor: Downsampling

Companies using: Grafana Labs, EA, Weaveworks
```

#### 4. **VictoriaMetrics**
```yaml
Type: Time-series database
Language: Go
Best for: High-cardinality, cost-effective Prometheus alternative

Features:
  • Prometheus-compatible (drop-in replacement)
  • 10x better compression than Prometheus
  • Handles high cardinality well
  • Lower resource usage

Benchmark: 2-3x faster queries, 50% less storage

Companies using: CERN, Synthesio, Zerodha
```

#### 5. **InfluxDB**
```yaml
Type: Time-series database
Language: Go
Query Language: InfluxQL / Flux
Best for: IoT, monitoring, real-time analytics

Pros:
  ✅ Purpose-built for time-series
  ✅ High write throughput
  ✅ Continuous queries (pre-aggregation)

Cons:
  ❌ Limited scalability (clustering in enterprise only)
  ❌ Less mature than Prometheus for K8s

Companies using: Cisco, IBM, eBay
Cost: Open-source / InfluxDB Cloud ($0.25/GB data in)
```

#### 6. **TimescaleDB**
```yaml
Type: PostgreSQL extension for time-series
Language: C (PostgreSQL)
Best for: SQL-based time-series, transactional + analytical

Features:
  • Full SQL support
  • Automatic partitioning (hypertables)
  • Continuous aggregates
  • Data retention policies

Companies using: Comcast, Warner Music Group
```

---

## Visualization & Dashboarding

### 1. **Grafana**
```yaml
Type: Observability platform
Language: Go + TypeScript (frontend)
Best for: Unified dashboards (metrics, logs, traces)

Data Sources (50+):
  - Prometheus, Loki, Elasticsearch
  - InfluxDB, CloudWatch, Datadog
  - PostgreSQL, MySQL

Features:
  ✅ Unified dashboarding
  ✅ Alerting (native)
  ✅ Templating & variables
  ✅ Plugins (panels, data sources, apps)
  ✅ Multi-tenancy

Companies using: PayPal, eBay, Intel, Wikimedia
Cost: Open-source / Grafana Cloud
```

### 2. **Kibana**
```yaml
Type: Data visualization for Elasticsearch
Language: TypeScript (Node.js)
Best for: Log search and exploration

Features:
  • Discover: Ad-hoc log search
  • Visualize: Charts, graphs, maps
  • Dashboard: Combined visualizations
  • Canvas: Pixel-perfect reporting
  • Machine Learning: Anomaly detection

Companies using: Netflix, Slack, Adobe
Cost: Open-source / Elastic Cloud
```

### 3. **Chronograf** (InfluxData)
```yaml
Type: Data visualization for InfluxDB
Best for: InfluxDB users

Features:
  - Template dashboards
  - Flux query builder
  - Alerting

Companies using: InfluxData ecosystem
```

---

## Alerting & Incident Management

### 1. **Prometheus Alertmanager**
```yaml
Type: Alert routing and management
Language: Go
Best for: Prometheus alerting

Features:
  • Grouping: Group related alerts
  • Inhibition: Suppress alerts based on others
  • Silencing: Temporarily mute alerts
  • Routing: Route to different receivers

Companies using: All Prometheus users
```

### 2. **PagerDuty**
```yaml
Type: Incident management platform
Best for: On-call management, escalation

Features:
  • On-call schedules
  • Escalation policies
  • Incident timelines
  • Post-mortem analysis
  • Integrations (200+)

Companies using: Zoom, Shopify, Slack, DoorDash
Cost: $21-41/user/month
```

### 3. **Opsgenie** (Atlassian)
```yaml
Type: Alert management and on-call
Best for: Atlassian ecosystem, DevOps teams

Features:
  • Alert enrichment
  • On-call scheduling
  • Incident workflows
  • ChatOps (Slack, Teams)

Companies using: Atlassian customers
Cost: $9-29/user/month
```

### 4. **VictorOps** (Splunk)
```yaml
Type: Incident management
Best for: Splunk users, real-time collaboration

Features:
  • Timeline view of incidents
  • War rooms (collaboration)
  • Post-incident review

Companies using: Splunk ecosystem
```

---

## Cloud Provider Solutions

### AWS

```yaml
Logs:
  • CloudWatch Logs: Centralized logging ($0.50/GB ingested)
  • S3 + Athena: Long-term storage + querying
  • OpenSearch Service: Managed Elasticsearch ($0.05/hr per node)

Metrics:
  • CloudWatch Metrics: 1-minute resolution ($0.30 per metric)
  • Managed Prometheus (AMP): $0.10/metric/month

Streaming:
  • Kinesis Data Streams: $0.015/shard-hour
  • Kinesis Firehose: $0.029/GB delivered

Full Stack:
  App → Kinesis → Lambda/Flink → S3 + OpenSearch + Athena
```

### Google Cloud Platform (GCP)

```yaml
Logs:
  • Cloud Logging: $0.50/GB ingested (after free 50 GB/month)
  • BigQuery: Log analytics with SQL

Metrics:
  • Cloud Monitoring: $0.2580/metric sample/month

Streaming:
  • Pub/Sub: $40/TB ingested

Full Stack:
  App → Pub/Sub → Dataflow → BigQuery + Cloud Logging
```

### Azure

```yaml
Logs:
  • Azure Monitor Logs: $2.76/GB ingested
  • Log Analytics: Query and analyze

Metrics:
  • Azure Monitor Metrics: Free (standard metrics)

Streaming:
  • Event Hubs: $0.028/million events

Full Stack:
  App → Event Hubs → Stream Analytics → Log Analytics + Blob Storage
```

---

## Commercial SaaS Platforms

### 1. **Datadog**
```yaml
Type: Full-stack observability platform
Best for: Unified monitoring (logs, metrics, traces, RUM)

Pricing:
  - Infrastructure monitoring: $15/host/month
  - Log management: $0.10/GB ingested
  - APM: $31/host/month

Features:
  ✅ All-in-one platform
  ✅ 600+ integrations
  ✅ Real user monitoring
  ✅ Synthetic monitoring
  ✅ Security monitoring

Companies using: Airbnb, Peloton, Samsung
```

### 2. **New Relic**
```yaml
Type: Observability platform
Best for: APM, full-stack monitoring

Pricing:
  - Consumption-based: $0.30/GB ingested (data) + $0.0005/compute-hour (queries)

Features:
  - APM (application performance)
  - Infrastructure monitoring
  - Log management (free with APM)
  - Browser monitoring

Companies using: DoorDash, GitHub, Epic Games
```

### 3. **Splunk**
```yaml
Type: Data platform (SIEM, logging, metrics)
Best for: Enterprise, security, compliance

Pricing:
  - Cloud: $150/GB ingested/month
  - On-prem: License-based

Features:
  ✅ Powerful search (SPL)
  ✅ SIEM capabilities
  ✅ Machine learning
  ✅ Enterprise features

Companies using: Cisco, Domino's Pizza, McLaren
```

### 4. **Dynatrace**
```yaml
Type: Software intelligence platform
Best for: APM, AIOps, enterprise

Pricing:
  - Full-stack monitoring: $0.08/hour per host
  - Digital experience monitoring: $0.00225/session

Features:
  - AI-powered root cause analysis
  - Auto-discovery and instrumentation
  - Business analytics

Companies using: BMW, SAP, Under Armour
```

### 5. **Sumo Logic**
```yaml
Type: Cloud-native observability
Best for: Multi-cloud, security analytics

Pricing:
  - Essentials: $90/GB/month
  - Enterprise: $150/GB/month

Features:
  - Cloud SIEM
  - Log analytics
  - Infrastructure monitoring

Companies using: Airbnb, Alaska Airlines
```

---

## Industry Implementations

### Uber
```yaml
Stack:
  - Logs: Custom ELK stack → ClickHouse (analytics)
  - Metrics: M3DB (distributed Prometheus-compatible TSDB, open-sourced by Uber)
  - Streaming: Apache Kafka
  - Volume: 100M+ metrics, 10 PB logs/year

Open-sourced:
  - M3DB: Time-series database
  - Jaeger: Distributed tracing
```

### Netflix
```yaml
Stack:
  - Metrics: Atlas (in-house TSDB)
  - Logs: Elasticsearch + Kafka
  - Tracing: Zipkin
  - Alerting: Custom (integrated with PagerDuty)
  - Volume: 2.5M metrics, 1 trillion events/day

Open-sourced:
  - Atlas: Metrics platform
  - Vector: Netflix's custom log router (now Datadog Vector)
```

### LinkedIn
```yaml
Stack:
  - Logging: Custom (based on Kafka)
  - Metrics: InGraphs (in-house)
  - Streaming: Apache Kafka (created by LinkedIn)
  - Volume: Millions of events/sec

Open-sourced:
  - Apache Kafka
  - Brooklin: Data streaming platform
```

### Airbnb
```yaml
Stack:
  - Logging: Elasticsearch + Kafka
  - Metrics: StatsD → Prometheus → M3DB
  - Tracing: OpenTelemetry
  - Visualization: Grafana
  - Alerting: PagerDuty

Tools used:
  - Superset: Data visualization (open-sourced by Airbnb)
```

---

## Cost Comparison

### Self-Hosted vs SaaS (100 TB/month logs, 10M metrics)

```yaml
Self-Hosted (AWS):
  Elasticsearch cluster: $15K/month
  Kafka cluster: $5K/month
  Prometheus + Thanos: $3K/month
  S3 storage: $2.5K/month
  EC2 instances (ops): $5K/month
  Total: ~$30K/month + engineering time

  Pros: Full control, customization
  Cons: Requires SRE team, operational overhead

Datadog:
  Logs: 100 TB × $0.10 = $10K/month
  Metrics: 10M × $0.005 = $50K/month
  Total: ~$60K/month

  Pros: Zero ops, fast setup
  Cons: 2x cost, vendor lock-in

New Relic:
  Data: 100 TB × $0.30 = $30K/month
  Queries: Variable
  Total: ~$30-40K/month

  Pros: Simple pricing, no host limits
  Cons: Query costs can spike

Verdict:
  - Small teams (<50 people): SaaS (Datadog, New Relic)
  - Medium teams (50-500): Hybrid (open-source + commercial for critical services)
  - Large teams (500+): Self-hosted with SRE team
```

---

## Recommended Stacks by Company Size

### Startup (1-50 servers)
```yaml
Stack:
  - Logs: CloudWatch Logs / GCP Logging (native cloud)
  - Metrics: Prometheus + Grafana Cloud
  - Alerting: PagerDuty
  - Cost: $500-2K/month

Rationale: Minimize ops overhead, use managed services
```

### Scale-up (50-500 servers)
```yaml
Stack:
  - Logs: Loki + S3 (cost-effective)
  - Metrics: Prometheus + Thanos
  - Streaming: Kafka (managed - AWS MSK / Confluent Cloud)
  - Visualization: Grafana
  - Alerting: PagerDuty
  - Cost: $5-15K/month

Rationale: Balance cost and control, introduce streaming
```

### Enterprise (500+ servers)
```yaml
Stack:
  - Logs: Elasticsearch (tiered) + S3
  - Metrics: Prometheus + Cortex/Thanos
  - Streaming: Kafka (self-hosted)
  - Processing: Flink
  - Visualization: Grafana + Kibana
  - Alerting: PagerDuty + internal tools
  - Cost: $30-100K/month

Rationale: Full control, dedicated SRE team, custom tooling
```

---

## Key Takeaways

1. **No one-size-fits-all**: Choose based on scale, budget, team size
2. **Open-source first**: ELK, Prometheus, Grafana provide 90% of features
3. **SaaS for speed**: Datadog/New Relic for rapid iteration, small teams
4. **Hybrid approach**: Critical services on SaaS, rest on open-source
5. **Cost optimization**: Sampling, tiering, compression save 70-90%

---

**Industry Trends (2025)**:
- **OpenTelemetry** becoming the standard for instrumentation
- **ClickHouse** gaining popularity for log analytics (cheaper than ES)
- **Loki** adoption growing (10x cheaper than Elasticsearch)
- **Thanos/Cortex** solving Prometheus long-term storage
- **FinOps**: Focus on observability cost optimization
