package com.ouroboros.i18n.dev.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.vavr.control.Try;

import com.ouroboros.dev.metadata.Metadata;
import com.ouroboros.dev.metadata.MetadataProvider;
import com.ouroboros.dev.metadata.MetadataProviderMixer;
import com.ouroboros.exception.BusinessException;
import com.ouroboros.i18n.dev.dto.I18nLocaleExportResult;
import com.ouroboros.i18n.dev.dto.I18nLocaleSyncResult;
import com.ouroboros.i18n.dev.model.I18nLocaleModel;
import com.ouroboros.i18n.model.TextDirection;
import com.ouroboros.modelmanage.annotation.DevelopModule;
import com.ouroboros.modelmanage.base.BaseDevelopService;
import com.ouroboros.util.Json;
import com.ouroboros.vcs.file.VCSFile;
import com.ouroboros.vcs.wrapper.ModelFileMetaWrapper;

@Service
@DevelopModule("i18n")
public class I18nLocaleDevService extends BaseDevelopService<I18nLocaleModel> {
  static final String MODEL_FILE_DIRECTORY = "i18n";
  static final String MODEL_FILE_EXTENSION = ".i18n.json";
  private static final String CONTENT_TYPE_JSON = "application/json";
  private static final String TEMPLATE_FILENAME = "translation-template.json";
  private static final String TEMPLATE_LOCALE = "template";
  private static final int MAX_SCHEMA_DEPTH = 20;

  private static final String[] UI_MODEL_TEXT_PROPERTIES = {
      "title", "description"
  };

  private static final String[] UI_SCHEMA_TEXT_PROPERTIES = {
      "label", "title", "placeholder", "description", "tpl", "content", "msg",
      "tooltip", "remark", "disabledTip", "popOver",
      "success", "failed",
      "*Failed", "*Success", "*Text", "*Label", "*Msg"
  };

  private static final String[] UI_SCHEMA_WILDCARD_PROPERTIES = {
      "definitions.*", "messages.*", "validationErrors.*"
  };

  private static final String[] UI_SCHEMA_BLACKLIST = {
      "data", "value", "defaultValue", "source", "reload", "redirect",
      "className", "style", "inputClassName", "labelClassName", "staticClassName",
      "visibleOn", "hiddenOn", "disabledOn", "requiredOn", "staticOn",
      "required", "disabled", "hidden", "static", "id", "localeKey", "localeKeys",
      "localePath", "name", "type"
  };

  private MetadataProviderMixer metadataProviderMixer = MetadataProviderMixer.getDistinctMixer(Collections.emptyList());

  @Autowired(required = false)
  public void setMetadataProviders(List<MetadataProvider> metadataProviders) {
    metadataProviderMixer = MetadataProviderMixer.getDistinctMixer(
        metadataProviders == null ? Collections.emptyList() : metadataProviders
    );
  }

  @Override
  protected String getModelFileDirectory() {
    return MODEL_FILE_DIRECTORY;
  }

  @Override
  protected String getModelFileExtension() {
    return MODEL_FILE_EXTENSION;
  }

  @Override
  protected boolean isAutoCommitWhenUpdate() {
    return false;
  }

  @Override
  protected String getFullName(I18nLocaleModel model) {
    return normalize(model == null ? null : model.getLocale());
  }

  @Override
  protected String getModelDisplayName() {
    return "多语言文件";
  }

  void setMetadataProvidersForTest(List<MetadataProvider> metadataProviders) {
    setMetadataProviders(metadataProviders);
  }

  @Override
  protected Try<Void> validateModel(I18nLocaleModel model) {
    if (model == null) {
      return Try.failure(new BusinessException("多语言文件不能为空"));
    }
    if (StringUtils.isBlank(model.getLocale())) {
      return Try.failure(new BusinessException("语言代码不能为空"));
    }
    if (StringUtils.containsAny(model.getLocale(), '/', '\\')) {
      return Try.failure(new BusinessException("语言代码不能包含路径分隔符"));
    }
    if (StringUtils.isBlank(model.getName())) {
      return Try.failure(new BusinessException("语言名称不能为空"));
    }
    return Try.success(null);
  }

