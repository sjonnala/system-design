# SAML Bearer Assertion Grant Flow (SAML 2.0 Profile for OAuth 2.0)

## Overview

The SAML Bearer Assertion Grant Flow (RFC 7522) allows OAuth 2.0 clients to exchange a **SAML assertion** for an **OAuth access token**. This bridges SAML-based enterprise authentication with OAuth 2.0 APIs.

**Use Case**: Enterprise user authenticates with SAML SSO (e.g., via Active Directory), then needs to access an OAuth 2.0 protected API.

## Why This Matters

**Problem**: You have:
- SAML for enterprise SSO (user authentication)
- OAuth 2.0 APIs (resource access)
- Need to connect them seamlessly

**Solution**: SAML Bearer Assertion Grant Flow

```
User ──SAML──► IdP ──SAML Assertion──► Client ──SAML Assertion──► OAuth Server ──Access Token──► API
```

## Flow Diagram

```
┌──────────┐                                    ┌─────────────┐
│   User   │                                    │  SAML IdP   │
└─────┬────┘                                    └──────┬──────┘
      │                                                │
      │  1. Access application                        │
      ├──────────────────────────────────────────────►│
      │                                                │
      │  2. Redirect to SAML IdP                      │
      │◄──────────────────────────────────────────────┤
      │                                                │
      │  3. Authenticate (username/password)          │
      ├──────────────────────────────────────────────►│
      │                                                │
      │  4. SAML Assertion (via POST)                 │
      │◄──────────────────────────────────────────────┤
      │                                                │
┌─────▼────┐                                    ┌──────▼──────┐
│  Client  │                                    │ OAuth Server│
│  (App)   │                                    │             │
└─────┬────┘                                    └──────┬──────┘
      │                                                │
      │  5. Exchange SAML Assertion for Access Token  │
      │     POST /token                                │
      │     grant_type=urn:ietf:params:oauth:         │
      │       grant-type:saml2-bearer                 │
      │     assertion=<base64(SAML)>                   │
      ├──────────────────────────────────────────────►│
      │                                                │
      │                                6. Validate SAML
      │                                - Verify signature
      │                                - Check expiration
      │                                - Validate audience
      │                                                │
      │  7. OAuth Access Token                        │
      │◄──────────────────────────────────────────────┤
      │  { access_token, token_type, expires_in }     │
      │                                                │
┌─────▼────┐                                    ┌──────▼──────┐
│  Client  │                                    │  API Server │
└─────┬────┘                                    └──────┬──────┘
      │                                                │
      │  8. Call API with Access Token                │
      │     Authorization: Bearer <access_token>       │
      ├──────────────────────────────────────────────►│
      │                                                │
      │  9. Protected Resource                        │
      │◄──────────────────────────────────────────────┤
      │                                                │
```

## Token Exchange Request

### Request

```http
POST /oauth/token HTTP/1.1
Host: oauth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Asaml2-bearer&
assertion=PHNhbWxwOlJlc3BvbnNlIHhtbG5zOnNhbWxwPSJ1cm46b2FzaXM6bmFtZXM6dGM6U...&
scope=read:profile%20write:data
```

**Parameters**:
- `grant_type` - **Required**: `urn:ietf:params:oauth:grant-type:saml2-bearer`
- `assertion` - **Required**: Base64-encoded SAML assertion
- `scope` - **Optional**: Requested OAuth scopes

### Response

```http
HTTP/1.1 200 OK
Content-Type: application/json

{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "read:profile write:data"
}
```

## SAML Assertion Requirements

The SAML assertion must meet specific criteria:

### Valid Assertion Structure

