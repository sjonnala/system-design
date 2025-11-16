# Message Queues & Event Streaming

## Why Message Queues?

**Problem**: Direct service-to-service communication has issues

```
Service A → calls → Service B (synchronous)

Issues:
1. Tight coupling
2. If B is down, A fails
3. If B is slow, A waits
4. Load spikes overwhelm B
5. Retry logic in every service
```

**Solution**: Asynchronous communication via message queue

```
Service A → publish → [Queue] → subscribe → Service B

Benefits:
1. Decoupling
2. Reliability (queue persists messages)
3. Async processing
4. Load smoothing
5. Built-in retry
```

---

## Core Concepts

### Point-to-Point (Queue)

**One message → One consumer**

```
Producer → [Queue: msg1, msg2, msg3] → Consumer 1 (gets msg1)
                                   → Consumer 2 (gets msg2)
                                   → Consumer 3 (gets msg3)
```

**Use Cases**:
- Task distribution
- Background jobs
- Load balancing work

**Example**: Order processing
```
Order Service → [Order Queue] → Payment Processor
```

### Publish-Subscribe (Topic)

**One message → Multiple consumers**

```
Publisher → [Topic: event1]
                 ↓
        ┌────────┼────────┐
        ↓        ↓        ↓
    Consumer 1  Consumer 2  Consumer 3
    (all receive event1)
```

**Use Cases**:
- Event broadcasting
- Real-time updates
- System notifications

**Example**: User signup
```
User Service → [UserSignup Topic]
                     ↓
        ┌────────────┼────────────┐
        ↓            ↓            ↓
    Email Service  Analytics  Recommendation Service
```

---

## Message Queue Implementations

### RabbitMQ

**Architecture**: Traditional message broker (AMQP protocol)

**Components**:
```
Producer → Exchange → [Queue] → Consumer

Exchange Types:
1. Direct: Route by exact routing key
2. Fanout: Broadcast to all queues
3. Topic: Route by pattern matching
4. Headers: Route by message headers
```

**Example - Direct Exchange**:
```python
import pika

# Producer
connection = pika.BlockingConnection(pika.ConnectionParameters('localhost'))
channel = connection.channel()

# Declare exchange and queue
channel.exchange_declare(exchange='orders', exchange_type='direct')
channel.queue_declare(queue='payment_queue')
channel.queue_bind(exchange='orders', queue='payment_queue', routing_key='payment')

# Publish message
channel.basic_publish(
    exchange='orders',
    routing_key='payment',
    body='{"order_id": 123, "amount": 99.99}'
)

connection.close()

# Consumer
def callback(ch, method, properties, body):
    print(f"Received: {body}")
    # Process message
    process_payment(body)
    # Acknowledge
    ch.basic_ack(delivery_tag=method.delivery_tag)

channel.basic_consume(queue='payment_queue', on_message_callback=callback)
channel.start_consuming()
```

**Features**:
- Message persistence
- Acknowledgments (at-least-once delivery)
- Dead letter queues
- Message priorities
- TTL (time-to-live)

**Use Cases**: Traditional messaging, task queues, RPC patterns

### Apache Kafka

**Architecture**: Distributed event streaming platform

**Key Concepts**:
```
Topic (log of events)
  ↓
Partitions (for parallelism)
  ↓
Messages (ordered within partition)

Example Topic: "user-events"
Partition 0: [evt1, evt3, evt5]
Partition 1: [evt2, evt4, evt6]
Partition 2: [evt7, evt8, evt9]
```

**Producer**:
```python
from kafka import KafkaProducer
import json

producer = KafkaProducer(
    bootstrap_servers=['localhost:9092'],
    value_serializer=lambda v: json.dumps(v).encode('utf-8')
)

# Publish message
producer.send(
    'user-events',
    value={'user_id': 123, 'action': 'signup', 'timestamp': 1234567890},
    key=b'user-123'  # Same key → same partition (ordering guaranteed)
)

producer.flush()
```

**Consumer**:
```python
from kafka import KafkaConsumer
import json

consumer = KafkaConsumer(
    'user-events',
    bootstrap_servers=['localhost:9092'],
    group_id='analytics-service',
    value_deserializer=lambda m: json.loads(m.decode('utf-8')),
    auto_offset_reset='earliest'  # Start from beginning
)

for message in consumer:
    print(f"Partition: {message.partition}")
    print(f"Offset: {message.offset}")
    print(f"Value: {message.value}")

    # Process event
    process_event(message.value)

    # Kafka tracks consumer offset automatically
```

**Consumer Groups**:
```
Topic: user-events (3 partitions)

Consumer Group "analytics":
  Consumer 1 → Partition 0
  Consumer 2 → Partition 1
  Consumer 3 → Partition 2

Consumer Group "email":
  Consumer 1 → Partition 0, 1, 2

Both groups receive all messages (pub-sub)
Within group, partitions distributed (load balancing)
```

