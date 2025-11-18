# Secrets Management

## Overview

Secrets management is the practice of securely storing, accessing, and managing sensitive data like:
- API keys and tokens
- Database credentials
- Private keys and certificates
- Encryption keys
- Third-party service credentials

**Why It Matters**: Hardcoded secrets are the #1 cause of security breaches. Proper secrets management is critical for security.

## The Problem

### What NOT to Do

```javascript
// ❌ NEVER hardcode secrets
const API_KEY = 'sk_live_abc123xyz789';
const DB_PASSWORD = 'mysecretpassword';

// ❌ NEVER commit secrets to version control
// .env file in git repo
DATABASE_URL=postgresql://user:password@localhost/db

// ❌ NEVER log secrets
console.log('API Key:', process.env.API_KEY);

// ❌ NEVER expose in client-side code
const apiKey = 'sk_live_abc123xyz789'; // Visible in browser!
```

## Secrets Management Solutions

### 1. HashiCorp Vault

**Features**:
- Dynamic secrets (temporary credentials)
- Encryption as a service
- Secret rotation
- Audit logging
- Fine-grained access control

#### Vault Architecture

```
┌─────────────────────────────────────────────┐
│              HashiCorp Vault                │
├─────────────────────────────────────────────┤
│                                             │
│  ┌──────────────┐      ┌─────────────────┐ │
│  │   Auth       │      │   Secrets       │ │
│  │   Methods    │      │   Engines       │ │
│  ├──────────────┤      ├─────────────────┤ │
│  │ - Token      │      │ - KV (v1/v2)    │ │
│  │ - AppRole    │      │ - Database      │ │
│  │ - Kubernetes │      │ - AWS           │ │
│  │ - LDAP       │      │ - PKI           │ │
│  │ - GitHub     │      │ - Transit       │ │
│  └──────────────┘      └─────────────────┘ │
│                                             │
│  ┌──────────────┐      ┌─────────────────┐ │
│  │   Policies   │      │   Audit         │ │
│  │              │      │   Logging       │ │
│  └──────────────┘      └─────────────────┘ │
│                                             │
│  ┌──────────────────────────────────────┐  │
│  │         Storage Backend              │  │
│  │  (Consul, etcd, S3, PostgreSQL)      │  │
│  └──────────────────────────────────────┘  │
└─────────────────────────────────────────────┘
```

#### Setup Vault

```bash
# Start Vault server
vault server -dev

# Set environment variable
export VAULT_ADDR='http://127.0.0.1:8200'
export VAULT_TOKEN='root-token'

# Enable KV secrets engine
vault secrets enable -path=secret kv-v2

# Store secret
vault kv put secret/myapp/db \
  username=admin \
  password=secretpassword

# Read secret
vault kv get secret/myapp/db

# Enable database secrets engine (dynamic secrets)
vault secrets enable database

# Configure database connection
vault write database/config/postgresql \
  plugin_name=postgresql-database-plugin \
  allowed_roles="readonly" \
  connection_url="postgresql://{{username}}:{{password}}@localhost:5432/mydb" \
  username="vault" \
  password="vaultpassword"

# Create role for dynamic credentials
vault write database/roles/readonly \
  db_name=postgresql \
  creation_statements="CREATE ROLE \"{{name}}\" WITH LOGIN PASSWORD '{{password}}' VALID UNTIL '{{expiration}}'; \
    GRANT SELECT ON ALL TABLES IN SCHEMA public TO \"{{name}}\";" \
  default_ttl="1h" \
  max_ttl="24h"

# Generate dynamic credentials
vault read database/creds/readonly
# Returns temporary username/password that expires
```

#### Application Integration

```javascript
const vault = require('node-vault')({
  endpoint: 'http://127.0.0.1:8200',
  token: process.env.VAULT_TOKEN
});

// Read static secret
async function getDbCredentials() {
  const result = await vault.read('secret/data/myapp/db');
  return result.data.data; // { username, password }
}

// Get dynamic credentials
async function getDynamicDbCredentials() {
  const result = await vault.read('database/creds/readonly');
  return result.data; // { username, password, lease_id }
  // Credentials are temporary and auto-expire
}

// Use credentials
async function connectToDatabase() {
  const creds = await getDbCredentials();

  const db = new Database({
    host: 'localhost',
    username: creds.username,
    password: creds.password,
    database: 'mydb'
  });

  return db;
}

// Encrypt data (Transit engine)
async function encryptSensitiveData(plaintext) {
  await vault.write('transit/encrypt/myapp', {
    plaintext: Buffer.from(plaintext).toString('base64')
  });

  return result.data.ciphertext;
}

async function decryptSensitiveData(ciphertext) {
  const result = await vault.write('transit/decrypt/myapp', {
    ciphertext
  });

  return Buffer.from(result.data.plaintext, 'base64').toString();
}
```

