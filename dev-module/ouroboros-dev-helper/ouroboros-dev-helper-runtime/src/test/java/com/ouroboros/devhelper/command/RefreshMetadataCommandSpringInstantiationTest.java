package com.ouroboros.devhelper.command;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class RefreshMetadataCommandSpringInstantiationTest {

  @Test
  void springShouldInstantiateRefreshMetadataCommandThroughProductionConstructor() {
    try (var context = new AnnotationConfigApplicationContext()) {
      context.register(RefreshMetadataCommand.class, com.ouroboros.devhelper.metadata.HelperMetadataSnapshots.class);

      context.refresh();

      assertNotNull(context.getBean(RefreshMetadataCommand.class));
    }
  }
}
