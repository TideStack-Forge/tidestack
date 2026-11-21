package com.ouroboros.security.rbac.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityProvider;
import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.RoleEntity;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;
import com.ouroboros.security.rbac.model.AdministratorRoleModel;
import com.ouroboros.security.rbac.model.RbacRoleModel;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.service.RoleService;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.security.rbac.service.UserSubjectService;

class ProviderLayerSmokeTest {

  @Test
  void springBeanAuthorityProviderShouldAggregateAuthorities() {
    var p1 = mock(AuthorityProvider.class);
    var p2 = mock(AuthorityProvider.class);
    var a1 = mock(Authority.class);
    var a2 = mock(Authority.class);

    when(p1.getAuthority("a")).thenReturn(Collections.singletonList(a1));
    when(p2.getAuthority("a")).thenReturn(Collections.singletonList(a2));
    when(p1.getAllAuthorities()).thenReturn(Collections.singletonList(a1));
    when(p2.getAllAuthorities()).thenReturn(Collections.singletonList(a2));

    var provider = new SpringBeanAuthorityProvider();
    ReflectionTestUtils.setField(provider, "authorityProviders", Arrays.asList(p1, p2));

    assertEquals(2, provider.getAuthority("a").size());
    assertEquals(2, provider.getAllAuthority().size());
    assertTrue(new SpringBeanAuthorityProvider.SingletonHolder().getAuthority("x").isEmpty());
    ReflectionTestUtils.setField(provider, "authorityProviders", null);
    assertTrue(provider.getAuthority("x").isEmpty());
    assertTrue(provider.getAllAuthority().isEmpty());
    ReflectionTestUtils.setField(SpringBeanAuthorityProvider.SingletonHolder.class, "AUTHORITY_PROVIDER", null);
    assertTrue(new SpringBeanAuthorityProvider.SingletonHolder().getAllAuthorities().isEmpty());
  }

  @Test
  void administratorRoleProviderShouldReturnAdministratorRoleOnly() {
    var provider = new AdministratorRoleProvider();

    var role = provider.getRole(provider.getAdministratorRoleId());
    var missing = provider.getRole("other");
    var all = provider.getAllRole();

    assertTrue(role.isPresent());
    assertFalse(missing.isPresent());
    assertEquals(1, all.size());
    assertTrue(all.containsKey("administrator"));
  }

  @Test
  void rbacUserProviderShouldMapServiceEntitiesToUsers() {
    var userService = mock(UserService.class);
    var claimsAggregator = mock(ClaimsAggregator.class);
    var subjectCenter = mock(SubjectCenter.class);
    var userSubjectCenter = mock(UserSubjectCenter.class);

    var userEntity = new UserEntity();
    userEntity.setUserIdentity("u-1");

    when(userService.getByUserIdentity("u-1")).thenReturn(Optional.of(userEntity));
    when(userService.searchAll()).thenReturn(Collections.singletonList(userEntity));

    var provider = new RbacUserProvider();
    ReflectionTestUtils.setField(provider, "userService", userService);
    ReflectionTestUtils.setField(provider, "claimsAggregator", claimsAggregator);
    ReflectionTestUtils.setField(provider, "subjectCenter", subjectCenter);
    ReflectionTestUtils.setField(provider, "userSubjectCenter", userSubjectCenter);

    var one = provider.getUser("u-1");
    var all = provider.getAllUser();

    assertTrue(one.isPresent());
    assertEquals("u-1", one.get().getUserIdentity());
    assertEquals(1, all.size());
  }

  @Test
  void rbacRoleProviderShouldMapServiceEntitiesToRoles() {
    var roleService = mock(RoleService.class);
    var roleAuthorityService = mock(RoleAuthorityService.class);

    var roleEntity = new RoleEntity();
    roleEntity.setId("r-1");
    roleEntity.setName("role-name");

    var mapEntity = new RoleAuthorityMapEntity();
    mapEntity.setId("m-1");
    mapEntity.setRole("r-1");
    mapEntity.setAuthority("system.read");

    when(roleService.getById("r-1")).thenReturn(Optional.of(roleEntity));
    when(roleService.searchAll()).thenReturn(Collections.singletonList(roleEntity));
    when(roleAuthorityService.getByRoleId("r-1")).thenReturn(Collections.singletonList(mapEntity));

    var provider = new RbacRoleProvider();
    ReflectionTestUtils.setField(provider, "roleService", roleService);
    ReflectionTestUtils.setField(provider, "roleAuthorityService", roleAuthorityService);

    var role = provider.getRole("r-1");
    var all = provider.getAllRole();

    assertTrue(role.isPresent());
    assertEquals("r-1", role.get().getId());
    assertEquals(1, all.size());
  }

  @Test
  void rbacUserSubjectProviderShouldResolveAllAccessPatterns() {
    var service = mock(UserSubjectService.class);
    var provider = new RbacUserSubjectProvider(service);

    var userSubject = new UserSubjectEntity();
    userSubject.setUserIdentity("u1");
    userSubject.setSubjectType("employee");
    userSubject.setSubjectId("s1");

    when(service.findByUser("u1")).thenReturn(Optional.of(userSubject));
    when(service.findByUserAndSubjectType("u1", "employee")).thenReturn(Optional.of(userSubject));
    when(service.findAllByUser("u1")).thenReturn(Collections.singletonList(userSubject));
    when(service.findBySubject("employee", "s1")).thenReturn(Optional.of(userSubject));
    when(service.findAll()).thenReturn(Collections.singletonList(userSubject));

    assertTrue(provider.getByUser("u1").isPresent());
    assertTrue(provider.getByUser("u1", "employee").isPresent());
    assertEquals(1, provider.getAllByUser("u1").size());
    assertEquals(2, provider.getAllByUsers(Arrays.asList("u1", "u2")).size());
    assertEquals(1, provider.getByUsers(Arrays.asList("u1", "u2"), "employee").size());
    assertTrue(provider.getBySubject("employee", "s1").isPresent());
    assertEquals(1, provider.getAll().size());
  }

  @Test
  void rbacRoleModelShouldCacheAuthorityModelsAndExposeMetadata() {
    var roleEntity = new RoleEntity();
    roleEntity.setId("r1");
    roleEntity.setName("role1");

    var mapEntity = new RoleAuthorityMapEntity();
    mapEntity.setAuthority("auth.x");

    var model = new RbacRoleModel(roleEntity, Collections.singletonList(mapEntity));
    var first = model.getAuthorities();
    var second = model.getAuthorities();

    assertEquals("r1", model.getId());
    assertEquals("role1", model.getName());
    assertEquals(1, first.size());
    assertSame(first, second);
  }

  @Test
  void administratorRoleModelShouldExposeIdAndName() {
    var model = new AdministratorRoleModel();
    assertEquals("administrator", model.getId());
    assertEquals("超级管理员", model.getName());
  }
}
