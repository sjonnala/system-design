# Design an E-Commerce & Marketplace Platform

**Companies**: Amazon, Flipkart, eBay, Alibaba, Shopify
**Difficulty**: Advanced
**Time**: 60-90 minutes

## Problem Statement

Design a large-scale e-commerce and marketplace platform like Amazon or Flipkart that supports:
- **Multi-vendor marketplace** (sellers can list products)
- **Product catalog** with search and filtering
- **Shopping cart** and checkout
- **Inventory management** with real-time tracking
- **Order processing** and fulfillment
- **Payment processing** with multiple gateways
- **Recommendation engine**
- **Reviews and ratings**
- **Flash sales** and auctions (optional)

---

## Step 1: Requirements (10 min)

### Functional Requirements

**Core Features**:
1. **Product Catalog**
   - Browse products by category
   - Search with filters (price, brand, rating, etc.)
   - Product details page (images, specs, reviews)

2. **Shopping Cart**
   - Add/remove/update items
   - Save for later
   - Apply coupons/discounts

3. **Inventory Management**
   - Real-time stock tracking
   - Multi-warehouse support
   - Low stock alerts
   - Reserve inventory during checkout

4. **Order Management**
   - Place orders
   - Order tracking
   - Order history
   - Cancellations and returns

5. **Payment Processing**
   - Multiple payment methods (cards, wallets, COD)
   - Payment gateway integration
   - Refund processing

6. **Seller Management**
   - Seller registration and onboarding
   - Product listing management
   - Order fulfillment
   - Seller analytics

7. **Recommendations**
   - Personalized product recommendations
   - Similar products
   - Frequently bought together

8. **Reviews & Ratings**
   - Product reviews
   - Seller ratings
   - Verified purchase badges

**Advanced Features** (Optional):
- Flash sales with high concurrency
- Live auctions
- Real-time pricing updates
- Fraud detection
- Multi-language/multi-currency support

### Non-Functional Requirements

1. **Scalability**: Handle millions of users, billions of products
2. **High Availability**: 99.99% uptime (4 nines)
3. **Low Latency**:
   - Product search: <200ms
   - Cart operations: <100ms
   - Checkout: <500ms
4. **Consistency**:
   - Strong consistency for inventory and payments
   - Eventual consistency for reviews, recommendations
5. **Security**: PCI-DSS compliance for payments
6. **Performance**: Handle 100K+ concurrent users during flash sales

### Capacity Estimation

**Assumptions** (Amazon-scale):
- **Active Users**: 300M monthly, 50M daily
- **Products**: 500M SKUs
- **Sellers**: 2M active sellers
- **Orders**: 10M orders/day
- **Read:Write Ratio**: 100:1 (browsing vs purchasing)

**Traffic Estimation**:

```
Daily Active Users: 50M
Average sessions per user: 3
Average page views per session: 10

Total page views/day: 50M × 3 × 10 = 1.5B page views
Page views/second: 1.5B ÷ 86400 ≈ 17,000 QPS
Peak (3x): 50,000 QPS
```

**Write Operations** (Orders):
```
10M orders/day ÷ 86400 ≈ 115 orders/sec
Peak (during sales): 1,000+ orders/sec
```

**Storage Estimation**:

```
Products:
- 500M products × 10KB (metadata, images URLs) = 5TB

User Data:
- 300M users × 1KB (profile, preferences) = 300GB

Orders (5 years):
- 10M orders/day × 5KB × 365 × 5 = 90TB

Product Images:
- 500M products × 5 images × 200KB = 500TB

Reviews:
- 100M reviews × 2KB = 200GB

Total: ~600TB (excluding CDN-cached images)
```

**Bandwidth**:
```
Read: 50K QPS × 100KB (avg page with images) = 5GB/s = 40 Gbps
Write: 115 orders/sec × 5KB = 575KB/s (negligible)
```

**Cache Requirements**:
```
Hot products (20% → 80% traffic):
- 100M products × 10KB = 1TB cache
Popular searches: 10GB
User sessions: 50GB
Total: ~1TB Redis cluster
```

---

## Step 2: High-Level Architecture (20 min)

### System Components Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                        CLIENT LAYER                             │
│  Web Browser  │  Mobile App  │  Seller Dashboard  │  APIs       │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                    CDN (CloudFront/Akamai)                      │
│  Static Assets │ Product Images │ CSS/JS │ Cached Pages         │
└────────────────────────┬────────────────────────────────────────┘
                         │
┌────────────────────────▼────────────────────────────────────────┐
│                   API GATEWAY / LOAD BALANCER                   │
│  Rate Limiting │ Auth │ Routing │ SSL Termination               │
└────────────────────────┬────────────────────────────────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
┌────────▼────────┐ ┌───▼──────────┐ ┌─▼────────────────┐
│  MICROSERVICES  │ │ MICROSERVICES│ │  MICROSERVICES   │
│   CLUSTER 1     │ │  CLUSTER 2   │ │   CLUSTER 3      │
└─────────────────┘ └──────────────┘ └──────────────────┘

MICROSERVICES:
1. User Service          2. Product Catalog Service
3. Search Service        4. Cart Service
5. Inventory Service     6. Order Service
7. Payment Service       8. Seller Service
9. Notification Service  10. Recommendation Service
11. Review Service       12. Analytics Service

