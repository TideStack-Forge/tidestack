package com.ouroboros.notification.dingtalk;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;

class RetiredDingtalkNotificationConfigurationPolicyTest {
  private final RetiredDingtalkNotificationConfigurationPolicy policy = new RetiredDingtalkNotificationConfigurationPolicy();
  private final ConfigurationManagementContext context = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);

  @Test void oldSecretsAreHiddenAndCanOnlyBeClearedWithTheDedicatedRotationPermission() {
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("actual-admin");
    var identity = new DefaultSecurityIdentity(user);
    var evaluator = mock(AuthorizationEvaluator.class);
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, EnterpriseConnectionAccess.ROTATE))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
    try (var standard = mockStatic(AuthorizationEvaluator.class)) {
      standard.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
      CurrentSecurityContextHolder.withIdentity(identity, () -> {
        assertThat(policy.projectRead(context, snapshot())).isEmpty();
        assertThatThrownBy(() -> policy.prepareWrite(context, snapshot(), new ConfigurationMutation.Replace("other-secret"), Map.of()))
            .hasMessageContaining("旧配置只允许清除");
        var cleared = policy.prepareWrite(context, snapshot(), ConfigurationMutation.RestoreDefaults.INSTANCE, Map.of());
        assertThat(cleared.value()).isNull();
        assertThat(cleared.auditActor()).isEqualTo("actual-admin");
        return null;
      });
    }
  }

  @Test void anOperatorNameDoesNotGrantCredentialCleanupAndScriptReadsStayHidden() {
    CurrentSecurityContextHolder.withContext(() -> Optional.empty(), () -> {
      assertThat(policy.projectRead(context, snapshot())).isEmpty();
      assertThat(policy.projectRead(new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.SCRIPT), snapshot())).isEmpty();
      assertThatThrownBy(() -> policy.prepareWrite(context, snapshot(), ConfigurationMutation.RestoreDefaults.INSTANCE, Map.of()))
          .hasMessageContaining("无权");
      return null;
    });
  }

  private ConfigurationSnapshot snapshot() {
    var metadata = new ConfigurationMetadata(); metadata.setKey("notification.dingTalk.appSecret");
    return new ConfigurationSnapshot(metadata.getKey(), "old-secret", "old-secret", "17", metadata);
  }
}