**Features**:
- High throughput (millions/sec)
- Persistent log (retain for days/weeks)
- Replay capability (reset offset)
- Partitioning for parallelism
- Exactly-once semantics (with transactions)

**Use Cases**:
- Event streaming
- Log aggregation
- Real-time analytics
- Change data capture (CDC)

### Amazon SQS/SNS

**SQS (Simple Queue Service)**: Managed message queue

**SNS (Simple Notification Service)**: Pub-sub

**Pattern**: SNS → SQS (fan-out)

```
Publisher → [SNS Topic]
                ↓
        ┌───────┼───────┐
        ↓       ↓       ↓
   [SQS Q1] [SQS Q2] [SQS Q3]
        ↓       ↓       ↓
    Service1 Service2 Service3
```

**Example**:
```python
import boto3

# SNS - Publish
sns = boto3.client('sns')
sns.publish(
    TopicArn='arn:aws:sns:us-east-1:123456789:order-events',
    Message=json.dumps({'order_id': 123, 'status': 'completed'}),
    MessageAttributes={
        'event_type': {'DataType': 'String', 'StringValue': 'order_completed'}
    }
)

# SQS - Consume
sqs = boto3.client('sqs')
messages = sqs.receive_message(
    QueueUrl='https://sqs.us-east-1.amazonaws.com/123456789/payment-queue',
    MaxNumberOfMessages=10,
    WaitTimeSeconds=20  # Long polling
)

for message in messages.get('Messages', []):
    body = json.loads(message['Body'])
    process_message(body)

    # Delete message (acknowledge)
    sqs.delete_message(
        QueueUrl='...',
        ReceiptHandle=message['ReceiptHandle']
    )
```

**Features**:
- Fully managed (no servers)
- Auto-scaling
- Standard queue (at-least-once, best-effort ordering)
- FIFO queue (exactly-once, strict ordering)
- Dead letter queues

---

## Message Delivery Guarantees

### At-Most-Once

**Guarantee**: Message delivered 0 or 1 times (might be lost)

```
Producer → send → [Broker]
                   (ack)
           ↓
       Consumer receive → process
                         (no ack to broker)

If consumer crashes: Message lost
```

**Use Case**: Metrics, logs (loss acceptable)

### At-Least-Once

**Guarantee**: Message delivered ≥ 1 times (might duplicate)

```
Producer → send → [Broker]
                   (ack after persist)
           ↓
       Consumer receive → process → ack
                         ↓
                    (crash before ack)
                         ↓
                  Message redelivered

Consumer must be idempotent!
```

**Use Case**: Most systems (with idempotent processing)

**Idempotent Processing**:
```python
processed_ids = set()  # or database

def process_message(message):
    if message.id in processed_ids:
        # Already processed, skip
        return

    # Process
    do_work(message)

    # Mark as processed
    processed_ids.add(message.id)
```

### Exactly-Once

**Guarantee**: Message delivered exactly 1 time

**Challenge**: Very difficult in distributed systems

**Kafka Approach** (with transactions):
```java
producer.initTransactions();

try {
    producer.beginTransaction();
    producer.send(new ProducerRecord<>("topic", key, value));
    producer.commitTransaction();
} catch (Exception e) {
    producer.abortTransaction();
}
```

**Application-Level Approach** (Transactional Outbox):
```sql
BEGIN TRANSACTION;

-- 1. Update business data
UPDATE orders SET status = 'completed' WHERE id = 123;

-- 2. Insert message to outbox table
INSERT INTO outbox (event_type, payload)
VALUES ('order_completed', '{"order_id": 123}');

COMMIT;

-- 3. Background process publishes from outbox to queue
```

**Use Case**: Financial transactions, critical workflows

---

## Message Queue Patterns

### Work Queue (Task Distribution)

```
Producer → [Queue]
              ↓
     ┌────────┼────────┐
     ↓        ↓        ↓
  Worker1  Worker2  Worker3

Each message processed by one worker
Load distributed automatically
```

**Example**: Image processing
```python
# Producer
for image_url in images:
    queue.publish({'url': image_url, 'resize': [100, 200, 500]})

# Worker
def process_image(message):
    image = download(message['url'])
    for size in message['resize']:
        resized = resize_image(image, size)
        upload(resized)
```

### Priority Queue

**Higher priority messages processed first**

```python
# RabbitMQ
channel.queue_declare(queue='tasks', arguments={'x-max-priority': 10})

# Publish with priority
channel.basic_publish(
    exchange='',
    routing_key='tasks',
    body='urgent task',
    properties=pika.BasicProperties(priority=9)
)
```

### Delayed/Scheduled Messages

**Process message after delay**

```python
# RabbitMQ with delayed exchange plugin
channel.basic_publish(
    exchange='delayed',
    routing_key='tasks',
    body='reminder',
    properties=pika.BasicProperties(headers={'x-delay': 60000})  # 60s delay
)
```

