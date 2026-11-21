package com.ouroboros.webapi.controller;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.pattern.PathPattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.AppContext;
import com.ouroboros.data.model.TypedDataModel;
import com.ouroboros.dynamicform.controller.DynamicFormDefineController;
import com.ouroboros.documentmanagement.service.DocumentLibraryContentFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryAdminFacade;
import com.ouroboros.documentmanagement.service.DocumentLibraryManagementFacade;
import com.ouroboros.documentmanagement.webapp.controller.DocumentFileOperationController;
import com.ouroboros.documentmanagement.webapp.controller.DocumentLibraryAdminOwnershipController;
import com.ouroboros.documentmanagement.webapp.controller.DocumentLibraryManagementController;
import com.ouroboros.documentmanagement.webapp.controller.DocumentLibraryNavigationController;
import com.ouroboros.dynamicform.controller.DynamicFormDataController;
import com.ouroboros.dynamicform.service.DynamicFormDataService;
import com.ouroboros.dynamicform.service.DynamicFormDefineService;
import com.ouroboros.fieldtype.FieldTypeCatalog;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.model.AppInfo;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.uischema.controller.CustomUISchemaController;
import com.ouroboros.uischema.model.CustomUISchema;
import com.ouroboros.uischema.service.CustomUISchemaService;
import com.ouroboros.webapi.WebAPIModel;
import com.ouroboros.webapi.WebAPIModelCatalog;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.config.WebAPIModelWebMvcConfiguration;
import com.ouroboros.webapi.extractor.RequestMappingOpenAPIExtractor;
import com.ouroboros.webapi.export.OpenAPIExportRequest;
import com.ouroboros.webapi.export.OpenAPIDocumentBuilder;
import com.ouroboros.webapi.export.OpenAPIDocumentService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@WebMvcTest(
    controllers = OpenAPIDocumentController.class,
    excludeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = WebAPIModelWebMvcConfiguration.class)
)
@Import({
    OpenAPIDocumentController.class,
    OpenAPIDocumentService.class,
    OpenAPIDocumentBuilder.class,
    DynamicFormDataController.class,
    DynamicFormDefineController.class,
    CustomUISchemaController.class,
    DocumentLibraryManagementController.class,
    DocumentLibraryNavigationController.class,
    DocumentFileOperationController.class,
    DocumentLibraryAdminOwnershipController.class,
    WebAPIDiscoverController.class,
    OpenAPIDocumentRuntimeInspectionTest.RuntimeInspectionConfiguration.class
})
@ContextConfiguration(classes = OpenAPIDocumentRuntimeInspectionTest.WebMvcSliceApplication.class)
@TestPropertySource(properties = "spring.main.allow-bean-definition-overriding=true")
@AutoConfigureMockMvc(addFilters = false)
class OpenAPIDocumentRuntimeInspectionTest {

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private OpenAPIDocumentService openAPIDocumentService;

  @MockitoBean
  private TypedDataModel<String, CustomUISchema> customUISchemaDataModel;

