package com.ouroboros.dev.runtime.model;

import java.util.Locale;
import java.util.Map;

public enum DebugTunnelTargetType {
  DEBUG(5005, TransportMode.TCP),
  BACKEND_API(8080, TransportMode.HTTP);

  private final int targetPort;
  private final TransportMode transportMode;

  DebugTunnelTargetType(int targetPort, TransportMode transportMode) {
    this.targetPort = targetPort;
    this.transportMode = transportMode;
  }

  public int getTargetPort() {
    return targetPort;
  }

  public TransportMode getTransportMode() {
    return transportMode;
  }

  public static DebugTunnelTargetType defaultType() {
    return DEBUG;
  }

  public static DebugTunnelTargetType fromMetadata(Map<String, String> metadata) {
    return fromName(metadata == null ? null : metadata.get("targetType"));
  }

  public static DebugTunnelTargetType fromName(String value) {
    if (value == null || value.trim().isEmpty()) {
      return null;
    }
    return valueOf(value.trim().toUpperCase(Locale.ROOT));
  }
}
