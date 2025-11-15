# 🛠️ Distributed Locking: Industry Tech Stack & DevOps Solutions

## Complete Technology Ecosystem for Production Distributed Locking

This document covers the **actual technologies, tools, and platforms** used by top companies for building distributed locking and coordination systems.

---

## 🎯 PART 1: Core Coordination Services (The Foundation)

### 1. etcd (Recommended for Modern Systems)

**What**: Distributed key-value store with Raft consensus, built by CoreOS (now part of Red Hat)

**Used By**: Kubernetes, Cloud Foundry, Patroni (PostgreSQL HA), OpenStack

**Why Choose etcd**:
```
✅ Strong consistency (linearizable reads/writes)
✅ Simple Raft implementation (easier to reason about)
✅ High performance: 10K write QPS, 100K read QPS
✅ Built-in lease mechanism (perfect for locks with TTL)
✅ Watch API for real-time notifications
✅ gRPC API (modern, efficient)
✅ Active development (CNCF graduated project)
```

**Tech Specs**:
```yaml
Language: Go
Consensus: Raft
API: gRPC, HTTP/JSON
Storage: B+tree (in-memory), WAL (disk)
Cluster Size: 3, 5, 7 nodes (odd numbers for quorum)
Performance:
  - Write: 10K QPS (leader bottleneck)
  - Read: 100K QPS (linearizable from leader)
  - Latency: <10ms P99 (same DC)
Deployment: Kubernetes, Docker, systemd, AWS, GCP, Azure
```

**Lock Implementation with etcd**:
```go
// Using official etcd client (Go)
package main

import (
    "context"
    "fmt"
    "time"
    clientv3 "go.etcd.io/etcd/client/v3"
    "go.etcd.io/etcd/client/v3/concurrency"
)

func main() {
    // Connect to etcd cluster
    cli, _ := clientv3.New(clientv3.Config{
        Endpoints:   []string{"localhost:2379"},
        DialTimeout: 5 * time.Second,
    })
    defer cli.Close()

    // Create session with TTL (lease)
    session, _ := concurrency.NewSession(cli, concurrency.WithTTL(30))
    defer session.Close()

    // Create lock
    mutex := concurrency.NewMutex(session, "/locks/my-resource")

    // Acquire lock
    ctx := context.Background()
    if err := mutex.Lock(ctx); err != nil {
        panic(err)
    }
    fmt.Println("Lock acquired!")

    // Critical section
    fmt.Println("Doing work...")
    time.Sleep(10 * time.Second)

    // Release lock
    mutex.Unlock(ctx)
    fmt.Println("Lock released!")
}
```

**DevOps Deployment (Kubernetes)**:
```yaml
# etcd StatefulSet
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: etcd
  namespace: coordination
spec:
  serviceName: etcd
  replicas: 5
  selector:
    matchLabels:
      app: etcd
  template:
    metadata:
      labels:
        app: etcd
    spec:
      containers:
      - name: etcd
        image: quay.io/coreos/etcd:v3.5.10
        ports:
        - containerPort: 2379  # Client port
          name: client
        - containerPort: 2380  # Peer port
          name: peer
        env:
        - name: ETCD_NAME
          valueFrom:
            fieldRef:
              fieldPath: metadata.name
        - name: ETCD_INITIAL_CLUSTER
          value: "etcd-0=http://etcd-0.etcd:2380,etcd-1=http://etcd-1.etcd:2380,etcd-2=http://etcd-2.etcd:2380,etcd-3=http://etcd-3.etcd:2380,etcd-4=http://etcd-4.etcd:2380"
        - name: ETCD_INITIAL_CLUSTER_STATE
          value: "new"
        - name: ETCD_INITIAL_CLUSTER_TOKEN
          value: "etcd-cluster-1"
        - name: ETCD_QUOTA_BACKEND_BYTES
          value: "8589934592"  # 8GB
        volumeMounts:
        - name: etcd-data
          mountPath: /var/lib/etcd
        livenessProbe:
          httpGet:
            path: /health
            port: 2379
          initialDelaySeconds: 30
          periodSeconds: 10
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
  volumeClaimTemplates:
  - metadata:
      name: etcd-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: "fast-ssd"
      resources:
        requests:
          storage: 50Gi
```

