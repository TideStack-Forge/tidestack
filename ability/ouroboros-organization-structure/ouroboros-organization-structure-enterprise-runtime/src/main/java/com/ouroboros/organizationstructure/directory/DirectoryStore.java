package com.ouroboros.organizationstructure.directory;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.time.LocalDateTime;

import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.exception.BusinessException;

/** Business records use the platform typed QueryDSL, including compare-and-set updates. */
public class DirectoryStore {
  private final TypedDataModel<String, DirectoryBinding> bindings;
  private final TypedDataModel<String, DirectorySyncScope> scopes;
  private final TypedDataModel<String, DirectorySyncRun> runs;

  public DirectoryStore(TypedDataModel<String, DirectoryBinding> bindings,
      TypedDataModel<String, DirectorySyncScope> scopes, TypedDataModel<String, DirectorySyncRun> runs) {
    this.bindings = bindings; this.scopes = scopes; this.runs = runs;
  }

  public List<DirectoryBinding> bindings(String connectionId) {
    return bindings.query().where(Map.of("connectionId", connectionId)).execute()
        .getOrElseThrow(error -> failed(error));
  }

  public List<DirectoryBinding> bindingsForLocal(String type, String localId) {
    return bindings.query().where(Map.of("objectType", type, "localId", localId)).execute()
        .getOrElseThrow(error -> failed(error));
  }

  public Optional<DirectoryBinding> binding(String id) {
    return Optional.ofNullable(bindings.get(id).getOrElseThrow(error -> failed(error)));
  }

  public DirectoryBinding insert(DirectoryBinding binding) {
    binding.setId(UUID.randomUUID().toString()); binding.setVersion(1L); binding.setUpdatedAt(LocalDateTime.now());
    return bindings.insert(binding).getOrElseThrow(error -> failed(error));
  }

  public void update(DirectoryBinding binding, Map<String, Object> changes) {
    var data = new java.util.LinkedHashMap<>(changes);
    data.put("version", binding.getVersion() + 1); data.put("updatedAt", LocalDateTime.now());
    changed(bindings.update(Map.of("id", binding.getId(), "version", binding.getVersion()), data)
        .getOrElseThrow(error -> failed(error)));
  }

  public List<DirectorySyncScope> scopes() {
    return scopes.query().execute().getOrElseThrow(error -> failed(error));
  }

  public Optional<DirectorySyncScope> scope(String id) {
    return Optional.ofNullable(scopes.get(id).getOrElseThrow(error -> failed(error)));
  }

  public void saveScope(DirectorySyncScope scope, Long expectedVersion) {
    scope.setUpdatedAt(LocalDateTime.now());
    scope.setSourceRouteKey(Boolean.TRUE.equals(scope.getEnabled())
        ? DirectoryJson.digest(List.of(scope.getConnectionId(), scope.getExternalRootId())) : null);
    if (expectedVersion == null) {
      scope.setVersion(1L); scopes.insert(scope).getOrElseThrow(error -> failed(error));
    } else {
      var values = new java.util.LinkedHashMap<String, Object>(Map.of(
          "connectionId", scope.getConnectionId(), "externalRootId", scope.getExternalRootId(),
          "enabled", scope.getEnabled(), "syncMobile", scope.getSyncMobile(), "syncEmail", scope.getSyncEmail(),
          "version", expectedVersion + 1, "updatedAt", scope.getUpdatedAt()));
      values.put("sourceRouteKey", scope.getSourceRouteKey());
      changed(scopes.update(Map.of("id", scope.getId(), "version", expectedVersion), values).getOrElseThrow(error -> failed(error)));
      scope.setVersion(expectedVersion + 1);
    }
  }

  /** Acquires this scope's write lock until the caller's transaction ends. */
  public void claimScope(DirectorySyncScope scope) {
    changed(scopes.update(Map.of("id", scope.getId(), "version", scope.getVersion(), "enabled", true),
        Map.of("version", scope.getVersion() + 1, "updatedAt", LocalDateTime.now()))
        .getOrElseThrow(error -> failed(error)));
  }

  public DirectorySyncRun insertRun(DirectorySyncRun run) {
    return runs.insert(run).getOrElseThrow(error -> failed(error));
  }

  public Optional<DirectorySyncRun> run(String id) {
    return Optional.ofNullable(runs.get(id).getOrElseThrow(error -> failed(error)));
  }

  public void completeRun(String id) {
    completeRun(id, "{}");
  }

  public void completeRun(String id, String decisionsJson) {
    changed(runs.update(Map.of("id", id, "status", "PREVIEW"),
        Map.of("status", "APPLIED", "appliedAt", LocalDateTime.now(), "decisionsJson", decisionsJson))
        .getOrElseThrow(error -> failed(error)));
  }

  /** Keep run identities while discarding personal data only after the preview is no longer usable. */
  @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
  public long clearExpiredRunContents(LocalDateTime now, int snapshotDays, int detailDays) {
    if (snapshotDays < 1 || detailDays < snapshotDays) {
      throw new com.ouroboros.exception.ValidationException("目录摘要保留期不得短于快照保留期，且必须为正数");
    }
    long count = 0;
    for (String status : List.of("PREVIEW", "APPLIED", "EXPIRED")) {
      String endField = "APPLIED".equals(status) ? "appliedAt" : "createdAt";
      var where = new LinkedHashMap<String, Object>();
      where.put("status", status); where.put(endField, Map.of("<", now.minusDays(snapshotDays)));
      where.put("snapshotClearedAt", null);
      var candidates = runs.query().where(where).clause("LIMIT", 100).execute().getOrElseThrow(DirectoryStore::failed);
      for (var run : candidates) {
        var condition = new LinkedHashMap<>(where); condition.put("id", run.getId());
        var changes = new LinkedHashMap<String, Object>();
        changes.put("snapshotJson", "{}"); changes.put("snapshotClearedAt", now);
        if ("PREVIEW".equals(status)) { changes.put("status", "EXPIRED"); }
        count += runs.update(condition, changes).getOrElseThrow(DirectoryStore::failed);
      }
    }
    for (String status : List.of("APPLIED", "EXPIRED")) {
      String endField = "APPLIED".equals(status) ? "appliedAt" : "createdAt";
      var where = new LinkedHashMap<String, Object>();
      where.put("status", status); where.put(endField, Map.of("<", now.minusDays(detailDays)));
      where.put("detailsClearedAt", null);
      for (var run : runs.query().where(where).clause("LIMIT", 100).execute().getOrElseThrow(DirectoryStore::failed)) {
        var condition = new LinkedHashMap<>(where); condition.put("id", run.getId());
        count += runs.update(condition, Map.of("decisionsJson", "{}", "detailsClearedAt", now))
            .getOrElseThrow(DirectoryStore::failed);
      }
    }
    return count;
  }

  private static void changed(Long rows) {
    if (rows == null || rows != 1) { throw new BusinessException(409, "目录关联或同步范围已改变，请重新预览"); }
  }

  private static BusinessException failed(Throwable error) { return new BusinessException("组织目录记录读写失败", error); }
}
