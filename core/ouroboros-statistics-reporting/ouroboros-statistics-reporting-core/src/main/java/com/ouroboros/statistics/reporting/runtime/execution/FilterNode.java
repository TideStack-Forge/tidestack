package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.List;

public class FilterNode {
  private String field;
  private String op;
  private Object value;
  private List<Object> values = new ArrayList<Object>();
  private List<FilterNode> children = new ArrayList<FilterNode>();

  public String getField() {
    return field;
  }

  public void setField(String field) {
    this.field = field;
  }

  public String getOp() {
    return op;
  }

  public void setOp(String op) {
    this.op = op;
  }

  public Object getValue() {
    return value;
  }

  public void setValue(Object value) {
    this.value = value;
  }

  public List<Object> getValues() {
    return values;
  }

  public void setValues(List<Object> values) {
    this.values = values;
  }

  public List<FilterNode> getChildren() {
    return children;
  }

  public void setChildren(List<FilterNode> children) {
    this.children = children;
  }

  public FilterNode copy() {
    FilterNode copied = new FilterNode();
    copied.setField(field);
    copied.setOp(op);
    copied.setValue(value);
    copied.setValues(values == null ? null : new ArrayList<Object>(values));
    List<FilterNode> copiedChildren = new ArrayList<FilterNode>();
    if (children != null) {
      for (FilterNode child : children) {
        copiedChildren.add(child == null ? null : child.copy());
      }
    }
    copied.setChildren(copiedChildren);
    return copied;
  }
}
