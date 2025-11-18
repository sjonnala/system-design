# JWT, JWS, and JWE - JSON Web Tokens

## Overview

**JWT (JSON Web Token)** is a compact, URL-safe means of representing claims to be transferred between two parties. JWTs are the backbone of modern stateless authentication and are used extensively in OAuth 2.0, OIDC, and microservices.

**Key Standards**:
- **JWT (RFC 7519)**: The token format
- **JWS (RFC 7515)**: JSON Web Signature - for signed tokens
- **JWE (RFC 7516)**: JSON Web Encryption - for encrypted tokens
- **JWK (RFC 7517)**: JSON Web Key - for cryptographic keys
- **JWA (RFC 7518)**: JSON Web Algorithms - supported algorithms

## JWT Structure

A JWT has three parts separated by dots:

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

└──────────────────┬──────────────────┘ └─────────────────────────────┬─────────────────────────────┘ └─────────────┬─────────────┘
              Header                                              Payload                                        Signature
```

### 1. Header

Describes the token type and signing algorithm:

```json
{
  "alg": "RS256",
  "typ": "JWT",
  "kid": "key-2024-01"
}
```

**Common Header Claims**:
- `alg` - Algorithm (HS256, RS256, ES256, etc.)
- `typ` - Type (usually "JWT")
- `kid` - Key ID (identifies which key was used)
- `cty` - Content type (for nested JWTs)

### 2. Payload

Contains the claims (statements about an entity):

```json
{
  "sub": "user-12345",
  "name": "John Doe",
  "email": "john@example.com",
  "iat": 1516239022,
  "exp": 1516242622,
  "iss": "https://auth.example.com",
  "aud": "my-app"
}
```

**Standard Claims (Registered)**:
- `iss` - Issuer (who created the token)
- `sub` - Subject (who the token is about)
- `aud` - Audience (who the token is for)
- `exp` - Expiration time (Unix timestamp)
- `nbf` - Not before (Unix timestamp)
- `iat` - Issued at (Unix timestamp)
- `jti` - JWT ID (unique identifier)

**Public Claims**: Defined by JWT users, should be collision-resistant
**Private Claims**: Custom claims agreed upon between parties

### 3. Signature

Ensures the token hasn't been tampered with:

```javascript
// Signature creation
const signature = HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  secret
);
```

For RSA (RS256):
```javascript
const signature = RSA_SHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  privateKey
);
```

## JWS (JSON Web Signature)

JWS is a signed JWT that ensures **integrity** and **authenticity**.

### Signing Algorithms

#### Symmetric Algorithms (HMAC)

**HS256 (HMAC with SHA-256)**:
- Same secret key for signing and verification
- Fast
- **Security Risk**: If client has the key, they can forge tokens

```javascript
// Signing
const jwt = require('jsonwebtoken');
const token = jwt.sign(
  { sub: 'user-123', name: 'John' },
  'shared-secret',
  { algorithm: 'HS256', expiresIn: '1h' }
);

// Verifying
const decoded = jwt.verify(token, 'shared-secret');
```

**When to use**:
- Service-to-service communication (both know secret)
- Single backend application
- NOT for client-facing tokens

#### Asymmetric Algorithms (RSA, ECDSA)

**RS256 (RSA with SHA-256)**:
- Private key for signing
- Public key for verification
- Slower than HMAC
- **Secure**: Public key can be distributed safely

```javascript
const fs = require('fs');

// Signing (authorization server)
const privateKey = fs.readFileSync('private.pem');
const token = jwt.sign(
  { sub: 'user-123', name: 'John' },
  privateKey,
  { algorithm: 'RS256', expiresIn: '1h' }
);

// Verifying (any service with public key)
const publicKey = fs.readFileSync('public.pem');
const decoded = jwt.verify(token, publicKey, { algorithms: ['RS256'] });
```

**When to use**:
- Client-facing applications (SPAs, mobile apps)
- Distributed systems (multiple services)
- OIDC ID tokens

**ES256 (ECDSA with SHA-256)**:
- Elliptic Curve Digital Signature Algorithm
- Smaller keys and signatures than RSA
- Faster than RSA
- Same security level with smaller keys (256-bit ECDSA ≈ 3072-bit RSA)

```javascript
// Generate ES256 key pair
const { generateKeyPairSync } = require('crypto');
const { privateKey, publicKey } = generateKeyPairSync('ec', {
  namedCurve: 'prime256v1'
});

