# Design a Distributed Job Scheduler

**Companies**: Airbnb (Airflow), LinkedIn (Azkaban), Netflix (Conductor), Uber (Cadence), Temporal
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a distributed job scheduler that can execute millions of tasks reliably at scale. The system should support recurring jobs, one-time jobs, job dependencies, retries, priority scheduling, and fault tolerance. Think of systems like Kubernetes CronJobs, Apache Airflow, or AWS Step Functions.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Schedule Jobs**: Support cron expressions and one-time execution
2. **Job Dependencies**: DAG-based task execution (Task B runs after Task A)
3. **Priority Scheduling**: High-priority jobs execute first
4. **Retry Mechanism**: Configurable retry policies with exponential backoff
5. **Job Monitoring**: Track job status (pending, running, completed, failed)
6. **Job History**: Store execution logs and audit trails
7. **Resource Management**: CPU/memory limits per job
8. **Distributed Execution**: Run jobs across multiple worker nodes

**Prioritize**: Focus on reliability, scalability, and fault tolerance for MVP

### Non-Functional Requirements

1. **High Availability**: 99.99% uptime (no single point of failure)
2. **Scalability**: Handle 10M+ jobs per day
3. **Low Latency**: Job scheduling within 1 second of trigger time
4. **Fault Tolerance**: Survive node failures without job loss
5. **Exactly-Once Execution**: No duplicate job executions
6. **Strong Consistency**: Accurate job state across distributed system

### Capacity Estimation

**Assumptions**:
- 10M jobs scheduled per day
- 50% recurring jobs (cron), 50% one-time jobs
- Average job execution time: 5 minutes
- Peak hours: 3x average load
- Job metadata size: 2KB per job
- Execution logs: 10KB per job

**Write Load (Job Scheduling)**:
```
10M jobs/day ÷ 86,400 sec = ~115 jobs/sec
Peak: 115 × 3 = ~350 jobs/sec
```

**Execution Load (Job Processing)**:
```
Concurrent jobs = Total jobs × Avg duration ÷ Seconds in day
                = 10M × 300 sec ÷ 86,400
                = ~35,000 concurrent executions
```

**Storage**:
```
Job metadata: 10M jobs/day × 2KB × 365 days = 7.3 TB/year
Execution logs: 10M jobs/day × 10KB × 365 days = 36.5 TB/year
Total: ~44 TB/year (retain 2 years = 88 TB)
```

**Database QPS**:
```
Reads (status checks): 35K concurrent × 10 checks/min ÷ 60 = ~6K QPS
Writes (state updates): 115 jobs/sec × 5 states = ~575 QPS
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                    API Gateway                          │
│              (Job Submission & Query)                   │
└──────────────────────┬──────────────────────────────────┘
                       │
            ┌──────────┴──────────┐
            ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│ Scheduler Service│  │  Executor Pool   │
│  (Coordinator)   │  │  (Worker Nodes)  │
└────────┬─────────┘  └────────┬─────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────┐
         │  Distributed Queue  │
         │    (Kafka/RabbitMQ) │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌──────────────────┐  ┌──────────────────┐
│  Job Metadata DB │  │  Execution Logs  │
│  (PostgreSQL)    │  │  (ClickHouse)    │
└──────────────────┘  └──────────────────┘
         │
         ▼
┌──────────────────┐
│  Coordination    │
│  (ZooKeeper/etcd)│
└──────────────────┘
```

### Components

**1. API Gateway**:
- REST/gRPC endpoints for job submission
- Authentication & authorization
- Rate limiting
- Request validation

**2. Scheduler Service (Brain)**:
- Cron job parser & trigger calculator
- Priority queue management
- DAG dependency resolver
- Job dispatching to executors
- Health checks & leader election

**3. Distributed Queue (Task Buffer)**:
- Kafka/RabbitMQ for job distribution
- Partitioned by priority
- Dead letter queue for failed jobs
- Message durability & replication

**4. Executor Pool (Workers)**:
- Stateless worker nodes
- Auto-scaling based on queue depth
- Resource isolation (containers/VMs)
- Job execution & status reporting

**5. Job Metadata Database**:
- Job definitions (cron, script, dependencies)
- Job status (pending, running, completed, failed)
- Execution history (last run, next run)
- User metadata

