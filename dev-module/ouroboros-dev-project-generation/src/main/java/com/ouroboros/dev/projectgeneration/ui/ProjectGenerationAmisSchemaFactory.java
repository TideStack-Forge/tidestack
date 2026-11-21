package com.ouroboros.dev.projectgeneration.ui;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateCatalogView;
import com.ouroboros.dev.projectgeneration.model.ProjectTemplateSummary;
import com.ouroboros.dev.projectgeneration.parameter.ProjectTemplateParameterValidator;

public class ProjectGenerationAmisSchemaFactory {

  private static final ObjectMapper JSON = JsonMapper.builder().build();
  private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

  private final ProjectTemplateParameterValidator parameterValidator;

  public ProjectGenerationAmisSchemaFactory(ProjectTemplateParameterValidator parameterValidator) {
    this.parameterValidator = parameterValidator == null
        ? new ProjectTemplateParameterValidator()
        : parameterValidator;
  }

  public JsonNode buildForm(ProjectTemplateCatalogView catalog) {
    return buildForm(null, catalog);
  }

  public JsonNode buildForm(String appName, ProjectTemplateCatalogView catalog) {
    ObjectNode form = object("type", "form");
    form.put("wrapWithPanel", true);
    form.put("mode", "horizontal");
    form.put("title", false);
    form.set("data", formData(appName, catalog));
    ArrayNode body = form.putArray("body");
    if (catalog.templates().isEmpty()) {
      body.add(alert("warning", "当前没有可用的官方项目模板，请刷新模板目录后重试。"));
    }
    body.add(templateSelect(catalog));
    addTemplateParameterItems(body, catalog);
    form.set("api", generationApi(catalog));
    form.set("actions", actions(catalog));
    return form;
  }

