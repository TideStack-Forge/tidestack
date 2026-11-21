package com.ouroboros.develop.logicflowdebug;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.*;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.annotation.*;

import com.ouroboros.app.cache.SpringBeanCacheManager;
import com.ouroboros.container.config.DockerConfiguration;
import com.ouroboros.container.service.impl.DockerContainerServiceImpl;
import com.ouroboros.dev.DevelopWorkspaceContext;
import com.ouroboros.dev.app.service.runtime.LogicFlowDebugHelperArtifactMaterializer;
import com.ouroboros.dev.runtime.docker.backend.DockerTestAppRuntimeBackend;
import com.ouroboros.dev.runtime.model.*;
import com.ouroboros.dev.runtime.service.*;
import com.ouroboros.logicflow.dev.controller.LogicFlowDebugController;
import com.ouroboros.logicflow.dev.service.*;

/** LF-DBG-CONTAINER-001 / UI-001 harness. Authentication and business stimuli are explicit fixture boundaries. */
public final class DebugBrowserFixture implements AutoCloseable {
  public static final String APP = "debug-container-it";
  public static final String OWNER = "alice";
  public final Path root;
  public final DefaultDevRuntimeService runtimes;
  public final CacheLogicFlowDebugEventStore store;
  public final SpringBeanCacheManager client;
  private final LettuceConnectionFactory connection;
  private final DockerTestAppRuntimeBackend backend;
  private final DockerContainerServiceImpl containers;
  private final String region = "debug-container-it-" + UUID.randomUUID();
  private final RuntimeIdentityKey key = RuntimeIdentityKey.of(APP, OWNER, RuntimeKind.TEST_APP, BackendType.DOCKER);
  public ConfigurableApplicationContext context;

  @Configuration(proxyBeanMethods = false)
  @Import({DebugMqFixture.Platform.class, LogicFlowDebugController.class,
      com.ouroboros.app.controller.ExceptionControllerAdvice.class, com.ouroboros.web.WebApiResultHttpStatusAdvice.class,
      Stimulus.class})
  @ImportAutoConfiguration({ServletWebServerFactoryAutoConfiguration.class, DispatcherServletAutoConfiguration.class,
      WebMvcAutoConfiguration.class, HttpMessageConvertersAutoConfiguration.class})
  public static class Web {}

  @RestController
  public static class Stimulus {
    private final DebugBrowserFixture fixture;
    public Stimulus(DebugBrowserFixture fixture) { this.fixture = fixture; }
    @PostMapping("/fixture/execute/{kind}")
    public Map<String, String> execute(@PathVariable("kind") String kind) throws Exception {
      fixture.execute(kind); return Map.of("result", "queued");
    }
  }

  public DebugBrowserFixture(Path root) throws Exception {
    this.root = root.toAbsolutePath();
    Files.createDirectories(this.root.resolve("deploy"));
    var workspace = new DevelopWorkspaceContext() {
      @Override public String getRootDir() { return DebugBrowserFixture.this.root.toString(); }
      @Override public String getDevRootDir() { return getRootDir(); }
      @Override public String getRunRootDir() { return getRootDir(); }
      @Override public String getDebugSupportFilesDir() { return DebugBrowserFixture.this.root.resolve("deploy/extend").toString(); }
      @Override public String getDevelopSupportFilesDir() { return DebugBrowserFixture.this.root.resolve("support").toString(); }
    };
    new LogicFlowDebugHelperArtifactMaterializer(workspace).materialize().get();
    var configuration = new DockerConfiguration();
    configuration.setHost(System.getProperty("logicflow.docker.host"));
    containers = new DockerContainerServiceImpl();
    ReflectionTestUtils.setField(containers, "dockerConfiguration", configuration);
    backend = new DockerTestAppRuntimeBackend(containers);
    runtimes = new DefaultDevRuntimeService(List.of(backend), List.of(), new InMemoryRuntimeStateStore());
    connection = new LettuceConnectionFactory("127.0.0.1", Integer.parseInt(System.getProperty("logicflow.redis.port")));
    connection.afterPropertiesSet();
    var manager = RedisCacheManager.builder(connection).cacheDefaults(RedisCacheConfiguration.defaultCacheConfig()).build();
    manager.afterPropertiesSet();
    var beans = new StaticListableBeanFactory(Map.of("manager", manager, "connection", connection));
    client = new SpringBeanCacheManager(beans.getBeanProvider(org.springframework.cache.CacheManager.class),
        beans.getBeanProvider(RedisConnectionFactory.class));
    store = new CacheLogicFlowDebugEventStore(() -> client, region,
        System::currentTimeMillis, CacheLogicFlowDebugEventStore.Limits.defaults(), true);
  }

