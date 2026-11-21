package com.ouroboros.logicflow.manage.repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.ouroboros.data.exception.UniqueConstraintViolationException;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.mapper.OuroborosExceptionMapper;
import com.ouroboros.logicflow.manage.model.RuntimeFlowCatalog;
import com.ouroboros.logicflow.manage.model.RuntimeFlowChange;
import com.ouroboros.logicflow.manage.model.RuntimeFlowInstanceStatus;
import com.ouroboros.logicflow.manage.model.RuntimeFlowOverride;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.util.Json;

/** Uses only the public typed data/QueryDSL boundary. No SQL or unconditional write API is exposed. */
@Repository
public class DataRuntimeFlowStore implements RuntimeFlowStore {
  private static final String CATALOG = "runtime-logicflows";
  private final TransactionTemplate transaction;

  public DataRuntimeFlowStore(PlatformTransactionManager transactions) {
    transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public Snapshot initialize() {
    if (catalog().query(Map.of("where", Map.of("id", CATALOG)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).isEmpty()) {
      try {
        transaction.executeWithoutResult(status -> catalog().insert(new RuntimeFlowCatalog()
            .setId(CATALOG).setEpoch(UUID.randomUUID().toString()).setRevision(0L))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException));
      } catch (RuntimeException failure) {
        if (!duplicate(failure)) { throw failure; }
        // The unique key winner is read after this failed transaction has rolled back.
      }
    }
    return read();
  }

  @Override
  public Snapshot read() {
    for (int attempt = 0; attempt < 5; attempt++) {
      var before = version();
      var rows = overrides().query(Map.of()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
      var after = version();
      if (!before.equals(after)) { continue; }
      var values = new HashMap<String, StoredOverride>();
      for (var row : rows) {
        var value = decode(StoredOverride.class, row.getPayload());
        String key = FlowModelDefinitions.nameKey(value.name());
        if (!key.equals(row.getNameKey()) || !FlowModelDefinitions.hash(key).equals(row.getId())
            || value.version() < 1 || value.version() != row.getVersion()
            || value.active() != (value.definition() != null) || values.putIfAbsent(key, value) != null) {
          throw new BusinessException(503, "逻辑流覆盖存储身份或版本损坏");
        }
        if (value.active() && !FlowModelDefinitions.read(value.definition()).getName().equals(value.name())) {
          throw new BusinessException(503, "逻辑流覆盖正文身份损坏");
        }
      }
      return new Snapshot(before, values);
    }
    throw new BusinessException(409, "逻辑流覆盖集合正在变化，请重试读取");
  }

  @Override
  public Optional<Change> operation(String operationId) {
    return changes().query(Map.of("where", Map.of("id", operationId)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException).stream().findFirst()
        .map(row -> decode(Change.class, row.getPayload()));
  }

  @Override
  public List<Change> history(String name) {
    String key = FlowModelDefinitions.nameKey(name);
    var indexed = changes().query(Map.of("where", Map.of("nameKey", key), "order", "committedAt desc", "limit", 100))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    // Earlier rows have no searchable columns. Read their immutable payloads without losing history.
    var legacyWhere = new java.util.HashMap<String, Object>();
    legacyWhere.put("nameKey", null);
    var legacy = changes().query(Map.of("where", legacyWhere)).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    return java.util.stream.Stream.concat(indexed.stream(), legacy.stream()).map(row -> decode(Change.class, row.getPayload()))
        .filter(change -> change.name().equalsIgnoreCase(name))
        .sorted(java.util.Comparator.comparing(Change::committedAt).reversed()).limit(100).toList();
  }

  @Override
  public Change commit(Version expected, Change change) {
    try {
      return Objects.requireNonNull(transaction.execute(status -> {
        var replay = operation(change.operationId());
        if (replay.isPresent()) { return replay(replay.get(), change); }
        if (!expected.next().equals(change.committedVersion())) {
          throw new BusinessException(400, "逻辑流提交代次必须递增一次");
        }
        long changed = catalog().update(Map.of("id", CATALOG, "epoch", expected.epoch(), "revision", expected.revision()),
            Map.of("revision", change.committedVersion().revision()))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
        if (changed != 1) { throw new BusinessException(409, "逻辑流覆盖集合已变化"); }
        if (change.changed()) {
          var after = Objects.requireNonNull(change.after());
          var key = FlowModelDefinitions.nameKey(after.name());
          var id = FlowModelDefinitions.hash(key);
          if (change.before() == null) {
            if (after.version() != 1) { throw new BusinessException(400, "首个覆盖版本必须为 1"); }
            overrides().insert(new RuntimeFlowOverride().setId(id).setNameKey(key)
                .setVersion(after.version()).setPayload(encode(after)))
                .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
          } else {
            if (after.version() != Math.addExact(change.before().version(), 1)) {
              throw new BusinessException(400, "逻辑流覆盖版本必须递增一次");
            }
            long updated = overrides().update(Map.of("id", id, "version", change.before().version()),
                Map.of("version", after.version(), "payload", encode(after)))
                .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
            if (updated != 1) { throw new BusinessException(409, "逻辑流覆盖已变化"); }
          }
        }
        changes().insert(new RuntimeFlowChange().setId(change.operationId()).setNameKey(FlowModelDefinitions.nameKey(change.name()))
            .setCommittedAt(change.committedAt()).setPayload(encode(change)))
            .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
        return change;
      }));
    } catch (RuntimeException failure) {
      var committed = operation(change.operationId());
      if (committed.isPresent()) { return replay(committed.get(), change); }
      throw failure;
    }
  }

  private Change replay(Change stored, Change request) {
    if (!stored.actor().equals(request.actor()) || !stored.requestHash().equals(request.requestHash())) {
      throw new BusinessException(409, "operationId 已用于其他请求");
    }
    return stored;
  }

  @Override
  public void report(Instance instance) {
    var rows = instancesModel().query(Map.of("where", Map.of("id", instance.bootId())))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    if (rows.isEmpty()) {
      instancesModel().insert(new RuntimeFlowInstanceStatus().setId(instance.bootId()).setPayload(encode(instance)))
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    } else {
      instancesModel().update(instance.bootId(), Map.of("payload", encode(instance)))
          .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    }
  }

  @Override
  public List<Instance> instances() {
    return instancesModel().query(Map.of()).getOrElseThrow(OuroborosExceptionMapper::toOuroborosException)
        .stream().map(row -> decode(Instance.class, row.getPayload())).toList();
  }

  private Version version() {
    var rows = catalog().query(Map.of("where", Map.of("id", CATALOG)))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
    if (rows.size() != 1 || rows.getFirst().getEpoch() == null || rows.getFirst().getRevision() == null
        || rows.getFirst().getRevision() < 0) {
      throw new BusinessException(503, "逻辑流覆盖集合尚未初始化或已损坏");
    }
    return new Version(rows.getFirst().getEpoch(), rows.getFirst().getRevision());
  }

  private static Map<String, Object> encode(Object value) {
    return Json.tryToJsonString(value).flatMap(Json::tryToMap)
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private static <T> T decode(Class<T> type, Map<String, Object> value) {
    return Json.tryToJsonString(value).flatMap(text -> Json.tryToBean(type, text))
        .getOrElseThrow(OuroborosExceptionMapper::toOuroborosException);
  }

  private static boolean duplicate(Throwable failure) {
    for (var cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof UniqueConstraintViolationException) { return true; }
    }
    return false;
  }

  private static <T> TypedDataModel<String, T> storage(Class<T> type) {
    return TypedDataModelCenter.getDataModel(String.class, type)
        .orElseThrow(() -> new BusinessException(503, "逻辑流管理存储尚未可用: " + type.getSimpleName()));
  }
  private static TypedDataModel<String, RuntimeFlowCatalog> catalog() { return storage(RuntimeFlowCatalog.class); }
  private static TypedDataModel<String, RuntimeFlowOverride> overrides() { return storage(RuntimeFlowOverride.class); }
  private static TypedDataModel<String, RuntimeFlowChange> changes() { return storage(RuntimeFlowChange.class); }
  private static TypedDataModel<String, RuntimeFlowInstanceStatus> instancesModel() { return storage(RuntimeFlowInstanceStatus.class); }
}
