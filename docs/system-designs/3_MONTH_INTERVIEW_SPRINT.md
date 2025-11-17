# 3-Month Interview Sprint - Quick Reference

**Goal:** Interview-ready in 12 weeks
**Time Required:** 120-150 hours (10-12 hours/week)
**Coverage:** Top 40-45 topics with highest interview frequency

---

## Week-by-Week Breakdown

### PHASE 1: CORE FUNDAMENTALS (Weeks 1-4) - 40-48 hours

| Week | Topics | Hours | Priority | Status |
|------|--------|-------|----------|--------|
| **Week 1** | Databases & Storage | 10-12h | CRITICAL | ⬜ |
| | - SQL vs NoSQL, ACID, Isolation levels | 4h | Must know | ⬜ |
| | - B-Tree, Hash indexes, LSM trees | 4h | Must know | ⬜ |
| | - Bloom filters | 2h | Must know | ⬜ |
| | - Practice problems | 2h | | ⬜ |
| **Week 2** | Replication & Consistency | 10-12h | CRITICAL | ⬜ |
| | - Single leader replication | 4h | Must know | ⬜ |
| | - Multi-leader, eventual consistency | 4h | Must know | ⬜ |
| | - CAP theorem, quorums | 2h | Must know | ⬜ |
| | - Practice problems | 2h | | ⬜ |
| **Week 3** | Partitioning & Caching | 10-12h | CRITICAL | ⬜ |
| | - Partitioning strategies, consistent hashing | 4h | Must know | ⬜ |
| | - Distributed caching, eviction policies | 4h | Must know | ⬜ |
| | - CDN, load balancing | 2h | Must know | ⬜ |
| | - Practice problems | 2h | | ⬜ |
| **Week 4** | Message Queues & Consensus | 10-12h | CRITICAL | ⬜ |
| | - Kafka architecture | 6h | Must know | ⬜ |
| | - Raft/Paxos overview, ZooKeeper | 4h | Important | ⬜ |
| | - Practice problems | 2h | | ⬜ |

**Phase 1 Checkpoint:** Can you design Twitter, Instagram, Uber? If yes, proceed. If no, review.

---

### PHASE 2: NOSQL & ADVANCED CONCEPTS (Weeks 5-8) - 40-48 hours

| Week | Topics | Hours | Priority | Status |
|------|--------|-------|----------|--------|
| **Week 5** | Dynamo & DynamoDB | 10-12h | HIGH | ⬜ |
| | - Dynamo paper, architecture | 6h | Important | ⬜ |
| | - DynamoDB specifics | 4h | Important | ⬜ |
| | - Practice: Shopping cart system | 2h | | ⬜ |
| **Week 6** | Cassandra & MongoDB | 10-12h | HIGH | ⬜ |
| | - Cassandra architecture | 5h | Important | ⬜ |
| | - MongoDB document model | 5h | Important | ⬜ |
| | - Database comparison | 2h | | ⬜ |
| **Week 7** | Distributed Transactions | 10-12h | MEDIUM | ⬜ |
| | - Two-phase commit (2PC) | 4h | Important | ⬜ |
| | - Saga pattern | 4h | Important | ⬜ |
| | - Practice: Payment system | 2h | | ⬜ |
| **Week 8** | BigTable/HBase & Column Stores | 10-12h | MEDIUM | ⬜ |
| | - BigTable paper, HBase | 6h | Good to know | ⬜ |
| | - Column-oriented storage | 4h | Good to know | ⬜ |
| | - Practice: Analytics system | 2h | | ⬜ |

**Phase 2 Checkpoint:** Can you explain when to use Cassandra vs DynamoDB vs MongoDB? Proceed if yes.

---

### PHASE 3: SPECIALIZED TOPICS & PRACTICE (Weeks 9-12) - 40-48 hours