// Signing
const token = jwt.sign(payload, privateKey, { algorithm: 'ES256' });

// Verifying
const decoded = jwt.verify(token, publicKey, { algorithms: ['ES256'] });
```

### Algorithm Comparison

| Algorithm | Type | Key Size | Performance | Use Case |
|-----------|------|----------|-------------|----------|
| **HS256** | Symmetric HMAC | 256 bits | Fastest | Internal services |
| **HS384** | Symmetric HMAC | 384 bits | Fast | Higher security internal |
| **HS512** | Symmetric HMAC | 512 bits | Fast | Highest security internal |
| **RS256** | Asymmetric RSA | 2048 bits | Slow | Public clients, OIDC |
| **RS384** | Asymmetric RSA | 2048 bits | Slow | Higher security public |
| **RS512** | Asymmetric RSA | 2048 bits | Slowest | Highest security public |
| **ES256** | Asymmetric ECDSA | 256 bits | Medium | Modern, efficient |
| **ES384** | Asymmetric ECDSA | 384 bits | Medium | Higher security |
| **ES512** | Asymmetric ECDSA | 521 bits | Medium | Highest security |

**Recommendation**: Use **ES256** for new systems (smaller, faster than RSA)

## JWE (JSON Web Encryption)

JWE provides **confidentiality** - encrypts the payload so only the recipient can read it.

### When to Use JWE

✅ **Use JWE when**:
- Token contains sensitive data (PII, health info, financial data)
- Compliance requires encryption (GDPR, HIPAA)
- Token passes through untrusted intermediaries

❌ **Don't use JWE when**:
- Payload is not sensitive
- JWS (signed) is sufficient
- Performance is critical (JWE is slower)

### JWE Structure

```
eyJhbGciOiJSU0EtT0FFUCIsImVuYyI6IkEyNTZHQ00ifQ.OKOawDo13gRp2ojaHV7LFpZcgV7T6DVZKTyKOMTYUmKoTCVJRgckCL9kiMT03JGeipsEdY3mx_etLbbWSrFr05kLzcSr4qKAq7YN7e9jwQRb23nfa6c9d-StnImGyFDbSv04uVuxIp5Zms1gNxKKK2Da14B8S4rzVRltdYwam_lDp5XnZAYpQdb76FdIKLaVmqgfwX7XWRxv2322i-vDxRfqNzo_tETKzpVLzfiwQyeyPGLBIO56YJ7eObdv0je81860ppamavo35UgoRdbYaBcoh9QcfylQr66oc6vFWXRcZ_ZT2LawVCWTIy3brGPi6UklfCpIMfIjf7iGdXKHzg.48V1_ALb6US04U3b.5eym8TW_c8SuK0ltJ3rpYIzOeDQz7TALvtu6UG9oMo4vpzs9tX_EFShS8iB7j6jiSdiwkIr3ajwQzaBtQD_A.XFBoMYUZodetZdvTiFvSkQ

└────────────┬────────────┘ └──────────────────────────────────┬──────────────────────────────────┘ └──────┬──────┘ └────────┬────────┘ └────────┬────────┘
          Header                            Encrypted Key                                           IV        Ciphertext     Auth Tag
```

**Five Parts**:
1. **Header**: Encryption algorithm metadata
2. **Encrypted Key**: Content encryption key (CEK) encrypted with recipient's public key
3. **Initialization Vector**: Random value for encryption
4. **Ciphertext**: Encrypted payload
5. **Authentication Tag**: Ensures integrity

### JWE Example

```javascript
const jose = require('node-jose');

// Recipient's key pair
const keystore = jose.JWK.createKeyStore();
const key = await keystore.generate('RSA', 2048);

