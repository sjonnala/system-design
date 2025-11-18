# SAML 2.0 - Security Assertion Markup Language

## Overview

SAML 2.0 (Security Assertion Markup Language) is an XML-based open standard for **authentication** and **authorization** between parties. It's the dominant protocol for **enterprise Single Sign-On (SSO)** and is widely used in organizations with centralized identity management.

**Key Use Case**: Enterprise SSO where employees log in once to access multiple applications.

**Modern Alternative**: OpenID Connect (OIDC) is simpler and more developer-friendly, but SAML 2.0 remains the standard in enterprise environments.

## Core Concepts

### The Three Parties

1. **Principal (User)** - The person trying to access a resource
2. **Identity Provider (IdP)** - Authenticates users and issues assertions (e.g., Okta, Azure AD, OneLogin)
3. **Service Provider (SP)** - The application the user wants to access (e.g., Salesforce, Jira, your app)

```
┌──────────┐         ┌─────────────┐         ┌──────────────┐
│   User   │────────►│     SP      │◄───────►│     IdP      │
│          │         │  (Your App) │         │  (Okta/AD)   │
└──────────┘         └─────────────┘         └──────────────┘
                           │                        │
                           │  "Who is this user?"   │
                           ├───────────────────────►│
                           │                        │
                           │  "Here's the user info"│
                           │◄───────────────────────┤
                           │  (SAML Assertion)      │
```

### SAML Assertions

An assertion is an XML document containing user identity and attributes. Think of it as a "ticket" that proves who you are.

**Three Types of Assertions**:

1. **Authentication Assertion** - Proves user was authenticated
2. **Attribute Assertion** - Contains user attributes (email, name, groups)
3. **Authorization Decision Assertion** - Specifies what user can access (rarely used)

**Example SAML Assertion**:
```xml
<saml:Assertion xmlns:saml="urn:oasis:names:tc:SAML:2.0:assertion"
                ID="_abc123"
                IssueInstant="2024-01-15T10:30:00Z"
                Version="2.0">
  <saml:Issuer>https://idp.example.com</saml:Issuer>

  <!-- Digital signature for verification -->
  <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
    ...
  </ds:Signature>

  <!-- Subject (who this assertion is about) -->
  <saml:Subject>
    <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">
      john.doe@example.com
    </saml:NameID>
    <saml:SubjectConfirmation Method="urn:oasis:names:tc:SAML:2.0:cm:bearer">
      <saml:SubjectConfirmationData
          NotOnOrAfter="2024-01-15T10:35:00Z"
          Recipient="https://sp.example.com/saml/acs"
          InResponseTo="_request123"/>
    </saml:SubjectConfirmation>
  </saml:Subject>

  <!-- Conditions (when assertion is valid) -->
  <saml:Conditions NotBefore="2024-01-15T10:30:00Z"
                   NotOnOrAfter="2024-01-15T10:35:00Z">
    <saml:AudienceRestriction>
      <saml:Audience>https://sp.example.com</saml:Audience>
    </saml:AudienceRestriction>
  </saml:Conditions>

  <!-- Authentication statement -->
  <saml:AuthnStatement AuthnInstant="2024-01-15T10:30:00Z"
                       SessionIndex="_session456">
    <saml:AuthnContext>
      <saml:AuthnContextClassRef>
        urn:oasis:names:tc:SAML:2.0:ac:classes:PasswordProtectedTransport
      </saml:AuthnContextClassRef>
    </saml:AuthnContext>
  </saml:AuthnStatement>

  <!-- Attributes (user information) -->
  <saml:AttributeStatement>
    <saml:Attribute Name="email">
      <saml:AttributeValue>john.doe@example.com</saml:AttributeValue>
    </saml:Attribute>
    <saml:Attribute Name="firstName">
      <saml:AttributeValue>John</saml:AttributeValue>
    </saml:Attribute>
    <saml:Attribute Name="lastName">
      <saml:AttributeValue>Doe</saml:AttributeValue>
    </saml:Attribute>
    <saml:Attribute Name="groups">
      <saml:AttributeValue>Engineering</saml:AttributeValue>
      <saml:AttributeValue>Admins</saml:AttributeValue>
    </saml:Attribute>
  </saml:AttributeStatement>
</saml:Assertion>
```

