# Design a Payment Gateway System

**Companies**: Stripe, PayPal, Square, Adyen, Braintree
**Difficulty**: Advanced
**Time**: 60-90 minutes

## Problem Statement

Design a payment gateway system that processes online payments securely and reliably. The system must handle payment authorization, capture, refunds, fraud detection, and maintain financial integrity with ACID guarantees.

---

## Step 1: Requirements (5-10 min)

### Functional Requirements

1. **Payment Processing**: Accept credit/debit card payments
2. **Payment Methods**: Support multiple payment methods (cards, wallets, bank transfers)
3. **Multi-Currency**: Handle payments in different currencies
4. **Transaction States**: Authorization, capture, void, refund
5. **Fraud Detection**: Real-time fraud analysis
6. **Reconciliation**: Daily settlement and reconciliation
7. **Webhooks**: Notify merchants of payment status
8. **PCI Compliance**: Secure handling of card data
9. **Idempotency**: Prevent duplicate payments
10. **Retry Logic**: Handle network failures gracefully

**Prioritize**: Focus on authorization/capture, ACID compliance, and fraud detection for MVP

### Non-Functional Requirements

1. **Consistency**: ACID transactions (financial data must be consistent)
2. **Availability**: 99.99% uptime (4 nines)
3. **Security**: PCI-DSS Level 1 compliance
4. **Latency**: <500ms for payment authorization
5. **Durability**: Zero data loss (financial records)
6. **Auditability**: Complete audit trail for all transactions
7. **Scalability**: Handle millions of transactions per day
8. **Compliance**: SOC 2, GDPR, regional regulations

### Capacity Estimation

**Assumptions**:
- 10M transactions per day
- Average transaction value: $50
- Peak hours: 8 hours/day (2x average)
- Transaction storage: 2KB per transaction
- Audit log: 5KB per transaction

**Write Load**:
```
10M transactions/day ÷ 86,400 sec = ~116 TPS (transactions per second)
Peak: 116 × 2 = ~230 TPS
```

**Read Load** (status checks, analytics):
```
Assume 5:1 read-write ratio = ~580 reads/sec
Peak: ~1,160 reads/sec
```

**Storage**:
```
Transactions: 10M/day × 2KB × 365 days × 5 years = 36.5 TB
Audit logs: 10M/day × 5KB × 365 days × 5 years = 91 TB
Total: ~128 TB (5-year retention)
```

**Throughput**:
```
Daily payment volume: 10M × $50 = $500M/day
Monthly: ~$15B/month
Annual: ~$180B/year
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                    Merchant/Client                      │
└──────────────────────┬──────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────┐
│            API Gateway (Rate Limiting, Auth)            │
└──────────────────────┬──────────────────────────────────┘
                       │
        ┌──────────────┼──────────────┐
        ▼              ▼              ▼
┌──────────────┐  ┌──────────┐  ┌──────────────┐
│  Payment     │  │  Fraud   │  │  Webhook     │
│  Service     │  │  Service │  │  Service     │
└──────┬───────┘  └─────┬────┘  └──────────────┘
       │                │
       ▼                ▼
┌──────────────────────────────────────┐
│    Payment Processor Integration     │
│  (Visa, Mastercard, PayPal, etc.)   │
└──────────────────┬───────────────────┘
                   │
        ┌──────────┴──────────┐
        ▼                     ▼
┌──────────────┐      ┌──────────────┐
│ PostgreSQL   │      │   Redis      │
│ (ACID DB)    │      │   Cache      │
└──────────────┘      └──────────────┘
        │
        ▼
┌──────────────┐
│  Analytics   │
│  (ClickHouse)│
└──────────────┘
```

### Components

**API Gateway**:
- TLS termination
- Authentication (API keys, OAuth)
- Rate limiting (per merchant)
- Request validation

**Payment Service**:
- Core payment processing logic
- Transaction state machine
- Idempotency handling
- Database transactions (ACID)

**Fraud Detection Service**:
- Real-time risk scoring
- ML-based fraud detection
- Rule engine for fraud rules
- Velocity checks

**Webhook Service**:
- Asynchronous notification delivery
- Retry with exponential backoff
- Webhook signature verification

**Payment Processor Integration**:
- Abstract layer for different processors
- Circuit breaker pattern
- Fallback to backup processors

---

## Step 3: Data Model (15 min)

### Database Choice

**CRITICAL**: For payment systems, we MUST use a relational database with ACID guarantees.

**PostgreSQL** (Recommended):
- ACID compliance (absolutely critical)
- Strong consistency
- Advanced locking mechanisms
- Audit trail support
- Battle-tested for financial systems

**Why NOT NoSQL**:
- Eventual consistency is unacceptable for money
- Risk of double charging or lost payments
- Weak transaction guarantees

### Schema Design

