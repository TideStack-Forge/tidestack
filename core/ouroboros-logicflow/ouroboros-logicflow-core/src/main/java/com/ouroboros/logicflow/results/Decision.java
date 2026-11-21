package com.ouroboros.logicflow.results;

public class Decision {
  private final boolean isSatisfied;
  private final Object value;

  private Decision(boolean isSatisfied, Object value) {
    this.isSatisfied = isSatisfied;
    this.value = value;
  }

  public static Decision satisfied(Object value) {
    return new Decision(true, value);
  }

  public static Decision unsatisfied() {
    return new Decision(false, null);
  }

  public boolean isSatisfied() {
    return isSatisfied;
  }

  public Object getValue() {
    return value;
  }
}
