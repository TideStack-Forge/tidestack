package com.ouroboros.dev.runtime.kubernetes.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;

import com.ouroboros.dev.runtime.event.DevRuntimeEvent;
import com.ouroboros.dev.runtime.event.DevRuntimeEventPublisher;
import com.ouroboros.dev.runtime.kubernetes.config.KubernetesRuntimeProperties;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.ExposureStrategy;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.model.WorkspaceMode;
import com.ouroboros.dev.runtime.model.WorkspaceRef;
import com.ouroboros.devhelper.command.CommandFailureType;
import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeActivatedEvent;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol.HelperIdentity;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;

public final class KubernetesRuntimeTestSupport {
  private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-03-13T00:00:00Z"), ZoneOffset.UTC);
  private static final String NAMESPACE = "dev-space";
  private static final String APP_INCARNATION_ID = "incarnation-a";

  private KubernetesRuntimeTestSupport() {
  }

  public static Clock fixedClock() {
    return FIXED_CLOCK;
  }

  public static String namespace() {
    return NAMESPACE;
  }

  public static KubernetesRuntimeProperties properties() {
    KubernetesRuntimeProperties properties = new KubernetesRuntimeProperties();
    properties.setNamespace(NAMESPACE);
    properties.setSharedPvcName("dev-runtime-pvc");
    properties.setMountedWorkspaceRoot("/workspace");
    properties.setIngressHostSuffix("develop.example.com");
    return properties;
  }

  public static RuntimeSpec tempPortSpec() {
    return testAppSpec(ExposureStrategy.TEMP_PORT, Duration.ofMinutes(30));
  }

  public static RuntimeSpec stableDomainSpec() {
    return testAppSpec(ExposureStrategy.STABLE_DOMAIN, Duration.ofMinutes(30));
  }

