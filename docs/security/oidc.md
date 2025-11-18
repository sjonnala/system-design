# OpenID Connect (OIDC) - Authentication Layer

## Overview

OpenID Connect (OIDC) is an **authentication protocol** built on top of OAuth 2.0. While OAuth 2.0 handles authorization, OIDC adds an identity layer that allows clients to verify the identity of users and obtain basic profile information.

**Key Difference**:
- **OAuth 2.0**: "What can you access?" (Authorization)
- **OIDC**: "Who are you?" (Authentication)

OIDC is the modern standard for Single Sign-On (SSO) and is used by Google, Microsoft, Okta, Auth0, and most identity providers.

## Why OIDC Exists

### The OAuth 2.0 Problem

OAuth 2.0 was designed for authorization, not authentication:

```javascript
// With OAuth 2.0 alone
const accessToken = await getOAuthToken(); // Can access API
// But who is the user? We don't know!

// With OIDC
const { accessToken, idToken } = await getOIDCTokens();
const userInfo = decodeIdToken(idToken);
console.log(`User: ${userInfo.email}`); // Now we know who they are!
```

### Pre-OIDC Authentication (Bad Patterns)

```javascript
// ❌ BAD: Using OAuth access token to "authenticate"
const accessToken = getAccessToken();
const user = await fetch('https://api.example.com/me', {
  headers: { Authorization: `Bearer ${accessToken}` }
});
// Problems:
// - Not standardized
// - Extra API call
// - Access token not designed for identity
```

```javascript
// ✅ GOOD: Using OIDC ID token
const idToken = getIdToken();
const user = jwt.verify(idToken, publicKey);
// - Standardized claims
// - No extra API call needed
// - Designed for identity
```

## Core Concepts

### The Three Tokens

#### 1. ID Token (The Star of OIDC)

**Purpose**: Contains user identity information

**Format**: Always a JWT (JSON Web Token)

**Example**:
```json
{
  "iss": "https://auth.example.com",
  "sub": "user-12345",
  "aud": "my-app-client-id",
  "exp": 1672531200,
  "iat": 1672527600,
  "nonce": "random-nonce-123",
  "name": "John Doe",
  "email": "john@example.com",
  "email_verified": true,
  "picture": "https://example.com/photo.jpg"
}
```

