package com.ouroboros.webapi.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.export.OpenAPIExportRequest;
import com.ouroboros.webapi.export.OpenAPIDocumentService;

@RestController
@RequestMapping("/api/sys/webapi")
@DependsOn("requestMappingHandlerMapping")
public class OpenAPIDocumentController {
  private final OpenAPIDocumentService openAPIDocumentService;
  private final ObjectMapper objectMapper;

  public OpenAPIDocumentController(OpenAPIDocumentService openAPIDocumentService, ObjectMapper objectMapper) {
    this.openAPIDocumentService = openAPIDocumentService;
    this.objectMapper = objectMapper.copy()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL);
  }

  @Operation(
      summary = "导出 OpenAPI 文档 JSON",
      description = "根据筛选条件导出当前注册 WebAPI 模型对应的 OpenAPI 3.1 JSON 文档。",
      tags = {"webapi-admin"}
  )
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功返回 OpenAPI JSON 文档"),
      @ApiResponse(responseCode = "500", description = "OpenAPI 文档序列化失败")
  })
  @GetMapping(value = "/openapi.json", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<String> exportOpenApiJson(
      @Parameter(description = "按 WebAPI 作用域筛选，例如 internal、public")
      @RequestParam(value = "scope", required = false) List<String> scopes,
      @Parameter(description = "按处理器类型筛选，例如 requestMapping")
      @RequestParam(value = "handlerType", required = false) List<String> handlerTypes,
      @Parameter(description = "按标签筛选 OpenAPI 文档中的接口")
      @RequestParam(value = "tag", required = false) List<String> tags,
      @Parameter(description = "按请求路径前缀筛选接口")
      @RequestParam(value = "pathPrefix", required = false) List<String> pathPrefixes,
      @Parameter(description = "按 API 唯一路径标识筛选接口")
      @RequestParam(value = "api", required = false) List<String> apis,
      @Parameter(description = "覆盖导出文档标题")
      @RequestParam(value = "title", required = false) String title,
      @Parameter(description = "覆盖导出文档版本号")
      @RequestParam(value = "version", required = false) String version,
      @Parameter(description = "为导出文档附加服务地址列表")
      @RequestParam(value = "serverUrl", required = false) List<String> serverUrls,
      @Parameter(description = "是否以格式化 JSON 输出文档")
      @RequestParam(value = "pretty", required = false) Boolean pretty
  ) throws JsonProcessingException {
    OpenAPIExportRequest request = OpenAPIExportRequest.builder()
        .scopes(parseEnumValues(scopes, WebAPIScope::fromValue))
        .handlerTypes(parseEnumValues(handlerTypes, HandlerType::fromValue))
        .tags(copyValues(tags))
        .pathPrefixes(copyValues(pathPrefixes))
        .apis(copyValues(apis))
        .title(title)
        .version(version)
        .serverUrls(copyValues(serverUrls))
        .pretty(Boolean.TRUE.equals(pretty))
        .build();

    ObjectWriter writer = Boolean.TRUE.equals(request.getPretty())
        ? objectMapper.writerWithDefaultPrettyPrinter()
        : objectMapper.writer();
    String json = writer.writeValueAsString(openAPIDocumentService.export(request));

    return ResponseEntity.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .body(json);
  }

  private <T> List<T> parseEnumValues(List<String> values, Function<String, T> parser) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    List<T> parsed = new ArrayList<>();
    for (String value : values) {
      if (StringUtils.isBlank(value)) {
        continue;
      }
      T parsedValue = parser.apply(value);
      if (parsedValue != null) {
        parsed.add(parsedValue);
      }
    }
    return parsed;
  }

  private List<String> copyValues(List<String> values) {
    if (values == null || values.isEmpty()) {
      return Collections.emptyList();
    }
    return values.stream()
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.toList());
  }
}
