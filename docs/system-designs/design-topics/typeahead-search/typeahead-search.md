# Design a Typeahead + Search System

**Companies**: Google Search, LinkedIn, Amazon, Facebook, Elasticsearch
**Difficulty**: Intermediate
**Time**: 60 minutes

## Problem Statement

Design a scalable typeahead (autocomplete) and search system like Google Search or LinkedIn's search bar. As users type, the system should suggest relevant completions in real-time (< 100ms latency) and support full-text search with ranking.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Typeahead/Autocomplete**: Suggest top-k queries as user types
2. **Search**: Return ranked results for complete query
3. **Query Logging**: Track user queries for analytics
4. **Personalization** (optional): Tailor results based on user history
5. **Spell Correction** (optional): Suggest corrections for typos
6. **Trending Queries** (optional): Show popular/trending searches

**Prioritize**: Focus on typeahead and search for MVP

### Non-Functional Requirements

1. **Low Latency**: <100ms for typeahead, <500ms for search
2. **High Availability**: 99.9% uptime
3. **Scalable**: Handle millions of queries per day
4. **Accurate**: Relevant results with good ranking
5. **Real-time**: New content indexed quickly (<5 minutes)

### Capacity Estimation

**Assumptions**:
- 100M Daily Active Users (DAU)
- 10 searches per user per day = 1B searches/day
- 10 typeahead requests per search = 10B typeahead/day
- Average query length: 50 characters
- Result set: 10-20 items per query

**Query Load**:
```
Typeahead QPS:
10B requests/day ÷ 100K seconds = 100,000 QPS
Peak: 200,000 QPS

Search QPS:
1B searches/day ÷ 100K seconds = 10,000 QPS
Peak: 20,000 QPS
```

**Storage**:
```
Query Logs (1 year):
1B queries/day × 50 bytes × 365 days = 18TB/year

Search Index:
Assuming 10B documents × 1KB average = 10TB raw data
Inverted index: ~5TB (compressed)
```

**Bandwidth**:
```
Typeahead: 100K QPS × 500 bytes (response) = 50 MB/s
Search: 10K QPS × 5KB (response with snippets) = 50 MB/s
Total: ~100 MB/s = 0.8 Gbps
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────┐
│                    Client                           │
│              (Browser / Mobile App)                 │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              CDN / Edge Network                     │
│         (Static assets, Geolocation)                │
└──────────────────────┬──────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────┐
│              Load Balancer (ALB)                    │
└──────────────────────┬──────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Typeahead API   │  │   Search API     │
│  (Low Latency)   │  │  (Full Ranking)  │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         │                     │
    ┌────┴──────┐         ┌───┴──────────┐
    ▼           ▼         ▼              ▼
┌────────┐  ┌──────┐  ┌────────┐  ┌──────────┐
│ Trie   │  │Redis │  │Elastic │  │Ranking   │
│ Cache  │  │Cache │  │Search  │  │Service   │
└────────┘  └──────┘  └────┬───┘  └────┬─────┘
                           │           │
                      ┌────┴───────────┴────┐
                      ▼                     ▼
                ┌──────────┐          ┌──────────┐
                │ Primary  │          │Analytics │
                │ Database │          │ Store    │
                │(Postgres)│          │(ClickH.) │
                └──────────┘          └──────────┘
```

### Components

**Typeahead Service**:
- Prefix-based autocomplete
- In-memory Trie data structure
- Redis cache for hot queries
- Returns top-10 suggestions

**Search Service**:
- Full-text search engine (Elasticsearch)
- Ranking and scoring
- Spell correction
- Filters and facets

**Indexing Service**:
- Crawls/ingests documents
- Builds inverted index
- Updates Elasticsearch in real-time
- Maintains Trie structure

**Query Logger**:
- Logs all queries asynchronously
- Kafka/SQS for buffering
- Stores in ClickHouse for analytics

**Ranking Service**:
- Machine Learning models
- Personalization
- Click-through rate (CTR) optimization