```sql
-- Payment Transactions (Core ledger)
CREATE TABLE payment_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    customer_id UUID,

    -- Payment details
    amount DECIMAL(19, 4) NOT NULL, -- Precise decimal for money
    currency CHAR(3) NOT NULL, -- ISO 4217 (USD, EUR, etc.)
    payment_method_id UUID NOT NULL,

    -- Transaction state
    status VARCHAR(20) NOT NULL, -- PENDING, AUTHORIZED, CAPTURED, FAILED, REFUNDED
    gateway_transaction_id VARCHAR(255), -- External processor ID

    -- ACID & Idempotency
    idempotency_key VARCHAR(255) UNIQUE NOT NULL,
    version INTEGER NOT NULL DEFAULT 1, -- Optimistic locking

    -- Financial constraints
    authorized_amount DECIMAL(19, 4),
    captured_amount DECIMAL(19, 4) DEFAULT 0,
    refunded_amount DECIMAL(19, 4) DEFAULT 0,

    -- Metadata
    description TEXT,
    metadata JSONB, -- Flexible merchant data

    -- Audit trail
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    authorized_at TIMESTAMP,
    captured_at TIMESTAMP,

    -- Constraints to ensure financial integrity
    CONSTRAINT positive_amounts CHECK (amount > 0),
    CONSTRAINT captured_lte_authorized CHECK (captured_amount <= authorized_amount),
    CONSTRAINT refunded_lte_captured CHECK (refunded_amount <= captured_amount)
);

-- Indexes for performance
CREATE INDEX idx_merchant_id ON payment_transactions(merchant_id, created_at DESC);
CREATE INDEX idx_customer_id ON payment_transactions(customer_id);
CREATE INDEX idx_status ON payment_transactions(status);
CREATE INDEX idx_idempotency ON payment_transactions(idempotency_key);

-- Payment Methods (Tokenized - PCI Compliance)
CREATE TABLE payment_methods (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL, -- CARD, BANK_ACCOUNT, WALLET

    -- Tokenized data (never store real card numbers!)
    token VARCHAR(255) NOT NULL UNIQUE, -- From payment processor
    fingerprint VARCHAR(255), -- For duplicate detection

    -- Card metadata (safe to store)
    card_brand VARCHAR(20), -- VISA, MASTERCARD, AMEX
    card_last4 VARCHAR(4),
    card_exp_month INTEGER,
    card_exp_year INTEGER,

    -- Billing details
    billing_address_id UUID,

    -- Status
    is_default BOOLEAN DEFAULT FALSE,
    is_active BOOLEAN DEFAULT TRUE,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_payment_methods ON payment_methods(customer_id, is_active);

-- Transaction Events (Immutable audit log)
CREATE TABLE transaction_events (
    id BIGSERIAL PRIMARY KEY,
    transaction_id UUID NOT NULL REFERENCES payment_transactions(id),

    -- Event details
    event_type VARCHAR(50) NOT NULL, -- AUTHORIZED, CAPTURED, REFUNDED, FAILED
    event_data JSONB NOT NULL,

    -- Audit
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255), -- System or user ID

    -- Immutability: NO updates or deletes allowed
    CONSTRAINT no_updates CHECK (FALSE) -- Trigger-enforced
);

-- Partition by month for performance
CREATE INDEX idx_transaction_events_tx ON transaction_events(transaction_id, occurred_at DESC);

-- Fraud Checks
CREATE TABLE fraud_checks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_id UUID NOT NULL REFERENCES payment_transactions(id),

    -- Risk assessment
    risk_score DECIMAL(5, 2) NOT NULL, -- 0.00 to 100.00
    risk_level VARCHAR(20) NOT NULL, -- LOW, MEDIUM, HIGH, CRITICAL

    -- Fraud signals
    signals JSONB NOT NULL, -- Array of triggered rules

    -- Decision
    decision VARCHAR(20) NOT NULL, -- APPROVED, DECLINED, REVIEW
    reviewed_by UUID, -- Manual review

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_fraud_checks_tx ON fraud_checks(transaction_id);
CREATE INDEX idx_fraud_risk ON fraud_checks(risk_level, created_at DESC);

-- Settlement Batches (Reconciliation)
CREATE TABLE settlement_batches (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,

    -- Batch details
    batch_date DATE NOT NULL,
    total_amount DECIMAL(19, 4) NOT NULL,
    total_fees DECIMAL(19, 4) NOT NULL,
    net_amount DECIMAL(19, 4) NOT NULL,
    transaction_count INTEGER NOT NULL,
    currency CHAR(3) NOT NULL,

    -- Settlement status
    status VARCHAR(20) NOT NULL, -- PENDING, PROCESSING, COMPLETED, FAILED
    settled_at TIMESTAMP,

    -- External reference
    settlement_reference VARCHAR(255),

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT net_calculation CHECK (net_amount = total_amount - total_fees)
);

-- Webhook Deliveries (Retry tracking)
CREATE TABLE webhook_deliveries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    merchant_id UUID NOT NULL,
    transaction_id UUID NOT NULL,

    -- Webhook details
    endpoint_url TEXT NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload JSONB NOT NULL,

    -- Delivery status
    status VARCHAR(20) NOT NULL, -- PENDING, DELIVERED, FAILED
    attempts INTEGER DEFAULT 0,
    last_attempt_at TIMESTAMP,
    next_retry_at TIMESTAMP,

    -- Response tracking
    response_status_code INTEGER,
    response_body TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_webhook_retry ON webhook_deliveries(status, next_retry_at)
WHERE status = 'PENDING';
```

