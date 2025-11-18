# Zero Trust Security and Passwordless Authentication

## Zero Trust Security

### Overview

**Zero Trust** is a security model based on the principle: **"Never trust, always verify"**

Traditional security models assume trust within the network perimeter (castle-and-moat). Zero Trust assumes **no implicit trust** regardless of location.

**Core Principles**:
1. **Verify explicitly** - Always authenticate and authorize based on all available data points
2. **Use least privilege access** - Limit user access with Just-In-Time and Just-Enough-Access (JIT/JEA)
3. **Assume breach** - Minimize blast radius and segment access

### Traditional vs Zero Trust

**Traditional Security (Perimeter-Based)**:
```
Internet ──► Firewall ──► Trusted Internal Network
                          (Everything inside is trusted)
```

**Zero Trust**:
```
Internet ──► Identity-Based Access
             ├─► Verify user identity
             ├─► Check device security posture
             ├─► Validate context (location, time)
             ├─► Enforce least privilege
             └─► Continuous monitoring
```

### Zero Trust Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    Zero Trust Components                 │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────┐         ┌─────────────────┐        │
│  │  Identity      │◄────────│   Policy        │        │
│  │  Provider      │         │   Engine        │        │
│  └────────┬───────┘         └─────────┬───────┘        │
│           │                           │                 │
│           │  ┌──────────────────┐    │                │
│           ├──│  Policy Decision │◄───┤                │
│           │  │  Point (PDP)     │    │                │
│           │  └────────┬─────────┘    │                │
│           │           │               │                 │
│  ┌────────▼───────┐   │      ┌───────▼─────────┐      │
│  │  Device Trust  │   │      │  Continuous     │      │
│  │  Assessment    │   │      │  Monitoring     │      │
│  └────────────────┘   │      └─────────────────┘      │
│                       │                                │
│              ┌────────▼─────────┐                     │
│              │  Policy          │                     │
│              │  Enforcement     │                     │
│              │  Point (PEP)     │                     │
│              └────────┬─────────┘                     │
│                       │                                │
│              ┌────────▼─────────┐                     │
│              │   Resources      │                     │
│              │  (Services, Data)│                     │
│              └──────────────────┘                     │
└──────────────────────────────────────────────────────────┘
```

### Implementation Components

#### 1. Identity Verification

```javascript
// Multi-factor identity verification
async function verifyIdentity(req) {
  const checks = {
    credentials: false,
    mfa: false,
    deviceTrust: false,
    location: false
  };

  // 1. Verify credentials (password/passwordless)
  const user = await authenticateUser(req.body.email, req.body.password);
  checks.credentials = !!user;

  // 2. Verify MFA
  if (user.mfaEnabled) {
    const mfaValid = await verifyMFA(user, req.body.mfaCode);
    checks.mfa = mfaValid;
  } else {
    checks.mfa = false; // Require MFA in zero trust
  }

  // 3. Check device trust
  const deviceFingerprint = req.headers['x-device-id'];
  const trustedDevice = await isDeviceTrusted(user.id, deviceFingerprint);
  checks.deviceTrust = trustedDevice;

  // 4. Validate location
  const location = geolocate(req.ip);
  const allowedLocations = user.allowedLocations || [];
  checks.location = allowedLocations.length === 0 ||
                    allowedLocations.some(l => isNear(location, l));

  return checks;
}
```

#### 2. Device Posture Assessment

```javascript
// Check device security posture
async function assessDevicePosture(deviceId) {
  const device = await db.devices.findById(deviceId);

  const posture = {
    encrypted: device.diskEncrypted,
    updated: device.osUpToDate,
    antivirus: device.antivirusActive,
    firewall: device.firewallEnabled,
    jailbroken: !device.isJailbroken,
    compliant: false
  };

  // Device must meet all requirements
  posture.compliant = Object.entries(posture)
    .filter(([key]) => key !== 'compliant')
    .every(([, value]) => value === true);

  return posture;
}

