package com.ouroboros.dev.runtime.kubernetes.mapper;

import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.fabric8.kubernetes.api.model.ContainerPortBuilder;
import io.fabric8.kubernetes.api.model.EnvVarBuilder;
import io.fabric8.kubernetes.api.model.IntOrString;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceRequirementsBuilder;
import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.ServiceBuilder;
import io.fabric8.kubernetes.api.model.ServicePort;
import io.fabric8.kubernetes.api.model.ServicePortBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.api.model.apps.DeploymentBuilder;
import io.fabric8.kubernetes.api.model.networking.v1.Ingress;
import io.fabric8.kubernetes.api.model.networking.v1.IngressBuilder;

import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceRef;

public class KubernetesRuntimeResourceFactory {
  public static final String ANNOTATION_LEASE_EXPIRES_AT = "ouroboros.dev/runtime-lease-expires-at";
  public static final String ANNOTATION_RUNTIME_GENERATION = "ouroboros.dev/runtime-generation";
  public static final String ANNOTATION_RESTARTED_AT = "ouroboros.dev/runtime-restarted-at";

  private static final String WORKSPACE_VOLUME = "workspace";
  private static final String WEB_PORT = "web";
  private static final String API_PORT = "api";
  private static final String DEBUG_PORT = "debug";
  private static final int WEB_TARGET_PORT = 80;
  private static final int API_TARGET_PORT = 8080;
  private static final int DEBUG_TARGET_PORT = 5005;
  private static final int NODE_PORT_BASE = 30000;
  private static final int NODE_PORT_BUCKETS = 900;

  private final KubernetesRuntimeProperties properties;
  private final Clock clock;

  public KubernetesRuntimeResourceFactory(KubernetesRuntimeProperties properties) {
    this(properties, Clock.systemUTC());
  }

  public KubernetesRuntimeResourceFactory(KubernetesRuntimeProperties properties, Clock clock) {
    this.properties = properties;
    this.clock = clock == null ? Clock.systemUTC() : clock;
  }

  public Deployment deployment(RuntimeSpec spec) {
    return deployment(spec, null);
  }

  public Deployment deployment(RuntimeSpec spec, Long runtimeGeneration) {
    if (spec.getRuntimeKind() == RuntimeKind.DEV_HELPER) {
      return helperDeployment(spec, runtimeGeneration);
    }
    return testAppDeployment(spec, runtimeGeneration);
  }

