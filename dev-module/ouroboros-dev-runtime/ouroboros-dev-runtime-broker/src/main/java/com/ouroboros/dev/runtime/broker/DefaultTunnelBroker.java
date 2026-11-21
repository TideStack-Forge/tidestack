package com.ouroboros.dev.runtime.broker;

import java.util.List;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelBroker;
import com.ouroboros.dev.runtime.service.TunnelTransport;

public class DefaultTunnelBroker implements TunnelBroker {
  private final List<TunnelTransport> tunnelTransports;

  public DefaultTunnelBroker(List<TunnelTransport> tunnelTransports) {
    this.tunnelTransports = tunnelTransports;
  }

  @Override
  public Try<DebugSessionBinding> open(DebugSession session, DebugConnector connector, TransportMode transportMode) {
    return resolveTransport(transportMode)
        .flatMap(transport -> transport.open(session, connector)
            .flatMap(ignored -> transport.getAccessInfo(session.getSessionId())));
  }

  @Override
  public Try<Void> close(String sessionId) {
    return Try.sequence(tunnelTransports.stream()
        .map(transport -> transport.close(sessionId))
        .collect(java.util.stream.Collectors.toList()))
        .map(ignored -> null);
  }

  private Try<TunnelTransport> resolveTransport(TransportMode transportMode) {
    if (transportMode == null) {
      return Try.failure(new IllegalArgumentException("transportMode is required"));
    }
    return tunnelTransports.stream()
        .filter(transport -> transportMode == transport.getTransportMode())
        .findFirst()
        .map(Try::success)
        .orElseGet(() -> Try.failure(new IllegalStateException("no tunnel transport for " + transportMode.name())));
  }
}
