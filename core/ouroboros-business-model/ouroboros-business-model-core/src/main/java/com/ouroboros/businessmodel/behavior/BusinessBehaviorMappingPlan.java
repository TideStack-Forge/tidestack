package com.ouroboros.businessmodel.behavior;

import java.util.AbstractMap;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.expression.EL;
import com.ouroboros.expression.ExpressionPlan;

/** Published EL value and presence plans; each view owns its values and failure cache. */
public final class BusinessBehaviorMappingPlan {
  private final Value value;
  private final String location;

  private BusinessBehaviorMappingPlan(Value value, String location) {
    this.value = value;
    this.location = location;
  }

  public static BusinessBehaviorMappingPlan flowInput(Map<String, Object> mapping, Map<String, Object> schema,
                                                      BehaviorDefinitionSource source) {
    return fields(mapping, schema, source, BehaviorMappingCompatibility.Field.FLOW_INPUT);
  }

  public static BusinessBehaviorMappingPlan subscriptionInput(Map<String, Object> mapping, BehaviorDefinitionSource source) {
    // This is a new contract with no legacy $. dialect, independently of the consumer model format.
    return fields(mapping, Map.of(), new BehaviorDefinitionSource(source.resource(),
        source.fieldPath() + ".target", "2.0"), BehaviorMappingCompatibility.Field.SUBSCRIPTION_INPUT);
  }

  public static BusinessBehaviorMappingPlan flowOutput(Object mapping, BehaviorDefinitionSource source) {
    // In the old output contract an object/list was a literal, not an expression tree.
    Value value = !source.current() && !(mapping instanceof String)
        ? new Constant(mapping) : compile(mapping, Map.of(), source, BehaviorMappingCompatibility.Field.FLOW_OUTPUT);
    return new BusinessBehaviorMappingPlan(value, source.location() + ".outputMapping");
  }

  public static Map<String, Object> upgradeFlowInput(Map<String, Object> mapping, Map<String, Object> schema,
                                                    BehaviorDefinitionSource source) {
    return upgradeFields(mapping, schema, source, BehaviorMappingCompatibility.Field.FLOW_INPUT);
  }

  public static Object upgradeFlowOutput(Object mapping, BehaviorDefinitionSource source) {
    return upgrade(mapping, Map.of(), source, BehaviorMappingCompatibility.Field.FLOW_OUTPUT, !(mapping instanceof String));
  }

  private static Map<String, Object> upgradeFields(Map<String, Object> mapping, Map<String, Object> schema,
      BehaviorDefinitionSource source, BehaviorMappingCompatibility.Field field) {
    Map<String, Object> result = new LinkedHashMap<>();
    mapping.forEach((name, value) -> result.put(name, upgrade(value, schema, source, field, false)));
    return result;
  }

  private static Object upgrade(Object raw, Map<String, Object> schema, BehaviorDefinitionSource source,
      BehaviorMappingCompatibility.Field field, boolean literalTree) {
    if (source.current()) { throw new IllegalArgumentException(source.location() + ": 已是新版定义"); }
    if (raw instanceof String text) {
      if (literalTree) { return "${ '" + text.replace("'", "''").replace("${", "$${") + "' }"; }
      if (field == BehaviorMappingCompatibility.Field.FLOW_INPUT && "$.context".equals(text)) { return "${ context }"; }
      var conversion = BehaviorMappingCompatibility.expression(text, field, false, schema);
      if (!conversion.collectionGuards().isEmpty()) {
        throw new IllegalArgumentException(source.location() + ": 旧映射缺少集合形状契约，不能证明升级等价");
      }
      var expression = conversion.expression();
      var plan = EL.compile(expression).getOrElseThrow(error -> new IllegalArgumentException(source.location() + ": 无效映射", error));
      // Legacy mappings materialize missing selectors as null. Keep that behavior after upgrade.
      if (plan.selectorPath().isPresent()) {
        return "${ true ? (" + expression.substring(2, expression.length() - 1).trim() + ") : null }";
      }
      return expression;
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Object> result = new LinkedHashMap<>();
      map.forEach((key, value) -> result.put(String.valueOf(key), upgrade(value, schema, source, field, literalTree)));
      return result;
    }
    if (raw instanceof List<?> list) {
      return list.stream().map(value -> upgrade(value, schema, source, field, literalTree)).toList();
    }
    return raw;
  }

