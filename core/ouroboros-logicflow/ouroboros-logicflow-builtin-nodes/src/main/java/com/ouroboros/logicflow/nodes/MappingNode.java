package com.ouroboros.logicflow.nodes;

import static com.ouroboros.logicflow.util.NodeUtil.buildContextMap;

import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ouroboros.logicflow.expression.LogicFlowExpression;
import com.ouroboros.logicflow.exception.FlowRuntimeException;
import com.ouroboros.logicflow.model.FlowModel;
import com.ouroboros.logicflow.model.FlowNodeMeta;
import com.ouroboros.logicflow.model.FlowNodePort;
import com.ouroboros.logicflow.nodes.support.LogicFlowMaps;

public class MappingNode extends AbstractNode<Object> {

  /**
   * 冲突解决策略枚举
   */
  public enum ConflictResolutionStrategy {
    /**
     * 覆盖已存在的值（默认行为）
     */
    OVERWRITE,
    /**
     * 跳过冲突字段，保持原有值
     */
    SKIP,
    /**
     * 尝试合并值（如果都是Map类型）
     */
    MERGE,
    /**
     * 遇到冲突时抛出异常
     */
    ERROR
  }

  final String MAPPING_CONFIG_NAME = "mapping";
  final String CONFLICT_STRATEGY_CONFIG_NAME = "conflictStrategy";

  private final Function<List<Object>, Object> mappingFunction;
  private final List<String> inputPortNames;
  private final Boolean mappingEachItem;
  private final ConflictResolutionStrategy conflictStrategy;

  public MappingNode(FlowModel flowModel, FlowNodeMeta meta) {
    super(flowModel, meta);
    this.mappingEachItem = meta.getExtraProp(Boolean.class, "mappingEachItem").orElse(false);
    this.conflictStrategy = getConflictStrategy(meta);
    this.inputPortNames = getInputPorts().stream().map(FlowNodePort::getName).collect(Collectors.toList());
    this.mappingFunction = compileMappingFunction();
  }

  /**
   * 获取冲突解决策略
   */
  private ConflictResolutionStrategy getConflictStrategy(FlowNodeMeta meta) {
    return meta.getExtraProp(String.class, CONFLICT_STRATEGY_CONFIG_NAME)
        .map(strategyStr -> {
          try {
            return ConflictResolutionStrategy.valueOf(strategyStr.toUpperCase());
          } catch (IllegalArgumentException e) {
            return ConflictResolutionStrategy.OVERWRITE;
          }
        })
        .orElse(ConflictResolutionStrategy.OVERWRITE);
  }

  /**
   * 编译映射函数
   *
   * @return 映射函数
   */
  private Function<List<Object>, Object> compileMappingFunction() {
    final var mappingConfig = getMappingConfig();
    final var mappingFunction = mappingEachItem
        ? compileCollectionMappingFunction(mappingConfig)
        : compileSingleMappingFunction(mappingConfig);

    return inputs -> {
      if (inputs == null || inputs.isEmpty()) {
        throw new FlowRuntimeException("映射节点的输入不能为空", null, this, inputs);
      }
      var context = buildContextMap(inputs, inputPortNames);
      return mappingFunction.apply(context);
    };
  }

  /**
   * 获取映射配置
   *
   * @return 映射配置
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> getMappingConfig() {
    return meta.getExtraProp(Map.class, MAPPING_CONFIG_NAME).orElseGet(Collections::emptyMap);
  }

  /**
   * 编译单个映射函数
   *
   * @param mappingConfig 映射配置
   * @return 映射函数
   */
  private Function<Map<String, Object>, Object> compileSingleMappingFunction(Map<String, Object> mappingConfig) {
    // 标识是否有嵌套字段
    var nested = mappingConfig.keySet().stream()
        .anyMatch(fieldName -> fieldName.contains(".") || fieldName.contains("[") && fieldName.contains("]"));

    // 为每个字段编译映射函数
    var fieldSetters = mappingConfig.entrySet().stream()
        .map(entry -> {
          return compileFieldSetter(entry.getKey(), entry.getValue());
        })
        .collect(Collectors.toList());

    // 将所有字段的映射函数合并为一个映射函数
    return context -> {
      var target = new ConflictAwareMap();
      fieldSetters.forEach(fieldSetter -> fieldSetter.accept(context, target));
      if (nested) {
        return LogicFlowMaps.Hierarchical.build(target.getInternalMap());
      }
      return target.getInternalMap();
    };
  }