DATA LAYER:
┌──────────────┬──────────────┬──────────────┬──────────────┐
│ PostgreSQL   │ MongoDB      │ Elasticsearch│ Redis Cache  │
│ (Orders,     │ (Product     │ (Product     │ (Sessions,   │
│  Users,      │  Catalog)    │  Search)     │  Cart, Hot   │
│  Payments)   │              │              │  Products)   │
└──────────────┴──────────────┴──────────────┴──────────────┘

MESSAGE QUEUE & STREAMING:
┌──────────────┬──────────────┬──────────────┐
│ Apache Kafka │ RabbitMQ     │ AWS SQS      │
│ (Events)     │ (Tasks)      │ (Async Jobs) │
└──────────────┴──────────────┴──────────────┘

STORAGE:
┌──────────────┬──────────────┬──────────────┐
│ Amazon S3    │ ClickHouse   │ Hadoop/Spark │
│ (Images,     │ (Analytics,  │ (Big Data,   │
│  Documents)  │  Logs)       │  ML Models)  │
└──────────────┴──────────────┴──────────────┘
```

---

## Step 3: Core Microservices Deep Dive

### 1. Product Catalog Service

**Responsibilities**:
- Manage product information (titles, descriptions, specs)
- Product categorization and taxonomy
- Product variants (size, color, etc.)
- Pricing management

**Database**: MongoDB (flexible schema for diverse products)

**Schema**:
```javascript
{
  "_id": "prod_12345",
  "name": "iPhone 15 Pro",
  "brand": "Apple",
  "category": ["Electronics", "Mobile Phones", "Smartphones"],
  "seller_id": "seller_789",
  "price": {
    "currency": "USD",
    "amount": 999.99,
    "discount": {
      "type": "percentage",
      "value": 10,
      "valid_until": "2025-12-31"
    }
  },
  "variants": [
    {
      "sku": "iphone15pro-256gb-black",
      "attributes": {
        "storage": "256GB",
        "color": "Black"
      },
      "price": 999.99,
      "inventory_id": "inv_456"
    }
  ],
  "images": [
    "https://cdn.example.com/products/iphone15pro-1.jpg",
    "https://cdn.example.com/products/iphone15pro-2.jpg"
  ],
  "specifications": {
    "display": "6.1-inch Super Retina XDR",
    "processor": "A17 Pro chip",
    "camera": "48MP Main camera"
  },
  "rating": {
    "average": 4.5,
    "count": 12453
  },
  "created_at": "2025-09-15T10:00:00Z",
  "updated_at": "2025-11-14T15:30:00Z"
}
```

**APIs**:
```http
GET    /api/v1/products/{productId}
GET    /api/v1/products?category=electronics&brand=apple
POST   /api/v1/products (seller creates product)
PUT    /api/v1/products/{productId}
DELETE /api/v1/products/{productId}
```

---

### 2. Search Service

**Responsibilities**:
- Full-text product search
- Faceted filtering (price range, brand, rating, etc.)
- Auto-suggestions and spell correction
- Search ranking and relevance

**Technology**: Elasticsearch / Solr

**Index Structure**:
```json
{
  "mappings": {
    "properties": {
      "product_id": { "type": "keyword" },
      "name": {
        "type": "text",
        "analyzer": "standard",
        "fields": {
          "keyword": { "type": "keyword" }
        }
      },
      "description": { "type": "text" },
      "brand": { "type": "keyword" },
      "category": { "type": "keyword" },
      "price": { "type": "float" },
      "rating": { "type": "float" },
      "review_count": { "type": "integer" },
      "in_stock": { "type": "boolean" },
      "seller_rating": { "type": "float" },
      "tags": { "type": "keyword" }
    }
  }
}
```

**Search Query Example**:
```json
{
  "query": {
    "bool": {
      "must": [
        {
          "multi_match": {
            "query": "wireless headphones",
            "fields": ["name^3", "description", "brand^2"]
          }
        }
      ],
      "filter": [
        { "range": { "price": { "gte": 50, "lte": 200 } } },
        { "term": { "in_stock": true } },
        { "range": { "rating": { "gte": 4.0 } } }
      ]
    }
  },
  "sort": [
    { "_score": "desc" },
    { "rating": "desc" }
  ],
  "aggs": {
    "brands": {
      "terms": { "field": "brand" }
    },
    "price_ranges": {
      "range": {
        "field": "price",
        "ranges": [
          { "to": 50 },
          { "from": 50, "to": 100 },
          { "from": 100, "to": 200 },
          { "from": 200 }
        ]
      }
    }
  }
}
```

**Ranking Factors**:
1. Text relevance score
2. Product rating
3. Number of reviews
4. Seller rating
5. Availability (in stock vs out of stock)
6. Recency
7. Sales velocity
8. Click-through rate (CTR)

---

### 3. Inventory Service

**Responsibilities**:
- Track product availability across warehouses
- Reserve inventory during checkout
- Release reserved inventory (timeout or cancellation)
- Low stock alerts
- Inventory replenishment

**Database**: PostgreSQL (strong consistency required)

**Schema**:
```sql
CREATE TABLE inventory (
    id BIGSERIAL PRIMARY KEY,
    sku VARCHAR(100) UNIQUE NOT NULL,
    warehouse_id VARCHAR(50) NOT NULL,
    available_quantity INTEGER NOT NULL DEFAULT 0,
    reserved_quantity INTEGER NOT NULL DEFAULT 0,
    total_quantity INTEGER GENERATED ALWAYS AS (available_quantity + reserved_quantity) STORED,
    last_updated TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT positive_qty CHECK (available_quantity >= 0 AND reserved_quantity >= 0)
);

