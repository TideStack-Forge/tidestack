package com.ouroboros.organizationstructure.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class OrganizationStructureUiContractTest {

  @Test
  void organizationManagementShouldBindEditDialogPerNodeKind() throws IOException {
    // 组织架构页的「编辑岗位/编辑部门/编辑组织」是按节点类型与层级范围互斥显示的三个入口，
    // 各自绑定对应的编辑 schema。这里按语义校验三者齐备且绑定正确，
    // 而不是断言某个具体表达式的字面量——后者会在等价重构时产生误报。
    JsonNode schema = new ObjectMapper().readTree(
        resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management.ui-schema.json"));

    JsonNode items = schema.at("/schema/body/0/items/0/body/2/items");
    assertTrue(items.isArray(), "操作区应存在按钮数组");

    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("编辑岗位", "/system-setting/organization-management/edit-position");
    expected.put("编辑部门", "/system-setting/organization-management/edit-department");
    expected.put("编辑组织", "/system-setting/organization-management/edit-company");

    Map<String, String> actual = new LinkedHashMap<>();
    Map<String, String> visibleOn = new LinkedHashMap<>();
    for (JsonNode item : items) {
      String label = item.path("label").asText();
      if (expected.containsKey(label)) {
        actual.put(label, item.at("/dialog/body/schemaId").asText());
        visibleOn.put(label, item.path("visibleOn").asText());
      }
    }

    assertEquals(expected, actual, "每个节点类型都应有对应的编辑入口，并绑定正确的编辑 schema");
    // 互斥可见：岗位入口仅对 position 生效，部门/组织入口按层级范围二选一
    assertTrue(visibleOn.get("编辑岗位").contains("nodeType === 'position'"),
        "编辑岗位应仅对岗位节点可见，实际=" + visibleOn.get("编辑岗位"));
    assertTrue(visibleOn.get("编辑部门").contains("nodeAdminLevelScope === 'DEPT'"),
        "编辑部门应仅对部门级节点可见，实际=" + visibleOn.get("编辑部门"));
    assertTrue(visibleOn.get("编辑组织").contains("nodeAdminLevelScope === 'ORG'"),
        "编辑组织应仅对组织级节点可见，实际=" + visibleOn.get("编辑组织"));
  }

  @Test
  void organizationManagementShouldBindDeleteActionPerNodeKind() throws IOException {
    // 删除入口与编辑入口同样是按节点类型互斥的三个按钮，各自调用对应资源的删除接口。
    JsonNode schema = new ObjectMapper().readTree(
        resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management.ui-schema.json"));

    JsonNode items = schema.at("/schema/body/0/items/0/body/2/items");
    assertTrue(items.isArray(), "操作区应存在按钮数组");

    Map<String, String> expected = new LinkedHashMap<>();
    expected.put("删除岗位", "/api/organization/positions/${nodeId}");
    expected.put("删除部门", "/api/organization/departments/${nodeId}");
    expected.put("删除组织", "/api/organization/companies/${nodeId}");

    Map<String, String> actual = new LinkedHashMap<>();
    for (JsonNode item : items) {
      String label = item.path("label").asText();
      if (expected.containsKey(label)) {
        actual.put(label, item.at("/onEvent/click/actions/0/args/api/url").asText());
      }
    }

    assertEquals(expected, actual, "每个节点类型都应有对应的删除入口，并调用正确的删除接口");
  }

  @Test
  void adminLevelDeleteButtonShouldRequireConfirmation() throws IOException {
    // 删除是不可逆操作，必须配置二次确认，避免误点直接删除
    JsonNode schema = new ObjectMapper().readTree(
        resourceAsString("META-INF/ouroboros/metadata/system-setting/admin-level-management.ui-schema.json"));

    JsonNode buttons = schema.at("/schema/body/0/columns/5/buttons");
    assertTrue(buttons.isArray(), "操作列应存在按钮数组");

    JsonNode deleteButton = null;
    for (JsonNode button : buttons) {
      if ("删除".equals(button.path("label").asText())) {
        deleteButton = button;
      }
    }

    assertNotNull(deleteButton, "应存在删除按钮");
    assertFalse(deleteButton.path("confirmText").asText().isBlank(),
        "删除按钮必须配置二次确认文案");
  }

  @Test
  void adminLevelManagementShouldUseCanonicalApiAndRegisteredEditView() throws IOException {
    String adminLevelManagement = resourceAsString("META-INF/ouroboros/metadata/system-setting/admin-level-management.ui-schema.json");
    String adminLevelEdit = resourceAsString("META-INF/ouroboros/metadata/system-setting/admin-level-management/edit.ui-schema.json");
    String adminLevelEditModel = resourceAsString("META-INF/ouroboros/metadata/admin-level-management/edit.ui-model.json");
    String authority = resourceAsString("META-INF/ouroboros/metadata/adminLevelManagement.authority.json");
    String addCompany = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management/add-company.ui-schema.json");
    String editCompany = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management/edit-company.ui-schema.json");
    String addDepartment = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management/add-department.ui-schema.json");
    String editDepartment = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management/edit-department.ui-schema.json");
    String organizationManagement = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management.ui-schema.json");
    String organizationDetail = resourceAsString("META-INF/ouroboros/metadata/system-setting/organization-management/organization-detail.ui-schema.json");
    String organizationDetailModel = resourceAsString("META-INF/ouroboros/metadata/organization-management/organization-detail.ui-model.json");

    assertTrue(adminLevelManagement.contains("\"url\": \"/api/admin-level\""));
    assertFalse(adminLevelManagement.contains("/api/admin-level/list"));
    assertTrue(adminLevelManagement.contains("\"name\": \"adminLevelCrud\""));
    assertTrue(adminLevelManagement.contains("\"type\": \"operation\""));
    assertTrue(adminLevelManagement.contains("\"label\": \"编辑\""));
    assertTrue(adminLevelManagement.contains("\"actionType\": \"closeDialog\""));
    assertTrue(adminLevelManagement.contains("\"componentName\": \"adminLevelCrud\""));

    assertTrue(adminLevelEdit.contains("\"url\": \"/api/admin-level/${id}\""));
    assertTrue(adminLevelEdit.contains("\"componentName\": \"adminLevelCrud\""));
    assertTrue(adminLevelEditModel.contains("\"path\": \"/system-setting/admin-level-management/edit\""));
    assertTrue(authority.contains("GET /api/admin-level"));
    assertTrue(authority.contains("/system-setting/admin-level-management/edit"));
    assertFalse(authority.contains("GET /api/admin-level/list"));

    assertTrue(addCompany.contains("/api/admin-level?scope=ORG&enabled=true"));
    assertTrue(editCompany.contains("/api/admin-level?scope=ORG&enabled=true"));
    assertTrue(addDepartment.contains("/api/admin-level?scope=DEPT&enabled=true"));
    assertTrue(editDepartment.contains("/api/admin-level?scope=DEPT&enabled=true"));
    assertTrue(organizationManagement.contains("\"nodeAdminLevelScope\": \"${item.adminLevelScope}\""));
    assertTrue(organizationManagement.contains("departmentId=${nodeAdminLevelScope === 'DEPT' ? nodeId : ''}&companyId=${nodeAdminLevelScope === 'ORG' ? nodeId : ''}&positionId=${nodeType === 'position' ? nodeId : ''}"));
    assertTrue(organizationManagement.contains("\"visibleOn\": \"${nodeId && nodeAdminLevelScope}\""));
    assertEquals(2, countOccurrences(organizationManagement, "\"visibleOn\": \"${nodeId && nodeAdminLevelScope === 'DEPT'}\""));
    assertTrue(organizationManagement.contains("\"departmentId\": \"${nodeAdminLevelScope === 'DEPT' ? nodeId : ''}\""));
    assertTrue(organizationManagement.contains("\"nodeAdminLevelScope\": \"${nodeAdminLevelScope}\""));
    assertFalse(organizationManagement.contains("nodeType === 'company'"));
    assertFalse(organizationManagement.contains("nodeType === 'department'"));
    assertFalse(organizationManagement.contains("\"visibleOn\": \"${nodeId && (nodeType === 'company' || nodeType === 'department')}\""));
    assertFalse(organizationManagement.contains("\"visibleOn\": \"${nodeId && (nodeType === 'department' || nodeType === 'company')}\""));
    assertTrue(organizationDetail.contains("${nodeType === 'position' ? '岗位' : nodeAdminLevelScope === 'ORG' ? '组织' : '部门'}"));
    assertTrue(organizationDetail.contains("${type === 'position' ? '岗位' : adminLevelScope === 'ORG' ? '组织' : '部门'}"));
    assertTrue(organizationDetailModel.contains("\"title\": \"组织节点信息\""));
    assertFalse(organizationDetail.contains("type === 'company'"));
  }

  private int countOccurrences(String text, String target) {
    int count = 0;
    int index = 0;
    while ((index = text.indexOf(target, index)) >= 0) {
      count++;
      index += target.length();
    }
    return count;
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
