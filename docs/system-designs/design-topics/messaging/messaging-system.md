# Design a Messaging System (WhatsApp / Messenger)

**Companies**: Meta (WhatsApp, Messenger), Telegram, Signal, WeChat
**Difficulty**: Advanced
**Time**: 60 minutes

## Problem Statement

Design a real-time messaging system like WhatsApp that supports:
- 1-on-1 chat
- Group chat
- Media sharing (images, videos, audio)
- Online presence
- Message delivery and read receipts

---

## Step 1: Requirements (10 min)

### Functional Requirements

1. **1-on-1 Messaging**: Send text messages between two users
2. **Group Messaging**: Support group chats (up to 256 members)
3. **Media Sharing**: Send images, videos, audio files
4. **Online Status**: Show user online/offline status
5. **Message Status**: Delivery and read receipts (✓, ✓✓, ✓✓ blue)
6. **Push Notifications**: Notify offline users
7. **Message History**: Persist and sync message history

**Prioritize**: Focus on 1-on-1 messaging and group chat for MVP

### Non-Functional Requirements

1. **Low Latency**: <100ms message delivery
2. **High Availability**: 99.99% uptime
3. **Scalable**: Handle billions of users
4. **Real-Time**: WebSocket-based persistent connections
5. **End-to-End Encryption**: Signal Protocol
6. **Eventual Consistency**: Acceptable for message history

### Capacity Estimation

**Assumptions**:
- 500M Daily Active Users (DAU)
- 50 messages per user per day
- 30% group messages (avg 10 members)
- 60% text, 30% images, 10% video/audio
- 100M concurrent connections
- 90 days message retention

**Traffic Load**:
```
Messages/day: 500M × 50 = 25 Billion messages/day
Avg QPS: 25B ÷ 100K = 250K messages/sec
Peak QPS: 250K × 3 = 750K messages/sec

With group fan-out:
- 1-on-1 (70%): 175K deliveries/sec
- Groups (30%): 75K × 10 = 750K deliveries/sec
- Total deliveries: 925K/sec at peak
```

**Storage**:
```
Message size:
- Text: 1 KB (content + metadata)
- Image: 500 KB (compressed)
- Video: 2 MB (short clips)

Daily storage:
- Text (60%): 15B × 1KB = 15 TB/day
- Images (30%): 7.5B × 500KB = 3,750 TB/day
- Video (10%): 2.5B × 2MB = 5,000 TB/day
Total: ~8,800 TB/day ≈ 9 TB/day

90 days: 9 TB × 90 = 810 TB ≈ 1 PB
```

**WebSocket Connections**:
```
Concurrent connections: 100M
Memory per connection: 10 KB
Total RAM: 100M × 10KB = 1 TB

Servers needed (100K connections/server):
100M ÷ 100K = 1,000 WebSocket servers
```

**Bandwidth**:
```
Peak text: 750K QPS × 1KB = 750 MB/sec
Peak media: 750K × 40% × 1MB = 300 GB/sec
Total: ~300 GB/sec (needs CDN!)
```

---

## Step 2: Architecture (20 min)

### High-Level Design

```
┌─────────────────────────────────────────────────────────┐
│                  Clients (Mobile/Web)                    │
│          iOS  •  Android  •  Web  •  Desktop             │
└────────────────────────┬────────────────────────────────┘
                         │ WebSocket / HTTP
                         ▼
┌─────────────────────────────────────────────────────────┐
│                    Load Balancer                         │
│              (Sticky sessions for WebSocket)             │
└────────────────────────┬────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
┌──────────────────┐      ┌──────────────────┐
│ WebSocket Server │      │ WebSocket Server │
│    (Node.js)     │      │    (Node.js)     │
│ 100K connections │      │ 100K connections │
└────────┬─────────┘      └────────┬─────────┘
         │                         │
         └──────────┬──────────────┘
                    ▼
         ┌─────────────────────┐
         │   API Services      │
         │   (Microservices)   │
         │                     │
         │ • Message Service   │
         │ • Group Service     │
         │ • User Service      │
         │ • Presence Service  │
         │ • Notification Svc  │
         └──────────┬──────────┘
                    │
         ┌──────────┴──────────┐
         ▼                     ▼
┌─────────────────┐  ┌─────────────────┐
│  Redis Cache    │  │  Message Queue  │
│  • Presence     │  │     (Kafka)     │
│  • Recent msgs  │  │                 │
└─────────────────┘  └─────────────────┘
         │                     │
         └──────────┬──────────┘
                    ▼
         ┌─────────────────────────────┐
         │      Data Layer             │
         │                             │
         │ • Cassandra (Messages)      │
         │ • PostgreSQL (Users/Groups) │
         │ • S3 (Media files)          │
         └─────────────────────────────┘
```

