package com.ouroboros.organizationstructure.directory.service;

import java.util.List;
import com.ouroboros.organizationstructure.directory.DirectoryBinding;
import com.ouroboros.organizationstructure.directory.DirectoryBindingCommands;
import com.ouroboros.organizationstructure.directory.DirectorySources;
import com.ouroboros.organizationstructure.directory.DirectoryStore;
import com.ouroboros.organizationstructure.directory.DirectorySyncCommands;
import com.ouroboros.enterprise.EnterpriseDeadline;
import com.ouroboros.enterprise.directory.EnterpriseDirectorySnapshot;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.exception.ValidationException;

/** Remote reads finish before any local write transaction begins. */
public class EnterpriseDirectoryService {
  private final EnterpriseConnectionCatalog connections;
  private final DirectorySources sources;
  private final DirectoryStore store;
  private final DirectoryBindingCommands bindings;
  private final DirectorySyncCommands synchronization;

  public EnterpriseDirectoryService(EnterpriseConnectionCatalog connections, DirectorySources sources,
      DirectoryStore store, DirectoryBindingCommands bindings, DirectorySyncCommands synchronization) {
    this.connections = connections; this.sources = sources; this.store = store;
    this.bindings = bindings; this.synchronization = synchronization;
  }

  public record Read(String connectionId, String revision, EnterpriseDirectorySnapshot snapshot) { }

  public Read read(String id) {
    var connection = connections.resolve(id).orElseThrow(() -> new ValidationException("企业连接不存在"));
    try {
      var snapshot = sources.require(connection.provider()).load(connection, EnterpriseDeadline.afterMillis(60000));
      if (snapshot == null || snapshot.departments().size() + snapshot.members().size() > 20000) {
        throw new ValidationException("目录快照为空或超过本次处理上限");
      }
      if (!connections.requireActive(id).revision().equals(connection.revision())) {
        throw new ValidationException("读取期间连接发生变更，请重新读取");
      }
      return new Read(id, connection.revision(), snapshot);
    } catch (ValidationException error) { throw error; }
    catch (Exception error) { throw new BusinessException("读取企业目录失败，请检查连接和通讯录权限", error); }
  }

  public DirectoryBinding bind(String connectionId, String type, String externalId, String localId, String scopeId) {
    var read = read(connectionId);
    return bindings.bind(connectionId, read.revision(), read.snapshot(), type, externalId, localId, scopeId);
  }

  public DirectorySyncCommands.Preview preview(String scopeId) {
    var scope = store.scope(scopeId).filter(item -> Boolean.TRUE.equals(item.getEnabled()))
        .orElseThrow(() -> new ValidationException("同步范围未启用"));
    var read = read(scope.getConnectionId());
    return synchronization.preview(scope, read.revision(), read.snapshot());
  }

  public List<EnterpriseConnectionCatalog.ConnectionSummary> connections() {
    return connections.listSummaries().stream().filter(item -> sources.providers().contains(item.provider())).toList();
  }
}
