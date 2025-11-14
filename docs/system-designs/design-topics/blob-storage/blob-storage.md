# Design a Blob Storage System (Dropbox / Google Drive / OneDrive)

**Companies**: Dropbox, Google Drive, Microsoft OneDrive, Box, iCloud
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a cloud file storage and synchronization service like Dropbox that allows users to store files, sync across devices, share with others, and access from anywhere.

---

## Step 1: Requirements (10 min)

### Functional Requirements

**Core Features** (MVP):
1. **File Upload/Download**: Users can upload and download files
2. **Synchronization**: Real-time sync across multiple devices
3. **File Sharing**: Share files/folders with other users
4. **Version History**: Track file versions, restore previous versions
5. **Offline Access**: Access files without internet connection

**Extended Features**:
6. **Collaboration**: Real-time collaborative editing
7. **File Search**: Search files by name, content, metadata
8. **Preview**: Preview files without downloading
9. **Mobile Apps**: iOS and Android support
10. **Desktop Clients**: Windows, Mac, Linux sync clients

**Prioritize**: Upload, download, sync, and sharing for core design

### Non-Functional Requirements

1. **Scalability**: Handle 500M users, 2+ Exabytes of data
2. **High Availability**: 99.99% uptime
3. **Low Latency**:
   - Metadata operations: <100ms
   - File upload: Dependent on size and bandwidth
   - Sync propagation: <3 seconds
