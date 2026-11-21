package com.ouroboros.dev.app.service.runtime;

import org.springframework.stereotype.Component;

import com.ouroboros.logicflow.dev.service.LogicFlowDebugNotifications;
import com.ouroboros.websocket.StompMessageSender;

@Component
public class TestAppLogicFlowDebugNotifications implements LogicFlowDebugNotifications {
  private final StompMessageSender sender;
  public record Update(String captureId, long receivedThrough) {}

  public TestAppLogicFlowDebugNotifications(StompMessageSender sender) {
    this.sender = sender;
  }

  @Override
  public void updated(String owner, String captureId, long receivedThrough) {
    sender.sendToUser(owner, "/queue/dev/logicflow/debug/" + captureId, new Update(captureId, receivedThrough));
  }
}
