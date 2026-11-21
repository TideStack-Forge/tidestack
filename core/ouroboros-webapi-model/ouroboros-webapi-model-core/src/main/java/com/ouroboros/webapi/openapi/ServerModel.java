package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Server 模型
 */
public class ServerModel extends AbstractExtraPropsModel<ServerModel, ServerModel.Builder> implements Model<ServerModel> {

  /**
   * 服务器 URL
   */
  private String url;

  /**
   * 描述
   */
  private String description;

  /**
   * URL 变量
   */
  private Map<String, ServerVariableModel> variables;

  public static Builder builder() {
    return new Builder();
  }

  public String getUrl() {
    return url;
  }

  public String getDescription() {
    return description;
  }

  public Map<String, ServerVariableModel> getVariables() {
    return variables;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<ServerModel, Builder> {
    public Builder() {
      model = new ServerModel();
    }

    public Builder url(String url) {
      model.url = url;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder variables(Map<String, ServerVariableModel> variables) {
      model.variables = variables;
      return this;
    }
  }
}
