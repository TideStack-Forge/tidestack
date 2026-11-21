package com.ouroboros.dev.runtime.docker.backend;

import java.util.*;
import io.vavr.control.Try;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.runtime.docker.mapper.DockerRuntimeMapper;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.RuntimeBackend;

/** Dedicated language workloads use the existing container service and runtime lifecycle. */
public final class DockerLanguageRuntimeBackend implements RuntimeBackend {
  private final ContainerService containers;
  public DockerLanguageRuntimeBackend(ContainerService containers) { this.containers = containers; }
  public BackendType getBackendType() { return BackendType.DOCKER; }
  public RuntimeKind getRuntimeKind() { return RuntimeKind.LANGUAGE_SERVICE; }
  public boolean supports(RuntimeSpec spec) { return spec.getBackendType() == getBackendType() && spec.getRuntimeKind() == getRuntimeKind(); }
  public Try<RuntimeInstance> create(RuntimeSpec spec) {
    var container = new Container(); container.setName(name(RuntimeIdentityKey.from(spec))); container.setImage(spec.getImage());
    container.setNetwork(spec.getNetwork()); container.setEnvironmentalVariables(spec.getEnv());
    var labels = new LinkedHashMap<>(spec.getLabels());
    labels.put("OUROBOROS_WORKLOAD_ID", spec.getWorkloadId()); labels.put("OUROBOROS_RUNTIME_KIND", "LANGUAGE_SERVICE");
    labels.put("OUROBOROS_RUNTIME_PROFILE", spec.getProfile()); container.setLabels(labels);
    container.setAutoRemove(true);
    container.setPortBindAddress("127.0.0.1");
    container.setPortBindings(Map.of("tcp", Map.of(8099, spec.getRequestedPorts().getOrDefault("language", 0))));
    if (spec.getResourceLimits().containsKey("memoryBytes")) container.setMemoryLimitBytes(Long.valueOf(spec.getResourceLimits().get("memoryBytes")));
    if (spec.getResourceLimits().containsKey("nanoCpus")) container.setNanoCpus(Long.valueOf(spec.getResourceLimits().get("nanoCpus")));
    return containers.create(container).fold(Try::failure, id -> getStatus(RuntimeIdentityKey.from(spec)));
  }
  public Try<RuntimeInstance> start(RuntimeSpec spec) { return containers.start(name(RuntimeIdentityKey.from(spec))).fold(Try::failure, value -> getStatus(RuntimeIdentityKey.from(spec))); }
  public Try<RuntimeInstance> stop(RuntimeIdentityKey key) { return containers.stop(name(key)).fold(Try::failure, value -> getStatus(key)); }
  public Try<RuntimeInstance> restart(RuntimeSpec spec, long generation) { return containers.restart(name(RuntimeIdentityKey.from(spec))).fold(Try::failure, value -> getStatus(RuntimeIdentityKey.from(spec))); }
  public Try<Void> delete(RuntimeIdentityKey key) {
    return containers.stop(name(key)).fold(error -> missing(error) ? Try.success(null) : Try.<Void>failure(error), value -> Try.<Void>success(null))
        .flatMap(ignored -> containers.delete(name(key)).fold(error -> missing(error) ? Try.success(null) : Try.failure(error), Try::success));
  }
  private boolean missing(Exception error) {
    return error instanceof com.github.dockerjava.api.exception.NotFoundException
        || error instanceof com.github.dockerjava.api.exception.ConflictException && error.getMessage().contains("already in progress");
  }
  public Try<RuntimeInstance> reloadWorkspace(RuntimeSpec spec, long generation) { return Try.failure(new UnsupportedOperationException("Language environments are immutable")); }
  public Try<RuntimeInstance> getStatus(RuntimeIdentityKey key) {
    return containers.getContainerInfoByName(name(key)).fold(Try::failure, container -> {
      Integer port = Optional.ofNullable(container.getPortBindings()).map(ports -> ports.get("tcp")).map(ports -> ports.get(8099)).orElse(null);
      return Try.success(RuntimeInstance.builder().runtimeId(container.getId()).workloadId(key.getWorkloadId()).profile(key.getProfile())
          .runtimeKind(getRuntimeKind()).backendType(getBackendType()).status(DockerRuntimeMapper.toRuntimeStatus(container.getStatus()))
          .accessRef(port == null ? Map.of() : Map.of("language", "tcp://127.0.0.1:" + port)).backendMetadata(Map.of("containerName", name(key))).build());
    });
  }
  public Try<List<String>> getLogs(RuntimeIdentityKey key) { return containers.getLogs(name(key), 200).fold(Try::failure, Try::success); }
  private String name(RuntimeIdentityKey key) { return "ouroboros-language-" + key.getWorkloadId() + "-" + key.getProfile(); }
}