// Endpoint to report device posture
app.post('/api/device/posture', requireAuth, async (req, res) => {
  const { deviceId, posture } = req.body;

  await db.devices.update(deviceId, {
    ...posture,
    lastChecked: new Date()
  });

  res.json({ success: true });
});
```

#### 3. Continuous Authorization

```javascript
// Re-evaluate access on every request
async function enforceZeroTrust(req, res, next) {
  const accessToken = req.headers.authorization?.split(' ')[1];

  // 1. Verify token
  const payload = jwt.verify(accessToken, publicKey);

  // 2. Check if token is revoked
  const revoked = await isTokenRevoked(payload.jti);
  if (revoked) {
    return res.status(401).json({ error: 'Token revoked' });
  }

  // 3. Check device posture
  const deviceId = req.headers['x-device-id'];
  const posture = await assessDevicePosture(deviceId);
  if (!posture.compliant) {
    return res.status(403).json({
      error: 'Device does not meet security requirements',
      posture
    });
  }

  // 4. Evaluate context
  const riskScore = calculateRiskScore(req, payload);
  if (riskScore > 70) {
    return res.status(403).json({ error: 'High risk detected' });
  }

  // 5. Check resource-level permissions
  const hasAccess = await checkPermission(
    payload.sub,
    req.method,
    req.path
  );

  if (!hasAccess) {
    return res.status(403).json({ error: 'Forbidden' });
  }

  req.user = payload;
  next();
}

app.use('/api', enforceZeroTrust);
```

#### 4. Micro-Segmentation

```javascript
// Network micro-segmentation using service mesh
// Istio AuthorizationPolicy
const authzPolicy = `
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: payment-service-authz
  namespace: production
spec:
  selector:
    matchLabels:
      app: payment-service
  action: ALLOW
  rules:
  - from:
    - source:
        principals: ["cluster.local/ns/production/sa/order-service"]
    to:
    - operation:
        methods: ["POST"]
        paths: ["/api/process-payment"]
  - from:
    - source:
        principals: ["cluster.local/ns/production/sa/refund-service"]
    to:
    - operation:
        methods: ["POST"]
        paths: ["/api/refund"]
`;

// Only order-service can call /api/process-payment
// Only refund-service can call /api/refund
```

### Zero Trust for Microservices

```javascript
// Service-to-service authentication with zero trust
class ZeroTrustServiceClient {
  constructor(serviceName, targetService) {
    this.serviceName = serviceName;
    this.targetService = targetService;
  }

