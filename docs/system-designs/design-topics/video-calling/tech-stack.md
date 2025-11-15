# Tech Stack & Industry Solutions for Video Calling Systems

## Table of Contents
1. [Industry Tech Stacks](#industry-tech-stacks)
2. [WebRTC Libraries & SDKs](#webrtc-libraries--sdks)
3. [SFU Solutions](#sfu-solutions)
4. [Signaling Servers](#signaling-servers)
5. [TURN/STUN Servers](#turnstun-servers)
6. [Media Processing](#media-processing)
7. [Infrastructure & DevOps](#infrastructure--devops)
8. [Monitoring & Observability](#monitoring--observability)
9. [Cloud Services](#cloud-services)
10. [Complete Deployment Example](#complete-deployment-example)

---

## Industry Tech Stacks

### 1. **Zoom**

**Architecture**:
```
Technology Stack:
├── Backend: C++, Go, Java
├── Client: Electron (Desktop), Swift/Kotlin (Mobile)
├── Media: Custom Multimedia Router (MMR)
├── Signaling: Custom protocol over WebSocket
├── Database: PostgreSQL, MySQL, MongoDB
├── Cache: Redis, Memcached
├── Message Queue: Kafka
├── Storage: AWS S3, Azure Blob
└── CDN: Cloudflare, AWS CloudFront
```

**Key Technologies**:
- **Custom MMR (Multimedia Router)**: Proprietary SFU/MCU hybrid
- **Simulcast**: Multiple quality streams (360p, 720p, 1080p)
- **H.264 SVC**: Scalable Video Coding for bandwidth adaptation
- **Opus Audio Codec**: High-quality, low-latency audio
- **End-to-End Encryption**: AES-256 GCM

**Infrastructure**:
- **Multi-cloud**: AWS (primary), Azure, Oracle Cloud
- **17 data centers** globally
- **Kubernetes** for orchestration
- **Terraform** for infrastructure as code

**Monitoring**:
- **Datadog** for APM and infrastructure monitoring
- **Custom analytics** for call quality metrics (MOS scores)
- **Splunk** for log aggregation

---

### 2. **Google Meet**

**Architecture**:
```
Technology Stack:
├── Backend: Go, Java, C++
├── Client: Angular (Web), Swift/Kotlin (Mobile)
├── Media: WebRTC (SFU mode)
├── Signaling: gRPC over HTTP/2
├── Database: Cloud Spanner, Bigtable
├── Cache: Memorystore (Redis)
├── Message Queue: Cloud Pub/Sub
├── Storage: Google Cloud Storage
└── CDN: Google Cloud CDN
```

**Key Technologies**:
- **WebRTC**: Standard WebRTC implementation
- **SFU**: Google's custom SFU based on libjingle
- **VP8/VP9 Codecs**: Google's video codecs (open-source)
- **Opus Audio**: Standard WebRTC audio codec
- **QUIC Protocol**: For faster connection establishment

**Infrastructure**:
- **Google Cloud Platform (GCP)** exclusively
- **30+ regions** worldwide
- **GKE (Google Kubernetes Engine)** for container orchestration
- **Deployment Manager** for infrastructure as code

**Monitoring**:
- **Cloud Monitoring** (formerly Stackdriver)
- **Cloud Trace** for distributed tracing
- **Cloud Logging** for centralized logs

---

### 3. **Jitsi (Open Source)**

**Architecture**:
```
Technology Stack:
├── Backend: Java (Jitsi Videobridge)
├── Frontend: React
├── Media: WebRTC (SFU - Videobridge)
├── Signaling: XMPP (Prosody), WebSocket
├── Database: PostgreSQL (optional)
├── Cache: Redis (optional)
├── Recording: Jibri (FFmpeg-based)
└── Deployment: Docker, Kubernetes
```

**Key Components**:
- **Jitsi Videobridge (JVB)**: Open-source SFU written in Java
- **Jicofo**: Conference focus component (manages rooms)
- **Prosody**: XMPP server for signaling
- **Jitsi Meet**: React-based web client
- **Jibri**: Recording and streaming service

**Infrastructure**:
- **Docker Compose** for simple deployments
- **Kubernetes Helm Charts** for production
- **Can run on AWS, Azure, GCP, or self-hosted**

**Monitoring**:
- **Prometheus** + **Grafana** (community dashboards)
- **ELK Stack** (Elasticsearch, Logstash, Kibana) for logs

---

### 4. **Microsoft Teams**

**Architecture**:
```
Technology Stack:
├── Backend: .NET Core, C++, TypeScript
├── Client: Electron (Desktop), React Native (Mobile)
├── Media: Azure Communication Services (ACS)
├── Signaling: Custom over HTTPS/WebSocket
├── Database: Azure Cosmos DB, SQL Server
├── Cache: Azure Cache for Redis
├── Message Queue: Azure Service Bus
├── Storage: Azure Blob Storage
└── CDN: Azure CDN
```

**Key Technologies**:
- **Azure Communication Services**: Managed WebRTC infrastructure
- **H.264/H.265**: Video codecs
- **Opus/SILK**: Audio codecs
- **Media Processor**: Intelligent video routing
- **Azure Media Services**: For recording and streaming

**Infrastructure**:
- **Microsoft Azure** exclusively
- **60+ Azure regions**
- **Azure Kubernetes Service (AKS)**
- **ARM Templates / Bicep** for IaC

**Monitoring**:
- **Azure Monitor**
- **Application Insights**
- **Azure Sentinel** for security

---

### 5. **Discord**

**Architecture**:
```
Technology Stack:
├── Backend: Elixir, Rust, Python, Go
├── Client: React (Web), React Native (Mobile)
├── Media: Custom WebRTC implementation (Rust)
├── Voice: Custom Opus implementation
├── Database: ScyllaDB (Cassandra), PostgreSQL
├── Cache: Redis
├── Message Queue: NATS
├── Storage: Google Cloud Storage
└── CDN: Cloudflare
```

**Key Technologies**:
- **Rust WebRTC**: Custom WebRTC stack for low latency
- **Opus Audio**: Optimized for gaming voice chat
- **Dave (WebRTC server)**: Written in Rust
- **ScyllaDB**: High-performance NoSQL database
- **Elixir/Erlang**: For real-time messaging

**Infrastructure**:
- **Google Cloud Platform**
- **Self-managed Kubernetes**
- **High availability across 30+ locations**

---

## WebRTC Libraries & SDKs

### 1. **Native WebRTC (Browser)**

```javascript
// Standard WebRTC API (all modern browsers)
const pc = new RTCPeerConnection({
  iceServers: [
    { urls: 'stun:stun.l.google.com:19302' }
  ]
});

const stream = await navigator.mediaDevices.getUserMedia({
  video: true,
  audio: true
});
```

**Pros**: Built-in, no dependencies
**Cons**: Limited customization

---

### 2. **mediasoup (Node.js SFU)**

```bash
npm install mediasoup
```

```javascript
const mediasoup = require('mediasoup');

const worker = await mediasoup.createWorker({
  logLevel: 'warn',
  rtcMinPort: 40000,
  rtcMaxPort: 49999
});

const router = await worker.createRouter({
  mediaCodecs: [
    {
      kind: 'audio',
      mimeType: 'audio/opus',
      clockRate: 48000,
      channels: 2
    }
  ]
});
```

**Use Case**: Production-grade SFU for Node.js applications
**Companies Using**: Daily.co, Whereby
**Pros**:
- Excellent documentation
- TypeScript support
- Active community
- High performance

**Cons**:
- Node.js only (server-side)
- Requires understanding of WebRTC internals

**GitHub**: https://github.com/versatica/mediasoup

---

### 3. **Janus Gateway (C)**

```bash
# Install Janus
git clone https://github.com/meetecho/janus-gateway.git
cd janus-gateway
sh autogen.sh
./configure --prefix=/opt/janus
make
make install
```

```javascript
// Client-side Janus JavaScript SDK
const janus = new Janus({
  server: 'wss://janus.example.com',
  success: function() {
    // Attach to VideoRoom plugin
    janus.attach({
      plugin: 'janus.plugin.videoroom',
      success: function(pluginHandle) {
        // Join room
      }
    });
  }
});
```

**Use Case**: Flexible WebRTC gateway with plugins
**Pros**:
- Multiple plugins (SFU, MCU, SIP gateway, recording)
- C-based (very fast)
- Mature and stable

**Cons**:
- Steeper learning curve
- C code harder to modify

**GitHub**: https://github.com/meetecho/janus-gateway

---

### 4. **Jitsi Videobridge**

```yaml
# docker-compose.yml
version: '3'
services:
  jitsi-videobridge:
    image: jitsi/jvb:latest
    ports:
      - "10000:10000/udp"
    environment:
      - JVB_BREWERY_MUC=JvbBrewery@internal.auth.meet.jitsi
      - JVB_AUTH_USER=jvb
      - JVB_STUN_SERVERS=stun.l.google.com:19302
```

**Use Case**: Open-source video conferencing
**Pros**:
- Complete solution (includes UI)
- Battle-tested (millions of users)
- Easy deployment

**Cons**:
- Java-based (higher memory usage)
- Less flexible than mediasoup

**GitHub**: https://github.com/jitsi/jitsi-videobridge

---

### 5. **Pion (Go WebRTC)**

```go
package main

import (
    "github.com/pion/webrtc/v3"
)

func main() {
    config := webrtc.Configuration{
        ICEServers: []webrtc.ICEServer{
            {
                URLs: []string{"stun:stun.l.google.com:19302"},
            },
        },
    }

    peerConnection, err := webrtc.NewPeerConnection(config)
    if err != nil {
        panic(err)
    }
}
```

**Use Case**: Go-based WebRTC applications
**Pros**:
- Pure Go implementation
- Great for microservices
- High performance

**Cons**:
- Smaller community than mediasoup
- Less mature

**GitHub**: https://github.com/pion/webrtc

---

### 6. **LiveKit (Modern SFU)**

```bash
docker run --rm -p 7880:7880 \
  -e LIVEKIT_API_KEY=your-api-key \
  -e LIVEKIT_API_SECRET=your-secret \
  livekit/livekit-server
```

```typescript
// Client SDK
import { Room } from 'livekit-client';

const room = new Room();
await room.connect('wss://livekit.example.com', token);

await room.localParticipant.setCameraEnabled(true);
await room.localParticipant.setMicrophoneEnabled(true);
```

**Use Case**: Modern, cloud-native video infrastructure
**Pros**:
- Easy to use
- Excellent SDKs (JS, Swift, Kotlin, Flutter)
- Built-in recording, simulcast
- Cloud or self-hosted

**Cons**:
- Newer (less battle-tested)
- Commercial product (but has open-source version)

**GitHub**: https://github.com/livekit/livekit

---

## SFU Solutions Comparison

| Solution | Language | Performance | Ease of Use | Community | Best For |
|----------|----------|-------------|-------------|-----------|----------|
| **mediasoup** | Node.js | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Production apps |
| **Janus** | C | ⭐⭐⭐⭐⭐ | ⭐⭐ | ⭐⭐⭐⭐ | Flexible use cases |
| **Jitsi** | Java | ⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | Quick deployment |
| **LiveKit** | Go | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | Modern apps |
| **Pion** | Go | ⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | Custom solutions |

---

## Signaling Servers

### 1. **Socket.io (Node.js)**

```javascript
const io = require('socket.io')(3000);

io.on('connection', (socket) => {
  socket.on('join', (meetingId) => {
    socket.join(meetingId);
    socket.to(meetingId).emit('participant-joined', socket.id);
  });

  socket.on('offer', (data) => {
    socket.to(data.to).emit('offer', {
      from: socket.id,
      sdp: data.sdp
    });
  });

  socket.on('answer', (data) => {
    socket.to(data.to).emit('answer', {
      from: socket.id,
      sdp: data.sdp
    });
  });

  socket.on('ice-candidate', (data) => {
    socket.to(data.to).emit('ice-candidate', {
      from: socket.id,
      candidate: data.candidate
    });
  });
});
```

**Pros**: Easy to use, battle-tested
**Cons**: Can be resource-intensive at scale

---

### 2. **uWebSockets.js (High Performance)**

```javascript
const uWS = require('uWebSockets.js');

const app = uWS.App()
  .ws('/*', {
    open: (ws) => {
      console.log('Client connected');
    },
    message: (ws, message, isBinary) => {
      const data = JSON.parse(Buffer.from(message).toString());
      // Handle signaling
    },
    close: (ws) => {
      console.log('Client disconnected');
    }
  })
  .listen(3000, (token) => {
    console.log('Signaling server running on port 3000');
  });
```

**Pros**: 10x faster than Socket.io
**Cons**: Lower-level API

---

### 3. **Centrifugo (Go)**

```bash
docker run -d -p 8000:8000 centrifugo/centrifugo \
  centrifugo --config=config.json
```

**Pros**: Scalable, language-agnostic
**Cons**: More complex setup

---

## TURN/STUN Servers

### 1. **Coturn (Open Source)**

```bash
# Install Coturn
apt-get install coturn

# Configuration
cat > /etc/turnserver.conf << EOF
listening-port=3478
tls-listening-port=5349
external-ip=YOUR_PUBLIC_IP
realm=turn.example.com
lt-cred-mech
user=username:password
fingerprint
EOF

# Start
systemctl start coturn
```

**Use Case**: Most popular open-source TURN server
**GitHub**: https://github.com/coturn/coturn

---

### 2. **Pion TURN (Go)**

```go
package main

import (
    "github.com/pion/turn/v2"
)

func main() {
    s, err := turn.NewServer(turn.ServerConfig{
        Realm: "example.com",
        AuthHandler: func(username, realm string, srcAddr net.Addr) ([]byte, bool) {
            return turn.GenerateAuthKey(username, realm, "password"), true
        },
    })

    s.Start()
}
```

**Use Case**: Embedded TURN server in Go applications

---

### 3. **Commercial TURN Services**

| Service | Pricing | Bandwidth | Regions |
|---------|---------|-----------|---------|
| **Twilio TURN** | $0.002/min | Unlimited | Global |
| **Xirsys** | $9.95/month | 10 GB | 30+ regions |
| **Metered TURN** | $0.004/GB | Pay-as-you-go | Global |

---

## Media Processing

### 1. **FFmpeg (Recording, Transcoding)**

```bash
# Record RTP stream to MP4
ffmpeg -protocol_whitelist file,rtp,udp \
  -i rtp://0.0.0.0:5004 \
  -c:v libx264 -preset veryfast -crf 23 \
  -c:a aac -b:a 128k \
  output.mp4

# Transcode to multiple qualities
ffmpeg -i input.mp4 \
  -vf scale=1920:1080 -c:v libx264 -b:v 4000k -c:a aac 1080p.mp4 \
  -vf scale=1280:720 -c:v libx264 -b:v 2500k -c:a aac 720p.mp4 \
  -vf scale=640:360 -c:v libx264 -b:v 800k -c:a aac 360p.mp4
```

**Use Case**: Industry standard for video processing

---

### 2. **GStreamer**

```bash
# Stream to RTMP
gst-launch-1.0 -v \
  udpsrc port=5004 ! \
  rtph264depay ! h264parse ! \
  flvmux ! \
  rtmpsink location='rtmp://live.twitch.tv/app/STREAM_KEY'
```

**Use Case**: Real-time streaming pipelines

---

### 3. **MediaMTX (RTMP/WebRTC Server)**

```bash
docker run --rm -it \
  -p 8554:8554 \
  -p 1935:1935 \
  -p 8889:8889 \
  aler9/mediamtx
```

**Use Case**: Unified streaming server (RTMP, WebRTC, HLS)

---

## Infrastructure & DevOps

### Kubernetes Deployment

**Namespace & ConfigMap**:
```yaml
# namespace.yaml
apiVersion: v1
kind: Namespace
metadata:
  name: video-calling

---
# configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: app-config
  namespace: video-calling
data:
  TURN_SECRET: "your-turn-secret"
  STUN_SERVERS: "stun:stun.l.google.com:19302"
  REDIS_URL: "redis://redis-service:6379"
  DATABASE_URL: "postgresql://postgres:5432/meetings"
```

**SFU Deployment**:
```yaml
# sfu-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sfu-server
  namespace: video-calling
spec:
  replicas: 10
  selector:
    matchLabels:
      app: sfu
  template:
    metadata:
      labels:
        app: sfu
    spec:
      containers:
      - name: mediasoup
        image: myregistry/mediasoup-server:latest
        ports:
        - containerPort: 3000
          protocol: TCP
        - containerPort: 40000
          protocol: UDP
          hostPort: 40000
        - containerPort: 49999
          protocol: UDP
          hostPort: 49999
        env:
        - name: RTC_MIN_PORT
          value: "40000"
        - name: RTC_MAX_PORT
          value: "49999"
        - name: PUBLIC_IP
          valueFrom:
            fieldRef:
              fieldPath: status.hostIP
        resources:
          requests:
            memory: "2Gi"
            cpu: "2"
          limits:
            memory: "4Gi"
            cpu: "4"
        livenessProbe:
          httpGet:
            path: /health
            port: 3000
          initialDelaySeconds: 30
          periodSeconds: 10

---
apiVersion: v1
kind: Service
metadata:
  name: sfu-service
  namespace: video-calling
spec:
  type: LoadBalancer
  selector:
    app: sfu
  ports:
  - name: http
    port: 3000
    targetPort: 3000
  - name: rtp-start
    port: 40000
    targetPort: 40000
    protocol: UDP
  - name: rtp-end
    port: 49999
    targetPort: 49999
    protocol: UDP
```

**Signaling Server Deployment**:
```yaml
# signaling-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: signaling-server
  namespace: video-calling
spec:
  replicas: 5
  selector:
    matchLabels:
      app: signaling
  template:
    metadata:
      labels:
        app: signaling
    spec:
      containers:
      - name: signaling
        image: myregistry/signaling-server:latest
        ports:
        - containerPort: 8080
        envFrom:
        - configMapRef:
            name: app-config
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1"

---
apiVersion: v1
kind: Service
metadata:
  name: signaling-service
  namespace: video-calling
spec:
  type: ClusterIP
  selector:
    app: signaling
  ports:
  - port: 8080
    targetPort: 8080

---
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: signaling-ingress
  namespace: video-calling
  annotations:
    kubernetes.io/ingress.class: nginx
    cert-manager.io/cluster-issuer: letsencrypt-prod
spec:
  tls:
  - hosts:
    - signal.example.com
    secretName: signaling-tls
  rules:
  - host: signal.example.com
    http:
      paths:
      - path: /
        pathType: Prefix
        backend:
          service:
            name: signaling-service
            port:
              number: 8080
```

**TURN Server Deployment**:
```yaml
# turn-deployment.yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: turn-server
  namespace: video-calling
spec:
  selector:
    matchLabels:
      app: turn
  template:
    metadata:
      labels:
        app: turn
    spec:
      hostNetwork: true
      containers:
      - name: coturn
        image: coturn/coturn:latest
        ports:
        - containerPort: 3478
          protocol: UDP
        - containerPort: 3478
          protocol: TCP
        volumeMounts:
        - name: config
          mountPath: /etc/coturn
        env:
        - name: EXTERNAL_IP
          valueFrom:
            fieldRef:
              fieldPath: status.hostIP
        resources:
          requests:
            memory: "1Gi"
            cpu: "1"
          limits:
            memory: "2Gi"
            cpu: "2"
      volumes:
      - name: config
        configMap:
          name: turn-config

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: turn-config
  namespace: video-calling
data:
  turnserver.conf: |
    listening-port=3478
    realm=turn.example.com
    lt-cred-mech
    fingerprint
    log-file=stdout
```

**PostgreSQL StatefulSet**:
```yaml
# postgres-statefulset.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: video-calling
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
          value: meetings
        - name: POSTGRES_USER
          value: postgres
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
# redis-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: video-calling
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
        - --cluster-enabled yes
        resources:
          requests:
            memory: "2Gi"
            cpu: "1"
          limits:
            memory: "4Gi"
            cpu: "2"
        volumeMounts:
        - name: data
          mountPath: /data
      volumes:
      - name: data
        persistentVolumeClaim:
          claimName: redis-pvc
```

**HorizontalPodAutoscaler**:
```yaml
# hpa.yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: sfu-hpa
  namespace: video-calling
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: sfu-server
  minReplicas: 5
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

### Terraform Infrastructure

**AWS Infrastructure**:
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
    Name = "video-calling-vpc"
  }
}

# Subnets
resource "aws_subnet" "public" {
  count                   = 3
  vpc_id                  = aws_vpc.main.id
  cidr_block              = "10.0.${count.index + 1}.0/24"
  availability_zone       = data.aws_availability_zones.available.names[count.index]
  map_public_ip_on_launch = true

  tags = {
    Name = "public-subnet-${count.index + 1}"
  }
}

# EKS Cluster
resource "aws_eks_cluster" "main" {
  name     = "video-calling-cluster"
  role_arn = aws_iam_role.eks_cluster.arn
  version  = "1.28"

  vpc_config {
    subnet_ids = aws_subnet.public[*].id
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_cluster_policy
  ]
}

# EKS Node Group
resource "aws_eks_node_group" "main" {
  cluster_name    = aws_eks_cluster.main.name
  node_group_name = "sfu-nodes"
  node_role_arn   = aws_iam_role.eks_node.arn
  subnet_ids      = aws_subnet.public[*].id

  instance_types = ["c5.2xlarge"]  # 8 vCPU, 16 GB RAM

  scaling_config {
    desired_size = 10
    max_size     = 100
    min_size     = 5
  }

  depends_on = [
    aws_iam_role_policy_attachment.eks_worker_node_policy
  ]
}

# RDS PostgreSQL
resource "aws_db_instance" "postgres" {
  identifier        = "video-calling-db"
  engine            = "postgres"
  engine_version    = "15.3"
  instance_class    = "db.r6g.2xlarge"
  allocated_storage = 500
  storage_type      = "gp3"
  iops              = 12000

  db_name  = "meetings"
  username = "postgres"
  password = var.db_password

  multi_az               = true
  publicly_accessible    = false
  vpc_security_group_ids = [aws_security_group.rds.id]
  db_subnet_group_name   = aws_db_subnet_group.main.name

  backup_retention_period = 7
  backup_window           = "03:00-04:00"
  maintenance_window      = "sun:04:00-sun:05:00"

  tags = {
    Name = "video-calling-postgres"
  }
}

# ElastiCache Redis
resource "aws_elasticache_replication_group" "redis" {
  replication_group_id       = "video-calling-redis"
  replication_group_description = "Redis cluster for video calling"
  engine                     = "redis"
  engine_version             = "7.0"
  node_type                  = "cache.r6g.xlarge"
  num_cache_clusters         = 3
  parameter_group_name       = "default.redis7"
  port                       = 6379
  subnet_group_name          = aws_elasticache_subnet_group.main.name
  security_group_ids         = [aws_security_group.redis.id]
  automatic_failover_enabled = true

  tags = {
    Name = "video-calling-redis"
  }
}

# S3 Bucket for Recordings
resource "aws_s3_bucket" "recordings" {
  bucket = "video-calling-recordings-${var.environment}"

  tags = {
    Name = "recordings-bucket"
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "recordings" {
  bucket = aws_s3_bucket.recordings.id

  rule {
    id     = "delete-old-recordings"
    status = "Enabled"

    expiration {
      days = 90
    }

    transition {
      days          = 30
      storage_class = "STANDARD_IA"
    }

    transition {
      days          = 60
      storage_class = "GLACIER"
    }
  }
}

# CloudFront Distribution
resource "aws_cloudfront_distribution" "recordings" {
  origin {
    domain_name = aws_s3_bucket.recordings.bucket_regional_domain_name
    origin_id   = "S3-recordings"

    s3_origin_config {
      origin_access_identity = aws_cloudfront_origin_access_identity.main.cloudfront_access_identity_path
    }
  }

  enabled             = true
  default_root_object = "index.html"

  default_cache_behavior {
    allowed_methods  = ["GET", "HEAD"]
    cached_methods   = ["GET", "HEAD"]
    target_origin_id = "S3-recordings"

    forwarded_values {
      query_string = false

      cookies {
        forward = "none"
      }
    }

    viewer_protocol_policy = "redirect-to-https"
    min_ttl                = 0
    default_ttl            = 86400
    max_ttl                = 31536000
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

# Variables
variable "aws_region" {
  default = "us-east-1"
}

variable "environment" {
  default = "production"
}

variable "db_password" {
  type      = string
  sensitive = true
}
```

---

## Monitoring & Observability

### Prometheus Configuration

```yaml
# prometheus.yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  # SFU Servers
  - job_name: 'sfu-servers'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - video-calling
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: sfu

  # Signaling Servers
  - job_name: 'signaling-servers'
    kubernetes_sd_configs:
      - role: pod
        namespaces:
          names:
            - video-calling
    relabel_configs:
      - source_labels: [__meta_kubernetes_pod_label_app]
        action: keep
        regex: signaling

  # PostgreSQL
  - job_name: 'postgres'
    static_configs:
      - targets: ['postgres-exporter:9187']

  # Redis
  - job_name: 'redis'
    static_configs:
      - targets: ['redis-exporter:9121']

# Alerting rules
rule_files:
  - '/etc/prometheus/rules/*.yaml'
```

**Alerting Rules**:
```yaml
# alert-rules.yaml
groups:
  - name: video_calling_alerts
    interval: 30s
    rules:
      # High packet loss
      - alert: HighPacketLoss
        expr: avg(webrtc_packet_loss_percent) > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High packet loss detected"
          description: "Packet loss is {{ $value }}% (threshold: 5%)"

      # SFU overloaded
      - alert: SFUOverloaded
        expr: sfu_active_participants > 450
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "SFU server {{ $labels.instance }} is overloaded"
          description: "{{ $labels.instance }} has {{ $value }} participants (max: 500)"

      # High CPU usage
      - alert: HighCPUUsage
        expr: rate(process_cpu_seconds_total[5m]) * 100 > 80
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage on {{ $labels.instance }}"
          description: "CPU usage is {{ $value }}%"

      # Database connection pool exhausted
      - alert: DatabaseConnectionPoolExhausted
        expr: pg_stat_database_numbackends / pg_settings_max_connections > 0.9
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "Database connection pool nearly exhausted"
          description: "{{ $value }}% of connections in use"

      # TURN bandwidth spike
      - alert: TURNBandwidthHigh
        expr: rate(turn_bytes_sent[5m]) + rate(turn_bytes_received[5m]) > 100e9  # 100 Gbps
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "TURN bandwidth very high (expensive!)"
          description: "TURN traffic is {{ $value | humanize }}bps"
```

### Grafana Dashboards

```json
{
  "dashboard": {
    "title": "Video Calling System Overview",
    "panels": [
      {
        "title": "Active Meetings",
        "targets": [
          {
            "expr": "sum(active_meetings)"
          }
        ]
      },
      {
        "title": "Total Participants",
        "targets": [
          {
            "expr": "sum(active_participants)"
          }
        ]
      },
      {
        "title": "Average Call Quality (MOS)",
        "targets": [
          {
            "expr": "avg(call_quality_mos_score)"
          }
        ]
      },
      {
        "title": "Packet Loss %",
        "targets": [
          {
            "expr": "avg(webrtc_packet_loss_percent) by (meeting_id)"
          }
        ]
      },
      {
        "title": "SFU CPU Usage",
        "targets": [
          {
            "expr": "rate(process_cpu_seconds_total{job=\"sfu-servers\"}[5m]) * 100"
          }
        ]
      },
      {
        "title": "Bandwidth Usage",
        "targets": [
          {
            "expr": "sum(rate(sfu_bytes_sent[5m]) + rate(sfu_bytes_received[5m])) by (instance)"
          }
        ]
      }
    ]
  }
}
```

### OpenTelemetry Configuration

```yaml
# otel-collector-config.yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317
      http:
        endpoint: 0.0.0.0:4318

processors:
  batch:
    timeout: 10s
    send_batch_size: 1024

exporters:
  jaeger:
    endpoint: jaeger:14250
    tls:
      insecure: true

  prometheus:
    endpoint: 0.0.0.0:8889

  logging:
    loglevel: debug

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [batch]
      exporters: [jaeger, logging]

    metrics:
      receivers: [otlp]
      processors: [batch]
      exporters: [prometheus, logging]
```

---

## Cloud Services

### AWS Services

| Service | Use Case | Alternative |
|---------|----------|-------------|
| **AWS Kinesis Video Streams** | WebRTC signaling & streaming | Build custom SFU |
| **Amazon Chime SDK** | Fully managed video calling | Self-hosted Jitsi |
| **CloudFront** | CDN for recordings | Cloudflare |
| **S3** | Recording storage | MinIO (self-hosted) |
| **EKS** | Kubernetes orchestration | Self-managed K8s |
| **RDS** | Managed PostgreSQL | Self-hosted Postgres |
| **ElastiCache** | Managed Redis | Self-hosted Redis |

**Amazon Chime SDK Example**:
```javascript
const { ChimeSDK } = require('amazon-chime-sdk-js');

const meetingSession = new ChimeSDK.MeetingSession(
  configuration,
  logger,
  deviceController
);

await meetingSession.audioVideo.start();
```

---

### Azure Services

| Service | Use Case |
|---------|----------|
| **Azure Communication Services** | Managed WebRTC infrastructure |
| **Azure Media Services** | Recording, transcoding, streaming |
| **Azure CDN** | Content delivery |
| **Blob Storage** | Recording storage |
| **AKS** | Kubernetes |
| **Cosmos DB** | NoSQL database |

---

### GCP Services

| Service | Use Case |
|---------|----------|
| **Vertex AI** | Background blur, noise suppression (ML) |
| **Cloud Storage** | Recording storage |
| **Cloud CDN** | Content delivery |
| **GKE** | Kubernetes |
| **Cloud Spanner** | Globally distributed database |

---

## Complete Deployment Example

### Docker Compose (Development)

```yaml
# docker-compose.yml
version: '3.8'

services:
  postgres:
    image: postgres:15
    environment:
      POSTGRES_DB: meetings
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
    volumes:
      - redis_data:/data

  signaling:
    build: ./signaling-server
    ports:
      - "8080:8080"
    environment:
      REDIS_URL: redis://redis:6379
      DATABASE_URL: postgresql://postgres:postgres@postgres:5432/meetings
    depends_on:
      - postgres
      - redis

  sfu:
    build: ./sfu-server
    ports:
      - "3000:3000"
      - "40000-49999:40000-49999/udp"
    environment:
      PUBLIC_IP: ${PUBLIC_IP}
      RTC_MIN_PORT: 40000
      RTC_MAX_PORT: 49999
    depends_on:
      - redis

  turn:
    image: coturn/coturn:latest
    network_mode: host
    volumes:
      - ./turnserver.conf:/etc/coturn/turnserver.conf

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3001:3000"
    environment:
      - GF_SECURITY_ADMIN_PASSWORD=admin
    volumes:
      - grafana_data:/var/lib/grafana

volumes:
  postgres_data:
  redis_data:
  prometheus_data:
  grafana_data:
```

---

## CI/CD Pipeline

### GitHub Actions

```yaml
# .github/workflows/deploy.yml
name: Deploy Video Calling System

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

      - name: Build and push SFU image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/sfu-server:$IMAGE_TAG ./sfu-server
          docker push $ECR_REGISTRY/sfu-server:$IMAGE_TAG

      - name: Build and push Signaling image
        env:
          ECR_REGISTRY: ${{ steps.login-ecr.outputs.registry }}
          IMAGE_TAG: ${{ github.sha }}
        run: |
          docker build -t $ECR_REGISTRY/signaling-server:$IMAGE_TAG ./signaling-server
          docker push $ECR_REGISTRY/signaling-server:$IMAGE_TAG

      - name: Update kubeconfig
        run: |
          aws eks update-kubeconfig --name video-calling-cluster --region us-east-1

      - name: Deploy to Kubernetes
        run: |
          kubectl set image deployment/sfu-server sfu=$ECR_REGISTRY/sfu-server:$IMAGE_TAG -n video-calling
          kubectl set image deployment/signaling-server signaling=$ECR_REGISTRY/signaling-server:$IMAGE_TAG -n video-calling
          kubectl rollout status deployment/sfu-server -n video-calling
          kubectl rollout status deployment/signaling-server -n video-calling

      - name: Run integration tests
        run: |
          npm run test:integration
```

---

## Summary

This tech stack guide covers:

1. ✅ **Industry Solutions**: Zoom, Google Meet, Jitsi, Teams, Discord
2. ✅ **WebRTC Libraries**: mediasoup, Janus, LiveKit, Pion
3. ✅ **Signaling**: Socket.io, uWebSockets.js, Centrifugo
4. ✅ **TURN/STUN**: Coturn, commercial services
5. ✅ **Media Processing**: FFmpeg, GStreamer
6. ✅ **Infrastructure**: Kubernetes, Terraform, Docker
7. ✅ **Monitoring**: Prometheus, Grafana, OpenTelemetry
8. ✅ **Cloud**: AWS, Azure, GCP services
9. ✅ **CI/CD**: GitHub Actions, automated deployments

### Recommended Stack for Production

```
Frontend: React + WebRTC APIs
Backend: Node.js + mediasoup (SFU)
Signaling: uWebSockets.js
TURN: Coturn (self-hosted) + Twilio (backup)
Database: PostgreSQL (RDS) + Redis (ElastiCache)
Storage: S3 + CloudFront
Orchestration: Kubernetes (EKS)
Monitoring: Prometheus + Grafana
Logging: ELK Stack
Tracing: Jaeger (OpenTelemetry)
```

**Estimated Cost** (10M concurrent users):
- Compute: $150K/month (EKS nodes)
- TURN bandwidth: $50K/month
- Storage (90-day retention): $15K/month
- Database: $10K/month
- **Total: ~$225K/month**

---

**Next Steps**: Check out [video-calling.md](./video-calling.md) for the complete system design guide, or [architecture.html](./architecture.html) for interactive architecture diagrams!