**Key Claims**:
- `iss` - Issuer (who created the token)
- `sub` - Subject (unique user ID)
- `aud` - Audience (your app's client ID)
- `exp` - Expiration time
- `iat` - Issued at time
- `nonce` - Replay attack prevention

**Validation**:
```javascript
const jwt = require('jsonwebtoken');

function validateIdToken(idToken, nonce) {
  try {
    const decoded = jwt.verify(idToken, publicKey, {
      algorithms: ['RS256'],
      issuer: 'https://auth.example.com',
      audience: 'my-app-client-id'
    });

    // Verify nonce (CSRF protection)
    if (decoded.nonce !== nonce) {
      throw new Error('Invalid nonce');
    }

    // Verify expiration
    if (decoded.exp < Date.now() / 1000) {
      throw new Error('Token expired');
    }

    return decoded; // User info
  } catch (error) {
    throw new Error('Invalid ID token');
  }
}
```

#### 2. Access Token (OAuth 2.0)

Same as in OAuth 2.0 - used to access APIs.

#### 3. Refresh Token (OAuth 2.0)

Same as in OAuth 2.0 - used to get new access/ID tokens.

### OIDC Flows

OIDC inherits OAuth 2.0 grant types and adds authentication:

#### 1. Authorization Code Flow (Recommended)

```
┌──────────┐                                           ┌──────────────┐
│  User    │                                           │ Auth Server  │
└─────┬────┘                                           └──────┬───────┘
      │                                                       │
      │  1. GET /authorize?response_type=code&               │
      │     scope=openid profile email                       │
      ├──────────────────────────────────────────────────────►│
      │                                                       │
      │  2. User logs in                                     │
      ├──────────────────────────────────────────────────────►│
      │                                                       │
      │  3. Redirect with code                               │
      │◄──────────────────────────────────────────────────── │
      │                                                       │
┌─────▼────┐                                           ┌──────▼───────┐
│  Client  │  4. POST /token                           │ Auth Server  │
│          │     grant_type=authorization_code         │              │
│          ├──────────────────────────────────────────►│              │
│          │     code=ABC123                           │              │
│          │                                           │              │
│          │  5. Returns:                              │              │
│          │     - id_token (user identity)            │              │
│          │     - access_token (API access)           │              │
│          │     - refresh_token                       │              │
│          │◄──────────────────────────────────────────┤              │
└──────────┘                                           └──────────────┘
```

**Request**:
```http
GET /authorize?
  response_type=code&
  client_id=my-app&
  redirect_uri=https://app.com/callback&
  scope=openid profile email&
  state=xyz123&
  nonce=random456
```

**Response**:
```json
{
  "id_token": "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9...",
  "access_token": "SlAV32hkKG",
  "token_type": "Bearer",
  "expires_in": 3600,
  "refresh_token": "8xLOxBtZp8"
}
```

#### 2. Implicit Flow (Deprecated)

**Status**: Deprecated - Use Authorization Code + PKCE instead

#### 3. Hybrid Flow

Combines Authorization Code and Implicit flows. Returns some tokens immediately and others via token endpoint.

**Use Case**: Rare - only when you need tokens at different stages

```http
GET /authorize?
  response_type=code id_token&  // Both code and id_token
  ...
```

### Scopes in OIDC

OIDC defines standard scopes:

#### Required: `openid`

Triggers OIDC mode. Without this, it's just OAuth 2.0.

```http
scope=openid  // Returns id_token
```

#### Standard Scopes

```
openid          - Enables OIDC, returns sub claim
profile         - name, family_name, given_name, picture, etc.
email           - email, email_verified
address         - formatted address, street_address, locality, etc.
phone           - phone_number, phone_number_verified
offline_access  - Returns refresh token
```

**Example**:
```http
GET /authorize?
  scope=openid profile email&
  ...
```

**Resulting ID Token**:
```json
{
  "sub": "user-123",
  "name": "John Doe",
  "given_name": "John",
  "family_name": "Doe",
  "picture": "https://example.com/photo.jpg",
  "email": "john@example.com",
  "email_verified": true
}
```

### UserInfo Endpoint

Alternative to getting user data from ID token:

```http
GET /userinfo HTTP/1.1
Host: auth.example.com
Authorization: Bearer access_token_here
```

**Response**:
```json
{
  "sub": "user-123",
  "name": "John Doe",
  "email": "john@example.com",
  "email_verified": true,
  "picture": "https://example.com/photo.jpg",
  "updated_at": 1672527600
}
```

**When to use**:
- ID token too large (many claims)
- Need fresh user data
- Claims not in ID token

## Discovery and Metadata

### OpenID Provider Configuration

Every OIDC provider exposes a discovery endpoint:

```http
GET /.well-known/openid-configuration
```

**Example Response**:
```json
{
  "issuer": "https://auth.example.com",
  "authorization_endpoint": "https://auth.example.com/authorize",
  "token_endpoint": "https://auth.example.com/token",
  "userinfo_endpoint": "https://auth.example.com/userinfo",
  "jwks_uri": "https://auth.example.com/.well-known/jwks.json",
  "response_types_supported": ["code", "token", "id_token"],
  "subject_types_supported": ["public"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "scopes_supported": ["openid", "profile", "email"],
  "claims_supported": ["sub", "name", "email", "picture"]
}
```

**Usage**:
```javascript
async function configureOIDC() {
  const response = await fetch(
    'https://auth.example.com/.well-known/openid-configuration'
  );
  const config = await response.json();

  return {
    authorizationEndpoint: config.authorization_endpoint,
    tokenEndpoint: config.token_endpoint,
    userinfoEndpoint: config.userinfo_endpoint,
    jwksUri: config.jwks_uri
  };
}
```

### JSON Web Key Set (JWKS)

Public keys for verifying ID tokens:

```http
GET /.well-known/jwks.json
```

**Response**:
```json
{
  "keys": [
    {
      "kty": "RSA",
      "use": "sig",
      "kid": "key-2024-01",
      "alg": "RS256",
      "n": "0vx7agoebGcQSuuPiLJXZpt...",
      "e": "AQAB"
    }
  ]
}
```

**Usage**:
```javascript
const jwksClient = require('jwks-rsa');

const client = jwksClient({
  jwksUri: 'https://auth.example.com/.well-known/jwks.json'
});

function getKey(header, callback) {
  client.getSigningKey(header.kid, (err, key) => {
    const signingKey = key.publicKey || key.rsaPublicKey;
    callback(null, signingKey);
  });
}

jwt.verify(idToken, getKey, options, (err, decoded) => {
  console.log(decoded); // Verified user info
});
```

## Authentication Flows in Detail

### Complete Login Flow

```javascript
// Step 1: Generate nonce and state
const state = generateRandomString();
const nonce = generateRandomString();
sessionStorage.setItem('oidc_state', state);
sessionStorage.setItem('oidc_nonce', nonce);

// Step 2: Redirect to authorization endpoint
const authUrl = new URL('https://auth.example.com/authorize');
authUrl.searchParams.set('response_type', 'code');
authUrl.searchParams.set('client_id', 'my-app');
authUrl.searchParams.set('redirect_uri', 'https://app.com/callback');
authUrl.searchParams.set('scope', 'openid profile email');
authUrl.searchParams.set('state', state);
authUrl.searchParams.set('nonce', nonce);

window.location.href = authUrl.toString();

// Step 3: Handle callback
const urlParams = new URLSearchParams(window.location.search);
const code = urlParams.get('code');
const returnedState = urlParams.get('state');

// Verify state
if (returnedState !== sessionStorage.getItem('oidc_state')) {
  throw new Error('CSRF attack detected');
}

// Step 4: Exchange code for tokens (server-side)
const tokenResponse = await fetch('https://auth.example.com/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'authorization_code',
    code: code,
    redirect_uri: 'https://app.com/callback',
    client_id: 'my-app',
    client_secret: 'secret123'
  })
});

const tokens = await tokenResponse.json();
// tokens = { id_token, access_token, refresh_token }

// Step 5: Validate ID token
const idTokenPayload = validateIdToken(
  tokens.id_token,
  sessionStorage.getItem('oidc_nonce')
);

console.log('User logged in:', idTokenPayload.email);
```

### Single Sign-On (SSO) Flow

```
User logs in to App A
      │
      ├─ Session created at Auth Server
      │
User navigates to App B
      │
      ├─ App B redirects to Auth Server
      │
      ├─ Auth Server sees existing session
      │
      └─ Returns tokens for App B (no login required!)
```

**Implementation**:
```javascript
// App A login
GET /authorize?
  client_id=app-a&
  scope=openid profile&
  ...

// Auth server creates session cookie
Set-Cookie: session=abc123; Domain=auth.example.com

// App B login (user already logged in)
GET /authorize?
  client_id=app-b&
  scope=openid profile&
  ...

// Auth server sees session cookie, immediately redirects with code
// No login required!
```

### Logout

#### Front-Channel Logout

```http
GET /logout?
  post_logout_redirect_uri=https://app.com&
  id_token_hint=eyJhbGciOiJSUzI1NiIs...
```

#### RP-Initiated Logout (Recommended)

```javascript
function logout() {
  const idToken = getStoredIdToken();

  // Clear local session
  clearLocalStorage();
  clearSessionStorage();

  // Redirect to OIDC logout endpoint
  const logoutUrl = new URL('https://auth.example.com/logout');
  logoutUrl.searchParams.set('id_token_hint', idToken);
  logoutUrl.searchParams.set(
    'post_logout_redirect_uri',
    'https://app.com/logged-out'
  );

  window.location.href = logoutUrl.toString();
}
```

#### Back-Channel Logout

Auth server notifies all apps when user logs out:

```http
POST /backchannel-logout HTTP/1.1
Host: app-a.example.com
Content-Type: application/x-www-form-urlencoded

logout_token=eyJhbGciOiJSUzI1NiIs...
```

App receives logout token and terminates local session.

## OIDC in Microservices

### Pattern 1: API Gateway Authentication

```
┌──────────┐         ┌─────────────┐         ┌───────────┐
│  User    │────────►│ API Gateway │────────►│ Service A │
│  (SPA)   │ id_token└──────┬──────┘ claims  └───────────┘
└──────────┘                │
                            │ validate
                            ▼
                    ┌──────────────┐
                    │ OIDC Provider│
                    │  (Auth0)     │
                    └──────────────┘
```

**API Gateway**:
```javascript
const express = require('express');
const { auth } = require('express-openid-connect');

const app = express();

// OIDC middleware
app.use(auth({
  authRequired: false,
  auth0Logout: true,
  issuerBaseURL: 'https://your-tenant.auth0.com',
  baseURL: 'https://app.example.com',
  clientID: 'your-client-id',
  secret: 'long-random-secret',
  idpLogout: true
}));

// Protected route
app.get('/api/profile', requiresAuth(), (req, res) => {
  res.json({
    user: req.oidc.user  // User from ID token
  });
});

// Forward user info to services
app.use('/api', (req, res, next) => {
  if (req.oidc.user) {
    req.headers['X-User-Id'] = req.oidc.user.sub;
    req.headers['X-User-Email'] = req.oidc.user.email;
  }
  next();
}, proxyToServices);
```

### Pattern 2: BFF (Backend for Frontend)

```
┌──────────┐         ┌──────────┐         ┌───────────────┐
│   SPA    │────────►│   BFF    │────────►│ OIDC Provider │
└──────────┘ cookie  └─────┬────┘  OIDC   └───────────────┘
                           │
                           │ service auth
                           ▼
                    ┌─────────────┐
                    │  Services   │
                    └─────────────┘
```

**BFF handles all OIDC complexity**:
```javascript
// SPA only deals with session cookie
app.get('/api/login', (req, res) => {
  res.oidc.login({ returnTo: '/dashboard' });
});

// BFF handles token management
app.get('/api/data', requiresAuth(), async (req, res) => {
  const accessToken = req.oidc.accessToken;

  // Call backend service
  const data = await fetch('https://service-a/data', {
    headers: {
      Authorization: `Bearer ${accessToken}`
    }
  });

  res.json(await data.json());
});
```

### Pattern 3: Token Exchange for Service-to-Service

```javascript
// Service A received user's access token
// Needs to call Service B on behalf of user

const response = await fetch('https://auth.example.com/token', {
  method: 'POST',
  headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
  body: new URLSearchParams({
    grant_type: 'urn:ietf:params:oauth:grant-type:token-exchange',
    subject_token: userAccessToken,
    subject_token_type: 'urn:ietf:params:oauth:token-type:access_token',
    audience: 'service-b',
    scope: 'service-b:read'
  })
});

const { access_token } = await response.json();

// Use exchanged token to call Service B
await fetch('https://service-b/api/data', {
  headers: { Authorization: `Bearer ${access_token}` }
});
```

## Security Best Practices

### 1. Always Validate ID Token

```javascript
// ❌ DON'T: Trust without validation
const payload = JSON.parse(atob(idToken.split('.')[1]));

// ✅ DO: Verify signature, issuer, audience, expiration
const payload = jwt.verify(idToken, publicKey, {
  algorithms: ['RS256'],
  issuer: expectedIssuer,
  audience: clientId
});
```

### 2. Use Nonce for Replay Protection

```javascript
// Client
const nonce = generateRandomString();
sessionStorage.setItem('nonce', nonce);

const authUrl = `.../authorize?...&nonce=${nonce}`;

// On callback
const idToken = jwt.verify(token, publicKey);
if (idToken.nonce !== sessionStorage.getItem('nonce')) {
  throw new Error('Replay attack detected');
}
```

### 3. Validate State Parameter

```javascript
// Prevent CSRF attacks
const state = generateRandomString();
sessionStorage.setItem('state', state);

// On callback
if (callbackState !== sessionStorage.getItem('state')) {
  throw new Error('CSRF attack detected');
}
```

### 4. Use Short-Lived ID Tokens

```
✅ ID token lifetime: 5-15 minutes
✅ Access token: 15-60 minutes
✅ Refresh token: Use rotation
```

### 5. Secure Token Storage

**Browser**:
```javascript
// ✅ For session: Use httpOnly cookies
Set-Cookie: session=...; HttpOnly; Secure; SameSite=Strict

// ⚠️ If must use storage: Use sessionStorage, not localStorage
sessionStorage.setItem('id_token', token);
```

**Mobile**:
```swift
// iOS - Use Keychain
KeychainAccess.set(idToken, forKey: "id_token")

// Android - Use EncryptedSharedPreferences
```

### 6. Implement Proper Logout

```javascript
// Clear all tokens
async function logout() {
  const idToken = getIdToken();

  // 1. Clear local session
  clearAllTokens();

  // 2. Revoke tokens
  await revokeTokens();

  // 3. OIDC logout (clears SSO session)
  const logoutUrl = `${issuer}/logout?` +
    `id_token_hint=${idToken}&` +
    `post_logout_redirect_uri=${window.location.origin}`;

  window.location.href = logoutUrl;
}
```

## Common Pitfalls

### Pitfall 1: Using Access Token for Identity

```javascript
// ❌ DON'T: Use access token to identify user
const accessToken = getAccessToken();
const payload = jwt.decode(accessToken); // Might not have user info!

// ✅ DO: Use ID token
const idToken = getIdToken();
const user = jwt.verify(idToken, publicKey);
```

### Pitfall 2: Forgetting `openid` Scope

```javascript
// ❌ DON'T: Missing openid scope
scope=profile email  // This is just OAuth 2.0!

// ✅ DO: Include openid
scope=openid profile email  // Now it's OIDC
```

### Pitfall 3: Not Validating Nonce

```javascript
// ❌ DON'T: Skip nonce validation
const user = jwt.verify(idToken, publicKey);

// ✅ DO: Validate nonce
const user = jwt.verify(idToken, publicKey);
if (user.nonce !== expectedNonce) {
  throw new Error('Invalid nonce');
}
```

### Pitfall 4: Storing Tokens Insecurely

```javascript
// ❌ DON'T: LocalStorage (XSS vulnerable)
localStorage.setItem('id_token', token);

// ✅ DO: httpOnly cookie or sessionStorage
// Or use BFF pattern
```

## Real-World Examples

### Example 1: React SPA with Auth0

```javascript
import { Auth0Provider, useAuth0 } from '@auth0/auth0-react';

// App.js
<Auth0Provider
  domain="your-tenant.auth0.com"
  clientId="your-client-id"
  redirectUri={window.location.origin}
  audience="https://api.example.com"
  scope="openid profile email"
>
  <App />
</Auth0Provider>

// Profile component
function Profile() {
  const { user, isAuthenticated, loginWithRedirect, logout } = useAuth0();

  if (!isAuthenticated) {
    return <button onClick={loginWithRedirect}>Log In</button>;
  }

  return (
    <div>
      <img src={user.picture} alt={user.name} />
      <h2>{user.name}</h2>
      <p>{user.email}</p>
      <button onClick={() => logout({ returnTo: window.location.origin })}>
        Log Out
      </button>
    </div>
  );
}
```

### Example 2: Node.js Backend with Passport

```javascript
const passport = require('passport');
const { Strategy } = require('passport-openidconnect');

passport.use('oidc', new Strategy({
    issuer: 'https://auth.example.com',
    authorizationURL: 'https://auth.example.com/authorize',
    tokenURL: 'https://auth.example.com/token',
    userInfoURL: 'https://auth.example.com/userinfo',
    clientID: process.env.CLIENT_ID,
    clientSecret: process.env.CLIENT_SECRET,
    callbackURL: 'https://app.com/callback',
    scope: 'openid profile email'
  },
  (issuer, profile, done) => {
    return done(null, profile);
  }
));

app.get('/login', passport.authenticate('oidc'));

app.get('/callback',
  passport.authenticate('oidc', { failureRedirect: '/login' }),
  (req, res) => {
    res.redirect('/dashboard');
  }
);
```

### Example 3: Spring Boot Java

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/", "/login").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo
                    .oidcUserService(oidcUserService())
                )
            );
        return http.build();
    }

    @Bean
    public OidcUserService oidcUserService() {
        return new OidcUserService();
    }
}

