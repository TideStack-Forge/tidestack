# Remote Debugging Pipeline

本文维护远程调试的当前机制与限制。

## Purpose and Scope

The Remote Debugging Pipeline enables IDE-style remote debugging of applications running in containers (Docker) or Kubernetes clusters by establishing secure debug tunnels between the development platform and runtime instances. It provides:

- **Session-based debug tunnel management** with lifecycle tracking
- **Multi-backend support** for Docker and Kubernetes environments
- **TCP tunnel brokering** with automatic port allocation
- **Multi-channel communication** for control, data, status, and commands
- **Runtime state synchronization** and session invalidation

## Actors and Components

### Core Components

| Component | Module | Responsibility |
|---|---|---|
| **RuntimeDebugSessionController** | `ouroboros-dev-app-management` | HTTP API for session open/get/close |
| **DefaultDebugSessionService** | `ouroboros-dev-runtime-broker` | Session state machine and lifecycle management |
| **DefaultTunnelBroker** | `ouroboros-dev-runtime-broker` | Delegates tunnel open/close to the `TunnelTransport` matching the session's `TransportMode` |
| **TcpTunnelTransport** | `ouroboros-dev-runtime-broker` | Loopback TCP listener plus bidirectional byte-stream proxying |
| **DebugConnector** | `ouroboros-dev-runtime-core` (interface), implemented in backend modules | Upstream debug socket connection |
| **DebugSessionReaper** | `ouroboros-dev-runtime-broker` | TTL/idle expiration logic (exposed as `reap(Instant)`; **not scheduled**) |
| **DebugSessionRuntimeEventListener** | `ouroboros-dev-runtime-broker` | Session invalidation on runtime changes |

### Backend Connectors

**DockerDebugConnector** (`ouroboros-dev-runtime-docker`):
- Resolves the container host and target port through `DockerDebugTargetResolver`
- Opens a TCP socket to the resolved target
- The target port comes from `DebugTunnelTargetType` (`DEBUG` → 5005, `BACKEND_API` → 8080); it is not hard-coded in the connector

**KubernetesDebugConnector** (`ouroboros-dev-runtime-kubernetes`):
- Finds the pod and forwards to the target port
- Returns the forwarded socket

### Supporting Infrastructure

**Runtime Management**:
- `DefaultDevRuntimeService` - Runtime orchestration (create/start/stop/restart/reload)
- `InMemoryRuntimeStateStore` - Runtime spec/instance/operation state storage
- `DockerRuntimeEventBridge` / `KubernetesRuntimeStatusSynchronizer` - Event translation

**Container Abstraction**:
- `ContainerService` - Abstract container operations
- `DockerContainerServiceImpl` - Docker client integration
- Kubernetes Fabric8 client - K8s API integration

**Debug Artifact Preparation**:
- `DefaultDebugRuntimePreparationService` - Artifact upload and runtime restart
- `WorkspaceDebugSessionWorkspaceStorage` - Debug binary storage
- `DefaultDebugProfileAuthorizationService` - Debug key validation

## Entry Points and Extension Points

### HTTP API Entry Points

**Session Management** (`RuntimeDebugSessionController`, `@RequestMapping("/api/dev/runtime/debug-sessions")`):
```
POST   /api/dev/runtime/debug-sessions          # Open debug session
GET    /api/dev/runtime/debug-sessions/{id}     # Get session details
DELETE /api/dev/runtime/debug-sessions/{id}     # Close session
```

There is **no** session list endpoint.

**Debug Profile Management** (`RuntimeDebugProfileController`, `@RequestMapping("/api/dev/{appName}")`):
```
POST   /api/dev/{appName}/authorize    # Authorize dev key and resolve profile
POST   /api/dev/{appName}/uploads      # Upload debug artifacts
```

**Request/Response Models**:

Open session request (`OpenDebugSessionRequest`):
```java
{
  "appName": "order-service",
  "profile": "dev-profile",
  "runtimeKind": "container",          // RuntimeKind
  "backendType": "DOCKER",             // BackendType
  "expectedGeneration": 5,             // optional, guards against stale runtime
  "maxLifetimeSeconds": 1800,          // optional, defaults to ouroboros.dev.runtime.debug.max-lifetime-seconds
  "idleTimeoutSeconds": 600,           // optional, defaults to ouroboros.dev.runtime.debug.idle-timeout-seconds
  "reason": "optional note",
  "uploadBatchId": "optional-artifact-batch-id",
  "metadata": { "targetType": "DEBUG" }
}
```