### Money Handling - CRITICAL PATTERNS

```python
# ALWAYS use Decimal for money, NEVER float!
from decimal import Decimal, ROUND_HALF_UP

class Money:
    """
    Immutable money object with currency.
    Prevents common financial bugs.
    """
    def __init__(self, amount: Decimal, currency: str):
        if not isinstance(amount, Decimal):
            raise TypeError("Amount must be Decimal type")

        self.amount = amount.quantize(
            Decimal('0.0001'),  # 4 decimal places
            rounding=ROUND_HALF_UP
        )
        self.currency = currency

    def __add__(self, other):
        if self.currency != other.currency:
            raise ValueError("Cannot add different currencies")
        return Money(self.amount + other.amount, self.currency)

    def __eq__(self, other):
        return self.amount == other.amount and self.currency == other.currency

# Example usage
price = Money(Decimal('19.99'), 'USD')
tax = Money(Decimal('2.00'), 'USD')
total = price + tax  # Money(Decimal('21.99'), 'USD')
```

---

## Step 4: APIs (10 min)

### Create Payment Intent

```http
POST /v1/payment-intents
Authorization: Bearer sk_live_...
Idempotency-Key: uuid-v4-here
Content-Type: application/json

Request:
{
  "amount": 1999, // cents
  "currency": "usd",
  "payment_method": "pm_card_visa",
  "customer_id": "cus_123",
  "description": "Order #1234",
  "capture_method": "automatic", // or "manual"
  "metadata": {
    "order_id": "1234",
    "customer_email": "user@example.com"
  }
}

Response: 201 Created
{
  "id": "pi_3abc123",
  "object": "payment_intent",
  "amount": 1999,
  "currency": "usd",
  "status": "requires_confirmation",
  "client_secret": "pi_3abc123_secret_xyz",
  "payment_method": "pm_card_visa",
  "created": 1699564800,
  "metadata": {...}
}
```

### Confirm Payment

```http
POST /v1/payment-intents/{id}/confirm
Authorization: Bearer sk_live_...

Request:
{
  "payment_method": "pm_card_visa",
  "return_url": "https://merchant.com/payment/complete"
}

Response: 200 OK
{
  "id": "pi_3abc123",
  "status": "succeeded", // or "requires_action" for 3DS
  "amount_received": 1999,
  "charges": [{
    "id": "ch_123",
    "amount": 1999,
    "status": "succeeded",
    "authorization_code": "123456"
  }]
}
```

### Create Refund

```http
POST /v1/refunds
Authorization: Bearer sk_live_...
Idempotency-Key: uuid-v4-here

Request:
{
  "payment_intent": "pi_3abc123",
  "amount": 1999, // full or partial
  "reason": "requested_by_customer",
  "metadata": {
    "refund_ticket": "RT-5678"
  }
}

Response: 201 Created
{
  "id": "re_123",
  "object": "refund",
  "amount": 1999,
  "status": "succeeded",
  "payment_intent": "pi_3abc123",
  "reason": "requested_by_customer",
  "created": 1699564900
}
```

### Webhook Events

```http
POST https://merchant.com/webhooks/stripe
Stripe-Signature: t=1699564800,v1=abc123...

Payload:
{
  "id": "evt_123",
  "object": "event",
  "type": "payment_intent.succeeded",
  "data": {
    "object": {
      "id": "pi_3abc123",
      "status": "succeeded",
      "amount": 1999
    }
  }
}
```

---

## Step 5: Core Algorithms & State Machine

### Payment State Machine

```python
from enum import Enum
from typing import Optional

class PaymentStatus(Enum):
    CREATED = "created"
    PENDING = "pending"
    AUTHORIZED = "authorized"
    CAPTURED = "captured"
    PARTIALLY_REFUNDED = "partially_refunded"
    REFUNDED = "refunded"
    FAILED = "failed"
    CANCELED = "canceled"

class PaymentStateMachine:
    """
    Enforces valid state transitions for payments.
    CRITICAL: Prevents invalid financial operations!
    """

    VALID_TRANSITIONS = {
        PaymentStatus.CREATED: [PaymentStatus.PENDING, PaymentStatus.CANCELED],
        PaymentStatus.PENDING: [PaymentStatus.AUTHORIZED, PaymentStatus.FAILED, PaymentStatus.CANCELED],
        PaymentStatus.AUTHORIZED: [PaymentStatus.CAPTURED, PaymentStatus.CANCELED, PaymentStatus.FAILED],
        PaymentStatus.CAPTURED: [PaymentStatus.PARTIALLY_REFUNDED, PaymentStatus.REFUNDED],
        PaymentStatus.PARTIALLY_REFUNDED: [PaymentStatus.REFUNDED],
        PaymentStatus.REFUNDED: [],  # Terminal state
        PaymentStatus.FAILED: [],    # Terminal state
        PaymentStatus.CANCELED: []   # Terminal state
    }

    @classmethod
    def can_transition(cls, from_status: PaymentStatus, to_status: PaymentStatus) -> bool:
        return to_status in cls.VALID_TRANSITIONS.get(from_status, [])

    @classmethod
    def validate_transition(cls, from_status: PaymentStatus, to_status: PaymentStatus):
        if not cls.can_transition(from_status, to_status):
            raise InvalidTransitionError(
                f"Cannot transition from {from_status.value} to {to_status.value}"
            )
```

