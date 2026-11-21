package com.ouroboros.security.rbac.service.impl;

import java.util.Arrays;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.ouroboros.data.model.TypedDataModelCenter;
import com.ouroboros.security.externalauth.ExternalIdentityAttributes;
import com.ouroboros.security.rbac.Application;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserRoleMapEntity;
import com.ouroboros.security.rbac.entity.UserSsoEntity;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;
import com.ouroboros.security.rbac.service.UserRoleService;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.security.rbac.service.UserSsoService;
import com.ouroboros.security.rbac.service.UserSubjectService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = Application.class, properties = {
    "spring.datasource.url=jdbc:h2:mem:rbac_service_contract;MODE=MySQL;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1"
})
class ServiceImplIntegrationCoverageTest {

  @Autowired
  private UserService userService;

  @Autowired
  private RoleService roleService;

  @Autowired
  private UserRoleService userRoleService;

  @Autowired
  private RoleAuthorityService roleAuthorityService;

  @Autowired
  private UserSubjectService userSubjectService;

  @Autowired
  private UserSsoService userSsoService;

  @Autowired
  private org.springframework.context.ApplicationContext applicationContext;

  @BeforeEach
  void refreshAndRequireDataModels() {
    com.ouroboros.security.context.CurrentSecurityContextHolder.setDelegate(() -> java.util.Optional.of(
        com.ouroboros.security.identity.SystemSecurityIdentities.system()));
    applicationContext.publishEvent(new com.ouroboros.lifecycle.event.OuroborosModelRefreshingEvent(applicationContext));
    applicationContext.publishEvent(new com.ouroboros.lifecycle.event.OuroborosModelRefreshedEvent(applicationContext));
    assertTrue(TypedDataModelCenter.getDataModel(String.class, UserEntity.class).isPresent());
    assertTrue(TypedDataModelCenter.getDataModel(String.class, RoleEntity.class).isPresent());
    assertTrue(TypedDataModelCenter.getDataModel(String.class, UserRoleMapEntity.class).isPresent());
    assertTrue(TypedDataModelCenter.getDataModel(String.class, RoleAuthorityMapEntity.class).isPresent());
    assertTrue(TypedDataModelCenter.getDataModel(String.class, UserSubjectEntity.class).isPresent());
    assertTrue(TypedDataModelCenter.getDataModel(String.class, UserSsoEntity.class).isPresent());
  }