### Components

**1. WebSocket Gateway**:
- Maintains persistent connections (100K per server)
- Handles real-time message delivery
- Manages connection lifecycle (connect, disconnect, heartbeat)
- Routes messages to appropriate services
- Technology: Node.js, Go, or Erlang

**2. Message Service**:
- Validates and processes messages
- Applies end-to-end encryption
- Stores messages in database
- Publishes events to message queue
- Handles delivery logic

**3. Group Service**:
- Manages group membership
- Handles group message fan-out
- Enforces group policies (max members, permissions)

**4. Presence Service**:
- Tracks online/offline status
- Manages last seen timestamps
- Handles typing indicators
- Uses Redis with TTL

**5. Notification Service**:
- Sends push notifications to offline users
- Integrates with FCM (Android) and APNS (iOS)
- Queues notifications for delivery

**6. User Service**:
- User registration and authentication
- Profile management
- Contact list management

---

## Step 3: Data Model (10 min)

### Database Choice

**Messages: Cassandra (NoSQL)**
- Time-series data (messages ordered by time)
- High write throughput (millions/sec)
- Horizontal scalability
- Multi-DC replication
- Perfect for append-only workload

**Users/Groups: PostgreSQL (SQL)**
- ACID transactions for user operations
- Complex queries for contacts, groups
- Smaller dataset, more structured

### Schema Design

```sql
-- CASSANDRA: Messages Table
CREATE TABLE messages (
    chat_id UUID,              -- Partition key
    message_id TIMEUUID,       -- Clustering key (time-ordered)
    sender_id UUID,
    content TEXT,
    encrypted_content BLOB,
    type TEXT,                 -- 'text', 'image', 'video', 'audio'
    status TEXT,               -- 'sent', 'delivered', 'read'
    media_url TEXT,
    created_at TIMESTAMP,
    PRIMARY KEY (chat_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);

-- CASSANDRA: User Inbox (Denormalized)
CREATE TABLE user_chats (
    user_id UUID,              -- Partition key
    chat_id UUID,
    last_message_time TIMESTAMP,  -- Clustering key
    last_message TEXT,
    unread_count INT,
    participants SET<UUID>,
    PRIMARY KEY (user_id, last_message_time, chat_id)
) WITH CLUSTERING ORDER BY (last_message_time DESC);

-- POSTGRESQL: Users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    phone_number VARCHAR(20) UNIQUE NOT NULL,
    username VARCHAR(50) UNIQUE,
    email VARCHAR(255),
    public_key TEXT,           -- For E2E encryption
    profile_picture_url TEXT,
    status_message TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP
);

CREATE INDEX idx_users_phone ON users(phone_number);
CREATE INDEX idx_users_username ON users(username);

-- POSTGRESQL: Contacts
CREATE TABLE contacts (
    user_id UUID,
    contact_id UUID,
    contact_name VARCHAR(100),
    added_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, contact_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (contact_id) REFERENCES users(id)
);

-- POSTGRESQL: Groups
CREATE TABLE groups (
    id UUID PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    creator_id UUID,
    group_picture_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    max_members INT DEFAULT 256,
    FOREIGN KEY (creator_id) REFERENCES users(id)
);

-- POSTGRESQL: Group Members
CREATE TABLE group_members (
    group_id UUID,
    user_id UUID,
    role TEXT,                 -- 'admin', 'member'
    joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, user_id),
    FOREIGN KEY (group_id) REFERENCES groups(id),
    FOREIGN KEY (user_id) REFERENCES users(id)
);

CREATE INDEX idx_group_members_user ON group_members(user_id);
```

