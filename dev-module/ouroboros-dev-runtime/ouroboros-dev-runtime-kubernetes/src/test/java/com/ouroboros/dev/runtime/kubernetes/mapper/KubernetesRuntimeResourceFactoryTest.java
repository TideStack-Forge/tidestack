package com.ouroboros.dev.runtime.kubernetes.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.fabric8.kubernetes.api.model.ContainerPort;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.VolumeMount;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;

import com.ouroboros.dev.runtime.kubernetes.support.KubernetesRuntimeTestSupport;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeSpec;

class KubernetesRuntimeResourceFactoryTest {

  private final KubernetesRuntimeResourceFactory factory = new KubernetesRuntimeResourceFactory(
      KubernetesRuntimeTestSupport.properties(),
      KubernetesRuntimeTestSupport.fixedClock()
  );

  @Test
  void shouldCreateNodePortServiceForTempPortRuntimeWithOnlyWebServicePort() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();

    Service service = factory.service(spec);

    assertEquals("NodePort", service.getSpec().getType());
    assertEquals(
        KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(spec)),
        service.getMetadata().getName()
    );
    assertEquals(1, service.getSpec().getPorts().size());
    assertEquals("web", service.getSpec().getPorts().get(0).getName());
    assertNotNull(service.getSpec().getPorts().get(0).getNodePort());
    assertTrue(service.getSpec().getPorts().stream().map(ServicePort::getName).noneMatch("api"::equals));
    assertTrue(service.getSpec().getPorts().stream().map(ServicePort::getName).noneMatch("debug"::equals));
    assertEquals(
        String.valueOf(KubernetesRuntimeTestSupport.fixedClock().millis() + spec.getTtl().toMillis()),
        service.getMetadata().getAnnotations().get(KubernetesRuntimeResourceFactory.ANNOTATION_LEASE_EXPIRES_AT)
    );
  }

  @Test
  void shouldCreateIngressForStableDomainRuntime() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.stableDomainSpec();

    Ingress ingress = factory.ingress(spec);

    assertEquals("crm-alice.develop.example.com", ingress.getSpec().getRules().get(0).getHost());
    assertEquals(
        KubernetesResourceNameFactory.serviceName(RuntimeIdentityKey.from(spec)),
        ingress.getSpec().getRules().get(0).getHttp().getPaths().get(0).getBackend().getService().getName()
    );
  }

  @Test
  void shouldCreateDeploymentWithWorkspaceMountsAndGenerationMetadata() {
    RuntimeSpec spec = KubernetesRuntimeTestSupport.tempPortSpec();

    Deployment deployment = factory.deployment(spec, 7L);

    assertEquals(Integer.valueOf(1), deployment.getSpec().getReplicas());
    assertEquals("ouroboros/test-app:latest", deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getImage());
    assertEquals(
        "workspace/run/crm/alice/metadata",
        deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts().get(0).getSubPath()
    );
    assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getEnv())
        .filteredOn(env -> "CUSTOM_CLASS_PATH".equals(env.getName()))
        .isEmpty();
    assertThat(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getVolumeMounts())
        .filteredOn(mount -> "/app/deploy/extend".equals(mount.getMountPath()))
        .singleElement()
        .extracting(VolumeMount::getSubPath)
        .isEqualTo("workspace/run/crm/alice/extend");
    assertEquals(
        "7",
        deployment.getMetadata().getAnnotations().get(KubernetesRuntimeResourceFactory.ANNOTATION_RUNTIME_GENERATION)
    );
    assertTrue(deployment.getSpec().getTemplate().getSpec().getContainers().get(0).getPorts().stream()
        .map(ContainerPort::getContainerPort)
        .map(Integer::intValue)
        .anyMatch(port -> port == 5005));
  }
}