  @Override
  protected Try<String> getPermanentJson(I18nLocaleModel model) {
    Map<String, Object> payload = new LinkedHashMap<>();
    payload.put("locale", normalize(model.getLocale()));
    payload.put("name", StringUtils.trimToEmpty(model.getName()));
    payload.put("version", StringUtils.defaultIfBlank(StringUtils.trim(model.getVersion()), "1.0.0"));
    payload.put("enabled", model.getEnabled() == null ? Boolean.TRUE : model.getEnabled());
    payload.put("isDefault", model.getIsDefault() == null ? Boolean.FALSE : model.getIsDefault());
    payload.put("messages", model.getMessages() == null ? new LinkedHashMap<>() : model.getMessages());
    payload.put("timestamp", System.currentTimeMillis());
    payload.put("direction", model.getDirection().name());
    return Json.tryToPrettyJsonString(payload);
  }

  public Try<I18nLocaleExportResult> exportTemplate() {
    return extractTemplate(TEMPLATE_LOCALE)
        .flatMap(model -> Json.tryToPrettyJsonString(model)
            .map(content -> new I18nLocaleExportResult(TEMPLATE_FILENAME, content, CONTENT_TYPE_JSON)));
  }

  public Try<I18nLocaleExportResult> exportLocale(String locale) {
    return Try.of(() -> getOne(locale)
            .orElseThrow(() -> new BusinessException("语言文件不存在: " + locale)))
        .flatMap(model -> Json.tryToPrettyJsonString(model)
            .map(content -> new I18nLocaleExportResult(locale + MODEL_FILE_EXTENSION, content, CONTENT_TYPE_JSON)));
  }

  public Try<I18nLocaleModel> importLocale(I18nLocaleModel imported) {
    var validateResult = validateModel(imported);
    if (validateResult.isFailure()) {
      return Try.failure(validateResult.getCause());
    }

    String locale = normalize(imported.getLocale());
    Optional<I18nLocaleModel> existing = getOne(locale);
    I18nLocaleModel merged = new I18nLocaleModel()
        .setLocale(locale)
        .setName(StringUtils.trimToEmpty(imported.getName()))
        .setVersion(StringUtils.defaultIfBlank(StringUtils.trim(imported.getVersion()), "1.0.0"))
        .setMessages(copyMessages(imported.getMessages()))
        .setDirection(Optional.ofNullable(imported.getDirection()).orElse(TextDirection.LTR))
        .setTimestamp(System.currentTimeMillis());

    if (existing.isPresent()) {
      merged.setEnabled(existing.get().getEnabled());
      merged.setIsDefault(existing.get().getIsDefault());
      return update(locale, merged);
    }

    merged.setEnabled(imported.getEnabled() == null ? Boolean.TRUE : imported.getEnabled());
    merged.setIsDefault(Boolean.FALSE);
    return create(merged, "");
  }

  public Try<I18nLocaleSyncResult> syncFromMetadata(String locale, boolean removeOrphaned) {
    return extractTemplate(locale).flatMap(template -> {
      Optional<I18nLocaleModel> existing = getOne(locale);
      Map<String, Map<String, String>> mergedMessages = existing
          .map(model -> copyMessages(model.getMessages()))
          .orElse(new LinkedHashMap<>());

      int removedCount = 0;
      if (removeOrphaned && existing.isPresent()) {
        removedCount = removeOrphanedEntries(mergedMessages, template.getMessages());
      }

      int addedCount = addMissingEntries(mergedMessages, template.getMessages());
      final int finalAddedCount = addedCount;
      final int finalRemovedCount = removedCount;
      I18nLocaleModel mergedModel = existing
          .map(model -> new I18nLocaleModel()
              .setLocale(model.getLocale())
              .setName(model.getName())
              .setVersion(model.getVersion())
              .setEnabled(model.getEnabled())
              .setIsDefault(model.getIsDefault())
              .setDirection(model.getDirection())
              .setTimestamp(System.currentTimeMillis())
              .setMessages(mergedMessages))
          .orElse(new I18nLocaleModel()
              .setLocale(normalize(locale))
              .setName(normalize(locale))
              .setVersion("1.0.0")
              .setEnabled(Boolean.TRUE)
              .setIsDefault(Boolean.FALSE)
              .setDirection(TextDirection.LTR)
              .setTimestamp(System.currentTimeMillis())
              .setMessages(mergedMessages));

      Try<I18nLocaleModel> persistence = existing.isPresent()
          ? update(locale, mergedModel)
          : create(mergedModel, "");
      return persistence.map(ignore -> new I18nLocaleSyncResult(finalAddedCount, finalRemovedCount));
    });
  }

