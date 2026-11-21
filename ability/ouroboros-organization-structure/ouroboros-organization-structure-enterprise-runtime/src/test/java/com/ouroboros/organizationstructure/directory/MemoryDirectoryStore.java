package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.BeanWrapperImpl;
import com.ouroboros.exception.ValidationException;

/** Behavior fixture for command tests; database constraints are exercised separately. */
final class MemoryDirectoryStore extends DirectoryStore {
  final Map<String, DirectoryBinding> associations = new LinkedHashMap<>();
  final Map<String, DirectorySyncScope> sources = new LinkedHashMap<>();
  final Map<String, DirectorySyncRun> previews = new LinkedHashMap<>();
  MemoryDirectoryStore() { super(null, null, null); }
  @Override public List<DirectoryBinding> bindings(String id) { return associations.values().stream().filter(b -> id.equals(b.getConnectionId())).toList(); }
  @Override public List<DirectoryBinding> bindingsForLocal(String type, String localId) {
    return associations.values().stream().filter(b -> type.equals(b.getObjectType()) && localId.equals(b.getLocalId())).toList();
  }
  @Override public Optional<DirectoryBinding> binding(String id) { return Optional.ofNullable(associations.get(id)); }
  @Override public DirectoryBinding insert(DirectoryBinding b) {
    for (var other : associations.values()) {
      if (b.getExternalRouteKey() != null && b.getExternalRouteKey().equals(other.getExternalRouteKey())
          || b.getLocalRouteKey() != null && b.getLocalRouteKey().equals(other.getLocalRouteKey())
          || b.getSyncOwnerKey() != null && b.getSyncOwnerKey().equals(other.getSyncOwnerKey())) {
        throw new ValidationException("unique conflict");
      }
    }
    b.setId(UUID.randomUUID().toString()); b.setVersion(1L); b.setUpdatedAt(LocalDateTime.now()); associations.put(b.getId(), b); return b;
  }
  @Override public void update(DirectoryBinding b, Map<String,Object> changes) {
    var row = associations.get(b.getId()); var wrapper = new BeanWrapperImpl(row);
    changes.forEach(wrapper::setPropertyValue); row.setVersion(row.getVersion()+1); row.setUpdatedAt(LocalDateTime.now());
  }
  @Override public List<DirectorySyncScope> scopes() { return List.copyOf(sources.values()); }
  @Override public Optional<DirectorySyncScope> scope(String id) { return Optional.ofNullable(sources.get(id)); }
  @Override public void saveScope(DirectorySyncScope s, Long version) { s.setVersion(version == null ? 1L : version+1); sources.put(s.getId(), s); }
  @Override public void claimScope(DirectorySyncScope s) { s.setVersion(s.getVersion()+1); }
  @Override public DirectorySyncRun insertRun(DirectorySyncRun r) { previews.put(r.getId(), r); return r; }
  @Override public Optional<DirectorySyncRun> run(String id) { return Optional.ofNullable(previews.get(id)); }
  @Override public void completeRun(String id) { previews.get(id).setStatus("APPLIED"); previews.get(id).setAppliedAt(LocalDateTime.now()); }
  @Override public void completeRun(String id, String decisions) { completeRun(id); previews.get(id).setDecisionsJson(decisions); }
}
