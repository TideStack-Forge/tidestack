package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Media Type 模型
 */
public class MediaTypeModel extends AbstractExtraPropsModel<MediaTypeModel, MediaTypeModel.Builder> implements Model<MediaTypeModel> {

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

  /**
   * 编码信息
   */
  private Map<String, EncodingModel> encoding;

  public static Builder builder() {
    return new Builder();
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

  public Map<String, EncodingModel> getEncoding() {
    return encoding;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<MediaTypeModel, Builder> {
    public Builder() {
      model = new MediaTypeModel();
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

    public Builder encoding(Map<String, EncodingModel> encoding) {
      model.encoding = encoding;
      return this;
    }
  }
}
