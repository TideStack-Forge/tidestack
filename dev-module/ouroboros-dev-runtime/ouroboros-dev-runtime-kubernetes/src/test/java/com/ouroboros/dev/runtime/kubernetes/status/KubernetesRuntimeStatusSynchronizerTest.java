package com.ouroboros.dev.runtime.kubernetes.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;

import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class KubernetesRuntimeStatusSynchronizerTest {

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
  void shouldMapClusterResourcesIntoUnifiedRuntimeStatusesWithoutDirectApiExposure() {
    KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(
        KubernetesRuntimeTestSupport.properties(),
        KubernetesRuntimeTestSupport.fixedClock()
    );
    KubernetesRuntimeStatusSynchronizer synchronizer = new KubernetesRuntimeStatusSynchronizer(
        server.getClient(),
        KubernetesRuntimeTestSupport.properties(),
        new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher()
    );
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();

    assertEquals(RuntimeStatus.NOT_FOUND, synchronizer.reconcile(spec).getStatus());

    server.getClient().services().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.service(spec)).createOrReplace();
    Deployment stoppedDeployment = new DeploymentBuilder(factory.deployment(spec, null))
        .editSpec()
        .withReplicas(Integer.valueOf(0))
        .endSpec()
        .build();
    server.getClient().apps().deployments().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(stoppedDeployment).createOrReplace();

    RuntimeInstance stopped = synchronizer.reconcile(spec);
    assertEquals(RuntimeStatus.STOPPED, stopped.getStatus());

    server.getClient().apps().deployments().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.deployment(spec, null)).createOrReplace();
    server.getClient().pods().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(KubernetesRuntimeTestSupport.readyPod(spec)).createOrReplace();

    RuntimeInstance running = synchronizer.reconcile(spec);
    assertEquals(RuntimeStatus.RUNNING, running.getStatus());
    assertTrue(running.getWebEndpoint().startsWith("http://127.0.0.1:"));
    assertEquals(null, running.getApiEndpoint());
    assertEquals(null, running.getDebugEndpoint());
    assertFalse(running.getAccessRef().containsKey("apiNodePort"));
    assertFalse(running.getAccessRef().containsKey("debugNodePort"));
  }

  @Test
  void shouldPublishStatusChangedEventWhenReconcileFindsRunningRuntime() {
    KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher publisher = new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher();
    KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(
        KubernetesRuntimeTestSupport.properties(),
        KubernetesRuntimeTestSupport.fixedClock()
    );
    KubernetesRuntimeStatusSynchronizer synchronizer = new KubernetesRuntimeStatusSynchronizer(
        server.getClient(),
        KubernetesRuntimeTestSupport.properties(),
        publisher
    );
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();

    server.getClient().services().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.service(spec)).createOrReplace();
    server.getClient().apps().deployments().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(factory.deployment(spec, 3L)).createOrReplace();
    server.getClient().pods().inNamespace(KubernetesRuntimeTestSupport.namespace()).resource(KubernetesRuntimeTestSupport.readyPod(spec)).createOrReplace();

    RuntimeInstance runtime = synchronizer.reconcileAndPublish(spec);

    assertEquals(RuntimeStatus.RUNNING, runtime.getStatus());
    assertEquals(1, publisher.events().size());
    assertEquals(DevRuntimeEventType.RUNTIME_STATUS_CHANGED, publisher.events().get(0).getType());
  }
}
