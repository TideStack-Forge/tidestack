package com.ouroboros.businessmodel.event.store;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** System settings for the durable business Event worker. */
@ConfigurationProperties("ouroboros.business-event.delivery")
public class BusinessEventDeliveryProperties {
  private int batchSize = 100;

  public int getBatchSize() {
    return batchSize;
  }

  public void setBatchSize(int batchSize) {
    this.batchSize = batchSize;
  }

  private int leaseSeconds = 60;

  public int getLeaseSeconds() {
    return leaseSeconds;
  }

  public void setLeaseSeconds(int leaseSeconds) {
    this.leaseSeconds = leaseSeconds;
  }

  private int maxRetryCount = 6;

  public int getMaxRetryCount() {
    return maxRetryCount;
  }

  public void setMaxRetryCount(int maxRetryCount) {
    this.maxRetryCount = maxRetryCount;
  }

}
