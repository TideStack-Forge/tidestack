package com.ouroboros.websocket;

import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

public class DefaultInitRequestValidator {
  private static final Pattern SUBSCRIPTION_TOKEN_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

  public void validate(InitRequest request, InitRequestContext context, InitRouteDefinition routeDefinition) {
    validateProtocol(request, context);
    validateRoute(routeDefinition);
  }

  public void validateProtocol(InitRequest request, InitRequestContext context) {
    if (request == null) {
      throw new IllegalArgumentException("init request must not be null");
    }
    if (context == null) {
      throw new IllegalArgumentException("init request context must not be null");
    }

    validateSessionId(context.getSessionId());
    validateSubscriptionToken(request.getSubscriptionToken());
  }

  public void validateRoute(InitRouteDefinition routeDefinition) {
    if (routeDefinition == null) {
      throw new IllegalArgumentException("init route definition must not be null");
    }

    validateDestination(routeDefinition.getRequestDestination(), "/app", "request destination");
    validateDestination(routeDefinition.getReplyDestination(), "/queue", "reply destination");
  }

  private void validateSessionId(String sessionId) {
    if (StringUtils.isBlank(sessionId)) {
      throw new IllegalArgumentException("sessionId must not be blank");
    }
  }

  private void validateSubscriptionToken(String subscriptionToken) {
    if (StringUtils.isBlank(subscriptionToken)) {
      throw new IllegalArgumentException("subscriptionToken must not be blank");
    }
    if (!SUBSCRIPTION_TOKEN_PATTERN.matcher(subscriptionToken).matches()) {
      throw new IllegalArgumentException("subscriptionToken must be path-safe");
    }
  }

  private void validateDestination(String destination, String namespacePrefix, String label) {
    if (StringUtils.isBlank(destination)) {
      throw new IllegalArgumentException(label + " must not be blank");
    }
    if (!destination.equals(namespacePrefix) && !destination.startsWith(namespacePrefix + "/")) {
      throw new IllegalArgumentException(label + " must stay within " + namespacePrefix + " namespace");
    }
    if (destination.contains("..") || destination.contains("//") || containsWhitespace(destination)) {
      throw new IllegalArgumentException(label + " must be path-safe");
    }
  }

  private boolean containsWhitespace(String value) {
    for (int i = 0; i < value.length(); i++) {
      if (Character.isWhitespace(value.charAt(i))) {
        return true;
      }
    }
    return false;
  }
}
