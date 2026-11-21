package com.ouroboros.organizationstructure.directory;

import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.exception.ValidationException;

/** Explicit administrator commands; no phone/name matching and no SSO writes. */
public class DirectoryBindingCommands {
  private final DirectoryStore store;
  private final DirectoryOrganization organization;
  private final EnterpriseConnectionCatalog connections;

  public DirectoryBindingCommands(DirectoryStore store, DirectoryOrganization organization,
      EnterpriseConnectionCatalog connections) {
    this.store = store; this.organization = organization; this.connections = connections;
  }

  @Transactional(rollbackFor = Exception.class)
  public DirectorySyncScope configure(DirectorySyncScope requested, Long expectedVersion) {
    organization.company(requested.getId()); connections.requireActive(requested.getConnectionId());
    var current = store.scope(requested.getId());
    if (current.isPresent()) {
      if (!Objects.equals(expectedVersion, current.get().getVersion())) { throw new ValidationException("同步范围已修改，请刷新"); }
      if (Boolean.TRUE.equals(current.get().getEnabled()) &&
          (!current.get().getConnectionId().equals(requested.getConnectionId())
          || !current.get().getExternalRootId().equals(requested.getExternalRootId()))) {
        throw new ValidationException("更换主来源前必须先停用原同步范围");
      }
    } else if (expectedVersion != null) { throw new ValidationException("同步范围不存在"); }
    if (requested.getExternalRootId() == null || requested.getExternalRootId().isBlank()) {
      throw new ValidationException("请选择外部根部门");
    }
    if (Boolean.TRUE.equals(requested.getEnabled())) {
      for (var other : store.scopes()) {
        if (!Boolean.TRUE.equals(other.getEnabled()) || other.getId().equals(requested.getId())) { continue; }
        if (organization.contains(other.getId(), requested.getId()) || organization.contains(requested.getId(), other.getId())) {
          throw new ValidationException("此组织范围已存在主通讯录来源");
        }
        if (other.getConnectionId().equals(requested.getConnectionId())
            && other.getExternalRootId().equals(requested.getExternalRootId())) {
          throw new ValidationException("同一外部组织范围不能同步到多个本地公司");
        }
      }
    }
    store.saveScope(requested, expectedVersion);
    if (Boolean.TRUE.equals(requested.getEnabled())) {
      for (var binding : store.bindings(requested.getConnectionId())) {
        if (requested.getId().equals(binding.getScopeId())) {
          String fields = "MEMBER".equals(binding.getObjectType()) ? "name,appointments"
              + (Boolean.TRUE.equals(requested.getSyncMobile()) ? ",mobile" : "")
              + (Boolean.TRUE.equals(requested.getSyncEmail()) ? ",email" : "") : "name,parent";
          store.update(binding, Map.of("managedFields", fields));
        }
      }
    }
    if (!Boolean.TRUE.equals(requested.getEnabled())) {
      for (var binding : store.bindings(requested.getConnectionId())) {
        if (requested.getId().equals(binding.getScopeId())) {
          var changes = new LinkedHashMap<String, Object>();
          changes.put("syncOwnerKey", null); changes.put("managedFields", null); changes.put("scopeId", null);
          store.update(binding, changes);
        }
      }
    }
    return requested;
  }

  @Transactional(rollbackFor = Exception.class)
  public DirectoryBinding bind(String connectionId, String connectionRevision, EnterpriseDirectorySnapshot snapshot,
      String type, String externalId, String localId, String scopeId) {
    requireRevision(connectionId, connectionRevision);
    if (!"MEMBER".equals(type) && !"DEPARTMENT".equals(type)) { throw new ValidationException("目录对象类型无效"); }
    boolean active;
    if ("MEMBER".equals(type)) {
      active = snapshot.members().stream().filter(member -> member.externalId().equals(externalId)).findFirst()
          .orElseThrow(() -> new ValidationException("外部成员不在已读取的范围内")).active();
      organization.employee(localId);
    } else {
      if (snapshot.departments().stream().noneMatch(department -> department.externalId().equals(externalId))) {
        throw new ValidationException("外部部门不在已读取的范围内");
      }
      if (!"department".equals(organization.node(localId).getType())) { throw new ValidationException("只能绑定本地部门"); }
      active = true;
    }
    if (store.bindings(connectionId).stream().anyMatch(binding ->
        binding.getExternalRouteKey() != null && type.equals(binding.getObjectType())
            && (externalId.equals(binding.getExternalId()) || localId.equals(binding.getLocalId())))) {
      throw new ValidationException("外部对象或本地对象已有关联，请先检查并解绑");
    }
    var binding = new DirectoryBinding(); binding.setConnectionId(connectionId); binding.setObjectType(type);
    binding.setExternalId(externalId); binding.setLocalId(localId); binding.setActive(active);
    route(binding);
    if (scopeId != null && !scopeId.isBlank()) {
      var scope = store.scope(scopeId).filter(item -> Boolean.TRUE.equals(item.getEnabled())
          && connectionId.equals(item.getConnectionId())).orElseThrow(() -> new ValidationException("主同步范围未启用"));
      store.claimScope(scope);
      if ("DEPARTMENT".equals(type) && !organization.contains(scopeId, localId)) {
        throw new ValidationException("绑定部门不在此同步范围内");
      }
      own(binding, scope);
    }
    return store.insert(binding);
  }

  @Transactional(rollbackFor = Exception.class)
  public void unbind(String id, long expectedVersion) {
    var binding = store.binding(id).orElseThrow(() -> new ValidationException("目录关联不存在"));
    if (binding.getVersion() != expectedVersion) { throw new ValidationException("目录关联已修改，请刷新"); }
    if (binding.getScopeId() != null) {
      store.scope(binding.getScopeId()).filter(scope -> Boolean.TRUE.equals(scope.getEnabled())).ifPresent(store::claimScope);
    }
    DirectoryWriteContext.run(binding.getScopeId(), () -> organization.endAppointments(DirectoryOrganization.ownedAppointments(binding)));
    var changes = new LinkedHashMap<String, Object>(); changes.put("active", false);
    changes.put("syncOwnerKey", null); changes.put("managedFields", null); changes.put("scopeId", null);
    changes.put("externalRouteKey", null); changes.put("localRouteKey", null);
    store.update(binding, changes);
  }

  static void own(DirectoryBinding binding, DirectorySyncScope scope) {
    binding.setSyncOwnerKey(binding.getObjectType() + ":" + binding.getLocalId()); binding.setScopeId(scope.getId());
    binding.setManagedFields("MEMBER".equals(binding.getObjectType())
        ? "name,appointments" + (Boolean.TRUE.equals(scope.getSyncMobile()) ? ",mobile" : "")
            + (Boolean.TRUE.equals(scope.getSyncEmail()) ? ",email" : "") : "name,parent");
  }

  static void route(DirectoryBinding binding) {
    binding.setExternalRouteKey(DirectoryJson.digest(List.of(binding.getConnectionId(), binding.getObjectType(), binding.getExternalId())));
    binding.setLocalRouteKey(DirectoryJson.digest(List.of(binding.getConnectionId(), binding.getObjectType(), binding.getLocalId())));
  }

  private void requireRevision(String id, String expected) {
    if (!connections.requireActive(id).revision().equals(expected)) {
      throw new ValidationException("连接已改变，请重新读取目录");
    }
  }
}
