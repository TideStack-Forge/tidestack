package com.ouroboros.dev.runtime.kubernetes.status;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import io.fabric8.kubernetes.api.model.HasMetadata;
import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.vavr.control.Try;

import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.event.DevRuntimeEventType;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesRuntimeResourceFactory;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeCapability;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.WorkspaceRef;

public class KubernetesRuntimeStatusSynchronizer {
  private final KubernetesClient client;
  private final KubernetesRuntimeProperties properties;
  private final DevRuntimeEventPublisher eventPublisher;
  private final Map<RuntimeIdentityKey, RuntimeInstance> lastSeen = new ConcurrentHashMap<RuntimeIdentityKey, RuntimeInstance>();

  public KubernetesRuntimeStatusSynchronizer(
      KubernetesClient client,
      KubernetesRuntimeProperties properties,
      DevRuntimeEventPublisher eventPublisher
  ) {
    this.client = client;
    this.properties = properties;
    this.eventPublisher = eventPublisher == null ? DevRuntimeEventPublisher.noop() : eventPublisher;
  }

  public RuntimeInstance reconcile(RuntimeSpec spec) {
    return reconcile(RuntimeIdentityKey.from(spec), spec.getWorkspaceRef());
  }

  public RuntimeInstance reconcile(RuntimeIdentityKey key) {
    return reconcile(key, null);
  }

  public RuntimeInstance reconcileAndPublish(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    RuntimeInstance runtime = reconcile(key, spec.getWorkspaceRef());
    publishIfChanged(key, runtime);
    return runtime;
  }

  public RuntimeInstance reconcileAndPublish(RuntimeIdentityKey key) {
    RuntimeInstance runtime = reconcile(key, null);
    publishIfChanged(key, runtime);
    return runtime;
  }

  private RuntimeInstance reconcile(RuntimeIdentityKey key, WorkspaceRef workspaceRef) {
    Deployment deployment = deployment(key);
    Service service = service(key);
    Ingress ingress = ingress(key);
    if (deployment == null && service == null && ingress == null) {
      return runtimeInstance(
          key,
          RuntimeStatus.NOT_FOUND,
          null,
          null,
          null,
          null,
          workspaceRef,
          Collections.<String, String>emptyMap(),
          Collections.<String, String>emptyMap()
      );
    }
    List<Pod> pods = pods(key);
    String webEndpoint = webEndpoint(service, ingress);
    Map<String, String> accessRef = key.getRuntimeKind() == com.ouroboros.dev.runtime.model.RuntimeKind.LANGUAGE_SERVICE
        ? Map.of("language", "tcp://" + KubernetesResourceNameFactory.serviceName(key) + "." + properties.getNamespace() + ".svc:8099") : accessRef(service, ingress);
    Map<String, String> metadata = backendMetadata(deployment, service, ingress);
    var readyPods = pods.stream().filter(pod -> pod.getMetadata() != null
        && pod.getMetadata().getDeletionTimestamp() == null && hasReadyPod(List.of(pod))).toList();
    if (readyPods.size() == 1 && readyPods.getFirst().getMetadata().getUid() != null) {
      metadata.put("debugPodUid", readyPods.getFirst().getMetadata().getUid());
    }
    return runtimeInstance(
        key,
        statusOf(deployment, service, ingress, pods),
        runtimeGeneration(deployment),
        webEndpoint,
        null,
        null,
        workspaceRef,
        accessRef,
        metadata
    );
  }

  private void publishIfChanged(RuntimeIdentityKey key, RuntimeInstance runtime) {
    RuntimeInstance previous = lastSeen.get(key);
    if (runtime.getStatus() == RuntimeStatus.NOT_FOUND) {
      if (previous != null && previous.getStatus() != RuntimeStatus.NOT_FOUND) {
        eventPublisher.publish(DevRuntimeEventType.RUNTIME_DELETED, previous);
      }
      lastSeen.remove(key);
      return;
    }
    if (previous == null || previous.getStatus() != runtime.getStatus()) {
      eventPublisher.publish(runtime.getStatus() == RuntimeStatus.FAILED ? DevRuntimeEventType.RUNTIME_FAILED : DevRuntimeEventType.RUNTIME_STATUS_CHANGED, runtime);
    } else if (endpointChanged(previous, runtime)) {
      eventPublisher.publish(DevRuntimeEventType.RUNTIME_ENDPOINT_CHANGED, runtime);
    }
    lastSeen.put(key, runtime);
  }

