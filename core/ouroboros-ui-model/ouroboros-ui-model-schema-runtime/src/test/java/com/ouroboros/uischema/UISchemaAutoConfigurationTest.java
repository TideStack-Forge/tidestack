package com.ouroboros.uischema;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ComponentScan;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UISchemaAutoConfigurationTest {

  @Test
  void shouldExposeExpectedComponentScanAndInitCleanly() {
    ComponentScan componentScan = UISchemaAutoConfiguration.class.getAnnotation(ComponentScan.class);
    assertNotNull(componentScan);
    assertArrayEquals(new String[]{"com.ouroboros.uischema.**"}, componentScan.value());

    UISchemaAutoConfiguration configuration = new UISchemaAutoConfiguration();
    assertDoesNotThrow(configuration::init);
  }
}
