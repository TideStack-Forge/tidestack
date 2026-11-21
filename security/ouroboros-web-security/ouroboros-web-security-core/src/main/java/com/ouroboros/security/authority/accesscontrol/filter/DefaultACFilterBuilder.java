package com.ouroboros.security.authority.accesscontrol.filter;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.annotation.Priority;

import com.ouroboros.security.authority.accesscontrol.AccessControlDefine;

@Priority(999)
public class DefaultACFilterBuilder implements AccessControlFilterBuilder, RowScopeResolver {
  @Override
  public Map<String, Object> build(Map<String, Object> params, AccessControlDefine acDefine) {
    return params.entrySet().stream()
        .reduce(new LinkedHashMap<>(), (filter, entry) -> {
          if (entry.getKey().startsWith("$")) {
            return filter;
          }
          if (acDefine.getFilterDefine().stream().anyMatch(f -> f.getName().equals(entry.getKey()))) {
            filter.put(entry.getKey(), entry.getValue());
          }
          return filter;
        }, (a, b) -> {
          a.putAll(b);
          return a;
        });
  }

  @Override
  public com.ouroboros.security.authority.accesscontrol.RowConstraint resolve(Map<String, Object> params, AccessControlDefine define) {
    var filters = build(params, define);
    filters.keySet().forEach(params::remove);
    return filters.isEmpty() ? com.ouroboros.security.authority.accesscontrol.RowConstraint.all()
        : com.ouroboros.security.authority.accesscontrol.RowConstraint.filter(filters);
  }
}
