package com.ouroboros.dev.runtime.kubernetes.backend;

import java.util.LinkedHashMap;
import java.util.Map;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.kubernetes.status.KubernetesRuntimeStatusSynchronizer;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.service.RuntimeBackend;

public class KubernetesTestAppRuntimeBackend implements RuntimeBackend {
  private final KubernetesClient client;
  private final KubernetesRuntimeResourceFactory resourceFactory;
  private final KubernetesRuntimeStatusSynchronizer statusSynchronizer;
  private final String namespace;

  public KubernetesTestAppRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer
  ) {
    this(client, resourceFactory, statusSynchronizer, null);
  }

  public KubernetesTestAppRuntimeBackend(
      KubernetesClient client,
      KubernetesRuntimeResourceFactory resourceFactory,
      KubernetesRuntimeStatusSynchronizer statusSynchronizer,
      KubernetesRuntimeProperties properties
  ) {
    this.client = client;
    this.resourceFactory = resourceFactory;
    this.statusSynchronizer = statusSynchronizer;
    this.namespace = properties == null || properties.getNamespace() == null ? "default" : properties.getNamespace();
  }

  @Override
  public BackendType getBackendType() {
    return BackendType.KUBERNETES;
  }

  @Override
  public RuntimeKind getRuntimeKind() {
    return RuntimeKind.TEST_APP;
  }

  @Override
  public boolean supports(RuntimeSpec spec) {
    return spec.getBackendType() == BackendType.KUBERNETES && spec.getRuntimeKind() == RuntimeKind.TEST_APP;
  }

  @Override
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    return createWithCompensation(spec);
  }

  private Try<RuntimeInstance> createWithCompensation(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return Try.of(() -> {
      boolean createdService = false;
      boolean createdIngress = false;
      boolean createdDeployment = false;
      try {
        createdService = ensureService(spec, false);
        createdIngress = ensureIngress(spec, false);
        createdDeployment = ensureDeployment(spec, null, false);
        return statusSynchronizer.reconcile(spec);
      } catch (Exception exception) {
        compensateCreateFailure(key, createdDeployment, createdIngress, createdService);
        throw exception;
      }
    });
  }

  @Override
  public Try<RuntimeInstance> start(RuntimeSpec spec) {
    return Try.of(() -> {
      ensureService(spec, true);
      ensureIngress(spec, true);
      if (deployment(RuntimeIdentityKey.from(spec)) == null) {
        ensureDeployment(spec, null, true);
      } else {
        scale(RuntimeIdentityKey.from(spec), 1);
      }
      return statusSynchronizer.reconcile(spec);
    });
  }

  @Override
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) {
    return Try.of(() -> {
      if (deployment(key) != null) {
        scale(key, 0);
      }
      RuntimeInstance runtime = statusSynchronizer.reconcile(key);
      return runtime.getStatus() == RuntimeStatus.NOT_FOUND ? stoppedInstance(key) : runtime;
    });
  }

  @Override
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long runtimeGeneration) {
    return Try.of(() -> {
      ensureService(spec, true);
      ensureIngress(spec, true);
      ensureDeployment(spec, Long.valueOf(runtimeGeneration), true);
      return statusSynchronizer.reconcile(spec);
    });
  }

  @Override
  public Try<Void> delete(RuntimeIdentityKey key) {
    return Try.run(() -> {
      deleteDeployment(key);
      deleteIngress(key);
      deleteService(key);
    }).map(value -> null);
  }

  @Override
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long runtimeGeneration) {
    return Try.of(() -> {
      if (deployment(RuntimeIdentityKey.from(spec)) == null) {
        throw new IllegalStateException("runtime not found");
      }
      ensureDeployment(spec, Long.valueOf(runtimeGeneration), true);
      return statusSynchronizer.reconcile(spec);
    });
  }

  @Override
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return Try.success(statusSynchronizer.reconcile(key));
  }

  private void compensateCreateFailure(RuntimeIdentityKey key, boolean createdDeployment, boolean createdIngress, boolean createdService) {
    if (createdDeployment) {
      deleteDeployment(key);
    }
    if (createdIngress) {
      deleteIngress(key);
    }
    if (createdService) {
      deleteService(key);
    }
  }

  private boolean ensureService(RuntimeSpec spec, boolean repair) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    Service current = service(key);
    if (current != null) {
      if (!repair) {
        return false;
      }
      replace(retainedService(current, resourceFactory.service(spec)));
      return true;
    }
    apply(resourceFactory.service(spec));
    return true;
  }

  private boolean ensureIngress(RuntimeSpec spec, boolean repair) {
    Ingress desired = resourceFactory.ingress(spec);
    if (desired == null) {
      return false;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    Ingress current = ingress(key);
    if (current != null) {
      if (!repair) {
        return false;
      }
      replace(retainedIngress(current, desired));
      return true;
    }
    apply(desired);
    return true;
  }

  private boolean ensureDeployment(RuntimeSpec spec, Long runtimeGeneration, boolean repair) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    if (!repair && deployment(key) != null) {
      return false;
    }
    apply(resourceFactory.deployment(spec, runtimeGeneration));
    return true;
  }

  private void scale(RuntimeIdentityKey key, int replicas) {
    Deployment current = deployment(key);
    if (current == null) {
      return;
    }
    Deployment scaled = new DeploymentBuilder(current)
        .editSpec()
        .withReplicas(Integer.valueOf(replicas))
        .endSpec()
        .build();
    apply(scaled);
  }

  private void apply(io.fabric8.kubernetes.api.model.HasMetadata resource) {
    client.resource(resource).createOrReplace();
  }

  private void replace(io.fabric8.kubernetes.api.model.HasMetadata resource) {
    client.resource(resource).lockResourceVersion().replace();
  }

  private Service retainedService(Service current, Service desired) {
    return new ServiceBuilder(current)
        .editOrNewMetadata()
        .withLabels(copyMap(desired.getMetadata() == null ? null : desired.getMetadata().getLabels()))
        .withAnnotations(mergedAnnotations(current.getMetadata() == null ? null : current.getMetadata().getAnnotations(),
            desired.getMetadata() == null ? null : desired.getMetadata().getAnnotations()))
        .endMetadata()
        .build();
  }

  private Ingress retainedIngress(Ingress current, Ingress desired) {
    return new IngressBuilder(current)
        .editOrNewMetadata()
        .withLabels(copyMap(desired.getMetadata() == null ? null : desired.getMetadata().getLabels()))
        .withAnnotations(mergedAnnotations(current.getMetadata() == null ? null : current.getMetadata().getAnnotations(),
            desired.getMetadata() == null ? null : desired.getMetadata().getAnnotations()))
        .endMetadata()
        .build();
  }

  private Map<String, String> mergedAnnotations(Map<String, String> existing, Map<String, String> desired) {
    Map<String, String> merged = new LinkedHashMap<String, String>();
    if (existing != null) {
      merged.putAll(existing);
    }
    if (desired != null) {
      merged.putAll(desired);
    }
    return merged.isEmpty() ? null : merged;
  }

  private Map<String, String> copyMap(Map<String, String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    return new LinkedHashMap<String, String>(values);
  }

  private Deployment deployment(RuntimeIdentityKey key) {
    return client.apps().deployments()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .get();
  }

  private Service service(RuntimeIdentityKey key) {
    return client.services()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .get();
  }

  private Ingress ingress(RuntimeIdentityKey key) {
    return client.network().v1().ingresses()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .get();
  }

  private void deleteDeployment(RuntimeIdentityKey key) {
    client.apps().deployments()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .delete();
  }

  private void deleteService(RuntimeIdentityKey key) {
    client.services()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .delete();
  }

  private void deleteIngress(RuntimeIdentityKey key) {
    client.network().v1().ingresses()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .delete();
  }

  private RuntimeInstance stoppedInstance(RuntimeIdentityKey key) {
    return RuntimeInstance.builder()
        .runtimeId(KubernetesResourceNameFactory.deploymentName(key))
        .appName(key.getAppName())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind())
        .backendType(key.getBackendType())
        .status(RuntimeStatus.STOPPED)
        .build();
  }
}
