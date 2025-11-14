# DevOps Stack & Tech Solutions for Video Streaming Platforms

Comprehensive guide to the DevOps tools, infrastructure, and tech stacks used by Netflix, YouTube, and other major streaming platforms.

---

## Table of Contents
1. [Cloud Infrastructure](#cloud-infrastructure)
2. [Storage Solutions](#storage-solutions)
3. [Container Orchestration](#container-orchestration)
4. [CI/CD Pipelines](#cicd-pipelines)
5. [Monitoring & Observability](#monitoring--observability)
6. [Message Queues & Streaming](#message-queues--streaming)
7. [Databases](#databases)
8. [CDN & Edge Computing](#cdn--edge-computing)
9. [Video Encoding Tools](#video-encoding-tools)
10. [Security & Compliance](#security--compliance)
11. [Infrastructure as Code](#infrastructure-as-code)
12. [Real Industry Examples](#real-industry-examples)

---

## Cloud Infrastructure

### AWS (Amazon Web Services)

#### Compute
```yaml
Services:
  EC2:
    - Purpose: Application servers, transcoding workers
    - Instance Types:
        API Servers: c5.2xlarge (8 vCPU, 16 GB RAM)
        Transcoding: c5.9xlarge (36 vCPU, 72 GB RAM)
        GPU Encoding: p3.2xlarge (1 V100 GPU)
    - Auto Scaling Groups for dynamic capacity
  
  Lambda:
    - Purpose: Serverless functions for upload callbacks, thumbnail generation
    - Triggers: S3 events, API Gateway, EventBridge
    - Runtime: Python 3.11, Node.js 18
  
  ECS/EKS:
    - Purpose: Container orchestration
    - ECS: Fargate for serverless containers
    - EKS: Kubernetes for complex microservices

Networking:
  VPC:
    - Multi-AZ deployment (us-east-1a, us-east-1b, us-east-1c)
    - Public subnets for load balancers
    - Private subnets for application/database servers
    - NAT Gateways for outbound internet access
  
  ALB (Application Load Balancer):
    - Layer 7 load balancing
    - Path-based routing (/api/v1/videos → video-service)
    - SSL termination
    - Health checks
  
  CloudFront:
    - CDN for video delivery
    - 450+ edge locations globally
    - Origin shield to reduce origin load
    - Lambda@Edge for request/response manipulation
  
  Route 53:
    - DNS management
    - GeoDNS routing to nearest region
    - Health checks and failover
```

**Cost Optimization:**
```yaml
Strategies:
  - Spot Instances for transcoding (70% savings)
  - Reserved Instances for baseline capacity (up to 72% savings)
  - S3 Intelligent-Tiering (automatic cost optimization)
  - CloudFront cost optimization via Open Connect-style approach
```

### Google Cloud Platform (GCP)

```yaml
Compute:
  GCE (Compute Engine):
    - Similar to EC2
    - n1-standard, n2-standard series
    - Preemptible VMs (like Spot Instances)
  
  GKE (Google Kubernetes Engine):
    - Managed Kubernetes
    - Autopilot mode (fully managed)
    - Multi-cluster service mesh

Storage:
  Cloud Storage:
    - Multi-Regional: Hot content ($0.026/GB)
    - Nearline: Warm content ($0.010/GB)
    - Coldline: Archive ($0.004/GB)
  
  Persistent Disks:
    - SSD for databases
    - Standard for backups

Networking:
  Cloud CDN:
    - Integrated with Cloud Storage
    - Anycast IP for global load balancing
  
  Cloud Load Balancing:
    - Global HTTP(S) load balancer
    - SSL certificates (managed)

Media Services:
  Transcoder API:
    - Managed video transcoding
    - $0.015 per minute (HD), $0.03 per minute (4K)
    - Automatic scaling
  
  Live Stream API:
    - Real-time streaming
    - Low-latency delivery
```

### Microsoft Azure

```yaml
Compute:
  Virtual Machines:
    - D-series (general purpose)
    - F-series (compute optimized)
    - N-series (GPU for encoding)
  
  AKS (Azure Kubernetes Service):
    - Managed Kubernetes
    - Azure AD integration
    - Virtual nodes (serverless)

Storage:
  Azure Blob Storage:
    - Hot tier: Frequently accessed
    - Cool tier: Infrequently accessed (30 days)
    - Archive tier: Rarely accessed (180 days)
  
  Azure Files:
    - SMB/NFS file shares
    - For shared transcoding workflows

Media Services:
  Azure Media Services:
    - Complete video platform
    - Encoding, packaging, DRM, streaming
    - LiveEvent for live streaming
    - StreamingEndpoint for CDN

Networking:
  Azure CDN:
    - Microsoft CDN, Akamai, Verizon options
    - Dynamic site acceleration
  
  Azure Front Door:
    - Global load balancer
    - Web Application Firewall (WAF)
```

---

## Storage Solutions

### Object Storage

#### AWS S3
```yaml
Pricing (as of 2024):
  S3 Standard: $0.023/GB/month
  S3 Intelligent-Tiering: $0.023/GB (frequent) + $0.0125 (infrequent)
  S3 Glacier Instant Retrieval: $0.004/GB/month
  S3 Glacier Deep Archive: $0.00099/GB/month

Transfer Costs:
  Upload: Free
  Download (to internet): $0.09/GB (first 10 TB)
  CloudFront to origin: Free

Use Cases:
  - S3 Standard: Recently uploaded videos, trending content
  - Intelligent-Tiering: Videos with unpredictable access patterns
  - Glacier: Old content, rarely watched (>6 months)

Configuration:
  Lifecycle Policy:
    - Move to IA after 30 days
    - Move to Glacier after 90 days
    - Delete after 7 years (compliance)
```

**S3 Lifecycle Policy Example:**
```json
{
  "Rules": [
    {
      "Id": "MoveToIA",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 90,
          "StorageClass": "GLACIER_IR"
        },
        {
          "Days": 365,
          "StorageClass": "DEEP_ARCHIVE"
        }
      ]
    }
  ]
}
```

#### Google Cloud Storage
```yaml
Pricing:
  Multi-Regional: $0.026/GB/month (hot content)
  Nearline: $0.010/GB/month (30-day minimum)
  Coldline: $0.004/GB/month (90-day minimum)
  Archive: $0.0012/GB/month (365-day minimum)

Features:
  - Automatic lifecycle management
  - Object versioning
  - Requester pays
  - Strong consistency

YouTube Use Case:
  - Multi-Regional for new uploads
  - Nearline for videos older than 1 month
  - Coldline for rarely watched content
```

#### Custom Storage (Netflix)

**Netflix Open Connect Storage:**
```yaml
Hardware:
  - Custom FreeBSD servers
  - 100-200 TB SSD per server
  - 10-40 Gbps network cards
  - Low-power, high-density chassis

Deployment:
  - 17,000+ servers globally
  - Placed at ISP data centers (free to ISPs)
  - Reduces transit costs by 95%

Software:
  - FreeBSD-based OS
  - Nginx for HTTP serving
  - Custom caching algorithms
  - Overnight content prefetch

Cost Savings:
  - Own CDN: $6.4M/month
  - vs AWS CloudFront: $135M/month
  - Savings: ~95% at scale
```

---

## Container Orchestration

### Kubernetes (K8s)

**Architecture for Streaming Platform:**
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: video-platform

---
# Video Upload Service Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: upload-service
  namespace: video-platform
spec:
  replicas: 10
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
        image: video-platform/upload-service:v2.3.1
        ports:
        - containerPort: 8080
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1000m"
        env:
        - name: S3_BUCKET
          value: "video-uploads"
        - name: KAFKA_BROKERS
          value: "kafka-0.kafka:9092,kafka-1.kafka:9092"
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
# Horizontal Pod Autoscaler
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: upload-service-hpa
  namespace: video-platform
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: upload-service
  minReplicas: 10
  maxReplicas: 100
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

---
# Service (Load Balancer)
apiVersion: v1
kind: Service
metadata:
  name: upload-service
  namespace: video-platform
spec:
  selector:
    app: upload-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer

---
# Transcoding Worker (Batch Job)
apiVersion: batch/v1
kind: Job
metadata:
  name: transcode-job-12345
  namespace: video-platform
spec:
  parallelism: 6  # 6 resolutions in parallel
  completions: 6
  template:
    spec:
      containers:
      - name: transcoder
        image: video-platform/transcoder:v1.5.0
        resources:
          requests:
            memory: "8Gi"
            cpu: "4000m"
          limits:
            memory: "16Gi"
            cpu: "8000m"
        env:
        - name: VIDEO_ID
          value: "550e8400-e29b-41d4-a716-446655440000"
        - name: PROFILE
          value: "1080p"
      restartPolicy: OnFailure
      nodeSelector:
        workload: compute-intensive
```

**Kubernetes Cluster Setup:**
```yaml
Cluster Configuration:
  Master Nodes: 3 (HA setup)
  Worker Nodes:
    - API Services: 20× c5.2xlarge (8 vCPU, 16 GB)
    - Transcoding: 50× c5.9xlarge (36 vCPU, 72 GB) - Spot instances
    - Database: 10× r5.4xlarge (16 vCPU, 128 GB)
  
  Node Pools:
    - api-pool: General microservices
    - transcoding-pool: Video encoding (spot instances)
    - database-pool: Cassandra, PostgreSQL
    - monitoring-pool: Prometheus, Grafana, ELK

Networking:
  CNI: Calico (network policies)
  Ingress: Nginx Ingress Controller
  Service Mesh: Istio (traffic management, observability)

Storage:
  StorageClass:
    - gp3: General purpose SSD
    - io2: High-performance database volumes
    - efs: Shared file storage (for temp transcoding files)
```

### Docker

**Dockerfile for Upload Service:**
```dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy application code
COPY . .

# Create non-root user
RUN useradd -m -u 1000 appuser && chown -R appuser:appuser /app
USER appuser

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/health || exit 1

# Run application
CMD ["gunicorn", "--bind", "0.0.0.0:8080", "--workers", "4", "app:app"]
```

**Docker Compose for Local Development:**
```yaml
version: '3.8'

services:
  upload-service:
    build: ./services/upload
    ports:
      - "8080:8080"
    environment:
      - S3_ENDPOINT=http://minio:9000
      - KAFKA_BROKERS=kafka:9092
    depends_on:
      - kafka
      - minio

  playback-service:
    build: ./services/playback
    ports:
      - "8081:8080"
    environment:
      - CDN_URL=http://localhost:9000
      - REDIS_URL=redis://redis:6379

  postgres:
    image: postgres:15
    environment:
      POSTGRES_PASSWORD: password
    volumes:
      - postgres_data:/var/lib/postgresql/data

  cassandra:
    image: cassandra:4.1
    ports:
      - "9042:9042"
    volumes:
      - cassandra_data:/var/lib/cassandra

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  kafka:
    image: confluentinc/cp-kafka:7.5.0
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    ports:
      - "9000:9000"
      - "9001:9001"
    environment:
      MINIO_ROOT_USER: admin
      MINIO_ROOT_PASSWORD: password
    volumes:
      - minio_data:/data

volumes:
  postgres_data:
  cassandra_data:
  minio_data:
```

---

## CI/CD Pipelines

### Jenkins Pipeline

```groovy
pipeline {
    agent any
    
    environment {
        DOCKER_REGISTRY = 'docker.io/video-platform'
        AWS_REGION = 'us-east-1'
        EKS_CLUSTER = 'video-platform-prod'
        SLACK_CHANNEL = '#deployments'
    }
    
    stages {
        stage('Checkout') {
            steps {
                git branch: 'main', url: 'https://github.com/company/video-platform.git'
            }
        }
        
        stage('Build') {
            parallel {
                stage('Upload Service') {
                    steps {
                        dir('services/upload') {
                            sh 'docker build -t ${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER} .'
                            sh 'docker tag ${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER} ${DOCKER_REGISTRY}/upload-service:latest'
                        }
                    }
                }
                stage('Playback Service') {
                    steps {
                        dir('services/playback') {
                            sh 'docker build -t ${DOCKER_REGISTRY}/playback-service:${BUILD_NUMBER} .'
                        }
                    }
                }
                stage('Transcoding Worker') {
                    steps {
                        dir('services/transcoder') {
                            sh 'docker build -t ${DOCKER_REGISTRY}/transcoder:${BUILD_NUMBER} .'
                        }
                    }
                }
            }
        }
        
        stage('Test') {
            parallel {
                stage('Unit Tests') {
                    steps {
                        sh 'pytest tests/unit --junitxml=reports/unit-tests.xml'
                    }
                }
                stage('Integration Tests') {
                    steps {
                        sh 'docker-compose -f docker-compose.test.yml up --abort-on-container-exit'
                    }
                }
                stage('Security Scan') {
                    steps {
                        sh 'trivy image ${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER}'
                    }
                }
            }
        }
        
        stage('Push Images') {
            when {
                branch 'main'
            }
            steps {
                withCredentials([usernamePassword(credentialsId: 'docker-hub', usernameVariable: 'USER', passwordVariable: 'PASS')]) {
                    sh 'echo $PASS | docker login -u $USER --password-stdin'
                    sh 'docker push ${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER}'
                    sh 'docker push ${DOCKER_REGISTRY}/upload-service:latest'
                    sh 'docker push ${DOCKER_REGISTRY}/playback-service:${BUILD_NUMBER}'
                    sh 'docker push ${DOCKER_REGISTRY}/transcoder:${BUILD_NUMBER}'
                }
            }
        }
        
        stage('Deploy to Staging') {
            when {
                branch 'main'
            }
            steps {
                script {
                    sh 'aws eks update-kubeconfig --name ${EKS_CLUSTER} --region ${AWS_REGION}'
                    sh 'kubectl set image deployment/upload-service upload=${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER} -n staging'
                    sh 'kubectl rollout status deployment/upload-service -n staging --timeout=5m'
                }
            }
        }
        
        stage('Smoke Tests') {
            steps {
                sh 'pytest tests/smoke --base-url=https://staging.video-platform.com'
            }
        }
        
        stage('Deploy to Production') {
            when {
                branch 'main'
            }
            steps {
                input message: 'Deploy to production?', ok: 'Deploy'
                
                script {
                    sh 'kubectl set image deployment/upload-service upload=${DOCKER_REGISTRY}/upload-service:${BUILD_NUMBER} -n production'
                    sh 'kubectl rollout status deployment/upload-service -n production --timeout=10m'
                }
            }
        }
        
        stage('Performance Tests') {
            steps {
                sh 'k6 run tests/load/upload-test.js'
            }
        }
    }
    
    post {
        success {
            slackSend(channel: env.SLACK_CHANNEL, color: 'good', message: "Deployment successful: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
        }
        failure {
            slackSend(channel: env.SLACK_CHANNEL, color: 'danger', message: "Deployment failed: ${env.JOB_NAME} #${env.BUILD_NUMBER}")
        }
    }
}
```

### GitHub Actions

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  AWS_REGION: us-east-1
  ECR_REGISTRY: 123456789.dkr.ecr.us-east-1.amazonaws.com
  EKS_CLUSTER: video-platform-prod

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    
    strategy:
      matrix:
        service: [upload, playback, transcoder]
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v2
    
    - name: Login to ECR
      uses: aws-actions/amazon-ecr-login@v1
    
    - name: Build image
      run: |
        docker build -t $ECR_REGISTRY/${{ matrix.service }}-service:${{ github.sha }} \
          -t $ECR_REGISTRY/${{ matrix.service }}-service:latest \
          ./services/${{ matrix.service }}
    
    - name: Run tests
      run: |
        docker run --rm $ECR_REGISTRY/${{ matrix.service }}-service:${{ github.sha }} pytest
    
    - name: Security scan
      uses: aquasecurity/trivy-action@master
      with:
        image-ref: ${{ env.ECR_REGISTRY }}/${{ matrix.service }}-service:${{ github.sha }}
        format: 'sarif'
        output: 'trivy-results.sarif'
    
    - name: Push image
      if: github.ref == 'refs/heads/main'
      run: |
        docker push $ECR_REGISTRY/${{ matrix.service }}-service:${{ github.sha }}
        docker push $ECR_REGISTRY/${{ matrix.service }}-service:latest

  deploy-staging:
    needs: build-and-test
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}
    
    - name: Update kubeconfig
      run: aws eks update-kubeconfig --name $EKS_CLUSTER --region $AWS_REGION
    
    - name: Deploy to staging
      run: |
        kubectl set image deployment/upload-service \
          upload=$ECR_REGISTRY/upload-service:${{ github.sha }} \
          -n staging
        kubectl rollout status deployment/upload-service -n staging --timeout=5m

  deploy-production:
    needs: deploy-staging
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production
    
    steps:
    - uses: actions/checkout@v3
    
    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: ${{ env.AWS_REGION }}
    
    - name: Update kubeconfig
      run: aws eks update-kubeconfig --name $EKS_CLUSTER --region $AWS_REGION
    
    - name: Deploy to production
      run: |
        kubectl set image deployment/upload-service \
          upload=$ECR_REGISTRY/upload-service:${{ github.sha }} \
          -n production
        kubectl rollout status deployment/upload-service -n production --timeout=10m
    
    - name: Notify Slack
      uses: slackapi/slack-github-action@v1
      with:
        payload: |
          {
            "text": "Production deployment successful: ${{ github.sha }}"
          }
      env:
        SLACK_WEBHOOK_URL: ${{ secrets.SLACK_WEBHOOK }}
```

### GitLab CI/CD

```yaml
stages:
  - build
  - test
  - deploy-staging
  - deploy-production

variables:
  DOCKER_REGISTRY: registry.gitlab.com/video-platform
  KUBE_NAMESPACE_STAGING: staging
  KUBE_NAMESPACE_PROD: production

build:
  stage: build
  image: docker:24
  services:
    - docker:24-dind
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $DOCKER_REGISTRY/upload-service:$CI_COMMIT_SHA ./services/upload
    - docker push $DOCKER_REGISTRY/upload-service:$CI_COMMIT_SHA

test:
  stage: test
  image: $DOCKER_REGISTRY/upload-service:$CI_COMMIT_SHA
  script:
    - pytest tests/
    - pylint services/upload/

deploy-staging:
  stage: deploy-staging
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context video-platform/staging
    - kubectl set image deployment/upload-service upload=$DOCKER_REGISTRY/upload-service:$CI_COMMIT_SHA -n $KUBE_NAMESPACE_STAGING
    - kubectl rollout status deployment/upload-service -n $KUBE_NAMESPACE_STAGING
  only:
    - main

deploy-production:
  stage: deploy-production
  image: bitnami/kubectl:latest
  script:
    - kubectl set image deployment/upload-service upload=$DOCKER_REGISTRY/upload-service:$CI_COMMIT_SHA -n $KUBE_NAMESPACE_PROD
    - kubectl rollout status deployment/upload-service -n $KUBE_NAMESPACE_PROD
  when: manual
  only:
    - main
```

---

## Monitoring & Observability

### Prometheus + Grafana

**Prometheus Configuration:**
```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # Kubernetes API server
  - job_name: 'kubernetes-apiservers'
    kubernetes_sd_configs:
    - role: endpoints
    scheme: https
    tls_config:
      ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
    bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
    
  # Kubernetes nodes
  - job_name: 'kubernetes-nodes'
    kubernetes_sd_configs:
    - role: node
    
  # Application pods
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

  # Custom application metrics
  - job_name: 'upload-service'
    static_configs:
    - targets: ['upload-service:9090']

  # Kafka
  - job_name: 'kafka'
    static_configs:
    - targets: ['kafka-0:9308', 'kafka-1:9308', 'kafka-2:9308']

  # Cassandra
  - job_name: 'cassandra'
    static_configs:
    - targets: ['cassandra-0:9500', 'cassandra-1:9500', 'cassandra-2:9500']
```

**Key Metrics to Track:**
```yaml
Video Playback Metrics:
  - video_playback_start_total (counter)
  - video_playback_buffer_time_seconds (histogram)
  - video_playback_errors_total (counter)
  - video_quality_switches_total (counter)
  - video_startup_time_seconds (histogram)

Upload Metrics:
  - video_upload_initiated_total (counter)
  - video_upload_completed_total (counter)
  - video_upload_failed_total (counter)
  - video_upload_duration_seconds (histogram)
  - video_upload_size_bytes (histogram)

Transcoding Metrics:
  - transcode_jobs_queued (gauge)
  - transcode_jobs_processing (gauge)
  - transcode_jobs_completed_total (counter)
  - transcode_duration_seconds (histogram)
  - transcode_cost_dollars (counter)

Infrastructure Metrics:
  - node_cpu_usage_percent (gauge)
  - node_memory_usage_bytes (gauge)
  - pod_restarts_total (counter)
  - http_requests_total (counter)
  - http_request_duration_seconds (histogram)

Business Metrics:
  - active_users (gauge)
  - concurrent_viewers (gauge)
  - video_views_total (counter)
  - watch_time_hours_total (counter)
  - cdn_bandwidth_gbps (gauge)
```

**Grafana Dashboards:**
```json
{
  "dashboard": {
    "title": "Video Platform Overview",
    "panels": [
      {
        "title": "Concurrent Viewers",
        "targets": [
          {
            "expr": "sum(concurrent_viewers)"
          }
        ],
        "type": "graph"
      },
      {
        "title": "QoE - Buffering Ratio",
        "targets": [
          {
            "expr": "rate(video_playback_buffer_time_seconds_sum[5m]) / rate(video_playback_time_seconds_sum[5m])"
          }
        ],
        "type": "graph",
        "alert": {
          "conditions": [
            {
              "evaluator": {
                "params": [0.01],
                "type": "gt"
              },
              "query": {
                "params": ["A", "5m", "now"]
              }
            }
          ],
          "message": "Buffering ratio exceeded 1%"
        }
      },
      {
        "title": "CDN Bandwidth",
        "targets": [
          {
            "expr": "sum(rate(cdn_bytes_sent[1m])) * 8 / 1e9"
          }
        ],
        "type": "graph"
      }
    ]
  }
}
```

### ELK Stack (Elasticsearch, Logstash, Kibana)

**Logstash Pipeline:**
```ruby
input {
  kafka {
    bootstrap_servers => "kafka-0:9092,kafka-1:9092"
    topics => ["application-logs", "play-events"]
    codec => json
  }
}

filter {
  if [type] == "play-event" {
    mutate {
      add_field => {
        "event_type" => "%{event}"
        "user_id" => "%{userId}"
        "video_id" => "%{videoId}"
      }
    }
    
    grok {
      match => {
        "message" => "%{TIMESTAMP_ISO8601:timestamp} %{LOGLEVEL:level} %{GREEDYDATA:message}"
      }
    }
    
    date {
      match => ["timestamp", "ISO8601"]
      target => "@timestamp"
    }
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch-0:9200", "elasticsearch-1:9200"]
    index => "video-platform-logs-%{+YYYY.MM.dd}"
  }
}
```

**Elasticsearch Index Template:**
```json
{
  "index_patterns": ["video-platform-logs-*"],
  "settings": {
    "number_of_shards": 3,
    "number_of_replicas": 1,
    "index.lifecycle.name": "logs-policy",
    "index.lifecycle.rollover_alias": "video-platform-logs"
  },
  "mappings": {
    "properties": {
      "timestamp": {"type": "date"},
      "level": {"type": "keyword"},
      "event_type": {"type": "keyword"},
      "user_id": {"type": "keyword"},
      "video_id": {"type": "keyword"},
      "message": {"type": "text"},
      "error": {"type": "text"}
    }
  }
}
```

### Distributed Tracing (Jaeger)

**Service Instrumentation (Python):**
```python
from opentelemetry import trace
from opentelemetry.exporter.jaeger.thrift import JaegerExporter
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor

# Setup Jaeger exporter
jaeger_exporter = JaegerExporter(
    agent_host_name='jaeger-agent',
    agent_port=6831,
)

trace.set_tracer_provider(TracerProvider())
trace.get_tracer_provider().add_span_processor(
    BatchSpanProcessor(jaeger_exporter)
)

tracer = trace.get_tracer(__name__)

# Instrument code
@app.route('/api/v1/videos/<video_id>/playback')
def get_playback_info(video_id):
    with tracer.start_as_current_span("get_playback_info") as span:
        span.set_attribute("video_id", video_id)
        
        # Check cache
        with tracer.start_as_current_span("redis_get"):
            cached = redis_client.get(f'video:{video_id}')
        
        if not cached:
            # Query database
            with tracer.start_as_current_span("cassandra_query"):
                video = cassandra_client.execute(
                    "SELECT * FROM videos WHERE video_id = ?",
                    [video_id]
                )
        
        # Generate signed URLs
        with tracer.start_as_current_span("generate_signed_urls"):
            manifest_url = generate_signed_url(video_id)
        
        return jsonify({
            'video_id': video_id,
            'manifest_url': manifest_url
        })
```

---

## Message Queues & Streaming

### Apache Kafka

**Cluster Setup:**
```yaml
version: '3'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.5.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    volumes:
      - zk-data:/var/lib/zookeeper/data
      - zk-log:/var/lib/zookeeper/log

  kafka-0:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 0
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-0:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
      KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
      KAFKA_NUM_PARTITIONS: 12
    volumes:
      - kafka-0-data:/var/lib/kafka/data

  kafka-1:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-1:9092
    volumes:
      - kafka-1-data:/var/lib/kafka/data

  kafka-2:
    image: confluentinc/cp-kafka:7.5.0
    depends_on:
      - zookeeper
    environment:
      KAFKA_BROKER_ID: 2
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka-2:9092
    volumes:
      - kafka-2-data:/var/lib/kafka/data
```

**Topic Configuration:**
```bash
# Create topics
kafka-topics --create --topic video.uploaded \
  --bootstrap-server kafka-0:9092 \
  --partitions 12 \
  --replication-factor 3 \
  --config retention.ms=604800000  # 7 days

kafka-topics --create --topic video.encoded \
  --bootstrap-server kafka-0:9092 \
  --partitions 12 \
  --replication-factor 3

kafka-topics --create --topic play.events \
  --bootstrap-server kafka-0:9092 \
  --partitions 24 \
  --replication-factor 3 \
  --config retention.ms=86400000  # 1 day (high volume)
```

**Producer (Python):**
```python
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers=['kafka-0:9092', 'kafka-1:9092', 'kafka-2:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8'),
    acks='all',  # Wait for all replicas
    compression_type='lz4',
    batch_size=16384,
    linger_ms=10
)

# Publish event
event = {
    'video_id': '550e8400-e29b-41d4-a716-446655440000',
    's3_key': 'raw/550e8400.../video.mp4',
    'duration': 3600,
    'resolution': '1920x1080'
}

producer.send('video.uploaded', value=event)
producer.flush()
```

**Consumer (Python):**
```python
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    'video.uploaded',
    bootstrap_servers=['kafka-0:9092', 'kafka-1:9092', 'kafka-2:9092'],
    group_id='transcoding-workers',
    value_deserializer=lambda m: json.loads(m.decode('utf-8')),
    auto_offset_reset='earliest',
    enable_auto_commit=True,
    max_poll_records=10
)

for message in consumer:
    event = message.value
    print(f"Processing video: {event['video_id']}")
    
    # Transcode video
    transcode_video(event)
    
    # Consumer automatically commits offset
```

### AWS SQS (Alternative)

```python
import boto3

sqs = boto3.client('sqs', region_name='us-east-1')

# Send message
response = sqs.send_message(
    QueueUrl='https://sqs.us-east-1.amazonaws.com/123456/video-uploaded',
    MessageBody=json.dumps({
        'video_id': '550e8400-e29b-41d4-a716-446655440000',
        's3_key': 'raw/550e8400.../video.mp4'
    }),
    DelaySeconds=0
)

# Receive messages
messages = sqs.receive_message(
    QueueUrl='https://sqs.us-east-1.amazonaws.com/123456/video-uploaded',
    MaxNumberOfMessages=10,
    WaitTimeSeconds=20  # Long polling
)

for message in messages.get('Messages', []):
    body = json.loads(message['Body'])
    
    # Process message
    transcode_video(body)
    
    # Delete message
    sqs.delete_message(
        QueueUrl='https://sqs.us-east-1.amazonaws.com/123456/video-uploaded',
        ReceiptHandle=message['ReceiptHandle']
    )
```

---

## Video Encoding Tools

### FFmpeg

**Installation:**
```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install ffmpeg

# Build from source with custom encoders
git clone https://git.ffmpeg.org/ffmpeg.git
cd ffmpeg
./configure --enable-gpl --enable-libx264 --enable-libx265 --enable-libvpx --enable-nonfree
make -j$(nproc)
sudo make install
```

**Multi-resolution encoding:**
```bash
#!/bin/bash
INPUT="input.mp4"
OUTPUT_DIR="output"

# Create output directory
mkdir -p $OUTPUT_DIR

# 4K encoding (H.265)
ffmpeg -i $INPUT \
  -c:v libx265 -preset medium -crf 23 \
  -vf scale=3840:2160 \
  -b:v 12000k -maxrate 12840k -bufsize 18000k \
  -c:a aac -b:a 192k \
  -movflags +faststart \
  $OUTPUT_DIR/4k.mp4 &

# 1080p encoding
ffmpeg -i $INPUT \
  -c:v libx264 -preset medium -crf 23 \
  -vf scale=1920:1080 \
  -b:v 5000k -maxrate 5350k -bufsize 7500k \
  -c:a aac -b:a 128k \
  -movflags +faststart \
  $OUTPUT_DIR/1080p.mp4 &

# 720p encoding
ffmpeg -i $INPUT \
  -c:v libx264 -preset fast -crf 23 \
  -vf scale=1280:720 \
  -b:v 2500k -maxrate 2675k -bufsize 3750k \
  -c:a aac -b:a 128k \
  -movflags +faststart \
  $OUTPUT_DIR/720p.mp4 &

# Wait for all encoding jobs
wait

echo "Encoding complete!"
```

**HLS packaging:**
```bash
ffmpeg -i input.mp4 \
  -c:v libx264 -preset medium -crf 23 \
  -vf scale=1920:1080 \
  -b:v 5000k -maxrate 5350k -bufsize 7500k \
  -c:a aac -b:a 128k \
  -f hls \
  -hls_time 6 \
  -hls_playlist_type vod \
  -hls_segment_filename "1080p/segment_%03d.ts" \
  1080p/playlist.m3u8
```

### AWS Elemental MediaConvert

```python
import boto3

mediaconvert = boto3.client('mediaconvert', region_name='us-east-1')

# Create transcoding job
response = mediaconvert.create_job(
    Role='arn:aws:iam::123456789:role/MediaConvertRole',
    Settings={
        'Inputs': [
            {
                'FileInput': 's3://video-uploads/raw/video.mp4',
                'AudioSelectors': {
                    'Audio Selector 1': {
                        'DefaultSelection': 'DEFAULT'
                    }
                }
            }
        ],
        'OutputGroups': [
            {
                'Name': 'Apple HLS',
                'OutputGroupSettings': {
                    'Type': 'HLS_GROUP_SETTINGS',
                    'HlsGroupSettings': {
                        'Destination': 's3://videos/encoded/video-id/',
                        'SegmentLength': 6,
                        'MinSegmentLength': 0
                    }
                },
                'Outputs': [
                    {
                        'NameModifier': '_1080p',
                        'VideoDescription': {
                            'Width': 1920,
                            'Height': 1080,
                            'CodecSettings': {
                                'Codec': 'H_264',
                                'H264Settings': {
                                    'Bitrate': 5000000,
                                    'RateControlMode': 'CBR'
                                }
                            }
                        },
                        'AudioDescriptions': [
                            {
                                'CodecSettings': {
                                    'Codec': 'AAC',
                                    'AacSettings': {
                                        'Bitrate': 128000,
                                        'SampleRate': 48000
                                    }
                                }
                            }
                        ]
                    },
                    {
                        'NameModifier': '_720p',
                        'VideoDescription': {
                            'Width': 1280,
                            'Height': 720,
                            'CodecSettings': {
                                'Codec': 'H_264',
                                'H264Settings': {
                                    'Bitrate': 2500000,
                                    'RateControlMode': 'CBR'
                                }
                            }
                        }
                    }
                ]
            }
        ]
    }
)

print(f"Job ID: {response['Job']['Id']}")
```

**Pricing:**
- HD (720p-1080p): $0.015/minute
- UHD (4K): $0.03/minute
- Audio-only: $0.00522/minute

---

## Infrastructure as Code

### Terraform

**VPC and Networking:**
```hcl
# vpc.tf
provider "aws" {
  region = "us-east-1"
}

resource "aws_vpc" "video_platform" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "video-platform-vpc"
  }
}

resource "aws_subnet" "public" {
  count                   = 3
  vpc_id                  = aws_vpc.video_platform.id
  cidr_block              = "10.0.${count.index + 1}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "public-subnet-${count.index + 1}"
  }
}

resource "aws_subnet" "private" {
  count             = 3
  vpc_id            = aws_vpc.video_platform.id
  cidr_block        = "10.0.${count.index + 101}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]

  tags = {
    Name = "private-subnet-${count.index + 1}"
  }
}

resource "aws_internet_gateway" "main" {
  vpc_id = aws_vpc.video_platform.id

  tags = {
    Name = "video-platform-igw"
  }
}

resource "aws_nat_gateway" "main" {
  count         = 3
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id

  tags = {
    Name = "nat-gateway-${count.index + 1}"
  }
}

resource "aws_eip" "nat" {
  count  = 3
  domain = "vpc"
}
```

**S3 Buckets:**
```hcl
# s3.tf
resource "aws_s3_bucket" "video_uploads" {
  bucket = "video-platform-uploads"

  tags = {
    Name        = "Video Uploads"
    Environment = "production"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "video_uploads" {
  bucket = aws_s3_bucket.video_uploads.id

  rule {
    id     = "move-to-ia"
    status = "Enabled"

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER_IR"
    }

    transition {
      days          = 365
      storage_class = "DEEP_ARCHIVE"
    }
  }
}

resource "aws_s3_bucket" "encoded_videos" {
  bucket = "video-platform-encoded"

  tags = {
    Name = "Encoded Videos"
  }
}

resource "aws_s3_bucket_versioning" "encoded_videos" {
  bucket = aws_s3_bucket.encoded_videos.id

  versioning_configuration {
    status = "Enabled"
  }
}
```

**EKS Cluster:**
```hcl
# eks.tf
module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "~> 19.0"

  cluster_name    = "video-platform-prod"
  cluster_version = "1.28"

  vpc_id     = aws_vpc.video_platform.id
  subnet_ids = aws_subnet.private[*].id

  eks_managed_node_groups = {
    api_services = {
      min_size     = 5
      max_size     = 50
      desired_size = 20

      instance_types = ["c5.2xlarge"]
      capacity_type  = "ON_DEMAND"

      labels = {
        workload = "api"
      }
    }

    transcoding = {
      min_size     = 10
      max_size     = 200
      desired_size = 50

      instance_types = ["c5.9xlarge"]
      capacity_type  = "SPOT"

      labels = {
        workload = "compute-intensive"
      }

      taints = [{
        key    = "workload"
        value  = "transcoding"
        effect = "NO_SCHEDULE"
      }]
    }

    database = {
      min_size     = 3
      max_size     = 10
      desired_size = 6

      instance_types = ["r5.4xlarge"]
      capacity_type  = "ON_DEMAND"

      labels = {
        workload = "database"
      }
    }
  }

  tags = {
    Environment = "production"
  }
}
```

**CloudFront Distribution:**
```hcl
# cloudfront.tf
resource "aws_cloudfront_distribution" "video_cdn" {
  enabled             = true
  is_ipv6_enabled     = true
  comment             = "Video Platform CDN"
  default_root_object = "index.html"

  origin {
    domain_name = aws_s3_bucket.encoded_videos.bucket_regional_domain_name
    origin_id   = "S3-encoded-videos"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.video_cdn.cloudfront_access_identity_path
    }
  }

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD", "OPTIONS"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-encoded-videos"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400   # 1 day
    max_ttl                = 31536000 # 1 year
    compress               = true
  }

  price_class = "PriceClass_All"

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = false
    acm_certificate_arn            = aws_acm_certificate.cdn.arn
    ssl_support_method             = "sni-only"
    minimum_protocol_version       = "TLSv1.2_2021"
  }

  tags = {
    Environment = "production"
  }
}

resource "aws_cloudfront_origin_access_identity" "video_cdn" {
  comment = "Access identity for video CDN"
}
```

---

## Real Industry Examples

### Netflix Tech Stack

```yaml
Programming Languages:
  - Java: Microservices, backend
  - Python: Data analytics, ML pipelines
  - Node.js: UI layer
  - Groovy: Build automation (Gradle)

Frontend:
  - React: Web player
  - Swift: iOS app
  - Kotlin: Android app

Databases:
  - MySQL (sharded): Billing, user data
  - Cassandra: Video metadata, watch history
  - EVCache (Memcached): Distributed cache
  - Elasticsearch: Search

Message Queue:
  - Apache Kafka: Event streaming

Storage:
  - AWS S3: Origin storage
  - Open Connect: Edge caching (17K+ servers)

Container Orchestration:
  - Titus: Netflix's container platform (on AWS)
  - Based on Apache Mesos

CI/CD:
  - Spinnaker: Multi-cloud continuous delivery
  - Jenkins: Build automation

Monitoring:
  - Atlas: Metrics platform
  - Vector: On-host performance monitoring
  - Mantis: Real-time stream processing

Chaos Engineering:
  - Chaos Monkey: Random instance termination
  - Chaos Kong: Entire AWS region failure simulation

Video Encoding:
  - Custom-built encoders
  - Per-title encoding optimization
  - AV1 codec adoption

CDN:
  - Netflix Open Connect (own CDN)
  - 95%+ of traffic served from edge
  - Colocation with ISPs

API Gateway:
  - Zuul: Dynamic routing, monitoring, security

Service Discovery:
  - Eureka: Service registry

Circuit Breaker:
  - Hystrix: Fault tolerance library

Recommendation:
  - Custom ML models
  - Apache Spark for batch processing
  - TensorFlow for deep learning
```

### YouTube (Google) Tech Stack

```yaml
Programming Languages:
  - C++: Video processing, backend
  - Java: Backend services
  - Python: Infrastructure automation, ML
  - Go: Microservices
  - JavaScript/TypeScript: Frontend

Frontend:
  - Polymer (Web Components)
  - Android native
  - iOS native

Databases:
  - Bigtable: Video metadata, comments
  - Spanner: Global SQL database
  - MySQL (sharded): User data

Storage:
  - Colossus: Google's distributed file system
  - Google Cloud Storage: Public API

Video Serving:
  - Google Global Cache (GGC)
  - Edge PoPs worldwide
  - Hundreds of thousands of servers

Video Encoding:
  - VP9 codec (Google-developed)
  - AV1 adoption (next-gen)
  - Custom encoding pipelines
  - TPUs for ML-optimized encoding

Container Orchestration:
  - Borg: Internal Kubernetes predecessor
  - Kubernetes: Open-source version

CI/CD:
  - Bazel: Build system
  - Internal deployment tools

Monitoring:
  - Borgmon: Metrics collection
  - Dapper: Distributed tracing

Message Queue:
  - Pub/Sub: Real-time messaging

Search:
  - Custom search infrastructure
  - Real-time indexing

Recommendation:
  - TensorFlow Recommenders
  - Multi-stage candidate generation + ranking
  - Deep neural networks

CDN:
  - Google Global Cache
  - Direct peering with ISPs
  - Anycast IP routing
```

### Twitch (Amazon) Tech Stack

```yaml
Programming Languages:
  - Go: Backend microservices
  - Python: Data processing, ML
  - JavaScript/TypeScript: Frontend
  - Elixir: Real-time chat

Frontend:
  - React: Web player
  - React Native: Mobile apps

Databases:
  - PostgreSQL: User data, channels
  - DynamoDB: Metadata, chat
  - Redis: Caching, session storage
  - Elasticsearch: Search

Storage:
  - AWS S3: VOD storage
  - Custom live streaming infrastructure

Live Streaming:
  - RTMP ingest
  - HLS/DASH delivery
  - 2-5 second latency (standard)
  - Low-Latency HLS (<2 seconds)

CDN:
  - Amazon CloudFront
  - Custom edge servers
  - Global PoPs

Video Encoding:
  - FFmpeg-based transcoding
  - Real-time multi-bitrate
  - Hardware-accelerated (NVIDIA GPUs)

Container Orchestration:
  - Amazon ECS (Elastic Container Service)
  - Docker containers

Message Queue:
  - Amazon Kinesis: Real-time data streams
  - AWS SQS: Decoupling services

Monitoring:
  - Amazon CloudWatch
  - Custom metrics platform
  - Real-time dashboards

Chat:
  - IRC-based protocol
  - Elixir/Phoenix for real-time websockets
  - Horizontally scaled

Recommendation:
  - Amazon Personalize
  - Custom ML models
  - Real-time trending algorithm
```

---

## Cost Optimization Strategies

```yaml
Compute:
  - Use Spot Instances for transcoding (70% savings)
  - Reserved Instances for baseline capacity (72% savings)
  - Right-size instances (avoid over-provisioning)
  - Auto-scaling to match demand

Storage:
  - S3 Intelligent-Tiering (automatic optimization)
  - Lifecycle policies (move to Glacier)
  - Delete old transcoding artifacts
  - Compression (reduce storage by 30%)

CDN:
  - Own CDN at scale (95% savings vs third-party)
  - Aggressive caching (>95% hit ratio)
  - Optimize cache duration
  - Geo-restrictions (block expensive regions if needed)

Database:
  - Use read replicas (offload reads)
  - Connection pooling (reduce overhead)
  - Aggressive caching (Redis)
  - Optimize queries (avoid full scans)

Monitoring:
  - Sample high-volume metrics (not 100%)
  - Retain logs for limited period
  - Use log levels (don't log DEBUG in production)

General:
  - Delete unused resources (zombie instances)
  - Use CloudWatch billing alerts
  - Regular cost review meetings
  - Tagging for cost attribution
```

---

**This comprehensive DevOps guide covers the complete tech stack and infrastructure used by Netflix, YouTube, and other major streaming platforms.**