  public static RuntimeSpec helperSpec() {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("dev-helper")
        .runtimeKind(RuntimeKind.DEV_HELPER)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(ExposureStrategy.INTERNAL_ONLY)
        .image("ouroboros/dev-helper:latest")
        .env(Collections.singletonMap(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, APP_INCARNATION_ID))
        .ttl(Duration.ofMinutes(30))
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName("dev-runtime-pvc")
            .namespace(NAMESPACE)
            .subPath("workspace/run/crm/dev-helper")
            .build())
        .build();
  }

  public static RuntimeSpec testAppSpec(ExposureStrategy exposureStrategy, Duration ttl) {
    return RuntimeSpec.builder()
        .appName("crm")
        .profile("alice")
        .runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.KUBERNETES)
        .workspaceMode(WorkspaceMode.PVC)
        .exposureStrategy(exposureStrategy)
        .image("ouroboros/test-app:latest")
        .env(testAppEnv())
        .ttl(ttl)
        .workspaceRef(WorkspaceRef.builder()
            .workspaceMode(WorkspaceMode.PVC)
            .pvcName("dev-runtime-pvc")
            .namespace(NAMESPACE)
            .subPath("workspace/run/crm/alice")
            .build())
        .build();
  }

  private static Map<String, String> testAppEnv() {
    return Collections.emptyMap();
  }

  public static Pod readyPod(RuntimeSpec spec) {
    RuntimeIdentityKey key = RuntimeIdentityKey.from(spec);
    return new PodBuilder()
        .withNewMetadata()
        .withName(KubernetesResourceNameFactory.deploymentName(key) + "-pod")
        .withNamespace(NAMESPACE)
        .withLabels(KubernetesResourceNameFactory.labels(key))
        .endMetadata()
        .withNewStatus()
        .withPhase("Running")
        .addNewCondition()
        .withType("Ready")
        .withStatus("True")
        .endCondition()
        .endStatus()
        .build();
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

  public static HelperRuntimeActivatedEvent helperActivated(
      String appName,
      String helperInstanceId,
      long startedAtEpochMillis
  ) {
    return new HelperRuntimeActivatedEvent(
        appName,
        new HelperIdentity(helperInstanceId, startedAtEpochMillis, APP_INCARNATION_ID),
        runtimeTarget(appName)
    );
  }

  public static MetadataSnapshotPublishedEvent publishedSnapshot(
      CommandRequest request
  ) {
    return new MetadataSnapshotPublishedEvent(
        request.getAppName(),
        request.getRuntimeGeneration(),
        request.getHelperInstanceId(),
        request.getHelperStartedAtEpochMillis().longValue(),
        request.getAppIncarnationId(),
        request.getRefreshOperationId(),
        1L,
        runtimeTarget(request.getAppName())
    );
  }

  public static HelperRuntimeTarget runtimeTarget(String appName) {
    RuntimeIdentityKey key = RuntimeIdentityKey.of(
        appName,
        "dev-helper",
        RuntimeKind.DEV_HELPER,
        BackendType.KUBERNETES
    );
    return new HelperRuntimeTarget(
        BackendType.KUBERNETES.name(),
        "dev-helper",
        KubernetesResourceNameFactory.deploymentName(key)
    );
  }

  public static CommandResult successResult(CommandRequest request) {
    return commandResult(request, true, null);
  }

  public static CommandResult timeoutResult(CommandRequest request) {
    return commandResult(request, false, "执行超时");
  }

  public static CommandResult responseTimeoutResult(CommandRequest request) {
    return commandResult(request, false, "等待开发助手响应超时")
        .setFailureType(CommandFailureType.RESPONSE_TIMEOUT);
  }

  public static CommandResult failureResult(
      CommandRequest request,
      String message
  ) {
    return commandResult(request, false, message);
  }

  private static CommandResult commandResult(
      CommandRequest request,
      boolean success,
      String message
  ) {
    return new CommandResult()
        .setAppName(request.getAppName())
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(request.getRuntimeGeneration())
        .setHelperInstanceId(request.getHelperInstanceId())
        .setHelperStartedAtEpochMillis(request.getHelperStartedAtEpochMillis())
        .setAppIncarnationId(request.getAppIncarnationId())
        .setRefreshOperationId(request.getRefreshOperationId())
        .setSuccess(success)
        .setMessage(message);
  }

  public static class RecordingDevHelperCommandService implements DevHelperCommandService {
    private final List<CommandRequest> requests = new ArrayList<CommandRequest>();
    private final List<Consumer<CommandResult>> globalListeners = new CopyOnWriteArrayList<Consumer<CommandResult>>();
    private final List<RuntimeException> execFailures = new ArrayList<RuntimeException>();

    public List<CommandRequest> requests() {
      return Collections.unmodifiableList(requests);
    }

    public void emit(CommandResult result) {
      for (Consumer<CommandResult> listener : globalListeners) {
        listener.accept(result);
      }
    }

    public void failNextExec(RuntimeException exception) {
      execFailures.add(exception);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params) {
      return execRough("crm", command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params, long timeout) {
      return execRough("crm", command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params) {
      return execRough(appName, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params, long timeout) {
      return record(appName, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params) {
      return exec("crm", command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params, long timeout) {
      return exec("crm", command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params) {
      return exec(appName, command, params, 30000L);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params, long timeout) {
      return record(appName, command, params, timeout);
    }

    @Override
    public void registerGlobalResultListener(Consumer<CommandResult> listener) {
      globalListeners.add(listener);
    }

    @Override
    public void unregisterGlobalResultListener(Consumer<CommandResult> listener) {
      globalListeners.remove(listener);
    }

    @Override
    public void registerCommandResultListener(String command, Consumer<CommandResult> listener) {
    }

    @Override
    public void unregisterCommandResultListener(String command, Consumer<CommandResult> listener) {
    }

    @Override
    public StreamChannel createStreamChannel(String channelName, Boolean strictInOrder) {
      return new StreamChannel() {
        @Override
        public void send(String command, Map<String, Object> params) {
        }

        @Override
        public void send(String command, Map<String, Object> params, long timeout) {
        }

        @Override
        public void send(String appName, String command, Map<String, Object> params) {
        }

        @Override
        public void send(String appName, String command, Map<String, Object> params, long timeout) {
        }

        @Override
        public void subscribe(Consumer<CommandResult> handler) {
        }

        @Override
        public void unsubscribe(Consumer<CommandResult> handler) {
        }
      };
    }

    @Override
    public void destroyStreamChannel(String channelName) {
    }

    private CompletableFuture<CommandResult> record(String appName, String command, Map<String, Object> params, long timeout) {
      if (!execFailures.isEmpty()) {
        throw execFailures.remove(0);
      }
      CommandRequest request = new CommandRequest()
          .setAppName(appName)
          .setCommand(command)
          .setParams(params == null ? Collections.<String, Object>emptyMap() : new LinkedHashMap<String, Object>(params))
          .setTimeout(timeout)
          .setRuntimeGeneration(resolveRuntimeGeneration(params))
          .setHelperInstanceId(resolveHelperInstanceId(params))
          .setHelperStartedAtEpochMillis(resolveHelperStartedAtEpochMillis(params))
          .setAppIncarnationId(resolveAppIncarnationId(params))
          .setRefreshOperationId(resolveRefreshOperationId(params));
      requests.add(request);
      return new CompletableFuture<CommandResult>();
    }

    private Long resolveRuntimeGeneration(Map<String, Object> params) {
      if (params == null) {
        return null;
      }
      Object value = params.get("runtimeGeneration");
      if (value instanceof Number) {
        return Long.valueOf(((Number) value).longValue());
      }
      if (value == null) {
        return null;
      }
      try {
        return Long.valueOf(value.toString());
      } catch (NumberFormatException exception) {
        return null;
      }
    }

    private String resolveHelperInstanceId(Map<String, Object> params) {
      if (params == null || params.get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY) == null) {
        return null;
      }
      return params.get(MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY).toString();
    }

    private Long resolveHelperStartedAtEpochMillis(Map<String, Object> params) {
      if (params == null) {
        return null;
      }
      Object value = params.get(MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY);
      return value instanceof Number ? Long.valueOf(((Number) value).longValue()) : null;
    }

    private String resolveRefreshOperationId(Map<String, Object> params) {
      if (params == null || params.get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY) == null) {
        return null;
      }
      return params.get(MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY).toString();
    }

    private String resolveAppIncarnationId(Map<String, Object> params) {
      if (params == null || params.get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY) == null) {
        return null;
      }
      return params.get(MetadataRefreshProtocol.APP_INCARNATION_ID_KEY).toString();
    }
  }
}
