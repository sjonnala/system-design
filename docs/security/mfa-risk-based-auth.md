# Multi-Factor Authentication (MFA) and Risk-Based Authentication

## Multi-Factor Authentication (MFA)

### Overview

MFA requires users to provide two or more verification factors to gain access to a resource. It's one of the most effective security controls against account takeover.

**The Three Factor Types**:
1. **Something you know** - Password, PIN, security question
2. **Something you have** - Phone, hardware token, smart card
3. **Something you are** - Fingerprint, facial recognition, iris scan

### Why MFA Matters

**Statistics**:
- 99.9% of account compromise attacks can be blocked by MFA (Microsoft)
- Stolen passwords account for 81% of breaches (Verizon DBIR)
- SMS-based MFA reduces risk by 96%, authenticator apps by 99.9%

## MFA Methods

### 1. Time-Based One-Time Password (TOTP)

**How it works**: Generates 6-digit codes that change every 30 seconds

**Algorithm**:
```javascript
const TOTP = HMAC-SHA1(secret, floor(current_time / 30))
```

**Implementation**:
```javascript
const speakeasy = require('speakeasy');
const QRCode = require('qrcode');

// Generate secret
const secret = speakeasy.generateSecret({
  name: 'MyApp (user@example.com)',
  length: 32
});

// Generate QR code for user to scan
QRCode.toDataURL(secret.otpauth_url, (err, dataUrl) => {
  console.log(dataUrl); // Display to user
});

// Verify TOTP code
const verified = speakeasy.totp.verify({
  secret: secret.base32,
  encoding: 'base32',
  token: userInputCode,
  window: 1 // Allow 1 step before/after for clock skew
});

if (verified) {
  console.log('MFA successful');
}
```

**Popular Apps**: Google Authenticator, Microsoft Authenticator, Authy

**Pros**:
- No internet required
- Works offline
- Very secure

**Cons**:
- Can be lost if device is lost (without backup)
- Manual code entry required

### 2. SMS-Based OTP

**How it works**: Send 6-digit code via SMS

**Implementation**:
```javascript
const twilio = require('twilio');
const client = twilio(accountSid, authToken);

// Generate and store OTP
const otp = Math.floor(100000 + Math.random() * 900000);
await db.otps.create({
  userId: user.id,
  otp: otp,
  expiresAt: new Date(Date.now() + 5 * 60 * 1000) // 5 minutes
});

// Send SMS
await client.messages.create({
  body: `Your verification code is: ${otp}`,
  from: '+1234567890',
  to: user.phoneNumber
});

// Verify OTP
app.post('/verify-otp', async (req, res) => {
  const { userId, otp } = req.body;

  const storedOtp = await db.otps.findOne({
    userId,
    otp,
    expiresAt: { $gt: new Date() }
  });

  if (!storedOtp) {
    return res.status(401).json({ error: 'Invalid or expired OTP' });
  }

  // Delete used OTP
  await db.otps.delete(storedOtp.id);

  res.json({ success: true });
});
```

**Pros**:
- Easy to use
- No app required
- Wide compatibility

**Cons**:
- Vulnerable to SIM swapping
- SMS interception
- Requires cell service
- **NOT recommended for high-security applications**

### 3. Push Notifications

**How it works**: Send push notification to mobile app for approval

**Flow**:
```
1. User enters password
2. Server sends push notification to user's device
3. User approves/denies on device
4. Server receives response and grants/denies access
```

**Implementation** (using Firebase Cloud Messaging):
```javascript
const admin = require('firebase-admin');

// Send push notification
async function sendMfaChallenge(userId) {
  const challengeId = crypto.randomUUID();

  // Store challenge
  await db.mfaChallenges.create({
    id: challengeId,
    userId,
    status: 'pending',
    expiresAt: new Date(Date.now() + 2 * 60 * 1000) // 2 minutes
  });

  // Get user's device token
  const user = await db.users.findById(userId);

  // Send push notification
  await admin.messaging().send({
    token: user.deviceToken,
    notification: {
      title: 'Login Approval Required',
      body: 'Approve login to MyApp from Chrome on Windows'
    },
    data: {
      challengeId,
      location: 'San Francisco, CA',
      device: 'Chrome on Windows'
    }
  });

  return challengeId;
}

// User approves on mobile app
app.post('/mfa/approve', async (req, res) => {
  const { challengeId, approved } = req.body;

  const challenge = await db.mfaChallenges.findById(challengeId);

  if (!challenge || challenge.expiresAt < new Date()) {
    return res.status(400).json({ error: 'Invalid or expired challenge' });
  }

  await db.mfaChallenges.update(challengeId, {
    status: approved ? 'approved' : 'denied'
  });

  res.json({ success: true });
});

// Poll for approval on web
app.get('/mfa/status/:challengeId', async (req, res) => {
  const challenge = await db.mfaChallenges.findById(req.params.challengeId);

  res.json({
    status: challenge.status,
    expired: challenge.expiresAt < new Date()
  });
});
```

