package com.ouroboros.dev.runtime.service;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.TransportMode;

public interface TunnelTransport {
  TransportMode getTransportMode();

  Try<Void> open(DebugSession session, DebugConnector connector);

  Try<Void> close(String sessionId);

  Try<DebugSessionBinding> getAccessInfo(String sessionId);
}
