package com.ouroboros.dev.runtime.gateway;

import io.vavr.control.Try;

public interface RuntimeGatewayCoordinator {
  Try<Void> upsert(RuntimeGatewayRoute route);

  Try<Void> delete(RuntimeGatewayRouteKey key);
}
