package com.ouroboros.devhelper.command;

public class CommandResult {
  private String appName;
  private String profile;
  private String command;
  private String sequenceId;
  private String channelName;
  private Long runtimeGeneration;
  private String helperInstanceId;
  private Long helperStartedAtEpochMillis;
  private String appIncarnationId;
  private String refreshOperationId;
  private CommandFailureType failureType;
  private boolean success;
  private String message;
  private Object data;

  public CommandResult() {
    this.success = false;
  }

  public CommandResult(CommandRequest request) {
    this.success = false;
    this.appName = request.getAppName();
    this.profile = request.getProfile();
    this.command = request.getCommand();
    this.sequenceId = request.getSequenceId();
    this.channelName = request.getChannelName();
    this.runtimeGeneration = request.getRuntimeGeneration();
    this.helperInstanceId = request.getHelperInstanceId();
    this.helperStartedAtEpochMillis = request.getHelperStartedAtEpochMillis();
    this.appIncarnationId = request.getAppIncarnationId();
    this.refreshOperationId = request.getRefreshOperationId();
  }

  public CommandResult(CommandRequest request, Object data) {
    this.success = true;
    this.appName = request.getAppName();
    this.profile = request.getProfile();
    this.command = request.getCommand();
    this.sequenceId = request.getSequenceId();
    this.channelName = request.getChannelName();
    this.runtimeGeneration = request.getRuntimeGeneration();
    this.helperInstanceId = request.getHelperInstanceId();
    this.helperStartedAtEpochMillis = request.getHelperStartedAtEpochMillis();
    this.appIncarnationId = request.getAppIncarnationId();
    this.refreshOperationId = request.getRefreshOperationId();
    this.data = data;
  }

  public String getProfile() {
    return profile;
  }

  public CommandResult setProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public String getAppName() {
    return appName;
  }

  public CommandResult setAppName(String appName) {
    this.appName = appName;
    return this;
  }

  public String getCommand() {
    return command;
  }

  public CommandResult setCommand(String command) {
    this.command = command;
    return this;
  }

  public String getSequenceId() {
    return sequenceId;
  }

  public CommandResult setSequenceId(String sequenceId) {
    this.sequenceId = sequenceId;
    return this;
  }

  public String getChannelName() {
    return channelName;
  }

  public CommandResult setChannelName(String channelName) {
    this.channelName = channelName;
    return this;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public CommandResult setRuntimeGeneration(Long runtimeGeneration) {
    this.runtimeGeneration = runtimeGeneration;
    return this;
  }

  public String getHelperInstanceId() {
    return helperInstanceId;
  }

  public CommandResult setHelperInstanceId(String helperInstanceId) {
    this.helperInstanceId = helperInstanceId;
    return this;
  }

  public Long getHelperStartedAtEpochMillis() {
    return helperStartedAtEpochMillis;
  }

  public CommandResult setHelperStartedAtEpochMillis(Long helperStartedAtEpochMillis) {
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    return this;
  }

  public String getAppIncarnationId() {
    return appIncarnationId;
  }

  public CommandResult setAppIncarnationId(String appIncarnationId) {
    this.appIncarnationId = appIncarnationId;
    return this;
  }

  public String getRefreshOperationId() {
    return refreshOperationId;
  }

  public CommandFailureType getFailureType() {
    return failureType;
  }

  public CommandResult setFailureType(CommandFailureType failureType) {
    this.failureType = failureType;
    return this;
  }

  public CommandResult setRefreshOperationId(String refreshOperationId) {
    this.refreshOperationId = refreshOperationId;
    return this;
  }

  public Object getData() {
    return data;
  }

  public boolean getSuccess() {
    return success;
  }

  public CommandResult setSuccess(boolean success) {
    this.success = success;
    return this;
  }

  public CommandResult setData(Object data) {
    this.data = data;
    return this;
  }

  public String getMessage() {
    return message;
  }

  public CommandResult setMessage(String message) {
    this.message = message;
    return this;
  }
}