### Idempotency Implementation

```python
import hashlib
from typing import Optional
from dataclasses import dataclass
from datetime import datetime, timedelta

@dataclass
class IdempotentRequest:
    key: str
    created_at: datetime
    response: dict

class IdempotencyService:
    """
    Prevents duplicate payment processing.
    CRITICAL: Must be idempotent for at-least-once delivery!
    """

    def __init__(self, redis_client, ttl_hours: int = 24):
        self.redis = redis_client
        self.ttl = ttl_hours * 3600

    async def get_or_create(self, idempotency_key: str, request_hash: str) -> Optional[dict]:
        """
        Check if request with this idempotency key was already processed.
        Returns cached response if found, None otherwise.
        """
        cache_key = f"idempotency:{idempotency_key}"

        # Get existing response
        cached = await self.redis.get(cache_key)
        if cached:
            stored_request = IdempotentRequest(**json.loads(cached))

            # Verify request body hasn't changed (prevent key reuse with different params)
            stored_hash = hashlib.sha256(
                json.dumps(stored_request.response, sort_keys=True).encode()
            ).hexdigest()

            if stored_hash == request_hash:
                return stored_request.response
            else:
                raise IdempotencyConflictError(
                    "Idempotency key reused with different parameters"
                )

        return None

    async def store_response(self, idempotency_key: str, response: dict):
        """Store successful response for future duplicate requests."""
        cache_key = f"idempotency:{idempotency_key}"

        idempotent_request = IdempotentRequest(
            key=idempotency_key,
            created_at=datetime.utcnow(),
            response=response
        )

        await self.redis.setex(
            cache_key,
            self.ttl,
            json.dumps(idempotent_request.__dict__, default=str)
        )
```

### Two-Phase Commit for Payment Processing

```python
from contextlib import asynccontextmanager
from typing import AsyncContextManager

class PaymentProcessor:
    """
    Implements two-phase commit pattern for payment processing.
    Phase 1: Authorize (hold funds)
    Phase 2: Capture (transfer funds)
    """

    async def authorize_payment(
        self,
        payment_intent: PaymentIntent,
        payment_method: PaymentMethod
    ) -> Authorization:
        """
        Phase 1: Reserve funds on customer's card.
        Creates authorization that expires in 7 days.
        """
        async with self.db_transaction() as tx:
            # 1. Validate payment can be authorized
            if payment_intent.status != PaymentStatus.PENDING:
                raise InvalidStateError("Payment must be in PENDING state")

            # 2. Call payment processor (Stripe, etc.)
            try:
                auth_result = await self.processor.authorize(
                    amount=payment_intent.amount,
                    currency=payment_intent.currency,
                    payment_method=payment_method.token
                )
            except ProcessorError as e:
                # Mark as failed and rollback
                await tx.update_payment_status(
                    payment_intent.id,
                    PaymentStatus.FAILED,
                    error=str(e)
                )
                raise

            # 3. Update payment intent with authorization
            await tx.update_payment(
                payment_intent.id,
                status=PaymentStatus.AUTHORIZED,
                authorized_amount=payment_intent.amount,
                authorization_code=auth_result.code,
                authorization_expires_at=datetime.utcnow() + timedelta(days=7)
            )

            # 4. Record audit event
            await tx.create_event(
                payment_intent.id,
                event_type="payment.authorized",
                data=auth_result.to_dict()
            )

            # 5. Commit transaction
            await tx.commit()

            return Authorization(
                id=auth_result.id,
                amount=payment_intent.amount,
                expires_at=datetime.utcnow() + timedelta(days=7)
            )

    async def capture_payment(
        self,
        payment_intent: PaymentIntent,
        amount: Optional[Decimal] = None
    ) -> Capture:
        """
        Phase 2: Capture previously authorized funds.
        Can capture partial amount.
        """
        async with self.db_transaction() as tx:
            # 1. Validate can capture
            if payment_intent.status != PaymentStatus.AUTHORIZED:
                raise InvalidStateError("Payment must be AUTHORIZED to capture")

            capture_amount = amount or payment_intent.authorized_amount

            if capture_amount > payment_intent.authorized_amount:
                raise ValueError("Cannot capture more than authorized amount")

            # 2. Call payment processor
            try:
                capture_result = await self.processor.capture(
                    authorization_id=payment_intent.authorization_id,
                    amount=capture_amount
                )
            except ProcessorError as e:
                await tx.create_event(
                    payment_intent.id,
                    event_type="payment.capture_failed",
                    data={"error": str(e)}
                )
                raise

            # 3. Update payment with captured amount
            new_captured = payment_intent.captured_amount + capture_amount

            await tx.update_payment(
                payment_intent.id,
                status=PaymentStatus.CAPTURED,
                captured_amount=new_captured,
                captured_at=datetime.utcnow()
            )

            # 4. Record audit event
            await tx.create_event(
                payment_intent.id,
                event_type="payment.captured",
                data={
                    "amount": str(capture_amount),
                    "total_captured": str(new_captured)
                }
            )

            # 5. Enqueue webhook notification (async)
            await self.webhook_queue.enqueue(
                merchant_id=payment_intent.merchant_id,
                event_type="payment_intent.succeeded",
                payload=payment_intent.to_dict()
            )

            await tx.commit()

            return Capture(
                id=capture_result.id,
                amount=capture_amount,
                captured_at=datetime.utcnow()
            )

    @asynccontextmanager
    async def db_transaction(self) -> AsyncContextManager:
        """Database transaction with automatic rollback on error."""
        async with self.db.begin() as tx:
            try:
                yield tx
            except Exception:
                await tx.rollback()
                raise
```