```xml
<saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_assertion123"
                IssueInstant="2024-01-15T10:30:00Z"
                Version="2.0">

  <!-- Issuer: The SAML IdP -->
  <saml:Issuer>https://idp.example.com</saml:Issuer>

  <!-- Signature: Must be valid -->
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    <!-- ... -->
  </ds:Signature>

  <!-- Subject: User identity -->
  <saml:Subject>
    <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">
      john.doe@example.com
    </saml:NameID>

    <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
      <saml:SubjectConfirmationData
          NotOnOrAfter="2024-01-15T10:35:00Z"
          Recipient="https://oauth.example.com/token"/>
    </saml:SubjectConfirmation>
  </saml:Subject>

  <!-- Conditions: Time validity and audience -->
  <saml:Conditions
      NotBefore="2024-01-15T10:30:00Z"
      NotOnOrAfter="2024-01-15T10:35:00Z">
    <saml:AudienceRestriction>
      <saml:Audience>https://oauth.example.com</saml:Audience>
    </saml:AudienceRestriction>
  </saml:Conditions>

  <!-- Authentication statement -->
  <saml:AuthnStatement AuthnInstant="2024-01-15T10:30:00Z">
    <saml:AuthnContext>
      <saml:AuthnContextClassRef>
        urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport
      </saml:AuthnContextClassRef>
    </saml:AuthnContext>
  </saml:AuthnStatement>

  <!-- Attributes: User information -->
  <saml:AttributeStatement>
    <saml:Attribute Name="email">
      <saml:AttributeValue>john.doe@example.com</saml:AttributeValue>
    </saml:Attribute>
    <saml:Attribute Name="groups">
      <saml:AttributeValue>Engineering</saml:AttributeValue>
      <saml:AttributeValue>Admins</saml:AttributeValue>
    </saml:Attribute>
  </saml:AttributeStatement>
</saml:Assertion>
```

### Critical Validations

The OAuth authorization server **MUST** validate:

1. **Signature** - Verify with IdP's public key
2. **Issuer** - Must be trusted SAML IdP
3. **Audience** - Must match OAuth server URL
4. **Time validity** - NotBefore ≤ now < NotOnOrAfter
5. **Subject Confirmation** - Method must be Bearer, Recipient must match token endpoint
6. **Single use** - Assertion ID must not be reused (prevent replay)

## Implementation

### OAuth Server (Authorization Server)

```javascript
const express = require('express');
const saml = require('samlify');
const jwt = require('jsonwebtoken');

const app = express();

// Configure SAML Identity Provider
const idp = saml.IdentityProvider({
  metadata: fs.readFileSync('./idp-metadata.xml')
});

// Configure SAML Service Provider (our OAuth server)
const sp = saml.ServiceProvider({
  entityID: 'https://oauth.example.com',
  assertionConsumerService: [{
    Binding: 'urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST',
    Location: 'https://oauth.example.com/token'
  }]
});

// Token endpoint
app.post('/token', express.urlencoded({ extended: true }), async (req, res) => {
  const { grant_type, assertion, scope } = req.body;

  // Check grant type
  if (grant_type !== 'urn:ietf:params:oauth:grant-type:saml2-bearer') {
    return res.status(400).json({
      error: 'unsupported_grant_type',
      error_description: 'Only SAML2 bearer assertion supported'
    });
  }

  if (!assertion) {
    return res.status(400).json({
      error: 'invalid_request',
      error_description: 'assertion parameter required'
    });
  }

  try {
    // Decode base64 assertion
    const samlAssertion = Buffer.from(assertion, 'base64').toString('utf-8');

    // Validate SAML assertion
    const parseResult = await sp.parseLoginResponse(idp, 'post', {
      body: { SAMLResponse: assertion }
    });

    // Extract user info from assertion
    const { extract } = parseResult;
    const userId = extract.nameID;
    const attributes = extract.attributes || {};

    // Check assertion hasn't been used before (replay prevention)
    const assertionId = extract.conditions.notOnOrAfter; // Simplified
    if (await isAssertionUsed(assertionId)) {
      return res.status(400).json({
        error: 'invalid_grant',
        error_description: 'Assertion has already been used'
      });
    }

    await markAssertionAsUsed(assertionId);

    // Create OAuth access token
    const accessToken = jwt.sign(
      {
        sub: userId,
        email: attributes.email,
        groups: attributes.groups,
        scope: scope || 'default'
      },
      privateKey,
      {
        algorithm: 'RS256',
        expiresIn: '1h',
        issuer: 'https://oauth.example.com',
        audience: 'api.example.com'
      }
    );

    res.json({
      access_token: accessToken,
      token_type: 'Bearer',
      expires_in: 3600,
      scope: scope || 'default'
    });

  } catch (error) {
    console.error('SAML validation error:', error);

    res.status(400).json({
      error: 'invalid_grant',
      error_description: 'Invalid SAML assertion'
    });
  }
});

// Replay attack prevention
const usedAssertions = new Set();

async function isAssertionUsed(assertionId) {
  return usedAssertions.has(assertionId);
}

async function markAssertionAsUsed(assertionId) {
  usedAssertions.add(assertionId);

  // Clean up after expiration (5 minutes)
  setTimeout(() => {
    usedAssertions.delete(assertionId);
  }, 5 * 60 * 1000);
}

app.listen(3000);
```

