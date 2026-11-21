package com.ouroboros.websocket.integration;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RedisMultiInstanceIT extends MultiInstanceIntegrationTestBase {

  @Container
  private static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
    .withExposedPorts(6379);

  @Test
  void topicBroadcastAcrossInstances() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());

    WebSocketTestClient client1 = createClient(instance1);
    WebSocketTestClient client2 = createClient(instance2);

    client1.subscribe("/topic/integration");
    client2.subscribe("/topic/integration");

    Thread.sleep(1000);

    client1.send("/app/broadcast", "Hello from Redis");

    String msg1 = client1.waitForMessage(5);
    String msg2 = client2.waitForMessage(5);

    assertThat(msg1).isEqualTo("Hello from Redis");
    assertThat(msg2).isEqualTo("Hello from Redis");
  }

  @Test
  void noDuplicateMessagesFromSameInstance() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());

    WebSocketTestClient client = createClient(instance1);
    client.subscribe("/topic/integration");

    Thread.sleep(1000);

    client.send("/app/broadcast", "Test message");

    String msg = client.waitForMessage(5);
    assertThat(msg).isEqualTo("Test message");

    String duplicate = client.waitForMessage(1);
    assertThat(duplicate).isNull();
  }

  @Test
  void messageRoutingWithThreeInstances() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());
    ConfigurableApplicationContext instance3 = startInstance("instance-3", getRedisConfig());

    WebSocketTestClient client1 = createClient(instance1);
    WebSocketTestClient client2 = createClient(instance2);
    WebSocketTestClient client3 = createClient(instance3);

    client1.subscribe("/topic/integration");
    client2.subscribe("/topic/integration");
    client3.subscribe("/topic/integration");

    Thread.sleep(1000);

    client2.send("/app/broadcast", "Message from instance 2");

    String msg1 = client1.waitForMessage(5);
    String msg2 = client2.waitForMessage(5);
    String msg3 = client3.waitForMessage(5);

    assertThat(msg1).isEqualTo("Message from instance 2");
    assertThat(msg2).isEqualTo("Message from instance 2");
    assertThat(msg3).isEqualTo("Message from instance 2");
  }

  @Test
  void instanceFailoverScenario() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());

    WebSocketTestClient client1 = createClient(instance1);
    WebSocketTestClient client2 = createClient(instance2);

    client1.subscribe("/topic/integration");
    client2.subscribe("/topic/integration");

    Thread.sleep(1000);

    client1.send("/app/broadcast", "Before shutdown");
    assertThat(client1.waitForMessage(5)).isEqualTo("Before shutdown");
    assertThat(client2.waitForMessage(5)).isEqualTo("Before shutdown");

    instance1.close();
    instances.remove(instance1);

    Thread.sleep(500);

    client2.send("/app/broadcast", "After instance1 shutdown");
    String msg = client2.waitForMessage(5);
    assertThat(msg).isEqualTo("After instance1 shutdown");
  }

  @Test
  void concurrentMessagesFromMultipleInstances() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRedisConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRedisConfig());

    WebSocketTestClient client1 = createClient(instance1);
    WebSocketTestClient client2 = createClient(instance2);

    client1.subscribe("/topic/integration");
    client2.subscribe("/topic/integration");

    Thread.sleep(1000);

    client1.send("/app/broadcast", "Message-A");
    client2.send("/app/broadcast", "Message-B");

    String msg1a = client1.waitForMessage(5);
    String msg1b = client1.waitForMessage(5);
    String msg2a = client2.waitForMessage(5);
    String msg2b = client2.waitForMessage(5);

    assertThat(msg1a).isIn("Message-A", "Message-B");
    assertThat(msg1b).isIn("Message-A", "Message-B");
    assertThat(msg2a).isIn("Message-A", "Message-B");
    assertThat(msg2b).isIn("Message-A", "Message-B");
  }

  private Map<String, String> getRedisConfig() {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "redis-pubsub");
    config.put("spring.data.redis.host", redis.getHost());
    config.put("spring.data.redis.port", String.valueOf(redis.getMappedPort(6379)));
    return config;
  }
}
