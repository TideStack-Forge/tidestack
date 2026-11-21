# WebSocket Multi-Instance Migration Guide

Redis defaults are separate contracts: Pub/Sub channels are `websocket:topic`, `websocket:user`, `websocket:session` and `websocket:user-registry`; registry keys use `websocket:registry:session:<id>` and `websocket:registry:user:<id>`. If deployment overrides the Redis channel properties or `registry-key-prefix`, substitute those configured values in every diagnostic command below.

Monitoring prerequisite: the module does not ship `/actuator/health/websocket` or `websocket.*` meters. Use broker-specific checks and real STOMP delivery across instances; custom dashboards and connection/latency metrics must be supplied and validated by the deployment. Generic Actuator health, when enabled, only covers the contributors actually installed.

## Overview

This guide provides step-by-step instructions for migrating from SimpleBroker (single-instance) to multi-instance WebSocket deployments using RabbitMQ STOMP Relay or Redis Pub/Sub.

## Pre-Migration Checklist

Before starting migration, verify the following:

### Infrastructure Readiness

- [ ] Target broker infrastructure deployed and tested (RabbitMQ or Redis)
- [ ] Network connectivity verified between application instances and broker
- [ ] Firewall rules configured for broker ports
- [ ] Monitoring and alerting configured for broker
- [ ] Backup and recovery procedures tested

### Application Readiness

- [ ] Application version supports pluggable broker architecture (1.0.0-beta.2+)
- [ ] Maven dependencies available in artifact repository
- [ ] Configuration management system supports new broker settings
- [ ] Load balancer configured for WebSocket traffic (sticky sessions or proper routing)
- [ ] Health check endpoints configured

### Team Readiness

- [ ] Operations team trained on new broker operations
- [ ] Runbook reviewed and understood
- [ ] Rollback procedure documented and tested
- [ ] Communication plan for downtime (if applicable)
- [ ] Incident response team on standby

### Testing Readiness

- [ ] Test environment mirrors production topology
- [ ] Test scripts prepared for validation
- [ ] Performance baseline established
- [ ] Acceptance criteria defined

## Migration Path Selection

### Choose RabbitMQ if:

- RabbitMQ already deployed in your infrastructure
- Need reliable message delivery guarantees
- Require broker management UI and monitoring
- Planning to scale beyond 10 application instances
- High throughput messaging scenarios (>50,000 msg/sec)
- Team has RabbitMQ expertise

### Choose Redis if:

- Redis already deployed in your infrastructure
- Want simpler operations and lower complexity
- Cloud platform with managed Redis but no MQ
- Moderate scale (2-10 application instances)
- Team has Redis expertise
- Prefer lightweight solution

## Migration to RabbitMQ STOMP Relay

### Phase 1: Infrastructure Setup

#### Step 1.1: Deploy RabbitMQ with STOMP Plugin

**Using Docker Compose (Development/Staging):**

```bash
# Navigate to deployment scripts
cd scripts/deploy/develop-docker-compose

# Start RabbitMQ with STOMP plugin
docker-compose -f docker-compose-websocket-rabbitmq.yml up -d

# Verify RabbitMQ is running
docker ps | grep rabbitmq
```

**Using Docker (Production):**

```bash
# Pull RabbitMQ image with management plugin
docker pull rabbitmq:3.12-management

# Run RabbitMQ container
docker run -d \
  --name rabbitmq \
  --hostname rabbitmq-prod \
  -p 5672:5672 \
  -p 15672:15672 \
  -p 61613:61613 \
  -e RABBITMQ_DEFAULT_USER=admin \
  -e RABBITMQ_DEFAULT_PASS=admin123 \
  -v rabbitmq-data:/var/lib/rabbitmq \
  rabbitmq:3.12-management

# Enable STOMP plugin
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_stomp
docker exec rabbitmq rabbitmq-plugins enable rabbitmq_management

# Verify plugins enabled
docker exec rabbitmq rabbitmq-plugins list
```

Expected output:
```
[E*] rabbitmq_management       3.12.0
[E*] rabbitmq_stomp            3.12.0
```

#### Step 1.2: Verify RabbitMQ Health