  @Test
  void shouldExportOpenApi31MetadataAndRuntimeControllerDescriptions() throws Exception {
    JsonNode document = exportDocument();

    assertEquals("3.1.0", document.path("openapi").asText());
    assertEquals("Runtime Inspection API", document.path("info").path("title").asText());
    assertEquals("dev", document.path("info").path("version").asText());

    JsonNode openApiJson = operation(document, "/api/sys/webapi/openapi.json", "get");
    assertEquals("导出 OpenAPI 文档 JSON", openApiJson.path("summary").asText());
    assertEquals("webapi-admin", openApiJson.path("tags").get(0).asText());
    assertEquals("按 WebAPI 作用域筛选，例如 internal、public", parameterByName(openApiJson, "scope").path("description").asText());
    assertEquals("按处理器类型筛选，例如 requestMapping", parameterByName(openApiJson, "handlerType").path("description").asText());
    assertEquals("按请求路径前缀筛选接口", parameterByName(openApiJson, "pathPrefix").path("description").asText());
    assertEquals("是否以格式化 JSON 输出文档", parameterByName(openApiJson, "pretty").path("description").asText());
    assertEquals("成功返回 OpenAPI JSON 文档", openApiJson.path("responses").path("200").path("description").asText());
    assertEquals("OpenAPI 文档序列化失败", openApiJson.path("responses").path("500").path("description").asText());

    JsonNode discover = operation(document, "/api/sys/webapi/discover", "get");
    assertEquals("发现已注册的 WebAPI 模型", discover.path("summary").asText());
    assertEquals("webapi-admin", discover.path("tags").get(0).asText());
    assertEquals("成功返回已注册的 WebAPI 模型列表", discover.path("responses").path("200").path("description").asText());

    JsonNode createSchema = requestSchema(document, "/api/document-libraries", "post");
    JsonNode resolvedCreateSchema = componentSchema(document, createSchema);
    assertTrue(requiredFields(resolvedCreateSchema).contains("name"), resolvedCreateSchema.toPrettyString());
    assertTrue(requiredFields(resolvedCreateSchema).contains("displayName"), resolvedCreateSchema.toPrettyString());
  }

