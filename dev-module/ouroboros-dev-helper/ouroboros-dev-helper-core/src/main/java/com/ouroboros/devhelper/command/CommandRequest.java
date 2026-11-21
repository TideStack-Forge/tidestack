package com.ouroboros.devhelper.command;

import java.util.Map;

public class CommandRequest {
  private String appName;
  private String profile;
  private String command;
  private Map<String, Object> params;
  private long timeout = 0;
  private String sequenceId;
  private String channelName;
  private Long runtimeGeneration;
  private String helperInstanceId;
  private Long helperStartedAtEpochMillis;
  private String appIncarnationId;
  private String refreshOperationId;

  public String getProfile() {
    return profile;
  }

  public CommandRequest setProfile(String profile) {
    this.profile = profile;
    return this;
  }

  public String getAppName() {
    return appName;
  }

  public CommandRequest setAppName(String appName) {
    this.appName = appName;
    return this;
  }

  public String getCommand() {
    return command;
  }

  public CommandRequest setCommand(String command) {
    this.command = command;
    return this;
  }

  public Map<String, Object> getParams() {
    return params;
  }

  public CommandRequest setParams(Map<String, Object> params) {
    this.params = params;
    return this;
  }

  public long getTimeout() {
    return timeout;
  }

  public CommandRequest setTimeout(long timeout) {
    this.timeout = timeout;
    return this;
  }

  public String getSequenceId() {
    return sequenceId;
  }

  public CommandRequest setSequenceId(String sequenceId) {
    this.sequenceId = sequenceId;
    return this;
  }

  public String getChannelName() {
    return channelName;
  }

  public CommandRequest setChannelName(String channelName) {
    this.channelName = channelName;
    return this;
  }

  public Long getRuntimeGeneration() {
    return runtimeGeneration;
  }

  public CommandRequest setRuntimeGeneration(Long runtimeGeneration) {
    this.runtimeGeneration = runtimeGeneration;
    return this;
  }

  public String getHelperInstanceId() {
    return helperInstanceId;
  }

  public CommandRequest setHelperInstanceId(String helperInstanceId) {
    this.helperInstanceId = helperInstanceId;
    return this;
  }

  public Long getHelperStartedAtEpochMillis() {
    return helperStartedAtEpochMillis;
  }

  public CommandRequest setHelperStartedAtEpochMillis(Long helperStartedAtEpochMillis) {
    this.helperStartedAtEpochMillis = helperStartedAtEpochMillis;
    return this;
  }

  public String getAppIncarnationId() {
    return appIncarnationId;
  }

  public CommandRequest setAppIncarnationId(String appIncarnationId) {
    this.appIncarnationId = appIncarnationId;
    return this;
  }

  public String getRefreshOperationId() {
    return refreshOperationId;
  }

  public CommandRequest setRefreshOperationId(String refreshOperationId) {
    this.refreshOperationId = refreshOperationId;
    return this;
  }
}