CREATE INDEX idx_inventory_sku ON inventory(sku);
CREATE INDEX idx_inventory_warehouse ON inventory(warehouse_id);

-- Reservations table (for cart checkout flow)
CREATE TABLE inventory_reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_id UUID UNIQUE NOT NULL,
    sku VARCHAR(100) NOT NULL,
    warehouse_id VARCHAR(50) NOT NULL,
    quantity INTEGER NOT NULL,
    user_id BIGINT NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    status VARCHAR(20) DEFAULT 'active', -- active, completed, cancelled, expired
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (sku, warehouse_id) REFERENCES inventory(sku, warehouse_id)
);

CREATE INDEX idx_reservations_user ON inventory_reservations(user_id);
CREATE INDEX idx_reservations_expires ON inventory_reservations(expires_at) WHERE status = 'active';
```

**Critical Operations**:

**1. Reserve Inventory** (during checkout):
```sql
-- Atomic operation using transaction
BEGIN;

-- Check availability
SELECT available_quantity
FROM inventory
WHERE sku = 'iphone15pro-256gb-black'
  AND warehouse_id = 'WH001'
  AND available_quantity >= 1
FOR UPDATE;  -- Lock the row

-- If available, reserve it
UPDATE inventory
SET available_quantity = available_quantity - 1,
    reserved_quantity = reserved_quantity + 1
WHERE sku = 'iphone15pro-256gb-black'
  AND warehouse_id = 'WH001';

-- Create reservation record
INSERT INTO inventory_reservations (reservation_id, sku, warehouse_id, quantity, user_id, expires_at)
VALUES (gen_random_uuid(), 'iphone15pro-256gb-black', 'WH001', 1, 12345, NOW() + INTERVAL '10 minutes');

COMMIT;
```

**2. Release Expired Reservations** (background job):
```sql
-- Run every minute
UPDATE inventory i
SET available_quantity = available_quantity + r.quantity,
    reserved_quantity = reserved_quantity - r.quantity
FROM inventory_reservations r
WHERE i.sku = r.sku
  AND i.warehouse_id = r.warehouse_id
  AND r.status = 'active'
  AND r.expires_at < NOW();

UPDATE inventory_reservations
SET status = 'expired'
WHERE status = 'active' AND expires_at < NOW();
```

**3. Deduct Inventory** (order confirmed):
```sql
BEGIN;

UPDATE inventory
SET reserved_quantity = reserved_quantity - 1
WHERE sku = ? AND warehouse_id = ?;

UPDATE inventory_reservations
SET status = 'completed'
WHERE reservation_id = ?;

COMMIT;
```

**Optimization**: Use **Redis** for caching inventory counts:
```python
def check_availability(sku, warehouse_id, quantity):
    cache_key = f"inventory:{sku}:{warehouse_id}"

    # Try cache first
    cached_qty = redis.get(cache_key)
    if cached_qty is not None and int(cached_qty) >= quantity:
        return True

    # Fall back to database
    db_qty = db.query("SELECT available_quantity FROM inventory WHERE sku = ? AND warehouse_id = ?", sku, warehouse_id)

    # Update cache (TTL: 5 seconds for fast-moving items)
    redis.setex(cache_key, 5, db_qty)

    return db_qty >= quantity
```

---

### 4. Cart Service

**Responsibilities**:
- Add/remove/update cart items
- Save for later
- Apply coupons and discounts
- Calculate total price
- Merge guest cart with user cart after login

**Storage**: Redis (fast, session-based)

**Data Structure**:
```json
// Redis Key: cart:{user_id}
{
  "user_id": "user_12345",
  "items": [
    {
      "product_id": "prod_789",
      "sku": "iphone15pro-256gb-black",
      "quantity": 1,
      "price": 999.99,
      "seller_id": "seller_456",
      "added_at": "2025-11-14T10:30:00Z"
    },
    {
      "product_id": "prod_321",
      "sku": "airpods-pro-2",
      "quantity": 2,
      "price": 249.99,
      "seller_id": "seller_456",
      "added_at": "2025-11-14T11:00:00Z"
    }
  ],
  "coupons": ["SAVE10", "FIRSTORDER"],
  "updated_at": "2025-11-14T11:00:00Z",
  "expires_at": "2025-11-21T11:00:00Z"  // 7 days
}
```

**APIs**:
```http
GET    /api/v1/cart
POST   /api/v1/cart/items
PUT    /api/v1/cart/items/{itemId}
DELETE /api/v1/cart/items/{itemId}
POST   /api/v1/cart/coupons
GET    /api/v1/cart/summary  // Calculate totals with discounts
```

**Cart Summary Calculation**:
```python
def calculate_cart_summary(user_id):
    cart = redis.get(f"cart:{user_id}")

    subtotal = sum(item['price'] * item['quantity'] for item in cart['items'])

    # Apply coupons
    discount = 0
    for coupon_code in cart['coupons']:
        coupon = get_coupon(coupon_code)
        if coupon['type'] == 'percentage':
            discount += subtotal * (coupon['value'] / 100)
        elif coupon['type'] == 'fixed':
            discount += coupon['value']

    # Calculate tax
    tax = (subtotal - discount) * 0.08  # 8% tax

    # Shipping
    shipping = calculate_shipping(cart['items'])

    total = subtotal - discount + tax + shipping

    return {
        'subtotal': subtotal,
        'discount': discount,
        'tax': tax,
        'shipping': shipping,
        'total': total,
        'items': cart['items']
    }