  public List<JsonNode> buildParameterItems(ProjectTemplateSummary template) {
    parameterValidator.validateContract(template.parameterSchema(), template.uiHints());
    Set<String> required = required(template.parameterSchema());
    JsonNode properties = template.parameterSchema().path("properties");
    List<ParameterEntry> entries = new ArrayList<>();
    int index = 0;
    var fields = properties.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      entries.add(new ParameterEntry(index++, entry.getKey(), entry.getValue()));
    }
    entries.sort(Comparator
        .comparingInt((ParameterEntry entry) -> orderOf(template.uiHints().path(entry.name())))
        .thenComparingInt(ParameterEntry::index));
    return entries.stream()
        .map(entry -> (JsonNode) formItem(
            entry.name(),
            entry.property(),
            template.uiHints().path(entry.name()),
            required.contains(entry.name())
        ))
        .toList();
  }

  private ObjectNode formData(String appName, ProjectTemplateCatalogView catalog) {
    ObjectNode data = NODES.objectNode();
    if (appName != null && !appName.isBlank()) {
      data.put("appName", appName);
    }
    data.put("scope", catalog.scope().name());
    data.put("catalogRevision", catalog.revision());
    data.put("targetPlatformVersion", catalog.targetPlatformVersion());
    data.put("allowPreview", false);
    putInitialParameterValues(data, catalog);
    if (!catalog.templates().isEmpty()) {
      data.set("template", templateOption(catalog.templates().get(0)));
    }
    return data;
  }

  private ObjectNode templateSelect(ProjectTemplateCatalogView catalog) {
    ObjectNode select = object("type", "list-select");
    select.put("id", "project-generator-template");
    select.put("name", "template");
    select.put("label", "项目模板");
    select.put("required", true);
    select.put("searchable", true);
    select.put("clearable", false);
    select.put("joinValues", false);
    select.put("extractValue", false);
    select.put("labelField", "label");
    select.put("valueField", "value");
    ObjectNode itemSchema = select.putObject("itemSchema");
    itemSchema.put("type", "tpl");
    itemSchema.put("tpl", "<div><strong>${label}</strong><div class=\"text-muted\">${description}</div></div>");
    select.put("description", "只展示当前平台版本兼容的官方模板。");
    ArrayNode options = select.putArray("options");
    catalog.templates().forEach(template -> options.add(templateOption(template)));
    return select;
  }

  private ObjectNode templateOption(ProjectTemplateSummary template) {
    ObjectNode option = NODES.objectNode();
    option.put("label", template.title());
    option.put("value", template.templateId() + "@" + template.templateVersion() + "#" + template.compatibilityRowId());
    option.put("templateId", template.templateId());
    option.put("templateVersion", template.templateVersion());
    option.put("compatibilityRowId", template.compatibilityRowId());
    option.put("title", template.title());
    option.put("description", template.description());
    option.put("channel", template.channel());
    return option;
  }

  private ObjectNode generationApi(ProjectTemplateCatalogView catalog) {
    ObjectNode api = object("method", "post");
    api.put("url", catalog.scope() == com.ouroboros.dev.projectgeneration.model.ProjectTemplateScope.PLATFORM_COMPONENT_LIBRARY
        ? "/api/project-generations"
        : "/api/dev/${appName}/project-generations");
    api.put("responseType", "blob");
    return api;
  }


  private ArrayNode actions(ProjectTemplateCatalogView catalog) {
    boolean noTemplate = catalog.templates().isEmpty();
    ArrayNode actions = NODES.arrayNode();
    ObjectNode cancel = object("type", "button");
    cancel.put("label", "取消");
    cancel.put("actionType", "cancel");
    actions.add(cancel);

    ObjectNode submit = object("type", "submit");
    submit.put("label", "生成并下载");
    submit.put("level", "primary");
    submit.put("disabledOn", noTemplate ? "true" : "${!template}");
    actions.add(submit);
    return actions;
  }


  private void addTemplateParameterItems(ArrayNode body, ProjectTemplateCatalogView catalog) {
    for (ProjectTemplateSummary template : catalog.templates()) {
      for (JsonNode item : buildParameterItems(template)) {
        addTemplateVisibleOn((ObjectNode) item, template);
        body.add(item);
      }
    }
  }

  private void putInitialParameterValues(ObjectNode data, ProjectTemplateCatalogView catalog) {
    if (catalog.templates().isEmpty()) {
      return;
    }
    JsonNode properties = catalog.templates().get(0).parameterSchema().path("properties");
    var fields = properties.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> entry = fields.next();
      JsonNode defaultValue = entry.getValue().path("default");
      if (!defaultValue.isMissingNode()) {
        data.set(entry.getKey(), defaultValue);
      } else {
        data.put(entry.getKey(), "");
      }
    }
  }

  private void addTemplateVisibleOn(ObjectNode item, ProjectTemplateSummary template) {
    String templateCondition = "template"
        + " && template.templateId === " + jsonLiteral(NODES.textNode(template.templateId()))
        + " && template.templateVersion === " + jsonLiteral(NODES.textNode(template.templateVersion()))
        + " && template.compatibilityRowId === " + jsonLiteral(NODES.textNode(template.compatibilityRowId()));
    String itemCondition = unwrapExpression(item.path("visibleOn").asText(null));
    String condition = itemCondition == null
        ? templateCondition
        : templateCondition + " && (" + itemCondition + ")";
    item.put("visibleOn", "${" + condition + "}");
  }

  private ObjectNode formItem(String name, JsonNode property, JsonNode hint, boolean required) {
    String type = property.path("type").asText();
    String control = hint.path("control").asText(defaultControl(property));
    ObjectNode item = object("type", amisControl(control, property));
    item.put("id", "project-generator-" + name);
    item.put("name", name);
    item.put("label", property.path("title").asText(name));
    item.put("required", required);
    item.put("clearable", !required);
    putText(item, "description", description(property, hint));
    putText(item, "placeholder", hint.path("placeholder").asText(null));
    if (!property.path("default").isMissingNode()) {
      item.set("value", property.path("default"));
    }
    if (isSelectionControl(control)) {
      item.set("options", enumOptions(property.path("enum"), hint));
      item.put("clearable", !required);
    }
    if ("integer".equals(type)) {
      putNumber(item, "min", property.path("minimum"));
      putNumber(item, "max", property.path("maximum"));
    }
    if ("string".equals(type)) {
      putNumber(item, "minLength", property.path("minLength"));
      putNumber(item, "maxLength", property.path("maxLength"));
    }
    putVisibleOn(item, hint.path("visibleWhen"));
    return item;
  }

  private String defaultControl(JsonNode property) {
    if (property.path("enum").isArray()) {
      return "select";
    }
    return switch (property.path("type").asText()) {
      case "boolean" -> "toggle";
      case "integer" -> "number";
      default -> "text";
    };
  }

  private String amisControl(String control, JsonNode property) {
    return switch (control) {
      case "select" -> "select";
      case "list-select" -> "list-select";
      case "button-group-select" -> "button-group-select";
      case "toggle" -> "switch";
      case "number" -> "input-number";
      default -> property.path("enum").isArray() ? "select" : "input-text";
    };
  }

  private static boolean isSelectionControl(String control) {
    return Set.of("select", "list-select", "button-group-select").contains(control);
  }

  private ArrayNode enumOptions(JsonNode enumValues, JsonNode hint) {
    ArrayNode options = NODES.arrayNode();
    if (!enumValues.isArray()) {
      return options;
    }
    for (JsonNode enumValue : enumValues) {
      ObjectNode option = NODES.objectNode();
      String value = enumValue.asText();
      JsonNode label = hint.path("optionLabels").path(value);
      option.set("label", label.isTextual() ? label : enumValue);
      option.set("value", enumValue);
      JsonNode visibleOn = hint.path("optionVisibleWhen").path(value);
      if (visibleOn.isTextual() && !visibleOn.asText().isBlank()) {
        option.put("visibleOn", visibleOn.asText());
      }
      options.add(option);
    }
    return options;
  }

  private String description(JsonNode property, JsonNode hint) {
    String help = hint.path("help").asText(null);
    if (help != null && !help.isBlank()) {
      return help;
    }
    String description = property.path("description").asText(null);
    return description == null || description.isBlank() ? null : description;
  }

  private void putVisibleOn(ObjectNode item, JsonNode visibleWhen) {
    if (!visibleWhen.isObject()) {
      return;
    }
    String parameter = visibleWhen.path("parameter").asText();
    if (visibleWhen.has("equals")) {
      item.put("visibleOn", "${" + parameter + " === " + jsonLiteral(visibleWhen.path("equals")) + "}");
      return;
    }
    if (visibleWhen.path("in").isArray()) {
      item.put("visibleOn", "${" + jsonLiteral(visibleWhen.path("in")) + ".indexOf(" + parameter + ") !== -1}");
    }
  }

  private String unwrapExpression(String expression) {
    if (expression == null || expression.isBlank()) {
      return null;
    }
    String trimmed = expression.trim();
    if (trimmed.startsWith("${") && trimmed.endsWith("}")) {
      return trimmed.substring(2, trimmed.length() - 1);
    }
    return trimmed;
  }

  private Set<String> required(JsonNode schema) {
    JsonNode required = schema.path("required");
    if (!required.isArray()) {
      return Set.of();
    }
    Set<String> result = new HashSet<>();
    for (JsonNode item : required) {
      result.add(item.asText());
    }
    return result;
  }

  private int orderOf(JsonNode hint) {
    return hint.path("order").isInt() ? hint.path("order").asInt() : Integer.MAX_VALUE;
  }

  private void putText(ObjectNode node, String field, String value) {
    if (value != null && !value.isBlank()) {
      node.put(field, value);
    }
  }

  private void putNumber(ObjectNode node, String field, JsonNode value) {
    if (value.isIntegralNumber()) {
      node.put(field, value.asLong());
    }
  }

  private String jsonLiteral(JsonNode value) {
    try {
      return JSON.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException("Cannot serialize project generator AMIS expression value", e);
    }
  }

  private ObjectNode alert(String level, String body) {
    ObjectNode alert = object("type", "alert");
    alert.put("level", level);
    alert.put("body", body);
    return alert;
  }

  private ObjectNode object(String firstKey, String firstValue) {
    ObjectNode object = NODES.objectNode();
    object.put(firstKey, firstValue);
    return object;
  }

  private record ParameterEntry(int index, String name, JsonNode property) {
  }
}