## SAML Flows (Bindings)

### 1. SP-Initiated Flow (Most Common)

**Use Case**: User starts at your application (SP)

```
1. User visits https://yourapp.com
      │
      ▼
2. App redirects to IdP with SAML AuthnRequest
      │
      ▼
3. IdP authenticates user (login page)
      │
      ▼
4. IdP sends SAML Response back to app
      │
      ▼
5. App validates assertion and creates session
      │
      ▼
6. User is logged in!
```

**Detailed Flow**:

```
┌──────────┐                                              ┌─────────────┐
│  User    │                                              │     SP      │
└─────┬────┘                                              └──────┬──────┘
      │                                                          │
      │  1. GET https://app.example.com                          │
      ├─────────────────────────────────────────────────────────►│
      │                                                          │
      │  2. Redirect to IdP (302)                                │
      │     Location: https://idp.example.com/sso?SAMLRequest=.. │
      │◄─────────────────────────────────────────────────────────┤
      │                                                          │
┌─────▼────┐                                              ┌──────▼──────┐
│  User    │                                              │     IdP     │
└─────┬────┘                                              └──────┬──────┘
      │                                                          │
      │  3. Present login page (if not authenticated)            │
      │◄─────────────────────────────────────────────────────────┤
      │                                                          │
      │  4. Submit credentials                                   │
      ├─────────────────────────────────────────────────────────►│
      │                                                          │
      │  5. Redirect to SP with SAMLResponse (302)               │
      │     Location: https://app.example.com/saml/acs           │
      │     POST data: SAMLResponse=base64(assertion)            │
      │◄─────────────────────────────────────────────────────────┤
      │                                                          │
┌─────▼────┐                                              ┌──────▼──────┐
│  User    │                                              │     SP      │
└─────┬────┘                                              └──────┬──────┘
      │                                                          │
      │  6. POST SAMLResponse to ACS                             │
      ├─────────────────────────────────────────────────────────►│
      │                                                          │
      │                                              7. Validate assertion
      │                                              8. Create session
      │                                                          │
      │  9. Set session cookie, redirect to app                  │
      │◄─────────────────────────────────────────────────────────┤
      │                                                          │
      │  10. Access protected resource                           │
      ├─────────────────────────────────────────────────────────►│
      │                                                          │
```

**Step 2 - SAML AuthnRequest** (sent from SP to IdP):
```xml
<samlp:AuthnRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                    ID="_request123"
                    Version="2.0"
                    IssueInstant="2024-01-15T10:30:00Z"
                    Destination="https://idp.example.com/sso"
                    AssertionConsumerServiceURL="https://sp.example.com/saml/acs"
                    ProtocolBinding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST">
  <saml:Issuer>https://sp.example.com</saml:Issuer>
  <samlp:NameIDPolicy Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress"
                      AllowCreate="true"/>
</samlp:AuthnRequest>
```

**Step 5 - SAML Response** (sent from IdP to SP):
```xml
<samlp:Response xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                ID="_response456"
                InResponseTo="_request123"
                Version="2.0"
                IssueInstant="2024-01-15T10:30:05Z"
                Destination="https://sp.example.com/saml/acs">
  <saml:Issuer>https://idp.example.com</saml:Issuer>
  <samlp:Status>
    <samlp:StatusCode Value="urn:oasis:names:tc:SAML:2.0:status:Success"/>
  </samlp:Status>
  <saml:Assertion>
    <!-- Assertion XML (shown above) -->
  </saml:Assertion>
</samlp:Response>
```

