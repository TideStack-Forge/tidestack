package com.ouroboros.dev.projectgeneration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.ouroboros.dev.projectgeneration.controller.ProjectGenerationController;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;
import com.ouroboros.dev.projectgeneration.service.impl.BuildMetadataProjectPlatformVersionProvider;
import com.ouroboros.dev.projectgeneration.service.impl.EmptyCatalogProjectGenerationService;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.web.WebApiResult;

class ProjectGenerationApiContractTest {

  private static final ObjectMapper JSON = JsonMapper.builder().build();

  @Test
  void shouldExposeProjectTemplateFormAndGenerationRoutesUnderDevAppPath() throws Exception {
    RequestMapping baseMapping = ProjectGenerationController.class.getAnnotation(RequestMapping.class);
    assertNotNull(baseMapping);
    assertEquals("/api/dev/{appName}", baseMapping.value()[0]);
    assertEquals("/api", baseMapping.value()[1]);

    Method list = ProjectGenerationController.class.getMethod(
        "list",
        String.class,
        ProjectTemplateScope.class,
        boolean.class
    );
    Method generate = ProjectGenerationController.class.getMethod(
        "generate",
        String.class,
        com.fasterxml.jackson.databind.JsonNode.class
    );
    Method form = ProjectGenerationController.class.getMethod(
        "form",
        String.class,
        ProjectTemplateScope.class
    );
    assertEquals("/project-templates", list.getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/project-generation-form", form.getAnnotation(GetMapping.class).value()[0]);
    assertEquals("/project-generations", generate.getAnnotation(PostMapping.class).value()[0]);
    assertEquals(WebApiResult.class, list.getReturnType());
    assertEquals(WebApiResult.class, form.getReturnType());
    assertEquals(ResponseEntity.class, generate.getReturnType());
  }


  @Test
  void shouldAdaptAmisFormPayloadToCanonicalGenerationRequest() throws Exception {
    ProjectGenerationRequest request = ProjectGenerationRequest.fromPayload(JSON.readTree("""
        {
          "scope": "MICRO_FRONTEND",
          "catalogRevision": "cat_42",
          "targetPlatformVersion": "2.0.0",
          "allowPreview": false,
          "template": {
            "templateId": "frontend.micro-app",
            "templateVersion": "1.0.0",
            "compatibilityRowId": "frontend.micro-app-1.0.0-platform-2.0.0"
          },
          "framework": "react",
          "buildTool": "webpack",
          "packageName": "@example/customer-portal",
          "includeExample": true
        }
        """));

    assertEquals(ProjectTemplateScope.MICRO_FRONTEND, request.scope());
    assertEquals("frontend.micro-app", request.templateId());
    assertEquals("1.0.0", request.templateVersion());
    assertEquals("frontend.micro-app-1.0.0-platform-2.0.0", request.compatibilityRowId());
    assertEquals("react", request.parameters().get("framework"));
    assertEquals("webpack", request.parameters().get("buildTool"));
    assertEquals("@example/customer-portal", request.parameters().get("packageName"));
    assertEquals(true, request.parameters().get("includeExample"));
  }

  @Test
  void shouldReturnPlatformVersionAndScopeForEmptyCatalog() {
    EmptyCatalogProjectGenerationService service = new EmptyCatalogProjectGenerationService(
        new BuildMetadataProjectPlatformVersionProvider(null, "2.0.0-test")
    );

    ProjectTemplateCatalogView view = service.list("demo", ProjectTemplateScope.MICRO_FRONTEND, false);

    assertEquals(EmptyCatalogProjectGenerationService.EMPTY_REVISION, view.revision());
    assertEquals("2.0.0-test", view.targetPlatformVersion());
    assertEquals(ProjectTemplateScope.MICRO_FRONTEND, view.scope());
    assertEquals(false, view.previewIncluded());
    assertEquals(0, view.templates().size());
  }

  @Test
  void shouldFailClosedWhenCatalogIsNotLoaded() {
    EmptyCatalogProjectGenerationService service = new EmptyCatalogProjectGenerationService(
        new BuildMetadataProjectPlatformVersionProvider(null, "2.0.0-test")
    );

    assertThrows(BusinessException.class, () -> service.generate("demo", null));
  }

  @Test
  void shouldStreamGeneratedZipAndCloseGeneratedProject() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);
    byte[] zipBytes = "zip-body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ProjectGenerationController controller = new ProjectGenerationController(serviceReturning(zipBytes, closed));

    ResponseEntity<StreamingResponseBody> response = controller.generate("demo", generationPayload());
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    response.getBody().writeTo(output);

    assertEquals(MediaType.parseMediaType("application/zip"), response.getHeaders().getContentType());
    assertEquals(zipBytes.length, response.getHeaders().getContentLength());
    assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("attachment"));
    assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION).contains("filename*="));
    assertEquals("zip-body", output.toString(java.nio.charset.StandardCharsets.UTF_8));
    assertTrue(closed.get());
  }

  @Test
  void shouldCloseGeneratedProjectWhenStreamingFails() throws Exception {
    AtomicBoolean closed = new AtomicBoolean(false);
    byte[] zipBytes = "zip-body".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    ProjectGenerationController controller = new ProjectGenerationController(serviceReturning(zipBytes, closed));
    ResponseEntity<StreamingResponseBody> response = controller.generate("demo", generationPayload());

    assertThrows(IOException.class, () -> response.getBody().writeTo(new OutputStream() {
      @Override
      public void write(int value) throws IOException {
        throw new IOException("client disconnected");
      }

      @Override
      public void write(byte[] bytes, int offset, int length) throws IOException {
        throw new IOException("client disconnected");
      }
    }));
    assertTrue(closed.get());
  }


  private static com.fasterxml.jackson.databind.JsonNode generationPayload() throws Exception {
    return JSON.readTree("""
        {
          "scope": "MICRO_FRONTEND",
          "catalogRevision": "rev",
          "targetPlatformVersion": "2.0.0",
          "templateId": "frontend.micro-app",
          "templateVersion": "1.0.0",
          "compatibilityRowId": "frontend.micro-app-1.0.0-platform-2.0.0",
          "allowPreview": false,
          "parameters": {"framework": "react", "buildTool": "webpack", "packageName": "customer-portal"}
        }
        """);
  }

  private static ProjectGenerationService serviceReturning(byte[] zipBytes, AtomicBoolean closed) {
    return new ProjectGenerationService() {
      @Override
      public ProjectTemplateCatalogView list(String appName, ProjectTemplateScope scope, boolean includePreview) {
        return new ProjectTemplateCatalogView("rev", "2.0.0", scope, includePreview, List.of());
      }

      @Override
      public GeneratedProject generate(String appName, ProjectGenerationRequest request) {
        return new GeneratedProject(
            "客户门户-1.0.0.zip",
            zipBytes.length,
            "0".repeat(64),
            null,
            new ByteArrayInputStream(zipBytes),
            () -> closed.set(true)
        );
      }
    };
  }
}
