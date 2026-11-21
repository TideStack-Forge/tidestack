# WebSocket Multi-Instance Configuration Guide

Current property names are defined by [WebSocketBrokerProperties](../ouroboros-websocket-core/src/main/java/com/ouroboros/websocket/config/WebSocketBrokerProperties.java). STOMP relay uses `ouroboros.websocket.rabbitmq.port` and separate system/client credentials; AMQP uses `spring.rabbitmq.*`. Redis TTL and heartbeat are Duration values (`registry-entry-ttl`, `heartbeat-interval`). Supply all referenced credentials through the target environment; examples do not provision broker accounts or prove deployment readiness.

Redis defaults are separate contracts: Pub/Sub channels are `websocket:topic`, `websocket:user`, `websocket:session` and `websocket:user-registry`; registry keys use `websocket:registry:session:<id>` and `websocket:registry:user:<id>`. If deployment overrides the Redis channel properties or `registry-key-prefix`, substitute those configured values in every diagnostic command below.

## Overview

Ouroboros WebSocket now supports three broker modes for different deployment scenarios:

| Mode | Best For | External Dependencies | Horizontal Scaling |
|------|----------|----------------------|-------------------|
| `simple` | Development, single-instance | None | ❌ No |
| `rabbitmq-stomp-relay` | Production with MQ | RabbitMQ | ✅ Yes |
| `redis-pubsub` | Production with Redis | Redis | ✅ Yes |

The full `ouroboros-ability-all-runtime` aggregate (also included by `ouroboros-ability-all`) packages all three implementations. The ordinary mothership base therefore supports selection through `ouroboros.websocket.broker-type` without adding broker jars. Only the selected implementation is activated; the default remains `simple`. External Redis/RabbitMQ services and credentials are still required for distributed modes. The standalone WebSocket starter packages only SimpleBroker, so standalone consumers must add the distributed broker module they select.

## 1. SimpleBroker Mode

### When to Use
- Local development
- CI tests
- Single-instance deployments
- Prototyping

### Configuration

```yaml
ouroboros:
  websocket:
    broker-type: simple
```

### Characteristics
- Zero external dependencies
- In-memory storage of session and subscription state
- Lowest latency (no network hop)
- Cannot scale across multiple application instances

### Deployment Example

```yaml
# application-dev.yml
spring:
  profiles:
    active: dev

ouroboros:
  websocket:
    broker-type: simple

server:
  port: 8080
```

### Limitations
- Sessions are lost when instance restarts
- Requires session affinity if behind load balancer
- All state stored in single JVM heap

## 2. RabbitMQ STOMP Relay Mode

### When to Use
- Production deployments already using RabbitMQ
- Need reliable message delivery
- Require broker management UI and monitoring
- High throughput messaging scenarios

### Prerequisites

1. RabbitMQ server running
2. STOMP plugin enabled
3. AMQP and STOMP ports accessible

### Docker Compose Setup

```bash
# Start RabbitMQ with STOMP plugin
docker-compose -f scripts/deploy/develop-docker-compose/docker-compose-websocket-rabbitmq.yml up -d
```

### Configuration

```yaml
ouroboros:
  websocket:
    broker-type: rabbitmq-stomp-relay
    rabbitmq:
      host: localhost
      port: 61613
      system-login: ${RABBITMQ_SYSTEM_USER}
      system-passcode: ${RABBITMQ_SYSTEM_PASSWORD}
      client-login: ${RABBITMQ_CLIENT_USER}
      client-passcode: ${RABBITMQ_CLIENT_PASSWORD}
      virtual-host: /
      registry-exchange: ouroboros.websocket.registry

spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: /
```

### Production Configuration Example

```yaml
ouroboros:
  websocket:
    broker-type: rabbitmq-stomp-relay
    rabbitmq:
      host: rabbitmq.prod.example.com
      port: 61613
      system-login: ${RABBITMQ_SYSTEM_USER}
      system-passcode: ${RABBITMQ_SYSTEM_PASSWORD}
      client-login: ${RABBITMQ_CLIENT_USER}
      client-passcode: ${RABBITMQ_CLIENT_PASSWORD}
      virtual-host: /ouroboros
      registry-exchange: ouroboros.websocket.registry

spring:
  rabbitmq:
    host: rabbitmq.prod.example.com
    port: 5672
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: /ouroboros
    listener:
      simple:
        concurrency: 3
        max-concurrency: 10
```

### Required Dependencies

Add to your pom.xml if not already using `ouroboros-ability-all-runtime` or `ouroboros-ability-all`:

```xml
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-websocket-broker-rabbitmq</artifactId>
  <version>${ouroboros.version}</version>
</dependency>
```

### STOMP Plugin Setup

Enable on RabbitMQ server:

```bash
rabbitmq-plugins enable rabbitmq_stomp
rabbitmq-plugins enable rabbitmq_management
```

Verify enabled plugins:

```bash
rabbitmq-plugins list
```

### Port Requirements

- **5672**: AMQP protocol (session registry)
- **61613**: STOMP protocol (WebSocket message routing)
- **15672**: Management UI (optional, but recommended)

### Monitoring

Access RabbitMQ Management UI:
- URL: `http://localhost:15672`
- Username: `guest` / `admin`
- Password: `guest` / `admin123`

### Troubleshooting

#### STOMP connection refused
```bash
# Check if STOMP plugin is enabled
rabbitmq-plugins list | grep stomp

# Check port listening
netstat -an | grep 61613
```

