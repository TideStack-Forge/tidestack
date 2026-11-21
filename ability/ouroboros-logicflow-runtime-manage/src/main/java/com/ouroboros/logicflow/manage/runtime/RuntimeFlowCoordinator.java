package com.ouroboros.logicflow.manage.runtime;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent;
import com.ouroboros.logicflow.manage.provider.DatabaseFlowModelMetaProvider;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Instance;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Snapshot;
import com.ouroboros.logicflow.manage.service.RuntimeFlowApply;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowModelPublication;
import com.ouroboros.logicflow.model.FlowModelRefreshSupport.ApplyResult;
import com.ouroboros.logicflow.model.FlowModelSource;

/** One worker per boot reconciles durable latest state. Hints never carry authoritative definitions. */
@Component
public class RuntimeFlowCoordinator implements RuntimeFlowApply, AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RuntimeFlowCoordinator.class);
  private final RuntimeFlowStore store;
  private final DatabaseFlowModelMetaProvider source;
  private final FlowModelProvider runtime;
  private final ObjectProvider<ConfigurationService> configuration;
  private final String bootId = UUID.randomUUID().toString();
  private final String nodeName = System.getenv().getOrDefault("HOSTNAME", "local");
  private final ScheduledExecutorService worker;
  private final AtomicBoolean requested = new AtomicBoolean();
  private volatile boolean initialized;
  private long lastCheckNanos;
  private volatile Instance local;

  public RuntimeFlowCoordinator(RuntimeFlowStore store, DatabaseFlowModelMetaProvider source,
      FlowModelProvider runtime, ObjectProvider<ConfigurationService> configuration) {
    this.store = store; this.source = source; this.runtime = runtime; this.configuration = configuration;
    local = new Instance(bootId, nodeName, "uninitialized", "unobserved", "unobserved", "unpublished",
        "starting", null, Instant.now().toString());
    worker = Executors.newSingleThreadScheduledExecutor(task -> {
      var thread = new Thread(task, "logicflow-runtime-reconcile-" + bootId);
      thread.setDaemon(true);
      return thread;
    });
    worker.scheduleWithFixedDelay(this::tick, 1, 1, TimeUnit.SECONDS);
  }

  @EventListener(OuroborosModelRefreshingEvent.class)
  @Order(50)
  public void initializeStorage(OuroborosModelRefreshingEvent event) {
    source.accept(store.initialize());
    initialized = true;
  }

  @EventListener(OuroborosContextRefreshedEvent.class)
  @Order(200)
  public void contextRefreshed(OuroborosContextRefreshedEvent event) { request(); }

  @Override public String instanceId() { return bootId; }
  @Override public void request() { requested.set(true); }
  public Instance localStatus() { return local; }

  private void tick() {
    if (!initialized) { return; }
    try {
      long now = System.nanoTime();
      if (!requested.getAndSet(false) && now - lastCheckNanos < TimeUnit.SECONDS.toNanos(pollSeconds())) { return; }
      lastCheckNanos = now;
      reconcile();
    } catch (RuntimeException failure) {
      local = status(local.desiredVersion(), "failed", "应用核对失败: " + failure.getClass().getSimpleName());
      log.warn("逻辑流实例 {} 核对失败", bootId, failure);
      report();
    }
  }

  /** Reconcile without replaying Context events; publication itself remains version checked. */
  public synchronized void reconcile() {
    var snapshot = OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var captured = store.read();
      source.accept(captured);
      return captured;
    });
    var published = runtime.publication().orElseThrow(() -> new IllegalStateException("逻辑流运行时未提供完成态"));
    String desired = snapshot.version().token();
    if (published.complete() && desired.equals(appliedVersion(published))) {
      local = status(desired, "applied", null);
      report();
      return;
    }
    local = status(desired, "building", null);
    report();
    var overrides = new ArrayList<>(published.catalogue().sources().stream()
        .filter(FlowModelSource::runtimeOverride)
        .filter(value -> !DatabaseFlowModelMetaProvider.SOURCE_ID.equals(value.id())).toList());
    overrides.add(DatabaseFlowModelMetaProvider.source(snapshot));
    var roots = new java.util.LinkedHashSet<String>();
    snapshot.overrides().values().forEach(row -> roots.add(row.name()));
    var candidate = runtime.refreshSupport().orElseThrow(() -> new IllegalStateException("不支持独立刷新"))
        .prepare(overrides, Set.copyOf(roots));
    var latest = OuroborosContextRefreshLifecycle.withStableContext(store::read);
    if (!latest.version().equals(snapshot.version())) {
      requested.set(true);
      local = status(latest.version().token(), "pending", null);
      report();
      return;
    }
    var result = candidate.publish(Duration.ofSeconds(5));
    local = status(desired, result == ApplyResult.PUBLISHED ? "applied" : "failed",
        result == ApplyResult.PUBLISHED ? null : "独立刷新未发布: " + result);
    if (result == ApplyResult.STALE || result == ApplyResult.TIMED_OUT) { requested.set(true); }
    report();
  }

  private Instance status(String desired, String state, String error) {
    var published = runtime.publication().orElse(null);
    return new Instance(bootId, nodeName, desired, published == null ? "unobserved" : appliedVersion(published),
        published == null ? "unobserved" : published.catalogue().defaults().stamp(),
        published == null ? "unpublished" : published.id(), state, error, Instant.now().toString());
  }

  private static String appliedVersion(FlowModelPublication publication) {
    if (!publication.complete()) { return "partial"; }
    return publication.catalogue().sources().stream()
        .filter(value -> DatabaseFlowModelMetaProvider.SOURCE_ID.equals(value.id()))
        .map(FlowModelSource::version).findFirst().orElse("unobserved");
  }

  private void report() {
    try {
      OuroborosContextRefreshLifecycle.withStableContext(() -> { store.report(local); return null; });
    } catch (RuntimeException failure) {
      var previous = local;
      local = new Instance(previous.bootId(), previous.nodeName(), previous.desiredVersion(), previous.appliedVersion(),
          previous.defaultFingerprint(), previous.publicationId(), previous.status(),
          "实例回报失败: " + failure.getClass().getSimpleName(), Instant.now().toString());
      log.warn("逻辑流实例 {} 状态回报失败", bootId, failure);
    }
  }

  private int pollSeconds() {
    var service = configuration.getIfAvailable();
    int seconds = service == null ? 5 : service.get(Integer.class, "logicflow.runtimeManagement.pollSeconds").orElse(5);
    if (seconds < 1 || seconds > 300) { throw new IllegalStateException("逻辑流核对周期必须为 1 至 300 秒"); }
    return seconds;
  }

  @PreDestroy @Override public void close() { worker.shutdownNow(); }
}
