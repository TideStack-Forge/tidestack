package com.ouroboros.dev.runtime.service;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.DebugSession;
import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;

public interface DebugSessionService {
  Try<DebugSession> open(OpenDebugSessionCommand command);

  Try<DebugSession> get(String sessionId);

  Try<Void> close(String sessionId);
}
