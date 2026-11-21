package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 参数模型
 */
public class ParameterModel extends AbstractExtraPropsModel<ParameterModel, ParameterModel.Builder> implements Model<ParameterModel> {

  /**
   * 参数名称
   */
  private String name;

  /**
   * 参数位置
   */
  private ParameterLocation in;

  /**
   * 参数描述
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
   * 是否允许空值
   */
  private Boolean allowEmptyValue;

  /**
   * 参数 schema（类型、格式等）
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

  public String getName() {
    return name;
  }

  public ParameterLocation getIn() {
    return in;
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

  public Boolean getAllowEmptyValue() {
    return allowEmptyValue;
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

  public static class Builder extends AbstractExtraPropsModel.Builder<ParameterModel, Builder> {
    public Builder() {
      model = new ParameterModel();
    }

    public Builder name(String name) {
      model.name = name;
      return this;
    }

    public Builder in(ParameterLocation in) {
      model.in = in;
      return this;
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

    public Builder allowEmptyValue(Boolean allowEmptyValue) {
      model.allowEmptyValue = allowEmptyValue;
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
