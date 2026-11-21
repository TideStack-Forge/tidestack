package com.ouroboros.uimodel.dev.controller;

import java.util.Collections;

import org.junit.jupiter.api.Test;

import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UIDevControllerSchemaFallbackTest {

  @Test
  void shouldFallbackToUiSchemaProviderWhenMetadataMisses() {
    UIDevController controller = new UIDevController(null, Collections.emptyList());

    WebApiResult<?> result = controller.getUiSchema("App1", "/dev/{appName}/ui-schema/preview");

    assertEquals(0, result.getCode());
    assertNotNull(result.getData());
  }
}