**6. Execution Logs Storage**:
- Time-series database (ClickHouse)
- Real-time log streaming
- Retention policies
- Analytics & reporting

**7. Coordination Service**:
- Distributed locks (prevent duplicate execution)
- Leader election for scheduler
- Service discovery
- Configuration management

---

## Step 3: Data Model (10 min)

### Database Choice

**Primary Storage**: PostgreSQL (ACID guarantees for job metadata)
**Logs Storage**: ClickHouse (high-throughput time-series analytics)
**Cache**: Redis (hot job data, distributed locks)

### Schema

```sql
-- Job Definition Table
CREATE TABLE jobs (
    job_id UUID PRIMARY KEY,
    job_name VARCHAR(255) NOT NULL,
    job_type VARCHAR(50) NOT NULL, -- 'cron', 'one_time', 'dependent'
    cron_expression VARCHAR(100), -- e.g., '*/5 * * * *'
    script TEXT NOT NULL, -- Job payload (script, command, HTTP endpoint)
    priority INTEGER DEFAULT 5, -- 1 (highest) to 10 (lowest)
    max_retries INTEGER DEFAULT 3,
    timeout_seconds INTEGER DEFAULT 3600,
    resource_limits JSONB, -- {cpu: '1', memory: '2Gi'}
    dependencies JSONB, -- [job_id_1, job_id_2] (DAG)
    created_by VARCHAR(255),
    created_at TIMESTAMP DEFAULT NOW(),
    is_active BOOLEAN DEFAULT TRUE,
    next_run_at TIMESTAMP,
    last_run_at TIMESTAMP,
    INDEX idx_next_run_at (next_run_at),
    INDEX idx_priority (priority)
);

-- Job Execution Table (immutable history)
CREATE TABLE job_executions (
    execution_id UUID PRIMARY KEY,
    job_id UUID REFERENCES jobs(job_id),
    status VARCHAR(50) NOT NULL, -- 'pending', 'running', 'completed', 'failed', 'cancelled'
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    worker_node VARCHAR(255),
    retry_count INTEGER DEFAULT 0,
    exit_code INTEGER,
    error_message TEXT,
    execution_logs TEXT, -- Summary logs (full logs in ClickHouse)
    INDEX idx_job_id (job_id),
    INDEX idx_status (status),
    INDEX idx_started_at (started_at)
);

-- Distributed Locks Table (for exactly-once execution)
CREATE TABLE distributed_locks (
    lock_key VARCHAR(255) PRIMARY KEY,
    owner_id VARCHAR(255) NOT NULL,
    acquired_at TIMESTAMP DEFAULT NOW(),
    expires_at TIMESTAMP NOT NULL,
    INDEX idx_expires_at (expires_at)
);

-- Worker Nodes Table (health tracking)
CREATE TABLE worker_nodes (
    node_id VARCHAR(255) PRIMARY KEY,
    ip_address VARCHAR(45),
    capacity JSONB, -- {cpu: '4', memory: '8Gi'}
    current_load JSONB, -- {running_jobs: 5}
    status VARCHAR(50) DEFAULT 'healthy', -- 'healthy', 'unhealthy', 'draining'
    last_heartbeat TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_status (status),
    INDEX idx_last_heartbeat (last_heartbeat)
);
```

**ClickHouse Schema (Execution Logs)**:
```sql
CREATE TABLE job_logs (
    execution_id UUID,
    job_id UUID,
    timestamp DateTime,
    log_level String, -- 'info', 'warn', 'error'
    message String,
    worker_node String,
    tags Array(String)
) ENGINE = MergeTree()
PARTITION BY toYYYYMM(timestamp)
ORDER BY (execution_id, timestamp);
```

---

## Step 4: APIs (5 min)

### Create Job

```http
POST /api/v1/jobs
Content-Type: application/json
Authorization: Bearer <token>

Request:
{
  "job_name": "daily_report",
  "job_type": "cron",
  "cron_expression": "0 2 * * *", // 2 AM daily
  "script": "python /scripts/daily_report.py",
  "priority": 5,
  "max_retries": 3,
  "timeout_seconds": 1800,
  "resource_limits": {
    "cpu": "500m",
    "memory": "1Gi"
  },
  "dependencies": [] // Empty for independent jobs
}

Response: 201 Created
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "job_name": "daily_report",
  "next_run_at": "2025-11-15T02:00:00Z",
  "status": "active",
  "created_at": "2025-11-14T10:00:00Z"
}
```