```bash
# Check RabbitMQ status
docker exec rabbitmq rabbitmqctl status

# Check ports are listening
netstat -an | grep -E "5672|15672|61613"

# Access management UI
open http://localhost:15672
# Login: admin / admin123
```

#### Step 1.3: Create Virtual Host (Production)

```bash
# Create dedicated virtual host for WebSocket
docker exec rabbitmq rabbitmqctl add_vhost /ouroboros

# Create dedicated user
docker exec rabbitmq rabbitmqctl add_user ouroboros_ws <secure-password>

# Set permissions
docker exec rabbitmq rabbitmqctl set_permissions -p /ouroboros ouroboros_ws ".*" ".*" ".*"

# Verify
docker exec rabbitmq rabbitmqctl list_vhosts
docker exec rabbitmq rabbitmqctl list_users
```

### Phase 2: Application Configuration

#### Step 2.1: Update Maven Dependencies

Add to your application's `pom.xml` (if not using `ouroboros-ability-all`):

```xml
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-websocket-broker-rabbitmq</artifactId>
  <version>${ouroboros.version}</version>
</dependency>
```

Rebuild application:

```bash
mvn clean package -DskipTests
```

#### Step 2.2: Update Configuration

**Development/Staging (`application-dev.yml`):**

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

**Production (`application-prod.yml`):**

```yaml
ouroboros:
  websocket:
    broker-type: rabbitmq-stomp-relay
    rabbitmq:
      host: ${RABBITMQ_HOST:rabbitmq.prod.example.com}
      port: ${RABBITMQ_STOMP_PORT:61613}
      system-login: ${RABBITMQ_SYSTEM_USER}
      system-passcode: ${RABBITMQ_SYSTEM_PASSWORD}
      client-login: ${RABBITMQ_CLIENT_USER}
      client-passcode: ${RABBITMQ_CLIENT_PASSWORD}
      virtual-host: ${RABBITMQ_VHOST:/ouroboros}
      registry-exchange: ouroboros.websocket.registry

spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:rabbitmq.prod.example.com}
    port: ${RABBITMQ_AMQP_PORT:5672}
    username: ${RABBITMQ_USER}
    password: ${RABBITMQ_PASSWORD}
    virtual-host: ${RABBITMQ_VHOST:/ouroboros}
    listener:
      simple:
        concurrency: 3
        max-concurrency: 10
        retry:
          enabled: true
          initial-interval: 1000
          max-attempts: 3
```

#### Step 2.3: Configure Environment Variables

**Using Docker:**

```bash
# Create .env file
cat > .env <<EOF
RABBITMQ_HOST=rabbitmq.prod.example.com
RABBITMQ_STOMP_PORT=61613
RABBITMQ_AMQP_PORT=5672
RABBITMQ_USER=ouroboros_ws
RABBITMQ_PASSWORD=<secure-password>
RABBITMQ_VHOST=/ouroboros
EOF

# Update docker-compose.yml
docker-compose up -d
```

**Using Kubernetes:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: websocket-rabbitmq-secret
type: Opaque
stringData:
  rabbitmq-user: ouroboros_ws
  rabbitmq-password: <secure-password>
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: websocket-rabbitmq-config
data:
  rabbitmq-host: rabbitmq.prod.example.com
  rabbitmq-stomp-port: "61613"
  rabbitmq-amqp-port: "5672"
  rabbitmq-vhost: /ouroboros
```

### Phase 3: Deployment

#### Step 3.1: Deploy to Test Environment

```bash
# Deploy first instance
docker run -d \
  --name app-instance-1 \
  -p 8081:8080 \
  --env-file .env \
  -e SPRING_PROFILES_ACTIVE=prod \
  ouroboros-develop:latest

# Wait for startup
sleep 30

# Check health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

No module-specific health JSON is supplied. Confirm broker configuration and complete the STOMP delivery check described in the operations runbook.

#### Step 3.2: Deploy Second Instance

```bash
# Deploy second instance
docker run -d \
  --name app-instance-2 \
  -p 8082:8080 \
  --env-file .env \
  -e SPRING_PROFILES_ACTIVE=prod \
  ouroboros-develop:latest

# Wait for startup
sleep 30

# Check health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

#### Step 3.3: Verify Multi-Instance Communication

**Test Script:**

```bash
#!/bin/bash

