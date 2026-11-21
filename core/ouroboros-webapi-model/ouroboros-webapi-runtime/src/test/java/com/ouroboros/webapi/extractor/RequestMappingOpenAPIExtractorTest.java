package com.ouroboros.webapi.extractor;

import java.lang.reflect.Method;
import java.util.Collections;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import com.ouroboros.web.WebApiResult;
import com.ouroboros.webapi.HandlerType;
import com.ouroboros.webapi.SimpleWebAPIModel;
import com.ouroboros.webapi.WebAPIScope;
import com.ouroboros.webapi.openapi.MediaTypeModel;
import com.ouroboros.webapi.openapi.ParameterLocation;
import com.ouroboros.webapi.openapi.ParameterModel;
import com.ouroboros.webapi.openapi.ResponseModel;
import com.ouroboros.webapi.openapi.SchemaModel;

import static org.junit.jupiter.api.Assertions.*;

class RequestMappingOpenAPIExtractorTest {

  private final RequestMappingOpenAPIExtractor extractor = new RequestMappingOpenAPIExtractor();

  @Test
  void shouldExtractSpringMvcParametersAndOperationMetadata() throws Exception {
    Method method = TestController.class.getMethod("getUser", String.class, boolean.class, String.class);
    SimpleWebAPIModel model = extract("GET", "/users/{id}", mapping("/users/{id}", RequestMethod.GET), method);

    assertEquals("Get user", model.getSummary());
    assertEquals(Collections.singletonList("users"), model.getTags());
    assertEquals(3, model.getParameters().size());

    ParameterModel id = parameter(model, "id");
    assertEquals(ParameterLocation.PATH, id.getIn());
    assertEquals("User id", id.getDescription());
    assertEquals(Boolean.TRUE, id.getRequired());

    ParameterModel verbose = parameter(model, "verbose");
    assertEquals(ParameterLocation.QUERY, verbose.getIn());
    assertEquals("boolean", verbose.getSchema().getType());

    ParameterModel traceId = parameter(model, "X-Trace-Id");
    assertEquals(ParameterLocation.HEADER, traceId.getIn());

    assertTrue(model.getResponses().containsKey("200"));
  }

  @Test
  void shouldExtractRequestBodySchemaAndValidationConstraints() throws Exception {
    Method method = TestController.class.getMethod("createUser", CreateUserRequest.class);
    SimpleWebAPIModel model = extract("POST", "/users", mapping("/users", RequestMethod.POST), method);

    assertNotNull(model.getRequestBody());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertNotNull(model.getRequestBody().getContent());
    assertTrue(model.getRequestBody().getContent().containsKey("application/json"));

    SchemaModel bodySchema = model.getRequestBody().getContent().get("application/json").getSchema();
    assertEquals("object", bodySchema.getType());
    assertTrue(bodySchema.getRequired().contains("name"));
    assertTrue(bodySchema.getRequired().contains("email"));

    SchemaModel nameSchema = bodySchema.getProperties().get("name");
    assertEquals(Integer.valueOf(3), nameSchema.getMinLength());
    assertEquals(Integer.valueOf(50), nameSchema.getMaxLength());
    assertEquals("Display name", nameSchema.getDescription());
    assertNull(nameSchema.getDefaultValue());

    SchemaModel emailSchema = bodySchema.getProperties().get("email");
    assertEquals("email", emailSchema.getFormat());

    SchemaModel statusSchema = bodySchema.getProperties().get("status");
    assertEquals("active", statusSchema.getDefaultValue());
  }

  @Test
  void shouldPreferExplicitSwaggerResponses() throws Exception {
    Method method = TestController.class.getMethod("deleteUser", String.class);
    SimpleWebAPIModel model = extract("POST", "/users/{id}/delete", mapping("/users/{id}/delete", RequestMethod.POST), method);

    assertEquals("Deleted", model.getResponses().get("204").getDescription());
    assertEquals("Missing user", model.getResponses().get("404").getDescription());
  }

  @Test
  void shouldExtractAuthStyleSwaggerMetadata() throws Exception {
    Method method = TestController.class.getMethod("bindExternalIdentity", String.class, CreateUserRequest.class);
    SimpleWebAPIModel model = extract("POST", "/auth/bind/{provider}", mapping("/auth/bind/{provider}", RequestMethod.POST), method);

    assertEquals("Bind external identity", model.getSummary());
    assertEquals("Bind an external account to the current platform user.", model.getDescription());
    assertEquals(Collections.singletonList("authentication"), model.getTags());

    ParameterModel provider = parameter(model, "provider");
    assertEquals("Authentication provider key", provider.getDescription());

    assertNotNull(model.getRequestBody());
    assertEquals("Binding request payload", model.getRequestBody().getDescription());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());

