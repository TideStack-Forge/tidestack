package com.ouroboros.configuration.profile;

import com.ouroboros.security.profile.UserProfilePolicySource;
import com.ouroboros.security.profile.UserProfilePolicy;
import com.ouroboros.security.profile.ContactVerificationDelivery;
import com.ouroboros.security.profile.ContactVerificationProof;

import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import com.ouroboros.configuration.ConfigurationService;
import com.ouroboros.configuration.ConfigurationSnapshot;
import com.ouroboros.configuration.management.ConfigurationManagementAccessPolicy;
import com.ouroboros.configuration.management.ConfigurationManagementContext;
import com.ouroboros.configuration.management.ConfigurationMutation;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.identity.CredentialContext;
import com.ouroboros.util.Json;

@Component
public final class ConfigurationUserProfilePolicySource implements UserProfilePolicySource, ConfigurationManagementAccessPolicy {
  public static final String KEY = "security.user.profile";
  public static final String AUTHORITY = "systemSetting.userProfile.configure";
  private final ConfigurationService configuration;
  private final List<ContactVerificationDelivery> delivery;
  private final org.springframework.beans.factory.ObjectProvider<ContactVerificationProof> proofs;

  public ConfigurationUserProfilePolicySource(ConfigurationService configuration,
      org.springframework.beans.factory.ObjectProvider<ContactVerificationDelivery> delivery,
      org.springframework.beans.factory.ObjectProvider<ContactVerificationProof> proofs) {
    this.configuration = configuration;
    this.proofs = proofs;
    this.delivery = delivery.orderedStream().toList();
  }

  public Snapshot current() {
    var snapshot = configuration.getSnapshot(KEY).orElseThrow(() -> new BusinessException(503, "资料规则不可用"));
    return new Snapshot(snapshot.revision() + ":protection-v2", parse(snapshot.value()));
  }

  private UserProfilePolicy parse(Object value) {
    return Json.tryToBean(UserProfilePolicy.class, Json.toJsonString(value))
        .getOrElseThrow(error -> new BusinessException(503, "[PROFILE_POLICY_INVALID] 用户资料规则无效", error));
  }

  public boolean supports(String key) { return KEY.equals(key); }

  private boolean allowed(ConfigurationManagementContext context) {
    var identity = com.ouroboros.security.context.CurrentSecurityContextHolder.findIdentity().orElse(null);
    return identity != null
        && identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM
        && AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, AUTHORITY)
            instanceof AuthorizationDecision.Permit;
  }

  public Optional<ConfigurationSnapshot> projectRead(ConfigurationManagementContext context, ConfigurationSnapshot snapshot) {
    return allowed(context) ? Optional.of(snapshot) : Optional.empty();
  }

  public java.util.Set<String> writeDependencies(String key, ConfigurationMutation mutation) { return java.util.Set.of(); }

  public com.ouroboros.configuration.management.ConfigurationPreparedWrite prepareWrite(ConfigurationManagementContext context,
      ConfigurationSnapshot old, ConfigurationMutation mutation, java.util.Map<String, ConfigurationSnapshot> dependencies) {
    if (!allowed(context)) { throw new BusinessException(403, "[CONFIGURATION_ACCESS_DENIED] 无权管理用户资料规则"); }
    Object value;
    if (mutation instanceof ConfigurationMutation.Replace replace) { value = replace.value(); }
    else if (mutation instanceof ConfigurationMutation.RestoreDefaults) { value = UserProfilePolicy.defaults(); }
    else { throw new BusinessException(400, "[PROFILE_INVALID] 不支持的规则修改"); }
    UserProfilePolicy policy = parse(value);
    UserProfilePolicy previous = mutation instanceof ConfigurationMutation.RestoreDefaults ? policy : parse(old.value());
    for (var field : policy.fields()) {
      previous.fields().stream().filter(f -> f.name().equals(field.name())).findFirst().ifPresent(existing -> {
        if (!existing.type().equals(field.type())) { throw new BusinessException(400, "资料字段类型变更需要显式迁移"); }
      });
    }
    policy.verification().forEach((kind, mode) -> {
      if (mode.equals("REQUIRED") && (delivery.stream().noneMatch(adapter -> adapter.supports(kind))
          || proofs.stream().noneMatch(ContactVerificationProof::ready))) {
        throw new BusinessException(400, "[CONTACT_DELIVERY_UNAVAILABLE] 对应联系方式投递能力尚未就绪");
      }
    });
    return new com.ouroboros.configuration.management.ConfigurationPreparedWrite(policy, context.userIdentity());
  }
}