---

## Step 4: APIs (10 min)

### WebSocket API

```javascript
// WebSocket Connection
ws://api.messaging.com/ws?auth_token=<JWT>

// Client → Server Events
{
  "type": "send_message",
  "data": {
    "chat_id": "uuid",
    "content": "Hello!",
    "type": "text",
    "encrypted": true,
    "timestamp": "2025-11-14T10:30:00Z"
  }
}

{
  "type": "typing_indicator",
  "data": {
    "chat_id": "uuid",
    "is_typing": true
  }
}

{
  "type": "read_receipt",
  "data": {
    "chat_id": "uuid",
    "message_id": "uuid"
  }
}

// Server → Client Events
{
  "type": "new_message",
  "data": {
    "message_id": "uuid",
    "chat_id": "uuid",
    "sender_id": "uuid",
    "content": "Hi there!",
    "timestamp": "2025-11-14T10:30:05Z",
    "status": "delivered"
  }
}

{
  "type": "message_delivered",
  "data": {
    "message_id": "uuid",
    "delivered_at": "2025-11-14T10:30:01Z"
  }
}

{
  "type": "message_read",
  "data": {
    "message_id": "uuid",
    "read_at": "2025-11-14T10:31:00Z"
  }
}
```

### REST API (for HTTP clients)

```http
# Send Message
POST /api/v1/messages
Authorization: Bearer <JWT>
Content-Type: application/json

Request:
{
  "chat_id": "uuid",
  "recipient_id": "uuid",
  "content": "Hello!",
  "type": "text",
  "encrypted_content": "base64..."
}

Response: 201 Created
{
  "message_id": "uuid",
  "status": "sent",
  "timestamp": "2025-11-14T10:30:00Z"
}

# Get Messages (Pagination)
GET /api/v1/chats/{chat_id}/messages?limit=50&before=<message_id>

Response: 200 OK
{
  "messages": [
    {
      "message_id": "uuid",
      "sender_id": "uuid",
      "content": "Hello!",
      "type": "text",
      "timestamp": "2025-11-14T10:30:00Z",
      "status": "read"
    },
    ...
  ],
  "next_cursor": "uuid"
}

# Create Group
POST /api/v1/groups
{
  "name": "Team Chat",
  "description": "Project discussion",
  "member_ids": ["uuid1", "uuid2", "uuid3"]
}

Response: 201 Created
{
  "group_id": "uuid",
  "name": "Team Chat",
  "members": [...],
  "created_at": "2025-11-14T10:30:00Z"
}

# Get User Status
GET /api/v1/users/{user_id}/status

Response: 200 OK
{
  "user_id": "uuid",
  "status": "online",
  "last_seen": "2025-11-14T10:29:00Z"
}
```

---

## Step 5: Core Algorithms & Flows

### 1. Send Message Flow

```python
def send_message(sender_id, recipient_id, content, type="text"):
    # 1. Validate sender and recipient
    if not user_exists(recipient_id):
        return error("Recipient not found")

    # 2. Generate unique message ID (time-ordered)
    message_id = generate_timeuuid()

    # 3. Create chat if doesn't exist
    chat_id = get_or_create_chat(sender_id, recipient_id)

    # 4. Apply E2E encryption
    encrypted_content = encrypt_with_recipient_key(content, recipient_id)

    # 5. Store message in database
    message = {
        'message_id': message_id,
        'chat_id': chat_id,
        'sender_id': sender_id,
        'content': content,
        'encrypted_content': encrypted_content,
        'type': type,
        'status': 'sent',
        'created_at': now()
    }
    db.cassandra.insert('messages', message)

    # 6. Update sender's inbox
    update_user_inbox(sender_id, chat_id, message)

    # 7. Check if recipient is online
    is_online = redis.exists(f"user:{recipient_id}:online")

    if is_online:
        # 8a. Recipient online: Push via WebSocket
        connection = get_websocket_connection(recipient_id)
        connection.send({
            'type': 'new_message',
            'data': message
        })

        # Update status to delivered
        update_message_status(message_id, 'delivered')
    else:
        # 8b. Recipient offline: Queue push notification
        queue.publish('notifications', {
            'user_id': recipient_id,
            'message': f"New message from {get_username(sender_id)}",
            'data': message
        })

    # 9. Publish event for analytics
    kafka.publish('message.sent', {
        'message_id': message_id,
        'sender_id': sender_id,
        'recipient_id': recipient_id,
        'timestamp': now()
    })

    return message
```

