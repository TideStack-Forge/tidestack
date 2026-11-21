package com.ouroboros.organizationstructure.directory;

import java.time.LocalDateTime;
import java.util.*;
import org.springframework.transaction.annotation.Transactional;
import com.ouroboros.enterprise.directory.*;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.exception.ValidationException;

/** Applies a confirmed immutable preview in one local transaction, without remote calls. */
public class DirectorySyncCommands {
  private final DirectoryStore store;
  private final DirectoryOrganization organization;
  private final EnterpriseConnectionCatalog connections;

  public DirectorySyncCommands(DirectoryStore store, DirectoryOrganization organization, EnterpriseConnectionCatalog connections) {
    this.store = store; this.organization = organization; this.connections = connections;
  }

  public record Change(String objectType, String externalId, String name, String action,
                       List<Map<String, String>> candidates) { }
  public record Preview(String runId, boolean complete, List<Change> changes, List<String> missingPendingReview,
                        List<Map<String, Object>> primaryDepartmentChoices) { }

  public Preview preview(DirectorySyncScope scope, String revision, EnterpriseDirectorySnapshot raw) {
    for (var other : store.scopes()) {
      if (other.getId().equals(scope.getId()) || !Boolean.TRUE.equals(other.getEnabled())
          || !scope.getConnectionId().equals(other.getConnectionId())) { continue; }
      var currentIds = select(scope, raw).departments().stream().map(EnterpriseDirectoryDepartment::externalId).toList();
      if (currentIds.contains(other.getExternalRootId()) || raw.departments().stream()
          .anyMatch(item -> item.externalId().equals(other.getExternalRootId())) && select(other, raw).departments().stream()
          .anyMatch(item -> item.externalId().equals(scope.getExternalRootId()))) {
        throw new ValidationException("外部目录范围与另一主来源范围重叠，请先处理");
      }
    }
    var snapshot = select(scope, raw); var bindings = routed(scope.getConnectionId());
    var changes = new ArrayList<Change>();
    for (var department : snapshot.departments()) {
      if (department.externalId().equals(scope.getExternalRootId())) { continue; }
      changes.add(change(bindings, "DEPARTMENT", department.externalId(), department.name()));
    }
    for (var member : snapshot.members()) {
      changes.add(member.active() ? change(bindings, "MEMBER", member.externalId(), member.name())
          : new Change("MEMBER", member.externalId(), member.name(), "外部明确停用，仅结束本来源关系", List.of()));
    }
    var observed = new HashSet<String>();
    snapshot.departments().forEach(item -> observed.add("DEPARTMENT:" + item.externalId()));
    snapshot.members().forEach(item -> observed.add("MEMBER:" + item.externalId()));
    var missing = bindings.stream().filter(item -> scope.getId().equals(item.getScopeId()))
        .filter(item -> !observed.contains(item.getObjectType() + ":" + item.getExternalId()))
        .map(item -> item.getObjectType() + ":" + item.getExternalId()).toList();
    var run = new DirectorySyncRun(); run.setId(UUID.randomUUID().toString()); run.setConnectionId(scope.getConnectionId());
    run.setScopeId(scope.getId()); run.setScopeVersion(scope.getVersion()); run.setConnectionRevision(revision);
    run.setComplete(snapshot.complete()); run.setSnapshotJson(DirectoryJson.encode(snapshot)); run.setStatus("PREVIEW");
    run.setCreatedAt(LocalDateTime.now()); run.setBaseline(baseline(scope)); store.insertRun(run);
    var choices = snapshot.members().stream().filter(member -> member.active() && member.primaryDepartmentExternalId() == null)
        .filter(member -> { var binding = find(bindings, "MEMBER", member.externalId());
          return binding == null || scope.getId().equals(binding.getScopeId()); })
        .map(member -> Map.<String, Object>of("externalId", member.externalId(), "name", member.name(),
            "departmentOptions", member.departmentExternalIds().stream().map(id -> Map.of("value", id, "label", id)).toList())).toList();
    return new Preview(run.getId(), snapshot.complete(), List.copyOf(changes), missing, choices);
  }

  @Transactional(rollbackFor = Exception.class)
  public void apply(String runId, boolean confirmCreation) {
    apply(runId, confirmCreation, Map.of());
  }

