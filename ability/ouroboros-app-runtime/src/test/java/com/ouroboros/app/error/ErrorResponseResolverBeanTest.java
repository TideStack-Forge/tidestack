package com.ouroboros.app.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorResponseResolverBeanTest {

  @Test
  void errorResponseResolver_canBeCreatedAsSpringComponent() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(RequestLocaleResolver.class, ErrorResponseResolver.class);
      context.refresh();

      assertNotNull(context.getBean(ErrorResponseResolver.class));
    }
  }
}
