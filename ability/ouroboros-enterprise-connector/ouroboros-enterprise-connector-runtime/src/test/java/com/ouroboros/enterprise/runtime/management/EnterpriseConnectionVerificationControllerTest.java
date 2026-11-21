package com.ouroboros.enterprise.runtime.management;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ouroboros.enterprise.EnterpriseCredential;
import com.ouroboros.enterprise.ResolvedEnterpriseConnection;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.enterprise.token.EnterpriseApplicationTokens;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;
import com.ouroboros.util.Json;

class EnterpriseConnectionVerificationControllerTest {
  private final EnterpriseConnectionCatalog connections = mock(EnterpriseConnectionCatalog.class);
  private final EnterpriseApplicationTokens tokens = mock(EnterpriseApplicationTokens.class);
  private final AuthorizationEvaluator evaluator = mock(AuthorizationEvaluator.class);
  private final DefaultSecurityIdentity identity = new DefaultSecurityIdentity(mock(User.class));
  private final ResolvedEnterpriseConnection connection = new ResolvedEnterpriseConnection("main",
      Map.of("provider", "test", "revision", "v1"), new Credential("stored-secret"));
  private EnterpriseConnectionVerificationController controller;
  private MockedStatic<AuthorizationEvaluator> authorization;

  @BeforeEach void setUp() {
    authorization = mockStatic(AuthorizationEvaluator.class);
    authorization.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
    when(tokens.provider()).thenReturn("test");
    when(connections.resolve("main")).thenReturn(Optional.of(connection));
    permit(EnterpriseConnectionAccess.VIEW); permit(EnterpriseConnectionAccess.VERIFY);
    controller = new EnterpriseConnectionVerificationController(connections, List.of(tokens), 1000);
  }

  @AfterEach void tearDown() { authorization.close(); }

  @Test void returnsOnlySafeFreshCheckFactsWithoutChangingConnectionOrCache() throws Exception {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      var response = new MockHttpServletResponse();
      var result = controller.verify("main", response).getData();
      assertThat(result).containsEntry("connectionId", "main").containsEntry("provider", "test");
      assertThat(result.get("checkedAt")).isNotNull();
      assertThat(Json.toJsonString(result)).doesNotContain("stored-secret");
      assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
      return null;
    });
    verify(tokens).verifyConnection(eq(connection), any());
    verify(tokens, never()).accessToken(any(), any());
    verify(tokens, never()).invalidate(any(), any());
    verify(connections, times(2)).resolve("main");
  }

  @Test void configurationOrReadAccessCannotPerformTheRemoteCheck() {
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, EnterpriseConnectionAccess.VERIFY)).thenReturn(null);
    permit(EnterpriseConnectionAccess.CONFIGURE);
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.verify("main", new MockHttpServletResponse())).hasMessageContaining("无权");
      return null;
    });
    verifyNoInteractions(connections); verify(tokens).provider(); verifyNoMoreInteractions(tokens);
  }

  @Test void missingOrDisabledConnectionsCannotStartAnExchange() throws Exception {
    when(connections.resolve("main")).thenThrow(new com.ouroboros.exception.ValidationException("连接已停用"));
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.verify("main", new MockHttpServletResponse())).hasMessageContaining("停用");
      doReturn(Optional.empty()).when(connections).resolve("main");
      assertThatThrownBy(() -> controller.verify("main", new MockHttpServletResponse())).hasMessageContaining("不存在");
      return null;
    });
    verify(tokens, never()).verifyConnection(any(), any());
  }

  @Test void aConcurrentRotationCannotBeReportedAsVerificationOfTheNewCredential() {
    var replacement = new ResolvedEnterpriseConnection("main", Map.of("provider", "test", "revision", "v2"), new Credential("new-secret"));
    when(connections.resolve("main")).thenReturn(Optional.of(connection), Optional.of(replacement));
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.verify("main", new MockHttpServletResponse())).hasMessageContaining("已改变");
      return null;
    });
  }

  @Test void providerFailuresAreHandledWithoutReturningSecretBearingMessagesOrCauses() throws Exception {
    doThrow(new IllegalStateException("corpsecret=stored-secret; access_token=returned-secret"))
        .when(tokens).verifyConnection(any(), any());
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.verify("main", new MockHttpServletResponse()))
          .hasMessageContaining("CHECK_FAILED").hasMessageNotContaining("stored-secret").hasMessageNotContaining("returned-secret").hasNoCause();
      return null;
    });
    verify(connections).resolve("main");
  }

  @Test void absentTokenModulesCannotReportSuccessAndInvalidAssemblyFailsEarly() {
    var missing = new EnterpriseConnectionVerificationController(connections, List.of(), 1000);
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> missing.verify("main", new MockHttpServletResponse())).hasMessageContaining("未安装");
      return null;
    });
    assertThatThrownBy(() -> new EnterpriseConnectionVerificationController(connections, List.of(tokens), 0))
        .hasMessageContaining("超时");
    assertThatThrownBy(() -> new EnterpriseConnectionVerificationController(connections, List.of(tokens, tokens), 1000))
        .isInstanceOf(IllegalStateException.class);
  }

  private void permit(String authority) {
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, authority))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
  }

  private record Credential(String secret) implements EnterpriseCredential {
    @Override public Map<String, String> applicationIdentity() { return Map.of("app", "test"); }
  }
}