### Dead Letter Queue (DLQ)

**Failed messages sent to separate queue**

```python
# RabbitMQ
channel.queue_declare(
    queue='tasks',
    arguments={
        'x-dead-letter-exchange': 'dlx',
        'x-dead-letter-routing-key': 'failed_tasks',
        'x-message-ttl': 60000,  # Expire after 60s if not processed
        'x-max-retries': 3
    }
)

# Messages that fail 3 times → DLQ
# Manual inspection/reprocessing
```

---

## Event Streaming vs Message Queues

### Message Queue (RabbitMQ, SQS)

**Characteristics**:
- Messages deleted after consumption
- Low latency
- Limited retention
- Consumer-driven

**Use Cases**:
- Task queues
- Request/reply
- Command processing

### Event Stream (Kafka)

**Characteristics**:
- Events retained (days/weeks)
- High throughput
- Replay capability
- Multiple consumers

**Use Cases**:
- Event sourcing
- Change data capture
- Real-time analytics
- Log aggregation

**Comparison**:
| Feature | Message Queue | Event Stream |
|---------|--------------|--------------|
| Retention | Short (minutes) | Long (days/weeks) |
| Consumption | Destructive | Non-destructive |
| Ordering | Best-effort | Guaranteed (per partition) |
| Throughput | Moderate | Very high |
| Replay | No | Yes |

---

## Interview Q&A

**Q: "Design a notification system for an e-commerce platform"**

**Answer**:
```
I'd use a pub-sub architecture with Kafka:

Architecture:

                    [Order Events Topic]
                            ↓
        ┌───────────────────┼───────────────────┐
        ↓                   ↓                   ↓
[Email Consumer]    [SMS Consumer]    [Push Consumer]
(sends emails)      (sends SMS)       (push notifications)

Components:

1. Event Publisher (Order Service):
```python
# When order placed
kafka_producer.send(
    'order-events',
    key=str(order.user_id),  # Partition by user
    value={
        'event_type': 'order_placed',
        'order_id': order.id,
        'user_id': order.user_id,
        'amount': order.total,
        'timestamp': now()
    }
)
```

2. Email Consumer:
```python
consumer = KafkaConsumer(
    'order-events',
    group_id='email-service',
    auto_offset_reset='latest'
)

for message in consumer:
    event = message.value

    if event['event_type'] == 'order_placed':
        send_order_confirmation_email(event['user_id'], event['order_id'])

    elif event['event_type'] == 'order_shipped':
        send_shipping_notification(event['user_id'], event['tracking_number'])
```

3. SMS Consumer (similar):
```python
consumer = KafkaConsumer(
    'order-events',
    group_id='sms-service',  # Different group = receives all events
    auto_offset_reset='latest'
)

# Send SMS for high-value orders
for message in consumer:
    event = message.value

    if event['event_type'] == 'order_placed' and event['amount'] > 500:
        send_sms_confirmation(event['user_id'])
```

Features:

1. User Preferences:
```python
# Filter based on user settings
if user_preferences[user_id]['email_enabled']:
    send_email()

if user_preferences[user_id]['sms_enabled']:
    send_sms()
```

2. Retry Logic (with exponential backoff):
```python
@retry(max_attempts=3, backoff=exponential)
def send_email(user_id, order_id):
    email_service.send(...)
```

3. Dead Letter Queue:
```python
try:
    send_notification()
except Exception:
    # After retries exhausted
    dlq_producer.send('failed-notifications', message)
```

4. Idempotency:
```python
# Track sent notifications in DB
if notification_sent(user_id, event_id):
    return  # Already sent

send_notification()
mark_as_sent(user_id, event_id)
```

5. Rate Limiting:
```python
rate_limiter = TokenBucket(100 emails/minute)

if rate_limiter.consume():
    send_email()
else:
    # Delay or drop (based on priority)
    schedule_later()
```

Why Kafka:
- Multiple consumers (email, SMS, push) receive same events
- Replay capability (re-send notifications if needed)
- High throughput (handle millions of orders)
- Ordering per user (partition by user_id)
- Scalable (add more consumers)

Trade-offs:
- Complexity: Need to manage Kafka cluster
- But: Decoupled, scalable, reliable
- Alternative: SNS + SQS (simpler, managed, but less flexible)
```

---

## Key Takeaways

1. **Async Communication**: Decouple services with message queues
2. **Queue vs Topic**: Point-to-point for tasks, pub-sub for events
3. **Delivery Guarantees**: Choose based on requirements (at-least-once common)
4. **Idempotency**: Essential for at-least-once delivery
5. **Kafka vs RabbitMQ**: Stream processing vs traditional messaging

---

## Further Reading

- "Designing Data-Intensive Applications" - Chapter 11
- Kafka: The Definitive Guide
- RabbitMQ in Depth
- Enterprise Integration Patterns
- AWS messaging services documentation
- Event-Driven Architecture patterns