**Monitoring (Prometheus)**:
```yaml
# Prometheus scrape config for etcd
- job_name: 'etcd'
  static_configs:
    - targets: ['etcd-0.etcd:2379', 'etcd-1.etcd:2379', 'etcd-2.etcd:2379']
  metric_relabel_configs:
    - source_labels: [__name__]
      regex: 'etcd_(server|mvcc|network|disk)_.*'
      action: keep

# Key metrics to alert on
alert_rules:
  - alert: EtcdHighFsyncDuration
    expr: histogram_quantile(0.99, rate(etcd_disk_wal_fsync_duration_seconds_bucket[5m])) > 0.5
    annotations:
      summary: "etcd fsync taking too long"

  - alert: EtcdHighCommitDuration
    expr: histogram_quantile(0.99, rate(etcd_disk_backend_commit_duration_seconds_bucket[5m])) > 0.25

  - alert: EtcdNoLeader
    expr: etcd_server_has_leader == 0
```

---

### 2. HashiCorp Consul

**What**: Service mesh + distributed key-value store with Raft consensus

**Used By**: HashiCorp products, Nomad, Vault

**Why Choose Consul**:
```
✅ Multi-datacenter support (built-in WAN federation)
✅ Service discovery + health checking (bonus features)
✅ Strong consistency (Raft consensus)
✅ Distributed locks and semaphores
✅ Easy setup with Consul Connect (service mesh)
✅ Enterprise support available
```

**Tech Specs**:
```yaml
Language: Go
Consensus: Raft
API: HTTP/JSON, gRPC (newer versions)
Features: Service discovery, health checks, KV store, distributed locks
Performance:
  - Write: 5-8K QPS
  - Read: 50K+ QPS
Deployment: Docker, Kubernetes, VMs, AWS, GCP, Azure
```

**Lock Implementation with Consul**:
```go
// Using official Consul client (Go)
package main

import (
    "fmt"
    "time"
    consulapi "github.com/hashicorp/consul/api"
)

func main() {
    // Connect to Consul
    config := consulapi.DefaultConfig()
    client, _ := consulapi.NewClient(config)

    // Create session (with TTL)
    session := client.Session()
    sessionID, _, _ := session.Create(&consulapi.SessionEntry{
        Name:      "my-lock-session",
        TTL:       "30s",  // Lock TTL
        Behavior:  "delete",  // Delete lock on session expire
        LockDelay: time.Second * 5,
    }, nil)

    // Acquire lock
    kv := client.KV()
    lockKey := "locks/my-resource"

    acquired, _, _ := kv.Acquire(&consulapi.KVPair{
        Key:     lockKey,
        Value:   []byte("owner-instance-42"),
        Session: sessionID,
    }, nil)

    if acquired {
        fmt.Println("Lock acquired!")

        // Critical section
        fmt.Println("Doing work...")
        time.Sleep(10 * time.Second)

        // Release lock
        kv.Release(&consulapi.KVPair{
            Key:     lockKey,
            Session: sessionID,
        }, nil)
        fmt.Println("Lock released!")
    } else {
        fmt.Println("Failed to acquire lock")
    }
}
```

**DevOps Deployment (Docker Compose)**:
```yaml
version: '3.8'
services:
  consul-server-1:
    image: hashicorp/consul:1.16
    container_name: consul-server-1
    command: agent -server -bootstrap-expect=3 -ui -client=0.0.0.0
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    ports:
      - "8500:8500"  # HTTP API
      - "8600:8600"  # DNS
    volumes:
      - consul-data-1:/consul/data

  consul-server-2:
    image: hashicorp/consul:1.16
    container_name: consul-server-2
    command: agent -server -join=consul-server-1 -client=0.0.0.0
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    volumes:
      - consul-data-2:/consul/data

  consul-server-3:
    image: hashicorp/consul:1.16
    container_name: consul-server-3
    command: agent -server -join=consul-server-1 -client=0.0.0.0
    environment:
      - CONSUL_BIND_INTERFACE=eth0
    volumes:
      - consul-data-3:/consul/data

volumes:
  consul-data-1:
  consul-data-2:
  consul-data-3:
```