### Get Job Status

```http
GET /api/v1/jobs/{job_id}

Response: 200 OK
{
  "job_id": "550e8400-e29b-41d4-a716-446655440000",
  "job_name": "daily_report",
  "status": "active",
  "last_execution": {
    "execution_id": "660e9500-f39c-52e5-b827-557766551111",
    "status": "completed",
    "started_at": "2025-11-14T02:00:00Z",
    "completed_at": "2025-11-14T02:05:23Z",
    "exit_code": 0
  },
  "next_run_at": "2025-11-15T02:00:00Z"
}
```

### Cancel Job Execution

```http
DELETE /api/v1/executions/{execution_id}

Response: 200 OK
{
  "execution_id": "660e9500-f39c-52e5-b827-557766551111",
  "status": "cancelled",
  "message": "Job execution cancelled successfully"
}
```

### List Job Executions

```http
GET /api/v1/jobs/{job_id}/executions?limit=10&offset=0

Response: 200 OK
{
  "executions": [
    {
      "execution_id": "660e9500-f39c-52e5-b827-557766551111",
      "status": "completed",
      "started_at": "2025-11-14T02:00:00Z",
      "duration_seconds": 323,
      "exit_code": 0
    }
  ],
  "total": 45,
  "limit": 10,
  "offset": 0
}
```

---

## Step 5: Core Algorithms

### 1. Cron Expression Parsing

```python
from datetime import datetime, timedelta
from croniter import croniter

def calculate_next_run(cron_expression: str, last_run: datetime) -> datetime:
    """
    Calculate next execution time from cron expression.

    Examples:
    - '*/5 * * * *'  → Every 5 minutes
    - '0 2 * * *'    → Daily at 2 AM
    - '0 9 * * MON'  → Every Monday at 9 AM
    """
    cron = croniter(cron_expression, last_run)
    return cron.get_next(datetime)

# Usage
next_run = calculate_next_run('0 2 * * *', datetime.now())
print(f"Next run: {next_run}")  # 2025-11-15 02:00:00
```

### 2. Priority Queue Scheduling

```python
import heapq
from dataclasses import dataclass, field
from typing import List

@dataclass(order=True)
class ScheduledJob:
    priority: int
    scheduled_time: datetime = field(compare=False)
    job_id: str = field(compare=False)

class PriorityScheduler:
    def __init__(self):
        self.queue: List[ScheduledJob] = []

    def schedule(self, job_id: str, priority: int, scheduled_time: datetime):
        """Add job to priority queue."""
        job = ScheduledJob(priority, scheduled_time, job_id)
        heapq.heappush(self.queue, job)

    def get_next_job(self) -> ScheduledJob:
        """Pop highest priority job that's ready to run."""
        now = datetime.now()
        while self.queue:
            job = heapq.heappop(self.queue)
            if job.scheduled_time <= now:
                return job
            else:
                # Not ready yet, put back
                heapq.heappush(self.queue, job)
                return None
        return None

# Usage
scheduler = PriorityScheduler()
scheduler.schedule('job-1', priority=1, scheduled_time=datetime.now())
scheduler.schedule('job-2', priority=5, scheduled_time=datetime.now())
next_job = scheduler.get_next_job()  # Returns job-1 (higher priority)
```

### 3. DAG Dependency Resolution

