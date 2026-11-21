package com.ouroboros.security.rbac;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.ouroboros.security.rbac.service.UserService;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PasswordImplTest {

  @Test
  void generatorShouldReturnNonEmptyBase64LikePassword() {
    var password = new PasswordImpl();
    var generated = password.generatorDefaultPassword();
    assertNotNull(generated);
    assertTrue(generated.length() > 0);
  }

  @Test
  void verifyByUserIdentityShouldMatchEncodedPasswordAndHandleMissingUser() {
    var userService = mock(UserService.class);
    var encoder = mock(PasswordEncoder.class);

    when(userService.getPasswordByUserIdentity("u1")).thenReturn(Optional.of("encoded"));
    when(userService.getPasswordByUserIdentity("u2")).thenReturn(Optional.empty());
    when(encoder.matches("raw", "encoded")).thenReturn(true);

    var password = new PasswordImpl();
    ReflectionTestUtils.setField(password, "userService", userService);
    ReflectionTestUtils.setField(password, "passwordEncoder", encoder);

    assertTrue(password.verify("u1", "raw"));
    assertFalse(password.verify("u2", "raw"));
  }

  @Test
  void verifyByPrincipalShouldResolveUserIdThenDelegate() {
    var userService = mock(UserService.class);
    var encoder = mock(PasswordEncoder.class);
    var resolver = mock(UserIdResolver.class);

    when(resolver.getUserId("email", "a@b.com")).thenReturn(Optional.of("u1"));
    when(resolver.getUserId("email", "missing@b.com")).thenReturn(Optional.empty());
    when(userService.getPasswordByUserIdentity("u1")).thenReturn(Optional.of("encoded"));
    when(encoder.matches("raw", "encoded")).thenReturn(true);

    var password = new PasswordImpl();
    ReflectionTestUtils.setField(password, "userService", userService);
    ReflectionTestUtils.setField(password, "passwordEncoder", encoder);
    ReflectionTestUtils.setField(password, "userIdResolver", resolver);

    assertTrue(password.verify("a@b.com", "raw", "email"));
    assertFalse(password.verify("missing@b.com", "raw", "email"));
  }
}
