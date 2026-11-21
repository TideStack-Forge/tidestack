package com.ouroboros.dev.projectgeneration.catalog;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Predicate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;

final class ProjectTemplateFamilyRegistry {

  static final String FRAMEWORK_FIELD = "framework";
  static final String BUILD_TOOL_FIELD = "buildTool";

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final List<FamilyDefinition> FAMILIES = List.of(
      frontendFamily(
          "frontend.micro-app",
          "1.0.0",
          ProjectTemplateScope.MICRO_FRONTEND,
          "前端应用",
          "生成可挂载到平台的前端应用。",
          branch("react", "webpack", "frontend.micro-app.react-webpack", "1.0.0"),
          branch("vue", "vite", "frontend.micro-app.vue-vite", "1.0.0")
      ),
      frontendFamily(
          "frontend.layout",
          "1.0.0",
          ProjectTemplateScope.MICRO_FRONTEND,
          "自定义布局",
          "生成平台顶栏和侧栏布局。",
          branch("react", "webpack", "frontend.layout.react-webpack", "1.0.0"),
          branch("vue", "vite", "frontend.layout.vue-vite", "1.0.0")
      ),
      frontendFamily(
          "frontend.component-library",
          "1.0.0",
          ProjectTemplateScope.MICRO_FRONTEND,
          "组件库",
          "生成可复用的微前端组件库。",
          branch("react", "webpack", "frontend.component-library.react-webpack", "1.0.0"),
          branch("vue", "vite", "frontend.component-library.vue-vite", "1.0.0")
      ),
      frontendFamily(
          "frontend.login",
          "0.1.0",
          ProjectTemplateScope.MICRO_FRONTEND,
          "自定义登录页",
          "生成接入平台认证的登录页。",
          branch("react", "webpack", "frontend.login.react-webpack", "0.1.0"),
          branch("vue", "vite", "frontend.login.vue-vite", "0.1.0")
      ),
      frontendFamily(
          "frontend.platform-component-library",
          "1.0.0",
          ProjectTemplateScope.PLATFORM_COMPONENT_LIBRARY,
          "平台组件库",
          "生成可上传到公共组件库的项目。",
          branch("react", "webpack", "frontend.platform-component-library.react-webpack", "1.0.0"),
          branch("vue", "vite", "frontend.platform-component-library.vue-vite", "1.0.0")
      )
  );

  private ProjectTemplateFamilyRegistry() {
  }

  static List<ProjectTemplateSummary> summaries(
      ProjectTemplateCatalogDocument catalog,
      ProjectTemplateScope scope,
      String targetPlatformVersion,
      BiFunction<ProjectTemplateCompatibilityRecord, String, Boolean> visible) {
    List<ProjectTemplateSummary> summaries = new ArrayList<>();
    Map<String, ProjectTemplateRelease> releases = releasesByKey(catalog);
    for (FamilyDefinition family : FAMILIES) {
      if (family.scope() != scope) {
        continue;
      }
      List<VisibleBranch> branches = family.visibleBranches(releases, record -> visible.apply(record, targetPlatformVersion));
      if (branches.isEmpty()) {
        continue;
      }
      summaries.add(family.summary(branches, targetPlatformVersion));
    }
    return summaries;
  }

