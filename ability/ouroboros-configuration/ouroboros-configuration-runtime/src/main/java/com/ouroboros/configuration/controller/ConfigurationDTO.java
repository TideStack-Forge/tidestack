package com.ouroboros.configuration.controller;

/**
 * @author liansz
 */
public class ConfigurationDTO {

  private String key;

  private Object value;

  private String expectedRevision;

  private Operation operation = Operation.SET;

  public enum Operation { SET, RESTORE_DEFAULT }

  public Operation getOperation() { return operation; }

  public void setOperation(Operation operation) { this.operation = operation; }

  public String getKey() {
    return key;
  }

  public void setKey(String key) {
    this.key = key;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public String getExpectedRevision() {
    return expectedRevision;
  }

  public void setExpectedRevision(String expectedRevision) {
    this.expectedRevision = expectedRevision;
  }
}