---

### 3. Apache ZooKeeper (Legacy, but Still Widely Used)

**What**: Centralized coordination service for distributed applications

**Used By**: Kafka, Hadoop, HBase, Solr, Storm

**Why Choose ZooKeeper**:
```
✅ Battle-tested (15+ years in production)
✅ Mature ecosystem (many libraries, tools)
✅ Ephemeral nodes (auto-cleanup on disconnect)
✅ Watch mechanism (event notifications)
✅ ZAB protocol (Zookeeper Atomic Broadcast - similar to Paxos)

❌ Lower write throughput (~5K QPS vs 10K for etcd)
❌ More complex to operate
❌ Older codebase (less active development)
```

**Tech Specs**:
```yaml
Language: Java
Consensus: ZAB (Zookeeper Atomic Broadcast)
API: Java native, C bindings, REST (via curator)
Performance:
  - Write: 5K QPS
  - Read: 50K QPS
Cluster Size: 3, 5, 7 nodes
Deployment: VMs, Docker, Kubernetes
```

**Lock Implementation (Java with Curator)**:
```java
// Using Apache Curator (high-level ZooKeeper client)
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.recipes.locks.InterProcessMutex;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.util.concurrent.TimeUnit;

public class ZooKeeperLockExample {
    public static void main(String[] args) throws Exception {
        // Connect to ZooKeeper ensemble
        CuratorFramework client = CuratorFrameworkFactory.newClient(
            "localhost:2181,localhost:2182,localhost:2183",
            new ExponentialBackoffRetry(1000, 3)
        );
        client.start();

        // Create distributed lock
        String lockPath = "/locks/my-resource";
        InterProcessMutex lock = new InterProcessMutex(client, lockPath);

        try {
            // Acquire lock (with timeout)
            if (lock.acquire(30, TimeUnit.SECONDS)) {
                System.out.println("Lock acquired!");

                try {
                    // Critical section
                    System.out.println("Doing work...");
                    Thread.sleep(10000);
                } finally {
                    // Always release in finally block
                    lock.release();
                    System.out.println("Lock released!");
                }
            } else {
                System.out.println("Failed to acquire lock within timeout");
            }
        } finally {
            client.close();
        }
    }
}
```

---

### 4. Redis (with Redlock Algorithm)

**What**: In-memory data structure store, used for distributed locking via Redlock

**Used By**: Twitter, GitHub, Snapchat, Airbnb (for best-effort locks)

**Why Choose Redis**:
```
✅ Extremely fast (<1ms latency)
✅ Simple to deploy and operate
✅ Familiar tool (already in many stacks)
✅ Good for non-critical locks (rate limiting, caching)

❌ NOT strongly consistent (AP, not CP in CAP)
❌ Redlock algorithm debated (Martin Kleppmann critique)
❌ Vulnerable to clock skew issues
❌ Not safe under all partition scenarios
```

**When to Use Redis Locks**:
- Rate limiting (best-effort)
- Preventing duplicate job execution (non-critical)
- Temporary exclusive access (short-lived)

**When NOT to Use**:
- Financial transactions (need strong consistency)
- Leader election (split-brain risk)
- Critical resource coordination

**Tech Specs**:
```yaml
Language: C
Consistency: Eventually consistent (master-replica async replication)
API: RESP protocol (Redis Serialization Protocol)
Performance:
  - Ops: 100K+ QPS (single instance)
  - Latency: <1ms
Cluster: Redis Cluster (sharding) or Sentinel (HA)
```

