package com.ouroboros.dev.app.model;

public class AppManagedBranchCreateRequest {
  private String editionCode;
  private String fromRevision;

  public String getEditionCode() {
    return editionCode;
  }

  public void setEditionCode(String editionCode) {
    this.editionCode = editionCode;
  }

  public String getFromRevision() {
    return fromRevision;
  }

  public void setFromRevision(String fromRevision) {
    this.fromRevision = fromRevision;
  }
}