**Pros**:
- Excellent UX (one tap)
- Shows context (location, device)
- Very secure

**Cons**:
- Requires app installation
- Needs internet connection

### 4. Hardware Security Keys (FIDO2/WebAuthn)

**How it works**: Physical device (YubiKey, Titan) for authentication

**Implementation** (WebAuthn):
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
app.post('/mfa/register/start', async (req, res) => {
  const user = req.user;

  const registrationOptions = await f2l.attestationOptions();

  // Store challenge
  req.session.challenge = registrationOptions.challenge;

  res.json({
    ...registrationOptions,
    user: {
      id: Buffer.from(user.id),
      name: user.email,
      displayName: user.name
    }
  });
});

app.post('/mfa/register/finish', async (req, res) => {
  const { credential } = req.body;
  const challenge = req.session.challenge;

  const attestationExpectations = {
    challenge,
    origin: 'https://example.com',
    factor: 'either'
  };

  const regResult = await f2l.attestationResult(
    credential,
    attestationExpectations
  );

  // Store credential
  await db.credentials.create({
    userId: req.user.id,
    credentialId: regResult.authnrData.get('credId'),
    publicKey: regResult.authnrData.get('credentialPublicKeyPem'),
    counter: regResult.authnrData.get('counter')
  });

  res.json({ success: true });
});

// Authentication
app.post('/mfa/authenticate/start', async (req, res) => {
  const user = req.user;

  const credentials = await db.credentials.find({ userId: user.id });

  const assertionOptions = await f2l.assertionOptions();

  req.session.challenge = assertionOptions.challenge;

  res.json({
    ...assertionOptions,
    allowCredentials: credentials.map(c => ({
      type: 'public-key',
      id: c.credentialId
    }))
  });
});

app.post('/mfa/authenticate/finish', async (req, res) => {
  const { credential } = req.body;
  const challenge = req.session.challenge;

  const storedCredential = await db.credentials.findOne({
    credentialId: credential.id
  });

  const assertionExpectations = {
    challenge,
    origin: 'https://example.com',
    factor: 'either',
    publicKey: storedCredential.publicKey,
    prevCounter: storedCredential.counter,
    userHandle: req.user.id
  };

  const authnResult = await f2l.assertionResult(
    credential,
    assertionExpectations
  );

  // Update counter
  await db.credentials.update(storedCredential.id, {
    counter: authnResult.authnrData.get('counter')
  });

  res.json({ success: true });
});
```

**Pros**:
- Phishing-resistant
- Most secure option
- No codes to type

**Cons**:
- Requires hardware purchase
- Can be lost
- Limited browser support (improving)

### 5. Biometric Authentication

**Types**:
- **Fingerprint** - Touch ID, in-display sensors
- **Facial recognition** - Face ID, Windows Hello
- **Iris scan** - Samsung devices
- **Voice recognition** - Less common

**Implementation** (using WebAuthn with platform authenticator):
```javascript
// Enable biometric authentication
const publicKeyCredentialCreationOptions = {
  challenge: Uint8Array.from(randomChallenge),
  rp: {
    name: "My App",
    id: "example.com",
  },
  user: {
    id: Uint8Array.from(userId),
    name: userEmail,
    displayName: userName,
  },
  pubKeyCredParams: [{alg: -7, type: "public-key"}],
  authenticatorSelection: {
    authenticatorAttachment: "platform", // Built-in biometric
    userVerification: "required"
  },
  timeout: 60000,
  attestation: "direct"
};

