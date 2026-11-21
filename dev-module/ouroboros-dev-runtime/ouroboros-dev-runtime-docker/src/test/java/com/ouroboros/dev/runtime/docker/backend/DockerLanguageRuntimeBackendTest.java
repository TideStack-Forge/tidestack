package com.ouroboros.dev.runtime.docker.backend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.ouroboros.dev.runtime.docker.support.DockerRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.*;

class DockerLanguageRuntimeBackendTest {
  @Test void usesTheExistingLifecycleAndLogServiceForAnImmutableLanguageWorkload() {
    var containers = new DockerRuntimeTestSupport.FakeContainerService();
    var backend = new DockerLanguageRuntimeBackend(containers);
    var spec = RuntimeSpec.builder().workloadId("groovy-environment").profile("lease").image("language:verified")
        .runtimeKind(RuntimeKind.LANGUAGE_SERVICE).backendType(BackendType.DOCKER).workspaceMode(WorkspaceMode.NONE)
        .requestedPorts(Map.of("language", 40123)).build();
    var key = RuntimeIdentityKey.from(spec);
    assertTrue(backend.supports(spec));
    assertFalse(backend.supports(RuntimeSpec.builder().appName("crm").runtimeKind(RuntimeKind.TEST_APP).backendType(BackendType.DOCKER).build()));
    var created = backend.create(spec).get();
    assertNull(created.getAppName()); assertEquals("groovy-environment", created.getWorkloadId());
    assertEquals(RuntimeStatus.STOPPED, created.getStatus());
    assertEquals(RuntimeStatus.RUNNING, backend.start(spec).get().getStatus());
    assertEquals("tcp://127.0.0.1:40123", backend.getStatus(key).get().getAccessRef().get("language"));
    assertEquals(RuntimeStatus.RUNNING, backend.restart(spec, 2).get().getStatus());
    containers.putLogs("ouroboros-language-groovy-environment-lease", List.of("compiler ready"));
    assertEquals(List.of("compiler ready"), backend.getLogs(key).get());
    assertTrue(backend.reloadWorkspace(spec, 2).isFailure());
    assertEquals(RuntimeStatus.STOPPED, backend.stop(key).get().getStatus());
    backend.delete(key).get();
    assertTrue(backend.getStatus(key).isFailure());
  }
}