---

## Step 3: Data Model (10 min)

### Database Choice

**Typeahead**: In-memory Trie + Redis
- Fast prefix matching (O(m) where m = prefix length)
- Low latency (<10ms)
- Precomputed suggestions

**Search**: Elasticsearch (Inverted Index)
- Full-text search capabilities
- Distributed, horizontally scalable
- Real-time indexing

**Analytics**: ClickHouse (Column-oriented)
- Fast aggregations
- Time-series query logs
- Efficient compression

### Typeahead Data Structure: Trie

```python
class TrieNode:
    def __init__(self):
        self.children = {}  # char -> TrieNode
        self.is_end_of_word = False
        self.frequency = 0  # Query popularity
        self.top_queries = []  # Top-10 queries with this prefix

# Example Trie for ["search", "seattle", "see", "seen"]
#       root
#        |
#        s
#        |
#        e
#       / \
#      a   e
#      |   |
#      r   [n]  (top: "seen", "see")
#      |
#      c
#      |
#      h
#     [*]  (top: "search", "seattle", "see", "seen")
```

### Elasticsearch Index Schema

```json
{
  "mappings": {
    "properties": {
      "doc_id": { "type": "keyword" },
      "title": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "content": {
        "type": "text",
        "analyzer": "english"
      },
      "category": { "type": "keyword" },
      "timestamp": { "type": "date" },
      "popularity_score": { "type": "float" },
      "user_engagement": { "type": "float" }
    }
  },
  "settings": {
    "number_of_shards": 10,
    "number_of_replicas": 2,
    "refresh_interval": "5s"
  }
}
```

### Query Log Schema (ClickHouse)

```sql
CREATE TABLE query_logs (
    query_id UUID,
    query_text String,
    user_id UInt64,
    timestamp DateTime,
    latency_ms UInt32,
    results_count UInt16,
    clicked_result_id String,
    user_location String,
    device_type String
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (timestamp, user_id);
```

---

## Step 4: APIs (5 min)

### Typeahead API

```http
GET /api/typeahead?prefix={query}&limit={k}

Request:
GET /api/typeahead?prefix=sea&limit=10

Response: 200 OK
{
  "suggestions": [
    { "text": "seattle weather", "frequency": 1500000 },
    { "text": "search engine", "frequency": 1200000 },
    { "text": "seahawks", "frequency": 800000 },
    { "text": "sea world", "frequency": 500000 },
    { "text": "seattle news", "frequency": 450000 }
  ],
  "latency_ms": 15
}
```

### Search API

```http
POST /api/search
Content-Type: application/json

Request:
{
  "query": "machine learning courses",
  "filters": {
    "category": "education",
    "date_range": { "gte": "2024-01-01" }
  },
  "page": 1,
  "size": 20,
  "user_id": "user123"
}

Response: 200 OK
{
  "total_hits": 15234,
  "took_ms": 45,
  "results": [
    {
      "doc_id": "doc1",
      "title": "Introduction to Machine Learning",
      "snippet": "Learn <em>machine learning</em> fundamentals...",
      "score": 8.5,
      "url": "https://example.com/ml-course",
      "category": "education"
    },
    // ... 19 more results
  ],
  "facets": {
    "category": {
      "education": 8500,
      "technology": 4200,
      "research": 2534
    }
  }
}
```

---

## Step 5: Core Algorithms

### Typeahead Algorithm: Trie with Top-K Caching