# Connect client to instance 1
wscat -c ws://localhost:8081/websocket/stomp &
CLIENT1_PID=$!

# Connect client to instance 2
wscat -c ws://localhost:8082/websocket/stomp &
CLIENT2_PID=$!

# Wait for connections
sleep 5

# Send message from client 1
echo "SEND\ndestination:/app/broadcast\n\nHello from instance 1" | nc localhost 8081

# Check if client 2 receives message
# (Manual verification or use automated test client)

# Cleanup
kill $CLIENT1_PID $CLIENT2_PID
```

#### Step 3.4: Verify RabbitMQ Integration

```bash
# Check registry exchange exists
docker exec rabbitmq rabbitmqctl list_exchanges | grep ouroboros.websocket.registry

# Check temporary queues (one per instance)
docker exec rabbitmq rabbitmqctl list_queues

# Check connections (should see 2 AMQP + 2 STOMP)
docker exec rabbitmq rabbitmqctl list_connections

# Check message flow in management UI
open http://localhost:15672/#/queues
```

### Phase 4: Production Rollout

#### Step 4.1: Rolling Deployment (Zero Downtime)

**Option A: Blue-Green Deployment**

```bash
# Deploy new instances (green) with RabbitMQ config
for i in {1..3}; do
  docker run -d \
    --name app-green-$i \
    -p $((8100+i)):8080 \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=prod \
    ouroboros-develop:latest
done

# Wait for all instances to be healthy
for i in {1..3}; do
  while ! curl -f http://localhost:$((8100+i))/actuator/health; do
    sleep 5
  done
done

# Switch load balancer to green instances
# (Load balancer configuration depends on your setup)

# Verify traffic is flowing
# Monitor metrics and logs

# After verification, stop blue instances
for i in {1..3}; do
  docker stop app-blue-$i
  docker rm app-blue-$i
done
```

**Option B: Rolling Update**

```bash
# Update instances one by one
for i in {1..3}; do
  echo "Updating instance $i..."
  
  # Stop old instance
  docker stop app-instance-$i
  
  # Start new instance with RabbitMQ config
  docker run -d \
    --name app-instance-$i \
    -p $((8080+i)):8080 \
    --env-file .env \
    -e SPRING_PROFILES_ACTIVE=prod \
    ouroboros-develop:latest
  
  # Wait for health check
  while ! curl -f http://localhost:$((8080+i))/actuator/health; do
    sleep 5
  done
  
  echo "Instance $i updated successfully"
  
  # Wait before updating next instance
  sleep 30
done
```

#### Step 4.2: Monitor During Rollout

```bash
# Monitor RabbitMQ connections
watch -n 5 'docker exec rabbitmq rabbitmqctl list_connections'

# Monitor application health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.

# Monitor logs for errors
tail -f logs/application.log | grep -E "ERROR|WARN|WebSocket|STOMP"

# Monitor RabbitMQ queues
watch -n 5 'docker exec rabbitmq rabbitmqctl list_queues'
```

### Phase 5: Validation

#### Step 5.1: Functional Testing

```bash
# Run integration tests
mvn verify -pl ouroboros-websocket-integration-tests -Dit.test=RabbitMqMultiInstanceIT

# Manual testing checklist:
# [ ] Client can connect to any instance
# [ ] Messages broadcast to all clients across instances
# [ ] User-specific messages delivered correctly
# [ ] Session-specific messages delivered correctly
# [ ] Clients reconnect after instance restart
# [ ] No duplicate messages
# [ ] No lost messages
```

#### Step 5.2: Performance Testing

```bash
# Load test with multiple clients
# (Use your preferred load testing tool)

# Monitor metrics
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.

# Check RabbitMQ performance
docker exec rabbitmq rabbitmqctl list_queues name messages_ready messages_unacknowledged
```

#### Step 5.3: Acceptance Criteria

- [ ] All instances healthy and connected to RabbitMQ
- [ ] Cross-instance messaging working correctly
- [ ] No errors in application logs
- [ ] No errors in RabbitMQ logs
- [ ] Message latency within acceptable range (p95 < 50ms)
- [ ] No message loss or duplication
- [ ] Load balancer routing correctly
- [ ] Monitoring and alerting working

## Migration to Redis Pub/Sub

### Phase 1: Infrastructure Setup

#### Step 1.1: Deploy Redis

**Using Docker Compose (Development/Staging):**

```bash
# Navigate to deployment scripts
cd scripts/deploy/develop-docker-compose

