package com.ouroboros.configuration.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class ConfigurationUpdateEventTest {

  @Test
  public void constructor_exposesKeyAndValue() {
    ConfigurationUpdateEvent<String> event = new ConfigurationUpdateEvent<String>("security.session", "enabled");

    assertEquals("security.session", event.getKey());
    assertSame("enabled", event.getValue());
  }
}
