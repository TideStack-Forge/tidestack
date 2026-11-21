package com.ouroboros.businesslog.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.ouroboros.AppContext;

class FileBusinessOperationTemplateProviderTest {

  @TempDir
  Path tempDir;

  @Test
  void shouldLoadBusinessLogTemplatesFromAppHome() throws Exception {
    Path templateDir = Files.createDirectories(tempDir.resolve("business-log-template"));
    copyFixture("business-log-template/create-order.business-log.json", templateDir.resolve("create-order.business-log.json"));
    copyFixture("business-log-template/update-order.business-log.json", templateDir.resolve("nested").resolve("update-order.business-log.json"));

    AppContext appContext = () -> tempDir.toString();
    FileBusinessOperationTemplateProvider provider = new FileBusinessOperationTemplateProvider(appContext);

    var operations = provider.getAllTemplates().stream()
        .map(template -> template.getHttpMethod() + " " + template.getUrl() + " => " + template.getOperation())
        .sorted()
        .collect(Collectors.toList());

    assertEquals(2, operations.size());
    assertEquals("POST /orders => 创建订单", operations.get(0));
    assertEquals("PUT /orders/{id} => 更新订单", operations.get(1));
    assertTrue(provider.getAllTemplates().stream().anyMatch(template -> Boolean.TRUE.equals(template.isShouldSaveResponseBody())));
  }

  private void copyFixture(String classpathResource, Path target) throws Exception {
    Files.createDirectories(target.getParent());
    try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(classpathResource)) {
      if (inputStream == null) {
        throw new IllegalStateException("Missing fixture: " + classpathResource);
      }
      Files.copy(inputStream, target);
    }
  }
}