# Start Redis
docker-compose -f docker-compose-websocket-redis.yml up -d

# Verify Redis is running
docker ps | grep redis
```

**Using Docker (Production):**

```bash
# Pull Redis image
docker pull redis:7-alpine

# Run Redis container
docker run -d \
  --name redis \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine redis-server --appendonly yes --requirepass <secure-password>

# Verify Redis is running
docker ps | grep redis
```

#### Step 1.2: Verify Redis Health

```bash
# Test Redis connectivity
redis-cli -a <secure-password> ping

# Check Redis info
redis-cli -a <secure-password> INFO server

# Test pub/sub
redis-cli -a <secure-password> PUBSUB CHANNELS
```

#### Step 1.3: Configure Redis (Production)

```bash
# Set memory limit
redis-cli -a <secure-password> CONFIG SET maxmemory 2gb
redis-cli -a <secure-password> CONFIG SET maxmemory-policy allkeys-lru

# Enable persistence
redis-cli -a <secure-password> CONFIG SET save "900 1 300 10 60 10000"

# Verify configuration
redis-cli -a <secure-password> CONFIG GET maxmemory
redis-cli -a <secure-password> CONFIG GET maxmemory-policy
```

### Phase 2: Application Configuration

#### Step 2.1: Update Maven Dependencies

Add to your application's `pom.xml` (if not using `ouroboros-ability-all`):

```xml
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-websocket-broker-redis</artifactId>
  <version>${ouroboros.version}</version>
</dependency>
```

Rebuild application:

```bash
mvn clean package -DskipTests
```

#### Step 2.2: Update Configuration

**Development/Staging (`application-dev.yml`):**

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

**Production (`application-prod.yml`):**

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
      host: ${REDIS_HOST:redis.prod.example.com}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
      timeout: 2000ms
      lettuce:
        pool:
          max-active: 20
          max-idle: 10
          min-idle: 2
          max-wait: 1000ms
```

#### Step 2.3: Configure Environment Variables

**Using Docker:**

```bash
# Create .env file
cat > .env <<EOF
REDIS_HOST=redis.prod.example.com
REDIS_PORT=6379
REDIS_PASSWORD=<secure-password>
EOF

# Update docker-compose.yml
docker-compose up -d
```

**Using Kubernetes:**

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: websocket-redis-secret
type: Opaque
stringData:
  redis-password: <secure-password>
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: websocket-redis-config
data:
  redis-host: redis.prod.example.com
  redis-port: "6379"
```

### Phase 3: Deployment

#### Step 3.1: Deploy to Test Environment

```bash
# Deploy first instance
docker run -d \
  --name app-instance-1 \
  -p 8081:8080 \
  --env-file .env \
  -e SPRING_PROFILES_ACTIVE=prod \
  ouroboros-develop:latest

# Wait for startup
sleep 30

# Check health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

No module-specific health JSON is supplied. Confirm broker configuration and complete the STOMP delivery check described in the operations runbook.

#### Step 3.2: Deploy Second Instance

```bash
# Deploy second instance
docker run -d \
  --name app-instance-2 \
  -p 8082:8080 \
  --env-file .env \
  -e SPRING_PROFILES_ACTIVE=prod \
  ouroboros-develop:latest

# Wait for startup
sleep 30

# Check health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.
```

#### Step 3.3: Verify Multi-Instance Communication

```bash
# Check pub/sub channels
redis-cli -a <secure-password> PUBSUB CHANNELS

# Expected output:
# 1) "websocket:topic"
# 2) "websocket:user"
# 3) "websocket:session"

# Check number of subscribers (should be 2)
redis-cli -a <secure-password> PUBSUB NUMSUB websocket:topic

# Check session registry keys
redis-cli -a <secure-password> KEYS "websocket:registry:session:*"
```

#### Step 3.4: Verify Redis Integration

