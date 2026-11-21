package com.ouroboros.logicflow.manage.observation;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.lifecycle.event.OuroborosContextRefreshedEvent;
import com.ouroboros.logicflow.diagnostic.FlowExecutionContext;
import com.ouroboros.logicflow.diagnostic.FlowRuntimeAnalyzer;
import com.ouroboros.logicflow.manage.repository.DataFlowObservationStore;
import com.ouroboros.logicflow.manage.runtime.RuntimeFlowCoordinator;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import io.vavr.control.Either;

/** Policy observation and persistence run on one background worker for this boot. */
@Component
public class RuntimeFlowObservation implements AutoCloseable {
  private static final Logger log = LoggerFactory.getLogger(RuntimeFlowObservation.class);
  private final DataFlowObservationStore store;
  private final ObjectProvider<ConfigurationService> configurations;
  private final FlowObservationCollector collector;
  private final ScheduledExecutorService worker;
  private volatile boolean ready;
  private volatile String error;
  private volatile FlowObservation.Status lastStatus;
  private long nextWarning;
  private long lastCleanup;
  private final java.util.Set<String> reportedExpiries = new java.util.HashSet<>();

  public RuntimeFlowObservation(DataFlowObservationStore store, ObjectProvider<ConfigurationService> configurations,
      RuntimeFlowCoordinator coordinator) {
    this.store = store; this.configurations = configurations;
    collector = new FlowObservationCollector(coordinator.instanceId());
    lastStatus = collector.status("starting");
    Bridge.current = collector;
    worker = Executors.newSingleThreadScheduledExecutor(task -> {
      var thread = new Thread(task, "logicflow-observation-" + coordinator.instanceId());
      thread.setDaemon(true); return thread;
    });
    worker.scheduleWithFixedDelay(this::tick, 1, 5, TimeUnit.SECONDS);
  }
  @EventListener(OuroborosContextRefreshedEvent.class)
  public void ready(OuroborosContextRefreshedEvent event) { ready = true; }
  public FlowObservation.Status localStatus() { return lastStatus; }

  public synchronized void tick() {
    if (!ready) { return; }
    try {
      OuroborosContextRefreshLifecycle.withStableContext(() -> {
        var service = configurations.getObject();
        var snapshot = service.getSnapshot(FlowObservationConfiguration.KEY).orElseThrow(() -> new IllegalStateException("缺少观测配置定义"));
        var policies = FlowObservationConfiguration.parse(snapshot.value());
        collector.accept(policies, snapshot.revision());
        reportedExpiries.retainAll(policies.flows().values().stream().map(FlowObservation.Policy::captureId).toList());
        policies.flows().forEach((name, policy) -> {
          if (policy.diagnosticEnabled() && policy.diagnosticExpiresAt() <= System.currentTimeMillis()
              && !reportedExpiries.contains(policy.captureId())) {
            store.expiry(name, policy);
            reportedExpiries.add(policy.captureId());
          }
        });
        collector.expire();
        for (var window : collector.windows()) { store.save(window); collector.acknowledgeWindow(window); }
        for (var run : collector.pending()) { store.save(run); collector.acknowledgeRun(run.id()); }
        if (System.currentTimeMillis() - lastCleanup >= 60_000) {
          int metricHours = service.get(Integer.class, "logicflow.runtimeObservation.metricRetentionHours").orElse(24);
          int runHours = service.get(Integer.class, "logicflow.runtimeObservation.runRetentionHours").orElse(1);
          if (metricHours < 1 || metricHours > 168 || runHours < 1 || runHours > 24) { throw new IllegalStateException("观测保留时长超出范围"); }
          store.cleanup(System.currentTimeMillis(), metricHours, runHours);
          lastCleanup = System.currentTimeMillis();
        }
        error = null;
        lastStatus = collector.status(null);
        store.report(lastStatus);
        return null;
      });
    } catch (RuntimeException failure) {
      collector.unavailable();
      collector.expire();
      error = failure.getClass().getSimpleName();
      boolean warn = nextWarning == 0 || System.nanoTime() - nextWarning >= 0;
      if (warn) {
        log.warn("逻辑流观测核对失败，暂停接纳新采集", failure);
        nextWarning = System.nanoTime() + TimeUnit.MINUTES.toNanos(1);
      }
      lastStatus = collector.status(error);
      try { store.report(lastStatus); }
      catch (RuntimeException reportFailure) {
        lastStatus = collector.status("report-unavailable");
        if (warn) { log.warn("逻辑流观测故障状态回报失败", reportFailure); }
      }
    }
  }

  @PreDestroy @Override public void close() {
    worker.shutdownNow();
    if (Bridge.current == collector) { Bridge.current = null; }
  }

  /** Original Analyzer SPI bridge, following the runtime provider's existing Spring/SPI assembly. */
  public static class Bridge implements FlowRuntimeAnalyzer {
    private static volatile FlowObservationCollector current;
    @Override public boolean isEnabled() { return current != null && current.isEnabled(); }
    @Override public boolean isEnabled(FlowModel model) { var value = current; return value != null && value.isEnabled(model); }
    @Override public void onFlowStart(FlowExecutionContext c, FlowModel m, List<Object> in) { var value = current; if (value != null) { value.onFlowStart(c, m, in); } }
    @Override public void onFlowCompleted(FlowExecutionContext c, FlowModel m, List<Object> in, List<Either<Throwable, Object>> results, Object out) { var value = current; if (value != null) { value.onFlowCompleted(c, m, in, results, out); } }
    @Override public void onFlowFailed(FlowExecutionContext c, FlowModel m, List<Object> in, List<Either<Throwable, Object>> results, Throwable failure) { var value = current; if (value != null) { value.onFlowFailed(c, m, in, results, failure); } }
    @Override public void onNodeStart(FlowExecutionContext c, FlowModel m, FlowNode n, List<Object> in) { var value = current; if (value != null) { value.onNodeStart(c, m, n, in); } }
    @Override public void onNodeCompleted(FlowExecutionContext c, FlowModel m, FlowNode n, List<Object> in, List<Object> out) { var value = current; if (value != null) { value.onNodeCompleted(c, m, n, in, out); } }
    @Override public void onNodeFailed(FlowExecutionContext c, FlowModel m, FlowNode n, List<Object> in, Throwable failure) { var value = current; if (value != null) { value.onNodeFailed(c, m, n, in, failure); } }
    @Override public void onNodeSkipped(FlowExecutionContext c, FlowModel m, FlowNode n, List<Object> in) { var value = current; if (value != null) { value.onNodeSkipped(c, m, n, in); } }
    @Override public void onNodeBlocked(FlowExecutionContext c, FlowModel m, FlowNode n, Throwable failure) { var value = current; if (value != null) { value.onNodeBlocked(c, m, n, failure); } }
  }
}
