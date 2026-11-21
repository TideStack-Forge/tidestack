package com.ouroboros.dev.runtime.kubernetes.status;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.client.KubernetesClient;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;

public class KubernetesRuntimeLeaseCleaner {
  private final KubernetesClient client;
  private final KubernetesRuntimeProperties properties;
  private final Clock clock;

  public KubernetesRuntimeLeaseCleaner(KubernetesClient client, KubernetesRuntimeProperties properties) {
    this(client, properties, Clock.systemUTC());
  }

  public KubernetesRuntimeLeaseCleaner(KubernetesClient client, KubernetesRuntimeProperties properties, Clock clock) {
    this.client = client;
    this.properties = properties;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public List<RuntimeIdentityKey> cleanExpired() {
    Set<RuntimeIdentityKey> expired = new LinkedHashSet<RuntimeIdentityKey>();
    collectExpired(expired, client.services().inNamespace(properties.getNamespace()).list().getItems());
    collectExpired(expired, client.apps().deployments().inNamespace(properties.getNamespace()).list().getItems());
    collectExpired(expired, client.network().v1().ingresses().inNamespace(properties.getNamespace()).list().getItems());
    for (RuntimeIdentityKey key : expired) {
      deleteRuntime(key);
    }
    return new ArrayList<RuntimeIdentityKey>(expired);
  }

  private void collectExpired(Set<RuntimeIdentityKey> expired, List<? extends HasMetadata> resources) {
    if (resources == null) {
      return;
    }
    for (HasMetadata resource : resources) {
      if (!isExpired(resource)) {
        continue;
      }
      RuntimeIdentityKey key = KubernetesRuntimeStatusSynchronizer.keyOf(resource);
      if (key != null) {
        expired.add(key);
      }
    }
  }

  private boolean isExpired(HasMetadata resource) {
    if (resource == null || resource.getMetadata() == null) {
      return false;
    }
    Map<String, String> annotations = resource.getMetadata().getAnnotations();
    if (annotations == null) {
      return false;
    }
    String leaseExpiresAt = annotations.get(KubernetesRuntimeResourceFactory.ANNOTATION_LEASE_EXPIRES_AT);
    if (leaseExpiresAt == null || leaseExpiresAt.trim().isEmpty()) {
      return false;
    }
    try {
      return Long.parseLong(leaseExpiresAt) <= clock.millis();
    } catch (NumberFormatException exception) {
      return false;
    }
  }

  private void deleteRuntime(RuntimeIdentityKey key) {
    client.apps().deployments()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .delete();
    client.services()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .delete();
    client.network().v1().ingresses()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .delete();
  }
}