```python
from collections import defaultdict, deque
from typing import Dict, List, Set

class DAGScheduler:
    def __init__(self):
        self.graph: Dict[str, List[str]] = defaultdict(list)
        self.in_degree: Dict[str, int] = defaultdict(int)

    def add_dependency(self, parent_job: str, child_job: str):
        """
        Add edge: parent_job → child_job
        Child can only run after parent completes.
        """
        self.graph[parent_job].append(child_job)
        self.in_degree[child_job] += 1
        if parent_job not in self.in_degree:
            self.in_degree[parent_job] = 0

    def detect_cycle(self) -> bool:
        """Returns True if cycle detected (invalid DAG)."""
        visited = set()
        rec_stack = set()

        def dfs(node: str) -> bool:
            visited.add(node)
            rec_stack.add(node)

            for neighbor in self.graph[node]:
                if neighbor not in visited:
                    if dfs(neighbor):
                        return True
                elif neighbor in rec_stack:
                    return True  # Cycle detected

            rec_stack.remove(node)
            return False

        for node in self.graph:
            if node not in visited:
                if dfs(node):
                    return True
        return False

    def get_execution_order(self) -> List[str]:
        """
        Topological sort using Kahn's algorithm.
        Returns list of jobs in execution order.
        """
        if self.detect_cycle():
            raise ValueError("Cycle detected in job dependencies")

        queue = deque([node for node, degree in self.in_degree.items() if degree == 0])
        execution_order = []

        while queue:
            node = queue.popleft()
            execution_order.append(node)

            for neighbor in self.graph[node]:
                self.in_degree[neighbor] -= 1
                if self.in_degree[neighbor] == 0:
                    queue.append(neighbor)

        return execution_order

# Usage
dag = DAGScheduler()
dag.add_dependency('extract_data', 'transform_data')
dag.add_dependency('transform_data', 'load_data')
dag.add_dependency('load_data', 'send_report')

order = dag.get_execution_order()
print(order)  # ['extract_data', 'transform_data', 'load_data', 'send_report']
```

### 4. Distributed Lock (Exactly-Once Execution)

```python
import time
import uuid
from redis import Redis

class DistributedLock:
    def __init__(self, redis_client: Redis):
        self.redis = redis_client

    def acquire_lock(self, job_id: str, ttl_seconds: int = 300) -> str:
        """
        Acquire distributed lock for job execution.
        Returns lock_token if successful, None otherwise.
        """
        lock_key = f"job_lock:{job_id}"
        lock_token = str(uuid.uuid4())

        # SET NX (only if not exists) with expiration
        acquired = self.redis.set(
            lock_key,
            lock_token,
            nx=True,  # Only set if doesn't exist
            ex=ttl_seconds  # Auto-expire after TTL
        )

        return lock_token if acquired else None

    def release_lock(self, job_id: str, lock_token: str) -> bool:
        """
        Release lock only if we own it (using Lua script for atomicity).
        """
        lock_key = f"job_lock:{job_id}"

        # Lua script for atomic check-and-delete
        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("del", KEYS[1])
        else
            return 0
        end
        """

        result = self.redis.eval(lua_script, 1, lock_key, lock_token)
        return result == 1

    def extend_lock(self, job_id: str, lock_token: str, additional_seconds: int) -> bool:
        """Extend lock expiration (for long-running jobs)."""
        lock_key = f"job_lock:{job_id}"

        lua_script = """
        if redis.call("get", KEYS[1]) == ARGV[1] then
            return redis.call("expire", KEYS[1], ARGV[2])
        else
            return 0
        end
        """

        result = self.redis.eval(lua_script, 1, lock_key, lock_token, additional_seconds)
        return result == 1

# Usage
redis = Redis(host='localhost', port=6379)
lock = DistributedLock(redis)

# Executor attempts to acquire lock before running job
lock_token = lock.acquire_lock('job-123', ttl_seconds=300)
if lock_token:
    try:
        # Execute job
        execute_job('job-123')
    finally:
        # Always release lock
        lock.release_lock('job-123', lock_token)
else:
    print("Another executor is already running this job")
```

### 5. Retry Logic with Exponential Backoff

