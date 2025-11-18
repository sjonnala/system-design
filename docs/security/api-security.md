# API Security - mTLS, API Keys, Rate Limiting, and More

## Overview

API security encompasses multiple layers of protection for HTTP APIs, especially critical in microservices architectures where services communicate over the network.

**Key Security Concerns**:
- **Authentication**: Who is calling the API?
- **Authorization**: What can they access?
- **Confidentiality**: Encrypt data in transit
- **Integrity**: Prevent tampering
- **Availability**: Prevent abuse and DoS

## mTLS (Mutual TLS)

### Overview

mTLS extends TLS by requiring **both** client and server to present certificates for authentication.

**Standard TLS** (One-way):
```
Client ────────────► Server
       (verifies server cert only)
```

**mTLS** (Two-way):
```
Client ◄────────────► Server
       (both verify each other's certs)
```

### How mTLS Works

```
1. Client initiates TLS handshake
      │
      ▼
2. Server sends its certificate
      │
      ▼
3. Client verifies server certificate
      │
      ▼
4. Server requests client certificate
      │
      ▼
5. Client sends its certificate
      │
      ▼
6. Server verifies client certificate
      │
      ▼
7. Encrypted connection established
```

### Implementation

#### Generating Certificates

```bash
# Create CA (Certificate Authority)
openssl genrsa -out ca.key 4096
openssl req -new -x509 -days 3650 -key ca.key -out ca.crt

# Create Server Certificate
openssl genrsa -out server.key 2048
openssl req -new -key server.key -out server.csr
openssl x509 -req -days 365 -in server.csr -CA ca.crt -CAkey ca.key -set_serial 01 -out server.crt

# Create Client Certificate
openssl genrsa -out client.key 2048
openssl req -new -key client.key -out client.csr
openssl x509 -req -days 365 -in client.csr -CA ca.crt -CAkey ca.key -set_serial 02 -out client.crt
```

#### Node.js Server with mTLS

```javascript
const https = require('https');
const fs = require('fs');
const express = require('express');

const app = express();

const options = {
  // Server's certificate
  key: fs.readFileSync('server.key'),
  cert: fs.readFileSync('server.crt'),

  // CA certificate (to verify clients)
  ca: fs.readFileSync('ca.crt'),

  // Require client certificate
  requestCert: true,
  rejectUnauthorized: true
};

app.get('/api/data', (req, res) => {
  // Client certificate info
  const cert = req.socket.getPeerCertificate();

  console.log('Client CN:', cert.subject.CN);
  console.log('Client fingerprint:', cert.fingerprint);

  res.json({
    message: 'Secure data',
    authenticatedAs: cert.subject.CN
  });
});

https.createServer(options, app).listen(443);
```

#### Node.js Client with mTLS

```javascript
const https = require('https');
const fs = require('fs');

const options = {
  hostname: 'api.example.com',
  port: 443,
  path: '/api/data',
  method: 'GET',

  // Client's certificate
  key: fs.readFileSync('client.key'),
  cert: fs.readFileSync('client.crt'),

  // CA to verify server
  ca: fs.readFileSync('ca.crt')
};

const req = https.request(options, (res) => {
  res.on('data', (d) => {
    console.log(d.toString());
  });
});

req.on('error', (e) => {
  console.error(e);
});

req.end();
```

#### NGINX with mTLS

```nginx
server {
    listen 443 ssl;
    server_name api.example.com;

    # Server certificate
    ssl_certificate /etc/nginx/ssl/server.crt;
    ssl_certificate_key /etc/nginx/ssl/server.key;

    # Client certificate verification
    ssl_client_certificate /etc/nginx/ssl/ca.crt;
    ssl_verify_client on;
    ssl_verify_depth 2;

    location /api {
        # Pass client cert info to backend
        proxy_set_header X-Client-CN $ssl_client_s_dn;
        proxy_set_header X-Client-Fingerprint $ssl_client_fingerprint;

        proxy_pass http://backend;
    }
}
```

### mTLS in Service Mesh (Istio)

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: production
spec:
  mtls:
    mode: STRICT  # Require mTLS for all traffic

---
apiVersion: networking.istio.io/v1beta1
kind: DestinationRule
metadata:
  name: service-a
spec:
  host: service-a.production.svc.cluster.local
  trafficPolicy:
    tls:
      mode: ISTIO_MUTUAL  # Use Istio-managed mTLS
