package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

public class FreeApprovalTaskOptionsVO {
  List<String> consultUserIds;

  public List<String> getConsultUserIds() {
    return consultUserIds;
  }

  public void setConsultUserIds(List<String> consultUserIds) {
    this.consultUserIds = consultUserIds;
  }
}