#### Kubernetes Integration

```yaml
# Vault Agent Injector
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
spec:
  template:
    metadata:
      annotations:
        vault.hashicorp.com/agent-inject: "true"
        vault.hashicorp.com/role: "myapp"
        vault.hashicorp.com/agent-inject-secret-db: "secret/data/myapp/db"
        vault.hashicorp.com/agent-inject-template-db: |
          {{- with secret "secret/data/myapp/db" -}}
          export DB_USER="{{ .Data.data.username }}"
          export DB_PASS="{{ .Data.data.password }}"
          {{- end }}
    spec:
      serviceAccountName: myapp
      containers:
      - name: myapp
        image: myapp:latest
        command: ["/bin/sh"]
        args: ["-c", "source /vault/secrets/db && npm start"]
```

### 2. AWS Secrets Manager

**Features**:
- Automatic secret rotation
- Integration with AWS services
- Encryption at rest (KMS)
- Fine-grained IAM permissions

#### Store Secret

```bash
# Create secret
aws secretsmanager create-secret \
  --name myapp/db/credentials \
  --secret-string '{"username":"admin","password":"secretpass"}'

# Retrieve secret
aws secretsmanager get-secret-value \
  --secret-id myapp/db/credentials
```

#### Application Integration

```javascript
const AWS = require('aws-sdk');
const secretsManager = new AWS.SecretsManager({ region: 'us-east-1' });

async function getSecret(secretName) {
  try {
    const data = await secretsManager.getSecretValue({
      SecretId: secretName
    }).promise();

    if ('SecretString' in data) {
      return JSON.parse(data.SecretString);
    } else {
      // Binary secret
      const buff = Buffer.from(data.SecretBinary, 'base64');
      return buff.toString('ascii');
    }
  } catch (err) {
    throw err;
  }
}

// Use in application
async function connectToDatabase() {
  const secret = await getSecret('myapp/db/credentials');

  const db = new Database({
    host: process.env.DB_HOST,
    username: secret.username,
    password: secret.password,
    database: 'mydb'
  });

  return db;
}

// Cache secrets to reduce API calls
class SecretCache {
  constructor(ttl = 300000) { // 5 minutes default
    this.cache = new Map();
    this.ttl = ttl;
  }

  async get(secretName) {
    const cached = this.cache.get(secretName);

    if (cached && Date.now() - cached.timestamp < this.ttl) {
      return cached.value;
    }

    const value = await getSecret(secretName);

    this.cache.set(secretName, {
      value,
      timestamp: Date.now()
    });

    return value;
  }
}

const secretCache = new SecretCache();
```

#### Automatic Rotation

```javascript
// Lambda function for secret rotation
exports.handler = async (event) => {
  const { SecretId, Token, Step } = event;

  if (Step === 'createSecret') {
    // Generate new password
    const newPassword = generateStrongPassword();

    // Store as AWSPENDING version
    await secretsManager.putSecretValue({
      SecretId,
      ClientRequestToken: Token,
      SecretString: JSON.stringify({ password: newPassword }),
      VersionStages: ['AWSPENDING']
    }).promise();

  } else if (Step === 'setSecret') {
    // Update database with new password
    const pending = await getSecretVersion(SecretId, 'AWSPENDING');
    await updateDatabasePassword(pending.password);

  } else if (Step === 'testSecret') {
    // Test new credentials
    const pending = await getSecretVersion(SecretId, 'AWSPENDING');
    await testDatabaseConnection(pending.password);

  } else if (Step === 'finishSecret') {
    // Mark AWSPENDING as AWSCURRENT
    await secretsManager.updateSecretVersionStage({
      SecretId,
      VersionStage: 'AWSCURRENT',
      MoveToVersionId: Token
    }).promise();
  }
};
```

### 3. Azure Key Vault

