package com.ouroboros.dev.app.resource;

import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppEditAccessSchemaTest {

  @Test
  void dashboardSchemaAddsEditAccessEntryCard() {
    String dashboardSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/dashboard.ui-schema.json");

    assertTrue(dashboardSchema.contains("/api/app/${appName}/edit-access-entry"));
    assertTrue(dashboardSchema.contains("/dev/${appName}/edit-access"));
    assertTrue(dashboardSchema.contains("\"type\": \"button-group\""));
    assertTrue(dashboardSchema.contains("\"label\": \"编辑应用配置\""));
    assertTrue(dashboardSchema.contains("\"label\": \"更多\""));
    assertTrue(dashboardSchema.contains("\"label\": \"编辑权限\""));
    assertTrue(dashboardSchema.contains("\"className\": \"h-full flex flex-col justify-between px-4 py-5\""));
  }

  @Test
  void editAccessPageSchemaUsesEntryAndConfigApis() {
    String editAccessSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/edit-access.ui-schema.json");

    assertTrue(editAccessSchema.contains("/api/app/${appName}/edit-access-entry"));
    assertTrue(editAccessSchema.contains("/api/app/${appName}/edit-access"));
    assertTrue(editAccessSchema.contains("/api/app/${appName}/edit-access/roles?perPage=1000"));
    assertTrue(editAccessSchema.contains("无权限访问"));
    assertTrue(editAccessSchema.contains("\"title\": false"));
    assertFalse(editAccessSchema.contains("\"type\": \"combo\""));
    assertTrue(editAccessSchema.contains("\"type\": \"user-picker\""));
    assertTrue(editAccessSchema.contains("\"type\": \"organization-picker\""));
    assertTrue(editAccessSchema.contains("\"type\": \"select\""));
    assertFalse(editAccessSchema.contains("/api/app/${appName}/edit-access/users"));
    assertFalse(editAccessSchema.contains("/api/app/${appName}/edit-access/organizations"));
    assertFalse(editAccessSchema.contains("\"listUrl\""));
    assertFalse(editAccessSchema.contains("\"getByIdsUrl\""));
    assertFalse(editAccessSchema.contains("\"organizationTreeUrl\""));
    assertTrue(editAccessSchema.contains("\"joinValues\": false"));
    assertTrue(editAccessSchema.contains("\"extractValue\": false"));
    assertTrue(editAccessSchema.contains("\"multiple\": true"));
  }

  @Test
  void editAccessUiModelRegistersRoute() {
    String editAccessModel = readResource("META-INF/ouroboros/metadata/dev/{appName}/edit-access.ui-model.json");

    assertTrue(editAccessModel.contains("\"path\": \"/dev/{appName}/edit-access\""));
    assertTrue(editAccessModel.contains("\"title\": \"编辑权限\""));
  }

  @Test
  void vcsButtonsFragmentFetchesCurrentUserEditability() {
    String vcsButtons = readSiblingModuleResource(
        "ouroboros-dev-model-manage-webapi-scaffold",
        "META-INF/ouroboros/dev/fragment-schema/vcs-buttons.json.ftl"
    );

    assertTrue(vcsButtons.contains("/api/app/${appName}/edit-access-entry"));
    assertTrue(vcsButtons.contains("currentUserCanEdit"));
  }

  @Test
  void authorityMetadataDefinesDedicatedEditAccessManagementPermission() {
    String appsAuthority = readResource("META-INF/ouroboros/metadata/apps.authority.json");
    String editAccessManagementAuthority = readResource("META-INF/ouroboros/metadata/apps/edit-access-management.authority.json");

    assertTrue(appsAuthority.contains("/api/dev/{name}/dev-workspace-menu"));
    assertTrue(appsAuthority.contains("/api/app/{name}/edit-access-entry"));
    assertTrue(editAccessManagementAuthority.contains("\"fullName\": \"Apps.editAccessManagement\""));
    assertTrue(editAccessManagementAuthority.contains("/api/app/{name}/edit-access-entry"));
    assertTrue(editAccessManagementAuthority.contains("/api/app/{name}/edit-access"));
    assertTrue(editAccessManagementAuthority.contains("/api/app/{name}/edit-access/roles"));
    assertFalse(editAccessManagementAuthority.contains("/api/app/{name}/edit-access/users"));
    assertFalse(editAccessManagementAuthority.contains("/api/app/{name}/edit-access/organizations"));
    assertTrue(editAccessManagementAuthority.contains("/dev/{appName}/edit-access"));
  }

  @Test
  void appsListSchemaAddsEditAccessMenuEntryWithPermissionFlag() {
    String appsSchema = readResource("META-INF/ouroboros/metadata/dev/apps.ui-schema.json");

    assertTrue(appsSchema.contains("\"canManageEditAccess\": \"$canManageEditAccess\""));
    assertTrue(appsSchema.contains("\"label\": \"编辑权限\""));
    assertTrue(appsSchema.contains("\"visibleOn\": \"${canManageEditAccess}\""));
    assertTrue(appsSchema.contains("/dev/${name}/edit-access"));
  }

  @Test
  void appsListOpenActionNavigatesToDashboardAfterMenuUpdate() {
    String appsSchema = readResource("META-INF/ouroboros/metadata/dev/apps.ui-schema.json");

    assertTrue(appsSchema.contains("\"eventName\": \"updateMenu\""));
    assertTrue(appsSchema.contains("\"eventName\": \"updateAppName\""));
    assertTrue(appsSchema.contains("\"stopPropagation\": true"));
    assertTrue(appsSchema.contains("\"actionType\": \"openOrSwitchTab\""));
    assertTrue(appsSchema.contains("\"url\": \"/dev/${name}/dashboard\""));
    assertTrue(appsSchema.contains("\"replaceCurrentTab\": true"));
    assertFalse(appsSchema.contains("\"actionType\": \"openMenu\""));
    assertFalse(appsSchema.contains("\"menuName\": \"businessModelManagement\""));
  }

  @Test
  void appCreateSchemaReloadsAppListAfterSubmitSuccess() {
    String addAppSchema = readResource("META-INF/ouroboros/metadata/dev/apps/add.ui-schema.json");

    assertTrue(addAppSchema.contains("\"submitSucc\""));
    assertTrue(addAppSchema.contains("\"actionType\": \"closeDialog\""));
    assertTrue(addAppSchema.contains("\"actionType\": \"reload\""));
    assertTrue(addAppSchema.contains("\"componentName\": \"appList\""));
  }

  @Test
  void extendJarManagementSchemaAddsBackendGeneratorEntryBehindEditAccess() {
    String extendJarSchema = readResource("META-INF/ouroboros/metadata/dev/{appName}/extend-jar-management.ui-schema.json");

    assertTrue(extendJarSchema.contains("/api/app/${appName}/edit-access-entry"));
    assertTrue(extendJarSchema.contains("\"label\": \"上传\""));
    assertTrue(extendJarSchema.contains("\"label\": \"生成项目\""));
    assertTrue(extendJarSchema.indexOf("\"label\": \"生成项目\"") < extendJarSchema.indexOf("\"label\": \"上传\""));
    assertTrue(extendJarSchema.contains("\"level\": \"enhance\""));
    assertTrue(extendJarSchema.contains("\"visibleOn\": \"${currentUserCanEdit}\""));
    assertTrue(extendJarSchema.contains("\"type\": \"ui-schema\""));
    assertTrue(extendJarSchema.contains("/api/dev/${appName}/project-generation-form"));
    assertTrue(extendJarSchema.contains("\"scope\": \"BACKEND_EXTENSION\""));
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

  private String readSiblingModuleResource(String siblingModuleName, String resourcePath) {
    try {
      Path moduleBase = Paths.get(AppEditAccessSchemaTest.class.getProtectionDomain().getCodeSource().getLocation().toURI())
          .getParent()
          .getParent();
      Path resourceFile = moduleBase.getParent()
          .resolve(siblingModuleName)
          .resolve("src/main/resources")
          .resolve(resourcePath);
      assertTrue(Files.exists(resourceFile), "resource should exist: " + resourceFile);
      return new String(Files.readAllBytes(resourceFile), StandardCharsets.UTF_8);
    } catch (Exception e) {
      throw new AssertionError("failed to read sibling module resource: " + resourcePath, e);
    }
  }
}
