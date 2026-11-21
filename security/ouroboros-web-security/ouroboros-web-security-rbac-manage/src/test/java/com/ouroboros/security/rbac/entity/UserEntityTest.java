package com.ouroboros.security.rbac.entity;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class UserEntityTest {

  @Test
  void shouldTreatNullEnabledAsTrueForLegacyUsers() {
    UserEntity entity = new UserEntity();

    assertDoesNotThrow(() -> ReflectionTestUtils.setField(entity, "isEnabled", null));
    assertTrue(entity.isEnabled());
  }

  @Test
  void shouldKeepExplicitDisabledState() {
    UserEntity entity = new UserEntity();

    entity.setEnabled(Boolean.FALSE);

    assertFalse(entity.isEnabled());
  }
}
