package com.ouroboros.dev.app.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class BuiltinOrgUiModelContractTest {

  @Test
  void builtinPositionAndRoleRoutesShouldUseUiModelPathPlaceholders() throws IOException {
    String builtinPositionAdd = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-position/add.ui-model.json");
    String builtinPositionEdit = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-position/edit.ui-model.json");
    String builtinRoleAdd = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-role/add.ui-model.json");
    String builtinRoleEdit = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-role/edit.ui-model.json");
    String builtinPositionList = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-position.ui-schema.json");
    String builtinRoleList = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-role.ui-schema.json");
    String builtinRoleAddSchema = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-role/add.ui-schema.json");
    String builtinRoleEditSchema = resourceAsString("META-INF/ouroboros/metadata/dev/{appName}/builtin-role/edit.ui-schema.json");

    assertTrue(builtinPositionAdd.contains("\"path\": \"/dev/{appName}/builtin-position/add\""));
    assertTrue(builtinPositionEdit.contains("\"path\": \"/dev/{appName}/builtin-position/edit\""));
    assertTrue(builtinRoleAdd.contains("\"path\": \"/dev/{appName}/builtin-role/add\""));
    assertTrue(builtinRoleEdit.contains("\"path\": \"/dev/{appName}/builtin-role/edit\""));

    assertDialogUrlMatchesUiModelPath(
        builtinPositionList,
        builtinPositionAdd,
        "/dev/${appName}/builtin-position/add"
    );
    assertDialogUrlMatchesUiModelPath(
        builtinPositionList,
        builtinPositionEdit,
        "/dev/${appName}/builtin-position/edit"
    );
    assertDialogUrlMatchesUiModelPath(
        builtinRoleList,
        builtinRoleAdd,
        "/dev/${appName}/builtin-role/add"
    );
    assertDialogUrlMatchesUiModelPath(
        builtinRoleList,
        builtinRoleEdit,
        "/dev/${appName}/builtin-role/edit"
    );

    assertTrue(builtinRoleAddSchema.contains("\"url\": \"/api/dev/${appName}/builtin-role/authority-define-tree\""));
    assertTrue(builtinRoleEditSchema.contains("\"url\": \"/api/dev/${appName}/builtin-role/authority-define-tree\""));
    assertFalse(builtinRoleAddSchema.contains("/api/sys/authority/define-tree"));
    assertFalse(builtinRoleEditSchema.contains("/api/sys/authority/define-tree"));

    assertFalse(builtinPositionAdd.contains("${appName}"));
    assertFalse(builtinPositionEdit.contains("${appName}"));
    assertFalse(builtinPositionEdit.contains("${key}"));
    assertFalse(builtinRoleAdd.contains("${appName}"));
    assertFalse(builtinRoleEdit.contains("${appName}"));
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

  private void assertDialogUrlMatchesUiModelPath(String parentSchema, String childUiModel, String dialogUrl) {
    assertTrue(parentSchema.contains("\"url\": \"" + dialogUrl + "\""));
    assertTrue(childUiModel.contains("\"path\": \"" + dialogUrl.replace("${", "{") + "\""));
  }
}
