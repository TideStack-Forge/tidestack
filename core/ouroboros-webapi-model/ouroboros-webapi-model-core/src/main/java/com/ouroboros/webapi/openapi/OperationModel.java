package com.ouroboros.webapi.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 Operation 模型
 */
public class OperationModel extends AbstractExtraPropsModel<OperationModel, OperationModel.Builder> implements Model<OperationModel> {
  private String summary;
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

  public String getSummary() {
    return summary;
  }

  public String getDescription() {
    return description;
  }

  public String getOperationId() {
    return operationId;
  }

  public List<String> getTags() {
    return tags != null ? tags : Collections.emptyList();
  }

  public List<ParameterModel> getParameters() {
    return parameters != null ? parameters : Collections.emptyList();
  }

  public RequestBodyModel getRequestBody() {
    return requestBody;
  }

  public Map<String, ResponseModel> getResponses() {
    return responses != null ? responses : Collections.emptyMap();
  }

  public Boolean getDeprecated() {
    return deprecated;
  }

  public List<SecurityRequirementModel> getSecurity() {
    return security != null ? security : Collections.emptyList();
  }

  public List<ServerModel> getServers() {
    return servers != null ? servers : Collections.emptyList();
  }

  public ExternalDocsModel getExternalDocs() {
    return externalDocs;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<OperationModel, Builder> {
    public Builder() {
      model = new OperationModel();
    }

    public Builder summary(String summary) {
      model.summary = summary;
      return this;
    }

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
        model.responses = new LinkedHashMap<>();
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

    public Builder security(SecurityRequirementModel securityRequirement) {
      if (model.security == null) {
        model.security = new ArrayList<>();
      }
      model.security.add(securityRequirement);
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
