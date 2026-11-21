package com.ouroboros.dev.runtime.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class DebugSessionBinding {
  private final String host;
  private final Integer port;
  private final DebugSessionBindingScope bindingScope;
  private final DebugTunnelTargetType targetType;
  private final TransportMode transportType;
  private final String clientAccessHint;
  private final Map<String, String> metadata;

  private DebugSessionBinding(Builder builder) {
    this.host = builder.host;
    this.port = builder.port;
    this.bindingScope = builder.bindingScope;
    this.targetType = resolveTargetType(builder.targetType, builder.metadata);
    this.transportType = resolveTransportType(builder.transportType, this.targetType, builder.metadata);
    this.clientAccessHint = builder.clientAccessHint;
    this.metadata = immutableCopy(builder.metadata);
  }

  public static Builder builder() {
    return new Builder();
  }

  public String getHost() {
    return host;
  }

  public Integer getPort() {
    return port;
  }

  public DebugSessionBindingScope getBindingScope() {
    return bindingScope;
  }

  public DebugTunnelTargetType getTargetType() {
    return targetType;
  }

  public TransportMode getTransportType() {
    return transportType;
  }

  public String getClientAccessHint() {
    return clientAccessHint;
  }

  public Map<String, String> getMetadata() {
    return metadata;
  }

  private static Map<String, String> immutableCopy(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<String, String>(values));
  }

  private static DebugTunnelTargetType resolveTargetType(DebugTunnelTargetType targetType, Map<String, String> metadata) {
    DebugTunnelTargetType metadataTargetType = DebugTunnelTargetType.fromMetadata(metadata);
    if (targetType != null) {
      return targetType;
    }
    return metadataTargetType == null ? DebugTunnelTargetType.defaultType() : metadataTargetType;
  }

  private static TransportMode resolveTransportType(
      TransportMode transportType,
      DebugTunnelTargetType targetType,
      Map<String, String> metadata
  ) {
    if (transportType != null) {
      return transportType;
    }
    TransportMode metadataTransportType = TransportMode.fromMetadata(metadata, "transportType");
    if (metadataTransportType != null) {
      return metadataTransportType;
    }
    DebugTunnelTargetType resolvedTargetType = targetType == null ? DebugTunnelTargetType.defaultType() : targetType;
    return resolvedTargetType.getTransportMode();
  }

  public static class Builder {
    private String host;
    private Integer port;
    private DebugSessionBindingScope bindingScope;
    private DebugTunnelTargetType targetType;
    private TransportMode transportType;
    private String clientAccessHint;
    private Map<String, String> metadata;

    private Builder() {
    }

    public Builder host(String host) {
      this.host = host;
      return this;
    }

    public Builder port(Integer port) {
      this.port = port;
      return this;
    }

    public Builder bindingScope(DebugSessionBindingScope bindingScope) {
      this.bindingScope = bindingScope;
      return this;
    }

    public Builder targetType(DebugTunnelTargetType targetType) {
      this.targetType = targetType;
      return this;
    }

    public Builder transportType(TransportMode transportType) {
      this.transportType = transportType;
      return this;
    }

    public Builder clientAccessHint(String clientAccessHint) {
      this.clientAccessHint = clientAccessHint;
      return this;
    }

    public Builder metadata(Map<String, String> metadata) {
      this.metadata = metadata;
      return this;
    }

    public DebugSessionBinding build() {
      return new DebugSessionBinding(this);
    }
  }
}
