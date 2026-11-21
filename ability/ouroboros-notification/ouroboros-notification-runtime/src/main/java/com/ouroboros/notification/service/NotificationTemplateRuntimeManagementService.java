package com.ouroboros.notification.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;

import jakarta.annotation.Priority;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.OuroborosException;
import com.ouroboros.notification.service.model.NotificationTemplateManagement.*;
import com.ouroboros.notification.service.model.NotificationTemplateModel;
import com.ouroboros.notification.template.NotificationTemplateCenter;
import com.ouroboros.notification.template.NotificationTemplateCenter.TemplateDefinition;
import com.ouroboros.notification.template.NotificationTemplateDefinitionValidator;
import com.ouroboros.notification.template.NotificationTemplateRevision;
import com.ouroboros.notification.template.provider.DatabaseNotificationTemplateProvider;
import com.ouroboros.util.Json;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Owns management commands; resolution, persistence and sending keep their original owners. */
@Service
public class NotificationTemplateRuntimeManagementService {
  private static final Logger log = LoggerFactory.getLogger(NotificationTemplateRuntimeManagementService.class);
  private final NotificationTemplateService persistence;
  private final NotificationTemplateDefinitionValidator validator;
  private final TransactionTemplate transaction;

  public NotificationTemplateRuntimeManagementService(NotificationTemplateService persistence,
      NotificationTemplateDefinitionValidator validator, PlatformTransactionManager transactions) {
    this.persistence = persistence; this.validator = validator;
    transaction = new TransactionTemplate(transactions);
    // This boundary must finish its commit before reporting post-commit visibility.
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Definition getRuntimeDefinition(String name) { return project(observe(name)); }

  public List<Definition> getRuntimeDefinitions() {
    try { return NotificationTemplateCenter.observeAllTemplates().values().stream().map(this::project).toList(); }
    catch (RuntimeException failure) { throw dependencyFailure(failure); }
  }

  public Validation validateRuntimeOverride(String name, Command command) {
    var definition = getRuntimeDefinition(name);
    requireEditable(definition); requireVersions(definition, command);
    return candidate(definition, command);
  }

  public WriteResult saveRuntimeOverride(String name, Command command, String actor) {
    requireActor(actor);
    var committed = Objects.requireNonNull(transaction.execute(status -> save(name, command, actor)));
    return confirm(committed);
  }

  public WriteResult restoreDefault(String name, Command command, String actor) {
    requireActor(actor);
    var committed = Objects.requireNonNull(transaction.execute(status -> {
      var observed = observe(name); var definition = project(observed);
      requireEditable(definition); requireVersions(definition, command);
      if (command.changes() != null && !command.changes().isEmpty()) {
        throw new BusinessException(400, "撤销覆盖请求不能包含模板修改");
      }
      var stored = stored(observed);
      if (stored == null) { return result(definition, false, true, null); }
      if (!persistence.deleteOverride(stored.getId(), stored.getRevision())) { throw conflict(definition, "OVERRIDE_CHANGED"); }
      return new WriteResult(definition.name(), true, true, false, definition.defaultRevision(), NotificationTemplateRevision.NONE, null, null);
    }));
    return confirm(committed);
  }

  private WriteResult save(String name, Command command, String actor) {
    var observed = observe(name); var definition = project(observed);
    requireEditable(definition); requireVersions(definition, command);
    var validation = candidate(definition, command);
    if (!validation.valid()) {
      throw new ManagementException(400, "[NOTIFICATION_TEMPLATE_INVALID] 通知模板检查未通过",
          new ErrorDetails("INVALID", definition.defaultRevision(), definition.overrideRevision(), validation.errors()));
    }
    if (validation.changes().isEmpty()) { return result(definition, false, true, null); }
    var old = stored(observed);
    var snapshot = Json.tryToBean(NotificationTemplateModel.class,
        Json.tryToJsonString(validation.candidate()).getOrElseThrow(error -> new BusinessException(400, "通知模板 JSON 不合法", error)))
        .getOrElseThrow(error -> new BusinessException(400, "通知模板无法转换为完整定义", error));
    snapshot.setNameKey(NotificationTemplateRevision.nameKey(snapshot.getName()));
    snapshot.setRevision(old == null ? 1L : Math.addExact(old.getRevision(), 1L));
    snapshot.setBaseDefaultRevision(definition.defaultRevision());
    snapshot.setUpdateBy(actor); snapshot.setUpdateAt(LocalDateTime.now());
    boolean changed;
    if (old == null) { changed = persistence.insertOverride(snapshot); }
    else { snapshot.setId(old.getId()); changed = persistence.replaceOverride(snapshot, old.getRevision()); }
    if (!changed) { throw conflict(definition, "OVERRIDE_CHANGED"); }
    return new WriteResult(definition.name(), true, true, false, definition.defaultRevision(),
        NotificationTemplateRevision.overrideRevision(snapshot), null, null);
  }

  private Validation candidate(Definition definition, Command command) {
    if (command.changes() == null) { throw new BusinessException(400, "changes 必须是对象"); }
    var candidateBuilder = Json.tryToMap(Json.tryToJsonString(definition.effectiveTemplate())
        .getOrElseThrow(error -> new BusinessException(400, "模板快照 JSON 不合法", error)))
        .getOrElseThrow(error -> new BusinessException(400, "模板快照不是对象", error));
    var errors = new ArrayList<Problem>();
    command.changes().forEach((field, value) -> {
      switch (field) {
        case "title" -> {
          if (!(value instanceof String text) || text.length() > 255) { errors.add(problem(field, "INVALID_TYPE", "标题必须为最多 255 个字符的字符串")); }
          else { candidateBuilder.put(field, text); }
        }
        case "isEnabled" -> {
          if (!(value instanceof Boolean)) { errors.add(problem(field, "INVALID_TYPE", "启用状态必须为布尔值")); }
          else { candidateBuilder.put(field, value); }
        }
        case "channel" -> {
          if (!(value instanceof Map<?, ?>)) { errors.add(problem(field, "INVALID_TYPE", "渠道定义必须是对象")); }
          else { candidateBuilder.put(field, value); }
        }
        default -> errors.add(problem(field, "READ_ONLY_OR_UNKNOWN_FIELD", "此字段只读或不支持修改"));
      }
    });
    // Detach request maps as well as the Provider snapshot before invoking extension validators.
    var candidate = Json.tryToMap(Json.tryToJsonString(candidateBuilder).getOrElseThrow(error -> new BusinessException(400, "候选模板 JSON 不合法", error)))
        .getOrElseThrow(error -> new BusinessException(400, "候选模板不是对象", error));
    errors.addAll(validator.validate(candidate, definition.effectiveTemplate()));
    var warnings = new ArrayList<>(validator.diagnostics(candidate));
    if (!Objects.equals(candidate.get("payloadSchema"), definition.defaultTemplate().get("payloadSchema"))) {
      warnings.add(problem("payloadSchema", "PAYLOAD_CONTRACT_CHANGED", "当前覆盖载荷契约与部署默认不同；核对升级后再撤销并重建覆盖"));
    }
    return new Validation(errors.isEmpty(), candidate, differences(definition.effectiveTemplate(), candidate),
        differences(definition.defaultTemplate(), candidate), List.copyOf(errors), List.copyOf(warnings));
  }

  private TemplateDefinition observe(String name) {
    try {
      return NotificationTemplateCenter.observeTemplate(name).orElseThrow(() -> new BusinessException(404, "通知模板不存在"));
    } catch (OuroborosException failure) {
      if (failure.getErrorCode() == 404 || failure.getErrorCode() == 400 || failure.getErrorCode() == 409) { throw failure; }
      throw dependencyFailure(failure);
    } catch (RuntimeException failure) {
      throw dependencyFailure(failure);
    }
  }

  private Definition project(TemplateDefinition observed) {
    var current = observed.effective(); var deployed = observed.deployedDefault(); var stored = stored(observed);
    var effective = NotificationTemplateRevision.snapshot(current.template());
    var defaults = deployed == null ? null : deployed.template() == current.template()
        ? effective : NotificationTemplateRevision.snapshot(deployed.template());
    String baseRevision = deployed == null ? null : NotificationTemplateRevision.defaultRevision(defaults);
    var diagnostics = new ArrayList<>(validator.diagnostics(effective));
    int overridePriority = DatabaseNotificationTemplateProvider.Proxy.class.getAnnotation(Priority.class).value();
    boolean supportedSource = observed.definitions().stream().filter(item -> item.runtimeOverride())
        .allMatch(item -> item.provider().equals(DatabaseNotificationTemplateProvider.Proxy.class.getName()));
    boolean canEdit = deployed != null && deployed.priority() > overridePriority && supportedSource
        && (stored == null || current.runtimeOverride());
    if (deployed == null) { diagnostics.add(problem("name", "DEFAULT_MISSING", "没有部署默认定义，历史数据库模板保持只读")); }
    else if (!canEdit) { diagnostics.add(problem("name", "OVERRIDE_SHADOWED", "当前来源优先级使数据库覆盖无法保证生效")); }
    String source = stored != null && current.runtimeOverride() ? (deployed == null ? "DATABASE_ONLY" : "OVERRIDE") : "DEFAULT";
    Boolean baseChanged = Boolean.FALSE;
    if (stored != null) {
      baseChanged = baseRevision == null || stored.getBaseDefaultRevision() == null
          ? null : Boolean.valueOf(!baseRevision.equals(stored.getBaseDefaultRevision()));
    }
    return new Definition(current.template().getName(), defaults, effective, stored != null, deployed != null,
        source, deployed == null ? null : deployed.provider(), current.provider(), baseRevision,
        NotificationTemplateRevision.overrideRevision(stored), baseChanged,
        stored == null ? null : stored.getUpdateBy(), stored == null ? null : stored.getUpdateAt(),
        Map.of("view", true, "edit", canEdit, "restore", canEdit && stored != null), List.copyOf(diagnostics),
        defaults == null ? List.of() : differences(defaults, effective));
  }

  private NotificationTemplateModel stored(TemplateDefinition observed) {
    return observed.definitions().stream()
        .filter(item -> item.provider().equals(DatabaseNotificationTemplateProvider.Proxy.class.getName()))
        .map(item -> item.template()).filter(NotificationTemplateModel.class::isInstance)
        .map(NotificationTemplateModel.class::cast).findFirst().orElse(null);
  }

  private void requireEditable(Definition definition) {
    if (!definition.defaultExists()) { throw conflict(definition, "DEFAULT_MISSING"); }
    if (!definition.availableActions().get("edit")) { throw conflict(definition, "OVERRIDE_SHADOWED"); }
  }

  private void requireVersions(Definition definition, Command command) {
    if (command == null || command.expectedDefaultRevision() == null || command.expectedDefaultRevision().isBlank()
        || command.expectedOverrideRevision() == null || command.expectedOverrideRevision().isBlank()) {
      throw new BusinessException(400, "[NOTIFICATION_TEMPLATE_VERSION_REQUIRED] 必须提供默认版本及覆盖版本");
    }
    if (!Objects.equals(definition.defaultRevision(), command.expectedDefaultRevision())) { throw conflict(definition, "DEFAULT_CHANGED"); }
    if (!Objects.equals(definition.overrideRevision(), command.expectedOverrideRevision())) { throw conflict(definition, "OVERRIDE_CHANGED"); }
  }

  private void requireActor(String actor) {
    if (actor == null || actor.isBlank()) { throw new BusinessException(403, "通知模板管理需要已认证身份"); }
  }

  private ManagementException conflict(Definition definition, String reason) {
    return new ManagementException(409, "[NOTIFICATION_TEMPLATE_" + reason + "] 模板状态已变化或不允许此次操作，请重新核对",
        new ErrorDetails(reason, definition.defaultRevision(), definition.overrideRevision(), List.of()));
  }

  private BusinessException dependencyFailure(Throwable failure) {
    return new BusinessException(503, "[NOTIFICATION_TEMPLATE_DEPENDENCY_UNAVAILABLE] 通知模板依赖读取失败", failure);
  }

  private WriteResult confirm(WriteResult committed) {
    if (!committed.changed()) { return committed; }
    try {
      var current = getRuntimeDefinition(committed.name());
      boolean confirmed = Objects.equals(committed.overrideRevision(), current.overrideRevision())
          && Objects.equals(committed.defaultRevision(), current.defaultRevision())
          && (NotificationTemplateRevision.NONE.equals(committed.overrideRevision()) || "OVERRIDE".equals(current.effectiveSource()));
      return new WriteResult(committed.name(), true, true, confirmed, committed.defaultRevision(), committed.overrideRevision(), current,
          confirmed ? null : "COMMITTED_VISIBILITY_UNCONFIRMED");
    } catch (RuntimeException failure) {
      // The commit succeeded. Report uncertainty explicitly; never imply rollback or retry the write.
      log.error("通知模板 {} 已提交，但生效状态核对失败", committed.name(), failure);
      return new WriteResult(committed.name(), true, true, false, committed.defaultRevision(), committed.overrideRevision(), null,
          "COMMITTED_VISIBILITY_UNCONFIRMED");
    }
  }

  private WriteResult result(Definition definition, boolean changed, boolean confirmed, String reason) {
    return new WriteResult(definition.name(), changed, changed, confirmed, definition.defaultRevision(), definition.overrideRevision(), definition, reason);
  }

  public static List<Difference> differences(Map<String, Object> before, Map<String, Object> after) {
    var differences = new ArrayList<Difference>(); differences("", before, after, differences); return List.copyOf(differences);
  }

  private static void differences(String path, Object before, Object after, List<Difference> differences) {
    if (Objects.equals(before, after)) { return; }
    if (before instanceof Map<?, ?> first && after instanceof Map<?, ?> second) {
      var keys = new TreeSet<String>(); first.keySet().forEach(key -> keys.add(String.valueOf(key))); second.keySet().forEach(key -> keys.add(String.valueOf(key)));
      keys.forEach(key -> {
        String fieldPath = path.isEmpty() ? key : path + "." + key;
        if (!first.containsKey(key) || !second.containsKey(key)) {
          differences.add(new Difference(fieldPath, first.containsKey(key) ? "REMOVE" : "ADD", first.get(key), second.get(key)));
        } else { differences(fieldPath, first.get(key), second.get(key), differences); }
      });
    } else { differences.add(new Difference(path, "REPLACE", before, after)); }
  }

  private Problem problem(String path, String code, String message) { return new Problem(path, null, code, message); }
}
