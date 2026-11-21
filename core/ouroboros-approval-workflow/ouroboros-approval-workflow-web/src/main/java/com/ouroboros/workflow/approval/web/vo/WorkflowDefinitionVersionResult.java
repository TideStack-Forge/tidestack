package com.ouroboros.workflow.approval.web.vo;

import java.time.LocalDateTime;

import com.ouroboros.workflow.approval.model.FlowDefinitionVersion;

public class WorkflowDefinitionVersionResult {
  private String flowKey;
  private Integer version;
  private String processDefinitionId;
  private String deploymentId;
  private LocalDateTime deploymentTime;
  private String deployedBy;
  private String status;

  public WorkflowDefinitionVersionResult(FlowDefinitionVersion version) {
    this.version = version.getVersion();
    this.processDefinitionId = version.getProcessDefinitionId();
    this.deploymentId = version.getDeploymentId();
    this.deploymentTime = version.getDeploymentTime();
    this.deployedBy = version.getDeployedBy();
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public Integer getVersion() {
    return version;
  }

  public void setVersion(Integer version) {
    this.version = version;
  }

  public String getProcessDefinitionId() {
    return processDefinitionId;
  }

  public void setProcessDefinitionId(String processDefinitionId) {
    this.processDefinitionId = processDefinitionId;
  }

  public String getDeploymentId() {
    return deploymentId;
  }

  public void setDeploymentId(String deploymentId) {
    this.deploymentId = deploymentId;
  }

  public LocalDateTime getDeploymentTime() {
    return deploymentTime;
  }

  public void setDeploymentTime(LocalDateTime deploymentTime) {
    this.deploymentTime = deploymentTime;
  }

  public String getDeployedBy() {
    return deployedBy;
  }

  public void setDeployedBy(String deployedBy) {
    this.deployedBy = deployedBy;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }
}
