# Design Collaborative Real-time Editor (Google Docs/Notion)

## Table of Contents
1. [Introduction](#introduction)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [System Architecture](#system-architecture)
5. [Data Models](#data-models)
6. [API Design](#api-design)
7. [Operational Transformation (OT)](#operational-transformation-ot)
8. [CRDT Implementation](#crdt-implementation)
9. [Real-time Synchronization](#real-time-synchronization)
10. [Conflict Resolution](#conflict-resolution)
11. [Presence & Cursors](#presence--cursors)
12. [Version Control](#version-control)
13. [Offline Support](#offline-support)
14. [Security](#security)
15. [Advanced Features](#advanced-features)
16. [Scaling Strategy](#scaling-strategy)

---

## Introduction

Design a **collaborative real-time editor** like Google Docs, Notion, or Figma that supports:
- **Real-time multi-user editing** with sub-100ms latency
- **Conflict-free synchronization** across 100+ simultaneous editors
- **Offline editing** with automatic conflict resolution
- **Version history** with ability to restore any previous state
- **Rich text formatting**, comments, and suggestions
- **Global availability** with 99.99% uptime

### Key Challenges
- **Convergence**: All clients must reach the same final state
- **Causality**: Preserve user intentions despite concurrent edits
- **Low Latency**: Real-time sync under 100ms
- **Conflict Resolution**: Automatic, deterministic conflict handling
- **Offline Support**: Edit without connectivity, sync later
- **Scalability**: Support millions of concurrent users

---

## Requirements

### Functional Requirements
1. **Document Management**
   - Create, read, update, delete documents
   - Organize in folders/workspaces
   - Share with permissions (owner, editor, viewer)
   - Generate shareable links

2. **Rich Text Editing**
   - Text formatting (bold, italic, underline, fonts, colors)
   - Paragraphs, headings, lists (ordered/unordered)
   - Images, videos, embeds
   - Tables, code blocks
   - Links and mentions

3. **Real-time Collaboration**
   - Multiple users editing simultaneously
   - Real-time cursor positions and selections
   - Presence awareness (who's viewing/editing)
   - Conflict-free synchronization

4. **Comments & Suggestions**
   - Inline comments on text
   - Reply threads
   - Suggestion mode (tracked changes)
   - Resolve/delete comments

5. **Version History**
   - Time-travel to any previous version
   - Compare versions (diff view)
   - Restore previous versions
   - Named versions (tags)

6. **Offline Support**
   - Edit documents offline
   - Queue operations locally
   - Sync when reconnected
   - Resolve conflicts automatically

### Non-Functional Requirements
1. **Low Latency**: <100ms for operations to propagate
2. **High Availability**: 99.99% uptime
3. **Scalability**: 10M concurrent users, 100B documents
4. **Consistency**: Strong eventual consistency
5. **Performance**: 10K operations/sec per document
6. **Data Durability**: Zero data loss

### Out of Scope
- Real-time voice/video collaboration (separate feature)
- Advanced image editing (use dedicated tools)
- Database-like features (queries, formulas) - see Notion/Airtable design

---

## Capacity Estimation

Using the **POWER technique** (see [scale-estimation-guide.md](./scale-estimation-guide.md)):

### Assumptions (Google Docs Scale)
- **Total users**: 2 billion
- **Daily Active Users (DAU)**: 100 million
- **Peak concurrent users**: 10 million
- **Documents per user**: 50 on average
- **Operations per minute**: 60 ops/min (1 op/sec)

### Traffic Calculations

**Peak operations per second**:
```
10M concurrent users × 60 ops/min = 600M ops/min
= 10M ops/sec
```

**Document loads per second**:
```
100M DAU × 10 loads/day = 1B loads/day
= 12K loads/sec (average)
= 36K loads/sec (peak, 3× average)
```

### Storage Calculations

**Operations log** (30-day retention):
```
10M ops/sec × 86,400 sec/day × 30 days × 100 bytes/op
= 2.6 PB (with compression: ~850 TB)
```

**Documents**:
```
2B users × 50 docs/user × 50 KB/doc = 5 PB
```

**Total storage**: ~15 PB (including snapshots, media)

### Infrastructure

**WebSocket servers**:
```
10M concurrent users / 50K connections per server = 200 servers
With headroom: 300 servers
```

**Database shards**:
```
100 shards × 3 instances (1 master + 2 replicas) = 300 PostgreSQL instances
```

**See** [scale-estimation-guide.md](./scale-estimation-guide.md) for detailed calculations.

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      CLIENT TIER                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   Web    │  │  Mobile  │  │ Desktop  │  │   SDK    │   │
│  │  Editor  │  │   App    │  │   App    │  │          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                  WebSocket Connection
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                 LOAD BALANCER (ALB)                          │
│              Sticky Sessions by Document ID                  │
└─────────────────────────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  WebSocket   │  │  WebSocket   │  │  WebSocket   │
│  Server 1    │  │  Server 2    │  │  Server N    │
│ (Node.js/Go) │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
         │                 │                 │
         └─────────────────┼─────────────────┘
                           │
                    Redis Pub/Sub
                  (Cross-server broadcast)
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   APPLICATION TIER                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Collab   │  │ Document │  │ Version  │  │ Presence │   │
│  │ Service  │  │ Service  │  │ Control  │  │ Service  │   │
│  │ (OT/CRDT)│  │          │  │          │  │          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  PostgreSQL  │  │    Redis     │  │      S3      │
│   (Sharded)  │  │   (Cache)    │  │  (Snapshots) │
└──────────────┘  └──────────────┘  └──────────────┘

┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│Elasticsearch │  │    Kafka     │  │  Monitoring  │
│   (Search)   │  │  (Events)    │  │ (Prometheus) │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Component Breakdown

#### 1. **Client Tier**
- **Rich Text Editor**: Quill.js, ProseMirror, Slate.js
- **CRDT Library**: Y.js (for CRDT approach) or ShareDB (for OT)
- **WebSocket Client**: Socket.io-client, native WebSocket
- **Local Storage**: IndexedDB for offline support
- **Operation Queue**: Buffer for pending operations

#### 2. **WebSocket Gateway**
- **Technology**: Node.js (Socket.io), Go (gorilla/websocket), uWebSockets.js
- **Responsibilities**:
  - Maintain persistent connections (50K per server)
  - Route operations to collaboration service
  - Broadcast transformed operations to clients
  - Heartbeat/ping-pong for connection health
- **Scaling**: Stateless, horizontally scalable via Redis Pub/Sub

#### 3. **Collaboration Service**
- **Technology**: Node.js, Go, Rust
- **Core Algorithms**:
  - **Operational Transformation (OT)**: Google Docs approach
  - **CRDT (Conflict-free Replicated Data Type)**: Figma/Notion approach
- **Responsibilities**:
  - Transform concurrent operations
  - Assign global sequence numbers
  - Ensure causality and convergence
  - Broadcast transformed ops

#### 4. **Document Service**
- **Technology**: Node.js, Java Spring Boot, Go
- **Responsibilities**:
  - CRUD operations for documents
  - Permissions management (RBAC)
  - Folder hierarchy
  - Export to PDF/DOCX/Markdown

#### 5. **Version Control Service**
- **Responsibilities**:
  - Create snapshots (every 100 operations)
  - Store deltas (operation diffs)
  - Time-travel queries
  - Compare versions

#### 6. **Presence Service**
- **Technology**: Redis + WebSocket
- **Responsibilities**:
  - Track active users per document
  - Real-time cursor positions
  - User colors and metadata
  - Idle detection (5-min timeout)

---

## Data Models

### PostgreSQL Schema

```sql
-- Documents Table
CREATE TABLE documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(500) NOT NULL,
    owner_id UUID REFERENCES users(id),
    folder_id UUID REFERENCES folders(id),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    is_deleted BOOLEAN DEFAULT FALSE,
    is_public BOOLEAN DEFAULT FALSE,
    current_version BIGINT DEFAULT 0,
    INDEX idx_owner (owner_id),
    INDEX idx_folder (folder_id),
    INDEX idx_updated (updated_at)
);

-- Users Table
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255),
    avatar_url VARCHAR(1000),
    created_at TIMESTAMP DEFAULT NOW(),
    last_active_at TIMESTAMP
);

-- Collaborators Table (Permissions)
CREATE TABLE document_collaborators (
    doc_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    role VARCHAR(20) NOT NULL, -- 'owner', 'editor', 'viewer'
    granted_at TIMESTAMP DEFAULT NOW(),
    granted_by UUID REFERENCES users(id),
    PRIMARY KEY (doc_id, user_id),
    INDEX idx_user_docs (user_id, doc_id)
);

-- Operations Log (Sharded by doc_id)
CREATE TABLE operations_log (
    id BIGSERIAL PRIMARY KEY,
    doc_id UUID NOT NULL,
    user_id UUID REFERENCES users(id),
    operation JSONB NOT NULL,
    version BIGINT NOT NULL,
    timestamp TIMESTAMP DEFAULT NOW(),
    INDEX idx_doc_version (doc_id, version),
    INDEX idx_timestamp (doc_id, timestamp)
) PARTITION BY HASH (doc_id);

-- Create 100 partitions for sharding
CREATE TABLE operations_log_0 PARTITION OF operations_log
    FOR VALUES WITH (MODULUS 100, REMAINDER 0);
-- ... (repeat for partitions 1-99)

-- Document Snapshots
CREATE TABLE document_snapshots (
    id BIGSERIAL PRIMARY KEY,
    doc_id UUID NOT NULL,
    version BIGINT NOT NULL,
    content TEXT, -- Full document state (or NULL if stored in S3)
    s3_url VARCHAR(1000), -- S3 path for large snapshots
    size_bytes BIGINT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (doc_id, version),
    INDEX idx_doc_version (doc_id, version DESC)
);

-- Comments
CREATE TABLE comments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    doc_id UUID REFERENCES documents(id) ON DELETE CASCADE,
    user_id UUID REFERENCES users(id),
    parent_comment_id UUID REFERENCES comments(id), -- For replies
    content TEXT NOT NULL,
    position JSONB, -- { "start": 100, "end": 105 }
    is_resolved BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_doc_comments (doc_id, created_at)
);

-- Folders
CREATE TABLE folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    owner_id UUID REFERENCES users(id),
    parent_folder_id UUID REFERENCES folders(id),
    created_at TIMESTAMP DEFAULT NOW()
);
```

### Redis Data Structures

```python
# Document State Cache
doc:{doc_id}:snapshot -> STRING (JSON document state, TTL: 1 hour)

# Operation Buffer (Last 1000 operations)
doc:{doc_id}:ops -> LIST of JSONB operations
# LPUSH for new ops, LTRIM to keep last 1000

# Presence Data
doc:{doc_id}:presence -> HASH
# HSET doc:123:presence user-789 '{"cursor":42,"color":"#FF6B6B"}'
# TTL: 30 seconds, refresh on activity

# Document Version
doc:{doc_id}:version -> STRING (current version number)

# Active Documents (Sorted Set by last activity)
active_docs -> SORTED SET {doc_id: timestamp}

# User Sessions
session:{session_id} -> HASH {user_id, doc_id, ws_server_id}

# Pub/Sub Channels (for WebSocket server communication)
CHANNEL: doc:{doc_id}:operations
```

---

## API Design

### REST API Endpoints

```http
### Authentication
POST   /api/v1/auth/login
POST   /api/v1/auth/register
POST   /api/v1/auth/refresh
POST   /api/v1/auth/logout

### Documents
POST   /api/v1/documents                    # Create document
GET    /api/v1/documents/:id                # Get document
PATCH  /api/v1/documents/:id                # Update metadata
DELETE /api/v1/documents/:id                # Soft delete
GET    /api/v1/documents                    # List user's documents
POST   /api/v1/documents/:id/duplicate      # Duplicate document

### Collaboration
POST   /api/v1/documents/:id/share          # Share with user/team
GET    /api/v1/documents/:id/collaborators  # List collaborators
DELETE /api/v1/documents/:id/collaborators/:userId
PATCH  /api/v1/documents/:id/collaborators/:userId # Update role

### Version History
GET    /api/v1/documents/:id/versions       # List versions
GET    /api/v1/documents/:id/versions/:v    # Get specific version
POST   /api/v1/documents/:id/versions/:v/restore # Restore version
POST   /api/v1/documents/:id/versions/tag   # Create named version

### Comments
POST   /api/v1/documents/:id/comments       # Add comment
GET    /api/v1/documents/:id/comments       # List comments
PATCH  /api/v1/comments/:id                 # Update comment
DELETE /api/v1/comments/:id                 # Delete comment
POST   /api/v1/comments/:id/resolve         # Resolve comment

### Export
GET    /api/v1/documents/:id/export/pdf
GET    /api/v1/documents/:id/export/docx
GET    /api/v1/documents/:id/export/markdown

### Search
GET    /api/v1/search?q=keyword&type=documents
```

### WebSocket Protocol

```javascript
// Client → Server: Edit Operation
{
  "type": "operation",
  "docId": "doc-12345",
  "operation": {
    "type": "insert",
    "position": 42,
    "text": "Hello",
    "format": { "bold": true }
  },
  "clientVersion": 156,
  "userId": "user-789"
}

// Server → Client: Transformed Operation
{
  "type": "operation",
  "docId": "doc-12345",
  "operation": {
    "type": "insert",
    "position": 45, // Transformed position
    "text": "World"
  },
  "version": 157,
  "userId": "user-456",
  "timestamp": "2025-11-14T10:30:00Z"
}

// Client → Server: Cursor Update
{
  "type": "cursor",
  "docId": "doc-12345",
  "position": 50,
  "selection": { "start": 50, "end": 55 }
}

// Server → Clients: Presence Update
{
  "type": "presence",
  "docId": "doc-12345",
  "users": {
    "user-789": {
      "name": "Alice",
      "color": "#FF6B6B",
      "cursor": 50,
      "selection": { "start": 50, "end": 55 }
    }
  }
}

// Server → Client: Acknowledgement
{
  "type": "ack",
  "docId": "doc-12345",
  "version": 157,
  "operationId": "op-abc123"
}
```

---

## Operational Transformation (OT)

### Core Concept

**Operational Transformation** ensures that concurrent operations converge to the same final state by transforming operations based on other concurrent operations.

### OT Properties

1. **TP1 (Transformation Property 1)**: 
   ```
   op1 ∘ transform(op2, op1) = op2 ∘ transform(op1, op2)
   ```
   Applying op1 then transformed op2 equals applying op2 then transformed op1.

2. **TP2 (Transformation Property 2)**: 
   ```
   transform(op3, op1 ∘ op2) = transform(transform(op3, op1), op2)
   ```
   Transforming against a composition equals sequential transforms.

### Operation Types

```javascript
// Insert Operation
{
  type: 'insert',
  position: 10,
  text: 'Hello',
  attributes: { bold: true, color: '#FF0000' }
}

// Delete Operation
{
  type: 'delete',
  position: 5,
  length: 3
}

// Retain Operation (for formatting)
{
  type: 'retain',
  length: 10,
  attributes: { italic: true }
}
```

### Transform Function

```javascript
// OT Transform Function (simplified)
function transform(op1, op2) {
  // Both insert
  if (op1.type === 'insert' && op2.type === 'insert') {
    if (op1.position < op2.position) {
      return [
        op1,
        { ...op2, position: op2.position + op1.text.length }
      ];
    } else if (op1.position > op2.position) {
      return [
        { ...op1, position: op1.position + op2.text.length },
        op2
      ];
    } else {
      // Same position: use tie-breaker (user ID, timestamp)
      if (op1.userId < op2.userId) {
        return [
          op1,
          { ...op2, position: op2.position + op1.text.length }
        ];
      } else {
        return [
          { ...op1, position: op1.position + op2.text.length },
          op2
        ];
      }
    }
  }

  // Insert and Delete
  if (op1.type === 'insert' && op2.type === 'delete') {
    if (op1.position <= op2.position) {
      // Insert before delete: shift delete position right
      return [
        op1,
        { ...op2, position: op2.position + op1.text.length }
      ];
    } else if (op1.position >= op2.position + op2.length) {
      // Insert after delete: shift insert position left
      return [
        { ...op1, position: op1.position - op2.length },
        op2
      ];
    } else {
      // Insert within delete range: complex case
      // Split delete into two parts
      const before = { ...op2, length: op1.position - op2.position };
      const after = {
        ...op2,
        position: op1.position + op1.text.length,
        length: op2.length - before.length
      };
      return [op1, before, after];
    }
  }

  // Delete and Delete
  if (op1.type === 'delete' && op2.type === 'delete') {
    // Overlapping deletes: merge or split based on ranges
    if (op1.position < op2.position) {
      if (op1.position + op1.length <= op2.position) {
        // Non-overlapping: shift op2 left
        return [
          op1,
          { ...op2, position: op2.position - op1.length }
        ];
      } else {
        // Overlapping: complex merging logic
        // (implementation depends on specific requirements)
      }
    }
  }

  // Retain operations (for formatting)
  // ... (similar logic for retain)

  return [op1, op2];
}
```

### Server-Side OT Implementation

```javascript
// Collaboration Service
class OTEngine {
  constructor() {
    this.documents = new Map(); // doc_id -> { version, operations }
  }

  async processOperation(docId, operation, clientVersion) {
    // 1. Get current document state
    const doc = await this.getDocument(docId);
    
    // 2. Transform operation against concurrent operations
    let transformedOp = operation;
    for (let v = clientVersion; v < doc.version; v++) {
      const concurrentOp = await this.getOperation(docId, v);
      [transformedOp, _] = transform(transformedOp, concurrentOp);
    }

    // 3. Assign global version number
    const newVersion = doc.version + 1;
    transformedOp.version = newVersion;

    // 4. Persist operation
    await this.saveOperation(docId, transformedOp);

    // 5. Update document version
    await this.incrementVersion(docId);

    // 6. Broadcast transformed operation to all clients
    await this.broadcast(docId, transformedOp);

    return { success: true, version: newVersion };
  }

  async saveOperation(docId, operation) {
    // Save to PostgreSQL operations_log
    await db.query(
      `INSERT INTO operations_log (doc_id, user_id, operation, version, timestamp)
       VALUES ($1, $2, $3, $4, NOW())`,
      [docId, operation.userId, JSON.stringify(operation), operation.version]
    );

    // Add to Redis operation buffer
    await redis.lpush(
      `doc:${docId}:ops`,
      JSON.stringify(operation)
    );
    await redis.ltrim(`doc:${docId}:ops`, 0, 999); // Keep last 1000

    // Create snapshot every 100 operations
    if (operation.version % 100 === 0) {
      await this.createSnapshot(docId, operation.version);
    }
  }

  async broadcast(docId, operation) {
    // Publish to Redis Pub/Sub for cross-server broadcast
    await redis.publish(
      `doc:${docId}:operations`,
      JSON.stringify(operation)
    );
  }
}
```

---

## CRDT Implementation

### What is CRDT?

**Conflict-free Replicated Data Types** are data structures that automatically resolve conflicts, guaranteeing **strong eventual consistency** without requiring a central server.

### CRDT for Text: YATA (Y.js)

Y.js uses the **YATA (Yet Another Transformation Approach)** algorithm, which assigns a unique identifier to each character.

### Character Identifiers

```javascript
// Each character has a unique ID
{
  id: {
    client: 'user-789',
    clock: 5 // Logical clock (increments per operation)
  },
  char: 'H',
  deleted: false
}
```

### Y.js Implementation

```javascript
// Client-side Y.js
import * as Y from 'yjs'
import { WebsocketProvider } from 'y-websocket'

// Create CRDT document
const ydoc = new Y.Doc()

// Get text type
const ytext = ydoc.getText('content')

// Listen for changes
ytext.observe((event) => {
  console.log('Changes:', event.changes)
})

// Insert text
ytext.insert(0, 'Hello')
ytext.insert(5, ' World')

// Delete text
ytext.delete(0, 5)

// Format text
ytext.format(0, 5, { bold: true })

// Connect to WebSocket server for sync
const wsProvider = new WebsocketProvider(
  'ws://localhost:1234',
  'my-document-id',
  ydoc
)

// Generate update (delta) for syncing
ydoc.on('update', (update) => {
  // Send update to server
  ws.send(Y.encodeStateAsUpdate(ydoc))
})

// Apply update from another client
ws.on('message', (data) => {
  Y.applyUpdate(ydoc, data)
})
```

### Server-Side CRDT (Y.js)

```javascript
// Server for Y.js synchronization
import * as Y from 'yjs'
import { WebSocketServer } from 'ws'

const wss = new WebSocketServer({ port: 1234 })

// Store documents in memory (or Redis)
const docs = new Map()

wss.on('connection', (ws, req) => {
  const docId = req.url.slice(1) // Extract doc ID from URL

  // Get or create Y.Doc
  if (!docs.has(docId)) {
    docs.set(docId, new Y.Doc())
  }
  const ydoc = docs.get(docId)

  // Send current state to new client
  ws.send(Y.encodeStateAsUpdate(ydoc))

  // Handle updates from client
  ws.on('message', (message) => {
    // Apply update to server document
    Y.applyUpdate(ydoc, message)

    // Broadcast to all other clients
    wss.clients.forEach((client) => {
      if (client !== ws && client.readyState === WebSocket.OPEN) {
        client.send(message)
      }
    })

    // Persist update to database
    saveUpdate(docId, message)
  })
})

async function saveUpdate(docId, update) {
  // Save to PostgreSQL or S3
  await db.query(
    `INSERT INTO crdt_updates (doc_id, update, timestamp)
     VALUES ($1, $2, NOW())`,
    [docId, update]
  )
}
```

### OT vs CRDT Trade-offs

| Aspect | OT (Operational Transformation) | CRDT (Y.js) |
|--------|--------------------------------|-------------|
| **Algorithm Complexity** | Complex (transform functions) | Simpler (commutative operations) |
| **Requires Central Server** | Yes (for ordering) | No (peer-to-peer capable) |
| **Memory Overhead** | Low (operations only) | High (metadata per character) |
| **Offline Support** | Difficult | Excellent |
| **Convergence Guarantee** | Requires correct transform | Automatic (mathematical proof) |
| **Performance** | Faster (less overhead) | Slower (more metadata) |
| **Used By** | Google Docs, Etherpad | Figma, Notion, Atom Teletype |

**Recommendation**: Use **OT** for centralized systems (Google Docs), **CRDT** for distributed/offline-first (Notion, Figma).

---

## Real-time Synchronization

### WebSocket Communication Flow

```
Client A                WebSocket Server           Collaboration Service
   |                            |                            |
   | 1. Connect (docId)         |                            |
   |--------------------------->|                            |
   |                            | 2. Authenticate & Join     |
   |                            |--------------------------->|
   |                            |<---------------------------|
   |                            | 3. Send current state      |
   |<---------------------------|                            |
   |                            |                            |
   | 4. User types "H"          |                            |
   | insert("H", pos=0)         |                            |
   |--------------------------->|                            |
   |                            | 5. Transform & persist     |
   |                            |--------------------------->|
   |                            |<---------------------------|
   |                            | 6. Broadcast to clients    |
   |<---------------------------|                            |
Client B                       |                            |
   |<---------------------------|                            |
   | 7. Apply operation         |                            |
```

### Client-Side Implementation

```javascript
class CollaborativeEditor {
  constructor(docId, userId) {
    this.docId = docId
    this.userId = userId
    this.localVersion = 0
    this.pendingOps = []
    this.ws = null
    this.editor = null // Quill.js or ProseMirror instance
  }

  async initialize() {
    // Initialize editor
    this.editor = new Quill('#editor', {
      theme: 'snow',
      modules: {
        toolbar: [
          ['bold', 'italic', 'underline'],
          [{ 'list': 'ordered'}, { 'list': 'bullet' }],
          ['link', 'image']
        ]
      }
    })

    // Connect WebSocket
    await this.connectWebSocket()

    // Listen to local edits
    this.editor.on('text-change', (delta, oldDelta, source) => {
      if (source === 'user') {
        this.handleLocalChange(delta)
      }
    })
  }

  async connectWebSocket() {
    this.ws = new WebSocket(`ws://localhost:8080/docs/${this.docId}`)

    this.ws.onopen = () => {
      console.log('WebSocket connected')
      this.joinDocument()
    }

    this.ws.onmessage = (event) => {
      const message = JSON.parse(event.data)
      this.handleRemoteMessage(message)
    }

    this.ws.onerror = (error) => {
      console.error('WebSocket error:', error)
    }

    this.ws.onclose = () => {
      console.log('WebSocket closed. Reconnecting...')
      setTimeout(() => this.connectWebSocket(), 1000)
    }
  }

  joinDocument() {
    this.ws.send(JSON.stringify({
      type: 'join',
      docId: this.docId,
      userId: this.userId
    }))
  }

  handleLocalChange(delta) {
    // Convert Quill delta to operation
    const operation = this.deltaToOperation(delta)

    // Add to pending operations
    this.pendingOps.push(operation)

    // Send to server
    this.ws.send(JSON.stringify({
      type: 'operation',
      docId: this.docId,
      operation,
      clientVersion: this.localVersion
    }))
  }

  handleRemoteMessage(message) {
    switch (message.type) {
      case 'init':
        // Initial document state
        this.editor.setContents(message.content)
        this.localVersion = message.version
        break

      case 'operation':
        // Remote operation
        this.applyRemoteOperation(message.operation)
        this.localVersion = message.version
        break

      case 'ack':
        // Acknowledgement of our operation
        this.handleAck(message)
        break

      case 'presence':
        // Update presence (cursors, users)
        this.updatePresence(message.users)
        break
    }
  }

  applyRemoteOperation(operation) {
    // Transform against pending operations
    let transformedOp = operation
    for (const pendingOp of this.pendingOps) {
      [transformedOp, _] = transform(transformedOp, pendingOp)
    }

    // Apply to editor (source='api' to avoid triggering text-change)
    const delta = this.operationToDelta(transformedOp)
    this.editor.updateContents(delta, 'api')
  }

  handleAck(message) {
    // Remove acknowledged operation from pending
    this.pendingOps.shift()
    this.localVersion = message.version
  }

  deltaToOperation(delta) {
    // Convert Quill.js delta to our operation format
    // (simplified)
    if (delta.ops[0]?.retain !== undefined) {
      const retain = delta.ops[0].retain
      const op = delta.ops[1]

      if (op.insert) {
        return {
          type: 'insert',
          position: retain,
          text: op.insert,
          attributes: op.attributes || {}
        }
      } else if (op.delete) {
        return {
          type: 'delete',
          position: retain,
          length: op.delete
        }
      }
    }
  }

  operationToDelta(operation) {
    // Convert our operation to Quill.js delta
    if (operation.type === 'insert') {
      return new Delta()
        .retain(operation.position)
        .insert(operation.text, operation.attributes)
    } else if (operation.type === 'delete') {
      return new Delta()
        .retain(operation.position)
        .delete(operation.length)
    }
  }

  updatePresence(users) {
    // Render cursors and selections for other users
    Object.entries(users).forEach(([userId, userData]) => {
      if (userId !== this.userId) {
        this.renderCursor(userId, userData.cursor, userData.color, userData.name)
      }
    })
  }

  renderCursor(userId, position, color, name) {
    // Create cursor element
    const cursor = document.createElement('div')
    cursor.className = 'remote-cursor'
    cursor.style.borderColor = color
    cursor.style.top = /* calculate based on position */
    cursor.style.left = /* calculate based on position */

    const label = document.createElement('div')
    label.textContent = name
    label.style.backgroundColor = color
    cursor.appendChild(label)

    document.getElementById('editor').appendChild(cursor)
  }
}

// Usage
const editor = new CollaborativeEditor('doc-12345', 'user-789')
await editor.initialize()
```

---

## Conflict Resolution

### Conflict Types

1. **Concurrent Edits**: Two users edit same position simultaneously
2. **Insert-Delete Conflicts**: One user inserts, another deletes at overlapping positions
3. **Format Conflicts**: Different formatting applied to same text

### Resolution Strategies

#### 1. **Operational Transformation**
- Automatically transforms operations to preserve intent
- Deterministic based on operation types and positions
- Requires central sequencing

#### 2. **Last-Write-Wins (LWW)**
- Use timestamps to determine winner
- Simple but loses data
- Used as tie-breaker in OT/CRDT

#### 3. **User-Based Priority**
- Document owner > editors > viewers
- Or use alphabetical user ID as tie-breaker

#### 4. **Merge Both Changes**
- Keep both edits (CRDT approach)
- May result in unexpected text
- Example: "H" + "i" at same position → "Hi" or "iH" (sorted by user ID)

### Example: Concurrent Insert Conflict

```
Initial state: "Hello World"

User A inserts "Beautiful " at position 6:
→ "Hello Beautiful World"

User B inserts "Amazing " at position 6 (same time):
→ "Hello Amazing World"

Resolution (OT with user ID tie-breaker):
If A's ID < B's ID:
→ "Hello Beautiful Amazing World"
Otherwise:
→ "Hello Amazing Beautiful World"
```

---

## Presence & Cursors

### Real-time Cursor Tracking

```javascript
// Client sends cursor updates (throttled to 10/sec)
const sendCursorUpdate = throttle((position, selection) => {
  ws.send(JSON.stringify({
    type: 'cursor',
    docId: docId,
    userId: userId,
    position,
    selection
  }))
}, 100) // 10 updates/sec

editor.on('selection-change', (range) => {
  if (range) {
    sendCursorUpdate(range.index, {
      start: range.index,
      end: range.index + range.length
    })
  }
})
```

### Server-Side Presence Management

```javascript
class PresenceService {
  constructor() {
    this.redis = createRedisClient()
  }

  async updatePresence(docId, userId, presenceData) {
    const key = `doc:${docId}:presence`

    // Set user presence with 30s expiry
    await this.redis.hset(
      key,
      userId,
      JSON.stringify({
        ...presenceData,
        lastSeen: Date.now()
      })
    )
    await this.redis.expire(key, 30)

    // Broadcast to all users in document
    await this.broadcastPresence(docId)
  }

  async broadcastPresence(docId) {
    const presence = await this.getPresence(docId)

    await this.redis.publish(
      `doc:${docId}:presence`,
      JSON.stringify({
        type: 'presence',
        users: presence
      })
    )
  }

  async getPresence(docId) {
    const key = `doc:${docId}:presence`
    const presenceData = await this.redis.hgetall(key)

    const users = {}
    for (const [userId, data] of Object.entries(presenceData)) {
      users[userId] = JSON.parse(data)
    }

    return users
  }

  async removeUser(docId, userId) {
    await this.redis.hdel(`doc:${docId}:presence`, userId)
    await this.broadcastPresence(docId)
  }
}
```

### User Color Assignment

```javascript
// Assign unique colors to users
const USER_COLORS = [
  '#FF6B6B', '#4ECDC4', '#45B7D1', '#FFA07A',
  '#98D8C8', '#F7DC6F', '#BB8FCE', '#85C1E2'
]

function getUserColor(userId) {
  // Hash user ID to color index
  const hash = userId.split('').reduce((acc, char) => {
    return acc + char.charCodeAt(0)
  }, 0)
  return USER_COLORS[hash % USER_COLORS.length]
}
```

---

## Version Control

### Snapshot Strategy

**Create snapshot every 100 operations**:
```javascript
async function createSnapshot(docId, version) {
  // Reconstruct document state from operations
  const operations = await db.query(
    `SELECT operation FROM operations_log
     WHERE doc_id = $1 AND version <= $2
     ORDER BY version ASC`,
    [docId, version]
  )

  let content = ''
  for (const { operation } of operations) {
    content = applyOperation(content, JSON.parse(operation))
  }

  // Store snapshot
  if (content.length > 100000) {
    // Large document: store in S3
    const s3Url = await uploadToS3(docId, version, content)
    await db.query(
      `INSERT INTO document_snapshots (doc_id, version, s3_url, size_bytes)
       VALUES ($1, $2, $3, $4)`,
      [docId, version, s3Url, content.length]
    )
  } else {
    // Small document: store in database
    await db.query(
      `INSERT INTO document_snapshots (doc_id, version, content)
       VALUES ($1, $2, $3)`,
      [docId, version, content]
    )
  }
}
```

### Time-Travel (Load Previous Version)

```javascript
async function loadVersion(docId, targetVersion) {
  // Find nearest snapshot before target version
  const snapshot = await db.query(
    `SELECT version, content, s3_url FROM document_snapshots
     WHERE doc_id = $1 AND version <= $2
     ORDER BY version DESC
     LIMIT 1`,
    [docId, targetVersion]
  )

  let content
  if (snapshot.s3_url) {
    content = await downloadFromS3(snapshot.s3_url)
  } else {
    content = snapshot.content
  }

  // Apply operations from snapshot version to target version
  if (snapshot.version < targetVersion) {
    const operations = await db.query(
      `SELECT operation FROM operations_log
       WHERE doc_id = $1 AND version > $2 AND version <= $3
       ORDER BY version ASC`,
      [docId, snapshot.version, targetVersion]
    )

    for (const { operation } of operations) {
      content = applyOperation(content, JSON.parse(operation))
    }
  }

  return content
}
```

---

## Offline Support

### Client-Side Queue

```javascript
class OfflineQueue {
  constructor(docId) {
    this.docId = docId
    this.db = null // IndexedDB
  }

  async init() {
    this.db = await openDB('collaborative-editor', 1, {
      upgrade(db) {
        db.createObjectStore('pending_operations', { keyPath: 'id', autoIncrement: true })
      }
    })
  }

  async queueOperation(operation) {
    await this.db.add('pending_operations', {
      docId: this.docId,
      operation,
      timestamp: Date.now()
    })
  }

  async getPendingOperations() {
    const tx = this.db.transaction('pending_operations', 'readonly')
    const store = tx.objectStore('pending_operations')
    return await store.getAll()
  }

  async clearOperation(id) {
    await this.db.delete('pending_operations', id)
  }

  async syncPendingOperations(ws) {
    const pending = await this.getPendingOperations()

    for (const { id, operation } of pending) {
      // Send to server
      ws.send(JSON.stringify({
        type: 'operation',
        docId: this.docId,
        operation
      }))

      // Wait for acknowledgement
      await new Promise((resolve) => {
        ws.once('message', (message) => {
          if (message.type === 'ack' && message.operationId === operation.id) {
            this.clearOperation(id)
            resolve()
          }
        })
      })
    }
  }
}
```

---

## Security

### 1. **Authentication & Authorization**

```javascript
// JWT-based authentication
const jwt = require('jsonwebtoken')

function generateToken(userId, docId, role) {
  return jwt.sign(
    { userId, docId, role },
    process.env.JWT_SECRET,
    { expiresIn: '1h' }
  )
}

function verifyDocumentAccess(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1]

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET)
    req.user = decoded

    // Check permission
    if (!hasPermission(decoded.userId, decoded.docId, decoded.role)) {
      return res.status(403).json({ error: 'Forbidden' })
    }

    next()
  } catch (error) {
    res.status(401).json({ error: 'Unauthorized' })
  }
}
```

### 2. **Operation Validation**

```javascript
function validateOperation(operation, userId, docId) {
  // Check user has write permission
  if (!hasWritePermission(userId, docId)) {
    throw new Error('User does not have write permission')
  }

  // Validate operation structure
  if (!operation.type || !['insert', 'delete', 'retain'].includes(operation.type)) {
    throw new Error('Invalid operation type')
  }

  // Validate position
  if (typeof operation.position !== 'number' || operation.position < 0) {
    throw new Error('Invalid position')
  }

  // Validate text length (prevent DoS)
  if (operation.type === 'insert' && operation.text.length > 10000) {
    throw new Error('Text too long')
  }

  // Sanitize text (prevent XSS)
  if (operation.type === 'insert') {
    operation.text = sanitizeHTML(operation.text)
  }

  return true
}
```

### 3. **Rate Limiting**

```javascript
const rateLimit = require('express-rate-limit')

const operationLimiter = rateLimit({
  windowMs: 60 * 1000, // 1 minute
  max: 1000, // 1000 operations per minute per user
  keyGenerator: (req) => req.user.userId,
  handler: (req, res) => {
    res.status(429).json({ error: 'Too many operations. Please slow down.' })
  }
})

app.post('/api/v1/operations', operationLimiter, handleOperation)
```

### 4. **Audit Logging**

```javascript
async function logOperation(docId, userId, operation) {
  await db.query(
    `INSERT INTO audit_log (doc_id, user_id, operation_type, operation, ip_address, timestamp)
     VALUES ($1, $2, $3, $4, $5, NOW())`,
    [docId, userId, operation.type, JSON.stringify(operation), req.ip]
  )
}
```

---

## Advanced Features

### 1. **Comments & Suggestions**

```javascript
// Comment data structure
{
  id: 'comment-123',
  docId: 'doc-456',
  userId: 'user-789',
  content: 'Great point!',
  position: { start: 100, end: 105 }, // Character range
  resolved: false,
  createdAt: '2025-11-14T10:00:00Z',
  replies: [
    {
      id: 'reply-abc',
      userId: 'user-111',
      content: 'Thanks!',
      createdAt: '2025-11-14T10:05:00Z'
    }
  ]
}
```

### 2. **Suggestions (Track Changes)**

```javascript
// Suggestion operation (doesn't apply immediately)
{
  type: 'suggestion',
  operation: {
    type: 'insert',
    position: 50,
    text: 'revised text'
  },
  userId: 'user-789',
  status: 'pending' // pending, accepted, rejected
}

// Accept suggestion
function acceptSuggestion(suggestionId) {
  const suggestion = getSuggestion(suggestionId)
  applyOperation(suggestion.operation)
  markSuggestionAccepted(suggestionId)
}

// Reject suggestion
function rejectSuggestion(suggestionId) {
  markSuggestionRejected(suggestionId)
}
```

### 3. **Rich Media Embeds**

```javascript
// Image embed operation
{
  type: 'insert',
  position: 100,
  embed: {
    type: 'image',
    url: 'https://cdn.example.com/image.jpg',
    width: 800,
    height: 600
  }
}

// Video embed
{
  type: 'insert',
  position: 200,
  embed: {
    type: 'video',
    provider: 'youtube',
    videoId: 'dQw4w9WgXcQ'
  }
}
```

### 4. **Mentions & Notifications**

```javascript
// Mention operation
{
  type: 'insert',
  position: 50,
  text: '@Alice',
  attributes: {
    mention: {
      userId: 'user-789',
      name: 'Alice'
    }
  }
}

// Send notification when mentioned
async function handleMention(docId, mentionedUserId, mentionerUserId) {
  await sendNotification({
    userId: mentionedUserId,
    type: 'mention',
    message: `${mentionerUserId} mentioned you in a document`,
    docId,
    link: `/documents/${docId}`
  })
}
```

---

## Scaling Strategy

### 1. **Horizontal Scaling**

| Component | Scaling Strategy |
|-----------|------------------|
| **WebSocket Servers** | Stateless, scale with load balancer + Redis Pub/Sub |
| **Collaboration Service** | Stateless, partition by document ID |
| **Document Service** | Stateless, standard REST API scaling |
| **Database** | Shard by document ID (100 shards) |
| **Redis** | Cluster mode (6 nodes: 3 masters, 3 replicas) |

### 2. **Database Sharding**

```python
# Shard by document ID
def get_shard(doc_id):
    hash_value = hashlib.md5(doc_id.encode()).hexdigest()
    shard_number = int(hash_value, 16) % NUM_SHARDS
    return f"shard_{shard_number}"

# Route queries to correct shard
connection = get_db_connection(get_shard(doc_id))
```

### 3. **Caching Strategy**

```
Level 1: Browser Cache (Service Worker)
  ↓ miss
Level 2: CDN (CloudFront) for static assets
  ↓ miss
Level 3: Redis (document snapshots, operation buffer)
  ↓ miss
Level 4: Database (PostgreSQL)
```

### 4. **Multi-Region Deployment**

```
Primary Region (us-east-1):
  - Full read-write capability
  - Master databases

Secondary Regions (us-west-2, eu-central-1, ap-southeast-1):
  - Read replicas (async replication)
  - Cache servers
  - WebSocket servers (route to primary for writes)
```

### 5. **Auto-Scaling Rules**

```yaml
WebSocket Servers:
  - Scale up: CPU > 70% for 2 minutes
  - Scale down: CPU < 30% for 10 minutes
  - Min instances: 50
  - Max instances: 500

Collaboration Service:
  - Scale up: Queue depth > 1000
  - Scale down: Queue depth < 100
  - Min instances: 20
  - Max instances: 200
```

---

## Summary

This collaborative editor design covers:

1. ✅ **Architecture**: WebSocket gateway, collaboration service, OT/CRDT engines
2. ✅ **Algorithms**: Operational Transformation (Google Docs) and CRDT (Figma/Notion)
3. ✅ **Real-time Sync**: Sub-100ms latency for 10M concurrent users
4. ✅ **Conflict Resolution**: Automatic, deterministic conflict handling
5. ✅ **Offline Support**: Queue operations locally, sync on reconnect
6. ✅ **Presence**: Real-time cursor tracking and user awareness
7. ✅ **Version Control**: Time-travel to any previous state
8. ✅ **Security**: JWT auth, operation validation, rate limiting
9. ✅ **Scalability**: 100 database shards, 300 WebSocket servers

### Key Takeaways

- **Choose OT for centralized systems** (Google Docs) with central sequencing server
- **Choose CRDT for distributed/offline-first** (Figma, Notion) with peer-to-peer support
- **Use snapshots** (every 100 ops) to avoid replaying millions of operations
- **Throttle presence updates** (10/sec) to reduce bandwidth
- **Shard by document ID** for even load distribution
- **Cache aggressively** (85% hit rate) to reduce database load

### Performance Benchmarks

- **Operation Latency**: <100ms (P95)
- **Document Load**: <200ms
- **Concurrent Editors**: 100 per document
- **Throughput**: 10M ops/sec (peak)
- **Storage**: 15 PB (100B documents)
- **Cost**: ~$1.23M/month for 100M DAU

---

**Next Steps**: Check out [architecture.html](./architecture.html) for interactive diagrams, [flow-diagram.html](./flow-diagram.html) for OT/CRDT workflows, [scale-estimation-guide.md](./scale-estimation-guide.md) for capacity planning, and [tech-stack.md](./tech-stack.md) for industry solutions!
