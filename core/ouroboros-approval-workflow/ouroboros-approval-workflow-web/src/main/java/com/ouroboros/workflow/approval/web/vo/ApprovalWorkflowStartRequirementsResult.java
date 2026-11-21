package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.workflow.approval.definition.startrequirements.Requirement;
import com.ouroboros.workflow.approval.definition.startrequirements.StartRequirements;

public class ApprovalWorkflowStartRequirementsResult {
  private boolean requiresInput;
  private SchemaNode schema;
  /** 引擎无关发起需求契约（Phase 4 M22；旧前端兼容期与 schema 双结构并存，schema 为兼容字段）。 */
  private List<Requirement> requirements;
  /** 是否支持发起时抄送（Phase 4 M22）。 */
  private boolean ccEnabled;

  public ApprovalWorkflowStartRequirementsResult() {
  }

  public ApprovalWorkflowStartRequirementsResult(boolean requiresInput, SchemaNode schema) {
    this.requiresInput = requiresInput;
    this.schema = schema;
  }

  public static ApprovalWorkflowStartRequirementsResult none() {
    return new ApprovalWorkflowStartRequirementsResult(false, null);
  }

  public static ApprovalWorkflowStartRequirementsResult inputRequired(SchemaNode schema) {
    return new ApprovalWorkflowStartRequirementsResult(true, schema);
  }

  /**
   * 组装响应：{@code requirements}/{@code ccEnabled}/{@code requiresInput} 全部来自引擎无关契约，
   * {@code schema} 按同一引擎无关契约生成。
   *
   * <p>{@code requiresInput} 必须由 {@code requirements} 派生而不是沿用旧结构：两侧分别计算时会出现
   * {@code requiresInput=false} 但 {@code requirements} 非空的矛盾响应。
   * {@code ccEnabled} 来自发起需求解析器。
   */
  public static ApprovalWorkflowStartRequirementsResult of(
      StartRequirements startRequirements, SchemaNode schema) {
    List<Requirement> requirements = startRequirements.requirements() == null
        ? List.of()
        : startRequirements.requirements();
    ApprovalWorkflowStartRequirementsResult result = new ApprovalWorkflowStartRequirementsResult(
        !requirements.isEmpty(), requirements.isEmpty() ? null : schema);
    result.setRequirements(requirements);
    result.setCcEnabled(startRequirements.ccEnabled());
    return result;
  }

  public boolean isRequiresInput() {
    return requiresInput;
  }

  public void setRequiresInput(boolean requiresInput) {
    this.requiresInput = requiresInput;
  }

  public SchemaNode getSchema() {
    return schema;
  }

  public void setSchema(SchemaNode schema) {
    this.schema = schema;
  }

  public List<Requirement> getRequirements() {
    return requirements;
  }

  public void setRequirements(List<Requirement> requirements) {
    this.requirements = requirements;
  }

  public boolean isCcEnabled() {
    return ccEnabled;
  }

  public void setCcEnabled(boolean ccEnabled) {
    this.ccEnabled = ccEnabled;
  }
}
