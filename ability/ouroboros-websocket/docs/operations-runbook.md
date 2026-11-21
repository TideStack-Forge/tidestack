# WebSocket Multi-Instance Operations Runbook

Redis defaults are separate contracts: Pub/Sub channels are `websocket:topic`, `websocket:user`, `websocket:session` and `websocket:user-registry`; registry keys use `websocket:registry:session:<id>` and `websocket:registry:user:<id>`. If deployment overrides the Redis channel properties or `registry-key-prefix`, substitute those configured values in every diagnostic command below.

## Overview

This runbook provides troubleshooting procedures, monitoring guidelines, and incident response for the Ouroboros WebSocket multi-instance deployment across three broker modes: SimpleBroker, RabbitMQ STOMP Relay, and Redis Pub/Sub.

## Health Checks

### Application Health Endpoint

If Actuator is installed and the health endpoint is exposed, `/actuator/health` reports application health. This module does not provide a WebSocket `HealthIndicator` or `websocket.*` Micrometer meters. Application health alone does not prove STOMP delivery or cross-instance routing.

Validate the selected broker with its diagnostics below, then use real STOMP clients to CONNECT, SUBSCRIBE, send a message and observe delivery. For distributed modes, connect clients to different instances and repeat after reconnecting. Connection counts and message latency require deployment-owned instrumentation.

### SimpleBroker Health

SimpleBroker has no external broker dependency. A running application is only a prerequisite; confirm the actual STOMP subscription and message delivery.

```bash
# Verify WebSocket endpoint is accessible
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  http://localhost:8080/websocket/stomp
```

A handshake response can help diagnose the endpoint, but HTTP 400 does not prove a usable WebSocket connection. Complete STOMP CONNECT/SUBSCRIBE and message delivery with a client.

### RabbitMQ Health

Check STOMP relay connection:

```bash
# Check RabbitMQ is running
docker ps | grep rabbitmq

# Check STOMP port is listening
netstat -an | grep 61613

# Check AMQP port is listening
netstat -an | grep 5672

# Verify STOMP plugin is enabled
docker exec rabbitmq rabbitmq-plugins list | grep stomp
```

Expected output:
```
[E*] rabbitmq_stomp                    3.12.0
```

Check registry exchange exists:

```bash
docker exec rabbitmq rabbitmqctl list_exchanges | grep ouroboros.websocket.registry
```

Expected: `ouroboros.websocket.registry	fanout`

Check application connections:

```bash
docker exec rabbitmq rabbitmqctl list_connections
```

Look for connections from your application instances.

### Redis Health

Check Redis connection:

```bash
# Check Redis is running
docker ps | grep redis

# Test Redis connectivity
redis-cli ping
```

Expected: `PONG`

Check pub/sub channels:

```bash
redis-cli PUBSUB CHANNELS
```

Expected output (when instances are running):
```
1) "websocket:topic"
2) "websocket:user"
3) "websocket:session"
```

Check session registry keys:

```bash
redis-cli KEYS "websocket:registry:session:*"
```

Expected: List of session keys (one per active WebSocket connection).

Check TTL on a session key:

```bash
redis-cli TTL "websocket:registry:session:SESSION_ID"
```

Expected: Number between 1 and `registry-entry-ttl` (default 60).

## Common Issues and Solutions

### Issue: Messages Not Reaching All Clients

#### Symptoms
- Client connected to instance A sends message
- Client connected to instance B doesn't receive it
- No errors in application logs

#### Diagnosis

**For RabbitMQ:**

1. Check STOMP plugin is enabled:
```bash
docker exec rabbitmq rabbitmq-plugins list | grep stomp
```

If not enabled:
```bash
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_stomp
```

2. Check broker connectivity in application logs:
```bash
grep "STOMP" logs/application.log
```

Look for:
- `Connected to STOMP broker` (good)
- `Failed to connect to STOMP broker` (bad)
- `STOMP connection lost` (bad)

3. Verify user-destination broadcast configuration:
```bash
curl http://localhost:8080/actuator/configprops | grep websocket
```

Check `ouroboros.websocket.rabbitmq.registry-exchange` is set correctly.

