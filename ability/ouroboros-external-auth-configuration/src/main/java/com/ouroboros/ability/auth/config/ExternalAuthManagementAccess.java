package com.ouroboros.ability.auth.config;

import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.identity.CredentialContext;

public final class ExternalAuthManagementAccess {
  public static final String CONFIGURE = "systemSetting.integrationManagement.connections.login.configure";
  public static final String SETTINGS = "systemSetting.integrationManagement.connections.login.settings";
  private ExternalAuthManagementAccess() { }

  public static boolean permits(String authority) {
    return CurrentSecurityContextHolder.findIdentity().filter(identity -> identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM)
        .filter(identity -> AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, authority)
            instanceof AuthorizationDecision.Permit).isPresent();
  }

  public static String authority(String group) {
    return switch (group) {
      case "ldap", "oauth2", "cas" -> CONFIGURE;
      case "settings" -> SETTINGS;
      default -> throw new com.ouroboros.exception.ValidationException("未知的外部登录配置分组");
    };
  }

  public static void require(String authority) {
    if (!permits(authority)) { throw new AuthorizationDeniedException("无权管理此外部登录配置"); }
  }
}
