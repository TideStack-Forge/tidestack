package com.ouroboros.devhelper.command;

import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

@Component
@ConditionalOnProperty(name = "ouroboros.dev-helper.test-app", havingValue = "false", matchIfMissing = true)
public class CreateStreamChannelCommand extends AbstractAsyncCommand {
  private static final String COMMAND = Commands.CREATE_STREAM_CHANNEL.getCommand();
  private static final String CHANNEL_PREFIX = "ouroboros-dev-helper-stream-";
  private static final String CHANNEL_TOPIC_PREFIX = "ouroboros.dev-helper.stream.";

  public CreateStreamChannelCommand() {
    super(COMMAND);
  }

  @Override
  protected void doExecute(CommandRequest request) {
    String bindingName = CHANNEL_PREFIX + request.getAppName() + "-" + request.getChannelName();
    String topicName = CHANNEL_TOPIC_PREFIX + request.getAppName() + "." + request.getChannelName();
    failure(request, "暂未实现");
  }
}