```

---

### 5. Order Service

**Responsibilities**:
- Create orders from cart
- Order lifecycle management
- Order tracking
- Cancellations and returns
- Integration with payment and inventory services

**Database**: PostgreSQL (ACID properties for financial data)

**Schema**:
```sql
CREATE TABLE orders (
    id BIGSERIAL PRIMARY KEY,
    order_number VARCHAR(50) UNIQUE NOT NULL,
    user_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,  -- pending, confirmed, shipped, delivered, cancelled, returned

    -- Pricing
    subtotal DECIMAL(10, 2) NOT NULL,
    discount DECIMAL(10, 2) DEFAULT 0,
    tax DECIMAL(10, 2) NOT NULL,
    shipping_fee DECIMAL(10, 2) NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,

    -- Address
    shipping_address_id BIGINT NOT NULL,
    billing_address_id BIGINT NOT NULL,

    -- Payment
    payment_method VARCHAR(50),
    payment_status VARCHAR(50),  -- pending, completed, failed, refunded

    -- Tracking
    estimated_delivery DATE,
    shipped_at TIMESTAMP,
    delivered_at TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (shipping_address_id) REFERENCES addresses(id),
    FOREIGN KEY (billing_address_id) REFERENCES addresses(id)
);

CREATE INDEX idx_orders_user ON orders(user_id);
CREATE INDEX idx_orders_status ON orders(status);
CREATE INDEX idx_orders_created ON orders(created_at);

CREATE TABLE order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id VARCHAR(100) NOT NULL,
    sku VARCHAR(100) NOT NULL,
    seller_id BIGINT NOT NULL,

    quantity INTEGER NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,

    status VARCHAR(50),  -- pending, shipped, delivered, returned
    tracking_number VARCHAR(100),

    FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_order_items_order ON order_items(order_id);
CREATE INDEX idx_order_items_seller ON order_items(seller_id);
```

**Order Creation Flow** (Distributed Transaction):

```python
def create_order(user_id, cart_summary):
    """
    Create order using Saga pattern for distributed transactions
    """
    order_id = None
    reservation_ids = []

    try:
        # Step 1: Create order record
        order = db.execute("""
            INSERT INTO orders (order_number, user_id, status, subtotal, discount, tax, shipping_fee, total_amount, ...)
            VALUES (?, ?, 'pending', ?, ?, ?, ?, ?, ...)
            RETURNING id
        """, generate_order_number(), user_id, cart_summary['subtotal'], ...)
        order_id = order['id']

        # Step 2: Reserve inventory for all items
        for item in cart_summary['items']:
            reservation_id = inventory_service.reserve_inventory(
                sku=item['sku'],
                quantity=item['quantity'],
                user_id=user_id,
                order_id=order_id
            )
            reservation_ids.append(reservation_id)

        # Step 3: Process payment
        payment_result = payment_service.process_payment(
            user_id=user_id,
            order_id=order_id,
            amount=cart_summary['total'],
            payment_method=cart_summary['payment_method']
        )

        if payment_result['status'] != 'success':
            raise PaymentFailedException(payment_result['error'])

        # Step 4: Update order status
        db.execute("UPDATE orders SET status = 'confirmed', payment_status = 'completed' WHERE id = ?", order_id)

        # Step 5: Deduct inventory (complete reservations)
        for reservation_id in reservation_ids:
            inventory_service.complete_reservation(reservation_id)

        # Step 6: Clear cart
        redis.delete(f"cart:{user_id}")

        # Step 7: Publish order created event
        kafka.publish('order.created', {
            'order_id': order_id,
            'user_id': user_id,
            'total': cart_summary['total'],
            'items': cart_summary['items']
        })

        return {'order_id': order_id, 'status': 'success'}

    except Exception as e:
        # ROLLBACK: Saga compensation
        logger.error(f"Order creation failed: {e}")

        # Cancel payment (if processed)
        if payment_result and payment_result['status'] == 'success':
            payment_service.refund_payment(payment_result['transaction_id'])

        # Release inventory reservations
        for reservation_id in reservation_ids:
            inventory_service.cancel_reservation(reservation_id)

        # Mark order as failed
        if order_id:
            db.execute("UPDATE orders SET status = 'failed' WHERE id = ?", order_id)

        return {'status': 'failed', 'error': str(e)}
```

---

### 6. Payment Service

**Responsibilities**:
- Process payments through multiple gateways (Stripe, PayPal, etc.)
- Handle different payment methods (cards, wallets, COD)
- PCI-DSS compliance
- Refund processing
- Payment retry logic

**Database**: PostgreSQL (financial transactions)

**Schema**:
```sql
CREATE TABLE payments (
    id BIGSERIAL PRIMARY KEY,
    payment_id VARCHAR(100) UNIQUE NOT NULL,
    order_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,

    amount DECIMAL(10, 2) NOT NULL,
    currency VARCHAR(3) DEFAULT 'USD',

    payment_method VARCHAR(50),  -- card, wallet, upi, cod
    payment_gateway VARCHAR(50),  -- stripe, paypal, razorpay

    status VARCHAR(50),  -- pending, processing, completed, failed, refunded

    -- Gateway details
    gateway_transaction_id VARCHAR(255),
    gateway_response TEXT,  -- JSON response from gateway

    -- Card details (tokenized, PCI-compliant)
    card_last4 VARCHAR(4),
    card_brand VARCHAR(20),

    -- Retry logic
    retry_count INTEGER DEFAULT 0,
    last_retry_at TIMESTAMP,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    FOREIGN KEY (order_id) REFERENCES orders(id)
);