| Week | Topics | Hours | Priority | Status |
|------|--------|-------|----------|--------|
| **Week 9** | Stream Processing & Real-time | 10-12h | MEDIUM | ⬜ |
| | - Stream processing concepts | 4h | Important | ⬜ |
| | - Kafka deep dive | 4h | Important | ⬜ |
| | - WebSocket, real-time communication | 2h | Good to know | ⬜ |
| | - Practice: Real-time analytics | 2h | | ⬜ |
| **Week 10** | Observability & Specialized DBs | 10-12h | MEDIUM | ⬜ |
| | - Distributed tracing (Dapper) | 4h | Good to know | ⬜ |
| | - Time series databases | 4h | Good to know | ⬜ |
| | - Practice: Monitoring system | 2h | | ⬜ |
| **Week 11** | Advanced Systems | 10-12h | MEDIUM | ⬜ |
| | - Google Spanner overview | 5h | Good to know | ⬜ |
| | - Aurora, cloud architectures | 3h | Good to know | ⬜ |
| | - Practice: Global system | 4h | | ⬜ |
| **Week 12** | FINAL REVIEW & MOCK INTERVIEWS | 10-12h | CRITICAL | ⬜ |
| | - Review all notes, create cheat sheets | 4h | Must do | ⬜ |
| | - Mock interviews (3-4 problems) | 4h | Must do | ⬜ |
| | - Weak areas deep dive | 4h | Must do | ⬜ |

**Final Checkpoint:** Can you solve 15+ different system design problems confidently? Ready for interviews!

---

## Daily Schedule Template

### Weekday (2 hours/day, 5 days = 10 hours)
```
Monday-Thursday: Deep Learning (2h each)
- 7:00-8:00 PM: Study new material (1h)
  * Read documentation/papers
  * Watch video tutorials
  * Take structured notes
- 8:00-9:00 PM: Practice & Apply (1h)
  * Work through examples
  * Draw architecture diagrams
  * Create flashcards

Friday: Light Review (2h)
- 7:00-8:00 PM: Week review (1h)
- 8:00-9:00 PM: Practice problem (1h)
```

### Weekend (2-4 hours)
```
Saturday or Sunday Morning:
- 9:00-11:00 AM: Deep dive session (2h)
  * Complex topic study
  * Work through difficult concepts
- 11:00-12:00 PM: Practice design problem (1h)
- Optional: Additional 1-2h for weak areas
```

---

## Topic Checklist by Interview Frequency

### TIER 1: Asked in 90%+ Interviews (Must Know)
**Time: ~60-70 hours**

#### Databases (8-10h)
- [ ] SQL vs NoSQL tradeoffs (when to use each)
- [ ] ACID properties & guarantees
- [ ] Isolation levels (Read Committed, Snapshot, Serializable)
- [ ] Database indexes (B-Tree for range, Hash for equality)
- [ ] When to denormalize

#### Replication (12-15h)
- [ ] Single leader replication (read scalability)
- [ ] Multi-leader replication (write scalability, conflicts)
- [ ] Leaderless replication (high availability)
- [ ] Eventual consistency & stale reads
- [ ] Write conflict resolution strategies
- [ ] Read-your-writes consistency

#### Partitioning (6-8h)
- [ ] Partitioning by key range
- [ ] Partitioning by hash
- [ ] Consistent hashing (add/remove nodes)
- [ ] Hot spots & rebalancing

#### Caching (9-11h)
- [ ] Cache-aside pattern
- [ ] Write-through vs write-back
- [ ] Cache eviction: LRU, LFU, LRU-K
- [ ] Cache invalidation strategies
- [ ] Redis use cases
- [ ] CDN for static content

#### Load Balancing (3-4h)
- [ ] Round robin, least connections, weighted
- [ ] Layer 4 vs Layer 7 load balancing
- [ ] Health checks
- [ ] Session affinity

#### Message Queues (6-8h)
- [ ] Kafka architecture (topics, partitions, consumer groups)
- [ ] At-least-once vs exactly-once delivery
- [ ] Message ordering guarantees
- [ ] Pub-sub vs queue patterns

#### Consensus & Coordination (8-10h)
- [ ] Raft algorithm overview (leader election, log replication)
- [ ] ZooKeeper use cases (service discovery, configuration)
- [ ] Leader election patterns
- [ ] Distributed locks

#### CAP Theorem (3-4h)
- [ ] Consistency, Availability, Partition Tolerance
- [ ] CP vs AP systems with examples
- [ ] Quorum reads/writes (W + R > N)

### TIER 2: Asked in 50-70% Interviews (Should Know)
**Time: ~35-45 hours**

#### NoSQL Databases (15-18h)
- [ ] DynamoDB (partition keys, GSI/LSI, eventual consistency)
- [ ] Cassandra (wide-column, tunable consistency)
- [ ] MongoDB (document model, sharding)
- [ ] When to use each NoSQL type

#### Advanced Data Structures (8-10h)
- [ ] LSM trees (write-optimized, compaction)
- [ ] SSTables (sorted string tables)
- [ ] Bloom filters (space-efficient membership testing)
- [ ] Merkle trees (anti-entropy in replication)

