package com.ouroboros.webapi.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.ouroboros.AppContext;
import com.ouroboros.model.AppInfo;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelCatalog;
import com.ouroboros.webapi.openapi.OpenAPIDocumentModel;
import com.ouroboros.webapi.openapi.ServerModel;

@Service
public class OpenAPIDocumentService {
  private static final String DEFAULT_TITLE = "Ouroboros WebAPI";
  private static final String DEFAULT_VERSION = "dev";

  private final WebAPIModelCatalog webAPIModelCatalog;
  private final OpenAPIDocumentBuilder openAPIDocumentBuilder;
  private final AppContext appContext;

  @Autowired
  public OpenAPIDocumentService(
      WebAPIModelCatalog webAPIModelCatalog,
      OpenAPIDocumentBuilder openAPIDocumentBuilder,
      ObjectProvider<AppContext> appContextProvider
  ) {
    this.webAPIModelCatalog = webAPIModelCatalog;
    this.openAPIDocumentBuilder = openAPIDocumentBuilder;
    this.appContext = appContextProvider.getIfAvailable();
  }

  public OpenAPIDocumentService(
      WebAPIModelCatalog webAPIModelCatalog,
      OpenAPIDocumentBuilder openAPIDocumentBuilder,
      AppContext appContext
  ) {
    this.webAPIModelCatalog = webAPIModelCatalog;
    this.openAPIDocumentBuilder = openAPIDocumentBuilder;
    this.appContext = appContext;
  }

  public OpenAPIDocumentModel export(OpenAPIExportRequest request) {
    OpenAPIExportRequest effectiveRequest = request != null ? request : OpenAPIExportRequest.builder().build();
    List<WebAPIModel> filteredModels = filterModels(webAPIModelCatalog.listAll(), effectiveRequest);
    OpenAPIExportRequest resolvedRequest = resolveInfo(effectiveRequest);
    List<ServerModel> servers = mergeServers(effectiveRequest, filteredModels);
    return openAPIDocumentBuilder.build(filteredModels, resolvedRequest, servers);
  }

  private List<WebAPIModel> filterModels(List<WebAPIModel> models, OpenAPIExportRequest request) {
    List<WebAPIModel> filtered = new ArrayList<>();
    for (WebAPIModel model : models) {
      if (!matchesScope(model, request)) {
        continue;
      }
      if (!matchesHandlerType(model, request)) {
        continue;
      }
      if (!matchesTag(model, request)) {
        continue;
      }
      if (!matchesPathPrefix(model, request)) {
        continue;
      }
      if (!matchesApi(model, request)) {
        continue;
      }
      filtered.add(model);
    }
    return filtered;
  }

  private boolean matchesScope(WebAPIModel model, OpenAPIExportRequest request) {
    return request.getScopes().isEmpty() || request.getScopes().contains(model.getScope());
  }

  private boolean matchesHandlerType(WebAPIModel model, OpenAPIExportRequest request) {
    return request.getHandlerTypes().isEmpty() || request.getHandlerTypes().contains(model.getHandlerType());
  }

  private boolean matchesTag(WebAPIModel model, OpenAPIExportRequest request) {
    if (request.getTags().isEmpty()) {
      return true;
    }
    for (String tag : model.getTags()) {
      if (request.getTags().contains(tag)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesPathPrefix(WebAPIModel model, OpenAPIExportRequest request) {
    if (request.getPathPrefixes().isEmpty()) {
      return true;
    }
    String path = StringUtils.defaultString(model.getPath());
    for (String pathPrefix : request.getPathPrefixes()) {
      if (path.startsWith(pathPrefix)) {
        return true;
      }
    }
    return false;
  }

  private boolean matchesApi(WebAPIModel model, OpenAPIExportRequest request) {
    return request.getApis().isEmpty() || request.getApis().contains(buildApi(model));
  }

  private String buildApi(WebAPIModel model) {
    return StringUtils.trimToEmpty(model.getMethod()) + " " + StringUtils.trimToEmpty(model.getPath());
  }

  private OpenAPIExportRequest resolveInfo(OpenAPIExportRequest request) {
    return OpenAPIExportRequest.builder()
        .scopes(request.getScopes())
        .handlerTypes(request.getHandlerTypes())
        .tags(request.getTags())
        .pathPrefixes(request.getPathPrefixes())
        .apis(request.getApis())
        .title(resolveTitle(request.getTitle()))
        .version(resolveVersion(request.getVersion()))
        .serverUrls(request.getServerUrls())
        .pretty(request.getPretty())
        .build();
  }

  private String resolveTitle(String titleOverride) {
    if (StringUtils.isNotBlank(titleOverride)) {
      return titleOverride;
    }

    AppInfo appInfo = appContext != null ? appContext.getAppInfo().orElse(null) : null;
    if (appInfo != null) {
      if (StringUtils.isNotBlank(appInfo.getTitle())) {
        return appInfo.getTitle();
      }
      if (StringUtils.isNotBlank(appInfo.getName())) {
        return appInfo.getName();
      }
    }

    return DEFAULT_TITLE;
  }

  private String resolveVersion(String versionOverride) {
    if (StringUtils.isNotBlank(versionOverride)) {
      return versionOverride;
    }
    String implementationVersion = OpenAPIDocumentService.class.getPackage().getImplementationVersion();
    return StringUtils.isNotBlank(implementationVersion) ? implementationVersion : DEFAULT_VERSION;
  }

  private List<ServerModel> mergeServers(OpenAPIExportRequest request, List<WebAPIModel> models) {
    Map<String, ServerModel> merged = new LinkedHashMap<>();
    for (String serverUrl : request.getServerUrls()) {
      if (StringUtils.isBlank(serverUrl)) {
        continue;
      }
      merged.putIfAbsent("url:" + serverUrl, ServerModel.builder().url(serverUrl).build());
    }

    for (WebAPIModel model : models) {
      for (ServerModel server : model.getServers()) {
        putServer(merged, server);
      }
    }

    return new ArrayList<>(merged.values());
  }

  private void putServer(Map<String, ServerModel> merged, ServerModel server) {
    if (server == null) {
      return;
    }
    String url = StringUtils.trimToNull(server.getUrl());
    if (url != null) {
      merged.putIfAbsent("url:" + url, server);
      return;
    }
    merged.put("generated:" + merged.size(), server);
  }
}