```bash
# Monitor pub/sub activity
redis-cli -a <secure-password> MONITOR | grep -E "PUBLISH|SUBSCRIBE"

# Check session TTL
redis-cli -a <secure-password> KEYS "websocket:registry:session:*" | while read key; do
  echo "$key: $(redis-cli -a <secure-password> TTL "$key")s"
done

# Check heartbeat is working (TTL should refresh every 20-30s)
watch -n 5 'redis-cli -a <secure-password> KEYS "websocket:registry:session:*" | head -1 | xargs redis-cli -a <secure-password> TTL'
```

### Phase 4: Production Rollout

Follow the same rolling deployment procedure as RabbitMQ (Phase 4 above), but monitor Redis instead:

```bash
# Monitor Redis connections
watch -n 5 'redis-cli -a <secure-password> CLIENT LIST'

# Monitor pub/sub channels
watch -n 5 'redis-cli -a <secure-password> PUBSUB CHANNELS'

# Monitor session registry size
watch -n 5 'redis-cli -a <secure-password> KEYS "websocket:registry:session:*" | wc -l'

# Monitor Redis memory
watch -n 5 'redis-cli -a <secure-password> INFO memory | grep used_memory_human'
```

### Phase 5: Validation

#### Step 5.1: Functional Testing

```bash
# Run integration tests
mvn verify -pl ouroboros-websocket-integration-tests -Dit.test=RedisMultiInstanceIT

# Manual testing checklist:
# [ ] Client can connect to any instance
# [ ] Messages broadcast to all clients across instances
# [ ] No duplicate messages (deduplication working)
# [ ] Session registry consistent across instances
# [ ] Sessions expire after TTL without heartbeat
# [ ] Sessions persist with heartbeat
# [ ] Clients reconnect after instance restart
```

#### Step 5.2: Performance Testing

```bash
# Load test with multiple clients

# Monitor metrics
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.
# Collect this WebSocket signal through deployment-owned instrumentation; no built-in meter is registered.

# Check Redis performance
redis-cli -a <secure-password> INFO stats | grep instantaneous_ops_per_sec
redis-cli -a <secure-password> --latency
```

#### Step 5.3: Acceptance Criteria

- [ ] All instances healthy and connected to Redis
- [ ] Cross-instance messaging working correctly
- [ ] No duplicate messages
- [ ] No errors in application logs
- [ ] No errors in Redis logs
- [ ] Message latency within acceptable range (p95 < 50ms)
- [ ] Session registry consistent
- [ ] Heartbeat mechanism working
- [ ] Load balancer routing correctly
- [ ] Monitoring and alerting working

## Rollback Procedures

### Rollback from RabbitMQ to SimpleBroker

#### Step 1: Update Configuration

```yaml
ouroboros:
  websocket:
    broker-type: simple
```

Remove RabbitMQ-specific configuration.

#### Step 2: Remove Dependency (Optional)

If you want to remove the dependency:

```xml
<!-- Remove from pom.xml -->
<dependency>
  <groupId>com.ouroboros</groupId>
  <artifactId>ouroboros-websocket-broker-rabbitmq</artifactId>
</dependency>
```

Rebuild:
```bash
mvn clean package -DskipTests
```

#### Step 3: Rolling Restart

```bash
# Restart instances one by one with SimpleBroker config
for i in {1..3}; do
  docker stop app-instance-$i
  docker run -d \
    --name app-instance-$i \
    -p $((8080+i)):8080 \
    -e SPRING_PROFILES_ACTIVE=prod \
    -e OUROBOROS_WEBSOCKET_BROKER_TYPE=simple \
    ouroboros-develop:latest
  
  sleep 30
done
```

#### Step 4: Verify

```bash
# Check broker type
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.

# Expected: "simple"
```

#### Step 5: Configure Load Balancer for Sticky Sessions

Since SimpleBroker doesn't support multi-instance, configure sticky sessions:

**Nginx:**
```nginx
upstream websocket {
  ip_hash;  # Sticky sessions
  server app-instance-1:8080;
  server app-instance-2:8080;
  server app-instance-3:8080;
}
```

**HAProxy:**
```
backend websocket
  balance source  # Sticky sessions
  server app1 app-instance-1:8080 check
  server app2 app-instance-2:8080 check
  server app3 app-instance-3:8080 check
```

### Rollback from Redis to SimpleBroker

Follow the same procedure as RabbitMQ rollback above.

### Emergency Rollback (Immediate)

If critical issues occur during migration:

