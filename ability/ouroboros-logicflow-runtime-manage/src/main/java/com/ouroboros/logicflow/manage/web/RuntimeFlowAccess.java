package com.ouroboros.logicflow.manage.web;

import org.springframework.stereotype.Component;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.logicflow.manage.service.RuntimeFlowManagement.Access;
import com.ouroboros.security.authorize.AuthorizationDecision;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

/** Every endpoint checks the trusted identity, independently of menu and route filtering. */
@Component
public class RuntimeFlowAccess {
  public static final String READ = "systemSetting.logicflow.read";
  public static final String EDIT = "systemSetting.logicflow.edit";
  public static final String RELOAD = "systemSetting.logicflow.reload";
  public static final String OBSERVE_READ = "systemSetting.logicflow.observation.read";
  public static final String OBSERVE_MANAGE = "systemSetting.logicflow.observation.manage";

  public static boolean permits(String authority) {
    return CurrentSecurityContextHolder.findAuthentication().filter(value -> value.isAuthenticated())
        .map(value -> value.getAuthorizationDecision(authority) instanceof AuthorizationDecision.Permit).orElse(false);
  }

  public Access require(String authority) {
    var authentication = CurrentSecurityContextHolder.findAuthentication()
        .filter(value -> value.isAuthenticated())
        .orElseThrow(() -> new BusinessException(401, "请先登录"));
    authentication.getAuthorizationDecision(READ).requirePermit();
    if (!READ.equals(authority)) { authentication.getAuthorizationDecision(authority).requirePermit(); }
    return new Access(CurrentSecurityContextHolder.getRequiredIdentity().getUserIdentity(),
        authentication.getAuthorizationDecision(EDIT) instanceof AuthorizationDecision.Permit,
        authentication.getAuthorizationDecision(RELOAD) instanceof AuthorizationDecision.Permit);
  }
}