```python
import time
from typing import Callable
from datetime import datetime, timedelta

class RetryPolicy:
    def __init__(self, max_retries: int = 3, base_delay: int = 2, max_delay: int = 60):
        self.max_retries = max_retries
        self.base_delay = base_delay  # Initial delay in seconds
        self.max_delay = max_delay    # Maximum delay cap

    def calculate_next_retry(self, retry_count: int) -> datetime:
        """
        Exponential backoff: 2^retry_count seconds
        Retry 1: 2 sec, Retry 2: 4 sec, Retry 3: 8 sec
        """
        delay = min(self.base_delay ** retry_count, self.max_delay)
        return datetime.now() + timedelta(seconds=delay)

    def execute_with_retry(self, func: Callable, *args, **kwargs):
        """Execute function with retry logic."""
        retry_count = 0
        last_error = None

        while retry_count <= self.max_retries:
            try:
                return func(*args, **kwargs)
            except Exception as e:
                last_error = e
                retry_count += 1

                if retry_count <= self.max_retries:
                    next_retry = self.calculate_next_retry(retry_count)
                    delay = (next_retry - datetime.now()).total_seconds()
                    print(f"Retry {retry_count}/{self.max_retries} after {delay}s")
                    time.sleep(delay)
                else:
                    print(f"Max retries exceeded. Job failed.")
                    raise last_error

# Usage
retry_policy = RetryPolicy(max_retries=3, base_delay=2, max_delay=60)

def unreliable_job():
    import random
    if random.random() < 0.5:
        raise Exception("Random failure")
    return "Success"

retry_policy.execute_with_retry(unreliable_job)
```

---

## Step 6: Optimizations & Trade-offs (15 min)

### 1. Scheduler High Availability (Leader Election)

**Challenge**: Single scheduler is a bottleneck and single point of failure.

**Solution**: Active-Passive leader election using ZooKeeper/etcd

```python
from kazoo.client import KazooClient
from kazoo.recipe.election import Election

class SchedulerLeader:
    def __init__(self, zk_hosts: str):
        self.zk = KazooClient(hosts=zk_hosts)
        self.zk.start()
        self.election = Election(self.zk, "/scheduler/election")

    def run_as_leader(self, schedule_jobs_func):
        """
        Only run scheduling loop if this instance is the leader.
        """
        def leader_callback():
            print("I am the leader! Starting scheduler...")
            schedule_jobs_func()  # Run scheduling loop

        # Blocks until elected as leader
        self.election.run(leader_callback)

# Usage
scheduler = SchedulerLeader(zk_hosts='localhost:2181')
scheduler.run_as_leader(schedule_jobs_loop)
```

**Benefits**:
- Automatic failover if leader dies
- Prevents duplicate job scheduling
- Health checks via heartbeat

### 2. Worker Auto-Scaling

**Metric-Based Scaling**:
```python
class WorkerAutoScaler:
    def __init__(self, min_workers: int = 5, max_workers: int = 100):
        self.min_workers = min_workers
        self.max_workers = max_workers

    def calculate_desired_workers(self, queue_depth: int, avg_processing_time: float) -> int:
        """
        Scale based on queue depth and processing time.
        Target: Clear queue within 5 minutes.
        """
        target_clearance_time = 300  # 5 minutes

        if queue_depth == 0:
            return self.min_workers

        # Calculate workers needed to clear queue in target time
        desired_workers = (queue_depth * avg_processing_time) / target_clearance_time

        # Apply min/max bounds
        return max(self.min_workers, min(int(desired_workers), self.max_workers))

# Kubernetes HPA (Horizontal Pod Autoscaler)
# Scale based on custom metrics (queue depth from Kafka)
```

### 3. Job Partitioning (Sharding)

**Problem**: Single queue becomes bottleneck at high scale.

**Solution**: Partition jobs by priority and type

```python
class JobPartitioner:
    def get_partition(self, job) -> int:
        """
        Partition strategy:
        - High priority (1-3): Partitions 0-2
        - Medium priority (4-7): Partitions 3-6
        - Low priority (8-10): Partitions 7-9
        """
        if job.priority <= 3:
            return job.priority - 1  # 0, 1, 2
        elif job.priority <= 7:
            return job.priority - 1  # 3, 4, 5, 6
        else:
            return job.priority - 1  # 7, 8, 9

    def assign_to_queue(self, job):
        partition = self.get_partition(job)
        kafka_producer.send(
            topic='scheduled_jobs',
            partition=partition,
            value=job.to_dict()
        )
```

**Benefits**:
- High-priority jobs get dedicated partitions
- Prevents head-of-line blocking
- Better parallelism

### 4. Time-Wheel Scheduling (Efficient Delay Queue)

**Problem**: Checking every job in database for "next_run_at" is expensive.

