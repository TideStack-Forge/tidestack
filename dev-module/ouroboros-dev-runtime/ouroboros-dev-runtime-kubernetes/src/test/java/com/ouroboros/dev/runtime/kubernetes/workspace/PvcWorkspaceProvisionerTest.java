package com.ouroboros.dev.runtime.kubernetes.workspace;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.dev.runtime.service.WorkspaceProvisioner;

class PvcWorkspaceProvisionerTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldWriteWorkspaceContentIntoSharedPvcRootAndReturnPvcWorkspaceRef() throws Exception {
    Path devRoot = tempDir.resolve("dev");
    Path debugSupportRoot = tempDir.resolve("debug-support");
    Path mountedWorkspaceRoot = tempDir.resolve("mounted-pvc");
    java.nio.file.Files.createDirectories(devRoot.resolve("crm"));
    java.nio.file.Files.createDirectories(debugSupportRoot);
    java.nio.file.Files.write(devRoot.resolve("crm/model.json"), "{\"type\":\"object\"}".getBytes(StandardCharsets.UTF_8));
    java.nio.file.Files.write(debugSupportRoot.resolve("debug.jar"), "debug".getBytes(StandardCharsets.UTF_8));

    KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
    properties.setNamespace("dev-space");
    properties.setSharedPvcName("dev-runtime-pvc");
    properties.setMountedWorkspaceRoot(mountedWorkspaceRoot.toString());

    PvcWorkspaceProvisioner provisioner = new PvcWorkspaceProvisioner(workspaceContext(devRoot, debugSupportRoot), properties);
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .build();

    WorkspaceRef workspaceRef = provisioner.prepare(spec, WorkspaceProvisioner.Operation.CREATE).get();

    assertTrue(provisioner.supports(spec));
    assertEquals(WorkspaceMode.PVC, workspaceRef.getWorkspaceMode());
    assertEquals("dev-runtime-pvc", workspaceRef.getPvcName());
    assertEquals("dev-space", workspaceRef.getNamespace());
    assertEquals("workspace/run/crm/alice", workspaceRef.getSubPath());
    assertTrue(java.nio.file.Files.exists(mountedWorkspaceRoot.resolve("workspace/run/crm/alice/metadata/model.json")));
    assertTrue(java.nio.file.Files.exists(mountedWorkspaceRoot.resolve("workspace/run/crm/alice/extend/debug.jar")));
  }

  @Test
  void shouldExcludeVcsSecretsFromDevHelperWorkspace() throws Exception {
    Path devRoot = tempDir.resolve("dev-helper-dev");
    Path debugSupportRoot = tempDir.resolve("dev-helper-debug-support");
    Path developSupportRoot = tempDir.resolve("dev-helper-develop-support");
    Path mountedWorkspaceRoot = tempDir.resolve("dev-helper-mounted-pvc");
    java.nio.file.Files.createDirectories(devRoot);
    java.nio.file.Files.createDirectories(debugSupportRoot);
    java.nio.file.Files.createDirectories(devRoot.resolve("crm/business-model"));
    java.nio.file.Files.createDirectories(developSupportRoot.resolve("vcs/crm"));
    java.nio.file.Files.createDirectories(mountedWorkspaceRoot);
    java.nio.file.Files.write(devRoot.resolve("crm/business-model/order.business.json"), "{\"name\":\"order\"}".getBytes(StandardCharsets.UTF_8));
    java.nio.file.Files.write(developSupportRoot.resolve("helper.json"), "helper".getBytes(StandardCharsets.UTF_8));
    java.nio.file.Files.write(developSupportRoot.resolve("vcs/crm/credentials.json"), "secret".getBytes(StandardCharsets.UTF_8));

    KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
    properties.setNamespace("dev-space");
    properties.setSharedPvcName("dev-runtime-pvc");
    properties.setMountedWorkspaceRoot(mountedWorkspaceRoot.toString());

    PvcWorkspaceProvisioner provisioner = new PvcWorkspaceProvisioner(workspaceContext(devRoot, debugSupportRoot, developSupportRoot), properties);
    RuntimeSpec spec = RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .build();

    WorkspaceRef workspaceRef = provisioner.prepare(spec, WorkspaceProvisioner.Operation.CREATE).get();

    assertEquals("workspace/run/crm/dev-helper", workspaceRef.getSubPath());
    assertTrue(java.nio.file.Files.exists(mountedWorkspaceRoot.resolve("workspace/run/crm/dev-helper/metadata/business-model/order.business.json")));
    assertTrue(java.nio.file.Files.exists(mountedWorkspaceRoot.resolve("workspace/run/crm/dev-helper/extend/helper.json")));
    assertFalse(java.nio.file.Files.exists(mountedWorkspaceRoot.resolve("workspace/run/crm/dev-helper/extend/vcs/crm/credentials.json")));
  }

  private static DevelopWorkspaceContext workspaceContext(Path devRoot, Path debugSupportRoot) {
    return workspaceContext(devRoot, debugSupportRoot, devRoot.getParent().resolve("develop-support"));
  }

  private static DevelopWorkspaceContext workspaceContext(Path devRoot, Path debugSupportRoot, Path developSupportRoot) {
    return new DevelopWorkspaceContext() {
      @Override
      public String getRootDir() {
        return devRoot.getParent().toString();
      }

      @Override
      public String getDevRootDir() {
        return devRoot.toString();
      }

      @Override
      public String getRunRootDir() {
        return devRoot.getParent().resolve("run").toString();
      }

      @Override
      public String getDebugSupportFilesDir() {
        return debugSupportRoot.toString();
      }

      @Override
      public String getDevelopSupportFilesDir() {
        return developSupportRoot.toString();
      }
    };
  }
}