// Create JWE
const payload = JSON.stringify({
  sub: 'user-123',
  ssn: '123-45-6789', // Sensitive data
  credit_card: '4111-1111-1111-1111'
});

const encrypted = await jose.JWE.createEncrypt(
  { format: 'compact', contentAlg: 'A256GCM', fields: { alg: 'RSA-OAEP' } },
  key
)
.update(payload)
.final();

console.log(encrypted); // Encrypted JWT

// Decrypt JWE
const decrypted = await jose.JWE.createDecrypt(keystore).decrypt(encrypted);
const claims = JSON.parse(decrypted.plaintext.toString());
console.log(claims); // Original payload
```

### JWE Algorithms

**Key Encryption Algorithms** (alg):
- `RSA-OAEP` - RSA with OAEP padding (recommended)
- `RSA-OAEP-256` - RSA with OAEP and SHA-256
- `A256KW` - AES Key Wrap with 256-bit key
- `dir` - Direct use of shared symmetric key
- `ECDH-ES` - Elliptic Curve Diffie-Hellman Ephemeral Static
- `ECDH-ES+A256KW` - ECDH-ES with AES Key Wrap

**Content Encryption Algorithms** (enc):
- `A256GCM` - AES-GCM with 256-bit key (recommended)
- `A192GCM` - AES-GCM with 192-bit key
- `A128GCM` - AES-GCM with 128-bit key
- `A256CBC-HS512` - AES-CBC with HMAC-SHA512

## JWT Best Practices

### 1. Choose the Right Algorithm

```javascript
// ❌ DON'T: Use 'none' algorithm
{ "alg": "none" } // No signature!

// ❌ DON'T: Use HS256 for client-facing tokens
const token = jwt.sign(payload, 'secret', { algorithm: 'HS256' });
// Client can see secret and forge tokens

// ✅ DO: Use RS256 or ES256 for client-facing
const token = jwt.sign(payload, privateKey, { algorithm: 'ES256' });
```

### 2. Always Verify Algorithm

```javascript
// ❌ DON'T: Allow any algorithm
jwt.verify(token, key);

// ✅ DO: Specify allowed algorithms
jwt.verify(token, publicKey, { algorithms: ['RS256'] });
```

**Critical**: Prevents algorithm confusion attacks (e.g., RS256 → HS256)

### 3. Set Short Expiration Times

```javascript
// ❌ DON'T: Long-lived tokens
{ expiresIn: '30d' } // 30 days is too long!

// ✅ DO: Short-lived with refresh mechanism
{ expiresIn: '15m' } // 15 minutes for access token
{ expiresIn: '7d' }  // 7 days for refresh token
```

### 4. Validate All Claims

```javascript
function validateToken(token) {
  const decoded = jwt.verify(token, publicKey, {
    algorithms: ['RS256'],
    issuer: 'https://auth.example.com', // Verify issuer
    audience: 'my-app',                  // Verify audience
    clockTolerance: 30                   // 30 second clock skew tolerance
  });

  // Additional custom validation
  if (!decoded.sub) {
    throw new Error('Missing subject');
  }

  return decoded;
}
```

### 5. Don't Store Sensitive Data

```javascript
// ❌ DON'T: Store sensitive data in JWT
{
  sub: 'user-123',
  password: 'secret123',      // NO!
  ssn: '123-45-6789',        // NO!
  credit_card: '4111...'     // NO!
}

// ✅ DO: Store only necessary claims
{
  sub: 'user-123',
  email: 'john@example.com',
  roles: ['user', 'admin']
}

// ⚠️ OR: Use JWE if you must include sensitive data
```

### 6. Implement Token Revocation

JWT is stateless, so revocation is challenging:

**Option 1: Short TTL + Deny List**
```javascript
const revokedTokens = new Set();

app.post('/logout', (req, res) => {
  const token = req.headers.authorization.split(' ')[1];
  const decoded = jwt.decode(token);

  // Add to deny list until expiration
  revokedTokens.add(decoded.jti);

  // Clean up after expiration
  setTimeout(() => {
    revokedTokens.delete(decoded.jti);
  }, (decoded.exp * 1000) - Date.now());

  res.json({ message: 'Logged out' });
});

