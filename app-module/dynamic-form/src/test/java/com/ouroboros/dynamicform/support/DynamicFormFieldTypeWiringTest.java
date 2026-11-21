package com.ouroboros.dynamicform.support;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.dynamicform.schema.DynamicFormFieldSchemaBuilder;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration;

class DynamicFormFieldTypeWiringTest {

  @TempDir
  Path tempDir;

  @Test
  void contextRefreshesWhenDynamicFormUsesFieldTypeCatalog() throws Exception {
    Files.createDirectories(tempDir.resolve("field-types"));

    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    try {
      context.getBeanFactory().registerSingleton("appContext", new AppContext() {
        @Override
        public String getHomePath() {
          return tempDir.toString();
        }
      });
      context.register(FieldTypeRuntimeAutoConfiguration.class, DynamicFormFieldSchemaBuilder.class);
      context.refresh();

      assertNotNull(context.getBean(FieldTypeCatalog.class));
      assertNotNull(context.getBean(DynamicFormFieldSchemaBuilder.class));
    } finally {
      context.close();
    }
  }
}
