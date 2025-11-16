# 🚀 E-Commerce Tech Stack & DevOps Guide

## Complete Technology Stack Used by Amazon, Flipkart, Alibaba

This guide covers the **battle-tested technologies** and **DevOps practices** used in production by top e-commerce companies.

---

## 📦 PART 1: Frontend Technologies

### Web Applications

**Frameworks:**
```
React.js / Next.js (Server-Side Rendering)
├── Why? SEO-friendly, fast page loads, code splitting
├── Used by: Amazon, Flipkart, Shopify
├── Package Manager: npm / yarn
├── Build Tool: Webpack / Vite
└── State Management: Redux / Zustand / React Query

Vue.js / Nuxt.js
├── Why? Lightweight, easy to learn, progressive framework
├── Used by: Alibaba, Lazada
└── State Management: Vuex / Pinia

Angular (Enterprise)
├── Why? Full-featured, TypeScript-native, enterprise-ready
├── Used by: Large enterprise e-commerce
└── State Management: NgRx
```

**UI Component Libraries:**
```
Material-UI (MUI) - Google's Material Design
Ant Design - Enterprise-grade UI (Alibaba)
Chakra UI - Accessible, themeable
Tailwind CSS - Utility-first CSS framework
```

**Performance Optimization:**
```
Code Splitting:
- React.lazy() / Suspense
- Dynamic imports
- Route-based splitting

Image Optimization:
- Next.js Image component (automatic WebP conversion)
- Lazy loading (Intersection Observer API)
- Responsive images (srcset)

Caching:
- Service Workers (PWA)
- Browser caching (Cache-Control headers)
- LocalStorage for cart data
```

---

### Mobile Applications

**Native Development:**
```
iOS:
├── Language: Swift / SwiftUI
├── Architecture: MVVM / Clean Architecture
├── Networking: URLSession / Alamofire
└── Local Storage: Core Data / Realm

Android:
├── Language: Kotlin
├── Architecture: MVVM / Clean Architecture
├── Networking: Retrofit / OkHttp
├── UI: Jetpack Compose
└── Local Storage: Room / SQLite
```

**Cross-Platform:**
```
React Native
├── Why? Single codebase for iOS + Android
├── Used by: Shopify, Walmart
├── UI: React Native Paper / NativeBase
└── Navigation: React Navigation

Flutter
├── Why? Fast development, beautiful UI, single codebase
├── Used by: Alibaba (Xianyu app)
├── Language: Dart
└── State Management: Bloc / Riverpod
```

---

## 🔧 PART 2: Backend Technologies

### Programming Languages & Frameworks

**Java / Spring Boot** (Most Popular for E-Commerce)
```
Why Java?
✓ Mature ecosystem
✓ Excellent performance (JVM optimization)
✓ Strong typing (fewer runtime errors)
✓ Battle-tested at scale (Amazon, eBay)

Spring Boot Ecosystem:
├── Spring Boot - Core framework
├── Spring Cloud - Microservices (Service Discovery, Config)
├── Spring Security - Authentication & Authorization
├── Spring Data JPA - Database abstraction
└── Spring WebFlux - Reactive programming

Example Microservice Structure:
src/
├── main/
│   ├── java/
│   │   └── com.ecommerce.product/
│   │       ├── controller/      (REST APIs)
│   │       ├── service/         (Business Logic)
│   │       ├── repository/      (Database)
│   │       ├── model/           (Entities)
│   │       ├── dto/             (Data Transfer Objects)
│   │       └── config/          (Spring Configuration)
│   └── resources/
│       ├── application.yml      (Config)
│       └── db/migration/        (Flyway migrations)
└── test/                        (Unit & Integration Tests)
```

**Node.js / Express** (High Concurrency)
```
Why Node.js?
✓ Non-blocking I/O (perfect for I/O-heavy tasks)
✓ Fast development
✓ Same language as frontend (JavaScript/TypeScript)
✓ Large npm ecosystem

Popular Frameworks:
├── Express.js - Minimalist, flexible
├── NestJS - TypeScript, Angular-like (enterprise)
├── Fastify - High performance
└── Koa - Modern, lightweight

Use Cases:
✓ API Gateway (high throughput)
✓ Real-time features (Socket.io for live tracking)
✓ Cart Service (low latency, session management)
```

