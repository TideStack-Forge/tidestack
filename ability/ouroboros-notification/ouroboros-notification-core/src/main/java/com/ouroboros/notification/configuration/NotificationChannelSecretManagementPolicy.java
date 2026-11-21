package com.ouroboros.notification.configuration;

import java.util.Optional;
import java.util.Map;
import java.util.Set;

import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.configuration.management.ConfigurationPreparedWrite;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.identity.CredentialContext;

/** The original configuration service owns reads, CAS writes and restore commands. */
public class NotificationChannelSecretManagementPolicy implements ConfigurationManagementAccessPolicy {
  private final String key;

  public NotificationChannelSecretManagementPolicy(String key) { this.key = key; }

  @Override
  public boolean supports(String key) { return this.key.equals(key); }

  @Override
  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    if (!allowed(context)) { return Optional.empty(); }
    var metadata = snapshot.metadata();
    metadata.setDefaultValue(null);
    metadata.setExtraProp("secretConfigured", snapshot.value() instanceof String text && !text.isBlank());
    return Optional.of(new ConfigurationSnapshot(snapshot.key(), null, null, snapshot.revision(), metadata));
  }

  @Override
  public Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return Set.of(); }

  @Override
  public ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context, ConfigurationSnapshot old,
      ConfigurationMutation mutation, Map<String, ConfigurationSnapshot> dependencies) {
    if (!allowed(context)) { throw new BusinessException(403, "[CONFIGURATION_ACCESS_DENIED] 无权管理通知渠道凭据"); }
    String actor = CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity();
    if (mutation == ConfigurationMutation.RestoreDefaults.INSTANCE) { return new ConfigurationPreparedWrite(old.metadata().getDefaultValue(), actor); }
    if (mutation instanceof ConfigurationMutation.Replace replace && replace.value() instanceof String text && !text.isBlank()) {
      return new ConfigurationPreparedWrite(text, actor);
    }
    throw new BusinessException(400, "[NOTIFICATION_SECRET_INVALID] 替换凭据必须提供非空字符串；不提交表示保留，清除覆盖请恢复默认");
  }

  private boolean allowed(ConfigurationManagementContext context) {
    var identity = CurrentSecurityContextHolder.findIdentity().orElse(null);
    return context.authenticated() && context.purpose() == ConfigurationManagementContext.Purpose.MANAGEMENT
        && identity != null && identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM
        && AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, "systemSetting.configurationManagement")
            instanceof AuthorizationDecision.Permit;
  }
}
