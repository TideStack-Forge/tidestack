package com.ouroboros.resource.dev.ui;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceUiMetadataTest {

  @Test
  void devMenuUiModelAndUiSchemaUseFinalResourceNaming() {
    String devMenu = readResource("META-INF/ouroboros/metadata/AllModels.Resource.dev-menu.json");
    String listPageModel = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource.ui-model.json");
    String detailPageModel = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource/{fullName}.ui-model.json");
    String contentUploadModel = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource/content-upload.ui-model.json");
    String listPageSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource.ui-schema.json");
    String detailPageSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource/{fullName}.ui-schema.json");
    String contentUploadSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/resource/content-upload.ui-schema.json");

    assertTrue(devMenu.contains("\"url\": \"/dev/{appName}/resource\""));
    assertTrue(devMenu.contains("\"title\": \"资源管理\""));

    assertLightweightUiModel(listPageModel, "/dev/{appName}/resource", "资源管理");
    assertLightweightUiModel(detailPageModel, "/dev/{appName}/resource/{fullName}", "资源详情");
    assertLightweightUiModel(contentUploadModel, "/dev/{appName}/resource/content-upload", "上传资源内容");

    assertFalse(listPageSchema.contains("\"path\""));
    assertTrue(listPageSchema.contains("\"type\": \"button-group-select\""));
    assertTrue(listPageSchema.contains("\"name\": \"scope\""));
    assertTrue(listPageSchema.contains("\"value\": \"frontend\""));
    assertTrue(listPageSchema.contains("\"aside\""));
    assertTrue(listPageSchema.contains("\"name\": \"resourceDirectoryTree\""));
    assertTrue(listPageSchema.contains("\"name\": \"resourceList\""));
    assertTrue(listPageSchema.contains("\"componentName\": \"resourceList\""));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/directories"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/content"));
    assertTrue(listPageSchema.contains("\"name\": \"keyword\""));
    assertTrue(listPageSchema.contains("\"label\": \"新建目录\""));
    assertTrue(listPageSchema.contains("\"url\": \"/api/dev/${appName}/resource/directories\""));
    assertTrue(listPageSchema.contains("\"label\": \"详情\""));
    assertTrue(listPageSchema.contains("\"title\": \"资源详情\""));
    assertTrue(listPageSchema.contains("\"title\": \"基础信息\""));
    assertTrue(listPageSchema.contains("\"content\": \"${model.directory || '<根目录>'}\""));
    assertTrue(listPageSchema.contains("\"label\": \"扩展元数据\""));
    assertTrue(listPageSchema.contains("\"source\": \"${model.metadata}\""));
    assertTrue(listPageSchema.contains("\"name\": \"lockState\""));
    assertTrue(listPageSchema.contains("\"name\": \"versionState\""));
    assertTrue(listPageSchema.contains("\"name\": \"lockOwner\""));
    assertTrue(listPageSchema.contains("\"label\": \"提交\""));
    assertTrue(listPageSchema.contains("\"label\": \"回滚\""));
    assertTrue(listPageSchema.contains("\"label\": \"锁定\""));
    assertTrue(listPageSchema.contains("\"label\": \"释放锁\""));
    assertTrue(listPageSchema.contains("\"title\": \"变更记录\""));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/status"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/commit"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/rollback"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/lock"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/release-lock"));
    assertTrue(listPageSchema.contains("/api/dev/${appName}/resource/${model.scope}/${model.key}/change-log"));

    assertFalse(detailPageSchema.contains("\"path\""));
    assertTrue(detailPageSchema.contains("/api/dev/${appName}/resource/${fullName}/status"));
    assertTrue(detailPageSchema.contains("/api/dev/${appName}/resource/content"));

    assertFalse(contentUploadSchema.contains("\"path\""));
    assertTrue(contentUploadSchema.contains("/api/dev/${appName}/resource/content"));
  }

  private void assertLightweightUiModel(String content, String path, String title) {
    Map<String, Object> model = Json.toMap(content);
    assertEquals(path, model.get("path"));
    assertEquals("amis", model.get("type"));
    assertEquals(title, model.get("title"));

    Set<String> schemaOnlyKeys = Set.of("api", "aside", "body", "columns", "dialog", "drawer", "filter", "toolbar");
    schemaOnlyKeys.forEach(key -> assertFalse(model.containsKey(key), "ui-model should not contain ui-schema key: " + key));
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
