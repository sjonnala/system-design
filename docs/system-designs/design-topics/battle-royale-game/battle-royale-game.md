# Design a Battle Royale Game System

**Companies**: Epic Games (Fortnite), PUBG Corp, Activision (Warzone), Respawn (Apex Legends)
**Difficulty**: Advanced
**Time**: 60 minutes
**Pattern**: Gaming & Interactive - State Synchronization and Real-time Multiplayer

## Problem Statement

Design a scalable Battle Royale game system that supports 100 players per match with real-time state synchronization, low-latency gameplay (<50ms), matchmaking, anti-cheat, voice communication, and spectator features. The system must handle millions of concurrent players globally.

---

## Step 1: Requirements (5 min)

### Functional Requirements

1. **Matchmaking**: Match 100 players based on skill, region, and queue time
2. **Game Session Management**: Create, manage, and destroy game instances
3. **Real-time State Synchronization**: Sync player positions, actions, combat at 60Hz
4. **Combat System**: Hit detection, weapon mechanics, damage calculation
5. **Safe Zone/Storm Mechanics**: Shrinking play area over time
6. **Inventory Management**: Loot, weapons, items, crafting
7. **Voice Communication**: Proximity-based and team voice chat
8. **Leaderboards & Statistics**: Player stats, rankings, match history
9. **Spectator Mode**: Watch ongoing matches, player perspectives
10. **Replay System**: Record and replay matches
11. **Anti-Cheat**: Detect and prevent cheating in real-time

**Prioritize**: Focus on matchmaking, game session management, and state synchronization for MVP

### Non-Functional Requirements

1. **Ultra-Low Latency**: <50ms server tick rate, <100ms RTT for players
2. **High Availability**: 99.95% uptime for game services
3. **Scalability**: Support 10M+ concurrent players globally
4. **Consistency**: Deterministic game state across all clients
5. **Security**: Prevent cheating, DDoS protection
6. **Regional Performance**: <30ms latency to nearest game server

### Capacity Estimation

**Assumptions**:
- 10M concurrent players globally
- 100 players per match
- Average match duration: 20 minutes
- 60 tick rate (60 updates/second)
- Player retention: 2 hours/day average

**Match Load**:
```
Concurrent matches = 10M players ÷ 100 players/match = 100,000 active matches
Matches started per hour = (10M × 2 hours) ÷ 20 min = ~6M matches/day = 250K matches/hour
```

**Network Load**:
```
State updates per second = 100 players × 60 ticks/sec = 6,000 updates/match
Total updates/sec globally = 100K matches × 6K = 600M updates/sec
```

**Data per Update**:
```
Player state: Position (12B) + Rotation (8B) + Velocity (12B) + Health (4B) + Action (4B) = ~40 bytes
Per match bandwidth: 6,000 updates × 40 bytes = 240 KB/sec = ~2 Mbps
Total bandwidth: 100K matches × 2 Mbps = 200 Tbps (distributed across regions)
```

**Storage** (Analytics & Replays):
```
Match replay data: 20 min × 60 sec × 60 ticks × 100 players × 40 bytes = ~2.9 GB/match
Daily replay storage: 6M matches × 2.9 GB = ~17 PB/day (compressed ~10:1 = 1.7 PB)
Player stats per match: 10 KB
Daily stats: 6M matches × 100 players × 10 KB = 6 TB/day
```

---

## Step 2: High-Level Architecture (20 min)

### System Components Diagram