**Redlock Implementation**:
```python
# Using redlock-py library
from redlock import Redlock

# Connect to 5 independent Redis instances
redlock = Redlock([
    {"host": "redis1", "port": 6379, "db": 0},
    {"host": "redis2", "port": 6379, "db": 0},
    {"host": "redis3", "port": 6379, "db": 0},
    {"host": "redis4", "port": 6379, "db": 0},
    {"host": "redis5", "port": 6379, "db": 0},
])

# Acquire lock
resource = "payment:order:12345"
ttl = 30000  # 30 seconds

lock = redlock.lock(resource, ttl)

if lock:
    print(f"Lock acquired: {lock}")
    try:
        # Critical section
        print("Doing work...")
        time.sleep(5)
    finally:
        # Release lock
        redlock.unlock(lock)
        print("Lock released")
else:
    print("Failed to acquire lock")
```

**Redlock Algorithm Steps**:
```
1. Get current time (milliseconds)
2. Try to acquire lock in all N instances sequentially:
   - Use SET resource_name random_value NX PX ttl
3. Calculate time elapsed
4. Lock acquired only if:
   - Acquired in quorum (>N/2 instances)
   - Elapsed time < lock validity time
5. If not acquired, release all instances
6. If acquired, use it and release before expiry
```

**Redis Cluster Deployment (Kubernetes)**:
```yaml
# Using Redis Operator
apiVersion: redis.redis.opstreelabs.in/v1beta1
kind: RedisCluster
metadata:
  name: redis-cluster
spec:
  clusterSize: 6  # 3 masters + 3 replicas
  kubernetesConfig:
    image: redis:7.0
    resources:
      requests:
        cpu: 100m
        memory: 128Mi
      limits:
        cpu: 1
        memory: 1Gi
  redisExporter:
    enabled: true
    image: oliver006/redis_exporter:latest
  storage:
    volumeClaimTemplate:
      spec:
        accessModes: ["ReadWriteOnce"]
        storageClassName: "fast-ssd"
        resources:
          requests:
            storage: 10Gi
```

---

## 🎯 PART 2: Lock Management SDKs & Libraries

### Language-Specific Libraries

**Go**:
```go
// etcd client
go get go.etcd.io/etcd/client/v3

// Consul client
go get github.com/hashicorp/consul/api

// Redis with Redlock
go get github.com/go-redsync/redsync/v4
```

**Java**:
```xml
<!-- Apache Curator (ZooKeeper) -->
<dependency>
    <groupId>org.apache.curator</groupId>
    <artifactId>curator-recipes</artifactId>
    <version>5.5.0</version>
</dependency>

<!-- Redisson (Redis) -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson</artifactId>
    <version>3.23.5</version>
</dependency>
```

**Python**:
```bash
# etcd client
pip install etcd3

# Consul client
pip install python-consul

# Redis Redlock
pip install redlock-py

# Kazoo (ZooKeeper)
pip install kazoo
```

**Node.js**:
```bash
# etcd client
npm install etcd3

# Consul client
npm install consul

# Redlock
npm install redlock
```

---

## 🎯 PART 3: DevOps & Infrastructure Tools

### Monitoring & Observability

**Prometheus + Grafana**:
```yaml
# Prometheus scrape targets for distributed locks
scrape_configs:
  # etcd metrics
  - job_name: 'etcd'
    static_configs:
      - targets: ['etcd:2379']
    metrics_path: /metrics

  # Consul metrics
  - job_name: 'consul'
    static_configs:
      - targets: ['consul:8500']
    metrics_path: /v1/agent/metrics

  # Custom lock service metrics
  - job_name: 'lock-service'
    kubernetes_sd_configs:
      - role: pod
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        regex: lock-service
        action: keep
```

**Grafana Dashboards** (pre-built):
- **etcd**: https://grafana.com/grafana/dashboards/3070
- **Consul**: https://grafana.com/grafana/dashboards/13396
- **Redis**: https://grafana.com/grafana/dashboards/763

