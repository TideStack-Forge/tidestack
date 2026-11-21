package com.ouroboros.businessmodel.behavior;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Defines the materialized Schema locations supported by business object reference resolution. */
public final class BusinessObjectReferenceSchemas {
  private BusinessObjectReferenceSchemas() {
  }

  public static List<String> unsupportedReferencePaths(Object schema) {
    List<String> paths = new ArrayList<>();
    inspect(schema, "", true, containsObjectReference(schema), paths);
    return List.copyOf(paths);
  }

  /** Identity aliases name the id of one explicitly declared, materialized object role. */
  public static boolean isObjectIdentityPointer(Map<String, Object> root, Object value) {
    if (!(value instanceof String pointer) || !pointer.startsWith("/params/") && !pointer.startsWith("/data/")
        || !pointer.endsWith("/id") || pointer.matches(".*~(?![01]).*")) {
      return false;
    }
    Map<?, ?> schema = root;
    String[] segments = pointer.substring(1).split("/", -1);
    for (int index = 0; index < segments.length; index++) {
      if (index == segments.length - 1 && !(schema.get("x-business-object-ref") instanceof String)) {
        return false;
      }
      String name = segments[index].replace("~1", "/").replace("~0", "~");
      if (!(schema.get("properties") instanceof Map<?, ?> properties)
          || !(properties.get(name) instanceof Map<?, ?> nested)) {
        return false;
      }
      schema = nested;
    }
    return true;
  }

  private static boolean containsObjectReference(Object value) {
    if (value instanceof Map<?, ?> map) {
      return map.containsKey("x-business-object-ref") || map.containsKey("x-business-relation")
          || map.containsKey("x-business-object-identity") || map.values().stream()
          .anyMatch(BusinessObjectReferenceSchemas::containsObjectReference);
    }
    return value instanceof List<?> list && list.stream().anyMatch(BusinessObjectReferenceSchemas::containsObjectReference);
  }

  private static void inspect(Object value, String path, boolean materialized, boolean hasReferences, List<String> paths) {
    if (value instanceof Map<?, ?> map) {
      if (map.containsKey("x-business-object-ref") && !materialized) {
        paths.add(path + ".x-business-object-ref");
      }
      if (map.containsKey("x-business-relation") && !materialized) {
        paths.add(path + ".x-business-relation");
      }
      if (map.containsKey("x-business-object-identity") && !materialized) {
        paths.add(path + ".x-business-object-identity");
      }
      map.forEach((key, nested) -> {
        String name = String.valueOf(key);
        String nestedPath = path + "." + name;
        if (hasReferences && nested instanceof String
            && ("$ref".equals(name) || "$dynamicRef".equals(name) || "$recursiveRef".equals(name))) {
          paths.add(nestedPath);
        }
        if (materialized && "properties".equals(name) && nested instanceof Map<?, ?> properties) {
          properties.forEach((property, definition) -> inspect(definition, nestedPath + "." + property,
              true, hasReferences, paths));
        } else {
          inspect(nested, nestedPath, materialized && "items".equals(name) && nested instanceof Map<?, ?>,
              hasReferences, paths);
        }
      });
    } else if (value instanceof List<?> list) {
      for (int index = 0; index < list.size(); index++) {
        inspect(list.get(index), path + "[" + index + "]", false, hasReferences, paths);
      }
    }
  }
}
