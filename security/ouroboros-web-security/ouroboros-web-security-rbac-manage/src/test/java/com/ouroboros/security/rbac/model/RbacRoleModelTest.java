package com.ouroboros.security.rbac.model;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RbacRoleModelTest {

  @Test
  void shouldCacheAuthoritiesAndExposeCreatedAt() {
    var entity = new RoleEntity();
    entity.setId("r1");
    entity.setName("Role 1");
    entity.setCreatedAt(LocalDateTime.now());

    var authority = new RoleAuthorityMapEntity();
    authority.setAuthority("sys.read");

    var model = new RbacRoleModel(entity, Collections.singletonList(authority));

    var first = model.getAuthorities();
    var second = model.getAuthorities();

    assertEquals("r1", model.getId());
    assertEquals("Role 1", model.getName());
    assertSame(first, second);
    assertTrue(model.getCreatedAt() != null);
  }
}
