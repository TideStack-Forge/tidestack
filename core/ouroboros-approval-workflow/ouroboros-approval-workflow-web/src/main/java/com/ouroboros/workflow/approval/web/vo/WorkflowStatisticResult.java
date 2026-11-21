package com.ouroboros.workflow.approval.web.vo;

public class WorkflowStatisticResult {
  private String id;
  private String name;
  private String category;
  private Long totalCnt;
  private Long runningCnt;
  private Long finishedCnt;
  private String processDefinitionId;
  private String description;
  private String status;
  private String statusName;

  public WorkflowStatisticResult(String id, String name, String category, Long totalCnt, Long runningCnt, Long finishedCnt, String processDefinitionId, String description,
                                 String status) {
    this.id = id;
    this.name = name;
    this.category = category;
    this.totalCnt = totalCnt;
    this.runningCnt = runningCnt;
    this.finishedCnt = finishedCnt;
    this.processDefinitionId = processDefinitionId;
    this.description = description;
    this.status = status;
    this.statusName = switch (status) {
      case "draft" -> "草稿";
      case "deployed" -> "已部署";
      case "offline" -> "已下线";
      default -> "未知";
    };
  }

  public WorkflowStatisticResult() {
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

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public Long getTotalCnt() {
    return totalCnt;
  }

  public void setTotalCnt(Long totalCnt) {
    this.totalCnt = totalCnt;
  }

  public Long getRunningCnt() {
    return runningCnt;
  }

  public void setRunningCnt(Long runningCnt) {
    this.runningCnt = runningCnt;
  }

  public Long getFinishedCnt() {
    return finishedCnt;
  }

  public void setFinishedCnt(Long finishedCnt) {
    this.finishedCnt = finishedCnt;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getStatusName() {
    return statusName;
  }

  public void setStatusName(String statusName) {
    this.statusName = statusName;
  }
}