4. Check RabbitMQ queues:
```bash
docker exec rabbitmq rabbitmqctl list_queues
```

Each instance should have a temporary queue bound to the registry exchange.

**For Redis:**

1. Verify all instances connected to same Redis:
```bash
redis-cli CLIENT LIST
```

Count connections. Should match number of application instances.

2. Check pub/sub activity:
```bash
redis-cli PUBSUB NUMSUB websocket:topic websocket:user websocket:session
```

Expected: Number of subscribers should match number of instances.

3. Check for deduplication issues in logs:
```bash
grep "sourceInstanceId" logs/application.log
```

Look for messages being filtered correctly.

4. Test pub/sub manually:
```bash
# Terminal 1: Subscribe
redis-cli SUBSCRIBE websocket:topic

# Terminal 2: Publish
redis-cli PUBLISH websocket:topic "test message"
```

If Terminal 1 doesn't receive the message, Redis pub/sub is broken.

#### Resolution

**RabbitMQ:**
- Restart RabbitMQ: `docker restart rabbitmq`
- Restart application instances to reconnect
- Check firewall rules for ports 5672 and 61613

**Redis:**
- Restart Redis: `docker restart redis`
- Restart application instances to reconnect
- Check firewall rules for port 6379

### Issue: High Memory Usage

#### Symptoms
- Application memory usage growing over time
- OutOfMemoryError in logs
- Slow response times

#### Diagnosis

**For SimpleBroker:**

1. Check JVM heap usage:
```bash
curl http://localhost:8080/actuator/metrics/jvm.memory.used
```

2. Check number of active sessions:
```bash
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

SimpleBroker stores all session state in memory. High connection count = high memory usage.

**For RabbitMQ:**

1. Check RabbitMQ memory usage:
```bash
docker exec rabbitmq rabbitmqctl status | grep memory
```

2. Check queue depths:
```bash
docker exec rabbitmq rabbitmqctl list_queues name messages
```

If queues are accumulating messages, consumers aren't keeping up.

3. Check for message accumulation:
```bash
docker exec rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

**For Redis:**

1. Check Redis memory usage:
```bash
redis-cli INFO memory
```

2. Check number of session keys:
```bash
redis-cli DBSIZE
```

3. Check for stale sessions (TTL not expiring):
```bash
redis-cli KEYS "websocket:registry:session:*" | while read key; do
  echo "$key: $(redis-cli TTL "$key")"
done
```

If many keys have TTL -1 (no expiration), heartbeat mechanism is broken.

#### Resolution

**SimpleBroker:**
- Increase JVM heap: `-Xmx2g` (adjust based on connection count)
- Reduce connection count (scale horizontally instead)
- Consider migrating to RabbitMQ or Redis for better scalability

**RabbitMQ:**
- Increase RabbitMQ memory limit in `docker-compose.yml`:
```yaml
environment:
  RABBITMQ_VM_MEMORY_HIGH_WATERMARK: 2GB
```
- Check for slow consumers: `rabbitmqctl list_consumers`
- Increase consumer concurrency in application.yml:
```yaml
spring:
  rabbitmq:
    listener:
      simple:
        concurrency: 5
        max-concurrency: 20
```

**Redis:**
- Set maxmemory policy:
```bash
redis-cli CONFIG SET maxmemory 2gb
redis-cli CONFIG SET maxmemory-policy allkeys-lru
```
- Check heartbeat is running:
```bash
grep "Heartbeat" logs/application.log
```
- Manually clean stale sessions:
```bash
redis-cli KEYS "websocket:registry:session:*" | xargs redis-cli DEL
```

### Issue: Connection Failures

#### Symptoms
- Clients can't establish WebSocket connection
- HTTP 503 or connection timeout
- "WebSocket handshake failed" in browser console

#### Diagnosis

1. Check WebSocket endpoint is accessible:
```bash
curl -i -N -H "Connection: Upgrade" -H "Upgrade: websocket" \
  http://localhost:8080/websocket/stomp
```

Expected: HTTP 101 or HTTP 400 (not 503 or timeout).

