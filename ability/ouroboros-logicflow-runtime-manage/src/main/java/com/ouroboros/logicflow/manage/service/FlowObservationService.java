package com.ouroboros.logicflow.manage.service;

import com.ouroboros.logicflow.manage.observation.FlowObservationConfiguration;
import com.ouroboros.logicflow.manage.observation.RuntimeFlowObservation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.management.ConfigurationManagementService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.manage.repository.DataFlowObservationStore;
import com.ouroboros.logicflow.manage.web.RuntimeFlowAccess;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelDefinitions;

@Service
public class FlowObservationService {
  private final ObjectProvider<ConfigurationService> configurations;
  private final ObjectProvider<ConfigurationManagementService> management;
  private final DataFlowObservationStore store;
  private final RuntimeFlowObservation runtime;

  public FlowObservationService(ObjectProvider<ConfigurationService> configurations,
      ObjectProvider<ConfigurationManagementService> management, DataFlowObservationStore store, RuntimeFlowObservation runtime) {
    this.configurations = configurations; this.management = management; this.store = store; this.runtime = runtime;
  }
  public PolicyView read(String name) {
    String actual = requireModel(name);
    var snapshot = configurations.getObject().getSnapshot(FlowObservationConfiguration.KEY)
        .orElseThrow(() -> new BusinessException(503, "观测配置尚未就绪"));
    var policies = FlowObservationConfiguration.parse(snapshot.value());
    return new PolicyView(actual, policies.flows().getOrDefault(FlowModelDefinitions.nameKey(actual), Policy.off()),
        snapshot.revision(), RuntimeFlowAccess.permits(RuntimeFlowAccess.OBSERVE_READ), RuntimeFlowAccess.permits(RuntimeFlowAccess.OBSERVE_MANAGE));
  }
  public PolicyView save(String name, Command command) {
    requireModel(name);
    if (command.expectedRevision() == null || command.expectedRevision().isBlank()) { throw new BusinessException(400, "缺少观测配置版本"); }
    management.getObject().mutateManagementConfigurations(
        Map.of(FlowObservationConfiguration.KEY, new FlowObservationConfiguration.Change(name, command)),
        Map.of(FlowObservationConfiguration.KEY, command.expectedRevision()));
    return read(name);
  }
  public List<Status> statuses(String name) {
    requireModel(name);
    var statuses = new ArrayList<>(store.statuses());
    var local = runtime.localStatus();
    statuses.removeIf(value -> value.bootId().equals(local.bootId()));
    statuses.add(local);
    return List.copyOf(statuses);
  }
  public Metrics metrics(String name, int minutes, String bootId, String runtimeRevision) {
    requireModel(name);
    if (minutes != 15 && minutes != 60) { throw new BusinessException(400, "时间范围必须为 15 或 60 分钟"); }
    long to = System.currentTimeMillis();
    long from = to - minutes * 60_000L;
    var read = store.windows(name, from);
    var windows = read.stream().limit(5000)
        .filter(window -> bootId == null || bootId.isBlank() || bootId.equals(window.bootId()))
        .filter(window -> runtimeRevision == null || runtimeRevision.isBlank() || runtimeRevision.equals(window.runtimeRevision())).toList();
    long completed = windows.stream().mapToLong(Window::completed).sum();
    long failed = windows.stream().mapToLong(Window::failed).sum();
    long incomplete = windows.stream().mapToLong(Window::incomplete).sum();
    long[] histogram = new long[31];
    windows.forEach(window -> {
      for (int index = 0; index < Math.min(31, window.histogram().size()); index++) { histogram[index] += window.histogram().get(index); }
    });
    Double percentile = null;
    long count = 0;
    for (int index = 0; index < histogram.length && completed > 0; index++) {
      count += histogram[index];
      if (count >= Math.ceil(completed * 0.95)) { percentile = Math.pow(2, index); break; }
    }
    return new Metrics(!windows.isEmpty(), completed, failed, incomplete, completed == 0 ? null : failed * 100d / completed,
        percentile, from, to, read.size() > 5000, windows);
  }
  public List<Run> runs(String name) { requireModel(name); return store.runs(name, System.currentTimeMillis() - 24 * 3_600_000L); }
  public Run run(String name, String id) { requireModel(name); return store.run(name, id); }
  public List<Map<String, Object>> history(String name) { requireModel(name); return store.history(name); }
  private String requireModel(String name) {
    return FlowModelCenter.getFlowModel(name).map(value -> value.getName()).orElseThrow(() -> new BusinessException(404, "逻辑流不存在"));
  }
}
