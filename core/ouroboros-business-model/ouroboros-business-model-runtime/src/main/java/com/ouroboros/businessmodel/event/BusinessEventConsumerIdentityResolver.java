package com.ouroboros.businessmodel.event;

import java.util.Objects;
import java.util.Optional;
import com.ouroboros.security.rbac.UserCenter;

/** Resolves only explicitly assigned enabled accounts through the platform UserCenter. */
public final class BusinessEventConsumerIdentityResolver {
  private final BusinessEventConsumerProperties properties;
  private final UserCenter users;

  public BusinessEventConsumerIdentityResolver(BusinessEventConsumerProperties properties, UserCenter users) {
    this.properties = Objects.requireNonNull(properties);
    this.users = Objects.requireNonNull(users);
  }

  public Optional<com.ouroboros.security.rbac.User> resolve(String modelFullName) {
    String principal = properties.getPrincipals().get(modelFullName);
    return principal == null || principal.isBlank() ? Optional.empty()
        : users.getUser(principal).filter(user -> user.isEnabled());
  }
}
