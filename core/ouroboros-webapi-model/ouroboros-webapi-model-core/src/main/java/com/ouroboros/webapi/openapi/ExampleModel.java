package com.ouroboros.webapi.openapi;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Example 模型
 */
public class ExampleModel extends AbstractExtraPropsModel<ExampleModel, ExampleModel.Builder> implements Model<ExampleModel> {

  /**
   * 示例摘要
   */
  private String summary;

  /**
   * 示例描述
   */
  private String description;

  /**
   * 示例值
   */
  private Object value;

  /**
   * 外部示例 URL
   */
  private String externalValue;

  public static Builder builder() {
    return new Builder();
  }

  public String getSummary() {
    return summary;
  }

  public String getDescription() {
    return description;
  }

  public Object getValue() {
    return value;
  }

  public String getExternalValue() {
    return externalValue;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ExampleModel, Builder> {
    public Builder() {
      model = new ExampleModel();
    }

    public Builder summary(String summary) {
      model.summary = summary;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder value(Object value) {
      model.value = value;
      return this;
    }

    public Builder externalValue(String externalValue) {
      model.externalValue = externalValue;
      return this;
    }
  }
}
