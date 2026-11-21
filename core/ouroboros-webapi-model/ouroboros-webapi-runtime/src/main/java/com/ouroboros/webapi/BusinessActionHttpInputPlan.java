package com.ouroboros.webapi;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.util.UriComponentsBuilder;

import com.ouroboros.exception.BusinessException;
import com.ouroboros.expression.ExpressionPlan;
import com.ouroboros.logicflow.adapter.RequestInfoMap;
import com.ouroboros.util.Maps;

/** Immutable route plan. It never retains invocation values or request objects. */
final class BusinessActionHttpInputPlan {
  private final Map<String, Projection> fields;
  private final java.util.Set<String> arraySources;

  BusinessActionHttpInputPlan(Map<?, ?> mapping, List<com.ouroboros.webapi.openapi.ParameterModel> parameters) {
    this(parameters, BusinessActionWebApiContract.compileMapping(mapping, null, null));
  }

  BusinessActionHttpInputPlan(WebAPIModel model) {
    this(model.getParameters(), BusinessActionWebApiContract.compileInput(model));
  }

  private BusinessActionHttpInputPlan(List<com.ouroboros.webapi.openapi.ParameterModel> parameters,
                                      Map<String, ExpressionPlan> mapping) {
    java.util.Set<String> arrays = new java.util.HashSet<>();
    parameters.stream().filter(parameter -> parameter.getSchema() != null && "array".equals(parameter.getSchema().getType()))
        .forEach(parameter -> {
          if (parameter.getIn() == com.ouroboros.webapi.openapi.ParameterLocation.QUERY) {
            if (!"form".equals(parameter.getExtraProp(String.class, "style").orElse("form"))
                || !parameter.getExtraProp(Boolean.class, "explode").orElse(true)) {
              throw new IllegalArgumentException("Action query arrays require form/explode repeated parameters");
            }
            arrays.add("query." + parameter.getName());
          } else if (parameter.getIn() == com.ouroboros.webapi.openapi.ParameterLocation.HEADER) {
            arrays.add("headers." + parameter.getName().toLowerCase(java.util.Locale.ROOT));
          }
        });
    arraySources = java.util.Set.copyOf(arrays);
    List<Field> compiled = new ArrayList<>();
    mapping.forEach((name, expression) -> {
      String[] destination = name.split("\\.");
      compiled.add(new Field(destination, expression));
    });
    fields = compile(compiled, 0);
  }

  /** Like RequestInfoMap, this is a key-based view, not a materialized input tree. */
  Map<String, Object> read(ServerRequest request, RequestInfoMap info) {
    return view(fields, new Sources(request, info));
  }

  private static Map<String, Projection> compile(List<Field> fields, int offset) {
    Map<String, List<Field>> groups = new LinkedHashMap<>();
    fields.forEach(field -> groups.computeIfAbsent(field.destination()[offset], ignored -> new ArrayList<>()).add(field));
    Map<String, Projection> result = new LinkedHashMap<>();
    groups.forEach((part, group) -> {
      Field first = group.getFirst();
      if (part.endsWith("[]")) {
        result.put(part.substring(0, part.length() - 2), new Projection(first.source(),
            java.util.Arrays.copyOfRange(first.destination(), offset + 1, first.destination().length), Map.of()));
      } else if (offset == first.destination().length - 1) {
        result.put(part, new Projection(first.source(), null, Map.of()));
      } else {
        result.put(part, new Projection(null, null, compile(group, offset + 1)));
      }
    });
    return java.util.Collections.unmodifiableMap(result);
  }

