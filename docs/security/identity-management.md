# Identity Management Protocols - SCIM, LDAP, RADIUS, TACACS+

## SCIM (System for Cross-domain Identity Management)

### Overview

SCIM is a REST API standard (RFC 7643, 7644) for **automated user provisioning and de-provisioning** across systems. It solves the problem of syncing user data between identity providers and applications.

**Use Case**: When a new employee joins, automatically create accounts in Slack, GitHub, Jira, etc.

### Core Concepts

**SCIM Resources**:
- **User** - User account data
- **Group** - Collections of users
- **Enterprise User** - Extended user attributes
- **Custom Resources** - Application-specific

**SCIM Operations**:
- **Create** - POST /Users
- **Read** - GET /Users/{id}
- **Update** - PUT/PATCH /Users/{id}
- **Delete** - DELETE /Users/{id}
- **Search** - GET /Users?filter=...

### SCIM User Resource

```json
{
  "schemas": ["urn:ietf:params:scim:schemas:core:2.0:User"],
  "id": "user-12345",
  "userName": "john.doe@example.com",
  "name": {
    "givenName": "John",
    "familyName": "Doe",
    "formatted": "John Doe"
  },
  "emails": [
    {
      "value": "john.doe@example.com",
      "type": "work",
      "primary": true
    }
  ],
  "active": true,
  "groups": [
    {
      "value": "group-123",
      "display": "Engineering",
      "$ref": "https://api.example.com/scim/v2/Groups/group-123"
    }
  ],
  "meta": {
    "resourceType": "User",
    "created": "2024-01-15T10:00:00Z",
    "lastModified": "2024-01-15T10:00:00Z",
    "location": "https://api.example.com/scim/v2/Users/user-12345",
    "version": "W/\"abc123\""
  }
}
```

### Implementation (Express + MongoDB)

```javascript
const express = require('express');
const router = express.Router();

// List users
router.get('/Users', async (req, res) => {
  const { startIndex = 1, count = 100, filter } = req.query;

  let query = {};

  // Parse SCIM filter (simplified)
  if (filter) {
    // Example: filter=userName eq "john@example.com"
    const match = filter.match(/(\w+)\s+(eq|ne|co|sw)\s+"([^"]+)"/);
    if (match) {
      const [, field, op, value] = match;
      if (op === 'eq') query[field] = value;
      if (op === 'co') query[field] = new RegExp(value);
      if (op === 'sw') query[field] = new RegExp(`^${value}`);
    }
  }

  const total = await db.users.count(query);
  const users = await db.users.find(query)
    .skip(startIndex - 1)
    .limit(count);

  res.json({
    schemas: ['urn:ietf:params:scim:api:messages:2.0:ListResponse'],
    totalResults: total,
    startIndex: parseInt(startIndex),
    itemsPerPage: users.length,
    Resources: users.map(toScimUser)
  });
});

// Get user
router.get('/Users/:id', async (req, res) => {
  const user = await db.users.findById(req.params.id);

  if (!user) {
    return res.status(404).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '404',
      detail: 'User not found'
    });
  }

  res.json(toScimUser(user));
});

// Create user
router.post('/Users', async (req, res) => {
  const userData = req.body;

  // Validate required fields
  if (!userData.userName) {
    return res.status(400).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '400',
      detail: 'userName is required'
    });
  }

  // Check if user exists
  const existing = await db.users.findOne({ userName: userData.userName });
  if (existing) {
    return res.status(409).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '409',
      detail: 'User already exists'
    });
  }

  // Create user
  const user = await db.users.create({
    userName: userData.userName,
    name: userData.name,
    emails: userData.emails,
    active: userData.active !== false,
    externalId: userData.externalId
  });

  res.status(201).json(toScimUser(user));
});

// Update user (PUT - full replace)
router.put('/Users/:id', async (req, res) => {
  const user = await db.users.findByIdAndUpdate(
    req.params.id,
    fromScimUser(req.body),
    { new: true }
  );

  if (!user) {
    return res.status(404).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '404'
    });
  }

  res.json(toScimUser(user));
});

// Patch user (partial update)
router.patch('/Users/:id', async (req, res) => {
  const { Operations } = req.body;

  let user = await db.users.findById(req.params.id);

  if (!user) {
    return res.status(404).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '404'
    });
  }

  // Apply operations
  for (const op of Operations) {
    if (op.op === 'replace') {
      if (op.path === 'active') {
        user.active = op.value;
      } else if (op.path.startsWith('name.')) {
        const field = op.path.split('.')[1];
        user.name[field] = op.value;
      }
    } else if (op.op === 'add') {
      // Add to array fields
    } else if (op.op === 'remove') {
      // Remove from array fields
    }
  }

  user = await db.users.save(user);

  res.json(toScimUser(user));
});

// Delete user (soft delete - set active=false)
router.delete('/Users/:id', async (req, res) => {
  const user = await db.users.findByIdAndUpdate(
    req.params.id,
    { active: false },
    { new: true }
  );

  if (!user) {
    return res.status(404).json({
      schemas: ['urn:ietf:params:scim:api:messages:2.0:Error'],
      status: '404'
    });
  }

  res.status(204).send();
});

// Helper functions
function toScimUser(user) {
  return {
    schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
    id: user.id,
    userName: user.userName,
    name: user.name,
    emails: user.emails,
    active: user.active,
    meta: {
      resourceType: 'User',
      created: user.createdAt.toISOString(),
      lastModified: user.updatedAt.toISOString(),
      location: `https://api.example.com/scim/v2/Users/${user.id}`
    }
  };
}

