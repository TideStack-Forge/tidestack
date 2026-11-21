package com.ouroboros.dev.runtime.kubernetes.backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.mockwebserver.http.RecordedRequest;

import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesMockServerSupport;
import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;

class KubernetesTestAppRuntimeBackendTest {

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
  void shouldRetainNodePortEndpointAcrossLifecycleOperations() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();
    KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(
        KubernetesRuntimeTestSupport.properties(),
        KubernetesRuntimeTestSupport.fixedClock()
    );
    KubernetesTestAppRuntimeBackend backend = new KubernetesTestAppRuntimeBackend(
        server.getClient(),
        factory,
        new KubernetesRuntimeStatusSynchronizer(
            server.getClient(),
            KubernetesRuntimeTestSupport.properties(),
            new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher()
        ),
        KubernetesRuntimeTestSupport.properties()
    );

    RuntimeInstance created = backend.create(spec).get();
    Integer retainedNodePort = service(spec).getSpec().getPorts().get(0).getNodePort();
    RuntimeInstance stopped = backend.stop(RuntimeIdentityKey.from(spec)).get();
    RuntimeInstance started = backend.start(spec).get();
    RuntimeInstance restarted = backend.restart(spec, 7L).get();

    assertTrue(created.getWebEndpoint().endsWith(String.valueOf(retainedNodePort)));
    assertEquals(RuntimeStatus.STOPPED, stopped.getStatus());
    assertEquals(retainedNodePort, service(spec).getSpec().getPorts().get(0).getNodePort());
    assertTrue(started.getWebEndpoint().endsWith(String.valueOf(retainedNodePort)));
    assertEquals(Long.valueOf(7L), restarted.getRuntimeGeneration());

    backend.delete(RuntimeIdentityKey.from(spec)).get();

    assertNull(service(spec));
    assertNull(server.getClient().apps().deployments()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.deploymentName(RuntimeIdentityKey.from(spec)))
        .get());
  }

  @Test
  void shouldCleanupRetainedResourcesWhenCreateFailsPartially() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.stableDomainSpec();
    KubernetesRuntimeResourceFactory failingFactory = new KubernetesRuntimeResourceFactory(
        KubernetesRuntimeTestSupport.properties(),
        KubernetesRuntimeTestSupport.fixedClock()
    ) {
      @Override
      public io.fabric8.kubernetes.api.model.networking.v1.Ingress ingress(RuntimeSpec runtimeSpec) {
        throw new IllegalStateException("boom");
      }
    };
    KubernetesTestAppRuntimeBackend backend = new KubernetesTestAppRuntimeBackend(
        server.getClient(),
        failingFactory,
        new KubernetesRuntimeStatusSynchronizer(
            server.getClient(),
            KubernetesRuntimeTestSupport.properties(),
            new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher()
        ),
        KubernetesRuntimeTestSupport.properties()
    );

    assertTrue(backend.create(spec).isFailure());
    assertNull(service(spec));
    assertNull(server.getClient().network().v1().ingresses()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.ingressName(RuntimeIdentityKey.from(spec)))
        .get());
  }

  @Test
  void shouldNotPostRetainedServiceAgainWhenStartingStoppedTempPortRuntime() throws Exception {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();
    KubernetesTestAppRuntimeBackend backend = new KubernetesTestAppRuntimeBackend(
        server.getClient(),
        new KubernetesRuntimeResourceFactory(
            KubernetesRuntimeTestSupport.properties(),
            KubernetesRuntimeTestSupport.fixedClock()
        ),
        new KubernetesRuntimeStatusSynchronizer(
            server.getClient(),
            KubernetesRuntimeTestSupport.properties(),
            new KubernetesRuntimeTestSupport.RecordingDevRuntimeEventPublisher()
        ),
        KubernetesRuntimeTestSupport.properties()
    );

    backend.create(spec).get();
    backend.stop(RuntimeIdentityKey.from(spec)).get();
    drainRequests();

    backend.start(spec).get();

    assertEquals(0, countRequests("POST", "/api/v1/namespaces/" + KubernetesRuntimeTestSupport.namespace()
        + "/services"));
  }

  private Service service(RuntimeSpec spec) {
    return server.getClient().services()
        .inNamespace(KubernetesRuntimeTestSupport.namespace())
        .withName(KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(spec)))
        .get();
  }

  private void drainRequests() throws Exception {
    while (server.takeRequest(100, TimeUnit.MILLISECONDS) != null) {
      // Keep draining until the queue is empty so assertions only see the current operation.
    }
  }

  private int countRequests(String method, String path) throws Exception {
    int count = 0;
    List<RecordedRequest> drained = new ArrayList<RecordedRequest>();
    RecordedRequest request;
    while ((request = server.takeRequest(100, TimeUnit.MILLISECONDS)) != null) {
      drained.add(request);
      if (method.equals(request.getMethod()) && path.equals(request.getPath())) {
        count++;
      }
    }
    return count;
  }
}
