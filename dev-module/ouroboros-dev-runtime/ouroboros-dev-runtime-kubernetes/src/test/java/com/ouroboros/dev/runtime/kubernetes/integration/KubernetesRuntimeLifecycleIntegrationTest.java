package com.ouroboros.dev.runtime.kubernetes.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Service;

import com.ouroboros.dev.runtime.kubernetes.backend.KubernetesTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeLeaseCleaner;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;

class KubernetesRuntimeLifecycleIntegrationTest {

  private KubernetesMockServerSupport server;
  private KubernetesRuntimeProperties properties;
  private KubernetesTestAppRuntimeBackend backend;

  @BeforeEach
  void setUp() {
    server = KubernetesMockServerSupport.crudHttps();
    server.start();
    properties = KubernetesRuntimeTestSupport.properties();
    backend = new KubernetesTestAppRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(properties, KubernetesRuntimeTestSupport.fixedClock()),
        new KubernetesRuntimeStatusSynchronizer(
            server.getClient(),
            properties,
            new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher()
        ),
        properties
    );
  }

  @AfterEach
  void tearDown() {
    server.close();
  }

  @Test
  void shouldDriveTempPortRuntimeAcrossLifecycleOperations() {
    RuntimeSpec spec = testAppSpec("integration-temp", ExposureStrategy.TEMP_PORT, Duration.ofMinutes(30));
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);

    RuntimeInstance created = backend.create(spec).get();
    Integer retainedNodePort = service(spec).getSpec().getPorts().get(0).getNodePort();
    assertEquals(RuntimeStatus.STARTING, created.getStatus());

    markReady(spec);
    RuntimeInstance running = backend.getStatus(key).get();
    assertEquals(RuntimeStatus.RUNNING, running.getStatus());
    assertTrue(running.getWebEndpoint().endsWith(String.valueOf(retainedNodePort)));

    RuntimeInstance stopped = backend.stop(key).get();
    assertEquals(RuntimeStatus.STOPPED, stopped.getStatus());
    deleteReadyPod(spec);

    RuntimeInstance started = backend.start(spec).get();
    assertEquals(RuntimeStatus.STARTING, started.getStatus());
    markReady(spec);
    RuntimeInstance startedRunning = backend.getStatus(key).get();
    assertEquals(RuntimeStatus.RUNNING, startedRunning.getStatus());
    assertTrue(startedRunning.getWebEndpoint().endsWith(String.valueOf(retainedNodePort)));

    deleteReadyPod(spec);
    RuntimeInstance restarted = backend.restart(spec, 7L).get();
    assertEquals(RuntimeStatus.STARTING, restarted.getStatus());
    markReady(spec);
    RuntimeInstance restartedRunning = backend.getStatus(key).get();
    assertEquals(RuntimeStatus.RUNNING, restartedRunning.getStatus());
    assertEquals(Long.valueOf(7L), restartedRunning.getRuntimeGeneration());
    assertTrue(restartedRunning.getWebEndpoint().endsWith(String.valueOf(retainedNodePort)));

    backend.delete(key).get();
    RuntimeInstance deleted = backend.getStatus(key).get();
    assertEquals(RuntimeStatus.NOT_FOUND, deleted.getStatus());
  }

  @Test
  void shouldKeepStableDomainRuntimeWhileCleaningExpiredRuntime() {
    RuntimeSpec stableSpec = testAppSpec("integration-stable", ExposureStrategy.STABLE_DOMAIN, Duration.ofMinutes(30));
    RuntimeIdentityKey stableKey = RuntimeIdentityKey.from(stableSpec);
    RuntimeSpec expiringSpec = testAppSpec("integration-expiring", ExposureStrategy.TEMP_PORT, Duration.ofSeconds(5));
    RuntimeIdentityKey expiringKey = RuntimeIdentityKey.from(expiringSpec);

    backend.create(stableSpec).get();
    markReady(stableSpec);
    RuntimeInstance stableRuntime = backend.getStatus(stableKey).get();
    assertEquals(RuntimeStatus.RUNNING, stableRuntime.getStatus());
    assertEquals("https://crm-integration-stable.develop.example.com", stableRuntime.getWebEndpoint());

    backend.create(expiringSpec).get();

    KubernetesRuntimeLeaseCleaner cleaner = new KubernetesRuntimeLeaseCleaner(
        server.getClient(),
        properties,
        Clock.offset(KubernetesRuntimeTestSupport.fixedClock(), expiringSpec.getTtl().plus(Duration.ofSeconds(1)))
    );

    assertEquals(1, cleaner.cleanExpired().size());

    RuntimeInstance expiredRuntime = backend.getStatus(expiringKey).get();
    assertEquals(RuntimeStatus.NOT_FOUND, expiredRuntime.getStatus());

    RuntimeInstance retainedStable = backend.getStatus(stableKey).get();
    assertEquals(RuntimeStatus.RUNNING, retainedStable.getStatus());
    assertEquals("https://crm-integration-stable.develop.example.com", retainedStable.getWebEndpoint());
  }

  private RuntimeSpec testAppSpec(String profile, ExposureStrategy exposureStrategy, Duration ttl) {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile(profile)
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(exposureStrategy)
        .image("ouroboros/test-app:latest")
        .ttl(ttl)
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName("dev-runtime-pvc")
            .namespace(KubernetesRuntimeTestSupport.namespace())
            .subPath("workspace/run/crm/" + profile)
            .build())
        .build();
  }

  private void markReady(RuntimeSpec spec) {
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .resource(KubernetesRuntimeTestSupport.readyPod(spec))
        .createOrReplace();
  }

  private void deleteReadyPod(RuntimeSpec spec) {
    server.getClient().pods()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesRuntimeTestSupport.readyPod(spec).getMetadata().getName())
        .delete();
  }

  private Service service(RuntimeSpec spec) {
    return server.getClient().services()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(spec)))
        .get();
  }
}
