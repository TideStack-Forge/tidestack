package com.ouroboros.ability.auth.oidc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import com.ouroboros.security.authority.BaseAuthority;
import com.ouroboros.security.authority.accesscontrol.*;

class OidcGrantPermissionsTest {
  @Test
  void freezesAllFieldsAndPreservesExplicitExclusionsAndRows() {
    var authority = authority();
    var snapshot = OidcGrantPermissions.freeze(authority, ResolvedAccessControlParams.all());
    assertEquals(FieldConstraint.only(Set.of("id", "name")), snapshot.getFieldConstraint());
    var limited = OidcGrantPermissions.freeze(authority,
        new ResolvedAccessControlParams(new FieldConstraint(FieldConstraint.Mode.ALL, Set.of(), Set.of("name")), RowConstraint.filter(java.util.Map.of("id", "one"))));
    assertEquals(FieldConstraint.only(Set.of("id")), limited.getFieldConstraint());
    assertEquals(RowConstraint.filter(java.util.Map.of("id", "one")), limited.getRowConstraint());
  }

  @Test
  void unknownFieldUniverseCannotCreateAnUnboundedGrant() {
    assertThrows(OAuth2AuthenticationException.class,
        () -> OidcGrantPermissions.freeze(new BaseAuthority(), ResolvedAccessControlParams.all()));
  }

  @Test
  void executionChangesInvalidateDigestAndDisplayChangesDoNot() {
    var authority = authority();
    String before = OidcGrantPermissions.digest(authority);
    authority.setTitle("新展示名称");
    assertEquals(before, OidcGrantPermissions.digest(authority));
    authority.setApis(List.of("GET /api/another-resource"));
    assertNotEquals(before, OidcGrantPermissions.digest(authority));
    authority.setApis(List.of("GET /api/records"));
    var changed = mock(AccessControlDefine.class);
    when(changed.getCommonFields()).thenReturn(List.of(new AccessControlDefine.Field("id", "编号", "String", "")));
    when(changed.getControlledFields()).thenReturn(List.of());
    when(changed.getFilterDefine()).thenReturn(List.of());
    authority.setAccessControlDefine(changed);
    assertNotEquals(before, OidcGrantPermissions.digest(authority));
  }

  private BaseAuthority authority() {
    var authority = new BaseAuthority();
    authority.setFullName("records.search"); authority.setDelegable(true); authority.setApis(List.of("GET /api/records"));
    var definition = mock(AccessControlDefine.class);
    when(definition.getCommonFields()).thenReturn(List.of(new AccessControlDefine.Field("id", "编号", "String", ""),
        new AccessControlDefine.Field("name", "名称", "String", "")));
    when(definition.getControlledFields()).thenReturn(List.of());
    when(definition.getFilterDefine()).thenReturn(List.of());
    authority.setAccessControlDefine(definition);
    return authority;
  }
}
