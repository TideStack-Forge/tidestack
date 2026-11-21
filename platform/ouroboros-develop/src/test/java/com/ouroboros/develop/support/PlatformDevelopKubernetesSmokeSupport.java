package com.ouroboros.develop.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.springframework.context.ApplicationEventPublisher;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.api.model.PodBuilder;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.metadata.ConfigScope;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.constant.ConfigurationConstant;
import com.ouroboros.dev.app.initializer.DataSourceInitializer;
import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.dev.app.model.ContainerDatasource;
import com.ouroboros.dev.runtime.kubernetes.mapper.KubernetesResourceNameFactory;
import com.ouroboros.dev.runtime.model.BackendType;
import com.ouroboros.dev.runtime.model.RuntimeIdentityKey;
import com.ouroboros.dev.runtime.model.RuntimeKind;
import com.ouroboros.devhelper.command.CommandResult;
import com.ouroboros.devhelper.command.Commands;
import com.ouroboros.devhelper.dev.DevHelperCommandService;
import com.ouroboros.devhelper.metadata.HelperRuntimeTarget;
import com.ouroboros.devhelper.metadata.MetadataRefreshProtocol;
import com.ouroboros.devhelper.metadata.MetadataSnapshotPublishedEvent;

public final class PlatformDevelopKubernetesSmokeSupport {
  public static final String APP_NAME = "crm";
  public static final String APP_TITLE = "CRM";
  // Current platform flow still derives the test-app profile from the authenticated user.
  // Keep it centralized so later shared/custom profile work only changes one boundary.
  public static final String TEST_PROFILE = "alice";
  public static final String HELPER_PROFILE = "dev-helper";
  public static final String NAMESPACE = "dev-space";

  private PlatformDevelopKubernetesSmokeSupport() {
  }

  public static WorkspaceFixture createWorkspaceFixture() {
    try {
      return new WorkspaceFixture(Files.createTempDirectory("develop-k8s-smoke-"));
    } catch (IOException exception) {
      throw new IllegalStateException("failed to create workspace fixture", exception);
    }
  }

  public static void markTestAppReady(KubernetesClient kubernetesClient) {
    RuntimeIdentityKey key = RuntimeIdentityKey.of(APP_NAME, TEST_PROFILE, RuntimeKind.TEST_APP, BackendType.KUBERNETES);
    kubernetesClient.pods()
        .inNamespace(NAMESPACE)
        .resource(new PodBuilder()
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
        .build())
        .createOrReplace();
  }

  public static void markDevHelperReady(KubernetesClient kubernetesClient) {
    RuntimeIdentityKey key = devHelperIdentityKey();
    kubernetesClient.pods()
        .inNamespace(NAMESPACE)
        .resource(new PodBuilder()
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
        .build())
        .createOrReplace();
  }

  public static void emitDevHelperOnline(
      KubernetesClient kubernetesClient,
      RecordingDevHelperCommandService commandService,
      String helperInstanceId,
      long startedAtEpochMillis
  ) {
    Map<String, Object> data = new LinkedHashMap<String, Object>();
    MetadataRefreshProtocol.addHelperIdentity(
        data,
        new MetadataRefreshProtocol.HelperIdentity(
            helperInstanceId,
            startedAtEpochMillis,
            devHelperAppIncarnationId(kubernetesClient)
        )
    );
    commandService.emit(new CommandResult()
        .setAppName(APP_NAME)
        .setCommand(Commands.KNOCK_KNOCK.getCommand())
        .setHelperInstanceId(helperInstanceId)
        .setHelperStartedAtEpochMillis(Long.valueOf(startedAtEpochMillis))
        .setAppIncarnationId(devHelperAppIncarnationId(kubernetesClient))
        .setData(data)
        .setSuccess(true));
  }

  public static void emitRefreshSuccess(RecordingDevHelperCommandService commandService, Long runtimeGeneration) {
    commandService.emit(new CommandResult()
        .setAppName(APP_NAME)
        .setCommand(Commands.REFRESH_METADATA.getCommand())
        .setRuntimeGeneration(runtimeGeneration)
        .setSuccess(true));
  }