  @Test
  void shouldPreserveParameterLocationsRequestBodyAndResponsesForBackfilledApis() throws Exception {
    JsonNode document = exportDocument();

    JsonNode createLibrary = operation(document, "/api/document-libraries", "post");
    assertEquals("创建档案库", createLibrary.path("summary").asText());
    assertEquals("document-library", createLibrary.path("tags").get(0).asText());
    assertEquals("档案库创建请求", createLibrary.path("requestBody").path("description").asText());
    assertTrue(createLibrary.path("requestBody").path("required").asBoolean());
    assertEquals("档案库创建成功", createLibrary.path("responses").path("200").path("description").asText());
    assertEquals("档案库参数不合法", createLibrary.path("responses").path("400").path("description").asText());

    JsonNode transferOwnership = operation(document, "/api/document-management/admin/libraries/{libraryId}/transfer-ownership", "post");
    assertEquals("后台转移档案库所有权", transferOwnership.path("summary").asText());
    assertEquals("document-library", transferOwnership.path("tags").get(0).asText());
    assertEquals("档案库 ID", parameterByName(transferOwnership, "libraryId").path("description").asText());
    assertEquals("所有权转移请求", transferOwnership.path("requestBody").path("description").asText());
    assertTrue(transferOwnership.path("requestBody").path("required").asBoolean());
    assertEquals("所有权转移成功", transferOwnership.path("responses").path("200").path("description").asText());
    assertEquals("转移请求参数不合法", transferOwnership.path("responses").path("400").path("description").asText());
    assertEquals("档案库或新所有者不存在", transferOwnership.path("responses").path("404").path("description").asText());

    JsonNode browseDirectory = operation(
        document,
        "/api/document-libraries/{libraryId}/directories/{directoryId}/browse",
        "get"
    );
    assertEquals("浏览目录内容", browseDirectory.path("summary").asText());
    assertEquals("document-library", browseDirectory.path("tags").get(0).asText());
    JsonNode libraryId = parameterByName(browseDirectory, "libraryId");
    assertEquals("path", libraryId.path("in").asText());
    assertTrue(libraryId.path("required").asBoolean());
    assertEquals("档案库 ID", libraryId.path("description").asText());

    JsonNode includeDir = parameterByName(browseDirectory, "includeDir");
    assertEquals("query", includeDir.path("in").asText());
    assertFalse(includeDir.path("required").asBoolean());
    assertEquals("是否包含子目录", includeDir.path("description").asText());
    assertEquals("成功返回目录浏览结果", browseDirectory.path("responses").path("200").path("description").asText());
    assertEquals("目录不存在或当前用户无权访问", browseDirectory.path("responses").path("404").path("description").asText());

    JsonNode dynamicFormAdd = operation(document, "/api/dynamic-form/{name}/data", "post");
    assertEquals("新增动态表单数据", dynamicFormAdd.path("summary").asText());
    assertEquals("dynamic-form", dynamicFormAdd.path("tags").get(0).asText());
    JsonNode dynamicFormName = parameterByName(dynamicFormAdd, "name");
    assertEquals("path", dynamicFormName.path("in").asText());
    assertEquals("动态表单名称", dynamicFormName.path("description").asText());
    assertEquals("动态表单数据内容", dynamicFormAdd.path("requestBody").path("description").asText());
    assertTrue(dynamicFormAdd.path("requestBody").path("required").asBoolean());
    assertEquals("动态表单数据新增成功", dynamicFormAdd.path("responses").path("200").path("description").asText());
    assertEquals("动态表单数据参数不合法或当前表单状态不支持新增", dynamicFormAdd.path("responses").path("400").path("description").asText());
    assertEquals("动态表单不存在", dynamicFormAdd.path("responses").path("404").path("description").asText());

    JsonNode dynamicFormSearch = operation(document, "/api/dynamic-form/{name}/data", "get");
    assertEquals("分页查询动态表单数据", dynamicFormSearch.path("summary").asText());
    assertEquals("dynamic-form", dynamicFormSearch.path("tags").get(0).asText());
    assertEquals("动态表单名称", parameterByName(dynamicFormSearch, "name").path("description").asText());
    assertEquals("分页、排序与筛选参数", parameterByName(dynamicFormSearch, "params").path("description").asText());
    assertEquals("成功返回动态表单数据分页结果", dynamicFormSearch.path("responses").path("200").path("description").asText());
    assertEquals("动态表单当前状态不支持查询", dynamicFormSearch.path("responses").path("400").path("description").asText());
    assertEquals("动态表单不存在", dynamicFormSearch.path("responses").path("404").path("description").asText());

    JsonNode dynamicFormCreate = operation(document, "/api/dynamic-form", "post");
    assertEquals("创建动态表单定义", dynamicFormCreate.path("summary").asText());
    assertEquals("dynamic-form", dynamicFormCreate.path("tags").get(0).asText());
    assertEquals("动态表单定义", dynamicFormCreate.path("requestBody").path("description").asText());
    assertTrue(dynamicFormCreate.path("requestBody").path("required").asBoolean());
    assertEquals("动态表单定义创建成功", dynamicFormCreate.path("responses").path("200").path("description").asText());
    assertEquals("动态表单定义参数不合法", dynamicFormCreate.path("responses").path("400").path("description").asText());

    JsonNode validateFieldName = operation(document, "/api/dynamic-form/validate-field-name", "post");
    assertEquals("验证字段名", validateFieldName.path("summary").asText());
    assertEquals("dynamic-form", validateFieldName.path("tags").get(0).asText());
    assertEquals("字段名校验请求", validateFieldName.path("requestBody").path("description").asText());
    assertTrue(validateFieldName.path("requestBody").path("required").asBoolean());
    assertEquals("字段名校验通过", validateFieldName.path("responses").path("200").path("description").asText());
    assertEquals("字段名校验失败", validateFieldName.path("responses").path("422").path("description").asText());

    JsonNode fieldParamsSchema = operation(document, "/api/dynamic-form/field-types/{fieldName}/params-schema", "get");
    assertEquals("获取字段参数 Schema", fieldParamsSchema.path("summary").asText());
    assertEquals("字段类型名称", parameterByName(fieldParamsSchema, "fieldName").path("description").asText());
    assertEquals("成功返回字段参数 Schema", fieldParamsSchema.path("responses").path("200").path("description").asText());
    assertEquals("字段类型不存在", fieldParamsSchema.path("responses").path("404").path("description").asText());

    JsonNode customUiSchemaCreate = operation(document, "/api/sys/custom-ui-schema", "post");
    assertEquals("创建自定义界面 Schema", customUiSchemaCreate.path("summary").asText());
    assertEquals("ui-schema", customUiSchemaCreate.path("tags").get(0).asText());
    assertEquals("自定义界面 Schema 定义", customUiSchemaCreate.path("requestBody").path("description").asText());
    assertEquals("自定义界面 Schema 创建成功", customUiSchemaCreate.path("responses").path("201").path("description").asText());
    assertEquals("界面 Schema 参数不合法", customUiSchemaCreate.path("responses").path("400").path("description").asText());

    JsonNode customUiSchemaSearch = operation(document, "/api/sys/custom-ui-schema", "get");
    assertEquals("分页查询自定义界面 Schema", customUiSchemaSearch.path("summary").asText());
    assertEquals("ui-schema", customUiSchemaSearch.path("tags").get(0).asText());
    assertEquals("分页、排序与筛选参数", parameterByName(customUiSchemaSearch, "params").path("description").asText());
    assertEquals("成功返回自定义界面 Schema 分页结果", customUiSchemaSearch.path("responses").path("200").path("description").asText());

    JsonNode uploadDocument = operation(
        document,
        "/api/document-libraries/{libraryId}/directories/{directoryId}/upload",
        "post"
    );
    assertTrue(uploadDocument.path("parameters").isArray());
    assertEquals(2, uploadDocument.path("parameters").size(), uploadDocument.toPrettyString());
    assertEquals("档案库 ID", parameterByName(uploadDocument, "libraryId").path("description").asText());
    assertEquals("目标目录 ID", parameterByName(uploadDocument, "directoryId").path("description").asText());
    assertEquals("multipart/form-data", firstFieldName(uploadDocument.path("requestBody").path("content")));

    JsonNode uploadRequestSchema = uploadDocument.path("requestBody")
        .path("content")
        .path("multipart/form-data")
        .path("schema");
    JsonNode resolvedUploadRequestSchema = componentSchema(document, uploadRequestSchema);
    assertEquals("object", resolvedUploadRequestSchema.path("type").asText(), resolvedUploadRequestSchema.toPrettyString());
    assertTrue(resolvedUploadRequestSchema.path("properties").has("file"), resolvedUploadRequestSchema.toPrettyString());
    assertTrue(resolvedUploadRequestSchema.path("properties").has("file[]"), resolvedUploadRequestSchema.toPrettyString());
    assertFalse(hasParameterNamed(uploadDocument, "file"), uploadDocument.toPrettyString());
    assertFalse(hasParameterNamed(uploadDocument, "file[]"), uploadDocument.toPrettyString());
  }