Session response (`DebugSessionDTO`):
```java
{
  "sessionId": "dbg-<uuid>",
  "appName": "order-service",
  "profile": "dev-profile",
  "runtimeKind": "CONTAINER",
  "backendType": "DOCKER",
  "targetType": "DEBUG",
  "transportMode": "TCP",
  "runtimeIdAtOpen": "...",
  "runtimeGeneration": 5,
  "ownerUserId": "...",
  "ownerUserName": "...",
  "status": "ACTIVE",
  "debugAccessStrategy": "SESSION_TUNNEL",
  "host": "127.0.0.1",
  "port": 43001,
  "bindingScope": "LOOPBACK_ONLY",
  "transportType": "TCP",
  "clientAccessHint": "ssh -L 5005:127.0.0.1:43001 broker-host",
  "requestedAt": "2026-04-12T10:30:00Z",
  "expiresAt": "2026-04-12T11:00:00Z"
}
```

### Extension Points

**1. Add New Backend Connector**

Implement `DebugConnector` (`com.ouroboros.dev.runtime.service.DebugConnector`). The custom-backend snippet below is a target extension sketch: current `BackendType` contains only DOCKER and KUBERNETES, so CUSTOM requires a justified enum/backend integration change before compilation.

```java
public interface DebugConnector {
    BackendType getBackendType();
    Try<Socket> connect(DebugSession session);
}

public class CustomBackendDebugConnector implements DebugConnector {
    @Override
    public BackendType getBackendType() {
        return BackendType.CUSTOM;
    }

    @Override
    public Try<Socket> connect(DebugSession session) {
        return Try.of(() -> new Socket(resolveHost(session), resolvePort(session)));
    }
}
```

Connectors are collected as a `List<DebugConnector>` by `DefaultDebugSessionService` and selected by `getBackendType()`; the concrete beans are created by the backend auto-configurations (they are not `@Component`-scanned).

**2. Add Custom Debug Target Resolver**

Target resolution is **not** an SPI interface. Each backend module provides a concrete resolver; for Docker it is `DockerDebugTargetResolver`, which turns a `DebugSession` plus target port into a host/port target:

```java
class DockerDebugTargetResolver {
    Try<DebugTarget> resolve(DebugSession session, int targetPort);
}
```

**3. Extend Session Lifecycle Hooks**

Listen to `DevRuntimeEvent` for custom invalidation logic. The built-in listener uses `DevRuntimeEventType`:

```java
public class CustomDebugSessionListener {
    @EventListener
    public void onRuntimeEvent(DevRuntimeEvent event) {
        if (event.getType() == DevRuntimeEventType.RUNTIME_DELETED) {
            // Custom cleanup logic
        }
    }
}
```

**4. Add a Tunnel Transport**

Implement `TunnelTransport` and register it as a bean; `DefaultTunnelBroker` resolves the transport by `TransportMode` (`TCP`, `HTTP`). Existing implementations: `TcpTunnelTransport` (broker), `HttpTunnelTransport` (core), `DockerTcpTunnelTransport`, `KubernetesTcpTunnelTransport`.

## Lifecycle and State Machine

### Session State Machine

```
                    ┌──────────────┐
                    │  REQUESTED   │ (Initial state)
                    └──────┬───────┘
                           │
                    [Validate & Prepare]
                           │
                    ┌──────▼───────┐
                    │   OPENING    │ (Tunnel creation in progress)
                    └──────┬───────┘
                           │
                    [Tunnel Established]
                           │
                    ┌──────▼───────┐
                    │    ACTIVE    │
                    └──────┬───────┘
                           │
        [Close Request / Timeout / Runtime Change]
                           │
                    ┌──────▼───────┐
                    │   CLOSING    │ (Cleanup in progress)
                    └──────┬───────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
  ┌─────▼─────┐  ┌─────────▼───┐  ┌───────────▼──┐
  │  CLOSED   │  │   FAILED    │  │ INVALIDATED  │
  └───────────┘  └─────────────┘  └──────────────┘
        │                  │                  │
        └──────────────────┴──────────────────┘
                           │
                      ┌────▼─────┐
                      │ EXPIRED  │
                      └──────────┘
```

