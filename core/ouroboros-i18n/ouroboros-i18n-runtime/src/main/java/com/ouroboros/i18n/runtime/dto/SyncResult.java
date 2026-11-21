package com.ouroboros.i18n.runtime.dto;

/**
 * 同步结果
 */
public class SyncResult {
  private Integer added;
  private Integer removed;

  public SyncResult() {
  }

  public SyncResult(Integer added, Integer removed) {
    this.added = added;
    this.removed = removed;
  }

  public Integer getAdded() {
    return added;
  }

  public void setAdded(Integer added) {
    this.added = added;
  }

  public Integer getRemoved() {
    return removed;
  }

  public void setRemoved(Integer removed) {
    this.removed = removed;
  }
}
