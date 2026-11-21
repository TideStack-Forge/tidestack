package com.ouroboros.dev.projectgeneration.render;

import java.io.IOException;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateCompatibilityRecord;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateIdentifierContracts;
import com.ouroboros.dev.projectgeneration.catalog.ProjectTemplateRelease;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;
import com.ouroboros.template.TextTemplateEngine;
import com.ouroboros.template.TextTemplateException;

public class ProjectTemplateRenderer {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Set<String> PATH_TEMPLATE_FORMATS = Set.of("path-segment", "java-class-name", "js-identifier");
  private static final String CONTRACT_VERSION = "2";

  private final ProjectTemplateRendererLimits limits;
  private final ProjectTemplateParameterValidator parameterValidator;
  private final TextTemplateEngine textTemplateEngine;
  private final ProjectTemplateEscapes escapes;

  public ProjectTemplateRenderer(
      ProjectTemplateRendererLimits limits,
      ProjectTemplateParameterValidator parameterValidator) {
    this(limits, parameterValidator, new TextTemplateEngine());
  }

  public ProjectTemplateRenderer(
      ProjectTemplateRendererLimits limits,
      ProjectTemplateParameterValidator parameterValidator,
      TextTemplateEngine textTemplateEngine) {
    this.limits = limits == null ? ProjectTemplateRendererLimits.defaults() : limits;
    this.parameterValidator = parameterValidator == null
        ? new ProjectTemplateParameterValidator()
        : parameterValidator;
    this.textTemplateEngine = textTemplateEngine == null ? new TextTemplateEngine() : textTemplateEngine;
    this.escapes = new ProjectTemplateEscapes();
  }

  public RenderedProjectTemplate render(
      ProjectTemplateRelease release,
      ProjectTemplateCompatibilityRecord compatibilityRecord,
      Map<String, byte[]> archiveEntries,
      Map<String, Object> parameters,
      ProjectTemplateRenderContext context) {
    if (release == null || compatibilityRecord == null || archiveEntries == null || context == null) {
      throw new IllegalArgumentException("Template render dependencies are required");
    }
    long deadlineNanos = System.nanoTime() + limits.deadline().toNanos();
    Map<String, Object> normalized = parameterValidator.normalize(
        release.parameterSchema(),
        release.uiHints(),
        parameters
    );
    String projectName = ProjectTemplateNaming.projectDirectory(normalized, release.parameterSchema());
    JsonNode manifest = readManifest(archiveEntries);
    validateManifestIdentity(release, manifest);
    JsonNode operations = manifest.path("operations");
    if (!operations.isArray()) {
      throw new IllegalArgumentException("Template manifest operations must be an array");
    }
    limits.validateOperationCount(operations.size());
    Map<String, byte[]> renderedEntries = new LinkedHashMap<>();
    Set<String> caseFoldedTargets = new HashSet<>();
    long totalBytes = 0;
    for (JsonNode operation : operations) {
      checkDeadline(deadlineNanos);
      if (!operation.isObject()) {
        throw new IllegalArgumentException("Template operation must be an object");
      }
      if (!shouldRun(operation.path("when"), normalized)) {
        continue;
      }
      byte[] bytes = renderOperation(release, operation, archiveEntries, normalized);
      String target = projectName + "/"
          + canonicalTarget(renderTarget(operation, normalized, release.parameterSchema()));
      requireUniqueTarget(caseFoldedTargets, target);
      limits.validateEntrySize(bytes.length, target);
      totalBytes += bytes.length;
      limits.validateTotalSize(totalBytes);
      renderedEntries.put(target, bytes);
    }
    byte[] provenance = provenance(release, compatibilityRecord, normalized, context);
    String provenanceTarget = projectName + "/.ouroboros-template.json";
    requireUniqueTarget(caseFoldedTargets, provenanceTarget);
    limits.validateEntrySize(provenance.length, provenanceTarget);
    totalBytes += provenance.length;
    limits.validateTotalSize(totalBytes);
    renderedEntries.put(provenanceTarget, provenance);
    return new RenderedProjectTemplate(projectName, renderedEntries);
  }

  private byte[] renderOperation(
      ProjectTemplateRelease release,
      JsonNode operation,
      Map<String, byte[]> archiveEntries,
      Map<String, Object> parameters) {
    String kind = operationText(operation, "kind");
    String source = canonicalSource(operationText(operation, "source"));
    byte[] sourceBytes = archiveEntries.get(source);
    if (sourceBytes == null) {
      throw new IllegalArgumentException("Template operation source is missing: " + source);
    }
    return switch (kind) {
      case "copy" -> sourceBytes.clone();
      case "render" -> renderContent(release, operation, sourceBytes, parameters);
      default -> throw new IllegalArgumentException("Unsupported template operation kind: " + kind);
    };
  }

