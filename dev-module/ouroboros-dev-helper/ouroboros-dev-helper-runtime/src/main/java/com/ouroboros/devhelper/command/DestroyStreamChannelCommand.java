package com.ouroboros.devhelper.command;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public class DestroyStreamChannelCommand extends AbstractAsyncCommand {
  private static final String COMMAND = Commands.DESTROY_STREAM_CHANNEL.getCommand();

  public DestroyStreamChannelCommand() {
    super(COMMAND);
  }

  @Override
  protected void doExecute(CommandRequest request) {
    failure(request, "暂未实现");
  }
}