  private JsonNode exportDocument() throws Exception {
    return objectMapper.valueToTree(openAPIDocumentService.export(
        OpenAPIExportRequest.builder()
            .pretty(true)
            .build()
    ));
  }

  private JsonNode operation(JsonNode document, String path, String method) {
    JsonNode operation = document.path("paths").path(path).path(method);
    assertFalse(operation.isMissingNode(), "missing operation " + method + " " + path
        + ", available paths: " + document.path("paths").fieldNames().toString());
    return operation;
  }

  private JsonNode parameterByName(JsonNode operation, String name) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (name.equals(parameter.path("name").asText())) {
        return parameter;
      }
    }
    fail("missing parameter " + name);
    return objectMapper.nullNode();
  }

  private boolean hasParameterNamed(JsonNode operation, String name) {
    for (JsonNode parameter : operation.path("parameters")) {
      if (name.equals(parameter.path("name").asText())) {
        return true;
      }
    }
    return false;
  }

  private String firstFieldName(JsonNode node) {
    Iterator<String> fieldNames = node.fieldNames();
    assertTrue(fieldNames.hasNext(), "missing content types");
    return fieldNames.next();
  }

  private JsonNode requestSchema(JsonNode document, String path, String method) {
    JsonNode schema = operation(document, path, method)
        .path("requestBody")
        .path("content")
        .path("application/json")
        .path("schema");
    assertFalse(schema.isMissingNode(), "missing request schema for " + method + " " + path);
    return schema;
  }

  private JsonNode componentSchema(JsonNode document, JsonNode schemaNode) {
    String ref = schemaNode.path("$ref").asText();
    if (!ref.isEmpty()) {
      assertTrue(ref.startsWith("#/components/schemas/"), "schema is not component ref: " + ref);
      String componentName = ref.substring("#/components/schemas/".length());
      JsonNode component = document.path("components").path("schemas").path(componentName);
      assertFalse(component.isMissingNode(), "missing component schema " + componentName);
      return component;
    }
    return schemaNode;
  }

  private List<String> requiredFields(JsonNode schema) {
    List<String> required = new ArrayList<>();
    Iterator<JsonNode> iterator = schema.path("required").elements();
    while (iterator.hasNext()) {
      required.add(iterator.next().asText());
    }
    return required;
  }

  @TestConfiguration
  static class RuntimeInspectionConfiguration {

    @Bean
    AppContext appContext() {
      return new AppContext() {
        @Override
        public String getHomePath() {
          return ".";
        }

        @Override
        public Optional<AppInfo> getAppInfo() {
          return Optional.of(new AppInfo()
              .setTitle("Runtime Inspection API")
              .setName("runtime-inspection"));
        }
      };
    }

    @Bean(name = "requestMappingHandlerMapping")
    RequestMappingHandlerMapping requestMappingHandlerMapping() {
      return new RequestMappingHandlerMapping();
    }

    @Bean
    WebAPIModelCatalog webAPIModelCatalog(RequestMappingHandlerMapping requestMappingHandlerMapping) {
      RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();
      return new WebAPIModelCatalog(() -> requestMappingHandlerMapping.getHandlerMethods().entrySet().stream()
          .map(entry -> buildWebApiModel(extractor, entry.getKey(), entry.getValue()))
          .collect(Collectors.toList()));
    }

    private WebAPIModel buildWebApiModel(
        RequestMappingOpenAPIExtractor extractor,
        RequestMappingInfo requestMappingInfo,
        HandlerMethod handlerMethod) {
      String path = firstPath(requestMappingInfo);
      String method = requestMappingInfo.getMethodsCondition().getMethods().stream()
          .findFirst()
          .map(Enum::name)
          .orElse("GET");

      return extractor.extract(
              SimpleWebAPIModel.builder()
                  .api(method, path)
                  .scope(WebAPIScope.INTERNAL)
                  .handlerType(HandlerType.REQUEST_MAPPING),
              requestMappingInfo,
              handlerMethod.getBeanType(),
              handlerMethod.getMethod())
          .build();
    }

    private String firstPath(RequestMappingInfo requestMappingInfo) {
      if (requestMappingInfo.getPathPatternsCondition() != null) {
        return requestMappingInfo.getPathPatternsCondition().getPatterns().stream()
            .map(PathPattern::getPatternString)
            .findFirst()
            .orElse("/");
      }
      if (requestMappingInfo.getPatternsCondition() != null) {
        return requestMappingInfo.getPatternsCondition().getPatterns().stream()
            .findFirst()
            .orElse("/");
      }
      return "/";
    }

    @Bean
    DynamicFormDataService dynamicFormDataService() {
      return mock(DynamicFormDataService.class);
    }

    @Bean
    DynamicFormDefineService dynamicFormDefineService() {
      return mock(DynamicFormDefineService.class);
    }

    @Bean
    FieldTypeCatalog fieldTypeCatalog() {
      return mock(FieldTypeCatalog.class);
    }

    @Bean
    CustomUISchemaService customUISchemaService() {
      return mock(CustomUISchemaService.class);
    }

    @Bean
    DocumentLibraryManagementFacade documentLibraryManagementFacade() {
      return mock(DocumentLibraryManagementFacade.class);
    }

    @Bean
    DocumentLibraryContentFacade documentLibraryContentFacade() {
      return mock(DocumentLibraryContentFacade.class);
    }

    @Bean
    DocumentLibraryAdminFacade documentLibraryAdminFacade() {
      return mock(DocumentLibraryAdminFacade.class);
    }
  }

  @SpringBootConfiguration
  static class WebMvcSliceApplication {
  }
}