  @Transactional(rollbackFor = Exception.class)
  public void apply(String runId, boolean confirmCreation, Map<String, String> primaryDepartments) {
    var run = store.run(runId).orElseThrow(() -> new ValidationException("同步预览不存在"));
    if ("APPLIED".equals(run.getStatus())) { return; }
    if (!"PREVIEW".equals(run.getStatus()) || run.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
      throw new ValidationException("同步预览已失效，请重新读取");
    }
    if (!Boolean.TRUE.equals(run.getComplete())) { throw new ValidationException("目录读取不完整，不能应用同步"); }
    if (!confirmCreation) { throw new ValidationException("请确认新增对象、候选绑定及任职变化后再应用"); }
    if (!connections.requireActive(run.getConnectionId()).revision().equals(run.getConnectionRevision())) {
      throw new ValidationException("企业连接已改变，请重新预览");
    }
    var scope = store.scope(run.getScopeId()).filter(item -> Boolean.TRUE.equals(item.getEnabled()))
        .orElseThrow(() -> new ValidationException("同步范围已停用"));
    if (!Objects.equals(scope.getVersion(), run.getScopeVersion()) || !scope.getConnectionId().equals(run.getConnectionId())
        || !run.getBaseline().equals(baseline(scope))) {
      throw new ValidationException("同步范围、关联或本地资料已改变，请重新预览");
    }
    store.claimScope(scope);
    var snapshot = withPrimaryDepartments(DirectoryJson.decode(run.getSnapshotJson(), EnterpriseDirectorySnapshot.class), primaryDepartments);
    DirectoryWriteContext.run(scope.getId(), () -> applySnapshot(scope, snapshot));
    store.completeRun(runId, DirectoryJson.encode(primaryDepartments));
  }

  private static EnterpriseDirectorySnapshot withPrimaryDepartments(EnterpriseDirectorySnapshot snapshot, Map<String, String> choices) {
    var known = snapshot.members().stream().map(EnterpriseDirectoryMember::externalId).toList();
    if (!known.containsAll(choices.keySet())) { throw new ValidationException("主部门选择包含不属于此预览的成员"); }
    var members = snapshot.members().stream().map(member -> {
      String choice = choices.get(member.externalId());
      if (choice == null) { return member; }
      if (member.primaryDepartmentExternalId() != null || !member.departmentExternalIds().contains(choice)) {
        throw new ValidationException("只能为未明确主部门的成员选择其已归属的部门");
      }
      return new EnterpriseDirectoryMember(member.externalId(), member.name(), member.mobile(), member.email(),
          member.departmentExternalIds(), choice, member.active());
    }).toList();
    return new EnterpriseDirectorySnapshot(snapshot.departments(), members, snapshot.complete());
  }

  private void applySnapshot(DirectorySyncScope scope, EnterpriseDirectorySnapshot snapshot) {
    var bindings = routed(scope.getConnectionId());
    var departments = new HashMap<String, String>(); departments.put(scope.getExternalRootId(), scope.getId());
    for (var external : snapshot.departments()) {
      if (external.externalId().equals(scope.getExternalRootId())) { continue; }
      var binding = find(bindings, "DEPARTMENT", external.externalId());
      String parent = departments.get(external.parentExternalId());
      if (parent == null) { throw new ValidationException("外部父部门不可见，请核对目录范围"); }
      if (binding == null) {
        String id = organization.createDepartment(external, parent);
        binding = create(scope, "DEPARTMENT", external.externalId(), id, true); bindings.add(binding);
      } else if (scope.getId().equals(binding.getScopeId())) {
        organization.updateDepartment(binding.getLocalId(), external, parent);
      }
      if (!organization.contains(scope.getId(), binding.getLocalId())) {
        throw new ValidationException("已关联部门不在此本地同步范围内");
      }
      departments.put(external.externalId(), binding.getLocalId());
    }
    for (var external : snapshot.members()) {
      var binding = find(bindings, "MEMBER", external.externalId());
      if (!external.active()) {
        if (binding != null) {
          organization.endAppointments(DirectoryOrganization.ownedAppointments(binding));
          store.update(binding, Map.of("active", false));
        }
        continue;
      }
      if (binding == null) {
        String id = organization.createMember(external, scope);
        binding = create(scope, "MEMBER", external.externalId(), id, true); bindings.add(binding);
      } else if (!Boolean.TRUE.equals(binding.getActive())) {
        throw new ValidationException("外部成员已停用或解绑，需管理员明确重新关联");
      }
      if (binding.getSyncOwnerKey() != null && !scope.getId().equals(binding.getScopeId())) {
        throw new ValidationException("员工已由另一主通讯录来源维护");
      }
      if (scope.getId().equals(binding.getScopeId())) {
        organization.updateMember(binding.getLocalId(), external, scope);
        var appointments = organization.appointments(binding.getLocalId(), external, departments,
            DirectoryOrganization.ownedAppointments(binding));
        store.update(binding, Map.of("appointmentIds", DirectoryJson.encode(appointments)));
      }
    }
  }