const credential = await navigator.credentials.create({
  publicKey: publicKeyCredentialCreationOptions
});
```

**Pros**:
- Excellent UX
- Cannot be forgotten
- Fast

**Cons**:
- Privacy concerns
- Can't be changed if compromised
- May not work for all users (injuries, etc.)
- Requires compatible hardware

### 6. Backup Codes

**How it works**: One-time use codes generated during MFA setup

**Implementation**:
```javascript
const crypto = require('crypto');

// Generate backup codes
function generateBackupCodes(count = 10) {
  const codes = [];
  for (let i = 0; i < count; i++) {
    const code = crypto.randomBytes(4).toString('hex').toUpperCase();
    codes.push(code.match(/.{1,4}/g).join('-')); // Format: XXXX-XXXX
  }
  return codes;
}

// Store backup codes (hashed)
app.post('/mfa/generate-backup-codes', async (req, res) => {
  const user = req.user;

  const codes = generateBackupCodes();

  // Hash and store
  const hashedCodes = codes.map(code => ({
    userId: user.id,
    code: bcrypt.hashSync(code, 10),
    used: false
  }));

  await db.backupCodes.createMany(hashedCodes);

  // Return codes to user (only time they see them)
  res.json({ codes });
});

// Verify backup code
app.post('/mfa/verify-backup-code', async (req, res) => {
  const { userId, code } = req.body;

  const backupCodes = await db.backupCodes.find({
    userId,
    used: false
  });

  for (const storedCode of backupCodes) {
    if (bcrypt.compareSync(code, storedCode.code)) {
      // Mark as used
      await db.backupCodes.update(storedCode.id, { used: true });

      return res.json({ success: true });
    }
  }

  res.status(401).json({ error: 'Invalid backup code' });
});
```

**Pros**:
- Works when primary MFA unavailable
- Simple to implement

**Cons**:
- Can be stolen
- One-time use
- Users may lose them

## MFA Enforcement Strategies

### 1. Mandatory MFA for All Users

```javascript
app.post('/login', async (req, res) => {
  const user = await authenticate(req.body.email, req.body.password);

  if (!user.mfaEnabled) {
    // Force MFA setup
    return res.json({
      status: 'mfa_required',
      setupUrl: '/mfa/setup'
    });
  }

  // Require MFA verification
  const challengeId = await sendMfaChallenge(user);

  res.json({
    status: 'mfa_challenge',
    challengeId
  });
});
```

### 2. Optional MFA with Incentives

```javascript
// Offer extended session for users with MFA
if (user.mfaEnabled) {
  sessionDuration = 30 * 24 * 60 * 60 * 1000; // 30 days
} else {
  sessionDuration = 24 * 60 * 60 * 1000; // 1 day
}
```

### 3. Adaptive MFA (Risk-Based)

```javascript
// Only require MFA for risky actions
const riskScore = calculateRiskScore(req);