CREATE INDEX idx_payments_order ON payments(order_id);
CREATE INDEX idx_payments_user ON payments(user_id);
CREATE INDEX idx_payments_status ON payments(status);
```

**Payment Processing**:
```python
def process_payment(order_id, user_id, amount, payment_method, card_token):
    """
    Process payment through appropriate gateway
    """
    # Create payment record
    payment = db.execute("""
        INSERT INTO payments (payment_id, order_id, user_id, amount, payment_method, status)
        VALUES (?, ?, ?, ?, ?, 'pending')
        RETURNING id
    """, generate_payment_id(), order_id, user_id, amount, payment_method)

    try:
        # Route to appropriate payment gateway
        if payment_method == 'card':
            result = stripe.charge(
                amount=amount * 100,  # Convert to cents
                currency='usd',
                source=card_token,
                description=f"Order {order_id}"
            )

            # Update payment record
            db.execute("""
                UPDATE payments
                SET status = 'completed',
                    gateway_transaction_id = ?,
                    gateway_response = ?,
                    card_last4 = ?,
                    card_brand = ?
                WHERE id = ?
            """, result.id, json.dumps(result), result.card.last4, result.card.brand, payment['id'])

            return {'status': 'success', 'transaction_id': result.id}

        elif payment_method == 'wallet':
            # Process wallet payment
            pass

        elif payment_method == 'cod':
            # Cash on delivery - mark as pending
            db.execute("UPDATE payments SET status = 'pending_cod' WHERE id = ?", payment['id'])
            return {'status': 'success', 'payment_method': 'cod'}

    except stripe.CardError as e:
        # Payment failed
        db.execute("""
            UPDATE payments
            SET status = 'failed',
                gateway_response = ?
            WHERE id = ?
        """, str(e), payment['id'])

        return {'status': 'failed', 'error': str(e)}
```

**Idempotency** (prevent duplicate charges):
```python
@idempotent(key_fn=lambda order_id, **kwargs: f"payment:{order_id}")
def process_payment(order_id, user_id, amount, payment_method, card_token):
    # Check if payment already processed for this order
    existing_payment = db.query("SELECT * FROM payments WHERE order_id = ? AND status = 'completed'", order_id)
    if existing_payment:
        return {'status': 'success', 'transaction_id': existing_payment['gateway_transaction_id']}

    # Process payment...
```

---

### 7. Recommendation Service

**Responsibilities**:
- Personalized product recommendations
- "Similar products"
- "Frequently bought together"
- Trending products
- Real-time recommendations based on browsing

**Technology**:
- Apache Spark (batch ML models)
- TensorFlow/PyTorch (deep learning models)
- Redis (real-time recommendations cache)

**Recommendation Strategies**:

**1. Collaborative Filtering**:
```python
# User-based: Find similar users, recommend their purchases
# Item-based: Find similar products, recommend those

from sklearn.metrics.pairwise import cosine_similarity

def item_based_recommendations(product_id, top_n=10):
    """
    Find similar products using item-item collaborative filtering
    """
    # Load item-item similarity matrix (pre-computed)
    similarity_matrix = load_similarity_matrix()

    # Get similar items
    similar_items = similarity_matrix[product_id].argsort()[-top_n:][::-1]

    return similar_items
```

**2. Content-Based Filtering**:
```python
def content_based_recommendations(user_id, top_n=10):
    """
    Recommend products based on user's browsing/purchase history
    """
    # Get user's interaction history
    user_history = get_user_history(user_id)

    # Extract features (category, brand, price range, attributes)
    user_profile = build_user_profile(user_history)

    # Find products matching user profile
    candidates = search_products_by_features(user_profile)

    # Rank by similarity score
    ranked = rank_by_similarity(user_profile, candidates)

    return ranked[:top_n]
```

**3. Hybrid Approach** (Amazon-style):
```python
def get_recommendations(user_id, context=None):
    """
    Hybrid recommendation system
    """
    recommendations = []

    # Collaborative filtering (40% weight)
    collab_recs = collaborative_filtering(user_id)
    recommendations.extend([(p, 0.4) for p in collab_recs])

    # Content-based (30% weight)
    content_recs = content_based_filtering(user_id)
    recommendations.extend([(p, 0.3) for p in content_recs])

    # Trending products (20% weight)
    trending = get_trending_products()
    recommendations.extend([(p, 0.2) for p in trending])

    # Contextual (current session, 10% weight)
    if context:
        contextual = contextual_recommendations(context)
        recommendations.extend([(p, 0.1) for p in contextual])

    # Aggregate scores and rank
    final_recs = aggregate_and_rank(recommendations)

    return final_recs
```

**ML Pipeline**:
```
1. Data Collection
   └─> User interactions (views, clicks, purchases)
   └─> Product features (category, brand, price, attributes)

2. Feature Engineering (Spark job - daily)
   └─> User-item matrix
   └─> Item-item similarity matrix
   └─> User embeddings
   └─> Product embeddings

3. Model Training (weekly)
   └─> Matrix Factorization (ALS)
   └─> Deep Learning (Neural Collaborative Filtering)
   └─> Gradient Boosting (ranking model)