```

### When to Use mTLS

✅ **Use mTLS for**:
- Service-to-service communication in microservices
- Zero Trust security model
- Highly sensitive APIs
- Internal APIs within secure perimeter

❌ **Don't use mTLS for**:
- Public-facing APIs (hard to distribute client certs)
- Mobile/web clients (certificate management complexity)
- Third-party integrations (use OAuth 2.0 instead)

## API Keys

### Overview

API keys are simple, static tokens used to authenticate API requests.

### Implementation

#### Generating API Keys

```javascript
const crypto = require('crypto');

function generateApiKey() {
  // Format: prefix_randomstring
  const prefix = 'sk'; // Secret key
  const random = crypto.randomBytes(32).toString('base64url');
  return `${prefix}_${random}`;
}

// Create API key
app.post('/api-keys', requireAuth, async (req, res) => {
  const { name, scopes } = req.body;

  const apiKey = generateApiKey();

  // Hash before storing
  const hashedKey = crypto.createHash('sha256').update(apiKey).digest('hex');

  await db.apiKeys.create({
    userId: req.user.id,
    name,
    keyHash: hashedKey,
    scopes,
    createdAt: new Date()
  });

  // Return plaintext only once
  res.json({
    apiKey, // sk_xxxxxxxxxxx
    message: 'Save this key - you won\'t see it again'
  });
});
```

#### Validating API Keys

```javascript
async function validateApiKey(req, res, next) {
  const apiKey = req.headers['x-api-key'] || req.headers['authorization']?.replace('Bearer ', '');

  if (!apiKey) {
    return res.status(401).json({ error: 'API key required' });
  }

  // Hash the provided key
  const hashedKey = crypto.createHash('sha256').update(apiKey).digest('hex');

  // Look up in database
  const key = await db.apiKeys.findOne({
    keyHash: hashedKey,
    revokedAt: null
  });

  if (!key) {
    return res.status(401).json({ error: 'Invalid API key' });
  }

  // Update last used
  await db.apiKeys.update(key.id, { lastUsedAt: new Date() });

  req.apiKey = key;
  req.user = await db.users.findById(key.userId);

  next();
}

app.get('/api/data', validateApiKey, (req, res) => {
  res.json({ data: 'secret data' });
});
```

#### Scope-Based Authorization

```javascript
function requireScope(...requiredScopes) {
  return (req, res, next) => {
    const keyScopes = req.apiKey.scopes || [];

    const hasScope = requiredScopes.every(scope => keyScopes.includes(scope));

    if (!hasScope) {
      return res.status(403).json({
        error: 'Insufficient scope',
        required: requiredScopes,
        provided: keyScopes
      });
    }

    next();
  };
}

app.delete('/api/users/:id',
  validateApiKey,
  requireScope('users:delete'),
  deleteUser
);
```

### API Key Best Practices

1. **Use Prefixes**: `sk_live_...`, `pk_test_...`
2. **Hash Before Storage**: Never store plaintext
3. **Support Rotation**: Allow key replacement
4. **Limit Scope**: Principle of least privilege
5. **Monitor Usage**: Track last used, requests per key
6. **Set Expiration**: Auto-expire old keys
7. **Rate Limit Per Key**: Prevent abuse

```javascript
// Complete example
const apiKeySchema = {
  id: 'uuid',
  userId: 'uuid',
  name: 'string', // "Production Server Key"
  keyHash: 'string', // SHA-256 hash
  prefix: 'string', // "sk_live"
  scopes: ['string'], // ["users:read", "posts:write"]
  createdAt: 'datetime',
  lastUsedAt: 'datetime',
  expiresAt: 'datetime',
  revokedAt: 'datetime',
  rateLimit: 'number' // Requests per hour
};
```

## Rate Limiting

### Overview

Rate limiting controls the number of requests a client can make in a given time period.

### Algorithms

#### 1. Fixed Window

```javascript
const rateLimit = new Map(); // { userId: { count, resetAt } }

function fixedWindowRateLimit(userId, limit, windowMs) {
  const now = Date.now();
  const user = rateLimit.get(userId);

  if (!user || now > user.resetAt) {
    // New window
    rateLimit.set(userId, {
      count: 1,
      resetAt: now + windowMs
    });
    return true;
  }

  if (user.count < limit) {
    user.count++;
    return true;
  }

  return false; // Rate limit exceeded
}

app.use((req, res, next) => {
  const allowed = fixedWindowRateLimit(req.user.id, 100, 60000); // 100/min

  if (!allowed) {
    return res.status(429).json({ error: 'Rate limit exceeded' });
  }

  next();
});
```

**Pros**: Simple
**Cons**: Burst at window boundaries

#### 2. Sliding Window Log

```javascript
const requestLogs = new Map(); // { userId: [timestamps] }

