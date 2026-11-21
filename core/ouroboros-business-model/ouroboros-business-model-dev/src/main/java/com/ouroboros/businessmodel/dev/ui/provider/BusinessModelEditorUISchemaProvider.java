package com.ouroboros.businessmodel.dev.ui.provider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ouroboros.businessmodel.plugin.BusinessModelFeaturePlugin;
import com.ouroboros.businessmodel.plugin.BusinessModelFeaturePluginRegistry;
import com.ouroboros.businessmodel.schema.EditorSchemaProvider;
import com.ouroboros.businessmodel.schema.PluginEditorSchemaContributor;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.uischema.schema.TplNode;
import com.ouroboros.util.SPI;

public class BusinessModelEditorUISchemaProvider implements UISchemaProvider {
  private static final Logger logger = LoggerFactory.getLogger(BusinessModelEditorUISchemaProvider.class);
  private static final String MODEL_SCHEMA_PATH = "/dev/{appName}/business-model/editor";

  private final List<EditorSchemaProvider> editorSchemaProviders;
  private final List<PluginEditorSchemaContributor> spiPluginEditorSchemaContributors;
  private final BusinessModelFeaturePluginRegistry pluginRegistry;

  public BusinessModelEditorUISchemaProvider() {
    this.editorSchemaProviders = SPI.getCachedReversedServiceStream(EditorSchemaProvider.class)
        .collect(Collectors.toList());
    this.spiPluginEditorSchemaContributors = SPI.getCachedServiceStream(PluginEditorSchemaContributor.class)
        .sorted(Comparator.comparingInt(PluginEditorSchemaContributor::getOrder))
        .collect(Collectors.toList());
    this.pluginRegistry = BusinessModelFeaturePluginRegistry.getInstance();
  }

  @Override
  public Optional<Object> getUISchema(String path) {
    if (!path.equals(MODEL_SCHEMA_PATH)) {
      return Optional.empty();
    }
    return Optional.ofNullable(generateEditorSchema(Collections.emptyMap()));
  }

  @Override
  public Optional<Object> getUISchema(String path, Map<String, Object> context) {
    if (!path.equals(MODEL_SCHEMA_PATH)) {
      return Optional.empty();
    }
    return Optional.ofNullable(generateEditorSchema(context));
  }

  @Override
  public Map<String, Object> getUISchemaMap() {
    return Collections.singletonMap(MODEL_SCHEMA_PATH, generateEditorSchema(Collections.emptyMap()));
  }

  private SchemaNode generateEditorSchema(Map<String, Object> context) {
    Map<String, Object> finalContext = buildContext(context);
    String modelType = resolveModelType(finalContext);
    EditorSchemaProvider provider = resolveProvider(modelType).orElse(null);
    if (provider == null) {
      logger.warn("No EditorSchemaProvider found for modelType={} and no default fallback provider is available.", modelType);
      return null;
    }

    SchemaNode baseSchema = provider.generateBaseSchema(finalContext);
    if (baseSchema == null) {
      return null;
    }

    pluginEditorSchemaContributors(modelType).forEach(contributor -> mergeSchemaFragment(baseSchema, contributor.contributeEditorFragment(finalContext)));
    return resolveConcreteAppName(baseSchema, finalContext);
  }

  private SchemaNode resolveConcreteAppName(SchemaNode schema, Map<String, Object> context) {
    Object appNameValue = context.get("appName");
    if (!(appNameValue instanceof String appName) || StringUtils.isBlank(appName) || "${appName}".equals(appName)) {
      return schema;
    }
    resolveAppNamePlaceholders(schema, appName);
    return schema;
  }

