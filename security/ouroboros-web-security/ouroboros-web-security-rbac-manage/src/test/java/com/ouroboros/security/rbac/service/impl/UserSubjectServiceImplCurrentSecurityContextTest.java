package com.ouroboros.security.rbac.service.impl;

import java.lang.reflect.Method;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.entity.UserSubjectEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class UserSubjectServiceImplCurrentSecurityContextTest {

  @AfterEach
  void clearSecurityContext() {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.clearContext();
  }

  @Test
  void createShouldUseSeamIdentityAndForwardEntityFields() throws Exception {
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(() -> "seam-user")));

    CapturingUserSubjectServiceImpl service = new CapturingUserSubjectServiceImpl();
    UserSubjectEntity created = service.create("target-user", "ORG", "org-1");

    assertSame(service.getCapturedEntity(), created);
    assertEquals("seam-user", created.getCreatedBy());
    assertNotNull(created.getCreatedAt());
    assertEquals("target-user", created.getUserIdentity());
    assertEquals("ORG", created.getSubjectType());
    assertEquals("org-1", created.getSubjectId());
  }

  @Test
  void createShouldIgnoreLegacyPrincipalWhenSeamIdentityMissing() throws Exception {
    CurrentSecurityContextHolder.clearDelegate();
    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("legacy-principal", "secret"));

    CapturingUserSubjectServiceImpl service = new CapturingUserSubjectServiceImpl();
    UserSubjectEntity created = service.create("target-user", "ORG", "org-1");

    assertSame(service.getCapturedEntity(), created);
    assertNull(created.getCreatedBy());
    assertNotEquals("legacy-principal", created.getCreatedBy());
    assertNotEquals("anonymousUser", created.getCreatedBy());
    assertNotNull(created.getCreatedAt());
    assertEquals("target-user", created.getUserIdentity());
    assertEquals("ORG", created.getSubjectType());
    assertEquals("org-1", created.getSubjectId());
  }

  @Test
  void cacheKeysShouldNotDependOnJavaParameterNameMetadata() throws Exception {
    assertEquals("#p0", cacheKey("findByUser", String.class));
    assertEquals("#p0 + ':' + #p1", cacheKey("findByUserAndSubjectType", String.class, String.class));
    assertEquals("'all:' + #p0", cacheKey("findAllByUser", String.class));
    assertEquals("#p0 + ':' + #p1", cacheKey("findBySubject", String.class, String.class));
    assertEquals("'all'", cacheKey("findAll"));
  }

  private String cacheKey(String methodName, Class<?>... parameterTypes) throws Exception {
    Method method = UserSubjectServiceImpl.class.getDeclaredMethod(methodName, parameterTypes);
    return method.getAnnotation(Cacheable.class).key();
  }

  private static final class CapturingUserSubjectServiceImpl extends UserSubjectServiceImpl {

    private UserSubjectEntity capturedEntity;

    @Override
    public UserSubjectEntity create(UserSubjectEntity entity) {
      this.capturedEntity = entity;
      return entity;
    }

    private UserSubjectEntity getCapturedEntity() {
      return capturedEntity;
    }
  }
}