---

## Step 6: Optimizations & Trade-offs (20 min)

### 1. ACID Compliance & Financial Integrity

**Database Isolation Levels**:

```sql
-- CRITICAL: Use SERIALIZABLE isolation for financial transactions
BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

-- Example: Prevent double-capture
UPDATE payment_transactions
SET
    status = 'CAPTURED',
    captured_amount = captured_amount + 19.99,
    version = version + 1
WHERE
    id = 'pi_123'
    AND status = 'AUTHORIZED'
    AND version = 5  -- Optimistic locking
    AND (captured_amount + 19.99) <= authorized_amount;  -- Safety check

-- Check if update succeeded
IF @@ROWCOUNT = 0 THEN
    ROLLBACK;
    RAISE EXCEPTION 'Concurrent modification or invalid capture amount';
END IF;

COMMIT;
```

**Optimistic Locking**:
```python
async def update_payment_with_optimistic_lock(
    payment_id: UUID,
    current_version: int,
    updates: dict
) -> Payment:
    """
    Prevents lost updates in concurrent scenarios.
    """
    result = await db.execute(
        """
        UPDATE payment_transactions
        SET
            status = $1,
            version = version + 1,
            updated_at = CURRENT_TIMESTAMP
        WHERE
            id = $2
            AND version = $3
        RETURNING *
        """,
        updates['status'],
        payment_id,
        current_version
    )

    if not result:
        raise ConcurrentModificationError(
            f"Payment {payment_id} was modified by another process"
        )

    return Payment(**result)
```

### 2. Fraud Detection System

```python
from typing import List, Dict
from dataclasses import dataclass

@dataclass
class FraudSignal:
    rule_id: str
    severity: float  # 0.0 to 1.0
    description: str

class FraudDetectionEngine:
    """
    Real-time fraud detection with ML and rule-based approaches.
    """

    def __init__(self, ml_model, rule_engine):
        self.ml_model = ml_model
        self.rule_engine = rule_engine

    async def assess_risk(
        self,
        payment: PaymentIntent,
        context: Dict
    ) -> FraudAssessment:
        """
        Multi-layered fraud detection:
        1. Rule-based checks (velocity, blacklist, etc.)
        2. ML model scoring
        3. Device fingerprinting
        4. Behavioral analysis
        """
        signals: List[FraudSignal] = []

        # 1. Velocity checks (same card used multiple times in short period)
        velocity_signal = await self.check_velocity(
            payment_method_id=payment.payment_method_id,
            time_window_minutes=60,
            max_attempts=5
        )
        if velocity_signal:
            signals.append(velocity_signal)

        # 2. Geographic anomaly (card used in different countries)
        geo_signal = await self.check_geographic_anomaly(
            customer_id=payment.customer_id,
            ip_address=context['ip_address'],
            billing_country=context['billing_country']
        )
        if geo_signal:
            signals.append(geo_signal)

        # 3. Amount anomaly (unusually large transaction)
        amount_signal = await self.check_amount_anomaly(
            customer_id=payment.customer_id,
            amount=payment.amount,
            currency=payment.currency
        )
        if amount_signal:
            signals.append(amount_signal)

        # 4. ML model prediction
        ml_score = await self.ml_model.predict(
            features=self.extract_features(payment, context)
        )

        # 5. Device fingerprint check
        device_signal = await self.check_device_fingerprint(
            fingerprint=context.get('device_fingerprint'),
            customer_id=payment.customer_id
        )
        if device_signal:
            signals.append(device_signal)

        # Combine signals into final risk score
        rule_score = sum(s.severity for s in signals) / len(signals) if signals else 0
        final_score = (rule_score * 0.4) + (ml_score * 0.6)  # Weighted combination

        # Determine risk level and decision
        if final_score >= 0.8:
            risk_level = "CRITICAL"
            decision = "DECLINED"
        elif final_score >= 0.5:
            risk_level = "HIGH"
            decision = "REVIEW"  # Manual review queue
        elif final_score >= 0.3:
            risk_level = "MEDIUM"
            decision = "APPROVED"  # But monitor closely
        else:
            risk_level = "LOW"
            decision = "APPROVED"

        return FraudAssessment(
            risk_score=final_score,
            risk_level=risk_level,
            decision=decision,
            signals=signals,
            ml_score=ml_score
        )

    async def check_velocity(
        self,
        payment_method_id: UUID,
        time_window_minutes: int,
        max_attempts: int
    ) -> Optional[FraudSignal]:
        """Check if card is being used too frequently."""
        count = await db.execute(
            """
            SELECT COUNT(*)
            FROM payment_transactions
            WHERE
                payment_method_id = $1
                AND created_at >= NOW() - INTERVAL '$2 minutes'
                AND status IN ('AUTHORIZED', 'CAPTURED')
            """,
            payment_method_id,
            time_window_minutes
        )

        if count > max_attempts:
            return FraudSignal(
                rule_id="velocity_exceeded",
                severity=0.7,
                description=f"Card used {count} times in {time_window_minutes} minutes"
            )

        return None
```

