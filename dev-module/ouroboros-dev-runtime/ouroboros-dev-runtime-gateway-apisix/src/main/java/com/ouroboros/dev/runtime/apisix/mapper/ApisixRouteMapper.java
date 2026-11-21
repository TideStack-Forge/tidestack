package com.ouroboros.dev.runtime.apisix.mapper;

import java.util.Collections;

import com.ouroboros.dev.runtime.apisix.config.ApisixGatewayProperties;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;

public class ApisixRouteMapper {
  private final ApisixGatewayProperties properties;

  public ApisixRouteMapper(ApisixGatewayProperties properties) {
    this.properties = properties;
  }

  public boolean supports(GatewayRouteSyncCommand command) {
    return command != null
        && hasText(command.getAppName())
        && hasText(command.getProfile())
        && command.getBackendType() == BackendType.DOCKER
        && command.getRuntimeKind() == RuntimeKind.TEST_APP;
  }

  public String routeId(GatewayRouteSyncCommand command) {
    if (!supports(command) || !hasText(properties.getRouteIdPrefix())) {
      return null;
    }
    return sanitize(properties.getRouteIdPrefix())
        + "-"
        + sanitize(command.getBackendType().name())
        + "-"
        + sanitize(command.getRuntimeKind().name())
        + "-"
        + sanitize(command.getAppName())
        + "-"
        + sanitize(command.getProfile());
  }

  public ApisixRoutePayload toRoute(GatewayRouteSyncCommand command) {
    if (!supports(command) || !hasText(command.getStableDomainHost())) {
      return null;
    }
    String routeId = routeId(command);
    if (!hasText(routeId)) {
      return null;
    }
    ApisixRoutePayload.Upstream upstream = new ApisixRoutePayload.Upstream();
    upstream.getNodes().put("ouroboros-test-app-" + command.getAppName() + "-" + command.getProfile() + ":80", Integer.valueOf(1));

    ApisixRoutePayload payload = new ApisixRoutePayload();
    payload.setId(routeId);
    payload.setUri("/*");
    payload.setHosts(Collections.singletonList(command.getStableDomainHost()));
    payload.setUpstream(upstream);
    return payload;
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String sanitize(String value) {
    if (!hasText(value)) {
      return "x";
    }
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
  }
}
