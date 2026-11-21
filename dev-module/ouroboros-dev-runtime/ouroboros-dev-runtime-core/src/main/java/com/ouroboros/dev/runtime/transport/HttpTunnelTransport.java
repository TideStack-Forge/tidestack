package com.ouroboros.dev.runtime.transport;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.DebugSessionBindingScope;
import com.ouroboros.dev.runtime.model.TransportMode;
import com.ouroboros.dev.runtime.service.DebugConnector;
import com.ouroboros.dev.runtime.service.TunnelTransport;

public class HttpTunnelTransport implements TunnelTransport {
  private final String proxyBasePath;
  private final ConcurrentHashMap<String, HttpTunnelSession> tunnelSessions = new ConcurrentHashMap<String, HttpTunnelSession>();

  public HttpTunnelTransport(String proxyBasePath) {
    this.proxyBasePath = proxyBasePath == null || proxyBasePath.trim().isEmpty() ? "/api/tunnel" : proxyBasePath;
  }

  @Override
  public TransportMode getTransportMode() {
    return TransportMode.HTTP;
  }

  @Override
  public Try<Void> open(DebugSession session, DebugConnector connector) {
    if (session == null || session.getSessionId() == null || session.getSessionId().trim().isEmpty()) {
      return Try.failure(new IllegalArgumentException("sessionId is required"));
    }
    if (connector == null) {
      return Try.failure(new IllegalArgumentException("debug connector is required"));
    }
    if (tunnelSessions.containsKey(session.getSessionId())) {
      return Try.failure(new IllegalStateException("HTTP tunnel session already exists"));
    }
    HttpTunnelSession tunnelSession = new HttpTunnelSession(session, connector);
    HttpTunnelSession existing = tunnelSessions.putIfAbsent(session.getSessionId(), tunnelSession);
    if (existing != null) {
      return Try.failure(new IllegalStateException("HTTP tunnel session already exists"));
    }
    return Try.success(null);
  }

  @Override
  public Try<Void> close(String sessionId) {
    tunnelSessions.remove(sessionId);
    return Try.success(null);
  }

  @Override
  public Try<DebugSessionBinding> getAccessInfo(String sessionId) {
    HttpTunnelSession tunnelSession = tunnelSessions.get(sessionId);
    if (tunnelSession == null) {
      return Try.failure(new IllegalStateException("HTTP tunnel session not found"));
    }
    String proxyPath = proxyBasePath + "/" + sessionId;
    DebugSessionBinding binding = DebugSessionBinding.builder()
        .bindingScope(DebugSessionBindingScope.LOOPBACK_ONLY)
        .targetType(tunnelSession.session.getTargetType())
        .transportType(getTransportMode())
        .clientAccessHint("Proxy path: " + proxyPath + "/**")
        .metadata(bindingMetadata(tunnelSession.session, proxyPath))
        .build();
    return Try.success(binding);
  }

  public Try<HttpTunnelSession> getSession(String sessionId) {
    HttpTunnelSession tunnelSession = tunnelSessions.get(sessionId);
    if (tunnelSession == null) {
      return Try.failure(new IllegalStateException("HTTP tunnel session not found"));
    }
    return Try.success(tunnelSession);
  }

  private Map<String, String> bindingMetadata(DebugSession session, String proxyPath) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    metadata.put("proxyBasePath", proxyBasePath);
    metadata.put("proxyPath", proxyPath);
    metadata.put("targetType", session.getTargetType().name());
    metadata.put("targetPort", String.valueOf(session.getTargetType().getTargetPort()));
    metadata.put("transportType", getTransportMode().name());
    return metadata;
  }

  public static class HttpTunnelSession {
    private final DebugSession session;
    private final DebugConnector connector;

    private HttpTunnelSession(DebugSession session, DebugConnector connector) {
      this.session = session;
      this.connector = connector;
    }

    public DebugSession getSession() {
      return session;
    }

    public DebugConnector getConnector() {
      return connector;
    }
  }
}
