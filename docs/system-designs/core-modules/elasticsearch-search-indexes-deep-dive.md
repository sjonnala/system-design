# What's Elasticsearch Used For? Search Indexes - Deep Dive

## Contents

- [What's Elasticsearch Used For? Search Indexes - Deep Dive](#whats-elasticsearch-used-for-search-indexes---deep-dive)
  - [Core Mental Model](#core-mental-model)
  - [Elasticsearch Architecture](#1-elasticsearch-architecture)
  - [Indexing Pipeline & Document Lifecycle](#2-indexing-pipeline--document-lifecycle)
  - [Query DSL & Search Patterns](#3-query-dsl--search-patterns)
  - [Distributed Search: Scatter-Gather](#4-distributed-search-scatter-gather)
  - [Aggregations: Analytics at Scale](#5-aggregations-analytics-at-scale)
  - [Production Use Cases](#6-production-use-cases)
  - [Elasticsearch vs Traditional Databases](#7-elasticsearch-vs-traditional-databases)
  - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
  - [MIND MAP: ELASTICSEARCH CONCEPTS](#mind-map-elasticsearch-concepts)

## Core Mental Model

```text
Elasticsearch = Distributed Search Engine + Document Store
Built on: Apache Lucene (inverted index library)
Written in: Java
Primary Use: Full-text search, log analytics, metrics

Core Equation:
Speed = Inverted Index + RAM Caching + Horizontal Scaling

Near Real-Time Search:
Write → Index → Searchable (within 1 second refresh)
```

**Key Characteristics:**
```
┌──────────────────────────────────────────────────────┐
│ 1. DOCUMENT-ORIENTED: JSON documents, schemaless     │
│ 2. DISTRIBUTED: Sharding + replication out-of-box   │
│ 3. NEAR REAL-TIME: Visible in ~1 second             │
│ 4. FULL-TEXT SEARCH: Inverted index, relevance      │
│ 5. AGGREGATIONS: Real-time analytics (like SQL)     │
│ 6. RESTful API: HTTP JSON interface                 │
└──────────────────────────────────────────────────────┘
```

**Terminology Mapping:**
```
┌──────────────────┬───────────────────┬──────────────────┐
│ Elasticsearch    │ Relational DB     │ Description      │
├──────────────────┼───────────────────┼──────────────────┤
│ Index            │ Database          │ Logical namespace│
│ Document         │ Row               │ JSON object      │
│ Field            │ Column            │ Key-value pair   │
│ Mapping          │ Schema            │ Field types      │
│ Shard            │ Partition         │ Horizontal split │
│ Replica          │ Replica           │ Copy for HA      │
└──────────────────┴───────────────────┴──────────────────┘
```

**Architecture Overview:**
```text
Elasticsearch Cluster:

┌─────────────────────────────────────────────────────┐
│ Master Node (cluster coordination)                  │
│  - Index creation/deletion                          │
│  - Shard allocation                                 │
│  - Health monitoring                                │
└─────────────────────────────────────────────────────┘
        ↓ coordinates
┌─────────────────────────────────────────────────────┐
│ Data Nodes (store data + execute queries)           │
│                                                      │
│  Node 1          Node 2          Node 3             │
│  ┌─────────┐    ┌─────────┐    ┌─────────┐        │
│  │Shard 0  │    │Shard 1  │    │Shard 2  │        │  Primary shards
│  │Replica 1│    │Replica 2│    │Replica 0│        │  Replica shards
│  └─────────┘    └─────────┘    └─────────┘        │
│                                                      │
│  Each shard is a Lucene index (inverted index)      │
└─────────────────────────────────────────────────────┘
        ↓ expose API
┌─────────────────────────────────────────────────────┐
│ Client/Coordinating Nodes (route requests)          │
│  - Receive client requests                          │
│  - Scatter queries to data nodes                    │
│  - Gather results and merge                         │
│  - Return to client                                 │
└─────────────────────────────────────────────────────┘
```

---

### 1. **Elasticsearch Architecture**

🎓 **PROFESSOR**: Elasticsearch distributes data using **sharding** for horizontal scalability:

```text
Index Sharding:

Index: "products" (1 TB data, 10M documents)
  ↓ split into
Shards: [Shard 0] [Shard 1] [Shard 2] [Shard 3] [Shard 4]
         250 GB    250 GB    250 GB    250 GB    250 GB

Each shard is an independent Lucene index:
- Can be on different nodes
- Queries executed in parallel
- Results merged

Replication (High Availability):
Primary Shard 0 → Replica Shard 0 (different node)
Primary Shard 1 → Replica Shard 1 (different node)

If node with Primary 0 fails:
- Replica 0 promoted to Primary
- New replica created on healthy node
- No data loss!
```

#### Shard Allocation Example

```text
3-Node Cluster, 5 Primary Shards, 1 Replica per shard:

┌──────────────────┬──────────────────┬──────────────────┐
│ Node 1           │ Node 2           │ Node 3           │
├──────────────────┼──────────────────┼──────────────────┤
│ P0 (primary)     │ P1 (primary)     │ P2 (primary)     │
│ R1 (replica)     │ R2 (replica)     │ R3 (replica)     │
│ R4 (replica)     │ P3 (primary)     │ P4 (primary)     │
│                  │                  │ R0 (replica)     │
└──────────────────┴──────────────────┴──────────────────┘

Shard Allocation Rules:
1. Primary and its replica NEVER on same node
2. Balanced distribution across nodes
3. Rack awareness (optional - different racks)

Node 2 fails:
1. Elasticsearch detects failure (health check)
2. Replica 1, Replica 2, Primary 3 lost
3. Promote: R1 (Node 1) → P1, R2 (Node 3) → P2, R3 (Node 3) → P3
4. Create new replicas on Node 1 and Node 3
5. Cluster remains available (slight degradation)
```

🏗️ **ARCHITECT**: Cluster configuration:

```yaml
# elasticsearch.yml configuration

# Cluster name (nodes with same cluster.name join together)
cluster.name: production-search

# Node roles
node.roles:
  - master    # Can be elected as master
  - data      # Stores data and executes queries
  - ingest    # Preprocessing pipeline

# Discovery (cluster formation)
discovery.seed_hosts:
  - es-node-1.example.com:9300
  - es-node-2.example.com:9300
  - es-node-3.example.com:9300

cluster.initial_master_nodes:
  - es-node-1
  - es-node-2
  - es-node-3

# Shard allocation
cluster.routing.allocation.awareness.attributes: rack_id
node.attr.rack_id: rack1

# Memory settings (critical!)
bootstrap.memory_lock: true  # Lock memory to prevent swapping
# Set heap size: -Xms16g -Xmx16g (50% of RAM, max 32GB)
```

**Python Client Example:**

```python
from elasticsearch import Elasticsearch

# Connect to cluster
es = Elasticsearch(
    ['http://localhost:9200'],
    basic_auth=('elastic', 'password'),
    request_timeout=30
)

# Check cluster health
health = es.cluster.health()
print(f"Status: {health['status']}")  # green, yellow, red
print(f"Nodes: {health['number_of_nodes']}")
print(f"Shards: {health['active_shards']}")
print(f"Relocating: {health['relocating_shards']}")
print(f"Unassigned: {health['unassigned_shards']}")

# Cluster stats
stats = es.cluster.stats()
print(f"Documents: {stats['indices']['count']}")
print(f"Store size: {stats['indices']['store']['size']}")
```

---

### 2. **Indexing Pipeline & Document Lifecycle**

🎓 **PROFESSOR**: Document indexing flows through multiple stages:

```text
Indexing Pipeline:

1. Client Request
   POST /products/_doc/123
   { "name": "Laptop", "price": 999 }
     ↓
2. Coordinating Node
   - Parse request
   - Route to primary shard (hash-based routing)
   - document_id → shard_num = hash(doc_id) % num_primary_shards
     ↓
3. Primary Shard (Lucene)
   - Analyze text fields (tokenize, stem, etc.)
   - Build inverted index
   - Write to in-memory buffer
   - Append to transaction log (crash safety)
     ↓
4. Replicate to Replica Shards
   - Forward operation to all replicas
   - Wait for confirmation (configurable)
     ↓
5. Return Success to Client
   - Document indexed (in translog)
   - Not yet searchable!
     ↓
6. Refresh (every 1 second by default)
   - Flush in-memory buffer to segment (Lucene segment)
   - Segment is immutable, searchable
   - Document now searchable!
     ↓
7. Merge (background, periodic)
   - Merge small segments into large segments
   - Delete logically deleted documents
   - Optimize for read performance
     ↓
8. Flush (periodic)
   - Commit Lucene segments to disk
   - Clear transaction log
   - Persist durably
```

**Near Real-Time (NRT) Search:**

```text
Write at 10:00:00.000
Refresh at 10:00:01.000 (1 second later)
Document searchable at 10:00:01.000

Why 1 second delay?
- Creating Lucene segments is expensive
- Too frequent: CPU thrashing
- Too rare: Not "real-time" enough
- 1 second: Sweet spot for most use cases

Tuning:
- Real-time updates: refresh_interval: 100ms (expensive!)
- Bulk indexing: refresh_interval: 30s (throughput > latency)
- Logging: refresh_interval: -1 (manual refresh)
```

🏗️ **ARCHITECT**: Index settings and mapping:

```python
# Create index with settings and mapping
index_config = {
    "settings": {
        "number_of_shards": 5,
        "number_of_replicas": 1,
        "refresh_interval": "1s",  # Default

        "analysis": {
            "analyzer": {
                "my_custom_analyzer": {
                    "type": "custom",
                    "tokenizer": "standard",
                    "filter": [
                        "lowercase",
                        "stop",
                        "snowball"  # Stemming
                    ]
                }
            }
        }
    },
    "mappings": {
        "properties": {
            "title": {
                "type": "text",  # Full-text search
                "analyzer": "my_custom_analyzer",
                "fields": {
                    "keyword": {
                        "type": "keyword"  # Exact match, aggregations
                    }
                }
            },
            "price": {
                "type": "float"
            },
            "created_at": {
                "type": "date",
                "format": "yyyy-MM-dd HH:mm:ss||epoch_millis"
            },
            "category": {
                "type": "keyword"  # No analysis, exact match
            },
            "description": {
                "type": "text",
                "analyzer": "english"  # Built-in English analyzer
            }
        }
    }
}

es.indices.create(index="products", body=index_config)


# Index document
doc = {
    "title": "Wireless Bluetooth Headphones",
    "price": 79.99,
    "category": "Electronics",
    "description": "Premium noise-cancelling headphones with 30-hour battery",
    "created_at": "2024-01-15 10:30:00"
}

# Index with auto-generated ID
response = es.index(index="products", document=doc)
print(f"Indexed: {response['_id']}")

# Index with specific ID
es.index(index="products", id="12345", document=doc)


# Bulk indexing (much faster!)
from elasticsearch.helpers import bulk

actions = [
    {
        "_index": "products",
        "_id": i,
        "_source": {
            "title": f"Product {i}",
            "price": i * 10
        }
    }
    for i in range(10000)
]

success, failed = bulk(es, actions)
print(f"Indexed: {success}, Failed: {failed}")
```

---

### 3. **Query DSL & Search Patterns**

🎓 **PROFESSOR**: Elasticsearch Query DSL is a **rich JSON-based query language**:

#### Query Types Hierarchy

```text
Query Context (scoring):
├─ Match Query (full-text, analyzed)
├─ Multi-Match Query (multiple fields)
├─ Match Phrase Query (exact phrase, ordered)
├─ Query String Query (Lucene syntax)
└─ More Like This (similarity search)

Filter Context (no scoring, cacheable):
├─ Term Query (exact match, not analyzed)
├─ Terms Query (match any of values)
├─ Range Query (>, <, >=, <=)
├─ Exists Query (field exists)
└─ Bool Query (AND, OR, NOT)

Compound Queries:
└─ Bool Query
   ├─ must (AND, scored)
   ├─ should (OR, scored)
   ├─ must_not (NOT, filtered)
   └─ filter (AND, not scored, cached)
```

🏗️ **ARCHITECT**: Common search patterns:

#### Pattern 1: Full-Text Search

```python
# Simple match query (most common)
query = {
    "query": {
        "match": {
            "description": {
                "query": "wireless headphones",
                "operator": "and"  # Both words required (default: "or")
            }
        }
    }
}

results = es.search(index="products", body=query)


# Multi-field search with boosting
query = {
    "query": {
        "multi_match": {
            "query": "bluetooth speaker",
            "fields": [
                "title^3",      # Title 3x more important
                "description",
                "category^2"    # Category 2x important
            ],
            "type": "best_fields",  # Best matching field wins
            "tie_breaker": 0.3      # Boost other fields by 30%
        }
    }
}
```

#### Pattern 2: Filtering + Search

```python
# Bool query: Combine search + filters
query = {
    "query": {
        "bool": {
            "must": [
                # Full-text search (scored)
                {
                    "match": {
                        "description": "wireless headphones"
                    }
                }
            ],
            "filter": [
                # Exact filters (not scored, cacheable)
                {
                    "term": {
                        "category": "Electronics"
                    }
                },
                {
                    "range": {
                        "price": {
                            "gte": 50,
                            "lte": 200
                        }
                    }
                },
                {
                    "range": {
                        "created_at": {
                            "gte": "2024-01-01"
                        }
                    }
                }
            ],
            "should": [
                # Optional boosters (increase score)
                {
                    "term": {
                        "brand": "Sony"
                    }
                }
            ],
            "must_not": [
                # Exclude
                {
                    "term": {
                        "status": "discontinued"
                    }
                }
            ]
        }
    }
}
```

#### Pattern 3: Phrase Search

```python
# Exact phrase match
query = {
    "query": {
        "match_phrase": {
            "description": {
                "query": "noise cancelling",
                "slop": 2  # Allow 2 words between (fuzzy phrase)
            }
        }
    }
}

# Example matches:
# "noise cancelling" ✓ (exact)
# "noise reduction cancelling" ✓ (slop=1)
# "noise and echo cancelling" ✓ (slop=2)
# "noise very good sound cancelling" ✗ (slop=3, exceeds limit)
```

#### Pattern 4: Fuzzy Search (Typo Tolerance)

```python
# Fuzzy match (edit distance)
query = {
    "query": {
        "match": {
            "title": {
                "query": "blutooth",  # Typo!
                "fuzziness": "AUTO"    # Auto edit distance (1-2)
            }
        }
    }
}

# Matches: "bluetooth" (edit distance 1)

# Manual fuzziness
query = {
    "query": {
        "fuzzy": {
            "title": {
                "value": "wireles",  # Missing 's'
                "fuzziness": 1       # Max 1 character different
            }
        }
    }
}

# Matches: "wireless"
```

#### Pattern 5: Pagination

```python
# Basic pagination (shallow - up to 10,000 results)
query = {
    "from": 20,   # Offset (page 3 if size=10)
    "size": 10,   # Results per page
    "query": {
        "match_all": {}
    }
}

# Deep pagination (search_after - for >10K results)
query = {
    "size": 100,
    "query": {"match_all": {}},
    "sort": [
        {"created_at": "desc"},
        {"_id": "asc"}  # Tiebreaker
    ]
}

# First request
results = es.search(index="products", body=query)
last_hit = results['hits']['hits'][-1]
last_sort = last_hit['sort']

# Next page
query["search_after"] = last_sort
next_results = es.search(index="products", body=query)
```

---

### 4. **Distributed Search: Scatter-Gather**

🎓 **PROFESSOR**: Elasticsearch parallelizes search across shards:

```text
Scatter-Gather Pattern:

Client: "Search for 'laptop'"
     ↓
Coordinating Node:
     ↓ scatter query to all shards
┌─────────┬─────────┬─────────┬─────────┐
│ Shard 0 │ Shard 1 │ Shard 2 │ Shard 3 │  (parallel execution)
│ Search  │ Search  │ Search  │ Search  │
│ Top 10  │ Top 10  │ Top 10  │ Top 10  │
└────┬────┴────┬────┴────┬────┴────┬────┘
     │         │         │         │
     └─────────┴────gather results─┘
               ↓
     Coordinating Node:
     - Merge 40 results (4 shards × top 10)
     - Re-sort by score
     - Return global top 10
               ↓
     Client: Receives top 10 results
```

**Query Execution Phases:**

```text
Phase 1: Query (Scatter)
- Coordinating node sends query to all shards
- Each shard executes query locally
- Returns document IDs + scores (lightweight)
- Coordinating node merges and sorts

Phase 2: Fetch (Targeted)
- Coordinating node identifies which documents needed
- Requests full documents from relevant shards only
- Assembles final response

Why two phases?
- Minimize data transfer
- Only fetch documents actually shown to user
- Phase 1: 4 shards × 10 IDs = 40 IDs (small)
- Phase 2: Fetch 10 full documents (optimized)
```

🏗️ **ARCHITECT**: Performance tuning:

```python
# Preference routing (shard selection)
# Use same shard for same user (cache benefit)
query = {
    "query": {"match_all": {}}
}

results = es.search(
    index="products",
    body=query,
    preference=f"user_{user_id}"  # Route to consistent shard
)


# Request cache (identical queries)
# Cached at shard level for filter context
query = {
    "query": {
        "bool": {
            "filter": [
                {"term": {"category": "Electronics"}},  # Cacheable
                {"range": {"price": {"gte": 100}}}      # Cacheable
            ]
        }
    }
}

# First request: Execute query, cache result
# Subsequent requests: Return from cache (until refresh)


# Field data loading (aggregations on text fields)
# Warning: Memory intensive!
query = {
    "aggs": {
        "popular_brands": {
            "terms": {
                "field": "brand.keyword",  # Use keyword field!
                "size": 10
            }
        }
    }
}

# Never aggregate on analyzed "text" fields - use "keyword" instead
```

---

### 5. **Aggregations: Analytics at Scale**

🎓 **PROFESSOR**: Aggregations are Elasticsearch's **real-time analytics engine**:

```text
Aggregation Types:

Metric Aggregations (compute metrics):
├─ avg, sum, min, max, stats
├─ cardinality (distinct count)
├─ percentiles (P50, P95, P99)
└─ value_count (count)

Bucket Aggregations (group data):
├─ terms (group by field values)
├─ range (group by numeric/date ranges)
├─ histogram (group by intervals)
├─ date_histogram (time-series bucketing)
└─ filters (custom groups)

Pipeline Aggregations (aggregations on aggregations):
├─ moving_avg (moving average)
├─ derivative (rate of change)
└─ bucket_sort (sort buckets)
```

🏗️ **ARCHITECT**: Common aggregation patterns:

#### Pattern 1: Faceted Search (E-commerce Filters)

```python
# Product search with facets
query = {
    "query": {
        "match": {
            "title": "laptop"
        }
    },
    "aggs": {
        # Brand facet
        "brands": {
            "terms": {
                "field": "brand.keyword",
                "size": 20
            }
        },
        # Price ranges
        "price_ranges": {
            "range": {
                "field": "price",
                "ranges": [
                    {"to": 500},
                    {"from": 500, "to": 1000},
                    {"from": 1000, "to": 2000},
                    {"from": 2000}
                ]
            }
        },
        # Average price
        "avg_price": {
            "avg": {
                "field": "price"
            }
        }
    }
}

results = es.search(index="products", body=query)

# Extract facets
brands = results['aggregations']['brands']['buckets']
for bucket in brands:
    print(f"{bucket['key']}: {bucket['doc_count']} products")

price_ranges = results['aggregations']['price_ranges']['buckets']
for bucket in price_ranges:
    print(f"${bucket.get('from', 0)}-${bucket.get('to', '∞')}: {bucket['doc_count']}")
```

#### Pattern 2: Time-Series Analytics (Metrics, Logs)

```python
# Metrics over time
query = {
    "query": {
        "range": {
            "timestamp": {
                "gte": "now-7d/d",  # Last 7 days
                "lte": "now/d"
            }
        }
    },
    "aggs": {
        "sales_over_time": {
            "date_histogram": {
                "field": "timestamp",
                "calendar_interval": "day",
                "extended_bounds": {
                    "min": "now-7d/d",
                    "max": "now/d"
                }
            },
            "aggs": {
                # Metrics per day
                "revenue": {
                    "sum": {"field": "amount"}
                },
                "avg_order_value": {
                    "avg": {"field": "amount"}
                },
                "order_count": {
                    "value_count": {"field": "_id"}
                }
            }
        }
    },
    "size": 0  # Don't return documents, only aggregations
}

results = es.search(index="orders", body=query)

# Extract time-series
for bucket in results['aggregations']['sales_over_time']['buckets']:
    date = bucket['key_as_string']
    revenue = bucket['revenue']['value']
    avg_order = bucket['avg_order_value']['value']
    count = bucket['order_count']['value']

    print(f"{date}: ${revenue:,.2f} revenue, {count} orders, ${avg_order:.2f} avg")
```

#### Pattern 3: Nested Aggregations (Drill-down)

```python
# Category → Brand → Price stats
query = {
    "aggs": {
        "categories": {
            "terms": {"field": "category.keyword"},
            "aggs": {
                "brands": {
                    "terms": {"field": "brand.keyword"},
                    "aggs": {
                        "price_stats": {
                            "stats": {"field": "price"}
                        }
                    }
                }
            }
        }
    },
    "size": 0
}

# Result structure:
# Electronics (500 products)
#   ├─ Sony (200 products)
#   │   └─ Price: avg $500, min $200, max $1000
#   └─ Samsung (150 products)
#       └─ Price: avg $600, min $300, max $1200
```

---

### 6. **Production Use Cases**

🏗️ **ARCHITECT**: Real-world Elasticsearch applications:

#### Use Case 1: E-commerce Product Search

```python
class ProductSearchService:
    """
    E-commerce search with autocomplete, filters, facets
    """
    def __init__(self, es_client):
        self.es = es_client

    def search_products(self, query: str, filters: dict, page: int = 1):
        """
        Full-featured product search
        """
        # Build query
        must = [
            {
                "multi_match": {
                    "query": query,
                    "fields": ["title^3", "description", "brand^2"],
                    "fuzziness": "AUTO"
                }
            }
        ]

        filter_clauses = []

        if filters.get('category'):
            filter_clauses.append({
                "term": {"category.keyword": filters['category']}
            })

        if filters.get('min_price') or filters.get('max_price'):
            range_filter = {}
            if filters.get('min_price'):
                range_filter['gte'] = filters['min_price']
            if filters.get('max_price'):
                range_filter['lte'] = filters['max_price']

            filter_clauses.append({
                "range": {"price": range_filter}
            })

        # Build full query
        body = {
            "from": (page - 1) * 20,
            "size": 20,
            "query": {
                "bool": {
                    "must": must,
                    "filter": filter_clauses
                }
            },
            "aggs": {
                "categories": {
                    "terms": {"field": "category.keyword", "size": 10}
                },
                "brands": {
                    "terms": {"field": "brand.keyword", "size": 20}
                },
                "price_ranges": {
                    "range": {
                        "field": "price",
                        "ranges": [
                            {"to": 50}, {"from": 50, "to": 100},
                            {"from": 100, "to": 200}, {"from": 200}
                        ]
                    }
                }
            },
            "highlight": {
                "fields": {
                    "title": {},
                    "description": {}
                }
            }
        }

        return self.es.search(index="products", body=body)
```

#### Use Case 2: Log Analytics (ELK Stack)

```python
# Index logs from application
log_entry = {
    "timestamp": "2024-01-15T10:30:45Z",
    "level": "ERROR",
    "service": "payment-service",
    "message": "Payment gateway timeout",
    "user_id": 12345,
    "request_id": "abc-def-123",
    "duration_ms": 5000,
    "status_code": 504
}

es.index(index="logs-2024.01.15", document=log_entry)


# Search logs: Find errors in last hour
query = {
    "query": {
        "bool": {
            "must": [
                {"match": {"message": "timeout"}},
                {"term": {"level": "ERROR"}}
            ],
            "filter": [
                {
                    "range": {
                        "timestamp": {
                            "gte": "now-1h"
                        }
                    }
                }
            ]
        }
    },
    "aggs": {
        "errors_by_service": {
            "terms": {"field": "service.keyword", "size": 10}
        },
        "errors_over_time": {
            "date_histogram": {
                "field": "timestamp",
                "fixed_interval": "5m"
            }
        }
    },
    "sort": [{"timestamp": "desc"}]
}
```

#### Use Case 3: Real-Time Metrics & Monitoring

```python
# Index metrics (time-series data)
metric = {
    "timestamp": int(time.time() * 1000),
    "metric_name": "api.latency",
    "value": 245,  # milliseconds
    "tags": {
        "endpoint": "/api/users",
        "method": "GET",
        "status": 200,
        "region": "us-east-1"
    }
}

es.index(index="metrics", document=metric)


# Query: P95 latency per endpoint (last 5 minutes)
query = {
    "query": {
        "bool": {
            "filter": [
                {"term": {"metric_name": "api.latency"}},
                {"range": {"timestamp": {"gte": "now-5m"}}}
            ]
        }
    },
    "aggs": {
        "by_endpoint": {
            "terms": {"field": "tags.endpoint.keyword"},
            "aggs": {
                "latency_percentiles": {
                    "percentiles": {
                        "field": "value",
                        "percents": [50, 95, 99]
                    }
                }
            }
        }
    },
    "size": 0
}
```

---

### 7. **Elasticsearch vs Traditional Databases**

🎓 **PROFESSOR**: When to use each:

```text
┌─────────────────────┬──────────────────┬──────────────────┐
│ Requirement         │ PostgreSQL       │ Elasticsearch    │
├─────────────────────┼──────────────────┼──────────────────┤
│ Full-text search    │ Basic (pg_trgm)  │ Excellent ✅     │
│ ACID transactions   │ Excellent ✅     │ None ❌          │
│ Joins               │ Excellent ✅     │ Limited ❌       │
│ Real-time analytics │ Slow for big data│ Fast ✅          │
│ Horizontal scaling  │ Complex          │ Built-in ✅      │
│ Schema enforcement  │ Strict ✅        │ Flexible         │
│ Consistency         │ Strong ✅        │ Eventual         │
│ Use case            │ OLTP, CRUD       │ Search, Logs     │
└─────────────────────┴──────────────────┴──────────────────┘

Hybrid Architecture (Common):
┌──────────────────┐       ┌──────────────────┐
│  PostgreSQL      │       │  Elasticsearch   │
│  (source of      │  sync │  (search index)  │
│   truth, CRUD)   │──────→│  (read replica)  │
└──────────────────┘       └──────────────────┘
       ↑                            ↓
    Write ops                   Search queries
```

**Data Sync Patterns:**

```python
# Pattern 1: Application-level sync
def create_product(product_data):
    # 1. Write to PostgreSQL (source of truth)
    product = db.session.add(Product(**product_data))
    db.session.commit()

    # 2. Index in Elasticsearch (async)
    es.index(index="products", id=product.id, document=product.to_dict())

    return product


# Pattern 2: Change Data Capture (CDC)
# Use Debezium, Maxwell, or pg_logical to stream changes
# PostgreSQL → Kafka → Elasticsearch

# Pattern 3: Periodic batch sync
from elasticsearch.helpers import bulk

def sync_to_elasticsearch():
    # Fetch from PostgreSQL
    products = Product.query.filter_by(updated_at > last_sync).all()

    # Bulk index in Elasticsearch
    actions = [
        {
            "_index": "products",
            "_id": p.id,
            "_source": p.to_dict()
        }
        for p in products
    ]

    bulk(es, actions)
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. Requirements Clarification
```
Questions:
- Query types? (Full-text, filters, aggregations)
- Scale? (Documents, queries/sec, index size)
- Latency? (Real-time vs batch acceptable)
- Consistency needs? (Eventual OK vs must be immediate)
```

### 2. High-Level Design
```
Components:
1. Application → PostgreSQL (CRUD, ACID)
2. Change stream → Kafka (buffer, durability)
3. Kafka → Elasticsearch (indexing)
4. Application → Elasticsearch (search)

Why not write directly to Elasticsearch?
- No transactions (PostgreSQL is source of truth)
- Consistency guarantees (PostgreSQL)
- Backup/recovery easier with SQL
```

### 3. Capacity Planning
```
Index size estimation:
- 10M documents × 5KB avg = 50 GB raw data
- Inverted index overhead: ~50% = 75 GB
- Replicas (1 replica): ×2 = 150 GB

Shard sizing:
- Target: 20-50 GB per shard (optimal)
- 150 GB / 30 GB = 5 primary shards
- Total shards: 5 primary + 5 replica = 10 shards

Nodes:
- 3 nodes minimum (HA)
- 150 GB / 3 nodes = 50 GB per node
- Heap: 32 GB max, 50% of RAM
- Servers: 64 GB RAM, 100 GB SSD
```

### 4. Optimization Strategies
```
For Indexing:
- Bulk API (1000-5000 docs per request)
- Disable refresh during bulk (refresh_interval: -1)
- Reduce replicas during indexing (restore after)

For Searching:
- Filter context (cacheable, no scoring)
- Query cache (node-level, identical queries)
- Request cache (shard-level, filter results)
- Routing (consistent shard for same user)

For Aggregations:
- Use keyword fields (not text)
- Limit bucket size (size parameter)
- Use filters to reduce dataset
```

---

## 🧠 **MIND MAP: ELASTICSEARCH CONCEPTS**
```
                    ELASTICSEARCH
                          |
        ┌─────────────────┼─────────────────┐
        ↓                 ↓                  ↓
  ARCHITECTURE        SEARCH            AGGREGATIONS
        |                 |                  |
   ┌────┴────┐      ┌────┴────┐        ┌────┴────┐
   ↓         ↓      ↓         ↓        ↓         ↓
 Shards  Replicas  Match   Bool    Terms     Date
 Master  Data      Multi   Filter  Range   Histogram
 Cluster Nodes     Phrase  Fuzzy   Metric  Facets
```

### 💡 **EMOTIONAL ANCHORS**

1. **Elasticsearch = Library Card Catalog 📚**
   - Each card: Document
   - Card index: Inverted index
   - Multiple libraries: Shards
   - Backup copies: Replicas

2. **Sharding = Pizza Slices 🍕**
   - Whole pizza: Index
   - Slices: Shards (easier to handle)
   - More people: Parallel eating (search)
   - Each person gets slices: Distributed

3. **Aggregations = Pivot Tables 📊**
   - Group data: Bucket aggregations
   - Calculate metrics: Metric aggregations
   - Nested groups: Sub-aggregations
   - Real-time Excel for big data!

---

## 🔑 **KEY TAKEAWAYS**

```
1. Elasticsearch = Distributed Lucene + JSON API
2. Sharding enables horizontal scaling (parallelism)
3. Near real-time: 1-second refresh interval (tunable)
4. Query DSL: Rich, expressive JSON queries
5. Aggregations: Real-time analytics at scale
6. Not a database: Use with PostgreSQL/MySQL
7. Use cases: Search, logs, metrics, analytics
8. ELK Stack: Elasticsearch + Logstash + Kibana
```

**Production Mindset**:
> "Elasticsearch excels at search and analytics but lacks ACID transactions. Use it as a specialized index alongside your primary database, not as a replacement. Sync data via Change Data Capture (Debezium + Kafka) for reliability."