### 2. IdP-Initiated Flow

**Use Case**: User starts at IdP portal (e.g., Okta dashboard)

```
1. User logs into IdP portal
      │
      ▼
2. User clicks app icon
      │
      ▼
3. IdP sends SAML Response to app (no AuthnRequest)
      │
      ▼
4. App validates assertion and creates session
      │
      ▼
5. User is logged in!
```

**Difference**: No `InResponseTo` field in assertion (no initial request)

### HTTP Bindings

SAML messages can be sent via:

1. **HTTP-Redirect** (GET) - Used for AuthnRequest
   ```
   https://idp.example.com/sso?SAMLRequest=base64(deflated(xml))
   ```

2. **HTTP-POST** (POST) - Used for Response (preferred)
   ```html
   <form method="post" action="https://sp.example.com/saml/acs">
     <input type="hidden" name="SAMLResponse" value="base64(xml)"/>
   </form>
   ```

3. **HTTP-Artifact** - Reference to assertion (advanced, rarely used)

## SAML Metadata

Both SP and IdP exchange metadata XML to configure trust.

### SP Metadata

Describes your application:

```xml
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                     entityID="https://sp.example.com">
  <md:SPSSODescriptor
      AuthnRequestsSigned="true"
      WantAssertionsSigned="true"
      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">

    <!-- Where IdP should send assertions -->
    <md:AssertionConsumerService
        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
        Location="https://sp.example.com/saml/acs"
        index="0"
        isDefault="true"/>

    <!-- SP's public key for signature verification -->
    <md:KeyDescriptor use="signing">
      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:X509Data>
          <ds:X509Certificate>MIIDXTCCAkWgAwIBAgIJA...</ds:X509Certificate>
        </ds:X509Data>
      </ds:KeyInfo>
    </md:KeyDescriptor>
  </md:SPSSODescriptor>
</md:EntityDescriptor>
```

**Typically available at**: `https://sp.example.com/saml/metadata`

### IdP Metadata

Describes the identity provider:

```xml
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata"
                     entityID="https://idp.example.com">
  <md:IDPSSODescriptor
      WantAuthnRequestsSigned="false"
      protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">

    <!-- IdP's public key for signature verification -->
    <md:KeyDescriptor use="signing">
      <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
        <ds:X509Data>
          <ds:X509Certificate>MIIDZjCCAk6gAwIBAgIGA...</ds:X509Certificate>
        </ds:X509Data>
      </ds:KeyInfo>
    </md:KeyDescriptor>

    <!-- Single Sign-On endpoint -->
    <md:SingleSignOnService
        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
        Location="https://idp.example.com/sso"/>
    <md:SingleSignOnService
        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST"
        Location="https://idp.example.com/sso"/>

    <!-- Single Logout Service -->
    <md:SingleLogoutService
        Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect"
        Location="https://idp.example.com/slo"/>
  </md:IDPSSODescriptor>
</md:EntityDescriptor>
```

## Implementing SAML in Your Application

### Step 1: Configure SP Metadata

```javascript
// Using passport-saml (Node.js)
const saml = require('passport-saml');

const samlStrategy = new saml.Strategy(
  {
    // SP settings
    callbackUrl: 'https://sp.example.com/saml/acs',
    entryPoint: 'https://idp.example.com/sso',
    issuer: 'https://sp.example.com',

    // IdP certificate (from IdP metadata)
    cert: idpCertificate,

    // SP private key (for signing requests)
    privateKey: spPrivateKey,
    decryptionPvk: spPrivateKey,

    // Signature settings
    signatureAlgorithm: 'sha256',

    // Attribute mapping
    identifierFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress',
  },
  (profile, done) => {
    // Extract user from SAML assertion
    const user = {
      id: profile.nameID,
      email: profile.email,
      firstName: profile.firstName,
      lastName: profile.lastName,
      groups: profile.groups
    };
    return done(null, user);
  }
);
```