### 3. Webhook Delivery with Retry

```python
import asyncio
from typing import Optional

class WebhookDeliveryService:
    """
    Reliable webhook delivery with exponential backoff retry.
    """

    MAX_RETRIES = 5
    INITIAL_DELAY = 5  # seconds

    async def deliver_webhook(
        self,
        merchant_id: UUID,
        event_type: str,
        payload: dict
    ):
        """
        Deliver webhook with automatic retry on failure.
        """
        # Get merchant webhook configuration
        webhook_config = await self.get_webhook_config(merchant_id)

        if not webhook_config or not webhook_config.enabled:
            return

        # Create delivery record
        delivery = await db.create_webhook_delivery(
            merchant_id=merchant_id,
            endpoint_url=webhook_config.url,
            event_type=event_type,
            payload=payload,
            status='PENDING'
        )

        # Attempt delivery with retries
        for attempt in range(self.MAX_RETRIES):
            try:
                # Sign payload
                signature = self.sign_payload(
                    payload,
                    webhook_config.secret
                )

                # Send HTTP POST
                async with aiohttp.ClientSession() as session:
                    async with session.post(
                        webhook_config.url,
                        json=payload,
                        headers={
                            'Content-Type': 'application/json',
                            'X-Webhook-Signature': signature,
                            'X-Webhook-Event-Type': event_type
                        },
                        timeout=aiohttp.ClientTimeout(total=30)
                    ) as response:
                        response_body = await response.text()

                        # Update delivery record
                        await db.update_webhook_delivery(
                            delivery.id,
                            status='DELIVERED' if response.status == 200 else 'FAILED',
                            attempts=attempt + 1,
                            response_status_code=response.status,
                            response_body=response_body[:1000],  # Truncate
                            last_attempt_at=datetime.utcnow()
                        )

                        if response.status == 200:
                            return  # Success!

            except Exception as e:
                await db.update_webhook_delivery(
                    delivery.id,
                    status='FAILED',
                    attempts=attempt + 1,
                    response_body=str(e)[:1000],
                    last_attempt_at=datetime.utcnow()
                )

            # Exponential backoff before retry
            if attempt < self.MAX_RETRIES - 1:
                delay = self.INITIAL_DELAY * (2 ** attempt)  # 5, 10, 20, 40, 80 seconds
                await asyncio.sleep(delay)

        # All retries failed - send to dead letter queue
        await self.send_to_dlq(delivery)

    def sign_payload(self, payload: dict, secret: str) -> str:
        """Generate HMAC signature for webhook verification."""
        import hmac
        import hashlib

        payload_str = json.dumps(payload, sort_keys=True)
        signature = hmac.new(
            secret.encode(),
            payload_str.encode(),
            hashlib.sha256
        ).hexdigest()

        return signature
```

### 4. Settlement & Reconciliation

```python
from datetime import date, timedelta
from decimal import Decimal

class SettlementService:
    """
    Daily settlement and reconciliation process.
    Ensures merchant payouts are accurate.
    """

    async def create_daily_settlement(
        self,
        merchant_id: UUID,
        settlement_date: date
    ) -> SettlementBatch:
        """
        Create settlement batch for merchant's daily transactions.
        """
        # Get all captured transactions for the day
        transactions = await db.execute(
            """
            SELECT
                id,
                amount,
                currency,
                captured_at
            FROM payment_transactions
            WHERE
                merchant_id = $1
                AND status = 'CAPTURED'
                AND DATE(captured_at) = $2
                AND settlement_batch_id IS NULL
            FOR UPDATE  -- Lock rows to prevent double settlement
            """,
            merchant_id,
            settlement_date
        )

        if not transactions:
            return None  # No transactions to settle

        # Calculate totals
        total_amount = sum(Decimal(t['amount']) for t in transactions)
        transaction_count = len(transactions)

        # Calculate fees (e.g., 2.9% + $0.30 per transaction)
        total_fees = sum(
            (Decimal(t['amount']) * Decimal('0.029')) + Decimal('0.30')
            for t in transactions
        )

        # Net amount to merchant
        net_amount = total_amount - total_fees

        # Create settlement batch
        batch = await db.create_settlement_batch(
            merchant_id=merchant_id,
            batch_date=settlement_date,
            total_amount=total_amount,
            total_fees=total_fees,
            net_amount=net_amount,
            transaction_count=transaction_count,
            currency='USD',
            status='PENDING'
        )

        # Link transactions to batch
        await db.execute(
            """
            UPDATE payment_transactions
            SET settlement_batch_id = $1
            WHERE id = ANY($2)
            """,
            batch.id,
            [t['id'] for t in transactions]
        )

        # Initiate payout to merchant bank account
        await self.initiate_payout(batch)

        return batch

    async def reconcile_settlement(
        self,
        batch: SettlementBatch
    ) -> ReconciliationReport:
        """
        Reconcile settlement batch with payment processor.
        Detect discrepancies.
        """
        # Get processor's settlement report
        processor_report = await self.processor.get_settlement_report(
            batch.batch_date
        )

        # Compare our records with processor
        our_total = batch.total_amount
        processor_total = Decimal(processor_report['total_amount'])

        discrepancy = abs(our_total - processor_total)

        if discrepancy > Decimal('0.01'):  # Allow 1 cent tolerance
            # Investigate discrepancy
            await self.create_reconciliation_alert(
                batch_id=batch.id,
                our_total=our_total,
                processor_total=processor_total,
                discrepancy=discrepancy
            )

        return ReconciliationReport(
            batch_id=batch.id,
            our_total=our_total,
            processor_total=processor_total,
            discrepancy=discrepancy,
            is_balanced=(discrepancy <= Decimal('0.01'))
        )
```

