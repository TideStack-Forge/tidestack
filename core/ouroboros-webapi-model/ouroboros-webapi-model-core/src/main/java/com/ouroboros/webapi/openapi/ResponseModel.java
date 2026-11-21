package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Response 模型
 */
public class ResponseModel extends AbstractExtraPropsModel<ResponseModel, ResponseModel.Builder> implements Model<ResponseModel> {

  /**
   * 响应描述
   */
  private String description;

  /**
   * 响应头
   */
  private Map<String, HeaderModel> headers;

  /**
   * 响应内容（按 Content-Type 分类）
   */
  private Map<String, MediaTypeModel> content;

  /**
   * 链接
   */
  private Map<String, LinkModel> links;

  public static Builder builder() {
    return new Builder();
  }

  public String getDescription() {
    return description;
  }

  public Map<String, HeaderModel> getHeaders() {
    return headers;
  }

  public Map<String, MediaTypeModel> getContent() {
    return content;
  }

  public Map<String, LinkModel> getLinks() {
    return links;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ResponseModel, Builder> {
    public Builder() {
      model = new ResponseModel();
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder headers(Map<String, HeaderModel> headers) {
      model.headers = headers;
      return this;
    }

    public Builder content(Map<String, MediaTypeModel> content) {
      model.content = content;
      return this;
    }

    public Builder links(Map<String, LinkModel> links) {
      model.links = links;
      return this;
    }
  }
}