### Step 2: Initiate SSO (SP-Initiated)

```javascript
const express = require('express');
const passport = require('passport');

// Redirect to IdP for authentication
app.get('/login',
  passport.authenticate('saml', {
    successRedirect: '/dashboard',
    failureRedirect: '/login-failed'
  })
);
```

This generates and redirects with AuthnRequest:
```
GET https://idp.example.com/sso?SAMLRequest=base64(...)&RelayState=...
```

### Step 3: Handle SAML Response (ACS Endpoint)

```javascript
// Assertion Consumer Service (ACS)
app.post('/saml/acs',
  passport.authenticate('saml', {
    failureRedirect: '/login-failed',
    failureFlash: true
  }),
  (req, res) => {
    // SAML assertion validated successfully
    // req.user contains user info from assertion

    // Create session
    req.session.userId = req.user.id;
    req.session.email = req.user.email;

    // Redirect to app
    res.redirect('/dashboard');
  }
);
```

### Step 4: Validate Assertion

**Critical validations**:

```javascript
function validateSAMLAssertion(assertion, idpCert) {
  // 1. Verify signature
  if (!verifySignature(assertion, idpCert)) {
    throw new Error('Invalid signature');
  }

  // 2. Check issuer
  if (assertion.Issuer !== expectedIdpIssuer) {
    throw new Error('Invalid issuer');
  }

  // 3. Check audience
  if (!assertion.Conditions.AudienceRestriction.includes(spEntityId)) {
    throw new Error('Invalid audience');
  }

  // 4. Check time validity
  const now = new Date();
  const notBefore = new Date(assertion.Conditions.NotBefore);
  const notOnOrAfter = new Date(assertion.Conditions.NotOnOrAfter);

  if (now < notBefore || now >= notOnOrAfter) {
    throw new Error('Assertion expired or not yet valid');
  }

  // 5. Check InResponseTo (SP-initiated only)
  if (expectedRequestId && assertion.InResponseTo !== expectedRequestId) {
    throw new Error('Invalid InResponseTo');
  }

  // 6. Prevent replay attacks
  if (isAssertionUsed(assertion.ID)) {
    throw new Error('Assertion already used');
  }
  markAssertionAsUsed(assertion.ID);

  return true;
}
```

### Step 5: Extract User Attributes

```javascript
function extractUserInfo(assertion) {
  const attributes = assertion.AttributeStatement.Attribute;

  const user = {};

  attributes.forEach(attr => {
    switch (attr.Name) {
      case 'email':
      case 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/emailaddress':
        user.email = attr.AttributeValue;
        break;
      case 'firstName':
      case 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/givenname':
        user.firstName = attr.AttributeValue;
        break;
      case 'lastName':
      case 'http://schemas.xmlsoap.org/ws/2005/05/identity/claims/surname':
        user.lastName = attr.AttributeValue;
        break;
      case 'groups':
      case 'http://schemas.xmlsoap.org/claims/Group':
        user.groups = Array.isArray(attr.AttributeValue)
          ? attr.AttributeValue
          : [attr.AttributeValue];
        break;
    }
  });

  return user;
}
```

## Single Logout (SLO)

Logout from one app logs out from all apps.

### SP-Initiated Logout

```
1. User clicks logout in App A
      │
      ▼
2. App A sends LogoutRequest to IdP
      │
      ▼
3. IdP sends LogoutRequest to App B, App C, etc.
      │
      ▼
4. Each app responds with LogoutResponse
      │
      ▼
5. IdP sends LogoutResponse to App A
      │
      ▼
6. User logged out from all apps
```

**LogoutRequest**:
```xml
<samlp:LogoutRequest xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol"
                     ID="_logout123"
                     Version="2.0"
                     IssueInstant="2024-01-15T11:00:00Z"
                     Destination="https://idp.example.com/slo">
  <saml:Issuer>https://sp.example.com</saml:Issuer>
  <saml:NameID Format="urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress">
    john.doe@example.com
  </saml:NameID>
  <samlp:SessionIndex>_session456</samlp:SessionIndex>
</samlp:LogoutRequest>
```

