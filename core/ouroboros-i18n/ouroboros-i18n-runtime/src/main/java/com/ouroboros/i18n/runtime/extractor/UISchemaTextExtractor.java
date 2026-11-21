package com.ouroboros.i18n.runtime.extractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.vavr.control.Either;

import com.ouroboros.exception.OuroborosException;
import com.ouroboros.i18n.extractor.TextExtractor;
import com.ouroboros.i18n.model.I18nEntry;
import com.ouroboros.i18n.runtime.model.I18nEntryImpl;
import com.ouroboros.uischema.UISchemaProvider;
import com.ouroboros.uischema.schema.SchemaNode;
import com.ouroboros.util.Json;
import com.ouroboros.util.SPI;

/**
 * UI Schema 文案提取器
 * 路径生成规则: localePath > id > name > (type if unique, else index)
 * <p>
 * 提取的文本属性:
 * - 基础: label, title, placeholder, description
 * - 提示: tooltip, remark, disabledTip, validationErrors
 * - 按钮: submitText, resetText, confirmText, cancelText
 * - 其他: name (作为标识), btnLabel, optionLabel
 * <p>
 * 自动递归策略:
 * - 所有对象和数组类型的属性都会自动递归处理
 * - 通过黑名单排除不需要处理的属性
 * - 通配符属性特殊处理（如 messages.*, definitions.*）
 * <p>
 * 特殊属性:
 * - localeKey: 完整的 key，覆盖整个路径
 * - localePath: 当前节点的路径标识，替换节点标识符部分
 */
public class UISchemaTextExtractor implements TextExtractor {

  private static final Logger logger = LoggerFactory.getLogger(UISchemaTextExtractor.class);
  private static final int MAX_DEPTH = 20;

  // 需要提取的文本属性
  private static final String[] TEXT_PROPERTIES = {
      // 基础文本
      "label", "title", "placeholder", "description", "tpl", "content", "msg",
      // 提示文本
      "tooltip", "remark", "disabledTip", "popOver",
      // 消息提示
      "success", "failed",
      "*Failed",
      "*Success",
      // 通配符支持
      "*Text", "*Label", "*Msg"
  };

  // 通配符属性（特殊处理：key -> string 映射）
  private static final String[] WILDCARD_PROPERTIES = {
      "definitions.*",
      "messages.*",
      "validationErrors.*"
  };

  // 属性黑名单（不递归处理）
  private static final String[] PROPERTY_BLACKLIST = {
      "data",           // 数据字段，通常不包含UI文本
      "value",          // 值字段
      "defaultValue",   // 默认值
      "source",         // 数据源
      "reload",         // 重载配置
      "redirect",       // 重定向配置
      "className",      // CSS类名
      "style",          // 样式
      "inputClassName", // 输入框类名
      "labelClassName", // 标签类名
      "staticClassName",// 静态类名
      "visibleOn",      // 可见性表达式
      "hiddenOn",       // 隐藏表达式
      "disabledOn",     // 禁用表达式
      "requiredOn",     // 必填表达式
      "staticOn",       // 静态表达式
      "required",       // 必填标志
      "disabled",       // 禁用标志
      "hidden",         // 隐藏标志
      "static",         // 静态标志
      "id",             // ID
      "localeKey",      // 多语言键前缀/直达键
      "localeKeys",     // 属性级多语言键映射
      "localePath",     // 多语言路径标识
      "name",           // 名称（作为标识符，不是文本）
      "type"            // 类型
  };

  @Override
  public Either<OuroborosException, List<I18nEntry>> extract() {
    List<I18nEntry> entries = new ArrayList<>();

    SPI.getServiceStream(UISchemaProvider.class).forEach(provider -> {
      try {
        Map<String, Object> schemaMap = provider.getUISchemaMap();
        if (schemaMap == null) {
          return;
        }

        schemaMap.forEach((schemaPath, schemaObject) -> {
          try {
            String json = Json.toJsonString(schemaObject);
            Json.tryToBean(SchemaNode.class, json).onSuccess(node -> {
              extractFromNode(schemaPath, node, "", entries, 0);
            });
          } catch (Exception e) {
            logger.error("Failed to process schema: {}", schemaPath, e);
          }
        });
      } catch (Exception e) {
        logger.error("Failed to extract from provider: {}", provider.getClass().getName(), e);
      }
    });

    logger.debug("Extracted {} UI schema entries", entries.size());
    return Either.right(entries);
  }