```
                        ┌─────────────────────────────────┐
                        │     Client (Game Engine)        │
                        │   Unity/Unreal Engine + Netcode│
                        └──────────────┬──────────────────┘
                                       │
                        ┌──────────────┴──────────────┐
                        │                             │
                ┌───────▼────────┐          ┌────────▼────────┐
                │   CDN (Assets) │          │  Voice Service   │
                │  CloudFront/   │          │   Agora/Vivox    │
                │   Fastly       │          └──────────────────┘
                └────────────────┘
                                       │
                        ┌──────────────▼──────────────────┐
                        │    Global Load Balancer          │
                        │   GeoDNS + Anycast Routing       │
                        └──────────────┬──────────────────┘
                                       │
        ┌──────────────────────────────┼──────────────────────────────┐
        │                              │                              │
   ┌────▼────┐                    ┌────▼────┐                   ┌────▼────┐
   │ US-West │                    │ EU-West │                   │ Asia-SE │
   │ Region  │                    │ Region  │                   │ Region  │
   └────┬────┘                    └────┬────┘                   └────┬────┘
        │                              │                              │
        │                              │                              │
   ┌────▼──────────────────────────────▼──────────────────────────────▼────┐
   │                    Regional Services (Per Region)                     │
   │  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐                │
   │  │ Matchmaking │  │Authentication│  │  Lobby       │                │
   │  │   Service   │  │   Service    │  │  Service     │                │
   │  └──────┬──────┘  └──────┬───────┘  └──────┬───────┘                │
   │         │                │                  │                        │
   │  ┌──────▼────────────────▼──────────────────▼───────┐                │
   │  │        Game Session Manager (GSM)                │                │
   │  │    Orchestrates dedicated game servers           │                │
   │  └──────┬─────────────────────────────────────┬─────┘                │
   │         │                                     │                      │
   │  ┌──────▼──────┐                      ┌───────▼────────┐             │
   │  │  Game Server│  [×1000s per region] │  Anti-Cheat    │             │
   │  │    Fleet    │                      │    Service     │             │
   │  │ (Dedicated) │                      │  (Server-side) │             │
   │  └──────┬──────┘                      └────────────────┘             │
   └─────────┼──────────────────────────────────────────────────────────────┘
             │
   ┌─────────▼──────────────────────────────────────────┐
   │               Data Layer (Global)                  │
   │  ┌──────────┐  ┌──────────┐  ┌─────────────┐     │
   │  │ Player   │  │  Match   │  │  Analytics  │     │
   │  │   DB     │  │ History  │  │    Store    │     │
   │  │(DynamoDB)│  │ (S3 +    │  │(ClickHouse) │     │
   │  │          │  │Athena)   │  │             │     │
   │  └──────────┘  └──────────┘  └─────────────┘     │
   └────────────────────────────────────────────────────┘
```

### Core Components

#### 1. Client (Game Engine)
- **Rendering Engine**: Unity/Unreal Engine
- **Network Layer**: Custom UDP-based netcode or Photon/Mirror
- **Client-side Prediction**: Predict player actions to hide latency
- **Lag Compensation**: Rewind time for hit detection
- **Asset Streaming**: Download maps, skins, updates

#### 2. Matchmaking Service
- **Skill-based Matching**: ELO/MMR algorithm
- **Region-based Routing**: Route to nearest data center
- **Queue Management**: Handle player queues, party formation
- **Backfill Logic**: Fill incomplete lobbies

#### 3. Game Session Manager (GSM)
- **Server Allocation**: Spin up dedicated servers on-demand
- **Health Monitoring**: Track server performance, auto-replace failed servers
- **Auto-scaling**: Scale server fleet based on demand
- **Region Selection**: Choose optimal region for match

#### 4. Dedicated Game Server
- **Authoritative Server**: Server is source of truth (not P2P)
- **Physics Simulation**: Run game physics at 60Hz
- **State Broadcast**: Send state updates to all clients
- **Hit Detection**: Server-side validation to prevent cheating
- **Event Processing**: Handle player inputs, combat, looting

#### 5. Anti-Cheat Service
- **Behavioral Analysis**: Detect impossible actions (speed hacks, aimbots)
- **Signature Detection**: Known cheat patterns
- **Server-side Validation**: Validate all client actions
- **Report System**: Player reporting and review

#### 6. Voice Service
- **Proximity Chat**: Spatial audio based on player distance
- **Team Chat**: Squad/team communication channels
- **Voice Codec**: Opus codec for low-latency audio
- **Integration**: Agora, Vivox, or custom WebRTC

---

## Step 3: Detailed Design (25 min)

### 3.1 Matchmaking Algorithm

