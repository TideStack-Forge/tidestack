package com.ouroboros.dev.runtime.apisix;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.ouroboros.dev.runtime.DevRuntimeCoreAutoConfiguration;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class ApisixGatewayRouteSyncIntegrationTest {

  @Test
  void shouldSynchronizeUpsertRouteAcrossSpringEventChain() throws Exception {
    try (RecordingApisixAdminServer server = new RecordingApisixAdminServer()) {
      new ApplicationContextRunner()
          .withPropertyValues(
              "ouroboros.dev.runtime.gateway.enabled=true",
              "ouroboros.dev.runtime.gateway.provider=apisix",
              "ouroboros.dev.runtime.gateway.stable-domain-host-suffix=develop.example.com",
              "ouroboros.dev.runtime.gateway.apisix.base-url=" + server.baseUrl(),
              "ouroboros.dev.runtime.gateway.apisix.admin-key=secret",
              "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=rtg"
          )
          .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
          .run(context -> {
            context.getBean(DevRuntimeEventPublisher.class).publish(runtimeCreatedEvent());

            assertEquals(1, server.requests().size());
            RecordedRequest request = server.requests().get(0);
            assertEquals("PUT", request.method);
            assertEquals("/apisix/admin/routes/rtg-docker-test-app-crm-dev", request.path);
            assertEquals("secret", request.adminKey);
            assertTrue(request.body.contains("\"uri\":\"/*\""));
            assertTrue(request.body.contains("\"hosts\":[\"crm-dev.develop.example.com\"]"));
            assertTrue(request.body.contains("\"ouroboros-test-app-crm-dev:80\":1"));
          });
    }
  }

  @Test
  void shouldSynchronizeDeleteRouteAcrossSpringEventChain() throws Exception {
    try (RecordingApisixAdminServer server = new RecordingApisixAdminServer()) {
      new ApplicationContextRunner()
          .withPropertyValues(
              "ouroboros.dev.runtime.gateway.enabled=true",
              "ouroboros.dev.runtime.gateway.provider=apisix",
              "ouroboros.dev.runtime.gateway.stable-domain-host-suffix=develop.example.com",
              "ouroboros.dev.runtime.gateway.apisix.base-url=" + server.baseUrl(),
              "ouroboros.dev.runtime.gateway.apisix.admin-key=secret",
              "ouroboros.dev.runtime.gateway.apisix.route-id-prefix=rtg"
          )
          .withConfiguration(AutoConfigurations.of(DevRuntimeCoreAutoConfiguration.class, ApisixGatewayAutoConfiguration.class))
          .run(context -> {
            context.getBean(DevRuntimeEventPublisher.class).publish(runtimeDeletedEvent());

            assertEquals(1, server.requests().size());
            RecordedRequest request = server.requests().get(0);
            assertEquals("DELETE", request.method);
            assertEquals("/apisix/admin/routes/rtg-docker-test-app-crm-dev", request.path);
            assertEquals("secret", request.adminKey);
          });
    }
  }

  private static DevRuntimeEvent runtimeCreatedEvent() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_CREATED)
        .runtimeId("runtime-1")
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.RUNNING)
        .webEndpoint("http://127.0.0.1:18080")
        .apiEndpoint("http://127.0.0.1:18081")
        .build();
  }

  private static DevRuntimeEvent runtimeDeletedEvent() {
    return DevRuntimeEvent.builder()
        .type(DevRuntimeEventType.RUNTIME_DELETED)
        .runtimeId("runtime-1")
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .exposureStrategy(ExposureStrategy.STABLE_DOMAIN)
        .status(RuntimeStatus.STOPPED)
        .build();
  }

  static class RecordingApisixAdminServer implements AutoCloseable {
    private final HttpServer httpServer;
    private final List<RecordedRequest> requests = Collections.synchronizedList(new ArrayList<RecordedRequest>());

    RecordingApisixAdminServer() throws IOException {
      this.httpServer = HttpServer.create(new InetSocketAddress(0), 0);
      this.httpServer.createContext("/apisix/admin/routes", this::handle);
      this.httpServer.start();
    }

    List<RecordedRequest> requests() {
      return requests;
    }

    String baseUrl() {
      return "http://127.0.0.1:" + httpServer.getAddress().getPort();
    }

    private void handle(HttpExchange exchange) throws IOException {
      RecordedRequest request = new RecordedRequest();
      request.method = exchange.getRequestMethod();
      request.path = exchange.getRequestURI().getPath();
      request.adminKey = exchange.getRequestHeaders().getFirst("X-API-KEY");
      request.body = read(exchange.getRequestBody());
      requests.add(request);
      byte[] response = "{}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, response.length);
      exchange.getResponseBody().write(response);
      exchange.close();
    }

    @Override
    public void close() {
      httpServer.stop(0);
    }
  }

  static class RecordedRequest {
    private String method;
    private String path;
    private String adminKey;
    private String body;
  }

  private static String read(InputStream inputStream) throws IOException {
    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    byte[] buffer = new byte[256];
    int read;
    while ((read = inputStream.read(buffer)) >= 0) {
      outputStream.write(buffer, 0, read);
    }
    return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
  }
}
