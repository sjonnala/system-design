# System Design - Java Reference Implementation

This repository contains reference implementations of common system design patterns and algorithms in Java. Each module demonstrates key concepts used in building scalable distributed systems.

## 📦 Modules

### 1. Rate Limiter
Implementations of various rate limiting algorithms:
- **Token Bucket**: Smooth traffic with burst support
- **Sliding Window**: Precise rate limiting
- **Fixed Window**: Simple request counting

**Use Cases**: API throttling, DDoS protection, resource management

[📖 Documentation](./rate-limiter/README.md)

---

### 2. Consistent Hashing
Distributed hashing with virtual nodes for load distribution:
- Minimal key remapping on node changes
- Configurable virtual nodes for better distribution
- Thread-safe implementation

**Use Cases**: Distributed caching, database sharding, CDN routing

[📖 Documentation](./consistent-hashing/README.md)

---

### 3. Distributed Cache
Cache implementations with eviction policies:
- **LRU Cache**: Least Recently Used eviction
- O(1) operations with statistics tracking
- Thread-safe with concurrent access support

**Use Cases**: Database query caching, session storage, CDN edge caching

[📖 Documentation](./distributed-cache/README.md)

---

### 4. URL Shortener
Complete URL shortening service:
- Base62 encoding for compact URLs
- Custom short codes support
- Click tracking and analytics
- URL expiration with TTL

**Use Cases**: Link shortening (like bit.ly), campaign tracking, QR codes

[📖 Documentation](./url-shortener/README.md)

---

### 5. Load Balancer
Multiple load balancing strategies:
- **Round Robin**: Even distribution
- **Least Connections**: Load-aware routing
- **IP Hash**: Sticky sessions
- Health checking and connection tracking

**Use Cases**: Traffic distribution, high availability, scaling web services

[📖 Documentation](./load-balancer/README.md)

---

### 6. Distributed Queue
Message queue with delivery guarantees:
- Priority-based ordering
- Visibility timeout (like AWS SQS)
- Dead Letter Queue (DLQ)
- Automatic retry mechanism

**Use Cases**: Task queues, event processing, asynchronous communication

[📖 Documentation](./distributed-queue/README.md)

---

## 🚀 Getting Started

### Prerequisites
- Java 17 or higher
- Maven 3.6+

### Build All Modules
```bash
mvn clean install
```

### Run Tests
```bash
# All modules
mvn test

# Specific module
cd rate-limiter
mvn test
```

### Run a Specific Test
```bash
mvn test -Dtest=TokenBucketRateLimiterTest
```

## 📚 Project Structure

```
system-design/
├── pom.xml                      # Parent POM
├── rate-limiter/                # Rate limiting algorithms
│   ├── src/main/java/
│   ├── src/test/java/
│   └── README.md
├── consistent-hashing/          # Consistent hash ring
│   ├── src/main/java/
│   ├── src/test/java/
│   └── README.md
├── distributed-cache/           # Cache implementations
│   ├── src/main/java/
│   ├── src/test/java/
│   └── README.md
├── url-shortener/               # URL shortening service
│   ├── src/main/java/
│   ├── src/test/java/
│   └── README.md
├── load-balancer/               # Load balancing strategies
│   ├── src/main/java/
│   ├── src/test/java/
│   └── README.md
└── distributed-queue/           # Message queue
    ├── src/main/java/
    ├── src/test/java/
    └── README.md
```

## 🎯 Learning Path

**Beginner**: Start here to understand basics
1. **Rate Limiter** - Learn resource protection
2. **LRU Cache** - Understand caching fundamentals
3. **URL Shortener** - See ID generation and encoding

**Intermediate**: Build on fundamentals
4. **Load Balancer** - Traffic distribution strategies
5. **Distributed Queue** - Asynchronous processing

**Advanced**: Complex distributed systems
6. **Consistent Hashing** - Data partitioning at scale