function fromScimUser(scimUser) {
  return {
    userName: scimUser.userName,
    name: scimUser.name,
    emails: scimUser.emails,
    active: scimUser.active
  };
}

module.exports = router;
```

### SCIM Client (Provisioning from IdP)

```javascript
// Sync users from IdP to your app
class SCIMClient {
  constructor(baseUrl, bearerToken) {
    this.baseUrl = baseUrl;
    this.token = bearerToken;
  }

  async createUser(userData) {
    const response = await fetch(`${this.baseUrl}/Users`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/scim+json',
        'Authorization': `Bearer ${this.token}`
      },
      body: JSON.stringify({
        schemas: ['urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: userData.email,
        name: {
          givenName: userData.firstName,
          familyName: userData.lastName
        },
        emails: [{
          value: userData.email,
          type: 'work',
          primary: true
        }],
        active: true
      })
    });

    return response.json();
  }

  async deactivateUser(userId) {
    const response = await fetch(`${this.baseUrl}/Users/${userId}`, {
      method: 'PATCH',
      headers: {
        'Content-Type': 'application/scim+json',
        'Authorization': `Bearer ${this.token}`
      },
      body: JSON.stringify({
        schemas: ['urn:ietf:params:scim:api:messages:2.0:PatchOp'],
        Operations: [{
          op: 'replace',
          path: 'active',
          value: false
        }]
      })
    });

    return response.json();
  }
}
```

## LDAP (Lightweight Directory Access Protocol)

### Overview

LDAP is a protocol for accessing and maintaining directory services. Commonly used with **Active Directory** for enterprise authentication and user management.

**Key Concepts**:
- **Directory**: Hierarchical database of entries
- **DN (Distinguished Name)**: Unique identifier (e.g., `cn=john,ou=users,dc=example,dc=com`)
- **Attributes**: Key-value pairs (cn, mail, telephoneNumber)
- **Schema**: Defines object classes and attributes

### LDAP Hierarchy

```
dc=com
└── dc=example
    ├── ou=users
    │   ├── cn=john.doe
    │   ├── cn=jane.smith
    │   └── cn=bob.jones
    ├── ou=groups
    │   ├── cn=engineering
    │   └── cn=admins
    └── ou=computers
        ├── cn=laptop-001
        └── cn=server-001
```

### LDAP Operations

1. **Bind** - Authenticate to LDAP server
2. **Search** - Query directory
3. **Add** - Create entry
4. **Modify** - Update entry
5. **Delete** - Remove entry
6. **Compare** - Check attribute value

### Implementation (Node.js with ldapjs)

```javascript
const ldap = require('ldapjs');

// Connect to LDAP server
const client = ldap.createClient({
  url: 'ldap://ldap.example.com:389'
});

// Bind (authenticate)
client.bind('cn=admin,dc=example,dc=com', 'adminPassword', (err) => {
  if (err) {
    console.error('Bind failed:', err);
    return;
  }

  console.log('Bound successfully');
});

// Search for users
function searchUsers(filter, callback) {
  const opts = {
    filter: filter, // e.g., '(&(objectClass=person)(mail=*@example.com))'
    scope: 'sub',
    attributes: ['cn', 'mail', 'telephoneNumber', 'memberOf']
  };

  client.search('ou=users,dc=example,dc=com', opts, (err, res) => {
    const results = [];

    res.on('searchEntry', (entry) => {
      results.push(entry.object);
    });

    res.on('end', () => {
      callback(null, results);
    });

    res.on('error', (err) => {
      callback(err);
    });
  });
}

