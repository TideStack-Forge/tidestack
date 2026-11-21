package com.ouroboros.dev.runtime.docker.workspace;

import java.nio.file.Paths;

import io.vavr.control.Try;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;

public class HostPathWorkspaceProvisioner implements WorkspaceProvisioner {
  private final String hostRunRootDir;

  public HostPathWorkspaceProvisioner(String hostRunRootDir) {
    this.hostRunRootDir = hostRunRootDir;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.DOCKER && spec.getWorkspaceMode() == WorkspaceMode.HOST_PATH;
  }

  @Override
  public Try<WorkspaceRef> prepare(RuntimeSpec spec, Operation operation) {
    return Try.success(WorkspaceRef.builder()
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .hostPath(Paths.get(hostRunRootDir, spec.getAppName(), spec.getProfile()).toString())
        .build());
  }
}
