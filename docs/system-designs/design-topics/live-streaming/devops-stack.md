# DevOps Stack & Tech Solutions for Live Streaming Platforms

Comprehensive guide to the DevOps tools, infrastructure, and tech stacks used by Twitch, YouTube Live, and Facebook Gaming.

---

## Table of Contents
1. [Cloud Infrastructure](#cloud-infrastructure)
2. [Ingest Infrastructure](#ingest-infrastructure)
3. [Transcoding Infrastructure](#transcoding-infrastructure)
4. [CDN & Edge Delivery](#cdn--edge-delivery)
5. [Chat Infrastructure](#chat-infrastructure)
6. [Container Orchestration](#container-orchestration)
7. [CI/CD Pipelines](#cicd-pipelines)
8. [Monitoring & Observability](#monitoring--observability)
9. [Databases](#databases)
10. [Real Industry Examples](#real-industry-examples)

---

## Cloud Infrastructure

### AWS Stack

```yaml
Compute:
  Ingest Servers:
    - EC2 c5n.4xlarge (16 vCPU, 42 GB, 25 Gbps network)
    - Purpose: RTMP ingest, stream validation
    - Distribution: 100+ locations globally
  
  Transcoding Workers:
    - EC2 g4dn.xlarge (4 vCPU, 16 GB, 1× NVIDIA T4 GPU)
    - Auto-scaling: Based on concurrent stream count
    - Spot instances: 70% cost savings
  
  API Servers:
    - ECS Fargate / EKS (Kubernetes)
    - c5.2xlarge (8 vCPU, 16 GB RAM)
    - Auto-scaling: Based on QPS

Storage:
  Live Segments:
    - S3 Standard (temporary, TTL: 2 hours)
  
  VOD Archives:
    - S3 Intelligent-Tiering
    - Lifecycle: Standard (7 days) → IA (60 days)
  
  Chat Logs:
    - DynamoDB (time-series, TTL: 30 days)

Networking:
  CloudFront:
    - CDN for video delivery
    - 450+ edge locations
    - Origin shield for ingest protection
  
  Direct Connect:
    - Dedicated connections to major ISPs
    - Reduces latency, improves reliability

Real-time Streaming:
  Kinesis Data Streams:
    - Analytics events pipeline
    - Capacity: 1 million records/second
  
  AWS IVS (Interactive Video Service):
    - Managed live streaming (alternative to custom)
    - Built-in low-latency delivery
```

### GCP Stack (YouTube Live)

```yaml
Compute:
  GCE Instances:
    - n2-highmem series for transcoding
    - Preemptible VMs for cost savings
  
  GKE (Kubernetes):
    - Microservices orchestration
    - Autopilot mode for managed scaling

Storage:
  Cloud Storage:
    - Multi-regional for live segments
    - Nearline for archived VODs
  
  Bigtable:
    - Chat messages, stream metadata
    - Low-latency, high-throughput

Networking:
  Cloud CDN:
    - Integrated with Cloud Storage
    - Global Anycast IP
  
  Google Global Cache:
    - Free cache nodes to ISPs
    - 90%+ cache hit ratio

Media Services:
  Live Stream API:
    - Managed live streaming
    - Low-latency: 5-10 seconds
    - Ultra-low latency: <2 seconds
```

---

## Ingest Infrastructure

### Nginx-RTMP Setup

**Installation:**
```bash
# Build Nginx with RTMP module
git clone https://github.com/nginx/nginx.git
git clone https://github.com/arut/nginx-rtmp-module.git

cd nginx
./configure --add-module=../nginx-rtmp-module
make -j$(nproc)
sudo make install
```

**Configuration:**
```nginx
worker_processes auto;
rtmp_auto_push on;

events {
    worker_connections 4096;
}

rtmp {
    server {
        listen 1935;
        chunk_size 4096;
        
        application live {
            live on;
            record off;
            
            # Allow publish from authenticated users only
            allow publish 127.0.0.1;
            deny publish all;
            allow play all;
            
            # Callback for authentication
            on_publish http://auth.example.com/rtmp/auth;
            on_publish_done http://auth.example.com/rtmp/done;
            
            # Push to transcoding
            push rtmp://transcode.example.com/live;
            
            # Health check
            exec_publish curl -X POST http://health.example.com/stream/start/$name;
            exec_publish_done curl -X POST http://health.example.com/stream/end/$name;
            
            # Bandwidth limit per stream
            max_connections 1;
        }
        
        application hls {
            live on;
            hls on;
            hls_path /tmp/hls;
            hls_fragment 2s;  # Low-latency
            hls_playlist_length 10s;
            
            # Variants
            hls_variant _1080p60 BANDWIDTH=6000000,RESOLUTION=1920x1080;
            hls_variant _720p60 BANDWIDTH=4500000,RESOLUTION=1280x720;
            hls_variant _720p BANDWIDTH=3000000,RESOLUTION=1280x720;
            hls_variant _480p BANDWIDTH=1500000,RESOLUTION=852x480;
        }
    }
}

http {
    server {
        listen 8080;
        
        location /hls {
            types {
                application/vnd.apple.mpegurl m3u8;
                video/mp2t ts;
            }
            root /tmp;
            add_header Cache-Control no-cache;
            add_header Access-Control-Allow-Origin *;
        }
        
        location /stats {
            rtmp_stat all;
            rtmp_stat_stylesheet stat.xsl;
        }
    }
}
```

### Wowza Streaming Engine

```yaml
Deployment:
  Instance: EC2 c5.4xlarge (16 vCPU, 32 GB RAM)
  Concurrent Streams: 500-1000 per instance
  
Protocols Supported:
  Ingest:
    - RTMP/RTMPS
    - SRT (low-latency, reliable)
    - WebRTC
    - RTSP
  
  Delivery:
    - HLS
    - DASH
    - WebRTC
    - CMAF (Low-latency HLS/DASH)

Configuration:
  Transcoding: Yes (live adaptive bitrate)
  Recording: Yes (DVR, VOD)
  DRM: Widevine, FairPlay, PlayReady
  
Pricing:
  License: $65/month per instance (perpetual: $995)
  AWS Marketplace: $0.30/hour + EC2 costs
```

---

## Transcoding Infrastructure

### FFmpeg GPU Cluster

**Docker Image:**
```dockerfile
FROM nvidia/cuda:11.8.0-runtime-ubuntu22.04

# Install FFmpeg with NVENC support
RUN apt-get update && apt-get install -y \
    ffmpeg \
    nvidia-cuda-toolkit \
    && rm -rf /var/lib/apt/lists/*

# Transcoder script
COPY transcode.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/transcode.sh

ENTRYPOINT ["/usr/local/bin/transcode.sh"]
```

**Transcode Script:**
```bash
#!/bin/bash
# transcode.sh - GPU-accelerated transcoding

STREAM_ID=$1
INPUT_URL=$2
OUTPUT_BASE=$3

# 1080p60
ffmpeg -hwaccel cuda -i "$INPUT_URL" \
  -c:v h264_nvenc -preset p4 -tune ll -b:v 6000k -maxrate 6000k -bufsize 12000k \
  -s 1920x1080 -r 60 -g 120 \
  -c:a aac -b:a 160k \
  -f flv "${OUTPUT_BASE}/1080p60.flv" &

# 720p60
ffmpeg -hwaccel cuda -i "$INPUT_URL" \
  -c:v h264_nvenc -preset p4 -tune ll -b:v 4500k -maxrate 4500k -bufsize 9000k \
  -s 1280x720 -r 60 -g 120 \
  -c:a aac -b:a 128k \
  -f flv "${OUTPUT_BASE}/720p60.flv" &

# 480p
ffmpeg -hwaccel cuda -i "$INPUT_URL" \
  -c:v h264_nvenc -preset p4 -tune ll -b:v 1500k -maxrate 1500k -bufsize 3000k \
  -s 852x480 -r 30 -g 60 \
  -c:a aac -b:a 96k \
  -f flv "${OUTPUT_BASE}/480p.flv" &

wait
```

**Kubernetes Deployment:**
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: transcoder
spec:
  replicas: 100
  selector:
    matchLabels:
      app: transcoder
  template:
    metadata:
      labels:
        app: transcoder
    spec:
      nodeSelector:
        gpu: "true"
      containers:
      - name: transcoder
        image: live-platform/transcoder:latest
        resources:
          limits:
            nvidia.com/gpu: 1
            memory: "16Gi"
            cpu: "4000m"
          requests:
            memory: "8Gi"
            cpu: "2000m"
        env:
        - name: STREAM_QUEUE
          value: "sqs://transcoding-queue"

---
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: transcoder-hpa
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: transcoder
  minReplicas: 50
  maxReplicas: 5000
  metrics:
  - type: External
    external:
      metric:
        name: sqs_queue_depth
      target:
        type: AverageValue
        averageValue: "5"
```

---

## Chat Infrastructure

### Elixir/Phoenix WebSocket Server

**Installation:**
```bash
# Create new Phoenix project
mix phx.new chat_server --no-ecto --no-html

cd chat_server
```

**WebSocket Endpoint:**
```elixir
# lib/chat_server_web/channels/user_socket.ex
defmodule ChatServerWeb.UserSocket do
  use Phoenix.Socket

  channel "chat:*", ChatServerWeb.ChatChannel

  def connect(%{"token" => token}, socket, _connect_info) do
    case Phoenix.Token.verify(socket, "user auth", token, max_age: 86400) do
      {:ok, user_id} ->
        socket = assign(socket, :user_id, user_id)
        {:ok, socket}
      {:error, _reason} ->
        :error
    end
  end

  def id(socket), do: "user_socket:#{socket.assigns.user_id}"
end

# lib/chat_server_web/channels/chat_channel.ex
defmodule ChatServerWeb.ChatChannel do
  use Phoenix.Channel
  require Logger

  def join("chat:" <> channel_id, _params, socket) do
    send(self(), :after_join)
    {:ok, assign(socket, :channel_id, channel_id)}
  end

  def handle_info(:after_join, socket) do
    # Increment viewer count
    ChatServer.ViewerCounter.increment(socket.assigns.channel_id)
    
    # Send recent messages
    messages = ChatServer.MessageStore.get_recent(socket.assigns.channel_id, 50)
    push(socket, "messages", %{messages: messages})
    
    {:noreply, socket}
  end

  def handle_in("message", %{"text" => text}, socket) do
    user_id = socket.assigns.user_id
    channel_id = socket.assigns.channel_id
    
    # Rate limiting
    case ChatServer.RateLimiter.check(user_id) do
      :ok ->
        # AutoMod check
        case ChatServer.AutoMod.check(text) do
          :ok ->
            message = %{
              id: UUID.uuid4(),
              user_id: user_id,
              username: ChatServer.Users.get_username(user_id),
              text: text,
              timestamp: DateTime.utc_now()
            }
            
            # Broadcast to all in channel
            broadcast!(socket, "message", message)
            
            # Store for history
            ChatServer.MessageStore.save(channel_id, message)
            
            {:noreply, socket}
          
          {:block, reason} ->
            {:reply, {:error, %{reason: reason}}, socket}
        end
      
      {:error, :rate_limited} ->
        {:reply, {:error, %{reason: "You are sending messages too quickly"}}, socket}
    end
  end

  def terminate(_reason, socket) do
    ChatServer.ViewerCounter.decrement(socket.assigns.channel_id)
    :ok
  end
end
```

**Clustering:**
```elixir
# config/prod.exs
config :chat_server, ChatServerWeb.Endpoint,
  http: [port: {:system, "PORT"}],
  url: [host: {:system, "HOST"}, port: 443],
  server: true

# Phoenix PubSub with Redis
config :chat_server, ChatServer.PubSub,
  adapter: Phoenix.PubSub.Redis,
  host: "redis-cluster.example.com",
  port: 6379,
  node_name: System.get_env("NODE_NAME")

# Libcluster for automatic clustering
config :libcluster,
  topologies: [
    k8s: [
      strategy: Elixir.Cluster.Strategy.Kubernetes.DNS,
      config: [
        service: "chat-server",
        application_name: "chat_server",
        kubernetes_namespace: "production"
      ]
    ]
  ]
```

---

## Monitoring & Observability

### Prometheus + Grafana

**Metrics Collection:**
```python
from prometheus_client import Counter, Histogram, Gauge, start_http_server

# Stream metrics
stream_starts = Counter('stream_starts_total', 'Total stream starts')
stream_ends = Counter('stream_ends_total', 'Total stream ends')
concurrent_streams = Gauge('concurrent_streams', 'Current concurrent streams')
stream_bitrate = Histogram('stream_bitrate_kbps', 'Stream bitrate in Kbps',
                           buckets=[1000, 2000, 3000, 4500, 6000, 8000])

# Viewer metrics
concurrent_viewers = Gauge('concurrent_viewers', 'Current concurrent viewers')
viewer_joins = Counter('viewer_joins_total', 'Total viewer joins')
buffering_events = Counter('buffering_events_total', 'Buffering events')

# Chat metrics
chat_messages = Counter('chat_messages_total', 'Total chat messages')
chat_latency = Histogram('chat_latency_seconds', 'Chat message latency')

# Transcoding metrics
transcode_queue_depth = Gauge('transcode_queue_depth', 'Transcoding queue size')
transcode_duration = Histogram('transcode_duration_seconds', 'Time to transcode')

# Track events
def on_stream_start(stream_id, bitrate):
    stream_starts.inc()
    concurrent_streams.inc()
    stream_bitrate.observe(bitrate)

def on_viewer_join():
    viewer_joins.inc()
    concurrent_viewers.inc()

def on_chat_message(latency_ms):
    chat_messages.inc()
    chat_latency.observe(latency_ms / 1000)

# Start metrics server
start_http_server(9090)
```

**Grafana Dashboard:**
```json
{
  "dashboard": {
    "title": "Live Streaming Platform Overview",
    "panels": [
      {
        "title": "Concurrent Streams",
        "targets": [{
          "expr": "concurrent_streams"
        }],
        "type": "graph"
      },
      {
        "title": "Concurrent Viewers",
        "targets": [{
          "expr": "concurrent_viewers"
        }],
        "type": "graph"
      },
      {
        "title": "Total Bandwidth (Tbps)",
        "targets": [{
          "expr": "sum(rate(cdn_bytes_sent[1m])) * 8 / 1e12"
        }],
        "type": "graph"
      },
      {
        "title": "Chat Messages/sec",
        "targets": [{
          "expr": "rate(chat_messages_total[1m])"
        }],
        "type": "graph",
        "alert": {
          "conditions": [{
            "evaluator": {
              "params": [500000],
              "type": "gt"
            }
          }],
          "message": "Chat system overload"
        }
      },
      {
        "title": "Transcoding Queue",
        "targets": [{
          "expr": "transcode_queue_depth"
        }],
        "type": "graph"
      }
    ]
  }
}
```

---

## Real Industry Examples

### Twitch (Amazon) Tech Stack

```yaml
Programming Languages:
  - Go: Backend services, ingest servers
  - Elixir: Chat system (Phoenix framework)
  - JavaScript/TypeScript: Frontend
  - Python: Data analytics, ML

Frontend:
  - React: Web player
  - React Native: Mobile apps
  - Video.js: HLS player

Backend:
  - Go microservices
  - API Gateway: Custom (written in Go)

Databases:
  - PostgreSQL: User data, channels (sharded)
  - DynamoDB: Stream state, chat logs
  - Redis: Caching, viewer counts
  - Elasticsearch: Search

Message Queue:
  - Amazon Kinesis: Analytics events
  - SQS: Task queues

Storage:
  - S3: VOD storage
  - CloudFront: CDN

Container Orchestration:
  - Amazon ECS (Elastic Container Service)
  - Docker containers

Chat:
  - Elixir/Phoenix
  - IRC protocol over WebSocket
  - Redis Pub/Sub for multi-server

Monitoring:
  - Prometheus: Metrics
  - Grafana: Dashboards
  - ELK Stack: Logs
  - AWS CloudWatch

Transcoding:
  - NVIDIA GPU instances (EC2 g4dn)
  - Custom FFmpeg builds
  - Auto-scaling based on stream count

CDN:
  - Amazon CloudFront (primary)
  - Custom edge servers
  - ISP-level caching

Real-time Analytics:
  - Kinesis → Lambda → DynamoDB
  - Real-time viewer counts
  - Stream health monitoring
```

### YouTube Live (Google) Tech Stack

```yaml
Programming Languages:
  - C++: Video processing, backend
  - Java: Services
  - Python: Infrastructure, ML
  - Go: Microservices

Frontend:
  - Polymer: Web components
  - Android/iOS native apps

Backend:
  - Bigtable: Metadata, chat
  - Spanner: Global SQL database
  - Colossus: Distributed file system

Video Processing:
  - Custom encoding infrastructure
  - VP9 codec (Google-developed)
  - AV1 adoption (next-gen)
  - TPUs for ML-optimized encoding

Container Orchestration:
  - Borg: Internal Kubernetes predecessor
  - Kubernetes: External projects

CDN:
  - Google Global Cache (GGC)
  - Free cache nodes to ISPs
  - Direct peering with ISPs

Monitoring:
  - Borgmon: Metrics (Prometheus ancestor)
  - Dapper: Distributed tracing

Live Streaming:
  - RTMP ingest
  - HLS/DASH delivery
  - Low-latency: 5-10 seconds
  - Ultra-low-latency: <3 seconds
```

### Facebook Gaming (Meta) Tech Stack

```yaml
Programming Languages:
  - Hack: Facebook's PHP dialect
  - C++: Infrastructure
  - Python: Tools, automation

Frontend:
  - React: Web player
  - React Native: Mobile

Backend:
  - MySQL: User data (sharded)
  - Cassandra: Time-series data
  - Memcached: Caching layer
  - TAO: Social graph database

Storage:
  - Haystack: Photo/video storage
  - f4: Warm storage (BLOB)

Container Orchestration:
  - Tupperware: Internal container system
  - Kubernetes adoption

CDN:
  - Meta's global network
  - Custom edge servers
  - ISP partnerships

Monitoring:
  - Scuba: Real-time analytics
  - ODS (Operational Data Store): Metrics
```

---

## Cost Optimization

```yaml
Transcoding:
  Strategy: Use EC2 Spot Instances
  Savings: 70% vs on-demand
  Monthly: $284K (spot) vs $947K (on-demand)

CDN:
  Own Infrastructure: 83% savings vs CloudFront
  Monthly: $1.5M (own) vs $8.7M (CloudFront)
  
Storage:
  S3 Intelligent-Tiering: 37% savings
  VODs: $1.9M (tiered) vs $3.0M (all Standard)

Chat:
  Elixir/Phoenix: Handles 5M concurrent connections
  Cost: $9K/month (18 servers) vs $100K+ (managed)

Total Monthly Savings:
  - Spot instances: $663K
  - Own CDN: $7.2M
  - Storage tiering: $1.1M
  - Total: ~$9M/month saved
```

---

**This comprehensive DevOps guide covers the complete infrastructure and tech stacks used by Twitch, YouTube Live, and Facebook Gaming.**
