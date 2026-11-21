package com.ouroboros.security.rbac.authority;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.FieldConstraint;
import com.ouroboros.security.authority.accesscontrol.RowConstraint;
import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsAggregator;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;
import com.ouroboros.security.rbac.model.AdministratorRoleModel;
import com.ouroboros.security.rbac.model.RbacUserModel;
import com.ouroboros.security.rbac.model.UserSubjectModel;
import com.ouroboros.security.rbac.authority.RbacGrantedAuthorityModel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthorityAndModelCoverageTest {

  @Test
  void administratorGrantedAuthorityModelShouldExposeAllAccessors() {
    var authority = mock(Authority.class);
    when(authority.getFullName()).thenReturn("sys.read");
    var definition = fieldDefinition();
    when(authority.getAccessControlDefine()).thenReturn(definition);

    var model = new AdministratorGrantedAuthorityModel(authority);

    assertEquals("sys.read", model.getAuthority());
    assertSame(authority, model.getAuthorityObject());
    assertEquals(FieldConstraint.all(), model.getAccessControlParams().getFieldConstraint());
    assertEquals(RowConstraint.all(), model.getAccessControlParams().getRowConstraint());
  }

  @Test
  void rbacGrantedAuthorityModelShouldUsePresetAuthorityAndCoverParamsBranches() {
    var entity = new RoleAuthorityMapEntity();
    entity.setAuthority("sys.write");
    entity.setAccessControlParams(new HashMap<String, Object>());

    var authority = mock(Authority.class);
    when(authority.getAccessControlDefine()).thenReturn(mock(AccessControlDefine.class));

    var model = new RbacGrantedAuthorityModel(entity);
    ReflectionTestUtils.setField(model, "authorityObject", authority);

    assertEquals("sys.write", model.getAuthority());
    assertNotNull(model.getAccessControlParams());

    var params = new HashMap<String, Object>();
    params.put("k", "v");
    entity.setAccessControlParams(params);
    assertNotNull(model.getAccessControlParams());
  }

  @Test
  void unconfiguredRbacRestrictionsAllowAllFieldsAndRows() {
    var authority = mock(Authority.class);
    var definition = fieldDefinition();
    when(authority.getAccessControlDefine()).thenReturn(definition);
    for (var settings : Arrays.<Map<String, Object>>asList(null, Map.of(), Map.of("$fieldControl", List.of()))) {
      var entity = new RoleAuthorityMapEntity();
      entity.setAuthority("app.orders.read");
      entity.setAccessControlParams(settings);
      var model = new RbacGrantedAuthorityModel(entity);
      ReflectionTestUtils.setField(model, "authorityObject", authority);

      var params = model.getAccessControlParams();
      assertEquals(FieldConstraint.all(), params.getFieldConstraint());
      assertTrue(params.getFieldConstraint().allows("newBusinessField"));
      assertEquals(List.of("*"), params.getAllowedFields());
      assertTrue(params.getDisallowedFields().isEmpty());
      assertEquals(RowConstraint.all(), params.getRowConstraint());
    }
  }

  private AccessControlDefine fieldDefinition() {
    var definition = mock(AccessControlDefine.class);
    when(definition.getCanControlFields()).thenReturn(true);
    when(definition.getCommonFields()).thenReturn(List.of(new AccessControlDefine.Field("id", "标识", "String", null)));
    when(definition.getControlledFields()).thenReturn(List.of(new AccessControlDefine.Field("name", "名称", "String", null)));
    return definition;
  }

  @Test
  void rbacGrantedAuthorityModelShouldReturnEmptyWhenAuthorityMissing() {
    var entity = new RoleAuthorityMapEntity();
    entity.setAuthority("sys.missing");
    var model = new RbacGrantedAuthorityModel(entity);
    assertEquals(AccessControlParams.empty().getClass(), model.getAccessControlParams().getClass());
  }

  @Test
  void rbacUserModelShouldCacheClaimsAndResolveSubject() {
    var entity = new UserEntity();
    entity.setUserIdentity("u1");
    entity.setEnabled(true);
    entity.setCreatedAt(LocalDateTime.now());

    var claimsAggregator = mock(ClaimsAggregator.class);
    var claims = Claims.builder().claim("x", "y").build();
    var userSubjectCenter = mock(UserSubjectCenter.class);
    var subjectCenter = mock(SubjectCenter.class);
    var userSubject = mock(UserSubject.class);
    var subject = mock(Subject.class);

    when(claimsAggregator.aggregate(org.mockito.ArgumentMatchers.any())).thenReturn(claims);
    when(userSubjectCenter.getByUser("u1")).thenReturn(Optional.of(userSubject));
    when(userSubject.getSubjectType()).thenReturn("ORG");
    when(userSubject.getSubjectId()).thenReturn("org-1");
    when(subjectCenter.getByTypeAndId("ORG", "org-1")).thenReturn(Optional.of(subject));

    var model = new RbacUserModel(entity, claimsAggregator, subjectCenter, userSubjectCenter);

    assertEquals("u1", model.getUserIdentity());
    assertSame(claims, model.getClaims());
    assertSame(claims, model.getClaims());
    assertSame(subject, model.getSubject());
    assertTrue(model.isEnabled());
    assertNotNull(model.getCreatedAt());
  }

  @Test
  void userSubjectModelShouldExposeWrappedEntityFieldsAndFactory() {
    var entity = new UserSubjectEntity();
    entity.setUserIdentity("u1");
    entity.setSubjectType("EMP");
    entity.setSubjectId("e1");
    entity.setCreatedBy("creator");
    entity.setCreatedAt(LocalDateTime.now());

    var model = UserSubjectModel.from(entity);

    assertEquals("u1", model.getUserIdentity());
    assertEquals("EMP", model.getSubjectType());
    assertEquals("e1", model.getSubjectId());
    assertEquals("creator", model.getCreatedBy());
    assertNotNull(model.getCreatedAt());
  }

  @Test
  void administratorRoleModelShouldExposeAuthorities() {
    var model = new AdministratorRoleModel();
    assertEquals("administrator", model.getId());
    assertEquals("超级管理员", model.getName());
    assertNotNull(model.getAuthorities());
  }
}
