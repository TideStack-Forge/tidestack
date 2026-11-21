package com.ouroboros.devhelper.command;

import java.util.concurrent.CompletableFuture;

public interface Command {
  String getCommand();

  CompletableFuture<CommandResult> execute(CommandRequest request);
}
