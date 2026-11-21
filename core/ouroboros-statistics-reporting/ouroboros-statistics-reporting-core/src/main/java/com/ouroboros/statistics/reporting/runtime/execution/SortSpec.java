package com.ouroboros.statistics.reporting.runtime.execution;

public class SortSpec {
  private String field;
  private String direction;

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getDirection() {
    return direction;
  }

  public void setDirection(String direction) {
    this.direction = direction;
  }

  public SortSpec copy() {
    SortSpec copied = new SortSpec();
    copied.setField(field);
    copied.setDirection(direction);
    return copied;
  }
}