4. Model Serving
   └─> Real-time inference API
   └─> Redis cache for hot recommendations
   └─> A/B testing framework

5. Feedback Loop
   └─> Track CTR, conversion rate
   └─> Retrain models with new data
```

---

### 8. Review & Rating Service

**Responsibilities**:
- Submit product reviews
- Rating aggregation
- Review moderation
- Verified purchase badges
- Helpful votes

**Database**: MongoDB (flexible schema, high write volume)

**Schema**:
```javascript
{
  "_id": "review_12345",
  "product_id": "prod_789",
  "user_id": "user_456",
  "order_id": "order_123",  // Verified purchase

  "rating": 5,  // 1-5 stars
  "title": "Amazing product!",
  "review_text": "This product exceeded my expectations...",

  "images": [
    "https://cdn.example.com/reviews/img1.jpg"
  ],

  "verified_purchase": true,
  "helpful_votes": 234,
  "total_votes": 250,

  "moderation_status": "approved",  // pending, approved, rejected
  "flagged_count": 0,

  "created_at": "2025-11-14T10:00:00Z",
  "updated_at": "2025-11-14T10:00:00Z"
}
```

**Rating Aggregation** (PostgreSQL materialized view):
```sql
CREATE MATERIALIZED VIEW product_ratings AS
SELECT
    product_id,
    COUNT(*) as review_count,
    AVG(rating) as average_rating,
    SUM(CASE WHEN rating = 5 THEN 1 ELSE 0 END) as five_star,
    SUM(CASE WHEN rating = 4 THEN 1 ELSE 0 END) as four_star,
    SUM(CASE WHEN rating = 3 THEN 1 ELSE 0 END) as three_star,
    SUM(CASE WHEN rating = 2 THEN 1 ELSE 0 END) as two_star,
    SUM(CASE WHEN rating = 1 THEN 1 ELSE 0 END) as one_star
FROM reviews
WHERE moderation_status = 'approved'
GROUP BY product_id;

-- Refresh daily or use triggers
REFRESH MATERIALIZED VIEW CONCURRENTLY product_ratings;
```

---

## Step 4: Advanced Features

### Flash Sales / Limited-Time Deals

**Challenge**: Handle 100K+ concurrent users trying to buy limited inventory

**Solutions**:

**1. Queue-Based System**:
```
User Request → Queue → Rate-Limited Worker Pool → Inventory Check → Order Processing
```

**Implementation**:
```python
# Use Redis for distributed queue
def handle_flash_sale_purchase(user_id, product_id):
    queue_position = redis.rpush(f"flash_sale_queue:{product_id}", user_id)

    # Return queue position to user
    return {
        'queue_position': queue_position,
        'estimated_wait': queue_position * 2  # 2 seconds per request
    }

# Background worker
def process_flash_sale_queue(product_id):
    while True:
        user_id = redis.lpop(f"flash_sale_queue:{product_id}")
        if not user_id:
            break

        # Check inventory
        if inventory_service.check_availability(product_id, quantity=1):
            # Reserve and create order
            order_id = create_order(user_id, product_id)

            # Notify user
            notification_service.send(user_id, f"Order {order_id} confirmed!")
        else:
            # Sold out
            notification_service.send(user_id, "Sorry, item sold out")
            break
```

**2. Inventory Pre-allocation**:
```python
# Before flash sale starts, pre-allocate inventory to regions
def pre_allocate_inventory(product_id, total_qty):
    regions = ['US-EAST', 'US-WEST', 'EU', 'ASIA']
    qty_per_region = total_qty // len(regions)

    for region in regions:
        redis.set(f"flash_sale:{product_id}:{region}", qty_per_region)

# During sale, deduct from regional quota
def purchase_flash_sale_item(user_id, product_id, region):
    qty = redis.decr(f"flash_sale:{product_id}:{region}")

    if qty >= 0:
        # Success! Create order
        return create_order(user_id, product_id)
    else:
        # Sold out in this region
        redis.incr(f"flash_sale:{product_id}:{region}")  # Restore
        return {"error": "Sold out"}
```

**3. CDN-based Rate Limiting**:
- Use CloudFlare or AWS Shield to absorb traffic spikes
- Implement bot detection
- CAPTCHA for suspicious traffic

---

### Fraud Detection

**Signals**:
- Multiple orders from same IP/device
- Shipping address != billing address
- High-value orders from new accounts
- Unusual purchasing patterns
- Stolen credit cards

**Implementation**:
```python
def calculate_fraud_score(order):
    score = 0

    # Check user account age
    if user_account_age(order['user_id']) < 7:  # days
        score += 20

    # Check order value
    if order['total'] > 1000:
        score += 15

    # Check shipping/billing mismatch
    if order['shipping_address'] != order['billing_address']:
        score += 10

    # Check velocity (orders per hour)
    recent_orders = count_orders_last_hour(order['user_id'])
    if recent_orders > 5:
        score += 25

    # Check device fingerprint
    if is_suspicious_device(order['device_id']):
        score += 30

    # Machine learning model
    ml_score = fraud_ml_model.predict(order)
    score += ml_score

    return score

def process_order_with_fraud_check(order):
    fraud_score = calculate_fraud_score(order)

    if fraud_score > 70:
        # High risk - block order
        return {"status": "blocked", "reason": "Fraud detected"}
    elif fraud_score > 40:
        # Medium risk - manual review
        send_to_manual_review(order)
        return {"status": "pending_review"}
    else:
        # Low risk - proceed
        return create_order(order)