#### Distributed Transactions (5-6h)
- [ ] Two-phase commit (2PC) protocol
- [ ] Saga pattern for long transactions
- [ ] Compensating transactions

#### Real-time Communication (3h)
- [ ] WebSocket vs polling vs long-polling
- [ ] Server-sent events (SSE)

#### Networking (2h)
- [ ] TCP vs UDP (reliability vs speed)
- [ ] HTTP/HTTPS basics

#### Observability (4-6h)
- [ ] Metrics, logs, traces
- [ ] Distributed tracing concepts
- [ ] Sampling strategies

### TIER 3: Asked in 20-40% Interviews (Nice to Have)
**Time: ~25-30 hours**

#### Stream Processing (8-10h)
- [ ] Batch vs stream processing
- [ ] Event time vs processing time
- [ ] Windowing (tumbling, sliding, session)
- [ ] Stateful stream processing

#### Advanced Databases (10-12h)
- [ ] BigTable/HBase (wide-column, tablets)
- [ ] Google Spanner (global consistency, TrueTime)
- [ ] Aurora (storage/compute separation)

#### Specialized Systems (7-8h)
- [ ] Time series databases (downsampling, compression)
- [ ] Graph databases (property graphs, Cypher)
- [ ] Full-text search (inverted indexes, ranking)

---

## Must Practice Design Problems

### Week 1-4 (Fundamentals)
1. [ ] URL Shortener (partitioning, caching)
2. [ ] Pastebin (object storage, expiration)
3. [ ] Rate Limiter (algorithms, distributed)
4. [ ] Key-Value Store (consistent hashing, replication)

### Week 5-8 (NoSQL & Advanced)
5. [ ] Twitter Feed (fan-out, caching, NoSQL)
6. [ ] Instagram (photo storage, CDN, sharding)
7. [ ] Uber (geospatial, real-time matching)
8. [ ] WhatsApp (message queue, delivery guarantees)
9. [ ] E-commerce Cart (transactions, consistency)
10. [ ] Payment System (2PC, saga, idempotency)

### Week 9-12 (Specialized & Practice)
11. [ ] Netflix (video streaming, CDN)
12. [ ] YouTube (video processing, recommendation)
13. [ ] Search Autocomplete (trie, caching)
14. [ ] Web Crawler (distributed, deduplication)
15. [ ] Notification System (fan-out, push/pull)
16. [ ] Analytics System (batch processing, data warehouse)
17. [ ] Recommendation Engine (collaborative filtering)
18. [ ] Distributed Cache (Redis/Memcached implementation)
19. [ ] API Gateway (rate limiting, auth, routing)
20. [ ] Real-time Leaderboard (sorted sets, updates)

---

## Study Resources by Week

### Weeks 1-4: Fundamentals
**Books:**
- Designing Data-Intensive Applications (Chapters 1-9)
- System Design Interview by Alex Xu (Volume 1)

**Videos:**
- Gaurav Sen System Design Playlist
- ByteByteGo YouTube Channel

**Papers:**
- CAP Theorem (Brewer)
- Dynamo (Amazon) - for eventual consistency concepts

### Weeks 5-8: NoSQL & Advanced
**Papers:**
- Dynamo: Amazon's Highly Available Key-value Store
- Bigtable: A Distributed Storage System for Structured Data
- Cassandra: A Decentralized Structured Storage System

**Documentation:**
- DynamoDB Developer Guide
- MongoDB Architecture Guide
- Cassandra Documentation

### Weeks 9-12: Specialized & Practice
**Papers:**
- Kafka: A Distributed Messaging System (LinkedIn)
- Dapper: Google's Distributed Tracing
- Spanner: Google's Globally Distributed Database

**Practice Platforms:**
- Grokking the System Design Interview (educative.io)
- System Design Primer (GitHub)
- Mock interviews on Pramp or interviewing.io

---

## Quick Reference: Must-Know Technologies

| Category | Technology | Why Important | Interview Frequency |
|----------|-----------|---------------|---------------------|
| Cache | Redis | In-memory store, common in all designs | 95% |
| Message Queue | Kafka | Streaming, event-driven architectures | 80% |
| NoSQL | DynamoDB | AWS, key-value store | 75% |
| NoSQL | Cassandra | Wide-column, high availability | 60% |
| NoSQL | MongoDB | Document store, flexibility | 65% |
| Coordination | ZooKeeper | Service discovery, config mgmt | 50% |
| Search | Elasticsearch | Full-text search, logs | 55% |
| Database | PostgreSQL/MySQL | Relational baseline | 90% |
| Storage | S3 | Object storage, blob storage | 85% |
| CDN | CloudFront/Akamai | Content delivery | 70% |
| Load Balancer | NGINX/HAProxy | Traffic distribution | 90% |

