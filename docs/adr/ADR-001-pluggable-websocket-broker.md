# ADR-001: Pluggable WebSocket Broker Architecture

## Status

Accepted. This record preserves the architectural decision; current implementation and deployment properties are maintained by the module design/configuration guide. Implementation progress and live multi-instance verification are separate evidence.

## Context

The Ouroboros platform's WebSocket implementation originally used Spring's SimpleBroker, which stores all session state and subscriptions in memory within a single JVM instance. This design creates critical limitations:

1. **No horizontal scaling**: Cannot run multiple application instances behind a load balancer
2. **Session affinity required**: Clients must always connect to the same server instance
3. **Single point of failure**: If the instance crashes, all WebSocket connections are lost
4. **Limited throughput**: Constrained by single-instance memory and CPU

As the platform evolves toward cloud-native deployment patterns with Kubernetes and container orchestration, these limitations become blocking issues for production deployments requiring high availability and horizontal scalability.

## Decision

We will implement a pluggable broker architecture that supports three operational modes:

### 1. SimpleBroker (Default)
- **Use case**: Development, single-instance deployments, testing
- **Characteristics**: In-memory, no external dependencies, zero configuration
- **Limitations**: Cannot scale horizontally

### 2. RabbitMQ STOMP Relay
- **Use case**: Production deployments with existing RabbitMQ infrastructure
- **Characteristics**: 
  - Uses RabbitMQ's native STOMP protocol support
  - STOMP relay for message routing (port 61613)
  - AMQP for distributed session registry (port 5672)
- **Benefits**: Battle-tested message broker, rich management UI, clustering support

### 3. Redis Pub/Sub
- **Use case**: Production deployments preferring Redis, cloud environments without MQ
- **Characteristics**:
  - Redis pub/sub for message fanout
  - Redis keys with TTL for session registry
  - Heartbeat mechanism (20s interval, 60s TTL)
  - Source instance deduplication
- **Benefits**: Simpler infrastructure, lower operational overhead, widely available in cloud platforms

### Architecture Principles

**Pluggability via the strategy interface**:
- Core abstractions in `ouroboros-websocket-core`
- Each broker implementation in separate module
- Runtime selection via configuration property and Spring strategy beans (the simple fallback is instantiated by the selection configuration)
- Fail-fast if required broker module missing

**Shared Runtime**:
- Common WebSocket configuration in `ouroboros-websocket-runtime`
- Strategy pattern for broker-specific behavior
- Unified API surface (`StompMessageSender`)

**Dependency Isolation**:
- Broker-specific dependencies only in broker modules
- Applications include only needed broker implementations
- No transitive dependency pollution

## Configuration

Broker selection still uses `ouroboros.websocket.broker-type`. Current property names, credentials, Redis TTL/heartbeat settings and deployment examples are maintained in the [configuration guide](../../ability/ouroboros-websocket/docs/configuration-guide.md), backed by [WebSocketBrokerProperties](../../ability/ouroboros-websocket/ouroboros-websocket-core/src/main/java/com/ouroboros/websocket/config/WebSocketBrokerProperties.java).

The original ADR examples used obsolete `stomp-port`/`amqp-port`/`username`/`password` and Redis seconds fields. They have been removed from the operational reading path; the historical decision remains available in Git. Current RabbitMQ relay properties use `port`, `system-login`/`system-passcode`, `client-login`/`client-passcode`; Redis uses `registry-entry-ttl` and `heartbeat-interval` durations. Spring AMQP connection settings are configured separately.

## Consequences

### Positive

1. **Horizontal Scalability**: Multiple application instances can share WebSocket state
2. **High Availability**: No single point of failure, instances can fail independently
3. **Flexible Deployment**: Choose broker based on infrastructure and requirements
4. **Backward Compatible**: Existing SimpleBroker deployments continue working unchanged
5. **Infrastructure Agnostic**: Support both MQ-based and Redis-based architectures
6. **Clear Migration Path**: Documented upgrade path from SimpleBroker to clustered modes

### Negative

1. **Increased Complexity**: Three broker implementations to maintain
2. **External Dependencies**: Production deployments require RabbitMQ or Redis
3. **Operational Overhead**: Additional infrastructure to monitor and maintain
4. **Testing Complexity**: Integration tests require Testcontainers and Docker

### Risks and Mitigations

**Risk**: Configuration errors lead to runtime failures
- **Mitigation**: Fail-fast validation at startup, clear error messages

**Risk**: Session registry inconsistency in distributed mode
- **Mitigation**: TTL-based expiration, heartbeat mechanism, thorough integration tests

**Risk**: Message delivery guarantees differ between brokers
- **Mitigation**: Document broker-specific loss/retry behavior. Redis Pub/Sub has no durable replay or at-least-once guarantee; snapshot and compensation protocols must be verified separately.

## Implementation Details

### Module Structure
```
ouroboros-websocket/
├── ouroboros-websocket-core/              # SPI interfaces
├── ouroboros-websocket-runtime/           # Shared runtime
├── ouroboros-websocket-broker-simple/     # SimpleBroker implementation
├── ouroboros-websocket-broker-rabbitmq/   # RabbitMQ implementation
├── ouroboros-websocket-broker-redis/      # Redis implementation
├── ouroboros-websocket-spring-boot-starter/  # Starter
└── ouroboros-websocket-integration-tests/ # Multi-instance tests
```

### Key Interfaces

**WebSocketBrokerStrategy**: Core strategy interface for broker-specific behavior
**DistributedSessionRegistry**: Session routing across instances
**BrokerEnvelope**: Message wrapper with source instance tracking
**LocalWebSocketOperations**: Local WebSocket session operations

### Redis Implementation Details

**V1 Design (Correctness First)**:
- Fanout pattern: All instances subscribe to Redis channels
- Each instance receives all messages, filters by local sessions
- Source instance ID prevents duplicate delivery
- Registry used for observability, not routing

**Message Channels**:
- `websocket:topic`: Topic broadcasts (default Redis channel)
- `websocket:user`: User-specific messages (default Redis channel)
- `websocket:session`: Session-specific messages (default Redis channel)

**Session Registry**:
- Key pattern: `websocket:registry:session:{sessionId}`
- TTL: 60 seconds (configurable)
- Heartbeat: 20 seconds (configurable)
- Automatic cleanup on expiration

### RabbitMQ Implementation Details

**Dual Protocol Approach**:
- STOMP relay for WebSocket message routing (Spring native support)
- AMQP for session registry pub/sub (richer messaging features)

**Session Registry**:
- Exchange: `websocket.registry`
- Fanout pattern for session lifecycle events
- Each instance maintains local cache of session routes

## Alternatives Considered

### Alternative 1: Redis-only with Lua Scripts
- **Rejected**: Increased complexity, harder to debug, limited benefit over fanout

### Alternative 2: Database-backed Session Registry
- **Rejected**: Higher latency, polling overhead, not real-time

### Alternative 3: Hazelcast/IMDG
- **Rejected**: Additional infrastructure dependency, overkill for this use case

### Alternative 4: Hard-coded RabbitMQ Only
- **Rejected**: Not flexible enough, excludes Redis-only deployments

## References

- [Spring WebSocket Documentation](https://docs.spring.io/spring-framework/reference/web/websocket.html)
- [RabbitMQ STOMP Plugin](https://www.rabbitmq.com/stomp.html)
- [Redis Pub/Sub](https://redis.io/docs/manual/pubsub/)
- [WebSocket module](../design/modules/ability/ouroboros-websocket.md)
