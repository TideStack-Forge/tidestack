package com.ouroboros.dev.runtime.kubernetes.backend;

import java.util.*;
import io.fabric8.kubernetes.api.model.*;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vavr.control.Try;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.service.RuntimeBackend;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;

/** Language services use an internal service and independent workload; no application PVC or helper handshake. */
public final class KubernetesLanguageRuntimeBackend implements RuntimeBackend {
  private final KubernetesClient client;
  private final String namespace;
  public KubernetesLanguageRuntimeBackend(KubernetesClient client, KubernetesRuntimeProperties properties) { this.client = client; namespace = properties.getNamespace(); }
  public BackendType getBackendType() { return BackendType.KUBERNETES; }
  public RuntimeKind getRuntimeKind() { return RuntimeKind.LANGUAGE_SERVICE; }
  public boolean supports(RuntimeSpec spec) { return spec.getBackendType() == getBackendType() && spec.getRuntimeKind() == getRuntimeKind(); }
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    return Try.of(() -> {
      var key = RuntimeIdentityKey.from(spec); String name = name(key); var labels = KubernetesResourceNameFactory.labels(key);
      var annotations = spec.getTtl() == null ? Map.<String,String>of() : Map.of(KubernetesRuntimeResourceFactory.ANNOTATION_LEASE_EXPIRES_AT, String.valueOf(System.currentTimeMillis() + spec.getTtl().toMillis()));
      var limits = new LinkedHashMap<String, Quantity>();
      if (spec.getResourceLimits().containsKey("memoryBytes")) limits.put("memory", new Quantity(spec.getResourceLimits().get("memoryBytes")));
      if (spec.getResourceLimits().containsKey("nanoCpus")) limits.put("cpu", new Quantity(String.valueOf(Long.parseLong(spec.getResourceLimits().get("nanoCpus")) / 1000000L) + "m"));
      var deployment = new DeploymentBuilder().withNewMetadata().withName(name).withNamespace(namespace).withLabels(labels).withAnnotations(annotations).endMetadata()
          .withNewSpec().withReplicas(0).withNewSelector().withMatchLabels(labels).endSelector()
          .withNewTemplate().withNewMetadata().withLabels(labels).endMetadata().withNewSpec()
          .addNewContainer().withName("language").withImage(spec.getImage()).withEnv(spec.getEnv().entrySet().stream().map(item -> new EnvVar(item.getKey(), item.getValue(), null)).toList())
          .withResources(new ResourceRequirementsBuilder().withLimits(limits).build()).addNewPort().withName("language").withContainerPort(8099).endPort()
          .endContainer().endSpec().endTemplate().endSpec().build();
      client.apps().deployments().inNamespace(namespace).resource(deployment).create();
      try {
        client.services().inNamespace(namespace).resource(new ServiceBuilder().withNewMetadata().withName(KubernetesResourceNameFactory.serviceName(key)).withLabels(labels).withAnnotations(annotations).endMetadata()
            .withNewSpec().withType("ClusterIP").withSelector(labels).addNewPort().withName("language").withPort(8099).withNewTargetPort(8099).endPort().endSpec().build()).create();
      } catch (RuntimeException error) { client.apps().deployments().inNamespace(namespace).withName(name).delete(); throw error; }
      return getStatus(key).get();
    });
  }
  public Try<RuntimeInstance> start(RuntimeSpec spec) { return scale(RuntimeIdentityKey.from(spec), 1); }
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) { return scale(key, 0); }
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long generation) {
    return Try.of(() -> { var key = RuntimeIdentityKey.from(spec); client.apps().deployments().inNamespace(namespace).withName(name(key)).rolling().restart(); return getStatus(key).get(); });
  }
  public Try<Void> delete(RuntimeIdentityKey key) {
    return Try.run(() -> { client.services().inNamespace(namespace).withName(KubernetesResourceNameFactory.serviceName(key)).delete(); client.apps().deployments().inNamespace(namespace).withName(name(key)).delete(); });
  }
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long generation) { return Try.failure(new UnsupportedOperationException("Language environments are immutable")); }
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return Try.of(() -> {
      var deployment = client.apps().deployments().inNamespace(namespace).withName(name(key)).get();
      RuntimeStatus state = deployment == null ? RuntimeStatus.NOT_FOUND : deployment.getSpec().getReplicas() == 0 ? RuntimeStatus.STOPPED
          : deployment.getStatus() != null && deployment.getStatus().getReadyReplicas() != null && deployment.getStatus().getReadyReplicas() > 0 ? RuntimeStatus.RUNNING : RuntimeStatus.STARTING;
      return RuntimeInstance.builder().workloadId(key.getWorkloadId()).profile(key.getProfile()).runtimeKind(getRuntimeKind()).backendType(getBackendType())
          .runtimeId(name(key)).status(state).accessRef(Map.of("language", "tcp://" + KubernetesResourceNameFactory.serviceName(key) + "." + namespace + ".svc:8099")).build();
    });
  }
  public Try<List<String>> getLogs(RuntimeIdentityKey key) {
    return Try.of(() -> client.pods().inNamespace(namespace).withLabels(KubernetesResourceNameFactory.labels(key)).list().getItems().stream()
        .flatMap(pod -> client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).inContainer("language").tailingLines(200).getLog().lines()).toList());
  }
  private Try<RuntimeInstance> scale(RuntimeIdentityKey key, int replicas) {
    return Try.of(() -> { client.apps().deployments().inNamespace(namespace).withName(name(key)).scale(replicas); return getStatus(key).get(); });
  }
  private String name(RuntimeIdentityKey key) {
    return KubernetesResourceNameFactory.deploymentName(key);
  }
}
