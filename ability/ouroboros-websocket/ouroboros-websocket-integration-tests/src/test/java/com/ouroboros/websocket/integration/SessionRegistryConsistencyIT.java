package com.ouroboros.websocket.integration;

import java.time.Duration;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import com.ouroboros.websocket.DistributedSessionRegistry;
import com.ouroboros.websocket.SessionRoute;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class SessionRegistryConsistencyIT extends MultiInstanceIntegrationTestBase {

  @Container
  private static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management")
    .withPluginsEnabled("rabbitmq_stomp");

  @Container
  private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);

  @Test
  void rabbitmqRegistrySessionLifecycle() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRabbitMqConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRabbitMqConfig());

    DistributedSessionRegistry registry1 = instance1.getBean(DistributedSessionRegistry.class);
    DistributedSessionRegistry registry2 = instance2.getBean(DistributedSessionRegistry.class);

    String sessionId = testSessionId();
    String userId = "user-123";

    registry1.registerSession(sessionId, userId, "instance-1");

    Thread.sleep(500);

    Optional<SessionRoute> route1 = registry1.findSession(sessionId);
    Optional<SessionRoute> route2 = registry2.findSession(sessionId);

    assertThat(route1).isPresent();
    assertThat(route2).isPresent();
    assertThat(route1.get().getSessionId()).isEqualTo(sessionId);
    assertThat(route1.get().getUserId()).isEqualTo(userId);
    assertThat(route1.get().getInstanceId()).isEqualTo("instance-1");

    registry1.unregisterSession(sessionId);

    Thread.sleep(500);

    assertThat(registry1.findSession(sessionId)).isNotPresent();
    assertThat(registry2.findSession(sessionId)).isNotPresent();
  }

  @Test
  void rabbitmqRegistryMultipleUserSessions() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRabbitMqConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRabbitMqConfig());

    DistributedSessionRegistry registry1 = instance1.getBean(DistributedSessionRegistry.class);
    DistributedSessionRegistry registry2 = instance2.getBean(DistributedSessionRegistry.class);

    String userId = "user-456";
    String session1 = testSessionId();
    String session2 = testSessionId();
    String session3 = testSessionId();

    registry1.registerSession(session1, userId, "instance-1");
    registry1.registerSession(session2, userId, "instance-1");
    registry2.registerSession(session3, userId, "instance-2");

    Thread.sleep(500);

    Collection<SessionRoute> userSessions1 = registry1.findUserSessions(userId);
    Collection<SessionRoute> userSessions2 = registry2.findUserSessions(userId);

    assertThat(userSessions1).hasSize(3);
    assertThat(userSessions2).hasSize(3);

    assertThat(userSessions1).extracting(SessionRoute::getSessionId)
      .containsExactlyInAnyOrder(session1, session2, session3);
  }

  @Test
  void redisRegistrySessionLifecycle() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());

    DistributedSessionRegistry registry1 = instance1.getBean(DistributedSessionRegistry.class);
    DistributedSessionRegistry registry2 = instance2.getBean(DistributedSessionRegistry.class);

    String sessionId = testSessionId();
    String userId = "user-789";

    registry1.registerSession(sessionId, userId, "instance-1");

    Thread.sleep(500);

    Optional<SessionRoute> route1 = registry1.findSession(sessionId);
    Optional<SessionRoute> route2 = registry2.findSession(sessionId);

    assertThat(route1).isPresent();
    assertThat(route2).isPresent();
    assertThat(route1.get().getSessionId()).isEqualTo(sessionId);
    assertThat(route1.get().getUserId()).isEqualTo(userId);

    registry1.unregisterSession(sessionId);

    Thread.sleep(500);

    assertThat(registry1.findSession(sessionId)).isNotPresent();
    assertThat(registry2.findSession(sessionId)).isNotPresent();
  }

  @Test
  void redisRegistryMultipleUserSessions() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());

    DistributedSessionRegistry registry1 = instance1.getBean(DistributedSessionRegistry.class);
    DistributedSessionRegistry registry2 = instance2.getBean(DistributedSessionRegistry.class);

    String userId = "user-999";
    String session1 = testSessionId();
    String session2 = testSessionId();
    String session3 = testSessionId();

    registry1.registerSession(session1, userId, "instance-1");
    registry1.registerSession(session2, userId, "instance-1");
    registry2.registerSession(session3, userId, "instance-2");

    Thread.sleep(500);

    Collection<SessionRoute> userSessions1 = registry1.findUserSessions(userId);
    Collection<SessionRoute> userSessions2 = registry2.findUserSessions(userId);

    assertThat(userSessions1).hasSize(3);
    assertThat(userSessions2).hasSize(3);

    assertThat(userSessions1).extracting(SessionRoute::getSessionId)
      .containsExactlyInAnyOrder(session1, session2, session3);
  }

  @Test
  void redisRegistrySessionTtlExpiration() throws Exception {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "redis-pubsub");
    config.put("spring.data.redis.host", redis.getHost());
    config.put("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
    config.put("ouroboros.websocket.redis.session-ttl-seconds", "3");
    config.put("ouroboros.websocket.redis.heartbeat-interval-seconds", "10");

    ConfigurableApplicationContext instance1 = startInstance("instance-1", config);
    DistributedSessionRegistry registry = instance1.getBean(DistributedSessionRegistry.class);

    String sessionId = testSessionId();
    registry.registerSession(sessionId, "user-ttl", "instance-1");

    Thread.sleep(500);
    assertThat(registry.findSession(sessionId)).isPresent();

    awaitTrue("session to expire", Duration.ofSeconds(5), 
      () -> !registry.findSession(sessionId).isPresent());
  }

  @Test
  void redisRegistryHeartbeatKeepsSessionAlive() throws Exception {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "redis-pubsub");
    config.put("spring.data.redis.host", redis.getHost());
    config.put("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
    config.put("ouroboros.websocket.redis.session-ttl-seconds", "5");
    config.put("ouroboros.websocket.redis.heartbeat-interval-seconds", "2");

    ConfigurableApplicationContext instance1 = startInstance("instance-1", config);

    WebSocketTestClient client = createClient(instance1);
    client.subscribe("/topic/integration");

    Thread.sleep(7000);

    client.send("/app/broadcast", "Still alive");
    String msg = client.waitForMessage(5);
    assertThat(msg).isEqualTo("Still alive");
  }

  private Map<String, String> getRabbitMqConfig() {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "rabbitmq-stomp-relay");
    config.put("ouroboros.websocket.rabbitmq.host", rabbitmq.getHost());
    config.put("ouroboros.websocket.rabbitmq.stomp-port", String.valueOf(rabbitmq.getMappedPort(61613)));
    config.put("ouroboros.websocket.rabbitmq.username", rabbitmq.getAdminUsername());
    config.put("ouroboros.websocket.rabbitmq.password", rabbitmq.getAdminPassword());
    return config;
  }

  private Map<String, String> getRedisConfig() {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "redis-pubsub");
    config.put("spring.data.redis.host", redis.getHost());
    config.put("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
    return config;
  }
}
