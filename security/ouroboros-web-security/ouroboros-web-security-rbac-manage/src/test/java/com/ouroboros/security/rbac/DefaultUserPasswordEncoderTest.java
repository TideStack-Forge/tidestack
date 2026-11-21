package com.ouroboros.security.rbac;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultUserPasswordEncoderTest {

  @Test
  void shouldEncodeAndMatchPasswords() {
    var encoder = new DefaultUserPasswordEncoder();

    var encoded = encoder.encode("secret");
    assertNotNull(encoded);
    assertTrue(encoder.matches("secret", encoded));
    assertFalse(encoder.matches("other", encoded));
  }
}
