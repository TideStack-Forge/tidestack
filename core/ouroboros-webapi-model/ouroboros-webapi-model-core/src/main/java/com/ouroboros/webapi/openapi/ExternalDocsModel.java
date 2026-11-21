package com.ouroboros.webapi.openapi;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 External Documentation 模型
 */
public class ExternalDocsModel extends AbstractExtraPropsModel<ExternalDocsModel, ExternalDocsModel.Builder> implements Model<ExternalDocsModel> {

  /**
   * 描述
   */
  private String description;

  /**
   * URL
   */
  private String url;

  public static Builder builder() {
    return new Builder();
  }

  public String getDescription() {
    return description;
  }

  public String getUrl() {
    return url;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ExternalDocsModel, Builder> {
    public Builder() {
      model = new ExternalDocsModel();
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder url(String url) {
      model.url = url;
      return this;
    }
  }
}
