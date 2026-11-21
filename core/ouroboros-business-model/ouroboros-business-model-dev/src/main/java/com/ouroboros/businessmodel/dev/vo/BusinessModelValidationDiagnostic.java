package com.ouroboros.businessmodel.dev.vo;

public class BusinessModelValidationDiagnostic {
  private final String diagnosticKind;
  private final String message;
  private final String scope;

  public BusinessModelValidationDiagnostic(String diagnosticKind, String message, String scope) {
    this.diagnosticKind = diagnosticKind;
    this.message = message;
    this.scope = scope;
  }

  public String getDiagnosticKind() {
    return diagnosticKind;
  }

  public String getMessage() {
    return message;
  }

  public String getScope() {
    return scope;
  }
}
