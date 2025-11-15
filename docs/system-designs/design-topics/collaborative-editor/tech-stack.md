# Tech Stack & Industry Solutions for Collaborative Editors

## Table of Contents
1. [Industry Tech Stacks](#industry-tech-stacks)
2. [OT Libraries & Frameworks](#ot-libraries--frameworks)
3. [CRDT Libraries](#crdt-libraries)
4. [Rich Text Editors](#rich-text-editors)
5. [WebSocket Solutions](#websocket-solutions)
6. [Infrastructure & DevOps](#infrastructure--devops)
7. [Monitoring & Observability](#monitoring--observability)
8. [Cloud Services](#cloud-services)
9. [Complete Deployment Example](#complete-deployment-example)

---

## Industry Tech Stacks

### 1. **Google Docs**

**Architecture**:
```
Technology Stack:
├── Frontend: JavaScript (Google Closure), TypeScript
├── Backend: C++, Java, Python
├── Collaboration: Operational Transformation (proprietary)
├── Storage: Colossus (Google's distributed file system)
├── Database: Bigtable, Spanner
├── Cache: Memcached
├── Infrastructure: Google Cloud Platform (GCP)
└── CDN: Google Cloud CDN
```

**Key Technologies**:
- **Custom OT Algorithm**: Proprietary implementation optimized for scale
- **Realtime API**: WebSocket-based with custom binary protocol
- **Offline Support**: Service workers with IndexedDB
- **Version History**: Delta-based snapshots every N operations
- **Comments**: Linked to character positions (anchor-based)

**Scale**:
- 2+ billion users
- 100M+ DAU
- 5+ trillion documents

**Infrastructure**:
- **Multi-region**: Global deployment across 35+ regions
- **Kubernetes** (GKE) for container orchestration
- **Load Balancing**: Global load balancer with anycast
- **Monitoring**: Cloud Monitoring (Stackdriver)

---

### 2. **Notion**

**Architecture**:
```
Technology Stack:
├── Frontend: React, TypeScript
├── Backend: Node.js, Kubernetes
├── Collaboration: CRDT-based (custom implementation)
├── Database: PostgreSQL (sharded), Redis
├── Real-time: WebSocket (Socket.io)
├── Storage: AWS S3
├── Search: Elasticsearch
└── Infrastructure: AWS
```

**Key Technologies**:
- **Block-based Editor**: Everything is a block (text, image, database)
- **CRDT Synchronization**: Custom CRDT for blocks
- **Offline-First**: Full offline support with conflict resolution
- **Database-as-Content**: Tables are first-class citizens
- **API**: REST API + WebSocket for real-time

**Unique Features**:
- **Databases within documents**: Notion-specific innovation
- **Templates**: Reusable page templates
- **Relations**: Link blocks across pages

**Infrastructure**:
- **AWS EKS** for Kubernetes
- **PostgreSQL** sharded by workspace
- **Redis** for caching and Pub/Sub
- **CloudFront** CDN

---

### 3. **Figma**

**Architecture**:
```
Technology Stack:
├── Frontend: C++ (compiled to WebAssembly), TypeScript
├── Backend: Rust, Go
├── Collaboration: CRDT (custom implementation)
├── Real-time: WebSocket (custom protocol)
├── Rendering: WebGL (multiplayer cursors, canvas)
├── Database: PostgreSQL, RocksDB
├── Cache: Redis
├── Storage: AWS S3
└── Infrastructure: AWS
```

**Key Technologies**:
- **WebAssembly**: C++ rendering engine for performance
- **Operational CRDT**: Custom CRDT for vector graphics
- **Multiplayer Cursors**: Real-time cursor tracking with interpolation
- **LiveGraph**: Proprietary real-time collaboration protocol
- **Vector Networks**: Intelligent path simplification

**Performance**:
- **Sub-50ms latency**: Optimized WebSocket protocol
- **60fps rendering**: WebGL-based canvas
- **100+ simultaneous editors**: Per file

**Infrastructure**:
- **AWS** with multi-region deployment
- **RocksDB** for fast local state
- **Custom CDN**: Optimized for large design files

---

### 4. **Microsoft Office 365 (Word Online)**

**Architecture**:
```
Technology Stack:
├── Frontend: TypeScript, React, Office UI Fabric
├── Backend: .NET Core, C#
├── Collaboration: OT-based (custom)
├── Storage: SharePoint, OneDrive (Azure Blob)
├── Database: Azure SQL Database, Cosmos DB
├── Cache: Azure Cache for Redis
├── Real-time: SignalR (WebSocket abstraction)
└── Infrastructure: Microsoft Azure
```

**Key Technologies**:
- **Office JavaScript API**: Extensibility via add-ins
- **Co-authoring**: OT-based with presence
- **AutoSave**: Every 2 seconds to OneDrive
- **Version History**: Integrated with SharePoint

**Infrastructure**:
- **Azure Kubernetes Service (AKS)**
- **Azure Front Door**: Global load balancer
- **Cosmos DB**: Multi-region replication

---

### 5. **Dropbox Paper**

**Architecture**:
```
Technology Stack:
├── Frontend: React, TypeScript
├── Backend: Python (Django), Go
├── Collaboration: OT (ShareDB)
├── Database: MySQL (sharded), PostgreSQL
├── Cache: Memcached
├── Storage: Dropbox Storage (S3-like)
└── Infrastructure: AWS
```

**Key Technologies**:
- **ShareDB**: Open-source OT library
- **Markdown-based**: Rich text stored as Markdown
- **Dropbox Integration**: Native file sync
- **Comments**: Thread-based discussions

---

### 6. **Etherpad**

**Architecture**:
```
Technology Stack (Open Source):
├── Frontend: JavaScript (jQuery)
├── Backend: Node.js
├── Collaboration: Easysync (custom OT)
├── Database: SQLite, PostgreSQL, MySQL
├── Plugins: 400+ community plugins
└── Deployment: Docker, PM2
```

**Key Technologies**:
- **Easysync Protocol**: Lightweight OT algorithm
- **Changeset**: Efficient delta representation
- **Open Source**: MIT license
- **Self-hostable**: Run on your own servers

**Use Cases**:
- Education (quick collaborative notes)
- Hackathons
- Open-source projects

---

## OT Libraries & Frameworks

### 1. **ShareDB**

```bash
npm install sharedb sharedb-mongo
```

```javascript
// Server
const ShareDB = require('sharedb')
const WebSocket = require('ws')
const http = require('http')

const backend = new ShareDB()
const connection = backend.connect()

// Create document
const doc = connection.get('documents', 'doc-123')
await doc.fetch()

if (doc.type === null) {
  doc.create({ content: '' }, 'rich-text')
}

// Subscribe to changes
doc.subscribe((err) => {
  if (err) throw err
  console.log('Subscribed to document')
})

doc.on('op', (op, source) => {
  console.log('Operation received:', op)
})

// Submit operation
doc.submitOp([{ p: ['content', 0], si: 'Hello' }], (err) => {
  if (err) throw err
})
```

**Pros**:
- Mature, battle-tested
- Multiple database adapters (MongoDB, PostgreSQL, Redis)
- Built-in presence support
- Rich-text type (Quill Delta compatible)

**Cons**:
- Node.js only (server-side)
- Less performant than pure CRDT for offline-first

**Use Cases**: Dropbox Paper, CodeSandbox, various startups

---

### 2. **Automerge**

```bash
npm install @automerge/automerge
```

```javascript
import * as Automerge from '@automerge/automerge'

// Create document
let doc = Automerge.init()

// Make changes
doc = Automerge.change(doc, 'Add hello', (doc) => {
  doc.content = 'Hello World'
})

// Generate changes (for sending to other peers)
const changes = Automerge.getChanges(Automerge.init(), doc)

// Apply changes from another peer
let otherDoc = Automerge.init()
otherDoc = Automerge.applyChanges(otherDoc, changes)

// Merge documents
const merged = Automerge.merge(doc, otherDoc)
```

**Pros**:
- Pure CRDT (automatic conflict resolution)
- Immutable data structures
- Time-travel (full history)
- Peer-to-peer friendly

**Cons**:
- Higher memory overhead
- Slower for real-time typing (better for structured data)

**Use Cases**: Collaborative apps with offline-first requirements

---

### 3. **Yjs**

```bash
npm install yjs y-websocket
```

```javascript
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

// Create Y.Doc
const ydoc = new Y.Doc()

// Get shared types
const ytext = ydoc.getText('content')
const yarray = ydoc.getArray('items')
const ymap = ydoc.getMap('metadata')

// Insert text
ytext.insert(0, 'Hello ')
ytext.insert(6, 'World')

// Format text
ytext.format(0, 5, { bold: true })

// Listen to changes
ytext.observe((event) => {
  console.log('Changes:', event.changes)
})

// Connect to WebSocket server
const wsProvider = new WebsocketProvider(
  'ws://localhost:1234',
  'my-room',
  ydoc
)

// Sync with other clients
ydoc.on('update', (update) => {
  // Send update to other clients
  // Y.applyUpdate(otherDoc, update)
})
```

**Pros**:
- Fastest CRDT implementation (10-100× faster than Automerge)
- Excellent TypeScript support
- Rich ecosystem (editor bindings, providers)
- Low memory overhead
- Built-in garbage collection

**Cons**:
- Less intuitive API than Automerge
- Requires understanding of CRDT concepts

**Use Cases**: Figma-like apps, collaborative IDEs, real-time apps

**Editor Bindings**:
- `y-quill`: Quill.js binding
- `y-prosemirror`: ProseMirror binding
- `y-monaco`: Monaco Editor (VS Code) binding
- `y-codemirror`: CodeMirror binding

---

## CRDT Libraries

### Comparison: Automerge vs Yjs

| Feature | Automerge | Yjs |
|---------|-----------|-----|
| **Performance** | Moderate | Excellent (10-100× faster) |
| **Memory** | High (full history) | Low (with GC) |
| **API** | Simple, immutable | Complex, mutable |
| **TypeScript** | Good | Excellent |
| **Offline Support** | Excellent | Excellent |
| **Garbage Collection** | Manual | Automatic |
| **Use Case** | Structured data | Real-time text editing |

**Recommendation**: Use **Yjs** for real-time text editors, **Automerge** for structured data with time-travel requirements.

---

## Rich Text Editors

### 1. **Quill.js**

```bash
npm install quill
```

```javascript
import Quill from 'quill'

const quill = new Quill('#editor', {
  theme: 'snow',
  modules: {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'],
      ['blockquote', 'code-block'],
      [{ 'header': 1 }, { 'header': 2 }],
      [{ 'list': 'ordered'}, { 'list': 'bullet' }],
      [{ 'indent': '-1'}, { 'indent': '+1' }],
      [{ 'size': ['small', false, 'large', 'huge'] }],
      [{ 'color': [] }, { 'background': [] }],
      [{ 'align': [] }],
      ['link', 'image', 'video']
    ]
  }
})

// Listen to changes
quill.on('text-change', (delta, oldDelta, source) => {
  if (source === 'user') {
    console.log('User edit:', delta)
  }
})

// Get contents
const contents = quill.getContents()

// Set contents
quill.setContents([
  { insert: 'Hello ' },
  { insert: 'World', attributes: { bold: true } }
])
```

**Pros**:
- Easy to use
- Delta format (OT-friendly)
- Extensible via modules
- Good documentation

**Cons**:
- Limited nested structure
- Performance issues with large documents (>100KB)

**Used By**: Dropbox Paper, Slack, Slab

---

### 2. **ProseMirror**

```bash
npm install prosemirror-state prosemirror-view prosemirror-schema-basic
```

```javascript
import { EditorState } from 'prosemirror-state'
import { EditorView } from 'prosemirror-view'
import { Schema, DOMParser } from 'prosemirror-model'
import { schema as basicSchema } from 'prosemirror-schema-basic'

const state = EditorState.create({
  schema: basicSchema,
  doc: DOMParser.fromSchema(basicSchema).parse(document.querySelector('#content'))
})

const view = new EditorView(document.querySelector('#editor'), {
  state,
  dispatchTransaction(transaction) {
    const newState = view.state.apply(transaction)
    view.updateState(newState)
    
    // Send changes to collaboration backend
    if (transaction.docChanged) {
      sendToServer(transaction.steps)
    }
  }
})
```

**Pros**:
- Highly customizable (schema-based)
- Excellent for complex documents
- Strong TypeScript support
- Collaborative editing built-in (via y-prosemirror)

**Cons**:
- Steeper learning curve
- More boilerplate code

**Used By**: New York Times, Atlassian, GitLab

---

### 3. **Slate.js**

```bash
npm install slate slate-react
```

```javascript
import { createEditor } from 'slate'
import { Slate, Editable, withReact } from 'slate-react'

const editor = useMemo(() => withReact(createEditor()), [])

const [value, setValue] = useState([
  {
    type: 'paragraph',
    children: [{ text: 'Hello World' }]
  }
])

return (
  <Slate editor={editor} value={value} onChange={setValue}>
    <Editable />
  </Slate>
)
```

**Pros**:
- React-first design
- Fully customizable
- Modern architecture
- JSON document model

**Cons**:
- Smaller ecosystem than Quill/ProseMirror
- Still evolving (breaking changes)

**Used By**: GitBook, Coda

---

### 4. **TipTap**

```bash
npm install @tiptap/react @tiptap/starter-kit
```

```javascript
import { useEditor, EditorContent } from '@tiptap/react'
import StarterKit from '@tiptap/starter-kit'
import Collaboration from '@tiptap/extension-collaboration'
import * as Y from 'yjs'

const ydoc = new Y.Doc()

const editor = useEditor({
  extensions: [
    StarterKit,
    Collaboration.configure({
      document: ydoc
    })
  ],
  content: '<p>Hello World!</p>'
})

return <EditorContent editor={editor} />
```

**Pros**:
- Built on ProseMirror (stable foundation)
- Excellent Yjs integration
- Rich extension ecosystem
- Modern API

**Cons**:
- Paid pro extensions (mentions, comments, etc.)

**Used By**: GitHub (discussions), Slite

---

## WebSocket Solutions

### 1. **Socket.io**

```bash
npm install socket.io socket.io-client
```

**Server**:
```javascript
const io = require('socket.io')(3000)

io.on('connection', (socket) => {
  socket.on('join-document', (docId) => {
    socket.join(docId)
    
    // Send current document state
    socket.emit('init', getDocumentState(docId))
  })

  socket.on('operation', (data) => {
    // Broadcast to room
    socket.to(data.docId).emit('operation', data)
  })
})
```

**Client**:
```javascript
import io from 'socket.io-client'

const socket = io('http://localhost:3000')

socket.on('connect', () => {
  socket.emit('join-document', 'doc-123')
})

socket.on('operation', (data) => {
  applyOperation(data.operation)
})
```

**Pros**:
- Automatic reconnection
- Fallback to long-polling
- Room-based broadcasting
- Large ecosystem

**Cons**:
- Higher overhead than plain WebSocket
- Protocol overhead

---

### 2. **uWebSockets.js**

```bash
npm install uNetworking/uWebSockets.js#v20.34.0
```

```javascript
const uWS = require('uWebSockets.js')

const app = uWS.App()
  .ws('/*', {
    open: (ws) => {
      console.log('Client connected')
    },
    message: (ws, message, isBinary) => {
      const data = JSON.parse(Buffer.from(message).toString())
      
      // Broadcast to all clients
      app.publish('doc-' + data.docId, message)
    },
    close: (ws) => {
      console.log('Client disconnected')
    }
  })
  .listen(3000, (token) => {
    console.log('Server running on port 3000')
  })
```

**Pros**:
- 10× faster than Socket.io
- Lower memory usage
- High concurrency (1M+ connections)

**Cons**:
- Lower-level API
- Smaller ecosystem

---

## Infrastructure & DevOps

### Kubernetes Deployment

**Namespace & ConfigMap**:
```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: collaborative-editor

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: collaborative-editor
data:
  DATABASE_URL: "postgresql://postgres:5432/documents"
  REDIS_URL: "redis://redis-service:6379"
  JWT_SECRET: "your-secret-key"
```

**WebSocket Server Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: websocket-server
  namespace: collaborative-editor
spec:
  replicas: 10
  selector:
    matchLabels:
      app: websocket
  template:
    metadata:
      labels:
        app: websocket
    spec:
      containers:
      - name: websocket
        image: myregistry/websocket-server:latest
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: app-config
        resources:
          requests:
            memory: "1Gi"
            cpu: "500m"
          limits:
            memory: "2Gi"
            cpu: "1"
        livenessProbe:
          httpGet:
            path: /health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: websocket-service
  namespace: collaborative-editor
spec:
  type: LoadBalancer
  selector:
    app: websocket
  ports:
  - port: 80
    targetPort: 8080
  sessionAffinity: ClientIP  # Sticky sessions
```

**Collaboration Service**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: collaboration-service
  namespace: collaborative-editor
spec:
  replicas: 5
  selector:
    matchLabels:
      app: collaboration
  template:
    metadata:
      labels:
        app: collaboration
    spec:
      containers:
      - name: collaboration
        image: myregistry/collaboration-service:latest
        ports:
        - containerPort: 3000
        envFrom:
        - configMapRef:
            name: app-config
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
```

**PostgreSQL StatefulSet**:
```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: collaborative-editor
spec:
  serviceName: postgres
  replicas: 3
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_DB
          value: documents
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-secret
              key: password
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 100Gi
```

**Redis Deployment**:
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: collaborative-editor
spec:
  replicas: 3
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command:
        - redis-server
        - --appendonly yes
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
```

**HorizontalPodAutoscaler**:
```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: websocket-hpa
  namespace: collaborative-editor
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: websocket-server
  minReplicas: 10
  maxReplicas: 100
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
```

---

### Terraform Infrastructure (AWS)

```hcl
# main.tf
terraform {
  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 5.0"
    }
  }
}

provider "aws" {
  region = var.aws_region
}

# VPC
resource "aws_vpc" "main" {
  cidr_block           = "10.0.0.0/16"
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = {
    Name = "collaborative-editor-vpc"
  }
}

# EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = "collaborative-editor-cluster"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.28"

  vpc_config {
    subnet_ids = aws_subnet.public[*].id
  }
}

# EKS Node Group
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "app-nodes"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.public[*].id

  instance_types = ["c5.2xlarge"]

  scaling_config {
    desired_size = 20
    max_size     = 100
    min_size     = 10
  }
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  identifier        = "collaborative-editor-db"
  engine            = "postgres"
  engine_version    = "15.3"
  instance_class    = "db.r6g.2xlarge"
  allocated_storage = 1000
  storage_type      = "gp3"
  iops              = 12000

  db_name  = "documents"
  username = "postgres"
  password = var.db_password

  multi_az               = true
  publicly_accessible    = false
  backup_retention_period = 7
}

# ElastiCache Redis
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "collaborative-editor-redis"
  replication_group_description = "Redis for collaborative editor"
  engine                     = "redis"
  engine_version             = "7.0"
  node_type                  = "cache.r6g.xlarge"
  num_cache_clusters         = 3
  parameter_group_name       = "default.redis7"
  port                       = 6379
  automatic_failover_enabled = true
}

# S3 Bucket for Snapshots
resource "aws_s3_bucket" "snapshots" {
  bucket = "collaborative-editor-snapshots-${var.environment}"

  tags = {
    Name = "snapshots-bucket"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "snapshots" {
  bucket = aws_s3_bucket.snapshots.id

  rule {
    id     = "archive-old-snapshots"
    status = "Enabled"

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 90
      storage_class = "GLACIER"
    }

    expiration {
      days = 365
    }
  }
}

# CloudFront Distribution
resource "aws_cloudfront_distribution" "main" {
  origin {
    domain_name = aws_s3_bucket.snapshots.bucket_regional_domain_name
    origin_id   = "S3-snapshots"
  }

  enabled             = true
  default_root_object = "index.html"

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-snapshots"

    forwarded_values {
      query_string = false
      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 3600
    max_ttl                = 86400
  }

  restrictions {
    geo_restriction {
      restriction_type = "none"
    }
  }

  viewer_certificate {
    cloudfront_default_certificate = true
  }
}
```

---

## Monitoring & Observability

### Prometheus Configuration

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'websocket-servers'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - collaborative-editor
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: websocket

  - job_name: 'collaboration-service'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - collaborative-editor
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: collaboration

rule_files:
  - '/etc/prometheus/rules/*.yaml'
```

**Alerting Rules**:
```yaml
groups:
  - name: collaborative_editor_alerts
    interval: 30s
    rules:
      - alert: HighOperationLatency
        expr: histogram_quantile(0.95, operation_latency_seconds) > 0.1
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High operation latency"
          description: "P95 latency is {{ $value }}s (threshold: 0.1s)"

      - alert: HighConcurrentUsers
        expr: active_users > 15000000
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Very high concurrent users"
          description: "{{ $value }} concurrent users (near capacity)"

      - alert: DatabaseReplicationLag
        expr: pg_replication_lag_seconds > 60
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "High database replication lag"
```

---

## Cloud Services

### AWS Services

| Service | Use Case |
|---------|----------|
| **EKS** | Kubernetes orchestration |
| **RDS PostgreSQL** | Primary database (sharded) |
| **ElastiCache Redis** | Caching and Pub/Sub |
| **S3** | Document snapshots, media |
| **CloudFront** | CDN for static assets |
| **ALB** | Application load balancer |
| **CloudWatch** | Monitoring and logging |

### GCP Services

| Service | Use Case |
|---------|----------|
| **GKE** | Kubernetes |
| **Cloud SQL** | Managed PostgreSQL |
| **Memorystore** | Redis |
| **Cloud Storage** | Object storage |
| **Cloud CDN** | Content delivery |
| **Cloud Spanner** | Globally distributed SQL (if needed) |

---

## Complete Deployment Example

### Docker Compose (Development)

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: documents
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  redis:
    image: redis:7-alpine
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data

  websocket:
    build: ./websocket-server
    ports:
      - "8080:8080"
    environment:
      DATABASE_URL: postgresql://postgres:postgres@postgres:5432/documents
      REDIS_URL: redis://redis:6379
    depends_on:
      - postgres
      - redis

  collaboration:
    build: ./collaboration-service
    ports:
      - "3000:3000"
    environment:
      DATABASE_URL: postgresql://postgres:postgres@postgres:5432/documents
      REDIS_URL: redis://redis:6379
    depends_on:
      - postgres
      - redis

  frontend:
    build: ./frontend
    ports:
      - "80:80"
    depends_on:
      - websocket

volumes:
  postgres_data:
  redis_data:
```

---

## CI/CD Pipeline

### GitHub Actions

```yaml
name: Deploy Collaborative Editor

on:
  push:
    branches: [main]

jobs:
  build-and-deploy:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3

      - name: Configure AWS credentials
        uses: aws-actions/configure-aws-credentials@v2
        with:
          aws-access-key-id: ${{ secrets.AWS_ACCESS_KEY_ID }}
          aws-secret-access-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          aws-region: us-east-1

      - name: Login to Amazon ECR
        id: login-ecr
        uses: aws-actions/amazon-ecr-login@v1

      - name: Build and push WebSocket image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/websocket-server:$IMAGE_TAG ./websocket-server
          docker push $ECR_REGISTRY/websocket-server:$IMAGE_TAG

      - name: Build and push Collaboration service
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/collaboration-service:$IMAGE_TAG ./collaboration-service
          docker push $ECR_REGISTRY/collaboration-service:$IMAGE_TAG

      - name: Update kubeconfig
        run: |
          aws eks update-kubeconfig --name collaborative-editor-cluster --region us-east-1

      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/websocket-server websocket=$ECR_REGISTRY/websocket-server:$IMAGE_TAG -n collaborative-editor
          kubectl set image deployment/collaboration-service collaboration=$ECR_REGISTRY/collaboration-service:$IMAGE_TAG -n collaborative-editor
          kubectl rollout status deployment/websocket-server -n collaborative-editor
          kubectl rollout status deployment/collaboration-service -n collaborative-editor
```

---

## Summary

This tech stack guide covers:

1. ✅ **Industry Solutions**: Google Docs, Notion, Figma, Microsoft Word Online
2. ✅ **OT Libraries**: ShareDB, custom implementations
3. ✅ **CRDT Libraries**: Yjs (fastest), Automerge (immutable)
4. ✅ **Rich Text Editors**: Quill.js, ProseMirror, Slate.js, TipTap
5. ✅ **WebSocket Solutions**: Socket.io, uWebSockets.js
6. ✅ **Infrastructure**: Kubernetes, Terraform, Docker
7. ✅ **Monitoring**: Prometheus, Grafana
8. ✅ **Cloud**: AWS, GCP services

### Recommended Stack for Production

```
Frontend: React + TipTap + Yjs
Backend: Node.js + PostgreSQL (sharded) + Redis
Real-time: uWebSockets.js (high performance)
Collaboration: Yjs (CRDT) for offline-first, ShareDB (OT) for centralized
Orchestration: Kubernetes (EKS/GKE)
Monitoring: Prometheus + Grafana
Infrastructure as Code: Terraform
```

**Estimated Cost** (100M DAU):
- Compute: $300K/month (EKS nodes)
- Database: $600K/month (sharded PostgreSQL)
- Redis: $5K/month
- Storage (S3): $225K/month
- Bandwidth: $50K/month
- **Total: ~$1.2M/month**

---

**Next Steps**: Check out [collaborative-editor.md](./collaborative-editor.md) for complete system design, [architecture.html](./architecture.html) for interactive diagrams, or [scale-estimation-guide.md](./scale-estimation-guide.md) for capacity planning!
