package com.ouroboros.errorcode.dev.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ouroboros.errorcode.model.ErrorCodeDefinition;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ErrorCodeDefinitionModel extends ErrorCodeDefinition {
  @Override
  public ErrorCodeDefinitionModel setCode(Integer code) {
    super.setCode(code);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setTitle(String title) {
    super.setTitle(title);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setDefaultMessageTemplate(String defaultMessageTemplate) {
    super.setDefaultMessageTemplate(defaultMessageTemplate);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setBizCode(String bizCode) {
    super.setBizCode(bizCode);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setCategory(String category) {
    super.setCategory(category);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setDescription(String description) {
    super.setDescription(description);
    return this;
  }

  @Override
  public ErrorCodeDefinitionModel setEnabled(Boolean enabled) {
    super.setEnabled(enabled);
    return this;
  }
}
