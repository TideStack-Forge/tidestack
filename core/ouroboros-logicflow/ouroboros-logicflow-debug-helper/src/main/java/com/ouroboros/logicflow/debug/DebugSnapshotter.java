package com.ouroboros.logicflow.debug;

import java.lang.reflect.Array;
import com.ouroboros.logicflow.results.Decision;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Copies only known safe value containers. Application getters and toString are never called. */
public final class DebugSnapshotter {
  private static final Set<String> SAFE_CONTAINERS = Set.of(
      "java.util.ArrayList", "java.util.LinkedList", "java.util.HashMap", "java.util.LinkedHashMap",
      "java.util.TreeMap", "java.util.HashSet", "java.util.LinkedHashSet", "java.util.TreeSet",
      "java.util.Arrays$ArrayList", "java.util.Collections$SingletonList", "java.util.Collections$SingletonMap",
      "java.util.Collections$EmptyList", "java.util.Collections$EmptyMap", "java.util.Collections$EmptySet");
  private static final Set<Class<?>> SAFE_NUMBERS = Set.of(Byte.class, Short.class, Integer.class, Long.class, Float.class, Double.class);
  private final Limits limits;

  public DebugSnapshotter(Limits limits) {
    this.limits = limits;
  }

  public Snapshot capture(Object value) {
    var budget = new Budget();
    return budget.result(copy(value, 0, budget));
  }

  public Snapshot captureException(Throwable failure) {
    var budget = new Budget();
    return budget.result(copyException(failure, 0, budget));
  }

  private Object copy(Object value, int depth, Budget budget) {
    if (value == null || value instanceof Boolean || SAFE_NUMBERS.contains(value.getClass())) {
      return value;
    }
    if (value instanceof String string) {
      return budget.text(string);
    }
    if (value instanceof Character character) {
      return budget.text(character.toString());
    }
    if (value.getClass() == BigInteger.class || value.getClass() == BigDecimal.class) {
      if ((value instanceof BigInteger integer && integer.bitLength() > limits.maxString() * 3L)
          || (value instanceof BigDecimal decimal && decimal.precision() > limits.maxString())) {
        return budget.marker("number-limit");
      }
      return budget.text(value.toString());
    }
    if (value instanceof Enum<?> enumeration) {
      return budget.text(enumeration.name());
    }
    if (depth >= limits.maxDepth()) {
      return budget.marker("depth-limit");
    }
    if (budget.seen.put(value, Boolean.TRUE) != null) {
      return budget.marker("cycle");
    }
    try {
      if (value.getClass() == Decision.class) {
        var decision = (Decision) value;
        var result = new LinkedHashMap<String, Object>();
        result.put("active", decision.isSatisfied());
        result.put("value", decision.isSatisfied() ? copy(decision.getValue(), depth + 1, budget) : null);
        return Collections.unmodifiableMap(result);
      }
      if (value.getClass().isArray()) {
        var result = new ArrayList<Object>();
        int length = Array.getLength(value);
        for (int index = 0; index < length; index++) {
          if (index >= limits.maxItems() || !budget.nextItem()) {
            result.add(budget.marker("item-limit"));
            break;
          }
          result.add(copy(Array.get(value, index), depth + 1, budget));
        }
        return Collections.unmodifiableList(result);
      }
      if (safeContainer(value) && value instanceof Map<?, ?> map) {
        return copyMap(map, depth, budget);
      }
      if (safeContainer(value) && value instanceof Collection<?> collection) {
        return copyCollection(collection, depth, budget);
      }
      budget.reasons.add("unsupported");
      return Map.of("status", "unsupported", "type", budget.text(value.getClass().getName()));
    } catch (RuntimeException failure) {
      return budget.marker("capture-error:" + failure.getClass().getSimpleName());
    } finally {
      budget.seen.remove(value);
    }
  }

  private Object copyMap(Map<?, ?> source, int depth, Budget budget) {
    var result = new LinkedHashMap<String, Object>();
    int count = 0;
    for (var entry : source.entrySet()) {
      if (count++ >= limits.maxItems() || !budget.nextItem()) {
        result.put("$truncated", budget.marker("item-limit"));
        break;
      }
      if (!(entry.getKey() instanceof String key) || key.length() > limits.maxString()) {
        result.put("$unsupportedKey", budget.marker("key-limit"));
        continue;
      }
      String copiedKey = budget.text(key);
      result.put(copiedKey, sensitive(key) ? Map.of("status", "redacted") : copy(entry.getValue(), depth + 1, budget));
    }
    return Collections.unmodifiableMap(result);
  }

