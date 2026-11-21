package com.ouroboros.devhelper.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class AbstractAsyncCommandTest {

  @Test
  void shouldCompleteBroadcastRequestWithoutSequenceId() throws Exception {
    TestAsyncCommand command = new TestAsyncCommand();
    CommandRequest request = new CommandRequest()
        .setAppName("crm")
        .setCommand("knockKnock");

    CompletableFuture<CommandResult> future = command.execute(request);

    assertFalse(future.isDone());
    command.succeed(request, "ok");

    CommandResult result = future.get(1, TimeUnit.SECONDS);
    assertTrue(result.getSuccess());
    assertEquals("ok", result.getData());
    assertTrue(command.commandResultMap.isEmpty());
    assertTrue(command.commandResponseList.isEmpty());
  }

  @Test
  void shouldStillCompleteTargetedRequestWithSequenceId() throws Exception {
    TestAsyncCommand command = new TestAsyncCommand();
    CommandRequest request = new CommandRequest()
        .setAppName("crm")
        .setCommand("refresh")
        .setSequenceId("seq-1");

    CompletableFuture<CommandResult> future = command.execute(request);

    assertFalse(future.isDone());
    command.succeed(request, "ok");

    CommandResult result = future.get(1, TimeUnit.SECONDS);
    assertTrue(result.getSuccess());
    assertEquals("ok", result.getData());
    assertTrue(command.commandResultMap.isEmpty());
  }

  @Test
  void shouldCompleteFailureAndClearRegistrationWhenExecutionThrowsSynchronously() throws Exception {
    AbstractAsyncCommand command = new AbstractAsyncCommand("refresh") {
      @Override
      protected void doExecute(CommandRequest request) {
        throw new IllegalStateException("collection failed");
      }
    };
    CommandRequest request = new CommandRequest()
        .setAppName("crm")
        .setCommand("refresh")
        .setSequenceId("seq-failure");

    CommandResult result = command.execute(request).get(1, TimeUnit.SECONDS);

    assertFalse(result.getSuccess());
    assertEquals("collection failed", result.getMessage());
    assertTrue(command.commandResultMap.isEmpty());
  }

  private static final class TestAsyncCommand extends AbstractAsyncCommand {

    private TestAsyncCommand() {
      super("test");
    }

    @Override
    protected void doExecute(CommandRequest request) {
    }

    private void succeed(CommandRequest request, Object data) {
      success(request, data);
    }
  }
}
