package com.ouroboros.dev.runtime.apisix.command;

import java.util.Objects;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.event.EventListener;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.apisix.client.ApisixAdminClient;
import com.ouroboros.dev.runtime.apisix.mapper.ApisixRouteMapper;
import com.ouroboros.dev.runtime.apisix.model.ApisixRoutePayload;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommand;
import com.ouroboros.dev.runtime.gateway.command.GatewayRouteSyncCommandType;

public class ApisixGatewayRouteSyncConsumer {
  private static final Log log = LogFactory.getLog(ApisixGatewayRouteSyncConsumer.class);

  private final ApisixAdminClient apisixAdminClient;
  private final ApisixRouteMapper apisixRouteMapper;

  public ApisixGatewayRouteSyncConsumer(ApisixAdminClient apisixAdminClient, ApisixRouteMapper apisixRouteMapper) {
    this.apisixAdminClient = Objects.requireNonNull(apisixAdminClient, "apisixAdminClient must not be null");
    this.apisixRouteMapper = Objects.requireNonNull(apisixRouteMapper, "apisixRouteMapper must not be null");
  }

  @EventListener
  public void onApplicationEvent(GatewayRouteSyncCommand command) {
    if (!apisixRouteMapper.supports(command) || command == null) {
      return;
    }
    if (command.getType() == GatewayRouteSyncCommandType.UPSERT) {
      upsert(command);
      return;
    }
    if (command.getType() == GatewayRouteSyncCommandType.DELETE) {
      delete(command);
    }
  }

  private void upsert(GatewayRouteSyncCommand command) {
    ApisixRoutePayload routePayload = apisixRouteMapper.toRoute(command);
    if (routePayload == null) {
      return;
    }
    handleResult(apisixAdminClient.upsertRoute(routePayload.getId(), routePayload), command);
  }

  private void delete(GatewayRouteSyncCommand command) {
    String routeId = apisixRouteMapper.routeId(command);
    if (routeId == null || routeId.trim().isEmpty()) {
      return;
    }
    handleResult(apisixAdminClient.deleteRoute(routeId), command);
  }

  private void handleResult(Try<Void> result, GatewayRouteSyncCommand command) {
    if (result != null && result.isFailure()) {
      log.warn("Failed to synchronize APISIX route for command "
          + command.getType()
          + " appName=" + command.getAppName()
          + " profile=" + command.getProfile()
          + " runtimeKind=" + command.getRuntimeKind()
          + " backendType=" + command.getBackendType(), result.getCause());
    }
  }
}