#### Session registry not syncing
```bash
# Check exchange exists
rabbitmqctl list_exchanges | grep ouroboros.websocket.registry

# Check queues
rabbitmqctl list_queues
```

## 3. Redis Pub/Sub Mode

### When to Use
- Production deployments already using Redis
- Cloud platforms with managed Redis but no MQ
- Lower operational complexity preferred
- Moderate scale (up to ~10 instances)

### Prerequisites

1. Redis server running
2. Pub/sub enabled (default)
3. TCP port accessible

### Docker Compose Setup

```bash
# Start Redis
docker-compose -f scripts/deploy/develop-docker-compose/docker-compose-websocket-redis.yml up -d
```

### Configuration

```yaml
ouroboros:
  websocket:
    broker-type: redis-pubsub
    redis:
      registry-entry-ttl: 60s
      heartbeat-interval: 20s

spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### Production Configuration Example

```yaml
ouroboros:
  websocket:
    broker-type: redis-pubsub
    redis:
      registry-entry-ttl: 120s
      heartbeat-interval: 30s

spring:
  data:
    redis:
      host: redis.prod.example.com
      port: 6379
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 2
```

### Required Dependencies

Add to your pom.xml if not already using `ouroboros-ability-all-runtime` or `ouroboros-ability-all`:

```xml
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-websocket-broker-redis</artifactId>
  <version>${ouroboros.version}</version>
</dependency>
```

### Session Registry Behavior

**TTL + Heartbeat**:
- Session entries expire after `registry-entry-ttl`
- Heartbeat renews TTL every `heartbeat-interval`
- If instance crashes, sessions auto-cleanup after TTL

**Recommended Values**:
- Development: TTL=60s, Heartbeat=20s
- Production: TTL=120s, Heartbeat=30s
- High latency networks: TTL=180s, Heartbeat=45s

### Fanout Pattern (V1)

Current Redis implementation uses **fanout**:
- All instances subscribe to all message channels
- Each instance receives all messages
- Source instance deduplication prevents duplicates
- Simple and correct, but not optimal for large clusters

### Limitations

- More network traffic than targeted routing
- Not ideal for >10 application instances
- V2 will introduce targeted routing optimization

### Monitoring

```bash
# Check pub/sub activity
redis-cli PUBSUB CHANNELS

# Check session registry keys
redis-cli --scan --pattern "websocket:registry:*"

# Check TTL on session keys
redis-cli TTL "websocket:registry:session:your-session-id"
```

## 4. Environment-Specific Configurations

### Development

**Recommendation**: Use `simple`

```yaml
# application-dev.yml
ouroboros:
  websocket:
    broker-type: simple
```

### Staging

**Recommendation**: Use same mode as production for realistic testing

```yaml
# application-staging.yml
ouroboros:
  websocket:
    broker-type: redis-pubsub
```

### Production - Small Scale (2-5 instances)

**Recommendation**: `redis-pubsub` or `rabbitmq-stomp-relay`

Choose Redis if:
- Redis already deployed
- Want simpler operations
- Traffic is moderate

Choose RabbitMQ if:
- RabbitMQ already deployed
- Need richer broker features
- Higher throughput expected

### Production - Large Scale (10+ instances)

**Recommendation**: `rabbitmq-stomp-relay`

Reasons:
- Better scaling characteristics
- Broker-based message routing
- More mature clustering support
- Better monitoring and operational tooling

## 5. Migration Scenarios

### SimpleBroker → RabbitMQ

1. Deploy RabbitMQ with STOMP plugin
2. Add broker-rabbitmq dependency
3. Update configuration
4. Restart application instances

### SimpleBroker → Redis

1. Deploy Redis
2. Add broker-redis dependency
3. Update configuration
4. Restart application instances

### Redis → RabbitMQ

1. Ensure no active sessions need migration (WebSocket clients will reconnect)
2. Update configuration
3. Rolling restart instances
4. Monitor session redistribution

## 6. Health Checks

### SimpleBroker
- Always healthy if application is running
- No external dependencies to check

### RabbitMQ
- STOMP relay connection status
- AMQP registry exchange availability
- Queue binding status

### Redis
- Pub/sub connection status
- Session registry key accessibility
- Heartbeat renewal working

## 7. Performance Tuning

### SimpleBroker
- JVM heap size for session storage
- WebSocket thread pool size

### RabbitMQ
- STOMP connection pool size
- AMQP listener concurrency
- Exchange/queue persistence settings

### Redis
- Connection pool size
- Session TTL and heartbeat intervals
- Redis maxmemory policy

## 8. Security Considerations

### All Modes
- WebSocket authentication handled by existing token interceptor
- Session identity derived from authenticated user

### RabbitMQ
- Use dedicated virtual host for WebSocket traffic
- Separate RabbitMQ credentials per environment
- TLS for production RabbitMQ connections

### Redis
- Enable AUTH for production Redis
- Use Redis ACLs if available
- TLS for production Redis connections

## 9. Verification Commands

### Check Current Broker Type
```bash
curl http://localhost:8080/actuator/env | grep ouroboros.websocket.broker-type
```

### Verify WebSocket Endpoint
```bash
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" http://localhost:8080/websocket/stomp
```

### Run Integration Tests
```bash
mvn verify --file ability/ouroboros-websocket/ouroboros-websocket-integration-tests/pom.xml
```

## Related Documents

- [ADR-001-pluggable-websocket-broker.md](../ADR-001-pluggable-websocket-broker.md)
- [operations-runbook.md](./operations-runbook.md)
- [migration-guide.md](./migration-guide.md)
