package com.ouroboros.businessmodel.meta.features;

public class FeaturesSwitch {
  Boolean isEnabledAuditFields;
  Boolean isEnabledSoftDelete;
  Boolean isEnabledChangeLog;
  Boolean isEnabledDraft;
  Boolean isEnabledExclusiveLock;

  public Boolean getIsEnabledAuditFields() {
    return isEnabledAuditFields;
  }

  public Boolean getIsEnabledSoftDelete() {
    return isEnabledSoftDelete;
  }

  public Boolean getIsEnabledChangeLog() {
    return isEnabledChangeLog;
  }

  public Boolean getIsEnabledDraft() {
    return isEnabledDraft;
  }

  public Boolean getIsEnabledExclusiveLock() {
    return isEnabledExclusiveLock;
  }

  public void setIsEnabledAuditFields(Boolean isEnabledAuditFields) {
    this.isEnabledAuditFields = isEnabledAuditFields;
  }

  public void setIsEnabledSoftDelete(Boolean isEnabledSoftDelete) {
    this.isEnabledSoftDelete = isEnabledSoftDelete;
  }

  public void setIsEnabledChangeLog(Boolean isEnabledChangeLog) {
    this.isEnabledChangeLog = isEnabledChangeLog;
  }

  public void setIsEnabledDraft(Boolean isEnabledDraft) {
    this.isEnabledDraft = isEnabledDraft;
  }

  public void setIsEnabledExclusiveLock(Boolean isEnabledExclusiveLock) {
    this.isEnabledExclusiveLock = isEnabledExclusiveLock;
  }
}
