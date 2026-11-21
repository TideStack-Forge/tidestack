package com.ouroboros.cache;

import java.util.List;

public class ClearAllResult {
  private int totalRegions;
  private List<String> clearedRegions;
  private List<CacheRegionFailure> failedRegions;

  public int getTotalRegions() {
    return totalRegions;
  }

  public ClearAllResult setTotalRegions(int totalRegions) {
    this.totalRegions = totalRegions;
    return this;
  }

  public List<String> getClearedRegions() {
    return clearedRegions;
  }

  public ClearAllResult setClearedRegions(List<String> clearedRegions) {
    this.clearedRegions = clearedRegions;
    return this;
  }

  public List<CacheRegionFailure> getFailedRegions() {
    return failedRegions;
  }

  public ClearAllResult setFailedRegions(List<CacheRegionFailure> failedRegions) {
    this.failedRegions = failedRegions;
    return this;
  }
}