4. **Consistency**: Eventual consistency acceptable for most operations
5. **Durability**: 99.999999999% (11 9's) - no data loss
6. **Security**: Encryption at rest and in transit

### Capacity Estimation

**Assumptions**:
- 500M registered users
- 50M DAU (Daily Active Users)
- Average storage per user: 100 GB (mix of free 2GB and paid 2TB)
- Average file size: 5 MB
- Files per user: ~2,000 files

**Storage**:
```
Raw Storage:
  500M users × 100 GB = 50,000 PB = 50 Exabytes

With Deduplication (50%) + Compression (30%):
  50 EB × 0.5 × 0.7 = 17.5 EB logical storage

With Redundancy (1.5× for erasure coding):
  17.5 EB × 1.5 = 26.25 Exabytes physical storage
```

**Traffic**:
```
Daily Operations:
- Uploads: 50M DAU × 5 uploads/day = 250M uploads
- Downloads: 50M DAU × 20 downloads/day = 1B downloads
- Syncs: 50M DAU × 50 sync checks/day = 2.5B sync requests

QPS:
- Upload QPS: 250M ÷ 86,400 = ~2,900/sec
- Download QPS: 1B ÷ 86,400 = ~11,600/sec
- Sync QPS: 2.5B ÷ 86,400 = ~29,000/sec
- Peak (3×): ~90,000 QPS total
```

**Bandwidth**:
```
Upload: 2,900 uploads/s × 5 MB = ~15 GB/s = 120 Gbps
Download: 11,600 downloads/s × 5 MB = ~58 GB/s = 464 Gbps
Peak: 1.4 Tbps (CDN handles 99% → 14 Gbps origin)
```

---

## Step 2: High-Level Architecture (15 min)

### System Overview

```
                  ┌──────────────────────────┐
                  │  Clients (Desktop/Mobile/Web) │
                  └──────────┬──────────────┘
                             │
                  ┌──────────▼──────────────┐
                  │      CDN (CloudFront)    │
                  │   File Downloads (99%)   │
                  └──────────┬──────────────┘
                             │
                  ┌──────────▼──────────────┐
                  │   Load Balancer (ALB)   │
                  └──────────┬──────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│Upload Service │   │Download Service│   │ Sync Service  │
└───────┬───────┘   └───────┬───────┘   └───────┬───────┘
        │                    │                    │
        └────────────────────┼────────────────────┘
                             │
                  ┌──────────▼──────────────┐
                  │   Metadata Service      │
                  │   (PostgreSQL Sharded)  │
                  └──────────┬──────────────┘
                             │
        ┌────────────────────┼────────────────────┐
        ▼                    ▼                    ▼
┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│Block Storage  │   │  Redis Cache  │   │  Message Queue│
│ (S3/GCS)      │   │               │   │   (Kafka)     │
└───────────────┘   └───────────────┘   └───────────────┘
```

### Key Components

1. **Clients**
   - Desktop: Windows, Mac, Linux sync daemon
   - Mobile: iOS, Android apps
   - Web: Browser-based UI

2. **CDN / Edge**
   - Serve file downloads (99% cache hit)
   - Global distribution
   - DDoS protection

3. **API Gateway**
   - Authentication (JWT, OAuth)
   - Rate limiting
   - Request routing

4. **Core Services**
   - Upload Service: Handle chunked uploads
   - Download Service: Serve files
   - Metadata Service: File/folder metadata
   - Sync Service: Coordinate synchronization
   - Sharing Service: Permission management
   - Version Service: Version control

5. **Storage**
   - Block Storage (S3/GCS): Actual file blocks
   - Metadata DB (PostgreSQL): File metadata, chunks mapping
   - Cache (Redis): Hot metadata, sessions

6. **Message Queue (Kafka)**
   - Async operations
   - Event-driven sync
   - Decoupling services

---

## Step 3: Core Algorithms (20 min)

### 3.1 File Chunking & Deduplication

**Fixed-Size Chunking (Simple)**:
```python
CHUNK_SIZE = 4 * 1024 * 1024  # 4MB

def chunk_file(file_path):
    """Split file into fixed 4MB chunks"""
    chunks = []
    with open(file_path, 'rb') as f:
        index = 0
        while True:
            chunk_data = f.read(CHUNK_SIZE)
            if not chunk_data:
                break
            
            # Calculate SHA-256 hash
            chunk_hash = hashlib.sha256(chunk_data).hexdigest()
            
            chunks.append({
                'index': index,
                'hash': chunk_hash,
                'size': len(chunk_data)
            })
            index += 1
    
    return chunks
```

**Content-Defined Chunking (Better for dedup)**:
```python
def content_defined_chunking(file_data):
    """
    Variable-size chunks using Rabin fingerprint
    Better deduplication when content changes
    """
    chunks = []
    window_size = 48  # Rolling hash window
    avg_chunk_size = 4 * 1024 * 1024  # Target 4MB
    
    # Rabin fingerprint algorithm
    mask = (1 << 13) - 1  # Creates boundary when hash & mask == 0
    
    chunk_start = 0
    for i in range(window_size, len(file_data)):
        # Calculate rolling hash
        hash_value = rabin_hash(file_data[i-window_size:i])
        
        # Check for chunk boundary
        if (hash_value & mask == 0) or (i - chunk_start >= 8*1024*1024):
            chunk = file_data[chunk_start:i]
            chunk_hash = hashlib.sha256(chunk).hexdigest()
            chunks.append({
                'hash': chunk_hash,
                'size': len(chunk),
                'offset': chunk_start
            })
            chunk_start = i
    
    return chunks
```

**Deduplication**:
```python
def upload_with_dedup(file_chunks):
    """
    Upload only chunks that don't exist
    """
    uploaded_chunks = []
    
    for chunk in file_chunks:
        # Check if chunk already exists (query metadata DB)
        if chunk_exists(chunk['hash']):
            # Increment reference count
            increment_ref_count(chunk['hash'])
            uploaded_chunks.append({
                'hash': chunk['hash'],
                'uploaded': False,
                'deduplicated': True
            })
        else:
            # Upload new chunk to S3
            s3_key = f"blocks/{chunk['hash'][:2]}/{chunk['hash']}"
            s3.put_object(Bucket='dropbox-blocks', Key=s3_key, Body=chunk['data'])
            
            # Store chunk metadata
            store_chunk_metadata(chunk['hash'], s3_key, chunk['size'])
            
            uploaded_chunks.append({
                'hash': chunk['hash'],
                'uploaded': True,
                'deduplicated': False
            })
    
    return uploaded_chunks
```

### 3.2 File Upload Flow

```python
def handle_file_upload(user_id, file_name, file_path):
    """
    Complete file upload process
    """
    # 1. Chunk the file
    chunks = chunk_file(file_path)
    
    # 2. Check which chunks already exist (deduplication)
    chunk_hashes = [c['hash'] for c in chunks]
    existing_chunks = check_existing_chunks(chunk_hashes)
    
    # 3. Upload only new chunks (parallel)
    new_chunks = [c for c in chunks if c['hash'] not in existing_chunks]
    upload_chunks_parallel(new_chunks, num_threads=10)
    
    # 4. Create file metadata
    file_id = generate_uuid()
    file_metadata = {
        'file_id': file_id,
        'user_id': user_id,
        'name': file_name,
        'size': sum(c['size'] for c in chunks),
        'chunks': [{'index': c['index'], 'hash': c['hash']} for c in chunks],
        'version': 1,
        'created_at': datetime.now(),
        'modified_at': datetime.now()
    }
    
    # 5. Save metadata to database
    save_file_metadata(file_metadata)
    
    # 6. Publish event for async processing
    kafka.publish('file.uploaded', {
        'file_id': file_id,
        'user_id': user_id,
        'name': file_name,
        'size': file_metadata['size']
    })
    
    return file_id
```

### 3.3 Synchronization Algorithm

**Delta Sync (Efficient)**:
```python
def get_sync_delta(user_id, last_cursor):
    """
    Return changes since last sync cursor
    """
    # Query changes from metadata DB
    changes = db.query("""
        SELECT file_id, action, version, modified_at
        FROM file_changes
        WHERE user_id = ? AND change_id > ?
        ORDER BY change_id ASC
        LIMIT 1000
    """, user_id, last_cursor)
    
    return {
        'cursor': changes[-1]['change_id'] if changes else last_cursor,
        'has_more': len(changes) == 1000,
        'entries': [
            {
                'file_id': c['file_id'],
                'action': c['action'],  # 'created', 'modified', 'deleted'
                'version': c['version'],
                'modified_at': c['modified_at']
            }
            for c in changes
        ]
    }
```

**Conflict Resolution**:
```python
def resolve_conflict(server_version, client_version, file_id):
    """
    Resolve sync conflicts
    """
    # Compare timestamps
    server_modified = server_version['modified_at']
    client_modified = client_version['modified_at']
    
    if abs((server_modified - client_modified).total_seconds()) < 2:
        # Modified within 2 seconds → likely conflict
        # Create conflict copy
        conflict_file_id = create_conflict_copy(file_id, client_version)
        
        return {
            'resolution': 'conflict_copy',
            'winner': 'server',  # Server version kept as main
            'conflict_file_id': conflict_file_id,
            'message': 'Conflict copy created'
        }
    elif server_modified > client_modified:
        return {
            'resolution': 'server_wins',
            'message': 'Server version is newer'
        }
    else:
        return {
            'resolution': 'client_wins',
            'message': 'Client version accepted'
        }
```

---

## Step 4: Database Design (10 min)

### PostgreSQL Schema (Sharded)

```sql
-- Files Table (Sharded by user_id)
CREATE TABLE files (
    file_id UUID PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_folder_id UUID,  -- NULL for root
    name VARCHAR(255) NOT NULL,
    size BIGINT NOT NULL,
    mime_type VARCHAR(100),
    is_folder BOOLEAN DEFAULT FALSE,
    version INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    modified_at TIMESTAMP DEFAULT NOW(),
    deleted_at TIMESTAMP,  -- Soft delete
    is_deleted BOOLEAN DEFAULT FALSE
);

CREATE INDEX idx_files_user ON files(user_id, deleted_at) WHERE deleted_at IS NULL;
CREATE INDEX idx_files_parent ON files(parent_folder_id, deleted_at) WHERE deleted_at IS NULL;

-- Chunks Table (Global or sharded by chunk_hash prefix)
CREATE TABLE chunks (
    chunk_hash VARCHAR(64) PRIMARY KEY,
    size INT NOT NULL,
    storage_location TEXT NOT NULL,  -- S3 key
    reference_count INT DEFAULT 1,
    created_at TIMESTAMP DEFAULT NOW(),
    last_accessed_at TIMESTAMP
);

CREATE INDEX idx_chunks_ref_count ON chunks(reference_count);

-- File-Chunks Mapping
CREATE TABLE file_chunks (
    file_id UUID,
    chunk_index INT,
    chunk_hash VARCHAR(64),
    PRIMARY KEY (file_id, chunk_index)
);

-- File Versions
CREATE TABLE file_versions (
    version_id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    version INT NOT NULL,
    chunks JSON,  -- Array of chunk hashes
    size BIGINT,
    modified_at TIMESTAMP,
    modified_by BIGINT
);

CREATE INDEX idx_versions_file ON file_versions(file_id, version DESC);

-- Sharing
CREATE TABLE shares (
    share_id UUID PRIMARY KEY,
    file_id UUID NOT NULL,
    shared_by BIGINT,
    shared_with BIGINT,  -- NULL for public share
    permission VARCHAR(20),  -- 'read', 'write'
    share_token VARCHAR(64) UNIQUE,  -- For link sharing
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE INDEX idx_shares_file ON shares(file_id);
CREATE INDEX idx_shares_token ON shares(share_token);

-- Sync Cursor (for delta sync)
CREATE TABLE sync_cursors (
    user_id BIGINT,
    device_id VARCHAR(64),
    last_cursor BIGINT,
    last_sync_at TIMESTAMP,
    PRIMARY KEY (user_id, device_id)
);
```

### Redis Cache Schema

```python
# Session
redis.setex(f"session:{token}", 86400, user_id)

# File metadata cache
redis.setex(f"file:{file_id}", 3600, json.dumps(file_metadata))

# User's recent files
redis.zadd(f"recent:{user_id}", {file_id: timestamp})

# Chunk existence (Bloom filter alternative)
redis.setex(f"chunk:{chunk_hash}", 604800, "1")  # 7 days

# Folder structure cache
redis.setex(f"folder:{folder_id}:children", 1800, json.dumps(children))
```

---

## Step 5: APIs (5 min)

### File Operations

```http
# Upload initiation
POST /api/v1/files/upload/init
{
  "name": "document.pdf",
  "size": 10485760,
  "parent_folder_id": "folder-uuid"
}

Response:
{
  "file_id": "file-uuid",
  "upload_session_id": "session-uuid",
  "chunk_size": 4194304
}

# Upload chunk
POST /api/v1/files/upload/chunk
{
  "upload_session_id": "session-uuid",
  "chunk_index": 0,
  "chunk_hash": "sha256-hash",
  "chunk_data": "base64-encoded-data"
}

# Complete upload
POST /api/v1/files/upload/complete
{
  "upload_session_id": "session-uuid"
}

# Download file
GET /api/v1/files/{fileId}/download
# Returns signed S3 URLs for each chunk

# List folder
GET /api/v1/folders/{folderId}/files

# Delete file
DELETE /api/v1/files/{fileId}

# Restore version
POST /api/v1/files/{fileId}/restore/{versionId}
```

### Sync APIs

```http
# Get delta changes
GET /api/v1/sync/delta?cursor=12345

Response:
{
  "cursor": 12350,
  "has_more": false,
  "entries": [
    {
      "file_id": "uuid",
      "action": "modified",
      "version": 2,
      "modified_at": "2025-11-14T10:00:00Z"
    }
  ]
}

# WebSocket for real-time sync
WS /api/v1/sync/ws
→ {"type": "subscribe", "userId": "123"}
← {"type": "file.modified", "fileId": "uuid", "version": 2}
```

---

## Step 6: Advanced Topics (10 min)

### 6.1 Optimizations

**Parallel Upload**:
```python
def upload_file_parallel(file_path, num_threads=10):
    chunks = chunk_file(file_path)
    
    with ThreadPoolExecutor(max_workers=num_threads) as executor:
        futures = [executor.submit(upload_chunk, chunk) for chunk in chunks]
        results = [f.result() for f in futures]
    
    return results
```

**Smart Bandwidth Throttling**:
```python
class BandwidthThrottler:
    def __init__(self, max_bandwidth_mbps=10):
        self.max_bytes_per_sec = max_bandwidth_mbps * 1024 * 1024 / 8
        self.tokens = self.max_bytes_per_sec
        self.last_update = time.time()
    
    def consume(self, bytes_to_send):
        # Token bucket algorithm
        now = time.time()
        elapsed = now - self.last_update
        self.tokens = min(self.max_bytes_per_sec, 
                         self.tokens + elapsed * self.max_bytes_per_sec)
        
        if self.tokens >= bytes_to_send:
            self.tokens -= bytes_to_send
            self.last_update = now
            return bytes_to_send
        else:
            # Throttle
            time.sleep((bytes_to_send - self.tokens) / self.max_bytes_per_sec)
            self.tokens = 0
            self.last_update = time.time()
            return bytes_to_send
```

### 6.2 Security

**Encryption**:
- At rest: AES-256 (S3 server-side encryption)
- In transit: TLS 1.3
- Optional client-side encryption (zero-knowledge)

**Access Control**:
```python
def check_access(user_id, file_id, permission='read'):
    """
    Check if user has permission to access file
    """
    # 1. Check ownership
    file = get_file_metadata(file_id)
    if file['user_id'] == user_id:
        return True
    
    # 2. Check sharing
    share = get_share(file_id, user_id)
    if share and share['permission'] >= permission:
        return True
    
    return False
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (users, storage)?
- Deduplication strategy (file-level or chunk-level)?
- Consistency requirements (strong or eventual)?
- Multi-region support needed?
- Real-time collaboration required?

**Topics to Cover**:
- Chunking strategies (fixed vs content-defined)
- Deduplication algorithm
- Sync algorithm (long-polling vs WebSocket)
- Conflict resolution
- Database sharding
- CDN for downloads

**Common Follow-ups**:
- "How do you handle a 10GB file upload?"
  → Chunking, parallel upload, resumable
- "What if two users edit same file simultaneously?"
  → Conflict detection, create conflict copy, user resolves
- "How do you reduce storage costs?"
  → Deduplication (50%), compression (30%), tiering (50%)

---

## Key Takeaways

1. **Chunking is Critical**: Enables dedup, parallel transfer, resumable uploads
2. **Dedup Saves 50%**: Chunk-level dedup provides best savings
3. **CDN is Mandatory**: 99% of downloads from CDN, 1% origin
4. **Sync is Hard**: Delta sync + conflict resolution is complex
5. **Metadata Matters**: Fast metadata queries = fast UX
6. **Storage Tiering**: Hot/Warm/Cold saves 50% cost

## Further Reading

- [Dropbox: How We've Scaled Dropbox](https://dropbox.tech/)
- [Google Drive Architecture](https://cloud.google.com/architecture)
- [Rabin Fingerprinting for Chunking](https://en.wikipedia.org/wiki/Rabin_fingerprint)
- [Erasure Coding vs Replication](https://blog.min.io/erasure-coding/)