---

## Step 7: Advanced Considerations

### 1. PCI-DSS Compliance

**Never Store Sensitive Card Data**:
```python
# ❌ NEVER DO THIS
class PaymentMethod:
    card_number: str  # VIOLATION!
    cvv: str          # VIOLATION!

# ✅ CORRECT APPROACH
class PaymentMethod:
    token: str           # Tokenized reference from processor
    card_last4: str      # Safe to store
    card_brand: str      # Safe to store
    card_fingerprint: str  # Hash for duplicate detection
```

**PCI Compliance Checklist**:
- [ ] Use tokenization for card storage
- [ ] Encrypt data in transit (TLS 1.2+)
- [ ] Encrypt data at rest
- [ ] Implement access controls (least privilege)
- [ ] Maintain audit logs
- [ ] Regular security scanning
- [ ] Network segmentation (isolate cardholder data)
- [ ] Strong cryptography for key management

### 2. Multi-Currency Support

```python
from decimal import Decimal
from typing import Dict

class CurrencyConverter:
    """
    Handle multi-currency transactions with exchange rates.
    """

    async def convert(
        self,
        amount: Decimal,
        from_currency: str,
        to_currency: str,
        rate_timestamp: datetime
    ) -> Decimal:
        """
        Convert amount using historical exchange rate.
        CRITICAL: Use rate at transaction time, not current rate!
        """
        if from_currency == to_currency:
            return amount

        # Get exchange rate for the specific timestamp
        rate = await self.get_exchange_rate(
            from_currency=from_currency,
            to_currency=to_currency,
            timestamp=rate_timestamp
        )

        converted = amount * rate

        # Round to currency's minor units
        return self.round_to_currency_precision(converted, to_currency)

    def round_to_currency_precision(
        self,
        amount: Decimal,
        currency: str
    ) -> Decimal:
        """
        Round to appropriate decimal places for currency.
        """
        # Most currencies use 2 decimal places
        # Some (JPY, KRW) use 0
        # Some (BHD, KWD) use 3

        precision_map = {
            'JPY': 0, 'KRW': 0,  # Zero decimal currencies
            'BHD': 3, 'KWD': 3, 'OMR': 3,  # Three decimal currencies
        }

        precision = precision_map.get(currency, 2)  # Default to 2

        quantize_value = Decimal(10) ** -precision
        return amount.quantize(quantize_value, rounding=ROUND_HALF_UP)
```

### 3. High Availability & Disaster Recovery

**Multi-Region Architecture**:
```
Primary Region (us-east-1):
  - Active database (PostgreSQL Primary)
  - Redis cache
  - Application servers

Secondary Region (eu-west-1):
  - Read replica database
  - Redis cache (replicated)
  - Application servers (standby)

Failover Strategy:
  - RTO: 5 minutes (Recovery Time Objective)
  - RPO: 30 seconds (Recovery Point Objective)
  - Automatic DNS failover
  - Promote replica to primary
```

**Database Backup Strategy**:
```python
# Continuous backup with point-in-time recovery
- Automated daily snapshots
- Transaction log shipping (every 30 seconds)
- Cross-region backup replication
- 7-year retention for compliance
- Encrypted backups (AES-256)
```

### 4. Monitoring & Alerting

**Critical Metrics**:
```python
from prometheus_client import Counter, Histogram, Gauge

# Business metrics
payment_total = Counter(
    'payment_total',
    'Total number of payments',
    ['status', 'currency', 'payment_method']
)

payment_amount = Counter(
    'payment_amount_total',
    'Total payment amount processed',
    ['currency']
)

# Performance metrics
payment_latency = Histogram(
    'payment_processing_latency_seconds',
    'Payment processing latency',
    buckets=[0.1, 0.5, 1.0, 2.0, 5.0]
)

# Fraud metrics
fraud_detected = Counter(
    'fraud_detections_total',
    'Fraudulent payments detected',
    ['risk_level']
)

# Error metrics
payment_errors = Counter(
    'payment_errors_total',
    'Payment processing errors',
    ['error_type', 'payment_processor']
)

# SLA metrics
authorization_success_rate = Gauge(
    'authorization_success_rate',
    'Percentage of successful authorizations'
)
```

