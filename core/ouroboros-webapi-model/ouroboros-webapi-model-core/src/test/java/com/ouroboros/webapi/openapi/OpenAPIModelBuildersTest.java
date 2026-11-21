package com.ouroboros.webapi.openapi;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OpenAPIModelBuildersTest {

  @Test
  void simpleOpenApiModelsStoreTheirFields() {
    ExampleModel example = ExampleModel.builder()
        .summary("Example summary")
        .description("Example description")
        .value(Collections.singletonMap("demo", Boolean.TRUE))
        .externalValue("https://example.com/example.json")
        .build();

    ServerVariableModel serverVariable = ServerVariableModel.builder()
        .enumValues(Arrays.asList("dev", "prod"))
        .defaultValue("dev")
        .description("Target environment")
        .build();

    ServerModel server = ServerModel.builder()
        .url("https://api.example.com")
        .description("Default server")
        .variables(Collections.singletonMap("environment", serverVariable))
        .build();

    InfoModel info = InfoModel.builder()
        .title("Demo API")
        .version("1.0.0")
        .description("Demo description")
        .build();

    ExternalDocsModel externalDocs = ExternalDocsModel.builder()
        .description("Reference docs")
        .url("https://docs.example.com")
        .build();

    assertEquals("Example summary", example.getSummary());
    assertEquals("Example description", example.getDescription());
    assertEquals(Collections.singletonMap("demo", Boolean.TRUE), example.getValue());
    assertEquals("https://example.com/example.json", example.getExternalValue());

    assertEquals(Arrays.asList("dev", "prod"), serverVariable.getEnumValues());
    assertEquals("dev", serverVariable.getDefaultValue());
    assertEquals("Target environment", serverVariable.getDescription());

    assertEquals("https://api.example.com", server.getUrl());
    assertEquals("Default server", server.getDescription());
    assertEquals(serverVariable, server.getVariables().get("environment"));

    assertEquals("Demo API", info.getTitle());
    assertEquals("1.0.0", info.getVersion());
    assertEquals("Demo description", info.getDescription());

    assertEquals("Reference docs", externalDocs.getDescription());
    assertEquals("https://docs.example.com", externalDocs.getUrl());
  }

  @Test
  void parameterHeaderEncodingMediaTypeRequestBodyResponseAndLinkBuildersStoreNestedFields() {
    SchemaModel stringSchema = SchemaModel.builder().stringType().build();
    SchemaModel objectSchema = SchemaModel.builder().objectType().build();

    ExampleModel example = ExampleModel.builder()
        .summary("Example")
        .description("Example description")
        .value("demo")
        .externalValue("https://example.com/external")
        .build();

    HeaderModel header = HeaderModel.builder()
        .description("Header description")
        .required(Boolean.TRUE)
        .deprecated(Boolean.FALSE)
        .schema(stringSchema)
        .example("header-value")
        .examples(Collections.singletonMap("sample", example))
        .build();

    EncodingModel encoding = EncodingModel.builder()
        .contentType("application/json")
        .headers(Collections.singletonMap("X-Debug", header))
        .style("form")
        .explode(Boolean.TRUE)
        .allowReserved(Boolean.FALSE)
        .build();

    MediaTypeModel mediaType = MediaTypeModel.builder()
        .schema(objectSchema)
        .example(Collections.singletonMap("id", 1))
        .examples(Collections.singletonMap("sample", example))
        .encoding(Collections.singletonMap("payload", encoding))
        .build();

    ParameterModel parameter = ParameterModel.builder()
        .name("verbose")
        .in(ParameterLocation.QUERY)
        .description("Verbose output")
        .required(Boolean.TRUE)
        .deprecated(Boolean.FALSE)
        .allowEmptyValue(Boolean.TRUE)
        .schema(stringSchema)
        .example("true")
        .examples(Collections.singletonMap("default", example))
        .build();

    RequestBodyModel requestBody = RequestBodyModel.builder()
        .description("Request payload")
        .content(Collections.singletonMap("application/json", mediaType))
        .required(Boolean.TRUE)
        .build();

    ResponseModel response = ResponseModel.builder()
        .description("OK")
        .headers(Collections.singletonMap("X-Debug", header))
        .content(Collections.singletonMap("application/json", mediaType))
        .links(Collections.singletonMap("self", LinkModel.builder()
            .operationId("getUser")
            .description("Follow-up link")
            .build()))
        .build();

    LinkModel link = LinkModel.builder()
        .operationRef("#/paths/~1users/get")
        .operationId("getUser")
        .parameters(Collections.singletonMap("id", 1))
        .requestBody(Collections.singletonMap("enabled", Boolean.TRUE))
        .description("Link description")
        .server(ServerModel.builder().url("https://api.example.com").build())
        .build();

    assertEquals("Header description", header.getDescription());
    assertEquals(Boolean.TRUE, header.getRequired());
    assertEquals(Boolean.FALSE, header.getDeprecated());
    assertEquals(stringSchema, header.getSchema());
    assertEquals("header-value", header.getExample());
    assertEquals(example, header.getExamples().get("sample"));

    assertEquals("application/json", encoding.getContentType());
    assertEquals(header, encoding.getHeaders().get("X-Debug"));
    assertEquals("form", encoding.getStyle());
    assertEquals(Boolean.TRUE, encoding.getExplode());
    assertEquals(Boolean.FALSE, encoding.getAllowReserved());

    assertEquals(objectSchema, mediaType.getSchema());
    assertEquals(Collections.singletonMap("id", 1), mediaType.getExample());
    assertEquals(example, mediaType.getExamples().get("sample"));
    assertEquals(encoding, mediaType.getEncoding().get("payload"));

    assertEquals("verbose", parameter.getName());
    assertEquals(ParameterLocation.QUERY, parameter.getIn());
    assertEquals("Verbose output", parameter.getDescription());
    assertEquals(Boolean.TRUE, parameter.getRequired());
    assertEquals(Boolean.FALSE, parameter.getDeprecated());
    assertEquals(Boolean.TRUE, parameter.getAllowEmptyValue());
    assertEquals(stringSchema, parameter.getSchema());
    assertEquals("true", parameter.getExample());
    assertEquals(example, parameter.getExamples().get("default"));

    assertEquals("Request payload", requestBody.getDescription());
    assertEquals(mediaType, requestBody.getContent().get("application/json"));
    assertEquals(Boolean.TRUE, requestBody.getRequired());

    assertEquals("OK", response.getDescription());
    assertEquals(header, response.getHeaders().get("X-Debug"));
    assertEquals(mediaType, response.getContent().get("application/json"));
    assertEquals("getUser", response.getLinks().get("self").getOperationId());

    assertEquals("#/paths/~1users/get", link.getOperationRef());
    assertEquals("getUser", link.getOperationId());
    assertEquals(Collections.singletonMap("id", 1), link.getParameters());
    assertEquals(Collections.singletonMap("enabled", Boolean.TRUE), link.getRequestBody());
    assertEquals("Link description", link.getDescription());
    assertEquals("https://api.example.com", link.getServer().getUrl());
  }

  @Test
  void securityRequirementModelSupportsFactoryAndMutationMethods() {
    SecurityRequirementModel model = SecurityRequirementModel.of("bearerAuth", Arrays.asList("users:read", "users:write"));
    Map<String, List<String>> replacement = new LinkedHashMap<>();

    assertEquals(Arrays.asList("users:read", "users:write"), model.getRequirements().get("bearerAuth"));

    model.addRequirement("apiKey", Collections.singletonList("internal"));
    assertEquals(Collections.singletonList("internal"), model.getRequirements().get("apiKey"));

    replacement.put("oauth2", Collections.singletonList("admin"));
    model.setRequirements(replacement);

    assertSame(replacement, model.getRequirements());
    assertEquals(Collections.singletonList("admin"), model.getRequirements().get("oauth2"));
  }

  @Test
  void operationAndPathItemBuildersStoreEveryHttpMethodAndCollectionVariant() {
    ParameterModel parameter = ParameterModel.builder()
        .name("id")
        .in(ParameterLocation.PATH)
        .schema(SchemaModel.builder().stringType().build())
        .build();

    ResponseModel response = ResponseModel.builder().description("ok").build();
    SecurityRequirementModel securityRequirement = SecurityRequirementModel.of("bearerAuth", Collections.singletonList("users:read"));
    ServerModel server = ServerModel.builder().url("https://api.example.com").build();
    ExternalDocsModel externalDocs = ExternalDocsModel.builder().description("Reference").url("https://docs.example.com").build();

    OperationModel get = OperationModel.builder()
        .summary("Get user")
        .description("Loads user")
        .operationId("getUser")
        .tag("users")
        .tag("admin")
        .parameter(parameter)
        .requestBody(RequestBodyModel.builder().description("payload").build())
        .response("200", response)
        .deprecated(Boolean.TRUE)
        .security(securityRequirement)
        .server(server)
        .externalDocs(externalDocs)
        .build();

    OperationModel put = OperationModel.builder()
        .summary("Replace user")
        .build();
    OperationModel post = OperationModel.builder().summary("Create user").build();
    OperationModel delete = OperationModel.builder().summary("Delete user").build();
    OperationModel options = OperationModel.builder().summary("Options").build();
    OperationModel head = OperationModel.builder().summary("Head").build();
    OperationModel patch = OperationModel.builder().summary("Patch").build();
    OperationModel trace = OperationModel.builder().summary("Trace").build();

    PathItemModel pathItem = PathItemModel.builder()
        .get(get)
        .put(put)
        .post(post)
        .delete(delete)
        .options(options)
        .head(head)
        .patch(patch)
        .trace(trace)
        .build();

    assertEquals("Get user", get.getSummary());
    assertEquals("Loads user", get.getDescription());
    assertEquals("getUser", get.getOperationId());
    assertEquals(Arrays.asList("users", "admin"), get.getTags());
    assertEquals(Collections.singletonList(parameter), get.getParameters());
    assertEquals(response, get.getResponses().get("200"));
    assertEquals(Boolean.TRUE, get.getDeprecated());
    assertEquals(Collections.singletonList(securityRequirement), get.getSecurity());
    assertEquals(Collections.singletonList(server), get.getServers());
    assertEquals(externalDocs, get.getExternalDocs());

    assertEquals(put, pathItem.getPut());
    assertEquals(post, pathItem.getPost());
    assertEquals(delete, pathItem.getDelete());
    assertEquals(options, pathItem.getOptions());
    assertEquals(head, pathItem.getHead());
    assertEquals(patch, pathItem.getPatch());
    assertEquals(trace, pathItem.getTrace());
    assertEquals(get, pathItem.getGet());
  }

  @Test
  void openApiDocumentAndComponentsBuildersSupportAdditiveAndReplacementCollectionMethods() {
    SchemaModel userSchema = SchemaModel.builder().objectType().title("User").build();
    SchemaModel orderSchema = SchemaModel.builder().objectType().title("Order").build();

    ComponentsModel components = ComponentsModel.builder()
        .schema("User", userSchema)
        .schemas(Collections.singletonMap("Order", orderSchema))
        .build();

    InfoModel info = InfoModel.builder().title("Demo API").version("1.0.0").build();
    PathItemModel users = PathItemModel.builder()
        .get(OperationModel.builder().summary("List users").build())
        .build();
    PathItemModel orders = PathItemModel.builder()
        .get(OperationModel.builder().summary("List orders").build())
        .build();

    OpenAPIDocumentModel additiveDocument = OpenAPIDocumentModel.builder()
        .openapi("3.1.0")
        .jsonSchemaDialect("https://json-schema.org/draft/2020-12/schema")
        .info(info)
        .server(ServerModel.builder().url("https://api.example.com").build())
        .server(ServerModel.builder().url("https://api.example.com/internal").build())
        .path("/users", users)
        .path("/orders", orders)
        .components(components)
        .build();

    List<ServerModel> explicitServers = Arrays.asList(
        ServerModel.builder().url("https://one.example.com").build(),
        ServerModel.builder().url("https://two.example.com").build());
    Map<String, PathItemModel> explicitPaths = new LinkedHashMap<>();
    explicitPaths.put("/explicit", users);

    OpenAPIDocumentModel replacementDocument = OpenAPIDocumentModel.builder()
        .servers(explicitServers)
        .paths(explicitPaths)
        .build();

    assertEquals("3.1.0", additiveDocument.getOpenapi());
    assertEquals("https://json-schema.org/draft/2020-12/schema", additiveDocument.getJsonSchemaDialect());
    assertEquals(info, additiveDocument.getInfo());
    assertEquals(2, additiveDocument.getServers().size());
    assertEquals("https://api.example.com", additiveDocument.getServers().get(0).getUrl());
    assertEquals("https://api.example.com/internal", additiveDocument.getServers().get(1).getUrl());
    assertEquals(2, additiveDocument.getPaths().size());
    assertEquals(users, additiveDocument.getPaths().get("/users"));
    assertEquals(orders, additiveDocument.getPaths().get("/orders"));
    assertEquals(components, additiveDocument.getComponents());
    assertEquals(1, additiveDocument.getComponents().getSchemas().size());
    assertEquals(orderSchema, additiveDocument.getComponents().getSchemas().get("Order"));

    assertEquals(explicitServers, replacementDocument.getServers());
    assertEquals(explicitPaths, replacementDocument.getPaths());
    assertNull(replacementDocument.getInfo());
    assertNull(replacementDocument.getComponents());

    OpenAPIDocumentModel emptyDocument = OpenAPIDocumentModel.builder().build();
    assertNotNull(emptyDocument.getServers());
    assertTrue(emptyDocument.getServers().isEmpty());
    assertNotNull(emptyDocument.getPaths());
    assertTrue(emptyDocument.getPaths().isEmpty());
    assertNull(emptyDocument.getComponents());
  }

  @Test
  void parameterLocationEnumSupportsKnownValuesAndRejectsUnknownOnes() {
    assertEquals(ParameterLocation.PATH, ParameterLocation.fromValue("path"));
    assertEquals(ParameterLocation.QUERY, ParameterLocation.fromValue("query"));
    assertEquals(ParameterLocation.HEADER, ParameterLocation.fromValue("header"));
    assertEquals(ParameterLocation.COOKIE, ParameterLocation.fromValue("cookie"));
    assertEquals("path", ParameterLocation.PATH.getValue());
    assertEquals("query", ParameterLocation.QUERY.getValue());
    assertEquals("header", ParameterLocation.HEADER.getValue());
    assertEquals("cookie", ParameterLocation.COOKIE.getValue());
    assertNull(ParameterLocation.fromValue("unknown"));
  }
}