  @Override
  protected Function<VCSFile, Try<?>> buildSearchResultMapper() {
    return file -> buildModelFromFileContent(file.getContent())
        .map(model -> new ModelFileMetaWrapper<>(model, file));
  }

  @Override
  protected Predicate<ModelFileMetaWrapper<I18nLocaleModel>> buildSearchFilter(Map<String, Object> searchParam) {
    String keyword = StringUtils.trimToEmpty(String.valueOf(searchParam.getOrDefault("keyword", ""))).toLowerCase();
    return wrapper -> {
      if (StringUtils.isBlank(keyword)) {
        return true;
      }

      I18nLocaleModel model = wrapper.getModel();
      return contains(model.getLocale(), keyword)
          || contains(model.getName(), keyword)
          || contains(model.getVersion(), keyword)
          || contains(model.getDirection().name(), keyword)
          || containsMessages(model.getMessages(), keyword);
    };
  }

  private static boolean contains(String value, String keyword) {
    return value != null && value.toLowerCase().contains(keyword);
  }

  private static boolean containsMessages(Map<String, Map<String, String>> messages, String keyword) {
    if (messages == null || messages.isEmpty()) {
      return false;
    }

    for (Map.Entry<String, Map<String, String>> namespaceEntry : messages.entrySet()) {
      if (contains(namespaceEntry.getKey(), keyword)) {
        return true;
      }
      Map<String, String> entries = namespaceEntry.getValue();
      if (entries == null) {
        continue;
      }
      for (Map.Entry<String, String> entry : entries.entrySet()) {
        if (contains(entry.getKey(), keyword) || contains(entry.getValue(), keyword)) {
          return true;
        }
      }
    }
    return false;
  }

  private static String normalize(String value) {
    return StringUtils.trimToEmpty(value);
  }

  private Try<I18nLocaleModel> extractTemplate(String locale) {
    return Try.of(() -> {
      Map<String, Map<String, String>> messages = new LinkedHashMap<>();
      extractUIModelTexts(messages);
      extractUISchemaTexts(messages);

      return new I18nLocaleModel()
          .setLocale(normalize(locale))
          .setName(normalize(locale))
          .setVersion("1.0.0")
          .setEnabled(Boolean.TRUE)
          .setIsDefault(Boolean.FALSE)
          .setDirection(TextDirection.LTR)
          .setTimestamp(System.currentTimeMillis())
          .setMessages(messages);
    });
  }

