package com.ouroboros.security.authorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.authentication.OuroborosAuthentication.EffectiveAuthority;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.ResolvedAccessControlParams;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.identity.AuthorizationBoundary;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

class AuthorizationEvaluatorTest {
  private final BaseAuthority read = authority("orders.search", true);
  private final BaseAuthority manage = authority("system.manage", false);
  private final Map<String, Authority> definitions = Map.of(read.getFullName(), read, manage.getFullName(), manage);
  private final AuthorizationEvaluator evaluator = new AuthorizationEvaluator(name -> Optional.ofNullable(definitions.get(name)));

  @Test
  void legacyBlacklistIsTheCompleteComplementAndCannotRepresentAnUnknownUniverse() {
    var fields = mock(com.ouroboros.security.authority.accesscontrol.AccessControlDefine.class);
    when(fields.getCommonFields()).thenReturn(List.of(
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("id", "标识", "String", null),
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("name", "名称", "String", null),
        new com.ouroboros.security.authority.accesscontrol.AccessControlDefine.Field("cost", "金额", "String", null)));
    when(fields.getControlledFields()).thenReturn(List.of());
    read.setAccessControlDefine(fields);
    var user = user(List.of(role(read, new ResolvedAccessControlParams(FieldConstraint.only(Set.of("id", "name")), RowConstraint.all()))));
    var delegated = new DefaultSecurityIdentity(user,
        AuthorizationBoundary.restricted("platform-api", Map.of("orders.search",
            new ResolvedAccessControlParams(FieldConstraint.only(Set.of("id")), RowConstraint.all()))),
        new CredentialContext(CredentialContext.Kind.DELEGATED, "c", "g", "platform-api"));
    assertThat(evaluator.evaluate(delegated, "platform-api", "orders.search").requirePermit().getDisallowedFields())
        .containsExactly("cost", "name");
    assertThat(evaluator.evaluate(new DefaultSecurityIdentity(user), "platform-api", "orders.search").requirePermit().getDisallowedFields())
        .containsExactly("cost");
    read.setAccessControlDefine(com.ouroboros.security.authority.accesscontrol.AccessControlDefine.empty());
    assertThatThrownBy(() -> evaluator.evaluate(delegated, "platform-api", "orders.search").requirePermit().getDisallowedFields())
        .isInstanceOf(com.ouroboros.security.exception.AuthorizationDeniedException.class);
  }

