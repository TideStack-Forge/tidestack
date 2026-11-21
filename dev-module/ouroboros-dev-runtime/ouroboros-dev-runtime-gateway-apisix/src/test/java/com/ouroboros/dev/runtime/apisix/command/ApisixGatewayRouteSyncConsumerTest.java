package com.ouroboros.dev.runtime.apisix.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.apisix.client.ApisixAdminClient;
import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.mapper.ApisixRouteMapper;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommandType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class ApisixGatewayRouteSyncConsumerTest {

  @Test
  void shouldUpsertMappedRouteForSupportedCommand() {
    RecordingApisixAdminClient client = new RecordingApisixAdminClient();
    ApisixGatewayRouteSyncConsumer consumer = new ApisixGatewayRouteSyncConsumer(client, new ApisixRouteMapper(properties()));

    consumer.onApplicationEvent(upsertCommand());

    assertEquals(1, client.upsertRouteIds.size());
    assertEquals("rtg-docker-test-app-crm-dev", client.upsertRouteIds.get(0));
  }

  @Test
  void shouldDeleteMappedRouteForSupportedCommand() {
    RecordingApisixAdminClient client = new RecordingApisixAdminClient();
    ApisixGatewayRouteSyncConsumer consumer = new ApisixGatewayRouteSyncConsumer(client, new ApisixRouteMapper(properties()));

    consumer.onApplicationEvent(deleteCommand());

    assertEquals(1, client.deleteRouteIds.size());
    assertEquals("rtg-docker-test-app-crm-dev", client.deleteRouteIds.get(0));
  }

  @Test
  void shouldIgnoreUnsupportedScopeWithoutThrowing() {
    RecordingApisixAdminClient client = new RecordingApisixAdminClient();
    ApisixGatewayRouteSyncConsumer consumer = new ApisixGatewayRouteSyncConsumer(client, new ApisixRouteMapper(properties()));

    assertDoesNotThrow(() -> consumer.onApplicationEvent(kubernetesCommand()));
    assertEquals(0, client.upsertRouteIds.size());
    assertEquals(0, client.deleteRouteIds.size());
  }

  @Test
  void shouldSwallowClientFailures() {
    RecordingApisixAdminClient client = new RecordingApisixAdminClient();
    client.failUpsert = true;
    ApisixGatewayRouteSyncConsumer consumer = new ApisixGatewayRouteSyncConsumer(client, new ApisixRouteMapper(properties()));

    assertDoesNotThrow(() -> consumer.onApplicationEvent(upsertCommand()));
    assertEquals(1, client.upsertRouteIds.size());
  }

  private static ApisixGatewayProperties properties() {
    ApisixGatewayProperties properties = new ApisixGatewayProperties();
    properties.setRouteIdPrefix("rtg");
    return properties;
  }

  private static GatewayRouteSyncCommand upsertCommand() {
    return GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.UPSERT)
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .stableDomainHost("crm-dev.develop.example.com")
        .build();
  }

  private static GatewayRouteSyncCommand deleteCommand() {
    return GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.DELETE)
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .build();
  }

  private static GatewayRouteSyncCommand kubernetesCommand() {
    return GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.UPSERT)
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .stableDomainHost("crm-dev.develop.example.com")
        .build();
  }

  static class RecordingApisixAdminClient implements ApisixAdminClient {
    private final List<String> upsertRouteIds = new ArrayList<String>();
    private final List<String> deleteRouteIds = new ArrayList<String>();
    private boolean failUpsert;

    @Override
    public Try<Void> upsertRoute(String routeId, ApisixRoutePayload routePayload) {
      upsertRouteIds.add(routeId);
      return failUpsert ? Try.failure(new IllegalStateException("apisix unavailable")) : Try.success(null);
    }

    @Override
    public Try<Void> deleteRoute(String routeId) {
      deleteRouteIds.add(routeId);
      return Try.success(null);
    }
  }
}