// Check deny list on each request
function validateToken(req, res, next) {
  const token = req.headers.authorization.split(' ')[1];
  const decoded = jwt.verify(token, publicKey);

  if (revokedTokens.has(decoded.jti)) {
    return res.status(401).json({ error: 'Token revoked' });
  }

  req.user = decoded;
  next();
}
```

**Option 2: Redis-Based Revocation**
```javascript
const redis = require('redis');
const client = redis.createClient();

app.post('/logout', async (req, res) => {
  const token = req.headers.authorization.split(' ')[1];
  const decoded = jwt.decode(token);

  // Store revoked token in Redis with expiration
  const ttl = decoded.exp - Math.floor(Date.now() / 1000);
  await client.setex(`revoked:${decoded.jti}`, ttl, 'true');

  res.json({ message: 'Logged out' });
});

async function validateToken(req, res, next) {
  const token = req.headers.authorization.split(' ')[1];
  const decoded = jwt.verify(token, publicKey);

  // Check if revoked
  const revoked = await client.get(`revoked:${decoded.jti}`);
  if (revoked) {
    return res.status(401).json({ error: 'Token revoked' });
  }

  req.user = decoded;
  next();
}
```

**Option 3: Refresh Token Rotation**
```javascript
// Short-lived access token (15 min)
const accessToken = jwt.sign(payload, privateKey, {
  algorithm: 'RS256',
  expiresIn: '15m'
});

// Long-lived refresh token (stored in database)
const refreshToken = crypto.randomBytes(32).toString('hex');
await db.refreshTokens.create({
  token: refreshToken,
  userId: user.id,
  expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000) // 7 days
});

// On logout, delete refresh token from database
await db.refreshTokens.delete({ token: refreshToken });
```

### 7. Use Appropriate Key Sizes

```
❌ RSA 1024 bits - Too weak
✅ RSA 2048 bits - Minimum
✅ RSA 4096 bits - High security
✅ ECDSA P-256 - Recommended (equivalent to RSA 3072)
✅ ECDSA P-384 - High security
```

### 8. Rotate Keys Regularly

```javascript
// JWK Set with multiple keys
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2024-01",  // Currently active
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    },
    {
      "kty": "RSA",
      "kid": "key-2023-12",  // Still valid for verification
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    }
  ]
}

// Sign with latest key
const token = jwt.sign(payload, newPrivateKey, {
  algorithm: 'RS256',
  keyid: 'key-2024-01'
});

// Verify with key specified in token header
function getKey(header, callback) {
  const key = findKeyById(header.kid);
  callback(null, key);
}
jwt.verify(token, getKey);
```

## JWK (JSON Web Key)

JWK represents cryptographic keys in JSON format.

### RSA Public Key Example

```json
{
  "kty": "RSA",
  "use": "sig",
  "kid": "key-2024-01",
  "alg": "RS256",
  "n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
  "e": "AQAB"
}
```

**Fields**:
- `kty` - Key type (RSA, EC, oct)
- `use` - Usage (sig=signature, enc=encryption)
- `kid` - Key ID
- `alg` - Algorithm
- `n` - RSA modulus
- `e` - RSA exponent

### ECDSA Public Key Example

```json
{
  "kty": "EC",
  "use": "sig",
  "kid": "key-ec-2024-01",
  "alg": "ES256",
  "crv": "P-256",
  "x": "WKn-ZIGevcwGIyyrzFoZNBdaq9_TsqzGl96oc0CWuis",
  "y": "y77t-RvAHRKTsSGdIYUfweuOvwrvDD-Q3Hv5J0fSKbE"
}
```

### Symmetric Key Example

```json
{
  "kty": "oct",
  "kid": "key-hmac-2024-01",
  "alg": "HS256",
  "k": "GawgguFyGrWKav7AX4VKUg"
}
```

### JWKS (JWK Set) Endpoint

```http
GET /.well-known/jwks.json
```

Response:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "kid": "key-2024-01",
      "use": "sig",
      "alg": "RS256",
      "n": "...",
      "e": "AQAB"
    },
    {
      "kty": "EC",
      "kid": "key-ec-2024-01",
      "use": "sig",
      "alg": "ES256",
      "crv": "P-256",
      "x": "...",
      "y": "..."
    }
  ]
}
```

