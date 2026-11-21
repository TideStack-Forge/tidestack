package com.ouroboros.dev.runtime.apisix.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommandType;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class ApisixRouteMapperTest {

  @Test
  void shouldMapWebStableDomainCommandToDeterministicApisixRoute() {
    ApisixGatewayProperties properties = properties();
    ApisixRouteMapper mapper = new ApisixRouteMapper(properties);

    ApisixRoutePayload payload = mapper.toRoute(upsertCommand());

    assertEquals("rtg-docker-test-app-crm-dev", payload.getId());
    assertEquals("/*", payload.getUri());
    assertEquals("crm-dev.develop.example.com", payload.getHosts().get(0));
    assertEquals(Integer.valueOf(1), payload.getUpstream().getNodes().get("ouroboros-test-app-crm-dev:80"));
  }

  @Test
  void shouldIgnoreApiEndpointForFirstVersionWebRouteMapping() {
    ApisixGatewayProperties properties = properties();
    ApisixRouteMapper mapper = new ApisixRouteMapper(properties);

    ApisixRoutePayload payload = mapper.toRoute(upsertCommand());

    assertEquals(1, payload.getHosts().size());
    assertEquals(1, payload.getUpstream().getNodes().size());
  }

  private static GatewayRouteSyncCommand upsertCommand() {
    return GatewayRouteSyncCommand.builder()
        .type(GatewayRouteSyncCommandType.UPSERT)
        .appName("crm")
        .profile("dev")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .runtimeId("rt-crm-dev")
        .stableDomainHost("crm-dev.develop.example.com")
        .webEndpoint("https://crm-dev.develop.example.com")
        .apiEndpoint("https://crm-dev-api.develop.example.com")
        .build();
  }

  private static ApisixGatewayProperties properties() {
    ApisixGatewayProperties properties = new ApisixGatewayProperties();
    properties.setRouteIdPrefix("rtg");
    return properties;
  }
}