  private boolean endpointChanged(RuntimeInstance previous, RuntimeInstance current) {
    return !Objects.equals(previous.getWebEndpoint(), current.getWebEndpoint())
        || !Objects.equals(previous.getApiEndpoint(), current.getApiEndpoint())
        || !Objects.equals(previous.getDebugEndpoint(), current.getDebugEndpoint());
  }

  private RuntimeInstance runtimeInstance(
      RuntimeIdentityKey key,
      RuntimeStatus status,
      Long runtimeGeneration,
      String webEndpoint,
      String apiEndpoint,
      String debugEndpoint,
      WorkspaceRef workspaceRef,
      Map<String, String> accessRef,
      Map<String, String> backendMetadata
  ) {
    return RuntimeInstance.builder()
        .runtimeId(KubernetesResourceNameFactory.deploymentName(key))
        .appName(key.getAppName())
        .workloadId(key.getWorkloadId())
        .profile(key.getProfile())
        .runtimeKind(key.getRuntimeKind())
        .backendType(BackendType.KUBERNETES)
        .runtimeGeneration(runtimeGeneration)
        .status(status)
        .webEndpoint(webEndpoint)
        .apiEndpoint(apiEndpoint)
        .debugEndpoint(debugEndpoint)
        .workspaceRef(workspaceRef)
        .accessRef(accessRef)
        .backendMetadata(backendMetadata)
        .capabilities(RuntimeCapability.builder()
            .supportsPause(false)
            .supportsDebugPort(true)
            .supportsDebugSession(true)
            .supportsWorkspaceHotReload(true)
            .supportsNodePortExposure(true)
            .supportsIngressExposure(true)
            .build())
        .build();
  }

  private RuntimeStatus statusOf(Deployment deployment, Service service, Ingress ingress, List<Pod> pods) {
    if (deployment == null) {
      return service == null && ingress == null ? RuntimeStatus.NOT_FOUND : RuntimeStatus.STOPPED;
    }
    Integer replicas = deployment.getSpec() == null ? null : deployment.getSpec().getReplicas();
    if (replicas == null || replicas.intValue() == 0) {
      return RuntimeStatus.STOPPED;
    }
    if (hasReadyPod(pods)) {
      return RuntimeStatus.RUNNING;
    }
    return RuntimeStatus.STARTING;
  }

  private boolean hasReadyPod(List<Pod> pods) {
    if (pods == null || pods.isEmpty()) {
      return false;
    }
    for (Pod pod : pods) {
      if (pod == null || pod.getStatus() == null || pod.getStatus().getConditions() == null) {
        continue;
      }
      for (io.fabric8.kubernetes.api.model.PodCondition condition : pod.getStatus().getConditions()) {
        if ("Ready".equals(condition.getType()) && "True".equalsIgnoreCase(condition.getStatus())) {
          return true;
        }
      }
    }
    return false;
  }

  private Long runtimeGeneration(Deployment deployment) {
    if (deployment == null || deployment.getMetadata() == null || deployment.getMetadata().getAnnotations() == null) {
      return null;
    }
    return Try.of(() -> Long.valueOf(deployment.getMetadata().getAnnotations().get(KubernetesRuntimeResourceFactory.ANNOTATION_RUNTIME_GENERATION)))
        .getOrElse((Long) null);
  }

  private String webEndpoint(Service service, Ingress ingress) {
    if (ingress != null && ingress.getSpec() != null && ingress.getSpec().getRules() != null && !ingress.getSpec().getRules().isEmpty()) {
      String host = ingress.getSpec().getRules().get(0).getHost();
      return host == null ? null : "https://" + host;
    }
    Integer nodePort = nodePort(service, "web");
    return nodePort == null ? null : "http://127.0.0.1:" + nodePort;
  }

