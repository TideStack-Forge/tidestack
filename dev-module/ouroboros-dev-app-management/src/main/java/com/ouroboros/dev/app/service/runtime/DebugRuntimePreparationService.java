package com.ouroboros.dev.app.service.runtime;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.OpenDebugSessionCommand;

public interface DebugRuntimePreparationService {

  Try<OpenDebugSessionCommand> prepare(String sessionId, OpenDebugSessionCommand command, String uploadBatchId);
}