  private static BusinessBehaviorMappingPlan fields(Map<String, Object> mapping, Map<String, Object> schema,
                                                     BehaviorDefinitionSource source, BehaviorMappingCompatibility.Field field) {
    Map<String, Object> tree = new LinkedHashMap<>();
    mapping.forEach((name, expression) -> com.ouroboros.util.Maps.Hierarchical.setValue(tree, name,
        compile(expression, schema, source, field)));
    return new BusinessBehaviorMappingPlan(tree(tree), source.location() + ".inputMapping");
  }

  private static Value tree(Map<?, ?> map) {
    Map<String, Value> children = new LinkedHashMap<>();
    map.forEach((name, nested) -> children.put(String.valueOf(name), nested instanceof Value plan
        ? plan : tree((Map<?, ?>) nested)));
    return new ObjectValue(Collections.unmodifiableMap(children));
  }

  private static Value compile(Object raw, Map<String, Object> schema, BehaviorDefinitionSource source,
                                BehaviorMappingCompatibility.Field field) {
    if (raw instanceof String text) {
      try {
        var conversion = BehaviorMappingCompatibility.expression(text, field, source.current(), schema);
        ExpressionPlan plan = EL.compile(conversion.expression()).getOrElseThrow(error ->
            new IllegalArgumentException("Invalid platform expression", error));
        var guards = conversion.collectionGuards().stream().map(expression -> EL.compile(expression)
            .getOrElseThrow(error -> new IllegalArgumentException("Invalid legacy shape guard", error))).toList();
        boolean retainContext = field == BehaviorMappingCompatibility.Field.FLOW_INPUT
            && (!source.current() && (text.equals("$.context") || text.startsWith("$.context."))
                || plan.selectorPath().filter(path -> !path.isEmpty() && "context".equals(path.getFirst())).isPresent());
        return new ExpressionValue(plan, !source.current(), guards, retainContext);
      } catch (RuntimeException error) {
        throw new IllegalArgumentException(source.location() + ": invalid mapping", error);
      }
    }
    if (raw instanceof Map<?, ?> map) {
      Map<String, Value> fields = new LinkedHashMap<>();
      map.forEach((key, nested) -> fields.put(String.valueOf(key), compile(nested, schema, source, field)));
      return new ObjectValue(Collections.unmodifiableMap(fields));
    }
    if (raw instanceof List<?> list) {
      return new ArrayValue(list.stream().map(item -> compile(item, schema, source, field)).toList());
    }
    return new Constant(raw);
  }

  public Map<String, Object> map(Map<String, Object> context, UnaryOperator<Object> copy) {
    return view(((ObjectValue) value).fields(), context, copy, location);
  }

  public Object evaluate(Map<String, Object> context) {
    return value.read(context, UnaryOperator.identity(), location);
  }

  private interface Value {
    default boolean present(Map<String, Object> context) { return true; }
    Object read(Map<String, Object> context, UnaryOperator<Object> copy, String location);
  }

  private record ExpressionValue(ExpressionPlan expression, boolean materialize, List<ExpressionPlan> collectionGuards,
                                 boolean retainContext) implements Value {
    public boolean present(Map<String, Object> context) {
      return materialize || expression.isPresent(context).getOrElseThrow(error ->
          new BusinessException("业务映射存在性检查失败", error));
    }
    public Object read(Map<String, Object> context, UnaryOperator<Object> copy, String location) {
      for (var guard : collectionGuards) {
        if (Boolean.TRUE.equals(guard.evaluate(context).getOrElseThrow(error ->
            new BusinessException(location + ": 旧映射来源形状检查失败", error)))) {
          throw new BusinessException(location + ": 旧映射的集合路径缺少结构契约，不能等价转换");
        }
      }
      Object result = expression.evaluate(context).getOrElseThrow(error ->
          new BusinessException(location + ": 业务映射求值失败", error));
      return retainContext ? result : copy.apply(result);
    }
  }

