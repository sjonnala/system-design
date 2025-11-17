# Real-Time Communication Deep Dive: WebSockets, Long Polling, SSE

## Contents

- [Real-Time Communication Deep Dive: WebSockets, Long Polling, SSE](#real-time-communication-deep-dive-websockets-long-polling-sse)
    - [Core Mental Model](#core-mental-model)
    - [HTTP Polling Evolution](#2-http-polling-evolution)
    - [WebSockets Deep Dive](#3-websockets-deep-dive)
    - [Server-Sent Events (SSE) Deep Dive](#4-server-sent-events-sse-deep-dive)
    - [Comparison & Selection Guide](#5-comparison--selection-guide)
    - [Scaling Real-Time Systems](#6-scaling-real-time-systems)
    - [Security Considerations](#7-security-considerations)
    - [SYSTEM DESIGN INTERVIEW FRAMEWORK](#system-design-interview-framework)
        - [Requirements Clarification (RADIO: Requirements)](#1-requirements-clarification-radio-requirements)
        - [Capacity Estimation (RADIO: Scale)](#2-capacity-estimation-radio-scale)
        - [Data Model (RADIO: Data-Model)](#3-data-model-radio-data-model)
        - [High-Level Design (RADIO: Initial Design)](#4-high-level-design-radio-initial-design)
        - [Deep Dives (RADIO: Optimize)](#5-deep-dives-radio-optimize)
    - [MIND MAP: REAL-TIME COMMUNICATION](#mind-map-real-time-communication)

## Core Mental Model

🎓 **PROFESSOR**: The fundamental problem is **server-to-client communication** in a request-response protocol (HTTP).

```text
The Client-Server Communication Problem:
════════════════════════════════════════

Traditional HTTP (Request-Response):
────────────────────────────────────
Client                          Server
  │                               │
  │ ───── Request ──────────────> │
  │                               │ (Process)
  │ <──── Response ────────────── │
  │                               │
  └ Connection closed

Problem: Server CANNOT initiate communication!
• Client must poll for updates
• Inefficient for real-time data
• High latency for notifications

Real-Time Requirements:
───────────────────────
• Stock prices changing every second
• Chat messages arriving anytime
• Live sports scores updating
• Collaborative editing (Google Docs)
• Gaming state synchronization

Need: Server-initiated push!
```

**Evolution of Solutions:**

```text
┌──────────────────────────────────────────────────────────┐
│ 1. Short Polling (Legacy, ~1990s)                       │
│    ─────────────────────────────────────                 │
│    • Client requests every N seconds                     │
│    • Server responds immediately                         │
│    • Simple but wasteful                                 │
│    • High latency (up to N seconds)                      │
│                                                           │
├──────────────────────────────────────────────────────────┤
│ 2. Long Polling (AJAX era, ~2005)                       │
│    ─────────────────────────────────────                 │
│    • Client requests, server WAITS for data              │
│    • Server responds when data available OR timeout      │
│    • Better latency, but still hacky                     │
│    • Connection overhead per message                     │
│                                                           │
├──────────────────────────────────────────────────────────┤
│ 3. Server-Sent Events (HTML5, 2009)                     │
│    ─────────────────────────────────────                 │
│    • Persistent HTTP connection                          │
│    • Server pushes events to client                      │
│    • One-way (server → client only)                      │
│    • Simple, text-based protocol                         │
│                                                           │
├──────────────────────────────────────────────────────────┤
│ 4. WebSockets (HTML5, 2011)                             │
│    ─────────────────────────────────────                 │
│    • Upgrade from HTTP to WebSocket protocol             │
│    • Full-duplex, bidirectional                          │
│    • Persistent connection                               │
│    • Low overhead after handshake                        │
└──────────────────────────────────────────────────────────┘
```

**Visual Comparison:**

```text
Short Polling:
──────────────
Client          Server
  │──Request 1──>│
  │<─Response────│
  │ (wait 5s)    │
  │──Request 2──>│
  │<─Response────│
  │ (wait 5s)    │
  │──Request 3──>│
  │<─Response────│

Overhead: N requests/timeouts
Latency: 0 to 5 seconds

Long Polling:
─────────────
Client          Server
  │──Request 1──>│
  │              │ (wait for data...)
  │              │ (30 seconds later)
  │<─Response────│
  │──Request 2──>│
  │              │ (wait for data...)
  │<─Response────│

Overhead: 1 request per message
Latency: ~instant when data arrives

Server-Sent Events:
───────────────────
Client          Server
  │──Connect────>│
  │<=============│ (persistent)
  │   Event 1    │
  │<─────────────│
  │   Event 2    │
  │<─────────────│
  │   Event 3    │
  │<─────────────│

Overhead: 1 connection (reused)
Latency: ~instant
Direction: Server → Client only

WebSockets:
───────────
Client          Server
  │──Upgrade───>│
  │<─101 Switch─│
  │<============>│ (full-duplex)
  │──Message 1──>│
  │<─Message 2───│
  │──Message 3──>│
  │<─Message 4───│

Overhead: 1 connection (reused)
Latency: ~instant
Direction: Bidirectional
```

---

## 2. **HTTP Polling Evolution**

🎓 **PROFESSOR**: Understanding polling helps appreciate WebSockets/SSE improvements.

### A. Short Polling

```text
Mechanism:
══════════
1. Client sends request every N seconds
2. Server responds immediately (with or without data)
3. Client waits N seconds
4. Repeat

Drawbacks:
──────────
• Wasted requests when no new data (99% of the time)
• High latency (average: N/2 seconds)
• Server load: requests/sec × clients
• Not scalable
```

🏗️ **ARCHITECT**: Short polling implementation:

```java
public class ShortPollingClient {

    private final HttpClient httpClient;
    private final String apiUrl;
    private final int pollIntervalMs;

    /**
     * Short polling loop
     */
    public void startPolling() {
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                // Make HTTP request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/messages"))
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    String data = response.body();
                    if (!data.isEmpty()) {
                        handleNewData(data);
                    }
                }

            } catch (Exception e) {
                log.error("Polling error", e);
            }

        }, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Calculate overhead
     */
    public void calculateOverhead() {
        /**
         * Scenario: 10,000 clients, 5 second polling
         *
         * Requests/second: 10,000 / 5 = 2,000 req/sec
         * Bandwidth: 2,000 × (500 bytes request + 500 bytes response)
         *          = 2 MB/sec = 16 Mbps
         *
         * If only 1% of requests have new data:
         * • Wasted requests: 99%
         * • Wasted bandwidth: 99%
         *
         * Not scalable!
         */
    }
}
```

### B. Long Polling (Comet)

```text
Mechanism:
══════════
1. Client sends request
2. Server HOLDS request open (doesn't respond immediately)
3. When data available OR timeout (30-60s):
   - Server responds with data
4. Client immediately sends new request
5. Repeat

Benefits over Short Polling:
─────────────────────────────
• Lower latency (~instant when data arrives)
• Fewer wasted requests
• More scalable

Drawbacks:
──────────
• Still uses HTTP (request/response overhead)
• Held connections consume server resources
• Proxies may timeout
• Complex to implement correctly
```

```java
public class LongPollingServer {

    /**
     * Async servlet for long polling (Servlet 3.0+)
     */
    @WebServlet(urlPatterns = "/long-poll", asyncSupported = true)
    public class LongPollServlet extends HttpServlet {

        private final MessageQueue messageQueue;
        private final Map<String, AsyncContext> waitingClients = new ConcurrentHashMap<>();

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response)
                throws ServletException, IOException {

            String userId = request.getParameter("userId");
            String lastMessageId = request.getParameter("lastMessageId");

            // Check for new messages immediately
            List<Message> newMessages = messageQueue.getNewMessages(userId, lastMessageId);

            if (!newMessages.isEmpty()) {
                // Data available, respond immediately
                sendMessages(response, newMessages);
            } else {
                // No data, hold request open
                AsyncContext asyncContext = request.startAsync();
                asyncContext.setTimeout(30_000);  // 30 second timeout

                // Add to waiting clients
                waitingClients.put(userId, asyncContext);

                // Timeout handler
                asyncContext.addListener(new AsyncListener() {
                    @Override
                    public void onTimeout(AsyncEvent event) {
                        waitingClients.remove(userId);
                        // Respond with empty (client will reconnect)
                        sendMessages((HttpServletResponse) event.getSuppliedResponse(),
                            Collections.emptyList());
                        asyncContext.complete();
                    }

                    @Override
                    public void onComplete(AsyncEvent event) {
                        waitingClients.remove(userId);
                    }

                    // Other methods omitted...
                });
            }
        }

        /**
         * Push new message to waiting client
         */
        public void notifyClient(String userId, Message message) {
            AsyncContext asyncContext = waitingClients.remove(userId);

            if (asyncContext != null) {
                try {
                    sendMessages(
                        (HttpServletResponse) asyncContext.getResponse(),
                        Collections.singletonList(message)
                    );
                    asyncContext.complete();
                } catch (IOException e) {
                    log.error("Failed to notify client", e);
                }
            }
        }

        private void sendMessages(HttpServletResponse response, List<Message> messages)
                throws IOException {
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");

            String json = new Gson().toJson(messages);
            response.getWriter().write(json);
        }
    }
}

/**
 * Client-side long polling
 */
public class LongPollingClient {

    private volatile boolean running = true;
    private String lastMessageId = "0";

    public void start() {
        new Thread(this::pollLoop).start();
    }

    private void pollLoop() {
        while (running) {
            try {
                // Send request
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl + "/long-poll?userId=" + userId +
                        "&lastMessageId=" + lastMessageId))
                    .timeout(Duration.ofSeconds(35))  // Longer than server timeout
                    .GET()
                    .build();

                HttpResponse<String> response = httpClient.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() == 200) {
                    List<Message> messages = parseMessages(response.body());

                    for (Message message : messages) {
                        handleMessage(message);
                        lastMessageId = message.getId();
                    }
                }

                // Immediately reconnect (no delay)
                // This is key difference from short polling!

            } catch (Exception e) {
                log.error("Long poll error", e);
                // Wait before retry on error
                Thread.sleep(1000);
            }
        }
    }

    /**
     * Overhead calculation
     *
     * Compared to short polling:
     * ──────────────────────────
     * Short polling (5s interval):
     * • 10,000 clients = 2,000 req/sec
     *
     * Long polling:
     * • 10,000 clients, 1 message/min average
     * • Requests/sec: 10,000 / 60 = 167 req/sec
     * • 12x reduction!
     *
     * But:
     * • 10,000 held connections (memory/file descriptors)
     * • More complex server-side
     */
}
```

---

## 3. **WebSockets Deep Dive**

🎓 **PROFESSOR**: WebSockets provide **full-duplex communication over a single TCP connection**.

### A. WebSocket Protocol

```text
WebSocket Handshake (HTTP Upgrade):
════════════════════════════════════

Client Request:
───────────────
GET /chat HTTP/1.1
Host: example.com
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==
Sec-WebSocket-Version: 13

Server Response:
────────────────
HTTP/1.1 101 Switching Protocols
Upgrade: websocket
Connection: Upgrade
Sec-WebSocket-Accept: s3pPLMBiTxaQ9kYGzzhZRbK+xOo=

After handshake: HTTP connection becomes WebSocket connection
Protocol: ws:// or wss:// (secure)
```

**WebSocket Frame Format:**

```text
WebSocket Frame Structure:
═══════════════════════════

 0               1               2               3
 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7 0 1 2 3 4 5 6 7
+-+-+-+-+-------+-+-------------+-------------------------------+
|F|R|R|R| opcode|M| Payload len |    Extended payload length    |
|I|S|S|S|  (4)  |A|     (7)     |             (16/64)           |
|N|V|V|V|       |S|             |   (if payload len==126/127)   |
| |1|2|3|       |K|             |                               |
+-+-+-+-+-------+-+-------------+ - - - - - - - - - - - - - - - +
|     Extended payload length continued, if payload len == 127  |
+ - - - - - - - - - - - - - - - +-------------------------------+
|                               |Masking-key, if MASK set to 1  |
+-------------------------------+-------------------------------+
| Masking-key (continued)       |          Payload Data         |
+-------------------------------- - - - - - - - - - - - - - - - +
:                     Payload Data continued ...                :
+ - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - +
|                     Payload Data continued ...                |
+---------------------------------------------------------------+

Opcodes:
────────
0x0: Continuation frame
0x1: Text frame
0x2: Binary frame
0x8: Close
0x9: Ping
0xA: Pong

Minimal overhead: 2-14 bytes per frame (vs HTTP ~500 bytes)
```

🏗️ **ARCHITECT**: Production WebSocket implementation:

```java
import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;

/**
 * WebSocket server endpoint (Java WebSocket API)
 */
@ServerEndpoint("/chat")
public class ChatWebSocket {

    private static final Set<Session> sessions = Collections.synchronizedSet(new HashSet<>());
    private static final Map<String, Session> userSessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        sessions.add(session);
        log.info("WebSocket opened: {}", session.getId());

        // Extract user from session (authentication handled in HTTP handshake)
        String userId = (String) session.getUserProperties().get("userId");
        if (userId != null) {
            userSessions.put(userId, session);
        }

        // Send welcome message
        sendMessage(session, "Welcome to chat!");

        // Notify others
        broadcast("User " + userId + " joined", session);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        log.info("Message from {}: {}", session.getId(), message);

        // Parse message
        ChatMessage chatMessage = parseMessage(message);

        // Process based on type
        switch (chatMessage.getType()) {
            case "broadcast":
                broadcast(chatMessage.getContent(), session);
                break;

            case "private":
                sendToUser(chatMessage.getRecipient(), chatMessage.getContent());
                break;

            case "typing":
                notifyTyping(session, true);
                break;

            default:
                log.warn("Unknown message type: {}", chatMessage.getType());
        }
    }

    @OnMessage
    public void onBinaryMessage(byte[] data, Session session) {
        // Handle binary data (file uploads, images, etc.)
        log.info("Binary message from {}: {} bytes", session.getId(), data.length);

        // Broadcast binary data to all clients
        for (Session s : sessions) {
            if (s.isOpen() && !s.equals(session)) {
                try {
                    s.getBasicRemote().sendBinary(ByteBuffer.wrap(data));
                } catch (IOException e) {
                    log.error("Failed to send binary data", e);
                }
            }
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessions.remove(session);
        String userId = (String) session.getUserProperties().get("userId");
        if (userId != null) {
            userSessions.remove(userId);
        }

        log.info("WebSocket closed: {} - {}", session.getId(), closeReason);

        // Notify others
        broadcast("User " + userId + " left", null);
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("WebSocket error for session {}", session.getId(), error);

        // Close connection on error
        try {
            session.close(new CloseReason(
                CloseReason.CloseCodes.UNEXPECTED_CONDITION,
                error.getMessage()
            ));
        } catch (IOException e) {
            log.error("Failed to close session", e);
        }
    }

    /**
     * Broadcast message to all connected clients
     */
    private void broadcast(String message, Session exclude) {
        for (Session session : sessions) {
            if (session.isOpen() && !session.equals(exclude)) {
                sendMessage(session, message);
            }
        }
    }

    /**
     * Send message to specific user
     */
    private void sendToUser(String userId, String message) {
        Session session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            sendMessage(session, message);
        }
    }

    /**
     * Send message to session
     */
    private void sendMessage(Session session, String message) {
        try {
            session.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("Failed to send message", e);
        }
    }

    /**
     * Heartbeat to detect dead connections
     */
    @Scheduled(fixedRate = 30000)  // Every 30 seconds
    public void heartbeat() {
        for (Session session : sessions) {
            if (session.isOpen()) {
                try {
                    // Send ping frame
                    session.getBasicRemote().sendPing(ByteBuffer.wrap("ping".getBytes()));
                } catch (IOException e) {
                    log.warn("Heartbeat failed for session {}, closing", session.getId());
                    sessions.remove(session);
                }
            }
        }
    }
}

/**
 * WebSocket configuration
 */
@Configuration
public class WebSocketConfig {

    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    /**
     * Configure handshake interceptor for authentication
     */
    @Bean
    public HandshakeInterceptor handshakeInterceptor() {
        return new HandshakeInterceptor() {
            @Override
            public boolean beforeHandshake(ServerHttpRequest request,
                                          ServerHttpResponse response,
                                          WebSocketHandler wsHandler,
                                          Map<String, Object> attributes) {
                // Extract token from query params or headers
                String token = extractToken(request);

                if (isValidToken(token)) {
                    String userId = getUserIdFromToken(token);
                    attributes.put("userId", userId);
                    return true;
                }

                return false;  // Reject handshake
            }

            @Override
            public void afterHandshake(ServerHttpRequest request,
                                      ServerHttpResponse response,
                                      WebSocketHandler wsHandler,
                                      Exception exception) {
                // Post-handshake logic
            }
        };
    }
}
```

**JavaScript Client:**

```javascript
// WebSocket client (browser)
class ChatClient {
    constructor(url) {
        this.url = url;
        this.ws = null;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
    }

    connect() {
        this.ws = new WebSocket(this.url);

        this.ws.onopen = (event) => {
            console.log('WebSocket connected');
            this.reconnectAttempts = 0;

            // Send authentication if needed
            this.send({
                type: 'auth',
                token: localStorage.getItem('authToken')
            });
        };

        this.ws.onmessage = (event) => {
            const message = JSON.parse(event.data);
            this.handleMessage(message);
        };

        this.ws.onerror = (error) => {
            console.error('WebSocket error:', error);
        };

        this.ws.onclose = (event) => {
            console.log('WebSocket closed:', event.code, event.reason);

            // Reconnect with exponential backoff
            if (this.reconnectAttempts < this.maxReconnectAttempts) {
                const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
                console.log(`Reconnecting in ${delay}ms...`);

                setTimeout(() => {
                    this.reconnectAttempts++;
                    this.connect();
                }, delay);
            } else {
                console.error('Max reconnect attempts reached');
            }
        };
    }

    send(message) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(JSON.stringify(message));
        } else {
            console.error('WebSocket not connected');
        }
    }

    sendBinary(data) {
        if (this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(data);  // ArrayBuffer, Blob, etc.
        }
    }

    handleMessage(message) {
        switch (message.type) {
            case 'chat':
                this.displayMessage(message.content);
                break;
            case 'typing':
                this.showTypingIndicator(message.user);
                break;
            case 'user_joined':
                this.displayNotification(`${message.user} joined`);
                break;
            default:
                console.log('Unknown message type:', message.type);
        }
    }

    close() {
        if (this.ws) {
            this.ws.close(1000, 'Client closing');  // Normal closure
        }
    }
}

// Usage
const chat = new ChatClient('wss://example.com/chat');
chat.connect();

// Send message
document.getElementById('send').onclick = () => {
    const text = document.getElementById('message').value;
    chat.send({
        type: 'chat',
        content: text
    });
};
```

**Python WebSocket Server:**

```python
import asyncio
import websockets
import json

class ChatServer:
    def __init__(self):
        self.clients = set()
        self.user_sessions = {}

    async def register(self, websocket):
        """Register new client"""
        self.clients.add(websocket)
        print(f"Client connected: {websocket.remote_address}")

    async def unregister(self, websocket):
        """Unregister client"""
        self.clients.remove(websocket)
        # Remove from user sessions
        user_id = None
        for uid, ws in self.user_sessions.items():
            if ws == websocket:
                user_id = uid
                break
        if user_id:
            del self.user_sessions[user_id]
        print(f"Client disconnected: {websocket.remote_address}")

    async def broadcast(self, message, exclude=None):
        """Broadcast message to all clients"""
        if self.clients:
            # Send to all clients except exclude
            tasks = [
                client.send(message)
                for client in self.clients
                if client != exclude and client.open
            ]
            await asyncio.gather(*tasks, return_exceptions=True)

    async def send_to_user(self, user_id, message):
        """Send message to specific user"""
        websocket = self.user_sessions.get(user_id)
        if websocket and websocket.open:
            await websocket.send(message)

    async def handle_client(self, websocket, path):
        """Handle individual client connection"""
        await self.register(websocket)

        try:
            async for message in websocket:
                # Parse message
                data = json.loads(message)

                # Handle based on type
                if data['type'] == 'auth':
                    # Authenticate and store user
                    user_id = self.authenticate(data['token'])
                    if user_id:
                        self.user_sessions[user_id] = websocket
                        await websocket.send(json.dumps({
                            'type': 'auth_success',
                            'user_id': user_id
                        }))

                elif data['type'] == 'chat':
                    # Broadcast chat message
                    await self.broadcast(json.dumps({
                        'type': 'chat',
                        'user': data.get('user'),
                        'content': data['content']
                    }), exclude=websocket)

                elif data['type'] == 'private':
                    # Private message
                    await self.send_to_user(data['recipient'], json.dumps({
                        'type': 'private',
                        'from': data.get('user'),
                        'content': data['content']
                    }))

        except websockets.exceptions.ConnectionClosed:
            print(f"Connection closed: {websocket.remote_address}")
        finally:
            await self.unregister(websocket)

    def authenticate(self, token):
        """Validate token and return user_id"""
        # Implement token validation
        # Return user_id if valid, None otherwise
        pass

# Run server
async def main():
    server = ChatServer()
    async with websockets.serve(server.handle_client, "localhost", 8765):
        print("WebSocket server started on ws://localhost:8765")
        await asyncio.Future()  # Run forever

asyncio.run(main())
```

---

## 4. **Server-Sent Events (SSE) Deep Dive**

🎓 **PROFESSOR**: SSE provides **one-way streaming from server to client** over HTTP.

### A. SSE Protocol

```text
SSE vs WebSocket:
═════════════════

SSE:
• Uses regular HTTP (not upgrade)
• One-way: Server → Client only
• Text-based (UTF-8)
• Automatic reconnection
• Event IDs for resumption
• Simpler than WebSocket

WebSocket:
• Requires protocol upgrade
• Bidirectional
• Binary or text
• Manual reconnection
• More complex but more powerful
```

**SSE Message Format:**

```text
SSE Stream Format:
══════════════════

HTTP/1.1 200 OK
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive

event: message
id: 1
data: Hello, world!

event: message
id: 2
data: This is a multi-line
data: message that spans
data: multiple lines

event: custom
data: {"type":"notification","content":"New message"}

: This is a comment (ignored by client)

Each message ends with double newline (\n\n)
```

🏗️ **ARCHITECT**: SSE implementation:

```java
/**
 * Server-Sent Events endpoint (Spring)
 */
@RestController
public class SSEController {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    /**
     * SSE endpoint
     */
    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamEvents(@RequestParam String userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);  // No timeout

        // Store emitter
        emitters.put(userId, emitter);

        // Handle completion/timeout/error
        emitter.onCompletion(() -> {
            emitters.remove(userId);
            log.info("SSE completed for user: {}", userId);
        });

        emitter.onTimeout(() -> {
            emitters.remove(userId);
            log.info("SSE timeout for user: {}", userId);
        });

        emitter.onError((ex) -> {
            emitters.remove(userId);
            log.error("SSE error for user: {}", userId, ex);
        });

        // Send initial event
        try {
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connection established")
            );
        } catch (IOException e) {
            log.error("Failed to send initial event", e);
            emitter.completeWithError(e);
        }

        return emitter;
    }

    /**
     * Send event to specific user
     */
    public void sendToUser(String userId, String eventName, Object data) {
        SseEmitter emitter = emitters.get(userId);

        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
                    .id(String.valueOf(System.currentTimeMillis()))
                );
            } catch (IOException e) {
                log.error("Failed to send event to user: {}", userId, e);
                emitters.remove(userId);
                emitter.completeWithError(e);
            }
        }
    }

    /**
     * Broadcast to all connected clients
     */
    public void broadcast(String eventName, Object data) {
        List<String> deadEmitters = new ArrayList<>();

        for (Map.Entry<String, SseEmitter> entry : emitters.entrySet()) {
            try {
                entry.getValue().send(SseEmitter.event()
                    .name(eventName)
                    .data(data)
                );
            } catch (IOException e) {
                log.error("Failed to send to user: {}", entry.getKey(), e);
                deadEmitters.add(entry.getKey());
            }
        }

        // Remove dead emitters
        deadEmitters.forEach(emitters::remove);
    }

    /**
     * Example: Stock price updates
     */
    @Scheduled(fixedRate = 1000)  // Every second
    public void publishStockPrices() {
        StockPrice price = stockService.getCurrentPrice("AAPL");

        broadcast("stock-update", price);
    }

    /**
     * Example: Notification system
     */
    public void sendNotification(String userId, Notification notification) {
        sendToUser(userId, "notification", notification);
    }
}
```

**JavaScript Client (Native):**

```javascript
// SSE client (browser - native support!)
class SSEClient {
    constructor(url) {
        this.url = url;
        this.eventSource = null;
        this.reconnectAttempts = 0;
    }

    connect() {
        this.eventSource = new EventSource(this.url);

        // Built-in events
        this.eventSource.onopen = (event) => {
            console.log('SSE connected');
            this.reconnectAttempts = 0;
        };

        this.eventSource.onerror = (error) => {
            console.error('SSE error:', error);

            if (this.eventSource.readyState === EventSource.CLOSED) {
                console.log('SSE connection closed, will auto-reconnect');
                // EventSource automatically reconnects!
            }
        };

        // Default message handler
        this.eventSource.onmessage = (event) => {
            console.log('Message:', event.data);
            this.handleMessage(JSON.parse(event.data));
        };

        // Custom event handlers
        this.eventSource.addEventListener('notification', (event) => {
            const notification = JSON.parse(event.data);
            this.showNotification(notification);
        });

        this.eventSource.addEventListener('stock-update', (event) => {
            const stockPrice = JSON.parse(event.data);
            this.updateStockPrice(stockPrice);
        });

        this.eventSource.addEventListener('connected', (event) => {
            console.log('Initial connection:', event.data);
        });
    }

    close() {
        if (this.eventSource) {
            this.eventSource.close();
        }
    }

    handleMessage(data) {
        // Handle generic messages
        console.log('Received:', data);
    }

    showNotification(notification) {
        // Display notification to user
        if (Notification.permission === 'granted') {
            new Notification(notification.title, {
                body: notification.message
            });
        }
    }

    updateStockPrice(stockPrice) {
        // Update UI with stock price
        document.getElementById(`stock-${stockPrice.symbol}`).textContent =
            `$${stockPrice.price}`;
    }
}

// Usage
const sse = new SSEClient('/sse?userId=12345');
sse.connect();

// SSE automatically reconnects on disconnect!
// Supports Last-Event-ID header for resumption
```

**Python SSE Server:**

```python
from flask import Flask, Response, stream_with_context
import json
import time
from queue import Queue
from threading import Thread

app = Flask(__name__)

class SSEServer:
    def __init__(self):
        self.clients = {}  # user_id -> queue

    def register_client(self, user_id):
        """Register new SSE client"""
        queue = Queue()
        self.clients[user_id] = queue
        return queue

    def unregister_client(self, user_id):
        """Unregister SSE client"""
        if user_id in self.clients:
            del self.clients[user_id]

    def send_to_user(self, user_id, event_name, data):
        """Send event to specific user"""
        if user_id in self.clients:
            self.clients[user_id].put({
                'event': event_name,
                'data': json.dumps(data)
            })

    def broadcast(self, event_name, data):
        """Broadcast to all clients"""
        for user_id in list(self.clients.keys()):
            self.send_to_user(user_id, event_name, data)

sse_server = SSEServer()

@app.route('/sse')
def sse_stream():
    """SSE endpoint"""
    user_id = request.args.get('userId')

    def event_stream():
        queue = sse_server.register_client(user_id)

        try:
            # Send initial connection event
            yield f"event: connected\ndata: Connection established\n\n"

            # Stream events from queue
            while True:
                event = queue.get()  # Blocks until event available

                # Format SSE message
                yield f"event: {event['event']}\n"
                yield f"data: {event['data']}\n\n"

        except GeneratorExit:
            # Client disconnected
            sse_server.unregister_client(user_id)

    return Response(
        stream_with_context(event_stream()),
        mimetype='text/event-stream',
        headers={
            'Cache-Control': 'no-cache',
            'X-Accel-Buffering': 'no'  # Disable nginx buffering
        }
    )

# Example: Push notifications
@app.route('/notify/<user_id>', methods=['POST'])
def send_notification(user_id):
    """Send notification to user"""
    notification = request.json

    sse_server.send_to_user(user_id, 'notification', notification)

    return {'status': 'sent'}

# Background task: Stock price updates
def stock_price_updater():
    """Simulated stock price updates"""
    while True:
        price = get_current_stock_price('AAPL')
        sse_server.broadcast('stock-update', {
            'symbol': 'AAPL',
            'price': price,
            'timestamp': time.time()
        })
        time.sleep(1)

# Start background thread
Thread(target=stock_price_updater, daemon=True).start()

if __name__ == '__main__':
    app.run(threaded=True)
```

---

## 5. **Comparison & Selection Guide**

🏗️ **ARCHITECT**: Choose based on your requirements:

```text
┌──────────────────────────────────────────────────────────────┐
│                    DECISION MATRIX                           │
├──────────────────────────────────────────────────────────────┤
│                                                              │
│ Use Short Polling when:                                     │
│ ───────────────────────                                     │
│ ✓ Updates infrequent (> 1 minute)                          │
│ ✓ Simple implementation needed                              │
│ ✓ Firewall/proxy issues with long connections              │
│ ✗ Not recommended for real-time                             │
│                                                              │
│ Use Long Polling when:                                      │
│ ──────────────────                                          │
│ ✓ Need better latency than short polling                    │
│ ✓ WebSocket/SSE not available                               │
│ ✓ Firewalls block WebSocket                                 │
│ ✗ Complexity not justified for new projects                 │
│                                                              │
│ Use Server-Sent Events when:                                │
│ ────────────────────────────                                │
│ ✓ One-way server → client streaming                         │
│ ✓ Automatic reconnection needed                             │
│ ✓ Text-based data (JSON, XML)                               │
│ ✓ Simple implementation                                     │
│ ✓ Stock tickers, notifications, news feeds                  │
│ ✓ HTTP/2 available (multiplexing)                           │
│                                                              │
│ Use WebSockets when:                                        │
│ ─────────────────                                           │
│ ✓ Bidirectional communication                               │
│ ✓ Low latency critical (gaming, trading)                    │
│ ✓ High frequency updates (100+ msg/sec)                     │
│ ✓ Binary data (images, video, audio)                        │
│ ✓ Chat applications                                         │
│ ✓ Collaborative editing                                     │
│ ✓ Real-time gaming                                          │
└──────────────────────────────────────────────────────────────┘
```

**Feature Comparison:**

```text
┌──────────────────┬─────────┬──────────┬─────────┬──────────┐
│ Feature          │ Short   │ Long     │ SSE     │WebSocket │
│                  │ Polling │ Polling  │         │          │
├──────────────────┼─────────┼──────────┼─────────┼──────────┤
│ Latency          │ High    │ Low      │ Low     │ Very Low │
│ Bidirectional    │ No      │ No       │ No      │ Yes      │
│ Browser Support  │ 100%    │ 100%     │ 97%     │ 98%      │
│ Firewall/Proxy   │ Good    │ Medium   │ Good    │ Poor     │
│ Reconnection     │ Manual  │ Manual   │ Auto    │ Manual   │
│ Binary Data      │ Yes     │ Yes      │ No      │ Yes      │
│ HTTP/2 Multiplex │ No      │ No       │ Yes     │ No*      │
│ Overhead         │ Very    │ Medium   │ Low     │ Very Low │
│                  │ High    │          │         │          │
│ Complexity       │ Low     │ Medium   │ Low     │ High     │
│ Event IDs        │ No      │ No       │ Yes     │ Manual   │
│ CORS             │ Yes     │ Yes      │ Yes     │ Custom   │
└──────────────────┴─────────┴──────────┴─────────┴──────────┘

* WebSocket over HTTP/2 is still in development
```

**Real-World Usage Examples:**

```java
public class ProtocolSelection {

    /**
     * Example 1: Stock ticker
     */
    @UseCase("Stock Ticker")
    public Protocol stockTicker() {
        /**
         * Recommendation: Server-Sent Events
         *
         * Reasons:
         * - One-way streaming (server → client)
         * - Text data (JSON prices)
         * - Automatic reconnection
         * - Simple implementation
         * - HTTP/2 multiplexing (multiple stocks, one connection)
         *
         * Companies: Yahoo Finance, Bloomberg
         */
        return Protocol.SSE;
    }

    /**
     * Example 2: Chat application
     */
    @UseCase("Chat Application")
    public Protocol chatApp() {
        /**
         * Recommendation: WebSockets
         *
         * Reasons:
         * - Bidirectional (send and receive messages)
         * - Low latency (instant delivery)
         * - High frequency (typing indicators, presence)
         * - Binary support (images, files)
         *
         * Companies: Slack, Discord, WhatsApp Web
         */
        return Protocol.WEBSOCKET;
    }

    /**
     * Example 3: Live sports scores
     */
    @UseCase("Live Sports Scores")
    public Protocol liveScores() {
        /**
         * Recommendation: Server-Sent Events or Long Polling
         *
         * SSE if:
         * - Modern browsers
         * - Frequent updates (every few seconds)
         *
         * Long Polling if:
         * - Legacy browser support
         * - Updates less frequent (every 10-30s)
         *
         * Companies: ESPN (SSE), older sites (Long Polling)
         */
        return Protocol.SSE;
    }

    /**
     * Example 4: Multiplayer game
     */
    @UseCase("Multiplayer Game")
    public Protocol multiplayerGame() {
        /**
         * Recommendation: WebSockets (or UDP with WebRTC)
         *
         * Reasons:
         * - Bidirectional (player actions, game state)
         * - Very low latency (<50ms)
         * - High frequency (60+ updates/sec)
         * - Binary protocol (efficient)
         *
         * Companies: Agar.io, Slither.io
         *
         * Note: For competitive FPS, consider WebRTC with UDP
         */
        return Protocol.WEBSOCKET;
    }

    /**
     * Example 5: Notification system
     */
    @UseCase("Notifications")
    public Protocol notifications() {
        /**
         * Recommendation: Server-Sent Events
         *
         * Reasons:
         * - One-way (server → client)
         * - Infrequent (minutes between notifications)
         * - Automatic reconnection
         * - Simple implementation
         *
         * Fallback: Long polling for older browsers
         *
         * Companies: Facebook, Twitter (both use SSE/LP)
         */
        return Protocol.SSE;
    }

    /**
     * Example 6: Collaborative editing
     */
    @UseCase("Collaborative Editing")
    public Protocol collaborativeEditing() {
        /**
         * Recommendation: WebSockets
         *
         * Reasons:
         * - Bidirectional (edits from all users)
         * - Low latency (see changes instantly)
         * - High frequency (keystrokes)
         * - Operational transforms require ordering
         *
         * Companies: Google Docs, Figma, CodeSandbox
         */
        return Protocol.WEBSOCKET;
    }

    /**
     * Example 7: Admin dashboard
     */
    @UseCase("Admin Dashboard")
    public Protocol adminDashboard() {
        /**
         * Recommendation: Server-Sent Events
         *
         * Reasons:
         * - One-way (server → dashboard)
         * - Metrics, logs, alerts
         * - Multiple data streams (HTTP/2 multiplexing)
         * - Automatic reconnection
         *
         * Companies: Datadog, Grafana
         */
        return Protocol.SSE;
    }

    /**
     * Example 8: IoT device monitoring
     */
    @UseCase("IoT Monitoring")
    public Protocol iotMonitoring() {
        /**
         * Recommendation: Mixed
         *
         * Device → Server: MQTT over WebSocket or HTTP/2
         * Server → Dashboard: Server-Sent Events
         *
         * Reasons:
         * - Devices: Need bidirectional, lightweight
         * - Dashboard: One-way streaming sufficient
         *
         * Companies: AWS IoT, Azure IoT Hub
         */
        return Protocol.MIXED;
    }
}
```

---

## 6. **Scaling Real-Time Systems**

🏗️ **ARCHITECT**: Scaling WebSockets/SSE to millions of connections.

### A. Connection Management

```text
The C10K Problem (and beyond):
═══════════════════════════════

Challenge: Handle 10,000+ concurrent connections
Modern: C10M (10 million connections)

Traditional Thread-per-Connection:
───────────────────────────────────
• 10,000 connections = 10,000 threads
• Each thread: 1 MB stack = 10 GB RAM
• Context switching overhead
• Not scalable!

Solution: Event-Driven I/O
──────────────────────────
• Single thread handles many connections
• Non-blocking I/O (epoll, kqueue, IOCP)
• Event loop (Node.js, Netty, Tornado)
• Low memory per connection
```

**Node.js WebSocket Server (Highly Scalable):**

```javascript
const WebSocket = require('ws');
const Redis = require('ioredis');

class ScalableWebSocketServer {
    constructor() {
        this.wss = new WebSocket.Server({ port: 8080 });
        this.redis = new Redis();
        this.subscriber = new Redis();

        this.clients = new Map();  // connectionId -> ws
        this.userConnections = new Map();  // userId -> Set<connectionId>

        this.setupRedisSubscriber();
        this.setupWebSocketServer();
    }

    setupWebSocketServer() {
        this.wss.on('connection', (ws, req) => {
            const connectionId = this.generateConnectionId();
            this.clients.set(connectionId, ws);

            console.log(`Client connected: ${connectionId}`);
            console.log(`Total connections: ${this.clients.size}`);

            ws.on('message', (message) => {
                this.handleMessage(connectionId, message);
            });

            ws.on('close', () => {
                this.handleDisconnect(connectionId);
            });

            ws.on('error', (error) => {
                console.error(`WebSocket error: ${connectionId}`, error);
            });

            // Heartbeat
            ws.isAlive = true;
            ws.on('pong', () => {
                ws.isAlive = true;
            });
        });

        // Heartbeat interval
        setInterval(() => {
            this.wss.clients.forEach((ws) => {
                if (ws.isAlive === false) {
                    return ws.terminate();
                }

                ws.isAlive = false;
                ws.ping();
            });
        }, 30000);
    }

    setupRedisSubscriber() {
        /**
         * Use Redis Pub/Sub for multi-server scaling
         * Messages published to Redis are broadcast to all servers
         */
        this.subscriber.subscribe('websocket:broadcast');
        this.subscriber.subscribe('websocket:user');

        this.subscriber.on('message', (channel, message) => {
            const data = JSON.parse(message);

            if (channel === 'websocket:broadcast') {
                this.broadcastLocal(data);
            } else if (channel === 'websocket:user') {
                this.sendToUserLocal(data.userId, data.message);
            }
        });
    }

    handleMessage(connectionId, message) {
        const data = JSON.parse(message);

        switch (data.type) {
            case 'auth':
                this.authenticateConnection(connectionId, data.token);
                break;

            case 'subscribe':
                this.subscribeToChannel(connectionId, data.channel);
                break;

            case 'message':
                this.publishMessage(data);
                break;

            default:
                console.log('Unknown message type:', data.type);
        }
    }

    authenticateConnection(connectionId, token) {
        // Validate token and get userId
        const userId = this.validateToken(token);

        if (userId) {
            // Store user-connection mapping
            if (!this.userConnections.has(userId)) {
                this.userConnections.set(userId, new Set());
            }
            this.userConnections.get(userId).add(connectionId);

            // Store userId in connection
            const ws = this.clients.get(connectionId);
            ws.userId = userId;

            // Send auth success
            ws.send(JSON.stringify({
                type: 'auth_success',
                userId: userId
            }));
        }
    }

    publishMessage(data) {
        /**
         * Publish to Redis - will be received by all servers
         * This enables horizontal scaling
         */
        this.redis.publish('websocket:broadcast', JSON.stringify(data));
    }

    broadcastLocal(data) {
        /**
         * Broadcast to all connections on THIS server
         */
        this.clients.forEach((ws) => {
            if (ws.readyState === WebSocket.OPEN) {
                ws.send(JSON.stringify(data));
            }
        });
    }

    sendToUser(userId, message) {
        /**
         * Send to specific user across ALL servers
         */
        this.redis.publish('websocket:user', JSON.stringify({
            userId: userId,
            message: message
        }));
    }

    sendToUserLocal(userId, message) {
        /**
         * Send to user connections on THIS server
         */
        const connectionIds = this.userConnections.get(userId);
        if (connectionIds) {
            connectionIds.forEach((connectionId) => {
                const ws = this.clients.get(connectionId);
                if (ws && ws.readyState === WebSocket.OPEN) {
                    ws.send(JSON.stringify(message));
                }
            });
        }
    }

    handleDisconnect(connectionId) {
        const ws = this.clients.get(connectionId);
        const userId = ws?.userId;

        // Remove from clients
        this.clients.delete(connectionId);

        // Remove from user connections
        if (userId) {
            const connections = this.userConnections.get(userId);
            if (connections) {
                connections.delete(connectionId);
                if (connections.size === 0) {
                    this.userConnections.delete(userId);
                }
            }
        }

        console.log(`Client disconnected: ${connectionId}`);
        console.log(`Total connections: ${this.clients.size}`);
    }

    generateConnectionId() {
        return `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
    }

    validateToken(token) {
        // Implement token validation
        // Return userId if valid, null otherwise
        return 'user123';  // Simplified
    }
}

// Start server
const server = new ScalableWebSocketServer();

/**
 * Scaling strategy:
 * ─────────────────
 * Single server: 100K connections (Node.js event loop)
 * Multi-server: 10M+ connections (Redis Pub/Sub coordination)
 *
 * Architecture:
 * ────────────
 *        Load Balancer (sticky sessions)
 *              │
 *    ┌─────────┼─────────┬─────────┐
 *    ↓         ↓         ↓         ↓
 * [WS Srv] [WS Srv] [WS Srv] [WS Srv]
 *    │         │         │         │
 *    └─────────┴─────────┴─────────┘
 *              ↓
 *         Redis Pub/Sub
 */
```

### B. Load Balancing

```text
WebSocket Load Balancing Challenges:
═════════════════════════════════════

Problem: WebSockets are STATEFUL
• Connection must stay on same server
• Standard round-robin doesn't work

Solutions:
──────────

1. Sticky Sessions (IP-based or Cookie-based)
   ───────────────────────────────────────────
   • Same client → same server
   • Simple but limits scalability
   • Server failure = all clients reconnect

2. Consistent Hashing
   ───────────────────
   • Hash userId → server
   • Better than sticky sessions
   • Minimal disruption on server add/remove

3. Redis Pub/Sub (Recommended)
   ────────────────────────────
   • Any server can handle any connection
   • Servers communicate via Redis
   • Fully scalable
   • Server failure = clients reconnect to any server

4. Service Mesh (Kubernetes)
   ─────────────────────────
   • Istio/Linkerd handle routing
   • Health checks & automatic failover
   • mTLS for security
```

**Nginx Configuration for WebSocket:**

```nginx
# Nginx as WebSocket reverse proxy
upstream websocket_backend {
    # IP hash for sticky sessions
    ip_hash;

    server ws1.example.com:8080;
    server ws2.example.com:8080;
    server ws3.example.com:8080;
}

server {
    listen 443 ssl http2;
    server_name ws.example.com;

    ssl_certificate /path/to/cert.pem;
    ssl_certificate_key /path/to/key.pem;

    location /ws {
        # WebSocket proxying
        proxy_pass http://websocket_backend;

        # Required for WebSocket
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";

        # Pass headers
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

        # Timeouts (important!)
        proxy_connect_timeout 7d;
        proxy_send_timeout 7d;
        proxy_read_timeout 7d;
    }
}
```

---

## 7. **Security Considerations**

🎓 **PROFESSOR**: Real-time connections have **unique security challenges**.

```text
┌──────────────────────────────────────────────────────┐
│ Security Threats:                                    │
│ ─────────────────────────────────────                │
│                                                       │
│ 1. Authentication/Authorization                      │
│    • Who can connect?                                │
│    • What channels can they access?                  │
│                                                       │
│ 2. Denial of Service (DoS)                          │
│    • Connection exhaustion                           │
│    • Message flooding                                │
│                                                       │
│ 3. Message Injection                                 │
│    • XSS via broadcast messages                      │
│    • Code injection                                  │
│                                                       │
│ 4. Man-in-the-Middle (MITM)                         │
│    • Unencrypted connections                         │
│    • SSL stripping                                   │
│                                                       │
│ 5. Cross-Site WebSocket Hijacking (CSWSH)           │
│    • Similar to CSRF                                 │
│    • Validate Origin header                          │
└──────────────────────────────────────────────────────┘
```

**Security Best Practices:**

```java
public class WebSocketSecurity {

    /**
     * 1. Authentication during handshake
     */
    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                  ServerHttpResponse response,
                                  WebSocketHandler wsHandler,
                                  Map<String, Object> attributes) {
        // Extract token from query param or header
        String token = extractToken(request);

        if (!isValidToken(token)) {
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;  // Reject handshake
        }

        // Store user info for use in WebSocket handler
        String userId = getUserIdFromToken(token);
        attributes.put("userId", userId);
        attributes.put("permissions", getPermissions(userId));

        return true;
    }

    /**
     * 2. Validate Origin header (prevent CSWSH)
     */
    private boolean isValidOrigin(ServerHttpRequest request) {
        String origin = request.getHeaders().getFirst("Origin");

        if (origin == null) {
            return false;  // Reject if no Origin header
        }

        // Whitelist of allowed origins
        List<String> allowedOrigins = Arrays.asList(
            "https://example.com",
            "https://app.example.com"
        );

        return allowedOrigins.contains(origin);
    }

    /**
     * 3. Rate limiting
     */
    @Component
    public class WebSocketRateLimiter {

        private final Map<String, RateLimiter> limiters = new ConcurrentHashMap<>();

        public boolean allowMessage(String userId) {
            RateLimiter limiter = limiters.computeIfAbsent(
                userId,
                k -> RateLimiter.create(100.0)  // 100 messages/sec max
            );

            return limiter.tryAcquire();
        }
    }

    /**
     * 4. Input validation and sanitization
     */
    @OnMessage
    public void onMessage(String message, Session session) {
        // Validate message size
        if (message.length() > MAX_MESSAGE_SIZE) {
            log.warn("Message too large from session: {}", session.getId());
            return;
        }

        // Parse and validate JSON
        ChatMessage chatMessage;
        try {
            chatMessage = parseAndValidate(message);
        } catch (ValidationException e) {
            log.warn("Invalid message from session: {}", session.getId(), e);
            return;
        }

        // Sanitize content (prevent XSS)
        String sanitized = sanitizeHtml(chatMessage.getContent());
        chatMessage.setContent(sanitized);

        // Check rate limit
        String userId = (String) session.getUserProperties().get("userId");
        if (!rateLimiter.allowMessage(userId)) {
            log.warn("Rate limit exceeded for user: {}", userId);
            return;
        }

        // Process message
        handleMessage(chatMessage, session);
    }

    /**
     * 5. Use WSS (WebSocket Secure) in production
     */
    public void enforceWSS() {
        /**
         * Always use wss:// (WebSocket over TLS)
         * Never use ws:// in production
         *
         * Benefits:
         * - Encrypted communication
         * - Prevents MITM attacks
         * - Required for HTTPS sites (mixed content)
         */
    }

    /**
     * 6. Connection limits per user
     */
    private final Map<String, Integer> userConnectionCounts = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        String userId = (String) session.getUserProperties().get("userId");

        // Limit connections per user (prevent abuse)
        int count = userConnectionCounts.merge(userId, 1, Integer::sum);

        if (count > MAX_CONNECTIONS_PER_USER) {
            try {
                session.close(new CloseReason(
                    CloseReason.CloseCodes.TRY_AGAIN_LATER,
                    "Too many connections"
                ));
            } catch (IOException e) {
                log.error("Failed to close session", e);
            }
            return;
        }

        // Continue with normal connection setup
    }

    /**
     * 7. Message encryption (sensitive data)
     */
    public void encryptSensitiveMessages() {
        /**
         * Even with WSS, consider end-to-end encryption for
         * highly sensitive data:
         *
         * 1. Client encrypts with server's public key
         * 2. Server decrypts with private key
         * 3. Prevents compromise at server level
         *
         * Use cases: Banking, healthcare, legal
         */
    }
}
```

---

## 🎯 **SYSTEM DESIGN INTERVIEW FRAMEWORK**

### 1. Requirements Clarification (RADIO: Requirements)

```text
Functional:
- Real-time data updates
- Bidirectional or one-way?
- Message types (text, binary, media)?
- User authentication needed?
- Channel/room support?

Non-Functional:
- Concurrent users: 1K, 100K, 10M?
- Message rate: 1/min, 1/sec, 100/sec?
- Latency requirement: <100ms, <1s?
- Reliability: Can afford message loss?
- Scale: Single region or global?

Questions to Ask:
─────────────────
• Is communication bidirectional?
• What's the expected message frequency?
• Do we need message history?
• What's the max acceptable latency?
• Browser compatibility requirements?
• Mobile app support needed?
```

### 2. Capacity Estimation (RADIO: Scale)

```text
Example: Real-time chat application

Users:
──────
• Total users: 10 million
• Concurrent users: 1 million (10% online)
• Active chatters: 100K (10% of online)

Messages:
─────────
• Messages/user/hour: 10
• Total messages/hour: 100K × 10 = 1M
• Messages/second: 1M / 3600 ≈ 280 msg/sec

Connections:
────────────
• WebSocket connections: 1M concurrent
• Memory per connection: 10 KB (buffers, state)
• Total memory: 1M × 10 KB = 10 GB

Bandwidth (per connection):
────────────────────────────
• Average message size: 500 bytes
• Receive rate: 280 msg/sec ÷ 1M users = 0.28 msg/sec/user
• Bandwidth/user: 0.28 × 500 bytes = 140 bytes/sec
• Total bandwidth: 140 bytes/sec × 1M = 140 MB/sec = 1.1 Gbps

Servers needed:
───────────────
• Connections/server: 100K (Node.js can handle)
• Servers: 1M / 100K = 10 servers
• With 2x redundancy: 20 servers
```

### 3. Data Model (RADIO: Data Model)

```java
/**
 * Domain model for real-time messaging
 */

@Entity
public class Connection {
    private String connectionId;
    private String userId;
    private String serverId;
    private Instant connectedAt;
    private ConnectionStatus status;
    private Set<String> subscribedChannels;
}

@Entity
public class Message {
    private String messageId;
    private String channelId;
    private String senderId;
    private String content;
    private MessageType type;  // TEXT, IMAGE, FILE
    private Instant timestamp;
    private Map<String, String> metadata;
}

@Entity
public class Channel {
    private String channelId;
    private String name;
    private ChannelType type;  // PUBLIC, PRIVATE, DIRECT
    private Set<String> members;
    private Instant createdAt;
}

@Entity
public class Presence {
    private String userId;
    private PresenceStatus status;  // ONLINE, AWAY, OFFLINE
    private Instant lastSeen;
    private String currentActivity;
}
```

### 4. High-Level Design (RADIO: Initial Design)

```text
┌──────────────────────────────────────────────────────┐
│          REAL-TIME MESSAGING SYSTEM                  │
└──────────────────────────────────────────────────────┘

┌────────────┐
│  Clients   │ (Web, Mobile)
└──────┬─────┘
       │ WSS://
       ↓
┌──────────────────────────────────────────┐
│      Load Balancer (Sticky Sessions)     │
└──────────────┬───────────────────────────┘
               │
    ┌──────────┼──────────┬────────────┐
    ↓          ↓          ↓            ↓
┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐
│WebSocket│ │WebSocket│ │WebSocket│ │WebSocket│
│ Server  │ │ Server  │ │ Server  │ │ Server  │
│ (Node)  │ │ (Node)  │ │ (Node)  │ │ (Node)  │
└────┬────┘ └────┬────┘ └────┬────┘ └────┬────┘
     │           │           │           │
     └───────────┴───────────┴───────────┘
                 ↓
       ┌─────────────────┐
       │  Redis Pub/Sub  │ (Message coordination)
       └────────┬────────┘
                │
     ┌──────────┼──────────┐
     ↓          ↓          ↓
┌─────────┐ ┌─────────┐ ┌─────────┐
│ Message │ │ Presence│ │  User   │
│   DB    │ │ Service │ │ Service │
│(Cassandra)│ │ (Redis)│ │  (PG)  │
└─────────┘ └─────────┘ └─────────┘
```

### 5. Deep Dives (RADIO: Optimize)

**A. Message Delivery Guarantees**

```java
public class MessageDeliveryGuarantees {

    /**
     * At-most-once (fire and forget)
     */
    public void atMostOnce(Message message) {
        websocket.send(message);
        // No guarantee, no retry
        // Fastest, but may lose messages
    }

    /**
     * At-least-once (with retry)
     */
    public void atLeastOnce(Message message) {
        String messageId = message.getId();

        // Send message
        websocket.send(message);

        // Wait for ACK
        scheduler.schedule(() -> {
            if (!acknowledgedMessages.contains(messageId)) {
                // Retry
                websocket.send(message);
            }
        }, 5, TimeUnit.SECONDS);

        /**
         * Guarantees delivery, but may duplicate
         * Receiver must deduplicate using messageId
         */
    }

    /**
     * Exactly-once (with deduplication)
     */
    public void exactlyOnce(Message message) {
        String messageId = message.getId();

        // Check if already processed
        if (processedMessages.contains(messageId)) {
            return;  // Duplicate, skip
        }

        // Process message
        processMessage(message);

        // Mark as processed
        processedMessages.add(messageId);

        // Send ACK
        sendAck(messageId);

        /**
         * Expensive (state tracking), but no duplicates
         * Use for financial transactions, critical operations
         */
    }
}
```

**B. Offline Message Handling**

```text
Strategy 1: Store and Forward
──────────────────────────────
• Store messages while user offline
• Deliver on reconnection
• Limit: Last N hours or M messages

Strategy 2: Push Notifications
───────────────────────────────
• Detect user offline
• Send push notification (FCM, APNS)
• User opens app → connects → receives messages

Strategy 3: Hybrid
──────────────────
• Real-time if online (WebSocket)
• Push notification if offline
• Message history API for catch-up
```

---

## 🧠 **MIND MAP: REAL-TIME COMMUNICATION**

```text
      Real-Time Communication
               |
    ┌──────────┼──────────┐
    ↓          ↓          ↓
 Polling    SSE      WebSocket
    |          |          |
┌───┴───┐  ┌───┴───┐  ┌──┴──┐
↓       ↓  ↓       ↓  ↓     ↓
Short  Long One-  Auto- Full  Binary
Poll   Poll way  Recon duplex Support
 |       |    |      |    |      |
High   Lower Text  Event Bi-   Low
Latency Latency Only  ID  direct Overhead
```

---

## 💡 **EMOTIONAL ANCHORS (For Subconscious Power)**

1. **Short Polling = Asking "Are we there yet?" 🚗**
   - Kid asks every 5 minutes
   - Annoying and wasteful
   - High latency (might miss the exit!)

2. **Long Polling = Waiting for Pizza Delivery 🍕**
   - Call once, wait for answer
   - They call back when ready
   - Better than calling every minute!

3. **SSE = News Radio Station 📻**
   - Tune in, listen to stream
   - One-way broadcast
   - Auto-reconnects if signal lost
   - Perfect for updates!

4. **WebSocket = Phone Call 📞**
   - Two-way conversation
   - Instant communication
   - Stays connected
   - Requires both parties active

5. **Redis Pub/Sub = Intercom System 🔊**
   - Central hub broadcasts messages
   - All rooms hear announcement
   - Enables multi-server coordination

---

## 📚 **REAL-WORLD USAGE**

**Companies and their choices:**

1. **Slack: WebSockets**
   - Bidirectional chat
   - Typing indicators
   - Real-time collaboration
   - 10M+ daily active users

2. **Twitter: Server-Sent Events**
   - Live tweet streams
   - One-way notification feed
   - Automatic reconnection
   - Fallback to polling

3. **Facebook: Long Polling → WebSocket**
   - Started with long polling (2008)
   - Migrated to WebSocket (2014)
   - Billions of messages/day

4. **Figma: WebSockets**
   - Collaborative design
   - Cursor positions real-time
   - Low latency critical
   - Operational transforms

5. **Stock Exchanges: WebSockets**
   - Price updates (100+ msg/sec)
   - Order books
   - Trade execution
   - Sub-millisecond latency

---

## 🎤 **INTERVIEW TALKING POINTS**

**Strong answers:**

- "WebSockets provide full-duplex communication with 2-byte frame overhead vs 500+ bytes for HTTP, critical for high-frequency trading"

- "Server-Sent Events offer automatic reconnection and event IDs for resumption, perfect for one-way streaming like stock tickers"

- "For 1M concurrent connections, we'll use Node.js event loop handling 100K connections per server with Redis Pub/Sub for coordination"

- "Long polling requires new HTTP request per message with handshake overhead, while WebSocket reuses single connection"

**Red flags to avoid:**

- "WebSocket is always better" ❌ (SSE simpler for one-way)
- "Polling is never acceptable" ❌ (still valid for infrequent updates)
- "No need for authentication" ❌ (security critical!)
- "One server can handle everything" ❌ (need horizontal scaling)

**Advanced points (senior level):**

- "We'll use sticky sessions with consistent hashing to minimize connection disruption during server scaling"
- "Message delivery guarantees: at-most-once for chat (fast), exactly-once for payments (reliable)"
- "HTTP/2 multiplexing enables multiple SSE streams over single connection, reducing overhead"
- "For global deployment, we'll use anycast routing to nearest data center, then WebSocket to specific server"
- "Redis Pub/Sub enables N-server fanout with O(1) publish complexity, critical for 10M+ connections"