  private BiConsumer<Map<String, Object>, ConflictAwareMap> compileFieldSetter(String fieldName, Object mappingRule) {
    // 处理展开操作符 &
    if (isExpandField(fieldName)) {
      return compileExpandSetter(fieldName, mappingRule);
    }

    // 如果字段映射配置是字符串，则表示是一个表达式
    if (mappingRule instanceof String exprStr) {
      var expression = LogicFlowExpression.wrapExpression(exprStr);
      return (context, target) -> {
        var value = LogicFlowExpression.eval(expression, context);
        if (value.isLeft()) {
          Throwable throwable = value.getLeft();
          if (throwable instanceof RuntimeException) {
            throw (RuntimeException) throwable;
          }
          throw new FlowRuntimeException("表达式执行失败", throwable, this, null);
        }
        var result = value.getOrElseGet(throwable -> null);
        target.put(fieldName, result);
      };
    }

    // 如果字段映射配置是 Map，则表示嵌套映射
    if (mappingRule instanceof Map<?, ?>) {
      @SuppressWarnings("unchecked")
      var subMappingConfig = (Map<String, Object>) mappingRule;

      // 编译嵌套映射的字段设置器
      var subFieldSetters = subMappingConfig.entrySet().stream()
          .map(entry -> compileFieldSetter(entry.getKey(), entry.getValue()))
          .collect(Collectors.toList());

      return (context, target) -> {
        // 为嵌套映射创建新的目标Map
        var nestedTarget = new ConflictAwareMap();

        // 应用所有嵌套字段设置器
        subFieldSetters.forEach(setter -> setter.accept(context, nestedTarget));

        // 将嵌套结果设置到目标字段
        target.put(fieldName, nestedTarget.getInternalMap());
      };
    }

    return (context, target) -> {
      target.put(fieldName, mappingRule);
    };
  }

  /**
   * 检查字段名是否为展开字段
   *
   * @param fieldName 字段名
   * @return 是否为展开字段
   */
  private boolean isExpandField(String fieldName) {
    return "&".equals(fieldName) || fieldName.endsWith(".&");
  }

  /**
   * 编译展开设置器
   *
   * @param fieldName   字段名
   * @param mappingRule 映射规则
   * @return 展开设置器
   */
  private BiConsumer<Map<String, Object>, ConflictAwareMap> compileExpandSetter(String fieldName, Object mappingRule) {
    // 如果字段映射配置是字符串，则表示是一个表达式
    if (mappingRule instanceof String exprStr) {
      var expression = LogicFlowExpression.wrapExpression(exprStr);

      if ("&".equals(fieldName)) {
        // 直接展开到当前层级
        return (context, target) -> {
          var value = LogicFlowExpression.eval(expression, context);
          if (value.isLeft()) {
            Throwable throwable = value.getLeft();
            if (throwable instanceof RuntimeException) {
              throw (RuntimeException) throwable;
            }
            throw new FlowRuntimeException("展开表达式执行失败", throwable, this, null);
          }

          var result = value.getOrElseGet(throwable -> null);
          if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapToExpand = (Map<String, Object>) result;
            target.putAll(mapToExpand);
          } else if (result != null) {
            throw new FlowRuntimeException("展开操作要求值为Map类型，实际为: " + result.getClass().getSimpleName(), null, this, null);
          }
        };
      } else if (fieldName.endsWith(".&")) {
        // 处理嵌套路径展开，如 "X.&" 将内容展开到 X 路径下的各个字段
        String basePath = fieldName.substring(0, fieldName.length() - 2);
        return (context, target) -> {
          var value = LogicFlowExpression.eval(expression, context);
          if (value.isLeft()) {
            Throwable throwable = value.getLeft();
            if (throwable instanceof RuntimeException) {
              throw (RuntimeException) throwable;
            }
            throw new FlowRuntimeException("展开表达式执行失败", throwable, this, null);
          }

          var result = value.getOrElseGet(throwable -> null);
          if (result instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mapToExpand = (Map<String, Object>) result;
            // 将展开的内容作为嵌套字段设置，让 LogicFlowMaps.Hierarchical.build 处理合并
            for (Map.Entry<String, Object> entry : mapToExpand.entrySet()) {
              String nestedKey = basePath + "." + entry.getKey();
              target.put(nestedKey, entry.getValue());
            }
          } else if (result != null) {
            throw new FlowRuntimeException("展开操作要求值为Map类型，实际为: " + result.getClass().getSimpleName(), null, this, null);
          }
        };
      }
    }

