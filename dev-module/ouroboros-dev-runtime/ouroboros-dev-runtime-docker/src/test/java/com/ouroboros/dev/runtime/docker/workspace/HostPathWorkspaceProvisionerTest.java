package com.ouroboros.dev.runtime.docker.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;

class HostPathWorkspaceProvisionerTest {

  @Test
  void shouldBuildHostPathFromConfiguredRunRoot() {
    HostPathWorkspaceProvisioner provisioner = new HostPathWorkspaceProvisioner("/host/workspace/run");

    WorkspaceRef workspaceRef = provisioner.prepare(
        DockerRuntimeTestSupport.testAppSpec(),
        WorkspaceProvisioner.Operation.CREATE
    ).get();

    assertTrue(provisioner.supports(DockerRuntimeTestSupport.testAppSpec()));
    assertEquals(WorkspaceMode.HOST_PATH, workspaceRef.getWorkspaceMode());
    assertEquals("/host/workspace/run/crm/alice", workspaceRef.getHostPath());
  }
}