  private Object resolveAppNamePlaceholders(Object value, String appName) {
    if (value instanceof SchemaNode node) {
      Map<String, Object> resolvedProps = new LinkedHashMap<>();
      node.getProps().forEach((key, propValue) -> resolvedProps.put(key, resolveAppNamePlaceholders(propValue, appName)));
      node.getProps().clear();
      node.putProps(resolvedProps);
      return node;
    }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> resolvedMap = new LinkedHashMap<>();
      map.forEach((key, mapValue) -> resolvedMap.put(String.valueOf(key), resolveAppNamePlaceholders(mapValue, appName)));
      return resolvedMap;
    }
    if (value instanceof List<?> list) {
      return list.stream()
          .map(item -> resolveAppNamePlaceholders(item, appName))
          .collect(Collectors.toList());
    }
    if (value instanceof String text) {
      return text.replace("${appName}", appName);
    }
    return value;
  }

  private List<PluginEditorSchemaContributor> pluginEditorSchemaContributors(String modelType) {
    Map<String, PluginEditorSchemaContributor> contributors = new LinkedHashMap<>();
    pluginRegistry.getPluginsForModelType(modelType).stream()
        .map(BusinessModelFeaturePlugin::getEditorContributor)
        .filter(Objects::nonNull)
        .forEach(contributor -> contributors.putIfAbsent(contributor.getClass().getName(), contributor));
    spiPluginEditorSchemaContributors.stream()
        .filter(contributor -> contributors.containsKey(contributor.getClass().getName()))
        .forEach(contributor -> contributors.put(contributor.getClass().getName(), contributor));
    return contributors.values().stream()
        .sorted(Comparator.comparingInt(PluginEditorSchemaContributor::getOrder))
        .collect(Collectors.toList());
  }

  private Map<String, Object> buildContext(Map<String, Object> context) {
    Map<String, Object> finalContext = new LinkedHashMap<>();
    if (context != null) {
      finalContext.putAll(context);
    }
    finalContext.putIfAbsent("appName", "${appName}");
    finalContext.putIfAbsent("modelType", com.ouroboros.businessmodel.BusinessModelTypes.DEFAULT);
    return finalContext;
  }

  private String resolveModelType(Map<String, Object> context) {
    Object modelType = context.get("modelType");
    if (modelType instanceof String && StringUtils.isNotBlank((String) modelType)) {
      return (String) modelType;
    }
    return com.ouroboros.businessmodel.BusinessModelTypes.DEFAULT;
  }

  private Optional<EditorSchemaProvider> resolveProvider(String modelType) {
    Optional<EditorSchemaProvider> matchedProvider = editorSchemaProviders.stream()
        .filter(provider -> provider.support(modelType))
        .findFirst();
    if (matchedProvider.isPresent()) {
      return matchedProvider;
    }

    Optional<EditorSchemaProvider> fallbackProvider = editorSchemaProviders.stream()
        .filter(provider -> provider.support(com.ouroboros.businessmodel.BusinessModelTypes.DEFAULT))
        .findFirst();
    fallbackProvider.ifPresent(provider -> logger.warn("No EditorSchemaProvider matched modelType={}, falling back to default provider {}.", modelType, provider.getClass().getName()));
    return fallbackProvider;
  }

  private void mergeSchemaFragment(SchemaNode baseSchema, SchemaNode fragment) {
    if (baseSchema == null || fragment == null) {
      return;
    }

    Map<String, Object> fragmentProps = new LinkedHashMap<>(fragment.getProps());
    Object fragmentBody = fragmentProps.remove("body");
    // Plugin fragments use a generic root container as a body carrier. Its type
    // must not replace the editor shell root type, otherwise the wrapper/flex
    // contract that keeps tab scrolling inside the panel is broken.
    fragmentProps.remove("type");
    if (!fragmentProps.isEmpty()) {
      baseSchema.putProps(fragmentProps);
    }
    mergeBody(baseSchema, fragmentBody);
  }

  private void mergeBody(SchemaNode baseSchema, Object fragmentBody) {
    List<SchemaNode> fragmentNodes = toSchemaNodes(fragmentBody);
    if (fragmentNodes.isEmpty()) {
      return;
    }

    if (mergeIntoFeaturesTab(baseSchema, fragmentNodes)) {
      return;
    }

    List<SchemaNode> mergedBody = toSchemaNodes(baseSchema.getProp("body"));
    mergedBody.addAll(fragmentNodes);
    baseSchema.setProp("body", mergedBody);
  }

  @SuppressWarnings("unchecked")
  private boolean mergeIntoFeaturesTab(SchemaNode baseSchema, List<SchemaNode> fragmentNodes) {
    List<SchemaNode> rootBody = toSchemaNodes(baseSchema.getProp("body"));
    for (SchemaNode rootNode : rootBody) {
      if (!"tabs".equals(rootNode.getType())) {
        continue;
      }
      Object rawTabs = rootNode.getProp("tabs");
      if (!(rawTabs instanceof Iterable)) {
        continue;
      }
      for (Object rawTab : (Iterable<?>) rawTabs) {
        SchemaNode tabNode;
        if (rawTab instanceof SchemaNode) {
          tabNode = (SchemaNode) rawTab;
        } else if (rawTab instanceof Map) {
          tabNode = new SchemaNode((Map<String, Object>) rawTab);
        } else {
          continue;
        }
        if (!"features".equals(String.valueOf(tabNode.getProp("hash")))) {
          continue;
        }
        List<SchemaNode> tabBody = toSchemaNodes(tabNode.getProp("body"));
        tabBody.addAll(fragmentNodes);
        tabNode.setProp("body", tabBody);
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private List<SchemaNode> toSchemaNodes(Object body) {
    List<SchemaNode> nodes = new ArrayList<>();
    if (body == null) {
      return nodes;
    }
    if (body instanceof SchemaNode) {
      nodes.add((SchemaNode) body);
      return nodes;
    }
    if (body instanceof Map) {
      nodes.add(new SchemaNode((Map<String, Object>) body));
      return nodes;
    }
    if (body instanceof Iterable) {
      for (Object item : (Iterable<?>) body) {
        nodes.addAll(toSchemaNodes(item));
      }
      return nodes;
    }
    nodes.add(new TplNode(body.toString()));
    return nodes;
  }
}