**Key Metrics to Dashboard**:
```
Lock Service:
  - lock_acquire_total (rate)
  - lock_acquire_duration_seconds (histogram)
  - lock_active_count (gauge)
  - lock_contention_ratio (failed / total)
  - lock_hold_duration_seconds (histogram)

etcd:
  - etcd_server_has_leader (gauge)
  - etcd_server_proposals_committed_total (counter)
  - etcd_disk_wal_fsync_duration_seconds (histogram)
  - etcd_network_peer_round_trip_time_seconds (histogram)

Consensus:
  - raft_leader_elections_total (counter)
  - raft_commit_latency_seconds (histogram)
```

---

### Distributed Tracing

**Jaeger / Zipkin Integration**:
```go
// OpenTelemetry tracing for lock operations
import (
    "go.opentelemetry.io/otel"
    "go.opentelemetry.io/otel/trace"
)

func acquireLock(ctx context.Context, resource string) error {
    tracer := otel.Tracer("lock-service")
    ctx, span := tracer.Start(ctx, "lock.acquire",
        trace.WithAttributes(
            attribute.String("resource", resource),
        ))
    defer span.End()

    // Acquire lock
    start := time.Now()
    lock, err := lockManager.Acquire(ctx, resource, 30*time.Second)
    duration := time.Since(start)

    span.SetAttributes(
        attribute.Int64("duration_ms", duration.Milliseconds()),
        attribute.Bool("success", err == nil),
    )

    if err != nil {
        span.RecordError(err)
        return err
    }

    // Record fencing token
    span.SetAttributes(attribute.Int64("fencing_token", lock.Token))
    return nil
}
```

---

### CI/CD & Testing

**Testing Distributed Locks**:
```go
// Integration test with testcontainers
import (
    "github.com/testcontainers/testcontainers-go"
    "github.com/testcontainers/testcontainers-go/wait"
)

func TestDistributedLock(t *testing.T) {
    // Start etcd container
    ctx := context.Background()
    req := testcontainers.ContainerRequest{
        Image:        "quay.io/coreos/etcd:v3.5.10",
        ExposedPorts: []string{"2379/tcp"},
        Cmd: []string{
            "etcd",
            "--advertise-client-urls", "http://0.0.0.0:2379",
            "--listen-client-urls", "http://0.0.0.0:2379",
        },
        WaitingFor: wait.ForLog("ready to serve client requests"),
    }

    etcdContainer, _ := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
        ContainerRequest: req,
        Started:          true,
    })
    defer etcdContainer.Terminate(ctx)

    // Get etcd endpoint
    endpoint, _ := etcdContainer.Endpoint(ctx, "")

    // Test lock acquisition
    lockService := NewLockService(endpoint)
    lock, err := lockService.Acquire("test-resource", "client-1", 30*time.Second)
    assert.NoError(t, err)
    assert.NotNil(t, lock)

    // Test lock held by another
    lock2, err := lockService.Acquire("test-resource", "client-2", 30*time.Second)
    assert.Error(t, err)
    assert.Nil(t, lock2)

    // Release and retry
    lockService.Release("test-resource", "client-1")
    lock3, err := lockService.Acquire("test-resource", "client-2", 30*time.Second)
    assert.NoError(t, err)
    assert.NotNil(t, lock3)
}
```

**Chaos Engineering (Chaos Mesh)**:
```yaml
# Test lock behavior under network partition
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: etcd-partition
spec:
  action: partition
  mode: all
  selector:
    namespaces:
      - coordination
    labelSelectors:
      app: etcd
  direction: both
  duration: "30s"
  scheduler:
    cron: "*/10 * * * *"  # Every 10 minutes
```

---

## 🎯 PART 4: Cloud-Managed Services

### AWS Solutions

