# Distributed Systems

Critical concepts for understanding how large-scale systems work in practice.

## Contents

1. [Consensus Algorithms](consensus.md) - Paxos, Raft, and how distributed systems agree
2. [Replication Strategies](replication.md) - Keeping data synchronized across nodes
3. [Partitioning & Sharding](partitioning.md) - Dividing data across multiple machines
4. [Distributed Transactions](transactions.md) - Maintaining ACID in distributed environments
5. [Clock Synchronization](clocks.md) - Time in distributed systems
6. [Failure Detection](failure-detection.md) - Identifying and handling node failures

## Why This Matters for Senior Roles

Senior systems engineers must:
- Understand distributed systems theory
- Apply these concepts to real-world problems
- Make trade-offs between consistency, availability, and performance
- Debug issues in production distributed systems

This section provides the deep technical knowledge expected at senior levels.

## Study Approach

1. Start with consensus and replication - foundational concepts
2. Understand partitioning - critical for scalability
3. Learn about distributed transactions - complexity and trade-offs
4. Study clocks and failure detection - practical operational concerns

## Real-World Applications

- **Consensus**: Leader election in Kafka, etcd, ZooKeeper
- **Replication**: Database replication in PostgreSQL, MySQL, MongoDB
- **Partitioning**: Data sharding in DynamoDB, Cassandra, MongoDB
- **Transactions**: Distributed transactions in Google Spanner, CockroachDB
- **Clocks**: Ordering events in distributed logs, conflict resolution
- **Failure Detection**: Health checks in Kubernetes, service mesh
