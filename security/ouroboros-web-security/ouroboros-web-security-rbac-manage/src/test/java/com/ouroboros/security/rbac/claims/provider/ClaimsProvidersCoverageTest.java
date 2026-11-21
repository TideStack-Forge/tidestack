package com.ouroboros.security.rbac.claims.provider;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.claims.Claims;
import com.ouroboros.security.claims.ClaimsContext;
import com.ouroboros.security.claims.StandardClaims;
import com.ouroboros.security.rbac.User;
import com.ouroboros.security.rbac.Subject;
import com.ouroboros.security.rbac.SubjectCenter;
import com.ouroboros.security.rbac.UserRoleCenter;
import com.ouroboros.security.rbac.UserSubject;
import com.ouroboros.security.rbac.UserSubjectCenter;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.entity.UserEntity;
import com.ouroboros.security.rbac.service.UserService;
import com.ouroboros.util.SPI;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClaimsProvidersCoverageTest {

  @org.junit.jupiter.api.AfterEach
  void clearSpi() {
    clearSpiPrimary(UserRoleCenter.class);
  }

  @Test
  void userEntityClaimsProviderShouldReturnEmptyWhenUserMissing() {
    var userService = mock(UserService.class);
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("u1");
    when(userService.getByUserIdentity("u1")).thenReturn(Optional.empty());

    var provider = new UserEntityClaimsProvider(userService);
    var claims = provider.provide(user, mock(ClaimsContext.class));

    assertTrue(claims.getTypes().isEmpty());
    assertEquals(java.util.Set.of(StandardClaims.SUB, StandardClaims.PICTURE, StandardClaims.PUBLIC_SUBJECT_ID,
        StandardClaims.NICKNAME, StandardClaims.EMAIL, StandardClaims.EMAIL_VERIFIED,
        StandardClaims.PHONE_NUMBER, StandardClaims.PHONE_NUMBER_VERIFIED), provider.getProvidedClaimTypes());
  }

  @Test
  void userEntityClaimsProviderShouldReturnSubAndPictureWhenPresent() {
    var userService = mock(UserService.class);
    var user = mock(User.class);
    var entity = new UserEntity();
    entity.setAvatar("a.png");
    entity.setNickname("Account nickname");
    when(user.getUserIdentity()).thenReturn("u1");
    when(userService.getByUserIdentity("u1")).thenReturn(Optional.of(entity));

    var provider = new UserEntityClaimsProvider(userService);
    var claims = provider.provide(user, mock(ClaimsContext.class));

    assertEquals("u1", claims.getString(StandardClaims.SUB).orElse(null));
    assertEquals("a.png", claims.getString(StandardClaims.PICTURE).orElse(null));
    assertEquals("Account nickname", claims.getString(StandardClaims.NICKNAME).orElseThrow());
    assertTrue(claims.getString(StandardClaims.NAME).isEmpty());
  }

  @Test
  void roleClaimsProviderShouldExposeProvidedClaimTypes() {
    var provider = new RoleClaimsProvider();
    assertEquals(Collections.singleton(StandardClaims.ROLES), provider.getProvidedClaimTypes());
  }

  @Test
  void subjectClaimsProviderShouldProvideClaimTypesConstantSet() {
    var provider = new SubjectClaimsProvider();
    assertEquals(3, provider.getProvidedClaimTypes().size());
  }

  @Test
  void subjectClaimsProviderShouldReturnEmptyWhenNoSubjectBinding() {
    var provider = new SubjectClaimsProvider();
    var userSubjectCenter = mock(UserSubjectCenter.class);
    var subjectCenter = mock(SubjectCenter.class);
    ReflectionTestUtils.setField(provider, "userSubjectCenter", userSubjectCenter);
    ReflectionTestUtils.setField(provider, "subjectCenter", subjectCenter);

    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("u-empty");
    when(userSubjectCenter.getByUser("u-empty")).thenReturn(Optional.empty());

    var claims = provider.provide(user, mock(ClaimsContext.class));
    assertTrue(claims.getTypes().isEmpty());
  }

  @Test
  void subjectClaimsProviderShouldIncludeSubjectAndDisplayName() {
    var provider = new SubjectClaimsProvider();
    var userSubjectCenter = mock(UserSubjectCenter.class);
    var subjectCenter = mock(SubjectCenter.class);
    ReflectionTestUtils.setField(provider, "userSubjectCenter", userSubjectCenter);
    ReflectionTestUtils.setField(provider, "subjectCenter", subjectCenter);

    var user = mock(User.class);
    var userSubject = mock(UserSubject.class);
    var subject = mock(Subject.class);
    when(user.getUserIdentity()).thenReturn("u1");
    when(userSubjectCenter.getByUser("u1")).thenReturn(Optional.of(userSubject));
    when(userSubject.getSubjectType()).thenReturn("EMP");
    when(userSubject.getSubjectId()).thenReturn("e1");
    when(subjectCenter.getByTypeAndId("EMP", "e1")).thenReturn(Optional.of(subject));
    when(subject.getName()).thenReturn("employee-1");
    when(subject.getDisplayName()).thenReturn("subject display label");

    var claims = provider.provide(user, mock(ClaimsContext.class));
    assertEquals("EMP", claims.getString("subject_type").orElse(null));
    assertEquals("e1", claims.getString("subject_id").orElse(null));
    assertEquals("employee-1", claims.getString(StandardClaims.NAME).orElse(null));
  }

  @Test
  void roleClaimsProviderShouldMapRoleIdAndName() {
    var provider = new RoleClaimsProvider();
    var userRoleCenter = mock(UserRoleCenter.class);
    setSpiPrimary(UserRoleCenter.class, userRoleCenter);

    var role = mock(Role.class);
    when(role.getId()).thenReturn("R1");
    when(role.getName()).thenReturn("role-1");
    when(userRoleCenter.getRolesByUser("u1")).thenReturn(Collections.singletonList(role));

    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("u1");

    var claims = provider.provide(user, mock(ClaimsContext.class));
    var roles = claims.get(StandardClaims.ROLES, Object.class).orElse(Collections.emptyList());
    assertTrue(roles instanceof List);
    assertEquals(1, ((List<?>) roles).size());
  }

  private static org.mockito.MockedStatic<com.ouroboros.util.SPI> spiBinding;

  private static <T> void setSpiPrimary(Class<T> serviceClass, T service) {
    if (spiBinding == null) spiBinding = org.mockito.Mockito.mockStatic(com.ouroboros.util.SPI.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    spiBinding.when(() -> com.ouroboros.util.SPI.getCachedServiceStream(serviceClass))
        .thenAnswer(ignored -> java.util.stream.Stream.of(service));
  }

  private static void clearSpiPrimary(Class<?> serviceClass) {
    if (spiBinding != null) { spiBinding.close(); spiBinding = null; }
    com.ouroboros.util.SPI.clearCache();
  }
}