`DebugSessionStatus` defines exactly these eight values: `REQUESTED`, `OPENING`, `ACTIVE`, `CLOSING`, `CLOSED`, `EXPIRED`, `INVALIDATED`, `FAILED`. `EXPIRED` is a distinct terminal state produced by the reaper, not a successor of the other terminal states (the diagram above is illustrative of the terminal set).

### State Descriptions

| State | Description | Transitions |
|---|---|---|
| **REQUESTED** | Session created with resolved timeouts, not yet tunneled | → OPENING (tunnel success)<br>→ FAILED (validation/connection failure) |
| **OPENING** | Tunnel creation in progress | → ACTIVE (tunnel established)<br>→ FAILED (connection error) |
| **ACTIVE** | Tunnel active, accepting connections | → CLOSING (explicit close)<br>→ INVALIDATED (runtime change)<br>→ EXPIRED (reaper) |
| **CLOSING** | Cleanup in progress | → CLOSED (cleanup success)<br>→ FAILED (cleanup error) |
| **CLOSED** | Session cleanly terminated | Terminal state |
| **FAILED** | Session failed due to error | Terminal state |
| **INVALIDATED** | Session invalidated by runtime event | Terminal state |
| **EXPIRED** | Session expired by TTL/idle timeout | Terminal state |

`DebugSessionCloseReason` records why a session ended: `USER_CLOSED`, `EXPIRED`, `RUNTIME_RESTARTED`, `RUNTIME_DELETED`, `RUNTIME_GENERATION_CHANGED`, `FAILED`.

### Session Lifecycle Flow

```
Developer IDE          Dev Platform API       DebugSessionService      TunnelBroker         Backend Connector      Runtime Container
      |                       |                       |                      |                       |                       |
      |--Open Session-------->|                       |                      |                       |                       |
      |                       |                       |                      |                       |                       |
      |                       |--Create Session------>|                      |                       |                       |
      |                       |                       |                      |                       |                       |
      |                       |                 [Resolve Runtime + Generation]|                       |                       |
      |                       |                       |                      |                       |                       |
      |                       |                       |--Open Tunnel-------->|                       |                       |
      |                       |                       |   (TransportMode)    |--Allocate Port------->|                       |
      |                       |                       |                      |                       |                       |
      |                       |                       |                      |                [Bind Loopback Listener]      |
      |                       |                       |                      |                       |                       |
      |                       |                       |<--DebugSessionBinding|                       |                       |
      |                       |<--Session ACTIVE------|                      |                       |                       |
      |                       |                       |                      |                       |                       |
      |<--Endpoint Details----|                       |                      |                       |                       |
      |                       |                       |                      |                       |                       |
      |--Connect to Endpoint-------------------------------->|                |                       |                       |
      |                       |                       |                      |                       |                       |
      |                       |                       |                      |--connect(session)---->|                       |
      |                       |                       |                      |                       |--Connect to target---->|
      |                       |                       |                      |                       |<--Socket Established--|
      |                       |                       |                      |<--Upstream Socket-----|                       |
      |                       |                       |                [Bidirectional Proxy]         |                       |
      |                       |                       |                      |                       |                       |
      |<====================Debug Protocol (JDWP) Byte Stream===========================================|
      |                       |                       |                      |                       |                       |
      |--Close Session------->|                       |                      |                       |                       |
      |                       |--Close Session------->|                      |                       |                       |
      |                       |                       |--Close Tunnel------->|                       |                       |
      |                       |                       |                [Close Sockets / Release Port]   |                       |
      |                       |<--Session Closed------|                      |                       |                       |
      |<--Confirmation--------|                       |                      |                       |                       |
```

## Transport and Contracts

### Communication Channels

The remote debugging pipeline uses four distinct communication channels:

