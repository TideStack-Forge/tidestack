package com.ouroboros.security.authentication;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

import io.vavr.Tuple;
import io.vavr.Tuple2;

import com.ouroboros.security.authority.accesscontrol.AccessControlParams;
import com.ouroboros.security.context.AuthorizationDecisionContextHolder;
import com.ouroboros.security.rbac.Role;
import com.ouroboros.security.rbac.User;

public class DefaultAuthentication implements OuroborosAuthentication {

  private final com.ouroboros.security.identity.SecurityIdentity identity;
  private final String platformSessionId;

  public DefaultAuthentication(User userDetail) {
    this(new com.ouroboros.security.identity.DefaultSecurityIdentity(userDetail));
  }

  public DefaultAuthentication(User userDetail, HttpServletRequest request) {
    this(new com.ouroboros.security.identity.DefaultSecurityIdentity(userDetail));
  }

  public DefaultAuthentication(com.ouroboros.security.identity.SecurityIdentity identity) {
    this(identity, null);
  }

  private DefaultAuthentication(com.ouroboros.security.identity.SecurityIdentity identity, String platformSessionId) {
    this.identity = java.util.Objects.requireNonNull(identity);
    this.platformSessionId = platformSessionId;
  }

  public static DefaultAuthentication platformToken(User user, String sessionId) {
    return new DefaultAuthentication(new com.ouroboros.security.identity.DefaultSecurityIdentity(user), sessionId);
  }

  @Override
  public Optional<String> getPlatformSessionId() { return Optional.ofNullable(platformSessionId); }

  @Override
  public com.ouroboros.security.identity.SecurityIdentity getIdentity() { return identity; }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getDetails() {
    return identity.getUserDetail();
  }

  @Override
  public String getPrincipal() {
    return identity.getUserIdentity();
  }

  @Override
  public boolean isAuthenticated() {
    return true;
  }

  @Override
  public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
  }

  @Override
  public String getName() {
    return identity.getUserIdentity();
  }

  public com.ouroboros.security.authorize.AuthorizationDecision getAuthorizationDecision() {
    var target = findMethodAndPathForDecision();
    if (target.isEmpty()) {
      return new com.ouroboros.security.authorize.AuthorizationDecision.Deny(
          com.ouroboros.security.authorize.AuthorizationDecision.Reason.MISSING_DECISION_TARGET);
    }
    var names = com.ouroboros.security.authority.AuthorityCenter.getAllAuthority().values().stream()
        .filter(authority -> anyMatchMethodAndPath(authority.getApis(), target.get()))
        .map(authority -> authority.getFullName()).toList();
    return com.ouroboros.security.authorize.AuthorizationEvaluator.standard().evaluateRequest(identity,
        com.ouroboros.security.authorize.AuthorizationEvaluator.PLATFORM_API, names);
  }

  @Override
  public AccessControlParams getAccessControlParams() {
    return getAuthorizationDecision().requirePermit();
  }

  private Optional<Tuple2<String, String>> findMethodAndPathForDecision() {
    return AuthorizationDecisionContextHolder.findCurrentRequest()
        .map(currentRequest -> Tuple.of(currentRequest.getMethod(), currentRequest.getPath()));
  }

  private boolean anyMatchMethodAndPath(Collection<String> apis, Tuple2<String, String> methodAndPath) {
    return apis.stream().anyMatch(api -> {
      var method = api.substring(0, api.indexOf(" "));
      var path = api.substring(api.indexOf(" ") + 1);
      return method.equalsIgnoreCase(methodAndPath._1)
          && new PathPatternParser().parse(path).matches(PathContainer.parsePath(methodAndPath._2));
    });
  }

  public String getUserIdentity() {
    return identity.getUserIdentity();
  }

  @Override
  public User getUserDetail() {
    return identity.getUserDetail();
  }

  @Override
  public List<? extends Role> getRoles() {
    return identity.getRoles();
  }
}
