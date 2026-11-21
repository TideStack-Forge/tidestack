package com.ouroboros.security.rbac.profile;

import com.ouroboros.security.rbac.profile.service.UserProfileService;

import java.time.LocalDateTime;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authentication.OuroborosAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;
import com.ouroboros.security.session.UserSessionManager;

@Component
final class ProfileSession {
  private final ObjectProvider<UserSessionManager<?>> sessions;
  ProfileSession(ObjectProvider<UserSessionManager<?>> sessions) { this.sessions = sessions; }

  String require() {
    String identity = UserProfileService.self();
    String id = CurrentSecurityContextHolder.findAuthentication().flatMap(OuroborosAuthentication::getPlatformSessionId)
        .orElseThrow(ProfileSession::unavailable);
    var manager = sessions.getIfAvailable();
    if (manager == null || manager.getSession(id).filter(value -> value.getUser().equals(identity))
        .filter(value -> value.getExpiresAt() != null && value.getExpiresAt().isAfter(LocalDateTime.now())).isEmpty()) { throw unavailable(); }
    return id;
  }

  private static BusinessException unavailable() { return new BusinessException(401, "[PROFILE_IDENTITY_NOT_ALLOWED] 平台会话已失效"); }
}
