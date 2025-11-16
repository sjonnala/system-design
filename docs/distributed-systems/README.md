# Distributed Systems

Critical concepts for understanding how large-scale systems work in practice.

## Contents

### Foundational Concepts

1. [CAP Theorem](cap-theorem.md) - Understanding the fundamental trade-offs in distributed systems
2. [Consensus Algorithms](consensus.md) - Paxos, Raft, and how distributed systems agree
3. [Replication Strategies](replication.md) - Keeping data synchronized across nodes
4. [Partitioning & Sharding](partitioning.md) - Dividing data across multiple machines
5. [Distributed Transactions](transactions.md) - Maintaining ACID in distributed environments
6. [Clock Synchronization](clocks.md) - Time in distributed systems
7. [Failure Detection](failure-detection.md) - Identifying and handling node failures

### Platform Engineering & Infrastructure

8. [Service Discovery & Service Mesh](service-discovery.md) - How services find and communicate with each other
9. [Load Balancing](load-balancing.md) - Distributing traffic across multiple servers
10. [Circuit Breakers & Resilience Patterns](resilience-patterns.md) - Building fault-tolerant systems
11. [Message Queues & Event Streaming](message-queues.md) - Asynchronous communication patterns
12. [Distributed Caching](caching.md) - Performance optimization at scale

## Why This Matters for Senior Roles & System Design

Senior engineers and architects must:
- **Understand distributed systems theory** - CAP theorem, consensus, consistency models
- **Design scalable architectures** - Load balancing, caching, partitioning strategies
- **Build resilient systems** - Circuit breakers, retries, bulkheads, graceful degradation
- **Make informed trade-offs** - Consistency vs availability, latency vs throughput
- **Debug production issues** - Clock skew, network partitions, cascading failures
- **Architect microservices** - Service discovery, message queues, event streaming

This section provides the deep technical knowledge expected at senior levels and critical for acing system design interviews.

## Study Approach

### For Interview Preparation

**Phase 1 - Foundations** (1-2 weeks):
1. **CAP Theorem** - Understand fundamental trade-offs
2. **Replication Strategies** - How data stays synchronized
3. **Partitioning & Sharding** - Dividing data for scale
4. **Consensus Algorithms** - How distributed systems agree

**Phase 2 - Platform Engineering** (2-3 weeks):
5. **Load Balancing** - Traffic distribution algorithms
6. **Distributed Caching** - Performance at scale
7. **Service Discovery** - How services find each other
8. **Message Queues** - Asynchronous communication

**Phase 3 - Resilience & Advanced Topics** (1-2 weeks):
9. **Circuit Breakers & Resilience** - Fault tolerance patterns
10. **Distributed Transactions** - ACID in distributed environments
11. **Clock Synchronization** - Time and ordering
12. **Failure Detection** - Identifying and handling failures

### For On-the-Job Learning

Focus on topics relevant to your current work:
- **Building APIs**: Load balancing, caching, resilience patterns
- **Microservices**: Service discovery, message queues, distributed tracing
- **Data Platform**: Replication, partitioning, transactions
- **SRE/DevOps**: Failure detection, health checks, service mesh

## Real-World Applications

### Foundational Patterns
- **CAP Theorem**: Designing for consistency (banking) vs availability (social media)
- **Consensus**: Leader election in Kafka, etcd, ZooKeeper
- **Replication**: Database replication in PostgreSQL, MySQL, MongoDB
- **Partitioning**: Data sharding in DynamoDB, Cassandra, MongoDB
- **Transactions**: Distributed transactions in Google Spanner, CockroachDB
- **Clocks**: Ordering events in distributed logs, conflict resolution
- **Failure Detection**: Health checks in Kubernetes, service mesh

### Platform Engineering Patterns
- **Service Discovery**: Consul, Kubernetes service discovery, Istio service mesh
- **Load Balancing**: NGINX, HAProxy, AWS ALB/NLB, Layer 4 vs Layer 7
- **Circuit Breakers**: Netflix Hystrix, Resilience4j, preventing cascading failures
- **Message Queues**: RabbitMQ for tasks, Kafka for event streaming, SQS/SNS
- **Caching**: Redis for distributed cache, Memcached, CDN for static assets

## Key Topics by Use Case

### E-Commerce Platform
- Load balancing (traffic distribution)
- Caching (product catalog, session data)
- Message queues (order processing, notifications)
- Circuit breakers (payment gateway resilience)
- Partitioning (user data, product catalog)

### Social Media Platform
- CAP theorem (choose availability)
- Replication (eventual consistency)
- Caching (feed, user profiles)
- Message queues (timeline fanout)
- Service mesh (microservices communication)

### Financial System
- CAP theorem (choose consistency)
- Distributed transactions (ACID guarantees)
- Consensus (leader election)
- Replication (synchronous)
- Failure detection (fast failover)

### Streaming Platform
- Partitioning (user data, content)
- Caching (CDN, metadata)
- Load balancing (video streaming)
- Service discovery (microservices)
- Message queues (analytics, recommendations)

## Interview Preparation Tips

**System Design Questions - Common Topics**:
1. **Scalability**: Partitioning, load balancing, caching
2. **Reliability**: Replication, failure detection, resilience patterns
3. **Performance**: Caching strategies, CDN, load balancing
4. **Consistency**: CAP theorem, consensus, distributed transactions
5. **Communication**: Message queues, service discovery, API gateway

**Study Method**:
1. Read each guide thoroughly
2. Draw diagrams for each concept
3. Implement simple versions (Redis, message queue)
4. Practice explaining to others
5. Apply to real system design problems

**Practice Questions**:
- "Design Instagram" → Partitioning, caching, message queues, CAP
- "Design Uber" → Service discovery, distributed transactions, real-time messaging
- "Design Netflix" → CDN, caching, load balancing, resilience patterns
- "Design WhatsApp" → Message queues, partitioning, replication
- "Design a URL shortener" → Partitioning, caching, load balancing
