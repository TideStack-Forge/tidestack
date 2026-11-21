package com.ouroboros.configuration.metadata;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ConfigScopeTest {

  @Test
  public void getValue_and_forValue_supportKnownAndUnknownScopes() {
    assertEquals("frontend", ConfigScope.FRONTEND.getValue());
    assertEquals("backend", ConfigScope.BACKEND.getValue());
    assertSame(ConfigScope.FRONTEND, ConfigScope.BACKEND.forValue("FRONTEND"));
    assertSame(ConfigScope.BACKEND, ConfigScope.FRONTEND.forValue("unknown"));
  }
}
