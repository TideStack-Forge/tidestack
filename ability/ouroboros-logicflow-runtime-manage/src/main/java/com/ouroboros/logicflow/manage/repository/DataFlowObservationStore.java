package com.ouroboros.logicflow.manage.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Repository;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.logicflow.manage.model.*;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.util.Json;

/** Reporter writes cumulative windows and immutable runs with stable IDs, so a retry cannot double count. */
@Repository
public class DataFlowObservationStore {
  public void save(Window window) {
    var model = storage(RuntimeFlowMetricWindow.class);
    var row = new RuntimeFlowMetricWindow().setId(window.id()).setFlowName(window.flowName())
        .setObservedAt(window.startedAt()).setPayload(encode(window));
    if (model.query(Map.of("where", Map.of("id", window.id()))).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).isEmpty()) {
      model.insert(row).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      model.update(window.id(), Map.of("payload", row.getPayload())).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  public void save(Run run) {
    var model = storage(RuntimeFlowDiagnosticRun.class);
    if (model.query(Map.of("where", Map.of("id", run.id()))).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).isEmpty()) {
      model.insert(new RuntimeFlowDiagnosticRun().setId(run.id()).setFlowName(run.flowName())
          .setObservedAt(run.startedAt()).setPayload(encode(run))).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  public void report(Status status) {
    var model = storage(RuntimeFlowObservationStatus.class);
    if (model.query(Map.of("where", Map.of("id", status.bootId()))).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).isEmpty()) {
      model.insert(new RuntimeFlowObservationStatus().setId(status.bootId()).setFlowName("")
          .setObservedAt(status.reportedAt()).setPayload(encode(status))).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      model.update(status.bootId(), Map.of("observedAt", status.reportedAt(), "payload", encode(status)))
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  /** Called inside the original configuration transaction. */
  public void audit(String name, Policy before, Policy after) {
    storage(RuntimeFlowObservationChange.class).insert(new RuntimeFlowObservationChange().setId(UUID.randomUUID().toString())
        .setFlowName(name).setObservedAt(after.updatedAt()).setPayload(Map.of("before", encode(before), "after", encode(after))))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  public void expiry(String name, Policy policy) {
    var model = storage(RuntimeFlowObservationChange.class);
    String id = FlowModelDefinitions.hash("expiry|" + name + "|" + policy.captureId());
    if (model.get(id).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException) != null) { return; }
    var after = new Policy(policy.monitorEnabled(), false, policy.diagnosticExpiresAt(), policy.captureId(),
        "system-expiry", "诊断到期", policy.diagnosticExpiresAt());
    try {
      model.insert(new RuntimeFlowObservationChange().setId(id).setFlowName(name).setObservedAt(after.updatedAt())
          .setPayload(Map.of("before", encode(policy), "after", encode(after))))
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } catch (RuntimeException failure) {
      boolean duplicate = false;
      for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
        if (cause instanceof com.ouroboros.data.exception.UniqueConstraintViolationException) { duplicate = true; break; }
      }
      if (!duplicate) { throw failure; }
    }
  }

  public List<Window> windows(String name, long from) {
    return storage(RuntimeFlowMetricWindow.class).query(query(name, from, 5001))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().map(row -> decode(Window.class, row.getPayload())).toList();
  }
  public List<Run> runs(String name, long from) {
    return storage(RuntimeFlowDiagnosticRun.class).query(query(name, from, 100))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().map(row -> decode(Run.class, row.getPayload())).toList();
  }
  public Run run(String name, String id) {
    return storage(RuntimeFlowDiagnosticRun.class).query(Map.of("where", Map.of("id", id, "flowName", key(name))))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().findFirst()
        .map(row -> decode(Run.class, row.getPayload())).orElseThrow(() -> new BusinessException(404, "诊断样本不存在"));
  }
  public List<Status> statuses() {
    return storage(RuntimeFlowObservationStatus.class).query(Map.of("order", "observedAt desc", "limit", 500))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().map(row -> decode(Status.class, row.getPayload())).toList();
  }
  public List<Map<String, Object>> history(String name) {
    return storage(RuntimeFlowObservationChange.class).query(query(name, 0, 100))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().map(RuntimeFlowObservationChange::getPayload).toList();
  }

  public void cleanup(long now, int metricHours, int runHours) {
    storage(RuntimeFlowMetricWindow.class).delete(Map.of("observedAt", Map.of("$lt", now - metricHours * 3_600_000L)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    storage(RuntimeFlowDiagnosticRun.class).delete(Map.of("observedAt", Map.of("$lt", now - runHours * 3_600_000L)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    storage(RuntimeFlowObservationStatus.class).delete(Map.of("observedAt", Map.of("$lt", now - 24 * 3_600_000L)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private static Map<String, Object> query(String name, long from, int limit) {
    return Map.of("where", Map.of("flowName", key(name), "observedAt", Map.of("$gte", from)), "order", "observedAt desc", "limit", limit);
  }
  private static String key(String name) { return FlowModelDefinitions.nameKey(name); }
  private static Map<String, Object> encode(Object value) { return Json.toMap(Json.toJsonString(value)); }
  private static <T> T decode(Class<T> type, Map<String, Object> value) { return Json.tryToBean(type, Json.toJsonString(value)).get(); }
  private static <T> TypedDataModel<String, T> storage(Class<T> type) {
    return TypedDataModelCenter.getDataModel(String.class, type).orElseThrow(() -> new BusinessException(503, "观测存储尚未就绪"));
  }
}
