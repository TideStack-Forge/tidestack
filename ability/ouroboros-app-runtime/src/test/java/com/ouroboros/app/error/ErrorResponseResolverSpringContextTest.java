package com.ouroboros.app.error;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ErrorResponseResolverSpringContextTest {

  @Test
  void springContext_canCreateErrorResponseResolverComponent() {
    AnnotationConfigApplicationContext applicationContext = new AnnotationConfigApplicationContext();
    try {
      applicationContext.register(RequestLocaleResolver.class, ErrorResponseResolver.class);
      applicationContext.refresh();

      assertNotNull(applicationContext.getBean(ErrorResponseResolver.class));
    } finally {
      applicationContext.close();
    }
  }
}
