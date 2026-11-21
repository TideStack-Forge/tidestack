package com.ouroboros.statistics.reporting.runtime.execution;

import com.ouroboros.util.SPI;

/** Platform configuration is adapted in the ability layer. */
public interface DatasetExecutionPolicyProvider {
  boolean supportsCurrentContext();
  DatasetExecutionPolicy load();

  static DatasetExecutionPolicy current() {
    return SPI.getCachedReversedServiceStream(DatasetExecutionPolicyProvider.class)
        .filter(DatasetExecutionPolicyProvider::supportsCurrentContext).findFirst()
        .map(DatasetExecutionPolicyProvider::load).orElseGet(DatasetExecutionPolicy::defaults);
  }
}
