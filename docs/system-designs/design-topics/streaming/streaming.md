# Video Streaming System Design (Netflix/YouTube)

A comprehensive guide to designing large-scale video streaming platforms like Netflix, YouTube, and similar services.

---

## Table of Contents
1. [Problem Statement](#problem-statement)
2. [Requirements](#requirements)
3. [High-Level Architecture](#high-level-architecture)
4. [Core Components](#core-components)
5. [Video Upload Pipeline](#video-upload-pipeline)
6. [Video Transcoding](#video-transcoding)
7. [Adaptive Bitrate Streaming](#adaptive-bitrate-streaming)
8. [CDN Strategy](#cdn-strategy)
9. [Database Design](#database-design)
10. [API Design](#api-design)
11. [Recommendation System](#recommendation-system)
12. [Analytics & Monitoring](#analytics--monitoring)
13. [Security & DRM](#security--drm)
14. [Advanced Topics](#advanced-topics)

---

## Problem Statement

Design a video streaming platform that can:
- Support 300+ million users globally
- Stream billions of hours of video monthly
- Handle 10+ million concurrent viewers during peak hours
- Provide smooth playback with minimal buffering
- Adapt to varying network conditions
- Scale cost-effectively

**Examples:** Netflix, YouTube, Hulu, Disney+, Amazon Prime Video

---

## Requirements

### Functional Requirements

1. **Video Upload**
   - Users can upload videos up to 10 GB
   - Support multiple formats (MP4, AVI, MOV, MKV)
   - Resume upload on failure

2. **Video Processing**
   - Transcode to multiple resolutions (4K, 1080p, 720p, 480p, 360p, 240p)
   - Generate thumbnails and preview sprites
   - Extract metadata (duration, resolution, codec)

3. **Video Playback**
   - Adaptive bitrate streaming (HLS/DASH)
   - Support for web, mobile, smart TV, game consoles
   - Resume playback across devices
   - Offline download (mobile apps)

4. **Content Discovery**
   - Search videos by title, description, tags
   - Personalized recommendations
   - Browse by categories/genres
   - Trending videos

5. **User Management**
   - User authentication & profiles
   - Watch history
   - Watchlist/favorites
   - Multiple user profiles (family accounts)

6. **Live Streaming** (bonus)
   - Real-time video broadcast
   - Low-latency delivery (<5 seconds)

### Non-Functional Requirements

1. **Scalability**
   - Handle 10M concurrent viewers
   - Store petabytes of video content
   - Process 500,000+ video uploads daily (YouTube scale)

2. **Performance**
   - Video startup time: <1 second
   - Buffering ratio: <0.1% (less than 0.1% of playback time spent buffering)
   - CDN cache hit ratio: >95%

3. **Availability**
   - 99.99% uptime (52 minutes downtime/year)
   - No single point of failure
   - Graceful degradation

4. **Cost Efficiency**
   - Optimize CDN costs (largest expense)
   - Efficient storage tiering (hot/warm/cold)
   - Spot instances for transcoding

5. **Quality of Experience (QoE)**
   - Minimal buffering
   - Fast quality adaptation
   - Consistent playback

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                           │
│  Web Player  │  iOS App  │  Android App  │  Smart TV  │  Roku  │
└────────────┬────────────────────────────────────────────┬────────┘
             │                                            │
             ▼                                            ▼
┌─────────────────────────┐                  ┌──────────────────────┐
│     API GATEWAY         │                  │      CDN LAYER       │
│  (Zuul/Kong/AWS ALB)    │                  │  (CloudFront/OC)     │
└───────────┬─────────────┘                  └──────────┬───────────┘
            │                                            │
            ▼                                            ▼
┌─────────────────────────────────────────┐   ┌──────────────────────┐
│      MICROSERVICES LAYER                │   │   ORIGIN STORAGE     │
│ ┌──────────┐  ┌──────────┐             │   │   (S3/GCS/Azure)     │
│ │  User    │  │  Video   │  ┌────────┐ │   └──────────────────────┘
│ │ Service  │  │ Metadata │  │ Upload │ │
│ └──────────┘  └──────────┘  │ Service│ │
│                              └────────┘ │
│ ┌──────────┐  ┌──────────┐             │
│ │ Playback │  │  Search  │  ┌────────┐ │
│ │ Service  │  │ Service  │  │  Rec   │ │
│ └──────────┘  └──────────┘  │ Engine │ │
│                              └────────┘ │
└─────────────┬───────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────┐
│         MESSAGE QUEUE                   │
│  Kafka Topics: video.uploaded,          │
│  video.encoded, play.events             │
└───────────┬─────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────┐
│     TRANSCODING WORKERS                 │
│  (EC2/GCE Auto-scaling Group)           │
│  FFmpeg, MediaConvert, Custom Encoders  │
└─────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────┐
│         DATA LAYER                      │
│ PostgreSQL │ Cassandra │ Redis │ ES     │
│  (Users)   │ (Metadata)│(Cache)│(Search)│
└─────────────────────────────────────────┘
```

---

## Core Components

### 1. API Gateway

**Purpose:** Entry point for all client requests. Handles routing, authentication, rate limiting.

**Technologies:**
- Netflix Zuul
- Kong
- AWS API Gateway
- Nginx + Lua

**Responsibilities:**
- JWT token validation
- Request routing to microservices
- Rate limiting (per user/IP)
- Request/response transformation
- Circuit breaking (Hystrix pattern)

**Example Configuration:**
```yaml
routes:
  - path: /api/v1/videos/*
    service: video-service
    rate_limit: 1000 req/min
    auth: required
  
  - path: /api/v1/playback/*
    service: playback-service
    rate_limit: 10000 req/min
    auth: required
```

### 2. Video Upload Service

**Purpose:** Handle resumable video uploads from clients to object storage.

**Flow:**
1. Client requests upload URL
2. Service generates presigned S3 URL (valid 1 hour)
3. Client uploads directly to S3 (multipart upload)
4. S3 triggers Lambda/webhook on completion
5. Service validates file and publishes to Kafka

**API:**
```python
POST /api/v1/videos/initiate-upload
Request:
{
  "filename": "my-video.mp4",
  "size": 524288000,
  "content_type": "video/mp4",
  "title": "My Awesome Video",
  "description": "Check this out!",
  "tags": ["tutorial", "coding"]
}

Response:
{
  "video_id": "550e8400-e29b-41d4-a716-446655440000",
  "upload_url": "https://s3.amazonaws.com/videos/...",
  "chunk_size": 5242880  // 5 MB
}
```

**Implementation:**
```python
import boto3
import uuid

def initiate_upload(request):
    video_id = str(uuid.uuid4())
    s3_client = boto3.client('s3')
    
    # Generate presigned POST URL
    presigned_post = s3_client.generate_presigned_post(
        Bucket='video-uploads',
        Key=f'raw/{video_id}/{request.filename}',
        ExpiresIn=3600,  # 1 hour
        Fields={'acl': 'private'},
        Conditions=[
            ['content-length-range', 1, request.size]
        ]
    )
    
    # Save metadata to database
    video = Video(
        id=video_id,
        uploader_id=request.user_id,
        title=request.title,
        description=request.description,
        status='uploading'
    )
    db.session.add(video)
    db.session.commit()
    
    return {
        'video_id': video_id,
        'upload_url': presigned_post['url'],
        'fields': presigned_post['fields'],
        'chunk_size': 5 * 1024 * 1024
    }

# S3 Upload Complete Webhook
def on_upload_complete(event):
    video_id = extract_video_id(event['key'])
    
    # Validate video
    validation_result = validate_video(event['key'])
    if not validation_result.valid:
        update_video_status(video_id, 'failed', validation_result.error)
        return
    
    # Extract metadata
    metadata = extract_metadata(event['key'])
    update_video_metadata(video_id, metadata)
    
    # Publish to Kafka for transcoding
    kafka_producer.send('video.uploaded', {
        'video_id': video_id,
        's3_key': event['key'],
        'duration': metadata.duration,
        'resolution': metadata.resolution
    })
    
    update_video_status(video_id, 'processing')
```

### 3. Transcoding Service

**Purpose:** Convert uploaded videos into multiple resolutions and formats for adaptive streaming.

**Architecture:**
- **Job Queue:** Kafka topic `video.uploaded`
- **Workers:** Auto-scaling group of EC2 instances running FFmpeg
- **Priority Queue:** Premium users get higher priority

**Encoding Ladder:**
```python
ENCODING_PROFILES = [
    {
        'name': '4k',
        'resolution': '3840x2160',
        'video_bitrate': '12000k',
        'audio_bitrate': '192k',
        'codec': 'libx265',  # H.265 for 4K
        'preset': 'medium'
    },
    {
        'name': '1080p',
        'resolution': '1920x1080',
        'video_bitrate': '5000k',
        'audio_bitrate': '128k',
        'codec': 'libx264',
        'preset': 'medium'
    },
    {
        'name': '720p',
        'resolution': '1280x720',
        'video_bitrate': '2500k',
        'audio_bitrate': '128k',
        'codec': 'libx264',
        'preset': 'fast'
    },
    {
        'name': '480p',
        'resolution': '854x480',
        'video_bitrate': '1000k',
        'audio_bitrate': '96k',
        'codec': 'libx264',
        'preset': 'fast'
    },
    {
        'name': '360p',
        'resolution': '640x360',
        'video_bitrate': '700k',
        'audio_bitrate': '96k',
        'codec': 'libx264',
        'preset': 'veryfast'
    },
    {
        'name': '240p',
        'resolution': '426x240',
        'video_bitrate': '400k',
        'audio_bitrate': '64k',
        'codec': 'libx264',
        'preset': 'veryfast'
    }
]
```

**Transcoding Worker:**
```python
import subprocess
import json
from kafka import KafkaConsumer

consumer = KafkaConsumer('video.uploaded', group_id='transcoding-workers')

for message in consumer:
    job = json.loads(message.value)
    video_id = job['video_id']
    input_file = download_from_s3(job['s3_key'])
    
    # Transcode all profiles in parallel
    transcode_jobs = []
    for profile in ENCODING_PROFILES:
        job_process = transcode_async(input_file, profile, video_id)
        transcode_jobs.append(job_process)
    
    # Wait for all to complete
    for job_process in transcode_jobs:
        job_process.wait()
    
    # Generate HLS manifest
    generate_hls_manifest(video_id, ENCODING_PROFILES)
    
    # Upload to S3
    upload_to_s3(video_id, f's3://videos/{video_id}/')
    
    # Update database
    update_video_status(video_id, 'ready')
    
    # Publish completion event
    kafka_producer.send('video.encoded', {'video_id': video_id})

def transcode_async(input_file, profile, video_id):
    output_dir = f'/tmp/{video_id}/{profile["name"]}'
    os.makedirs(output_dir, exist_ok=True)
    
    # FFmpeg command for HLS output
    cmd = [
        'ffmpeg',
        '-i', input_file,
        '-vf', f'scale={profile["resolution"]}',
        '-c:v', profile['codec'],
        '-b:v', profile['video_bitrate'],
        '-maxrate', str(int(profile['video_bitrate'][:-1]) * 1.07) + 'k',
        '-bufsize', str(int(profile['video_bitrate'][:-1]) * 1.5) + 'k',
        '-c:a', 'aac',
        '-b:a', profile['audio_bitrate'],
        '-preset', profile['preset'],
        '-hls_time', '6',  # 6-second segments
        '-hls_playlist_type', 'vod',
        '-hls_segment_filename', f'{output_dir}/segment_%03d.ts',
        f'{output_dir}/playlist.m3u8'
    ]
    
    return subprocess.Popen(cmd)

def generate_hls_manifest(video_id, profiles):
    """Generate master playlist with all quality variants"""
    master_playlist = "#EXTM3U\n#EXT-X-VERSION:3\n\n"
    
    for profile in profiles:
        bitrate = int(profile['video_bitrate'][:-1]) * 1000  # Convert to bps
        resolution = profile['resolution'].replace('x', 'x')
        
        master_playlist += f'#EXT-X-STREAM-INF:BANDWIDTH={bitrate},RESOLUTION={resolution}\n'
        master_playlist += f'{profile["name"]}/playlist.m3u8\n\n'
    
    with open(f'/tmp/{video_id}/master.m3u8', 'w') as f:
        f.write(master_playlist)
```

### 4. Playback Service

**Purpose:** Generate playback manifests and signed URLs for video streaming.

**API:**
```python
GET /api/v1/videos/{video_id}/playback
Headers:
  Authorization: Bearer <jwt_token>

Response:
{
  "video_id": "550e8400-e29b-41d4-a716-446655440000",
  "manifest_url": "https://cdn.example.com/videos/550e.../master.m3u8?sig=...",
  "available_qualities": ["4k", "1080p", "720p", "480p", "360p", "240p"],
  "drm_license_url": "https://license.example.com/widevine",
  "thumbnail_url": "https://cdn.example.com/thumbnails/550e.../thumb.jpg",
  "sprite_url": "https://cdn.example.com/sprites/550e.../sprite.jpg",
  "analytics_endpoint": "https://analytics.example.com/events"
}
```

**Implementation:**
```python
from flask import Flask, request, jsonify
import jwt
from datetime import datetime, timedelta

app = Flask(__name__)

@app.route('/api/v1/videos/<video_id>/playback')
def get_playback_info(video_id):
    # Authenticate user
    token = request.headers.get('Authorization').replace('Bearer ', '')
    user = verify_jwt(token)
    
    # Check authorization
    video = Video.query.get(video_id)
    if not can_access_video(user, video):
        return jsonify({'error': 'Unauthorized'}), 403
    
    # Check subscription for quality
    max_quality = get_max_quality_for_user(user)
    
    # Generate signed CDN URLs (valid 4 hours)
    manifest_url = generate_signed_url(
        f'https://cdn.example.com/videos/{video_id}/master.m3u8',
        expires_in=14400
    )
    
    # DRM license URL (if premium content)
    drm_url = None
    if video.is_premium:
        drm_url = generate_drm_license_url(video_id, user.id)
    
    # Track playback initiation for analytics
    track_event('playback_initiated', {
        'user_id': user.id,
        'video_id': video_id,
        'device': request.user_agent.platform,
        'timestamp': datetime.utcnow().isoformat()
    })
    
    return jsonify({
        'video_id': video_id,
        'manifest_url': manifest_url,
        'available_qualities': filter_qualities_by_subscription(max_quality),
        'drm_license_url': drm_url,
        'thumbnail_url': f'https://cdn.example.com/thumbnails/{video_id}/thumb.jpg',
        'sprite_url': f'https://cdn.example.com/sprites/{video_id}/sprite.jpg',
        'analytics_endpoint': 'https://analytics.example.com/events'
    })

def generate_signed_url(url, expires_in):
    """Generate CloudFront signed URL"""
    expiration = datetime.utcnow() + timedelta(seconds=expires_in)
    
    # CloudFront URL signature
    policy = {
        'Statement': [{
            'Resource': url,
            'Condition': {
                'DateLessThan': {'AWS:EpochTime': int(expiration.timestamp())}
            }
        }]
    }
    
    signature = sign_with_private_key(json.dumps(policy))
    
    return f'{url}?Expires={int(expiration.timestamp())}&Signature={signature}&Key-Pair-Id={CLOUDFRONT_KEY_PAIR_ID}'
```

---

## Adaptive Bitrate Streaming

### HLS (HTTP Live Streaming) - Apple

**Master Playlist (master.m3u8):**
```m3u8
#EXTM3U
#EXT-X-VERSION:3

#EXT-X-STREAM-INF:BANDWIDTH=12000000,RESOLUTION=3840x2160,CODECS="hvc1.1.6.L153.B0,mp4a.40.2"
4k/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=5000000,RESOLUTION=1920x1080,CODECS="avc1.64001f,mp4a.40.2"
1080p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,CODECS="avc1.64001f,mp4a.40.2"
720p/playlist.m3u8

#EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480,CODECS="avc1.64001e,mp4a.40.2"
480p/playlist.m3u8
```

**Variant Playlist (1080p/playlist.m3u8):**
```m3u8
#EXTM3U
#EXT-X-VERSION:3
#EXT-X-TARGETDURATION:6
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-PLAYLIST-TYPE:VOD

#EXTINF:6.0,
segment_000.ts
#EXTINF:6.0,
segment_001.ts
#EXTINF:6.0,
segment_002.ts
...
#EXT-X-ENDLIST
```

### DASH (Dynamic Adaptive Streaming over HTTP) - MPEG

**Manifest (manifest.mpd):**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<MPD xmlns="urn:mpeg:dash:schema:mpd:2011" type="static">
  <Period duration="PT1H30M">
    <AdaptationSet mimeType="video/mp4" codecs="avc1.64001f">
      
      <Representation id="4k" bandwidth="12000000" width="3840" height="2160">
        <SegmentTemplate media="4k/$Number$.m4s" startNumber="1" duration="6"/>
      </Representation>
      
      <Representation id="1080p" bandwidth="5000000" width="1920" height="1080">
        <SegmentTemplate media="1080p/$Number$.m4s" startNumber="1" duration="6"/>
      </Representation>
      
      <Representation id="720p" bandwidth="2500000" width="1280" height="720">
        <SegmentTemplate media="720p/$Number$.m4s" startNumber="1" duration="6"/>
      </Representation>
      
    </AdaptationSet>
  </Period>
</MPD>
```

### ABR Algorithm

```python
class AdaptiveBitrateController:
    def __init__(self):
        self.bitrate_ladder = [400, 700, 1000, 2500, 5000, 12000]  # Kbps
        self.current_index = 0
        self.buffer_target = 20  # seconds
        self.bandwidth_history = []
        
    def select_next_quality(self, current_buffer_level, segment_download_stats):
        """
        Decide next quality based on buffer level and bandwidth estimate
        
        Args:
            current_buffer_level: Seconds of video buffered
            segment_download_stats: {size_bytes, download_time_ms}
        
        Returns:
            Selected bitrate in Kbps
        """
        # Estimate bandwidth from recent downloads
        bandwidth_kbps = self._estimate_bandwidth(segment_download_stats)
        self.bandwidth_history.append(bandwidth_kbps)
        
        # Use harmonic mean of last 5 measurements (conservative)
        if len(self.bandwidth_history) > 5:
            self.bandwidth_history = self.bandwidth_history[-5:]
        
        avg_bandwidth = self._harmonic_mean(self.bandwidth_history)
        
        # Buffer-based logic
        if current_buffer_level < 10:
            # Low buffer: emergency, drop quality
            self.current_index = max(0, self.current_index - 2)
        
        elif current_buffer_level < 15:
            # Below target: be conservative
            self.current_index = max(0, self.current_index - 1)
        
        elif current_buffer_level > 30:
            # High buffer: safe to upgrade
            # Select highest quality that bandwidth supports (with 85% safety margin)
            safe_bandwidth = avg_bandwidth * 0.85
            
            for i in range(len(self.bitrate_ladder) - 1, -1, -1):
                if self.bitrate_ladder[i] <= safe_bandwidth:
                    self.current_index = i
                    break
        
        # Clamp to valid range
        self.current_index = max(0, min(self.current_index, len(self.bitrate_ladder) - 1))
        
        return self.bitrate_ladder[self.current_index]
    
    def _estimate_bandwidth(self, stats):
        """Calculate bandwidth from segment download"""
        size_kbits = (stats['size_bytes'] * 8) / 1000
        time_s = stats['download_time_ms'] / 1000
        return size_kbits / time_s if time_s > 0 else 0
    
    def _harmonic_mean(self, values):
        """Harmonic mean (more conservative than arithmetic mean)"""
        if not values:
            return 0
        return len(values) / sum(1/v if v > 0 else 0 for v in values)
```

**Client-side Integration (JavaScript):**
```javascript
class VideoPlayer {
    constructor(videoElement) {
        this.video = videoElement;
        this.abrController = new ABRController();
        this.currentQualityIndex = 0;
        this.segmentDownloadStats = [];
    }
    
    async loadManifest(manifestUrl) {
        const response = await fetch(manifestUrl);
        this.manifest = await response.text();
        this.qualities = this.parseManifest(this.manifest);
        
        // Start with lowest quality for fast startup
        this.currentQualityIndex = 0;
        this.loadSegment(0);
    }
    
    async loadSegment(segmentNumber) {
        const quality = this.qualities[this.currentQualityIndex];
        const segmentUrl = quality.getSegmentUrl(segmentNumber);
        
        const startTime = performance.now();
        const response = await fetch(segmentUrl);
        const blob = await response.blob();
        const downloadTime = performance.now() - startTime;
        
        // Track download stats for ABR
        this.segmentDownloadStats.push({
            size_bytes: blob.size,
            download_time_ms: downloadTime
        });
        
        // Append to video buffer
        await this.appendToBuffer(blob);
        
        // ABR decision for next segment
        const bufferLevel = this.video.buffered.end(0) - this.video.currentTime;
        const nextQuality = this.abrController.selectNextQuality(
            bufferLevel,
            this.segmentDownloadStats[this.segmentDownloadStats.length - 1]
        );
        
        // Switch quality if different
        const newQualityIndex = this.qualities.findIndex(q => q.bitrate === nextQuality);
        if (newQualityIndex !== this.currentQualityIndex) {
            console.log(`Quality switch: ${quality.name} → ${this.qualities[newQualityIndex].name}`);
            this.currentQualityIndex = newQualityIndex;
        }
        
        // Load next segment
        this.loadSegment(segmentNumber + 1);
    }
}
```

---

## CDN Strategy

### Netflix Open Connect

Netflix's custom CDN with 17,000+ servers in 1,000+ locations.

**Architecture:**
```
┌─────────────┐
│   Origin    │  (AWS S3)
│   Storage   │
└──────┬──────┘
       │
       │ (Overnight sync of popular content)
       │
       ▼
┌─────────────────────────────────────┐
│    Netflix Open Connect Boxes      │
│  (Deployed at ISP data centers)    │
│                                     │
│  ┌──────────┐  ┌──────────┐       │
│  │ Cache 1  │  │ Cache 2  │  ...  │
│  │ 100 TB   │  │ 100 TB   │       │
│  └──────────┘  └──────────┘       │
└─────────────────┬───────────────────┘
                  │
                  ▼
            ┌──────────┐
            │  Users   │
            │(ISP subs)│
            └──────────┘
```

**Key Features:**
1. **Overnight Prefetch:** Predict popular content, push to edge before demand
2. **95%+ Cache Hit:** Serve from edge, minimal origin traffic
3. **ISP Peering:** Free caching boxes to ISPs, reduces transit costs
4. **FreeBSD-based:** Custom OS optimized for high-throughput streaming

**Cost Savings:**
- Third-party CDN: $135M/month (at 13.5 EB/month)
- Own CDN: $6.4M/month (95% savings)

### YouTube Google Global Cache (GGC)

Google provides free caching nodes to ISPs worldwide.

**Features:**
- Free hardware to ISPs
- Direct peering with Google backbone
- 90%+ cache hit ratio
- Smart routing based on latency and load

### Multi-CDN Strategy

Use multiple CDNs for redundancy and performance.

```python
class CDNSelector:
    def __init__(self):
        self.cdns = {
            'primary': 'open-connect.netflix.com',
            'backup1': 'cloudfront.amazonaws.com',
            'backup2': 'fastly.net'
        }
        self.health_checks = {}
    
    def get_cdn_for_user(self, user_location, video_id):
        """Select best CDN based on location and health"""
        
        # Check CDN health (updated every 30s)
        if self.health_checks.get('primary', {}).get('healthy', True):
            return self._build_url(self.cdns['primary'], video_id)
        
        elif self.health_checks.get('backup1', {}).get('healthy', False):
            return self._build_url(self.cdns['backup1'], video_id)
        
        else:
            return self._build_url(self.cdns['backup2'], video_id)
    
    def _build_url(self, cdn_domain, video_id):
        return f'https://{cdn_domain}/videos/{video_id}/master.m3u8'
```

---

## Database Design

### User Database (PostgreSQL - Sharded)

```sql
CREATE TABLE users (
    user_id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    subscription_tier VARCHAR(50) DEFAULT 'free',  -- free, basic, premium
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_login TIMESTAMP,
    email_verified BOOLEAN DEFAULT FALSE,
    
    -- Shard by user_id % 64
    CONSTRAINT check_subscription CHECK (subscription_tier IN ('free', 'basic', 'premium'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_username ON users(username);

-- User profiles (for family accounts)
CREATE TABLE user_profiles (
    profile_id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(user_id),
    profile_name VARCHAR(100),
    avatar_url VARCHAR(500),
    is_kids_profile BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_profiles_user ON user_profiles(user_id);
```

**Sharding Strategy:**
```python
NUM_SHARDS = 64

def get_shard_for_user(user_id):
    return user_id % NUM_SHARDS

def get_db_connection(user_id):
    shard_id = get_shard_for_user(user_id)
    return db_connections[f'users_shard_{shard_id}']
```

### Video Metadata (Cassandra)

**Why Cassandra?**
- Horizontal scalability (add nodes easily)
- High write throughput (watch history)
- Tunable consistency
- Time-series data (watch events)

```cql
-- Videos table
CREATE TABLE videos (
    video_id UUID PRIMARY KEY,
    uploader_id BIGINT,
    title TEXT,
    description TEXT,
    duration INT,  -- seconds
    upload_date TIMESTAMP,
    view_count COUNTER,
    like_count INT,
    dislike_count INT,
    status TEXT,  -- uploading, processing, ready, failed
    visibility TEXT,  -- public, private, unlisted
    original_filename TEXT,
    tags SET<TEXT>,
    category TEXT
);

CREATE INDEX ON videos (uploader_id);
CREATE INDEX ON videos (status);
CREATE INDEX ON videos (upload_date);

-- Video variants (different resolutions)
CREATE TABLE video_variants (
    video_id UUID,
    quality TEXT,  -- 4k, 1080p, 720p, 480p, 360p, 240p
    codec TEXT,    -- h264, h265, vp9, av1
    bitrate INT,   -- Kbps
    manifest_url TEXT,
    storage_location TEXT,
    file_size BIGINT,
    PRIMARY KEY (video_id, quality, codec)
);

-- Watch history (time-series)
CREATE TABLE watch_history (
    user_id BIGINT,
    video_id UUID,
    watched_at TIMESTAMP,
    watch_duration INT,  -- seconds watched
    completion_rate FLOAT,  -- 0.0 to 1.0
    device_type TEXT,
    quality TEXT,
    PRIMARY KEY ((user_id), watched_at, video_id)
) WITH CLUSTERING ORDER BY (watched_at DESC);

-- Comments
CREATE TABLE comments (
    comment_id UUID,
    video_id UUID,
    user_id BIGINT,
    parent_comment_id UUID,  -- for replies
    content TEXT,
    created_at TIMESTAMP,
    like_count INT,
    PRIMARY KEY ((video_id), created_at, comment_id)
) WITH CLUSTERING ORDER BY (created_at DESC);
```

### Caching Layer (Redis)

```python
import redis
import json

redis_client = redis.Redis(host='redis-cluster', port=6379, decode_responses=True)

# Cache user session
def cache_user_session(user_id, session_data, ttl=3600):
    key = f'session:{user_id}'
    redis_client.setex(key, ttl, json.dumps(session_data))

# Cache video metadata (hot videos)
def cache_video_metadata(video_id, metadata, ttl=1800):
    key = f'video:{video_id}'
    redis_client.setex(key, ttl, json.dumps(metadata))

# Cache recommendations
def cache_recommendations(user_id, recommendations, ttl=3600):
    key = f'recs:{user_id}'
    redis_client.setex(key, ttl, json.dumps(recommendations))

# Cache trending videos
def cache_trending_videos(videos, ttl=300):
    redis_client.setex('trending:global', ttl, json.dumps(videos))

# Leaderboard (sorted set)
def update_view_count_leaderboard(video_id, view_count):
    redis_client.zadd('leaderboard:views:24h', {video_id: view_count})
    redis_client.expire('leaderboard:views:24h', 86400)

def get_top_videos(limit=10):
    return redis_client.zrevrange('leaderboard:views:24h', 0, limit-1, withscores=True)
```

### Search Index (Elasticsearch)

```python
from elasticsearch import Elasticsearch

es = Elasticsearch(['es-node1:9200', 'es-node2:9200'])

# Index video
def index_video(video):
    doc = {
        'title': video.title,
        'description': video.description,
        'tags': video.tags,
        'uploader_id': video.uploader_id,
        'upload_date': video.upload_date.isoformat(),
        'view_count': video.view_count,
        'duration': video.duration,
        'category': video.category
    }
    
    es.index(index='videos', id=video.video_id, document=doc)

# Search videos
def search_videos(query, filters={}, page=1, page_size=20):
    body = {
        'query': {
            'bool': {
                'must': {
                    'multi_match': {
                        'query': query,
                        'fields': ['title^3', 'description', 'tags^2'],  # Boost title and tags
                        'fuzziness': 'AUTO'
                    }
                },
                'filter': []
            }
        },
        'sort': [
            {'_score': {'order': 'desc'}},
            {'view_count': {'order': 'desc'}}
        ],
        'from': (page - 1) * page_size,
        'size': page_size
    }
    
    # Add filters
    if 'duration_min' in filters:
        body['query']['bool']['filter'].append({
            'range': {'duration': {'gte': filters['duration_min']}}
        })
    
    if 'upload_date_from' in filters:
        body['query']['bool']['filter'].append({
            'range': {'upload_date': {'gte': filters['upload_date_from']}}
        })
    
    result = es.search(index='videos', body=body)
    
    return {
        'total': result['hits']['total']['value'],
        'videos': [hit['_source'] for hit in result['hits']['hits']]
    }
```

---

## API Design

### RESTful APIs

```python
from flask import Flask, request, jsonify
from flask_limiter import Limiter

app = Flask(__name__)
limiter = Limiter(app, key_func=lambda: request.headers.get('X-User-ID'))

# Upload APIs
@app.route('/api/v1/videos/initiate-upload', methods=['POST'])
@limiter.limit("10 per hour")  # Limit uploads
def initiate_upload():
    """Initiate video upload, get presigned URL"""
    pass

@app.route('/api/v1/videos/<video_id>/upload-complete', methods=['POST'])
def upload_complete(video_id):
    """Notify upload completion"""
    pass

# Playback APIs
@app.route('/api/v1/videos/<video_id>/playback', methods=['GET'])
@limiter.limit("10000 per hour")
def get_playback_info(video_id):
    """Get manifest URL and playback info"""
    pass

@app.route('/api/v1/videos/<video_id>', methods=['GET'])
@limiter.limit("1000 per hour")
def get_video_metadata(video_id):
    """Get video metadata (title, description, etc.)"""
    pass

# Search & Discovery
@app.route('/api/v1/search', methods=['GET'])
@limiter.limit("100 per minute")
def search_videos():
    """Search videos by query"""
    query = request.args.get('q')
    page = int(request.args.get('page', 1))
    results = search_videos_in_es(query, page=page)
    return jsonify(results)

@app.route('/api/v1/videos/trending', methods=['GET'])
def get_trending_videos():
    """Get trending videos (cached)"""
    cached = redis_client.get('trending:global')
    if cached:
        return jsonify(json.loads(cached))
    
    trending = compute_trending_videos()
    cache_trending_videos(trending, ttl=300)  # 5 min
    return jsonify(trending)

@app.route('/api/v1/recommendations', methods=['GET'])
@limiter.limit("100 per hour")
def get_recommendations():
    """Get personalized recommendations"""
    user_id = get_current_user().id
    
    cached = redis_client.get(f'recs:{user_id}')
    if cached:
        return jsonify(json.loads(cached))
    
    recommendations = recommendation_engine.get_recommendations(user_id)
    cache_recommendations(user_id, recommendations, ttl=3600)
    return jsonify(recommendations)

# Analytics
@app.route('/api/v1/events', methods=['POST'])
@limiter.limit("10000 per hour")
def track_event():
    """Track playback events (play, pause, buffer, etc.)"""
    event = request.json
    kafka_producer.send('play.events', event)
    return jsonify({'status': 'ok'})

# User APIs
@app.route('/api/v1/users/me/history', methods=['GET'])
def get_watch_history():
    """Get user's watch history"""
    user_id = get_current_user().id
    history = cassandra_client.execute(
        "SELECT * FROM watch_history WHERE user_id = ? LIMIT 50",
        [user_id]
    )
    return jsonify([dict(row) for row in history])

@app.route('/api/v1/videos/<video_id>/like', methods=['POST'])
def like_video(video_id):
    """Like a video"""
    user_id = get_current_user().id
    # Update Cassandra
    cassandra_client.execute(
        "UPDATE videos SET like_count = like_count + 1 WHERE video_id = ?",
        [video_id]
    )
    return jsonify({'status': 'ok'})
```

---

## Recommendation System

### Architecture

```
┌──────────────────┐
│  User Activity   │  (Watch history, searches, ratings)
│  Data Collection │
└────────┬─────────┘
         │
         ▼
┌──────────────────┐
│  Feature Store   │  (User & video embeddings)
│  (Redis/Cassandra│
└────────┬─────────┘
         │
         ▼
┌──────────────────────────────────┐
│     ML Models (Offline)          │
│  ┌────────────┐  ┌────────────┐ │
│  │Collaborative│  │Content-    │ │
│  │Filtering   │  │Based       │ │
│  └────────────┘  └────────────┘ │
│  ┌────────────┐  ┌────────────┐ │
│  │Deep Neural │  │Trending    │ │
│  │Network     │  │Algorithm   │ │
│  └────────────┘  └────────────┘ │
└────────┬─────────────────────────┘
         │
         ▼
┌──────────────────┐
│ Recommendation   │  (Real-time inference)
│ Service (API)    │
└──────────────────┘
```

### Collaborative Filtering

```python
import numpy as np
from scipy.sparse.linalg import svds

class CollaborativeFilteringModel:
    def __init__(self, k=50):
        self.k = k  # Number of latent factors
        self.user_factors = None
        self.video_factors = None
    
    def fit(self, user_video_matrix):
        """
        Train using SVD (Singular Value Decomposition)
        
        Args:
            user_video_matrix: Sparse matrix (users × videos)
                              Value = watch duration or rating
        """
        # Perform SVD
        U, sigma, Vt = svds(user_video_matrix, k=self.k)
        
        self.user_factors = U  # User embeddings
        self.video_factors = Vt.T  # Video embeddings
        self.sigma = np.diag(sigma)
    
    def predict_rating(self, user_id, video_id):
        """Predict how much user would like video"""
        user_vector = self.user_factors[user_id]
        video_vector = self.video_factors[video_id]
        
        return np.dot(user_vector, np.dot(self.sigma, video_vector))
    
    def recommend_for_user(self, user_id, n=10, exclude_watched=True):
        """Get top N recommendations for user"""
        user_vector = self.user_factors[user_id]
        
        # Score all videos
        scores = np.dot(np.dot(user_vector, self.sigma), self.video_factors.T)
        
        # Get top N
        top_indices = np.argsort(scores)[::-1]
        
        if exclude_watched:
            watched_videos = get_watched_videos(user_id)
            top_indices = [i for i in top_indices if i not in watched_videos]
        
        return top_indices[:n]
```

### Content-Based Filtering

```python
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

class ContentBasedModel:
    def __init__(self):
        self.tfidf = TfidfVectorizer(max_features=5000, stop_words='english')
        self.video_features = None
    
    def fit(self, videos):
        """
        Build video feature vectors from metadata
        
        Args:
            videos: List of {video_id, title, description, tags, category}
        """
        # Combine text features
        corpus = []
        for video in videos:
            text = f"{video['title']} {video['description']} {' '.join(video['tags'])} {video['category']}"
            corpus.append(text)
        
        # TF-IDF vectorization
        self.video_features = self.tfidf.fit_transform(corpus)
        self.video_ids = [v['video_id'] for v in videos]
    
    def find_similar_videos(self, video_id, n=10):
        """Find videos similar to given video"""
        video_idx = self.video_ids.index(video_id)
        video_vector = self.video_features[video_idx]
        
        # Cosine similarity with all videos
        similarities = cosine_similarity(video_vector, self.video_features).flatten()
        
        # Get top N (excluding self)
        similar_indices = np.argsort(similarities)[::-1][1:n+1]
        
        return [self.video_ids[i] for i in similar_indices]
    
    def recommend_for_user(self, user_id, n=10):
        """Recommend based on user's watch history"""
        watched_videos = get_watched_videos(user_id, limit=20)
        
        # Find videos similar to what user watched
        recommendations = set()
        for video_id in watched_videos:
            similar = self.find_similar_videos(video_id, n=5)
            recommendations.update(similar)
        
        # Remove already watched
        recommendations -= set(watched_videos)
        
        return list(recommendations)[:n]
```

### Deep Learning Model (YouTube-style)

```python
import tensorflow as tf
from tensorflow import keras

class DeepRecommendationModel:
    def __init__(self, num_users, num_videos, embedding_dim=256):
        self.model = self._build_model(num_users, num_videos, embedding_dim)
    
    def _build_model(self, num_users, num_videos, embedding_dim):
        """Two-tower neural network"""
        
        # User tower
        user_input = keras.Input(shape=(1,), name='user_id')
        user_embedding = keras.layers.Embedding(num_users, embedding_dim)(user_input)
        user_flat = keras.layers.Flatten()(user_embedding)
        
        # Add user features
        user_age = keras.Input(shape=(1,), name='user_age')
        user_country = keras.Input(shape=(1,), name='user_country')
        user_concat = keras.layers.Concatenate()([user_flat, user_age, user_country])
        
        user_dense = keras.layers.Dense(512, activation='relu')(user_concat)
        user_dense = keras.layers.Dense(256, activation='relu')(user_dense)
        
        # Video tower
        video_input = keras.Input(shape=(1,), name='video_id')
        video_embedding = keras.layers.Embedding(num_videos, embedding_dim)(video_input)
        video_flat = keras.layers.Flatten()(video_embedding)
        
        # Add video features
        video_duration = keras.Input(shape=(1,), name='video_duration')
        video_category = keras.Input(shape=(1,), name='video_category')
        video_concat = keras.layers.Concatenate()([video_flat, video_duration, video_category])
        
        video_dense = keras.layers.Dense(512, activation='relu')(video_concat)
        video_dense = keras.layers.Dense(256, activation='relu')(video_dense)
        
        # Dot product for similarity
        dot_product = keras.layers.Dot(axes=1)([user_dense, video_dense])
        
        # Output: probability of engagement
        output = keras.layers.Dense(1, activation='sigmoid')(dot_product)
        
        model = keras.Model(
            inputs=[user_input, user_age, user_country, video_input, video_duration, video_category],
            outputs=output
        )
        
        model.compile(optimizer='adam', loss='binary_crossentropy', metrics=['accuracy'])
        
        return model
    
    def train(self, training_data, epochs=10):
        """Train on user-video interactions"""
        self.model.fit(training_data, epochs=epochs, batch_size=1024)
    
    def predict_engagement(self, user_features, video_features):
        """Predict if user will watch video"""
        return self.model.predict([user_features, video_features])
```

---

## Security & DRM

### DRM Systems

```python
class DRMService:
    def __init__(self):
        self.widevine_key_server = 'https://license.widevine.com'
        self.fairplay_key_server = 'https://license.fairplay.com'
    
    def get_license_url(self, video_id, user_id, device_type):
        """Generate DRM license URL based on device"""
        
        if device_type in ['android', 'chrome']:
            # Widevine for Android/Chrome
            return self._get_widevine_license(video_id, user_id)
        
        elif device_type in ['ios', 'safari', 'macos']:
            # FairPlay for Apple devices
            return self._get_fairplay_license(video_id, user_id)
        
        elif device_type in ['windows', 'xbox']:
            # PlayReady for Microsoft
            return self._get_playready_license(video_id, user_id)
        
        else:
            return None  # No DRM, serve unencrypted
    
    def _get_widevine_license(self, video_id, user_id):
        # Generate license acquisition token
        token = jwt.encode({
            'video_id': video_id,
            'user_id': user_id,
            'exp': datetime.utcnow() + timedelta(hours=4)
        }, WIDEVINE_SECRET_KEY)
        
        return f'{self.widevine_key_server}/license?token={token}'
    
    def _get_fairplay_license(self, video_id, user_id):
        # FairPlay Streaming (FPS) license
        token = jwt.encode({
            'video_id': video_id,
            'user_id': user_id,
            'exp': datetime.utcnow() + timedelta(hours=4)
        }, FAIRPLAY_SECRET_KEY)
        
        return f'{self.fairplay_key_server}/license?token={token}'
```

### Content Encryption

```bash
# Encrypt video segments with AES-128
# Generate encryption key
openssl rand 16 > enc.key

# Create key info file for HLS
echo "https://license.example.com/keys/video123" > enc.keyinfo
echo "enc.key" >> enc.keyinfo
echo $(xxd -p enc.key) >> enc.keyinfo

# FFmpeg with encryption
ffmpeg -i input.mp4 \
  -hls_key_info_file enc.keyinfo \
  -hls_time 6 \
  -hls_playlist_type vod \
  output.m3u8
```

---

**This comprehensive guide covers the core design of Netflix/YouTube-scale video streaming platforms. For scale estimates, see [scale-estimation-guide.md](scale-estimation-guide.md). For DevOps stack, see [devops-stack.md](devops-stack.md).**
