# Design Video Calling System (Zoom/Google Meet)

## Table of Contents
1. [Introduction](#introduction)
2. [Requirements](#requirements)
3. [Capacity Estimation](#capacity-estimation)
4. [System Architecture](#system-architecture)
5. [Data Models](#data-models)
6. [API Design](#api-design)
7. [WebRTC Implementation](#webrtc-implementation)
8. [SFU Architecture](#sfu-architecture)
9. [Signaling Server](#signaling-server)
10. [TURN/STUN Servers](#turnstun-servers)
11. [Recording & Storage](#recording--storage)
12. [Security](#security)
13. [Advanced Features](#advanced-features)
14. [Scaling Strategy](#scaling-strategy)
15. [Monitoring & Observability](#monitoring--observability)

---

## Introduction

Design a **video calling system** like Zoom, Google Meet, or Microsoft Teams that supports:
- **1-on-1 video calls** (peer-to-peer)
- **Group video calls** (up to 100+ participants)
- **Screen sharing** and collaboration features
- **Real-time audio/video** with low latency (<300ms)
- **Recording** and playback
- **Global availability** with regional distribution

### Key Challenges
- **Low latency**: Real-time communication requires <300ms end-to-end latency
- **High bandwidth**: Video streams consume 1-5 Mbps per participant
- **NAT traversal**: Most users are behind firewalls/NATs
- **Scalability**: Supporting millions of concurrent calls
- **Quality adaptation**: Handling varying network conditions
- **Recording**: Storing and processing large video files

---

## Requirements

### Functional Requirements
1. **Initiate Calls**
   - Start 1-on-1 or group calls
   - Generate shareable meeting links
   - Schedule calls with calendar integration

2. **Join Calls**
   - Join via link (no account required for guests)
   - Waiting room for host approval
   - Browser and mobile app support

3. **Media Streaming**
   - Video streaming (720p, 1080p, 4K)
   - Audio streaming (high-quality Opus codec)
   - Screen sharing with annotations
   - Virtual backgrounds and filters

4. **Call Management**
   - Mute/unmute audio/video
   - Participant management (kick, mute all)
   - Chat during calls
   - Reactions and hand raising

5. **Recording**
   - Record calls (host permission required)
   - Store recordings for 90 days
   - Download and share recordings

### Non-Functional Requirements
1. **Low Latency**: <300ms end-to-end
2. **High Availability**: 99.99% uptime
3. **Scalability**: Support 10M concurrent users
4. **Bandwidth Efficiency**: Adaptive bitrate streaming
5. **Security**: End-to-end encryption (E2EE) option
6. **Global Reach**: Regional data centers

### Out of Scope
- Live streaming to 10K+ viewers (different architecture needed)
- Advanced video editing features
- AI-powered transcription (can be added later)

---

## Capacity Estimation

Using the **POWER technique** (see [scale-estimation-guide.md](./scale-estimation-guide.md)):

### Assumptions (Zoom-scale)
- **Total users**: 300M (similar to Zoom)
- **Daily active users (DAU)**: 30M (10% of total)
- **Peak concurrent calls**: 10M users in 5M calls
- **Average call duration**: 30 minutes
- **Group call average**: 5 participants per call

### Bandwidth Calculations

**Per Participant Bandwidth**:
- **720p video**: 2.5 Mbps upload + 2.5 Mbps download (1-on-1)
- **Group call (5 participants)**: 2.5 Mbps upload + 10 Mbps download (receiving 4 streams)
- **Audio only**: 50 Kbps (negligible compared to video)

**Peak Bandwidth**:
```
10M concurrent users × 2.5 Mbps upload = 25 Tbps upload
10M concurrent users × 5 Mbps download (average) = 50 Tbps download
Total: ~75 Tbps (across all regional data centers)
```

### Storage Calculations

**Recording Storage**:
- **Recording rate**: 5% of calls are recorded
- **Recorded minutes per day**: 30M users × 30 min × 5% = 45M minutes/day
- **Storage per minute**: 20 MB (1080p compressed)
- **Daily storage**: 45M min × 20 MB = 900 TB/day
- **90-day retention**: 900 TB × 90 = **81 PB**

With compression and deduplication: **~11 PB** (see scale estimation guide)

### Infrastructure

**SFU Servers** (for group calls):
- **Capacity per SFU**: 250 concurrent participants (10 calls × 25 participants)
- **Required SFUs**: 10M users / 250 = **40,000 SFU servers**

**TURN Servers** (for NAT traversal):
- **TURN usage**: 10% of users need TURN relay
- **Bandwidth per user**: 2.5 Mbps
- **TURN bandwidth**: 1M users × 2.5 Mbps = **2.5 Tbps**
- **TURN servers (10 Gbps each)**: 250 servers

---

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                         CLIENT TIER                          │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │ Web App  │  │  Mobile  │  │ Desktop  │  │  SDK     │   │
│  │ (WebRTC) │  │   App    │  │   App    │  │          │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      API GATEWAY (HTTPS)                     │
│              Load Balancer + Rate Limiting                   │
└─────────────────────────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  Signaling   │  │    REST      │  │   STUN/TURN  │
│   Server     │  │  API Server  │  │    Servers   │
│ (WebSocket)  │  │              │  │              │
└──────────────┘  └──────────────┘  └──────────────┘
         │                 │                 │
         └─────────────────┼─────────────────┘
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                     APPLICATION TIER                         │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │  User    │  │  Meeting │  │   Call   │  │Recording │   │
│  │ Service  │  │ Service  │  │ Analytics│  │ Service  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
         ┌─────────────────┼─────────────────┐
         ▼                 ▼                 ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│  PostgreSQL  │  │     Redis    │  │    Kafka     │
│  (Metadata)  │  │   (Cache)    │  │  (Events)    │
└──────────────┘  └──────────────┘  └──────────────┘

┌─────────────────────────────────────────────────────────────┐
│                       MEDIA TIER                             │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐   │
│  │   SFU    │  │   SFU    │  │   SFU    │  │Recording │   │
│  │ Server 1 │  │ Server 2 │  │ Server N │  │ Ingress  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                      STORAGE TIER                            │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                  │
│  │   S3     │  │CloudFront│  │  CDN     │                  │
│  │(Recordings)│ │  (Edge)  │  │          │                  │
│  └──────────┘  └──────────┘  └──────────┘                  │
└─────────────────────────────────────────────────────────────┘
```

### Component Breakdown

#### 1. **Client Tier**
- **Web App**: React/Vue with WebRTC APIs
- **Mobile Apps**: Native iOS/Android with WebRTC SDKs
- **Desktop Apps**: Electron with native codecs
- **SDK**: For third-party integrations

#### 2. **Signaling Server**
- **Purpose**: Exchange SDP offers/answers and ICE candidates
- **Protocol**: WebSocket (bidirectional, low-latency)
- **Tech**: Node.js/Go for high concurrency
- **Scaling**: Stateless, horizontally scalable

#### 3. **REST API Server**
- **Purpose**: Meeting creation, user management, recordings
- **Tech**: Node.js/Go/Java Spring Boot
- **Features**: Authentication, authorization, meeting CRUD

#### 4. **STUN/TURN Servers**
- **STUN**: Help clients discover public IP addresses
- **TURN**: Relay media when P2P fails (10% of cases)
- **Tech**: Coturn, Janus TURN

#### 5. **SFU (Selective Forwarding Unit)**
- **Purpose**: Forward media streams in group calls
- **Advantages**: Lower server CPU than MCU, better quality
- **Tech**: Janus, Jitsi Videobridge, mediasoup
- **Scaling**: Each SFU handles 250-500 concurrent participants

#### 6. **Recording Service**
- **Components**:
  - **Ingress**: Receive RTP streams from SFU
  - **Encoder**: Transcode to MP4/WebM
  - **Storage**: S3-compatible object storage
- **Tech**: FFmpeg, GStreamer

#### 7. **Databases**
- **PostgreSQL**: Users, meetings, recordings metadata
- **Redis**: Session management, presence, caching
- **Kafka**: Event streaming (call started, user joined, etc.)

---

## Data Models

### PostgreSQL Schema

```sql
-- Users Table
CREATE TABLE users (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) UNIQUE NOT NULL,
    name VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255),
    avatar_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    INDEX idx_email (email)
);

-- Meetings Table
CREATE TABLE meetings (
    meeting_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_user_id UUID REFERENCES users(user_id),
    title VARCHAR(500),
    meeting_link VARCHAR(100) UNIQUE NOT NULL,  -- e.g., "abc-defg-hij"
    scheduled_at TIMESTAMP,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    max_participants INT DEFAULT 100,
    require_password BOOLEAN DEFAULT FALSE,
    password_hash VARCHAR(255),
    waiting_room_enabled BOOLEAN DEFAULT FALSE,
    recording_enabled BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    status VARCHAR(50) DEFAULT 'scheduled',  -- scheduled, active, ended
    INDEX idx_host (host_user_id),
    INDEX idx_link (meeting_link),
    INDEX idx_scheduled (scheduled_at)
);

-- Participants Table
CREATE TABLE participants (
    participant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID REFERENCES meetings(meeting_id),
    user_id UUID REFERENCES users(user_id),
    display_name VARCHAR(255),
    joined_at TIMESTAMP DEFAULT NOW(),
    left_at TIMESTAMP,
    is_host BOOLEAN DEFAULT FALSE,
    is_moderator BOOLEAN DEFAULT FALSE,
    audio_enabled BOOLEAN DEFAULT TRUE,
    video_enabled BOOLEAN DEFAULT TRUE,
    INDEX idx_meeting (meeting_id),
    INDEX idx_user (user_id)
);

-- Recordings Table
CREATE TABLE recordings (
    recording_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meeting_id UUID REFERENCES meetings(meeting_id),
    storage_path VARCHAR(1000),  -- S3 path
    file_size_bytes BIGINT,
    duration_seconds INT,
    format VARCHAR(20),  -- mp4, webm
    resolution VARCHAR(20),  -- 720p, 1080p
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    status VARCHAR(50) DEFAULT 'processing',  -- processing, ready, failed
    download_url VARCHAR(1000),
    expires_at TIMESTAMP,  -- 90 days from creation
    INDEX idx_meeting (meeting_id)
);

-- Call Quality Metrics (for analytics)
CREATE TABLE call_metrics (
    metric_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    participant_id UUID REFERENCES participants(participant_id),
    timestamp TIMESTAMP DEFAULT NOW(),
    packet_loss_percent DECIMAL(5,2),
    jitter_ms INT,
    round_trip_time_ms INT,
    bitrate_kbps INT,
    resolution VARCHAR(20)
);
```

### Redis Data Structures

```python
# Active meetings set
meeting:active -> SET {meeting_id1, meeting_id2, ...}

# Participants in a meeting
meeting:{meeting_id}:participants -> SET {user_id1, user_id2, ...}

# User's current meeting
user:{user_id}:current_meeting -> STRING meeting_id

# SFU server assignment
meeting:{meeting_id}:sfu_server -> STRING "sfu-us-east-1-042"

# WebSocket connection mapping
ws_connection:{connection_id} -> HASH {user_id, meeting_id, sfu_server}

# Presence (online users)
presence:online -> SORTED SET {user_id: timestamp}

# Rate limiting
rate_limit:{user_id}:create_meeting -> STRING count (expires in 1 hour)
```

---

## API Design

### REST API Endpoints

```http
### Authentication
POST   /api/v1/auth/register          # Register new user
POST   /api/v1/auth/login             # Login
POST   /api/v1/auth/refresh           # Refresh JWT token

### Meetings
POST   /api/v1/meetings               # Create meeting
GET    /api/v1/meetings/:id           # Get meeting details
PATCH  /api/v1/meetings/:id           # Update meeting settings
DELETE /api/v1/meetings/:id           # Delete/cancel meeting
GET    /api/v1/meetings               # List user's meetings
POST   /api/v1/meetings/:id/join      # Join meeting (get join token)

### Participants
GET    /api/v1/meetings/:id/participants      # List participants
DELETE /api/v1/meetings/:id/participants/:uid # Kick participant

### Recordings
GET    /api/v1/meetings/:id/recordings        # List recordings
GET    /api/v1/recordings/:id                 # Get recording metadata
GET    /api/v1/recordings/:id/download        # Get download URL
DELETE /api/v1/recordings/:id                 # Delete recording
```

### Request/Response Examples

**Create Meeting**:
```json
POST /api/v1/meetings
{
  "title": "Team Standup",
  "scheduled_at": "2025-11-15T10:00:00Z",
  "max_participants": 50,
  "require_password": true,
  "password": "secret123",
  "waiting_room_enabled": true
}

Response 201:
{
  "meeting_id": "550e8400-e29b-41d4-a716-446655440000",
  "meeting_link": "https://meet.example.com/abc-defg-hij",
  "host_link": "https://meet.example.com/abc-defg-hij?host_key=xyz123"
}
```

**Join Meeting**:
```json
POST /api/v1/meetings/abc-defg-hij/join
{
  "display_name": "John Doe",
  "password": "secret123"
}

Response 200:
{
  "join_token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "signaling_server": "wss://signal-us-east-1.example.com",
  "stun_servers": ["stun:stun.example.com:3478"],
  "turn_servers": [
    {
      "urls": "turn:turn.example.com:3478",
      "username": "temp_user_123",
      "credential": "temp_pass_abc"
    }
  ],
  "sfu_server": "sfu-us-east-1-042.example.com"
}
```

### WebSocket Signaling Protocol

```javascript
// Client connects to signaling server
const ws = new WebSocket('wss://signal.example.com?token=JOIN_TOKEN');

// Message types
{
  "type": "join",
  "meeting_id": "abc-defg-hij",
  "user_id": "user-123"
}

{
  "type": "offer",
  "to": "user-456",
  "sdp": "v=0\r\no=- 123456 2 IN IP4 127.0.0.1\r\n..."
}

{
  "type": "answer",
  "to": "user-123",
  "sdp": "v=0\r\no=- 789012 2 IN IP4 127.0.0.1\r\n..."
}

{
  "type": "ice_candidate",
  "to": "user-456",
  "candidate": {
    "candidate": "candidate:1 1 UDP 2130706431 192.168.1.5 54321 typ host",
    "sdpMLineIndex": 0
  }
}

{
  "type": "participant_joined",
  "user_id": "user-789",
  "display_name": "Alice"
}

{
  "type": "participant_left",
  "user_id": "user-456"
}
```

---

## WebRTC Implementation

### Client-Side Code (JavaScript)

```javascript
class VideoCallClient {
  constructor(signalingServerUrl, iceServers) {
    this.signalingWs = null;
    this.peerConnections = new Map(); // user_id -> RTCPeerConnection
    this.localStream = null;
    this.iceServers = iceServers;
    this.signalingServerUrl = signalingServerUrl;
  }

  async initialize() {
    // Get local media
    this.localStream = await navigator.mediaDevices.getUserMedia({
      video: { width: 1280, height: 720 },
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true
      }
    });

    // Display local video
    document.getElementById('localVideo').srcObject = this.localStream;

    // Connect to signaling server
    this.connectSignaling();
  }

  connectSignaling() {
    this.signalingWs = new WebSocket(this.signalingServerUrl);

    this.signalingWs.onopen = () => {
      console.log('Connected to signaling server');
      this.sendMessage({ type: 'join', meeting_id: this.meetingId });
    };

    this.signalingWs.onmessage = (event) => {
      const message = JSON.parse(event.data);
      this.handleSignalingMessage(message);
    };
  }

  async handleSignalingMessage(message) {
    switch (message.type) {
      case 'offer':
        await this.handleOffer(message);
        break;
      case 'answer':
        await this.handleAnswer(message);
        break;
      case 'ice_candidate':
        await this.handleIceCandidate(message);
        break;
      case 'participant_joined':
        await this.createPeerConnection(message.user_id);
        break;
      case 'participant_left':
        this.removePeerConnection(message.user_id);
        break;
    }
  }

  async createPeerConnection(userId) {
    const pc = new RTCPeerConnection({
      iceServers: this.iceServers
    });

    // Add local tracks
    this.localStream.getTracks().forEach(track => {
      pc.addTrack(track, this.localStream);
    });

    // Handle remote track
    pc.ontrack = (event) => {
      const remoteVideo = document.getElementById(`video-${userId}`);
      remoteVideo.srcObject = event.streams[0];
    };

    // Handle ICE candidates
    pc.onicecandidate = (event) => {
      if (event.candidate) {
        this.sendMessage({
          type: 'ice_candidate',
          to: userId,
          candidate: event.candidate
        });
      }
    };

    // Store peer connection
    this.peerConnections.set(userId, pc);

    // Create and send offer
    const offer = await pc.createOffer();
    await pc.setLocalDescription(offer);

    this.sendMessage({
      type: 'offer',
      to: userId,
      sdp: offer.sdp
    });
  }

  async handleOffer(message) {
    const pc = await this.createPeerConnection(message.from);

    await pc.setRemoteDescription({
      type: 'offer',
      sdp: message.sdp
    });

    const answer = await pc.createAnswer();
    await pc.setLocalDescription(answer);

    this.sendMessage({
      type: 'answer',
      to: message.from,
      sdp: answer.sdp
    });
  }

  async handleAnswer(message) {
    const pc = this.peerConnections.get(message.from);
    await pc.setRemoteDescription({
      type: 'answer',
      sdp: message.sdp
    });
  }

  async handleIceCandidate(message) {
    const pc = this.peerConnections.get(message.from);
    await pc.addIceCandidate(message.candidate);
  }

  sendMessage(message) {
    this.signalingWs.send(JSON.stringify(message));
  }

  toggleAudio() {
    const audioTrack = this.localStream.getAudioTracks()[0];
    audioTrack.enabled = !audioTrack.enabled;
    return audioTrack.enabled;
  }

  toggleVideo() {
    const videoTrack = this.localStream.getVideoTracks()[0];
    videoTrack.enabled = !videoTrack.enabled;
    return videoTrack.enabled;
  }

  async shareScreen() {
    const screenStream = await navigator.mediaDevices.getDisplayMedia({
      video: { cursor: 'always' },
      audio: false
    });

    const screenTrack = screenStream.getVideoTracks()[0];

    // Replace video track in all peer connections
    this.peerConnections.forEach(pc => {
      const sender = pc.getSenders().find(s => s.track.kind === 'video');
      sender.replaceTrack(screenTrack);
    });

    // When screen sharing stops, switch back to camera
    screenTrack.onended = () => {
      const videoTrack = this.localStream.getVideoTracks()[0];
      this.peerConnections.forEach(pc => {
        const sender = pc.getSenders().find(s => s.track.kind === 'video');
        sender.replaceTrack(videoTrack);
      });
    };
  }

  disconnect() {
    this.peerConnections.forEach(pc => pc.close());
    this.peerConnections.clear();
    this.localStream.getTracks().forEach(track => track.stop());
    this.signalingWs.close();
  }
}

// Usage
const client = new VideoCallClient('wss://signal.example.com', [
  { urls: 'stun:stun.l.google.com:19302' },
  {
    urls: 'turn:turn.example.com:3478',
    username: 'user',
    credential: 'pass'
  }
]);

await client.initialize();
```

---

## SFU Architecture

### Why SFU over P2P and MCU?

| Architecture | Pros | Cons | Use Case |
|-------------|------|------|----------|
| **P2P** | Low latency, no server cost | Doesn't scale (N² connections) | 1-on-1 calls |
| **MCU** | Low client bandwidth | High server CPU, single quality | Large webinars |
| **SFU** | Scalable, flexible quality | Higher client bandwidth | **Group calls (recommended)** |

### SFU Server (mediasoup Example)

```javascript
// SFU Server using mediasoup (Node.js)
const mediasoup = require('mediasoup');

class SFUServer {
  constructor() {
    this.workers = [];
    this.routers = new Map(); // meeting_id -> Router
    this.transports = new Map(); // transport_id -> Transport
    this.producers = new Map(); // producer_id -> Producer
    this.consumers = new Map(); // consumer_id -> Consumer
  }

  async initialize() {
    // Create mediasoup workers (one per CPU core)
    const numWorkers = require('os').cpus().length;

    for (let i = 0; i < numWorkers; i++) {
      const worker = await mediasoup.createWorker({
        logLevel: 'warn',
        rtcMinPort: 40000,
        rtcMaxPort: 49999
      });

      this.workers.push(worker);
    }
  }

  async createRouter(meetingId) {
    // Round-robin worker selection
    const worker = this.workers[this.routers.size % this.workers.length];

    const router = await worker.createRouter({
      mediaCodecs: [
        {
          kind: 'audio',
          mimeType: 'audio/opus',
          clockRate: 48000,
          channels: 2
        },
        {
          kind: 'video',
          mimeType: 'video/VP8',
          clockRate: 90000,
          parameters: {
            'x-google-start-bitrate': 1000
          }
        },
        {
          kind: 'video',
          mimeType: 'video/H264',
          clockRate: 90000,
          parameters: {
            'packetization-mode': 1,
            'profile-level-id': '42e01f',
            'level-asymmetry-allowed': 1
          }
        }
      ]
    });

    this.routers.set(meetingId, router);
    return router;
  }

  async createWebRtcTransport(meetingId, direction) {
    const router = this.routers.get(meetingId);

    const transport = await router.createWebRtcTransport({
      listenIps: [
        { ip: '0.0.0.0', announcedIp: process.env.PUBLIC_IP }
      ],
      enableUdp: true,
      enableTcp: true,
      preferUdp: true,
      initialAvailableOutgoingBitrate: 1000000
    });

    this.transports.set(transport.id, transport);

    return {
      id: transport.id,
      iceParameters: transport.iceParameters,
      iceCandidates: transport.iceCandidates,
      dtlsParameters: transport.dtlsParameters
    };
  }

  async connectTransport(transportId, dtlsParameters) {
    const transport = this.transports.get(transportId);
    await transport.connect({ dtlsParameters });
  }

  async createProducer(transportId, kind, rtpParameters) {
    const transport = this.transports.get(transportId);

    const producer = await transport.produce({
      kind,
      rtpParameters
    });

    this.producers.set(producer.id, producer);

    return { id: producer.id };
  }

  async createConsumer(transportId, producerId, rtpCapabilities) {
    const transport = this.transports.get(transportId);
    const producer = this.producers.get(producerId);

    const router = transport.appData.router;

    if (!router.canConsume({ producerId, rtpCapabilities })) {
      throw new Error('Cannot consume');
    }

    const consumer = await transport.consume({
      producerId,
      rtpCapabilities,
      paused: false
    });

    this.consumers.set(consumer.id, consumer);

    return {
      id: consumer.id,
      producerId,
      kind: consumer.kind,
      rtpParameters: consumer.rtpParameters
    };
  }
}

// Initialize SFU
const sfu = new SFUServer();
await sfu.initialize();
```

### SFU API Endpoints

```http
POST /sfu/routers                    # Create router for meeting
POST /sfu/transports                 # Create WebRTC transport
POST /sfu/transports/:id/connect     # Connect transport
POST /sfu/producers                  # Create producer (send stream)
POST /sfu/consumers                  # Create consumer (receive stream)
DELETE /sfu/producers/:id            # Close producer
DELETE /sfu/consumers/:id            # Close consumer
```

---

## Signaling Server

### WebSocket Signaling Server (Node.js)

```javascript
const WebSocket = require('ws');
const redis = require('redis');

class SignalingServer {
  constructor() {
    this.wss = new WebSocket.Server({ port: 8080 });
    this.redis = redis.createClient();
    this.connections = new Map(); // connection_id -> { ws, userId, meetingId }
  }

  start() {
    this.wss.on('connection', (ws, req) => {
      const token = new URL(req.url, 'ws://base').searchParams.get('token');
      const { userId, meetingId } = this.verifyToken(token);

      const connectionId = this.generateId();
      this.connections.set(connectionId, { ws, userId, meetingId });

      // Add user to meeting
      this.redis.sadd(`meeting:${meetingId}:participants`, userId);

      // Notify others
      this.broadcast(meetingId, {
        type: 'participant_joined',
        user_id: userId
      }, userId);

      // Handle messages
      ws.on('message', (data) => {
        this.handleMessage(connectionId, JSON.parse(data));
      });

      // Handle disconnect
      ws.on('close', () => {
        this.handleDisconnect(connectionId);
      });

      // Send current participants list
      this.sendParticipantsList(ws, meetingId);
    });
  }

  handleMessage(connectionId, message) {
    const { userId, meetingId, ws } = this.connections.get(connectionId);

    switch (message.type) {
      case 'offer':
      case 'answer':
      case 'ice_candidate':
        // Forward to specific user
        this.sendToUser(meetingId, message.to, {
          ...message,
          from: userId
        });
        break;

      case 'chat':
        // Broadcast chat message
        this.broadcast(meetingId, {
          type: 'chat',
          from: userId,
          message: message.text,
          timestamp: Date.now()
        });
        break;

      case 'mute_audio':
        this.broadcast(meetingId, {
          type: 'participant_muted',
          user_id: userId,
          muted: message.muted
        });
        break;
    }
  }

  handleDisconnect(connectionId) {
    const { userId, meetingId } = this.connections.get(connectionId);

    // Remove from Redis
    this.redis.srem(`meeting:${meetingId}:participants`, userId);

    // Notify others
    this.broadcast(meetingId, {
      type: 'participant_left',
      user_id: userId
    });

    this.connections.delete(connectionId);
  }

  broadcast(meetingId, message, excludeUserId = null) {
    this.connections.forEach(({ ws, userId, meetingId: mid }) => {
      if (mid === meetingId && userId !== excludeUserId) {
        ws.send(JSON.stringify(message));
      }
    });
  }

  sendToUser(meetingId, targetUserId, message) {
    this.connections.forEach(({ ws, userId, meetingId: mid }) => {
      if (mid === meetingId && userId === targetUserId) {
        ws.send(JSON.stringify(message));
      }
    });
  }

  async sendParticipantsList(ws, meetingId) {
    const participants = await this.redis.smembers(`meeting:${meetingId}:participants`);
    ws.send(JSON.stringify({
      type: 'participants_list',
      participants
    }));
  }

  verifyToken(token) {
    // Verify JWT and extract userId, meetingId
    const payload = jwt.verify(token, process.env.JWT_SECRET);
    return { userId: payload.user_id, meetingId: payload.meeting_id };
  }

  generateId() {
    return require('crypto').randomBytes(16).toString('hex');
  }
}

const server = new SignalingServer();
server.start();
```

---

## TURN/STUN Servers

### Why TURN/STUN?

- **STUN (Session Traversal Utilities for NAT)**: Helps clients discover their public IP address
- **TURN (Traversal Using Relays around NAT)**: Relays media when direct P2P connection fails (~10% of cases)

### Coturn Configuration

```ini
# /etc/turnserver.conf

# Public IP
external-ip=203.0.113.1

# Listening ports
listening-port=3478
tls-listening-port=5349

# Realm
realm=turn.example.com

# Authentication
lt-cred-mech
user=tempuser:temppassword

# Fingerprint
fingerprint

# Logging
verbose

# Bandwidth limits
max-bps=1000000
total-quota=100

# SSL certificates
cert=/etc/ssl/turn_cert.pem
pkey=/etc/ssl/turn_key.pem
```

### TURN Credentials Generation

```javascript
// Generate temporary TURN credentials (expire in 24 hours)
function generateTurnCredentials(username) {
  const secret = process.env.TURN_SECRET;
  const ttl = 86400; // 24 hours
  const timestamp = Math.floor(Date.now() / 1000) + ttl;

  const turnUsername = `${timestamp}:${username}`;
  const hmac = crypto.createHmac('sha1', secret);
  hmac.update(turnUsername);
  const turnPassword = hmac.digest('base64');

  return {
    urls: 'turn:turn.example.com:3478',
    username: turnUsername,
    credential: turnPassword
  };
}
```

---

## Recording & Storage

### Recording Architecture

```
┌──────────┐
│   SFU    │ ──RTP──> ┌────────────────┐
│  Server  │          │   Recording    │
└──────────┘          │    Service     │
                      │  (FFmpeg Bot)  │
                      └────────────────┘
                              │
                              │ Upload MP4
                              ▼
                      ┌────────────────┐
                      │   S3 Storage   │
                      │   (Recordings) │
                      └────────────────┘
                              │
                              │ CDN
                              ▼
                      ┌────────────────┐
                      │   CloudFront   │
                      │   (Playback)   │
                      └────────────────┘
```

### Recording Service (FFmpeg)

```javascript
const { spawn } = require('child_process');
const AWS = require('aws-sdk');
const s3 = new AWS.S3();

class RecordingService {
  async startRecording(meetingId, rtpPort) {
    const outputFile = `/tmp/recording-${meetingId}.mp4`;

    const ffmpeg = spawn('ffmpeg', [
      '-protocol_whitelist', 'file,rtp,udp',
      '-i', `rtp://0.0.0.0:${rtpPort}`,
      '-c:v', 'libx264',
      '-preset', 'veryfast',
      '-crf', '23',
      '-c:a', 'aac',
      '-b:a', '128k',
      outputFile
    ]);

    ffmpeg.on('close', async (code) => {
      console.log(`Recording finished: ${outputFile}`);

      // Upload to S3
      const fileStream = fs.createReadStream(outputFile);
      const uploadParams = {
        Bucket: 'recordings-bucket',
        Key: `recordings/${meetingId}/${Date.now()}.mp4`,
        Body: fileStream,
        ContentType: 'video/mp4'
      };

      const result = await s3.upload(uploadParams).promise();

      // Save metadata to database
      await this.saveRecordingMetadata(meetingId, result.Location);

      // Delete temp file
      fs.unlinkSync(outputFile);
    });

    return { status: 'recording_started' };
  }

  async saveRecordingMetadata(meetingId, s3Url) {
    const recording = await db.query(`
      INSERT INTO recordings (meeting_id, storage_path, status, expires_at)
      VALUES ($1, $2, 'processing', NOW() + INTERVAL '90 days')
      RETURNING *
    `, [meetingId, s3Url]);

    return recording.rows[0];
  }
}
```

---

## Security

### 1. **End-to-End Encryption (E2EE)**

For maximum security (Zoom E2E mode):

```javascript
// Client-side encryption using insertable streams
const pc = new RTCPeerConnection();

// Generate encryption key
const encryptionKey = await crypto.subtle.generateKey(
  { name: 'AES-GCM', length: 128 },
  true,
  ['encrypt', 'decrypt']
);

// Encrypt outgoing frames
const sender = pc.addTrack(localStream.getVideoTracks()[0]);
const senderStreams = sender.createEncodedStreams();

const transformStream = new TransformStream({
  async transform(frame, controller) {
    const encrypted = await encryptFrame(frame, encryptionKey);
    controller.enqueue(encrypted);
  }
});

senderStreams.readable
  .pipeThrough(transformStream)
  .pipeTo(senderStreams.writable);
```

### 2. **Authentication & Authorization**

```javascript
// JWT-based authentication
const jwt = require('jsonwebtoken');

function generateJoinToken(userId, meetingId, isHost) {
  return jwt.sign(
    {
      user_id: userId,
      meeting_id: meetingId,
      is_host: isHost,
      exp: Math.floor(Date.now() / 1000) + 3600 // 1 hour
    },
    process.env.JWT_SECRET
  );
}

// Middleware
function verifyJoinToken(req, res, next) {
  const token = req.headers.authorization?.split(' ')[1];

  try {
    const decoded = jwt.verify(token, process.env.JWT_SECRET);
    req.user = decoded;
    next();
  } catch (error) {
    res.status(401).json({ error: 'Invalid token' });
  }
}
```

### 3. **Rate Limiting**

```javascript
const rateLimit = require('express-rate-limit');

const createMeetingLimiter = rateLimit({
  windowMs: 60 * 60 * 1000, // 1 hour
  max: 10, // 10 meetings per hour per user
  keyGenerator: (req) => req.user.user_id
});

app.post('/api/v1/meetings', createMeetingLimiter, createMeeting);
```

### 4. **DTLS-SRTP Encryption**

WebRTC automatically uses DTLS-SRTP for encrypting media streams in transit. Ensure:
- Valid SSL certificates on all servers
- Strong cipher suites
- Perfect Forward Secrecy (PFS)

---

## Advanced Features

### 1. **Virtual Backgrounds**

```javascript
// Using TensorFlow.js for background segmentation
const model = await bodyPix.load();

async function applyVirtualBackground(videoTrack) {
  const canvas = document.createElement('canvas');
  const ctx = canvas.getContext('2d');

  const video = document.createElement('video');
  video.srcObject = new MediaStream([videoTrack]);

  async function processFrame() {
    const segmentation = await model.segmentPerson(video);

    // Draw background image
    ctx.drawImage(backgroundImage, 0, 0);

    // Draw person only
    const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
    for (let i = 0; i < segmentation.data.length; i++) {
      if (segmentation.data[i] === 0) {
        imageData.data[i * 4 + 3] = 0; // Make background transparent
      }
    }
    ctx.putImageData(imageData, 0, 0);

    requestAnimationFrame(processFrame);
  }

  processFrame();
  return canvas.captureStream();
}
```

### 2. **Noise Suppression**

```javascript
// Use Krisp AI or browser's built-in noise suppression
const constraints = {
  audio: {
    echoCancellation: true,
    noiseSuppression: true,
    autoGainControl: true,
    // Browser-specific
    googNoiseSuppression: true,
    googHighpassFilter: true
  }
};
```

### 3. **Simulcast (Multiple Quality Streams)**

```javascript
// Send multiple quality layers
const sender = pc.addTrack(videoTrack);

const params = sender.getParameters();
if (!params.encodings) {
  params.encodings = [
    { rid: 'h', maxBitrate: 2500000 }, // High: 1080p
    { rid: 'm', scaleResolutionDownBy: 2, maxBitrate: 900000 }, // Med: 720p
    { rid: 'l', scaleResolutionDownBy: 4, maxBitrate: 300000 }  // Low: 360p
  ];
}
await sender.setParameters(params);
```

### 4. **Adaptive Bitrate**

```javascript
// Monitor connection quality and adjust bitrate
pc.getSenders().forEach(sender => {
  const stats = await sender.getStats();

  stats.forEach(report => {
    if (report.type === 'outbound-rtp') {
      const packetLoss = report.packetsLost / report.packetsSent;

      if (packetLoss > 0.05) {
        // High packet loss, reduce bitrate
        sender.setParameters({
          encodings: [{ maxBitrate: 500000 }]
        });
      }
    }
  });
});
```

---

## Scaling Strategy

### 1. **Horizontal Scaling**

| Component | Scaling Strategy |
|-----------|------------------|
| **Signaling Servers** | Stateless, scale with load balancer |
| **SFU Servers** | Partition by meeting_id (consistent hashing) |
| **TURN Servers** | Regional deployment, scale by bandwidth |
| **API Servers** | Stateless, auto-scaling groups |
| **Recording** | Queue-based (Kafka), scale workers |

### 2. **Regional Distribution**

```
US-East (Primary)
├── 200 SFU servers
├── 50 TURN servers
└── 20 Signaling servers

US-West
├── 150 SFU servers
├── 40 TURN servers
└── 15 Signaling servers

EU-Central
├── 180 SFU servers
├── 45 TURN servers
└── 18 Signaling servers

Asia-Pacific
├── 120 SFU servers
├── 30 TURN servers
└── 12 Signaling servers
```

### 3. **Database Sharding**

```sql
-- Shard by user_id (for users, meetings)
-- Shard key: user_id % num_shards

-- Shard 0: users with user_id % 4 = 0
-- Shard 1: users with user_id % 4 = 1
-- Shard 2: users with user_id % 4 = 2
-- Shard 3: users with user_id % 4 = 3
```

### 4. **Caching Strategy**

```
Redis Cache:
├── Meeting metadata (TTL: 1 hour)
├── Active participants (real-time)
├── User presence (TTL: 5 minutes)
└── Rate limiting counters

CDN Cache:
├── Recordings (TTL: 90 days)
├── Profile avatars (TTL: 7 days)
└── Static assets (TTL: 30 days)
```

### 5. **Load Balancing**

```nginx
# Nginx config for SFU load balancing
upstream sfu_servers {
    least_conn;  # Route to server with fewest connections

    server sfu-1.example.com:443 max_fails=3 fail_timeout=30s;
    server sfu-2.example.com:443 max_fails=3 fail_timeout=30s;
    server sfu-3.example.com:443 max_fails=3 fail_timeout=30s;
}

server {
    listen 443 ssl http2;

    location /sfu/ {
        proxy_pass https://sfu_servers;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
    }
}
```

---

## Monitoring & Observability

### 1. **Metrics to Track**

```javascript
// Prometheus metrics
const promClient = require('prom-client');

// Call quality metrics
const callQualityGauge = new promClient.Gauge({
  name: 'call_quality_score',
  help: 'Call quality score (0-5)',
  labelNames: ['meeting_id', 'region']
});

const packetLossGauge = new promClient.Gauge({
  name: 'packet_loss_percent',
  help: 'Packet loss percentage',
  labelNames: ['meeting_id', 'user_id']
});

// Infrastructure metrics
const activeMeetingsGauge = new promClient.Gauge({
  name: 'active_meetings_total',
  help: 'Total number of active meetings'
});

const sfuBandwidthGauge = new promClient.Gauge({
  name: 'sfu_bandwidth_mbps',
  help: 'SFU server bandwidth usage in Mbps',
  labelNames: ['server_id']
});
```

### 2. **Logging**

```javascript
// Structured logging with Winston
const winston = require('winston');

const logger = winston.createLogger({
  format: winston.format.json(),
  transports: [
    new winston.transports.File({ filename: 'error.log', level: 'error' }),
    new winston.transports.File({ filename: 'combined.log' })
  ]
});

// Log call events
logger.info('Call started', {
  meeting_id: 'abc-123',
  participants: 5,
  region: 'us-east-1',
  sfu_server: 'sfu-042'
});
```

### 3. **Distributed Tracing**

```javascript
// OpenTelemetry for distributed tracing
const { trace } = require('@opentelemetry/api');

const tracer = trace.getTracer('video-calling-service');

async function joinMeeting(meetingId, userId) {
  const span = tracer.startSpan('join_meeting');

  try {
    span.setAttribute('meeting_id', meetingId);
    span.setAttribute('user_id', userId);

    await allocateSFU(meetingId);
    await connectSignaling(userId);

    span.setStatus({ code: 0 });
  } catch (error) {
    span.setStatus({ code: 1, message: error.message });
    throw error;
  } finally {
    span.end();
  }
}
```

### 4. **Alerting Rules**

```yaml
# Prometheus alerting rules
groups:
  - name: video_calling_alerts
    rules:
      - alert: HighPacketLoss
        expr: avg(packet_loss_percent) > 5
        for: 5m
        annotations:
          summary: "High packet loss detected"

      - alert: SFUOverloaded
        expr: sfu_active_participants > 450
        for: 2m
        annotations:
          summary: "SFU server overloaded"

      - alert: TurnBandwidthHigh
        expr: turn_bandwidth_gbps > 100
        for: 10m
        annotations:
          summary: "TURN bandwidth usage very high (expensive!)"
```

---

## Summary

This video calling system design covers:

1. ✅ **Architecture**: Signaling, SFU, TURN/STUN servers
2. ✅ **WebRTC**: P2P and SFU-based group calls
3. ✅ **Scalability**: 10M concurrent users, 40K SFU servers
4. ✅ **Low Latency**: <300ms end-to-end
5. ✅ **Recording**: FFmpeg-based recording to S3
6. ✅ **Security**: E2EE option, DTLS-SRTP, authentication
7. ✅ **Advanced Features**: Virtual backgrounds, simulcast, adaptive bitrate
8. ✅ **Monitoring**: Prometheus metrics, distributed tracing

### Key Takeaways

- Use **SFU (Selective Forwarding Unit)** for group calls (better than MCU)
- Use **P2P** for 1-on-1 calls to save costs
- **TURN servers** are expensive (relay bandwidth) - only 10% of users need them
- **Simulcast** allows clients to receive appropriate quality based on their bandwidth
- **Regional distribution** is critical for low latency
- **Recording** can consume massive storage - use compression and lifecycle policies

### Further Reading
- [architecture.html](./architecture.html) - Interactive architecture diagram
- [flow-diagram.html](./flow-diagram.html) - Call flow visualizations
- [scale-estimation-guide.md](./scale-estimation-guide.md) - Detailed capacity planning
- [tech-stack.md](./tech-stack.md) - Industry solutions and DevOps tools

---

**Next Steps**: Dive into the [scale-estimation-guide.md](./scale-estimation-guide.md) to practice capacity planning for video calling systems, or check [tech-stack.md](./tech-stack.md) to see how Zoom, Google Meet, and other industry leaders implement these systems.