  private void extractFromNode(String schemaPath, SchemaNode node, String path, List<I18nEntry> entries, int depth) {
    if (depth > MAX_DEPTH) {
      return;
    }

    String keyPrefix = buildKeyPrefix(node, path);

    // 提取文本属性
    extractTextProperties(schemaPath, node, keyPrefix, entries);

    // 提取 onEvent 中的文本
    extractFromOnEvent(schemaPath, node, keyPrefix, entries);

    // 自动递归所有对象和数组属性
    processAllChildProperties(schemaPath, node, keyPrefix, entries, depth);
  }

  private String buildKeyPrefix(SchemaNode node, String path) {
    // 如果是根节点，使用 type 作为标识
    String currentPath = path;
    if (path.isEmpty()) {
      String type = node.getType();
      currentPath = (type != null && !type.trim().isEmpty()) ? type : "root";
    }

    // 检查是否有 localeKey 属性
    Object localeKey = node.getProp("localeKey");
    return (localeKey != null && !localeKey.toString().trim().isEmpty())
        ? localeKey.toString()
        : currentPath;
  }

  private void extractTextProperties(String schemaPath, SchemaNode node, String keyPrefix, List<I18nEntry> entries) {
    Object localeKey = node.getProp("localeKey");

    for (String prop : TEXT_PROPERTIES) {
      if (prop.startsWith("*")) {
        extractWildcardTextProperties(schemaPath, node, keyPrefix, localeKey, prop.substring(1), entries);
      } else {
        extractSingleTextProperty(schemaPath, node, keyPrefix, localeKey, prop, entries);
      }
    }
  }

  private void extractSingleTextProperty(String schemaPath, SchemaNode node, String keyPrefix, Object localeKey, String prop, List<I18nEntry> entries) {
    Object value = node.getProp(prop);
    if (value instanceof String && !((String) value).trim().isEmpty()) {
      // localeKey 替换路径前缀，但仍需拼接属性名
      String explicitKey = getExplicitLocaleKey(node, prop);
      String finalKey = explicitKey != null
          ? explicitKey
          : (localeKey != null && !localeKey.toString().trim().isEmpty())
          ? localeKey.toString() + "." + prop
          : keyPrefix + "." + prop;

      addI18nEntry(schemaPath, finalKey, (String) value, prop, entries);
    } else if (value != null && !(value instanceof String)) {
      // 如果是对象，递归提取（使用原路径，不使用 localeKey）
      SchemaNode childNode = toSchemaNode(value);
      if (childNode != null) {
        String childPath = keyPrefix.isEmpty() ? prop : keyPrefix + "." + prop;
        extractFromNode(schemaPath, childNode, childPath, entries, 0);
      }
    }
  }

  private void processAllChildProperties(String schemaPath, SchemaNode node, String keyPrefix, List<I18nEntry> entries, int depth) {
    Map<String, Object> props = node.getProps();
    if (props == null) return;

    props.forEach((propName, value) -> {
      // 跳过黑名单属性
      if (isBlacklisted(propName)) {
        return;
      }

      // 跳过已处理的文本属性
      if (isTextProperty(propName)) {
        return;
      }

      // 检查是否是通配符属性
      if (isWildcardProperty(propName)) {
        if (value instanceof Map) {
          String childPath = keyPrefix.isEmpty() ? propName : keyPrefix + "." + propName;
          processWildcardMap(schemaPath, node, (Map<?, ?>) value, propName, childPath, entries, depth);
        }
        return;
      }

      // 自动递归对象和数组
      String childPath = keyPrefix.isEmpty() ? propName : keyPrefix + "." + propName;

      String explicitKey = getExplicitLocaleKey(node, propName);
      if (value instanceof String && !((String) value).trim().isEmpty() && explicitKey != null) {
        addI18nEntry(schemaPath, explicitKey, (String) value, propName, entries);
        return;
      }

      if (value instanceof List) {
        processChildListWithProperty(schemaPath, (List<?>) value, childPath, propName, entries, depth);
      } else if (value instanceof Map || isComplexObject(value)) {
        SchemaNode childNode = toSchemaNode(value);
        if (childNode != null) {
          extractFromNode(schemaPath, childNode, childPath, entries, depth + 1);
        }
      }
    });
  }

  private boolean isBlacklisted(String propName) {
    for (String blacklisted : PROPERTY_BLACKLIST) {
      if (propName.equals(blacklisted)) {
        return true;
      }
    }
    return false;
  }

  private boolean isTextProperty(String propName) {
    for (String textProp : TEXT_PROPERTIES) {
      if (textProp.startsWith("*")) {
        String pattern = wildcardToRegex(textProp.substring(1));
        if (propName.matches(pattern)) {
          return true;
        }
      } else if (propName.equals(textProp)) {
        return true;
      }
    }
    return false;
  }