if (riskScore > THRESHOLD || action.requiresMfa) {
  return sendMfaChallenge(user);
}
```

## Risk-Based Authentication

### Overview

Risk-based authentication (RBA) analyzes context to determine authentication requirements dynamically.

**Factors Analyzed**:
- **Device**: Known/unknown device
- **Location**: Usual location, impossible travel
- **Behavior**: Typing patterns, mouse movements
- **Time**: Unusual time of access
- **Network**: IP address, VPN usage
- **Transaction**: Amount, recipient

### Risk Scoring Implementation

```javascript
function calculateRiskScore(req, user) {
  let risk = 0;

  // 1. Device fingerprinting
  const deviceFingerprint = req.headers['x-device-id'];
  const knownDevice = await db.devices.findOne({
    userId: user.id,
    fingerprint: deviceFingerprint
  });

  if (!knownDevice) {
    risk += 30; // Unknown device
  }

  // 2. Location check
  const currentLocation = geolocate(req.ip);
  const lastLocation = user.lastLoginLocation;

  if (lastLocation) {
    const distance = calculateDistance(currentLocation, lastLocation);
    const timeSinceLastLogin = Date.now() - user.lastLoginAt;

    // Impossible travel detection
    const maxPossibleDistance = (timeSinceLastLogin / 3600000) * 900; // 900 km/h
    if (distance > maxPossibleDistance) {
      risk += 50; // Impossible travel
    } else if (distance > 500) {
      risk += 20; // Different location
    }
  }

  // 3. Time-based anomaly
  const hour = new Date().getHours();
  const usualHours = user.usualLoginHours || [];

  if (!usualHours.includes(hour)) {
    risk += 15; // Unusual time
  }

  // 4. IP reputation
  const ipReputation = await checkIpReputation(req.ip);
  if (ipReputation.vpn) risk += 10;
  if (ipReputation.proxy) risk += 15;
  if (ipReputation.tor) risk += 40;
  if (ipReputation.blacklisted) risk += 50;

  // 5. Velocity check (multiple login attempts)
  const recentAttempts = await db.loginAttempts.count({
    userId: user.id,
    createdAt: { $gt: new Date(Date.now() - 15 * 60 * 1000) }
  });

  if (recentAttempts > 3) {
    risk += 25;
  }

  // 6. Browser/User-Agent anomaly
  const userAgent = req.headers['user-agent'];
  if (!user.knownUserAgents.includes(userAgent)) {
    risk += 10;
  }

  return Math.min(risk, 100); // Cap at 100
}
```

### Risk-Based Actions

```javascript
app.post('/login', async (req, res) => {
  const user = await authenticate(req.body.email, req.body.password);

  const riskScore = calculateRiskScore(req, user);

  if (riskScore < 20) {
    // Low risk - allow login
    const token = createSession(user);
    return res.json({ token });

  } else if (riskScore < 50) {
    // Medium risk - require MFA
    const challengeId = await sendMfaChallenge(user);
    return res.json({
      status: 'mfa_required',
      challengeId
    });

  } else {
    // High risk - block and notify
    await sendSecurityAlert(user, {
      type: 'suspicious_login',
      riskScore,
      location: geolocate(req.ip),
      device: req.headers['user-agent']
    });

    return res.status(403).json({
      error: 'Login blocked due to suspicious activity'
    });
  }
});
```

### Behavioral Biometrics

```javascript
// Track typing patterns
class BehavioralBiometrics {
  constructor() {
    this.keystrokeTimings = [];
    this.mouseMovements = [];
  }

  recordKeystroke(key, timestamp) {
    this.keystrokeTimings.push({ key, timestamp });
  }

  recordMouseMovement(x, y, timestamp) {
    this.mouseMovements.push({ x, y, timestamp });
  }

  async analyze(userId) {
    // Get user's baseline behavior
    const baseline = await db.behaviorProfiles.findOne({ userId });

    if (!baseline) {
      // Create baseline
      await db.behaviorProfiles.create({
        userId,
        avgKeystrokeSpeed: this.calculateAvgSpeed(this.keystrokeTimings),
        mousePattern: this.analyzeMousePattern(this.mouseMovements)
      });
      return 0; // No risk for first time
    }

    // Compare current behavior to baseline
    const currentSpeed = this.calculateAvgSpeed(this.keystrokeTimings);
    const speedDeviation = Math.abs(currentSpeed - baseline.avgKeystrokeSpeed);

    if (speedDeviation > baseline.avgKeystrokeSpeed * 0.5) {
      return 30; // Significant deviation
    }

    return 0;
  }

  calculateAvgSpeed(timings) {
    if (timings.length < 2) return 0;

    let totalTime = 0;
    for (let i = 1; i < timings.length; i++) {
      totalTime += timings[i].timestamp - timings[i-1].timestamp;
    }
    return totalTime / (timings.length - 1);
  }

  analyzeMousePattern(movements) {
    // Simplified: Could use ML for actual pattern matching
    return {
      speed: this.calculateAvgSpeed(movements),
      path: movements.slice(0, 100) // First 100 points
    };
  }
}
```

### Step-Up Authentication

**Concept**: Require additional authentication for sensitive actions

```javascript
// Normal actions - session is enough
app.get('/api/profile', requireAuth, (req, res) => {
  res.json(req.user);
});

// Sensitive action - require MFA step-up
app.post('/api/transfer-money', requireAuth, requireStepUp, async (req, res) => {
  const { amount, recipient } = req.body;

  // Perform transfer
  await transferMoney(req.user.id, recipient, amount);

  res.json({ success: true });
});

