package com.ouroboros.webapi.export;

import java.util.Collections;
import java.util.List;

import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIScope;

public class OpenAPIExportRequest {
  private List<WebAPIScope> scopes;
  private List<HandlerType> handlerTypes;
  private List<String> tags;
  private List<String> pathPrefixes;
  private List<String> apis;
  private String title;
  private String version;
  private List<String> serverUrls;
  private Boolean pretty;

  public static Builder builder() {
    return new Builder();
  }

  public List<WebAPIScope> getScopes() {
    return scopes != null ? scopes : Collections.emptyList();
  }

  public List<HandlerType> getHandlerTypes() {
    return handlerTypes != null ? handlerTypes : Collections.emptyList();
  }

  public List<String> getTags() {
    return tags != null ? tags : Collections.emptyList();
  }

  public List<String> getPathPrefixes() {
    return pathPrefixes != null ? pathPrefixes : Collections.emptyList();
  }

  public List<String> getApis() {
    return apis != null ? apis : Collections.emptyList();
  }

  public String getTitle() {
    return title;
  }

  public String getVersion() {
    return version;
  }

  public List<String> getServerUrls() {
    return serverUrls != null ? serverUrls : Collections.emptyList();
  }

  public Boolean getPretty() {
    return pretty != null ? pretty : Boolean.FALSE;
  }

  public static class Builder {
    private final OpenAPIExportRequest model = new OpenAPIExportRequest();

    public Builder scopes(List<WebAPIScope> scopes) {
      model.scopes = scopes;
      return this;
    }

    public Builder handlerTypes(List<HandlerType> handlerTypes) {
      model.handlerTypes = handlerTypes;
      return this;
    }

    public Builder tags(List<String> tags) {
      model.tags = tags;
      return this;
    }

    public Builder pathPrefixes(List<String> pathPrefixes) {
      model.pathPrefixes = pathPrefixes;
      return this;
    }

    public Builder apis(List<String> apis) {
      model.apis = apis;
      return this;
    }

    public Builder title(String title) {
      model.title = title;
      return this;
    }

    public Builder version(String version) {
      model.version = version;
      return this;
    }

    public Builder serverUrls(List<String> serverUrls) {
      model.serverUrls = serverUrls;
      return this;
    }

    public Builder pretty(Boolean pretty) {
      model.pretty = pretty;
      return this;
    }

    public OpenAPIExportRequest build() {
      return model;
    }
  }
}