**Go (Golang)** (High Performance)
```
Why Go?
✓ Compiled (fast execution)
✓ Goroutines (lightweight concurrency)
✓ Low memory footprint
✓ Fast startup time (serverless-friendly)

Popular Frameworks:
├── Gin - Fast HTTP framework
├── Echo - High performance, minimalist
└── Fiber - Express-like API

Use Cases:
✓ Payment Gateway (low latency critical)
✓ Inventory Service (high concurrency)
✓ Notification Service (async processing)
```

**Python** (Data & ML)
```
Why Python?
✓ Rich ML/AI libraries
✓ Fast prototyping
✓ Excellent for data processing

Frameworks:
├── FastAPI - Modern, async, auto-docs (OpenAPI)
├── Django - Full-featured (Django REST Framework)
└── Flask - Lightweight

Use Cases:
✓ Recommendation Engine (TensorFlow, PyTorch)
✓ Fraud Detection (Scikit-learn)
✓ Analytics Pipeline (Pandas, NumPy)
✓ Data Science (Jupyter Notebooks)
```

---

## 🗄️ PART 3: Database Technologies

### Relational Databases (ACID Transactions)

**PostgreSQL** (Most Popular)
```
Why PostgreSQL?
✓ Open-source, feature-rich
✓ ACID compliant
✓ Excellent performance (indexes, query optimizer)
✓ Supports JSONB (semi-structured data)
✓ Strong community

Use Cases:
✓ Orders (transactions required)
✓ Payments (financial data)
✓ Inventory (strong consistency)
✓ Users (structured data)

Features Used:
├── Partitioning (by date for orders)
├── Sharding (by user_id)
├── Read Replicas (read scaling)
├── Row-level locks (SELECT ... FOR UPDATE)
├── JSONB columns (flexible metadata)
└── Full-text search (tsvector)

Performance Tuning:
- Connection Pooling: PgBouncer / HikariCP
- Indexing: B-tree (default), GIN (JSONB), GiST (spatial)
- Vacuuming: Auto-vacuum configuration
- Query Optimization: EXPLAIN ANALYZE
```

**MySQL** (Also Popular)
```
Used by: Alibaba, Shopify

Variants:
├── MySQL Community Edition
├── Percona Server (MySQL fork with improvements)
└── MariaDB (MySQL fork, fully compatible)

Why MySQL?
✓ Mature, stable
✓ High read performance
✓ Excellent replication
✓ Large ecosystem (tools, libraries)

MySQL vs PostgreSQL:
- MySQL: Better for read-heavy workloads
- PostgreSQL: Better for complex queries, writes
```

### NoSQL Databases

**MongoDB** (Document Store)
```
Why MongoDB?
✓ Flexible schema (perfect for product catalog)
✓ Horizontal scaling (sharding built-in)
✓ Fast reads/writes
✓ Rich query language

Use Cases:
✓ Product Catalog (500M+ SKUs, diverse attributes)
✓ User Activity Logs (flexible schema)
✓ Reviews & Ratings (nested documents)

Schema Design:
{
  "_id": ObjectId("..."),
  "product_id": "PROD-12345",
  "name": "iPhone 15 Pro",
  "category": ["Electronics", "Mobile"],
  "variants": [
    {
      "sku": "iphone15-256-black",
      "price": 999.99,
      "attributes": { "storage": "256GB", "color": "Black" }
    }
  ],
  "reviews": [
    { "user_id": "user123", "rating": 5, "text": "Great!" }
  ]
}

Sharding Strategy:
- Shard Key: product_id (hashed sharding)
- Chunks: 64MB each
- Balancer: Automatic chunk migration

Performance:
- Indexes: Compound indexes on frequently queried fields
- Read Preference: primaryPreferred (read from replicas)
- Write Concern: majority (ensure durability)
```

**Cassandra** (Wide-Column Store)
```
Why Cassandra?
✓ Linear scalability (add nodes = add capacity)
✓ No single point of failure
✓ Tunable consistency
✓ Write-optimized

Use Cases:
✓ Time-Series Data (order status history)
✓ High-Volume Writes (analytics events)
✓ Distributed Globally (multi-datacenter)

Data Model:
CREATE TABLE order_history (
    order_id UUID,
    timestamp TIMESTAMP,
    status TEXT,
    details TEXT,
    PRIMARY KEY (order_id, timestamp)
) WITH CLUSTERING ORDER BY (timestamp DESC);

Consistency Levels:
- QUORUM (majority of replicas) - balanced
- ONE (fastest writes, eventual consistency)
- ALL (strong consistency, slowest)
```

