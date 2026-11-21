package com.ouroboros.dev.runtime.kubernetes.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;

class KubernetesResourceNameFactoryTest {

  @Test
  void shouldBuildStableNamesPerRuntimeIdentity() {
    RuntimeIdentityKey key = RuntimeIdentityKey.of("crm", "alice", RuntimeKind.TEST_APP, BackendType.KUBERNETES);

    assertEquals("devrt-test-app-crm-alice", KubernetesResourceNameFactory.deploymentName(key));
    assertEquals("devrt-test-app-crm-alice-svc", KubernetesResourceNameFactory.serviceName(key));
    assertEquals("devrt-test-app-crm-alice-ing", KubernetesResourceNameFactory.ingressName(key));

    Map<String, String> labels = KubernetesResourceNameFactory.labels(key);
    assertEquals("crm", labels.get("ouroboros.dev/runtime-app"));
    assertEquals("alice", labels.get("ouroboros.dev/runtime-profile"));
    assertEquals("TEST_APP", labels.get("ouroboros.dev/runtime-kind"));
  }
}
