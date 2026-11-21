# Ouroboros Platform Pipelines Architecture

本文维护元数据发现、远程调试和在线调试的当前管线边界。

## Overview

The Ouroboros development platform is built around three core pipeline architectures that enable low-code/pro-code hybrid development, remote runtime management, and real-time debugging capabilities. This document provides a unified view of these pipelines, their relationships, and shared infrastructure.

## Pipeline Family

### 1. Model Metadata Discovery Pipeline
**Purpose**: Discover, load, index, and serve model metadata from multiple sources (JAR, filesystem, VCS, typed code) to both runtime and development environments.

**Key Capabilities**:
- Multi-source metadata discovery (classpath, filesystem, VCS, annotations)
- Event-driven refresh lifecycle with ordered stages
- SPI-based provider composition with priority-based conflict resolution
- Development-time aggregation and caching
- Runtime indexing for fast model access

**Primary Use Cases**:
- Dynamic model loading for low-code applications
- Pro-code typed model discovery via annotations
- Development tool metadata access (UI schemas, business models, data models)
- Hot-reload during development

**Documentation**: [metadata-discovery.md](metadata-discovery.md)

### 2. Remote Debugging Pipeline
**Purpose**: Enable IDE-style remote debugging of applications running in containers or Kubernetes by establishing secure debug tunnels between development platform and runtime instances.

**Key Capabilities**:
- Session-based debug tunnel management with lifecycle tracking
- Multi-backend support (Docker, Kubernetes)
- TCP tunnel brokering with port allocation
- Multi-channel communication (HTTP control, TCP data, WebSocket status, MQ commands)
- Runtime state synchronization and session invalidation

**Primary Use Cases**:
- Remote Java debugging (JDWP) for containerized applications
- Development artifact hot-swap and runtime preparation
- Runtime status monitoring and log streaming
- Debug session lifecycle management with TTL/idle expiration

**Documentation**: [remote-debugging.md](remote-debugging.md)

### 3. Online Debugging Data Exchange Pipeline
**Purpose**: Capture and stream runtime execution state (starting with logic flow visualization) in real-time to development tools for debugging, monitoring, and optimization.

**Key Capabilities** (as implemented):
- Logic flow runtime execution event capture via the `FlowRuntimeAnalyzer` SPI
- MQ bridge (exchange `ouroboros.logicflow.debug`) feeding STOMP topics under `/topic/dev/{appName}/logicflow/debug`
- Bounded per-session history (500 events) with REST read-back of sessions, events, and single events
- Read-only observation — no breakpoints, step control, or variable editing
- App-scoped isolation (`appName` stamped by the producing runtime); no tenant partitioning

**Primary Use Cases**:
- Logic flow execution visualization and debugging
- Post-execution review of a session's retained event window
- Dev-time profiling of node-level execution
- Debugging development/containerized runtimes with the injected debug helper (the helper is not part of default production images)

**Documentation**: [online-debugging-exchange.md](online-debugging-exchange.md)

## Shared Concepts

### Extension Points

All three pipelines follow consistent extension patterns:

| Extension Type | Mechanism | Registration | Examples |
|---|---|---|---|
| **Metadata Providers** | Java SPI | `META-INF/services/` | `DataModelMetaProvider`, `UISchemaProvider` |
| **VCS Inspectors** | Java SPI | `META-INF/services/` | `BusinessModelInspector`, `UISchemaInspector` |
| **Runtime Analyzers** | Java SPI | `META-INF/services/` | `com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer` |
| **Debug Connectors** | Spring Bean | Auto-configuration | `DockerDebugConnector`, `KubernetesDebugConnector` |
| **Tunnel Transports** | Spring Bean | Auto-configuration | `TcpTunnelTransport`, `HttpTunnelTransport` |
| **Metadata Collectors** | Java SPI | `META-INF/services/` | `DataModelMetadataCollector`, `UIModelMetadataCollector` |

### Lifecycle Relationships

```
Platform Startup
    ↓
OuroborosContextRefreshingEvent
    ↓
┌─────────────────────────────────────────┐
│ Metadata Discovery Pipeline             │
│  - Load providers (SPI)                 │
│  - Scan sources (JAR, filesystem, VCS)  │
│  - Build metadata maps                  │
└─────────────────────────────────────────┘
    ↓
OuroborosMetadataRefreshedEvent
    ↓
┌─────────────────────────────────────────┐
│ Model Building & Indexing               │
│  - Build runtime models                 │
│  - Create indexes                       │
│  - Register routes/APIs                 │
└─────────────────────────────────────────┘
    ↓
OuroborosModelRefreshedEvent
    ↓
OuroborosContextRefreshedEvent
    ↓
┌─────────────────────────────────────────┐
│ Runtime Services Ready                  │
│  - Remote Debugging available           │
│  - Online Debugging active              │
│  - Application ready                    │
└─────────────────────────────────────────┘
```

### Communication Channels

The platform uses multiple communication channels, each optimized for specific use cases:

| Channel | Protocol | Use Case | Characteristics |
|---|---|---|---|
| **HTTP REST** | HTTP/JSON | Control plane APIs | Request/response, stateless |
| **WebSocket STOMP** | STOMP over SockJS | Real-time push | Bidirectional, session-based, pub/sub |
| **Message Queue** | RabbitMQ (abstracted) | Async commands and events | Cross-process transport; retention and delivery guarantees depend on binder/queue configuration |
| **Raw TCP** | TCP socket | Debug tunnel data | Low-level, byte-stream, proxied |