```javascript
const { SecretClient } = require('@azure/keyvault-secrets');
const { DefaultAzureCredential } = require('@azure/identity');

const credential = new DefaultAzureCredential();
const client = new SecretClient(
  'https://myvault.vault.azure.net',
  credential
);

// Set secret
await client.setSecret('db-password', 'secretpassword');

// Get secret
const secret = await client.getSecret('db-password');
console.log(secret.value);
```

### 4. Google Cloud Secret Manager

```javascript
const {SecretManagerServiceClient} = require('@google-cloud/secret-manager');

const client = new SecretManagerServiceClient();

// Create secret
await client.createSecret({
  parent: 'projects/my-project',
  secretId: 'db-password',
  secret: {
    replication: { automatic: {} }
  }
});

// Add secret version
await client.addSecretVersion({
  parent: 'projects/my-project/secrets/db-password',
  payload: {
    data: Buffer.from('secretpassword', 'utf8')
  }
});

// Access secret
const [version] = await client.accessSecretVersion({
  name: 'projects/my-project/secrets/db-password/versions/latest'
});

const password = version.payload.data.toString('utf8');
```

## Environment Variables (Local Development)

### Using .env Files

```bash
# .env (NOT committed to git)
DATABASE_URL=postgresql://user:password@localhost/mydb
API_KEY=sk_test_abc123
REDIS_URL=redis://localhost:6379
```

```javascript
// Load with dotenv
require('dotenv').config();

const dbUrl = process.env.DATABASE_URL;
const apiKey = process.env.API_KEY;
```

### .gitignore

```gitignore
# Environment files
.env
.env.local
.env.*.local

# Secrets
secrets/
*.key
*.pem
credentials.json
```

### .env.example (Template)

```bash
# .env.example (committed to git as template)
DATABASE_URL=postgresql://user:password@localhost/mydb
API_KEY=your_api_key_here
REDIS_URL=redis://localhost:6379
```

## Encryption at Rest

### Encrypting Secrets in Database

```javascript
const crypto = require('crypto');

const algorithm = 'aes-256-gcm';
const keyLength = 32;

// Master key (stored in KMS or environment)
const masterKey = Buffer.from(process.env.MASTER_KEY, 'hex');

function encrypt(plaintext) {
  const iv = crypto.randomBytes(16);
  const cipher = crypto.createCipheriv(algorithm, masterKey, iv);

  let encrypted = cipher.update(plaintext, 'utf8', 'hex');
  encrypted += cipher.final('hex');

  const authTag = cipher.getAuthTag();

  return {
    iv: iv.toString('hex'),
    encrypted,
    authTag: authTag.toString('hex')
  };
}

function decrypt(encryptedData) {
  const decipher = crypto.createDecipheriv(
    algorithm,
    masterKey,
    Buffer.from(encryptedData.iv, 'hex')
  );

  decipher.setAuthTag(Buffer.from(encryptedData.authTag, 'hex'));

  let decrypted = decipher.update(encryptedData.encrypted, 'hex', 'utf8');
  decrypted += decipher.final('utf8');

  return decrypted;
}

// Store encrypted secret
async function storeSecret(name, value) {
  const encrypted = encrypt(value);

  await db.secrets.create({
    name,
    iv: encrypted.iv,
    data: encrypted.encrypted,
    authTag: encrypted.authTag
  });
}

// Retrieve and decrypt secret
async function getSecret(name) {
  const secret = await db.secrets.findOne({ name });

  return decrypt({
    iv: secret.iv,
    encrypted: secret.data,
    authTag: secret.authTag
  });
}
```

## Secret Rotation

### Automated Secret Rotation

```javascript
class SecretRotator {
  constructor(secretsManager, resourceManager) {
    this.secretsManager = secretsManager;
    this.resourceManager = resourceManager;
  }

  async rotateSecret(secretName) {
    console.log(`Rotating secret: ${secretName}`);

    // 1. Generate new secret
    const newSecret = this.generateNewSecret();

    // 2. Store new secret as pending
    await this.secretsManager.createPendingVersion(secretName, newSecret);

    // 3. Update resource with new secret
    await this.resourceManager.updateCredentials(newSecret);

    // 4. Test new secret
    const testResult = await this.resourceManager.testConnection(newSecret);

    if (!testResult.success) {
      // Rollback
      await this.secretsManager.deletePendingVersion(secretName);
      throw new Error('Secret rotation failed - test failed');
    }

    // 5. Promote pending to current
    await this.secretsManager.promotePendingToCurrent(secretName);

    // 6. Deprecate old secret (keep for grace period)
    await this.secretsManager.deprecateOldVersion(secretName);

    console.log(`Secret rotated successfully: ${secretName}`);
  }

  generateNewSecret() {
    return crypto.randomBytes(32).toString('base64');
  }
}

// Schedule rotation
const cron = require('node-cron');

cron.schedule('0 0 * * 0', async () => { // Weekly
  const rotator = new SecretRotator(secretsManager, databaseManager);
  await rotator.rotateSecret('database-password');
});
```