  @org.junit.jupiter.api.AfterEach
  void clearActor() { com.ouroboros.security.context.CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void userAndRoleServicesShouldCoverCoreCrudBranches() throws Exception {
    var suffix = String.valueOf(System.currentTimeMillis());
    var userId = "it-user-" + suffix;
    var roleId = "it-role-" + suffix;

    var user = new UserEntity();
    user.setUserIdentity(userId);
    user.setPassword("p1");
    user.setAvatar("a1");
    var insertedUser = addAccount(user);
    assertNotNull(insertedUser);

    assertTrue(userService.getByUserIdentity(userId).isPresent());
    assertTrue(userService.getPasswordByUserIdentity(userId).isPresent());
    assertTrue(userService.modifyPassword(userId, "p2"));
    assertTrue(userService.resetPassword(userId));
    assertFalse(userService.modifyPassword("missing-" + suffix, "p3"));

    var role = new RoleEntity();
    role.setId(roleId);
    role.setName("role-" + suffix);
    var insertedRole = roleService.add(role);
    assertNotNull(insertedRole);
    assertTrue(roleService.getById(roleId).isPresent());

    role.setName("role-updated-" + suffix);
    assertTrue(roleService.update(roleId, role));
    assertFalse(roleService.update("missing-role-" + suffix, role));
  }

  @Test
  void relationServicesShouldCoverAddSearchDeleteAndBatchPaths() throws Exception {
    var suffix = String.valueOf(System.nanoTime());
    var userId = "it-u-" + suffix;
    var roleId = "it-r-" + suffix;

    var user = new UserEntity();
    user.setUserIdentity(userId);
    user.setPassword("p");
    addAccount(user);

    var role = new RoleEntity();
    role.setId(roleId);
    role.setName("n-" + suffix);
    roleService.add(role);

    var map1 = new UserRoleMapEntity(userId, roleId);
    var insertedMap = userRoleService.add(map1);
    assertNotNull(insertedMap.getId());
    assertTrue(userRoleService.getByRoleIdAndUserIdentity(roleId, userId).isPresent());
    assertFalse(userRoleService.searchListByRoleId(roleId).isEmpty());
    assertFalse(userRoleService.searchListByUserIdentity(userId).isEmpty());

    var map2 = new UserRoleMapEntity(userId, roleId + "-2");
    userRoleService.add(Collections.singletonList(map2));
    assertTrue(userRoleService.deleteByUserIdentityAndRoleIds(userId, Arrays.asList(roleId + "-2")));
    assertTrue(userRoleService.deleteByRoleIdAndUserIdentities(roleId, Arrays.asList(userId)));
    assertFalse(userRoleService.deleteByRoleIdAndUserIdentities("missing-role", Arrays.asList(userId)));
  }

  @Test
  void authoritySubjectAndSsoServicesShouldCoverPrimaryBranches() throws Exception {
    var suffix = String.valueOf(System.nanoTime());
    var roleId = "it-auth-role-" + suffix;
    var authName = "system.auth." + suffix;

    var roleAuth = new RoleAuthorityMapEntity();
    roleAuth.setRole(roleId);
    roleAuth.setAuthority(authName);
    roleAuth.setAccessControlParams(Collections.emptyMap());
    var inserted = roleAuthorityService.add(roleAuth);
    assertNotNull(inserted.getId());
    assertFalse(roleAuthorityService.getByRoleId(roleId).isEmpty());
    assertFalse(roleAuthorityService.getByRoleIds(Arrays.asList(roleId)).isEmpty());

    roleAuth.setId(inserted.getId());
    roleAuth.setAccessControlParams(Collections.singletonMap("k", "v"));
    roleAuthorityService.updateRoleAuthorities(roleId, Collections.singletonList(roleAuth));
    assertTrue(roleAuthorityService.delete(Collections.singletonList(inserted.getId())));

    var userId = "it-sub-u-" + suffix;
    var user = new UserEntity();
    user.setUserIdentity(userId);
    user.setPassword("pwd");
    addAccount(user);

    var subject = userSubjectService.create(userId, "ORG", "org-" + suffix);
    assertNotNull(subject.getId());
    assertTrue(userSubjectService.findByUser(userId).isPresent());
    assertTrue(userSubjectService.findByUserAndSubjectType(userId, "ORG").isPresent());
    assertFalse(userSubjectService.findAllByUser(userId).isEmpty());
    assertTrue(userSubjectService.findBySubject("ORG", "org-" + suffix).isPresent());
    assertTrue(userSubjectService.deleteByUserAndSubjectType(userId, "ORG"));
    assertFalse(userSubjectService.deleteByUserAndSubjectType(userId, "ORG"));

    var sso = userSsoService.createBinding(userId, "ding", "ext-" + suffix, "d", "e@x.com",
        java.util.Map.of(ExternalIdentityAttributes.MEMBER_ID, "member-" + suffix));
    assertNotNull(sso.getId());
    assertTrue(userSsoService.findByChannelAndExternalId("ding", "ext-" + suffix).isPresent());
    assertTrue(userSsoService.findByChannelAndMemberId("ding", "member-" + suffix).isPresent());
    assertFalse(userSsoService.findByUserIdentity(userId).isEmpty());
    assertTrue(userSsoService.existsByChannelAndExternalId("ding", "ext-" + suffix));
    assertTrue(userSsoService.deleteBinding(sso.getId()));
    assertFalse(userSsoService.findByChannelAndMemberId("ding", "member-" + suffix).isPresent());
    assertFalse(userSsoService.deleteBinding("missing-sso-" + suffix));
  }
  private UserEntity addAccount(UserEntity input) {
    try {
      return userService.add(input);
    } catch (com.ouroboros.exception.ValidationException error) {
      throw new AssertionError("Account creation validation fields: " + error.getErrors().keySet(), error);
    }
  }

}
