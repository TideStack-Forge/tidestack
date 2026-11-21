package com.ouroboros.i18n.dev.dto;

public class I18nLocaleSyncResult {
  private final int added;
  private final int removed;

  public I18nLocaleSyncResult(int added, int removed) {
    this.added = added;
    this.removed = removed;
  }

  public int getAdded() {
    return added;
  }

  public int getRemoved() {
    return removed;
  }
}