```bash
# Stop all new instances immediately
docker stop $(docker ps -q --filter "name=app-instance")

# Start old instances with SimpleBroker
docker start $(docker ps -aq --filter "name=app-old")

# Or deploy from backup
docker run -d --name app-instance-1 -p 8080:8080 ouroboros-develop:backup-tag
```

## Downtime Considerations

### Zero-Downtime Migration

**Possible with:**
- Blue-green deployment
- Rolling updates with proper load balancer configuration
- WebSocket clients that handle reconnection gracefully

**Requirements:**
- Load balancer supports WebSocket
- Clients implement reconnection logic
- Health checks configured correctly
- Sufficient capacity to run both old and new instances simultaneously

### Planned Downtime Migration

**When to use:**
- First migration (to test procedures)
- Critical production systems (reduce risk)
- Limited infrastructure capacity
- Clients don't handle reconnection well

**Procedure:**

1. **Announce downtime** (e.g., 30 minutes)
2. **Stop accepting new connections** (drain load balancer)
3. **Wait for existing connections to close** (or force close after timeout)
4. **Stop all instances**
5. **Deploy new instances with new broker config**
6. **Verify health**
7. **Resume accepting connections**

**Downtime estimate:**
- Infrastructure setup: 10-15 minutes (if not pre-deployed)
- Application deployment: 5-10 minutes
- Verification: 5-10 minutes
- **Total: 20-35 minutes**

## Testing and Validation

### Pre-Migration Testing

Test in staging environment first:

```bash
# Deploy staging environment
docker-compose -f docker-compose-staging.yml up -d

# Run automated tests
mvn verify -pl ouroboros-websocket-integration-tests

# Run manual tests
# - Connect multiple clients
# - Send messages across instances
# - Restart instances and verify reconnection
# - Simulate broker failure and recovery

# Load test
# - Use load testing tool (e.g., JMeter, Gatling)
# - Simulate expected production load
# - Monitor metrics and logs
# - Verify no errors or performance degradation
```

### Post-Migration Validation

After production migration:

```bash
# Smoke tests
curl http://localhost:8080/actuator/health
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.

# Functional tests
# - Connect test client
# - Send test message
# - Verify delivery

# Monitor for 24 hours
# - Check error rates
# - Check latency metrics
# - Check broker health
# - Review logs for warnings/errors

# Performance comparison
# - Compare metrics to baseline
# - Verify no degradation
```

## Troubleshooting Migration Issues

### Issue: Application Won't Start After Migration

**Symptoms:**
- Application fails to start
- Errors in logs about broker connection

**Diagnosis:**
```bash
# Check logs
tail -f logs/application.log | grep -E "ERROR|WARN"

# Check broker connectivity
telnet broker-host broker-port

# Check configuration
curl http://localhost:8080/actuator/configprops | grep websocket
```

**Resolution:**
- Verify broker is running and accessible
- Check credentials are correct
- Verify ports are open
- Check configuration syntax

### Issue: Messages Not Routing After Migration

**Symptoms:**
- Clients connected but messages not delivered
- No errors in logs

**Diagnosis:**
```bash
# Check broker type
# No built-in WebSocket health endpoint: use broker diagnostics and a STOMP delivery check.

# Check broker connectivity
# For RabbitMQ:
docker exec rabbitmq rabbitmqctl list_connections

# For Redis:
redis-cli CLIENT LIST
```

**Resolution:**
- Verify all instances using same broker
- Check broker configuration matches across instances
- Restart instances to re-establish connections

### Issue: Performance Degradation After Migration

**Symptoms:**
- Higher latency than SimpleBroker
- Slower message delivery

**Diagnosis:**
```bash
# Check broker latency
# For RabbitMQ:
docker exec rabbitmq rabbitmqctl list_queues name message_stats

# For Redis:
redis-cli --latency

# Check network latency
ping broker-host
```

**Resolution:**
- Move broker closer to application instances
- Increase broker resources
- Tune connection pool sizes
- Consider network optimization

## Related Documents

- [configuration-guide.md](./configuration-guide.md) - Configuration reference
- [operations-runbook.md](./operations-runbook.md) - Operations procedures
- [ADR-001-pluggable-websocket-broker.md](../ADR-001-pluggable-websocket-broker.md) - Architecture decisions
