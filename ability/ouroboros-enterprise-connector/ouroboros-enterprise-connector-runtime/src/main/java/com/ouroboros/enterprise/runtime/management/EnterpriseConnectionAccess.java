package com.ouroboros.enterprise.runtime.management;

import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.authorize.AuthorizationEvaluator;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.exception.AuthorizationDeniedException;
import com.ouroboros.security.identity.CredentialContext;

public final class EnterpriseConnectionAccess {
  private static final String PREFIX = "systemSetting.integrationManagement.connections.enterprise.";
  public static final String VIEW = PREFIX + "view";
  public static final String CONFIGURE = PREFIX + "configure";
  public static final String ROTATE = PREFIX + "rotate";
  public static final String DISABLE = PREFIX + "disable";
  public static final String VERIFY = PREFIX + "verify";
  private EnterpriseConnectionAccess() { }

  public static boolean permits(String authority) {
    return CurrentSecurityContextHolder.findIdentity()
        .filter(identity -> identity.getCredentialContext().kind() == CredentialContext.Kind.PLATFORM)
        .filter(identity -> AuthorizationEvaluator.standard().evaluate(identity, AuthorizationEvaluator.PLATFORM_API, authority)
            instanceof AuthorizationDecision.Permit).isPresent();
  }

  public static void require(String authority) {
    if (!permits(authority)) { throw new AuthorizationDeniedException("无权执行此企业连接操作"); }
  }

  public static void requireWriteAccess() {
    if (!permits(CONFIGURE) && !permits(ROTATE) && !permits(DISABLE)) {
      throw new AuthorizationDeniedException("无权修改企业连接");
    }
  }
}
