package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Encoding 模型
 * 用于描述特定属性的编码信息
 */
public class EncodingModel extends AbstractExtraPropsModel<EncodingModel, EncodingModel.Builder> implements Model<EncodingModel> {

  /**
   * Content-Type
   */
  private String contentType;

  /**
   * Headers
   */
  private Map<String, HeaderModel> headers;

  /**
   * Style（form, spaceDelimited, pipeDelimited, deepObject）
   */
  private String style;

  /**
   * 是否展开
   */
  private Boolean explode;

  /**
   * 是否允许保留字符
   */
  private Boolean allowReserved;

  public static Builder builder() {
    return new Builder();
  }

  public String getContentType() {
    return contentType;
  }

  public Map<String, HeaderModel> getHeaders() {
    return headers;
  }

  public String getStyle() {
    return style;
  }

  public Boolean getExplode() {
    return explode;
  }

  public Boolean getAllowReserved() {
    return allowReserved;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<EncodingModel, Builder> {
    public Builder() {
      model = new EncodingModel();
    }

    public Builder contentType(String contentType) {
      model.contentType = contentType;
      return this;
    }

    public Builder headers(Map<String, HeaderModel> headers) {
      model.headers = headers;
      return this;
    }

    public Builder style(String style) {
      model.style = style;
      return this;
    }

    public Builder explode(Boolean explode) {
      model.explode = explode;
      return this;
    }

    public Builder allowReserved(Boolean allowReserved) {
      model.allowReserved = allowReserved;
      return this;
    }
  }
}