**Usage**:
```javascript
const jwksClient = require('jwks-rsa');

const client = jwksClient({
  jwksUri: 'https://auth.example.com/.well-known/jwks.json',
  cache: true,
  rateLimit: true
});

function getKey(header, callback) {
  client.getSigningKey(header.kid, (err, key) => {
    const signingKey = key.publicKey || key.rsaPublicKey;
    callback(null, signingKey);
  });
}

jwt.verify(token, getKey, (err, decoded) => {
  console.log(decoded);
});
```

## Common Attacks and Mitigations

### 1. Algorithm Confusion Attack

**Attack**: Change `alg` from RS256 to HS256, sign with public key

```json
// Attacker changes header
{
  "alg": "HS256",  // Changed from RS256
  "typ": "JWT"
}
```

Then signs with the public key (which server uses to verify RS256):
```javascript
// Server has public key for RS256
const publicKey = getPublicKey();

// Attacker signs with HS256 using the public key as secret
const forgedToken = jwt.sign(payload, publicKey, { algorithm: 'HS256' });

// Server might accidentally verify with public key as secret!
```

**Mitigation**:
```javascript
// ✅ Always specify allowed algorithms
jwt.verify(token, publicKey, { algorithms: ['RS256'] });
```

### 2. None Algorithm Attack

**Attack**: Set `alg` to `none`, remove signature

```json
{
  "alg": "none",
  "typ": "JWT"
}
```

**Mitigation**:
```javascript
// ✅ Explicitly reject 'none' algorithm
jwt.verify(token, publicKey, { algorithms: ['RS256'] }); // 'none' not allowed
```

### 3. JWT Cracking (Weak Secrets)

**Attack**: Brute force weak HMAC secrets

```javascript
// ❌ Weak secret
const token = jwt.sign(payload, 'secret', { algorithm: 'HS256' });
// Can be cracked in seconds
```

**Mitigation**:
```javascript
// ✅ Strong random secret (256 bits minimum)
const secret = crypto.randomBytes(32).toString('hex');
const token = jwt.sign(payload, secret, { algorithm: 'HS256' });

// ✅ OR use RSA/ECDSA (public key can be shared)
const token = jwt.sign(payload, privateKey, { algorithm: 'ES256' });
```

### 4. Token Sidejacking

**Attack**: Steal JWT from localStorage (XSS)

```javascript
// ❌ Vulnerable to XSS
localStorage.setItem('token', jwt);
```

**Mitigation**:
```javascript
// ✅ Use httpOnly cookies
res.cookie('token', jwt, {
  httpOnly: true,    // Not accessible via JavaScript
  secure: true,      // HTTPS only
  sameSite: 'strict' // CSRF protection
});
```

### 5. Token Replay

**Attack**: Reuse stolen token before expiration

**Mitigation**:
```javascript
// ✅ Short expiration
{ expiresIn: '15m' }

// ✅ Bind to IP or user agent (careful with mobile)
{
  sub: 'user-123',
  ip: '192.168.1.1',
  user_agent: 'Mozilla/5.0...'
}

// ✅ Use refresh token rotation
```

## Microservices Patterns

### Pattern 1: API Gateway JWT Validation

```javascript
// API Gateway validates JWT, forwards claims to services
const express = require('express');
const jwt = require('jsonwebtoken');

const app = express();

const validateJWT = (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  try {
    const decoded = jwt.verify(token, publicKey, { algorithms: ['RS256'] });

    // Forward user claims as headers
    req.headers['X-User-Id'] = decoded.sub;
    req.headers['X-User-Email'] = decoded.email;
    req.headers['X-User-Roles'] = JSON.stringify(decoded.roles);

    next();
  } catch (err) {
    res.status(401).json({ error: 'Invalid token' });
  }
};

app.use('/api', validateJWT, proxyToServices);
```