```python
class Matchmaker:
    def __init__(self):
        self.queue = PriorityQueue()
        self.skill_tolerance_ms = 5000  # Expand skill range over time

    def find_match(self, player):
        """
        Match players based on:
        1. Region (hard constraint)
        2. Skill rating (soft constraint - expands over time)
        3. Queue time (FIFO for long-waiting players)
        """
        region = player.region
        skill = player.mmr  # Matchmaking Rating
        wait_time = time.now() - player.queue_join_time

        # Expand skill tolerance based on wait time
        skill_range = base_skill_range + (wait_time / 1000) * 50

        # Find 99 other players in region with similar skill
        candidates = self.queue.get_players(
            region=region,
            skill_min=skill - skill_range,
            skill_max=skill + skill_range,
            limit=99
        )

        if len(candidates) >= 99:
            # Create match with 100 players
            match_players = [player] + candidates[:99]
            self.create_game_session(match_players)
            return True

        return False  # Keep waiting

    def create_game_session(self, players):
        """
        Request game server from GSM
        """
        # 1. Request dedicated server
        server = game_session_manager.allocate_server(
            region=players[0].region,
            match_size=len(players),
            game_mode='battle_royale'
        )

        # 2. Send connection info to all players
        for player in players:
            send_match_found(player, server.ip, server.port, server.token)

        # 3. Monitor match lifecycle
        monitor_match(server.match_id)
```

### 3.2 Game Server State Synchronization

**Network Protocol**: UDP with custom reliability layer

```python
class GameServer:
    def __init__(self):
        self.tick_rate = 60  # 60Hz server
        self.tick_duration = 1.0 / self.tick_rate
        self.world_state = WorldState()
        self.clients = {}  # player_id -> ClientConnection

    def game_loop(self):
        """
        Main server loop running at 60Hz
        """
        while self.match_active:
            frame_start = time.now()

            # 1. Process client inputs (buffered from network thread)
            self.process_inputs()

            # 2. Simulate game physics
            self.simulate_physics()

            # 3. Process game logic
            self.process_combat()
            self.process_storm()
            self.check_win_conditions()

            # 4. Broadcast state to all clients
            self.broadcast_state()

            # 5. Sleep until next tick
            elapsed = time.now() - frame_start
            sleep(self.tick_duration - elapsed)

    def process_inputs(self):
        """
        Process buffered player inputs with timestamp validation
        """
        for player_id, input_buffer in self.input_buffers.items():
            while input_buffer.has_data():
                input_packet = input_buffer.pop()

                # Validate timestamp (prevent future inputs)
                if input_packet.timestamp > self.current_tick:
                    continue  # Reject future-dated inputs

                # Apply input to player state
                player = self.world_state.players[player_id]
                player.apply_input(input_packet)

    def simulate_physics(self):
        """
        Update positions, velocities, collisions
        """
        for player in self.world_state.players.values():
            # Update position based on velocity
            player.position += player.velocity * self.tick_duration

            # Check collisions with terrain
            self.check_terrain_collision(player)

            # Apply gravity
            if not player.is_grounded:
                player.velocity.y -= GRAVITY * self.tick_duration

    def broadcast_state(self):
        """
        Send state updates to all clients
        Uses delta compression - only send what changed
        """
        # Build snapshot of current world state
        snapshot = self.world_state.create_snapshot(self.current_tick)

        for player_id, client in self.clients.items():
            # Delta compression: only send changes since last ACK
            last_ack_tick = client.last_acknowledged_tick
            delta = snapshot.compute_delta(last_ack_tick)

            # Priority-based culling: nearby players get more updates
            player_pos = self.world_state.players[player_id].position
            delta.prioritize_by_distance(player_pos, max_distance=500)

            # Send compressed delta
            packet = StateUpdatePacket(
                tick=self.current_tick,
                delta=delta,
                reliable_events=self.get_reliable_events(player_id)
            )
            client.send(packet)
```

### 3.3 Client-Side Prediction & Lag Compensation