### Client Application

```javascript
// Client receives SAML assertion from IdP
async function exchangeSamlForOAuthToken(samlAssertion) {
  const response = await fetch('https://oauth.example.com/token', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams({
      grant_type: 'urn:ietf:params:oauth:grant-type:saml2-bearer',
      assertion: Buffer.from(samlAssertion).toString('base64'),
      scope: 'read:profile write:data'
    })
  });

  const data = await response.json();

  if (response.ok) {
    return data.access_token;
  } else {
    throw new Error(`Token exchange failed: ${data.error_description}`);
  }
}

// Use access token to call API
async function callAPI(accessToken) {
  const response = await fetch('https://api.example.com/data', {
    headers: {
      'Authorization': `Bearer ${accessToken}`
    }
  });

  return response.json();
}

// Complete flow
async function samlOAuthFlow(samlAssertion) {
  // Exchange SAML assertion for OAuth token
  const accessToken = await exchangeSamlForOAuthToken(samlAssertion);

  // Use token to access API
  const data = await callAPI(accessToken);

  return data;
}
```

## Common Use Cases

### 1. Enterprise API Access

**Scenario**: Enterprise employees use SAML SSO to access internal applications, which need to call OAuth-protected APIs.

```javascript
// After SAML authentication
app.post('/saml/acs', async (req, res) => {
  const { SAMLResponse } = req.body;

  // Validate SAML
  const user = await validateSAML(SAMLResponse);

  // Exchange for OAuth token
  const accessToken = await exchangeSamlForOAuthToken(SAMLResponse);

  // Store token in session
  req.session.userId = user.id;
  req.session.accessToken = accessToken;

  res.redirect('/dashboard');
});

// Use token to call APIs
app.get('/api/data', async (req, res) => {
  const data = await fetch('https://api.example.com/data', {
    headers: {
      'Authorization': `Bearer ${req.session.accessToken}`
    }
  });

  res.json(await data.json());
});
```

### 2. Mobile App with Enterprise SSO

**Scenario**: Mobile app needs to support enterprise SAML SSO and access OAuth APIs.

