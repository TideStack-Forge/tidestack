package com.ouroboros.app.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlatformCacheTokenPropertiesTest {

  @Test
  void requireSecret_returnsConfiguredSecret() {
    var properties = new PlatformCacheTokenProperties();
    properties.setSecret("platform-cache-secret");

    assertEquals("platform-cache-secret", properties.requireSecret());
  }

  @Test
  void requireSecret_failsWhenSecretMissing() {
    var properties = new PlatformCacheTokenProperties();

    assertThrows(IllegalStateException.class, properties::requireSecret);
  }
}