function slidingWindowLog(userId, limit, windowMs) {
  const now = Date.now();
  const logs = requestLogs.get(userId) || [];

  // Remove old timestamps
  const validLogs = logs.filter(timestamp => now - timestamp < windowMs);

  if (validLogs.length < limit) {
    validLogs.push(now);
    requestLogs.set(userId, validLogs);
    return true;
  }

  return false;
}
```

**Pros**: Accurate, no bursts
**Cons**: Memory intensive

#### 3. Token Bucket

```javascript
class TokenBucket {
  constructor(capacity, refillRate) {
    this.capacity = capacity;
    this.tokens = capacity;
    this.refillRate = refillRate; // tokens per second
    this.lastRefill = Date.now();
  }

  tryConsume(tokens = 1) {
    this.refill();

    if (this.tokens >= tokens) {
      this.tokens -= tokens;
      return true;
    }

    return false;
  }

  refill() {
    const now = Date.now();
    const timePassed = (now - this.lastRefill) / 1000;
    const tokensToAdd = timePassed * this.refillRate;

    this.tokens = Math.min(this.capacity, this.tokens + tokensToAdd);
    this.lastRefill = now;
  }
}

const buckets = new Map();

app.use((req, res, next) => {
  const userId = req.user.id;

  if (!buckets.has(userId)) {
    buckets.set(userId, new TokenBucket(100, 10)); // 100 capacity, 10/sec refill
  }

  const bucket = buckets.get(userId);

  if (bucket.tryConsume()) {
    next();
  } else {
    res.status(429).json({ error: 'Rate limit exceeded' });
  }
});
```

**Pros**: Allows bursts, smooth refill
**Cons**: More complex

#### 4. Leaky Bucket

```javascript
class LeakyBucket {
  constructor(capacity, leakRate) {
    this.capacity = capacity;
    this.queue = [];
    this.leakRate = leakRate; // requests per second

    setInterval(() => this.leak(), 1000 / leakRate);
  }

  tryAdd(request) {
    if (this.queue.length < this.capacity) {
      this.queue.push(request);
      return true;
    }
    return false;
  }

  leak() {
    if (this.queue.length > 0) {
      const request = this.queue.shift();
      request.next();
    }
  }
}

const bucket = new LeakyBucket(100, 10);

app.use((req, res, next) => {
  if (bucket.tryAdd({ next })) {
    // Request queued, will be processed at leak rate
  } else {
    res.status(429).json({ error: 'Rate limit exceeded' });
  }
});
```

**Pros**: Smooth output rate
**Cons**: Adds latency

### Redis-Based Rate Limiting

```javascript
const redis = require('redis');
const client = redis.createClient();

async function rateLimitRedis(userId, limit, windowSeconds) {
  const key = `rate_limit:${userId}`;

  // Increment counter
  const current = await client.incr(key);

  // Set expiration on first request
  if (current === 1) {
    await client.expire(key, windowSeconds);
  }

  return current <= limit;
}

app.use(async (req, res, next) => {
  const allowed = await rateLimitRedis(req.user.id, 100, 60);

  if (!allowed) {
    return res.status(429).json({ error: 'Rate limit exceeded' });
  }

  // Add rate limit headers
  res.setHeader('X-RateLimit-Limit', '100');
  res.setHeader('X-RateLimit-Remaining', '...');
  res.setHeader('X-RateLimit-Reset', '...');

  next();
});
```

### Express Rate Limit Library

```javascript
const rateLimit = require('express-rate-limit');

// Global rate limit
const globalLimiter = rateLimit({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 100, // 100 requests per window
  message: 'Too many requests',
  standardHeaders: true, // Return rate limit info in headers
  legacyHeaders: false
});

app.use('/api/', globalLimiter);

// Endpoint-specific rate limit
const loginLimiter = rateLimit({
  windowMs: 15 * 60 * 1000,
  max: 5, // 5 login attempts per 15 minutes
  skipSuccessfulRequests: true // Don't count successful logins
});

app.post('/login', loginLimiter, handleLogin);

// Per-user rate limit
const createAccountLimiter = rateLimit({
  windowMs: 60 * 60 * 1000,
  max: 3,
  keyGenerator: (req) => req.ip // Limit by IP
});

app.post('/signup', createAccountLimiter, createAccount);
```

## API Gateway Security

### Kong API Gateway

```yaml
# Rate limiting plugin
plugins:
  - name: rate-limiting
    config:
      minute: 100
      policy: local
      fault_tolerant: true