### 2. Group Message Flow

```python
def send_group_message(sender_id, group_id, content):
    # 1. Validate group membership
    if not is_group_member(sender_id, group_id):
        return error("Not a group member")

    # 2. Fetch group members
    members = get_group_members(group_id)

    # 3. Create message
    message_id = generate_timeuuid()
    message = {
        'message_id': message_id,
        'chat_id': group_id,
        'sender_id': sender_id,
        'content': content,
        'type': 'text',
        'status': 'sent',
        'created_at': now()
    }

    # 4. Store once in database
    db.cassandra.insert('messages', message)

    # 5. Fan-out to all members (parallel)
    delivery_tasks = []
    for member_id in members:
        if member_id == sender_id:
            continue  # Don't send to self

        task = async_deliver_to_member(member_id, group_id, message)
        delivery_tasks.append(task)

    # 6. Wait for all deliveries (with timeout)
    await asyncio.gather(*delivery_tasks, timeout=5)

    return message

async def async_deliver_to_member(member_id, group_id, message):
    # Update member's inbox
    update_user_inbox(member_id, group_id, message)

    # Check if member is online
    if redis.exists(f"user:{member_id}:online"):
        connection = get_websocket_connection(member_id)
        connection.send({
            'type': 'new_message',
            'data': message
        })
    else:
        # Queue push notification
        queue.publish('notifications', {
            'user_id': member_id,
            'message': f"New message in {get_group_name(group_id)}",
            'data': message
        })
```

### 3. Presence Management

```python
def handle_user_connection(user_id, connection):
    # 1. Store WebSocket connection
    connection_pool[user_id] = connection

    # 2. Set online status in Redis (5 min TTL)
    redis.setex(f"user:{user_id}:online", 300, "1")

    # 3. Update last seen
    redis.set(f"user:{user_id}:last_seen", now())

    # 4. Notify contacts (throttled)
    contacts = get_user_contacts(user_id)
    for contact_id in contacts[:50]:  # Limit to avoid thundering herd
        if redis.exists(f"user:{contact_id}:online"):
            notify_status_change(contact_id, user_id, "online")

    # 5. Setup heartbeat
    start_heartbeat(user_id, connection)

def handle_user_disconnection(user_id):
    # 1. Remove WebSocket connection
    connection_pool.pop(user_id, None)

    # 2. Remove online status (or let it expire)
    redis.delete(f"user:{user_id}:online")

    # 3. Update last seen
    redis.set(f"user:{user_id}:last_seen", now())

    # 4. Notify contacts
    contacts = get_user_contacts(user_id)
    for contact_id in contacts[:50]:
        if redis.exists(f"user:{contact_id}:online"):
            notify_status_change(contact_id, user_id, "offline")

def heartbeat(user_id):
    # Extend TTL on heartbeat
    redis.expire(f"user:{user_id}:online", 300)
```

### 4. Message Delivery Guarantees

```python
def ensure_message_delivery(message_id, recipient_id):
    # At-least-once delivery pattern

    # 1. Try WebSocket delivery
    try:
        connection = get_websocket_connection(recipient_id)
        connection.send(message)

        # 2. Wait for ACK with timeout
        ack = await wait_for_ack(message_id, timeout=5)

        if ack:
            update_message_status(message_id, 'delivered')
            return True
    except (ConnectionError, TimeoutError):
        pass

    # 3. Fallback: Queue for retry
    queue.publish('message.retry', {
        'message_id': message_id,
        'recipient_id': recipient_id,
        'retry_count': 0
    })

    # 4. Send push notification
    send_push_notification(recipient_id, message_id)

    return False

def retry_message_delivery(message_id, recipient_id, retry_count):
    max_retries = 5

    if retry_count >= max_retries:
        # Give up, mark as failed
        update_message_status(message_id, 'failed')
        return

    # Exponential backoff
    delay = 2 ** retry_count  # 2, 4, 8, 16, 32 seconds
    time.sleep(delay)

    # Retry delivery
    success = ensure_message_delivery(message_id, recipient_id)

    if not success:
        queue.publish('message.retry', {
            'message_id': message_id,
            'recipient_id': recipient_id,
            'retry_count': retry_count + 1
        })
```

