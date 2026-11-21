package com.ouroboros.logicflow.results;

public final class FlowOutcome {
  private final Kind kind;
  private final String nodeName;
  private final Object value;

  private FlowOutcome(Kind kind, String nodeName, Object value) {
    this.kind = kind;
    this.nodeName = nodeName;
    this.value = value;
  }

  public static FlowOutcome exit(String nodeName, Object value) {
    return new FlowOutcome(Kind.EXIT, nodeName, value);
  }

  public static FlowOutcome returned(String nodeName, Object value) {
    return new FlowOutcome(Kind.RETURN, nodeName, value);
  }

  public Kind getKind() {
    return kind;
  }

  public String getNodeName() {
    return nodeName;
  }

  public Object getValue() {
    return value;
  }

  public enum Kind {
    EXIT,
    RETURN
  }
}
