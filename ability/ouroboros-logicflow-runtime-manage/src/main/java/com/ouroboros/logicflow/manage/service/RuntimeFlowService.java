package com.ouroboros.logicflow.manage.service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.lifecycle.OuroborosContextRefreshLifecycle;
import com.ouroboros.logicflow.manage.provider.DatabaseFlowModelMetaProvider;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Change;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.Snapshot;
import com.ouroboros.logicflow.manage.repository.RuntimeFlowStore.StoredOverride;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.*;
import com.ouroboros.logicflow.model.FlowModelCenter;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelProvider;
import com.ouroboros.logicflow.model.FlowModelPublication;
import com.ouroboros.logicflow.model.FlowModelRefreshSupport.Candidate;
import com.ouroboros.logicflow.model.FlowModelSource;

/** Runtime management commands share formal source selection, the original factory and CAS storage. */
@Service
public class RuntimeFlowService {
  private static final String NO_DEFAULT = "无部署默认，不能编辑覆盖";
  private final RuntimeFlowStore store;
  private final FlowModelProvider runtime;
  private final RuntimeFlowApply apply;

  public RuntimeFlowService(RuntimeFlowStore store, FlowModelProvider runtime, RuntimeFlowApply apply) {
    this.store = store;
    this.runtime = runtime;
    this.apply = apply;
  }

