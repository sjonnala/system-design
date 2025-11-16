# Observability

Observability is the ability to understand the internal state of a system by examining its outputs. For distributed systems, this means using metrics, logs, and traces to understand system behavior and diagnose problems.

## Core Concepts

### The Three Pillars of Observability

```
┌─────────────────────────────────────────────────────────┐
│                    OBSERVABILITY                        │
│                                                         │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐         │
│  │ METRICS  │    │   LOGS   │    │  TRACES  │         │
│  │          │    │          │    │          │         │
│  │ What     │    │ Why      │    │ Where    │         │
│  │ happened │    │ happened │    │ happened │         │
│  └──────────┘    └──────────┘    └──────────┘         │
└─────────────────────────────────────────────────────────┘
```

### 1. Metrics

**Definition**: Numerical measurements over time

**Characteristics**:
- Aggregatable
- Low storage cost
- Good for alerting
- Historical trends

**Types**:
- **Counter**: Ever-increasing value (requests_total)
- **Gauge**: Point-in-time value (memory_usage)
- **Histogram**: Distribution of values (request_duration)
- **Summary**: Similar to histogram (quantiles)

### 2. Logs

**Definition**: Timestamped records of discrete events

**Characteristics**:
- High cardinality
- Detailed context
- Good for debugging
- High storage cost

**Levels**:
- ERROR: Application errors
- WARN: Warning conditions
- INFO: Informational messages
- DEBUG: Detailed debugging info

### 3. Traces

**Definition**: Recording the path of a request through a distributed system

**Characteristics**:
- Show request flow
- Identify bottlenecks
- Calculate latency breakdown
- Understand dependencies

**Components**:
- **Trace**: End-to-end request journey
- **Span**: Single operation within a trace
- **Tags**: Metadata about the span

## Metrics with Prometheus & Grafana

### Prometheus

**Overview**:
- Pull-based metrics system
- Time-series database
- PromQL query language
- Service discovery
- Alerting

**Architecture**:
```
┌──────────────────────────────────────────────────────┐
│                   Prometheus Server                  │
│  ┌────────────┐  ┌────────────┐  ┌──────────────┐  │
│  │  Retrieval │  │   Storage  │  │  HTTP Server │  │
│  │   (Pull)   │─►│   (TSDB)   │◄─│   (API)      │  │
│  └─────┬──────┘  └────────────┘  └──────┬───────┘  │
└────────┼─────────────────────────────────┼──────────┘
         │                                 │
         │ Scrape                          │ Query
         ▼                                 ▼
    ┌─────────┐                      ┌──────────┐
    │ Targets │                      │ Grafana  │
    │ (Apps)  │                      │ Alertmgr │
    └─────────┘                      └──────────┘
```

**Prometheus Configuration**:
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Kubernetes pods
  - job_name: 'kubernetes-pods'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__

  # Node exporter
  - job_name: 'node-exporter'
    static_configs:
      - targets: ['node-exporter:9100']

  # Application
  - job_name: 'app'
    static_configs:
      - targets: ['app:8080']
    metrics_path: /metrics