// Authenticate user
function authenticateUser(username, password, callback) {
  // First, search for user DN
  const filter = `(cn=${username})`;

  searchUsers(filter, (err, users) => {
    if (err || users.length === 0) {
      return callback(new Error('User not found'));
    }

    const userDN = users[0].dn;

    // Try to bind as the user
    const userClient = ldap.createClient({
      url: 'ldap://ldap.example.com:389'
    });

    userClient.bind(userDN, password, (err) => {
      if (err) {
        callback(new Error('Invalid password'));
      } else {
        callback(null, users[0]);
      }

      userClient.unbind();
    });
  });
}

// Add user
function addUser(userData) {
  const entry = {
    cn: userData.username,
    sn: userData.lastName,
    mail: userData.email,
    userPassword: userData.password,
    objectClass: ['person', 'organizationalPerson', 'inetOrgPerson']
  };

  const dn = `cn=${userData.username},ou=users,dc=example,dc=com`;

  client.add(dn, entry, (err) => {
    if (err) {
      console.error('Add failed:', err);
    } else {
      console.log('User added successfully');
    }
  });
}

// Modify user
function updateUserEmail(username, newEmail) {
  const dn = `cn=${username},ou=users,dc=example,dc=com`;

  const change = new ldap.Change({
    operation: 'replace',
    modification: {
      mail: newEmail
    }
  });

  client.modify(dn, change, (err) => {
    if (err) {
      console.error('Modify failed:', err);
    } else {
      console.log('Email updated');
    }
  });
}
```

### LDAP Authentication Middleware

```javascript
function ldapAuth(req, res, next) {
  const { username, password } = req.body;

  authenticateUser(username, password, (err, user) => {
    if (err) {
      return res.status(401).json({ error: 'Authentication failed' });
    }

    // User authenticated, create session
    req.user = {
      username: user.cn,
      email: user.mail,
      groups: user.memberOf || []
    };

    next();
  });
}

app.post('/login', ldapAuth, (req, res) => {
  const token = jwt.sign(
    { sub: req.user.username, groups: req.user.groups },
    privateKey,
    { algorithm: 'RS256', expiresIn: '8h' }
  );

  res.json({ token });
});
```

## RADIUS (Remote Authentication Dial-In User Service)

### Overview

RADIUS is a networking protocol for **AAA** (Authentication, Authorization, Accounting). Commonly used for:
- VPN authentication
- WiFi (WPA-Enterprise)
- Network device management
- Remote access

### How RADIUS Works

```
┌──────────┐         ┌────────────┐         ┌───────────────┐
│  Client  │────────►│   NAS      │────────►│ RADIUS Server │
│ (User)   │         │ (Network   │         │               │
│          │         │  Access    │         │               │
│          │         │  Server)   │         │               │
└──────────┘         └────────────┘         └───────────────┘

1. User enters credentials
2. NAS sends Access-Request to RADIUS server
3. RADIUS server validates credentials
4. RADIUS server sends Access-Accept or Access-Reject
5. NAS grants or denies access
```

### RADIUS Packet Types

- **Access-Request** - User credentials
- **Access-Accept** - Authentication successful
- **Access-Reject** - Authentication failed
- **Access-Challenge** - Need more info (e.g., MFA code)
- **Accounting-Request** - Usage data
- **Accounting-Response** - Acknowledgment

### RADIUS Server (Node.js)

```javascript
const radius = require('radius');
const dgram = require('dgram');

const server = dgram.createSocket('udp4');
const secret = 'shared-secret';

server.on('message', async (msg, rinfo) => {
  try {
    // Decode RADIUS packet
    const packet = radius.decode({ packet: msg, secret });

    if (packet.code === 'Access-Request') {
      const username = packet.attributes['User-Name'];
      const password = packet.attributes['User-Password'];

      // Authenticate user
      const valid = await authenticateUser(username, password);

      let response;

      if (valid) {
        // Access-Accept
        response = radius.encode_response({
          packet: msg,
          code: 'Access-Accept',
          secret,
          attributes: {
            'Reply-Message': 'Welcome!',
            'Session-Timeout': 3600,
            'Idle-Timeout': 600
          }
        });
      } else {
        // Access-Reject
        response = radius.encode_response({
          packet: msg,
          code: 'Access-Reject',
          secret,
          attributes: {
            'Reply-Message': 'Invalid credentials'
          }
        });
      }

      server.send(response, 0, response.length, rinfo.port, rinfo.address);
    }
  } catch (err) {
    console.error('Error processing RADIUS packet:', err);
  }
});

server.bind(1812); // RADIUS authentication port
console.log('RADIUS server listening on port 1812');
```

### RADIUS Client

```javascript
const radius = require('radius');
const dgram = require('dgram');

