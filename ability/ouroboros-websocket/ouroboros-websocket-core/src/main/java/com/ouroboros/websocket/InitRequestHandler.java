package com.ouroboros.websocket;

public interface InitRequestHandler<T extends InitRequest> {
  InitRouteDefinition getRouteDefinition(T request, InitRequestContext context);

  Iterable<?> getInitialPayloadItems(T request, InitRequestContext context);
}