**Implementation**:
```javascript
app.get('/logout', (req, res) => {
  // Generate logout request
  const logoutUrl = samlStrategy.generateLogoutUrl(req);

  // Clear local session
  req.session.destroy();

  // Redirect to IdP logout
  res.redirect(logoutUrl);
});

// Handle logout response from IdP
app.post('/saml/slo', (req, res) => {
  // Validate logout response
  samlStrategy.validateLogoutResponse(req.body.SAMLResponse);

  // Redirect to logged-out page
  res.redirect('/logged-out');
});
```

## SAML in Microservices

### Pattern 1: API Gateway with SAML

```
┌──────────┐         ┌─────────────┐         ┌───────────┐
│  User    │────────►│ API Gateway │────────►│ Service A │
│(Browser) │  SAML   └──────┬──────┘  claims └───────────┘
└──────────┘                │
                            │ SAML SSO
                            ▼
                    ┌──────────────┐
                    │     IdP      │
                    │  (Okta/AD)   │
                    └──────────────┘
```

**API Gateway** handles SAML:
```javascript
// API Gateway
app.use('/api', samlAuth, proxyToServices);

function samlAuth(req, res, next) {
  if (!req.session.user) {
    return res.redirect('/login'); // Initiates SAML flow
  }

  // Forward user claims to services
  req.headers['X-User-Id'] = req.session.user.id;
  req.headers['X-User-Email'] = req.session.user.email;
  req.headers['X-User-Groups'] = req.session.user.groups.join(',');

  next();
}
```

### Pattern 2: BFF (Backend for Frontend)

```
┌──────────┐         ┌──────────┐         ┌─────────┐
│   SPA    │────────►│   BFF    │────────►│   IdP   │
└──────────┘ cookie  └─────┬────┘  SAML   └─────────┘
                           │
                           │ JWT
                           ▼
                    ┌─────────────┐
                    │  Services   │
                    └─────────────┘
```

BFF converts SAML assertion to JWT:
```javascript
// After successful SAML authentication
app.post('/saml/acs', passport.authenticate('saml'), (req, res) => {
  // Create JWT from SAML attributes
  const token = jwt.sign(
    {
      sub: req.user.id,
      email: req.user.email,
      groups: req.user.groups
    },
    privateKey,
    { expiresIn: '1h', algorithm: 'RS256' }
  );

  // Store in httpOnly cookie
  res.cookie('auth_token', token, {
    httpOnly: true,
    secure: true,
    sameSite: 'strict'
  });

  res.redirect('/dashboard');
});

// Forward JWT to services
app.use('/api', (req, res, next) => {
  const token = req.cookies.auth_token;
  req.headers['Authorization'] = `Bearer ${token}`;
  next();
}, proxyToServices);
```

## Common Pitfalls

### Pitfall 1: Not Validating Signature

```javascript
// ❌ DON'T: Trust assertion without verification
const assertion = parseXML(samlResponse);
const user = extractUser(assertion); // DANGEROUS!

// ✅ DO: Verify signature
const assertion = parseXML(samlResponse);
if (!verifySignature(assertion, idpCertificate)) {
  throw new Error('Invalid signature');
}
const user = extractUser(assertion);
```

### Pitfall 2: Weak Clock Skew Tolerance

```javascript
// ❌ DON'T: Large clock skew (replay attacks)
const clockSkew = 600; // 10 minutes - too large!

// ✅ DO: Minimal clock skew
const clockSkew = 30; // 30 seconds
```

### Pitfall 3: Not Preventing Replay Attacks