**1. Amazon DynamoDB with Conditional Writes**:
```python
# Using DynamoDB for distributed locks
import boto3
from botocore.exceptions import ClientError

dynamodb = boto3.resource('dynamodb')
table = dynamodb.Table('distributed-locks')

def acquire_lock(resource, owner, ttl_seconds=30):
    """
    Acquire lock using DynamoDB conditional write.
    """
    import time
    expires_at = int(time.time()) + ttl_seconds

    try:
        # Conditional write: only succeed if lock doesn't exist
        table.put_item(
            Item={
                'resource_name': resource,
                'owner_id': owner,
                'expires_at': expires_at,
                'fencing_token': get_next_token(),
            },
            ConditionExpression='attribute_not_exists(resource_name) OR expires_at < :now',
            ExpressionAttributeValues={
                ':now': int(time.time())
            }
        )
        return True
    except ClientError as e:
        if e.response['Error']['Code'] == 'ConditionalCheckFailedException':
            return False  # Lock held by another
        raise
```

**2. AWS Systems Manager Parameter Store**:
```python
# Simple lock using SSM Parameter Store
import boto3

ssm = boto3.client('ssm')

def acquire_lock_ssm(resource, owner):
    try:
        # Try to create parameter (fails if exists)
        ssm.put_parameter(
            Name=f'/locks/{resource}',
            Value=owner,
            Type='String',
            Overwrite=False  # Fail if exists
        )
        return True
    except ssm.exceptions.ParameterAlreadyExists:
        return False
```

---

### Google Cloud Solutions

**1. Cloud Spanner (Strong Consistency)**:
```python
# Using Cloud Spanner for locks
from google.cloud import spanner

client = spanner.Client()
instance = client.instance('lock-instance')
database = instance.database('locks')

def acquire_lock_spanner(resource, owner, ttl_seconds=30):
    import time
    expires_at = int(time.time()) + ttl_seconds

    def acquire_txn(transaction):
        # Check if lock exists
        result = transaction.execute_sql(
            "SELECT owner_id, expires_at FROM locks WHERE resource_name = @resource",
            params={'resource': resource},
            param_types={'resource': spanner.param_types.STRING}
        )

        row = list(result)
        if row:
            # Lock exists, check if expired
            if row[0][1] > time.time():
                return False  # Still held

        # Acquire or renew
        transaction.execute_update(
            "INSERT OR REPLACE INTO locks (resource_name, owner_id, expires_at) VALUES (@resource, @owner, @expires)",
            params={'resource': resource, 'owner': owner, 'expires': expires_at},
            param_types={
                'resource': spanner.param_types.STRING,
                'owner': spanner.param_types.STRING,
                'expires': spanner.param_types.INT64
            }
        )
        return True

    with database.snapshot() as snapshot:
        return database.run_in_transaction(acquire_txn)
```

---

### Azure Solutions

**1. Azure Cosmos DB with Etag**:
```python
# Using Cosmos DB for optimistic locking
from azure.cosmos import CosmosClient, exceptions

client = CosmosClient(url, credential=key)
database = client.get_database_client('locks-db')
container = database.get_container_client('locks')

def acquire_lock_cosmos(resource, owner, ttl_seconds=30):
    import time
    expires_at = int(time.time()) + ttl_seconds

    try:
        # Try to create lock document
        container.create_item({
            'id': resource,
            'owner_id': owner,
            'expires_at': expires_at
        })
        return True
    except exceptions.CosmosResourceExistsError:
        # Lock exists, check if expired
        lock = container.read_item(item=resource, partition_key=resource)
        if lock['expires_at'] < time.time():
            # Expired, replace with etag
            container.replace_item(
                item=resource,
                body={'id': resource, 'owner_id': owner, 'expires_at': expires_at},
                etag=lock['_etag'],
                match_condition=exceptions.MatchConditions.IfNotModified
            )
            return True
        return False
```

---

## 🎯 PART 5: Production Deployment Patterns

### Kubernetes Deployment (Full Stack)