```python
class GameClient:
    def __init__(self):
        self.predicted_state = LocalPlayerState()
        self.server_state = WorldState()
        self.input_buffer = []

    def send_input(self, input_data):
        """
        Send player input to server with client-side prediction
        """
        # Add client timestamp
        input_packet = InputPacket(
            tick=self.local_tick,
            timestamp=time.now(),
            movement=input_data.movement,
            actions=input_data.actions
        )

        # Send to server
        self.connection.send_unreliable(input_packet)

        # Store for replay (in case of correction)
        self.input_buffer.append(input_packet)

        # Client-side prediction: apply input immediately
        self.predicted_state.apply_input(input_packet)

    def on_server_state_update(self, state_packet):
        """
        Reconcile server state with client prediction
        """
        # Extract server's authoritative state for local player
        server_player_state = state_packet.get_player(self.player_id)

        # Compare with predicted state
        position_error = (server_player_state.position -
                         self.predicted_state.position).length()

        if position_error > RECONCILIATION_THRESHOLD:
            # Significant misprediction - reconcile

            # 1. Snap to server state
            self.predicted_state.position = server_player_state.position
            self.predicted_state.velocity = server_player_state.velocity

            # 2. Replay inputs since server tick
            server_tick = state_packet.tick
            for input_packet in self.input_buffer:
                if input_packet.tick > server_tick:
                    self.predicted_state.apply_input(input_packet)

            # 3. Clean old inputs
            self.input_buffer = [i for i in self.input_buffer
                               if i.tick > server_tick]

        # Update other players (no prediction for them)
        for other_player in state_packet.players:
            if other_player.id != self.player_id:
                self.world_state.update_player(other_player)
```

### 3.4 Hit Detection with Lag Compensation

```python
class HitDetection:
    def __init__(self):
        self.state_history = CircularBuffer(size=120)  # 2 seconds @ 60Hz

    def validate_shot(self, shooter_id, target_id, shot_time, shot_position):
        """
        Server-side hit validation with lag compensation
        """
        # 1. Get shooter's latency
        shooter_latency = self.clients[shooter_id].rtt / 2

        # 2. Rewind world state to when shooter fired (their perspective)
        compensation_time = time.now() - shooter_latency
        historical_state = self.state_history.get_state_at(compensation_time)

        # 3. Check if target was in crosshair at that time
        target_historical_pos = historical_state.players[target_id].position
        target_hitbox = Hitbox(target_historical_pos)

        # 4. Ray-cast from shooter to check hit
        ray = Ray(origin=shot_position, direction=shot_direction)
        hit = ray.intersects(target_hitbox)

        if hit:
            # Valid hit - apply damage
            damage = self.calculate_damage(weapon_type, hit.location)
            self.apply_damage(target_id, damage)
            return True
        else:
            # Miss or potential cheat
            self.anti_cheat.log_suspicious_shot(shooter_id, shot_data)
            return False
```

### 3.5 Storm/Safe Zone Mechanics

```python
class StormSystem:
    def __init__(self):
        self.current_phase = 0
        self.phases = [
            {'duration': 300, 'shrink_time': 120, 'damage': 1, 'radius': 2000},
            {'duration': 180, 'shrink_time': 90,  'damage': 2, 'radius': 1000},
            {'duration': 120, 'shrink_time': 60,  'damage': 5, 'radius': 500},
            {'duration': 90,  'shrink_time': 45,  'damage': 10, 'radius': 250},
            {'duration': 60,  'shrink_time': 30,  'damage': 20, 'radius': 100},
        ]

    def update(self, delta_time):
        """
        Update storm position and apply damage
        """
        phase = self.phases[self.current_phase]

        # Shrink safe zone
        if self.shrink_timer < phase['shrink_time']:
            progress = self.shrink_timer / phase['shrink_time']
            self.safe_zone.radius = lerp(
                self.safe_zone.previous_radius,
                phase['radius'],
                progress
            )
            self.shrink_timer += delta_time

        # Apply damage to players outside safe zone
        for player in self.world_state.players.values():
            distance = (player.position - self.safe_zone.center).length()
            if distance > self.safe_zone.radius:
                self.apply_storm_damage(player, phase['damage'])

        # Move to next phase
        if self.phase_timer >= phase['duration']:
            self.current_phase += 1
            self.phase_timer = 0
            self.shrink_timer = 0
            self.safe_zone.previous_radius = self.safe_zone.radius
```

---

## Step 4: Data Model (10 min)

### Database Schema

