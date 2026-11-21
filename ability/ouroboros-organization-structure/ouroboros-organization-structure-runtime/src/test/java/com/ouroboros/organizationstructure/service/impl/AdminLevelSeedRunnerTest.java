package com.ouroboros.organizationstructure.service.impl;

import java.util.Collections;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import com.ouroboros.organizationstructure.entity.AdminLevelEntity;
import com.ouroboros.organizationstructure.service.AdminLevelService;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SystemSecurityIdentities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminLevelSeedRunnerTest {

  @Mock
  private AdminLevelService adminLevelService;

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void shouldSeedSevenRowsWhenEmpty() throws Exception {
    when(adminLevelService.findByCode(anyString())).thenReturn(Optional.empty());
    when(adminLevelService.create(any(AdminLevelEntity.class))).thenAnswer(invocation -> {
      assertEquals(SystemSecurityIdentities.SYSTEM_USER_ID,
          CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
      return invocation.getArgument(0);
    });
    when(adminLevelService.verifyExistingOrganizationNodeAdminLevels()).thenAnswer(invocation -> {
      assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
      return Collections.emptyList();
    });

    new AdminLevelSeedRunner(adminLevelService, true).run(null);

    verify(adminLevelService, times(7)).create(any(AdminLevelEntity.class));
    verify(adminLevelService).verifyExistingOrganizationNodeAdminLevels();
    assertFalse(CurrentSecurityContextHolder.findIdentity().isPresent());
  }

  @Test
  void shouldBeIdempotentWhenSeedsAlreadyExist() throws Exception {
    when(adminLevelService.findByCode("DEPARTMENT")).thenReturn(Optional.of(existing("dept")));
    when(adminLevelService.findByCode("DIVISION")).thenReturn(Optional.of(existing("div")));
    when(adminLevelService.findByCode("SECTION")).thenReturn(Optional.of(existing("sec")));
    when(adminLevelService.findByCode("UNIT")).thenReturn(Optional.of(existing("unit")));
    when(adminLevelService.findByCode("GROUP")).thenReturn(Optional.of(existing("group")));
    when(adminLevelService.findByCode("SUB_GROUP")).thenReturn(Optional.of(existing("sub")));
    when(adminLevelService.findByCode("BRANCH")).thenReturn(Optional.of(existing("branch")));

    new AdminLevelSeedRunner(adminLevelService, false).run(null);

    verify(adminLevelService, never()).create(any(AdminLevelEntity.class));
    verify(adminLevelService, never()).update(anyString(), any(AdminLevelEntity.class));
  }

  private AdminLevelEntity existing(String id) {
    AdminLevelEntity entity = new AdminLevelEntity();
    entity.setId(id);
    entity.setName("name");
    entity.setScope("DEPT");
    entity.setEnabled(Boolean.TRUE);
    return entity;
  }
}
