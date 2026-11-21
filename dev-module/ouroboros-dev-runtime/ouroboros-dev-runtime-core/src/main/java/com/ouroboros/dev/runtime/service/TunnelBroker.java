package com.ouroboros.dev.runtime.service;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.DebugSessionBinding;
import com.ouroboros.dev.runtime.model.TransportMode;

public interface TunnelBroker {
  Try<DebugSessionBinding> open(DebugSession session, DebugConnector connector, TransportMode transportMode);

  Try<Void> close(String sessionId);
}
