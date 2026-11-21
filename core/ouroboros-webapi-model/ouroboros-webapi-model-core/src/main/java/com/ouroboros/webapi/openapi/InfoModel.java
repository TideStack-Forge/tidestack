package com.ouroboros.webapi.openapi;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 文档信息模型
 */
public class InfoModel extends AbstractExtraPropsModel<InfoModel, InfoModel.Builder> implements Model<InfoModel> {
  private String title;
  private String version;
  private String description;

  public static Builder builder() {
    return new Builder();
  }

  public String getTitle() {
    return title;
  }

  public String getVersion() {
    return version;
  }

  public String getDescription() {
    return description;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<InfoModel, Builder> {
    public Builder() {
      model = new InfoModel();
    }

    public Builder title(String title) {
      model.title = title;
      return this;
    }

    public Builder version(String version) {
      model.version = version;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }
  }
}
