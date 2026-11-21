package com.ouroboros.statistics.reporting.definition.parameter;

public class ParameterDefinition {
  private String name;
  private String title;
  private String type;
  private Boolean required = false;
  private Object defaultValue;
  private String operator;
  private Boolean multiValue = false;
  private Boolean allowEmptyOmitCondition = false;
  private String valueSource;
  private String systemContextBinding;
  private Boolean visibleInView = true;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Boolean getRequired() {
    return required;
  }

  public boolean isRequired() {
    return Boolean.TRUE.equals(required);
  }

  public void setRequired(Boolean required) {
    this.required = required;
  }

  public Object getDefaultValue() {
    return defaultValue;
  }

  public void setDefaultValue(Object defaultValue) {
    this.defaultValue = defaultValue;
  }

  public String getOperator() {
    return operator;
  }

  public void setOperator(String operator) {
    this.operator = operator;
  }

  public Boolean getMultiValue() {
    return multiValue;
  }

  public boolean isMultiValue() {
    return Boolean.TRUE.equals(multiValue);
  }

  public void setMultiValue(Boolean multiValue) {
    this.multiValue = multiValue;
  }

  public Boolean getAllowEmptyOmitCondition() {
    return allowEmptyOmitCondition;
  }

  public boolean isAllowEmptyOmitCondition() {
    return Boolean.TRUE.equals(allowEmptyOmitCondition);
  }

  public void setAllowEmptyOmitCondition(Boolean allowEmptyOmitCondition) {
    this.allowEmptyOmitCondition = allowEmptyOmitCondition;
  }

  public String getValueSource() {
    return valueSource;
  }

  public void setValueSource(String valueSource) {
    this.valueSource = valueSource;
  }

  public String getSystemContextBinding() {
    return systemContextBinding;
  }

  public void setSystemContextBinding(String systemContextBinding) {
    this.systemContextBinding = systemContextBinding;
  }

  public Boolean getVisibleInView() {
    return visibleInView;
  }

  public boolean isVisibleInView() {
    return !Boolean.FALSE.equals(visibleInView);
  }

  public void setVisibleInView(Boolean visibleInView) {
    this.visibleInView = visibleInView;
  }
}