---

## Step 6: Optimizations & Trade-offs (20 min)

### 1. WebSocket Connection Management

**Challenge**: Maintain 100M concurrent connections

**Solution**:
```python
# Connection pooling with consistent hashing
def route_to_websocket_server(user_id):
    # Use consistent hashing for sticky routing
    server_index = hash(user_id) % num_servers
    server = websocket_servers[server_index]
    return server

# Connection metadata in Redis
def store_connection_metadata(user_id, server_id):
    redis.hset(f"connection:{user_id}", {
        'server_id': server_id,
        'connected_at': now(),
        'last_heartbeat': now()
    })

# Graceful shutdown
def shutdown_websocket_server(server_id):
    connections = get_connections_on_server(server_id)

    for connection in connections:
        # Send reconnect message
        connection.send({
            'type': 'reconnect',
            'reason': 'server_shutdown',
            'reconnect_url': get_available_server()
        })

        # Wait for client to disconnect
        connection.close(code=1012, reason="Service Restart")
```

**Benefits**:
- Sticky sessions for connection stability
- Graceful shutdown without message loss
- Horizontal scaling

### 2. Message Ordering

**Challenge**: Guarantee message order in distributed system

**Solution**:
```sql
-- Use TIMEUUID for chronological ordering
CREATE TABLE messages (
    chat_id UUID,
    message_id TIMEUUID,  -- Embedded timestamp
    ...
    PRIMARY KEY (chat_id, message_id)
) WITH CLUSTERING ORDER BY (message_id DESC);
```

```python
# TIMEUUID generation (time-based UUID v1)
import uuid
message_id = uuid.uuid1()  # Includes timestamp + randomness

# Messages naturally ordered by creation time
# Even across different servers/datacenters
```

**Benefits**:
- No coordination needed between servers
- Chronological order guaranteed
- Conflict-free in multi-DC setup

### 3. Read/Unread Tracking

**Challenge**: Track unread counts efficiently

**Solution**:
```python
# Denormalized unread count in user_chats
UPDATE user_chats
SET unread_count = unread_count + 1,
    last_message_time = ?,
    last_message = ?
WHERE user_id = ? AND chat_id = ?

# Reset on read
UPDATE user_chats
SET unread_count = 0
WHERE user_id = ? AND chat_id = ?

# Cache in Redis for online users
def increment_unread(user_id, chat_id):
    redis.hincrby(f"user:{user_id}:unread", chat_id, 1)
```

**Benefits**:
- O(1) unread count retrieval
- No need to scan messages table
- Fast inbox rendering

### 4. Media Handling

**Challenge**: Store and deliver billions of media files

**Solution**:
```python
# Upload flow
def upload_media(user_id, file):
    # 1. Generate unique file ID
    file_id = generate_uuid()

    # 2. Compress/resize (for images)
    if file.type == 'image':
        file = compress_image(file, quality=0.8)
        thumbnail = create_thumbnail(file, size=200)

    # 3. Upload to S3
    s3_key = f"media/{user_id}/{file_id}"
    s3.upload(s3_key, file)

    # 4. Upload thumbnail
    if thumbnail:
        s3.upload(f"{s3_key}_thumb", thumbnail)

    # 5. Generate CDN URL (CloudFront)
    media_url = f"https://cdn.messaging.com/{s3_key}"

    # 6. Return URL for message
    return {
        'media_url': media_url,
        'thumbnail_url': f"{media_url}_thumb",
        'size': file.size,
        'mime_type': file.mime_type
    }

# Message with media
{
  "message_id": "uuid",
  "type": "image",
  "media_url": "https://cdn.messaging.com/media/...",
  "thumbnail_url": "https://cdn.messaging.com/media/..._thumb",
  "caption": "Check this out!"
}
```

