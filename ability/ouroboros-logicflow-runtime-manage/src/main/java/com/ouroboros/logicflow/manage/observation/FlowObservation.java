package com.ouroboros.logicflow.manage.observation;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonAnySetter;

/** Values contain execution identity and timing, never business inputs, outputs or exception messages. */
public final class FlowObservation {
  private FlowObservation() { }

  public record Policy(boolean monitorEnabled, boolean diagnosticEnabled, long diagnosticExpiresAt,
      String captureId, String actor, String reason, long updatedAt) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知策略字段: " + name); }
    public static Policy off() { return new Policy(false, false, 0, "", "", "", 0); }
    public boolean diagnosticAt(long now) { return monitorEnabled && diagnosticEnabled && diagnosticExpiresAt > now; }
  }
  public record Policies(int schemaVersion, Map<String, Policy> flows) {
    public Policies { flows = Map.copyOf(flows); }
  }
  public record Command(String expectedRevision, Boolean monitorEnabled, Boolean diagnosticEnabled,
      Integer durationMinutes, String reason) {
    @JsonAnySetter public void unknown(String name, Object value) { throw new IllegalArgumentException("未知观测字段: " + name); }
  }
  public record PolicyView(String name, Policy policy, String revision, boolean canRead, boolean canManage) { }
  public record Span(String node, String type, String scope, long startedAt, double durationMillis,
      String result, String exceptionType) { }
  public record Run(String id, String flowName, String bootId, String parentInvocationId,
      String callerNodeName, String runtimeRevision, String policyRevision, long startedAt,
      double durationMillis, String result, String exceptionType, boolean truncated, List<Span> nodes) {
    public Run { nodes = List.copyOf(nodes); }
  }
  public record Window(String id, String flowName, String bootId, String runtimeRevision,
      String policyRevision, long startedAt, long updatedAt, long completed, long failed, long incomplete,
      List<Long> histogram) {
    public Window { histogram = List.copyOf(histogram); }
  }
  public record Status(String bootId, String revision, long reportedAt, String error, long rejected,
      long dropped, int active, Map<String, String> diagnostics) {
    public Status { diagnostics = Map.copyOf(diagnostics); }
  }
  public record Statuses(String name, String expectedRevision, List<Status> items) { }
  public record Metrics(boolean collected, long completed, long failed, long incomplete, Double failureRate,
      Double p95Millis, long from, long to, boolean partial, List<Window> windows) { }
}
