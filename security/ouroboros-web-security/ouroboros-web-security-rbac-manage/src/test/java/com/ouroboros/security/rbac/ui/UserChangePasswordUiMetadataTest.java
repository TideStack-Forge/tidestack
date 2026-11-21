package com.ouroboros.security.rbac.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;

class UserChangePasswordUiMetadataTest {

  @Test
  void changePasswordDialogIsBackedByUiModelAndUiSchema() {
    Map<String, Object> uiModel = Json.toMap(readResource(
        "META-INF/ouroboros/metadata/user-management/change-password.ui-model.json"));
    Map<String, Object> uiSchema = Json.toMap(readResource(
        "META-INF/ouroboros/metadata/system-setting/user-management/change-password.ui-schema.json"));

    assertEquals("/system-setting/user-management/change-password", uiModel.get("path"));
    assertEquals("amis", uiModel.get("type"));
    assertEquals("修改密码", uiModel.get("title"));
    assertFalse(uiModel.containsKey("body"));
    assertFalse(uiModel.containsKey("api"));

    assertEquals("/system-setting/user-management/change-password", uiSchema.get("name"));
    Map<?, ?> schema = asMap(uiSchema.get("schema"));
    assertEquals("form", schema.get("type"));
    assertNoLocaleKey(schema);
    assertEquals(false, schema.get("title"));
    assertEquals("horizontal", schema.get("mode"));

    Map<?, ?> api = asMap(schema.get("api"));
    assertEquals("patch", api.get("method"));
    assertEquals("/api/sys/user/modify-password", api.get("url"));
    Map<?, ?> data = asMap(api.get("data"));
    assertEquals("${credentials}", data.get("credentials"));
    assertEquals("${newPassword}", data.get("newPassword"));
    assertEquals("password", data.get("verificationMode"));
    assertFalse(data.containsKey("confirmPassword"));

    List<?> body = asList(schema.get("body"));
    Map<?, ?> oldPassword = findBodyItemByName(body, "credentials");
    assertNoLocaleKey(oldPassword);
    Map<?, ?> oldPasswordLocaleKeys = asMap(oldPassword.get("localeKeys"));
    assertEquals("changePassword.oldPassword", oldPasswordLocaleKeys.get("label"));
    assertEquals("changePassword.validation.oldPasswordRequired",
        oldPasswordLocaleKeys.get("validationErrors.minLength"));

    Map<?, ?> newPassword = findBodyItemByName(body, "newPassword");
    assertNoLocaleKey(newPassword);
    Map<?, ?> newPasswordLocaleKeys = asMap(newPassword.get("localeKeys"));
    assertEquals("changePassword.newPassword", newPasswordLocaleKeys.get("label"));
    assertEquals("changePassword.newPassword.description", newPasswordLocaleKeys.get("description"));
    assertEquals("changePassword.validation.newPasswordMinLength",
        newPasswordLocaleKeys.get("validationErrors.minLength"));

    Map<?, ?> confirmPassword = findBodyItemByName(body, "confirmPassword");
    assertNoLocaleKey(confirmPassword);
    Map<?, ?> confirmPasswordLocaleKeys = asMap(confirmPassword.get("localeKeys"));
    assertEquals("changePassword.confirmPassword", confirmPasswordLocaleKeys.get("label"));
    assertEquals("changePassword.validation.confirmPasswordMismatch",
        confirmPasswordLocaleKeys.get("validationErrors.equalsField"));
    assertEquals("input-password", confirmPassword.get("type"));
    assertTrue(asMap(confirmPassword.get("validations")).containsKey("equalsField"));

    Map<?, ?> passwordUpdatedNotice = findBodyItemByType(body, "alert");
    assertNoLocaleKey(passwordUpdatedNotice);
    assertEquals("changePassword.notice.passwordUpdated", asMap(passwordUpdatedNotice.get("localeKeys")).get("body"));

    List<?> actions = asList(schema.get("actions"));
    assertEquals(2, actions.size());
    Map<?, ?> cancelAction = asMap(actions.get(0));
    assertNoLocaleKey(cancelAction);
    assertEquals("cancel", asMap(cancelAction.get("localeKeys")).get("label"));
    assertEquals("cancel", cancelAction.get("actionType"));

    Map<?, ?> submitAction = asMap(actions.get(1));
    assertNoLocaleKey(submitAction);
    assertEquals("userMenu.changePassword", asMap(submitAction.get("localeKeys")).get("label"));
    assertEquals("submit", submitAction.get("type"));
  }

  private static Map<?, ?> findBodyItemByName(List<?> body, String name) {
    return body.stream()
        .map(UserChangePasswordUiMetadataTest::asMap)
        .filter(item -> name.equals(item.get("name")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("body item should exist: " + name));
  }

  private static Map<?, ?> findBodyItemByType(List<?> body, String type) {
    return body.stream()
        .map(UserChangePasswordUiMetadataTest::asMap)
        .filter(item -> type.equals(item.get("type")))
        .findFirst()
        .orElseThrow(() -> new AssertionError("body item should exist: " + type));
  }

  private static Map<?, ?> asMap(Object value) {
    return assertInstanceOf(Map.class, value);
  }

  private static List<?> asList(Object value) {
    return assertInstanceOf(List.class, value);
  }

  private static void assertNoLocaleKey(Map<?, ?> node) {
    assertFalse(node.containsKey("localeKey"));
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
