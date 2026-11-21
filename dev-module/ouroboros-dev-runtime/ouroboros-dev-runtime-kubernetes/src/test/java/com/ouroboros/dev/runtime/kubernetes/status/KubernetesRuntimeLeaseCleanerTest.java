package com.ouroboros.dev.runtime.kubernetes.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

class KubernetesRuntimeLeaseCleanerTest {

  private KubernetesMockServerSupport server;

  @BeforeEach
  void setUp() {
    server = KubernetesMockServerSupport.crudHttps();
    server.start();
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void shouldDeleteExpiredRuntimeResourcesAndRetainedEndpoints() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();
    KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(
        KubernetesRuntimeTestSupport.properties(),
        KubernetesRuntimeTestSupport.fixedClock()
    );
    server.getClient().services().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.service(spec)).createOrReplace();
    server.getClient().apps().deployments().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.deployment(spec, null)).createOrReplace();

    Clock expiredClock = Clock.offset(KubernetesRuntimeTestSupport.fixedClock(), spec.getTtl().plus(Duration.ofSeconds(1)));
    KubernetesRuntimeLeaseCleaner cleaner = new KubernetesRuntimeLeaseCleaner(
        server.getClient(),
        KubernetesRuntimeTestSupport.properties(),
        expiredClock
    );

    assertEquals(1, cleaner.cleanExpired().size());
    assertNull(server.getClient().services()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(spec)))
        .get());
    assertNull(server.getClient().apps().deployments()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.deploymentName(RuntimeIdentityKey.from(spec)))
        .get());
  }
}
