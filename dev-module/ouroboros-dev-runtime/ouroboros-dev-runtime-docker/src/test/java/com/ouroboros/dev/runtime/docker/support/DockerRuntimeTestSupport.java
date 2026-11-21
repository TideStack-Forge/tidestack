package com.ouroboros.dev.runtime.docker.support;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import io.vavr.control.Either;

import com.ouroboros.container.ContainerEventType;
import com.ouroboros.container.ContainerStatus;
import com.ouroboros.container.DockerEventListener;
import com.ouroboros.container.EventObject;
import com.ouroboros.container.model.Container;
import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.exception.PlatformException;

public final class DockerRuntimeTestSupport {
  private DockerRuntimeTestSupport() {
  }

  public static RuntimeSpec testAppSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .image("ouroboros/test-app:latest")
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .hostPath("/workspace/run/crm/alice")
            .build())
        .build();
  }

  public static RuntimeSpec helperSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.DOCKER)
        .workspaceMode(WorkspaceMode.HOST_PATH)
        .image("ouroboros/dev-helper:latest")
        .env(Collections.singletonMap(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, "app-incarnation"))
        .labels(Collections.singletonMap("ouroboros.dev/helper-image-source", "default"))
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.HOST_PATH)
            .hostPath("/workspace/run/crm/dev-helper")
            .build())
        .build();
  }

  public static class FakeDevelopWorkspaceContext implements DevelopWorkspaceContext {
    @Override
    public String getRootDir() {
      return "/workspace";
    }

    @Override
    public String getDevRootDir() {
      return "/workspace/dev";
    }

    @Override
    public String getRunRootDir() {
      return "/workspace/run";
    }

    @Override
    public String getDebugSupportFilesDir() {
      return "/workspace/debug-support";
    }

    @Override
    public String getDevelopSupportFilesDir() {
      return "/workspace/develop-support";
    }
  }

  public static class RecordingDevRuntimeEventPublisher implements DevRuntimeEventPublisher {
    private final List<DevRuntimeEvent> events = new ArrayList<DevRuntimeEvent>();

    @Override
    public void publish(DevRuntimeEvent event) {
      events.add(event);
    }

    public List<DevRuntimeEvent> events() {
      return Collections.unmodifiableList(events);
    }
  }

  public static class FakeContainerService implements ContainerService {
    private final Map<String, Container> containersById = new LinkedHashMap<String, Container>();
    private final Map<String, String> idsByName = new LinkedHashMap<String, String>();
    private final List<DockerEventListener> listeners = new ArrayList<DockerEventListener>();
    private final List<String> operationTrace = new ArrayList<String>();
    private final Map<String, List<String>> logsByName = new LinkedHashMap<String, List<String>>();
    private final AtomicInteger sequence = new AtomicInteger();
    private final Path fallbackWorkspaceRoot = createFallbackWorkspaceRoot();
    private Runnable afterNextInfoRead;

    @Override
    public Either<Exception, String> create(Container container) {
      operationTrace.add("create");
      String id = "cid-" + sequence.incrementAndGet();
      Container stored = copy(container);
      ensureWorkspaceExists(stored);
      stored.setId(id);
      stored.setStatus(ContainerStatus.STOPPED);
      containersById.put(id, stored);
      idsByName.put(stored.getName(), id);
      return Either.right(id);
    }

    @Override
    public Either<Exception, Void> delete(String id) {
      operationTrace.add("delete");
      String resolvedId = resolveId(id);
      if (resolvedId != null) {
        Container removed = containersById.remove(resolvedId);
        if (removed != null) {
          idsByName.remove(removed.getName());
        }
      }
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> start(String id) {
      operationTrace.add("start");
      Container container = require(id);
      container.setStatus(ContainerStatus.RUNNING);
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> pause(String id) {
      operationTrace.add("pause");
      Container container = require(id);
      container.setStatus(ContainerStatus.PAUSED);
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> unpause(String id) {
      operationTrace.add("unpause");
      Container container = require(id);
      container.setStatus(ContainerStatus.RUNNING);
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> stop(String id) {
      operationTrace.add("stop");
      Container container = require(id);
      container.setStatus(ContainerStatus.STOPPED);
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> kill(String id) {
      operationTrace.add("kill");
      Container container = require(id);
      container.setStatus(ContainerStatus.STOPPED);
      return Either.right(null);
    }

    @Override
    public Either<Exception, Void> restart(String id) {
      operationTrace.add("restart");
      Container container = require(id);
      container.setStatus(ContainerStatus.RUNNING);
      return Either.right(null);
    }

    @Override
    public void registerEventListener(DockerEventListener listener) {
      listeners.add(listener);
    }

    @Override
    public Either<Exception, Container> getContainerInfoById(String id) {
      Container container = containersById.get(resolveId(id));
      return container == null ? Either.left(noSuchContainer()) : Either.right(copy(container));
    }

    @Override
    public Either<Exception, Container> getContainerInfoByName(String name) {
      String id = idsByName.get(name);
      Either<Exception, Container> result = id == null
          ? Either.left(noSuchContainer())
          : Either.right(copy(containersById.get(id)));
      Runnable action = afterNextInfoRead;
      afterNextInfoRead = null;
      if (action != null) {
        action.run();
      }
      return result;
    }

    @Override
    public Either<Exception, List<Container>> getAllContainerInfo() {
      return Either.right(containersById.values().stream().map(this::copy).collect(Collectors.toList()));
    }

    @Override
    public Either<Exception, List<Container>> searchContainerInfo(Map<String, Collection<String>> filterMap) {
      List<Container> values = containersById.values().stream()
          .filter(container -> matches(filterMap, container))
          .map(this::copy)
          .collect(Collectors.toList());
      return Either.right(values);
    }

    @Override
    public Either<Exception, List<String>> getAllImageNames(String reference) {
      return Either.right(Collections.<String>emptyList());
    }

    @Override
    public Either<Exception, List<String>> getAllTaggedImageNames() {
      return Either.right(Collections.<String>emptyList());
    }

    @Override
    public Either<Exception, List<String>> getLogs(String id, Integer tailLines) {
      String resolvedId = resolveId(id);
      Container container = resolvedId == null ? null : containersById.get(resolvedId);
      if (container == null) {
        return Either.left(noSuchContainer());
      }
      List<String> logs = logsByName.getOrDefault(container.getName(), Collections.<String>emptyList());
      if (tailLines == null || logs.size() <= tailLines.intValue()) {
        return Either.right(new ArrayList<String>(logs));
      }
      return Either.right(new ArrayList<String>(logs.subList(logs.size() - tailLines.intValue(), logs.size())));
    }

    @Override
    public CompletableFuture<Void> pullImageAsync(String imageName) {
      return CompletableFuture.completedFuture(null);
    }

    public void put(Container container) {
      Container stored = copy(container);
      String id = stored.getId();
      if (id == null) {
        id = "cid-" + sequence.incrementAndGet();
        stored.setId(id);
      }
      containersById.put(id, stored);
      idsByName.put(stored.getName(), id);
    }

    public void runAfterNextInfoRead(Runnable action) {
      afterNextInfoRead = action;
    }

    public void putLogs(String containerName, List<String> logs) {
      logsByName.put(containerName, new ArrayList<String>(logs));
    }

    public List<String> operationTrace() {
      return Collections.unmodifiableList(operationTrace);
    }

    public void emit(ContainerEventType type, EventObject event) {
      listeners.forEach(listener -> listener.onEvent(type, event));
    }

    private Container require(String idOrName) {
      String resolvedId = resolveId(idOrName);
      Container container = resolvedId == null ? null : containersById.get(resolvedId);
      if (container == null) {
        throw new IllegalStateException("container not found");
      }
      return container;
    }

    private void ensureWorkspaceExists(Container container) {
      if (container.getVolumeBindings() == null) {
        return;
      }
      String hostPath = container.getVolumeBindings().get("/app/deploy");
      if (hostPath == null || hostPath.trim().isEmpty()) {
        return;
      }
      Path workspacePath = Paths.get(hostPath);
      if (Files.notExists(workspacePath)) {
        workspacePath = fallbackWorkspaceRoot.resolve(container.getName());
        container.getVolumeBindings().put("/app/deploy", workspacePath.toString());
      }
      try {
        Files.createDirectories(workspacePath);
      } catch (java.io.IOException exception) {
        throw new IllegalStateException("Unable to prepare fake Docker workspace", exception);
      }
    }

    private Path createFallbackWorkspaceRoot() {
      try {
        return Files.createTempDirectory("ouroboros-docker-runtime-test-");
      } catch (java.io.IOException exception) {
        throw new IllegalStateException("Unable to create fake Docker workspace", exception);
      }
    }

    private String resolveId(String idOrName) {
      if (containersById.containsKey(idOrName)) {
        return idOrName;
      }
      return idsByName.get(idOrName);
    }

    private PlatformException noSuchContainer() {
      return new PlatformException("docker error", new IllegalStateException("No such container"));
    }

    private boolean matches(Map<String, Collection<String>> filters, Container container) {
      if (filters == null || filters.isEmpty()) {
        return true;
      }
      if (filters.containsKey("name")) {
        Collection<String> names = filters.get("name");
        if (names != null && !names.contains(container.getName())) {
          return false;
        }
      }
      if (filters.containsKey("label")) {
        Collection<String> labels = filters.get("label");
        if (labels != null) {
          Map<String, String> containerLabels = container.getLabels() == null ? Collections.<String, String>emptyMap() : container.getLabels();
          boolean matched = labels.stream().allMatch(containerLabels::containsKey);
          if (!matched) {
            return false;
          }
        }
      }
      return true;
    }

    private Container copy(Container source) {
      Container copy = new Container();
      copy.setId(source.getId());
      copy.setIp(source.getIp());
      copy.setName(source.getName());
      copy.setImage(source.getImage());
      copy.setStatus(source.getStatus());
      copy.setNetwork(source.getNetwork());
      copy.setLabels(source.getLabels() == null ? null : new LinkedHashMap<String, String>(source.getLabels()));
      copy.setEnvironmentalVariables(source.getEnvironmentalVariables() == null ? null
          : new LinkedHashMap<String, String>(source.getEnvironmentalVariables()));
      copy.setVolumeBindings(source.getVolumeBindings() == null ? null : new LinkedHashMap<String, String>(source.getVolumeBindings()));
      if (source.getPortBindings() == null) {
        copy.setPortBindings(null);
      } else {
        Map<String, Map<Integer, Integer>> ports = new LinkedHashMap<String, Map<Integer, Integer>>();
        source.getPortBindings().forEach((protocol, bindings) -> ports.put(protocol, new LinkedHashMap<Integer, Integer>(bindings)));
        copy.setPortBindings(ports);
      }
      return copy;
    }
  }

}
