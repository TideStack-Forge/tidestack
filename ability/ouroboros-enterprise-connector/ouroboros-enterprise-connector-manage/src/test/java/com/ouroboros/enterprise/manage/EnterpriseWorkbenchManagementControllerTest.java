package com.ouroboros.enterprise.manage;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import com.ouroboros.enterprise.EnterpriseConnectionSnapshot;
import com.ouroboros.enterprise.runtime.EnterpriseConnectionCatalog;
import com.ouroboros.security.authorize.*;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.externalauth.ExternalAuthProviderInfoService;
import com.ouroboros.security.externalauth.ExternalAuthProviderInfoService.ProviderInfo;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;

class EnterpriseWorkbenchManagementControllerTest {
  private final EnterpriseConnectionCatalog connections = mock(EnterpriseConnectionCatalog.class);
  private final ExternalAuthProviderInfoService auth = mock(ExternalAuthProviderInfoService.class);
  private final EnterpriseWorkbenchManagementController controller = new EnterpriseWorkbenchManagementController(connections, auth);
  private final AuthorizationEvaluator evaluator = mock(AuthorizationEvaluator.class);
  private final User user = mock(User.class);
  private final DefaultSecurityIdentity identity = new DefaultSecurityIdentity(user);
  private MockedStatic<AuthorizationEvaluator> authorization;

  @BeforeEach void setup() {
    when(user.getUserIdentity()).thenReturn("admin");
    authorization = mockStatic(AuthorizationEvaluator.class); authorization.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
    permit(EnterpriseWorkbenchManagementController.VIEW); permit(EnterpriseWorkbenchManagementController.CONFIGURE);
    when(connections.listSummaries()).thenReturn(List.of(new EnterpriseConnectionCatalog.ConnectionSummary("feishu-main", "feishu")));
    when(connections.requireActive("feishu-main")).thenReturn(new EnterpriseConnectionSnapshot("feishu", "feishu-main", "1", "f", "managed:feishu-main", java.util.Map.of(), true, true));
    var info = new ProviderInfo(); info.setKey("feishu-main"); info.setLoginUrl("/api/auth/oauth2/feishu-main/authorize"); info.setAvailability("healthy");
    when(auth.listProviders()).thenReturn(List.of(info));
  }

  @AfterEach void cleanup() { authorization.close(); }

  @Test void generatesAConnectionScopedInternalEntryAndKeepsTheTargetPath() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      var result = controller.entryUrl("feishu-main", "/orders/list?status=open").getData();
      assertThat(result.get("entryUrl")).isEqualTo("/api/auth/oauth2/feishu-main/authorize?returnUrl=%2Forders%2Flist%3Fstatus%3Dopen");
      assertThat(result.get("connectionId")).isEqualTo("feishu-main");
      return null;
    });
  }

  @Test void rejectsExternalTargetsAndInactiveOrMissingAuthProfiles() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      assertThatThrownBy(() -> controller.entryUrl("feishu-main", "https://evil.example/steal")).hasMessageContaining("站内路径");
      when(auth.listProviders()).thenReturn(List.of());
      assertThatThrownBy(() -> controller.entryUrl("feishu-main", "/orders")).hasMessageContaining("登录入口");
      return null;
    });
  }

  @Test void aDisabledConnectionCannotProduceAnEntry() {
    CurrentSecurityContextHolder.withIdentity(identity, () -> {
      doThrow(new com.ouroboros.exception.ValidationException("连接已停用")).when(connections).requireActive("feishu-main");
      assertThatThrownBy(() -> controller.entryUrl("feishu-main", "/orders")).hasMessageContaining("停用");
      return null;
    });
  }

  private void permit(String authority) {
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, authority))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
  }
}