  private DirectoryBinding create(DirectorySyncScope scope, String type, String externalId, String localId, boolean active) {
    var binding = new DirectoryBinding(); binding.setConnectionId(scope.getConnectionId()); binding.setObjectType(type);
    binding.setExternalId(externalId); binding.setLocalId(localId); binding.setActive(active);
    DirectoryBindingCommands.route(binding); DirectoryBindingCommands.own(binding, scope); return store.insert(binding);
  }

  private Change change(List<DirectoryBinding> bindings, String type, String externalId, String name) {
    var current = find(bindings, type, externalId);
    return new Change(type, externalId, name, current == null ? "新增（不自动匹配）"
        : current.getSyncOwnerKey() == null ? "保留本地维护，仅使用关联" : "按主来源更新",
        current == null ? organization.candidates(type, name) : List.of());
  }

  private List<DirectoryBinding> routed(String connectionId) {
    return new ArrayList<>(store.bindings(connectionId).stream().filter(item -> item.getExternalRouteKey() != null).toList());
  }

  private static DirectoryBinding find(List<DirectoryBinding> bindings, String type, String externalId) {
    return bindings.stream().filter(item -> type.equals(item.getObjectType()) && externalId.equals(item.getExternalId()))
        .findFirst().orElse(null);
  }

  private String baseline(DirectorySyncScope scope) {
    var values = new ArrayList<Object>(); values.add(organization.company(scope.getId()));
    routed(scope.getConnectionId()).stream().sorted(Comparator.comparing(DirectoryBinding::getId)).forEach(binding -> {
      values.add(binding); values.add(organization.baseline(binding));
    });
    return DirectoryJson.digest(values);
  }

  static EnterpriseDirectorySnapshot select(DirectorySyncScope scope, EnterpriseDirectorySnapshot raw) {
    var byId = new LinkedHashMap<String, EnterpriseDirectoryDepartment>();
    for (var item : raw.departments()) {
      if (byId.put(item.externalId(), item) != null) { throw new ValidationException("外部部门 ID 重复"); }
    }
    var root = byId.get(scope.getExternalRootId());
    if (root == null) { throw new ValidationException("外部根部门不可见，不能同步"); }
    var selected = new ArrayList<EnterpriseDirectoryDepartment>(); selected.add(root);
    var ids = new LinkedHashSet<String>(); ids.add(root.externalId());
    for (int offset = 0; offset < selected.size(); offset++) {
      String parent = selected.get(offset).externalId();
      byId.values().stream().filter(item -> parent.equals(item.parentExternalId())).forEach(item -> {
        if (!ids.add(item.externalId())) { throw new ValidationException("外部部门层级存在环"); } selected.add(item);
      });
    }
    var memberIds = new HashSet<String>(); var members = new ArrayList<EnterpriseDirectoryMember>();
    for (var item : raw.members()) {
      if (!memberIds.add(item.externalId())) { throw new ValidationException("外部成员 ID 重复"); }
      var departments = item.departmentExternalIds().stream().filter(ids::contains).toList();
      if (!departments.isEmpty()) {
        members.add(new EnterpriseDirectoryMember(item.externalId(), item.name(),
            Boolean.TRUE.equals(scope.getSyncMobile()) ? item.mobile() : null,
            Boolean.TRUE.equals(scope.getSyncEmail()) ? item.email() : null,
            departments, ids.contains(item.primaryDepartmentExternalId()) ? item.primaryDepartmentExternalId() : null, item.active()));
      }
    }
    return new EnterpriseDirectorySnapshot(selected, members, raw.complete());
  }
}
