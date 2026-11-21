package com.ouroboros.dev.app.controller;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.dev.app.service.impl.DevMenuServiceImpl;
import com.ouroboros.dev.app.model.DevMenuModel;
import com.ouroboros.dev.app.service.DevMenuService;
import com.ouroboros.util.Json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevWorkspaceMenuControllerHttpTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    DevWorkspaceMenuController controller = new DevWorkspaceMenuController();
    ReflectionTestUtils.setField(controller, "devMenuService", new DevMenuServiceImpl());
    mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("tester", "n/a"));
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void devWorkspaceMenuEndpointReturnsDeduplicatedStructuredMenus() throws Exception {
    MvcResult result = mockMvc.perform(get("/api/dev/demo/dev-workspace-menu"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    JsonNode data = root.get("data");
    JsonNode main = data.get("main");
    JsonNode secondary = data.get("secondary");

    assertNotNull(main);
    assertNotNull(secondary);
    assertEquals("应用开发看板", main.get(0).get("title").asText());

    assertUniqueFullNames(main, new HashSet<>());
    assertUniqueFullNames(secondary, new HashSet<>());

    JsonNode organizationManagement = findByFullName(main, "OrganizationManagement");
    assertNotNull(organizationManagement);
    assertEquals("组织架构", organizationManagement.get("title").asText());
    assertEquals("内置角色管理", organizationManagement.get("children").get(0).get("title").asText());
    assertEquals("内置岗位管理", organizationManagement.get("children").get(1).get("title").asText());
  }

  @Test
  void devWorkspaceMenuEndpointFlattensExtraPropsIntoTopLevelJsonFields() throws Exception {
    DevWorkspaceMenuController controller = new DevWorkspaceMenuController();
    ReflectionTestUtils.setField(controller, "devMenuService", new DevMenuService() {
      @Override
      public java.util.List<? extends com.ouroboros.menu.MenuModel> getMainMenus(String appName) {
        return java.util.Collections.singletonList(menuWithFixedFlag());
      }

      @Override
      public java.util.List<? extends com.ouroboros.menu.MenuModel> getSecondaryMenus(String appName) {
        return java.util.Collections.emptyList();
      }
    });

    MockMvc customMockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    MvcResult result = customMockMvc.perform(get("/api/dev/demo/dev-workspace-menu"))
        .andExpect(status().isOk())
        .andReturn();

    JsonNode root = objectMapper.readTree(result.getResponse().getContentAsByteArray());
    JsonNode mainMenu = root.get("data").get("main").get(0);

    assertEquals(true, mainMenu.get("isFixedTab").asBoolean());
    assertTrue(mainMenu.get("extraProps") == null);
  }

  private static void assertUniqueFullNames(JsonNode menus, Set<String> fullNames) {
    Iterator<JsonNode> iterator = menus.elements();
    while (iterator.hasNext()) {
      JsonNode menu = iterator.next();
      assertTrue(fullNames.add(menu.get("fullName").asText()), "duplicate menu fullName: " + menu.get("fullName").asText());
      JsonNode children = menu.get("children");
      if (children != null && children.isArray()) {
        assertUniqueFullNames(children, fullNames);
      }
    }
  }

  private static JsonNode findByFullName(JsonNode menus, String fullName) {
    Iterator<JsonNode> iterator = menus.elements();
    while (iterator.hasNext()) {
      JsonNode menu = iterator.next();
      if (fullName.equals(menu.get("fullName").asText())) {
        return menu;
      }
      JsonNode children = menu.get("children");
      if (children != null && children.isArray()) {
        JsonNode child = findByFullName(children, fullName);
        if (child != null) {
          return child;
        }
      }
    }
    return null;
  }

  private static DevMenuModel menuWithFixedFlag() {
    return Json.toBean(DevMenuModel.class,
        "{\"fullName\":\"ApplicationManagement\",\"title\":\"应用管理\",\"url\":\"/dev/demo/ApplicationManagement\",\"placement\":\"main\",\"stage\":\"dev\",\"order\":1,\"isFixedTab\":true}");
  }
}
