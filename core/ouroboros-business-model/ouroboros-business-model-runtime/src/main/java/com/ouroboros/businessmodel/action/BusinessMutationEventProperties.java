package com.ouroboros.businessmodel.action;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** System limits for each independently consumed mutation fact. */
@ConfigurationProperties("ouroboros.business-event.mutation")
public class BusinessMutationEventProperties {
  private int maxIds = 500;
  private int maxPayloadBytes = 64 * 1024;

  public int getMaxIds() { return maxIds; }

  public void setMaxIds(int maxIds) {
    if (maxIds < 1) {
      throw new IllegalArgumentException("maxIds must be positive");
    }
    this.maxIds = maxIds;
  }

  public int getMaxPayloadBytes() { return maxPayloadBytes; }

  public void setMaxPayloadBytes(int maxPayloadBytes) {
    if (maxPayloadBytes < 1) {
      throw new IllegalArgumentException("maxPayloadBytes must be positive");
    }
    this.maxPayloadBytes = maxPayloadBytes;
  }
}
