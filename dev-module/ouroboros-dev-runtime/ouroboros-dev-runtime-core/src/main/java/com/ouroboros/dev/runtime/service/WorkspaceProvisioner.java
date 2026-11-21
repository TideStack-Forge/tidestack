package com.ouroboros.dev.runtime.service;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceRef;

public interface WorkspaceProvisioner {
  enum Operation {
    CREATE,
    START,
    RELOAD
  }

  boolean supports(RuntimeSpec spec);

  Try<WorkspaceRef> prepare(RuntimeSpec spec, Operation operation);
}