  static Optional<ResolvedProjectTemplate> resolveFamily(
      ProjectTemplateCatalogDocument catalog,
      ProjectTemplateGenerationSelection selection,
      BiConsumer<ProjectTemplateCompatibilityRecord, String> requireGeneratable) {
    Optional<FamilyDefinition> family = findFamily(selection.templateId());
    if (family.isEmpty()) {
      return Optional.empty();
    }
    FamilyDefinition definition = family.get();
    definition.requireRequestMatches(selection);
    String framework = requiredBranchValue(selection.parameters(), FRAMEWORK_FIELD);
    String buildTool = requiredBranchValue(selection.parameters(), BUILD_TOOL_FIELD);
    BranchDefinition branch = definition.branch(framework, buildTool);
    Map<String, ProjectTemplateRelease> releases = releasesByKey(catalog);
    ProjectTemplateRelease release = releases.get(branch.releaseKey());
    if (release == null || release.scope() != selection.scope()) {
      throw new IllegalArgumentException("Project template family branch is not published: " + branch.releaseKey());
    }
    ProjectTemplateCompatibilityRecord record = release.compatibilityRecords().stream()
        .sorted(Comparator.comparing(ProjectTemplateCompatibilityRecord::channel))
        .filter(candidate -> generatable(candidate, selection.targetPlatformVersion(), requireGeneratable))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("Project template family branch is not compatible with this request"));
    return Optional.of(new ResolvedProjectTemplate(
        release,
        record,
        definition.stripBranchParameters(selection.parameters())
    ));
  }

  static boolean isConcreteBranchTemplate(String templateId) {
    return FAMILIES.stream().flatMap(family -> family.branches().stream())
        .anyMatch(branch -> branch.templateId().equals(templateId));
  }

  static boolean isFamilyTemplate(String templateId) {
    return findFamily(templateId).isPresent();
  }

  private static boolean generatable(
      ProjectTemplateCompatibilityRecord record,
      String targetPlatformVersion,
      BiConsumer<ProjectTemplateCompatibilityRecord, String> requireGeneratable) {
    try {
      requireGeneratable.accept(record, targetPlatformVersion);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  private static Optional<FamilyDefinition> findFamily(String familyId) {
    return FAMILIES.stream().filter(family -> family.familyId().equals(familyId)).findFirst();
  }

  private static String requiredBranchValue(Map<String, Object> parameters, String name) {
    Object value = parameters == null ? null : parameters.get(name);
    if (!(value instanceof String text) || text.isBlank()) {
      throw new IllegalArgumentException("Missing project template branch parameter: " + name);
    }
    return text;
  }

  private static Map<String, ProjectTemplateRelease> releasesByKey(ProjectTemplateCatalogDocument catalog) {
    Map<String, ProjectTemplateRelease> releases = new LinkedHashMap<>();
    for (ProjectTemplateRelease release : catalog.releases()) {
      releases.put(release.templateId() + "@" + release.templateVersion(), release);
    }
    return releases;
  }

  private static FamilyDefinition frontendFamily(
      String familyId,
      String familyVersion,
      ProjectTemplateScope scope,
      String title,
      String description,
      BranchDefinition... branches) {
    return new FamilyDefinition(familyId, familyVersion, scope, title, description, List.of(branches));
  }

  private static BranchDefinition branch(String framework, String buildTool, String templateId, String templateVersion) {
    return new BranchDefinition(framework, buildTool, templateId, templateVersion);
  }

  private static String familyRowId(FamilyDefinition family, String targetPlatformVersion) {
    return family.familyId() + "-" + family.familyVersion() + "-platform-" + targetPlatformVersion;
  }

  private static ObjectNode stringEnumProperty(String title, List<String> values, String defaultValue) {
    ObjectNode property = JSON.createObjectNode();
    property.put("type", "string");
    property.put("title", title);
    property.put("maxLength", 32);
    property.put("default", defaultValue);
    ArrayNode enumValues = property.putArray("enum");
    values.forEach(enumValues::add);
    return property;
  }

  private static ObjectNode branchHint(int order, String help, Map<String, String> labels) {
    ObjectNode hint = JSON.createObjectNode();
    hint.put("order", order);
    hint.put("control", "button-group-select");
    hint.put("help", help);
    ObjectNode optionLabels = hint.putObject("optionLabels");
    labels.forEach(optionLabels::put);
    return hint;
  }

  private record FamilyDefinition(
      String familyId,
      String familyVersion,
      ProjectTemplateScope scope,
      String title,
      String description,
      List<BranchDefinition> branches
  ) {

    private ProjectTemplateSummary summary(List<VisibleBranch> visibleBranches, String targetPlatformVersion) {
      ProjectTemplateRelease baseRelease = visibleBranches.getFirst().release();
      return new ProjectTemplateSummary(
          familyId,
          familyVersion,
          familyRowId(this, targetPlatformVersion),
          title,
          description,
          visibleBranches.stream().anyMatch(branch -> branch.record().channel() == ProjectTemplateChannel.stable)
              ? ProjectTemplateChannel.stable.name()
              : ProjectTemplateChannel.preview.name(),
          parameterSchema(baseRelease.parameterSchema(), visibleBranches),
          uiHints(baseRelease.uiHints(), visibleBranches)
      );
    }

    private List<VisibleBranch> visibleBranches(
        Map<String, ProjectTemplateRelease> releases,
        Predicate<ProjectTemplateCompatibilityRecord> visible) {
      List<VisibleBranch> result = new ArrayList<>();
      for (BranchDefinition branch : branches) {
        ProjectTemplateRelease release = releases.get(branch.releaseKey());
        if (release == null) {
          continue;
        }
        release.compatibilityRecords().stream()
            .filter(visible)
            .findFirst()
            .ifPresent(record -> result.add(new VisibleBranch(branch, release, record)));
      }
      return result;
    }

    private BranchDefinition branch(String framework, String buildTool) {
      return branches.stream()
          .filter(branch -> branch.framework().equals(framework) && branch.buildTool().equals(buildTool))
          .findFirst()
          .orElseThrow(() -> new IllegalArgumentException("Unsupported project template branch: "
              + framework + "/" + buildTool));
    }

    private void requireRequestMatches(ProjectTemplateGenerationSelection selection) {
      if (scope != selection.scope()) {
        throw new IllegalArgumentException("Project template family scope does not match request");
      }
      if (!familyVersion.equals(selection.templateVersion())) {
        throw new IllegalArgumentException("Project template family version is stale");
      }
      if (!familyRowId(this, selection.targetPlatformVersion()).equals(selection.compatibilityRowId())) {
        throw new IllegalArgumentException("Project template family compatibility row is stale");
      }
    }

    private Map<String, Object> stripBranchParameters(Map<String, Object> parameters) {
      Map<String, Object> result = new LinkedHashMap<>();
      if (parameters == null) {
        return result;
      }
      for (Map.Entry<String, Object> entry : parameters.entrySet()) {
        if (!FRAMEWORK_FIELD.equals(entry.getKey()) && !BUILD_TOOL_FIELD.equals(entry.getKey())) {
          result.put(entry.getKey(), entry.getValue());
        }
      }
      return result;
    }

    private JsonNode parameterSchema(JsonNode baseSchema, List<VisibleBranch> visibleBranches) {
      ObjectNode schema = baseSchema.deepCopy();
      ObjectNode mergedProperties = JSON.createObjectNode();
      List<String> frameworks = distinct(visibleBranches, VisibleBranch::framework);
      List<String> buildTools = distinct(visibleBranches, VisibleBranch::buildTool);
      mergedProperties.set(FRAMEWORK_FIELD, stringEnumProperty("技术栈", frameworks, defaultValue(frameworks, "react")));
      mergedProperties.set(BUILD_TOOL_FIELD, stringEnumProperty("构建方式", buildTools, defaultValue(buildTools, "webpack")));
      baseSchema.path("properties").fields().forEachRemaining(entry -> mergedProperties.set(entry.getKey(), entry.getValue()));
      schema.set("properties", mergedProperties);

      ArrayNode required = JSON.createArrayNode();
      required.add(FRAMEWORK_FIELD);
      required.add(BUILD_TOOL_FIELD);
      for (JsonNode item : baseSchema.path("required")) {
        String name = item.asText();
        if (!FRAMEWORK_FIELD.equals(name) && !BUILD_TOOL_FIELD.equals(name)) {
          required.add(name);
        }
      }
      schema.set("required", required);
      return schema;
    }

    private JsonNode uiHints(JsonNode baseHints, List<VisibleBranch> visibleBranches) {
      ObjectNode hints = JSON.createObjectNode();
      hints.set(FRAMEWORK_FIELD, branchHint(
          1,
          "选择前端框架。",
          Map.of("react", "React", "vue", "Vue")
      ));
      ObjectNode buildToolHint = branchHint(
          2,
          "选择构建方式。",
          Map.of("webpack", "Webpack", "vite", "Vite")
      );
      ObjectNode visibleByOption = buildToolHint.putObject("optionVisibleWhen");
      for (String buildTool : distinct(visibleBranches, VisibleBranch::buildTool)) {
        List<String> frameworks = visibleBranches.stream()
            .filter(branch -> branch.buildTool().equals(buildTool))
            .map(VisibleBranch::framework)
            .distinct()
            .toList();
        visibleByOption.put(buildTool, equalsAnyExpression(FRAMEWORK_FIELD, frameworks));
      }
      hints.set(BUILD_TOOL_FIELD, buildToolHint);

      baseHints.fields().forEachRemaining(entry -> {
        if (!FRAMEWORK_FIELD.equals(entry.getKey()) && !BUILD_TOOL_FIELD.equals(entry.getKey())) {
          hints.set(entry.getKey(), entry.getValue());
        }
      });
      return hints;
    }

    private static String equalsAnyExpression(String field, List<String> values) {
      StringBuilder expression = new StringBuilder("${");
      for (int i = 0; i < values.size(); i++) {
        if (i > 0) {
          expression.append(" || ");
        }
        expression.append(field).append(" == '").append(values.get(i).replace("'", "\\'")).append("'");
      }
      return expression.append('}').toString();
    }

    private static List<String> distinct(List<VisibleBranch> branches, java.util.function.Function<VisibleBranch, String> mapper) {
      Set<String> values = new LinkedHashSet<>();
      branches.stream().map(mapper).forEach(values::add);
      return List.copyOf(values);
    }

    private static String defaultValue(List<String> values, String preferred) {
      return values.contains(preferred) ? preferred : values.getFirst();
    }
  }

  private record BranchDefinition(
      String framework,
      String buildTool,
      String templateId,
      String templateVersion
  ) {
    private String releaseKey() {
      return templateId + "@" + templateVersion;
    }
  }

  private record VisibleBranch(
      BranchDefinition definition,
      ProjectTemplateRelease release,
      ProjectTemplateCompatibilityRecord record
  ) {
    private String framework() {
      return definition.framework();
    }

    private String buildTool() {
      return definition.buildTool();
    }
  }
}