# API key authentication
plugins:
  - name: key-auth
    config:
      key_names:
        - apikey
      hide_credentials: true

# IP restriction
plugins:
  - name: ip-restriction
    config:
      allow:
        - 10.0.0.0/8
        - 192.168.0.0/16
```

### AWS API Gateway

```yaml
# API Gateway with usage plan
Resources:
  MyApiUsagePlan:
    Type: AWS::ApiGateway::UsagePlan
    Properties:
      UsagePlanName: BasicPlan
      Throttle:
        BurstLimit: 200
        RateLimit: 100
      Quota:
        Limit: 10000
        Period: MONTH

  MyApiKey:
    Type: AWS::ApiGateway::ApiKey
    Properties:
      Enabled: true

  MyApiUsagePlanKey:
    Type: AWS::ApiGateway::UsagePlanKey
    Properties:
      KeyId: !Ref MyApiKey
      KeyType: API_KEY
      UsagePlanId: !Ref MyApiUsagePlan
```

## Additional API Security Measures

### 1. Input Validation

```javascript
const { body, validationResult } = require('express-validator');

app.post('/api/users',
  [
    body('email').isEmail().normalizeEmail(),
    body('age').isInt({ min: 18, max: 120 }),
    body('name').trim().isLength({ min: 1, max: 100 }),
  ],
  (req, res) => {
    const errors = validationResult(req);
    if (!errors.isEmpty()) {
      return res.status(400).json({ errors: errors.array() });
    }

    // Process request
  }
);
```

### 2. CORS Configuration

```javascript
const cors = require('cors');

app.use(cors({
  origin: ['https://app.example.com', 'https://admin.example.com'],
  methods: ['GET', 'POST', 'PUT', 'DELETE'],
  allowedHeaders: ['Content-Type', 'Authorization'],
  credentials: true,
  maxAge: 86400 // 24 hours
}));
```

### 3. Request Signing (AWS Signature V4)

```javascript
const aws4 = require('aws4');

// Sign request
const request = {
  host: 'api.example.com',
  path: '/api/data',
  method: 'GET'
};

aws4.sign(request, {
  accessKeyId: 'AKIAIOSFODNN7EXAMPLE',
  secretAccessKey: 'wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY'
});

// Send request with signed headers
fetch('https://api.example.com/api/data', {
  headers: request.headers
});

// Verify signature on server
function verifySignature(req) {
  const expectedSignature = calculateSignature(req);
  const providedSignature = req.headers['authorization'];

  return expectedSignature === providedSignature;
}
```

### 4. Content Security Policy for APIs

```javascript
app.use((req, res, next) => {
  res.setHeader('X-Content-Type-Options', 'nosniff');
  res.setHeader('X-Frame-Options', 'DENY');
  res.setHeader('X-XSS-Protection', '1; mode=block');
  res.setHeader('Strict-Transport-Security', 'max-age=31536000; includeSubDomains');

  next();
});
```

### 5. API Versioning

```javascript
// URL versioning
app.use('/api/v1', v1Routes);
app.use('/api/v2', v2Routes);

// Header versioning
app.use((req, res, next) => {
  const version = req.headers['api-version'] || 'v1';

  if (version === 'v1') {
    v1Handler(req, res);
  } else if (version === 'v2') {
    v2Handler(req, res);
  } else {
    res.status(400).json({ error: 'Unsupported API version' });
  }
});
```

## Interview Questions

### Basic
1. What is mTLS and how does it differ from TLS?
2. What are API keys and when should you use them?
3. Explain the concept of rate limiting
4. What is the difference between authentication and authorization in APIs?

### Intermediate
5. Compare different rate limiting algorithms (fixed window, token bucket, leaky bucket)
6. How would you implement API key rotation?
7. Explain CORS and why it's important for API security
8. What are the security considerations for public vs internal APIs?

### Advanced
9. Design a complete API security strategy for a microservices platform
10. How would you implement mTLS in a service mesh?
11. Compare API keys vs OAuth 2.0 for different use cases
12. How do you prevent API abuse at scale?

## Further Reading

- [OWASP API Security Top 10](https://owasp.org/www-project-api-security/)
- [mTLS Best Practices](https://www.cloudflare.com/learning/access-management/what-is-mutual-tls/)
- [API Gateway Patterns](https://microservices.io/patterns/apigateway.html)
- [Rate Limiting Strategies](https://cloud.google.com/architecture/rate-limiting-strategies-techniques)