**Optimization**:
- Images: Compress to 80% quality, generate thumbnails
- Videos: Transcode to multiple bitrates (adaptive streaming)
- Use CDN for 99% of media traffic
- Lazy load: Send thumbnail first, full media on demand

### 5. Group Message Optimization

**Challenge**: Deliver to 256 members efficiently

**Solution**:
```python
# Store message once, reference multiple times
def send_group_message_optimized(sender_id, group_id, content):
    # 1. Store message once
    message_id = generate_timeuuid()
    db.cassandra.insert('messages', {
        'chat_id': group_id,
        'message_id': message_id,
        'sender_id': sender_id,
        'content': content
    })

    # 2. Fan-out only metadata (not full message)
    members = get_group_members(group_id)

    for member_id in members:
        # Store lightweight reference in user's inbox
        db.cassandra.insert('user_chats', {
            'user_id': member_id,
            'chat_id': group_id,
            'last_message_time': now(),
            'last_message': content[:50],  # Preview only
            'unread_count': 'unread_count + 1'  # Counter update
        })

    # 3. Notify online members
    online_members = [m for m in members if is_online(m)]
    batch_notify(online_members, {
        'type': 'new_message',
        'chat_id': group_id,
        'message_id': message_id
    })
```

**Benefits**:
- Storage: 99% reduction (1 message vs 256 copies)
- Faster writes
- Consistent data

### 6. Typing Indicators

**Challenge**: Real-time typing status without overwhelming system

**Solution**:
```python
# Ephemeral data in Redis with short TTL
def set_typing_indicator(user_id, chat_id, is_typing):
    key = f"typing:{chat_id}:{user_id}"

    if is_typing:
        redis.setex(key, 5, "1")  # 5 second TTL
    else:
        redis.delete(key)

    # Notify only online participants
    participants = get_chat_participants(chat_id)
    online_participants = [p for p in participants if is_online(p)]

    for participant_id in online_participants:
        if participant_id != user_id:
            notify(participant_id, {
                'type': 'typing_indicator',
                'chat_id': chat_id,
                'user_id': user_id,
                'is_typing': is_typing
            })

# Throttle typing events on client
def throttle_typing_events():
    last_sent = 0

    def on_keypress():
        now = time.now()
        if now - last_sent > 3:  # Send at most every 3 seconds
            send_typing_indicator(True)
            last_sent = now
```

**Benefits**:
- Low overhead (Redis, short TTL)
- Auto-cleanup (TTL expiration)
- Throttled to prevent spam

---

## Step 7: Advanced Considerations

### End-to-End Encryption

**Signal Protocol** (used by WhatsApp, Signal):

```python
# Key exchange (on registration)
def register_user(user_id):
    # Generate identity key pair
    identity_private_key = generate_private_key()
    identity_public_key = derive_public_key(identity_private_key)

    # Generate pre-keys
    pre_keys = [generate_pre_key() for _ in range(100)]

    # Upload to server
    upload_keys(user_id, identity_public_key, pre_keys)

# Encrypt message
def encrypt_message(sender_id, recipient_id, plaintext):
    # 1. Fetch recipient's public key
    recipient_public_key = fetch_public_key(recipient_id)

    # 2. Establish shared secret (ECDH)
    shared_secret = ecdh(sender_private_key, recipient_public_key)

    # 3. Derive encryption key
    encryption_key = kdf(shared_secret)

    # 4. Encrypt with AES-256
    ciphertext = aes256_encrypt(plaintext, encryption_key)

    # 5. Add HMAC for integrity
    hmac = generate_hmac(ciphertext, encryption_key)

    return {
        'ciphertext': ciphertext,
        'hmac': hmac
    }

# Decrypt message (on client)
def decrypt_message(encrypted_message, private_key):
    # 1. Derive decryption key
    decryption_key = kdf(private_key)

    # 2. Verify HMAC
    if not verify_hmac(encrypted_message['hmac'], decryption_key):
        raise IntegrityError()

    # 3. Decrypt
    plaintext = aes256_decrypt(encrypted_message['ciphertext'], decryption_key)

    return plaintext
```

**Benefits**:
- End-to-end: Server cannot read messages
- Forward secrecy: Past messages secure even if key compromised
- Authenticity: HMAC prevents tampering

### Offline Message Sync