### Pattern 2: Service-to-Service JWT

```javascript
// Service A creates JWT to call Service B
async function callServiceB(data) {
  const serviceToken = jwt.sign(
    {
      iss: 'service-a',
      aud: 'service-b',
      sub: 'service-a',
      scope: 'service-b:read'
    },
    privateKey,
    { algorithm: 'RS256', expiresIn: '5m' }
  );

  const response = await fetch('https://service-b/api/data', {
    headers: {
      'Authorization': `Bearer ${serviceToken}`
    },
    method: 'POST',
    body: JSON.stringify(data)
  });

  return response.json();
}
```

### Pattern 3: JWT with Refresh Token

```javascript
// Login endpoint
app.post('/login', async (req, res) => {
  const user = await authenticate(req.body.email, req.body.password);

  // Short-lived access token (JWT)
  const accessToken = jwt.sign(
    {
      sub: user.id,
      email: user.email,
      roles: user.roles
    },
    privateKey,
    { algorithm: 'RS256', expiresIn: '15m' }
  );

  // Long-lived refresh token (opaque, stored in DB)
  const refreshToken = crypto.randomBytes(32).toString('hex');
  await db.refreshTokens.create({
    token: refreshToken,
    userId: user.id,
    expiresAt: new Date(Date.now() + 7 * 24 * 60 * 60 * 1000)
  });

  res.json({ accessToken, refreshToken });
});

// Refresh endpoint
app.post('/refresh', async (req, res) => {
  const { refreshToken } = req.body;

  const storedToken = await db.refreshTokens.findOne({
    token: refreshToken,
    expiresAt: { $gt: new Date() }
  });

  if (!storedToken) {
    return res.status(401).json({ error: 'Invalid refresh token' });
  }

  const user = await db.users.findById(storedToken.userId);

  // Issue new access token
  const accessToken = jwt.sign(
    {
      sub: user.id,
      email: user.email,
      roles: user.roles
    },
    privateKey,
    { algorithm: 'RS256', expiresIn: '15m' }
  );

  res.json({ accessToken });
});
```

## Tools and Libraries

### Libraries
- **Node.js**: `jsonwebtoken`, `jose`, `node-jose`
- **Python**: `PyJWT`, `python-jose`, `authlib`
- **Java**: `jjwt`, `nimbus-jose-jwt`, `jose4j`
- **Go**: `golang-jwt/jwt`, `square/go-jose`
- **.NET**: `System.IdentityModel.Tokens.Jwt`, `jose-jwt`
- **Ruby**: `jwt`, `ruby-jose`

### Tools
- **jwt.io** - Decode, verify, and generate JWTs
- **jwt-cli** - Command-line JWT tool
- **Postman** - Test JWT APIs
- **JWK Generator** - Generate JWKs

## Interview Questions

### Basic
1. What is a JWT and what are its three parts?
2. What's the difference between JWS and JWE?
3. When would you use HS256 vs RS256?
4. What are registered claims in JWT?

### Intermediate
5. How do you prevent algorithm confusion attacks?
6. Explain JWT expiration and refresh token pattern
7. How would you implement JWT revocation?
8. What are the security risks of storing JWTs in localStorage?

### Advanced
9. Design a JWT-based authentication system for microservices
10. How do you handle JWT key rotation in a distributed system?
11. Compare JWE vs HTTPS for protecting JWT payload
12. How would you implement scope-based authorization with JWT?

## Further Reading

- [RFC 7519 - JWT](https://tools.ietf.org/html/rfc7519)
- [RFC 7515 - JWS](https://tools.ietf.org/html/rfc7515)
- [RFC 7516 - JWE](https://tools.ietf.org/html/rfc7516)
- [RFC 7517 - JWK](https://tools.ietf.org/html/rfc7517)
- [JWT.io](https://jwt.io/)
- [JWT Best Practices (IETF)](https://tools.ietf.org/html/rfc8725)
