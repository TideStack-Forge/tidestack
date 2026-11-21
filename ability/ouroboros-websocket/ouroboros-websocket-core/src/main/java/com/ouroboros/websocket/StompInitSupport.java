package com.ouroboros.websocket;

public interface StompInitSupport {
  <T extends InitRequest> void init(
      T request,
      InitRequestContext context,
      InitRequestHandler<T> handler,
      InitReplySender replySender);
}