| Channel | Protocol | Purpose | Characteristics |
|---|---|---|---|
| **HTTP REST** | HTTP/JSON | Control plane (session CRUD, profile management) | Stateless, request/response |
| **Raw TCP** | TCP byte stream | Debug data plane (JDWP protocol tunneling) | Stateful, bidirectional, low-level |
| **WebSocket STOMP** | STOMP | Status push (runtime state, logs) | Real-time, pub/sub |
| **Message Queue** | RabbitMQ (abstracted) | Command bus (dev-helper commands) | Async, reliable, cross-node |

### Channel Usage Patterns

**HTTP REST** (Control Plane):
```
POST /api/dev/runtime/debug-sessions
{
  "appName": "order-service",
  "profile": "dev-profile",
  "runtimeKind": "CONTAINER",
  "backendType": "DOCKER"
}

Response:
{
  "sessionId": "dbg-550e8400-e29b-41d4-a716-446655440000",
  "status": "ACTIVE",
  "host": "127.0.0.1",
  "port": 43001,
  "bindingScope": "LOOPBACK_ONLY",
  "transportType": "TCP"
}
```

**Raw TCP** (Debug Data Plane):
```
IDE → tcp://127.0.0.1:43001 (broker) → tcp://container-host:5005 (runtime)
[Opaque JDWP byte stream, broker does transparent forwarding]
```

**WebSocket STOMP** (Status Push):
`DevRuntimeStatusPushBridge` publishes runtime status on two destinations:

```javascript
// App status broadcast (all clients): /topic/app-container/{appName}-{profile}
stomp.subscribe('/topic/app-container/order-service-dev-profile', (message) => {
  const status = JSON.parse(message.body);
  // { runtimeId, containerId, appName, profile, status, backendType,
  //   webEndpoint, apiEndpoint, debugEndpoint, capabilities }
});

// App status for one user: sent to the profile as the STOMP user
stomp.subscribe('/user/queue/app/order-service/status', (message) => {
  const status = JSON.parse(message.body);
});

// Application logs (registered by DevAppStompCompensationRegistrar)
stomp.subscribe('/user/queue/app/order-service/logs', (message) => {
  console.log(message.body); // Log line
});

// Dev-helper container status / logs
stomp.subscribe('/topic/dev-helper/order-service', (message) => { /* helper status */ });
stomp.subscribe('/user/queue/app/order-service/helper/logs', (message) => { /* helper logs */ });
```

The status payload is resolved from `AppContainerService.getStatus(appName, profile)` (falling back to the event payload), and the user-scoped send uses `event.getProfile()` as the STOMP user.

**Message Queue** (Command Bus):
```json
// Command request (dev platform → runtime)
{
  "appName": "order-service",
  "command": "REFRESH_METADATA",
  "params": {},
  "timeout": 30000,
  "seq": 123,
  "channel": "ouroboros.dev-helper.command",
  "runtimeGeneration": 5
}

// Command response (runtime → dev platform)
{
  "success": true,
  "result": { "metadata": [...] },
  "seq": 123,
  "channel": "ouroboros.dev-helper.response"
}
```

### Tunnel Broker Protocol

**Port Allocation** (`BrokerPortAllocator`):
- Range: 43000-43999 (`DebugRuntimeBrokerAutoConfiguration.DEFAULT_MIN_PORT` / `DEFAULT_MAX_PORT`)
- Strategy: sequential scan of the range, skipping reserved ports, with an availability probe

**Tunnel Lifecycle:**
1. Allocate port from pool
2. Bind TCP listener on allocated port
3. On client connect: open upstream socket via connector
4. Bidirectional byte-stream proxy (client ↔ upstream)
5. On one client stream disconnect: close that stream pair. The listener and allocated port belong to the debug session and are released by session/tunnel close, so another connection can reuse the active tunnel.

**Binding Metadata** (`com.ouroboros.dev.runtime.model.DebugSessionBinding`):
```java
public class DebugSessionBinding {
    private String host;                     // "127.0.0.1"
    private Integer port;                    // 43001
    private DebugSessionBindingScope bindingScope; // LOOPBACK_ONLY | ...
    private DebugTunnelTargetType targetType;      // DEBUG | BACKEND_API
    private TransportMode transportType;           // TCP | HTTP
    private String clientAccessHint;               // "ssh -L 5005:127.0.0.1:43001 host"
    private Map<String, String> metadata;
}
```

## Sequence Diagram

