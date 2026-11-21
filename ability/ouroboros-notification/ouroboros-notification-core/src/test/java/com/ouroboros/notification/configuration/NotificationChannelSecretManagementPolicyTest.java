package com.ouroboros.notification.configuration;

import java.util.Optional;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.metadata.ConfigurationMetadata;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.rbac.User;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class NotificationChannelSecretManagementPolicyTest {
  private static final String KEY = "notification.email.authorizationCode";
  private final NotificationChannelSecretManagementPolicy policy = new NotificationChannelSecretManagementPolicy(KEY);
  private final ConfigurationManagementContext context = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);

  @Test
  void managementReadsExposeOnlyConfiguredStateAndRevision() {
    var user = mock(User.class);
    when(user.getUserIdentity()).thenReturn("actual-admin");
    var identity = new DefaultSecurityIdentity(user);
    var evaluator = mock(AuthorizationEvaluator.class);
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, "systemSetting.configurationManagement"))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
    try (var standard = mockStatic(AuthorizationEvaluator.class)) {
      standard.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
      CurrentSecurityContextHolder.withIdentity(identity, () -> {
        var projected = policy.projectRead(context, snapshot()).orElseThrow();
        assertNull(projected.value()); assertNull(projected.storedRawValue()); assertNull(projected.metadata().getDefaultValue());
        assertEquals(true, projected.metadata().getExtraProp("secretConfigured").orElseThrow()); assertEquals("17", projected.revision());
        String json = com.ouroboros.util.Json.toJsonString(projected);
        assertFalse(json.contains("stored-test-secret")); assertFalse(json.contains("default-test-secret"));
        var replaced = policy.prepareWrite(context, snapshot(), new ConfigurationMutation.Replace("replacement"), Map.of());
        assertEquals("replacement", replaced.value());
        assertEquals("actual-admin", replaced.auditActor());
        var restored = policy.prepareWrite(context, snapshot(), ConfigurationMutation.RestoreDefaults.INSTANCE, Map.of());
        assertEquals("default-test-secret", restored.value());
        assertEquals("actual-admin", restored.auditActor());
        assertThrows(BusinessException.class, () -> policy.prepareWrite(context, snapshot(), new ConfigurationMutation.Replace(" "), Map.of()));
        return null;
      });
    }
  }

  @Test
  void missingIdentityCannotReadOrChangeSecretsEvenWithAnOperatorString() {
    CurrentSecurityContextHolder.withContext(() -> Optional.empty(), () -> {
      assertTrue(policy.projectRead(context, snapshot()).isEmpty());
      assertEquals(403, assertThrows(BusinessException.class,
          () -> policy.prepareWrite(context, snapshot(), new ConfigurationMutation.Replace("replacement"), Map.of())).getErrorCode());
      return null;
    });
    assertTrue(policy.supports(KEY)); assertFalse(policy.supports("unrelated.password"));
  }

  private ConfigurationSnapshot snapshot() {
    var metadata = new ConfigurationMetadata(); metadata.setKey(KEY); metadata.setDefaultValue("default-test-secret");
    return new ConfigurationSnapshot(KEY, "stored-test-secret", "stored-test-secret", "17", metadata);
  }
}
