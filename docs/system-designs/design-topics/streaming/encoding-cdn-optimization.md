# Video Encoding & CDN Optimization Guide

Advanced techniques for optimizing video encoding quality and CDN performance for streaming platforms.

---

## Table of Contents
1. [Video Encoding Optimization](#video-encoding-optimization)
2. [Per-Title Encoding](#per-title-encoding)
3. [CDN Optimization Strategies](#cdn-optimization-strategies)
4. [Quality of Experience (QoE) Metrics](#quality-of-experience-qoe-metrics)
5. [Cost Optimization](#cost-optimization)

---

## Video Encoding Optimization

### Codec Selection

```yaml
H.264 (AVC):
  Pros:
    - Universal device support
    - Hardware acceleration everywhere
    - Mature, well-tested
  Cons:
    - Larger file sizes vs newer codecs
    - Higher bandwidth requirements
  Use Cases:
    - Primary codec for all platforms
    - Maximum compatibility

H.265 (HEVC):
  Pros:
    - 30-50% better compression than H.264
    - Excellent for 4K content
  Cons:
    - Licensing costs
    - Limited browser support (Safari only)
    - Higher encoding complexity
  Use Cases:
    - 4K/8K content
    - Premium tier (save bandwidth)
    - Apple ecosystem

VP9 (Google):
  Pros:
    - Royalty-free
    - 30-50% better compression than H.264
    - Chrome/Firefox native support
  Cons:
    - No Safari support
    - Slower encoding than H.264
  Use Cases:
    - YouTube's primary codec
    - Cost-conscious platforms
    - Chrome/Android heavy user base

AV1:
  Pros:
    - 30% better than VP9
    - Royalty-free
    - Future-proof
  Cons:
    - Very slow encoding (10-20× H.264)
    - Limited hardware acceleration (new devices only)
    - Not widely supported yet
  Use Cases:
    - Netflix experiments
    - Long-tail content (encode once, serve forever)
    - Future investment

Recommendation:
  Primary: H.264 (universal compatibility)
  Secondary: H.265 for 4K (Apple devices)
  Experimental: AV1 for archival content
```

### Encoding Presets (FFmpeg)

```bash
# Ultrafast: Fastest encoding, largest files (5× real-time, 30% larger files)
ffmpeg -i input.mp4 -c:v libx264 -preset ultrafast output.mp4

# Veryfast: Good for live streaming (3× real-time, 15% larger)
ffmpeg -i input.mp4 -c:v libx264 -preset veryfast output.mp4

# Fast: Balanced for real-time needs (2× real-time, 10% larger)
ffmpeg -i input.mp4 -c:v libx264 -preset fast output.mp4

# Medium: Default, good quality/speed trade-off (1× real-time, baseline)
ffmpeg -i input.mp4 -c:v libx264 -preset medium output.mp4

# Slow: Better compression (0.5× real-time, 5-10% smaller)
ffmpeg -i input.mp4 -c:v libx264 -preset slow output.mp4

# Veryslow: Best quality (0.25× real-time, 10-15% smaller)
ffmpeg -i input.mp4 -c:v libx264 -preset veryslow output.mp4

# Recommendation for VOD: Medium (good balance)
# Recommendation for cost optimization: Slow (worth the extra time)
```

### CRF (Constant Rate Factor) vs Bitrate

```bash
# CRF (Recommended for VOD): Variable bitrate based on content complexity
# Range: 0 (lossless) to 51 (worst quality)
# Sweet spot: 18-23 (visually lossless to high quality)

# Simple content (animation, slides): CRF 23
ffmpeg -i animation.mp4 -c:v libx264 -preset medium -crf 23 output.mp4

# Normal content (movies, TV): CRF 20-21
ffmpeg -i movie.mp4 -c:v libx264 -preset medium -crf 20 output.mp4

# Complex content (action, sports): CRF 18-19
ffmpeg -i action.mp4 -c:v libx264 -preset medium -crf 18 output.mp4

# Constant Bitrate (CBR): For streaming (predictable bandwidth)
ffmpeg -i input.mp4 -c:v libx264 -preset medium \
  -b:v 5000k -maxrate 5000k -bufsize 10000k output.mp4

# Variable Bitrate (VBR): For VOD (better quality)
ffmpeg -i input.mp4 -c:v libx264 -preset medium \
  -b:v 5000k -maxrate 5350k -bufsize 7500k output.mp4
```

### Two-Pass Encoding (Best Quality)

```bash
#!/bin/bash
# First pass: Analyze video
ffmpeg -i input.mp4 -c:v libx264 -preset medium \
  -b:v 5000k -pass 1 -f null /dev/null

# Second pass: Encode with optimal bit allocation
ffmpeg -i input.mp4 -c:v libx264 -preset medium \
  -b:v 5000k -pass 2 \
  -c:a aac -b:a 128k \
  output.mp4

# Cleanup
rm ffmpeg2pass-0.log
```

**Benefits:**
- 5-10% better quality at same bitrate
- More efficient bit distribution
- Smoother quality across scenes

**Trade-offs:**
- 2× encoding time
- Only useful for VOD (not live)

---

## Per-Title Encoding

### Netflix's Per-Title Encoding

**Concept:** Optimize bitrate for each video based on content complexity.

```python
import subprocess
import json
import numpy as np

def analyze_video_complexity(input_file):
    """Analyze video to determine optimal bitrate ladder"""
    
    # Run FFmpeg with VMAF quality metric
    cmd = [
        'ffmpeg', '-i', input_file,
        '-lavfi', 'vmaf=model_path=/usr/share/model/vmaf_v0.6.1.json',
        '-f', 'null', '-'
    ]
    
    result = subprocess.run(cmd, capture_output=True, text=True)
    
    # Parse VMAF scores
    vmaf_scores = parse_vmaf_output(result.stderr)
    complexity = np.mean(vmaf_scores)
    
    return complexity

def get_encoding_ladder(complexity):
    """
    Generate custom encoding ladder based on complexity
    
    Complexity ranges:
    - Low (70-100): Simple animation, slides, static scenes
    - Medium (40-70): Normal movies, TV shows
    - High (0-40): Action, sports, complex scenes
    """
    
    if complexity > 70:  # Simple content
        return [
            {'name': '4k', 'bitrate': '8000k', 'resolution': '3840x2160'},
            {'name': '1080p', 'bitrate': '3500k', 'resolution': '1920x1080'},
            {'name': '720p', 'bitrate': '1800k', 'resolution': '1280x720'},
            {'name': '480p', 'bitrate': '800k', 'resolution': '854x480'},
        ]
    elif complexity > 40:  # Normal content
        return [
            {'name': '4k', 'bitrate': '12000k', 'resolution': '3840x2160'},
            {'name': '1080p', 'bitrate': '5000k', 'resolution': '1920x1080'},
            {'name': '720p', 'bitrate': '2500k', 'resolution': '1280x720'},
            {'name': '480p', 'bitrate': '1000k', 'resolution': '854x480'},
        ]
    else:  # Complex content
        return [
            {'name': '4k', 'bitrate': '16000k', 'resolution': '3840x2160'},
            {'name': '1080p', 'bitrate': '6500k', 'resolution': '1920x1080'},
            {'name': '720p', 'bitrate': '3500k', 'resolution': '1280x720'},
            {'name': '480p', 'bitrate': '1500k', 'resolution': '854x480'},
        ]

# Example usage
complexity = analyze_video_complexity('movie.mp4')
ladder = get_encoding_ladder(complexity)

for profile in ladder:
    print(f"{profile['name']}: {profile['bitrate']} @ {profile['resolution']}")
```

**Benefits:**
- 30% bandwidth savings on average
- Better quality for complex content
- Lower storage for simple content

**Example:**
```
Simple Animation (e.g., BoJack Horseman):
  - 1080p: 3.5 Mbps (vs 5 Mbps standard) → 30% savings
  
Complex Action (e.g., Extraction):
  - 1080p: 6.5 Mbps (vs 5 Mbps standard) → Better quality
```

### VMAF-Based Optimization

```bash
#!/bin/bash
# Test multiple bitrates and pick optimal one that achieves VMAF > 95

INPUT="input.mp4"
TARGET_VMAF=95

for bitrate in 3000 4000 5000 6000 7000; do
    # Encode
    ffmpeg -i $INPUT -c:v libx264 -preset medium \
      -b:v ${bitrate}k -maxrate ${bitrate}k -bufsize $((bitrate * 2))k \
      -c:a aac -b:a 128k test_${bitrate}.mp4
    
    # Calculate VMAF
    vmaf_score=$(ffmpeg -i test_${bitrate}.mp4 -i $INPUT \
      -lavfi "vmaf=model_path=/usr/share/model/vmaf_v0.6.1.json" \
      -f null - 2>&1 | grep "VMAF score" | awk '{print $4}')
    
    echo "Bitrate: ${bitrate}k → VMAF: $vmaf_score"
    
    # If VMAF meets target, this is optimal bitrate
    if (( $(echo "$vmaf_score > $TARGET_VMAF" | bc -l) )); then
        echo "Optimal bitrate found: ${bitrate}k"
        mv test_${bitrate}.mp4 output_optimized.mp4
        
        # Cleanup
        rm test_*.mp4
        break
    fi
done
```

---

## CDN Optimization Strategies

### Cache Hit Ratio Optimization

**Goal:** Achieve >95% cache hit ratio

#### 1. Cache Tiering

```yaml
Edge Tier (Closest to user):
  - Popular content (top 10%)
  - Recently requested
  - Cache duration: 24 hours
  - Capacity: 10 TB per PoP
  
Mid Tier (Regional):
  - Warm content (next 30%)
  - Cache duration: 7 days
  - Capacity: 100 TB per region
  
Origin Shield:
  - All content
  - Reduces origin load by 90%
  - Cache duration: 30 days
  - Capacity: 1 PB

Request Flow:
  User → Edge (95% hit) → Mid (4% hit) → Origin Shield (0.9% hit) → S3 (0.1% miss)
```

#### 2. Predictive Prefetching

```python
import pandas as pd
from sklearn.ensemble import RandomForestClassifier

class PrefetchPredictor:
    def __init__(self):
        self.model = RandomForestClassifier()
    
    def train(self, historical_data):
        """
        Train model to predict videos that will be popular
        
        Features:
        - Upload time (day, hour)
        - Uploader popularity
        - Category/genre
        - Initial view velocity (first hour)
        - Social media mentions
        """
        X = historical_data[['upload_hour', 'uploader_followers', 'category', 
                             'first_hour_views', 'social_mentions']]
        y = historical_data['became_viral']  # Binary: hit top 1000 within 24h
        
        self.model.fit(X, y)
    
    def predict_viral_videos(self, recent_uploads):
        """Predict which videos will go viral → prefetch to edge"""
        predictions = self.model.predict_proba(recent_uploads)
        
        # Videos with >70% probability of going viral
        viral_candidates = recent_uploads[predictions[:, 1] > 0.7]
        
        return viral_candidates

# Prefetch strategy
def prefetch_to_edge(video_ids):
    """Push predicted popular content to all edge PoPs"""
    for pop in edge_pops:
        for video_id in video_ids:
            # Push all quality variants
            for quality in ['1080p', '720p', '480p']:
                cdn_api.prefetch(
                    url=f'https://origin.example.com/videos/{video_id}/{quality}/playlist.m3u8',
                    pops=[pop]
                )
```

#### 3. Overnight Sync (Netflix Model)

```python
import schedule
import time

def nightly_content_sync():
    """
    Run at 3 AM local time at each edge PoP
    Sync predicted popular content for next day
    """
    
    # Get trending predictions
    trending = get_trending_predictions_for_tomorrow()
    
    # Get regional preferences
    regional_popular = get_regional_popular_content()
    
    # Combine: 70% trending + 30% regional
    to_sync = trending[:7000] + regional_popular[:3000]
    
    # Download to local edge cache
    for video_id in to_sync:
        sync_video_to_local_cache(video_id)
    
    # Evict least recently used content
    evict_lru_content()

# Schedule for 3 AM
schedule.every().day.at("03:00").do(nightly_content_sync)

while True:
    schedule.run_pending()
    time.sleep(60)
```

### Geographic Distribution

```yaml
CDN PoP Placement Strategy:

High-Density Regions (50+ PoPs):
  - USA: 80 PoPs
  - Europe: 60 PoPs
  - Asia: 100 PoPs
  
PoP Sizing:
  Tier 1 Cities (NYC, Tokyo, London):
    - 50-100 servers per PoP
    - 5-10 PB total cache
  
  Tier 2 Cities:
    - 10-20 servers per PoP
    - 1-2 PB cache
  
  Tier 3 (Rural):
    - 2-5 servers
    - 200 TB cache

ISP Peering:
  - Negotiate direct peering with major ISPs
  - Place cache boxes inside ISP networks (Netflix model)
  - Reduces transit costs to near-zero
  - Improves latency (single hop)
```

### Cache Invalidation Strategy

```python
class CacheInvalidator:
    def __init__(self, cdn_api):
        self.cdn = cdn_api
    
    def invalidate_video(self, video_id, reason):
        """
        Invalidate cached video (e.g., content violation, user deletion)
        
        Strategies:
        1. Immediate purge (expensive, disrupts users)
        2. Soft delete (mark deleted, prevent new requests, lazy cleanup)
        3. TTL expiration (wait for natural cache expiry)
        """
        
        if reason == 'legal_dmca':
            # Immediate purge required
            self.cdn.purge_all_pops(f'/videos/{video_id}/*')
            print(f"Purged {video_id} from all PoPs immediately")
        
        elif reason == 'user_deleted':
            # Soft delete: Block new requests, let cache expire
            self.cdn.block_path(f'/videos/{video_id}/*')
            print(f"Blocked {video_id}, cache will expire naturally")
        
        elif reason == 'updated_version':
            # Versioned URLs avoid need for purge
            # Old: /videos/abc/v1/playlist.m3u8
            # New: /videos/abc/v2/playlist.m3u8
            print(f"New version uploaded, old cache will expire")
    
    def invalidate_user_content(self, user_id):
        """Invalidate all videos from a user (e.g., channel ban)"""
        video_ids = get_videos_by_user(user_id)
        
        for video_id in video_ids:
            self.invalidate_video(video_id, reason='user_banned')
```

---

## Quality of Experience (QoE) Metrics

### Key Metrics

```python
class QoECalculator:
    def calculate_qoe_score(self, session_data):
        """
        Calculate overall QoE score (0-100)
        
        Factors:
        - Startup time (weight: 30%)
        - Buffering ratio (weight: 40%)
        - Average bitrate (weight: 20%)
        - Quality switches (weight: 10%)
        """
        
        # Startup time score (0-100)
        # Target: <1 second
        if session_data['startup_time_ms'] < 1000:
            startup_score = 100
        elif session_data['startup_time_ms'] < 2000:
            startup_score = 80
        elif session_data['startup_time_ms'] < 3000:
            startup_score = 60
        else:
            startup_score = max(0, 100 - session_data['startup_time_ms'] / 50)
        
        # Buffering ratio score (0-100)
        # Target: <0.1% (less than 0.1% of time spent buffering)
        buffering_ratio = session_data['buffer_time_ms'] / session_data['total_play_time_ms']
        buffering_score = max(0, 100 - buffering_ratio * 10000)
        
        # Average bitrate score (0-100)
        # Higher is better (but capped at available bandwidth)
        avg_bitrate = session_data['total_bytes'] / session_data['total_play_time_ms'] * 8  # Kbps
        max_bitrate = 12000  # 12 Mbps (4K)
        bitrate_score = min(100, (avg_bitrate / max_bitrate) * 100)
        
        # Quality switches score (0-100)
        # Fewer is better (too many = jarring experience)
        switches = session_data['quality_switches']
        switch_score = max(0, 100 - switches * 5)
        
        # Weighted average
        qoe_score = (
            startup_score * 0.3 +
            buffering_score * 0.4 +
            bitrate_score * 0.2 +
            switch_score * 0.1
        )
        
        return {
            'overall_qoe': qoe_score,
            'startup_score': startup_score,
            'buffering_score': buffering_score,
            'bitrate_score': bitrate_score,
            'switch_score': switch_score
        }

# Example usage
session = {
    'startup_time_ms': 850,
    'buffer_time_ms': 200,
    'total_play_time_ms': 3600000,  # 1 hour
    'total_bytes': 2250000000,  # 2.25 GB
    'quality_switches': 3
}

qoe = QoECalculator().calculate_qoe_score(session)
print(f"QoE Score: {qoe['overall_qoe']:.1f}/100")
```

### Real-time Monitoring

```python
from prometheus_client import Counter, Histogram, Gauge

# Define metrics
playback_starts = Counter('video_playback_start_total', 'Total playback starts')
startup_time = Histogram('video_startup_time_seconds', 'Video startup time', 
                         buckets=[0.5, 1, 2, 3, 5, 10])
buffering_ratio = Histogram('video_buffering_ratio', 'Buffering ratio',
                            buckets=[0.001, 0.005, 0.01, 0.05, 0.1])
concurrent_viewers = Gauge('concurrent_viewers', 'Current concurrent viewers')

# Track events
def on_playback_start(startup_time_ms):
    playback_starts.inc()
    startup_time.observe(startup_time_ms / 1000)

def on_playback_complete(buffer_time_ms, total_time_ms):
    ratio = buffer_time_ms / total_time_ms
    buffering_ratio.observe(ratio)

def update_concurrent_viewers(count):
    concurrent_viewers.set(count)
```

---

## Cost Optimization

### Storage Tiering

```python
class StorageOptimizer:
    def optimize_video_storage(self, video_id):
        """
        Move video to appropriate storage tier based on access patterns
        
        Tiers:
        - S3 Standard: $0.023/GB (recently uploaded, trending)
        - S3 IA: $0.0125/GB (30+ days old, moderate access)
        - S3 Glacier IR: $0.004/GB (90+ days old, rare access)
        - S3 Deep Archive: $0.00099/GB (archival)
        """
        
        video = get_video_metadata(video_id)
        age_days = (datetime.now() - video.upload_date).days
        recent_views = get_view_count(video_id, days=7)
        
        # Decision logic
        if age_days < 30 or recent_views > 1000:
            # Hot content: Keep in Standard
            tier = 'STANDARD'
        
        elif age_days < 90 or recent_views > 100:
            # Warm content: Move to IA
            tier = 'STANDARD_IA'
        
        elif age_days < 365:
            # Cold content: Move to Glacier Instant Retrieval
            tier = 'GLACIER_IR'
        
        else:
            # Archive: Move to Deep Archive
            tier = 'DEEP_ARCHIVE'
        
        # Transition if needed
        current_tier = get_storage_tier(video_id)
        if current_tier != tier:
            transition_to_tier(video_id, tier)
            print(f"Moved {video_id} from {current_tier} to {tier}")

# Automated daily job
def daily_storage_optimization():
    videos = get_all_videos()
    
    for video_id in videos:
        optimize_video_storage(video_id)

# Cost savings example
"""
1 million videos, 10 GB average, lifecycle:

All Standard: 10M GB × $0.023 = $230,000/month

With tiering:
- 10% Standard (1M GB × $0.023): $23,000
- 30% IA (3M GB × $0.0125): $37,500
- 40% Glacier IR (4M GB × $0.004): $16,000
- 20% Deep Archive (2M GB × $0.00099): $1,980

Total: $78,480/month (66% savings)
"""
```

### Encoding Cost Optimization

```python
def optimize_encoding_cost(video_duration_minutes):
    """
    Choose encoding method based on video length
    
    Options:
    1. AWS MediaConvert: $0.015/min (HD) - Fast, managed
    2. EC2 On-Demand: $1.53/hour (c5.9xlarge) - Flexible
    3. EC2 Spot: $0.46/hour (70% off) - Cheapest
    """
    
    # Encoding speed: 1× real-time (c5.9xlarge)
    encoding_time_hours = video_duration_minutes / 60
    
    # Cost comparison
    mediaconvert_cost = video_duration_minutes * 0.015
    ec2_ondemand_cost = encoding_time_hours * 1.53
    ec2_spot_cost = encoding_time_hours * 0.46
    
    costs = {
        'MediaConvert': mediaconvert_cost,
        'EC2 On-Demand': ec2_ondemand_cost,
        'EC2 Spot': ec2_spot_cost
    }
    
    # Pick cheapest
    optimal = min(costs, key=costs.get)
    
    return {
        'method': optimal,
        'cost': costs[optimal],
        'all_costs': costs
    }

# Example: 45-minute video
result = optimize_encoding_cost(45)
print(f"Optimal method: {result['method']} (${result['cost']:.2f})")

"""
Output:
All costs: {'MediaConvert': $0.675, 'EC2 On-Demand': $1.15, 'EC2 Spot': $0.35}
Optimal method: EC2 Spot ($0.35)

For high volume (100,000 videos/month):
- MediaConvert: $67,500/month
- EC2 Spot: $35,000/month
- Savings: $32,500/month (48%)
"""
```

### CDN Cost Reduction

```yaml
Strategies:

1. Own CDN at Scale:
   Netflix example:
   - Third-party CDN: $135M/month (13.5 EB × $0.01/GB)
   - Open Connect: $6.4M/month (own infrastructure)
   - Savings: $128.6M/month (95%)

2. Intelligent Caching:
   - 95% cache hit: Origin serves only 5%
   - Origin bandwidth: 2.5 Tbps (vs 50 Tbps total)
   - Origin cost: $675K/month (vs $13.5M for 100% origin)
   - Savings: $12.8M/month (95%)

3. Regional Failover:
   - Primary CDN: Own infrastructure
   - Backup CDN: Cloudflare (pay only during failures)
   - Avg failover: 0.1% of traffic
   - Backup cost: $13.5K/month (vs $135M for primary)

4. Compression:
   - Enable gzip/brotli for manifests (10× reduction)
   - Manifest requests: 10% of total bandwidth
   - Savings: ~9% of total CDN cost

5. Smart Routing:
   - Route users to least expensive PoP
   - Avoid expensive regions (Africa, Australia)
   - Offer SD-only in expensive regions
   - Savings: 10-15% of total cost
```

---

**This guide provides advanced optimization techniques for video encoding and CDN performance used by platforms like Netflix, YouTube, and Twitch.**