  @Test
  void roleUnionIsIntersectedWithCredentialAndNeverChangesSharedUser() {
    User user = user(List.of(role(read, params(Set.of("id", "name"), "d1")),
        role(read, params(Set.of("id", "cost"), "d2"))));
    var platform = new DefaultSecurityIdentity(user);
    var full = evaluator.evaluate(platform, "platform-api", "orders.search").requirePermit();
    assertThat(full.getFieldConstraint().fields()).containsExactlyInAnyOrder("id", "name", "cost");
    assertThat(full.getRowConstraint().condition()).containsKey("OR");
    var delegated = new DefaultSecurityIdentity(user,
        AuthorizationBoundary.restricted("platform-api", Map.of("orders.search", params(Set.of("id", "name"), "d1"))),
        new CredentialContext(CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api"));
    var limited = evaluator.evaluate(delegated, "platform-api", "orders.search").requirePermit();
    assertThat(limited.getFieldConstraint().fields()).containsExactlyInAnyOrder("id", "name");
    assertThat(limited.getRowConstraint().condition()).containsKey("AND");
    assertThat(evaluator.evaluate(platform, "platform-api", "orders.search").requirePermit().getFieldConstraint())
        .isEqualTo(full.getFieldConstraint());
    assertThat(delegated.getUserDetail()).isSameAs(user);
    assertThat(user.getRoles()).hasSize(2);
  }

  @Test
  void unrestrictedStillRequiresAnExistingRoleGrant() {
    var identity = new DefaultSecurityIdentity(user(List.of()));
    assertThat(evaluator.evaluate(identity, "platform-api", "orders.search"))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.NOT_GRANTED));
    assertThat(evaluator.evaluate(identity, "platform-api", "unknown"))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.UNKNOWN_AUTHORITY));
    assertThat(evaluator.evaluate(null, "platform-api", "orders.search"))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.MISSING_IDENTITY));
  }

  @Test
  void emptyBoundaryWrongResourceAndNondelegableAuthorityAreDenied() {
    var user = user(List.of(role(read, ResolvedAccessControlParams.all()), role(manage, ResolvedAccessControlParams.all())));
    var identity = new DefaultSecurityIdentity(user, AuthorizationBoundary.restricted("platform-api", Map.of()),
        new CredentialContext(CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api"));
    assertThat(evaluator.effectiveAuthorities(identity, "platform-api")).isEmpty();
    assertThat(evaluator.evaluate(identity, "other-resource", "orders.search"))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.RESOURCE_MISMATCH));
    assertThat(evaluator.evaluate(identity, "platform-api", "system.manage"))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.NOT_DELEGABLE));
    assertThatThrownBy(() -> evaluator.evaluate(identity, "platform-api", "orders.search").requirePermit())
        .hasMessageContaining("BOUNDARY_EXCLUDED");
  }

  @Test
  void requestWithIncompatibleTargetsFailsInsteadOfReturningUnrestrictedParameters() {
    var identity = new DefaultSecurityIdentity(user(List.of(role(read, ResolvedAccessControlParams.all()),
        role(manage, ResolvedAccessControlParams.all()))));
    assertThat(evaluator.permitsRequest(identity, "platform-api", List.of("orders.search", "system.manage"))).isTrue();
    assertThat(evaluator.permitsRequest(identity, "platform-api", List.of("unknown"))).isFalse();
    assertThat(evaluator.evaluateRequest(identity, "platform-api", List.of("orders.search", "system.manage")))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.AMBIGUOUS_DECISION_TARGET));
    assertThat(evaluator.evaluateRequest(identity, "platform-api", List.of()))
        .isEqualTo(new AuthorizationDecision.Deny(AuthorizationDecision.Reason.MISSING_DECISION_TARGET));
    assertThat(evaluator.evaluateRequest(identity, "platform-api", List.of("unknown", "orders.search")))
        .isInstanceOf(AuthorizationDecision.Permit.class);
  }

  @Test
  void credentialAssociationCannotClaimAnUnrestrictedPlatformIdentity() {
    var user = user(List.of());
    var delegated = new CredentialContext(CredentialContext.Kind.DELEGATED, "c", "g", "platform-api");
    assertThatThrownBy(() -> new DefaultSecurityIdentity(user, AuthorizationBoundary.unrestricted(), delegated))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new DefaultSecurityIdentity(user, AuthorizationBoundary.restricted("wrong", Map.of()), delegated))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new CredentialContext(CredentialContext.Kind.DELEGATED, "c", null, "platform-api"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void frozenBoundaryDoesNotFollowFutureChangesToRoleSettingsOrCollections() {
    List<Object> departments = new ArrayList<>(List.of("d1"));
    Map<String, Object> mutable = new java.util.HashMap<>(Map.of("department", departments));
    var frozen = AuthorizationBoundary.restricted("platform-api", Map.of("orders.search",
        new ResolvedAccessControlParams(FieldConstraint.only(Set.of("id")), RowConstraint.filter(mutable))));
    departments.add("d2");
    mutable.put("extra", true);
    assertThat(frozen.permissions().get("orders.search").getRowConstraint().condition())
        .isEqualTo(Map.of("department", List.of("d1")));
    assertThatThrownBy(() -> frozen.permissions().clear()).isInstanceOf(UnsupportedOperationException.class);
  }

  private BaseAuthority authority(String name, boolean delegable) {
    var authority = new BaseAuthority();
    authority.setFullName(name);
    authority.setDelegable(delegable);
    return authority;
  }

  private AccessControlParams params(Set<String> fields, String department) {
    return new ResolvedAccessControlParams(FieldConstraint.only(fields), RowConstraint.filter(Map.of("department", department)));
  }

  private Role role(Authority authority, AccessControlParams params) {
    Role role = mock(Role.class);
    when(role.getAuthorities()).thenAnswer(invocation -> List.of(new EffectiveAuthority(authority, params)));
    return role;
  }

  private User user(List<Role> roles) {
    User user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("user");
    when(user.getRoles()).thenReturn(roles);
    return user;
  }
}
