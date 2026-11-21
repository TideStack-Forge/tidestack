package com.ouroboros.notification.dingtalk;

import java.util.*;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.*;
import com.ouroboros.enterprise.runtime.management.EnterpriseConnectionAccess;
import com.ouroboros.exception.ValidationException;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

/** Historical rows remain available for controlled cleanup; they no longer configure a notification channel. */
public final class RetiredDingtalkNotificationConfigurationPolicy implements ConfigurationManagementAccessPolicy {
  @Override public boolean supports(String key) {
    return Set.of("notification.dingTalk.appKey", "notification.dingTalk.appSecret").contains(key);
  }
  @Override public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    return Optional.empty();
  }
  @Override public Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return Set.of(); }
  @Override public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot old,
      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    EnterpriseConnectionAccess.require(EnterpriseConnectionAccess.ROTATE);
    if (mutation != ConfigurationMutation.RestoreDefaults.INSTANCE) {
      throw new ValidationException("钉钉通知凭据已迁入集成管理，旧配置只允许清除");
    }
    return new ConfigurationPreparedWrite(null, CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity());
  }
}
