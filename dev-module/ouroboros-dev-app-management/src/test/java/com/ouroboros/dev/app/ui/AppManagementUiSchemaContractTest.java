package com.ouroboros.dev.app.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class AppManagementUiSchemaContractTest {

  @Test
  void shouldExposeRuntimeFirstContainerManagementPage() throws IOException {
    String containerManagement = resourceAsString("META-INF/ouroboros/metadata/container-management.ui-schema.json");
    String containerDetail = resourceAsString("META-INF/ouroboros/metadata/dev/container/detail.ui-schema.json");
    String createForm = resourceAsString("META-INF/ouroboros/metadata/dev/container/create.ui-schema.json");
    String containerButtons = resourceAsString("META-INF/ouroboros/metadata/dev/container-control-buttons.ui-schema.json");
    String helperButtons = resourceAsString("META-INF/ouroboros/metadata/dev/helper-control-buttons.ui-schema.json");

    assertTrue(containerManagement.contains("运行实例"));
    assertTrue(containerManagement.contains("runtimeAccess"));
    assertTrue(containerManagement.contains("backendSummary"));
    assertTrue(containerDetail.contains("backendDetails"));
    assertTrue(containerManagement.contains("imageState"));
    assertTrue(createForm.contains("imageInputMode"));
    assertTrue(createForm.contains("\"name\": \"backendType\""));
    assertTrue(createForm.contains("\"type\": \"static\""));
    assertTrue(createForm.contains("availableBackends[0].label"));
    assertTrue(createForm.contains("\"name\": \"exposureStrategy\""));
    assertTrue(createForm.contains("availableExposureStrategies[0].label"));
    assertTrue(createForm.contains("\"componentName\": \"containerControlButtons\""));
    assertTrue(createForm.contains("backendType=${backendType}"));
    assertTrue(helperButtons.contains("/api/dev/${appName}/helper/create-init"));
    assertTrue(helperButtons.contains("imageInputMode"));
    assertTrue(helperButtons.contains("\"name\": \"backendType\""));
    assertTrue(helperButtons.contains("\"value\": \"${defaultBackendType}\""));
    assertTrue(helperButtons.contains("\"componentName\": \"helperControlButtons\""));
    assertTrue(helperButtons.contains("\"visibleOn\": \"${imageState && imageState.updateRecommended}\""));
    assertTrue(containerButtons.contains("imageState"));
    assertTrue(helperButtons.contains("imageState"));
    assertTrue(containerButtons.contains("停止并删除容器"));
    assertTrue(helperButtons.contains("停止并删除"));
    assertTrue(helperButtons.contains("\"initDestination\": \"/app/user/queue/app/${appName}/helper/logs/init\""));
    assertFalse(createForm.contains("\"label\": \"Docker\""));
    assertFalse(helperButtons.contains("\"label\": \"Docker\""));
    assertFalse(containerButtons.contains("!imageVersion"));
    assertFalse(helperButtons.contains("!imageVersion"));
    assertFalse(helperButtons.contains("subscribeDestination"));
  }

  private String resourceAsString(String path) throws IOException {
    InputStream stream = Thread.currentThread().getContextClassLoader().getResourceAsStream(path);
    if (stream == null) {
      throw new IOException("resource not found: " + path);
    }
    try (InputStream input = stream) {
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      byte[] buffer = new byte[1024];
      int read;
      while ((read = input.read(buffer)) != -1) {
        output.write(buffer, 0, read);
      }
      return new String(output.toByteArray(), StandardCharsets.UTF_8);
    }
  }
}