```javascript
// Mobile app opens browser for SAML authentication
// After SAML success, exchange assertion for OAuth token

// Deep link callback
app.get('/callback', async (req, res) => {
  const { SAMLResponse } = req.query;

  // Exchange SAML for OAuth token
  const accessToken = await exchangeSamlForOAuthToken(SAMLResponse);

  // Return to mobile app via deep link
  res.redirect(`myapp://auth?token=${accessToken}`);
});
```

### 3. Service-to-Service with SAML Trust

**Scenario**: Service A has SAML assertion, needs to call Service B's OAuth API.

```javascript
// Service A
async function callServiceB(samlAssertion, data) {
  // Get OAuth token
  const token = await exchangeSamlForOAuthToken(samlAssertion);

  // Call Service B
  const response = await fetch('https://service-b/api/process', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(data)
  });

  return response.json();
}
```

## Security Considerations

### 1. Validate Assertion Thoroughly

```javascript
function validateSAMLAssertion(assertion, idpCert) {
  // 1. Verify digital signature
  if (!verifySignature(assertion, idpCert)) {
    throw new Error('Invalid signature');
  }

  // 2. Check issuer is trusted
  if (!trustedIssuers.includes(assertion.Issuer)) {
    throw new Error('Untrusted issuer');
  }

  // 3. Validate time windows
  const now = new Date();
  if (now < assertion.Conditions.NotBefore ||
      now >= assertion.Conditions.NotOnOrAfter) {
    throw new Error('Assertion expired');
  }

  // 4. Check audience
  if (assertion.Conditions.Audience !== OAUTH_SERVER_URL) {
    throw new Error('Invalid audience');
  }

  // 5. Verify subject confirmation
  if (assertion.SubjectConfirmation.Method !== 'bearer') {
    throw new Error('Invalid confirmation method');
  }

  // 6. Check recipient
  if (assertion.SubjectConfirmationData.Recipient !== TOKEN_ENDPOINT) {
    throw new Error('Invalid recipient');
  }

  return true;
}
```

### 2. Prevent Replay Attacks

```javascript
// Use Redis for distributed replay prevention
const redis = require('redis');
const client = redis.createClient();

async function preventReplay(assertionId, expirationSeconds) {
  const key = `saml:assertion:${assertionId}`;

  // Check if already used
  const exists = await client.exists(key);
  if (exists) {
    throw new Error('Assertion already used');
  }

  // Mark as used with expiration
  await client.setex(key, expirationSeconds, 'used');
}
```

### 3. Short-Lived Assertions

```xml
<!-- SAML assertion should be short-lived -->
<saml:SubjectConfirmationData
    NotOnOrAfter="2024-01-15T10:35:00Z" <!-- Only 5 minutes -->
    Recipient="https://oauth.example.com/token"/>
```

### 4. Limit Token Scope

```javascript
// Don't grant all scopes from SAML assertion
function determineScopeFromSAML(samlAttributes, requestedScope) {
  const userGroups = samlAttributes.groups || [];

  const allowedScopes = {
    'Admins': ['read:all', 'write:all', 'delete:all'],
    'Engineering': ['read:all', 'write:data'],
    'Support': ['read:all']
  };

  const userAllowedScopes = userGroups
    .flatMap(group => allowedScopes[group] || []);

  const requestedScopes = requestedScope.split(' ');

  // Only grant scopes that are both requested and allowed
  const grantedScopes = requestedScopes
    .filter(s => userAllowedScopes.includes(s));

  return grantedScopes.join(' ');
}
```

## Comparison with Other Flows

| Feature | SAML Bearer | Authorization Code | Client Credentials |
|---------|-------------|-------------------|-------------------|
| **User context** | Yes (from SAML) | Yes | No |
| **Browser needed** | Yes (for SAML) | Yes | No |
| **Use case** | Enterprise SSO + API | Web/mobile apps | Service-to-service |
| **Complexity** | High | Medium | Low |
| **Enterprise adoption** | Very high | High | High |

## Interview Questions

### Basic
1. What is the SAML Bearer Assertion Grant Flow?
2. Why would you use SAML Bearer instead of Authorization Code flow?
3. What information does the SAML assertion contain?
4. What validations must be performed on a SAML assertion?

### Intermediate
5. How does the SAML Bearer flow bridge SAML and OAuth 2.0?
6. What is the format of the token request?
7. How do you prevent replay attacks with SAML assertions?
8. What is the role of the SubjectConfirmation element?

### Advanced
9. Design an authentication system that supports both SAML SSO and OAuth APIs
10. How would you implement SAML Bearer flow in a microservices architecture?
11. Compare SAML Bearer vs Token Exchange (RFC 8693) for enterprise scenarios
12. What are the security implications of using SAML assertions to obtain OAuth tokens?

## Further Reading

- [RFC 7522 - SAML 2.0 Profile for OAuth 2.0](https://tools.ietf.org/html/rfc7522)
- [SAML 2.0 Technical Overview](http://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html)
- [OAuth 2.0 Specifications](https://oauth.net/2/)
