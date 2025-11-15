# Design a Distributed Web Crawler

**Companies**: Google, Bing, DuckDuckGo, Archive.org, Common Crawl
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a scalable web crawler that can index billions of web pages across millions of domains. The system must be polite (respect robots.txt), efficient (high throughput), and maintain freshness (re-crawl pages periodically).

---

## Step 1: Requirements (7 min)

### Functional Requirements

1. **Crawl Web Pages**: Download HTML content from URLs
2. **Extract Links**: Parse HTML and discover new URLs
3. **Store Content**: Save pages for indexing/analysis
4. **Politeness**: Respect robots.txt and rate limits
5. **Prioritization**: Crawl important pages first
6. **Re-crawling**: Update pages based on freshness requirements
7. **Deduplication**: Avoid crawling same URLs multiple times

**Prioritize**: Focus on crawl + parse + store for MVP

### Non-Functional Requirements

1. **Scalability**: Handle billions of pages
2. **Politeness**: 1-5 requests/sec per domain
3. **Fault Tolerance**: Handle network failures, timeouts
4. **Efficiency**: Minimize redundant work
5. **Freshness**: Re-crawl pages based on change frequency
6. **Extensibility**: Support different page types (HTML, PDF, etc.)

### Capacity Estimation

**Assumptions**:
- 50 billion pages to index (Google-scale)
- 150 million new pages per day
- Average page size: 100 KB
- Re-crawl frequency: Mix of daily (10%), weekly (60%), monthly (30%)
- Average links per page: 100

**Crawl Load**:
```
Daily re-crawl (10%): 5B pages
Weekly re-crawl (60%): 30B ÷ 7 = 4.3B pages/day
Monthly re-crawl (30%): 15B ÷ 30 = 0.5B pages/day
New pages: 150M pages/day

Total: 5B + 4.3B + 0.5B + 0.15B ≈ 10B pages/day
Pages/sec: 10B ÷ 86,400 ≈ 115,000 pages/sec (theoretical max)

Realistic (with politeness): 1,500 pages/sec
Peak: ~3,000 pages/sec
```

**Storage**:
```
50B pages × 100 KB = 5,000 PB = 5 EB (raw)
With compression (3:1): 5 EB ÷ 3 = 1.7 EB
With replicas (3x): 1.7 EB × 3 = ~5 EB total

For smaller crawler (1B pages): 33 TB compressed
```

**Bandwidth**:
```
Download: 1,500 pages/sec × 100 KB = 150 MB/s = 1.2 Gbps
Peak: 300 MB/s = 2.4 Gbps
Storage writes (compressed): 50 MB/s = 400 Mbps
```

---

## Step 2: High-Level Architecture (15 min)

### System Components

```
┌─────────────────────────────────────────────────────────┐
│                    SEED URLs                            │
│         (Starting points, Sitemaps, Discovered)         │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│              URL FRONTIER (Priority Queue)              │
│  • Priority Queues (F1-F5): PageRank, freshness         │
│  • Back Queues (B1-Bn): Domain-based politeness         │
│  • Implementation: Kafka + Redis                        │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                 CRAWLER WORKERS (Fleet)                 │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐             │
│  │ Worker 1 │  │ Worker 2 │  │ Worker N │             │
│  │ 50 pgs/s │  │ 50 pgs/s │  │ 50 pgs/s │             │
│  └──────────┘  └──────────┘  └──────────┘             │
│                                                         │
│  Each worker:                                           │
│  1. Checks robots.txt                                   │
│  2. Resolves DNS (cached)                               │
│  3. Fetches HTTP content                                │
│  4. Parses HTML, extracts links                         │
│  5. Stores content                                      │
└────────────────────┬────────────────────────────────────┘
                     │
        ┌────────────┼────────────┐
        │            │            │
        ▼            ▼            ▼
┌──────────┐  ┌──────────┐  ┌──────────────┐
│  DEDUP   │  │ CONTENT  │  │    SEARCH    │
│  FILTER  │  │ STORAGE  │  │    INDEX     │
│ (Bloom+  │  │ (S3/HDFS)│  │(Elasticsearch)│
│  Redis)  │  └──────────┘  └──────────────┘
└────┬─────┘
     │
     └──> New URLs feed back to FRONTIER
```

