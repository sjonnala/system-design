# 🔧 Recommendation Engine: Complete Tech Stack & DevOps Guide

## Industry-Standard Technology Stack for Production ML Recommendation Systems

This document provides a comprehensive overview of the technology stack used by companies like **Netflix, Amazon, YouTube, Spotify, LinkedIn, TikTok** for building large-scale recommendation engines.

---

## 📋 Table of Contents

1. [Programming Languages & Frameworks](#programming-languages--frameworks)
2. [ML/AI Frameworks & Libraries](#mlai-frameworks--libraries)
3. [Data Storage Solutions](#data-storage-solutions)
4. [Feature Store](#feature-store)
5. [Message Queues & Stream Processing](#message-queues--stream-processing)
6. [Caching & In-Memory](#caching--in-memory)
7. [Vector Databases & ANN Search](#vector-databases--ann-search)
8. [Model Serving & Inference](#model-serving--inference)
9. [Orchestration & Workflow](#orchestration--workflow)
10. [Monitoring & Observability](#monitoring--observability)
11. [DevOps & CI/CD](#devops--cicd)
12. [Cloud Providers & Managed Services](#cloud-providers--managed-services)
13. [Real-World Tech Stacks by Company](#real-world-tech-stacks-by-company)

---

## 1. Programming Languages & Frameworks

### Backend Services

| **Language** | **Use Case** | **Why** | **Companies Using** |
|--------------|--------------|---------|---------------------|
| **Python** | ML training, data pipelines, prototyping | Rich ML ecosystem, fast development | Netflix, Spotify, Uber, Airbnb |
| **Java** | High-performance serving, microservices | Battle-tested, JVM performance | LinkedIn, Twitter, Amazon |
| **Go (Golang)** | API services, high-concurrency | Fast, efficient, good for microservices | Uber, Dropbox, Pinterest |
| **Scala** | Spark jobs, stream processing | Functional, works with Spark | Netflix, LinkedIn, Twitter |
| **C++** | Low-latency inference, custom kernels | Maximum performance | Google, Meta, TikTok |
| **Rust** | Performance-critical services | Memory safety + performance | Discord, Cloudflare |

### Web Frameworks

```python
# Python
- FastAPI (modern, async, type hints)
- Flask (lightweight, flexible)
- Django (full-featured, batteries included)

# Java
- Spring Boot (microservices, dependency injection)
- Micronaut (cloud-native, fast startup)
- Quarkus (GraalVM, Kubernetes-native)

# Go
- Gin (HTTP framework, fast)
- Echo (minimalist, high performance)
- gRPC (Google RPC framework)
```

---

## 2. ML/AI Frameworks & Libraries

### Deep Learning Frameworks

| **Framework** | **Strengths** | **Use Cases** | **Companies** |
|---------------|---------------|---------------|---------------|
| **TensorFlow** | Production-ready, TF Serving, TFX | Two-tower models, wide & deep | Google, Airbnb, Twitter |
| **PyTorch** | Research-friendly, dynamic graphs | Transformers, experimental models | Meta, Uber, Spotify |
| **JAX** | Automatic differentiation, XLA compilation | Research, custom training loops | Google (DeepMind), Hugging Face |
| **Keras** | High-level API, easy prototyping | Quick experiments, beginners | Startups, rapid prototyping |
| **MXNet** | Distributed training, efficient | Large-scale training | Amazon (legacy) |

### ML Libraries

```python
# Collaborative Filtering & Matrix Factorization
- Implicit (ALS, BPR algorithms)
- Surprise (CF library)
- LightFM (hybrid CF + content)

# Gradient Boosting
- XGBoost (gradient boosted trees, high performance)
- LightGBM (faster, lower memory, Microsoft)
- CatBoost (categorical features, Yandex)

# Embeddings & NLP
- Sentence-Transformers (text embeddings)
- Gensim (Word2Vec, Doc2Vec)
- spaCy (NLP pipelines)
- Hugging Face Transformers (BERT, GPT models)

# Recommendation-Specific
- TensorFlow Recommenders (TFRS) - Google
- PyTorch Geometric (graph neural networks)
- RecBole (unified RecSys framework)
- Microsoft Recommenders (best practices, utils)

# Approximate Nearest Neighbors
- FAISS (Facebook AI Similarity Search)
- Annoy (Spotify's ANN library)
- ScaNN (Google's scalable ANN)
- HNSW (Hierarchical Navigable Small World)

# AutoML & Hyperparameter Tuning
- Optuna (Bayesian optimization)
- Ray Tune (distributed hyperparameter tuning)
- Hyperopt (Tree Parzen Estimator)
- Keras Tuner (for TensorFlow/Keras)
```

### Real-World ML Stack Examples

**Netflix**:
```
Training: Python + TensorFlow + PyTorch
Serving: Java + TensorFlow Serving
Feature Engineering: Spark (Scala)
Experiment Tracking: Internal tools
```

**Spotify**:
```
Training: Python + PyTorch
Serving: Python microservices + TensorFlow Serving
ANN Search: Annoy (custom library)
Feature Store: Custom (Hendrix)
```

---

## 3. Data Storage Solutions

### Relational Databases (OLTP)

| **Database** | **Use Case** | **Scale** | **Companies** |
|--------------|--------------|-----------|---------------|
| **PostgreSQL** | User profiles, item metadata, ACID transactions | Millions of rows | Uber, Instagram, Reddit |
| **MySQL** | User accounts, content catalog | Millions of rows | YouTube, LinkedIn, Twitter |
| **Amazon Aurora** | Managed PostgreSQL/MySQL, high availability | Auto-scaling | Netflix, Airbnb |
| **Google Cloud SQL** | Managed MySQL/PostgreSQL | Managed scaling | Spotify |
| **CockroachDB** | Distributed SQL, geo-replication | Global scale | DoorDash, Comcast |

### NoSQL Databases

| **Database** | **Type** | **Use Case** | **Scale** | **Companies** |
|--------------|----------|--------------|-----------|---------------|
| **Cassandra** | Wide-column | Time-series interaction events, high write throughput | Billions of rows | Netflix, Apple, Discord |
| **DynamoDB** | Key-value | User sessions, fast lookups | Unlimited | Amazon, Lyft, Airbnb |
| **MongoDB** | Document | Flexible schemas, rapid development | Millions of docs | eBay, Uber Eats |
| **HBase** | Wide-column | Hadoop ecosystem, batch + real-time | Petabytes | Meta, Pinterest |
| **Redis** | In-memory | Caching, session store, real-time features | TBs in memory | Twitter, GitHub, Stack Overflow |

### Data Lakes & Warehouses

| **Solution** | **Type** | **Use Case** | **Companies** |
|--------------|----------|--------------|---------------|
| **Amazon S3** | Object storage | Raw events, training data, model artifacts | Netflix, Airbnb, Lyft |
| **Google Cloud Storage** | Object storage | Data lake, cold storage | Spotify, Twitter |
| **Azure Blob Storage** | Object storage | Archival, backups | LinkedIn |
| **HDFS** | Distributed file system | Hadoop ecosystem (legacy) | Yahoo, Cloudera customers |
| **Delta Lake** | Lakehouse | ACID on data lakes, time travel | Databricks customers |

### Analytical Databases (OLAP)

| **Database** | **Strengths** | **Use Case** | **Companies** |
|--------------|---------------|--------------|---------------|
| **ClickHouse** | Columnar, fast aggregations | Real-time analytics, click events | Uber, CloudFlare |
| **BigQuery** | Serverless, SQL on petabytes | Data warehouse, ML training datasets | Spotify, Twitter |
| **Snowflake** | Cloud data warehouse, separation of compute/storage | Analytics, BI dashboards | DoorDash, Adobe |
| **Redshift** | AWS data warehouse | OLAP queries, aggregations | Netflix (legacy), Yelp |
| **Druid** | Real-time analytics, time-series | Event analytics dashboards | Airbnb, Netflix |

---

## 4. Feature Store

**Why Feature Stores?**
- Centralized feature management
- Training-serving consistency
- Feature reusability across models
- Point-in-time correctness
- Versioning and lineage tracking

| **Solution** | **Type** | **Features** | **Companies** |
|--------------|----------|--------------|---------------|
| **Feast** | Open-source | Online + offline store, SDK for Python/Java | Gojek, GetYourGuide |
| **Tecton** | Managed | Real-time features, feature pipelines, monitoring | Coinbase, Zoom |
| **AWS SageMaker Feature Store** | Managed | Integrated with SageMaker, low latency | AWS customers |
| **Databricks Feature Store** | Managed | Unity Catalog integration, Delta Lake | Databricks customers |
| **Hopsworks** | Open-source + managed | HSFS, feature monitoring | Banks, healthcare |
| **Google Vertex AI Feature Store** | Managed | GCP integration, BigQuery connector | GCP customers |

**Netflix's Custom Feature Store**:
```
- Name: Not publicly disclosed (proprietary)
- Storage: Cassandra (online) + S3 (offline)
- Compute: Spark for feature engineering
- Serving: Custom microservices (Java)
- Scale: Billions of features, <10ms p99 latency
```

**Uber's Michelangelo**:
```
- Uber's internal ML platform
- Feature Store: Integrated with Michelangelo
- Storage: Hive (offline), Cassandra (online)
- Serving: Custom DSL for feature pipelines
```

---

## 5. Message Queues & Stream Processing

### Message Queues

| **Technology** | **Type** | **Use Case** | **Throughput** | **Companies** |
|----------------|----------|--------------|----------------|---------------|
| **Apache Kafka** | Distributed log | Event streaming, high throughput | Millions of msgs/sec | LinkedIn, Uber, Netflix, Airbnb |
| **AWS Kinesis** | Managed streaming | AWS-native, auto-scaling | 1 MB/sec per shard | Netflix, Zillow |
| **Google Pub/Sub** | Managed messaging | GCP-native, global | 100s of MB/sec | Spotify (via GCP) |
| **RabbitMQ** | Message broker | Complex routing, AMQP | 10K-50K msgs/sec | T-Mobile, Runtastic |
| **Apache Pulsar** | Distributed messaging | Multi-tenancy, geo-replication | Millions of msgs/sec | Yahoo, Tencent |
| **NATS** | Lightweight messaging | Microservices, IoT | Millions of msgs/sec | Netlify, Synadia |

### Stream Processing

| **Framework** | **Language** | **Use Case** | **Scale** | **Companies** |
|---------------|--------------|--------------|-----------|---------------|
| **Apache Flink** | Java/Scala | Real-time analytics, stateful processing | Petabytes/day | Uber, Alibaba, Netflix |
| **Apache Spark Streaming** | Scala/Python | Micro-batch processing | Terabytes/day | Netflix, Pinterest, Uber |
| **Kafka Streams** | Java | Stream processing within Kafka | High | LinkedIn, Confluent |
| **Google Dataflow** | Java/Python (Apache Beam) | Unified batch + stream | Managed | Spotify, Lyft |
| **AWS Kinesis Data Analytics** | SQL/Java | Real-time SQL queries on streams | Managed | AWS customers |

**Real-World Stream Processing at Netflix**:
```
Technology: Apache Flink
Use Case: Real-time user interaction processing
Scale: 8 trillion events/day
Pipeline:
  1. Kafka → Flink → Feature updates
  2. Windowed aggregations (5-min, 1-hour)
  3. Output to Redis (online features) + S3 (offline)
```

---

## 6. Caching & In-Memory

### In-Memory Data Stores

| **Technology** | **Type** | **Use Case** | **Latency** | **Companies** |
|----------------|----------|--------------|-------------|---------------|
| **Redis** | Key-value | Session cache, feature cache, recommendations | <1ms | Twitter, GitHub, Stack Overflow, Airbnb |
| **Memcached** | Key-value | Simple caching, session store | <1ms | Facebook (legacy), Pinterest |
| **Amazon ElastiCache** | Managed Redis/Memcached | AWS-native caching | <1ms | Netflix, Airbnb |
| **Google Cloud Memorystore** | Managed Redis | GCP-native | <1ms | Spotify |
| **Apache Ignite** | Distributed cache + compute | In-memory SQL, ACID transactions | <10ms | ING Bank, Sberbank |
| **Hazelcast** | Distributed cache | Java-native, distributed data structures | <10ms | T-Mobile, Bloomberg |

### Caching Strategies

```python
# Multi-tier caching for recommendations
class RecommendationCache:
    """
    L1 (Local in-memory): ~5ms
    L2 (Redis cluster):   ~10ms
    L3 (Database):        ~50-100ms
    """

    def get_recommendations(self, user_id, context):
        # L1: Check local cache (LRU, 1-minute TTL)
        if user_id in self.local_cache:
            return self.local_cache[user_id]

        # L2: Check Redis
        cache_key = f"recs:{user_id}:{context['device']}"
        cached = redis_client.get(cache_key)
        if cached:
            self.local_cache[user_id] = cached
            return cached

        # L3: Generate fresh recommendations
        recs = self.generate_recommendations(user_id, context)

        # Cache at all levels
        redis_client.setex(cache_key, ttl=900, value=recs)  # 15 min
        self.local_cache[user_id] = recs

        return recs
```

**Netflix Caching Architecture**:
```
- EVCache (custom memcached cluster)
- Scale: 30+ clusters, 100+ TB of data
- Purpose: Movie metadata, user profiles, recommendations
- Latency: <1ms p99
- Hit rate: 99%+
```

---

## 7. Vector Databases & ANN Search

### Vector Database Solutions

| **Technology** | **Type** | **Index Type** | **Scale** | **Companies** |
|----------------|----------|----------------|-----------|---------------|
| **FAISS** | Library (C++/Python) | IVF, HNSW, PQ | Billions of vectors | Meta, OpenAI, Cohere |
| **Pinecone** | Managed cloud | HNSW | Billions of vectors | ChatGPT plugins, Canva |
| **Milvus** | Open-source distributed | IVF, HNSW, DiskANN | Billions of vectors | eBay, NVIDIA, Shopee |
| **Weaviate** | Open-source ML-native | HNSW | Millions-billions | Rocket.Chat, Stack Overflow |
| **Qdrant** | Open-source Rust | HNSW | Millions-billions | Startups, Hetzner |
| **Chroma** | Open-source embedding DB | HNSW | Millions | LangChain community |
| **Vespa** | Distributed search + ML | Custom | Billions | Yahoo, Verizon Media |

### ANN Algorithm Comparison

| **Algorithm** | **Recall** | **Latency** | **Memory** | **Use Case** |
|---------------|------------|-------------|------------|--------------|
| **Flat (Brute Force)** | 100% | High | High | Benchmarking, small datasets |
| **IVF (Inverted File)** | 90-95% | Medium | Medium | Balanced performance |
| **HNSW** | 95-98% | Low | High | High recall needed |
| **PQ (Product Quantization)** | 80-90% | Low | Very low | Memory-constrained |
| **IVF + PQ** | 85-92% | Low | Low | Production (best trade-off) |

**Spotify's Annoy (Approximate Nearest Neighbors Oh Yeah)**:
```python
# Custom ANN library used by Spotify
from annoy import AnnoyIndex

# Build index
dimension = 128
index = AnnoyIndex(dimension, 'angular')  # Cosine similarity

for i, vector in enumerate(item_embeddings):
    index.add_item(i, vector)

index.build(n_trees=50)  # More trees = better recall, slower build
index.save('items.ann')

# Search
query_vector = get_user_embedding(user_id)
similar_items = index.get_nns_by_vector(query_vector, n=500)
```

**YouTube's ScaNN**:
```
- Technology: ScaNN (Google)
- Scale: 100M+ video embeddings
- Latency: <20ms for top-500 candidates
- Accuracy: 95%+ recall@500
```

---

## 8. Model Serving & Inference

### Serving Frameworks

| **Framework** | **Language** | **Features** | **Companies** |
|---------------|--------------|--------------|---------------|
| **TensorFlow Serving** | C++/gRPC | Versioning, batching, GPU support | Google, Uber, Airbnb |
| **TorchServe** | Python/Java | PyTorch models, multi-model | Meta, AWS |
| **NVIDIA Triton** | C++ | Multi-framework, GPU optimization | NVIDIA customers |
| **Seldon Core** | Kubernetes | Model deployment, A/B testing | Kubeflow users |
| **BentoML** | Python | Model packaging, REST/gRPC APIs | Startups |
| **KServe (KFServing)** | Kubernetes | Serverless inference | Kubeflow users |
| **Ray Serve** | Python | Scalable model serving, Python-native | Anyscale customers |

### Custom Serving Solutions

**Netflix**:
```java
// Custom Java-based model serving
class ModelServingService {
    private TensorFlowModel model;
    private FeatureStore featureStore;

    public List<Recommendation> recommend(long userId, int count) {
        // Fetch features
        Features userFeatures = featureStore.getUserFeatures(userId);

        // Generate candidates (from ANN search)
        List<Long> candidateIds = annSearchService.search(
            userFeatures.embedding, k=500
        );

        // Rank candidates with ML model
        List<Recommendation> ranked = model.rank(
            userId, candidateIds, userFeatures
        );

        return ranked.subList(0, count);
    }
}
```

**Uber Michelangelo**:
```
- Custom model serving platform
- Supports: TensorFlow, XGBoost, scikit-learn
- Features: Online + batch prediction, A/B testing, monitoring
- Scale: 1000s of models, millions of predictions/sec
- Deployment: Kubernetes pods with auto-scaling
```

---

## 9. Orchestration & Workflow

### Workflow Orchestration

| **Technology** | **Type** | **Use Case** | **Companies** |
|----------------|----------|--------------|---------------|
| **Apache Airflow** | Python DAGs | ETL pipelines, ML training schedules | Airbnb, Twitter, Lyft |
| **Prefect** | Modern Python | Data workflows, modern UI | Databricks, Cruise |
| **Dagster** | Asset-based | Data pipelines, software-defined assets | Elementl customers |
| **Kubeflow Pipelines** | Kubernetes-native | ML pipelines, end-to-end | Google Cloud, Seldon |
| **AWS Step Functions** | Managed state machine | Serverless workflows | AWS customers |
| **Google Cloud Composer** | Managed Airflow | GCP workflows | GCP customers |
| **Argo Workflows** | Kubernetes-native | Container workflows, CI/CD | Intuit, SAP |

**Netflix Data Pipeline**:
```python
# Simplified Airflow DAG for daily training
from airflow import DAG
from airflow.operators.spark_submit_operator import SparkSubmitOperator
from airflow.operators.python_operator import PythonOperator

dag = DAG(
    'recommendation_model_training',
    schedule_interval='0 2 * * *',  # Daily at 2 AM UTC
    catchup=False
)

# Task 1: Extract data from S3
extract_task = SparkSubmitOperator(
    task_id='extract_interactions',
    application='/scripts/extract.py',
    conf={'spark.executor.instances': '100'},
    dag=dag
)

# Task 2: Feature engineering
feature_task = SparkSubmitOperator(
    task_id='feature_engineering',
    application='/scripts/features.py',
    dag=dag
)

# Task 3: Model training
train_task = PythonOperator(
    task_id='train_model',
    python_callable=train_tensorflow_model,
    dag=dag
)

# Task 4: Model evaluation
eval_task = PythonOperator(
    task_id='evaluate_model',
    python_callable=evaluate_on_testset,
    dag=dag
)

# Task 5: Deploy if metrics improved
deploy_task = PythonOperator(
    task_id='deploy_model',
    python_callable=deploy_to_production,
    dag=dag
)

extract_task >> feature_task >> train_task >> eval_task >> deploy_task
```

### Container Orchestration

| **Technology** | **Use Case** | **Scale** | **Companies** |
|----------------|--------------|-----------|---------------|
| **Kubernetes** | Container orchestration, microservices | 1000s of pods | Google, Spotify, Airbnb, Uber |
| **Amazon ECS/EKS** | AWS container management | Auto-scaling | Netflix, Expedia |
| **Google GKE** | Managed Kubernetes | Managed | Spotify, Snapchat |
| **Azure AKS** | Managed Kubernetes on Azure | Managed | LinkedIn (Microsoft) |
| **Nomad** | Lightweight orchestration | Simpler than K8s | Cloudflare, Roblox |
| **Docker Swarm** | Simple container clustering | Small scale | Legacy, simple deployments |

---

## 10. Monitoring & Observability

### Metrics & Monitoring

| **Technology** | **Type** | **Use Case** | **Companies** |
|----------------|----------|--------------|---------------|
| **Prometheus** | Time-series DB | Metrics collection, alerting | SoundCloud, DigitalOcean, GitLab |
| **Grafana** | Visualization | Dashboards, alerting | Uber, eBay, Red Hat |
| **Datadog** | SaaS monitoring | Full-stack observability | Airbnb, Peloton, Samsung |
| **New Relic** | APM | Application performance | GitHub, DoorDash |
| **AWS CloudWatch** | AWS monitoring | Metrics, logs, alarms | AWS customers |
| **Google Cloud Monitoring** | GCP monitoring | Stackdriver, traces | GCP customers |

### Distributed Tracing

| **Technology** | **Protocol** | **Use Case** | **Companies** |
|----------------|--------------|--------------|---------------|
| **Jaeger** | OpenTracing | Request tracing, latency analysis | Uber, Red Hat |
| **Zipkin** | OpenTelemetry | Distributed tracing | Twitter, SoundCloud |
| **AWS X-Ray** | AWS-native | Trace AWS services | AWS customers |
| **Google Cloud Trace** | GCP-native | GCP service tracing | GCP customers |
| **Lightstep** | OpenTelemetry | Enterprise tracing | Twilio, GitHub |

### Logging

| **Technology** | **Type** | **Use Case** | **Companies** |
|----------------|----------|--------------|---------------|
| **ELK Stack** (Elasticsearch, Logstash, Kibana) | Search + analytics | Log aggregation, search | LinkedIn, Netflix, Walmart |
| **Splunk** | Enterprise logs | SIEM, analytics | Cisco, Domino's |
| **Fluentd** | Log collector | Unified logging layer | AWS, Microsoft |
| **Loki** | Log aggregation | Prometheus-inspired logs | Grafana Labs users |
| **AWS CloudWatch Logs** | Managed | AWS log aggregation | AWS customers |

**Netflix Monitoring Stack**:
```
Metrics: Atlas (custom time-series DB)
Dashboards: Grafana + custom tools
Tracing: Custom distributed tracing
Logging: ELK stack
Alerting: PagerDuty + Slack integration
ML Model Monitoring: Custom dashboards
  - Model drift detection
  - Prediction distribution shifts
  - Feature drift analysis
```

---

## 11. DevOps & CI/CD

### CI/CD Platforms

| **Platform** | **Type** | **Features** | **Companies** |
|--------------|----------|--------------|---------------|
| **Jenkins** | Self-hosted | Plugins, pipelines | Amazon, RedHat, Sony |
| **GitLab CI/CD** | Integrated | Git-native, Kubernetes integration | Siemens, T-Mobile |
| **GitHub Actions** | Cloud/self-hosted | YAML workflows, marketplace | Spotify, Shopify |
| **CircleCI** | Cloud | Fast, parallelism | Facebook, Spotify |
| **Travis CI** | Cloud | Open-source friendly | Mozilla, Zendesk |
| **AWS CodePipeline** | Managed | AWS-native | AWS customers |
| **Google Cloud Build** | Managed | GCP-native | GCP customers |
| **Argo CD** | Kubernetes-native | GitOps, declarative | Intuit, Adobe |

### Infrastructure as Code

| **Tool** | **Type** | **Use Case** | **Companies** |
|----------|----------|--------------|---------------|
| **Terraform** | Multi-cloud IaC | Provision infrastructure | Uber, Slack, Instacart |
| **AWS CloudFormation** | AWS IaC | AWS resources | Netflix, Expedia |
| **Pulumi** | Multi-cloud IaC | TypeScript/Python IaC | Mercedes-Benz, Snowflake |
| **Ansible** | Configuration management | Server provisioning | NASA, RedHat |
| **Chef** | Configuration management | Infrastructure automation | Facebook, Etsy |
| **Kubernetes Operators** | K8s-native | Custom resource management | Many K8s users |

### ML-Specific CI/CD

```yaml
# GitLab CI/CD Pipeline for ML Model
stages:
  - data_validation
  - feature_engineering
  - train
  - evaluate
  - deploy

data_validation:
  stage: data_validation
  script:
    - python scripts/validate_data.py
    - python scripts/check_data_drift.py

feature_engineering:
  stage: feature_engineering
  script:
    - spark-submit scripts/feature_pipeline.py
    - python scripts/validate_features.py

train_model:
  stage: train
  script:
    - python train.py --config configs/prod.yaml
    - python scripts/save_model.py
  artifacts:
    paths:
      - models/

evaluate_model:
  stage: evaluate
  script:
    - python evaluate.py --test-data s3://test-data/
    - python scripts/compare_to_baseline.py
  only:
    - master

deploy_canary:
  stage: deploy
  script:
    - kubectl apply -f k8s/canary-deployment.yaml
    - python scripts/monitor_canary.py --duration 1h
  only:
    - master
  when: manual

deploy_production:
  stage: deploy
  script:
    - kubectl apply -f k8s/production-deployment.yaml
    - python scripts/update_model_registry.py
  only:
    - master
  when: manual
```

---

## 12. Cloud Providers & Managed Services

### Cloud Provider Comparison

| **Service Type** | **AWS** | **Google Cloud** | **Azure** |
|------------------|---------|------------------|-----------|
| **Compute** | EC2, Lambda | Compute Engine, Cloud Functions | Virtual Machines, Functions |
| **Containers** | ECS, EKS | GKE | AKS |
| **Object Storage** | S3 | Cloud Storage | Blob Storage |
| **Data Warehouse** | Redshift | BigQuery | Synapse Analytics |
| **Streaming** | Kinesis | Pub/Sub, Dataflow | Event Hubs, Stream Analytics |
| **ML Platform** | SageMaker | Vertex AI | Azure ML |
| **Feature Store** | SageMaker Feature Store | Vertex AI Feature Store | Azure ML Feature Store |
| **Vector Search** | OpenSearch, Kendra | Vertex AI Matching Engine | Cognitive Search |
| **NoSQL** | DynamoDB | Firestore, Bigtable | Cosmos DB |
| **Cache** | ElastiCache | Memorystore | Cache for Redis |
| **Monitoring** | CloudWatch | Cloud Monitoring | Monitor |

### Company Cloud Choices

**Netflix**: AWS (primary)
```
- EC2 for compute (100K+ instances)
- S3 for storage (petabytes)
- DynamoDB for session data
- ElastiCache (Redis) for caching
- Lambda for serverless functions
```

**Spotify**: Google Cloud Platform
```
- GKE for Kubernetes
- BigQuery for data warehouse
- Cloud Storage for data lake
- Pub/Sub for messaging
- Dataflow for stream processing
```

**Uber**: Multi-cloud (AWS + GCP)
```
- AWS: Primary infrastructure
- GCP: BigQuery for analytics, ML training
- Custom Michelangelo platform on Kubernetes
```

---

## 13. Real-World Tech Stacks by Company

### 🎬 Netflix Recommendation System

```
┌─────────────────────────────────────────────────────────┐
│                    NETFLIX TECH STACK                   │
└─────────────────────────────────────────────────────────┘

LANGUAGES:
- Python (ML training, data science)
- Java (backend services, high performance)
- Scala (Spark jobs)
- JavaScript/TypeScript (frontend)

ML FRAMEWORKS:
- TensorFlow (primary for production models)
- PyTorch (research, experimentation)
- XGBoost (feature-based ranking)

DATA STORAGE:
- S3 (data lake, petabytes of data)
- Cassandra (user interactions, time-series)
- MySQL (metadata, catalog)
- EVCache (memcached cluster, caching)

STREAMING & MESSAGING:
- Apache Kafka (event streaming)
- Apache Flink (real-time analytics)
- Mantis (custom reactive stream processing)

MODEL SERVING:
- Custom Java microservices
- TensorFlow Serving (for some models)
- Zuul (API gateway)

FEATURE ENGINEERING:
- Spark (batch processing)
- S3 (offline feature store)
- Cassandra (online feature store)

ORCHESTRATION:
- Titus (container management, custom)
- Meson (workflow orchestration)
- Jenkins (CI/CD)

MONITORING:
- Atlas (custom time-series DB)
- Grafana (dashboards)
- Spectator (metrics library)

INFRASTRUCTURE:
- AWS (primary cloud provider)
- 100K+ EC2 instances
- Multi-region deployment
```

### 🎵 Spotify Recommendation System

```
┌─────────────────────────────────────────────────────────┐
│                   SPOTIFY TECH STACK                    │
└─────────────────────────────────────────────────────────┘

LANGUAGES:
- Python (ML, data pipelines)
- Java (backend services)
- Scala (Spark jobs)
- Go (some microservices)

ML FRAMEWORKS:
- TensorFlow (production models)
- PyTorch (research)
- LightGBM (ranking models)

DATA STORAGE:
- Google BigQuery (data warehouse)
- Cloud Storage (data lake)
- Cassandra (user data)
- PostgreSQL (metadata)
- Cloud Memorystore (Redis, caching)

STREAMING:
- Google Pub/Sub (messaging)
- Google Dataflow (stream processing)
- Kafka (event streaming)

MODEL SERVING:
- Custom Python microservices
- TensorFlow Serving
- Luigi (workflow orchestration)

ANN SEARCH:
- Annoy (custom library by Spotify)
- FAISS (experimentation)

FEATURE STORE:
- Hendrix (custom feature store)
- BigQuery (offline)
- Redis (online)

ORCHESTRATION:
- Google Kubernetes Engine (GKE)
- Styx (workflow scheduler)
- Apache Airflow

MONITORING:
- Google Cloud Monitoring
- Prometheus + Grafana
- Datadog

INFRASTRUCTURE:
- Google Cloud Platform (primary)
- Multi-region deployment
```

### 📦 Amazon Product Recommendations

```
┌─────────────────────────────────────────────────────────┐
│                    AMAZON TECH STACK                    │
└─────────────────────────────────────────────────────────┘

LANGUAGES:
- Java (primary backend language)
- Python (ML, data science)
- C++ (performance-critical services)

ML FRAMEWORKS:
- MXNet (deep learning, Amazon's framework)
- XGBoost (ranking)
- TensorFlow (transitioning to)

DATA STORAGE:
- DynamoDB (primary NoSQL, user sessions)
- S3 (data lake, exabytes)
- RDS/Aurora (relational data)
- ElastiCache (Redis, caching)

STREAMING:
- Amazon Kinesis (event streaming)
- Kinesis Data Analytics (stream processing)
- Internal messaging systems

MODEL SERVING:
- SageMaker (managed ML platform)
- Custom Java services
- Lambda (serverless inference)

FEATURE ENGINEERING:
- EMR (Spark on AWS)
- SageMaker Processing
- Glue (ETL)

ORCHESTRATION:
- Step Functions (workflow orchestration)
- ECS/EKS (container management)
- Internal build tools

MONITORING:
- CloudWatch (metrics, logs)
- X-Ray (distributed tracing)
- Internal monitoring tools

INFRASTRUCTURE:
- AWS (owns the platform)
- Global data centers
- Edge locations (CloudFront)
```

### 📹 YouTube Recommendation System

```
┌─────────────────────────────────────────────────────────┐
│                   YOUTUBE TECH STACK                    │
└─────────────────────────────────────────────────────────┘

LANGUAGES:
- Python (ML, data pipelines)
- C++ (high-performance serving)
- Java (backend services)
- Go (microservices)

ML FRAMEWORKS:
- TensorFlow (primary)
- JAX (research)

DATA STORAGE:
- BigQuery (data warehouse, petabytes)
- Bigtable (NoSQL, real-time serving)
- Cloud Storage (video files, data lake)
- Memcache (caching)

STREAMING:
- Google Pub/Sub
- Dataflow (Apache Beam)

MODEL SERVING:
- TensorFlow Serving
- Custom C++ services
- Vertex AI

ANN SEARCH:
- ScaNN (Google's ANN library)
- Custom vector search

FEATURE STORE:
- Feast (open-source)
- Bigtable (online)
- BigQuery (offline)

ORCHESTRATION:
- Kubernetes (GKE)
- Borg (Google's cluster management)
- Airflow (workflow orchestration)

MONITORING:
- Google Cloud Monitoring
- Borgmon (internal)
- Grafana

INFRASTRUCTURE:
- Google Cloud Platform
- Global CDN (millions of edge servers)
```

### 💼 LinkedIn Recommendation System

```
┌─────────────────────────────────────────────────────────┐
│                  LINKEDIN TECH STACK                    │
└─────────────────────────────────────────────────────────┘

LANGUAGES:
- Java (primary backend language)
- Scala (Kafka, Spark)
- Python (ML models)

ML FRAMEWORKS:
- TensorFlow (production models)
- PyTorch (research)
- LinkedIn's Pro-ML (custom ML platform)

DATA STORAGE:
- Espresso (custom NoSQL)
- Voldemort (distributed key-value)
- Azure Blob Storage (data lake)
- Venice (derived data platform)

STREAMING:
- Apache Kafka (created by LinkedIn)
- Samza (stream processing)
- Brooklin (data streaming)

MODEL SERVING:
- Pro-ML (LinkedIn's ML platform)
- Custom Java services

FEATURE ENGINEERING:
- Feathr (open-source feature store by LinkedIn)
- Spark (batch processing)
- Samza (real-time features)

ORCHESTRATION:
- Azkaban (workflow scheduler)
- Kubernetes (Azure AKS)

MONITORING:
- InGraphs (custom monitoring)
- Grafana
- Azure Monitor

INFRASTRUCTURE:
- Microsoft Azure (owned by Microsoft)
- Multi-data center deployment
```

---

## 🎯 Summary: Building a Production Recommendation Engine

### Minimal Viable Tech Stack (Startup)

```
┌─────────────────────────────────────────────────────────┐
│              MINIMAL PRODUCTION STACK                   │
└─────────────────────────────────────────────────────────┘

LANGUAGE: Python
FRAMEWORK: FastAPI
ML: PyTorch + Scikit-learn
DATABASE: PostgreSQL (metadata) + Redis (cache)
STORAGE: S3
MESSAGE QUEUE: Redis Streams (simple) or Kafka
FEATURE STORE: Simple Redis + S3
MODEL SERVING: BentoML or FastAPI
VECTOR SEARCH: FAISS (in-memory)
MONITORING: Prometheus + Grafana
ORCHESTRATION: Docker Compose → Kubernetes (scale up)
CLOUD: AWS (easiest) or GCP
CI/CD: GitHub Actions

COST: $500-2K/month (small scale)
```

### Enterprise Production Stack

```
┌─────────────────────────────────────────────────────────┐
│            ENTERPRISE PRODUCTION STACK                  │
└─────────────────────────────────────────────────────────┘

LANGUAGE: Python + Java/Go
ML FRAMEWORK: TensorFlow + PyTorch + XGBoost
STORAGE:
  - S3/GCS (data lake)
  - Cassandra/DynamoDB (interactions)
  - PostgreSQL/Aurora (metadata)
  - Redis Cluster (caching)
STREAMING: Kafka + Flink
FEATURE STORE: Feast or Tecton
MODEL SERVING: TensorFlow Serving + Kubernetes
VECTOR SEARCH: Pinecone or Milvus
MONITORING: Datadog or Prometheus + Grafana + ELK
ORCHESTRATION: Kubernetes + Airflow
CI/CD: GitLab CI or GitHub Actions + Argo CD
CLOUD: Multi-cloud (AWS + GCP)

COST: $50K-500K/month (depending on scale)
```

---

## 🚀 Getting Started Recommendations

**For Learning & Prototyping**:
```
1. Use Google Colab (free GPUs)
2. TensorFlow/PyTorch for models
3. Scikit-learn for baselines
4. FAISS for vector search
5. FastAPI for serving
6. Docker for packaging
```

**For Small Production (<1M users)**:
```
1. AWS or GCP (choose one)
2. Managed services (RDS, ElastiCache, S3)
3. BentoML or FastAPI for serving
4. GitHub Actions for CI/CD
5. Prometheus + Grafana for monitoring
```

**For Large Production (>10M users)**:
```
1. Kubernetes for orchestration
2. Kafka for event streaming
3. Feature Store (Feast or Tecton)
4. Distributed training (Horovod, Ray)
5. A/B testing infrastructure
6. Advanced monitoring (Datadog, Lightstep)
```

---

**This tech stack guide provides the complete landscape of technologies used in production recommendation systems at scale!** 🎯
