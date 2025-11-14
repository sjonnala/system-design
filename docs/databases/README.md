# Databases & Storage

Understanding data storage is fundamental to system design.

## Contents

1. [SQL vs NoSQL](sql-vs-nosql.md) - Choosing the right database type
2. [Database Scaling](scaling.md) - Replication, sharding, read replicas
3. [Indexing Strategies](indexing.md) - Optimizing query performance
4. [ACID vs BASE](acid-base.md) - Transaction models
5. [Data Modeling](data-modeling.md) - Schema design patterns
6. [Object Storage](object-storage.md) - S3, blob storage, and use cases

## Database Types Overview

**Relational (SQL)**: PostgreSQL, MySQL, Oracle
- Structured data with relationships
- ACID transactions
- Complex queries with JOINs

**Document (NoSQL)**: MongoDB, CouchDB
- Flexible schema
- JSON-like documents
- Good for evolving data models

**Key-Value (NoSQL)**: Redis, DynamoDB
- Simple get/put operations
- High performance
- Caching, session storage

**Wide-Column (NoSQL)**: Cassandra, HBase
- Time-series data
- High write throughput
- Scalable

**Graph (NoSQL)**: Neo4j, Amazon Neptune
- Relationship-heavy data
- Social networks
- Recommendation engines

## Common Interview Topics

- When to use SQL vs NoSQL
- Database sharding strategies
- Index design and trade-offs
- Transaction isolation levels
- CAP theorem in databases