```python
def sync_messages_on_reconnect(user_id, last_sync_time):
    # 1. Fetch all chats
    chats = get_user_chats(user_id)

    # 2. For each chat, fetch new messages
    new_messages = []
    for chat in chats:
        messages = db.cassandra.query('''
            SELECT * FROM messages
            WHERE chat_id = ?
            AND message_id > minTimeuuid(?)
            ORDER BY message_id ASC
        ''', chat.chat_id, last_sync_time)

        new_messages.extend(messages)

    # 3. Sort by time
    new_messages.sort(key=lambda m: m.created_at)

    # 4. Batch send to client
    batch_size = 100
    for i in range(0, len(new_messages), batch_size):
        batch = new_messages[i:i+batch_size]
        send_batch(user_id, batch)

    return len(new_messages)
```

### Multi-Device Support

```python
# Link multiple devices
def link_device(user_id, device_id, device_type):
    # 1. Generate device-specific keys
    device_keys = generate_device_keys()

    # 2. Store device metadata
    db.insert('user_devices', {
        'user_id': user_id,
        'device_id': device_id,
        'device_type': device_type,  # 'mobile', 'web', 'desktop'
        'public_key': device_keys.public,
        'registered_at': now()
    })

    # 3. Sync message history
    sync_history_to_device(user_id, device_id)

# Deliver to all user's devices
def deliver_message_multi_device(user_id, message):
    devices = get_user_devices(user_id)

    for device in devices:
        # Encrypt separately for each device
        encrypted_message = encrypt_for_device(message, device.public_key)

        # Check if device is online
        if is_device_online(device.device_id):
            connection = get_device_connection(device.device_id)
            connection.send(encrypted_message)
        else:
            # Queue for later delivery
            queue_for_device(device.device_id, encrypted_message)
```

---

## Step 8: Monitoring & Reliability

### Key Metrics

```python
# Prometheus metrics
message_send_latency = Histogram('message_send_latency_seconds')
message_delivery_rate = Counter('message_delivery_total', ['status'])
active_connections = Gauge('websocket_connections_active')
message_queue_depth = Gauge('message_queue_depth', ['queue'])

# Track in code
@message_send_latency.time()
def send_message(sender_id, recipient_id, content):
    # ... implementation ...
    message_delivery_rate.labels(status='sent').inc()
```

**Alerts**:
- Message delivery latency > 1s
- Failed delivery rate > 1%
- WebSocket disconnection rate > 5%
- Queue depth > 10,000 messages

### Failure Handling

**1. WebSocket Server Failure**:
```python
# Health check endpoint
@app.route('/health')
def health_check():
    return {
        'status': 'healthy',
        'connections': len(connection_pool),
        'memory_usage': get_memory_usage(),
        'uptime': get_uptime()
    }

# Graceful degradation
if websocket_unavailable:
    # Fallback to HTTP long-polling
    use_long_polling()
```

**2. Database Failure**:
```python
# Multi-DC replication (Cassandra)
consistency_level = 'LOCAL_QUORUM'  # Read/write to 2/3 nodes in local DC

# Fallback on failure
try:
    db.cassandra.query(query, consistency='QUORUM')
except TimeoutError:
    # Fallback to lower consistency
    db.cassandra.query(query, consistency='ONE')
```

**3. Message Queue Failure**:
```python
# Kafka with replication
kafka.publish(topic, message, replication_factor=3)

# Dead letter queue for failed messages
if not kafka.publish(topic, message):
    db.insert('dead_letter_queue', message)
    alert_ops_team()
```

---

## Complete System Diagram