```python
class TypeaheadService:
    def __init__(self):
        self.root = TrieNode()
        self.redis_cache = RedisClient()

    def insert_query(self, query: str, frequency: int):
        """Insert query into Trie with frequency"""
        node = self.root

        for char in query:
            if char not in node.children:
                node.children[char] = TrieNode()
            node = node.children[char]

            # Update top-k queries at each node
            self._update_top_queries(node, query, frequency)

        node.is_end_of_word = True
        node.frequency = frequency

    def _update_top_queries(self, node: TrieNode, query: str, freq: int):
        """Maintain top-10 queries at each node"""
        # Add query to list
        node.top_queries.append((query, freq))

        # Sort by frequency and keep top 10
        node.top_queries.sort(key=lambda x: x[1], reverse=True)
        node.top_queries = node.top_queries[:10]

    def get_suggestions(self, prefix: str, limit: int = 10) -> List[str]:
        """Get top-k suggestions for prefix"""

        # Check Redis cache first
        cache_key = f"typeahead:{prefix}"
        cached = self.redis_cache.get(cache_key)
        if cached:
            return json.loads(cached)

        # Traverse Trie to prefix node
        node = self.root
        for char in prefix:
            if char not in node.children:
                return []  # No suggestions
            node = node.children[char]

        # Return precomputed top queries
        suggestions = [q[0] for q in node.top_queries[:limit]]

        # Cache result
        self.redis_cache.setex(cache_key, 3600, json.dumps(suggestions))

        return suggestions

# Time Complexity: O(p + k) where p = prefix length, k = limit
# Space Complexity: O(n × m) where n = number of queries, m = avg length
```

### Search Ranking Algorithm

**Scoring Function** (BM25 + Custom Features):

```python
def calculate_search_score(query: str, document: dict) -> float:
    """
    Multi-factor ranking score
    """
    # 1. BM25 text relevance (Elasticsearch built-in)
    bm25_score = elasticsearch_bm25(query, document['content'])

    # 2. Popularity / Click-through rate
    popularity_score = document['popularity_score']

    # 3. Recency boost
    days_old = (now() - document['timestamp']).days
    recency_score = 1.0 / (1 + days_old / 30)

    # 4. Personalization (if user history available)
    personalization_score = get_user_preference_score(user_id, document)

    # 5. Query-independent features
    quality_score = document['quality_rating']

    # Weighted combination
    final_score = (
        0.5 * bm25_score +
        0.2 * popularity_score +
        0.15 * recency_score +
        0.1 * personalization_score +
        0.05 * quality_score
    )

    return final_score
```

**Elasticsearch Query DSL**:

```json
{
  "query": {
    "function_score": {
      "query": {
        "multi_match": {
          "query": "machine learning courses",
          "fields": ["title^3", "content", "tags^2"],
          "type": "best_fields",
          "fuzziness": "AUTO"
        }
      },
      "functions": [
        {
          "field_value_factor": {
            "field": "popularity_score",
            "modifier": "log1p",
            "factor": 0.5
          }
        },
        {
          "gauss": {
            "timestamp": {
              "origin": "now",
              "scale": "30d",
              "decay": 0.5
            }
          }
        }
      ],
      "score_mode": "sum",
      "boost_mode": "multiply"
    }
  }
}
```

---

## Step 6: Optimizations & Trade-offs (15 min)

### 1. Typeahead Optimizations

**Problem**: Trie can become very large for billions of queries

**Solution 1: Prefix Sampling**
```python
# Only store top-1000 most popular queries in Trie
# Use Elasticsearch for long-tail queries
if frequency < THRESHOLD:
    store_in_elasticsearch_only(query)
else:
    store_in_trie(query)
```

**Solution 2: Distributed Trie**
```python
# Shard Trie by first character(s)
shard_id = hash(prefix[0]) % num_shards
trie_shard = get_trie_shard(shard_id)
suggestions = trie_shard.get_suggestions(prefix)
```

**Solution 3: Redis Cache Layers**
```
L1: Application memory (hot prefixes: "a", "b", "c")
L2: Redis cache (warm prefixes: "se", "te", "ma")
L3: Trie on disk / Elasticsearch (cold prefixes)
```

### 2. Search Optimizations

**Index Sharding**:
```
# Horizontal sharding by doc_id
shard = hash(doc_id) % num_shards

# Or by category for better query routing
shard = category_to_shard[document.category]
```