### Debug Artifact Preparation Flow

```
Developer          Dev Platform          DebugProfileService     WorkspaceStorage     RuntimeService     Container
    |                    |                       |                      |                    |               |
    |--Upload Artifacts->|                       |                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |--Authorize Key------->|                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |                 [Validate Key & Profile]     |                    |               |
    |                    |                       |                      |                    |               |
    |                    |<--Authorized----------|                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |--Store Artifacts---------------------------->|                    |               |
    |                    |                       |                      |                    |               |
    |                    |                       |                [Persist Files + Manifest]|               |
    |                    |                       |                      |                    |               |
    |                    |<--Stored------------------------------------ |                    |               |
    |                    |                       |                      |                    |               |
    |<--Upload Complete--|                       |                      |                    |               |
    |                    |                       |                      |                    |               |
    |--Open Session----->|                       |                      |                    |               |
    | (with batchId)     |                       |                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |--Prepare Runtime----->|                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |                       |--Read Artifacts----->|                    |               |
    |                    |                       |                      |                    |               |
    |                    |                       |<--Files + Manifest---|                    |               |
    |                    |                       |                      |                    |               |
    |                    |                       |--Restart Runtime---------------------------->|            |
    |                    |                       |                      |                    |               |
    |                    |                       |                      |                    |--Restart----->|
    |                    |                       |                      |                    |               |
    |                    |                       |                      |                    |<--Ready-------|
    |                    |                       |                      |                    |               |
    |                    |                       |<--Runtime Ready-------------------------- |               |
    |                    |                       |                      |                    |               |
    |                    |<--Prepared------------|                      |                    |               |
    |                    |                       |                      |                    |               |
    |                    |--Open Tunnel (normal flow continues)         |                    |               |
```

## Failure Modes and Recovery

### Common Failure Scenarios

| Failure | Cause | Impact | Recovery |
|---|---|---|---|
| **Port Exhaustion** | All ports in range allocated | Session creation fails | Wait for sessions to close, widen the port range |
| **Connector Timeout** | Cannot reach container debug port | Session fails to open | Check container status, verify debug port exposed |
| **Runtime Not Ready** | Container not running or debug port not open | Session creation rejected | Start runtime, ensure debug flags enabled |
| **Generation Mismatch** | Runtime restarted since session request | Session invalidated | Refresh runtime status, create new session |
| **Tunnel Disconnect** | Network interruption, client crash | Session remains active until it is reaped | A reaper invocation cleans up idle sessions |
| **Backend Unavailable** | Docker daemon down, K8s API unreachable | All sessions for backend fail | Restart backend service, sessions auto-invalidate |
| **Debug Key Expired** | Authorization key past expiry time | Artifact upload rejected | Generate new debug key |

### Error Handling Strategy

**Session Creation Failures**:
- Validate runtime state before tunnel creation
- Fail fast with explicit error messages (`Try`-based result, surfaced as a platform error response)
- Clean up partial state (allocated ports, listeners)
- Return structured error response with recovery hints

**Runtime Change Invalidation**:
- `DebugSessionRuntimeEventListener` implements `DevRuntimeEventListener` and also exposes an `@EventListener onApplicationEvent(DevRuntimeEvent)`
- Invalidates sessions on: `RUNTIME_DELETED`, `RUNTIME_ENDPOINT_CHANGED`/`RUNTIME_STATUS_CHANGED` with a generation change, and `RUNTIME_STATUS_CHANGED` to a non-`RUNNING` status (→ `RUNTIME_RESTARTED` or `FAILED`)
- Closes tunnels and releases resources
- Marks matching sessions `INVALIDATED` with the corresponding `DebugSessionCloseReason`

**Automatic Cleanup**:
- `DebugSessionReaper.reap(Instant now)` expires sessions whose `expiresAt` has passed or whose `lastActiveAt + idleTimeoutSeconds` has passed
- Defaults come from `DevRuntimeProperties.DebugProperties`:
  - `ouroboros.dev.runtime.debug.max-lifetime-seconds` → **1800** (30 minutes)
  - `ouroboros.dev.runtime.debug.idle-timeout-seconds` → **600** (10 minutes)
