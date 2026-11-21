package com.ouroboros.workflow.approval.web.vo;

import java.util.Map;

public class SubmitTaskVO {
  String action;
  String comment;
  Map<String, Object> formData;
  Map<String, Object> payload;
  TransferTaskOptionsVO transfer;
  FreeRejectTaskOptionsVO freeReject;
  RecoveryTaskOptionsVO recovery;
  AddSignTaskOptionsVO addSign;
  FreeApprovalTaskOptionsVO freeApproval;

  public String getAction() {
    return action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public String getComment() {
    return comment;
  }

  public void setComment(String comment) {
    this.comment = comment;
  }

  public Map<String, Object> getFormData() {
    return formData;
  }

  public void setFormData(Map<String, Object> formData) {
    this.formData = formData;
  }

  public Map<String, Object> getPayload() {
    return payload;
  }

  public void setPayload(Map<String, Object> payload) {
    this.payload = payload;
  }

  public TransferTaskOptionsVO getTransfer() {
    return transfer;
  }

  public void setTransfer(TransferTaskOptionsVO transfer) {
    this.transfer = transfer;
  }

  public FreeRejectTaskOptionsVO getFreeReject() {
    return freeReject;
  }

  public void setFreeReject(FreeRejectTaskOptionsVO freeReject) {
    this.freeReject = freeReject;
  }

  public RecoveryTaskOptionsVO getRecovery() {
    return recovery;
  }

  public void setRecovery(RecoveryTaskOptionsVO recovery) {
    this.recovery = recovery;
  }

  public AddSignTaskOptionsVO getAddSign() {
    return addSign;
  }

  public void setAddSign(AddSignTaskOptionsVO addSign) {
    this.addSign = addSign;
  }

  public FreeApprovalTaskOptionsVO getFreeApproval() {
    return freeApproval;
  }

  public void setFreeApproval(FreeApprovalTaskOptionsVO freeApproval) {
    this.freeApproval = freeApproval;
  }
}
