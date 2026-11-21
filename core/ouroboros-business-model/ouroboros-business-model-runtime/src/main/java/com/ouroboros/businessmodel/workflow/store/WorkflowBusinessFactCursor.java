package com.ouroboros.businessmodel.workflow.store;

import com.ouroboros.data.annotation.Field;
import com.ouroboros.data.annotation.Model;
import com.ouroboros.data.annotation.PrimaryKey;

/** One locked receiver cursor per formal model/object/flow projection. Tasks never advance it. */
@Model(rawName = "t_business_workflow_fact_cursor", label = "业务工作流投影游标")
public class WorkflowBusinessFactCursor {
  @Field(size = 64) @PrimaryKey
  private String projectionKey;
  @Field
  private String modelFullName;
  @Field
  private String recordId;
  @Field
  private String flowKey;
  @Field(nullable = true)
  private String instanceId;
  @Field(nullable = true)
  private String approvalCaseId;
  @Field(nullable = true)
  private Integer submissionRound;
  @Field
  private Long stateSequence;
  @Field(nullable = true)
  private String lastEventFactId;

  public String getProjectionKey() { return projectionKey; }
  public void setProjectionKey(String projectionKey) { this.projectionKey = projectionKey; }
  public String getModelFullName() { return modelFullName; }
  public void setModelFullName(String modelFullName) { this.modelFullName = modelFullName; }
  public String getRecordId() { return recordId; }
  public void setRecordId(String recordId) { this.recordId = recordId; }
  public String getFlowKey() { return flowKey; }
  public void setFlowKey(String flowKey) { this.flowKey = flowKey; }
  public String getInstanceId() { return instanceId; }
  public void setInstanceId(String instanceId) { this.instanceId = instanceId; }
  public String getApprovalCaseId() { return approvalCaseId; }
  public void setApprovalCaseId(String approvalCaseId) { this.approvalCaseId = approvalCaseId; }
  public Integer getSubmissionRound() { return submissionRound; }
  public void setSubmissionRound(Integer submissionRound) { this.submissionRound = submissionRound; }
  public Long getStateSequence() { return stateSequence; }
  public void setStateSequence(Long stateSequence) { this.stateSequence = stateSequence; }
  public String getLastEventFactId() { return lastEventFactId; }
  public void setLastEventFactId(String lastEventFactId) { this.lastEventFactId = lastEventFactId; }
}
