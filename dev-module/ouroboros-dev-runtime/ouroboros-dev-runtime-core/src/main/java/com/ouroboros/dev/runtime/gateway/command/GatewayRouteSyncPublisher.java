package com.ouroboros.dev.runtime.gateway.command;

import io.vavr.control.Try;

public interface GatewayRouteSyncPublisher {
  Try<Void> publish(GatewayRouteSyncCommand command);
}