  private Object copyCollection(Collection<?> source, int depth, Budget budget) {
    var result = new ArrayList<Object>();
    int count = 0;
    for (Object item : source) {
      if (count++ >= limits.maxItems() || !budget.nextItem()) {
        result.add(budget.marker("item-limit"));
        break;
      }
      result.add(copy(item, depth + 1, budget));
    }
    return Collections.unmodifiableList(result);
  }

  private boolean safeContainer(Object value) {
    var type = value.getClass();
    return type.getClassLoader() == null && (SAFE_CONTAINERS.contains(type.getName())
        || type.getName().startsWith("java.util.ImmutableCollections$"));
  }

  private boolean sensitive(String key) {
    String normalized = key.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
    return normalized.contains("password") || normalized.contains("token") || normalized.contains("secret")
        || normalized.contains("authorization") || normalized.contains("cookie");
  }

  private Object copyException(Throwable failure, int depth, Budget budget) {
    if (failure == null) {
      return null;
    }
    if (depth >= limits.maxCauses() || !budget.nextItem()) {
      return budget.marker("cause-limit");
    }
    if (budget.seen.put(failure, Boolean.TRUE) != null) {
      return budget.marker("cycle");
    }
    try {
      var result = new LinkedHashMap<String, Object>();
      result.put("type", budget.text(failure.getClass().getName()));
      result.put("message", safeThrowableAccessor(failure, "getMessage") ? copy(failure.getMessage(), 0, budget) : budget.marker("unsupported-message"));
      var frames = new ArrayList<Object>();
      if (safeThrowableAccessor(failure, "getStackTrace")) {
        var stack = failure.getStackTrace();
        for (int index = 0; index < stack.length; index++) {
          if (index >= limits.maxFrames() || !budget.nextItem()) {
            frames.add(budget.marker("frame-limit"));
            break;
          }
          frames.add(budget.text(stack[index].toString()));
        }
      } else {
        frames.add(budget.marker("unsupported-frames"));
      }
      result.put("frames", Collections.unmodifiableList(frames));
      result.put("cause", safeThrowableAccessor(failure, "getCause") ? copyException(failure.getCause(), depth + 1, budget) : budget.marker("unsupported-cause"));
      var suppressed = new ArrayList<Object>();
      var exceptions = failure.getSuppressed();
      for (int index = 0; index < exceptions.length; index++) {
        if (index >= limits.maxSuppressed()) {
          suppressed.add(budget.marker("suppressed-limit"));
          break;
        }
        suppressed.add(copyException(exceptions[index], depth + 1, budget));
      }
      result.put("suppressed", Collections.unmodifiableList(suppressed));
      return Collections.unmodifiableMap(result);
    } finally {
      budget.seen.remove(failure);
    }
  }

  private boolean safeThrowableAccessor(Throwable failure, String method) {
    try {
      return failure.getClass().getMethod(method).getDeclaringClass().getClassLoader() == null;
    } catch (NoSuchMethodException exception) {
      throw new IllegalStateException("Throwable contract missing " + method, exception);
    }
  }

  public record Snapshot(Object value, boolean truncated, List<String> reasons) {
    public Snapshot {
      reasons = List.copyOf(reasons);
    }
  }

  public record Limits(int maxDepth, int maxItems, int maxString, int maxTotalItems, int maxTotalChars,
                        int maxFrames, int maxCauses, int maxSuppressed) {
    public Limits {
      if (maxDepth < 1 || maxItems < 1 || maxString < 1 || maxTotalItems < 1 || maxTotalChars < 1
          || maxFrames < 1 || maxCauses < 1 || maxSuppressed < 0) {
        throw new IllegalArgumentException("Snapshot limits must be positive");
      }
    }

    public static Limits defaults() {
      return new Limits(6, 50, 8192, 500, 32768, 128, 8, 4);
    }
  }

  private final class Budget {
    private final IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
    private final Set<String> reasons = new LinkedHashSet<>();
    private int items;
    private int chars;

    boolean nextItem() {
      return ++items <= limits.maxTotalItems();
    }

    String text(String value) {
      int available = Math.max(0, Math.min(limits.maxString(), limits.maxTotalChars() - chars));
      int length = Math.min(value.length(), available);
      chars += length;
      if (length < value.length()) {
        reasons.add(length < limits.maxString() ? "character-limit" : "string-limit");
      }
      return value.substring(0, length);
    }

    Map<String, Object> marker(String reason) {
      reasons.add(reason);
      return Map.of("status", reason);
    }

    Snapshot result(Object value) {
      return new Snapshot(value, !reasons.isEmpty(), new ArrayList<>(reasons));
    }
  }
}