**DynamoDB** (AWS Managed NoSQL)
```
Why DynamoDB?
✓ Fully managed (no ops)
✓ Auto-scaling
✓ Low latency (<10ms)
✓ Integrated with AWS ecosystem

Use Cases:
✓ Shopping Cart (low latency, high throughput)
✓ Session Store (TTL support)
✓ Real-time Inventory Tracking

Pricing Models:
- On-Demand: Pay per request (variable workload)
- Provisioned: Reserve capacity (predictable workload)

Performance:
- Partition Key: Choose high-cardinality key (user_id)
- Sort Key: For range queries (timestamp)
- Global Secondary Indexes: For alternate access patterns
- DynamoDB Streams: Change data capture (CDC)
```

### Search Engines

**Elasticsearch**
```
Why Elasticsearch?
✓ Full-text search (inverted indices)
✓ Faceted search (brand, price, rating filters)
✓ Near real-time search
✓ Distributed & scalable

Architecture:
Cluster
├── Master Nodes (3) - Cluster management
├── Data Nodes (6) - Store data, execute queries
└── Coordinating Nodes (2) - Route requests

Index Structure:
products (index)
├── Shard 0 (Primary) → Replica
├── Shard 1 (Primary) → Replica
├── Shard 2 (Primary) → Replica

Query DSL:
{
  "query": {
    "bool": {
      "must": [
        { "multi_match": { "query": "laptop", "fields": ["name^3", "description"] } }
      ],
      "filter": [
        { "range": { "price": { "gte": 500, "lte": 2000 } } },
        { "term": { "brand": "Apple" } }
      ]
    }
  },
  "aggs": {
    "brands": { "terms": { "field": "brand.keyword" } },
    "price_ranges": { "range": { "field": "price", "ranges": [...] } }
  }
}

Performance Tuning:
- Refresh Interval: 30s (reduce indexing overhead)
- Replica Shards: 1-2 (balance availability vs resources)
- Query Caching: Filter cache for frequent filters
- Bulk Indexing: Batch updates for efficiency
```

**Algolia / Typesense** (Managed Search)
```
Why Algolia?
✓ Blazing fast (<10ms search)
✓ Typo tolerance
✓ Instant search (as-you-type)
✓ Managed service (no ops)

Pricing: Pay per operation (can be expensive at scale)

Use Case: Smaller e-commerce sites, instant search
```

### Caching

**Redis**
```
Why Redis?
✓ In-memory (sub-millisecond latency)
✓ Rich data structures (strings, lists, sets, sorted sets, hashes)
✓ Pub/Sub messaging
✓ Lua scripting

Use Cases:
✓ Session Store (user sessions, cart)
✓ Product Cache (hot products)
✓ Rate Limiting (sliding window counter)
✓ Leaderboards (sorted sets)
✓ Real-time Analytics (HyperLogLog)

Data Structures:
# String: Simple key-value
SET product:123 '{"name": "iPhone", "price": 999}'
GET product:123

# Hash: Object-like storage
HSET user:456 name "John" email "john@example.com"
HGET user:456 name

# Sorted Set: Leaderboards, rankings
ZADD bestsellers 1500 "product:123" 1200 "product:456"
ZREVRANGE bestsellers 0 9  # Top 10

# TTL: Auto-expiration
SETEX session:abc 1800 '{"user_id": 123}'  # Expires in 30 min

Redis Cluster:
- Hash Slot-based sharding (16384 slots)
- Automatic failover (Sentinel / Cluster mode)
- Replication: Master-Replica

Persistence:
- RDB (snapshots): Fast, compact, data loss risk
- AOF (append-only file): Durable, slower, larger files
- Hybrid: RDB + AOF (best of both)
```

**Memcached**
```
Why Memcached?
✓ Simple, lightweight
✓ Multi-threaded (better CPU utilization)
✓ Pure cache (no persistence)

Use Case: Simple key-value caching
Redis vs Memcached: Redis wins for rich features, Memcached for pure caching
```

### Analytics Databases

**ClickHouse**
```
Why ClickHouse?
✓ Columnar storage (fast aggregations)
✓ Real-time analytics
✓ SQL support
✓ Compression (10:1 ratio)

Use Cases:
✓ Click stream analytics (1B+ events/day)
✓ Order analytics (daily/weekly/monthly reports)
✓ User behavior tracking

Schema:
CREATE TABLE click_events (
    event_time DateTime,
    user_id UInt64,
    product_id String,
    action String,
    device String,
    country String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(event_time)
ORDER BY (user_id, event_time);

Query Performance:
SELECT
    toDate(event_time) as date,
    count() as clicks,
    uniq(user_id) as unique_users
FROM click_events
WHERE event_time >= today() - 30
GROUP BY date
ORDER BY date;

-- Executes in <1 second on billions of rows!
```