function radiusAuth(username, password, callback) {
  const packet = radius.encode({
    code: 'Access-Request',
    secret: 'shared-secret',
    attributes: {
      'User-Name': username,
      'User-Password': password,
      'NAS-IP-Address': '192.168.1.1',
      'NAS-Port': 0
    }
  });

  const client = dgram.createSocket('udp4');

  client.on('message', (msg) => {
    const response = radius.decode({ packet: msg, secret: 'shared-secret' });

    if (response.code === 'Access-Accept') {
      callback(null, true);
    } else {
      callback(null, false);
    }

    client.close();
  });

  client.send(packet, 0, packet.length, 1812, 'radius.example.com');

  // Timeout after 5 seconds
  setTimeout(() => {
    client.close();
    callback(new Error('RADIUS timeout'));
  }, 5000);
}
```

## TACACS+ (Terminal Access Controller Access-Control System Plus)

### Overview

TACACS+ is a Cisco protocol similar to RADIUS but with key differences:
- **Separates AAA** - Authentication, authorization, accounting are separate
- **TCP-based** - More reliable than RADIUS (UDP)
- **Encrypts entire payload** - RADIUS only encrypts password
- **Fine-grained authorization** - Per-command authorization

**Use Case**: Network device management (routers, switches, firewalls)

### TACACS+ vs RADIUS

| Feature | TACACS+ | RADIUS |
|---------|---------|--------|
| Protocol | TCP (port 49) | UDP (1812, 1813) |
| AAA | Separate | Combined |
| Encryption | Full payload | Password only |
| Authorization | Granular (per-command) | Basic |
| Accounting | Detailed | Basic |
| Vendor | Cisco | Open standard |

### TACACS+ Packet Structure

```
┌─────────────────────────────────────┐
│          TACACS+ Header             │
│  Version | Type | Seq | Flags | ... │
├─────────────────────────────────────┤
│          Encrypted Payload          │
│  (Authentication/Authorization/     │
│   Accounting data)                  │
└─────────────────────────────────────┘
```

### Use Case Example

```bash
# Router configuration to use TACACS+
aaa new-model
aaa authentication login default group tacacs+ local
aaa authorization exec default group tacacs+ local
aaa accounting exec default start-stop group tacacs+

tacacs-server host 192.168.1.100 key MySecretKey
```

When admin logs into router:
1. Router sends authentication request to TACACS+ server
2. TACACS+ validates credentials (could use AD, LDAP, database)
3. Router requests authorization for commands
4. TACACS+ grants/denies each command based on user role
5. Router logs all actions to TACACS+ for accounting

## Best Practices

### SCIM
1. **Use pagination** for large user lists
2. **Implement filters** for efficient queries
3. **Support PATCH** for partial updates
4. **Version your API** to handle schema changes
5. **Secure with OAuth 2.0** (not basic auth)

### LDAP
1. **Use LDAPS (LDAP over SSL)** - Port 636
2. **Bind with service account** - Don't use admin credentials
3. **Implement connection pooling** - Reuse connections
4. **Use filters carefully** - Prevent LDAP injection
5. **Cache results** - Directory changes infrequently

### RADIUS/TACACS+
1. **Use strong shared secrets** - 32+ characters
2. **Implement timeout and retry** logic
3. **Monitor failed authentication** attempts
4. **Use TACACS+ for network devices** (better encryption)
5. **Use RADIUS for WiFi/VPN** (wider support)

## Interview Questions

### SCIM
1. What is SCIM and what problem does it solve?
2. How does SCIM provisioning work?
3. What are the main SCIM resource types?

### LDAP
4. Explain LDAP directory structure
5. What is a Distinguished Name (DN)?
6. How do you authenticate users with LDAP?

### RADIUS/TACACS+
7. What is RADIUS and what is it used for?
8. Compare RADIUS vs TACACS+
9. How does RADIUS authentication flow work?

### Advanced
10. Design a user provisioning system using SCIM
11. How would you integrate LDAP with a web application?
12. When would you choose TACACS+ over RADIUS?

## Further Reading

- [RFC 7643 - SCIM Core Schema](https://tools.ietf.org/html/rfc7643)
- [RFC 7644 - SCIM Protocol](https://tools.ietf.org/html/rfc7644)
- [RFC 4511 - LDAP](https://tools.ietf.org/html/rfc4511)
- [RFC 2865 - RADIUS](https://tools.ietf.org/html/rfc2865)
- [Cisco TACACS+ Documentation](https://www.cisco.com/c/en/us/support/docs/security-vpn/terminal-access-controller-access-control-system-tacacs-/13847-tacacsconfig.html)