```sql
-- Player Profile (DynamoDB - Global)
{
    "player_id": "uuid",
    "username": "string",
    "region": "us-west|eu-west|asia-se",
    "mmr": 1500,  // Matchmaking Rating
    "rank": "bronze|silver|gold|platinum|diamond|master",
    "total_matches": 1523,
    "wins": 145,
    "kills": 3421,
    "k_d_ratio": 1.42,
    "created_at": "timestamp",
    "last_login": "timestamp",
    "account_status": "active|banned|suspended"
}

-- Match History (DynamoDB with GSI)
{
    "match_id": "uuid",
    "region": "us-west",
    "game_mode": "battle_royale",
    "started_at": "timestamp",
    "ended_at": "timestamp",
    "duration_seconds": 1234,
    "winner": "player_id",
    "total_players": 100,
    "final_standings": [
        {"player_id": "...", "rank": 1, "kills": 12, "damage": 2341},
        {"player_id": "...", "rank": 2, "kills": 8, "damage": 1823},
        ...
    ],
    "replay_s3_path": "s3://replays/2025/11/16/match_xyz.replay"
}

-- Player Match Stats (Cassandra - Time-series)
CREATE TABLE player_match_stats (
    player_id UUID,
    match_id UUID,
    match_timestamp TIMESTAMP,
    placement INT,
    kills INT,
    damage_dealt INT,
    damage_taken INT,
    distance_traveled FLOAT,
    survival_time INT,
    weapons_used LIST<TEXT>,
    items_looted INT,
    PRIMARY KEY (player_id, match_timestamp)
) WITH CLUSTERING ORDER BY (match_timestamp DESC);

-- Leaderboard (Redis Sorted Set)
ZADD leaderboard:global:season_5 1500 player_id_1
ZADD leaderboard:global:season_5 2300 player_id_2
ZADD leaderboard:region:us-west:season_5 1850 player_id_3

-- Active Game Sessions (Redis Hash)
HSET game_session:match_123 "server_ip" "34.56.78.90"
HSET game_session:match_123 "server_port" "7777"
HSET game_session:match_123 "status" "in_progress"
HSET game_session:match_123 "players_alive" "45"
HSET game_session:match_123 "storm_phase" "3"
EXPIRE game_session:match_123 1800  # 30 min TTL

-- Matchmaking Queue (Redis List + Sorted Set)
# Queue by region and skill bracket
ZADD matchmaking:us-west:1000-1500 <timestamp> player_id
ZADD matchmaking:us-west:1500-2000 <timestamp> player_id
```

---

## Step 5: Technology Stack (DevOps & Infrastructure)

### Game Servers
- **Container Orchestration**: Kubernetes (GKE, EKS) with Agones (game server orchestration)
- **Dedicated Server**: C++ or Go for performance
- **Networking**: Custom UDP protocol with RakNet or ENet
- **Scaling**: Horizontal auto-scaling based on player count

### Backend Services
- **API Gateway**: Kong or AWS API Gateway
- **Microservices**: Go, Rust, or Java (Spring Boot)
- **Service Mesh**: Istio for inter-service communication
- **Message Queue**: Kafka for event streaming
- **Caching**: Redis Cluster for sessions, leaderboards

### Databases
- **Player Data**: DynamoDB (NoSQL, global tables for multi-region)
- **Time-series Analytics**: Cassandra or ClickHouse
- **Match History**: S3 + Athena for data lake
- **Transactional**: PostgreSQL (for billing, inventory)

### Infrastructure
- **Cloud Provider**: AWS, GCP, or Azure (multi-region deployment)
- **CDN**: CloudFront or Fastly for asset delivery
- **Load Balancing**: Global Load Balancer with GeoDNS
- **Compute**: EC2 Spot Instances for cost-effective game servers

### Monitoring & Observability
- **Metrics**: Prometheus + Grafana
- **Logging**: ELK Stack (Elasticsearch, Logstash, Kibana)
- **Tracing**: Jaeger or Zipkin for distributed tracing
- **APM**: Datadog or New Relic
- **Game Analytics**: Custom dashboards (Tableau, Looker)

### DevOps & CI/CD
- **Version Control**: Git with GitOps (ArgoCD, Flux)
- **CI/CD**: Jenkins, GitLab CI, or GitHub Actions
- **Infrastructure as Code**: Terraform, Pulumi
- **Configuration Management**: Consul, etcd
- **Secret Management**: Vault, AWS Secrets Manager

### Anti-Cheat & Security
- **Anti-Cheat Engine**: Easy Anti-Cheat, BattleEye, or custom
- **DDoS Protection**: Cloudflare, AWS Shield
- **WAF**: Web Application Firewall
- **Encryption**: TLS 1.3 for API, DTLS for game traffic