- Both can be overridden per session through `OpenDebugSessionRequest`
- **Important:** the reaper bean is created by `DebugRuntimeBrokerAutoConfiguration`, but **no `@Scheduled` method, `TaskScheduler`, or executor invokes `reap(Instant)` in production code** — the only caller in the repository is `DefaultDebugSessionServiceTest`. Expiration therefore only happens if some external trigger calls the reaper; treat "sessions expire automatically" as unimplemented until a scheduler is wired.

**Graceful Degradation**:
- Backend connector failures don't affect other backends
- Individual session failures don't impact other sessions
- Status push failures don't block session operations

## Security and Tenancy

### Authentication and Authorization

**Debug Key Authorization**:
```java
// Debug key structure (resolved by DefaultDebugProfileAuthorizationService)
{
  "keyId": "uuid",
  "profile": "dev-profile",
  "appName": "order-service",
  "userId": "developer@example.com",
  "expiresAt": "2026-04-13T00:00:00Z",
  "permissions": ["UPLOAD_ARTIFACTS", "OPEN_DEBUG_SESSION"]
}
```

**Authorization Flow**:
1. Developer requests debug key for profile
2. Platform validates profile ownership
3. Platform generates time-limited key with permissions
4. Developer uses key for artifact upload and session creation
5. Platform validates key on each operation

**Session Isolation**:
- Each session has a unique ID (`dbg-<uuid>`) and app/profile/runtime context
- Sessions are stored with `ownerUserId` / `ownerUserName` and a `debugAccessStrategy` (`SESSION_TUNNEL`)
- Tunnel bindings are isolated by session
- Status push uses both a broadcast topic and a user-scoped queue (the STOMP user is the profile)

### Network Security

**Tunnel Binding Scopes** (`DebugSessionBindingScope` = `LOOPBACK_ONLY` | `INTERNAL_NETWORK`, default `LOOPBACK_ONLY` via `ouroboros.dev.runtime.debug.bind-scope`):
- **Loopback** (default): Binds to 127.0.0.1, only local access
- **Internal network**: Binds to a routable address, requires SSH tunnel or VPN for remote access
- `ouroboros.dev.runtime.debug.broker-host` (default `127.0.0.1`) and `ouroboros.dev.runtime.debug.loopback-only` (default `true`) further control binding

**Client Access Patterns**:
```bash
# Local development (loopback binding)
IDE connects to: tcp://127.0.0.1:43001

# Remote development (network binding + SSH tunnel)
ssh -L 5005:127.0.0.1:43001 dev-platform-host
IDE connects to: tcp://localhost:5005
```

**Container Network Isolation**:
- Debug port (5005) not exposed to public internet
- Only accessible via platform tunnel broker
- Container-to-container traffic isolated by network policy

### Audit and Monitoring

> 未实施/未核实：以下能力在 `ouroboros-dev-runtime` 与 `ouroboros-dev-app-management` 中没有对应的 Micrometer 指标、审计事件或埋点实现。

**Session Audit Events** (未实施):
- Session created (user, app, profile, backend)
- Session opened (tunnel established, endpoint details)
- Session closed (reason: explicit, timeout, invalidation)
- Session failed (error details)

**Metrics** (未实施):
- Active session count by app/backend
- Session creation rate and success rate
- Average session duration
- Port pool utilization
- Tunnel throughput (bytes transferred)

## Code Map

### Session Management

**Core Service**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/DefaultDebugSessionService.java`

**Service Interface / Store**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/DebugSessionService.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/DebugSessionStore.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/InMemoryDebugSessionStore.java`

**HTTP API**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/controller/RuntimeDebugSessionController.java`
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/controller/RuntimeDebugProfileController.java`

**State Machine**:
- Session states defined in `DebugSessionStatus`; close reasons in `DebugSessionCloseReason` (`ouroboros-dev-runtime-core`)
- Transitions enforced in `DefaultDebugSessionService`

### Tunnel Broker

**Broker Implementation**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/DefaultTunnelBroker.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/TcpTunnelTransport.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/transport/HttpTunnelTransport.java`

**Port Allocation**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/BrokerPortAllocator.java`

**Note:** earlier revisions of this document named a `LocalTcpDebugTunnelBroker` class. No such main-source class exists; only the test file `LocalTcpDebugTunnelBrokerTest.java` keeps the old name.

