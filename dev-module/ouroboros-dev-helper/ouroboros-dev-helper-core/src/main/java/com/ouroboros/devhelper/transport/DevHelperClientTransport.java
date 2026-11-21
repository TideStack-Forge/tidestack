package com.ouroboros.devhelper.transport;

import java.util.function.Consumer;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;

/**
 * Caller-side helper communication, independent of the wire protocol.
 * Implementations preserve request/result identity fields and deliver unsolicited results too.
 * Send failures propagate to the caller; correlation and deadlines belong to the command service.
 */
public interface DevHelperClientTransport {
  void send(CommandRequest request);

  TransportSubscription subscribe(Consumer<CommandResult> receiver);
}
