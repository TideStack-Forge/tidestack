package com.ouroboros.dev.app.service.runtime;

import org.apache.commons.lang3.StringUtils;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.ouroboros.dev.app.model.AppContainerRequest;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.security.authentication.AnonymousAuthentication;
import com.ouroboros.security.context.CurrentSecurityContextHolder;

@Component
public class AuthenticationPrincipalTestAppProfileResolver implements TestAppProfileResolver {
  private static final String INVALID_PROFILE_CHARACTER_PATTERN = "[^A-Za-z0-9_-]";

  @Override
  public String resolveForCreate(AppContainerRequest request) {
    return resolveProfileId(resolveCurrentUserIdentity());
  }

  public String resolveCurrentUserIdentity() {
    return currentUserIdentity();
  }

  public static String currentUserIdentity() {
    Authentication springAuthentication = SecurityContextHolder.getContext().getAuthentication();
    if (springAuthentication != null && springAuthentication.isAuthenticated()
        && !(springAuthentication instanceof AnonymousAuthenticationToken) && StringUtils.isNotBlank(springAuthentication.getName())) {
      return springAuthentication.getName();
    }
    return CurrentSecurityContextHolder.findAuthentication()
        .filter(currentAuthentication -> currentAuthentication.isAuthenticated())
        .map(currentAuthentication -> currentAuthentication.getPrincipal())
        .orElseGet(() -> AnonymousAuthentication.getInstance().getPrincipal());
  }

  public String resolveProfileId(String userIdentity) {
    if (StringUtils.isBlank(userIdentity)) {
      throw new BusinessException("userIdentity 不能为空");
    }
    return toProfileName(StringUtils.trim(userIdentity));
  }

  private String toProfileName(String principal) {
    // 当前 profile 需要作为 Spring profile、目录名和 MQ topic routing key 片段使用。
    // 后续如果需要保留原始用户标识，可改为 base64url 或 percent-encoding 等可逆编码。
    return principal.replaceAll(INVALID_PROFILE_CHARACTER_PATTERN, "_");
  }
}
