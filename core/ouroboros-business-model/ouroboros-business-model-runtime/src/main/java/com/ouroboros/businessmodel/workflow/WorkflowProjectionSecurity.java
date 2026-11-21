package com.ouroboros.businessmodel.workflow;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import com.ouroboros.businessmodel.event.BusinessEventAuthorities;
import com.ouroboros.businessmodel.event.BusinessEventIdentity;
import com.ouroboros.businessmodel.security.BusinessBehaviorSecurity;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

/** Internal callback identity grants only this model's workflow permission and this Event's emit permission. */
final class WorkflowProjectionSecurity {
  private WorkflowProjectionSecurity() { }

  static <T> T withFact(BusinessEventIdentity event, Object recordId, Supplier<T> operation) {
    String permission = event.modelFullName() + ".workflow";
    AccessControlParams recordAccess = new RecordAccess(Map.of("id", Map.of("=", recordId)));
    Role role = new ProjectionRole(List.of(grant(permission, recordAccess),
        grant(BusinessEventAuthorities.emit(event), AccessControlParams.empty())));
    User user = new User() {
      @Override
      public String getUserIdentity() { return "workflow-projection:" + event.modelFullName(); }

      @Override
      public List<Role> getRoles() { return List.of(role); }
    };
    return CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user), () ->
        BusinessBehaviorSecurity.withAuthority(permission, event.modelFullName(), operation));
  }

  private static OuroborosGrantedAuthority grant(String name, AccessControlParams params) {
    var authority = new DefaultAuthority();
    authority.setFullName(name);
    return new Grant(name, authority, params);
  }

  private record Grant(String getAuthority, Authority getAuthorityObject,
                       AccessControlParams getAccessControlParams) implements OuroborosGrantedAuthority { }

  private record ProjectionRole(List<? extends OuroborosGrantedAuthority> getAuthorities) implements Role {
    @Override
    public String getId() { return "workflow-projection"; }

    @Override
    public String getName() { return "业务工作流事实投影"; }
  }

  private record RecordAccess(Map<String, Object> getFilters) implements AccessControlParams {
    @Override
    public List<String> getAllowedFields() { return List.of("*"); }

    @Override
    public List<String> getDisallowedFields() { return List.of(); }
  }
}