**Caching Strategy**:
```python
def search(query: str, filters: dict):
    # Cache key includes query + filters
    cache_key = f"search:{hash(query + str(filters))}"

    # Check cache (TTL: 5 minutes)
    cached_results = redis.get(cache_key)
    if cached_results:
        return cached_results

    # Query Elasticsearch
    results = elasticsearch.search(query, filters)

    # Cache results
    redis.setex(cache_key, 300, results)

    return results
```

**Query Rewriting**:
```python
def rewrite_query(query: str) -> str:
    """
    Optimize query before search
    """
    # 1. Spell correction
    corrected = spell_checker.correct(query)

    # 2. Synonym expansion
    # "ML" -> "machine learning"
    expanded = synonym_dictionary.expand(corrected)

    # 3. Stop word removal (for some queries)
    # "the machine learning" -> "machine learning"
    cleaned = remove_stop_words(expanded)

    return cleaned
```

### 3. Real-Time Indexing

**Challenge**: Index new documents with <5 minute delay

**Solution: Near Real-Time (NRT) Indexing**:

```python
class IndexingPipeline:
    def __init__(self):
        self.buffer = []
        self.buffer_size = 1000
        self.kafka_consumer = KafkaConsumer('new_docs')

    def process_documents(self):
        """
        Batch indexing for efficiency
        """
        for message in self.kafka_consumer:
            doc = json.loads(message.value)
            self.buffer.append(doc)

            # Flush when buffer full
            if len(self.buffer) >= self.buffer_size:
                self.flush_to_elasticsearch()

    def flush_to_elasticsearch(self):
        """
        Bulk index to Elasticsearch
        """
        actions = [
            {
                "_index": "search_index",
                "_id": doc["doc_id"],
                "_source": doc
            }
            for doc in self.buffer
        ]

        helpers.bulk(es_client, actions)
        self.buffer = []

        # Refresh index for NRT search (5s delay)
        es_client.indices.refresh(index="search_index")
```

### 4. Personalization

**User Profile Building**:
```python
class PersonalizationService:
    def build_user_profile(self, user_id: str) -> dict:
        """
        Build user interest profile from history
        """
        # Get user's query history
        queries = get_user_queries(user_id, last_n=100)

        # Get clicked documents
        clicks = get_user_clicks(user_id, last_n=50)

        # Extract topics using LDA/Topic Modeling
        topics = topic_model.infer(queries + clicks)

        # Build weighted profile
        profile = {
            "topics": topics,
            "preferred_categories": extract_categories(clicks),
            "query_patterns": analyze_query_patterns(queries)
        }

        return profile

    def personalize_results(self, results: List, user_profile: dict):
        """
        Re-rank results based on user profile
        """
        for result in results:
            # Boost score if matches user interests
            topic_match = cosine_similarity(
                result['topics'],
                user_profile['topics']
            )
            result['score'] += 0.2 * topic_match

        # Re-sort by adjusted score
        results.sort(key=lambda x: x['score'], reverse=True)

        return results
```

---

## Step 7: Advanced Considerations

### Spell Correction

**Algorithm: Edit Distance + Language Model**

```python
class SpellChecker:
    def __init__(self):
        self.dictionary = load_query_dictionary()  # Top 1M queries
        self.bigram_model = load_bigram_model()

    def suggest_corrections(self, query: str) -> List[str]:
        """
        Suggest corrections using edit distance
        """
        words = query.split()
        corrections = []

        for i, word in enumerate(words):
            # Find words within edit distance 2
            candidates = self._get_candidates(word, max_distance=2)

            if candidates:
                # Rank by language model probability
                scored = [
                    (cand, self._score_correction(words, i, cand))
                    for cand in candidates
                ]
                scored.sort(key=lambda x: x[1], reverse=True)

                corrections.append(scored[0][0])
            else:
                corrections.append(word)

        return ' '.join(corrections)

    def _get_candidates(self, word: str, max_distance: int) -> Set[str]:
        """
        Generate candidates using edit distance
        """
        candidates = set()

        # Check dictionary for close matches
        for dict_word in self.dictionary:
            if abs(len(dict_word) - len(word)) <= max_distance:
                if edit_distance(word, dict_word) <= max_distance:
                    candidates.add(dict_word)

        return candidates

    def _score_correction(self, words: List, index: int, candidate: str) -> float:
        """
        Score correction using bigram model
        """
        # P(candidate | previous_word)
        if index > 0:
            return self.bigram_model.prob(words[index-1], candidate)
        else:
            return self.bigram_model.unigram_prob(candidate)
```

