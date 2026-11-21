package com.ouroboros.workflow.approval.web.vo;

public class InstanceDetailResult {
  String id;
  String name;
  String status;
  String starter;

  String startTime;
  String endTime;

  String taskId;
  String taskName;

  public InstanceDetailResult() {
  }

  public InstanceDetailResult(String id, String name, String status, String starter, String startTime, String endTime, String taskId, String taskName) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.starter = starter;
    this.startTime = startTime;
    this.endTime = endTime;
    this.taskId = taskId;
    this.taskName = taskName;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStarter() {
    return starter;
  }

  public void setStarter(String starter) {
    this.starter = starter;
  }

  public String getStartTime() {
    return startTime;
  }

  public void setStartTime(String startTime) {
    this.startTime = startTime;
  }

  public String getEndTime() {
    return endTime;
  }

  public void setEndTime(String endTime) {
    this.endTime = endTime;
  }

  public String getTaskId() {
    return taskId;
  }

  public void setTaskId(String taskId) {
    this.taskId = taskId;
  }

  public String getTaskName() {
    return taskName;
  }

  public void setTaskName(String taskName) {
    this.taskName = taskName;
  }
}
