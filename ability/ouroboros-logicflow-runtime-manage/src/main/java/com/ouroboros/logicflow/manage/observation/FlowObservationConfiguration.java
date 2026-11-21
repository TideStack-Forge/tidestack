package com.ouroboros.logicflow.manage.observation;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.observation.FlowObservation.*;
import com.ouroboros.logicflow.manage.web.RuntimeFlowAccess;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.util.Json;
import com.ouroboros.logicflow.manage.repository.DataFlowObservationStore;

/** The protected configuration transaction owns validation, expiry and audit identity. */
@Component
public class FlowObservationConfiguration implements ConfigurationManagementAccessPolicy {
  public static final String KEY = "logicflow.runtimeObservation.policies";
  private final Clock clock;
  private final DataFlowObservationStore store;

  public FlowObservationConfiguration(DataFlowObservationStore store) { this.clock = Clock.systemUTC(); this.store = store; }

  public record Change(String name, Command command) implements ConfigurationMutation { }

  public static Policies parse(Object value) {
    try {
      var map = Json.tryToMap(value instanceof String text ? text : Json.toJsonString(value))
          .getOrElseThrow(failure -> new IllegalArgumentException("观测配置 JSON 无效", failure));
      if (map.isEmpty()) { return new Policies(1, Map.of()); }
      if (!map.keySet().equals(Set.of("schemaVersion", "flows")) || !Integer.valueOf(1).equals(map.get("schemaVersion"))) {
        throw new IllegalArgumentException("不支持的观测配置版本");
      }
      var policies = Json.tryToBean(Policies.class, Json.toJsonString(map))
          .getOrElseThrow(failure -> new IllegalArgumentException("观测策略格式无效", failure));
      if (policies.flows().size() > 1000) { throw new IllegalArgumentException("观测配置超出容量"); }
      policies.flows().forEach((key, policy) -> {
        if (!key.equals(FlowModelDefinitions.nameKey(key)) || key.length() > 200
            || policy == null || policy.captureId() == null || policy.actor() == null || policy.reason() == null
            || policy.reason().isBlank() || policy.actor().isBlank() || policy.actor().length() > 255 || policy.captureId().length() > 64
            || policy.updatedAt() < 0 || policy.reason().length() > 200 || policy.diagnosticEnabled() && (!policy.monitorEnabled()
                || policy.diagnosticExpiresAt() <= 0 || policy.captureId().isBlank())) {
          throw new IllegalArgumentException("观测策略无效");
        }
      });
      return policies;
    } catch (RuntimeException failure) {
      throw new BusinessException(503, "观测配置无效", failure);
    }
  }

  @Override public boolean supports(String key) { return KEY.equals(key); }
  @Override public Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return Set.of(); }

  @Override
  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    if (!context.authenticated() || context.purpose() != ConfigurationManagementContext.Purpose.MANAGEMENT
        || !RuntimeFlowAccess.permits(RuntimeFlowAccess.OBSERVE_MANAGE)) { return Optional.empty(); }
    return Optional.of(snapshot);
  }

  @Override
  public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot previous,
      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    var access = new RuntimeFlowAccess().require(RuntimeFlowAccess.OBSERVE_MANAGE);
    if (!(mutation instanceof Change change)) { throw new BusinessException(403, "请从逻辑流页面修改观测设置"); }
    var model = FlowModelCenter.getFlowModel(change.name()).orElseThrow(() -> new BusinessException(404, "逻辑流不存在"));
    var command = change.command();
    if (command.monitorEnabled() == null || command.diagnosticEnabled() == null || command.reason() == null
        || command.reason().isBlank() || command.reason().length() > 200
        || command.reason().chars().anyMatch(Character::isISOControl)
        || command.diagnosticEnabled() && !command.monitorEnabled()) {
      throw new BusinessException(400, "观测设置不合法");
    }
    var flows = new LinkedHashMap<>(parse(previous.value()).flows());
    String key = FlowModelDefinitions.nameKey(model.getName());
    var before = flows.getOrDefault(key, Policy.off());
    long expiresAt = 0;
    String captureId = "";
    if (command.diagnosticEnabled()) {
      if (!Set.of(5, 15, 30).contains(command.durationMinutes() == null ? 15 : command.durationMinutes())) {
        throw new BusinessException(400, "诊断时长必须为 5、15 或 30 分钟");
      }
      // Keeping an active diagnostic does not replenish its lease or per-boot budget.
      expiresAt = before.diagnosticAt(clock.millis()) ? before.diagnosticExpiresAt()
          : clock.millis() + (command.durationMinutes() == null ? 15 : command.durationMinutes()) * 60_000L;
      captureId = before.diagnosticAt(clock.millis()) ? before.captureId() : UUID.randomUUID().toString();
    }
    var after = new Policy(command.monitorEnabled(), command.diagnosticEnabled(), expiresAt, captureId,
        access.actor(), command.reason().trim(), clock.millis());
    flows.put(key, after);
    if (flows.size() > 1000) { throw new BusinessException(400, "最多保存 1000 条流程观测策略"); }
    store.audit(key, before, after);
    return new ConfigurationPreparedWrite(new Policies(1, flows), access.actor());
  }
}
