package com.ouroboros.businessmodel.event;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import com.ouroboros.businessmodel.behavior.BusinessModelBehavior;
import com.ouroboros.businessmodel.behavior.BusinessModelBehaviorProvider;
import com.ouroboros.businessmodel.behavior.EventDefinition;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

final class BusinessEventTestSupport {
  static final String MODEL = "sales.Order";
  static final BusinessEventIdentity IDENTITY = new BusinessEventIdentity(MODEL, "submitted");
  static final Map<String, Object> SCHEMA = Map.of("type", "object", "properties",
      Map.of("orderId", Map.of("type", "string")), "required", List.of("orderId"), "additionalProperties", false);

  private BusinessEventTestSupport() {
  }

  static EventDefinition event() {
    var event = new EventDefinition();
    event.setName("submitted");
    event.setSubject("model");
    event.setPayloadSchema(SCHEMA);
    event.setAllowedProducerKinds(List.of("action", "producer"));
    return event;
  }

  static BusinessModelBehaviorProvider provider(BusinessModelBehavior... behaviors) {
    Map<String, BusinessModelBehavior> models = new java.util.TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    for (var behavior : behaviors) {
      models.put(behavior.getModelFullName(), behavior);
    }
    return new BusinessModelBehaviorProvider() {
      public Optional<BusinessModelBehavior> getBusinessModelBehavior(String name) {
        return Optional.ofNullable(models.get(name));
      }

      public Map<String, BusinessModelBehavior> getBusinessModelBehaviorMap() {
        return models;
      }
    };
  }

  static User user(Map<String, ? extends AccessControlParams> permissions) {
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("event-service");
    when(user.isEnabled()).thenReturn(true);
    var role = mock(Role.class);
    List<OuroborosGrantedAuthority> grants = permissions.entrySet().stream().map(entry -> {
      var grant = mock(OuroborosGrantedAuthority.class);
      var authority = mock(Authority.class);
      when(authority.getFullName()).thenReturn(entry.getKey());
      when(grant.getAuthority()).thenReturn(entry.getKey());
      when(grant.getAuthorityObject()).thenReturn(authority);
      when(grant.getAccessControlParams()).thenReturn(entry.getValue() == AccessControlParams.empty()
          ? new com.ouroboros.security.authority.accesscontrol.SuperAccessControlParams(null) : entry.getValue());
      return grant;
    }).toList();
    when(role.getAuthorities()).thenAnswer(invocation -> grants);
    when(user.getRoles()).thenReturn(List.of(role));
    return user;
  }

  static <T> T withPermissions(List<String> permissions, Supplier<T> operation) {
    Map<String, AccessControlParams> params = new java.util.LinkedHashMap<>();
    permissions.forEach(permission -> params.put(permission, com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams.all()));
    return CurrentSecurityContextHolder.withIdentity(new DefaultSecurityIdentity(user(params)), operation);
  }

  static final class Transactions extends AbstractPlatformTransactionManager {
    int commits;
    int rollbacks;

    protected Object doGetTransaction() {
      return new Object();
    }

    protected void doBegin(Object transaction, TransactionDefinition definition) {
    }

    protected void doCommit(DefaultTransactionStatus status) {
      commits++;
    }

    protected void doRollback(DefaultTransactionStatus status) {
      rollbacks++;
    }
  }
}