**Channel Selection Guidelines**:
- **Control operations** (create, update, delete, query): HTTP REST
- **Real-time notifications** (status updates, logs, events): WebSocket STOMP
- **Cross-node coordination** (commands, durable events): Message Queue
- **Binary data streaming** (debug protocol, file transfer): Raw TCP

### Session Management Patterns

Remote debugging uses the session lifecycle below. Online LogicFlow observation uses execution/session IDs and bounded event buffers; it does not implement this remote tunnel state machine.

**Session Lifecycle**:
```
REQUESTED → OPENING → ACTIVE → CLOSING → CLOSED/FAILED/EXPIRED/INVALIDATED
```

**Common Session Components**:
- **Session ID**: Unique identifier (UUID)
- **Tenant/App Context**: Multi-tenancy isolation
- **User/Principal**: Authorization context
- **Creation/Expiry Time**: TTL management
- **State Machine**: Explicit state transitions
- **Metadata**: Session-specific configuration

**Shared Infrastructure**:
- **Session Store**: In-memory (`InMemoryDebugSessionStore`, `LogicFlowDebugEventStore`)
- **Session Reaper**: Expiration logic exists (`DebugSessionReaper.reap(Instant)`) but is **not scheduled** — remote debug sessions do not currently expire on their own
- **Event Listeners**: Invalidation on runtime changes (`DebugSessionRuntimeEventListener`)
- **Authorization**: Token-based or key-based access control

### Security Model

**Authentication**:
- **Development Platform**: JWT token-based authentication
- **Remote Debugging**: Debug key authorization with profile ownership validation
- **Online Debugging**: Reuses platform authentication; the `LogicFlowDebugController` adds no per-session authorization check

**Authorization**:
- **Resource-level**: App/module ownership checks
- **Capability-based payload depth tiers** (summary, debug, inspect): *unimplemented proposal* — the implemented analyzer always emits summarized payloads
- **Session isolation**: Per-user/per-session data segregation

**Data Protection**:
- **Sensitive field redaction**: Automatic masking of credentials, tokens
- **Payload truncation**: Size limits on large data structures
- **Audit logging**: Session creation, access, and termination events

## Extension Point Matrix

| Pipeline | Provider SPI | Event Hooks | Auto-Configuration | Dev Module |
|---|---|---|---|---|
| **Metadata Discovery** | `*MetaProvider`, `VCSFileInspector`, `MetadataCollector` | `OuroborosMetadata*Event`, `OuroborosModel*Event` | `*AutoConfiguration` in runtime modules | `*-dev` modules with UI schemas |
| **Remote Debugging** | `DebugConnector` (Spring Bean) | `DevRuntimeEvent` | `DebugRuntimeBrokerAutoConfiguration` | `ouroboros-dev-app-management` |
| **Online Debugging** | `FlowRuntimeAnalyzer` (SPI) | `FlowDebugEvent` over MQ | `LogicFlowDebugHelperAutoConfiguration` (runtime), `LogicFlowDevAutoConfiguration` (dev) | `ouroboros-logicflow-dev` |

## Glossary

**App Home**: Filesystem directory containing low-code model definitions for an application (e.g., `business-model/`, `ui-model/`, `logic-flow/`).

**Debug Session**: Stateful connection between development platform and runtime instance for debugging purposes.

**Debug Tunnel**: TCP proxy that bridges local development tools to remote debug ports in containers.

**Dev Module**: Maven module suffixed with `-dev` that provides development-time management UI and APIs for a specific model type.

**Metadata Provider**: SPI implementation that discovers and loads model metadata from a specific source.

**Model Center**: Runtime index that aggregates metadata from all providers and provides fast lookup by model name/ID.

**Runtime Analyzer**: SPI hook that observes runtime execution and emits debug events.

**Session ID**: Unique identifier for a debug session or execution run, used for event correlation and isolation.

**VCS Inspector**: SPI implementation that classifies and enriches file metadata from version control systems.

**Workspace**: Development-time directory structure containing source models, typically backed by Git.

## Architecture Principles

1. **SPI-based Extensibility**: All pipelines use Java SPI for provider registration, enabling modular extension without core changes.

2. **Event-driven Refresh**: Metadata and model changes propagate through ordered lifecycle events, ensuring consistent state.

3. **Multi-source Composition**: Metadata can come from multiple sources (JAR, filesystem, VCS, annotations) with priority-based conflict resolution.

4. **Separation of Concerns**: Clear boundaries between discovery, loading, indexing, and access layers.

5. **Development/Runtime Duality**: Models are managed in development mode (VCS-backed, mutable) and consumed in runtime mode (indexed, immutable).

6. **Channel Specialization**: Different communication channels for different use cases (HTTP for control, WebSocket for push, MQ for durability, TCP for streaming).

7. **Session Lifecycle**: Remote tunnels track explicit states and support cleanup through `DebugSessionReaper.reap(Instant)`. The reaper is not scheduled by the default configuration; automatic expiry must not be assumed.

8. **Capability-based Security**: Access control based on user capabilities and payload sensitivity, not just binary allow/deny.

## Related Documentation

- [Development Module Pattern Guide](../../../guides/development-module-guide.md)
- [Metadata Discovery Pipeline](metadata-discovery.md)
- [Remote Debugging Pipeline](remote-debugging.md)
- [Online Debugging Exchange Pipeline](online-debugging-exchange.md)
- [WebSocket Infrastructure](../../modules/ability/ouroboros-websocket.md)
- [Logic Flow Architecture](../../modules/core/ouroboros-logicflow.md)
- [VCS Integration](../../../../dev-module/ouroboros-dev-version-control-system/README.md)