**Apache Druid**
```
Why Druid?
✓ Real-time + historical analytics
✓ Sub-second queries
✓ Time-series optimized

Use Case: Real-time dashboards, streaming analytics
```

---

## 🔄 PART 4: Message Queues & Event Streaming

### Apache Kafka

**Why Kafka?**
```
✓ High throughput (millions of messages/sec)
✓ Durability (persistent log)
✓ Horizontal scaling (partitions)
✓ Exactly-once semantics
✓ Built-in replication
```

**Architecture:**
```
Kafka Cluster
├── Broker 1 (holds partitions 0, 1, 2)
├── Broker 2 (holds partitions 3, 4, 5)
└── Broker 3 (holds partitions 6, 7, 8)

Topics:
├── order.created (10 partitions, replication factor 3)
├── payment.completed (5 partitions, RF 3)
├── inventory.updated (10 partitions, RF 3)
└── analytics.events (20 partitions, RF 2)
```

**Producer Example (Java):**
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("acks", "all");  // Wait for all replicas
props.put("retries", 3);

Producer<String, String> producer = new KafkaProducer<>(props);

// Send message
ProducerRecord<String, String> record = new ProducerRecord<>(
    "order.created",
    orderId,  // Key (determines partition)
    orderJson  // Value
);

producer.send(record, (metadata, exception) -> {
    if (exception == null) {
        System.out.println("Sent to partition " + metadata.partition());
    } else {
        exception.printStackTrace();
    }
});
```

**Consumer Example:**
```java
Properties props = new Properties();
props.put("bootstrap.servers", "localhost:9092");
props.put("group.id", "order-processor-group");
props.put("enable.auto.commit", "false");  // Manual commit for exactly-once
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("order.created"));

while (true) {
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
    for (ConsumerRecord<String, String> record : records) {
        // Process order
        processOrder(record.value());

        // Manual commit after processing
        consumer.commitSync();
    }
}
```

### RabbitMQ

**Why RabbitMQ?**
```
✓ Message routing (exchanges, queues)
✓ Priority queues
✓ Message acknowledgments
✓ Dead letter queues
```

**Use Cases:**
```
✓ Task queues (email, notifications)
✓ RPC (request-reply pattern)
✓ Work distribution
```

**Exchange Types:**
```
1. Direct Exchange: Route by exact key match
2. Fanout Exchange: Broadcast to all queues
3. Topic Exchange: Pattern matching (logs.*)
4. Headers Exchange: Route by message headers
```

### AWS SQS / SNS

**SQS (Simple Queue Service):**
```
Why SQS?
✓ Fully managed
✓ Auto-scaling
✓ At-least-once delivery
✓ Visibility timeout (prevent duplicate processing)

Types:
- Standard Queue: Unlimited throughput, at-least-once, best-effort ordering
- FIFO Queue: Exactly-once, ordered, 3000 msg/sec limit

Use Case: Async job processing (email, image resizing)
```

**SNS (Simple Notification Service):**
```
Why SNS?
✓ Pub/Sub messaging
✓ Fan-out to multiple subscribers
✓ Push notifications (mobile, email, SMS)

Pattern:
SNS Topic → SQS Queue 1 (Email Service)
         → SQS Queue 2 (SMS Service)
         → SQS Queue 3 (Push Notification Service)
```

---

## ☁️ PART 5: Cloud & Infrastructure

### Cloud Providers

**AWS (Most Popular)**
```
Compute:
├── EC2 (Virtual Machines)
├── ECS (Container Service)
├── EKS (Kubernetes)
├── Lambda (Serverless)
└── Fargate (Serverless Containers)

Storage:
├── S3 (Object Storage)
├── EBS (Block Storage)
└── EFS (File Storage)

Databases:
├── RDS (PostgreSQL, MySQL)
├── DynamoDB (NoSQL)
├── ElastiCache (Redis, Memcached)
└── OpenSearch (Elasticsearch fork)

Networking:
├── VPC (Virtual Private Cloud)
├── Route 53 (DNS)
├── CloudFront (CDN)
└── ALB/NLB (Load Balancers)