**Alert Rules**:
```yaml
- alert: HighPaymentFailureRate
  expr: rate(payment_errors_total[5m]) > 0.05
  for: 5m
  annotations:
    summary: "Payment failure rate above 5%"

- alert: FraudSpikeDetected
  expr: rate(fraud_detected_total{risk_level="CRITICAL"}[5m]) > 10
  for: 2m
  annotations:
    summary: "Unusual spike in fraud detections"

- alert: DatabaseReplicationLag
  expr: postgres_replication_lag_seconds > 30
  for: 1m
  annotations:
    summary: "Database replication lag exceeds 30 seconds"

- alert: SettlementDiscrepancy
  expr: abs(settlement_discrepancy_amount) > 100
  annotations:
    summary: "Settlement discrepancy detected"
```

---

## Complete System Design Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                         PAYMENT GATEWAY                         │
│                        FULL ARCHITECTURE                        │
└────────────────────────────────────────────────────────────────┘

                        ┌──────────────┐
                        │   Merchant   │
                        │  Application │
                        └──────┬───────┘
                               │
                    ┌──────────▼──────────┐
                    │   API Gateway       │
                    │  - Rate Limiting    │
                    │  - Auth (API Keys)  │
                    │  - TLS Termination  │
                    └──────────┬──────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
    ┌──────────┐        ┌──────────┐        ┌──────────┐
    │ Payment  │        │  Fraud   │        │ Webhook  │
    │ Service  │───────▶│ Service  │        │ Service  │
    └────┬─────┘        └────┬─────┘        └────┬─────┘
         │                   │                    │
         │              ┌────▼────┐               │
         │              │   ML    │               │
         │              │  Model  │               │
         │              └─────────┘               │
         │                                        │
    ┌────▼──────────────────────────────┐        │
    │  Payment Processor Adapter        │        │
    │  - Circuit Breaker                │        │
    │  - Retry Logic                    │        │
    │  - Timeout Handling               │        │
    └────┬──────────────────────────────┘        │
         │                                        │
    ┌────┴────┬──────────┬──────────┐            │
    ▼         ▼          ▼          ▼            │
┌────────┐ ┌──────┐ ┌────────┐ ┌──────┐         │
│ Stripe │ │ Visa │ │PayPal  │ │Amex  │         │
└────────┘ └──────┘ └────────┘ └──────┘         │
                                                 │
    ┌────────────────────────────────────────────┤
    │                                            │
    ▼                                            ▼
┌────────────────────┐                  ┌────────────────┐
│  PostgreSQL        │                  │  Message Queue │
│  (Primary + Replica)│◀────────────────│  Apache Kafka  │
│  - ACID Guarantees │                  └────────┬───────┘
│  - Row-level Lock  │                           │
│  - Audit Logs      │                           │
└─────────┬──────────┘                           │
          │                                      │
     ┌────▼────┐                            ┌────▼────┐
     │  Redis  │                            │Analytics│
     │  Cache  │                            │Service  │
     └─────────┘                            └────┬────┘
                                                 │
                                            ┌────▼──────┐
                                            │ClickHouse │
                                            │ (OLAP DB) │
                                            └───────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Payment volume expectations? (TPS, daily volume)
- Supported payment methods? (cards, wallets, bank transfers)
- Compliance requirements? (PCI-DSS, SOC 2, regional)
- Multi-currency support needed?
- Fraud detection requirements?
- Settlement frequency? (daily, weekly)

**Topics to Cover**:
- ACID transactions (critical for financial systems)
- Idempotency (prevent double charges)
- State machine for payment lifecycle
- Fraud detection (rule-based + ML)
- PCI-DSS compliance (tokenization)
- Settlement and reconciliation
- High availability and disaster recovery

**Common Follow-ups**:
- "How do you handle payment processor failures?"
  → Circuit breaker, failover to backup processor, graceful degradation

- "How do you prevent double charging?"
  → Idempotency keys, optimistic locking, database constraints

- "How do you ensure ACID guarantees?"
  → PostgreSQL with SERIALIZABLE isolation, two-phase commit, audit logs

- "How do you detect fraud in real-time?"
  → Multi-layered approach: velocity checks, ML model, device fingerprinting

---

## Key Takeaways

1. **ACID is Non-Negotiable**: Financial systems MUST use ACID-compliant databases
2. **Idempotency is Critical**: Prevent duplicate payments with idempotency keys
3. **Never Store Card Data**: Use tokenization for PCI compliance
4. **State Machine**: Enforce valid payment state transitions
5. **Decimal, Not Float**: Use `Decimal` type for money calculations
6. **Audit Everything**: Maintain immutable audit logs for compliance
7. **Fraud Detection**: Multi-layered approach (rules + ML)
8. **Settlement Matters**: Daily reconciliation prevents financial discrepancies

## Further Reading

- PCI-DSS Compliance Guide
- Stripe Engineering Blog
- "Designing Data-Intensive Applications" by Martin Kleppmann
- Payment Card Industry Data Security Standard (PCI DSS)
- Two-Phase Commit Protocol
- Saga Pattern for Distributed Transactions
