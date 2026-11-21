package com.ouroboros.dev.app.service.runtime;

import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.service.AppEditAccessEvaluator;
import com.ouroboros.logicflow.dev.service.LogicFlowDebugAccess;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.exception.AuthorizationDeniedException;

@Component
public class TestAppLogicFlowDebugAccess implements LogicFlowDebugAccess {
  private final AuthenticationPrincipalTestAppProfileResolver profiles;
  private final AppEditAccessEvaluator access;

  public TestAppLogicFlowDebugAccess(AuthenticationPrincipalTestAppProfileResolver profiles, AppEditAccessEvaluator access) {
    this.profiles = profiles;
    this.access = access;
  }

  @Override
  public String currentOwner() {
    String owner = profiles.resolveCurrentUserIdentity();
    if (owner == null || owner.isBlank() || AnonymousAuthentication.getInstance().getPrincipal().equals(owner)) {
      throw new AuthorizationDeniedException("需要登录后才能调试");
    }
    return owner;
  }

  @Override
  public boolean canDebug(String appName, String profile, String owner) {
    return owner != null && !owner.isBlank() && !AnonymousAuthentication.getInstance().getPrincipal().equals(owner)
        && (profile == null || profiles.resolveProfileId(owner).equals(profile)) && access.canEdit(appName, owner);
  }
}