  private byte[] renderContent(
      ProjectTemplateRelease release,
      JsonNode operation,
      byte[] sourceBytes,
      Map<String, Object> parameters) {
    String target = operationText(operation, "target");
    String source = decodeUtf8(sourceBytes);
    if (isCommandScript(target) && containsFreemarkerSyntax(source)) {
      throw new IllegalArgumentException("Command scripts must not interpolate project template parameters");
    }
    return renderTextTemplate(source, parameters, release.parameterSchema(), "Template render source")
        .getBytes(StandardCharsets.UTF_8);
  }

  private String renderTarget(
      JsonNode operation,
      Map<String, Object> parameters,
      JsonNode parameterSchema) {
    String target = operationText(operation, "target");
    return renderTextTemplate(target, parameters, parameterSchema, "Template operation target");
  }

  private static JsonNode readManifest(Map<String, byte[]> archiveEntries) {
    byte[] manifest = archiveEntries.get("template.json");
    if (manifest == null) {
      throw new IllegalArgumentException("Template archive must contain template.json");
    }
    try {
      return OBJECT_MAPPER.readTree(manifest);
    } catch (IOException e) {
      throw new IllegalArgumentException("Template manifest must be JSON", e);
    }
  }

  private static void validateManifestIdentity(ProjectTemplateRelease release, JsonNode manifest) {
    if (!manifest.isObject()) {
      throw new IllegalArgumentException("Template manifest must be an object");
    }
    if (!CONTRACT_VERSION.equals(operationText(manifest, "contractVersion"))) {
      throw new IllegalArgumentException("Unsupported template manifest contractVersion");
    }
    if (!release.templateId().equals(operationText(manifest, "templateId"))
        || !release.templateVersion().equals(operationText(manifest, "templateVersion"))) {
      throw new IllegalArgumentException("Template manifest identity does not match catalog release");
    }
  }

  private static boolean shouldRun(JsonNode when, Map<String, Object> parameters) {
    if (when.isMissingNode() || when.isNull()) {
      return true;
    }
    if (!when.isObject()) {
      throw new IllegalArgumentException("Template operation when must be an object");
    }
    String parameter = operationText(when, "parameter");
    Object actual = parameters.get(parameter);
    if (actual == null) {
      return false;
    }
    JsonNode equals = when.path("equals");
    JsonNode in = when.path("in");
    if (!equals.isMissingNode() == !in.isMissingNode()) {
      throw new IllegalArgumentException("Template operation when must use exactly one of equals/in");
    }
    if (!equals.isMissingNode()) {
      return equals(actual, scalarValue(equals));
    }
    if (!in.isArray() || in.isEmpty()) {
      throw new IllegalArgumentException("Template operation when in must be a non-empty array");
    }
    for (JsonNode item : in) {
      if (equals(actual, scalarValue(item))) {
        return true;
      }
    }
    return false;
  }

  private static boolean equals(Object actual, Object expected) {
    if (!actual.getClass().equals(expected.getClass())) {
      throw new IllegalArgumentException("Template operation when value type does not match parameter");
    }
    return actual.equals(expected);
  }

  private static Object scalarValue(JsonNode node) {
    if (node.isTextual()) {
      return node.textValue();
    }
    if (node.isBoolean()) {
      return node.booleanValue();
    }
    if (node.isIntegralNumber()) {
      return node.intValue();
    }
    throw new IllegalArgumentException("Template operation when value must be scalar");
  }

  private static String canonicalSource(String source) {
    String canonical = canonicalRelativePath(source, "Template operation source");
    if (!canonical.startsWith("tree/")) {
      throw new IllegalArgumentException("Template operation source must be under tree/: " + source);
    }
    return canonical;
  }

  private static String canonicalTarget(String target) {
    return canonicalRelativePath(target, "Template operation target");
  }

  private static String canonicalRelativePath(String path, String fieldName) {
    if (path == null || path.isBlank()) {
      throw new IllegalArgumentException(fieldName + " is required");
    }
    if (!Normalizer.isNormalized(path, Normalizer.Form.NFC)) {
      throw new IllegalArgumentException(fieldName + " must be NFC");
    }
    if (path.startsWith("/") || path.startsWith("//") || path.contains("\\") || path.matches("^[A-Za-z]:.*")) {
      throw new IllegalArgumentException(fieldName + " must be a relative path");
    }
    for (int index = 0; index < path.length(); index++) {
      char ch = path.charAt(index);
      if (ch == '\u0000' || Character.isISOControl(ch)) {
        throw new IllegalArgumentException(fieldName + " must not contain control characters");
      }
    }
    for (String segment : path.split("/")) {
      if (segment.isBlank() || ".".equals(segment) || "..".equals(segment)) {
        throw new IllegalArgumentException(fieldName + " contains unsafe segment");
      }
    }
    return path;
  }

