# Scaling Strategies

Understanding when and how to scale your system is crucial for handling growth effectively.

## Contents
- [Vertical Scaling (Scale Up)](#vertical-scaling-scale-up)
- [Horizontal Scaling (Scale Out)](#horizontal-scaling-scale-out)
- [When to Use Each](#when-to-use-each)
- [Hybrid Approaches](#hybrid-approaches)
- [Cloud Auto-Scaling](#cloud-auto-scaling)

## Vertical Scaling (Scale Up)

Adding more resources to a single machine.

### What It Means
- Increase CPU cores
- Add more RAM
- Upgrade to faster storage (SSD, NVMe)
- Better network cards

### Advantages
- **Simple**: No code changes needed
- **No distributed complexity**: Everything on one machine
- **Strong consistency**: No network latency between components
- **Easier debugging**: Single point to monitor

### Disadvantages
- **Hardware limits**: Can't scale infinitely
- **Single point of failure**: If the machine goes down, everything stops
- **Expensive**: High-end hardware costs more per unit of performance
- **Downtime for upgrades**: Need to restart to add resources
- **Risk concentration**: All your eggs in one basket

### Good Use Cases
```
1. Legacy applications (hard to refactor)
2. Databases that require strong consistency
3. In-memory data stores (Redis, Memcached)
4. Small to medium workloads
5. Development/staging environments
```

### Real-World Example
```
PostgreSQL database:
- Start: 4 CPU, 16GB RAM → handles 1000 QPS
- Scale: 16 CPU, 64GB RAM → handles 4000 QPS
- Limit: Eventually hit single-machine ceiling
```

## Horizontal Scaling (Scale Out)

Adding more machines to distribute the load.

### What It Means
- Add more servers
- Distribute traffic across multiple instances
- Each instance handles a portion of the load

### Advantages
- **Virtually unlimited scaling**: Add more machines as needed
- **Fault tolerance**: If one machine fails, others continue
- **Cost-effective**: Use commodity hardware
- **No downtime**: Add/remove machines without stopping service
- **Geographic distribution**: Place servers closer to users

### Disadvantages
- **Complex architecture**: Requires load balancers, coordination
- **Data consistency challenges**: CAP theorem applies
- **Network overhead**: Communication between services
- **Harder debugging**: Distributed tracing needed
- **State management**: Sessions, caches must be shared

### Requirements for Horizontal Scaling
```
1. Stateless application design
2. Load balancer to distribute traffic
3. Shared data layer (database, cache)
4. Session management (sticky sessions or external store)
5. Service discovery (in dynamic environments)
```

### Making Applications Horizontally Scalable

#### Before (Not Scalable)
```python
class UserService:
    def __init__(self):
        self.user_cache = {}  # In-memory cache (problem!)

    def get_user(self, user_id):
        if user_id in self.user_cache:
            return self.user_cache[user_id]

        user = database.get_user(user_id)
        self.user_cache[user_id] = user
        return user
```

#### After (Scalable)
```python
class UserService:
    def __init__(self, redis_client):
        self.cache = redis_client  # Shared cache

    def get_user(self, user_id):
        cached = self.cache.get(f"user:{user_id}")
        if cached:
            return json.loads(cached)

        user = database.get_user(user_id)
        self.cache.setex(f"user:{user_id}", 3600, json.dumps(user))
        return user
```

### Real-World Example
```
Web application tier:
- Start: 1 server → handles 1000 RPS
- Scale: 10 servers → handles 10000 RPS
- Scale: 100 servers → handles 100000 RPS
```

## When to Use Each

### Choose Vertical Scaling When:
- Getting started (simpler)
- Application can't be easily distributed
- Strong consistency is critical
- Budget allows for premium hardware
- Workload has predictable, moderate growth

### Choose Horizontal Scaling When:
- Need high availability
- Expecting rapid growth
- Building cloud-native applications
- Cost optimization is important
- Serving global users

## Hybrid Approaches

Most real systems use both strategies together.

### Pattern 1: Scale Components Differently
```
Frontend:        Horizontal (stateless web servers)
Application:     Horizontal (API servers)
Cache:           Horizontal (Redis cluster)
Database:        Vertical + Read Replicas
```

### Pattern 2: Start Vertical, Then Horizontal
```
Stage 1: Single powerful server (vertical)
Stage 2: Add read replicas (horizontal for reads)
Stage 3: Shard database (horizontal for writes)
Stage 4: Microservices (horizontal everything)
```

### Pattern 3: Auto-Scaling Groups
```
Minimum: 2 instances (vertical: medium size)
Normal: 5 instances (scale out)
Peak: 20 instances (scale out more)
```

## Cloud Auto-Scaling

Modern cloud platforms enable dynamic scaling.

### Metrics-Based Scaling
```yaml
Trigger: CPU > 70% for 5 minutes
Action: Add 2 instances

Trigger: CPU < 30% for 10 minutes
Action: Remove 1 instance

Limits:
  Min: 2 instances
  Max: 20 instances
```

### Scheduled Scaling
```yaml
# Black Friday preparation
Event: Known traffic spike
Action: Pre-scale to 50 instances before event
Schedule: 11/24 11:00 PM
```

### Predictive Scaling
```yaml
# ML-based forecasting
Pattern: Historical traffic patterns
Action: Scale ahead of predicted demand
Example: Scale up weekdays 9 AM, scale down 6 PM
```

## Scaling Decision Matrix

| Factor | Vertical | Horizontal |
|--------|----------|------------|
| **Cost (small scale)** | Lower | Higher |
| **Cost (large scale)** | Higher | Lower |
| **Complexity** | Low | High |
| **Max capacity** | Limited | Unlimited |
| **Availability** | Single point of failure | High availability |
| **Consistency** | Easy | Challenging |
| **Implementation time** | Minutes | Days/Weeks |

## Key Takeaways

1. **Start simple**: Vertical scaling is often sufficient initially
2. **Plan for horizontal**: Design stateless applications from the start
3. **Database is usually the bottleneck**: Scale it last, most carefully
4. **Monitor before scaling**: Know your actual bottlenecks
5. **Use both strategies**: Hybrid approach works best in practice
6. **Automate scaling**: Don't scale manually in production

## Common Pitfalls

### Premature Horizontal Scaling
```
Problem: Building distributed system before needed
Impact: Wasted development time, added complexity
Solution: Scale vertically first, then horizontally when proven necessary
```

### Session Affinity (Sticky Sessions)
```
Problem: Load balancer routes user to same server (defeats scaling)
Impact: Uneven load distribution, can't remove servers easily
Solution: Store sessions in Redis/database, make servers stateless
```

### Ignoring Database Scaling
```
Problem: Scale app tier but database remains bottleneck
Impact: Limited overall scaling effectiveness
Solution: Use read replicas, caching, eventually sharding
```

### Hard-coded Configuration
```
Problem: Server IPs/endpoints hard-coded in application
Impact: Can't dynamically add/remove servers
Solution: Use service discovery (Consul, Kubernetes DNS)
```

## Interview Tips

When discussing scaling strategies:

1. **Ask about current scale**: "How many users/requests currently?"
2. **Ask about growth expectations**: "What's the expected growth rate?"
3. **Discuss tradeoffs**: Always mention pros/cons of each approach
4. **Start simple**: Suggest vertical first unless requirements dictate otherwise
5. **Think holistically**: Consider all system components, not just app servers

## Further Reading

- [Load Balancing](load-balancing.md) - Distributing traffic in horizontal scaling
- [Caching Strategies](caching.md) - Reducing load through caching
- [Database Scaling](../databases/scaling.md) - Scaling the data layer
- Designing Data-Intensive Applications (Chapter 1: Reliability, Scalability, Maintainability)
