# 🗄️ LinkedIn Connections: Complete Data Model

## Graph Database & Relational Schema Design

This document provides comprehensive coverage of data modeling for a LinkedIn Connections system, including graph database schemas (Neo4j), relational database schemas (PostgreSQL), and their integration patterns.

---

## TABLE OF CONTENTS

1. [Graph Database Schema (Neo4j)](#1-graph-database-schema-neo4j)
2. [Relational Database Schema (PostgreSQL)](#2-relational-database-schema-postgresql)
3. [Dual-Write Pattern](#3-dual-write-pattern)
4. [Query Patterns](#4-query-patterns)
5. [Indexing Strategy](#5-indexing-strategy)
6. [Data Consistency](#6-data-consistency)
7. [Migration Strategy](#7-migration-strategy)
8. [Real-World Examples](#8-real-world-examples)

---

## 1. GRAPH DATABASE SCHEMA (Neo4j)

### Why Graph Database for Connections?

```
Traditional RDBMS Challenge:
  SELECT * FROM connections c1
  JOIN connections c2 ON c1.user_id_2 = c2.user_id_1
  JOIN connections c3 ON c2.user_id_2 = c3.user_id_1
  WHERE c1.user_id_1 = ?
  
  → Multiple self-joins (slow!)
  → Exponential complexity for deeper traversal
  → 3+ degrees = too expensive

Graph Database Solution:
  MATCH (user)-[:CONNECTED_TO*1..3]-(connection)
  WHERE user.userId = $uid
  RETURN connection
  
  → Native graph traversal (fast!)
  → Linear complexity
  → Optimized for relationships
```

---

### Node Types

#### 1. User Node

```cypher
// User Node Schema
(:User {
  userId: BIGINT,           // Unique identifier
  email: STRING,            // Unique email (encrypted)
  name: STRING,             // Full name
  headline: STRING,         // Professional headline
  profilePictureUrl: STRING,// S3 URL
  location: STRING,         // "San Francisco, CA"
  industry: STRING,         // "Technology"
  connectionCount: INTEGER, // Denormalized for performance
  createdAt: TIMESTAMP,
  updatedAt: TIMESTAMP,
  isActive: BOOLEAN,
  privacyLevel: STRING      // PUBLIC, CONNECTIONS_ONLY, PRIVATE
})

// Indexes
CREATE INDEX user_id_index FOR (u:User) ON (u.userId);
CREATE INDEX user_email_index FOR (u:User) ON (u.email);
CREATE CONSTRAINT user_id_unique FOR (u:User) REQUIRE u.userId IS UNIQUE;

// Example
CREATE (u:User {
  userId: 123456,
  email: "john.doe@example.com",
  name: "John Doe",
  headline: "Senior Software Engineer at Google",
  profilePictureUrl: "https://s3.amazonaws.com/profiles/123456.jpg",
  location: "San Francisco, CA",
  industry: "Technology",
  connectionCount: 542,
  createdAt: datetime(),
  updatedAt: datetime(),
  isActive: true,
  privacyLevel: "PUBLIC"
})
```

#### 2. Company Node

```cypher
// Company Node Schema
(:Company {
  companyId: BIGINT,
  name: STRING,
  industry: STRING,
  size: STRING,        // "1-10", "11-50", "51-200", etc.
  website: STRING,
  logoUrl: STRING,
  description: TEXT,
  foundedYear: INTEGER
})

// Indexes
CREATE INDEX company_id_index FOR (c:Company) ON (c.companyId);
CREATE INDEX company_name_index FOR (c:Company) ON (c.name);

// Example
CREATE (c:Company {
  companyId: 1001,
  name: "Google",
  industry: "Technology",
  size: "10001+",
  website: "https://google.com",
  logoUrl: "https://s3.amazonaws.com/logos/google.png",
  description: "Search engine and technology company",
  foundedYear: 1998
})
```

#### 3. School Node

```cypher
// School Node Schema
(:School {
  schoolId: BIGINT,
  name: STRING,
  type: STRING,        // "University", "College", "High School"
  location: STRING,
  website: STRING,
  logoUrl: STRING
})

// Indexes
CREATE INDEX school_id_index FOR (s:School) ON (s.schoolId);
CREATE INDEX school_name_index FOR (s:School) ON (s.name);

// Example
CREATE (s:School {
  schoolId: 2001,
  name: "Stanford University",
  type: "University",
  location: "Stanford, CA",
  website: "https://stanford.edu",
  logoUrl: "https://s3.amazonaws.com/logos/stanford.png"
})
```

---

### Relationship Types

#### 1. CONNECTED_TO (Bidirectional Connection)

```cypher
// Connection Relationship Schema
(user1:User)-[:CONNECTED_TO {
  connectedAt: TIMESTAMP,
  initiatedBy: BIGINT,     // Who sent the request
  connectionStrength: FLOAT // 0.0-1.0 (ML-derived)
}]->(user2:User)

// Important: Create BOTH directions for fast lookups
CREATE (user1)-[:CONNECTED_TO {
  connectedAt: datetime(),
  initiatedBy: 123456,
  connectionStrength: 0.85
}]->(user2)

CREATE (user2)-[:CONNECTED_TO {
  connectedAt: datetime(),
  initiatedBy: 123456,
  connectionStrength: 0.85
}]->(user1)

// Query: Get all connections (very fast!)
MATCH (user:User {userId: $uid})-[:CONNECTED_TO]->(connection)
RETURN connection
```

**Why Bidirectional?**
- Fast queries in both directions
- No need to check both (u1→u2) and (u2→u1)
- Trade-off: 2x storage for 10x query speed

#### 2. REQUESTED (Pending Connection)

```cypher
// Pending connection request
(sender:User)-[:REQUESTED {
  requestedAt: TIMESTAMP,
  message: STRING,       // Optional message
  status: STRING         // "PENDING", "ACCEPTED", "REJECTED"
}]->(recipient:User)

// Example
MATCH (sender:User {userId: 123456}), (recipient:User {userId: 789012})
CREATE (sender)-[:REQUESTED {
  requestedAt: datetime(),
  message: "I'd like to add you to my professional network.",
  status: "PENDING"
}]->(recipient)

// When accepted, convert to CONNECTED_TO
MATCH (sender)-[r:REQUESTED {status: "PENDING"}]->(recipient)
WHERE sender.userId = $senderId AND recipient.userId = $recipientId

// Delete request edge
DELETE r

// Create bidirectional connection
CREATE (sender)-[:CONNECTED_TO {
  connectedAt: datetime(),
  initiatedBy: sender.userId,
  connectionStrength: 0.7
}]->(recipient)

CREATE (recipient)-[:CONNECTED_TO {
  connectedAt: datetime(),
  initiatedBy: sender.userId,
  connectionStrength: 0.7
}]->(sender)

// Update connection counts
SET sender.connectionCount = sender.connectionCount + 1
SET recipient.connectionCount = recipient.connectionCount + 1
```

#### 3. BLOCKED

```cypher
// Block relationship (one-way)
(blocker:User)-[:BLOCKED {
  blockedAt: TIMESTAMP,
  reason: STRING
}]->(blocked:User)

// Example
MATCH (blocker:User {userId: 123456}), (blocked:User {userId: 999999})
CREATE (blocker)-[:BLOCKED {
  blockedAt: datetime(),
  reason: "SPAM"
}]->(blocked)

// Check if blocked before allowing connection request
MATCH (sender:User {userId: $senderId})-[:BLOCKED]-(recipient:User {userId: $recipientId})
RETURN COUNT(*) > 0 AS isBlocked
```

#### 4. WORKS_AT

```cypher
// User employment relationship
(user:User)-[:WORKS_AT {
  startDate: DATE,
  endDate: DATE,         // NULL if current
  title: STRING,
  department: STRING,
  isCurrent: BOOLEAN
}]->(company:Company)

// Example (current job)
MATCH (user:User {userId: 123456}), (company:Company {companyId: 1001})
CREATE (user)-[:WORKS_AT {
  startDate: date('2020-06-01'),
  endDate: null,
  title: "Senior Software Engineer",
  department: "Cloud Infrastructure",
  isCurrent: true
}]->(company)

// Example (past job)
MATCH (user:User {userId: 123456}), (company:Company {companyId: 1002})
CREATE (user)-[:WORKS_AT {
  startDate: date('2018-01-01'),
  endDate: date('2020-05-31'),
  title: "Software Engineer",
  department: "Backend Services",
  isCurrent: false
}]->(company)
```

#### 5. ATTENDED

```cypher
// User education relationship
(user:User)-[:ATTENDED {
  startYear: INTEGER,
  endYear: INTEGER,
  degree: STRING,        // "B.S. Computer Science", "MBA"
  fieldOfStudy: STRING,
  gpa: FLOAT,           // Optional
  activities: [STRING]   // Clubs, sports, etc.
}]->(school:School)

// Example
MATCH (user:User {userId: 123456}), (school:School {schoolId: 2001})
CREATE (user)-[:ATTENDED {
  startYear: 2015,
  endYear: 2019,
  degree: "B.S. Computer Science",
  fieldOfStudy: "Artificial Intelligence",
  gpa: 3.85,
  activities: ["ACM", "Google Developer Student Club"]
}]->(school)
```

---

### Advanced Graph Queries

#### Query 1: Find Mutual Connections

```cypher
// Find mutual connections between two users
MATCH (user1:User {userId: $user1Id})-[:CONNECTED_TO]-(mutual)-[:CONNECTED_TO]-(user2:User {userId: $user2Id})
WHERE user1 <> user2 AND mutual <> user1 AND mutual <> user2
RETURN mutual.userId AS mutualUserId,
       mutual.name AS mutualName,
       mutual.headline AS mutualHeadline,
       mutual.profilePictureUrl AS mutualPictureUrl
ORDER BY mutual.name
LIMIT 50

// Performance: ~20-50ms for users with 500 connections
```

#### Query 2: Calculate Degrees of Separation

```cypher
// Find shortest path between two users (max 3 degrees)
MATCH path = shortestPath(
  (user1:User {userId: $user1Id})-[:CONNECTED_TO*1..3]-(user2:User {userId: $user2Id})
)
RETURN length(path) AS degree,
       [node IN nodes(path) | {
         userId: node.userId,
         name: node.name
       }] AS connectionPath

// Example Result:
// degree: 2
// path: [
//   {userId: 123456, name: "John Doe"},
//   {userId: 456789, name: "Jane Smith"}, // mutual connection
//   {userId: 789012, name: "Bob Johnson"}
// ]
```

#### Query 3: "People You May Know" Candidates

```cypher
// Find 2nd degree connections (friends of friends)
// Ranked by number of mutual connections
MATCH (user:User {userId: $userId})-[:CONNECTED_TO]->(friend)-[:CONNECTED_TO]->(candidate)
WHERE NOT (user)-[:CONNECTED_TO]-(candidate)  // Not already connected
  AND NOT (user)-[:REQUESTED]-(candidate)     // No pending request
  AND NOT (user)-[:BLOCKED]-(candidate)       // Not blocked
  AND user <> candidate
WITH candidate, COUNT(DISTINCT friend) AS mutualCount,
     COLLECT(DISTINCT friend.name)[0..5] AS sampleMutuals
ORDER BY mutualCount DESC
LIMIT 100
RETURN candidate.userId,
       candidate.name,
       candidate.headline,
       candidate.profilePictureUrl,
       mutualCount,
       sampleMutuals

// Performance: ~50-200ms (heavily cached)
```

#### Query 4: Find Shared Companies

```cypher
// Find users who worked at the same company
MATCH (user:User {userId: $userId})-[:WORKS_AT]->(company:Company)<-[:WORKS_AT]-(colleague)
WHERE NOT (user)-[:CONNECTED_TO]-(colleague)
  AND user <> colleague
RETURN colleague.userId,
       colleague.name,
       colleague.headline,
       company.name AS sharedCompany,
       colleague.connectionCount
ORDER BY colleague.connectionCount DESC
LIMIT 20
```

#### Query 5: Find Shared Schools

```cypher
// Find users who attended the same school
MATCH (user:User {userId: $userId})-[:ATTENDED]->(school:School)<-[:ATTENDED]-(classmate)
WHERE NOT (user)-[:CONNECTED_TO]-(classmate)
  AND user <> classmate
RETURN classmate.userId,
       classmate.name,
       classmate.headline,
       school.name AS sharedSchool,
       classmate.connectionCount
ORDER BY classmate.connectionCount DESC
LIMIT 20
```

---

## 2. RELATIONAL DATABASE SCHEMA (PostgreSQL)

### Why PostgreSQL Too?

```
Graph DB (Neo4j):        Relational DB (PostgreSQL):
✓ Relationship queries    ✓ Transactional integrity
✓ Graph traversal         ✓ Complex joins
✓ Pattern matching        ✓ ACID guarantees
✗ Complex transactions    ✗ Slow graph queries
✗ Reporting/analytics     ✓ Mature tooling

Solution: Use BOTH
- Neo4j: Social graph queries
- PostgreSQL: User data, transactions, metadata
- Redis: Cache layer
```

---

### Table Schemas

#### 1. users Table

```sql
-- Users table (master data)
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,  -- bcrypt hash
    name VARCHAR(255) NOT NULL,
    headline TEXT,
    profile_picture_url TEXT,
    location VARCHAR(255),
    industry VARCHAR(100),
    connection_count INTEGER DEFAULT 0,   -- Denormalized
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    is_email_verified BOOLEAN DEFAULT FALSE,
    privacy_level VARCHAR(50) DEFAULT 'PUBLIC',
    
    -- Search optimization
    search_vector TSVECTOR,  -- Full-text search
    
    CONSTRAINT check_privacy_level CHECK (
        privacy_level IN ('PUBLIC', 'CONNECTIONS_ONLY', 'PRIVATE')
    )
);

-- Indexes
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_name ON users(name);
CREATE INDEX idx_users_industry ON users(industry);
CREATE INDEX idx_users_location ON users(location);
CREATE INDEX idx_users_created_at ON users(created_at);
CREATE INDEX idx_users_connection_count ON users(connection_count);

-- Full-text search index
CREATE INDEX idx_users_search_vector ON users USING GIN(search_vector);

-- Trigger to update search_vector
CREATE OR REPLACE FUNCTION users_search_vector_update() RETURNS trigger AS $$
BEGIN
    NEW.search_vector :=
        setweight(to_tsvector('english', COALESCE(NEW.name, '')), 'A') ||
        setweight(to_tsvector('english', COALESCE(NEW.headline, '')), 'B') ||
        setweight(to_tsvector('english', COALESCE(NEW.location, '')), 'C');
    RETURN NEW;
END
$$ LANGUAGE plpgsql;

CREATE TRIGGER users_search_vector_trigger
BEFORE INSERT OR UPDATE ON users
FOR EACH ROW EXECUTE FUNCTION users_search_vector_update();

-- Partitioning (for large scale)
CREATE TABLE users_0 PARTITION OF users FOR VALUES FROM (0) TO (100000000);
CREATE TABLE users_1 PARTITION OF users FOR VALUES FROM (100000000) TO (200000000);
-- ... 8 partitions total (100M users each)
```

#### 2. connection_requests Table

```sql
-- Connection requests (pending, accepted, rejected)
CREATE TABLE connection_requests (
    request_id BIGSERIAL PRIMARY KEY,
    from_user_id BIGINT NOT NULL,
    to_user_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    message TEXT,
    requested_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    
    FOREIGN KEY (from_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (to_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    -- Prevent duplicate requests
    UNIQUE (from_user_id, to_user_id),
    
    CONSTRAINT check_status CHECK (
        status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'WITHDRAWN')
    ),
    
    -- Prevent self-connections
    CONSTRAINT check_not_self CHECK (from_user_id <> to_user_id)
);

-- Indexes
CREATE INDEX idx_connection_requests_from_user ON connection_requests(from_user_id);
CREATE INDEX idx_connection_requests_to_user ON connection_requests(to_user_id);
CREATE INDEX idx_connection_requests_status ON connection_requests(status);
CREATE INDEX idx_connection_requests_to_user_status ON connection_requests(to_user_id, status);
CREATE INDEX idx_connection_requests_requested_at ON connection_requests(requested_at);

-- Composite index for common queries
CREATE INDEX idx_connection_requests_pending ON connection_requests(to_user_id, status)
WHERE status = 'PENDING';
```

#### 3. connections Table

```sql
-- Accepted connections (denormalized for fast queries)
CREATE TABLE connections (
    connection_id BIGSERIAL PRIMARY KEY,
    user_id_1 BIGINT NOT NULL,  -- Always smaller user_id
    user_id_2 BIGINT NOT NULL,  -- Always larger user_id
    connected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    initiated_by BIGINT NOT NULL,  -- Who sent the request
    connection_strength FLOAT DEFAULT 0.5,  -- ML-derived (0.0-1.0)
    
    FOREIGN KEY (user_id_1) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (user_id_2) REFERENCES users(user_id) ON DELETE CASCADE,
    
    -- Ensure canonical ordering (smaller ID first)
    CONSTRAINT check_canonical_order CHECK (user_id_1 < user_id_2),
    
    -- Prevent duplicate connections
    UNIQUE (user_id_1, user_id_2)
);

-- Indexes (critical for performance!)
CREATE INDEX idx_connections_user1 ON connections(user_id_1);
CREATE INDEX idx_connections_user2 ON connections(user_id_2);
CREATE INDEX idx_connections_both ON connections(user_id_1, user_id_2);
CREATE INDEX idx_connections_connected_at ON connections(connected_at);

-- Trigger to maintain connection_count
CREATE OR REPLACE FUNCTION update_connection_count() RETURNS trigger AS $$
BEGIN
    IF TG_OP = 'INSERT' THEN
        UPDATE users SET connection_count = connection_count + 1
        WHERE user_id IN (NEW.user_id_1, NEW.user_id_2);
    ELSIF TG_OP = 'DELETE' THEN
        UPDATE users SET connection_count = connection_count - 1
        WHERE user_id IN (OLD.user_id_1, OLD.user_id_2);
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER connection_count_trigger
AFTER INSERT OR DELETE ON connections
FOR EACH ROW EXECUTE FUNCTION update_connection_count();
```

#### 4. user_activities Table (ML Features)

```sql
-- User activities for ML feature engineering
CREATE TABLE user_activities (
    activity_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    target_user_id BIGINT,  -- For profile views, connection requests
    metadata JSONB,         -- Flexible schema for different activity types
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    CONSTRAINT check_activity_type CHECK (
        activity_type IN (
            'PROFILE_VIEW', 'SEARCH', 'CONNECTION_REQUEST_SENT',
            'CONNECTION_ACCEPTED', 'RECOMMENDATION_SHOWN',
            'RECOMMENDATION_CLICKED', 'MESSAGE_SENT'
        )
    )
);

-- Indexes
CREATE INDEX idx_user_activities_user ON user_activities(user_id);
CREATE INDEX idx_user_activities_type ON user_activities(activity_type);
CREATE INDEX idx_user_activities_user_created ON user_activities(user_id, created_at DESC);
CREATE INDEX idx_user_activities_metadata ON user_activities USING GIN(metadata);

-- Partitioning by month (time-series data)
CREATE TABLE user_activities (
    activity_id BIGSERIAL,
    user_id BIGINT NOT NULL,
    activity_type VARCHAR(50) NOT NULL,
    target_user_id BIGINT,
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (activity_id, created_at)
) PARTITION BY RANGE (created_at);

CREATE TABLE user_activities_2025_11 PARTITION OF user_activities
FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');
-- Create partitions for each month
```

#### 5. blocked_users Table

```sql
-- Blocked users
CREATE TABLE blocked_users (
    block_id BIGSERIAL PRIMARY KEY,
    blocker_user_id BIGINT NOT NULL,
    blocked_user_id BIGINT NOT NULL,
    blocked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    reason VARCHAR(50),
    
    FOREIGN KEY (blocker_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (blocked_user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    
    UNIQUE (blocker_user_id, blocked_user_id),
    
    CONSTRAINT check_not_self_block CHECK (blocker_user_id <> blocked_user_id),
    CONSTRAINT check_block_reason CHECK (
        reason IN ('SPAM', 'HARASSMENT', 'INAPPROPRIATE', 'OTHER')
    )
);

-- Indexes
CREATE INDEX idx_blocked_users_blocker ON blocked_users(blocker_user_id);
CREATE INDEX idx_blocked_users_blocked ON blocked_users(blocked_user_id);
```

#### 6. companies Table

```sql
-- Companies (for employment history)
CREATE TABLE companies (
    company_id BIGSERIAL PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    industry VARCHAR(100),
    size VARCHAR(50),  -- "1-10", "11-50", "51-200", etc.
    website VARCHAR(255),
    logo_url TEXT,
    description TEXT,
    founded_year INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT check_size CHECK (
        size IN ('1-10', '11-50', '51-200', '201-500', '501-1000', 
                 '1001-5000', '5001-10000', '10001+')
    )
);

CREATE INDEX idx_companies_name ON companies(name);
CREATE INDEX idx_companies_industry ON companies(industry);
```

#### 7. user_employment Table

```sql
-- User employment history
CREATE TABLE user_employment (
    employment_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    company_id BIGINT NOT NULL,
    title VARCHAR(255) NOT NULL,
    department VARCHAR(255),
    start_date DATE NOT NULL,
    end_date DATE,  -- NULL if current
    is_current BOOLEAN DEFAULT FALSE,
    description TEXT,
    
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    FOREIGN KEY (company_id) REFERENCES companies(company_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_employment_user ON user_employment(user_id);
CREATE INDEX idx_user_employment_company ON user_employment(company_id);
CREATE INDEX idx_user_employment_current ON user_employment(user_id, is_current)
WHERE is_current = TRUE;
```

---

## 3. DUAL-WRITE PATTERN

### Challenge: Keeping Neo4j and PostgreSQL in Sync

```
Write Operation (Connection Accepted):
1. Update PostgreSQL (transactional)
2. Update Neo4j (graph)
3. Publish Kafka event (async)

Problem: What if step 2 fails?
- PostgreSQL has connection
- Neo4j doesn't
- Data inconsistency!

Solutions:
a) Two-Phase Commit (2PC) - too slow
b) Saga Pattern - complex
c) Event Sourcing - eventual consistency (recommended)
```

### Event Sourcing Approach

```java
@Service
@Transactional
public class ConnectionService {
    
    @Autowired
    private ConnectionRepository connectionRepo;  // PostgreSQL
    
    @Autowired
    private Neo4jTemplate neo4jTemplate;
    
    @Autowired
    private KafkaTemplate<String, ConnectionEvent> kafkaTemplate;
    
    public ConnectionResult acceptConnection(Long requestId, Long userId) {
        // 1. Update PostgreSQL (ACID transaction)
        ConnectionRequest request = connectionRepo.findById(requestId)
            .orElseThrow(() -> new NotFoundException("Request not found"));
        
        request.setStatus("ACCEPTED");
        request.setRespondedAt(Instant.now());
        connectionRepo.save(request);
        
        // Create connection record
        Connection connection = new Connection();
        connection.setUserId1(Math.min(request.getFromUserId(), userId));
        connection.setUserId2(Math.max(request.getFromUserId(), userId));
        connection.setConnectedAt(Instant.now());
        connection.setInitiatedBy(request.getFromUserId());
        connectionRepo.save(connection);
        
        // 2. Publish event (Kafka guarantees delivery)
        ConnectionEvent event = ConnectionEvent.builder()
            .eventType("connection.accepted")
            .fromUserId(request.getFromUserId())
            .toUserId(userId)
            .timestamp(Instant.now())
            .build();
        
        kafkaTemplate.send("connection.accepted", event);
        
        // 3. Async worker consumes event and updates Neo4j
        // If Neo4j update fails, event is retried (eventual consistency)
        
        return ConnectionResult.success(connection);
    }
}
```

**Async Worker (Neo4j Update)**:
```java
@Service
public class GraphSyncWorker {
    
    @KafkaListener(topics = "connection.accepted")
    public void handleConnectionAccepted(ConnectionEvent event) {
        try {
            // Update Neo4j (create bidirectional edges)
            neo4jTemplate.execute("""
                MATCH (user1:User {userId: $user1}), (user2:User {userId: $user2})
                
                // Delete request edge
                OPTIONAL MATCH (user1)-[r:REQUESTED]->(user2)
                DELETE r
                
                // Create bidirectional connection
                CREATE (user1)-[:CONNECTED_TO {
                    connectedAt: datetime($timestamp),
                    initiatedBy: $initiatedBy,
                    connectionStrength: 0.7
                }]->(user2)
                
                CREATE (user2)-[:CONNECTED_TO {
                    connectedAt: datetime($timestamp),
                    initiatedBy: $initiatedBy,
                    connectionStrength: 0.7
                }]->(user1)
                
                // Update counts
                SET user1.connectionCount = user1.connectionCount + 1
                SET user2.connectionCount = user2.connectionCount + 1
            """, Map.of(
                "user1", event.getFromUserId(),
                "user2", event.getToUserId(),
                "timestamp", event.getTimestamp(),
                "initiatedBy", event.getFromUserId()
            ));
            
            log.info("Neo4j updated successfully for connection: {} -> {}",
                event.getFromUserId(), event.getToUserId());
        } catch (Exception e) {
            log.error("Failed to update Neo4j, will retry", e);
            throw e;  // Kafka will retry
        }
    }
}
```

---

## 4. QUERY PATTERNS

### Pattern 1: Get User's Connections (PostgreSQL)

```sql
-- Query connections for a user (fast!)
SELECT 
    u.user_id,
    u.name,
    u.headline,
    u.profile_picture_url,
    c.connected_at,
    u.connection_count
FROM connections c
JOIN users u ON (
    CASE 
        WHEN c.user_id_1 = :userId THEN c.user_id_2
        WHEN c.user_id_2 = :userId THEN c.user_id_1
    END = u.user_id
)
WHERE c.user_id_1 = :userId OR c.user_id_2 = :userId
ORDER BY c.connected_at DESC
LIMIT 50 OFFSET :offset;

-- Performance: <10ms (indexed)
```

### Pattern 2: Get Mutual Connections (PostgreSQL)

```sql
-- Find mutual connections between two users
SELECT DISTINCT
    u.user_id,
    u.name,
    u.headline,
    u.profile_picture_url
FROM connections c1
JOIN connections c2 ON (
    (c1.user_id_1 = c2.user_id_1 OR c1.user_id_1 = c2.user_id_2 OR
     c1.user_id_2 = c2.user_id_1 OR c1.user_id_2 = c2.user_id_2)
    AND c1.connection_id <> c2.connection_id
)
JOIN users u ON (
    u.user_id IN (c1.user_id_1, c1.user_id_2)
    AND u.user_id <> :user1Id
    AND u.user_id <> :user2Id
)
WHERE (c1.user_id_1 = :user1Id OR c1.user_id_2 = :user1Id)
  AND (c2.user_id_1 = :user2Id OR c2.user_id_2 = :user2Id)
LIMIT 50;

-- Performance: ~50-100ms (complex join, use Neo4j for better performance)
```

### Pattern 3: 2nd Degree Connections (Neo4j - Much Faster!)

```cypher
// Find 2nd degree connections
MATCH (user:User {userId: $userId})-[:CONNECTED_TO*2]-(candidate:User)
WHERE NOT (user)-[:CONNECTED_TO]-(candidate)
  AND user <> candidate
RETURN DISTINCT candidate.userId, candidate.name, candidate.headline
LIMIT 100

// Performance: 50-100ms (optimized graph traversal)
```

---

## 5. INDEXING STRATEGY

### PostgreSQL Indexes

```sql
-- B-tree indexes (default, good for equality and range queries)
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_name ON users(name);

-- Composite indexes (for multi-column queries)
CREATE INDEX idx_connection_requests_pending 
ON connection_requests(to_user_id, status)
WHERE status = 'PENDING';

-- Partial indexes (index subset of data)
CREATE INDEX idx_users_active 
ON users(user_id)
WHERE is_active = TRUE;

-- GIN indexes (for full-text search and JSONB)
CREATE INDEX idx_users_search_vector ON users USING GIN(search_vector);
CREATE INDEX idx_user_activities_metadata ON user_activities USING GIN(metadata);

-- BRIN indexes (for time-series data, much smaller)
CREATE INDEX idx_user_activities_created_brin 
ON user_activities USING BRIN(created_at);
```

### Neo4j Indexes

```cypher
// Property indexes
CREATE INDEX user_id_index FOR (u:User) ON (u.userId);
CREATE INDEX user_email_index FOR (u:User) ON (u.email);

// Composite indexes
CREATE INDEX user_location_industry FOR (u:User) ON (u.location, u.industry);

// Unique constraints (automatically creates index)
CREATE CONSTRAINT user_id_unique FOR (u:User) REQUIRE u.userId IS UNIQUE;
CREATE CONSTRAINT company_id_unique FOR (c:Company) REQUIRE c.companyId IS UNIQUE;

// Full-text indexes
CREATE FULLTEXT INDEX user_search FOR (u:User) ON EACH [u.name, u.headline];

// Query using full-text index
CALL db.index.fulltext.queryNodes("user_search", "software engineer") 
YIELD node, score
RETURN node.userId, node.name, score
ORDER BY score DESC
LIMIT 10;
```

---

## 6. DATA CONSISTENCY

### Consistency Models

| **Operation** | **PostgreSQL** | **Neo4j** | **Redis** | **Model** |
|---------------|----------------|-----------|-----------|-----------|
| Create connection request | ACID (immediate) | N/A | N/A | Strong consistency |
| Accept connection | ACID (immediate) | Eventual (1-5s) | Invalidate cache | Eventual consistency |
| View connections | Read committed | Read committed | TTL-based | Read-your-writes |
| Recommendations | Stale OK (24h) | Stale OK (24h) | Stale OK (24h) | Eventual consistency |

### Handling Inconsistencies

```python
# Reconciliation job (runs hourly)
def reconcile_connections():
    """
    Compare PostgreSQL and Neo4j, fix discrepancies
    """
    # Get connections from PostgreSQL
    pg_connections = get_pg_connections()
    
    # Get connections from Neo4j
    neo4j_connections = get_neo4j_connections()
    
    # Find mismatches
    pg_only = pg_connections - neo4j_connections
    neo4j_only = neo4j_connections - pg_connections
    
    # Fix discrepancies
    for conn in pg_only:
        # PostgreSQL has it, Neo4j doesn't → Add to Neo4j
        create_neo4j_edge(conn.user1, conn.user2)
        log.warn(f"Added missing edge to Neo4j: {conn}")
    
    for conn in neo4j_only:
        # Neo4j has it, PostgreSQL doesn't → Remove from Neo4j
        delete_neo4j_edge(conn.user1, conn.user2)
        log.warn(f"Removed orphan edge from Neo4j: {conn}")
```

---

## 7. MIGRATION STRATEGY

### Zero-Downtime Migration

```
Step 1: Add Neo4j alongside PostgreSQL (dual-write)
Step 2: Backfill historical data (batch job)
Step 3: Validate data consistency
Step 4: Route read traffic to Neo4j (gradual rollout)
Step 5: Monitor performance and errors
Step 6: Remove old code paths
```

**Backfill Script**:
```python
# Backfill PostgreSQL → Neo4j
import psycopg2
from neo4j import GraphDatabase

# Connect to databases
pg_conn = psycopg2.connect("postgresql://...")
neo4j_driver = GraphDatabase.driver("bolt://...", auth=("neo4j", "password"))

# Fetch all connections
cursor = pg_conn.cursor()
cursor.execute("SELECT user_id_1, user_id_2, connected_at FROM connections")

batch_size = 10000
batch = []

for row in cursor:
    user1, user2, connected_at = row
    batch.append((user1, user2, connected_at))
    
    if len(batch) >= batch_size:
        # Bulk insert to Neo4j
        with neo4j_driver.session() as session:
            session.run("""
                UNWIND $connections AS conn
                MATCH (u1:User {userId: conn.user1})
                MATCH (u2:User {userId: conn.user2})
                MERGE (u1)-[:CONNECTED_TO {connectedAt: conn.connectedAt}]->(u2)
                MERGE (u2)-[:CONNECTED_TO {connectedAt: conn.connectedAt}]->(u1)
            """, connections=[
                {"user1": u1, "user2": u2, "connectedAt": ca}
                for u1, u2, ca in batch
            ])
        
        print(f"Inserted {len(batch)} connections")
        batch = []

cursor.close()
pg_conn.close()
neo4j_driver.close()
```

---

## 8. REAL-WORLD EXAMPLES

### LinkedIn's Actual Data Model

LinkedIn uses a custom graph database (similar to Facebook TAO) with:
- **Nodes**: Users, Companies, Jobs, Schools, Groups, Pages
- **Edges**: Connections, Follows, Works-At, Attended, Member-Of
- **Sharding**: Hash-based by user ID
- **Replication**: 3x replicas per shard
- **Cache**: Multi-tier (in-memory → Redis → database)

### Facebook TAO (The Associations and Objects)

```
TAO is Facebook's distributed graph database:
- Objects (nodes): User, Photo, Comment, Like
- Associations (edges): Friend, Tagged-In, Likes, Commented-On
- Scale: Billions of objects, trillions of associations
- Consistency: Eventually consistent
- Cache: 95%+ hit rate (aggressive caching)
```

---

## 🎯 KEY TAKEAWAYS

1. **Use Both**: Graph DB for relationships, RDBMS for transactional data
2. **Bidirectional Edges**: 2x storage for 10x query speed in graph DB
3. **Denormalization**: connection_count in user table (faster than COUNT(*))
4. **Event Sourcing**: Eventual consistency between PostgreSQL and Neo4j
5. **Aggressive Caching**: 80%+ cache hit rate reduces DB load
6. **Partitioning**: Shard by user_id for horizontal scaling
7. **Indexing**: Critical for performance (especially on connections table)

---

**This data model can scale from 1M to 1B users!** 🚀

---

*For complete implementation details, see the main LinkedIn Connections system design documentation.*
