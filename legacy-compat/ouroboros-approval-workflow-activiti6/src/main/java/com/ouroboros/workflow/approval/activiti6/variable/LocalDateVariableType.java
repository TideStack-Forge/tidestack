package com.ouroboros.workflow.approval.activiti6.variable;

import java.time.LocalDate;

import org.activiti.engine.impl.variable.ValueFields;
import org.activiti.engine.impl.variable.VariableType;
import org.springframework.stereotype.Component;

@Component
public class LocalDateVariableType implements VariableType {
  @Override
  public String getTypeName() {
    return "localdate";
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
    return LocalDate.class.isAssignableFrom(value.getClass());
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
    return LocalDate.parse(textValue);
  }
}