    assertEquals("Binding completed", model.getResponses().get("200").getDescription());
    assertEquals("External identity already bound", model.getResponses().get("409").getDescription());
  }

  @Test
  void shouldExtractSwaggerImplementationSchemasAndDefaultJsonContentType() throws Exception {
    Method method = TestController.class.getMethod("getDocumentedUser");
    SimpleWebAPIModel model = extract("GET", "/users/documented", mapping("/users/documented", RequestMethod.GET), method);

    ResponseModel response = model.getResponses().get("200");
    assertNotNull(response);
    assertEquals("Documented user response", response.getDescription());
    assertNotNull(response.getContent());
    assertTrue(response.getContent().containsKey("application/json"));

    SchemaModel schema = response.getContent().get("application/json").getSchema();
    assertNotNull(schema);
    assertEquals("object", schema.getType());
    assertNotNull(schema.getProperties());
    assertEquals("string", schema.getProperties().get("id").getType());
    assertEquals("User identifier", schema.getProperties().get("id").getDescription());
  }

  @Test
  void shouldExtractRequestPartAsMultipartRequestBody() throws Exception {
    Method method = TestController.class.getMethod("uploadAvatar", MultipartFile.class);
    SimpleWebAPIModel model = extract("POST", "/avatars", mapping("/avatars", RequestMethod.POST, "multipart/form-data"), method);

    assertTrue(model.getParameters().isEmpty());
    assertNotNull(model.getRequestBody());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("Avatar upload payload", model.getRequestBody().getDescription());

    MediaTypeModel multipart = model.getRequestBody().getContent().get("multipart/form-data");
    assertNotNull(multipart);
    assertEquals("object", multipart.getSchema().getType());
    assertTrue(multipart.getSchema().getRequired().contains("file"));

    SchemaModel fileSchema = multipart.getSchema().getProperties().get("file");
    assertEquals("string", fileSchema.getType());
    assertEquals("binary", fileSchema.getFormat());
    assertEquals("Avatar file", fileSchema.getDescription());
  }

  @Test
  void shouldExtractMultipartRequestParamFileAsRequestBody() throws Exception {
    Method method = TestController.class.getMethod("uploadAttachments", MultipartFile[].class);
    SimpleWebAPIModel model = extract("POST", "/attachments", mapping("/attachments", RequestMethod.POST, "multipart/form-data"), method);

    assertTrue(model.getParameters().isEmpty());
    assertNotNull(model.getRequestBody());

    MediaTypeModel multipart = model.getRequestBody().getContent().get("multipart/form-data");
    assertNotNull(multipart);
    SchemaModel filesSchema = multipart.getSchema().getProperties().get("files");
    assertEquals("array", filesSchema.getType());
    assertNotNull(filesSchema.getItems());
    assertEquals("string", filesSchema.getItems().getType());
    assertEquals("binary", filesSchema.getItems().getFormat());
    assertEquals("Attachment files", filesSchema.getDescription());
  }

  @Test
  void shouldExtractSwaggerRequestBodyForMultipartFormObject() throws Exception {
    Method method = TestController.class.getMethod("uploadChunkForm", UploadChunkForm.class);
    SimpleWebAPIModel model = extract("POST", "/chunks", mapping("/chunks", RequestMethod.POST, "multipart/form-data"), method);

    assertTrue(model.getParameters().isEmpty());
    assertNotNull(model.getRequestBody());
    assertEquals(Boolean.TRUE, model.getRequestBody().getRequired());
    assertEquals("Chunk upload form", model.getRequestBody().getDescription());

    MediaTypeModel multipart = model.getRequestBody().getContent().get("multipart/form-data");
    assertNotNull(multipart);
    SchemaModel schema = multipart.getSchema();
    assertEquals("object", schema.getType());
    assertTrue(schema.getRequired().contains("uploadId"));
    assertTrue(schema.getRequired().contains("file"));

    SchemaModel fileSchema = schema.getProperties().get("file");
    assertEquals("string", fileSchema.getType());
    assertEquals("binary", fileSchema.getFormat());
    assertEquals("Chunk file content", fileSchema.getDescription());
  }

  @Test
  void controllerContractMetadataKeepsInferredSuccessAndAllowsMethodErrorOverrides() throws Exception {
    var shared = extractor.extract(SimpleWebAPIModel.builder().get("/debug"), mapping("/debug", RequestMethod.GET),
        SharedContractController.class, SharedContractController.class.getMethod("read")).build();
    assertEquals(Collections.singletonList("debug"), shared.getTags());
    assertTrue(shared.getSecurity().getFirst().getRequirements().containsKey("bearerAuth"));
    assertNotNull(shared.getResponses().get("200").getContent().get("application/json").getSchema());
    assertEquals("Denied", shared.getResponses().get("403").getDescription());
    var overridden = extractor.extract(SimpleWebAPIModel.builder().post("/debug"), mapping("/debug", RequestMethod.POST),
        SharedContractController.class, SharedContractController.class.getMethod("stop")).build();
    assertEquals("Owner required", overridden.getResponses().get("403").getDescription());
    assertTrue(overridden.getResponses().containsKey("204"));
    assertFalse(overridden.getResponses().containsKey("200"));
    assertTrue(overridden.getSecurity().getFirst().getRequirements().containsKey("session"));
  }

  @io.swagger.v3.oas.annotations.tags.Tag(name = "debug")
  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "bearerAuth")
  @ApiResponses(@ApiResponse(responseCode = "403", description = "Denied"))
  static class SharedContractController {
    @Operation(summary = "Read")
    public WebApiResult<String> read() { return null; }
    @Operation(security = @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "session"))
    @ApiResponses({@ApiResponse(responseCode = "204", description = "Stopped"),
        @ApiResponse(responseCode = "403", description = "Owner required")})
    public void stop() {}
  }

  @Test
  void methodContractKeepsSecurityScopesAndDeprecationDocumentation() throws Exception {
    var model = extractor.extract(SimpleWebAPIModel.builder().get("/legacy"), mapping("/legacy", RequestMethod.GET),
        CompatibilityController.class, CompatibilityController.class.getMethod("legacy", String.class)).build();
    assertEquals("readLegacyOrders", model.getOperationId());
    assertEquals(Collections.singletonList("legacy-orders"), model.getTags());
    assertEquals(Boolean.TRUE, model.getDeprecated());
    assertEquals("https://example.org/migration", model.getExternalDocs().getUrl());
    assertEquals("Migration", model.getExternalDocs().getDescription());
    assertEquals(java.util.List.of("orders:read"), model.getSecurity().getFirst().getRequirements().get("oauth"));
    var cursor = parameter(model, "cursor");
    assertEquals(Boolean.TRUE, cursor.getRequired());
    assertEquals(Boolean.TRUE, cursor.getDeprecated());
    assertEquals("order-123", cursor.getExample());
    assertEquals("opaque cursor", cursor.getSchema().getTitle());
    assertEquals("^[a-z0-9-]+$", cursor.getSchema().getPattern());
    assertEquals(Integer.valueOf(3), cursor.getSchema().getMinLength());
    assertEquals(Integer.valueOf(64), cursor.getSchema().getMaxLength());
    assertEquals(java.util.List.of("order-123", "order-456"), cursor.getSchema().getEnumValues());
  }

  @Test
  void explicitResponseSchemaPreservesNumericBoundsAndAccessContract() throws Exception {
    var model = extractor.extract(SimpleWebAPIModel.builder().get("/progress"), mapping("/progress", RequestMethod.GET),
        CompatibilityController.class, CompatibilityController.class.getMethod("progress")).build();
    var schema = model.getResponses().get("200").getContent().get("application/json").getSchema();
    assertEquals("number", schema.getType());
    assertEquals("double", schema.getFormat());
    assertEquals(new java.math.BigDecimal("0.5"), schema.getMinimum());
    assertEquals(new java.math.BigDecimal("100"), schema.getMaximum());
    assertEquals(Boolean.TRUE, schema.getNullable());
    assertEquals(Boolean.TRUE, schema.getReadOnly());
    assertEquals(Boolean.TRUE, schema.getDeprecated());
    assertEquals("12.5", schema.getExample());
  }

  @io.swagger.v3.oas.annotations.tags.Tag(name = "controller-default")
  @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "controller-auth")
  static class CompatibilityController {
    @Operation(operationId = "readLegacyOrders", tags = "legacy-orders", deprecated = true,
        externalDocs = @io.swagger.v3.oas.annotations.ExternalDocumentation(url = "https://example.org/migration", description = "Migration"))
    @io.swagger.v3.oas.annotations.security.SecurityRequirement(name = "oauth", scopes = "orders:read")
    public String legacy(@RequestParam("after") @Parameter(name = "cursor", required = true, deprecated = true, example = "order-123",
        schema = @Schema(title = "opaque cursor", minLength = 3, maxLength = 64, pattern = "^[a-z0-9-]+$",
            allowableValues = {"order-123", "order-456"})) String after) { return null; }

    @ApiResponse(responseCode = "200", content = @Content(schema = @Schema(type = "number", format = "double",
        minimum = "0.5", maximum = "100", nullable = true, deprecated = true, example = "12.5", accessMode = Schema.AccessMode.READ_ONLY)))
    public Double progress() { return null; }
  }

  private SimpleWebAPIModel extract(String httpMethod, String path, RequestMappingInfo mapping, Method method) {
    return extractor.extract(
        SimpleWebAPIModel.builder()
            .api(httpMethod, path)
            .scope(WebAPIScope.INTERNAL)
            .handlerType(HandlerType.REQUEST_MAPPING),
        mapping,
        TestController.class,
        method
    ).build();
  }

  private RequestMappingInfo mapping(String path, RequestMethod method) {
    return mapping(path, method, "application/json");
  }

  private RequestMappingInfo mapping(String path, RequestMethod method, String consumes) {
    return RequestMappingInfo.paths(path)
        .methods(method)
        .consumes(consumes)
        .produces("application/json")
        .build();
  }

  private com.ouroboros.webapi.openapi.ParameterModel parameter(SimpleWebAPIModel model, String name) {
    return model.getParameters().stream()
        .filter(parameter -> name.equals(parameter.getName()))
        .findFirst()
        .orElseThrow(() -> new AssertionError("Missing parameter " + name));
  }

  @RestController
  static class TestController {

    @GetMapping("/users/{id}")
    @Operation(summary = "Get user", tags = {"users"})
    public WebApiResult<UserDto> getUser(
        @PathVariable("id") @Parameter(description = "User id") String id,
        @RequestParam(value = "verbose", defaultValue = "false") boolean verbose,
        @RequestHeader("X-Trace-Id") String traceId) {
      return WebApiResult.ok();
    }

    @PostMapping("/users")
    public WebApiResult<UserDto> createUser(@Valid @RequestBody CreateUserRequest request) {
      return WebApiResult.ok();
    }

    @PostMapping("/users/{id}/delete")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Deleted"),
        @ApiResponse(responseCode = "404", description = "Missing user")
    })
    public WebApiResult<Void> deleteUser(@PathVariable("id") String id) {
      return WebApiResult.ok();
    }

    @PostMapping("/auth/bind/{provider}")
    @Operation(
        summary = "Bind external identity",
        description = "Bind an external account to the current platform user.",
        tags = {"authentication"}
    )
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Binding completed"),
        @ApiResponse(responseCode = "409", description = "External identity already bound")
    })
    public WebApiResult<UserDto> bindExternalIdentity(
        @PathVariable("provider") @Parameter(description = "Authentication provider key") String provider,
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Binding request payload", required = true)
        @RequestBody CreateUserRequest request) {
      return WebApiResult.ok();
    }

    @GetMapping("/users/documented")
    @ApiResponse(
        responseCode = "200",
        description = "Documented user response",
        content = @Content(schema = @Schema(implementation = UserDto.class))
    )
    public WebApiResult<UserDto> getDocumentedUser() {
      return WebApiResult.ok();
    }

    @PostMapping(value = "/avatars", consumes = "multipart/form-data")
    public WebApiResult<Void> uploadAvatar(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "Avatar upload payload", required = true)
        @RequestPart("file") @Parameter(description = "Avatar file") MultipartFile file) {
      return WebApiResult.ok();
    }

    @PostMapping(value = "/attachments", consumes = "multipart/form-data")
    public WebApiResult<Void> uploadAttachments(
        @RequestParam("files") @Parameter(description = "Attachment files") MultipartFile[] files) {
      return WebApiResult.ok();
    }

    @PostMapping(value = "/chunks", consumes = "multipart/form-data")
    public WebApiResult<Void> uploadChunkForm(
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Chunk upload form",
            required = true,
            content = @Content(mediaType = "multipart/form-data")
        )
        UploadChunkForm form) {
      return WebApiResult.ok();
    }
  }

  static class CreateUserRequest {
    @Schema(description = "Display name")
    @NotBlank
    @Size(min = 3, max = 50)
    public String name;

    @NotBlank
    @Email
    public String email;

    @Schema(defaultValue = "active")
    public String status;
  }

  static class UserDto {
    @Schema(description = "User identifier")
    public String id;
  }

  static class UploadChunkForm {
    @Schema(description = "Upload session id", requiredMode = Schema.RequiredMode.REQUIRED)
    public String uploadId;

    @Schema(description = "Chunk file content", requiredMode = Schema.RequiredMode.REQUIRED)
    public MultipartFile file;
  }
}