Monitoring:
├── CloudWatch (Metrics, Logs)
├── X-Ray (Distributed Tracing)
└── CloudTrail (Audit Logs)
```

**Google Cloud Platform (GCP)**
```
Compute:
├── Compute Engine (VMs)
├── GKE (Kubernetes)
├── Cloud Run (Serverless Containers)
└── Cloud Functions (Serverless)

Databases:
├── Cloud SQL (PostgreSQL, MySQL)
├── Firestore (NoSQL)
├── Bigtable (Wide-column)
└── Memorystore (Redis)

Strengths:
✓ BigQuery (data warehouse, analytics)
✓ Vertex AI (ML platform)
✓ Networking (global fiber network)
```

**Azure**
```
Strengths:
✓ Enterprise integration (.NET, Active Directory)
✓ Hybrid cloud (Azure Arc)
✓ Cosmos DB (multi-model database)
```

### Container Orchestration

**Kubernetes**
```
Why Kubernetes?
✓ Container orchestration
✓ Auto-scaling (HPA, VPA, Cluster Autoscaler)
✓ Self-healing (restarts failed containers)
✓ Service discovery & load balancing
✓ Rolling updates & rollbacks

Architecture:
Control Plane:
├── API Server (kubectl commands)
├── Scheduler (assigns pods to nodes)
├── Controller Manager (maintains desired state)
└── etcd (key-value store for cluster state)

Worker Nodes:
├── kubelet (runs containers)
├── kube-proxy (networking)
└── Container Runtime (Docker, containerd)

Key Concepts:
- Pod: Smallest deployable unit (1+ containers)
- Deployment: Manages replicas, rolling updates
- Service: Stable network endpoint for pods
- ConfigMap/Secret: Configuration & credentials
- Ingress: HTTP routing to services
- PersistentVolume: Persistent storage
```

**Deployment Example:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 10
  selector:
    matchLabels:
      app: product-service
  template:
    metadata:
      labels:
        app: product-service
    spec:
      containers:
      - name: product-service
        image: ecommerce/product-service:v1.2.3
        ports:
        - containerPort: 8080
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: host
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
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
apiVersion: v1
kind: Service
metadata:
  name: product-service
spec:
  selector:
    app: product-service
  ports:
  - protocol: TCP
    port: 80
    targetPort: 8080
  type: LoadBalancer
---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: product-service-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: product-service
  minReplicas: 5
  maxReplicas: 50
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
```

---

## 🔨 PART 6: DevOps & CI/CD

### CI/CD Tools

**Jenkins**
```
Why Jenkins?
✓ Open-source, extensible
✓ Massive plugin ecosystem
✓ Self-hosted (full control)

Pipeline Example (Jenkinsfile):
pipeline {
    agent any

    stages {
        stage('Checkout') {
            steps {
                git 'https://github.com/ecommerce/product-service'
            }
        }

        stage('Build') {
            steps {
                sh './mvnw clean package'
            }
        }

        stage('Test') {
            steps {
                sh './mvnw test'
                junit 'target/surefire-reports/*.xml'
            }
        }

        stage('Docker Build') {
            steps {
                sh 'docker build -t product-service:${BUILD_NUMBER} .'
            }
        }

        stage('Push to Registry') {
            steps {
                sh 'docker push ecr.amazonaws.com/product-service:${BUILD_NUMBER}'
            }
        }

        stage('Deploy to K8s') {
            steps {
                sh 'kubectl set image deployment/product-service product-service=ecr.amazonaws.com/product-service:${BUILD_NUMBER}'
            }
        }
    }

    post {
        failure {
            slackSend channel: '#deployments', message: "Build failed: ${env.JOB_NAME} ${env.BUILD_NUMBER}"
        }
        success {
            slackSend channel: '#deployments', message: "Deployed successfully: ${env.JOB_NAME} ${env.BUILD_NUMBER}"
        }
    }
}
```

**GitLab CI**
```
Why GitLab CI?
✓ Integrated with GitLab
✓ YAML-based config
✓ Auto DevOps

.gitlab-ci.yml:
stages:
  - build
  - test
  - deploy

variables:
  DOCKER_IMAGE: $CI_REGISTRY_IMAGE:$CI_COMMIT_SHORT_SHA

build:
  stage: build
  image: maven:3.8-openjdk-17
  script:
    - mvn clean package
  artifacts:
    paths:
      - target/*.jar

test:
  stage: test
  image: maven:3.8-openjdk-17
  script:
    - mvn test
  coverage: '/Total.*?([0-9]{1,3})%/'

docker_build:
  stage: build
  image: docker:latest
  services:
    - docker:dind
  script:
    - docker login -u $CI_REGISTRY_USER -p $CI_REGISTRY_PASSWORD $CI_REGISTRY
    - docker build -t $DOCKER_IMAGE .
    - docker push $DOCKER_IMAGE

deploy_production:
  stage: deploy
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context production
    - kubectl set image deployment/product-service product-service=$DOCKER_IMAGE
    - kubectl rollout status deployment/product-service
  only:
    - main
  when: manual
```

