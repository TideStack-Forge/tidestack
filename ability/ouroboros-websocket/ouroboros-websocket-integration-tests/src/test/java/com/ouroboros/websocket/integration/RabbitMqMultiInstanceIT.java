package com.ouroboros.websocket.integration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class RabbitMqMultiInstanceIT extends MultiInstanceIntegrationTestBase {

  @Container
  private static final RabbitMQContainer rabbitmq = new RabbitMQContainer("rabbitmq:3.12-management")
    .withPluginsEnabled("rabbitmq_stomp");

  @Test
  void topicBroadcastAcrossInstances() throws Exception {
    Map<String, String> config1 = new HashMap<>();
    config1.put("ouroboros.websocket.broker-type", "rabbitmq-stomp-relay");
    config1.put("ouroboros.websocket.rabbitmq.host", rabbitmq.getHost());
    config1.put("ouroboros.websocket.rabbitmq.stomp-port", String.valueOf(rabbitmq.getMappedPort(61613)));
    config1.put("ouroboros.websocket.rabbitmq.username", rabbitmq.getAdminUsername());
    config1.put("ouroboros.websocket.rabbitmq.password", rabbitmq.getAdminPassword());
    ConfigurableApplicationContext instance1 = startInstance("instance-1", config1);

    Map<String, String> config2 = new HashMap<>();
    config2.put("ouroboros.websocket.broker-type", "rabbitmq-stomp-relay");
    config2.put("ouroboros.websocket.rabbitmq.host", rabbitmq.getHost());
    config2.put("ouroboros.websocket.rabbitmq.stomp-port", String.valueOf(rabbitmq.getMappedPort(61613)));
    config2.put("ouroboros.websocket.rabbitmq.username", rabbitmq.getAdminUsername());
    config2.put("ouroboros.websocket.rabbitmq.password", rabbitmq.getAdminPassword());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", config2);

    WebSocketTestClient client1 = createClient(instance1);
    WebSocketTestClient client2 = createClient(instance2);

    client1.subscribe("/topic/integration");
    client2.subscribe("/topic/integration");

    Thread.sleep(1000);

    client1.send("/app/broadcast", "Hello from instance 1");

    String msg1 = client1.waitForMessage(5);
    String msg2 = client2.waitForMessage(5);

    assertThat(msg1).isEqualTo("Hello from instance 1");
    assertThat(msg2).isEqualTo("Hello from instance 1");
  }

  @Test
  void messageRoutingWithThreeInstances() throws Exception {
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRabbitMqConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRabbitMqConfig());
    ConfigurableApplicationContext instance3 = startInstance("instance-3", getRabbitMqConfig());

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
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRabbitMqConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRabbitMqConfig());

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
    ConfigurableApplicationContext instance1 = startInstance("instance-1", getRabbitMqConfig());
    ConfigurableApplicationContext instance2 = startInstance("instance-2", getRabbitMqConfig());

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

  private Map<String, String> getRabbitMqConfig() {
    Map<String, String> config = new HashMap<>();
    config.put("ouroboros.websocket.broker-type", "rabbitmq-stomp-relay");
    config.put("ouroboros.websocket.rabbitmq.host", rabbitmq.getHost());
    config.put("ouroboros.websocket.rabbitmq.stomp-port", String.valueOf(rabbitmq.getMappedPort(61613)));
    config.put("ouroboros.websocket.rabbitmq.username", rabbitmq.getAdminUsername());
    config.put("ouroboros.websocket.rabbitmq.password", rabbitmq.getAdminPassword());
    return config;
  }
}
