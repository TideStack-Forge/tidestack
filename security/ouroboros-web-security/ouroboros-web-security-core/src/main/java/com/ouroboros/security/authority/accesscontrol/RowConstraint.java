package com.ouroboros.security.authority.accesscontrol;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** An immutable resolved QueryDSL condition. NONE is never represented by an empty condition. */
public record RowConstraint(Mode mode, Map<String, Object> condition) implements java.io.Serializable {
  public enum Mode { ALL, FILTER, NONE }

  public RowConstraint {
    Objects.requireNonNull(mode, "行模式不能为空");
    condition = immutableMap(condition);
    if ((mode == Mode.FILTER) == condition.isEmpty()) {
      throw new IllegalArgumentException("行模式与条件载荷不一致");
    }
  }

  public static RowConstraint all() { return new RowConstraint(Mode.ALL, Map.of()); }
  public static RowConstraint none() { return new RowConstraint(Mode.NONE, Map.of()); }
  public static RowConstraint filter(Map<String, Object> condition) { return new RowConstraint(Mode.FILTER, condition); }

  public RowConstraint and(RowConstraint other) {
    if (mode == Mode.NONE || other.mode == Mode.NONE) { return none(); }
    if (mode == Mode.ALL) { return other; }
    if (other.mode == Mode.ALL) { return this; }
    return combine(other, "AND");
  }

  public RowConstraint or(RowConstraint other) {
    if (mode == Mode.ALL || other.mode == Mode.ALL) { return all(); }
    if (mode == Mode.NONE) { return other; }
    if (other.mode == Mode.NONE) { return this; }
    return combine(other, "OR");
  }

  private RowConstraint combine(RowConstraint other, String operator) {
    List<Object> operands = new ArrayList<>();
    append(operands, condition, operator);
    append(operands, other.condition, operator);
    return filter(Map.of(operator, operands));
  }

  private static void append(List<Object> operands, Map<String, Object> condition, String operator) {
    if (condition.size() == 1 && condition.get(operator) instanceof List<?> nested) { operands.addAll(nested); }
    else { operands.add(condition); }
  }

  private static Map<String, Object> immutableMap(Map<String, Object> source) {
    Map<String, Object> copy = new LinkedHashMap<>();
    source.forEach((key, value) -> copy.put(Objects.requireNonNull(key), immutableValue(value)));
    return Collections.unmodifiableMap(copy);
  }

  private static Object immutableValue(Object value) {
    if (value == null || value instanceof String || value instanceof Boolean
        || value instanceof Byte || value instanceof Short || value instanceof Integer || value instanceof Long
        || value instanceof Float || value instanceof Double || value instanceof java.math.BigDecimal
        || value instanceof java.math.BigInteger || value instanceof java.time.temporal.TemporalAccessor) { return value; }
    if (value instanceof Map<?, ?> map) {
      Map<String, Object> copy = new LinkedHashMap<>();
      map.forEach((key, item) -> {
        if (!(key instanceof String name)) { throw new IllegalArgumentException("行条件键必须是字符串"); }
        copy.put(name, immutableValue(item));
      });
      return Collections.unmodifiableMap(copy);
    }
    if (value instanceof Collection<?> items) {
      return Collections.unmodifiableList(items.stream().map(RowConstraint::immutableValue).toList());
    }
    throw new IllegalArgumentException("行条件包含不可冻结的类型: " + value.getClass().getName());
  }
}