**GitHub Actions**
```
Why GitHub Actions?
✓ Integrated with GitHub
✓ Free for public repos
✓ Marketplace (reusable actions)

.github/workflows/deploy.yml:
name: Deploy to Production

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Build with Maven
      run: mvn clean package -DskipTests

    - name: Run Tests
      run: mvn test

    - name: Configure AWS credentials
      uses: aws-actions/configure-aws-credentials@v2
      with:
        aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
        aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
        aws-region: us-east-1

    - name: Login to Amazon ECR
      id: login-ecr
      uses: aws-actions/amazon-ecr-login@v1

    - name: Build, tag, and push image to Amazon ECR
      env:
        ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
        ECR_REPOSITORY: product-service
        IMAGE_TAG: ${{ github.sha }}
      run: |
        docker build -t $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG .
        docker push $ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG

    - name: Deploy to EKS
      run: |
        aws eks update-kubeconfig --name production-cluster --region us-east-1
        kubectl set image deployment/product-service product-service=$ECR_REGISTRY/$ECR_REPOSITORY:$IMAGE_TAG
        kubectl rollout status deployment/product-service
```

### Infrastructure as Code (IaC)

**Terraform**
```
Why Terraform?
✓ Multi-cloud (AWS, GCP, Azure)
✓ Declarative (describe desired state)
✓ State management
✓ Plan before apply (preview changes)

Example (EKS Cluster):
provider "aws" {
  region = "us-east-1"
}

module "eks" {
  source  = "terraform-aws-modules/eks/aws"
  version = "19.0"

  cluster_name    = "ecommerce-cluster"
  cluster_version = "1.27"

  vpc_id     = module.vpc.vpc_id
  subnet_ids = module.vpc.private_subnets

  eks_managed_node_groups = {
    general = {
      min_size     = 5
      max_size     = 50
      desired_size = 10

      instance_types = ["t3.xlarge"]
      capacity_type  = "ON_DEMAND"
    }
  }

  tags = {
    Environment = "production"
    Terraform   = "true"
  }
}

module "vpc" {
  source  = "terraform-aws-modules/vpc/aws"
  version = "5.0"

  name = "ecommerce-vpc"
  cidr = "10.0.0.0/16"

  azs             = ["us-east-1a", "us-east-1b", "us-east-1c"]
  private_subnets = ["10.0.1.0/24", "10.0.2.0/24", "10.0.3.0/24"]
  public_subnets  = ["10.0.101.0/24", "10.0.102.0/24", "10.0.103.0/24"]

  enable_nat_gateway = true
  enable_vpn_gateway = false

  tags = {
    Environment = "production"
  }
}

# RDS for PostgreSQL
resource "aws_db_instance" "orders_db" {
  identifier = "orders-db"

  engine         = "postgres"
  engine_version = "15.3"
  instance_class = "db.r6g.2xlarge"

  allocated_storage     = 1000
  max_allocated_storage = 5000
  storage_type          = "gp3"
  iops                  = 12000

  db_name  = "orders"
  username = "admin"
  password = var.db_password

  multi_az               = true
  backup_retention_period = 7
  backup_window          = "03:00-04:00"
  maintenance_window     = "Mon:04:00-Mon:05:00"

  enabled_cloudwatch_logs_exports = ["postgresql", "upgrade"]

  tags = {
    Environment = "production"
  }
}

# ElastiCache (Redis) Cluster
resource "aws_elasticache_replication_group" "redis_cluster" {
  replication_group_id = "ecommerce-redis"
  description          = "Redis cluster for caching"

  engine               = "redis"
  engine_version       = "7.0"
  node_type            = "cache.r6g.xlarge"
  num_cache_clusters   = 6  # 3 masters + 3 replicas

  automatic_failover_enabled = true
  multi_az_enabled           = true

  subnet_group_name = aws_elasticache_subnet_group.redis.name
  security_group_ids = [aws_security_group.redis.id]

  snapshot_retention_limit = 5
  snapshot_window          = "03:00-05:00"

  tags = {
    Environment = "production"
  }
}
```