  private record Constant(Object value) implements Value {
    private Constant {
      value = com.ouroboros.businessmodel.action.BusinessActionInputValues.snapshot(value);
    }
    public Object read(Map<String, Object> context, UnaryOperator<Object> copy, String location) {
      return copy.apply(com.ouroboros.businessmodel.action.BusinessActionInputValues.workCopy(value));
    }
  }

  private record ObjectValue(Map<String, Value> fields) implements Value {
    public boolean present(Map<String, Object> context) {
      return fields.isEmpty() || fields.values().stream().anyMatch(field -> field.present(context));
    }
    public Object read(Map<String, Object> context, UnaryOperator<Object> copy, String location) {
      return view(fields, context, copy, location);
    }
  }

  private record ArrayValue(List<Value> items) implements Value {
    public Object read(Map<String, Object> context, UnaryOperator<Object> copy, String location) {
      return new java.util.ArrayList<>(items.stream().map(item -> item.read(context, copy, location)).toList());
    }
  }

  private static Map<String, Object> view(Map<String, Value> fields, Map<String, Object> context,
                                         UnaryOperator<Object> copy, String location) {
    return new AbstractMap<>() {
      private final Map<String, Object> cache = new LinkedHashMap<>();
      private final Map<String, Boolean> presence = new LinkedHashMap<>();
      private final Map<String, RuntimeException> failures = new LinkedHashMap<>();
      private final Set<String> removed = new java.util.HashSet<>();

      @Override public boolean containsKey(Object key) {
        if (!(key instanceof String name) || removed.contains(name)) { return false; }
        if (cache.containsKey(name)) { return true; }
        if (!fields.containsKey(name)) { return false; }
        if (failures.containsKey(name)) { throw failures.get(name); }
        try { return presence.computeIfAbsent(name, ignored -> fields.get(name).present(context)); }
        catch (RuntimeException error) { throw failure(name, error); }
      }
      @Override public Object get(Object key) {
        if (!(key instanceof String name) || !containsKey(name)) { return null; }
        if (failures.containsKey(name)) { throw failures.get(name); }
        if (!cache.containsKey(name)) {
          try { cache.put(name, fields.get(name).read(context, copy, location + "." + name)); }
          catch (RuntimeException error) { throw failure(name, error); }
        }
        return cache.get(name);
      }
      private RuntimeException failure(String name, RuntimeException cause) {
        var failure = new BusinessException(location + "." + name + ": 业务映射失败", cause);
        failures.put(name, failure);
        return failure;
      }
      @Override public Object put(String name, Object value) {
        Object previous = get(name);
        removed.remove(name);
        failures.remove(name);
        cache.put(name, value);
        return previous;
      }
      @Override public Object remove(Object key) {
        Object previous = get(key);
        if (key instanceof String name) { removed.add(name); cache.remove(name); }
        return previous;
      }
      @Override public void clear() {
        removed.addAll(fields.keySet());
        cache.clear();
      }
      @Override public Set<String> keySet() {
        Set<String> keys = new java.util.LinkedHashSet<>(fields.keySet());
        keys.addAll(cache.keySet());
        keys.removeIf(name -> !containsKey(name));
        return Collections.unmodifiableSet(keys);
      }
      @Override public Set<Entry<String, Object>> entrySet() {
        Map<String, Object> view = this;
        return new java.util.AbstractSet<>() {
          public int size() { return view.keySet().size(); }
          public java.util.Iterator<Entry<String, Object>> iterator() {
            return view.keySet().stream().<Entry<String, Object>>map(name -> new Entry<>() {
              public String getKey() { return name; }
              public Object getValue() { return view.get(name); }
              public Object setValue(Object value) { return view.put(name, value); }
              public boolean equals(Object other) { return other instanceof Entry<?, ?> entry
                  && name.equals(entry.getKey()) && java.util.Objects.equals(getValue(), entry.getValue()); }
              public int hashCode() { return name.hashCode() ^ java.util.Objects.hashCode(getValue()); }
            }).iterator();
          }
        };
      }
    };
  }
}
