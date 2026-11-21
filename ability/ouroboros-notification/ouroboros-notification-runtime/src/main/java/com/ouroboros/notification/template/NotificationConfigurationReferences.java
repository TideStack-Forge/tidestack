package com.ouroboros.notification.template;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/** The same literal configuration references are used by checking and dispatch. */
public final class NotificationConfigurationReferences {
  private static final Pattern REFERENCE = Pattern.compile("#\\{(.+?)}");

  private NotificationConfigurationReferences() { }

  public static Object resolve(Object value, String path, BiFunction<String, String, String> lookup) {
    if (value instanceof String text) {
      return REFERENCE.matcher(text).replaceAll(match -> java.util.regex.Matcher.quoteReplacement(
          lookup.apply(path, match.group(1).trim())));
    }
    if (value instanceof Map<?, ?> map) {
      var resolved = new LinkedHashMap<String, Object>();
      map.forEach((key, nested) -> {
        String name = String.valueOf(key);
        resolved.put(name, resolve(nested, path.isEmpty() ? name : path + "." + name, lookup));
      });
      return resolved;
    }
    if (value instanceof Collection<?> values) {
      var resolved = new java.util.ArrayList<Object>();
      for (Object nested : values) { resolved.add(resolve(nested, path + "[" + resolved.size() + "]", lookup)); }
      return resolved;
    }
    return value;
  }
}
