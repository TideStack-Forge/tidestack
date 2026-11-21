package com.ouroboros.dev.projectgeneration.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.model.GeneratedProject;
import com.ouroboros.dev.projectgeneration.model.ProjectGenerationRequest;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.service.ProjectGenerationService;
import com.ouroboros.dev.projectgeneration.ui.ProjectGenerationAmisSchemaFactory;
import com.ouroboros.web.WebApiResult;

@RestController
@RequestMapping({"/api/dev/{appName}", "/api"})
public class ProjectGenerationController {

  private final ProjectGenerationService projectGenerationService;
  private final ProjectGenerationAmisSchemaFactory amisSchemaFactory = new ProjectGenerationAmisSchemaFactory(null);

  public ProjectGenerationController(ProjectGenerationService projectGenerationService) {
    this.projectGenerationService = projectGenerationService;
  }

  @GetMapping("/project-templates")
  public WebApiResult<ProjectTemplateCatalogView> list(
      @PathVariable(required = false) String appName,
      @RequestParam ProjectTemplateScope scope,
      @RequestParam(defaultValue = "false") boolean includePreview) {
    return WebApiResult.ok(projectGenerationService.list(appName, scope, includePreview));
  }

  @GetMapping("/project-generation-form")
  public WebApiResult<JsonNode> form(
      @PathVariable(required = false) String appName,
      @RequestParam ProjectTemplateScope scope) {
    ProjectTemplateCatalogView catalog = projectGenerationService.list(appName, scope, false);
    return WebApiResult.ok(amisSchemaFactory.buildForm(appName, catalog));
  }

  @PostMapping("/project-generations")
  public ResponseEntity<StreamingResponseBody> generate(
      @PathVariable(required = false) String appName,
      @RequestBody JsonNode payload) {
    GeneratedProject generatedProject = projectGenerationService.generate(appName, ProjectGenerationRequest.fromPayload(payload));
    ContentDisposition contentDisposition = ContentDisposition.attachment()
        .filename(generatedProject.filename(), StandardCharsets.UTF_8)
        .build();
    StreamingResponseBody body = outputStream -> {
      try (generatedProject) {
        generatedProject.content().transferTo(outputStream);
      }
    };

    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
        .contentType(MediaType.parseMediaType("application/zip"))
        .contentLength(generatedProject.size())
        .body(body);
  }
}