```
                    ┌──────────────────────────────────┐
                    │         Clients                  │
                    │  iOS • Android • Web • Desktop   │
                    └────────────────┬─────────────────┘
                                     │
                    ┌────────────────▼─────────────────┐
                    │      DNS / CloudFlare CDN        │
                    │   (DDoS protection, SSL)         │
                    └────────────────┬─────────────────┘
                                     │
                    ┌────────────────▼─────────────────┐
                    │     Load Balancer (AWS ALB)      │
                    │   Sticky sessions (IP hash)      │
                    └────────────────┬─────────────────┘
                                     │
          ┌──────────────────────────┼──────────────────────────┐
          ▼                          ▼                          ▼
    ┌─────────┐              ┌─────────┐              ┌─────────┐
    │   WS    │              │   WS    │              │   WS    │
    │ Server  │              │ Server  │              │ Server  │
    │  100K   │              │  100K   │              │  100K   │
    │  Conn   │              │  Conn   │              │  Conn   │
    └────┬────┘              └────┬────┘              └────┬────┘
         │                        │                        │
         └────────────────────────┼────────────────────────┘
                                  │
                    ┌─────────────▼──────────────┐
                    │    API Gateway (Kong)      │
                    │  Auth • Rate Limiting      │
                    └─────────────┬──────────────┘
                                  │
          ┌───────────────────────┼───────────────────────┐
          ▼                       ▼                       ▼
    ┌──────────┐          ┌──────────┐          ┌──────────┐
    │ Message  │          │  Group   │          │   User   │
    │ Service  │          │ Service  │          │ Service  │
    └────┬─────┘          └────┬─────┘          └────┬─────┘
         │                     │                      │
    ┌────┴─────┐          ┌────┴─────┐          ┌────┴─────┐
    │ Presence │          │  Notify  │          │  Media   │
    │ Service  │          │ Service  │          │ Service  │
    └────┬─────┘          └────┬─────┘          └────┬─────┘
         │                     │                      │
         └─────────────────────┼──────────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
    ┌──────────┐         ┌──────────┐        ┌──────────┐
    │  Redis   │         │  Kafka   │        │  Redis   │
    │ Cluster  │         │  Queue   │        │  Cache   │
    │(Presence)│         │          │        │(Messages)│
    └────┬─────┘         └────┬─────┘        └────┬─────┘
         │                    │                    │
         └────────────────────┼────────────────────┘
                              │
          ┌───────────────────┼───────────────────┐
          ▼                   ▼                   ▼
    ┌──────────┐        ┌──────────┐       ┌──────────┐
    │Cassandra │        │PostgreSQL│       │    S3    │
    │(Messages)│        │(Users/Grp│       │ (Media)  │
    │Time-Series│        │         )│       │   +CDN   │
    └──────────┘        └──────────┘       └──────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected scale (users, messages/sec)?
- Message retention period?
- Media support (images, videos)?
- Group chat size limits?
- Read receipts required?
- End-to-end encryption?
- Multi-device support?

**Topics to Cover**:
- WebSocket for real-time (critical!)
- Cassandra for time-series messages
- Message ordering (TIMEUUID)
- Presence management (Redis with TTL)
- Group message fan-out optimization
- End-to-end encryption (Signal Protocol)

**Common Follow-ups**:
- "How would you handle 10B messages/day?"
  → Cassandra sharding, more WebSocket servers, Kafka partitioning
- "How do you prevent message loss?"
  → At-least-once delivery, retry queues, persistent storage
- "How do you handle network partitions?"
  → Multi-DC replication, eventual consistency, conflict resolution
- "How would you implement voice/video calls?"
  → WebRTC, TURN/STUN servers, separate signaling channel

---

## Key Takeaways

1. **WebSocket is Critical**: Persistent connections enable real-time messaging
2. **Time-Series Database**: Cassandra perfect for append-only message logs
3. **Message Ordering**: TIMEUUID provides chronological order without coordination
4. **Fan-Out Optimization**: Store once, reference multiple times for groups
5. **Presence is Expensive**: Use Redis with TTL, throttle status updates
6. **Media Dominates**: 99% of bandwidth is media, use CDN aggressively
7. **E2E Encryption**: Signal Protocol for security and privacy
8. **At-Least-Once Delivery**: Accept duplicates, use idempotency keys

## Further Reading

- WhatsApp Engineering Blog
- Signal Protocol Specifications
- Cassandra Data Modeling Best Practices
- WebSocket Scaling Strategies
- End-to-End Encryption in Messaging

---

**System Design Principles Applied**:
- CAP Theorem: Chose AP (Availability + Partition tolerance)
- CQRS: Separate read/write paths
- Event Sourcing: Message queue for async processing
- Denormalization: User inbox for fast queries
- Idempotency: Duplicate message detection
