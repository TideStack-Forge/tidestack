package com.ouroboros.webapi;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;
import com.ouroboros.webapi.openapi.*;

public class SimpleWebAPIModel extends AbstractExtraPropsModel<SimpleWebAPIModel, SimpleWebAPIModel.Builder> implements WebAPIModel, Model<SimpleWebAPIModel> {
  private String method;
  private String path;
  private WebAPIScope scope;
  private HandlerType handlerType;
  private String summary;

  // OpenAPI 3.0 扩展字段
  private String description;
  private String operationId;
  private List<String> tags;
  private List<ParameterModel> parameters;
  private RequestBodyModel requestBody;
  private Map<String, ResponseModel> responses;
  private Boolean deprecated;
  private List<SecurityRequirementModel> security;
  private List<ServerModel> servers;
  private ExternalDocsModel externalDocs;

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public String getMethod() {
    return method;
  }

  @Override
  public String getPath() {
    return path != null
        ? path.startsWith("/") ? path : "/" + path
        : null;
  }

  @Override
  public String getApi() {
    return method + " " + path;
  }

  @Override
  public WebAPIScope getScope() {
    return scope;
  }

  @Override
  public HandlerType getHandlerType() {
    return handlerType;
  }

  @Override
  public String getSummary() {
    return summary;
  }

  @Override
  public String getDescription() {
    return description;
  }

  @Override
  public String getOperationId() {
    return operationId;
  }

  @Override
  public List<String> getTags() {
    return tags != null ? tags : WebAPIModel.super.getTags();
  }

  @Override
  public List<ParameterModel> getParameters() {
    return parameters != null ? parameters : WebAPIModel.super.getParameters();
  }

  @Override
  public RequestBodyModel getRequestBody() {
    return requestBody;
  }

  @Override
  public Map<String, ResponseModel> getResponses() {
    return responses != null ? responses : WebAPIModel.super.getResponses();
  }

  @Override
  public Boolean getDeprecated() {
    return deprecated != null ? deprecated : WebAPIModel.super.getDeprecated();
  }

  @Override
  public List<SecurityRequirementModel> getSecurity() {
    return security != null ? security : WebAPIModel.super.getSecurity();
  }

  @Override
  public List<ServerModel> getServers() {
    return servers != null ? servers : WebAPIModel.super.getServers();
  }

  @Override
  public ExternalDocsModel getExternalDocs() {
    return externalDocs;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<SimpleWebAPIModel, Builder> {
    public Builder() {
      model = new SimpleWebAPIModel();
    }

    public Builder api(String api) {
      var parts = api.split(" ");
      model.method = parts[0];
      model.path = parts[1];
      return this;
    }

    public Builder api(String method, String url) {
      model.method = method;
      model.path = url;
      return this;
    }

    public Builder post(String url) {
      return api("POST", url);
    }

    public Builder get(String url) {
      return api("GET", url);
    }

    public Builder put(String url) {
      return api("PUT", url);
    }

    public Builder delete(String url) {
      return api("DELETE", url);
    }

    public Builder patch(String url) {
      return api("PATCH", url);
    }

    public Builder scope(WebAPIScope scope) {
      model.scope = scope;
      return this;
    }

    public Builder handlerType(HandlerType handlerType) {
      model.handlerType = handlerType;
      return this;
    }

    public Builder summary(String summary) {
      model.summary = summary;
      return this;
    }

    // OpenAPI 3.0 扩展方法

    public Builder description(String description) {
      model.description = description;
      return this;
    }

    public Builder operationId(String operationId) {
      model.operationId = operationId;
      return this;
    }

    public Builder tags(List<String> tags) {
      model.tags = tags;
      return this;
    }

    public Builder tag(String tag) {
      if (model.tags == null) {
        model.tags = new ArrayList<>();
      }
      model.tags.add(tag);
      return this;
    }

    public Builder parameters(List<ParameterModel> parameters) {
      model.parameters = parameters;
      return this;
    }

    public Builder parameter(ParameterModel parameter) {
      if (model.parameters == null) {
        model.parameters = new ArrayList<>();
      }
      model.parameters.add(parameter);
      return this;
    }

    public Builder requestBody(RequestBodyModel requestBody) {
      model.requestBody = requestBody;
      return this;
    }

    public Builder responses(Map<String, ResponseModel> responses) {
      model.responses = responses;
      return this;
    }

    public Builder response(String statusCode, ResponseModel response) {
      if (model.responses == null) {
        model.responses = new HashMap<>();
      }
      model.responses.put(statusCode, response);
      return this;
    }

    public Builder deprecated(Boolean deprecated) {
      model.deprecated = deprecated;
      return this;
    }

    public Builder security(List<SecurityRequirementModel> security) {
      model.security = security;
      return this;
    }

    public Builder securityRequirement(SecurityRequirementModel requirement) {
      if (model.security == null) {
        model.security = new ArrayList<>();
      }
      model.security.add(requirement);
      return this;
    }

    public Builder servers(List<ServerModel> servers) {
      model.servers = servers;
      return this;
    }

    public Builder server(ServerModel server) {
      if (model.servers == null) {
        model.servers = new ArrayList<>();
      }
      model.servers.add(server);
      return this;
    }

    public Builder externalDocs(ExternalDocsModel externalDocs) {
      model.externalDocs = externalDocs;
      return this;
    }
  }
}