  private static void requireUniqueTarget(Set<String> caseFoldedTargets, String target) {
    String folded = target.toLowerCase(Locale.ROOT);
    if (!caseFoldedTargets.add(folded)) {
      throw new IllegalArgumentException("Rendered template contains duplicate target: " + target);
    }
  }

  private static String operationText(JsonNode node, String fieldName) {
    JsonNode value = node.get(fieldName);
    if (value == null || !value.isTextual() || value.textValue().isBlank()) {
      throw new IllegalArgumentException("Template field is missing: " + fieldName);
    }
    return value.textValue();
  }

  private static String decodeUtf8(byte[] bytes) {
    try {
      return StandardCharsets.UTF_8.newDecoder()
          .decode(java.nio.ByteBuffer.wrap(bytes))
          .toString();
    } catch (CharacterCodingException e) {
      throw new IllegalArgumentException("Template render source must be UTF-8", e);
    }
  }

  private static boolean isCommandScript(String target) {
    String lower = target.toLowerCase(Locale.ROOT);
    return lower.endsWith(".sh") || lower.endsWith(".cmd") || lower.endsWith(".ps1");
  }

  private static boolean containsFreemarkerSyntax(String text) {
    return text.contains("${") || text.contains("<#");
  }

  private String renderTextTemplate(
      String templateText,
      Map<String, Object> parameters,
      JsonNode parameterSchema,
      String fieldName) {
    try {
      return textTemplateEngine.render(templateText, freemarkerModel(parameters, parameterSchema));
    } catch (TextTemplateException e) {
      throw new IllegalArgumentException(fieldName + " could not be rendered", e);
    }
  }

  private Map<String, Object> freemarkerModel(Map<String, Object> parameters, JsonNode parameterSchema) {
    Map<String, Object> model = new LinkedHashMap<>(parameters);
    model.put("project", ProjectTemplateNaming.model(parameters, parameterSchema));
    model.put("path", freemarkerPathModel(parameters, parameterSchema));
    model.put("esc", escapes);
    return model;
  }

  private static Map<String, String> freemarkerPathModel(Map<String, Object> parameters, JsonNode parameterSchema) {
    Map<String, String> path = new LinkedHashMap<>();
    Map<String, Object> project = ProjectTemplateNaming.model(parameters, parameterSchema);
    project.forEach((key, value) -> {
      if (value instanceof String text) {
        path.put(key, text);
      }
    });
    JsonNode properties = parameterSchema.path("properties");
    for (Map.Entry<String, Object> entry : parameters.entrySet()) {
      if (!(entry.getValue() instanceof String text)) {
        continue;
      }
      String parameterName = entry.getKey();
      String semanticFormat = properties.path(parameterName).path("x-ouroboros-format").asText(null);
      if ("relative-path".equals(semanticFormat)) {
        path.put(parameterName, ProjectTemplateIdentifierContracts.requireRelativePathParameter(text, parameterName));
      } else if (semanticFormat != null && PATH_TEMPLATE_FORMATS.contains(semanticFormat)) {
        path.put(parameterName, ProjectTemplateIdentifierContracts.requirePathSegment(text, parameterName));
      }
    }
    return path;
  }

  private static void checkDeadline(long deadlineNanos) {
    if (System.nanoTime() > deadlineNanos) {
      throw new IllegalStateException("Template rendering deadline exceeded");
    }
  }

  private static byte[] provenance(
      ProjectTemplateRelease release,
      ProjectTemplateCompatibilityRecord compatibilityRecord,
      Map<String, Object> parameters,
      ProjectTemplateRenderContext context) {
    Map<String, Object> root = new LinkedHashMap<>();
    root.put("contractVersion", "1");
    root.put("generatedAt", Instant.EPOCH.toString());
    root.put("templateId", release.templateId());
    root.put("templateVersion", release.templateVersion());
    root.put("catalogRevision", context.catalogRevision());
    root.put("catalogDigestSha256", context.catalogDigestSha256());
    root.put("targetPlatformVersion", context.targetPlatformVersion());
    root.put("compatibilityRowId", compatibilityRecord.rowId());
    root.put("artifactSha256", context.artifactSha256());
    root.put("parameters", parameters);
    try {
      return OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValueAsBytes(root);
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("Cannot write template provenance", e);
    }
  }
}
