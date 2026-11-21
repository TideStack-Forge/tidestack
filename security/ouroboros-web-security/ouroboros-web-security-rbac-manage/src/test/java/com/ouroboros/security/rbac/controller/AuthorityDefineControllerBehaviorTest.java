package com.ouroboros.security.rbac.controller;

import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.appmodule.AppModuleIntegrator;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.AuthorityCenter;
import com.ouroboros.security.authority.DefaultAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.rbac.util.AccessControlDefineUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorityDefineControllerBehaviorTest {

  @Test
  void shouldCoverGetTreeAndTreeNodeAccessors() throws Exception {
    var integrator = mock(AppModuleIntegrator.class);

    var root = new DefaultAuthority();
    root.setFullName("sys");
    root.setTitle("System");
    root.setAccessControlDefine(new AccessControlDefine.EmptyAccessControlDefine());

    var child = new DefaultAuthority();
    child.setFullName("sys.user");
    child.setTitle("User");
    child.setAccessControlDefine(new AccessControlDefine.EmptyAccessControlDefine());

    when(integrator.rearrangeAuthorities(org.mockito.ArgumentMatchers.anyMap()))
        .thenReturn(java.util.Arrays.asList(root, child));

    var provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable(any())).thenReturn(integrator);

    var controller = new AuthorityDefineController(provider);

    var mvc = MockMvcBuilders.standaloneSetup(controller).build();
    mvc.perform(get("/api/sys/authority/define-tree"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.items").isArray());

    // direct nested node accessors
    var node = new AuthorityDefineController.AuthorityDefineTreeNode(child);
    assertEquals("sys.user", node.getFullName());
    assertEquals("sys", node.getParent());
    assertEquals("user", node.getName());
    assertEquals("User", node.getTitle());
    assertEquals(Integer.valueOf(0), node.getOrder());
    assertNotNull(node.getAccessControlSettingSchema());
    node.setChildren(Collections.singletonList(new AuthorityDefineController.AuthorityDefineTreeNode(root)));
    assertEquals(1, node.getChildren().size());
  }

  @Test
  void defaultConstructorPathShouldSupportEmptyIntegrator() {
    var provider = mock(ObjectProvider.class);
    when(provider.getIfAvailable(any())).thenReturn(mock(AppModuleIntegrator.class));

    var controller = new AuthorityDefineController(provider);
    assertNotNull(controller);
    assertTrue(AccessControlDefineUtils.generateAccessControlSettingSchema(new DefaultAuthority()).isEmpty());
  }
}
