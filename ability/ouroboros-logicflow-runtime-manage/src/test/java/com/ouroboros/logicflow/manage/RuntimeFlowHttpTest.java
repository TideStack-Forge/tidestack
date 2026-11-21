package com.ouroboros.logicflow.manage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ouroboros.logicflow.manage.web.*;
import com.ouroboros.logicflow.model.FlowModelDefinitions;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.context.CurrentSecurityContext;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.SecurityIdentity;
import com.ouroboros.util.Json;

class RuntimeFlowHttpTest {
  @TempDir Path home;
  static final String BASE = "/api/runtime/logicflows";

  static MockMvc mvc(RuntimeFlowFixture fixture) {
    var access = new RuntimeFlowAccess();
    return MockMvcBuilders.standaloneSetup(new RuntimeFlowController(fixture.service, fixture.store, access),
        new RuntimeFlowCatalogController(access), new FlowObservationController(fixture.observation.service, access)).setControllerAdvice(new RuntimeFlowHttpErrors()).build();
  }

  static CurrentSecurityContext identity(String actor, Set<String> permissions) {
    var auth = mock(OuroborosAuthentication.class);
    var identity = mock(SecurityIdentity.class);
    when(identity.getUserIdentity()).thenReturn(actor);
    when(identity.getCredentialContext()).thenReturn(com.ouroboros.security.identity.CredentialContext.platform());
    when(auth.isAuthenticated()).thenReturn(true);
    when(auth.getAuthorizationDecision(anyString())).thenAnswer(call -> permissions.contains(call.getArgument(0))
        ? new AuthorizationDecision.Permit(AccessControlParams.empty())
        : new AuthorizationDecision.Deny(AuthorizationDecision.Reason.NOT_GRANTED));
    return new CurrentSecurityContext() {
      @Override public Optional<SecurityIdentity> findIdentity() { return Optional.of(identity); }
      @Override public Optional<OuroborosAuthentication> findAuthentication() { return Optional.of(auth); }
    };
  }

  @Test void anonymousReaderEditorAndReloadPermissionsAreIndependent() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var mvc = mvc(f);
      mvc.perform(get(BASE + "/models")).andExpect(status().isUnauthorized());
      var reader = identity("reader", Set.of(RuntimeFlowAccess.READ));
      CurrentSecurityContextHolder.withContextThrowing(reader, () -> {
        mvc.perform(get(BASE + "/models")).andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.data.total").value(3));
        mvc.perform(post(BASE + "/apply").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(put(BASE + "/models/child/override").contentType("application/json").content("{}"))
            .andExpect(status().isForbidden());
        mvc.perform(get(BASE + "/catalog/references/flow")).andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(3));
        mvc.perform(get(BASE + "/catalog/node-definitions")).andExpect(status().isOk());
        return null;
      });
      var reloader = identity("reload", Set.of(RuntimeFlowAccess.READ, RuntimeFlowAccess.RELOAD));
      CurrentSecurityContextHolder.withContextThrowing(reloader, () -> {
        mvc.perform(post(BASE + "/apply").contentType("application/json")
            .content(Json.toJsonString(Map.of("expectedCatalogVersion", f.store.read().version().token()))))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.status").value("pending"));
        return null;
      });
      assertEquals(0, f.store.read().version().revision());
    }
  }

  @Test void writesReturnRealHttpStatusesReplayAndRejectUnknownCommands() throws Exception {
    try (var f = new RuntimeFlowFixture(home)) {
      var mvc = mvc(f);
      CurrentSecurityContextHolder.withContextThrowing(identity("editor", Set.of(RuntimeFlowAccess.READ, RuntimeFlowAccess.EDIT)), () -> {
        String token = f.service.read("child", RuntimeFlowManagementTest.EDITOR).editToken();
        String id = UUID.randomUUID().toString();
        var body = Map.of("operationId", id, "editToken", token,
            "definition", Json.tryToMap(FlowModelDefinitions.write(RuntimeFlowFixture.constant("child", "HTTP override"))).get());
        mvc.perform(put(BASE + "/models/child/override").contentType("application/json").content(Json.toJsonString(body)))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.actor").value("editor"));
        mvc.perform(put(BASE + "/models/child/override").contentType("application/json").content(Json.toJsonString(body)))
            .andExpect(status().isAccepted()).andExpect(jsonPath("$.data.operationId").value(id));
        mvc.perform(get(BASE + "/operations/" + id)).andExpect(status().isOk());
        var stale = new java.util.HashMap<>(body); stale.put("operationId", UUID.randomUUID().toString());
        mvc.perform(put(BASE + "/models/child/override").contentType("application/json").content(Json.toJsonString(stale)))
            .andExpect(status().isConflict());
        mvc.perform(post(BASE + "/models/child/revoke").contentType("application/json").content("{\"actor\":\"spoof\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post(BASE + "/models/child/validate").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/models/not-found")).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/catalog/references/arbitrary")).andExpect(status().isBadRequest());
        mvc.perform(get(BASE + "/catalog/references/mq-binding?bindingType=bad")).andExpect(status().isBadRequest());
        for (String kind : List.of("data-model", "configuration", "rpc-model", "notification-template", "mq-binding")) {
          mvc.perform(get(BASE + "/catalog/references/" + kind)).andExpect(status().isOk());
        }
        mvc.perform(get(BASE + "/instances")).andExpect(status().isOk());
        return null;
      });
    }
  }
}
