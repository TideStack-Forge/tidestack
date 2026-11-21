package com.ouroboros.organizationstructure.dto.request;

public class MoveNodeRequest {

  private String newParentId;

  public String getNewParentId() {
    return newParentId;
  }

  public void setNewParentId(String newParentId) {
    this.newParentId = newParentId;
  }
}
