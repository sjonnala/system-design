# Real-time Chat System Architecture

Deep dive into building a scalable real-time chat system for live streaming platforms (Twitch-style).

---

## Table of Contents
1. [Requirements](#requirements)
2. [Architecture Overview](#architecture-overview)
3. [Protocol Design](#protocol-design)
4. [Scalability](#scalability)
5. [Rate Limiting & Moderation](#rate-limiting--moderation)
6. [Emotes & Badges](#emotes--badges)

---

## Requirements

### Functional
- Real-time messaging (<100ms latency)
- Support 100K+ concurrent channels
- 15+ billion messages/day
- Emotes, badges, user roles
- Moderation tools (timeouts, bans, AutoMod)

### Non-Functional
- 99.99% availability
- Horizontal scalability
- Sub-second message delivery globally

---

## Architecture Overview

```
┌──────────┐
│  Client  │ ───WebSocket──→ ┌─────────────┐
└──────────┘                  │   Phoenix   │
                              │   Server    │
┌──────────┐                  │  (Elixir)   │
│  Client  │ ───WebSocket──→ ├─────────────┤
└──────────┘                  │ Redis PubSub│
                              └──────┬──────┘
┌──────────┐                         │
│  Client  │ ───WebSocket──→ ┌───────▼──────┐
└──────────┘                  │  DynamoDB    │
                              │ (Persistence)│
                              └──────────────┘
```

---

## Protocol Design

### IRC over WebSocket

**Connection:**
```
Client: wss://chat.twitch.tv:443
Server: 101 Switching Protocols

Client: CAP REQ :twitch.tv/tags twitch.tv/commands
Server: :tmi.twitch.tv CAP * ACK :twitch.tv/tags twitch.tv/commands

Client: PASS oauth:abc123xyz
Client: NICK username

Client: JOIN #channel
Server: :username!username@username.tmi.twitch.tv JOIN #channel
```

**Send Message:**
```
Client: PRIVMSG #channel :Hello chat!
Server: @badges=subscriber/12;color=#FF0000 :username!username@username.tmi.twitch.tv PRIVMSG #channel :Hello chat!
```

---

## Scalability

### Sharding by Channel

Each channel = separate GenServer process in Elixir.

```elixir
defmodule ChatServer.ChannelRegistry do
  use GenServer

  def start_link(_opts) do
    GenServer.start_link(__MODULE__, %{}, name: __MODULE__)
  end

  def get_or_create_channel(channel_id) do
    case Registry.lookup(ChatServer.Registry, channel_id) do
      [{pid, _}] -> pid
      [] ->
        {:ok, pid} = DynamicSupervisor.start_child(
          ChatServer.ChannelSupervisor,
          {ChatServer.Channel, channel_id}
        )
        pid
    end
  end
end

defmodule ChatServer.Channel do
  use GenServer

  def start_link(channel_id) do
    GenServer.start_link(__MODULE__, %{
      channel_id: channel_id,
      viewers: MapSet.new(),
      message_buffer: :queue.new()
    }, name: via_tuple(channel_id))
  end

  def send_message(channel_id, user_id, text) do
    GenServer.cast(via_tuple(channel_id), {:message, user_id, text})
  end

  def handle_cast({:message, user_id, text}, state) do
    # Broadcast to all viewers
    message = format_message(user_id, text)
    
    Enum.each(state.viewers, fn viewer_pid ->
      send(viewer_pid, {:chat_message, message})
    end)
    
    # Persist to DynamoDB
    ChatServer.Storage.save_message(state.channel_id, message)
    
    {:noreply, state}
  end

  defp via_tuple(channel_id) do
    {:via, Registry, {ChatServer.Registry, channel_id}}
  end
end
```

### Multi-Server Clustering

**Redis Pub/Sub for message fanout:**

```elixir
defmodule ChatServer.Cluster do
  use GenServer

  def init(_) do
    {:ok, redis} = Redix.start_link("redis://localhost:6379")
    {:ok, pubsub} = Redix.PubSub.start_link("redis://localhost:6379")
    
    Redix.PubSub.subscribe(pubsub, "chat:broadcast", self())
    
    {:ok, %{redis: redis, pubsub: pubsub}}
  end

  def broadcast_message(channel_id, message) do
    # Publish to Redis for other servers
    Redix.command(:redis, [
      "PUBLISH",
      "chat:broadcast",
      Jason.encode!(%{channel_id: channel_id, message: message})
    ])
  end

  def handle_info({:redix_pubsub, _from, :message, %{payload: payload}}, state) do
    %{"channel_id" => channel_id, "message" => message} = Jason.decode!(payload)
    
    # Deliver to local channel if it exists
    case Registry.lookup(ChatServer.Registry, channel_id) do
      [{pid, _}] -> send(pid, {:external_message, message})
      [] -> :ok  # Channel not on this server
    end
    
    {:noreply, state}
  end
end
```

---

## Rate Limiting & Moderation

### Sliding Window Rate Limiter

```python
import redis
import time

class RateLimiter:
    def __init__(self, redis_client):
        self.redis = redis_client
        self.limits = {
            'regular': {'messages': 20, 'window': 30},
            'subscriber': {'messages': 20, 'window': 30},
            'moderator': {'messages': 100, 'window': 30},
            'verified_bot': {'messages': 50, 'window': 30}
        }
    
    def check(self, user_id, user_role='regular'):
        limit_config = self.limits[user_role]
        max_messages = limit_config['messages']
        window_seconds = limit_config['window']
        
        key = f'ratelimit:{user_id}'
        now = time.time()
        window_start = now - window_seconds
        
        # Remove old entries
        self.redis.zremrangebyscore(key, '-inf', window_start)
        
        # Count messages in window
        count = self.redis.zcard(key)
        
        if count >= max_messages:
            # Get time until oldest message expires
            oldest = self.redis.zrange(key, 0, 0, withscores=True)
            if oldest:
                retry_after = oldest[0][1] + window_seconds - now
                return {'allowed': False, 'retry_after': retry_after}
        
        # Add current message
        self.redis.zadd(key, {str(now): now})
        self.redis.expire(key, window_seconds)
        
        return {'allowed': True, 'remaining': max_messages - count - 1}
```

### AutoMod (ML-based Content Filter)

```python
from transformers import pipeline

class AutoMod:
    def __init__(self):
        self.toxicity_model = pipeline(
            "text-classification",
            model="unitary/toxic-bert"
        )
        self.banned_phrases = set()  # Load from database
        
    def check_message(self, text, channel_settings):
        # 1. Banned phrases
        for phrase in self.banned_phrases:
            if phrase.lower() in text.lower():
                return {'action': 'block', 'reason': 'Banned phrase'}
        
        # 2. Toxicity detection
        toxicity_score = self.toxicity_model(text)[0]['score']
        sensitivity = channel_settings.get('automod_level', 2)  # 0-4
        threshold = [0.9, 0.8, 0.7, 0.6, 0.5][sensitivity]
        
        if toxicity_score > threshold:
            if sensitivity >= 3:
                return {'action': 'block', 'reason': 'AutoMod flagged'}
            else:
                return {'action': 'hold', 'reason': 'Held for moderator review'}
        
        # 3. Spam detection
        if self.is_spam(text):
            return {'action': 'block', 'reason': 'Spam detected'}
        
        return {'action': 'allow'}
    
    def is_spam(self, text):
        # Detect excessive caps, repetition, links
        caps_ratio = sum(1 for c in text if c.isupper()) / max(len(text), 1)
        if caps_ratio > 0.7 and len(text) > 10:
            return True
        
        # Check for excessive repetition
        words = text.split()
        if len(words) > 5:
            unique_ratio = len(set(words)) / len(words)
            if unique_ratio < 0.3:
                return True
        
        return False
```

---

## Emotes & Badges

### Emote Rendering System

**Database Schema:**
```sql
CREATE TABLE emotes (
    emote_id UUID PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,  -- 'Kappa', 'PogChamp'
    emote_type VARCHAR(20),  -- global, channel, bits
    channel_id BIGINT,  -- NULL for global emotes
    tier_required INT,  -- 1, 2, 3 for subscriber tiers
    image_url VARCHAR(500),
    image_url_2x VARCHAR(500),
    image_url_4x VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_emotes_code ON emotes(code);
CREATE INDEX idx_emotes_channel ON emotes(channel_id);

CREATE TABLE user_emote_access (
    user_id BIGINT,
    emote_id UUID REFERENCES emotes(emote_id),
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, emote_id)
);
```

**Client-side Rendering:**
```javascript
class EmoteParser {
  constructor() {
    this.emoteCache = new Map();
  }

  async parseMessage(text, channelId, userSubscriptions) {
    // Load emotes
    if (!this.emoteCache.has(channelId)) {
      await this.loadEmotes(channelId);
    }
    
    const emotes = this.emoteCache.get(channelId);
    
    // Replace emote codes with images
    const words = text.split(' ');
    const parsed = words.map(word => {
      const emote = emotes.get(word);
      
      if (emote) {
        return `<img src="${emote.url}" 
                     alt="${word}" 
                     class="emote" 
                     srcset="${emote.url_2x} 2x, ${emote.url_4x} 4x"
                     loading="lazy">`;
      }
      return word;
    });
    
    return parsed.join(' ');
  }

  async loadEmotes(channelId) {
    const response = await fetch(`/api/emotes/${channelId}`);
    const data = await response.json();
    
    const emoteMap = new Map();
    data.emotes.forEach(emote => {
      emoteMap.set(emote.code, emote);
    });
    
    this.emoteCache.set(channelId, emoteMap);
  }
}
```

### Badge System

**Badges:**
- Broadcaster
- Moderator
- VIP
- Subscriber (with tier and month count)
- Bits (bronze, silver, gold based on total bits)
- Turbo/Prime

**Rendering:**
```javascript
function renderUserBadges(badges) {
  const badgeHTML = badges.map(badge => {
    switch(badge.type) {
      case 'broadcaster':
        return '<span class="badge broadcaster">📡</span>';
      case 'moderator':
        return '<span class="badge moderator">🔧</span>';
      case 'subscriber':
        return `<span class="badge subscriber">${badge.months}mo</span>`;
      case 'bits':
        return `<span class="badge bits">${badge.tier}</span>`;
      default:
        return '';
    }
  }).join('');
  
  return badgeHTML;
}

// Usage
const message = {
  user: 'username',
  badges: [{type: 'subscriber', months: 24}, {type: 'bits', tier: 'gold'}],
  text: 'Hello chat Kappa'
};

const html = `
  ${renderUserBadges(message.badges)}
  <span class="username">${message.user}</span>:
  <span class="message">${parseEmotes(message.text)}</span>
`;
```

---

**This architecture enables Twitch-scale real-time chat with 15B+ messages/day, <100ms latency, and comprehensive moderation.**