**Deployment:**
```bash
# Initialize
terraform init

# Plan (preview changes)
terraform plan -out=tfplan

# Apply (execute changes)
terraform apply tfplan

# Destroy (tear down)
terraform destroy
```

---

## 📊 PART 7: Monitoring & Observability

### Monitoring Tools

**Prometheus + Grafana**
```
Why Prometheus?
✓ Time-series database
✓ Pull-based metrics collection
✓ PromQL query language
✓ Alerting (Alertmanager)

Metrics Collected:
- Application: request_duration_seconds, requests_total
- System: cpu_usage, memory_usage, disk_io
- Business: orders_per_second, revenue_per_minute

Grafana Dashboards:
- API Performance (latency, throughput, errors)
- Database Performance (connections, query time)
- Infrastructure (CPU, memory, network)
- Business Metrics (GMV, orders, conversion)
```

**Datadog**
```
Why Datadog?
✓ All-in-one (metrics, logs, traces)
✓ APM (Application Performance Monitoring)
✓ Real User Monitoring (RUM)
✓ Cloud integration (AWS, GCP, Azure)

Features:
- Distributed tracing (end-to-end request flow)
- Log management (centralized logging)
- Synthetic monitoring (uptime checks)
- Anomaly detection (ML-powered)

Pricing: $15-$23 per host per month
```

**New Relic**
```
Similar to Datadog:
- APM (code-level performance)
- Infrastructure monitoring
- Browser monitoring
- Synthetic monitoring
```

### Logging

**ELK Stack (Elasticsearch, Logstash, Kibana)**
```
Architecture:
Application Logs → Filebeat → Logstash → Elasticsearch → Kibana

Logstash Pipeline:
input {
  beats {
    port => 5044
  }
}

filter {
  json {
    source => "message"
  }

  grok {
    match => { "message" => "%{COMBINEDAPACHELOG}" }
  }

  date {
    match => [ "timestamp", "ISO8601" ]
  }
}

output {
  elasticsearch {
    hosts => ["elasticsearch:9200"]
    index => "logs-%{+YYYY.MM.dd}"
  }
}

Index Lifecycle Management:
- Hot: 0-7 days (on fast SSD)
- Warm: 7-30 days (on slower SSD)
- Cold: 30-90 days (on HDD)
- Delete: >90 days
```

**Splunk**
```
Enterprise logging solution:
- Log aggregation
- Search & analysis
- Alerting
- Dashboards

Pricing: $150+ per GB ingested per year (expensive!)
```

### Distributed Tracing

**Jaeger**
```
Why Jaeger?
✓ Open-source (CNCF project)
✓ Distributed tracing
✓ Root cause analysis
✓ Performance optimization

Trace Example:
User Request → API Gateway (5ms)
            → Product Service (20ms)
               → MongoDB (15ms)
               → Redis Cache (2ms)
            → Recommendation Service (30ms)
               → ML Model Inference (25ms)
Total: 55ms

Benefits:
- Identify bottlenecks (ML inference is slow)
- Detect failures (which service errored?)
- Optimize critical paths
```

**AWS X-Ray**
```
AWS-native tracing:
- Integrated with Lambda, ECS, API Gateway
- Service map visualization
- Trace analysis

Use: If fully on AWS, X-Ray is easiest
```

---

## 🔐 PART 8: Security & Compliance

### Security Tools

**Web Application Firewall (WAF)**
```
AWS WAF / Cloudflare WAF:
- SQL injection protection
- XSS protection
- Rate limiting (per IP)
- Geo-blocking
- Bot detection

Rules:
1. Block IPs with >100 requests/min
2. Block SQL injection patterns (UNION SELECT, OR 1=1)
3. Block XSS patterns (<script>, javascript:)
4. Allow only specific countries (if regional site)
```

**DDoS Protection**
```
AWS Shield Advanced:
- Layer 3/4 DDoS protection (SYN flood, UDP flood)
- Layer 7 DDoS protection (HTTP flood)
- DDoS cost protection (AWS credits during attack)

Cloudflare:
- Free tier includes DDoS protection
- Unlimited bandwidth (absorbs attacks)
- 200+ global PoPs
```

**Secrets Management**
```
HashiCorp Vault:
- Dynamic secrets (rotate DB credentials automatically)
- Encryption as a service
- PKI management
- Audit logging

AWS Secrets Manager:
- Automatic rotation (RDS, Redshift)
- Integration with RDS, Lambda
- Pricing: $0.40/secret/month + $0.05/10K API calls

Kubernetes Secrets:
- Base64 encoded (NOT encrypted by default!)
- Use external secrets operator (sync from Vault/AWS Secrets Manager)
```