### Backend Connectors

**Connector Interface**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/DebugConnector.java`

**Docker**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-docker/src/main/java/com/ouroboros/dev/runtime/docker/debug/DockerDebugConnector.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-docker/src/main/java/com/ouroboros/dev/runtime/docker/debug/DockerDebugTargetResolver.java`

**Kubernetes**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-kubernetes/src/main/java/com/ouroboros/dev/runtime/kubernetes/debug/KubernetesDebugConnector.java`

### Lifecycle Management

**Session Reaper**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/DebugSessionReaper.java`

**Event Listener**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/DebugSessionRuntimeEventListener.java`

### Debug Artifact Preparation

**Preparation Service**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/service/runtime/DefaultDebugRuntimePreparationService.java`

**Authorization**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/service/runtime/DefaultDebugProfileAuthorizationService.java`

**Storage**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/service/runtime/WorkspaceDebugSessionWorkspaceStorage.java`

### Runtime Management

**Core Runtime Service**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/DefaultDevRuntimeService.java`

**State Store**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/service/InMemoryRuntimeStateStore.java`

**Event Bridges**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-docker/src/main/java/com/ouroboros/dev/runtime/docker/event/DockerRuntimeEventBridge.java`
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-kubernetes/src/main/java/com/ouroboros/dev/runtime/kubernetes/status/KubernetesRuntimeStatusSynchronizer.java`

### Container Abstraction

**Service Interface**:
- `dev-module/ouroboros-dev-container/ouroboros-dev-container-core/src/main/java/com/ouroboros/container/service/ContainerService.java`

**Docker Implementation**:
- `dev-module/ouroboros-dev-container/ouroboros-dev-container-docker/src/main/java/com/ouroboros/container/service/impl/DockerContainerServiceImpl.java`

### Status Push

**WebSocket Bridge**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/listener/DevRuntimeStatusPushBridge.java`
  - `/topic/app-container/{appName}-{profile}`, `/topic/dev-helper/{appName}`
  - `/queue/app/{appName}/status` sent to the profile user

**Log Streaming**:
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/controller/AppDebugSupportController.java` (consumes `ouroboros.app.log`)
- `dev-module/ouroboros-dev-app-management/src/main/java/com/ouroboros/dev/app/config/DevAppStompCompensationRegistrar.java` (user topic `/user/queue/app/{appName}/logs`)

### Configuration

**Broker Config**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-broker/src/main/java/com/ouroboros/dev/runtime/broker/DebugRuntimeBrokerAutoConfiguration.java`

**Runtime Properties**:
- `dev-module/ouroboros-dev-runtime/ouroboros-dev-runtime-core/src/main/java/com/ouroboros/dev/runtime/config/DevRuntimeProperties.java` (`ouroboros.dev.runtime.*`)

**Docker Config**:
- `dev-module/ouroboros-dev-container/ouroboros-dev-container-docker/src/main/java/com/ouroboros/container/config/DockerConfiguration.java`

## Current Limitations

1. **Stream channel commands not implemented**: `CreateStreamChannelCommand` and `DestroyStreamChannelCommand` exist as dev-helper command classes but the streaming channel feature is not wired into the debug session flow
2. **Reaper is not scheduled**: `DebugSessionReaper.reap(Instant)` has no production caller, so TTL/idle expiration does not actually fire on its own
3. **No session list API**: sessions can only be fetched by id
4. **Single-user sessions**: `ownerUserId`/`ownerUserName` are recorded, but there is no sharing or multi-developer support
5. **No session recording**: Cannot record and replay debug sessions
6. **Limited backend support**: Only Docker and Kubernetes, no cloud-native platforms (ECS, Cloud Run)
7. **No debug protocol abstraction**: Target types are DEBUG (5005) and BACKEND_API (8080); the TCP transport forwards bytes without interpreting JDWP. First-class Node/Python/Go debug targets and tooling are not implemented.

**Related Documents:**
- [Metadata Discovery Pipeline](metadata-discovery.md)
- [Online Debugging Data Exchange Pipeline](online-debugging-exchange.md)
- [WebSocket Infrastructure](../../modules/ability/ouroboros-websocket.md)