## 🛠️ Technologies Used

- **Java 17**: Modern Java features
- **Maven**: Build and dependency management
- **JUnit 5**: Testing framework
- **Lombok**: Reduce boilerplate code
- **SLF4J**: Logging facade
- **Guava**: Google's core Java libraries

## 💡 Key System Design Concepts

Each module demonstrates important concepts:

### Scalability
- Horizontal scaling (Load Balancer)
- Data partitioning (Consistent Hashing)
- Caching strategies (Distributed Cache)

### Reliability
- Retry mechanisms (Distributed Queue)
- Health checking (Load Balancer)
- Failure handling (Dead Letter Queue)

### Performance
- O(1) operations (LRU Cache)
- Efficient encoding (URL Shortener)
- Rate limiting (Token Bucket)

### Distributed Systems
- Consistency vs Availability tradeoffs
- At-least-once delivery (Message Queue)
- Session persistence (IP Hash)

## 📖 Related Documentation

Explore the comprehensive system design documentation in the `docs/` folder:

- [**Fundamentals**](./docs/fundamentals/): Core concepts, CAP theorem, design principles
- [**Databases**](./docs/databases/): SQL vs NoSQL, data modeling
- [**Distributed Systems**](./docs/distributed-systems/): Consensus, replication, partitioning
- [**Scalability**](./docs/scalability/): Caching strategies, load balancing
- [**Interview Prep**](./docs/interview-prep/): Framework and senior engineer tricks

## 🎓 Interview Preparation

These implementations are perfect for:
- Understanding system design patterns
- Practicing coding interviews
- Learning distributed systems concepts
- Building production-ready features

**Common Interview Questions Covered**:
- Design a URL shortener (TinyURL)
- Design a rate limiter
- Implement an LRU cache
- Design a distributed cache
- Load balancing strategies
- Message queue design

## 🔧 Best Practices Demonstrated

1. **Clean Code**: Clear naming, single responsibility
2. **Testing**: Comprehensive unit tests with edge cases
3. **Documentation**: Inline comments and README files
4. **Thread Safety**: Concurrent data structures
5. **Error Handling**: Graceful failure handling
6. **Logging**: Proper logging levels (debug, info, warn)

## 🚀 Future Enhancements

Potential additions:
- [ ] Bloom Filter for efficient set membership
- [ ] Skip List for sorted data
- [ ] B-Tree implementation for databases
- [ ] Merkle Tree for data verification
- [ ] Circuit Breaker pattern
- [ ] Service Discovery
- [ ] Distributed Lock (Redis/Zookeeper)
- [ ] API Gateway implementation

## 📝 Contributing

Feel free to extend these implementations:
1. Add new algorithms or strategies
2. Improve test coverage
3. Optimize performance
4. Add more documentation
5. Create example applications

## 📚 References

- "Designing Data-Intensive Applications" by Martin Kleppmann
- "System Design Interview" by Alex Xu
- AWS Architecture Blog
- Google Cloud Architecture Center
- Microsoft Azure Architecture
- High Scalability Blog

## 📄 License

This project is for educational purposes. Use freely for learning and reference.

---

**Happy Learning! 🎉**

Built with ☕ and ❤️ for system design education.
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.systemdesign</groupId>
        <artifactId>system-design-reference</artifactId>
        <version>1.0-SNAPSHOT</version>
    </parent>

    <artifactId>distributed-queue</artifactId>
    <name>Distributed Queue Implementation</name>
    <description>Message queue implementation for distributed systems</description>

    <dependencies>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-api</artifactId>
        </dependency>
        <dependency>
            <groupId>org.slf4j</groupId>
            <artifactId>slf4j-simple</artifactId>
        </dependency>
        <dependency>
            <groupId>com.google.guava</groupId>
            <artifactId>guava</artifactId>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-api</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.junit.jupiter</groupId>
            <artifactId>junit-jupiter-engine</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
</project>

