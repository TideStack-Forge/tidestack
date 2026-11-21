package com.ouroboros.webapi.openapi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.model.AbstractExtraPropsModel;
import com.ouroboros.model.Model;

/**
 * OpenAPI 3.1 顶层文档模型
 */
public class OpenAPIDocumentModel extends AbstractExtraPropsModel<OpenAPIDocumentModel, OpenAPIDocumentModel.Builder> implements Model<OpenAPIDocumentModel> {
  private String openapi;
  private String jsonSchemaDialect;
  private InfoModel info;
  private List<ServerModel> servers;
  private Map<String, PathItemModel> paths;
  private ComponentsModel components;

  public static Builder builder() {
    return new Builder();
  }

  public String getOpenapi() {
    return openapi;
  }

  public String getJsonSchemaDialect() {
    return jsonSchemaDialect;
  }

  public InfoModel getInfo() {
    return info;
  }

  public List<ServerModel> getServers() {
    return servers != null ? servers : Collections.emptyList();
  }

  public Map<String, PathItemModel> getPaths() {
    return paths != null ? paths : Collections.emptyMap();
  }

  public ComponentsModel getComponents() {
    return components;
  }

  @Override
  protected Builder createBuilder() {
    return builder();
  }

  public static class Builder extends AbstractExtraPropsModel.Builder<OpenAPIDocumentModel, Builder> {
    public Builder() {
      model = new OpenAPIDocumentModel();
    }

    public Builder openapi(String openapi) {
      model.openapi = openapi;
      return this;
    }

    public Builder jsonSchemaDialect(String jsonSchemaDialect) {
      model.jsonSchemaDialect = jsonSchemaDialect;
      return this;
    }

    public Builder info(InfoModel info) {
      model.info = info;
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

    public Builder paths(Map<String, PathItemModel> paths) {
      model.paths = paths;
      return this;
    }

    public Builder path(String path, PathItemModel pathItem) {
      if (model.paths == null) {
        model.paths = new LinkedHashMap<>();
      }
      model.paths.put(path, pathItem);
      return this;
    }

    public Builder components(ComponentsModel components) {
      model.components = components;
      return this;
    }
  }
}
