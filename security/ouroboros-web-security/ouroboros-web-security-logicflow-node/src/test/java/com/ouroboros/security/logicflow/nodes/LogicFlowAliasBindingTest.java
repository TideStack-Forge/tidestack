package com.ouroboros.security.logicflow.nodes;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNode;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.logicflow.nodes.NodeFactory;
import com.ouroboros.logicflow.nodes.NodeReferenceCatalog;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogicFlowAliasBindingTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void createNode_supportsLegacyAndDottedAuthFilterAliases_withoutChangingWrappedFilterOutput() {
    Map<String, Object> expectedFilters = linkedMap("tenantId", "tenant-1");
    Map<String, Object> expectedWrappedResult = Collections.singletonMap("wrappedFilters", expectedFilters);
    AccessControlParams params = buildAccessControlParams(expectedFilters);
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)
        ))
    ));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));

    FlowModel flowModel = mock(FlowModel.class);
    when(flowModel.getConnections()).thenReturn(Collections.emptyList());

    FlowNodeMeta legacyMeta = authFilterMeta("legacy-auth-filter", "authFilter");
    FlowNodeMeta dottedMeta = authFilterMeta("dotted-auth-filter", "auth.filter");

    FlowNode legacyNode = assertDoesNotThrow(
        () -> NodeFactory.createNode(
            flowModel,
            legacyMeta,
            NodeReferenceCatalog.empty()
        ),
        "Legacy authFilter literal should keep resolving through NodeFactory");
    FlowNode dottedNode = assertDoesNotThrow(
        () -> NodeFactory.createNode(
            flowModel,
            dottedMeta,
            NodeReferenceCatalog.empty()
        ),
        "Dotted auth.filter alias should resolve through the same AuthFilterNode builder");

    assertTrue(legacyNode instanceof AuthFilterNode);
    assertTrue(dottedNode instanceof AuthFilterNode);
    assertEquals(legacyNode.getClass(), dottedNode.getClass());

    var legacyResult = legacyNode.execute(Collections.emptyList());
    var dottedResult = dottedNode.execute(Collections.emptyList());

    assertTrue(legacyResult.isSuccess());
    assertTrue(dottedResult.isSuccess());
    assertEquals(Collections.singletonList(expectedWrappedResult), legacyResult.get());
    assertEquals(legacyResult.get(), dottedResult.get());
  }

  private static FlowNodeMeta authFilterMeta(String name, String type) {
    return FlowNodeMeta.builder()
        .name(name)
        .type(type)
        .extraProp("authorityName", "orders.read")
        .extraProp("resultWrapperKey", "wrappedFilters")
        .build();
  }

  private static User buildUser(String identity, List<Role> roles) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return identity;
      }

      @Override
      public List<Role> getRoles() {
        return roles;
      }
    };
  }

  private static Role buildRole(String id, List<? extends OuroborosGrantedAuthority> authorities) {
    return new Role() {
      @Override
      public String getId() {
        return id;
      }

      @Override
      public String getName() {
        return id;
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return authorities;
      }
    };
  }

  private static Authority buildAuthority(String fullName) {
    return new Authority() {
      @Override
      public String getFullName() {
        return fullName;
      }

      @Override
      public String getTitle() {
        return fullName;
      }

      @Override
      public AccessControlDefine getAccessControlDefine() {
        return AccessControlDefine.empty();
      }

      @Override
      public List<String> getApis() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getUiViews() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getMenuItems() {
        return Collections.emptyList();
      }
    };
  }

  private static OuroborosGrantedAuthority buildGrantedAuthority(
      String authorityName,
      Authority authorityObject,
      AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return authorityName;
      }

      @Override
      public Authority getAuthorityObject() {
        return authorityObject;
      }

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }

  private static AccessControlParams buildAccessControlParams(Map<String, Object> filters) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return Collections.emptyList();
      }

      @Override
      public List<String> getDisallowedFields() {
        return Collections.emptyList();
      }

      @Override
      public Map<String, Object> getFilters() {
        return filters;
      }
    };
  }

  private static Map<String, Object> linkedMap(Object... pairs) {
    Map<String, Object> map = new LinkedHashMap<String, Object>();
    for (int i = 0; i < pairs.length; i += 2) {
      map.put((String) pairs[i], pairs[i + 1]);
    }
    return map;
  }
}
