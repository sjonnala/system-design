# DevOps Solutions & Tech Stack for Blob Storage Systems

## Complete Technology Stack used by Dropbox, Google Drive, OneDrive

This document covers the comprehensive DevOps solutions, tools, and technologies used in production blob storage systems at scale.

---

## 📦 Table of Contents

1. [Core Infrastructure](#core-infrastructure)
2. [Storage Technologies](#storage-technologies)
3. [Databases & Caching](#databases--caching)
4. [Messaging & Streaming](#messaging--streaming)
5. [Container Orchestration](#container-orchestration)
6. [CI/CD Pipeline](#cicd-pipeline)
7. [Monitoring & Observability](#monitoring--observability)
8. [Security & Compliance](#security--compliance)
9. [Infrastructure as Code](#infrastructure-as-code)
10. [Industry Examples](#industry-examples)

---

## 🏗️ Core Infrastructure

### Cloud Providers

**AWS (Amazon Web Services)** - Industry Standard
```yaml
Services Used:
  Storage:
    - S3: Object storage for file blocks
    - S3 Glacier: Cold storage archival
    - EBS: Block storage for databases
    - EFS: Shared file system
  
  Compute:
    - EC2: Virtual servers
    - Lambda: Serverless functions
    - ECS/EKS: Container orchestration
  
  Networking:
    - CloudFront: CDN for global distribution
    - Route 53: DNS management
    - ALB/NLB: Load balancers
    - VPC: Network isolation
  
  Database:
    - RDS (PostgreSQL/MySQL): Metadata storage
    - DynamoDB: NoSQL for high throughput
    - ElastiCache (Redis): Caching layer
```

**Google Cloud Platform (GCP)** - Google Drive's Home
```yaml
Services Used:
  Storage:
    - Cloud Storage: Object storage (equivalent to S3)
    - Persistent Disk: Block storage
    - Filestore: Managed NFS
  
  Compute:
    - Compute Engine: VMs
    - Cloud Functions: Serverless
    - GKE: Kubernetes Engine
  
  Networking:
    - Cloud CDN: Content delivery
    - Cloud Load Balancing: Global LB
    - Cloud DNS: DNS service
  
  Database:
    - Cloud SQL: Managed PostgreSQL/MySQL
    - Cloud Spanner: Globally distributed DB
    - Memorystore: Redis/Memcached
```

**Azure** - Microsoft OneDrive
```yaml
Services Used:
  Storage:
    - Azure Blob Storage: Object storage
    - Azure Files: SMB file shares
    - Azure Disk Storage: Block storage
  
  Compute:
    - Virtual Machines
    - Azure Functions
    - AKS: Kubernetes service
  
  Networking:
    - Azure CDN
    - Azure Front Door: Global LB
    - Azure DNS
  
  Database:
    - Azure Database for PostgreSQL
    - Cosmos DB: NoSQL
    - Azure Cache for Redis
```

### Multi-Cloud Strategy

**Why Multi-Cloud?**
- Avoid vendor lock-in
- Better global coverage
- Cost optimization
- Disaster recovery

**Real Example: Dropbox**
```
2015: Moved from AWS to own data centers ("Magic Pocket")
Reasons:
  - Cost: 50% reduction ($75M → $39M annually)
  - Control: Custom hardware and software
  - Scale: 500+ Petabytes of data
  
Architecture:
  - Custom SMR drives for storage
  - Self-built Reed-Solomon erasure coding
  - Still uses AWS for:
    - CloudFront (CDN)
    - Route 53 (DNS)
    - EC2 for burst capacity
```

---

## 💾 Storage Technologies

### Object Storage

**Amazon S3** - Gold Standard
```yaml
Features:
  - 99.999999999% (11 9's) durability
  - Automatic tiering (Standard, IA, Glacier)
  - Versioning and lifecycle policies
  - Event notifications (Lambda triggers)
  - Encryption at rest (SSE-S3, SSE-KMS)
  
Storage Classes:
  S3 Standard:         $0.023/GB/month (hot data)
  S3 Intelligent-Tiering: Auto-tiering
  S3 Standard-IA:      $0.0125/GB/month (warm)
  S3 Glacier Flexible: $0.004/GB/month (cold)
  S3 Glacier Deep Archive: $0.00099/GB/month (archive)
  
Best Practices:
  - Use S3 Transfer Acceleration for uploads
  - Enable S3 Intelligent-Tiering for automatic cost optimization
  - Use multipart upload for files >100MB
  - Implement lifecycle policies for automatic tiering
```

**Google Cloud Storage**
```yaml
Storage Classes:
  Standard:    $0.020/GB/month
  Nearline:    $0.010/GB/month (30-day minimum)
  Coldline:    $0.004/GB/month (90-day minimum)
  Archive:     $0.0012/GB/month (365-day minimum)
  
Features:
  - Lifecycle management
  - Object versioning
  - Pub/Sub notifications
  - Customer-managed encryption keys
```

**Azure Blob Storage**
```yaml
Tiers:
  Hot:      $0.018/GB/month
  Cool:     $0.010/GB/month
  Archive:  $0.00099/GB/month
  
Features:
  - Immutable storage (WORM)
  - Soft delete
  - Change feed
  - Azure AD integration
```

### Block Storage

**Chunking Technologies**
```python
# Rabin Fingerprinting (Content-Defined Chunking)
# Used by: Dropbox, Resilio Sync

class RabinChunker:
    def __init__(self, avg_chunk_size=4*1024*1024):
        self.window_size = 48
        self.avg_chunk_size = avg_chunk_size
        self.mask = (1 << 13) - 1  # Chunk boundary trigger
    
    def chunk(self, data):
        """
        Variable-size chunks for better deduplication
        """
        chunks = []
        chunk_start = 0
        
        for i in range(self.window_size, len(data)):
            hash_val = self.rabin_hash(data[i-self.window_size:i])
            
            if (hash_val & self.mask == 0) or \
               (i - chunk_start >= 8 * self.avg_chunk_size):
                chunk = data[chunk_start:i]
                chunks.append(chunk)
                chunk_start = i
        
        return chunks
```

**Deduplication Technologies**
```yaml
File-Level Dedup:
  - Simple: Hash entire file (SHA-256)
  - Storage savings: 20-30%
  - Used by: Early Dropbox, Google Drive

Chunk-Level Dedup:
  - Advanced: Hash each chunk (4MB blocks)
  - Storage savings: 50-70%
  - Used by: Modern Dropbox, Backblaze B2
  
Cross-User Dedup:
  - Privacy concerns: Can reveal file existence
  - Solutions:
    - Convergent encryption
    - Proof-of-ownership protocols
  - Storage savings: 60-80% (enterprise)
```

---

## 🗄️ Databases & Caching

### Relational Databases

**PostgreSQL** - Most Popular for Metadata
```yaml
Version: 15+
Deployment: AWS RDS, Google Cloud SQL, or self-managed

Schema Example:
  Tables:
    - users: User accounts
    - files: File metadata
    - chunks: Chunk information
    - shares: Sharing permissions
    - versions: Version history
  
Sharding Strategy:
  - Shard by user_id (data locality)
  - 64 shards initially
  - Each shard: 16TB max
  - Tools: Citus, pg_shard, or application-level
  
Optimizations:
  - Connection pooling (PgBouncer)
  - Read replicas (3-5 per master)
  - Materialized views for analytics
  - Partitioning by created_at (monthly)
  
Extensions:
  - pg_stat_statements: Query performance
  - pg_repack: Online table reorganization
  - pgcrypto: Encryption functions
```

**MySQL** - Alternative
```yaml
Version: 8.0+
Deployment: AWS RDS, Azure Database, or self-managed

Sharding:
  - ProxySQL for query routing
  - Vitess for horizontal scaling (YouTube uses this)
  - MySQL Group Replication

InnoDB Settings:
  innodb_buffer_pool_size: 70-80% of RAM
  innodb_log_file_size: 1-2GB
  innodb_flush_log_at_trx_commit: 2 (for performance)
```

### NoSQL Databases

**Cassandra** - for Time-Series Data
```yaml
Use Cases:
  - User timelines (activity history)
  - File access logs
  - Analytics events

Schema Example:
  CREATE TABLE file_access_log (
      user_id bigint,
      access_timestamp timestamp,
      file_id uuid,
      action text,  -- 'read', 'write', 'delete'
      PRIMARY KEY (user_id, access_timestamp)
  ) WITH CLUSTERING ORDER BY (access_timestamp DESC);

Deployment:
  - Nodes: 9+ (3 per datacenter, 3 DCs)
  - Replication Factor: 3
  - Consistency: Quorum (R=2, W=2)
  
Performance:
  - Writes: 100K ops/sec per node
  - Reads: 50K ops/sec per node
```

**DynamoDB** - for High-Throughput Metadata
```yaml
Use Cases:
  - Session management
  - User preferences
  - Real-time sync state

Table Example:
  users_table:
    Partition Key: user_id
    Sort Key: timestamp
    GSI: email-index (for login)
    
  sync_state_table:
    Partition Key: device_id
    Attributes: last_cursor, last_sync_at
    TTL: 30 days (auto-cleanup)

Pricing:
  - On-Demand: $1.25/million writes, $0.25/million reads
  - Provisioned: $0.65/WCU/month, $0.13/RCU/month
```

### Caching

**Redis** - In-Memory Cache
```yaml
Version: 7.0+
Deployment: AWS ElastiCache, Redis Enterprise

Architecture:
  - Redis Cluster (6 nodes: 3 master + 3 replica)
  - Sharding: Hash slots (16,384 slots)
  - Persistence: AOF (Append-Only File)

Data Structures Used:
  Strings:
    - SET session:{token} {user_id} EX 86400
    - GET chunk:{hash}
  
  Hashes:
    - HSET file:{file_id} name "doc.pdf" size 1024
    - HGETALL file:{file_id}
  
  Sorted Sets (for recent files):
    - ZADD recent:{user_id} {timestamp} {file_id}
    - ZREVRANGE recent:{user_id} 0 10
  
  Sets (for user's files):
    - SADD user:{user_id}:files {file_id}
    - SMEMBERS user:{user_id}:files

Memory Management:
  maxmemory: 70% of available RAM
  maxmemory-policy: allkeys-lru
  eviction: Least Recently Used

Performance:
  - Throughput: 100K ops/sec per instance
  - Latency: <1ms (P99)
  - Cache hit rate: 90%+
```

**Memcached** - Simple Alternative
```yaml
Use Cases:
  - Session caching
  - Simple key-value pairs
  - When Redis features not needed

Deployment:
  - AWS ElastiCache
  - Multiple nodes (10+)
  - No persistence (pure cache)

Performance:
  - Throughput: 1M ops/sec per node
  - Latency: <1ms
  - Memory efficient
```

---

## 📨 Messaging & Streaming

### Apache Kafka

**Industry Standard for Event Streaming**
```yaml
Version: 3.5+
Deployment: Confluent Cloud, AWS MSK, or self-managed

Architecture:
  Brokers: 9+ (3 per AZ, 3 AZs)
  Replication Factor: 3
  Partitions per topic: 30-100 (for parallelism)

Topics:
  file.uploaded:
    Partitions: 50
    Retention: 7 days
    Producer: Upload Service
    Consumers: Sync Service, Search Indexer, Analytics
  
  file.modified:
    Partitions: 50
    Retention: 7 days
    
  sync.trigger:
    Partitions: 100 (high throughput)
    Retention: 1 day

Configuration:
  min.insync.replicas: 2
  acks: all (for durability)
  compression.type: snappy
  batch.size: 16KB (for throughput)

Monitoring:
  - Kafka Manager (CMAK)
  - Confluent Control Center
  - Burrow (consumer lag monitoring)
```

**Kafka Alternatives**

**RabbitMQ** - for Complex Routing
```yaml
Use Cases:
  - When need complex routing (topic, fanout)
  - Lower throughput requirements
  - Message acknowledgments critical

Features:
  - Message queues
  - Pub/Sub
  - RPC patterns
  - Management UI

Performance:
  - Throughput: 20K msgs/sec
  - Latency: 1-5ms
```

**AWS SQS** - Managed Queue
```yaml
Use Cases:
  - Serverless architecture
  - Don't want to manage Kafka
  - Async task processing

Types:
  Standard Queue:
    - Unlimited throughput
    - At-least-once delivery
    - Best-effort ordering
  
  FIFO Queue:
    - Up to 3,000 msgs/sec
    - Exactly-once delivery
    - Strict ordering

Pricing:
  - $0.40/million requests
  - First 1M requests/month free
```

---

## ☸️ Container Orchestration

### Kubernetes (K8s)

**EKS / GKE / AKS**
```yaml
Cluster Setup:
  Node Pools:
    - api-pool: For API services (m5.xlarge, 10-50 nodes)
    - upload-pool: For uploads (c5.2xlarge, 20-100 nodes)
    - worker-pool: For async jobs (t3.large, 5-20 nodes)
  
  Namespaces:
    - production
    - staging
    - development

Service Deployment Example:
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: upload-service
  namespace: production
spec:
  replicas: 20
  selector:
    matchLabels:
      app: upload-service
  template:
    metadata:
      labels:
        app: upload-service
    spec:
      containers:
      - name: upload
        image: dropbox/upload-service:v1.2.3
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "2Gi"
            cpu: "1000m"
          limits:
            memory: "4Gi"
            cpu: "2000m"
        env:
        - name: S3_BUCKET
          value: "dropbox-blocks"
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-secret
              key: host
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
---
apiVersion: v1
kind: Service
metadata:
  name: upload-service
spec:
  selector:
    app: upload-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: upload-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: upload-service
  minReplicas: 20
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
```

**Service Mesh - Istio**
```yaml
Why Use Istio:
  - Traffic management (canary, blue-green)
  - Security (mTLS between services)
  - Observability (distributed tracing)
  - Fault injection for testing

Example Traffic Split (Canary):
---
apiVersion: networking.istio.io/v1alpha3
kind: VirtualService
metadata:
  name: upload-service
spec:
  hosts:
  - upload-service
  http:
  - match:
    - headers:
        x-canary:
          exact: "true"
    route:
    - destination:
        host: upload-service
        subset: v2
  - route:
    - destination:
        host: upload-service
        subset: v1
      weight: 90
    - destination:
        host: upload-service
        subset: v2
      weight: 10
```

---

## 🚀 CI/CD Pipeline

### CI/CD Tools

**Jenkins** - Most Flexible
```groovy
pipeline {
    agent any
    
    stages {
        stage('Build') {
            steps {
                sh 'docker build -t dropbox/upload-service:${BUILD_NUMBER} .'
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'go test ./... -v'
                    }
                }
                stage('Integration Tests') {
                    steps {
                        sh 'make integration-test'
                    }
                }
            }
        }
        
        stage('Security Scan') {
            steps {
                sh 'trivy image dropbox/upload-service:${BUILD_NUMBER}'
            }
        }
        
        stage('Push to Registry') {
            steps {
                sh 'docker push dropbox/upload-service:${BUILD_NUMBER}'
            }
        }
        
        stage('Deploy to Staging') {
            steps {
                sh 'kubectl set image deployment/upload-service upload=dropbox/upload-service:${BUILD_NUMBER} -n staging'
            }
        }
        
        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                input 'Deploy to production?'
                sh 'kubectl set image deployment/upload-service upload=dropbox/upload-service:${BUILD_NUMBER} -n production'
            }
        }
    }
    
    post {
        failure {
            slackSend(color: 'danger', message: "Build failed: ${env.JOB_NAME} ${env.BUILD_NUMBER}")
        }
        success {
            slackSend(color: 'good', message: "Build successful: ${env.JOB_NAME} ${env.BUILD_NUMBER}")
        }
    }
}
```

**GitHub Actions** - Native GitHub Integration
```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up Go
      uses: actions/setup-go@v4
      with:
        go-version: '1.21'
    
    - name: Run tests
      run: go test -v ./...
    
    - name: Build Docker image
      run: docker build -t dropbox/upload-service:${{ github.sha }} .
    
    - name: Push to ECR
      uses: aws-actions/amazon-ecr-login@v1
      with:
        registry: ${{ secrets.ECR_REGISTRY }}
    
    - name: Deploy to EKS
      uses: aws-actions/amazon-eks-update-kubeconfig@v1
      with:
        cluster-name: production
        role-arn: ${{ secrets.EKS_ROLE_ARN }}
    
    - name: Update deployment
      run: |
        kubectl set image deployment/upload-service \
          upload=dropbox/upload-service:${{ github.sha }} \
          -n production
```

**GitLab CI/CD**
```yaml
stages:
  - build
  - test
  - deploy

build:
  stage: build
  script:
    - docker build -t $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA .
    - docker push $CI_REGISTRY_IMAGE:$CI_COMMIT_SHA

test:
  stage: test
  script:
    - go test -v ./...
    - make integration-test

deploy_staging:
  stage: deploy
  script:
    - kubectl set image deployment/upload-service upload=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA -n staging
  environment:
    name: staging
  only:
    - develop

deploy_production:
  stage: deploy
  script:
    - kubectl set image deployment/upload-service upload=$CI_REGISTRY_IMAGE:$CI_COMMIT_SHA -n production
  environment:
    name: production
  when: manual
  only:
    - main
```

---

## 📊 Monitoring & Observability

### Metrics - Prometheus + Grafana

**Prometheus Setup**
```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'upload-service'
    kubernetes_sd_configs:
    - role: pod
    relabel_configs:
    - source_labels: [__meta_kubernetes_pod_label_app]
      action: keep
      regex: upload-service

  - job_name: 'kubernetes-nodes'
    kubernetes_sd_configs:
    - role: node
```

**Key Metrics to Track**
```promql
# Upload throughput
rate(upload_bytes_total[5m])

# Upload latency (P95)
histogram_quantile(0.95, 
  rate(upload_duration_seconds_bucket[5m]))

# Error rate
rate(upload_errors_total[5m]) / rate(upload_requests_total[5m])

# Storage usage
storage_used_bytes / storage_capacity_bytes

# Cache hit ratio
redis_keyspace_hits / (redis_keyspace_hits + redis_keyspace_misses)

# Queue lag
kafka_consumer_lag_seconds
```

**Grafana Dashboards**
```
Dashboards:
  1. Overview Dashboard
     - Total users
     - Storage used
     - QPS (upload, download, sync)
     - Error rates
  
  2. Service Health
     - Per-service QPS
     - Latency (P50, P95, P99)
     - Error rate
     - Pod count
  
  3. Infrastructure
     - CPU, memory, disk usage
     - Network throughput
     - Database connections
     - Cache hit ratio
  
  4. Business Metrics
     - New users/day
     - Files uploaded/day
     - Storage growth
     - Revenue (paid users)
```

### Logging - ELK Stack

**Elasticsearch + Logstash + Kibana**
```yaml
Elasticsearch:
  Version: 8.x
  Cluster: 9 nodes (3 master, 6 data)
  Indices:
    - app-logs-*: Application logs
    - access-logs-*: API access logs
    - error-logs-*: Error logs
  Retention: 30 days
  Snapshot: Daily to S3

Logstash Pipeline:
  input {
    kafka {
      bootstrap_servers => "kafka:9092"
      topics => ["logs"]
      codec => json
    }
  }
  
  filter {
    if [level] == "ERROR" {
      grok {
        match => { "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:error_message}" }
      }
    }
  }
  
  output {
    elasticsearch {
      hosts => ["elasticsearch:9200"]
      index => "app-logs-%{+YYYY.MM.dd}"
    }
  }

Kibana:
  Dashboards:
    - Error rate trends
    - Top errors by service
    - Slow query analysis
    - User activity logs
```

### Tracing - Jaeger

**Distributed Tracing Setup**
```yaml
Services instrumented with OpenTelemetry:
  - Upload Service
  - Download Service
  - Metadata Service
  - Sync Service

Trace Example:
  Span 1: API Gateway (10ms)
    Span 2: Auth check (5ms)
    Span 3: Upload Service (200ms)
      Span 4: Chunk dedup check (20ms)
      Span 5: S3 upload (150ms)
      Span 6: Metadata DB write (30ms)

Jaeger UI:
  - View request flow across services
  - Identify bottlenecks
  - Debug latency issues
```

### Alerting - PagerDuty / OpsGenie

**Alert Rules**
```yaml
Critical Alerts (Page immediately):
  - API error rate > 5% for 5 minutes
  - Database CPU > 90% for 10 minutes
  - Storage capacity > 85%
  - Upload service down (all pods)

Warning Alerts (Slack notification):
  - API latency P95 > 500ms for 15 minutes
  - Cache hit ratio < 80% for 30 minutes
  - Kafka consumer lag > 1 hour
  - Disk usage > 70%

Info Alerts (Email):
  - Daily summary of metrics
  - Weekly capacity planning report
  - Monthly cost analysis
```

---

## 🔒 Security & Compliance

### Security Tools

**Vulnerability Scanning**
```bash
# Docker image scanning
trivy image dropbox/upload-service:latest

# Kubernetes security
kube-bench  # CIS Kubernetes benchmark

# Secret scanning
git-secrets  # Prevent committing secrets
truffleHog   # Find secrets in git history
```

**Secrets Management**
```yaml
HashiCorp Vault:
  Features:
    - Dynamic secrets
    - Secret rotation
    - Encryption as a service
    - Audit logging
  
  Usage:
    vault kv put secret/db/password value=supersecret
    vault kv get secret/db/password

AWS Secrets Manager:
  - Automatic rotation
  - Integration with RDS
  - Fine-grained IAM policies

Kubernetes Secrets:
  - Store in etcd (encrypted)
  - Mount as environment variables
  - Use External Secrets Operator for Vault integration
```

**Compliance**
```yaml
Regulations:
  GDPR (EU):
    - Data residency (store EU data in EU)
    - Right to be forgotten (delete user data)
    - Data portability (export user data)
  
  HIPAA (Healthcare):
    - Encryption at rest and in transit
    - Access logging and auditing
    - Business Associate Agreements (BAA)
  
  SOC 2:
    - Security policies
    - Access controls
    - Incident response
    - Audit trail

Tools:
  - AWS Config: Compliance monitoring
  - CloudHealth: Multi-cloud compliance
  - Qualys: Vulnerability management
```

---

## 🏗️ Infrastructure as Code

### Terraform

**S3 Bucket + CloudFront Setup**
```hcl
# s3.tf
resource "aws_s3_bucket" "blocks" {
  bucket = "dropbox-blocks-prod"
  
  versioning {
    enabled = true
  }
  
  lifecycle_rule {
    enabled = true
    
    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }
    
    transition {
      days          = 90
      storage_class = "GLACIER"
    }
  }
  
  server_side_encryption_configuration {
    rule {
      apply_server_side_encryption_by_default {
        sse_algorithm = "AES256"
      }
    }
  }
}

# cloudfront.tf
resource "aws_cloudfront_distribution" "cdn" {
  origin {
    domain_name = aws_s3_bucket.blocks.bucket_regional_domain_name
    origin_id   = "S3-dropbox-blocks"
    
    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.oai.cloudfront_access_identity_path
    }
  }
  
  enabled             = true
  is_ipv6_enabled     = true
  price_class         = "PriceClass_All"
  
  default_cache_behavior {
    allowed_methods        = ["GET", "HEAD"]
    cached_methods         = ["GET", "HEAD"]
    target_origin_id       = "S3-dropbox-blocks"
    viewer_protocol_policy = "redirect-to-https"
    
    min_ttl     = 0
    default_ttl = 86400
    max_ttl     = 31536000
    
    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }
  }
}

# eks.tf
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"
  
  cluster_name    = "dropbox-prod"
  cluster_version = "1.28"
  
  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets
  
  eks_managed_node_groups = {
    api = {
      min_size     = 10
      max_size     = 50
      desired_size = 20
      
      instance_types = ["m5.xlarge"]
      capacity_type  = "ON_DEMAND"
    }
    
    upload = {
      min_size     = 20
      max_size     = 100
      desired_size = 30
      
      instance_types = ["c5.2xlarge"]
      capacity_type  = "SPOT"
    }
  }
}
```

---

## 🏢 Industry Examples

### Dropbox Tech Stack (2025)

```yaml
Storage:
  - Magic Pocket: Custom-built (500+ PB)
  - AWS S3: Backup and auxiliary
  - SMR drives: High-density storage
  
Compute:
  - Own data centers (9 locations)
  - Python: Backend services
  - Go: Performance-critical services
  - React: Web frontend
  
Databases:
  - MySQL: Metadata (sharded)
  - EdgeStore: Custom metadata store
  - Memcached: Caching
  
Infrastructure:
  - Kubernetes: Container orchestration
  - Terraform: Infrastructure as code
  - Jenkins: CI/CD
  
Monitoring:
  - Prometheus + Grafana
  - ELK Stack
  - Jaeger: Distributed tracing
```

### Google Drive Tech Stack

```yaml
Storage:
  - Colossus: Google's distributed file system
  - Google Cloud Storage
  
Compute:
  - Google Compute Engine
  - Google Kubernetes Engine
  - C++/Java/Python: Backend
  - Angular: Web frontend
  
Databases:
  - Spanner: Globally distributed SQL
  - Bigtable: NoSQL for logs
  - Memcache: Caching
  
Infrastructure:
  - Borg: Container orchestration (internal K8s)
  - Bazel: Build system
  - Internal CI/CD tools
  
Monitoring:
  - Borgmon: Internal monitoring
  - Dapper: Distributed tracing
```

### Microsoft OneDrive Tech Stack

```yaml
Storage:
  - Azure Blob Storage
  - Azure Files
  
Compute:
  - Azure VMs
  - Azure Kubernetes Service
  - C#/.NET: Backend
  - TypeScript/React: Frontend
  
Databases:
  - Azure SQL Database
  - Cosmos DB
  - Azure Cache for Redis
  
Infrastructure:
  - Azure DevOps: CI/CD
  - ARM templates: Infrastructure
  - Azure Monitor: Monitoring
```

---

## 🎯 Key Takeaways

1. **Storage**: S3/GCS/Azure Blob + custom solutions for scale
2. **Chunking**: Rabin fingerprinting for 50% dedup savings
3. **Databases**: PostgreSQL (sharded) + Redis (cache) + Cassandra (logs)
4. **Messaging**: Kafka for event streaming
5. **Orchestration**: Kubernetes (EKS/GKE/AKS)
6. **CI/CD**: Jenkins/GitHub Actions/GitLab CI
7. **Monitoring**: Prometheus + Grafana + ELK + Jaeger
8. **Security**: Vault + IAM + encryption everywhere
9. **IaC**: Terraform for reproducible infrastructure

**Cost Optimization:**
- Deduplication: 50% storage savings
- Compression: 30% additional savings
- Tiering: 50% cost reduction (hot/warm/cold)
- Spot instances: 70% compute cost savings
- CDN: 99% offload, 99% cost savings on bandwidth

---

**Remember:** The best tech stack is one that fits YOUR scale, team expertise, and budget!

🚀 **Start simple, scale as needed!**
