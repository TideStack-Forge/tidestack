package com.ouroboros.uischema.controller;

import java.util.Collections;

import com.ouroboros.web.WebApiResult;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UISchemaControllerRuntimeTest {

  @Test
  void shouldNormalizePathAndReturnSchemaFromSpi() {
    UISchemaController controller = new UISchemaController();

    WebApiResult<Object> result = controller.getUISchema("runtime/page");

    assertEquals(0, result.getCode());
    assertEquals("schema:/runtime/page", result.getData());
  }

  @Test
  void shouldDelegateQueryParameterEndpointToPathEndpoint() {
    UISchemaController controller = new UISchemaController();

    WebApiResult<Object> result = controller.getUISchema2("runtime/page", Collections.emptyMap());

    assertNotNull(result);
    assertEquals(0, result.getCode());
    assertEquals("schema:/runtime/page", result.getData());
  }

  @Test
  void shouldReturnNotFoundWhenSpiDoesNotProvideSchema() {
    UISchemaController controller = new UISchemaController();

    WebApiResult<Object> result = controller.getUISchema("missing/page");

    assertEquals(-1, result.getCode());
    assertEquals("未找到指定数据", result.getMsg());
  }
}
