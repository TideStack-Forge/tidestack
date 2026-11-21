package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Header 模型
 */
public class HeaderModel extends AbstractExtraPropsModel<HeaderModel, HeaderModel.Builder> implements Model<HeaderModel> {

  /**
   * 描述
   */
  private String description;

  /**
   * 是否必填
   */
  private Boolean required;

  /**
   * 是否废弃
   */
  private Boolean deprecated;

  /**
   * Schema 定义
   */
  private SchemaModel schema;

  /**
   * 示例值
   */
  private Object example;

  /**
   * 多个示例
   */
  private Map<String, ExampleModel> examples;

  public static Builder builder() {
    return new Builder();
  }

  public String getDescription() {
    return description;
  }

  public Boolean getRequired() {
    return required;
  }

  public Boolean getDeprecated() {
    return deprecated;
  }

  public SchemaModel getSchema() {
    return schema;
  }

  public Object getExample() {
    return example;
  }

  public Map<String, ExampleModel> getExamples() {
    return examples;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<HeaderModel, Builder> {
    public Builder() {
      model = new HeaderModel();
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder required(Boolean required) {
      model.required = required;
      return this;
    }

    public Builder deprecated(Boolean deprecated) {
      model.deprecated = deprecated;
      return this;
    }

    public Builder schema(SchemaModel schema) {
      model.schema = schema;
      return this;
    }

    public Builder example(Object example) {
      model.example = example;
      return this;
    }

    public Builder examples(Map<String, ExampleModel> examples) {
      model.examples = examples;
      return this;
    }
  }
}
