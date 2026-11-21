package com.ouroboros.security.rbac.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

class EntityPojoCoverageTest {

  @Test
  void roleEntityShouldSupportConstructorCopyAndAllFields() {
    var now = LocalDateTime.now();
    var source = new RoleEntity();
    source.setId("r1");
    source.setName("name");
    source.setCreatedBy("c");
    source.setCreatedAt(now);
    source.setUpdatedBy("u");
    source.setUpdatedAt(now);
    source.setDeletedBy("d");
    source.setDeletedAt(now);
    source.setDeleted(true);

    var copied = new RoleEntity(source);

    assertEquals("r1", copied.getId());
    assertEquals("name", copied.getName());
    assertEquals("c", copied.getCreatedBy());
    assertEquals(now, copied.getCreatedAt());
    assertEquals("u", copied.getUpdatedBy());
    assertEquals(now, copied.getUpdatedAt());
    assertEquals("d", copied.getDeletedBy());
    assertEquals(now, copied.getDeletedAt());
    assertTrue(copied.isDeleted());
  }

  @Test
  void userSsoEntityShouldReadWriteAllProperties() {
    var now = LocalDateTime.now();
    var entity = new UserSsoEntity();
    entity.setId("id");
    entity.setUserIdentity("u1");
    entity.setSsoChannel("wechat");
    entity.setSsoUserIdentity("openid");
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    entity.setExternalDisplayName("display");
    entity.setExternalEmail("a@b.com");

    assertEquals("id", entity.getId());
    assertEquals("u1", entity.getUserIdentity());
    assertEquals("wechat", entity.getSsoChannel());
    assertEquals("openid", entity.getSsoUserIdentity());
    assertEquals(now, entity.getCreatedAt());
    assertEquals(now, entity.getUpdatedAt());
    assertEquals("display", entity.getExternalDisplayName());
    assertEquals("a@b.com", entity.getExternalEmail());
  }

  @Test
  void roleAuthorityMapEntityShouldDefaultParamsToEmptyMap() {
    var entity = new RoleAuthorityMapEntity();
    entity.setId("id1");
    entity.setRole("role1");
    entity.setAuthority("authority1");

    assertNotNull(entity.getAccessControlParams());
    assertTrue(entity.getAccessControlParams().isEmpty());

    var params = new HashMap<String, Object>();
    params.put("k", "v");
    entity.setAccessControlParams(params);

    assertEquals("id1", entity.getId());
    assertEquals("role1", entity.getRole());
    assertEquals("authority1", entity.getAuthority());
    assertEquals("v", entity.getAccessControlParams().get("k"));
  }

  @Test
  void userRoleMapEntityShouldSupportConstructorsAndMutators() {
    var entity = new UserRoleMapEntity("u", "r");
    entity.setId("id");

    assertEquals("id", entity.getId());
    assertEquals("u", entity.getUser());
    assertEquals("r", entity.getRole());

    entity.setUser("u2");
    entity.setRole("r2");
    assertEquals("u2", entity.getUser());
    assertEquals("r2", entity.getRole());
  }

  @Test
  void userEntityCopyConstructorShouldPreserveState() {
    var now = LocalDateTime.now();
    var source = new UserEntity();
    source.setUserIdentity("u1");
    source.setPassword("p");
    source.setAvatar("a");
    source.setCreatedBy("c");
    source.setCreatedAt(now);
    source.setUpdatedBy("u");
    source.setUpdatedAt(now);
    source.setDeletedBy("d");
    source.setDeletedAt(now);
    source.setDeleted(true);
    source.setEnabled(false);

    var copied = new UserEntity(source);

    assertEquals("u1", copied.getUserIdentity());
    assertEquals("p", copied.getPassword());
    assertEquals("a", copied.getAvatar());
    assertEquals("c", copied.getCreatedBy());
    assertEquals(now, copied.getCreatedAt());
    assertEquals("u", copied.getUpdatedBy());
    assertEquals(now, copied.getUpdatedAt());
    assertEquals("d", copied.getDeletedBy());
    assertEquals(now, copied.getDeletedAt());
    assertTrue(copied.isDeleted());
    assertFalse(copied.isEnabled());
  }

  @Test
  void userSubjectEntityShouldSupportFluentIdSetter() {
    var now = LocalDateTime.now();
    var entity = new UserSubjectEntity()
        .setId("id1");
    entity.setUserIdentity("u1");
    entity.setSubjectType("employee");
    entity.setSubjectId("s1");
    entity.setCreatedBy("creator");
    entity.setCreatedAt(now);

    assertEquals("id1", entity.getId());
    assertEquals("u1", entity.getUserIdentity());
    assertEquals("employee", entity.getSubjectType());
    assertEquals("s1", entity.getSubjectId());
    assertEquals("creator", entity.getCreatedBy());
    assertEquals(now, entity.getCreatedAt());
    assertEquals(Collections.emptyMap(), new RoleAuthorityMapEntity().getAccessControlParams());
  }
}
