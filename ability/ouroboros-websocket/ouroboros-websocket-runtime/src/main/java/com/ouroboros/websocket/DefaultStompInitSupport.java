package com.ouroboros.websocket;

import java.util.LinkedHashMap;
import java.util.Map;

public class DefaultStompInitSupport implements StompInitSupport {

  private final DefaultInitRequestValidator initRequestValidator;

  public DefaultStompInitSupport(DefaultInitRequestValidator initRequestValidator) {
    this.initRequestValidator = initRequestValidator;
  }

  public <T extends InitRequest> void init(
      T request,
      InitRequestContext context,
      InitRequestHandler<T> handler,
      InitReplySender replySender) {
    Map<String, Object> headers = new LinkedHashMap<String, Object>();
    initRequestValidator.validateProtocol(request, context);
    InitRouteDefinition routeDefinition = handler.getRouteDefinition(request, context);
    initRequestValidator.validateRoute(routeDefinition);
    try {
      Iterable<?> initialPayloadItems = handler.getInitialPayloadItems(request, context);
      if (initialPayloadItems != null) {
        for (Object payloadItem : initialPayloadItems) {
          replySender.send(newReplyFrame(context.getSessionId(), routeDefinition, "init-data", payloadItem));
        }
      }
      replySender.send(newReplyFrame(context.getSessionId(), routeDefinition, "init-complete", null));
    } catch (RuntimeException ex) {
      if (routeDefinition == null) {
        throw ex;
      }
      try {
        replySender.send(newReplyFrame(context.getSessionId(), routeDefinition, "init-error", ex.getMessage()));
      } catch (RuntimeException sendErrorEx) {
        ex.addSuppressed(sendErrorEx);
        throw ex;
      }
    }
  }

  private InitReplyFrame newReplyFrame(
      String sessionId,
      InitRouteDefinition routeDefinition,
      String phase,
      Object payload) {
    Map<String, Object> headers = new LinkedHashMap<String, Object>();
    headers.put("subscriptionToken", routeDefinition.getSubscriptionToken());
    headers.put("phase", phase);
    return new InitReplyFrame(sessionId, routeDefinition.getReplyDestination(), phase, payload, headers);
  }
}
