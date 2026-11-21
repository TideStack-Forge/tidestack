package com.ouroboros.workflow.approval.external.runtime.web.vo;

import java.util.List;
import java.util.Map;

import com.ouroboros.workflow.approval.external.CcTarget;
import com.ouroboros.workflow.approval.external.runtime.start.ResultOnlyStartCommand;
import com.ouroboros.workflow.approval.FlowStartCommand;

/**
 * 存量结果型发起请求视图；统一入口允许省略业务记录，提供时须同时指定类型和主键。
 *
 * <p>userId 不来自请求体（由控制器从当前用户取）；scope 由运行时解析。
 */
public class ResultOnlyStartRequest {

  private String flowKey;
  private String businessRecordType;
  private String businessRecordId;
  private Map<String, Object> formData;
  private String idempotencyKey;
  private List<CcTarget> cc;
  private Map<String, List<String>> approvers;
  private Map<String, List<String>> targetSelectActioners;

  public ResultOnlyStartCommand toCommand(String userId) {
    return new ResultOnlyStartCommand(
        flowKey, businessRecordType, businessRecordId, userId, formData,
        idempotencyKey, cc, approvers, targetSelectActioners);
  }

  /** 旧 HTTP 请求只做协议转换，接入模式仍由统一发起服务选择。 */
  public FlowStartCommand toFlowStartCommand(String userId) {
    return toCommand(userId).toFlowStartCommand();
  }

  public String getFlowKey() {
    return flowKey;
  }

  public void setFlowKey(String flowKey) {
    this.flowKey = flowKey;
  }

  public String getBusinessRecordType() {
    return businessRecordType;
  }

  public void setBusinessRecordType(String businessRecordType) {
    this.businessRecordType = businessRecordType;
  }

  public String getBusinessRecordId() {
    return businessRecordId;
  }

  public void setBusinessRecordId(String businessRecordId) {
    this.businessRecordId = businessRecordId;
  }

  public Map<String, Object> getFormData() {
    return formData;
  }

  public void setFormData(Map<String, Object> formData) {
    this.formData = formData;
  }

  public String getIdempotencyKey() {
    return idempotencyKey;
  }

  public void setIdempotencyKey(String idempotencyKey) {
    this.idempotencyKey = idempotencyKey;
  }

  public List<CcTarget> getCc() {
    return cc;
  }

  public void setCc(List<CcTarget> cc) {
    this.cc = cc;
  }

  public Map<String, List<String>> getApprovers() {
    return approvers;
  }

  public void setApprovers(Map<String, List<String>> approvers) {
    this.approvers = approvers;
  }

  public Map<String, List<String>> getTargetSelectActioners() {
    return targetSelectActioners;
  }

  public void setTargetSelectActioners(Map<String, List<String>> targetSelectActioners) {
    this.targetSelectActioners = targetSelectActioners;
  }
}
