# OAuth 2.0 - Authorization Framework

## Overview

OAuth 2.0 (RFC 6749) is an **authorization framework** that enables third-party applications to obtain limited access to an HTTP service without exposing user credentials. It's the foundation of modern API security and is widely used in microservices architectures.

**Key Point**: OAuth 2.0 is for **authorization**, not **authentication**. For authentication, use OpenID Connect (OIDC) which is built on top of OAuth 2.0.

## Core Concepts

### Roles

1. **Resource Owner** - The user who owns the data (e.g., you on Facebook)
2. **Resource Server** - The API server hosting protected resources (e.g., Facebook API)
3. **Client** - The application requesting access (e.g., a mobile app)
4. **Authorization Server** - Issues access tokens after authenticating the resource owner (e.g., OAuth provider like Auth0, Okta)

### Tokens

#### Access Token
- **Purpose**: Short-lived credential to access protected resources
- **Lifetime**: Typically 15 minutes to 1 hour
- **Format**: Can be opaque (random string) or JWT (self-contained)
- **Usage**: Sent with every API request in Authorization header

```http
GET /api/user/profile HTTP/1.1
Host: api.example.com
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

#### Refresh Token
- **Purpose**: Long-lived credential to obtain new access tokens
- **Lifetime**: Days to months (or never expires)
- **Format**: Opaque string stored securely
- **Usage**: Sent to token endpoint to get new access token

```http
POST /oauth/token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&
refresh_token=tGzv3JOkF0XG5Qx2TlKWIA&
client_id=my-app&
client_secret=secret123
```

## OAuth 2.0 Grant Types

### 1. Authorization Code Flow (Most Secure)

**Use Case**: Web applications, mobile apps, SPAs (with PKCE)

**Flow**:
```
┌──────────┐                                      ┌──────────────┐
│          │                                      │              │
│  User    │                                      │ Auth Server  │
│          │                                      │              │
└─────┬────┘                                      └──────┬───────┘
      │                                                  │
      │  1. Click "Login with Google"                   │
      ├────────────────────────────────────────────────►│
      │                                                  │
      │  2. Redirect to login page                      │
      │◄─────────────────────────────────────────────── │
      │                                                  │
      │  3. Enter credentials                           │
      ├─────────────────────────────────────────────────►│
      │                                                  │
      │  4. Redirect with authorization code            │
      │◄─────────────────────────────────────────────── │
      │  https://app.com/callback?code=ABC123           │
      │                                                  │
┌─────▼────┐                                      ┌──────▼───────┐
│          │  5. Exchange code for tokens         │              │
│  Client  ├─────────────────────────────────────►│ Auth Server  │
│  (App)   │  POST /token                         │              │
│          │  code=ABC123                         │              │
│          │  client_id=...                       │              │
│          │  client_secret=...                   │              │
│          │                                      │              │
│          │  6. Access + Refresh tokens          │              │
│          │◄─────────────────────────────────────│              │
│          │  { access_token, refresh_token }     │              │
└──────────┘                                      └──────────────┘
```

**Request Examples**:

Step 1 - Authorization Request:
```http
GET /oauth/authorize?
  response_type=code&
  client_id=my-app-123&
  redirect_uri=https://app.com/callback&
  scope=read:profile write:posts&
  state=xyz123
```

Step 5 - Token Exchange:
```http
POST /oauth/token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=ABC123&
redirect_uri=https://app.com/callback&
client_id=my-app-123&
client_secret=secret456
```

Response:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "tGzv3JOkF0XG5Qx2TlKWIA",
  "scope": "read:profile write:posts"
}
```

### 2. Authorization Code with PKCE (Proof Key for Code Exchange)

**Use Case**: Mobile apps, SPAs, any public client (cannot keep secrets)

**Why PKCE?** Prevents authorization code interception attacks in public clients.

**Flow**:
```
Client generates:
- code_verifier = random string (43-128 chars)
- code_challenge = BASE64URL(SHA256(code_verifier))
```

Step 1 - Authorization Request with PKCE:
```http
GET /oauth/authorize?
  response_type=code&
  client_id=mobile-app&
  redirect_uri=myapp://callback&
  scope=read:profile&
  state=xyz&
  code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&
  code_challenge_method=S256
```

Step 2 - Token Exchange with PKCE:
```http
POST /oauth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=authorization_code&
code=ABC123&
redirect_uri=myapp://callback&
client_id=mobile-app&
code_verifier=dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk
```

**Key Security**: Auth server verifies `SHA256(code_verifier) == code_challenge`

### 3. Client Credentials Flow

**Use Case**: Service-to-service (machine-to-machine) communication, no user context

