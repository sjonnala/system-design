# 🛠️ Payment Gateway Tech Stack: Industry Solutions

## Real-World Technology Choices from Leading Payment Platforms

This document covers the **actual technology stacks** used by Stripe, Square, PayPal, Adyen, and other leading payment gateways, along with DevOps practices and infrastructure decisions.

---

## 📋 Table of Contents

1. [Programming Languages & Frameworks](#programming-languages--frameworks)
2. [Databases & Data Stores](#databases--data-stores)
3. [Message Queues & Event Streaming](#message-queues--event-streaming)
4. [Caching & In-Memory Stores](#caching--in-memory-stores)
5. [API Gateway & Load Balancing](#api-gateway--load-balancing)
6. [DevOps & Infrastructure](#devops--infrastructure)
7. [Monitoring & Observability](#monitoring--observability)
8. [Security & Compliance Tools](#security--compliance-tools)
9. [Payment Processor SDKs](#payment-processor-sdks)
10. [Cloud Providers](#cloud-providers)

---

## 1. Programming Languages & Frameworks

### Primary Languages

#### **Ruby (Stripe's Core)**

```ruby
# Stripe's original stack - still heavily used
# Why: Rapid development, excellent for financial DSLs

class PaymentIntent < ApplicationRecord
  include StateMachine

  state_machine :status do
    state :created
    state :pending
    state :authorized
    state :captured
    state :failed

    event :authorize do
      transition :pending => :authorized
    end

    event :capture do
      transition :authorized => :captured
    end
  end

  validates :amount, numericality: { greater_than: 0 }
  validates :currency, inclusion: { in: SUPPORTED_CURRENCIES }

  # ACID transaction with optimistic locking
  def capture!(amount = nil)
    transaction(isolation: :serializable) do
      lock!  # SELECT FOR UPDATE

      raise AlreadyCaptured if captured?
      raise InvalidAmount if amount && amount > authorized_amount

      self.captured_amount = amount || authorized_amount
      self.status = :captured
      save!

      # Publish event
      PaymentEvents.publish(:payment_captured, self)
    end
  end
end
```

**Stripe's Ruby Stack:**
- **Framework**: Ruby on Rails 7+
- **API**: Grape (REST API framework)
- **Background Jobs**: Sidekiq (Redis-backed)
- **Testing**: RSpec, FactoryBot
- **Linting**: RuboCop (custom payment rules)

**Why Stripe Uses Ruby:**
✅ Rapid iteration for product development
✅ Excellent DSL capabilities for financial logic
✅ Strong ecosystem for APIs (Grape, Sinatra)
✅ Battle-tested in high-traffic scenarios (Shopify, GitHub)

---

#### **Java (PayPal, Adyen, Square)**

```java
// Enterprise-grade payment processing
// Why: Type safety, performance, JVM ecosystem

@Service
@Transactional(isolation = Isolation.SERIALIZABLE)
public class PaymentService {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FraudDetectionService fraudService;

    @Autowired
    private ProcessorAdapter processorAdapter;

    @Autowired
    private KafkaTemplate<String, PaymentEvent> kafkaTemplate;

    /**
     * Authorize payment with two-phase commit
     * ACID guarantees enforced at database level
     */
    public PaymentIntent authorizePayment(
        AuthorizationRequest request
    ) throws PaymentException {

        // 1. Idempotency check
        Optional<PaymentIntent> existing = paymentRepository
            .findByIdempotencyKey(request.getIdempotencyKey());

        if (existing.isPresent()) {
            return existing.get(); // Return cached result
        }

        // 2. Create payment intent
        PaymentIntent payment = PaymentIntent.builder()
            .amount(new Money(request.getAmount(), request.getCurrency()))
            .status(PaymentStatus.PENDING)
            .idempotencyKey(request.getIdempotencyKey())
            .build();

        payment = paymentRepository.save(payment);

        // 3. Fraud check (async via Kafka)
        FraudCheckResult fraudResult = fraudService
            .checkRisk(payment, request.getContext());

        if (fraudResult.getRiskLevel() == RiskLevel.CRITICAL) {
            payment.setStatus(PaymentStatus.DECLINED);
            payment.setDeclineReason("fraud_detected");
            paymentRepository.save(payment);

            throw new FraudDeclinedException(fraudResult);
        }

        // 4. Call payment processor
        ProcessorAuthResponse authResponse = processorAdapter
            .authorize(payment, request.getPaymentMethod());

        // 5. Update payment with authorization (optimistic locking)
        payment.setStatus(PaymentStatus.AUTHORIZED);
        payment.setAuthorizedAmount(request.getAmount());
        payment.setAuthorizationCode(authResponse.getAuthCode());
        payment.incrementVersion(); // Optimistic lock

        payment = paymentRepository.save(payment);

        // 6. Publish event
        kafkaTemplate.send("payment.authorized",
            new PaymentAuthorizedEvent(payment));

        return payment;
    }
}
```

**PayPal's Java Stack:**
- **Framework**: Spring Boot 3.x
- **API**: Spring MVC (REST), gRPC (internal)
- **ORM**: Hibernate with custom extensions
- **Messaging**: Apache Kafka
- **Testing**: JUnit 5, Mockito, Testcontainers

**Adyen's Java Stack:**
- **Framework**: Spring Boot + Spring Cloud
- **API**: Spring WebFlux (reactive)
- **Database**: JPA + QueryDSL
- **Caching**: Caffeine (local), Redis (distributed)

**Why Payment Gateways Use Java:**
✅ Strong type safety (prevents financial bugs)
✅ Excellent performance (JVM optimizations)
✅ Mature ecosystem (Spring, Hibernate)
✅ Enterprise support and tooling
✅ Great for distributed systems (Spring Cloud)

---

#### **Go (Emerging Choice for High-Performance Services)**

```go
// Used by newer payment companies for core services
// Why: Performance, concurrency, low latency

package payment

import (
    "context"
    "database/sql"
    "github.com/shopspring/decimal"
    "time"
)

type PaymentService struct {
    db              *sql.DB
    fraudService    FraudService
    processorClient ProcessorClient
    kafkaProducer   KafkaProducer
}

// CapturePayment performs a two-phase commit capture
func (s *PaymentService) CapturePayment(
    ctx context.Context,
    paymentID string,
    amount decimal.Decimal,
) (*Payment, error) {

    // Begin database transaction with SERIALIZABLE isolation
    tx, err := s.db.BeginTx(ctx, &sql.TxOptions{
        Isolation: sql.LevelSerializable,
    })
    if err != nil {
        return nil, err
    }
    defer tx.Rollback()

    // 1. Lock and fetch payment (SELECT FOR UPDATE)
    payment, err := s.getPaymentForUpdate(ctx, tx, paymentID)
    if err != nil {
        return nil, err
    }

    // 2. Validate state
    if payment.Status != StatusAuthorized {
        return nil, ErrInvalidState
    }

    // 3. Validate amount
    if amount.GreaterThan(payment.AuthorizedAmount) {
        return nil, ErrAmountExceedsAuthorized
    }

    // 4. Call processor (with timeout and retry)
    ctx, cancel := context.WithTimeout(ctx, 30*time.Second)
    defer cancel()

    captureResp, err := s.processorClient.Capture(ctx, &CaptureRequest{
        AuthorizationID: payment.AuthorizationID,
        Amount:          amount,
    })
    if err != nil {
        return nil, err
    }

    // 5. Update payment (optimistic locking via version)
    result, err := tx.ExecContext(ctx, `
        UPDATE payments
        SET
            status = $1,
            captured_amount = captured_amount + $2,
            captured_at = $3,
            version = version + 1
        WHERE
            id = $4
            AND status = $5
            AND version = $6
            AND (captured_amount + $2) <= authorized_amount
    `,
        StatusCaptured,
        amount,
        time.Now(),
        paymentID,
        StatusAuthorized,
        payment.Version,
    )

    if err != nil {
        return nil, err
    }

    rowsAffected, _ := result.RowsAffected()
    if rowsAffected == 0 {
        return nil, ErrConcurrentModification
    }

    // 6. Insert audit event
    _, err = tx.ExecContext(ctx, `
        INSERT INTO payment_events (payment_id, event_type, event_data)
        VALUES ($1, $2, $3)
    `, paymentID, "payment.captured", captureResp)

    if err != nil {
        return nil, err
    }

    // 7. Commit transaction
    if err := tx.Commit(); err != nil {
        return nil, err
    }

    // 8. Publish Kafka event (async, fire-and-forget)
    go s.publishCapturedEvent(payment)

    return payment, nil
}
```

**Go Stack for Payment Services:**
- **Framework**: Gin, Echo, or custom HTTP servers
- **Database**: sqlx, pgx (PostgreSQL driver)
- **gRPC**: google.golang.org/grpc
- **Testing**: testify, go-sqlmock

**Why Emerging Payment Companies Use Go:**
✅ Exceptional performance (near C-level)
✅ Built-in concurrency (goroutines)
✅ Fast compilation and deployment
✅ Low memory footprint
✅ Great for microservices (gRPC support)

---

#### **Python (Fraud Detection & Machine Learning)**

```python
# Used for fraud detection, data science, ML models
# Why: Rich ML ecosystem, fast prototyping

import numpy as np
import pandas as pd
from sklearn.ensemble import GradientBoostingClassifier
from redis import Redis
from decimal import Decimal

class FraudDetectionService:
    """
    Real-time fraud detection using ML model + rule engine
    """

    def __init__(self, model_path: str, redis_client: Redis):
        self.model = self._load_model(model_path)
        self.redis = redis_client
        self.rules = FraudRuleEngine()

    def assess_risk(
        self,
        payment: Payment,
        context: RequestContext
    ) -> FraudAssessment:
        """
        Multi-layered fraud detection:
        1. Rule-based checks (velocity, blacklist)
        2. ML model scoring
        3. Combine scores
        """

        # Rule-based signals
        signals = []

        # Velocity check
        velocity_signal = self._check_velocity(
            payment_method_id=payment.payment_method_id,
            window_minutes=60,
            max_attempts=5
        )
        if velocity_signal:
            signals.append(velocity_signal)

        # Geographic anomaly
        geo_signal = self._check_geographic_anomaly(
            customer_id=payment.customer_id,
            ip_country=context.ip_country,
            billing_country=payment.billing_country
        )
        if geo_signal:
            signals.append(geo_signal)

        # ML model prediction
        features = self._extract_features(payment, context)
        ml_score = self.model.predict_proba(features)[0][1]  # Probability of fraud

        # Combine rule-based and ML scores
        rule_score = np.mean([s.severity for s in signals]) if signals else 0
        final_score = (rule_score * 0.4) + (ml_score * 0.6)

        # Determine risk level
        if final_score >= 0.8:
            risk_level = RiskLevel.CRITICAL
            decision = Decision.DECLINE
        elif final_score >= 0.5:
            risk_level = RiskLevel.HIGH
            decision = Decision.REVIEW
        elif final_score >= 0.3:
            risk_level = RiskLevel.MEDIUM
            decision = Decision.APPROVE
        else:
            risk_level = RiskLevel.LOW
            decision = Decision.APPROVE

        return FraudAssessment(
            risk_score=final_score,
            risk_level=risk_level,
            decision=decision,
            signals=signals,
            ml_score=ml_score
        )

    def _check_velocity(
        self,
        payment_method_id: str,
        window_minutes: int,
        max_attempts: int
    ) -> Optional[FraudSignal]:
        """Check if payment method is used too frequently"""

        # Redis sorted set for velocity tracking
        key = f"velocity:pm:{payment_method_id}"
        now = time.time()
        window_start = now - (window_minutes * 60)

        # Remove old entries
        self.redis.zremrangebyscore(key, 0, window_start)

        # Count recent attempts
        count = self.redis.zcount(key, window_start, now)

        if count >= max_attempts:
            return FraudSignal(
                rule_id="velocity_exceeded",
                severity=0.7,
                description=f"Payment method used {count} times in {window_minutes}min"
            )

        return None

    def _extract_features(
        self,
        payment: Payment,
        context: RequestContext
    ) -> np.ndarray:
        """
        Extract 150+ features for ML model:
        - Transaction features: amount, currency, time
        - Customer features: account age, transaction history
        - Device features: fingerprint, IP, user agent
        - Behavioral features: typing speed, mouse movements
        """

        features = {
            # Transaction features
            'amount_usd': self._normalize_amount(payment.amount, payment.currency),
            'amount_zscore': self._calculate_amount_zscore(payment),
            'hour_of_day': datetime.now().hour,
            'day_of_week': datetime.now().weekday(),

            # Customer features
            'customer_age_days': self._get_customer_age(payment.customer_id),
            'customer_txn_count': self._get_customer_txn_count(payment.customer_id),
            'customer_avg_amount': self._get_customer_avg_amount(payment.customer_id),

            # Device features
            'device_fingerprint_seen': self._is_device_known(context.device_fingerprint),
            'ip_country_matches_billing': context.ip_country == payment.billing_country,

            # Behavioral features (from frontend SDK)
            'typing_speed_wpm': context.typing_speed,
            'mouse_movements': len(context.mouse_events),

            # ... 140+ more features
        }

        return np.array(list(features.values())).reshape(1, -1)
```

**Python Stack for ML/Fraud:**
- **Framework**: FastAPI (async REST), Flask
- **ML Libraries**: scikit-learn, XGBoost, TensorFlow
- **Data**: pandas, NumPy
- **Feature Store**: Feast, Tecton
- **Model Serving**: TensorFlow Serving, MLflow

---

## 2. Databases & Data Stores

### Primary Database: PostgreSQL

```sql
-- Why PostgreSQL for payment systems:
-- ✅ ACID compliance (critical!)
-- ✅ SERIALIZABLE isolation level
-- ✅ Advanced locking mechanisms
-- ✅ Excellent performance
-- ✅ JSON support (metadata)
-- ✅ Battle-tested at scale

-- Stripe's PostgreSQL Configuration
-- Instance: AWS RDS PostgreSQL 15
-- Size: db.r6g.8xlarge (32 vCPU, 256 GB RAM)
-- Storage: io2 (50K IOPS provisioned)
-- Replication: 3-5 read replicas across AZs

-- Key configuration parameters
max_connections = 500
shared_buffers = 64GB
effective_cache_size = 192GB
work_mem = 256MB
maintenance_work_mem = 2GB
checkpoint_completion_target = 0.9
wal_buffers = 16MB
default_statistics_target = 500
random_page_cost = 1.1  -- SSD optimized
effective_io_concurrency = 200
max_worker_processes = 32
max_parallel_workers_per_gather = 8
max_parallel_workers = 32

-- Isolation level enforcement
ALTER DATABASE payments SET default_transaction_isolation = 'serializable';

-- Extensions
CREATE EXTENSION IF NOT EXISTS pg_stat_statements;  -- Query performance
CREATE EXTENSION IF NOT EXISTS pgcrypto;             -- Encryption
CREATE EXTENSION IF NOT EXISTS pg_trgm;              -- Fuzzy search
```

**PostgreSQL Partitioning Strategy:**

```sql
-- Range partitioning by created_at (monthly)
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- ... other columns
) PARTITION BY RANGE (created_at);

-- Create partitions (automated via cron)
CREATE TABLE payment_transactions_2024_01 PARTITION OF payment_transactions
    FOR VALUES FROM ('2024-01-01') TO ('2024-02-01');

CREATE TABLE payment_transactions_2024_02 PARTITION OF payment_transactions
    FOR VALUES FROM ('2024-02-01') TO ('2024-03-01');

-- Partition maintenance script
CREATE OR REPLACE FUNCTION create_monthly_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE;
    partition_name TEXT;
    start_date TEXT;
    end_date TEXT;
BEGIN
    partition_date := DATE_TRUNC('month', CURRENT_DATE + INTERVAL '1 month');
    partition_name := 'payment_transactions_' || TO_CHAR(partition_date, 'YYYY_MM');
    start_date := TO_CHAR(partition_date, 'YYYY-MM-DD');
    end_date := TO_CHAR(partition_date + INTERVAL '1 month', 'YYYY-MM-DD');

    EXECUTE format('CREATE TABLE IF NOT EXISTS %I PARTITION OF payment_transactions FOR VALUES FROM (%L) TO (%L)',
        partition_name, start_date, end_date);
END;
$$ LANGUAGE plpgsql;

-- Schedule daily (via pg_cron)
SELECT cron.schedule('create-monthly-partitions', '0 0 * * *', 'SELECT create_monthly_partition()');
```

---

### Alternatives & Specialized Databases

#### **MySQL (Square)**

```sql
-- Square uses MySQL (with InnoDB)
-- Why: Familiarity, tooling, horizontal scaling (Vitess)

-- Configuration for financial systems
innodb_flush_log_at_trx_commit = 1  -- Durability (ACID)
innodb_doublewrite = ON              -- Prevent corruption
transaction_isolation = 'SERIALIZABLE'
innodb_lock_wait_timeout = 50        -- Prevent deadlocks

-- Vitess for horizontal scaling
-- Sharding by merchant_id
```

#### **CockroachDB (Emerging Choice)**

```sql
-- Distributed SQL with strong consistency
-- Why: Built-in sharding, automatic failover, ACID

-- No configuration needed for ACID!
-- Default isolation: SERIALIZABLE
-- Automatic replication: 3x by default

CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    amount DECIMAL(19, 4) NOT NULL,
    -- CockroachDB handles sharding automatically!
    INDEX idx_merchant (merchant_id) STORING (amount, created_at)
);
```

---

### Analytics Database

#### **ClickHouse (OLAP)**

```sql
-- Real-time analytics and reporting
-- Why: Column-oriented, fast aggregations, compression

CREATE TABLE payment_events (
    event_id UUID,
    payment_id UUID,
    merchant_id UUID,
    event_type LowCardinality(String),
    amount Decimal(19, 4),
    currency FixedString(3),
    created_at DateTime,
    metadata String  -- JSON
)
ENGINE = MergeTree()
PARTITION BY toYYYYMM(created_at)
ORDER BY (merchant_id, created_at)
SETTINGS index_granularity = 8192;

-- Query performance
SELECT
    merchant_id,
    toDate(created_at) AS date,
    count() AS transaction_count,
    sum(amount) AS total_volume,
    avg(amount) AS avg_amount
FROM payment_events
WHERE
    event_type = 'payment.captured'
    AND created_at >= now() - INTERVAL 7 DAY
GROUP BY merchant_id, date
ORDER BY total_volume DESC
LIMIT 100;

-- Typical query latency: 50-200ms on billions of rows
```

---

## 3. Message Queues & Event Streaming

### Apache Kafka (Industry Standard)

```yaml
# Kafka cluster configuration for payment systems
# Used by: Stripe, PayPal, Square, Adyen

# Broker configuration
num.brokers: 3
replication.factor: 3
min.insync.replicas: 2  # Durability guarantee
log.retention.hours: 168  # 7 days
log.segment.bytes: 1073741824  # 1 GB

# Topics
topics:
  - name: payment.created
    partitions: 12
    replication: 3
    retention: 7d

  - name: payment.authorized
    partitions: 12
    replication: 3
    retention: 7d

  - name: payment.captured
    partitions: 12
    replication: 3
    retention: 7d

  - name: payment.refunded
    partitions: 12
    replication: 3
    retention: 7d

  - name: fraud.detected
    partitions: 12
    replication: 3
    retention: 30d  # Longer for analysis
```

**Kafka Producer Configuration:**

```java
// Java producer for payment events
Properties props = new Properties();
props.put("bootstrap.servers", "kafka-1:9092,kafka-2:9092,kafka-3:9092");
props.put("acks", "all");  // Wait for all replicas (durability)
props.put("retries", 3);
props.put("max.in.flight.requests.per.connection", 1);  // Ordering guarantee
props.put("enable.idempotence", true);  // Exactly-once semantics
props.put("compression.type", "snappy");
props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
props.put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer");

KafkaProducer<String, PaymentEvent> producer = new KafkaProducer<>(props);

// Publish event with partition key (merchant_id for ordering)
producer.send(new ProducerRecord<>(
    "payment.captured",
    payment.getMerchantId().toString(),  // Key (determines partition)
    paymentEvent                         // Value
), (metadata, exception) -> {
    if (exception != null) {
        logger.error("Failed to publish event", exception);
        // Alert ops team
    }
});
```

---

### AWS SQS (Alternative for AWS-centric stacks)

```python
# Used by smaller payment companies on AWS
import boto3

sqs = boto3.client('sqs')

# Create FIFO queue for ordered processing
queue_url = sqs.create_queue(
    QueueName='payment-events.fifo',
    Attributes={
        'FifoQueue': 'true',
        'ContentBasedDeduplication': 'true',  # Idempotency
        'MessageRetentionPeriod': '1209600',  # 14 days
        'VisibilityTimeout': '300'  # 5 minutes
    }
)

# Publish event
sqs.send_message(
    QueueUrl=queue_url,
    MessageBody=json.dumps(payment_event),
    MessageGroupId=merchant_id,  # Ordering within group
    MessageDeduplicationId=idempotency_key  # Exactly-once
)
```

---

## 4. Caching & In-Memory Stores

### Redis (Universal Choice)

```yaml
# Redis cluster configuration
# Used by: All major payment gateways

# Cluster setup
cluster:
  nodes: 6
  masters: 3
  replicas: 3

# Memory configuration
maxmemory: 32gb
maxmemory-policy: allkeys-lru  # Evict least recently used

# Persistence (for critical data like idempotency)
appendonly: yes
appendfsync: everysec  # Balance between performance and durability

# Replication
repl-diskless-sync: yes
repl-backlog-size: 512mb
```

**Use Cases:**

```python
import redis
from decimal import Decimal

redis_client = redis.Redis(
    host='redis-cluster',
    port=6379,
    decode_responses=True,
    socket_connect_timeout=5,
    socket_timeout=5
)

# 1. Idempotency cache
def cache_idempotency_result(key: str, result: dict, ttl: int = 86400):
    """Cache payment result for 24 hours"""
    redis_client.setex(
        f"idempotency:{key}",
        ttl,
        json.dumps(result)
    )

# 2. Rate limiting (token bucket)
def check_rate_limit(merchant_id: str, limit: int = 1000, window: int = 3600):
    """Allow 1000 requests per hour per merchant"""
    key = f"rate_limit:{merchant_id}:{int(time.time() / window)}"

    current = redis_client.incr(key)
    if current == 1:
        redis_client.expire(key, window)

    return current <= limit

# 3. Velocity tracking (fraud detection)
def track_payment_velocity(payment_method_id: str):
    """Track payment attempts for velocity checks"""
    key = f"velocity:pm:{payment_method_id}"
    now = time.time()

    # Add current attempt
    redis_client.zadd(key, {str(uuid.uuid4()): now})

    # Remove old entries (older than 1 hour)
    redis_client.zremrangebyscore(key, 0, now - 3600)

    # Set expiration
    redis_client.expire(key, 3600)

    # Count recent attempts
    count = redis_client.zcount(key, now - 3600, now)
    return count

# 4. Session storage
def store_checkout_session(session_id: str, data: dict, ttl: int = 1800):
    """Store checkout session for 30 minutes"""
    redis_client.setex(
        f"session:{session_id}",
        ttl,
        json.dumps(data)
    )
```

---

## 5. API Gateway & Load Balancing

### Kong (API Gateway)

```yaml
# Kong configuration for payment APIs
# Used by: Stripe, many fintech companies

plugins:
  - name: key-auth
    config:
      key_names:
        - apikey
      hide_credentials: true

  - name: rate-limiting
    config:
      minute: 1000  # 1000 requests per minute
      policy: redis
      redis_host: redis-cluster
      fault_tolerant: true

  - name: request-validator
    config:
      allowed_content_types:
        - application/json
      body_schema: |
        {
          "type": "object",
          "properties": {
            "amount": {"type": "integer", "minimum": 1},
            "currency": {"type": "string", "minLength": 3, "maxLength": 3}
          },
          "required": ["amount", "currency"]
        }

  - name: response-transformer
    config:
      remove:
        headers:
          - X-Internal-Debug

  - name: correlation-id
    config:
      header_name: X-Request-ID
      generator: uuid

  - name: prometheus
    config:
      per_consumer: true
```

---

### NGINX (Load Balancer)

```nginx
# NGINX configuration for payment gateway
# Used by: PayPal, Square

upstream payment_service {
    least_conn;  # Least connections algorithm

    server payment-1:8080 max_fails=3 fail_timeout=30s;
    server payment-2:8080 max_fails=3 fail_timeout=30s;
    server payment-3:8080 max_fails=3 fail_timeout=30s;

    keepalive 32;  # Connection pooling
}

server {
    listen 443 ssl http2;
    server_name api.payment.com;

    # TLS configuration
    ssl_certificate /etc/ssl/certs/payment.crt;
    ssl_certificate_key /etc/ssl/private/payment.key;
    ssl_protocols TLSv1.3;
    ssl_ciphers 'TLS_AES_128_GCM_SHA256:TLS_AES_256_GCM_SHA384';
    ssl_prefer_server_ciphers on;

    # Rate limiting
    limit_req_zone $binary_remote_addr zone=payment_limit:10m rate=100r/s;
    limit_req zone=payment_limit burst=200 nodelay;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
    add_header X-Frame-Options "DENY" always;
    add_header X-Content-Type-Options "nosniff" always;

    location /v1/payments {
        proxy_pass http://payment_service;
        proxy_http_version 1.1;

        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;

        # Timeouts
        proxy_connect_timeout 10s;
        proxy_send_timeout 30s;
        proxy_read_timeout 30s;

        # Buffering
        proxy_buffering off;

        # Connection reuse
        proxy_set_header Connection "";
    }
}
```

---

## 6. DevOps & Infrastructure

### Kubernetes (Container Orchestration)

```yaml
# Kubernetes deployment for payment service
# Used by: Stripe, Adyen, modern fintech

apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
  namespace: payments
spec:
  replicas: 10
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 25%
      maxUnavailable: 0  # Zero downtime

  selector:
    matchLabels:
      app: payment-service

  template:
    metadata:
      labels:
        app: payment-service
        version: v1.2.3
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/metrics"

    spec:
      affinity:
        podAntiAffinity:
          requiredDuringSchedulingIgnoredDuringExecution:
            - labelSelector:
                matchExpressions:
                  - key: app
                    operator: In
                    values:
                      - payment-service
              topologyKey: "kubernetes.io/hostname"

      containers:
        - name: payment-service
          image: payment-service:v1.2.3

          ports:
            - containerPort: 8080
              name: http

          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: payment-db-secret
                  key: url

            - name: STRIPE_API_KEY
              valueFrom:
                secretKeyRef:
                  name: stripe-secret
                  key: api-key

            - name: JAVA_OPTS
              value: "-Xms4g -Xmx4g -XX:+UseG1GC"

          resources:
            requests:
              cpu: "2"
              memory: "4Gi"
            limits:
              cpu: "4"
              memory: "8Gi"

          livenessProbe:
            httpGet:
              path: /health/live
              port: 8080
            initialDelaySeconds: 60
            periodSeconds: 10
            timeoutSeconds: 5
            failureThreshold: 3

          readinessProbe:
            httpGet:
              path: /health/ready
              port: 8080
            initialDelaySeconds: 30
            periodSeconds: 5
            timeoutSeconds: 3
            failureThreshold: 3

          volumeMounts:
            - name: config
              mountPath: /etc/payment-service

      volumes:
        - name: config
          configMap:
            name: payment-service-config

---
apiVersion: v1
kind: Service
metadata:
  name: payment-service
  namespace: payments
spec:
  type: ClusterIP
  selector:
    app: payment-service
  ports:
    - port: 80
      targetPort: 8080
      protocol: TCP

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: payment-service-hpa
  namespace: payments
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: payment-service
  minReplicas: 10
  maxReplicas: 100
  metrics:
    - type: Resource
      resource:
        name: cpu
        target:
          type: Utilization
          averageUtilization: 70
    - type: Resource
      resource:
        name: memory
        target:
          type: Utilization
          averageUtilization: 80
```

---

### CI/CD Pipeline (GitHub Actions)

```yaml
# .github/workflows/deploy-payment-service.yml
name: Deploy Payment Service

on:
  push:
    branches: [main]
    paths:
      - 'services/payment/**'

jobs:
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Set up JDK 17
        uses: actions/setup-java@v3
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Run tests
        run: |
          cd services/payment
          ./gradlew test

      - name: Run integration tests
        run: |
          docker-compose up -d postgres redis
          ./gradlew integrationTest

      - name: Security scan
        run: |
          ./gradlew dependencyCheckAnalyze

  build:
    needs: test
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Build Docker image
        run: |
          docker build -t payment-service:${{ github.sha }} .

      - name: Push to ECR
        run: |
          aws ecr get-login-password | docker login --username AWS --password-stdin $ECR_URL
          docker tag payment-service:${{ github.sha }} $ECR_URL/payment-service:${{ github.sha }}
          docker push $ECR_URL/payment-service:${{ github.sha }}

  deploy:
    needs: build
    runs-on: ubuntu-latest
    steps:
      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/payment-service payment-service=$ECR_URL/payment-service:${{ github.sha }}
          kubectl rollout status deployment/payment-service -n payments
```

---

## 7. Monitoring & Observability

### Prometheus + Grafana

```java
// Micrometer metrics in Spring Boot
@RestController
@RequestMapping("/v1/payments")
public class PaymentController {

    private final MeterRegistry meterRegistry;
    private final PaymentService paymentService;

    @PostMapping("/authorize")
    public ResponseEntity<PaymentIntent> authorize(@RequestBody AuthRequest request) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            PaymentIntent payment = paymentService.authorize(request);

            // Record success
            meterRegistry.counter("payment.authorized",
                "currency", payment.getCurrency(),
                "status", "success"
            ).increment();

            return ResponseEntity.ok(payment);

        } catch (FraudDeclinedException e) {
            meterRegistry.counter("payment.declined",
                "reason", "fraud"
            ).increment();
            throw e;

        } finally {
            sample.stop(meterRegistry.timer("payment.authorization.latency"));
        }
    }
}
```

**Grafana Dashboards:**
- Payment volume by currency
- Authorization success rate
- P50/P95/P99 latency
- Fraud detection metrics
- Processor health (circuit breaker status)

---

### Distributed Tracing (Jaeger)

```java
// OpenTelemetry instrumentation
@Component
public class PaymentTracer {

    private final Tracer tracer;

    public PaymentIntent authorizeWithTracing(AuthRequest request) {
        Span span = tracer.spanBuilder("payment.authorize")
            .setAttribute("merchant_id", request.getMerchantId())
            .setAttribute("amount", request.getAmount())
            .startSpan();

        try (Scope scope = span.makeCurrent()) {
            // Fraud check (child span)
            Span fraudSpan = tracer.spanBuilder("fraud.check").startSpan();
            FraudResult fraud = fraudService.check(request);
            fraudSpan.setAttribute("risk_score", fraud.getScore());
            fraudSpan.end();

            // Processor call (child span)
            Span processorSpan = tracer.spanBuilder("processor.authorize").startSpan();
            ProcessorResponse response = processor.authorize(request);
            processorSpan.setAttribute("processor", "stripe");
            processorSpan.end();

            return paymentIntent;

        } catch (Exception e) {
            span.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            span.end();
        }
    }
}
```

---

## 8. Security & Compliance Tools

### PCI-DSS Compliance Stack

- **Vault (HashiCorp)**: Secret management (API keys, database credentials)
- **TokenEx / Spreedly**: Card tokenization services
- **Qualys / Rapid7**: Vulnerability scanning (quarterly PCI scans)
- **Splunk / ELK**: Log aggregation and audit trails
- **AWS WAF / CloudFlare**: Web application firewall
- **CyberArk**: Privileged access management

### Example: HashiCorp Vault

```bash
# Store Stripe API key
vault kv put secret/stripe/api-key value="sk_live_..."

# Retrieve in application
vault kv get -field=value secret/stripe/api-key
```

---

## 9. Payment Processor SDKs

### Stripe SDK (Ruby/Java/Python/Go)

```ruby
# Ruby
require 'stripe'
Stripe.api_key = ENV['STRIPE_API_KEY']

payment_intent = Stripe::PaymentIntent.create({
  amount: 1999,
  currency: 'usd',
  payment_method: 'pm_card_visa',
  confirmation_method: 'automatic',
  confirm: true
})
```

### PayPal SDK

```java
// Java
PayPalHttpClient client = new PayPalHttpClient(environment);

OrderRequest orderRequest = new OrderRequest();
orderRequest.checkoutPaymentIntent("CAPTURE");

OrdersCreateRequest request = new OrdersCreateRequest();
request.requestBody(orderRequest);

HttpResponse<Order> response = client.execute(request);
```

---

## 10. Cloud Providers

### AWS (Most Common)

**Services Used:**
- **Compute**: ECS, EKS (Kubernetes)
- **Database**: RDS (PostgreSQL), DynamoDB
- **Cache**: ElastiCache (Redis)
- **Messaging**: MSK (Kafka), SQS
- **Storage**: S3 (backups, audit logs)
- **Networking**: VPC, ALB, Route 53
- **Security**: KMS, Secrets Manager, WAF
- **Monitoring**: CloudWatch

### GCP (Alternative)

**Services Used:**
- **Compute**: GKE (Kubernetes)
- **Database**: Cloud SQL (PostgreSQL), Cloud Spanner
- **Cache**: Memorystore (Redis)
- **Messaging**: Pub/Sub
- **Monitoring**: Cloud Monitoring, Cloud Trace

---

## Summary: Tech Stack Matrix

| **Component** | **Stripe** | **PayPal** | **Square** | **Adyen** |
|---------------|------------|------------|------------|-----------|
| **Language** | Ruby, Go | Java | Java, Go | Java |
| **Framework** | Rails, gRPC | Spring Boot | Spring Boot | Spring Boot |
| **Database** | PostgreSQL | Oracle, PostgreSQL | MySQL (Vitess) | PostgreSQL |
| **Cache** | Redis | Redis | Redis | Redis |
| **Message Queue** | Kafka | Kafka | Kafka | Kafka |
| **API Gateway** | Kong | Custom | Kong | Custom |
| **Container Orchestration** | Kubernetes | Kubernetes | Kubernetes | Kubernetes |
| **Cloud Provider** | AWS | Private + AWS | AWS, GCP | AWS, GCP, Azure |
| **Monitoring** | Prometheus, Grafana | Splunk | Datadog | Prometheus |
| **Tracing** | Jaeger | Jaeger | Jaeger | Jaeger |

---

## Recommended Stack for New Payment Gateway (2025)

```yaml
Language: Java 17 + Spring Boot 3.x (or Go for microservices)
Database: PostgreSQL 15+ (with partitioning)
Cache: Redis 7+ (cluster mode)
Message Queue: Apache Kafka
API Gateway: Kong
Container Orchestration: Kubernetes (EKS/GKE)
Cloud Provider: AWS
Monitoring: Prometheus + Grafana
Tracing: OpenTelemetry + Jaeger
Logging: ELK Stack (Elasticsearch, Logstash, Kibana)
CI/CD: GitHub Actions
Security: HashiCorp Vault, AWS KMS
Compliance: Vanta (SOC 2), Drata (compliance automation)
```

---

**Further Learning:**
- Stripe Engineering Blog: https://stripe.com/blog/engineering
- PayPal Tech Blog: https://medium.com/paypal-tech
- Square Engineering: https://developer.squareup.com/blog
- Payment Processing Books: "Payments Systems in the U.S." by Carol Coye Benson
