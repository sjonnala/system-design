# 🛠️ Stock Exchange Tech Stack & DevOps Solutions

## Production-Grade Technology Stack for Financial Trading Systems

**Industry References**: NASDAQ, NYSE, CME Group, LMAX Exchange, Interactive Brokers, Coinbase, Robinhood

---

## 🎯 Table of Contents

1. [Core Matching Engine Stack](#1-core-matching-engine-stack)
2. [Application Services](#2-application-services)
3. [Data Layer](#3-data-layer)
4. [Message Queue & Event Streaming](#4-message-queue--event-streaming)
5. [Networking & Low-Latency](#5-networking--low-latency)
6. [Container Orchestration](#6-container-orchestration)
7. [Monitoring & Observability](#7-monitoring--observability)
8. [Security & Compliance](#8-security--compliance)
9. [CI/CD Pipeline](#9-cicd-pipeline)
10. [Disaster Recovery](#10-disaster-recovery)
11. [Cost Optimization](#11-cost-optimization)
12. [Real-World Case Studies](#12-real-world-case-studies)

---

## 1. Core Matching Engine Stack

### Programming Languages

#### C++17/20 (Primary for Matching Engine)

**Why C++:**
- Deterministic memory management (no GC pauses)
- Zero-cost abstractions
- Direct hardware access
- Sub-microsecond latency achievable

**Key Libraries:**
```cpp
// Matching Engine Example
#include <boost/lockfree/spsc_queue.hpp>  // Lock-free queues
#include <boost/container/flat_map.hpp>   // Cache-friendly maps
#include <folly/concurrency/ConcurrentHashMap.h>  // Facebook's concurrent containers

// Ultra-low latency timer
#include <chrono>
using namespace std::chrono;
auto start = high_resolution_clock::now();
```

**Production Libraries:**
- **Boost**: Lock-free data structures, ASIO for networking
- **Folly**: Facebook's C++ library (concurrent containers)
- **Intel TBB**: Threading Building Blocks (parallel algorithms)
- **DPDK**: Data Plane Development Kit (kernel bypass networking)

**Compiler Optimizations:**
```bash
# GCC/Clang flags for maximum performance
g++ -std=c++17 -O3 -march=native -mtune=native \
    -flto -ffast-math -funroll-loops \
    -DNDEBUG -Wall -Wextra \
    matching_engine.cpp -o matching_engine

# Profile-guided optimization (PGO)
# Step 1: Compile with profiling
g++ -fprofile-generate matching_engine.cpp -o matching_engine
# Step 2: Run with representative workload
./matching_engine < production_orders.dat
# Step 3: Recompile with profile data
g++ -fprofile-use matching_engine.cpp -o matching_engine
```

#### Rust (Alternative for Matching Engine)

**Why Rust:**
- Memory safety without GC
- Fearless concurrency (no data races)
- Performance comparable to C++
- Modern tooling (Cargo, clippy)

**Production Example:**
```rust
use crossbeam::channel::{bounded, Sender, Receiver};
use parking_lot::RwLock;  // Faster than std::sync::RwLock
use dashmap::DashMap;     // Concurrent HashMap

pub struct OrderBook {
    bids: DashMap<Price, VecDeque<Order>>,
    asks: DashMap<Price, VecDeque<Order>>,
}

impl OrderBook {
    pub fn add_order(&self, order: Order) -> Option<Vec<Trade>> {
        // Lock-free insertion
        match order.side {
            Side::Buy => self.match_buy_order(order),
            Side::Sell => self.match_sell_order(order),
        }
    }
}
```

**Rust Libraries:**
- **crossbeam**: Lock-free data structures
- **tokio**: Async runtime (for network I/O)
- **serde**: Serialization (zero-copy deserialization)
- **dashmap**: Concurrent HashMap
- **rayon**: Data parallelism

#### Java 17+ (Application Services)

**Why Java for Services (not matching engine):**
- Rich ecosystem (Spring Boot, Kafka, etc.)
- Excellent tooling (IntelliJ, profilers)
- Battle-tested in production (NYSE uses Java)
- Modern GC (ZGC, Shenandoah) with <10ms pauses

**GC Tuning for Low Latency:**
```bash
# ZGC (Z Garbage Collector) - Sub-10ms pauses
java -XX:+UseZGC \
     -XX:ZCollectionInterval=5 \
     -XX:ZAllocationSpikeTolerance=2 \
     -Xmx32g -Xms32g \
     -XX:+AlwaysPreTouch \
     -XX:+UseTransparentHugePages \
     -jar order-service.jar

# Shenandoah GC (Alternative)
java -XX:+UseShenandoahGC \
     -Xmx32g -Xms32g \
     -jar order-service.jar
```

**Spring Boot Configuration:**
```yaml
spring:
  application:
    name: order-service
  datasource:
    hikari:
      maximum-pool-size: 50
      connection-timeout: 5000
      idle-timeout: 300000
  kafka:
    bootstrap-servers: kafka-1:9092,kafka-2:9092
    producer:
      acks: all
      retries: 3
      compression-type: lz4
```

---

## 2. Application Services

### API Gateway

#### Kong (Open Source)

```yaml
# kong.yml
_format_version: "2.1"

services:
  - name: order-service
    url: http://order-service:8080
    routes:
      - name: place-order
        paths:
          - /api/v1/orders
        methods:
          - POST
    plugins:
      - name: rate-limiting
        config:
          minute: 100
          policy: local
      - name: jwt
        config:
          key_claim_name: kid
      - name: request-size-limiting
        config:
          allowed_payload_size: 1
```

**Alternatives:**
- **AWS API Gateway**: Managed, serverless
- **Envoy Proxy**: High-performance, used in service mesh
- **NGINX Plus**: Commercial version with advanced features

### Order Service (Spring Boot)

```java
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @PostMapping
    @Transactional(isolation = Isolation.SERIALIZABLE)
    public ResponseEntity<OrderResponse> placeOrder(
        @Valid @RequestBody OrderRequest request,
        @AuthenticationPrincipal UserDetails user
    ) {
        // Pre-trade validation
        Account account = accountService.getAccount(user.getAccountId());
        if (account.getCashBalance().compareTo(request.getOrderValue()) < 0) {
            throw new InsufficientFundsException();
        }

        // Create order
        Order order = orderService.createOrder(request, account);

        // Publish to matching engine (Kafka)
        kafkaTemplate.send("orders", order);

        return ResponseEntity.status(HttpStatus.CREATED)
            .body(OrderResponse.from(order));
    }
}
```

### Trade Execution Service

```java
@Service
public class TradeExecutionService {

    @Transactional(isolation = Isolation.SERIALIZABLE, propagation = Propagation.REQUIRED)
    public Trade executeTrade(Order buyOrder, Order sellOrder, BigDecimal price, long quantity) {

        // 1. Create trade record
        Trade trade = Trade.builder()
            .symbol(buyOrder.getSymbol())
            .buyOrderId(buyOrder.getOrderId())
            .sellOrderId(sellOrder.getOrderId())
            .price(price)
            .quantity(quantity)
            .tradeValue(price.multiply(BigDecimal.valueOf(quantity)))
            .executedAt(Instant.now())
            .settlementDate(LocalDate.now().plusDays(2))  // T+2
            .build();

        tradeRepository.save(trade);

        // 2. Update buyer account
        Account buyerAccount = accountRepository.findByIdWithLock(buyOrder.getAccountId());
        buyerAccount.debit(trade.getTradeValue());
        buyerAccount.increasePosition(trade.getSymbol(), quantity);

        // 3. Update seller account
        Account sellerAccount = accountRepository.findByIdWithLock(sellOrder.getAccountId());
        sellerAccount.credit(trade.getTradeValue());
        sellerAccount.decreasePosition(trade.getSymbol(), quantity);

        // 4. Create ledger entries (double-entry bookkeeping)
        ledgerService.recordTrade(trade, buyerAccount, sellerAccount);

        // 5. Publish trade event
        applicationEventPublisher.publishEvent(new TradeExecutedEvent(trade));

        return trade;
    }
}
```

---

## 3. Data Layer

### PostgreSQL 15+ (ACID Transactions)

**Why PostgreSQL:**
- ACID compliant
- SERIALIZABLE isolation level
- Streaming replication
- Mature, battle-tested

**Production Configuration:**
```ini
# postgresql.conf

# Memory Configuration
shared_buffers = 16GB                    # 25% of RAM
effective_cache_size = 48GB              # 75% of RAM
work_mem = 256MB                         # For complex queries
maintenance_work_mem = 2GB

# Checkpointing
checkpoint_completion_target = 0.9
wal_buffers = 16MB
max_wal_size = 4GB
min_wal_size = 1GB

# Connection Management
max_connections = 500
shared_preload_libraries = 'pg_stat_statements'

# Write-Ahead Log (Durability)
wal_level = replica
wal_log_hints = on
fsync = on                               # CRITICAL for ACID
synchronous_commit = on                  # Wait for WAL sync
full_page_writes = on

# Replication (Synchronous for ACID)
synchronous_standby_names = 'standby1'   # Wait for 1 replica
max_wal_senders = 10
wal_keep_size = 1GB

# Query Optimization
random_page_cost = 1.1                   # For SSD
effective_io_concurrency = 200
```

**Replication Setup (Streaming):**
```bash
# Primary server
# Create replication user
CREATE USER replicator REPLICATION LOGIN ENCRYPTED PASSWORD 'secret';

# pg_hba.conf (allow replication)
host replication replicator 10.0.1.0/24 md5

# Standby server (standby1)
# Create base backup
pg_basebackup -h primary-db -U replicator -D /var/lib/postgresql/data -P --wal-method=stream

# standby.signal (mark as standby)
touch /var/lib/postgresql/data/standby.signal

# postgresql.conf on standby
primary_conninfo = 'host=primary-db port=5432 user=replicator password=secret'
promote_trigger_file = '/tmp/promote_standby'
```

**Partitioning Strategy:**
```sql
-- Partition orders by symbol (HASH)
CREATE TABLE orders (
    order_id BIGSERIAL,
    account_id BIGINT NOT NULL,
    symbol VARCHAR(10) NOT NULL,
    -- other columns
) PARTITION BY HASH (symbol);

-- Create 16 partitions
CREATE TABLE orders_p0 PARTITION OF orders FOR VALUES WITH (MODULUS 16, REMAINDER 0);
CREATE TABLE orders_p1 PARTITION OF orders FOR VALUES WITH (MODULUS 16, REMAINDER 1);
-- ... up to p15

-- Partition trades by date (RANGE)
CREATE TABLE trades (
    trade_id BIGSERIAL,
    executed_at TIMESTAMP NOT NULL,
    -- other columns
) PARTITION BY RANGE (executed_at);

-- Monthly partitions
CREATE TABLE trades_2025_01 PARTITION OF trades
FOR VALUES FROM ('2025-01-01') TO ('2025-02-01');

CREATE TABLE trades_2025_02 PARTITION OF trades
FOR VALUES FROM ('2025-02-01') TO ('2025-03-01');
```

### TimescaleDB (Market Data)

```sql
-- Create hypertable for market data
CREATE TABLE market_data (
    symbol VARCHAR(10) NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    open DECIMAL(20, 4),
    high DECIMAL(20, 4),
    low DECIMAL(20, 4),
    close DECIMAL(20, 4),
    volume BIGINT
);

-- Convert to hypertable (automatic partitioning by time)
SELECT create_hypertable('market_data', 'timestamp');

-- Continuous aggregate (pre-compute OHLCV for 1-minute bars)
CREATE MATERIALIZED VIEW ohlcv_1min
WITH (timescaledb.continuous) AS
SELECT symbol,
       time_bucket('1 minute', timestamp) AS bucket,
       first(open, timestamp) AS open,
       max(high) AS high,
       min(low) AS low,
       last(close, timestamp) AS close,
       sum(volume) AS volume
FROM market_data
GROUP BY symbol, bucket;

-- Compression (10x space savings)
ALTER TABLE market_data SET (
  timescaledb.compress,
  timescaledb.compress_segmentby = 'symbol',
  timescaledb.compress_orderby = 'timestamp DESC'
);

-- Compress data older than 7 days
SELECT add_compression_policy('market_data', INTERVAL '7 days');
```

### Redis Cluster (Caching)

```yaml
# redis.conf
port 6379
cluster-enabled yes
cluster-config-file nodes.conf
cluster-node-timeout 5000
appendonly yes
appendfsync everysec

# Memory management
maxmemory 10gb
maxmemory-policy allkeys-lru

# Performance
tcp-backlog 511
timeout 0
tcp-keepalive 300
```

**Cluster Setup (3 masters, 3 replicas):**
```bash
# Create cluster
redis-cli --cluster create \
  10.0.1.1:6379 10.0.1.2:6379 10.0.1.3:6379 \
  10.0.1.4:6379 10.0.1.5:6379 10.0.1.6:6379 \
  --cluster-replicas 1
```

**Caching Strategy:**
```java
@Service
public class AccountService {

    @Cacheable(value = "accounts", key = "#accountId")
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @CacheEvict(value = "accounts", key = "#account.accountId")
    public Account updateAccount(Account account) {
        return accountRepository.save(account);
    }
}
```

---

## 4. Message Queue & Event Streaming

### Apache Kafka

**Why Kafka:**
- High throughput (millions of messages/sec)
- Durable (replication, WAL)
- Ordered within partition
- Perfect for event sourcing

**Production Configuration:**
```properties
# server.properties

# Broker ID
broker.id=1

# Listeners
listeners=PLAINTEXT://10.0.1.1:9092
advertised.listeners=PLAINTEXT://kafka-1.internal:9092

# Log Configuration
log.dirs=/var/kafka-logs
num.partitions=32
default.replication.factor=3
min.insync.replicas=2

# Performance
num.network.threads=8
num.io.threads=16
socket.send.buffer.bytes=102400
socket.receive.buffer.bytes=102400
socket.request.max.bytes=104857600

# Retention
log.retention.hours=720  # 30 days
log.segment.bytes=1073741824
log.retention.check.interval.ms=300000

# Replication
replica.lag.time.max.ms=10000
unclean.leader.election.enable=false  # CRITICAL for data integrity
```

**Topic Design:**
```bash
# Orders topic (partitioned by symbol for parallelism)
kafka-topics.sh --create --topic orders \
  --partitions 32 \
  --replication-factor 3 \
  --config min.insync.replicas=2 \
  --config retention.ms=2592000000  # 30 days

# Trades topic
kafka-topics.sh --create --topic trades \
  --partitions 32 \
  --replication-factor 3 \
  --config min.insync.replicas=2

# Market data topic (high volume)
kafka-topics.sh --create --topic market-data \
  --partitions 64 \
  --replication-factor 3 \
  --config compression.type=lz4
```

**Producer Configuration (Java):**
```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");

// Reliability settings
props.put("acks", "all");  // Wait for all replicas
props.put("retries", 3);
props.put("max.in.flight.requests.per.connection", 1);  // Ordering guarantee
props.put("enable.idempotence", true);  // Exactly-once semantics

// Performance
props.put("compression.type", "lz4");
props.put("batch.size", 16384);
props.put("linger.ms", 10);

KafkaProducer<String, Trade> producer = new KafkaProducer<>(props);
```

**Consumer Configuration:**
```java
Properties props = new Properties();
props.put("bootstrap.servers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
props.put("group.id", "settlement-service");
props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
props.put("value.deserializer", "io.confluent.kafka.serializers.KafkaAvroDeserializer");

// Reliability
props.put("enable.auto.commit", false);  // Manual commit for exactly-once
props.put("isolation.level", "read_committed");  // Skip uncommitted transactions

// Performance
props.put("fetch.min.bytes", 1024);
props.put("max.poll.records", 500);

KafkaConsumer<String, Trade> consumer = new KafkaConsumer<>(props);
consumer.subscribe(Arrays.asList("trades"));
```

---

## 5. Networking & Low-Latency

### Kernel Bypass (DPDK)

**DPDK (Data Plane Development Kit):**
- Bypass kernel network stack
- User-space packet processing
- <10μs network latency

```c
// DPDK initialization
#include <rte_eal.h>
#include <rte_ethdev.h>

int main(int argc, char **argv) {
    // Initialize DPDK
    int ret = rte_eal_init(argc, argv);
    if (ret < 0)
        rte_exit(EXIT_FAILURE, "Cannot init EAL\n");

    // Get number of ports
    unsigned nb_ports = rte_eth_dev_count_avail();

    // Configure port
    struct rte_eth_conf port_conf = {
        .rxmode = {
            .max_rx_pkt_len = RTE_ETHER_MAX_LEN,
        },
    };

    rte_eth_dev_configure(port_id, 1, 1, &port_conf);

    // Start packet reception loop
    while (1) {
        struct rte_mbuf *pkts_burst[32];
        const uint16_t nb_rx = rte_eth_rx_burst(port_id, 0, pkts_burst, 32);

        for (uint16_t i = 0; i < nb_rx; i++) {
            process_packet(pkts_burst[i]);
        }
    }
}
```

### Solarflare NICs

**Configuration:**
```bash
# Install Solarflare driver
sudo apt-get install sfptpd

# Enable kernel bypass
onload --profile=latency ./matching_engine

# CPU pinning for network IRQs
sudo set_irq_affinity.sh 0-3 eth0  # Pin IRQs to cores 0-3
```

### Multicast for Market Data

```cpp
// Multicast sender (market data publisher)
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>

int main() {
    int sock = socket(AF_INET, SOCK_DGRAM, 0);

    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_addr.s_addr = inet_addr("239.1.1.1");  // Multicast group
    addr.sin_port = htons(12345);

    // Send market data
    MarketDataMessage msg = {
        .symbol_id = 1,      // AAPL
        .timestamp = get_nanos(),
        .bid = 150.50,
        .ask = 150.55,
        .last = 150.52,
        .volume = 1000
    };

    sendto(sock, &msg, sizeof(msg), 0, (struct sockaddr*)&addr, sizeof(addr));
}
```

---

## 6. Container Orchestration

### Kubernetes (EKS/GKE)

```yaml
# matching-engine-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: matching-engine
  namespace: trading
spec:
  replicas: 3
  selector:
    matchLabels:
      app: matching-engine
  template:
    metadata:
      labels:
        app: matching-engine
    spec:
      # Anti-affinity: spread across nodes
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
          - labelSelector:
              matchExpressions:
              - key: app
                operator: In
                values:
                - matching-engine
            topologyKey: "kubernetes.io/hostname"

      # Node selector: dedicated high-performance nodes
      nodeSelector:
        node-type: compute-optimized

      # Tolerations for dedicated nodes
      tolerations:
      - key: "matching-engine"
        operator: "Equal"
        value: "true"
        effect: "NoSchedule"

      containers:
      - name: matching-engine
        image: exchange/matching-engine:v1.5.3

        # Resource guarantees (no CPU throttling)
        resources:
          requests:
            memory: "32Gi"
            cpu: "16"
          limits:
            memory: "32Gi"
            cpu: "16"

        # Liveness probe
        livenessProbe:
          httpGet:
            path: /health
            port: 9090
          initialDelaySeconds: 30
          periodSeconds: 10

        # Readiness probe
        readinessProbe:
          httpGet:
            path: /ready
            port: 9090
          initialDelaySeconds: 10
          periodSeconds: 5

        # Environment variables
        env:
        - name: RUST_LOG
          value: "info"
        - name: KAFKA_BROKERS
          valueFrom:
            configMapKeyRef:
              name: kafka-config
              key: brokers

        # Volume mounts
        volumeMounts:
        - name: config
          mountPath: /etc/matching-engine
          readOnly: true

      volumes:
      - name: config
        configMap:
          name: matching-engine-config
```

### Service Mesh (Istio)

```yaml
# VirtualService for order-service
apiVersion: networking.istio.io/v1beta1
kind: VirtualService
metadata:
  name: order-service
spec:
  hosts:
  - order-service
  http:
  - match:
    - headers:
        user-tier:
          exact: "premium"
    route:
    - destination:
        host: order-service
        subset: v2
      weight: 100
    timeout: 5s
    retries:
      attempts: 3
      perTryTimeout: 1s
  - route:
    - destination:
        host: order-service
        subset: v1
      weight: 100
```

---

## 7. Monitoring & Observability

### Prometheus + Grafana

```yaml
# prometheus.yml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'matching-engine'
    static_configs:
      - targets: ['matching-engine:9090']
    metrics_path: /metrics

  - job_name: 'order-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - trading
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: order-service

  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']
```

**Custom Metrics:**
```java
// Micrometer (Spring Boot integration)
@Component
public class OrderMetrics {

    private final Counter ordersPlaced;
    private final Timer orderPlacementLatency;
    private final Gauge activeOrders;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersPlaced = Counter.builder("orders.placed")
            .description("Total orders placed")
            .tags("side", "buy")
            .register(registry);

        this.orderPlacementLatency = Timer.builder("orders.placement.latency")
            .description("Order placement latency")
            .publishPercentiles(0.5, 0.95, 0.99, 0.999)
            .register(registry);

        this.activeOrders = Gauge.builder("orders.active", this, OrderMetrics::getActiveOrderCount)
            .description("Active orders in matching engine")
            .register(registry);
    }

    public void recordOrder(Order order, long latencyMs) {
        ordersPlaced.increment();
        orderPlacementLatency.record(latencyMs, TimeUnit.MILLISECONDS);
    }

    private long getActiveOrderCount() {
        return orderRepository.countByStatus(OrderStatus.NEW);
    }
}
```

### Grafana Dashboards

```json
{
  "dashboard": {
    "title": "Trading System Overview",
    "panels": [
      {
        "title": "Order Placement Latency (p99)",
        "targets": [
          {
            "expr": "histogram_quantile(0.99, rate(orders_placement_latency_bucket[5m]))",
            "legendFormat": "p99"
          }
        ],
        "thresholds": [
          {"value": 10, "color": "red"}
        ]
      },
      {
        "title": "Orders Per Second",
        "targets": [
          {
            "expr": "rate(orders_placed_total[1m])",
            "legendFormat": "{{side}}"
          }
        ]
      }
    ]
  }
}
```

### Distributed Tracing (Jaeger)

```java
// OpenTelemetry instrumentation
@RestController
public class OrderController {

    @Autowired
    private Tracer tracer;

    @PostMapping("/orders")
    public OrderResponse placeOrder(@RequestBody OrderRequest request) {
        Span span = tracer.spanBuilder("placeOrder").startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute("symbol", request.getSymbol());
            span.setAttribute("quantity", request.getQuantity());

            // Validate order
            Span validateSpan = tracer.spanBuilder("validateOrder").startSpan();
            try (Scope validateScope = validateSpan.makeCurrent()) {
                orderValidator.validate(request);
            } finally {
                validateSpan.end();
            }

            // Submit to matching engine
            Span submitSpan = tracer.spanBuilder("submitToMatchingEngine").startSpan();
            try (Scope submitScope = submitSpan.makeCurrent()) {
                return matchingEngine.submit(request);
            } finally {
                submitSpan.end();
            }

        } catch (Exception e) {
            span.recordException(e);
            throw e;
        } finally {
            span.end();
        }
    }
}
```

---

## 8. Security & Compliance

### Secrets Management (HashiCorp Vault)

```bash
# Initialize Vault
vault operator init
vault operator unseal

# Enable database secrets engine
vault secrets enable database

# Configure PostgreSQL connection
vault write database/config/postgresql \
    plugin_name=postgresql-database-plugin \
    allowed_roles="readonly,readwrite" \
    connection_url="postgresql://{{username}}:{{password}}@postgres:5432/trading" \
    username="vault" \
    password="secret"

# Create role with dynamic credentials
vault write database/roles/readwrite \
    db_name=postgresql \
    creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
        GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
    default_ttl="1h" \
    max_ttl="24h"
```

**Application Integration:**
```java
@Configuration
public class VaultConfig {

    @Value("${vault.token}")
    private String vaultToken;

    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint endpoint = VaultEndpoint.create("vault.internal", 8200);
        VaultTemplate vaultTemplate = new VaultTemplate(
            endpoint,
            new TokenAuthentication(vaultToken)
        );
        return vaultTemplate;
    }

    @Bean
    public DataSource dataSource(VaultTemplate vaultTemplate) {
        // Get dynamic database credentials
        VaultResponse response = vaultTemplate.read("database/creds/readwrite");
        String username = response.getData().get("username").toString();
        String password = response.getData().get("password").toString();

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://postgres:5432/trading");
        config.setUsername(username);
        config.setPassword(password);
        return new HikariDataSource(config);
    }
}
```

### WAF (Web Application Firewall)

```nginx
# ModSecurity rules
SecRule ARGS "@rx <script" "id:1,deny,status:403,msg:'XSS Attempt'"
SecRule REQUEST_URI "@rx /api/v1/orders" "id:2,phase:1,t:none,pass,ctl:ruleEngine=On"

# Rate limiting
limit_req_zone $binary_remote_addr zone=api:10m rate=100r/s;
limit_req zone=api burst=20 nodelay;
```

---

## 9. CI/CD Pipeline

### GitLab CI/CD

```yaml
# .gitlab-ci.yml
stages:
  - test
  - build
  - deploy-staging
  - integration-test
  - deploy-prod

variables:
  DOCKER_REGISTRY: registry.gitlab.com/trading-exchange
  KUBE_NAMESPACE: trading

# Unit tests
unit-test:
  stage: test
  image: maven:3.8-openjdk-17
  script:
    - mvn clean test
    - mvn jacoco:report
  coverage: '/Total.*?([0-9]{1,3})%/'
  artifacts:
    reports:
      junit: target/surefire-reports/TEST-*.xml

# Build Docker image
build:
  stage: build
  image: docker:20.10
  services:
    - docker:dind
  script:
    - docker build -t $DOCKER_REGISTRY/order-service:$CI_COMMIT_SHA .
    - docker push $DOCKER_REGISTRY/order-service:$CI_COMMIT_SHA
  only:
    - main
    - develop

# Deploy to staging
deploy-staging:
  stage: deploy-staging
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context staging
    - kubectl set image deployment/order-service
        order-service=$DOCKER_REGISTRY/order-service:$CI_COMMIT_SHA
        -n $KUBE_NAMESPACE
    - kubectl rollout status deployment/order-service -n $KUBE_NAMESPACE
  environment:
    name: staging
  only:
    - develop

# Integration tests
integration-test:
  stage: integration-test
  script:
    - mvn verify -P integration-tests
  only:
    - develop

# Deploy to production
deploy-prod:
  stage: deploy-prod
  image: bitnami/kubectl:latest
  script:
    - kubectl config use-context production
    - kubectl set image deployment/order-service
        order-service=$DOCKER_REGISTRY/order-service:$CI_COMMIT_SHA
        -n $KUBE_NAMESPACE
    - kubectl rollout status deployment/order-service -n $KUBE_NAMESPACE
  environment:
    name: production
  when: manual  # Require manual approval
  only:
    - main
```

---

## 10. Disaster Recovery

### Backup Strategy

```bash
#!/bin/bash
# PostgreSQL backup script

BACKUP_DIR="/backups/postgresql"
DATE=$(date +%Y%m%d_%H%M%S)

# Base backup
pg_basebackup -h primary-db -U postgres -D $BACKUP_DIR/$DATE -Fp -Xs -P

# WAL archiving (continuous)
archive_command = 'test ! -f /wal_archive/%f && cp %p /wal_archive/%f'

# Upload to S3
aws s3 cp $BACKUP_DIR/$DATE s3://trading-backups/postgresql/$DATE/ --recursive

# Retention: Keep last 7 days
find $BACKUP_DIR -type d -mtime +7 -exec rm -rf {} \;
```

### Failover Automation (Patroni)

```yaml
# patroni.yml
scope: postgres-cluster
namespace: /service/
name: postgres-1

restapi:
  listen: 0.0.0.0:8008
  connect_address: postgres-1:8008

etcd:
  hosts: etcd-1:2379,etcd-2:2379,etcd-3:2379

bootstrap:
  dcs:
    ttl: 30
    loop_wait: 10
    retry_timeout: 10
    maximum_lag_on_failover: 1048576
    postgresql:
      use_pg_rewind: true
      parameters:
        synchronous_commit: "on"
        synchronous_standby_names: "*"

postgresql:
  listen: 0.0.0.0:5432
  connect_address: postgres-1:5432
  data_dir: /var/lib/postgresql/data
  pgpass: /tmp/pgpass
  authentication:
    replication:
      username: replicator
      password: secret
    superuser:
      username: postgres
      password: secret
```

---

## 11. Cost Optimization

### Reserved Instances (AWS)

```bash
# 1-year reserved instance savings: ~40%
# 3-year reserved instance savings: ~60%

# Example: 10 × m5.xlarge
On-Demand: $1,382/month
1-Year RI:   $828/month (40% savings)
3-Year RI:   $553/month (60% savings)
```

### Spot Instances (Non-Critical Workloads)

```yaml
# Kubernetes node pool with spot instances
apiVersion: karpenter.sh/v1alpha5
kind: Provisioner
metadata:
  name: spot-instances
spec:
  requirements:
    - key: karpenter.sh/capacity-type
      operator: In
      values: ["spot"]
    - key: kubernetes.io/arch
      operator: In
      values: ["amd64"]
  limits:
    resources:
      cpu: 1000
  providerRef:
    name: default
  ttlSecondsAfterEmpty: 30
```

---

## 12. Real-World Case Studies

### LMAX Exchange (Open Source Insights)

**Key Technologies:**
- **Disruptor**: Lock-free ring buffer (6M TPS)
- **Mechanical Sympathy**: Hardware-aware design
- **Java**: Yes, Java can do low latency!

```java
// LMAX Disruptor example
Disruptor<OrderEvent> disruptor = new Disruptor<>(
    OrderEvent::new,
    1024 * 1024,  // Ring buffer size (must be power of 2)
    DaemonThreadFactory.INSTANCE,
    ProducerType.MULTI,
    new BusySpinWaitStrategy()  // Low latency
);

// Event handler (business logic)
disruptor.handleEventsWith((event, sequence, endOfBatch) -> {
    processOrder(event.getOrder());
});

disruptor.start();
```

### NASDAQ OMX

**Tech Stack:**
- **Matching Engine**: C++
- **Market Data**: Multicast (UDP)
- **Message Protocol**: FIX 4.4, ITCH
- **Latency**: <100μs order-to-execution

### Coinbase (Crypto Exchange)

**Tech Stack:**
- **Languages**: Go, Ruby, Node.js
- **Database**: PostgreSQL (ACID), MongoDB (market data)
- **Queue**: RabbitMQ
- **Cloud**: AWS
- **Matching**: Custom Go-based engine

---

## 📚 Summary: Decision Matrix

| Component | Technology | Rationale |
|-----------|-----------|-----------|
| **Matching Engine** | C++17/Rust | Sub-μs latency, zero-cost abstractions |
| **Order Service** | Java 17 (Spring Boot) | Rich ecosystem, modern GC |
| **Database (OLTP)** | PostgreSQL 15 | ACID, proven reliability |
| **Time-Series** | TimescaleDB | 10x compression, continuous aggregates |
| **Cache** | Redis Cluster | Sub-ms latency, high availability |
| **Message Queue** | Kafka | Durability, ordering, event sourcing |
| **Networking** | DPDK, Solarflare | Kernel bypass, <10μs latency |
| **Orchestration** | Kubernetes | Industry standard, mature |
| **Monitoring** | Prometheus + Grafana | Open source, rich ecosystem |
| **Tracing** | Jaeger (OpenTelemetry) | Distributed tracing standard |
| **Secrets** | HashiCorp Vault | Dynamic credentials, audit log |
| **CI/CD** | GitLab CI/Argo CD | GitOps, declarative |

---

**Final Note**: In financial systems, **reliability > speed**, **correctness > features**, and **compliance > convenience**. Choose proven, battle-tested technologies over bleeding-edge tools.

---

*This tech stack powers billion-dollar trading platforms. Customize based on your scale, budget, and regulatory requirements.*
