package com.ouroboros.fieldtype.runtime;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.ouroboros.AppContext;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.fieldtype.FieldTypeCatalogContext;

import static org.junit.jupiter.api.Assertions.*;

class FieldTypeRuntimeAutoConfigurationContractTest {

  @TempDir
  java.nio.file.Path tempDir;

  @Test
  void springFactoriesRegistersFieldTypeRuntimeAutoConfiguration() {
    String springFactories = readResource("META-INF/spring.factories");

    assertTrue(springFactories.contains("org.springframework.boot.autoconfigure.EnableAutoConfiguration=com.ouroboros.fieldtype.runtime.FieldTypeRuntimeAutoConfiguration"));
  }

  @Test
  void autoConfigurationCreatesCatalogWithBundledDefinitions() throws Exception {
    Files.createDirectories(tempDir.resolve("field-types"));

    AnnotationConfigApplicationContext context = newContext();
    try {
      FieldTypeCatalog catalog = context.getBean(FieldTypeCatalog.class);
      assertNotNull(catalog);

      var definition = catalog.getMetadata(FieldTypeCatalogContext.runtime(), "单行文本");
      assertTrue(definition.isPresent());
      assertEquals("单行文本", definition.get().getLabel());
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationLoadsTypedRuntimeDirectory() throws Exception {
    Path fieldTypesDir = tempDir.resolve("field-types");
    Files.createDirectories(fieldTypesDir);
    Files.write(
        fieldTypesDir.resolve("RuntimeField.field-type.json"),
        java.util.Collections.singletonList("{\"name\":\"runtimeField\",\"label\":\"Runtime Field\",\"paramsUiSchema\":[{\"name\":\"size\",\"value\":18}]}")
    );

    AnnotationConfigApplicationContext context = newContext();
    try {
      FieldTypeCatalog catalog = context.getBean(FieldTypeCatalog.class);
      var definition = catalog.getMetadata(FieldTypeCatalogContext.runtime(), "runtimeField");
      assertTrue(definition.isPresent());
      assertEquals(18, definition.get().getDefaultParams().get("size"));
    } finally {
      context.close();
    }
  }

  @Test
  void autoConfigurationIncludesSpiProviders() {
    AnnotationConfigApplicationContext context = newContext();
    try {
      FieldTypeCatalog catalog = context.getBean(FieldTypeCatalog.class);
      assertTrue(catalog.getMetadata(FieldTypeCatalogContext.runtime(), "spiProvided").isPresent());
    } finally {
      context.close();
    }
  }

  private AnnotationConfigApplicationContext newContext() {
    AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    context.getBeanFactory().registerSingleton("appContext", new AppContext() {
      @Override
      public String getHomePath() {
        return tempDir.toString();
      }
    });
    context.register(FieldTypeRuntimeAutoConfiguration.class);
    context.refresh();
    return context;
  }

  private String readResource(String resourcePath) {
    ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    assertNotNull(classLoader, "classLoader should exist");

    try (InputStream inputStream = classLoader.getResourceAsStream(resourcePath)) {
      assertNotNull(inputStream, "resource should exist: " + resourcePath);
      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = inputStream.read(buffer)) != -1) {
        outputStream.write(buffer, 0, read);
      }
      return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read resource: " + resourcePath, e);
    }
  }
}