```javascript
// ✅ DO: Track used assertions
const usedAssertions = new Set();

function validateAssertion(assertion) {
  if (usedAssertions.has(assertion.ID)) {
    throw new Error('Assertion replay detected');
  }

  usedAssertions.add(assertion.ID);

  // Clean up old IDs after NotOnOrAfter expires
  setTimeout(() => {
    usedAssertions.delete(assertion.ID);
  }, 600000); // 10 minutes
}
```

### Pitfall 4: Insecure XML Parsing (XXE Attacks)

```javascript
// ❌ DON'T: Use unsafe XML parser
const xml = require('libxmljs').parseXml(samlResponse);

// ✅ DO: Disable external entities
const xml = require('libxmljs').parseXml(samlResponse, {
  noent: false,  // Disable entity expansion
  dtdload: false, // Disable DTD loading
  dtdvalid: false // Disable DTD validation
});
```

## Security Best Practices

### 1. Always Use HTTPS

```
❌ http://sp.example.com/saml/acs
✅ https://sp.example.com/saml/acs
```

### 2. Sign AuthnRequests

```javascript
const strategy = new SamlStrategy({
  // ...
  authnRequestsSigned: true,
  wantAssertionsSigned: true,
  privateKey: spPrivateKey
});
```

### 3. Validate Audience

```javascript
if (!assertion.Conditions.Audience.includes(SP_ENTITY_ID)) {
  throw new Error('Assertion not intended for this SP');
}
```

### 4. Check Time Windows

```javascript
const now = Date.now();
const notBefore = new Date(assertion.Conditions.NotBefore).getTime();
const notOnOrAfter = new Date(assertion.Conditions.NotOnOrAfter).getTime();

const clockSkew = 30000; // 30 seconds

if (now + clockSkew < notBefore || now - clockSkew >= notOnOrAfter) {
  throw new Error('Assertion time window invalid');
}
```

### 5. Use Secure Session Management

```javascript
app.use(session({
  secret: process.env.SESSION_SECRET,
  cookie: {
    httpOnly: true,
    secure: true, // HTTPS only
    sameSite: 'strict',
    maxAge: 3600000 // 1 hour
  },
  resave: false,
  saveUninitialized: false
}));
```

## SAML vs OIDC

| Feature | SAML 2.0 | OIDC |
|---------|----------|------|
| **Data Format** | XML | JSON (JWT) |
| **Complexity** | High | Low |
| **Message Size** | Large (KB) | Small (bytes) |
| **Mobile Support** | Poor | Excellent |
| **API Support** | Limited | Excellent |
| **Enterprise Adoption** | Very high | Growing |
| **Developer Experience** | Complex | Simple |
| **Use Case** | Enterprise SSO | Modern apps, mobile, APIs |
| **Token Format** | XML Assertion | JWT |
| **Learning Curve** | Steep | Gentle |

**When to use SAML**:
- Enterprise customers require it
- Integrating with existing SAML IdPs (Active Directory, Okta)
- B2B SaaS applications
- Compliance requirements

**When to use OIDC**:
- New applications
- Mobile apps
- Microservices APIs
- Developer-friendly authentication
- Smaller payloads

## Real-World Examples

### Example 1: Express.js with Passport-SAML

