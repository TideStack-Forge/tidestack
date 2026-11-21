package com.ouroboros.webapi.openapi;

import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.0 Link 模型
 */
public class LinkModel extends AbstractExtraPropsModel<LinkModel, LinkModel.Builder> implements Model<LinkModel> {

  /**
   * 操作引用
   */
  private String operationRef;

  /**
   * 操作 ID
   */
  private String operationId;

  /**
   * 参数映射
   */
  private Map<String, Object> parameters;

  /**
   * 请求体
   */
  private Object requestBody;

  /**
   * 描述
   */
  private String description;

  /**
   * 服务器
   */
  private ServerModel server;

  public static Builder builder() {
    return new Builder();
  }

  public String getOperationRef() {
    return operationRef;
  }

  public String getOperationId() {
    return operationId;
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public Object getRequestBody() {
    return requestBody;
  }

  public String getDescription() {
    return description;
  }

  public ServerModel getServer() {
    return server;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<LinkModel, Builder> {
    public Builder() {
      model = new LinkModel();
    }

    public Builder operationRef(String operationRef) {
      model.operationRef = operationRef;
      return this;
    }

    public Builder operationId(String operationId) {
      model.operationId = operationId;
      return this;
    }

    public Builder parameters(Map<String, Object> parameters) {
      model.parameters = parameters;
      return this;
    }

    public Builder requestBody(Object requestBody) {
      model.requestBody = requestBody;
      return this;
    }

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder server(ServerModel server) {
      model.server = server;
      return this;
    }
  }
}