  public void create() throws Exception {
    Files.deleteIfExists(root.resolve("deploy/identity.json"));
    Files.createDirectories(root.resolve("deploy/commands"));
    try (var entries = Files.list(root.resolve("deploy/commands"))) {
      for (Path command : entries.toList()) Files.delete(command);
    }
    var spec = RuntimeSpec.builder().appName(APP).profile(OWNER).runtimeKind(RuntimeKind.TEST_APP)
        .backendType(BackendType.DOCKER).workspaceMode(WorkspaceMode.HOST_PATH)
        .image(System.getProperty("logicflow.docker.image"))
        .workspaceRef(WorkspaceRef.builder().workspaceMode(WorkspaceMode.HOST_PATH).hostPath(root.resolve("deploy").toString()).build())
        .labels(Map.of("codex.task", "logicflow-online-debug"))
        .env(Map.of("OUROBOROS_APP_INCARNATION_ID", UUID.randomUUID().toString(), "OUROBOROS_DEBUG_OWNER", OWNER,
            "OUROBOROS_DEV_HELPER_TEST_APP", "true")).build();
    backend.create(spec).get();
    backend.start(spec).get();
    long deadline = System.nanoTime() + Duration.ofSeconds(45).toNanos();
    while (!Files.isRegularFile(root.resolve("deploy/identity.json"))) {
      if (System.nanoTime() > deadline) throw new IllegalStateException("Container helper did not become ready");
      if (backend.getStatus(key).get().getStatus() != RuntimeStatus.RUNNING) {
        throw new IllegalStateException("Container helper stopped during startup: " + logs());
      }
      Thread.sleep(250);
    }
  }

  public ConfigurableApplicationContext startPlatform(boolean web) {
    System.setProperty("logicflow.fixture.web", Boolean.toString(web));
    LogicFlowDebugAccess access = new LogicFlowDebugAccess() {
      @Override public String currentOwner() { return OWNER; }
      @Override public boolean canDebug(String app, String profile, String owner) {
        return APP.equals(app) && OWNER.equals(owner) && (profile == null || OWNER.equals(profile));
      }
    };
    context = DebugMqFixture.start(web ? Web.class : DebugMqFixture.Platform.class, "develop-container-it", "it",
        Integer.parseInt(System.getProperty("logicflow.rabbit.port")),
        Map.of("debugStore", store, "runtimeService", runtimes, "debugAccess", access, "browserFixture", this));
    return context;
  }

  public void execute(String kind) throws Exception {
    if (!List.of("ok", "fail").contains(kind)) throw new IllegalArgumentException("Unknown fixture stimulus");
    Files.writeString(root.resolve("deploy/commands/" + UUID.randomUUID()), kind);
  }

  public String logs() {
    return String.join("\n", containers.getLogs("ouroboros-test-app-" + APP + "-" + OWNER, null).get());
  }

  public void remove() {
    var existing = containers.getContainerInfoByName("ouroboros-test-app-" + APP + "-" + OWNER);
    if (existing.isRight()) {
      if (!"logicflow-online-debug".equals(existing.get().getLabels().get("codex.task"))) {
        throw new IllegalStateException("Refusing to remove a container outside this fixture");
      }
      var logs = containers.getLogs(existing.get().getId(), null).get();
      try { Files.writeString(root.resolve("helper-" + System.currentTimeMillis() + ".log"), String.join("\n", logs)); }
      catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
      backend.stop(key).get();
      backend.delete(key).get();
    }
  }

  @Override public void close() {
    try {
      if (context != null) context.close();
      remove();
      client.clear(region);
    } finally {
      connection.destroy();
      var docker = (com.github.dockerjava.api.DockerClient) ReflectionTestUtils.getField(containers, "dockerClient");
      if (docker != null) {
        try { docker.close(); }
        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    try (var fixture = new DebugBrowserFixture(Path.of(args[0]))) {
      fixture.create(); fixture.startPlatform(true);
      System.out.println("BROWSER_FIXTURE_READY");
      System.in.read();
    }
  }
}