  private void extractUIModelTexts(Map<String, Map<String, String>> messages) {
    for (Metadata metadata : metadataProviderMixer.getAppAllMetadataByModule(getAppName(), "ui-model")) {
      Map<String, Object> details = metadata.getDetails();
      if (details == null) {
        continue;
      }
      String namespace = metadata.getName();
      if (StringUtils.isBlank(namespace)) {
        continue;
      }
      for (String property : UI_MODEL_TEXT_PROPERTIES) {
        Object value = details.get(property);
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
          messages.computeIfAbsent(namespace, ignore -> new LinkedHashMap<>())
              .put(property, (String) value);
        }
      }
    }
  }

  private void extractUISchemaTexts(Map<String, Map<String, String>> messages) {
    for (Metadata metadata : metadataProviderMixer.getAppAllMetadataByModule(getAppName(), "ui-schema")) {
      if (StringUtils.isBlank(metadata.getName()) || metadata.getDetails() == null) {
        continue;
      }
      extractSchemaNode(metadata.getName(), metadata.getDetails(), "", messages, 0);
    }
  }

  private void extractSchemaNode(String namespace, Map<String, Object> node, String path,
                                 Map<String, Map<String, String>> messages, int depth) {
    if (depth > MAX_SCHEMA_DEPTH || node == null) {
      return;
    }

    String keyPrefix = buildSchemaKeyPrefix(node, path);
    extractSchemaTextProperties(namespace, node, keyPrefix, messages);
    extractSchemaOnEvent(namespace, node, keyPrefix, messages);
    processSchemaChildProperties(namespace, node, keyPrefix, messages, depth);
  }

  private String buildSchemaKeyPrefix(Map<String, Object> node, String path) {
    String currentPath = path;
    if (StringUtils.isBlank(currentPath)) {
      currentPath = getString(node, "type");
      if (StringUtils.isBlank(currentPath)) {
        currentPath = "root";
      }
    }

    String localeKey = getString(node, "localeKey");
    return StringUtils.isNotBlank(localeKey) ? localeKey : currentPath;
  }

  private void extractSchemaTextProperties(String namespace, Map<String, Object> node, String keyPrefix,
                                           Map<String, Map<String, String>> messages) {
    String localeKey = getString(node, "localeKey");

    for (String property : UI_SCHEMA_TEXT_PROPERTIES) {
      if (property.startsWith("*")) {
        extractSchemaWildcardTextProperties(namespace, node, keyPrefix, localeKey, property.substring(1), messages);
      } else {
        extractSchemaSingleTextProperty(namespace, node, keyPrefix, localeKey, property, messages);
      }
    }
  }

  private void extractSchemaSingleTextProperty(String namespace, Map<String, Object> node, String keyPrefix,
                                               String localeKey, String property,
                                               Map<String, Map<String, String>> messages) {
    Object value = node.get(property);
    if (value instanceof String && StringUtils.isNotBlank((String) value)) {
      String finalKey = Optional.ofNullable(getExplicitSchemaLocaleKey(node, property))
          .orElseGet(() -> StringUtils.isNotBlank(localeKey) ? localeKey + "." + property : keyPrefix + "." + property);
      putMessage(messages, namespace, finalKey, (String) value);
      return;
    }

    Map<String, Object> childNode = toMap(value);
    if (childNode != null) {
      String childPath = StringUtils.isBlank(keyPrefix) ? property : keyPrefix + "." + property;
      extractSchemaNode(namespace, childNode, childPath, messages, 0);
    }
  }

  private void extractSchemaWildcardTextProperties(String namespace, Map<String, Object> node, String keyPrefix,
                                                   String localeKey, String pattern,
                                                   Map<String, Map<String, String>> messages) {
    String regex = wildcardToRegex(pattern);
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      String property = entry.getKey();
      Object value = entry.getValue();
      if (!property.matches(regex)) {
        continue;
      }

      if (value instanceof String && StringUtils.isNotBlank((String) value)) {
        String finalKey = Optional.ofNullable(getExplicitSchemaLocaleKey(node, property))
            .orElseGet(() -> StringUtils.isNotBlank(localeKey) ? localeKey + "." + property : keyPrefix + "." + property);
        putMessage(messages, namespace, finalKey, (String) value);
        continue;
      }

      Map<String, Object> childNode = toMap(value);
      if (childNode != null) {
        String childPath = StringUtils.isBlank(keyPrefix) ? property : keyPrefix + "." + property;
        extractSchemaNode(namespace, childNode, childPath, messages, 0);
      }
    }
  }

  private void extractSchemaOnEvent(String namespace, Map<String, Object> node, String keyPrefix,
                                    Map<String, Map<String, String>> messages) {
    Object onEvent = node.get("onEvent");
    if (!(onEvent instanceof Map<?, ?>)) {
      return;
    }

    for (Map.Entry<?, ?> eventEntry : ((Map<?, ?>) onEvent).entrySet()) {
      Object eventConfig = eventEntry.getValue();
      if (!(eventConfig instanceof Map<?, ?>)) {
        continue;
      }
      Object actions = ((Map<?, ?>) eventConfig).get("actions");
      if (!(actions instanceof List<?>)) {
        continue;
      }

      List<?> actionList = (List<?>) actions;
      for (int i = 0; i < actionList.size(); i++) {
        Object action = actionList.get(i);
        if (!(action instanceof Map<?, ?>)) {
          continue;
        }
        String actionType = String.valueOf(((Map<?, ?>) action).get("actionType"));
        String property = null;
        if (Objects.equals("toast", actionType)) {
          property = "msg";
        } else if (Objects.equals("dialog", actionType) || Objects.equals("drawer", actionType)) {
          property = "title";
        }

        if (property == null) {
          continue;
        }
        Object value = ((Map<?, ?>) action).get(property);
        if (value instanceof String && StringUtils.isNotBlank((String) value)) {
          String key = keyPrefix + ".onEvent." + eventEntry.getKey() + "." + i + "." + property;
          putMessage(messages, namespace, key, (String) value);
        }
      }
    }
  }

  private void processSchemaChildProperties(String namespace, Map<String, Object> node, String keyPrefix,
                                            Map<String, Map<String, String>> messages, int depth) {
    for (Map.Entry<String, Object> entry : node.entrySet()) {
      String property = entry.getKey();
      Object value = entry.getValue();

      if (isSchemaBlacklisted(property) || isSchemaTextProperty(property)) {
        continue;
      }

      if (isSchemaWildcardProperty(property)) {
        if (value instanceof Map<?, ?>) {
          processSchemaWildcardMap(namespace, node, (Map<?, ?>) value, property, keyPrefix + "." + property, messages, depth);
        }
        continue;
      }

      String childPath = StringUtils.isBlank(keyPrefix) ? property : keyPrefix + "." + property;
      String explicitKey = getExplicitSchemaLocaleKey(node, property);
      if (value instanceof String && StringUtils.isNotBlank((String) value) && explicitKey != null) {
        putMessage(messages, namespace, explicitKey, (String) value);
        continue;
      }

      if (value instanceof List<?>) {
        processSchemaChildList(namespace, (List<?>) value, childPath, property, messages, depth);
        continue;
      }

      Map<String, Object> childNode = toMap(value);
      if (childNode != null) {
        extractSchemaNode(namespace, childNode, childPath, messages, depth + 1);
      }
    }
  }

  private void processSchemaChildList(String namespace, List<?> children, String path, String propertyName,
                                      Map<String, Map<String, String>> messages, int depth) {
    Map<String, Integer> typeCount = new LinkedHashMap<>();
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<Integer> indices = new ArrayList<>();

    for (int i = 0; i < children.size(); i++) {
      Map<String, Object> childNode = toMap(children.get(i));
      if (childNode == null) {
        continue;
      }
      nodes.add(childNode);
      indices.add(i);
      String type = getString(childNode, "type");
      if (StringUtils.isNotBlank(type)) {
        typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
      }
    }

    Map<String, Integer> usedCount = new LinkedHashMap<>();
    for (int i = 0; i < nodes.size(); i++) {
      Map<String, Object> childNode = nodes.get(i);
      int originalIndex = indices.get(i);
      String nodeId = getSchemaNodeIdentifier(childNode, originalIndex, typeCount);
      int duplicateCount = usedCount.getOrDefault(nodeId, 0);
      usedCount.put(nodeId, duplicateCount + 1);

      String finalNodeId = duplicateCount > 0 ? nodeId + "[" + duplicateCount + "]" : nodeId;
      String nodePath = buildSchemaNodePath(path, finalNodeId, propertyName, originalIndex);
      extractSchemaNode(namespace, childNode, nodePath, messages, depth + 1);
    }
  }

  private void processSchemaWildcardMap(String namespace, Map<String, Object> parentNode, Map<?, ?> map,
                                        String property, String basePath,
                                        Map<String, Map<String, String>> messages, int depth) {
    if (depth > MAX_SCHEMA_DEPTH) {
      return;
    }

    for (Map.Entry<?, ?> entry : map.entrySet()) {
      String childPath = basePath + "." + entry.getKey();
      Object value = entry.getValue();
      if (value instanceof String && StringUtils.isNotBlank((String) value)) {
        String explicitKey = getExplicitSchemaLocaleKey(parentNode, property + "." + entry.getKey());
        putMessage(messages, namespace, explicitKey != null ? explicitKey : childPath, (String) value);
        continue;
      }

      Map<String, Object> childNode = toMap(value);
      if (childNode != null) {
        extractSchemaNode(namespace, childNode, childPath, messages, depth + 1);
      }
    }
  }

  private int addMissingEntries(Map<String, Map<String, String>> target, Map<String, Map<String, String>> source) {
    int addedCount = 0;
    for (Map.Entry<String, Map<String, String>> namespaceEntry : source.entrySet()) {
      Map<String, String> targetEntries = target.computeIfAbsent(namespaceEntry.getKey(), ignore -> new LinkedHashMap<>());
      for (Map.Entry<String, String> entry : namespaceEntry.getValue().entrySet()) {
        if (!targetEntries.containsKey(entry.getKey())) {
          targetEntries.put(entry.getKey(), entry.getValue());
          addedCount++;
        }
      }
    }
    return addedCount;
  }

  private int removeOrphanedEntries(Map<String, Map<String, String>> existing, Map<String, Map<String, String>> template) {
    int removedCount = 0;
    for (Map.Entry<String, Map<String, String>> namespaceEntry : template.entrySet()) {
      Map<String, String> existingEntries = existing.get(namespaceEntry.getKey());
      if (existingEntries == null) {
        continue;
      }

      List<String> staleKeys = new ArrayList<>();
      for (String key : existingEntries.keySet()) {
        if (!namespaceEntry.getValue().containsKey(key)) {
          staleKeys.add(key);
        }
      }
      staleKeys.forEach(existingEntries::remove);
      removedCount += staleKeys.size();
    }

    existing.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isEmpty());
    return removedCount;
  }

  private Map<String, Map<String, String>> copyMessages(Map<String, Map<String, String>> messages) {
    Map<String, Map<String, String>> copied = new LinkedHashMap<>();
    if (messages == null) {
      return copied;
    }

    messages.forEach((namespace, entries) -> copied.put(
        namespace,
        entries == null ? new LinkedHashMap<>() : new LinkedHashMap<>(entries)
    ));
    return copied;
  }

  private void putMessage(Map<String, Map<String, String>> messages, String namespace, String key, String value) {
    messages.computeIfAbsent(namespace, ignore -> new LinkedHashMap<>()).put(key, value);
  }

  private boolean isSchemaBlacklisted(String property) {
    for (String blacklisted : UI_SCHEMA_BLACKLIST) {
      if (Objects.equals(property, blacklisted)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSchemaTextProperty(String property) {
    for (String textProperty : UI_SCHEMA_TEXT_PROPERTIES) {
      if (textProperty.startsWith("*")) {
        if (property.matches(wildcardToRegex(textProperty.substring(1)))) {
          return true;
        }
      } else if (Objects.equals(property, textProperty)) {
        return true;
      }
    }
    return false;
  }

  private boolean isSchemaWildcardProperty(String property) {
    for (String wildcardProperty : UI_SCHEMA_WILDCARD_PROPERTIES) {
      if (Objects.equals(property, wildcardProperty.replace(".*", ""))) {
        return true;
      }
    }
    return false;
  }

  private String getExplicitSchemaLocaleKey(Map<String, Object> node, String propertyPath) {
    Object localeKeys = node.get("localeKeys");
    if (!(localeKeys instanceof Map<?, ?> map)) {
      return null;
    }

    Object key = map.get(propertyPath);
    return key != null && StringUtils.isNotBlank(key.toString()) ? key.toString() : null;
  }

  private String getSchemaNodeIdentifier(Map<String, Object> node, int index, Map<String, Integer> typeCount) {
    String localePath = getString(node, "localePath");
    if (StringUtils.isNotBlank(localePath)) {
      return localePath;
    }

    String id = getString(node, "id");
    if (StringUtils.isNotBlank(id) && !id.startsWith("u:")) {
      return id;
    }

    String name = getString(node, "name");
    if (StringUtils.isNotBlank(name)) {
      return name;
    }

    String type = getString(node, "type");
    if (StringUtils.isNotBlank(type)) {
      if (typeCount.getOrDefault(type, 0) == 1) {
        return type;
      }
      return String.valueOf(index);
    }

    return String.valueOf(index);
  }

  private String buildSchemaNodePath(String parentPath, String nodeId, String propertyName, int index) {
    if (nodeId.matches("\\d+")) {
      String suffix = "." + propertyName;
      String basePath = parentPath.endsWith(suffix)
          ? parentPath.substring(0, parentPath.length() - suffix.length())
          : parentPath;
      return StringUtils.isBlank(basePath)
          ? propertyName + "[" + index + "]"
          : basePath + "." + propertyName + "[" + index + "]";
    }
    return parentPath + "." + nodeId;
  }

  private String wildcardToRegex(String wildcard) {
    String escaped = wildcard.replaceAll("([.^$+{}\\[\\]|()\\\\])", "\\\\$1");
    return escaped.replace("*", ".*");
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> toMap(Object value) {
    if (value instanceof Map<?, ?>) {
      return (Map<String, Object>) value;
    }
    return null;
  }

  private String getString(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value == null ? null : String.valueOf(value);
  }
}
