package com.ouroboros.websocket.integration;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

abstract class MultiInstanceIntegrationTestBase {

  protected final List<ConfigurableApplicationContext> instances = new ArrayList<>();
  protected final List<WebSocketTestClient> clients = new ArrayList<>();

  @AfterEach
  void tearDown() {
    clients.forEach(WebSocketTestClient::disconnect);
    clients.clear();

    instances.forEach(ConfigurableApplicationContext::close);
    instances.clear();
  }

  protected ConfigurableApplicationContext startInstance(String instanceId, Map<String, String> properties) {
    List<String> args = new ArrayList<>();
    args.add("--server.port=0");
    args.add("--spring.main.banner-mode=off");
    args.add("--spring.application.name=websocket-it-" + instanceId);
    args.add("--ouroboros.websocket.instance-id=" + instanceId);

    for (Map.Entry<String, String> entry : properties.entrySet()) {
      args.add("--" + entry.getKey() + "=" + entry.getValue());
    }

    SpringApplication app = new SpringApplication(IntegrationTestWebSocketApplication.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);
    ConfigurableApplicationContext context = app.run(args.toArray(new String[0]));
    instances.add(context);
    return context;
  }

  protected WebSocketTestClient createClient(ConfigurableApplicationContext context) throws Exception {
    int port = ((WebServerApplicationContext) context).getWebServer().getPort();
    WebSocketTestClient client = new WebSocketTestClient("http://localhost:" + port + "/websocket/stomp");
    client.connect();
    clients.add(client);
    return client;
  }

  protected static void awaitTrue(String description, Duration timeout, Supplier<Boolean> condition) {
    Instant deadline = Instant.now().plus(timeout);
    while (Instant.now().isBefore(deadline)) {
      if (Boolean.TRUE.equals(condition.get())) {
        return;
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException exception) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Interrupted while waiting for " + description, exception);
      }
    }
    throw new AssertionError("Timed out waiting for " + description);
  }

  protected static String testSessionId() {
    return "session-" + UUID.randomUUID();
  }
}
