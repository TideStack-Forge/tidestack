package com.ouroboros.widget;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.ObjectUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.util.ResourceUtil;

public class SimpleWidgetDefinitionGenerator implements WidgetDefinitionGenerator {
  private final static String FORM_ITEMS_RESOURCE_PATH = "META-INF/ouroboros/widget-definition-generator-form-items/Simple.json";
  private final static List<SchemaNode> formItems;
  static {
    formItems = ResourceUtil
        .getResourceContentStream(FORM_ITEMS_RESOURCE_PATH)
        .findFirst()
        .map(s -> Json.toList(new TypeReference<List<SchemaNode>>() {}, s))
        .orElse(Collections.emptyList());
  }
  @Override
  public String getName() {
    return "simple";
  }

  @Override
  public String getLabel() {
    return "基本小组件构造器";
  }

  @Override
  public String getDescription() {
    return "简单组件定义生成器";
  }

  @Override
  public List<SchemaNode> getFormItems() {
    return Collections.unmodifiableList(formItems);
  }

  @Override
  public WidgetDefinition buildWidgetDefinition(WidgetSchema metadata) {
    var widgetDefinition = new WidgetDefinition();
    widgetDefinition.setName(metadata.getName())
        .setTitle(metadata.getTitle())
        .setDescription(metadata.getDescription())
        .setCategory(metadata.getCategory())
        .setScene(metadata.getScene())
        .setAllowHeader(metadata.getAllowHeader())
        .setAllowFooter(metadata.getAllowFooter())
        .setSizeConstraints(metadata.getSizeConstraints())
        .setDefaultSize(metadata.getDefaultSize())
        .setDefaultConfig(getDefaultConfig(metadata))
        .setDefaultStyleConfig(getDefaultStyleConfig(metadata))
        .setConfigFormItems(getConfigFormItems(metadata))
        .setThumbSchema(getThumbSchema(metadata))
        .setSchemaTpl(getSchemaTpl(metadata))
        .setExampleData(getExampleData(metadata))
        .setConfigDialogSize(metadata.getConfigDialogSize() != null ? metadata.getConfigDialogSize() : "md");
    return widgetDefinition;
  }

  @SuppressWarnings("unchecked")
  private WidgetConfig getDefaultConfig(WidgetSchema metadata) {
    var widgetConfig = new WidgetConfig();
    if (metadata.getAllowHeader()) {
      widgetConfig.setTitle(metadata.getTitle());
    }
    var defaultConfigMap = (Map<String, Object>) Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("defaultConfig"))
        .filter(Map.class::isInstance).orElse(null);
    if (ObjectUtils.isEmpty(defaultConfigMap)) {
      return widgetConfig;
    }
    defaultConfigMap.forEach((k, v) -> {
      if ("title".equals(k)) {
        if (v == null) {
          return;
        }
        widgetConfig.setTitle(v.toString());
      }
      if ("header".equals(k)) {
        widgetConfig.setHeader(v);
      }
      if ("showHeader".equals(k)) {
        if (v == null) {
          return;
        }
        widgetConfig.setShowHeader(Boolean.parseBoolean(v.toString()));
      }
      if ("showFooter".equals(k)) {
        if (v == null) {
          return;
        }
        widgetConfig.setShowFooter(Boolean.parseBoolean(v.toString()));
      }
      widgetConfig.setExtraProp(k, v);
    });
    return widgetConfig;
  }

  private List<SchemaNode> getConfigFormItems(WidgetSchema metadata) {
    return Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("configFormItems"))
        .filter(List.class::isInstance)
        .map(list -> ((List<?>) list).stream())
        .orElseGet(Stream::empty)
        .map(item -> item instanceof Map<?, ?> map
            ? new SchemaNode((Map<String, ?>) map)
            : item instanceof SchemaNode schemaNode
            ? schemaNode
            : null)
        .filter(Objects::nonNull)
        .collect(Collectors.toList());
  }

  @SuppressWarnings("unchecked")
  private SchemaNode getThumbSchema(WidgetSchema metadata) {
    return Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("thumbSchema"))
        .filter(ObjectUtils::isNotEmpty)
        .map(s -> {
          if (s instanceof SchemaNode schema) {
            return schema;
          }
          if (s instanceof Map<?, ?> map) {
            return new SchemaNode((Map<String, ?>) map);
          }
          return null;
        })
        .orElse(null);
  }

  private Object getSchemaTpl(WidgetSchema metadata) {
    return Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("schemaTpl"))
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getDefaultStyleConfig(WidgetSchema metadata) {
    return (Map<String, Object>) Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("defaultStyleConfig"))
        .filter(Map.class::isInstance)
        .orElse(null);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> getExampleData(WidgetSchema metadata) {
    return (Map<String, Object>) Optional.ofNullable(metadata.getConfig())
        .map(m -> m.get("exampleData"))
        .filter(Map.class::isInstance)
        .orElse(null);
  }
}
