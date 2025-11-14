# Real-World System Designs

Comprehensive walkthroughs of common system design interview questions.

## Contents

1. [URL Shortener](design-topics/tiny-url/url-shortener.md) - Classic starter problem
2. [Social Media Feed](social-feed.md) - Instagram/Twitter feed
3. [Distributed Cache](distributed-cache.md) - Redis/Memcached design
4. [Notification System](notification-system.md) - Push notifications at scale
5. [Video Streaming Platform](video-streaming.md) - YouTube/Netflix-like system
6. [Ride Sharing Platform](ride-sharing.md) - Uber/Lyft design
7. [E-commerce Platform](ecommerce.md) - Amazon-like marketplace
8. [Search Engine](search-engine.md) - Google-like search
9. [Chat System](chat-system.md) - WhatsApp/Slack messaging
10. [Rate Limiter](rate-limiter.md) - API rate limiting service

## How to Use These Examples

### For Learning
1. Read the problem statement
2. Try designing yourself first (30-45 min)
3. Compare with provided solution
4. Note differences in approach
5. Understand trade-offs

### For Practice
- Time yourself (45 minutes)
- Use RADIO framework
- Draw diagrams
- Calculate capacity
- Discuss trade-offs

### Difficulty Levels

**Beginner** (Good starting points):
- URL Shortener
- Rate Limiter
- Distributed Cache

**Intermediate** (Common in interviews):
- Social Media Feed
- Notification System
- Chat System

**Advanced** (Senior level):
- Video Streaming
- Ride Sharing
- E-commerce
- Search Engine

## Common Patterns Across Systems

### Scalability Patterns
- Load balancing
- Caching (multiple levels)
- Database replication
- Sharding/partitioning
- Asynchronous processing

### Reliability Patterns
- Redundancy
- Health checks
- Circuit breakers
- Graceful degradation
- Retry logic

### Performance Patterns
- CDN for static content
- Database indexes
- Connection pooling
- Batch processing
- Read replicas

## Interview Success Tips

1. **Start Simple**: Begin with basic version, then scale
2. **Ask Questions**: Clarify requirements first
3. **Use Numbers**: Back-of-the-envelope calculations
4. **Draw Diagrams**: Visual communication is key
5. **Discuss Trade-offs**: Every decision has pros/cons
6. **Think Out Loud**: Share your reasoning
7. **Be Flexible**: Adapt to interviewer feedback

## What Interviewers Look For

**Technical Knowledge**:
- Understanding of distributed systems
- Database choices and trade-offs
- Caching strategies
- Load balancing
- Scalability patterns

**Problem Solving**:
- Breaking down complex problems
- Systematic approach
- Handling constraints
- Edge case consideration

**Communication**:
- Clear explanations
- Asking good questions
- Collaborative discussion
- Articulating trade-offs

**Experience**:
- Real-world insights
- Operational concerns
- Monitoring and debugging
- Cost considerations
