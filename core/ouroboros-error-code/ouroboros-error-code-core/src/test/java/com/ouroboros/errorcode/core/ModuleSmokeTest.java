package com.ouroboros.errorcode.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.errorcode.model.ErrorCodeDefinition;
import com.ouroboros.errorcode.provider.ErrorCodeDefinitionProvider;

class ModuleSmokeTest {

  @Test
  void errorCodeDefinitionShouldExposeConfiguredFieldsAndEnabledState() {
    var definition = new ErrorCodeDefinition();

    assertSame(definition, definition
        .setCode(10001)
        .setTitle("Validation failed")
        .setDefaultMessageTemplate("Field {0} is required")
        .setBizCode("VALIDATION_REQUIRED")
        .setCategory("validation")
        .setDescription("Raised when a required field is missing")
        .setEnabled(false));

    assertEquals(10001, definition.getCode());
    assertEquals("Validation failed", definition.getTitle());
    assertEquals("Field {0} is required", definition.getDefaultMessageTemplate());
    assertEquals("VALIDATION_REQUIRED", definition.getBizCode());
    assertEquals("validation", definition.getCategory());
    assertEquals("Raised when a required field is missing", definition.getDescription());
    assertEquals(Boolean.FALSE, definition.getEnabled());
    assertFalse(definition.isEnabled());

    definition.setEnabled(null);

    assertTrue(definition.isEnabled());
  }

  @Test
  void providerDefaultOrderShouldBeZero() {
    var provider = new ErrorCodeDefinitionProvider() {
      @Override
      public java.util.List<ErrorCodeDefinition> getDefinitions() {
        return Collections.emptyList();
      }
    };

    assertEquals(0, provider.getOrder());
  }
}
