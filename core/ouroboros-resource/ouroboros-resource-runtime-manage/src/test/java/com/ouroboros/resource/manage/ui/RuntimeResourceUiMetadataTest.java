package com.ouroboros.resource.manage.ui;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class RuntimeResourceUiMetadataTest {

  @Test
  void systemSettingMetadataUsesRuntimeResourceNaming() {
    String springFactories = readResource("META-INF/spring.factories");
    String appModules = readResource("META-INF/ouroboros/metadata/app-modules.json");
    String menu = readResource("META-INF/ouroboros/metadata/systemSetting/resourceManagement.menu.json");
    String authority = readResource("META-INF/ouroboros/metadata/systemSetting/resourceManagement.authority.json");
    String pageModel = readResource("META-INF/ouroboros/metadata/system-setting/resource-management.ui-model.json");
    String detailModel = readResource("META-INF/ouroboros/metadata/system-setting/resource-management/detail.ui-model.json");
    String uploadModel = readResource("META-INF/ouroboros/metadata/system-setting/resource-management/upload.ui-model.json");
    String pageSchema = readResource("META-INF/ouroboros/metadata/system-setting/resource-management.ui-schema.json");
    String uploadSchema = readResource("META-INF/ouroboros/metadata/system-setting/resource-management/upload.ui-schema.json");

    assertTrue(springFactories.contains("ResourceRuntimeManageAutoConfiguration"));
    assertTrue(appModules.contains("systemSetting.resourceManagement"));
    assertTrue(menu.contains("/system-setting/resource-management"));
    assertTrue(authority.contains("GET /api/sys/resource/runtime/directories"));
    assertTrue(authority.contains("GET /api/sys/resource/runtime/content"));
    assertTrue(authority.contains("GET /api/sys/resource/runtime"));
    assertTrue(authority.contains("POST /api/sys/resource/runtime/restore"));
    assertTrue(pageModel.contains("\"path\": \"/system-setting/resource-management\""));
    assertTrue(detailModel.contains("/system-setting/resource-management/detail"));
    assertTrue(uploadModel.contains("/system-setting/resource-management/upload"));
    assertTrue(pageSchema.contains("\"type\": \"button-group-select\""));
    assertTrue(pageSchema.contains("\"name\": \"scope\""));
    assertTrue(pageSchema.contains("\"value\": \"frontend\""));
    assertTrue(pageSchema.contains("\"aside\""));
    assertTrue(pageSchema.contains("\"name\": \"resourceDirectoryTree\""));
    assertTrue(pageSchema.contains("\"name\": \"resourceList\""));
    assertTrue(pageSchema.contains("\"componentName\": \"resourceList\""));
    assertTrue(pageSchema.contains("/api/sys/resource/runtime/directories?scope=${scope}"));
    assertTrue(pageSchema.contains("/api/sys/resource/runtime"));
    assertTrue(pageSchema.contains("/api/sys/resource/runtime/content"));
    assertTrue(pageSchema.contains("\"name\": \"keyword\""));
    assertTrue(pageSchema.contains("\"label\": \"新建覆盖资源\""));
    assertTrue(pageSchema.contains("\"url\": \"/system-setting/resource-management/upload\""));
    assertTrue(uploadSchema.contains("\"type\": \"select\""));
    assertTrue(uploadSchema.contains("\"name\": \"scope\""));
    assertTrue(uploadSchema.contains("\"type\": \"input-text\""));
    assertTrue(uploadSchema.contains("\"name\": \"key\""));
    assertTrue(readResource("META-INF/ouroboros/metadata/system-setting/resource-management/detail.ui-schema.json")
        .contains("/api/sys/resource/runtime/content?scope=${scope}&key=${key}"));
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