  private Integer nodePort(Service service, String portName) {
    if (service == null || service.getSpec() == null || service.getSpec().getPorts() == null) {
      return null;
    }
    for (ServicePort port : service.getSpec().getPorts()) {
      if (port != null && portName.equals(port.getName())) {
        return port.getNodePort();
      }
    }
    return null;
  }

  private Map<String, String> accessRef(Service service, Ingress ingress) {
    Map<String, String> accessRef = new LinkedHashMap<String, String>();
    Integer webNodePort = nodePort(service, "web");
    if (webNodePort != null) {
      accessRef.put("webNodePort", String.valueOf(webNodePort));
    }
    if (ingress != null && ingress.getSpec() != null && ingress.getSpec().getRules() != null && !ingress.getSpec().getRules().isEmpty()) {
      accessRef.put("ingressHost", ingress.getSpec().getRules().get(0).getHost());
    }
    return accessRef;
  }

  private Map<String, String> backendMetadata(Deployment deployment, Service service, Ingress ingress) {
    Map<String, String> metadata = new LinkedHashMap<String, String>();
    if (deployment != null && deployment.getMetadata() != null) {
      metadata.put("deployment", deployment.getMetadata().getName());
      if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null
          && deployment.getSpec().getTemplate().getSpec() != null) {
        deployment.getSpec().getTemplate().getSpec().getContainers().stream()
            .filter(container -> "app".equals(container.getName()) && container.getEnv() != null)
            .flatMap(container -> container.getEnv().stream())
            .filter(env -> List.of("OUROBOROS_APP_INCARNATION_ID", "OUROBOROS_DEBUG_OWNER").contains(env.getName())
                && env.getValue() != null)
            .forEach(env -> metadata.put(env.getName(), env.getValue()));
      }
    }
    if (service != null && service.getMetadata() != null) {
      metadata.put("service", service.getMetadata().getName());
      if (service.getSpec() != null) {
        metadata.put("serviceType", service.getSpec().getType());
      }
    }
    if (ingress != null && ingress.getMetadata() != null) {
      metadata.put("ingress", ingress.getMetadata().getName());
    }
    return metadata;
  }

  private Deployment deployment(RuntimeIdentityKey key) {
    return client.apps().deployments()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .get();
  }

  private Service service(RuntimeIdentityKey key) {
    return client.services()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .get();
  }

  private Ingress ingress(RuntimeIdentityKey key) {
    return client.network().v1().ingresses()
        .inNamespace(properties.getNamespace())
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .get();
  }

  private List<Pod> pods(RuntimeIdentityKey key) {
    return client.pods()
        .inNamespace(properties.getNamespace())
        .withLabels(KubernetesResourceNameFactory.labels(key))
        .list()
        .getItems();
  }

  static RuntimeIdentityKey keyOf(HasMetadata resource) {
    if (resource == null || resource.getMetadata() == null) {
      return null;
    }
    return keyOf(resource.getMetadata().getLabels());
  }

  static RuntimeIdentityKey keyOf(Map<String, String> labels) {
    if (labels == null || labels.isEmpty()) {
      return null;
    }
    String appName = labels.get("ouroboros.dev/runtime-app");
    String profile = labels.get("ouroboros.dev/runtime-profile");
    String runtimeKind = labels.get("ouroboros.dev/runtime-kind");
    String backendType = labels.get("ouroboros.dev/runtime-backend");
    if ((appName == null && labels.get("ouroboros.dev/runtime-workload") == null) || runtimeKind == null || backendType == null) {
      return null;
    }
    return RuntimeIdentityKey.builder().appName(appName).workloadId(labels.get("ouroboros.dev/runtime-workload")).profile(profile)
        .runtimeKind(com.ouroboros.dev.runtime.model.RuntimeKind.valueOf(runtimeKind)).backendType(BackendType.valueOf(backendType)).build();
  }
}
