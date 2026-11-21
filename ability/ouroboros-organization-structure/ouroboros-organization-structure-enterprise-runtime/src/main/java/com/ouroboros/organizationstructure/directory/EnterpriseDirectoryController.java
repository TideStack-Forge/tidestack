package com.ouroboros.organizationstructure.directory;

import com.ouroboros.organizationstructure.directory.service.EnterpriseDirectoryService;

import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping("/api/sys/enterprise-directory")
public class EnterpriseDirectoryController {
  static final String PREFIX = "systemSetting.integrationManagement.connections.directory.";
  public static final String VIEW = PREFIX + "view";
  public static final String BIND = PREFIX + "bind";
  public static final String SYNCHRONIZE = PREFIX + "synchronize";
  private final EnterpriseDirectoryService directory;
  private final DirectoryBindingCommands bindings;
  private final DirectorySyncCommands synchronization;
  private final DirectoryStore store;
  private final DirectoryOrganization organization;

  public EnterpriseDirectoryController(EnterpriseDirectoryService directory, DirectoryBindingCommands bindings,
      DirectorySyncCommands synchronization, DirectoryStore store, DirectoryOrganization organization) {
    this.directory = directory; this.bindings = bindings; this.synchronization = synchronization; this.store = store; this.organization = organization;
  }
  @GetMapping("/management-state") public WebApiResult<?> managementState() {
    EnterpriseConnectionAccess.require(VIEW);
    return WebApiResult.ok(Map.of("canBind", EnterpriseConnectionAccess.permits(BIND),
        "canSynchronize", EnterpriseConnectionAccess.permits(SYNCHRONIZE)));
  }
  @GetMapping("/local-options") public WebApiResult<?> options(@RequestParam String objectType) {
    EnterpriseConnectionAccess.require(VIEW);
    if (!List.of("MEMBER", "DEPARTMENT", "COMPANY").contains(objectType)) {
      throw new com.ouroboros.exception.ValidationException("组织对象类型无效");
    }
    return WebApiResult.ok(Map.of("options", organization.options(objectType)));
  }
  @GetMapping("/connections") public WebApiResult<?> connections() {
    EnterpriseConnectionAccess.require(VIEW); return WebApiResult.ok(Map.of("options", directory.connections().stream()
        .map(item -> Map.of("label", item.connectionId(), "value", item.connectionId())).toList()));
  }
  @GetMapping("/snapshot") public WebApiResult<?> snapshot(@RequestParam String connectionId) {
    EnterpriseConnectionAccess.require(VIEW); return WebApiResult.ok(directory.read(connectionId));
  }
  @GetMapping("/bindings") public WebApiResult<?> bindings(@RequestParam String connectionId) {
    EnterpriseConnectionAccess.require(VIEW); return WebApiResult.ok(Map.of("items", store.bindings(connectionId)));
  }
  public record Bind(String connectionId, String objectType, String externalId, String localId, String scopeId) { }
  @PostMapping("/bindings") public WebApiResult<?> bind(@RequestBody Bind request) {
    EnterpriseConnectionAccess.require(BIND);
    if (request.scopeId() != null && !request.scopeId().isBlank()) { EnterpriseConnectionAccess.require(SYNCHRONIZE); }
    return WebApiResult.ok(directory.bind(request.connectionId(), request.objectType(), request.externalId(), request.localId(), request.scopeId()));
  }
  public record Unbind(long expectedVersion, boolean acknowledgeImpact) { }
  @PostMapping("/bindings/{id}/unbind") public WebApiResult<?> unbind(@PathVariable String id, @RequestBody Unbind request) {
    EnterpriseConnectionAccess.require(BIND);
    if (!request.acknowledgeImpact()) { throw new com.ouroboros.exception.ValidationException("请先确认解绑影响"); }
    bindings.unbind(id, request.expectedVersion()); return WebApiResult.ok();
  }
  @GetMapping("/scopes") public WebApiResult<?> scopes() {
    EnterpriseConnectionAccess.require(VIEW); return WebApiResult.ok(Map.of("items", store.scopes()));
  }
  public record Scope(String id, String connectionId, String externalRootId, boolean enabled,
                      boolean syncMobile, boolean syncEmail, Long expectedVersion, boolean acknowledgeDisable) { }
  @PutMapping("/scopes") public WebApiResult<?> scope(@RequestBody Scope request) {
    EnterpriseConnectionAccess.require(SYNCHRONIZE);
    if (!request.enabled() && !request.acknowledgeDisable()) {
      throw new com.ouroboros.exception.ValidationException("请先确认停用同步的影响");
    }
    var scope = new DirectorySyncScope(); scope.setId(request.id()); scope.setConnectionId(request.connectionId());
    scope.setExternalRootId(request.externalRootId()); scope.setEnabled(request.enabled());
    scope.setSyncMobile(request.syncMobile()); scope.setSyncEmail(request.syncEmail());
    return WebApiResult.ok(bindings.configure(scope, request.expectedVersion()));
  }
  @PostMapping("/scopes/{id}/preview") public WebApiResult<?> preview(@PathVariable String id) {
    EnterpriseConnectionAccess.require(SYNCHRONIZE); return WebApiResult.ok(directory.preview(id));
  }
  public record PrimaryDepartment(String externalId, String primaryDepartmentId) { }
  public record Apply(boolean confirmCreation, List<PrimaryDepartment> primaryDepartmentChoices) { }
  @PostMapping("/runs/{id}/apply") public WebApiResult<?> apply(@PathVariable String id, @RequestBody Apply request) {
    EnterpriseConnectionAccess.require(SYNCHRONIZE);
    var choices = new java.util.LinkedHashMap<String, String>();
    if (request.primaryDepartmentChoices() != null) {
      for (var choice : request.primaryDepartmentChoices()) {
        if (choice.externalId() == null || choice.primaryDepartmentId() == null || choice.primaryDepartmentId().isBlank()
            || choices.put(choice.externalId(), choice.primaryDepartmentId()) != null) {
          throw new com.ouroboros.exception.ValidationException("请为每个待确认成员选择唯一主部门");
        }
      }
    }
    synchronization.apply(id, request.confirmCreation(), choices); return WebApiResult.ok();
  }
}