  public static void emitRefreshSuccess(
      ApplicationEventPublisher eventPublisher,
      RecordingDevHelperCommandService commandService,
      Long runtimeGeneration
  ) {
    emitRefreshSuccess(commandService, runtimeGeneration);
    RecordingDevHelperCommandService.CommandInvocation invocation = commandService.latestInvocations.get(APP_NAME);
    if (invocation == null || invocation.params == null) {
      throw new IllegalStateException("refresh command invocation is not available");
    }
    eventPublisher.publishEvent(new MetadataSnapshotPublishedEvent(
        APP_NAME,
        runtimeGeneration,
        textParameter(invocation.params, MetadataRefreshProtocol.HELPER_INSTANCE_ID_KEY),
        longParameter(invocation.params, MetadataRefreshProtocol.HELPER_STARTED_AT_EPOCH_MILLIS_KEY),
        textParameter(invocation.params, MetadataRefreshProtocol.APP_INCARNATION_ID_KEY),
        textParameter(invocation.params, MetadataRefreshProtocol.REFRESH_OPERATION_ID_KEY),
        1L,
        new HelperRuntimeTarget(
            BackendType.KUBERNETES.name(),
            HELPER_PROFILE,
            KubernetesResourceNameFactory.deploymentName(devHelperIdentityKey())
        )
    ));
  }

  public static RuntimeIdentityKey testAppIdentityKey() {
    return RuntimeIdentityKey.of(APP_NAME, TEST_PROFILE, RuntimeKind.TEST_APP, BackendType.KUBERNETES);
  }

  public static RuntimeIdentityKey devHelperIdentityKey() {
    return RuntimeIdentityKey.of(APP_NAME, HELPER_PROFILE, RuntimeKind.DEV_HELPER, BackendType.KUBERNETES);
  }

  public static boolean realClusterEnabled() {
    return isTruthy(System.getenv("K8S_PLATFORM_REAL_SMOKE"));
  }

  public static String realClusterEnvOrDefault(String name, String disabledDefaultValue) {
    String value = System.getenv(name);
    if (value != null && !value.trim().isEmpty()) {
      return value.trim();
    }
    return disabledDefaultValue;
  }

  public static RealClusterSettings realClusterSettings() {
    String testAppImage = requireEnv("K8S_SMOKE_IMAGE");
    String helperImage = optionalEnv("K8S_SMOKE_HELPER_IMAGE", testAppImage);
    return new RealClusterSettings(
        requireEnv("K8S_SMOKE_NAMESPACE"),
        requireEnv("K8S_SMOKE_PVC"),
        testAppImage,
        helperImage,
        optionalEnv("K8S_SMOKE_INGRESS_SUFFIX", "develop.example.com"),
        durationSeconds(optionalEnv("K8S_PLATFORM_REAL_TIMEOUT_SECONDS", "180"))
    );
  }

  public static KubernetesClient createRealClusterClient() {
    return new KubernetesClientBuilder().build();
  }

  public static void waitForCondition(String description, Duration timeout, Supplier<Boolean> condition) {
    waitForValue(description, timeout, () -> Boolean.TRUE.equals(condition.get()) ? Boolean.TRUE : null);
  }

  public static <T> T waitForValue(String description, Duration timeout, Supplier<T> supplier) {
    long timeoutMillis = timeout == null ? Duration.ofSeconds(60).toMillis() : timeout.toMillis();
    long deadline = System.currentTimeMillis() + Math.max(timeoutMillis, 1000L);
    RuntimeException lastError = null;

    while (System.currentTimeMillis() <= deadline) {
      try {
        T value = supplier.get();
        if (value != null) {
          return value;
        }
      } catch (RuntimeException exception) {
        lastError = exception;
      }
      sleepQuietly(1000L);
    }

    if (lastError != null) {
      throw new IllegalStateException("timed out waiting for " + description, lastError);
    }
    throw new IllegalStateException("timed out waiting for " + description);
  }

