package com.ouroboros.dev.app.initializer;

public abstract class AbstractSimpleDataSourceInitializer implements DataSourceInitializer {
  private String typeName;

  public AbstractSimpleDataSourceInitializer(String typeName) {
    this.typeName = typeName;
  }

  @Override
  public String getTypeName() {
    return typeName;
  }
}