// Step-up middleware
async function requireStepUp(req, res, next) {
  const lastStepUp = req.session.lastStepUpAt;

  // Step-up valid for 5 minutes
  if (lastStepUp && Date.now() - lastStepUp < 5 * 60 * 1000) {
    return next();
  }

  // Check if user completed step-up challenge
  const stepUpToken = req.headers['x-step-up-token'];

  if (!stepUpToken) {
    // Send MFA challenge
    const challengeId = await sendMfaChallenge(req.user);
    return res.status(428).json({
      error: 'step_up_required',
      challengeId
    });
  }

  // Verify step-up token
  const challenge = await db.mfaChallenges.findById(stepUpToken);

  if (challenge && challenge.status === 'approved') {
    req.session.lastStepUpAt = Date.now();
    return next();
  }

  res.status(401).json({ error: 'Invalid step-up token' });
}
```

## Best Practices

### 1. Support Multiple MFA Methods

```javascript
const mfaMethods = {
  totp: { enabled: true, label: 'Authenticator App', security: 'high' },
  sms: { enabled: true, label: 'SMS Code', security: 'medium' },
  push: { enabled: true, label: 'Push Notification', security: 'high' },
  webauthn: { enabled: true, label: 'Security Key', security: 'highest' },
  backup: { enabled: true, label: 'Backup Codes', security: 'low' }
};
```

### 2. Allow MFA Recovery

```javascript
// Email-based recovery
app.post('/mfa/recover', async (req, res) => {
  const { email } = req.body;
  const user = await db.users.findOne({ email });

  // Generate recovery token
  const token = crypto.randomBytes(32).toString('hex');

  await db.recoveryTokens.create({
    userId: user.id,
    token,
    expiresAt: new Date(Date.now() + 24 * 60 * 60 * 1000) // 24 hours
  });

  // Send recovery email
  await sendEmail(user.email, {
    subject: 'MFA Recovery',
    body: `Click here to reset MFA: https://example.com/mfa/recover/${token}`
  });

  res.json({ success: true });
});
```

### 3. Remember Trusted Devices

```javascript
app.post('/mfa/trust-device', async (req, res) => {
  const deviceFingerprint = req.headers['x-device-id'];

  await db.trustedDevices.create({
    userId: req.user.id,
    fingerprint: deviceFingerprint,
    expiresAt: new Date(Date.now() + 30 * 24 * 60 * 60 * 1000) // 30 days
  });

  res.json({ success: true });
});

// Skip MFA for trusted devices
async function requireMfa(req, res, next) {
  const deviceFingerprint = req.headers['x-device-id'];

  const trusted = await db.trustedDevices.findOne({
    userId: req.user.id,
    fingerprint: deviceFingerprint,
    expiresAt: { $gt: new Date() }
  });

  if (trusted) {
    return next(); // Skip MFA
  }

  // Require MFA
  const challengeId = await sendMfaChallenge(req.user);
  res.status(428).json({ challengeId });
}
```

### 4. Rate Limit MFA Attempts

```javascript
const rateLimiter = require('express-rate-limit');

const mfaLimiter = rateLimiter({
  windowMs: 15 * 60 * 1000, // 15 minutes
  max: 5, // 5 attempts
  message: 'Too many MFA attempts, please try again later'
});

app.post('/mfa/verify', mfaLimiter, verifyMfaCode);
```

## Interview Questions

### Basic
1. What is MFA and why is it important?
2. Name three types of MFA factors
3. What's the difference between TOTP and SMS OTP?
4. What are backup codes and when are they used?

### Intermediate
5. How does TOTP (Time-Based OTP) work?
6. What is risk-based authentication?
7. Explain step-up authentication with an example
8. Why is SMS-based MFA considered less secure?

### Advanced
9. Design an MFA system for a banking application
10. How would you implement impossible travel detection?
11. Compare TOTP vs WebAuthn for enterprise use
12. How do you handle MFA in microservices architecture?

## Further Reading

- [NIST Special Publication 800-63B](https://pages.nist.gov/800-63-3/sp800-63b.html) - Digital Identity Guidelines
- [FIDO2/WebAuthn Specifications](https://www.w3.org/TR/webauthn/)
- [RFC 6238 - TOTP](https://tools.ietf.org/html/rfc6238)
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)