  async call(endpoint, data) {
    // 1. Get service identity token
    const serviceToken = await this.getServiceToken();

    // 2. Verify mTLS certificate
    const clientCert = await this.getClientCertificate();

    // 3. Add request context
    const context = {
      requestId: crypto.randomUUID(),
      timestamp: new Date().toISOString(),
      sourceService: this.serviceName,
      targetService: this.targetService
    };

    // 4. Make request with mTLS and JWT
    const response = await fetch(`https://${this.targetService}${endpoint}`, {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${serviceToken}`,
        'X-Request-Context': JSON.stringify(context)
      },
      cert: clientCert.cert,
      key: clientCert.key,
      ca: clientCert.ca,
      body: JSON.stringify(data)
    });

    return response.json();
  }

  async getServiceToken() {
    // Get short-lived token from identity provider
    const response = await fetch('https://auth.internal/token', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        grant_type: 'client_credentials',
        client_id: this.serviceName,
        client_secret: process.env.SERVICE_SECRET,
        audience: this.targetService
      })
    });

    const { access_token } = await response.json();
    return access_token;
  }

  async getClientCertificate() {
    return {
      cert: fs.readFileSync('/etc/certs/service.crt'),
      key: fs.readFileSync('/etc/certs/service.key'),
      ca: fs.readFileSync('/etc/certs/ca.crt')
    };
  }
}
```

## Passwordless Authentication

### Overview

Passwordless authentication eliminates passwords entirely, using alternative factors like:
- Biometrics (fingerprint, face, iris)
- Magic links (email/SMS)
- Hardware tokens (FIDO2/WebAuthn)
- Push notifications

**Benefits**:
- No password to forget or steal
- Resistant to phishing
- Better user experience
- Lower support costs

### WebAuthn/FIDO2

**How it works**:
1. User registers device (fingerprint, Face ID, YubiKey)
2. Device generates public/private key pair
3. Public key sent to server, private key stays on device
4. To authenticate, server sends challenge
5. Device signs challenge with private key
6. Server verifies with public key

#### Implementation

```javascript
const { Fido2Lib } = require('fido2-lib');

const f2l = new Fido2Lib({
  timeout: 60000,
  rpId: 'example.com',
  rpName: 'My App',
  challengeSize: 128,
  attestation: 'none',
  cryptoParams: [-7, -257] // ES256, RS256
});

// Registration
app.post('/auth/register/start', async (req, res) => {
  const { email } = req.body;

  let user = await db.users.findOne({ email });

  if (!user) {
    user = await db.users.create({
      email,
      id: crypto.randomUUID()
    });
  }

  const registrationOptions = await f2l.attestationOptions();

  req.session.challenge = registrationOptions.challenge;
  req.session.userId = user.id;

  res.json({
    ...registrationOptions,
    user: {
      id: Buffer.from(user.id).toString('base64url'),
      name: email,
      displayName: email
    }
  });
});

app.post('/auth/register/finish', async (req, res) => {
  const { credential } = req.body;
  const expectedChallenge = req.session.challenge;

  const attestationExpectations = {
    challenge: expectedChallenge,
    origin: 'https://example.com',
    factor: 'either'
  };

  const regResult = await f2l.attestationResult(
    credential,
    attestationExpectations
  );

  // Store credential
  await db.credentials.create({
    userId: req.session.userId,
    credentialId: regResult.authnrData.get('credId'),
    publicKey: regResult.authnrData.get('credentialPublicKeyPem'),
    counter: regResult.authnrData.get('counter'),
    createdAt: new Date()
  });

  res.json({ success: true });
});

// Authentication
app.post('/auth/login/start', async (req, res) => {
  const { email } = req.body;

  const user = await db.users.findOne({ email });

  if (!user) {
    return res.status(404).json({ error: 'User not found' });
  }

  const credentials = await db.credentials.find({ userId: user.id });

  const assertionOptions = await f2l.assertionOptions();

  req.session.challenge = assertionOptions.challenge;
  req.session.userId = user.id;

  res.json({
    ...assertionOptions,
    allowCredentials: credentials.map(c => ({
      type: 'public-key',
      id: c.credentialId
    }))
  });
});

app.post('/auth/login/finish', async (req, res) => {
  const { credential } = req.body;
  const expectedChallenge = req.session.challenge;
  const userId = req.session.userId;

  const storedCredential = await db.credentials.findOne({
    credentialId: credential.id
  });

  if (!storedCredential) {
    return res.status(401).json({ error: 'Invalid credential' });
  }

  const assertionExpectations = {
    challenge: expectedChallenge,
    origin: 'https://example.com',
    factor: 'either',
    publicKey: storedCredential.publicKey,
    prevCounter: storedCredential.counter,
    userHandle: userId
  };

  const authnResult = await f2l.assertionResult(
    credential,
    assertionExpectations
  );

  // Update counter (prevent replay attacks)
  await db.credentials.update(storedCredential.id, {
    counter: authnResult.authnrData.get('counter'),
    lastUsed: new Date()
  });

  // Create session
  const token = jwt.sign(
    { sub: userId },
    privateKey,
    { algorithm: 'RS256', expiresIn: '1h' }
  );

  res.json({ token });
});
```

#### Client-Side (Browser)

```javascript
// Registration
async function registerPasswordless(email) {
  // Get options from server
  const optionsResponse = await fetch('/auth/register/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });

  const options = await optionsResponse.json();

  // Convert base64url strings to Uint8Array
  options.challenge = base64urlToUint8Array(options.challenge);
  options.user.id = base64urlToUint8Array(options.user.id);

  // Create credential (triggers biometric prompt)
  const credential = await navigator.credentials.create({
    publicKey: options
  });

  // Send credential to server
  const finishResponse = await fetch('/auth/register/finish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      credential: {
        id: credential.id,
        rawId: uint8ArrayToBase64url(credential.rawId),
        type: credential.type,
        response: {
          attestationObject: uint8ArrayToBase64url(credential.response.attestationObject),
          clientDataJSON: uint8ArrayToBase64url(credential.response.clientDataJSON)
        }
      }
    })
  });

  return finishResponse.json();
}

// Login
async function loginPasswordless(email) {
  // Get options from server
  const optionsResponse = await fetch('/auth/login/start', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email })
  });

  const options = await optionsResponse.json();

  // Convert challenge
  options.challenge = base64urlToUint8Array(options.challenge);
  options.allowCredentials = options.allowCredentials.map(c => ({
    ...c,
    id: base64urlToUint8Array(c.id)
  }));

  // Get credential (triggers biometric prompt)
  const credential = await navigator.credentials.get({
    publicKey: options
  });

  // Send credential to server
  const finishResponse = await fetch('/auth/login/finish', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      credential: {
        id: credential.id,
        rawId: uint8ArrayToBase64url(credential.rawId),
        type: credential.type,
        response: {
          authenticatorData: uint8ArrayToBase64url(credential.response.authenticatorData),
          clientDataJSON: uint8ArrayToBase64url(credential.response.clientDataJSON),
          signature: uint8ArrayToBase64url(credential.response.signature),
          userHandle: credential.response.userHandle ?
            uint8ArrayToBase64url(credential.response.userHandle) : null
        }
      }
    })
  });

  const { token } = await finishResponse.json();
  localStorage.setItem('token', token);

  return token;
}
```

### Magic Links

```javascript
// Send magic link
app.post('/auth/magic-link', async (req, res) => {
  const { email } = req.body;

  let user = await db.users.findOne({ email });

  if (!user) {
    user = await db.users.create({ email });
  }

  // Generate token
  const token = crypto.randomBytes(32).toString('hex');

  // Store token
  await db.magicTokens.create({
    userId: user.id,
    token,
    expiresAt: new Date(Date.now() + 15 * 60 * 1000) // 15 minutes
  });

  // Send email
  const magicLink = `https://example.com/auth/verify?token=${token}`;

  await sendEmail(email, {
    subject: 'Log in to My App',
    html: `Click <a href="${magicLink}">here</a> to log in. Link expires in 15 minutes.`
  });

  res.json({ success: true });
});

// Verify magic link
app.get('/auth/verify', async (req, res) => {
  const { token } = req.query;

  const magicToken = await db.magicTokens.findOne({
    token,
    expiresAt: { $gt: new Date() },
    used: false
  });

  if (!magicToken) {
    return res.status(401).json({ error: 'Invalid or expired token' });
  }

  // Mark as used
  await db.magicTokens.update(magicToken.id, { used: true });

  // Create session
  const user = await db.users.findById(magicToken.userId);

  const sessionToken = jwt.sign(
    { sub: user.id, email: user.email },
    privateKey,
    { algorithm: 'RS256', expiresIn: '7d' }
  );

  res.redirect(`/dashboard?token=${sessionToken}`);
});
```

### One-Time Passwords (OTP)

```javascript
// Send OTP to email
app.post('/auth/otp/send', async (req, res) => {
  const { email } = req.body;

  const otp = Math.floor(100000 + Math.random() * 900000).toString();

  await db.otps.create({
    email,
    otp: bcrypt.hashSync(otp, 10),
    expiresAt: new Date(Date.now() + 10 * 60 * 1000) // 10 minutes
  });

  await sendEmail(email, {
    subject: 'Your login code',
    text: `Your code is: ${otp}`
  });

  res.json({ success: true });
});

// Verify OTP
app.post('/auth/otp/verify', async (req, res) => {
  const { email, otp } = req.body;

  const storedOtp = await db.otps.findOne({
    email,
    expiresAt: { $gt: new Date() }
  });

  if (!storedOtp || !bcrypt.compareSync(otp, storedOtp.otp)) {
    return res.status(401).json({ error: 'Invalid OTP' });
  }

  // Delete used OTP
  await db.otps.delete(storedOtp.id);

  // Create/get user
  let user = await db.users.findOne({ email });
  if (!user) {
    user = await db.users.create({ email });
  }

  // Create session
  const token = jwt.sign(
    { sub: user.id, email },
    privateKey,
    { algorithm: 'RS256', expiresIn: '7d' }
  );

  res.json({ token });
});
```

## Best Practices

### Zero Trust
1. **Identity is the new perimeter** - Strong authentication required
2. **Verify explicitly** - Use all available data points
3. **Least privilege access** - Grant minimum necessary permissions
4. **Assume breach** - Design for compromise scenarios
5. **Continuous monitoring** - Real-time threat detection

### Passwordless
1. **Fallback mechanisms** - Provide recovery options
2. **Progressive enrollment** - Don't force immediately
3. **Multi-device support** - Allow multiple authenticators
4. **Clear UX** - Educate users on new auth method
5. **Gradual rollout** - Test with subset of users first

## Interview Questions

### Zero Trust
1. What is Zero Trust and how does it differ from traditional security?
2. Explain the three core principles of Zero Trust
3. How do you implement Zero Trust in a microservices architecture?
4. What is device posture assessment?

### Passwordless
5. What is WebAuthn and how does it work?
6. Compare magic links vs WebAuthn for passwordless auth
7. How do you handle account recovery in passwordless systems?
8. What are the security advantages of passwordless authentication?

### Advanced
9. Design a Zero Trust architecture for a financial services platform
10. How would you implement continuous authorization in microservices?
11. Compare FIDO2 vs traditional MFA
12. What are the challenges of implementing passwordless auth at scale?

## Further Reading

- [NIST Zero Trust Architecture](https://www.nist.gov/publications/zero-trust-architecture)
- [WebAuthn Spec](https://www.w3.org/TR/webauthn/)
- [FIDO Alliance](https://fidoalliance.org/)
- [Microsoft Zero Trust](https://www.microsoft.com/en-us/security/business/zero-trust)
