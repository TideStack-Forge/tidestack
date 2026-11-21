package com.ouroboros.logicflow.dev.vo;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.ouroboros.logicflow.model.FlowModelMeta;
import com.ouroboros.logicflow.model.FlowModelValidationDiagnostic;
import com.ouroboros.logicflow.model.FlowModelValidationState;

public class LogicFlowValidationSnapshot {
  private final FlowModelValidationState validationState;
  private final String validationSummary;
  private final List<String> validationErrors;
  private final List<FlowModelValidationDiagnostic> validationDiagnostics;
  private final LocalDateTime lastValidatedTime;

  private LogicFlowValidationSnapshot(
      FlowModelValidationState validationState,
      String validationSummary,
      List<String> validationErrors,
      List<FlowModelValidationDiagnostic> validationDiagnostics,
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

  public static LogicFlowValidationSnapshot from(FlowModelMeta model) {
    if (model == null) {
      return new LogicFlowValidationSnapshot(null, null, Collections.emptyList(), Collections.emptyList(), null);
    }
    return new LogicFlowValidationSnapshot(
        model.getValidationState(),
        model.getValidationSummary(),
        model.getValidationErrors(),
        model.getValidationDiagnostics(),
        model.getLastValidatedTime()
    );
  }

  public FlowModelValidationState getValidationState() {
    return validationState;
  }

  public String getValidationSummary() {
    return validationSummary;
  }

  public List<String> getValidationErrors() {
    return validationErrors;
  }

  public List<FlowModelValidationDiagnostic> getValidationDiagnostics() {
    return validationDiagnostics;
  }

  public LocalDateTime getLastValidatedTime() {
    return lastValidatedTime;
  }
}