**Solution**: Hierarchical Time Wheel (inspired by Kafka's approach)

```python
from collections import defaultdict
from typing import List
import time

class TimeWheel:
    def __init__(self, tick_duration: int = 1, wheel_size: int = 60):
        """
        tick_duration: seconds per slot (e.g., 1 second)
        wheel_size: number of slots (e.g., 60 for 60-second wheel)
        """
        self.tick_duration = tick_duration
        self.wheel_size = wheel_size
        self.current_tick = 0
        self.slots: List[List] = [[] for _ in range(wheel_size)]

    def add_job(self, job_id: str, delay_seconds: int):
        """Add job to wheel at appropriate slot."""
        ticks_from_now = delay_seconds // self.tick_duration
        slot_index = (self.current_tick + ticks_from_now) % self.wheel_size
        self.slots[slot_index].append(job_id)

    def tick(self) -> List[str]:
        """Advance wheel by one tick, return jobs to execute."""
        jobs_to_run = self.slots[self.current_tick]
        self.slots[self.current_tick] = []  # Clear slot
        self.current_tick = (self.current_tick + 1) % self.wheel_size
        return jobs_to_run

    def run(self):
        """Run scheduler loop."""
        while True:
            jobs = self.tick()
            for job_id in jobs:
                execute_job(job_id)
            time.sleep(self.tick_duration)

# Usage
wheel = TimeWheel(tick_duration=1, wheel_size=3600)  # 1-hour wheel
wheel.add_job('job-1', delay_seconds=30)
wheel.add_job('job-2', delay_seconds=3600)
```

### 5. Dead Letter Queue (DLQ)

**Handle Failed Jobs**:
```python
class DeadLetterHandler:
    def __init__(self, kafka_producer):
        self.producer = kafka_producer

    def send_to_dlq(self, job, error: Exception, retry_count: int):
        """Move failed job to dead letter queue for manual intervention."""
        dlq_message = {
            'job_id': job.job_id,
            'original_payload': job.to_dict(),
            'error_message': str(error),
            'retry_count': retry_count,
            'failed_at': datetime.now().isoformat()
        }

        self.producer.send(
            topic='dead_letter_queue',
            value=dlq_message
        )

        # Alert operations team
        send_alert(f"Job {job.job_id} moved to DLQ after {retry_count} retries")

# Consumer for DLQ (manual or automated recovery)
def dlq_consumer():
    for message in kafka_consumer.subscribe(['dead_letter_queue']):
        job_data = message.value
        # Log to database for analysis
        # Optionally: Auto-retry after investigation
        # Send to monitoring system
```

---

## Step 7: Advanced Considerations

### Distributed Consensus

**Raft/Paxos for Scheduler Cluster**:
```
┌─────────────┐
│  Scheduler  │ ← Leader (writes to DB, schedules jobs)
│  Node 1     │
└─────────────┘
      │
      ├───────────┐
      ▼           ▼
┌─────────────┐ ┌─────────────┐
│  Scheduler  │ │  Scheduler  │ ← Followers (replicate state, ready for failover)
│  Node 2     │ │  Node 3     │
└─────────────┘ └─────────────┘
```

**Benefits**:
- Strong consistency across cluster
- Automatic leader election
- No split-brain scenarios

### Idempotency

**Ensure jobs can be retried safely**:
```python
class IdempotentJob:
    def execute(self, job_id: str, execution_id: str):
        """
        Use execution_id as idempotency key.
        """
        # Check if already processed
        if self.is_already_processed(execution_id):
            print(f"Execution {execution_id} already completed. Skipping.")
            return

        try:
            # Perform job
            result = self.do_work(job_id)

            # Mark as processed (atomic operation)
            self.mark_as_processed(execution_id, result)
        except Exception as e:
            # Record failure
            self.mark_as_failed(execution_id, e)
            raise

    def is_already_processed(self, execution_id: str) -> bool:
        # Check in database or Redis
        return db.exists(f"execution:{execution_id}")

    def mark_as_processed(self, execution_id: str, result):
        # Atomic write
        db.set(f"execution:{execution_id}", result)
```

### Resource Isolation

**Containerization with Kubernetes**:
```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: job-execution-12345
spec:
  template:
    spec:
      containers:
      - name: job-runner
        image: job-executor:v1
        resources:
          requests:
            cpu: "500m"
            memory: "1Gi"
          limits:
            cpu: "1000m"
            memory: "2Gi"
      restartPolicy: Never
  backoffLimit: 3  # Retry 3 times
```

**Benefits**:
- CPU/memory guarantees
- Fault isolation
- Easy scaling

### Monitoring & Observability

**Key Metrics**:
```python
from prometheus_client import Counter, Histogram, Gauge

# Metrics
jobs_scheduled = Counter('jobs_scheduled_total', 'Total jobs scheduled', ['priority'])
jobs_completed = Counter('jobs_completed_total', 'Total jobs completed', ['status'])
job_duration = Histogram('job_duration_seconds', 'Job execution duration')
queue_depth = Gauge('queue_depth', 'Current queue depth', ['priority'])
worker_utilization = Gauge('worker_utilization', 'Worker CPU/memory utilization')

# Usage
jobs_scheduled.labels(priority='high').inc()
job_duration.observe(execution_time)
queue_depth.labels(priority='high').set(current_depth)
```

**Distributed Tracing**:
```python
from opentelemetry import trace
from opentelemetry.instrumentation.requests import RequestsInstrumentor

tracer = trace.get_tracer(__name__)

def schedule_job(job_id: str):
    with tracer.start_as_current_span("schedule_job") as span:
        span.set_attribute("job.id", job_id)

        # Schedule job
        queue.publish(job)

        span.set_attribute("queue.partition", partition)
        span.add_event("Job scheduled successfully")
```

---

## Complete System Design Diagram

```
                          ┌──────────────────┐
                          │   API Gateway    │
                          │  (Kong / Envoy)  │
                          └────────┬─────────┘
                                   │
                          ┌────────▼─────────┐
                          │  Scheduler Svc   │ ← Leader Election (ZooKeeper)
                          │  (3 replicas)    │
                          └────────┬─────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
           ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
           │Kafka Partition│ │Kafka Partition│ │Kafka Partition│
           │ (High Pri)  │ │ (Med Pri)   │ │ (Low Pri)   │
           └─────────────┘ └─────────────┘ └─────────────┘
                    │              │              │
                    └──────────────┼──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │      Executor Pool          │
                    │  (K8s Pods, Auto-scaled)    │
                    │  Min: 10, Max: 1000         │
                    └──────────────┬──────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    ▼              ▼              ▼
           ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
           │  PostgreSQL │ │   Redis     │ │ ClickHouse  │
           │  (Jobs DB)  │ │ (Locks)     │ │  (Logs)     │
           └─────────────┘ └─────────────┘ └─────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected job volume (QPS)?
- Average job execution time?
- Retry requirements?
- Dependency complexity (simple chains vs. complex DAGs)?
- Exactly-once vs. at-least-once semantics?
- Job isolation requirements?

**Topics to Cover**:
- Distributed coordination (leader election, locks)
- Scheduling algorithms (cron, priority queue, DAG)
- Fault tolerance (retries, DLQ, idempotency)
- Scalability (partitioning, auto-scaling)
- Monitoring (metrics, logs, tracing)

**Common Follow-ups**:
- "How do you prevent duplicate executions?"
  → Distributed locks with TTL, idempotency keys
- "How do you handle long-running jobs?"
  → Heartbeat mechanism, lock extension, timeout handling
- "How do you scale to 100M jobs/day?"
  → Partitioning by priority, worker auto-scaling, time-wheel optimization
- "What if scheduler node crashes during execution?"
  → Leader election ensures new leader takes over, jobs in queue are safe

---

## Key Takeaways

1. **Distributed Coordination is Critical**: Leader election, distributed locks prevent chaos
2. **Idempotency is Non-Negotiable**: Jobs must be safely retryable
3. **Partitioning Scales**: Separate queues by priority prevent head-of-line blocking
4. **Time-Wheel Optimizes**: Efficient data structure for delay scheduling
5. **Observability is Essential**: Metrics, logs, tracing for debugging at scale
6. **DAG Resolution**: Topological sort for dependency management

## Further Reading

- Apache Airflow Architecture
- Temporal Workflow Engine
- Kubernetes Job & CronJob Controllers
- Uber Cadence Design
- Google Borg Paper (cluster scheduling)
- "Designing Data-Intensive Applications" - Martin Kleppmann