---

## Step 6: Advanced Topics (10 min)

### 6.1 Server Reconciliation Strategy

**Problem**: Client and server state can diverge due to latency

**Solution**:
1. **Client-side Prediction**: Client immediately applies inputs
2. **Server Reconciliation**: Server sends authoritative state
3. **Input Replay**: Client replays inputs after reconciliation
4. **Interpolation**: Smooth out corrections visually

### 6.2 Network Optimization Techniques

**Interest Management** (Relevancy System):
```python
def get_relevant_entities(player_position, max_distance=500):
    """
    Only send updates for nearby entities
    Reduces bandwidth by 60-80%
    """
    relevant = []
    for entity in world.entities:
        distance = (entity.position - player_position).length()
        if distance < max_distance:
            # Closer entities get higher update priority
            priority = 1.0 - (distance / max_distance)
            relevant.append((entity, priority))

    return sorted(relevant, key=lambda x: x[1], reverse=True)
```

**Delta Compression**:
```python
# Only send changed fields
previous_state = {position: (100, 50, 200), health: 100}
current_state = {position: (102, 50, 200), health: 95}

# Delta
delta = {position: (102, 50, 200), health: 95}  # Only 2 fields
```

**Bit Packing**:
```
Instead of:  position_x: float32 (4 bytes)
Use:         position_x: int16 (2 bytes) scaled by 0.01
Saves 50% on position data
```

### 6.3 Anti-Cheat Strategies

**Server-side Validation**:
```python
def validate_player_action(action):
    # Check if action is physically possible
    max_speed = 10  # units per second
    distance_moved = (action.new_pos - player.last_pos).length()
    time_elapsed = action.timestamp - player.last_update_time

    if distance_moved / time_elapsed > max_speed * 1.1:  # 10% tolerance
        # Speed hack detected
        flag_player_for_review(player.id, "impossible_movement")
        return False

    return True
```

**Behavioral Analysis**:
- Track headshot percentage (>70% = suspicious)
- Monitor reaction times (<100ms consistently = aimbot)
- Check for perfect recoil compensation
- Analyze crosshair placement (pre-aiming through walls)

### 6.4 Spectator & Replay System

**Architecture**:
```
Game Server → Event Stream → Kafka → Replay Recorder
                                           ↓
                                      S3 Storage
                                           ↓
                                    Replay Player
                                           ↓
                                  Spectator Clients
```

**Replay Format**:
```json
{
  "match_id": "abc123",
  "tick_rate": 60,
  "total_ticks": 72000,
  "events": [
    {"tick": 0, "type": "match_start", "players": [...]},
    {"tick": 120, "type": "player_landed", "player_id": "p1", "position": [...]},
    {"tick": 3600, "type": "player_killed", "victim": "p2", "killer": "p1", "weapon": "rifle"},
    ...
  ]
}
```

---

## Step 7: Scalability & Performance

### Regional Architecture

```
                    Global Layer
                         |
        ┌────────────────┼────────────────┐
        |                |                |
    US-West          EU-West          Asia-SE
        |                |                |
   ┌────┴────┐      ┌────┴────┐      ┌────┴────┐
   | 30K     |      | 40K     |      | 30K     |
   | Matches |      | Matches |      | Matches |
   └─────────┘      └─────────┘      └─────────┘
```

**Per-Region Capacity**:
- 30,000 concurrent matches
- 3M concurrent players
- 1,500 game servers (20 matches per server instance)

### Auto-scaling Strategy

```yaml
# Kubernetes HPA for game servers
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: game-server-fleet
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: game-server
  minReplicas: 100
  maxReplicas: 5000
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Pods
    pods:
      metric:
        name: active_matches_per_pod
      target:
        type: AverageValue
        averageValue: "20"
```

### Cost Optimization

**Use Spot Instances for Game Servers**:
```
On-Demand cost: $0.50/hour × 1500 servers = $750/hour = $540K/month
Spot Instance cost: $0.15/hour × 1500 servers = $225/hour = $162K/month
Savings: $378K/month (70% reduction)
```

**Matchmaking Optimization**:
- Minimize server spin-up time with warm pool
- Pack matches efficiently to reduce idle servers
- Use predictive scaling based on historical data

---

## Step 8: Monitoring & Alerts

