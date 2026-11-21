package com.ouroboros.workflow.approval.web.vo;

public class TaskInfoResult {
  WorkflowModelResult workflowModel;

  String startTime;
  String endTime;
  String processStarter;

  String taskId;
  String taskName;

  boolean canEnd;
  boolean canRepeat;
  String type;

  public TaskInfoResult() {
  }

  public TaskInfoResult(WorkflowModelResult workflowModel, String startTime, String endTime, String processStarter, String taskId, String taskName, boolean canEnd, boolean canRepeat, String type) {
    this.workflowModel = workflowModel;
    this.startTime = startTime;
    this.endTime = endTime;
    this.processStarter = processStarter;
    this.taskId = taskId;
    this.taskName = taskName;
    this.canEnd = canEnd;
    this.canRepeat = canRepeat;
    this.type = type;
  }

  public WorkflowModelResult getWorkflowModel() {
    return workflowModel;
  }

  public void setWorkflowModel(WorkflowModelResult workflowModel) {
    this.workflowModel = workflowModel;
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

  public String getProcessStarter() {
    return processStarter;
  }

  public void setProcessStarter(String processStarter) {
    this.processStarter = processStarter;
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

  public boolean isCanEnd() {
    return canEnd;
  }

  public void setCanEnd(boolean canEnd) {
    this.canEnd = canEnd;
  }

  public boolean isCanRepeat() {
    return canRepeat;
  }

  public void setCanRepeat(boolean canRepeat) {
    this.canRepeat = canRepeat;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }
}
