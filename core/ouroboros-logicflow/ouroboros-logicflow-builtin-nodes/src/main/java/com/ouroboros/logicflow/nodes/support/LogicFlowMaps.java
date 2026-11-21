package com.ouroboros.logicflow.nodes.support;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.ouroboros.logicflow.util.LogicFlowJson;

import io.vavr.control.Try;

public final class LogicFlowMaps {
  private LogicFlowMaps() {
  }

  public static <K, V> Map<K, V> merge(Map<K, V>[] sources) {
    var target = new LinkedHashMap<K, V>();
    for (var source : sources) {
      if (source != null) {
        target.putAll(source);
      }
    }
    return target;
  }

  public static <K, V> Map<K, V> deepMerge(Map<K, V>[] sources) {
    var target = new LinkedHashMap<K, V>();
    for (var source : sources) {
      deepMergeTo(target, source);
    }
    return target;
  }

  private static <K, V> void deepMergeTo(Map<K, V> target, Map<K, V> source) {
    if (source == null) {
      return;
    }
    source.forEach((key, value) -> {
      var oldValue = target.get(key);
      if (oldValue instanceof Map<?, ?> oldMap && value instanceof Map<?, ?> newMap) {
        var merged = new LinkedHashMap<Object, Object>(oldMap);
        newMap.forEach(merged::put);
        target.put(key, (V) merged);
        return;
      }
      target.put(key, value);
    });
  }

  public static <K, V> Map<K, V> omit(Map<K, V> map, Collection<K> keys) {
    Objects.requireNonNull(keys, "keys cannot be null");
    return pick(map, key -> !keys.contains(key));
  }

  private static <K, V> Map<K, V> pick(Map<K, V> map, Predicate<K> predicate) {
    Objects.requireNonNull(map, "map cannot be null");
    var result = new LinkedHashMap<K, V>();
    map.forEach((key, value) -> {
      if (predicate.test(key)) {
        result.put(key, value);
      }
    });
    return result;
  }

  public static <T> Try<T> tryToBean(Class<T> type, Map<?, ?> map) {
    return Try.of(() -> LogicFlowJson.OBJECT_MAPPER.convertValue(map, type));
  }

  public static final class Hierarchical {
    private Hierarchical() {
    }

    public static Map<String, Object> build(Map<String, Object> flattened) {
      var result = new LinkedHashMap<String, Object>();
      if (flattened == null) {
        return result;
      }
      flattened.forEach((key, value) -> setValue(result, splitPath(key), deepClone(value)));
      return result;
    }

    public static Optional<Object> getValue(Map<String, Object> map, String path) {
      if (map == null || path == null) {
        return Optional.empty();
      }
      Object current = map;
      for (var segment : splitPath(path)) {
        if (current instanceof Map<?, ?> currentMap) {
          current = currentMap.get(segment);
        } else if (current instanceof List<?> currentList && segment.matches("\\d+")) {
          var index = Integer.parseInt(segment);
          current = index >= 0 && index < currentList.size() ? currentList.get(index) : null;
        } else {
          return Optional.empty();
        }
        if (current == null) {
          return Optional.empty();
        }
      }
      return Optional.of(current);
    }

    private static List<String> splitPath(String path) {
      var segments = new ArrayList<String>();
      var token = new StringBuilder();
      for (var index = 0; index < path.length(); index++) {
        var character = path.charAt(index);
        if (character == '.') {
          flushToken(segments, token);
        } else if (character == '[') {
          flushToken(segments, token);
          var end = path.indexOf(']', index);
          if (end < 0) {
            token.append(character);
          } else {
            segments.add(path.substring(index + 1, end));
            index = end;
          }
        } else {
          token.append(character);
        }
      }
      flushToken(segments, token);
      return segments;
    }

    private static void flushToken(List<String> segments, StringBuilder token) {
      if (token.length() > 0) {
        segments.add(token.toString());
        token.setLength(0);
      }
    }

    private static void setValue(Map<String, Object> map, List<String> path, Object value) {
      Object current = map;
      for (var index = 0; index < path.size(); index++) {
        var segment = path.get(index);
        var last = index == path.size() - 1;
        if (current instanceof Map<?, ?> rawMap) {
          Map<String, Object> currentMap = (Map<String, Object>) rawMap;
          if (last) {
            currentMap.put(segment, value);
            return;
          }
          var next = currentMap.get(segment);
          if (!(next instanceof Map<?, ?>) && !(next instanceof List<?>)) {
            next = path.get(index + 1).matches("\\d+") ? new ArrayList<>() : new LinkedHashMap<String, Object>();
            currentMap.put(segment, next);
          }
          current = next;
        } else if (current instanceof List<?> rawList) {
          List<Object> currentList = (List<Object>) rawList;
          var listIndex = Integer.parseInt(segment);
          fillList(currentList, listIndex);
          if (last) {
            currentList.set(listIndex, value);
            return;
          }
          var next = currentList.get(listIndex);
          if (!(next instanceof Map<?, ?>) && !(next instanceof List<?>)) {
            next = path.get(index + 1).matches("\\d+") ? new ArrayList<>() : new LinkedHashMap<String, Object>();
            currentList.set(listIndex, next);
          }
          current = next;
        }
      }
    }

    private static void fillList(List<Object> list, int index) {
      while (list.size() <= index) {
        list.add(null);
      }
    }

    private static Object deepClone(Object value) {
      if (value instanceof Map<?, ?> map) {
        var cloned = new LinkedHashMap<String, Object>();
        map.forEach((key, item) -> cloned.put(String.valueOf(key), deepClone(item)));
        return cloned;
      }
      if (value instanceof Collection<?> collection) {
        var cloned = new ArrayList<Object>();
        collection.forEach(item -> cloned.add(deepClone(item)));
        return cloned;
      }
      return value;
    }
  }
}
