package com.ouroboros.businessmodel.dev.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BusinessModelValidationSnapshot {
  private final BusinessModelValidationState validationState;
  private final String validationSummary;
  private final List<String> validationErrors;
  private final List<BusinessModelValidationDiagnostic> validationDiagnostics;
  private final LocalDateTime lastValidatedTime;

  public BusinessModelValidationSnapshot(
      BusinessModelValidationState validationState,
      String validationSummary,
      List<String> validationErrors,
      List<BusinessModelValidationDiagnostic> validationDiagnostics,
      LocalDateTime lastValidatedTime
  ) {
    this.validationState = validationState;
    this.validationSummary = validationSummary;
    this.validationErrors = validationErrors == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(validationErrors));
    this.validationDiagnostics = validationDiagnostics == null
        ? Collections.emptyList()
        : Collections.unmodifiableList(new ArrayList<>(validationDiagnostics));
    this.lastValidatedTime = lastValidatedTime;
  }

  public BusinessModelValidationState getValidationState() {
    return validationState;
  }

  public String getValidationSummary() {
    return validationSummary;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }

  public List<BusinessModelValidationDiagnostic> getValidationDiagnostics() {
    return validationDiagnostics;
  }

  public LocalDateTime getLastValidatedTime() {
    return lastValidatedTime;
  }
}
