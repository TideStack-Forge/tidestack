package com.ouroboros.i18n.dev.dto;

public class I18nLocaleSyncRequest {
  private Boolean removeOrphaned;

  public Boolean getRemoveOrphaned() {
    return removeOrphaned;
  }

  public I18nLocaleSyncRequest setRemoveOrphaned(Boolean removeOrphaned) {
    this.removeOrphaned = removeOrphaned;
    return this;
  }
}
