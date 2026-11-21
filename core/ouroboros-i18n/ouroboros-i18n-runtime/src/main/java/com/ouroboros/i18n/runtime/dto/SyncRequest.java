package com.ouroboros.i18n.runtime.dto;

/**
 * 同步文案请求
 */
public class SyncRequest {
  private String locale;
  private Boolean removeOrphaned;

  public SyncRequest() {
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public Boolean getRemoveOrphaned() {
    return removeOrphaned != null ? removeOrphaned : false;
  }

  public void setRemoveOrphaned(Boolean removeOrphaned) {
    this.removeOrphaned = removeOrphaned;
  }
}