### Query Understanding

**Intent Classification**:

```python
class QueryIntentClassifier:
    """
    Classify query into categories:
    - Navigational: "facebook login"
    - Informational: "how to learn python"
    - Transactional: "buy iphone 15"
    """

    def classify(self, query: str) -> str:
        # Use trained ML model (BERT, etc.)
        features = self.extract_features(query)
        intent = self.model.predict(features)

        return intent

    def extract_features(self, query: str) -> dict:
        return {
            "has_question_words": self._has_question_words(query),
            "has_buy_intent": self._has_buy_words(query),
            "has_brand_name": self._has_brand_name(query),
            "query_length": len(query.split()),
        }
```

### Trending Queries

**Real-Time Trending Detection**:

```python
class TrendingQueryDetector:
    def __init__(self):
        self.window_size = 3600  # 1 hour
        self.redis = RedisClient()

    def track_query(self, query: str):
        """
        Track query frequency in time windows
        """
        timestamp = int(time.time())
        window_key = f"trending:{timestamp // self.window_size}"

        # Increment count in sorted set
        self.redis.zincrby(window_key, 1, query)

        # Set expiration (24 hours)
        self.redis.expire(window_key, 86400)

    def get_trending(self, limit: int = 10) -> List[str]:
        """
        Get top trending queries in current window
        """
        timestamp = int(time.time())
        window_key = f"trending:{timestamp // self.window_size}"

        # Get top queries by score
        trending = self.redis.zrevrange(window_key, 0, limit-1, withscores=True)

        return [query for query, score in trending]
```

### A/B Testing for Ranking

```python
class RankingExperiment:
    def __init__(self):
        self.experiments = {
            "control": {"model": "bm25", "weight": 0.5},
            "treatment_a": {"model": "learning_to_rank", "weight": 0.3},
            "treatment_b": {"model": "neural_ranking", "weight": 0.2}
        }

    def get_ranking_model(self, user_id: str) -> str:
        """
        Assign user to experiment group
        """
        # Consistent hashing for stable assignment
        hash_val = hash(user_id) % 100

        if hash_val < 50:
            return "control"
        elif hash_val < 80:
            return "treatment_a"
        else:
            return "treatment_b"

    def log_result(self, user_id: str, query: str, clicked_doc: str):
        """
        Log experiment results for analysis
        """
        experiment = self.get_ranking_model(user_id)

        event = {
            "user_id": user_id,
            "experiment": experiment,
            "query": query,
            "clicked_doc": clicked_doc,
            "timestamp": time.time()
        }

        kafka_producer.send("experiment_events", event)
```

---

## Complete System Design Diagram

