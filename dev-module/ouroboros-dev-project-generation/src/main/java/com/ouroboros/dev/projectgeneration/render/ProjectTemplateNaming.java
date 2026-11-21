package com.ouroboros.dev.projectgeneration.render;

import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;

/**
 * Derives project-file names from the canonical user-facing identifier.
 * The generator must not ask users to repeat values that are mechanically derived.
 */
final class ProjectTemplateNaming {

  private ProjectTemplateNaming() {
  }

  static String projectDirectory(Map<String, Object> parameters, JsonNode parameterSchema) {
    String rootParameter = parameterSchema.path("x-ouroboros-project-root").asText(null);
    if (rootParameter == null || rootParameter.isBlank()) {
      throw new IllegalArgumentException("Template parameter schema must declare x-ouroboros-project-root");
    }
    Object rootValue = parameters.get(rootParameter);
    if (!(rootValue instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("Project root parameter is missing: " + rootParameter);
    }
    String base = npmBaseName(text);
    return ProjectTemplateIdentifierContracts.requirePathSegment(slug(base), "projectDirectory");
  }

  static Map<String, Object> model(Map<String, Object> parameters, JsonNode parameterSchema) {
    String rootParameter = parameterSchema.path("x-ouroboros-project-root").asText(null);
    Object rootValue = rootParameter == null ? null : parameters.get(rootParameter);
    String root = rootValue instanceof String text && !text.isBlank() ? npmBaseName(text) : "project";
    String slug = slug(root);

    Map<String, Object> model = new LinkedHashMap<>();
    model.put("name", ProjectTemplateIdentifierContracts.requirePathSegment(slug, "project.name"));
    model.put("slug", slug);
    model.put("sourceName", pascalCase(slug));
    model.put("libraryName", slug);

    Object javaPackage = parameters.get("packageName");
    String packageFormat = parameterSchema.path("properties").path("packageName")
        .path("x-ouroboros-format").asText(null);
    if (javaPackage instanceof String text && "java-package".equals(packageFormat)) {
      model.put("packagePath", text.replace('.', '/'));
    }
    return model;
  }

  private static String npmBaseName(String value) {
    int slash = value.lastIndexOf('/');
    return slash >= 0 ? value.substring(slash + 1) : value;
  }

  private static String slug(String value) {
    String normalized = value.replace('.', '-');
    if (normalized.isBlank()) {
      throw new IllegalArgumentException("Project identifier must not be blank");
    }
    return normalized;
  }

  private static String pascalCase(String value) {
    StringBuilder result = new StringBuilder();
    boolean upperNext = true;
    for (int index = 0; index < value.length(); index++) {
      char ch = value.charAt(index);
      if (!Character.isLetterOrDigit(ch)) {
        upperNext = true;
        continue;
      }
      result.append(upperNext ? Character.toUpperCase(ch) : ch);
      upperNext = false;
    }
    if (result.isEmpty()) {
      throw new IllegalArgumentException("Project identifier must contain a name");
    }
    return result.toString();
  }
}