### Component Details

**1. URL Frontier**
- **Purpose**: Manage URLs to be crawled
- **Structure**: Two-tier queue system
  - **Front Queues (F1-F5)**: Priority-based (PageRank, freshness)
  - **Back Queues (B1-Bn)**: Domain-based (politeness enforcement)
- **Implementation**: Kafka (distributed queue) + Redis (state)

**2. Crawler Workers**
- **Purpose**: Fetch and parse web pages
- **Count**: 30-40 workers for 1,500 pages/sec
- **Rate**: 50 pages/sec per worker
- **Responsibilities**:
  - robots.txt checking
  - DNS resolution (cached)
  - HTTP fetching
  - Content parsing
  - Link extraction

**3. Deduplication System**
- **Purpose**: Avoid re-crawling same URLs
- **Components**:
  - **Bloom Filter**: Fast negative lookup (in-memory, 320 GB)
  - **Hash Table**: Exact matching (distributed Redis, 3.2 TB)
- **Hash Function**: SHA-256 of normalized URL

**4. Content Storage**
- **Purpose**: Store crawled pages
- **Technology**: S3 / HDFS
- **Format**: Compressed JSON/Parquet
- **Tiering**:
  - Hot: Last 7 days (SSD)
  - Warm: 6 months (S3 Standard)
  - Cold: 5 years (S3 Glacier)

**5. Search Index**
- **Purpose**: Enable full-text search
- **Technology**: Elasticsearch / Solr
- **Index**: Inverted index (term → documents)
- **Processing**: Tokenization, stemming, stop words

---

## Step 3: Data Model (8 min)

### URL Metadata Database

**Technology Choice**: PostgreSQL (sharded) or Cassandra

```sql
-- PostgreSQL Schema
CREATE TABLE crawled_urls (
    id BIGSERIAL PRIMARY KEY,
    url_hash VARCHAR(64) UNIQUE NOT NULL,
    url TEXT NOT NULL,
    domain VARCHAR(255) NOT NULL,

    -- Crawl metadata
    first_seen TIMESTAMP DEFAULT NOW(),
    last_crawled TIMESTAMP,
    next_crawl TIMESTAMP,
    crawl_frequency INTERVAL,
    crawl_priority INTEGER DEFAULT 5,

    -- Status
    http_status INTEGER,
    content_hash VARCHAR(64),  -- For duplicate content detection
    content_type VARCHAR(100),
    page_rank FLOAT DEFAULT 0.0,

    -- Error tracking
    error_count INTEGER DEFAULT 0,
    last_error TEXT,

    -- Flags
    is_active BOOLEAN DEFAULT TRUE,
    robots_allowed BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_domain ON crawled_urls(domain);
CREATE INDEX idx_next_crawl ON crawled_urls(next_crawl) WHERE is_active = TRUE;
CREATE INDEX idx_priority ON crawled_urls(crawl_priority DESC, next_crawl);
CREATE INDEX idx_url_hash ON crawled_urls(url_hash);

-- Sharding strategy: Hash(url_hash) % num_shards
```

### robots.txt Cache

```sql
CREATE TABLE robots_cache (
    domain VARCHAR(255) PRIMARY KEY,
    content TEXT,
    fetched_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP,
    crawl_delay INTEGER,  -- seconds
    is_allowed BOOLEAN DEFAULT TRUE
);

CREATE INDEX idx_expires ON robots_cache(expires_at);
```

### Content Storage Schema (Document Store)