```javascript
const express = require('express');
const passport = require('passport');
const { Strategy } = require('passport-saml');
const fs = require('fs');

const app = express();

// SAML Strategy
passport.use(new Strategy(
  {
    callbackUrl: 'https://app.example.com/saml/acs',
    entryPoint: 'https://idp.example.com/sso',
    issuer: 'https://app.example.com',
    cert: fs.readFileSync('./idp-cert.pem', 'utf-8'),
    privateKey: fs.readFileSync('./sp-key.pem', 'utf-8'),
    decryptionPvk: fs.readFileSync('./sp-key.pem', 'utf-8'),
    signatureAlgorithm: 'sha256',
    identifierFormat: 'urn:oasis:names:tc:SAML:1.1:nameid-format:emailAddress'
  },
  (profile, done) => {
    return done(null, {
      id: profile.nameID,
      email: profile.email,
      displayName: profile.displayName
    });
  }
));

// Serialize user
passport.serializeUser((user, done) => done(null, user));
passport.deserializeUser((user, done) => done(null, user));

// Routes
app.get('/login',
  passport.authenticate('saml', { failureRedirect: '/', failureFlash: true })
);

app.post('/saml/acs',
  passport.authenticate('saml', { failureRedirect: '/', failureFlash: true }),
  (req, res) => {
    res.redirect('/dashboard');
  }
);

app.get('/saml/metadata', (req, res) => {
  res.type('application/xml');
  res.send(passport._strategy('saml').generateServiceProviderMetadata());
});

app.get('/logout', (req, res) => {
  req.logout();
  res.redirect('/');
});

app.listen(3000);
```

### Example 2: Spring Boot Java

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .authorizeRequests()
                .antMatchers("/", "/saml/**").permitAll()
                .anyRequest().authenticated()
            .and()
            .saml2Login()
            .and()
            .saml2Logout();
    }

    @Bean
    public RelyingPartyRegistrationRepository relyingPartyRegistrationRepository() {
        RelyingPartyRegistration registration = RelyingPartyRegistrations
            .fromMetadataLocation("https://idp.example.com/metadata")
            .registrationId("okta")
            .build();
        return new InMemoryRelyingPartyRegistrationRepository(registration);
    }
}

// application.yml
spring:
  security:
    saml2:
      relyingparty:
        registration:
          okta:
            entity-id: https://app.example.com
            acs:
              location: https://app.example.com/saml/acs
            signing:
              credentials:
                - private-key-location: classpath:credentials/sp-key.pem
                  certificate-location: classpath:credentials/sp-cert.pem
```

## Tools and Libraries

### SAML Providers (IdPs)
- **Okta** - Enterprise identity platform
- **Azure Active Directory** - Microsoft identity
- **OneLogin** - Cloud identity management
- **Auth0** - Supports SAML (also OIDC)
- **Keycloak** - Open-source
- **Shibboleth** - Open-source IdP

### Libraries
- **Node.js**: `passport-saml`, `saml2-js`
- **Python**: `python3-saml`, `pysaml2`
- **Java**: Spring Security SAML, `OpenSAML`
- **Ruby**: `ruby-saml`, `omniauth-saml`
- **Go**: `crewjam/saml`
- **.NET**: `Sustainsys.Saml2`, `ITfoxtec.Identity.Saml2`

### Testing Tools
- **SAMLtool.com** - Online SAML validator
- **SAML Tracer** - Browser extension for debugging
- **SAMLtest.id** - Free SAML test IdP

## Interview Questions

### Basic
1. What is SAML and what problem does it solve?
2. Explain the three parties in SAML (Principal, IdP, SP)
3. What is a SAML assertion?
4. What's the difference between SP-initiated and IdP-initiated flows?

### Intermediate
5. How does SAML SSO work? Walk through the flow.
6. What validations must you perform on a SAML assertion?
7. How does Single Logout work in SAML?
8. What is SAML metadata and why is it important?

### Advanced
9. How would you implement SAML in a microservices architecture?
10. Compare SAML vs OIDC - when would you use each?
11. What are the security risks in SAML and how do you mitigate them?
12. How do you handle SAML assertions in a multi-region deployment?

## Further Reading

- [SAML 2.0 Technical Overview (OASIS)](http://docs.oasis-open.org/security/saml/Post2.0/sstc-saml-tech-overview-2.0.html)
- [SAML 2.0 Specifications](http://saml.xml.org/saml-specifications)
- [OneLogin SAML Developer Tools](https://www.samltool.com/)
- [SAML Security Cheat Sheet (OWASP)](https://cheatsheetseries.owasp.org/cheatsheets/SAML_Security_Cheat_Sheet.html)