## Best Practices

### 1. Never Hardcode Secrets

```javascript
// ❌ Bad
const apiKey = 'sk_live_abc123';

// ✅ Good
const apiKey = process.env.API_KEY;

// ✅ Better
const apiKey = await secretsManager.getSecret('api-key');
```

### 2. Use Least Privilege

```javascript
// Vault policy
path "secret/data/myapp/*" {
  capabilities = ["read"]
}

path "database/creds/readonly" {
  capabilities = ["read"]
}

// AWS IAM policy
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "secretsmanager:GetSecretValue",
    "Resource": "arn:aws:secretsmanager:*:*:secret:myapp/*"
  }]
}
```

### 3. Rotate Secrets Regularly

```javascript
// Rotation schedule
const rotationSchedule = {
  'database-password': '30d',
  'api-keys': '90d',
  'jwt-signing-key': '180d',
  'encryption-key': '365d'
};
```

### 4. Audit Access

```javascript
// Log all secret access
async function getSecret(secretName) {
  const secret = await secretsManager.get(secretName);

  await auditLog.create({
    action: 'SECRET_ACCESS',
    secret: secretName,
    user: currentUser.id,
    timestamp: new Date(),
    ipAddress: requestIp
  });

  return secret;
}
```

### 5. Use Short-Lived Credentials

```javascript
// Generate temporary database credentials (15 minutes)
const tempCreds = await vault.read('database/creds/myapp');

// Credentials expire automatically after 15 minutes
```

### 6. Encrypt Secrets in Transit and at Rest

```javascript
// Always use TLS
const vaultClient = vault({
  endpoint: 'https://vault.example.com', // HTTPS
  token: process.env.VAULT_TOKEN
});

// Encrypt at rest
await secretsManager.putSecret('api-key', apiKey, {
  kmsKeyId: 'arn:aws:kms:us-east-1:123456789:key/abc-123'
});
```

## Common Pitfalls

### Pitfall 1: Secrets in Logs

```javascript
// ❌ Bad
console.log('Connecting with password:', password);

// ✅ Good
console.log('Connecting to database...');

// Sanitize logs
function sanitizeLog(message) {
  return message.replace(/password=\S+/gi, 'password=[REDACTED]');
}
```

### Pitfall 2: Secrets in Error Messages

```javascript
// ❌ Bad
catch (err) {
  throw new Error(`Database connection failed: ${dbPassword}`);
}

// ✅ Good
catch (err) {
  throw new Error('Database connection failed');
  // Log separately with redaction
}
```

### Pitfall 3: Secrets in URLs

```javascript
// ❌ Bad
const url = `https://api.example.com/data?apikey=${apiKey}`;

// ✅ Good
const response = await fetch('https://api.example.com/data', {
  headers: {
    'Authorization': `Bearer ${apiKey}`
  }
});
```

## Interview Questions

### Basic
1. What are secrets and why should they not be hardcoded?
2. What is the difference between encryption at rest vs in transit?
3. What is secret rotation and why is it important?
4. Name three secrets management solutions

### Intermediate
5. How does HashiCorp Vault's dynamic secrets feature work?
6. Explain the difference between static and dynamic secrets
7. How would you implement secret rotation for a database password?
8. What are the security considerations for storing secrets in environment variables?

### Advanced
9. Design a secrets management strategy for a microservices platform
10. How would you implement zero-downtime secret rotation?
11. Compare Vault vs AWS Secrets Manager vs environment variables
12. How do you handle secrets in a CI/CD pipeline?

## Further Reading

- [HashiCorp Vault Documentation](https://www.vaultproject.io/docs)
- [AWS Secrets Manager](https://aws.amazon.com/secrets-manager/)
- [OWASP Secrets Management Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html)
- [12-Factor App: Config](https://12factor.net/config)
