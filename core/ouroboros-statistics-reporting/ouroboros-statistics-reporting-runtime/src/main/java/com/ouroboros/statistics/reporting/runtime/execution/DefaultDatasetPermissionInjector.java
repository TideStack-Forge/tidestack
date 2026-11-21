package com.ouroboros.statistics.reporting.runtime.execution;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ouroboros.statistics.reporting.definition.DatasetDefinition;

public class DefaultDatasetPermissionInjector implements DatasetPermissionInjector {
  public static final String PERMISSION_BINDINGS_CONTEXT_KEY = "__statisticsReportingPermissionBindings";

  @Override
  public DatasetExecutionRequest inject(DatasetDefinition dataset, DatasetExecutionRequest request) {
    if (dataset == null) {
      throw new IllegalArgumentException("dataset definition is required");
    }
    if (request == null) {
      throw new IllegalArgumentException("dataset execution request is required");
    }

    DatasetExecutionRequest authorized = request.copy();
    Map<String, Object> permissionDefinition = dataset.getPermissionDefinition() == null
        ? new LinkedHashMap<String, Object>()
        : dataset.getPermissionDefinition();
    ensureReadable(dataset, permissionDefinition, authorized.getExecutionContext());
    applyScopeBindings(permissionDefinition, authorized);
    return authorized;
  }

  public boolean canExport(DatasetDefinition dataset, DatasetExecutionRequest request) {
    if (dataset == null) {
      return false;
    }
    Map<String, Object> permissionDefinition = dataset.getPermissionDefinition() == null
        ? new LinkedHashMap<String, Object>()
        : dataset.getPermissionDefinition();
    List<String> exportRoles = toStringList(permissionDefinition.get("exportRoles"));
    if (exportRoles.isEmpty()) {
      return true;
    }
    return hasAnyRole(exportRoles, request == null ? null : request.getExecutionContext());
  }

  private void ensureReadable(DatasetDefinition dataset,
                              Map<String, Object> permissionDefinition,
                              Map<String, Object> executionContext) {
    List<String> requiredRoles = toStringList(permissionDefinition.get("requiredRoles"));
    if (!requiredRoles.isEmpty() && !hasAnyRole(requiredRoles, executionContext)) {
      throw new IllegalArgumentException("dataset access denied: " + dataset.getName());
    }
  }

  private void applyScopeBindings(Map<String, Object> permissionDefinition, DatasetExecutionRequest request) {
    Map<String, String> scopeBindings = toStringMap(permissionDefinition.get("scopeBindings"));
    if (scopeBindings.isEmpty()) {
      return;
    }

    Map<String, Object> executionContext = request.getExecutionContext() == null
        ? new LinkedHashMap<String, Object>()
        : request.getExecutionContext();
    Map<String, Object> appliedParameters = request.getAppliedParameters() == null
        ? new LinkedHashMap<String, Object>()
        : request.getAppliedParameters();
    List<String> appliedBindings = new ArrayList<String>();

    scopeBindings.forEach((parameterName, contextKey) -> {
      if (isBlank(parameterName) || isBlank(contextKey)) {
        return;
      }
      Object contextValue = executionContext.get(contextKey);
      if (isEmpty(contextValue)) {
        throw new IllegalArgumentException(
            "missing required permission scope binding: " + contextKey);
      }
      appliedParameters.put(parameterName, contextValue);
      appliedBindings.add(parameterName + "<-" + contextKey);
    });

    if (!appliedBindings.isEmpty()) {
      executionContext.put(PERMISSION_BINDINGS_CONTEXT_KEY, appliedBindings);
      request.setExecutionContext(executionContext);
      request.setAppliedParameters(appliedParameters);
    }
  }

  private boolean hasAnyRole(List<String> requiredRoles, Map<String, Object> executionContext) {
    if (requiredRoles.isEmpty()) {
      return true;
    }
    List<String> currentRoles = toStringList(executionContext == null ? null : executionContext.get("roles"));
    for (String requiredRole : requiredRoles) {
      for (String currentRole : currentRoles) {
        if (requiredRole.equals(currentRole)) {
          return true;
        }
      }
    }
    return false;
  }

  private Map<String, String> toStringMap(Object value) {
    Map<String, String> mapped = new LinkedHashMap<String, String>();
    if (!(value instanceof Map)) {
      return mapped;
    }
    ((Map<?, ?>) value).forEach((key, item) -> {
      if (key != null && item != null) {
        mapped.put(String.valueOf(key), String.valueOf(item));
      }
    });
    return mapped;
  }

  private List<String> toStringList(Object value) {
    List<String> values = new ArrayList<String>();
    if (value instanceof Collection) {
      for (Object item : (Collection<?>) value) {
        if (item != null && !String.valueOf(item).trim().isEmpty()) {
          values.add(String.valueOf(item));
        }
      }
    } else if (value != null && !String.valueOf(value).trim().isEmpty()) {
      values.add(String.valueOf(value));
    }
    return values;
  }

  private boolean isEmpty(Object value) {
    if (value == null) {
      return true;
    }
    if (value instanceof String) {
      return ((String) value).trim().isEmpty();
    }
    if (value instanceof Collection) {
      return ((Collection<?>) value).isEmpty();
    }
    if (value instanceof Map) {
      return ((Map<?, ?>) value).isEmpty();
    }
    return false;
  }

  private boolean isBlank(String value) {
    return value == null || value.trim().isEmpty();
  }
}