// application.yml
spring:
  security:
    oauth2:
      client:
        registration:
          auth0:
            client-id: ${CLIENT_ID}
            client-secret: ${CLIENT_SECRET}
            scope: openid,profile,email
        provider:
          auth0:
            issuer-uri: https://your-tenant.auth0.com/
```

## OIDC vs SAML

| Feature | OIDC | SAML 2.0 |
|---------|------|----------|
| **Format** | JSON (JWT) | XML |
| **Protocol** | Built on OAuth 2.0 | Standalone |
| **Use Case** | Modern apps, mobile, APIs | Enterprise SSO |
| **Complexity** | Simple | Complex |
| **Token Size** | Small (JWT) | Large (XML) |
| **Mobile Support** | Excellent | Poor |
| **Browser Support** | Excellent | Excellent |
| **Adoption** | Growing (modern) | Established (enterprise) |

## Tools and Libraries

### OIDC Providers
- **Auth0** - Managed, developer-friendly
- **Okta** - Enterprise identity platform
- **Azure AD** - Microsoft identity
- **Google Identity** - Google accounts
- **Keycloak** - Open-source
- **AWS Cognito** - AWS managed

### Client Libraries
- **JavaScript**: `oidc-client-ts`, `@auth0/auth0-spa-js`
- **React**: `@auth0/auth0-react`, `react-oidc-context`
- **Node.js**: `passport-openidconnect`, `express-openid-connect`
- **Python**: `authlib`, `python-jose`
- **Java**: Spring Security OAuth2, `pac4j`
- **Go**: `coreos/go-oidc`

## Interview Questions

### Basic
1. What is OIDC and how does it differ from OAuth 2.0?
2. What is an ID token and what does it contain?
3. What are the standard OIDC scopes?
4. How does Single Sign-On work with OIDC?

### Intermediate
5. Explain the complete OIDC authentication flow
6. How do you validate an ID token?
7. What is the purpose of the nonce parameter?
8. When would you use the UserInfo endpoint vs ID token?

### Advanced
9. How would you implement OIDC in a microservices architecture?
10. Explain the BFF pattern and why it's used with SPAs
11. How do you handle logout in a multi-app SSO scenario?
12. Design an authentication system using OIDC for a mobile app calling microservices

## Further Reading

- [OpenID Connect Core 1.0](https://openid.net/specs/openid-connect-core-1_0.html)
- [OpenID Connect Discovery 1.0](https://openid.net/specs/openid-connect-discovery-1_0.html)
- [oauth.net/connect](https://oauth.net/connect/)
- [JWT.io](https://jwt.io/) - Decode and verify JWTs
- [OpenID Foundation](https://openid.net/)
