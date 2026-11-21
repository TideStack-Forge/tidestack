package com.ouroboros.security.rbac.controller;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.RoleCenter;
import com.ouroboros.security.rbac.entity.RoleAuthorityMapEntity;
import com.ouroboros.security.rbac.model.BuiltinRoleModel;
import com.ouroboros.security.rbac.service.RoleAuthorityService;
import com.ouroboros.security.rbac.store.BuiltinRoleDefinitionStore;
import com.ouroboros.util.Json;
import com.ouroboros.web.WebApiResultHttpStatusAdvice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RoleAuthorityPayloadBehaviorTest {

  private static final String ROLE_ID = "reviewer";
  private static final String AUTHORITY = "workflow.approve";

  @TempDir
  Path appHome;

  private final List<RoleAuthorityMapEntity> savedAuthorities = new ArrayList<>();
  private final RoleAuthorityService authorityService = mock(RoleAuthorityService.class);
  private MockedStatic<RoleCenter> roleCenters;
  private BuiltinRoleDefinitionStore store;
  private MockMvc mvc;

  @BeforeEach
  void setUp() throws Exception {
    store = new BuiltinRoleDefinitionStore(() -> appHome.toString());
    var builtinRole = new BuiltinRoleModel();
    builtinRole.setId(ROLE_ID);
    builtinRole.setName("Reviewer");
    builtinRole.setAuthorities(List.of());
    store.save(builtinRole).get();

    when(authorityService.getByRoleId(ROLE_ID)).thenReturn(savedAuthorities);
    doAnswer(invocation -> {
      savedAuthorities.clear();
      savedAuthorities.addAll(invocation.<Collection<RoleAuthorityMapEntity>>getArgument(1));
      return null;
    }).when(authorityService).updateRoleAuthorities(eq(ROLE_ID), anyCollection());

    var controller = new RoleController();
    ReflectionTestUtils.setField(controller, "roleAuthorityService", authorityService);
    ReflectionTestUtils.setField(controller, "builtinRoleDefinitionStore", store);
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new RoleControllerHttpStatusTest.BusinessExceptionAdvice(),
            new WebApiResultHttpStatusAdvice())
        .build();
    roleCenters = mockStatic(RoleCenter.class);
  }

  @AfterEach
  void tearDown() {
    roleCenters.close();
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldSaveUnconfiguredParamsAsEmptyObjects(boolean builtin) throws Exception {
    configureRole(builtin);
    var authorityItems = List.of(
        Map.of("authority", AUTHORITY),
        Map.of("authority", AUTHORITY, "accessControlParams", ""),
        Map.of("authority", AUTHORITY, "accessControlParams", "  \t"),
        Map.of("authority", AUTHORITY, "accessControlParams", Map.of()));

    for (var item : authorityItems) {
      putAuthorities(Json.toJsonString(List.of(item)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.items[0].accessControlParams").isMap())
          .andExpect(jsonPath("$.data.items[0].accessControlParams").isEmpty());
      assertEquals(Map.of(), savedParams(builtin));
    }
    putAuthorities("[{\"authority\":\"" + AUTHORITY + "\",\"accessControlParams\":null}]")
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items[0].accessControlParams").isMap())
        .andExpect(jsonPath("$.data.items[0].accessControlParams").isEmpty());
    assertEquals(Map.of(), savedParams(builtin));
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldPreserveConfiguredParamsInObjectAndSerializedForms(boolean builtin) throws Exception {
    configureRole(builtin);
    var params = Map.of("allowedFields", List.of("name"), "filters", Map.of("department", List.of("D1")));
    for (var value : List.of(params, Json.toJsonString(params))) {
      putAuthorities(Json.toJsonString(List.of(Map.of("authority", AUTHORITY, "accessControlParams", value))))
          .andExpect(status().isOk());
      assertEquals(params, savedParams(builtin));
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void shouldRejectMalformedParamsWithoutSaving(boolean builtin) throws Exception {
    configureRole(builtin);
    for (var value : List.of("not-a-json", "[]", "42", "false", List.of("invalid"), 42, false)) {
      putAuthorities(Json.toJsonString(List.of(Map.of("authority", AUTHORITY, "accessControlParams", value))))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.msg").value("角色权限数据非法: 第1项"));
    }
    verify(authorityService, never()).updateRoleAuthorities(eq(ROLE_ID), anyCollection());
    assertEquals(List.of(), store.findById(ROLE_ID).orElseThrow().getAuthorityDefinitions());
  }

  private void configureRole(boolean builtin) {
    var role = mock(Role.class);
    when(role.getId()).thenReturn(ROLE_ID);
    when(role.isBuiltinRole()).thenReturn(builtin);
    when(role.canEditAuthorities()).thenReturn(true);
    var center = mock(RoleCenter.class);
    when(center.getRole(ROLE_ID)).thenReturn(Optional.of(role));
    roleCenters.when(RoleCenter::instance).thenReturn(center);
  }

  private org.springframework.test.web.servlet.ResultActions putAuthorities(String payload) throws Exception {
    return mvc.perform(put("/api/sys/role/{id}/authority", ROLE_ID)
        .contentType("application/json").content(payload));
  }

  private Map<String, Object> savedParams(boolean builtin) {
    return builtin
        ? store.findById(ROLE_ID).orElseThrow().getAuthorityDefinitions().get(0).getAccessControlParamsSetting()
        : savedAuthorities.get(0).getAccessControlParams();
  }
}
