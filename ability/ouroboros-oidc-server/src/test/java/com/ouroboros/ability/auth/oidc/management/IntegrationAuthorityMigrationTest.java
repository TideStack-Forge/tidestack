package com.ouroboros.ability.auth.oidc.management;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;

class IntegrationAuthorityMigrationTest {
  @Test
  void renamesPersistedGrantsAndPermissionReferencesWhilePreservingTheirRestrictions() {
    var roles = mock(RoleService.class);
    var authorities = mock(RoleAuthorityService.class);
    var role = new RoleEntity(); role.setId("role-1");
    when(roles.searchAll()).thenReturn(List.of(role));
    var old = new RoleAuthorityMapEntity(); old.setId("grant-1"); old.setRole(role.getId());
    old.setAuthority("systemSetting.authClients.manage");
    old.setAccessControlParams(Map.of("allowedFields", List.of("clientName"), "effect", "DENY",
        "filters", Map.of("clientIds", List.of("app-1"), "grantableAuthorityNames", List.of("systemSetting.authClients.security", "orders.read"))));
    when(authorities.getByRoleId(role.getId())).thenReturn(List.of(old));
    when(authorities.update(anyString(), any())).thenReturn(true);
    new IntegrationAuthorityMigration(roles, authorities).migrate();
    var saved = ArgumentCaptor.forClass(RoleAuthorityMapEntity.class);
    verify(authorities).update(eq("grant-1"), saved.capture());
    assertThat(saved.getValue().getAuthority()).isEqualTo(ClientManagementAccess.MANAGE);
    assertThat(saved.getValue().getAccessControlParams()).containsEntry("effect", "DENY")
        .containsEntry("allowedFields", List.of("clientName"));
    assertThat(saved.getValue().getAccessControlParams().toString()).contains("app-1", ClientManagementAccess.SECURITY, "orders.read")
        .doesNotContain("systemSetting.authClients");
    assertThat(old.getAuthority()).isEqualTo("systemSetting.authClients.manage");
  }

  @Test
  void alreadyMigratedGrantsAreNotRewrittenAndStorageFailureAbortsMigration() {
    var roles = mock(RoleService.class); var authorities = mock(RoleAuthorityService.class);
    var role = new RoleEntity(); role.setId("role-1"); when(roles.searchAll()).thenReturn(List.of(role));
    var grant = new RoleAuthorityMapEntity(); grant.setId("grant-1"); grant.setAuthority(ClientManagementAccess.MANAGE);
    when(authorities.getByRoleId(role.getId())).thenReturn(List.of(grant));
    var migration = new IntegrationAuthorityMigration(roles, authorities);
    migration.migrate(); verify(authorities, never()).update(anyString(), any());
    grant.setAuthority("systemSetting.authClients.manage");
    assertThatThrownBy(migration::migrate).hasMessageContaining("迁移未写入");
  }
}
