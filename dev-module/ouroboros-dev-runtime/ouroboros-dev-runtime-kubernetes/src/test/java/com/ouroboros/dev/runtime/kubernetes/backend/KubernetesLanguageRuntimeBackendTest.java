package com.ouroboros.dev.runtime.kubernetes.backend;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.model.*;

class KubernetesLanguageRuntimeBackendTest {
  @Test void createsAnIndependentInternalWorkloadWithLimitsAndDeletesItsResources() {
    try (var server = KubernetesMockServerSupport.crudHttps()) {
      server.start(); var client = server.getClient();
      var properties = new KubernetesRuntimeProperties(); properties.setNamespace("default");
      var backend = new KubernetesLanguageRuntimeBackend(client, properties);
      var spec = RuntimeSpec.builder().workloadId("groovy-environment").profile("lease001").image("language:verified")
          .runtimeKind(RuntimeKind.LANGUAGE_SERVICE).backendType(BackendType.KUBERNETES).workspaceMode(WorkspaceMode.NONE)
          .ttl(java.time.Duration.ofMinutes(1)).resourceLimits(Map.of("memoryBytes", "1073741824", "nanoCpus", "2000000000")).build();
      var created = backend.create(spec).get();
      assertNull(created.getAppName()); assertEquals("groovy-environment", created.getWorkloadId());
      assertEquals(RuntimeStatus.STOPPED, created.getStatus());
      var deployment = client.apps().deployments().inNamespace("default").list().getItems().getFirst();
      var container = deployment.getSpec().getTemplate().getSpec().getContainers().getFirst();
      assertEquals("language:verified", container.getImage());
      assertEquals("1073741824", container.getResources().getLimits().get("memory").getAmount());
      assertTrue(deployment.getSpec().getTemplate().getSpec().getVolumes().isEmpty());
      assertEquals("ClusterIP", client.services().inNamespace("default").list().getItems().getFirst().getSpec().getType());
      assertEquals(RuntimeStatus.STARTING, backend.start(spec).get().getStatus());
      assertEquals(RuntimeStatus.STOPPED, backend.stop(RuntimeIdentityKey.from(spec)).get().getStatus());
      var cleaner = new com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeLeaseCleaner(client, properties,
          java.time.Clock.fixed(java.time.Instant.now().plusSeconds(120), java.time.ZoneOffset.UTC));
      assertEquals(java.util.List.of(RuntimeIdentityKey.from(spec)), cleaner.cleanExpired());
      backend.delete(RuntimeIdentityKey.from(spec)).get();
      assertTrue(client.apps().deployments().inNamespace("default").list().getItems().isEmpty());
      assertTrue(client.services().inNamespace("default").list().getItems().isEmpty());
    }
  }
}