  private Deployment testAppDeployment(RuntimeSpec spec, Long runtimeGeneration) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    Map<String, String> labels = runtimeLabels(key);
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .withNamespace(properties.getNamespace())
        .withLabels(labels)
        .withAnnotations(annotations(spec, runtimeGeneration))
        .endMetadata()
        .withNewSpec()
        .withReplicas(Integer.valueOf(1))
        .withNewSelector()
        .withMatchLabels(labels)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(labels)
        .withAnnotations(templateAnnotations(runtimeGeneration))
        .endMetadata()
        .withNewSpec()
        .withVolumes(volumes(spec.getWorkspaceRef()))
        .addNewContainer()
        .withName("app")
        .withImage(spec.getImage())
        .withEnv(envVars(spec))
        .withPorts(
            new ContainerPortBuilder().withName(WEB_PORT).withContainerPort(Integer.valueOf(WEB_TARGET_PORT)).build(),
            new ContainerPortBuilder().withName(API_PORT).withContainerPort(Integer.valueOf(API_TARGET_PORT)).build(),
            new ContainerPortBuilder().withName(DEBUG_PORT).withContainerPort(Integer.valueOf(DEBUG_TARGET_PORT)).build()
        )
        .withVolumeMounts(volumeMounts(spec.getWorkspaceRef()))
        .withResources(resourceRequirements(spec))
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  private Deployment helperDeployment(RuntimeSpec spec, Long runtimeGeneration) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    Map<String, String> labels = runtimeLabels(key);
    return new DeploymentBuilder()
        .withNewMetadata()
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .withNamespace(properties.getNamespace())
        .withLabels(labels)
        .withAnnotations(annotations(spec, runtimeGeneration))
        .endMetadata()
        .withNewSpec()
        .withReplicas(Integer.valueOf(1))
        .withNewSelector()
        .withMatchLabels(labels)
        .endSelector()
        .withNewTemplate()
        .withNewMetadata()
        .withLabels(labels)
        .withAnnotations(templateAnnotations(runtimeGeneration))
        .endMetadata()
        .withNewSpec()
        .withVolumes(volumes(spec.getWorkspaceRef()))
        .addNewContainer()
        .withName("helper")
        .withImage(spec.getImage())
        .withEnv(envVars(spec))
        .withVolumeMounts(helperVolumeMounts(spec.getWorkspaceRef()))
        .withResources(resourceRequirements(spec))
        .endContainer()
        .endSpec()
        .endTemplate()
        .endSpec()
        .build();
  }

  public Service service(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    boolean nodePortExposure = spec.getExposureStrategy() == ExposureStrategy.TEMP_PORT;
    List<ServicePort> ports = new ArrayList<ServicePort>();
    ports.add(servicePort(key, WEB_PORT, WEB_TARGET_PORT, 0, nodePortExposure));
    return new ServiceBuilder()
        .withNewMetadata()
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .withNamespace(properties.getNamespace())
        .withLabels(runtimeLabels(key))
        .withAnnotations(annotations(spec, null))
        .endMetadata()
        .withNewSpec()
        .withType(nodePortExposure ? "NodePort" : "ClusterIP")
        .withSelector(runtimeLabels(key))
        .withPorts(ports)
        .endSpec()
        .build();
  }

  public Ingress ingress(RuntimeSpec spec) {
    if (spec.getExposureStrategy() != ExposureStrategy.STABLE_DOMAIN || !hasText(properties.getIngressHostSuffix())) {
      return null;
    }
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return new IngressBuilder()
        .withNewMetadata()
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .withNamespace(properties.getNamespace())
        .withLabels(runtimeLabels(key))
        .withAnnotations(annotations(spec, null))
        .endMetadata()
        .withNewSpec()
        .addNewRule()
        .withHost(ingressHost(spec))
        .withNewHttp()
        .addNewPath()
        .withPath("/")
        .withPathType("Prefix")
        .withNewBackend()
        .withNewService()
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .withNewPort()
        .withNumber(Integer.valueOf(WEB_TARGET_PORT))
        .endPort()
        .endService()
        .endBackend()
        .endPath()
        .endHttp()
        .endRule()
        .endSpec()
        .build();
  }

  public String ingressHost(RuntimeSpec spec) {
    return sanitizeHostPart(spec.getAppName()) + "-" + sanitizeHostPart(spec.getProfile()) + "." + properties.getIngressHostSuffix();
  }

  private ServicePort servicePort(RuntimeIdentityKey key, String name, int targetPort, int offset, boolean nodePortExposure) {
    ServicePortBuilder builder = new ServicePortBuilder()
        .withName(name)
        .withPort(Integer.valueOf(targetPort))
        .withTargetPort(new IntOrString(targetPort));
    if (nodePortExposure) {
      builder.withNodePort(Integer.valueOf(nodePort(key, offset)));
    }
    return builder.build();
  }

  private int nodePort(RuntimeIdentityKey key, int offset) {
    int bucket = Math.abs((key.getAppName() + ":" + key.getProfile() + ":" + key.getRuntimeKind()).hashCode()) % NODE_PORT_BUCKETS;
    return NODE_PORT_BASE + (bucket * 3) + offset;
  }

  private Map<String, String> annotations(RuntimeSpec spec, Long runtimeGeneration) {
    Map<String, String> annotations = new LinkedHashMap<String, String>();
    if (spec.getTtl() != null) {
      annotations.put(ANNOTATION_LEASE_EXPIRES_AT, String.valueOf(clock.millis() + spec.getTtl().toMillis()));
    }
    if (runtimeGeneration != null) {
      annotations.put(ANNOTATION_RUNTIME_GENERATION, String.valueOf(runtimeGeneration));
      annotations.put(ANNOTATION_RESTARTED_AT, String.valueOf(clock.millis()));
    }
    return annotations;
  }

  private Map<String, String> templateAnnotations(Long runtimeGeneration) {
    Map<String, String> annotations = new LinkedHashMap<String, String>();
    if (runtimeGeneration != null) {
      annotations.put(ANNOTATION_RUNTIME_GENERATION, String.valueOf(runtimeGeneration));
      annotations.put(ANNOTATION_RESTARTED_AT, String.valueOf(clock.millis()));
    }
    return annotations;
  }

  private Map<String, String> runtimeLabels(RuntimeIdentityKey key) {
    return new LinkedHashMap<String, String>(KubernetesResourceNameFactory.labels(key));
  }

  private List<io.fabric8.kubernetes.api.model.EnvVar> envVars(RuntimeSpec spec) {
    List<io.fabric8.kubernetes.api.model.EnvVar> envVars = new ArrayList<io.fabric8.kubernetes.api.model.EnvVar>();
    if (spec.getEnv() == null) {
      return envVars;
    }
    for (Map.Entry<String, String> entry : spec.getEnv().entrySet()) {
      envVars.add(new EnvVarBuilder().withName(entry.getKey()).withValue(entry.getValue()).build());
    }
    if (spec.getRuntimeKind() == RuntimeKind.TEST_APP) {
      envVars.removeIf(value -> "OUROBOROS_DEBUG_POD_UID".equals(value.getName()));
      envVars.add(new EnvVarBuilder().withName("OUROBOROS_DEBUG_POD_UID")
          .withNewValueFrom().withNewFieldRef().withFieldPath("metadata.uid").endFieldRef().endValueFrom().build());
    }
    return envVars;
  }

  private List<io.fabric8.kubernetes.api.model.VolumeMount> volumeMounts(WorkspaceRef workspaceRef) {
    List<io.fabric8.kubernetes.api.model.VolumeMount> mounts = new ArrayList<io.fabric8.kubernetes.api.model.VolumeMount>();
    if (workspaceRef == null || workspaceRef.getPvcName() == null) {
      return mounts;
    }
    mounts.add(new VolumeMountBuilder()
        .withName(WORKSPACE_VOLUME)
        .withMountPath("/app/deploy")
        .withSubPath(workspaceRef.getSubPath() + "/metadata")
        .build());
    mounts.add(new VolumeMountBuilder()
        .withName(WORKSPACE_VOLUME)
        .withMountPath("/app/deploy/extend")
        .withSubPath(workspaceRef.getSubPath() + "/extend")
        .build());
    return mounts;
  }

  private List<io.fabric8.kubernetes.api.model.VolumeMount> helperVolumeMounts(WorkspaceRef workspaceRef) {
    List<io.fabric8.kubernetes.api.model.VolumeMount> mounts = new ArrayList<io.fabric8.kubernetes.api.model.VolumeMount>();
    if (workspaceRef == null || workspaceRef.getPvcName() == null) {
      return mounts;
    }
    mounts.add(new VolumeMountBuilder()
        .withName(WORKSPACE_VOLUME)
        .withMountPath("/app/deploy")
        .withSubPath(workspaceRef.getSubPath())
        .build());
    mounts.add(new VolumeMountBuilder()
        .withName(WORKSPACE_VOLUME)
        .withMountPath("/app/metadata")
        .withSubPath(workspaceRef.getSubPath())
        .build());
    return mounts;
  }

  private List<io.fabric8.kubernetes.api.model.Volume> volumes(WorkspaceRef workspaceRef) {
    List<io.fabric8.kubernetes.api.model.Volume> volumes = new ArrayList<io.fabric8.kubernetes.api.model.Volume>();
    if (workspaceRef == null || workspaceRef.getPvcName() == null) {
      return volumes;
    }
    volumes.add(new VolumeBuilder()
        .withName(WORKSPACE_VOLUME)
        .withNewPersistentVolumeClaim()
        .withClaimName(workspaceRef.getPvcName())
        .endPersistentVolumeClaim()
        .build());
    return volumes;
  }

  private io.fabric8.kubernetes.api.model.ResourceRequirements resourceRequirements(RuntimeSpec spec) {
    if (spec.getResourceLimits() == null || spec.getResourceLimits().isEmpty()) {
      return null;
    }
    Map<String, Quantity> limits = new LinkedHashMap<String, Quantity>();
    for (Map.Entry<String, String> entry : spec.getResourceLimits().entrySet()) {
      limits.put(entry.getKey(), new Quantity(entry.getValue()));
    }
    return new ResourceRequirementsBuilder().withLimits(limits).build();
  }

  private boolean hasText(String value) {
    return value != null && !value.trim().isEmpty();
  }

  private String sanitizeHostPart(String value) {
    if (!hasText(value)) {
      return "x";
    }
    return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+", "").replaceAll("-+$", "");
  }
}
