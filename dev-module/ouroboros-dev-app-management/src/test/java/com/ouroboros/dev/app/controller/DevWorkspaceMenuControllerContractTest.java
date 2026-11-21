package com.ouroboros.dev.app.controller;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PathVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DevWorkspaceMenuControllerContractTest {

  @Test
  void appNamePathVariableIsExplicitlyBound() throws Exception {
    Method method = DevWorkspaceMenuController.class.getMethod("getDevMenuList", String.class);
    Parameter parameter = method.getParameters()[0];
    PathVariable pathVariable = parameter.getAnnotation(PathVariable.class);

    assertNotNull(pathVariable);
    assertEquals("appName", pathVariable.value());
  }
}
