package com.ouroboros.workflow.approval.activiti6.variable;

import java.time.LocalDateTime;

import org.activiti.engine.impl.variable.ValueFields;
import org.activiti.engine.impl.variable.VariableType;
import org.springframework.stereotype.Component;

@Component
public class LocalDateTimeVariableType implements VariableType {
  @Override
  public String getTypeName() {
    return "localdatetime";
  }

  @Override
  public boolean isCachable() {
    return true;
  }

  @Override
  public boolean isAbleToStore(Object value) {
    if (value == null) {
      return true;
    }
    return LocalDateTime.class.isAssignableFrom(value.getClass());
  }

  @Override
  public void setValue(Object value, ValueFields valueFields) {
    if (value == null) {
      valueFields.setTextValue(null);
    } else {
      valueFields.setTextValue(value.toString());
    }
  }

  @Override
  public Object getValue(ValueFields valueFields) {
    String textValue = valueFields.getTextValue();
    if (textValue == null) {
      return null;
    }
    return LocalDateTime.parse(textValue);
  }
}
