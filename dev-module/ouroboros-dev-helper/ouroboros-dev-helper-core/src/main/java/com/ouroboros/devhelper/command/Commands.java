package com.ouroboros.devhelper.command;

public enum Commands {
  // 开发平台启动后，会发送一个 knockKnock 的命令，所有助手监听到这个命令后，会回应一个 knockKnock 响应
  // 当应用助手启动后，也会发送一个 knockKnock 的命令，通知开发平台助手上线了
  KNOCK_KNOCK("knockKnock"),
  READ_METADATA("readMetadata"),
  APPROVAL_CANDIDATES("approvalCandidates"),
  EDITOR_CATALOG("editorCatalog"),
  REFRESH_METADATA("refreshMetadata"),
  CREATE_STREAM_CHANNEL("createStreamChannel"),
  DESTROY_STREAM_CHANNEL("destroyStreamChannel");

  private final String command;

  Commands(String command) {
    this.command = command;
  }

  public String getCommand() {
    return command;
  }
}