2. Check application is running:
```bash
curl http://localhost:8080/actuator/health
```

3. Check for authentication errors in logs:
```bash
grep "WebSocket" logs/application.log | grep -i "auth\|denied\|forbidden"
```

4. Check SockJS fallback is working:
```bash
curl http://localhost:8080/websocket/stomp/info
```

Expected: JSON response with WebSocket transport info.

#### Resolution

- Verify authentication token is valid
- Check CORS configuration if connecting from browser
- Verify load balancer is configured for WebSocket (sticky sessions or proper routing)
- Check firewall rules allow WebSocket traffic
- Restart application if health check fails

### Issue: Duplicate Messages

#### Symptoms
- Clients receive the same message multiple times
- Only happens with Redis broker
- Logs show multiple deliveries

#### Diagnosis

1. Check sourceInstanceId in logs:
```bash
grep "BrokerEnvelope" logs/application.log | grep "sourceInstanceId"
```

2. Verify deduplication is working:
```bash
grep "Skipping message from self" logs/application.log
```

If this log line is missing, deduplication is broken.

3. Check instance IDs are unique:
```bash
curl http://localhost:8080/actuator/env | grep "spring.application.instance-id"
```

Each instance must have a unique ID.

#### Resolution

- Ensure each instance has unique `spring.application.instance-id`
- Check `RedisClusterEnvelopeListener` is filtering correctly
- Restart instances to regenerate instance IDs
- If problem persists, check Redis pub/sub isn't duplicating messages:
```bash
redis-cli PUBSUB NUMSUB websocket:topic
```

### Issue: Session Registry Out of Sync

#### Symptoms
- User messages not delivered (404 user not found)
- Session-specific messages fail
- Registry size doesn't match connection count

#### Diagnosis

**For RabbitMQ:**

1. Check registry exchange bindings:
```bash
docker exec rabbitmq rabbitmqctl list_bindings | grep ouroboros.websocket.registry
```

Each instance should have a binding.

2. Check for registry events in logs:
```bash
grep "SessionRegistry" logs/application.log
```

Look for `sessionAdded` and `sessionRemoved` events.

**For Redis:**

1. Compare registry size to connection count:
```bash
# Registry size
redis-cli KEYS "websocket:registry:session:*" | wc -l

# Connection count
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

Should be equal (or close, accounting for eventual consistency).

2. Check heartbeat is running:
```bash
grep "Renewing session TTL" logs/application.log
```

Should see periodic log entries (every `heartbeat-interval`).

3. Check for expired sessions:
```bash
redis-cli KEYS "websocket:registry:session:*" | while read key; do
  ttl=$(redis-cli TTL "$key")
  if [ "$ttl" -lt 10 ]; then
    echo "WARNING: $key expires in ${ttl}s"
  fi
done
```

#### Resolution

**RabbitMQ:**
- Restart instances to re-register sessions
- Check network connectivity between instances and RabbitMQ
- Verify registry exchange is fanout type (not topic or direct)

**Redis:**
- Increase TTL if sessions expire too quickly:
```yaml
ouroboros:
  websocket:
    redis:
      registry-entry-ttl: 120s
      heartbeat-interval: 30s
```
- Manually refresh sessions:
```bash
redis-cli KEYS "websocket:registry:session:*" | while read key; do
  redis-cli EXPIRE "$key" 120
done
```
- Check heartbeat scheduler is running:
```bash
curl http://localhost:8080/actuator/scheduledtasks | grep -i heartbeat
```

## Performance Troubleshooting

### High Latency

#### Symptoms
- Messages take >100ms to deliver
- Clients report slow updates
- Metrics show high p95/p99 latency

#### Diagnosis

1. Check broker latency:

**RabbitMQ:**
```bash
# Check queue latency
docker exec rabbitmq rabbitmqctl list_queues name message_stats
```

**Redis:**
```bash
# Check Redis latency
redis-cli --latency
```

2. Check network latency between instances and broker:
```bash
# For RabbitMQ
ping rabbitmq-host

