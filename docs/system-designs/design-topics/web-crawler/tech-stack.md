# 🛠️ Web Crawler Technology Stack & Industry Solutions

## Complete Tech Stack for Production Web Crawlers

This document provides a comprehensive guide to the technologies, tools, and DevOps solutions used by industry-leading web crawlers like Google, Bing, Common Crawl, and Archive.org.

---

## 📋 Table of Contents

1. [Programming Languages](#programming-languages)
2. [Crawling Frameworks](#crawling-frameworks)
3. [Message Queues & Streaming](#message-queues--streaming)
4. [Storage Solutions](#storage-solutions)
5. [Caching & In-Memory Stores](#caching--in-memory-stores)
6. [Databases](#databases)
7. [Search & Indexing](#search--indexing)
8. [DevOps & Infrastructure](#devops--infrastructure)
9. [Monitoring & Observability](#monitoring--observability)
10. [Security & Compliance](#security--compliance)
11. [Industry Comparisons](#industry-comparisons)

---

## 1. Programming Languages

### Python 🐍
**Use Case**: Rapid development, prototyping, focused crawlers

**Pros**:
- Rich ecosystem (BeautifulSoup, lxml, Scrapy)
- Easy to learn and maintain
- Excellent for data science integration

**Cons**:
- Slower than compiled languages
- GIL limits true parallelism

**When to Use**: Small-medium scale crawlers (<100M pages)

```python
# Example: Simple Scrapy spider
import scrapy

class MySpider(scrapy.Spider):
    name = 'myspider'
    start_urls = ['https://example.com']

    def parse(self, response):
        for link in response.css('a::attr(href)').getall():
            yield response.follow(link, callback=self.parse)
```

**Companies Using**: Many startups, research projects

---

### Go (Golang) 🔵
**Use Case**: High-performance, concurrent crawling

**Pros**:
- Excellent concurrency (goroutines)
- Fast execution speed
- Low memory footprint
- Easy deployment (single binary)

**Cons**:
- Smaller ecosystem than Python
- Steeper learning curve

**When to Use**: Medium-large scale crawlers (100M-10B pages)

```go
// Example: Colly crawler
package main

import (
    "github.com/gocolly/colly/v2"
    "log"
)

func main() {
    c := colly.NewCollector(
        colly.Async(true),
        colly.MaxDepth(5),
    )

    c.Limit(&colly.LimitRule{
        DomainGlob:  "*",
        Parallelism: 100,
        Delay:       1 * time.Second,
    })

    c.OnHTML("a[href]", func(e *colly.HTMLElement) {
        link := e.Attr("href")
        c.Visit(e.Request.AbsoluteURL(link))
    })

    c.Visit("https://example.com")
    c.Wait()
}
```

**Companies Using**: Medium-sized tech companies, microservices

---

### Java ☕
**Use Case**: Enterprise-grade, battle-tested crawlers

**Pros**:
- Mature ecosystem
- Excellent performance
- Strong type system
- Great for large teams

**Cons**:
- Verbose syntax
- Higher memory usage
- Slower development

**When to Use**: Enterprise crawlers, Google/Bing scale (10B+ pages)

```java
// Example: Apache Nutch
import org.apache.nutch.crawl.Crawler;

public class MyCrawler {
    public static void main(String[] args) {
        Crawler crawler = new Crawler();
        crawler.setTopN(1000);
        crawler.setDepth(5);
        crawler.run();
    }
}
```

**Companies Using**: Google, Bing, LinkedIn

---

### C++ ⚡
**Use Case**: Ultra high-performance, low-level control

**Pros**:
- Fastest execution speed
- Full control over memory
- Efficient resource usage

**Cons**:
- Complex development
- Harder to maintain
- Manual memory management

**When to Use**: Extreme scale crawlers (Google-level), specialized parsers

**Companies Using**: Google (some components), Facebook

---

## 2. Crawling Frameworks

### Apache Nutch (Java)
**Description**: Highly extensible, production-ready web crawler

**Features**:
- Hadoop integration for distributed crawling
- Plugin architecture
- Automatic URL normalization
- robots.txt support
- Focused crawling

**Best For**: Large-scale enterprise crawling

**Setup**:
```bash
# Install Nutch
wget https://archive.apache.org/dist/nutch/2.4/apache-nutch-2.4-bin.tar.gz
tar -xzf apache-nutch-2.4-bin.tar.gz
cd apache-nutch-2.4

# Configure seed URLs
echo "https://example.com" > urls/seed.txt

# Crawl
bin/nutch inject urls
bin/nutch generate
bin/nutch fetch
bin/nutch parse
bin/nutch updatedb
```

**Companies Using**: Common Crawl, Archive.org

---

### Scrapy (Python)
**Description**: Fast, high-level web crawling framework

**Features**:
- Asynchronous (Twisted)
- Built-in selectors (CSS, XPath)
- Middleware pipeline
- Item pipelines
- Automatic throttling

**Best For**: Medium-scale crawling, data extraction

**Setup**:
```bash
# Install
pip install scrapy

# Create project
scrapy startproject myproject
cd myproject

# Create spider
scrapy genspider example example.com

# Run
scrapy crawl example -o output.json
```

**Example Spider**:
```python
import scrapy

class ExampleSpider(scrapy.Spider):
    name = 'example'
    start_urls = ['https://example.com']

    custom_settings = {
        'CONCURRENT_REQUESTS': 16,
        'DOWNLOAD_DELAY': 0.5,
        'ROBOTSTXT_OBEY': True,
    }

    def parse(self, response):
        # Extract data
        yield {
            'title': response.css('h1::text').get(),
            'url': response.url,
        }

        # Follow links
        for link in response.css('a::attr(href)').getall():
            yield response.follow(link, callback=self.parse)
```

**Companies Using**: Many startups, data science teams

---

### Colly (Go)
**Description**: Lightning-fast, elegant scraping framework

**Features**:
- Highly concurrent (goroutines)
- Async crawling
- Distributed crawling support
- Rate limiting
- Request caching

**Best For**: High-performance, concurrent crawling

**Setup**:
```bash
go get -u github.com/gocolly/colly/v2
```

**Example**:
```go
package main

import (
    "github.com/gocolly/colly/v2"
    "github.com/gocolly/colly/v2/queue"
)

func main() {
    c := colly.NewCollector(
        colly.Async(true),
    )

    c.Limit(&colly.LimitRule{
        DomainGlob:  "*",
        Parallelism: 100,
        RandomDelay: 1 * time.Second,
    })

    c.OnHTML("a[href]", func(e *colly.HTMLElement) {
        e.Request.Visit(e.Attr("href"))
    })

    c.OnRequest(func(r *colly.Request) {
        fmt.Println("Visiting", r.URL)
    })

    // Start crawling
    c.Visit("https://example.com")
    c.Wait()
}
```

---

### Heritrix (Java)
**Description**: Internet Archive's web crawler

**Features**:
- Built for archival quality
- Highly configurable
- WARC format support
- Frontier management
- Politeness controls

**Best For**: Web archiving, long-term preservation

**Companies Using**: Internet Archive, national libraries

---

## 3. Message Queues & Streaming

### Apache Kafka ⭐ (Recommended)
**Use Case**: URL frontier, event streaming

**Why Kafka**:
- High throughput (millions of messages/sec)
- Durability (persistent logs)
- Partitioning (scale horizontally)
- Exactly-once semantics

**Architecture**:
```
Topics:
  - url.frontier: URLs to crawl (partitioned by domain)
  - page.fetched: Fetched pages for processing
  - links.extracted: Discovered URLs
  - crawl.errors: Failed requests
```

**Configuration**:
```properties
# Kafka config for crawler
bootstrap.servers=kafka1:9092,kafka2:9092,kafka3:9092
num.partitions=100
replication.factor=3
compression.type=lz4

# URL frontier topic
topic.url.frontier.partitions=200
topic.url.frontier.retention.ms=604800000  # 7 days
```

**Python Example**:
```python
from kafka import KafkaProducer, KafkaConsumer
import json

# Producer (add URLs to frontier)
producer = KafkaProducer(
    bootstrap_servers=['kafka1:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8'),
    compression_type='lz4'
)

producer.send('url.frontier', {
    'url': 'https://example.com',
    'priority': 5,
    'depth': 2
})

# Consumer (fetch URLs from frontier)
consumer = KafkaConsumer(
    'url.frontier',
    bootstrap_servers=['kafka1:9092'],
    group_id='crawler-workers',
    value_deserializer=lambda m: json.loads(m.decode('utf-8'))
)

for message in consumer:
    url_data = message.value
    crawl(url_data['url'])
```

**Companies Using**: LinkedIn, Netflix, Uber

---

### RabbitMQ
**Use Case**: Alternative to Kafka, easier setup

**When to Use**: Smaller scale (<1M messages/day)

**Configuration**:
```python
import pika

# Connect
connection = pika.BlockingConnection(pika.ConnectionParameters('localhost'))
channel = connection.channel()

# Declare queue
channel.queue_declare(queue='url_frontier', durable=True)

# Publish URL
channel.basic_publish(
    exchange='',
    routing_key='url_frontier',
    body='https://example.com',
    properties=pika.BasicProperties(delivery_mode=2)  # Persistent
)
```

---

### AWS SQS
**Use Case**: Managed queue service (AWS)

**Pros**:
- Fully managed
- Auto-scaling
- Dead letter queues

**Cons**:
- Vendor lock-in
- Higher latency than Kafka

---

## 4. Storage Solutions

### Amazon S3 ⭐ (Recommended)
**Use Case**: Primary content storage

**Why S3**:
- Unlimited scale
- High durability (99.999999999%)
- Lifecycle policies (move to Glacier)
- Cheap ($0.023/GB/month)

**Architecture**:
```
Bucket structure:
  crawled-content/
    domain.com/
      2025/11/15/
        abc123.json.gz
        def456.json.gz
```

**Python Example**:
```python
import boto3
import gzip
import json

s3 = boto3.client('s3')

def store_page(url, content):
    # Compress
    compressed = gzip.compress(json.dumps(content).encode())

    # Generate key
    domain = extract_domain(url)
    date = datetime.now().strftime('%Y/%m/%d')
    key = f"crawled/{domain}/{date}/{url_hash}.json.gz"

    # Upload
    s3.put_object(
        Bucket='web-crawler-content',
        Key=key,
        Body=compressed,
        StorageClass='STANDARD_IA',  # Infrequent access
        ContentType='application/json',
        ContentEncoding='gzip'
    )
```

**Lifecycle Policy**:
```json
{
  "Rules": [
    {
      "Id": "Archive old crawls",
      "Status": "Enabled",
      "Transitions": [
        {
          "Days": 30,
          "StorageClass": "STANDARD_IA"
        },
        {
          "Days": 90,
          "StorageClass": "GLACIER"
        }
      ]
    }
  ]
}
```

---

### HDFS (Hadoop Distributed File System)
**Use Case**: On-premise large-scale storage

**When to Use**: Existing Hadoop ecosystem, cost-sensitive

**Configuration**:
```xml
<!-- hdfs-site.xml -->
<configuration>
  <property>
    <name>dfs.replication</name>
    <value>3</value>
  </property>
  <property>
    <name>dfs.blocksize</name>
    <value>134217728</value> <!-- 128MB -->
  </property>
</configuration>
```

**Companies Using**: Yahoo, Facebook (historical)

---

### Google Cloud Storage (GCS)
**Use Case**: Google Cloud environment

**Similar to S3**: Same features, Google ecosystem integration

---

## 5. Caching & In-Memory Stores

### Redis ⭐ (Recommended)
**Use Case**: URL deduplication, robots.txt cache, frontier state

**Why Redis**:
- In-memory speed (<1ms latency)
- Data structures (sets, sorted sets, hashes)
- Pub/Sub messaging
- Clustering support

**Architecture**:
```
Redis Cluster:
  - 6 nodes (3 masters, 3 replicas)
  - Hash slot partitioning
  - Automatic failover
```

**Use Cases**:

**1. URL Deduplication**:
```python
import redis

redis_client = redis.StrictRedis(host='redis-cluster', port=6379)

def is_url_seen(url):
    url_hash = hashlib.sha256(url.encode()).hexdigest()
    return redis_client.exists(f"url:{url_hash}")

def mark_url_seen(url):
    url_hash = hashlib.sha256(url.encode()).hexdigest()
    redis_client.setex(
        f"url:{url_hash}",
        86400 * 30,  # 30 days
        url
    )
```

**2. robots.txt Cache**:
```python
def get_robots_txt(domain):
    # Check cache
    cached = redis_client.get(f"robots:{domain}")
    if cached:
        return cached.decode()

    # Fetch and cache
    robots_txt = fetch_robots_txt(domain)
    redis_client.setex(f"robots:{domain}", 86400, robots_txt)
    return robots_txt
```

**3. Rate Limiting (Token Bucket)**:
```python
import time

def can_fetch(domain):
    key = f"rate_limit:{domain}"

    # Get current tokens
    tokens = redis_client.get(key)
    if tokens is None:
        # Initialize bucket
        redis_client.set(key, 10)  # 10 tokens
        return True

    tokens = int(tokens)
    if tokens > 0:
        # Consume token
        redis_client.decr(key)
        return True

    return False

# Refill tokens (separate process)
def refill_tokens():
    while True:
        for domain in get_active_domains():
            redis_client.incr(f"rate_limit:{domain}", amount=1)
        time.sleep(1)  # Refill every second
```

---

### Memcached
**Use Case**: Simple key-value caching

**When to Use**: Simpler requirements, less memory

---

## 6. Databases

### PostgreSQL ⭐ (Recommended)
**Use Case**: URL metadata, crawl state

**Why PostgreSQL**:
- ACID transactions
- Rich indexing (B-tree, GiST, GIN)
- Partitioning support
- JSON support
- Mature replication

**Schema**:
```sql
-- Main URL table (partitioned by domain)
CREATE TABLE crawled_urls (
    id BIGSERIAL,
    url_hash VARCHAR(64) NOT NULL,
    url TEXT NOT NULL,
    domain VARCHAR(255) NOT NULL,

    -- Crawl metadata
    first_seen TIMESTAMP DEFAULT NOW(),
    last_crawled TIMESTAMP,
    next_crawl TIMESTAMP,
    http_status INTEGER,
    content_hash VARCHAR(64),

    -- Priority
    page_rank FLOAT DEFAULT 0.0,
    crawl_priority INTEGER DEFAULT 5,

    PRIMARY KEY (id, domain)
) PARTITION BY HASH (domain);

-- Create partitions
CREATE TABLE crawled_urls_0 PARTITION OF crawled_urls
    FOR VALUES WITH (MODULUS 16, REMAINDER 0);
-- ... repeat for 1-15

-- Indexes
CREATE INDEX idx_next_crawl ON crawled_urls(next_crawl) WHERE next_crawl IS NOT NULL;
CREATE INDEX idx_url_hash ON crawled_urls(url_hash);
```

**Sharding**:
```python
# Client-side sharding
def get_shard(domain):
    shard_id = hash(domain) % NUM_SHARDS
    return db_connections[shard_id]

# Insert
shard = get_shard('example.com')
shard.execute("INSERT INTO crawled_urls (...) VALUES (...)")
```

---

### Cassandra
**Use Case**: Massive scale, multi-datacenter

**When to Use**: >100TB data, multi-region

**Schema**:
```cql
CREATE KEYSPACE crawler WITH replication = {
    'class': 'NetworkTopologyStrategy',
    'dc1': 3,
    'dc2': 3
};

CREATE TABLE crawled_urls (
    url_hash TEXT PRIMARY KEY,
    url TEXT,
    domain TEXT,
    last_crawled TIMESTAMP,
    http_status INT,
    content_hash TEXT
) WITH compaction = {
    'class': 'TimeWindowCompactionStrategy',
    'compaction_window_size': 1,
    'compaction_window_unit': 'DAYS'
};
```

**Companies Using**: Apple, Netflix, Instagram

---

### MongoDB
**Use Case**: Flexible schema, document storage

**When to Use**: Rapidly changing schema, small-medium scale

---

## 7. Search & Indexing

### Elasticsearch ⭐ (Recommended)
**Use Case**: Full-text search index

**Why Elasticsearch**:
- Real-time indexing
- Distributed architecture
- Rich query DSL
- Aggregations
- Near-real-time search

**Index Mapping**:
```json
{
  "mappings": {
    "properties": {
      "url": { "type": "keyword" },
      "title": {
        "type": "text",
        "analyzer": "standard"
      },
      "content": {
        "type": "text",
        "analyzer": "english"
      },
      "crawled_at": { "type": "date" },
      "domain": { "type": "keyword" },
      "language": { "type": "keyword" }
    }
  }
}
```

**Indexing**:
```python
from elasticsearch import Elasticsearch

es = Elasticsearch(['es1:9200', 'es2:9200'])

def index_page(page_data):
    es.index(
        index='web-pages',
        id=page_data['url_hash'],
        body={
            'url': page_data['url'],
            'title': page_data['title'],
            'content': page_data['text'],
            'crawled_at': page_data['crawled_at'],
            'domain': page_data['domain']
        }
    )

# Search
results = es.search(
    index='web-pages',
    body={
        'query': {
            'match': {
                'content': 'web crawler'
            }
        }
    }
)
```

**Companies Using**: Uber, LinkedIn, Netflix

---

### Apache Solr
**Use Case**: Enterprise search, Lucene-based

**When to Use**: Enterprise requirements, existing Solr expertise

---

## 8. DevOps & Infrastructure

### Container Orchestration

#### Kubernetes ⭐ (Recommended)
**Use Case**: Deploy crawler workers at scale

**Architecture**:
```yaml
# Crawler deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: crawler-workers
spec:
  replicas: 40
  selector:
    matchLabels:
      app: crawler
  template:
    metadata:
      labels:
        app: crawler
    spec:
      containers:
      - name: crawler
        image: mycompany/crawler:v1.0
        resources:
          requests:
            cpu: "2"
            memory: "4Gi"
          limits:
            cpu: "4"
            memory: "8Gi"
        env:
        - name: KAFKA_BROKERS
          value: "kafka-0.kafka:9092,kafka-1.kafka:9092"
        - name: REDIS_HOST
          value: "redis-cluster"
        - name: CRAWL_RATE
          value: "50"  # pages/sec per worker
```

**Autoscaling**:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: crawler-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: crawler-workers
  minReplicas: 10
  maxReplicas: 100
  metrics:
  - type: Pods
    pods:
      metric:
        name: queue_depth
      target:
        type: AverageValue
        averageValue: "10000"  # URLs per worker
```

---

#### Docker Compose
**Use Case**: Local development, small deployments

```yaml
version: '3.8'

services:
  crawler:
    image: crawler:latest
    deploy:
      replicas: 4
    environment:
      - KAFKA_BROKERS=kafka:9092
      - REDIS_HOST=redis
    depends_on:
      - kafka
      - redis

  kafka:
    image: confluentinc/cp-kafka:latest
    ports:
      - "9092:9092"

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"

  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: crawler
      POSTGRES_PASSWORD: secret
    volumes:
      - pg-data:/var/lib/postgresql/data

volumes:
  pg-data:
```

---

### CI/CD

#### GitHub Actions
```yaml
name: Crawler CI/CD

on:
  push:
    branches: [main]

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - name: Run tests
        run: |
          pytest tests/
          pylint crawler/

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - name: Build Docker image
        run: |
          docker build -t crawler:${{ github.sha }} .
          docker push myregistry/crawler:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/crawler-workers \
            crawler=myregistry/crawler:${{ github.sha }}
```

---

### Infrastructure as Code

#### Terraform
```hcl
# AWS infrastructure for crawler

# S3 bucket for content
resource "aws_s3_bucket" "crawler_content" {
  bucket = "web-crawler-content"

  lifecycle_rule {
    id      = "archive"
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
}

# EKS cluster for crawler workers
resource "aws_eks_cluster" "crawler" {
  name     = "crawler-cluster"
  role_arn = aws_iam_role.eks_cluster.arn

  vpc_config {
    subnet_ids = aws_subnet.crawler[*].id
  }
}

# RDS PostgreSQL for metadata
resource "aws_db_instance" "crawler_db" {
  identifier        = "crawler-metadata"
  engine            = "postgres"
  engine_version    = "15"
  instance_class    = "db.r6g.2xlarge"
  allocated_storage = 1000

  multi_az               = true
  backup_retention_period = 7
}

# ElastiCache Redis cluster
resource "aws_elasticache_cluster" "crawler_cache" {
  cluster_id           = "crawler-redis"
  engine               = "redis"
  node_type            = "cache.r6g.xlarge"
  num_cache_nodes      = 3
  parameter_group_name = "default.redis7"
}
```

---

## 9. Monitoring & Observability

### Prometheus + Grafana ⭐
**Use Case**: Metrics, dashboards, alerting

**Metrics Collection**:
```python
from prometheus_client import Counter, Histogram, Gauge, start_http_server

# Define metrics
pages_crawled = Counter('crawler_pages_total', 'Total pages crawled')
crawl_duration = Histogram('crawler_duration_seconds', 'Crawl duration')
queue_size = Gauge('crawler_queue_size', 'URLs in frontier')

# Instrument code
def crawl_page(url):
    with crawl_duration.time():
        # Crawl logic
        response = fetch(url)
        parse(response)

    pages_crawled.inc()
    queue_size.set(get_frontier_size())

# Expose metrics endpoint
start_http_server(8000)
```

**Grafana Dashboard**:
```json
{
  "dashboard": {
    "title": "Web Crawler Metrics",
    "panels": [
      {
        "title": "Crawl Rate",
        "targets": [{
          "expr": "rate(crawler_pages_total[5m])"
        }]
      },
      {
        "title": "Queue Depth",
        "targets": [{
          "expr": "crawler_queue_size"
        }]
      }
    ]
  }
}
```

---

### ELK Stack (Elasticsearch, Logstash, Kibana)
**Use Case**: Centralized logging

**Logstash Configuration**:
```ruby
input {
  kafka {
    bootstrap_servers => "kafka:9092"
    topics => ["crawler-logs"]
    codec => json
  }
}

filter {
  if [level] == "ERROR" {
    mutate {
      add_tag => ["error"]
    }
  }
}

output {
  elasticsearch {
    hosts => ["es:9200"]
    index => "crawler-logs-%{+YYYY.MM.dd}"
  }
}
```

---

### Jaeger (Distributed Tracing)
**Use Case**: Debug slow requests, trace crawl flow

```python
from jaeger_client import Config

def init_tracer(service_name):
    config = Config(
        config={
            'sampler': {'type': 'const', 'param': 1},
            'logging': True,
        },
        service_name=service_name,
    )
    return config.initialize_tracer()

tracer = init_tracer('crawler-worker')

# Trace crawl flow
with tracer.start_span('crawl_page') as span:
    span.set_tag('url', url)

    with tracer.start_span('fetch', child_of=span) as fetch_span:
        response = fetch(url)

    with tracer.start_span('parse', child_of=span) as parse_span:
        data = parse(response)
```

---

## 10. Security & Compliance

### Rate Limiting & DDoS Protection

#### Cloudflare
**Use Case**: DDoS protection, rate limiting

**Features**:
- Automatic DDoS mitigation
- Rate limiting rules
- Bot detection
- CAPTCHA challenges

---

### SSL/TLS

#### Let's Encrypt
**Use Case**: Free SSL certificates

```bash
# Certbot
sudo certbot --nginx -d crawler.example.com
```

---

### Secrets Management

#### HashiCorp Vault
**Use Case**: Secure credential storage

```python
import hvac

# Connect to Vault
client = hvac.Client(url='https://vault:8200')
client.token = os.getenv('VAULT_TOKEN')

# Read secret
secret = client.secrets.kv.v2.read_secret_version(
    path='crawler/db-password'
)
db_password = secret['data']['data']['password']
```

---

## 11. Industry Comparisons

### Google (Googlebot)

**Scale**: 100+ billion pages
**Crawl Rate**: 10,000+ pages/second
**Tech Stack**:
- **Language**: C++, Java
- **Storage**: Bigtable, Colossus (GFS successor)
- **Compute**: Borg (Kubernetes predecessor)
- **Indexing**: Custom inverted index

**Special Features**:
- JavaScript rendering (headless Chrome)
- Mobile-first indexing
- Sitemaps protocol
- PageRank algorithm

---

### Common Crawl

**Scale**: 50+ billion pages/crawl
**Frequency**: Monthly crawls
**Tech Stack**:
- **Crawler**: Apache Nutch (customized)
- **Storage**: S3 (public dataset)
- **Format**: WARC (Web ARChive)
- **Compute**: AWS EMR (Hadoop)

**Dataset**:
- Free public dataset
- 300+ TB per crawl
- Accessible via S3

---

### Archive.org (Internet Archive)

**Scale**: 800+ billion pages archived
**Tech Stack**:
- **Crawler**: Heritrix
- **Storage**: Petabox (custom archive system)
- **Format**: WARC
- **Replay**: Wayback Machine

**Mission**: Preserve web history

---

## 📊 Quick Comparison Table

| **Component** | **Small Scale** | **Medium Scale** | **Large Scale (Google)** |
|---------------|-----------------|------------------|--------------------------|
| **Language** | Python (Scrapy) | Go (Colly) | Java/C++ |
| **Queue** | RabbitMQ | Kafka | Custom |
| **Storage** | PostgreSQL + S3 | Cassandra + HDFS | Bigtable + Colossus |
| **Cache** | Redis | Redis Cluster | Custom in-memory |
| **Search** | Elasticsearch | Elasticsearch Cluster | Custom index |
| **Compute** | Docker Compose | Kubernetes (10-100 nodes) | Borg (1000s of nodes) |
| **Monitoring** | Prometheus + Grafana | Prometheus + Grafana | Custom (Borgmon) |
| **Scale** | 1M-100M pages | 100M-10B pages | 100B+ pages |
| **Cost/Month** | $100-$1,000 | $5,000-$50,000 | $Millions |

---

## 🚀 Getting Started: Recommended Stack

### For Startups / Small Projects (< 10M pages)

```
Language:       Python (Scrapy)
Queue:          RabbitMQ or managed SQS
Storage:        S3 + PostgreSQL
Cache:          Redis (single instance)
Search:         Elasticsearch (3-node cluster)
Deployment:     Docker Compose → Kubernetes
Monitoring:     Prometheus + Grafana
Cost:           $100-$500/month
```

### For Growing Companies (10M - 1B pages)

```
Language:       Go (Colly) or Python (Scrapy)
Queue:          Kafka (3-node cluster)
Storage:        S3 + PostgreSQL (sharded)
Cache:          Redis Cluster (6 nodes)
Search:         Elasticsearch (9+ nodes)
Deployment:     Kubernetes (20-50 nodes)
Monitoring:     Prometheus + Grafana + Jaeger
Cost:           $5,000-$20,000/month
```

### For Enterprise (1B+ pages)

```
Language:       Java (Nutch) or custom
Queue:          Kafka (large cluster) or custom
Storage:        HDFS or Cassandra + S3
Cache:          Redis Cluster or custom
Search:         Elasticsearch (large cluster) or Solr
Deployment:     Kubernetes (100+ nodes)
Monitoring:     Full observability stack
Cost:           $50,000+/month
```

---

## 📚 Additional Resources

- **Apache Nutch**: https://nutch.apache.org/
- **Scrapy**: https://scrapy.org/
- **Colly**: https://go-colly.org/
- **Common Crawl**: https://commoncrawl.org/
- **robots.txt Standard**: https://www.robotstxt.org/
- **WARC Format**: https://iipc.github.io/warc-specifications/

---

**Remember**: Start simple, measure, and scale as needed. Don't over-engineer for scale you don't have yet!

🕷️ Happy Crawling!