  private boolean isWildcardProperty(String propName) {
    for (String wildcardProp : WILDCARD_PROPERTIES) {
      String baseName = wildcardProp.replace(".*", "");
      if (propName.equals(baseName)) {
        return true;
      }
    }
    return false;
  }

  private boolean isComplexObject(Object value) {
    if (value == null) return false;
    if (value instanceof String || value instanceof Number || value instanceof Boolean) {
      return false;
    }
    return true;
  }

  private void addI18nEntry(String schemaPath, String key, String value, String sourceProperty, List<I18nEntry> entries) {
    entries.add(new I18nEntryImpl(
        schemaPath,
        key,
        value,
        "UISchema: " + schemaPath,
        sourceProperty
    ));
  }

  private void extractFromOnEvent(String schemaPath, SchemaNode node, String path, List<I18nEntry> entries) {
    Object onEvent = node.getProp("onEvent");
    if (!(onEvent instanceof Map)) return;

    ((Map<?, ?>) onEvent).forEach((eventName, actions) -> {
      if (!(actions instanceof Map)) return;

      Object actionsList = ((Map<?, ?>) actions).get("actions");
      if (!(actionsList instanceof List<?> list)) return;

      for (int i = 0; i < list.size(); i++) {
        extractFromEventAction(schemaPath, list.get(i), path, eventName, i, entries);
      }
    });
  }

  private void extractFromEventAction(String schemaPath, Object action, String path, Object eventName, int index, List<I18nEntry> entries) {
    if (!(action instanceof Map<?, ?> actionMap)) return;

    String actionType = String.valueOf(actionMap.get("actionType"));

    switch (actionType) {
      case "toast":
        extractEventText(schemaPath, actionMap, "msg", path, eventName, index, "onEvent.toast.msg", entries);
        break;
      case "dialog":
      case "drawer":
        extractEventText(schemaPath, actionMap, "title", path, eventName, index, "onEvent." + actionType + ".title", entries);
        break;
    }
  }

  private void extractEventText(String schemaPath, Map<?, ?> actionMap, String property, String path, Object eventName, int index, String sourceProperty, List<I18nEntry> entries) {
    Object value = actionMap.get(property);
    if (value instanceof String && !((String) value).trim().isEmpty()) {
      String key = path + ".onEvent." + eventName + "." + index + "." + property;
      addI18nEntry(schemaPath, key, (String) value, sourceProperty, entries);
    }
  }

  private void processChildListWithProperty(String schemaPath, List<?> children, String path, String propertyName, List<I18nEntry> entries, int depth) {
    // 统计每个 type 的出现次数
    Map<String, Integer> typeCount = new HashMap<>();
    List<SchemaNode> nodes = new ArrayList<>();
    List<Integer> originalIndices = new ArrayList<>();

    for (int i = 0; i < children.size(); i++) {
      Object child = children.get(i);
      SchemaNode node = toSchemaNode(child);
      if (node != null) {
        nodes.add(node);
        originalIndices.add(i);  // 记录原始索引
        String type = node.getType();
        if (type != null && !type.trim().isEmpty()) {
          typeCount.put(type, typeCount.getOrDefault(type, 0) + 1);
        }
      }
    }

    // 处理每个节点
    Map<String, Integer> usedCount = new HashMap<>();
    for (int i = 0; i < nodes.size(); i++) {
      SchemaNode node = nodes.get(i);
      int originalIndex = originalIndices.get(i);  // 使用原始索引
      String nodeId = getNodeIdentifier(node, originalIndex, typeCount);

      // 处理重复标识符
      int count = usedCount.getOrDefault(nodeId, 0);
      String finalNodeId = nodeId;
      if (count > 0) {
        finalNodeId = nodeId + "[" + count + "]";
      }
      usedCount.put(nodeId, count + 1);

      // 构建节点路径，使用原始索引
      String nodePath = buildNodePathWithProperty(path, finalNodeId, propertyName, originalIndex);
      extractFromNode(schemaPath, node, nodePath, entries, depth + 1);
    }
  }

  private String buildNodePathWithProperty(String parentPath, String nodeId, String propertyName, int index) {
    // 如果节点ID是纯数字（下标），使用属性名[index]格式
    if (nodeId.matches("\\d+")) {
      // 移除路径末尾的属性名，然后添加 propertyName[index]
      String basePath = parentPath.substring(0, parentPath.lastIndexOf("." + propertyName));
      return basePath.isEmpty() ? propertyName + "[" + index + "]" : basePath + "." + propertyName + "[" + index + "]";
    }

    return parentPath + "." + nodeId;
  }

