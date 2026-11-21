package com.ouroboros.fieldtype.dev.controller;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.dev.controller.DevFieldTypeController;
import com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration;

class DevFieldTypeControllerWiringTest {

  @TempDir
  Path tempDir;

  @Test
  void contextRefreshesWhenDevControllerUsesFieldTypeCatalog() throws Exception {
    Files.createDirectories(tempDir.resolve("field-types"));

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.getBeanFactory().registerSingleton("appContext", new AppContext() {
        @Override
        public String getHomePath() {
          return tempDir.toString();
        }
      });
      context.register(FieldTypeRuntimeAutoConfiguration.class, DevFieldTypeController.class);
      context.refresh();

      assertNotNull(context.getBean(FieldTypeCatalog.class));
      assertNotNull(context.getBean(DevFieldTypeController.class));
    } finally {
      context.close();
    }
  }
}