```json
{
  "url": "https://example.com/page",
  "url_hash": "sha256:a3b2c1...",
  "crawled_at": "2025-11-15T10:30:00Z",

  "http": {
    "status_code": 200,
    "headers": {
      "content-type": "text/html; charset=utf-8",
      "last-modified": "2025-11-14T15:20:00Z"
    },
    "fetch_time_ms": 450
  },

  "content": {
    "html": "<html>...</html>",  // Original HTML
    "text": "Extracted text content...",  // Parsed text
    "title": "Page Title",
    "meta_description": "Page description",
    "language": "en",
    "encoding": "utf-8"
  },

  "links": {
    "outgoing": [
      "https://example.com/page2",
      "https://other.com/page"
    ],
    "count": 100
  },

  "metadata": {
    "size_bytes": 45678,
    "compressed_size_bytes": 15226,
    "depth": 3,  // Distance from seed
    "parent_url": "https://example.com/"
  }
}
```

---

## Step 4: Core Components Deep Dive (20 min)

### 4.1 URL Frontier (The Heart of the Crawler)

**Challenge**: Balance **priority** (crawl important pages first) with **politeness** (don't overwhelm servers).

**Solution**: Two-tier queue architecture

#### Front Queues (Priority-based)

```python
class FrontQueue:
    """Priority-based queues"""
    F1 = PriorityQueue()  # Urgent (news, trending): priority 9-10
    F2 = PriorityQueue()  # High (popular sites): priority 7-8
    F3 = PriorityQueue()  # Medium (regular): priority 4-6
    F4 = PriorityQueue()  # Low (deep pages): priority 1-3
    F5 = PriorityQueue()  # Archive (old): priority 0

def assign_to_front_queue(url, metadata):
    """Assign URL to priority queue"""
    priority = calculate_priority(
        page_rank=metadata.page_rank,
        freshness_score=metadata.freshness,
        domain_authority=metadata.domain_auth
    )

    if priority >= 9:
        F1.enqueue(url)
    elif priority >= 7:
        F2.enqueue(url)
    elif priority >= 4:
        F3.enqueue(url)
    elif priority >= 1:
        F4.enqueue(url)
    else:
        F5.enqueue(url)

def select_from_front_queues():
    """Biased selection from front queues"""
    # Probability distribution: F1(40%), F2(30%), F3(20%), F4(7%), F5(3%)
    rand = random.random()

    if rand < 0.40 and not F1.empty():
        return F1.dequeue()
    elif rand < 0.70 and not F2.empty():
        return F2.dequeue()
    elif rand < 0.90 and not F3.empty():
        return F3.dequeue()
    elif rand < 0.97 and not F4.empty():
        return F4.dequeue()
    else:
        return F5.dequeue() if not F5.empty() else None
```

#### Back Queues (Politeness enforcement)

```python
class BackQueue:
    """Domain-specific queues for politeness"""
    def __init__(self, domain, rate_limit=1.0):
        self.domain = domain
        self.queue = Queue()
        self.rate_limit = rate_limit  # requests per second
        self.last_fetch_time = 0

    def can_fetch(self):
        """Check if enough time has passed"""
        now = time.time()
        time_since_last = now - self.last_fetch_time
        return time_since_last >= (1.0 / self.rate_limit)

    def record_fetch(self):
        """Record fetch time"""
        self.last_fetch_time = time.time()

# Router: Map URL to back queue
back_queues = {}  # domain -> BackQueue

def route_to_back_queue(url):
    """Route URL to domain-specific back queue"""
    domain = extract_domain(url)

    if domain not in back_queues:
        # Create new back queue for this domain
        rate_limit = get_crawl_rate_for_domain(domain)  # From robots.txt
        back_queues[domain] = BackQueue(domain, rate_limit)

    back_queues[domain].queue.enqueue(url)
    return back_queues[domain]

def select_ready_url():
    """Select URL from back queue that's ready to fetch"""
    for domain, back_queue in back_queues.items():
        if not back_queue.queue.empty() and back_queue.can_fetch():
            url = back_queue.queue.dequeue()
            back_queue.record_fetch()
            return url

    return None  # No URLs ready yet (politeness delay)
```

### 4.2 Crawler Worker Implementation

```python
import requests
from bs4 import BeautifulSoup
from urllib.parse import urljoin, urlparse
import hashlib
import time

class CrawlerWorker:
    def __init__(self, worker_id, target_rate=50):
        self.worker_id = worker_id
        self.target_rate = target_rate  # pages per second
        self.session = requests.Session()
        self.session.headers.update({
            'User-Agent': 'MyBot/1.0 (+https://mybot.com/info)',
            'Accept': 'text/html,application/xhtml+xml',
            'Accept-Encoding': 'gzip, deflate'
        })

    def crawl(self, url):
        """Main crawl logic"""
        try:
            # Step 1: Check robots.txt
            if not self.is_allowed_by_robots(url):
                logger.info(f"Blocked by robots.txt: {url}")
                return None

            # Step 2: Fetch page
            response = self.fetch_page(url)

            if response is None:
                return None

            # Step 3: Parse content
            parsed_data = self.parse_content(url, response)

            # Step 4: Store content
            self.store_content(parsed_data)

            # Step 5: Extract and enqueue links
            self.process_links(parsed_data['links'])

            return parsed_data

        except Exception as e:
            logger.error(f"Crawl failed for {url}: {e}")
            self.record_error(url, str(e))
            return None

    def is_allowed_by_robots(self, url):
        """Check robots.txt permission"""
        domain = extract_domain(url)

        # Check cache first
        robots_rules = redis.get(f"robots:{domain}")

        if robots_rules is None:
            # Fetch robots.txt
            robots_url = f"{urlparse(url).scheme}://{domain}/robots.txt"
            try:
                response = self.session.get(robots_url, timeout=5)
                robots_rules = response.text

                # Cache for 24 hours
                redis.setex(f"robots:{domain}", 86400, robots_rules)
            except:
                # If robots.txt doesn't exist, allow by default
                return True

        # Parse and check rules
        return self.robots_parser.can_fetch(self.user_agent, url, robots_rules)

    def fetch_page(self, url):
        """HTTP GET request with retries"""
        max_retries = 3
        timeout = 10

        for attempt in range(max_retries):
            try:
                response = self.session.get(
                    url,
                    timeout=timeout,
                    allow_redirects=True,
                    stream=False
                )

                # Check status code
                if response.status_code == 200:
                    return response
                elif response.status_code == 429:
                    # Too Many Requests - backoff
                    wait_time = 2 ** attempt
                    logger.warning(f"Rate limited by {url}, waiting {wait_time}s")
                    time.sleep(wait_time)
                else:
                    logger.warning(f"HTTP {response.status_code} for {url}")
                    return None

            except requests.Timeout:
                logger.warning(f"Timeout on attempt {attempt+1} for {url}")
                if attempt == max_retries - 1:
                    return None
            except Exception as e:
                logger.error(f"Fetch error: {e}")
                return None

        return None

    def parse_content(self, url, response):
        """Parse HTML and extract data"""
        soup = BeautifulSoup(response.content, 'lxml')

        # Remove scripts and styles
        for tag in soup(['script', 'style', 'nav', 'footer']):
            tag.decompose()

        # Extract text
        text = soup.get_text(separator=' ', strip=True)

        # Extract metadata
        title = soup.find('title').text if soup.find('title') else ''
        meta_desc = soup.find('meta', {'name': 'description'})
        description = meta_desc.get('content', '') if meta_desc else ''

        # Extract links
        links = []
        for tag in soup.find_all(['a', 'link'], href=True):
            href = tag['href']
            absolute_url = urljoin(url, href)

            # Filter valid HTTP(S) URLs
            if absolute_url.startswith(('http://', 'https://')):
                links.append(self.normalize_url(absolute_url))

        return {
            'url': url,
            'url_hash': hashlib.sha256(url.encode()).hexdigest(),
            'html': response.text,
            'text': text,
            'title': title,
            'description': description,
            'links': links,
            'http_status': response.status_code,
            'content_type': response.headers.get('content-type', ''),
            'size_bytes': len(response.content),
            'crawled_at': datetime.utcnow().isoformat()
        }

    def normalize_url(self, url):
        """Normalize URL for deduplication"""
        parsed = urlparse(url)

        # Convert to lowercase
        url = url.lower()

        # Remove fragment (#section)
        url = url.split('#')[0]

        # Sort query parameters
        if parsed.query:
            params = sorted(parsed.query.split('&'))
            url = f"{parsed.scheme}://{parsed.netloc}{parsed.path}?{'&'.join(params)}"

        # Remove trailing slash (unless root)
        if parsed.path != '/' and url.endswith('/'):
            url = url[:-1]

        return url

    def store_content(self, data):
        """Store content in S3/HDFS"""
        # Compress content
        import gzip
        import json

        compressed_data = gzip.compress(json.dumps(data).encode())

        # Store in S3 (partitioned by domain and date)
        domain = extract_domain(data['url'])
        date = datetime.utcnow().strftime('%Y/%m/%d')
        key = f"crawled/{domain}/{date}/{data['url_hash']}.json.gz"

        s3_client.put_object(
            Bucket='web-crawler-content',
            Key=key,
            Body=compressed_data,
            ContentType='application/json',
            ContentEncoding='gzip'
        )

    def process_links(self, links):
        """Send discovered links to deduplication"""
        for link in links:
            # Check if already seen
            if not self.is_url_seen(link):
                # Add to frontier
                kafka_producer.send('url.discovered', {
                    'url': link,
                    'discovered_at': datetime.utcnow().isoformat()
                })

    def is_url_seen(self, url):
        """Check if URL already crawled"""
        url_hash = hashlib.sha256(url.encode()).hexdigest()

        # Fast check: Bloom filter
        if not bloom_filter.contains(url_hash):
            return False

        # Exact check: Redis
        return redis.exists(f"url:{url_hash}")
```

### 4.3 URL Deduplication System

```python
from pybloom_live import BloomFilter

class URLDeduplicator:
    def __init__(self, capacity=100_000_000_000, error_rate=0.001):
        """
        capacity: Expected number of URLs (100B)
        error_rate: False positive rate (0.1%)
        """
        # Bloom filter for fast negative lookup
        self.bloom_filter = BloomFilter(capacity=capacity, error_rate=error_rate)

        # Redis for exact matching
        self.redis_client = redis.StrictRedis(
            host='redis-cluster',
            port=6379,
            decode_responses=False
        )

    def is_seen(self, url):
        """Check if URL already seen"""
        url_hash = hashlib.sha256(url.encode()).hexdigest()

        # Step 1: Bloom filter check (fast, probabilistic)
        if not self.bloom_filter.contains(url_hash):
            # Definitely not seen
            return False

        # Step 2: Redis check (exact, slower)
        return self.redis_client.exists(f"url:{url_hash}")

    def mark_as_seen(self, url):
        """Mark URL as seen"""
        url_hash = hashlib.sha256(url.encode()).hexdigest()

        # Add to Bloom filter
        self.bloom_filter.add(url_hash)

        # Add to Redis with metadata
        self.redis_client.setex(
            f"url:{url_hash}",
            86400 * 30,  # 30 days TTL
            url
        )

    def batch_check(self, urls):
        """Check multiple URLs efficiently"""
        unseen_urls = []

        for url in urls:
            if not self.is_seen(url):
                unseen_urls.append(url)
                self.mark_as_seen(url)

        return unseen_urls
```

### 4.4 Content Hash for Duplicate Detection

```python
from simhash import Simhash

def calculate_content_hash(text):
    """Calculate SimHash for near-duplicate detection"""
    # Extract features (words)
    features = text.lower().split()

    # Calculate SimHash
    simhash_value = Simhash(features).value

    return simhash_value

def is_duplicate_content(simhash1, simhash2, distance_threshold=3):
    """Check if two pages have similar content"""
    # Hamming distance
    distance = bin(simhash1 ^ simhash2).count('1')

    return distance <= distance_threshold
```

---

## Step 5: Advanced Optimizations (10 min)

### 5.1 DNS Caching

```python
import dns.resolver
from functools import lru_cache

class DNSCache:
    def __init__(self):
        self.cache = {}  # domain -> (IP, expiry)
        self.resolver = dns.resolver.Resolver()
        self.resolver.nameservers = ['8.8.8.8', '8.8.4.4']  # Google DNS

    @lru_cache(maxsize=10000)
    def resolve(self, domain):
        """Resolve domain to IP with caching"""
        now = time.time()

        # Check cache
        if domain in self.cache:
            ip, expiry = self.cache[domain]
            if now < expiry:
                return ip

        # Resolve DNS
        try:
            answers = self.resolver.resolve(domain, 'A')
            ip = str(answers[0])
            ttl = answers.rrset.ttl

            # Cache result
            self.cache[domain] = (ip, now + ttl)
            return ip
        except Exception as e:
            logger.error(f"DNS resolution failed for {domain}: {e}")
            return None
```

### 5.2 Connection Pooling

```python
from requests.adapters import HTTPAdapter
from requests.packages.urllib3.util.retry import Retry

def create_session():
    """Create HTTP session with connection pooling"""
    session = requests.Session()

    # Retry strategy
    retry_strategy = Retry(
        total=3,
        backoff_factor=1,
        status_forcelist=[429, 500, 502, 503, 504],
        allowed_methods=["GET"]
    )

    # HTTP adapter with pooling
    adapter = HTTPAdapter(
        pool_connections=100,  # Number of connection pools
        pool_maxsize=100,      # Max connections per pool
        max_retries=retry_strategy
    )

    session.mount("http://", adapter)
    session.mount("https://", adapter)

    return session
```

### 5.3 Adaptive Re-crawl Scheduling

```python
def calculate_next_crawl(url_metadata):
    """Determine next crawl time based on change frequency"""
    last_modified = url_metadata.last_modified
    content_hash = url_metadata.content_hash
    previous_hash = url_metadata.previous_hash

    # Check if content changed
    content_changed = (content_hash != previous_hash)

    # Calculate change frequency
    if content_changed:
        # Content changes frequently -> crawl more often
        if url_metadata.change_count > 10:
            # Changes very frequently (news site)
            next_crawl = timedelta(hours=1)
        elif url_metadata.change_count > 5:
            # Changes regularly
            next_crawl = timedelta(hours=24)
        else:
            # Changes occasionally
            next_crawl = timedelta(days=7)
    else:
        # Content hasn't changed -> increase interval
        current_interval = url_metadata.crawl_interval
        next_crawl = min(current_interval * 1.5, timedelta(days=30))

    return datetime.utcnow() + next_crawl
```

### 5.4 Focused Crawling (Topic-Specific)

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.naive_bayes import MultinomialNB

class FocusedCrawler:
    def __init__(self, topic):
        self.topic = topic
        self.classifier = self.train_classifier()

    def train_classifier(self):
        """Train ML model to identify relevant pages"""
        # Training data: Positive (relevant) and negative (irrelevant) examples
        positive_texts = load_positive_examples(self.topic)
        negative_texts = load_negative_examples()

        X = positive_texts + negative_texts
        y = [1] * len(positive_texts) + [0] * len(negative_texts)

        # TF-IDF vectorization
        vectorizer = TfidfVectorizer(max_features=5000)
        X_vec = vectorizer.fit_transform(X)

        # Train classifier
        classifier = MultinomialNB()
        classifier.fit(X_vec, y)

        return (vectorizer, classifier)

    def is_relevant(self, text):
        """Predict if page is relevant to topic"""
        vectorizer, classifier = self.classifier

        # Vectorize
        X = vectorizer.transform([text])

        # Predict
        prob = classifier.predict_proba(X)[0][1]

        return prob > 0.7  # 70% confidence threshold

    def prioritize_url(self, url, text):
        """Assign priority score based on relevance"""
        if self.is_relevant(text):
            return 9  # High priority
        else:
            return 2  # Low priority
```

---

## Step 6: Monitoring & Observability

### Key Metrics to Track

```python
from prometheus_client import Counter, Histogram, Gauge

# Counters
pages_crawled = Counter('crawler_pages_crawled_total', 'Total pages crawled')
pages_failed = Counter('crawler_pages_failed_total', 'Total pages failed')
links_discovered = Counter('crawler_links_discovered_total', 'Total links discovered')

# Histograms (latency)
fetch_latency = Histogram('crawler_fetch_latency_seconds', 'HTTP fetch latency')
parse_latency = Histogram('crawler_parse_latency_seconds', 'Parse latency')

# Gauges (current state)
frontier_size = Gauge('crawler_frontier_size', 'URLs in frontier')
active_workers = Gauge('crawler_active_workers', 'Number of active workers')
crawl_rate = Gauge('crawler_pages_per_second', 'Current crawl rate')

# Metrics collection
def record_crawl_metrics(url, response, start_time):
    """Record metrics for a crawl operation"""
    duration = time.time() - start_time

    pages_crawled.inc()
    fetch_latency.observe(duration)

    if response.status_code == 200:
        # Success metrics
        crawl_rate.set(calculate_current_rate())
    else:
        # Failure metrics
        pages_failed.inc()
```

### Dashboards (Grafana)

```
┌─────────────────────────────────────────────────────────┐
│         WEB CRAWLER MONITORING DASHBOARD                │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  Crawl Rate: [====|====] 1,523 pages/sec              │
│  Frontier Size: [========] 47.2M URLs                   │
│  Active Workers: [====] 38/40                           │
│  Success Rate: [===========] 94.7%                      │
│                                                         │
├─────────────────────────────────────────────────────────┤
│  LATENCY PERCENTILES                                    │
│  P50: 245ms  │  P95: 892ms  │  P99: 1,450ms           │
├─────────────────────────────────────────────────────────┤
│  TOP ERRORS (Last Hour)                                 │
│  • Connection Timeout: 1,234                            │
│  • robots.txt Block: 892                                │
│  • HTTP 404: 567                                        │
│  • HTTP 503: 234                                        │
└─────────────────────────────────────────────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (billions vs millions of pages)?
- Politeness requirements (requests/sec per domain)?
- Content types (HTML only or PDFs, images too)?
- Re-crawl frequency (daily, weekly, monthly)?
- Freshness requirements (real-time vs batch)?
- Budget constraints (cloud cost limits)?

**Topics to Cover**:
- URL Frontier design (two-tier queue system)
- Politeness enforcement (domain-based rate limiting)
- Deduplication strategy (Bloom filter + hash table)
- Distributed architecture (worker fleet)
- robots.txt compliance
- DNS caching and connection pooling

**Common Follow-ups**:
- "How would you handle JavaScript-heavy sites?"
  → Use headless browsers (Puppeteer, Selenium) for JS rendering
- "How do you detect and handle crawler traps?"
  → URL pattern detection, depth limits, infinite scroll detection
- "How do you prioritize which pages to crawl first?"
  → PageRank, domain authority, freshness, backlink count
- "How do you ensure politeness at scale?"
  → Domain-based back queues, adaptive rate limiting, respect robots.txt

---

## Key Takeaways

1. **Politeness is Critical**: Rate limiting per domain is non-negotiable
2. **Two-Tier Frontier**: Balance priority (quality) with politeness (ethics)
3. **Deduplication is Essential**: Bloom filter for speed, hash table for accuracy
4. **Distributed by Domain**: Shard workers by domain for parallelism
5. **Respect robots.txt**: Always check and cache robots.txt rules
6. **Adaptive Scheduling**: Re-crawl based on content change frequency
7. **Compression Saves Money**: 3:1 compression ratio for storage
8. **Monitor Everything**: Crawl rate, errors, latency, frontier size

## Further Reading

- **Papers**:
  - "Mercator: A Scalable, Extensible Web Crawler" (Google)
  - "The Anatomy of a Large-Scale Hypertextual Web Search Engine" (Brin & Page)
  - "Web Crawling" (Olston & Najork, Foundations and Trends)
- **Open Source Crawlers**:
  - Apache Nutch (Java)
  - Scrapy (Python)
  - Colly (Go)
  - Heritrix (Java, Archive.org)
- **Books**:
  - "Mining the Web" by Soumen Chakrabarti
  - "Web Crawling and Data Mining with Apache Nutch"
- **Standards**:
  - robots.txt Specification (robotstxt.org)
  - Sitemaps Protocol (sitemaps.org)