### Compliance

**PCI-DSS (Payment Card Industry)**
```
Requirements:
1. Never store raw card numbers (use tokenization)
2. Encrypt data in transit (TLS 1.2+)
3. Encrypt data at rest (AES-256)
4. Network segmentation (isolate payment systems)
5. Regular vulnerability scans
6. Access controls (principle of least privilege)
7. Audit logging (all access to cardholder data)

Easiest Approach:
- Use Stripe/PayPal (they handle PCI compliance)
- Only store tokenized card data
- Reduces PCI scope massively
```

**GDPR (General Data Protection Regulation)**
```
Requirements:
1. User consent (explicit opt-in)
2. Right to access (export user data)
3. Right to deletion (delete all user data)
4. Data portability (export in machine-readable format)
5. Privacy by design (minimize data collection)

Implementation:
- Data export API: GET /api/user/{id}/export
- Data deletion: DELETE /api/user/{id} (hard delete from all DBs)
- Audit logs: Track all access to PII
```

---

## 🚀 PART 9: Real-World E-Commerce Tech Stacks

### Amazon

```
Frontend:
- React, Next.js (SSR for SEO)
- CloudFront (CDN)

Backend:
- Java (Spring Boot)
- AWS Lambda (serverless functions)
- API Gateway

Databases:
- DynamoDB (cart, sessions)
- RDS PostgreSQL (orders, payments)
- DocumentDB (product catalog)
- ElastiCache (Redis)
- OpenSearch (product search)

Messaging:
- AWS SQS/SNS
- Apache Kafka (internal)

Infrastructure:
- EC2, ECS, EKS
- S3 (product images, backups)
- Route 53 (DNS)

Monitoring:
- CloudWatch
- Custom internal tools
```

### Shopify

```
Frontend:
- React, TypeScript
- Polaris (design system)

Backend:
- Ruby on Rails (monolith + microservices)
- Go (performance-critical services)

Databases:
- MySQL (sharded)
- Redis (caching)
- Kafka (event streaming)

Infrastructure:
- GCP, AWS
- Kubernetes
- Docker

Scale:
- 1.7M+ merchants
- $444B+ GMV (2022)
- Black Friday: 80M shoppers, 44M orders
```

### Flipkart (India)

```
Frontend:
- React Native (mobile)
- Next.js (web)

Backend:
- Java (Spring Boot)
- Node.js (API Gateway)

Databases:
- MySQL (sharded)
- MongoDB (product catalog)
- Redis (caching)
- Elasticsearch (search)

Cloud:
- Self-hosted data centers + AWS/GCP

Big Data:
- Hadoop, Spark
- Kafka
```

---

## 📝 Summary: Recommended Tech Stack for E-Commerce

### For Startups (MVP)

```
Frontend: React + Next.js
Backend: Node.js (Express) or Python (FastAPI)
Database: PostgreSQL
Cache: Redis
Search: Elasticsearch / Algolia
Deployment: AWS / GCP
CI/CD: GitHub Actions
Monitoring: Datadog (free tier)
```

### For Growth Stage (Scaling)

```
Frontend: React + Next.js, React Native
Backend: Java (Spring Boot) microservices
Databases:
  - PostgreSQL (orders, payments, inventory)
  - MongoDB (product catalog)
  - Redis (cache, sessions)
  - Elasticsearch (search)
Message Queue: Kafka
Cloud: AWS / GCP (multi-region)
Container Orchestration: Kubernetes (EKS/GKE)
CI/CD: GitLab CI / GitHub Actions
Monitoring: Prometheus + Grafana + Jaeger
IaC: Terraform
```

### For Enterprise (Amazon-Scale)

```
All of the above, plus:
- Service Mesh: Istio
- API Management: Kong Enterprise
- Data Warehouse: BigQuery / Redshift
- ML Platform: SageMaker / Vertex AI
- CDN: CloudFront + Akamai (multi-CDN)
- Security: WAF, DDoS protection, Vault
- Observability: Datadog + Custom tools
- Compliance: PCI-DSS, GDPR, SOC 2
```

---

**This tech stack guide provides the real-world technologies used by Amazon, Flipkart, and other top e-commerce platforms. Use it as a reference when designing your own systems!** 🛒🚀