  private void processWildcardMap(String schemaPath, SchemaNode parentNode, Map<?, ?> map, String propName,
                                  String basePath, List<I18nEntry> entries, int depth) {
    if (depth > MAX_DEPTH) return;

    map.forEach((key, value) -> {
      String childPath = basePath + "." + key;

      if (value instanceof String && !((String) value).trim().isEmpty()) {
        String explicitKey = getExplicitLocaleKey(parentNode, propName + "." + key);
        addI18nEntry(schemaPath, explicitKey != null ? explicitKey : childPath, (String) value, String.valueOf(key), entries);
      } else {
        SchemaNode childNode = toSchemaNode(value);
        if (childNode != null) {
          extractFromNode(schemaPath, childNode, childPath, entries, depth + 1);
        }
      }
    });
  }

  private void extractWildcardTextProperties(String schemaPath, SchemaNode node, String keyPrefix, Object localeKey, String pattern, List<I18nEntry> entries) {
    Map<String, Object> props = node.getProps();
    if (props == null) return;

    String regex = wildcardToRegex(pattern);

    props.entrySet().stream()
        .filter(entry -> entry.getKey().matches(regex))
        .forEach(entry -> {
          String propName = entry.getKey();
          Object value = entry.getValue();

          if (value instanceof String && !((String) value).trim().isEmpty()) {
            // localeKey 替换路径前缀，但仍需拼接属性名
            String explicitKey = getExplicitLocaleKey(node, propName);
            String finalKey = explicitKey != null
                ? explicitKey
                : (localeKey != null && !localeKey.toString().trim().isEmpty())
                ? localeKey.toString() + "." + propName
                : keyPrefix + "." + propName;

            addI18nEntry(schemaPath, finalKey, (String) value, propName, entries);
          } else if (value != null && !(value instanceof String)) {
            // 递归提取对象（使用原路径，不使用 localeKey）
            SchemaNode childNode = toSchemaNode(value);
            if (childNode != null) {
              String childPath = keyPrefix.isEmpty() ? propName : keyPrefix + "." + propName;
              extractFromNode(schemaPath, childNode, childPath, entries, 0);
            }
          }
        });
  }

  private String wildcardToRegex(String wildcard) {
    // 转义正则表达式特殊字符，但保留 *
    String escaped = wildcard.replaceAll("([.^$+{}\\[\\]|()\\\\])", "\\\\$1");
    // 将 * 转换为 .*
    return escaped.replace("*", ".*");
  }

  private String getNodeIdentifier(SchemaNode node, int index, Map<String, Integer> typeCount) {
    // 0. 最高优先级：localePath（自定义路径标识）
    Object localePath = node.getProp("localePath");
    if (localePath != null && !localePath.toString().trim().isEmpty()) {
      return localePath.toString();
    }

    // 1. 优先 id（忽略 u: 开头的 id）
    Object id = node.getProp("id");
    if (id != null && !id.toString().trim().isEmpty()) {
      String idStr = id.toString();
      if (!idStr.startsWith("u:")) {
        return idStr;
      }
    }

    // 2. 其次 name
    Object name = node.getProp("name");
    if (name != null && !name.toString().trim().isEmpty()) {
      return name.toString();
    }

    // 3. 根据上下文选择 type 或 index
    String type = node.getType();
    if (type != null && !type.trim().isEmpty()) {
      // 如果该 type 在数组中唯一，使用 type
      if (typeCount.getOrDefault(type, 0) == 1) {
        return type;
      }
      // 如果该 type 重复，使用 index
      return String.valueOf(index);
    }

    // 4. 兜底用 index
    return String.valueOf(index);
  }

  private SchemaNode toSchemaNode(Object obj) {
    if (obj instanceof SchemaNode) {
      return (SchemaNode) obj;
    }
    try {
      String json = Json.toJsonString(obj);
      return Json.tryToBean(SchemaNode.class, json).getOrNull();
    } catch (Exception e) {
      return null;
    }
  }

  private String getExplicitLocaleKey(SchemaNode node, String propertyPath) {
    Object localeKeys = node.getProp("localeKeys");
    if (!(localeKeys instanceof Map<?, ?> map)) {
      return null;
    }

    Object key = map.get(propertyPath);
    return key != null && !key.toString().trim().isEmpty() ? key.toString() : null;
  }
}
