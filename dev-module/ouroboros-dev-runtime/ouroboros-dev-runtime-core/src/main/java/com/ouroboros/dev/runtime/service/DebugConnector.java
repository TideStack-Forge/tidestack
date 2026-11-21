package com.ouroboros.dev.runtime.service;

import java.net.Socket;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.DebugSession;

public interface DebugConnector {
  BackendType getBackendType();

  Try<Socket> connect(DebugSession session);
}
