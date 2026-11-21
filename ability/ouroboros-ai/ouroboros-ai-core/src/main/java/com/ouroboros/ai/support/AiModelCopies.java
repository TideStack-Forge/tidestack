package com.ouroboros.ai.support;

import java.util.*;

public final class AiModelCopies {

  private AiModelCopies() {
  }

  public static Map<String, Object> copyMap(Map<String, Object> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyMap();
    }
    return Collections.unmodifiableMap(new LinkedHashMap<>(source));
  }

  public static List<String> copyStringList(List<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  public static Set<String> copyStringSet(Set<String> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(source));
  }

  public static <T> List<T> copyList(List<T> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptyList();
    }
    return Collections.unmodifiableList(new ArrayList<>(source));
  }

  public static <T> Set<T> copySet(Set<T> source) {
    if (source == null || source.isEmpty()) {
      return Collections.emptySet();
    }
    return Collections.unmodifiableSet(new LinkedHashSet<>(source));
  }
}