  private static Map<String, Object> view(Map<String, Projection> fields, Sources sources) {
    return java.util.Collections.unmodifiableMap(new java.util.AbstractMap<>() {
      private final Map<String, Object> values = new LinkedHashMap<>();
      private final Map<String, Boolean> presence = new LinkedHashMap<>();
      private final Map<String, RuntimeException> failures = new LinkedHashMap<>();

      @Override
      public boolean containsKey(Object key) {
        if (!(key instanceof String name) || !fields.containsKey(name)) { return false; }
        if (failures.containsKey(name)) { throw failures.get(name); }
        try {
          return presence.computeIfAbsent(name, ignored -> fields.get(name).present(sources));
        } catch (RuntimeException failure) {
          var located = new BusinessException("Action HTTP 输入存在性检查失败：" + name, failure);
          failures.put(name, located);
          throw located;
        }
      }

      @Override
      public Object get(Object key) {
        if (!(key instanceof String name) || !containsKey(name)) { return null; }
        if (failures.containsKey(name)) { throw failures.get(name); }
        if (!values.containsKey(name)) {
          try {
            values.put(name, fields.get(name).read(sources));
          } catch (RuntimeException failure) {
            var located = new BusinessException("Action HTTP 输入映射失败：" + name, failure);
            failures.put(name, located);
            throw located;
          }
        }
        return values.get(name);
      }

      @Override
      public java.util.Set<String> keySet() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        fields.keySet().stream().filter(this::containsKey).forEach(keys::add);
        return java.util.Collections.unmodifiableSet(keys);
      }

      @Override
      public java.util.Set<Entry<String, Object>> entrySet() { return entries(this); }
    });
  }

  /** Entry keys can be inspected without evaluating their values. */
  private static java.util.Set<Map.Entry<String, Object>> entries(Map<String, Object> map) {
    return new java.util.AbstractSet<>() {
      @Override public int size() { return map.keySet().size(); }
      @Override public java.util.Iterator<Map.Entry<String, Object>> iterator() {
        var keys = map.keySet().iterator();
        return new java.util.Iterator<>() {
          @Override public boolean hasNext() { return keys.hasNext(); }
          @Override public Map.Entry<String, Object> next() {
            String key = keys.next();
            return new Map.Entry<>() {
              @Override public String getKey() { return key; }
              @Override public Object getValue() { return map.get(key); }
              @Override public Object setValue(Object value) { throw new UnsupportedOperationException(); }
              @Override public boolean equals(Object other) {
                return other instanceof Map.Entry<?, ?> entry && key.equals(entry.getKey())
                    && java.util.Objects.equals(getValue(), entry.getValue());
              }
              @Override public int hashCode() { return key.hashCode() ^ java.util.Objects.hashCode(getValue()); }
            };
          }
        };
      }
    };
  }

  private record Projection(ExpressionPlan source, String[] itemPath, Map<String, Projection> children) {
    boolean present(Sources sources) {
      return source != null ? sources.present(source) : children.values().stream().anyMatch(child -> child.present(sources));
    }

    Object read(Sources sources) {
      if (source == null) { return view(children, sources); }
      Object value = sources.value(source);
      if (itemPath == null) { return value; }
      if (!(value instanceof List<?> items)) { throw new BusinessException("Action HTTP 数组映射来源必须为数组"); }
      List<Object> result = new ArrayList<>();
      for (Object item : items) {
        Map<String, Object> projected = new LinkedHashMap<>();
        put(projected, itemPath, 0, item);
        result.add(projected);
      }
      return result;
    }
  }

  private final class Sources {
    private final ServerRequest request;
    private final RequestInfoMap info;
    private Map<String, Object> query;
    private final Map<String, Object> headers;

    Sources(ServerRequest request, RequestInfoMap info) {
      this.request = request;
      this.info = info;
      this.headers = new java.util.AbstractMap<>() {
        @Override public boolean containsKey(Object key) {
          return key instanceof String name && request.servletRequest().getHeader(name) != null;
        }
        @Override public java.util.Set<String> keySet() {
          return new java.util.LinkedHashSet<>(java.util.Collections.list(request.servletRequest().getHeaderNames()));
        }
        @Override public Object get(Object key) {
          if (!(key instanceof String name)) { return null; }
          List<String> values = java.util.Collections.list(request.servletRequest().getHeaders(name));
          return values.isEmpty() ? null : transportValue("headers." + name.toLowerCase(java.util.Locale.ROOT), values);
        }
        @Override public java.util.Set<Entry<String, Object>> entrySet() { return entries(this); }
      };
    }

    boolean present(ExpressionPlan plan) {
      if (plan.selectorPath().filter(path -> path.size() >= 2 && "request".equals(path.get(0))
          && ("body".equals(path.get(1)) || "data".equals(path.get(1)))).isPresent() && !hasBody(request)) {
        return false;
      }
      return plan.isPresent(context()).getOrElseThrow(error -> new BusinessException("Action HTTP 输入来源不可读取", error));
    }

    Object value(ExpressionPlan plan) {
      return plan.evaluate(context()).getOrElseThrow(error -> new BusinessException("Action HTTP 表达式求值失败", error));
    }

    private Map<String, Object> context() {
      return new java.util.AbstractMap<>() {
        @Override public boolean containsKey(Object key) { return java.util.Set.of("request", "query", "headers").contains(key); }
        @Override public Object get(Object key) { return "request".equals(key) ? info : key instanceof String name ? root(name) : null; }
        @Override public java.util.Set<String> keySet() { return java.util.Set.of("request", "query", "headers"); }
        @Override public java.util.Set<Entry<String, Object>> entrySet() { return entries(this); }
      };
    }

    private Object root(String name) {
      if ("headers".equals(name)) { return headers; }
      if ("query".equals(name)) {
        if (query == null) { query = query(request); }
        return query;
      }
      return null;
    }
  }

  private static boolean hasBody(ServerRequest request) {
    return request.servletRequest().getContentLengthLong() > 0
        || (request.servletRequest().getContentLengthLong() < 0 && request.headers().contentType().isPresent());
  }

  private Map<String, Object> query(ServerRequest request) {
    Map<String, Object> values = new LinkedHashMap<>();
    UriComponentsBuilder.fromUri(request.uri()).build().getQueryParams().forEach((name, raw) -> {
      List<String> decoded = raw.stream().map(value -> value == null ? "" : URLDecoder.decode(value, StandardCharsets.UTF_8)).toList();
      String decodedName = URLDecoder.decode(name, StandardCharsets.UTF_8);
      values.put(decodedName, transportValue("query." + decodedName, decoded));
    });
    return Maps.Hierarchical.build(values);
  }

  private Object transportValue(String source, List<String> values) {
    return values.size() == 1 && !arraySources.contains(source) ? values.getFirst() : List.copyOf(values);
  }

  private static void put(Map<String, Object> result, String[] path, int index, Object value) {
    String part = path[index];
    if (part.endsWith("[]")) {
      if (!(value instanceof List<?> values) || index + 1 >= path.length) {
        throw new BusinessException("Action HTTP 数组映射来源必须为数组");
      }
      List<Object> mapped = new ArrayList<>();
      for (Object item : values) {
        Map<String, Object> reference = new LinkedHashMap<>();
        put(reference, path, index + 1, item);
        mapped.add(reference);
      }
      result.put(part.substring(0, part.length() - 2), mapped);
    } else if (index == path.length - 1) {
      result.put(part, value);
    } else {
      Map<String, Object> child = new LinkedHashMap<>();
      if (result.get(part) instanceof Map<?, ?> existing) {
        existing.forEach((key, nested) -> child.put(String.valueOf(key), nested));
      }
      put(child, path, index + 1, value);
      result.put(part, child);
    }
  }

  private record Field(String[] destination, ExpressionPlan source) {
  }
}