```

---

## Step 5: Data Models Summary

### Database Choice Matrix

| **Service** | **Database** | **Reason** |
|-------------|--------------|------------|
| User Service | PostgreSQL | ACID transactions, user data integrity |
| Product Catalog | MongoDB | Flexible schema, diverse product types |
| Search | Elasticsearch | Full-text search, faceted filtering |
| Cart | Redis | Fast, session-based, TTL support |
| Inventory | PostgreSQL | Strong consistency, locking |
| Orders | PostgreSQL | ACID transactions, financial data |
| Payments | PostgreSQL | ACID transactions, compliance |
| Reviews | MongoDB | High write volume, flexible schema |
| Analytics | ClickHouse | Time-series data, fast aggregations |
| Recommendations | Redis + S3 | Real-time cache + ML model storage |

---

## Step 6: Scaling Strategies

### 1. Database Sharding

**Product Catalog** (MongoDB sharding):
```javascript
// Shard by product_id hash
sh.shardCollection("ecommerce.products", { "product_id": "hashed" })

// Result: Evenly distributed across shards
// 500M products → 10 shards = 50M products per shard
```

**Orders** (PostgreSQL partitioning):
```sql
-- Partition by date (monthly)
CREATE TABLE orders_2025_11 PARTITION OF orders
FOR VALUES FROM ('2025-11-01') TO ('2025-12-01');

CREATE TABLE orders_2025_12 PARTITION OF orders
FOR VALUES FROM ('2025-12-01') TO ('2026-01-01');

-- Shard by user_id for horizontal scaling
-- Shard 0: user_id % 10 = 0
-- Shard 1: user_id % 10 = 1
-- ...
```

### 2. Caching Strategy

**Multi-Tier Caching**:
```
Client (Browser Cache)
    ↓
CDN (CloudFront) - Static assets, product images
    ↓
Redis Cluster (Application Cache) - Hot products, sessions
    ↓
Database Read Replicas
    ↓
Database Primary
```

**Cache Invalidation**:
```python
# When product is updated
def update_product(product_id, updates):
    # Update database
    db.update("products", product_id, updates)

    # Invalidate cache
    redis.delete(f"product:{product_id}")

    # Invalidate search index
    elasticsearch.update(product_id, updates)

    # Publish cache invalidation event
    kafka.publish("cache.invalidate", {"product_id": product_id})
```

### 3. Read/Write Separation (CQRS)

```
WRITE PATH:
User → API Gateway → Write Service → Primary DB → Event Bus → Read Model Update

READ PATH:
User → API Gateway → Read Service → Read Replica / Cache → Response

BENEFITS:
- Optimize read and write independently
- Scale reads and writes separately
- Use different data models for read/write
```

### 4. Asynchronous Processing

**Message Queue Architecture**:
```
Kafka Topics:
- order.created
- payment.completed
- inventory.updated
- notification.email
- analytics.event

Consumers:
- Email Service (sends order confirmation)
- Analytics Service (tracks metrics)
- Recommendation Service (updates user profile)
- Inventory Service (updates stock)
```

---

## Step 7: Monitoring & Observability

### Key Metrics

**Business Metrics**:
- Orders per second
- Conversion rate (cart → order)
- Average order value
- Revenue per minute
- Inventory turnover rate

**Technical Metrics**:
- API latency (p50, p95, p99)
- Error rates (4xx, 5xx)
- Cache hit ratio
- Database query time
- Message queue lag

**Alerting**:
```yaml
alerts:
  - name: HighCheckoutLatency
    condition: p95_latency > 500ms
    duration: 5m
    severity: critical
    action: page_oncall

  - name: LowInventory
    condition: available_quantity < 10
    severity: warning
    action: notify_ops

  - name: PaymentFailureSpike
    condition: payment_failure_rate > 5%
    duration: 2m
    severity: critical
    action: page_oncall
```

### Distributed Tracing

```python
# Using OpenTelemetry
from opentelemetry import trace

tracer = trace.get_tracer(__name__)

@tracer.start_as_current_span("create_order")
def create_order(user_id, cart):
    with tracer.start_as_current_span("reserve_inventory"):
        inventory_service.reserve(cart.items)

    with tracer.start_as_current_span("process_payment"):
        payment_service.process(cart.total)

    with tracer.start_as_current_span("save_order"):
        db.insert_order(...)

    return order_id