    // 对于非表达式的展开操作，不做任何处理
    return (context, target) -> {
      // 展开操作必须使用表达式
    };
  }

  /**
   * 支持冲突检测的Map包装类
   */
  private class ConflictAwareMap {
    private final Map<String, Object> internalMap = new LinkedHashMap<>();

    public void put(String key, Object value) {
      handleFieldConflict(key, value);
    }

    public void putAll(Map<String, Object> map) {
      for (Map.Entry<String, Object> entry : map.entrySet()) {
        handleFieldConflict(entry.getKey(), entry.getValue());
      }
    }

    public Map<String, Object> getInternalMap() {
      return internalMap;
    }

    private void handleFieldConflict(String key, Object newValue) {
      Object existingValue = internalMap.get(key);

      if (existingValue == null || conflictStrategy == ConflictResolutionStrategy.OVERWRITE) {
        internalMap.put(key, newValue);
        return;
      }

      switch (conflictStrategy) {
        case SKIP:
          // 保持原值，不覆盖
          logConflictSkipped(key, existingValue, newValue);
          break;

        case MERGE:
          if (existingValue instanceof Map && newValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> existingMap = (Map<String, Object>) existingValue;
            @SuppressWarnings("unchecked")
            Map<String, Object> newMap = (Map<String, Object>) newValue;

            Map<String, Object> merged = new LinkedHashMap<>(existingMap);
            merged.putAll(newMap);
            internalMap.put(key, merged);
            logConflictMerged(key, existingValue, newValue);
          } else {
            // 无法合并时回退到覆盖
            internalMap.put(key, newValue);
            logConflictOverwritten(key, existingValue, newValue);
          }
          break;

        case ERROR:
          throw new FlowRuntimeException(
              String.format("字段冲突：键 '%s' 已存在值 '%s'，尝试设置新值 '%s'",
                  key, existingValue, newValue), null, MappingNode.this, null);

        default:
          internalMap.put(key, newValue);
      }
    }

    private void logConflictSkipped(String key, Object existing, Object newValue) {
      // 可以在这里添加日志记录
      // logger.debug("字段冲突已跳过：键 '{}', 保持原值 '{}', 跳过新值 '{}'", key, existing, newValue);
    }

    private void logConflictMerged(String key, Object existing, Object newValue) {
      // logger.debug("字段冲突已合并：键 '{}', 原值 '{}', 新值 '{}'", key, existing, newValue);
    }

    private void logConflictOverwritten(String key, Object existing, Object newValue) {
      // logger.debug("字段冲突已覆盖：键 '{}', 原值 '{}', 新值 '{}'", key, existing, newValue);
    }
  }

  /**
   * 编译集合映射函数
   *
   * @param mappingConfig 映射配置
   * @return 映射函数
   */
  @SuppressWarnings({"rawtypes", "unchecked"})
  private Function<Map<String, Object>, Object> compileCollectionMappingFunction(Map<String, Object> mappingConfig) {
    // 编译单个映射函数
    var itemMappingFunction = compileSingleMappingFunction(mappingConfig);

    // 将单个映射函数应用到集合中的每个元素上
    return context -> {
      var result = new ArrayList<>();

      // 获取集合输入
      var input = context.get("$0");

      Collection<?> collection;
      if (input == null) {
        return Collections.emptyList();
      } else if (input instanceof Collection<?>) {
        collection = (Collection<?>) input;
      } else {
        collection = Collections.singletonList(input);
      }

      var index = 0;
      for (var item : collection) {
        var itemContext = new HashMap<>(context);
        if (item instanceof Map<?, ?> map) {
          itemContext.putAll((Map) map);
        }
        itemContext.put("$0", item);
        itemContext.put("$item", item);
        itemContext.put("$index", index++);
        itemContext.put("$parentContext", context);
        result.add(itemMappingFunction.apply(itemContext));
      }
      return result;
    };
  }

  @Override
  protected final List<Object> doExecute(List<Object> inputs) {
    try {
      var result = mappingFunction.apply(inputs);
      return Collections.singletonList(result);
    } catch (Exception e) {
      throw new FlowRuntimeException("映射节点执行失败", e, this, inputs);
    }
  }

  public static class Builder implements NodeBuilder<MappingNode> {
    @Override
    public Boolean support(FlowNodeMeta meta) {
      return "mapping".equalsIgnoreCase(meta.getType());
    }

    @Override
    public MappingNode build(FlowModel owner, FlowNodeMeta metadata, NodeReferenceCatalog references) {
      return new MappingNode(owner, metadata);
    }
  }
}