**Flow**:
```
┌──────────────┐                    ┌──────────────┐
│              │  1. Request token  │              │
│  Service A   ├───────────────────►│ Auth Server  │
│              │  client credentials│              │
│              │                    │              │
│              │  2. Access token   │              │
│              │◄───────────────────┤              │
└──────┬───────┘                    └──────────────┘
       │
       │  3. Call Service B API
       │
┌──────▼───────┐
│              │
│  Service B   │
│  (API)       │
└──────────────┘
```

**Request**:
```http
POST /oauth/token HTTP/1.1
Host: auth.example.com
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

grant_type=client_credentials&
scope=api:read api:write
```

**Response**:
```json
{
  "access_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "token_type": "Bearer",
  "expires_in": 3600,
  "scope": "api:read api:write"
}
```

**Common Use**:
```javascript
// Service A calls Service B
const token = await getClientCredentialsToken();
const response = await fetch('https://service-b/api/data', {
  headers: {
    'Authorization': `Bearer ${token}`
  }
});
```

### 4. Resource Owner Password Credentials (ROPC) - Legacy/Deprecated

**Use Case**: Trusted first-party apps only (NOT recommended)

**Warning**: Avoid this! Only use when other flows are impossible.

**Request**:
```http
POST /oauth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=password&
username=user@example.com&
password=secretpassword&
client_id=trusted-app&
client_secret=secret123&
scope=read:profile
```

**Why Avoid**:
- Client sees user password (violates OAuth principle)
- No multi-factor authentication support
- Can't support SSO properly
- Security risk if client is compromised

### 5. Implicit Flow - DEPRECATED

**Former Use**: Single-page apps (SPAs)

**Status**: Deprecated - Use Authorization Code + PKCE instead

**Why Deprecated**:
- Access token exposed in URL fragment
- No refresh token support
- Vulnerable to token leakage

## Token Management

### Token Refresh Flow

```http
POST /oauth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=refresh_token&
refresh_token=tGzv3JOkF0XG5Qx2TlKWIA&
client_id=my-app&
client_secret=secret123
```

Response:
```json
{
  "access_token": "new_access_token_here",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "new_refresh_token_here"
}
```

### Token Revocation

```http
POST /oauth/revoke HTTP/1.1
Content-Type: application/x-www-form-urlencoded

token=access_token_or_refresh_token&
token_type_hint=refresh_token&
client_id=my-app&
client_secret=secret123
```

### Token Introspection

```http
POST /oauth/introspect HTTP/1.1
Content-Type: application/x-www-form-urlencoded
Authorization: Basic base64(client_id:client_secret)

token=eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...
```

Response:
```json
{
  "active": true,
  "scope": "read:profile write:posts",
  "client_id": "my-app-123",
  "username": "user@example.com",
  "exp": 1672531200,
  "iat": 1672527600,
  "sub": "user-12345"
}
```

## Scopes and Permissions

### Scope Design

**Good Scope Design**:
```
read:profile      - Read user profile
write:profile     - Update user profile
read:posts        - Read posts
write:posts       - Create/edit posts
delete:posts      - Delete posts
admin:users       - Manage all users
```

**Requesting Scopes**:
```http
GET /oauth/authorize?
  ...&
  scope=read:profile write:posts read:analytics
```

**Validating Scopes in API**:
```javascript
function requireScope(...requiredScopes) {
  return (req, res, next) => {
    const tokenScopes = req.token.scope.split(' ');
    const hasScope = requiredScopes.every(s => tokenScopes.includes(s));

    if (!hasScope) {
      return res.status(403).json({ error: 'insufficient_scope' });
    }
    next();
  };
}

app.delete('/posts/:id',
  requireScope('delete:posts'),
  deletePost
);
```

## Security Best Practices

### 1. Use HTTPS Everywhere
```
❌ http://auth.example.com/oauth/authorize
✅ https://auth.example.com/oauth/authorize
```

### 2. Validate Redirect URIs
```javascript
// Authorization server must validate
const allowedRedirects = [
  'https://app.example.com/callback',
  'myapp://callback'
];

if (!allowedRedirects.includes(requestedRedirect)) {
  throw new Error('Invalid redirect_uri');
}
```

### 3. Use State Parameter (CSRF Protection)
```javascript
// Client generates random state
const state = generateRandomString();
sessionStorage.setItem('oauth_state', state);

// Include in authorization request
const authUrl = `${authServer}/authorize?...&state=${state}`;

// Validate on callback
const callbackState = new URLSearchParams(window.location.search).get('state');
if (callbackState !== sessionStorage.getItem('oauth_state')) {
  throw new Error('CSRF attack detected!');
}
```

### 4. Short-Lived Access Tokens
```
✅ Access token: 15-60 minutes
✅ Refresh token: Days/months
❌ Access token: Never expires
```

### 5. Secure Token Storage