# For Redis
ping redis-host
```

3. Check application metrics:
```bash
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
```

#### Resolution

- Move broker closer to application instances (same datacenter/region)
- Increase broker resources (CPU, memory)
- Tune connection pool sizes:

**RabbitMQ:**
```yaml
spring:
  rabbitmq:
    cache:
      connection:
        size: 10
      channel:
        size: 50
```

**Redis:**
```yaml
spring:
  data:
    redis:
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
```

### Low Throughput

#### Symptoms
- Can't handle expected message volume
- Messages queue up
- Broker CPU/memory saturated

#### Diagnosis

1. Check message rate:
```bash
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
```

2. Check broker resource usage:

**RabbitMQ:**
```bash
docker stats rabbitmq
```

**Redis:**
```bash
docker stats redis
redis-cli INFO stats | grep instantaneous_ops_per_sec
```

3. Check for bottlenecks in application:
```bash
# Thread pool exhaustion
curl http://localhost:8080/actuator/metrics/executor.active

# GC pressure
curl http://localhost:8080/actuator/metrics/jvm.gc.pause
```

#### Resolution

- Scale broker horizontally (RabbitMQ cluster, Redis Cluster)
- Increase application instances
- Tune thread pools:
```yaml
spring:
  task:
    execution:
      pool:
        core-size: 10
        max-size: 50
```
- Consider batching messages if applicable
- For Redis V1, consider migrating to RabbitMQ for better scaling (>10 instances)

## Monitoring and Alerting

### Signals to Collect

#### Application Signals

The `websocket.*` names below are suggested deployment metrics, not meters registered by this module. Implement and validate collection before configuring alerts. Thresholds are examples to tune for the deployment. JVM metrics also require the corresponding Actuator/Micrometer exposure.

| Metric | Threshold | Alert Level |
|--------|-----------|-------------|
| `websocket.connections.active` | >1000 per instance | Warning |
| `websocket.messages.sent.rate` | <expected rate | Warning |
| `websocket.messages.failed` | >0 | Critical |
| `websocket.registry.size` | != connections | Warning |
| `jvm.memory.used` | >80% of max | Warning |
| `jvm.gc.pause` | >1s | Critical |

#### RabbitMQ Metrics

| Metric | Threshold | Alert Level |
|--------|-----------|-------------|
| Queue depth | >1000 messages | Warning |
| Memory usage | >80% | Critical |
| Connection count | <expected | Critical |
| Message rate | <expected | Warning |

#### Redis Metrics

| Metric | Threshold | Alert Level |
|--------|-----------|-------------|
| Memory usage | >80% | Warning |
| Pub/sub channels | <3 | Critical |
| Key count | Growing unbounded | Warning |
| Latency | >10ms | Warning |

### Prometheus Queries

```promql
# Connection count per instance
websocket_connections_active{instance="$instance"}

# Message rate
rate(websocket_messages_sent_total[5m])

# Failed messages
rate(websocket_messages_failed_total[5m])

# Registry size mismatch
abs(websocket_registry_size - websocket_connections_active) > 10

# RabbitMQ queue depth
rabbitmq_queue_messages{queue=~".*websocket.*"}

# Redis memory usage
redis_memory_used_bytes / redis_memory_max_bytes > 0.8
```

### Log Patterns to Monitor

#### Error Patterns

```bash
# Connection failures
grep -E "Connection refused|Connection timeout|Connection reset" logs/application.log

# Authentication failures
grep -E "Authentication failed|Access denied|Unauthorized" logs/application.log

# Broker failures
grep -E "STOMP.*failed|Redis.*failed|Broker.*error" logs/application.log

# Message delivery failures
grep -E "Failed to send|Message delivery failed|Destination not found" logs/application.log
```

#### Warning Patterns

```bash
# High latency
grep -E "took [0-9]{3,}ms|latency.*high" logs/application.log

# Memory pressure
grep -E "OutOfMemoryError|GC overhead|Heap.*full" logs/application.log