```

---

## Step 8: DevOps & Infrastructure

### Technology Stack (Industry Standard)

**Frontend**:
- **Web**: React.js / Next.js / Vue.js
- **Mobile**: React Native / Flutter / Swift (iOS) / Kotlin (Android)
- **Admin Dashboard**: React + Material-UI / Ant Design

**Backend**:
- **API Gateway**: Kong / AWS API Gateway / NGINX
- **Microservices**: Java (Spring Boot) / Go / Node.js (Express)
- **Message Queue**: Apache Kafka / RabbitMQ / AWS SQS
- **Task Queue**: Celery (Python) / Bull (Node.js)

**Databases**:
- **Relational**: PostgreSQL / MySQL / AWS RDS
- **NoSQL**: MongoDB / DynamoDB / Cassandra
- **Cache**: Redis / Memcached
- **Search**: Elasticsearch / Solr / Algolia
- **Analytics**: ClickHouse / Apache Druid

**Storage**:
- **Object Storage**: Amazon S3 / Google Cloud Storage / MinIO
- **CDN**: CloudFront / Cloudflare / Akamai

**DevOps & Infrastructure**:
- **Container Orchestration**: Kubernetes (EKS / GKE / AKS)
- **CI/CD**: Jenkins / GitLab CI / GitHub Actions / CircleCI
- **IaC**: Terraform / AWS CloudFormation / Pulumi
- **Monitoring**: Prometheus + Grafana / Datadog / New Relic
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana) / Splunk
- **Tracing**: Jaeger / Zipkin / AWS X-Ray
- **Service Mesh**: Istio / Linkerd

**ML/AI**:
- **Frameworks**: TensorFlow / PyTorch / Scikit-learn
- **ML Ops**: MLflow / Kubeflow / SageMaker
- **Data Processing**: Apache Spark / Hadoop / Airflow

### Deployment Architecture

```yaml
# Kubernetes Deployment Example
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 10
  selector:
    matchLabels:
      app: product-service
  template:
    metadata:
      labels:
        app: product-service
    spec:
      containers:
      - name: product-service
        image: product-service:v1.2.3
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
        env:
        - name: DB_HOST
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: host
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /ready
            port: 8080
          initialDelaySeconds: 10
          periodSeconds: 5
```

### Multi-Region Deployment

```
REGION 1 (US-EAST):
├── Load Balancer (ALB)
├── Kubernetes Cluster (EKS)
│   ├── Product Service (10 pods)
│   ├── Order Service (15 pods)
│   ├── Payment Service (5 pods)
│   └── ...
├── PostgreSQL Primary + Replicas
├── Redis Cluster (3 masters, 3 replicas)
└── Elasticsearch Cluster (6 nodes)

REGION 2 (EU-WEST):
├── Load Balancer (ALB)
├── Kubernetes Cluster (EKS)
├── PostgreSQL Replicas (read-only)
├── Redis Cluster
└── Elasticsearch Cluster

GLOBAL:
├── CloudFront CDN (200+ edge locations)
├── Route 53 (DNS, geo-routing)
└── S3 (cross-region replication)
```

---

## Step 9: Security Considerations

**1. Authentication & Authorization**:
- JWT tokens with short expiry (15 min)
- Refresh tokens (7 days)
- OAuth 2.0 for third-party integrations
- Role-based access control (RBAC)

**2. Data Protection**:
- Encryption at rest (database, S3)
- Encryption in transit (TLS 1.3)
- PII data masking in logs
- GDPR compliance (data deletion, export)

**3. API Security**:
- Rate limiting (per user, per IP)
- Input validation
- SQL injection prevention
- XSS protection
- CSRF tokens

**4. Payment Security**:
- PCI-DSS Level 1 compliance
- Tokenization (never store raw card numbers)
- 3D Secure / CVV verification
- Fraud detection

**5. Infrastructure Security**:
- VPC isolation
- Security groups / Network ACLs
- WAF (Web Application Firewall)
- DDoS protection
- Secrets management (AWS Secrets Manager / HashiCorp Vault)

---

## Interview Tips

**Questions to Ask**:
1. Scale expectations? (users, products, orders per day)
2. Geographic distribution? (single region vs global)
3. Consistency requirements? (strong vs eventual)
4. Budget constraints?
5. Time to market? (MVP vs full-featured)
6. Marketplace vs single-seller?

**Topics to Cover**:
1. **Microservices architecture** (explain why over monolith)
2. **Database choices** (SQL vs NoSQL for different services)
3. **Inventory management** (strong consistency, reservations)
4. **Payment processing** (idempotency, security)
5. **Caching strategy** (multi-tier, invalidation)
6. **Scalability** (sharding, read replicas, async processing)
7. **Flash sales** (queue-based, rate limiting)
8. **Recommendations** (ML pipeline, hybrid approach)

**Common Follow-ups**:
- "How do you handle inventory conflicts?" → Optimistic locking, reservations
- "What if payment gateway is down?" → Circuit breaker, retry logic, fallback
- "How do you ensure data consistency?" → Distributed transactions (Saga pattern)
- "How do you prevent overselling?" → Strong consistency for inventory, locking

---

## Key Takeaways

1. **Microservices Architecture**: Necessary for independent scaling and deployment
2. **Strong Consistency for Money**: Inventory and payments require ACID transactions
3. **Eventual Consistency for Others**: Reviews, recommendations can be eventually consistent
4. **Caching is Critical**: Multi-tier caching reduces database load by 80-90%
5. **Async Processing**: Use message queues for non-critical operations
6. **Distributed Transactions**: Use Saga pattern for cross-service transactions
7. **Monitoring**: Comprehensive observability is non-negotiable at scale
8. **Security**: PCI-DSS compliance, encryption, fraud detection are must-haves

---

## Further Reading

- **Books**:
  - "Designing Data-Intensive Applications" by Martin Kleppmann
  - "Microservices Patterns" by Chris Richardson
  - "Building Microservices" by Sam Newman

- **Case Studies**:
  - Amazon Architecture
  - Shopify Engineering Blog
  - Flipkart Tech Blog
  - eBay Architecture

- **Technologies**:
  - Kubernetes Documentation
  - Apache Kafka Guide
  - PostgreSQL Performance Tuning
  - Elasticsearch Best Practices

---

*This design prioritizes scalability, reliability, and security while maintaining developer productivity through microservices architecture and modern DevOps practices.*
