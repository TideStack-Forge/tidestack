package com.ouroboros.organizationstructure.ui.provider;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersonnelProfileFormSchemeDevSchemaContractTest {

  private static final String SCHEMA_RESOURCE =
      "META-INF/ouroboros/metadata/dev/{appName}/organization-management/personnel-profile-form-scheme.ui-schema.json";

  @Test
  void schemaShouldExposeFormSchemeManagementCapabilities() {
    Map<?, ?> schema = ResourceUtil.getResourceContentStream(SCHEMA_RESOURCE)
        .findFirst()
        .map(Json::toMap)
        .map(resource -> map(resource.get("schema")))
        .orElseThrow(AssertionError::new);

    assertEquals("page", schema.get("type"));

    List<?> body = list(schema.get("body"));
    assertEquals("alert", map(body.get(0)).get("type"));

    Map<?, ?> crud = map(body.get(1));
    assertEquals("crud", crud.get("type"));
    assertEquals("/api/dev/${appName}/organization-management/personnel-profile-form-scheme",
        map(crud.get("api")).get("url"));

    assertTrue(containsColumn(crud, "isDefault"));
    assertTrue(containsColumn(crud, "enabled"));
    assertTrue(containsColumn(crud, "priority"));

    Map<?, ?> createDialog = findToolbarButton(crud, "新增方案");
    assertNotNull(createDialog);
    assertTrue(containsFormField(createDialog, "name"));
    assertTrue(containsFormField(createDialog, "enabled"));
    assertTrue(containsFormField(createDialog, "isDefault"));
    assertTrue(containsFormField(createDialog, "createSchema"));
    assertTrue(containsFormField(createDialog, "editSchema"));
    assertTrue(containsFormField(createDialog, "detailSchema"));

    Map<?, ?> editButton = findOperationButton(crud, "编辑");
    assertNotNull(editButton);
    assertTrue(containsFormField(map(editButton.get("dialog")), "enabled"));
    assertTrue(containsFormField(map(editButton.get("dialog")), "isDefault"));

    Map<?, ?> viewButton = findOperationButton(crud, "查看");
    assertNotNull(viewButton);
    assertTrue(dialogContainsTab(viewButton, "新增 Schema"));
    assertTrue(dialogContainsTab(viewButton, "编辑 Schema"));
    assertTrue(dialogContainsTab(viewButton, "详情 Schema"));
  }

  private boolean containsColumn(Map<?, ?> crud, String name) {
    for (Object item : list(crud.get("columns"))) {
      Map<?, ?> column = map(item);
      if (name.equals(column.get("name"))) {
        return true;
      }
    }
    return false;
  }

  private Map<?, ?> findToolbarButton(Map<?, ?> crud, String label) {
    for (Object toolbarItem : list(crud.get("headerToolbar"))) {
      Map<?, ?> item = map(toolbarItem);
      if (label.equals(item.get("label"))) {
        return map(item.get("dialog"));
      }
    }
    return null;
  }

  private Map<?, ?> findOperationButton(Map<?, ?> crud, String label) {
    for (Object item : list(crud.get("columns"))) {
      Map<?, ?> column = map(item);
      if (!"operation".equals(column.get("type"))) {
        continue;
      }
      for (Object buttonItem : list(column.get("buttons"))) {
        Map<?, ?> button = map(buttonItem);
        if (label.equals(button.get("label"))) {
          return button;
        }
      }
    }
    return null;
  }

  private boolean containsFormField(Map<?, ?> dialog, String name) {
    Map<?, ?> body = map(dialog.get("body"));
    for (Object item : list(body.get("body"))) {
      Map<?, ?> field = map(item);
      if (name.equals(field.get("name"))) {
        return true;
      }
    }
    return false;
  }

  private boolean dialogContainsTab(Map<?, ?> button, String title) {
    Map<?, ?> dialog = map(button.get("dialog"));
    Map<?, ?> body = map(dialog.get("body"));
    for (Object item : list(body.get("tabs"))) {
      Map<?, ?> tab = map(item);
      if (title.equals(tab.get("title"))) {
        return true;
      }
    }
    return false;
  }

  private List<?> list(Object value) {
    return List.class.cast(value);
  }

  private Map<?, ?> map(Object value) {
    return Map.class.cast(value);
  }
}
