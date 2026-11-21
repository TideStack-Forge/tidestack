package com.ouroboros.webapi.openapi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Server Variable 模型
 */
public class ServerVariableModel extends AbstractExtraPropsModel<ServerVariableModel, ServerVariableModel.Builder> implements Model<ServerVariableModel> {

  /**
   * 枚举值
   */
  @JsonProperty("enum")
  private List<String> enumValues;

  /**
   * 默认值
   */
  @JsonProperty("default")
  private String defaultValue;

  /**
   * 描述
   */
  private String description;

  public static Builder builder() {
    return new Builder();
  }

  public List<String> getEnumValues() {
    return enumValues;
  }

  public String getDefaultValue() {
    return defaultValue;
  }

  public String getDescription() {
    return description;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ServerVariableModel, Builder> {
    public Builder() {
      model = new ServerVariableModel();
    }

    public Builder enumValues(List<String> enumValues) {
      model.enumValues = enumValues;
      return this;
    }

    public Builder defaultValue(String defaultValue) {
      model.defaultValue = defaultValue;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }
  }
}
