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
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuthFilterNodeCompatibilityTest {

  @AfterEach
  void cleanup() {
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void execute_returnsNamedPermissionFilters_fromCurrentSecurityIdentity() {
    Map<String, Object> expectedFilters = linkedMap("tenantId", "tenant-1");
    AccessControlParams params = buildAccessControlParams(expectedFilters);
    User user = buildUser("user-1", Collections.singletonList(
        buildRole("role-1", Collections.singletonList(
            buildGrantedAuthority("orders.read", buildAuthority("orders.read"), params)
        ))
    ));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(user)));

    FlowModel flowModel = mock(FlowModel.class);
    when(flowModel.getConnections()).thenReturn(Collections.emptyList());

    FlowNodeMeta meta = FlowNodeMeta.builder()
        .name("authFilter")
        .type("authFilter")
        .extraProp("authorityName", "orders.read")
        .build();

    AuthFilterNode node = new AuthFilterNode(flowModel, meta);

    var result = node.execute(Collections.emptyList());

    assertTrue(result.isSuccess());
    assertEquals(Collections.singletonList(expectedFilters), result.get());
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