**Browser**:
```javascript
// ❌ Don't store in localStorage (XSS vulnerable)
localStorage.setItem('access_token', token);

// ✅ Use httpOnly cookie
// Set by server with Set-Cookie header
Set-Cookie: access_token=...; HttpOnly; Secure; SameSite=Strict
```

**Mobile**:
```kotlin
// ✅ Use secure storage
// iOS: Keychain
// Android: EncryptedSharedPreferences
```

### 6. Token Rotation
```javascript
// Rotate refresh tokens on each use
app.post('/oauth/token', async (req, res) => {
  if (req.body.grant_type === 'refresh_token') {
    // Invalidate old refresh token
    await revokeToken(req.body.refresh_token);

    // Issue new access + refresh tokens
    const tokens = await generateNewTokens();
    res.json(tokens);
  }
});
```

## Microservices Implementation Patterns

### Pattern 1: API Gateway with Token Validation

```
┌─────────┐         ┌─────────────┐         ┌───────────┐
│ Client  │────────►│ API Gateway │────────►│ Service A │
└─────────┘  token  └──────┬──────┘  claims └───────────┘
                            │
                            │ validate token
                            ▼
                    ┌──────────────┐
                    │ Auth Server  │
                    │ (Introspect) │
                    └──────────────┘
```

**API Gateway Code**:
```javascript
const express = require('express');
const jwt = require('jsonwebtoken');

const validateToken = async (req, res, next) => {
  const token = req.headers.authorization?.split(' ')[1];

  if (!token) {
    return res.status(401).json({ error: 'missing_token' });
  }

  try {
    // Option 1: Validate JWT locally (if using JWTs)
    const decoded = jwt.verify(token, publicKey, {
      algorithms: ['RS256'],
      issuer: 'https://auth.example.com'
    });

    // Option 2: Introspect with auth server (opaque tokens)
    // const result = await introspectToken(token);

    req.user = decoded;
    next();
  } catch (err) {
    res.status(401).json({ error: 'invalid_token' });
  }
};

app.use('/api', validateToken, proxyToServices);
```

### Pattern 2: Service-to-Service with Client Credentials

```javascript
// Service A calls Service B
class ServiceBClient {
  constructor() {
    this.token = null;
    this.tokenExpiry = null;
  }

  async getToken() {
    // Return cached token if valid
    if (this.token && Date.now() < this.tokenExpiry) {
      return this.token;
    }

    // Get new token
    const response = await fetch('https://auth.example.com/oauth/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
        'Authorization': `Basic ${base64(clientId + ':' + clientSecret)}`
      },
      body: 'grant_type=client_credentials&scope=service-b:read'
    });

    const data = await response.json();
    this.token = data.access_token;
    this.tokenExpiry = Date.now() + (data.expires_in * 1000) - 60000; // 1 min buffer

    return this.token;
  }

  async callServiceB(endpoint) {
    const token = await this.getToken();
    return fetch(`https://service-b/api/${endpoint}`, {
      headers: {
        'Authorization': `Bearer ${token}`
      }
    });
  }
}
```

### Pattern 3: Token Exchange (RFC 8693)

Used when Service A needs to call Service B on behalf of a user.

```http
POST /oauth/token HTTP/1.1
Content-Type: application/x-www-form-urlencoded

grant_type=urn:ietf:params:oauth:grant-type:token-exchange&
subject_token=user_access_token&
subject_token_type=urn:ietf:params:oauth:token-type:access_token&
audience=service-b&
scope=service-b:read
```

## Common Pitfalls and Solutions

### Pitfall 1: Storing Tokens Insecurely
```javascript
// ❌ DON'T: Store in localStorage
localStorage.setItem('token', accessToken);

// ✅ DO: Use httpOnly cookies or secure storage
// Server sets: Set-Cookie: token=...; HttpOnly; Secure
```

### Pitfall 2: Not Validating Token Signature
```javascript
// ❌ DON'T: Just decode without verification
const payload = JSON.parse(base64Decode(token.split('.')[1]));

// ✅ DO: Verify signature
const payload = jwt.verify(token, publicKey, { algorithms: ['RS256'] });
```

### Pitfall 3: Using Wrong Grant Type
```javascript
// ❌ DON'T: Use ROPC for third-party apps
// ❌ DON'T: Use Implicit flow (deprecated)