  public Page list(String keyword, String filter, int page, int pageSize, Access access) {
    if (page < 1 || pageSize < 1 || pageSize > 100) { throw new BusinessException(400, "分页参数不合法"); }
    return OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var snapshot = store.read();
      var publication = publication();
      var names = new TreeSet<String>(String::compareToIgnoreCase);
      publication.catalogue().sources().forEach(source -> names.addAll(source.definitions().keySet()));
      snapshot.overrides().values().forEach(row -> names.add(row.name()));
      names.addAll(FlowModelCenter.getFlowModelMap().keySet());
      var values = names.stream().map(name -> view(name, snapshot, publication, access))
          .filter(view -> contains(view.name(), keyword) || contains(view.description(), keyword))
          .filter(view -> switch (filter == null ? "all" : filter) {
            case "overridden" -> view.override() != null && view.override().active();
            case "default-changed" -> view.defaultChanged();
            case "readonly" -> !view.readOnlyReasons().isEmpty();
            case "all", "" -> true;
            default -> throw new BusinessException(400, "不支持的筛选条件");
          }).map(view -> new Summary(view.name(), view.description(), view.defaultSource(),
              view.override() != null && view.override().active(), view.defaultChanged(), view.readOnlyReasons().isEmpty(),
              state(view), view.override() == null ? null : view.override().actor(),
              view.override() == null ? null : view.override().updatedAt(), view.valid())).toList();
      long offset = (long) (page - 1) * pageSize;
      return new Page(values.stream().skip(offset).limit(pageSize).toList(), values.size(),
          apply.instanceId(), snapshot.version().token());
    });
  }

  public View read(String name, Access access) {
    requireName(name);
    return OuroborosContextRefreshLifecycle.withStableContext(() -> view(name, store.read(), publication(), access));
  }

  public Operations operations(String name, Access access) {
    var view = read(name, access);
    var observedAt = Instant.now();
    var defaults = publication().catalogue().defaults().stamp();
    var instances = store.instances().stream().map(instance -> {
      boolean recent = Instant.parse(instance.reportedAt()).plusSeconds(30).isAfter(observedAt);
      String state = !recent ? "stale" : !Objects.equals(instance.error(), null) || instance.status().equals("failed") ? "failed"
          : view.catalogVersion().equals(instance.appliedVersion()) && instance.status().equals("applied") ? "applied" : "pending";
      return new InstanceView(instance, state, recent, instance.bootId().equals(view.instanceId()), !defaults.equals(instance.defaultFingerprint()));
    }).toList();
    return new Operations(view, state(view), observedAt.toEpochMilli(), instances, store.history(view.name()));
  }

  public Validation validate(String name, ValidationCommand command, Access access) {
    requireEdit(access);
    return OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var snapshot = store.read();
      var view = view(name, snapshot, publication(), access);
      requireEditable(view, false);
      requireToken(view, command.editToken());
      var formal = requireDefinition(view, command.definition());
      var candidate = candidate(snapshot, view, formal, access.actor());
      return validation(candidate, view, formal);
    });
  }

  public Change save(String name, Command command, Access access) {
    requireEdit(access);
    requireName(name);
    requireOperationId(command.operationId());
    String definition = command.definition() == null ? null : FlowModelDefinitions.write(command.definition());
    String hash = requestHash("save", name, command.editToken(), definition, null);
    return write(name, command.operationId(), command.editToken(), definition, null, "save", hash, access);
  }

  public Change revoke(String name, VersionedCommand command, Access access) {
    requireEdit(access);
    requireName(name);
    requireOperationId(command.operationId());
    return write(name, command.operationId(), command.editToken(), null, null, "revoke",
        requestHash("revoke", name, command.editToken(), null, null), access);
  }

  public Change restore(String name, RestoreCommand command, Access access) {
    requireEdit(access);
    requireName(name);
    requireOperationId(command.operationId());
    requireOperationId(command.fromChangeId());
    return write(name, command.operationId(), command.editToken(), null, command.fromChangeId(), "restore",
        requestHash("restore", name, command.editToken(), null, command.fromChangeId()), access);
  }

  private Change write(String name, String operationId, String token, String definition, String fromChange,
      String action, String requestHash, Access access) {
    requireName(name);
    var committed = OuroborosContextRefreshLifecycle.withStableContext(() -> {
      var replay = store.operation(operationId);
      if (replay.isPresent()) { return replay(replay.get(), requestHash, access.actor()); }
      var snapshot = store.read();
      var view = view(name, snapshot, publication(), access);
      requireToken(view, token);
      String target = definition;
      if (fromChange != null) {
        var previous = store.operation(fromChange).orElseThrow(() -> new BusinessException(404, "恢复来源操作不存在"));
        if (!previous.name().equalsIgnoreCase(name)) { throw new BusinessException(400, "恢复来源不属于当前逻辑流"); }
        target = previous.before() != null && previous.before().active() ? previous.before().definition() : null;
      }
      requireEditable(view, target == null);
      if (target != null) { target = requireDefinition(view, FlowModelDefinitions.read(target)); }
      var candidate = candidate(snapshot, view, target, access.actor());
      if (!candidate.report().valid(candidate.checkScope())) {
        throw new BusinessException(422, "逻辑流候选检查未通过，请先检查并处理诊断");
      }
      var before = snapshot.overrides().get(FlowModelDefinitions.nameKey(view.name()));
      boolean changed = target == null ? before != null && before.active()
          : before == null || !before.active() || !target.equals(before.definition())
              || !view.defaultHash().equals(before.baseDefaultHash());
      var after = changed ? new StoredOverride(view.name(), before == null ? 1 : Math.addExact(before.version(), 1),
          target != null, target, view.defaultHash(), access.actor(), Instant.now().toString()) : before;
      var change = new Change(operationId, requestHash, access.actor(), action, view.name(), before, after,
          snapshot.version().next(), Instant.now().toString(), changed);
      return store.commit(snapshot.version(), change);
    });
    apply.request();
    return committed;
  }

  public Operation operation(String id, Access access) {
    requireOperationId(id);
    var change = store.operation(id).orElseThrow(() -> new BusinessException(404, "逻辑流操作不存在"));
    var current = store.read().version();
    return new Operation(change, current.token(), !current.equals(change.committedVersion()), store.instances());
  }

  public ApplyReceipt requestApply(String expectedVersion, Access access) {
    if (!access.reload()) { throw new BusinessException(403, "没有重新加载逻辑流权限"); }
    var current = store.read().version().token();
    if (!current.equals(expectedVersion)) { throw new BusinessException(409, "逻辑流覆盖集合已变化，请刷新后重试"); }
    apply.request();
    return new ApplyReceipt(apply.instanceId(), current, "pending");
  }

  private Candidate candidate(Snapshot snapshot, View view, String definition, String actor) {
    var rows = new LinkedHashMap<>(snapshot.overrides());
    String key = FlowModelDefinitions.nameKey(view.name());
    var before = rows.get(key);
    rows.put(key, new StoredOverride(view.name(), before == null ? 1 : before.version() + 1,
        definition != null, definition, view.defaultHash(), actor, Instant.now().toString()));
    var overrides = new ArrayList<>(publication().catalogue().sources().stream()
        .filter(FlowModelSource::runtimeOverride)
        .filter(source -> !DatabaseFlowModelMetaProvider.SOURCE_ID.equals(source.id())).toList());
    overrides.add(DatabaseFlowModelMetaProvider.source(new Snapshot(snapshot.version().next(), rows)));
    return runtime.refreshSupport().orElseThrow(() -> new BusinessException(503, "当前运行时不支持逻辑流独立刷新"))
        .prepare(overrides, Set.of(view.name()));
  }

  private Validation validation(Candidate candidate, View view, String definition) {
    String before = view.override() != null && view.override().active()
        ? view.override().definition() : view.defaultDefinition() == null ? null : FlowModelDefinitions.write(view.defaultDefinition());
    return new Validation(candidate.report().valid(candidate.checkScope()), candidate.report(),
        candidate.report().impactedBy(Set.of(view.name())), FlowModelDefinitions.hash(definition),
        candidate.basePublicationId(), FlowModelDifferences.between(before, definition));
  }

  private View view(String requestedName, Snapshot snapshot, FlowModelPublication publication, Access access) {
    requireName(requestedName);
    String key = FlowModelDefinitions.nameKey(requestedName);
    var defaults = publication.catalogue().defaults();
    var defaultEntry = defaults.definitions().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(requestedName)).findFirst();
    var row = snapshot.overrides().get(key);
    var model = FlowModelCenter.getFlowModel(requestedName).orElse(null);
    var observed = publication.catalogue().sources().stream()
        .flatMap(source -> source.definitions().entrySet().stream())
        .filter(entry -> entry.getKey().equalsIgnoreCase(requestedName))
        .map(entry -> FlowModelDefinitions.read(entry.getValue())).reduce((lower, higher) -> higher);
    if (defaultEntry.isEmpty() && row == null && model == null && observed.isEmpty()) {
      throw new BusinessException(404, "逻辑流不存在");
    }
    var defaultMeta = defaultEntry.map(entry -> FlowModelDefinitions.read(entry.getValue())).orElse(null);
    String name = defaultMeta != null ? defaultMeta.getName() : row != null ? row.name()
        : model != null ? model.getName() : observed.orElseThrow().getName();
    String defaultHash = defaultEntry.map(entry -> FlowModelDefinitions.hash(entry.getValue())).orElse("NONE");
    var exact = publication.definitions().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name)).findFirst();
    boolean owned = FlowModelCenter.providerFor(name).flatMap(FlowModelProvider::publication)
        .map(value -> value.id().equals(publication.id())).orElse(false);
    var reasons = new ArrayList<String>();
    if (defaultMeta == null) { reasons.add(NO_DEFAULT); }
    if (!owned || runtime.refreshSupport().isEmpty()) { reasons.add("执行来源不支持受管独立刷新"); }
    publication.catalogue().sources().forEach(source -> {
      if (!source.complete()) { reasons.add("来源不完整: " + source.id()); }
      boolean defines = source.definitions().keySet().stream().anyMatch(value -> value.equalsIgnoreCase(name));
      if (defines && !source.declared()) { reasons.add("来源未声明: " + source.id()); }
      if (defines && !source.id().equals(DatabaseFlowModelMetaProvider.SOURCE_ID)
          && (source.runtimeOverride() || source.priority() >= DatabaseFlowModelMetaProvider.PRIORITY)) {
        reasons.add("来源优先级冲突: " + source.id());
      }
    });
    var actions = new ArrayList<String>();
    if (access.edit() && reasons.isEmpty()) { actions.add("edit"); }
    if (access.edit() && reasons.stream().allMatch(NO_DEFAULT::equals) && row != null && row.active()) { actions.add("revoke"); }
    if (access.reload()) { actions.add("apply"); }
    String token = FlowModelDefinitions.fingerprint(Map.of("catalog", snapshot.version().token(),
        "override", row == null ? "NONE" : Long.toString(row.version()), "default", defaults.stamp(), "name", key));
    String appliedVersion = publication.catalogue().sources().stream()
        .filter(source -> DatabaseFlowModelMetaProvider.SOURCE_ID.equals(source.id()))
        .map(FlowModelSource::version).findFirst().orElse("unobserved");
    var sources = publication.catalogue().sources().stream().flatMap(source -> source.definitions().entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .map(entry -> new Source(source.id(), source.priority(), source.runtimeOverride(), source.declared(), source.complete(),
            Boolean.TRUE.equals(FlowModelDefinitions.read(entry.getValue()).isEnabled()),
            source.id().equals(defaults.origins().get(name)), source.version(), source.diagnostics()))).toList();
    return new View(name, defaultMeta != null ? defaultMeta.getDescription() : model != null ? model.getDescription()
            : observed.map(FlowModelMeta::getDescription).orElse(null),
        defaults.origins().get(name), defaultMeta, row, owned ? exact.map(entry -> FlowModelDefinitions.read(entry.getValue())).orElse(null) : null,
        owned && exact.isPresent(), model == null ? null : model.getRevision(), token, snapshot.version().token(), appliedVersion, defaultHash,
        publication.id(), List.copyOf(reasons), List.copyOf(actions), row != null && row.active()
            && !defaultHash.equals(row.baseDefaultHash()), apply.instanceId(), formalMap(row != null && row.active() ? row.definition() : defaultEntry.map(Map.Entry::getValue).orElse(null)),
        UUID.randomUUID().toString(), sources, model == null ? null : model.isValid(),
        model == null ? List.of() : model.getErrors().stream()
            .map(error -> Objects.toString(error.getMessage(), error.getClass().getSimpleName())).toList());
  }

  private static Map<String, Object> formalMap(String definition) {
    return definition == null ? null : com.ouroboros.util.Json.tryToMap(definition).get();
  }

  private FlowModelPublication publication() {
    return runtime.publication().filter(value -> !value.id().equals("unpublished"))
        .orElseThrow(() -> new BusinessException(503, "逻辑流完成态尚未可用"));
  }

  private String requireDefinition(View view, FlowModelMeta meta) {
    if (meta == null || !view.name().equals(meta.getName())) { throw new BusinessException(400, "不能创建或重命名逻辑流"); }
    var baseline = Objects.requireNonNull(view.defaultDefinition());
    if (!Objects.equals(meta.isEnabled(), baseline.isEnabled())
        || !Objects.equals(meta.getInputDataType(), baseline.getInputDataType())
        || !Objects.equals(meta.getOutputDataType(), baseline.getOutputDataType())
        || !FlowModelDefinitions.fingerprint(meta.getInputSchema()).equals(FlowModelDefinitions.fingerprint(baseline.getInputSchema()))
        || !FlowModelDefinitions.fingerprint(meta.getOutputSchema()).equals(FlowModelDefinitions.fingerprint(baseline.getOutputSchema()))) {
      throw new BusinessException(400, "不能修改逻辑流启用状态或对外输入输出合同");
    }
    if (meta.getNodes().size() > 2000 || meta.getConnections().size() > 8000) { throw new BusinessException(400, "逻辑流图规模超过上限"); }
    String definition = FlowModelDefinitions.write(meta);
    if (definition.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 2_000_000) { throw new BusinessException(400, "逻辑流定义超过 2 MB"); }
    return definition;
  }

  private static String state(View view) {
    if (!view.exactMetadata()) { return "unobserved"; }
    if (!view.catalogVersion().equals(view.appliedCatalogVersion())) { return "pending"; }
    String saved = view.override() != null && view.override().active() ? view.override().definition()
        : view.defaultDefinition() == null ? null : FlowModelDefinitions.write(view.defaultDefinition());
    return Objects.equals(saved, FlowModelDefinitions.write(view.publishedDefinition())) ? "applied" : "pending";
  }

  private static Change replay(Change stored, String hash, String actor) {
    if (!stored.requestHash().equals(hash) || !stored.actor().equals(actor)) { throw new BusinessException(409, "operationId 已用于其他请求"); }
    return stored;
  }
  private static String requestHash(String action, String name, String token, String definition, String previous) {
    var values = new LinkedHashMap<String, Object>();
    values.put("action", action); values.put("name", FlowModelDefinitions.nameKey(name));
    values.put("editToken", token); values.put("definition", definition); values.put("fromChange", previous);
    return FlowModelDefinitions.fingerprint(values);
  }
  private static void requireToken(View view, String token) {
    if (!view.editToken().equals(token)) { throw new BusinessException(409, "逻辑流版本或默认来源已变化；请保留草稿并重新对比"); }
  }
  private static void requireEditable(View view, boolean revoke) {
    if (view.readOnlyReasons().stream().anyMatch(reason -> !revoke || !NO_DEFAULT.equals(reason))) {
      throw new BusinessException(409, "逻辑流当前只读: " + String.join("；", view.readOnlyReasons()));
    }
  }
  private static void requireEdit(Access access) {
    if (access == null || !access.edit() || access.actor() == null || access.actor().isBlank()) { throw new BusinessException(403, "没有编辑逻辑流权限"); }
  }
  private static void requireOperationId(String id) {
    try { if (!UUID.fromString(id).toString().equals(id)) { throw new IllegalArgumentException("noncanonical"); } }
    catch (IllegalArgumentException | NullPointerException failure) { throw new BusinessException(400, "operationId 必须是规范 UUID", failure); }
  }
  private static void requireName(String name) {
    if (name == null || name.isBlank() || name.length() > 200) { throw new BusinessException(400, "逻辑流名称不合法"); }
  }
  private static boolean contains(String text, String keyword) {
    return keyword == null || keyword.isBlank() || text != null && org.apache.commons.lang3.StringUtils.containsIgnoreCase(text, keyword);
  }
}