# Alerting rules
rule_files:
  - /etc/prometheus/rules/*.yml

# Alertmanager
alerting:
  alertmanagers:
    - static_configs:
        - targets: ['alertmanager:9093']
```

**Instrumenting Application** (Node.js example):
```javascript
const express = require('express');
const promClient = require('prom-client');

const app = express();

// Enable default metrics (CPU, memory, etc.)
promClient.collectDefaultMetrics();

// Custom metrics
const httpRequestDuration = new promClient.Histogram({
  name: 'http_request_duration_seconds',
  help: 'Duration of HTTP requests in seconds',
  labelNames: ['method', 'route', 'status_code'],
  buckets: [0.1, 0.5, 1, 2, 5]
});

const httpRequestTotal = new promClient.Counter({
  name: 'http_requests_total',
  help: 'Total number of HTTP requests',
  labelNames: ['method', 'route', 'status_code']
});

const activeConnections = new promClient.Gauge({
  name: 'active_connections',
  help: 'Number of active connections'
});

// Middleware to track metrics
app.use((req, res, next) => {
  const start = Date.now();
  activeConnections.inc();

  res.on('finish', () => {
    const duration = (Date.now() - start) / 1000;
    const labels = {
      method: req.method,
      route: req.route?.path || req.path,
      status_code: res.statusCode
    };

    httpRequestDuration.observe(labels, duration);
    httpRequestTotal.inc(labels);
    activeConnections.dec();
  });

  next();
});

// Expose metrics endpoint
app.get('/metrics', async (req, res) => {
  res.set('Content-Type', promClient.register.contentType);
  res.send(await promClient.register.metrics());
});

app.listen(8080);
```

**Common PromQL Queries**:
```promql
# Request rate (req/sec)
rate(http_requests_total[5m])

# Average request duration
rate(http_request_duration_seconds_sum[5m])
  / rate(http_request_duration_seconds_count[5m])

# 95th percentile latency
histogram_quantile(0.95,
  rate(http_request_duration_seconds_bucket[5m]))

# Error rate
sum(rate(http_requests_total{status_code=~"5.."}[5m]))
  / sum(rate(http_requests_total[5m]))

# CPU usage
rate(container_cpu_usage_seconds_total[5m])

# Memory usage
container_memory_usage_bytes / container_spec_memory_limit_bytes

# Disk I/O
rate(container_fs_reads_bytes_total[5m])
```

**Alert Rules**:
```yaml
# /etc/prometheus/rules/alerts.yml
groups:
  - name: application
    interval: 30s
    rules:
      # High error rate
      - alert: HighErrorRate
        expr: |
          sum(rate(http_requests_total{status_code=~"5.."}[5m]))
          / sum(rate(http_requests_total[5m])) > 0.05
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High error rate detected"
          description: "Error rate is {{ $value | humanizePercentage }}"

      # High latency
      - alert: HighLatency
        expr: |
          histogram_quantile(0.95,
            rate(http_request_duration_seconds_bucket[5m])) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High latency detected"
          description: "P95 latency is {{ $value }}s"

      # Service down
      - alert: ServiceDown
        expr: up == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "Service {{ $labels.job }} is down"

      # High CPU usage
      - alert: HighCPUUsage
        expr: |
          rate(container_cpu_usage_seconds_total[5m]) > 0.8
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.pod }}"
          description: "CPU usage is {{ $value | humanizePercentage }}"

      # High memory usage
      - alert: HighMemoryUsage
        expr: |
          container_memory_usage_bytes / container_spec_memory_limit_bytes > 0.9
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage on {{ $labels.pod }}"
          description: "Memory usage is {{ $value | humanizePercentage }}"
```

### Grafana

**Dashboard Example** (JSON):
```json
{
  "dashboard": {
    "title": "Application Metrics",
    "panels": [
      {
        "title": "Request Rate",
        "targets": [
          {
            "expr": "sum(rate(http_requests_total[5m])) by (service)",
            "legendFormat": "{{ service }}"
          }
        ],
        "type": "graph"
      },
      {
        "title": "Error Rate",
        "targets": [
          {
            "expr": "sum(rate(http_requests_total{status_code=~\"5..\"}[5m])) / sum(rate(http_requests_total[5m]))",
            "legendFormat": "Error Rate"
          }
        ],
        "type": "graph"
      },
      {
        "title": "P95 Latency",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))",
            "legendFormat": "P95"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

**Dashboard as Code** (Grafonnet):
```jsonnet
local grafana = import 'grafonnet/grafana.libsonnet';

grafana.dashboard.new(
  'Application Dashboard',
  time_from='now-6h',
)
.addPanel(
  grafana.graphPanel.new(
    'Request Rate',
    datasource='Prometheus',
    format='reqps',
  )
  .addTarget(
    grafana.prometheus.target(
      'sum(rate(http_requests_total[5m])) by (service)',
      legendFormat='{{ service }}',
    )
  ),
  gridPos={x: 0, y: 0, w: 12, h: 8}
)
```

## Logging with ELK/Loki

### ELK Stack (Elasticsearch, Logstash, Kibana)

**Architecture**:
```
┌──────────┐     ┌──────────┐     ┌──────────────┐     ┌─────────┐
│   Apps   │────►│ Filebeat │────►│   Logstash   │────►│  Elastic│
│          │     │ (shipper)│     │ (processing) │     │ Search  │
└──────────┘     └──────────┘     └──────────────┘     └────┬────┘
                                                             │
                                                             ▼
                                                        ┌─────────┐
                                                        │ Kibana  │
                                                        │ (visual)│
                                                        └─────────┘
```

**Structured Logging** (JSON):
```javascript
const winston = require('winston');

const logger = winston.createLogger({
  format: winston.format.combine(
    winston.format.timestamp(),
    winston.format.json()
  ),
  transports: [
    new winston.transports.Console(),
    new winston.transports.File({ filename: 'app.log' })
  ]
});

// Usage
logger.info('User login', {
  userId: '12345',
  email: 'user@example.com',
  ip: '192.168.1.1',
  duration: 125
});

// Output:
// {
//   "timestamp": "2024-01-15T10:30:00.000Z",
//   "level": "info",
//   "message": "User login",
//   "userId": "12345",
//   "email": "user@example.com",
//   "ip": "192.168.1.1",
//   "duration": 125
// }
```

**Filebeat Configuration**:
```yaml
# filebeat.yml
filebeat.inputs:
  - type: log
    enabled: true
    paths:
      - /var/log/app/*.log
    json.keys_under_root: true
    json.add_error_key: true

processors:
  - add_kubernetes_metadata:
      host: ${NODE_NAME}
      matchers:
      - logs_path:
          logs_path: "/var/log/containers/"

output.logstash:
  hosts: ["logstash:5044"]

# Or directly to Elasticsearch
output.elasticsearch:
  hosts: ["elasticsearch:9200"]
  index: "app-logs-%{+yyyy.MM.dd}"
```

**Logstash Pipeline**:
```ruby
# logstash.conf
input {
  beats {
    port => 5044
  }
}

filter {
  # Parse JSON logs
  json {
    source => "message"
  }

  # Add GeoIP
  geoip {
    source => "ip"
  }

  # Parse timestamp
  date {
    match => ["timestamp", "ISO8601"]
  }

  # Add fields
  mutate {
    add_field => {
      "environment" => "production"
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "app-logs-%{+YYYY.MM.dd}"
  }

  # Debug output
  stdout {
    codec => rubydebug
  }
}
```

### Loki (Grafana Loki)

**Overview**:
- Like Prometheus, but for logs
- Indexes labels, not content
- Lower cost than Elasticsearch
- Integrated with Grafana

**Architecture**:
```
┌──────────┐     ┌─────────┐     ┌──────────┐     ┌─────────┐
│   Apps   │────►│ Promtail│────►│   Loki   │◄────│ Grafana │
│          │     │         │     │          │     │         │
└──────────┘     └─────────┘     └──────────┘     └─────────┘
```

**Promtail Configuration**:
```yaml
# promtail.yaml
server:
  http_listen_port: 9080

positions:
  filename: /tmp/positions.yaml

clients:
  - url: http://loki:3100/loki/api/v1/push

scrape_configs:
  - job_name: system
    static_configs:
      - targets:
          - localhost
        labels:
          job: varlogs
          __path__: /var/log/*.log

  - job_name: kubernetes-pods
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        target_label: app
      - source_labels: [__meta_kubernetes_pod_name]
        target_label: pod
      - source_labels: [__meta_kubernetes_namespace]
        target_label: namespace
```

**LogQL Queries**:
```logql
# All logs from a service
{app="api"}

# Error logs
{app="api"} |= "error"

# JSON parsing
{app="api"} | json | status_code >= 500

# Rate of errors
rate({app="api"} |= "error" [5m])

# Log pattern
{app="api"} |~ "user \\w+ logged in"

# Aggregate
sum by (status_code) (rate({app="api"} | json [5m]))
```

## Distributed Tracing

### Jaeger

**Overview**:
- CNCF graduated project
- OpenTelemetry compatible
- Distributed tracing system
- Performance monitoring

**Architecture**:
```
┌─────────────────────────────────────────────────────┐
│                  Application                        │
│  ┌──────────┐    ┌──────────┐    ┌──────────┐     │
│  │Service A │───►│Service B │───►│Service C │     │
│  └────┬─────┘    └────┬─────┘    └────┬─────┘     │
└───────┼──────────────┼──────────────┼──────────────┘
        │              │              │
        │  Spans       │  Spans       │  Spans
        ▼              ▼              ▼
   ┌────────────────────────────────────────┐
   │         Jaeger Agent (local)           │
   └────────────────┬───────────────────────┘
                    │
                    ▼
   ┌────────────────────────────────────────┐
   │         Jaeger Collector               │
   └────────────────┬───────────────────────┘
                    │
         ┌──────────┼──────────┐
         ▼                     ▼
   ┌──────────┐         ┌──────────┐
   │ Storage  │         │  Jaeger  │
   │(Cassandra│         │    UI    │
   │  /ES)    │         └──────────┘
   └──────────┘
```

**Instrumenting with OpenTelemetry** (Node.js):
```javascript
const { NodeTracerProvider } = require('@opentelemetry/sdk-trace-node');
const { JaegerExporter } = require('@opentelemetry/exporter-jaeger');
const { Resource } = require('@opentelemetry/resources');
const { SemanticResourceAttributes } = require('@opentelemetry/semantic-conventions');
const { SimpleSpanProcessor } = require('@opentelemetry/sdk-trace-base');
const { HttpInstrumentation } = require('@opentelemetry/instrumentation-http');
const { ExpressInstrumentation } = require('@opentelemetry/instrumentation-express');
const { registerInstrumentations } = require('@opentelemetry/instrumentation');

// Initialize provider
const provider = new NodeTracerProvider({
  resource: new Resource({
    [SemanticResourceAttributes.SERVICE_NAME]: 'my-service',
  }),
});

// Configure Jaeger exporter
const exporter = new JaegerExporter({
  endpoint: 'http://jaeger:14268/api/traces',
});

provider.addSpanProcessor(new SimpleSpanProcessor(exporter));
provider.register();

// Auto-instrument HTTP and Express
registerInstrumentations({
  instrumentations: [
    new HttpInstrumentation(),
    new ExpressInstrumentation(),
  ],
});

// Manual instrumentation
const { trace } = require('@opentelemetry/api');

async function processOrder(orderId) {
  const tracer = trace.getTracer('order-service');

  return tracer.startActiveSpan('processOrder', async (span) => {
    span.setAttribute('order.id', orderId);

    try {
      // Validate order
      await tracer.startActiveSpan('validateOrder', async (validateSpan) => {
        // validation logic
        validateSpan.end();
      });

      // Process payment
      await tracer.startActiveSpan('processPayment', async (paymentSpan) => {
        paymentSpan.setAttribute('payment.amount', 99.99);
        // payment logic
        paymentSpan.end();
      });

      span.setStatus({ code: 1 }); // OK
      return { success: true };
    } catch (error) {
      span.recordException(error);
      span.setStatus({ code: 2, message: error.message }); // ERROR
      throw error;
    } finally {
      span.end();
    }
  });
}
```

**Trace Visualization**:
```
Request: GET /api/orders/123
Total Duration: 245ms

Service A (API Gateway)         [========================================] 245ms
  └─ Service B (Order Service)  [============================] 180ms
      ├─ Validate Order         [=====] 30ms
      ├─ Service C (Payment)    [===============] 120ms
      │   └─ Database Query     [==========] 80ms
      └─ Service D (Inventory)  [====] 30ms
```

### OpenTelemetry

**Overview**:
- Vendor-neutral standard
- Unified API for metrics, logs, traces
- Auto-instrumentation libraries
- Replaces OpenTracing + OpenCensus

**Collector Configuration**:
```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024

  resource:
    attributes:
      - key: environment
        value: production
        action: upsert

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

  prometheus:
    endpoint: 0.0.0.0:8889

  logging:
    loglevel: debug

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch, resource]
      exporters: [jaeger, logging]

    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus]
```

## Observability Patterns

### RED Method (Requests, Errors, Duration)

**For Services**:
```promql
# Rate
sum(rate(http_requests_total[5m])) by (service)

# Errors
sum(rate(http_requests_total{status_code=~"5.."}[5m])) by (service)

# Duration
histogram_quantile(0.99,
  sum(rate(http_request_duration_seconds_bucket[5m])) by (le, service))
```

### USE Method (Utilization, Saturation, Errors)

**For Resources**:
```promql
# Utilization (CPU)
avg(rate(container_cpu_usage_seconds_total[5m])) by (pod)

# Saturation (Memory pressure)
avg(container_memory_working_set_bytes / container_spec_memory_limit_bytes) by (pod)

# Errors (OOM kills)
rate(container_oom_events_total[5m])
```

### The Four Golden Signals (Google SRE)

1. **Latency**: Time to serve requests
2. **Traffic**: Demand on the system
3. **Errors**: Failed requests
4. **Saturation**: Resource utilization

## System Design Interview Questions

### Q: Design an observability system for 1000 microservices

**Requirements**:
- Metrics, logs, and traces
- Retention: 30 days
- Query latency < 1s
- Cost-effective
- High availability

**Design**:
```
┌─────────────────────────────────────────────────────────────────┐
│                     1000 Microservices                          │
│  (Each service exports metrics, logs, traces)                   │
└────┬────────────────────┬──────────────────┬─────────────────────┘
     │                    │                  │
     │ Metrics            │ Logs             │ Traces
     ▼                    ▼                  ▼
┌──────────┐         ┌──────────┐      ┌──────────┐
│Prometheus│         │  Loki    │      │  Jaeger  │
│ (Pull)   │         │ (Push)   │      │ (Push)   │
└────┬─────┘         └────┬─────┘      └────┬─────┘
     │                    │                  │
     │                    │                  │
     ▼                    ▼                  ▼
┌──────────┐         ┌──────────┐      ┌──────────┐
│  Thanos  │         │  Loki    │      │Cassandra │
│(Long-term│         │ (Object  │      │  /ES     │
│ storage) │         │ Storage) │      │          │
└────┬─────┘         └────┬─────┘      └────┬─────┘
     │                    │                  │
     └────────────────────┼──────────────────┘
                          ▼
                   ┌──────────────┐
                   │   Grafana    │
                   │ (Unified UI) │
                   └──────────────┘
```

**Key Decisions**:

1. **Metrics**: Prometheus + Thanos
   - Prometheus for short-term (2 days)
   - Thanos for long-term (S3/GCS)
   - Global view across clusters

2. **Logs**: Loki
   - Lower cost than Elasticsearch
   - Object storage backend (S3)
   - LogQL queries in Grafana

3. **Traces**: Jaeger
   - Sampling: 1% of requests
   - Storage: Cassandra (scale) or Elasticsearch
   - Head-based sampling initially, tail-based for production

4. **Cost Optimization**:
   - Metrics: 15s scrape interval
   - Logs: Compress and deduplicate
   - Traces: Adaptive sampling
   - Retention: Tier-based (hot → warm → cold)

5. **High Availability**:
   - Multi-replica Prometheus (federation)
   - Loki: Distributed mode
   - Jaeger: Collector cluster
   - Load balancing

### Q: How do you debug a latency spike in production?

**Investigation Steps**:

1. **Check Dashboards**:
```promql
# P99 latency by service
histogram_quantile(0.99,
  rate(http_request_duration_seconds_bucket[5m]))
```

2. **Identify Affected Services**:
```promql
# Services with high latency
topk(10,
  histogram_quantile(0.99,
    rate(http_request_duration_seconds_bucket[5m])))
```

3. **Check Error Rate**:
```promql
# Correlate with errors
rate(http_requests_total{status_code=~"5.."}[5m])
```

4. **Examine Traces**:
- Find slow traces in Jaeger
- Identify slow span (database, external API)
- Check span attributes

5. **Review Logs**:
```logql
{service="api"} |= "slow" | json | duration > 1000
```

6. **Check Resource Metrics**:
```promql
# CPU throttling?
rate(container_cpu_cfs_throttled_seconds_total[5m])

# Memory pressure?
container_memory_working_set_bytes / container_spec_memory_limit_bytes
```

7. **Look for External Dependencies**:
- Database slow queries
- External API latency
- Network issues

## Best Practices

### 1. Cardinality Control

**DON'T**:
```javascript
// High cardinality labels (user ID, email, etc.)
metrics.counter('requests', {
  user_id: '123456',  // ❌ Millions of unique values
  email: 'user@example.com'  // ❌
});
```

**DO**:
```javascript
// Low cardinality labels
metrics.counter('requests', {
  service: 'api',  // ✓ Few values
  method: 'POST',  // ✓
  status: '200'    // ✓
});
```

### 2. Structured Logging

```javascript
// Good structured logging
logger.info('Payment processed', {
  orderId: '123',
  amount: 99.99,
  currency: 'USD',
  duration: 125,
  success: true
});

// Bad unstructured logging
logger.info(`Payment processed for order 123 amount $99.99 in 125ms`);
```

### 3. Sampling Strategy

```javascript
// Adaptive sampling
const sampler = {
  shouldSample: (trace) => {
    // Always sample errors
    if (trace.hasError) return true;

    // Always sample slow requests
    if (trace.duration > 1000) return true;

    // Sample 1% of normal requests
    return Math.random() < 0.01;
  }
};
```

## Resources

**Tools**:
- [Prometheus](https://prometheus.io/)
- [Grafana](https://grafana.com/)
- [Jaeger](https://www.jaegertracing.io/)
- [Loki](https://grafana.com/oss/loki/)
- [OpenTelemetry](https://opentelemetry.io/)

**Learning**:
- [Google SRE Book - Monitoring](https://sre.google/sre-book/monitoring-distributed-systems/)
- [Observability Engineering (book)](https://www.honeycomb.io/observability-engineering-oreilly-book)
