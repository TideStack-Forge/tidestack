package com.ouroboros.workflow.approval.web.vo;

import java.util.List;

public class AddSignTaskOptionsVO {
  List<String> targetUserIds;

  AddSignPosition position;

  public List<String> getTargetUserIds() {
    return targetUserIds;
  }

  public void setTargetUserIds(List<String> targetUserIds) {
    this.targetUserIds = targetUserIds;
  }

  public AddSignPosition getPosition() {
    return position;
  }

  public void setPosition(AddSignPosition position) {
    this.position = position;
  }
}