  public static void waitForTestAppReady(KubernetesClient kubernetesClient, RealClusterSettings settings) {
    RuntimeIdentityKey key = testAppIdentityKey();
    waitForCondition(
        "test-app ready pod",
        settings.getTimeout(),
        () -> hasReadyPod(kubernetesClient, settings.getNamespace(), key)
    );
  }

  public static void waitForHelperDeployment(KubernetesClient kubernetesClient, String namespace, Duration timeout) {
    RuntimeIdentityKey key = devHelperIdentityKey();
    waitForCondition(
        "dev-helper deployment",
        timeout,
        () -> kubernetesClient.apps().deployments()
            .inNamespace(namespace)
            .withName(KubernetesResourceNameFactory.deploymentName(key))
            .get() != null
    );
  }

  public static void cleanupRuntimeResources(KubernetesClient kubernetesClient, String namespace, RuntimeIdentityKey key) {
    if (kubernetesClient == null || namespace == null || key == null) {
      return;
    }
    kubernetesClient.apps().deployments()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.deploymentName(key))
        .delete();
    kubernetesClient.network().v1().ingresses()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.ingressName(key))
        .delete();
    kubernetesClient.services()
        .inNamespace(namespace)
        .withName(KubernetesResourceNameFactory.serviceName(key))
        .delete();
  }

  private static boolean hasReadyPod(KubernetesClient kubernetesClient, String namespace, RuntimeIdentityKey key) {
    List<Pod> pods = kubernetesClient.pods()
        .inNamespace(namespace)
        .withLabels(KubernetesResourceNameFactory.labels(key))
        .list()
        .getItems();
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

  private static String devHelperAppIncarnationId(KubernetesClient kubernetesClient) {
    Deployment deployment = kubernetesClient.apps().deployments()
        .inNamespace(NAMESPACE)
        .withName(KubernetesResourceNameFactory.deploymentName(devHelperIdentityKey()))
        .get();
    if (deployment == null
        || deployment.getSpec() == null
        || deployment.getSpec().getTemplate() == null
        || deployment.getSpec().getTemplate().getSpec() == null
        || deployment.getSpec().getTemplate().getSpec().getContainers() == null) {
      throw new IllegalStateException("dev-helper deployment is not available");
    }
    for (io.fabric8.kubernetes.api.model.Container container : deployment.getSpec().getTemplate().getSpec().getContainers()) {
      if (container == null || container.getEnv() == null) {
        continue;
      }
      for (io.fabric8.kubernetes.api.model.EnvVar env : container.getEnv()) {
        if (env != null
            && MetadataRefreshProtocol.APP_INCARNATION_ID_ENV.equals(env.getName())
            && env.getValue() != null
            && !env.getValue().trim().isEmpty()) {
          return env.getValue().trim();
        }
      }
    }
    throw new IllegalStateException("dev-helper app incarnation id is not available");
  }

  private static String textParameter(Map<String, Object> params, String name) {
    Object value = params.get(name);
    if (value == null || value.toString().trim().isEmpty()) {
      throw new IllegalStateException("missing refresh command parameter: " + name);
    }
    return value.toString().trim();
  }

  private static long longParameter(Map<String, Object> params, String name) {
    return Long.parseLong(textParameter(params, name));
  }

  private static String requireEnv(String name) {
    String value = System.getenv(name);
    if (value == null || value.trim().isEmpty()) {
      throw new IllegalStateException("required environment variable is missing: " + name);
    }
    return value.trim();
  }

  private static String optionalEnv(String name, String defaultValue) {
    String value = System.getenv(name);
    return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
  }

  private static Duration durationSeconds(String value) {
    try {
      return Duration.ofSeconds(Long.parseLong(value));
    } catch (NumberFormatException exception) {
      throw new IllegalStateException("invalid timeout seconds: " + value, exception);
    }
  }

  private static boolean isTruthy(String value) {
    if (value == null) {
      return false;
    }
    String normalized = value.trim().toLowerCase();
    return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
  }

  private static void sleepQuietly(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("interrupted while waiting", exception);
    }
  }

  public static final class RealClusterSettings {
    private final String namespace;
    private final String pvcName;
    private final String testAppImage;
    private final String helperImage;
    private final String ingressHostSuffix;
    private final Duration timeout;

    public RealClusterSettings(String namespace,
                               String pvcName,
                               String testAppImage,
                               String helperImage,
                               String ingressHostSuffix,
                               Duration timeout) {
      this.namespace = namespace;
      this.pvcName = pvcName;
      this.testAppImage = testAppImage;
      this.helperImage = helperImage;
      this.ingressHostSuffix = ingressHostSuffix;
      this.timeout = timeout == null ? Duration.ofSeconds(180) : timeout;
    }

    public String getNamespace() {
      return namespace;
    }

    public String getPvcName() {
      return pvcName;
    }

    public String getTestAppImage() {
      return testAppImage;
    }

    public String getHelperImage() {
      return helperImage;
    }

    public String getIngressHostSuffix() {
      return ingressHostSuffix;
    }

    public Duration getTimeout() {
      return timeout;
    }
  }

  public static final class WorkspaceFixture implements AutoCloseable {
    private final Path root;
    private final Path devRoot;
    private final Path runRoot;
    private final Path debugSupportRoot;
    private final Path developSupportRoot;
    private final Path mountedWorkspaceRoot;

    private WorkspaceFixture(Path root) throws IOException {
      this.root = root;
      this.devRoot = root.resolve("workspace").resolve("dev");
      this.runRoot = root.resolve("workspace").resolve("run");
      this.debugSupportRoot = root.resolve("debug-support");
      this.developSupportRoot = root.resolve("develop-support");
      this.mountedWorkspaceRoot = root.resolve("mounted");
      reset();
    }

    public void reset() {
      recreateDirectory(devRoot);
      recreateDirectory(runRoot);
      recreateDirectory(debugSupportRoot);
      recreateDirectory(developSupportRoot);
      recreateDirectory(mountedWorkspaceRoot);
      recreateDirectory(appRoot());
      write(appRoot().resolve("app.json"), "{\"app\":\"crm\",\"version\":\"v1\"}");
      write(debugSupportRoot.resolve("debug.txt"), "debug-support-v1");
      write(developSupportRoot.resolve("helper.txt"), "helper-support-v1");
    }

    public DevelopWorkspaceContext workspaceContext() {
      return new DevelopWorkspaceContext() {
        @Override
        public String getRootDir() {
          return root.resolve("workspace").toString();
        }

        @Override
        public String getDevRootDir() {
          return devRoot.toString();
        }

        @Override
        public String getRunRootDir() {
          return runRoot.toString();
        }

        @Override
        public String getDebugSupportFilesDir() {
          return debugSupportRoot.toString();
        }

        @Override
        public String getDevelopSupportFilesDir() {
          return developSupportRoot.toString();
        }
      };
    }

    public String mountedWorkspaceRoot() {
      return mountedWorkspaceRoot.toString();
    }

    public void writeAppMetadata(String fileName, String content) {
      write(appRoot().resolve(fileName), content);
    }

    public void writeDevelopSupportFile(String fileName, String content) {
      write(developSupportRoot.resolve(fileName), content);
    }

    public String readMountedMetadata(String profile, String fileName) {
      return read(mountedRuntimeRoot(profile).resolve("metadata").resolve(fileName));
    }

    public String readMountedDevelopSupport(String fileName) {
      return read(mountedRuntimeRoot(HELPER_PROFILE).resolve("extend").resolve(fileName));
    }

    public boolean mountedMetadataMatches(String profile, String fileName, String expected) {
      return expected.equals(readIfExists(mountedRuntimeRoot(profile).resolve("metadata").resolve(fileName)));
    }

    public boolean mountedDevelopSupportMatches(String fileName, String expected) {
      return expected.equals(readIfExists(mountedRuntimeRoot(HELPER_PROFILE).resolve("extend").resolve(fileName)));
    }

    private Path appRoot() {
      return devRoot.resolve(APP_NAME);
    }

    private Path mountedRuntimeRoot(String profile) {
      return mountedWorkspaceRoot.resolve("workspace").resolve("run").resolve(APP_NAME).resolve(profile);
    }

    private void recreateDirectory(Path path) {
      deleteRecursively(path);
      try {
        Files.createDirectories(path);
      } catch (IOException exception) {
        throw new IllegalStateException("failed to recreate directory " + path, exception);
      }
    }

    private void write(Path path, String content) {
      try {
        if (path.getParent() != null) {
          Files.createDirectories(path.getParent());
        }
        Files.write(path, content.getBytes(StandardCharsets.UTF_8));
      } catch (IOException exception) {
        throw new IllegalStateException("failed to write file " + path, exception);
      }
    }

    private String read(Path path) {
      try {
        return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
      } catch (IOException exception) {
        throw new IllegalStateException("failed to read file " + path, exception);
      }
    }

    private String readIfExists(Path path) {
      if (path == null || !Files.exists(path)) {
        return null;
      }
      return read(path);
    }

    private void deleteRecursively(Path path) {
      if (path == null || !Files.exists(path)) {
        return;
      }
      try (Stream<Path> walk = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
        walk.sorted(Comparator.reverseOrder()).forEach(current -> {
          try {
            Files.deleteIfExists(current);
          } catch (IOException exception) {
            throw new IllegalStateException("failed to delete " + current, exception);
          }
        });
      } catch (IOException exception) {
        throw new IllegalStateException("failed to walk " + path, exception);
      }
    }

    @Override
    public void close() {
      deleteRecursively(root);
    }
  }

  public static final class MapBackedConfigurationService implements ConfigurationService {
    private final Map<String, Object> values;

    public MapBackedConfigurationService(Map<String, Object> values) {
      this.values = new LinkedHashMap<String, Object>(values);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> java.util.Optional<T> get(String key) {
      return java.util.Optional.ofNullable((T) values.get(key));
    }

    @Override
    public Map<String, Object> getValues(ConfigScope scope) {
      if (scope == null) {
        return Collections.emptyMap();
      }
      Map<String, Object> scoped = new LinkedHashMap<String, Object>();
      for (Map.Entry<String, Object> entry : values.entrySet()) {
        if (entry.getKey().startsWith(scope.getValue())) {
          scoped.put(entry.getKey(), entry.getValue());
        }
      }
      return scoped;
    }
  }

  public static Map<String, Object> defaultConfigurationValues() {
    Map<String, Object> values = new LinkedHashMap<String, Object>();
    values.put(ConfigurationConstant.DEV_HELPER_CONTAINER_IMAGE, "ouroboros-mothership:latest");
    values.put(ConfigurationConstant.TEST_APP_CONTAINER_IMAGE, "ouroboros/test-app:latest");
    values.put(ConfigurationConstant.TEST_APP_PREFERRED_RUNTIME_BACKEND, BackendType.KUBERNETES.name());
    values.put(ConfigurationConstant.TEST_APP_DEFAULT_EXPOSURE_STRATEGY, "TEMP_PORT");
    values.put(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_DRIVER_CLASS, "org.h2.Driver");
    values.put(ConfigurationConstant.TEST_APP_MEMORY_DATASOURCE_URL, "jdbc:h2:mem:helper");
    return values;
  }

  public static final class MemoryDataSourceInitializer implements DataSourceInitializer {
    @Override
    public String getTypeName() {
      return "memory";
    }

    @Override
    public io.vavr.control.Try<ContainerDatasource> init(String appName,
                                                         String profileName,
                                                         String environmentName,
                                                         AppContainerRequest.DataSourceDefine dataSourceDefine) {
      return io.vavr.control.Try.success(new ContainerDatasource()
          .setDriverClassName("org.h2.Driver")
          .setUrl("jdbc:h2:mem:" + appName + "_" + profileName)
          .setUsername("sa")
          .setPassword(""));
    }
  }

  public static final class RecordingDevHelperCommandService implements DevHelperCommandService {
    private final List<Consumer<CommandResult>> globalListeners = new CopyOnWriteArrayList<Consumer<CommandResult>>();
    private final ConcurrentMap<String, List<Consumer<CommandResult>>> commandListeners = new ConcurrentHashMap<String, List<Consumer<CommandResult>>>();
    private final ConcurrentMap<String, CommandInvocation> latestInvocations = new ConcurrentHashMap<String, CommandInvocation>();

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params) {
      return execRough(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String command, Map<String, Object> params, long timeout) {
      return execRough(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params) {
      return record(appName, command, params, 0L);
    }

    @Override
    public CompletableFuture<CommandResult> execRough(String appName, String command, Map<String, Object> params, long timeout) {
      return record(appName, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params) {
      return exec(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String command, Map<String, Object> params, long timeout) {
      return exec(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params, timeout);
    }

    @Override
    public CompletableFuture<CommandResult> exec(String appName, String command, Map<String, Object> params) {
      return record(appName, command, params, 0L);
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
      commandListeners.computeIfAbsent(command, key -> new CopyOnWriteArrayList<Consumer<CommandResult>>()).add(listener);
    }

    @Override
    public void unregisterCommandResultListener(String command, Consumer<CommandResult> listener) {
      List<Consumer<CommandResult>> listeners = commandListeners.get(command);
      if (listeners != null) {
        listeners.remove(listener);
      }
    }

    @Override
    public StreamChannel createStreamChannel(String channelName, Boolean strictInOrder) {
      return new RecordingStreamChannel();
    }

    @Override
    public void destroyStreamChannel(String channelName) {
    }

    public Long latestRuntimeGeneration(String appName) {
      CommandInvocation invocation = latestInvocations.get(appName);
      return invocation == null ? null : invocation.runtimeGeneration;
    }

    public void emit(CommandResult result) {
      if (result == null) {
        return;
      }
      for (Consumer<CommandResult> listener : globalListeners) {
        listener.accept(result);
      }
      List<Consumer<CommandResult>> listeners = commandListeners.get(result.getCommand());
      if (listeners == null) {
        return;
      }
      for (Consumer<CommandResult> listener : listeners) {
        listener.accept(result);
      }
    }

    private CompletableFuture<CommandResult> record(String appName, String command, Map<String, Object> params, long timeout) {
      Long runtimeGeneration = null;
      if (params != null && params.get("runtimeGeneration") != null) {
        runtimeGeneration = Long.valueOf(String.valueOf(params.get("runtimeGeneration")));
      }
      latestInvocations.put(appName, new CommandInvocation(appName, command, runtimeGeneration, timeout, params));
      return CompletableFuture.completedFuture(new CommandResult()
          .setAppName(appName)
          .setCommand(command)
          .setRuntimeGeneration(runtimeGeneration)
          .setSuccess(true));
    }

    private final class RecordingStreamChannel implements StreamChannel {
      private final List<Consumer<CommandResult>> subscribers = new CopyOnWriteArrayList<Consumer<CommandResult>>();

      @Override
      public void send(String command, Map<String, Object> params) {
        record(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params, 0L);
      }

      @Override
      public void send(String command, Map<String, Object> params, long timeout) {
        record(PlatformDevelopKubernetesSmokeSupport.APP_NAME, command, params, timeout);
      }

      @Override
      public void send(String appName, String command, Map<String, Object> params) {
        record(appName, command, params, 0L);
      }

      @Override
      public void send(String appName, String command, Map<String, Object> params, long timeout) {
        record(appName, command, params, timeout);
      }

      @Override
      public void subscribe(Consumer<CommandResult> handler) {
        subscribers.add(handler);
      }

      @Override
      public void unsubscribe(Consumer<CommandResult> handler) {
        subscribers.remove(handler);
      }
    }

    private static final class CommandInvocation {
      private final String appName;
      private final String command;
      private final Long runtimeGeneration;
      private final long timeout;
      private final Map<String, Object> params;

      private CommandInvocation(
          String appName,
          String command,
          Long runtimeGeneration,
          long timeout,
          Map<String, Object> params
      ) {
        this.appName = appName;
        this.command = command;
        this.runtimeGeneration = runtimeGeneration;
        this.timeout = timeout;
        this.params = params == null ? Collections.emptyMap() : new LinkedHashMap<String, Object>(params);
      }
    }
  }
}
