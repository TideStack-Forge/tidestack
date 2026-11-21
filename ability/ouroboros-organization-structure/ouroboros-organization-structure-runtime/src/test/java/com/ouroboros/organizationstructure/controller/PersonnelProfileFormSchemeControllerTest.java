package com.ouroboros.organizationstructure.controller;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.organizationstructure.profile.entity.PersonnelProfileFormSchemeEntity;
import com.ouroboros.organizationstructure.profile.service.PersonnelProfileFormSchemeService;
import com.ouroboros.web.ItemsWebApiResult;
import com.ouroboros.web.WebApiResult;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PersonnelProfileFormSchemeControllerTest {

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldSupportCrudAndPreviewFlows() throws Exception {
    PersonnelProfileFormSchemeService service = mock(PersonnelProfileFormSchemeService.class);
    PersonnelProfileFormSchemeController controller = new PersonnelProfileFormSchemeController(service);

    PersonnelProfileFormSchemeEntity scheme = scheme("s-1", "demo-app", "方案A");
    when(service.searchAll("demo-app")).thenReturn(Collections.singletonList(scheme));
    when(service.getById("s-1", "demo-app")).thenReturn(Optional.of(scheme));
    when(service.create(org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class), org.mockito.ArgumentMatchers.eq("tester")))
        .thenAnswer(invocation -> invocation.getArgument(0));
    when(service.update(org.mockito.ArgumentMatchers.eq("s-1"), org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class), org.mockito.ArgumentMatchers.eq("tester")))
        .thenReturn(Boolean.TRUE);
    when(service.delete("s-1", "tester")).thenReturn(Boolean.TRUE);

    ItemsWebApiResult<PersonnelProfileFormSchemeEntity> list = controller.list("demo-app");
    assertEquals(1, list.getData().getItems().size());

    WebApiResult<PersonnelProfileFormSchemeEntity> detail = controller.detail("demo-app", "s-1");
    assertEquals("s-1", detail.getData().getId());

    SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("tester", "n/a"));

    PersonnelProfileFormSchemeEntity createInput = scheme(null, null, "新方案");
    WebApiResult<PersonnelProfileFormSchemeEntity> created = controller.create("demo-app", createInput);
    assertEquals("demo-app", created.getData().getAppName());

    PersonnelProfileFormSchemeEntity updateInput = scheme("s-1", null, "更新方案");
    WebApiResult<Boolean> updated = controller.update("demo-app", "s-1", updateInput);
    assertTrue(updated.getData());
    assertEquals("demo-app", updateInput.getAppName());

    WebApiResult<Boolean> deleted = controller.delete("demo-app", "s-1");
    assertTrue(deleted.getData());

    verify(service).create(org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class), org.mockito.ArgumentMatchers.eq("tester"));
    verify(service).update(org.mockito.ArgumentMatchers.eq("s-1"), org.mockito.ArgumentMatchers.any(PersonnelProfileFormSchemeEntity.class), org.mockito.ArgumentMatchers.eq("tester"));
  }

  @Test
  void detailUpdateDeleteShouldReturn404WhenSchemeMissingInApp() throws Exception {
    PersonnelProfileFormSchemeService service = mock(PersonnelProfileFormSchemeService.class);
    PersonnelProfileFormSchemeController controller = new PersonnelProfileFormSchemeController(service);

    when(service.getById("missing", "demo-app")).thenReturn(Optional.empty());

    assertEquals(404, controller.detail("demo-app", "missing").getCode());
    assertEquals(404, controller.update("demo-app", "missing", scheme(null, null, "x")).getCode());
    assertEquals(404, controller.delete("demo-app", "missing").getCode());
  }

  private PersonnelProfileFormSchemeEntity scheme(String id, String appName, String name) {
    PersonnelProfileFormSchemeEntity entity = new PersonnelProfileFormSchemeEntity();
    entity.setId(id);
    entity.setAppName(appName);
    entity.setName(name);
    entity.setEnabled(true);
    entity.setIsDefault(true);
    entity.setPriority(0);
    entity.setCreateSchema(schema("create"));
    entity.setEditSchema(schema("edit"));
    entity.setDetailSchema(schema("detail"));
    entity.setScope(Collections.<String, Object>emptyMap());
    entity.setMatchingRules(Collections.<String, Object>emptyMap());
    return entity;
  }

  private java.util.Map<String, Object> schema(String marker) {
    java.util.Map<String, Object> map = new LinkedHashMap<String, Object>();
    map.put("type", "page");
    map.put("marker", marker);
    return map;
  }
}