// ✅ DO: Use Authorization Code + PKCE for SPAs/mobile
// ✅ DO: Use Client Credentials for service-to-service
```

### Pitfall 4: Not Implementing Token Refresh
```javascript
// ✅ DO: Implement automatic refresh
async function fetchWithAuth(url, options = {}) {
  let token = getAccessToken();

  // Try request
  let response = await fetch(url, {
    ...options,
    headers: {
      ...options.headers,
      'Authorization': `Bearer ${token}`
    }
  });

  // If 401, refresh and retry
  if (response.status === 401) {
    token = await refreshAccessToken();
    response = await fetch(url, {
      ...options,
      headers: {
        ...options.headers,
        'Authorization': `Bearer ${token}`
      }
    });
  }

  return response;
}
```

## Real-World Examples

### Example 1: Mobile App Login with Google

```kotlin
// Android - Using AppAuth library
val authRequest = AuthorizationRequest.Builder(
    serviceConfig,
    clientId = "your-app.apps.googleusercontent.com",
    responseType = ResponseTypeValues.CODE,
    redirectUri = Uri.parse("com.yourapp:/oauth2callback")
)
.setScopes("profile", "email")
.setCodeVerifier(codeVerifier) // PKCE
.build()

authService.performAuthorizationRequest(authRequest) { response, error ->
    if (response != null) {
        // Exchange code for tokens
        exchangeCodeForTokens(response.authorizationCode)
    }
}
```

### Example 2: Microservice API Gateway

```javascript
// Express API Gateway
const express = require('express');
const { auth } = require('express-oauth2-jwt-bearer');

const app = express();

// Validate JWT tokens
const checkJwt = auth({
  issuerBaseURL: 'https://auth.example.com',
  audience: 'https://api.example.com',
  tokenSigningAlg: 'RS256'
});

// Protect all routes
app.use('/api', checkJwt);

// Scope-based authorization
const requireScope = (scope) => (req, res, next) => {
  if (req.auth.scope.includes(scope)) {
    next();
  } else {
    res.status(403).json({ error: 'Forbidden' });
  }
};

app.get('/api/admin', requireScope('admin'), (req, res) => {
  res.json({ message: 'Admin data' });
});
```

### Example 3: SPA with Auth0

```javascript
// React SPA
import { Auth0Provider, useAuth0 } from '@auth0/auth0-react';

// Wrap app
<Auth0Provider
  domain="your-domain.auth0.com"
  clientId="your-client-id"
  redirectUri={window.location.origin}
  audience="https://api.example.com"
  scope="read:profile write:posts"
>
  <App />
</Auth0Provider>

// Use in component
function Profile() {
  const { user, isAuthenticated, getAccessTokenSilently } = useAuth0();

  const callAPI = async () => {
    const token = await getAccessTokenSilently();
    const response = await fetch('https://api.example.com/profile', {
      headers: {
        Authorization: `Bearer ${token}`
      }
    });
    return response.json();
  };

  return isAuthenticated ? <div>Hello {user.name}</div> : <div>Please log in</div>;
}
```

## OAuth 2.1 (Upcoming)

OAuth 2.1 consolidates best practices:

1. **PKCE required** for all authorization code flows
2. **Implicit flow removed** entirely
3. **Refresh token rotation** recommended
4. **Redirect URI exact matching** required
5. **Bearer token usage** clarified

## Tools and Libraries

### Authorization Servers
- **Auth0** - Managed service
- **Okta** - Enterprise identity platform
- **Keycloak** - Open-source (Red Hat)
- **ORY Hydra** - Open-source, cloud-native
- **Azure AD** - Microsoft identity platform
- **AWS Cognito** - AWS managed service

### Client Libraries
- **JavaScript**: `oauth4webapi`, `@auth0/auth0-spa-js`
- **Python**: `authlib`, `oauthlib`
- **Java**: `Spring Security OAuth2`, `pac4j`
- **Go**: `golang.org/x/oauth2`
- **Node.js**: `passport-oauth2`, `simple-oauth2`

## Interview Questions

### Basic
1. What is OAuth 2.0 and what problem does it solve?
2. Explain the difference between authentication and authorization
3. What are the four main roles in OAuth 2.0?
4. What is the difference between access token and refresh token?

### Intermediate
5. Which OAuth grant type should you use for a mobile app? Why?
6. Explain PKCE and why it's important
7. How would you implement OAuth 2.0 in a microservices architecture?
8. What are scopes and how do you use them?

### Advanced
9. How do you handle token refresh in a distributed system?
10. Explain token introspection vs JWT validation
11. Design an OAuth flow for a mobile app calling multiple microservices
12. How would you implement token revocation across services?

## Further Reading

- [RFC 6749 - OAuth 2.0 Authorization Framework](https://tools.ietf.org/html/rfc6749)
- [RFC 7636 - PKCE](https://tools.ietf.org/html/rfc7636)
- [RFC 8693 - Token Exchange](https://tools.ietf.org/html/rfc8693)
- [OAuth 2.0 Security Best Current Practice](https://tools.ietf.org/html/draft-ietf-oauth-security-topics)
- [oauth.net](https://oauth.net/) - Official OAuth website