```yaml
# Complete Kubernetes deployment for lock service
---
# etcd StatefulSet (already shown above)

---
# Lock Manager Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: lock-manager
  namespace: coordination
spec:
  replicas: 3
  selector:
    matchLabels:
      app: lock-manager
  template:
    metadata:
      labels:
        app: lock-manager
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "9090"
    spec:
      containers:
      - name: lock-manager
        image: mycompany/lock-manager:v1.0.0
        ports:
        - containerPort: 8080  # gRPC
          name: grpc
        - containerPort: 9090  # Metrics
          name: metrics
        env:
        - name: ETCD_ENDPOINTS
          value: "etcd-0.etcd:2379,etcd-1.etcd:2379,etcd-2.etcd:2379"
        - name: LOG_LEVEL
          value: "info"
        - name: LOCK_DEFAULT_TTL
          value: "30s"
        resources:
          requests:
            memory: "256Mi"
            cpu: "250m"
          limits:
            memory: "512Mi"
            cpu: "500m"
        livenessProbe:
          grpc:
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 10
        readinessProbe:
          grpc:
            port: 8080
          initialDelaySeconds: 5
          periodSeconds: 5

---
# Service
apiVersion: v1
kind: Service
metadata:
  name: lock-manager
  namespace: coordination
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: grpc
  selector:
    app: lock-manager

---
# ServiceMonitor for Prometheus
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: lock-manager
  namespace: coordination
spec:
  selector:
    matchLabels:
      app: lock-manager
  endpoints:
  - port: metrics
    interval: 15s
```

---

### Terraform (Infrastructure as Code)

```hcl
# Deploy etcd cluster on AWS
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

# VPC and Networking
resource "aws_vpc" "lock_cluster" {
  cidr_block = "10.0.0.0/16"
  tags = {
    Name = "lock-cluster-vpc"
  }
}

resource "aws_subnet" "lock_subnet" {
  count             = 3
  vpc_id            = aws_vpc.lock_cluster.id
  cidr_block        = "10.0.${count.index}.0/24"
  availability_zone = data.aws_availability_zones.available.names[count.index]
}

# EC2 instances for etcd
resource "aws_instance" "etcd" {
  count         = 5
  ami           = "ami-0c55b159cbfafe1f0"  # Amazon Linux 2
  instance_type = "t3.medium"
  subnet_id     = aws_subnet.lock_subnet[count.index % 3].id

  user_data = <<-EOF
              #!/bin/bash
              yum install -y docker
              systemctl start docker

              docker run -d \
                --name etcd \
                --network host \
                quay.io/coreos/etcd:v3.5.10 \
                etcd \
                --name etcd-${count.index} \
                --initial-advertise-peer-urls http://$(hostname -i):2380 \
                --listen-peer-urls http://0.0.0.0:2380 \
                --listen-client-urls http://0.0.0.0:2379 \
                --advertise-client-urls http://$(hostname -i):2379 \
                --initial-cluster ${join(",", [for i in range(5) : "etcd-${i}=http://etcd-${i}:2380"])}
              EOF

  tags = {
    Name = "etcd-${count.index}"
    Role = "coordination"
  }
}

# Application Load Balancer
resource "aws_lb" "lock_service" {
  name               = "lock-service-lb"
  internal           = true
  load_balancer_type = "application"
  subnets            = aws_subnet.lock_subnet[*].id
}

resource "aws_lb_target_group" "etcd" {
  name     = "etcd-targets"
  port     = 2379
  protocol = "HTTP"
  vpc_id   = aws_vpc.lock_cluster.id

  health_check {
    path = "/health"
    port = 2379
  }
}

resource "aws_lb_target_group_attachment" "etcd" {
  count            = 5
  target_group_arn = aws_lb_target_group.etcd.arn
  target_id        = aws_instance.etcd[count.index].id
  port             = 2379
}
```

---

## 🎯 PART 6: Industry Best Practices

### Configuration Management

