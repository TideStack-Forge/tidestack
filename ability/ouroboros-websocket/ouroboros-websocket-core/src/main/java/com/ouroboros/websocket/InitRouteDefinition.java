package com.ouroboros.websocket;

public final class InitRouteDefinition {
  private final String requestDestination;
  private final String replyDestination;
  private final String subscriptionToken;

  public InitRouteDefinition(String requestDestination, String replyDestination, String subscriptionToken) {
    this.requestDestination = requestDestination;
    this.replyDestination = replyDestination;
    this.subscriptionToken = subscriptionToken;
  }

  public String getRequestDestination() {
    return requestDestination;
  }

  public String getReplyDestination() {
    return replyDestination;
  }

  public String getSubscriptionToken() {
    return subscriptionToken;
  }
}