# Session issues
grep -E "Session.*expired|Session.*not found|Registry.*inconsistent" logs/application.log
```

## Incident Response Procedures

### Broker Failure

#### RabbitMQ Down

**Impact:** Multi-instance message routing stops. Connections remain local (SimpleBroker fallback behavior).

**Immediate Actions:**
1. Check RabbitMQ status: `docker ps | grep rabbitmq`
2. Check logs: `docker logs rabbitmq`
3. Attempt restart: `docker restart rabbitmq`

**If restart fails:**
1. Check disk space: `df -h`
2. Check RabbitMQ data directory: `docker exec rabbitmq ls -lh /var/lib/rabbitmq`
3. If corrupted, restore from backup or redeploy

**Recovery:**
1. Verify RabbitMQ is healthy: `docker exec rabbitmq rabbitmqctl status`
2. Verify STOMP plugin: `docker exec rabbitmq rabbitmq-plugins list | grep stomp`
3. Restart application instances to reconnect
4. Verify cross-instance messaging works

#### Redis Down

**Impact:** Multi-instance message routing stops. Session registry unavailable.

**Immediate Actions:**
1. Check Redis status: `docker ps | grep redis`
2. Check logs: `docker logs redis`
3. Attempt restart: `docker restart redis`

**If restart fails:**
1. Check disk space: `df -h`
2. Check Redis persistence: `docker exec redis ls -lh /data`
3. If corrupted, restore from backup or redeploy (session data is ephemeral, loss is acceptable)

**Recovery:**
1. Verify Redis is healthy: `redis-cli ping`
2. Restart application instances to reconnect
3. Sessions will re-register automatically
4. Verify cross-instance messaging works

### Application Instance Failure

**Impact:** Connections to failed instance are lost. Clients will reconnect to healthy instances.

**Immediate Actions:**
1. Check instance health: `curl http://instance:8080/actuator/health`
2. Check logs: `tail -f logs/application.log`
3. Check resource usage: `top`, `free -h`, `df -h`

**If instance is unresponsive:**
1. Capture thread dump: `jstack <pid> > thread-dump.txt`
2. Capture heap dump: `jmap -dump:format=b,file=heap-dump.hprof <pid>`
3. Restart instance

**Recovery:**
1. Verify instance is healthy
2. Verify broker connectivity
3. Monitor for recurring failures
4. Analyze dumps if available

### Network Partition

**Impact:** Instances can't communicate with broker. Messages don't route across instances.

**Diagnosis:**
1. Check connectivity from each instance:
```bash
# From instance 1
ping broker-host
telnet broker-host broker-port

# From instance 2
ping broker-host
telnet broker-host broker-port
```

2. Check firewall rules:
```bash
iptables -L -n
```

3. Check network interfaces:
```bash
ip addr show
ip route show
```

**Resolution:**
1. Fix network connectivity
2. Restart affected instances
3. Verify cross-instance messaging works

## Capacity Planning

### SimpleBroker

**Limits:**
- Single instance only
- Memory: ~1MB per 100 connections
- CPU: Minimal overhead

**Scaling:**
- Vertical only (increase instance resources)
- Not suitable for >1000 concurrent connections

### RabbitMQ

**Capacity per instance:**
- 1 vCPU per 10,000 messages/sec
- 2GB RAM baseline + 1GB per 100,000 queued messages
- Network: 100Mbps per 5,000 messages/sec

**Scaling:**
- Horizontal: RabbitMQ cluster
- Monitor queue depths and consumer lag
- Add nodes when CPU >70% or memory >80%

### Redis

**Capacity per instance:**
- 1 vCPU per 50,000 pub/sub messages/sec
- 1GB RAM baseline + session registry size (~1KB per session)
- Network: 100Mbps per 10,000 messages/sec

**Scaling:**
- Horizontal: Redis Cluster or Sentinel
- Monitor pub/sub channel traffic
- Add nodes when CPU >70% or memory >80%
- Note: V1 fanout pattern limits scaling to ~10 application instances

## Related Documents

- [configuration-guide.md](./configuration-guide.md) - Configuration reference
- [migration-guide.md](./migration-guide.md) - Migration procedures
- [ADR-001-pluggable-websocket-broker.md](../ADR-001-pluggable-websocket-broker.md) - Architecture decisions