---

## Interview Preparation Checklist

### Technical Mastery
- [ ] Can explain CAP theorem with 3 real examples
- [ ] Can draw replication architectures (single/multi/leaderless)
- [ ] Can explain consistent hashing with diagrams
- [ ] Can describe 5 cache eviction policies
- [ ] Can explain Kafka architecture in 5 minutes
- [ ] Can compare 3 NoSQL databases (use cases)
- [ ] Can explain 2PC with failure scenarios
- [ ] Can design a URL shortener in 20 minutes
- [ ] Can design Twitter in 45 minutes
- [ ] Can design Uber in 45 minutes

### Common Interview Patterns
- [ ] Read-heavy systems: Caching strategy
- [ ] Write-heavy systems: Message queues, async processing
- [ ] Global systems: Multi-region replication, CDN
- [ ] Real-time systems: WebSocket, stream processing
- [ ] Consistency critical: Strong consistency, transactions
- [ ] High availability: Replication, failover
- [ ] Cost optimization: Storage tiering, compression

### Calculation Skills
- [ ] QPS calculation (queries per second)
- [ ] Storage estimation (data size over time)
- [ ] Bandwidth calculation (upload/download)
- [ ] Memory estimation (cache size)
- [ ] Number of servers needed
- [ ] Database sharding (number of shards)

### Common Tradeoffs to Discuss
- [ ] Consistency vs Availability
- [ ] Latency vs Throughput
- [ ] SQL vs NoSQL
- [ ] Normalization vs Denormalization
- [ ] Vertical vs Horizontal Scaling
- [ ] Batch vs Stream Processing
- [ ] Push vs Pull
- [ ] Sync vs Async

---

## Success Metrics

### After Week 4:
✅ Can solve 5+ fundamental design problems
✅ Understand database, caching, replication basics
✅ Can explain partitioning and load balancing

### After Week 8:
✅ Can solve 10+ design problems
✅ Know when to use different NoSQL databases
✅ Understand distributed transactions

### After Week 12:
✅ Can solve 15+ design problems confidently
✅ Can handle follow-up questions
✅ Can discuss tradeoffs fluently
✅ Ready for FAANG interviews

---

## Emergency: If Running Out of Time

### Absolute Minimum (40-50 hours, 4-5 weeks)
**Week 1:** Databases, caching, replication (12h)
**Week 2:** Partitioning, load balancing, message queues (12h)
**Week 3:** NoSQL basics (DynamoDB), practice 5 problems (12h)
**Week 4:** Practice 10 more problems, mock interviews (12h)

**Topics to Skip Temporarily:**
- Advanced consensus (Raft details)
- Stream processing (Flink, advanced Kafka)
- Specialized databases (time series, graph)
- Advanced systems (Spanner, Aurora)
- Batch processing (MapReduce, Spark)

**Focus Only On:**
- Core database concepts
- Caching strategies
- Basic replication
- Partitioning/sharding
- Load balancing
- One message queue (Kafka basics)
- One NoSQL (DynamoDB)
- Practice, practice, practice

---

## Weekly Progress Tracker

| Week | Planned Hours | Actual Hours | Topics Completed | Problems Solved | Notes |
|------|---------------|--------------|------------------|-----------------|-------|
| 1 | 10-12h | | /4 | /2 | |
| 2 | 10-12h | | /4 | /2 | |
| 3 | 10-12h | | /4 | /2 | |
| 4 | 10-12h | | /4 | /2 | |
| 5 | 10-12h | | /3 | /2 | |
| 6 | 10-12h | | /3 | /2 | |
| 7 | 10-12h | | /3 | /2 | |
| 8 | 10-12h | | /3 | /2 | |
| 9 | 10-12h | | /3 | /2 | |
| 10 | 10-12h | | /3 | /2 | |
| 11 | 10-12h | | /3 | /2 | |
| 12 | 10-12h | | /3 | /4 | |

**Total:** 120-144 hours over 12 weeks

---

**Start Date:** _____________
**Target Interview Date:** _____________
**Current Week:** _____________

**Next Action:** Block calendar for Week 1, start with SQL vs NoSQL review!
