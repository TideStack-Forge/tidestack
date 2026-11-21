package com.ouroboros.configuration.profile;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.DefaultSecurityIdentity;
import com.ouroboros.security.profile.ContactVerificationDelivery;
import com.ouroboros.security.profile.ContactVerificationProof;
import com.ouroboros.security.profile.UserProfilePolicy;
import com.ouroboros.security.rbac.User;

class ConfigurationUserProfilePolicySourceTest {
  @AfterEach
  void clearIdentity() { CurrentSecurityContextHolder.clearDelegate(); }

  @Test
  void authorizedResetCanRecoverAnObsoletePolicyButStillValidatesNewWrites() {
    var beans = new StaticListableBeanFactory();
    var source = new ConfigurationUserProfilePolicySource(mock(ConfigurationService.class),
        beans.getBeanProvider(ContactVerificationDelivery.class), beans.getBeanProvider(ContactVerificationProof.class));
    var old = new ConfigurationSnapshot(ConfigurationUserProfilePolicySource.KEY, Map.of("obsolete", true),
        null, "old", null);
    var context = new ConfigurationManagementContext("admin", ConfigurationManagementContext.Purpose.MANAGEMENT);
    var identity = new DefaultSecurityIdentity(mock(User.class));
    CurrentSecurityContextHolder.setDelegate(() -> Optional.of(identity));
    var evaluator = mock(AuthorizationEvaluator.class);
    when(evaluator.evaluate(identity, AuthorizationEvaluator.PLATFORM_API, ConfigurationUserProfilePolicySource.AUTHORITY))
        .thenReturn(new AuthorizationDecision.Permit(AccessControlParams.empty()));
    try (var standard = mockStatic(AuthorizationEvaluator.class)) {
      standard.when(AuthorizationEvaluator::standard).thenReturn(evaluator);
      var reset = assertInstanceOf(UserProfilePolicy.class,
          source.prepareWrite(context, old, ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value());
      assertFalse(reset.field("nickname").editableBy().isEmpty());
      assertTrue(reset.field("formalName").editableBy().isEmpty());
      assertTrue(reset.field("displayName").editableBy().isEmpty());
      assertThrows(BusinessException.class,
          () -> source.prepareWrite(context, old, new ConfigurationMutation.Replace(Map.of("invalid", true)), java.util.Map.of()).value());
      CurrentSecurityContextHolder.clearDelegate();
      var denied = assertThrows(BusinessException.class,
          () -> source.prepareWrite(context, old, ConfigurationMutation.RestoreDefaults.INSTANCE, java.util.Map.of()).value());
      assertTrue(denied.getMessage().contains("CONFIGURATION_ACCESS_DENIED"));
    }
  }
}
