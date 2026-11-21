package com.ouroboros.app.controller;

import com.ouroboros.AppContext;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

class FrontendResourceViewControllerConditionalTest {

  @Test
  void springContext_skipsFrontendResourceViewControllerWhenResourceServiceIsMissing() {
    AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
    applicationContext.registerBean(AppContext.class, () -> () -> ".");
    applicationContext.register(FrontendResourceViewController.class);

    try {
      assertDoesNotThrow(applicationContext::refresh);
      assertFalse(applicationContext.containsBeanDefinition("frontendResourceViewController"));
    } finally {
      applicationContext.close();
    }
  }
}
