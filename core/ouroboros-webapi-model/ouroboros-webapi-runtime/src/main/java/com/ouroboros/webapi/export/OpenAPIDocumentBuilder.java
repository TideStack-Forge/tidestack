package com.ouroboros.webapi.export;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.openapi.ExternalDocsModel;
import com.ouroboros.webapi.openapi.InfoModel;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.OpenAPIDocumentModel;
import com.ouroboros.webapi.openapi.OperationModel;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.PathItemModel;
import com.ouroboros.webapi.openapi.RequestBodyModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;
import com.ouroboros.webapi.openapi.SchemaModelJsonSchemaSupport;
import com.ouroboros.webapi.openapi.SecurityRequirementModel;
import com.ouroboros.webapi.openapi.ServerModel;

@Component
public class OpenAPIDocumentBuilder {
  private static final Logger logger = LoggerFactory.getLogger(OpenAPIDocumentBuilder.class);

  public OpenAPIDocumentModel build(
      List<WebAPIModel> webAPIModels,
      OpenAPIExportRequest exportRequest,
      List<ServerModel> servers
  ) {
    SchemaComponentRegistry componentRegistry = new SchemaComponentRegistry();
    Map<String, PathItemModel.Builder> pathBuilders = new LinkedHashMap<>();

    for (WebAPIModel webAPIModel : webAPIModels) {
      PathItemModel.Builder pathBuilder = pathBuilders.computeIfAbsent(webAPIModel.getPath(), key -> PathItemModel.builder());
      OperationModel operation = toOperationModel(webAPIModel, componentRegistry);
      applyOperation(pathBuilder, webAPIModel.getMethod(), operation, webAPIModel.getPath());
    }

    Map<String, PathItemModel> paths = new LinkedHashMap<>();
    pathBuilders.forEach((path, builder) -> paths.put(path, builder.build()));

    InfoModel info = InfoModel.builder()
        .title(exportRequest.getTitle())
        .version(exportRequest.getVersion())
        .build();

    return OpenAPIDocumentModel.builder()
        .openapi("3.1.0")
        .jsonSchemaDialect(SchemaModelJsonSchemaSupport.OPENAPI_31_BASE_DIALECT)
        .info(info)
        .servers(servers != null ? new ArrayList<>(servers) : new ArrayList<ServerModel>())
        .paths(paths)
        .components(componentRegistry.buildComponents())
        .build();
  }

  private OperationModel toOperationModel(WebAPIModel webAPIModel, SchemaComponentRegistry componentRegistry) {
    return OperationModel.builder()
        .summary(webAPIModel.getSummary())
        .description(webAPIModel.getDescription())
        .operationId(webAPIModel.getOperationId())
        .tags(new ArrayList<>(webAPIModel.getTags()))
        .parameters(copyParameters(webAPIModel.getParameters(), componentRegistry))
        .requestBody(copyRequestBody(webAPIModel.getRequestBody(), componentRegistry))
        .responses(copyResponses(webAPIModel.getResponses(), componentRegistry))
        .deprecated(webAPIModel.getDeprecated())
        .security(new ArrayList<SecurityRequirementModel>(webAPIModel.getSecurity()))
        .servers(new ArrayList<ServerModel>(webAPIModel.getServers()))
        .externalDocs(copyExternalDocs(webAPIModel.getExternalDocs()))
        .build();
  }

  private List<ParameterModel> copyParameters(List<ParameterModel> parameters, SchemaComponentRegistry componentRegistry) {
    List<ParameterModel> copied = new ArrayList<>();
    parameters.forEach(parameter -> copied.add(parameter.map(builder ->
        builder.schema(SchemaModelJsonSchemaSupport.normalizeForOpenAPI31(componentRegistry.rewriteForUsage(parameter.getSchema())))
    )));
    return copied;
  }

  private RequestBodyModel copyRequestBody(RequestBodyModel requestBody, SchemaComponentRegistry componentRegistry) {
    if (requestBody == null) {
      return null;
    }
    return requestBody.map(builder -> builder.content(copyMediaTypes(requestBody.getContent(), componentRegistry)));
  }

  private Map<String, ResponseModel> copyResponses(Map<String, ResponseModel> responses, SchemaComponentRegistry componentRegistry) {
    Map<String, ResponseModel> copied = new LinkedHashMap<>();
    responses.forEach((status, response) -> copied.put(status,
        response.map(builder -> builder.content(copyMediaTypes(response.getContent(), componentRegistry)))
    ));
    return copied;
  }

  private Map<String, MediaTypeModel> copyMediaTypes(Map<String, MediaTypeModel> mediaTypes, SchemaComponentRegistry componentRegistry) {
    if (mediaTypes == null || mediaTypes.isEmpty()) {
      return mediaTypes;
    }
    Map<String, MediaTypeModel> copied = new LinkedHashMap<>();
    mediaTypes.forEach((contentType, mediaType) -> copied.put(contentType,
        mediaType.map(builder -> builder.schema(
            SchemaModelJsonSchemaSupport.normalizeForOpenAPI31(componentRegistry.rewriteForUsage(mediaType.getSchema()))
        ))
    ));
    return copied;
  }

  private ExternalDocsModel copyExternalDocs(ExternalDocsModel externalDocs) {
    return externalDocs != null ? externalDocs.clone() : null;
  }

  private void applyOperation(PathItemModel.Builder pathBuilder, String method, OperationModel operation, String path) {
    String normalized = method != null ? method.toLowerCase(Locale.ROOT) : "";
    switch (normalized) {
      case "get":
        warnIfPresent(pathBuilder.build().getGet(), path, normalized);
        pathBuilder.get(operation);
        break;
      case "put":
        warnIfPresent(pathBuilder.build().getPut(), path, normalized);
        pathBuilder.put(operation);
        break;
      case "post":
        warnIfPresent(pathBuilder.build().getPost(), path, normalized);
        pathBuilder.post(operation);
        break;
      case "delete":
        warnIfPresent(pathBuilder.build().getDelete(), path, normalized);
        pathBuilder.delete(operation);
        break;
      case "options":
        warnIfPresent(pathBuilder.build().getOptions(), path, normalized);
        pathBuilder.options(operation);
        break;
      case "head":
        warnIfPresent(pathBuilder.build().getHead(), path, normalized);
        pathBuilder.head(operation);
        break;
      case "patch":
        warnIfPresent(pathBuilder.build().getPatch(), path, normalized);
        pathBuilder.patch(operation);
        break;
      case "trace":
        warnIfPresent(pathBuilder.build().getTrace(), path, normalized);
        pathBuilder.trace(operation);
        break;
      default:
        logger.warn("Unsupported method {} for path {}, skipping OpenAPI export", method, path);
    }
  }

  private void warnIfPresent(OperationModel existing, String path, String method) {
    if (existing != null) {
      logger.warn("Duplicate OpenAPI operation detected for {} {}. Keeping the later model.", method, path);
    }
  }
}
