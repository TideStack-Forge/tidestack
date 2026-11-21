package com.ouroboros.dev.app.model;

import jakarta.validation.constraints.Min;

public class DebugKeyGenerationRequest {
  private String description;

  @Min(1)
  private Integer expiryDays;

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Integer getExpiryDays() {
    return expiryDays;
  }

  public void setExpiryDays(Integer expiryDays) {
    this.expiryDays = expiryDays;
  }
}
