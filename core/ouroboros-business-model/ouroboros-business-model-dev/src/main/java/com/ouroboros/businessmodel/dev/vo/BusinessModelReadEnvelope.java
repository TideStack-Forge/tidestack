package com.ouroboros.businessmodel.dev.vo;

public class BusinessModelReadEnvelope<T> {
  private final T businessModel;
  private final BusinessModelValidationSnapshot validationSnapshot;
  private final String appName;

  public BusinessModelReadEnvelope(T businessModel, BusinessModelValidationSnapshot validationSnapshot) {
    this(businessModel, validationSnapshot, null);
  }

  public BusinessModelReadEnvelope(T businessModel, BusinessModelValidationSnapshot validationSnapshot, String appName) {
    this.businessModel = businessModel;
    this.validationSnapshot = validationSnapshot;
    this.appName = appName;
  }

  public T getBusinessModel() {
    return businessModel;
  }

  public BusinessModelValidationSnapshot getValidationSnapshot() {
    return validationSnapshot;
  }

  public String getAppName() {
    return appName;
  }
}