**etcd Tuning**:
```bash
# /etc/etcd/etcd.conf.yml
name: 'etcd-1'
data-dir: /var/lib/etcd
listen-client-urls: http://0.0.0.0:2379
advertise-client-urls: http://10.0.1.10:2379
listen-peer-urls: http://0.0.0.0:2380
initial-advertise-peer-urls: http://10.0.1.10:2380

# Performance tuning
heartbeat-interval: 100  # ms (default)
election-timeout: 1000   # ms (default)
snapshot-count: 10000
max-snapshots: 5
max-wals: 5

# Storage
quota-backend-bytes: 8589934592  # 8GB

# Logging
log-level: info
logger: zap

# Security (production)
client-transport-security:
  cert-file: /etc/etcd/certs/server.crt
  key-file: /etc/etcd/certs/server.key
  client-cert-auth: true
  trusted-ca-file: /etc/etcd/certs/ca.crt
```

### Backup & Disaster Recovery

**etcd Backup**:
```bash
# Automated backup script
#!/bin/bash
ETCDCTL_API=3 etcdctl \
  --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/etcd/certs/ca.crt \
  --cert=/etc/etcd/certs/server.crt \
  --key=/etc/etcd/certs/server.key \
  snapshot save /backup/etcd-snapshot-$(date +%Y%m%d-%H%M%S).db

# Upload to S3
aws s3 cp /backup/etcd-snapshot-*.db s3://my-backup-bucket/etcd/

# Retention policy (keep last 7 days)
find /backup -name "etcd-snapshot-*.db" -mtime +7 -delete
```

**etcd Restore**:
```bash
# Restore from snapshot
ETCDCTL_API=3 etcdctl snapshot restore /backup/etcd-snapshot-20231115-120000.db \
  --data-dir=/var/lib/etcd-restore \
  --name=etcd-1 \
  --initial-cluster=etcd-1=http://10.0.1.10:2380,etcd-2=http://10.0.1.11:2380,etcd-3=http://10.0.1.12:2380 \
  --initial-advertise-peer-urls=http://10.0.1.10:2380
```

---

## 🎯 PART 7: Cost Estimation

### AWS Cost (Monthly)

```
Lock Service Infrastructure:
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
5× EC2 t3.medium (etcd cluster)
  - 2 vCPU, 4GB RAM each
  - Cost: $30/month × 5 = $150/month

3× EC2 t3.small (lock manager)
  - 2 vCPU, 2GB RAM each
  - Cost: $15/month × 3 = $45/month

EBS Storage (50GB per etcd node)
  - gp3 SSD: $4/GB-month
  - Cost: $4 × 50 × 5 = $1,000/month

Application Load Balancer
  - Cost: $16/month + $0.008/LCU-hour
  - Estimated: $50/month

CloudWatch Logs & Metrics
  - Cost: $30/month

Total: ~$1,275/month
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Managed Service Alternative (DynamoDB):
  - 10K write RCUs: $65/month
  - 10K read RCUs: $13/month
  - Storage (100GB): $25/month
Total: ~$103/month (87% cheaper!) ✅
```

---

## 🎯 Summary: Tech Stack Decision Matrix

| **Criterion** | **etcd** | **Consul** | **ZooKeeper** | **Redis** | **DynamoDB** |
|---------------|----------|------------|---------------|-----------|--------------|
| **Consistency** | Strong (CP) | Strong (CP) | Strong (CP) | Eventual (AP) | Strong (CP) |
| **Performance** | 10K write QPS | 5-8K QPS | 5K QPS | 100K+ QPS | 10K+ QPS |
| **Latency** | <10ms | <15ms | <20ms | <1ms | <10ms |
| **Ease of Use** | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐ |
| **Operational** | Medium | Medium | High | Low | Very Low |
| **Cost** | Medium | Medium | Medium | Low | Low |
| **Best For** | K8s, critical locks | Service mesh | Legacy (Hadoop) | Fast, non-critical | Serverless, managed |

**Recommendation**:
- **New projects**: etcd (modern, performant, K8s native)
- **Service mesh**: Consul (extra features)
- **Best-effort locks**: Redis (fastest)
- **Serverless/managed**: DynamoDB (zero ops)
- **Legacy Hadoop**: ZooKeeper (ecosystem integration)
