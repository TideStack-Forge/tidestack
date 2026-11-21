package com.ouroboros.devhelper.transport;

import java.util.function.Consumer;

import com.ouroboros.devhelper.command.CommandRequest;
import com.ouroboros.devhelper.command.CommandResult;

/**
 * Application-side helper communication, independent of the wire protocol.
 * Routing, command execution and runtime identity checks belong to the dispatcher.
 * Implementations must allow results without a preceding request, including presence events.
 */
public interface DevHelperServerTransport {
  void send(CommandResult result);

  TransportSubscription subscribe(Consumer<CommandRequest> receiver);
}
