package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Request Body 模型
 */
public class RequestBodyModel extends AbstractExtraPropsModel<RequestBodyModel, RequestBodyModel.Builder> implements Model<RequestBodyModel> {

  /**
   * 请求体描述
   */
  private String description;

  /**
   * 请求体内容（按 Content-Type 分类）
   */
  private Map<String, MediaTypeModel> content;

  /**
   * 是否必填
   */
  private Boolean required;

  public static Builder builder() {
    return new Builder();
  }

  public String getDescription() {
    return description;
  }

  public Map<String, MediaTypeModel> getContent() {
    return content;
  }

  public Boolean getRequired() {
    return required;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<RequestBodyModel, Builder> {
    public Builder() {
      model = new RequestBodyModel();
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder content(Map<String, MediaTypeModel> content) {
      model.content = content;
      return this;
    }

    public Builder required(Boolean required) {
      model.required = required;
      return this;
    }
  }
}
