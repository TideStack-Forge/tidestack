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
import com.ouroboros.security.OuroborosAuthenticationHolder;
import com.ouroboros.security.authentication.DefaultAuthentication;
import com.ouroboros.security.authority.Authority;
import com.ouroboros.security.authority.OuroborosGrantedAuthority;
import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LogicFlowAuthNodesCurrentSecurityContextTest {

  @AfterEach
  void cleanup() {
    AuthorizationDecisionContextHolder.clearCurrentRequest();
    SecurityContextHolder.clearContext();
    CurrentSecurityContextHolder.clearDelegate();
  }

  @Test
  void execute_returnsWrappedAllowedFields_fromCurrentSecurityIdentityDecisionContext() {
    List<String> expectedAllowedFields = Collections.singletonList("visibleField");
    AccessControlParams params = buildAccessControlParams(
        expectedAllowedFields,
        Collections.singletonList("secretField"),
        linkedMap("tenantId", "tenant-1"));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(buildUser(params))));

    AuthAllowedFieldsNode node = new AuthAllowedFieldsNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authAllowedFields")
        .type("authAllowedFields")
        .extraProp("resultWrapperKey", "allowedFields")
        .build());

    var result = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> node.execute(Collections.emptyList()));

    assertTrue(result.isSuccess());
    assertEquals(
        Collections.singletonList(Collections.singletonMap("allowedFields", expectedAllowedFields)),
        result.get());
  }

  @Test
  void execute_returnsWrappedDisallowedFields_fromCurrentSecurityIdentityDecisionContext() {
    List<String> expectedDisallowedFields = Collections.singletonList("secretField");
    AccessControlParams params = buildAccessControlParams(
        Collections.singletonList("visibleField"),
        expectedDisallowedFields,
        linkedMap("tenantId", "tenant-1"));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(buildUser(params))));

    AuthDisallowedFieldsNode node = new AuthDisallowedFieldsNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authDisallowedFields")
        .type("authDisallowedFields")
        .extraProp("resultWrapperKey", "disallowedFields")
        .build());

    var result = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> node.execute(Collections.emptyList()));

    assertTrue(result.isSuccess());
    assertEquals(
        Collections.singletonList(Collections.singletonMap("disallowedFields", expectedDisallowedFields)),
        result.get());
  }

  @Test
  void execute_returnsWrappedFilters_fromCurrentSecurityIdentityDecisionContext() {
    Map<String, Object> expectedFilters = linkedMap("tenantId", "tenant-1");
    AccessControlParams params = buildAccessControlParams(
        Collections.singletonList("visibleField"),
        Collections.singletonList("secretField"),
        expectedFilters);
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(new DefaultSecurityIdentity(buildUser(params))));

    AuthFilterNode node = new AuthFilterNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authFilter")
        .type("authFilter")
        .extraProp("resultWrapperKey", "authFilter")
        .build());

    var result = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> node.execute(Collections.emptyList()));

    assertTrue(result.isSuccess());
    assertEquals(
        Collections.singletonList(Collections.singletonMap("authFilter", expectedFilters)),
        result.get());
  }

  @Test
  void execute_deniesInsteadOfReturningUnrestrictedParameters_whenCurrentSecurityContextSeamIsEmpty() {
    AccessControlParams holderParams = buildAccessControlParams(
        Collections.singletonList("visibleField"),
        Collections.singletonList("secretField"),
        linkedMap("tenantId", "tenant-1"));
    SecurityContextHolder.getContext().setAuthentication(
        OuroborosAuthenticationHolder.toSpringAuthentication(new DefaultAuthentication(buildUser(holderParams))));
    CurrentSecurityContextHolder.clearDelegate();

    AuthAllowedFieldsNode allowedFieldsNode = new AuthAllowedFieldsNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authAllowedFields")
        .type("authAllowedFields")
        .extraProp("resultWrapperKey", "allowedFields")
        .build());
    AuthDisallowedFieldsNode disallowedFieldsNode = new AuthDisallowedFieldsNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authDisallowedFields")
        .type("authDisallowedFields")
        .extraProp("resultWrapperKey", "disallowedFields")
        .build());
    AuthFilterNode filterNode = new AuthFilterNode(mockFlowModel(), FlowNodeMeta.builder()
        .name("authFilter")
        .type("authFilter")
        .extraProp("resultWrapperKey", "authFilter")
        .build());

    var allowedFieldsResult = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> allowedFieldsNode.execute(Collections.emptyList()));
    var disallowedFieldsResult = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> disallowedFieldsNode.execute(Collections.emptyList()));
    var filterResult = AuthorizationDecisionContextHolder.withCurrentRequest(
        "GET",
        "/api/orders",
        () -> filterNode.execute(Collections.emptyList()));

    assertAll(
        () -> assertFalse(allowedFieldsResult.isSuccess()),
        () -> assertFalse(disallowedFieldsResult.isSuccess()),
        () -> assertFalse(filterResult.isSuccess()));
  }

  @Test
  void namedNodesCannotEscapeTheDelegatedCredentialBoundary() {
    var params = buildAccessControlParams(List.of("visibleField"), List.of("secretField"), linkedMap("tenantId", "tenant-1"));
    var identity = new DefaultSecurityIdentity(buildUser(params),
        com.ouroboros.security.identity.AuthorizationBoundary.restricted("platform-api", Map.of()),
        new com.ouroboros.security.identity.CredentialContext(
            com.ouroboros.security.identity.CredentialContext.Kind.DELEGATED, "client", "grant", "platform-api"));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(identity));
    var meta = FlowNodeMeta.builder().name("auth").type("auth").extraProp("authorityName", "orders.read").build();
    assertAll(
        () -> assertFalse(new AuthAllowedFieldsNode(mockFlowModel(), meta).execute(List.of()).isSuccess()),
        () -> assertFalse(new AuthDisallowedFieldsNode(mockFlowModel(), meta).execute(List.of()).isSuccess()),
        () -> assertFalse(new AuthFilterNode(mockFlowModel(), meta).execute(List.of()).isSuccess()));
  }

  private static FlowModel mockFlowModel() {
    FlowModel flowModel = mock(FlowModel.class);
    when(flowModel.getConnections()).thenReturn(Collections.emptyList());
    return flowModel;
  }

  private static User buildUser(AccessControlParams params) {
    return new User() {
      @Override
      public String getUserIdentity() {
        return "user-1";
      }

      @Override
      public List<Role> getRoles() {
        return Collections.singletonList(buildRole(params));
      }
    };
  }

  private static Role buildRole(AccessControlParams params) {
    return new Role() {
      @Override
      public String getId() {
        return "role-1";
      }

      @Override
      public String getName() {
        return "role-1";
      }

      @Override
      public List<? extends OuroborosGrantedAuthority> getAuthorities() {
        return Collections.singletonList(buildGrantedAuthority(params));
      }
    };
  }

  private static OuroborosGrantedAuthority buildGrantedAuthority(AccessControlParams params) {
    return new OuroborosGrantedAuthority() {
      @Override
      public String getAuthority() {
        return "orders.read";
      }

      @Override
      public Authority getAuthorityObject() {
        return new Authority() {
          @Override
          public String getFullName() {
            return "orders.read";
          }

          @Override
          public String getTitle() {
            return "orders.read";
          }

          @Override
          public AccessControlDefine getAccessControlDefine() {
            return AccessControlDefine.empty();
          }

          @Override
          public List<String> getApis() {
            return Collections.singletonList("GET /api/orders");
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

      @Override
      public AccessControlParams getAccessControlParams() {
        return params;
      }
    };
  }

  private static AccessControlParams buildAccessControlParams(
      List<String> allowedFields,
      List<String> disallowedFields,
      Map<String, Object> filters) {
    return new AccessControlParams() {
      @Override
      public List<String> getAllowedFields() {
        return allowedFields;
      }

      @Override
      public List<String> getDisallowedFields() {
        return disallowedFields;
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
