package com.ouroboros.dev.app.service.impl;

import java.util.LinkedHashMap;

import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.container.service.ContainerService;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.service.runtime.TestAppRuntimeSpecFactory;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeInstance;
import com.ouroboros.dev.runtime.model.RuntimeStatus;
import com.ouroboros.dev.runtime.model.RuntimeSpec;
import com.ouroboros.dev.runtime.service.DevRuntimeService;
import com.ouroboros.dev.runtime.service.RuntimeStateStore;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.exception.BusinessException;

@Service
public class AppLifecycleService {

  private final AppConfigService appConfigService;
  private final AppRuntimeQueryService runtimeQueryService;
  private final TestAppRuntimeSpecFactory runtimeSpecFactory;
  private final DevRuntimeService devRuntimeService;
  private final RuntimeStateStore runtimeStateStore;
  private final ContainerService containerService;

  public AppLifecycleService(AppConfigService appConfigService,
                             AppRuntimeQueryService runtimeQueryService,
                             TestAppRuntimeSpecFactory runtimeSpecFactory,
                             DevRuntimeService devRuntimeService,
                             RuntimeStateStore runtimeStateStore,
                             ContainerService containerService) {
    this.appConfigService = appConfigService;
    this.runtimeQueryService = runtimeQueryService;
    this.runtimeSpecFactory = runtimeSpecFactory;
    this.devRuntimeService = devRuntimeService;
    this.runtimeStateStore = runtimeStateStore;
    this.containerService = containerService;
  }

  public Try<String> create(AppContainerRequest request, String profileName) {
    if (request == null) {
      return Try.failure(new BusinessException("测试应用运行时请求不能为空"));
    }
    return createInstance(request, profileName)
        .map(created -> created.instance.getRuntimeId());
  }

  public Try<String> createAndStart(AppContainerRequest request, String profileName) {
    if (request == null) {
      return Try.failure(new BusinessException("测试应用运行时请求不能为空"));
    }
    return createInstance(request, profileName)
        .flatMap(created -> devRuntimeService.start(RuntimeIdentityKey.from(created.spec))
            .map(instance -> created.instance.getRuntimeId()));
  }

  private Try<CreatedRuntime> createInstance(AppContainerRequest request, String profileName) {
    return Try.run(() -> appConfigService.resetConfigFile(request.getAppName(), profileName, request.getConfigFile()))
        .flatMap(v -> appConfigService.prepareRequest(request))
        .flatMap(prepared -> Try.of(() -> runtimeSpecFactory.create(prepared, profileName)))
        .map(this::preserveStoredIncarnation)
        .flatMap(spec -> appConfigService.buildRunWorkspace(spec.getAppName(), spec.getProfile())
            .map(v -> spec))
        .flatMap(spec -> devRuntimeService.create(spec)
            .map(instance -> new CreatedRuntime(spec, instance)));
  }

  private RuntimeSpec preserveStoredIncarnation(RuntimeSpec candidate) {
    var previous = runtimeStateStore.findSpec(RuntimeIdentityKey.from(candidate));
    if (previous.isEmpty()) {
      return candidate;
    }
    var env = new LinkedHashMap<>(candidate.getEnv());
    String incarnation = previous.get().getEnv().get(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
    if (incarnation == null) {
      env.remove(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV);
      env.remove("OUROBOROS_DEV_HELPER_TEST_APP");
      env.remove("OUROBOROS_DEBUG_OWNER");
    } else {
      env.put(MetadataRefreshProtocol.APP_INCARNATION_ID_ENV, incarnation);
    }
    return candidate.withEnv(env);
  }

  public Try<Void> delete(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> {
          if (!runtimeStateStore.findSpec(record.getKey()).isPresent() && record.getSpec().getBackendType() == BackendType.DOCKER) {
            String containerName = runtimeQueryService.containerNameOf(record);
            Try<Void> stopResult = runtimeQueryService.isActive(record.getInstance() == null ? null : record.getInstance().getStatus())
                ? Try.run(() -> containerService.stop(containerName).getOrElseThrow(error -> error))
                : Try.success(null);
            return stopResult.flatMap(ignore -> Try.run(() -> containerService.delete(containerName).getOrElseThrow(error -> error)));
          }
          Try<RuntimeInstance> stopResult = runtimeQueryService.isActive(record.getInstance() == null ? null : record.getInstance().getStatus())
              ? devRuntimeService.stop(record.getKey())
              : Try.success(record.getInstance());
          return stopResult.flatMap(instance -> devRuntimeService.delete(record.getKey()).map(v -> (Void) null));
        });
  }

  public Try<Void> start(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> appConfigService.buildRunWorkspace(record.getSpec().getAppName(), record.getSpec().getProfile())
            .flatMap(v -> devRuntimeService.start(record.getKey()))
            .map(instance -> (Void) null));
  }

  private static final class CreatedRuntime {
    private final RuntimeSpec spec;
    private final RuntimeInstance instance;

    private CreatedRuntime(RuntimeSpec spec, RuntimeInstance instance) {
      this.spec = spec;
      this.instance = instance;
    }
  }

  public Try<Void> stop(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> devRuntimeService.stop(record.getKey()).map(instance -> (Void) null));
  }

  public Try<Void> pause(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(this::ensurePauseSupported)
        .flatMap(record -> Try.run(() -> containerService.pause(runtimeQueryService.containerNameOf(record)).getOrElseThrow(error -> error)));
  }

  public Try<Void> unpause(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(this::ensurePauseSupported)
        .flatMap(record -> Try.run(() -> containerService.unpause(runtimeQueryService.containerNameOf(record)).getOrElseThrow(error -> error)));
  }

  public Try<Void> kill(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> {
          if (record.getSpec().getBackendType() != BackendType.DOCKER) {
            return devRuntimeService.stop(record.getKey()).map(instance -> (Void) null);
          }
          return Try.run(() -> containerService.kill(runtimeQueryService.containerNameOf(record)).getOrElseThrow(error -> error))
              .flatMap(v -> devRuntimeService.getStatus(record.getKey()).recover(x -> record.getInstance()).map(instance -> (Void) null));
        });
  }

  public Try<Void> restart(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> appConfigService.buildRunWorkspace(record.getSpec().getAppName(), record.getSpec().getProfile())
            .flatMap(v -> devRuntimeService.restart(record.getKey()))
            .map(instance -> (Void) null));
  }

  public Try<Void> forceRestart(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> appConfigService.buildRunWorkspace(record.getSpec().getAppName(), record.getSpec().getProfile())
            .flatMap(v -> devRuntimeService.stop(record.getKey()))
            .flatMap(v -> devRuntimeService.start(record.getKey()))
            .map(instance -> (Void) null));
  }

  public Try<Void> reloadWorkspace(String containerId) {
    return runtimeQueryService.resolveRuntimeById(containerId)
        .flatMap(record -> appConfigService.buildRunWorkspace(record.getSpec().getAppName(), record.getSpec().getProfile())
            .flatMap(v -> devRuntimeService.reloadWorkspace(record.getSpec()))
            .map(instance -> (Void) null));
  }

  private Try<AppRuntimeQueryService.RuntimeRecord> ensurePauseSupported(AppRuntimeQueryService.RuntimeRecord record) {
    if (record.getSpec().getBackendType() != BackendType.DOCKER) {
      return Try.failure(new BusinessException("当前运行时不支持该操作"));
    }
    if (record.getInstance() == null || record.getInstance().getCapabilities() == null || !record.getInstance().getCapabilities().isSupportsPause()) {
      return Try.failure(new BusinessException("当前运行时不支持该操作"));
    }
    return Try.success(record);
  }
}