### Key Metrics

| **Metric** | **Target** | **Alert Threshold** |
|------------|------------|---------------------|
| Server Tick Rate | 60 Hz | <55 Hz |
| Player RTT (P95) | <100ms | >150ms |
| Match Start Latency | <30s | >60s |
| Server CPU | <70% | >85% |
| Packet Loss | <1% | >3% |
| Matchmaking Time (P95) | <60s | >180s |
| Concurrent Players | - | Drop >20% |
| Cheat Detection Rate | - | >5% of matches |

### Sample Dashboard

```
┌─────────────────────────────────────────────────────────┐
│               Battle Royale Metrics                     │
├─────────────────────────────────────────────────────────┤
│ Concurrent Players: 9.8M         ▲ 5% vs yesterday     │
│ Active Matches: 98K              ▬ Stable               │
│ Avg Server Tick: 59.8 Hz         ✓ Healthy              │
│ P95 Player Latency: 87ms         ✓ Healthy              │
│ Matchmaking P95: 42s             ✓ Healthy              │
│ Cheat Flags: 234/hour            ⚠ Slightly elevated    │
├─────────────────────────────────────────────────────────┤
│ Regional Breakdown:                                     │
│  US-West:  3.2M players (32K matches)                   │
│  EU-West:  4.1M players (41K matches)                   │
│  Asia-SE:  2.5M players (25K matches)                   │
└─────────────────────────────────────────────────────────┘
```

---

## Interview Tips

**Questions to Ask**:
- Expected concurrent player count?
- Target latency requirements?
- Cross-platform support (PC, console, mobile)?
- Regional distribution of players?
- Anti-cheat priority level?
- Budget constraints for infrastructure?

**Topics to Cover**:
- Network protocol (UDP vs TCP trade-offs)
- Client-server architecture (dedicated vs P2P)
- State synchronization strategy
- Lag compensation techniques
- Matchmaking algorithm
- Anti-cheat implementation
- Scalability (regional deployment)

**Common Follow-ups**:
- "How would you handle a DDoS attack on game servers?"
  → Use Cloudflare/AWS Shield, rate limiting, IP filtering, game server anonymization
- "How do you prevent lag switches and network manipulation?"
  → Server-side validation, timeout detection, consistency checks
- "How would you optimize for mobile with limited bandwidth?"
  → Reduce tick rate to 30Hz, aggressive compression, adaptive quality
- "How do you handle server crashes mid-match?"
  → State snapshots every 10s, server migration, reconnection logic

---

## Key Takeaways

1. **Authoritative Server Model**: Server is source of truth - prevents cheating
2. **Client-side Prediction**: Essential for responsive gameplay despite latency
3. **Lag Compensation**: Rewind time for fair hit detection
4. **Regional Deployment**: Low latency requires servers close to players
5. **UDP over TCP**: Gaming requires speed over guaranteed delivery
6. **Interest Management**: Only send relevant data to reduce bandwidth
7. **Anti-Cheat is Critical**: Server-side validation + behavioral analysis
8. **Observability**: Real-time monitoring of latency, tick rate, player experience

---

## Real-World Examples

### Fortnite (Epic Games)
- **100M+ concurrent players** during events
- Uses **Unreal Engine** with custom netcode
- Runs on **AWS** with global edge deployment
- **Easy Anti-Cheat** integration
- Custom **matchmaking** with skill-based and creative modes

### PUBG
- Pioneered **battle royale** at scale (30M+ concurrent)
- Initially struggled with **server performance** (solved with optimization)
- Uses **BattlEye** anti-cheat
- Deployed on **Azure** with regional servers

### Apex Legends (Respawn/EA)
- **Source Engine** modified for BR
- **20Hz tick rate** (lower than competitors - criticized)
- Advanced **lag compensation** for fast-paced combat
- Uses **Easy Anti-Cheat**

---

## Further Reading

- "Networked Physics" by Glenn Fiedler
- "Fast-Paced Multiplayer" series (Gaffer On Games)
- "Overwatch Gameplay Architecture" (GDC Talk)
- "Fortnite Server Architecture" (AWS re:Invent)
- "Deterministic Lockstep Networking" (Age of Empires)
- Gabriel Gambetta's "Client-Server Game Architecture"