```
                    ┌────────────────┐
                    │     Client     │
                    │  (React/Vue)   │
                    └────────┬───────┘
                             │
                    ┌────────▼────────┐
                    │   CDN / WAF     │
                    │  (CloudFlare)   │
                    └────────┬────────┘
                             │
                    ┌────────▼────────┐
                    │ Load Balancer   │
                    │   (AWS ALB)     │
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
    ┌─────────┐        ┌─────────┐       ┌─────────┐
    │Typeahead│        │ Search  │       │Analytics│
    │ Service │        │ Service │       │ Service │
    └────┬────┘        └────┬────┘       └────┬────┘
         │                  │                  │
    ┌────▼──────┐      ┌────▼─────────┐      │
    │   Trie    │      │Elasticsearch │      │
    │   Cache   │      │   Cluster    │      │
    └────┬──────┘      │  (10 shards) │      │
         │             └────┬─────────┘      │
    ┌────▼──────┐           │                │
    │   Redis   │           │                │
    │  Cluster  │      ┌────▼─────────┐      │
    └───────────┘      │  Ranking     │      │
                       │  Service     │      │
                       │  (ML Model)  │      │
                       └──────────────┘      │
                                             │
    ┌────────────────────────────────────────┘
    │
    ▼
┌───────────────────────────────────────────┐
│         Message Queue (Kafka)             │
│  Topics: queries, clicks, indexing        │
└───────┬───────────────────────┬───────────┘
        │                       │
    ┌───▼──────┐          ┌─────▼─────┐
    │ClickHouse│          │  Indexing │
    │Analytics │          │  Pipeline │
    └──────────┘          └─────┬─────┘
                                │
                          ┌─────▼─────┐
                          │PostgreSQL │
                          │ Metadata  │
                          └───────────┘
```

---

## Step 8: Monitoring & Observability

### Key Metrics to Track

**Typeahead**:
- P50/P95/P99 latency
- Cache hit rate (target: >90%)
- Suggestion relevance (click-through rate)
- QPS per region

**Search**:
- Query latency (P95 < 500ms)
- Index freshness (lag time)
- Search quality metrics:
  - Mean Reciprocal Rank (MRR)
  - Normalized Discounted Cumulative Gain (NDCG)
  - Click-through rate on top results
- Zero-result queries percentage

**Infrastructure**:
- Elasticsearch cluster health
- Indexing throughput
- Cache memory usage
- Kafka consumer lag

### Alerting Rules

```yaml
alerts:
  - name: HighTypeaheadLatency
    condition: p95_latency > 100ms for 5 minutes
    severity: critical

  - name: LowCacheHitRate
    condition: cache_hit_rate < 80% for 10 minutes
    severity: warning

  - name: HighZeroResultRate
    condition: zero_result_rate > 10% for 15 minutes
    severity: warning

  - name: ElasticsearchDown
    condition: cluster_health == "red"
    severity: critical
```

---

## Interview Tips

**Questions to Ask**:
- What type of content are we searching? (Web pages, products, people?)
- What's the expected query volume and latency requirements?
- Do we need personalization?
- What languages do we need to support?
- Do we need fuzzy matching / spell correction?

**Topics to Cover**:
- Trie data structure for typeahead (key optimization)
- Inverted index concept (Elasticsearch internals)
- Caching strategy (multi-tier)
- Ranking algorithm trade-offs
- Scaling strategy (sharding, replication)

**Common Follow-ups**:
- "How would you handle multi-language search?"
  → Use language-specific analyzers, Unicode normalization
- "How do you prevent search abuse (scraping)?"
  → Rate limiting, CAPTCHA, bot detection
- "How would you implement 'Did you mean?' suggestions?"
  → Edit distance algorithm, query logs analysis
- "How do you ensure search quality?"
  → A/B testing, relevance feedback, click-through analysis

---

## Key Takeaways

1. **Two Different Problems**: Typeahead (prefix matching, <100ms) vs Search (ranking, <500ms)
2. **Trie for Typeahead**: Precompute top-k suggestions at each node
3. **Inverted Index for Search**: Core data structure behind Elasticsearch
4. **Caching is Critical**: Multi-tier caching (app → Redis → Elasticsearch)
5. **Ranking = ML Problem**: BM25 baseline + learning-to-rank models
6. **Real-Time Matters**: NRT indexing, incremental updates
7. **Measure Everything**: Latency, relevance metrics, user engagement

---

## Further Reading

- Elasticsearch: The Definitive Guide
- Introduction to Information Retrieval (Manning, Raghavan, Schütze)
- "Searching with Deep Learning" - Google AI Blog
- "Building a Search Engine" - Coursera
- Trie Data Structure - LeetCode problems (#208, #211)
- BM25 Ranking Algorithm
- Learning to Rank (LambdaMART, RankNet)
